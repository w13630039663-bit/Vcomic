package com.venera.desktop.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryColumnMode
import com.venera.compose.gallery.domain.GalleryWallLayout
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.gallery.data.DESKTOP_BROWSE_PENDING_READING
import com.venera.desktop.gallery.data.DesktopBrowseView
import com.venera.desktop.gallery.data.desktopBrowseHeaderReading
import com.venera.desktop.gallery.data.desktopBrowseRowLine
import com.venera.desktop.gallery.ui.selection.DesktopSelectionDock
import com.venera.desktop.gallery.ui.selection.DesktopSelectionDockBottomInset
import com.venera.desktop.gallery.ui.selection.DockActionPill
import com.venera.desktop.gallery.ui.selection.DockIconAction
import com.venera.desktop.gallery.ui.selection.LocalSelectionDockHazeState
import com.venera.desktop.ui.DesktopGlobalNavAction
import com.venera.desktop.ui.DesktopListShortcutAction
import com.venera.desktop.ui.DesktopModifierState
import com.venera.desktop.ui.DesktopPageShell
import com.venera.desktop.ui.DesktopPageWidth
import com.venera.desktop.ui.RegisterDesktopShortcutHandler
import com.venera.desktop.ui.components.DesktopMenuEntry
import com.venera.desktop.ui.computeShiftRangeSelection
import com.venera.desktop.ui.matchGlobalNavShortcut
import com.venera.desktop.ui.matchListShortcut
import com.venera.desktop.ui.theme.VeneraType
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 合并页的两个视图。两份账各读各的档，合并只发生在这一层。 */
internal enum class DesktopGallerySavedView(val label: String) {
    FAVORITES("收藏"),
    HISTORY("浏览历史"),
}

/** 日期分区（仅历史视图）。 */
private enum class SavedBucket(val label: String) {
    TODAY("今天"),
    YESTERDAY("昨天"),
    EARLIER("更早"),
}

/**
 * 分站计数：站轴照传进来的全集走，没条目的站记 0。
 *
 * 分类行要「跟随后台全部站源」—— 轴上每一站都列出来，不因当前视图没有那站的条目而消失：
 * 两个视图各摆一种分类行，用户会以为筛选项本身在变。
 *
 * @param axis 全部已接站（调用点传 `GallerySite.entries`，顺序即枚举自然序）
 * @param sites 当前视图里每条数据所属的站，重复出现几次就算几条
 * @return 与 [axis] 同序、同长的「站 → 条数」表
 */
internal fun desktopGallerySiteCats(
    axis: List<GallerySite>,
    sites: List<GallerySite>,
): List<Pair<GallerySite, Int>> {
    val counts = sites.groupingBy { it }.eachCount()
    return axis.map { site -> site to (counts[site] ?: 0) }
}

/** 收藏卡属于哪一站：先认原模型，再退回 `uid` 的站键前缀；两路都认不出时给 null（不猜）。 */
internal fun desktopSavedCardSiteOf(card: DesktopWallCardModel): GallerySite? =
    card.sourcePost?.site ?: GallerySite.fromRouteKey(card.uid.substringBefore(':'))

/**
 * 画廊的「收藏与历史」一页两视图。
 *
 * 屏只吃成品：收藏卡已过守卫起口，历史行已按看过的时刻排好；读写档、清空、取消收藏全在壳。
 *
 * @param view 当前视图；收藏与历史各一份成品数据
 * @param axis 站点分类轴（全部站源），分类行永远按它列全
 * @param favoriteTotal 存档里的收藏总数（表头念总数，不念筛完的张数）
 * @param favoriteBlocked 守卫挡下的张数；>0 时并进表头那一句
 * @param historyLoaded 历史档读回来没有；`false` 与「没有记录」是两句话，不许合并
 * @param clearText 清空把手的文案（收藏 / 记录），第一次按下变「确认清空 N 条」
 * @param clearCount 清空会把多少条一起删掉，为空时不摆把手
 * @param onClear 第二次按下才调；武装状态由本屏自己管
 * @param onOpenFavorite 打开一张收藏
 * @param onRemoveFavorite 取消收藏（写侧在壳，屏只发指令）
 * @param onOpenHistory 回原卡（壳里现取整张帖）
 * @param onRemoveHistory 删掉这一条历史记录
 */
@Composable
internal fun DesktopGallerySavedScreen(
    mode: WideScreenLayoutMode,
    view: DesktopGallerySavedView,
    onPickView: (DesktopGallerySavedView) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    siteKey: String?,
    onPickSite: (String?) -> Unit,
    axis: List<GallerySite>,
    favorites: List<DesktopWallCardModel>,
    favoriteTotal: Int,
    favoriteBlocked: Int,
    history: List<DesktopBrowseView>,
    historyLoaded: Boolean,
    clearText: String,
    clearCount: Int,
    onClear: () -> Unit,
    clearNote: String?,
    note: String?,
    onOpenFavorite: (DesktopWallCardModel) -> Unit,
    onRemoveFavorite: (DesktopWallCardModel) -> Unit,
    onOpenHistory: (DesktopBrowseView) -> Unit,
    onRemoveHistory: (DesktopBrowseView) -> Unit,
    onRemoveFavorites: ((List<DesktopWallCardModel>) -> Unit)?,
    onRemoveHistories: ((List<DesktopBrowseView>) -> Unit)?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    var clearArmed by remember(view) { mutableStateOf(false) }

    val favoriteSites = favorites.mapNotNull { desktopSavedCardSiteOf(it) }
    val historySites = history.map { it.site }
    // 分类行跟随后台全部站源：轴上每一站都摆，没条目的记 0，不因当前视图没数据而消失。
    val cats = desktopGallerySiteCats(axis, if (view == DesktopGallerySavedView.FAVORITES) favoriteSites else historySites)

    val filteredFavorites = remember(favorites, siteKey, query) {
        favorites.filter { card ->
            (siteKey == null || desktopSavedCardSiteOf(card)?.routeKey == siteKey) &&
                (query.isBlank() || listOf(card.title, card.author, card.tags).any { it.contains(query, ignoreCase = true) })
        }
    }
    val filteredHistory = remember(history, historyLoaded, siteKey, query) {
        if (!historyLoaded) emptyList()
        else history.filter { row ->
            (siteKey == null || row.site.routeKey == siteKey) &&
                (query.isBlank() || desktopBrowseRowLine(row).contains(query, ignoreCase = true))
        }
    }

    fun favKey(card: DesktopWallCardModel): String = card.uid.ifBlank { card.url }
    fun histKey(row: DesktopBrowseView): String = "${row.site.routeKey}:${row.postId}"

    val visibleKeys = remember(view, filteredFavorites, filteredHistory) {
        when (view) {
            DesktopGallerySavedView.FAVORITES -> filteredFavorites.map { favKey(it) }
            DesktopGallerySavedView.HISTORY -> filteredHistory.map { histKey(it) }
        }
    }
    val selectedKeys = remember { mutableStateListOf<String>() }
    val selectedSet = selectedKeys.toSet()
    var lastAnchorKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(view) {
        selectedKeys.clear()
        lastAnchorKey = null
    }
    LaunchedEffect(visibleKeys) {
        val valid = visibleKeys.toHashSet()
        selectedKeys.removeAll { it !in valid }
    }

    fun toggleKey(key: String) {
        if (DesktopModifierState.isShiftDown && lastAnchorKey != null) {
            val next = computeShiftRangeSelection(
                orderedKeys = visibleKeys,
                anchorKey = lastAnchorKey,
                targetKey = key,
                currentSelected = selectedKeys.toSet(),
            )
            selectedKeys.clear()
            selectedKeys.addAll(next)
        } else {
            if (key in selectedKeys) selectedKeys.remove(key) else selectedKeys.add(key)
        }
        lastAnchorKey = key
    }

    val selectedFavCards = remember(view, filteredFavorites, selectedSet) {
        if (view == DesktopGallerySavedView.FAVORITES) {
            filteredFavorites.filter { favKey(it) in selectedSet }
        } else {
            emptyList()
        }
    }
    val selectedHistoryRows = remember(view, filteredHistory, selectedSet) {
        if (view == DesktopGallerySavedView.HISTORY) {
            filteredHistory.filter { histKey(it) in selectedSet }
        } else {
            emptyList()
        }
    }

    fun removeSelectedItems() {
        when (view) {
            DesktopGallerySavedView.FAVORITES -> if (selectedFavCards.isNotEmpty()) {
                val targets = selectedFavCards.toList()
                selectedKeys.clear()
                if (onRemoveFavorites != null) onRemoveFavorites(targets) else targets.forEach(onRemoveFavorite)
            }
            DesktopGallerySavedView.HISTORY -> if (selectedHistoryRows.isNotEmpty()) {
                val targets = selectedHistoryRows.toList()
                selectedKeys.clear()
                if (onRemoveHistories != null) onRemoveHistories(targets) else targets.forEach(onRemoveHistory)
            }
        }
    }

    RegisterDesktopShortcutHandler(visibleKeys.size, selectedSet.size, view, priority = 100) { signal ->
        when (matchListShortcut(signal)) {
            DesktopListShortcutAction.SELECT_ALL -> {
                if (visibleKeys.isEmpty()) return@RegisterDesktopShortcutHandler false
                selectedKeys.clear()
                selectedKeys.addAll(visibleKeys)
                true
            }
            DesktopListShortcutAction.DELETE_SELECTED -> {
                if (selectedSet.isEmpty()) return@RegisterDesktopShortcutHandler false
                removeSelectedItems()
                true
            }
            else -> {
                if (matchGlobalNavShortcut(signal) == DesktopGlobalNavAction.ESCAPE &&
                    selectedSet.isNotEmpty()
                ) {
                    selectedKeys.clear()
                    true
                } else {
                    false
                }
            }
        }
    }

    DesktopPageShell(
        mode = mode,
        title = "收藏与历史",
        width = DesktopPageWidth.Full,
        edgeToEdgeX = true,
        // 页头标题裸摆，不包卡片底。
        titleCard = false,
        subtitle = when (view) {
            DesktopGallerySavedView.FAVORITES -> if (favoriteBlocked > 0) {
                "共 $favoriteTotal 张 · 已挡下 $favoriteBlocked 张"
            } else {
                "共 $favoriteTotal 张"
            }
            DesktopGallerySavedView.HISTORY -> if (historyLoaded) {
                desktopBrowseHeaderReading(history)
            } else {
                DESKTOP_BROWSE_PENDING_READING
            }
        },
        note = note,
        onBack = onBack,
        modifier = modifier,
    ) {
        val dockHazeState = rememberHazeState()
        CompositionLocalProvider(LocalSelectionDockHazeState provides dockHazeState) {
        Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().hazeSource(dockHazeState, zIndex = 0f)) {
            ViewBar(
                view = view,
                onPickView = { picked ->
                    clearArmed = false
                    onPickView(picked)
                },
                favoritesTotal = favoriteTotal,
                historyTotal = history.size,
            )
            Toolbar(
                query = query,
                onQueryChange = onQueryChange,
                cats = cats,
                siteKey = siteKey,
                onPickSite = onPickSite,
                clearText = clearText,
                clearCount = clearCount,
                clearArmed = clearArmed,
                onClearClick = {
                    if (clearArmed) {
                        clearArmed = false
                        onClear()
                    } else {
                        clearArmed = true
                    }
                },
            )
            if (clearNote != null) {
                Text(
                    text = clearNote,
                    fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                    color = tokens.color.textTertiary,
                    modifier = Modifier.padding(bottom = tokens.spacing.space2),
                )
            }
            Spacer(Modifier.height(tokens.spacing.space3))
            when (view) {
                DesktopGallerySavedView.FAVORITES -> FavoritesWall(
                    filtered = filteredFavorites,
                    query = query,
                    selectedSet = selectedSet,
                    hasSelection = selectedSet.isNotEmpty(),
                    onToggleSelect = { toggleKey(favKey(it)) },
                    onOpen = onOpenFavorite,
                    onRemove = onRemoveFavorite,
                )
                DesktopGallerySavedView.HISTORY -> HistoryWall(
                    rows = history,
                    filtered = filteredHistory,
                    loaded = historyLoaded,
                    query = query,
                    selectedSet = selectedSet,
                    hasSelection = selectedSet.isNotEmpty(),
                    onToggleSelect = { toggleKey(histKey(it)) },
                    onOpen = onOpenHistory,
                    onRemove = onRemoveHistory,
                )
            }
        }
        DesktopSelectionDock(
            selectedCount = selectedSet.size,
            totalCount = visibleKeys.size,
            onSelectAll = {
                selectedKeys.clear()
                selectedKeys.addAll(visibleKeys)
            },
            onInvertSelect = {
                selectedKeys.clear()
                selectedKeys.addAll(visibleKeys.filter { it !in selectedSet })
            },
            onClearSelect = { selectedKeys.clear() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = DesktopSelectionDockBottomInset),
        ) {
            val removeLabel = if (view == DesktopGallerySavedView.FAVORITES) {
                "取消收藏 (${selectedSet.size})"
            } else {
                "删除记录 (${selectedSet.size})"
            }
            DockActionPill(
                text = removeLabel,
                danger = true,
                onClick = { removeSelectedItems() },
            )
            DockIconAction(
                icon = Icons.Outlined.ContentCopy,
                description = "复制所选链接",
                onClick = {
                    val links = when (view) {
                        DesktopGallerySavedView.FAVORITES -> selectedFavCards.mapNotNull { card ->
                            card.sourcePost?.let { it.site.pageUrlPrefix + it.id } ?: card.url.takeIf { it.isNotBlank() }
                        }
                        DesktopGallerySavedView.HISTORY -> selectedHistoryRows.map { row ->
                            row.site.pageUrlPrefix + row.postId
                        }
                    }
                    if (links.isNotEmpty()) copyText(links.joinToString("\n"))
                },
            )
        }
        }
        }
    }
}

@Composable
private fun ViewBar(
    view: DesktopGallerySavedView,
    onPickView: (DesktopGallerySavedView) -> Unit,
    favoritesTotal: Int,
    historyTotal: Int,
) {
    val tokens = VeneraTokens
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = tokens.spacing.space2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space3),
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(tokens.color.surfaceContainerHigh)
                .border(1.dp, tokens.color.outlineVariant, RoundedCornerShape(8.dp))
                .padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            DesktopGallerySavedView.entries.forEach { entry ->
                val on = entry == view
                Text(
                    text = entry.label,
                    fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (on) tokens.color.onPrimary else tokens.color.textSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (on) tokens.color.primary else Color.Transparent)
                        .clickable { onPickView(entry) }
                        .padding(horizontal = 14.dp, vertical = 5.dp),
                )
            }
        }
        Text(
            text = "收藏 $favoritesTotal 张 · 浏览记录 $historyTotal 条",
            fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
            color = tokens.color.textTertiary,
        )
    }
}

@Composable
private fun Toolbar(
    query: String,
    onQueryChange: (String) -> Unit,
    cats: List<Pair<GallerySite, Int>>,
    siteKey: String?,
    onPickSite: (String?) -> Unit,
    clearText: String,
    clearCount: Int,
    clearArmed: Boolean,
    onClearClick: () -> Unit,
) {
    val tokens = VeneraTokens
    Row(
        modifier = Modifier.fillMaxWidth().height(36.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space1),
    ) {
        com.venera.desktop.ui.components.DesktopSearchField(
            value = query,
            onValueChange = onQueryChange,
            onSearch = {},
            modifier = Modifier.width(240.dp),
            placeholder = "搜标签 / 画师",
        )
        Spacer(Modifier.width(tokens.spacing.space2))
        Text(
            text = "站点",
            fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
            color = tokens.color.textTertiary,
        )
        SitePill(label = "全部", count = cats.sumOf { it.second }, on = siteKey == null) { onPickSite(null) }
        cats.forEach { (site, count) ->
            SitePill(label = site.displayName, count = count, on = siteKey == site.routeKey) {
                onPickSite(site.routeKey)
            }
        }
        Spacer(Modifier.weight(1f))
        if (clearCount > 0) {
            Text(
                text = if (clearArmed) "确认清空 $clearCount 条" else clearText,
                fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                fontWeight = if (clearArmed) FontWeight.SemiBold else FontWeight.Normal,
                color = if (clearArmed) tokens.color.onPrimary else Color(0xFFE5484D),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (clearArmed) Color(0xFFDC2626) else Color.Transparent)
                    .clickable { onClearClick() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun SitePill(label: String, count: Int, on: Boolean, onClick: () -> Unit) {
    val tokens = VeneraTokens
    Text(
        text = "$label $count",
        fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
        color = if (on) tokens.color.onPrimary else tokens.color.textSecondary,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (on) tokens.color.primary else tokens.color.surfaceContainerHigh)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/** 一格成品卡片数据（两视图共用，交给唯一卡渲染）。 */
private class SavedEntry(
    val key: String,
    val card: DesktopWallCardModel,
    val pageUrl: String?,
    val removeLabel: String,
    val selected: Boolean,
    val onToggleSelect: () -> Unit,
    val onOpen: () -> Unit,
    val onRemove: () -> Unit,
)

/**
 * 作品墙：布局与列距跟图库同一档 [LocalDesktopWallSpec]，切等宽 / 瀑布流这一屏跟着变。
 *
 * @param sections 分组；收藏一个无名组，历史按日期三组
 * @param showHeads 是否摆组头（组头整行铺满，不吃列宽）
 */
@Composable
private fun SavedWall(
    sections: List<Pair<String, List<SavedEntry>>>,
    showHeads: Boolean,
    hasSelection: Boolean,
) {
    val tokens = VeneraTokens
    val spec = LocalDesktopWallSpec.current
    val gap = spec.wallGap
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // 列数档位按物理像素判：用 dp 会在高缩放下永远少一列（与主墙同一条口径）。
        val wallWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { maxWidth.toPx() }
        val columns = spec.columnsFor(wallWidthPx, GalleryColumnMode.AUTO)
        val contentPadding = PaddingValues(bottom = if (hasSelection) 88.dp else tokens.spacing.space6)
        if (spec.layout == GalleryWallLayout.Staggered) {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(columns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding,
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalItemSpacing = gap,
            ) {
                sections.forEach { (label, entries) ->
                    if (showHeads) {
                        item(key = "head-$label", span = StaggeredGridItemSpan.FullLine) {
                            SectionHead(label, entries.size)
                        }
                    }
                    items(entries.size, key = { idx -> entries[idx].key }) { idx ->
                        SavedTile(entries[idx], index = idx)
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding,
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                sections.forEach { (label, entries) ->
                    if (showHeads) {
                        item(key = "head-$label", span = { GridItemSpan(maxLineSpan) }) {
                            SectionHead(label, entries.size)
                        }
                    }
                    items(entries.size, key = { idx -> entries[idx].key }) { idx ->
                        SavedTile(entries[idx], index = idx)
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoritesWall(
    filtered: List<DesktopWallCardModel>,
    query: String,
    selectedSet: Set<String>,
    hasSelection: Boolean,
    onToggleSelect: (DesktopWallCardModel) -> Unit,
    onOpen: (DesktopWallCardModel) -> Unit,
    onRemove: (DesktopWallCardModel) -> Unit,
) {
    if (filtered.isEmpty()) {
        CenterNote(if (query.isBlank()) "还没有收藏" else "没有匹配的收藏")
        return
    }
    SavedWall(
        sections = listOf(
            "" to filtered.map { card ->
                val key = card.uid.ifBlank { card.url }
                SavedEntry(
                    key = key,
                    card = card,
                    pageUrl = card.sourcePost?.let { it.site.pageUrlPrefix + it.id },
                    removeLabel = "取消收藏",
                    selected = key in selectedSet,
                    onToggleSelect = { onToggleSelect(card) },
                    onOpen = { onOpen(card) },
                    onRemove = { onRemove(card) },
                )
            },
        ),
        showHeads = false,
        hasSelection = hasSelection,
    )
}

@Composable
private fun HistoryWall(
    rows: List<DesktopBrowseView>,
    filtered: List<DesktopBrowseView>,
    loaded: Boolean,
    query: String,
    selectedSet: Set<String>,
    hasSelection: Boolean,
    onToggleSelect: (DesktopBrowseView) -> Unit,
    onOpen: (DesktopBrowseView) -> Unit,
    onRemove: (DesktopBrowseView) -> Unit,
) {
    if (!loaded) {
        CenterNote(DESKTOP_BROWSE_PENDING_READING)
        return
    }
    if (rows.isEmpty()) {
        CenterNote("还没有浏览记录")
        return
    }
    val today = LocalDate.now()
    if (filtered.isEmpty()) {
        CenterNote(if (query.isBlank()) "还没有浏览记录" else "没有匹配的记录")
        return
    }
    val grouped = filtered.groupBy { row ->
        val day = savedDayOf(row.viewedAt)
        when {
            day == today -> SavedBucket.TODAY
            day == today.minusDays(1) -> SavedBucket.YESTERDAY
            else -> SavedBucket.EARLIER
        }
    }
    val sections = SavedBucket.entries.mapNotNull { bucket ->
        val bucketRows = grouped[bucket].orEmpty()
        if (bucketRows.isEmpty()) return@mapNotNull null
        bucket.label to bucketRows.map { row ->
            val desc = desktopBrowseRowLine(row)
            val uid = "${row.site.routeKey}:${row.postId}"
            SavedEntry(
                key = uid,
                card = DesktopWallCardModel(
                    title = desc,
                    url = row.thumbUrl,
                    aspect = 1f,
                    masked = false,
                    uid = uid,
                ),
                pageUrl = row.site.pageUrlPrefix + row.postId,
                removeLabel = "删除这条记录",
                selected = uid in selectedSet,
                onToggleSelect = { onToggleSelect(row) },
                onOpen = { onOpen(row) },
                onRemove = { onRemove(row) },
            )
        }
    }
    SavedWall(sections = sections, showHeads = true, hasSelection = hasSelection)
}

/**
 * 复用桌面唯一画廊浮动画框卡片（关闭底部信息栏），保留右键移除入口。
 *
 * @param entry 这一格的渲染模型与打开/移除动作。
 * @param index 在分组墙上的序号，用于入场阶梯动效。
 */
@Composable
private fun SavedTile(entry: SavedEntry, index: Int = 0) {
    val uri = LocalUriHandler.current
    val searchHandoff = LocalDesktopSearchHandoff.current
    val spec = LocalDesktopWallSpec.current
    val aspect = when {
        spec.layout == GalleryWallLayout.Staggered -> entry.card.aspect
        else -> spec.rows?.let(com.venera.desktop.settings.DesktopGridDensity::rowsToRatio)
            ?: spec.coverAspect.ratio
            ?: if (desktopGridCardIsWide(entry.card)) 2f else 1f
    }
    val extraMenu = buildList {
        entry.pageUrl?.let { url ->
            add(
                DesktopMenuEntry.Item(
                    label = "复制作品链接",
                    icon = Icons.Outlined.Link,
                    onClick = { copyText(url) },
                ),
            )
            add(
                DesktopMenuEntry.Item(
                    label = "在站方打开",
                    icon = Icons.Outlined.Language,
                    onClick = { uri.openUri(url) },
                ),
            )
        }
        add(
            DesktopMenuEntry.Item(
                label = entry.removeLabel,
                icon = Icons.Outlined.Delete,
                isDanger = true,
                onClick = entry.onRemove,
            ),
        )
    }
    DesktopGalleryCard(
        card = entry.card,
        aspect = aspect,
        index = index,
        onOpenPost = { entry.onOpen() },
        onSearchArtist = { label ->
            searchHandoff(
                listOf(entry.card.sourcePost?.site ?: GallerySite.YANDERE),
                label,
            )
        },
        onOpenArtist = null,
        selected = entry.selected,
        onToggleSelect = entry.onToggleSelect,
        showInfo = false,
        extraMenuItems = extraMenu,
    )
}

@Composable
private fun SectionHead(label: String, count: Int) {
    val tokens = VeneraTokens
    Row(
        modifier = Modifier.fillMaxWidth().height(28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(13.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(tokens.color.primary),
        )
        Text(
            text = label,
            fontSize = VeneraType.cardTitle.size, lineHeight = VeneraType.cardTitle.lineHeight,
            fontWeight = FontWeight.SemiBold,
            color = tokens.color.textPrimary,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "$count 张",
            fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
            color = tokens.color.textTertiary,
        )
    }
}

@Composable
private fun CenterNote(text: String) {
    val tokens = VeneraTokens
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            fontSize = VeneraType.body.size, lineHeight = VeneraType.body.lineHeight,
            color = tokens.color.textTertiary,
        )
    }
}

/** 复制一段文本到系统剪贴板（右键菜单那两枚）。 */
private fun copyText(text: String) {
    if (text.isBlank()) return
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
}

private fun savedDayOf(at: Long): LocalDate =
    Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate()
