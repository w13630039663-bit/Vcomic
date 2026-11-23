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
internal fun DesktopComicAndSourceHost(
    r: DesktopRoute,
    layoutMode: WideScreenLayoutMode,
    engine: com.venera.engineprobe.EngineSession?,
    ports: GalleryPorts,
    scope: CoroutineScope,
    downloadsRoot: java.io.File?,
    galleryPaths: com.venera.compose.data.platform.PathProvider?,
    galleryPrefs: com.venera.desktop.gallery.data.DesktopGalleryPreferences?,
    galleryGuard: com.venera.desktop.gallery.data.DesktopGalleryContentGuard?,
    readerPrefs: com.venera.desktop.gallery.data.DesktopComicReaderPreferences?,
    desktopUx: DesktopUxPlatformServices,
    businessHandle: com.venera.desktop.business.DesktopBusinessPorts.Handle?,
    queueTasks: List<com.venera.desktop.gallery.data.DesktopComicQueueTask>,
    queueConcurrency: Int,
    queuePrimed: Boolean,
    favoritesLoaded: Boolean,
    favoritesState: MutableState<List<com.venera.compose.data.db.FavoriteRecord>>,
    favoriteFolderNames: List<String>,
    localComicScan: DesktopLocalComicLibraryScan?,
    readerMode: com.venera.desktop.gallery.data.DesktopReaderMode,
    readerModeNote: String?,
    detailCoreCache: MutableMap<String, Pair<CoroutineScope, ComicDetailCore>>,
    sourceUi: SourceManageUiState,
    filesUi: FilesAuxUiState,
    detailUi: DetailAndReaderUiState,
    aggregateSearch: com.venera.desktop.search.DesktopAggregateSearchHolder,
    runSourceImport: () -> Unit,
    writeSourceEnabled: ((String, Boolean) -> Unit)?,
    writeSourceOrder: ((List<String>) -> Unit)?,
    readSourceText: ((String) -> String?)?,
    clearSourceOverride: (suspend (String) -> Unit)?,
    loginWithCookies: (suspend (String, List<String>) -> String)?,
    loginWithPassword: (suspend (String, String, String) -> String)?,
    loginWithWebview: (suspend (String) -> String)?,
    writeHistory: (suspend (com.venera.compose.data.db.HistoryRecord) -> Unit)?,
    addFavorite: (suspend (com.venera.compose.data.db.FavoriteRecord) -> Unit)?,
    removeFavorite: (suspend (String) -> Unit)?,
    createFavoriteFolder: (suspend (String) -> Unit)?,
    deleteFavoriteFolder: (suspend (String) -> Unit)?,
    openSettings: () -> Unit,
    searchComics: (suspend (String, Int, String, List<String?>) -> DesktopComicSearchOutcome)?,
    onDownloadsDelete: (com.venera.desktop.gallery.data.DesktopComicDownloadedChapter, Boolean) -> Unit,
    flyToDownloadCenter: (String?, Boolean) -> Unit,
    routeState: MutableState<DesktopRoute>,
    currentTabState: MutableState<VeneraNavTab>,
    commentsTickState: MutableIntState,
    readerPagesState: MutableState<List<com.venera.desktop.gallery.data.DesktopComicPage>>,
    searchReturnRouteState: MutableState<DesktopRoute?>,
    sourceTickState: MutableIntState,
    downloadsTickState: MutableIntState,
    localComicImportTickState: MutableState<Int>,
    armedDeleteKeyState: MutableState<String?>,
    readerModeChoiceState: MutableState<com.venera.desktop.gallery.data.DesktopReaderMode?>,
    searchQueryState: MutableState<String>,
    searchSourceKeyState: MutableState<String?>,
    searchTickState: MutableIntState,
    /** 追更账本；null = 装配点没接 ⇒ 详情页追更按钮不摆（没有写口就没有那枚钮）。 */
    followDao: com.venera.compose.data.db.ComicFollowDao? = null,
) {
    val ux = com.venera.compose.components.LocalUxPlatformServices.current
    var favorites by favoritesState
    var route by routeState
    var currentTab by currentTabState
    var commentsTick by commentsTickState
    var readerPages by readerPagesState
    var searchReturnRoute by searchReturnRouteState
    var sourceTick by sourceTickState
    var downloadsTick by downloadsTickState
    var localComicImportTick by localComicImportTickState
    var armedDeleteKey by armedDeleteKeyState
    var readerModeChoice by readerModeChoiceState
    var searchQuery by searchQueryState
    var searchSourceKey by searchSourceKeyState
    var searchTick by searchTickState
                val localImportPickAction: (() -> Unit)? = downloadsRoot?.let { root ->
                    {
                        val startDir = java.io.File(root, "imported").takeIf { it.isDirectory }?.absolutePath
                        val picked = com.venera.desktop.platform.desktopPickFile(
                            owner = null,
                            mode = java.awt.FileDialog.LOAD,
                            title = "导入 CBZ / ZIP 漫画包",
                            initialDir = startDir,
                            filterPattern = "*.cbz;*.zip;*.CBZ;*.ZIP",
                        )?.let { java.io.File(it) }
                        picked?.let {
                            filesUi.localComicImportPick = it
                            filesUi.localComicNote = null
                        }
                    }
                }
                // 「补齐这一话」的接线在合并页与独立屏之间也只有一处
                // （`DesktopDownloadsEntryTest` 钉的还是「壳里一处按」；复制一份就是两条轨）。
                val downloadsRepairAction: (com.venera.desktop.gallery.data.DesktopComicDownloadedChapter) -> Unit =
                    { chapter ->
                        val root = downloadsRoot
                        if (root == null) {
                            filesUi.downloadsNote = "下载根目录拿不到 ⇒ 这一话补不了"
                        } else {
                            filesUi.downloadsNote = desktopComicRepairChapter(
                                root,
                                chapter,
                                DesktopComicShelfRawIdIndex.of(queueTasks),
                            )
                        }
                    }
                // 「打开这一话的目录」：盘上定树路走既有纯件（desktopComicLocalChapter，读盘在 IO 线程），
                // 壳只把定好的那格交系统文件管理器；找不到话那一档如实念，不开空头目录。
                val openDownloadedChapterDir: (com.venera.desktop.gallery.data.DesktopComicDownloadedChapter) -> Unit = { ch ->
                    val root = downloadsRoot
                    if (root == null) {
                        filesUi.downloadsNote = "下载根目录拿不到 ⇒ 目录开不了"
                    } else {
                        scope.launch {
                            val dir = withContext(Dispatchers.IO) {
                                com.venera.desktop.gallery.data.desktopComicLocalChapter(
                                    root,
                                    ch.sourceKey,
                                    ch.comicId,
                                    ch.chapterId,
                                )?.dir
                            }
                            if (dir == null) {
                                filesUi.downloadsNote = "盘上没再认出这一话 ⇒ 目录开不了"
                            } else {
                                runCatching { java.awt.Desktop.getDesktop().open(dir) }
                            }
                        }
                    }
                }
    when (r) {
                    is DesktopRoute.SourceManage, is DesktopRoute.SourceEdit -> DesktopSourceManageScreen(
                        rows = sourceUi.sourceRows,
                        selectedKey = sourceUi.sourceSelectedKey,
                        onSelectedChange = { sourceUi.sourceSelectedKey = it; sourceUi.sourceSettingsNote = null },
                        onImportSource = runSourceImport,
                        updateBusy = sourceUi.sourceUpdateBusy,
                        updateNote = sourceUi.sourceUpdateNote,
                        note = if (r is DesktopRoute.SourceEdit && sourceUi.sourceEditNote != null) {
                            sourceUi.sourceEditNote
                        } else {
                            sourceUi.sourceNote
                        },
                        onToggleSource = { key, enabled ->
                            // 启停之后**重读清单**而不是本地翻那一格 ——
                            // canDisable 是"整屏算出来的闸"（只剩一颗开着时别的那些行也跟着变），
                            // 本地只改那一行会把闸画旧（用户关掉倒数第二颗后，最后一颗仍显示能关）。
                            runCatching { writeSourceEnabled?.invoke(key, enabled) }
                                .onFailure { t ->
                                    println("D_源启停 写失败 $key ${t.javaClass.simpleName}: ${t.message?.take(160) ?: ""}")
                                    com.venera.desktop.DesktopErrorBus.report(
                                        category = "漫画",
                                        headline = "源启停开关写入失败",
                                        humanReason = "这颗源的启停状态没写进配置，重启后会回到原状态；可再点一次。",
                                        detail = "$key: ${t.javaClass.simpleName}: ${t.message.orEmpty()}",
                                        dedupKey = "comic:source_enabled_write:$key",
                                    )
                                    sourceUi.sourceNote = com.venera.desktop.platform.desktopFailureNoteOf(t)
                                }
                            sourceTick++
                        },
                        // 拖拽重排：顺序先落盘，启停只动真正变过的那几颗，逐颗走既有写口
                        // （它自带「读回 null 先落默认档」那条保护，别绕开）。
                        onReorderSources = if (writeSourceOrder == null) {
                            null
                        } else {
                            { ordered, enabled ->
                                runCatching { writeSourceOrder?.invoke(ordered) }
                                    .onFailure { t ->
                                        println("D_源顺序 写失败 ${t.javaClass.simpleName}: ${t.message?.take(160) ?: ""}")
                                        com.venera.desktop.DesktopErrorBus.report(
                                            category = "漫画",
                                            headline = "源顺序保存失败",
                                            humanReason = "拖拽后的源顺序没写进配置，重启后会回到旧顺序；可再拖一次。",
                                            detail = "${t.javaClass.simpleName}: ${t.message.orEmpty()}",
                                            dedupKey = "comic:source_order_write",
                                        )
                                        sourceUi.sourceNote = com.venera.desktop.platform.desktopFailureNoteOf(t)
                                    }
                                sourceUi.sourceRows.forEach { row ->
                                    val should = row.key in enabled
                                    if (row.enabled != should) {
                                        runCatching { writeSourceEnabled?.invoke(row.key, should) }
                                    }
                                }
                                sourceTick++
                            }
                        },
                        onBack = { route = DesktopRoute.Tab },
                        // ── ：编辑 / 撤回 ──
                        //
                        // 读原文那一口不在手时**整枚把手不摆**（§119 灰把手那条）。
                        onEditSource = if (readSourceText == null) {
                            null
                        } else {
                            { key ->
                                route = DesktopRoute.SourceEdit(
                                    key = key,
                                    // 名字从**已经在手的那份清单**里取，不为了一颗名字再读一遍 34 份 js。
                                    title = sourceUi.sourceRows.firstOrNull { it.key == key }?.title ?: key,
                                )
                                sourceUi.sourceEditTick++
                            }
                        },
                        // 那枚「登录」只在装配点接上至少一路、且那颗源声明了账密、Cookie
                        // 或网页登录方式时才摆。能力位已经跟着清单那一趟从脚本文本读回来了。
                        onSourceLogin = if (loginWithCookies == null && loginWithPassword == null) {
                            if (loginWithWebview == null) null
                            else { key ->
                                sourceUi.loginNote = null
                                sourceUi.loginDraft = null
                                sourceUi.loginPasswordDraft = null
                                val row = sourceUi.sourceRows.firstOrNull { it.key == key }
                                route = DesktopRoute.SourceLogin(
                                    key = key,
                                    title = row?.title ?: key,
                                    fields = row?.cookieFields.orEmpty(),
                                    supportsPasswordLogin = row?.supportsPasswordLogin == true,
                                    supportsWebLogin = row?.supportsWebLogin == true,
                                )
                            }
                        } else {
                            { key ->
                                sourceUi.loginNote = null
                                sourceUi.loginDraft = null
                                sourceUi.loginPasswordDraft = null
                                val row = sourceUi.sourceRows.firstOrNull { it.key == key }
                                route = DesktopRoute.SourceLogin(
                                    key = key,
                                    title = row?.title ?: key,
                                    fields = row?.cookieFields.orEmpty(),
                                    supportsPasswordLogin = row?.supportsPasswordLogin == true,
                                    supportsWebLogin = row?.supportsWebLogin == true,
                                )
                            }
                        },
                        onClearSourceOverride = if (clearSourceOverride == null) {
                            null
                        } else {
                            { key ->
                                scope.launch {
                                    runCatching { clearSourceOverride.invoke(key) }.fold(
                                        onSuccess = {
                                            // 撤完必须重读清单：那一列「已改写」标的是覆盖层里有没有文件，
                                            // 不重读就会继续标着已经撤掉的那一颗（屏上念一句已经不存在的话）。
                                            sourceTick++
                                            sourceUi.sourceEditTick++
                                            sourceUi.sourceDetailTick++
                                        },
                                        onFailure = { t ->
                                            println("D_源改写 撤销失败 $key ${t.javaClass.simpleName}: ${t.message?.take(160) ?: ""}")
                                            com.venera.desktop.DesktopErrorBus.report(
                                                category = "漫画",
                                                headline = "源改写撤销失败",
                                                humanReason = "这颗源的改写脚本没撤掉，覆盖层文件可能被占用；可重试或手动清理。",
                                                detail = "$key: ${t.javaClass.simpleName}: ${t.message.orEmpty()}",
                                                dedupKey = "comic:source_revert:$key",
                                            )
                                            sourceUi.sourceNote = com.venera.desktop.platform.desktopFailureNoteOf(t)
                                        },
                                    )
                                }
                            }
                        },
                        // ── ：详情面板三段 ──
                        account = sourceUi.sourceAccount,
                        onSignOutSource = if (engine == null) null else {
                            {
                                val key = sourceUi.sourceSelectedKey
                                if (key != null) {
                                    runCatching { engine.host.logoutSource(key) }
                                        .onFailure { t ->
                                            println("D_源退出登录 写失败 $key ${t.javaClass.simpleName}: ${t.message?.take(160) ?: ""}")
                                            com.venera.desktop.DesktopErrorBus.report(
                                                category = "漫画",
                                                headline = "源退出登录失败",
                                                humanReason = "账号没退干净，凭据文件可能被占用；可重试，必要时手动删除该源凭据。",
                                                detail = "$key: ${t.javaClass.simpleName}: ${t.message.orEmpty()}",
                                                dedupKey = "comic:source_logout:$key",
                                            )
                                            sourceUi.sourceSettingsNote = com.venera.desktop.platform.desktopFailureNoteOf(t)
                                        }
                                    sourceUi.sourceDetailTick++
                                }
                            }
                        },
                        settings = sourceUi.sourceSettings,
                        onSettingChange = if (engine == null) null else { settingKey, value ->
                            val key = sourceUi.sourceSelectedKey ?: return@DesktopSourceManageScreen
                            val parsed = com.venera.desktop.gallery.data.DesktopSourceSettings
                                .parseValueForWrite(value)
                            val prevSettings = sourceUi.sourceSettings
                            // 立即乐观更新界面上的当前选项，避免等待后台线程期间一直显示旧值。
                            if (prevSettings != null) {
                                sourceUi.sourceSettings = prevSettings.map { item ->
                                    if (item.settingKey == settingKey) item.copy(currentValue = value) else item
                                }
                            }
                            scope.launch {
                                val got = desktopEngineCall(engine) {
                                    engine.load(key, runInit = false)
                                    engine.saveSettingValue(settingKey, parsed)
                                    engine.applySettingLive(settingKey)
                                }
                                got.fold(
                                    onSuccess = {
                                        sourceUi.sourceSettingsNote = null
                                        sourceUi.sourceDetailTick++
                                    },
                                    onFailure = { t ->
                                        sourceUi.sourceSettings = prevSettings
                                        val detail = "${t.javaClass.simpleName}: ${t.message.orEmpty()}"
                                        println("D_站点设置 存失败 $key.$settingKey $detail")
                                        sourceUi.sourceSettingsNote = com.venera.desktop.platform.desktopFailureNoteOf(t)
                                        com.venera.desktop.DesktopErrorBus.report(
                                            category = "站点设置",
                                            headline = "保存 $key.$settingKey 失败",
                                            humanReason = "这项设置没写进源配置，界面已回滚到原值；可再改一次。",
                                            detail = detail,
                                            dedupKey = "setting:save:$key:$settingKey",
                                        )
                                    },
                                )
                            }
                        },
                        onSettingCallback = if (engine == null) null else { settingKey ->
                            val key = sourceUi.sourceSelectedKey ?: return@DesktopSourceManageScreen
                            scope.launch {
                                val got = desktopEngineCall(engine) {
                                    engine.load(key)
                                    engine.runSettingCallback(settingKey)
                                }
                                got.fold(
                                    onSuccess = { note ->
                                        sourceUi.sourceSettingsNote = note ?: "跑完了（源没给回话）"
                                        sourceUi.sourceDetailTick++
                                    },
                                    onFailure = { t ->
                                        val note = "没跑成：${t.javaClass.simpleName} " + (t.message?.take(160) ?: "")
                                        sourceUi.sourceSettingsNote = note
                                        com.venera.desktop.DesktopErrorBus.report(
                                            category = "站点设置",
                                            headline = "执行 $key.$settingKey 失败",
                                            detail = note,
                                            dedupKey = "setting:cb:$key:$settingKey",
                                        )
                                    },
                                )
                            }
                        },
                        settingsNote = sourceUi.sourceSettingsNote,
                    )
                    // 手填凭据为普通页面而非独立窗口，独占路由分支渲染，返回时回到源管理页。
                    is DesktopRoute.SourceLogin -> DesktopSourceLoginScreen(
                        mode = layoutMode,
                        sourceName = r.title,
                        fields = r.fields,
                        supportsPasswordLogin = r.supportsPasswordLogin,
                        supportsWebLogin = r.supportsWebLogin,
                        note = sourceUi.loginNote,
                        busy = sourceUi.loginBusy,
                        onSubmitCookies = { values ->
                            // 登录提交以 loginTick 驱动而非 Map 等值，确保用户未改输入直接重试时仍能重新触发请求。
                            sourceUi.loginDraft = values
                            sourceUi.loginPasswordDraft = null
                            sourceUi.loginTick++
                        },
                        onSubmitPassword = { account, password ->
                            sourceUi.loginDraft = null
                            sourceUi.loginPasswordDraft = account to password
                            sourceUi.loginTick++
                        },
                        onStartWebLogin = {
                            sourceUi.loginDraft = null
                            sourceUi.loginPasswordDraft = null
                            sourceUi.loginWebviewTrigger = true
                            sourceUi.loginTick++
                        },
                        onBack = { route = DesktopRoute.SourceManage },
                    )
                    // 设置页缺少写口件时不渲染；Pixiv 账号/作品/画师三屏接入真实端口，看图复用 GalleryPostViewer。
                    is DesktopRoute.Detail -> {
                        // 桌面漫画详情页使用 DesktopComicDetailScreen 并复用同一 detailCore（含按本缓存与路由同步）。
                        val handle = businessHandle
                        if (handle == null) {
                            // 装配没接上三枚读数（paths / guard / assetDir 任一缺）⇒ 如实念成因，
                            // 不拿空端口假装取到了数（与"没有写口就没有那枚钮"同一条口径）。
                            Column(
                                modifier = Modifier.fillMaxSize().padding(VeneraTokens.spacing.screenHorizontal),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text("无法打开详情", color = VeneraTokens.color.textSecondary)
                                Spacer(Modifier.height(VeneraTokens.spacing.space4))
                                com.venera.desktop.ui.DesktopBackButton(onClick = { route = DesktopRoute.Tab })
                            }
                        } else {
                            // 详情核按本 LRU 缓存（上限 4 本，逐出时取消作用域）。缓存本体挂在壳层
                            // （见上方 detailCoreCache）：进阅读器时分支离场不销毁它，返回直接复用状态。
                            val detailCacheKey = "${r.sourceKey}|${r.comicId}"
                            val detailCached = detailCoreCache[detailCacheKey]
                            val detailScope = detailCached?.first ?: CoroutineScope(SupervisorJob() + Dispatchers.IO)
                            val detailCore = detailCached?.second
                                ?: com.venera.desktop.business.DesktopBusinessPorts.createDetailCore(handle, detailScope)
                            if (detailCached == null) {
                                detailCoreCache[detailCacheKey] = detailScope to detailCore
                                while (detailCoreCache.size > 4) {
                                    val oldest = detailCoreCache.entries.firstOrNull { it.key != detailCacheKey }
                                    if (oldest == null) break
                                    oldest.value.first.cancel()
                                    detailCoreCache.remove(oldest.key)
                                }
                            }
                            // 监听详情核 currentComic 变化：推荐跳本原地换本时同步更新 route 并逐出失配缓存核。
                            LaunchedEffect(detailCore) {
                                detailCore.currentComic.collect { c ->
                                    val target = c ?: return@collect
                                    if (target.id != r.comicId || target.sourceName.let { it.isNotBlank() && it != r.sourceKey }) {
                                        detailCoreCache.values.forEach { it.first.cancel() }
                                        detailCoreCache.clear()
                                        route = DesktopRoute.Detail(
                                            from = route,
                                            comicId = target.id,
                                            title = target.title,
                                            cover = target.coverUrl,
                                            sourceKey = r.sourceKey,
                                        )
                                    }
                                }
                            }
                            // 路由仅带四字段构造初始 ComicItem（tags/author 随后由 liveDetails 覆盖），sourceName 经 resolveSourceKey 还原真键。
                            val comic = com.venera.compose.feature.ComicItem(
                                id = r.comicId,
                                title = r.title,
                                author = "",
                                coverUrl = r.cover,
                                sourceName = r.sourceKey,
                            )
                            // 桌面详情页：吃核 + 打码判据单源（壳现算 masked 递下去）。
                            // 本地收藏改道主表：已收档 = 主表里这一本占着哪些夹（现读现算）。
                            val desktopFavAddedFolders = favorites
                                .filter { it.comicId == r.comicId }
                                .map { it.folderName }
                                .toSet()
                            com.venera.desktop.detail.DesktopComicDetailScreen(
                                followPort = remember(detailCacheKey, followDao) {
                                    followDao?.let { com.venera.desktop.follow.DesktopFollowDetailPort(it) }
                                },
                                localFavoriteFolders = favoriteFolderNames,
                                localFavoriteAdded = desktopFavAddedFolders,
                                localFavorited = if (!favoritesLoaded) null else desktopFavAddedFolders.isNotEmpty(),
                                onLocalFavoriteToggle = { folder, added ->
                                    // 加书架与移出分档，不再都念「收藏」。
                                    DesktopPetCueBus.emit(if (added) DesktopPetCue.REMOVE_FAVORITE else DesktopPetCue.ADD_BOOKSHELF)
                                    scope.launch {
                                        if (added) {
                                            runCatching { removeFavorite?.invoke(r.comicId) }
                                        } else {
                                            runCatching {
                                                addFavorite?.invoke(
                                                    com.venera.compose.data.db.FavoriteRecord(
                                                        comicId = r.comicId,
                                                        title = r.title,
                                                        author = detailUi.detailInfo?.author.orEmpty(),
                                                        coverUrl = r.cover,
                                                        sourceName = r.sourceKey,
                                                        folderName = folder,
                                                        tags = detailUi.detailInfo?.tags.orEmpty().joinToString("\u001F"),
                                                    ),
                                                )
                                            }
                                        }
                                        detailUi.favoriteTick++
                                    }
                                },
                                // 新建夹走桌面主表写口，写完扳 tick 重读夹名单（详情弹窗与收藏屏同源）。
                                onLocalFavoriteFolderCreate = if (createFavoriteFolder == null) {
                                    null
                                } else {
                                    { name ->
                                        scope.launch {
                                            runCatching { createFavoriteFolder?.invoke(name) }
                                            detailUi.favoriteTick++
                                        }
                                    }
                                },
                                core = detailCore,
                                comic = comic,
                                mode = layoutMode,
                                masked = desktopUx.coverMaskStateOf(
                                    sourceKey = r.sourceKey,
                                    title = r.title,
                                    author = "",
                                    tags = emptyList(),
                                    comicId = r.comicId,
                                ) != "VISIBLE",
                                onBack = { route = DesktopRoute.Tab },
                                        onStartLiveReading = { session ->
                                            // 将详情核 onStartLiveReading 会话映射回 DesktopRoute.Reader，页表仍由壳层 LaunchedEffect(route) 统一拉取。
                                            val chapter = session.chapters
                                                .getOrNull(session.initialChapterIndex)
                                                ?: session.chapters.firstOrNull()
                                            if (chapter == null) {
                                                detailUi.readerNote = "这一话认不出来 ⇒ 没进阅读器（源站可能删了那一话）"
                                            } else {
                                                detailUi.readerPage = session.initialPageIndex
                                                readerPages = emptyList()
                                                detailUi.readerNote = "正在取这一话的页…"
                                                route = DesktopRoute.Reader(
                                                    comicId = session.comicId,
                                                    title = session.comicTitle,
                                                    cover = session.coverUrl,
                                                    sourceKey = session.sourceKey,
                                                    from = route,
                                                    episode = com.venera.engineprobe.EngineSession.Episode(
                                                        name = chapter.title,
                                                        id = chapter.id,
                                                        placeholder = chapter.id == session.comicId,
                                                    ),
                                                    startPage = session.initialPageIndex,
                                                )
                                            }
                                        },
                                        onSearchTag = { tag ->
                                            // 点标签 = 装进搜索框**并立刻发车**（与那排历史 chips 同一条腿）。
                                            // 没接上 `searchComics` 装配位时这一趟不发车（屏上标签仍念原文，只是
                                            // 点下去没有目的地 —— 事实不因门缺失被抹掉，也不假装能搜）。
                                            if (searchComics != null) {
                                                // tag 搜索按**这一本的源**发车
                                                // （从前恒走默认源），并留下返回这一本的退路。
                                                val tagRow = sourceUi.sourceRows.firstOrNull {
                                                    it.key.equals(r.sourceKey, ignoreCase = true) ||
                                                        it.title.equals(r.sourceKey, ignoreCase = true)
                                                }
                                                if (tagRow != null) {
                                                    searchSourceKey = tagRow.key
                                                    aggregateSearch.target = com.venera.desktop.search.DesktopSearchTarget.Single(tagRow.key)
                                                } else {
                                                    aggregateSearch.target = com.venera.desktop.search.DesktopSearchTarget.Single(
                                                        searchSourceKey ?: DESKTOP_EXPLORE_SOURCE,
                                                    )
                                                }
                                                searchReturnRoute = route
                                                searchQuery = tag
                                                searchTick++
                                                route = DesktopRoute.Tab
                                                currentTab = VeneraNavTab.SEARCH
                                            }
                                        },
                                        onDownloadStarted = { cover -> flyToDownloadCenter(cover, true) },
                            )
                        }
                    }


                    is DesktopRoute.Reader -> DesktopReaderScreen(
                        pages = readerPages,
                        episodeLabel = r.episode.name,
                        note = detailUi.readerNote,
                        masked = desktopUx.coverMaskStateOf(
                            sourceKey = r.sourceKey,
                            title = r.title,
                            author = "",
                            tags = emptyList(),
                            comicId = r.comicId,
                        ) != "VISIBLE",
                        currentPage = detailUi.readerPage,
                        // 这一趟从哪一页起：路由上带着（详情那一枚胶囊递库里那一页，
                        // 章节表那些行递 0）。**不**在这里读 `readerPage` —— 那一枚是
                        // "外面跟踪到哪儿了"，用它当起点会让续读永远落在人现在站的地方。
                        startPage = r.startPage,
                        // 跨话那一路的 effect 键（157 批）：相邻两话可能同页数，只键 pages.size
                        // 就不重落起点（人从第 40 页跨到下一话还站第 40 页）。话号进键，切话必重跑。
                        episodeKey = r.episode.id,
                        // 顶栏面包屑（阅读器重做批）：作品题名 + 源名。
                        comicTitle = r.title,
                        sourceKey = r.sourceKey,
                        onPageChange = { DesktopPetCueBus.emit(DesktopPetCue.TURN_PAGE); detailUi.readerPage = it },
                        onBack = {
                            route = r.from ?: DesktopRoute.Detail(r.comicId, r.title, r.cover, r.sourceKey)
                        },
                        // ── ：模式档 / 跨话 / 目录 ──
                        readerMode = readerMode,
                        seedNote = readerModeNote,
                        onPickMode = readerPrefs?.let { prefs ->
                            { m ->
                                // 写盘（stored 流跟着变）+ **当场记下这一挑**：种子还在时
                                // `desktopReaderModeOf` 会顶回种子那一档，不记 choice 就是"点了没反应"。
                                prefs.setReadingMode(m)
                                readerModeChoice = m
                            }
                        },
                        // 阅读器章节目录复用详情页保留的 detailInfo；从历史或启动参数直进时为 null 则不展示目录。
                        episodes = detailUi.detailInfo?.episodes?.map { it.name.ifBlank { it.id } },
                        chapterIndex = detailUi.detailInfo?.episodes?.indexOfFirst { it.id == r.episode.id } ?: -1,
                        onGoChapter = if (detailUi.detailInfo == null) null else { index ->
                            val ep = detailUi.detailInfo?.episodes?.getOrNull(index)
                            if (ep != null) {
                                // 与从详情进阅读同一跳法：换 route ⇒ 取页 effect 自己重跑（键里有 route）。
                                detailUi.readerPage = 0
                                readerPages = emptyList()
                                detailUi.readerNote = "正在取这一话的页…"
                                route = r.copy(episode = ep, startPage = 0)
                            }
                        },
                    )


                    // 「下载与本地」合并页内嵌复用三档原屏（embedded = true），同时保留三条旧路由直达入口。
                    DesktopRoute.Files -> com.venera.desktop.downloads.DesktopDownloadsPipelineScreen(
                        mode = layoutMode,
                        downloadsRoot = downloadsRoot,
                        queueTasks = queueTasks,
                        queuePrimed = queuePrimed,
                        queueNote = filesUi.queueNote,
                        onQueuePauseResume = { task ->
                            val root = downloadsRoot
                            if (task.status == com.venera.desktop.gallery.data.DesktopComicQueueStatus.RUNNING) {
                                DesktopComicDownloadQueue.requestStop(task.taskId)
                                filesUi.queueNote = desktopComicQueueStoppedLine(task)
                            } else if (root != null) {
                                DesktopComicDownloadQueue.resume(root, task.taskId)
                                filesUi.queueNote = "已恢复下载：${task.comicTitle} · ${task.chapterTitle}"
                            }
                        },
                        onQueueRemove = { task ->
                            val root = downloadsRoot
                            if (root == null) filesUi.queueNote = "下载根目录拿不到 ⇒ 这一笔撤不掉"
                            else filesUi.queueNote = desktopComicQueueRemovedLine(
                                task,
                                DesktopComicDownloadQueue.drop(root, task.taskId),
                            )
                        },
                        downloadedListing = filesUi.downloadsListing,
                        downloadedNote = filesUi.downloadsNote,
                        onRepairDownloadedChapter = downloadsRepairAction,
                        onDeleteDownloadedChapter = { onDownloadsDelete(it, true) },
                        onOpenDownloadedChapterDir = openDownloadedChapterDir,
                        onOpenDownloadedChapter = { ch ->
                            val rawIndex = com.venera.desktop.gallery.data.DesktopComicShelfRawIdIndex.of(queueTasks)
                            val rawComicId = rawIndex.rawIdOf(ch.sourceKey, ch.comicId) ?: com.venera.desktop.gallery.data.restoreSafeComicId(ch.comicId)
                            val mappedTask = queueTasks.firstOrNull { it.comicId == rawComicId || it.comicId == ch.comicId }
                            val effectiveTitle = mappedTask?.comicTitle?.takeIf { it.isNotBlank() && !it.startsWith("http") } ?: ch.title
                            // 主人口径「观看后首页历史要正确记录（含预览画面）」：封面从队列任务
                            // 回查官方 coverUrl —— 恒空串的话历史行/首页 Hero 就是永久空白框。
                            route = DesktopRoute.Reader(
                                comicId = rawComicId,
                                title = effectiveTitle,
                                cover = mappedTask?.comicCover.orEmpty(),
                                sourceKey = ch.sourceKey,
                                episode = com.venera.engineprobe.EngineSession.Episode(name = ch.title, id = ch.chapterId),
                                startPage = 0,
                                from = DesktopRoute.Files,
                            )
                        },
                        onOpenComicDetail = { sourceKey, comicId, title, cover ->
                            petWork(title)
                            val rawIndex = com.venera.desktop.gallery.data.DesktopComicShelfRawIdIndex.of(queueTasks)
                            val rawComicId = rawIndex.rawIdOf(sourceKey, comicId) ?: com.venera.desktop.gallery.data.restoreSafeComicId(comicId)
                            route = DesktopRoute.Detail(
                                from = route,
                                comicId = rawComicId,
                                title = title,
                                cover = cover ?: "",
                                sourceKey = sourceKey,
                            )
                        },
                        localComicScan = localComicScan,
                        localComicNote = filesUi.localComicNote,
                        onImportComics = localImportPickAction,
                        onOpenLocalComic = { comic ->
                            route = DesktopRoute.LocalComicReader(
                                title = comic.title,
                                rootPath = comic.rootPath,
                                startPage = 0,
                                from = DesktopRoute.Files,
                            )
                        },
                        onDeleteLocalComic = { comic ->
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    runCatching { java.io.File(comic.rootPath).deleteRecursively() }
                                }
                                downloadsTick++
                                localComicImportTick++
                            }
                        },
                        images = filesUi.imageItems.orEmpty(),
                        imagesScanning = false,
                        imagesNote = filesUi.imageNote,
                        onOpenImageDir = { img ->
                            scope.launch {
                                runCatching {
                                    java.awt.Desktop.getDesktop().open(java.io.File(img.path).parentFile)
                                }
                            }
                        },
                        onOpenImageSaveDir = {
                            scope.launch {
                                runCatching {
                                    val dir = galleryPaths?.let { pp ->
                                        com.venera.desktop.gallery.data.desktopGallerySaveDir(
                                            pp,
                                            galleryPrefs?.downloadDir?.value,
                                        )
                                    } ?: downloadsRoot
                                    if (dir != null) {
                                        dir.mkdirs()
                                        java.awt.Desktop.getDesktop().open(dir)
                                    }
                                }
                            }
                        },
                        onOpenImage = { img ->
                            scope.launch {
                                runCatching {
                                    java.awt.Desktop.getDesktop().open(java.io.File(img.path))
                                }
                            }
                        },
                        onOpenImageDetail = { img ->
                            if (img.fileName.startsWith("pixiv_")) {
                                val stem = img.fileName.substringBeforeLast('.')
                                val id = if ("_p" in stem) stem.substringBefore("_p").removePrefix("pixiv_") else stem.removePrefix("pixiv_")
                                val illustId = id.toLongOrNull()
                                if (illustId != null) {
                                    // 下载记录里没有"列"：把账本清掉，免得旧墙的列冒充这一屏的顺序。
                                    DesktopPixivWorkTrail.clear()
                                    route = pixivWorkRouteOf(
                                        illustId = illustId,
                                        title = "pixiv #$illustId",
                                        coverUrl = "",
                                        from = DesktopRoute.Files,
                                    )
                                }
                            }
                        },
                        onCopyImageToClipboard = { img ->
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    runCatching {
                                        val bytes = java.io.File(img.path).readBytes()
                                        com.venera.desktop.gallery.data.DesktopPixivImageBytes.copyToClipboard(bytes)
                                        ux.toast("已复制图片到系统剪贴板")
                                    }.onFailure { ux.toast("复制失败：${it.message}") }
                                }
                            }
                        },
                        onDeleteImages = { list ->
                            scope.launch {
                                val (ok, bad) = withContext(Dispatchers.IO) {
                                    com.venera.desktop.downloads.deleteDesktopLocalImages(list.flatMap { it.allPaths })
                                }
                                filesUi.imageItems = withContext(Dispatchers.IO) {
                                    com.venera.desktop.downloads.scanDesktopLocalImages(
                                        galleryPaths,
                                        galleryPrefs?.downloadDir?.value,
                                    )
                                }
                                filesUi.imageNote = com.venera.desktop.downloads.desktopImageDeleteReceipt(ok, bad)
                            }
                        },
                        onOpenSettingsDownloads = {
                            openSettings()
                        },
                        onBack = { route = DesktopRoute.Tab },
                    )

                    DesktopRoute.Queue -> DesktopComicQueueScreen(
                        mode = layoutMode,
                        tasks = queueTasks,
                        primed = queuePrimed,
                        note = filesUi.queueNote,
                        concurrency = queueConcurrency,
                        onStop = { task ->
                            DesktopComicDownloadQueue.requestStop(task.taskId)
                            filesUi.queueNote = desktopComicQueueStoppedLine(task)
                        },
                        onRemove = { task ->
                                    val root = downloadsRoot
                                    if (root == null) filesUi.queueNote = "下载根目录拿不到 ⇒ 这一笔撤不掉"
                                    else filesUi.queueNote = desktopComicQueueRemovedLine(
                                        task,
                                        DesktopComicDownloadQueue.drop(root, task.taskId),
                                    )
                        },
                        onBack = { route = DesktopRoute.Tab },
                        expandedTaskId = filesUi.queueExpandedTaskId,
                        onToggleExpand = { t ->
                            filesUi.queueExpandedTaskId = if (filesUi.queueExpandedTaskId == t.taskId) null else t.taskId
                        },
                        detailFiles = filesUi.queueDetailFiles,
                        detailLoading = filesUi.queueDetailLoading,
                        onOpenDir = { t ->
                            val root = downloadsRoot
                            if (root == null) filesUi.queueNote = "下载根目录拿不到 ⇒ 目录开不了"
                            else scope.launch {
                                runCatching {
                                    java.awt.Desktop.getDesktop().open(
                                        com.venera.desktop.gallery.data.desktopComicQueueDir(root, t),
                                    )
                                }
                            }
                        },
                    )


                    DesktopRoute.Downloads -> DesktopComicDownloadsScreen(
                        mode = layoutMode,
                        listing = filesUi.downloadsListing,
                        note = filesUi.downloadsNote,
                        armedKey = armedDeleteKey,
                        // 「补齐这一话」：只把那一案排进队列，盘上一颗文件都不动 ——
                        // 真正的重读由上面那颗 effect 的终局键负责（按下那一刻重数只会念出同一句「缺 N 页」）。
                        onRepair = downloadsRepairAction,
                        onDelete = { ch -> onDownloadsDelete(ch, false) },
                        onOpenChapterDir = openDownloadedChapterDir,
                        onBack = {
                            // 出了那一屏就把武装那格交回去：停在"已武装"出去转一圈再回来，
                            // 第二按就删的是当初没点过的那一话（批 77 收藏清空同一口径）。
                            armedDeleteKey = null
                            route = DesktopRoute.Tab
                        },
                    )

                    // 本地漫画屏仅消费壳层扫描结果，零 IO；无下载根目录时隐藏入口。
                    DesktopRoute.LocalComics -> DesktopLocalComicLibraryScreen(
                        mode = layoutMode,
                        scan = localComicScan,
                        note = filesUi.localComicNote,
                        onBack = { route = DesktopRoute.Tab },
                        // 导入把手：弹 AWT 文件框（壳层的窗口副作用，屏不碰）→ 把选中的文件
                        // 交给上面那颗 effect（解包落盘 + tick 重数）。与合并页共用同一枚
                        // `localImportPickAction`（FileDialog 块只在上面写一遍）。
                        onImportPick = localImportPickAction,
                        busy = filesUi.localComicImportBusy,
                        // 「阅读」把手：只挂 sourceKey == imported 的行（归档自含页图，不依赖引擎）。
                        onOpenImported = { comic ->
                            route = DesktopRoute.LocalComicReader(
                                title = comic.title,
                                rootPath = comic.rootPath,
                                startPage = 0,
                            )
                        },
                    )

                    // 导入漫画阅读器直接按本地 .jpg 页表走 DISK Fetcher，不经过引擎。
                    is DesktopRoute.LocalComicReader -> {
                        val root = downloadsRoot
                        val (effectiveDir, pageFiles) = remember(r.rootPath) {
                            val base = java.io.File(r.rootPath)
                            if (!base.isDirectory) return@remember null to emptyList()
                            val imageExts = setOf("jpg", "jpeg", "png", "webp", "gif", "avif")
                            val rootImages = base.listFiles { f ->
                                f.isFile && f.length() > 0 && f.extension.lowercase(java.util.Locale.ROOT) in imageExts
                            }?.sortedBy { it.name }.orEmpty()
                            if (rootImages.isNotEmpty()) {
                                return@remember base to rootImages
                            }
                            val subDirs = base.listFiles { f -> f.isDirectory }?.sortedBy { it.name }.orEmpty()
                            for (sub in subDirs) {
                                val subImages = sub.listFiles { f ->
                                    f.isFile && f.length() > 0 && f.extension.lowercase(java.util.Locale.ROOT) in imageExts
                                }?.sortedBy { it.name }.orEmpty()
                                if (subImages.isNotEmpty()) {
                                    return@remember sub to subImages
                                }
                            }
                            null to emptyList()
                        }
                        val localPages = pageFiles.size
                        // 导入漫画翻页同样写入 comic_history（含书名、本地封面与进度），内部使用 NonCancellable 确保离屏写完。
                        LaunchedEffect(r.rootPath, detailUi.readerPage, localPages) {
                            val write = writeHistory ?: return@LaunchedEffect
                            if (localPages == 0) return@LaunchedEffect
                            val coverFile = effectiveDir?.let { dir ->
                                listOf("cover.jpg", "cover.png", "cover.webp")
                                    .map { java.io.File(dir, it) }
                                    .firstOrNull { it.isFile }?.absolutePath
                            }.orEmpty()
                            val progress = com.venera.desktop.gallery.data.DesktopReadingProgress(
                                comicId = r.title,
                                title = r.title,
                                sourceKey = "imported",
                                chapterTitle = effectiveDir?.name ?: "全一话",
                                chapterIndex = 0,
                                pageIndex = detailUi.readerPage,
                                totalPages = localPages,
                                coverUrl = coverFile,
                            )
                            com.venera.desktop.gallery.data.desktopWriteReadingHistory(
                                progress = progress,
                                nowMillis = System.currentTimeMillis(),
                                save = write,
                            )
                        }
                        DesktopReaderScreen(
                            pages = pageFiles.mapIndexed { index, file ->
                                DesktopComicPage(
                                    sourceKey = "imported",
                                    comicId = effectiveDir?.parentFile?.name ?: r.title,
                                    episodeId = effectiveDir?.name ?: "ch_0",
                                    imageKey = file.name,
                                    page = index,
                                    engine = null,
                                    localDir = effectiveDir,
                                )
                            },
                            episodeLabel = effectiveDir?.name ?: "全一话",
                            note = if (root == null) "下载目录不可用" else if (localPages == 0) "未找到漫画页面图片" else null,
                            masked = false,
                            currentPage = detailUi.readerPage,
                            startPage = r.startPage,
                            onPageChange = { DesktopPetCueBus.emit(DesktopPetCue.TURN_PAGE); detailUi.readerPage = it },
                            onBack = { route = r.from ?: DesktopRoute.Files },
                        )
                    }

                    // 画廊浏览历史屏零 IO 零网络，读档、记行与回原卡取图均由壳层回调承担。
                    is DesktopRoute.ComicComments -> DesktopCommentsScreen(
                        mode = layoutMode,
                        title = r.title,
                        view = detailUi.commentsView,
                        onLoadMore = { commentsTick++ },
                        // 返回 = 原样把详情路由拼回来（评论屏是独立二级页，不像对岸那枚浮层背后详情还留着）。
                        onBack = {
                            route = DesktopRoute.Detail(
                                from = r.from,
                                comicId = r.comicId,
                                title = r.title,
                                cover = r.cover,
                                sourceKey = r.sourceKey,
                            )
                        },
                    )

                    // 画廊那一域的标签搜索：从墙顶栏那枚入口进，与
                    // Queue / Downloads / History 同一族（二级页路由，由对应域顶栏入口进）。
                    is DesktopRoute.BlockingRules -> com.venera.desktop.settings.DesktopBlockingRulesScreen(
                        type = r.type,
                        guard = galleryGuard,
                        onBack = { route = DesktopRoute.Settings },
                    )

                    // ── ：图库作品详情页（对齐 Pixiv 详情页与安卓端信息架构）──
        else -> Unit
    }
}
