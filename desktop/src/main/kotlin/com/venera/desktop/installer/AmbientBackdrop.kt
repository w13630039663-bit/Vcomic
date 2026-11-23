package com.venera.desktop.installer

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 云母窗口环境光层：主极光团 6s 缓慢漂移，辅光晕团 10s 呼吸。
 *
 * @param clockMs 自窗口出现起的毫秒数，换页与主题切换时不重置相位。
 */
@Composable
fun AmbientBackdrop(clockMs: Long, modifier: Modifier = Modifier) {
    val palette = LocalInstallerPalette.current
    Canvas(modifier) {
        drawOrb(
            clockMs = clockMs,
            phase = 0f,
            base = Offset(size.width * 0.20f, size.height * 0.14f),
            radiusX = size.width * 0.14f,
            radiusY = size.height * 0.09f,
            color = palette.orbPrimary,
            peakAlpha = 1f,
        )
        drawOrb(
            clockMs = clockMs,
            phase = PI.toFloat(),
            base = Offset(size.width * 0.82f, size.height * 0.86f),
            radiusX = size.width * 0.10f,
            radiusY = size.height * 0.08f,
            color = palette.orbSecondary,
            peakAlpha = 1f,
        )
    }
}

private fun DrawScope.drawOrb(
    clockMs: Long,
    phase: Float,
    base: Offset,
    radiusX: Float,
    radiusY: Float,
    color: Color,
    peakAlpha: Float,
) {
    val angle = 2.0 * PI * (clockMs.toDouble() / InstallerTimeline.BACK_LIGHT_DRIFT_MS.toDouble()) + phase
    val center = Offset(
        x = base.x + radiusX * cos(angle).toFloat(),
        y = base.y + radiusY * sin(angle).toFloat(),
    )
    val breath = InstallerTimeline.breathFactor(clockMs, phase = phase)
    val finalColor = color.copy(alpha = (color.alpha * peakAlpha * breath).coerceIn(0f, 1f))
    val radius = ORB_RADIUS.toPx() * (0.94f + 0.08f * breath)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(finalColor, Color.Transparent),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}

private val ORB_RADIUS = 210.dp
