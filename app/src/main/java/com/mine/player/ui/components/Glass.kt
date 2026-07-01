package com.mine.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.mine.player.ui.theme.MR

/**
 * Translucent dark "glass" surface that mirrors the original `--glass-bg` gradient and
 * cyan-tinted hairline border. True backdrop blur (RenderEffect/Haze over the GL stage)
 * is a later enhancement; v1 leans on layered translucency so the bloom behind bleeds through.
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    borderColor: Color = MR.GlassBorderSoft,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(MR.GlassTop, MR.GlassMid, MR.GlassBottom),
                )
            )
            .border(1.dp, borderColor, shape),
        content = content,
    )
}
