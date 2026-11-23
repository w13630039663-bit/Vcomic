// SPDX-License-Identifier: Apache-2.0
// Ported from Kyant0/AndroidLiquidGlass (LiquidBottomTabs.kt)
// 三层结构：半透明外壳 + 隐藏着色图标采样层 + 指示器药丸；效果顺序必须为 color filter -> blur -> lens。
package com.venera.compose.components.backdrop

import com.venera.compose.components.LocalWindowWidthDp
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.venera.compose.components.wideScreenChromeMaxWidth
import com.venera.compose.ui.tokens.VeneraTokens
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/** 官方 catalog 里的默认高度：外壳 64dp，内容层/指示器 56dp（配合 4dp padding 得到 56 + 8 = 64）。 */
private val ShellHeight = 64.dp
private val ContentHeight = 56.dp
private val HorizontalPadding = 4.dp

// 外壳采用 blur(8dp) + lens(24dp, 24dp) 且关闭 depthEffect，避免折射形变过强。
private val ShellBlurRadius = 8.dp
private val ShellRefractionHeight = 24.dp
private val ShellRefractionAmount = 24.dp

// 指示器药丸：官方数值 lens(10dp, 14dp, dispersion)，且**乘 pressProgress**。
// 静止时折射为 0（干净），按下时才出现折射 + 色散。
private val PillRefractionHeight = 10.dp
private val PillRefractionAmount = 14.dp

/**
 * 官方 Liquid Glass 悬浮底栏。
 *
 * @param selectedTabIndex 当前选中下标（用 lambda 传入以避免把整个组件拖进重组）。
 * @param onTabSelected 选中回调；拖拽松手与点击都会走这里。
 * @param backdrop 页面内容的录制层（[layerBackdrop]），底栏不在其中，避免自引用递归。
 */
@Composable
fun VeneraLiquidGlassNavBar(
    selectedTabIndex: () -> Int,
    onTabSelected: (index: Int) -> Unit,
    backdrop: Backdrop,
    tabsCount: Int,
    modifier: Modifier = Modifier,
    isLightTheme: Boolean = true,
    // 选中着色必须取自主题色板，禁止硬编码固定蓝色。
    accentColor: Color = VeneraTokens.color.primary,
    // 官方 catalog 默认 0.4 的容器色。半透明玻璃要「透」必须压低这一层，
    // 否则再强的折射也被这层不透明表面盖住了。
    containerColor: Color = if (isLightTheme) Color(0xFFFAFAFA).copy(0.22f) else Color(0xFF121212).copy(0.28f),
    content: @Composable RowScope.() -> Unit
) {
    val tabsBackdrop = rememberLayerBackdrop()

    // 宽屏收口与悬浮胶囊共用 WideScreenPolicy；必须用 requiredWidth 覆盖宿主传入的 fillMaxWidth min 约束。
    val capScreenWidth = LocalWindowWidthDp
    val cap = wideScreenChromeMaxWidth(capScreenWidth)
        ?.let { Modifier.requiredWidth(it) }
        ?: Modifier

    BoxWithConstraints(
        modifier.then(cap),
        contentAlignment = Alignment.CenterStart
    ) {
        val density = LocalDensity.current
        val tabWidth = with(density) {
            (constraints.maxWidth.toFloat() - 8f.dp.toPx()) / tabsCount
        }

        // 拖拽越界时整条栏做小幅反向位移（官方用 EaseOut 压缩位移量）。
        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxWidth).fastCoerceIn(-1f, 1f)
                with(density) {
                    4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        // 必须直接读取 selectedTabIndex() 的值而非将 lambda 传入 snapshotFlow，
        // 否则 lambda 捕获的普通 val 在 snapshotFlow 中恒为常量导致药丸不更新。
        val selectedIndex = selectedTabIndex()
        var currentIndex by remember { mutableIntStateOf(selectedIndex) }
        // 区分宿主回写与内部手势：宿主同步 currentIndex 时抑制下一次回调，避免触发双重震动。
        var suppressNextNotification by remember { mutableStateOf(false) }
        // 外部导航是权威来源：一旦宿主的下标变了，药丸立即跟过去。
        // 拖拽期间由内部驱动（下方 drag 回调），松手时回调宿主，形成单一数据流。
        LaunchedEffect(selectedIndex) {
            if (currentIndex != selectedIndex) {
                suppressNextNotification = true
                currentIndex = selectedIndex
            }
        }
        val dampedDragAnimation = remember(animationScope) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedIndex.toFloat(),
                valueRange = 0f..(tabsCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                // 官方按压放大比例：78/56 ≈ 1.393。长按时药丸会明显鼓起来（不只是高光）。
                pressedScale = 78f / 56f,
                onDragStarted = {},
                onDragStopped = {
                    val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                    currentIndex = targetIndex
                    animateToValue(targetIndex.toFloat())
                    animationScope.launch {
                        offsetAnimation.animateTo(
                            0f,
                            spring(1f, 300f, 0.5f)
                        )
                    }
                },
                onDrag = { _, dragAmount ->
                    updateValue(
                        (targetValue + dragAmount.x / tabWidth * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (tabsCount - 1).toFloat())
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                }
            )
        }
        // 外部下标变化 -> 药丸动画跟过去（不回调 onTabSelected，避免与宿主导航形成回环）。
        LaunchedEffect(selectedIndex, dampedDragAnimation) {
            if (dampedDragAnimation.targetValue != selectedIndex.toFloat()) {
                dampedDragAnimation.animateToValue(selectedIndex.toFloat())
            }
        }
        // 拖拽松手后 currentIndex 由 onDragStopped 写入，这里负责通知宿主切换页面。
        // 宿主回写引发的变更被 suppressNextNotification 拦下（清标志、不回调）。
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { currentIndex }
                .drop(1)
                .collectLatest { index ->
                    if (suppressNextNotification) {
                        suppressNextNotification = false
                    } else {
                        onTabSelected(index)
                    }
                }
        }

        val interactiveHighlight = remember(animationScope) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, _ ->
                    Offset(
                        if (isLtr) (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset,
                        size.height / 2f
                    )
                }
            )
        }

        // ── 层 1：玻璃外壳（vibrancy ⇒ blur ⇒ lens，官方推荐顺序）──
        Row(
            Modifier
                .graphicsLayer {
                    translationX = panelOffset
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        // 官方《Backdrop effects》：效果顺序必须是 color filter ⇒ blur ⇒ lens。
                        // 外壳取官方 catalog 原值：vibrancy + blur(8dp) + lens(24dp, 24dp)，不开 depthEffect。
                        vibrancy()
                        blur(ShellBlurRadius.toPx())
                        lens(
                            ShellRefractionHeight.toPx(),
                            ShellRefractionAmount.toPx(),
                        )
                    },
                    layerBlock = {
                        val progress = dampedDragAnimation.pressProgress
                        val scale = lerp(1f, 1f + 16f.dp.toPx() / size.width, progress)
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = { drawRect(containerColor) }
                )
                .then(interactiveHighlight.modifier)
                .height(ShellHeight)
                .fillMaxWidth()
                .padding(HorizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )

        // ── 层 2：隐形着色层，仅用于给指示器药丸提供「主题色图标」采样源 ──
        CompositionLocalProvider(
            LocalLiquidBottomTabScale provides {
                lerp(1f, 1.2f, dampedDragAnimation.pressProgress)
            }
        ) {
            Row(
                Modifier
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer {
                        translationX = panelOffset
                    }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { Capsule() },
                        effects = {
                            // 官方这一层与外壳用**完全相同**的 blur/lens（vibrancy + blur(8dp) + lens(24dp·progress)）。
                            // 两层的模糊若不一致，药丸采样时就会看到「错位的图标残影」。
                            val progress = dampedDragAnimation.pressProgress
                            vibrancy()
                            blur(ShellBlurRadius.toPx())
                            lens(
                                ShellRefractionHeight.toPx() * progress,
                                ShellRefractionHeight.toPx() * progress
                            )
                        },
                        highlight = {
                            val progress = dampedDragAnimation.pressProgress
                            Highlight.Default.copy(alpha = progress)
                        },
                        onDrawSurface = { drawRect(containerColor) }
                    )
                    .then(interactiveHighlight.modifier)
                    .height(ContentHeight)
                    .fillMaxWidth()
                    .padding(horizontal = HorizontalPadding)
                    .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }

        // ── 层 3：指示器药丸，采样「页面 + 上面那层着色图标」的合成源 ──
        Box(
            Modifier
                .padding(horizontal = HorizontalPadding)
                .graphicsLayer {
                    translationX =
                        if (isLtr) dampedDragAnimation.value * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 1f) * tabWidth + panelOffset
                }
                .then(interactiveHighlight.gestureModifier)
                .then(dampedDragAnimation.modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                    shape = { Capsule() },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        // 折射强度必须随 pressProgress 归零，避免静止时折射下方隐藏着色图标层。
                        lens(
                            PillRefractionHeight.toPx() * progress,
                            PillRefractionAmount.toPx() * progress,
                            chromaticAberration = true
                        )
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Default.copy(alpha = progress)
                    },
                    shadow = {
                        val progress = dampedDragAnimation.pressProgress
                        Shadow(alpha = progress)
                    },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(
                            radius = 8f.dp * progress,
                            alpha = progress
                        )
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(
                            if (isLightTheme) Color.Black.copy(0.1f)
                            else Color.White.copy(0.1f),
                            alpha = 1f - progress
                        )
                        drawRect(Color.Black.copy(alpha = 0.03f * progress))
                    }
                )
                .height(ContentHeight)
                .fillMaxWidth(1f / tabsCount)
        )
    }
}
