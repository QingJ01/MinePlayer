package com.mine.player.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.mine.player.audio.AlbumArtLoader
import com.mine.player.audio.Track
import com.mine.player.ui.rememberWindowInfo
import com.mine.player.ui.components.CoverImage
import com.mine.player.ui.theme.LocalPalette
import com.mine.player.ui.theme.Palette
import kotlinx.coroutines.launch
import kotlin.math.abs

/** "歌单架": a cover-flow of the library over a frosted (blurred) backdrop of the current cover. */
@Composable
fun CoverWallScreen(
    tracks: List<Track>,
    currentIndex: Int,
    onPlay: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val p = LocalPalette.current
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize().background(p.bg)) {
        if (tracks.isEmpty()) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                WallHeader(onClose)
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("曲库为空", color = p.muted, fontSize = 14.sp)
                }
            }
            return@Box
        }

        val pagerState = rememberPagerState(initialPage = currentIndex.coerceIn(0, tracks.lastIndex)) { tracks.size }
        val scope = rememberCoroutineScope()
        val cur = tracks[pagerState.currentPage]

        // Frosted backdrop: a heavily-blurred copy of the current cover, tinted by the theme.
        var backdrop by remember { mutableStateOf<ImageBitmap?>(null) }
        LaunchedEffect(cur.albumId) {
            backdrop = AlbumArtLoader.load(context, cur, 256)?.asImageBitmap()
        }
        backdrop?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(52.dp),
            )
        }
        Box(Modifier.fillMaxSize().background(p.bg.copy(alpha = 0.68f)))

        // Landscape is short (≈360dp tall): let the cover flow take the leftover height instead of a
        // fixed 300dp, and size each cover by height so it always fits.
        val landscape = rememberWindowInfo().isLandscape
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            WallHeader(onClose)
            if (!landscape) Spacer(Modifier.weight(1f))

            HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(horizontal = if (landscape) 150.dp else 76.dp),
                modifier = if (landscape) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth().height(300.dp),
            ) { page ->
                val offset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                val absO = abs(offset).coerceIn(0f, 1f)
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = (if (landscape) Modifier.fillMaxHeight() else Modifier.fillMaxWidth())
                            .aspectRatio(1f)
                            .graphicsLayer {
                                rotationY = (offset * 34f).coerceIn(-52f, 52f)
                                cameraDistance = 16f * density
                                val s = lerp(1f, 0.78f, absO)
                                scaleX = s
                                scaleY = s
                                alpha = lerp(1f, 0.5f, absO)
                                shape = RoundedCornerShape(18.dp)
                                clip = true
                                shadowElevation = lerp(18f, 0f, absO) * density
                            }
                            .clickable {
                                if (page == pagerState.currentPage) { onPlay(page); onClose() }
                                else scope.launch { pagerState.animateScrollToPage(page) }
                            },
                    ) {
                        CoverImage(track = tracks[page], modifier = Modifier.fillMaxSize(), corner = 18.dp, loadPx = 512)
                    }
                }
            }

            Spacer(Modifier.height(if (landscape) 10.dp else 26.dp))
            Text(
                text = cur.title,
                color = p.ink,
                fontSize = if (landscape) 16.sp else 19.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = cur.displayArtist,
                color = p.muted,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(if (landscape) 12.dp else 26.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(p.accent)
                    .clickable { onPlay(pagerState.currentPage); onClose() }
                    .padding(horizontal = 36.dp, vertical = if (landscape) 9.dp else 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Rounded.PlayArrow, null, tint = p.onAccent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.size(8.dp))
                Text("播放", color = p.onAccent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(if (landscape) 12.dp else 0.dp))
            if (!landscape) Spacer(Modifier.weight(1.3f))
        }
    }
}

@Composable
private fun WallHeader(onClose: () -> Unit) {
    val p: Palette = LocalPalette.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Rounded.KeyboardArrowDown, "收起", tint = p.ink, modifier = Modifier.size(28.dp))
        }
        Text("歌单架", color = p.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 2.dp))
    }
}
