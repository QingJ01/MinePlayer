package com.mine.player.ui.screens

import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Tablet
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.launch
import com.mine.player.BuildConfig
import com.mine.player.audio.AudioEffects
import com.mine.player.data.Settings
import com.mine.player.data.UpdateChecker
import com.mine.player.ui.theme.AccentOptions
import com.mine.player.ui.theme.LocalPalette
import com.mine.player.ui.theme.onAccentFor
import kotlin.math.roundToInt

private enum class SettingsPage(val title: String) {
    ROOT("设置"), APPEARANCE("外观"), VISUAL("视觉"), LIBRARY("媒体来源"), EQ("音频"),
    NOTIFICATION("通知"), CAR("车机模式"), TABLET("平板模式"), ABOUT("关于"), LICENSES("开源与致谢")
}

/** Where the header/back gesture returns from a given page (sub-pages return to their parent). */
private fun SettingsPage.parent(): SettingsPage = when (this) {
    SettingsPage.LICENSES -> SettingsPage.ABOUT
    else -> SettingsPage.ROOT
}

@Composable
fun SettingsScreen(
    settings: Settings,
    onToggleFollowSystem: (Boolean) -> Unit,
    onToggleDark: (Boolean) -> Unit,
    onToggleImmersive: (Boolean) -> Unit,
    onAccentFollowSystem: (Boolean) -> Unit,
    onAccentColor: (Int) -> Unit,
    onDefaultPreset: (Int) -> Unit,
    onDefaultLyric: (Int) -> Unit,
    onSensitivity: (Float) -> Unit,
    onFlowStrength: (Float) -> Unit,
    onMinDuration: (Int) -> Unit,
    onRescan: () -> Unit,
    scanning: Boolean,
    trackCount: Int,
    onToggleMediaStore: (Boolean) -> Unit,
    onAddCustomFolder: (String) -> Unit,
    onRemoveCustomFolder: (String) -> Unit,
    onAddBlockedFolder: (String) -> Unit,
    onRemoveBlockedFolder: (String) -> Unit,
    playbackSpeed: Float,
    onPlaybackSpeed: (Float) -> Unit,
    outputSampleRate: Int,
    onOutputSampleRate: (Int) -> Unit,
    onExclusiveFocus: (Boolean) -> Unit,
    onReplayGain: (Boolean) -> Unit,
    onFadeInOut: (Boolean) -> Unit,
    onGapless: (Boolean) -> Unit,
    onToggleNotification: (Boolean) -> Unit,
    onToggleCloseButton: (Boolean) -> Unit,
    onToggleCar: (Boolean) -> Unit,
    onForceLandscape: (Boolean) -> Unit,
    onCoverLyrics: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val p = LocalPalette.current
    var page by remember { mutableStateOf(SettingsPage.ROOT) }

    BackHandler(enabled = page != SettingsPage.ROOT) { page = page.parent() }

    Column(modifier = Modifier.fillMaxSize().background(p.bg).statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { if (page == SettingsPage.ROOT) onBack() else page = page.parent() }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = p.ink)
            }
            Text(page.title, color = p.ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }

        Column(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = 640.dp) // keep settings readable (centered) on tablets / landscape
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding().padding(horizontal = 16.dp),
        ) {
            when (page) {
                SettingsPage.ROOT -> {
                    Spacer(Modifier.height(8.dp))
                    Card {
                        NavRow(Icons.Rounded.Palette, "外观", "深色 / 浅色、沉浸、强调色") { page = SettingsPage.APPEARANCE }
                        InsetDivider()
                        NavRow(Icons.Rounded.AutoAwesome, "视觉", "默认预设、歌词模式、灵敏度") { page = SettingsPage.VISUAL }
                        InsetDivider()
                        NavRow(Icons.Rounded.LibraryMusic, "媒体来源", "扫描目录、媒体库、屏蔽") { page = SettingsPage.LIBRARY }
                        InsetDivider()
                        NavRow(Icons.Rounded.GraphicEq, "音频", "输出、均衡器、速度") { page = SettingsPage.EQ }
                        InsetDivider()
                        NavRow(Icons.Rounded.Notifications, "通知", "通知栏与锁屏控件") { page = SettingsPage.NOTIFICATION }
                        InsetDivider()
                        NavRow(Icons.Rounded.DirectionsCar, "车机模式", "Android Auto 车载浏览") { page = SettingsPage.CAR }
                        InsetDivider()
                        NavRow(Icons.Rounded.Tablet, "平板模式", "锁定横屏、封面歌词") { page = SettingsPage.TABLET }
                        InsetDivider()
                        NavRow(Icons.Rounded.Info, "关于", "版本、更新检测、开源致谢") { page = SettingsPage.ABOUT }
                    }
                }

                SettingsPage.APPEARANCE -> AppearancePage(
                    settings, onToggleFollowSystem, onToggleDark, onToggleImmersive,
                    onAccentFollowSystem, onAccentColor,
                )
                SettingsPage.VISUAL -> VisualPage(settings, onDefaultPreset, onDefaultLyric, onSensitivity, onFlowStrength)
                SettingsPage.LIBRARY -> MediaSourcePage(
                    settings, onRescan, scanning, trackCount, onToggleMediaStore, onMinDuration,
                    onAddCustomFolder, onRemoveCustomFolder, onAddBlockedFolder, onRemoveBlockedFolder,
                )
                SettingsPage.EQ -> AudioPage(
                    settings, playbackSpeed, onPlaybackSpeed, outputSampleRate, onOutputSampleRate,
                    onExclusiveFocus, onReplayGain, onFadeInOut, onGapless,
                )
                SettingsPage.NOTIFICATION -> NotificationPage(settings, onToggleNotification, onToggleCloseButton)
                SettingsPage.CAR -> CarPage(settings, onToggleCar)
                SettingsPage.TABLET -> TabletPage(settings, onForceLandscape, onCoverLyrics)
                SettingsPage.ABOUT -> AboutPage(onOpenLibraries = { page = SettingsPage.LICENSES })
                SettingsPage.LICENSES -> LicensesPage()
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

// ---- Pages ------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearancePage(
    settings: Settings,
    onToggleFollowSystem: (Boolean) -> Unit,
    onToggleDark: (Boolean) -> Unit,
    onToggleImmersive: (Boolean) -> Unit,
    onAccentFollowSystem: (Boolean) -> Unit,
    onAccentColor: (Int) -> Unit,
) {
    val p = LocalPalette.current
    Section("主题")
    Card {
        SwitchRow("跟随系统", "自动跟随系统的深色 / 浅色", settings.followSystemTheme, onToggleFollowSystem)
        InsetDivider(inset = false)
        SwitchRow(
            "深色主题",
            if (settings.followSystemTheme) "关闭「跟随系统」后可手动切换" else null,
            settings.darkTheme,
            onToggleDark,
            enabled = !settings.followSystemTheme,
        )
        InsetDivider(inset = false)
        SwitchRow("沉浸模式", "隐藏顶部状态栏，全屏浏览；下滑可临时唤出", settings.immersiveMode, onToggleImmersive)
    }

    Section("强调色")
    Card {
        SwitchRow("跟随系统取色", "使用系统主题色（需 Android 12+）", settings.accentFollowSystem, onAccentFollowSystem)
        val dimmed = settings.accentFollowSystem
        InsetDivider(inset = false)
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AccentOptions.forEach { c ->
                val argb = c.toArgb()
                val active = !dimmed && argb == settings.accentColor
                Box(
                    modifier = Modifier.size(34.dp).clip(CircleShape)
                        .background(if (dimmed) c.copy(alpha = 0.35f) else c)
                        .border(if (active) 2.dp else 0.dp, p.ink, CircleShape)
                        .clickable(enabled = !dimmed) { onAccentColor(argb) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (active) Icon(Icons.Rounded.Check, null, tint = onAccentFor(c), modifier = Modifier.size(20.dp))
                }
            }
        }
        // Custom color band only makes sense when NOT following the system color, so hide it then.
        if (!dimmed) {
            InsetDivider(inset = false)
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("自定义", color = p.muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Box(
                        modifier = Modifier.size(22.dp).clip(CircleShape).background(Color(settings.accentColor)),
                    )
                }
                Spacer(Modifier.height(10.dp))
                HueBar(onPick = onAccentColor)
                Text("点击色带选取任意颜色", color = p.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

/** Rainbow bar: tap anywhere to pick that hue as a vivid accent color. */
@Composable
private fun HueBar(onPick: (Int) -> Unit) {
    val spectrum = remember { (0..12).map { Color.hsv((it * 30f) % 360f, 0.82f, 0.95f) } }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(Brush.horizontalGradient(spectrum))
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val frac = (offset.x / size.width).coerceIn(0f, 1f)
                    onPick(Color.hsv(frac * 360f, 0.82f, 0.95f).toArgb())
                }
            },
    )
}

@Composable
private fun VisualPage(
    settings: Settings,
    onDefaultPreset: (Int) -> Unit,
    onDefaultLyric: (Int) -> Unit,
    onSensitivity: (Float) -> Unit,
    onFlowStrength: (Float) -> Unit,
) {
    Section("默认预设")
    Card { ChipBlock(listOf("丝绸", "黑胶", "星球", "隧道"), settings.defaultPreset, onDefaultPreset) }

    Section("默认歌词模式")
    Card { ChipBlock(listOf("单句", "整屏", "封面"), settings.defaultLyricMode, onDefaultLyric) }

    Section("律动强度")
    Card {
        SliderBlock("强度", settings.sensitivity.roundToInt().toString(), settings.sensitivity, 3f..18f, onSensitivity)
    }

    Section("粒子流动幅度")
    Card {
        SliderBlock(
            "流动幅度",
            "${(settings.flowStrength * 10).roundToInt() / 10f}×",
            settings.flowStrength,
            0.6f..3f,
            onFlowStrength,
        )
    }
}

@Composable
private fun MediaSourcePage(
    settings: Settings,
    onRescan: () -> Unit,
    scanning: Boolean,
    trackCount: Int,
    onToggleMediaStore: (Boolean) -> Unit,
    onMinDuration: (Int) -> Unit,
    onAddCustomFolder: (String) -> Unit,
    onRemoveCustomFolder: (String) -> Unit,
    onAddBlockedFolder: (String) -> Unit,
    onRemoveBlockedFolder: (String) -> Unit,
) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val pickCustom = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        treeUriToPath(uri)?.let(onAddCustomFolder)
    }
    val pickBlocked = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        treeUriToPath(uri)?.let(onAddBlockedFolder)
    }

    Card {
        ActionRow(
            Icons.Rounded.Refresh,
            "开始扫描",
            if (scanning) "扫描中…" else "已收录 $trackCount 首 · 点此重新扫描",
            onClick = onRescan,
            busy = scanning,
        )
    }

    Section("媒体库")
    Card {
        SwitchRow(
            "使用 Android 媒体库",
            if (settings.customFolders.isNotEmpty()) "已按下方自定义文件夹限定，此开关暂不影响"
            else if (settings.useMediaStore) "扫描系统媒体库中的全部音乐"
            else "未添加自定义文件夹时曲库为空",
            settings.useMediaStore, onToggleMediaStore,
        )
    }

    Section("自定义文件夹")
    Card {
        if (settings.customFolders.isEmpty()) {
            EmptyHint("添加后，曲库将只包含这些目录里的音乐")
        } else {
            settings.customFolders.sorted().forEachIndexed { i, path ->
                if (i > 0) InsetDivider(inset = false)
                FolderRow(path) { onRemoveCustomFolder(path) }
            }
            InsetDivider(inset = false)
        }
        ActionRow(Icons.Rounded.CreateNewFolder, "添加自定义文件夹", "选择一个音乐目录") { pickCustom.launch(null) }
    }

    Section("被屏蔽的文件夹")
    Card {
        if (settings.blockedFolders.isEmpty()) {
            EmptyHint("这里的文件夹不会出现在曲库中")
        } else {
            settings.blockedFolders.sorted().forEachIndexed { i, path ->
                if (i > 0) InsetDivider(inset = false)
                FolderRow(path) { onRemoveBlockedFolder(path) }
            }
            InsetDivider(inset = false)
        }
        ActionRow(Icons.Rounded.Block, "添加屏蔽文件夹", "选择要排除的目录") { pickBlocked.launch(null) }
    }

    Section("过滤")
    Card {
        SliderBlock("不扫描短于", "${settings.minDurationSec}s", settings.minDurationSec.toFloat(), 0f..60f) {
            onMinDuration(it.roundToInt())
        }
    }

    Section("权限")
    Card {
        ActionRow(Icons.AutoMirrored.Rounded.OpenInNew, "管理存储权限", "打开系统权限设置") { openStoragePermission(context) }
    }
    Text(
        "选择目录用系统文件选择器授权；扫描基于系统媒体库，若新文件没出现，先在文件管理器里让系统扫描到，再回来「开始扫描」",
        color = p.muted, fontSize = 12.sp,
        modifier = Modifier.padding(start = 6.dp, top = 10.dp),
    )
}

@Composable
private fun FolderRow(path: String, onRemove: () -> Unit) {
    val p = LocalPalette.current
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Folder, null, tint = p.ink2, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(path, color = p.ink2, fontSize = 13.sp, maxLines = 2, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        Icon(
            Icons.Rounded.Close, "移除", tint = p.muted,
            modifier = Modifier.size(20.dp).clip(CircleShape).clickable(onClick = onRemove).padding(1.dp),
        )
    }
}

@Composable
private fun EmptyHint(text: String) {
    val p = LocalPalette.current
    Text(text, color = p.muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp))
}

@Composable
private fun NotificationPage(
    settings: Settings,
    onToggleNotification: (Boolean) -> Unit,
    onToggleCloseButton: (Boolean) -> Unit,
) {
    Section("播放通知")
    Card {
        SwitchRow(
            "显示播放通知",
            "在通知栏 / 锁屏显示封面与播放控件",
            settings.showNotification, onToggleNotification,
        )
        InsetDivider(inset = false)
        SwitchRow(
            "显示关闭按钮",
            "在通知里加一个关闭键，点击停止播放并移除通知",
            settings.showCloseButton, onToggleCloseButton,
        )
    }
    val p = LocalPalette.current
    Text(
        "关闭通知后 App 退到后台时，系统可能会限制或停止播放；需要后台常驻播放请保持开启",
        color = p.muted, fontSize = 12.sp,
        modifier = Modifier.padding(start = 6.dp, top = 10.dp),
    )
}

@Composable
private fun CarPage(settings: Settings, onToggleCar: (Boolean) -> Unit) {
    val p = LocalPalette.current
    Section("车机 · Android Auto")
    Card {
        SwitchRow(
            "车载浏览",
            "连接车机（Android Auto）后，在车屏上按 歌曲 / 专辑 / 艺术家 浏览并播放本地曲库；关闭后车机看不到曲库",
            settings.carBrowsingEnabled, onToggleCar,
        )
    }
    Text(
        "使用方法：\n" +
            "1. 手机安装 Android Auto，进其设置连点版本号开启开发者选项 → 打开「未知来源」，MinePlayer 才会在未上架时出现\n" +
            "2. 用数据线连接车机，或用电脑上的 DHU 模拟车机\n" +
            "3. 在车屏媒体应用里选择 MinePlayer 即可浏览播放",
        color = p.muted, fontSize = 12.sp, lineHeight = 18.sp,
        modifier = Modifier.padding(start = 6.dp, top = 12.dp),
    )
}

@Composable
private fun TabletPage(
    settings: Settings,
    onForceLandscape: (Boolean) -> Unit,
    onCoverLyrics: (Boolean) -> Unit,
) {
    val p = LocalPalette.current
    Section("平板 / 横屏")
    Card {
        SwitchRow(
            "锁定横屏",
            "始终以横屏（平板）布局运行，适合平板或横置的车机 / 支架",
            settings.forceLandscape, onForceLandscape,
        )
        InsetDivider(inset = false)
        SwitchRow(
            "封面歌词",
            "横屏播放页在左侧封面中央显示当前歌词",
            settings.coverLyrics, onCoverLyrics,
        )
    }
    Text(
        "横屏 / 大屏下：左侧常驻导航栏、专辑与曲库多列、播放页封面在左控制在右。竖屏仍是手机布局。",
        color = p.muted, fontSize = 12.sp, lineHeight = 18.sp,
        modifier = Modifier.padding(start = 6.dp, top = 12.dp),
    )
}

@Composable
private fun AboutPage(onOpenLibraries: () -> Unit) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appIcon = remember {
        context.packageManager.getApplicationIcon(context.packageName).toBitmap().asImageBitmap()
    }
    fun open(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    // GitHub-release update check (auto-runs once, tap to retry / open the release).
    var checking by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var latestUrl by remember { mutableStateOf<String?>(null) }
    fun checkUpdate(force: Boolean = false) {
        if (checking) return
        checking = true; status = null; latestUrl = null
        scope.launch {
            val r = UpdateChecker.check(force)
            checking = false
            when (r) {
                is UpdateChecker.Result.Failed -> status = "检查失败，点按重试"
                is UpdateChecker.Result.NoReleases -> status = "已是最新版本"
                is UpdateChecker.Result.Found -> {
                    if (UpdateChecker.isNewer(r.release.tag, BuildConfig.VERSION_NAME)) {
                        status = "发现新版本 ${r.release.tag}，点按下载"; latestUrl = r.release.url
                    } else {
                        status = "已是最新版本"
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { checkUpdate() }

    Section("关于")
    Card {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(bitmap = appIcon, contentDescription = null, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(15.dp)))
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("MinePlayer", color = p.ink, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text("版本 ${BuildConfig.VERSION_NAME}", color = p.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 3.dp))
            }
        }
        InsetDivider(inset = false)
        InfoRow("简介", "原生 Android 本地音乐播放器 · 沉浸粒子视觉，音乐全部来自本机")
    }

    Section("更新")
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { if (latestUrl != null) open(latestUrl!!) else checkUpdate(force = true) }
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text("检查更新", color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(
                    when {
                        checking -> "检查中…"
                        status != null -> status!!
                        else -> "当前 v${BuildConfig.VERSION_NAME}"
                    },
                    color = if (latestUrl != null) p.accent else p.muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            when {
                checking -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = p.accent)
                latestUrl != null -> Icon(Icons.Rounded.SystemUpdate, "下载新版本", tint = p.accent, modifier = Modifier.size(20.dp))
                else -> Icon(Icons.Rounded.Refresh, "重新检查", tint = p.muted, modifier = Modifier.size(20.dp))
            }
        }
    }

    Section("开源与致谢")
    Card {
        NavRow(Icons.Rounded.Code, "开源与致谢", "原项目与使用的开源库") { onOpenLibraries() }
    }
}

@Composable
private fun LicensesPage() {
    val p = LocalPalette.current
    val context = LocalContext.current
    fun open(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    Section("原项目")
    Card {
        LinkRow(
            "Mineradio",
            "粒子视觉风格移植自此项目（Web / Electron 版）",
            "github.com/XxHuberrr/Mineradio",
        ) { open("https://github.com/XxHuberrr/Mineradio") }
    }

    Section("开源库")
    Card {
        LinkRow("Jetpack Compose · Material 3", "界面框架", "developer.android.com/jetpack/compose") {
            open("https://developer.android.com/jetpack/compose")
        }
        InsetDivider(inset = false)
        LinkRow("AndroidX Media3（ExoPlayer / Session）", "音频播放与媒体会话", "github.com/androidx/media") {
            open("https://github.com/androidx/media")
        }
        InsetDivider(inset = false)
        LinkRow("AndroidX Lifecycle · Activity · Core-KTX", "生命周期与基础组件", "developer.android.com/jetpack/androidx") {
            open("https://developer.android.com/jetpack/androidx")
        }
        InsetDivider(inset = false)
        LinkRow("DataStore Preferences", "设置持久化", "developer.android.com/topic/libraries/architecture/datastore") {
            open("https://developer.android.com/topic/libraries/architecture/datastore")
        }
        InsetDivider(inset = false)
        LinkRow("Kotlin Coroutines", "异步与数据流", "github.com/Kotlin/kotlinx.coroutines") {
            open("https://github.com/Kotlin/kotlinx.coroutines")
        }
        InsetDivider(inset = false)
        LinkRow("Guava", "ListenableFuture（媒体会话异步）", "github.com/google/guava") {
            open("https://github.com/google/guava")
        }
        InsetDivider(inset = false)
        LinkRow("Material Icons Extended", "图标集", "fonts.google.com/icons") {
            open("https://fonts.google.com/icons")
        }
    }
    Text(
        "以上开源库均以各自的开源许可证发布（多为 Apache-2.0）。粒子视觉引擎基于 OpenGL ES 2.0 / GLSL 自研。",
        color = p.muted, fontSize = 12.sp, lineHeight = 18.sp,
        modifier = Modifier.padding(start = 6.dp, top = 12.dp, end = 6.dp),
    )
}

@Composable
private fun LinkRow(title: String, subtitle: String, host: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = p.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            Text(host, color = p.accent, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Icon(Icons.Rounded.Link, "打开链接", tint = p.muted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun AudioPage(
    settings: Settings,
    playbackSpeed: Float,
    onPlaybackSpeed: (Float) -> Unit,
    outputSampleRate: Int,
    onOutputSampleRate: (Int) -> Unit,
    onExclusiveFocus: (Boolean) -> Unit,
    onReplayGain: (Boolean) -> Unit,
    onFadeInOut: (Boolean) -> Unit,
    onGapless: (Boolean) -> Unit,
) {
    val p = LocalPalette.current
    val context = LocalContext.current

    Section("音频输出")
    Card {
        SwitchRow("不与其他应用一起播放", "请求独占音频焦点，不与其他应用同时出声；部分设备的系统设置可能使其失效", settings.exclusiveFocus, onExclusiveFocus)
        InsetDivider(inset = false)
        SwitchRow("音量平衡", "读取 ReplayGain 标签，按需衰减音量使各曲目响度一致；切歌后生效", settings.replayGain, onReplayGain)
        InsetDivider(inset = false)
        SwitchRow("播放暂停时淡入淡出", "暂停 / 播放时对音量做短暂渐变，过渡更柔和", settings.fadeInOut, onFadeInOut)
        InsetDivider(inset = false)
        SwitchRow("无间隙播放", "裁剪曲目首尾静音，让现场 / 概念专辑、DJ Set 衔接更连贯", settings.gapless, onGapless)
    }

    Section("输出采样频率")
    Card {
        ChipBlockRaw(
            options = listOf(0 to "跟随音源", 44100 to "44.1k", 48000 to "48k", 96000 to "96k", 192000 to "192k"),
            isActive = { it == outputSampleRate },
            onSelect = onOutputSampleRate,
        )
        Text(
            "改变后下一首生效；多数设备不支持 96k 以上，强制过高可能导致播放异常",
            color = p.muted, fontSize = 12.sp,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
        )
    }

    Section("播放速度")
    Card {
        ChipBlockRaw(
            options = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).map { it to "${it}x" },
            isActive = { kotlin.math.abs(it - playbackSpeed) < 0.001f },
            onSelect = onPlaybackSpeed,
        )
    }

    Section("系统音效")
    Card {
        ActionRow(Icons.Rounded.GraphicEq, "系统均衡器", "调起设备自带音效面板") { openSystemEq(context) }
        InsetDivider(inset = false)
        var compressor by remember { mutableStateOf(AudioEffects.compressorEnabled) }
        SwitchRow("压限器（Compressor）", "响亮的部分更安静，安静的部分更响亮", compressor, onChange = {
            compressor = it; AudioEffects.setCompressor(it)
        })
    }

    Section("均衡器")
    Card {
        if (!AudioEffects.available) {
            Text(
                "开始播放后可调节均衡器",
                color = p.muted, fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            )
        } else {
            var enabled by remember { mutableStateOf(AudioEffects.enabled) }
            val bandCount = remember { AudioEffects.bandCount }
            val range = remember { AudioEffects.levelRange() }
            val presets = remember { AudioEffects.presetNames() }
            var levels by remember { mutableStateOf((0 until bandCount).map { AudioEffects.level(it) }) }

            SwitchRow("启用均衡器", null, enabled, onChange = { enabled = it; AudioEffects.enabled = it })
            if (presets.isNotEmpty()) {
                InsetDivider(inset = false)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    presets.forEachIndexed { i, name ->
                        Chip(name, selected = false) {
                            AudioEffects.usePreset(i)
                            if (!enabled) { enabled = true; AudioEffects.enabled = true }
                            levels = (0 until bandCount).map { AudioEffects.level(it) }
                        }
                    }
                }
            }
            InsetDivider(inset = false)
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                (0 until bandCount).forEach { band ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(formatHz(AudioEffects.centerHz(band)), color = p.ink2, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.width(60.dp))
                        Slider(
                            value = levels.getOrElse(band) { 0 }.toFloat(),
                            onValueChange = { v ->
                                AudioEffects.setLevel(band, v.toInt())
                                levels = levels.toMutableList().also { it[band] = v.toInt() }
                                if (!enabled) { enabled = true; AudioEffects.enabled = true }
                            },
                            valueRange = range.first.toFloat()..range.second.toFloat(),
                            colors = SliderDefaults.colors(thumbColor = p.accent, activeTrackColor = p.accent, inactiveTrackColor = p.hair),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

// ---- Reusable building blocks ----------------------------------------------

@Composable
private fun Section(title: String) {
    val p = LocalPalette.current
    Text(
        title,
        color = p.muted,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.6.sp,
        modifier = Modifier.padding(start = 6.dp, top = 22.dp, bottom = 8.dp),
    )
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.surface),
        content = content,
    )
}

/** Hairline separator between rows in a card. [inset] leaves room for a leading icon. */
@Composable
private fun InsetDivider(inset: Boolean = true) {
    val p = LocalPalette.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (inset) 56.dp else 16.dp)
            .height(1.dp)
            .background(p.hair.copy(alpha = 0.7f)),
    )
}

@Composable
private fun NavRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 60.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = p.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = p.muted, fontSize = 12.sp)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = p.muted, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, subtitle: String?, busy: Boolean = false, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = !busy, onClick = onClick).heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, tint = p.accent)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) Text(subtitle, color = p.muted, fontSize = 12.sp)
        }
        if (busy) {
            CircularProgressIndicator(color = p.accent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val p = LocalPalette.current
    val titleColor = if (enabled) p.ink else p.muted
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { onChange(!checked) }.heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = titleColor, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Text(subtitle, color = p.muted, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = p.onAccent,
                checkedTrackColor = p.accent,
                uncheckedTrackColor = p.surfaceAlt,
                uncheckedBorderColor = p.hair,
            ),
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val p = LocalPalette.current
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(label, color = p.muted, fontSize = 14.sp, modifier = Modifier.width(56.dp))
        Text(value, color = p.ink2, fontSize = 14.sp, modifier = Modifier.weight(1f))
    }
}

/** Leading icon inside a subtle rounded tile. */
@Composable
private fun IconTile(icon: ImageVector, tint: androidx.compose.ui.graphics.Color = LocalPalette.current.ink2) {
    val p = LocalPalette.current
    Box(
        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(p.surfaceAlt),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ChipBlock(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEachIndexed { i, label -> Chip(label, selected = i == selected) { onSelect(i) } }
    }
}

@Composable
private fun <T> ChipBlockRaw(options: List<Pair<T, String>>, isActive: (T) -> Boolean, onSelect: (T) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) -> Chip(label, selected = isActive(value)) { onSelect(value) } }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Text(
        text = label,
        color = if (selected) p.onAccent else p.ink2,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) p.accent else p.surfaceAlt)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun SliderBlock(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    val p = LocalPalette.current
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Tune, null, tint = p.ink2, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(label, color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(valueText, color = p.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = p.accent, activeTrackColor = p.accent, inactiveTrackColor = p.hair),
        )
    }
}

// ---- helpers ----------------------------------------------------------------

/** Convert a SAF tree Uri (e.g. content://…/tree/primary:音乐) to a filesystem path for filtering. */
private fun treeUriToPath(uri: Uri?): String? {
    if (uri == null) return null
    val docId = try { DocumentsContract.getTreeDocumentId(uri) } catch (_: Throwable) { return null }
    val split = docId.split(":", limit = 2)
    val type = split.getOrNull(0) ?: return null
    val rel = split.getOrNull(1).orEmpty()
    val base = if (type.equals("primary", ignoreCase = true)) "/storage/emulated/0" else "/storage/$type"
    return if (rel.isEmpty()) base else "$base/$rel"
}

private fun openStoragePermission(context: Context) {
    val pkgUri = Uri.parse("package:${context.packageName}")
    val primary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Intent(AndroidSettings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, pkgUri)
    } else {
        Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri)
    }
    try {
        context.startActivity(primary)
    } catch (_: Throwable) {
        try {
            context.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri))
        } catch (_: Throwable) {
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

private fun formatHz(hz: Int): String = if (hz >= 1000) "${hz / 1000}kHz" else "${hz}Hz"
