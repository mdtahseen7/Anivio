package com.nuvio.app.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.app_icon_av
import org.jetbrains.compose.resources.imageResource
import kotlin.math.pow

// Sampled from the original JPEG. Android launch themes and iOS launch color use the same RGB.
internal val AvSplashBackground = Color(0xFF020200)

@Composable
internal fun AvSplash(onFinished: () -> Unit) {
    val clock = remember { Animatable(0f) }
    val finish by rememberUpdatedState(onFinished)
    LaunchedEffect(Unit) {
        clock.animateTo(3.4f, tween(3400, easing = LinearEasing))
        finish()
    }
    AvLogoSurface(
        timeSeconds = clock.value,
        modifier = Modifier.fillMaxSize().graphicsLayer { alpha = ((3.4f - clock.value) / 0.2f).coerceIn(0f, 1f) }
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } },
    )
}

@Composable
internal fun AvLogoSurface(modifier: Modifier = Modifier, timeSeconds: Float = 3.1f) {
    val bitmap = imageResource(Res.drawable.app_icon_av)
    val spine = remember { Path().apply { moveTo(295f, 1050f); lineTo(650f, 350f); lineTo(1020f, 1120f); lineTo(1320f, 540f) } }
    val bar = remember { Path().apply { moveTo(330f, 1000f); lineTo(830f, 1000f) } }
    val barClip = remember { Path().apply { moveTo(440f, 921f); lineTo(762f, 921f); lineTo(841f, 1081f); lineTo(364f, 1081f); close() } }
    val spineClip = remember { Path().apply { fillType = PathFillType.EvenOdd; addRect(androidx.compose.ui.geometry.Rect(0f, 0f, 1600f, 1600f)); addPath(barClip) } }
    val spineMeasure = remember { PathMeasure().apply { setPath(spine, false) } }
    val barMeasure = remember { PathMeasure().apply { setPath(bar, false) } }
    BoxWithConstraints(modifier.background(AvSplashBackground), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(minOf(maxWidth, maxHeight) * (800f / 1080f))) {
            val t = timeSeconds
            val arrival = ((t - 0.08f) / 1.12f).coerceIn(0f, 1f)
            val settle = ((t - 1.2f) / 0.38f).coerceIn(0f, 1f)
            val scale = if (t < 1.2f) 0.94f + 0.075f * (1f - (1f - arrival).pow(4)) else 1.015f - 0.015f * (1f - (1f - settle).pow(3))
            withTransform({
                translate(top = size.height * ((34f - 8f * (1f - (1f - arrival).pow(4))) / 800f))
                scale(scale, scale, pivot = androidx.compose.ui.geometry.Offset(size.width / 2, size.height * 0.47f))
            }) {
                drawIntoCanvas { canvas ->
                    val bounds = androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height)
                    canvas.saveLayer(bounds, Paint())
                    drawImage(bitmap, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
                    if (t < 1.32f) {
                        canvas.saveLayer(bounds, Paint().apply { blendMode = BlendMode.DstIn })
                        withTransform({ scale(size.width / 1600f, size.height / 1600f, pivot = androidx.compose.ui.geometry.Offset.Zero) }) {
                            val p = ((t - 0.08f) / 1.18f).coerceIn(0f, 1f)
                            val eased = if (p < 0.5f) 4f * p.pow(3) else 1f - (-2f * p + 2f).pow(3) / 2f
                            val traced = Path().also { spineMeasure.getSegment(0f, spineMeasure.length * eased, it, true) }
                            clipPath(spineClip) { drawPath(traced, Color.White, style = Stroke(330f, cap = StrokeCap.Square, join = StrokeJoin.Miter)) }
                            val b = ((t - 0.78f) / 0.42f).coerceIn(0f, 1f)
                            val tracedBar = Path().also { barMeasure.getSegment(0f, barMeasure.length * (1f - (1f - b).pow(4)), it, true) }
                            clipPath(barClip) { drawPath(tracedBar, Color.White, style = Stroke(180f, cap = StrokeCap.Butt)) }
                            if (t > 1.18f) drawRect(Color.White, alpha = ((t - 1.18f) / 0.14f).coerceIn(0f, 1f), size = androidx.compose.ui.geometry.Size(1600f, 1600f))
                        }
                        canvas.restore()
                    }
                    canvas.restore()
                }
            }
        }
    }
}
