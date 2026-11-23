package com.venera.compose.ui.tokens

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * 全局动画 Token（时长 / 缓动 / 位移）与页面进出场预设。
 *
 * 与 [VeneraSpacingTokens] 同一条规矩：新写 UI 代码的动画时长、缓动曲线、
 * 位移距离不得再裸写 `tween(220)` / `FastOutSlowInEasing` / `12.dp` 一类散数，
 * 一律吃本文件。存量不追溯。
 */
object VeneraMotion {
    // ── 时长（毫秒）──

    /** 微反馈：按压、勾选、小图标变色。 */
    const val durationFast: Int = 120

    /** 常规：卡片入场、内容淡入、展开收起。 */
    const val durationNormal: Int = 220

    /** 页面级：路由切换、抽屉、大面积面板。 */
    const val durationPage: Int = 300

    /** 慢速：英雄图、跨大位移的强调动画。 */
    const val durationSlow: Int = 420

    // ── 缓动 ──

    /** 常规过渡（进、出通用）。 */
    val easingStandard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** 元素进入视口：先快后缓的减速感。 */
    val easingDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** 元素离开视口：先缓后快的加速感。 */
    val easingAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** 强调型（英雄图 / 大面板），带轻微过冲前摇。 */
    val easingEmphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    // ── 位移 ──

    /** 卡片 / 组件入场的默认滑入距离。 */
    val slideItem: Dp = 16.dp

    /** 卡片入场最大上浮距离，终态归零。 */
    val enterTranslationY: Dp = 14.dp

    /** 卡片入场起始缩放，终态恒 1。 */
    const val enterBaseScale: Float = 0.985f

    /** 卡片入场错峰步长（毫秒）。 */
    const val staggerStepMs: Int = 35

    /** 卡片入场错峰上限档数，超出后不再增加延迟。 */
    const val staggerMaxSteps: Int = 6

    /**
     * rail 首载 stagger 的模糊起点：blur 从这个值收到 0（
     * 本文件此前无 blur 条目，此处是唯一出处）。
     */
    val staggerBlurStart: Dp = 4.dp

    /**
     * 动效降级全局读数（新增）。桌面宿主没有系统级 reduced-motion
     * 读数（全仓无先例），先落这枚默认 `false` 的本地开关；偏好接线（设置项）来源待定。
     * 为真时位移 / 缩放 / 模糊全部置零，动效退化为纯 opacity 过渡（时长不变）。
     */
    var reduceMotion: Boolean by mutableStateOf(false)
        private set

    /** [reduceMotion] 的唯一写口（设置偏好接上后从偏好面调；接线前无人调，恒 false）。 */
    fun setReduceMotionEnabled(enabled: Boolean) {
        reduceMotion = enabled
    }

    /** 页面级横滑（进 / 返回）的位移比例基准，实际值按屏宽算。 */
    const val slidePageFraction: Float = 0.12f

    // ── 组合工具：tween 快捷写法 ──

    fun <T> tweenFast() = tween<T>(durationFast, easing = easingStandard)
    fun <T> tweenNormal() = tween<T>(durationNormal, easing = easingStandard)
    fun <T> tweenPage() = tween<T>(durationPage, easing = easingStandard)
    fun <T> tweenSlow() = tween<T>(durationSlow, easing = easingStandard)
    fun <T> tweenIn() = tween<T>(durationNormal, easing = easingDecelerate)
    fun <T> tweenOut() = tween<T>(durationNormal, easing = easingAccelerate)
}

// 页面级进出场与返回预设，供导航转场或 AnimatedContent 使用。

/** 压入：新页自右侧滑入 + 淡入。 */
fun VeneraPageEnter(): EnterTransition =
    slideInHorizontally(VeneraMotion.tweenIn()) { it / 6 } + fadeIn(VeneraMotion.tweenIn())

/** 压入：旧页向左微移 + 淡出（保持在新页底下再收掉）。 */
fun VeneraPageExit(): ExitTransition =
    slideOutHorizontally(VeneraMotion.tweenOut()) { -it / 8 } + fadeOut(VeneraMotion.tweenOut())

/** 返回：上层页向右滑出 + 淡出。 */
fun VeneraPagePopExit(): ExitTransition =
    slideOutHorizontally(VeneraMotion.tweenOut()) { it / 6 } + fadeOut(VeneraMotion.tweenOut())

/** 返回：底下那页从左微移回正 + 淡入。 */
fun VeneraPagePopEnter(): EnterTransition =
    slideInHorizontally(VeneraMotion.tweenIn()) { -it / 8 } + fadeIn(VeneraMotion.tweenIn())

/** 纵向浮层（底部抽屉 / 全屏面板）进场。 */
fun VeneraSheetEnter(): EnterTransition =
    slideInVertically(VeneraMotion.tweenIn()) { it } + fadeIn(VeneraMotion.tweenIn())

/** 纵向浮层退场。 */
fun VeneraSheetExit(): ExitTransition =
    slideOutVertically(VeneraMotion.tweenOut()) { it } + fadeOut(VeneraMotion.tweenOut())

// ── 复用工具：标准卡片入场 ──

/**
 * 标准卡片入场：错峰延迟 + 低弹弹簧推进到 1。
 * 已播标记按 [cardKey] 存 saveable，滚动回收后重组合直接落定终态，不重放。
 * 读返回值的 value 驱动 graphicsLayer；卡片无自带绘制层时用 [VeneraCardEnter]。
 *
 * @param index 网格序号，决定错峰延迟（超出 [VeneraMotion.staggerMaxSteps] 档取上限）。
 * @param cardKey 卡片稳定标识（uid / url / id），同时是动画与已播标记的 key。
 */
@Composable
fun rememberCardEnterProgress(
    index: Int,
    cardKey: String,
): Animatable<Float, AnimationVector1D> {
    val played = rememberSaveable(cardKey) { mutableStateOf(false) }
    val entrance = remember(cardKey) { Animatable(if (played.value) 1f else 0f) }
    LaunchedEffect(cardKey) {
        if (played.value) return@LaunchedEffect
        delay(index.coerceAtMost(VeneraMotion.staggerMaxSteps) * VeneraMotion.staggerStepMs.toLong())
        entrance.animateTo(
            1f,
            spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        )
        played.value = true
    }
    return entrance
}

/**
 * 标准卡片入场包装：淡入 / 上浮 / 微缩放，进度同 [rememberCardEnterProgress]。
 * 全仓卡片入场唯一口径；新功能的卡片必须吃本工具或其进度，不得散写时长、错峰与弹簧。
 *
 * @param index 网格序号，决定错峰延迟。
 * @param cardKey 卡片稳定标识（uid / url / id），同时是动画与已播标记的 key。
 */
@Composable
fun VeneraCardEnter(
    index: Int,
    cardKey: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val entrance = rememberCardEnterProgress(index, cardKey)
    Box(
        modifier.graphicsLayer {
            alpha = entrance.value
            translationY = (1f - entrance.value) * VeneraMotion.enterTranslationY.toPx()
            val baseScale = VeneraMotion.enterBaseScale +
                (1f - VeneraMotion.enterBaseScale) * entrance.value
            scaleX = baseScale
            scaleY = baseScale
        },
    ) {
        content()
    }
}
