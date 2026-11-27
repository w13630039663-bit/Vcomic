package com.venera.desktop.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.settings.DesktopMascot
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/** 测试台架的静止档：true 时待机吉祥物不跑帧循环（`waitForIdle` 才等得到静止）。运行时不提供。 */
internal val LocalMascotStill = androidx.compose.runtime.staticCompositionLocalOf { false }

/**
 * 视口内 100×100 的形状常量与节奏参数：全部按设置区规格稿逆向，勿凭观感调参。
 * 眼与底部缺口靠 [androidx.compose.ui.graphics.BlendMode] 类似的 DstOut 抠穿，
 * 因此绘制层必须离屏（`CompositingStrategy.Offscreen`），否则会抠穿下层内容。
 */
private const val VIEW = 100f
private const val STROKE_ARMS = 15.5f
private const val STROKE_WING = 13.5f
private const val EYE_W = 4.1f
private const val EYE_FULL_H = 10.2f
private const val EYE_MIN_H = 1.02f
private const val NOTCH_W = 2.6f

/** 慢眨眼曲线分段：110ms 闭 + 40ms 保持 + 140ms 开，全程 290ms。 */
private const val BLINK_CLOSE_MS = 110f
private const val BLINK_HOLD_MS = 40f
private const val BLINK_TOTAL_MS = 290f
private const val BLINK_MIN_H = 0.10f

/** 视线跟随的活跃窗口与阻尼速率；打盹判定与目标透明度系数照稿。 */
private const val GAZE_ACTIVE_MS = 2600L
private const val DOZE_ALPHA_FACTOR = 0.72f
private const val LISTEN_ALPHA_BOOST = 0.14f
private const val LISTEN_ALPHA_MAX = 0.55f

/**
 * 待机吉祥物的状态机：呼吸、随机慢眨眼、偶发低头看书、打盹与视线跟随。
 * 时间统一用帧毫秒，几何在 [tick] 里直接写进复用的 [Path]。
 */
private class MascotIdleState {
    var breathPhase = 0f
    var breathCycleCurrent = 4.8f

    var bodyAlpha = 0.28f
    var bodySink = 0f
    var dozeBlend = 0f
    var eyeHeightFactor = 1f
    var gazeX = 0f
    var gazeY = 0f

    var pointerNormX = 0f
    var pointerNormY = 0f
    var lastPointerMoveMs = Long.MIN_VALUE / 2

    var blinkActive = false
    var blinkElapsedMs = 0f
    var nextBlinkAtMs = 0L

    var readingActive = false
    var readingEndMs = 0L
    var readingGazeX = 0f
    var readingGazeY = 0f
    var pendingReadingAtMs = 0L

    var lastActivityMs = 0L
    var inputListening = false
    var modeOverride = DesktopMascot.ModeOverride.AUTO

    var idleAlphaTarget = 0.28f
    var breathCycleTarget = 4.8f
    var breathAmp = 1f
    var dozeDelayMs = 15000L
    var gazeFollow = true

    val armsPath = Path()
    val leftWingPath = Path()
    val rightWingPath = Path()
    val junctionPath = Path()
    var notchTop = 0f
    var notchBottom = 0f
    var eyeLeftX = 0f
    var eyeRightX = 0f
    var eyeTop = 0f
    var eyeH = 0f
    var eyeRx = 0f

    /** 首帧基线：活动计时与眨眼排程从当前帧起算，避免开屏立即触发。 */
    fun bootstrap(nowMs: Long) {
        if (lastActivityMs == 0L) lastActivityMs = nowMs
        if (nextBlinkAtMs == 0L) nextBlinkAtMs = nowMs + Random.nextLong(3800, 6200)
    }

    /** 指针活动：更新视线目标并重置打盹计时。 */
    fun onPointer(nx: Float, ny: Float, nowMs: Long) {
        pointerNormX = nx
        pointerNormY = ny
        lastPointerMoveMs = nowMs
        markActivity(nowMs)
    }

    /** 外部活动（输入、取数等）：只重置打盹计时。 */
    fun markActivity(nowMs: Long) {
        lastActivityMs = nowMs
    }

    /** 轻点：打盹时只唤醒；清醒时慢眨眼一次。 */
    fun onTap(nowMs: Long) {
        markActivity(nowMs)
        if (!isDozing(nowMs)) triggerBlink(allowFollowUpRead = false, nowMs)
    }

    private fun listening(): Boolean = when (modeOverride) {
        DesktopMascot.ModeOverride.LISTENING -> true
        DesktopMascot.ModeOverride.IDLE, DesktopMascot.ModeOverride.DOZING -> false
        DesktopMascot.ModeOverride.AUTO -> inputListening
    }

    private fun isDozing(nowMs: Long): Boolean = when (modeOverride) {
        DesktopMascot.ModeOverride.DOZING -> true
        DesktopMascot.ModeOverride.IDLE, DesktopMascot.ModeOverride.LISTENING -> false
        DesktopMascot.ModeOverride.AUTO -> nowMs - lastActivityMs >= dozeDelayMs
    }

    private fun triggerBlink(allowFollowUpRead: Boolean, nowMs: Long) {
        blinkActive = true
        blinkElapsedMs = 0f
        nextBlinkAtMs = nowMs + Random.nextLong(3800, 6800)
        if (allowFollowUpRead && !listening() && !isDozing(nowMs) && Random.nextDouble() < 0.35) {
            pendingReadingAtMs = nowMs + 520
        }
    }

    /** 慢眨眼曲线：闭、停、开三段，中间不回升。 */
    private fun blinkCurve(elapsedMs: Float): Float {
        val closeT = (elapsedMs / BLINK_CLOSE_MS).coerceIn(0f, 1f)
        if (elapsedMs < BLINK_CLOSE_MS) {
            val e = closeT * closeT * (3f - 2f * closeT)
            return 1f - (1f - BLINK_MIN_H) * e
        }
        if (elapsedMs < BLINK_CLOSE_MS + BLINK_HOLD_MS) return BLINK_MIN_H
        val openT = ((elapsedMs - BLINK_CLOSE_MS - BLINK_HOLD_MS) / (BLINK_TOTAL_MS - BLINK_CLOSE_MS - BLINK_HOLD_MS)).coerceIn(0f, 1f)
        val e = 1f - (1f - openT).pow(3f)
        return BLINK_MIN_H + (1f - BLINK_MIN_H) * e
    }

    private fun damp(current: Float, target: Float, rate: Float, dt: Float): Float =
        current + (target - current) * (1f - exp(-rate * dt))

    /** 每帧推进：呼吸 → 眨眼与低头排程 → 目标值阻尼 → 视线优先级 → 写几何。 */
    fun tick(nowMs: Long, dt: Float) {
        bootstrap(nowMs)
        val dozing = isDozing(nowMs)
        val listening = listening()

        // A. 呼吸相位连续累加，打盹放慢；周期平滑跟随避免滑块拖动时跳帧。
        val targetCycle = if (dozing) breathCycleTarget * 1.35f else breathCycleTarget
        breathCycleCurrent = damp(breathCycleCurrent, targetCycle, 2.2f, dt)
        breathPhase = (breathPhase + dt / breathCycleCurrent * 2f * Math.PI.toFloat()) % (2f * Math.PI.toFloat())
        val breath = 0.5f - 0.5f * kotlin.math.cos(breathPhase)
        val wingLift = breath * 1.4f * breathAmp
        val earSpread = breath * 0.7f * breathAmp

        // B. 打盹吞掉眨眼与低头；清醒时按排程触发，眨眼后偶发低头看书。
        if (dozing) {
            blinkActive = false
            readingActive = false
            pendingReadingAtMs = 0L
        } else {
            if (!blinkActive && nowMs >= nextBlinkAtMs) {
                triggerBlink(allowFollowUpRead = modeOverride != DesktopMascot.ModeOverride.LISTENING, nowMs = nowMs)
            }
            if (blinkActive) {
                blinkElapsedMs += dt * 1000f
                if (blinkElapsedMs >= BLINK_TOTAL_MS) blinkActive = false
            }
            if (pendingReadingAtMs != 0L && nowMs >= pendingReadingAtMs) {
                pendingReadingAtMs = 0L
                if (!listening) {
                    readingActive = true
                    readingGazeX = if (Random.nextDouble() < 0.5) -0.48f else 0.48f
                    readingGazeY = 0.72f
                    readingEndMs = nowMs + Random.nextLong(1800, 3000)
                }
            }
            if (readingActive && nowMs >= readingEndMs) readingActive = false
        }

        // C. 目标透明度与身体下沉，强阻尼平滑。
        val targetAlpha = when {
            dozing -> idleAlphaTarget * DOZE_ALPHA_FACTOR
            listening -> min(idleAlphaTarget + LISTEN_ALPHA_BOOST, LISTEN_ALPHA_MAX)
            else -> idleAlphaTarget
        }
        bodyAlpha = damp(bodyAlpha, targetAlpha, 4.2f, dt)
        bodySink = damp(bodySink, if (dozing) 1.3f else 0f, 3.6f, dt)
        dozeBlend = damp(dozeBlend, if (dozing) 1f else 0f, 3.8f, dt)
        val targetEyeH = if (dozing) 0.42f else 1f
        eyeHeightFactor = if (blinkActive) blinkCurve(blinkElapsedMs) else damp(eyeHeightFactor, targetEyeH, 5.2f, dt)

        // D. 视线优先级：打盹归零 → 聚焦抬眼 → 鼠标跟随 → 低头看书 → 回正。
        val pointerActive = gazeFollow && nowMs - lastPointerMoveMs < GAZE_ACTIVE_MS
        val targetGazeX: Float
        val targetGazeY: Float
        when {
            dozing -> { targetGazeX = 0f; targetGazeY = 0f }
            listening -> { targetGazeX = if (pointerActive) pointerNormX * 0.35f else 0f; targetGazeY = -0.75f }
            pointerActive && !readingActive -> { targetGazeX = pointerNormX; targetGazeY = pointerNormY }
            readingActive -> { targetGazeX = readingGazeX; targetGazeY = readingGazeY }
            else -> { targetGazeX = 0f; targetGazeY = 0f }
        }
        gazeX = damp(gazeX, targetGazeX, 3.4f, dt)
        gazeY = damp(gazeY, targetGazeY, 3.4f, dt)

        writeGeometry(earSpread, wingLift)
    }

    /** 按定稿公式写四件形状与两枚镂空件。 */
    private fun writeGeometry(earSpread: Float, wingLift: Float) {
        val sink = bodySink
        val leftArmX = 27f - earSpread
        val rightArmX = 73f + earSpread
        val topY = 19f + sink
        val vertexY = 58f + sink
        armsPath.reset()
        armsPath.moveTo(leftArmX, topY)
        armsPath.lineTo(50f, vertexY)
        armsPath.lineTo(rightArmX, topY)

        val wingRootY = 68.5f + sink
        val wingCtrlY = 57.5f + sink - wingLift * 0.5f
        val wingTipY = 63.5f + sink - wingLift
        leftWingPath.reset()
        leftWingPath.moveTo(50f, wingRootY)
        leftWingPath.quadraticBezierTo(31f, wingCtrlY, 13.5f, wingTipY)
        rightWingPath.reset()
        rightWingPath.moveTo(50f, wingRootY)
        rightWingPath.quadraticBezierTo(69f, wingCtrlY, 86.5f, wingTipY)

        junctionPath.reset()
        junctionPath.moveTo(39f, 48f + sink)
        junctionPath.lineTo(61f, 48f + sink)
        junctionPath.lineTo(64f, 68f + sink)
        junctionPath.quadraticBezierTo(50f, 76f + sink, 36f, 68f + sink)
        junctionPath.close()

        notchTop = 72.5f + sink
        notchBottom = 80.5f + sink

        eyeH = max(EYE_MIN_H, EYE_FULL_H * eyeHeightFactor)
        val gazeDx = gazeX.coerceIn(-1f, 1f) * 1.6f
        val gazeDy = gazeY.coerceIn(-1f, 1f) * 1.2f
        // 打盹混合：清醒时眼睛缩在中心，打盹时底边锚定成眯眼，不跳位。
        val centerY = 56.8f + sink + gazeDy
        val centerTop = centerY - eyeH * 0.5f
        val bottomAnchoredTop = 61.9f + sink + gazeDy - eyeH
        eyeTop = centerTop + (bottomAnchoredTop - centerTop) * dozeBlend
        eyeLeftX = 44.2f + gazeDx - EYE_W * 0.5f
        eyeRightX = 55.8f + gazeDx - EYE_W * 0.5f
        eyeRx = min(2.05f, eyeH * 0.5f)
    }
}

/**
 * 待机吉祥物「薇薇」：V 形身 + 摊开书页，呼吸 / 慢眨眼 / 打盹 / 视线跟随。
 *
 * @param awakeKey 变化即重置打盹计时（输入框键入、取数触发等外部活动）
 * @param modifier 命中与视线跟随的指针范围，空态落位传整片空白区
 * @param size 形状渲染边长；命中区自动外扩 12dp
 * @param spec 调参（透明度、呼吸节奏、打盹延迟、视线跟随、状态锁档）
 * @param focusedListening 输入框聚焦 = 「我在听」抬眼；聚焦下长时间无活动仍会打盹
 * @param prompt 吉祥物下方的伴随内容（提示语），与形状整体居中、间距自配
 */
@Composable
internal fun DesktopMascotIdle(
    awakeKey: Any?,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    spec: DesktopMascot.Spec,
    focusedListening: Boolean = false,
    prompt: @Composable () -> Unit = {},
) {
    val ink = VeneraTokens.color.textPrimary
    val state = remember { MascotIdleState() }
    var frame by remember { mutableLongStateOf(0L) }

    // 设置变更直接写状态机目标值，动画参数跨重组连续。
    LaunchedEffect(spec) {
        state.idleAlphaTarget = spec.idleAlpha
        state.breathCycleTarget = spec.breathCycle
        state.breathAmp = spec.breathAmp
        state.dozeDelayMs = (spec.dozeDelay.toLong() * 1000L)
        state.gazeFollow = spec.gazeFollow
        state.modeOverride = spec.modeOverride
    }
    LaunchedEffect(focusedListening) { state.inputListening = focusedListening }

    // 输入框键入、发车等活动经键值变化重置打盹计时。
    LaunchedEffect(awakeKey) { state.markActivity(state.lastActivityMs) }

    // 静止档或窗口失焦时跳过动画循环，聚焦时按低频定拍更新以降低 CPU/GPU 唤醒。
    val windowFocused = androidx.compose.ui.platform.LocalWindowInfo.current.isWindowFocused
    if (!LocalMascotStill.current) {
        LaunchedEffect(windowFocused) {
            if (!windowFocused) return@LaunchedEffect
            var lastMs = System.currentTimeMillis()
            while (true) {
                kotlinx.coroutines.delay(66L)
                val nowMs = System.currentTimeMillis()
                val dt = ((nowMs - lastMs) / 1000f).coerceIn(0.001f, 0.2f)
                lastMs = nowMs
                state.tick(nowMs, dt)
                frame++
            }
        }
    }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                // 指针范围即视线跟随半径的基准；移动与按压都算活动。
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        val cx = this@pointerInput.size.width * 0.5f
                        val cy = this@pointerInput.size.height * 0.5f
                        val radius = max(this@pointerInput.size.width, this@pointerInput.size.height) * 0.34f
                        val nx = ((change.position.x - cx) / radius).coerceIn(-1f, 1f)
                        val ny = ((change.position.y - cy) / radius).coerceIn(-1f, 1f)
                        val nowMs = event.changes.maxOfOrNull { it.uptimeMillis } ?: 0L
                        state.onPointer(nx, ny, nowMs)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val hitInteraction = remember { MutableInteractionSource() }
        androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(size + 12.dp)
                    .clickable(
                        interactionSource = hitInteraction,
                        indication = com.venera.desktop.ui.components.DesktopNoIndication,
                    ) { state.onTap(System.currentTimeMillis()) },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(
                    modifier = Modifier
                        .size(size)
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
                ) {
                    frame
                    scale(this.size.minDimension / VIEW, this.size.minDimension / VIEW, pivot = Offset.Zero) {
                        drawMascot(state, ink)
                    }
                }
            }
            prompt()
        }
    }
}

private fun DrawScope.drawMascot(state: MascotIdleState, ink: Color) {
    val bodyColor = ink.copy(alpha = state.bodyAlpha)
    drawPath(state.armsPath, color = bodyColor, style = Stroke(width = STROKE_ARMS, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(state.leftWingPath, color = bodyColor, style = Stroke(width = STROKE_WING, cap = StrokeCap.Round))
    drawPath(state.rightWingPath, color = bodyColor, style = Stroke(width = STROKE_WING, cap = StrokeCap.Round))
    drawPath(state.junctionPath, color = bodyColor)

    // 镂空件：DstOut 抠穿半透明形象，等价定稿的 mask 真镂空。
    val cut = Color.Black
    drawLine(
        color = cut,
        start = Offset(50f, state.notchTop),
        end = Offset(50f, state.notchBottom),
        strokeWidth = NOTCH_W,
        cap = StrokeCap.Round,
        blendMode = androidx.compose.ui.graphics.BlendMode.DstOut,
    )
    val eyeSize = Size(EYE_W, state.eyeH)
    val corner = CornerRadius(state.eyeRx, state.eyeRx)
    drawRoundRect(color = cut, topLeft = Offset(state.eyeLeftX, state.eyeTop), size = eyeSize, cornerRadius = corner, blendMode = androidx.compose.ui.graphics.BlendMode.DstOut)
    drawRoundRect(color = cut, topLeft = Offset(state.eyeRightX, state.eyeTop), size = eyeSize, cornerRadius = corner, blendMode = androidx.compose.ui.graphics.BlendMode.DstOut)
}
