package com.venera.desktop.explore

import com.venera.compose.components.venera.hScrollEndFade
import com.venera.desktop.gallery.ui.GalleryTokens
import com.venera.desktop.gallery.ui.selection.LocalSelectionDockHazeState
import com.venera.desktop.ui.theme.VeneraType
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.components.LocalWindowWidth
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.compose.components.imageWallColumnCount
import com.venera.compose.components.imageWallHorizontalPadding
import com.venera.compose.components.venera.VeneraCard
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.components.venera.VeneraCoverMask
import com.venera.compose.ui.tokens.VeneraCardEnter
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.ui.DesktopActionButton
import com.venera.desktop.ui.DesktopPageWidth
import com.venera.desktop.ui.DesktopFilterChip
import com.venera.desktop.ui.GridPageTemplate
import com.venera.desktop.ui.desktopPageMaxWidth
import com.venera.engineprobe.EngineSession
import com.venera.desktop.ui.macCardShadow
import com.venera.desktop.ui.components.desktopCoverFloat
import com.venera.desktop.ui.components.desktopUiFloat
import com.venera.desktop.gallery.ui.LocalChromePalette
import com.venera.desktop.gallery.ui.selection.DockActionPill
import com.venera.desktop.settings.DesktopI18n

/**
 * 桌面探索屏 —— 对齐安卓 `UnifiedExploreScreen`（第二次重做）。
 *
 * 「探索页面应该是和安卓一样有这些分类选项的，每个源都不一样，目前电脑端还是没有…
 * 去掉更多按钮」。
 *
 * ## 本轮
 *
 *  - **分类面板真接上**：源 JS 的 `category.parts`（排行榜 / 每週必看 / 成人A漫 …）由
 *    `EngineSession.categoryBoard()` 现读，逐组摆选块；点一枚走
 *    `loadCategory`（category 型）或 `loadRanking`（排行榜档），结果原地展示，「返回探索」回默认流。
 *    每颗源的分组与条目都不同（禁漫 vs Picacg vs 拷贝），桌面一个都不写死。
 * - **溢出菜单整颗删掉**：它的九枚落点全在侧栏
 *    （下载与本地 / 阅读历史 / 阅读统计 / 源管理 / 备份与还原…），不再在屏头重复一遍。
 *  - 卡片与收藏/搜索同一卡座形；「选块」语言与其余屏一致。
 */
@Composable
internal fun DesktopExploreScreen(
    mode: WideScreenLayoutMode,
    /** 当前源名字，顶栏显示用。 */
    sourceKey: String,
    /** 可挑的源（key + 显示名，壳从源清单现算）。空表 ⇒ 源选块那一排整颗不摆。 */
    sourceChoices: List<ExploreSourceChoice> = emptyList(),
    onPickSource: ((String?) -> Unit)? = null,
    /** 这一颗源声明的探索方式名（壳现读；空/一枚时那一排不摆）。 */
    modes: List<String> = emptyList(),
    selectedMode: Int = 0,
    onPickMode: (Int) -> Unit = {},
    /** 分类面板分组（源 JS `category.parts`；空表 = 这一源没有分类面板）。 */
    categoryParts: List<EngineSession.CategoryPart> = emptyList(),
    /** 排行榜档位（源没声明 `enableRankingPage` 时为空表）。 */
    rankingChips: List<Pair<String, String>> = emptyList(),
    /** 点一枚分类 chip（`partName` 用于选中态与标题；kind = "category" / "search"）。 */
    onPickCategory: (partName: String, label: String, param: String?, kind: String) -> Unit = { _, _, _, _ -> },
    /** 点一枚排行榜档位（值是源的档位 key）。 */
    onPickRanking: (String) -> Unit = {},
    /** 当前正在看的那一格分类（`null` = 看默认探索流）。 */
    pickedLabel: String? = null,
    /** 分类结果那一页（`pickedLabel != null` 时才有意义；`null` = 正在取）。 */
    pickedPage: EngineSession.Page? = null,
    pickedLoadingMore: Boolean = false,
    pickedHasMore: Boolean = false,
    onLoadMorePicked: () -> Unit = {},
    /** 回默认探索流。 */
    onClearPicked: () -> Unit = {},
    /** 各分区的漫画；空 + note 为空 = 还在读。 */
    sections: List<EngineSession.Section>,
    /** 读取中 / 失败成因。非空照原话上屏。 */
    note: String?,
    /** 打码判定：给一张卡，说要不要遮。判据在外面（`UxPlatformServices`），这里只问。 */
    maskedOf: (EngineSession.ComicCard) -> Boolean,
    onOpen: (EngineSession.ComicCard) -> Unit,
    /**
     * 批量收进书架。null = 这一侧没有写口 ⇒ 整屏不进入多选（不摆勾选钮与坞）。
     * 只补缺失项由装配点负责，屏只交选中的卡。
     */
    onAddFavorites: ((List<EngineSession.ComicCard>) -> Unit)?,
    /** 探索卡右键按当前源搜标签（装配点绑当前源键）。 */
    onSearchComicTag: ((String) -> Unit)? = null,
    onReload: () -> Unit,
    /** 以下九枚**屏上不再摆**（溢出菜单已删，落点统一在侧栏）；保留形参只为既有
     *  装配/判据台架不必整体重写。 */
    onOpenSourceManage: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenDownloads: (() -> Unit)?,
    onOpenLocalComics: (() -> Unit)?,
    onOpenHistory: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenSettings: () -> Unit,
    /** 某一枚卡在本机那一棵树上的那一句标记。`null` = 那一角什么都不摆。 */
    offlineBadgeOf: (EngineSession.ComicCard) -> String?,
    /**
     * 自画大标题（discover-split-）：独立进探索页（EXPLORE tab / 取证种子）时
     * `true`（旧形状一字不变）；被选择器页 [com.venera.desktop.search.DesktopDiscoverScreen]
     * 收编时 `false` —— 大标题由选择器页统一画，本屏页头只留 SOURCE 徽标 + 读数 + 重新取。
     */
    showOwnTitle: Boolean = true,
    /** 分类网格滚动态：壳层递入则跨 Tab/路由切换存活；不递（收编进选择器页）就自持。 */
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState? = null,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    val maxWidth = desktopPageMaxWidth(DesktopPageWidth.Browse, mode)
    // 卡上的站标胶囊念显示名；源清单没递进来（空表）时如实回落到源键。
    val sourceLabel = sourceChoices.firstOrNull { it.key == sourceKey }?.name?.ifBlank { null } ?: sourceKey
    val addFavorites = onAddFavorites
    // 多选账住屏内：探索卡的号只在当前源里唯一，键带封面上避免同名不同本。
    val selectedKeys = androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateListOf<String>()
    }
    val selectedSet = selectedKeys.toSet()
    fun cardKey(card: EngineSession.ComicCard): String = "${card.id}|${card.cover}"
    fun toggleCard(card: EngineSession.ComicCard) {
        val key = cardKey(card)
        if (key in selectedKeys) selectedKeys.remove(key) else selectedKeys.add(key)
    }
    val allCards: List<EngineSession.ComicCard> = if (pickedLabel != null) {
        pickedPage?.comics.orEmpty()
    } else {
        sections.flatMap { it.comics }
    }
    // 换源 / 切分类：旧选择不属于眼前这批卡，清掉，避免坞里计数对不上。
    androidx.compose.runtime.LaunchedEffect(sourceKey, pickedLabel) {
        selectedKeys.clear()
    }

    val dockHazeState = rememberHazeState()
    androidx.compose.runtime.CompositionLocalProvider(LocalSelectionDockHazeState provides dockHazeState) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .then(if (maxWidth != null) Modifier.width(maxWidth) else Modifier)
                .fillMaxSize()
                .hazeSource(dockHazeState, zIndex = 0f)
                .padding(horizontal = tokens.spacing.screenHorizontal),
        ) {
            val totalItems = sections.sumOf { it.comics.size }
            // ── 页头（macOS 稿）──
            // 独立进屏（EXPLORE 取证种子）时保留原双行页头；被选择器页收编时标题与副题由上层统一画，
            // 这里只留右端「重新取」一行 —— 否则同一屏会出现两遍「探索」。
            if (showOwnTitle) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = tokens.spacing.space5),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space3),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SOURCE · $sourceKey",
                            fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                            fontWeight = VeneraType.cardTitle.weight,
                            color = tokens.color.primary,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = DesktopI18n.t("探索", "Explore"),
                                fontSize = VeneraType.statNumber.size, lineHeight = VeneraType.statNumber.lineHeight,
                                fontWeight = tokens.type.weightBold,
                                color = tokens.color.textPrimary,
                            )
                            Spacer(Modifier.width(tokens.spacing.space2))
                            Text(
                                text = "EXPLORE",
                                fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                                color = tokens.color.textTertiary,
                            )
                        }
                        Text(
                            text = DesktopI18n.t(
                                "$totalItems 本作品 · ${sections.size} 个分区 · ${if (note == null) "已接入" else "有提示"}",
                                "$totalItems comics · ${sections.size} sections · ${if (note == null) "Connected" else "Notice"}",
                            ),
                            fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                            color = tokens.color.textTertiary,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    DesktopActionButton(text = DesktopI18n.t("重新取", "Reload"), onClick = onReload)
                }
                Spacer(Modifier.height(tokens.spacing.space3))
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    DesktopActionButton(text = DesktopI18n.t("重新取", "Reload"), onClick = onReload)
                }
            }

            // ── 控制甲板：探索源 + 探索方式 + 分类面板（都是源自己声明的）──
            if ((sourceChoices.isNotEmpty() && onPickSource != null) || modes.size > 1 ||
                categoryParts.isNotEmpty() || rankingChips.isNotEmpty()
            ) {
                // 严格还原设计稿：控制面板是**白卡 + 微影**（截图里是灰底大卡），
                // 三组内容（探索源 / 探索方式 / 排行榜 / 分类）一字不删。
                val deckShape = RoundedCornerShape(10.dp)
                val deckPalette = LocalChromePalette.current
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(deckShape)
                        .background(deckPalette.elevated)
                        .macCardShadow(deckShape)
                        .padding(14.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (sourceChoices.isNotEmpty() && onPickSource != null) {
                            SelectorRow(label = DesktopI18n.t("探索源", "Source")) {
                                sourceChoices.forEach { choice ->
                                    DesktopFilterChip(
                                        text = choice.name,
                                        selected = choice.key == sourceKey,
                                        onClick = { if (choice.key != sourceKey) onPickSource(choice.key) },
                                    )
                                }
                            }
                        }
                        if (modes.size > 1) {
                            SelectorRow(label = DesktopI18n.t("探索方式", "Mode")) {
                                modes.forEachIndexed { index, name ->
                                    DesktopFilterChip(
                                        text = name,
                                        selected = index == selectedMode,
                                        onClick = { if (index != selectedMode) onPickMode(index) },
                                    )
                                }
                            }
                        }
                        if (rankingChips.isNotEmpty()) {
                            SelectorRow(label = DesktopI18n.t("排行榜", "Rankings")) {
                                rankingChips.forEach { (key, label) ->
                                    DesktopFilterChip(
                                        text = label,
                                        selected = pickedLabel == "排行榜:$label",
                                        onClick = { onPickRanking(key) },
                                    )
                                }
                            }
                        }
                        categoryParts.forEach { part ->
                            SelectorRow(label = part.name.ifBlank { DesktopI18n.t("分类", "Category") }) {
                                part.chips.forEach { chip ->
                                    DesktopFilterChip(
                                        text = chip.label,
                                        selected = pickedLabel == "${part.name}:${chip.label}",
                                        onClick = { onPickCategory(part.name, chip.label, chip.param, chip.kind) },
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(tokens.spacing.space3))
            }

            if (note != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = tokens.spacing.space3)
                        .clip(RoundedCornerShape(tokens.shape.medium))
                        .background(tokens.color.errorContainer.copy(alpha = 0.56f))
                        .padding(horizontal = tokens.spacing.space3, vertical = tokens.spacing.space2),
                ) {
                    Text(
                        text = note,
                        fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                        color = tokens.color.onErrorContainer,
                    )
                }
            }

            // ── 分类结果区 / 默认探索流 ──
            if (pickedLabel != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = tokens.spacing.space2),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = DesktopI18n.t("分类 · ", "Category · ") + pickedLabel.substringAfterLast(':'),
                        fontSize = VeneraType.body.size, lineHeight = VeneraType.body.lineHeight,
                        fontWeight = VeneraType.cardTitle.weight,
                        color = tokens.color.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    DesktopActionButton(text = DesktopI18n.t("返回探索", "Back to Explore"), onClick = onClearPicked)
                }
                CategoryResultGrid(
                    page = pickedPage,
                    sourceKey = sourceKey,
                    sourceLabel = sourceLabel,
                    maskedOf = maskedOf,
                    offlineBadgeOf = offlineBadgeOf,
                    onOpen = onOpen,
                    onSearchComicTag = onSearchComicTag,
                    loadingMore = pickedLoadingMore,
                    hasMore = pickedHasMore,
                    onLoadMore = onLoadMorePicked,
                    gridState = gridState,
                    selectedKeys = selectedSet,
                    onToggleSelect = if (addFavorites != null) ({ card -> toggleCard(card) }) else null,
                )
                return@Column
            }

            if (sections.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (note == null) DesktopI18n.t("正在从「$sourceKey」取探索页…", "Loading explore page from '$sourceKey'…") else "",
                        fontSize = VeneraType.body.size, lineHeight = VeneraType.body.lineHeight,
                        color = tokens.color.textTertiary,
                    )
                }
                return@Column
            }

            val windowWidth = LocalWindowWidth.current ?: 1280.dp
            val cardLayout by com.venera.desktop.settings.DesktopComicCardLayout.config.collectAsState()
            val columns = if (cardLayout.isCustom) {
                cardLayout.resolveGridColumns(windowWidth)
            } else {
                when {
                    windowWidth < 1000.dp -> 5
                    windowWidth < 1200.dp -> 6
                    else -> 7
                }
            }
            val coverAspect = cardLayout.resolveCoverAspect(0.72f)
            val gridItems = sections.flatMapIndexed { index, section ->
                section.comics.map { card ->
                    ExploreGridItem(
                        sectionIndex = index,
                        sectionTitle = section.title,
                        sectionCount = section.comics.size,
                        card = card,
                    )
                }
            }
            GridPageTemplate(
                items = gridItems,
                columns = columns,
                itemKey = { item -> "${item.sectionIndex}:${item.card.id}:${item.card.cover}" },
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = tokens.spacing.space6),
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space3),
                verticalItemSpacing = tokens.spacing.space3,
                groupKey = { it.sectionIndex },
                groupHeader = { item ->
                    SectionHeader(title = item.sectionTitle, count = item.sectionCount)
                },
            ) { item ->
                com.venera.desktop.ui.components.DesktopComicCardMenuArea(
                    actions = com.venera.desktop.ui.components.DesktopComicCardActions(
                        sourceKey = sourceKey,
                        onOpenDetail = { onOpen(item.card) },
                        onSearchInSource = onSearchComicTag,
                        onCopyTitle = {
                            val sel = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                            sel.setContents(java.awt.datatransfer.StringSelection(item.card.title), null)
                        },
                    ),
                    tag = item.card.tags.firstOrNull() ?: item.card.title,
                ) {
                ComicCell(
                    card = item.card,
                    masked = maskedOf(item.card),
                    badge = offlineBadgeOf(item.card),
                    sourceLabel = sourceLabel,
                    coverAspect = coverAspect,
                    onOpen = { onOpen(item.card) },
                    selected = cardKey(item.card) in selectedSet,
                    onToggleSelect = if (addFavorites != null) ({ toggleCard(item.card) }) else null,
                )
                }
            }
        }
        if (addFavorites != null) {
            com.venera.desktop.gallery.ui.selection.DesktopSelectionDock(
                selectedCount = selectedSet.size,
                totalCount = allCards.size,
                onSelectAll = {
                    selectedKeys.clear()
                    selectedKeys.addAll(allCards.map { cardKey(it) })
                },
                onInvertSelect = {
                    val all = allCards.map { cardKey(it) }
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
                    text = DesktopI18n.t("收藏 (${selectedSet.size})", "Favorite (${selectedSet.size})"),
                    primary = true,
                    onClick = {
                        val picked = allCards.filter { cardKey(it) in selectedSet }
                        selectedKeys.clear()
                        addFavorites(picked)
                    },
                )
            }
        }
    }
    }
}

/** 一行「标签 + 横向滚动选块」（探索源 / 探索方式 / 每个分类组共用）。 */
@Composable
private fun SelectorRow(label: String, content: @Composable () -> Unit) {
    val tokens = VeneraTokens
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = label,
            fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
            fontWeight = VeneraType.cardTitle.weight,
            color = tokens.color.textTertiary,
            maxLines = 1,
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()).hScrollEndFade(VeneraTokens.color.background),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content()
        }
    }
}

/** 分类结果网格（无限下滑自动续页 + 右下角回顶钮；主人口径撤「加载更多」钮）。 */
@Composable
private fun CategoryResultGrid(
    page: EngineSession.Page?,
    sourceKey: String,
    /** 当前源显示名（分类结果的站标胶囊文案）。 */
    sourceLabel: String,
    maskedOf: (EngineSession.ComicCard) -> Boolean,
    offlineBadgeOf: (EngineSession.ComicCard) -> String?,
    onOpen: (EngineSession.ComicCard) -> Unit,
    onSearchComicTag: ((String) -> Unit)?,
    loadingMore: Boolean,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    /** 分类网格滚动态：壳层递入则跨 Tab/路由切换存活；不递就自持。 */
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState? = null,
    selectedKeys: Set<String> = emptySet(),
    onToggleSelect: ((EngineSession.ComicCard) -> Unit)? = null,
) {
    val tokens = VeneraTokens
    when {
        page == null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(DesktopI18n.t("正在取这一分类…", "Loading category…"), fontSize = VeneraType.body.size, lineHeight = VeneraType.body.lineHeight, color = tokens.color.textTertiary)
        }

        page.error != null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(DesktopI18n.t("取分类失败：${page.error}", "Failed to load category: ${page.error}"), fontSize = VeneraType.body.size, lineHeight = VeneraType.body.lineHeight, color = tokens.color.textTertiary)
        }

        page.comics.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(DesktopI18n.t("这一分类没有返回作品。", "No comics returned in this category."), fontSize = VeneraType.body.size, lineHeight = VeneraType.body.lineHeight, color = tokens.color.textTertiary)
        }

        else -> {
            // 近尾自动扳同一颗 onLoadMore（「正在加载更多…」读数照摆，不重复触发由壳相位挡）。
            val grid = gridState ?: androidx.compose.foundation.lazy.grid.rememberLazyGridState()
            com.venera.desktop.ui.DesktopGridAutoLoadEffect(
                state = grid,
                columns = 4,
                enabled = hasMore && !loadingMore,
                onLoadMore = onLoadMore,
            )
            val cardLayout by com.venera.desktop.settings.DesktopComicCardLayout.config.collectAsState()
            val coverAspect = cardLayout.resolveCoverAspect(0.72f)
            androidx.compose.foundation.layout.BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val columnsCount = cardLayout.resolveGridColumns(this.maxWidth, 150.dp, 3, 6)
                LazyVerticalGrid(
                    state = grid,
                    columns = GridCells.Fixed(columnsCount),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = tokens.spacing.space6),
                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space3),
                    verticalArrangement = Arrangement.spacedBy(tokens.spacing.space3),
                ) {
                    itemsIndexed(page.comics, key = { _, card -> card.id + card.cover }) { index, card ->
                        VeneraCardEnter(index, card.id + card.cover) {
                            com.venera.desktop.ui.components.DesktopComicCardMenuArea(
                                actions = com.venera.desktop.ui.components.DesktopComicCardActions(
                                    sourceKey = sourceKey,
                                    onOpenDetail = { onOpen(card) },
                                    onSearchInSource = onSearchComicTag,
                                    onCopyTitle = {
                                        val sel = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                                        sel.setContents(java.awt.datatransfer.StringSelection(card.title), null)
                                    },
                                ),
                                tag = card.tags.firstOrNull() ?: card.title,
                            ) {
                                ComicCell(
                                    card = card,
                                    masked = maskedOf(card),
                                    badge = offlineBadgeOf(card),
                                    sourceLabel = sourceLabel,
                                    coverAspect = coverAspect,
                                    onOpen = { onOpen(card) },
                                    selected = "${card.id}|${card.cover}" in selectedKeys,
                                    onToggleSelect = onToggleSelect?.let { toggle -> { toggle(card) } },
                                )
                            }
                        }
                    }
                    if (loadingMore) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = DesktopI18n.t("正在加载更多…", "Loading more…"),
                                    fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                                    color = tokens.color.textTertiary,
                                )
                            }
                        }
                    }
                }
                com.venera.desktop.ui.DesktopBackToTopFab(
                    state = grid,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                )
            }
        }
    }
}

private data class ExploreGridItem(
    val sectionIndex: Int,
    val sectionTitle: String,
    val sectionCount: Int,
    val card: EngineSession.ComicCard,
)

@Composable
private fun SectionHeader(title: String, count: Int) {
    val tokens = VeneraTokens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = tokens.spacing.space4, bottom = tokens.spacing.space1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(24.dp)
                .clip(RoundedCornerShape(50))
                .background(tokens.color.primary),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title.ifBlank { DesktopI18n.t("未命名分区", "Untitled Section") },
                fontSize = VeneraType.sectionTitle.size, lineHeight = VeneraType.sectionTitle.lineHeight,
                fontWeight = VeneraType.cardTitle.weight,
                color = tokens.color.textPrimary,
            )
            Text(
                text = DesktopI18n.t("继续浏览这一组", "Browse this section"),
                fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                color = tokens.color.textTertiary,
            )
        }
        Text(
            text = DesktopI18n.t("$count 本", "$count comics"),
            fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
            fontWeight = VeneraType.cardTitle.weight,
            color = tokens.color.textSecondary,
        )
    }
}

/**
 * 探索卡：与收藏/搜索同一颗卡座形（封面齐边 + 信息底座 + 固定 36dp 双行标题槽）。
 *
 * 立体浮动（环境光 / 追光边 / 底托 / 视差 / 站标唤醒）全部走公共浮卡框，
 * 本卡只保留自己的内容与点击；标题槽仍是固定 36dp，悬停不动测量。
 *
 * @param sourceLabel 当前源的显示名，摆在标题槽右上角，悬停时点亮。
 */
@Composable
private fun ComicCell(
    card: EngineSession.ComicCard,
    masked: Boolean,
    /** 本机那一棵树算好的那一句（`null` = 本机没有整话 ⇒ 那一角不摆）。 */
    badge: String?,
    sourceLabel: String,
    coverAspect: Float = 0.72f,
    onOpen: () -> Unit,
    selected: Boolean = false,
    onToggleSelect: (() -> Unit)? = null,
) {
    val tokens = VeneraTokens
    // 严格还原设计稿：探索卡与搜索卡同一卡座 —— 白卡 + 微影，去 1dp 描边。
    val palette = LocalChromePalette.current

    com.venera.desktop.ui.components.DesktopFloatingCard(
        cornerRadius = 12.dp,
        surface = palette.elevated,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
    ) { floatState ->
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(coverAspect),
            ) {
                VeneraCover(
                    url = card.cover,
                    contentDescription = card.title,
                    mask = if (masked) VeneraCoverMask.Masked else VeneraCoverMask.Visible,
                    // 外层已定死 0.72 框；自持 3:4 会溢出绘制压到框外（卡片同因同修）。
                    preserveAspectRatio = false,
                    modifier = Modifier
                        .fillMaxSize()
                        .desktopCoverFloat(floatState),
                )
                if (badge != null) {
                    Text(
                        text = badge,
                        fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                        fontWeight = VeneraType.control.weight,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .desktopUiFloat(floatState)
                            .clip(GalleryTokens.Shape.Pill)
                            .background(com.venera.desktop.gallery.ui.GalleryTokens.Color.OverlayScrim)
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp),
                    contentAlignment = Alignment.TopStart,
                ) {
                    Text(
                        text = card.title,
                        fontSize = VeneraType.aux.size,
                        fontWeight = VeneraType.cardTitle.weight,
                        color = tokens.color.textPrimary,
                        maxLines = 2,
                        lineHeight = 18.sp,
                        overflow = TextOverflow.Ellipsis,
                        // 给站标胶囊让出右端，胶囊压在槽内不额外占高。
                        modifier = Modifier.fillMaxWidth().padding(end = 46.dp),
                    )
                    com.venera.desktop.ui.components.DesktopCardSourcePill(
                        text = sourceLabel,
                        highlighted = floatState.isHovered,
                        modifier = Modifier.align(Alignment.TopEnd),
                    )
                }
                val subtitle = card.author.ifBlank {
                    card.tags.take(2).joinToString(" · ").ifBlank { "" }
                }
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                        color = tokens.color.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** 源下拉的一格（key + 显示名；壳从源清单现算，屏不查）。 */
internal data class ExploreSourceChoice(val key: String, val name: String)
