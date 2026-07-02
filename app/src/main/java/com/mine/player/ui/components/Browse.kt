package com.mine.player.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.mine.player.audio.QualityTier
import com.mine.player.audio.Track
import com.mine.player.audio.qualityTier
import com.mine.player.ui.theme.LocalPalette

enum class BrowseTab(val title: String, val icon: ImageVector, val color: Color) {
    SONGS("歌曲", Icons.Rounded.MusicNote, Color(0xFF3DD16E)),
    ALBUMS("专辑", Icons.Rounded.Album, Color(0xFFEF5DA8)),
    ARTISTS("艺术家", Icons.Rounded.Person, Color(0xFFF2A03D)),
    PLAYLISTS("歌单", Icons.AutoMirrored.Rounded.QueueMusic, Color(0xFF3D8BF2)),
}

@Composable
fun BrowseHeader(
    title: String,
    onMenu: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val p = LocalPalette.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            onBack != null -> IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = p.ink) }
            onMenu != null -> IconButton(onClick = onMenu) { Icon(Icons.Rounded.Menu, "菜单", tint = p.ink) }
        }
        Text(
            title,
            color = p.ink,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
        trailing()
    }
}

@Composable
fun TrackListItem(track: Track, isCurrent: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AlbumThumb(track = track, sizeDp = 46.dp, corner = 11.dp)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    track.title,
                    color = if (isCurrent) p.accent else p.ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                QualityBadge(track.qualityTier(), Modifier.padding(start = 6.dp))
            }
            Text(track.displayArtist, color = p.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Text(formatTime(track.durationMs), color = p.muted, fontSize = 12.sp)
    }
}

@Composable
fun AppDrawer(
    selected: BrowseTab,
    onSelect: (BrowseTab) -> Unit,
    onStats: () -> Unit,
    onSettings: () -> Unit,
) {
    val p = LocalPalette.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(p.surface)
            .padding(vertical = 24.dp),
    ) {
        Text(
            "MINEPLAYER",
            color = p.ink,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 4.sp,
            modifier = Modifier.padding(start = 24.dp, bottom = 20.dp),
        )
        BrowseTab.values().forEach { tab ->
            DrawerRow(tab.icon, tab.title, tab.color, selected = tab == selected) { onSelect(tab) }
        }
        Spacer(Modifier.size(12.dp))
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).size(1.dp).background(p.hair))
        Spacer(Modifier.size(12.dp))
        DrawerRow(Icons.Rounded.BarChart, "听歌统计", Color(0xFF2BB8A3), selected = false, onClick = onStats)
        DrawerRow(Icons.Rounded.Settings, "设置", Color(0xFF60636B), selected = false, onClick = onSettings)
    }
}

@Composable
private fun DrawerRow(icon: ImageVector, title: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) p.surfaceAlt else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = color, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(16.dp))
        Text(title, color = p.ink, fontSize = 16.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

/** Persistent left navigation rail for large / landscape screens (replaces the modal drawer). */
@Composable
fun AppNavRail(
    selected: BrowseTab,
    onSelect: (BrowseTab) -> Unit,
    onStats: () -> Unit,
    onSettings: () -> Unit,
) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val appIcon = remember {
        context.packageManager.getApplicationIcon(context.packageName).toBitmap().asImageBitmap()
    }
    // Scrollable: on short landscape screens all entries stay reachable instead of being clipped.
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(80.dp)
            .background(p.surface)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            bitmap = appIcon,
            contentDescription = "MinePlayer",
            modifier = Modifier
                .padding(bottom = 8.dp)
                .size(40.dp)
                .clip(RoundedCornerShape(11.dp)),
        )
        BrowseTab.values().forEach { tab ->
            NavRailItem(tab.icon, tab.title, tab.color, selected = tab == selected) { onSelect(tab) }
        }
        Box(modifier = Modifier.padding(vertical = 6.dp).width(30.dp).height(1.dp).background(p.hair))
        NavRailItem(Icons.Rounded.BarChart, "统计", Color(0xFF2BB8A3), selected = false, onClick = onStats)
        NavRailItem(Icons.Rounded.Settings, "设置", Color(0xFF60636B), selected = false, onClick = onSettings)
    }
}

@Composable
private fun NavRailItem(icon: ImageVector, label: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (selected) color.copy(alpha = 0.18f) else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = if (selected) color else p.ink2, modifier = Modifier.size(23.dp)) }
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            color = if (selected) p.ink else p.muted,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

