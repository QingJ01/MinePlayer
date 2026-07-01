package com.mine.player.audio

import android.app.Application
import android.content.ComponentName
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.mine.player.data.PlaybackStateStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.pow

enum class PlayMode { LIST, SHUFFLE, SINGLE }

@OptIn(UnstableApi::class)
class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = LocalLibraryRepository(app)
    private val stateStore = PlaybackStateStore(app)

    /** Shared PCM tap that drives the audio-reactive visuals (same instance the service uses). */
    val analyzer = AudioAnalyzerHolder.instance

    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var playlistLoaded = false

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _playMode = MutableStateFlow(PlayMode.LIST)
    val playMode: StateFlow<PlayMode> = _playMode.asStateFlow()

    private val _sleepRemainingSec = MutableStateFlow(0)
    val sleepRemainingSec: StateFlow<Int> = _sleepRemainingSec.asStateFlow()
    private var sleepJob: Job? = null

    private val _playbackSpeed = MutableStateFlow(1f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    // Volume normalization (ReplayGain) + play/pause fade. baseVolume is the target
    // set by ReplayGain; fades ramp controller.volume toward it.
    private var replayGainEnabled = false
    private var fadeEnabled = false
    private var baseVolume = 1f
    private var volumeJob: Job? = null

    val currentTrack: StateFlow<Track?> =
        combine(_tracks, _currentIndex) { list, idx -> list.getOrNull(idx) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            if (!isPlaying) saveState() // remember the spot whenever playback pauses
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            controller?.let {
                _currentIndex.value = it.currentMediaItemIndex
                _durationMs.value = it.duration.coerceAtLeast(0L)
                _positionMs.value = it.currentPosition.coerceAtLeast(0L)
            }
            updateVolumeForCurrentTrack()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                _durationMs.value = controller?.duration?.coerceAtLeast(0L) ?: 0L
            }
        }
    }

    init {
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        controllerFuture = future
        future.addListener({
            val c = try {
                future.get()
            } catch (_: Exception) {
                null
            }
            controller = c
            if (c != null) {
                c.addListener(playerListener)
                _isPlaying.value = c.isPlaying
                _currentIndex.value = c.currentMediaItemIndex
                _durationMs.value = c.duration.coerceAtLeast(0L)
                applyPlayMode(c, _playMode.value)
                c.setPlaybackSpeed(_playbackSpeed.value)
                maybePreload()
            }
        }, ContextCompat.getMainExecutor(app))
        startPositionLoop()
    }

    fun loadLibrary(
        minDurationMs: Long = 10_000L,
        useMediaStore: Boolean = true,
        customFolders: Set<String> = emptySet(),
        blockedFolders: Set<String> = emptySet(),
    ) {
        viewModelScope.launch {
            _isScanning.value = true
            _tracks.value = repository.loadTracks(minDurationMs, useMediaStore, customFolders, blockedFolders)
            _isScanning.value = false
            maybePreload()
        }
    }

    /**
     * Load the queue into the player (paused) as soon as the library is known, resuming the
     * last-played track and position. A media notification then appears on app open, and playback
     * picks up where it left off.
     */
    private fun maybePreload() {
        val c = controller ?: return
        if (c.mediaItemCount > 0) return // already has a queue (playing or previously loaded)
        val list = _tracks.value
        if (list.isEmpty()) return
        ensurePlaylist(c, list) // sets items + prepare(); playWhenReady stays false → paused
        val savedIndex = list.indexOfFirst { it.id == stateStore.trackId() }
        if (savedIndex >= 0) {
            val pos = stateStore.positionMs()
            c.seekTo(savedIndex, pos)
            _currentIndex.value = savedIndex
            _positionMs.value = pos
        }
    }

    /** Persist the current track + position so playback can resume next launch. */
    private fun saveState() {
        val c = controller ?: return
        val track = currentTrack.value ?: return
        stateStore.save(track.id, c.currentPosition)
    }

    fun playTrackAt(index: Int) {
        val c = controller ?: return
        val list = _tracks.value
        if (index !in list.indices) return
        ensurePlaylist(c, list)
        volumeJob?.cancel()
        c.volume = baseVolume
        c.seekTo(index, 0L)
        c.playWhenReady = true
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.mediaItemCount == 0) {
            if (_tracks.value.isNotEmpty()) playTrackAt(0)
            return
        }
        if (c.isPlaying) pauseWithFade(c) else playWithFade(c)
    }

    // ---- Volume: ReplayGain normalization + play/pause fade -----------------

    fun setReplayGainEnabled(enabled: Boolean) {
        replayGainEnabled = enabled
        updateVolumeForCurrentTrack()
    }

    fun setFadeEnabled(enabled: Boolean) {
        fadeEnabled = enabled
    }

    /** Recompute [baseVolume] for the current track and apply it (unless a fade is mid-flight). */
    private fun updateVolumeForCurrentTrack() {
        if (!replayGainEnabled) {
            baseVolume = 1f
            if (volumeJob?.isActive != true) controller?.volume = 1f
            return
        }
        val track = currentTrack.value
        if (track == null) {
            baseVolume = 1f
            return
        }
        viewModelScope.launch {
            val db = ReplayGain.readGainDb(getApplication(), track.uri)
            // Only attenuate (clamp <= 1) so boosting quiet tracks never clips.
            baseVolume = if (db != null) 10f.pow(db / 20f).coerceIn(0.05f, 1f) else 1f
            if (volumeJob?.isActive != true) controller?.volume = baseVolume
        }
    }

    private fun playWithFade(c: MediaController) {
        volumeJob?.cancel()
        if (!fadeEnabled) {
            c.volume = baseVolume
            c.play()
            return
        }
        c.volume = 0f
        c.play()
        volumeJob = viewModelScope.launch { ramp(0f, baseVolume, 260L) }
    }

    private fun pauseWithFade(c: MediaController) {
        volumeJob?.cancel()
        if (!fadeEnabled) {
            c.pause()
            return
        }
        volumeJob = viewModelScope.launch {
            ramp(c.volume, 0f, 240L)
            c.pause()
            c.volume = baseVolume // restore so a resume from the notification isn't silent
        }
    }

    private suspend fun ramp(from: Float, to: Float, durationMs: Long) {
        val steps = 16
        val stepDelay = (durationMs / steps).coerceAtLeast(1L)
        for (i in 1..steps) {
            controller?.volume = (from + (to - from) * (i.toFloat() / steps)).coerceIn(0f, 1f)
            delay(stepDelay)
        }
        controller?.volume = to.coerceIn(0f, 1f)
    }

    fun next() {
        controller?.let { if (it.hasNextMediaItem()) it.seekToNextMediaItem() }
    }

    fun previous() {
        val c = controller ?: return
        if (c.currentPosition > 3000L) {
            c.seekTo(0L)
        } else if (c.hasPreviousMediaItem()) {
            c.seekToPreviousMediaItem()
        } else {
            c.seekTo(0L)
        }
    }

    fun seekTo(ms: Long) {
        val c = controller ?: return
        c.seekTo(ms.coerceIn(0L, _durationMs.value.coerceAtLeast(0L)))
        _positionMs.value = ms
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        controller?.setPlaybackSpeed(speed)
    }

    fun cyclePlayMode() {
        val next = when (_playMode.value) {
            PlayMode.LIST -> PlayMode.SHUFFLE
            PlayMode.SHUFFLE -> PlayMode.SINGLE
            PlayMode.SINGLE -> PlayMode.LIST
        }
        _playMode.value = next
        controller?.let { applyPlayMode(it, next) }
    }

    private fun applyPlayMode(c: MediaController, mode: PlayMode) {
        c.shuffleModeEnabled = mode == PlayMode.SHUFFLE
        c.repeatMode = if (mode == PlayMode.SINGLE) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_ALL
    }

    /** Insert a track right after the current one in the queue. */
    fun playNext(track: Track) {
        val c = controller ?: return
        val at = (c.currentMediaItemIndex + 1).coerceIn(0, c.mediaItemCount)
        c.addMediaItem(at, mediaItemFor(track))
    }

    /** MediaItem carrying metadata so the system media notification shows title / artist / art. */
    private fun mediaItemFor(track: Track): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.displayArtist)
            .setAlbumTitle(track.album.ifBlank { null })
            .apply { track.albumArtUri?.let { setArtworkUri(it) } }
            .build()
        return MediaItem.Builder()
            .setMediaId(track.id.toString())
            .setUri(track.uri)
            .setMediaMetadata(metadata)
            .build()
    }

    /** minutes <= 0 cancels; otherwise pause playback after that many minutes. */
    fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        if (minutes <= 0) {
            _sleepRemainingSec.value = 0
            return
        }
        _sleepRemainingSec.value = minutes * 60
        sleepJob = viewModelScope.launch {
            while (_sleepRemainingSec.value > 0) {
                delay(1000L)
                _sleepRemainingSec.value = _sleepRemainingSec.value - 1
            }
            controller?.pause()
        }
    }

    private fun ensurePlaylist(c: MediaController, list: List<Track>) {
        if (playlistLoaded && c.mediaItemCount == list.size) return
        c.setMediaItems(list.map { mediaItemFor(it) })
        c.prepare()
        playlistLoaded = true
    }

    private fun startPositionLoop() {
        viewModelScope.launch {
            var sinceSave = 0
            while (true) {
                controller?.let {
                    if (it.isPlaying) {
                        _positionMs.value = it.currentPosition.coerceAtLeast(0L)
                        if (++sinceSave >= 12) { // persist position roughly every 3s while playing
                            sinceSave = 0
                            saveState()
                        }
                    }
                }
                delay(250L)
            }
        }
    }

    override fun onCleared() {
        saveState()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        super.onCleared()
    }
}
