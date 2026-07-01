package com.mine.player.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.mine.player.audio.AudioInfo
import com.mine.player.audio.AudioOutput
import com.mine.player.audio.PlayerViewModel
import com.mine.player.audio.Track
import com.mine.player.audio.readAudioInfo
import com.mine.player.data.PlaylistStore
import com.mine.player.data.Settings
import com.mine.player.data.SettingsRepository
import com.mine.player.ui.components.AddToPlaylistSheet
import com.mine.player.ui.components.AppDrawer
import com.mine.player.ui.components.BrowseTab
import com.mine.player.ui.components.PlaceholderScreen
import com.mine.player.ui.components.SleepTimerSheet
import com.mine.player.ui.components.TrackActionSheet
import com.mine.player.ui.components.TrackInfoSheet
import com.mine.player.ui.components.TransportBar
import com.mine.player.ui.screens.AlbumsScreen
import com.mine.player.ui.screens.ArtistsScreen
import com.mine.player.ui.screens.CoverWallScreen
import com.mine.player.ui.screens.LibraryScreen
import com.mine.player.ui.screens.NowPlayingScreen
import com.mine.player.ui.screens.PlaylistsScreen
import com.mine.player.ui.screens.SettingsScreen
import com.mine.player.ui.theme.LocalPalette
import kotlinx.coroutines.launch

private val audioPermission: String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

@Composable
fun MinePlayerRoot(
    viewModel: PlayerViewModel,
    settings: Settings,
    settingsRepo: SettingsRepository,
) {
    val context = LocalContext.current
    val palette = LocalPalette.current
    val scope = rememberCoroutineScope()

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, audioPermission) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { result -> granted = result }
    // Go straight to the home screen and ask for the audio permission there, once, on launch.
    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(audioPermission)
    }

    // Ask for POST_NOTIFICATIONS (Android 13+) so the media notification / lock-screen controls show.
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Apply settings to runtime.
    LaunchedEffect(settings.sensitivity) { viewModel.analyzer.sensitivity = settings.sensitivity }
    LaunchedEffect(settings.outputSampleRate) { AudioOutput.setOutputSampleRate(settings.outputSampleRate) }
    LaunchedEffect(settings.replayGain) { viewModel.setReplayGainEnabled(settings.replayGain) }
    LaunchedEffect(settings.fadeInOut) { viewModel.setFadeEnabled(settings.fadeInOut) }
    LaunchedEffect(granted, settings.minDurationSec, settings.useMediaStore, settings.customFolders, settings.blockedFolders) {
        if (granted) viewModel.loadLibrary(
            settings.minDurationSec * 1000L,
            settings.useMediaStore,
            settings.customFolders,
            settings.blockedFolders,
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val tracks by viewModel.tracks.collectAsState()
        val scanning by viewModel.isScanning.collectAsState()
        val currentIndex by viewModel.currentIndex.collectAsState()
        val currentTrack by viewModel.currentTrack.collectAsState()
        val isPlaying by viewModel.isPlaying.collectAsState()
        val playMode by viewModel.playMode.collectAsState()
        val playbackSpeed by viewModel.playbackSpeed.collectAsState()

        var showNowPlaying by remember { mutableStateOf(false) }
        var showCoverWall by remember { mutableStateOf(false) }
        var showSettings by remember { mutableStateOf(false) }
        var browseTab by remember { mutableStateOf(BrowseTab.SONGS) }
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val playlistStore = remember { PlaylistStore(context.applicationContext) }
        val playlists by playlistStore.playlists.collectAsState()
        val sleepRemaining by viewModel.sleepRemainingSec.collectAsState()
        var menuTrack by remember { mutableStateOf<Track?>(null) }
        var infoTrack by remember { mutableStateOf<Track?>(null) }
        var addToPlaylistTrack by remember { mutableStateOf<Track?>(null) }
        var showCreateDialog by remember { mutableStateOf(false) }
        var pendingAddTrack by remember { mutableStateOf<Track?>(null) }
        var showSleepSheet by remember { mutableStateOf(false) }
        var pendingArtist by remember { mutableStateOf<String?>(null) }
        var pendingAlbumId by remember { mutableStateOf<Long?>(null) }

        // Deleting a track uses the system delete-confirmation (scoped storage); reload on success.
        val deleteLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                viewModel.loadLibrary(
                    settings.minDurationSec * 1000L, settings.useMediaStore,
                    settings.customFolders, settings.blockedFolders,
                )
            }
        }
        val requestDelete: (Track) -> Unit = { t ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val pi = MediaStore.createDeleteRequest(context.contentResolver, listOf(t.uri))
                    deleteLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                } catch (_: Throwable) {
                }
            } else {
                try {
                    context.contentResolver.delete(t.uri, null, null)
                    viewModel.loadLibrary(
                        settings.minDurationSec * 1000L, settings.useMediaStore,
                        settings.customFolders, settings.blockedFolders,
                    )
                } catch (_: Throwable) {
                }
            }
        }

        // Centralized system-bar control: one keyed effect drives visibility +
        // icon contrast for the whole app, so the status bar no longer flickers
        // when moving between the library and the black now-playing stage.
        val nowPlayingActive = showNowPlaying && currentTrack != null
        val effectiveDark = if (settings.followSystemTheme) isSystemInDarkTheme() else settings.darkTheme
        SystemBarsController(
            lightBackground = !effectiveDark && !nowPlayingActive,
            hideStatusBar = nowPlayingActive || settings.immersiveMode,
            hideNavBar = nowPlayingActive,
        )

        when {
            showSettings -> {
                BackHandler { showSettings = false }
                SettingsScreen(
                    settings = settings,
                    onToggleFollowSystem = { scope.launch { settingsRepo.setFollowSystemTheme(it) } },
                    onToggleDark = { scope.launch { settingsRepo.setDark(it) } },
                    onToggleImmersive = { scope.launch { settingsRepo.setImmersiveMode(it) } },
                    onAccentFollowSystem = { scope.launch { settingsRepo.setAccentFollowSystem(it) } },
                    onAccentColor = { argb -> scope.launch { settingsRepo.setAccentColor(argb); settingsRepo.setAccentFollowSystem(false) } },
                    onDefaultPreset = { scope.launch { settingsRepo.setDefaultPreset(it) } },
                    onDefaultLyric = { scope.launch { settingsRepo.setDefaultLyricMode(it) } },
                    onSensitivity = { scope.launch { settingsRepo.setSensitivity(it) } },
                    onMinDuration = { scope.launch { settingsRepo.setMinDuration(it) } },
                    onRescan = {
                        viewModel.loadLibrary(
                            settings.minDurationSec * 1000L,
                            settings.useMediaStore,
                            settings.customFolders,
                            settings.blockedFolders,
                        )
                    },
                    scanning = scanning,
                    trackCount = tracks.size,
                    onToggleMediaStore = { scope.launch { settingsRepo.setUseMediaStore(it) } },
                    onAddCustomFolder = { scope.launch { settingsRepo.addCustomFolder(it) } },
                    onRemoveCustomFolder = { scope.launch { settingsRepo.removeCustomFolder(it) } },
                    onAddBlockedFolder = { scope.launch { settingsRepo.addBlockedFolder(it) } },
                    onRemoveBlockedFolder = { scope.launch { settingsRepo.removeBlockedFolder(it) } },
                    playbackSpeed = playbackSpeed,
                    onPlaybackSpeed = { viewModel.setPlaybackSpeed(it) },
                    outputSampleRate = settings.outputSampleRate,
                    onOutputSampleRate = { scope.launch { settingsRepo.setOutputSampleRate(it) } },
                    onExclusiveFocus = { scope.launch { settingsRepo.setExclusiveFocus(it) } },
                    onToggleNotification = { scope.launch { settingsRepo.setShowNotification(it) } },
                    onToggleCloseButton = { scope.launch { settingsRepo.setShowCloseButton(it) } },
                    onReplayGain = { scope.launch { settingsRepo.setReplayGain(it) } },
                    onFadeInOut = { scope.launch { settingsRepo.setFadeInOut(it) } },
                    onGapless = { scope.launch { settingsRepo.setGapless(it) } },
                    onBack = { showSettings = false },
                )
            }

            showNowPlaying && currentTrack != null -> {
                BackHandler { showNowPlaying = false }
                NowPlayingScreen(
                    track = currentTrack,
                    isPlaying = isPlaying,
                    positionFlow = viewModel.positionMs,
                    durationFlow = viewModel.durationMs,
                    analyzer = viewModel.analyzer,
                    initialPreset = settings.defaultPreset,
                    initialLyricMode = settings.defaultLyricMode,
                    sleepRemainingSec = sleepRemaining,
                    onOpenSleep = { showSleepSheet = true },
                    queue = tracks,
                    currentIndex = currentIndex,
                    onJumpTo = { viewModel.playTrackAt(it) },
                    playMode = playMode,
                    onCyclePlayMode = { viewModel.cyclePlayMode() },
                    onTogglePlay = { viewModel.togglePlayPause() },
                    onNext = { viewModel.next() },
                    onPrev = { viewModel.previous() },
                    onSeek = { viewModel.seekTo(it) },
                    onClose = { showNowPlaying = false },
                )
            }

            showCoverWall -> {
                BackHandler { showCoverWall = false }
                CoverWallScreen(
                    tracks = tracks,
                    currentIndex = currentIndex,
                    onPlay = { viewModel.playTrackAt(it); showCoverWall = false },
                    onClose = { showCoverWall = false },
                )
            }

            else -> {
                val currentTrackId = tracks.getOrNull(currentIndex)?.id
                val onPlayTrack: (com.mine.player.audio.Track) -> Unit = { t ->
                    val idx = tracks.indexOf(t); if (idx >= 0) viewModel.playTrackAt(idx)
                }
                val drawerWidth = (LocalConfiguration.current.screenWidthDp * 0.6f).dp
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet(
                            modifier = Modifier.width(drawerWidth),
                            drawerContainerColor = palette.surface,
                        ) {
                            AppDrawer(
                                selected = browseTab,
                                onSelect = { browseTab = it; scope.launch { drawerState.close() } },
                                onSettings = { showSettings = true; scope.launch { drawerState.close() } },
                            )
                        }
                    },
                ) {
                    Box(modifier = Modifier.fillMaxSize().background(palette.bg)) {
                        when (browseTab) {
                            BrowseTab.SONGS -> LibraryScreen(
                                tracks = tracks,
                                currentIndex = currentIndex,
                                playMode = playMode,
                                onCyclePlayMode = { viewModel.cyclePlayMode() },
                                onTrackClick = { viewModel.playTrackAt(it) },
                                onShuffle = { if (tracks.isNotEmpty()) viewModel.playTrackAt(tracks.indices.random()) },
                                onMenu = { scope.launch { drawerState.open() } },
                                onOpenShelf = { showCoverWall = true },
                                onTrackMenu = { menuTrack = it },
                                sortKeyIndex = settings.sortKey,
                                sortAscending = settings.sortAscending,
                                onSort = { k, a -> scope.launch { settingsRepo.setSort(k, a) } },
                                modifier = Modifier.statusBarsPadding(),
                                contentPadding = PaddingValues(bottom = 118.dp),
                            )
                            BrowseTab.ALBUMS -> AlbumsScreen(
                                tracks, currentTrackId, onPlayTrack,
                                onMenu = { scope.launch { drawerState.open() } },
                                initialOpenId = pendingAlbumId,
                                onInitialConsumed = { pendingAlbumId = null },
                            )
                            BrowseTab.ARTISTS -> ArtistsScreen(
                                tracks, currentTrackId, onPlayTrack,
                                onMenu = { scope.launch { drawerState.open() } },
                                initialOpen = pendingArtist,
                                onInitialConsumed = { pendingArtist = null },
                            )
                            BrowseTab.FOLDERS -> PlaceholderScreen("文件夹") { scope.launch { drawerState.open() } }
                            BrowseTab.PLAYLISTS -> PlaylistsScreen(
                                playlists = playlists,
                                tracks = tracks,
                                currentTrackId = currentTrackId,
                                onPlayTrack = onPlayTrack,
                                onCreatePlaylist = { pendingAddTrack = null; showCreateDialog = true },
                                onDeletePlaylist = { playlistStore.delete(it) },
                                onMenu = { scope.launch { drawerState.open() } },
                            )
                        }

                        TransportBarHost(
                            track = currentTrack,
                            isPlaying = isPlaying,
                            positionFlow = viewModel.positionMs,
                            durationFlow = viewModel.durationMs,
                            onTogglePlay = { viewModel.togglePlayPause() },
                            onNext = { viewModel.next() },
                            onPrev = { viewModel.previous() },
                            onClick = { if (currentTrack != null) showNowPlaying = true },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                        )
                    }
                }
            }
        }

        menuTrack?.let { t ->
            TrackActionSheet(
                track = t,
                onAddToPlaylist = { addToPlaylistTrack = t; menuTrack = null },
                onPlayNext = { viewModel.playNext(t) },
                onShare = { shareTrack(context, t) },
                onOpenArtist = { browseTab = BrowseTab.ARTISTS; pendingArtist = t.displayArtist; menuTrack = null },
                onOpenAlbum = { browseTab = BrowseTab.ALBUMS; pendingAlbumId = t.albumId; menuTrack = null },
                onInfo = { infoTrack = t; menuTrack = null },
                onDelete = { menuTrack = null; requestDelete(t) },
                onDismiss = { menuTrack = null },
            )
        }
        infoTrack?.let { t ->
            var audioInfo by remember(t.id) { mutableStateOf<AudioInfo?>(null) }
            LaunchedEffect(t.id) { audioInfo = readAudioInfo(context, t.uri) }
            TrackInfoSheet(track = t, audioInfo = audioInfo, onDismiss = { infoTrack = null })
        }
        addToPlaylistTrack?.let { t ->
            AddToPlaylistSheet(
                playlists = playlists,
                onPick = { plId -> playlistStore.addTrack(plId, t.id); addToPlaylistTrack = null },
                onCreate = { pendingAddTrack = t; addToPlaylistTrack = null; showCreateDialog = true },
                onDismiss = { addToPlaylistTrack = null },
            )
        }
        if (showSleepSheet) {
            SleepTimerSheet(sleepRemaining, onPick = { viewModel.setSleepTimer(it) }, onDismiss = { showSleepSheet = false })
        }
        if (showCreateDialog) {
            CreatePlaylistDialog(
                onCreate = { name ->
                    val id = playlistStore.create(name)
                    pendingAddTrack?.let { playlistStore.addTrack(id, it.id) }
                    pendingAddTrack = null
                    showCreateDialog = false
                },
                onDismiss = { showCreateDialog = false },
            )
        }
    }
}

private fun shareTrack(context: Context, track: Track) {
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/*"
            putExtra(Intent.EXTRA_STREAM, track.uri)
            putExtra(Intent.EXTRA_TITLE, track.title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享"))
    } catch (_: Throwable) {
    }
}

@Composable
private fun CreatePlaylistDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.surface,
        title = { Text("新建歌单", color = p.ink) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text("歌单名称", color = p.muted) },
            )
        },
        confirmButton = { TextButton(onClick = { onCreate(name.ifBlank { "新歌单" }) }) { Text("创建", color = p.accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = p.muted) } },
    )
}

/**
 * Wraps [TransportBar], collecting position/duration here so the frequent (250 ms) progress
 * updates recompose only the mini-player, not the whole library/drawer tree.
 */
@Composable
private fun TransportBarHost(
    track: Track?,
    isPlaying: Boolean,
    positionFlow: kotlinx.coroutines.flow.StateFlow<Long>,
    durationFlow: kotlinx.coroutines.flow.StateFlow<Long>,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val positionMs by positionFlow.collectAsState()
    val durationMs by durationFlow.collectAsState()
    TransportBar(
        track = track,
        isPlaying = isPlaying,
        positionMs = positionMs,
        durationMs = durationMs,
        onTogglePlay = onTogglePlay,
        onNext = onNext,
        onPrev = onPrev,
        onClick = onClick,
        modifier = modifier,
    )
}

