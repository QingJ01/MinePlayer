package com.mine.player.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mine.player.lyrics.LrcParser
import com.mine.player.lyrics.LyricLine
import com.mine.player.ui.theme.MR

/** Where the active line sits in the viewport (0 = top, 0.5 = center); slightly above center. */
private const val LYRIC_CENTER_BIAS = 0.35f

/** Minimal single-line lyric: only the current line, centered, crossfading between lines. */
@Composable
fun SingleLyricView(
    lines: List<LyricLine>,
    positionMs: Long,
    modifier: Modifier = Modifier,
    maxFontSize: TextUnit = 19.sp,
    translationMaxFontSize: TextUnit = 14.sp,
) {
    if (lines.isEmpty()) return
    val idx = LrcParser.activeIndex(lines, positionMs)
    val text = if (idx >= 0) lines[idx].text else ""
    // Reserve a stable height (two lines at max font) so a shorter/longer line — or the auto-size
    // shrinking a long line — never shifts the surrounding controls.
    val reserved = (maxFontSize.value * 1.35f + translationMaxFontSize.value * 1.35f + 12f).dp
    Box(modifier = modifier.height(reserved), contentAlignment = Alignment.Center) {
        Crossfade(targetState = text, animationSpec = tween(320), label = "singleLyric") { t ->
            val parts = t.split("\n").filter { it.isNotBlank() }
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
            AutoSizeText(
                text = parts.getOrElse(0) { "" },
                color = MR.Accent,
                maxFontSize = maxFontSize,
                minFontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (parts.size > 1) {
                AutoSizeText(
                    text = parts.drop(1).joinToString(" "),
                    color = MR.Ink2.copy(alpha = 0.7f),
                    maxFontSize = translationMaxFontSize,
                    minFontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.padding(top = 3.dp),
                )
                }
            }
        }
    }
}

/**
 * Centered, auto-scrolling lyric stage that highlights the active line and keeps
 * it exactly at the vertical middle of the viewport. When [onLineClick] is set,
 * tapping a line reports it (used to seek to that line's timestamp).
 */
@Composable
fun LyricView(
    lines: List<LyricLine>,
    positionMs: Long,
    modifier: Modifier = Modifier,
    onLineClick: ((LyricLine) -> Unit)? = null,
) {
    if (lines.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "纯音乐 · 暂无歌词",
                color = MR.Muted,
                fontSize = 14.sp,
                letterSpacing = 2.sp,
            )
        }
        return
    }

    val current = LrcParser.activeIndex(lines, positionMs)
    val listState = rememberLazyListState()
    // Half-screen padding top & bottom so even the first/last line can reach the center.
    val halfViewport = (LocalConfiguration.current.screenHeightDp / 2).dp

    // Pin the active line to the exact vertical center. Everything is computed in the
    // layout's own frame (viewportStartOffset/EndOffset + item.offset), so the large
    // content padding cancels out instead of pushing the line into the lower half.
    LaunchedEffect(current, lines) {
        if (current < 0) return@LaunchedEffect
        var info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == current }
        if (info == null) {
            listState.scrollToItem(current)
            info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == current }
        }
        if (info == null) return@LaunchedEffect
        val li = listState.layoutInfo
        // Target ~44% down the viewport (a touch above the geometric center) so the active line
        // reads as vertically centered against the bottom-weighted controls / nav area.
        val target = li.viewportStartOffset + (li.viewportEndOffset - li.viewportStartOffset) * LYRIC_CENTER_BIAS
        val itemCenter = info.offset + info.size / 2f
        listState.animateScrollBy(itemCenter - target)
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(vertical = halfViewport),
    ) {
        itemsIndexed(lines) { i, line ->
            LyricRow(line = line, active = i == current, onClick = onLineClick?.let { { it(line) } })
        }
    }
}

@Composable
private fun LyricRow(line: LyricLine, active: Boolean, onClick: (() -> Unit)? = null) {
    val color by animateColorAsState(
        targetValue = if (active) MR.Accent else MR.Ink2.copy(alpha = 0.42f),
        label = "lyricColor",
    )
    // Emphasize the active line by SCALING (a draw-time transform) — not by changing the font size,
    // which would change the row height and make the list jump when the active line switches.
    val scale by animateFloatAsState(
        targetValue = if (active) 1.14f else 0.86f,
        label = "lyricScale",
    )
    // Bilingual lines arrive as "original\ntranslation"; show the original prominently and
    // the translation smaller and dimmer beneath it.
    val parts = line.text.split('\n').filter { it.isNotBlank() }
    val primary = parts.firstOrNull() ?: line.text
    val translation = if (parts.size > 1) parts.drop(1).joinToString(" ") else null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 28.dp, vertical = 9.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Constant font sizing → the measured row height never changes with the active state.
        AutoSizeText(
            text = primary,
            color = color,
            maxFontSize = 18.sp,
            minFontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (translation != null) {
            AutoSizeText(
                text = translation,
                color = if (active) MR.Ink2.copy(alpha = 0.72f) else MR.Ink2.copy(alpha = 0.30f),
                maxFontSize = 13.sp,
                minFontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

/**
 * A single-line lyric that shrinks its font to fit the available width instead of wrapping.
 * Steps the size down from [maxFontSize] to [minFontSize]; only if even the minimum overflows
 * does it fall back to a two-line wrap.
 */
@Composable
private fun AutoSizeText(
    text: String,
    color: Color,
    maxFontSize: TextUnit,
    minFontSize: TextUnit,
    fontWeight: FontWeight,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val measurer = rememberTextMeasurer()
        val maxWidthPx = constraints.maxWidth
        val fit = remember(text, maxWidthPx, maxFontSize, minFontSize, fontWeight) {
            if (maxWidthPx <= 0 || text.isEmpty()) {
                maxFontSize to false
            } else {
                var size = maxFontSize
                var chosen: Pair<TextUnit, Boolean>? = null
                while (size.value > minFontSize.value) {
                    val w = measurer.measure(
                        text = text,
                        style = TextStyle(fontSize = size, fontWeight = fontWeight),
                        softWrap = false,
                        maxLines = 1,
                    ).size.width
                    if (w <= maxWidthPx * 0.98f) {
                        chosen = size to false
                        break
                    }
                    size = (size.value - 1f).sp
                }
                chosen ?: (minFontSize to true)
            }
        }
        val fontSize = fit.first
        val wrap = fit.second
        Text(
            text = text,
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            textAlign = TextAlign.Center,
            maxLines = if (wrap) 2 else 1,
            softWrap = wrap,
            overflow = TextOverflow.Ellipsis,
            lineHeight = (fontSize.value * 1.3f).sp,
        )
    }
}
