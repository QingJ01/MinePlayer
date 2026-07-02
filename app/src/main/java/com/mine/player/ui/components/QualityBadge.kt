package com.mine.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mine.player.audio.QualityTier

private val HrGold = Color(0xFFC79A2E)
private val SqTeal = Color(0xFF16B6A4)
private val HqBlue = Color(0xFF6E8FC8)

/** Small quality pill (HR / SQ / HQ). Renders nothing for the standard tier. */
@Composable
fun QualityBadge(tier: QualityTier, modifier: Modifier = Modifier) {
    val (label, color) = when (tier) {
        QualityTier.HIRES -> "HR" to HrGold
        QualityTier.SQ -> "SQ" to SqTeal
        QualityTier.HQ -> "HQ" to HqBlue
        QualityTier.STANDARD -> return
    }
    Text(
        text = label,
        color = color,
        fontSize = 9.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 0.5.sp,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}
