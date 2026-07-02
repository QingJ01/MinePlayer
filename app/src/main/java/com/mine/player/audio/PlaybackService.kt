package com.mine.player.audio

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.mine.player.R
import com.mine.player.data.ListenStatsStore
import com.mine.player.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Hosts the ExoPlayer + MediaSession so playback survives backgrounding and shows a media
 * notification. The shared [AudioAnalyzerHolder.instance] is wired into the audio sink here so
 * the visuals stay reactive regardless of which component owns the player.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {

    private var mediaSession: MediaLibrarySession? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    @Volatile private var notificationEnabled = true

    // The browsable library exposed to car head units, loaded lazily from MediaStore.
    // All mutation happens on the main thread (serviceScope is Main-confined).
    @Volatile private var library: List<Track> = emptyList()
    private var libraryDeferred: Deferred<List<Track>>? = null
    // When off, car head units (Android Auto) see an empty library.
    @Volatile private var carEnabled = true
    private val settingsRepo by lazy { SettingsRepository(applicationContext) }

    // Listen-stats accounting lives here (not in the ViewModel) so background and
    // Android Auto playback count too. A "play" = 30s+ listened, then finished naturally.
    private val statsStore by lazy { ListenStatsStore(applicationContext) }
    private var listenTrackId: Long? = null
    private var listenArtist: String = ""
    private var listenMs = 0L

    private val closeCommand = SessionCommand(ACTION_CLOSE, Bundle.EMPTY)
    private val closeButton by lazy {
        CommandButton.Builder()
            .setDisplayName("关闭")
            .setIconResId(R.drawable.ic_notification_close)
            .setSessionCommand(closeCommand)
            .build()
    }

    /**
     * Library session callback: exposes the custom "close" command, the browsable car media tree,
     * and resolves browsed items into playable ones.
     */
    private val sessionCallback = object : MediaLibrarySession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                .add(closeCommand)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == ACTION_CLOSE) {
                val player = session.player
                player.pause()
                player.clearMediaItems()
                try {
                    stopForeground(Service.STOP_FOREGROUND_REMOVE)
                } catch (_: Throwable) {
                }
                stopSelf()
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            if (carEnabled) libraryAsync() // warm the tree; root itself needs no library
            return Futures.immediateFuture(LibraryResult.ofItem(CarBrowseTree.rootItem(), params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            if (!carEnabled) {
                return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.of(), params))
            }
            val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            serviceScope.launch {
                try {
                    val tracks = libraryAsync().await()
                    val paged = CarBrowseTree.childrenPaged(parentId, tracks, page, pageSize)
                    future.set(LibraryResult.ofItemList(ImmutableList.copyOf(paged), params))
                } catch (t: Throwable) {
                    future.setException(t)
                }
            }
            return future
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val item = CarBrowseTree.item(mediaId, library)
            return Futures.immediateFuture(
                if (item != null) LibraryResult.ofItem(item, null)
                else LibraryResult.ofError<MediaItem>(SessionResult.RESULT_ERROR_BAD_VALUE)
            )
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            // Items from the in-app controller already carry a URI — pass them through untouched.
            if (mediaItems.all { it.localConfiguration != null }) {
                return Futures.immediateFuture(mediaItems)
            }
            // URI-less items (Android Auto / playback resumption) must be re-resolved against the
            // library; wait for the load instead of failing, and drop ids that no longer resolve —
            // an unresolved URI-less item would crash ExoPlayer's media source factory.
            val future = SettableFuture.create<MutableList<MediaItem>>()
            serviceScope.launch {
                try {
                    val tracks = libraryAsync().await()
                    val resolved = mediaItems.mapNotNullTo(ArrayList(mediaItems.size)) {
                        CarBrowseTree.resolvePlayable(it, tracks)
                    }
                    future.set(resolved)
                } catch (t: Throwable) {
                    future.setException(t)
                }
            }
            return future
        }
    }

    /**
     * Load the browsable library lazily (from MediaStore, honoring the folder/duration filters).
     * The result is cached — including a legitimately empty one — so browse callbacks don't
     * re-scan; a failed load clears the cache so the next request can retry (e.g. once the
     * storage permission has been granted).
     */
    private fun libraryAsync(): Deferred<List<Track>> {
        libraryDeferred?.let { return it }
        val deferred = serviceScope.async {
            try {
                val s = settingsRepo.settings.first()
                val loaded = LocalLibraryRepository(applicationContext).loadTracks(
                    minDurationMs = s.minDurationSec * 1000L,
                    useMediaStore = s.useMediaStore,
                    customFolders = s.customFolders,
                    blockedFolders = s.blockedFolders,
                )
                library = loaded
                notifyBrowseTreeChanged() // tell subscribed browsers the tree is now populated
                loaded
            } catch (t: Throwable) {
                libraryDeferred = null
                emptyList()
            }
        }
        libraryDeferred = deferred
        return deferred
    }

    /** Re-announce every category to subscribed browsers with cheap (item-free) child counts. */
    private fun notifyBrowseTreeChanged() {
        val session = mediaSession ?: return
        CarBrowseTree.categoryIds().forEach { id ->
            val count = if (carEnabled) CarBrowseTree.childCount(id, library) else 0
            session.notifyChildrenChanged(id, count, null)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf<AudioProcessor>(AudioOutput.sonic, AudioAnalyzerHolder.instance))
                    .build()
            }
        }
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        val player = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        // Fixed audio session id so the equalizer can attach to it.
        val am = getSystemService(android.media.AudioManager::class.java)
        if (am != null) {
            try {
                val sessionId = am.generateAudioSessionId()
                player.setAudioSessionId(sessionId)
                AudioEffects.attach(sessionId)
            } catch (_: Throwable) {
            }
        }

        // Tapping the media notification returns to the app.
        val sessionActivity = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(
                this, 0, it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
        mediaSession = MediaLibrarySession.Builder(this, player, sessionCallback)
            .apply { if (sessionActivity != null) setSessionActivity(sessionActivity) }
            .build()

        // Count listens at the player level so every controller (UI, notification, car) is covered.
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // AUTO / REPEAT means the previous track reached its natural end → count it;
                // any other reason (manual skip, queue change) is not a completion.
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
                ) {
                    recordCompletedPlay()
                } else {
                    listenTrackId = null
                    listenMs = 0L
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    recordCompletedPlay() // last track finished with nothing to advance to
                }
            }
        })
        serviceScope.launch {
            while (isActive) {
                mediaSession?.player?.let { if (it.isPlaying) tickListen(it) }
                delay(1000L)
            }
        }

        // Reload the car browse library when any library-affecting setting changes, so the tree
        // never serves a stale list after a rescan / folder / min-duration change in the app.
        serviceScope.launch {
            settingsRepo.settings
                .map { listOf(it.minDurationSec, it.useMediaStore, it.customFolders, it.blockedFolders) }
                .distinctUntilChanged()
                .drop(1) // the initial load reads settings itself
                .collect {
                    if (libraryDeferred != null) { // only refresh if something already requested it
                        libraryDeferred = null
                        libraryAsync()
                    }
                }
        }

        // Apply audio-output preferences from settings, reacting to live changes.
        serviceScope.launch {
            settingsRepo.settings.map { it.exclusiveFocus }.distinctUntilChanged().collect { exclusive ->
                try {
                    player.setAudioAttributes(audioAttributes, /* handleAudioFocus = */ exclusive)
                } catch (_: Throwable) {
                }
            }
        }
        serviceScope.launch {
            settingsRepo.settings.map { it.gapless }.distinctUntilChanged().collect { gapless ->
                try {
                    player.skipSilenceEnabled = gapless
                } catch (_: Throwable) {
                }
            }
        }
        serviceScope.launch {
            settingsRepo.settings.map { it.showNotification }.distinctUntilChanged().collect { enabled ->
                notificationEnabled = enabled
                // Re-evaluate immediately so the notification appears / disappears on toggle.
                mediaSession?.let { onUpdateNotification(it, it.player.playWhenReady) }
            }
        }
        serviceScope.launch {
            settingsRepo.settings.map { it.showCloseButton }.distinctUntilChanged().collect { show ->
                val layout = if (show) listOf(closeButton) else emptyList()
                try {
                    mediaSession?.setCustomLayout(layout)
                } catch (_: Throwable) {
                }
            }
        }
        serviceScope.launch {
            settingsRepo.settings.map { it.carBrowsingEnabled }.distinctUntilChanged().collect { enabled ->
                carEnabled = enabled
                // Push the new (populated or empty) tree to any connected head unit.
                notifyBrowseTreeChanged()
            }
        }
    }

    /** Accumulate how long the current track has actually been playing (any controller). */
    private fun tickListen(player: Player) {
        val item = player.currentMediaItem ?: return
        val id = item.mediaId.removePrefix(CarBrowseTree.SONG_PREFIX).toLongOrNull() ?: return
        if (id != listenTrackId) {
            listenTrackId = id
            listenArtist = item.mediaMetadata.artist?.toString().orEmpty()
            listenMs = 0L
        }
        listenMs += 1000L
    }

    /** Count a play for the just-finished track if it was listened to for 30s+. */
    private fun recordCompletedPlay() {
        val id = listenTrackId
        if (id != null && listenMs >= 30_000L) {
            statsStore.recordPlay(id, listenArtist)
        }
        listenTrackId = null
        listenMs = 0L
    }

    companion object {
        private const val ACTION_CLOSE = "com.mine.player.CLOSE"
    }

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        if (notificationEnabled) {
            super.onUpdateNotification(session, startInForegroundRequired)
        } else {
            // Suppress the media notification. Note: Android requires a foreground notification for
            // background playback, so hiding it may limit playback once the app is backgrounded.
            try {
                stopForeground(Service.STOP_FOREGROUND_REMOVE)
            } catch (_: Throwable) {
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        AudioEffects.release()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
