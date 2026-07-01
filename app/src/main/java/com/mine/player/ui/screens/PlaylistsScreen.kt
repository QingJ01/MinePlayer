package com.mine.player.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mine.player.audio.Track
import com.mine.player.data.Playlist
import com.mine.player.ui.components.BrowseHeader
import com.mine.player.ui.components.TrackListItem
import com.mine.player.ui.theme.LocalPalette

@Composable
fun PlaylistsScreen(
    playlists: List<Playlist>,
    tracks: List<Track>,
    currentTrackId: Long?,
    onPlayTrack: (Track) -> Unit,
    onCreatePlaylist: () -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    onMenu: () -> Unit,
) {
    val p = LocalPalette.current
    var openId by remember { mutableStateOf<Long?>(null) }
    val open = playlists.firstOrNull { it.id == openId }
    BackHandler(enabled = openId != null) { openId = null }

    Column(modifier = Modifier.fillMaxSize().background(p.bg).statusBarsPadding()) {
        if (open == null) {
            BrowseHeader("歌单", onMenu = onMenu) {
                IconButton(onClick = onCreatePlaylist) { Icon(Icons.Rounded.Add, "新建歌单", tint = p.ink) }
            }
            if (playlists.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("还没有歌单\n点右上角 + 新建", color = p.muted, fontSize = 14.sp)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
                    items(playlists, key = { it.id }) { pl ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { openId = pl.id }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(p.surfaceAlt),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, tint = p.ink2, modifier = Modifier.size(24.dp)) }
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(pl.name, color = p.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                Text("${pl.trackIds.size} 首", color = p.muted, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        } else {
            BrowseHeader(open.name, onBack = { openId = null }) {
                IconButton(onClick = { onDeletePlaylist(open.id); openId = null }) {
                    Icon(Icons.Rounded.DeleteOutline, "删除歌单", tint = p.ink)
                }
            }
            val byId = remember(tracks) { tracks.associateBy { it.id } }
            val songs = open.trackIds.mapNotNull { byId[it] }
            if (songs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("歌单是空的", color = p.muted, fontSize = 14.sp)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
                    items(songs, key = { it.id }) { t ->
                        TrackListItem(t, isCurrent = t.id == currentTrackId) { onPlayTrack(t) }
                    }
                }
            }
        }
    }
}
