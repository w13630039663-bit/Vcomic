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
internal fun DesktopTabHost(
    currentTab: VeneraNavTab,
    layoutMode: WideScreenLayoutMode,
    homeSkinLight: Boolean,
    ports: GalleryPorts,
    galleryPrefs: com.venera.desktop.gallery.data.DesktopGalleryPreferences?,
    galleryPaths: com.venera.compose.data.platform.PathProvider?,
    railPrefs: com.venera.compose.data.platform.JsonKeyValueStore?,
    scope: CoroutineScope,
    downloadsRoot: java.io.File?,
    desktopUx: DesktopUxPlatformServices,
    businessHandle: com.venera.desktop.business.DesktopBusinessPorts.Handle?,
    searchHandoff: (List<GallerySite>, String) -> Unit,
    pixivSuggestEngine: com.venera.compose.data.platform.HttpEngine?,
    searchComics: (suspend (String, Int, String, List<String?>) -> DesktopComicSearchOutcome)?,
    browseClearUi: BrowseClearUiState,
    detailUi: DetailAndReaderUiState,
    exploreCategoryBoard: com.venera.engineprobe.EngineSession.CategoryBoard,
    explorePickUi: ExplorePickUiState,
    favoritesLoaded: Boolean,
    favoritesState: MutableState<List<com.venera.compose.data.db.FavoriteRecord>>,
    favoritesNote: String?,
    netFavUi: NetFavUiState,
    galleryWallCards: List<DesktopWallCardModel>,
    galleryWallBlocked: Int,
    galleryWallUnrenderable: String,
    aggregateSearch: com.venera.desktop.search.DesktopAggregateSearchHolder,
    searchFilterGroups: List<com.venera.engineprobe.EngineSession.SearchOption>,
    galleryAuxUi: GalleryAuxUiState,
    sourceUi: SourceManageUiState,
    filesUi: FilesAuxUiState,
    localComicScan: DesktopLocalComicLibraryScan?,
    homeSourceRows: List<DesktopHomeSourceStatusRow>,
    historySourceName: String?,
    queueTasks: List<com.venera.desktop.gallery.data.DesktopComicQueueTask>,
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
    dailyGallery: DailyGalleryUiState,
    pixivPreviewLeg: PixivPreviewLeg,
    gallerySearchSourceOptions: List<com.venera.compose.gallery.domain.GallerySearchSource>,
    galleryFavorites: List<com.venera.compose.gallery.data.GalleryFavorite>,
    wallSelectedUids: androidx.compose.runtime.snapshots.SnapshotStateList<String>,
    addFavorite: (suspend (com.venera.compose.data.db.FavoriteRecord) -> Unit)?,
    removeFavorite: (suspend (String) -> Unit)?,
    createFavoriteFolder: (suspend (String) -> Unit)?,
    deleteFavoriteFolder: (suspend (String) -> Unit)?,
    openSettings: () -> Unit,
    openWallCardAny: (DesktopWallCardModel) -> Unit,
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
    homeRecommendComicsState: MutableState<List<com.venera.engineprobe.EngineSession.ComicCard>>,
    homeRecommendNoteState: MutableState<String?>,
    favoritesTabPickedState: MutableState<String?>,
    favoritesSourceKeyState: MutableState<String>,
    galleryWallTickState: MutableIntState,
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
    gallerySearchQueryState: MutableState<String>,
    gallerySearchSourceState: MutableState<Int>,
    gallerySearchTickState: MutableIntState,
    gallerySearchModeState: MutableState<Int>,
    pixivSearchTermState: MutableState<String>,
    pixivSearchTickState: MutableIntState,
    historyTickState: MutableIntState,
    gallerySearchR18State: MutableState<Boolean>,
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
    // ── 追更：账本与巡检器由 Router 那颗引擎所在层建好递下来（本层不碰 engine）──
    followDao: com.venera.compose.data.db.ComicFollowDao?,
    followChecker: com.venera.desktop.follow.DesktopFollowUpdateChecker?,
    followImporter: com.venera.desktop.follow.DesktopNetworkFollowImporter?,
    /** 开始导入某夹（Router 层持引擎，本层不碰会话）；null = 写口没接。 */
    onStartFollowImport: ((sourceName: String, folderId: String, folderName: String) -> Unit)?,
) {
    var route by routeState
    var favorites by favoritesState
    var exploreSections by exploreSectionsState
    var exploreNote by exploreNoteState
    var exploreTick by exploreTickState
    var exploreSourceKey by exploreSourceKeyState
    var exploreModes by exploreModesState
    var exploreModeIndex by exploreModeIndexState
    var explorePickTick by explorePickTickState
    var explorePickMoreTick by explorePickMoreTickState
    var explorePickPageNo by explorePickPageNoState
    var homeRecommendComics by homeRecommendComicsState
    var homeRecommendNote by homeRecommendNoteState
    var favoritesTabPicked by favoritesTabPickedState
    var favoritesSourceKey by favoritesSourceKeyState
    var galleryWallTick by galleryWallTickState
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
    var gallerySearchQuery by gallerySearchQueryState
    var gallerySearchSource by gallerySearchSourceState
    var gallerySearchTick by gallerySearchTickState
    var gallerySearchMode by gallerySearchModeState
    var pixivSearchTerm by pixivSearchTermState
    var pixivSearchTick by pixivSearchTickState
    var historyTick by historyTickState
    var gallerySearchR18 by gallerySearchR18State
    var homeRecommendR18 by homeRecommendR18State
    var homeRecommendFailed by homeRecommendFailedState
    var homeRetryTick by homeRetryTickState
    var filesSegment by filesSegmentState
    var wallPage by wallPageState
    var galleryHomeDomain by galleryHomeDomainState
    var galleryRankingMode by galleryRankingModeState
    var gallerySort by gallerySortState
    var wallRefreshTick by wallRefreshTickState
    var wallRefreshing by wallRefreshingState
    var wallMoreTick by wallMoreTickState
    var booruRatingFilter by booruRatingFilterState
    var booruSiteFilter by booruSiteFilterState
    var wallDensity by wallDensityState
    var pixivDiscoverLeg by pixivDiscoverLegState
    var pixivLocalFollowed by pixivLocalFollowedState
    // ── 追更：账本流与巡检进度在这一层读一次，Hub 只收成品 ──
    val followRecords = followDao?.followsFlow?.collectAsState()?.value.orEmpty()
    val followProgress = followChecker?.progress?.collectAsState()?.value
        ?: com.venera.desktop.follow.FollowCheckProgress()
    val followImportProgress = followImporter?.progress?.collectAsState()?.value
        ?: com.venera.desktop.follow.NetFollowImportProgress()
    // 已订阅的夹键 `源|夹id`：账本一变就重读一次（SQLite 读很轻）。
    var followSyncKeys by remember(followDao) { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(followDao, followRecords) {
        followSyncKeys = followDao?.let { dao ->
            runCatching { dao.listSyncSources() }.getOrDefault(emptyList())
                .map { sync -> "${sync.sourceName}|${sync.networkFolderId}" }
                .toSet()
        }.orEmpty()
    }
    AnimatedContent(
                        targetState = currentTab,
                        transitionSpec = {
                            DesktopNavMotion.sameLevelEnter() togetherWith
                                DesktopNavMotion.sameLevelExit()
                        },
                        label = "desktop-tab-motion",
                    ) { tab ->
                    // 首页问候名那格的 pixiv 账号（浅皮屏吃它取站方账号名；类型不配 = 没接）。
                    val homePixivAccount = ports.pixivAccount as? com.venera.desktop.gallery.data.DesktopPixivAccount
                    when (tab) {
                    VeneraNavTab.GALLERY -> DesktopGalleryWallPane(
                        layoutMode,
                        // 阶段三 T3：从前递"投影后的卡表"（桌面自画墙），现在递整颗墙页值
                        //（账 + 卡 + 落屏墙）—— 共享页吃 `wall` 渲卡，`cards` 那份投影只留作
                        // "点开一张卡要交给看图屏什么"的查表，两枚同源一颗粒子。
                        wallPage = wallPage,
                        wallRefreshing = wallRefreshing,
                        // 页尾那三格吃的是**成品**（判据在 `gallery/ui/DesktopGalleryWallFooter.kt`）；
                        // 这里只搬。屏自己拼 `batch.atEnd ||` 就是同一件事两个出处。
                        wallFooter,
                        onLoadMore = { wallMoreTick++ },
                        // 悬浮：顶栏真输入场的四样接线。pixiv 域发车走 pixivSearchTick 独立轴
                        //（桌面来源轴剥 pixiv 的既定口径），booru 域走 seed 三件套。
                        searchR18 = gallerySearchR18,
                        onPickSearchR18 = { allowed ->
                            gallerySearchR18 = allowed
                            railPrefs?.put("pref_gallery_search_r18", if (allowed) 1 else 0)
                        },
                        onTopSearch = { site, term ->
                            if (site == GallerySite.PIXIV) {
                                gallerySearchMode = 1
                                pixivSearchTerm = term
                                pixivSearchTick++
                            } else {
                                gallerySearchMode = 0
                                val seed = desktopSearchSeedFor(
                                    site?.let { listOf(it) } ?: GallerySite.entries.filter { it != GallerySite.PIXIV },
                                    term,
                                    gallerySearchTick + 1,
                                )
                                gallerySearchQuery = seed.query
                                gallerySearchSource = gallerySearchSourceOptions.indexOf(seed.source).coerceAtLeast(0)
                                gallerySearchTick++
                            }
                            route = DesktopRoute.GallerySearch
                        },
                        pixivSuggestEngine = pixivSuggestEngine,
                        // 画廊首页顶栏那枚 🔖（安卓 `GalleryScreen` 的 actions 里，
                        // 搜索与历史之间）。此前桌面没有这一格 ⇒ hub 只编译得过、点不到，这一枚把门开上。
                        onOpenBookmarks = { route = DesktopRoute.GalleryBookmarksHub },
                        // 历史入口常驻可点，点击时递增 browseTick 强制重读最新浏览记录。
                        onOpenBrowseHistory = {
                            browseClearUi.browseTick++
                            route = DesktopRoute.GalleryBrowseHistory
                        },
                        // 从前这一支根本没有这一枚形参 —— 卡摆着一动不动。现在递进真
                        // 路由；屏那侧只对 `openable` 那一档接点击，打码的卡点不开（A 条：不放大成人图）。
                        onOpenPost = openWallCardAny,
                        // 那一排「图库站 / Pixiv」的内容域与点格（域状态住壳、
                        // 切换换真取数腿——见上面 `galleryHomeDomain` 那段）。
                        domain = galleryHomeDomain,
                        onPickDomain = { picked ->
                            petFilter()
                            galleryHomeDomain = picked
                            wallSelectedUids.clear()
                            wallListAnchor.reset()
                            wallStaggeredAnchor.reset()
                        },
                        // 同口径的交接门：首页画师栏那一格吃的就是这一路，
                        // 画廊首页的画师行点一位画师 = 把他名下的标签经交接槽送去画廊搜索。
                        // （§150 新加的那几枚形参都从这扇**已有的**门走，不另起第二份路由。）
                        followedArtists = homeArtists,
                        onOpenArtist = { artist -> searchHandoff(artist.sites, artist.name) },
                        dailyCards = dailyGallery.homeCards,
                        rankingMode = galleryRankingMode,
                        onPickRankingMode = { picked -> galleryRankingMode = picked },
                        // 滚动位置锚点住壳层，LazyListState 随当前分支创建并随离场释放，避免锁住节点树。
                        gridState = rememberRetainedLazyListState(wallListAnchor),
                        // 阶段三 T3：共享页的端口腿与两枚偏好 —— 装配点递给屏，屏不找静态口。
                        ports = ports,
                        galleryPrefs = galleryPrefs,
                        // pixiv 域那枚「我的 pixiv →」入口（账号页 + 登录）。
                        onOpenPixivAccount = { route = DesktopRoute.PixivAccount },
                        // 图库站：行高密度（盘上记忆）与 Ctrl 多选的选卡账。
                        // 两者都住装配点这一层（行对齐墙的渲染输入，pane/scaffold 只递不存）。
                        wallDensity = wallDensity,
                        onPickWallDensity = { picked ->
                            wallDensity = picked
                            railPrefs?.put("pref_wall_density", picked.ordinal)
                        },
                        wallSelectedUids = wallSelectedUids.toList(),
                        onToggleWallSelect = { card ->
                            val uid = card.uid.ifBlank { card.sourcePost?.uid?.toString().orEmpty() }
                            if (uid.isNotBlank()) {
                                if (uid in wallSelectedUids) wallSelectedUids.remove(uid) else wallSelectedUids.add(uid)
                            }
                        },
                        onClearWallSelect = { wallSelectedUids.clear() },
                        onSetWallSelection = { uids ->
                            wallSelectedUids.clear()
                            wallSelectedUids.addAll(uids)
                        },
                        galleryPaths = galleryPaths,
                        wallLayout = wallLayout,
                        onPickWallLayout = { next -> galleryPrefs?.setWallLayout(next) },
                        staggeredState = rememberRetainedLazyStaggeredGridState(wallStaggeredAnchor),
                        // 阶段二 · 域 2：BOORU 日榜那一排的「查看全部」落点（安卓日榜二级页共享）。
                        onOpenDaily = { route = DesktopRoute.GalleryDaily },
                        // BOORU 主墙排序档（推荐 / 最新 / 最热）与点档。
                        // 与 domain 同族：档状态住壳、切换换真取数腿（首屏那颗 effect 按它分派）。
                        sort = gallerySort,
                        onPickSort = { picked ->
                            gallerySort = picked
                            wallSelectedUids.clear()
                            wallListAnchor.reset()
                            wallStaggeredAnchor.reset()
                        },
                        // 浮动刷新钮：扳一记 = 保留旧墙 + 顶部细进度 + 重取当前档整墙 + 顶缓存。
                        // 不清空 wallPage：切档/切域才换脸，手动刷新不闪空墙。
                        onRefresh = {
                            wallRefreshing = true
                            wallRefreshTick++
                        },
                        // PIXIV 榜单预览条：独立榜单腿的成品（壳里按档取+缓存，见 pixivPreviewCards）。
                        previewCards = pixivPreviewLeg.cards,
                        previewAvatars = pixivPreviewLeg.avatars,
                        previewTitles = pixivPreviewLeg.titles,
                        // 发现带 + 关注新作登录腿，与三枚动作门。
                        pixivDiscover = pixivDiscoverLeg,
                        // 标签词原样交接、来源打「全部」（桌面搜索来源轴的第一档就是它 —— 两站词表不通，不猜站）。
                        onSearchTag = { tag ->
                            searchHandoff(com.venera.compose.gallery.data.GallerySite.entries.toList(), tag)
                        },
                        // 推荐画师点进去 = 画师详情页（用户口径）；从前是拿名字去搜索，等于绕一圈。
                        onSearchUser = { user ->
                            route = DesktopRoute.PixivArtist(
                                userId = user.id,
                                name = user.name,
                                from = DesktopRoute.Tab,
                            )
                        },
                        onTogglePixivFollow = { user ->
                            val pixiv = ports.pixiv
                            val account = ports.pixivAccount
                            if (pixiv != null && account != null) {
                                scope.launch {
                                    val bearer = account.bearerOrNull() ?: return@launch
                                    val target = user.id !in pixivLocalFollowed
                                    val result = if (target) {
                                        pixiv.followAdd(bearer, user.id.toString(), false)
                                    } else {
                                        pixiv.followRemove(bearer, user.id.toString())
                                    }
                                    // 站方答成功才进本地账：失败不翻钮（屏上不发假信号）。
                                    if (result.isSuccess) {
                                        pixivLocalFollowed = if (target) {
                                            pixivLocalFollowed + user.id
                                        } else {
                                            pixivLocalFollowed - user.id
                                        }
                                        pixivDiscoverLeg = pixivDiscoverLeg.copy(followedIds = pixivLocalFollowed)
                                        desktopUx.toast(if (target) "已关注画师" else "已取消关注")
                                    } else {
                                        desktopUx.toast("关注操作未完成")
                                    }
                                }
                            }
                        },
                        pixivisionArticles = galleryAuxUi.pixivisionArticles,
                        onOpenPixivisionArticle = { article ->
                            galleryAuxUi.pixivisionArticleOpen = article
                            route = DesktopRoute.PixivisionArticle
                        },
                        // 图库站：筛选架四件（BOORU 域过滤住壳）。
                        booruRatingFilter = booruRatingFilter,
                        onPickBooruRating = { picked -> booruRatingFilter = picked },
                        booruSiteFilter = booruSiteFilter,
                        onPickBooruSite = { picked -> booruSiteFilter = picked },
                        tagDimensions = galleryAuxUi.tagDimensionGroups,
                        tagDictionaryReady = galleryAuxUi.tagDictReady,
                    )
                    // 显式穷举各 Tab 分支（不写 else），确保新增 Tab 时编译期暴露未接线分支。
                    VeneraNavTab.HOME -> if (homeSkinLight) {
                        // ──：浅皮档整屏交给 DesktopHomeLightScreen
                        //（数据口子一字不动；经典档走本 pane，逐字节不变）──
                        com.venera.desktop.DesktopHomeLightScreen(
                            mode = layoutMode,
                            stats = homeStats,
                            pixivAccount = homePixivAccount,
                            history = homeHistory,
                            onOpenHistory = { record ->
                                val key = desktopComicHistoryOpenSourceOf(
                                    record = record,
                                    currentSourceKey = DESKTOP_EXPLORE_SOURCE,
                                    currentSourceName = historySourceName,
                                ) ?: sourceUi.sourceRows.firstOrNull { src ->
                                    src.key.equals(record.sourceName, ignoreCase = true) ||
                                        src.title.equals(record.sourceName, ignoreCase = true)
                                }?.key ?: record.sourceName.takeIf { it.isNotBlank() }
                                if (key == null) {
                                    historyTick++
                                    route = DesktopRoute.History
                                } else {
                                    detailUi.detailInfo = null
                                    detailUi.detailNote = null
                                    route = DesktopRoute.Detail(
                                        from = route,
                                        comicId = record.comicId,
                                        title = record.title,
                                        cover = record.coverUrl,
                                        sourceKey = key,
                                    )
                                }
                            },
                            // 窄版同口径：投影按栏头档位选腿，切档即换数。
                            daily = if (dailyGallery.homePixivPick) dailyGallery.homePixivCards else dailyGallery.homeCards,
                            followedArtists = homeArtists,
                            recommendComics = if (homeRecommendComics.isNotEmpty()) homeRecommendComics else remember(exploreSections) {
                                // 兜底也只吃「最新」组，别把探索页 7 组全摊回推荐格。
                                exploreSections.firstOrNull { it.title == "最新" }?.comics
                                    ?.takeIf { it.isNotEmpty() }
                                    ?: exploreSections.flatMap { it.comics }
                            },
                            comicMaskedOf = { card ->
                                val jmKey = sourceUi.sourceRows.firstOrNull { it.title.contains("禁漫") }?.key ?: ("j" + "m")
                                desktopUx.coverMaskStateOf(
                                    sourceKey = jmKey,
                                    title = card.title,
                                    author = "",
                                    tags = emptyList(),
                                    comicId = card.id,
                                ) != "VISIBLE"
                            },
                            recommendNote = homeRecommendNote ?: exploreNote,
                            localComicCount = localComicScan?.comics?.size,
                            onOpenPost = openWallCardAny,
                            onOpenArtist = { artist ->
                                searchHandoff(artist.sites, artist.name)
                            },
                            onOpenComicCard = { card ->
                                val jmKey = sourceUi.sourceRows.firstOrNull { it.title.contains("禁漫") }?.key ?: ("j" + "m")
                                // 按取数档位选源：正常档的卡来自默认安全源，只有 R18 档才是禁漫日排行。
                                val targetSourceKey = if (homeRecommendR18) jmKey else DESKTOP_EXPLORE_SOURCE
                                detailUi.detailInfo = null
                                detailUi.detailNote = null
                                route = DesktopRoute.Detail(
                                    from = route,
                                    comicId = card.id,
                                    title = card.title,
                                    cover = card.cover,
                                    sourceKey = targetSourceKey,
                                )
                            },
                            recommendR18 = homeRecommendR18,
                            onPickRecommendR18 = { on ->
                                homeRecommendR18 = on
                                railPrefs?.put("pref_home_recommend_r18", on)
                                homeRecommendComics = emptyList()
                                homeRecommendNote = null
                                homeRetryTick++
                            },
                            recommendFailed = homeRecommendFailed,
                            dailyFailed = dailyGallery.homeFailed,
                            dailyPixiv = dailyGallery.homePixivPick,
                            onPickDailyPixiv = { picked ->
                                dailyGallery.homePixivPick = picked
                                railPrefs?.put("pref_daily_home_pixiv", picked)
                            },
                            homeSourceRows = homeSourceRows,
                        onRetryHome = {
                                homeRecommendComics = emptyList()
                                homeRecommendNote = null
                                homeRecommendFailed = false
                                dailyGallery.homeCards = emptyList()
                                dailyGallery.homePixivCards = emptyList()
                                dailyGallery.homeFailed = false
                                dailyGallery.homeLoadedTick.intValue = -1
                                dailyGallery.homePixivLoadedTick.intValue = -1
                                homeRetryTick++
                            },
                            onOpenLocalComics = if (downloadsRoot == null) null else {
                                {
                                    filesUi.filesDomain = com.venera.desktop.downloads.DesktopFilesDomain.COMIC
                                    filesSegment = com.venera.desktop.downloads.DesktopFilesSegment.LOCAL
                                    route = DesktopRoute.Files
                                }
                            },
                            onGoSourceManage = { route = DesktopRoute.SourceManage },
                            onOpenAllHistory = {
                                historyTick++
                                route = DesktopRoute.History
                            },
                            downloadTasks = queueTasks,
                            onOpenDownloads = if (downloadsRoot == null) null else {
                                {
                                    filesUi.filesDomain = com.venera.desktop.downloads.DesktopFilesDomain.COMIC
                                    filesSegment = com.venera.desktop.downloads.DesktopFilesSegment.QUEUE
                                    route = DesktopRoute.Files
                                }
                            },
                        )
                    } else {
                    DesktopHomePane(
                        layoutMode,
                        homeStats,
                        homeHistory,
                        // 首页历史卡点击在壳层按源可用性分派：不可直开时跳转历史屏展示具体原因，不留死点击。
                        onOpenHistory = { record ->
                            val key = desktopComicHistoryOpenSourceOf(
                                record = record,
                                currentSourceKey = DESKTOP_EXPLORE_SOURCE,
                                currentSourceName = historySourceName,
                            ) ?: sourceUi.sourceRows.firstOrNull { src ->
                                src.key.equals(record.sourceName, ignoreCase = true) ||
                                    src.title.equals(record.sourceName, ignoreCase = true)
                            }?.key ?: record.sourceName.takeIf { it.isNotBlank() }
                            if (key == null) {
                                historyTick++
                                route = DesktopRoute.History
                            } else {
                                detailUi.detailInfo = null
                                detailUi.detailNote = null
                                route = DesktopRoute.Detail(
                                    from = route,
                                    comicId = record.comicId,
                                    title = record.title,
                                    cover = record.coverUrl,
                                    sourceKey = key,
                                )
                            }
                        },
                        // ── ：首页那四格 ──
                        // 每日热门吃独立两源账本；投影按栏头档位选腿，切档即换数。
                        daily = if (dailyGallery.homePixivPick) dailyGallery.homePixivCards else dailyGallery.homeCards,
                        followedArtists = homeArtists,
                        // 兜底退回探索页那份货，也只吃「最新」组（与常驻腿同一口径）。
                        recommendComics = if (homeRecommendComics.isNotEmpty()) homeRecommendComics else remember(exploreSections) {
                            exploreSections.firstOrNull { it.title == "最新" }?.comics
                                ?.takeIf { it.isNotEmpty() }
                                ?: exploreSections.flatMap { it.comics }
                        },
                        recommendNote = homeRecommendNote ?: exploreNote,
                        comicMaskedOf = { card ->
                            // 与探索屏同一判据、同一源键：探索页拿不到 tags，只有源级预设那一道生效。
                            val jmKey = sourceUi.sourceRows.firstOrNull { it.title.contains("禁漫") }?.key ?: ("j" + "m")
                            desktopUx.coverMaskStateOf(
                                sourceKey = jmKey,
                                title = card.title,
                                author = "",
                                tags = emptyList(),
                                comicId = card.id,
                            ) != "VISIBLE"
                        },
                        localComicCount = localComicScan?.comics?.size,
                        // 点一张每日热门 = 进看图屏（与墙上那扇门同一个路由，返回回 Tab 那面墙还在原处）。
                        onOpenPost = openWallCardAny,
                        // 点一位画师 = 把他名下的标签经交接槽送去画廊搜索（来源轴跟着站走）。
                        onOpenArtist = { artist ->
                            searchHandoff(artist.sites, artist.name)
                        },
                        onOpenComicCard = { card ->
                            val jmKey = sourceUi.sourceRows.firstOrNull { it.title.contains("禁漫") }?.key ?: ("j" + "m")
                            detailUi.detailInfo = null
                            detailUi.detailNote = null
                            route = DesktopRoute.Detail(
                                from = route,
                                comicId = card.id,
                                title = card.title,
                                cover = card.cover,
                                // 按取数档位选源：正常档的卡来自默认安全源，只有 R18 档才是禁漫日排行。
                                sourceKey = if (homeRecommendR18) jmKey else DESKTOP_EXPLORE_SOURCE,
                            )
                        },
                        // 没有下载根 = 那棵本级树数不着 ⇒ 门不摆（与探索屏 onOpenLocalComics 同一口径）。
                        // 同一内容只留一扇门——这两颗也开进合并页的「本地漫画」档
                        //（独立 LocalComics 屏只留取证种子直进，屏体与档位吃同一颗 embedded 件）。
                        onOpenLocalComics = if (downloadsRoot == null) null else {
                            {
                                filesUi.filesDomain = com.venera.desktop.downloads.DesktopFilesDomain.COMIC
                                filesSegment = com.venera.desktop.downloads.DesktopFilesSegment.LOCAL
                                route = DesktopRoute.Files
                            }
                        },
                        // ── 宽屏双源批：推荐 R18 档 + 两腿失败可视化/重试。
                        recommendR18 = homeRecommendR18,
                        onPickRecommendR18 = { on ->
                            homeRecommendR18 = on
                            railPrefs?.put("pref_home_recommend_r18", on)
                            homeRecommendComics = emptyList()
                            homeRecommendNote = null
                            homeRetryTick++
                        },
                        recommendFailed = homeRecommendFailed,
                        dailyFailed = dailyGallery.homeFailed,
                        dailyPixiv = dailyGallery.homePixivPick,
                        onPickDailyPixiv = { picked ->
                            dailyGallery.homePixivPick = picked
                            railPrefs?.put("pref_daily_home_pixiv", picked)
                        },
                        onRetryHome = {
                            homeRecommendComics = emptyList()
                            homeRecommendNote = null
                            homeRecommendFailed = false
                            dailyGallery.homeCards = emptyList()
                            dailyGallery.homePixivCards = emptyList()
                            dailyGallery.homeFailed = false
                            dailyGallery.homeLoadedTick.intValue = -1
                            dailyGallery.homePixivLoadedTick.intValue = -1
                            homeRetryTick++
                        },
                        onGoSourceManage = { route = DesktopRoute.SourceManage },
                        onOpenAllHistory = {
                            historyTick++
                            route = DesktopRoute.History
                        },
                        downloadTasks = queueTasks,
                        onOpenDownloads = if (downloadsRoot == null) null else {
                            {
                                filesUi.filesDomain = com.venera.desktop.downloads.DesktopFilesDomain.COMIC
                                filesSegment = com.venera.desktop.downloads.DesktopFilesSegment.QUEUE
                                route = DesktopRoute.Files
                            }
                        },
                        pixivAccount = homePixivAccount,
                    )
                    }
                    VeneraNavTab.FAVORITES -> com.venera.desktop.favorites.DesktopFavoritesHubScreen(
                        mode = layoutMode,
                        // 源面板四路并列，选中键落壳（本地/追更/画廊/源 key）。
                        selectedSource = favoritesSourceKey,
                        onPickSource = { key ->
                            petSource(key)
                            if (key != favoritesSourceKey) {
                                favoritesSourceKey = key
                                if (key != com.venera.desktop.favorites.FAVORITES_SOURCE_LOCAL &&
                                    key != com.venera.desktop.favorites.FAVORITES_SOURCE_GALLERY &&
                                    key != com.venera.desktop.favorites.FAVORITES_SOURCE_FOLLOW
                                ) {
                                    // 切网络路 = 展开那颗源并取首屏（原「点一颗切过去」口径原样搬）。
                                    netFavUi.netFavExpandedKey = key
                                    netFavUi.netFavPage = null
                                    netFavUi.netFavFolders = null
                                    netFavUi.netFavFolderPicked = null
                                    netFavUi.netFavNext = null
                                    netFavUi.netFavPageNo = 1
                                    netFavUi.netFavMaxPage = null
                                    netFavUi.netFavReloadTick++
                                }
                            }
                        },
                        // ── 本地那一档：逐字转交原屏那九枚，一字不重拼 ──
                        shownFavorites = favoritesShown,
                        folderTabs = favoritesFolderTabs,
                        localFolderPicked = favoritesTabPicked,
                        // 排序档与设置页「收藏夹排序」同一枚偏好：改哪边另一处立刻跟。
                        localSortMode = if (
                            galleryPaths?.let {
                                com.venera.desktop.business.DesktopComicPreferencesPort
                                    .getInstance(it).favoriteSortOrder.collectAsState().value
                            } == "title"
                        ) 1 else 0,
                        onPickLocalSortMode = { i ->
                            galleryPaths?.let {
                                com.venera.desktop.business.DesktopComicPreferencesPort
                                    .getInstance(it).setFavoriteSortOrder(if (i == 1) "title" else "time")
                            }
                        },
                        countNote = favoritesCountNote,
                        localCount = if (favoritesLoaded) favorites.size else -1,
                        // 本周读数按**全量收藏**算（不是筛后那一批），只在这一屏装配点转交。
                        localWeekStats = remember(favorites, favoritesLoaded) {
                            if (favoritesLoaded) {
                                com.venera.desktop.favorites.desktopFavWeekStats(
                                    favorites,
                                    System.currentTimeMillis(),
                                )
                            } else {
                                com.venera.desktop.favorites.FavWeekStats.EMPTY
                            }
                        },
                        loaded = favoritesLoaded,
                        note = favoritesNote,
                        filterNote = favoritesFilterNote,
                        // 挑某一档 = 改这一屏"看哪一格"，不动写侧那枚 `favoriteFolderPicked`。
                        // 「全部」那一档交回 null，而屏递进来的正是那颗 tab 身上的 `folder`。
                        onFolderTabSelect = { folder -> petFilter(); favoritesTabPicked = folder },
                        // ── 建/删夹：写侧全在装配点，壳只管扳 tick 重读 ──
                        onCreateFolder = if (createFavoriteFolder == null) {
                            null
                        } else {
                            { name ->
                                scope.launch {
                                    runCatching { createFavoriteFolder?.invoke(name) }
                                    detailUi.favoriteTick++
                                }
                            }
                        },
                        onDeleteFolder = if (deleteFavoriteFolder == null) {
                            null
                        } else {
                            { name ->
                                scope.launch {
                                    runCatching { deleteFavoriteFolder?.invoke(name) }
                                    detailUi.favoriteTick++
                                    if (favoritesTabPicked == name) favoritesTabPicked = null
                                }
                            }
                        },
                        maskedOf = { fav ->
                            desktopUx.coverMaskStateOf(
                                sourceKey = fav.sourceName,
                                title = fav.title,
                                author = fav.author,
                                tags = fav.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                                comicId = fav.comicId,
                            ) != "VISIBLE"
                        },
                        onOpen = { fav ->
                            petWork(fav.title, fav.author, fav.tags.substringBefore(',').trim())
                            detailUi.detailInfo = null
                            detailUi.detailNote = null
                            route = DesktopRoute.Detail(
                                from = route,
                                comicId = fav.comicId,
                                title = fav.title,
                                cover = fav.coverUrl,
                                sourceKey = fav.sourceName,
                            )
                        },
                        onRemove = { fav ->
                            DesktopPetCueBus.emit(DesktopPetCue.REMOVE_FAVORITE)
                            // 移出后本地立刻反映（先把这行摘掉），再异步落库 ——
                            // 等落库再刷会让按钮"按了没反应"。
                            favorites = favorites.filterNot { it.comicId == fav.comicId }
                            scope.launch {
                                runCatching { removeFavorite?.invoke(fav.comicId) }
                            }
                        },
                        onSearchComicTag = null,
                        // 本地收藏卡右键「加入追更」：只登记、不建基线，首轮巡检再补快照。
                        onAddFollowFavorite = if (followDao == null) {
                            null
                        } else {
                            { fav ->
                                scope.launch {
                                    runCatching {
                                        followDao.addIfAbsent(
                                            com.venera.compose.data.db.ComicFollowRecord(
                                                comicId = fav.comicId,
                                                sourceName = fav.sourceName,
                                                title = fav.title,
                                                author = fav.author,
                                                coverUrl = fav.coverUrl,
                                                originType = com.venera.compose.data.db.ComicFollowOrigin.LOCAL_FAV,
                                                createdAt = System.currentTimeMillis(),
                                            ),
                                        )
                                    }.onSuccess { added ->
                                        desktopUx.toast(
                                            com.venera.desktop.settings.DesktopI18n.tr(
                                                if (added) "已加入追更" else "已在追更清单中",
                                                if (added) "Added to follow updates" else "Already in follow updates",
                                            ),
                                        )
                                    }.onFailure { err ->
                                        com.venera.desktop.DesktopErrorBus.report(
                                            category = "漫画",
                                            headline = "加入追更失败",
                                            humanReason = "这一本没写进追更账本，本地收藏本身不受影响；可再点一次。",
                                            detail = "${err::class.java.simpleName}: ${err.message.orEmpty()}",
                                            dedupKey = "comic:follow_add",
                                        )
                                        desktopUx.toastLong(
                                            com.venera.desktop.settings.DesktopI18n.tr(
                                                "加入追更失败：${com.venera.desktop.platform.desktopFailureNoteOf(err)}",
                                                "Failed to follow: ${com.venera.desktop.platform.desktopFailureNoteOf(err)}",
                                            ),
                                        )
                                    }
                                }
                            }
                        },
                        // ── 画廊那一档：成品全在这层算（投影与投卡腿在上面那颗 effect）──
                        // loaded 恒真是**真话**而不是兜底：那颗 store 的 getInstance 构造时同步读盘
                        //（§138 那条），flow 第一次给值就是盘上的账，不存在"还没读回来"那一档。
                        galleryLoaded = true,
                        galleryTotal = galleryFavorites.size,
                        galleryCards = galleryWallCards,
                        galleryBlocked = galleryWallBlocked,
                        galleryUnrenderableLine = galleryWallUnrenderable,
                        // 两步清空：批 77 那三枚纯件逐枚搬（可见性 / label / 该走哪档），
                        // 真清那一笔走那颗 runner（写 + 读回 + 读数都在里面）。
                        galleryClearVisible = galleryFavorites.isNotEmpty() &&
                            com.venera.desktop.gallery.ui.desktopFavoritesClearVisible(galleryFavorites.size),
                        galleryClearLabel = com.venera.desktop.gallery.ui.desktopFavoritesClearLabel(
                            browseClearUi.galleryClearStage,
                            galleryFavorites.size,
                        ),
                        // hub 的武装状态住壳（合并页那侧由屏自己管）。
                        onGalleryClearClick = {
                            val next = com.venera.desktop.gallery.ui.desktopFavoritesClearNext(
                                stage = browseClearUi.galleryClearStage,
                                countAtArm = browseClearUi.galleryClearAtArm,
                                countNow = galleryFavorites.size,
                            )
                            if (next == com.venera.desktop.gallery.ui.DesktopFavoritesClearStage.FIRE) {
                                browseClearUi.galleryClearStage = next
                                scope.launch {
                                    val outcome = com.venera.desktop.gallery.ui
                                        .desktopClearGalleryFavorites(ports.favorites, galleryFavorites.size)
                                    browseClearUi.galleryClearNote =
                                        com.venera.desktop.gallery.ui.desktopFavoritesClearReading(outcome)
                                    galleryWallTick++
                                }
                                browseClearUi.galleryClearStage =
                                    com.venera.desktop.gallery.ui.DesktopFavoritesClearStage.IDLE
                            } else {
                                browseClearUi.galleryClearAtArm = galleryFavorites.size
                                browseClearUi.galleryClearStage = next
                            }
                        },
                        galleryClearNote = browseClearUi.galleryClearNote,
                        // 与那面墙、搜索墙同一个看图落点，不自造第四颗。
                        onOpenGalleryPost = { card -> petImage(card); route = DesktopRoute.GalleryPostViewer(card) },
                        onGalleryRemoveSelected = { uids ->
                            if (uids.isNotEmpty()) {
                                scope.launch {
                                    runCatching { ports.favorites.removeAll(uids) }
                                    browseClearUi.galleryClearNote = "已取消收藏 ${uids.size} 张"
                                }
                            }
                        },
                        // ── 网络那一档：面板数据与右列成品（展开/加载腿全在壳）──
                        netSources = netFavUi.netFavSources.orEmpty(),
                        netActiveSource = netFavUi.netFavSources.orEmpty().firstOrNull { it.key == favoritesSourceKey },
                        netPage = netFavUi.netFavPage,
                        netFolders = netFavUi.netFavFolders,
                        onPickNetFolder = { folderId ->
                            petFilter()
                            if (netFavUi.netFavFolderPicked != folderId) {
                                netFavUi.netFavFolderPicked = folderId
                                netFavUi.netFavPage = null
                                netFavUi.netFavNext = null
                                netFavUi.netFavPageNo = 1
                                netFavUi.netFavMaxPage = null
                                netFavUi.netFavReloadTick++
                            }
                        },
                        netMaskedOf = { row ->
                            desktopUx.coverMaskStateOf(
                                sourceKey = row.sourceKey,
                                title = row.title,
                                author = "",
                                tags = emptyList(),
                                comicId = row.comicId,
                            ) != "VISIBLE"
                        },
                        onOpenNetComic = { row ->
                            petWork(row.title)
                            detailUi.detailInfo = null
                            detailUi.detailNote = null
                            route = DesktopRoute.Detail(
                                from = route,
                                comicId = row.comicId,
                                title = row.title,
                                cover = row.cover,
                                sourceKey = row.sourceKey,
                            )
                        },
                        onGoSourceManage = { route = DesktopRoute.SourceManage },
                        // 刷新当前源 = 与「点同一颗源」同一条原地重取口径。
                        onRefreshNet = {
                            DesktopPetCueBus.emit(DesktopPetCue.LOADING)
                            netFavUi.netFavPage = null
                            netFavUi.netFavReloadTick++
                        },
                        netLoadingMore = netFavUi.netFavLoadingMore,
                        onLoadNetMore = { DesktopPetCueBus.emit(DesktopPetCue.LOADING); netFavUi.netFavMoreTick++ },
                        // ── 追更那一档：清单与进度已在上层读成成品；写口逐枚走账 ──
                        followRecords = followRecords,
                        followProgress = followProgress,
                        onCheckFollow = {
                            DesktopPetCueBus.emit(DesktopPetCue.LOADING)
                            followChecker?.start(scope, followRecords, force = false)
                        },
                        onCancelFollowCheck = { followChecker?.cancel() },
                        onOpenFollow = { rec ->
                            detailUi.detailInfo = null
                            detailUi.detailNote = null
                            route = DesktopRoute.Detail(
                                from = route,
                                comicId = rec.comicId,
                                title = rec.title,
                                cover = rec.coverUrl,
                                sourceKey = rec.sourceName,
                            )
                        },
                        onMarkFollowRead = { rec ->
                            scope.launch {
                                runCatching { followDao?.markRead(rec.comicId, rec.sourceName) }
                            }
                        },
                        onSetFollowStatus = { rec, status ->
                            scope.launch {
                                runCatching { followDao?.setStatus(rec.comicId, rec.sourceName, status) }
                            }
                        },
                        // 单本重查 = `force` 绕过冷却，只查这一本。
                        onRetryFollow = { rec -> followChecker?.start(scope, listOf(rec), force = true) },
                        onBatchRetryFollow = { targets -> followChecker?.start(scope, targets, force = true) },
                        onRemoveFollow = { rec ->
                            scope.launch {
                                runCatching { followDao?.remove(rec.comicId, rec.sourceName) }
                            }
                        },
                        followMaskedOf = { rec ->
                            desktopUx.coverMaskStateOf(
                                sourceKey = rec.sourceName,
                                title = rec.title,
                                author = rec.author,
                                tags = emptyList(),
                                comicId = rec.comicId,
                            ) != "VISIBLE"
                        },
                        // ── 网站收藏导追更：订阅键 / 夹内 id / 进度 / 两个把手 ──
                        followSubscribed = run {
                            val key = netFavUi.netFavExpandedKey
                            key != null &&
                                "${key}|${netFavUi.netFavFolders?.selectedId.orEmpty()}" in followSyncKeys
                        },
                        followComicIds = followRecords
                            .filter { it.sourceName == netFavUi.netFavExpandedKey }
                            .mapTo(HashSet()) { it.comicId },
                        followImportProgress = followImportProgress,
                        // 未登录的源不摆按钮：导入必然拿不到东西。
                        onFollowImport = if (
                            netFavUi.netFavSources.orEmpty()
                                .firstOrNull { it.key == netFavUi.netFavExpandedKey }?.logged == true
                        ) {
                            onStartFollowImport?.let { start ->
                                {
                                    val key = netFavUi.netFavExpandedKey
                                    if (key != null) {
                                        val folderId = netFavUi.netFavFolders?.selectedId.orEmpty()
                                        val folderName = netFavUi.netFavFolders?.let { st ->
                                            st.folders.firstOrNull { it.id == st.selectedId }?.name
                                        }
                                        start(key, folderId, folderName ?: key)
                                    }
                                }
                            }
                        } else {
                            null
                        },
                        onCancelFollowImport = { followImporter?.cancel() },
                        onBatchUnfavoriteNetFavorites = businessHandle?.let { handle ->
                            val sourceCatalog = remember(handle) {
                                com.venera.desktop.business.DesktopBusinessPorts.createSourceCatalog(handle)
                            }
                            val handler: (String, String?, List<com.venera.desktop.favorites.NetFavComicRow>) -> Unit =
                                { sourceKey, folderId, comics ->
                                    scope.launch {
                                        val fn = sourceCatalog.getSource(sourceKey)?.favoriteData?.addOrDelFavorite
                                        if (fn == null) {
                                            desktopUx.toast(
                                                com.venera.desktop.settings.DesktopI18n.tr(
                                                    "该源暂不支持取消网络收藏",
                                                    "This source does not support removing network favorites",
                                                ),
                                            )
                                            return@launch
                                        }
                                        val removedIds = hashSetOf<String>()
                                        var firstErr: String? = null
                                        comics.forEach { row ->
                                            val res = try {
                                                fn(row.comicId, folderId.orEmpty(), false, null)
                                            } catch (t: Throwable) {
                                                Result.failure(t)
                                            }
                                            if (res.isSuccess) {
                                                removedIds += row.comicId
                                            } else if (firstErr == null) {
                                                firstErr = res.exceptionOrNull()?.message
                                            }
                                        }
                                        if (removedIds.isNotEmpty() && netFavUi.netFavExpandedKey == sourceKey) {
                                            netFavUi.netFavPage = netFavUi.netFavPage?.let { page ->
                                                page.copy(comics = page.comics.filterNot { it.comicId in removedIds })
                                            }
                                        }
                                        val okCount = removedIds.size
                                        val failCount = comics.size - okCount
                                        if (failCount > 0) {
                                            com.venera.desktop.DesktopErrorBus.report(
                                                category = "漫画",
                                                headline = "取消网络收藏失败",
                                                humanReason = "这一批里有 $failCount 本没撤掉（多收藏集的源要连点多次才干净）；可稍后重试。",
                                                detail = "$sourceKey: ${firstErr ?: "未知错误"}",
                                                dedupKey = "comic:net_fav_unfav:$sourceKey",
                                            )
                                        }
                                        desktopUx.toast(
                                            when {
                                                failCount == 0 -> com.venera.desktop.settings.DesktopI18n.tr(
                                                    "已取消 $okCount 本网络收藏",
                                                    "Removed $okCount from network favorites",
                                                )
                                                okCount == 0 -> com.venera.desktop.settings.DesktopI18n.tr(
                                                    "取消收藏失败：${com.venera.desktop.platform.desktopFailureNoteOfText(firstErr ?: "未知错误")}",
                                                    "Failed to unfavorite: ${com.venera.desktop.platform.desktopFailureNoteOfText(firstErr ?: "Unknown error")}",
                                                )
                                                else -> com.venera.desktop.settings.DesktopI18n.tr(
                                                    "已取消 $okCount 本 · 失败 $failCount 本",
                                                    "Removed $okCount · Failed $failCount",
                                                )
                                            },
                                        )
                                    }
                                }
                            handler
                        },
                        onBatchFollowNetFavorites = followDao?.let { dao ->
                            { comics ->
                                scope.launch {
                                    var added = 0
                                    val now = System.currentTimeMillis()
                                    comics.forEach { row ->
                                        val ok = runCatching {
                                            dao.addIfAbsent(
                                                com.venera.compose.data.db.ComicFollowRecord(
                                                    comicId = row.comicId,
                                                    sourceName = row.sourceKey,
                                                    title = row.title,
                                                    author = "",
                                                    coverUrl = row.cover,
                                                    originType = com.venera.compose.data.db.ComicFollowOrigin.NET_FOLDER,
                                                    createdAt = now,
                                                ),
                                            )
                                        }.getOrDefault(false)
                                        if (ok) added++
                                    }
                                    desktopUx.toast(
                                        com.venera.desktop.settings.DesktopI18n.tr(
                                            if (added > 0) "已加入 $added 本追更" else "所选条目已在追更清单中",
                                            if (added > 0) "Added $added to follow updates" else "Already in follow updates",
                                        ),
                                    )
                                }
                            }
                        },
                    )


                    VeneraNavTab.SEARCH -> DesktopSearchTabPane(
                        layoutMode = layoutMode,
                        ports = ports,
                        galleryPaths = galleryPaths,
                        scope = scope,
                        desktopUx = desktopUx,
                        businessHandle = businessHandle,
                        searchComics = searchComics,
                        detailUi = detailUi,
                        exploreCategoryBoard = exploreCategoryBoard,
                        explorePickUi = explorePickUi,
                        aggregateSearch = aggregateSearch,
                        searchFilterGroups = searchFilterGroups,
                        sourceUi = sourceUi,
                        filesUi = filesUi,
                        searchHistoryChips = searchHistoryChips,
                        searchMoreState = searchMoreState,
                        searchFailedFooter = searchFailedFooter,
                        searchEmptyNote = searchEmptyNote,
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
                    )
                    VeneraNavTab.EXPLORE -> DesktopExploreScreen(
                        mode = layoutMode,
                        gridState = rememberRetainedLazyGridState(exploreAnchor),
                        // 探索能切源（tool-suite 稿②拍板）。显示名吃当前源键,
                        // 可挑清单吃源管理那一排"开启账";挑中进 `exploreSourceKey`(取数 effect 的键)。
                        sourceKey = exploreSourceKey ?: DESKTOP_EXPLORE_SOURCE,
                        sourceChoices = sourceUi.sourceRows.filter { it.enabled }.map {
                            com.venera.desktop.explore.ExploreSourceChoice(key = it.key, name = it.title)
                        },
                        onPickSource = { key ->
                            petSource(sourceUi.sourceRows.firstOrNull { it.key == key }?.title.orEmpty())
                            if (key != (exploreSourceKey ?: DESKTOP_EXPLORE_SOURCE)) {
                                exploreAnchor.reset()
                                exploreSourceKey = key
                                exploreSections = emptyList()
                                // 换源：正在看的分类结果属于旧源，交回去（否则会串源）。
                                explorePickUi.pick = null
                                explorePickUi.page = null
                                explorePickPageNo = 1
                                explorePickUi.maxPage = null
                            }
                        },
                        modes = exploreModes,
                        selectedMode = exploreModeIndex,
                        onPickMode = { index ->
                            if (index != exploreModeIndex) {
                                exploreModeIndex = index
                                exploreSections = emptyList()
                                exploreTick++
                            }
                        },
                        // ── 分类面板：组与档位都来自源 JS 的 category ──
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
                        note = exploreNote,
                        maskedOf = { card ->
                            // 打码判据走共享的那一条（`UxPlatformServices`），不在这里另算。
                            // 探索页拿不到 tags，所以只有源级预设那一道会生效 ——
                            // 这正是本轮修 C 时补上预设表的原因。
                            desktopUx.coverMaskStateOf(
                                sourceKey = DESKTOP_EXPLORE_SOURCE,
                                title = card.title,
                                author = "",
                                tags = emptyList(),
                                comicId = card.id,
                            ) != "VISIBLE"
                        },
                        onOpen = { card ->
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
                        onAddFavorites = addFavorite?.let { add ->
                            { cards ->
                                scope.launch {
                                    val sourceKey = exploreSourceKey ?: DESKTOP_EXPLORE_SOURCE
                                    val existing = favorites
                                        .map { "${it.sourceName}|${it.comicId}" }
                                        .toSet()
                                    var added = 0
                                    cards.forEach { card ->
                                        val key = "$sourceKey|${card.id}"
                                        if (key !in existing) {
                                            val record = com.venera.compose.data.db.FavoriteRecord(
                                                comicId = card.id,
                                                title = card.title,
                                                author = card.author,
                                                coverUrl = card.cover,
                                                sourceName = sourceKey,
                                                tags = card.tags.joinToString(","),
                                            )
                                            if (runCatching { add(record) }.isSuccess) added++
                                        }
                                    }
                                    detailUi.favoriteTick++
                                    desktopUx.toast("已加入 $added 本收藏")
                                }
                            }
                        },
                        onReload = { exploreTick++ },
                        // 源管理那一屏唯一的入口（此前它挂在那条路由上而全仓无人切过去）。
                        // 不顺手清 `detailInfo` / `readerPages`：那两路各自有 `LaunchedEffect(route)`
                        // 在进屏时自己置空重取，这里多清一次只会让人以为"不清就会串数据"。
                        onOpenSourceManage = { route = DesktopRoute.SourceManage },
                        // 队列不再有第二扇门——探索屏这颗也进合并页的队列档。
                        onOpenQueue = {
                            filesUi.filesDomain = com.venera.desktop.downloads.DesktopFilesDomain.COMIC
                            filesSegment = com.venera.desktop.downloads.DesktopFilesSegment.QUEUE
                            route = DesktopRoute.Files
                        },
                        // 有下载根目录才有那一屏（没有根 ⇒ 那棵树数不着，摆一扇通向空的门更坏）。
                        // 同一扇只留一道——探索这颗"本机已下载"也进合并页档位。
                        onOpenDownloads = if (downloadsRoot == null) null else {
                            {
                                filesUi.filesDomain = com.venera.desktop.downloads.DesktopFilesDomain.COMIC
                                filesSegment = com.venera.desktop.downloads.DesktopFilesSegment.DOWNLOADED
                                route = DesktopRoute.Files
                            }
                        },
                        // 本地漫画与本机已下载共用下载根目录判据（无根目录则隐藏入口），均打开合并页对应页签。
                        onOpenLocalComics = if (downloadsRoot == null) null else {
                            {
                                filesUi.filesDomain = com.venera.desktop.downloads.DesktopFilesDomain.COMIC
                                filesSegment = com.venera.desktop.downloads.DesktopFilesSegment.LOCAL
                                route = DesktopRoute.Files
                            }
                        },
                        // 卡上那枚「已下载 N 话 / 离线不全 齐/总 话」：判据与归一全在纯件里，
                        // 这里只把「这一张卡该念哪一句」递过去（`null` = 本机没有整话 ⇒ 那一角不摆）。
                        offlineBadgeOf = { card ->
                            desktopComicOfflineBadgeLabelOf(
                                badges = filesUi.comicCardBadges,
                                sourceKey = DESKTOP_EXPLORE_SOURCE,
                                comicId = card.id,
                            )
                        },
                    // 进「阅读历史」那一屏。按下去先把那张表重读一次：
                    // 刚读完一话再进去要看得到那一行（`homeHistory` 那份快照本来只在进应用时读一次）。
                    onOpenHistory = {
                        historyTick++
                        route = DesktopRoute.History
                    },
                    // 进「阅读统计」那一屏。那一屏的读源挂在 route 上自己重读
                    // （见下面 `DesktopRoute.ReadingStats` 那一支那颗 effect），这里不必像历史那样
                    // 先 tick 一张表 —— 统计屏每次进都是从库现读。
                    onOpenStats = { route = DesktopRoute.ReadingStats },
                    // 进「备份与还原」那一屏。不 tick 一张表 —— 那一屏每次
                    // 打开都是"当前库的状态"，导与恢两跳都是即时动作（跑完的结果自己带回来）。
                        onOpenBackup = { route = DesktopRoute.Backup },
                        onOpenSettings = { openSettings() },
                    )
                    }
                    }

}
