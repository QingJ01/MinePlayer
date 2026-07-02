package com.mine.player.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mine.player.audio.Album
import com.mine.player.audio.Track
import com.mine.player.audio.groupAlbums
import com.mine.player.ui.components.BrowseHeader
import com.mine.player.ui.components.CoverImage
import com.mine.player.ui.components.TrackListItem
import com.mine.player.ui.theme.LocalPalette

@Composable
fun AlbumsScreen(
    tracks: List<Track>,
    currentTrackId: Long?,
    onPlayTrack: (Track) -> Unit,
    onMenu: (() -> Unit)? = null,
    initialOpenId: Long? = null,
    onInitialConsumed: () -> Unit = {},
) {
    val p = LocalPalette.current
    var open by remember { mutableStateOf<Album?>(null) }
    BackHandler(enabled = open != null) { open = null }
    val albums = remember(tracks) { groupAlbums(tracks) }
    // Deep-link from the track menu's "专辑：…" row.
    LaunchedEffect(initialOpenId) {
        if (initialOpenId != null) {
            albums.firstOrNull { it.id == initialOpenId }?.let { open = it }
            onInitialConsumed()
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(p.bg).statusBarsPadding()) {
        val cur = open
        if (cur == null) {
            BrowseHeader("专辑", onMenu = onMenu) {
                Text("${albums.size}", color = p.muted, fontSize = 14.sp, modifier = Modifier.padding(end = 12.dp))
            }
            LazyVerticalGrid(
                // Adaptive: 2 covers on a phone, more as the screen widens (landscape / tablet).
                columns = GridCells.Adaptive(minSize = 168.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 120.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(albums, key = { it.id }) { album ->
                    Column(
                        modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable { open = album }.padding(4.dp),
                    ) {
                        CoverImage(album.cover, modifier = Modifier.fillMaxWidth().aspectRatio(1f), corner = 14.dp)
                        Text(album.name, color = p.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp, start = 2.dp))
                        Text(album.artist, color = p.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 2.dp))
                    }
                }
            }
        } else {
            BrowseHeader(cur.name, onBack = { open = null })
            val songs = remember(cur, tracks) { tracks.filter { it.albumId == cur.id } }
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 120.dp),
                ) {
                    items(songs, key = { it.id }) { t ->
                        TrackListItem(t, isCurrent = t.id == currentTrackId) { onPlayTrack(t) }
                    }
                }
            }
        }
    }
}
