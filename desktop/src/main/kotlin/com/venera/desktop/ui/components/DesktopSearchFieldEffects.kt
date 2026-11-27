package com.venera.desktop.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.venera.compose.ui.tokens.LocalVeneraDarkTheme
import com.venera.compose.ui.tokens.VeneraMotion
import com.venera.desktop.settings.DesktopPixelBuddyBus
import com.venera.desktop.settings.PixelBuddyCardRevealPhase
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.math.sin

/**
 * 监听结果列表的用户滚动动作：一旦开始滚动立刻通知像素伙伴中断当前台阶动画并避让回墙。
 */
@Composable
fun PixelBuddyScrollInterruptEffect(scrolling: Boolean) {
    LaunchedEffect(scrolling) {
        if (scrolling) DesktopPixelBuddyBus.reportUserScroll()
    }
}

/**
 * 注册空结果区或加载失败重试按钮的窗口位置，供像素伙伴打盹或举锤提醒。
 */
@Composable
fun Modifier.registerPixelBuddyStatusAnchor(isRetry: Boolean): Modifier {
    DisposableEffect(isRetry) {
        onDispose { DesktopPixelBuddyBus.reportStatusAnchor(isRetry, null) }
    }
    return this.onGloballyPositioned { coords ->
        val rect = coords.boundsInWindow()
        if (rect.width > 20f && rect.height > 20f) {
            DesktopPixelBuddyBus.reportStatusAnchor(isRetry, rect)
        }
    }
}

/**
 * 将当前可见的漫画或画廊卡片边框注册给像素伙伴层，并在搜索描绘会话中绘制暗幕实时擦除与点亮扫光。
 */
@Composable
fun Modifier.registerPixelBuddyCard(key: String): Modifier {
    DisposableEffect(key) {
        onDispose { DesktopPixelBuddyBus.unregisterCard(key) }
    }
    val positioned = this.onGloballyPositioned { coords ->
        val rect = coords.boundsInWindow()
        if (rect.width > 40f && rect.height > 40f) {
            DesktopPixelBuddyBus.registerCard(key, rect)
        }
    }
    val paintActive by DesktopPixelBuddyBus.cardPaintActive.collectAsState()
    if (!paintActive || VeneraMotion.reduceMotion) return positioned
    return positioned.pixelBuddyCardRevealOverlay(key)
}

@Composable
private fun Modifier.pixelBuddyCardRevealOverlay(key: String): Modifier {
    val isDark = LocalVeneraDarkTheme.current
    val veilBaseColor = if (isDark) Color(0xFF181A21) else Color(0xFFF3F5FA)
    val phaseFlow = remember(key) {
        DesktopPixelBuddyBus.cardPaint
            .map { it.phaseOf(key) }
            .distinctUntilChanged()
    }
    val phase by phaseFlow.collectAsState(initial = PixelBuddyCardRevealPhase.None)
    val awakenProgress = remember(key) { Animatable(0f) }
    val awakenedSession = (phase as? PixelBuddyCardRevealPhase.Awakened)?.sessionId ?: 0
    LaunchedEffect(awakenedSession) {
        if (awakenedSession <= 0) {
            awakenProgress.snapTo(0f)
            return@LaunchedEffect
        }
        awakenProgress.snapTo(0f)
        awakenProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 560, easing = VeneraMotion.easingDecelerate),
        )
    }
    if (phase is PixelBuddyCardRevealPhase.None) return this

    return this.drawWithContent {
        drawContent()
        val radiusPx = 12.dp.toPx()
        val cardPath = Path().apply {
            addRoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(radiusPx, radiusPx)))
        }
        clipPath(cardPath) {
            when (val st = phase) {
                is PixelBuddyCardRevealPhase.Waiting -> {
                    val accent = Color(st.accentArgb)
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                accent.copy(alpha = 0.16f),
                                veilBaseColor.copy(alpha = 0.72f),
                                veilBaseColor.copy(alpha = 0.78f),
                            ),
                        ),
                    )
                    drawRoundRect(
                        color = accent.copy(alpha = 0.28f),
                        cornerRadius = CornerRadius(radiusPx, radiusPx),
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
                is PixelBuddyCardRevealPhase.Skimming -> {
                    val accent = Color(st.accentArgb)
                    val p = st.progress.coerceIn(0f, 1f)
                    val veilLeft = size.width * p
                    if (veilLeft < size.width) {
                        val veilW = size.width - veilLeft
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    accent.copy(alpha = 0.24f),
                                    veilBaseColor.copy(alpha = 0.72f),
                                    veilBaseColor.copy(alpha = 0.80f),
                                ),
                                startX = veilLeft,
                                endX = size.width,
                            ),
                            topLeft = Offset(veilLeft, 0f),
                            size = Size(veilW, size.height),
                        )
                        drawRect(
                            color = accent.copy(alpha = 0.65f),
                            topLeft = Offset(veilLeft, 0f),
                            size = Size(2.dp.toPx().coerceAtMost(veilW), size.height),
                        )
                    }
                    drawRoundRect(
                        color = accent.copy(alpha = 0.56f),
                        cornerRadius = CornerRadius(radiusPx, radiusPx),
                        style = Stroke(width = 1.2.dp.toPx()),
                    )
                }
                is PixelBuddyCardRevealPhase.Awakened -> {
                    val p = awakenProgress.value
                    if (p < 1f) {
                        val accent = Color(st.accentArgb)
                        val veilFade = (1f - (p / 0.6f).coerceIn(0f, 1f))
                        if (veilFade > 0f) {
                            drawRect(color = veilBaseColor.copy(alpha = 0.36f * veilFade))
                        }
                        val bandW = size.width * 0.44f
                        val cx = -bandW + (size.width + bandW * 2f) * p
                        val fade = sin(p * Math.PI).toFloat()
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    accent.copy(alpha = 0.22f * fade),
                                    Color.White.copy(alpha = 0.14f * fade),
                                    Color.Transparent,
                                ),
                                start = Offset(cx - bandW * 0.5f, 0f),
                                end = Offset(cx + bandW * 0.5f, size.height),
                            ),
                        )
                        drawRoundRect(
                            color = accent.copy(alpha = 0.48f * fade),
                            cornerRadius = CornerRadius(radiusPx, radiusPx),
                            style = Stroke(width = 1.dp.toPx()),
                        )
                    }
                }
                PixelBuddyCardRevealPhase.None -> Unit
            }
        }
    }
}

/**
 * 搜索框确认时的整框流光扫过效果：按回车确认搜索后，一道柔和的斜向高光从左向右掠过整个搜索框。
 */
@Composable
internal fun Modifier.desktopSearchConfirmSweep(
    confirmTick: Int,
    sweepColor: Color,
): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(confirmTick) {
        if (confirmTick <= 0) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = if (VeneraMotion.reduceMotion) VeneraMotion.durationNormal else 420,
                easing = VeneraMotion.easingDecelerate,
            ),
        )
    }
    val p = progress.value
    if (p <= 0f || p >= 1f) return this
    return this.drawWithContent {
        drawContent()
        if (VeneraMotion.reduceMotion) {
            val alpha = sin(p * Math.PI).toFloat() * 0.14f
            drawRect(color = sweepColor.copy(alpha = alpha))
        } else {
            val bandW = size.width * 0.38f
            val centerX = -bandW + (size.width + bandW * 2f) * p
            val fade = sin(p * Math.PI).toFloat()
            val brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    sweepColor.copy(alpha = 0.24f * fade),
                    Color.White.copy(alpha = 0.18f * fade),
                    sweepColor.copy(alpha = 0.20f * fade),
                    Color.Transparent,
                ),
                start = Offset(centerX - bandW * 0.5f, 0f),
                end = Offset(centerX + bandW * 0.5f, size.height),
            )
            drawRect(brush = brush)
        }
    }
}

/**
 * 搜索框柔和变形宽度：未聚焦时保持舒展默认宽度，聚焦后平滑展宽，确认/失焦后柔和缩回。
 */
@Composable
internal fun rememberSearchMorphWidth(
    focused: Boolean,
    collapsedWidth: Dp?,
    expandedWidth: Dp?,
): Dp? {
    if (collapsedWidth == null || expandedWidth == null) return null
    val target = if (focused) expandedWidth else collapsedWidth
    val animated by animateDpAsState(
        targetValue = target,
        animationSpec = VeneraMotion.tweenNormal(),
        label = "search-morph-width",
    )
    return animated
}

/**
 * 搜索框柔和变形宽度比例：未聚焦时收窄到 [collapsedFraction]（如 0.56f），聚焦后平滑展开至全宽 1f。
 */
@Composable
internal fun rememberSearchMorphFraction(
    focused: Boolean,
    collapsedFraction: Float?,
): Float? {
    if (collapsedFraction == null) return null
    val target = if (focused) 1f else collapsedFraction.coerceIn(0.25f, 1f)
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = VeneraMotion.tweenNormal(),
        label = "search-morph-fraction",
    )
    return animated
}

private val CAT_IDLE = listOf(
    ".#..........#...",
    ".##........##...",
    ".############...",
    ".############...",
    ".###o####o###..#",
    ".###o####o###..#",
    ".############s##",
    ".#############..",
    "..#.#....#.#....",
    "..#.#....#.#....",
)

private val CAT_HAPPY = listOf(
    ".#..........#...",
    ".##........##...",
    ".############..#",
    ".###s####s###..#",
    ".##s#s##s#s##s##",
    ".#############..",
    "..###########...",
    "..#.#....#.#....",
)

/**
 * 蹲在搜索框右上角框沿的极简 3px 像素小猫：
 * 聚焦时跃上框角，打字时随输入轻轻蹦跶，确认搜索时开心跳起冒星，失焦后优雅退场。
 */
@Composable
internal fun DesktopSearchPixelPerch(
    focused: Boolean,
    typingTick: Int,
    confirmTick: Int,
    modifier: Modifier = Modifier,
) {
    val presence by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = VeneraMotion.tweenNormal(),
        label = "search-buddy-presence",
    )
    val hop = remember { Animatable(0f) }
    LaunchedEffect(typingTick) {
        if (typingTick <= 0 || !focused || VeneraMotion.reduceMotion) return@LaunchedEffect
        hop.snapTo(0f)
        hop.animateTo(1f, tween(durationMillis = 160, easing = VeneraMotion.easingStandard))
        hop.snapTo(0f)
    }
    LaunchedEffect(confirmTick) {
        if (confirmTick <= 0 || VeneraMotion.reduceMotion) return@LaunchedEffect
        hop.snapTo(0f)
        hop.animateTo(1f, tween(durationMillis = 260, easing = VeneraMotion.easingDecelerate))
        hop.snapTo(0f)
    }

    if (presence <= 0.02f) return
    val hopLift = sin(hop.value * Math.PI).toFloat() * 7f
    val enterLift = (1f - presence) * 14f
    val happy = hop.value > 0.05f

    Canvas(
        modifier = modifier
            .size(width = 32.dp, height = 20.dp)
            .offset(y = (-17).dp)
            .graphicsLayer {
                alpha = presence
                translationY = enterLift - hopLift
                scaleX = 0.85f + 0.15f * presence
                scaleY = 0.85f + 0.15f * presence
            },
    ) {
        val rows = if (happy) CAT_HAPPY else CAT_IDLE
        val px = size.width / 16f
        val bodyColor = Color(0xFF947FD6)
        val shadowColor = Color(0xFF735EB5)
        val eyeColor = Color(0xFF141413)
        for (ry in rows.indices) {
            val line = rows[ry]
            var rx = 0
            while (rx < line.length) {
                val ch = line[rx]
                var run = 1
                while (rx + run < line.length && line[rx + run] == ch) run++
                val color = when (ch) {
                    '#' -> bodyColor
                    's' -> shadowColor
                    'o' -> eyeColor
                    else -> null
                }
                if (color != null) {
                    drawRect(
                        color = color,
                        topLeft = Offset(rx * px, ry * px),
                        size = Size(run * px, px),
                    )
                }
                rx += run
            }
        }
    }
}
