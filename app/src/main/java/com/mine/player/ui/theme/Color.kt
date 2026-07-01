package com.mine.player.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Core palette ported 1:1 from the original MinePlayer `index.html` CSS variables.
 * Keep these values authoritative — the whole "dark glass / cyan / champagne" identity
 * depends on them matching the desktop original.
 */
object MR {
    val Black = Color(0xFF000000)
    val Bg = Color(0xFF08090B)        // --fc-bg
    val Paper = Color(0xFF0E1014)     // --fc-paper
    val Ink = Color(0xFFE8ECEF)       // --fc-ink
    val Ink2 = Color(0xFFD2D7DC)      // --fc-ink-2
    val Muted = Color(0xFF8A9099)     // --fc-muted
    val Hair = Color(0xFF1A1D22)      // --fc-hair
    val Hair2 = Color(0xFF262A31)     // --fc-hair-2

    val Accent = Color(0xFF00F5D4)    // --fc-accent (cyan)
    val AccentHover = Color(0xFF00E0BE)
    val Blue = Color(0xFF2442FF)      // --fc-blue
    val Warm = Color(0xFFF8F4EE)      // --fc-warm

    val Champagne = Color(0xFFF4D28A)       // --champagne
    val ChampagneDeep = Color(0xFF9A6F2C)

    val ChillCyan = Color(0xFF8FE9FF)
    val ChillBlue = Color(0xFF73A7FF)
    val ChillMint = Color(0xFF9CFFDF)

    val SourceNetease = Color(0xFFD95B67)
    val SourceQQ = Color(0xFF00F5D4)
    val SourceLocal = Color(0xFF9DB8CF)

    // Glass surfaces (approximated from --glass-bg gradient stops)
    val GlassTop = Color(0xB348484C)
    val GlassMid = Color(0xB31B1E22)
    val GlassBottom = Color(0xBC080C0E)
    val GlassBorder = Color(0x4D00F5D4)
    val GlassBorderSoft = Color(0x18FFFFFF)
}
