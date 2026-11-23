package com.venera.compose.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.components.venera.VeneraCoverMask
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.venera.compose.ui.tokens.VeneraTokens
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import kotlin.math.roundToInt

/**
 * 侧栏二级入口。
 *
 * 和主 tab 分开：主 tab 是页面身份（Home / Search / Gallery ...），
 * 二级入口是当前产品里已经有真实落点的工具页（队列、历史、源管理 ...）。
 * `selected` 与 `onClick` 都由调用点按真实 route 计算，不在组件里造死按钮。
 */
data class VeneraSideBarItem(
    val key: String,
    val title: String,
    val icon: ImageVector,
    val selected: Boolean,
    val onClick: () -> Unit,
)

data class VeneraSideBarGroup(
    val title: String,
    val items: List<VeneraSideBarItem>,
)

/** 侧栏下载状态条的那一笔：正在跑的那一格的题名 + 进度句 + 队列中任务数角标。 */
data class VeneraSideBarDownloadActivity(
    val title: String,
    val progressLine: String,
    /** 队列里"在跑+待跑"的任务数；>0 才在入口右上角摆角标（0 = 无进行中，不摆假角标）。 */
    val activeCount: Int = 0,
)

/**
 * 「卡片 → 下载中心」飞入动效的桌面壳挂手。
 *
 * 共享件只两笔：把下载中心入口的当窗 bounds 报给壳（飞入胶囊的落点），
 * 以及壳在胶囊落地时 +1 的 [popTick]（角标自己弹一下作为"收到"的回执）。
 *
 * ## 为什么走 CompositionLocal 而不是加形参
 * 这是**桌面壳单方面的动画需求**；加形参就得要求所有 Android 调用点各补一行空转实参 ——
 * 为复用无谓扩大跨平台面（AGENTS 的 UI 复用纪律）。local 不注入 ⇒ `current == null`
 * ⇒ 报送与弹跳两条 modifier 都不挂 ⇒ Android 行为逐字节不变。
 */
@Stable
class VeneraSideBarDownloadAnchor {
    /** 下载中心入口在 composition root 坐标的 bounds（桌面单窗 ⇒ root 即窗口；null = 那一格今天没摆，壳不放飞入票）。 */
    val boundsInRoot = mutableStateOf<Rect?>(null)

    /** 每落一张飞入胶囊 +1；>0 起角标自己弹一下。 */
    val popTick = mutableStateOf(0)
}

val LocalVeneraSideBarDownloadAnchor = compositionLocalOf<VeneraSideBarDownloadAnchor?> { null }

/** 侧栏账号卡的那一格：pixiv 登录态。 */
data class VeneraSideBarAccountCard(
    /** 头像座上的首字母（名首字;未登录给 "?"）。 */
    val initial: String,
    val name: String,
    /** 那一行小字（已登录念 pixiv ID/账号;未登录念「点按登录」）。 */
    val subtitle: String,
    val logged: Boolean,
    val avatarUrl: String? = null,
)

/**
 * 大屏侧边导航栏。
 *
 * 两档形态（`userExpanded` 手动切换，默认落在折叠档）：
 *
 * 1. 折叠档（新）：76dp 玻璃浮动轨 —— 圆角 16、描边高光、外投影，
 *    **占布局位不遮内容**（内容区在 Row 里天然让位）。一级 = 高频目的地
 *    （首页/搜索/画廊/收藏/阅读历史/下载与本地，按 key 从 secondaryGroups 提级），
 *    低频成组功能收进「频道锚点」，悬停/点按弹出二级毛玻璃面板（240dp，标题复用
 *    「▎竖条 + 中文 + 英文微标」分区语言）。选中指示器是一枚 44dp 圆角滑块，
 * `spring(dampingRatio = 0.55f)` 过冲回弹（的"弹性选中指示器"）。
 *    玻璃材质两档口径：常驻轨 `alpha .82` 实感玻璃（安静），二级弹层 `alpha .94`
 * 实底面板（Compose 桌面 backdrop blur 不免费，按兜底走实色玻璃）。
 * 2. 展开档：左图标右文字的横向大条目 + 分组标签。
 *
 * 二级入口映射：`history`/`files` 提进一级；`daily`/`browse-history`/`bookmarks`/`pixiv`
 * 收进「画廊频道」（探索 tab 作频道首项）；`stats`/`sources`/`backup` 收进「工具频道」。
 * 认不得的 key 不丢 —— 按原组名追加成「更多」频道。secondaryGroups 全空（安卓大屏）
 * 时整轨退化为五个 tab 平铺，探索回一级。
 */
/** 轨皮肤槽的作用域：展开态、当前轨宽、展开/收起扳手由骨架递进来。 */
class VeneraRailSkinScope(
    val isExpanded: Boolean,
    val width: Dp,
    val onToggleExpanded: () -> Unit,
    // 两档定宽：皮肤据此按目标宽度布局内容，宽度动画只负责裁剪窗口，避免内容被窄栏挤压重排。
    val expandedWidth: Dp = Dp.Unspecified,
    val collapsedWidth: Dp = Dp.Unspecified,
)

@Composable
fun VeneraSideBar(
    currentTab: VeneraNavTab?,
    onTabSelected: (VeneraNavTab) -> Unit,
    mode: WideScreenLayoutMode,
    modifier: Modifier = Modifier,
    // 为 null 时不渲染设置入口，避免无落点时展示无效按钮；settingsSelected 仅在入口可见时生效。
    onSettingsSelected: (() -> Unit)? = null,
    settingsSelected: Boolean = false,
    secondaryGroups: List<VeneraSideBarGroup> = emptyList(),
    // 桌面侧栏扩展槽：为 null 时不渲染对应条目（0 徽章与未就绪 null 语义不同）。
    favoriteBadge: Int? = null,
    downloadActivity: VeneraSideBarDownloadActivity? = null,
    onDownloadActivityClick: (() -> Unit)? = null,
    downloadsSelected: Boolean = false,
    accountCard: VeneraSideBarAccountCard? = null,
    onAccountCardClick: (() -> Unit)? = null,
    // 侧栏展开与折叠两档宽度。
    railExpandedWidth: Dp = 200.dp,
    railCollapsedWidth: Dp = 80.dp,
    // 展开态初值与变更回调，未传时仅在会话内临时切换。
    initialExpanded: Boolean = false,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    // 非空时将整条轨的绘制权交给调用方，骨架与宽度动画保持不变。
    railSkin: (@Composable VeneraRailSkinScope.() -> Unit)? = null,
    // hiddenTabs 隐藏指定主标签；tabTitleOverride 仅覆盖显示文案，不改共享枚举与行为。
    hiddenTabs: Set<VeneraNavTab> = emptySet(),
    tabTitleOverride: Map<VeneraNavTab, String> = emptyMap(),
    // 内容区底色，用于透明窗根模式下遮实内容区；null 时不绘制。
    contentSurface: Color? = null,
    // 侧栏玻璃基色；null = 主题色。与 contentSurface 同源，两侧才不会一深一浅。
    railSurface: Color? = null,
    // 贴在内容区顶部的命令带右半（44dp），画在水平内边距之外以使窗控贴右缘；null 时不绘制。
    contentTopBar: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val tokens = VeneraTokens
    val ux = LocalUxPlatformServices.current

    if (mode == WideScreenLayoutMode.Compact) {
        Box(modifier = modifier.fillMaxSize()) { content() }
        return
    }

    // 用户支持手动展开/折叠（**常驻为折叠玻璃轨**，按展开钮才展开；
    // v3 获批「记住选择」：桌面把初值与变化回调递进来（持久化住在壳），
    // 安卓不递 ⇒ 初值 false、回调空，会话内临时展开的旧形状逐像素不变）。
    var userExpanded by remember(initialExpanded) { mutableStateOf(initialExpanded) }
    val isExpanded = userExpanded

    // 侧栏宽度平滑过渡：折叠/展开两档由调用点定宽
    val targetRailWidth = if (isExpanded) railExpandedWidth else railCollapsedWidth
    val animatedRailWidth by animateDpAsState(
        targetValue = targetRailWidth,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "RailWidthAnimation",
    )

    // ── 桌面飞入挂手：入口 bounds 报送 + 角标 popTick 弹跳 ──
    // Android 侧不注入那枚 local ⇒ downloadAnchor == null ⇒ 下面两条 Modifier 都是空链段，行为与从前逐字节一致。
    val downloadAnchor = LocalVeneraSideBarDownloadAnchor.current
    val anchorPopKey = downloadAnchor?.popTick?.value ?: 0
    val anchorPopScale = remember { Animatable(1f) }
    LaunchedEffect(anchorPopKey) {
        if (anchorPopKey > 0) {
            anchorPopScale.animateTo(1.34f, tween(110))
            anchorPopScale.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 900f))
        }
    }
    val flyEntryReporter: Modifier = if (downloadAnchor != null) {
        Modifier.onGloballyPositioned { downloadAnchor.boundsInRoot.value = it.boundsInRoot() }
    } else {
        Modifier
    }
    val badgePopLayer: Modifier = if (downloadAnchor != null) {
        Modifier.graphicsLayer {
            scaleX = anchorPopScale.value
            scaleY = anchorPopScale.value
        }
    } else {
        Modifier
    }

    // ── 底部落脚点（下载与本地图标 → 账号卡 → 设置格），两档形态共用这一段 ──
    val railFooter: @Composable () -> Unit = {
        if (downloadActivity != null && onDownloadActivityClick != null) {
            val downloadColor = if (downloadsSelected) tokens.color.primary else tokens.color.textSecondary
            Spacer(modifier = Modifier.height(8.dp))
            if (isExpanded) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (downloadsSelected) tokens.color.primary.copy(alpha = 0.16f) else Color.Transparent
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            ux.hapticTap()
                            onDownloadActivityClick()
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .then(flyEntryReporter),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Download,
                        contentDescription = if (com.venera.desktop.settings.DesktopLanguage.isEnglish()) "Downloads" else "下载与本地",
                        tint = downloadColor,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = if (com.venera.desktop.settings.DesktopLanguage.isEnglish()) "Downloads" else "下载与本地",
                        fontSize = 14.sp,
                        fontWeight = if (downloadsSelected) FontWeight.Bold else FontWeight.Medium,
                        color = downloadColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (downloadActivity.activeCount > 0) {
                        Text(
                            text = downloadActivity.activeCount.coerceAtMost(99).toString(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            modifier = Modifier
                                .then(badgePopLayer)
                                .clip(CircleShape)
                                .background(tokens.color.primary)
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                }
            } else {
                // 折叠态：紧凑方块图标按钮，悬浮在头像正上方，右上角完整挂载任务数角标（不被clip截断）
                Box(
                    modifier = Modifier
                        .size(width = 48.dp, height = 40.dp)
                        .then(flyEntryReporter),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 44.dp, height = 36.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (downloadsSelected) tokens.color.primary.copy(alpha = 0.16f) else Color.Transparent
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                ux.hapticTap()
                                onDownloadActivityClick()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Download,
                            contentDescription = "下载与本地",
                            tint = downloadColor,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    if (downloadActivity.activeCount > 0) {
                        Text(
                            text = downloadActivity.activeCount.coerceAtMost(99).toString(),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            modifier = Modifier
                                .then(badgePopLayer)
                                .align(Alignment.TopEnd)
                                .offset(x = 1.dp, y = 1.dp)
                                .clip(CircleShape)
                                .background(tokens.color.primary)
                                .padding(horizontal = 4.5.dp, vertical = 0.5.dp),
                        )
                    }
                }
            }
        }
        if (accountCard != null && onAccountCardClick != null) {
            Spacer(modifier = Modifier.height(8.dp))
            if (isExpanded) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onAccountCardClick() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                if (accountCard.logged) tokens.color.primary.copy(alpha = 0.28f)
                                else tokens.color.outlineVariant.copy(alpha = 0.5f),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!accountCard.avatarUrl.isNullOrBlank()) {
                            VeneraCover(
                                url = accountCard.avatarUrl,
                                contentDescription = accountCard.name,
                                mask = VeneraCoverMask.Visible,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Text(
                                text = accountCard.initial,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (accountCard.logged) tokens.color.primary else tokens.color.textTertiary,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = accountCard.name,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = tokens.color.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = accountCard.subtitle,
                            fontSize = 10.sp,
                            color = tokens.color.textTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            } else {
                // 折叠态:只剩头像圆点,登录态用描边色念(色不是唯一指示,展开态有字)。
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(
                            if (accountCard.logged) tokens.color.primary.copy(alpha = 0.28f)
                            else tokens.color.outlineVariant.copy(alpha = 0.5f),
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onAccountCardClick() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (!accountCard.avatarUrl.isNullOrBlank()) {
                        VeneraCover(
                            url = accountCard.avatarUrl,
                            contentDescription = accountCard.name,
                            mask = VeneraCoverMask.Visible,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text(
                            text = accountCard.initial,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (accountCard.logged) tokens.color.primary else tokens.color.textTertiary,
                        )
                    }
                }
            }
        }

        // ── 底部那一枚「设置」格（桌面 §151；安卓那侧这枚槽恒不递 ⇒ 整颗不画）──
        if (onSettingsSelected != null) {
            val settingsColor =
                if (settingsSelected) tokens.color.primary else tokens.color.textSecondary
            Spacer(modifier = Modifier.height(8.dp))
            if (isExpanded) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            ux.hapticTap()
                            onSettingsSelected()
                        }
                        .background(
                            if (settingsSelected) {
                                tokens.color.primary.copy(alpha = tokens.current.selectedSurfaceAlpha)
                            } else {
                                Color.Transparent
                            }
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (settingsSelected) Icons.Filled.Settings else Icons.Outlined.Settings,
                        contentDescription = if (com.venera.desktop.settings.DesktopLanguage.isEnglish()) "Settings" else "设置",
                        tint = settingsColor,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = if (com.venera.desktop.settings.DesktopLanguage.isEnglish()) "Settings" else "设置",
                        fontSize = 14.sp,
                        fontWeight = if (settingsSelected) FontWeight.Bold else FontWeight.Medium,
                        color = settingsColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(width = 44.dp, height = 36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            ux.hapticTap()
                            onSettingsSelected()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (settingsSelected) Icons.Filled.Settings else Icons.Outlined.Settings,
                        contentDescription = if (com.venera.desktop.settings.DesktopLanguage.isEnglish()) "Settings" else "设置",
                        tint = settingsColor,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }

    Row(modifier = modifier.fillMaxSize()) {
        if (railSkin != null) {
            // ══ 皮肤档：轨宽与展开/收起动画照旧，绘制权整体外交 ══
            // 宽度动画期间按窗口裁剪：皮肤内容固定按目标宽度布局，窄栏不再把内容压扁重排。
            Box(
                modifier = Modifier
                    .width(animatedRailWidth)
                    .fillMaxHeight()
                    .clipToBounds(),
            ) {
                railSkin(
                    VeneraRailSkinScope(
                        isExpanded = isExpanded,
                        width = animatedRailWidth,
                        onToggleExpanded = {
                            ux.hapticTap()
                            userExpanded = !userExpanded
                            onExpandedChange?.invoke(userExpanded)
                        },
                        expandedWidth = railExpandedWidth,
                        collapsedWidth = railCollapsedWidth,
                    )
                )
            }
        } else if (isExpanded) {
            // ══ 展开档（左图标右文字横向条目 + 分组标签）══
            val railBlur by com.venera.desktop.settings.DesktopRailBlur.visible.collectAsState()
            // 调用方给的基色自带 alpha（与内容区同一阶梯），这里不再二次覆盖。
            // ⚠️ 下面 surfaceContainerHigh 那两条 else 属「经典」皮肤档，该档已停用
            // （见 DesktopHomeSkin.CLASSIC）：现行浅皮档总会递 railSurface。改外观勿动 else 分支。
            val railBg = if (railSurface != null) {
                railSurface
            } else if (com.venera.desktop.DesktopDwmBackdrop.active && railBlur) {
                tokens.color.surfaceContainerHigh.copy(alpha = 0.62f)
            } else {
                tokens.color.surfaceContainerHigh
            }
            Column(
                modifier = Modifier
                    .width(animatedRailWidth)
                    .fillMaxHeight()
                    .background(railBg)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 顶部折叠/展开按钮
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    IconButton(
                        onClick = {
                            ux.hapticTap()
                            userExpanded = false
                            onExpandedChange?.invoke(false)
                        },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuOpen,
                            contentDescription = "折叠导航栏",
                            tint = tokens.color.textSecondary,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                ) {
                    val anySecondarySelected =
                        settingsSelected || secondaryGroups.any { it.items.any { item -> item.selected } }
                    VeneraNavTab.entries.forEach { tab ->
                        if (tab in hiddenTabs) return@forEach
                        val selected = !anySecondarySelected && (tab == currentTab)
                        // 「搜图」紧跟「收藏」下面（展开档）。
                        // 折叠轨仍走 tierItems（它是主 tab 之后的第一枚，本就贴着收藏）。
                        // 这里画过的那一枚从下面 secondaryGroups 里跳过，免得同屏两枚。
                        if (tab == VeneraNavTab.FAVORITES) {
                            val reverse = secondaryGroups.flatMap { it.items }
                                .firstOrNull { it.key == "reverse-search" }
                            if (reverse != null) {
                                VeneraSideBarActionItem(
                                    item = reverse,
                                    expanded = true,
                                    onClick = {
                                        if (!reverse.selected) {
                                            ux.hapticTap()
                                            reverse.onClick()
                                        }
                                    },
                                )
                            }
                        }
                        val contentColor = if (selected) tokens.color.primary else tokens.color.textSecondary
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    if (!selected) {
                                        ux.hapticTap()
                                        onTabSelected(tab)
                                    }
                                }
                                .background(
                                    if (selected) {
                                        tokens.color.primary.copy(alpha = tokens.current.selectedSurfaceAlpha)
                                    } else {
                                        Color.Transparent
                                    }
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AnimatedNavIcon(
                                tab = tab,
                                selected = selected,
                                tint = contentColor,
                                baseSize = 24.dp,
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            val isEn = com.venera.desktop.settings.LocalDesktopLanguage.current == com.venera.desktop.settings.DesktopLanguage.EN
                            val tabTitle = tabTitleOverride[tab]?.let { com.venera.desktop.settings.DesktopI18n.t(it) } ?: if (isEn) {
                                when (tab) {
                                    VeneraNavTab.HOME -> "Home"
                                    VeneraNavTab.SEARCH -> "Search"
                                    VeneraNavTab.GALLERY -> "Gallery"
                                    VeneraNavTab.FAVORITES -> "Favorites"
                                    VeneraNavTab.EXPLORE -> "Explore"
                                }
                            } else {
                                tab.title
                            }
                            Text(
                                text = tabTitle,
                                fontSize = 14.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = contentColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (tab == VeneraNavTab.FAVORITES && favoriteBadge != null) {
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    text = favoriteBadge.toString(),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) tokens.color.primary else tokens.color.textTertiary,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(
                                            (if (selected) tokens.color.primary else tokens.color.outlineVariant)
                                                .copy(alpha = 0.24f),
                                        )
                                        .padding(horizontal = 8.dp, vertical = 1.dp),
                                )
                            }
                        }
                    }

                    secondaryGroups.forEach { group ->
                        // 「搜图」已在收藏下面画过（令），这里跳过避免同屏两枚。
                        val visibleItems = group.items.filter { it.key != "reverse-search" }
                        if (group.title.isNotEmpty() && visibleItems.isNotEmpty()) Spacer(modifier = Modifier.height(8.dp))
                        if (group.title.isNotEmpty() && visibleItems.isNotEmpty()) Text(
                            text = com.venera.desktop.settings.DesktopI18n.t(group.title),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 28.dp, vertical = 4.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = tokens.color.textTertiary,
                        )
                        visibleItems.forEach { item ->
                            VeneraSideBarActionItem(
                                item = item,
                                expanded = true,
                                onClick = {
                                    if (!item.selected) {
                                        ux.hapticTap()
                                        item.onClick()
                                    }
                                },
                            )
                        }
                    }
                }

                railFooter()
            }
        } else {
            // ══ 折叠档（双层玻璃浮动轨）══
            VeneraGlassRail(
                currentTab = currentTab,
                onTabSelected = onTabSelected,
                secondaryGroups = secondaryGroups,
                favoriteBadge = favoriteBadge,
                anySecondarySelected = settingsSelected || secondaryGroups.any { it.items.any { item -> item.selected } },
                railWidth = animatedRailWidth,
                footer = railFooter,
                onExpandRequested = {
                    ux.hapticTap()
                    userExpanded = true
                    onExpandedChange?.invoke(true)
                },
                hiddenTabs = hiddenTabs,
                tabTitleOverride = tabTitleOverride,
            )
        }

        // 内容区左右对称留 24dp 边距；命令带右半占 44dp 布局高度且不吃水平内边距，使窗控贴窗口右缘。
        // contentSurface 只铺内容区，避免把顶栏一起染一遍（透明时双重叠色、底板实底时盖死顶栏模糊）。
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            if (contentTopBar != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                ) {
                    contentTopBar()
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(contentSurface ?: Color.Transparent)
                    .padding(horizontal = 24.dp),
            ) {
                content()
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// 折叠玻璃轨（私有件）
// ═══════════════════════════════════════════════════════════════════

private val GlassRailShape = RoundedCornerShape(16.dp)
private val GlassItemShape = RoundedCornerShape(12.dp)

/** 轨上一格：tab 格吃 [tab]（走 AnimatedNavIcon 弹跳图标），二级入口格吃 [item]。 */
private data class RailEntry(
    val id: String,
    val label: String,
    val tab: VeneraNavTab? = null,
    val item: VeneraSideBarItem? = null,
)

/** 频道（二级面板）：锚点 + 弹出的一组条目。 */
private data class RailChannel(
    val key: String,
    val title: String,
    val en: String,
    val icon: ImageVector,
    val entries: List<RailEntry>,
)

private fun RailEntry.isSelected(currentTab: VeneraNavTab?, anySecondarySelected: Boolean): Boolean =
    tab?.let { !anySecondarySelected && it == currentTab } ?: (item?.selected == true)

@Composable
private fun VeneraGlassRail(
    currentTab: VeneraNavTab?,
    onTabSelected: (VeneraNavTab) -> Unit,
    secondaryGroups: List<VeneraSideBarGroup>,
    favoriteBadge: Int?,
    anySecondarySelected: Boolean,
    railWidth: Dp,
    footer: @Composable () -> Unit,
    onExpandRequested: () -> Unit,
    // 桌面自持标签口径（同 VeneraSideBar 那两枚：安卓不递 ⇒ 旧形状逐字节不变）。
    hiddenTabs: Set<VeneraNavTab> = emptySet(),
    tabTitleOverride: Map<VeneraNavTab, String> = emptyMap(),
) {
    val tokens = VeneraTokens
    val ux = LocalUxPlatformServices.current
    val density = androidx.compose.ui.platform.LocalDensity.current

    // 频道开合：点按钉住（focusable Popup 点外/Esc 收）；悬停临时开，挪进面板续命。
    var pinnedChannel by remember { mutableStateOf<String?>(null) }
    var hoverChannel by remember { mutableStateOf<String?>(null) }
    var panelHover by remember { mutableStateOf<String?>(null) }
    val openChannelKey = pinnedChannel ?: hoverChannel ?: panelHover
    // 悬停→面板的跨缝保护：鼠标从锚点挪进 Popup 的瞬间，锚点 Exit 与面板 Enter 之间
    // 有个"两头都不悬停"的空缝，不留宽限就变成关了又开的死循环（真窗量到的闪烁成因）。
    // 350ms 宽限：Exit 只排一次延迟关，进面板/回锚点即取消。
    val scope = rememberCoroutineScope()
    var hoverCloseJob by remember { mutableStateOf<Job?>(null) }
    fun cancelHoverClose() {
        hoverCloseJob?.cancel()
        hoverCloseJob = null
    }
    fun scheduleHoverClose() {
        cancelHoverClose()
        hoverCloseJob = scope.launch {
            delay(350)
            hoverChannel = null
            panelHover = null
        }
    }

    // ── 一级 / 二级映射（见类头注释；认不得的 key 按原组名兜底成「更多」频道，一条不丢）──
    val flatItems = secondaryGroups.flatMap { it.items }
    fun sec(key: String) = flatItems.firstOrNull { it.key == key }
    // 「搜图」紧跟「收藏」—— 折叠轨里提级项排第一枚。
    val tierItems = listOfNotNull(sec("reverse-search"), sec("history"), sec("files"))
    val galleryItems = listOfNotNull(sec("daily"), sec("browse-history"), sec("bookmarks"), sec("pixiv"))
    val toolItems = listOfNotNull(sec("stats"), sec("sources"), sec("backup"))
    val mappedKeys = setOf("history", "files", "daily", "browse-history", "bookmarks", "pixiv", "reverse-search", "stats", "sources", "backup")
    val channels = buildList {
        if (galleryItems.isNotEmpty()) {
            add(
                RailChannel(
                    key = "gallery",
                    title = "画廊频道",
                    en = "GALLERY",
                    icon = Icons.Outlined.Layers,
                    entries = galleryItems.map { RailEntry(id = "item:${it.key}", label = it.title, item = it) },
                ),
            )
        }
        if (toolItems.isNotEmpty()) {
            add(
                RailChannel(
                    key = "tools",
                    title = "工具频道",
                    en = "TOOLS",
                    icon = Icons.Outlined.Build,
                    entries = toolItems.map { RailEntry(id = "item:${it.key}", label = it.title, item = it) },
                ),
            )
        }
        secondaryGroups.forEach { group ->
            val rest = group.items.filter { it.key !in mappedKeys }
            if (rest.isNotEmpty()) {
                add(
                    RailChannel(
                        key = "more:${group.title}",
                        title = group.title,
                        en = "MORE",
                        icon = Icons.Outlined.MoreHoriz,
                        entries = rest.map { RailEntry(id = "item:${it.key}", label = it.title, item = it) },
                    ),
                )
            }
        }
    }
    // 一级 tabs：五个全在一级（探索不是画廊功能，回一级）。
    // `hiddenTabs` 整颗摘除（桌面隐藏探索），`tabTitleOverride` 覆盖文案。
    val tierTabs = VeneraNavTab.entries.filter { it !in hiddenTabs }
    val tier1 = buildList {
        tierTabs.forEach { t ->
            add(RailEntry(id = "tab:${t.name}", label = tabTitleOverride[t] ?: t.title, tab = t))
        }
        tierItems.forEach { add(RailEntry(id = "item:${it.key}", label = it.title, item = it)) }
    }

    // 弹性选中指示器：44dp 滑块贴住激活格，spring 过冲回弹（口径 dampingRatio ≈ .55）。
    val entryYs = remember { mutableStateMapOf<String, Float>() }
    var contentTopY by remember { mutableStateOf(Float.NaN) }
    var railTopY by remember { mutableStateOf(Float.NaN) }
    var railHeightPx by remember { mutableStateOf(0f) }
    val activeTier = tier1.firstOrNull { it.isSelected(currentTab, anySecondarySelected) }
    val activeChannel = channels.firstOrNull { ch -> ch.entries.any { it.isSelected(currentTab, anySecondarySelected) } }
    val activeId = activeTier?.id ?: activeChannel?.let { "channel:${it.key}" }
    val targetY = activeId?.let { id -> entryYs[id] }?.let { it - contentTopY }
    val indicatorY = remember { Animatable(0f) }
    var indicatorPlaced by remember { mutableStateOf(false) }
    LaunchedEffect(targetY) {
        if (targetY == null || targetY.isNaN()) return@LaunchedEffect
        if (!indicatorPlaced) {
            indicatorY.snapTo(targetY)
            indicatorPlaced = true
        } else {
            indicatorY.animateTo(targetY, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow))
        }
    }

    // 玻璃轨外包一层水平内距（左右各 6dp），让外投影有地方呼吸、浮动感成立。
    Box(
        modifier = Modifier
            .width(railWidth)
            .fillMaxHeight()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 6.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .shadow(18.dp, GlassRailShape)
                .clip(GlassRailShape)
                .background(tokens.color.surfaceContainerHigh.copy(alpha = 0.82f))
                .border(1.dp, tokens.color.outlineVariant.copy(alpha = 0.6f), GlassRailShape)
                .onGloballyPositioned {
                    railTopY = it.positionInRoot().y
                    railHeightPx = it.size.height.toFloat()
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
        // 顶部折叠钮：展开回宽栏
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(
                onClick = onExpandRequested,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "展开导航栏",
                    tint = tokens.color.textSecondary,
                )
            }
        }

        // 轨身（可滚）：一级格 → 分隔线 → 频道锚点
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { contentTopY = it.positionInRoot().y },
            ) {
                // 指示器垫底（先画），44dp 滑块在激活格背后
                if (targetY != null && !targetY.isNaN() && indicatorPlaced) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset { IntOffset(0, indicatorY.value.roundToInt()) }
                            .size(44.dp, 44.dp)
                            .clip(GlassItemShape)
                            .background(tokens.color.primary.copy(alpha = tokens.current.selectedSurfaceAlpha))
                            .border(1.dp, tokens.color.primary.copy(alpha = 0.45f), GlassItemShape),
                    )
                }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    tier1.forEach { entry ->
                        GlassRailItem(
                            id = entry.id,
                            selected = entry.isSelected(currentTab, anySecondarySelected),
                            onClick = {
                                if (!entry.isSelected(currentTab, anySecondarySelected)) {
                                    ux.hapticTap()
                                    val tab = entry.tab
                                    val item = entry.item
                                    if (tab != null) onTabSelected(tab) else item?.onClick()
                                }
                            },
                            registerY = { id, y -> entryYs[id] = y },
                        ) {
                            val selected = entry.isSelected(currentTab, anySecondarySelected)
                            val tint = if (selected) tokens.color.primary else tokens.color.textSecondary
                            val tab = entry.tab
                            val item = entry.item
                            if (tab != null) {
                                AnimatedNavIcon(tab = tab, selected = selected, tint = tint, baseSize = 20.dp)
                            } else if (item != null) {
                                Icon(imageVector = item.icon, contentDescription = entry.label, tint = tint, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    if (channels.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .width(28.dp)
                                .height(1.dp)
                                .background(tokens.color.outlineVariant),
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        channels.forEach { channel ->
                            val channelActive = channel.entries.any { it.isSelected(currentTab, anySecondarySelected) }
                            RailChannelAnchor(
                                channel = channel,
                                active = channelActive,
                                open = openChannelKey == channel.key,
                                onClick = {
                                    ux.hapticTap()
                                    pinnedChannel = if (pinnedChannel == channel.key) null else channel.key
                                },
                                onHover = { inside ->
                                    if (inside) {
                                        cancelHoverClose()
                                        hoverChannel = channel.key
                                    } else {
                                        scheduleHoverClose()
                                    }
                                },
                                registerY = { id, y -> entryYs[id] = y },
                            )
                        }
                    }
                }
            }
        }

        footer()

        // ── 二级毛玻璃面板（瞬时层；点外/Esc/再点锚点收起）──
        val openChannel = channels.firstOrNull { it.key == openChannelKey }
        if (openChannel != null) {
            val anchorId = "channel:${openChannel.key}"
            val anchorY = entryYs[anchorId]?.let { it - railTopY } ?: 0f
            val panelHeightEstimate = with(density) { (64 + openChannel.entries.size * 44).dp.toPx() }
            val offsetY = (anchorY - panelHeightEstimate / 2f)
                .coerceIn(0f, (railHeightPx - panelHeightEstimate).coerceAtLeast(0f))
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(
                    with(density) { railWidth.roundToPx() },
                    offsetY.roundToInt(),
                ),
                onDismissRequest = { pinnedChannel = null },
                // 只有钉住档才吃焦点（点外/Esc 收）；纯悬停档不吃焦点 —— 每次悬停弹层都抢一次
                // 窗焦点本身就是闪烁/抖动的另一来源。
                properties = PopupProperties(focusable = pinnedChannel != null),
            ) {
                RailFlyoutPanel(
                    channel = openChannel,
                    currentTab = currentTab,
                    anySecondarySelected = anySecondarySelected,
                    onTabSelected = onTabSelected,
                    onPicked = {
                        pinnedChannel = null
                        hoverChannel = null
                        panelHover = null
                    },
                    onPanelHover = { inside ->
                        if (inside) {
                            cancelHoverClose()
                            panelHover = openChannel.key
                        } else {
                            scheduleHoverClose()
                        }
                    },
                )
            }
        }
        }
    }
}

/** 轨上一格：44dp 圆角按钮，悬停微底、选中吃主题色。图标由调用点画（tab 走 AnimatedNavIcon）。 */
@Composable
private fun GlassRailItem(
    id: String,
    selected: Boolean,
    onClick: () -> Unit,
    registerY: (String, Float) -> Unit,
    icon: @Composable () -> Unit,
) {
    val tokens = VeneraTokens
    var hovered by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .padding(vertical = 3.dp)
            .size(44.dp)
            .onGloballyPositioned { registerY(id, it.positionInRoot().y) }
            .pointerInput(id) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Enter -> hovered = true
                            PointerEventType.Exit -> hovered = false
                            else -> {}
                        }
                    }
                }
            }
            .clip(GlassItemShape)
            .background(
                when {
                    selected -> tokens.color.primary.copy(alpha = 0.0f) // 底交给指示器滑块
                    hovered -> tokens.color.outlineVariant.copy(alpha = 0.28f)
                    else -> Color.Transparent
                }
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        icon()
    }
}

/** 频道锚点：44dp 格 + 右缘微光点（下层有内容）；active = 频道内有选中项。 */
@Composable
private fun RailChannelAnchor(
    channel: RailChannel,
    active: Boolean,
    open: Boolean,
    onClick: () -> Unit,
    onHover: (Boolean) -> Unit,
    registerY: (String, Float) -> Unit,
) {
    val tokens = VeneraTokens
    var hovered by remember { mutableStateOf(false) }
    val tint = if (active || open) tokens.color.primary else tokens.color.textSecondary
    Box(
        modifier = Modifier
            .padding(vertical = 3.dp)
            .size(44.dp)
            .onGloballyPositioned { registerY("channel:${channel.key}", it.positionInRoot().y) }
            .pointerInput(channel.key) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Enter -> {
                                hovered = true
                                onHover(true)
                            }
                            PointerEventType.Exit -> {
                                hovered = false
                                onHover(false)
                            }
                            else -> {}
                        }
                    }
                }
            }
            .clip(GlassItemShape)
            .background(
                when {
                    open -> tokens.color.outlineVariant.copy(alpha = 0.36f)
                    hovered -> tokens.color.outlineVariant.copy(alpha = 0.28f)
                    else -> Color.Transparent
                }
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = channel.icon,
            contentDescription = channel.title,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
        if (active) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 3.dp)
                    .size(3.dp)
                    .clip(CircleShape)
                    .background(tokens.color.primary),
            )
        }
    }
}

/** 二级毛玻璃面板：▎竖条 + 中文 + 英文微标标题（复用首页分区语言）+ 条目列。 */
@Composable
private fun RailFlyoutPanel(
    channel: RailChannel,
    currentTab: VeneraNavTab?,
    anySecondarySelected: Boolean,
    onTabSelected: (VeneraNavTab) -> Unit,
    onPicked: () -> Unit,
    onPanelHover: (Boolean) -> Unit,
) {
    val tokens = VeneraTokens
    val ux = LocalUxPlatformServices.current
    Column(
        modifier = Modifier
            .width(240.dp)
            .shadow(24.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(tokens.color.surfaceContainerHigh.copy(alpha = 0.94f))
            .border(1.dp, tokens.color.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .pointerInput(channel.key) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Enter -> onPanelHover(true)
                            PointerEventType.Exit -> onPanelHover(false)
                            else -> {}
                        }
                    }
                }
            }
            .padding(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 2.dp, end = 6.dp, top = 6.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(3.dp, 14.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(tokens.color.primary),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = com.venera.desktop.settings.DesktopI18n.t(channel.title),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = tokens.color.textPrimary,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = channel.en,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = tokens.color.textTertiary,
            )
            Spacer(modifier = Modifier.weight(1f))
            val isEn = com.venera.desktop.settings.LocalDesktopLanguage.current == com.venera.desktop.settings.DesktopLanguage.EN
            Text(
                text = if (isEn) "${channel.entries.size} items" else "${channel.entries.size} 项",
                fontSize = 10.sp,
                color = tokens.color.textTertiary,
            )
        }
        channel.entries.forEach { entry ->
            val selected = entry.isSelected(currentTab, anySecondarySelected)
            var hovered by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        when {
                            selected -> tokens.color.primary.copy(alpha = tokens.current.selectedSurfaceAlpha)
                            hovered -> tokens.color.outlineVariant.copy(alpha = 0.28f)
                            else -> Color.Transparent
                        }
                    )
                    .pointerInput(entry.id) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                when (event.type) {
                                    PointerEventType.Enter -> hovered = true
                                    PointerEventType.Exit -> hovered = false
                                    else -> {}
                                }
                            }
                        }
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        ux.hapticTap()
                        val tab = entry.tab
                        val item = entry.item
                        if (tab != null) onTabSelected(tab) else item?.onClick()
                        onPicked()
                    }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val tint = if (selected) tokens.color.primary else tokens.color.textSecondary
                val tab = entry.tab
                val item = entry.item
                if (tab != null) {
                    AnimatedNavIcon(tab = tab, selected = selected, tint = tint, baseSize = 17.dp)
                } else if (item != null) {
                    Icon(imageVector = item.icon, contentDescription = entry.label, tint = tint, modifier = Modifier.size(17.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = com.venera.desktop.settings.DesktopI18n.t(entry.label),
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) tokens.color.textPrimary else tokens.color.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun VeneraSideBarActionItem(
    item: VeneraSideBarItem,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val tokens = VeneraTokens
    val bgAlpha by animateFloatAsState(
        targetValue = if (item.selected) tokens.current.selectedSurfaceAlpha else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "action-item-bg",
    )
    val color by animateColorAsState(
        targetValue = if (item.selected) tokens.color.primary else tokens.color.textSecondary,
        animationSpec = tween(durationMillis = 200),
        label = "action-item-color",
    )
    if (expanded) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
                .background(tokens.color.primary.copy(alpha = bgAlpha))
                .padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.title,
                tint = color,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = com.venera.desktop.settings.DesktopI18n.t(item.title),
                fontSize = 13.sp,
                fontWeight = if (item.selected) FontWeight.SemiBold else FontWeight.Normal,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
                .padding(vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 48.dp, height = 28.dp)
                    .clip(RoundedCornerShape(14.dp))
                .background(tokens.color.primary.copy(alpha = bgAlpha)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.title,
                    tint = color,
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = com.venera.desktop.settings.DesktopI18n.t(item.title),
                fontSize = 10.sp,
                fontWeight = if (item.selected) FontWeight.SemiBold else FontWeight.Normal,
                color = color,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
    }
}
