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
import androidx.compose.runtime.MutableLongState

@Composable
internal fun DesktopComicAndFilesEffects(
    engine: com.venera.engineprobe.EngineSession?,
    downloadsRoot: java.io.File?,
    comicCache: com.venera.desktop.cache.DesktopComicCache?,
    queueTasks: List<com.venera.desktop.gallery.data.DesktopComicQueueTask>,
    exploreSections: List<com.venera.engineprobe.EngineSession.Section>,
    homeHistory: DesktopHomeHistorySnapshot,
    historySourceName: String?,
    favoriteFolderComicId: String?,
    favoriteFolderNames: List<String>,
    favoriteFolderCurrent: String?,
    favoriteFolderSelected: String,
    favoritesTabSelected: String?,
    favoritesShown: List<com.venera.compose.data.db.FavoriteRecord>,
    favoritesFolderTabs: List<com.venera.desktop.gallery.data.DesktopFavoriteFolderTab>,
    detailUi: DetailAndReaderUiState,
    sourceUi: SourceManageUiState,
    filesUi: FilesAuxUiState,
    readFavorites: (suspend () -> Pair<List<com.venera.compose.data.db.FavoriteRecord>, Boolean>)?,
    readFavoriteFolders: (suspend () -> List<String>)?,
    isFavoriteOf: (suspend (String) -> Boolean)?,
    readHistoryRow: (suspend (String) -> com.venera.compose.data.db.HistoryRecord?)?,
    writeHistory: (suspend (com.venera.compose.data.db.HistoryRecord) -> Unit)?,
    readSources: (() -> List<com.venera.desktop.sourcemanage.DesktopSourceRowUi>)?,
    readSourceText: ((String) -> String?)?,
    loginWithCookies: (suspend (String, List<String>) -> String)?,
    loginWithPassword: (suspend (String, String, String) -> String)?,
    loginWithWebview: (suspend (String) -> String)?,
    routeState: MutableState<DesktopRoute>,
    currentTabState: MutableState<VeneraNavTab>,
    commentsTickState: MutableIntState,
    readerPagesState: MutableState<List<com.venera.desktop.gallery.data.DesktopComicPage>>,
    favoritesState: MutableState<List<com.venera.compose.data.db.FavoriteRecord>>,
    favoritesLoadedState: MutableState<Boolean>,
    favoritesNoteState: MutableState<String?>,
    favoriteFolderPickedState: MutableState<String?>,
    sourceTickState: MutableIntState,
    filesSegmentState: MutableState<com.venera.desktop.downloads.DesktopFilesSegment>,
    downloadsTickState: MutableIntState,
    localComicImportTickState: MutableState<Int>,
    localComicScanState: MutableState<DesktopLocalComicLibraryScan?>,
    lastPageTurnAtState: MutableLongState,
) {
    var route by routeState
    var currentTab by currentTabState
    var commentsTick by commentsTickState
    var readerPages by readerPagesState
    var favorites by favoritesState
    var favoritesLoaded by favoritesLoadedState
    var favoritesNote by favoritesNoteState
    var favoriteFolderPicked by favoriteFolderPickedState
    var sourceTick by sourceTickState
    var filesSegment by filesSegmentState
    var downloadsTick by downloadsTickState
    var localComicImportTick by localComicImportTickState
    var localComicScan by localComicScanState
    var lastPageTurnAt by lastPageTurnAtState
    LaunchedEffect(route, commentsTick) {
        val r = route
        if (r !is DesktopRoute.ComicComments) {
            // 离开评论屏就把状态交回起手 —— 留着上一本的评论，下一本进来看见的是一屏假账。
            detailUi.commentsView = desktopCommentsInitialView()
            return@LaunchedEffect
        }
        val e = engine
        if (e == null) {
            detailUi.commentsView = desktopCommentsInitialView().copy(isLoading = false, loaded = true, error = "引擎没起来：读不到评论。")
            println(desktopCommentsScreenReading(r.comicId, detailUi.commentsView))
            return@LaunchedEffect
        }
        detailUi.commentsView = desktopCommentsLoading(detailUi.commentsView)
        // commentsTick=0 走第一页；每扳一次往下一页（对齐对岸 requestedPage = page + 1）。
        val requestedPage = if (commentsTick == 0) 1 else detailUi.commentsView.page + 1
        if (commentsTick == 0) {
            val capsResult = desktopEngineCall(e) {
                e.load(r.sourceKey)
                e.commentCapabilities()
            }
            val caps = capsResult.getOrNull()
            detailUi.commentsView = when {
                capsResult.isFailure || caps == null -> {
                    val t = capsResult.exceptionOrNull()
                    desktopCommentsFailed(
                        detailUi.commentsView,
                        "问不到这一颗源支不支持评论：${t?.javaClass?.simpleName ?: "?"} " + (t?.message?.take(160) ?: ""),
                    )
                }
                !caps.canLoad -> detailUi.commentsView.copy(isLoading = false, canLoad = false, loaded = true)
                else -> detailUi.commentsView.copy(canLoad = true)
            }
            println(desktopCommentsScreenReading(r.comicId, detailUi.commentsView))
            // 不支持（或问不到）都到此为止：那一屏念的是屏上那句实话，不许再发评论那一跳。
            if (!detailUi.commentsView.canLoad) return@LaunchedEffect
        }
        val pageResult = desktopEngineCall(e) {
            e.load(r.sourceKey)
            e.comments(r.comicId, r.subId, requestedPage, null)
        }
        detailUi.commentsView = pageResult.fold(
            onSuccess = { p ->
                desktopCommentsReceived(
                    current = detailUi.commentsView,
                    comments = p.comments,
                    requestedPage = requestedPage,
                    maximumPage = p.maxPage,
                )
            },
            onFailure = { t ->
                // 原文只进日志与面板详情；屏上念归档短句。
                println("D_评论 失败 ${t.javaClass.simpleName}: ${t.message?.take(160) ?: ""}")
                com.venera.desktop.DesktopErrorBus.report(
                    category = "漫画",
                    headline = "章节评论读取失败",
                    humanReason = "这一章的评论区没取回来，已显示的评论与页码都没丢；可稍后重试或切到独立评论屏。",
                    detail = "${r.sourceKey}: ${t.javaClass.simpleName}: ${t.message.orEmpty()}",
                    dedupKey = "comic:comments_read:${r.sourceKey}",
                )
                desktopCommentsFailed(
                    detailUi.commentsView,
                    com.venera.desktop.platform.desktopFailureNoteOf(t),
                )
            },
        )
        println(desktopCommentsScreenReading(r.comicId, detailUi.commentsView))
    }


    // 详情取数按 route 触发（从阅读器返回不重拉），失败原因写入 detailNote 避免空白页。
    LaunchedEffect(route) {
        val r = route
        if (r !is DesktopRoute.Detail) {
            // 离开详情（进阅读器 / 回底栏 / 去别的二级页）就把批量弹窗关掉：那扇窗挂在详情这一层，
            // 状态留在壳里的话回到详情时会看见一扇"没人叫它开"的弹窗。
            detailUi.showBulkDownload = false
            // 挑过的那一格也跟着交回去。**这一格不是洁癖** —— `favoriteFolderPicked`
            // 决定下一次写落到哪一格，留着它的话从 A 本挑过「追更」再进 B 本，屏上那一排把光标画在
            // 「追更」而那句念「这一本会落到 追更」，而 B 本从头到尾没被用户挑过（静默搬人东西）。
            favoriteFolderPicked = null
            return@LaunchedEffect
        }
        // 换一本（含从阅读器回到详情）也重落一次：那一排的光标该跟着"这一本现在那一格"走。
        favoriteFolderPicked = null
        val e = engine
        if (e == null) {
            detailUi.detailNote = "引擎没起来：取不到这一本的章节。"
            return@LaunchedEffect
        }
        // 若内存已有当前漫画详情（例如从阅读器返回），保持持久化数据，不重新发起网络刷新
        if (detailUi.detailInfo != null && (detailUi.detailInfo?.title == r.title || r.title.isBlank())) {
            return@LaunchedEffect
        }
        // 尝试从磁盘持久化缓存读取
        val cached = withContext(Dispatchers.IO) { comicCache?.readDetails(r.sourceKey, r.comicId) }
        if (cached != null) {
            detailUi.detailInfo = cached
            detailUi.detailNote = null
            return@LaunchedEffect
        }
        detailUi.detailInfo = null
        detailUi.detailNote = null
        detailUi.detailNote = desktopEngineCall(e) {
            e.load(r.sourceKey)
            e.details(r.comicId)
        }.fold(
            onSuccess = { info ->
                detailUi.detailInfo = info
                withContext(Dispatchers.IO) { comicCache?.writeDetails(r.sourceKey, r.comicId, info) }
                null
            },
            onFailure = { t ->
                // 屏上念归档短句 + 可执行提示（TLS 被阻断会念「网络受阻 · 需配置代理后重试」），
                // 原文只进日志 —— 与 `DesktopErrorCopy` 头注那条口径一致。
                println("D_详情 失败 ${t.javaClass.simpleName}: ${t.message?.take(160) ?: ""}")
                com.venera.desktop.platform.desktopFailureNoteOf(t)
            },
        )
    }


    // 详情页收藏状态独立查询本地库，不与源站详情共用引擎锁；键含 favoriteTick 以便写后校正。
    LaunchedEffect(route, detailUi.favoriteTick) {
        val r = route
        if (r !is DesktopRoute.Detail) return@LaunchedEffect
        detailUi.detailHistoryOutcome = null
        val ask = isFavoriteOf
        if (ask == null) {
            detailUi.detailFavorited = null
            return@LaunchedEffect
        }
        detailUi.detailFavorited = null
        val got = withContext(Dispatchers.IO) { runCatching { ask(r.comicId) } }
        // 查失败念成"读不回来"而不是"没收过"：后者会让用户按下去写一条重复的。
        detailUi.detailFavorited = got.getOrNull()
    }


    // 详情页阅读进度独立查询本地库单条记录（getHistory），不等待异步刷新的全表流。
    LaunchedEffect(route, detailUi.favoriteTick) {
        val r = route
        if (r !is DesktopRoute.Detail) return@LaunchedEffect
        val ask = readHistoryRow ?: return@LaunchedEffect
        detailUi.detailHistoryOutcome = null
        detailUi.detailHistoryOutcome = withContext(Dispatchers.IO) { runCatching { ask(r.comicId) } }
        // 取证读数：这一本"读到哪儿"那一格是**读到了**、**库里没这一本**、还是**库没读到**
        // —— 第三档在屏上的形状是"那一枚胶囊不见了"，只有这一行分得出。
        println(desktopComicHistoryProgressLine(r.comicId, detailUi.detailHistoryOutcome!!))
    }


    // 阅读器取页优先检查本地已下载树：整话齐全不问源站，缺页时仅拉页键并按页复用本地文件；仅在进屏时读盘一次。
    LaunchedEffect(route) {
        val r = route
        if (r !is DesktopRoute.Reader) return@LaunchedEffect
        val root = downloadsRoot
        // 读盘（含每页头部那判）在 IO 线程上，且与"问引擎"分开：塌了不影响另一路。
        val local: DesktopComicLocalChapter? =
            if (root == null) null else withContext(Dispatchers.IO) {
                runCatching {
                    // 源与号先归一到盘上那一形（净化）再去认话，见那颗件自己的 KDoc。
                    desktopComicOfflineLocalChapter(root, r.sourceKey, r.comicId, r.episode.id)
                }.getOrNull()
            }
        val shouldAsk = desktopComicOfflineShouldAskEngine(local)
        val e = engine

        // 源站那一趟的三档各是一条分支（`DesktopComicSourceOutcome`）。
        // ⚠️ 「想问而引擎没起来」**不清掉页表**：盘上有的那几页照样翻开，那一档由屏顶那句念成因。
        //    从前这里是 `e == null` 就整屏失败 + `readerPages = emptyList()`（24 MB 一并清空）。
        var imageKeys: List<String> = emptyList()
        val outcome: DesktopComicSourceOutcome = when {
            // 本机整话已齐 ⇒ 一颗源站都不问（引擎没起来也无所谓，断网翻得开就是这一档）。
            !shouldAsk -> DesktopComicSourceOutcome.NotNeeded
            e == null -> DesktopComicSourceOutcome.Unreachable("引擎没起来")
            else -> {
                // 页表也持久化 —— 先读缓存，引擎那趟失败时退回缓存（图仍走 Coil 盘缓存）。
                val cachedKeys = withContext(Dispatchers.IO) {
                    comicCache?.readPages(r.sourceKey, r.comicId, r.episode.id)
                }
                val got = desktopEngineCall(e) {
                    e.load(r.sourceKey)
                    // 页表必须包装为 DesktopComicPage 对象供专用 Fetcher 识别，不可传缓存键字符串（会被当成无效 URL）。
                    e.pages(r.comicId, r.episode)
                }
                got.fold(
                    onSuccess = { keys ->
                        imageKeys = keys
                        if (keys.isNotEmpty()) {
                            withContext(Dispatchers.IO) {
                                comicCache?.writePages(r.sourceKey, r.comicId, r.episode.id, keys)
                            }
                        }
                        DesktopComicSourceOutcome.Delivered(keys.size)
                    },
                    onFailure = { t ->
                        // 引擎这趟没成而缓存里有页表 ⇒ 退回缓存（至少能翻，图看 Coil 盘缓存）。
                        if (!cachedKeys.isNullOrEmpty()) {
                            imageKeys = cachedKeys
                            DesktopComicSourceOutcome.Delivered(cachedKeys.size)
                        } else {
                            // 无缓存可退：这一话真的打不开，屏上念归档短句，原文进日志与面板。
                            println("D_取页 失败 ${t.javaClass.simpleName}: ${t.message?.take(160) ?: ""}")
                            com.venera.desktop.DesktopErrorBus.report(
                                category = "漫画",
                                headline = "章节页表读取失败",
                                humanReason = "这一话的页表没取回来，且本机没有缓存；换一话或稍后重试。",
                                detail = "${r.sourceKey} ${r.comicId}: ${t.javaClass.simpleName}: ${t.message.orEmpty()}",
                                dedupKey = "comic:pages_read:${r.sourceKey}",
                            )
                            DesktopComicSourceOutcome.Unreachable(
                                com.venera.desktop.platform.desktopFailureNoteOf(t),
                            )
                        }
                    },
                )
            }
        }

        val served = desktopComicReaderPages(
            sourceKey = r.sourceKey,
            comicId = r.comicId,
            episodeId = r.episode.id,
            imageKeys = imageKeys,
            localDir = local?.dir,
            localPages = local?.writtenPages ?: 0,
            // 纯本机那一趟里引擎可以没有（`null`），那时页表里每一页都带 `localDir` ⇒ 仍算可取。
            engine = e,
        )
        readerPages = served
        detailUi.readerNote = desktopComicOfflineReadingNote(
            local = local,
            outcome = outcome,
            servedPages = served.size,
        )
        println(
            desktopComicOfflineReadingReading(
                episodeId = r.episode.id,
                local = local,
                outcome = outcome,
                servedPages = served.size,
                pagesWithLocalDir = served.count { it.localDir != null },
            ),
        )
    }


    // 收藏取数等待 favoritesFlow 首个非空值（超时回退当前值），同时服务收藏屏与详情页文件夹栏，键含 favoriteTick。
    LaunchedEffect(currentTab, route, favoritesLoaded, detailUi.favoriteTick) {
        if (currentTab != VeneraNavTab.FAVORITES && route !is DesktopRoute.Detail) return@LaunchedEffect
        val read = readFavorites ?: return@LaunchedEffect
        val got = withContext(Dispatchers.IO) { runCatching { read() } }
        got.fold(
            onSuccess = { (list, loaded) ->
                favorites = list
                favoritesLoaded = loaded
                favoritesNote = null
                // 本地收藏读回为空才报，读不到/没读回不误报。
                if (loaded && list.isEmpty() && currentTab == VeneraNavTab.FAVORITES) {
                    DesktopPetCueBus.emit(DesktopPetCue.COLLECTION_EMPTY)
                }
            },
            onFailure = { t ->
                favoritesLoaded = true
                println("D_收藏 读失败 ${t.javaClass.simpleName}: ${t.message?.take(160) ?: ""}")
                com.venera.desktop.DesktopErrorBus.report(
                    category = "漫画",
                    headline = "本地收藏读取失败",
                    humanReason = "收藏夹这一列没读出来，收藏本身仍在本地库里；可重启或稍后重试。",
                    detail = "${t.javaClass.simpleName}: ${t.message.orEmpty()}",
                    dedupKey = "comic:favorites_read",
                )
                favoritesNote = com.venera.desktop.platform.desktopFailureNoteOf(t)
            },
        )
    }


    // 首次进搜索页恢复上次选源目标（默认全网聚合），并在源管理启停变化时同步剔除停用源。
    /**
     * 全局返回：先问 `desktopShellBack` 那张表；表说"到头了"（一级页）而此刻正停在
     * **由详情页 tag 跳进来的搜索**上，就弹回那本详情。
     */
    LaunchedEffect(currentTab, route, sourceTick) {
        // 历史屏与搜索屏同样依赖源清单（用于显示名与源选择器）；本地读取脚本与 JSON，不占用引擎锁。
        if (currentTab != VeneraNavTab.FAVORITES &&
            currentTab != VeneraNavTab.SEARCH &&
            route !is DesktopRoute.SourceManage &&
            // 编辑那一屏把源管理当**背景**渲（对岸那颗是独立 Dialog 窗口），
            // 所以停在那一屏时清单也在屏幕上。少了这一档，「撤回改写」之后 `sourceTick++`
            // 会撞在这句 return 上 ⇒ 那一列「已改写」永远刷不掉（本喵先写下再抓到的自己的一坑）。
            route !is DesktopRoute.SourceEdit &&
            // 手填凭据那一屏的**门就吃清单里那份字段名单**（34 颗里只有 3 颗有那一形）。
            // 少了这一档，从编辑屏或种子直达进来时 `sourceRows` 是空的 ⇒ 那一枚把手一颗都摆不出来。
            route !is DesktopRoute.SourceLogin &&
            route !is DesktopRoute.History
        ) {
            return@LaunchedEffect
        }
        val read = readSources ?: return@LaunchedEffect
        runCatching { read() }.fold(
            onSuccess = { rows ->
                sourceUi.sourceRows = rows
                sourceUi.sourceNote = null
                // 站点墙的抽屉由 selectedKey 派生（非 null 即开）：进屏不预选，否则一进来就弹抽屉，
                // 与「关掉 = onSelectedChange(null)」互相拆台（下一次清单重读又会把人拽回抽屉）。
                // 挑中的那颗已不在清单时把它清掉，免得抽屉宿主指向一颗不存在的站。
                if (sourceUi.sourceSelectedKey != null &&
                    rows.none { it.key == sourceUi.sourceSelectedKey }
                ) {
                    sourceUi.sourceSelectedKey = null
                }
            },
            onFailure = { t ->
                sourceUi.sourceRows = emptyList()
                println("D_源清单 读失败 ${t.javaClass.simpleName}: ${t.message?.take(160) ?: ""}")
                com.venera.desktop.DesktopErrorBus.report(
                    category = "漫画",
                    headline = "源清单读取失败",
                    humanReason = "随包的漫画源清单没读出来，源管理与搜索的显示名会跟着缺；重启可重读。",
                    detail = "${t.javaClass.simpleName}: ${t.message.orEmpty()}",
                    dedupKey = "comic:source_rows_read",
                )
                sourceUi.sourceNote = com.venera.desktop.platform.desktopFailureNoteOf(t)
            },
        )
    }

    // 源管理详情面板按选中源与 sourceDetailTick 读取设置与账号状态；无引擎时隐藏这两段，保留清单与维护卡。
    LaunchedEffect(sourceUi.sourceSelectedKey, sourceUi.sourceDetailTick, sourceUi.sourceRows) {
        val key = sourceUi.sourceSelectedKey ?: return@LaunchedEffect
        val e = engine
        if (e == null) {
            sourceUi.sourceSettings = null
            sourceUi.sourceAccount = null
            return@LaunchedEffect
        }
        // 设置定义：读原文 + 纯解析（不起引擎，本地读文件）。
        val script = readSourceText?.invoke(key)
        val defs = script?.let {
            runCatching {
                com.venera.desktop.gallery.data.DesktopSourceSettings.definitionsOf(it)
            }.getOrNull()
        }.orEmpty()
        // 账号读数：罐里那一格 + 手填凭据那一形在不在（跟着清单那行的名单走，同一份账）。
        sourceUi.sourceAccount = com.venera.desktop.sourcemanage.DesktopSourceAccountUi(
            loggedIn = runCatching { e.host.isLogged(key) }.getOrDefault(false),
            accountLine = "",
            canLoginWithCookies = sourceUi.sourceRows.firstOrNull { it.key == key }?.cookieFields?.isNotEmpty() == true,
            canLoginWithPassword = sourceUi.sourceRows.firstOrNull { it.key == key }?.supportsPasswordLogin == true,
            canLoginWithWebview = sourceUi.sourceRows.firstOrNull { it.key == key }?.supportsWebLogin == true,
        )
        if (defs.isEmpty()) {
            sourceUi.sourceSettings = null
            return@LaunchedEffect
        }
        // 读取站点设置只需注册默认设置项，跳过联网 init() 防止因站方线路超时卡死或拖慢界面回显。
        val gotValues = desktopEngineCall(e) {
            e.load(key, runInit = false)
            e.loadSettingsAll()
        }
        gotValues.fold(
            onSuccess = { raw ->
                val values = com.venera.desktop.gallery.data.DesktopSourceSettings.stringifyValues(raw)
                sourceUi.sourceSettings = defs.map { def ->
                    com.venera.desktop.sourcemanage.DesktopSourceSettingUi(
                        settingKey = def.settingKey,
                        title = def.title,
                        type = def.type,
                        options = def.options,
                        buttonText = def.buttonText,
                        currentValue = values[def.settingKey].orEmpty().ifBlank { def.default },
                    )
                }
            },
            onFailure = { t ->
                sourceUi.sourceSettings = null
                val note = "读不到设置值：${t.javaClass.simpleName} " + (t.message?.take(160) ?: "")
                sourceUi.sourceSettingsNote = note
                com.venera.desktop.DesktopErrorBus.report(
                    category = "站点设置",
                    headline = "读取 $key 设置失败",
                    detail = note,
                    dedupKey = "setting:read:$key",
                )
            },
        )
    }

    // 源编辑读原文键含 sourceEditTick，确保撤回改写后再次进入能读到最新内容而非旧缓存。
    LaunchedEffect(route, sourceUi.sourceEditTick) {
        val r = route
        if (r !is DesktopRoute.SourceEdit) {
            // 离开那一屏就把稿子交回"还没读到"：留着上一颗源的原文，进下一颗时
            // 会先闪一屏别人的脚本 —— 那是比空屏更糟的形状。
            sourceUi.sourceEditText = null
            sourceUi.sourceEditNote = null
            return@LaunchedEffect
        }
        val read = readSourceText
        if (read == null) {
            sourceUi.sourceEditNote = "无法打开"
            return@LaunchedEffect
        }
        val got = withContext(Dispatchers.IO) { runCatching { read(r.key) } }
        val text = got.getOrNull()
        when {
            got.isFailure -> {
                sourceUi.sourceEditText = null
                val t = got.exceptionOrNull()
                sourceUi.sourceEditNote = "读不到原文：${t?.javaClass?.simpleName ?: "?"} " + (t?.message?.take(160) ?: "")
            }
            // 取不到那一篇（清单里有这颗、原文却读不出来）也要念实话，不许给一篇空的。
            text == null -> {
                sourceUi.sourceEditText = null
                sourceUi.sourceEditNote = "读不到 ${r.key} 的脚本原文（包内与仓库两处都没有）。"
            }
            else -> {
                sourceUi.sourceEditText = text
                sourceUi.sourceEditNote = null
            }
        }
    }

    // 手填凭据登录以 loginTick 为键；离开页面时清空提示与提交快照，避免跨源残留登录状态。
    LaunchedEffect(route, sourceUi.loginDraft, sourceUi.loginPasswordDraft, sourceUi.loginWebviewTrigger, sourceUi.loginTick) {
        val r = route
        if (r !is DesktopRoute.SourceLogin) {
            sourceUi.loginNote = null
            sourceUi.loginDraft = null
            sourceUi.loginPasswordDraft = null
            sourceUi.loginWebviewTrigger = false
            sourceUi.loginBusy = false
            return@LaunchedEffect
        }
        val isWeb = sourceUi.loginWebviewTrigger
        val passwordDraft = sourceUi.loginPasswordDraft
        val cookieDraft = sourceUi.loginDraft
        if (!isWeb && passwordDraft == null && cookieDraft == null) return@LaunchedEffect
        // 幂等：同一 tick 已发过车就跳过。effect 内下面清空草稿会重跑本 effect，
        // 否则同一笔登录会被提交两遍（UI busy 只挡手点，挡不住这次重组重入）。
        if (sourceUi.loginHandledTick == sourceUi.loginTick) return@LaunchedEffect
        sourceUi.loginHandledTick = sourceUi.loginTick
        sourceUi.loginBusy = true
        val got = if (isWeb) {
            val doLogin = loginWithWebview
            if (doLogin == null) {
                Result.failure(IllegalStateException("装配点没接上网页登录这一路"))
            } else {
                runCatching { doLogin(r.key) }
            }
        } else if (passwordDraft != null) {
            val doLogin = loginWithPassword
            if (doLogin == null) {
                Result.failure(IllegalStateException("装配点没接上账号密码登录这一路"))
            } else {
                runCatching { doLogin(r.key, passwordDraft.first, passwordDraft.second) }
            }
        } else {
            val doLogin = loginWithCookies
            if (doLogin == null) {
                Result.failure(IllegalStateException("装配点没接上手填 Cookie 这一路"))
            } else {
                val payload = com.venera.desktop.gallery.data.DesktopCookieLogin.payload(r.fields, cookieDraft.orEmpty())
                runCatching { doLogin(r.key, payload) }
            }
        }
        sourceUi.loginBusy = false
        sourceUi.loginDraft = null
        sourceUi.loginPasswordDraft = null
        sourceUi.loginWebviewTrigger = false
        sourceUi.loginNote = got.fold(
            onSuccess = { it },
            onFailure = { t ->
                println("D_源登录 失败 $r.key ${t.javaClass.simpleName}: ${t.message?.take(200) ?: ""}")
                com.venera.desktop.DesktopErrorBus.report(
                    category = "漫画",
                    headline = "源账号登录失败",
                    humanReason = "这颗源的账号没登上，凭据可能失效或站方接口变了；可在源管理里重填凭据。",
                    detail = "${r.key}: ${t.javaClass.simpleName}: ${t.message.orEmpty()}",
                    dedupKey = "comic:source_login:${r.key}",
                )
                com.venera.desktop.platform.desktopFailureNoteOf(t)
            },
        )
    }

    // 话级已下载扫描统一收口于此（供下载屏、批量弹窗与探索徽章共用），键含 route、downloadsTick 与任务终局 rerollKey，失败时报错不报空。
    LaunchedEffect(
        route,
        filesSegment,
        currentTab,
        downloadsTick,
        detailUi.showBulkDownload,
        desktopComicShelfRerollKey(queueTasks),
    ) {
        // 补链：合并页的「本机已下载」档才是今天的主路（这颗 effect 从前只认旧 Downloads
        // 路由与探索格——门并页后档位不落旧路由，表没人数、屏就永远念"正在数…"）。
        if (
            route !is DesktopRoute.Downloads && !detailUi.showBulkDownload && currentTab != VeneraNavTab.EXPLORE &&
            currentTab != VeneraNavTab.SEARCH && // 选择器页的探索档也吃离线徽章
            route !is DesktopRoute.Files
        ) {
            return@LaunchedEffect
        }
        val root = downloadsRoot
        if (root == null) {
            filesUi.downloadsListing = DesktopComicDownloadListing(
                groups = emptyList(), skipped = 0, rootExists = false,
                error = "下载根目录拿不到 ⇒ 那棵树数不着",
            )
            return@LaunchedEffect
        }
        val listing = withContext(Dispatchers.IO) {
            runCatching {
                val (chapters, skipped) = desktopComicDownloadedScan(root)
                DesktopComicDownloadListing(
                    groups = desktopComicChaptersByComic(chapters),
                    skipped = skipped,
                    rootExists = root.isDirectory,
                )
            }.getOrElse { t ->
                DesktopComicDownloadListing(
                    groups = emptyList(), skipped = 0, rootExists = root.isDirectory,
                    error = "${t.javaClass.simpleName} ${t.message?.take(160) ?: ""}",
                )
            }
        }
        filesUi.downloadsListing = listing
        // 取证读数（只进 stdout，不进界面）：那六格与表头那句出自**同一次**读盘，不另走一趟。
        if (listing.error == null) println(desktopComicDownloadedReading(listing))
        // 探索卡下载徽章复用同一次扫描结果；命中读数日志移至独立 effect，避免因卡片尚未到达而误报 0。
        filesUi.comicCardBadges = if (listing.error == null) {
            desktopComicOfflineBadges(listing.groups.flatMap { it.chapters })
        } else {
            // 数不出来时那张表要**清空**：留着上一趟的旧表，卡上会继续贴一枚已经不作数的徽章。
            emptyMap()
        }
    }

    // 本地漫画本级扫描与话级扫描分开，在进入本地漫画/Tab 或删除/导入后重扫，失败时报错不报空。
    LaunchedEffect(route, filesSegment, downloadsTick, localComicImportTick) {
        if (
            route !is DesktopRoute.LocalComics && route !is DesktopRoute.Tab &&
            !(route is DesktopRoute.Files ||
                filesSegment == com.venera.desktop.downloads.DesktopFilesSegment.LOCAL)
        ) {
            return@LaunchedEffect
        }
        val root = downloadsRoot
        if (root == null) {
            localComicScan = DesktopLocalComicLibraryScan(
                comics = emptyList(), rootExists = false,
                error = "下载根目录拿不到 ⇒ 那棵树数不着",
            )
            return@LaunchedEffect
        }
        localComicScan = withContext(Dispatchers.IO) {
            runCatching {
                DesktopLocalComicLibraryScan(
                    comics = desktopLocalComics(root),
                    rootExists = root.isDirectory,
                    error = null,
                )
            }.getOrElse { t ->
                DesktopLocalComicLibraryScan(
                    comics = emptyList(), rootExists = root.isDirectory,
                    error = "${t.javaClass.simpleName} ${t.message?.take(160) ?: ""}",
                )
            }
        }
        // 取证读数（只进 stdout）：本 / 话 / 页 / 根 / 塌出自**同一次**扫描，不另走一趟盘。
        localComicScan?.let { println(desktopLocalComicLibraryReading(it)) }
    }

    // CBZ/ZIP 导入的文件选择框、解包落盘与重扫触发均留在壳层，保持屏组件零 IO。
    LaunchedEffect(filesUi.localComicImportPick) {
        val pick = filesUi.localComicImportPick ?: return@LaunchedEffect
        filesUi.localComicImportPick = null
        if (filesUi.localComicImportBusy) return@LaunchedEffect
        filesUi.localComicImportBusy = true
        filesUi.localComicNote = null
        val root = downloadsRoot
        val outcome = withContext(Dispatchers.IO) {
            runCatching { desktopLocalComicImport(pick, root) }.getOrElse { t ->
                DesktopLocalComicImportOutcome(
                    error = "导入塌了：${t.javaClass.simpleName} ${t.message?.take(160) ?: ""}",
                )
            }
        }
        filesUi.localComicImportBusy = false
        filesUi.localComicNote = outcome.error
            ?: "成功导入「${outcome.title}」· ${outcome.pages} 页"
        println("D_本地导入 成功=" + (if (outcome.error == null) "1" else "0") +
            " 页=" + outcome.pages + " 题=" + outcome.title +
            (outcome.error?.let { " 因=$it" } ?: ""))
        if (outcome.error != null) {
            DesktopErrorBus.report(
                category = "漫画",
                headline = "本地漫画导入失败",
                humanReason = outcome.error,
                detail = "title=${outcome.title} pages=${outcome.pages}\n${outcome.error}",
                dedupKey = "comic:local_import_fail",
            )
        }
        if (outcome.error == null) localComicImportTick++
    }

    // 独立 effect 输出探索卡下载徽章命中读数，确保盘表与卡片均就绪后再统计。
    LaunchedEffect(currentTab, filesUi.comicCardBadges, exploreSections) {
        if (currentTab != VeneraNavTab.EXPLORE && currentTab != VeneraNavTab.SEARCH) return@LaunchedEffect
        // 只有「那一棵树真数回来了」才打这一句：还没数（`null`）或数不出来（`error`）时打出去
        // 会念成 `贴上=0 盘上=0`，那与"确实一张都没贴"在读数上长得一模一样 —— 本仓那一族假话。
        val listing = filesUi.downloadsListing ?: return@LaunchedEffect
        if (listing.error != null) return@LaunchedEffect
        println(
            desktopComicOfflineHitReading(
                sourceKey = DESKTOP_EXPLORE_SOURCE,
                badges = filesUi.comicCardBadges,
                cardIds = exploreSections.flatMap { it.comics }.map { it.id },
            ),
        )
    }

    // 仅在历史屏开启且 homeHistory 已读回时输出取证读数，避免未就绪时误报行=0。
    LaunchedEffect(route, homeHistory, historySourceName) {
        if (route !is DesktopRoute.History) return@LaunchedEffect
        if (!homeHistory.loaded) return@LaunchedEffect
        println(
            desktopComicHistoryScreenReading(
                sourceKey = DESKTOP_EXPLORE_SOURCE,
                sourceName = historySourceName,
                rowCount = homeHistory.records.size,
                note = homeHistory.note,
            ),
        )
    }


    /**
     * 「本机已下载」那一行的删除那两步（接上门）。
     *
     * 第一次按只**武装**（盘上一颗文件都不动），第二次按才真删 —— 而"该不该真删"和"武装记谁"
     * 都不在这里重写，问的是 `DesktopComicDownloadsDelete.kt` 里那两颗纯函数。
     * 删完立刻 `downloadsTick++`：删除是同步改盘的，那一行不该等队列那张表才改口。
     */
    LaunchedEffect(route, detailUi.readerPage, readerPages.size) {
        val r = route
        if (r !is DesktopRoute.Reader) return@LaunchedEffect
        val write = writeHistory ?: return@LaunchedEffect
        if (readerPages.isEmpty()) return@LaunchedEffect
        val nowMillis = System.currentTimeMillis()
        // 会话时长真账：上一翻页到这一翻页的真实间隔 = 上一页的阅读时长（钳 1~600 秒）。
        // 从前固定 15 秒是假账 —— 排行榜的「分钟」全是它乘出来的。
        val realSeconds = if (lastPageTurnAt > 0L) {
            ((nowMillis - lastPageTurnAt) / 1000L).coerceIn(1L, 600L)
        } else {
            15L
        }
        lastPageTurnAt = nowMillis
        val progress = com.venera.desktop.gallery.data.DesktopReadingProgress(
            comicId = r.comicId,
            title = r.title,
            sourceKey = r.sourceKey,
            chapterTitle = r.episode.name,
            chapterIndex = detailUi.detailInfo?.episodes?.indexOfFirst { it.id == r.episode.id } ?: 0,
            pageIndex = detailUi.readerPage,
            totalPages = readerPages.size,
            coverUrl = r.cover,
        )
        withContext(Dispatchers.IO) {
            com.venera.desktop.gallery.data.desktopWriteReadingHistory(
                progress = progress,
                nowMillis = nowMillis,
                save = write,
            )
            runCatching {
                val core = com.venera.compose.data.db.DatabasePorts.of(null).core
                val store = com.venera.compose.data.db.ReadingStatsStore(core)
                val todayStr = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                val tagsStr = detailUi.detailInfo?.tags.orEmpty().joinToString("\u001F")
                store.insertSession(
                    comicId = r.comicId,
                    comicTitle = r.title,
                    sourceName = r.sourceKey,
                    tags = tagsStr,
                    chapterTitle = r.episode.name,
                    pagesRead = 1,
                    durationSeconds = realSeconds,
                    readDate = todayStr,
                    createdAt = nowMillis,
                )
            }
        }
    }


    // 详情页收藏夹栏状态在组合期由已读回的收藏表派生；未加载完成时返回 null，不可折算为未收藏。
    LaunchedEffect(
        favoriteFolderComicId, favoritesLoaded, favoriteFolderSelected, favoriteFolderNames,
    ) {
        val id = favoriteFolderComicId ?: return@LaunchedEffect
        if (!favoritesLoaded) return@LaunchedEffect
        println(
            desktopFavoriteFolderReading(
                comicId = id,
                currentFolder = favoriteFolderCurrent,
                selected = favoriteFolderSelected,
                folderCount = favoriteFolderNames.size,
            ),
        )
    }

    // 仅在收藏屏开启且收藏表已读回时输出筛选夹取证读数，键仅取总数与展示数。
    LaunchedEffect(
        currentTab, favoritesLoaded, favoritesTabSelected, favorites.size, favoritesShown.size,
    ) {
        if (currentTab != VeneraNavTab.FAVORITES || !favoritesLoaded) return@LaunchedEffect
        println(
            desktopFavoriteFolderTabReading(
                folder = favoritesTabSelected,
                total = favorites.size,
                shown = favoritesShown.size,
                tabCount = favoritesFolderTabs.size,
            ),
        )
    }
}
