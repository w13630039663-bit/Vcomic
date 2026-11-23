package com.venera.compose.components.venera

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.venera.compose.components.CHROME_BAND_TOP_BAR
import com.venera.compose.components.ChromeCaptureSuppress
import com.venera.compose.components.VeneraAmbientChrome
import com.venera.compose.components.drawVeneraAmbient
import com.venera.compose.components.reportChromeBand
import com.venera.compose.ui.tokens.LocalVeneraDarkTheme
import com.venera.compose.ui.tokens.VeneraTokens
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.TopAppBarDefaults
import top.yukonga.miuix.kmp.basic.TopAppBarState
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 顶栏这一侧的采样层，供 [VeneraTopBarPill] 直接取用。
 *
 * 为什么要走 CompositionLocal 而不是逐屏传参：`navigationIcon` / `actions` / `bottomContent`
 * 都是 [VeneraTopAppBar] **内部求值**的 composable lambda，而它自己已经收了 `backdrop` 参数——
 * 在这里 provide 一次，13 个调用点一行都不用改。
 * 用 `compositionLocalOf` 不用 static：这个值会随能力检测与 `enableBlur` 在 null↔非 null 之间切，
 * static 会让切换不重组（正是 `VeneraTokens.kt` 那条"切档留旧值"的病的形状）。
 */
val LocalTopBarBackdrop = compositionLocalOf<Backdrop?> { null }

/**
 * Venera 统一顶栏（对齐 pixez-miuix 规范）：
 * - 顶置时大标题靠左底衬全通透；
 * - 下滑时大标题自动折叠居中，并淡入 HyperOS 渐变式高斯模糊（从状态栏向内容区平滑衰减，无硬切边缘）；
 * - 优雅降级：不支持 RuntimeShader 时平滑降级为 surface 半透明。
 *
 * @param title 折叠后居中的小标题。
 * @param largeTitle 顶置时靠左的大标题；默认与 [title] 相同。
 * @param subtitle 大标题下方副标题（可选）。
 * @param scrollBehavior 滚动行为控制器，需挂载到列表的 nestedScroll 上。
 * @param backdrop 采样源，由 [rememberTopBarBackdrop] 提供并挂载到列表上。
 * @param navigationIcon 左侧导航图标。
 * @param actions 右侧操作图标集合。
 * @param bottomContent 顶栏下方常驻内容（如分类 Tab 等）。
 * @param onDoubleTap 双击顶栏空白处（新增，加性可选）。
 *   画廊用它"双击回到顶部"。**默认 null ⇒ 其余 12 个调用点零改动、零行为差异**。
 *
 *   ⚠️ 手势与顶栏内的动作钮是**并存**的：`detectTapGestures` 挂在顶栏这层 Box 上，
 *   而 `actions` 里那些 `VeneraTopBarPill` 各有自己的 clickable，子组件先消费点击事件，
 *   所以"点胶囊仍然生效、双击空白处才回顶"两条预期同时成立。
 *   这一条**尚未真机验证**（本轮设备未连），真机上要先确认这两件事都成立。
 * @param scrollProgressOverride 玻璃进度覆盖源（设置 hero 页专用，加性可选）。
 *   hero 页调用方**不挂** scrollBehavior 的 nestedScroll 连接（原因见
 *   [SettingsHome]），折叠机制整个不参与，玻璃/分割线/小标题的进度由调用方从
 *   列表 scrollState 直接推导传入。null（默认）= 其余页面走 contentOffset 原路。
 * @param opaqueChrome 整条顶栏（含 bottomContent）铺一层不透明的页面氛围光（加性可选）。
 *   默认 false = 现有 13 个调用点零变化。为真的页必须同时**不传 backdrop**，也不要把内容
 *   挂进 `blurBackdropSource` —— 玻璃采的是录制下来的内容层，不是屏幕像素；采了的话
 *   内容会绕过这层实底继续从顶栏里透出来（pixiv 账号页真机读数就是这么来的）。
 */
@Composable
fun VeneraTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    largeTitle: String = title,
    subtitle: String = "",
    scrollBehavior: ScrollBehavior? = null,
    backdrop: Backdrop? = null,
    opaqueChrome: Boolean = false,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    bottomContent: @Composable () -> Unit = {},
    onDoubleTap: (() -> Unit)? = null,
    scrollProgressOverride: State<Float>? = null,
) {
    val tokens = VeneraTokens
    val density = LocalDensity.current
    val surfaceColor = MiuixTheme.colorScheme.surface

    // 动态淡入进度：仅根据 contentOffset（下滑 48dp 线性淡入），对齐 pixez-miuix 规范。
    // 不使用 collapsedFraction——它在大标题折叠动画中会快速跳至 1.0，导致模糊背板过早 100% 不透明（白色覆盖）。
    val scrollProgress = remember(scrollBehavior) {
        derivedStateOf {
            val state = scrollBehavior?.state ?: return@derivedStateOf 0f
            val thresholdPx = with(density) { 48.dp.toPx() }
            if (thresholdPx > 0f) (-state.contentOffset / thresholdPx).coerceIn(0f, 1f) else 0f
        }
    }
    val activeProgress = scrollProgressOverride ?: scrollProgress

    // 顶部 inset 必须与底层 TopAppBarLayout 一致（systemBars.Top），避免自绘标题与按钮行错位。
    val collapsedTopInset = WindowInsets.systemBars.only(WindowInsetsSides.Top)
        .asPaddingValues().calculateTopPadding()
    // 覆盖进度路径不挂 nestedScroll，底层 barHeight 会停在展开态且原生小标题不显示；
    // 仅在 largeTitle 为空时将外层裁切为折叠高度，测量时放开 maxHeight 防止按钮被居中上顶。
    val pinCollapsedHeight = scrollProgressOverride != null && largeTitle.isEmpty()
    // 钉高度那一路用的像素高度 = `顶部 inset + 折叠高度`。组合期取好：`layout{}` 是测量期，
    // 那里读不到 `collapsedTopInset` 这个组合期局部量。
    val pinnedBarHeightPx = with(density) {
        (collapsedTopInset + TopAppBarDefaults.CollapsedHeight).roundToPx()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            // ── 起点截帧时让位（与壳层底栏同一枚开关，成因写在那一处）──
            // 顶栏是玻璃覆盖层，滚到上面那几行的卡会被它压住；PixelCopy 读的是已合成表面，
            // 于是那一条玻璃会被截进飞行体。只走 alpha：顶栏的折叠状态与滚动锚点都不参与。
            .graphicsLayer {
                alpha = if (ChromeCaptureSuppress.suppressed) 0f else 1f
            }
            // 它自己占的那一块由它自己报 —— 这里不存"顶栏有多高"的第二份真相。
            .reportChromeBand(CHROME_BAND_TOP_BAR)
            // 双击回顶部使用 pointerInput 而非 combinedClickable，避免给整条顶栏附加点击语义或抢占子按钮事件。
            .then(
                if (onDoubleTap != null) {
                    Modifier.pointerInput(onDoubleTap) {
                        detectTapGestures(onDoubleTap = { onDoubleTap() })
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        // ── 实底那一路：把页面同一份氛围光铺满整条顶栏（含 bottomContent）──
        // 画在背板与 miuix 本体之前，且住在这一颗 Box 里 —— `ChromeCaptureSuppress` 的
        // 整条 alpha 对它同样生效，起始帧截图不会把这条实底截进飞行体。
        if (opaqueChrome) {
            VeneraAmbientChrome(modifier = Modifier.matchParentSize())
        }

        // ── 真实渐变高斯模糊背板（对齐 pixez-miuix 规范）──
        if (backdrop != null && isRuntimeShaderSupported()) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        alpha = activeProgress.value
                    }
                    .progressiveTextureBlur(
                        backdrop = backdrop,
                        shape = RectangleShape,
                        // 从状态栏开始 100% 强度模糊，底部平滑衰减至 0，curve = 2.2f 杜绝硬切边缘
                        gradient = ProgressiveBlur.Top.copy(curve = 2.2f),
                        blurRadius = 10f,
                        colors = BlurDefaults.blurColors(
                            blendColors = listOf(
                                // 低强度补底：只负责提升文字对比，不再把玻璃推向白色。
                                // 泛白的主因曾是采样层缺氛围光（见 rememberTopBarBackdrop），
                                // 修掉后这里只需要很轻的一层。
                                BlendColorEntry(color = surfaceColor.copy(alpha = GlassTintAlpha)),
                            ),
                        ),
                    ),
            )
        } else {
            // 降级兜底：不支持 RuntimeShader 时显示半透明底色
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        alpha = activeProgress.value
                    }
                    .background(surfaceColor.copy(alpha = 0.85f)),
            )
        }

        // 覆盖进度路径下原生小标题不显示，此处自绘一份并与底层共用 systemBars.Top inset 保证同线居中。
        if (scrollProgressOverride != null && title.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
                    .height(TopAppBarDefaults.CollapsedHeight)
                    .graphicsLayer { alpha = activeProgress.value },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = title,
                    // 直接读取主题 title3 字号，与原生小标题保持同号并随风格档切换。
                    fontSize = MiuixTheme.textStyles.title3.fontSize,
                    fontWeight = tokens.type.weightMedium,
                    color = tokens.color.textPrimary,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }

        // ── 顶栏本体：miuix TopAppBar，底色透明，由背板负责视觉背景 ──
        // 顶栏里的图标按钮（VeneraTopBarPill）要磨砂就得拿到**同一份**采样层，
        // 否则它会采到别层内容、颜色与背后那条对不上（收藏页那颗当初就是这么对齐的）。
        CompositionLocalProvider(LocalTopBarBackdrop provides backdrop) {
            // 底层 TopAppBarLayout 不受 maxHeight 约束，必须配合外层 clipToBounds 裁掉空大标题行。
            if (pinCollapsedHeight) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        // 测量时放开 maxHeight 防止内部按钮被居中上顶，再由外层裁切为折叠高度。
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(
                                constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
                            )
                            val width = if (constraints.maxWidth == Constraints.Infinity) {
                                placeable.width
                            } else {
                                constraints.maxWidth
                            }
                            layout(width, pinnedBarHeightPx) { placeable.place(0, 0) }
                        }
                        .clipToBounds(),
                ) {
                    VeneraTopAppBarBody(
                        title = title,
                        largeTitle = largeTitle,
                        subtitle = subtitle,
                        scrollBehavior = scrollBehavior,
                        navigationIcon = navigationIcon,
                        actions = actions,
                        bottomContent = bottomContent,
                    )
                }
            } else {
                VeneraTopAppBarBody(
                    title = title,
                    largeTitle = largeTitle,
                    subtitle = subtitle,
                    scrollBehavior = scrollBehavior,
                    navigationIcon = navigationIcon,
                    actions = actions,
                    bottomContent = bottomContent,
                )
            }
        }
    }
}

/**
 * miuix [TopAppBar] 本体的那一份装配（底色透明，视觉背景交给背板）。
 *
 * 单独抽出来只为一件事：上面那条"钉高度"的分支要和原样分支**共用同一份参数表**，
 * 否则将来加一个参数只改一边，两路就悄悄分叉了（这正是本仓反复踩过的"两处各写一遍"形状）。
 */
@Composable
private fun VeneraTopAppBarBody(
    title: String,
    largeTitle: String,
    subtitle: String,
    scrollBehavior: ScrollBehavior?,
    navigationIcon: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    bottomContent: @Composable () -> Unit,
) {
    val tokens = VeneraTokens
    TopAppBar(
        title = title,
        largeTitle = largeTitle,
        subtitle = subtitle,
        color = Color.Transparent,
        titleColor = tokens.color.textPrimary,
        largeTitleColor = tokens.color.textPrimary,
        subtitleColor = tokens.color.textSecondary,
        navigationIcon = navigationIcon,
        actions = actions,
        scrollBehavior = scrollBehavior,
        bottomContent = bottomContent,
    )
}

/**
 * 带有底色保护与平台着色器能力检测的 Backdrop 采样源。
 *
 * 1. 运行时熔断：若硬件/平台不支持 RuntimeShader (Android < API 33) 或主动禁用模糊，返回 null；
 * 2. 氛围光注入：采样层必须复刻 [com.venera.compose.components.drawVeneraAmbient] 的
 *    页面背景（surface 底色 + primary/secondary 取色光斑 + 浅色压暗）。
 *    毛玻璃模糊的是**采样层**而非屏幕像素——若采样层只铺纯 surface，玻璃会比
 *    实际页面背景偏白，与 MD3 动态取色脱节（「模糊玻璃变白」的用户报告根因）。
 */
@Composable
fun rememberTopBarBackdrop(enableBlur: Boolean = true): LayerBackdrop? {
    if (!enableBlur || !isRuntimeShaderSupported()) return null
    val isDark = LocalVeneraDarkTheme.current
    val primary = MiuixTheme.colorScheme.primary
    val secondary = MiuixTheme.colorScheme.tertiaryContainer.let {
        if (it != Color.Unspecified) it else Color(0xFF0A63F6)
    }
    val surfaceColor = MiuixTheme.colorScheme.surface
    return rememberLayerBackdrop {
        drawVeneraAmbient(
            primary = primary,
            secondary = secondary,
            surface = surfaceColor,
            isDark = isDark,
        )
        drawContent()
    }
}

/**
 * 方便内容列表快捷挂载 Backdrop 采样源。
 */
fun Modifier.blurBackdropSource(backdrop: LayerBackdrop?): Modifier =
    if (backdrop != null) this.layerBackdrop(backdrop) else this

/**
 * 创建与 VeneraTopAppBar 配套的滚动行为。
 */
@Composable
fun rememberVeneraTopAppBarBehavior(
    state: TopAppBarState = rememberTopAppBarState(),
    canScroll: () -> Boolean = { true },
): ScrollBehavior = MiuixScrollBehavior(state = state, canScroll = canScroll)
