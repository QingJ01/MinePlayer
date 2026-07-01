package com.mine.player.ui

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * The single source of truth for system-bar appearance and visibility.
 *
 * Previously each screen toggled the bars from its own [DisposableEffect], so
 * entering / leaving the now-playing stage produced competing hide/show calls
 * and the status bar visibly flickered ("乱跳"). Driving everything from one
 * keyed effect here means the bars only change when the inputs actually change.
 *
 * @param lightBackground true when the bar sits on a light surface (uses dark
 *   icons); false for dark surfaces / the black now-playing stage (light icons).
 * @param hideStatusBar hide the top status bar (immersive mode / now-playing).
 * @param hideNavBar hide the bottom navigation bar (now-playing only).
 */
@Composable
fun SystemBarsController(
    lightBackground: Boolean,
    hideStatusBar: Boolean,
    hideNavBar: Boolean,
) {
    val view = LocalView.current
    DisposableEffect(lightBackground, hideStatusBar, hideNavBar) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, view) }
        if (controller != null) {
            controller.isAppearanceLightStatusBars = lightBackground
            controller.isAppearanceLightNavigationBars = lightBackground
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

            if (hideStatusBar) controller.hide(WindowInsetsCompat.Type.statusBars())
            else controller.show(WindowInsetsCompat.Type.statusBars())

            if (hideNavBar) controller.hide(WindowInsetsCompat.Type.navigationBars())
            else controller.show(WindowInsetsCompat.Type.navigationBars())
        }
        onDispose { }
    }
}
