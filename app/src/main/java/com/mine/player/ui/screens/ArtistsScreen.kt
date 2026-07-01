package com.mine.player.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mine.player.audio.Artist
import com.mine.player.audio.Track
import com.mine.player.audio.groupArtists
import com.mine.player.ui.components.BrowseHeader
import com.mine.player.ui.components.CoverImage
import com.mine.player.ui.components.TrackListItem
import com.mine.player.ui.theme.LocalPalette

@Composable
fun ArtistsScreen(
    tracks: List<Track>,
    currentTrackId: Long?,
    onPlayTrack: (Track) -> Unit,
    onMenu: () -> Unit,
    initialOpen: String? = null,
    onInitialConsumed: () -> Unit = {},
) {
    val p = LocalPalette.current
    var open by remember { mutableStateOf<Artist?>(null) }
    BackHandler(enabled = open != null) { open = null }
    val artists = remember(tracks) { groupArtists(tracks) }
    // Deep-link from the track menu's "艺术家：…" row.
    LaunchedEffect(initialOpen) {
        if (initialOpen != null) {
            artists.firstOrNull { it.name == initialOpen }?.let { open = it }
            onInitialConsumed()
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(p.bg).statusBarsPadding()) {
        val cur = open
        if (cur == null) {
            BrowseHeader("艺术家", onMenu = onMenu) {
                Text("${artists.size}", color = p.muted, fontSize = 14.sp, modifier = Modifier.padding(end = 12.dp))
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
                items(artists, key = { it.name }) { artist ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { open = artist }.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CoverImage(artist.cover, modifier = Modifier.size(48.dp), corner = 24.dp)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(artist.name, color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${artist.count} 首", color = p.muted, fontSize = 12.sp)
                        }
                    }
                }
            }
        } else {
            BrowseHeader(cur.name, onBack = { open = null })
            val songs = remember(cur, tracks) { tracks.filter { it.displayArtist == cur.name } }
            LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
                items(songs, key = { it.id }) { t ->
                    TrackListItem(t, isCurrent = t.id == currentTrackId) { onPlayTrack(t) }
                }
            }
        }
    }
}
