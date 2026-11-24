package com.venera.desktop.app

import com.venera.desktop.ui.DailyGalleryUiState
import com.venera.desktop.ui.DesktopHomeSourceStatusRow
import com.venera.desktop.data.DesktopHomeStats
import com.venera.desktop.ui.DesktopScrollAnchor
import com.venera.desktop.ui.NetFavUiState
import com.venera.desktop.ui.PixivDiscoverLeg
import com.venera.desktop.ui.PixivPreviewLeg
import com.venera.desktop.ui.constFlow
import com.venera.desktop.ui.currentWallQuality
import com.venera.desktop.data.DesktopHomeHistorySnapshot
import com.venera.desktop.data.DesktopHomeHistorySource
import com.venera.desktop.data.DesktopHomeStatsSource
import com.venera.desktop.data.DesktopReadingStats
import com.venera.desktop.data.DesktopReadingStatsSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import com.venera.compose.components.VeneraNavTab
import com.venera.compose.data.db.FavoriteRecord
import com.venera.compose.data.platform.JsonKeyValueStore
import com.venera.compose.data.platform.PathProvider
import com.venera.compose.gallery.data.GalleryContentGuard
import com.venera.compose.gallery.data.GalleryFavorite
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.data.PixivAuthState
import com.venera.compose.gallery.data.PixivRankingMode
import com.venera.compose.gallery.domain.GalleryFollowedArtistsReadout
import com.venera.compose.gallery.domain.GalleryHomeDomain
import com.venera.compose.gallery.domain.GalleryPreviewQuality
import com.venera.compose.gallery.domain.GalleryWallLayout
import com.venera.compose.gallery.ui.GalleryDailyCore
import com.venera.desktop.business.DesktopBusinessPorts
import com.venera.desktop.gallery.data.DesktopGalleryContentGuard
import com.venera.desktop.gallery.data.DesktopGalleryDailySnapshotStore
import com.venera.desktop.gallery.data.DesktopGalleryPreferences
import com.venera.desktop.gallery.data.DesktopGalleryTagDictionary
import com.venera.desktop.gallery.data.DesktopComicReaderPreferences
import com.venera.desktop.gallery.data.DesktopFavoriteFolderTab
import com.venera.desktop.gallery.data.DesktopReaderMode
import com.venera.desktop.gallery.data.DesktopSourceNames
import com.venera.desktop.gallery.data.desktopFavoriteFolderChoice
import com.venera.desktop.gallery.data.desktopFavoriteFolderCountNote
import com.venera.desktop.gallery.data.desktopFavoriteFolderFilterNote
import com.venera.desktop.gallery.data.desktopFavoriteFolderNames
import com.venera.desktop.gallery.data.desktopFavoriteFolderOf
import com.venera.desktop.gallery.data.desktopFavoriteFolderTabChoice
import com.venera.desktop.gallery.data.desktopFavoriteFolderTabRows
import com.venera.desktop.gallery.data.desktopFavoriteFoldersShown
import com.venera.desktop.gallery.ui.DesktopGalleryMorePhase
import com.venera.desktop.gallery.ui.DesktopGalleryRatingFilter
import com.venera.desktop.gallery.ui.DesktopGallerySort
import com.venera.desktop.gallery.ui.DesktopGalleryWallFooter
import com.venera.desktop.gallery.ui.DesktopWallCardModel
import com.venera.desktop.gallery.ui.DesktopWallDensity
import com.venera.desktop.gallery.ui.DesktopWallPage
import com.venera.desktop.gallery.ui.desktopFollowedArtistsRowReadout
import com.venera.desktop.gallery.ui.desktopGalleryWallFooter
import com.venera.desktop.gallery.ui.desktopReaderModeOf
import com.venera.desktop.gallery.ui.desktopReaderModeSeedNote
import com.venera.desktop.settings.DesktopHomeSkin
import com.venera.desktop.sourcemanage.DesktopSourceRowUi
import com.venera.engineprobe.EngineSession
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

/** 首页源测速、统计、历史与阅读统计状态分组。 */
internal class DesktopHomeStatusSlice(
    val homeLatencyByHost: SnapshotStateMap<String, Long>,
    val homeFailedHosts: SnapshotStateMap<String, Long>,
    val homeProbingHosts: SnapshotStateList<String>,
    val homeProbeStamp: MutableMap<String, Long>,
    val homeScope: CoroutineScope,
    val homeLines: List<Pair<String, String>>,
    val homeSourceRows: List<DesktopHomeSourceStatusRow>,
    val historySourceName: String?,
    val homeHistory: DesktopHomeHistorySnapshot,
    val homeStats: DesktopHomeStats,
    val galleryFavorites: List<GalleryFavorite>,
    val homeArtists: GalleryFollowedArtistsReadout,
    val readingStats: DesktopReadingStats,
    val homeSkinLight: Boolean,
)

@Composable
internal fun rememberDesktopHomeStatusSlice(
    ports: GalleryPorts,
    assetDir: File?,
    overrideDir: File?,
    sourceRows: List<DesktopSourceRowUi>,
    currentTab: VeneraNavTab,
    route: DesktopRoute,
    historyTick: Int,
): DesktopHomeStatusSlice {
    val homeLatencyByHost = remember { mutableStateMapOf<String, Long>() }
    val homeFailedHosts = remember { mutableStateMapOf<String, Long>() }
    val homeProbingHosts = remember { mutableStateListOf<String>() }
    val homeProbeStamp = remember { mutableMapOf<String, Long>() }
    val homeScope = rememberCoroutineScope()

    val homeEnabledHosts: List<Pair<String, String>> = remember(sourceRows, assetDir, overrideDir) {
        sourceRows.filter { it.enabled }.mapNotNull { row ->
            val script = assetDir?.let { dir ->
                DesktopSourceNames.scriptOf(dir, row.key, overrideDir)
            }
            val host = script
                ?.let { DesktopSourceNames.apiHostOf(it) }
                ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            (row.title.ifBlank { row.key }) to host
        }
    }
    val homeVisible = currentTab == VeneraNavTab.HOME && route == DesktopRoute.Tab
    val homeLines: List<Pair<String, String>> = remember(homeEnabledHosts, homeVisible) {
        if (!homeVisible) emptyList() else listOf("pixiv" to "www.pixiv.net", "yande.re" to "yande.re") + homeEnabledHosts
    }
    val homeSourceRows = homeLines.map { (name, host) ->
        val latency = homeLatencyByHost[host]
        DesktopHomeSourceStatusRow(
            name = name,
            host = host,
            probing = host in homeProbingHosts,
            latencyMs = latency,
            failed = latency == null && homeFailedHosts.containsKey(host),
        )
    }

    val historySourceName: String? = sourceRows.firstOrNull { it.key == DESKTOP_EXPLORE_SOURCE }?.title
        ?: if (sourceRows.isEmpty()) null else ""

    val homeHistory by produceState(initialValue = DesktopHomeHistorySnapshot.Loading, ports, historyTick) {
        value = DesktopHomeHistorySource.read()
    }
    val homeStats by produceState(initialValue = DesktopHomeStats.Empty, ports) {
        value = DesktopHomeStatsSource.read()
    }
    val galleryFollows by ports.follows.follows.collectAsState()
    val galleryFavorites by ports.favorites.favorites.collectAsState()
    val homeArtists = remember(galleryFollows, galleryFavorites) {
        desktopFollowedArtistsRowReadout(galleryFollows, galleryFavorites)
    }
    val readingStats by produceState(initialValue = DesktopReadingStats.Loading, route) {
        value = if (route is DesktopRoute.ReadingStats) DesktopReadingStatsSource.read() else DesktopReadingStats.Loading
    }
    val homeSkinLight = DesktopHomeSkin.mode.collectAsState().value == DesktopHomeSkin.LIGHT

    return DesktopHomeStatusSlice(
        homeLatencyByHost = homeLatencyByHost,
        homeFailedHosts = homeFailedHosts,
        homeProbingHosts = homeProbingHosts,
        homeProbeStamp = homeProbeStamp,
        homeScope = homeScope,
        homeLines = homeLines,
        homeSourceRows = homeSourceRows,
        historySourceName = historySourceName,
        homeHistory = homeHistory,
        homeStats = homeStats,
        galleryFavorites = galleryFavorites,
        homeArtists = homeArtists,
        readingStats = readingStats,
        homeSkinLight = homeSkinLight,
    )
}

/** 画廊主墙、Pixiv 发现与每日热门状态分组。 */
internal class DesktopWallAndPixivSlice(
    val galleryWallCardsState: MutableState<List<DesktopWallCardModel>>,
    val galleryWallBlockedState: MutableIntState,
    val galleryWallUnrenderableState: MutableState<String>,
    val galleryWallTickState: MutableIntState,
    val wallPageState: MutableState<DesktopWallPage?>,
    val galleryHomeDomainState: MutableState<GalleryHomeDomain>,
    val galleryRankingModeState: MutableState<PixivRankingMode>,
    val gallerySortState: MutableState<DesktopGallerySort>,
    val galleryWallQuality: GalleryPreviewQuality,
    val wallCache: SnapshotStateMap<String, DesktopWallPage>,
    val wallRefreshTickState: MutableIntState,
    val lastAppliedRefreshTickState: MutableIntState,
    val wallRefreshingState: MutableState<Boolean>,
    val pixivAuthState: PixivAuthState,
    val pixivAccountAvatarUrl: String?,
    val wallCacheKey: String,
    val cards: List<DesktopWallCardModel>,
    val galleryViewerQueue: List<DesktopWallCardModel>,
    val wallPhaseState: MutableState<DesktopGalleryMorePhase>,
    val wallMoreTickState: MutableIntState,
    val wallListAnchor: DesktopScrollAnchor,
    val wallStaggeredAnchor: DesktopScrollAnchor,
    val searchResultAnchor: DesktopScrollAnchor,
    val searchArtistAnchor: DesktopScrollAnchor,
    val exploreAnchor: DesktopScrollAnchor,
    val artistProfileAnchor: DesktopScrollAnchor,
    val pixivWorkViewerQueueState: MutableState<List<DesktopWallCardModel>>,
    val pixivWorkResumePageState: MutableIntState,
    val wallLayout: GalleryWallLayout,
    val wallFooter: DesktopGalleryWallFooter,
    val dailyGallery: DailyGalleryUiState,
    val pixivPreviewLegState: MutableState<PixivPreviewLeg>,
    val pixivPreviewCache: SnapshotStateMap<PixivRankingMode, PixivPreviewLeg>,
    val lastAppliedPreviewTickState: MutableIntState,
    val booruRatingFilterState: MutableState<DesktopGalleryRatingFilter>,
    val booruSiteFilterState: MutableState<GallerySite?>,
    val wallDensityState: MutableState<DesktopWallDensity>,
    val wallSelectedUids: SnapshotStateList<String>,
    val pixivDiscoverLegState: MutableState<PixivDiscoverLeg>,
    val pixivLocalFollowedState: MutableState<Set<Long>>,
    val tagDictionary: DesktopGalleryTagDictionary?,
    val dailyCore: GalleryDailyCore,
)

@Composable
internal fun rememberDesktopWallAndPixivSlice(
    ports: GalleryPorts,
    galleryPaths: PathProvider?,
    galleryPrefs: DesktopGalleryPreferences?,
    railPrefs: JsonKeyValueStore?,
    maskModeOf: () -> String,
    wallBlockAiOf: () -> Boolean,
    wallGuardOf: () -> GalleryContentGuard?,
    currentMaskMode: String,
): DesktopWallAndPixivSlice {
    val galleryWallCardsState = remember { mutableStateOf<List<DesktopWallCardModel>>(emptyList()) }
    val galleryWallBlockedState = remember { mutableIntStateOf(0) }
    val galleryWallUnrenderableState = remember { mutableStateOf("") }
    val galleryWallTickState = remember { mutableIntStateOf(0) }

    val wallPageState = remember { mutableStateOf<DesktopWallPage?>(null) }
    val galleryHomeDomainState = remember { mutableStateOf(GalleryHomeDomain.BOORU) }
    val galleryRankingModeState = remember { mutableStateOf(PixivRankingMode.DAILY) }
    val gallerySortState = remember { mutableStateOf(DesktopGallerySort.RECOMMENDED) }
    val galleryWallQuality = currentWallQuality(galleryPrefs)
    val wallCache = remember { mutableStateMapOf<String, DesktopWallPage>() }
    val wallRefreshTickState = remember { mutableIntStateOf(0) }
    val lastAppliedRefreshTickState = remember { mutableIntStateOf(-1) }
    val wallRefreshingState = remember { mutableStateOf(false) }

    val pixivAuthState by (
        ports.pixivAccount?.authState ?: constFlow(PixivAuthState.Anonymous)
        ).collectAsState()
    val pixivAccountAvatarUrl by produceState<String?>(null, pixivAuthState, ports.pixiv) {
        val signed = pixivAuthState as? PixivAuthState.SignedIn
        val uid = signed?.userId?.toLongOrNull()
        value = if (uid != null && ports.pixiv != null) {
            withContext(Dispatchers.IO) {
                ports.pixiv?.userHead(uid)?.getOrNull()?.avatarUrl
            }
        } else null
    }

    val galleryHomeDomain by galleryHomeDomainState
    val galleryRankingMode by galleryRankingModeState
    val gallerySort by gallerySortState
    val wallCacheKey = remember(galleryHomeDomain, galleryRankingMode, gallerySort, pixivAuthState, currentMaskMode) {
        val leg = if (galleryHomeDomain == GalleryHomeDomain.PIXIV) {
            if (pixivAuthState is PixivAuthState.SignedIn) "REC" else "RANK"
        } else {
            "-"
        }
        "${galleryHomeDomain}|${galleryRankingMode}|${gallerySort}|$leg|$currentMaskMode"
    }

    val wallPage by wallPageState
    val cards = wallPage?.cards.orEmpty()
    val galleryViewerQueue = remember(wallPage) { cards.filter { it.openable } }
    val wallPhaseState = remember { mutableStateOf<DesktopGalleryMorePhase>(DesktopGalleryMorePhase.Idle) }
    val wallMoreTickState = remember { mutableIntStateOf(0) }
    val wallPhase by wallPhaseState

    val wallListAnchor = remember { DesktopScrollAnchor() }
    val wallStaggeredAnchor = remember { DesktopScrollAnchor() }
    val searchResultAnchor = remember { DesktopScrollAnchor() }
    val searchArtistAnchor = remember { DesktopScrollAnchor() }
    val exploreAnchor = remember { DesktopScrollAnchor() }
    val artistProfileAnchor = remember { DesktopScrollAnchor() }

    val pixivWorkViewerQueueState = remember { mutableStateOf(emptyList<DesktopWallCardModel>()) }
    val pixivWorkResumePageState = remember { mutableIntStateOf(0) }
    val wallLayout by (galleryPrefs?.wallLayout ?: constFlow(GalleryWallLayout.Staggered)).collectAsState()
    val wallFooter = desktopGalleryWallFooter(
        batch = wallPage?.batch,
        onScreen = cards.size,
        phase = wallPhase,
        unit = "张",
    )

    val dailyGallery = remember(railPrefs) {
        DailyGalleryUiState(railPrefs?.getBoolean("pref_daily_home_pixiv", false) ?: false)
    }
    val pixivPreviewLegState = remember {
        mutableStateOf(PixivPreviewLeg(emptyList(), emptyMap(), emptyMap()))
    }
    val pixivPreviewCache = remember { mutableStateMapOf<PixivRankingMode, PixivPreviewLeg>() }
    val lastAppliedPreviewTickState = remember { mutableIntStateOf(-1) }
    val booruRatingFilterState = remember { mutableStateOf(DesktopGalleryRatingFilter.All) }
    val booruSiteFilterState = remember { mutableStateOf<GallerySite?>(null) }
    val wallDensityState = remember(railPrefs) {
        mutableStateOf(
            DesktopWallDensity.entries.getOrElse(
                railPrefs?.getInt("pref_wall_density", 1) ?: 1,
            ) { DesktopWallDensity.Standard },
        )
    }
    val wallSelectedUids = remember { mutableStateListOf<String>() }
    val pixivDiscoverLegState = remember {
        mutableStateOf(PixivDiscoverLeg(emptyList(), emptySet(), emptyList(), emptyMap(), ready = false))
    }
    val pixivLocalFollowedState = remember { mutableStateOf(emptySet<Long>()) }
    val tagDictionary = remember(galleryPaths) { galleryPaths?.let { DesktopGalleryTagDictionary(it) } }
    val dailyCore = remember(ports) {
        GalleryDailyCore(
            readSnapshot = {
                val paths = galleryPaths
                if (paths == null) null else DesktopGalleryDailySnapshotStore.read(paths)
            },
            writeSnapshot = { snapshot ->
                val paths = galleryPaths
                if (paths != null) DesktopGalleryDailySnapshotStore.write(paths, snapshot)
            },
        ).also { core ->
            core.maskMode = maskModeOf()
            core.blockAi = wallBlockAiOf()
            val guard = wallGuardOf()
            if (guard != null) {
                core.blockedRuleOf = { post -> guard.blockedGalleryRule(author = post.author, tags = post.tagList)?.pattern }
            }
        }
    }

    return DesktopWallAndPixivSlice(
        galleryWallCardsState = galleryWallCardsState,
        galleryWallBlockedState = galleryWallBlockedState,
        galleryWallUnrenderableState = galleryWallUnrenderableState,
        galleryWallTickState = galleryWallTickState,
        wallPageState = wallPageState,
        galleryHomeDomainState = galleryHomeDomainState,
        galleryRankingModeState = galleryRankingModeState,
        gallerySortState = gallerySortState,
        galleryWallQuality = galleryWallQuality,
        wallCache = wallCache,
        wallRefreshTickState = wallRefreshTickState,
        lastAppliedRefreshTickState = lastAppliedRefreshTickState,
        wallRefreshingState = wallRefreshingState,
        pixivAuthState = pixivAuthState,
        pixivAccountAvatarUrl = pixivAccountAvatarUrl,
        wallCacheKey = wallCacheKey,
        cards = cards,
        galleryViewerQueue = galleryViewerQueue,
        wallPhaseState = wallPhaseState,
        wallMoreTickState = wallMoreTickState,
        wallListAnchor = wallListAnchor,
        wallStaggeredAnchor = wallStaggeredAnchor,
        searchResultAnchor = searchResultAnchor,
        searchArtistAnchor = searchArtistAnchor,
        exploreAnchor = exploreAnchor,
        artistProfileAnchor = artistProfileAnchor,
        pixivWorkViewerQueueState = pixivWorkViewerQueueState,
        pixivWorkResumePageState = pixivWorkResumePageState,
        wallLayout = wallLayout,
        wallFooter = wallFooter,
        dailyGallery = dailyGallery,
        pixivPreviewLegState = pixivPreviewLegState,
        pixivPreviewCache = pixivPreviewCache,
        lastAppliedPreviewTickState = lastAppliedPreviewTickState,
        booruRatingFilterState = booruRatingFilterState,
        booruSiteFilterState = booruSiteFilterState,
        wallDensityState = wallDensityState,
        wallSelectedUids = wallSelectedUids,
        pixivDiscoverLegState = pixivDiscoverLegState,
        pixivLocalFollowedState = pixivLocalFollowedState,
        tagDictionary = tagDictionary,
        dailyCore = dailyCore,
    )
}

/** 收藏夹列表派生、阅读器模式与 UX/业务端口组合状态分组。 */
internal class DesktopFavoriteAndReaderSlice(
    val favoritesState: MutableState<List<FavoriteRecord>>,
    val favoritesLoadedState: MutableState<Boolean>,
    val favoritesNoteState: MutableState<String?>,
    val favoriteFolderPickedState: MutableState<String?>,
    val favoritesTabPickedState: MutableState<String?>,
    val favoritesSourceKeyState: MutableState<String>,
    val favoriteFoldersState: MutableState<List<String>>,
    val netFavUi: NetFavUiState,
    val favoriteFolderNames: List<String>,
    val favoriteFolderComicId: String?,
    val favoriteFolderCurrent: String?,
    val favoriteFolderSelected: String,
    val favoritesTabSelected: String?,
    val favoritesShown: List<FavoriteRecord>,
    val favoritesFolderTabs: List<DesktopFavoriteFolderTab>,
    val favoritesCountNote: String,
    val favoritesFilterNote: String?,
    val readerModeChoiceState: MutableState<DesktopReaderMode?>,
    val readerMode: DesktopReaderMode,
    val readerModeNote: String?,
    val desktopUx: DesktopUxPlatformServices,
    val businessHandle: DesktopBusinessPorts.Handle?,
)

@Composable
internal fun rememberDesktopFavoriteAndReaderSlice(
    route: DesktopRoute,
    galleryGuard: DesktopGalleryContentGuard?,
    maskModeOf: () -> String,
    blockedByUserOf: (sourceKey: String, title: String, author: String, tags: List<String>) -> Boolean,
    currentMaskMode: String,
    engine: EngineSession?,
    assetDir: File?,
    overrideDir: File?,
    galleryPaths: PathProvider?,
    downloadsRoot: File?,
    readerPrefs: DesktopComicReaderPreferences?,
    readerModeSeed: DesktopReaderMode?,
): DesktopFavoriteAndReaderSlice {
    val favoritesState = remember { mutableStateOf<List<FavoriteRecord>>(emptyList()) }
    val favoritesLoadedState = remember { mutableStateOf(false) }
    val favoritesNoteState = remember { mutableStateOf<String?>(null) }
    val favoriteFolderPickedState = remember { mutableStateOf<String?>(null) }
    val favoritesTabPickedState = remember { mutableStateOf<String?>(null) }
    val favoritesSourceKeyState = androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf(com.venera.desktop.favorites.FAVORITES_SOURCE_LOCAL)
    }
    val favoriteFoldersState = remember { mutableStateOf<List<String>>(emptyList()) }
    val netFavUi = remember { NetFavUiState() }

    val favorites by favoritesState
    val favoritesLoaded by favoritesLoadedState
    val favoriteFolders by favoriteFoldersState
    val favoriteFolderPicked by favoriteFolderPickedState
    val favoritesTabPicked by favoritesTabPickedState

    val favoriteFolderComicId = (route as? DesktopRoute.Detail)?.comicId
    val favoriteFolderNames = (favoriteFolders + desktopFavoriteFolderNames(favorites)).distinct()
    val favoriteFolderCurrent = desktopFavoriteFolderOf(
        favoriteFolderComicId ?: "", favorites, favoriteFolderComicId != null && favoritesLoaded,
    )
    val favoriteFolderSelected = desktopFavoriteFolderChoice(
        favoriteFolderPicked, favoriteFolderCurrent, favoriteFolderNames,
    )
    val favoritesTabSelected = desktopFavoriteFolderTabChoice(favoritesTabPicked, favoriteFolderNames)
    val favoritesShown = desktopFavoriteFoldersShown(favoritesTabSelected, favorites)
    val favoritesFolderTabs = desktopFavoriteFolderTabRows(
        names = favoriteFolderNames,
        selected = favoritesTabSelected,
        favorites = favorites,
        countsKnown = favoritesLoaded,
    )
    val favoritesCountNote = desktopFavoriteFolderCountNote(
        folder = favoritesTabSelected,
        total = favorites.size,
        shown = favoritesShown.size,
        countsKnown = favoritesLoaded,
    )
    val favoritesFilterNote = desktopFavoriteFolderFilterNote(
        folder = favoritesTabSelected,
        total = favorites.size,
        shown = favoritesShown.size,
    )

    val noPrefsReaderMode = remember { MutableStateFlow(DesktopReaderMode.VERTICAL_CONTINUOUS) }
    val storedReaderMode by (readerPrefs?.readingMode ?: noPrefsReaderMode).collectAsState()
    val readerModeChoiceState = remember { mutableStateOf<DesktopReaderMode?>(null) }
    val readerModeChoice by readerModeChoiceState
    val readerMode = readerModeChoice ?: desktopReaderModeOf(readerModeSeed, storedReaderMode)
    val readerModeNote = if (readerModeChoice == null) {
        desktopReaderModeSeedNote(readerModeSeed, storedReaderMode)
    } else {
        null
    }

    val desktopUx = remember(maskModeOf, blockedByUserOf, currentMaskMode) {
        DesktopUxPlatformServices(maskMode = maskModeOf, blockedByUser = blockedByUserOf)
    }
    val latestDownloadsRoot by rememberUpdatedState(downloadsRoot)
    val businessHandle = remember(desktopUx, engine, assetDir, overrideDir, galleryPaths, galleryGuard) {
        val paths = galleryPaths
        val guard = galleryGuard
        val dir = assetDir
        if (paths == null || guard == null || dir == null) {
            null
        } else {
            DesktopBusinessPorts.Handle(
                engine = engine,
                assetDir = dir,
                overrideDir = overrideDir,
                paths = paths,
                downloadsRoot = { latestDownloadsRoot },
                coverMask = { sourceKey, title, author, tags, comicId ->
                    desktopUx.coverMaskStateOf(sourceKey, title, author, tags, comicId, "")
                },
                rules = guard,
                guardMaskMode = guard.nsfwMaskMode,
            )
        }
    }

    return DesktopFavoriteAndReaderSlice(
        favoritesState = favoritesState,
        favoritesLoadedState = favoritesLoadedState,
        favoritesNoteState = favoritesNoteState,
        favoriteFolderPickedState = favoriteFolderPickedState,
        favoritesTabPickedState = favoritesTabPickedState,
        favoritesSourceKeyState = favoritesSourceKeyState,
        favoriteFoldersState = favoriteFoldersState,
        netFavUi = netFavUi,
        favoriteFolderNames = favoriteFolderNames,
        favoriteFolderComicId = favoriteFolderComicId,
        favoriteFolderCurrent = favoriteFolderCurrent,
        favoriteFolderSelected = favoriteFolderSelected,
        favoritesTabSelected = favoritesTabSelected,
        favoritesShown = favoritesShown,
        favoritesFolderTabs = favoritesFolderTabs,
        favoritesCountNote = favoritesCountNote,
        favoritesFilterNote = favoritesFilterNote,
        readerModeChoiceState = readerModeChoiceState,
        readerMode = readerMode,
        readerModeNote = readerModeNote,
        desktopUx = desktopUx,
        businessHandle = businessHandle,
    )
}
