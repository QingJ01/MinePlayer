package com.mine.player.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mine.player.audio.AlbumArtLoader
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
    onMenu: (() -> Unit)? = null,
    initialOpen: String? = null,
    onInitialConsumed: () -> Unit = {},
) {
    val p = LocalPalette.current
    var open by remember { mutableStateOf<Artist?>(null) }
    BackHandler(enabled = open != null) { open = null }
    val artists = remember(tracks) { groupArtists(tracks) }
    // Deep-link from the track menu's "艺术家：…" row / the now-playing artist tap.
    LaunchedEffect(initialOpen) {
        if (initialOpen != null) {
            artists.firstOrNull { it.name == initialOpen }?.let { open = it }
            onInitialConsumed()
        }
    }

    val cur = open
    if (cur == null) {
        Column(modifier = Modifier.fillMaxSize().background(p.bg).statusBarsPadding()) {
            BrowseHeader("艺术家", onMenu = onMenu) {
                Text("${artists.size}", color = p.muted, fontSize = 14.sp, modifier = Modifier.padding(end = 12.dp))
            }
            LazyVerticalGrid(
                // One column on a phone, two-plus once there's room (landscape / tablet).
                columns = GridCells.Adaptive(minSize = 340.dp),
                contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 120.dp),
            ) {
                items(artists, key = { it.name }) { artist ->
                    ArtistRow(artist) { open = artist }
                }
            }
        }
    } else {
        val songs = remember(cur, tracks) { tracks.filter { it.displayArtist == cur.name } }
        ArtistDetail(
            artist = cur,
            songs = songs,
            currentTrackId = currentTrackId,
            onPlayTrack = onPlayTrack,
            onBack = { open = null },
        )
    }
}

@Composable
private fun ArtistRow(artist: Artist, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(artist.cover, modifier = Modifier.size(52.dp), corner = 26.dp)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(artist.name, color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${artist.count} 首", color = p.muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ArtistDetail(
    artist: Artist,
    songs: List<Track>,
    currentTrackId: Long?,
    onPlayTrack: (Track) -> Unit,
    onBack: () -> Unit,
) {
    val p = LocalPalette.current
    Box(modifier = Modifier.fillMaxSize().background(p.bg)) {
        LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
            item {
                ArtistHero(artist = artist, onPlay = { songs.firstOrNull()?.let(onPlayTrack) })
            }
            items(songs, key = { it.id }) { t ->
                TrackListItem(t, isCurrent = t.id == currentTrackId) { onPlayTrack(t) }
            }
        }
        // Floating back button over the immersive hero.
        IconButton(
            onClick = onBack,
            modifier = Modifier.statusBarsPadding().padding(4.dp),
        ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = p.ink) }
    }
}

@Composable
private fun ArtistHero(artist: Artist, onPlay: () -> Unit) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val cover = artist.cover

    // Frosted backdrop: a heavily-blurred copy of the artist's cover, reusing the album art.
    var backdrop by remember(cover.albumId) {
        mutableStateOf(AlbumArtLoader.cachedFor(cover, 256)?.asImageBitmap())
    }
    if (backdrop == null) {
        LaunchedEffect(cover.albumId) { backdrop = AlbumArtLoader.load(context, cover, 256)?.asImageBitmap() }
    }

    Box(modifier = Modifier.fillMaxWidth().height(322.dp)) {
        backdrop?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().blur(52.dp),
            )
        }
        // Scrim so the avatar/text read against the busy blur and the list blends into the page.
        Box(
            modifier = Modifier.matchParentSize().background(
                Brush.verticalGradient(listOf(p.bg.copy(alpha = 0.30f), p.bg.copy(alpha = 0.60f), p.bg)),
            ),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 40.dp, bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Circular avatar (reused album art) inside a soft accent ring.
            Box(
                modifier = Modifier
                    .size(124.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(p.accent.copy(alpha = 0.55f), p.accent.copy(alpha = 0.14f)))),
                contentAlignment = Alignment.Center,
            ) {
                CoverImage(cover, modifier = Modifier.size(114.dp), corner = 999.dp, loadPx = 320)
            }
            Spacer(Modifier.height(14.dp))
            Text(
                artist.name,
                color = p.ink,
                fontSize = 25.sp,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            Spacer(Modifier.height(5.dp))
            Text("${artist.count} 首歌", color = p.muted, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(p.accent)
                    .clickable(onClick = onPlay)
                    .padding(horizontal = 26.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.PlayArrow, null, tint = p.onAccent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("播放", color = p.onAccent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
