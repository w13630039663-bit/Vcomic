package com.venera.desktop.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.venera.compose.ui.tokens.VeneraMotion
import com.venera.desktop.settings.DesktopThemeMode
import com.venera.desktop.settings.DesktopThemeRevealPrefs
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlinx.coroutines.launch

/**
 * 全窗主题切换触发器：记录触发控件中心坐标并在换肤前完成离屏截帧。
 */
@Stable
internal class DesktopThemeTrigger(
    private val onSwitch: (originInRoot: Offset?, targetMode: String) -> Unit,
) {
    /** 发起一次带全窗动效的主题模式切换。 */
    fun switchMode(targetMode: String, originInRoot: Offset? = null) {
        onSwitch(originInRoot, targetMode)
    }
}

internal val LocalDesktopThemeTrigger = staticCompositionLocalOf<DesktopThemeTrigger?> { null }

private data class StarburstSpark(
    val angleRad: Float,
    val speedBias: Float,
    val strokePx: Float,
    val tailPx: Float,
)

private val RevealSparks = List(36) { i ->
    val jitter = ((i * 73) % 100) / 100f - 0.5f
    StarburstSpark(
        angleRad = ((2.0 * PI / 36.0) * i + jitter * 0.14).toFloat(),
        speedBias = 0.78f + ((i * 37) % 42) / 100f,
        strokePx = 1.6f + ((i * 19) % 22) / 10f,
        tailPx = 18f + ((i * 29) % 24),
    )
}

/**
 * 全窗 GPU 离屏截帧转场容器：
 * 切换瞬间捕获旧主题窗口纹理，再按所选特效以 60fps 擦除揭露新主题，
 * 避免全树颜色逐一重组造成的掉帧。
 */
@Composable
internal fun DesktopThemeRevealHost(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val graphicsLayer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf<ImageBitmap?>(null) }
    var revealOrigin by remember { mutableStateOf<Offset?>(null) }
    var activeEffect by remember { mutableStateOf(DesktopThemeRevealPrefs.STARBURST) }
    var targetDark by remember { mutableStateOf(true) }
    val progress = remember { Animatable(0f) }

    val trigger = remember(graphicsLayer) {
        DesktopThemeTrigger { originInRoot, nextMode ->
            val currentMode = DesktopThemeMode.mode.value
            val currentDark = DesktopThemeMode.isDark(currentMode)
            val nextDark = DesktopThemeMode.isDark(nextMode)
            val chosenFx = DesktopThemeRevealPrefs.effect.value

            if (currentDark == nextDark || chosenFx == DesktopThemeRevealPrefs.NONE || VeneraMotion.reduceMotion) {
                DesktopThemeMode.writeBound(nextMode)
                return@DesktopThemeTrigger
            }

            scope.launch {
                val captured = runCatching { graphicsLayer.toImageBitmap() }.getOrNull()
                if (captured == null) {
                    DesktopThemeMode.writeBound(nextMode)
                    return@launch
                }
                snapshot = captured
                revealOrigin = originInRoot
                activeEffect = chosenFx
                targetDark = nextDark
                progress.snapTo(0f)
                DesktopThemeMode.writeBound(nextMode)
                progress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 760, easing = LinearEasing),
                )
                snapshot = null
            }
        }
    }

    CompositionLocalProvider(LocalDesktopThemeTrigger provides trigger) {
        Box(modifier = modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        graphicsLayer.record {
                            this@drawWithContent.drawContent()
                        }
                        drawLayer(graphicsLayer)
                    },
                content = content,
            )

            val snapBmp = snapshot
            if (snapBmp != null) {
                val raw = progress.value.coerceIn(0f, 1f)
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(99f),
                ) {
                    val ox = revealOrigin?.x ?: (size.width * 0.10f)
                    val oy = revealOrigin?.y ?: (size.height * 0.85f)
                    val maxR = hypot(
                        max(ox, size.width - ox),
                        max(oy, size.height - oy),
                    ) + 32f
                    val eased = 1f - (1f - raw).pow(3f)

                    when (activeEffect) {
                        DesktopThemeRevealPrefs.RIFT ->
                            drawRiftReveal(snapBmp, raw, eased, targetDark)
                        DesktopThemeRevealPrefs.VORTEX ->
                            drawVortexReveal(snapBmp, ox, oy, maxR, raw, eased, targetDark)
                        else ->
                            drawStarburstReveal(snapBmp, ox, oy, maxR, raw, eased, targetDark)
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawStarburstReveal(
    snap: ImageBitmap,
    ox: Float,
    oy: Float,
    maxR: Float,
    raw: Float,
    eased: Float,
    targetDark: Boolean,
) {
    val r1 = eased * maxR
    val feather = 36f
    val layerRect = Rect(0f, 0f, size.width, size.height)

    drawContext.canvas.saveLayer(layerRect, Paint())
    drawImage(
        image = snap,
        dstSize = IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1)),
    )
    if (r1 > 1f) {
        val innerStop = ((r1 - feather) / r1).coerceIn(0f, 0.99f)
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to Color.Black,
                    innerStop to Color.Black,
                    1f to Color.Transparent,
                ),
                center = Offset(ox, oy),
                radius = r1,
            ),
            radius = r1,
            center = Offset(ox, oy),
            blendMode = BlendMode.DstOut,
        )
    }
    drawContext.canvas.restore()

    val ringAlpha = (1f - raw) * 0.92f
    if (r1 <= 4f || ringAlpha <= 0.01f) return

    val ringWidth = 28.dp.toPx()
    val outerR = r1 + ringWidth * 0.6f
    val innerR = (r1 - ringWidth * 0.8f).coerceAtLeast(0f)
    val midStart = (innerR / outerR).coerceIn(0f, 0.9f)
    val glowBrush = if (targetDark) {
        Brush.radialGradient(
            colorStops = arrayOf(
                midStart to Color.Transparent,
                (midStart + (1f - midStart) * 0.45f) to Color(0xFF0A84FF).copy(alpha = ringAlpha * 0.60f),
                (midStart + (1f - midStart) * 0.72f) to Color(0xFFA78BFA).copy(alpha = ringAlpha * 0.92f),
                (midStart + (1f - midStart) * 0.88f) to Color.White.copy(alpha = ringAlpha * 0.72f),
                1f to Color.Transparent,
            ),
            center = Offset(ox, oy),
            radius = outerR,
        )
    } else {
        Brush.radialGradient(
            colorStops = arrayOf(
                midStart to Color.Transparent,
                (midStart + (1f - midStart) * 0.45f) to Color(0xFFFFA52D).copy(alpha = ringAlpha * 0.65f),
                (midStart + (1f - midStart) * 0.74f) to Color(0xFFFFF8CD).copy(alpha = ringAlpha * 0.95f),
                (midStart + (1f - midStart) * 0.90f) to Color(0xFF78D2FF).copy(alpha = ringAlpha * 0.45f),
                1f to Color.Transparent,
            ),
            center = Offset(ox, oy),
            radius = outerR,
        )
    }
    drawCircle(
        brush = glowBrush,
        radius = r1,
        center = Offset(ox, oy),
        style = Stroke(width = ringWidth),
    )

    if (raw > 0.10f) {
        val p2 = ((raw - 0.10f) / 0.90f).coerceIn(0f, 1f)
        val r2 = (1f - (1f - p2).pow(2.6f)) * maxR * 0.92f
        val echoColor = if (targetDark) Color(0xFF8B5CF6) else Color(0xFFFFD778)
        drawCircle(
            color = echoColor.copy(alpha = (1f - p2) * 0.45f),
            radius = r2,
            center = Offset(ox, oy),
            style = Stroke(width = 3.dp.toPx()),
        )
    }

    val sparkColor = if (targetDark) Color(0xFFBEE1FF) else Color(0xFFFFF4B4)
    RevealSparks.forEach { sp ->
        val sr = r1 * sp.speedBias
        val sx = ox + cos(sp.angleRad) * sr
        val sy = oy + sin(sp.angleRad) * sr
        val tr = (sr - sp.tailPx * (1f - raw * 0.45f)).coerceAtLeast(0f)
        val tx = ox + cos(sp.angleRad) * tr
        val ty = oy + sin(sp.angleRad) * tr
        drawLine(
            brush = Brush.linearGradient(
                colors = listOf(Color.Transparent, sparkColor.copy(alpha = ringAlpha)),
                start = Offset(tx, ty),
                end = Offset(sx, sy),
            ),
            start = Offset(tx, ty),
            end = Offset(sx, sy),
            strokeWidth = sp.strokePx,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawRiftReveal(
    snap: ImageBitmap,
    raw: Float,
    eased: Float,
    targetDark: Boolean,
) {
    val diag = hypot(size.width, size.height)
    val halfSpread = eased * diag * 0.65f
    val layerRect = Rect(0f, 0f, size.width, size.height)

    drawContext.canvas.saveLayer(layerRect, Paint())
    drawImage(
        image = snap,
        dstSize = IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1)),
    )
    rotate(degrees = 38f, pivot = center) {
        if (halfSpread > 1f) {
            drawRect(
                color = Color.Black,
                topLeft = Offset(-diag, center.y - halfSpread),
                size = Size(diag * 2f, halfSpread * 2f),
                blendMode = BlendMode.DstOut,
            )
        }
    }
    drawContext.canvas.restore()

    val beamAlpha = (1f - raw) * 0.92f
    val beamColor = if (targetDark) Color(0xFF64B4FF) else Color(0xFFFFDC78)
    rotate(degrees = 38f, pivot = center) {
        val beamHalf = 18.dp.toPx()
        listOf(center.y - halfSpread, center.y + halfSpread).forEach { edgeY ->
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, beamColor.copy(alpha = beamAlpha), Color.Transparent),
                    startY = edgeY - beamHalf,
                    endY = edgeY + beamHalf,
                ),
                topLeft = Offset(-diag, edgeY - beamHalf),
                size = Size(diag * 2f, beamHalf * 2f),
            )
        }
    }
}

private fun DrawScope.drawVortexReveal(
    snap: ImageBitmap,
    ox: Float,
    oy: Float,
    maxR: Float,
    raw: Float,
    eased: Float,
    targetDark: Boolean,
) {
    val sweepDeg = eased * 360f
    val layerRect = Rect(0f, 0f, size.width, size.height)

    drawContext.canvas.saveLayer(layerRect, Paint())
    drawImage(
        image = snap,
        dstSize = IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1)),
    )
    if (sweepDeg > 1f) {
        drawArc(
            color = Color.Black,
            startAngle = -90f,
            sweepAngle = sweepDeg,
            useCenter = true,
            topLeft = Offset(ox - maxR, oy - maxR),
            size = Size(maxR * 2f, maxR * 2f),
            blendMode = BlendMode.DstOut,
        )
    }
    drawContext.canvas.restore()

    val beamAlpha = (1f - raw) * 0.92f
    if (beamAlpha > 0.01f) {
        val rad = ((sweepDeg - 90f) * PI / 180.0).toFloat()
        val bx = ox + cos(rad) * maxR
        val by = oy + sin(rad) * maxR
        val edgeCol = if (targetDark) Color(0xFF0A84FF) else Color(0xFFFFBE46)
        drawLine(
            brush = Brush.linearGradient(
                colors = listOf(Color.White.copy(alpha = beamAlpha), edgeCol.copy(alpha = beamAlpha * 0.85f), Color.Transparent),
                start = Offset(ox, oy),
                end = Offset(bx, by),
            ),
            start = Offset(ox, oy),
            end = Offset(bx, by),
            strokeWidth = 6.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}
