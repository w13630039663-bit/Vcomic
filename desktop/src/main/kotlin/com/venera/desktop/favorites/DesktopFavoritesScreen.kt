package com.venera.desktop.favorites

import com.venera.desktop.gallery.ui.selection.LocalSelectionDockHazeState
import com.venera.desktop.ui.theme.VeneraType
import com.venera.desktop.ui.vShadow
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import com.venera.compose.components.venera.hScrollEndFade
import com.venera.desktop.gallery.ui.GalleryTokens
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.desktop.ui.DesktopPageWidth
import com.venera.desktop.ui.desktopPageMaxWidth
import com.venera.desktop.ui.components.desktopCoverFloat
import com.venera.desktop.ui.components.desktopUiFloat
import com.venera.compose.components.venera.VeneraButton
import com.venera.compose.components.venera.VeneraCard
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.components.venera.VeneraCoverMask
import com.venera.compose.components.venera.VeneraTextButton
import com.venera.compose.data.db.FavoriteRecord
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.gallery.data.DESKTOP_FAVORITE_DEFAULT_FOLDER
import com.venera.desktop.gallery.data.DesktopFavoriteFolderTab
import com.venera.desktop.gallery.data.desktopFavoriteFolderLine
import com.venera.desktop.gallery.ui.selection.DockActionPill
import com.venera.desktop.settings.DesktopI18n

/**
 * 桌面收藏屏 —— 对应安卓 `FavoritesScreen`（1337 行）。
 *
 * ## 与安卓的差别（写明）
 *
 * 安卓那 1337 行有文件夹分组、排序、多选、批量移出、更新角标、下拉刷新。
 * 这一批做：网格铺开 + 点开详情 + 移出收藏 + 多选批量移出（悬停勾选，底部悬浮坞）。
 * 没做：更新检测（`hasUpdate` 只显示角标，不做检测）；批量下载与文件夹移动没有写口，坞里不摆。
 *
 * ## 书库式版面
 *
 * 顶上一道「最近收藏」封面细轨（只在「全部 + 按收藏时间」时摆），下面一条夹索引，再下面一面书架。
 * 夹索引那一排是选夹的唯一入口（也是读侧筛选的唯一出处，筛在壳里）。
 * 按夹分区的书架没做：判据在测试视口里数屏上的格子（三夹三格必须同时上屏），纵向分区会把
 * 第三段挤出可视区；改成不懒加载又会拖垮大库。吸顶页眉随分区一并搁置。
 *
 * ## 那一本在**哪一夹**现在看得见
 *
 * 从前每一格只念题名与源名，而 `folderName` 那一列在桌面上恒走默认值 ⇒ 屏上没什么可念。
 * 详情那一屏接上那一排选择器之后，用户能把一本收到别的夹里，
 * 而这一屏**看不出改了没有** —— 那就等于这一格只是把库改了个没人看得见的地方。
 * 所以这一格补的是**显示**（不是分组）：那一行念的是这条记录自己带的夹名。
 *
 * 为什么直接吃 `fav.folderName` 而不新加形参：那块数据本来就在传进来的那一份 `FavoriteRecord` 里，
 * 再加一枚形参就是"同一件事有两个出处"（与 `DesktopMaskJudgementSingleSourceTest` 对
 * 「屏自己再判一遍」同一族）。屏仍然不碰库。
 *
 * ## 空态为什么要分两句
 *
 * `favorites` 为空有两种成因：「还没读回来」与「库读回来了但确实没有收藏」。
 * 合成一句就等于把"库还没开"说成"你没收过藏" —— 本仓反复记过的那类假完成。
 *
 * ## 那一排「只看哪一夹」
 *
 * 上一批之后用户**改得动**夹却**看不了**某一夹 —— 那一列的价值只兑现了一半。这一批补的是筛选：
 * 顶部一排把手（「全部」+ 逐夹，各带条数），下面那面墙只摆挑中那一夹的本子。
 *
 * 四格全是**成品**（判据全在 `gallery/data/DesktopFavoriteFolders.kt`，屏只搬，与 `maskedOf` 和
 * 「夹：X」那一行同一条口径）：`folderTabs` 是那一排的渲染输入，`shownFavorites` 是筛完那一批，
 * `countNote` 是表头右侧那一句，`filterNote` 是筛空那句。屏**不查库也不自己筛** ——
 * 在这里写一遍 `filter { it.folderName == … }` 就是同一件事两个出处。
 *
 * ⚠️ 空态分**三档**：「正在读本地收藏…」／「这一格里还没有本子」／「还没有收藏」。
 * 中间那一档是这一批新增的：筛中一夹而那一夹没本子时，那一本明明收藏着，
 * 沿用最后一句就是假话（而它会劝用户去"再收一遍"）。
 *
 * 那一排**恒摆**，不按"有没有第二夹"摆：整排随数据出现/消失会让人刚把最后一本移出「追更」
 * 就再也回不到那一档。
 */
@Composable
internal fun DesktopFavoritesScreen(
    mode: WideScreenLayoutMode,
    /** 筛完要摆上屏的那一批（算式在 `desktopFavoriteFoldersShown`，屏不自己筛）。 */
    shownFavorites: List<FavoriteRecord>,
    /** 顶部那一排把手（成品，算式在 `desktopFavoriteFolderTabRows`）。 */
    folderTabs: List<DesktopFavoriteFolderTab>,
    /** 当前挑中的那一夹（`null` = 「全部」）；只为把选块画成选中态，判据仍在壳里。 */
    selectedFolder: String? = null,
    /** 表头右侧那一句条数（成品，算式在 `desktopFavoriteFolderCountNote`；空串 = 整格不念）。 */
    countNote: String,
    /** `false` = 还没读回来。与"读了但确实没有"要分开说。 */
    loaded: Boolean,
    /** 读失败成因。非空照原话上屏。 */
    note: String?,
    /** 挑中一夹而那一格里没本子时那句（成品，`null` = 念原来那两档空态）。 */
    filterNote: String?,
    /** 挑某一夹（`null` = 回「全部」）。⚠️ 必传、无默认值：带 `{}` 就是"那一排摆着而按不动"（评审 R6 那一族）。 */
    onFolderTabSelect: (String?) -> Unit,
    maskedOf: (FavoriteRecord) -> Boolean,
    onOpen: (FavoriteRecord) -> Unit,
    onRemove: (FavoriteRecord) -> Unit,
    /** 右键「加入追更」（只登记账本）；null = 装配点没接，菜单不摆这一项。 */
    onAddFollow: ((FavoriteRecord) -> Unit)? = null,
    /** 右键按每条收藏自己的源搜标签（源键 + 词）。null = 收藏卡不摆搜标签项。 */
    onSearchComicTag: ((sourceKey: String, tag: String) -> Unit)? = null,
    /**
     * 本地**可自行新建 / 删除夹**。
     * 两枚都 nullable：null = 对应把手不摆（判据台架不传就不出现，不放假把手）。
     * 写侧全在装配点（LocalFavoritesManager），屏零库调用。
     */
    onCreateFolder: ((String) -> Unit)? = null,
    onDeleteFolder: ((String) -> Unit)? = null,
    /** 是否自己画「收藏」标题行。直接测试/独立使用默认 true；收藏 hub 用统一页壳时传 false。 */
    showHeader: Boolean = true,
    /** 是否已经被 `DesktopPageShell` 约束过宽度与左右内边距；hub 内传 true，避免双层缩进。 */
    embedded: Boolean = false,
    /**
     * 排序档：0 = 收藏时间（库读回原序）/ 1 = 标题。
     * 由装配点从 `favoriteSortOrder` 派生并与设置页共用同一个值，因此跨会话记忆。
     */
    sortMode: Int = 0,
    /** 改排序档（写回 `favoriteSortOrder`）。 */
    onPickSortMode: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    val palette = com.venera.desktop.gallery.ui.LocalChromePalette.current
    // 排序（macOS 皮批 · 做真排序）：0 = 收藏时间（库序）/ 1 = 标题。
    // 档位由设置页与屏内那颗下拉共用同一枚偏好，改哪边另一处立刻同步。
    var showNewFolderDialog by remember { androidx.compose.runtime.mutableStateOf(false) }
    var viewMode by rememberSaveable { mutableStateOf(0) } // 0 = 网格, 1 = 双列
    val cardLayout by com.venera.desktop.settings.DesktopComicCardLayout.config.collectAsState()
    val maxWidth = desktopPageMaxWidth(DesktopPageWidth.Browse, mode)
    val sortedFavorites = if (sortMode == 1) {
        shownFavorites.sortedBy { it.title }
    } else {
        shownFavorites
    }
    // 本地书架的批量移除：选择账住屏内，键 = 源 + 号（号可跨源重复）。
    val selectedKeys = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    val selectedSet = selectedKeys.toSet()
    var lastAnchorKey by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    fun favKey(fav: FavoriteRecord): String = "${fav.sourceName}|${fav.comicId}"
    fun toggleFav(fav: FavoriteRecord) {
        val key = favKey(fav)
        if (com.venera.desktop.ui.DesktopModifierState.isShiftDown && lastAnchorKey != null) {
            val ordered = sortedFavorites.map { favKey(it) }
            val next = com.venera.desktop.ui.computeShiftRangeSelection(
                orderedKeys = ordered,
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
    val keyedFavorites = remember(sortedFavorites) { sortedFavorites.associateBy { favKey(it) } }

    com.venera.desktop.ui.RegisterDesktopShortcutHandler(
        sortedFavorites.size,
        selectedSet.size,
        priority = 100,
    ) { signal ->
        when (com.venera.desktop.ui.matchListShortcut(signal)) {
            com.venera.desktop.ui.DesktopListShortcutAction.SELECT_ALL -> {
                if (sortedFavorites.isEmpty()) return@RegisterDesktopShortcutHandler false
                selectedKeys.clear()
                selectedKeys.addAll(sortedFavorites.map { favKey(it) })
                true
            }
            com.venera.desktop.ui.DesktopListShortcutAction.DELETE_SELECTED -> {
                if (selectedSet.isEmpty()) return@RegisterDesktopShortcutHandler false
                val targets = selectedSet.mapNotNull { keyedFavorites[it] }
                selectedKeys.clear()
                targets.forEach { onRemove(it) }
                true
            }
            else -> {
                if (com.venera.desktop.ui.matchGlobalNavShortcut(signal) ==
                    com.venera.desktop.ui.DesktopGlobalNavAction.ESCAPE && selectedSet.isNotEmpty()
                ) {
                    selectedKeys.clear()
                    true
                } else {
                    false
                }
            }
        }
    }

    val dockHazeState = rememberHazeState()
    androidx.compose.runtime.CompositionLocalProvider(LocalSelectionDockHazeState provides dockHazeState) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .then(
                    if (!embedded && maxWidth != null) {
                        Modifier.widthIn(max = maxWidth)
                    } else {
                        Modifier
                    },
                )
                .fillMaxSize()
                .hazeSource(dockHazeState, zIndex = 0f)
                .then(
                    if (!embedded) Modifier.padding(horizontal = tokens.spacing.screenHorizontal) else Modifier,
                ),
        ) {
            if (showHeader) {
                // 书库页头：题名 + 排序口径与条数副题（不再用卡壳页头；标题不超过 6 字）。
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = DesktopI18n.t("收藏", "Favorites"),
                        fontSize = VeneraType.pageTitle.size, lineHeight = VeneraType.pageTitle.lineHeight,
                        fontWeight = VeneraType.pageTitle.weight,
                        color = tokens.color.textPrimary,
                    )
                    if (countNote.isNotBlank()) {
                        Text(
                            text = countNote,
                            fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                            color = tokens.color.textTertiary,
                            modifier = Modifier.weight(1f).padding(bottom = 4.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            } else if (countNote.isNotBlank() && selectedFolder != null) {
                // 嵌入 hub 时题名与总数由 hub 页头念（「共 N 本」），这里只在**筛过**时补一句
                // 「共 N 本中的 M 本」；全档再念一遍「N 本」就是同屏两句重数。
                Text(
                    text = countNote,
                    fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                    color = tokens.color.textTertiary,
                    modifier = Modifier.padding(bottom = 8.dp),
                    maxLines = 1,
                )
            }

            // ── 最近收藏细轨：只在「全部」且按收藏时间排时摆（别的档里它念的不是"最近"）。──
            if (selectedFolder == null && sortMode == 0 && sortedFavorites.isNotEmpty()) {
                RecentFavoritesRail(
                    favorites = sortedFavorites.take(10),
                    maskedOf = maskedOf,
                    onOpen = onOpen,
                )
            }

            // ── 那一排「只看哪一夹」：屏只搬那一排，不算夹名也不算条数 ──
            // 书库式夹索引：左侧固定标签，夹名按条数可横向滚动；右端排序与新建夹。
            // 删夹仍走 chip 右键菜单（删夹 = 成员移回默认，装配点管）。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = DesktopI18n.t("夹索引", "Folders"),
                    fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                    color = tokens.color.textTertiary,
                    maxLines = 1,
                )
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()).hScrollEndFade(VeneraTokens.color.background),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    folderTabs.forEach { tab ->
                        val deletable = tab.folder != null &&
                            tab.folder != DESKTOP_FAVORITE_DEFAULT_FOLDER &&
                            onDeleteFolder != null
                        val chip = @Composable {
                            com.venera.desktop.ui.DesktopFilterChip(
                                text = tab.label,
                                selected = tab.folder == selectedFolder,
                                onClick = { onFolderTabSelect(tab.folder) },
                            )
                        }
                        if (deletable) {
                            com.venera.desktop.ui.components.DesktopContextMenuArea(
                                items = {
                                    listOf(
                                        com.venera.desktop.ui.components.DesktopMenuEntry.Item(
                                            label = DesktopI18n.tr("删除夹", "Delete Folder"),
                                            isDanger = true,
                                            onClick = { onDeleteFolder?.invoke(tab.folder!!) },
                                        ),
                                    )
                                },
                            ) { chip() }
                        } else {
                            chip()
                        }
                    }
                }
                com.venera.desktop.ui.DesktopPopUpButton(
                    label = DesktopI18n.t("视图", "View"),
                    value = if (viewMode == 1) DesktopI18n.t("双列", "2-Col") else DesktopI18n.t("网格", "Grid"),
                    options = listOf(DesktopI18n.t("网格", "Grid"), DesktopI18n.t("双列", "2-Col")),
                    selectedIndex = viewMode,
                    onSelect = { viewMode = it },
                )
                com.venera.desktop.ui.DesktopPopUpButton(
                    label = DesktopI18n.t("排序", "Sort"),
                    value = if (sortMode == 1) DesktopI18n.t("标题", "Title") else DesktopI18n.t("收藏时间", "Date Added"),
                    options = listOf(DesktopI18n.t("收藏时间", "Date Added"), DesktopI18n.t("标题", "Title")),
                    selectedIndex = sortMode,
                    onSelect = { onPickSortMode(it) },
                )
                if (onCreateFolder != null) {
                    com.venera.desktop.ui.DesktopPushButton(
                        text = DesktopI18n.t("＋ 新建文件夹", "+ New Folder"),
                        onClick = { showNewFolderDialog = true },
                    )
                }
            }

            if (showNewFolderDialog && onCreateFolder != null) {
                NewFolderDialog(
                    onConfirm = { name ->
                        showNewFolderDialog = false
                        onCreateFolder(name)
                    },
                    onDismiss = { showNewFolderDialog = false },
                )
            }

            if (note != null && (sortedFavorites.isNotEmpty() || !loaded || filterNote != null)) {
                Text(
                    text = note,
                    fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                    color = tokens.color.textTertiary,
                    modifier = Modifier.padding(bottom = tokens.spacing.space2),
                )
            }

            when {
                // 一面墙：夹的选择由上一条夹索引交代，书架不再按夹切成多段。
                // 为什么不做纵向分区：判据在测试视口里数屏上的格子（三夹三格必须同时上屏），
                // 纵向分区会把第三段挤出可视区；改成不懒加载又会拖垮大库。
                sortedFavorites.isNotEmpty() -> BoxWithConstraints(Modifier.fillMaxSize()) {
                    val gridColumns = if (viewMode == 1) {
                        cardLayout.resolveDoubleColumns(2)
                    } else {
                        cardLayout.resolveGridColumns(this.maxWidth, 150.dp, 3, 6)
                    }
                    val coverAspect = cardLayout.resolveCoverAspect(0.75f)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(gridColumns),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = tokens.spacing.space6),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        itemsIndexed(sortedFavorites) { index, fav ->
                            // 浮动入场：每张卡按序号错峰淡入并上浮 16dp。
                            // 懒加载滚入的格子会重新播一次——正是「卡片浮上来」的观感来源。
                            val favIdentity = favKey(fav)
                            var appeared by remember(favIdentity) { mutableStateOf(false) }
                            LaunchedEffect(favIdentity) {
                                delay((index.coerceAtMost(14) * 32).toLong())
                                appeared = true
                            }
                            val enterAlpha by animateFloatAsState(
                                targetValue = if (appeared) 1f else 0f,
                                animationSpec = tween(durationMillis = 220),
                                label = "fav-enter-alpha",
                            )
                            val enterShift by animateFloatAsState(
                                targetValue = if (appeared) 0f else 16f,
                                animationSpec = tween(durationMillis = 260),
                                label = "fav-enter-shift",
                            )
                            Box(
                                modifier = Modifier.graphicsLayer {
                                    alpha = enterAlpha
                                    translationY = enterShift
                                },
                            ) {
                                FavoriteCell(
                                    fav = fav,
                                    masked = maskedOf(fav),
                                    doubleColumn = viewMode == 1,
                                    coverAspect = coverAspect,
                                    selected = favIdentity in selectedSet,
                                    onToggleSelect = { toggleFav(fav) },
                                    onOpen = { onOpen(fav) },
                                    onRemove = { onRemove(fav) },
                                    onAddFollow = onAddFollow?.let { add -> { add(fav) } },
                                    onSearchComicTag = onSearchComicTag,
                                )
                            }
                        }
                    }
                }

                // 筛中一夹而那一格里没本子：那一本明明收藏着，不许念成"还没有收藏"。
                filterNote != null -> CenterNote(filterNote)

                // 「还没读回来」与「读了确实没有」是两件事，屏上分开说。
                // 读的时候摆骨架而不是一句「正在读…」：位置与最终书架同形，读回来就直接落位。
                !loaded -> FavoriteSkeletonGrid()

                note != null -> CenterNote(note)

                else -> CenterNote(
                    DesktopI18n.t(
                        "还没有收藏。在探索页点开一本，详情页可以收藏。",
                        "No favorites yet. Open a comic in Explore to favorite it.",
                    ),
                )
            }
        }
        com.venera.desktop.gallery.ui.selection.DesktopSelectionDock(
            selectedCount = selectedSet.size,
            totalCount = sortedFavorites.size,
            onSelectAll = {
                selectedKeys.clear()
                selectedKeys.addAll(sortedFavorites.map { favKey(it) })
            },
            onInvertSelect = {
                val all = sortedFavorites.map { favKey(it) }
                selectedKeys.clear()
                selectedKeys.addAll(all.filter { it !in selectedSet })
            },
            onClearSelect = { selectedKeys.clear() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    bottom = com.venera.desktop.gallery.ui.selection.DesktopSelectionDockBottomInset,
                ),
        ) {
            DockActionPill(
                text = DesktopI18n.t("取消收藏 (${selectedSet.size})", "Unfavorite (${selectedSet.size})"),
                danger = true,
                onClick = {
                    val targets = selectedSet.mapNotNull { keyedFavorites[it] }
                    selectedKeys.clear()
                    targets.forEach { onRemove(it) }
                },
            )
        }
    }
    }
}

@Composable
private fun CenterNote(text: String) {
    // 统一空态：居中容器 + 玻璃卡由 DesktopEmptyState 提供，文案仍是逐字钉着的成品句。
    com.venera.desktop.ui.DesktopEmptyState(text = text)
}

/**
 * 最近收藏细轨（书库顶那条封面带）。
 *
 * 只吃屏上已经排好序的那一批；点封面等同于点那一本（`onOpen`），不另起入口。
 */
@Composable
private fun RecentFavoritesRail(
    favorites: List<FavoriteRecord>,
    maskedOf: (FavoriteRecord) -> Boolean,
    onOpen: (FavoriteRecord) -> Unit,
) {
    val tokens = VeneraTokens
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = DesktopI18n.t("最近收藏", "Recently Favorited"),
                fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                color = tokens.color.textTertiary,
                maxLines = 1,
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(tokens.color.textTertiary.copy(alpha = 0.16f)),
            )
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            favorites.forEach { fav ->
                Box(
                    modifier = Modifier
                        .width(56.dp)
                        .height(74.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(tokens.color.surfaceVariant)
                        .border(1.dp, tokens.color.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .clickable { onOpen(fav) },
                ) {
                    VeneraCover(
                        url = fav.coverUrl,
                        contentDescription = fav.title,
                        mask = if (maskedOf(fav)) VeneraCoverMask.Masked else VeneraCoverMask.Visible,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

/**
 * 读库期间的骨架书架：格数与最终书架同一口径（150dp 一列，钳 3~6 列），
 * 每格照实体书卡的三段比例摆封面块 + 题名两块，整体做 900ms 呼吸脉冲。
 *
 * 为什么不是一句「正在读…」：本地库大的时候那一句要挂一会儿，页面会跳一下；
 * 骨架先把位置占住，读回来直接换成真卡片，书架不位移。
 */
@Composable
private fun FavoriteSkeletonGrid() {
    val cardLayout by com.venera.desktop.settings.DesktopComicCardLayout.config.collectAsState()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val gridColumns = cardLayout.resolveGridColumns(this.maxWidth, 150.dp, 3, 6)
        val pulse by rememberInfiniteTransition(label = "fav-skeleton").animateFloat(
            initialValue = 0.26f,
            targetValue = 0.60f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "fav-skeleton-pulse",
        )
                val skeletonDesc = DesktopI18n.t("正在读本地收藏", "Loading local favorites")
                Column(
            modifier = Modifier
                .fillMaxSize()
                // 骨架替掉了原来那句「正在读本地收藏…」，语义上把它留在这里：
                // 读屏仍念得出这一页在干什么，测试也有据可依。
                .semantics { contentDescription = skeletonDesc },
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            repeat(3) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    repeat(gridColumns) {
                        FavoriteSkeletonCell(pulse = pulse, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteSkeletonCell(pulse: Float, modifier: Modifier = Modifier) {
    val tokens = VeneraTokens
    val block = tokens.color.surfaceContainerHigh.copy(alpha = pulse)
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(12.dp))
                .background(block),
        )
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth(0.82f).height(12.dp).clip(RoundedCornerShape(4.dp)).background(block))
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth(0.5f).height(10.dp).clip(RoundedCornerShape(4.dp)).background(block))
    }
}

@Composable
private fun FavoriteCell(
    fav: FavoriteRecord,
    masked: Boolean,
    doubleColumn: Boolean = false,
    coverAspect: Float = 0.75f,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    onSearchComicTag: ((sourceKey: String, tag: String) -> Unit)?,
    onAddFollow: (() -> Unit)? = null,
    selected: Boolean = false,
    onToggleSelect: (() -> Unit)? = null,
) {
    val tokens = VeneraTokens
    // 实体书卡：封面压 3:4 + 卡下信息区（题名两行 + 夹名一行的固定槽），
    // 源徽章与「移出」仍压在封面上，鼠标悬停只改描边与抬升。
    val favTag = fav.tags.split(Regex("[,; ]")).firstOrNull { it.isNotBlank() } ?: fav.title
    val favGate = onSearchComicTag
    val palette = com.venera.desktop.gallery.ui.LocalChromePalette.current
    com.venera.desktop.ui.components.DesktopComicCardMenuArea(
        actions = com.venera.desktop.ui.components.DesktopComicCardActions(
            sourceKey = fav.sourceName,
            onOpenDetail = onOpen,
            onRemoveFavorite = onRemove,
            onAddFollow = onAddFollow,
            onSearchInSource = favGate?.let { gate -> { tag: String -> gate(fav.sourceName, tag) } },
            onCopyTitle = {
                val sel = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                sel.setContents(java.awt.datatransfer.StringSelection(fav.title), null)
            },
        ),
        tag = favTag,
    ) {
    // 右键五口、打码、恒可用「移出」全部保留。卡面是书库式实体卡（底色 + 发丝描边 + 内距 7），
    // 封面左侧压一道书脊暗缝；「移出」文案逐字被判据钉着，不许换图标。
    com.venera.desktop.ui.components.DesktopFloatingCard(
        cornerRadius = 12.dp,
        surface = palette.card,
        tray = false,
        chromeAspect = null,
        liftDp = 4.dp,
        hoverElevation = 10.dp,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
    ) { floatState ->
        val chipEdge by androidx.compose.animation.animateColorAsState(
            targetValue = if (floatState.isHovered) tokens.color.primary.copy(alpha = 0.55f)
            else Color.Transparent,
            animationSpec = androidx.compose.animation.core.tween(200),
            label = "favSourceEdge",
        )
        if (doubleColumn) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.card)
                    .padding(9.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(82.dp)
                        .height(110.dp)
                        .clip(RoundedCornerShape(7.dp)),
                ) {
                    VeneraCover(
                        url = fav.coverUrl,
                        contentDescription = fav.title,
                        mask = if (masked) VeneraCoverMask.Masked else VeneraCoverMask.Visible,
                        modifier = Modifier.fillMaxSize().desktopCoverFloat(floatState),
                    )
                    if (onToggleSelect != null && (selected || floatState.isHovered)) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(5.dp)
                                .size(22.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(if (selected) tokens.color.primary else Color.Black.copy(alpha = 0.56f))
                                .border(
                                    1.5.dp,
                                    if (selected) tokens.color.primary else Color.White.copy(alpha = 0.70f),
                                    androidx.compose.foundation.shape.CircleShape,
                                )
                                .clickable(onClick = onToggleSelect),
                            contentAlignment = Alignment.Center,
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = if (selected) DesktopI18n.t("已选中", "Selected") else DesktopI18n.t("选择", "Select"),
                                tint = Color.White.copy(alpha = if (selected) 1f else 0.72f),
                                modifier = Modifier.size(13.dp),
                            )
                        }
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = fav.title,
                        fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                        fontWeight = VeneraType.cardTitle.weight,
                        color = tokens.color.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (fav.author.isNotBlank()) {
                        Text(
                            text = fav.author,
                            fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                            color = tokens.color.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${fav.sourceName} · ",
                            fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                            color = tokens.color.textTertiary,
                            maxLines = 1,
                        )
                        FavoriteFolderText(
                            folderName = fav.folderName,
                            color = tokens.color.textTertiary,
                        )
                    }
                }
            }
        } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.card)
                .padding(7.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(coverAspect)
                    .clip(RoundedCornerShape(7.dp)),
            ) {
                VeneraCover(
                    url = fav.coverUrl,
                    contentDescription = fav.title,
                    mask = if (masked) VeneraCoverMask.Masked else VeneraCoverMask.Visible,
                    modifier = Modifier
                        .fillMaxSize()
                        .desktopCoverFloat(floatState),
                )
                // 书脊暗缝：左沿 6dp 的白→黑窄渐变，让封面读成一册有厚度的书。
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .width(6.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.22f),
                                    Color.Black.copy(alpha = 0.25f),
                                    Color.Transparent,
                                ),
                            ),
                        ),
                )
                Text(
                    text = fav.sourceName,
                    fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .desktopUiFloat(floatState)
                        .background(palette.glassDark, RoundedCornerShape(4.dp))
                        .border(1.dp, chipEdge, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 1.5.dp),
                )
                if (onToggleSelect != null && (selected || floatState.isHovered)) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp)
                            .size(24.dp)
                            .desktopUiFloat(floatState)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(
                                if (selected) tokens.color.primary
                                else Color.Black.copy(alpha = 0.56f),
                            )
                            .border(
                                1.5.dp,
                                if (selected) tokens.color.primary else Color.White.copy(alpha = 0.70f),
                                androidx.compose.foundation.shape.CircleShape,
                            )
                            .clickable(onClick = onToggleSelect),
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = if (selected) DesktopI18n.t("已选中", "Selected") else DesktopI18n.t("选择", "Select"),
                            tint = Color.White.copy(alpha = if (selected) 1f else 0.72f),
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
            Text(
                text = fav.title,
                fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                fontWeight = VeneraType.cardTitle.weight,
                color = tokens.color.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .heightIn(min = 40.dp),
            )
            // 那一本在哪一夹。念的是这条记录**自己带的那一格**，屏不查库；
            // 「默认」那一格也照念而不省略 —— 一屏里有的念有的不念，用户就分不出"没夹"与"没读到"。
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(tokens.color.textTertiary),
                )
                FavoriteFolderText(
                    folderName = fav.folderName,
                    color = tokens.color.textTertiary,
                )
            }
        }
        }
    }
}
}

/** 收藏夹标签单源文案组件，保证双列与网格视图共用同一出口。 */
@Composable
private fun FavoriteFolderText(
    folderName: String,
    color: Color,
) {
    Text(
        text = desktopFavoriteFolderLine(folderName),
        fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * 新建夹对话框（macOS 皮批 · 「本地可自行添加文件夹」）。
 * 只收名字；判重 / 落库全在装配点（LocalFavoritesManager.createFolder 会抛重名，
 * 壳侧 runCatching 收成读数）。回车 = 确认，Esc = 取消。
 */
@Composable
private fun NewFolderDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = com.venera.desktop.gallery.ui.LocalChromePalette.current
    var name by remember { androidx.compose.runtime.mutableStateOf("") }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(min = 320.dp)
                .vShadow(8.dp, RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
                .background(palette.elevated)
                .border(0.5.dp, palette.hairline, RoundedCornerShape(10.dp))
                .padding(18.dp),
        ) {
            Text(
                text = DesktopI18n.t("新建文件夹", "New Folder"),
                fontSize = VeneraType.body.size, lineHeight = VeneraType.body.lineHeight,
                fontWeight = VeneraType.cardTitle.weight,
                color = palette.ink1,
            )
            androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))
            com.venera.desktop.ui.components.DesktopInputField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = DesktopI18n.t("输入文件夹名", "Folder name"),
            )
            androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                com.venera.desktop.ui.DesktopPushButton(text = DesktopI18n.t("取消", "Cancel"), onClick = onDismiss)
                com.venera.desktop.ui.DesktopPushButton(
                    text = DesktopI18n.t("创建", "Create"),
                    primary = true,
                    onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                )
            }
        }
    }
}

private val MacShapeInput = RoundedCornerShape(6.dp)
