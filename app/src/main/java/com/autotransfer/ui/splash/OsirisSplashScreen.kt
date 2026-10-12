package com.autotransfer.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autotransfer.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

private val BgVoid = Color(0xFF04040A)
private val BgRadial = Color(0xFF0A0A14)
private val Gold = Color(0xFFD4AF37)
private val GoldLight = Color(0xFFF0D060)
private val Rojo = Color(0xFFE53935)
private val TextHeading = Color(0xFFF5F0E0)
private val TextMuted = Color(0xFF5C5A54)

private val HUD_FONT = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold)
)

private val BOOT_STEPS = listOf(
    "INITIALIZING PDF ENGINE..." to false,
    "LOADING CONFIG..." to false,
    "CONNECTING FIREBASE..." to false,
    "SYSTEM READY" to true
)

@Composable
fun OsirisSplash(title: String = "Auto Transfer", subtitle: String = "By John Ram", onTimeout: () -> Unit) {
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.0"
    }

    var currentMessage by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        delay(250)
        BOOT_STEPS.forEach { (msg, _) ->
            currentMessage = msg
            delay(250)
        }
        delay(250)
        onTimeout()
    }

    Box(Modifier.fillMaxSize().background(BgVoid)) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                Brush.radialGradient(
                    colors = listOf(BgRadial, BgVoid),
                    center = center,
                    radius = size.maxDimension * 0.75f
                )
            )
        }

        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            Compass()
            Spacer(Modifier.height(30.dp))
            AnimatedTitle(title)
            Spacer(Modifier.height(16.dp))
            AnimatedSubtitle(subtitle)
            Spacer(Modifier.height(28.dp))
            OsirisProgressBar()
            Spacer(Modifier.height(16.dp))
            BootMessage(currentMessage, isReady = currentMessage == BOOT_STEPS.last().first)
        }

        Text(
            "V $versionName",
            modifier = Modifier.align(Alignment.TopStart).padding(20.dp),
            color = Gold,
            fontSize = 11.sp,
            fontFamily = HUD_FONT,
            fontWeight = FontWeight.Bold,
            letterSpacing = 3.sp
        )

        HUDOverlay()
    }
}

@Composable
private fun Compass(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "rings")
    val outer by transition.animateFloat(
        -90f, 270f,
        infiniteRepeatable(tween(3000, easing = LinearEasing)),
        label = "ringOuter"
    )
    val mid by transition.animateFloat(
        90f, -270f,
        infiniteRepeatable(tween(4000, easing = LinearEasing)),
        label = "ringMid"
    )
    val inner by transition.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(5000, easing = LinearEasing)),
        label = "ringInner"
    )

    Canvas(modifier.size(140.dp)) {
        val c = center

        drawCircle(Gold.copy(alpha = 0.22f), radius = 26.dp.toPx(), center = c)
        drawCircle(Gold.copy(alpha = 0.45f), radius = 22.dp.toPx(), center = c)
        drawCircle(Gold, radius = 20.dp.toPx(), center = c, style = Stroke(2.dp.toPx()))
        drawCircle(BgVoid, radius = 16.dp.toPx(), center = c)

        drawCircle(Rojo.copy(alpha = 0.6f), radius = 40.dp.toPx(), center = c, style = Stroke(1.dp.toPx()))
        drawCircle(Gold.copy(alpha = 0.35f), radius = 55.dp.toPx(), center = c, style = Stroke(1.5.dp.toPx()))
        drawCircle(Gold.copy(alpha = 0.14f), radius = 70.dp.toPx(), center = c, style = Stroke(1.dp.toPx()))

        val outerR = 70.dp.toPx()
        drawArc(
            color = Gold,
            startAngle = outer - 45f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(c.x - outerR, c.y - outerR),
            size = Size(outerR * 2f, outerR * 2f),
            style = Stroke(2.dp.toPx())
        )

        drawOrb(Rojo, c, 40.dp.toPx(), inner)
        drawOrb(Gold, c, 55.dp.toPx(), mid)
        drawOrb(GoldLight, c, 70.dp.toPx(), outer)
    }
}

private fun DrawScope.drawOrb(color: Color, center: Offset, radius: Float, angleDeg: Float) {
    val a = Math.toRadians((angleDeg % 360).toDouble())
    val pos = Offset(
        center.x + radius * cos(a.toFloat()),
        center.y + radius * sin(a.toFloat())
    )
    drawCircle(color.copy(alpha = 0.25f), radius = 4.dp.toPx(), center = pos)
    drawCircle(color, radius = 2.dp.toPx(), center = pos)
}

@Composable
private fun AnimatedTitle(title: String) {
    val letters = remember(title) { title.uppercase().toList() }
    val progresses = remember(letters) { letters.map { Animatable(0f) } }
    LaunchedEffect(letters) {
        delay(250)
        letters.indices.forEach { i ->
            launch {
                delay(i * 55L)
                progresses[i].animateTo(1f, tween(500, easing = FastOutSlowInEasing))
            }
        }
    }
    Row {
        letters.forEachIndexed { i, ch ->
            val scale = 0.6f + 0.4f * progresses[i].value
            val alpha = progresses[i].value
            Text(
                text = ch.toString(),
                color = TextHeading,
                fontSize = 18.sp,
                fontFamily = HUD_FONT,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                modifier = Modifier.graphicsLayer {
                    this.scaleX = scale
                    this.scaleY = scale
                    this.alpha = alpha
                }
            )
        }
    }
}

@Composable
private fun AnimatedSubtitle(subtitle: String) {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(950)
        alpha.animateTo(1f, tween(500, easing = FastOutSlowInEasing))
    }
    Text(
        text = subtitle.uppercase(),
        color = Gold.copy(alpha = alpha.value),
        fontSize = 11.sp,
        fontFamily = HUD_FONT,
        fontWeight = FontWeight.Bold,
        letterSpacing = 5.sp
    )
}

@Composable
private fun OsirisProgressBar() {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(1200, easing = LinearEasing))
    }
    Box(
        Modifier.width(240.dp).height(2.dp).background(Gold.copy(alpha = 0.1f))
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.value)
                .fillMaxHeight()
                .background(
                    Brush.horizontalGradient(listOf(Gold, Rojo, Gold))
                )
        )
    }
}

@Composable
private fun BootMessage(message: String, isReady: Boolean) {
    Text(
        text = message,
        color = if (isReady) Gold else TextMuted,
        fontSize = 10.sp,
        fontFamily = HUD_FONT,
        letterSpacing = 2.sp
    )
}

@Composable
private fun HUDOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        val step = 4.dp.toPx()
        var y = 0f
        while (y < size.height) {
            drawLine(Color.White.copy(alpha = 0.015f), Offset(0f, y), Offset(size.width, y))
            y += step
        }

        drawRect(
            Brush.radialGradient(
                colors = listOf(Color.Transparent, Color(0x9904040A), Color(0xF204040A)),
                center = center,
                radius = size.maxDimension * 0.85f
            )
        )

        val m = 14.dp.toPx()
        val len = 26.dp.toPx()
        val sw = 2.dp.toPx()
        val w = size.width
        val h = size.height

        drawLine(Gold, Offset(m, m), Offset(m + len, m), strokeWidth = sw)
        drawLine(Gold, Offset(m, m), Offset(m, m + len), strokeWidth = sw)
        drawLine(Gold, Offset(w - m, m), Offset(w - m - len, m), strokeWidth = sw)
        drawLine(Gold, Offset(w - m, m), Offset(w - m, m + len), strokeWidth = sw)
        drawLine(Gold, Offset(m, h - m), Offset(m + len, h - m), strokeWidth = sw)
        drawLine(Gold, Offset(m, h - m), Offset(m, h - m - len), strokeWidth = sw)
        drawLine(Gold, Offset(w - m, h - m), Offset(w - m - len, h - m), strokeWidth = sw)
        drawLine(Gold, Offset(w - m, h - m), Offset(w - m, h - m - len), strokeWidth = sw)
    }
}