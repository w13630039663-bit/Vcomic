package com.venera.desktop.app

import com.venera.desktop.ui.BrowseClearUiState
import com.venera.desktop.ui.DailyGalleryUiState
import com.venera.desktop.ui.DesktopGalleryWallPane
import com.venera.desktop.ui.DesktopHomeLightScreen
import com.venera.desktop.ui.DesktopHomePane
import com.venera.desktop.ui.DesktopHomeSourceStatusRow
import com.venera.desktop.data.DesktopHomeStats
import com.venera.desktop.ui.DesktopScrollAnchor
import com.venera.desktop.ui.DetailAndReaderUiState
import com.venera.desktop.ui.FilesAuxUiState
import com.venera.desktop.ui.GalleryAuxUiState
import com.venera.desktop.ui.PixivDiscoverLeg
import com.venera.desktop.ui.PixivPreviewLeg
import com.venera.desktop.ui.SourceManageUiState
import com.venera.desktop.ui.rememberRetainedLazyListState
import com.venera.desktop.ui.rememberRetainedLazyStaggeredGridState
import com.venera.desktop.data.DesktopHomeHistorySnapshot
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.venera.compose.components.WideScreenLayoutMode
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
import com.venera.desktop.downloads.DesktopFilesDomain
import com.venera.desktop.downloads.DesktopFilesSegment
import com.venera.desktop.gallery.data.DesktopComicQueueTask
import com.venera.desktop.gallery.data.DesktopGalleryPreferences
import com.venera.desktop.gallery.data.DesktopLocalComicLibraryScan
import com.venera.desktop.gallery.data.DesktopPixivAccount
import com.venera.desktop.gallery.ui.DesktopGalleryRatingFilter
import com.venera.desktop.gallery.ui.DesktopGallerySort
import com.venera.desktop.gallery.ui.DesktopGalleryWallFooter
import com.venera.desktop.gallery.ui.DesktopWallCardModel
import com.venera.desktop.gallery.ui.DesktopWallDensity
import com.venera.desktop.gallery.ui.DesktopWallPage
import com.venera.desktop.gallery.ui.desktopComicHistoryOpenSourceOf
import com.venera.desktop.gallery.ui.desktopSearchSeedFor
import com.venera.engineprobe.EngineSession
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 画廊（GALLERY）页签视图装配，委托给 [DesktopGalleryWallPane]。
 */
@Composable
internal fun DesktopGalleryWallPaneTab(
    layoutMode: WideScreenLayoutMode,
    ports: GalleryPorts,
    galleryPrefs: DesktopGalleryPreferences?,
    galleryPaths: PathProvider?,
    railPrefs: JsonKeyValueStore?,
    scope: CoroutineScope,
    desktopUx: DesktopUxPlatformServices,
    searchHandoff: (List<GallerySite>, String) -> Unit,
    pixivSuggestEngine: com.venera.compose.data.platform.HttpEngine?,
    browseClearUi: BrowseClearUiState,
    galleryAuxUi: GalleryAuxUiState,
    homeArtists: GalleryFollowedArtistsReadout,
    wallListAnchor: DesktopScrollAnchor,
    wallStaggeredAnchor: DesktopScrollAnchor,
    wallLayout: GalleryWallLayout,
    wallFooter: DesktopGalleryWallFooter,
    dailyGallery: DailyGalleryUiState,
    pixivPreviewLeg: PixivPreviewLeg,
    gallerySearchSourceOptions: List<GallerySearchSource>,
    wallSelectedUids: SnapshotStateList<String>,
    openWallCardAny: (DesktopWallCardModel) -> Unit,
    routeState: MutableState<DesktopRoute>,
    gallerySearchQueryState: MutableState<String>,
    gallerySearchSourceState: MutableState<Int>,
    gallerySearchTickState: MutableIntState,
    gallerySearchModeState: MutableState<Int>,
    pixivSearchTermState: MutableState<String>,
    pixivSearchTickState: MutableIntState,
    gallerySearchR18State: MutableState<Boolean>,
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
) {
    var route by routeState
    var gallerySearchQuery by gallerySearchQueryState
    var gallerySearchSource by gallerySearchSourceState
    var gallerySearchTick by gallerySearchTickState
    var gallerySearchMode by gallerySearchModeState
    var pixivSearchTerm by pixivSearchTermState
    var pixivSearchTick by pixivSearchTickState
    var gallerySearchR18 by gallerySearchR18State
    val wallPage by wallPageState
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

    DesktopGalleryWallPane(
        layoutMode,
        wallPage = wallPage,
        wallRefreshing = wallRefreshing,
        wallFooter,
        onLoadMore = { wallMoreTick++ },
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
        onOpenBookmarks = { route = DesktopRoute.GalleryBookmarksHub },
        onOpenBrowseHistory = {
            browseClearUi.browseTick++
            route = DesktopRoute.GalleryBrowseHistory
        },
        onOpenPost = openWallCardAny,
        domain = galleryHomeDomain,
        onPickDomain = { picked ->
            petFilter()
            galleryHomeDomain = picked
            wallSelectedUids.clear()
            wallListAnchor.reset()
            wallStaggeredAnchor.reset()
        },
        followedArtists = homeArtists,
        onOpenArtist = { artist -> searchHandoff(artist.sites, artist.name) },
        dailyCards = dailyGallery.homeCards,
        rankingMode = galleryRankingMode,
        onPickRankingMode = { picked -> galleryRankingMode = picked },
        gridState = rememberRetainedLazyListState(wallListAnchor),
        ports = ports,
        galleryPrefs = galleryPrefs,
        onOpenPixivAccount = { route = DesktopRoute.PixivAccount },
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
        onOpenDaily = { route = DesktopRoute.GalleryDaily },
        sort = gallerySort,
        onPickSort = { picked ->
            gallerySort = picked
            wallSelectedUids.clear()
            wallListAnchor.reset()
            wallStaggeredAnchor.reset()
        },
        onRefresh = {
            wallRefreshing = true
            wallRefreshTick++
        },
        previewCards = pixivPreviewLeg.cards,
        previewAvatars = pixivPreviewLeg.avatars,
        previewTitles = pixivPreviewLeg.titles,
        pixivDiscover = pixivDiscoverLeg,
        onSearchTag = { tag ->
            searchHandoff(GallerySite.entries, tag)
        },
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
        booruRatingFilter = booruRatingFilter,
        onPickBooruRating = { picked -> booruRatingFilter = picked },
        booruSiteFilter = booruSiteFilter,
        onPickBooruSite = { picked -> booruSiteFilter = picked },
        tagDimensions = galleryAuxUi.tagDimensionGroups,
        tagDictionaryReady = galleryAuxUi.tagDictReady,
    )
}

/**
 * 首页（HOME）页签内容装配，按 [homeSkinLight] 分派至浅色首页或经典首页。
 */
@Composable
internal fun DesktopHomeTab(
    layoutMode: WideScreenLayoutMode,
    homeSkinLight: Boolean,
    homePixivAccount: DesktopPixivAccount?,
    railPrefs: JsonKeyValueStore?,
    downloadsRoot: File?,
    desktopUx: DesktopUxPlatformServices,
    searchHandoff: (List<GallerySite>, String) -> Unit,
    detailUi: DetailAndReaderUiState,
    sourceUi: SourceManageUiState,
    filesUi: FilesAuxUiState,
    localComicScan: DesktopLocalComicLibraryScan?,
    homeSourceRows: List<DesktopHomeSourceStatusRow>,
    historySourceName: String?,
    queueTasks: List<DesktopComicQueueTask>,
    homeHistory: DesktopHomeHistorySnapshot,
    homeStats: DesktopHomeStats,
    homeArtists: GalleryFollowedArtistsReadout,
    dailyGallery: DailyGalleryUiState,
    openWallCardAny: (DesktopWallCardModel) -> Unit,
    exploreSections: List<EngineSession.Section>,
    exploreNote: String?,
    routeState: MutableState<DesktopRoute>,
    homeRecommendComicsState: MutableState<List<EngineSession.ComicCard>>,
    homeRecommendNoteState: MutableState<String?>,
    historyTickState: MutableIntState,
    homeRecommendR18State: MutableState<Boolean>,
    homeRecommendFailedState: MutableState<Boolean>,
    homeRetryTickState: MutableState<Int>,
    filesSegmentState: MutableState<DesktopFilesSegment>,
) {
    var route by routeState
    var homeRecommendComics by homeRecommendComicsState
    var homeRecommendNote by homeRecommendNoteState
    var historyTick by historyTickState
    var homeRecommendR18 by homeRecommendR18State
    var homeRecommendFailed by homeRecommendFailedState
    var homeRetryTick by homeRetryTickState
    var filesSegment by filesSegmentState

    val openHistory: (com.venera.compose.data.db.HistoryRecord) -> Unit = { record ->
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
    }
    val dailyCards = if (dailyGallery.homePixivPick) dailyGallery.homePixivCards else dailyGallery.homeCards
    val recommendComics = if (homeRecommendComics.isNotEmpty()) homeRecommendComics else remember(exploreSections) {
        exploreSections.firstOrNull { it.title == "最新" }?.comics
            ?.takeIf { it.isNotEmpty() }
            ?: exploreSections.flatMap { it.comics }
    }
    val comicMaskedOf: (EngineSession.ComicCard) -> Boolean = { card ->
        val jmKey = sourceUi.sourceRows.firstOrNull { it.title.contains("禁漫") }?.key ?: ("j" + "m")
        desktopUx.coverMaskStateOf(
            sourceKey = jmKey,
            title = card.title,
            author = "",
            tags = emptyList(),
            comicId = card.id,
        ) != "VISIBLE"
    }
    val onOpenComicCard: (EngineSession.ComicCard) -> Unit = { card ->
        val jmKey = sourceUi.sourceRows.firstOrNull { it.title.contains("禁漫") }?.key ?: ("j" + "m")
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
    }
    val onPickRecommendR18: (Boolean) -> Unit = { on ->
        homeRecommendR18 = on
        railPrefs?.put("pref_home_recommend_r18", on)
        homeRecommendComics = emptyList()
        homeRecommendNote = null
        homeRetryTick++
    }
    val onPickDailyPixiv: (Boolean) -> Unit = { picked ->
        dailyGallery.homePixivPick = picked
        railPrefs?.put("pref_daily_home_pixiv", picked)
    }
    val onRetryHome: () -> Unit = {
        homeRecommendComics = emptyList()
        homeRecommendNote = null
        homeRecommendFailed = false
        dailyGallery.homeCards = emptyList()
        dailyGallery.homePixivCards = emptyList()
        dailyGallery.homeFailed = false
        dailyGallery.homeLoadedTick.intValue = -1
        dailyGallery.homePixivLoadedTick.intValue = -1
        homeRetryTick++
    }
    val onOpenLocalComics: (() -> Unit)? = if (downloadsRoot == null) null else {
        {
            filesUi.filesDomain = DesktopFilesDomain.COMIC
            filesSegment = DesktopFilesSegment.LOCAL
            route = DesktopRoute.Files
        }
    }
    val onOpenDownloads: (() -> Unit)? = if (downloadsRoot == null) null else {
        {
            filesUi.filesDomain = DesktopFilesDomain.COMIC
            filesSegment = DesktopFilesSegment.QUEUE
            route = DesktopRoute.Files
        }
    }

    if (homeSkinLight) {
        DesktopHomeLightScreen(
            mode = layoutMode,
            stats = homeStats,
            pixivAccount = homePixivAccount,
            history = homeHistory,
            onOpenHistory = openHistory,
            daily = dailyCards,
            followedArtists = homeArtists,
            recommendComics = recommendComics,
            comicMaskedOf = comicMaskedOf,
            recommendNote = homeRecommendNote ?: exploreNote,
            localComicCount = localComicScan?.comics?.size,
            onOpenPost = openWallCardAny,
            onOpenArtist = { artist -> searchHandoff(artist.sites, artist.name) },
            onOpenComicCard = onOpenComicCard,
            recommendR18 = homeRecommendR18,
            onPickRecommendR18 = onPickRecommendR18,
            recommendFailed = homeRecommendFailed,
            dailyFailed = dailyGallery.homeFailed,
            dailyPixiv = dailyGallery.homePixivPick,
            onPickDailyPixiv = onPickDailyPixiv,
            homeSourceRows = homeSourceRows,
            onRetryHome = onRetryHome,
            onOpenLocalComics = onOpenLocalComics,
            onGoSourceManage = { route = DesktopRoute.SourceManage },
            onOpenAllHistory = {
                historyTick++
                route = DesktopRoute.History
            },
            downloadTasks = queueTasks,
            onOpenDownloads = onOpenDownloads,
        )
    } else {
        DesktopHomePane(
            layoutMode,
            homeStats,
            homeHistory,
            onOpenHistory = openHistory,
            daily = dailyCards,
            followedArtists = homeArtists,
            recommendComics = recommendComics,
            recommendNote = homeRecommendNote ?: exploreNote,
            comicMaskedOf = comicMaskedOf,
            localComicCount = localComicScan?.comics?.size,
            onOpenPost = openWallCardAny,
            onOpenArtist = { artist -> searchHandoff(artist.sites, artist.name) },
            onOpenComicCard = onOpenComicCard,
            onOpenLocalComics = onOpenLocalComics,
            recommendR18 = homeRecommendR18,
            onPickRecommendR18 = onPickRecommendR18,
            recommendFailed = homeRecommendFailed,
            dailyFailed = dailyGallery.homeFailed,
            dailyPixiv = dailyGallery.homePixivPick,
            onPickDailyPixiv = onPickDailyPixiv,
            onRetryHome = onRetryHome,
            onGoSourceManage = { route = DesktopRoute.SourceManage },
            onOpenAllHistory = {
                historyTick++
                route = DesktopRoute.History
            },
            downloadTasks = queueTasks,
            onOpenDownloads = onOpenDownloads,
            pixivAccount = homePixivAccount,
        )
    }
}
