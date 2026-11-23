package com.venera.desktop.installer

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 安装向导的时间轴与取值曲线。
 *
 * 全部为纯函数：界面只按毫秒取进度，不在绘制层做条件判断，便于单测钉住取值域。
 * 时间口径统一为「从本页进入时刻起算的毫秒数」。
 */
object InstallerTimeline {

    /** 启动徽记逐段生长的总时长（31 片，错峰 0..560ms + 单片 240ms）。 */
    const val EMBLEM_DRAW_MS = 800L
    const val EMBLEM_PIECES = 31

    /** 品牌字放大淡入延迟与时长。 */
    const val WORDMARK_DELAY_MS = 800L
    const val WORDMARK_FADE_MS = 300L

    /** 副题淡入延迟。 */
    const val TAGLINE_DELAY_MS = 920L

    /** 收拢进主界面开始与完成时刻（1250ms 起，300ms 收拢）。 */
    const val SPLASH_FADE_START_MS = 1250L
    const val SPLASH_FADE_MS = 300L

    /** 启动页总时长，到时进入选项页。 */
    const val SPLASH_TOTAL_MS = 1550L

    /** 安装进度推进总时长（12s 单调曲线）与完成前停留。 */
    const val INSTALL_RUN_MS = 12_000L
    const val INSTALL_HOLD_MS = 320L

    /** 目录面板展开时长。 */
    const val PATH_EXPAND_MS = 300L

    /** 按钮按压反馈时长与缩放比例。 */
    const val BUTTON_PRESS_MS = 120
    const val BUTTON_PRESS_SCALE = 0.97f

    /** 安装页五幕轮播：6s 一幕（1s 入、4s 持、1s 出）。 */
    const val SCENE_CYCLE_MS = 6_000L
    const val SCENE_FADE_MS = 1_000L
    const val SCENE_COUNT = 5

    /** 插画浮动：2s 一周期，纵向振幅 ±33dp。 */
    const val BOB_CYCLE_MS = 2_000L
    const val BOB_AMPLITUDE = 33f

    /** 背景光球漂移周期与透明度呼吸周期。 */
    const val BACK_LIGHT_DRIFT_MS = 6_000L
    const val BACK_LIGHT_BREATH_MS = 10_000L

    /** 启动徽记总体绘制进度，0 到 1。 */
    fun emblemProgress(elapsedMs: Long): Float =
        easeInOutSine(clamp01(elapsedMs.toFloat() / EMBLEM_DRAW_MS.toFloat()))

    /**
     * 第 [pieceIndex]（0..30）片标志在 [elapsedMs] 时的独立生长进度（0..1）。
     * 错峰延迟 0..560ms，单片时长 240ms，总跨度严格 800ms。
     */
    fun emblemPieceProgress(elapsedMs: Long, pieceIndex: Int, totalPieces: Int = EMBLEM_PIECES): Float {
        if (totalPieces <= 1) return emblemProgress(elapsedMs)
        val clampedIdx = pieceIndex.coerceIn(0, totalPieces - 1)
        val delayMs = (clampedIdx.toFloat() / (totalPieces - 1).toFloat()) * 560f
        val local = ((elapsedMs.toFloat() - delayMs) / 240f).coerceIn(0f, 1f)
        return easeOutCubic(local)
    }

    /** 品牌字放大淡入进度，0 到 1（含 800ms 延迟）。 */
    fun wordmarkProgress(elapsedMs: Long): Float =
        windowProgress(elapsedMs, WORDMARK_DELAY_MS, WORDMARK_FADE_MS)

    /** 副题淡入进度，0 到 1。 */
    fun taglineProgress(elapsedMs: Long): Float =
        windowProgress(elapsedMs, TAGLINE_DELAY_MS, 280L)

    /** 收拢进主界面的进度，0 到 1（1250ms 起，300ms 完成）。 */
    fun splashFadeProgress(elapsedMs: Long): Float =
        easeOutCubic(windowProgress(elapsedMs, SPLASH_FADE_START_MS, SPLASH_FADE_MS))

    /** 标题栏与选项页入场进度，与收拢同窗口。 */
    fun chromeProgress(elapsedMs: Long): Float = splashFadeProgress(elapsedMs)

    /**
     * 12s 严格单调递增平滑进度曲线：`0.72 * smoothstep(t) + 0.28 * t`。
     * 导数恒 > 0.28，确保进度条不倒退、不卡顿。
     */
    fun installProgress(elapsedMs: Long): Float {
        val x = clamp01(elapsedMs.toFloat() / INSTALL_RUN_MS.toFloat())
        val smooth = x * x * (3f - 2f * x)
        return (0.72f * smooth + 0.28f * x).coerceIn(0f, 1f)
    }

    /** 进度百分比取整，严格落在 0 到 100。 */
    fun percentLabel(progress: Float): Int =
        (clamp01(progress) * 100f).toInt().coerceIn(0, 100)

    /** 五幕介绍序号，0 到 4，按 6 秒一幕循环。 */
    fun sceneIndex(elapsedMs: Long): Int {
        if (elapsedMs < 0) return 0
        return ((elapsedMs / SCENE_CYCLE_MS) % SCENE_COUNT.toLong()).toInt()
    }

    /** 单幕透明度：1s 入、4s 保持、1s 出。 */
    fun sceneAlpha(elapsedMs: Long): Float {
        if (elapsedMs < 0) return 0f
        val phase = elapsedMs % SCENE_CYCLE_MS
        return when {
            phase < SCENE_FADE_MS -> phase.toFloat() / SCENE_FADE_MS.toFloat()
            phase < SCENE_CYCLE_MS - SCENE_FADE_MS -> 1f
            else -> (SCENE_CYCLE_MS - phase).toFloat() / SCENE_FADE_MS.toFloat()
        }.coerceIn(0f, 1f)
    }

    /** 插画纵向浮动偏移（dp，±33）。 */
    fun bobOffsetDp(elapsedMs: Long): Float {
        val phase = 2.0 * PI * (elapsedMs % BOB_CYCLE_MS).toDouble() / BOB_CYCLE_MS.toDouble()
        return (-sin(phase) * BOB_AMPLITUDE).toFloat()
    }

    /** 光球透明度呼吸系数，0.72 到 1。 */
    fun breathFactor(elapsedMs: Long, cycleMs: Long = BACK_LIGHT_BREATH_MS, phase: Float = 0f): Float {
        val radians = 2.0 * PI * (elapsedMs.toDouble() / cycleMs.toDouble()) + phase.toDouble()
        return 0.72f + 0.28f * ((sin(radians).toFloat() + 1f) / 2f)
    }

    private fun windowProgress(elapsedMs: Long, delayMs: Long, durationMs: Long): Float {
        if (elapsedMs <= delayMs) return 0f
        return clamp01((elapsedMs - delayMs).toFloat() / durationMs.toFloat())
    }

    private fun easeInOutSine(x: Float): Float =
        ((1f - cos(PI * x.toDouble())) / 2.0).toFloat()

    private fun easeOutCubic(x: Float): Float {
        val inv = 1f - clamp01(x)
        return 1f - inv * inv * inv
    }

    private fun clamp01(value: Float): Float = value.coerceIn(0f, 1f)
}
