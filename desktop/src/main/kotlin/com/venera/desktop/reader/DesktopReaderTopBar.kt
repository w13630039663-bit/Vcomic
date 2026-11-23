package com.venera.desktop.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Filter
import androidx.compose.material.icons.outlined.FirstPage
import androidx.compose.material.icons.outlined.FitScreen
import androidx.compose.material.icons.outlined.LastPage
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.ui.theme.VeneraType
import dev.nucleusframework.window.windowDragArea

private val TopBarSurface = Color(0xE616181D)
private val TopBarBorder = Color(0x1CFFFFFF)
private val TopBarTextPrimary = Color(0xFFF3F4F6)
private val TopBarTextSecondary = Color(0xFFA1A7B3)
private val TopBarDividerColor = Color(0x1FFFFFFF)

/**
 * 常驻顶部工具栏（分组流对齐稿 §6）。
 *
 * 横向分组：返回｜章节｜四页导航｜书签两颗｜适配/1:1/缩放｜放大镜/跳页｜页码｜四段设置｜更多。
 * 组间 1dp 竖线；全部按钮吃真回调，页码文本受 [showPageNumber] 控制显隐。
 */
@Composable
internal fun ReaderTopBar(
    title: String,
    episodeLabel: String,
    currentPage: Int,
    totalPages: Int,
    canPrevChapter: Boolean,
    canNextChapter: Boolean,
    hasChapters: Boolean,
    isBookmarked: Boolean,
    magnifierOn: Boolean,
    settingsOpen: Boolean,
    settingsTab: Int,
    showPageNumber: Boolean,
    onBack: () -> Unit,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onFirstPage: () -> Unit,
    onLastPage: () -> Unit,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onResetZoom: () -> Unit,
    onCycleFit: () -> Unit,
    onToggleMagnifier: () -> Unit,
    onOpenJumpDialog: () -> Unit,
    onToggleBookmark: () -> Unit,
    onOpenBookmarkList: () -> Unit,
    onOpenSettingsTab: (Int) -> Unit,
    /** 更多菜单：倒序开关（当前态由调用方吃 prefs）。 */
    reverseReading: Boolean,
    onToggleReverse: () -> Unit,
    autoPlaySecs: Int,
    onCycleAutoPlay: () -> Unit,
    onToggleShowPageNumber: () -> Unit,
    onHideTopBar: () -> Unit,
    onInteract: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    var moreMenuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            // 阅读器覆盖全屏后壳的标题栏不可见，顶栏自己承担拖窗；空白处可拖，按钮在上层仍可点。
            .windowDragArea(enabled = true)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                        if (ev.type == PointerEventType.Press) onInteract()
                    }
                }
            }
            .height(TOP_BAR_HEIGHT)
            .background(TopBarSurface)
            .drawBehind {
                drawLine(
                    color = TopBarBorder,
                    start = Offset(0f, size.height - 0.5f),
                    end = Offset(size.width, size.height - 0.5f),
                    strokeWidth = 1f,
                )
            }
            .padding(horizontal = 6.dp)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        TopBarIcon(Icons.AutoMirrored.Outlined.ArrowBack, com.venera.desktop.settings.DesktopI18n.t("返回", "Back"), onClick = onBack)
        TopBarText(title.ifBlank { episodeLabel })
        TopBarDivider()
        // 语义锚点「上一话/下一话」：无章表时整枚不挂载（对齐旧 HUD 口径，disabled 不算"不摆"）。
        if (hasChapters) {
            TopBarIcon(
                Icons.Outlined.SkipPrevious,
                com.venera.desktop.settings.DesktopI18n.t("上一话", "Prev chapter"),
                enabled = hasChapters && canPrevChapter,
                onClick = onPrevChapter,
            )
            TopBarIcon(
                Icons.Outlined.SkipNext,
                com.venera.desktop.settings.DesktopI18n.t("下一话", "Next chapter"),
                enabled = hasChapters && canNextChapter,
                onClick = onNextChapter,
            )
        }
        TopBarDivider()
        TopBarIcon(Icons.Outlined.FirstPage, com.venera.desktop.settings.DesktopI18n.t("首页", "First page"), enabled = totalPages > 0, onClick = onFirstPage)
        TopBarIcon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, com.venera.desktop.settings.DesktopI18n.t("上一页", "Prev page"), enabled = totalPages > 0, onClick = onPrevPage)
        TopBarIcon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, com.venera.desktop.settings.DesktopI18n.t("下一页", "Next page"), enabled = totalPages > 0, onClick = onNextPage)
        TopBarIcon(Icons.Outlined.LastPage, com.venera.desktop.settings.DesktopI18n.t("末页", "Last page"), enabled = totalPages > 0, onClick = onLastPage)
        TopBarDivider()
        TopBarIcon(
            if (isBookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
            com.venera.desktop.settings.DesktopI18n.t("书签", "Bookmark"),
            active = isBookmarked,
            onClick = onToggleBookmark,
        )
        // 语义锚点「目录」：无章表时整枚不挂载（打开章节目录抽屉）。
        if (hasChapters) {
            TopBarIcon(Icons.Outlined.CollectionsBookmark, com.venera.desktop.settings.DesktopI18n.t("目录", "Chapters"), onClick = onOpenBookmarkList)
        }
        TopBarDivider()
        TopBarIcon(Icons.Outlined.FitScreen, com.venera.desktop.settings.DesktopI18n.t("适配", "Fit"), onClick = onCycleFit)
        TopBarTextUnit("1:1", com.venera.desktop.settings.DesktopI18n.t("缩放复位", "Reset zoom"), onClick = onResetZoom)
        TopBarIcon(Icons.Outlined.ZoomOut, com.venera.desktop.settings.DesktopI18n.t("缩小", "Zoom out"), onClick = onZoomOut)
        TopBarIcon(Icons.Outlined.ZoomIn, com.venera.desktop.settings.DesktopI18n.t("放大", "Zoom in"), onClick = onZoomIn)
        TopBarDivider()
        TopBarIcon(
            Icons.Outlined.Search,
            com.venera.desktop.settings.DesktopI18n.t("放大镜", "Magnifier"),
            active = magnifierOn,
            onClick = onToggleMagnifier,
        )
        TopBarIcon(Icons.Outlined.Filter, com.venera.desktop.settings.DesktopI18n.t("跳页", "Jump to page"), onClick = onOpenJumpDialog)
        TopBarDivider()
        if (showPageNumber) {
            TopBarText(
                if (totalPages > 0) "${currentPage + 1} / $totalPages" else "-",
                color = TopBarTextSecondary,
                width = 64.dp,
            )
            TopBarDivider()
        }
        TopBarIcon(Icons.Outlined.MenuBook, com.venera.desktop.settings.DesktopI18n.t("电子书", "Book"), active = settingsOpen && settingsTab == 0, onClick = { onOpenSettingsTab(0) })
        TopBarIcon(Icons.Outlined.AutoAwesome, com.venera.desktop.settings.DesktopI18n.t("页", "Page"), active = settingsOpen && settingsTab == 1, onClick = { onOpenSettingsTab(1) })
        TopBarIcon(Icons.Outlined.Palette, com.venera.desktop.settings.DesktopI18n.t("筛选", "Filter"), active = settingsOpen && settingsTab == 2, onClick = { onOpenSettingsTab(2) })
        TopBarIcon(Icons.Outlined.Search, "AI", active = settingsOpen && settingsTab == 3, onClick = { onOpenSettingsTab(3) })
        Box {
            TopBarIcon(Icons.Outlined.MoreVert, com.venera.desktop.settings.DesktopI18n.t("更多", "More"), active = moreMenuOpen, onClick = { moreMenuOpen = true })
            DropdownMenu(
                expanded = moreMenuOpen,
                onDismissRequest = { moreMenuOpen = false },
                modifier = Modifier.background(TopBarSurface),
                containerColor = Color(0xFF262B33),
            ) {
                DropdownMenuItem(
                    text = { TopBarMenuLabel(if (reverseReading) com.venera.desktop.settings.DesktopI18n.t("倒序 开", "Reverse: On") else com.venera.desktop.settings.DesktopI18n.t("倒序 关", "Reverse: Off")) },
                    onClick = {
                        onToggleReverse()
                        moreMenuOpen = false
                    },
                )
                DropdownMenuItem(
                    text = { TopBarMenuLabel(com.venera.desktop.settings.DesktopI18n.t("自动翻页 $autoPlaySecs 秒", "Auto-turn ${autoPlaySecs}s")) },
                    onClick = {
                        onCycleAutoPlay()
                        moreMenuOpen = false
                    },
                )
                DropdownMenuItem(
                    text = { TopBarMenuLabel(if (showPageNumber) com.venera.desktop.settings.DesktopI18n.t("页码 开", "Page #: On") else com.venera.desktop.settings.DesktopI18n.t("页码 关", "Page #: Off")) },
                    onClick = {
                        onToggleShowPageNumber()
                        moreMenuOpen = false
                    },
                )
                DropdownMenuItem(
                    text = { TopBarMenuLabel(com.venera.desktop.settings.DesktopI18n.t("隐藏顶栏", "Hide top bar")) },
                    onClick = {
                        onHideTopBar()
                        moreMenuOpen = false
                    },
                )
            }
        }
    }
}

@Composable
private fun RowScope.TopBarDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 3.dp)
            .width(1.dp)
            .fillMaxHeight()
            .padding(vertical = 12.dp)
            .background(TopBarDividerColor),
    )
}

@Composable
private fun TopBarText(
    text: String,
    color: Color = TopBarTextPrimary,
    width: androidx.compose.ui.unit.Dp = 168.dp,
) {
    Text(
        text = text,
        fontSize = VeneraType.body.size,
        fontWeight = FontWeight.Medium,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 6.dp).width(width),
    )
}

/** 「1:1」文本钮：图标库里没有合适矢量，直接文字承担。 */
@Composable
private fun TopBarTextUnit(text: String, contentDescription: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = VeneraType.micro.size,
            fontWeight = FontWeight.SemiBold,
            color = TopBarTextPrimary,
        )
    }
}

@Composable
private fun TopBarMenuLabel(text: String) {
    Text(text = text, fontSize = VeneraType.aux.size, color = TopBarTextPrimary)
}

@Composable
private fun TopBarIcon(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val tokens = VeneraTokens
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(if (active) tokens.color.primary.copy(alpha = 0.25f) else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = when {
                !enabled -> TopBarTextSecondary.copy(alpha = 0.35f)
                active -> tokens.color.primary
                else -> TopBarTextPrimary
            },
            modifier = Modifier.size(16.dp),
        )
    }
}

/** 顶部工具栏固定高度；画布从该高度以下起排。 */
internal val TOP_BAR_HEIGHT = 44.dp
