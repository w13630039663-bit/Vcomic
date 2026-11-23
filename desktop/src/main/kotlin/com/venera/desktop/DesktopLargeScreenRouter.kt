package com.venera.desktop
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
internal fun DesktopLargeScreenRouter(
    contentRoute: DesktopRoute,
    settingsOpen: Boolean,
    layoutMode: WideScreenLayoutMode,
    engine: com.venera.engineprobe.EngineSession?,
    ports: GalleryPorts,
    scope: CoroutineScope,
    downloadsRoot: java.io.File?,
    galleryPaths: com.venera.compose.data.platform.PathProvider?,
    galleryPrefs: com.venera.desktop.gallery.data.DesktopGalleryPreferences?,
    galleryGuard: com.venera.desktop.gallery.data.DesktopGalleryContentGuard?,
    readerPrefs: com.venera.desktop.gallery.data.DesktopComicReaderPreferences?,
    desktopUx: DesktopUxPlatformServices,
    businessHandle: com.venera.desktop.business.DesktopBusinessPorts.Handle?,
    queueTasks: List<com.venera.desktop.gallery.data.DesktopComicQueueTask>,
    queueConcurrency: Int,
    queuePrimed: Boolean,
    favoritesLoaded: Boolean,
    favoritesState: MutableState<List<com.venera.compose.data.db.FavoriteRecord>>,
    favoriteFolderNames: List<String>,
    localComicScan: DesktopLocalComicLibraryScan?,
    readerMode: com.venera.desktop.gallery.data.DesktopReaderMode,
    readerModeNote: String?,
    detailCoreCache: MutableMap<String, Pair<CoroutineScope, ComicDetailCore>>,
    sourceUi: SourceManageUiState,
    filesUi: FilesAuxUiState,
    detailUi: DetailAndReaderUiState,
    aggregateSearch: com.venera.desktop.search.DesktopAggregateSearchHolder,
    runSourceImport: () -> Unit,
    writeSourceEnabled: ((String, Boolean) -> Unit)?,
    writeSourceOrder: ((List<String>) -> Unit)?,
    readSourceText: ((String) -> String?)?,
    clearSourceOverride: (suspend (String) -> Unit)?,
    loginWithCookies: (suspend (String, List<String>) -> String)?,
    loginWithPassword: (suspend (String, String, String) -> String)?,
    loginWithWebview: (suspend (String) -> String)?,
    writeHistory: (suspend (com.venera.compose.data.db.HistoryRecord) -> Unit)?,
    addFavorite: (suspend (com.venera.compose.data.db.FavoriteRecord) -> Unit)?,
    removeFavorite: (suspend (String) -> Unit)?,
    createFavoriteFolder: (suspend (String) -> Unit)?,
    deleteFavoriteFolder: (suspend (String) -> Unit)?,
    openSettings: () -> Unit,
    searchComics: (suspend (String, Int, String, List<String?>) -> DesktopComicSearchOutcome)?,
    onDownloadsDelete: (com.venera.desktop.gallery.data.DesktopComicDownloadedChapter, Boolean) -> Unit,
    flyToDownloadCenter: (String?, Boolean) -> Unit,
    routeState: MutableState<DesktopRoute>,
    currentTabState: MutableState<VeneraNavTab>,
    commentsTickState: MutableIntState,
    readerPagesState: MutableState<List<com.venera.desktop.gallery.data.DesktopComicPage>>,
    searchReturnRouteState: MutableState<DesktopRoute?>,
    sourceTickState: MutableIntState,
    downloadsTickState: MutableIntState,
    localComicImportTickState: MutableState<Int>,
    armedDeleteKeyState: MutableState<String?>,
    readerModeChoiceState: MutableState<com.venera.desktop.gallery.data.DesktopReaderMode?>,
    searchQueryState: MutableState<String>,
    searchSourceKeyState: MutableState<String?>,
    searchTickState: MutableIntState,
    shellBackdropUrl: String?,
    wallDensity: DesktopWallDensity,
    artistProfileAnchor: DesktopScrollAnchor,
    dailyCore: com.venera.compose.gallery.ui.GalleryDailyCore,
    dailyGallery: DailyGalleryUiState,
    galleryAuxUi: GalleryAuxUiState,
    openWallCardAny: (DesktopWallCardModel) -> Unit,
    searchHandoff: (List<GallerySite>, String) -> Unit,
    pixivAccountTabState: MutableState<Int>,
    pixivArtistSectionState: MutableState<com.venera.compose.gallery.data.PixivArtistSection>,
    lastPixivArtistIdState: MutableState<Long?>,
    pixivSearchKindState: MutableIntState,
    pixivSearchTermState: MutableState<String>,
    pixivSearchTickState: MutableIntState,
    gallerySearchModeState: MutableState<Int>,
    pixivWorkViewerQueueState: MutableState<List<DesktopWallCardModel>>,
    pixivWorkResumePageState: MutableIntState,
    railPrefs: com.venera.compose.data.platform.JsonKeyValueStore?,
    assetDir: java.io.File?,
    overrideDir: java.io.File?,
    maskModeOf: () -> String,
    wallGuardOf: () -> com.venera.compose.gallery.data.GalleryContentGuard?,
    wallBlockAiOf: () -> Boolean,
    historySourceName: String?,
    readingStats: DesktopReadingStats,
    galleryFavorites: List<com.venera.compose.gallery.data.GalleryFavorite>,
    galleryWallQuality: GalleryPreviewQuality,
    galleryWallCards: List<DesktopWallCardModel>,
    galleryWallBlocked: Int,
    galleryWallUnrenderable: String,
    galleryViewerQueue: List<DesktopWallCardModel>,
    gallerySearchToday: java.time.LocalDate,
    gallerySearchSourceOptions: List<com.venera.compose.gallery.domain.GallerySearchSource>,
    gallerySearchRankingChoices: List<com.venera.desktop.gallery.ui.DesktopRankingChoice>,
    gallerySearchRankingLanding: com.venera.compose.gallery.domain.GalleryRanking,
    gallerySearchPeriodReading: String?,
    gallerySearchCanPeriodBack: Boolean,
    gallerySearchCanPeriodForward: Boolean,
    gallerySearchCards: List<DesktopWallCardModel>,
    gallerySearchFooter: com.venera.desktop.gallery.ui.DesktopGalleryWallFooter,
    pixivSearchCards: List<DesktopWallCardModel>,
    pixivSearchFooter: com.venera.desktop.gallery.ui.DesktopGalleryWallFooter,
    pixivCollectionSearch: PixivCollectionSearchLeg,
    searchResultAnchor: DesktopScrollAnchor,
    searchArtistAnchor: DesktopScrollAnchor,
    backupUi: BackupUiState,
    browseClearUi: BrowseClearUiState,
    openGalleryPostAny: (com.venera.compose.gallery.data.GalleryPost) -> Unit,
    savedUiState: MutableState<SavedUiState>,
    historyTickState: MutableIntState,
    galleryWallTickState: MutableIntState,
    gallerySearchQueryState: MutableState<String>,
    gallerySearchSourceState: MutableState<Int>,
    gallerySearchTickState: MutableIntState,
    gallerySearchMoreTickState: MutableIntState,
    gallerySearchR18State: MutableState<Boolean>,
    gallerySearchPageState: MutableState<DesktopWallPage?>,
    gallerySearchSearchingState: MutableState<Boolean>,
    gallerySearchFailedState: MutableState<Boolean>,
    gallerySearchHistoryState: MutableState<List<GallerySearchEntry>>,
    gallerySearchHistoryNoteState: MutableState<String?>,
    gallerySearchRankingState: MutableState<com.venera.compose.gallery.domain.GalleryRanking>,
    gallerySearchRankingAnchorState: MutableState<java.time.LocalDate?>,
    pixivSearchMoreTickState: MutableIntState,
    pixivSearchFilterState: MutableState<com.venera.compose.gallery.domain.PixivSearchFilter>,
    pixivSearchPageState: MutableState<DesktopWallPage?>,
    pixivSearchSearchingState: MutableState<Boolean>,
    pixivSearchUsersState: MutableState<List<com.venera.compose.gallery.data.PixivUser>>,
    gallerySuggestCandidatesState: MutableState<List<com.venera.compose.gallery.data.GalleryTagSuggestion>>,
    currentTab: VeneraNavTab,
    homeSkinLight: Boolean,
    pixivSuggestEngine: com.venera.compose.data.platform.HttpEngine?,
    exploreCategoryBoard: com.venera.engineprobe.EngineSession.CategoryBoard,
    explorePickUi: ExplorePickUiState,
    favoritesNote: String?,
    netFavUi: NetFavUiState,
    searchFilterGroups: List<com.venera.engineprobe.EngineSession.SearchOption>,
    homeSourceRows: List<DesktopHomeSourceStatusRow>,
    homeHistory: DesktopHomeHistorySnapshot,
    homeStats: DesktopHomeStats,
    homeArtists: com.venera.compose.gallery.domain.GalleryFollowedArtistsReadout,
    favoritesShown: List<com.venera.compose.data.db.FavoriteRecord>,
    favoritesFolderTabs: List<com.venera.desktop.gallery.data.DesktopFavoriteFolderTab>,
    favoritesCountNote: String,
    favoritesFilterNote: String?,
    searchHistoryChips: List<String>,
    searchMoreState: com.venera.desktop.search.DesktopComicSearchMore,
    searchFailedFooter: String?,
    searchEmptyNote: String?,
    wallListAnchor: DesktopScrollAnchor,
    wallStaggeredAnchor: DesktopScrollAnchor,
    exploreAnchor: DesktopScrollAnchor,
    wallLayout: com.venera.compose.gallery.domain.GalleryWallLayout,
    wallFooter: com.venera.desktop.gallery.ui.DesktopGalleryWallFooter,
    pixivPreviewLeg: PixivPreviewLeg,
    wallSelectedUids: androidx.compose.runtime.snapshots.SnapshotStateList<String>,
    exploreSectionsState: MutableState<List<com.venera.engineprobe.EngineSession.Section>>,
    exploreNoteState: MutableState<String?>,
    exploreTickState: MutableIntState,
    exploreSourceKeyState: MutableState<String?>,
    exploreModesState: MutableState<List<String>>,
    exploreModeIndexState: MutableIntState,
    explorePickTickState: MutableIntState,
    explorePickMoreTickState: MutableIntState,
    explorePickPageNoState: MutableIntState,
    homeRecommendComicsState: MutableState<List<com.venera.engineprobe.EngineSession.ComicCard>>,
    homeRecommendNoteState: MutableState<String?>,
    favoritesTabPickedState: MutableState<String?>,
    favoritesSourceKeyState: MutableState<String>,
    discoverViewExploreState: MutableState<Boolean>,
    searchResultsState: MutableState<List<com.venera.engineprobe.EngineSession.ComicCard>>,
    searchingState: MutableState<Boolean>,
    searchFilterValuesState: MutableState<List<String?>>,
    searchMaxPageState: MutableState<Int?>,
    searchCanLoadMoreState: MutableState<Boolean>,
    hasSearchedState: MutableState<Boolean>,
    searchHistoryState: MutableState<List<String>>,
    searchHistoryNoteState: MutableState<String?>,
    searchCauseState: MutableState<String?>,
    loadMoreTickState: MutableIntState,
    homeRecommendR18State: MutableState<Boolean>,
    homeRecommendFailedState: MutableState<Boolean>,
    homeRetryTickState: MutableState<Int>,
    filesSegmentState: MutableState<com.venera.desktop.downloads.DesktopFilesSegment>,
    wallPageState: MutableState<DesktopWallPage?>,
    galleryHomeDomainState: MutableState<GalleryHomeDomain>,
    galleryRankingModeState: MutableState<com.venera.compose.gallery.data.PixivRankingMode>,
    gallerySortState: MutableState<DesktopGallerySort>,
    wallRefreshTickState: MutableIntState,
    wallRefreshingState: MutableState<Boolean>,
    wallMoreTickState: MutableIntState,
    booruRatingFilterState: MutableState<DesktopGalleryRatingFilter>,
    booruSiteFilterState: MutableState<GallerySite?>,
    wallDensityState: MutableState<DesktopWallDensity>,
    pixivDiscoverLegState: MutableState<PixivDiscoverLeg>,
    pixivLocalFollowedState: MutableState<Set<Long>>,
    /** 追更账本；null = 装配点没接 ⇒ 详情页追更按钮与追更视图都不摆。 */
    followDao: com.venera.compose.data.db.ComicFollowDao? = null,
) {
                // ── 追更巡检器：引擎在本层，故在此建；离开路由层时回收临时会话 ──
                val followCooldownHours by (galleryPrefs?.followCooldownHours?.collectAsState()
                    ?: remember { mutableStateOf(com.venera.desktop.gallery.data.DesktopGalleryPreferences.DEFAULT_FOLLOW_COOLDOWN_HOURS) })
                val followChecker = remember(followDao, engine) {
                    followDao?.let { dao ->
                        engine?.let { base ->
                            com.venera.desktop.follow.DesktopFollowUpdateChecker(
                                dao = dao,
                                details = com.venera.desktop.follow.EngineFollowDetailSource { base },
                                cooldownMillisProvider = { followCooldownHours * 60L * 60L * 1000L },
                            )
                        }
                    }
                }
                androidx.compose.runtime.DisposableEffect(followChecker) {
                    onDispose { followChecker?.close() }
                }
                // 启动自动检查默认关；开了才在窗起来后跑一轮（冷却期内的条目自动跳过）。
                val followAutoCheck = galleryPrefs?.followAutoCheck?.collectAsState()?.value ?: false
                androidx.compose.runtime.LaunchedEffect(followChecker, followAutoCheck) {
                    if (followAutoCheck && followChecker != null && followDao != null) {
                        val records = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            runCatching { followDao.listSync() }.getOrDefault(emptyList())
                        }
                        if (records.isNotEmpty()) {
                            followChecker.start(scope, records, force = false)
                        }
                    }
                }
                // ── 网站收藏导追更：取页口要引擎会话，故也在本层建，TabHost 只按夹触发 ──
                val followImporter = remember(followDao) {
                    followDao?.let { com.venera.desktop.follow.DesktopNetworkFollowImporter(it) }
                }
                val onStartFollowImport: ((String, String, String) -> Unit)? =
                    if (engine != null && followImporter != null) {
                        { sourceName, folderId, folderName ->
                            val session = engine
                            val importer = followImporter
                            if (session != null && importer != null) {
                                val followSource = com.venera.desktop.follow.NetworkFavoritesFollowSource(
                                    baseSession = session,
                                    sourceName = sourceName,
                                    folderId = folderId,
                                )
                                importer.start(
                                    scope,
                                    com.venera.desktop.follow.NetFollowImportRequest(
                                        sourceName = sourceName,
                                        folderId = folderId,
                                        folderName = folderName,
                                    ),
                                    followSource::loadPage,
                                    onClose = followSource::close,
                                )
                            }
                        }
                    } else {
                        null
                    }
                val navSlidePx = with(LocalDensity.current) {
                    DesktopNavMotion.slideDistance.roundToPx()
                }
                // 路由级状态保险：分支销毁时 rememberSaveable 落进 holder，回到主 Tab 时清理已退栈的二级路由键避免只增不减。
                val routeSaveableHolder = rememberSaveableStateHolder()
                val trackedSubRoutes = remember { mutableStateListOf<DesktopRoute>() }
                LaunchedEffect(contentRoute) {
                    if (contentRoute == DesktopRoute.Tab) {
                        if (trackedSubRoutes.isNotEmpty()) {
                            val removedCount = trackedSubRoutes.size
                            trackedSubRoutes.forEach { old -> routeSaveableHolder.removeState(old) }
                            trackedSubRoutes.clear()
                            println("D_路由缓存 回Tab清理二级页SavedState 条数=$removedCount")
                        }
                    } else if (contentRoute !in trackedSubRoutes) {
                        trackedSubRoutes.add(contentRoute)
                        while (trackedSubRoutes.size > 8) {
                            val stale = trackedSubRoutes.removeAt(0)
                            routeSaveableHolder.removeState(stale)
                        }
                    }
                }
                AnimatedContent(
                    targetState = contentRoute,
                    transitionSpec = {
                        val fromLevel = desktopRouteLevel(initialState)
                        val toLevel = desktopRouteLevel(targetState)
                        when {
                            toLevel > fromLevel ->
                                DesktopNavMotion.forwardEnter(navSlidePx) togetherWith
                                    DesktopNavMotion.forwardExit(navSlidePx)
                            toLevel < fromLevel ->
                                DesktopNavMotion.backEnter(navSlidePx) togetherWith
                                    DesktopNavMotion.backExit(navSlidePx)
                            else ->
                                DesktopNavMotion.sameLevelEnter() togetherWith
                                    DesktopNavMotion.sameLevelExit()
                        }
                    },
                    label = "desktop-route-motion",
                ) { page ->
                    routeSaveableHolder.SaveableStateProvider(page) {
                    when (val r = page) {
                    // 源编辑为独立 Dialog 窗口，主区域保持渲染源管理背景，避免关闭编辑窗后出现空白。
                    is DesktopRoute.SourceManage,
                    is DesktopRoute.SourceEdit,
                    is DesktopRoute.SourceLogin,
                    is DesktopRoute.Detail,
                    is DesktopRoute.Reader,
                    DesktopRoute.Files,
                    DesktopRoute.Queue,
                    DesktopRoute.Downloads,
                    DesktopRoute.LocalComics,
                    is DesktopRoute.LocalComicReader,
                    is DesktopRoute.ComicComments,
                    is DesktopRoute.BlockingRules -> DesktopComicAndSourceHost(
                        r = r,
                        layoutMode = layoutMode,
                        engine = engine,
                        ports = ports,
                        scope = scope,
                        downloadsRoot = downloadsRoot,
                        galleryPaths = galleryPaths,
                        galleryPrefs = galleryPrefs,
                        galleryGuard = galleryGuard,
                        readerPrefs = readerPrefs,
                        desktopUx = desktopUx,
                        businessHandle = businessHandle,
                        queueTasks = queueTasks,
                        queueConcurrency = queueConcurrency,
                        queuePrimed = queuePrimed,
                        favoritesLoaded = favoritesLoaded,
                        favoritesState = favoritesState,
                        favoriteFolderNames = favoriteFolderNames,
                        localComicScan = localComicScan,
                        readerMode = readerMode,
                        readerModeNote = readerModeNote,
                        detailCoreCache = detailCoreCache,
                        sourceUi = sourceUi,
                        filesUi = filesUi,
                        detailUi = detailUi,
                        aggregateSearch = aggregateSearch,
                        runSourceImport = runSourceImport,
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
                        openSettings = openSettings,
                        searchComics = searchComics,
                        onDownloadsDelete = { ch, forceDirect -> onDownloadsDelete(ch, forceDirect) },
                        flyToDownloadCenter = flyToDownloadCenter,
                        routeState = routeState,
                        currentTabState = currentTabState,
                        commentsTickState = commentsTickState,
                        readerPagesState = readerPagesState,
                        searchReturnRouteState = searchReturnRouteState,
                        sourceTickState = sourceTickState,
                        downloadsTickState = downloadsTickState,
                        localComicImportTickState = localComicImportTickState,
                        armedDeleteKeyState = armedDeleteKeyState,
                        readerModeChoiceState = readerModeChoiceState,
                        searchQueryState = searchQueryState,
                        searchSourceKeyState = searchSourceKeyState,
                        searchTickState = searchTickState,
                        followDao = followDao,
                    )
                    DesktopRoute.PixivAccount,
                    is DesktopRoute.PixivArtist,
                    is DesktopRoute.PixivWork,
                    DesktopRoute.PixivLogin,
                    DesktopRoute.PixivBookmarksUnwired,
                    DesktopRoute.PixivCollections,
                    is DesktopRoute.PixivCollectionDetail,
                    DesktopRoute.PixivisionArticle,
                    DesktopRoute.GalleryDaily -> DesktopPixivRoutesHost(
                        r = r,
                        ports = ports,
                        galleryPaths = galleryPaths,
                        galleryPrefs = galleryPrefs,
                        shellBackdropUrl = shellBackdropUrl,
                        wallDensity = wallDensity,
                        artistProfileAnchor = artistProfileAnchor,
                        dailyCore = dailyCore,
                        dailyGallery = dailyGallery,
                        filesUi = filesUi,
                        galleryAuxUi = galleryAuxUi,
                        openWallCardAny = openWallCardAny,
                        searchHandoff = searchHandoff,
                        flyToDownloadCenter = flyToDownloadCenter,
                        routeState = routeState,
                        pixivAccountTabState = pixivAccountTabState,
                        pixivArtistSectionState = pixivArtistSectionState,
                        lastPixivArtistIdState = lastPixivArtistIdState,
                        pixivSearchKindState = pixivSearchKindState,
                        pixivSearchTermState = pixivSearchTermState,
                        pixivSearchTickState = pixivSearchTickState,
                        gallerySearchModeState = gallerySearchModeState,
                        pixivWorkViewerQueueState = pixivWorkViewerQueueState,
                        pixivWorkResumePageState = pixivWorkResumePageState,
                    )
                    DesktopRoute.Settings -> Unit // 设置改由主内容区之上的叠层渲染（底层页不动）


                    DesktopRoute.GalleryReverseSearch,
                    DesktopRoute.GalleryFavorites,
                    DesktopRoute.GalleryBrowseHistory,
                    DesktopRoute.FollowedArtists,
                    DesktopRoute.History,
                    DesktopRoute.ReadingStats,
                    DesktopRoute.Backup,
                    DesktopRoute.GallerySearch,
                    DesktopRoute.GalleryBookmarksHub,
                    is DesktopRoute.GalleryPostDetail,
                    is DesktopRoute.GalleryPostDetail,
                    is DesktopRoute.GalleryPostViewer -> DesktopGalleryAndAccountHost(
                        r = r,
                        layoutMode = layoutMode,
                        ports = ports,
                        scope = scope,
                        galleryPaths = galleryPaths,
                        galleryPrefs = galleryPrefs,
                        railPrefs = railPrefs,
                        assetDir = assetDir,
                        overrideDir = overrideDir,
                        desktopUx = desktopUx,
                        businessHandle = businessHandle,
                        maskModeOf = maskModeOf,
                        wallGuardOf = wallGuardOf,
                        wallBlockAiOf = wallBlockAiOf,
                        shellBackdropUrl = shellBackdropUrl,
                        historySourceName = historySourceName,
                        readingStats = readingStats,
                        galleryFavorites = galleryFavorites,
                        galleryWallQuality = galleryWallQuality,
                        galleryWallCards = galleryWallCards,
                        galleryWallBlocked = galleryWallBlocked,
                        galleryWallUnrenderable = galleryWallUnrenderable,
                        galleryViewerQueue = galleryViewerQueue,
                        gallerySearchToday = gallerySearchToday,
                        gallerySearchSourceOptions = gallerySearchSourceOptions,
                        gallerySearchRankingChoices = gallerySearchRankingChoices,
                        gallerySearchRankingLanding = gallerySearchRankingLanding,
                        gallerySearchPeriodReading = gallerySearchPeriodReading,
                        gallerySearchCanPeriodBack = gallerySearchCanPeriodBack,
                        gallerySearchCanPeriodForward = gallerySearchCanPeriodForward,
                        gallerySearchCards = gallerySearchCards,
                        gallerySearchFooter = gallerySearchFooter,
                        pixivSearchCards = pixivSearchCards,
                        pixivSearchFooter = pixivSearchFooter,
                        pixivCollectionSearch = pixivCollectionSearch,
                        searchResultAnchor = searchResultAnchor,
                        searchArtistAnchor = searchArtistAnchor,
                        backupUi = backupUi,
                        browseClearUi = browseClearUi,
                        detailUi = detailUi,
                        filesUi = filesUi,
                        galleryAuxUi = galleryAuxUi,
                        sourceUi = sourceUi,
                        openWallCardAny = openWallCardAny,
                        openGalleryPostAny = openGalleryPostAny,
                        searchHandoff = searchHandoff,
                        flyToDownloadCenter = flyToDownloadCenter,
                        routeState = routeState,
                        savedUiState = savedUiState,
                        historyTickState = historyTickState,
                        galleryWallTickState = galleryWallTickState,
                        gallerySearchQueryState = gallerySearchQueryState,
                        gallerySearchSourceState = gallerySearchSourceState,
                        gallerySearchTickState = gallerySearchTickState,
                        gallerySearchMoreTickState = gallerySearchMoreTickState,
                        gallerySearchModeState = gallerySearchModeState,
                        gallerySearchR18State = gallerySearchR18State,
                        gallerySearchPageState = gallerySearchPageState,
                        gallerySearchSearchingState = gallerySearchSearchingState,
                        gallerySearchFailedState = gallerySearchFailedState,
                        gallerySearchHistoryState = gallerySearchHistoryState,
                        gallerySearchHistoryNoteState = gallerySearchHistoryNoteState,
                        gallerySearchRankingState = gallerySearchRankingState,
                        gallerySearchRankingAnchorState = gallerySearchRankingAnchorState,
                        pixivSearchTermState = pixivSearchTermState,
                        pixivSearchKindState = pixivSearchKindState,
                        pixivSearchTickState = pixivSearchTickState,
                        pixivSearchMoreTickState = pixivSearchMoreTickState,
                        pixivSearchFilterState = pixivSearchFilterState,
                        pixivSearchPageState = pixivSearchPageState,
                        pixivSearchSearchingState = pixivSearchSearchingState,
                        pixivSearchUsersState = pixivSearchUsersState,
                        gallerySuggestCandidatesState = gallerySuggestCandidatesState,
                        searchQueryState = searchQueryState,
                        pixivWorkViewerQueueState = pixivWorkViewerQueueState,
                        pixivWorkResumePageState = pixivWorkResumePageState,
                    )
                    DesktopRoute.Tab -> DesktopTabHost(
                        currentTab = currentTab,
                        layoutMode = layoutMode,
                        homeSkinLight = homeSkinLight,
                        ports = ports,
                        galleryPrefs = galleryPrefs,
                        galleryPaths = galleryPaths,
                        railPrefs = railPrefs,
                        scope = scope,
                        downloadsRoot = downloadsRoot,
                        desktopUx = desktopUx,
                        businessHandle = businessHandle,
                        searchHandoff = searchHandoff,
                        pixivSuggestEngine = pixivSuggestEngine,
                        searchComics = searchComics,
                        browseClearUi = browseClearUi,
                        detailUi = detailUi,
                        exploreCategoryBoard = exploreCategoryBoard,
                        explorePickUi = explorePickUi,
                        favoritesLoaded = favoritesLoaded,
                        favoritesState = favoritesState,
                        favoritesNote = favoritesNote,
                        netFavUi = netFavUi,
                        galleryWallCards = galleryWallCards,
                        galleryWallBlocked = galleryWallBlocked,
                        galleryWallUnrenderable = galleryWallUnrenderable,
                        aggregateSearch = aggregateSearch,
                        searchFilterGroups = searchFilterGroups,
                        galleryAuxUi = galleryAuxUi,
                        sourceUi = sourceUi,
                        filesUi = filesUi,
                        localComicScan = localComicScan,
                        homeSourceRows = homeSourceRows,
                        historySourceName = historySourceName,
                        queueTasks = queueTasks,
                        homeHistory = homeHistory,
                        homeStats = homeStats,
                        homeArtists = homeArtists,
                        favoritesShown = favoritesShown,
                        favoritesFolderTabs = favoritesFolderTabs,
                        favoritesCountNote = favoritesCountNote,
                        favoritesFilterNote = favoritesFilterNote,
                        searchHistoryChips = searchHistoryChips,
                        searchMoreState = searchMoreState,
                        searchFailedFooter = searchFailedFooter,
                        searchEmptyNote = searchEmptyNote,
                        wallListAnchor = wallListAnchor,
                        wallStaggeredAnchor = wallStaggeredAnchor,
                        exploreAnchor = exploreAnchor,
                        wallLayout = wallLayout,
                        wallFooter = wallFooter,
                        dailyGallery = dailyGallery,
                        pixivPreviewLeg = pixivPreviewLeg,
                        gallerySearchSourceOptions = gallerySearchSourceOptions,
                        galleryFavorites = galleryFavorites,
                        wallSelectedUids = wallSelectedUids,
                        addFavorite = addFavorite,
                        removeFavorite = removeFavorite,
                        createFavoriteFolder = createFavoriteFolder,
                        deleteFavoriteFolder = deleteFavoriteFolder,
                        openSettings = openSettings,
                        openWallCardAny = openWallCardAny,
                        routeState = routeState,
                        exploreSectionsState = exploreSectionsState,
                        exploreNoteState = exploreNoteState,
                        exploreTickState = exploreTickState,
                        exploreSourceKeyState = exploreSourceKeyState,
                        exploreModesState = exploreModesState,
                        exploreModeIndexState = exploreModeIndexState,
                        explorePickTickState = explorePickTickState,
                        explorePickMoreTickState = explorePickMoreTickState,
                        explorePickPageNoState = explorePickPageNoState,
                        homeRecommendComicsState = homeRecommendComicsState,
                        homeRecommendNoteState = homeRecommendNoteState,
                        favoritesTabPickedState = favoritesTabPickedState,
                        favoritesSourceKeyState = favoritesSourceKeyState,
                        galleryWallTickState = galleryWallTickState,
                        searchQueryState = searchQueryState,
                        discoverViewExploreState = discoverViewExploreState,
                        searchResultsState = searchResultsState,
                        searchingState = searchingState,
                        searchTickState = searchTickState,
                        searchSourceKeyState = searchSourceKeyState,
                        searchFilterValuesState = searchFilterValuesState,
                        searchMaxPageState = searchMaxPageState,
                        searchCanLoadMoreState = searchCanLoadMoreState,
                        hasSearchedState = hasSearchedState,
                        searchHistoryState = searchHistoryState,
                        searchHistoryNoteState = searchHistoryNoteState,
                        searchCauseState = searchCauseState,
                        loadMoreTickState = loadMoreTickState,
                        gallerySearchQueryState = gallerySearchQueryState,
                        gallerySearchSourceState = gallerySearchSourceState,
                        gallerySearchTickState = gallerySearchTickState,
                        gallerySearchModeState = gallerySearchModeState,
                        pixivSearchTermState = pixivSearchTermState,
                        pixivSearchTickState = pixivSearchTickState,
                        historyTickState = historyTickState,
                        gallerySearchR18State = gallerySearchR18State,
                        homeRecommendR18State = homeRecommendR18State,
                        homeRecommendFailedState = homeRecommendFailedState,
                        homeRetryTickState = homeRetryTickState,
                        filesSegmentState = filesSegmentState,
                        wallPageState = wallPageState,
                        galleryHomeDomainState = galleryHomeDomainState,
                        galleryRankingModeState = galleryRankingModeState,
                        gallerySortState = gallerySortState,
                        wallRefreshTickState = wallRefreshTickState,
                        wallRefreshingState = wallRefreshingState,
                        wallMoreTickState = wallMoreTickState,
                        booruRatingFilterState = booruRatingFilterState,
                        booruSiteFilterState = booruSiteFilterState,
                        wallDensityState = wallDensityState,
                        pixivDiscoverLegState = pixivDiscoverLegState,
                        pixivLocalFollowedState = pixivLocalFollowedState,
                        followDao = followDao,
                        followChecker = followChecker,
                        followImporter = followImporter,
                        onStartFollowImport = onStartFollowImport,
                    )
                    } // SaveableStateProvider
                }
                }
}
