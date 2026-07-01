package com.mine.player.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mine.player.ui.theme.MR
import com.mine.player.visual.GlHost
import com.mine.player.visual.gl.FullscreenShaderRenderer
import kotlinx.coroutines.delay

/** WebGL-style boot animation (ported splash shader); tap anywhere to enter. */
@Composable
fun SplashScreen(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val renderer = remember {
        FullscreenShaderRenderer(
            context.applicationContext,
            "shaders/splash.vert",
            "shaders/splash.frag",
        )
    }
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(5200)
        ready = true
    }

    val pulse = rememberInfiniteTransition(label = "hint")
    val hintAlpha by pulse.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "hintAlpha",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onDismiss() },
    ) {
        GlHost(renderer = renderer, modifier = Modifier.fillMaxSize())

        Text(
            text = "MINEPLAYER",
            color = MR.Ink,
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 8.sp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 96.dp),
        )

        if (ready) {
            Text(
                text = "点击进入",
                color = MR.Champagne,
                fontSize = 13.sp,
                letterSpacing = 4.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 88.dp)
                    .alpha(hintAlpha),
            )
        }
    }
}
