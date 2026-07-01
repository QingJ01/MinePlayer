package com.mine.player.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** Semantic colors that swap between dark and light. Read via [LocalPalette]. */
data class Palette(
    val bg: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val ink: Color,
    val ink2: Color,
    val muted: Color,
    val hair: Color,
    val accent: Color,
    val onAccent: Color,
    val isDark: Boolean,
)

/** Curated preset accent colors (the actual ARGB is stored in settings). */
val AccentOptions = listOf(
    Color(0xFF00F5D4), // cyan
    Color(0xFF19C37D), // green
    Color(0xFF4FD1C5), // teal
    Color(0xFF73A7FF), // blue
    Color(0xFF7C83FF), // indigo
    Color(0xFFB388FF), // purple
    Color(0xFFFF7AB6), // pink
    Color(0xFFFF6B6B), // coral
    Color(0xFFFF8A5B), // tangerine
    Color(0xFFFFB067), // orange
    Color(0xFFF4D28A), // champagne
    Color(0xFF9CFFDF), // mint
)

val DefaultAccent = AccentOptions[0]

fun onAccentFor(accent: Color): Color = if (accent.luminance() > 0.5f) Color(0xFF06110F) else Color.White

fun darkPalette(accent: Color) = Palette(
    bg = Color(0xFF000000),
    surface = Color(0xFF14171C),
    surfaceAlt = Color(0xFF0E1014),
    ink = Color(0xFFE8ECEF),
    ink2 = Color(0xFFC7CDD4),
    muted = Color(0xFF8A9099),
    hair = Color(0xFF262A31),
    accent = accent,
    onAccent = onAccentFor(accent),
    isDark = true,
)

fun lightPalette(accent: Color) = Palette(
    bg = Color(0xFFF5F6F8),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFEDEFF2),
    ink = Color(0xFF1B1E23),
    ink2 = Color(0xFF41474F),
    muted = Color(0xFF8A9099),
    hair = Color(0xFFE4E7EB),
    accent = accent,
    onAccent = onAccentFor(accent),
    isDark = false,
)

val LocalPalette = staticCompositionLocalOf { darkPalette(AccentOptions[0]) }
