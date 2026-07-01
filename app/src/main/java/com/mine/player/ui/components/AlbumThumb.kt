package com.mine.player.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mine.player.audio.AlbumArtLoader
import com.mine.player.audio.Track
import com.mine.player.ui.theme.MR

/** Shared placeholder gradient — hoisted so every thumbnail row doesn't allocate its own Brush. */
private val PlaceholderBrush = Brush.linearGradient(listOf(MR.Hair2, MR.Paper, MR.Bg))

/** Fills the given [modifier] (e.g. fillMaxWidth + aspectRatio) with the album cover. */
@Composable
fun CoverImage(
    track: Track?,
    modifier: Modifier = Modifier,
    corner: Dp = 12.dp,
    loadPx: Int = 320,
) {
    val context = LocalContext.current
    var bitmap by remember(track?.albumId, loadPx) {
        mutableStateOf(track?.let { AlbumArtLoader.cachedFor(it, loadPx)?.asImageBitmap() })
    }
    if (bitmap == null && track != null) {
        LaunchedEffect(track.albumId, loadPx) {
            bitmap = AlbumArtLoader.load(context, track, loadPx)?.asImageBitmap()
        }
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .background(PlaceholderBrush),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(bitmap = bmp, contentDescription = track?.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Rounded.MusicNote, null, tint = MR.Champagne.copy(alpha = 0.55f), modifier = Modifier.fillMaxSize(0.3f))
        }
    }
}

/** Album cover thumbnail with async bitmap loading and a branded fallback placeholder. */
@Composable
fun AlbumThumb(
    track: Track?,
    sizeDp: Dp,
    modifier: Modifier = Modifier,
    corner: Dp = 12.dp,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val px = with(density) { sizeDp.roundToPx() }.coerceAtLeast(64)

    var bitmap by remember(track?.albumId, px) {
        mutableStateOf(track?.let { AlbumArtLoader.cachedFor(it, px)?.asImageBitmap() })
    }
    if (bitmap == null && track != null) {
        LaunchedEffect(track.albumId, px) {
            bitmap = AlbumArtLoader.load(context, track, px)?.asImageBitmap()
        }
    }

    val shape = RoundedCornerShape(corner)
    Box(
        modifier = modifier
            .size(sizeDp)
            .clip(shape)
            .background(PlaceholderBrush),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = track?.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MR.Champagne.copy(alpha = 0.55f),
                modifier = Modifier.size(sizeDp / 2.4f),
            )
        }
    }
}
