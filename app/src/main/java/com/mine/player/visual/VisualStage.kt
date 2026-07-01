package com.mine.player.visual

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.mine.player.visual.gl.VisualRenderer

/** Hosts any OpenGL ES renderer as a TextureView, wired to the activity lifecycle. */
@Composable
fun GlHost(
    renderer: GLTextureView.Renderer,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var glView by remember { mutableStateOf<GLTextureView?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            GLTextureView(ctx).apply {
                setRenderer(renderer)
                glView = this
            }
        },
    )

    DisposableEffect(lifecycleOwner, glView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> glView?.onActivityPause()
                Lifecycle.Event.ON_RESUME -> glView?.onActivityResume()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

/** The cover-particle / skull stage. */
@Composable
fun VisualStage(
    renderer: VisualRenderer,
    modifier: Modifier = Modifier,
) = GlHost(renderer = renderer, modifier = modifier)
