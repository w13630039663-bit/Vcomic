package com.venera.desktop.gallery.ui

import com.venera.desktop.ui.components.registerPixelBuddyStatusAnchor
import com.venera.desktop.ui.vShadow
import com.venera.desktop.ui.theme.VeneraType
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import com.venera.compose.components.LocalWindowWidth
import com.venera.compose.components.VeneraEmptyView
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.data.GalleryTagSuggestion
import com.venera.compose.gallery.domain.GalleryColumnMode
import com.venera.compose.gallery.domain.GalleryRanking
import com.venera.compose.gallery.domain.GallerySearchEntry
import com.venera.compose.gallery.domain.PixivSearchFilter
import com.venera.compose.ui.tokens.LocalVeneraDarkTheme
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.ui.GridPageTemplate
import java.time.LocalDate
import kotlinx.coroutines.launch



/**
 * 画廊那一域的**标签搜索屏**（桌面化与聚焦下拉浮层重构）。
 *
 * ## 它补的是哪一笔欠账
 *
 * `fetchSearchPage`（取一页，与续页同一颗）、`boardOf`（来源轴 → 哪一站的板）、
 * 以及 `DesktopSearchHistory` 那一整排纯件（读 / 写 / 落点 / 清），从桌面起就都在，
 * `DesktopSearchPagingTest` / `DesktopSearchHistoryTest` 也一直在绿 —— 而 `desktop/src/main` 里
 * 没有任何一屏调它们。本屏把门开上。
 *
 * ##最新重构纪律：
 * 1. **搜索框聚焦智能下拉浮层 (Flyout)**：
 *    将「最近搜索」与「标签联想词」从左侧面板移到搜索框正下方浮层，彻底根治展开时的空白留白与过长问题。
 *    支持 Pixiv 官方关联词方案 (AutoComplete) 与 Booru 实时补全，含日文/中文译名微标与分类。
 * 2. **高级筛选侧栏默认隐藏，并具备流畅抽屉过渡**：
 *    `inspectorOpen` 默认 `false`，使用 `expandHorizontally + fadeIn / shrinkHorizontally + fadeOut` 平滑开合。
 * 3. **卡片位置绝对稳定**：
 *    列数基于全局窗口宽度的稳定分档，解耦局部每像素 `onSizeChanged`，窗口缩放与抽屉开合时卡片不乱跳。
 * 4. **搜索卡片流畅过渡**：
 *    模式切换与流更新使用 `AnimatedContent` 平滑淡入，彻底杜绝突兀闪现。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DesktopGallerySearchScreen(
    mode: WideScreenLayoutMode,
    /**
     * 结果墙的滚动位（进详情返回不掉回顶）。**必传无默认值**，
     * 与墙的 `gridState` 同一条纪律：state 住在壳那一层，屏内 remember 一离路由就丢。
     */
    resultGridState: androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState,
    /** 画师行（pixiv 搜画师档）的滚动位，同 [resultGridState] 那条纪律。**必传无默认值**。 */
    artistListState: androidx.compose.foundation.lazy.LazyListState,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    /** 进行中：与「搜完了 0 条」要分开说。 */
    searching: Boolean,
    /** 首轮取数失败：与「站方回了 0 条」分开，空态给重试出口。 */
    failed: Boolean,
    /** 重试：重发当前词。 */
    onRetry: () -> Unit,
    /** 来源轴那几档的显示名（「全部」+ 每站），由壳从 [GallerySearchSource.options] 的 label 算好，屏不重述来源表。 */
    sources: List<String>,
    /** 当前选中那一档的下标（与 [sources] 同源）。 */
    selectedSource: Int,
    /** 换来源档：壳据此重建 queries 并另起一轮。 */
    onPickSource: (Int) -> Unit,
    history: List<GallerySearchEntry>,
    /** 点一条历史：壳据 [desktopHistoryLanding] 装回查询词与来源档，并另起一轮。 */
    onPickHistory: (GallerySearchEntry) -> Unit,
    onClearHistory: () -> Unit,
    /** 「最近搜索那份记不住」那一句（写盘塌了才非空）；非空时那一排整颗不摆。 */
    historyNote: String?,
    cards: List<DesktopWallCardModel>,
    footer: DesktopGalleryWallFooter,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * 标签联想那一排（门开在两颗既有纯件上）。
     * 三枚均必传无默认值（`DesktopCallbackParamLedgerTest` 扫的就是这里那族默认 `{}`）。
     */
    suggestCandidates: List<GalleryTagSuggestion>,
    suggestLine: String?,
    onPickSuggestion: (String) -> Unit,
    /** 结果卡点下去看大图。 */
    onOpenPost: (DesktopWallCardModel) -> Unit,
    /** 点 pixiv 结果卡上的画师名 → 壳接画师详情页路由；null = 回退浏览器。与画师行那枚 [onOpenArtist]（ PixivUser）不同轴。 */
    onOpenCardArtist: ((Long, String) -> Unit)?,
    /** 0 = 图库（Booru），1 = Pixiv 专属。 */
    searchMode: Int,
    onPickMode: (Int) -> Unit,
    /** 图库域的排行档（壳按当前来源腿算好的真档——Gelbooru 时间窗拦下的档不在表里）。 */
    rankingChoices: List<DesktopRankingChoice>,
    selectedRanking: GalleryRanking,
    onPickRanking: (GalleryRanking) -> Unit,
    /** 窗口读数（"~"）；不设窗口的两档为 null = 不念。 */
    periodReading: String?,
    canPeriodBack: Boolean,
    canPeriodForward: Boolean,
    onPeriodBack: () -> Unit,
    onPeriodForward: () -> Unit,
    // Pixiv 域控件（判据全在共享面 PixivSearchFilter，屏只摆档不判真假）。
    pixivTerm: String,
    onPixivTermChange: (String) -> Unit,
    pixivFilter: PixivSearchFilter,
    onPixivFilter: (PixivSearchFilter) -> Unit,
    pixivKind: Int,
    onPickPixivKind: (Int) -> Unit,
    /** 画师维度那一排结果（`searchUser` 直出的用户表，屏自渲染 —— 它不吃图墙）。 */
    pixivUsers: List<com.venera.compose.gallery.data.PixivUser>,
    onOpenArtist: (com.venera.compose.gallery.data.PixivUser) -> Unit,
    // ── 珍藏册维度（目标下拉第三项；四枚必传无默认，回调台账那把尺）──
    /** 珍藏册端口（壳传 `ports.pixivCollections`；`null` = 没装，收藏钮触发念成因）。 */
    collections: com.venera.compose.gallery.data.GalleryPixivCollections?,
    pixivCollectionCards: List<com.venera.compose.gallery.data.PixivCollectionCard>,
    pixivCollectionTotal: Int,
    /** 还有没有下一页（壳按 nextOffset 判；false = 那枚「更多」钮隐去，不摆假开关）。 */
    pixivCollectionHasMore: Boolean,
    onLoadMoreCollections: () -> Unit,
    onOpenCollection: (com.venera.compose.gallery.data.PixivCollectionCard) -> Unit,
    avatars: Map<String, String>,
    titles: Map<String, String>,
    searchR18: Boolean,
    onPickSearchR18: (Boolean) -> Unit,
    paginationMode: DesktopSearchPaginationMode,
    onPickPaginationMode: (DesktopSearchPaginationMode) -> Unit,
    currentPage: Int,
    canNextPage: Boolean,
    pageLoading: Boolean,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    onJumpPage: (Int) -> Unit,
    initialDropdownOpen: Boolean = false,
) {
    val tokens = VeneraTokens
    val colors = GalleryTokens.Color
    val isDark = LocalVeneraDarkTheme.current
    val density = androidx.compose.ui.platform.LocalDensity.current

    // ⭐️：高级筛选的侧栏默认隐藏，显示时带流畅过渡
    var inspectorOpen by rememberSaveable { mutableStateOf(false) }

    // 列数吃统一规格（同一张内容宽档位表 + 密度偏置），与主墙同读一档。
    val wallSpec = LocalDesktopWallSpec.current
    val windowWidth = LocalWindowWidth.current ?: 1280.dp
    val columns = remember(windowWidth.value, inspectorOpen, wallSpec.density, density) {
        val usableWidth = (windowWidth.value - 76f - (if (inspectorOpen) 284f else 0f) - 40f).coerceAtLeast(0f)
        wallSpec.columnsFor(with(density) { usableWidth.dp.toPx() }, GalleryColumnMode.AUTO)
    }

    // ⭐️ 真正悬浮式状态：平时绝不弹出、不占空间，点击搜索框才弹出
    var searchDropdownOpen by remember { mutableStateOf(initialDropdownOpen) }
    var searchInputFocused by remember { mutableStateOf(false) }
    var pagerInputEditing by remember { mutableStateOf(false) }

    val pagedActive = paginationMode == DesktopSearchPaginationMode.PAGED &&
        (searchMode != 1 || pixivKind == DESKTOP_PIXIV_SEARCH_WORKS)

    androidx.compose.runtime.LaunchedEffect(currentPage, pagedActive) {
        if (pagedActive) {
            runCatching { resultGridState.scrollToItem(0) }
        }
    }

    val currentTerm = if (searchMode == 1) pixivTerm else query
    val activeHistory = remember(history, searchMode) {
        if (searchMode == 1) {
            history.filter { it.sites.contains(GallerySite.PIXIV) }
        } else {
            history.filter { it.sites.any { s -> s != GallerySite.PIXIV } }
        }
    }

    // 页面不铺自己的底板，透出壳层全局底板；深浅两档都由壳层底板负责。
    Column(modifier = modifier.fillMaxSize()) {
        // ══ ① 顶部透明指挥调度栏 (Search Command Bar) ══
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(50f)
                .drawBehind {
                    drawLine(
                        color = colors.Divider,
                        start = Offset(0f, size.height - 0.5f),
                        end = Offset(size.width, size.height - 0.5f),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 模式分段器（图库 Booru ↔ Pixiv 专属）
                GallerySearchModeSwitch(searchMode = searchMode, onPickMode = onPickMode)

                // ⭐️ 集成式主搜索框包装区（包含正下方智能悬浮下拉层）
                Box(modifier = Modifier.weight(1f)) {
                    GallerySearchInputBox(
                        value = currentTerm,
                        placeholder = if (searchMode == 1) {
                            "标签 / 日文原名 / 中文译名 / 画师…"
                        } else {
                            "标签，空格分隔；减号开头是排除"
                        },
                        searching = searching,
                        onValueChange = { text ->
                            searchDropdownOpen = true
                            if (searchMode == 1) onPixivTermChange(text) else onQueryChange(text)
                        },
                        onClick = { searchDropdownOpen = true },
                        onFocusChanged = { focused ->
                            searchInputFocused = focused
                            if (focused) searchDropdownOpen = true
                        },
                        onSearch = {
                            searchDropdownOpen = false
                            onSearch()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // ⭐️ 悬浮式下拉层 (Popup)：完全悬浮在最上层，绝不往下撑开父容器，点击外部自动收起
                    val hasSuggestions = suggestCandidates.isNotEmpty() || suggestLine != null
                    val hasHistory = activeHistory.isNotEmpty()
                    if (searchDropdownOpen && (hasHistory || hasSuggestions || historyNote != null)) {
                        Popup(
                            alignment = Alignment.TopStart,
                            offset = IntOffset(0, with(density) { 44.dp.roundToPx() }),
                            onDismissRequest = { searchDropdownOpen = false },
                            properties = PopupProperties(focusable = false),
                        ) {
                            GallerySearchDropdownFlyout(
                                history = activeHistory,
                                historyNote = historyNote,
                                onPickHistory = { entry ->
                                    com.venera.desktop.settings.DesktopPixelBuddyBus.reportSearchConfirm(entry.query)
                                    if (searchMode == 1) onPixivTermChange(entry.query) else onQueryChange(entry.query)
                                    onPickHistory(entry)
                                },
                                onClearHistory = onClearHistory,
                                suggestCandidates = suggestCandidates,
                                suggestLine = suggestLine,
                                onPickSuggestion = { tag ->
                                    // ⭐️ 解决 Pixiv 点击联想词后输入框不显示的问题：立即填入对应模式的状态并触发
                                    com.venera.desktop.settings.DesktopPixelBuddyBus.reportSearchConfirm(tag)
                                    if (searchMode == 1) onPixivTermChange(tag) else onQueryChange(tag)
                                    onPickSuggestion(tag)
                                },
                                modifier = Modifier.widthIn(min = 400.dp, max = 560.dp),
                            )
                        }
                    }
                }

                // ⭐️：pixiv 域目标下拉（插画·漫画 / 小说（禁用画位）/ 珍藏册 / 用户）。
                if (searchMode == 1) {
                    PixivSearchTargetMenu(
                        selectedKind = pixivKind,
                        onPickKind = onPickPixivKind,
                    )
                }

                // 全屏唯一主行动按钮（36dp 等高、10dp 圆角、accent 主色）
                GallerySearchSubmitButton(
                    searching = searching,
                    enabled = currentTerm.isNotBlank() && !searching,
                    onClick = {
                        searchDropdownOpen = false
                        onSearch()
                    },
                )

                // 侧栏高级筛选开关
                GallerySearchInspectorToggle(
                    open = inspectorOpen,
                    activeFilters = if (searchMode == 1) pixivFilter.activeCount else 0,
                    onClick = { inspectorOpen = !inspectorOpen },
                )
            }

            // 快捷条：高频项平铺，长选项聚合进 Pop-up 下拉芯片，彻底消除右侧渐隐截断块
            GallerySearchQuickStrip(
                searchMode = searchMode,
                sources = sources,
                selectedSource = selectedSource,
                onPickSource = onPickSource,
                rankingChoices = rankingChoices,
                selectedRanking = selectedRanking,
                onPickRanking = onPickRanking,
                searchR18 = searchR18,
                onPickSearchR18 = onPickSearchR18,
                pixivFilter = pixivFilter,
                onPixivFilter = onPixivFilter,
                pixivKind = pixivKind,
                onPickPixivKind = onPickPixivKind,
                paginationMode = paginationMode,
                onPickPaginationMode = onPickPaginationMode,
            )
        }

        // ══ ② 主体：左「高级筛选检视栏」（流畅抽屉动效）+ 右结果墙 ══
        Row(modifier = Modifier.fillMaxSize()) {
            // ⭐️：流畅抽屉过渡展开/收起
            AnimatedVisibility(
                visible = inspectorOpen,
                enter = expandHorizontally(
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMediumLow,
                        dampingRatio = 0.82f,
                    ),
                    expandFrom = Alignment.Start,
                ) + fadeIn(animationSpec = tween(220)),
                exit = shrinkHorizontally(
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMediumLow,
                        dampingRatio = 0.82f,
                    ),
                    shrinkTowards = Alignment.Start,
                ) + fadeOut(animationSpec = tween(180)),
            ) {
                Column(
                    modifier = Modifier
                        .width(284.dp)
                        .fillMaxHeight()
                        .drawBehind {
                            drawLine(
                                color = colors.Divider,
                                start = Offset(size.width - 0.5f, 0f),
                                end = Offset(size.width - 0.5f, size.height),
                                strokeWidth = 1.dp.toPx(),
                            )
                        }
                        .verticalScroll(rememberScrollState())
                        .padding(
                            start = 20.dp,
                            end = 14.dp,
                            top = 14.dp,
                            bottom = 20.dp,
                        ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (searchMode == 1) {
                        DesktopPixivFilterInspector(
                            filter = pixivFilter,
                            onFilter = onPixivFilter,
                        )
                    } else {
                        DesktopBooruFilterInspector(
                            rankingChoices = rankingChoices,
                            selectedRanking = selectedRanking,
                            onPickRanking = onPickRanking,
                            periodReading = periodReading,
                            canPeriodBack = canPeriodBack,
                            canPeriodForward = canPeriodForward,
                            onPeriodBack = onPeriodBack,
                            onPeriodForward = onPeriodForward,
                            searchR18 = searchR18,
                            onPickSearchR18 = onPickSearchR18,
                        )
                    }
                }
            }

            // ⭐️ 右侧沉浸式结果墙：卡片位置稳定，模式切换带平滑淡入动效
            com.venera.desktop.ui.DesktopStaggeredAutoLoadEffect(
                state = resultGridState,
                columns = columns,
                // 分页模式与珍藏册维度不走触底自动续页。
                enabled = !pagedActive && footer.moreLabel != null &&
                    (searchMode != 1 || pixivKind == DESKTOP_PIXIV_SEARCH_WORKS),
                onLoadMore = onLoadMore,
            )
            com.venera.desktop.ui.DesktopListAutoLoadEffect(
                state = artistListState,
                enabled = footer.moreLabel != null && searchMode == 1 && pixivKind == DESKTOP_PIXIV_SEARCH_ARTIST,
                onLoadMore = onLoadMore,
            )
            com.venera.desktop.ui.components.PixelBuddyScrollInterruptEffect(
                resultGridState.isScrollInProgress || artistListState.isScrollInProgress,
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .onPreviewKeyEvent { event ->
                        if (
                            pagedActive &&
                            !searchInputFocused &&
                            !pagerInputEditing &&
                            !searching &&
                            !pageLoading &&
                            event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown
                        ) {
                            when (event.key) {
                                androidx.compose.ui.input.key.Key.DirectionLeft -> {
                                    if (currentPage > 1) {
                                        onPrevPage()
                                        true
                                    } else {
                                        false
                                    }
                                }
                                androidx.compose.ui.input.key.Key.DirectionRight -> {
                                    if (canNextPage) {
                                        onNextPage()
                                        true
                                    } else {
                                        false
                                    }
                                }
                                else -> false
                            }
                        } else {
                            false
                        }
                    }
                    .padding(
                        start = 20.dp,
                        end = 20.dp,
                        top = 14.dp,
                        bottom = 12.dp,
                    ),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 页尾那几句（正在取 / 到底了）
                    footer.lines.forEach { line ->
                        Text(
                            text = line,
                            fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                            color = colors.TextTertiary,
                            modifier = Modifier.padding(bottom = tokens.spacing.space1),
                        )
                    }

                    // ⭐️ 搜索卡片流畅过渡 (AnimatedContent)：三轴结果流
                    val streamKind = when {
                        searchMode != 1 -> 0
                        pixivKind == DESKTOP_PIXIV_SEARCH_ARTIST -> 1
                        pixivKind == DESKTOP_PIXIV_SEARCH_COLLECTION -> 2
                        else -> 0
                    }
                    AnimatedContent(
                        targetState = streamKind,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        transitionSpec = {
                            fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(160))
                        },
                        label = "searchStreamTransition",
                    ) { kind ->
                        if (kind == 1) {
                            androidx.compose.foundation.lazy.LazyColumn(
                                state = artistListState,
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(bottom = tokens.spacing.space6),
                            ) {
                                if (pixivUsers.isEmpty() && !searching) {
                                    item {
                                        VeneraEmptyView(
                                            title = "无结果",
                                            message = "换个画师名或关键词再试",
                                            tone = com.venera.compose.components.VeneraEmptyTone.Nothing,
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                                        )
                                    }
                                }
                                items(
                                    count = pixivUsers.size,
                                    key = { pixivUsers[it].id },
                                ) { index ->
                                    val user = pixivUsers[index]
                                    DesktopPixivSearchUserRow(
                                        user = user,
                                        isDark = isDark,
                                        onOpenArtist = { onOpenArtist(user) },
                                    )
                                }
                            }
                        } else if (kind == 2) {
                            PixivCollectionSearchRail(
                                collections = collections,
                                cards = pixivCollectionCards,
                                total = pixivCollectionTotal,
                                hasMore = pixivCollectionHasMore,
                                searching = searching,
                                failed = failed,
                                onRetry = onRetry,
                                onLoadMore = onLoadMoreCollections,
                                onOpenCollection = onOpenCollection,
                            )
                        } else {
                            val effectiveWallSpec = if (pagedActive) {
                                wallSpec.copy(layout = com.venera.compose.gallery.domain.GalleryWallLayout.Grid)
                            } else {
                                wallSpec
                            }
                            CompositionLocalProvider(LocalDesktopWallSpec provides effectiveWallSpec) {
                                GridPageTemplate(
                                    items = cards,
                                    columns = columns,
                                    itemKey = { card -> card.uid.ifBlank { card.url } },
                                    state = resultGridState,
                                    modifier = Modifier.fillMaxSize(),
                                    spacing = wallSpec.wallGap,
                                    contentPadding = PaddingValues(bottom = tokens.spacing.space6),
                                    emptyContent = {
                                        VeneraEmptyView(
                                            title = when {
                                                failed -> "加载失败"
                                                searching -> null
                                                else -> "无结果"
                                            },
                                            message = when {
                                                failed -> "请稍后重试"
                                                searching -> "搜索中…"
                                                else -> "换个关键词再试"
                                            },
                                            tone = if (failed) {
                                                com.venera.compose.components.VeneraEmptyTone.Failed
                                            } else {
                                                com.venera.compose.components.VeneraEmptyTone.Nothing
                                            },
                                            actionText = if (failed) "重试" else null,
                                            onAction = if (failed) onRetry else null,
                                            modifier = if (searching) {
                                                Modifier.fillMaxSize()
                                            } else {
                                                Modifier.fillMaxSize().registerPixelBuddyStatusAnchor(isRetry = failed)
                                            },
                                        )
                                    },
                                ) { card ->
                                    DesktopWallCard(
                                        card = card,
                                        onOpenPost = onOpenPost,
                                        titleOverride = titles[card.uid],
                                        avatarUrl = avatars[card.uid],
                                        onToggleSelect = null,
                                        onOpenArtist = onOpenCardArtist,
                                    )
                                }
                            }
                        }
                    }

                    if (pagedActive && (cards.isNotEmpty() || currentPage > 1 || pageLoading)) {
                        DesktopGallerySearchPagerBar(
                            currentPage = currentPage,
                            canNextPage = canNextPage,
                            loading = pageLoading || searching,
                            onPrevPage = onPrevPage,
                            onNextPage = onNextPage,
                            onJumpPage = onJumpPage,
                            onEditingPageChange = { pagerInputEditing = it },
                        )
                    }
                }

                // 悬浮回顶按钮
                com.venera.desktop.ui.DesktopBackToTopFab(
                    state = resultGridState,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(tokens.spacing.space4),
                )
                com.venera.desktop.ui.DesktopBackToTopFab(
                    state = artistListState,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(tokens.spacing.space4),
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// 核心组件：搜索框正下方弹出式智能浮层 (Search Dropdown Flyout)
// ═══════════════════════════════════════════════════════════════════

/**
 * 搜索框正下方的智能下拉浮层：统一列表行结构，集成「最近搜索」与「实时标签联想」。
 */

/** 图库 (Booru) 域高级筛选面板：排行周期步进 + 全年龄同步开关卡片。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DesktopBooruFilterInspector(
    rankingChoices: List<DesktopRankingChoice>,
    selectedRanking: GalleryRanking,
    onPickRanking: (GalleryRanking) -> Unit,
    periodReading: String?,
    canPeriodBack: Boolean,
    canPeriodForward: Boolean,
    onPeriodBack: () -> Unit,
    onPeriodForward: () -> Unit,
    searchR18: Boolean,
    onPickSearchR18: (Boolean) -> Unit,
) {
    val colors = GalleryTokens.Color

    if (rankingChoices.size > 1) {
        GalleryInspectorCard(title = "排行与时间窗", actionLabel = null, onAction = null) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                rankingChoices.forEach { choice ->
                    GalleryFilterChip(
                        label = choice.label,
                        selected = choice.ranking == selectedRanking,
                        onClick = { onPickRanking(choice.ranking) },
                    )
                }
            }
            periodReading?.let { reading ->
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        text = "统计周期",
                        fontSize = 11.5.sp,
                        color = colors.TextTertiary,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.SurfaceLevel2)
                            .border(1.dp, colors.OutlineHairline, RoundedCornerShape(8.dp))
                            .padding(3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        com.venera.desktop.ui.DesktopButton(
                            text = "← 上一期",
                            onClick = onPeriodBack,
                            size = com.venera.desktop.ui.DesktopButtonSize.Compact,
                            variant = com.venera.desktop.ui.DesktopButtonVariant.Ghost,
                            enabled = canPeriodBack,
                            horizontalPadding = 8.dp,
                        )
                        Text(
                            text = reading,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.TextPrimary,
                            maxLines = 1,
                        )
                        com.venera.desktop.ui.DesktopButton(
                            text = "下一期 →",
                            onClick = onPeriodForward,
                            size = com.venera.desktop.ui.DesktopButtonSize.Compact,
                            variant = com.venera.desktop.ui.DesktopButtonVariant.Ghost,
                            enabled = canPeriodForward,
                            horizontalPadding = 8.dp,
                        )
                    }
                }
            }
        }
    }

    GalleryInspectorCard(title = "分级过滤", actionLabel = null, onAction = null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .clickable { onPickSearchR18(!searchR18) }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GalleryCompactSwitch(!searchR18) { onPickSearchR18(!it) }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "全年龄检索",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.TextPrimary,
                )
                Text(
                    text = "自动追加站方非成人过滤参数",
                    fontSize = 11.5.sp,
                    color = colors.TextTertiary,
                )
            }
        }
    }
}

internal fun currentPresetDays(filter: PixivSearchFilter): Int? {
    val today = LocalDate.now()
    return PixivSearchFilter.DATE_PRESETS.filterNotNull().firstOrNull { days ->
        filter.from == today.minusDays((days - 1).toLong()) && filter.to == today
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PixivFilterChipRow(label: String, content: @Composable () -> Unit) {
    val colors = GalleryTokens.Color
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            text = label,
            fontSize = 11.5.sp,
            color = colors.TextTertiary,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) { content() }
    }
}

/** 画师搜索结果卡：44dp 圆形头像（浅色带微框）+ 信息列 + 右侧轻按钮。 */
@Composable
internal fun DesktopPixivSearchUserRow(
    user: com.venera.compose.gallery.data.PixivUser,
    isDark: Boolean,
    onOpenArtist: () -> Unit,
) {
    val colors = GalleryTokens.Color
    val shape = RoundedCornerShape(12.dp)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (!isDark) Modifier.vShadow(4.dp, shape) else Modifier)
            .hoverable(interaction)
            .clip(shape)
            .background(if (hovered) colors.SurfaceLevel2 else colors.SurfaceLevel1)
            .border(1.dp, if (hovered) colors.OutlineHover else colors.OutlineHairline, shape)
            .clickable(interactionSource = interaction, indication = null) { onOpenArtist() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        coil3.compose.AsyncImage(
            model = user.avatarUrl,
            contentDescription = user.name,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .border(1.dp, colors.ImageHairline, CircleShape),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = user.name,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (user.account.isNotBlank()) {
                Text(
                    text = "@${user.account}",
                    fontSize = 12.sp,
                    color = colors.TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(
            modifier = Modifier
                .height(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (hovered) colors.AccentSoft else colors.SurfaceLevel2)
                .border(1.dp, if (hovered) colors.OutlineSelected else colors.OutlineHairline, RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "查看作品 →",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (hovered) colors.AccentPrimary else colors.TextSecondary,
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// pixiv 搜索目标下拉 + 珍藏册结果卡带
// ═══════════════════════════════════════════════════════════════════

/**
 * pixiv 搜索目标下拉：收编至全仓标准 `DesktopPopUpButton`（含不可选置灰项）。
 */
@Composable
internal fun PixivSearchTargetMenu(selectedKind: Int, onPickKind: (Int) -> Unit) {
    val options = listOf(
        Triple("插画·漫画", DESKTOP_PIXIV_SEARCH_WORKS, true),
        Triple("小说", -1, false),
        Triple("珍藏册", DESKTOP_PIXIV_SEARCH_COLLECTION, true),
        Triple("用户", DESKTOP_PIXIV_SEARCH_ARTIST, true),
    )
    val selectedIndex = options.indexOfFirst { it.third && it.second == selectedKind }.coerceAtLeast(0)
    com.venera.desktop.ui.DesktopPopUpButton(
        label = null,
        value = options[selectedIndex].first,
        options = options.map { it.first },
        selectedIndex = selectedIndex,
        height = 32.dp,
        optionEnabled = options.map { it.third },
        onSelect = { idx ->
            val picked = options.getOrNull(idx) ?: return@DesktopPopUpButton
            if (picked.third && picked.second != selectedKind) onPickKind(picked.second)
        },
    )
}

/**
 * 珍藏册搜索结果卡带（240dp 拼贴卡横排 + 总数读数 + 「更多」把手）。
 * 收藏态读端口 `bookmarkedIds` 一帧（本机落盘快照，详情打开时以站方回填）；
 * 端口缺席 / 未登录时按下念成因（不假成功）。
 */
@Composable
internal fun PixivCollectionSearchRail(
    collections: com.venera.compose.gallery.data.GalleryPixivCollections?,
    cards: List<com.venera.compose.gallery.data.PixivCollectionCard>,
    total: Int,
    hasMore: Boolean,
    searching: Boolean,
    failed: Boolean,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenCollection: (com.venera.compose.gallery.data.PixivCollectionCard) -> Unit,
) {
    val colors = GalleryTokens.Color
    val tokens = VeneraTokens
    val scope = rememberCoroutineScope()
    val ux = com.venera.compose.components.LocalUxPlatformServices.current
    val likedIds = collections?.bookmarkedIds?.collectAsState()?.value ?: emptySet()

    val toggleLike: (com.venera.compose.gallery.data.PixivCollectionCard) -> Unit = { card ->
        val port = collections
        if (port == null) {
            ux.toast("收藏需要 pixiv 网页登录")
        } else {
            scope.launch {
                val liked = card.id in likedIds
                val result = if (liked) port.removeBookmark(card.id) else port.addBookmark(card.id, private0 = false)
                result.fold(
                    onSuccess = { ux.toast(if (liked) "已取消收藏" else "已收藏") },
                    onFailure = { t -> ux.toast("收藏没成功：${t.message?.take(80) ?: "网络或登录态问题"}") },
                )
            }
        }
    }

    if (cards.isEmpty()) {
        VeneraEmptyView(
            title = when {
                failed -> "加载失败"
                searching -> null
                else -> "无结果"
            },
            message = when {
                failed -> "请稍后重试"
                searching -> "搜索中…"
                else -> "换个标签再试"
            },
            tone = if (failed) {
                com.venera.compose.components.VeneraEmptyTone.Failed
            } else {
                com.venera.compose.components.VeneraEmptyTone.Nothing
            },
            actionText = if (failed) "重试" else null,
            onAction = if (failed) onRetry else null,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
    Column(modifier = Modifier.fillMaxSize().padding(bottom = tokens.spacing.space6)) {
        Text(
            text = "共 $total 本",
            fontSize = 12.sp,
            color = colors.TextTertiary,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(cards, key = { _, card -> card.id }) { index, card ->
                Box(Modifier.then(CollectionMotion.railStagger(index))) {
                    CollectionCoverCard(
                        card = card,
                        liked = card.id in likedIds,
                        onClick = { onOpenCollection(card) },
                        onToggleLike = toggleLike,
                        onOpenCreator = { onOpenCollection(card) },
                        width = 240.dp,
                    )
                }
            }
        }
        if (hasMore) {
            androidx.compose.material3.TextButton(onClick = onLoadMore) {
                Text("更多", color = colors.AccentPrimary, fontSize = 13.sp)
            }
        }
    }
}
