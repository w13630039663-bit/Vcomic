package com.venera.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
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
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

private object LightRailTokens {
    private val palette: DesktopChromePalette
        @Composable @ReadOnlyComposable get() = LocalChromePalette.current

    val ink1: Color @Composable @ReadOnlyComposable get() = palette.ink1
    val ink2: Color @Composable @ReadOnlyComposable get() = palette.ink2
    val ink3: Color @Composable @ReadOnlyComposable get() = palette.ink3
    val pill: Color @Composable @ReadOnlyComposable get() = palette.pill
    val railLine: Color @Composable @ReadOnlyComposable get() = palette.railLine
    val card: Color @Composable @ReadOnlyComposable get() = palette.card
    val hover: Color @Composable @ReadOnlyComposable get() = palette.hover
    val accent: Color @Composable @ReadOnlyComposable get() = palette.accent
    val dark: Boolean @Composable @ReadOnlyComposable get() = palette.dark
}

private val FooterIconShape = RoundedCornerShape(8.dp)

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
    val expandedW = scope.expandedWidth.takeIf { it != Dp.Unspecified } ?: 240.dp
    val collapsedW = scope.collapsedWidth.takeIf { it != Dp.Unspecified } ?: 64.dp
    Box(modifier = Modifier.fillMaxSize()) {
        LightGlassBackdrop(modifier = Modifier.matchParentSize())
        if (expanded) {
            // requiredWidth 绕开父窗口的窄约束：宽度动画未走完时展开档仍按目标宽排版，只被裁不被压。
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
        // 全窗同透明度下以 1dp 轻发丝线切分侧栏与内容区。
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(1.dp)
                .fillMaxHeight()
                .background(LightRailTokens.railLine),
        )
    }
}

@Composable
private fun LightGlassBackdrop(modifier: Modifier) {
    val railBlur by com.venera.desktop.settings.DesktopRailBlur.visible.collectAsState()
    val palette = LocalChromePalette.current
    // 与内容区/顶栏共用 DesktopGlass.currentSurface，确保 Acrylic/Mica 两档全窗浓度一致。
    val bg = if (DesktopDwmBackdrop.active && railBlur) {
        com.venera.desktop.gallery.ui.DesktopGlass.currentSurface(palette)
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
        // 1. 顶部轻量化品牌区
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = androidx.compose.ui.res.painterResource("vcomic_icon.png"),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(6.dp)),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "VCOMIC",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp,
                color = LightRailTokens.ink1,
            )
        }

        // 2. 主导航区（2×2 Bento 磁贴网格）
        DesktopRailBentoGrid(
            currentTab = currentTab,
            anySecondarySelected = anySecondarySelected,
            onTabSelected = onTabSelected,
        )

        // 3. 下半区容器：滚动列表与桌宠铺满，底部叠放真 Haze 虚化底坞
        val dockHazeState = rememberHazeState()
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(dockHazeState, zIndex = 0f),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = if (petVisible) 298.dp else 166.dp),
                ) {
                    secondaryGroups.forEach { group ->
                        if (group.title.isBlank()) {
                            group.items.forEach { item ->
                                DesktopRailMonoNavRow(
                                    icon = item.icon,
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
                                    DesktopRailMonoNavRow(
                                        icon = item.icon,
                                        title = item.title,
                                        selected = item.selected,
                                        onClick = { if (!item.selected) { ux.hapticTap(); item.onClick() } },
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. 桌宠区（受外观设置开关控制，站立在天象开关正上方）
                if (petVisible) {
                    DesktopRailPetZone(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 152.dp),
                    )
                }
            }

            // 5. 底部堆叠区：星穹实时天气昼夜开关 + 悬浮双层身份底坞
            Column(
                modifier = Modifier.align(Alignment.BottomCenter),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DesktopRailThemeSwitch(expanded = true)
                DesktopRailFloatingDock(
                    downloadActivity = downloadActivity,
                    onDownloadActivityClick = onDownloadActivityClick,
                    downloadsSelected = downloadsSelected,
                    accountCard = accountCard,
                    onAccountCardClick = onAccountCardClick,
                    onSettingsSelected = onSettingsSelected,
                    settingsSelected = settingsSelected,
                    onCollapse = onCollapse,
                    hazeState = dockHazeState,
                )
            }
        }
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

    Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        // 分组标题左缘与下方条目图标左缘对齐，小箭头置右。
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(interactionSource = interaction, indication = null, onClick = onToggle)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (hovered) LightRailTokens.ink2 else LightRailTokens.ink3,
                maxLines = 1,
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = if (hovered) LightRailTokens.ink2 else LightRailTokens.ink3,
                modifier = Modifier
                    .size(14.dp)
                    .graphicsLayer { rotationZ = chevronRotation },
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(180, easing = FastOutSlowInEasing)) + fadeIn(tween(140)),
            exit = shrinkVertically(tween(160, easing = FastOutSlowInEasing)) + fadeOut(tween(110)),
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
private fun MacFooterIconButton(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    badgeCount: Int = 0,
    onClick: () -> Unit,
) {
    val palette = LocalChromePalette.current
    val glassActive = rememberDesktopRailGlassActive()
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    Box(
        modifier = Modifier
            .size(32.dp)
            .background(desktopRailGlassSurface(palette, glassActive, selected, hovered), FooterIconShape)
            .clip(FooterIconShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = when {
                selected -> LightRailTokens.accent
                hovered -> LightRailTokens.ink1
                else -> LightRailTokens.ink2
            },
            modifier = Modifier.size(18.dp),
        )
        if (badgeCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 1.dp, end = 1.dp)
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

        Spacer(Modifier.height(10.dp))

        tierItems.forEach { item ->
            LightRailCell(selected = item.selected, onClick = { if (!item.selected) { ux.hapticTap(); item.onClick() } }) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.title,
                    tint = if (item.selected) LightRailTokens.accent else LightRailTokens.ink2,
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
                    tint = if (channelActive) LightRailTokens.accent else LightRailTokens.ink2,
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

        DesktopRailThemeSwitch(
            expanded = false,
            modifier = Modifier.padding(bottom = 6.dp),
        )
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
private fun LightRailCell(
    selected: Boolean,
    onClick: () -> Unit,
    hovered: Boolean = false,
    // 频道锚点用：频道内有选中项时给轻底提示，强度低于真实选中，避免同屏两处"同样亮"。
    hint: Boolean = false,
    onHover: ((Boolean) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val palette = LocalChromePalette.current
    val glassActive = rememberDesktopRailGlassActive()
    val interaction = remember { MutableInteractionSource() }
    val cellShape = RoundedCornerShape(9.dp)
    val cellBg = desktopRailGlassSurface(palette, glassActive, selected, hovered || hint)
    var mod = Modifier
        .padding(vertical = 2.dp)
        .size(40.dp)
        .background(cellBg, cellShape)
        .clip(cellShape)
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    if (onHover != null) {
        mod = mod.hoverable(interaction)
    }
    Box(modifier = mod, contentAlignment = Alignment.Center) {
        content()
    }
}

@Composable
private fun LightTabIcon(tab: VeneraNavTab, selected: Boolean, size: Dp = 15.dp) {
    Icon(
        imageVector = if (selected) tab.filledIcon else tab.outlinedIcon,
        contentDescription = tab.title,
        tint = if (selected) LightRailTokens.accent else LightRailTokens.ink2,
        modifier = Modifier.size(size),
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
                        tint = if (item.selected) LightRailTokens.ink1 else LightRailTokens.ink2,
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
