package com.venera.desktop.app

import com.venera.desktop.ui.BrowseClearUiState
import com.venera.desktop.ui.ExplorePickUiState
import com.venera.desktop.ui.GalleryAuxUiState
import com.venera.desktop.ui.PixivCollectionSearchLeg
import com.venera.desktop.ui.SourceManageUiState
import com.venera.desktop.data.desktopEngineCall
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
internal fun DesktopSearchAndExploreEffects(
    ports: GalleryPorts,
    engine: com.venera.engineprobe.EngineSession?,
    scope: CoroutineScope,
    galleryPaths: com.venera.compose.data.platform.PathProvider?,
    galleryPrefs: com.venera.desktop.gallery.data.DesktopGalleryPreferences?,
    currentMaskMode: String,
    maskModeOf: () -> String,
    wallGuardOf: () -> com.venera.compose.gallery.data.GalleryContentGuard?,
    wallBlockAiOf: () -> Boolean,
    searchComics: (suspend (String, Int, String, List<String?>) -> DesktopComicSearchOutcome)?,
    aggregateSearch: com.venera.desktop.search.DesktopAggregateSearchHolder,
    browseClearUi: BrowseClearUiState,
    explorePickUi: ExplorePickUiState,
    galleryAuxUi: GalleryAuxUiState,
    sourceUi: SourceManageUiState,
    pixivCollectionSearch: PixivCollectionSearchLeg,
    gallerySearchSourceOptions: List<com.venera.compose.gallery.domain.GallerySearchSource>,
    gallerySearchToday: java.time.LocalDate,
    wallPage: DesktopWallPage?,
    routeState: MutableState<DesktopRoute>,
    currentTabState: MutableState<VeneraNavTab>,
    sourceTickState: MutableIntState,
    searchQueryState: MutableState<String>,
    searchResultsState: MutableState<List<com.venera.engineprobe.EngineSession.ComicCard>>,
    searchingState: MutableState<Boolean>,
    searchTickState: MutableIntState,
    searchSourceKeyState: MutableState<String?>,
    searchFilterGroupsState: MutableState<List<com.venera.engineprobe.EngineSession.SearchOption>>,
    searchFilterValuesState: MutableState<List<String?>>,
    searchPageState: MutableIntState,
    searchMaxPageState: MutableState<Int?>,
    loadingMoreState: MutableState<Boolean>,
    searchCanLoadMoreState: MutableState<Boolean>,
    hasSearchedState: MutableState<Boolean>,
    searchHistoryState: MutableState<List<String>>,
    searchHistoryNoteState: MutableState<String?>,
    searchCauseState: MutableState<String?>,
    loadMoreTickState: MutableIntState,
    gallerySearchQueryState: MutableState<String>,
    gallerySearchSourceState: MutableState<Int>,
    gallerySearchPageState: MutableState<DesktopWallPage?>,
    gallerySearchPhaseState: MutableState<DesktopGalleryMorePhase>,
    gallerySearchSearchingState: MutableState<Boolean>,
    gallerySearchFailedState: MutableState<Boolean>,
    gallerySearchHistoryState: MutableState<List<GallerySearchEntry>>,
    gallerySearchHistoryNoteState: MutableState<String?>,
    gallerySearchTickState: MutableIntState,
    gallerySearchMoreTickState: MutableIntState,
    gallerySearchJobState: MutableState<kotlinx.coroutines.Job?>,
    gallerySearchModeState: MutableState<Int>,
    gallerySearchRankingState: MutableState<com.venera.compose.gallery.domain.GalleryRanking>,
    gallerySearchRankingAnchorState: MutableState<java.time.LocalDate?>,
    gallerySearchR18State: MutableState<Boolean>,
    gallerySuggestCandidatesState: MutableState<List<com.venera.compose.gallery.data.GalleryTagSuggestion>>,
    pixivSearchTermState: MutableState<String>,
    pixivSearchFilterState: MutableState<com.venera.compose.gallery.domain.PixivSearchFilter>,
    pixivSearchKindState: MutableIntState,
    pixivSearchPageState: MutableState<DesktopWallPage?>,
    pixivSearchPhaseState: MutableState<DesktopGalleryMorePhase>,
    pixivSearchSearchingState: MutableState<Boolean>,
    pixivSearchTickState: MutableIntState,
    pixivSearchMoreTickState: MutableIntState,
    pixivSearchUsersState: MutableState<List<com.venera.compose.gallery.data.PixivUser>>,
    pixivSearchUsersCursorState: MutableState<String?>,
    exploreSectionsState: MutableState<List<com.venera.engineprobe.EngineSession.Section>>,
    exploreNoteState: MutableState<String?>,
    exploreTickState: MutableIntState,
    exploreSourceKeyState: MutableState<String?>,
    exploreModesState: MutableState<List<String>>,
    exploreModeIndexState: MutableIntState,
    exploreCategoryBoardState: MutableState<com.venera.engineprobe.EngineSession.CategoryBoard>,
    explorePickTickState: MutableIntState,
    explorePickMoreTickState: MutableIntState,
    explorePickPageNoState: MutableIntState,
    homeRecommendComicsState: MutableState<List<com.venera.engineprobe.EngineSession.ComicCard>>,
    homeRecommendNoteState: MutableState<String?>,
    homeRecommendR18State: MutableState<Boolean>,
) {
    var route by routeState
    var currentTab by currentTabState
    var sourceTick by sourceTickState
    var searchQuery by searchQueryState
    var searchResults by searchResultsState
    var searching by searchingState
    var searchTick by searchTickState
    var searchSourceKey by searchSourceKeyState
    var searchFilterGroups by searchFilterGroupsState
    var searchFilterValues by searchFilterValuesState
    var searchPage by searchPageState
    var searchMaxPage by searchMaxPageState
    var loadingMore by loadingMoreState
    var searchCanLoadMore by searchCanLoadMoreState
    var hasSearched by hasSearchedState
    var searchHistory by searchHistoryState
    var searchHistoryNote by searchHistoryNoteState
    var searchCause by searchCauseState
    var loadMoreTick by loadMoreTickState
    var gallerySearchQuery by gallerySearchQueryState
    var gallerySearchSource by gallerySearchSourceState
    var gallerySearchPage by gallerySearchPageState
    var gallerySearchPhase by gallerySearchPhaseState
    var gallerySearchSearching by gallerySearchSearchingState
    var gallerySearchFailed by gallerySearchFailedState
    var gallerySearchHistory by gallerySearchHistoryState
    var gallerySearchHistoryNote by gallerySearchHistoryNoteState
    var gallerySearchTick by gallerySearchTickState
    var gallerySearchMoreTick by gallerySearchMoreTickState
    var gallerySearchJob by gallerySearchJobState
    var gallerySearchMode by gallerySearchModeState
    var gallerySearchRanking by gallerySearchRankingState
    var gallerySearchRankingAnchor by gallerySearchRankingAnchorState
    var gallerySearchR18 by gallerySearchR18State
    var gallerySuggestCandidates by gallerySuggestCandidatesState
    var pixivSearchTerm by pixivSearchTermState
    var pixivSearchFilter by pixivSearchFilterState
    var pixivSearchKind by pixivSearchKindState
    var pixivSearchPage by pixivSearchPageState
    var pixivSearchPhase by pixivSearchPhaseState
    var pixivSearchSearching by pixivSearchSearchingState
    var pixivSearchTick by pixivSearchTickState
    var pixivSearchMoreTick by pixivSearchMoreTickState
    var pixivSearchUsers by pixivSearchUsersState
    var pixivSearchUsersCursor by pixivSearchUsersCursorState
    var exploreSections by exploreSectionsState
    var exploreNote by exploreNoteState
    var exploreTick by exploreTickState
    var exploreSourceKey by exploreSourceKeyState
    var exploreModes by exploreModesState
    var exploreModeIndex by exploreModeIndexState
    var exploreCategoryBoard by exploreCategoryBoardState
    var explorePickTick by explorePickTickState
    var explorePickMoreTick by explorePickMoreTickState
    var explorePickPageNo by explorePickPageNoState
    var homeRecommendComics by homeRecommendComicsState
    var homeRecommendNote by homeRecommendNoteState
    var homeRecommendR18 by homeRecommendR18State
    LaunchedEffect(currentTab, sourceUi.sourceRows, galleryPaths) {
        if (currentTab != VeneraNavTab.SEARCH) return@LaunchedEffect
        val enabledKeys = com.venera.desktop.search.desktopSearchSelectableSources(sourceUi.sourceRows)
        if (enabledKeys.isEmpty()) return@LaunchedEffect
        val base = if (!aggregateSearch.hydrated) {
            aggregateSearch.hydrated = true
            com.venera.desktop.search.readSearchTarget(galleryPaths)
        } else {
            aggregateSearch.target
        }
        val resolved = com.venera.desktop.search.resolveSearchTarget(base, enabledKeys)
        aggregateSearch.target = resolved
        if (resolved is com.venera.desktop.search.DesktopSearchTarget.Single) {
            searchSourceKey = resolved.sourceKey
        }
    }

    // 多源聚合流式检索：复用单引擎锁逐源执行并设单源超时，每完一源立刻刷新该泳道。
    LaunchedEffect(aggregateSearch.searchTick) {
        if (aggregateSearch.searchTick == 0) return@LaunchedEffect
        val doSearch = searchComics ?: return@LaunchedEffect
        val word = searchQuery.trim()
        if (word.isBlank()) return@LaunchedEffect
        val aggTarget = aggregateSearch.target as? com.venera.desktop.search.DesktopSearchTarget.Aggregate
            ?: return@LaunchedEffect
        val enabledRows = sourceUi.sourceRows.filter { it.enabled }
        val targetPlan = com.venera.desktop.search.planAggregateTargets(
            enabledRows = enabledRows,
            aggTarget = aggTarget,
            keyOf = { it.key },
            isUnconfiguredSelfHosted = { sourceKey ->
                val store = engine?.host?.dataStoreOf()
                if (store == null) {
                    false
                } else {
                    com.venera.desktop.search.isUnconfiguredSelfHostedSource(sourceKey) { sKey, settingKey ->
                        store.loadSetting(sKey, settingKey)?.toString()
                    }
                }
            },
        )
        val targets = targetPlan.active
        val appended = withContext(Dispatchers.IO) {
            runCatching {
                desktopComicSearchHistoryAppend(desktopComicSearchHistoryStore(ports), word)
            }
        }
        appended.getOrNull()?.let { searchHistory = it }
        searchHistoryNote = desktopComicSearchHistoryNoteText(
            appended.exceptionOrNull()?.let {
                "${it.javaClass.simpleName} ${it.message?.take(120) ?: ""}"
            },
        )
        val startMs = System.currentTimeMillis()
        aggregateSearch.searching = true
        aggregateSearch.hasSearched = true
        aggregateSearch.skippedSelfHosted = targetPlan.skippedSelfHosted
        aggregateSearch.lanes = targets.associate { row ->
            row.key to com.venera.desktop.search.DesktopAggregateLaneState(
                sourceKey = row.key,
                sourceLabel = row.title,
                status = com.venera.desktop.search.AggregateLaneStatus.Loading,
            )
        }
        com.venera.desktop.search.DesktopAggregateSearchRunner.run(
            query = word,
            targets = targets.map { it.key to it.title },
            engine = engine,
            fallbackSearch = doSearch,
            onLaneComplete = { nextLane ->
                aggregateSearch.lanes = aggregateSearch.lanes + (nextLane.sourceKey to nextLane)
                if (nextLane.status == com.venera.desktop.search.AggregateLaneStatus.Failed &&
                    !nextLane.error.isNullOrBlank()
                ) {
                    com.venera.desktop.data.DesktopErrorBus.report(
                        category = "聚合搜索",
                        headline = "${nextLane.sourceLabel} 检索失败",
                        detail = nextLane.error,
                        dedupKey = "agg:${nextLane.sourceKey}:${nextLane.error.take(40)}",
                    )
                }
            },
        )
        aggregateSearch.searching = false
        val hitLanes = aggregateSearch.lanes.values.count { it.status == com.venera.desktop.search.AggregateLaneStatus.Ready }
        val costMs = System.currentTimeMillis() - startMs
        val skippedNote = if (targetPlan.skippedSelfHosted.isEmpty()) "" else " 跳过未配置私服=${targetPlan.skippedSelfHosted.joinToString(",")}"
        println("D_聚合搜索 词=$word 源=${targets.size} 命中源=$hitLanes 总本=${aggregateSearch.lanes.values.sumOf { it.cards.size }} 耗时=${costMs}ms$skippedNote")
    }

    // 聚合泳道单源局部重试：仅重跑目标源并写回该泳道，不动已就绪源。
    LaunchedEffect(aggregateSearch.retryTick) {
        if (aggregateSearch.retryTick == 0) return@LaunchedEffect
        val key = aggregateSearch.retrySourceKey ?: return@LaunchedEffect
        val doSearch = searchComics ?: return@LaunchedEffect
        val word = searchQuery.trim()
        if (word.isBlank()) return@LaunchedEffect
        val title = sourceUi.sourceRows.firstOrNull { it.key == key }?.title ?: key
        aggregateSearch.lanes = aggregateSearch.lanes + (
            key to com.venera.desktop.search.DesktopAggregateLaneState(
                sourceKey = key,
                sourceLabel = title,
                status = com.venera.desktop.search.AggregateLaneStatus.Loading,
            )
        )
        val got = withContext(Dispatchers.IO) {
            runCatching {
                kotlinx.coroutines.withTimeout(15_000L) {
                    doSearch(word, 1, key, emptyList())
                }
            }
        }
        val nextLane = got.fold(
            onSuccess = { outcome ->
                when {
                    outcome.sourceError != null -> com.venera.desktop.search.DesktopAggregateLaneState(
                        sourceKey = key,
                        sourceLabel = title,
                        status = com.venera.desktop.search.AggregateLaneStatus.Failed,
                        error = outcome.sourceError,
                    )
                    outcome.cards.isEmpty() -> com.venera.desktop.search.DesktopAggregateLaneState(
                        sourceKey = key,
                        sourceLabel = title,
                        status = com.venera.desktop.search.AggregateLaneStatus.Empty,
                    )
                    else -> com.venera.desktop.search.DesktopAggregateLaneState(
                        sourceKey = key,
                        sourceLabel = title,
                        status = com.venera.desktop.search.AggregateLaneStatus.Ready,
                        cards = outcome.cards,
                    )
                }
            },
            onFailure = { t ->
                if (t is kotlinx.coroutines.CancellationException && t !is kotlinx.coroutines.TimeoutCancellationException) {
                    throw t
                }
                val reason = if (t is kotlinx.coroutines.TimeoutCancellationException) {
                    "检索超时（15s），可单独重试"
                } else {
                    "检索失败：${t.javaClass.simpleName} ${t.message?.take(120).orEmpty()}".trim()
                }
                com.venera.desktop.search.DesktopAggregateLaneState(
                    sourceKey = key,
                    sourceLabel = title,
                    status = com.venera.desktop.search.AggregateLaneStatus.Failed,
                    error = reason,
                )
            },
        )
        aggregateSearch.lanes = aggregateSearch.lanes + (key to nextLane)
    }

    // 搜索按 searchTick 触发：发车前先落历史、页码归 1、保留 sourceError；换源时重读 optionList 并重置筛选重搜。
    LaunchedEffect(currentTab, searchSourceKey, sourceTick, aggregateSearch.target) {
        if (currentTab != VeneraNavTab.SEARCH) return@LaunchedEffect
        if (aggregateSearch.target is com.venera.desktop.search.DesktopSearchTarget.Aggregate) {
            searchFilterGroups = emptyList()
            searchFilterValues = emptyList()
            return@LaunchedEffect
        }
        val e = engine ?: return@LaunchedEffect
        val key = searchSourceKey ?: DESKTOP_EXPLORE_SOURCE
        val groups = withContext(Dispatchers.IO) {
            desktopEngineCall(e) {
                e.load(key)
                e.searchOptions()
            }.getOrElse { emptyList() }
        }
        searchFilterGroups = groups
        searchFilterValues = groups.map { it.defaultValue }
        println("D_搜索筛选 源=$key 组=${groups.size} 值=${searchFilterValues.size}")
        if (hasSearched && searchQuery.isNotBlank()) {
            searchTick++
        }
    }

    LaunchedEffect(searchTick) {
        if (searchTick == 0) return@LaunchedEffect
        val doSearch = searchComics ?: return@LaunchedEffect
        val word = searchQuery.trim()
        // 写搜索历史采用现读盘再追加写回，避免启动初期内存空表覆盖已有历史；写失败仅提示不阻断搜索。
        val appended = withContext(Dispatchers.IO) {
            runCatching {
                desktopComicSearchHistoryAppend(desktopComicSearchHistoryStore(ports), word)
            }
        }
        appended.getOrNull()?.let { searchHistory = it }
        searchHistoryNote = desktopComicSearchHistoryNoteText(
            appended.exceptionOrNull()?.let {
                "${it.javaClass.simpleName} ${it.message?.take(120) ?: ""}"
            },
        )
        searching = true
        hasSearched = true
        searchPage = 1
        searchMaxPage = null
        searchCanLoadMore = false
        searchCause = null
        val got = withContext(Dispatchers.IO) {
            runCatching {
                kotlinx.coroutines.withTimeout(15_000L) {
                    doSearch(word, 1, searchSourceKey ?: DESKTOP_EXPLORE_SOURCE, searchFilterValues)
                }
            }
        }
        searching = false
        got.fold(
            onSuccess = { outcome ->
                // 真·空结果才报，失败/加载中不误报。
                if (outcome.cards.isEmpty() && outcome.sourceError == null) {
                    DesktopPetCueBus.emit(DesktopPetCue.SEARCH_EMPTY)
                }
                searchResults = outcome.cards
                searchMaxPage = outcome.maxPage
                searchCanLoadMore = desktopComicSearchCanLoadMore(
                    declaredMaxPage = outcome.maxPage,
                    pageJustFetched = 1,
                    unseenItemsOnPage = outcome.cards.size,
                )
                // 站方答了而答的是失败：这一趟的成因，交页尾/中间那两颗成品各念一句。
                searchCause = outcome.sourceError
                outcome.sourceError?.takeIf { it.isNotBlank() }?.let { raw ->
                    println("D_搜索 源报错 ${raw.take(200)}")
                    com.venera.desktop.data.DesktopErrorBus.report(
                        category = "漫画",
                        headline = "搜索被源站拒绝",
                        humanReason = "源站回的是失败而不是空结果，换词重搜不会变好；先确认该源是否可用。",
                        detail = "${searchSourceKey ?: DESKTOP_EXPLORE_SOURCE}: $raw",
                        dedupKey = "comic:search_denied:${searchSourceKey ?: DESKTOP_EXPLORE_SOURCE}",
                    )
                }
            },
            onFailure = { t ->
                searchResults = emptyList()
                searchCanLoadMore = false
                // 原文只进日志与错误面板详情；屏上念归档短句（握手被切断 ⇒ 网络受阻 · 需配置代理后重试）。
                println("D_搜索 原始失败 ${t.javaClass.simpleName}: ${t.message?.take(160) ?: ""}")
                com.venera.desktop.data.DesktopErrorBus.report(
                    category = "漫画",
                    headline = "漫画搜索失败",
                    humanReason = "这一次搜索请求没打通，换词重搜不会变好；先看网络或换源。",
                    detail = "${t.javaClass.simpleName}: ${t.message.orEmpty()}",
                    dedupKey = "comic:search_fail:${searchSourceKey ?: DESKTOP_EXPLORE_SOURCE}",
                )
                searchCause = com.venera.desktop.platform.desktopFailureNoteOf(t)
            },
        )
        println(
            desktopComicSearchReading(
                query = word,
                page = searchPage,
                declaredMaxPage = searchMaxPage,
                resultCount = searchResults.size,
                canLoadMore = searchCanLoadMore,
                historyCount = searchHistory.size,
                reason = searchCause,
            ),
        )
    }

    // 切回搜索 Tab 时重读最近搜索历史；读失败保持空表并提示原因，不伪装成无历史。
    LaunchedEffect(currentTab) {
        if (currentTab != VeneraNavTab.SEARCH) return@LaunchedEffect
        val result = withContext(Dispatchers.IO) {
            runCatching { desktopComicSearchHistoryRead(desktopComicSearchHistoryStore(ports)) }
        }
        result.fold(
            onSuccess = {
                searchHistory = it
                searchHistoryNote = null
            },
            onFailure = { t ->
                searchHistory = emptyList()
                searchHistoryNote = desktopComicSearchHistoryNoteText(
                    "${t.javaClass.simpleName} ${t.message?.take(120) ?: ""}",
                )
            },
        )
    }

    // 加载更多以 loadMoreTick 为键（失败重试时页码不变仍可重发），并拦截未发车、到底与并发中状态。
    LaunchedEffect(loadMoreTick) {
        if (loadMoreTick == 0) return@LaunchedEffect
        val doSearch = searchComics ?: return@LaunchedEffect
        if (!hasSearched || !searchCanLoadMore || searching || loadingMore) return@LaunchedEffect
        val nextPage = searchPage + 1
        loadingMore = true
        searchCause = null
        val got = withContext(Dispatchers.IO) {
            // 续页跟着**当前那颗源**走（挑过源再翻页不许漂回默认站）。
            runCatching { doSearch(searchQuery.trim(), nextPage, searchSourceKey ?: DESKTOP_EXPLORE_SOURCE, searchFilterValues) }
        }
        loadingMore = false
        got.fold(
            onSuccess = { outcome ->
                // 先排重、再算翻不翻得动（顺序由对岸 `SearchViewModel.kt:441-455` 定，
                // 反过来会把"这一页被剔空"误判成"到底"）。
                val unseen = desktopComicSearchUnseen(searchResults, outcome.cards)
                searchResults = searchResults + unseen
                searchMaxPage = outcome.maxPage
                searchCanLoadMore = desktopComicSearchCanLoadMore(
                    declaredMaxPage = outcome.maxPage,
                    pageJustFetched = nextPage,
                    unseenItemsOnPage = unseen.size,
                )
                // 续页翻到头才报，失败/空结果不误报。
                if (!searchCanLoadMore && outcome.sourceError == null && searchResults.isNotEmpty()) {
                    DesktopPetCueBus.emit(DesktopPetCue.LIST_END)
                }
                // 页号只在**真有新条目**时才推进（对岸 `:457` 同一判据）：
                // 越界把末页原样回吐的那类源，推页号就是拿同一页反复要。
                if (outcome.cards.isNotEmpty()) searchPage = nextPage
                searchCause = outcome.sourceError
            },
            onFailure = { t ->
                searchCanLoadMore = false
                println("D_搜索 原始翻页失败 ${t.javaClass.simpleName}: ${t.message?.take(160) ?: ""}")
                com.venera.desktop.data.DesktopErrorBus.report(
                    category = "漫画",
                    headline = "漫画搜索翻页失败",
                    humanReason = "翻下一页时请求断了，已取到的结果仍在；可再点一次「加载更多」。",
                    detail = "${t.javaClass.simpleName}: ${t.message.orEmpty()}",
                    dedupKey = "comic:search_more_fail:${searchSourceKey ?: DESKTOP_EXPLORE_SOURCE}",
                )
                searchCause = com.venera.desktop.platform.desktopFailureNoteOf(t)
            },
        )
        println(
            desktopComicSearchReading(
                query = searchQuery.trim(),
                page = searchPage,
                declaredMaxPage = searchMaxPage,
                resultCount = searchResults.size,
                canLoadMore = searchCanLoadMore,
                historyCount = searchHistory.size,
                reason = searchCause,
            ),
        )
    }

    LaunchedEffect(gallerySearchQuery, gallerySearchSource, route) {
        val open = route is DesktopRoute.GallerySearch
        val word = gallerySearchQuery.trim()
        if (!open || gallerySearchMode != 0 || word.isEmpty() || gallerySearchSearching) {
            galleryAuxUi.gallerySuggestTerm = ""
            gallerySuggestCandidates = emptyList()
            galleryAuxUi.gallerySuggestLine = null
            galleryAuxUi.gallerySuggestLoading = false
            return@LaunchedEffect
        }
        delay(250)
        // 防抖后再读一次当前词：这 250ms 里用户可能又改了字，问一个过期前缀的补全是空转。
        val nowWord = gallerySearchQuery.trim()
        if (nowWord.isEmpty() || route !is DesktopRoute.GallerySearch) return@LaunchedEffect
        val source =
            gallerySearchSourceOptions[gallerySearchSource.coerceIn(0, gallerySearchSourceOptions.lastIndex)]
        val legs = source.availableLegs(desktopGelbooruConfigured(ports))
        if (legs.isEmpty()) return@LaunchedEffect
        galleryAuxUi.gallerySuggestLoading = true
        // 腿并行 + 每腿独立超时：串行 map 时一条挂死腿（R18 过盾桥冷启动可达数十秒）会把
        // 整排候选拖到不上屏；到点放弃的腿按失败入账，屏上「部分来源暂不可用」照常点名。
        val outcome = withContext(Dispatchers.IO) {
            runCatching {
                coroutineScope {
                    legs.map { site ->
                        async {
                            val waitMs = if (site == GallerySite.KONACHAN_R18) {
                                DESKTOP_SUGGEST_R18_LEG_WAIT_MS
                            } else {
                                DESKTOP_SUGGEST_LEG_WAIT_MS
                            }
                            val got = withTimeoutOrNull(waitMs) {
                                boardOf(ports, site).searchTags(nowWord)
                            }
                            site to (got ?: Result.failure(IllegalStateException("联想腿超时")))
                        }
                    }.map { it.await() }
                }
            }
        }
        galleryAuxUi.gallerySuggestLoading = false
        val merged = outcome.getOrNull()?.let {
            desktopMergeSuggestionLegs(nowWord, it, limit = 8)
        }
        if (merged == null) {
            // 整趟取补全塌了（不是某腿塌，是这趟编排本身抛）：读数交 null 而不是编一句假失败。
            galleryAuxUi.gallerySuggestTerm = nowWord
            gallerySuggestCandidates = emptyList()
            galleryAuxUi.gallerySuggestLine = null
        } else {
            galleryAuxUi.gallerySuggestTerm = nowWord
            gallerySuggestCandidates = merged.candidates
            galleryAuxUi.gallerySuggestLine = desktopSuggestionReading(nowWord, merged)
        }
    }

    // ── ：Pixiv 专属搜索关联词 (AutoComplete) 联想 ──
    LaunchedEffect(pixivSearchTerm, gallerySearchMode, route) {
        val open = route is DesktopRoute.GallerySearch
        val word = pixivSearchTerm.trim()
        if (!open || gallerySearchMode != 1 || word.isEmpty() || gallerySearchSearching) {
            if (gallerySearchMode == 1) {
                galleryAuxUi.gallerySuggestTerm = ""
                gallerySuggestCandidates = emptyList()
                galleryAuxUi.gallerySuggestLine = null
                galleryAuxUi.gallerySuggestLoading = false
            }
            return@LaunchedEffect
        }
        delay(250)
        val nowWord = pixivSearchTerm.trim()
        if (nowWord.isEmpty() || route !is DesktopRoute.GallerySearch || gallerySearchMode != 1) return@LaunchedEffect
        galleryAuxUi.gallerySuggestLoading = true
        val engine = galleryPaths?.let { com.venera.desktop.gallery.data.DesktopGalleryHandle.of(it).engine }
        val suggestions = com.venera.desktop.gallery.data.DesktopPixivAutocomplete.query(
            engine = engine,
            word = nowWord,
            limit = 8,
        )
        galleryAuxUi.gallerySuggestLoading = false
        galleryAuxUi.gallerySuggestTerm = nowWord
        gallerySuggestCandidates = suggestions
        galleryAuxUi.gallerySuggestLine = if (suggestions.isNotEmpty()) "找到 ${suggestions.size} 个 Pixiv 官方关联标签" else null
    }

    LaunchedEffect(route) {
        if (route !is DesktopRoute.GallerySearch) return@LaunchedEffect
        val got = withContext(Dispatchers.IO) {
            runCatching { desktopHistoryRead(desktopHistoryStore(ports)) }
        }
        got.fold(
            onSuccess = { gallerySearchHistory = it; gallerySearchHistoryNote = null },
            onFailure = { t ->
                gallerySearchHistory = emptyList()
                gallerySearchHistoryNote = desktopComicSearchHistoryNoteText(
                    "${t.javaClass.simpleName} ${t.message?.take(120) ?: ""}",
                )
            },
        )
    }
    // 按「搜索」/ 换来源档 / 点一条历史：重取。键取「按了几次」而不是取 query，
    // 否则改空再打同一串、或连点同一条历史时那颗 effect 不会重跑（那条同形）。
    LaunchedEffect(gallerySearchTick) {
        if (gallerySearchTick == 0) return@LaunchedEffect
        val word = gallerySearchQuery.trim()
        // 新一轮发车：先取消上一轮在途取数（壳层 scope 不受组合取消，这里显式 cancel），
        // 并重置闸门——被取消的旧轮不会走到 3306 那行清标志，留着会把新一轮自己拦掉。
        gallerySearchJob?.cancel()
        gallerySearchSearching = false
        if (word.isEmpty()) return@LaunchedEffect
        val source = gallerySearchSourceOptions[gallerySearchSource.coerceIn(0, gallerySearchSourceOptions.lastIndex)]
        // Gelbooru 未配账号时发车前剔除并记入 silent，避免无效 401 请求及 sitesDone 永不完结导致下一页假存在。
        val configured = desktopGelbooruConfigured(ports)
        val legs = source.availableLegs(configured)
        val missing = source.missingLegs(configured)
        gallerySearchSearching = true
        gallerySearchFailed = false
        gallerySearchPhase = DesktopGalleryMorePhase.Idle
        // 取数挪进壳层长寿 scope（真窗首修）：悬浮搜索发车那一帧 route 与 tick 同帧换，
        // 组合作用域的在途轮被整轮取消成假失败（LeftCompositionCancellationException，页上只剩
        // 缺席读数而页是空）。挪出后 effect 重启只管发车，在途轮跑完照常落账。
        gallerySearchJob = scope.launch {
            val loaded = runCatching {
                desktopWallFirstPage(
                    ports = ports,
                    queries = legs.associateWith { site ->
                        val suffix = desktopRankingQuery(site, desktopRankingLanding(legs, gallerySearchRanking), gallerySearchRankingAnchor, gallerySearchToday)
                        // 全年龄档（顶栏搜索浮层那枚开关）在查询级追加站方排除 tag；续页复用同串，一次拼对全程生效。
                        val ratingSuffix = desktopSearchRatingSuffix(site, gallerySearchR18)
                        listOf(word, suffix, ratingSuffix).filter { it.isNotBlank() }.joinToString(" ")
                    },
                    silent = missing,
                    guard = wallGuardOf(),
                    maskMode = maskModeOf(),
                    blockAi = wallBlockAiOf(),
                )
            }.onFailure {
                // 被新一轮取消：重抛让本协程立即退出，绝不落账盖掉新结果。
                if (it is kotlinx.coroutines.CancellationException) throw it
                println("D_画廊搜索 首轮失败 ${it::class.java.simpleName}: ${it.message?.take(200)}")
            }.getOrNull()
            gallerySearchSearching = false
            gallerySearchFailed = loaded == null
            gallerySearchPage = loaded
            if (loaded != null) {
                val entry = desktopSearchHistoryEntry(loaded.batch.queries.keys.toList(), word)
                val pushed = withContext(Dispatchers.IO) {
                    runCatching { desktopHistoryPush(desktopHistoryStore(ports), gallerySearchHistory, entry) }
                }
                pushed.getOrNull()?.let { gallerySearchHistory = it }
                pushed.exceptionOrNull()?.let {
                    gallerySearchHistoryNote = desktopComicSearchHistoryNoteText(
                        "${it.javaClass.simpleName} ${it.message?.take(120) ?: ""}",
                    )
                }
            }
            println(
                desktopGalleryWallFooter(
                    loaded?.batch,
                    loaded?.cards?.size ?: 0,
                    gallerySearchPhase,
                    "张",
                ).reading,
            )
        }
    }
    // 续页：与墙那一枚 `LaunchedEffect(wallMoreTick)` 同形，只是取数件换成 `fetchSearchPage`。
    LaunchedEffect(gallerySearchMoreTick) {
        if (gallerySearchMoreTick == 0) return@LaunchedEffect
        val page = gallerySearchPage
        if (page == null || gallerySearchPhase is DesktopGalleryMorePhase.Loading) return@LaunchedEffect
        if (!desktopWallMoreAllowed(page.batch, gallerySearchPhase)) return@LaunchedEffect
        gallerySearchPhase = DesktopGalleryMorePhase.Loading
        val got = runCatching { fetchSearchPage(ports, page.batch).getOrThrow() }
        got.fold(
            onSuccess = { next ->
                gallerySearchPage = desktopWallPageAfter(
                    base = page,
                    next = next.batch,
                    guard = wallGuardOf(),
                    maskMode = maskModeOf(),
                    blockAi = wallBlockAiOf(),
                )
                gallerySearchPhase = DesktopGalleryMorePhase.Idle
            },
            onFailure = { t ->
                gallerySearchPhase = DesktopGalleryMorePhase.Failed(
                    "${t.javaClass.simpleName}: ${t.message?.take(160) ?: ""}",
                )
            },
        )
        println(
            desktopGalleryWallFooter(
                gallerySearchPage?.batch,
                gallerySearchPage?.cards?.size ?: 0,
                gallerySearchPhase,
                "张",
            ).reading,
        )
    }

    // ── ：Pixiv 专属搜索那两枚 effect（首屏 + 续页）──
    // 取数腿在 DesktopPixivSearch.kt（作品=tagWorks/人气=popularPreview、画师=searchUser），
    // 落地判据（守卫/去重/页码账）与图库域同一套；历史与图库域共用一份档（落点按 entry 的 source）。
    LaunchedEffect(pixivSearchTick) {
        if (pixivSearchTick == 0) return@LaunchedEffect
        val word = pixivSearchTerm.trim()
        if (word.isEmpty() || pixivSearchSearching) return@LaunchedEffect
        pixivSearchSearching = true
        pixivSearchPhase = DesktopGalleryMorePhase.Idle
        // 与图库域首轮同修：取数挪进壳层长寿 scope，发车帧的路由切换不再取消在途轮。
        val kindNow = pixivSearchKind
        val filterNow = pixivSearchFilter
        scope.launch {
            // 画师维度：不吃图墙，直出用户表（游标给续页）。
            if (kindNow == DESKTOP_PIXIV_SEARCH_ARTIST) {
                desktopPixivSearchUsersPage(ports, word, null).fold(
                    onSuccess = { (users, cursor) ->
                        pixivSearchUsers = users
                        pixivSearchUsersCursor = cursor
                        pixivSearchPage = null
                    },
                    onFailure = { t ->
                        pixivSearchUsers = emptyList()
                        pixivSearchUsersCursor = null
                        pixivSearchPage = null
                        pixivSearchPhase = DesktopGalleryMorePhase.Failed(
                            com.venera.desktop.platform.desktopFailureNoteOf(t),
                        )
                    },
                )
                pixivSearchSearching = false
                return@launch
            }
            // 珍藏册维度：匿名 www 腿（GalleryPixivCollections.search），交卡表 + 总数 + 下一页 offset。
            if (kindNow == DESKTOP_PIXIV_SEARCH_COLLECTION) {
                val port = ports.pixivCollections
                if (port == null) {
                    pixivCollectionSearch.cards = emptyList()
                    pixivCollectionSearch.total = 0
                    pixivCollectionSearch.offset = -1
                    pixivSearchPhase = DesktopGalleryMorePhase.Failed("这台桌面还没接珍藏册端口")
                } else {
                    desktopPixivCollectionsSearchPage(port, word, offset = 0).fold(
                        onSuccess = { page ->
                            pixivCollectionSearch.cards = page.cards
                            pixivCollectionSearch.total = page.total
                            pixivCollectionSearch.offset = page.nextOffset ?: -1
                            pixivSearchPage = null
                            pixivSearchPhase = DesktopGalleryMorePhase.Idle
                        },
                        onFailure = { t ->
                            pixivCollectionSearch.cards = emptyList()
                            pixivCollectionSearch.total = 0
                            pixivCollectionSearch.offset = -1
                            pixivSearchPhase = DesktopGalleryMorePhase.Failed(
                                "${t.javaClass.simpleName}: ${t.message?.take(160) ?: ""}",
                            )
                        },
                    )
                }
                pixivSearchSearching = false
                return@launch
            }
            val loaded = runCatching {
                desktopPixivSearchFirstPage(
                    ports = ports,
                    word = word,
                    filter = filterNow,
                    kind = kindNow,
                    guard = wallGuardOf(),
                    maskMode = maskModeOf(),
                    blockAi = wallBlockAiOf(),
                )
            }.onFailure {
                println("D_pixiv搜索 首轮失败 ${it::class.java.simpleName}: ${it.message?.take(200)}")
                pixivSearchPhase = DesktopGalleryMorePhase.Failed(
                    com.venera.desktop.platform.desktopFailureNoteOf(it),
                )
            }.getOrNull()
            pixivSearchSearching = false
            pixivSearchPage = loaded
            if (loaded != null && loaded.batch.posts.isNotEmpty()) {
                val entry = desktopSearchHistoryEntry(listOf(com.venera.compose.gallery.data.GallerySite.PIXIV), word)
                val pushed = withContext(Dispatchers.IO) {
                    runCatching { desktopHistoryPush(desktopHistoryStore(ports), gallerySearchHistory, entry) }
                }
                pushed.getOrNull()?.let { gallerySearchHistory = it }
                pushed.exceptionOrNull()?.let {
                    gallerySearchHistoryNote = desktopComicSearchHistoryNoteText(
                        "${it.javaClass.simpleName} ${it.message?.take(120) ?: ""}",
                    )
                }
            }
            println(
                desktopGalleryWallFooter(
                    loaded?.batch,
                    loaded?.cards?.size ?: 0,
                    pixivSearchPhase,
                    "张",
                ).reading,
            )
        }
    }
    LaunchedEffect(pixivSearchMoreTick) {
        if (pixivSearchMoreTick == 0) return@LaunchedEffect
        val page = pixivSearchPage
        // 画师维度：游标续页（游标尽 = 到底，相位回 Idle 不再响扳机）。
        if (pixivSearchKind == DESKTOP_PIXIV_SEARCH_ARTIST) {
            val cursor = pixivSearchUsersCursor
            if (cursor == null || pixivSearchPhase is DesktopGalleryMorePhase.Loading) return@LaunchedEffect
            pixivSearchPhase = DesktopGalleryMorePhase.Loading
            desktopPixivSearchUsersPage(ports, pixivSearchTerm.trim(), cursor).fold(
                onSuccess = { (users, next) ->
                    pixivSearchUsers = pixivSearchUsers + users
                    pixivSearchUsersCursor = next
                    pixivSearchPhase = DesktopGalleryMorePhase.Idle
                },
                onFailure = { t ->
                    pixivSearchPhase = DesktopGalleryMorePhase.Failed(
                        "${t.javaClass.simpleName}: ${t.message?.take(160) ?: ""}",
                    )
                },
            )
            return@LaunchedEffect
        }
        // 珍藏册维度：offset 翻页（`offset <= 0` = 到底 / 没有下一页）。
        if (pixivSearchKind == DESKTOP_PIXIV_SEARCH_COLLECTION) {
            val port = ports.pixivCollections ?: return@LaunchedEffect
            if (pixivCollectionSearch.offset <= 0 || pixivSearchPhase is DesktopGalleryMorePhase.Loading) {
                return@LaunchedEffect
            }
            pixivSearchPhase = DesktopGalleryMorePhase.Loading
            desktopPixivCollectionsSearchPage(port, pixivSearchTerm.trim(), offset = pixivCollectionSearch.offset).fold(
                onSuccess = { page ->
                    pixivCollectionSearch.cards = pixivCollectionSearch.cards + page.cards
                    pixivCollectionSearch.total = page.total
                    pixivCollectionSearch.offset = page.nextOffset ?: -1
                    pixivSearchPhase = DesktopGalleryMorePhase.Idle
                },
                onFailure = { t ->
                    pixivSearchPhase = DesktopGalleryMorePhase.Failed(
                        "${t.javaClass.simpleName}: ${t.message?.take(160) ?: ""}",
                    )
                },
            )
            return@LaunchedEffect
        }
        if (page == null || pixivSearchPhase is DesktopGalleryMorePhase.Loading) return@LaunchedEffect
        if (!desktopWallMoreAllowed(page.batch, pixivSearchPhase)) return@LaunchedEffect
        pixivSearchPhase = DesktopGalleryMorePhase.Loading
        val got = runCatching {
            fetchPixivSearchNext(
                ports = ports,
                base = page,
                word = pixivSearchTerm.trim(),
                filter = pixivSearchFilter,
                kind = pixivSearchKind,
                guard = wallGuardOf(),
                maskMode = maskModeOf(),
                blockAi = wallBlockAiOf(),
            ).getOrThrow()
        }
        got.fold(
            onSuccess = { next ->
                pixivSearchPage = next
                pixivSearchPhase = DesktopGalleryMorePhase.Idle
            },
            onFailure = { t ->
                pixivSearchPhase = DesktopGalleryMorePhase.Failed(
                    "${t.javaClass.simpleName}: ${t.message?.take(160) ?: ""}",
                )
            },
        )
        println(
            desktopGalleryWallFooter(
                pixivSearchPage?.batch,
                pixivSearchPage?.cards?.size ?: 0,
                pixivSearchPhase,
                "张",
            ).reading,
        )
    }
    LaunchedEffect(route) {
        val r = route
        val card: DesktopWallCardModel? = when (r) {
            is DesktopRoute.GalleryPostDetail -> r.post
            is DesktopRoute.GalleryPostViewer -> r.post
            else -> null
        }
        val pixivId: Long? = (r as? DesktopRoute.PixivWork)?.illustId
        val paths = galleryPaths ?: return@LaunchedEffect
        if (card?.masked == true) return@LaunchedEffect
        val posts = wallPage?.batch?.posts.orEmpty() + gallerySearchPage?.batch?.posts.orEmpty()
        val post = card?.sourcePost
            ?: card?.let { desktopBrowsePostOfUid(posts, it.uid) }
            ?: pixivId?.let { id -> posts.firstOrNull { it.site == GallerySite.PIXIV && it.id == id } }
            ?: return@LaunchedEffect
        // 先用壳里已经拿着的那一颗（读侧 effect 取到过就绝不第二次 `getInstance` —— 那颗件按数据根
        // 自己缓存，拿到的是同一个对象，但"取到过还再去取"是多余的一跳）；没进过历史屏时才是 null，
        // 这一趟在 IO 上现取。取不到（没数据根 / 读盘塌了）⇒ 什么都不写，也不崩窗。
        val store = browseClearUi.browseStore ?: withContext(Dispatchers.IO) {
            runCatching { DesktopGalleryBrowseHistory.getInstance(paths) }.getOrNull()
        } ?: return@LaunchedEffect
        withContext(Dispatchers.IO) { runCatching { store.recordView(post, System.currentTimeMillis()) } }
        browseClearUi.browseStore = store
        browseClearUi.browseRows = store.rows.value
    }

    // 探索取数按 currentTab、exploreTick 与选中源触发（前置 engine.load），首页推荐漫画复用同一份结果。
    LaunchedEffect(currentTab, exploreTick, exploreSourceKey, currentMaskMode) {
        // 分离稿：SEARCH 格的探索档也是探索的消费点 ⇒ 闸门放开 SEARCH。
        if (currentTab != VeneraNavTab.EXPLORE &&
            currentTab != VeneraNavTab.HOME &&
            currentTab != VeneraNavTab.SEARCH
        ) return@LaunchedEffect
        val e = engine
        if (e == null) {
            exploreNote = "引擎没起来：取不到探索页。"
            return@LaunchedEffect
        }
        exploreNote = null
        val wantedSource = exploreSourceKey ?: DESKTOP_EXPLORE_SOURCE
        // 探索方式清单单独一趟（同一把锁）。`e.exploreModes` 不是取数腿，
        // 单开一趟换"`e.explore(` 那一跳仍紧贴 `desktopEngineCall` 行"（引擎账尺的量程）。
        val modeNames = withContext(Dispatchers.IO) {
            desktopEngineCall(e) {
                e.load(wantedSource)
                e.exploreModes()
            }.getOrElse { emptyList() }
        }
        val board = withContext(Dispatchers.IO) {
            desktopEngineCall(e) {
                e.load(wantedSource)
                e.categoryBoard()
            }.getOrElse { com.venera.engineprobe.EngineSession.CategoryBoard(emptyList(), emptyList()) }
        }
        exploreCategoryBoard = board
        val modeIndex = exploreModeIndex.coerceIn(0, maxOf(0, modeNames.size - 1))
        exploreModes = modeNames
        exploreModeIndex = modeIndex
        // 过 `desktopEngineCall`：这颗引擎同时被详情/阅读器/搜索用着，
        // 裸调会让两趟 `load()` 交错（实测：`装载后取不到 source key`）。
        val got = desktopEngineCall(e) {
            e.load(wantedSource)
            e.explore(modeIndex, 1)
        }
        got.fold(
            onSuccess = { page ->
                exploreSections = page.sections.ifEmpty {
                    // 有的源 `explore()` 回的是单个 comics 列表（不带分区）。
                    // 那种情况包成一个无名分区，而不是让屏空着。
                    if (page.comics.isEmpty()) emptyList()
                    else listOf(com.venera.engineprobe.EngineSession.Section(title = "", comics = page.comics))
                }
                exploreNote = page.error?.takeIf { it.isNotBlank() }?.let { raw ->
                    println("D_探索 源报错 ${raw.take(200)}")
                    com.venera.desktop.data.DesktopErrorBus.report(
                        category = "漫画",
                        headline = "探索内容被源站拒绝",
                        humanReason = "该源这一轮没回探索内容而是回了失败，换分类重试也不会变好。",
                        detail = "$wantedSource: $raw",
                        dedupKey = "comic:explore_denied:$wantedSource",
                    )
                    com.venera.desktop.platform.desktopFailureNoteOfText(raw)
                } ?: if (exploreSections.isEmpty()) "这一源没回探索内容。" else null
            },
            onFailure = { t ->
                exploreSections = emptyList()
                val errMsg = "${t.javaClass.simpleName}: ${t.message?.take(160) ?: ""}"
                println("D_探索 失败 $errMsg")
                exploreNote = com.venera.desktop.platform.desktopFailureNoteOf(t)
                com.venera.desktop.data.DesktopErrorBus.report(
                    category = "探索页",
                    headline = "探索内容加载失败（$wantedSource）",
                    detail = errMsg,
                    dedupKey = "explore:$wantedSource:${t.javaClass.simpleName}",
                )
            },
        )
    }

    // ── 探索分类 chip 取数──
    // 点一枚分类/排行榜 → 第一页；「加载更多」按 maxPage 续。两种腿都过同一把锁。
    LaunchedEffect(explorePickTick) {
        if (explorePickTick == 0) return@LaunchedEffect
        val pick = explorePickUi.pick ?: return@LaunchedEffect
        val e = engine ?: return@LaunchedEffect
        val key = exploreSourceKey ?: DESKTOP_EXPLORE_SOURCE
        explorePickUi.page = null
        explorePickPageNo = 1
        explorePickUi.maxPage = null
        val got = if (pick.kind == "ranking") {
            desktopEngineCall(e) {
                e.load(key)
                e.loadRanking(pick.param ?: "", 1)
            }
        } else {
            desktopEngineCall(e) {
                e.load(key)
                e.loadCategory(pick.label, pick.param, pick.kind, 1)
            }
        }
        got.fold(
            onSuccess = { p ->
                explorePickUi.page = p
                explorePickUi.maxPage = p.maxPage
            },
            onFailure = { t ->
                println("D_探索 取分类失败 ${t.javaClass.simpleName}: ${t.message?.take(160) ?: ""}")
                com.venera.desktop.data.DesktopErrorBus.report(
                    category = "漫画",
                    headline = "探索分类取数失败",
                    humanReason = "点开的这个分类没取回内容，重试或换一个分类。",
                    detail = "${t.javaClass.simpleName}: ${t.message.orEmpty()}",
                    dedupKey = "comic:explore_pick_fail:$key",
                )
                explorePickUi.page = com.venera.engineprobe.EngineSession.Page(
                    comics = emptyList(),
                    error = com.venera.desktop.platform.desktopFailureNoteOf(t),
                )
            },
        )
    }

    LaunchedEffect(explorePickMoreTick) {
        if (explorePickMoreTick == 0) return@LaunchedEffect
        val pick = explorePickUi.pick ?: return@LaunchedEffect
        val current = explorePickUi.page ?: return@LaunchedEffect
        if (explorePickUi.loading) return@LaunchedEffect
        val e = engine ?: return@LaunchedEffect
        val key = exploreSourceKey ?: DESKTOP_EXPLORE_SOURCE
        val nextPage = explorePickPageNo + 1
        if (nextPage > (explorePickUi.maxPage ?: (nextPage - 1))) return@LaunchedEffect
        explorePickUi.loading = true
        val got = if (pick.kind == "ranking") {
            desktopEngineCall(e) {
                e.load(key)
                e.loadRanking(pick.param ?: "", nextPage)
            }
        } else {
            desktopEngineCall(e) {
                e.load(key)
                e.loadCategory(pick.label, pick.param, pick.kind, nextPage)
            }
        }
        explorePickUi.loading = false
        if (explorePickUi.pick != pick) return@LaunchedEffect
        got.fold(
            onSuccess = { p ->
                val fresh = p.comics.filterNot { c -> current.comics.any { it.id == c.id } }
                val denied = p.error?.takeIf { it.isNotBlank() }?.let { raw ->
                    println("D_探索 续页源报错 ${raw.take(200)}")
                    com.venera.desktop.data.DesktopErrorBus.report(
                        category = "漫画",
                        headline = "探索分类翻页被源站拒绝",
                        humanReason = "源站回了失败而不是空页，继续点加载更多不会变好。",
                        detail = "$key: $raw",
                        dedupKey = "comic:explore_pick_more_denied:$key",
                    )
                    com.venera.desktop.platform.desktopFailureNoteOfText(raw)
                }
                explorePickUi.page = current.copy(comics = current.comics + fresh, error = denied)
                explorePickPageNo = nextPage
                if (p.maxPage != null) explorePickUi.maxPage = p.maxPage
            },
            onFailure = { t ->
                println("D_探索 续页失败 ${t.javaClass.simpleName}")
                com.venera.desktop.data.DesktopErrorBus.report(
                    category = "漫画",
                    headline = "探索分类翻页失败",
                    humanReason = "这个分类的下页没取回来，已显示的内容还在；可再点一次加载更多。",
                    detail = "${t.javaClass.simpleName}: ${t.message.orEmpty()}",
                    dedupKey = "comic:explore_pick_more_fail:$key",
                )
                explorePickUi.page = current.copy(
                    error = com.venera.desktop.platform.desktopFailureNoteOf(t),
                )
            },
        )
    }

    // 首页推荐漫画复用探索引擎：正常档直取 GoDa 热门更新，R18 档接禁漫天堂日排行。
    LaunchedEffect(currentTab, exploreTick, currentMaskMode, homeRecommendR18) {
        if (currentTab != VeneraNavTab.HOME) return@LaunchedEffect
        if (homeRecommendComics.isNotEmpty()) return@LaunchedEffect
        val e = engine ?: return@LaunchedEffect
        // R18 档接**禁漫天堂日排行**（`categoryComics.ranking` 的 `mv_t-日排行`）。
        // 从前这个开关只清列表、没有取数腿 —— 切过去永远是空的（假开关）。
        val jmKey = sourceUi.sourceRows.firstOrNull { it.title.contains("禁漫") }?.key ?: ("j" + "m")
        // 两条腿各包一层 `desktopEngineCall`：判据要求每一跳紧跟守卫（距离 ≤2），
        // 分支写进守卫里面会把距离推到 3~6（`DesktopEngineCallCoverageTest` 当场红）。
        val got = if (homeRecommendR18) {
            desktopEngineCall(e) {
                e.load(jmKey)
                e.loadRanking("mv_t-日排行", 1)
            }
        } else {
            desktopEngineCall(e) {
                e.load(DESKTOP_HOME_RECOMMEND_SOURCE)
                e.explore(0, 1)
            }
        }
        got.fold(
            onSuccess = { page ->
                // GoDa 页面栏目标题实测为繁体「熱門更新」，同时兼容简体「热门更新」，取不到退回整页。
                val cards = page.sections.firstOrNull { it.title == "熱門更新" || it.title == "热门更新" }?.comics
                    ?.takeIf { it.isNotEmpty() }
                    ?: page.sections.flatMap { it.comics }.ifEmpty { page.comics }
                if (cards.isNotEmpty()) {
                    homeRecommendComics = cards
                    homeRecommendNote = if (homeRecommendR18) {
                        "推荐漫画（禁漫天堂 · 日排行）"
                    } else {
                        "推荐漫画（GoDa漫画 · 热门更新）"
                    }
                } else {
                    homeRecommendNote = page.error?.takeIf { it.isNotBlank() }
                        ?.let { raw ->
                            println("D_推荐 源报错 ${raw.take(200)}")
                            com.venera.desktop.data.DesktopErrorBus.report(
                                category = "漫画",
                                headline = "首页推荐被源站拒绝",
                                humanReason = "首页推荐这一格没取回内容，源站回的是失败而不是空列表。",
                                detail = (if (homeRecommendR18) "jm" else DESKTOP_HOME_RECOMMEND_SOURCE) + ": " + raw,
                                dedupKey = "comic:home_recommend_denied",
                            )
                            com.venera.desktop.platform.desktopFailureNoteOfText(raw)
                        }
                        ?: "这一源没回推荐内容。"
                }
            },
            onFailure = { t ->
                println("D_推荐 取数失败 ${t.javaClass.simpleName}: ${t.message?.take(160) ?: ""}")
                com.venera.desktop.data.DesktopErrorBus.report(
                    category = "漫画",
                    headline = "首页推荐取数失败",
                    humanReason = "首页那一排推荐没取回来，重启或换源后可重试。",
                    detail = "${t.javaClass.simpleName}: ${t.message.orEmpty()}",
                    dedupKey = "comic:home_recommend_fail",
                )
                homeRecommendNote = com.venera.desktop.platform.desktopFailureNoteOf(t)
            },
        )
    }
}
