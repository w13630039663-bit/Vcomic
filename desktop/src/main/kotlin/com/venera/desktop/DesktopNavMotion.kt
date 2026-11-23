package com.venera.desktop

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.unit.dp

/**
 * 桌面全局导航动画规范（唯一出处）。
 *
 * 口径：
 * - 同级切换（主 Tab 之间、同级二级路由互跳）：**180ms 纯交叉淡化**，无方向性位移；
 * - 层级推进 / 返回：**220ms**，Fade+Slide 组合 —— 推进时新页自右滑入+淡入、
 *   旧页向左**半距**轻移+淡出（"轻微退出"）；返回严格取反，不允许返回仍向前滑；
 * - 缓动统一 [FastOutSlowInEasing]；滑动距离 [slideDistance]（桌面轻量位移 24~40dp 取中），
 *   新旧页各走一整距 / 半距；
 * - 固定 chrome（自绘标题栏、VeneraSideBar）住在转场容器**外**，不跟随页面重播转场；
 * - 页面内部的状态变化（筛选、档位、标签）改的是屏内 state，不落 `DesktopRoute`，
 *   天然不触发这层动画 —— 不允许把屏内切换误接成路由跳转来"蹭"转场。
 *
 * 层级判定见 [desktopRouteLevel]；消费点是 `VeneraDesktopLargeScreen` 里那唯一一颗
 * 路由 `AnimatedContent` 与 Tab 分派处的同级淡化。所有页面共用这一套，不得各自设参。
 */
internal object DesktopNavMotion {

    /** 同级切换时长（纯淡化）。 */
    const val durationSameLevel: Int = 180

    /** 层级推进 / 返回时长。 */
    const val durationLevel: Int = 220

    /** 全桌面导航唯一缓动。 */
    val easing = FastOutSlowInEasing

    /** 推进 / 返回的滑动距离（调用点按密度换算成 px；旧页只走它的一半）。 */
    val slideDistance = 32.dp

    fun <T> tweenSameLevel() = tween<T>(durationSameLevel, easing = easing)
    fun <T> tweenLevel() = tween<T>(durationLevel, easing = easing)

    /** 推进：新页（更深层）自右滑入 + 淡入。`slidePx` 由调用点按密度换算好递进来。 */
    fun forwardEnter(slidePx: Int): EnterTransition =
        slideInHorizontally(tweenLevel()) { slidePx } + fadeIn(tweenLevel())

    /** 推进：旧页向左半距轻移 + 淡出（"轻微退出"，不整屏推出）。 */
    fun forwardExit(slidePx: Int): ExitTransition =
        slideOutHorizontally(tweenLevel()) { -slidePx / 2 } + fadeOut(tweenLevel())

    /** 返回：严格反转推进 —— 下层页从左半距回正 + 淡入。 */
    fun backEnter(slidePx: Int): EnterTransition =
        slideInHorizontally(tweenLevel()) { -slidePx / 2 } + fadeIn(tweenLevel())

    /** 返回：上层页向右整距滑出 + 淡出（与进入方向镜像）。 */
    fun backExit(slidePx: Int): ExitTransition =
        slideOutHorizontally(tweenLevel()) { slidePx } + fadeOut(tweenLevel())

    /** 同级：交叉淡化，无位移。 */
    fun sameLevelEnter(): EnterTransition = fadeIn(tweenSameLevel())

    /** 同级：交叉淡化，无位移。 */
    fun sameLevelExit(): ExitTransition = fadeOut(tweenSameLevel())
}
