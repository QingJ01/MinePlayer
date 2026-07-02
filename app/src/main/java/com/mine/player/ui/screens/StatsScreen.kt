package com.mine.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mine.player.audio.Track
import com.mine.player.data.ListenStatsStore
import com.mine.player.ui.components.AlbumThumb
import com.mine.player.ui.components.BrowseHeader
import com.mine.player.ui.theme.LocalPalette

private val Gold = Color(0xFFF3C02B)
private val Silver = Color(0xFFAFB6C2)
private val Bronze = Color(0xFFCB8A5A)

@Composable
fun StatsScreen(tracks: List<Track>, onBack: () -> Unit) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val store = remember { ListenStatsStore(context) }
    var refresh by remember { mutableIntStateOf(0) }
    var showArtists by remember { mutableStateOf(false) }

    val total = remember(refresh) { store.totalPlays() }
    val distinctSongs = remember(refresh) { store.distinctSongs() }
    val distinctArtists = remember(refresh) { store.distinctArtists() }
    val trackById = remember(tracks) { tracks.associateBy { it.id } }
    val topSongs = remember(refresh, tracks) {
        store.topSongs(100).mapNotNull { (id, c) -> trackById[id]?.let { it to c } }
    }
    val topArtists = remember(refresh) { store.topArtists(100) }

    Column(modifier = Modifier.fillMaxSize().background(p.bg).statusBarsPadding()) {
        BrowseHeader("听歌统计", onBack = onBack) {
            if (total > 0) {
                Text(
                    "清空",
                    color = p.muted,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { store.clear(); refresh++ }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }

        // Hero card (fixed)
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Brush.linearGradient(listOf(p.accent.copy(alpha = 0.22f), p.accent.copy(alpha = 0.06f))))
                .padding(horizontal = 22.dp, vertical = 18.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$total", color = p.accent, fontSize = 42.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.width(6.dp))
                Text("次播放", color = p.ink2, fontSize = 15.sp, modifier = Modifier.padding(bottom = 7.dp))
            }
            if (total > 0) {
                Text("$distinctSongs 首歌 · $distinctArtists 位歌手", color = p.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }

        // Songs / Artists toggle (fixed)
        Row(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(p.surfaceAlt)
                .padding(3.dp),
        ) {
            SegItem("歌曲", selected = !showArtists) { showArtists = false }
            SegItem("歌手", selected = showArtists) { showArtists = true }
        }

        LazyColumn(
            modifier = Modifier.widthIn(max = 680.dp).fillMaxSize().align(Alignment.CenterHorizontally),
            contentPadding = PaddingValues(top = 6.dp, bottom = 120.dp),
        ) {
            if (total == 0) {
                item {
                    Text(
                        "还没有听歌记录\n完整听完一首歌(30 秒以上)就会记录",
                        color = p.muted,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 24.dp),
                    )
                }
            } else if (!showArtists) {
                itemsIndexed(topSongs, key = { _, it -> it.first.id }) { i, (t, c) -> SongStatRow(i + 1, t, c) }
            } else {
                itemsIndexed(topArtists, key = { _, it -> it.first }) { i, (name, c) -> ArtistStatRow(i + 1, name, c) }
            }
        }
    }
}

@Composable
private fun RowScope.SegItem(label: String, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Text(
        label,
        color = if (selected) p.onAccent else p.ink2,
        fontSize = 14.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) p.accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
    )
}

@Composable
private fun RankBadge(n: Int) {
    val p = LocalPalette.current
    val medal = when (n) {
        1 -> Gold
        2 -> Silver
        3 -> Bronze
        else -> null
    }
    Box(
        modifier = Modifier
            .size(24.dp)
            .then(if (medal != null) Modifier.clip(CircleShape).background(medal) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$n",
            color = if (medal != null) Color(0xFF20242B) else p.muted,
            fontSize = 13.sp,
            fontWeight = if (medal != null) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
private fun CountPill(count: Int) {
    val p = LocalPalette.current
    Text(
        "$count 次",
        color = p.accent,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(p.surfaceAlt).padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun SongStatRow(rank: Int, track: Track, count: Int) {
    val p = LocalPalette.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RankBadge(rank)
        Spacer(Modifier.width(10.dp))
        AlbumThumb(track = track, sizeDp = 46.dp, corner = 11.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(track.title, color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.displayArtist, color = p.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(10.dp))
        CountPill(count)
    }
}

@Composable
private fun ArtistStatRow(rank: Int, name: String, count: Int) {
    val p = LocalPalette.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RankBadge(rank)
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier.size(46.dp).clip(CircleShape).background(p.surfaceAlt),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Person, null, tint = p.ink2, modifier = Modifier.size(24.dp)) }
        Spacer(Modifier.width(12.dp))
        Text(name, color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        CountPill(count)
    }
}
