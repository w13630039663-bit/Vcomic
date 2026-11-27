package com.venera.desktop.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ZoomOutMap
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.gallery.data.DesktopComicPage
import com.venera.desktop.ui.theme.VeneraType


internal val HudSurface = Color(0xE616181D)
internal val HudBorder = Color(0x2E3A46)
internal val HudTextPrimary = Color(0xFFF3F4F6)
internal val HudTextDim = Color(0xB3FFFFFF)
internal val HudRowStroke = Color(0x3A4452)
internal val HudDropdownBg = Color(0xFF262B33)
internal val HudDivider = Color(0x1FFFFFFF)

/** 浮层进出时长：进入从容、退出利落（退出短于进入，避免拖尾）。 */
internal const val HUD_ENTER_MS = 220
internal const val HUD_EXIT_MS = 150

/** 设置弹窗段位：0=电子书 1=页 2=筛选 3=AI。顶栏四段钮按此定位。 */
internal const val SETTINGS_TAB_EBOOK = 0
internal const val SETTINGS_TAB_PAGE = 1
internal const val SETTINGS_TAB_FILTER = 2
internal const val SETTINGS_TAB_AI = 3

/**
 * 阅读器顶部极简悬浮栏。
 */
@Composable
internal fun ReaderTopHud(
    visible: Boolean,
    title: String,
    subtitle: String,
    canPrevChapter: Boolean,
    canNextChapter: Boolean,
    hasChapters: Boolean,
    isBookmarked: Boolean,
    magnifierOn: Boolean,
    stripOpen: Boolean,
    tocOpen: Boolean,
    settingsOpen: Boolean,
    onBack: () -> Unit,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onRotate: () -> Unit,
    onToggleMagnifier: () -> Unit,
    onToggleBookmark: () -> Unit,
    onToggleStrip: () -> Unit,
    onToggleToc: () -> Unit,
    onToggleSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(HUD_ENTER_MS)) + slideInVertically(tween(HUD_ENTER_MS)) { -it / 2 },
        exit = fadeOut(tween(HUD_EXIT_MS)) + slideOutVertically(tween(HUD_EXIT_MS)) { -it / 2 },
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .clip(RoundedCornerShape(12.dp))
                    .background(HudSurface)
                    .border(1.dp, HudBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HudIconButton(
                    icon = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = com.venera.desktop.settings.DesktopI18n.t("返回", "Back"),
                    onClick = onBack,
                )
                Column(modifier = Modifier.widthIn(max = 420.dp)) {
                    Text(
                        text = title.ifBlank { subtitle },
                        fontSize = VeneraType.body.size,
                        fontWeight = FontWeight.SemiBold,
                        color = HudTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (title.isNotBlank() && subtitle.isNotBlank() && title != subtitle) {
                        Text(
                            text = subtitle,
                            fontSize = VeneraType.aux.size,
                            color = HudTextDim,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (canPrevChapter || canNextChapter) {
                    Spacer(Modifier.width(4.dp))
                    HudIconButton(
                        icon = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                        contentDescription = com.venera.desktop.settings.DesktopI18n.t("上一话", "Prev chapter"),
                        enabled = canPrevChapter,
                        onClick = onPrevChapter,
                    )
                    HudIconButton(
                        icon = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = com.venera.desktop.settings.DesktopI18n.t("下一话", "Next chapter"),
                        enabled = canNextChapter,
                        onClick = onNextChapter,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(HudSurface)
                    .border(1.dp, HudBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                HudIconButton(
                    icon = Icons.Outlined.Settings,
                    contentDescription = com.venera.desktop.settings.DesktopI18n.t("旋转", "Rotate"),
                    onClick = onRotate,
                )
                HudIconButton(
                    icon = Icons.Outlined.ZoomOutMap,
                    contentDescription = com.venera.desktop.settings.DesktopI18n.t("放大镜", "Magnifier"),
                    active = magnifierOn,
                    onClick = onToggleMagnifier,
                )
                HudIconButton(
                    icon = if (isBookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = com.venera.desktop.settings.DesktopI18n.t("书签", "Bookmark"),
                    active = isBookmarked,
                    onClick = onToggleBookmark,
                )
                HudIconButton(
                    icon = Icons.Outlined.Collections,
                    contentDescription = com.venera.desktop.settings.DesktopI18n.t("缩略图", "Thumbnails"),
                    active = stripOpen,
                    onClick = onToggleStrip,
                )
                if (hasChapters) {
                    HudIconButton(
                        icon = Icons.Outlined.FormatListBulleted,
                        contentDescription = com.venera.desktop.settings.DesktopI18n.t("目录", "Chapters"),
                        active = tocOpen,
                        onClick = onToggleToc,
                    )
                }
                HudIconButton(
                    icon = Icons.Outlined.Settings,
                    contentDescription = com.venera.desktop.settings.DesktopI18n.t("设置", "Settings"),
                    active = settingsOpen,
                    onClick = onToggleSettings,
                )
            }
        }
    }
}

/**
 * 按需呼出的底部缩略图胶片条。
 */
@Composable
internal fun ReaderThumbnailStripOverlay(
    visible: Boolean,
    pages: List<DesktopComicPage>,
    currentPage: Int,
    onSelectPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    val stripState = rememberLazyListState()
    LaunchedEffect(visible, currentPage) {
        if (visible && currentPage in pages.indices) {
            stripState.animateScrollToItem((currentPage - 2).coerceAtLeast(0))
        }
    }
    AnimatedVisibility(
        visible = visible && pages.isNotEmpty(),
        enter = fadeIn(tween(HUD_ENTER_MS)) + slideInVertically(tween(HUD_ENTER_MS)) { it / 2 },
        exit = fadeOut(tween(HUD_EXIT_MS)) + slideOutVertically(tween(HUD_EXIT_MS)) { it / 2 },
        modifier = modifier,
    ) {
        LazyRow(
            state = stripState,
            modifier = Modifier
                .padding(horizontal = 22.dp, vertical = 82.dp)
                .widthIn(max = 860.dp)
                .height(116.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(HudSurface)
                .border(1.dp, HudBorder, RoundedCornerShape(16.dp)),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(pages) { index, page ->
                val selected = index == currentPage
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(0.7f)
                        .clip(RoundedCornerShape(8.dp))
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) tokens.color.primary else HudBorder,
                            shape = RoundedCornerShape(8.dp),
                        )
                        .clickable { onSelectPage(index) },
                ) {
                    AsyncImage(
                        model = page,
                        contentDescription = com.venera.desktop.settings.DesktopI18n.t("第 ${index + 1} 页", "Page ${index + 1}"),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize(),
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color(0xAA000000))
                            .padding(vertical = 2.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "${index + 1}",
                            fontSize = VeneraType.aux.size,
                            color = if (selected) tokens.color.primary else HudTextPrimary,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 按需呼出的章节目录与书签悬浮抽屉。
 */
@Composable
internal fun ReaderTocOverlay(
    visible: Boolean,
    episodes: List<String>,
    currentChapter: Int,
    bookmarks: List<Int>,
    onSelectChapter: (Int) -> Unit,
    onSelectBookmarkPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    var tabIndex by remember { mutableStateOf(0) }
    AnimatedVisibility(
        visible = visible && (episodes.isNotEmpty() || bookmarks.isNotEmpty()),
        enter = fadeIn(tween(HUD_ENTER_MS)) + slideInHorizontally(tween(HUD_ENTER_MS)) { it / 2 },
        exit = fadeOut(tween(HUD_EXIT_MS)) + slideOutHorizontally(tween(HUD_EXIT_MS)) { it / 2 },
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .padding(top = 70.dp, end = 20.dp, bottom = 84.dp)
                .width(280.dp)
                .heightIn(max = 520.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(HudSurface)
                .border(1.dp, HudBorder, RoundedCornerShape(16.dp))
                .padding(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                HudModeChip(
                    label = com.venera.desktop.settings.DesktopI18n.t("目录 (${episodes.size})", "Chapters (${episodes.size})"),
                    selected = tabIndex == 0,
                    onClick = { tabIndex = 0 },
                )
                HudModeChip(
                    label = com.venera.desktop.settings.DesktopI18n.t("书签 (${bookmarks.size})", "Bookmarks (${bookmarks.size})"),
                    selected = tabIndex == 1,
                    onClick = { tabIndex = 1 },
                )
            }
            if (tabIndex == 0) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    itemsIndexed(episodes) { idx, label ->
                        val active = idx == currentChapter
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (active) tokens.color.primary.copy(alpha = 0.22f) else Color.Transparent)
                                .clickable { onSelectChapter(idx) }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = label,
                                fontSize = VeneraType.aux.size,
                                color = if (active) tokens.color.primary else HudTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (active) {
                                Text(
                                    text = com.venera.desktop.settings.DesktopI18n.t("阅读中", "Reading"),
                                    fontSize = VeneraType.aux.size,
                                    color = tokens.color.primary,
                                )
                            }
                        }
                    }
                }
            } else {
                if (bookmarks.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(com.venera.desktop.settings.DesktopI18n.t("暂无书签", "No bookmarks"), fontSize = VeneraType.aux.size, color = HudTextDim)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(bookmarks) { pageIdx ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onSelectBookmarkPage(pageIdx) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = com.venera.desktop.settings.DesktopI18n.t("第 ${pageIdx + 1} 页", "Page ${pageIdx + 1}"),
                                    fontSize = VeneraType.aux.size,
                                    color = HudTextPrimary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun HudIconButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val tokens = VeneraTokens
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(if (active) tokens.color.primary.copy(alpha = 0.25f) else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = when {
                !enabled -> HudTextDim.copy(alpha = 0.35f)
                active -> tokens.color.primary
                else -> HudTextPrimary
            },
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
internal fun HudModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tokens = VeneraTokens
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) tokens.color.primary.copy(alpha = 0.25f) else Color(0x1AFFFFFF))
            .border(
                width = 1.dp,
                color = if (selected) tokens.color.primary else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = label,
            fontSize = VeneraType.aux.size,
            color = if (selected) tokens.color.primary else HudTextPrimary,
        )
    }
}

/**
 * 3D 翻页透视度调节弹窗。
 */
@Composable
internal fun ReaderTurnPerspectiveDialog(
    visible: Boolean,
    perspective: Int,
    onPerspectiveChange: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    val tokens = VeneraTokens
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x88000000))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(320.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(HudSurface)
                .border(1.dp, HudBorder, RoundedCornerShape(16.dp))
                .clickable(enabled = false) {}
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(com.venera.desktop.settings.DesktopI18n.t("翻页", "Page Turn"), fontSize = VeneraType.body.size, fontWeight = FontWeight.SemiBold, color = HudTextPrimary)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(com.venera.desktop.settings.DesktopI18n.t("看法 (透视度)", "Perspective"), fontSize = VeneraType.aux.size, color = HudTextDim)
                Text("$perspective", fontSize = VeneraType.aux.size, color = HudTextPrimary)
            }
            Slider(
                value = perspective.toFloat(),
                onValueChange = { onPerspectiveChange(it.toInt().coerceIn(5, 50)) },
                valueRange = 5f..50f,
                colors = SliderDefaults.colors(
                    thumbColor = tokens.color.primary,
                    activeTrackColor = tokens.color.primary,
                ),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    text = com.venera.desktop.settings.DesktopI18n.t("关闭", "Close"),
                    fontSize = VeneraType.aux.size,
                    color = tokens.color.primary,
                    modifier = Modifier.clickable(onClick = onDismiss).padding(4.dp),
                )
            }
        }
    }
}

/**
 * 导览视图详细设置抽屉弹窗。
 */
@Composable
internal fun ReaderGuidedViewConfigDialog(
    visible: Boolean,
    style: Int,
    padding: Int,
    maxZoom: Float,
    maskStyle: Int,
    showFirst: Boolean,
    showLast: Boolean,
    onUpdate: ((DesktopReaderPrefsState) -> DesktopReaderPrefsState) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    val tokens = VeneraTokens
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x88000000))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(360.dp)
                .heightIn(max = 580.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(HudSurface)
                .border(1.dp, HudBorder, RoundedCornerShape(16.dp))
                .verticalScroll(rememberScrollState())
                .clickable(enabled = false) {}
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(com.venera.desktop.settings.DesktopI18n.t("导览视图", "Guided View"), fontSize = VeneraType.body.size, fontWeight = FontWeight.SemiBold, color = HudTextPrimary)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    com.venera.desktop.settings.DesktopI18n.t("聚焦", "Focus") to 0,
                    com.venera.desktop.settings.DesktopI18n.t("隐藏", "Hide") to 1,
                    com.venera.desktop.settings.DesktopI18n.t("沉浸式", "Immersive") to 2,
                ).forEach { (label, idx) ->
                    HudModeChip(
                        label = label,
                        selected = style == idx,
                        onClick = { onUpdate { it.copy(guidedViewStyle = idx) } },
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(com.venera.desktop.settings.DesktopI18n.t("边距", "Padding"), fontSize = VeneraType.aux.size, color = HudTextDim)
                    Text("${padding}px", fontSize = VeneraType.aux.size, color = HudTextPrimary)
                }
                Slider(
                    value = padding.toFloat(),
                    onValueChange = { v -> onUpdate { it.copy(guidedViewPadding = v.toInt().coerceIn(0, 200)) } },
                    valueRange = 0f..200f,
                    colors = SliderDefaults.colors(thumbColor = tokens.color.primary, activeTrackColor = tokens.color.primary),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(com.venera.desktop.settings.DesktopI18n.t("最大缩放倍数", "Max zoom"), fontSize = VeneraType.aux.size, color = HudTextDim)
                    Text("${String.format("%.1f", maxZoom)}x", fontSize = VeneraType.aux.size, color = HudTextPrimary)
                }
                Slider(
                    value = maxZoom,
                    onValueChange = { v -> onUpdate { it.copy(guidedViewMaxZoom = kotlin.math.round(v * 10f) / 10f) } },
                    valueRange = 1.5f..4.0f,
                    colors = SliderDefaults.colors(thumbColor = tokens.color.primary, activeTrackColor = tokens.color.primary),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(com.venera.desktop.settings.DesktopI18n.t("在首画板前显示完整页面", "Show full page before first panel"), fontSize = VeneraType.aux.size, color = HudTextPrimary)
                com.venera.desktop.ui.DesktopSwitch(
                    checked = showFirst,
                    onCheckedChange = { v -> onUpdate { it.copy(guidedViewShowFullFirst = v) } },
                    colors = com.venera.desktop.ui.DesktopSwitchDefaults.hudDark(),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(com.venera.desktop.settings.DesktopI18n.t("在尾画板后显示完整页面", "Show full page after last panel"), fontSize = VeneraType.aux.size, color = HudTextPrimary)
                com.venera.desktop.ui.DesktopSwitch(
                    checked = showLast,
                    onCheckedChange = { v -> onUpdate { it.copy(guidedViewShowFullLast = v) } },
                    colors = com.venera.desktop.ui.DesktopSwitchDefaults.hudDark(),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(com.venera.desktop.settings.DesktopI18n.t("隐藏效果", "Mask style"), fontSize = VeneraType.aux.size, color = HudTextDim)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        com.venera.desktop.settings.DesktopI18n.t("白色", "White") to 0,
                        com.venera.desktop.settings.DesktopI18n.t("黑色", "Black") to 1,
                        com.venera.desktop.settings.DesktopI18n.t("模糊", "Blur") to 2,
                    ).forEach { (label, idx) ->
                        HudModeChip(
                            label = label,
                            selected = maskStyle == idx,
                            onClick = { onUpdate { it.copy(guidedViewMaskStyle = idx) } },
                        )
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    text = com.venera.desktop.settings.DesktopI18n.t("关闭", "Close"),
                    fontSize = VeneraType.aux.size,
                    color = tokens.color.primary,
                    modifier = Modifier.clickable(onClick = onDismiss).padding(4.dp),
                )
            }
        }
    }
}
