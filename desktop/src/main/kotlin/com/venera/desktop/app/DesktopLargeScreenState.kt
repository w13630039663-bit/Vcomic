package com.venera.desktop.app

import com.venera.desktop.ui.BackupUiState
import com.venera.desktop.ui.BrowseClearUiState
import com.venera.desktop.ui.DailyGalleryUiState
import com.venera.desktop.ui.DesktopFlyState
import com.venera.desktop.ui.DesktopHomeSourceStatusRow
import com.venera.desktop.data.DesktopHomeStats
import com.venera.desktop.ui.DesktopScrollAnchor
import com.venera.desktop.ui.DetailAndReaderUiState
import com.venera.desktop.ui.ExplorePickUiState
import com.venera.desktop.ui.FilesAuxUiState
import com.venera.desktop.ui.GalleryAuxUiState
import com.venera.desktop.ui.NetFavUiState
import com.venera.desktop.ui.PixivCollectionSearchLeg
import com.venera.desktop.ui.PixivDiscoverLeg
import com.venera.desktop.ui.PixivPreviewLeg
import com.venera.desktop.ui.SavedUiState
import com.venera.desktop.ui.SourceManageUiState
import com.venera.desktop.data.DesktopHomeHistorySnapshot
import com.venera.desktop.data.DesktopReadingStats
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
import androidx.compose.runtime.Stable

@Stable
internal class DesktopLargeScreenState(
    val layoutMode: WideScreenLayoutMode,
    val scope: CoroutineScope,
    val desktopUx: DesktopUxPlatformServices,
    val businessHandle: com.venera.desktop.business.DesktopBusinessPorts.Handle?,
    val queueTasks: List<com.venera.desktop.gallery.data.DesktopComicQueueTask>,
    val queueConcurrency: Int,
    val queuePrimed: Boolean,
    val favoritesLoaded: Boolean,
    val favoritesState: MutableState<List<com.venera.compose.data.db.FavoriteRecord>>,
    val favoriteFolderNames: List<String>,
    val localComicScan: DesktopLocalComicLibraryScan?,
    val readerMode: com.venera.desktop.gallery.data.DesktopReaderMode,
    val readerModeNote: String?,
    val detailCoreCache: MutableMap<String, Pair<CoroutineScope, ComicDetailCore>>,
    val sourceUi: SourceManageUiState,
    val filesUi: FilesAuxUiState,
    val detailUi: DetailAndReaderUiState,
    val aggregateSearch: com.venera.desktop.search.DesktopAggregateSearchHolder,
    val runSourceImport: () -> Unit,
    val openSettings: () -> Unit,
    val onDownloadsDelete: (com.venera.desktop.gallery.data.DesktopComicDownloadedChapter, Boolean) -> Unit,
    val flyToDownloadCenter: (String?, Boolean) -> Unit,
    val routeState: MutableState<DesktopRoute>,
    val currentTabState: MutableState<VeneraNavTab>,
    val commentsTickState: MutableIntState,
    val readerPagesState: MutableState<List<com.venera.desktop.gallery.data.DesktopComicPage>>,
    val searchReturnRouteState: MutableState<DesktopRoute?>,
    val sourceTickState: MutableIntState,
    val downloadsTickState: MutableIntState,
    val localComicImportTickState: MutableState<Int>,
    val armedDeleteKeyState: MutableState<String?>,
    val readerModeChoiceState: MutableState<com.venera.desktop.gallery.data.DesktopReaderMode?>,
    val searchQueryState: MutableState<String>,
    val searchSourceKeyState: MutableState<String?>,
    val searchTickState: MutableIntState,
    val wallDensity: DesktopWallDensity,
    val artistProfileAnchor: DesktopScrollAnchor,
    val dailyCore: com.venera.compose.gallery.ui.GalleryDailyCore,
    val dailyGallery: DailyGalleryUiState,
    val galleryAuxUi: GalleryAuxUiState,
    val openWallCardAny: (DesktopWallCardModel) -> Unit,
    val pixivAccountTabState: MutableState<Int>,
    val pixivArtistSectionState: MutableState<com.venera.compose.gallery.data.PixivArtistSection>,
    val lastPixivArtistIdState: MutableState<Long?>,
    val pixivSearchKindState: MutableIntState,
    val pixivSearchTermState: MutableState<String>,
    val pixivSearchTickState: MutableIntState,
    val gallerySearchModeState: MutableState<Int>,
    val pixivWorkViewerQueueState: MutableState<List<DesktopWallCardModel>>,
    val pixivWorkResumePageState: MutableIntState,
    val railPrefs: com.venera.compose.data.platform.JsonKeyValueStore?,
    val overrideDir: java.io.File?,
    val historySourceName: String?,
    val readingStats: DesktopReadingStats,
    val galleryFavorites: List<com.venera.compose.gallery.data.GalleryFavorite>,
    val galleryWallQuality: GalleryPreviewQuality,
    val galleryWallCards: List<DesktopWallCardModel>,
    val galleryWallBlocked: Int,
    val galleryWallUnrenderable: String,
    val galleryViewerQueue: List<DesktopWallCardModel>,
    val gallerySearchToday: java.time.LocalDate,
    val gallerySearchSourceOptions: List<com.venera.compose.gallery.domain.GallerySearchSource>,
    val gallerySearchRankingChoices: List<com.venera.desktop.gallery.ui.DesktopRankingChoice>,
    val gallerySearchRankingLanding: com.venera.compose.gallery.domain.GalleryRanking,
    val gallerySearchPeriodReading: String?,
    val gallerySearchCanPeriodBack: Boolean,
    val gallerySearchCanPeriodForward: Boolean,
    val gallerySearchCards: List<DesktopWallCardModel>,
    val gallerySearchFooter: com.venera.desktop.gallery.ui.DesktopGalleryWallFooter,
    val pixivSearchCards: List<DesktopWallCardModel>,
    val pixivSearchFooter: com.venera.desktop.gallery.ui.DesktopGalleryWallFooter,
    val pixivCollectionSearch: PixivCollectionSearchLeg,
    val searchResultAnchor: DesktopScrollAnchor,
    val searchArtistAnchor: DesktopScrollAnchor,
    val backupUi: BackupUiState,
    val browseClearUi: BrowseClearUiState,
    val openGalleryPostAny: (com.venera.compose.gallery.data.GalleryPost) -> Unit,
    val savedUiState: MutableState<SavedUiState>,
    val historyTickState: MutableIntState,
    val galleryWallTickState: MutableIntState,
    val gallerySearchQueryState: MutableState<String>,
    val gallerySearchSourceState: MutableState<Int>,
    val gallerySearchTickState: MutableIntState,
    val gallerySearchMoreTickState: MutableIntState,
    val gallerySearchR18State: MutableState<Boolean>,
    val gallerySearchPageState: MutableState<DesktopWallPage?>,
    val gallerySearchSearchingState: MutableState<Boolean>,
    val gallerySearchFailedState: MutableState<Boolean>,
    val gallerySearchHistoryState: MutableState<List<GallerySearchEntry>>,
    val gallerySearchHistoryNoteState: MutableState<String?>,
    val gallerySearchRankingState: MutableState<com.venera.compose.gallery.domain.GalleryRanking>,
    val gallerySearchRankingAnchorState: MutableState<java.time.LocalDate?>,
    val pixivSearchMoreTickState: MutableIntState,
    val pixivSearchFilterState: MutableState<com.venera.compose.gallery.domain.PixivSearchFilter>,
    val pixivSearchPageState: MutableState<DesktopWallPage?>,
    val pixivSearchSearchingState: MutableState<Boolean>,
    val pixivSearchUsersState: MutableState<List<com.venera.compose.gallery.data.PixivUser>>,
    val gallerySuggestCandidatesState: MutableState<List<com.venera.compose.gallery.data.GalleryTagSuggestion>>,
    val currentTab: VeneraNavTab,
    val homeSkinLight: Boolean,
    val pixivSuggestEngine: com.venera.compose.data.platform.HttpEngine?,
    val exploreCategoryBoard: com.venera.engineprobe.EngineSession.CategoryBoard,
    val explorePickUi: ExplorePickUiState,
    val favoritesNote: String?,
    val netFavUi: NetFavUiState,
    val searchFilterGroups: List<com.venera.engineprobe.EngineSession.SearchOption>,
    val homeSourceRows: List<DesktopHomeSourceStatusRow>,
    val homeHistory: DesktopHomeHistorySnapshot,
    val homeStats: DesktopHomeStats,
    val homeArtists: com.venera.compose.gallery.domain.GalleryFollowedArtistsReadout,
    val favoritesShown: List<com.venera.compose.data.db.FavoriteRecord>,
    val favoritesFolderTabs: List<com.venera.desktop.gallery.data.DesktopFavoriteFolderTab>,
    val favoritesCountNote: String,
    val favoritesFilterNote: String?,
    val searchHistoryChips: List<String>,
    val searchMoreState: com.venera.desktop.search.DesktopComicSearchMore,
    val searchFailedFooter: String?,
    val searchEmptyNote: String?,
    val wallListAnchor: DesktopScrollAnchor,
    val wallStaggeredAnchor: DesktopScrollAnchor,
    val exploreAnchor: DesktopScrollAnchor,
    val wallLayout: com.venera.compose.gallery.domain.GalleryWallLayout,
    val wallFooter: com.venera.desktop.gallery.ui.DesktopGalleryWallFooter,
    val pixivPreviewLeg: PixivPreviewLeg,
    val wallSelectedUids: androidx.compose.runtime.snapshots.SnapshotStateList<String>,
    val exploreSectionsState: MutableState<List<com.venera.engineprobe.EngineSession.Section>>,
    val exploreNoteState: MutableState<String?>,
    val exploreTickState: MutableIntState,
    val exploreSourceKeyState: MutableState<String?>,
    val exploreModesState: MutableState<List<String>>,
    val exploreModeIndexState: MutableIntState,
    val explorePickTickState: MutableIntState,
    val explorePickMoreTickState: MutableIntState,
    val explorePickPageNoState: MutableIntState,
    val homeRecommendComicsState: MutableState<List<com.venera.engineprobe.EngineSession.ComicCard>>,
    val homeRecommendNoteState: MutableState<String?>,
    val favoritesTabPickedState: MutableState<String?>,
    val favoritesSourceKeyState: MutableState<String>,
    val discoverViewExploreState: MutableState<Boolean>,
    val searchResultsState: MutableState<List<com.venera.engineprobe.EngineSession.ComicCard>>,
    val searchingState: MutableState<Boolean>,
    val searchFilterValuesState: MutableState<List<String?>>,
    val searchMaxPageState: MutableState<Int?>,
    val searchCanLoadMoreState: MutableState<Boolean>,
    val hasSearchedState: MutableState<Boolean>,
    val searchHistoryState: MutableState<List<String>>,
    val searchHistoryNoteState: MutableState<String?>,
    val searchCauseState: MutableState<String?>,
    val loadMoreTickState: MutableIntState,
    val homeRecommendR18State: MutableState<Boolean>,
    val homeRecommendFailedState: MutableState<Boolean>,
    val homeRetryTickState: MutableState<Int>,
    val filesSegmentState: MutableState<com.venera.desktop.downloads.DesktopFilesSegment>,
    val wallPageState: MutableState<DesktopWallPage?>,
    val galleryHomeDomainState: MutableState<GalleryHomeDomain>,
    val galleryRankingModeState: MutableState<com.venera.compose.gallery.data.PixivRankingMode>,
    val gallerySortState: MutableState<DesktopGallerySort>,
    val wallRefreshTickState: MutableIntState,
    val wallRefreshingState: MutableState<Boolean>,
    val wallMoreTickState: MutableIntState,
    val booruRatingFilterState: MutableState<DesktopGalleryRatingFilter>,
    val booruSiteFilterState: MutableState<GallerySite?>,
    val wallDensityState: MutableState<DesktopWallDensity>,
    val pixivDiscoverLegState: MutableState<PixivDiscoverLeg>,
    val pixivLocalFollowedState: MutableState<Set<Long>>,
    val comicCache: com.venera.desktop.cache.DesktopComicCache?,
    val exploreSections: List<com.venera.engineprobe.EngineSession.Section>,
    val favoriteFolderComicId: String?,
    val favoriteFolderCurrent: String?,
    val favoriteFolderSelected: String,
    val favoritesTabSelected: String?,
    val favoritesLoadedState: MutableState<Boolean>,
    val favoritesNoteState: MutableState<String?>,
    val favoriteFolderPickedState: MutableState<String?>,
    val localComicScanState: MutableState<DesktopLocalComicLibraryScan?>,
    val lastPageTurnAtState: MutableLongState,
    val currentMaskMode: String,
    val pixivAuthState: com.venera.compose.gallery.data.PixivAuthState,
    val wallCache: androidx.compose.runtime.snapshots.SnapshotStateMap<String, DesktopWallPage>,
    val wallCacheKey: String,
    val cards: List<DesktopWallCardModel>,
    val pixivPreviewCache: androidx.compose.runtime.snapshots.SnapshotStateMap<com.venera.compose.gallery.data.PixivRankingMode, PixivPreviewLeg>,
    val tagDictionary: com.venera.desktop.gallery.data.DesktopGalleryTagDictionary?,
    val galleryWallCardsState: MutableState<List<DesktopWallCardModel>>,
    val galleryWallBlockedState: MutableIntState,
    val galleryWallUnrenderableState: MutableState<String>,
    val lastAppliedRefreshTickState: MutableIntState,
    val wallPhaseState: MutableState<DesktopGalleryMorePhase>,
    val pixivPreviewLegState: MutableState<PixivPreviewLeg>,
    val lastAppliedPreviewTickState: MutableIntState,
    val wallPage: DesktopWallPage?,
    val searchFilterGroupsState: MutableState<List<com.venera.engineprobe.EngineSession.SearchOption>>,
    val searchPageState: MutableIntState,
    val loadingMoreState: MutableState<Boolean>,
    val gallerySearchPhaseState: MutableState<DesktopGalleryMorePhase>,
    val gallerySearchJobState: MutableState<kotlinx.coroutines.Job?>,
    val pixivSearchPhaseState: MutableState<DesktopGalleryMorePhase>,
    val pixivSearchUsersCursorState: MutableState<String?>,
    val exploreCategoryBoardState: MutableState<com.venera.engineprobe.EngineSession.CategoryBoard>,
    val pixivCallback: DesktopPixivCallback?,
    val homeLines: List<Pair<String, String>>,
    val homeLatencyByHost: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Long>,
    val homeProbeStamp: MutableMap<String, Long>,
    val homeProbingHosts: androidx.compose.runtime.snapshots.SnapshotStateList<String>,
    val homeFailedHosts: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Long>,
    val homeScope: CoroutineScope,
    val performSourceUpdateCheck: suspend (Boolean) -> Unit,
    val favoriteFoldersState: MutableState<List<String>>,
    val routeBeforeSettingsState: MutableState<DesktopRoute>,
    val settingsOpen: Boolean,
    val contentRoute: DesktopRoute,
    val desktopShellBackWithSearch: ((DesktopRoute) -> Unit) -> (() -> Unit)?,
    val shellMode: WideScreenLayoutMode,
    val fullscreenChrome: Boolean,
    val railExpandedSavedState: MutableState<Boolean>,
    val pixivAccountAvatarUrl: String?,
    val flyState: DesktopFlyState,
    val flyAnchor: com.venera.compose.components.VeneraSideBarDownloadAnchor,
    val flyHostTopLeftState: MutableState<Offset>,
    val flyPointer: MutableState<Offset?>,
)
