package com.mine.player.audio

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.mine.player.R
import com.mine.player.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Hosts the ExoPlayer + MediaSession so playback survives backgrounding and shows a media
 * notification. The shared [AudioAnalyzerHolder.instance] is wired into the audio sink here so
 * the visuals stay reactive regardless of which component owns the player.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    @Volatile private var notificationEnabled = true

    private val closeCommand = SessionCommand(ACTION_CLOSE, Bundle.EMPTY)
    private val closeButton by lazy {
        CommandButton.Builder()
            .setDisplayName("关闭")
            .setIconResId(R.drawable.ic_notification_close)
            .setSessionCommand(closeCommand)
            .build()
    }

    /** Session callback: expose the custom "close" command and handle it by stopping playback. */
    private val sessionCallback = object : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
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
        mediaSession = MediaSession.Builder(this, player)
            .setCallback(sessionCallback)
            .apply { if (sessionActivity != null) setSessionActivity(sessionActivity) }
            .build()

        // Apply audio-output preferences from settings, reacting to live changes.
        val settingsRepo = SettingsRepository(applicationContext)
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

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
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
