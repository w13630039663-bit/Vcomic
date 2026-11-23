package com.venera.desktop.search

import com.venera.desktop.ui.vShadow
import com.venera.desktop.ui.theme.VeneraType
import com.venera.desktop.ui.DesktopPushButton
import com.venera.desktop.ui.components.DesktopSearchField
import com.venera.desktop.ui.components.desktopCoverFloat
import com.venera.desktop.ui.components.desktopUiFloat
import com.venera.desktop.gallery.ui.GalleryTokens
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.compose.components.venera.VeneraCard
import com.venera.compose.components.venera.VeneraChip
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.components.venera.VeneraCoverMask
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.settings.DesktopI18n
import com.venera.desktop.ui.DesktopPageWidth
import com.venera.desktop.ui.desktopPageMaxWidth
import com.venera.engineprobe.EngineSession
import com.venera.desktop.ui.macCardShadow

/** 源选择器里的一枚（键 + 显示名）。壳从源清单算好递进来，屏不读清单也不认键。 */
internal data class DesktopSearchSourceOption(val key: String, val label: String)

/** 高级筛选组模型（对齐安卓 SearchOptionGroup：排序/分类/Tag多选） */
internal data class DesktopSearchFilterGroup(
    val label: String,
    val type: String, // "dropdown" | "select" | "multi-select"
    val options: List<Pair<String, String>>, // key -> label
    val selectedKeys: Set<String> = emptySet(),
)

/**
 * 桌面搜索屏 —— 完全重做（严格对齐 desktop-search-overhaul-.html ）：
 * 1. 满宽超大沉浸式搜索条 + 显式「检索」按钮 + 回车即搜；
 * 2. 漫画源胶囊横排选择；
 * 3. 真实高级筛选控制面板（排序、分类、语言、多选标签，对齐安卓真实筛选能力）；
 * 4. 检索结果元数据行与横卡/竖卡视图切换；
 * 5. 宽屏大横卡结果流（左侧 96dp 黄金封面，右侧固定高度标题、作者、Mini 标签流、底部源名与评分），自适应三列；
 * 6. 页尾翻页四态（加载更多 / 正在加载 / 到底了 / 重试）完全保留并精确对账。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DesktopSearchScreen(
    mode: WideScreenLayoutMode,
    sourceKey: String,
    sourceLabel: String,
    sourceOptions: List<DesktopSearchSourceOption>,
    onPickSource: (String) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    results: List<EngineSession.ComicCard>,
    searching: Boolean,
    note: String?,
    maskedOf: (EngineSession.ComicCard) -> Boolean,
    onOpen: (EngineSession.ComicCard) -> Unit,
    historyChips: List<String>,
    onPickHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    /** 检索场下拉用的完整历史；与 [historyChips] 分开，结果页那排收起时下拉仍看得到。 */
    dropdownHistory: List<String> = historyChips,
    moreState: DesktopComicSearchMore,
    onLoadMore: () -> Unit,
    failedFooter: String?,
    emptyNote: String?,
    /**
     * 该源 `search.optionList` 摊平后的筛选组（壳现读；空表 = 这一源没有高级筛选）。
     * 主人「筛选和分类每个源都不一样的，你这个定死了」——这里**不写死任何一组**。
     */
    filterGroups: List<EngineSession.SearchOption> = emptyList(),
    /** 与 [filterGroups] 次序一一对应的当前值（多选 = JSON 串、单选 = 键、下拉未选 = null）。 */
    filterValues: List<String?> = emptyList(),
    /** 改某组的值（写完由壳记下，屏不持状态）。 */
    onFilterChange: (Int, String?) -> Unit = { _, _ -> },
    /** 「应用筛选」：按当前值重搜。 */
    onApplyFilters: () -> Unit = {},
    /** 「恢复源默认」：回该源 optionList 的默认值。 */
    onResetFilters: () -> Unit = {},
    /**
     * 自画大标题（discover-split-）：
     * 独立进搜索页时 `true`（旧形状一字不变）；被选择器页 [DesktopDiscoverScreen] 收编时
     * `false` —— 大标题由选择器页统一画，本屏从检索栏直接起，避免双标题。
     */
    showOwnTitle: Boolean = true,
    /**
     * 自画检索行（macOS 稿）：选择器页把检索场提到**顶部工具栏行**时传 false，
     * 内容区不再重复一条检索行（设计稿：检索场钉工具栏右，内容区从筛选行起）。
     */
    showSearchField: Boolean = true,
    /** 当前结果视图档位；由壳从「默认视图」偏好读出，屏不自己记。 */
    viewMode: DesktopSearchViewMode = DesktopSearchViewMode.Single,
    /** 切档：壳写回偏好并更新状态，屏只转发。 */
    onViewModeChange: (DesktopSearchViewMode) -> Unit = {},
    /** 点结果卡选中速览；null = 退回 [onOpen] 直接进详情页。 */
    onSelectCard: ((EngineSession.ComicCard) -> Unit)? = null,
    /** 当前速览选中的作品 id，用于左栏选中态；null = 未选中。 */
    selectedCardId: String? = null,
    /** 当前速览选中的作品归属源键，聚合模式下用于精确匹配高亮。 */
    selectedCardSourceKey: String? = null,
    /** 按作品 id 查已取到的更新日期；空串 = 该本还没取详情，不摆徽标。 */
    dateOf: (String) -> String = { "" },
    /** 速览栏整颗作为 slot 由壳组装，屏只负责摆左右两栏、不认详情数据。 */
    peekPane: (@Composable () -> Unit)? = null,
    /** 当前搜索目标（单源 / 自定义多源聚合）；默认保持单源以兼容旧调用点。 */
    searchTarget: DesktopSearchTarget = DesktopSearchTarget.Single(sourceKey),
    /** 多源勾选确认回调，交由壳落盘并触发聚合检索。 */
    onPickMultiSources: (Set<String>) -> Unit = {},
    /** 已按四态分层排好的聚合泳道列表。 */
    aggregateLanes: List<DesktopAggregateLaneState> = emptyList(),
    /** 聚合模式是否已发起过搜索。 */
    aggregateHasSearched: Boolean = false,
    /** 聚合模式「仅看有结果」开关状态。 */
    aggregateOnlyHits: Boolean = false,
    /** 切换聚合模式「仅看有结果」开关。 */
    onToggleAggregateOnlyHits: () -> Unit = {},
    /** 聚合模式下按每张卡片归属源键判定封面遮罩。 */
    aggregateMaskedOf: (String, EngineSession.ComicCard) -> Boolean = { _, card -> maskedOf(card) },
    /** 聚合泳道单击卡片：回传归属源键、源名与卡片，供右侧速览精准拉取。 */
    onSelectAggregateCard: ((String, String, EngineSession.ComicCard) -> Unit)? = null,
    /** 聚合泳道进完整详情页：携带卡片归属源键。 */
    onOpenAggregateDetail: (String, EngineSession.ComicCard) -> Unit = { _, card -> onOpen(card) },
    /** 聚合泳道右键按标签搜该源。 */
    onSearchTagInSource: (String, String) -> Unit = { _, tag -> onQueryChange(tag); onSearch() },
    /** 聚合泳道点击「查看全部 →」切入该单源深搜。 */
    onDrillDownSource: (String) -> Unit = onPickSource,
    /** 聚合泳道失败时单源重试。 */
    onRetryAggregateSource: (String) -> Unit = {},
    /** 全网聚合时自动跳过的未配置私服列表，供界面展示提示。 */
    aggregateSkippedSelfHosted: List<String> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    val pageMaxWidth = desktopPageMaxWidth(DesktopPageWidth.Full, mode)
    // 输入框聚焦 = 吉祥物「我在听」；聚焦下长时间无活动仍照计时打盹。
    var inputFocused by remember { mutableStateOf(false) }
    val mascotSpec = com.venera.desktop.settings.DesktopMascot.current.collectAsState().value
    // 风格对齐：白卡架与卡片吃同一颗 macOS 色板（elevated / ink / accent）。
    val palette = com.venera.desktop.gallery.ui.LocalChromePalette.current
    val isAggregate = searchTarget is DesktopSearchTarget.Aggregate

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .then(if (pageMaxWidth != null) Modifier.width(pageMaxWidth) else Modifier)
                .fillMaxSize()
                .padding(horizontal = tokens.spacing.screenHorizontal),
        ) {
            // ── 页头一行（独立进屏时自画；选择器页收编时由页头统一画）──
            if (showOwnTitle) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = tokens.spacing.space4, bottom = tokens.spacing.space2),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        text = DesktopI18n.t("搜索", "Search"),
                        fontSize = 16.sp, lineHeight = 24.sp,
                        fontWeight = tokens.type.weightBold,
                        color = tokens.color.textPrimary,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = DesktopI18n.t("${results.size} 本 · $sourceLabel", "${results.size} comics · $sourceLabel"),
                        fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                        color = tokens.color.textTertiary,
                        maxLines = 1,
                    )
                }
            }

            // ── 检索行（macOS 稿）：选择器页把检索场提到顶部工具栏行时整段不画 ──
            if (showSearchField) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        SearchField(
                            value = query,
                            onValueChange = onQueryChange,
                            onSearch = onSearch,
                            searching = searching,
                            history = dropdownHistory,
                            onPickHistory = onPickHistory,
                            onClearHistory = onClearHistory,
                            onFocusedChange = { inputFocused = it },
                        )
                    }
                    if (sourceOptions.isNotEmpty()) {
                        DesktopSearchSourcePicker(
                            target = searchTarget,
                            sourceOptions = sourceOptions,
                            onSelectSingle = onPickSource,
                            onSelectMulti = onPickMultiSources,
                        )
                    }
                    com.venera.desktop.ui.DesktopActionButton(
                        text = DesktopI18n.t("开始检索", "Search"),
                        primary = true,
                        onClick = onSearch,
                    )
                }
                Spacer(Modifier.height(10.dp))
            }

            // ── 单源专属筛选面板（聚合模式隐藏；来源已独立至顶栏选择器，空筛选组不画空框）──
            if (!isAggregate && filterGroups.isNotEmpty()) {
                SearchFilterPanel(
                    filterGroups = filterGroups,
                    filterValues = filterValues,
                    onFilterChange = onFilterChange,
                    onApplyFilters = onApplyFilters,
                    onResetFilters = onResetFilters,
                )
            }

            if (note != null) {
                Text(
                    text = note,
                    fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                    color = tokens.color.textTertiary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            // ── 「最近搜索」白卡架（风格对齐）：elevated 白卡 + 微影 r10，
            //    左标题、中 chip 横滚、右「清空」蓝链接（设计稿 shelf 同形）。
            //    判据（DESKTOP_COMIC_SEARCH_HISTORY_TITLE / CLEAR）一字不动。
            if (historyChips.isNotEmpty()) {
                val shelfShape = RoundedCornerShape(10.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .clip(shelfShape)
                        .background(palette.elevated)
                        .vShadow(1.dp, shelfShape)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = DesktopI18n.t(DESKTOP_COMIC_SEARCH_HISTORY_TITLE, "Recent"),
                        fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                        fontWeight = VeneraType.cardTitle.weight,
                        color = tokens.color.textPrimary,
                        maxLines = 1,
                    )
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        historyChips.forEach { word ->
                            com.venera.desktop.ui.DesktopFilterChip(
                                text = word,
                                selected = false,
                                onClick = { onPickHistory(word) },
                            )
                        }
                    }
                    Text(
                        text = DesktopI18n.t(DESKTOP_COMIC_SEARCH_HISTORY_CLEAR, "Clear"),
                        fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                        color = tokens.color.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onClearHistory)
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            if (isAggregate) {
                DesktopAggregateSearchContent(
                    orderedLanes = aggregateLanes,
                    hasSearched = aggregateHasSearched,
                    onlyHits = aggregateOnlyHits,
                    onToggleOnlyHits = onToggleAggregateOnlyHits,
                    selectedCardId = selectedCardId,
                    selectedCardSourceKey = selectedCardSourceKey,
                    maskedOf = aggregateMaskedOf,
                    onSelectCard = { sKey, sLabel, card ->
                        val picker = onSelectAggregateCard
                        if (picker != null) picker(sKey, sLabel, card)
                        else onOpenAggregateDetail(sKey, card)
                    },
                    onOpenDetail = onOpenAggregateDetail,
                    onSearchTagInSource = onSearchTagInSource,
                    onDrillDownSource = onDrillDownSource,
                    onRetrySource = onRetryAggregateSource,
                    peekPane = peekPane,
                    skippedSelfHosted = aggregateSkippedSelfHosted,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            } else {
            // ── 分区头（风格对齐）：15/700 标题 + 11.5 灰副 + 右侧分段视图切换 ──
            if (results.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        text = DesktopI18n.t("结果 ${results.size}", "${results.size} Results"),
                        fontSize = VeneraType.cardTitle.size, lineHeight = VeneraType.cardTitle.lineHeight,
                        fontWeight = VeneraType.cardTitle.weight,
                        color = tokens.color.textPrimary,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(start = 12.dp),
                    ) {
                        Text(
                            text = DesktopI18n.t("来源", "Source"),
                            fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                            color = tokens.color.textTertiary,
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(palette.glassDark)
                                .border(1.dp, GalleryTokens.Color.OutlineHairline, RoundedCornerShape(6.dp))
                                .padding(horizontal = 9.dp, vertical = 3.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Box(
                                    Modifier
                                        .size(6.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(tokens.color.primary),
                                )
                                Text(
                                    text = sourceLabel,
                                    fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                                    color = tokens.color.textPrimary,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    com.venera.desktop.ui.DesktopSegmented(
                        options = DesktopSearchViewMode.entries.map { it.label },
                        selectedIndex = viewMode.ordinal,
                        onSelect = { onViewModeChange(DesktopSearchViewMode.entries[it]) },
                    )
                }
            }

            // ── 搜索结果大流（左结果 + 右速览栏两栏独立）──
            when {
                results.isNotEmpty() -> {
                    // 主人口径：页尾「加载更多」钮撤掉 —— 近尾自动续（enabled = Button
                    // 即"还有下一页可扳"那档），右下角摆回顶钮；失败重试那一枚保留（那是失败出口）。
                    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
                    com.venera.desktop.ui.DesktopGridAutoLoadEffect(
                        state = gridState,
                        columns = 4,
                        enabled = moreState == DesktopComicSearchMore.Button,
                        onLoadMore = onLoadMore,
                    )
                    Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    androidx.compose.foundation.layout.BoxWithConstraints(
                        modifier = Modifier.fillMaxSize(),
                    ) {
                    val cardLayout by com.venera.desktop.settings.DesktopComicCardLayout.config.collectAsState()
                    val autoGridCols = when {
                        maxWidth < 950.dp -> 4
                        maxWidth < 1150.dp -> 5
                        else -> 6
                    }
                    val gridCols = if (cardLayout.isCustom) {
                        cardLayout.resolveGridColumns(maxWidth)
                    } else {
                        autoGridCols
                    }
                    val doubleCols = cardLayout.resolveDoubleColumns(2)
                    val coverAspect = cardLayout.resolveCoverAspect(0.72f)
                    val columns = when (viewMode) {
                        DesktopSearchViewMode.Single -> GridCells.Fixed(1)
                        DesktopSearchViewMode.Double -> GridCells.Fixed(doubleCols)
                        DesktopSearchViewMode.Grid -> GridCells.Fixed(gridCols)
                    }
                    LazyVerticalGrid(
                        state = gridState,
                        columns = columns,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 8.dp, bottom = tokens.spacing.space4),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        itemsIndexed(results, key = { _, card -> card.id }) { index, card ->
                            // 搜索结果卡补右键。搜标签走**当前屏的 sourceKey**
                            //（onQueryChange + onSearch 用同一颗源重搜，不吃全局默认源）；收藏走打开详情页，
                            // 详情页自带本地/网络两条腿且按源 —— 不在卡上摆没落点的假收藏项。
                            val cardTag = card.tags.firstOrNull()
                            com.venera.desktop.ui.components.DesktopComicCardMenuArea(
                                actions = com.venera.desktop.ui.components.DesktopComicCardActions(
                                    sourceKey = sourceKey,
                                    onOpenDetail = { onOpen(card) },
                                    onSearchInSource = { tag ->
                                        onQueryChange(tag)
                                        onSearch()
                                    },
                                    onCopyTitle = {
                                        val sel = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                                        sel.setContents(java.awt.datatransfer.StringSelection(card.title), null)
                                    },
                                ),
                                tag = cardTag,
                            ) {
                            // 单击 = 选中右侧速览；右键菜单的「查看详情」仍走完整二级页。
                            val selectCard = { (onSelectCard ?: onOpen)(card) }
                            if (viewMode != DesktopSearchViewMode.Grid) {
                                SearchRowCard(
                                    card = card,
                                    sourceLabel = sourceLabel,
                                    masked = maskedOf(card),
                                    selected = selectedCardId == card.id,
                                    index = index,
                                    updateTime = dateOf(card.id),
                                    onClick = selectCard,
                                )
                            } else {
                                SearchGridCard(
                                    card = card,
                                    sourceKey = sourceLabel,
                                    masked = maskedOf(card),
                                    coverAspect = coverAspect,
                                    onClick = selectCard,
                                )
                            }
                            }
                        }

                        // 页尾状态栏（全跨列）：自动续页后只剩读数与失败重试。
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            SearchFooter(
                                moreState = moreState,
                                onLoadMore = onLoadMore,
                                failedText = failedFooter,
                            )
                        }
                    }
                    com.venera.desktop.ui.DesktopBackToTopFab(
                        state = gridState,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(tokens.spacing.space3),
                    )
                    }
                    }
                    peekPane?.invoke()
                    }
                }

                emptyNote != null -> {
                    com.venera.desktop.ui.components.DesktopMascotIdle(
                        awakeKey = query,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        size = mascotSpec.sizeDp.dp,
                        spec = mascotSpec,
                        focusedListening = inputFocused,
                    ) {
                        androidx.compose.foundation.layout.Spacer(Modifier.height(18.dp))
                        Text(
                            text = emptyNote,
                            fontSize = VeneraType.body.size, lineHeight = VeneraType.body.lineHeight,
                            color = tokens.color.textTertiary,
                        )
                    }
                }

                else -> {
                    SearchSkeletonGrid(
                        viewMode = viewMode,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun SearchSkeletonGrid(
    viewMode: DesktopSearchViewMode,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    val cardLayout by com.venera.desktop.settings.DesktopComicCardLayout.config.collectAsState()
    val doubleCols = cardLayout.resolveDoubleColumns(2)
    val columns = if (viewMode == DesktopSearchViewMode.Grid) {
        GridCells.Fixed(if (cardLayout.isCustom) cardLayout.cols else 4)
    } else {
        GridCells.Fixed(if (viewMode == DesktopSearchViewMode.Single) 1 else doubleCols)
    }
    Column(modifier = modifier) {
        // 骨架只说"还没到"，这行说"正在等"，两者同屏才不会被看成卡死。
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            com.venera.compose.components.venera.VeneraShimmer(
                modifier = Modifier.size(12.dp),
                shape = androidx.compose.foundation.shape.CircleShape,
                emphasized = true,
            )
            Text(
                text = DesktopI18n.t("正在搜索…", "Searching…"),
                fontSize = VeneraType.aux.size,
                lineHeight = VeneraType.aux.lineHeight,
                color = tokens.color.textTertiary,
            )
        }
        LazyVerticalGrid(
            columns = columns,
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(top = 6.dp, bottom = tokens.spacing.space4),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            userScrollEnabled = false,
        ) {
            items(if (viewMode == DesktopSearchViewMode.Grid) 12 else 8) {
                if (viewMode == DesktopSearchViewMode.Grid) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(tokens.color.surfaceContainerHigh.copy(alpha = 0.35f))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SkeletonShimmer(Modifier.fillMaxWidth().height(180.dp), 8.dp)
                        SkeletonShimmer(Modifier.fillMaxWidth(0.85f).height(14.dp), 4.dp)
                        SkeletonShimmer(Modifier.fillMaxWidth(0.5f).height(12.dp), 4.dp)
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(116.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(tokens.color.surfaceContainerHigh.copy(alpha = 0.35f))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SkeletonShimmer(Modifier.width(72.dp).fillMaxHeight(), 8.dp)
                        Column(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SkeletonShimmer(Modifier.fillMaxWidth(0.75f).height(16.dp), 4.dp)
                            SkeletonShimmer(Modifier.fillMaxWidth(0.45f).height(12.dp), 4.dp)
                            Spacer(Modifier.weight(1f))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                SkeletonShimmer(Modifier.width(48.dp).height(18.dp), 6.dp)
                                SkeletonShimmer(Modifier.width(56.dp).height(18.dp), 6.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 结果骨架块：统一走扫光强调档，卡片底色上的静态灰块太容易被看成加载完成。 */
@Composable
private fun SkeletonShimmer(modifier: Modifier, cornerRadius: Dp) {
    com.venera.compose.components.venera.VeneraShimmer(
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius),
        emphasized = true,
    )
}

/** 行式结果卡：封面 + 两行标题 + 作者独立行 + 两行标签（+N）+ 源角标 + hover 动作坞。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchRowCard(
    card: EngineSession.ComicCard,
    sourceLabel: String,
    masked: Boolean,
    selected: Boolean,
    index: Int,
    updateTime: String,
    onClick: () -> Unit,
) {
    val tokens = VeneraTokens
    val palette = com.venera.desktop.gallery.ui.LocalChromePalette.current
    val selectedColor = tokens.color.primary

    // 按序入场：前五张各差 45ms，第六张起封顶；换本不重播（key 是 card.id，组合不重建）。
    val entrance = remember(card.id) { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(card.id) {
        kotlinx.coroutines.delay(index.coerceAtMost(5) * 45L)
        entrance.animateTo(
            1f,
            androidx.compose.animation.core.spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        )
    }

    com.venera.desktop.ui.components.DesktopFloatingCard(
        cornerRadius = 12.dp,
        surface = palette.card,
        selected = selected,
        selectedColor = selectedColor,
        modifier = Modifier
            .fillMaxWidth()
            .height(156.dp)
            .graphicsLayer {
                alpha = entrance.value
                translationY = (1f - entrance.value) * 14f
                scaleX = 0.985f + 0.015f * entrance.value
                scaleY = scaleX
            }
            .clickable(onClick = onClick),
    ) { floatState ->
        // 选中态的左侧竖条：画在内容之上，不占测量。
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(selectedColor),
            )
        }
        Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    modifier = Modifier
                        .width(96.dp)
                        .aspectRatio(0.72f)
                        .clip(RoundedCornerShape(4.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(4.dp)),
                ) {
                    VeneraCover(
                        url = card.cover,
                        contentDescription = card.title,
                        mask = if (masked) VeneraCoverMask.Masked else VeneraCoverMask.Visible,
                        modifier = Modifier
                            .fillMaxSize()
                            .desktopCoverFloat(floatState),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f).padding(end = 92.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = card.title,
                        fontSize = VeneraType.bodySecondary.size,
                        fontWeight = VeneraType.cardTitle.weight,
                        color = tokens.color.textPrimary,
                        maxLines = 2,
                        lineHeight = VeneraType.bodySecondary.lineHeight,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!card.author.isNullOrBlank()) {
                        Text(
                            text = DesktopI18n.t("作者：", "Author: ") + card.author,
                            fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                            color = tokens.color.textTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (updateTime.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(palette.pill)
                                .border(1.dp, GalleryTokens.Color.OutlineHairline, RoundedCornerShape(5.dp))
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = updateTime,
                                fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                                color = tokens.color.textTertiary,
                                maxLines = 1,
                            )
                        }
                    }
                    if (card.tags.isNotEmpty()) {
                        SearchTagFlow(card.tags)
                    }
                }
            }

            // 源标在右上角，显示源名而非内部键；悬停点亮（与画廊卡信息栏同一条腿）。
            com.venera.desktop.ui.components.DesktopCardSourcePill(
                text = sourceLabel,
                highlighted = floatState.isHovered,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .desktopUiFloat(floatState),
            )

            // hover 三动作：收藏 / 下载 / 更多。落点统一进详情页，那里有真收藏与真下载腿；
            // 不在卡上摆「点了只翻图标色」的假收藏态。卡高定死 156dp ⇒ 显隐只淡入淡出，不推挤。
            androidx.compose.animation.AnimatedVisibility(
                visible = floatState.isHovered,
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(120)),
                modifier = Modifier.align(Alignment.BottomEnd),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SearchDockButton(DesktopI18n.t("收藏", "Favorite"), onClick)
                    SearchDockButton(DesktopI18n.t("下载", "Download"), onClick)
                    SearchDockButton(DesktopI18n.t("更多", "More"), onClick)
                }
            }
        }
    }
}

/** 标签两行封顶，放不下的数量收进 +N，不出现第三行。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchTagFlow(tags: List<String>) {
    val tokens = VeneraTokens
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        maxLines = 2,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val shown = tags.take(7)
        shown.forEach { tag ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(tokens.color.surfaceVariant.copy(alpha = 0.6f))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            ) {
                Text(
                    text = tag,
                    fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                    color = tokens.color.textSecondary,
                    maxLines = 1,
                )
            }
        }
        if (tags.size > shown.size) {
            Text(
                text = "+${tags.size - shown.size}",
                fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                fontWeight = VeneraType.cardTitle.weight,
                color = tokens.color.textTertiary,
                modifier = Modifier.padding(horizontal = 3.dp, vertical = 2.dp),
            )
        }
    }
}

/** hover 动作坞里的一枚文字钮；收编至标准 `DesktopButton(Compact)`。 */
@Composable
private fun SearchDockButton(label: String, onClick: () -> Unit) {
    com.venera.desktop.ui.DesktopButton(
        text = label,
        onClick = onClick,
        size = com.venera.desktop.ui.DesktopButtonSize.Compact,
        variant = com.venera.desktop.ui.DesktopButtonVariant.Secondary,
        horizontalPadding = 10.dp,
    )
}

/** 竖卡展示（卡座底板 + 固定 36dp 双行标题槽） */
@Composable
private fun SearchGridCard(
    card: EngineSession.ComicCard,
    sourceKey: String,
    masked: Boolean,
    coverAspect: Float = 0.72f,
    onClick: () -> Unit,
) {
    val tokens = VeneraTokens
    // 风格对齐：白卡 + 微影（去 1dp 描边）。
    val palette = com.venera.desktop.gallery.ui.LocalChromePalette.current

    com.venera.desktop.ui.components.DesktopFloatingCard(
        cornerRadius = 12.dp,
        surface = palette.elevated,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
                    modifier = Modifier
                        .fillMaxSize()
                        .desktopCoverFloat(floatState),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                // 站标从封面左上搬进信息栏：常态低饱和，悬停点亮（与画廊卡同一条腿）。
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = card.title,
                        fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                        fontWeight = VeneraType.control.weight,
                        color = palette.ink1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().padding(end = 46.dp),
                    )
                    com.venera.desktop.ui.components.DesktopCardSourcePill(
                        text = sourceKey,
                        highlighted = floatState.isHovered,
                        modifier = Modifier.align(Alignment.TopEnd),
                    )
                }
                if (!card.author.isNullOrBlank()) {
                    Text(
                        text = card.author,
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

/** 页尾翻页四态（严格对齐单元测试断言与常例文案） */
@Composable
private fun SearchFooter(
    moreState: DesktopComicSearchMore,
    onLoadMore: () -> Unit,
    failedText: String?,
) {
    val tokens = VeneraTokens
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = tokens.spacing.space4),
        contentAlignment = Alignment.Center,
    ) {
        when (moreState) {
            DesktopComicSearchMore.Button -> {
                // 主人口径：这一态不再摆「加载更多」钮 —— 近尾观察器自动扳，
                // 屏上只念一句读数（文案照旧出自常量，测试口径一起改）。
                Text(
                    text = DesktopI18n.t("$DESKTOP_COMIC_SEARCH_MORE_LABEL · 继续下滑自动加载", "Load more · Scroll down to load"),
                    fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                    color = tokens.color.textTertiary,
                )
            }
            DesktopComicSearchMore.Loading -> {
                Text(
                    text = DesktopI18n.t(DESKTOP_COMIC_SEARCH_LOADING_LABEL, "Loading more…"),
                    fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                    color = tokens.color.textTertiary,
                )
            }
            DesktopComicSearchMore.End -> {
                Text(
                    text = DesktopI18n.t(DESKTOP_COMIC_SEARCH_END_LABEL, "No more results"),
                    fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                    color = tokens.color.textTertiary,
                )
            }
            DesktopComicSearchMore.Failed -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
                ) {
                    if (failedText != null) {
                        Text(
                            text = failedText,
                            fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                            color = tokens.color.textTertiary,
                        )
                    }
                    DesktopPushButton(onClick = onLoadMore) {
                        Text(DesktopI18n.t(DESKTOP_COMIC_SEARCH_RETRY_LABEL, "Retry"))
                    }
                }
            }
            DesktopComicSearchMore.None -> {}
        }
    }
}

/** 满宽单行搜索输入框；聚焦或点按时展开搜索历史浮层。 */
@Composable
internal fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    searching: Boolean,
    history: List<String> = emptyList(),
    onPickHistory: (String) -> Unit = {},
    onClearHistory: () -> Unit = {},
    /** 聚焦态上报：空态区吉祥物据此切「我在听」姿态。 */
    onFocusedChange: (Boolean) -> Unit = {},
) {
    val palette = com.venera.desktop.gallery.ui.LocalChromePalette.current
    val density = LocalDensity.current
    val fieldInteraction = remember { MutableInteractionSource() }
    val focused by fieldInteraction.collectIsFocusedAsState()
    androidx.compose.runtime.LaunchedEffect(focused) { onFocusedChange(focused) }
    var suppressDropdown by remember { mutableStateOf(false) }
    var fieldWidthPx by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var fieldBottomPx by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val open = focused && !suppressDropdown

    // 重新聚焦时抬起浮层：发车/Esc/点外部压住后焦点往往还在，第二次点场靠这一把抬起。
    androidx.compose.runtime.LaunchedEffect(focused) {
        if (focused) {
            suppressDropdown = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned {
                fieldWidthPx = it.size.width
                fieldBottomPx = it.size.height + it.positionInParent().y.roundToInt()
            }
            .pointerInput(Unit) {
                awaitEachGesture { awaitFirstDown(requireUnconsumed = false); suppressDropdown = false }
            },
    ) {
        DesktopSearchField(
            value = value,
            onValueChange = { onValueChange(it); suppressDropdown = false },
            onSearch = { suppressDropdown = true; onSearch() },
            searching = searching,
            placeholder = DesktopI18n.t("搜索漫画名、作者或标签… 回车检索", "Search title, author, or tags… Press Enter"),
            onClear = { onValueChange("") },
            interactionSource = fieldInteraction,
        )

        if (open && history.isNotEmpty()) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, fieldBottomPx + with(density) { 6.dp.roundToPx() }),
                onDismissRequest = { suppressDropdown = true },
                properties = PopupProperties(focusable = false),
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(min = 280.dp)
                        .width(with(density) { fieldWidthPx.toDp() })
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.elevated)
                        .border(1.dp, palette.hairline, RoundedCornerShape(10.dp))
                        .padding(vertical = 8.dp, horizontal = 10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = DesktopI18n.t(DESKTOP_COMIC_SEARCH_HISTORY_TITLE, "Recent"),
                            fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                            fontWeight = VeneraType.cardTitle.weight,
                            color = VeneraTokens.color.textSecondary,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = DesktopI18n.t(DESKTOP_COMIC_SEARCH_HISTORY_CLEAR, "Clear"),
                            fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                            color = VeneraTokens.color.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { suppressDropdown = true; onClearHistory() }
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        history.forEach { word ->
                            VeneraChip(
                                text = word,
                                variant = com.venera.compose.components.venera.VeneraChipVariant.Neutral,
                                onClick = { suppressDropdown = true; onPickHistory(word) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 多选筛选值（`["0","1"]` 这一形，与安卓 `SearchOptionValues` 同编码）→ 键集合。
 * 空串 / null / `[]` 都读成空集（"一档都没选"）。
 */
internal fun desktopMultiSelectKeys(value: String?): Set<String> =
    value
        ?.trim()
        ?.removePrefix("[")
        ?.removeSuffix("]")
        ?.split(',')
        ?.map { it.trim().trim('"') }
        ?.filter { it.isNotEmpty() }
        ?.toSet()
        ?: emptySet()

/** 键集合 → 多选筛选值（JSON 串；源 JS 的 `JSON.parse(options[i])` 认这个形）。 */
internal fun desktopMultiSelectEncode(keys: Set<String>): String =
    keys.joinToString(separator = ",", prefix = "[", postfix = "]") { "\"" + it + "\"" }

/**
 * 单源专属可展开筛选面板：默认收起成一行，展开后按源筛选组与动作行依次铺开。
 * 来源选择已独立至顶栏 [DesktopSearchSourcePicker]，本面板不再混放来源行。
 *
 * @param filterGroups 该源 `search.optionList` 摊平后的筛选组，次序与 [filterValues] 对应。
 * @param filterValues 各组当前值，由壳持有，屏不记状态。
 * @param onFilterChange 改某组的值，转交壳。
 * @param onApplyFilters 按当前值重搜，转交壳。
 * @param onResetFilters 回该源默认值，转交壳。
 */
@Composable
private fun SearchFilterPanel(
    filterGroups: List<EngineSession.SearchOption>,
    filterValues: List<String?>,
    onFilterChange: (Int, String?) -> Unit,
    onApplyFilters: () -> Unit,
    onResetFilters: () -> Unit,
) {
    val tokens = VeneraTokens
    val palette = com.venera.desktop.gallery.ui.LocalChromePalette.current
    var expanded by remember { mutableStateOf(false) }
    val shape = GalleryTokens.Shape.Large
    // 偏离默认值的组数 = 已启用项。
    val activeCount = filterGroups.indices.count { index ->
        filterValues.getOrNull(index) != filterGroups[index].defaultValue
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(palette.elevated)
            .border(1.dp, GalleryTokens.Color.OutlineHairline, shape)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = DesktopI18n.t("筛选条件", "Filters"),
                fontSize = VeneraType.cardTitle.size, lineHeight = VeneraType.cardTitle.lineHeight,
                fontWeight = VeneraType.cardTitle.weight,
                color = tokens.color.textPrimary,
            )
            Spacer(Modifier.weight(1f))
            if (activeCount > 0) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(tokens.color.primary.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = DesktopI18n.t("$activeCount 项已启用", "$activeCount active"),
                        fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                        color = tokens.color.primary,
                    )
                }
            }
            Text(
                text = if (expanded) "⌃" else "⌄",
                fontSize = VeneraType.control.size,
                color = tokens.color.textTertiary,
                modifier = Modifier.padding(start = 10.dp),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
            ) + fadeIn(animationSpec = tween(180)),
            exit = shrinkVertically(animationSpec = tween(220)) + fadeOut(animationSpec = tween(120)),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                filterGroups.forEachIndexed { index, group ->
                    val value = filterValues.getOrNull(index)
                    SearchFilterGroupRow(group.label.ifBlank { DesktopI18n.t("选项 ${index + 1}", "Option ${index + 1}") }) {
                        when (group.type) {
                            "multi-select" -> {
                                val picked = desktopMultiSelectKeys(value)
                                group.choices.forEach { (key, label) ->
                                    com.venera.desktop.ui.DesktopFilterChip(
                                        text = label,
                                        selected = key in picked,
                                        onClick = {
                                            val next = if (key in picked) picked - key else picked + key
                                            onFilterChange(index, desktopMultiSelectEncode(next))
                                        },
                                    )
                                }
                            }
                            "select" -> group.choices.forEach { (key, label) ->
                                com.venera.desktop.ui.DesktopFilterChip(
                                    text = label,
                                    selected = value == key,
                                    onClick = { onFilterChange(index, key) },
                                )
                            }
                            "dropdown" -> {
                                val labels = listOf(DesktopI18n.t("未选择", "Any")) + group.choices.map { it.second }
                                com.venera.desktop.ui.DesktopPopUpButton(
                                    label = null,
                                    value = group.choices.firstOrNull { it.first == value }?.second
                                        ?: DesktopI18n.t("未选择", "Any"),
                                    options = labels,
                                    selectedIndex = group.choices.indexOfFirst { it.first == value } + 1,
                                    onSelect = { i ->
                                        onFilterChange(index, if (i == 0) null else group.choices[i - 1].first)
                                    },
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    com.venera.desktop.ui.DesktopButton(
                        text = DesktopI18n.t("重置", "Reset"),
                        onClick = onResetFilters,
                        size = com.venera.desktop.ui.DesktopButtonSize.Compact,
                        variant = com.venera.desktop.ui.DesktopButtonVariant.Ghost,
                    )
                    com.venera.desktop.ui.DesktopButton(
                        text = DesktopI18n.t("应用筛选", "Apply"),
                        onClick = { onApplyFilters(); expanded = false },
                        size = com.venera.desktop.ui.DesktopButtonSize.Compact,
                        variant = com.venera.desktop.ui.DesktopButtonVariant.Primary,
                    )
                }
            }
        }
    }
}

/** 筛选面板里的一行：左侧组名 + 右侧可横滚的胶囊组。 */
@Composable
private fun SearchFilterGroupRow(
    label: String,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = label,
            fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
            fontWeight = VeneraType.cardTitle.weight,
            color = VeneraTokens.color.textTertiary,
            modifier = Modifier.width(40.dp),
        )
        Row(
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()
        }
    }
}
