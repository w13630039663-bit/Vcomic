package com.venera.desktop.app

import com.venera.desktop.ui.ExplorePickUiState
import com.venera.desktop.ui.FilesAuxUiState
import com.venera.desktop.ui.GalleryAuxUiState
import com.venera.desktop.ui.PixivCollectionSearchLeg
import com.venera.desktop.ui.SourceManageUiState
import com.venera.desktop.data.desktopEngineCall
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.venera.compose.data.platform.JsonKeyValueStore
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.gallery.data.GalleryTagSuggestion
import com.venera.compose.gallery.data.PixivUser
import com.venera.compose.gallery.domain.GalleryRanking
import com.venera.compose.gallery.domain.GallerySearchEntry
import com.venera.compose.gallery.domain.GallerySearchSource
import com.venera.compose.gallery.domain.PixivSearchFilter
import com.venera.desktop.downloads.DesktopFilesSegment
import com.venera.desktop.gallery.data.DesktopLocalComicLibraryScan
import com.venera.desktop.gallery.data.DesktopSourceOverrides
import com.venera.desktop.gallery.ui.DESKTOP_PIXIV_SEARCH_WORKS
import com.venera.desktop.gallery.ui.DesktopGalleryMorePhase
import com.venera.desktop.gallery.ui.DesktopGalleryWallFooter
import com.venera.desktop.gallery.ui.DesktopRankingChoice
import com.venera.desktop.gallery.ui.DesktopWallCardModel
import com.venera.desktop.gallery.ui.DesktopWallPage
import com.venera.desktop.gallery.ui.desktopGallerySearchSourceOptions
import com.venera.desktop.gallery.ui.desktopGalleryWallFooter
import com.venera.desktop.gallery.ui.desktopGelbooruConfigured
import com.venera.desktop.gallery.ui.desktopRankingAnchorReading
import com.venera.desktop.gallery.ui.desktopRankingChoices
import com.venera.desktop.gallery.ui.desktopRankingLanding
import com.venera.desktop.gallery.ui.desktopRankingStep
import com.venera.desktop.search.DesktopAggregateSearchHolder
import com.venera.desktop.search.DesktopComicSearchMore
import com.venera.desktop.search.desktopComicSearchEmptyNote
import com.venera.desktop.search.desktopComicSearchFailedFooter
import com.venera.desktop.search.desktopComicSearchHistoryChips
import com.venera.desktop.search.desktopComicSearchMoreState
import com.venera.engineprobe.EngineSession
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 探索、分类面板与首页推荐漫画的状态分组。 */
internal class DesktopExploreSlice(
    val exploreSectionsState: MutableState<List<EngineSession.Section>>,
    val exploreNoteState: MutableState<String?>,
    val exploreTickState: MutableIntState,
    val exploreSourceKeyState: MutableState<String?>,
    val exploreModesState: MutableState<List<String>>,
    val exploreModeIndexState: MutableIntState,
    val exploreCategoryBoardState: MutableState<EngineSession.CategoryBoard>,
    val explorePickUi: ExplorePickUiState,
    val explorePickTickState: MutableIntState,
    val explorePickMoreTickState: MutableIntState,
    val explorePickPageNoState: MutableIntState,
    val discoverViewExploreState: MutableState<Boolean>,
    val homeRecommendComicsState: MutableState<List<EngineSession.ComicCard>>,
    val homeRecommendNoteState: MutableState<String?>,
    val homeRecommendR18State: MutableState<Boolean>,
    val homeRecommendFailedState: MutableState<Boolean>,
    val homeRetryTickState: MutableState<Int>,
)

@Composable
internal fun rememberDesktopExploreSlice(railPrefs: JsonKeyValueStore?): DesktopExploreSlice {
    val exploreSectionsState = remember { mutableStateOf<List<EngineSession.Section>>(emptyList()) }
    val exploreNoteState = remember { mutableStateOf<String?>(null) }
    val exploreTickState = remember { mutableIntStateOf(0) }
    val exploreSourceKeyState = remember { mutableStateOf<String?>(null) }
    val exploreModesState = remember { mutableStateOf<List<String>>(emptyList()) }
    val exploreModeIndexState = remember { mutableIntStateOf(0) }
    val exploreCategoryBoardState = remember {
        mutableStateOf(EngineSession.CategoryBoard(emptyList(), emptyList()))
    }
    val explorePickUi = remember { ExplorePickUiState() }
    val explorePickTickState = remember { mutableIntStateOf(0) }
    val explorePickMoreTickState = remember { mutableIntStateOf(0) }
    val explorePickPageNoState = remember { mutableIntStateOf(1) }
    val discoverViewExploreState = remember { mutableStateOf(false) }
    val homeRecommendComicsState = remember { mutableStateOf<List<EngineSession.ComicCard>>(emptyList()) }
    val homeRecommendNoteState = remember { mutableStateOf<String?>(null) }
    val homeRecommendR18State = androidx.compose.runtime.saveable.rememberSaveable(railPrefs) {
        mutableStateOf(railPrefs?.getBoolean("pref_home_recommend_r18", false) ?: false)
    }
    val homeRecommendFailedState = remember { mutableStateOf(false) }
    val homeRetryTickState = remember { mutableStateOf(0) }

    return DesktopExploreSlice(
        exploreSectionsState = exploreSectionsState,
        exploreNoteState = exploreNoteState,
        exploreTickState = exploreTickState,
        exploreSourceKeyState = exploreSourceKeyState,
        exploreModesState = exploreModesState,
        exploreModeIndexState = exploreModeIndexState,
        exploreCategoryBoardState = exploreCategoryBoardState,
        explorePickUi = explorePickUi,
        explorePickTickState = explorePickTickState,
        explorePickMoreTickState = explorePickMoreTickState,
        explorePickPageNoState = explorePickPageNoState,
        discoverViewExploreState = discoverViewExploreState,
        homeRecommendComicsState = homeRecommendComicsState,
        homeRecommendNoteState = homeRecommendNoteState,
        homeRecommendR18State = homeRecommendR18State,
        homeRecommendFailedState = homeRecommendFailedState,
        homeRetryTickState = homeRetryTickState,
    )
}

/** 漫画搜索、聚合搜索与画廊/Pixiv 搜索双模态状态分组。 */
internal class DesktopSearchAndGallerySearchSlice(
    val searchQueryState: MutableState<String>,
    val searchResultsState: MutableState<List<EngineSession.ComicCard>>,
    val searchingState: MutableState<Boolean>,
    val searchTickState: MutableIntState,
    val searchSourceKeyState: MutableState<String?>,
    val aggregateSearch: DesktopAggregateSearchHolder,
    val searchReturnRouteState: MutableState<DesktopRoute?>,
    val searchFilterGroupsState: MutableState<List<EngineSession.SearchOption>>,
    val searchFilterValuesState: MutableState<List<String?>>,
    val searchPageState: MutableIntState,
    val searchMaxPageState: MutableState<Int?>,
    val loadingMoreState: MutableState<Boolean>,
    val searchCanLoadMoreState: MutableState<Boolean>,
    val hasSearchedState: MutableState<Boolean>,
    val searchHistoryState: MutableState<List<String>>,
    val searchHistoryNoteState: MutableState<String?>,
    val searchCauseState: MutableState<String?>,
    val loadMoreTickState: MutableIntState,
    val searchHistoryChips: List<String>,
    val searchMoreState: DesktopComicSearchMore,
    val searchFailedFooter: String?,
    val searchEmptyNote: String?,
    val gallerySearchR18State: MutableState<Boolean>,
    val gallerySearchQueryState: MutableState<String>,
    val gallerySearchSourceState: MutableState<Int>,
    val gallerySearchPageState: MutableState<DesktopWallPage?>,
    val gallerySearchPhaseState: MutableState<DesktopGalleryMorePhase>,
    val gallerySearchSearchingState: MutableState<Boolean>,
    val gallerySearchFailedState: MutableState<Boolean>,
    val gallerySearchHistoryState: MutableState<List<GallerySearchEntry>>,
    val gallerySearchHistoryNoteState: MutableState<String?>,
    val gallerySearchTickState: MutableIntState,
    val gallerySearchMoreTickState: MutableIntState,
    val gallerySearchJobState: MutableState<Job?>,
    val gallerySearchModeState: MutableState<Int>,
    val gallerySearchRankingState: MutableState<GalleryRanking>,
    val gallerySearchRankingAnchorState: MutableState<LocalDate?>,
    val pixivSearchTermState: MutableState<String>,
    val pixivSearchFilterState: MutableState<PixivSearchFilter>,
    val pixivSearchKindState: MutableIntState,
    val pixivSearchPageState: MutableState<DesktopWallPage?>,
    val pixivSearchPhaseState: MutableState<DesktopGalleryMorePhase>,
    val pixivSearchSearchingState: MutableState<Boolean>,
    val pixivSearchTickState: MutableIntState,
    val pixivSearchMoreTickState: MutableIntState,
    val pixivSearchUsersState: MutableState<List<PixivUser>>,
    val pixivSearchUsersCursorState: MutableState<String?>,
    val pixivCollectionSearch: PixivCollectionSearchLeg,
    val galleryAuxUi: GalleryAuxUiState,
    val gallerySuggestCandidatesState: MutableState<List<GalleryTagSuggestion>>,
    val gallerySearchToday: LocalDate,
    val gallerySearchSourceOptions: List<GallerySearchSource>,
    val gallerySearchRankingChoices: List<DesktopRankingChoice>,
    val gallerySearchRankingLanding: GalleryRanking,
    val gallerySearchPeriodReading: String?,
    val gallerySearchCanPeriodBack: Boolean,
    val gallerySearchCanPeriodForward: Boolean,
    val pixivSearchCards: List<DesktopWallCardModel>,
    val pixivSearchFooter: DesktopGalleryWallFooter,
    val gallerySearchCards: List<DesktopWallCardModel>,
    val gallerySearchFooter: DesktopGalleryWallFooter,
)

@Composable
internal fun rememberDesktopSearchAndGallerySearchSlice(
    ports: GalleryPorts,
    railPrefs: JsonKeyValueStore?,
): DesktopSearchAndGallerySearchSlice {
    val searchQueryState = remember { mutableStateOf("") }
    val searchResultsState = remember { mutableStateOf<List<EngineSession.ComicCard>>(emptyList()) }
    val searchingState = remember { mutableStateOf(false) }
    val searchTickState = remember { mutableIntStateOf(0) }
    val searchSourceKeyState = remember { mutableStateOf<String?>(null) }
    val aggregateSearch = remember { DesktopAggregateSearchHolder() }
    val searchReturnRouteState = remember { mutableStateOf<DesktopRoute?>(null) }
    val searchFilterGroupsState = remember { mutableStateOf<List<EngineSession.SearchOption>>(emptyList()) }
    val searchFilterValuesState = remember { mutableStateOf<List<String?>>(emptyList()) }
    val searchPageState = remember { mutableIntStateOf(1) }
    val searchMaxPageState = remember { mutableStateOf<Int?>(null) }
    val loadingMoreState = remember { mutableStateOf(false) }
    val searchCanLoadMoreState = remember { mutableStateOf(false) }
    val hasSearchedState = remember { mutableStateOf(false) }
    val searchHistoryState = remember { mutableStateOf<List<String>>(emptyList()) }
    val searchHistoryNoteState = remember { mutableStateOf<String?>(null) }
    val searchCauseState = remember { mutableStateOf<String?>(null) }
    val loadMoreTickState = remember { mutableIntStateOf(0) }

    val searchQuery by searchQueryState
    val searchResults by searchResultsState
    val searching by searchingState
    val searchHistory by searchHistoryState
    val loadingMore by loadingMoreState
    val searchCanLoadMore by searchCanLoadMoreState
    val hasSearched by hasSearchedState
    val searchCause by searchCauseState

    val isAggregateTarget = aggregateSearch.target is com.venera.desktop.search.DesktopSearchTarget.Aggregate
    val searchHistoryChips = desktopComicSearchHistoryChips(
        history = searchHistory,
        query = searchQuery,
        searching = if (isAggregateTarget) aggregateSearch.searching else searching,
        resultCount = if (isAggregateTarget) aggregateSearch.lanes.values.sumOf { it.cards.size } else searchResults.size,
    )
    val searchMoreState = desktopComicSearchMoreState(
        loadingMore = loadingMore,
        canLoadMore = searchCanLoadMore,
        hasSearched = hasSearched,
        resultsEmpty = searchResults.isEmpty(),
        reason = searchCause,
    )
    val searchFailedFooter = if (searchMoreState == DesktopComicSearchMore.Failed) {
        desktopComicSearchFailedFooter(searchCause)
    } else {
        null
    }
    val searchEmptyNote = desktopComicSearchEmptyNote(
        query = searchQuery.trim(),
        sourceKey = DESKTOP_EXPLORE_SOURCE,
        searched = hasSearched,
        searching = searching,
        resultCount = searchResults.size,
        reason = searchCause,
    )

    val gallerySearchR18State = remember {
        mutableStateOf((railPrefs?.getInt("pref_gallery_search_r18", 0) ?: 0) == 1)
    }
    val gallerySearchQueryState = remember { mutableStateOf("") }
    val gallerySearchSourceState = remember { mutableStateOf(0) }
    val gallerySearchPageState = remember { mutableStateOf<DesktopWallPage?>(null) }
    val gallerySearchPhaseState = remember { mutableStateOf<DesktopGalleryMorePhase>(DesktopGalleryMorePhase.Idle) }
    val gallerySearchSearchingState = remember { mutableStateOf(false) }
    val gallerySearchFailedState = remember { mutableStateOf(false) }
    val gallerySearchHistoryState = remember { mutableStateOf<List<GallerySearchEntry>>(emptyList()) }
    val gallerySearchHistoryNoteState = remember { mutableStateOf<String?>(null) }
    val gallerySearchTickState = remember { mutableIntStateOf(0) }
    val gallerySearchMoreTickState = remember { mutableIntStateOf(0) }
    val gallerySearchJobState = remember { mutableStateOf<Job?>(null) }
    val gallerySearchModeState = remember { mutableStateOf(0) }
    val gallerySearchRankingState = remember { mutableStateOf(GalleryRanking.NEWEST) }
    val gallerySearchRankingAnchorState = remember { mutableStateOf<LocalDate?>(null) }

    val pixivSearchTermState = remember { mutableStateOf("") }
    val pixivSearchFilterState = remember { mutableStateOf(PixivSearchFilter.DEFAULT) }
    val pixivSearchKindState = remember { mutableIntStateOf(DESKTOP_PIXIV_SEARCH_WORKS) }
    val pixivSearchPageState = remember { mutableStateOf<DesktopWallPage?>(null) }
    val pixivSearchPhaseState = remember { mutableStateOf<DesktopGalleryMorePhase>(DesktopGalleryMorePhase.Idle) }
    val pixivSearchSearchingState = remember { mutableStateOf(false) }
    val pixivSearchTickState = remember { mutableIntStateOf(0) }
    val pixivSearchMoreTickState = remember { mutableIntStateOf(0) }
    val pixivSearchUsersState = remember { mutableStateOf<List<PixivUser>>(emptyList()) }
    val pixivSearchUsersCursorState = remember { mutableStateOf<String?>(null) }
    val pixivCollectionSearch = remember { PixivCollectionSearchLeg() }

    val galleryAuxUi = remember { GalleryAuxUiState() }
    val gallerySuggestCandidatesState = remember { mutableStateOf<List<GalleryTagSuggestion>>(emptyList()) }

    val gallerySearchSourceOptions = desktopGallerySearchSourceOptions()
    val gallerySearchToday = LocalDate.now(ZoneOffset.UTC)
    val gallerySearchSource by gallerySearchSourceState
    val gallerySearchRanking by gallerySearchRankingState
    val gallerySearchRankingAnchor by gallerySearchRankingAnchorState
    val gallerySearchLegs = gallerySearchSourceOptions[gallerySearchSource.coerceIn(0, gallerySearchSourceOptions.lastIndex)]
        .availableLegs(desktopGelbooruConfigured(ports))
    val gallerySearchRankingChoices = desktopRankingChoices(gallerySearchLegs, gallerySearchToday)
    val gallerySearchRankingLanding = desktopRankingLanding(gallerySearchLegs, gallerySearchRanking)
    val gallerySearchPeriodReading = desktopRankingAnchorReading(
        gallerySearchRankingLanding, gallerySearchRankingAnchor, gallerySearchToday,
    ).takeIf { it.isNotEmpty() }
    val gallerySearchCanPeriodBack = desktopRankingStep(
        gallerySearchRankingLanding, gallerySearchRankingAnchor, backwards = true, todayUtc = gallerySearchToday,
    ) != null
    val gallerySearchCanPeriodForward = desktopRankingStep(
        gallerySearchRankingLanding, gallerySearchRankingAnchor, backwards = false, todayUtc = gallerySearchToday,
    ) != null

    val pixivSearchPage by pixivSearchPageState
    val pixivSearchPhase by pixivSearchPhaseState
    val pixivSearchCards = pixivSearchPage?.cards.orEmpty()
    val pixivSearchFooter = desktopGalleryWallFooter(
        batch = pixivSearchPage?.batch,
        onScreen = pixivSearchCards.size,
        phase = pixivSearchPhase,
        unit = "张",
    )
    val gallerySearchPage by gallerySearchPageState
    val gallerySearchPhase by gallerySearchPhaseState
    val gallerySearchCards = gallerySearchPage?.cards.orEmpty()
    val gallerySearchFooter = desktopGalleryWallFooter(
        batch = gallerySearchPage?.batch,
        onScreen = gallerySearchCards.size,
        phase = gallerySearchPhase,
        unit = "张",
    )

    return DesktopSearchAndGallerySearchSlice(
        searchQueryState = searchQueryState,
        searchResultsState = searchResultsState,
        searchingState = searchingState,
        searchTickState = searchTickState,
        searchSourceKeyState = searchSourceKeyState,
        aggregateSearch = aggregateSearch,
        searchReturnRouteState = searchReturnRouteState,
        searchFilterGroupsState = searchFilterGroupsState,
        searchFilterValuesState = searchFilterValuesState,
        searchPageState = searchPageState,
        searchMaxPageState = searchMaxPageState,
        loadingMoreState = loadingMoreState,
        searchCanLoadMoreState = searchCanLoadMoreState,
        hasSearchedState = hasSearchedState,
        searchHistoryState = searchHistoryState,
        searchHistoryNoteState = searchHistoryNoteState,
        searchCauseState = searchCauseState,
        loadMoreTickState = loadMoreTickState,
        searchHistoryChips = searchHistoryChips,
        searchMoreState = searchMoreState,
        searchFailedFooter = searchFailedFooter,
        searchEmptyNote = searchEmptyNote,
        gallerySearchR18State = gallerySearchR18State,
        gallerySearchQueryState = gallerySearchQueryState,
        gallerySearchSourceState = gallerySearchSourceState,
        gallerySearchPageState = gallerySearchPageState,
        gallerySearchPhaseState = gallerySearchPhaseState,
        gallerySearchSearchingState = gallerySearchSearchingState,
        gallerySearchFailedState = gallerySearchFailedState,
        gallerySearchHistoryState = gallerySearchHistoryState,
        gallerySearchHistoryNoteState = gallerySearchHistoryNoteState,
        gallerySearchTickState = gallerySearchTickState,
        gallerySearchMoreTickState = gallerySearchMoreTickState,
        gallerySearchJobState = gallerySearchJobState,
        gallerySearchModeState = gallerySearchModeState,
        gallerySearchRankingState = gallerySearchRankingState,
        gallerySearchRankingAnchorState = gallerySearchRankingAnchorState,
        pixivSearchTermState = pixivSearchTermState,
        pixivSearchFilterState = pixivSearchFilterState,
        pixivSearchKindState = pixivSearchKindState,
        pixivSearchPageState = pixivSearchPageState,
        pixivSearchPhaseState = pixivSearchPhaseState,
        pixivSearchSearchingState = pixivSearchSearchingState,
        pixivSearchTickState = pixivSearchTickState,
        pixivSearchMoreTickState = pixivSearchMoreTickState,
        pixivSearchUsersState = pixivSearchUsersState,
        pixivSearchUsersCursorState = pixivSearchUsersCursorState,
        pixivCollectionSearch = pixivCollectionSearch,
        galleryAuxUi = galleryAuxUi,
        gallerySuggestCandidatesState = gallerySuggestCandidatesState,
        gallerySearchToday = gallerySearchToday,
        gallerySearchSourceOptions = gallerySearchSourceOptions,
        gallerySearchRankingChoices = gallerySearchRankingChoices,
        gallerySearchRankingLanding = gallerySearchRankingLanding,
        gallerySearchPeriodReading = gallerySearchPeriodReading,
        gallerySearchCanPeriodBack = gallerySearchCanPeriodBack,
        gallerySearchCanPeriodForward = gallerySearchCanPeriodForward,
        pixivSearchCards = pixivSearchCards,
        pixivSearchFooter = pixivSearchFooter,
        gallerySearchCards = gallerySearchCards,
        gallerySearchFooter = gallerySearchFooter,
    )
}

/** 源管理、本地文件扫描与导入状态分组。 */
internal class DesktopSourceAndFilesSlice(
    val sourceUi: SourceManageUiState,
    val sourceTickState: MutableIntState,
    val runSourceImport: () -> Unit,
    val performSourceUpdateCheck: suspend (Boolean) -> Unit,
    val filesUi: FilesAuxUiState,
    val downloadsTickState: MutableIntState,
    val localComicScanState: MutableState<DesktopLocalComicLibraryScan?>,
    val localComicImportTickState: MutableState<Int>,
    val historyTickState: MutableIntState,
    val armedDeleteKeyState: MutableState<String?>,
    val filesSegmentState: MutableState<DesktopFilesSegment>,
)

@Composable
internal fun rememberDesktopSourceAndFilesSlice(
    scope: CoroutineScope,
    engine: EngineSession?,
    overrideDir: File?,
    paths: com.venera.compose.data.platform.PathProvider? = null,
    assetDir: File? = null,
): DesktopSourceAndFilesSlice {
    val sourceUi = remember {
        SourceManageUiState().apply {
            selectedSourceRepo = com.venera.desktop.gallery.data.DesktopSourceRepoPreferences.readSelectedRepo(paths)
        }
    }
    val sourceTickState = remember { mutableIntStateOf(0) }
    var sourceTick by sourceTickState
    val validateScript: suspend (fileKey: String, text: String) -> Result<String?> = { fileKey, text ->
        desktopEngineCall(engine) { engine?.checkLoadable(fileKey, text) }
    }
    val installSourceText: suspend (fileKey: String, text: String, version: String, repoId: String) -> String? =
        { fileKey, text, version, repoId ->
            val dir = overrideDir
            if (dir == null) {
                "改写层目录建不出来，装不进去。"
            } else if (!DesktopSourceOverrides.isSafeKey(fileKey)) {
                "key 不合法（只许字母数字与 _ -）：$fileKey"
            } else {
                validateScript(fileKey, text).fold(
                    onSuccess = {
                        DesktopSourceOverrides.writeText(dir, fileKey, text)
                        com.venera.desktop.gallery.data.DesktopSourceUpdater.markInstalled(dir, fileKey, version, repoId)
                        if (assetDir != null && paths != null) {
                            runCatching {
                                com.venera.desktop.data.writeDesktopSourceEnabled(
                                    assetDir = assetDir,
                                    sourceState = com.venera.desktop.gallery.data.DesktopSourceState(paths),
                                    key = fileKey,
                                    overrideDir = dir,
                                    enabled = true,
                                )
                            }
                        }
                        sourceTick++
                        null
                    },
                    onFailure = { "验装载没过：${it.message?.take(140)}" },
                )
            }
        }
    val runSourceImport: () -> Unit = {
        runSourceImportHelper(installSourceText, sourceUi, scope) { sourceTick++ }
    }
    val performSourceUpdateCheck: suspend (announce: Boolean) -> Unit = { announce ->
        performSourceUpdateCheckHelper(announce, sourceUi, overrideDir, engine, paths) { sourceTick++ }
    }
    sourceUi.onSelectRepo = { repo ->
        sourceUi.selectedSourceRepo = repo
        com.venera.desktop.gallery.data.DesktopSourceRepoPreferences.saveSelectedRepo(paths, repo)
        sourceUi.sourceUpdateNote = null
    }
    sourceUi.onCheckSync = {
        scope.launch { performSourceUpdateCheck(false) }
    }
    sourceUi.onInstallStoreEntry = { entry ->
        installRepoSourceEntry(
            entry = entry,
            proxy = com.venera.desktop.gallery.data.DesktopSourceRepoPreferences.resolveProxy(paths),
            overrideDir = overrideDir,
            assetDir = assetDir,
            paths = paths,
            validateScript = validateScript,
            onSuccess = { sourceTick++ },
        )
    }
    sourceUi.proxyProvider = {
        com.venera.desktop.gallery.data.DesktopSourceRepoPreferences.resolveProxy(paths)
    }

    val filesUi = remember { FilesAuxUiState() }
    val downloadsTickState = remember { mutableIntStateOf(0) }
    val localComicScanState = remember { mutableStateOf<DesktopLocalComicLibraryScan?>(null) }
    val localComicImportTickState = remember { mutableStateOf(0) }
    val historyTickState = remember { mutableIntStateOf(0) }
    val armedDeleteKeyState = remember { mutableStateOf<String?>(null) }
    val filesSegmentState = remember { mutableStateOf(DesktopFilesSegment.QUEUE) }

    return DesktopSourceAndFilesSlice(
        sourceUi = sourceUi,
        sourceTickState = sourceTickState,
        runSourceImport = runSourceImport,
        performSourceUpdateCheck = performSourceUpdateCheck,
        filesUi = filesUi,
        downloadsTickState = downloadsTickState,
        localComicScanState = localComicScanState,
        localComicImportTickState = localComicImportTickState,
        historyTickState = historyTickState,
        armedDeleteKeyState = armedDeleteKeyState,
        filesSegmentState = filesSegmentState,
    )
}
