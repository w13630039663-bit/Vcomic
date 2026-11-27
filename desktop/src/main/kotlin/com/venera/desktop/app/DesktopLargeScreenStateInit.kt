package com.venera.desktop.app

import com.venera.desktop.ui.desktopShellBack
import com.venera.desktop.ui.BackupUiState
import com.venera.desktop.ui.BrowseClearUiState
import com.venera.desktop.ui.DesktopFlyState
import com.venera.desktop.ui.DetailAndReaderUiState
import com.venera.desktop.ui.SavedUiState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import com.venera.compose.components.VeneraNavTab
import com.venera.compose.components.VeneraSideBarDownloadAnchor
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.compose.components.sideBarModeFor
import com.venera.compose.components.wideScreenLayoutMode
import com.venera.compose.data.db.FavoriteRecord
import com.venera.compose.data.db.HistoryRecord
import com.venera.compose.data.platform.JsonKeyValueStore
import com.venera.compose.data.platform.PathProvider
import com.venera.compose.data.platform.PreferenceKeys
import com.venera.compose.feature.ComicDetailCore
import com.venera.compose.gallery.data.GalleryContentGuard
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.gallery.data.GalleryPost
import com.venera.desktop.cache.DesktopComicCache
import com.venera.desktop.gallery.data.DesktopComicDownloadQueue
import com.venera.desktop.gallery.data.DesktopComicDownloadedChapter
import com.venera.desktop.gallery.data.DesktopComicPage
import com.venera.desktop.gallery.data.DesktopComicReaderPreferences
import com.venera.desktop.gallery.data.DesktopDecoTierPreferences
import com.venera.desktop.gallery.data.DesktopGalleryContentGuard
import com.venera.desktop.gallery.data.DesktopGalleryHandle
import com.venera.desktop.gallery.data.DesktopGalleryPreferences
import com.venera.desktop.gallery.data.DesktopReaderMode
import com.venera.desktop.gallery.ui.DesktopGallerySavedView
import com.venera.desktop.gallery.ui.DesktopWallCardModel
import com.venera.desktop.platform.DesktopPixivCallback
import com.venera.desktop.search.DesktopComicSearchOutcome
import com.venera.desktop.sourcemanage.DesktopSourceRowUi
import com.venera.engineprobe.EngineSession
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 装配大屏主壳全部领域状态，按领域委派至 [DesktopLargeScreenSearchAndSourceState] 与 [DesktopLargeScreenWallAndHomeState]。
 */
@Composable
internal fun rememberDesktopLargeScreenState(
    windowWidth: Dp,
    ports: GalleryPorts,
    modifier: Modifier = Modifier,
    maskModeOf: () -> String = { "BLUR" },
    blockedByUserOf: (sourceKey: String, title: String, author: String, tags: List<String>) -> Boolean =
        { _, _, _, _ -> false },
    wallGuardOf: () -> GalleryContentGuard? = { null },
    wallBlockAiOf: () -> Boolean = { false },
    engine: EngineSession? = null,
    onWindowMinimize: (() -> Unit)? = null,
    onWindowToggleMaximize: (() -> Unit)? = null,
    windowMaximized: Boolean = false,
    onShellBackdropChange: (String?, Boolean) -> Unit = { _, _ -> },
    onWindowClose: (() -> Unit)? = null,
    windowDragModifier: Modifier = Modifier,
    writeHistory: (suspend (HistoryRecord) -> Unit)? = null,
    readHistoryRow: (suspend (String) -> HistoryRecord?)? = null,
    setMaskMode: ((String) -> Boolean)? = null,
    setBlockAi: ((Boolean) -> Unit)? = null,
    readSources: (() -> List<DesktopSourceRowUi>)? = null,
    writeSourceEnabled: ((String, Boolean) -> Unit)? = null,
    writeSourceOrder: ((List<String>) -> Unit)? = null,
    readSourceText: ((String) -> String?)? = null,
    saveSourceText: (suspend (key: String, content: String) -> String?)? = null,
    clearSourceOverride: (suspend (String) -> Unit)? = null,
    loginWithCookies: (suspend (key: String, values: List<String>) -> String)? = null,
    loginWithPassword: (suspend (key: String, account: String, password: String) -> String)? = null,
    loginWithWebview: (suspend (key: String) -> String)? = null,
    readFavorites: (suspend () -> Pair<List<FavoriteRecord>, Boolean>)? = null,
    readFavoriteFolders: (suspend () -> List<String>)? = null,
    createFavoriteFolder: (suspend (String) -> Unit)? = null,
    deleteFavoriteFolder: (suspend (String) -> Unit)? = null,
    onOpenCardArtist: ((DesktopWallCardModel) -> Unit)? = null,
    removeFavorite: (suspend (String) -> Unit)? = null,
    isFavoriteOf: (suspend (String) -> Boolean)? = null,
    addFavorite: (suspend (FavoriteRecord) -> Unit)? = null,
    searchComics: (suspend (String, Int, String, List<String?>) -> DesktopComicSearchOutcome)? = null,
    downloadsRoot: File? = null,
    readerPrefs: DesktopComicReaderPreferences? = null,
    readerModeSeed: DesktopReaderMode? = null,
    initialTab: VeneraNavTab = VeneraNavTab.HOME,
    initialRoute: DesktopRoute = DesktopRoute.Tab,
    openFirstGalleryPost: Boolean = false,
    pixivLoginCallback: StateFlow<DesktopPixivCallback?> = MutableStateFlow(null),
    galleryPaths: PathProvider? = null,
    galleryPrefs: DesktopGalleryPreferences? = null,
    galleryGuard: DesktopGalleryContentGuard? = null,
    decoPrefs: DesktopDecoTierPreferences? = null,
    assetDir: File? = null,
    overrideDir: File? = null,
): DesktopLargeScreenState {
    val scope = rememberCoroutineScope()
    val routeState = remember { mutableStateOf<DesktopRoute>(initialRoute) }
    var route by routeState
    val routeBeforeSettingsState = remember { mutableStateOf<DesktopRoute>(DesktopRoute.Tab) }
    var routeBeforeSettings by routeBeforeSettingsState
    val settingsOpen = route is DesktopRoute.Settings
    val contentRoute: DesktopRoute = if (settingsOpen) routeBeforeSettings else route
    fun openSettings() {
        if (!settingsOpen) routeBeforeSettings = route
        route = DesktopRoute.Settings
    }

    fun openGalleryPostAny(post: GalleryPost) =
        openGalleryPostHelper(post, galleryPrefs, routeState)

    val openWallCardAny: (DesktopWallCardModel) -> Unit = { card ->
        openWallCardHelper(card, galleryPrefs, routeState)
    }

    val pixivCallback by pixivLoginCallback.collectAsState()
    val browseClearUi = remember { BrowseClearUiState() }
    val savedUiState = remember { mutableStateOf(SavedUiState(DesktopGallerySavedView.FAVORITES, "", null)) }
    val backupUi = remember { BackupUiState() }
    val detailUi = remember { DetailAndReaderUiState() }
    val detailCoreCache = remember { mutableMapOf<String, Pair<CoroutineScope, ComicDetailCore>>() }
    val commentsTickState = remember { mutableIntStateOf(0) }
    val readerPagesState = remember { mutableStateOf<List<DesktopComicPage>>(emptyList()) }
    val lastPageTurnAtState = remember { mutableLongStateOf(0L) }

    val layoutMode = wideScreenLayoutMode(windowWidth)
    val sideBarActive = layoutMode != WideScreenLayoutMode.Compact
    val effectiveMode = sideBarModeFor(layoutMode, sideBarActive)

    val currentTabState = remember { mutableStateOf(initialTab) }
    val currentTab by currentTabState

    val railPrefs = remember(galleryPaths) {
        galleryPaths?.let { JsonKeyValueStore(PreferenceKeys.PREFS_NAME, it) }
    }
    val railExpandedSavedState = remember(railPrefs) {
        mutableStateOf(railPrefs?.getBoolean("pref_sidebar_expanded", false) ?: false)
    }
    val readerChromePolicy = rememberDesktopReaderShellChromePolicy(
        contentRoute = contentRoute,
        savedRailExpanded = railExpandedSavedState.value,
        onSaveRailExpanded = { expanded ->
            railExpandedSavedState.value = expanded
            railPrefs?.put("pref_sidebar_expanded", expanded)
        },
    )
    val fullscreenChrome = readerChromePolicy.fullscreenChrome
    val shellMode = if (fullscreenChrome) WideScreenLayoutMode.Compact else effectiveMode
    val comicCache = remember(galleryPaths) { galleryPaths?.let { DesktopComicCache(it.cacheRoot) } }
    val queueConcurrency = remember(galleryPaths) {
        galleryPaths?.let {
            JsonKeyValueStore(PreferenceKeys.PREFS_NAME, it).getInt("pref_download_threads", 1).coerceIn(1, 16)
        } ?: 1
    }
    val queueTasks by DesktopComicDownloadQueue.tasks.collectAsState()
    val queuePrimed by DesktopComicDownloadQueue.primed.collectAsState()

    val explore = rememberDesktopExploreSlice(railPrefs)
    val search = rememberDesktopSearchAndGallerySearchSlice(ports, railPrefs)
    val sourceAndFiles = rememberDesktopSourceAndFilesSlice(scope, engine, overrideDir, galleryPaths, assetDir)

    val home = rememberDesktopHomeStatusSlice(
        ports = ports,
        assetDir = assetDir,
        overrideDir = overrideDir,
        sourceRows = sourceAndFiles.sourceUi.sourceRows,
        currentTab = currentTab,
        route = route,
        historyTick = sourceAndFiles.historyTickState.intValue,
        galleryPaths = galleryPaths,
    )
    sourceAndFiles.sourceUi.latencyItems = home.latencyItems
    sourceAndFiles.sourceUi.onReprobeHosts = home.reprobeHosts

    val currentMaskMode = maskModeOf()
    val wall = rememberDesktopWallAndPixivSlice(
        ports = ports,
        galleryPaths = galleryPaths,
        galleryPrefs = galleryPrefs,
        railPrefs = railPrefs,
        maskModeOf = maskModeOf,
        wallBlockAiOf = wallBlockAiOf,
        wallGuardOf = wallGuardOf,
        currentMaskMode = currentMaskMode,
    )

    val favAndReader = rememberDesktopFavoriteAndReaderSlice(
        route = route,
        galleryGuard = galleryGuard,
        maskModeOf = maskModeOf,
        blockedByUserOf = blockedByUserOf,
        currentMaskMode = currentMaskMode,
        engine = engine,
        assetDir = assetDir,
        overrideDir = overrideDir,
        galleryPaths = galleryPaths,
        downloadsRoot = downloadsRoot,
        readerPrefs = readerPrefs,
        readerModeSeed = readerModeSeed,
    )

    var searchReturnRoute by search.searchReturnRouteState
    fun desktopShellBackWithSearch(setRoute: (DesktopRoute) -> Unit): (() -> Unit)? {
        desktopShellBack(route) { setRoute(it) }?.let { return it }
        if (route != DesktopRoute.Tab || currentTab != VeneraNavTab.SEARCH) return null
        val target = searchReturnRoute ?: return null
        return {
            searchReturnRoute = null
            setRoute(target)
        }
    }

    var downloadsTick by sourceAndFiles.downloadsTickState
    fun onDownloadsDelete(chapter: DesktopComicDownloadedChapter, forceDirect: Boolean = false) =
        onDownloadsDeleteHelper(
            chapter, forceDirect, downloadsRoot, sourceAndFiles.filesUi, sourceAndFiles.armedDeleteKeyState, scope,
        ) { downloadsTick++ }

    val flyState = remember { DesktopFlyState() }
    val flyAnchor = remember { VeneraSideBarDownloadAnchor() }
    val flyPointer = remember { mutableStateOf<Offset?>(null) }
    val flyHostTopLeftState = remember { mutableStateOf(Offset.Zero) }
    val flyHostTopLeft by flyHostTopLeftState
    fun flyToDownloadCenter(coverUrl: String?, isComic: Boolean) =
        flyToDownloadCenterHelper(coverUrl, isComic, flyPointer, flyHostTopLeft, flyState)

    val pixivSuggestEngine = remember(galleryPaths) {
        galleryPaths?.let { DesktopGalleryHandle.of(it).engine }
    }
    val desktopShellBackFn: ((DesktopRoute) -> Unit) -> (() -> Unit)? = ::desktopShellBackWithSearch

    return DesktopLargeScreenState(
        layoutMode = layoutMode,
        scope = scope,
        desktopUx = favAndReader.desktopUx,
        businessHandle = favAndReader.businessHandle,
        queueTasks = queueTasks,
        queueConcurrency = queueConcurrency,
        queuePrimed = queuePrimed,
        favoritesLoaded = favAndReader.favoritesLoadedState.value,
        favoritesState = favAndReader.favoritesState,
        favoriteFolderNames = favAndReader.favoriteFolderNames,
        localComicScan = sourceAndFiles.localComicScanState.value,
        readerMode = favAndReader.readerMode,
        readerModeNote = favAndReader.readerModeNote,
        detailCoreCache = detailCoreCache,
        sourceUi = sourceAndFiles.sourceUi,
        filesUi = sourceAndFiles.filesUi,
        detailUi = detailUi,
        aggregateSearch = search.aggregateSearch,
        runSourceImport = sourceAndFiles.runSourceImport,
        openSettings = ::openSettings,
        onDownloadsDelete = { ch, forceDirect -> onDownloadsDelete(ch, forceDirect) },
        flyToDownloadCenter = ::flyToDownloadCenter,
        routeState = routeState,
        currentTabState = currentTabState,
        commentsTickState = commentsTickState,
        readerPagesState = readerPagesState,
        searchReturnRouteState = search.searchReturnRouteState,
        sourceTickState = sourceAndFiles.sourceTickState,
        downloadsTickState = sourceAndFiles.downloadsTickState,
        localComicImportTickState = sourceAndFiles.localComicImportTickState,
        armedDeleteKeyState = sourceAndFiles.armedDeleteKeyState,
        readerModeChoiceState = favAndReader.readerModeChoiceState,
        searchQueryState = search.searchQueryState,
        searchSourceKeyState = search.searchSourceKeyState,
        searchTickState = search.searchTickState,
        wallDensity = wall.wallDensityState.value,
        artistProfileAnchor = wall.artistProfileAnchor,
        dailyCore = wall.dailyCore,
        dailyGallery = wall.dailyGallery,
        galleryAuxUi = search.galleryAuxUi,
        openWallCardAny = openWallCardAny,
        pixivAccountTabState = remember { mutableStateOf(0) },
        pixivArtistSectionState = remember { mutableStateOf(com.venera.compose.gallery.data.PixivArtistSection.ILLUST) },
        lastPixivArtistIdState = remember { mutableStateOf<Long?>(null) },
        pixivSearchKindState = search.pixivSearchKindState,
        pixivSearchTermState = search.pixivSearchTermState,
        pixivSearchTickState = search.pixivSearchTickState,
        gallerySearchModeState = search.gallerySearchModeState,
        pixivWorkViewerQueueState = wall.pixivWorkViewerQueueState,
        pixivWorkResumePageState = wall.pixivWorkResumePageState,
        railPrefs = railPrefs,
        overrideDir = overrideDir,
        historySourceName = home.historySourceName,
        readingStats = home.readingStats,
        galleryFavorites = home.galleryFavorites,
        galleryWallQuality = wall.galleryWallQuality,
        galleryWallCards = wall.galleryWallCardsState.value,
        galleryWallBlocked = wall.galleryWallBlockedState.intValue,
        galleryWallUnrenderable = wall.galleryWallUnrenderableState.value,
        galleryViewerQueue = wall.galleryViewerQueue,
        gallerySearchToday = search.gallerySearchToday,
        gallerySearchSourceOptions = search.gallerySearchSourceOptions,
        gallerySearchRankingChoices = search.gallerySearchRankingChoices,
        gallerySearchRankingLanding = search.gallerySearchRankingLanding,
        gallerySearchPeriodReading = search.gallerySearchPeriodReading,
        gallerySearchCanPeriodBack = search.gallerySearchCanPeriodBack,
        gallerySearchCanPeriodForward = search.gallerySearchCanPeriodForward,
        gallerySearchCards = search.gallerySearchCards,
        gallerySearchFooter = search.gallerySearchFooter,
        pixivSearchCards = search.pixivSearchCards,
        pixivSearchFooter = search.pixivSearchFooter,
        pixivCollectionSearch = search.pixivCollectionSearch,
        searchResultAnchor = wall.searchResultAnchor,
        searchArtistAnchor = wall.searchArtistAnchor,
        backupUi = backupUi,
        browseClearUi = browseClearUi,
        openGalleryPostAny = ::openGalleryPostAny,
        savedUiState = savedUiState,
        historyTickState = sourceAndFiles.historyTickState,
        galleryWallTickState = wall.galleryWallTickState,
        gallerySearchQueryState = search.gallerySearchQueryState,
        gallerySearchSourceState = search.gallerySearchSourceState,
        gallerySearchTickState = search.gallerySearchTickState,
        gallerySearchMoreTickState = search.gallerySearchMoreTickState,
        gallerySearchR18State = search.gallerySearchR18State,
        gallerySearchPageState = search.gallerySearchPageState,
        gallerySearchSearchingState = search.gallerySearchSearchingState,
        gallerySearchFailedState = search.gallerySearchFailedState,
        gallerySearchHistoryState = search.gallerySearchHistoryState,
        gallerySearchHistoryNoteState = search.gallerySearchHistoryNoteState,
        gallerySearchRankingState = search.gallerySearchRankingState,
        gallerySearchRankingAnchorState = search.gallerySearchRankingAnchorState,
        pixivSearchMoreTickState = search.pixivSearchMoreTickState,
        pixivSearchFilterState = search.pixivSearchFilterState,
        pixivSearchPageState = search.pixivSearchPageState,
        pixivSearchSearchingState = search.pixivSearchSearchingState,
        pixivSearchUsersState = search.pixivSearchUsersState,
        gallerySuggestCandidatesState = search.gallerySuggestCandidatesState,
        currentTab = currentTab,
        homeSkinLight = home.homeSkinLight,
        pixivSuggestEngine = pixivSuggestEngine,
        exploreCategoryBoard = explore.exploreCategoryBoardState.value,
        explorePickUi = explore.explorePickUi,
        favoritesNote = favAndReader.favoritesNoteState.value,
        netFavUi = favAndReader.netFavUi,
        searchFilterGroups = search.searchFilterGroupsState.value,
        homeSourceRows = home.homeSourceRows,
        homeHistory = home.homeHistory,
        homeStats = home.homeStats,
        homeArtists = home.homeArtists,
        favoritesShown = favAndReader.favoritesShown,
        favoritesFolderTabs = favAndReader.favoritesFolderTabs,
        favoritesCountNote = favAndReader.favoritesCountNote,
        favoritesFilterNote = favAndReader.favoritesFilterNote,
        searchHistoryChips = search.searchHistoryChips,
        searchMoreState = search.searchMoreState,
        searchFailedFooter = search.searchFailedFooter,
        searchEmptyNote = search.searchEmptyNote,
        wallListAnchor = wall.wallListAnchor,
        wallStaggeredAnchor = wall.wallStaggeredAnchor,
        exploreAnchor = wall.exploreAnchor,
        wallLayout = wall.wallLayout,
        wallFooter = wall.wallFooter,
        pixivPreviewLeg = wall.pixivPreviewLegState.value,
        wallSelectedUids = wall.wallSelectedUids,
        exploreSectionsState = explore.exploreSectionsState,
        exploreNoteState = explore.exploreNoteState,
        exploreTickState = explore.exploreTickState,
        exploreSourceKeyState = explore.exploreSourceKeyState,
        exploreModesState = explore.exploreModesState,
        exploreModeIndexState = explore.exploreModeIndexState,
        explorePickTickState = explore.explorePickTickState,
        explorePickMoreTickState = explore.explorePickMoreTickState,
        explorePickPageNoState = explore.explorePickPageNoState,
        homeRecommendComicsState = explore.homeRecommendComicsState,
        homeRecommendNoteState = explore.homeRecommendNoteState,
        favoritesTabPickedState = favAndReader.favoritesTabPickedState,
        favoritesSourceKeyState = favAndReader.favoritesSourceKeyState,
        discoverViewExploreState = explore.discoverViewExploreState,
        searchResultsState = search.searchResultsState,
        searchingState = search.searchingState,
        searchFilterValuesState = search.searchFilterValuesState,
        searchMaxPageState = search.searchMaxPageState,
        searchCanLoadMoreState = search.searchCanLoadMoreState,
        hasSearchedState = search.hasSearchedState,
        searchHistoryState = search.searchHistoryState,
        searchHistoryNoteState = search.searchHistoryNoteState,
        searchCauseState = search.searchCauseState,
        loadMoreTickState = search.loadMoreTickState,
        homeRecommendR18State = explore.homeRecommendR18State,
        homeRecommendFailedState = explore.homeRecommendFailedState,
        homeRetryTickState = explore.homeRetryTickState,
        filesSegmentState = sourceAndFiles.filesSegmentState,
        wallPageState = wall.wallPageState,
        galleryHomeDomainState = wall.galleryHomeDomainState,
        galleryRankingModeState = wall.galleryRankingModeState,
        gallerySortState = wall.gallerySortState,
        wallRefreshTickState = wall.wallRefreshTickState,
        wallRefreshingState = wall.wallRefreshingState,
        wallMoreTickState = wall.wallMoreTickState,
        booruRatingFilterState = wall.booruRatingFilterState,
        booruSiteFilterState = wall.booruSiteFilterState,
        wallDensityState = wall.wallDensityState,
        pixivDiscoverLegState = wall.pixivDiscoverLegState,
        pixivLocalFollowedState = wall.pixivLocalFollowedState,
        comicCache = comicCache,
        exploreSections = explore.exploreSectionsState.value,
        favoriteFolderComicId = favAndReader.favoriteFolderComicId,
        favoriteFolderCurrent = favAndReader.favoriteFolderCurrent,
        favoriteFolderSelected = favAndReader.favoriteFolderSelected,
        favoritesTabSelected = favAndReader.favoritesTabSelected,
        favoritesLoadedState = favAndReader.favoritesLoadedState,
        favoritesNoteState = favAndReader.favoritesNoteState,
        favoriteFolderPickedState = favAndReader.favoriteFolderPickedState,
        localComicScanState = sourceAndFiles.localComicScanState,
        lastPageTurnAtState = lastPageTurnAtState,
        currentMaskMode = currentMaskMode,
        pixivAuthState = wall.pixivAuthState,
        wallCache = wall.wallCache,
        wallCacheKey = wall.wallCacheKey,
        cards = wall.cards,
        pixivPreviewCache = wall.pixivPreviewCache,
        tagDictionary = wall.tagDictionary,
        galleryWallCardsState = wall.galleryWallCardsState,
        galleryWallBlockedState = wall.galleryWallBlockedState,
        galleryWallUnrenderableState = wall.galleryWallUnrenderableState,
        lastAppliedRefreshTickState = wall.lastAppliedRefreshTickState,
        wallPhaseState = wall.wallPhaseState,
        pixivPreviewLegState = wall.pixivPreviewLegState,
        lastAppliedPreviewTickState = wall.lastAppliedPreviewTickState,
        wallPage = wall.wallPageState.value,
        searchFilterGroupsState = search.searchFilterGroupsState,
        searchPageState = search.searchPageState,
        loadingMoreState = search.loadingMoreState,
        gallerySearchPhaseState = search.gallerySearchPhaseState,
        gallerySearchJobState = search.gallerySearchJobState,
        pixivSearchPhaseState = search.pixivSearchPhaseState,
        pixivSearchUsersCursorState = search.pixivSearchUsersCursorState,
        exploreCategoryBoardState = explore.exploreCategoryBoardState,
        pixivCallback = pixivCallback,
        homeLines = home.homeLines,
        homeLatencyByHost = home.homeLatencyByHost,
        homeProbeStamp = home.homeProbeStamp,
        homeProbingHosts = home.homeProbingHosts,
        homeFailedHosts = home.homeFailedHosts,
        homeScope = home.homeScope,
        performSourceUpdateCheck = sourceAndFiles.performSourceUpdateCheck,
        favoriteFoldersState = favAndReader.favoriteFoldersState,
        routeBeforeSettingsState = routeBeforeSettingsState,
        settingsOpen = settingsOpen,
        contentRoute = contentRoute,
        desktopShellBackWithSearch = desktopShellBackFn,
        shellMode = shellMode,
        fullscreenChrome = fullscreenChrome,
        readerChromePolicy = readerChromePolicy,
        railExpandedSavedState = railExpandedSavedState,
        pixivAccountAvatarUrl = wall.pixivAccountAvatarUrl,
        flyState = flyState,
        flyAnchor = flyAnchor,
        flyHostTopLeftState = flyHostTopLeftState,
        flyPointer = flyPointer,
    )
}
