package com.venera.desktop.app
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

import androidx.compose.runtime.MutableLongState

@Composable
internal fun DesktopLargeScreenAllEffects(
    state: DesktopLargeScreenState,
    ports: GalleryPorts,
    maskModeOf: () -> String = { "BLUR" },
    wallGuardOf: () -> com.venera.compose.gallery.data.GalleryContentGuard? = { null },
    wallBlockAiOf: () -> Boolean = { false },
    engine: com.venera.engineprobe.EngineSession? = null,
    writeHistory: (suspend (com.venera.compose.data.db.HistoryRecord) -> Unit)? = null,
    readHistoryRow: (suspend (String) -> com.venera.compose.data.db.HistoryRecord?)? = null,
    readSources: (() -> List<com.venera.desktop.sourcemanage.DesktopSourceRowUi>)? = null,
    readSourceText: ((String) -> String?)? = null,
    loginWithCookies: (suspend (key: String, values: List<String>) -> String)? = null,
    loginWithPassword: (suspend (key: String, account: String, password: String) -> String)? = null,
    loginWithWebview: (suspend (key: String) -> String)? = null,
    readFavorites: (suspend () -> Pair<List<com.venera.compose.data.db.FavoriteRecord>, Boolean>)? = null,
    readFavoriteFolders: (suspend () -> List<String>)? = null,
    isFavoriteOf: (suspend (String) -> Boolean)? = null,
    searchComics: (suspend (String, Int, String, List<String?>) -> DesktopComicSearchOutcome)? = null,
    downloadsRoot: java.io.File? = null,
    openFirstGalleryPost: Boolean = false,
    galleryPaths: com.venera.compose.data.platform.PathProvider? = null,
    galleryPrefs: com.venera.desktop.gallery.data.DesktopGalleryPreferences? = null,
) {
    DesktopComicAndFilesEffects(
        engine = engine,
        downloadsRoot = downloadsRoot,
        comicCache = state.comicCache,
        queueTasks = state.queueTasks,
        exploreSections = state.exploreSections,
        homeHistory = state.homeHistory,
        historySourceName = state.historySourceName,
        favoriteFolderComicId = state.favoriteFolderComicId,
        favoriteFolderNames = state.favoriteFolderNames,
        favoriteFolderCurrent = state.favoriteFolderCurrent,
        favoriteFolderSelected = state.favoriteFolderSelected,
        favoritesTabSelected = state.favoritesTabSelected,
        favoritesShown = state.favoritesShown,
        favoritesFolderTabs = state.favoritesFolderTabs,
        detailUi = state.detailUi,
        sourceUi = state.sourceUi,
        filesUi = state.filesUi,
        readFavorites = readFavorites,
        readFavoriteFolders = readFavoriteFolders,
        isFavoriteOf = isFavoriteOf,
        readHistoryRow = readHistoryRow,
        writeHistory = writeHistory,
        readSources = readSources,
        readSourceText = readSourceText,
        loginWithCookies = loginWithCookies,
        loginWithPassword = loginWithPassword,
        loginWithWebview = loginWithWebview,
        routeState = state.routeState,
        currentTabState = state.currentTabState,
        commentsTickState = state.commentsTickState,
        readerPagesState = state.readerPagesState,
        favoritesState = state.favoritesState,
        favoritesLoadedState = state.favoritesLoadedState,
        favoritesNoteState = state.favoritesNoteState,
        favoriteFolderPickedState = state.favoriteFolderPickedState,
        sourceTickState = state.sourceTickState,
        filesSegmentState = state.filesSegmentState,
        downloadsTickState = state.downloadsTickState,
        localComicImportTickState = state.localComicImportTickState,
        localComicScanState = state.localComicScanState,
        lastPageTurnAtState = state.lastPageTurnAtState,
    )
    DesktopGalleryWallEffects(
        ports = ports,
        engine = engine,
        galleryPaths = galleryPaths,
        galleryPrefs = galleryPrefs,
        currentMaskMode = state.currentMaskMode,
        maskModeOf = maskModeOf,
        wallGuardOf = wallGuardOf,
        wallBlockAiOf = wallBlockAiOf,
        pixivAuthState = state.pixivAuthState,
        galleryFavorites = state.galleryFavorites,
        galleryWallQuality = state.galleryWallQuality,
        wallCache = state.wallCache,
        wallCacheKey = state.wallCacheKey,
        cards = state.cards,
        pixivPreviewCache = state.pixivPreviewCache,
        tagDictionary = state.tagDictionary,
        readingStats = state.readingStats,
        backupUi = state.backupUi,
        netFavUi = state.netFavUi,
        sourceUi = state.sourceUi,
        galleryAuxUi = state.galleryAuxUi,
        dailyGallery = state.dailyGallery,
        dailyCore = state.dailyCore,
        routeState = state.routeState,
        currentTabState = state.currentTabState,
        favoritesState = state.favoritesState,
        favoritesSourceKeyState = state.favoritesSourceKeyState,
        galleryWallCardsState = state.galleryWallCardsState,
        galleryWallBlockedState = state.galleryWallBlockedState,
        galleryWallUnrenderableState = state.galleryWallUnrenderableState,
        galleryWallTickState = state.galleryWallTickState,
        homeRetryTickState = state.homeRetryTickState,
        wallPageState = state.wallPageState,
        galleryHomeDomainState = state.galleryHomeDomainState,
        galleryRankingModeState = state.galleryRankingModeState,
        gallerySortState = state.gallerySortState,
        wallRefreshTickState = state.wallRefreshTickState,
        lastAppliedRefreshTickState = state.lastAppliedRefreshTickState,
        wallRefreshingState = state.wallRefreshingState,
        wallPhaseState = state.wallPhaseState,
        wallMoreTickState = state.wallMoreTickState,
        pixivPreviewLegState = state.pixivPreviewLegState,
        lastAppliedPreviewTickState = state.lastAppliedPreviewTickState,
        pixivDiscoverLegState = state.pixivDiscoverLegState,
        pixivLocalFollowedState = state.pixivLocalFollowedState,
    )
    DesktopSearchAndExploreEffects(
        ports = ports,
        engine = engine,
        scope = state.scope,
        galleryPaths = galleryPaths,
        galleryPrefs = galleryPrefs,
        currentMaskMode = state.currentMaskMode,
        maskModeOf = maskModeOf,
        wallGuardOf = wallGuardOf,
        wallBlockAiOf = wallBlockAiOf,
        searchComics = searchComics,
        aggregateSearch = state.aggregateSearch,
        browseClearUi = state.browseClearUi,
        explorePickUi = state.explorePickUi,
        galleryAuxUi = state.galleryAuxUi,
        sourceUi = state.sourceUi,
        pixivCollectionSearch = state.pixivCollectionSearch,
        gallerySearchSourceOptions = state.gallerySearchSourceOptions,
        gallerySearchToday = state.gallerySearchToday,
        wallPage = state.wallPage,
        routeState = state.routeState,
        currentTabState = state.currentTabState,
        sourceTickState = state.sourceTickState,
        searchQueryState = state.searchQueryState,
        searchResultsState = state.searchResultsState,
        searchingState = state.searchingState,
        searchTickState = state.searchTickState,
        searchSourceKeyState = state.searchSourceKeyState,
        searchFilterGroupsState = state.searchFilterGroupsState,
        searchFilterValuesState = state.searchFilterValuesState,
        searchPageState = state.searchPageState,
        searchMaxPageState = state.searchMaxPageState,
        loadingMoreState = state.loadingMoreState,
        searchCanLoadMoreState = state.searchCanLoadMoreState,
        hasSearchedState = state.hasSearchedState,
        searchHistoryState = state.searchHistoryState,
        searchHistoryNoteState = state.searchHistoryNoteState,
        searchCauseState = state.searchCauseState,
        loadMoreTickState = state.loadMoreTickState,
        gallerySearchQueryState = state.gallerySearchQueryState,
        gallerySearchSourceState = state.gallerySearchSourceState,
        gallerySearchPageState = state.gallerySearchPageState,
        gallerySearchPhaseState = state.gallerySearchPhaseState,
        gallerySearchSearchingState = state.gallerySearchSearchingState,
        gallerySearchFailedState = state.gallerySearchFailedState,
        gallerySearchHistoryState = state.gallerySearchHistoryState,
        gallerySearchHistoryNoteState = state.gallerySearchHistoryNoteState,
        gallerySearchTickState = state.gallerySearchTickState,
        gallerySearchMoreTickState = state.gallerySearchMoreTickState,
        gallerySearchJobState = state.gallerySearchJobState,
        gallerySearchModeState = state.gallerySearchModeState,
        gallerySearchRankingState = state.gallerySearchRankingState,
        gallerySearchRankingAnchorState = state.gallerySearchRankingAnchorState,
        gallerySearchR18State = state.gallerySearchR18State,
        gallerySuggestCandidatesState = state.gallerySuggestCandidatesState,
        pixivSearchTermState = state.pixivSearchTermState,
        pixivSearchFilterState = state.pixivSearchFilterState,
        pixivSearchKindState = state.pixivSearchKindState,
        pixivSearchPageState = state.pixivSearchPageState,
        pixivSearchPhaseState = state.pixivSearchPhaseState,
        pixivSearchSearchingState = state.pixivSearchSearchingState,
        pixivSearchTickState = state.pixivSearchTickState,
        pixivSearchMoreTickState = state.pixivSearchMoreTickState,
        pixivSearchUsersState = state.pixivSearchUsersState,
        pixivSearchUsersCursorState = state.pixivSearchUsersCursorState,
        exploreSectionsState = state.exploreSectionsState,
        exploreNoteState = state.exploreNoteState,
        exploreTickState = state.exploreTickState,
        exploreSourceKeyState = state.exploreSourceKeyState,
        exploreModesState = state.exploreModesState,
        exploreModeIndexState = state.exploreModeIndexState,
        exploreCategoryBoardState = state.exploreCategoryBoardState,
        explorePickTickState = state.explorePickTickState,
        explorePickMoreTickState = state.explorePickMoreTickState,
        explorePickPageNoState = state.explorePickPageNoState,
        homeRecommendComicsState = state.homeRecommendComicsState,
        homeRecommendNoteState = state.homeRecommendNoteState,
        homeRecommendR18State = state.homeRecommendR18State,
    )
    DesktopLargeScreenMiscEffects(
        ports = ports,
        engine = engine,
        downloadsRoot = downloadsRoot,
        galleryPaths = galleryPaths,
        galleryPrefs = galleryPrefs,
        queueConcurrency = state.queueConcurrency,
        queueTasks = state.queueTasks,
        pixivCallback = state.pixivCallback,
        browseClearUi = state.browseClearUi,
        detailUi = state.detailUi,
        filesUi = state.filesUi,
        detailCoreCache = state.detailCoreCache,
        homeLines = state.homeLines,
        homeLatencyByHost = state.homeLatencyByHost,
        homeProbeStamp = state.homeProbeStamp,
        homeProbingHosts = state.homeProbingHosts,
        homeFailedHosts = state.homeFailedHosts,
        homeScope = state.homeScope,
        performSourceUpdateCheck = state.performSourceUpdateCheck,
        readFavoriteFolders = readFavoriteFolders,
        openFirstGalleryPost = openFirstGalleryPost,
        wallPage = state.wallPage,
        routeState = state.routeState,
        savedUiState = state.savedUiState,
        favoriteFoldersState = state.favoriteFoldersState,
    )
}

@Composable
internal fun DesktopLargeScreenRouterWiring(
    state: DesktopLargeScreenState,
    ports: GalleryPorts,
    maskModeOf: () -> String = { "BLUR" },
    wallGuardOf: () -> com.venera.compose.gallery.data.GalleryContentGuard? = { null },
    wallBlockAiOf: () -> Boolean = { false },
    engine: com.venera.engineprobe.EngineSession? = null,
    writeHistory: (suspend (com.venera.compose.data.db.HistoryRecord) -> Unit)? = null,
    writeSourceEnabled: ((String, Boolean) -> Unit)? = null,
    writeSourceOrder: ((List<String>) -> Unit)? = null,
    readSourceText: ((String) -> String?)? = null,
    clearSourceOverride: (suspend (String) -> Unit)? = null,
    loginWithCookies: (suspend (key: String, values: List<String>) -> String)? = null,
    loginWithPassword: (suspend (key: String, account: String, password: String) -> String)? = null,
    loginWithWebview: (suspend (key: String) -> String)? = null,
    createFavoriteFolder: (suspend (String) -> Unit)? = null,
    deleteFavoriteFolder: (suspend (String) -> Unit)? = null,
    removeFavorite: (suspend (String) -> Unit)? = null,
    addFavorite: (suspend (com.venera.compose.data.db.FavoriteRecord) -> Unit)? = null,
    searchComics: (suspend (String, Int, String, List<String?>) -> DesktopComicSearchOutcome)? = null,
    downloadsRoot: java.io.File? = null,
    readerPrefs: com.venera.desktop.gallery.data.DesktopComicReaderPreferences? = null,
    galleryPaths: com.venera.compose.data.platform.PathProvider? = null,
    galleryPrefs: com.venera.desktop.gallery.data.DesktopGalleryPreferences? = null,
    galleryGuard: com.venera.desktop.gallery.data.DesktopGalleryContentGuard? = null,
    assetDir: java.io.File? = null,
    overrideDir: java.io.File? = null,
    shellBackdropUrl: String?,
    /** 追更账本；null = 装配点没接 ⇒ 追更相关入口不摆。 */
    followDao: com.venera.compose.data.db.ComicFollowDao? = null,
) {
    DesktopLargeScreenRouter(
        contentRoute = state.contentRoute,
        settingsOpen = state.settingsOpen,
        layoutMode = state.layoutMode,
        engine = engine,
        ports = ports,
        scope = state.scope,
        downloadsRoot = downloadsRoot,
        galleryPaths = galleryPaths,
        galleryPrefs = galleryPrefs,
        galleryGuard = galleryGuard,
        readerPrefs = readerPrefs,
        desktopUx = state.desktopUx,
        businessHandle = state.businessHandle,
        queueTasks = state.queueTasks,
        queueConcurrency = state.queueConcurrency,
        queuePrimed = state.queuePrimed,
        favoritesLoaded = state.favoritesLoaded,
        favoritesState = state.favoritesState,
        favoriteFolderNames = state.favoriteFolderNames,
        localComicScan = state.localComicScan,
        readerMode = state.readerMode,
        readerModeNote = state.readerModeNote,
        detailCoreCache = state.detailCoreCache,
        sourceUi = state.sourceUi,
        filesUi = state.filesUi,
        detailUi = state.detailUi,
        aggregateSearch = state.aggregateSearch,
        runSourceImport = state.runSourceImport,
        writeSourceEnabled = writeSourceEnabled,
        writeSourceOrder = writeSourceOrder,
        readSourceText = readSourceText,
        clearSourceOverride = clearSourceOverride,
        loginWithCookies = loginWithCookies,
        loginWithPassword = loginWithPassword,
        loginWithWebview = loginWithWebview,
        writeHistory = writeHistory,
        addFavorite = addFavorite,
        removeFavorite = removeFavorite,
        createFavoriteFolder = createFavoriteFolder,
        deleteFavoriteFolder = deleteFavoriteFolder,
        openSettings = state.openSettings,
        searchComics = searchComics,
        onDownloadsDelete = state.onDownloadsDelete,
        flyToDownloadCenter = state.flyToDownloadCenter,
        routeState = state.routeState,
        currentTabState = state.currentTabState,
        commentsTickState = state.commentsTickState,
        readerPagesState = state.readerPagesState,
        searchReturnRouteState = state.searchReturnRouteState,
        sourceTickState = state.sourceTickState,
        downloadsTickState = state.downloadsTickState,
        localComicImportTickState = state.localComicImportTickState,
        armedDeleteKeyState = state.armedDeleteKeyState,
        readerModeChoiceState = state.readerModeChoiceState,
        searchQueryState = state.searchQueryState,
        searchSourceKeyState = state.searchSourceKeyState,
        searchTickState = state.searchTickState,
        shellBackdropUrl = shellBackdropUrl,
        followDao = followDao,
        wallDensity = state.wallDensity,
        artistProfileAnchor = state.artistProfileAnchor,
        dailyCore = state.dailyCore,
        dailyGallery = state.dailyGallery,
        galleryAuxUi = state.galleryAuxUi,
        openWallCardAny = state.openWallCardAny,
        searchHandoff = LocalDesktopSearchHandoff.current,
        pixivAccountTabState = state.pixivAccountTabState,
        pixivArtistSectionState = state.pixivArtistSectionState,
        lastPixivArtistIdState = state.lastPixivArtistIdState,
        pixivSearchKindState = state.pixivSearchKindState,
        pixivSearchTermState = state.pixivSearchTermState,
        pixivSearchTickState = state.pixivSearchTickState,
        gallerySearchModeState = state.gallerySearchModeState,
        pixivWorkViewerQueueState = state.pixivWorkViewerQueueState,
        pixivWorkResumePageState = state.pixivWorkResumePageState,
        railPrefs = state.railPrefs,
        assetDir = assetDir,
        overrideDir = overrideDir,
        maskModeOf = maskModeOf,
        wallGuardOf = wallGuardOf,
        wallBlockAiOf = wallBlockAiOf,
        historySourceName = state.historySourceName,
        readingStats = state.readingStats,
        galleryFavorites = state.galleryFavorites,
        galleryWallQuality = state.galleryWallQuality,
        galleryWallCards = state.galleryWallCards,
        galleryWallBlocked = state.galleryWallBlocked,
        galleryWallUnrenderable = state.galleryWallUnrenderable,
        galleryViewerQueue = state.galleryViewerQueue,
        gallerySearchToday = state.gallerySearchToday,
        gallerySearchSourceOptions = state.gallerySearchSourceOptions,
        gallerySearchRankingChoices = state.gallerySearchRankingChoices,
        gallerySearchRankingLanding = state.gallerySearchRankingLanding,
        gallerySearchPeriodReading = state.gallerySearchPeriodReading,
        gallerySearchCanPeriodBack = state.gallerySearchCanPeriodBack,
        gallerySearchCanPeriodForward = state.gallerySearchCanPeriodForward,
        gallerySearchCards = state.gallerySearchCards,
        gallerySearchFooter = state.gallerySearchFooter,
        pixivSearchCards = state.pixivSearchCards,
        pixivSearchFooter = state.pixivSearchFooter,
        pixivCollectionSearch = state.pixivCollectionSearch,
        searchResultAnchor = state.searchResultAnchor,
        searchArtistAnchor = state.searchArtistAnchor,
        backupUi = state.backupUi,
        browseClearUi = state.browseClearUi,
        openGalleryPostAny = state.openGalleryPostAny,
        savedUiState = state.savedUiState,
        historyTickState = state.historyTickState,
        galleryWallTickState = state.galleryWallTickState,
        gallerySearchQueryState = state.gallerySearchQueryState,
        gallerySearchSourceState = state.gallerySearchSourceState,
        gallerySearchTickState = state.gallerySearchTickState,
        gallerySearchMoreTickState = state.gallerySearchMoreTickState,
        gallerySearchR18State = state.gallerySearchR18State,
        gallerySearchPageState = state.gallerySearchPageState,
        gallerySearchSearchingState = state.gallerySearchSearchingState,
        gallerySearchFailedState = state.gallerySearchFailedState,
        gallerySearchHistoryState = state.gallerySearchHistoryState,
        gallerySearchHistoryNoteState = state.gallerySearchHistoryNoteState,
        gallerySearchRankingState = state.gallerySearchRankingState,
        gallerySearchRankingAnchorState = state.gallerySearchRankingAnchorState,
        pixivSearchMoreTickState = state.pixivSearchMoreTickState,
        pixivSearchFilterState = state.pixivSearchFilterState,
        pixivSearchPageState = state.pixivSearchPageState,
        pixivSearchSearchingState = state.pixivSearchSearchingState,
        pixivSearchUsersState = state.pixivSearchUsersState,
        gallerySuggestCandidatesState = state.gallerySuggestCandidatesState,
        currentTab = state.currentTab,
        homeSkinLight = state.homeSkinLight,
        pixivSuggestEngine = state.pixivSuggestEngine,
        exploreCategoryBoard = state.exploreCategoryBoard,
        explorePickUi = state.explorePickUi,
        favoritesNote = state.favoritesNote,
        netFavUi = state.netFavUi,
        searchFilterGroups = state.searchFilterGroups,
        homeSourceRows = state.homeSourceRows,
        homeHistory = state.homeHistory,
        homeStats = state.homeStats,
        homeArtists = state.homeArtists,
        favoritesShown = state.favoritesShown,
        favoritesFolderTabs = state.favoritesFolderTabs,
        favoritesCountNote = state.favoritesCountNote,
        favoritesFilterNote = state.favoritesFilterNote,
        searchHistoryChips = state.searchHistoryChips,
        searchMoreState = state.searchMoreState,
        searchFailedFooter = state.searchFailedFooter,
        searchEmptyNote = state.searchEmptyNote,
        wallListAnchor = state.wallListAnchor,
        wallStaggeredAnchor = state.wallStaggeredAnchor,
        exploreAnchor = state.exploreAnchor,
        wallLayout = state.wallLayout,
        wallFooter = state.wallFooter,
        pixivPreviewLeg = state.pixivPreviewLeg,
        wallSelectedUids = state.wallSelectedUids,
        exploreSectionsState = state.exploreSectionsState,
        exploreNoteState = state.exploreNoteState,
        exploreTickState = state.exploreTickState,
        exploreSourceKeyState = state.exploreSourceKeyState,
        exploreModesState = state.exploreModesState,
        exploreModeIndexState = state.exploreModeIndexState,
        explorePickTickState = state.explorePickTickState,
        explorePickMoreTickState = state.explorePickMoreTickState,
        explorePickPageNoState = state.explorePickPageNoState,
        homeRecommendComicsState = state.homeRecommendComicsState,
        homeRecommendNoteState = state.homeRecommendNoteState,
        favoritesTabPickedState = state.favoritesTabPickedState,
        favoritesSourceKeyState = state.favoritesSourceKeyState,
        discoverViewExploreState = state.discoverViewExploreState,
        searchResultsState = state.searchResultsState,
        searchingState = state.searchingState,
        searchFilterValuesState = state.searchFilterValuesState,
        searchMaxPageState = state.searchMaxPageState,
        searchCanLoadMoreState = state.searchCanLoadMoreState,
        hasSearchedState = state.hasSearchedState,
        searchHistoryState = state.searchHistoryState,
        searchHistoryNoteState = state.searchHistoryNoteState,
        searchCauseState = state.searchCauseState,
        loadMoreTickState = state.loadMoreTickState,
        homeRecommendR18State = state.homeRecommendR18State,
        homeRecommendFailedState = state.homeRecommendFailedState,
        homeRetryTickState = state.homeRetryTickState,
        filesSegmentState = state.filesSegmentState,
        wallPageState = state.wallPageState,
        galleryHomeDomainState = state.galleryHomeDomainState,
        galleryRankingModeState = state.galleryRankingModeState,
        gallerySortState = state.gallerySortState,
        wallRefreshTickState = state.wallRefreshTickState,
        wallRefreshingState = state.wallRefreshingState,
        wallMoreTickState = state.wallMoreTickState,
        booruRatingFilterState = state.booruRatingFilterState,
        booruSiteFilterState = state.booruSiteFilterState,
        wallDensityState = state.wallDensityState,
        pixivDiscoverLegState = state.pixivDiscoverLegState,
        pixivLocalFollowedState = state.pixivLocalFollowedState,
    )
}
