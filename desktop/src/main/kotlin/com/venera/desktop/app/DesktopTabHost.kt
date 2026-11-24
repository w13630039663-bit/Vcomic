package com.venera.desktop.app

import com.venera.desktop.ui.BrowseClearUiState
import com.venera.desktop.ui.DailyGalleryUiState
import com.venera.desktop.ui.DesktopHomeSourceStatusRow
import com.venera.desktop.data.DesktopHomeStats
import com.venera.desktop.ui.DesktopNavMotion
import com.venera.desktop.ui.DesktopScrollAnchor
import com.venera.desktop.ui.DetailAndReaderUiState
import com.venera.desktop.ui.ExplorePickUiState
import com.venera.desktop.ui.FilesAuxUiState
import com.venera.desktop.ui.GalleryAuxUiState
import com.venera.desktop.ui.NetFavUiState
import com.venera.desktop.ui.PixivDiscoverLeg
import com.venera.desktop.ui.PixivPreviewLeg
import com.venera.desktop.ui.SourceManageUiState
import com.venera.desktop.data.DesktopHomeHistorySnapshot
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.venera.compose.components.VeneraNavTab
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.compose.data.db.ComicFollowDao
import com.venera.compose.data.db.FavoriteRecord
import com.venera.compose.data.platform.HttpEngine
import com.venera.compose.data.platform.JsonKeyValueStore
import com.venera.compose.data.platform.PathProvider
import com.venera.compose.gallery.data.GalleryFavorite
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.data.PixivRankingMode
import com.venera.compose.gallery.domain.GalleryFollowedArtistsReadout
import com.venera.compose.gallery.domain.GalleryHomeDomain
import com.venera.compose.gallery.domain.GallerySearchSource
import com.venera.compose.gallery.domain.GalleryWallLayout
import com.venera.desktop.business.DesktopBusinessPorts
import com.venera.desktop.downloads.DesktopFilesSegment
import com.venera.desktop.follow.DesktopFollowUpdateChecker
import com.venera.desktop.follow.DesktopNetworkFollowImporter
import com.venera.desktop.follow.FollowCheckProgress
import com.venera.desktop.follow.NetFollowImportProgress
import com.venera.desktop.gallery.data.DesktopComicQueueTask
import com.venera.desktop.gallery.data.DesktopFavoriteFolderTab
import com.venera.desktop.gallery.data.DesktopGalleryPreferences
import com.venera.desktop.gallery.data.DesktopLocalComicLibraryScan
import com.venera.desktop.gallery.data.DesktopPixivAccount
import com.venera.desktop.gallery.ui.DesktopGalleryRatingFilter
import com.venera.desktop.gallery.ui.DesktopGallerySort
import com.venera.desktop.gallery.ui.DesktopGalleryWallFooter
import com.venera.desktop.gallery.ui.DesktopWallCardModel
import com.venera.desktop.gallery.ui.DesktopWallDensity
import com.venera.desktop.gallery.ui.DesktopWallPage
import com.venera.desktop.search.DesktopAggregateSearchHolder
import com.venera.desktop.search.DesktopComicSearchMore
import com.venera.desktop.search.DesktopComicSearchOutcome
import com.venera.engineprobe.EngineSession
import java.io.File
import kotlinx.coroutines.CoroutineScope

/**
 * 桌面端五枚顶层导航页签（GALLERY / HOME / FAVORITES / SEARCH / EXPLORE）的动效分发宿主。
 *
 * 各分支视图装配已抽至 [DesktopTabGalleryAndHomePanes] 与 [DesktopTabFavoritesAndExplorePanes]。
 */
@Composable
internal fun DesktopTabHost(
    currentTab: VeneraNavTab,
    layoutMode: WideScreenLayoutMode,
    homeSkinLight: Boolean,
    ports: GalleryPorts,
    galleryPrefs: DesktopGalleryPreferences?,
    galleryPaths: PathProvider?,
    railPrefs: JsonKeyValueStore?,
    scope: CoroutineScope,
    downloadsRoot: File?,
    desktopUx: DesktopUxPlatformServices,
    businessHandle: DesktopBusinessPorts.Handle?,
    searchHandoff: (List<GallerySite>, String) -> Unit,
    pixivSuggestEngine: HttpEngine?,
    searchComics: (suspend (String, Int, String, List<String?>) -> DesktopComicSearchOutcome)?,
    browseClearUi: BrowseClearUiState,
    detailUi: DetailAndReaderUiState,
    exploreCategoryBoard: EngineSession.CategoryBoard,
    explorePickUi: ExplorePickUiState,
    favoritesLoaded: Boolean,
    favoritesState: MutableState<List<FavoriteRecord>>,
    favoritesNote: String?,
    netFavUi: NetFavUiState,
    galleryWallCards: List<DesktopWallCardModel>,
    galleryWallBlocked: Int,
    galleryWallUnrenderable: String,
    aggregateSearch: DesktopAggregateSearchHolder,
    searchFilterGroups: List<EngineSession.SearchOption>,
    galleryAuxUi: GalleryAuxUiState,
    sourceUi: SourceManageUiState,
    filesUi: FilesAuxUiState,
    localComicScan: DesktopLocalComicLibraryScan?,
    homeSourceRows: List<DesktopHomeSourceStatusRow>,
    historySourceName: String?,
    queueTasks: List<DesktopComicQueueTask>,
    homeHistory: DesktopHomeHistorySnapshot,
    homeStats: DesktopHomeStats,
    homeArtists: GalleryFollowedArtistsReadout,
    favoritesShown: List<FavoriteRecord>,
    favoritesFolderTabs: List<DesktopFavoriteFolderTab>,
    favoritesCountNote: String,
    favoritesFilterNote: String?,
    searchHistoryChips: List<String>,
    searchMoreState: DesktopComicSearchMore,
    searchFailedFooter: String?,
    searchEmptyNote: String?,
    wallListAnchor: DesktopScrollAnchor,
    wallStaggeredAnchor: DesktopScrollAnchor,
    exploreAnchor: DesktopScrollAnchor,
    wallLayout: GalleryWallLayout,
    wallFooter: DesktopGalleryWallFooter,
    dailyGallery: DailyGalleryUiState,
    pixivPreviewLeg: PixivPreviewLeg,
    gallerySearchSourceOptions: List<GallerySearchSource>,
    galleryFavorites: List<GalleryFavorite>,
    wallSelectedUids: SnapshotStateList<String>,
    addFavorite: (suspend (FavoriteRecord) -> Unit)?,
    removeFavorite: (suspend (String) -> Unit)?,
    createFavoriteFolder: (suspend (String) -> Unit)?,
    deleteFavoriteFolder: (suspend (String) -> Unit)?,
    openSettings: () -> Unit,
    openWallCardAny: (DesktopWallCardModel) -> Unit,
    routeState: MutableState<DesktopRoute>,
    exploreSectionsState: MutableState<List<EngineSession.Section>>,
    exploreNoteState: MutableState<String?>,
    exploreTickState: MutableIntState,
    exploreSourceKeyState: MutableState<String?>,
    exploreModesState: MutableState<List<String>>,
    exploreModeIndexState: MutableIntState,
    explorePickTickState: MutableIntState,
    explorePickMoreTickState: MutableIntState,
    explorePickPageNoState: MutableIntState,
    homeRecommendComicsState: MutableState<List<EngineSession.ComicCard>>,
    homeRecommendNoteState: MutableState<String?>,
    favoritesTabPickedState: MutableState<String?>,
    favoritesSourceKeyState: MutableState<String>,
    galleryWallTickState: MutableIntState,
    searchQueryState: MutableState<String>,
    discoverViewExploreState: MutableState<Boolean>,
    searchResultsState: MutableState<List<EngineSession.ComicCard>>,
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
    filesSegmentState: MutableState<DesktopFilesSegment>,
    wallPageState: MutableState<DesktopWallPage?>,
    galleryHomeDomainState: MutableState<GalleryHomeDomain>,
    galleryRankingModeState: MutableState<PixivRankingMode>,
    gallerySortState: MutableState<DesktopGallerySort>,
    wallRefreshTickState: MutableIntState,
    wallRefreshingState: MutableState<Boolean>,
    wallMoreTickState: MutableIntState,
    booruRatingFilterState: MutableState<DesktopGalleryRatingFilter>,
    booruSiteFilterState: MutableState<GallerySite?>,
    wallDensityState: MutableState<DesktopWallDensity>,
    pixivDiscoverLegState: MutableState<PixivDiscoverLeg>,
    pixivLocalFollowedState: MutableState<Set<Long>>,
    followDao: ComicFollowDao?,
    followChecker: DesktopFollowUpdateChecker?,
    followImporter: DesktopNetworkFollowImporter?,
    onStartFollowImport: ((sourceName: String, folderId: String, folderName: String) -> Unit)?,
) {
    val followRecords = followDao?.followsFlow?.collectAsState()?.value.orEmpty()
    val followProgress = followChecker?.progress?.collectAsState()?.value ?: FollowCheckProgress()
    val followImportProgress = followImporter?.progress?.collectAsState()?.value ?: NetFollowImportProgress()
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
            DesktopNavMotion.sameLevelEnter() togetherWith DesktopNavMotion.sameLevelExit()
        },
        label = "desktop-tab-motion",
    ) { tab ->
        val homePixivAccount = ports.pixivAccount as? DesktopPixivAccount
        when (tab) {
            VeneraNavTab.GALLERY -> DesktopGalleryWallPaneTab(
                layoutMode = layoutMode,
                ports = ports,
                galleryPrefs = galleryPrefs,
                galleryPaths = galleryPaths,
                railPrefs = railPrefs,
                scope = scope,
                desktopUx = desktopUx,
                searchHandoff = searchHandoff,
                pixivSuggestEngine = pixivSuggestEngine,
                browseClearUi = browseClearUi,
                galleryAuxUi = galleryAuxUi,
                homeArtists = homeArtists,
                wallListAnchor = wallListAnchor,
                wallStaggeredAnchor = wallStaggeredAnchor,
                wallLayout = wallLayout,
                wallFooter = wallFooter,
                dailyGallery = dailyGallery,
                pixivPreviewLeg = pixivPreviewLeg,
                gallerySearchSourceOptions = gallerySearchSourceOptions,
                wallSelectedUids = wallSelectedUids,
                openWallCardAny = openWallCardAny,
                routeState = routeState,
                gallerySearchQueryState = gallerySearchQueryState,
                gallerySearchSourceState = gallerySearchSourceState,
                gallerySearchTickState = gallerySearchTickState,
                gallerySearchModeState = gallerySearchModeState,
                pixivSearchTermState = pixivSearchTermState,
                pixivSearchTickState = pixivSearchTickState,
                gallerySearchR18State = gallerySearchR18State,
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
            )

            VeneraNavTab.HOME -> DesktopHomeTab(
                layoutMode = layoutMode,
                homeSkinLight = homeSkinLight,
                homePixivAccount = homePixivAccount,
                railPrefs = railPrefs,
                downloadsRoot = downloadsRoot,
                desktopUx = desktopUx,
                searchHandoff = searchHandoff,
                detailUi = detailUi,
                sourceUi = sourceUi,
                filesUi = filesUi,
                localComicScan = localComicScan,
                homeSourceRows = homeSourceRows,
                historySourceName = historySourceName,
                queueTasks = queueTasks,
                homeHistory = homeHistory,
                homeStats = homeStats,
                homeArtists = homeArtists,
                dailyGallery = dailyGallery,
                openWallCardAny = openWallCardAny,
                exploreSections = exploreSectionsState.value,
                exploreNote = exploreNoteState.value,
                routeState = routeState,
                homeRecommendComicsState = homeRecommendComicsState,
                homeRecommendNoteState = homeRecommendNoteState,
                historyTickState = historyTickState,
                homeRecommendR18State = homeRecommendR18State,
                homeRecommendFailedState = homeRecommendFailedState,
                homeRetryTickState = homeRetryTickState,
                filesSegmentState = filesSegmentState,
            )

            VeneraNavTab.FAVORITES -> DesktopFavoritesTab(
                layoutMode = layoutMode,
                ports = ports,
                galleryPaths = galleryPaths,
                scope = scope,
                desktopUx = desktopUx,
                businessHandle = businessHandle,
                browseClearUi = browseClearUi,
                detailUi = detailUi,
                favoritesLoaded = favoritesLoaded,
                favoritesState = favoritesState,
                favoritesNote = favoritesNote,
                netFavUi = netFavUi,
                galleryWallCards = galleryWallCards,
                galleryWallBlocked = galleryWallBlocked,
                galleryWallUnrenderable = galleryWallUnrenderable,
                galleryFavorites = galleryFavorites,
                favoritesShown = favoritesShown,
                favoritesFolderTabs = favoritesFolderTabs,
                favoritesCountNote = favoritesCountNote,
                favoritesFilterNote = favoritesFilterNote,
                removeFavorite = removeFavorite,
                createFavoriteFolder = createFavoriteFolder,
                deleteFavoriteFolder = deleteFavoriteFolder,
                routeState = routeState,
                favoritesTabPickedState = favoritesTabPickedState,
                favoritesSourceKeyState = favoritesSourceKeyState,
                galleryWallTickState = galleryWallTickState,
                followDao = followDao,
                followRecords = followRecords,
                followProgress = followProgress,
                followImportProgress = followImportProgress,
                followSyncKeys = followSyncKeys,
                followChecker = followChecker,
                followImporter = followImporter,
                onStartFollowImport = onStartFollowImport,
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

            VeneraNavTab.EXPLORE -> DesktopExploreTab(
                layoutMode = layoutMode,
                scope = scope,
                downloadsRoot = downloadsRoot,
                desktopUx = desktopUx,
                detailUi = detailUi,
                exploreCategoryBoard = exploreCategoryBoard,
                explorePickUi = explorePickUi,
                sourceUi = sourceUi,
                filesUi = filesUi,
                exploreAnchor = exploreAnchor,
                favorites = favoritesState.value,
                addFavorite = addFavorite,
                openSettings = openSettings,
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
                historyTickState = historyTickState,
                filesSegmentState = filesSegmentState,
            )
        }
    }
}
