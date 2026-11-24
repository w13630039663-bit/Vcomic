package com.venera.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewSidebar
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.venera.compose.components.LocalVeneraSideBarDownloadAnchor
import com.venera.compose.components.LocalUxPlatformServices
import com.venera.compose.components.VeneraNavTab
import com.venera.compose.components.VeneraRailSkinScope
import com.venera.compose.components.VeneraSideBarAccountCard
import com.venera.compose.components.VeneraSideBarDownloadActivity
import com.venera.compose.components.VeneraSideBarGroup
import com.venera.compose.components.VeneraSideBarItem
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.components.venera.VeneraCoverMask
import com.venera.desktop.gallery.ui.DesktopChromeColors
import com.venera.desktop.gallery.ui.DesktopChromePalette
import com.venera.desktop.gallery.ui.LocalChromePalette
import com.venera.desktop.ui.components.DesktopPetSlot

private object LightRailTokens {
    private val palette: DesktopChromePalette
        @Composable @ReadOnlyComposable get() = LocalChromePalette.current

    val ink1: Color @Composable @ReadOnlyComposable get() = palette.ink1
    val ink2: Color @Composable @ReadOnlyComposable get() = palette.ink2
    val ink3: Color @Composable @ReadOnlyComposable get() = palette.ink3
    val pill: Color @Composable @ReadOnlyComposable get() = palette.pill
    val glass: Color @Composable @ReadOnlyComposable get() = palette.railGlass
    val page: Color @Composable @ReadOnlyComposable get() = palette.page
    val sheen: Color @Composable @ReadOnlyComposable get() = palette.glassSheen
    val raised: Color @Composable @ReadOnlyComposable get() = palette.raised
    val card: Color @Composable @ReadOnlyComposable get() = palette.card
    val hover: Color @Composable @ReadOnlyComposable get() = palette.hover
    val hairline: Color @Composable @ReadOnlyComposable get() = palette.hairline
    val danger: Color @Composable @ReadOnlyComposable get() = palette.danger
    val brandStart: Color @Composable @ReadOnlyComposable get() = palette.brandStart
    val brandEnd: Color @Composable @ReadOnlyComposable get() = palette.brandEnd
    val dark: Boolean @Composable @ReadOnlyComposable get() = palette.dark

    val accent: Color = Color(0xFF8B7CF6)
    val accentIcon: Color = Color(0xFFC4B5FD)
    val statusOnline: Color = Color(0xFF34D399)
}

private val NavRowShape = RoundedCornerShape(7.dp)
private val FooterIconShape = RoundedCornerShape(7.dp)

@Composable
internal fun VeneraLightRailSkin(
    scope: VeneraRailSkinScope,
    currentTab: VeneraNavTab?,
    onTabSelected: (VeneraNavTab) -> Unit,
    secondaryGroups: List<VeneraSideBarGroup>,
    downloadActivity: VeneraSideBarDownloadActivity?,
    onDownloadActivityClick: (() -> Unit)?,
    downloadsSelected: Boolean,
    accountCard: VeneraSideBarAccountCard?,
    onAccountCardClick: (() -> Unit)?,
    onSettingsSelected: (() -> Unit)?,
    settingsSelected: Boolean,
) {
    val expanded = scope.isExpanded
    // 两档内容按各自目标宽度落位，宽度动画只裁剪窗口：
    // 展开档始终以 260 宽排版，收起时右缘被逐帧裁掉，文字不再在 64 宽里压缩重排。
    val expandedW = scope.expandedWidth.takeIf { it != Dp.Unspecified } ?: 260.dp
    val collapsedW = scope.collapsedWidth.takeIf { it != Dp.Unspecified } ?: 64.dp
    Box(modifier = Modifier.fillMaxSize()) {
        LightGlassBackdrop(modifier = Modifier.matchParentSize())
        // 三块玻璃同源同值后，右缘那条发丝线反而成了"两块板"的证据，已删；
        // 分区改由导航项自身的选中态承担，不再靠画线切分。
        if (expanded) {
            // requiredWidth 绕开父窗口的窄约束：宽度动画未走完时展开档仍按 260 排版，只被裁不被压。
            Box(modifier = Modifier.requiredWidth(expandedW).fillMaxHeight()) {
                LightRailExpanded(
                    currentTab = currentTab,
                    onTabSelected = onTabSelected,
                    secondaryGroups = secondaryGroups,
                    downloadActivity = downloadActivity,
                    onDownloadActivityClick = onDownloadActivityClick,
                    downloadsSelected = downloadsSelected,
                    accountCard = accountCard,
                    onAccountCardClick = onAccountCardClick,
                    onSettingsSelected = onSettingsSelected,
                    settingsSelected = settingsSelected,
                    onCollapse = scope.onToggleExpanded,
                )
            }
        } else {
            Box(modifier = Modifier.width(collapsedW).fillMaxHeight()) {
                LightRailCollapsed(
                    currentTab = currentTab,
                    onTabSelected = onTabSelected,
                    secondaryGroups = secondaryGroups,
                    downloadActivity = downloadActivity,
                    onDownloadActivityClick = onDownloadActivityClick,
                    downloadsSelected = downloadsSelected,
                    accountCard = accountCard,
                    onAccountCardClick = onAccountCardClick,
                    onSettingsSelected = onSettingsSelected,
                    settingsSelected = settingsSelected,
                    onExpand = scope.onToggleExpanded,
                )
            }
        }
    }
}

@Composable
private fun LightGlassBackdrop(modifier: Modifier) {
    val railBlur by com.venera.desktop.settings.DesktopRailBlur.visible.collectAsState()
    val palette = LocalChromePalette.current
    // 与内容区/顶栏共用 DesktopGlass.surface，避免深浅色下侧栏基色或浓度漂移。
    val bg = if (DesktopDwmBackdrop.active && railBlur) {
        com.venera.desktop.gallery.ui.DesktopGlass.surface(palette)
    } else {
        palette.page
    }
    Box(modifier.background(bg))
}

// ────────────────────────── 展开档（248dp）──────────────────────────

@Composable
private fun LightRailExpanded(
    currentTab: VeneraNavTab?,
    onTabSelected: (VeneraNavTab) -> Unit,
    secondaryGroups: List<VeneraSideBarGroup>,
    downloadActivity: VeneraSideBarDownloadActivity?,
    onDownloadActivityClick: (() -> Unit)?,
    downloadsSelected: Boolean,
    accountCard: VeneraSideBarAccountCard?,
    onAccountCardClick: (() -> Unit)?,
    onSettingsSelected: (() -> Unit)?,
    settingsSelected: Boolean,
    onCollapse: () -> Unit,
) {
    val ux = LocalUxPlatformServices.current
    val petVisible by com.venera.desktop.settings.DesktopPetVisibility.visible.collectAsState()
    val anySecondarySelected =
        settingsSelected || downloadsSelected || secondaryGroups.any { it.items.any { item -> item.selected } }
    val groupExpandedMap = remember { mutableStateMapOf<String, Boolean>() }

    Column(modifier = Modifier.fillMaxSize()) {
        // 1. 顶部品牌区（保留 [V] VENERA）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 12.dp, top = 14.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = androidx.compose.ui.res.painterResource("vcomic_icon.png"),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "VCOMIC",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = LightRailTokens.ink1,
            )
        }

        // 2. 主导航区（4 项核心入口）：与品牌区留出一段呼吸距离
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, top = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            VeneraNavTab.entries.forEach { tab ->
                if (tab == VeneraNavTab.EXPLORE) return@forEach
                val selected = !anySecondarySelected && tab == currentTab
                MacNavRow(
                    rowHeight = 36.dp,
                    icon = { LightTabIcon(tab, selected = selected, size = 18.dp) },
                    title = if (tab == VeneraNavTab.SEARCH) "漫画搜索" else tab.title,
                    selected = selected,
                    onClick = { if (!selected) { ux.hapticTap(); onTabSelected(tab) } },
                )
            }
        }

        MacDivider()

        // 3. 中间可折叠分组区 + 桌宠区
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 8.dp, end = 8.dp, bottom = if (petVisible) 168.dp else 12.dp),
            ) {
                secondaryGroups.forEach { group ->
                    if (group.title.isBlank()) {
                        group.items.forEach { item ->
                            MacNavRow(
                                rowHeight = 34.dp,
                                icon = {
                                    Icon(
                                        item.icon,
                                        contentDescription = null,
                                        tint = if (item.selected) LightRailTokens.accentIcon else LightRailTokens.ink2,
                                        modifier = Modifier.size(17.dp),
                                    )
                                },
                                title = item.title,
                                selected = item.selected,
                                onClick = { if (!item.selected) { ux.hapticTap(); item.onClick() } },
                            )
                        }
                    } else {
                        val isGroupExpanded = groupExpandedMap[group.title] ?: true
                        MacCollapsibleGroup(
                            title = group.title,
                            expanded = isGroupExpanded,
                            onToggle = {
                                ux.hapticTap()
                                groupExpandedMap[group.title] = !isGroupExpanded
                            },
                        ) {
                            group.items.forEach { item ->
                                MacNavRow(
                                    rowHeight = 34.dp,
                                    icon = {
                                        Icon(
                                            item.icon,
                                            contentDescription = null,
                                            tint = if (item.selected) LightRailTokens.accentIcon else LightRailTokens.ink2,
                                            modifier = Modifier.size(17.dp),
                                        )
                                    },
                                    title = item.title,
                                    selected = item.selected,
                                    onClick = { if (!item.selected) { ux.hapticTap(); item.onClick() } },
                                )
                            }
                        }
                    }
                }
            }

            // 4. 桌宠区（受外观设置开关控制）
            if (petVisible) {
                MacPetZone(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 6.dp),
                )
            }
        }

        // 5. 极简单行底栏（46dp）
        MacFooterBar(
            downloadActivity = downloadActivity,
            onDownloadActivityClick = onDownloadActivityClick,
            downloadsSelected = downloadsSelected,
            accountCard = accountCard,
            onAccountCardClick = onAccountCardClick,
            onSettingsSelected = onSettingsSelected,
            settingsSelected = settingsSelected,
            onCollapse = onCollapse,
        )
    }
}

@Composable
private fun MacCollapsibleGroup(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
        label = "GroupChevron",
    )

    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (hovered) LightRailTokens.hover else Color.Transparent)
                .clickable(interactionSource = interaction, indication = null, onClick = onToggle)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = if (hovered) LightRailTokens.ink2 else LightRailTokens.ink3,
                modifier = Modifier
                    .size(15.dp)
                    .graphicsLayer { rotationZ = chevronRotation },
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (hovered) LightRailTokens.ink2 else LightRailTokens.ink3,
                maxLines = 1,
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(200, easing = FastOutSlowInEasing)) + fadeIn(tween(160)),
            exit = shrinkVertically(tween(180, easing = FastOutSlowInEasing)) + fadeOut(tween(120)),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun MacPetZone(modifier: Modifier = Modifier) {
    // 暂注释 PetFloat 无限浮动动画：持续驱动 Modifier.offset 每帧重组与全窗 GPU 重绘，导致空载单核高占用。
    // val infiniteTransition = rememberInfiniteTransition(label = "PetFloat")
    // val floatOffset by infiniteTransition.animateFloat(
    //     initialValue = 0f,
    //     targetValue = -3f,
    //     animationSpec = infiniteRepeatable(
    //         animation = tween(durationMillis = 2100, easing = FastOutSlowInEasing),
    //         repeatMode = RepeatMode.Reverse,
    //     ),
    //     label = "PetFloatY",
    // )
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        // 脚底淡紫色环境光斑
        Box(
            modifier = Modifier
                .padding(bottom = 4.dp)
                .size(width = 96.dp, height = 20.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(LightRailTokens.accent.copy(alpha = 0.24f), Color.Transparent),
                    ),
                    shape = CircleShape,
                )
        )
        DesktopPetSlot()
    }
}

@Composable
private fun MacFooterBar(
    downloadActivity: VeneraSideBarDownloadActivity?,
    onDownloadActivityClick: (() -> Unit)?,
    downloadsSelected: Boolean,
    accountCard: VeneraSideBarAccountCard?,
    onAccountCardClick: (() -> Unit)?,
    onSettingsSelected: (() -> Unit)?,
    settingsSelected: Boolean,
    onCollapse: () -> Unit,
) {
    val ux = LocalUxPlatformServices.current
    val downloadAnchor = LocalVeneraSideBarDownloadAnchor.current
    val anchorPopKey = downloadAnchor?.popTick?.value ?: 0
    val anchorPopScale = remember { Animatable(1f) }
    LaunchedEffect(anchorPopKey) {
        if (anchorPopKey > 0) {
            anchorPopScale.animateTo(1.32f, tween(110))
            anchorPopScale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 850f))
        }
    }
    val flyEntryReporter: Modifier = if (downloadAnchor != null) {
        Modifier.onGloballyPositioned { downloadAnchor.boundsInRoot.value = it.boundsInRoot() }
    } else {
        Modifier
    }

    val activeCount = downloadActivity?.activeCount ?: 0

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            // 只留一丝压暗做图标衬底，不再叠 18% 黑——否则底条脱离玻璃、看着像实板。
            .background(if (LightRailTokens.dark) Color(0x0F000000) else Color(0x05000000)),
    ) {
        // 顶边分割线 / 活跃下载进度光带
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .height(1.dp)
                .background(LightRailTokens.hairline),
        )
        if (activeCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth(0.68f)
                    .height(1.5.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, LightRailTokens.accent, LightRailTokens.accentIcon),
                        )
                    ),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // 折叠钮钉在底栏最左：与收起态竖列最底那颗展开钮同一纵线，收起/展开时按钮不横跳。
            MacFooterIconButton(
                icon = Icons.AutoMirrored.Outlined.ViewSidebar,
                contentDescription = "折叠导航栏",
                selected = false,
                onClick = { ux.hapticTap(); onCollapse() },
            )
            // 左侧：头像 + 用户名
            if (accountCard != null && onAccountCardClick != null) {
                val chipInteraction = remember { MutableInteractionSource() }
                val chipHovered by chipInteraction.collectIsHoveredAsState()
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (chipHovered) LightRailTokens.hover else Color.Transparent)
                        .clickable(interactionSource = chipInteraction, indication = null) {
                            ux.hapticTap()
                            onAccountCardClick()
                        }
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.size(28.dp)) {
                        LightAvatar(accountCard, size = 28.dp)
                        if (accountCard.logged) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(LightRailTokens.statusOnline)
                                    .border(1.5.dp, Color(0xFF18191E), CircleShape),
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = accountCard.name,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = LightRailTokens.ink1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                Spacer(Modifier.weight(1f))
            }

            // 右侧：下载 + 设置
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (downloadActivity != null && onDownloadActivityClick != null) {
                    MacFooterIconButton(
                        icon = Icons.Outlined.Download,
                        contentDescription = "下载与本地",
                        selected = downloadsSelected,
                        badgeCount = activeCount,
                        badgeScale = anchorPopScale.value,
                        modifier = flyEntryReporter,
                        onClick = { ux.hapticTap(); onDownloadActivityClick() },
                    )
                }
                if (onSettingsSelected != null) {
                    MacFooterIconButton(
                        icon = Icons.Outlined.Settings,
                        contentDescription = "设置",
                        selected = settingsSelected,
                        onClick = { ux.hapticTap(); onSettingsSelected() },
                    )
                }
            }
        }
    }
}

@Composable
private fun MacFooterIconButton(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    badgeCount: Int = 0,
    badgeScale: Float = 1f,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 600f),
        label = "FooterBtnScale",
    )

    Box(
        modifier = modifier
            .size(32.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(FooterIconShape)
            .background(
                when {
                    selected -> LightRailTokens.pill
                    hovered -> LightRailTokens.hover
                    else -> Color.Transparent
                }
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (selected || hovered) LightRailTokens.ink1 else LightRailTokens.ink2,
            modifier = Modifier.size(18.dp),
        )
        if (badgeCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 1.dp, end = 1.dp)
                    .graphicsLayer {
                        scaleX = badgeScale
                        scaleY = badgeScale
                    }
                    .background(LightRailTokens.accent, CircleShape)
                    .padding(horizontal = 3.5.dp, vertical = 0.5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (badgeCount > 99) "99+" else "$badgeCount",
                    color = DesktopChromeColors.SurfaceWhite,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 10.sp,
                )
            }
        }
    }
}

// ────────────────────────── 折叠档（60dp）──────────────────────────

private data class LightRailChannel(
    val key: String,
    val title: String,
    val icon: ImageVector,
    val entries: List<VeneraSideBarItem>,
)

@Composable
private fun LightRailCollapsed(
    currentTab: VeneraNavTab?,
    onTabSelected: (VeneraNavTab) -> Unit,
    secondaryGroups: List<VeneraSideBarGroup>,
    downloadActivity: VeneraSideBarDownloadActivity?,
    onDownloadActivityClick: (() -> Unit)?,
    downloadsSelected: Boolean,
    accountCard: VeneraSideBarAccountCard?,
    onAccountCardClick: (() -> Unit)?,
    onSettingsSelected: (() -> Unit)?,
    settingsSelected: Boolean,
    onExpand: () -> Unit,
) {
    val ux = LocalUxPlatformServices.current
    val flatItems = secondaryGroups.flatMap { it.items }
    fun sec(key: String) = flatItems.firstOrNull { it.key == key }
    val tierItems = listOfNotNull(sec("reverse-search"), sec("history"), sec("files"))
    val galleryItems = listOfNotNull(sec("daily"), sec("gallery-saved"), sec("followed-artists"), sec("browse-history"), sec("bookmarks"), sec("pixiv"))
    val toolItems = listOfNotNull(sec("stats"), sec("sources"), sec("backup"))
    val mappedKeys = setOf("history", "files", "daily", "gallery-saved", "followed-artists", "browse-history", "bookmarks", "pixiv", "reverse-search", "stats", "sources", "backup")
    val rest = flatItems.filter { it.key !in mappedKeys }
    val channels = buildList {
        if (galleryItems.isNotEmpty()) {
            add(LightRailChannel("gallery", "画廊频道", Icons.Outlined.Layers, galleryItems))
        }
        if (toolItems.isNotEmpty()) {
            add(LightRailChannel("tools", "工具频道", Icons.Outlined.Build, toolItems))
        }
        if (rest.isNotEmpty()) {
            add(LightRailChannel("more", "更多", Icons.Outlined.MoreHoriz, rest))
        }
    }
    val anySecondarySelected =
        settingsSelected || downloadsSelected || secondaryGroups.any { it.items.any { item -> item.selected } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = androidx.compose.ui.res.painterResource("vcomic_icon.png"),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(7.dp)),
            )
        }
        Spacer(Modifier.height(6.dp))

        VeneraNavTab.entries.forEach { tab ->
            if (tab == VeneraNavTab.EXPLORE) return@forEach
            val selected = !anySecondarySelected && tab == currentTab
            LightRailCell(selected = selected, onClick = { if (!selected) { ux.hapticTap(); onTabSelected(tab) } }) {
                LightTabIcon(tab, selected = selected, size = 18.dp)
            }
        }

        MacDivider()

        tierItems.forEach { item ->
            LightRailCell(selected = item.selected, onClick = { if (!item.selected) { ux.hapticTap(); item.onClick() } }) {
                Icon(
                    item.icon,
                    contentDescription = item.title,
                    tint = if (item.selected) LightRailTokens.accentIcon else LightRailTokens.ink2,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        channels.forEach { channel ->
            var pinned by remember(channel.key) { mutableStateOf(false) }
            var hover by remember(channel.key) { mutableStateOf(false) }
            val channelActive = channel.entries.any { it.selected }
            LightRailCell(
                selected = false,
                hint = channelActive,
                hovered = hover,
                onClick = { ux.hapticTap(); pinned = !pinned },
                onHover = { hover = it },
            ) {
                Icon(
                    channel.icon,
                    contentDescription = channel.title,
                    tint = if (channelActive) LightRailTokens.accentIcon.copy(alpha = 0.75f) else LightRailTokens.ink2,
                    modifier = Modifier.size(18.dp),
                )
                if (pinned) {
                    LightChannelPopup(
                        channel = channel,
                        onDismiss = { pinned = false },
                        onItem = { item ->
                            ux.hapticTap()
                            pinned = false
                            item.onClick()
                        },
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            val petVisible by com.venera.desktop.settings.DesktopPetVisibility.visible.collectAsState()
            if (petVisible) {
                DesktopPetSlot(
                    compact = true,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
        }

        if (accountCard != null && onAccountCardClick != null) {
            Box(
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { ux.hapticTap(); onAccountCardClick() },
                contentAlignment = Alignment.Center,
            ) {
                LightAvatar(accountCard, size = 26.dp)
            }
        }
        if (downloadActivity != null && onDownloadActivityClick != null) {
            MacFooterIconButton(
                icon = Icons.Outlined.Download,
                contentDescription = "下载与本地",
                selected = downloadsSelected,
                badgeCount = downloadActivity.activeCount,
                onClick = { ux.hapticTap(); onDownloadActivityClick() },
            )
        }
        if (onSettingsSelected != null) {
            MacFooterIconButton(
                icon = Icons.Outlined.Settings,
                contentDescription = "设置",
                selected = settingsSelected,
                onClick = { ux.hapticTap(); onSettingsSelected() },
            )
        }
        MacFooterIconButton(
            icon = Icons.AutoMirrored.Outlined.ViewSidebar,
            contentDescription = "展开导航栏",
            selected = false,
            onClick = { ux.hapticTap(); onExpand() },
        )
    }
}

// ────────────────────────── 基础组件 ──────────────────────────

@Composable
private fun MacNavRow(
    rowHeight: Dp,
    icon: @Composable () -> Unit,
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 600f),
        label = "NavRowScale",
    )
    val iconScale by animateFloatAsState(
        targetValue = if (hovered && !selected) 1.06f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 500f),
        label = "NavIconScale",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(NavRowShape)
            .background(
                when {
                    selected -> LightRailTokens.pill
                    hovered -> LightRailTokens.hover
                    else -> Color.Transparent
                }
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(3.dp)
                    .height(rowHeight - 14.dp)
                    .clip(RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp))
                    .background(LightRailTokens.accent),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                },
            ) {
                icon()
            }
            Spacer(Modifier.width(9.dp))
            Text(
                text = title,
                fontSize = if (rowHeight >= 36.dp) 14.sp else 13.5.sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                color = if (selected || hovered) LightRailTokens.ink1 else LightRailTokens.ink2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun LightRailCell(
    selected: Boolean,
    onClick: () -> Unit,
    hovered: Boolean = false,
    // 频道锚点用：频道内有选中项时给轻底提示，强度低于真实选中，避免同屏两处"同样亮"。
    hint: Boolean = false,
    onHover: ((Boolean) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    var mod = Modifier
        .padding(vertical = 2.dp)
        .size(40.dp)
        .clip(RoundedCornerShape(9.dp))
        .background(
            when {
                selected -> LightRailTokens.pill
                hint -> LightRailTokens.accent.copy(alpha = 0.10f)
                hovered -> LightRailTokens.hover
                else -> Color.Transparent
            }
        )
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    if (onHover != null) {
        mod = mod.hoverable(interaction)
    }
    Box(modifier = mod, contentAlignment = Alignment.Center) {
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(3.dp)
                    .height(18.dp)
                    .clip(RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp))
                    .background(LightRailTokens.accent),
            )
        }
        content()
    }
}

@Composable
private fun LightTabIcon(tab: VeneraNavTab, selected: Boolean, size: Dp = 15.dp) {
    Icon(
        imageVector = if (selected) tab.filledIcon else tab.outlinedIcon,
        contentDescription = tab.title,
        tint = if (selected) LightRailTokens.accentIcon else LightRailTokens.ink2,
        modifier = Modifier.size(size),
    )
}

@Composable
private fun MacDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .height(1.dp)
            .background(LightRailTokens.hairline)
    )
}

@Composable
private fun LightAvatar(card: VeneraSideBarAccountCard, size: Dp = 24.dp) {
    if (!card.avatarUrl.isNullOrBlank()) {
        VeneraCover(
            url = card.avatarUrl,
            contentDescription = card.name,
            modifier = Modifier.size(size).clip(CircleShape),
            mask = VeneraCoverMask.Visible,
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFF7FA8C9), Color(0xFF4C6E92)))),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                card.initial,
                color = DesktopChromeColors.SurfaceWhite,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun LightChannelPopup(
    channel: LightRailChannel,
    onDismiss: () -> Unit,
    onItem: (VeneraSideBarItem) -> Unit,
) {
    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(44, -10),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            modifier = Modifier
                .width(180.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(LightRailTokens.card)
                .padding(6.dp),
        ) {
            Text(
                channel.title,
                fontSize = 11.sp,
                color = LightRailTokens.ink3,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
            channel.entries.forEach { item ->
                val interaction = remember { MutableInteractionSource() }
                val hovered by interaction.collectIsHoveredAsState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (item.selected || hovered) LightRailTokens.pill else Color.Transparent)
                        .clickable(interactionSource = interaction, indication = null) { onItem(item) }
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        item.icon,
                        contentDescription = null,
                        tint = if (item.selected) LightRailTokens.accentIcon else LightRailTokens.ink2,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        item.title,
                        fontSize = 12.5.sp,
                        color = if (item.selected) LightRailTokens.ink1 else LightRailTokens.ink2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
