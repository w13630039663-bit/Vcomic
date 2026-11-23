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
internal fun DesktopLargeScreenMiscEffects(
    ports: GalleryPorts,
    engine: com.venera.engineprobe.EngineSession?,
    downloadsRoot: java.io.File?,
    galleryPaths: com.venera.compose.data.platform.PathProvider?,
    galleryPrefs: com.venera.desktop.gallery.data.DesktopGalleryPreferences?,
    queueConcurrency: Int,
    queueTasks: List<com.venera.desktop.gallery.data.DesktopComicQueueTask>,
    pixivCallback: DesktopPixivCallback?,
    browseClearUi: BrowseClearUiState,
    detailUi: DetailAndReaderUiState,
    filesUi: FilesAuxUiState,
    detailCoreCache: MutableMap<String, Pair<CoroutineScope, ComicDetailCore>>,
    homeLines: List<Pair<String, String>>,
    homeLatencyByHost: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Long>,
    homeProbeStamp: MutableMap<String, Long>,
    homeProbingHosts: androidx.compose.runtime.snapshots.SnapshotStateList<String>,
    homeFailedHosts: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Long>,
    homeScope: CoroutineScope,
    performSourceUpdateCheck: suspend (Boolean) -> Unit,
    readFavoriteFolders: (suspend () -> List<String>)?,
    openFirstGalleryPost: Boolean,
    wallPage: DesktopWallPage?,
    routeState: MutableState<DesktopRoute>,
    savedUiState: MutableState<SavedUiState>,
    favoriteFoldersState: MutableState<List<String>>,
) {
    var route by routeState
    var savedUi by savedUiState
    var favoriteFolders by favoriteFoldersState
    LaunchedEffect(pixivCallback?.id, ports.pixivAccount) {
        val callback = pixivCallback ?: return@LaunchedEffect
        val account = ports.pixivAccount ?: return@LaunchedEffect
        val result = account.completeLogin(callback.url)
        route = if (result.isSuccess) DesktopRoute.PixivAccount else DesktopRoute.PixivLogin
    }

    LaunchedEffect(route) {
        val picked = when (route) {
            DesktopRoute.GalleryBrowseHistory -> DesktopGallerySavedView.HISTORY
            DesktopRoute.GalleryFavorites -> DesktopGallerySavedView.FAVORITES
            else -> return@LaunchedEffect
        }
        savedUi = savedUi.copy(view = picked)
    }

    // 在 Dispatchers.IO 获取浏览历史存储实例；galleryPaths 为 null 时保持 browseRows 为 null 并通过 browseNote 说明原因。
    LaunchedEffect(route, browseClearUi.browseTick) {
        if (route !is DesktopRoute.GalleryBrowseHistory && route !is DesktopRoute.GalleryFavorites) {
            // 离开那一屏就把上一趟「回原卡」的果交回去：出去转一圈再进来，屏上不该还挂着别人的成因。
            browseClearUi.browseNote = null
            return@LaunchedEffect
        }
        val paths = galleryPaths
        if (paths == null) {
            browseClearUi.browseStore = null
            browseClearUi.browseRows = null
            browseClearUi.browseNote = "这台机器还没定下数据根 ⇒ 那份档读不到"
            return@LaunchedEffect
        }
        val got = withContext(Dispatchers.IO) {
            runCatching { DesktopGalleryBrowseHistory.getInstance(paths) }
        }
        got.fold(
            onSuccess = { store ->
                browseClearUi.browseStore = store
                browseClearUi.browseRows = store.rows.value
                browseClearUi.browseNote = store.notice.value
            },
            onFailure = { t ->
                browseClearUi.browseStore = null
                browseClearUi.browseRows = null
                browseClearUi.browseNote = "那份档读不开：${t.javaClass.simpleName} ${t.message?.take(160) ?: ""}".trim()
            },
        )
    }
    DisposableEffect(detailCoreCache) {
        onDispose {
            detailCoreCache.values.forEach { it.first.cancel() }
            detailCoreCache.clear()
        }
    }
    LaunchedEffect(Unit) {
        com.venera.desktop.platform.DesktopNotifier.install()
        kotlinx.coroutines.delay(8_000)
        runCatching { performSourceUpdateCheck(true) }
            .onFailure {
                println("D_源自检 没跑成：${it.javaClass.simpleName} ${it.message?.take(160)}")
            }
    }
    LaunchedEffect(homeLines) {
        if (homeLines.isEmpty()) return@LaunchedEffect
        val now = System.currentTimeMillis()
        val due = homeLines.filter { (host) -> now - (homeProbeStamp[host] ?: 0L) > 60_000L }
        if (due.isEmpty()) return@LaunchedEffect
        due.forEach { (_, host) -> homeProbeStamp[host] = now }
        homeProbingHosts.addAll(due.map { it.second }.filterNot { it in homeProbingHosts })
        due.forEach { (_, host) ->
            homeScope.launch {
                val ms = com.venera.desktop.platform.DesktopSourceLatencyProbe.probe(host)
                homeProbingHosts.removeAll(listOf(host))
                if (ms != null) {
                    homeLatencyByHost[host] = ms
                } else {
                    homeFailedHosts[host] = System.currentTimeMillis()
                }
            }
        }
    }
    DisposableEffect(engine, downloadsRoot, queueConcurrency) {
        val root = downloadsRoot
        // 泵的作用域随这一轮 effect 新建：之前共用一颗 remember 的 scope，onDispose 取消后
        // 新一轮 launch 落在已取消的作用域里，泵永远起不来（引擎一切换下载就停摆到重启）。
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        if (root != null) desktopComicStartQueuePump(scope, engine, root, queueConcurrency)
        onDispose { scope.cancel() }
    }
    LaunchedEffect(queueTasks) {
        val busy = queueTasks.any {
            it.status == com.venera.desktop.gallery.data.DesktopComicQueueStatus.PENDING ||
                it.status == com.venera.desktop.gallery.data.DesktopComicQueueStatus.RUNNING
        }
        if (busy) filesUi.queueWasBusy = true
        if (filesUi.queueWasBusy && !busy && queueTasks.any {
                it.status == com.venera.desktop.gallery.data.DesktopComicQueueStatus.DONE
            }) {
            filesUi.queueWasBusy = false
            DesktopPetCueBus.emit(DesktopPetCue.DOWNLOAD_ALL_DONE)
        }
    }
    LaunchedEffect(route, filesUi.imageScanTick, downloadsRoot, galleryPaths, galleryPrefs?.downloadDir?.value) {
        if (route is DesktopRoute.Files || filesUi.imageScanTick > 0) {
            filesUi.imageItems = withContext(Dispatchers.IO) {
                com.venera.desktop.downloads.scanDesktopLocalImages(
                    galleryPaths,
                    galleryPrefs?.downloadDir?.value,
                )
            }
        }
    }
    LaunchedEffect(filesUi.queueExpandedTaskId, queueTasks, downloadsRoot) {
        val task = queueTasks.firstOrNull { it.taskId == filesUi.queueExpandedTaskId }
        if (task == null) {
            filesUi.queueDetailFiles = emptyList()
            filesUi.queueDetailLoading = false
            return@LaunchedEffect
        }
        filesUi.queueDetailLoading = true
        filesUi.queueDetailFiles = withContext(Dispatchers.IO) {
            val root = downloadsRoot ?: return@withContext emptyList()
            val dir = com.venera.desktop.gallery.data.desktopComicQueueDir(root, task)
            dir.listFiles()?.filter { it.isFile }?.map { it.name to it.length() }?.sortedBy { it.first }
                ?: emptyList()
        }
        filesUi.queueDetailLoading = false
    }
    LaunchedEffect(detailUi.favoriteTick) {
        val read = readFavoriteFolders ?: return@LaunchedEffect
        favoriteFolders = withContext(Dispatchers.IO) { runCatching { read() } }.getOrDefault(emptyList())
    }
    var galleryViewerSeedConsumed by remember { mutableStateOf(false) }
    LaunchedEffect(wallPage, openFirstGalleryPost) {
        if (!openFirstGalleryPost || galleryViewerSeedConsumed) return@LaunchedEffect
        val card = wallPage?.cards?.firstOrNull { it.openable } ?: return@LaunchedEffect
        galleryViewerSeedConsumed = true
        route = DesktopRoute.GalleryPostViewer(card, from = DesktopRoute.Tab)
    }
}
