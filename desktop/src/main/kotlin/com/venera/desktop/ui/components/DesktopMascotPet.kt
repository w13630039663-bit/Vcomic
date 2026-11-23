package com.venera.desktop.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.venera.compose.ui.tokens.VeneraTokens
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** 桌宠姿态全表；事件映射见 [petPoseFor]，THINKING 与 DOZE 暂无事件源仅保留能力。 */
enum class MascotPetPose {
    IDLE, WORKING, DOWNLOADING, RETURN, FAVORITE, TAP, ANOMALY, ALERT, THINKING, SHY, DONE, DOZE, NIGHT,
}

/** 视口 120×120 的形状常量与节奏参数：按 v3 动画稿逆向，勿凭观感调参。 */
private const val VIEW = 120f
private const val STROKE_ARM = 17.5f
private const val STROKE_WING = 15.2f
private const val EYE_W = 4.5f
private const val EYE_BASE_H = 10.6f
private const val EYE_ANOMALY_H = 8.6f
private const val EYE_MIN_H = 1.4f
private const val NOTCH_W = 3.4f
private const val IDLE_ALPHA = 0.84f
private const val DOZE_ALPHA = 0.62f

/** 慢眨眼曲线：65ms 闭 + 25ms 保持 + 75ms 开。 */
private const val BLINK_CLOSE_MS = 65f
private const val BLINK_HOLD_MS = 25f
private const val BLINK_TOTAL_MS = 165f
private const val BLINK_MIN_H = 0.10f

/** 一次性姿态到点自动回待机；气泡仍按 CueBus holdMs 常驻，与姿态回退互不影响。 */
private val AUTO_RETURN_SEC = mapOf(
    MascotPetPose.DONE to 2.6f,
    MascotPetPose.ALERT to 0.9f,
    MascotPetPose.TAP to 1.2f,
    MascotPetPose.SHY to 2.2f,
)

/** 事件 → 姿态映射表：一次钉死，接新事件只补此表。 */
internal fun petPoseFor(cue: DesktopPetCue): MascotPetPose = when (cue.priority) {
    8 -> MascotPetPose.ANOMALY
    7 -> MascotPetPose.ALERT
    6 -> MascotPetPose.DONE
    5 -> if (cue == DesktopPetCue.DOWNLOADING) MascotPetPose.DOWNLOADING else MascotPetPose.WORKING
    else -> when (cue) {
        DesktopPetCue.TAP_AGAIN -> MascotPetPose.SHY
        DesktopPetCue.TOGGLE_FAVORITE,
        DesktopPetCue.ADD_BOOKSHELF,
        DesktopPetCue.REMOVE_FAVORITE,
        -> MascotPetPose.FAVORITE
        DesktopPetCue.SEARCH_EMPTY,
        DesktopPetCue.COLLECTION_EMPTY,
        DesktopPetCue.LIST_END,
        -> MascotPetPose.ANOMALY
        DesktopPetCue.NETWORK_RESTORED,
        DesktopPetCue.WINDOW_RETURN,
        DesktopPetCue.BACK_HOME,
        DesktopPetCue.FIRST_LAUNCH,
        -> MascotPetPose.RETURN
        DesktopPetCue.LATE_NIGHT -> MascotPetPose.NIGHT
        DesktopPetCue.IDLE,
        DesktopPetCue.NO_TASK,
        DesktopPetCue.AWAY,
        DesktopPetCue.DOZING,
        -> MascotPetPose.IDLE
        else -> MascotPetPose.TAP
    }
}

/** 桌宠姿态状态机：弹簧物理 + 三眼形 + 装饰粒子，节奏全部按 v3 稿逆向。 */
private class MascotPetState {
    var breathPhase = 0f
    var breathPeriodCurrent = 4.8f
    var workPhase = 0f

    var bodyTx = 0f
    var bodyTy = 0f
    var velBodyTy = 0f
    var bodyRot = 0f
    var scaleX = 1f
    var scaleY = 1f
    var velScaleX = 0f
    var velScaleY = 0f

    var leftArmDeg = 0f
    var rightArmDeg = 0f
    var velRightArm = 0f
    var leftWingDeg = 0f
    var rightWingDeg = 0f
    var velLeftWing = 0f
    var velRightWing = 0f

    var eyeHeightFactor = 1f
    var dozeAnchorBlend = 0f
    var smileBlend = 0f
    var worrySlant = 0f
    var gazeX = 0f
    var gazeY = 0f

    var sparkleAlpha = 0f
    var heartAlpha = 0f
    var returnTicksAlpha = 0f
    var tapTicksAlpha = 0f
    var anomalyAlpha = 0f
    var monoAlpha = IDLE_ALPHA

    var blinkActive = false
    var blinkElapsedMs = 0f
    var nextBlinkAtMs = 0L
    var readingActive = false
    var readingEndMs = 0L
    var readingGazeX = 0f
    var readingGazeY = 0f
    var pendingReadAtMs = 0L
    var stateEnteredAtMs = 0L

    val armsPath = Path()
    val leftWingPath = Path()
    val rightWingPath = Path()
    val junctionPath = Path()
    val leftArcPath = Path()
    val rightArcPath = Path()

    var pose = MascotPetPose.IDLE

    /** 事件切入：一次性态由 [AUTO_RETURN_SEC] 回退；TAP 连击冲量在此注入。 */
    fun enter(newPose: MascotPetPose, nowMs: Long) {
        if (newPose == pose) return
        pose = newPose
        stateEnteredAtMs = nowMs
        readingActive = false
        pendingReadAtMs = 0L
        when (newPose) {
            MascotPetPose.TAP -> {
                scaleX = 1.11f
                scaleY = 0.89f
                velScaleX = -0.8f
                velScaleY = 1.4f
                velBodyTy = -36f
                velRightArm = 165f
                blinkActive = true
                blinkElapsedMs = 0f
            }
            MascotPetPose.SHY -> velBodyTy = -30f
            MascotPetPose.FAVORITE -> {
                velBodyTy = -68f
                velScaleX = -0.65f
                velScaleY = 0.95f
            }
            MascotPetPose.RETURN -> {
                velBodyTy = -46f
                velScaleY = 0.55f
            }
            else -> Unit
        }
    }

    fun bootstrap(nowMs: Long) {
        if (nextBlinkAtMs == 0L) nextBlinkAtMs = nowMs + Random.nextLong(2400, 4600)
        if (stateEnteredAtMs == 0L) stateEnteredAtMs = nowMs
    }

    private fun damp(current: Float, target: Float, rate: Float, dt: Float): Float =
        current + (target - current) * (1f - exp(-rate * dt))

    private fun stepSpring(pos: Float, vel: Float, target: Float, stiffness: Float, damping: Float, dt: Float): Pair<Float, Float> {
        val nextVel = (vel + (target - pos) * stiffness * dt) * exp(-damping * dt)
        return (pos + nextVel * dt) to nextVel
    }

    private fun blinkCurve(elapsedMs: Float): Float {
        if (elapsedMs < BLINK_CLOSE_MS) {
            val t = elapsedMs / BLINK_CLOSE_MS
            return 1f - (1f - BLINK_MIN_H) * (t * t * (3f - 2f * t))
        }
        if (elapsedMs < BLINK_CLOSE_MS + BLINK_HOLD_MS) return BLINK_MIN_H
        val t = ((elapsedMs - BLINK_CLOSE_MS - BLINK_HOLD_MS) / (BLINK_TOTAL_MS - BLINK_CLOSE_MS - BLINK_HOLD_MS)).coerceIn(0f, 1f)
        return BLINK_MIN_H + (1f - BLINK_MIN_H) * (1f - (1f - t).pow(3f))
    }

    /** 每帧推进：呼吸 → 眨眼与低头 → 目标姿态 → 弹簧与阻尼 → 写几何。 */
    fun tick(nowMs: Long, dt: Float, effectivePose: MascotPetPose) {
        bootstrap(nowMs)
        val elapsedSec = (nowMs - stateEnteredAtMs) / 1000f

        val targetPeriod = when (effectivePose) {
            MascotPetPose.DOZE -> 6.4f
            MascotPetPose.NIGHT -> 5.6f
            else -> 4.8f
        }
        breathPeriodCurrent = damp(breathPeriodCurrent, targetPeriod, 2.2f, dt)
        breathPhase = (breathPhase + dt / breathPeriodCurrent * 2f * Math.PI.toFloat()) % (2f * Math.PI.toFloat())
        val breath = (0.5f - 0.5f * cos(breathPhase) + sin(breathPhase * 2f) * 0.06f).coerceIn(0f, 1f)
        workPhase = (workPhase + dt * 2.5f) % (2f * Math.PI.toFloat())

        val canRandomBlink = effectivePose == MascotPetPose.IDLE || effectivePose == MascotPetPose.WORKING || effectivePose == MascotPetPose.DOWNLOADING
        if (canRandomBlink && !blinkActive && nowMs >= nextBlinkAtMs) {
            blinkActive = true
            blinkElapsedMs = 0f
            nextBlinkAtMs = nowMs + Random.nextLong(3500, 6000)
            if (effectivePose == MascotPetPose.IDLE && Random.nextDouble() < 0.38) pendingReadAtMs = nowMs + 450
        }
        if (blinkActive) {
            blinkElapsedMs += dt * 1000f
            if (blinkElapsedMs >= BLINK_TOTAL_MS) blinkActive = false
        }
        if (effectivePose == MascotPetPose.IDLE && pendingReadAtMs in 1 until nowMs) {
            pendingReadAtMs = 0L
            readingActive = true
            readingGazeX = if (Random.nextDouble() < 0.5) -0.45f else 0.45f
            readingGazeY = 0.72f
            readingEndMs = nowMs + Random.nextLong(1800, 2400)
        }
        if (readingActive && (effectivePose != MascotPetPose.IDLE || nowMs >= readingEndMs)) readingActive = false

        // 目标姿态（数值全部照 v3 稿）
        var targetTx = 0f
        var targetScaleX = 1f
        var targetScaleY = 1f
        var targetTy = -breath * 1.1f
        var targetRot = 0f
        var targetLeftArm = -breath * 1.0f
        var targetRightArm = breath * 1.0f
        var targetLeftWing = breath * 2.0f
        var targetRightWing = -breath * 2.0f
        var targetEyeH = 1f
        var targetDozeAnchor = 0f
        var targetSmile = 0f
        var targetWorry = 0f
        var targetGazeX = 0f
        var targetGazeY = 0f
        var targetSparkle = 0f
        var targetHeart = 0f
        var targetReturnTicks = 0f
        var targetTapTicks = 0f
        var targetAnomaly = 0f
        var targetMonoAlpha = IDLE_ALPHA

        when (effectivePose) {
            MascotPetPose.WORKING -> {
                targetRot = 7f + sin(workPhase) * 0.9f
                targetTy = -1.2f + sin(workPhase * 1.4f) * 1.1f
                targetLeftArm = -1.5f
                targetRightArm = 1.5f
                targetLeftWing = 2.6f * sin(workPhase)
                targetRightWing = -2.6f * sin(workPhase + 0.9f)
                targetGazeX = 0.32f + sin(nowMs * 0.0018f) * 0.52f
                targetGazeY = -0.35f
                targetSparkle = 1f
            }
            MascotPetPose.DOWNLOADING -> {
                targetRot = 2.4f
                targetTy = -0.8f + sin(workPhase * 2.2f) * 0.8f
                targetLeftWing = 4.2f * sin(workPhase * 2.2f)
                targetRightWing = -4.2f * sin(workPhase * 2.2f + 0.7f)
                targetLeftArm = -1.2f
                targetRightArm = 1.2f
                targetGazeX = 0.5f
                targetGazeY = 0.62f
                targetEyeH = 0.72f
                targetSparkle = 0.9f
            }
            MascotPetPose.RETURN -> {
                targetTy = -2.8f + sin(elapsedSec * 2.6f) * 1.4f
                targetRot = sin(elapsedSec * 2.2f) * 3.2f
                targetLeftArm = -5.5f - breath * 1.2f
                targetRightArm = 5.5f + breath * 1.2f
                targetLeftWing = 3.2f + breath * 1.5f
                targetRightWing = -3.2f - breath * 1.5f
                targetSmile = 1f
                targetReturnTicks = 0.9f
                targetHeart = 0.95f
            }
            MascotPetPose.FAVORITE -> {
                targetTy = -2.4f + sin(elapsedSec * 3f) * 1f
                targetLeftArm = -2.8f
                targetRightArm = 2.8f
                targetLeftWing = 6.2f + breath * 1.4f
                targetRightWing = -6.2f - breath * 1.4f
                targetSmile = 1f
                targetHeart = 1f
            }
            MascotPetPose.TAP -> {
                val perk = elapsedSec < 0.55f
                targetRightArm = if (perk) 10.5f else breath * 1f
                targetLeftArm = -breath * 1f
                targetRot = if (elapsedSec < 0.45f) -2.2f else 0f
                targetTapTicks = if (elapsedSec < 0.65f) max(0f, 1f - elapsedSec / 0.65f) else 0f
            }
            MascotPetPose.ANOMALY -> {
                val shake = if (elapsedSec < 0.8f) sin(elapsedSec * 26f) * exp(-elapsedSec * 5.2f) * 4.4f else 0f
                targetTx = shake
                targetRot = shake * 0.55f
                targetTy = 1.6f
                targetLeftArm = 4.6f
                targetRightArm = -4.6f
                targetLeftWing = -3.4f + breath * 0.8f
                targetRightWing = 3.4f - breath * 0.8f
                targetWorry = 1f
                targetGazeY = 0.25f
                targetAnomaly = 0.92f
            }
            MascotPetPose.ALERT -> {
                targetEyeH = 1.18f
                targetGazeY = -0.5f
                targetLeftArm = -3.4f - breath * 0.8f
                targetRightArm = 3.4f + breath * 0.8f
                targetTy = -2.2f
            }
            MascotPetPose.THINKING -> {
                targetLeftWing = 2.2f * sin(elapsedSec * 2.6f)
                targetRightWing = -2.2f * sin(elapsedSec * 2.6f + 1.2f)
                targetRot = sin(elapsedSec * 1.3f) * 3.4f
                targetLeftArm = -2f
                targetRightArm = 2f
                targetGazeX = sin(elapsedSec * 1.7f) * 0.7f
                targetGazeY = -0.15f
                targetEyeH = 0.85f
            }
            MascotPetPose.SHY -> {
                targetScaleX = 0.94f
                targetScaleY = 0.94f
                targetTx = sin(elapsedSec * 5.2f) * 1.4f
                targetRot = sin(elapsedSec * 4.1f) * 2.2f
                targetLeftWing = -2.2f
                targetRightWing = 2.2f
                targetWorry = 0.55f
                targetEyeH = 0.62f
                targetGazeX = -0.3f
                targetGazeY = 0.3f
            }
            MascotPetPose.DONE -> {
                if (elapsedSec < 0.35f) {
                    targetLeftWing = -5.5f
                    targetRightWing = 5.5f
                    targetTy = 0.6f
                } else {
                    val beat = max(0f, sin((elapsedSec - 0.35f) * Math.PI.toFloat() * 2f / 0.75f))
                    targetLeftWing = 5f * beat + breath * 1.4f
                    targetRightWing = -5f * beat - breath * 1.4f
                    targetTy = -1.6f
                }
                targetSmile = 1f
                targetHeart = 1f
            }
            MascotPetPose.DOZE -> {
                targetTy = 3.2f - breath * 0.7f
                targetLeftArm = 1.6f - breath * 0.6f
                targetRightArm = -1.6f + breath * 0.6f
                targetLeftWing = -1.8f + breath * 1.3f
                targetRightWing = 1.8f - breath * 1.3f
                targetDozeAnchor = 1f
                targetMonoAlpha = DOZE_ALPHA
                val sleepCycle = elapsedSec % 7.2f
                targetEyeH = if (sleepCycle in 3.2f..5.7f) 0.16f else 0.40f
            }
            MascotPetPose.NIGHT -> {
                targetEyeH = 0.55f
                targetGazeY = 0.1f
            }
            MascotPetPose.IDLE -> Unit
        }

        val (nsx, nvsx) = stepSpring(scaleX, velScaleX, targetScaleX, 210f, 14f, dt)
        val (nsy, nvsy) = stepSpring(scaleY, velScaleY, targetScaleY, 210f, 14f, dt)
        scaleX = nsx; velScaleX = nvsx
        scaleY = nsy; velScaleY = nvsy
        val (nty, nvty) = stepSpring(bodyTy, velBodyTy, targetTy, 110f, 13f, dt)
        bodyTy = nty; velBodyTy = nvty
        val (nra, nvra) = stepSpring(rightArmDeg, velRightArm, targetRightArm, 150f, 12f, dt)
        rightArmDeg = nra; velRightArm = nvra

        bodyTx = damp(bodyTx, targetTx, 18f, dt)
        bodyRot = damp(bodyRot, targetRot, 7.5f, dt)
        leftArmDeg = damp(leftArmDeg, targetLeftArm, 7.5f, dt)
        val (nlw, nvlw) = stepSpring(leftWingDeg, velLeftWing, targetLeftWing, 170f, 12.5f, dt)
        leftWingDeg = nlw; velLeftWing = nvlw
        val (nrw, nvrw) = stepSpring(rightWingDeg, velRightWing, targetRightWing, 170f, 12.5f, dt)
        rightWingDeg = nrw; velRightWing = nvrw

        eyeHeightFactor = if (blinkActive && effectivePose != MascotPetPose.RETURN && effectivePose != MascotPetPose.FAVORITE && effectivePose != MascotPetPose.DOZE) {
            blinkCurve(blinkElapsedMs)
        } else {
            damp(eyeHeightFactor, targetEyeH, if (effectivePose == MascotPetPose.DOZE) 3.8f else 8.5f, dt)
        }
        dozeAnchorBlend = damp(dozeAnchorBlend, targetDozeAnchor, 4.5f, dt)
        smileBlend = damp(smileBlend, targetSmile, 10f, dt)
        worrySlant = damp(worrySlant, targetWorry, 8.5f, dt)
        gazeX = damp(gazeX, targetGazeX, 4.2f, dt)
        gazeY = damp(gazeY, targetGazeY, 4.2f, dt)
        sparkleAlpha = damp(sparkleAlpha, targetSparkle, 6.5f, dt)
        heartAlpha = damp(heartAlpha, targetHeart, 6.5f, dt)
        returnTicksAlpha = damp(returnTicksAlpha, targetReturnTicks, 7.5f, dt)
        tapTicksAlpha = damp(tapTicksAlpha, targetTapTicks, 11f, dt)
        anomalyAlpha = damp(anomalyAlpha, targetAnomaly, 6.5f, dt)
        monoAlpha = damp(monoAlpha, targetMonoAlpha, 4.5f, dt)

        writeGeometry()
    }

    /** 按当前姿态量写全部路径；眼与弯月弧坐标每帧重算。 */
    private fun writeGeometry() {
        val st = pose
        armsPath.reset()
        armsPath.moveTo(60f, 74f)
        armsPath.lineTo(36f, 28f)
        armsPath.moveTo(60f, 74f)
        armsPath.lineTo(84f, 28f)
        // 旋转走绘制层 pivot，路径保持静止
        leftWingPath.reset()
        leftWingPath.moveTo(59f, 87f)
        leftWingPath.quadraticBezierTo(37f, 71f, 15f, 80.5f)
        rightWingPath.reset()
        rightWingPath.moveTo(61f, 87f)
        rightWingPath.quadraticBezierTo(83f, 71f, 105f, 80.5f)
        junctionPath.reset()
        junctionPath.moveTo(45f, 55f)
        junctionPath.lineTo(75f, 55f)
        junctionPath.lineTo(74f, 79f)
        junctionPath.quadraticBezierTo(60f, 89f, 46f, 79f)
        junctionPath.close()

        // 弯月弧：坐标已含视线偏移
        val adx = gazeX.coerceIn(-1f, 1f) * 2.1f
        val ady = gazeY.coerceIn(-1f, 1f) * 1.5f - 0.5f
        leftArcPath.reset()
        leftArcPath.moveTo(50.4f + adx, 68.2f + ady)
        leftArcPath.quadraticBezierTo(54f + adx, 61.6f + ady, 57.6f + adx, 68.2f + ady)
        rightArcPath.reset()
        rightArcPath.moveTo(62.4f + adx, 68.2f + ady)
        rightArcPath.quadraticBezierTo(66f + adx, 61.6f + ady, 69.6f + adx, 68.2f + ady)

        val eyeBaseH = if (st == MascotPetPose.ANOMALY) EYE_ANOMALY_H else EYE_BASE_H
        val eyeH = max(EYE_MIN_H, eyeBaseH * eyeHeightFactor)
        val gazeDx = gazeX.coerceIn(-1f, 1f) * 2.1f
        val gazeDy = gazeY.coerceIn(-1f, 1f) * 1.5f
        val baseCenterY = 66.2f + if (st == MascotPetPose.ANOMALY) 1f else 0f + gazeDy
        val centerTopY = baseCenterY - eyeH * 0.5f
        val bottomAnchorTopY = 71.5f + gazeDy - eyeH
        val eyeTopY = centerTopY + (bottomAnchorTopY - centerTopY) * dozeAnchorBlend
        val leftCx = 54f + gazeDx
        val rightCx = 66f + gazeDx

        eyesLeftTop = Offset(leftCx - EYE_W * 0.5f, eyeTopY)
        eyesRightTop = Offset(rightCx - EYE_W * 0.5f, eyeTopY)
        eyesSize = Size(EYE_W, eyeH)
        eyesRx = min(EYE_W * 0.5f, eyeH * 0.5f)
        eyesLeftCenter = Offset(leftCx, eyeTopY + eyeH * 0.5f)
        eyesRightCenter = Offset(rightCx, eyeTopY + eyeH * 0.5f)
        eyePillOpacity = 1f - smileBlend
        arcOpacity = smileBlend
    }

    var eyesLeftTop = Offset.Zero
    var eyesRightTop = Offset.Zero
    var eyesSize = Size(EYE_W, EYE_BASE_H)
    var eyesRx = 2.25f
    var eyesLeftCenter = Offset.Zero
    var eyesRightCenter = Offset.Zero
    var eyePillOpacity = 1f
    var arcOpacity = 0f
}

/**
 * 桌宠薇薇：极简剪影 + 弹簧物理 + 三眼形 + 装饰粒子。
 *
 * @param pose 事件映射出的姿态（[petPoseFor]），变化时注入弹簧冲量
 * @param modifier 绘制范围；形状按视口等比缩放居中
 * @param size 形状渲染边长
 */
@Composable
internal fun DesktopMascotPet(
    pose: MascotPetPose,
    modifier: Modifier = Modifier,
    size: Dp = 124.dp,
) {
    val ink = VeneraTokens.color.textPrimary
    val starColor = Color(0xFFFBBF24)
    val heartColor = Color(0xFFF87171)
    val state = remember { MascotPetState() }
    var frame by remember { mutableLongStateOf(0L) }
    // 深夜态按时钟切（23–5 点），非闲置计时器，不与“无本地计时器”决策冲突
    var lateNight by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            val hour = java.time.LocalTime.now().hour
            lateNight = hour >= 23 || hour < 5
            kotlinx.coroutines.delay(60_000L)
        }
    }

    LaunchedEffect(pose) {
        state.enter(pose, System.currentTimeMillis())
        // 一次性姿态到点收势回待机；气泡仍按事件 holdMs 常驻，互不影响
        val autoSec = AUTO_RETURN_SEC[pose]
        if (autoSec != null) {
            kotlinx.coroutines.delay((autoSec * 1000f).toLong())
            if (pose == state.pose) state.enter(MascotPetPose.IDLE, System.currentTimeMillis())
        }
    }

    val windowFocused = androidx.compose.ui.platform.LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(windowFocused) {
        if (!windowFocused) return@LaunchedEffect
        // 用定频 66ms(15fps) 替代每帧 withFrameNanos 空转，且窗口失焦自动暂停以降低 CPU/GPU 唤醒。
        var lastMs = System.currentTimeMillis()
        while (true) {
            kotlinx.coroutines.delay(66L)
            val nowMs = System.currentTimeMillis()
            val dtSec = ((nowMs - lastMs) / 1000f).coerceIn(0.001f, 0.08f)
            lastMs = nowMs
            val effective = if (pose == MascotPetPose.IDLE && lateNight) MascotPetPose.NIGHT else pose
            state.tick(nowMs, dtSec, effective)
            frame++
        }
    }

    androidx.compose.foundation.layout.Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    // 层整体透明度 + 离屏：形状满色单层叠加，眼与缺口 DstOut 抠穿，
                    // 等价定稿的 mask 方案（避免多笔画同色 alpha 叠加出现色差台阶）
                    alpha = state.monoAlpha
                    compositingStrategy = CompositingStrategy.Offscreen
                },
        ) {
            frame
            val sc = this.size.minDimension / VIEW
            scale(sc, sc, pivot = Offset.Zero) {
                drawMascotPet(state, ink, starColor, heartColor)
            }
        }
    }
}

private fun DrawScope.drawMascotPet(state: MascotPetState, ink: Color, starColor: Color, heartColor: Color) {
    // 层 alpha 由 graphicsLayer 统一承担；层内一律满色，杜绝多笔画色差
    val bodyColor = ink
    val bodyPivot = Offset(60f, 82f)
    withTransform({
        translate(state.bodyTx, state.bodyTy)
        translate(bodyPivot.x, bodyPivot.y)
        rotate(state.bodyRot)
        scale(state.scaleX, state.scaleY)
        translate(-bodyPivot.x, -bodyPivot.y)
    }) {
        // V 臂各自绕肩点旋转；书页翼绕翼根旋转
        rotate(state.leftArmDeg, pivot = Offset(60f, 74f)) {
            drawLine(bodyColor, Offset(60f, 74f), Offset(36f, 28f), STROKE_ARM, StrokeCap.Round)
        }
        rotate(state.rightArmDeg, pivot = Offset(60f, 74f)) {
            drawLine(bodyColor, Offset(60f, 74f), Offset(84f, 28f), STROKE_ARM, StrokeCap.Round)
        }
        drawPath(state.junctionPath, bodyColor)
        rotate(state.leftWingDeg, pivot = Offset(59f, 87f)) {
            drawPath(state.leftWingPath, bodyColor, style = Stroke(STROKE_WING, cap = StrokeCap.Round))
        }
        rotate(state.rightWingDeg, pivot = Offset(61f, 87f)) {
            drawPath(state.rightWingPath, bodyColor, style = Stroke(STROKE_WING, cap = StrokeCap.Round))
        }

        // 眼与缺口：DstOut 抠穿（剪影表情），胶囊与弯月弧按混合量部分抠穿实现交叉淡变；内倾委屈眼按量旋转
        if (state.eyePillOpacity > 0.01f) {
            val slant = state.worrySlant * 17f
            rotate(slant, pivot = state.eyesLeftCenter) {
                drawRoundRect(Color.Black.copy(alpha = state.eyePillOpacity), state.eyesLeftTop, state.eyesSize, CornerRadius(state.eyesRx, state.eyesRx), blendMode = androidx.compose.ui.graphics.BlendMode.DstOut)
            }
            rotate(-slant, pivot = state.eyesRightCenter) {
                drawRoundRect(Color.Black.copy(alpha = state.eyePillOpacity), state.eyesRightTop, state.eyesSize, CornerRadius(state.eyesRx, state.eyesRx), blendMode = androidx.compose.ui.graphics.BlendMode.DstOut)
            }
        }
        if (state.arcOpacity > 0.01f) {
            val arcStroke = Stroke(2.8f, cap = StrokeCap.Round)
            drawPath(state.leftArcPath, Color.Black.copy(alpha = state.arcOpacity), style = arcStroke, blendMode = androidx.compose.ui.graphics.BlendMode.DstOut)
            drawPath(state.rightArcPath, Color.Black.copy(alpha = state.arcOpacity), style = arcStroke, blendMode = androidx.compose.ui.graphics.BlendMode.DstOut)
        }
        drawLine(
            Color.Black,
            Offset(60f, 86f), Offset(60f, 98.5f), NOTCH_W, StrokeCap.Round,
            blendMode = androidx.compose.ui.graphics.BlendMode.DstOut,
        )
    }

    // 装饰层：剪影之外正常绘制
    val now = System.currentTimeMillis()
    if (state.sparkleAlpha > 0.01f) {
        val pulse = { phase: Float -> 0.55f + 0.45f * sin(now * 0.0046f + phase) }
        drawQuadrantStar(starColor, Offset(22f, 34f), 5.2f * (0.75f + pulse(0f) * 0.35f))
        drawQuadrantStar(starColor, Offset(101f, 41f), 6.2f * (0.72f + pulse(2.1f) * 0.38f))
        drawQuadrantStar(starColor, Offset(96f, 22f), 3.6f * (0.75f + pulse(4.2f) * 0.35f))
    }
    if (state.heartAlpha > 0.01f) {
        val cycle = ((now / 1000f) % 2.1f) / 2.1f
        var hx = 97f
        var hy = 45f + sin(now * 0.0032f) * 3.2f
        var hScale = 0.96f + 0.08f * sin(now * 0.0032f)
        if (state.pose == MascotPetPose.FAVORITE) {
            hx = 93f + sin(cycle * Math.PI.toFloat() * 2f) * 2.2f
            hy = 52f - cycle * 22f
            hScale = if (cycle < 0.16f) 0.55f + (cycle / 0.16f) * 0.55f else 1.1f - (cycle - 0.16f) * 0.18f
        }
        val env = if (state.pose == MascotPetPose.FAVORITE) {
            if (cycle < 0.15f) cycle / 0.15f else if (cycle > 0.72f) (1f - cycle) / 0.28f else 1f
        } else 1f
        drawHeart(heartColor, Offset(hx, hy), hScale, state.heartAlpha * env.coerceIn(0f, 1f))
    }
    if (state.returnTicksAlpha > 0.01f) {
        val c = ink.copy(alpha = state.returnTicksAlpha)
        drawLine(c, Offset(21f, 52f), Offset(15f, 49f), 2.4f, StrokeCap.Round)
        drawLine(c, Offset(20f, 60f), Offset(15f, 60.5f), 2.4f, StrokeCap.Round)
        drawLine(c, Offset(64f, 21f), Offset(67f, 15f), 2.4f, StrokeCap.Round)
    }
    if (state.tapTicksAlpha > 0.01f) {
        val c = ink.copy(alpha = state.tapTicksAlpha)
        drawLine(c, Offset(95f, 21f), Offset(101f, 16f), 2.4f, StrokeCap.Round)
        drawLine(c, Offset(100f, 30f), Offset(106f, 29f), 2.4f, StrokeCap.Round)
    }
    if (state.anomalyAlpha > 0.01f) {
        val a = state.anomalyAlpha * (0.55f + 0.45f * sin(now * 0.0036f))
        drawLine(starColor.copy(alpha = a), Offset(97f, 34f), Offset(97f, 43f), 3f, StrokeCap.Round)
        drawCircle(heartColor.copy(alpha = a), 1.9f, Offset(97f, 48.5f))
    }
}

/** 四角星：两段二次贝塞尔凹边成星。 */
private fun DrawScope.drawQuadrantStar(color: Color, center: Offset, r: Float) {
    val p = Path().apply {
        moveTo(center.x, center.y - r)
        quadraticBezierTo(center.x, center.y, center.x + r, center.y)
        quadraticBezierTo(center.x, center.y, center.x, center.y + r)
        quadraticBezierTo(center.x, center.y, center.x - r, center.y)
        quadraticBezierTo(center.x, center.y, center.x, center.y - r)
        close()
    }
    drawPath(p, color)
}

/** 心形：双贝塞尔凸弧。 */
private fun DrawScope.drawHeart(color: Color, center: Offset, scaleF: Float, alpha: Float) {
    val p = Path().apply {
        moveTo(center.x, center.y + 5.6f)
        cubicTo(center.x - 6.8f, center.y + 1.2f, center.x - 7.4f, center.y - 5.2f, center.x - 3.3f, center.y - 5.6f)
        cubicTo(center.x - 1.2f, center.y - 5.8f, center.x, center.y - 4.1f, center.x, center.y - 2.8f)
        cubicTo(center.x, center.y - 4.1f, center.x + 1.2f, center.y - 5.8f, center.x + 3.3f, center.y - 5.6f)
        cubicTo(center.x + 7.4f, center.y - 5.2f, center.x + 6.8f, center.y + 1.2f, center.x, center.y + 5.6f)
        close()
    }
    withTransform({
        translate(center.x, center.y)
        scale(scaleF, scaleF)
        translate(-center.x, -center.y)
    }) {
        drawPath(p, color.copy(alpha = alpha))
    }
}
