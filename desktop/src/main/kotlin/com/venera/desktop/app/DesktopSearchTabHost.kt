package com.venera.desktop.app

import com.venera.desktop.ui.DetailAndReaderUiState
import com.venera.desktop.ui.ExplorePick
import com.venera.desktop.ui.ExplorePickUiState
import com.venera.desktop.ui.FilesAuxUiState
import com.venera.desktop.ui.SourceManageUiState
import com.venera.desktop.ui.theme.VeneraType
import com.venera.desktop.ui.DesktopPushButton
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import com.venera.compose.feature.ComicDetailCore
import com.venera.compose.feature.HistoryScreenBody
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.shadow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.venera.desktop.ui.DesktopTitleBarAction
import com.venera.desktop.ui.DesktopWindowTitleBar
import com.venera.desktop.ui.GridPageTemplate
import com.venera.desktop.platform.DesktopPixivCallback
import com.venera.desktop.ui.components.DesktopPetCue
import com.venera.desktop.ui.components.DesktopPetCueBus
import com.venera.desktop.ui.components.DesktopPetPayload
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.components.VeneraNavTab
import com.venera.compose.components.VeneraSideBar
import com.venera.compose.components.VeneraSideBarGroup
import com.venera.compose.components.VeneraSideBarItem
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.desktop.ui.DesktopCommandBar
import com.venera.desktop.ui.DesktopPageWidth
import com.venera.desktop.ui.DesktopPageShell
import com.venera.desktop.ui.desktopPageMaxWidth
import com.venera.compose.components.sideBarModeFor
import com.venera.compose.components.venera.VeneraTextButton
import com.venera.compose.gallery.domain.GalleryHomeDomain
import com.venera.compose.gallery.domain.GalleryPreviewQuality
import com.venera.compose.components.wideScreenLayoutMode
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.desktop.favorites.DesktopFavoritesScreen
import com.venera.desktop.search.DesktopSearchScreen
import com.venera.desktop.search.DesktopComicSearchOutcome
import com.venera.desktop.search.DesktopComicSearchMore
import com.venera.desktop.search.desktopComicSearchCanLoadMore
import com.venera.desktop.search.desktopComicSearchEmptyNote
import com.venera.desktop.search.desktopComicSearchFailedFooter
import com.venera.desktop.search.desktopComicSearchHistoryChips
import com.venera.desktop.search.desktopComicSearchDropdownHistory
import com.venera.desktop.search.desktopComicSearchHistoryAppend
import com.venera.desktop.search.desktopComicSearchHistoryClearAll
import com.venera.desktop.search.desktopComicSearchHistoryNoteText
import com.venera.desktop.search.desktopComicSearchHistoryRead
import com.venera.desktop.search.desktopComicSearchHistoryStore
import com.venera.desktop.search.desktopComicSearchMoreState
import com.venera.desktop.search.desktopComicSearchReading
import com.venera.desktop.search.desktopComicSearchUnseen
import com.venera.desktop.sourcemanage.DesktopSourceManageScreen
import com.venera.desktop.sourcemanage.DesktopSourceLoginScreen
import com.venera.desktop.explore.DesktopExploreScreen
import com.venera.desktop.gallery.ui.DesktopReverseSearchScreen
import com.venera.desktop.gallery.ui.DesktopGalleryTopSearch
import com.venera.desktop.gallery.ui.DESKTOP_SUGGEST_LEG_WAIT_MS
import com.venera.desktop.gallery.ui.DESKTOP_SUGGEST_R18_LEG_WAIT_MS
import com.venera.desktop.gallery.ui.desktopSearchRatingSuffix
import com.venera.desktop.gallery.ui.DesktopGalleryFavoritesWallScreen
import com.venera.desktop.gallery.ui.DesktopGallerySavedScreen
import com.venera.desktop.gallery.ui.DesktopGallerySavedView
import com.venera.desktop.gallery.ui.desktopCanBrowseExternally
import com.venera.desktop.gallery.ui.openInBrowser
import com.venera.desktop.gallery.ui.desktopComicHistoryProgressLine
import com.venera.desktop.gallery.ui.desktopComicHistoryScreenReading
import com.venera.desktop.gallery.ui.desktopComicHistoryOpenSourceOf
import com.venera.desktop.gallery.ui.DesktopComicHistoryRowGate
import com.venera.desktop.gallery.ui.desktopComicHistoryRowGate
import com.venera.desktop.gallery.ui.desktopComicHistoryRowGateNote
import com.venera.desktop.gallery.ui.DesktopWallCard
import com.venera.desktop.gallery.ui.DESKTOP_PIXIV_SEARCH_WORKS
import com.venera.desktop.gallery.ui.DESKTOP_PIXIV_SEARCH_ARTIST
import com.venera.desktop.gallery.ui.DESKTOP_PIXIV_SEARCH_COLLECTION
import com.venera.desktop.gallery.ui.desktopPixivCollectionsSearchPage
import com.venera.desktop.gallery.ui.desktopPixivSearchFirstPage
import com.venera.desktop.gallery.ui.desktopPixivSearchUsersPage
import com.venera.desktop.gallery.ui.fetchPixivSearchNext
import com.venera.desktop.gallery.ui.desktopGallerySearchSourceOptions
import com.venera.desktop.gallery.ui.desktopRankingChoices
import com.venera.desktop.gallery.ui.desktopRankingLanding
import com.venera.desktop.gallery.ui.desktopRankingQuery
import com.venera.desktop.gallery.ui.desktopRankingAnchorReading
import com.venera.desktop.gallery.ui.desktopRankingStep
import com.venera.desktop.detail.DesktopCommentsScreen
import com.venera.desktop.detail.desktopCommentsInitialView
import com.venera.desktop.detail.desktopCommentsLoading
import com.venera.desktop.detail.desktopCommentsReceived
import com.venera.desktop.detail.desktopCommentsFailed
import com.venera.desktop.detail.desktopCommentsScreenReading
import com.venera.desktop.reader.DesktopReaderScreen
import com.venera.desktop.queue.DesktopComicQueueScreen
import com.venera.desktop.gallery.data.DesktopComicDownloadQueue
import com.venera.desktop.gallery.ui.desktopComicQueueRemovedLine
import com.venera.desktop.gallery.ui.desktopComicQueueStoppedLine
import com.venera.desktop.gallery.ui.desktopComicEpisodeDownloadNote
import com.venera.desktop.gallery.ui.desktopComicStartQueuePump
import com.venera.desktop.gallery.ui.DesktopWallCardModel
import com.venera.desktop.gallery.ui.DesktopWallPage
import com.venera.desktop.gallery.ui.DesktopGalleryHero
import com.venera.desktop.gallery.ui.DesktopGalleryMetrics
import com.venera.desktop.gallery.ui.DesktopGalleryChromeRow
import com.venera.desktop.gallery.ui.DesktopGalleryPixivisionArticleScreen
import com.venera.desktop.gallery.ui.DesktopGalleryRatingFilter
import com.venera.desktop.gallery.ui.DesktopWallDensity
import com.venera.desktop.gallery.ui.columnsFor
import com.venera.desktop.gallery.ui.DesktopGalleryTagDimensions
import com.venera.desktop.gallery.ui.desktopTagDimensionsOf
import com.venera.desktop.gallery.ui.desktopFilteredWallPage
import com.venera.desktop.gallery.ui.desktopHotTagsOf
import com.venera.desktop.gallery.ui.desktopWallFollowingCards
import com.venera.desktop.gallery.ui.DesktopGalleryWallHead
import com.venera.desktop.gallery.ui.DesktopGalleryMorePhase
import com.venera.desktop.gallery.ui.DesktopGalleryWallFooter
import com.venera.desktop.gallery.ui.desktopGalleryWallFooter
import com.venera.desktop.gallery.ui.desktopWallMoreAllowed
import com.venera.desktop.gallery.ui.desktopPixivWallFirstPage
import com.venera.desktop.gallery.ui.desktopPixivRecommendedFirstPage
import com.venera.desktop.gallery.ui.fetchPixivRecommendedNext
import androidx.compose.runtime.mutableStateMapOf
import com.venera.desktop.gallery.ui.desktopDailyHomeCards
import com.venera.desktop.gallery.ui.DesktopGalleryHomeRows
import com.venera.desktop.gallery.ui.fetchPixivWallPage
import com.venera.desktop.gallery.ui.desktopWallPageAfter
import com.venera.desktop.gallery.ui.fetchWallPage
import com.venera.desktop.gallery.data.desktopGallerySaveDir
import com.venera.desktop.gallery.data.desktopResolveSaveDir
import com.venera.desktop.gallery.data.desktopSaveImageRun
import com.venera.desktop.gallery.data.desktopSaveImageScreenReading
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.data.toPost
import com.venera.compose.gallery.domain.GallerySearchEntry
import com.venera.compose.gallery.domain.GallerySearchSource
import com.venera.desktop.gallery.ui.DesktopGallerySearchScreen
import com.venera.desktop.gallery.ui.desktopGelbooruConfigured
import com.venera.desktop.gallery.ui.desktopHistoryClearAll
import com.venera.desktop.gallery.ui.desktopHistoryLanding
import com.venera.desktop.gallery.ui.desktopHistoryPush
import com.venera.desktop.gallery.ui.desktopHistoryRead
import com.venera.desktop.gallery.ui.desktopHistoryStore
import com.venera.desktop.gallery.ui.desktopSearchHistoryEntry
import com.venera.desktop.gallery.ui.desktopWallFirstPage
import com.venera.desktop.gallery.ui.DesktopGallerySort
import com.venera.desktop.gallery.ui.desktopWallRecommendedFirstPage
import com.venera.desktop.gallery.ui.desktopWallSortQueries
import com.venera.desktop.gallery.ui.desktopWallSilentSites
import com.venera.desktop.gallery.ui.fetchSearchPage
import com.venera.desktop.gallery.ui.LocalDesktopSearchHandoff
import com.venera.desktop.gallery.ui.desktopSearchSeedFor
import com.venera.desktop.downloads.DesktopComicDownloadsScreen
import com.venera.desktop.downloads.DesktopLocalComicLibraryScreen
import com.venera.desktop.gallery.ui.DesktopComicDownloadListing
import com.venera.desktop.gallery.data.DesktopLocalComicLibraryScan
import com.venera.desktop.gallery.data.desktopLocalComics
import com.venera.desktop.gallery.data.desktopLocalComicLibraryReading
import com.venera.desktop.gallery.ui.DESKTOP_WALL_QUALITY
import com.venera.desktop.gallery.data.DesktopComicShelfRawIdIndex
import com.venera.desktop.gallery.data.DesktopComicLocalChapter
import com.venera.desktop.gallery.data.DesktopComicSourceOutcome
import com.venera.desktop.gallery.data.desktopComicOfflineLocalChapter
import com.venera.desktop.gallery.data.DesktopBrowseView
import com.venera.desktop.gallery.data.DesktopGalleryBrowseHistory
import com.venera.desktop.gallery.ui.boardOf
import com.venera.desktop.gallery.ui.desktopMergeSuggestionLegs
import com.venera.desktop.gallery.ui.desktopSuggestionReading
import com.venera.desktop.gallery.ui.desktopBrowsePostOfUid
import com.venera.desktop.gallery.ui.desktopBrowseRecoveryNote
import com.venera.desktop.gallery.ui.desktopWallGuardedCards
import com.venera.desktop.gallery.data.DesktopLocalComicImportOutcome
import com.venera.desktop.gallery.data.DesktopComicPage
import com.venera.desktop.gallery.data.desktopComicChapterPageFileName
import com.venera.desktop.gallery.data.desktopComicOfflineReadingNote
import com.venera.desktop.gallery.data.desktopLocalComicImport
import com.venera.desktop.gallery.data.desktopComicOfflineReadingReading
import com.venera.desktop.gallery.data.desktopComicOfflineShouldAskEngine
import com.venera.desktop.gallery.data.desktopComicReaderPages
import com.venera.desktop.gallery.data.desktopComicChaptersByComic
import com.venera.desktop.gallery.data.desktopComicBulkEmptyNote
import com.venera.desktop.gallery.data.desktopComicOfflineBadgeLabelOf
import com.venera.desktop.gallery.data.desktopComicOfflineBadges
import com.venera.desktop.gallery.data.desktopComicOfflineHitReading
import com.venera.desktop.gallery.data.desktopComicBulkEnqueueNote
import com.venera.desktop.gallery.data.desktopComicBulkRows
import com.venera.desktop.gallery.data.desktopFavoriteFolderActionLabel
import com.venera.desktop.gallery.data.desktopFavoriteFolderChoice
import com.venera.desktop.gallery.data.desktopFavoriteFolderNames
import com.venera.desktop.gallery.data.desktopFavoriteFolderOf
import com.venera.desktop.gallery.data.desktopFavoriteFolderReading
import com.venera.desktop.gallery.data.desktopFavoriteFolderCountNote
import com.venera.desktop.gallery.data.desktopFavoriteFolderFilterNote
import com.venera.desktop.gallery.data.desktopFavoriteFolderTabChoice
import com.venera.desktop.gallery.data.desktopFavoriteFolderTabReading
import com.venera.desktop.gallery.data.desktopFavoriteFolderTabRows
import com.venera.desktop.gallery.data.desktopFavoriteFoldersShown
import com.venera.desktop.gallery.data.desktopFavoriteRecordToWrite
import com.venera.desktop.gallery.data.desktopFavoriteRowOf
import com.venera.desktop.gallery.data.desktopComicDownloadedScan
import com.venera.desktop.gallery.data.desktopComicRepairChapter
import com.venera.desktop.gallery.data.desktopComicShelfRerollKey
import com.venera.desktop.gallery.ui.desktopComicDeleteChapter
import com.venera.desktop.gallery.ui.desktopComicDeleteRowKey
import com.venera.desktop.gallery.ui.desktopComicDeleteScreenLine
import com.venera.desktop.gallery.ui.desktopComicDeleteShouldFire
import com.venera.desktop.gallery.ui.desktopComicDeleteNextKey
import com.venera.desktop.gallery.ui.desktopComicDownloadedReading
import com.venera.desktop.gallery.ui.DesktopVeneraTheme
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState

@Composable
internal fun DesktopSearchTabPane(
    layoutMode: WideScreenLayoutMode,
    ports: GalleryPorts,
    galleryPaths: com.venera.compose.data.platform.PathProvider?,
    scope: CoroutineScope,
    desktopUx: DesktopUxPlatformServices,
    businessHandle: com.venera.desktop.business.DesktopBusinessPorts.Handle?,
    searchComics: (suspend (String, Int, String, List<String?>) -> DesktopComicSearchOutcome)?,
    detailUi: DetailAndReaderUiState,
    exploreCategoryBoard: com.venera.engineprobe.EngineSession.CategoryBoard,
    explorePickUi: ExplorePickUiState,
    aggregateSearch: com.venera.desktop.search.DesktopAggregateSearchHolder,
    searchFilterGroups: List<com.venera.engineprobe.EngineSession.SearchOption>,
    sourceUi: SourceManageUiState,
    filesUi: FilesAuxUiState,
    searchHistoryChips: List<String>,
    searchMoreState: com.venera.desktop.search.DesktopComicSearchMore,
    searchFailedFooter: String?,
    searchEmptyNote: String?,
    routeState: MutableState<DesktopRoute>,
    exploreSectionsState: MutableState<List<com.venera.engineprobe.EngineSession.Section>>,
    exploreNoteState: MutableState<String?>,
    exploreTickState: MutableIntState,
    exploreSourceKeyState: MutableState<String?>,
    exploreModesState: MutableState<List<String>>,
    exploreModeIndexState: MutableIntState,
    explorePickTickState: MutableIntState,
    explorePickMoreTickState: MutableIntState,
    explorePickPageNoState: MutableIntState,
    searchQueryState: MutableState<String>,
    discoverViewExploreState: MutableState<Boolean>,
    searchResultsState: MutableState<List<com.venera.engineprobe.EngineSession.ComicCard>>,
    searchingState: MutableState<Boolean>,
    searchTickState: MutableIntState,
    searchSourceKeyState: MutableState<String?>,
    searchFilterValuesState: MutableState<List<String?>>,
    searchMaxPageState: MutableState<Int?>,
    searchCanLoadMoreState: MutableState<Boolean>,
    hasSearchedState: MutableState<Boolean>,
    searchHistoryState: MutableState<List<String>>,
    searchHistoryNoteState: MutableState<String?>,
    searchCauseState: MutableState<String?>,
    loadMoreTickState: MutableIntState,
) {
    var route by routeState
    var exploreSections by exploreSectionsState
    var exploreNote by exploreNoteState
    var exploreTick by exploreTickState
    var exploreSourceKey by exploreSourceKeyState
    var exploreModes by exploreModesState
    var exploreModeIndex by exploreModeIndexState
    var explorePickTick by explorePickTickState
    var explorePickMoreTick by explorePickMoreTickState
    var explorePickPageNo by explorePickPageNoState
    var searchQuery by searchQueryState
    var discoverViewExplore by discoverViewExploreState
    var searchResults by searchResultsState
    var searching by searchingState
    var searchTick by searchTickState
    var searchSourceKey by searchSourceKeyState
    var searchFilterValues by searchFilterValuesState
    var searchMaxPage by searchMaxPageState
    var searchCanLoadMore by searchCanLoadMoreState
    var hasSearched by hasSearchedState
    var searchHistory by searchHistoryState
    var searchHistoryNote by searchHistoryNoteState
    var searchCause by searchCauseState
    var loadMoreTick by loadMoreTickState
                        // 当前那颗键与它的显示名（成品：从源清单现认）。清单没认到那颗时
                        // 显示名退回念键本身 —— 「认不到就冒充默认源的名字」是假话。
                        val currentSearchKey = searchSourceKey ?: DESKTOP_EXPLORE_SOURCE
                        // 视图档与速览选中态住这一分支的局部状态：离开这一格就清掉，
                        // 与「切 tab 清选中本」的规格同形，也不占壳顶层那颗 lambda 的捕获槽。
                        var searchViewMode by remember {
                            mutableStateOf(com.venera.desktop.search.readSearchViewMode(galleryPaths))
                        }
                        var peek by remember {
                            mutableStateOf(com.venera.desktop.search.DesktopPeekUiState())
                        }
                        // 源选择器那一排**跟着开启账走**（§158 改口，主人的裁决）：源管理开了什么
                        // 这里就能选什么；分级归遮罩那条链管（封面糊不糊），不归选择器管（能不能选）。
                        // 判据（enabled 过滤 + 原序）在纯件那颗里，这里只把它映射成屏要的形状。
                        val searchOptions = com.venera.desktop.search.desktopSearchSelectableSources(sourceUi.sourceRows)
                            .map { key ->
                                val row = sourceUi.sourceRows.first { it.key == key }
                                com.venera.desktop.search.DesktopSearchSourceOption(key, row.title)
                            }
                        val currentSearchLabel = sourceUi.sourceRows.firstOrNull { it.key == currentSearchKey }?.title
                            ?: currentSearchKey
                        val enabledSourceKeys = searchOptions.map { it.key }
                        val orderedAggregateLanes = com.venera.desktop.search.sortAggregateLanes(
                            lanes = aggregateSearch.lanes,
                            sourceOrder = enabledSourceKeys,
                        )
                        val effectiveSearching = if (aggregateSearch.target is com.venera.desktop.search.DesktopSearchTarget.Aggregate) {
                            aggregateSearch.searching
                        } else {
                            searching
                        }
                        // 速览装配整段抽到顶层函数：壳这颗大 lambda 的捕获表已近 253 槽上限，
                        // 任何局部 remember / lambda 都会把起窗推到 ClassFormatError（编译不报）。
                        val peekPane = com.venera.desktop.search.desktopSearchPeekPaneOf(
                            state = peek,
                            handle = businessHandle,
                            sourceKey = currentSearchKey,
                            sourceLabel = currentSearchLabel,
                            onStateChange = { next -> peek = next },
                            onDateLoaded = { id, time ->
                                peek = peek.copy(updateTimes = peek.updateTimes + (id to time))
                            },
                            onOpenFullDetail = { id, title, cover ->
                                detailUi.detailInfo = null
                                detailUi.detailNote = null
                                route = DesktopRoute.Detail(
                                    from = route,
                                    comicId = id,
                                    title = title,
                                    cover = cover,
                                    sourceKey = peek.sourceKey?.takeIf { it.isNotBlank() } ?: currentSearchKey,
                                )
                            },
                        )
                        val searchMaskedOf: (String, com.venera.engineprobe.EngineSession.ComicCard) -> Boolean = { sKey, card ->
                            desktopUx.coverMaskStateOf(
                                sourceKey = sKey,
                                title = card.title,
                                author = card.author.orEmpty(),
                                tags = card.tags,
                                comicId = card.id,
                            ) != "VISIBLE"
                        }
                        // 分离稿：这一格是选择器双页（搜索 / 探索），
                        // 两套管线状态仍全在壳里，屏只做排布与转发。
                        com.venera.desktop.search.DesktopDiscoverScreen(
                            mode = layoutMode,
                            sourceKey = currentSearchKey,
                            sourceLabel = currentSearchLabel,
                            sourceOptions = searchOptions,
                            searchTarget = aggregateSearch.target,
                            // 切到单源：落盘记住选择；有词且搜过则立刻切入该源深搜。
                            onPickSource = { key ->
                                petSource(searchOptions.firstOrNull { it.key == key }?.label.orEmpty())
                                val nextTarget = com.venera.desktop.search.DesktopSearchTarget.Single(key)
                                val changed = aggregateSearch.target != nextTarget || searchSourceKey != key
                                aggregateSearch.target = nextTarget
                                com.venera.desktop.search.writeSearchTarget(galleryPaths, nextTarget)
                                if (changed) {
                                    searchSourceKey = key
                                    searchCause = null
                                    if ((hasSearched || aggregateSearch.hasSearched) && searchQuery.isNotBlank()) {
                                        searchResults = emptyList()
                                        searching = true
                                        searchCanLoadMore = false
                                        searchMaxPage = null
                                        searchTick++
                                    }
                                }
                            },
                            onPickMultiSources = { keys ->
                                val next = com.venera.desktop.search.normalizeMultiSelection(keys, enabledSourceKeys)
                                if (next != null) {
                                    aggregateSearch.target = next
                                    com.venera.desktop.search.writeSearchTarget(galleryPaths, next)
                                    when (next) {
                                        is com.venera.desktop.search.DesktopSearchTarget.Single -> {
                                            searchSourceKey = next.sourceKey
                                            searchCause = null
                                            if (searchQuery.isNotBlank()) {
                                                searchResults = emptyList()
                                                searching = true
                                                searchCanLoadMore = false
                                                searchMaxPage = null
                                                searchTick++
                                            }
                                        }
                                        is com.venera.desktop.search.DesktopSearchTarget.Aggregate -> {
                                            if (searchQuery.isNotBlank()) {
                                                aggregateSearch.searchTick++
                                            } else {
                                                aggregateSearch.lanes = emptyMap()
                                                aggregateSearch.hasSearched = false
                                            }
                                        }
                                    }
                                }
                            },
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            onSearch = {
                                if (searchQuery.isNotBlank()) {
                                    if (aggregateSearch.target is com.venera.desktop.search.DesktopSearchTarget.Aggregate) {
                                        aggregateSearch.searchTick++
                                    } else {
                                        searchResults = emptyList()
                                        searching = true
                                        searchCause = null
                                        searchTick++
                                    }
                                }
                            },
                            results = searchResults,
                            searching = effectiveSearching,
                            // 屏顶那一行只留给"那一排记不住"：搜的成因有结果时进页尾、
                            // 没结果时进中间那一格，两处都不许与这一行同时念同一句。
                            note = searchHistoryNote,
                            maskedOf = { card -> searchMaskedOf(currentSearchKey, card) },
                            historyChips = searchHistoryChips,
                            dropdownHistory = desktopComicSearchDropdownHistory(searchHistory),
                            onPickHistory = { word ->
                                petSearch(word)
                                // 与对岸 `onPick = { viewModel.search(it) }` 同形：装回那个词**并立刻再搜一遍**，
                                // 不是只填输入框（只填不搜就是一枚按了要再按第二次的把手）。
                                searchQuery = word
                                if (aggregateSearch.target is com.venera.desktop.search.DesktopSearchTarget.Aggregate) {
                                    aggregateSearch.searchTick++
                                } else {
                                    searchResults = emptyList()
                                    searching = true
                                    searchCause = null
                                    searchTick++
                                }
                            },
                            onClearHistory = {
                                // 清空走 `remove`（对岸同一语义：删那个键，而不是写一份空串）。
                                // 本地**立刻**摘掉那一排，落盘在 IO 线程跟上 —— 等落盘再改口会让人
                                // 以为那枚把手按了没反应（与收藏「移出」同一手法）。
                                val cleared = searchHistory
                                searchHistory = emptyList()
                                scope.launch {
                                    val did = withContext(Dispatchers.IO) {
                                        runCatching {
                                            desktopComicSearchHistoryClearAll(desktopComicSearchHistoryStore(ports))
                                        }
                                    }
                                    searchHistoryNote = desktopComicSearchHistoryNoteText(
                                        did.exceptionOrNull()?.let {
                                            "${it.javaClass.simpleName} ${it.message?.take(120) ?: ""}"
                                        },
                                    )
                                    if (did.isFailure) searchHistory = cleared
                                }
                            },
                            moreState = searchMoreState,
                            onLoadMore = { loadMoreTick++ },
                            failedFooter = searchFailedFooter,
                            emptyNote = searchEmptyNote,
                            // ── 按源动态筛选：组与值都在壳里，屏只搬 ──
                            filterGroups = searchFilterGroups,
                            filterValues = searchFilterValues,
                            onFilterChange = { index, value ->
                                searchFilterValues = searchFilterValues.toMutableList().also {
                                    if (index in it.indices) it[index] = value
                                }
                            },
                            onApplyFilters = {
                                petFilter()
                                if (searchQuery.isNotBlank()) {
                                    searchResults = emptyList()
                                    searching = true
                                    searchCause = null
                                    searchTick++
                                }
                            },
                            onResetFilters = {
                                petFilter()
                                searchFilterValues = searchFilterGroups.map { it.defaultValue }
                                if (searchQuery.isNotBlank()) {
                                    searchResults = emptyList()
                                    searching = true
                                    searchCause = null
                                    searchTick++
                                }
                            },
                            viewMode = searchViewMode,
                            onViewModeChange = { next ->
                                searchViewMode = next
                                com.venera.desktop.search.writeSearchViewMode(galleryPaths, next)
                            },
                            onSelectCard = { card ->
                                peek = peek.copy(
                                    open = true,
                                    comicId = card.id,
                                    title = card.title,
                                    cover = card.cover,
                                    sourceKey = currentSearchKey,
                                    sourceLabel = currentSearchLabel,
                                )
                            },
                            selectedCardId = peek.comicId,
                            selectedCardSourceKey = peek.sourceKey,
                            dateOf = { id -> peek.updateTimes[id].orEmpty() },
                            peekPane = peekPane,
                            aggregateLanes = orderedAggregateLanes,
                            aggregateHasSearched = aggregateSearch.hasSearched,
                            aggregateOnlyHits = aggregateSearch.onlyHits,
                            aggregateSkippedSelfHosted = aggregateSearch.skippedSelfHosted,
                            onToggleAggregateOnlyHits = { aggregateSearch.onlyHits = !aggregateSearch.onlyHits },
                            aggregateMaskedOf = searchMaskedOf,
                            onSelectAggregateCard = { sKey, sLabel, card ->
                                peek = peek.copy(
                                    open = true,
                                    comicId = card.id,
                                    title = card.title,
                                    cover = card.cover,
                                    sourceKey = sKey,
                                    sourceLabel = sLabel,
                                )
                            },
                            onOpenAggregateDetail = { sKey, card ->
                                petWork(card.title)
                                detailUi.detailInfo = null
                                detailUi.detailNote = null
                                route = DesktopRoute.Detail(
                                    from = route,
                                    comicId = card.id,
                                    title = card.title,
                                    cover = card.cover,
                                    sourceKey = sKey,
                                )
                            },
                            onSearchTagInSource = { sKey, tag ->
                                val nextTarget = com.venera.desktop.search.DesktopSearchTarget.Single(sKey)
                                aggregateSearch.target = nextTarget
                                com.venera.desktop.search.writeSearchTarget(galleryPaths, nextTarget)
                                searchSourceKey = sKey
                                searchQuery = tag
                                searchResults = emptyList()
                                searching = true
                                searchCause = null
                                searchTick++
                            },
                            onDrillDownSource = { sKey ->
                                val nextTarget = com.venera.desktop.search.DesktopSearchTarget.Single(sKey)
                                aggregateSearch.target = nextTarget
                                com.venera.desktop.search.writeSearchTarget(galleryPaths, nextTarget)
                                searchSourceKey = sKey
                                searchCause = null
                                if (searchQuery.isNotBlank()) {
                                    searchResults = emptyList()
                                    searching = true
                                    searchCanLoadMore = false
                                    searchMaxPage = null
                                    searchTick++
                                }
                            },
                            onRetryAggregateSource = { sKey ->
                                aggregateSearch.retrySourceKey = sKey
                                aggregateSearch.retryTick++
                            },
                            onOpenResult = { card ->
                                petWork(card.title)
                                detailUi.detailInfo = null
                                detailUi.detailNote = null
                                route = DesktopRoute.Detail(
                                    from = route,
                                    comicId = card.id,
                                    title = card.title,
                                    cover = card.cover,
                                    // 挑过源之后那一本属于**那颗**源 —— 从前这里硬编默认键，
                                    // 换源搜出来的卡点进去会拿错源装载（详情整趟问的是别的站）。
                                    sourceKey = currentSearchKey,
                                )
                            },
                            // ═══ 发现阶段（探索作为同格第二页）═══
                            exploreSourceKey = exploreSourceKey ?: DESKTOP_EXPLORE_SOURCE,
                            exploreSourceChoices = sourceUi.sourceRows.filter { it.enabled }.map {
                                com.venera.desktop.explore.ExploreSourceChoice(key = it.key, name = it.title)
                            },
                            onPickExploreSource = { key ->
                                petSource(sourceUi.sourceRows.firstOrNull { it.key == key }?.title.orEmpty())
                                if (key != (exploreSourceKey ?: DESKTOP_EXPLORE_SOURCE)) {
                                    exploreSourceKey = key
                                    exploreSections = emptyList()
                                    // 换源：正在看的分类结果属于旧源，交回去（否则会串源）。
                                    explorePickUi.pick = null
                                    explorePickUi.page = null
                                    explorePickPageNo = 1
                                    explorePickUi.maxPage = null
                                }
                            },
                            exploreModes = exploreModes,
                            exploreSelectedMode = exploreModeIndex,
                            onPickExploreMode = { index ->
                                petFilter()
                                if (index != exploreModeIndex) {
                                    exploreModeIndex = index
                                    exploreSections = emptyList()
                                    exploreTick++
                                }
                            },
                            categoryParts = exploreCategoryBoard.parts,
                            rankingChips = exploreCategoryBoard.ranking,
                            onPickCategory = { partName, label, param, kind ->
                                petFilter()
                                explorePickUi.pick = ExplorePick(
                                    display = "$partName:$label",
                                    label = label,
                                    param = param,
                                    kind = kind,
                                )
                                explorePickTick++
                            },
                            onPickRanking = { key ->
                                petFilter()
                                val label = exploreCategoryBoard.ranking.firstOrNull { it.first == key }?.second ?: key
                                explorePickUi.pick = ExplorePick(
                                    display = "排行榜:$label",
                                    label = label,
                                    param = key,
                                    kind = "ranking",
                                )
                                explorePickTick++
                            },
                            pickedLabel = explorePickUi.pick?.display,
                            pickedPage = explorePickUi.page,
                            pickedLoadingMore = explorePickUi.loading,
                            pickedHasMore = (explorePickUi.maxPage ?: 1) > explorePickPageNo,
                            onLoadMorePicked = { explorePickMoreTick++ },
                            onClearPicked = {
                                explorePickUi.pick = null
                                explorePickUi.page = null
                                explorePickPageNo = 1
                                explorePickUi.maxPage = null
                            },
                            sections = exploreSections,
                            exploreNote = exploreNote,
                            onOpenCard = { card ->
                                petWork(card.title)
                                detailUi.detailInfo = null
                                detailUi.detailNote = null
                                // 点开的本子跟着**当前源**走（换源后探索墙全是那颗源的书）。
                                route = DesktopRoute.Detail(
                                    from = route,
                                    comicId = card.id,
                                    title = card.title,
                                    cover = card.cover,
                                    sourceKey = exploreSourceKey ?: DESKTOP_EXPLORE_SOURCE,
                                )
                            },
                            // 同格两页：右键搜标签只改查询与源，不再跨 tab 跳 —— 选择器自动落到搜索页。
                            onSearchComicTag = if (searchComics != null) {
                                { tag ->
                                    val targetKey = exploreSourceKey ?: DESKTOP_EXPLORE_SOURCE
                                    searchQuery = tag
                                    petSearch(tag)
                                    searchSourceKey = targetKey
                                    aggregateSearch.target = com.venera.desktop.search.DesktopSearchTarget.Single(targetKey)
                                    searchTick++
                                }
                            } else {
                                null
                            },
                            onReloadExplore = { DesktopPetCueBus.emit(DesktopPetCue.LOADING); exploreTick++ },
                            offlineBadgeOf = { card ->
                                desktopComicOfflineBadgeLabelOf(
                                    badges = filesUi.comicCardBadges,
                                    sourceKey = DESKTOP_EXPLORE_SOURCE,
                                    comicId = card.id,
                                )
                            },
                            // 选择器两档住壳（面包屑同源）；见 DesktopDiscoverScreen 头注。
                            viewExplore = discoverViewExplore,
                            onViewChange = { petSwitchPage(); discoverViewExplore = it },
                        )
}
