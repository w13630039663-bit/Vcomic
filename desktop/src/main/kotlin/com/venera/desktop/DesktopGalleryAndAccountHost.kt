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
internal fun DesktopGalleryAndAccountHost(
    r: DesktopRoute,
    layoutMode: WideScreenLayoutMode,
    ports: GalleryPorts,
    scope: CoroutineScope,
    galleryPaths: com.venera.compose.data.platform.PathProvider?,
    galleryPrefs: com.venera.desktop.gallery.data.DesktopGalleryPreferences?,
    railPrefs: com.venera.compose.data.platform.JsonKeyValueStore?,
    assetDir: java.io.File?,
    overrideDir: java.io.File?,
    desktopUx: DesktopUxPlatformServices,
    businessHandle: com.venera.desktop.business.DesktopBusinessPorts.Handle?,
    maskModeOf: () -> String,
    wallGuardOf: () -> com.venera.compose.gallery.data.GalleryContentGuard?,
    wallBlockAiOf: () -> Boolean,
    shellBackdropUrl: String?,
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
    detailUi: DetailAndReaderUiState,
    filesUi: FilesAuxUiState,
    galleryAuxUi: GalleryAuxUiState,
    sourceUi: SourceManageUiState,
    openWallCardAny: (DesktopWallCardModel) -> Unit,
    openGalleryPostAny: (com.venera.compose.gallery.data.GalleryPost) -> Unit,
    searchHandoff: (List<GallerySite>, String) -> Unit,
    flyToDownloadCenter: (String?, Boolean) -> Unit,
    routeState: MutableState<DesktopRoute>,
    savedUiState: MutableState<SavedUiState>,
    historyTickState: MutableIntState,
    galleryWallTickState: MutableIntState,
    gallerySearchQueryState: MutableState<String>,
    gallerySearchSourceState: MutableState<Int>,
    gallerySearchTickState: MutableIntState,
    gallerySearchMoreTickState: MutableIntState,
    gallerySearchModeState: MutableState<Int>,
    gallerySearchR18State: MutableState<Boolean>,
    gallerySearchPageState: MutableState<DesktopWallPage?>,
    gallerySearchSearchingState: MutableState<Boolean>,
    gallerySearchFailedState: MutableState<Boolean>,
    gallerySearchHistoryState: MutableState<List<GallerySearchEntry>>,
    gallerySearchHistoryNoteState: MutableState<String?>,
    gallerySearchRankingState: MutableState<com.venera.compose.gallery.domain.GalleryRanking>,
    gallerySearchRankingAnchorState: MutableState<java.time.LocalDate?>,
    pixivSearchTermState: MutableState<String>,
    pixivSearchKindState: MutableIntState,
    pixivSearchTickState: MutableIntState,
    pixivSearchMoreTickState: MutableIntState,
    pixivSearchFilterState: MutableState<com.venera.compose.gallery.domain.PixivSearchFilter>,
    pixivSearchPageState: MutableState<DesktopWallPage?>,
    pixivSearchSearchingState: MutableState<Boolean>,
    pixivSearchUsersState: MutableState<List<com.venera.compose.gallery.data.PixivUser>>,
    gallerySuggestCandidatesState: MutableState<List<com.venera.compose.gallery.data.GalleryTagSuggestion>>,
    searchQueryState: MutableState<String>,
    pixivWorkViewerQueueState: MutableState<List<DesktopWallCardModel>>,
    pixivWorkResumePageState: MutableIntState,
) {
    var route by routeState
    var savedUi by savedUiState
    var historyTick by historyTickState
    var galleryWallTick by galleryWallTickState
    var gallerySearchQuery by gallerySearchQueryState
    var gallerySearchSource by gallerySearchSourceState
    var gallerySearchTick by gallerySearchTickState
    var gallerySearchMoreTick by gallerySearchMoreTickState
    var gallerySearchMode by gallerySearchModeState
    var gallerySearchR18 by gallerySearchR18State
    var gallerySearchPage by gallerySearchPageState
    var gallerySearchSearching by gallerySearchSearchingState
    var gallerySearchFailed by gallerySearchFailedState
    var gallerySearchHistory by gallerySearchHistoryState
    var gallerySearchHistoryNote by gallerySearchHistoryNoteState
    var gallerySearchRanking by gallerySearchRankingState
    var gallerySearchRankingAnchor by gallerySearchRankingAnchorState
    var pixivSearchTerm by pixivSearchTermState
    var pixivSearchKind by pixivSearchKindState
    var pixivSearchTick by pixivSearchTickState
    var pixivSearchMoreTick by pixivSearchMoreTickState
    var pixivSearchFilter by pixivSearchFilterState
    var pixivSearchPage by pixivSearchPageState
    var pixivSearchSearching by pixivSearchSearchingState
    var pixivSearchUsers by pixivSearchUsersState
    var gallerySuggestCandidates by gallerySuggestCandidatesState
    var searchQuery by searchQueryState
    var pixivWorkViewerQueue by pixivWorkViewerQueueState
    var pixivWorkResumePage by pixivWorkResumePageState
    when (r) {
                    DesktopRoute.GalleryReverseSearch -> DesktopReverseSearchScreen(
                        mode = layoutMode,
                        onBack = { route = DesktopRoute.Tab },
                        onOpenRoute = { route = it },
                        onOpenPost = openWallCardAny,
                    )
                    // 画廊「收藏与历史」合并页承接两条路由，分别落入对应初始页签并各自读取独立数据源。
                    DesktopRoute.GalleryFavorites, DesktopRoute.GalleryBrowseHistory -> DesktopGallerySavedScreen(
                        mode = layoutMode,
                        view = savedUi.view,
                        onPickView = { savedUi = savedUi.copy(view = it) },
                        query = savedUi.query,
                        onQueryChange = { savedUi = savedUi.copy(query = it) },
                        siteKey = savedUi.siteKey,
                        onPickSite = { savedUi = savedUi.copy(siteKey = it) },
                        axis = GallerySite.entries,
                        favorites = galleryWallCards,
                        favoriteTotal = galleryFavorites.size,
                        favoriteBlocked = galleryWallBlocked,
                        // `null` = 还没读回来（或这台机器还没定下数据根）⇒ 屏上念「还没读到」那一档，
                        // 不折成「本机还没点开过图」。
                        history = browseClearUi.browseRows.orEmpty(),
                        historyLoaded = browseClearUi.browseRows != null,
                        clearText = if (savedUi.view == DesktopGallerySavedView.FAVORITES) "清空收藏" else "清空记录",
                        clearCount = if (savedUi.view == DesktopGallerySavedView.FAVORITES) {
                            galleryFavorites.size
                        } else {
                            browseClearUi.browseRows.orEmpty().size
                        },
                        onClear = {
                            if (savedUi.view == DesktopGallerySavedView.FAVORITES) {
                                scope.launch {
                                      val outcome = com.venera.desktop.gallery.ui
                                          .desktopClearGalleryFavorites(ports.favorites, galleryFavorites.size)
                                      browseClearUi.galleryClearNote =
                                          com.venera.desktop.gallery.ui.desktopFavoritesClearReading(outcome)
                                      galleryWallTick++
                                  }
                            } else {
                                val store = browseClearUi.browseStore
                                if (store == null) {
                                    browseClearUi.browseNote = "那份档还没读回来，清不了"
                                } else {
                                    scope.launch {
                                        runCatching { store.clear() }
                                            .onSuccess {
                                                browseClearUi.browseRows = store.rows.value
                                                browseClearUi.browseNote = null
                                            }
                                            .onFailure { t -> browseClearUi.browseNote = "没清掉：${t.javaClass.simpleName}" }
                                    }
                                }
                            }
                        },
                        clearNote = if (savedUi.view == DesktopGallerySavedView.FAVORITES) browseClearUi.galleryClearNote else null,
                        note = if (savedUi.view == DesktopGallerySavedView.FAVORITES) {
                            galleryWallUnrenderable.takeIf { it.isNotEmpty() }
                        } else {
                            browseClearUi.browseNote
                        },
                        onOpenFavorite = { card -> openWallCardAny(card) },
                        onRemoveFavorite = { card ->
                            DesktopPetCueBus.emit(DesktopPetCue.REMOVE_FAVORITE)
                            card.sourcePost?.let { post ->
                                scope.launch { runCatching { ports.favorites.toggle(post) } }
                            }
                        },
                        onRemoveFavorites = { cards ->
                            DesktopPetCueBus.emit(DesktopPetCue.REMOVE_FAVORITE)
                            val uids = cards.mapNotNull { it.sourcePost?.uid ?: it.uid.takeIf(String::isNotBlank) }
                            if (uids.isNotEmpty()) {
                                scope.launch {
                                    runCatching { ports.favorites.removeAll(uids) }
                                        .onSuccess { galleryWallTick++ }
                                }
                            }
                        },
                        // 回原卡：现取那一张的完整帖，再过唯一那颗守卫起口投成卡（不在这里自己拼卡）。
                        // pixiv 没有 board（`boardOf` 对它直接抛），作品号本身够进作品页 —— 单开一支，
                        // 否则那一行按下去只会拿到一条"取不回来"。
                        onOpenHistory = { row ->
                            if (row.site == GallerySite.PIXIV) {
                                browseClearUi.browseNote = null
                                DesktopPixivWorkTrail.clear()
                                route = pixivWorkRouteOf(
                                    illustId = row.postId,
                                    title = "pixiv #${row.postId}",
                                    coverUrl = row.thumbUrl,
                                    from = route,
                                )
                            } else {
                                scope.launch {
                                    val outcome = withContext(Dispatchers.IO) {
                                        runCatching { boardOf(ports, row.site).fetchById(row.postId).getOrThrow() }
                                    }
                                    val fetched = outcome.getOrNull()
                                    val failure = outcome.exceptionOrNull()
                                    val card = if (fetched == null) null else withContext(Dispatchers.IO) {
                                        desktopWallGuardedCards(
                                            listOf(fetched),
                                            galleryWallQuality,
                                            wallGuardOf(),
                                            maskModeOf(),
                                            wallBlockAiOf(),
                                        ).firstOrNull()
                                    }
                                    val recovery = desktopBrowseRecoveryNote(row, fetched, failure, card)
                                    if (recovery == null && card != null) {
                                        browseClearUi.browseNote = null
                                        route = DesktopRoute.GalleryPostViewer(card, from = route)
                                    } else {
                                        browseClearUi.browseNote = recovery
                                    }
                                }
                            }
                        },
                        onRemoveHistory = { row ->
                            val store = browseClearUi.browseStore
                            if (store == null) {
                                browseClearUi.browseNote = "那份档还没读回来，删不了这一条"
                            } else {
                                scope.launch {
                                    runCatching { store.removeOne(row.site.routeKey, row.postId) }
                                        .onSuccess {
                                            browseClearUi.browseRows = store.rows.value
                                            browseClearUi.browseNote = null
                                        }
                                        .onFailure { t -> browseClearUi.browseNote = "这一条没删掉：${t.javaClass.simpleName}" }
                                }
                            }
                        },
                        onRemoveHistories = { rows ->
                            val store = browseClearUi.browseStore
                            if (store == null) {
                                browseClearUi.browseNote = "那份档还没读回来，删不了这些"
                            } else {
                                scope.launch {
                                    runCatching { store.removeMany(rows.map { it.site.routeKey to it.postId }) }
                                        .onSuccess {
                                            browseClearUi.browseRows = store.rows.value
                                            browseClearUi.browseNote = null
                                        }
                                        .onFailure { t -> browseClearUi.browseNote = "没删掉：${t.javaClass.simpleName}" }
                                }
                            }
                        },
                        onBack = { route = DesktopRoute.Tab },
                    )

                    DesktopRoute.FollowedArtists -> com.venera.desktop.gallery.ui.DesktopArtistInboxScreen(
                        mode = layoutMode,
                        ports = ports,
                        paths = galleryPaths,
                        prefs = galleryPrefs,
                        guard = wallGuardOf(),
                        maskMode = maskModeOf(),
                        blockAi = wallBlockAiOf(),
                        onOpenPost = openWallCardAny,
                        onBack = { route = DesktopRoute.Tab },
                        onOpenPixivAccount = { route = DesktopRoute.PixivAccount },
                        onOpenArtist = null,
                    )

                    // 阅读历史屏直接复用共享 HistoryScreenBody 并订阅 HistoryDao.historyFlow，零额外 IO。
                    DesktopRoute.History -> {
                        val handle = businessHandle
                        if (handle == null) {
                            // 装配没接上三枚读数（paths / guard / assetDir 任一缺）⇒ 如实念成因，
                            // 不拿空端口假装读到了数（与详情那一路同一条口径）。
                            Column(
                                modifier = Modifier.fillMaxSize().padding(VeneraTokens.spacing.screenHorizontal),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text("无法打开历史", color = VeneraTokens.color.textSecondary)
                                Spacer(Modifier.height(VeneraTokens.spacing.space4))
                                com.venera.desktop.ui.DesktopBackButton(onClick = { route = DesktopRoute.Tab })
                            }
                        } else {
                            // 内核作用域跟随这一屏（进页建、离页撤），**不引 lifecycle-viewmodel**
                            // （同详情那一路：`viewModel()` 计数守恒在安卓侧，桌面这一路本就没有那一枚）。
                            val historyScope = remember(handle) {
                                CoroutineScope(SupervisorJob() + Dispatchers.IO)
                            }
                            DisposableEffect(historyScope) {
                                onDispose { historyScope.cancel() }
                            }
                            val historyCore = remember(handle) {
                                com.venera.desktop.business.DesktopBusinessPorts.createHistoryCore(handle, historyScope)
                            }
                            // 不再用共享 `HistoryScreenBody` —— 换成桌面自制屏
                            // （「不要用共享 ui 了改成自制的」）。行门控/取键/成因口径原样搬过来。
                            val historyRecords = historyCore.history.collectAsState().value
                            com.venera.desktop.history.DesktopHistoryScreen(
                                mode = layoutMode,
                                records = historyRecords,
                                loaded = true,
                                note = null,
                                countNote = if (historyRecords.isEmpty()) "" else "${historyRecords.size} 条",
                                maskedOf = { record ->
                                    desktopUx.coverMaskStateOf(
                                        sourceKey = record.sourceName,
                                        title = record.title,
                                        author = record.author,
                                        comicId = record.comicId,
                                    ) != "VISIBLE"
                                },
                                openableOf = { record ->
                                    val gate = desktopComicHistoryRowGate(
                                        record,
                                        DESKTOP_EXPLORE_SOURCE,
                                        historySourceName,
                                    )
                                    gate == DesktopComicHistoryRowGate.OPEN || (record.sourceName.isNotBlank() && sourceUi.sourceRows.any {
                                        it.key.equals(record.sourceName, ignoreCase = true) || it.title.equals(record.sourceName, ignoreCase = true)
                                    })
                                },
                                noteOf = { record ->
                                    val isRecognized = record.sourceName.isNotBlank() && sourceUi.sourceRows.any {
                                        it.key.equals(record.sourceName, ignoreCase = true) || it.title.equals(record.sourceName, ignoreCase = true)
                                    }
                                    if (isRecognized) null else desktopComicHistoryRowGateNote(
                                        desktopComicHistoryRowGate(
                                            record,
                                            DESKTOP_EXPLORE_SOURCE,
                                            historySourceName,
                                        )
                                    )
                                },
                                onOpen = { record ->
                                    petWork(record.title, record.author)
                                    val key = desktopComicHistoryOpenSourceOf(
                                        record = record,
                                        currentSourceKey = DESKTOP_EXPLORE_SOURCE,
                                        currentSourceName = historySourceName,
                                    ) ?: sourceUi.sourceRows.firstOrNull { src ->
                                        src.key.equals(record.sourceName, ignoreCase = true) ||
                                            src.title.equals(record.sourceName, ignoreCase = true)
                                    }?.key ?: record.sourceName.takeIf { it.isNotBlank() }
                                    key?.let { effectiveKey ->
                                        detailUi.detailInfo = null
                                        detailUi.detailNote = null
                                        route = DesktopRoute.Detail(
                                            from = route,
                                            comicId = record.comicId,
                                            title = record.title,
                                            cover = record.coverUrl,
                                            sourceKey = effectiveKey,
                                        )
                                    }
                                },
                                onClearAll = { historyCore.clearAll() },
                                onDeleteRecords = { list -> historyCore.deleteRecords(list) },
                                onBack = {
                                    route = DesktopRoute.Tab
                                },
                            )
                        }
                    }


                    // 阅读统计那一屏：从探索屏顶栏那枚「阅读统计」入口进。
                    // 那一屏**不碰库**：读那张表的是上面 `readingStats` 那颗 produceState（键 `route`，
                    // 进屏现读一次），这里只把成品递下去 —— 与历史屏那一支（读表在 `homeHistory`）同族。
                    DesktopRoute.ReadingStats -> DesktopReadingStatsScreen(
                        mode = layoutMode,
                        stats = readingStats,
                        onBack = { route = DesktopRoute.Tab },
                    )

                    // 备份与还原工作台（零 IO 屏：文件框与 IO 全由壳层调度，覆盖模式仍需二次确认）。
                    DesktopRoute.Backup -> {
                        val davState by com.venera.desktop.platform.DesktopWebDavSync.state.collectAsState()
                        val davLastSync by com.venera.desktop.platform.DesktopWebDavSync.lastSync.collectAsState()
                        val inspectAndPreview: (String) -> Unit = { targetPath ->
                            backupUi.lastPath = targetPath
                            backupUi.busy = true
                            backupUi.armed = false
                            backupUi.note = null
                            scope.launch {
                                desktopVeneraInspectFile(targetPath).fold(
                                    onSuccess = { parsed ->
                                        backupUi.preview = parsed
                                        backupUi.selectedModules = DesktopBackupModule.entries
                                            .filter { m -> parsed.isModuleAvailable(m) && m != DesktopBackupModule.SOURCE_ACCOUNTS }
                                            .toSet()
                                        backupUi.importStrategy = DesktopImportStrategy.MERGE
                                        backupUi.importStage = DesktopBackupImportStage.PREVIEW
                                    },
                                    onFailure = { t ->
                                        backupUi.note = "解析失败：${t.message?.take(160) ?: t.javaClass.simpleName}"
                                    },
                                )
                                backupUi.busy = false
                            }
                        }
                        // 拖放：与反向搜索台同一做法，DropTarget 挂窗口 contentPane。
                        // 只在待选态装，进预览/完成或离屏即拆，不与其他屏抢同一个投放目标。
                        DisposableEffect(backupUi.importStage) {
                            if (backupUi.importStage != DesktopBackupImportStage.IDLE ||
                                java.awt.GraphicsEnvironment.isHeadless()
                            ) {
                                backupUi.dragOver = false
                                return@DisposableEffect onDispose {}
                            }
                            val pane = (java.awt.Frame.getFrames()
                                .firstOrNull { it is javax.swing.JFrame && it.isVisible } as? javax.swing.JFrame)?.contentPane
                            val target = pane?.let { host ->
                                java.awt.dnd.DropTarget(host, object : java.awt.dnd.DropTargetAdapter() {
                                    override fun dragEnter(e: java.awt.dnd.DropTargetDragEvent) {
                                        if (e.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.javaFileListFlavor)) {
                                            backupUi.dragOver = true
                                            e.acceptDrag(java.awt.dnd.DnDConstants.ACTION_COPY)
                                        } else {
                                            backupUi.dragOver = false
                                            e.rejectDrag()
                                        }
                                    }

                                    override fun dragExit(e: java.awt.dnd.DropTargetEvent) {
                                        backupUi.dragOver = false
                                    }

                                    override fun drop(e: java.awt.dnd.DropTargetDropEvent) {
                                        backupUi.dragOver = false
                                        if (!e.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.javaFileListFlavor)) {
                                            e.dropComplete(false)
                                            return
                                        }
                                        e.acceptDrop(java.awt.dnd.DnDConstants.ACTION_COPY)
                                        val files = runCatching {
                                            e.transferable.getTransferData(java.awt.datatransfer.DataFlavor.javaFileListFlavor) as? List<*>
                                        }.getOrNull()?.filterIsInstance<java.io.File>().orEmpty()
                                        e.dropComplete(true)
                                        val archive = files.firstOrNull {
                                            it.isFile && (it.name.endsWith(".venera", true) || it.name.endsWith(".json", true))
                                        } ?: return
                                        inspectAndPreview(archive.absolutePath)
                                    }
                                })
                            }
                            pane?.dropTarget = target
                            onDispose {
                                backupUi.dragOver = false
                                pane?.dropTarget = null
                            }
                        }
                        DesktopBackupScreen(
                            mode = layoutMode,
                            assetCounts = backupUi.assetCounts,
                            note = backupUi.note,
                            includeSourceAccounts = backupUi.includeSourceAccounts,
                            importStage = backupUi.importStage,
                            preview = backupUi.preview,
                            selectedModules = backupUi.selectedModules,
                            importStrategy = backupUi.importStrategy,
                            lastRestore = backupUi.veneraRestore,
                            stagingFileName = backupUi.stagingFile?.name,
                            davConfig = backupUi.davConfig,
                            davState = davState,
                            davLastSync = davLastSync,
                            armed = backupUi.armed,
                            busy = backupUi.busy,
                            canFileDialog = desktopCanSaveBackupFile(),
                            importDragOver = backupUi.dragOver,
                            onToggleIncludeAccounts = { backupUi.includeSourceAccounts = it },
                            onExport = {
                                val path = desktopPickBackupSavePath(
                                    null,
                                    desktopVeneraDefaultFileName(System.currentTimeMillis()),
                                )
                                if (path == null) {
                                    backupUi.note = "未导出"
                                } else {
                                    backupUi.lastPath = path
                                    backupUi.busy = true
                                    backupUi.note = null
                                    scope.launch {
                                        desktopVeneraExportTo(
                                            path = path,
                                            paths = galleryPaths,
                                            assetDir = assetDir,
                                            overrideDir = overrideDir,
                                            options = DesktopVeneraExportOptions(backupUi.includeSourceAccounts),
                                        ).fold(
                                            onSuccess = { c ->
                                                backupUi.lastCounts = DesktopBackupCounts(c.history, c.stats, c.guardRules)
                                                backupUi.note = "已导出 .venera 归档：${java.io.File(path).name}"
                                            },
                                            onFailure = { t ->
                                                backupUi.note = "导出失败：${t.message?.take(160) ?: t.javaClass.simpleName}"
                                            },
                                        )
                                        backupUi.busy = false
                                    }
                                }
                            },
                            onOpenDataDir = {
                                val dir = galleryPaths?.dataRoot
                                if (dir != null) {
                                    runCatching {
                                        if (java.awt.Desktop.isDesktopSupported()) {
                                            java.awt.Desktop.getDesktop().open(dir)
                                        }
                                    }.onFailure {
                                        backupUi.note = "无法打开目录：${dir.absolutePath}"
                                    }
                                }
                            },
                            onPickImportFile = {
                                val path = desktopPickBackupOpenPath(null)
                                if (path != null) inspectAndPreview(path)
                            },
                            onInspectStagingFile = {
                                val f = backupUi.stagingFile
                                if (f != null && f.isFile) {
                                    inspectAndPreview(f.absolutePath)
                                } else {
                                    backupUi.note = "中转文件不存在，请重新拉取或从本机选取"
                                }
                            },
                            onClearPreview = {
                                backupUi.armed = false
                                backupUi.preview = null
                                backupUi.importStage = DesktopBackupImportStage.IDLE
                            },
                            onSelectStrategy = { s ->
                                backupUi.importStrategy = s
                                backupUi.armed = false
                            },
                            onToggleModule = { m ->
                                backupUi.armed = false
                                backupUi.selectedModules = if (m in backupUi.selectedModules) {
                                    backupUi.selectedModules - m
                                } else {
                                    backupUi.selectedModules + m
                                }
                            },
                            onSelectAllModules = { all ->
                                backupUi.armed = false
                                val p = backupUi.preview
                                backupUi.selectedModules = if (all && p != null) {
                                    DesktopBackupModule.entries.filter { p.isModuleAvailable(it) }.toSet()
                                } else {
                                    emptySet()
                                }
                            },
                            onArmRestore = {
                                backupUi.armed = true
                                backupUi.note = "再次点击「确认覆盖所选数据」将先清空所选模块后写入"
                            },
                            onConfirmRestore = {
                                val parsed = backupUi.preview
                                if (parsed != null) {
                                    backupUi.busy = true
                                    backupUi.armed = false
                                    backupUi.note = null
                                    scope.launch {
                                        desktopVeneraImportFromPreview(
                                            preview = parsed,
                                            selected = backupUi.selectedModules,
                                            strategy = backupUi.importStrategy,
                                            paths = galleryPaths,
                                            assetDir = assetDir,
                                        ).fold(
                                            onSuccess = { outcome ->
                                                backupUi.veneraRestore = outcome
                                                backupUi.lastRestore = DesktopRestoreOutcome(
                                                    history = outcome.history,
                                                    stats = outcome.stats,
                                                    guardImported = outcome.guardImported,
                                                    guardSkipped = outcome.guardSkipped,
                                                )
                                                backupUi.importStage = DesktopBackupImportStage.DONE
                                                backupUi.assetRefreshTick++
                                            },
                                            onFailure = { t ->
                                                backupUi.note = "导入失败：${t.message?.take(160) ?: t.javaClass.simpleName}"
                                            },
                                        )
                                        backupUi.busy = false
                                    }
                                }
                            },
                            onResetImportDone = {
                                backupUi.armed = false
                                backupUi.preview = null
                                backupUi.importStage = DesktopBackupImportStage.IDLE
                            },
                            onSaveDavConfig = { cfg ->
                                galleryPaths?.let { p ->
                                    com.venera.desktop.platform.DesktopWebDavSync.saveConfig(p, cfg)
                                    backupUi.davConfig = com.venera.desktop.platform.DesktopWebDavSync.readConfig(p)
                                    backupUi.note = "WebDAV 配置已保存"
                                }
                            },
                            onTestDavConnection = { cfg ->
                                scope.launch {
                                    com.venera.desktop.platform.DesktopWebDavSync.testConnection(cfg)
                                }
                            },
                            onDavUpload = {
                                galleryPaths?.let { p ->
                                    scope.launch {
                                        com.venera.desktop.platform.DesktopWebDavSync.upload(
                                            paths = p,
                                            assetDir = assetDir,
                                            overrideDir = overrideDir,
                                            includeSourceAccounts = backupUi.includeSourceAccounts,
                                        )
                                    }
                                }
                            },
                            onDavDownloadAndPreview = {
                                galleryPaths?.let { p ->
                                    scope.launch {
                                        val st = com.venera.desktop.platform.DesktopWebDavSync.downloadLatest(p)
                                        val downloaded = com.venera.desktop.platform.DesktopWebDavSync.lastDownloadedFile.value
                                        if (st is com.venera.desktop.platform.DesktopWebDavSync.State.Done && downloaded != null) {
                                            backupUi.stagingFile = downloaded
                                            inspectAndPreview(downloaded.absolutePath)
                                        }
                                    }
                                }
                            },
                            onDismissNote = { backupUi.note = null },
                            onBack = {
                                backupUi.armed = false
                                route = DesktopRoute.Tab
                            },
                        )
                    }

                    DesktopRoute.GallerySearch -> DesktopGallerySearchScreen(
                        mode = layoutMode,
                        // 分支内构造并随离场释放，仅在壳层保留轻量滚动锚点恢复位置。
                        resultGridState = rememberRetainedLazyStaggeredGridState(searchResultAnchor),
                        artistListState = rememberRetainedLazyListState(searchArtistAnchor),
                        query = gallerySearchQuery,
                        onQueryChange = { gallerySearchQuery = it },
                        onSearch = {
                            petSearch(searchQuery)
                            val firedWord = if (gallerySearchMode == 1) pixivSearchTerm else gallerySearchQuery
                            petSearch(firedWord)
                            // 发车落账（顶栏同参）：pixiv 档记 PIXIV 一站、图库档记这一轮真发的
                            // booru 腿；两域条目各带各的站标记，下拉按模式过滤就天然分开。
                            desktopSearchHistoryEntry(
                                if (gallerySearchMode == 1) {
                                    listOf(com.venera.compose.gallery.data.GallerySite.PIXIV)
                                } else {
                                    gallerySearchSourceOptions[gallerySearchSource.coerceIn(0, gallerySearchSourceOptions.lastIndex)]
                                        .availableLegs(desktopGelbooruConfigured(ports))
                                },
                                firedWord,
                            )?.let { entry ->
                                val snapshot = gallerySearchHistory
                                scope.launch {
                                    val next = withContext(Dispatchers.IO) {
                                        runCatching { desktopHistoryPush(desktopHistoryStore(ports), snapshot, entry) }
                                    }
                                    next.getOrNull()?.let { gallerySearchHistory = it }
                                }
                            }
                            if (gallerySearchMode == 1) pixivSearchTick++ else gallerySearchTick++
                        },
                        searching = if (gallerySearchMode == 1) pixivSearchSearching else gallerySearchSearching,
                        failed = if (gallerySearchMode == 1) {
                            pixivSearchPage == null && pixivSearchCards.isEmpty() && pixivSearchFooter.lines.isNotEmpty()
                        } else {
                            gallerySearchFailed
                        },
                        onRetry = {
                            if (gallerySearchQuery.isNotBlank()) {
                                if (gallerySearchMode == 1) pixivSearchTick++ else gallerySearchTick++
                            }
                        },
                        sources = gallerySearchSourceOptions.map { it.label },
                        selectedSource = gallerySearchSource,
                        onPickSource = { index ->
                            petSource(gallerySearchSourceOptions[index].label)
                            gallerySearchSource = index
                            // 换来源档 ⇒ 另起一轮（屏只交出「换档了」这一下，重取由壳那枚 effect 做）。
                            // 排行档跟着落：新腿兑现不了的档落回默认（与排行榜屏同一颗判据），不留假开关。
                            gallerySearchRanking = desktopRankingLanding(
                                gallerySearchSourceOptions[index].availableLegs(desktopGelbooruConfigured(ports)),
                                gallerySearchRanking,
                            )
                            gallerySearchTick++
                        },
                        // ── ：双模式 + 图库排行日期窗 + Pixiv 五段筛选 ──
                        searchMode = gallerySearchMode,
                        onPickMode = { mode ->
                            petFilter()
                            gallerySearchMode = mode
                            gallerySearchRankingAnchor = null
                        },
                        rankingChoices = gallerySearchRankingChoices,
                        selectedRanking = gallerySearchRankingLanding,
                        onPickRanking = { picked ->
                            petFilter()
                            gallerySearchRanking = picked
                            gallerySearchRankingAnchor = null
                            gallerySearchTick++
                        },
                        periodReading = gallerySearchPeriodReading,
                        canPeriodBack = gallerySearchCanPeriodBack,
                        canPeriodForward = gallerySearchCanPeriodForward,
                        onPeriodBack = {
                            gallerySearchRankingAnchor = desktopRankingStep(
                                gallerySearchRankingLanding, gallerySearchRankingAnchor,
                                backwards = true, todayUtc = gallerySearchToday,
                            )
                            gallerySearchTick++
                        },
                        onPeriodForward = {
                            gallerySearchRankingAnchor = desktopRankingStep(
                                gallerySearchRankingLanding, gallerySearchRankingAnchor,
                                backwards = false, todayUtc = gallerySearchToday,
                            )
                            gallerySearchTick++
                        },
                        pixivTerm = pixivSearchTerm,
                        onPixivTermChange = { pixivSearchTerm = it },
                        pixivFilter = pixivSearchFilter,
                        onPixivFilter = { petFilter(); pixivSearchFilter = it },
                        pixivKind = pixivSearchKind,
                        onPickPixivKind = { picked ->
                            petFilter()
                            pixivSearchKind = picked
                            // 维度换档重发一轮：两维是两份结果账，不互相当作"已搜过"。
                            if (pixivSearchTerm.isNotBlank()) pixivSearchTick++
                        },
                        pixivUsers = pixivSearchUsers,
                        onOpenArtist = { user ->
                            route = DesktopRoute.PixivArtist(
                                userId = user.id,
                                name = user.name,
                                from = DesktopRoute.GallerySearch,
                            )
                        },
                        // ── 珍藏册维度（目标下拉第三项）──
                        collections = ports.pixivCollections,
                        pixivCollectionCards = pixivCollectionSearch.cards,
                        pixivCollectionTotal = pixivCollectionSearch.total,
                        pixivCollectionHasMore = pixivCollectionSearch.offset > 0,
                        onLoadMoreCollections = { pixivSearchMoreTick++ },
                        onOpenCollection = { card ->
                            route = DesktopRoute.PixivCollectionDetail(
                                collectionId = card.id,
                                title = card.title,
                                coverUrl = card.thumbnailImageUrl,
                                from = DesktopRoute.GallerySearch,
                            )
                        },
                        avatars = if (gallerySearchMode == 1) pixivSearchPage?.avatars.orEmpty() else gallerySearchPage?.avatars.orEmpty(),
                        titles = if (gallerySearchMode == 1) pixivSearchPage?.titles.orEmpty() else gallerySearchPage?.titles.orEmpty(),
                        searchR18 = gallerySearchR18,
                        onPickSearchR18 = { allowed ->
                            gallerySearchR18 = allowed
                            railPrefs?.put("pref_gallery_search_r18", if (allowed) 1 else 0)
                            if (gallerySearchMode == 0 && gallerySearchQuery.isNotBlank()) {
                                gallerySearchTick++
                            }
                        },
                        history = gallerySearchHistory,
                        onPickHistory = { entry ->
                            // 点一条历史：装回它当初那串词与那一档来源，并另起一轮
                            // （连点同一条不另起一轮会被取数守卫整笔挡掉）。
                            val landing = desktopHistoryLanding(entry)
                            val pixivEntry = landing.source.sites == listOf(com.venera.compose.gallery.data.GallerySite.PIXIV)
                            if (pixivEntry) {
                                gallerySearchMode = 1
                                pixivSearchTerm = landing.query
                                pixivSearchTick++
                            } else {
                                gallerySearchMode = 0
                                gallerySearchQuery = landing.query
                                gallerySearchSource = gallerySearchSourceOptions
                                    .indexOfFirst { it.sites == landing.source.sites }
                                    .coerceAtLeast(0)
                                gallerySearchTick++
                            }
                        },
                        onClearHistory = {
                            val cleared = gallerySearchHistory
                            gallerySearchHistory = emptyList()
                            scope.launch {
                                val did = withContext(Dispatchers.IO) {
                                    runCatching { desktopHistoryClearAll(desktopHistoryStore(ports)) }
                                }
                                gallerySearchHistoryNote = if (did.isSuccess) {
                                    null
                                } else {
                                    desktopComicSearchHistoryNoteText(
                                        did.exceptionOrNull()?.let {
                                            "${it.javaClass.simpleName} ${it.message?.take(120) ?: ""}"
                                        },
                                    )
                                }
                                if (did.isFailure) gallerySearchHistory = cleared
                            }
                        },
                        historyNote = gallerySearchHistoryNote,
                        cards = if (gallerySearchMode == 1) pixivSearchCards else gallerySearchCards,
                        footer = if (gallerySearchMode == 1) pixivSearchFooter else gallerySearchFooter,
                        onLoadMore = if (gallerySearchMode == 1) {
                            { pixivSearchMoreTick++ }
                        } else {
                            { gallerySearchMoreTick++ }
                        },
                        // 标签联想那一排。三枚成品由壳里那颗 effect 算好（并表复检 +
                        // 那句读数都在两颗既有纯件里），屏只搬。点一枚候选装回输入框而不自动发车
                        //（对岸 applyHistory(autoSearch=false) 同口径：补全给的是词根，未必就是用户要的整串）。
                        suggestCandidates = gallerySuggestCandidates,
                        suggestLine = galleryAuxUi.gallerySuggestLine,
                        onPickSuggestion = { name ->
                            if (gallerySearchMode == 1) {
                                pixivSearchTerm = name
                            } else {
                                gallerySearchQuery = name
                            }
                        },
                        // 搜索屏返回统一收口到标题栏面包屑；点击结果卡复用桌面看图屏并受 openable 门控。
                        onOpenPost = openWallCardAny,
                        // 点 pixiv 结果卡上的画师名 → 画师详情页（桌内跳转，不外抛浏览器）。
                        onOpenCardArtist = { id, name ->
                            route = DesktopRoute.PixivArtist(
                                userId = id,
                                name = name,
                                from = DesktopRoute.GallerySearch,
                            )
                        },
                    )

                    // 接入共享 GalleryBookmarksHubScreen：本地收藏切至 FAVORITES Tab，Pixiv 书签落未接入说明页，返回回 Tab。
                    DesktopRoute.GalleryBookmarksHub -> com.venera.compose.gallery.ui.GalleryBookmarksHubScreen(
                        // 合主线后这一屏换成「本地收藏瀑布流」那一形（原来只有两颗卡）。
                        // 形参跟着换：点一张收藏图 → 进看图屏（与墙上那扇门同一个路由）。
                        onOpenPost = { post ->
                            petPost(post.author)
                            // pixiv 帖子的收藏卡也进作品详情页（统一判据在
                            // `openGalleryPostAny`）；booru 帖子照旧直接进看图屏。
                            openGalleryPostAny(post)
                        },
                        // pixiv 云端书签：起账号屏（PixivAccount）的「收藏」段是落点，
                        // 不再念"还没接"（pixiv 端口已真接线，收藏腿 `bookmarkedIllusts` 在）。
                        onOpenPixivBookmarks = { route = DesktopRoute.PixivAccount },
                        onBack = { route = DesktopRoute.Tab },
                        // 收藏档走装配点手上那颗（与 ports.favorites 同一份契约）。
                        favorites = ports.favorites,
                    )

                    is DesktopRoute.GalleryPostDetail -> {
                        val savePaths = galleryPaths
                        val pixivZoomNow = (galleryPrefs?.pixivZoomQuality
                            ?: constFlow(com.venera.compose.gallery.domain.PixivImageQuality.LARGE)).collectAsState().value
                        com.venera.desktop.gallery.ui.DesktopGalleryPostDetailScreen(
                            post = r.post,
                            favoriteUids = galleryFavorites.map { it.uid }.toSet(),
                            lexicon = ports.lexicon,
                            pixiv = ports.pixiv,
                            paths = galleryPaths,
                            // 「动图逐帧播」档 != NEVER（WIFI_ONLY 桌面按"播"，与设置行文案一致）→ gif 详情页自动起播。
                            animatedAutoPlay = (galleryPrefs?.galleryAnimated
                                ?: constFlow(com.venera.compose.gallery.domain.GalleryAnimatedMode.ALWAYS))
                                .collectAsState().value != com.venera.compose.gallery.domain.GalleryAnimatedMode.NEVER,
                            // 「视频自动播放」总闸：false ⇒ 详情页视频不自动起播（退首帧，可手动播）。
                            videoAutoPlay = (galleryPrefs?.videoAutoPlay ?: constFlow(true)).collectAsState().value,
                            // 图库设置扩批三件：详情页画布取图档 / 右栏宽度 / 展开态记忆（写口落盘在壳）。
                            detailQuality = (galleryPrefs?.detailQuality
                                ?: constFlow(com.venera.desktop.gallery.ui.DesktopDetailTier.SAMPLE))
                                .collectAsState().value,
                            paneWidth = (galleryPrefs?.detailPaneWidth
                                ?: constFlow(com.venera.desktop.gallery.ui.DesktopDetailPaneWidth.Standard))
                                .collectAsState().value,
                            paneOpenDefault = (galleryPrefs?.detailPaneOpen ?: constFlow(true)).collectAsState().value,
                            onPaneOpenPersist = { on -> galleryPrefs?.setDetailPaneOpen(on) },
                            backdropFromShell = shellBackdropUrl != null,
                            onBack = { route = r.from ?: DesktopRoute.Tab },
                            onOpenViewer = { card ->
                                route = DesktopRoute.GalleryPostViewer(card, from = r)
                            },
                            onToggleFavorite = { card ->
                                DesktopPetCueBus.emit(DesktopPetCue.TOGGLE_FAVORITE)
                                card.sourcePost?.let { ports.favorites.toggle(it) }
                                    ?: (card.uid in galleryFavorites.map { it.uid })
                            },
                            onSaveImage = if (savePaths != null) {
                                { card ->
                                    val source = card.sourcePost
                                    val target = if (source == null) {
                                        null
                                    } else {
                                        desktopResolveSaveDir(savePaths, galleryPrefs)
                                    }
                                    when {
                                        source == null -> "这一张没有站方原模型，存图动作接不上"
                                        target == null -> "已取消保存（没选目录）。"
                                        else -> {
                                            // 点击下载那一刻立即触发飞入动效
                                            flyToDownloadCenter(card.url, false)
                                            val reading = desktopSaveImageScreenReading(
                                                desktopSaveImageRun(desktopUx.platformContext(), target, source, galleryPaths),
                                            )
                                            if (reading.startsWith("已存到本机")) {
                                                filesUi.imageScanTick++
                                            }
                                            reading
                                        }
                                    }
                                }
                            } else {
                                null
                            },
                            onCopyImage = galleryPaths?.let { pp ->
                                { card ->
                                    runCatching {
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            val bytes = com.venera.desktop.gallery.data.DesktopPixivImageBytes.fetch(
                                                paths = pp,
                                                url = com.venera.desktop.gallery.ui.desktopCopyImageUrl(card),
                                            )
                                            com.venera.desktop.gallery.data.DesktopPixivImageBytes.copyToClipboard(bytes)
                                        }
                                    }.isSuccess
                                }
                            },
                            onSearchTag = { site, tag ->
                                searchHandoff(listOf(site), tag)
                            },
                            // 详情页创作者名片的关注入口：亮灭读名单流，写口仍走同一颗端口。
                            followedArtists = ports.follows.follows.collectAsState().value,
                            onToggleFollow = { followedSite, followedName ->
                                ports.follows.toggle(followedSite, followedName).also { nowFollowing ->
                                    desktopUx.toast(if (nowFollowing) "已关注画师" else "已取消关注")
                                }
                            },
                        )
                    }

                    // ── ：墙上那张可点卡点进来的看大图那一屏 ──
                    // 桌面自搭（安卓那颗 GalleryPostScreen 是 ③ 档编不过）。返回回 Tab，那一面墙还在原处。
                    is DesktopRoute.GalleryPostViewer -> {
                        // 主墙上的卡带同一批可点卡进看图屏，←/→ 才有真队列可翻；搜索 / 收藏 /
                        // 日榜里的卡若不在主墙，退回单张，不摆一对按了没反应的箭头。
                        val viewerQueue = when {
                            // 详情页点大图带全页表进来的：左右翻的是同一篇的其他页。
                            pixivWorkViewerQueue.isNotEmpty() && pixivWorkViewerQueue.any { it.uid == r.post.uid } ->
                                pixivWorkViewerQueue
                            galleryViewerQueue.any { it.uid == r.post.uid } -> galleryViewerQueue
                            else -> listOf(r.post)
                        }
                        val viewerIndex = viewerQueue.indexOfFirst { it.uid == r.post.uid }.coerceAtLeast(0)
                        val savePaths = galleryPaths
                        val pixivZoomNow = (galleryPrefs?.pixivZoomQuality
                            ?: constFlow(com.venera.compose.gallery.domain.PixivImageQuality.LARGE)).collectAsState().value
                            com.venera.desktop.gallery.ui.DesktopGalleryPostViewer(
                                post = r.post,
                                queue = viewerQueue,
                                // 退出把翻到的页号回传：详情页中央大图接着停在那一张。
                                onExitIndex = { idx ->
                                    if (r.from is DesktopRoute.PixivWork) {
                                        pixivWorkViewerQueue = emptyList()
                                        pixivWorkResumePage = idx
                                    }
                                },
                            pixiv = ports.pixiv,
                            paths = galleryPaths,
                            zoomQuality = pixivZoomNow,
                            // 画质档写口（播放器内三档胶囊当场换，写盘 + 推流）。
                            onZoomQualityChange = { q -> galleryPrefs?.setPixivZoomQuality(q) },
                            // pixiv 收藏腿（pixiv 卡不带 sourcePost —— 从前收藏/下载在这站整排不摆的根因）。
                            pixivBookmarkToggle = ports.pixiv?.let { p ->
                                { id: Long, add: Boolean ->
                                    val bearer = ports.pixivAccount?.bearerOrNull()
                                    if (bearer == null) {
                                        false
                                    } else if (add) {
                                        p.bookmarkAdd(bearer, id, private = false).isSuccess
                                    } else {
                                        p.bookmarkRemove(bearer, id).isSuccess
                                    }
                                }
                            },
                            // pixiv 存原图 + 复制图片两腿：字节层走同一套防盗链头（pixiv/booru 自适应）。
                            // 2.4 下载目录：默认开关开 ⇒ 直落配置/内置目录（重名让位）；关 ⇒ 沿用逐张手选的文件框。
                            onSavePixivImage = galleryPaths?.let { pp ->
                                { card: com.venera.desktop.gallery.ui.DesktopWallCardModel ->
                                    val url = com.venera.desktop.gallery.ui.desktopViewerFullUrl(
                                        card,
                                        pixivZoomNow,
                                    )
                                    val suggestedName = "pixiv_${card.uid.substringAfter(':')}.jpg"
                                    // 点击下载那一刻立即触发飞入动效
                                    flyToDownloadCenter(card.url, false)
                                    val receipt = if (galleryPrefs?.downloadUseDefaultDir?.value ?: true) {
                                        com.venera.desktop.gallery.data.DesktopPixivImageBytes.saveToDir(
                                            paths = pp,
                                            url = url,
                                            suggestedName = suggestedName,
                                            dir = desktopGallerySaveDir(pp, galleryPrefs?.downloadDir?.value),
                                        )
                                    } else {
                                        com.venera.desktop.gallery.data.DesktopPixivImageBytes.saveToFile(
                                            paths = pp,
                                            url = url,
                                            suggestedName = suggestedName,
                                        )
                                    }
                                    if (receipt.startsWith("已保存：")) {
                                        java.io.File(receipt.removePrefix("已保存：")).parent?.let { parentDir ->
                                            galleryPrefs?.addExtraImageDir(parentDir)
                                        }
                                        filesUi.imageScanTick++
                                    }
                                    receipt
                                }
                            },
                            onCopyImage = galleryPaths?.let { pp ->
                                { card: com.venera.desktop.gallery.ui.DesktopWallCardModel ->
                                    runCatching {
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            val bytes = com.venera.desktop.gallery.data.DesktopPixivImageBytes.fetch(
                                                paths = pp,
                                                url = com.venera.desktop.gallery.ui.desktopCopyImageUrl(card),
                                            )
                                            com.venera.desktop.gallery.data.DesktopPixivImageBytes.copyToClipboard(bytes)
                                        }
                                    }.isSuccess
                                }
                            },
                            initialIndex = viewerIndex,
                                favoriteUids = galleryFavorites.map { it.uid }.toSet(),
                                // 「大图页」三档偏好的消费点（补全 —— 从前写口在、读口没有）：
                                // 自动连播 / 提前加载（相邻 N 张 coil 预取）/ 画布底色（GLASS 桌面无模糊层 ⇒ 黑）。
                                autoPlaySec = (galleryPrefs?.galleryAutoPlaySec
                                    ?: constFlow(0)).collectAsState().value,
                                preloadMode = (galleryPrefs?.galleryPreload ?: constFlow(
                                    com.venera.compose.gallery.domain.GalleryPreloadMode.NEXT,
                                )).collectAsState().value,
                                backdropColor = (galleryPrefs?.galleryBackdrop ?: constFlow(
                                    com.venera.compose.gallery.domain.GalleryViewerBackdrop.GLASS,
                                )).collectAsState().value.let { backdrop ->
                                    androidx.compose.ui.graphics.Color(backdrop.argb ?: 0xFF000000)
                                },
                            // 主人第 5 点：播放器返回弹回来路 —— 详情页中央大图进来的
                            // 回详情页，墙/历史屏进来的回各自来处；没有来路才落画廊首页。
                            onBack = { route = r.from ?: DesktopRoute.Tab },
                            onToggleFavorite = { card ->
                                DesktopPetCueBus.emit(DesktopPetCue.TOGGLE_FAVORITE)
                                card.sourcePost?.let { ports.favorites.toggle(it) }
                                    ?: (card.uid in galleryFavorites.map { it.uid })
                            },
                            onSaveImage = if (savePaths != null) {
                                { card ->
                                    val source = card.sourcePost
                                    val target = if (source == null) {
                                        null
                                    } else {
                                        desktopResolveSaveDir(savePaths, galleryPrefs)
                                    }
                                    when {
                                        source == null -> "这一张没有站方原模型，存图动作接不上"
                                        target == null -> "已取消保存（没选目录）。"
                                        else -> {
                                            // 点击下载那一刻立即触发飞入动效
                                            flyToDownloadCenter(card.url, false)
                                            val reading = desktopSaveImageScreenReading(
                                                desktopSaveImageRun(desktopUx.platformContext(), target, source, galleryPaths),
                                            )
                                            if (reading.startsWith("已存到本机")) {
                                                filesUi.imageScanTick++
                                            }
                                            reading
                                        }
                                    }
                                }
                            } else {
                                null
                            },
                        )
                    }

                    // 日榜二级页接入共享 GalleryDailyScreenBody，墙卡片与加载环通过槽位注入桌面实现。
        else -> Unit
    }
}
