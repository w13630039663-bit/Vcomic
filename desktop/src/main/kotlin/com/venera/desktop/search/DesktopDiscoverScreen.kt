package com.venera.desktop.search

import com.venera.desktop.ui.theme.VeneraType
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.explore.DesktopExploreScreen
import com.venera.desktop.explore.ExploreSourceChoice
import com.venera.desktop.settings.DesktopI18n
import com.venera.desktop.ui.DesktopPageWidth
import com.venera.desktop.ui.DesktopSelectionBlock
import com.venera.desktop.ui.desktopPageMaxWidth
import com.venera.engineprobe.EngineSession

/** 选择器两档：搜索页 / 探索页（分离双页 ——复核令， discover-split-）。 */
private enum class DiscoverView(val en: String) {
    Search("COMIC SEARCH"),
    Discover("EXPLORE"),
    ;

    val title: String
        get() = when (this) {
            Search -> DesktopI18n.tr("漫画搜索", "Comic Search")
            Discover -> DesktopI18n.tr("探索", "Explore")
        }
}

/**
 * 桌面「漫画搜索」选择器页（「还是做个选择器分离吧，搜索一个页面
 * 探索一个页面，搜索的栏不要固定一个位置」—— 上一版两屏叠一页、检索栏全局钉顶，被否）。
 *
 * 页头 = 大标题 + 右缘「搜索 / 探索」选择器同一行；两页各自完整（见两颗子屏的重排）：
 *  - **搜索页**：检索栏（字段 + 检索钮）→ 源胶囊行 → 筛选甲板 → 最近搜索 → 结果流；
 *    检索栏只属于这一页、跟随内容流，不再固定在整页顶上；
 *  - **探索页**：紧凑页头（源徽标 + 读数 + 重新取）→ 控制甲板 → 分区漫画流。
 *
 * 大标题由本页统一画（子屏 `showOwnTitle = false` 不再自画，避免双标题）。
 * 状态仍全 hoist 在壳里（两套取数/筛选/翻页/历史管线零复制）；探索取数闸门在壳里已放开 SEARCH 格。
 *
 * 自动切换：探索卡右键「按源搜标签」这类入口会直接发车一轮检索（`searching` 翻真）
 * 而人多半还停在探索页 —— 那一拍自动切到搜索页接住结果；只在开检瞬间切，不抢手动选择。
 */
@Composable
internal fun DesktopDiscoverScreen(
    mode: WideScreenLayoutMode,
    // ── 检索段（DesktopSearchScreen 全量形参，原样转发）──
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
    onOpenResult: (EngineSession.ComicCard) -> Unit,
    historyChips: List<String>,
    onPickHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    /** 检索场下拉用的完整历史，不受结果页那排的收起条件影响。 */
    dropdownHistory: List<String> = historyChips,
    moreState: DesktopComicSearchMore,
    onLoadMore: () -> Unit,
    failedFooter: String?,
    emptyNote: String?,
    filterGroups: List<EngineSession.SearchOption> = emptyList(),
    filterValues: List<String?> = emptyList(),
    onFilterChange: (Int, String?) -> Unit = { _, _ -> },
    onApplyFilters: () -> Unit = {},
    onResetFilters: () -> Unit = {},
    viewMode: DesktopSearchViewMode = DesktopSearchViewMode.Single,
    onViewModeChange: (DesktopSearchViewMode) -> Unit = {},
    onSelectCard: ((EngineSession.ComicCard) -> Unit)? = null,
    selectedCardId: String? = null,
    selectedCardSourceKey: String? = null,
    /** 按作品 id 查已取到的更新日期；空串 = 该本还没取详情。 */
    dateOf: (String) -> String = { "" },
    peekPane: (@Composable () -> Unit)? = null,
    searchTarget: DesktopSearchTarget = DesktopSearchTarget.Single(sourceKey),
    onPickMultiSources: (Set<String>) -> Unit = {},
    aggregateLanes: List<DesktopAggregateLaneState> = emptyList(),
    aggregateHasSearched: Boolean = false,
    aggregateOnlyHits: Boolean = false,
    onToggleAggregateOnlyHits: () -> Unit = {},
    aggregateMaskedOf: (String, EngineSession.ComicCard) -> Boolean = { _, card -> maskedOf(card) },
    onSelectAggregateCard: ((String, String, EngineSession.ComicCard) -> Unit)? = null,
    onOpenAggregateDetail: (String, EngineSession.ComicCard) -> Unit = { _, card -> onOpenResult(card) },
    onSearchTagInSource: (String, String) -> Unit = { _, tag -> onQueryChange(tag); onSearch() },
    onDrillDownSource: (String) -> Unit = onPickSource,
    onRetryAggregateSource: (String) -> Unit = {},
    aggregateSkippedSelfHosted: List<String> = emptyList(),
    // ── 发现阶段（DesktopExploreScreen 全量形参，原样转发）──
    exploreSourceKey: String,
    exploreSourceChoices: List<ExploreSourceChoice>,
    onPickExploreSource: (String?) -> Unit,
    exploreModes: List<String>,
    exploreSelectedMode: Int,
    onPickExploreMode: (Int) -> Unit,
    categoryParts: List<EngineSession.CategoryPart>,
    rankingChips: List<Pair<String, String>>,
    onPickCategory: (String, String, String?, String) -> Unit,
    onPickRanking: (String) -> Unit,
    pickedLabel: String?,
    pickedPage: EngineSession.Page?,
    pickedLoadingMore: Boolean,
    pickedHasMore: Boolean,
    onLoadMorePicked: () -> Unit,
    onClearPicked: () -> Unit,
    sections: List<EngineSession.Section>,
    exploreNote: String?,
    onOpenCard: (EngineSession.ComicCard) -> Unit,
    onSearchComicTag: ((String) -> Unit)?,
    onReloadExplore: () -> Unit,
    offlineBadgeOf: (EngineSession.ComicCard) -> String?,
    /**
     * 选择器两档是否停在「探索」（壳持有，分离稿）：命令带面包屑读同一份，
     * 切路由回来选择器不归零。`onViewChange` 回写壳。
     */
    viewExplore: Boolean = false,
    onViewChange: (Boolean) -> Unit = {},
) {
    val tokens = VeneraTokens
    val maxWidth = desktopPageMaxWidth(DesktopPageWidth.Full, mode)

    // 选择器两档由壳持有（面包屑同源 + 切路由不归零）；页内只做展示与切换。
    var view by remember { mutableStateOf(if (viewExplore) DiscoverView.Discover else DiscoverView.Search) }
    LaunchedEffect(viewExplore) {
        view = if (viewExplore) DiscoverView.Discover else DiscoverView.Search
    }

    // 探索卡右键「按源搜标签」这类入口直接发车一轮检索（searching 翻真）而人还停在
    // 探索页 —— 自动切到搜索页接住结果。键 = searching：只在开检那一拍切；
    // 结果到货 / 翻页（searching 回落）不会把人拽回来，手动选择不被抢。
    LaunchedEffect(searching) {
        if (searching && view == DiscoverView.Discover) {
            view = DiscoverView.Search
            onViewChange(false)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .then(if (maxWidth != null) Modifier.width(maxWidth) else Modifier)
                .fillMaxSize()
                .padding(horizontal = tokens.spacing.screenHorizontal),
        ) {
            // ── 工具栏行（44dp，macOS 稿）：分段「搜索 / 探索」+ 检索场（仅搜索页）──
            // 设计稿把检索场钉在工具栏右、内容区不再重复一条检索行；分段控件是全站唯一互斥小档形。
            Row(
                // 「栏太小」二次复核：与画廊顶栏同步 44 → 52 → 60dp。
                modifier = Modifier.fillMaxWidth().height(60.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                com.venera.desktop.ui.DesktopSegmented(
                    options = DiscoverView.entries.map { if (it == DiscoverView.Search) DesktopI18n.t("搜索", "Search") else DesktopI18n.t("探索", "Explore") },
                    selectedIndex = view.ordinal,
                    onSelect = { idx ->
                        val v = DiscoverView.entries.getOrNull(idx) ?: view
                        if (v != view) {
                            view = v
                            onViewChange(v == DiscoverView.Discover)
                        }
                    },
                )
                if (view == DiscoverView.Search) {
                    Box(modifier = Modifier.weight(1f)) {
                        SearchField(
                            value = query,
                            onValueChange = onQueryChange,
                            onSearch = onSearch,
                            searching = searching,
                            history = dropdownHistory,
                            onPickHistory = onPickHistory,
                            onClearHistory = onClearHistory,
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
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }

            // ── 标题 + 副题（20/700 + 12 灰，与收藏页同一形）──
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 10.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = view.title,
                    fontSize = VeneraType.statNumber.size, lineHeight = VeneraType.statNumber.lineHeight,
                    fontWeight = VeneraType.cardTitle.weight,
                    color = tokens.color.textPrimary,
                )
                Text(
                    text = if (view == DiscoverView.Search) {
                        when (val target = searchTarget) {
                            is DesktopSearchTarget.Aggregate -> {
                                val enabledKeys = sourceOptions.map { it.key }
                                val activeKeys = if (target.allEnabled || target.selectedKeys.isEmpty()) {
                                    enabledKeys
                                } else {
                                    enabledKeys.filter { it in target.selectedKeys }.ifEmpty { enabledKeys }
                                }
                                val total = aggregateLanes.sumOf { it.cards.size }
                                val head = describeAggregateLabel(activeKeys, sourceOptions)
                                buildString {
                                    append(head)
                                    if (total > 0) append(DesktopI18n.t(" · $total 本", " · $total comics"))
                                }
                            }
                            is DesktopSearchTarget.Single -> buildString {
                                append(sourceLabel)
                                if (results.isNotEmpty()) append(DesktopI18n.t(" · ${results.size} 本", " · ${results.size} comics"))
                                if (filterGroups.isNotEmpty()) append(DesktopI18n.t(" · ${filterGroups.size} 项筛选", " · ${filterGroups.size} filters"))
                            }
                        }
                    } else {
                        DesktopI18n.t(
                            "$exploreSourceKey · ${sections.size} 个分区 · ${sections.sumOf { it.comics.size }} 本",
                            "$exploreSourceKey · ${sections.size} sections · ${sections.sumOf { it.comics.size }} comics",
                        )
                    },
                    fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                    color = tokens.color.textTertiary,
                    modifier = Modifier.padding(start = 12.dp),
                    maxLines = 1,
                )
            }

            when (view) {
                DiscoverView.Search -> DesktopSearchScreen(
                    mode = mode,
                    sourceKey = sourceKey,
                    sourceLabel = sourceLabel,
                    sourceOptions = sourceOptions,
                    onPickSource = onPickSource,
                    query = query,
                    onQueryChange = onQueryChange,
                    onSearch = onSearch,
                    results = results,
                    searching = searching,
                    note = note,
                    maskedOf = maskedOf,
                    onOpen = onOpenResult,
                    historyChips = historyChips,
                    onPickHistory = onPickHistory,
                    onClearHistory = onClearHistory,
                    dropdownHistory = dropdownHistory,
                    moreState = moreState,
                    onLoadMore = onLoadMore,
                    failedFooter = failedFooter,
                    emptyNote = emptyNote,
                    filterGroups = filterGroups,
                    filterValues = filterValues,
                    onFilterChange = onFilterChange,
                    onApplyFilters = onApplyFilters,
                    onResetFilters = onResetFilters,
                    viewMode = viewMode,
                    onViewModeChange = onViewModeChange,
                    onSelectCard = onSelectCard,
                    selectedCardId = selectedCardId,
                    selectedCardSourceKey = selectedCardSourceKey,
                    dateOf = dateOf,
                    peekPane = peekPane,
                    searchTarget = searchTarget,
                    onPickMultiSources = onPickMultiSources,
                    aggregateLanes = aggregateLanes,
                    aggregateHasSearched = aggregateHasSearched,
                    aggregateOnlyHits = aggregateOnlyHits,
                    onToggleAggregateOnlyHits = onToggleAggregateOnlyHits,
                    aggregateMaskedOf = aggregateMaskedOf,
                    onSelectAggregateCard = onSelectAggregateCard,
                    onOpenAggregateDetail = onOpenAggregateDetail,
                    onSearchTagInSource = onSearchTagInSource,
                    onDrillDownSource = onDrillDownSource,
                    onRetryAggregateSource = onRetryAggregateSource,
                    aggregateSkippedSelfHosted = aggregateSkippedSelfHosted,
                    // 大标题由选择器页统一画，子屏不再自画（检索栏跟随内容流）。
                    showOwnTitle = false,
                    // 检索场已提到顶部工具栏行 ⇒ 内容区不再重复一条。
                    showSearchField = false,
                    modifier = Modifier.weight(1f),
                )

                DiscoverView.Discover -> DesktopExploreScreen(
                    mode = mode,
                    sourceKey = exploreSourceKey,
                    sourceChoices = exploreSourceChoices,
                    onPickSource = onPickExploreSource,
                    modes = exploreModes,
                    selectedMode = exploreSelectedMode,
                    onPickMode = onPickExploreMode,
                    categoryParts = categoryParts,
                    rankingChips = rankingChips,
                    onPickCategory = onPickCategory,
                    onPickRanking = onPickRanking,
                    pickedLabel = pickedLabel,
                    pickedPage = explorePickPage(pickedPage),
                    pickedLoadingMore = pickedLoadingMore,
                    pickedHasMore = pickedHasMore,
                    onLoadMorePicked = onLoadMorePicked,
                    onClearPicked = onClearPicked,
                    sections = sections,
                    note = exploreNote,
                    maskedOf = maskedOf,
                    onOpen = onOpenCard,
                    // 选择器页没有批量收藏的写口 ⇒ 探索屏不进入多选（勾选钮与坞都不摆）。
                    onAddFavorites = null,
                    onSearchComicTag = onSearchComicTag,
                    onReload = onReloadExplore,
                    // 九枚旧入口屏上就不摆（落点统一在侧栏）；转发空占位。
                    onOpenSourceManage = {},
                    onOpenQueue = {},
                    onOpenDownloads = null,
                    onOpenLocalComics = null,
                    onOpenHistory = {},
                    onOpenStats = {},
                    onOpenBackup = {},
                    onOpenSettings = {},
                    offlineBadgeOf = offlineBadgeOf,
                    // 大标题由选择器页统一画；探索屏只留紧凑页头（源徽标 + 读数 + 重新取）。
                    showOwnTitle = false,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 透传（独立小函数留个名字给注释锚定：pickedPage 语义不变）。 */
private fun explorePickPage(page: EngineSession.Page?): EngineSession.Page? = page
