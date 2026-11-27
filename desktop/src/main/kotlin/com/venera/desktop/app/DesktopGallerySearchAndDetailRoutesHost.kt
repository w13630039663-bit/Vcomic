package com.venera.desktop.app

import com.venera.desktop.ui.DesktopScrollAnchor
import com.venera.desktop.ui.FilesAuxUiState
import com.venera.desktop.ui.GalleryAuxUiState
import com.venera.desktop.ui.PixivCollectionSearchLeg
import com.venera.desktop.ui.constFlow
import com.venera.desktop.ui.rememberRetainedLazyListState
import com.venera.desktop.ui.rememberRetainedLazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.compose.data.platform.JsonKeyValueStore
import com.venera.compose.data.platform.PathProvider
import com.venera.compose.gallery.data.GalleryContentGuard
import com.venera.compose.gallery.data.GalleryFavorite
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.data.GalleryTagSuggestion
import com.venera.compose.gallery.data.PixivUser
import com.venera.compose.gallery.domain.GalleryAnimatedMode
import com.venera.compose.gallery.domain.GalleryRanking
import com.venera.compose.gallery.domain.GallerySearchEntry
import com.venera.compose.gallery.domain.GallerySearchSource
import com.venera.compose.gallery.domain.PixivSearchFilter
import com.venera.compose.gallery.ui.GalleryBookmarksHubScreen
import com.venera.desktop.gallery.data.DesktopGalleryPreferences
import com.venera.desktop.gallery.data.DesktopPixivImageBytes
import com.venera.desktop.gallery.data.desktopResolveSaveDir
import com.venera.desktop.gallery.data.desktopSaveImageRun
import com.venera.desktop.gallery.data.desktopSaveImageScreenReading
import com.venera.desktop.gallery.ui.DesktopDetailPaneWidth
import com.venera.desktop.gallery.ui.DesktopDetailTier
import com.venera.desktop.gallery.ui.DesktopGalleryPostDetailScreen
import com.venera.desktop.gallery.ui.DesktopGallerySearchScreen
import com.venera.desktop.gallery.ui.DesktopGalleryWallFooter
import com.venera.desktop.gallery.ui.DesktopRankingChoice
import com.venera.desktop.gallery.ui.DesktopWallCardModel
import com.venera.desktop.gallery.ui.DesktopWallPage
import com.venera.desktop.gallery.ui.desktopCopyImageUrl
import com.venera.desktop.gallery.ui.desktopGelbooruConfigured
import com.venera.desktop.gallery.ui.desktopHistoryClearAll
import com.venera.desktop.gallery.ui.desktopHistoryLanding
import com.venera.desktop.gallery.ui.desktopHistoryPush
import com.venera.desktop.gallery.ui.desktopHistoryStore
import com.venera.desktop.gallery.ui.desktopRankingStep
import com.venera.desktop.gallery.ui.desktopRankingLanding
import com.venera.desktop.gallery.ui.desktopSearchHistoryEntry
import com.venera.desktop.gallery.ui.rememberDesktopGallerySearchPagerController
import com.venera.desktop.search.desktopComicSearchHistoryNoteText
import com.venera.desktop.ui.components.DesktopPetCue
import com.venera.desktop.ui.components.DesktopPetCueBus
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 画廊标签/Pixiv 双模式搜索路由（[DesktopRoute.GallerySearch]）实现。
 */
@Composable
internal fun DesktopGallerySearchRoute(
    layoutMode: WideScreenLayoutMode,
    ports: GalleryPorts,
    scope: CoroutineScope,
    galleryPrefs: DesktopGalleryPreferences?,
    railPrefs: JsonKeyValueStore?,
    maskModeOf: () -> String,
    wallGuardOf: () -> GalleryContentGuard?,
    wallBlockAiOf: () -> Boolean,
    gallerySearchToday: LocalDate,
    gallerySearchSourceOptions: List<GallerySearchSource>,
    gallerySearchRankingChoices: List<DesktopRankingChoice>,
    gallerySearchRankingLanding: GalleryRanking,
    gallerySearchPeriodReading: String?,
    gallerySearchCanPeriodBack: Boolean,
    gallerySearchCanPeriodForward: Boolean,
    gallerySearchCards: List<DesktopWallCardModel>,
    gallerySearchFooter: DesktopGalleryWallFooter,
    pixivSearchCards: List<DesktopWallCardModel>,
    pixivSearchFooter: DesktopGalleryWallFooter,
    pixivCollectionSearch: PixivCollectionSearchLeg,
    searchResultAnchor: DesktopScrollAnchor,
    searchArtistAnchor: DesktopScrollAnchor,
    galleryAuxUi: GalleryAuxUiState,
    openWallCardAny: (DesktopWallCardModel) -> Unit,
    routeState: MutableState<DesktopRoute>,
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
    gallerySearchRankingState: MutableState<GalleryRanking>,
    gallerySearchRankingAnchorState: MutableState<LocalDate?>,
    pixivSearchTermState: MutableState<String>,
    pixivSearchKindState: MutableIntState,
    pixivSearchTickState: MutableIntState,
    pixivSearchMoreTickState: MutableIntState,
    pixivSearchFilterState: MutableState<PixivSearchFilter>,
    pixivSearchPageState: MutableState<DesktopWallPage?>,
    pixivSearchSearchingState: MutableState<Boolean>,
    pixivSearchUsersState: MutableState<List<PixivUser>>,
    gallerySuggestCandidatesState: MutableState<List<GalleryTagSuggestion>>,
    searchQueryState: MutableState<String>,
) {
    var route by routeState
    var gallerySearchQuery by gallerySearchQueryState
    var gallerySearchSource by gallerySearchSourceState
    var gallerySearchTick by gallerySearchTickState
    var gallerySearchMoreTick by gallerySearchMoreTickState
    var gallerySearchMode by gallerySearchModeState
    var gallerySearchR18 by gallerySearchR18State
    val gallerySearchPage by gallerySearchPageState
    val gallerySearchSearching by gallerySearchSearchingState
    val gallerySearchFailed by gallerySearchFailedState
    var gallerySearchHistory by gallerySearchHistoryState
    var gallerySearchHistoryNote by gallerySearchHistoryNoteState
    var gallerySearchRanking by gallerySearchRankingState
    var gallerySearchRankingAnchor by gallerySearchRankingAnchorState
    var pixivSearchTerm by pixivSearchTermState
    var pixivSearchKind by pixivSearchKindState
    var pixivSearchTick by pixivSearchTickState
    var pixivSearchMoreTick by pixivSearchMoreTickState
    var pixivSearchFilter by pixivSearchFilterState
    val pixivSearchPage by pixivSearchPageState
    val pixivSearchSearching by pixivSearchSearchingState
    val pixivSearchUsers by pixivSearchUsersState
    val gallerySuggestCandidates by gallerySuggestCandidatesState
    val searchQuery by searchQueryState

    val pager = rememberDesktopGallerySearchPagerController(
        ports = ports,
        scope = scope,
        galleryPrefs = galleryPrefs,
        maskModeOf = maskModeOf,
        wallGuardOf = wallGuardOf,
        wallBlockAiOf = wallBlockAiOf,
        gallerySearchMode = gallerySearchMode,
        gallerySearchQuery = gallerySearchQuery,
        gallerySearchSource = gallerySearchSource,
        gallerySearchSourceOptions = gallerySearchSourceOptions,
        gallerySearchRanking = gallerySearchRanking,
        gallerySearchRankingLanding = gallerySearchRankingLanding,
        gallerySearchRankingAnchor = gallerySearchRankingAnchor,
        gallerySearchToday = gallerySearchToday,
        gallerySearchR18 = gallerySearchR18,
        gallerySearchPageState = gallerySearchPageState,
        gallerySearchTickState = gallerySearchTickState,
        pixivSearchTerm = pixivSearchTerm,
        pixivSearchFilter = pixivSearchFilter,
        pixivSearchKind = pixivSearchKind,
        pixivSearchPageState = pixivSearchPageState,
        pixivSearchTickState = pixivSearchTickState,
    )

    DesktopGallerySearchScreen(
        mode = layoutMode,
        resultGridState = rememberRetainedLazyStaggeredGridState(searchResultAnchor),
        artistListState = rememberRetainedLazyListState(searchArtistAnchor),
        query = gallerySearchQuery,
        onQueryChange = { gallerySearchQuery = it },
        onSearch = {
            petSearch(searchQuery)
            val firedWord = if (gallerySearchMode == 1) pixivSearchTerm else gallerySearchQuery
            petSearch(firedWord)
            desktopSearchHistoryEntry(
                if (gallerySearchMode == 1) {
                    listOf(GallerySite.PIXIV)
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
            gallerySearchRanking = desktopRankingLanding(
                gallerySearchSourceOptions[index].availableLegs(desktopGelbooruConfigured(ports)),
                gallerySearchRanking,
            )
            gallerySearchTick++
        },
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
        paginationMode = pager.mode,
        onPickPaginationMode = pager.onPickMode,
        currentPage = pager.currentPage,
        canNextPage = pager.canNextPage,
        pageLoading = pager.pageLoading,
        onPrevPage = pager.onPrevPage,
        onNextPage = pager.onNextPage,
        onJumpPage = pager.onJumpPage,
        history = gallerySearchHistory,
        onPickHistory = { entry ->
            val landing = desktopHistoryLanding(entry)
            val pixivEntry = landing.source.sites == listOf(GallerySite.PIXIV)
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
                        did.exceptionOrNull()?.let { "${it.javaClass.simpleName} ${it.message?.take(120) ?: ""}" },
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
        suggestCandidates = gallerySuggestCandidates,
        suggestLine = galleryAuxUi.gallerySuggestLine,
        onPickSuggestion = { name ->
            if (gallerySearchMode == 1) pixivSearchTerm = name else gallerySearchQuery = name
        },
        onOpenPost = openWallCardAny,
        onOpenCardArtist = { id, name ->
            route = DesktopRoute.PixivArtist(
                userId = id,
                name = name,
                from = DesktopRoute.GallerySearch,
            )
        },
    )
}

/**
 * 画廊本地书签流（[DesktopRoute.GalleryBookmarksHub]）与画廊作品详情页（[DesktopRoute.GalleryPostDetail]）实现。
 */
@Composable
internal fun DesktopGalleryBookmarksAndPostDetailRoute(
    r: DesktopRoute,
    ports: GalleryPorts,
    galleryPaths: PathProvider?,
    galleryPrefs: DesktopGalleryPreferences?,
    desktopUx: DesktopUxPlatformServices,
    shellBackdropUrl: String?,
    galleryFavorites: List<GalleryFavorite>,
    filesUi: FilesAuxUiState,
    openGalleryPostAny: (GalleryPost) -> Unit,
    searchHandoff: (List<GallerySite>, String) -> Unit,
    flyToDownloadCenter: (String?, Boolean) -> Unit,
    routeState: MutableState<DesktopRoute>,
) {
    var route by routeState
    when (r) {
        DesktopRoute.GalleryBookmarksHub -> GalleryBookmarksHubScreen(
            onOpenPost = { post ->
                petPost(post.author)
                openGalleryPostAny(post)
            },
            onOpenPixivBookmarks = { route = DesktopRoute.PixivAccount },
            onBack = { route = DesktopRoute.Tab },
            favorites = ports.favorites,
        )

        is DesktopRoute.GalleryPostDetail -> {
            val savePaths = galleryPaths
            DesktopGalleryPostDetailScreen(
                post = r.post,
                favoriteUids = galleryFavorites.map { it.uid }.toSet(),
                lexicon = ports.lexicon,
                pixiv = ports.pixiv,
                paths = galleryPaths,
                animatedAutoPlay = (galleryPrefs?.galleryAnimated
                    ?: constFlow(GalleryAnimatedMode.ALWAYS))
                    .collectAsState().value != GalleryAnimatedMode.NEVER,
                videoAutoPlay = (galleryPrefs?.videoAutoPlay ?: constFlow(true)).collectAsState().value,
                detailQuality = (galleryPrefs?.detailQuality ?: constFlow(DesktopDetailTier.SAMPLE))
                    .collectAsState().value,
                paneWidth = (galleryPrefs?.detailPaneWidth ?: constFlow(DesktopDetailPaneWidth.Standard))
                    .collectAsState().value,
                paneOpenDefault = (galleryPrefs?.detailPaneOpen ?: constFlow(true)).collectAsState().value,
                onPaneOpenPersist = { on -> galleryPrefs?.setDetailPaneOpen(on) },
                backdropFromShell = shellBackdropUrl != null,
                onBack = { route = r.from ?: DesktopRoute.Tab },
                onOpenViewer = { card -> route = DesktopRoute.GalleryPostViewer(card, from = r) },
                onToggleFavorite = { card ->
                    DesktopPetCueBus.emit(DesktopPetCue.TOGGLE_FAVORITE)
                    card.sourcePost?.let { ports.favorites.toggle(it) }
                        ?: (card.uid in galleryFavorites.map { it.uid })
                },
                onSaveImage = savePaths?.let {
                    { card ->
                        val source = card.sourcePost
                        val target = if (source == null) null else desktopResolveSaveDir(savePaths, galleryPrefs)
                        when {
                            source == null -> "这一张没有站方原模型，存图动作接不上"
                            target == null -> "已取消保存（没选目录）。"
                            else -> {
                                flyToDownloadCenter(card.url, false)
                                val reading = desktopSaveImageScreenReading(
                                    desktopSaveImageRun(desktopUx.platformContext(), target, source, galleryPaths),
                                )
                                if (reading.startsWith("已存到本机")) filesUi.imageScanTick++
                                reading
                            }
                        }
                    }
                },
                onCopyImage = galleryPaths?.let { pp ->
                    { card ->
                        runCatching {
                            withContext(Dispatchers.IO) {
                                val bytes = DesktopPixivImageBytes.fetch(paths = pp, url = desktopCopyImageUrl(card))
                                DesktopPixivImageBytes.copyToClipboard(bytes)
                            }
                        }.isSuccess
                    }
                },
                onSearchTag = { site, tag -> searchHandoff(listOf(site), tag) },
                followedArtists = ports.follows.follows.collectAsState().value,
                onToggleFollow = { followedSite, followedName ->
                    ports.follows.toggle(followedSite, followedName).also { nowFollowing ->
                        desktopUx.toast(if (nowFollowing) "已关注画师" else "已取消关注")
                    }
                },
            )
        }

        else -> Unit
    }
}
