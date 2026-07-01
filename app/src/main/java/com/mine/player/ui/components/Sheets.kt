package com.mine.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mine.player.audio.AudioInfo
import com.mine.player.audio.Track
import com.mine.player.data.Playlist
import com.mine.player.ui.theme.LocalPalette

@Composable
private fun SheetRow(icon: ImageVector, label: String, tint: androidx.compose.ui.graphics.Color? = null, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint ?: p.ink2, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(18.dp))
        Text(label, color = p.ink, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SheetHeader(text: String) {
    val p = LocalPalette.current
    Text(text, color = p.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 24.dp, top = 8.dp, bottom = 8.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackActionSheet(
    track: Track,
    onAddToPlaylist: () -> Unit,
    onPlayNext: () -> Unit,
    onShare: () -> Unit,
    onOpenArtist: () -> Unit,
    onOpenAlbum: () -> Unit,
    onInfo: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val p = LocalPalette.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = p.surface) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 36.dp)) {
            Row(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                AlbumThumb(track = track, sizeDp = 46.dp, corner = 10.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(track.title, color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        buildString {
                            append(track.displayArtist)
                            if (track.album.isNotBlank()) append(" · ${track.album}")
                        },
                        color = p.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            SheetRow(Icons.AutoMirrored.Rounded.PlaylistAdd, "添加到歌单") { onAddToPlaylist() }
            SheetRow(Icons.Rounded.SkipNext, "下一首播放") { onPlayNext(); onDismiss() }
            SheetRow(Icons.Rounded.Share, "分享") { onShare(); onDismiss() }
            SheetRow(Icons.Rounded.Person, "艺术家：${track.displayArtist}") { onOpenArtist(); onDismiss() }
            if (track.album.isNotBlank()) {
                SheetRow(Icons.Rounded.Album, "专辑：${track.album}") { onOpenAlbum(); onDismiss() }
            }
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp).height(1.dp).background(p.hair.copy(alpha = 0.6f)))
            SheetRow(Icons.Rounded.Info, "歌曲信息") { onInfo() }
            SheetRow(Icons.Rounded.DeleteOutline, "永久删除", tint = Color(0xFFE5484D)) { onDelete() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackInfoSheet(track: Track, audioInfo: AudioInfo?, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = p.surface) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 36.dp)) {
            Row(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                AlbumThumb(track = track, sizeDp = 46.dp, corner = 10.dp)
                Spacer(Modifier.width(14.dp))
                Text(track.title, color = p.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            InfoLine("艺术家", track.displayArtist)
            InfoLine("专辑", track.album.ifBlank { "未知专辑" })
            InfoLine("格式", audioInfo?.display() ?: "读取中…")
            InfoLine("时长", formatTime(track.durationMs))
            track.filePath?.let { InfoLine("路径", it) }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    val p = LocalPalette.current
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 7.dp)) {
        Text(label, color = p.muted, fontSize = 12.sp)
        Text(value, color = p.ink2, fontSize = 14.sp, modifier = Modifier.padding(top = 3.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
    playlists: List<Playlist>,
    onPick: (Long) -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val p = LocalPalette.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = p.surface) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 36.dp)) {
            SheetHeader("加入歌单")
            SheetRow(Icons.Rounded.Add, "新建歌单", tint = p.accent) { onCreate() }
            playlists.forEach { pl ->
                SheetRow(Icons.AutoMirrored.Rounded.QueueMusic, "${pl.name}  (${pl.trackIds.size})") { onPick(pl.id) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheet(remainingSec: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = p.surface) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 36.dp)) {
            SheetHeader("睡眠定时")
            if (remainingSec > 0) {
                Text(
                    "剩余 %d:%02d".format(remainingSec / 60, remainingSec % 60),
                    color = p.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 24.dp, bottom = 4.dp),
                )
            }
            listOf(0 to "关闭", 15 to "15 分钟", 30 to "30 分钟", 45 to "45 分钟", 60 to "60 分钟").forEach { (m, label) ->
                SheetRow(Icons.Rounded.Bedtime, label) { onPick(m); onDismiss() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(queue: List<Track>, currentIndex: Int, onJumpTo: (Int) -> Unit, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        if (currentIndex in queue.indices) listState.scrollToItem(currentIndex)
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = p.surface) {
        Column(modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
            SheetHeader("播放队列 · ${queue.size}")
            LazyColumn(state = listState) {
                itemsIndexed(queue, key = { _, t -> t.id }) { i, t ->
                    val active = i == currentIndex
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onJumpTo(i); onDismiss() }.padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(t.title, color = if (active) p.accent else p.ink, fontSize = 15.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(t.displayArtist, color = p.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoSheet(
    track: Track,
    audioInfo: AudioInfo?,
    keepScreenOn: Boolean,
    onKeepScreenOn: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val p = LocalPalette.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = p.surface) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 36.dp)) {
            SheetHeader("详情")
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.LightMode, null, tint = p.ink2, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(18.dp))
                Text("保持屏幕常亮", color = p.ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Switch(checked = keepScreenOn, onCheckedChange = onKeepScreenOn, colors = SwitchDefaults.colors(checkedThumbColor = p.onAccent, checkedTrackColor = p.accent))
            }
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                Text("音频信息", color = p.muted, fontSize = 13.sp)
                Text(audioInfo?.display() ?: "读取中…", color = p.ink2, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
            }
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                Text("出自专辑", color = p.muted, fontSize = 13.sp)
                Text(track.album.ifBlank { "未知专辑" }, color = p.ink2, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                Text("艺术家 · ${track.displayArtist}", color = p.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}
