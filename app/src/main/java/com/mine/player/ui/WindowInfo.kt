package com.mine.player.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/** Coarse width bucket, mirroring Material's window size classes. */
enum class WidthClass { COMPACT, MEDIUM, EXPANDED }

/** Responsive window description, recomputed on every configuration change. */
data class WindowInfo(
    val widthClass: WidthClass,
    val isLandscape: Boolean,
    val widthDp: Int,
    val heightDp: Int,
) {
    /** Phone portrait: the classic single-pane, modal-drawer layout. */
    val isCompact: Boolean get() = widthClass == WidthClass.COMPACT
    /** Tablet / large landscape: enough room for a persistent nav rail and side panels. */
    val isExpanded: Boolean get() = widthClass == WidthClass.EXPANDED
    /** Medium+ (large landscape phone, tablet): swap the modal drawer for a persistent rail. */
    val useRail: Boolean get() = widthClass != WidthClass.COMPACT
}

@Composable
fun rememberWindowInfo(): WindowInfo {
    val c = LocalConfiguration.current
    val w = c.screenWidthDp
    val h = c.screenHeightDp
    val widthClass = when {
        w >= 840 -> WidthClass.EXPANDED
        w >= 600 -> WidthClass.MEDIUM
        else -> WidthClass.COMPACT
    }
    return WindowInfo(
        widthClass = widthClass,
        isLandscape = c.orientation == Configuration.ORIENTATION_LANDSCAPE,
        widthDp = w,
        heightDp = h,
    )
}
