package com.mine.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mine.player.audio.PlayMode
import com.mine.player.audio.QualityTier
import com.mine.player.audio.Track
import com.mine.player.audio.qualityTier
import com.mine.player.ui.components.AlbumThumb
import com.mine.player.ui.components.QualityBadge
import com.mine.player.ui.components.formatTime
import com.mine.player.ui.theme.LocalPalette

private enum class SortKey(val label: String) {
    TITLE("标题"), ARTIST("艺术家"), DURATION("时长"), DATE("最近添加")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    tracks: List<Track>,
    currentTrackId: Long?,
    playMode: PlayMode,
    onCyclePlayMode: () -> Unit,
    onTrackClick: (Int) -> Unit,
    onShuffle: () -> Unit,
    onMenu: (() -> Unit)? = null,
    onOpenShelf: () -> Unit,
    onTrackMenu: (Track) -> Unit,
    sortKeyIndex: Int,
    sortAscending: Boolean,
    onSort: (Int, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val p = LocalPalette.current
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    val sortKey = SortKey.values().getOrElse(sortKeyIndex) { SortKey.TITLE }
    val ascending = sortAscending
    var sortMenu by remember { mutableStateOf(false) }

    val filtered = remember(tracks, query, sortKey, ascending) {
        val base = if (query.isBlank()) tracks
        else tracks.filter { it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true) }
        val sorted = when (sortKey) {
            SortKey.TITLE -> base.sortedBy { it.title.lowercase() }
            SortKey.ARTIST -> base.sortedBy { it.displayArtist.lowercase() }
            SortKey.DURATION -> base.sortedBy { it.durationMs }
            SortKey.DATE -> base.sortedBy { it.dateAdded }
        }
        if (ascending) sorted else sorted.reversed()
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Header — icons on the same grid as the toolbar below (consistent 6dp edge insets,
        // play-mode wrapped in an IconButton so the two rows line up on both sides).
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onMenu != null) {
                IconButton(onClick = onMenu) { Icon(Icons.Rounded.Menu, "菜单", tint = p.ink) }
            } else {
                Spacer(Modifier.width(6.dp))
            }
            if (searching) {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = p.ink, fontSize = 18.sp),
                    cursorBrush = SolidColor(p.ink),
                    modifier = Modifier.weight(1f).padding(start = 6.dp),
                    decorationBox = { inner ->
                        if (query.isEmpty()) Text("搜索曲目", color = p.muted, fontSize = 18.sp)
                        inner()
                    },
                )
            } else {
                Text("歌曲", color = p.ink, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(start = 6.dp))
            }
            IconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                Icon(Icons.Rounded.Search, "搜索", tint = p.ink)
            }
            IconButton(onClick = onOpenShelf) {
                Icon(Icons.Rounded.GridView, "歌单架", tint = p.ink)
            }
        }

        // Toolbar: play-mode toggle + count + sort
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val (modeIcon, modeDesc) = when (playMode) {
                PlayMode.LIST -> Icons.Rounded.Repeat to "列表循环"
                PlayMode.SHUFFLE -> Icons.Rounded.Shuffle to "随机播放"
                PlayMode.SINGLE -> Icons.Rounded.RepeatOne to "单曲循环"
            }
            IconButton(onClick = onCyclePlayMode) { Icon(modeIcon, modeDesc, tint = p.ink) }
            Text("${tracks.size} 首", color = p.ink2, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f).padding(start = 6.dp))
            IconButton(onClick = { sortMenu = true }) { Icon(Icons.Rounded.SwapVert, "排序", tint = p.ink) }
        }

        // Adaptive columns: a single list on a phone, two-plus once the screen is wide (landscape / tablet).
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 360.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            if (filtered.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = if (tracks.isEmpty()) "没有扫描到本地音乐" else "没有匹配的曲目",
                        color = p.muted,
                        fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth().padding(40.dp),
                    )
                }
            } else {
                itemsIndexed(filtered, key = { _, t -> t.id }) { _, track ->
                    TrackRow(
                        track = track,
                        isCurrent = track.id == currentTrackId,
                        onClick = { onTrackClick(tracks.indexOf(track)) },
                        onMenu = { onTrackMenu(track) },
                    )
                }
            }
        }

        if (sortMenu) {
            ModalBottomSheet(onDismissRequest = { sortMenu = false }, containerColor = p.surface) {
                Column(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 40.dp)) {
                    Text("排序方式", color = p.ink, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp, start = 4.dp))
                    SortKey.values().forEach { key ->
                        val active = key == sortKey
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onSort(key.ordinal, ascending); sortMenu = false }.padding(horizontal = 8.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(key.label, color = if (active) p.accent else p.ink, fontSize = 16.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
                            if (active) Icon(Icons.Rounded.Check, null, tint = p.accent, modifier = Modifier.size(20.dp))
                        }
                    }
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).padding(vertical = 0.dp).background(p.hair.copy(alpha = 0.5f)))
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onSort(sortKey.ordinal, !ascending) }.padding(horizontal = 8.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (ascending) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward, null, tint = p.ink, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(if (ascending) "升序" else "降序", color = p.ink, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackRow(track: Track, isCurrent: Boolean, onClick: () -> Unit, onMenu: () -> Unit) {
    val p = LocalPalette.current
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AlbumThumb(track = track, sizeDp = 50.dp, corner = 12.dp)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = track.title,
                    color = p.ink,
                    fontSize = 16.sp,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                QualityBadge(track.qualityTier(), Modifier.padding(start = 6.dp))
            }
            Text(
                text = buildString {
                    append(track.displayArtist)
                    if (track.album.isNotBlank()) append(" · ${track.album}")
                },
                color = p.muted,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        if (isCurrent) {
            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(p.accent))
            Spacer(Modifier.width(8.dp))
        }
        Text(formatTime(track.durationMs), color = p.muted, fontSize = 12.sp)
        IconButton(onClick = onMenu) { Icon(Icons.Rounded.MoreVert, "更多", tint = p.muted) }
    }
}
