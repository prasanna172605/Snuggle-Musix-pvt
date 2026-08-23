package com.snuggle.music.ui.theme

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

fun Modifier.liquidGlass(
    shape: Shape,
    borderAlpha: Float = 0.22f,
    scrimAlpha: Float = 0.4f
): Modifier = this.then(
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Modifier
            .graphicsLayer {
                renderEffect = RenderEffect.createBlurEffect(
                    30f, 30f, Shader.TileMode.CLAMP
                ).asComposeRenderEffect()
                clip = true
                this.shape = shape
            }
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.08f),
                        Color.White.copy(alpha = 0.03f)
                    )
                ),
                shape = shape
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = borderAlpha),
                        Color.White.copy(alpha = borderAlpha * 0.3f)
                    )
                ),
                shape = shape
            )
    } else {
        Modifier
            .background(
                color = Color.Black.copy(alpha = scrimAlpha),
                shape = shape
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = borderAlpha),
                shape = shape
            )
    }
)
