package com.autotransfer.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

private val Crimson1 = Color(0xFF1A0000)
private val Crimson2 = Color(0xFF0D0000)
private val Crimson3 = Color(0xFF2B0000)
private val Crimson4 = Color(0xFF000000)

@Composable
fun SithBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Crimson1, Crimson2, SithBlack),
                    startY = 0f,
                    endY = h
                )
            )

            val cx = w * 0.6f
            val cy = h * 0.3f
            val r = w * 0.8f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x33C72C41),
                        Color(0x118B0000),
                        Color(0x00000000)
                    )
                ),
                radius = r,
                center = Offset(cx, cy)
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x22981C1C),
                        Color(0x00000000)
                    )
                ),
                radius = w * 0.5f,
                center = Offset(w * 0.3f, h * 0.7f)
            )

            val smokeColor = Color(0x08FFFFFF)
            val bands = 5
            for (i in 0 until bands) {
                val y = h * (0.1f + 0.15f * i)
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0x00000000),
                            smokeColor,
                            Color(0x00000000)
                        ),
                        startY = y,
                        endY = y + h * 0.08f
                    )
                )
            }
        }
        content()
    }
}
