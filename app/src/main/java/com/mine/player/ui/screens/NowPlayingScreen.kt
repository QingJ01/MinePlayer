package com.mine.player.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.media.audiofx.AudioEffect
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mine.player.audio.AlbumArtLoader
import com.mine.player.audio.AudioEffects
import com.mine.player.audio.AudioInfo
import com.mine.player.audio.PlayMode
import com.mine.player.audio.Track
import com.mine.player.audio.readAudioInfo
import com.mine.player.lyrics.LyricLine
import com.mine.player.lyrics.LyricRepository
import com.mine.player.ui.components.InfoSheet
import com.mine.player.ui.components.LyricView
import com.mine.player.ui.components.QueueSheet
import com.mine.player.ui.components.SingleLyricView
import com.mine.player.ui.components.formatTime
import com.mine.player.ui.theme.MR
import com.mine.player.visual.AudioAnalyzer
import com.mine.player.visual.CoverColor
import com.mine.player.visual.VisualStage
import com.mine.player.visual.gl.VisualRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

private data class VisualPreset(val label: String, val preset: Float, val skull: Boolean)

private val PRESETS = listOf(
    VisualPreset("丝绸", 0f, false),
    VisualPreset("黑胶", 4f, false),
    VisualPreset("星球", 2f, false),
    VisualPreset("隧道", 1f, false),
)

/**
 * Full-screen now-playing experience built as three horizontally-swipeable pages
 * over a single fixed particle stage:
 *
 *   右滑 → 歌词页 · 中间 → 视觉 + 基础播控 · 左滑 → 聚合控制台
 *
 * The stage is a fixed background so the GL surface never scrolls; the pages are
 * transparent overlays that slide across it. Nothing is pinned to the very top
 * edge anymore, so the camera cutout no longer covers any controls.
 */
@Composable
fun NowPlayingScreen(
    track: Track?,
    isPlaying: Boolean,
    positionFlow: StateFlow<Long>,
    durationFlow: StateFlow<Long>,
    analyzer: AudioAnalyzer,
    initialPreset: Int = 0,
    initialLyricMode: Int = 0,
    sleepRemainingSec: Int = 0,
    onOpenSleep: () -> Unit = {},
    queue: List<Track> = emptyList(),
    currentIndex: Int = -1,
    onJumpTo: (Int) -> Unit = {},
    playMode: PlayMode = PlayMode.LIST,
    onCyclePlayMode: () -> Unit = {},
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: (Long) -> Unit,
    onClose: () -> Unit,
) {
    // Collected here (not at the app root) so only this screen recomposes as position ticks.
    val positionMs by positionFlow.collectAsState()
    val durationMs by durationFlow.collectAsState()

    val context = LocalContext.current
    val renderer = remember { VisualRenderer(context.applicationContext, analyzer) }

    // Stage backdrop tinted from the current cover (used by both the GL clear color and the
    // Compose background behind it, so the two always agree).
    var backdropColor by remember { mutableStateOf(Color(CoverColor.DEFAULT)) }
    LaunchedEffect(track?.albumId) {
        val bmp = track?.let { AlbumArtLoader.load(context, it, 512) }
        renderer.submitCover(bmp)
        val argb = if (bmp != null) withContext(Dispatchers.Default) { CoverColor.backdrop(bmp) } else CoverColor.DEFAULT
        backdropColor = Color(argb)
        renderer.setBackground(AndroidColor.red(argb) / 255f, AndroidColor.green(argb) / 255f, AndroidColor.blue(argb) / 255f)
    }

    var lyrics by remember(track?.id) { mutableStateOf<List<LyricLine>>(emptyList()) }
    LaunchedEffect(track?.id) {
        lyrics = track?.let { LyricRepository(context).load(it) } ?: emptyList()
    }

    var presetIndex by remember { mutableStateOf(initialPreset.coerceIn(0, PRESETS.lastIndex)) }
    LaunchedEffect(Unit) {
        renderer.preset = PRESETS[presetIndex].preset
        renderer.skullMode = PRESETS[presetIndex].skull
    }

    var showQueue by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var immersive by remember { mutableStateOf(false) }
    var keepScreenOn by remember { mutableStateOf(false) }

    // In immersive mode the cover sits nearer the center (no bottom controls crowding it).
    LaunchedEffect(immersive) { renderer.setCoverLift(if (immersive) 0.05f else 0.28f) }
    // Back exits immersive first, before closing the now-playing screen.
    BackHandler(enabled = immersive) { immersive = false }
    var audioInfo by remember(track?.id) { mutableStateOf<AudioInfo?>(null) }
    LaunchedEffect(track?.id) { audioInfo = track?.let { readAudioInfo(context, it.uri) } }

    // System-bar visibility is owned centrally by SystemBarsController; here we
    // only keep the screen awake when the user asks for it.
    val view = LocalView.current
    DisposableEffect(keepScreenOn) {
        view.keepScreenOn = keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    // The active lyric line rides along on the center page unless the user set the
    // default mode to "封面" (cover only).
    val showCenterLyric = initialLyricMode != 2

    // 0 = lyrics (right swipe), 1 = center visual, 2 = console (left swipe)
    val pagerState = rememberPagerState(initialPage = 1) { 3 }

    Box(modifier = Modifier.fillMaxSize().background(backdropColor)) {
        // Fixed particle stage behind everything.
        VisualStage(renderer = renderer, modifier = Modifier.fillMaxSize())

        if (immersive) {
            ImmersiveOverlay(
                track = track,
                lyrics = lyrics,
                positionMs = positionMs,
                isPlaying = isPlaying,
                onTogglePlay = onTogglePlay,
                onPrev = onPrev,
                onNext = onNext,
                onExit = { immersive = false },
            )
            return@Box
        }

        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                // 右滑 → 控制台
                0 -> ConsolePage(
                    presetIndex = presetIndex,
                    onSelectPreset = { i ->
                        presetIndex = i
                        renderer.preset = PRESETS[i].preset
                        renderer.skullMode = PRESETS[i].skull
                    },
                    playMode = playMode,
                    onCyclePlayMode = onCyclePlayMode,
                    sleepRemainingSec = sleepRemainingSec,
                    onEnterImmersive = { immersive = true },
                    onOpenQueue = { showQueue = true },
                    onOpenSleep = onOpenSleep,
                    onOpenInfo = { showInfo = true },
                    onOpenSystemEq = { openSystemEq(context) },
                    onClose = onClose,
                )

                1 -> CenterPage(
                    renderer = renderer,
                    track = track,
                    isPlaying = isPlaying,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    lyrics = lyrics,
                    showLyricLine = showCenterLyric,
                    onTogglePlay = onTogglePlay,
                    onNext = onNext,
                    onPrev = onPrev,
                    onSeek = onSeek,
                )

                // 左滑 → 歌词页
                else -> LyricsPage(
                    lyrics = lyrics,
                    positionMs = positionMs,
                    title = track?.title ?: "歌词",
                    artist = track?.displayArtist ?: "",
                    onSeek = onSeek,
                )
            }
        }

        PageDots(
            count = 3,
            current = pagerState.currentPage,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 8.dp),
        )
    }

    if (showQueue) {
        QueueSheet(
            queue = queue,
            currentIndex = currentIndex,
            onJumpTo = onJumpTo,
            onDismiss = { showQueue = false },
        )
    }
    if (showInfo && track != null) {
        InfoSheet(
            track = track,
            audioInfo = audioInfo,
            keepScreenOn = keepScreenOn,
            onKeepScreenOn = { keepScreenOn = it },
            onDismiss = { showInfo = false },
        )
    }
}

/** Center page: transparent over the stage; forwards touch to the renderer and hosts basic playback controls. */
@Composable
private fun CenterPage(
    renderer: VisualRenderer,
    track: Track?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    lyrics: List<LyricLine>,
    showLyricLine: Boolean,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            // Non-consuming touch tap: drives the interactive shader uniform while
            // still letting the pager claim horizontal swipes.
            .pointerInput(renderer) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull()
                        // Ignore events already claimed by the pager (horizontal swipe) or by a
                        // control (seek / buttons) so the particle push doesn't fire during them.
                        if (change != null && change.pressed && !change.isConsumed) {
                            renderer.setTouch(
                                (change.position.x / size.width).coerceIn(0f, 1f),
                                (change.position.y / size.height).coerceIn(0f, 1f),
                                true,
                            )
                        } else {
                            renderer.setTouch(0.5f, 0.5f, false)
                        }
                    }
                }
            },
    ) {
        // Soft scrim so the controls read against a busy stage.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(320.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, MR.Black.copy(alpha = 0.82f)))),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 34.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (showLyricLine) {
                SingleLyricView(
                    lines = lyrics,
                    positionMs = positionMs,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                )
            }
            Text(
                text = track?.title ?: "未在播放",
                color = MR.Ink,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = track?.displayArtist ?: "",
                color = MR.Muted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            SeekRow(positionMs, durationMs, onSeek)

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPrev) {
                    Icon(Icons.Rounded.SkipPrevious, "上一首", tint = MR.Ink2, modifier = Modifier.size(32.dp))
                }
                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(MR.Accent, MR.ChillMint)))
                        .clickable(onClick = onTogglePlay),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        tint = MR.Black,
                        modifier = Modifier.size(34.dp),
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Rounded.SkipNext, "下一首", tint = MR.Ink2, modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}

/**
 * Left-swipe page: full synced lyrics. The active line is pinned to the exact
 * vertical center of the screen; the page title shows the song name; tapping any
 * line seeks playback to that line's timestamp.
 */
@Composable
private fun LyricsPage(
    lyrics: List<LyricLine>,
    positionMs: Long,
    title: String,
    artist: String,
    onSeek: (Long) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to MR.Black.copy(alpha = 0.32f),
                    0.5f to MR.Black.copy(alpha = 0.55f),
                    1f to MR.Black.copy(alpha = 0.32f),
                )
            ),
    ) {
        // Full-height list → its own center is the screen center.
        if (lyrics.isEmpty()) {
            Text(
                "暂无歌词",
                color = MR.Muted,
                fontSize = 15.sp,
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            LyricView(
                lines = lyrics,
                positionMs = positionMs,
                onLineClick = { onSeek(it.timeMs) },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Song name + artist, top-left over a short fade so they stay readable.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(130.dp)
                .background(Brush.verticalGradient(listOf(MR.Black.copy(alpha = 0.72f), Color.Transparent))),
        )
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .displayCutoutPadding()
                .padding(start = 24.dp, end = 24.dp, top = 12.dp),
        ) {
            Text(
                text = title,
                color = MR.Ink,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (artist.isNotBlank()) {
                Text(
                    text = artist,
                    color = MR.Muted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/** Left-swipe page: aggregated console. Everything that used to sit in the top bar lives here. */
/**
 * Stripped-down full-screen mode: only the particle cover, the active lyric line, the song
 * name / artist and prev / next. The chrome (title, artist, prev-next, exit) fades away after a
 * few seconds of inactivity for maximum immersion; a tap brings it back. Back exits.
 */
@Composable
private fun ImmersiveOverlay(
    track: Track?,
    lyrics: List<LyricLine>,
    positionMs: Long,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onExit: () -> Unit,
) {
    var chromeVisible by remember { mutableStateOf(true) }
    LaunchedEffect(chromeVisible) {
        if (chromeVisible) {
            delay(4500)
            chromeVisible = false
        }
    }
    val chromeAlpha by animateFloatAsState(if (chromeVisible) 1f else 0f, label = "immersiveChrome")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { chromeVisible = !chromeVisible } },
    ) {
        // Active lyric line — the ambient focus, always visible and larger than normal.
        SingleLyricView(
            lines = lyrics,
            positionMs = positionMs,
            maxFontSize = 24.sp,
            translationMaxFontSize = 16.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 150.dp)
                .fillMaxWidth(),
        )

        // Top chrome: song name + artist + exit.
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .statusBarsPadding()
                .displayCutoutPadding()
                .padding(horizontal = 22.dp, vertical = 12.dp)
                .alpha(chromeAlpha),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    track?.title ?: "", color = MR.Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    track?.displayArtist ?: "", color = MR.Muted, fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onExit, enabled = chromeVisible) {
                Icon(Icons.Rounded.FullscreenExit, "退出沉浸", tint = MR.Ink2, modifier = Modifier.size(24.dp))
            }
        }

        // Bottom chrome: prev / play-pause / next.
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 40.dp, start = 40.dp, end = 40.dp)
                .alpha(chromeAlpha),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrev, enabled = chromeVisible) {
                Icon(Icons.Rounded.SkipPrevious, "上一首", tint = MR.Ink, modifier = Modifier.size(38.dp))
            }
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(MR.Accent, MR.ChillMint)))
                    .clickable(enabled = chromeVisible, onClick = onTogglePlay),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    tint = MR.Black,
                    modifier = Modifier.size(34.dp),
                )
            }
            IconButton(onClick = onNext, enabled = chromeVisible) {
                Icon(Icons.Rounded.SkipNext, "下一首", tint = MR.Ink, modifier = Modifier.size(38.dp))
            }
        }
    }
}

@Composable
private fun ConsolePage(
    presetIndex: Int,
    onSelectPreset: (Int) -> Unit,
    playMode: PlayMode,
    onCyclePlayMode: () -> Unit,
    sleepRemainingSec: Int,
    onEnterImmersive: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenSleep: () -> Unit,
    onOpenInfo: () -> Unit,
    onOpenSystemEq: () -> Unit,
    onClose: () -> Unit,
) {
    val (modeIcon, modeLabel) = when (playMode) {
        PlayMode.LIST -> Icons.Rounded.Repeat to "列表循环"
        PlayMode.SHUFFLE -> Icons.Rounded.Shuffle to "随机播放"
        PlayMode.SINGLE -> Icons.Rounded.RepeatOne to "单曲循环"
    }
    Box(modifier = Modifier.fillMaxSize().background(MR.Black.copy(alpha = 0.62f))) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .displayCutoutPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 16.dp),
        ) {
            Text("控制台", color = MR.Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(18.dp))

            Text("视觉预设", color = MR.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            PresetSelector(selected = presetIndex, onSelect = onSelectPreset)
            Spacer(Modifier.height(20.dp))

            ConsoleTile(Icons.Rounded.Fullscreen, "沉浸模式", "只留封面 · 歌词 · 切歌，闲置自动隐藏", onEnterImmersive)
            ConsoleTile(modeIcon, "播放模式", modeLabel, onCyclePlayMode)
            ConsoleTile(Icons.AutoMirrored.Rounded.QueueMusic, "播放队列", "查看与切换当前队列", onOpenQueue)
            ConsoleTile(
                Icons.Rounded.Bedtime,
                "睡眠定时",
                if (sleepRemainingSec > 0) "剩余 %d:%02d".format(sleepRemainingSec / 60, sleepRemainingSec % 60) else "定时停止播放",
                onOpenSleep,
                accentSubtitle = sleepRemainingSec > 0,
            )
            ConsoleTile(Icons.Rounded.Info, "音频信息 · 屏幕常亮", "格式 / 采样率 / 保持常亮", onOpenInfo)
            ConsoleTile(Icons.Rounded.GraphicEq, "系统音效", "调起设备自带均衡器", onOpenSystemEq)
            Spacer(Modifier.height(8.dp))
            ConsoleTile(Icons.Rounded.KeyboardArrowDown, "收起播放页", "返回曲库", onClose)
        }
    }
}

@Composable
private fun ConsoleTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    accentSubtitle: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MR.Ink2, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = MR.Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = if (accentSubtitle) MR.Accent else MR.Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { i ->
            val active = i == current
            Box(
                modifier = Modifier
                    .height(6.dp)
                    .width(if (active) 18.dp else 6.dp)
                    .clip(CircleShape)
                    .background(if (active) MR.Ink else MR.Ink.copy(alpha = 0.32f)),
            )
        }
    }
}

@Composable
private fun SeekRow(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
    var scrub by remember { mutableStateOf<Float?>(null) }
    val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val value = scrub ?: fraction

    val scrubbing = scrub != null
    val thumbSize = if (scrubbing) 15.dp else 11.dp

    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp)
                .pointerInput(durationMs) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        fun frac(x: Float) = (x / size.width).coerceIn(0f, 1f)
                        scrub = frac(down.position.x)
                        while (true) {
                            val event = awaitPointerEvent()
                            val ch = event.changes.firstOrNull() ?: break
                            if (!ch.pressed) break
                            scrub = frac(ch.position.x)
                            ch.consume()
                        }
                        scrub?.let { onSeek((it * durationMs).toLong()) }
                        scrub = null
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            val trackW = maxWidth
            Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(MR.Hair2))
            Box(Modifier.fillMaxWidth(value).height(3.dp).clip(CircleShape).background(MR.Accent))
            Box(
                Modifier
                    .offset(x = (trackW - thumbSize) * value)
                    .size(thumbSize)
                    .clip(CircleShape)
                    .background(MR.Accent),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(formatTime((value * durationMs).toLong()), color = MR.Muted, fontSize = 11.sp)
            Text(formatTime(durationMs), color = MR.Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun PresetSelector(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start),
    ) {
        PRESETS.forEachIndexed { i, p ->
            val active = i == selected
            Text(
                text = p.label,
                color = if (active) MR.Black else MR.Muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (active) MR.Accent else MR.Ink.copy(alpha = 0.10f))
                    .clickable { onSelect(i) }
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            )
        }
    }
}

private fun openSystemEq(context: Context) {
    val sid = AudioEffects.sessionId
    if (sid == 0) return
    try {
        val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sid)
            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
            putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
        }
        context.startActivity(intent)
    } catch (_: Throwable) {
    }
}
