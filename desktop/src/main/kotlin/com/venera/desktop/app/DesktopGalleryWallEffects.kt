package com.venera.desktop.app

import com.venera.desktop.ui.BackupUiState
import com.venera.desktop.ui.DAILY_PIXIV_MODES
import com.venera.desktop.ui.DailyGalleryUiState
import com.venera.desktop.ui.GalleryAuxUiState
import com.venera.desktop.ui.NetFavUiState
import com.venera.desktop.ui.PixivDiscoverLeg
import com.venera.desktop.ui.PixivPreviewLeg
import com.venera.desktop.ui.SourceManageUiState
import com.venera.desktop.ui.currentWallQuality
import com.venera.desktop.ui.putBoundedMapEntry
import com.venera.desktop.data.desktopEngineCall
import com.venera.desktop.data.DesktopReadingStats
import com.venera.desktop.data.desktopBackupScreenReading
import com.venera.desktop.data.desktopReadLocalAssetCounts
import com.venera.desktop.data.desktopReadingStatsScreenReading
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
internal fun DesktopGalleryWallEffects(
    ports: GalleryPorts,
    engine: com.venera.engineprobe.EngineSession?,
    galleryPaths: com.venera.compose.data.platform.PathProvider?,
    galleryPrefs: com.venera.desktop.gallery.data.DesktopGalleryPreferences?,
    currentMaskMode: String,
    maskModeOf: () -> String,
    wallGuardOf: () -> com.venera.compose.gallery.data.GalleryContentGuard?,
    wallBlockAiOf: () -> Boolean,
    pixivAuthState: com.venera.compose.gallery.data.PixivAuthState,
    galleryFavorites: List<com.venera.compose.gallery.data.GalleryFavorite>,
    galleryWallQuality: GalleryPreviewQuality,
    wallCache: androidx.compose.runtime.snapshots.SnapshotStateMap<String, DesktopWallPage>,
    wallCacheKey: String,
    cards: List<DesktopWallCardModel>,
    pixivPreviewCache: androidx.compose.runtime.snapshots.SnapshotStateMap<com.venera.compose.gallery.data.PixivRankingMode, PixivPreviewLeg>,
    tagDictionary: com.venera.desktop.gallery.data.DesktopGalleryTagDictionary?,
    readingStats: DesktopReadingStats,
    backupUi: BackupUiState,
    netFavUi: NetFavUiState,
    sourceUi: SourceManageUiState,
    galleryAuxUi: GalleryAuxUiState,
    dailyGallery: DailyGalleryUiState,
    dailyCore: com.venera.compose.gallery.ui.GalleryDailyCore,
    routeState: MutableState<DesktopRoute>,
    currentTabState: MutableState<VeneraNavTab>,
    favoritesState: MutableState<List<com.venera.compose.data.db.FavoriteRecord>>,
    favoritesSourceKeyState: MutableState<String>,
    galleryWallCardsState: MutableState<List<DesktopWallCardModel>>,
    galleryWallBlockedState: MutableIntState,
    galleryWallUnrenderableState: MutableState<String>,
    galleryWallTickState: MutableIntState,
    homeRetryTickState: MutableState<Int>,
    wallPageState: MutableState<DesktopWallPage?>,
    galleryHomeDomainState: MutableState<GalleryHomeDomain>,
    galleryRankingModeState: MutableState<com.venera.compose.gallery.data.PixivRankingMode>,
    gallerySortState: MutableState<DesktopGallerySort>,
    wallRefreshTickState: MutableIntState,
    lastAppliedRefreshTickState: MutableIntState,
    wallRefreshingState: MutableState<Boolean>,
    wallPhaseState: MutableState<DesktopGalleryMorePhase>,
    wallMoreTickState: MutableIntState,
    pixivPreviewLegState: MutableState<PixivPreviewLeg>,
    lastAppliedPreviewTickState: MutableIntState,
    pixivDiscoverLegState: MutableState<PixivDiscoverLeg>,
    pixivLocalFollowedState: MutableState<Set<Long>>,
) {
    var route by routeState
    var currentTab by currentTabState
    var favorites by favoritesState
    var favoritesSourceKey by favoritesSourceKeyState
    var galleryWallCards by galleryWallCardsState
    var galleryWallBlocked by galleryWallBlockedState
    var galleryWallUnrenderable by galleryWallUnrenderableState
    var galleryWallTick by galleryWallTickState
    var homeRetryTick by homeRetryTickState
    var wallPage by wallPageState
    var galleryHomeDomain by galleryHomeDomainState
    var galleryRankingMode by galleryRankingModeState
    var gallerySort by gallerySortState
    var wallRefreshTick by wallRefreshTickState
    var lastAppliedRefreshTick by lastAppliedRefreshTickState
    var wallRefreshing by wallRefreshingState
    var wallPhase by wallPhaseState
    var wallMoreTick by wallMoreTickState
    var pixivPreviewLeg by pixivPreviewLegState
    var lastAppliedPreviewTick by lastAppliedPreviewTickState
    var pixivDiscoverLeg by pixivDiscoverLegState
    var pixivLocalFollowed by pixivLocalFollowedState
    LaunchedEffect(favoritesSourceKey, galleryFavorites.size, galleryWallTick, currentMaskMode, route) {
        // 两处消费这面墙：收藏 hub 的「画廊」档，画廊域的收藏二级页。
        if (favoritesSourceKey != com.venera.desktop.favorites.FAVORITES_SOURCE_GALLERY &&
            route !is DesktopRoute.GalleryFavorites
        ) {
            return@LaunchedEffect
        }
        val attempt = withContext(Dispatchers.IO) {
            runCatching {
                val projection = com.venera.desktop.gallery.ui.projectFavorites(galleryFavorites)
                val cards = com.venera.desktop.gallery.ui.desktopGalleryFavoritesWallCards(
                    projection.posts,
                    currentWallQuality(galleryPrefs),
                    wallGuardOf(),
                    maskModeOf(),
                    wallBlockAiOf(),
                )
                Triple(projection, cards, projection.posts.size - cards.size)
            }
        }
        attempt.onSuccess { (projection, cards, blocked) ->
            galleryWallCards = cards
            galleryWallBlocked = blocked
            galleryWallUnrenderable = if (projection.unrecognizedCount > 0 || projection.droppedUnusable > 0) {
                projection.droppedLabel + " —— 记录还在存档里，界面上画不出来。"
            } else {
                ""
            }
            println("D_收藏墙 投=${cards.size} 挡=$blocked 存档=${projection.total}")
        }.onFailure {
            galleryWallUnrenderable = "这一屏没投出来：${it.javaClass.simpleName}"
        }
    }

        // 网络收藏三步取数（能力探测 → 按 loadComics/loadNext 能力取页 → 单开手风琴），全程走 desktopEngineCall 串行锁。
    LaunchedEffect(favoritesSourceKey, engine, sourceUi.sourceRows, currentTab) {
        // 分段默认落「网络」后，探测不能跟着开机就跑（逐颗 load 34 份源太贵）——
        // 仍然只在**站在收藏那一格**时才探（与从前"切到网络段才探"同一份懒加载）。
        if (currentTab != VeneraNavTab.FAVORITES) return@LaunchedEffect
        // 本地/画廊档也触发一次网络源探测（已探测则短路），避免默认落本地档时网络源列表恒空。
        if ((favoritesSourceKey == com.venera.desktop.favorites.FAVORITES_SOURCE_LOCAL ||
                favoritesSourceKey == com.venera.desktop.favorites.FAVORITES_SOURCE_GALLERY) &&
            netFavUi.netFavSources != null
        ) return@LaunchedEffect
        val cacheFile = galleryPaths?.let { java.io.File(it.cacheRoot, "net_fav_sources.json") }
        if (netFavUi.netFavSources == null && cacheFile?.isFile == true) {
            val cachedRows = withContext(Dispatchers.IO) {
                runCatching {
                    val type = object : com.google.gson.reflect.TypeToken<List<com.venera.desktop.favorites.NetFavSourceRow>>() {}.type
                    com.google.gson.Gson().fromJson<List<com.venera.desktop.favorites.NetFavSourceRow>>(cacheFile.readText(), type)
                }.getOrNull()
            }
            if (!cachedRows.isNullOrEmpty()) {
                netFavUi.netFavSources = cachedRows
                if (netFavUi.netFavExpandedKey == null) {
                    netFavUi.netFavExpandedKey = cachedRows.firstOrNull { it.logged }?.key ?: cachedRows.firstOrNull()?.key
                }
            }
        }
        // 若源清单尚未加载回来，等待下一次 sourceRows 更新，绝不提前置为 emptyList
        if (engine == null || sourceUi.sourceRows.isEmpty()) {
            return@LaunchedEffect
        }
        val rows = withContext(Dispatchers.IO) {
            runCatching {
                sourceUi.sourceRows.filter { it.enabled }.mapNotNull { row ->
                    // load 与读能力合进**同一把锁的一趟**（本仓"load 后紧跟动作、两跳之间会
                    // 被别的调用点抢走 window['temp_source']"那条锁纪律）。
                    val probe = desktopEngineCall(engine) {
                        row.key to (engine.load(row.key) to engine.hasNetworkFavorites())
                    }.getOrNull() ?: return@mapNotNull null
                    val (loadedName, has) = probe.second
                    if (!has) return@mapNotNull null
                    val logged = runCatching { engine.host.isLogged(row.key) }.getOrDefault(false)
                    com.venera.desktop.favorites.NetFavSourceRow(key = row.key, name = loadedName, logged = logged)
                }
            }.getOrDefault(emptyList())
        }
        netFavUi.netFavSources = rows
        if (netFavUi.netFavExpandedKey == null) {
            netFavUi.netFavExpandedKey = rows.firstOrNull { it.logged }?.key ?: rows.firstOrNull()?.key
        }
        if (rows.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                runCatching { cacheFile?.writeText(com.google.gson.Gson().toJson(rows)) }
            }
        }
        println("D_网络收藏 源=${rows.size} 登录=${rows.count { it.logged }} 默认选中=${netFavUi.netFavExpandedKey}")
    }

    LaunchedEffect(netFavUi.netFavExpandedKey, netFavUi.netFavFolderPicked, netFavUi.netFavReloadTick) {
        val key = netFavUi.netFavExpandedKey ?: return@LaunchedEffect
        val sources = netFavUi.netFavSources ?: return@LaunchedEffect
        val src = sources.firstOrNull { it.key == key } ?: return@LaunchedEffect
        if (!src.logged) return@LaunchedEffect
        netFavUi.netFavPage = null
        val session = engine ?: return@LaunchedEffect
        val outcome = desktopEngineCall(session) {
            session.load(key)
            val caps = session.networkFavoritesCaps()
            val folderId = if (caps.multiFolder) {
                // 多夹源:夹清单是展开那一趟现取的(comicId=null = 全部夹),选中格走 picked(**夹 id**) ?: 第一个。
                val folders = runCatching { session.networkFavoritesFolders() }.getOrDefault(emptyList())
                val picked =
                    netFavUi.netFavFolderPicked?.takeIf { p -> folders.any { it.first == p } } ?: folders.firstOrNull()?.first
                netFavUi.netFavFolders = com.venera.desktop.favorites.NetFavFoldersState(
                    folders = folders.map {
                        com.venera.desktop.favorites.NetFavFolderRow(id = it.first, name = it.second)
                    },
                    selectedId = picked,
                )
                picked
            } else {
                netFavUi.netFavFolders = null
                null
            }
            // 网络收藏翻页按能力位（loadComics vs loadNext）分派，不可按 multiFolder 分流。
            when {
                caps.hasLoadComics -> session.networkFavoritesLoadComics(1, folderId)
                caps.hasLoadNext -> session.networkFavoritesLoadNext(null, folderId)
                else -> com.venera.engineprobe.EngineSession.Page(
                    comics = emptyList(),
                    error = "该源未声明 favorites.loadComics / favorites.loadNext",
                )
            }
        }
        val page = outcome.getOrNull()
        // 首屏那一趟把续页账也落下来（游标 / 页码两形折平）。
        netFavUi.netFavNext = page?.next
        netFavUi.netFavPageNo = 1
        netFavUi.netFavMaxPage = page?.maxPage
        netFavUi.netFavPage = if (page == null) {
            com.venera.desktop.favorites.NetFavPageState(
                comics = emptyList(),
                error = outcome.exceptionOrNull()?.message?.take(160) ?: "取数失败",
            )
        } else {
            com.venera.desktop.favorites.NetFavPageState(
                comics = page.comics.map {
                    com.venera.desktop.favorites.NetFavComicRow(
                        sourceKey = key,
                        comicId = it.id,
                        title = it.title,
                        cover = it.cover,
                    )
                },
                error = page.error,
                hasMore = page.next != null || ((page.maxPage ?: 1) > 1),
            )
        }
        println("D_网络收藏 源=$key 本=${page?.comics?.size ?: 0} 失败=${page?.error ?: "无"}")
    }

    // 网络收藏续页按游标或页码追加，并按 comicId 去重防止站方忽略页码时重复展示。
    LaunchedEffect(netFavUi.netFavMoreTick) {
        if (netFavUi.netFavMoreTick == 0) return@LaunchedEffect
        if (netFavUi.netFavLoadingMore) return@LaunchedEffect
        val key = netFavUi.netFavExpandedKey ?: return@LaunchedEffect
        val current = netFavUi.netFavPage ?: return@LaunchedEffect
        val src = netFavUi.netFavSources?.firstOrNull { it.key == key } ?: return@LaunchedEffect
        if (!src.logged) return@LaunchedEffect
        val session = engine ?: return@LaunchedEffect
        val cursor = netFavUi.netFavNext
        val nextPage = netFavUi.netFavPageNo + 1
        if (cursor == null && nextPage > (netFavUi.netFavMaxPage ?: nextPage - 1)) return@LaunchedEffect
        val folderId = netFavUi.netFavFolders?.selectedId
        netFavUi.netFavLoadingMore = true
        val outcome = desktopEngineCall(session) {
            session.load(key)
            if (cursor != null) {
                session.networkFavoritesLoadNext(cursor, folderId)
            } else {
                session.networkFavoritesLoadComics(nextPage, folderId)
            }
        }
        netFavUi.netFavLoadingMore = false
        // 取回来时用户可能已换了源/夹：那就不把旧源的下一页塞进新源的列表（串源）。
        if (netFavUi.netFavExpandedKey != key) return@LaunchedEffect
        outcome.fold(
            onSuccess = { page ->
                val fresh = page.comics.map {
                    com.venera.desktop.favorites.NetFavComicRow(
                        sourceKey = key,
                        comicId = it.id,
                        title = it.title,
                        cover = it.cover,
                    )
                }.filterNot { row -> current.comics.any { it.comicId == row.comicId } }
                netFavUi.netFavNext = page.next
                if (cursor == null) netFavUi.netFavPageNo = nextPage
                if (page.maxPage != null) netFavUi.netFavMaxPage = page.maxPage
                val moreLeft = if (cursor != null) page.next != null
                else nextPage < (page.maxPage ?: nextPage)
                netFavUi.netFavPage = current.copy(
                    comics = current.comics + fresh,
                    error = page.error,
                    hasMore = moreLeft,
                )
                println("D_网络收藏续页 源=$key 新增=${fresh.size} 总=${current.comics.size + fresh.size} 还有=$moreLeft")
            },
            onFailure = { t ->
                netFavUi.netFavPage = current.copy(error = "续页失败：${t.javaClass.simpleName} " + (t.message?.take(120) ?: ""))
            },
        )
    }
    LaunchedEffect(route, readingStats) {
        val s = readingStats
        if (route is DesktopRoute.ReadingStats && s.loaded) {
            println(
                desktopReadingStatsScreenReading(
                    loaded = s.loaded, note = s.note, trendCount = s.trend.size,
                    topCount = s.topComics.size, todayPages = s.todayPages,
                )
            )
        }
    }

    LaunchedEffect(route, backupUi.assetRefreshTick) {
        if (route is DesktopRoute.Backup) {
            backupUi.assetCounts = desktopReadLocalAssetCounts(galleryPaths)
            galleryPaths?.let { p ->
                backupUi.davConfig = com.venera.desktop.platform.DesktopWebDavSync.readConfig(p)
                backupUi.stagingFile = com.venera.desktop.platform.DesktopWebDavSync.latestStagingFile(p)
                com.venera.desktop.platform.DesktopWebDavSync.loadLastSync(p)
            }
        }
    }

    // 备份与还原那一屏的取证读数。键里带三枚状态（counts/restore/note）——
    // 那三枚各管"上一步跑到哪儿了"，任何一枚变了都该重打一次；只挂 route 会漏掉"进屏后跑
    // 完一趟"那一档（读数永远是「还没动过」）。与 §119 那颗历史读数 effect 同族纪律。
    LaunchedEffect(route, backupUi.lastCounts, backupUi.lastRestore, backupUi.note) {
        if (route is DesktopRoute.Backup) {
            println(
                desktopBackupScreenReading(
                    counts = backupUi.lastCounts,
                    restore = backupUi.lastRestore,
                    path = backupUi.lastPath,
                    note = backupUi.note,
                )
            )
        }
    }


    // 详情页评论能力探测与分页拉取各占一趟独立 engineCall（均以 load 开头），防止并发抢占 temp_source 并满足行首扫描规则。
    LaunchedEffect(ports, galleryHomeDomain, galleryRankingMode, gallerySort, wallRefreshTick, pixivAuthState, currentMaskMode) {
        val key = wallCacheKey
        // 刷新扳机按过（tick 变了）⇒ 强制重取当前档并顶掉缓存；没按过而这一档已在缓存 ⇒ 摆缓存、零网络。
        val isRefresh = wallRefreshTick != lastAppliedRefreshTick
        if (isRefresh) wallCache.remove(key)
        // 手动刷新且已有墙 ⇒ 旧墙留屏、只亮进度，继续往下重取（不闪空、也不提前 return）。
        // 其余（首屏/切域/切档）⇒ 命中缓存即摆并回；未命中先置 null 再发车，绝不留别域旧图。
        val oldWallOnRefresh = if (isRefresh) wallPage else null
        if (oldWallOnRefresh == null) {
            wallPage = wallCache[key]
            if (wallPage != null) {
                lastAppliedRefreshTick = wallRefreshTick
                wallRefreshing = false
                return@LaunchedEffect
            }
        }
        val loaded = runCatching {
            if (galleryHomeDomain == GalleryHomeDomain.PIXIV) {
                val signedIn = pixivAuthState is com.venera.compose.gallery.data.PixivAuthState.SignedIn
                val rec = if (signedIn) {
                    desktopPixivRecommendedFirstPage(
                        ports = ports,
                        quality = galleryWallQuality,
                        guard = wallGuardOf(),
                        maskMode = maskModeOf(),
                        blockAi = wallBlockAiOf(),
                        bypassCache = isRefresh,
                    )
                } else {
                    null
                }
                // 未登录 / 端口缺位 / 推荐腿塌了 ⇒ 落回匿名榜单腿：屏上仍是一面真取了数的 pixiv 墙。
                rec ?: desktopPixivWallFirstPage(
                    ports = ports,
                    mode = galleryRankingMode,
                    quality = galleryWallQuality,
                    // 守卫与 booru 那条共用同一颗起口（安全评审 B：第二条取数路不许绕过去）。
                    guard = wallGuardOf(),
                    maskMode = maskModeOf(),
                    blockAi = wallBlockAiOf(),
                    bypassCache = isRefresh,
                )
            } else if (gallerySort == DesktopGallerySort.RECOMMENDED) {
                // 推荐 = 每日热门那颗三站混排件（桌面唯一说得通"推荐"的出处，判据不重述）。
                desktopWallRecommendedFirstPage(
                    ports = ports,
                    quality = galleryWallQuality,
                    guard = wallGuardOf(),
                    maskMode = maskModeOf(),
                    blockAi = wallBlockAiOf(),
                )
            } else if (gallerySort == DesktopGallerySort.LATEST) {
                val configured = com.venera.desktop.gallery.ui.desktopGelbooruConfigured(ports)
                desktopWallFirstPage(
                    ports = ports,
                    gelbooruConfigured = configured,
                    // 首屏与续页均必须传入内容守卫，否则取数层会跳过遮罩判定。
                    quality = galleryWallQuality,
                    guard = wallGuardOf(),
                    maskMode = maskModeOf(),
                    blockAi = wallBlockAiOf(),
                )
            } else {
                // 最热 = 逐站点名的高分后缀（`order:score` / `sort:score:desc`，两站写法
                // 不能互抄）；Gelbooru 没配凭据那一腿**发车前就剔**，原因照 silent 交进账本。
                val configured = com.venera.desktop.gallery.ui.desktopGelbooruConfigured(ports)
                desktopWallFirstPage(
                    ports = ports,
                    queries = desktopWallSortQueries(DesktopGallerySort.HOTTEST)
                        .orEmpty()
                        .filterKeys { it != GallerySite.GELBOORU || configured },
                    silent = desktopWallSilentSites(configured),
                    quality = galleryWallQuality,
                    guard = wallGuardOf(),
                    maskMode = maskModeOf(),
                    blockAi = wallBlockAiOf(),
                )
            }
        }.onFailure {
            if (it is kotlinx.coroutines.CancellationException) throw it
            // 取数整条路塌了要念出来：空墙静默是最难查的那种失败。
            println("D_取数 首屏失败 ${it::class.java.simpleName}: ${it.message?.take(200)}")
        }.getOrNull()
        // 塌了那一档**不写回空墙**：交回 null（还没到货）而不是"到货了而零张"，
        // 屏上那句「这一面墙还是空的」因此仍然成立，而页尾不会去数一份不存在的账。
        // 刷新失败留旧墙（旧页还在、只是这轮没换成）；首屏/切档失败仍是 null（还没到货）。
        wallPage = loaded ?: oldWallOnRefresh
        // 到货**且有真卡**才进缓存（超时塌出来的空页一旦进缓存，那一档就
        // 永远摆缓存空页 = 永远转圈/空墙；空档切回来重取一次还能自愈，不该占位）。
        // 续页翻出来的新段仍由续页那颗 effect 同步进缓存（见 `wallMoreTick` 那一支）。
        if (loaded != null && loaded.cards.isNotEmpty()) {
            putBoundedMapEntry(wallCache, key, loaded, maxEntries = 8)
        }
        lastAppliedRefreshTick = wallRefreshTick
        wallRefreshing = false
        println("D_取数 上屏=${loaded?.cards?.size ?: "还没到货"} 张")
    }
    LaunchedEffect(wallMoreTick) {
        if (wallMoreTick == 0) return@LaunchedEffect
        val page = wallPage
        // 四道早退里"摆不摆把手"那一条**不在这里重拼**：交 [desktopWallMoreAllowed]
        // （页尾那颗件吃的是同一颗）。在这里再写一遍 `atEnd ||` 就是同一件事两个出处 ——
        // 把「Failed」折进「End」那一档时红过的那两条，写的正是这一族。
        if (page == null || wallPhase is DesktopGalleryMorePhase.Loading) return@LaunchedEffect
        if (!desktopWallMoreAllowed(page.batch, wallPhase)) return@LaunchedEffect
        wallPhase = DesktopGalleryMorePhase.Loading
        // 续页仅沿用首屏已发出的 queries（不重算已拦截站点），BOORU 与 PIXIV 榜单共用 desktopWallPageAfter 落地。
        val got = runCatching {
            // 续页三路分派：Pixiv 推荐走游标腿直接返回新页（不走 desktopWallPageAfter 防重复合并），Pixiv 榜单与 Booru 共用落地函数。
            if (page.isRecommended) {
                fetchPixivRecommendedNext(
                    ports = ports,
                    base = page,
                    guard = wallGuardOf(),
                    maskMode = maskModeOf(),
                    blockAi = wallBlockAiOf(),
                ).getOrThrow()
            } else {
                val next = if (galleryHomeDomain == GalleryHomeDomain.PIXIV) {
                    fetchPixivWallPage(ports, page.batch, mode = galleryRankingMode).getOrThrow()
                } else {
                    fetchWallPage(ports, page.batch, page.batch.queries).getOrThrow()
                }
                desktopWallPageAfter(
                    base = page,
                    next = next,
                    guard = wallGuardOf(),
                    maskMode = maskModeOf(),
                    blockAi = wallBlockAiOf(),
                )
            }
        }
        got.fold(
            onSuccess = { newPage ->
                // 三腿都交回**整颗新页**（推荐游标腿自己并过段，另两条走 `desktopWallPageAfter`）。
                wallPage = newPage
                // 续页翻出来的新段同步进缓存（键 = 上面那颗唯一键 `wallCacheKey`，
                // 与首屏读同一把）：切走再切回来吃的是"含已翻页"那一颗，而不是退回首屏。
                putBoundedMapEntry(wallCache, wallCacheKey, newPage, maxEntries = 8)
                wallPhase = DesktopGalleryMorePhase.Idle
            },
            onFailure = { t ->
                if (t is kotlinx.coroutines.CancellationException) throw t
                // 失败那一档**不撤**屏上那一批卡（追加语义只掉这一笔），也只换相位：
                // 那一句「已经摆出来的这些一张不少」由页尾那颗件念，这里不另拼一句。
                wallPhase = DesktopGalleryMorePhase.Failed(
                    "${t.javaClass.simpleName}: ${t.message?.take(160) ?: ""}",
                )
            },
        )
        println(desktopGalleryWallFooter(wallPage?.batch, wallPage?.cards?.size ?: 0, wallPhase, "张").reading)
    }
    LaunchedEffect(ports, wallRefreshTick, currentMaskMode, homeRetryTick) {
        if (dailyGallery.homePixivCards.isNotEmpty() && dailyGallery.homePixivLoadedTick.intValue == wallRefreshTick) return@LaunchedEffect
        dailyGallery.homePixivLoadedTick.intValue = wallRefreshTick
        val page = runCatching {
            com.venera.desktop.gallery.ui.desktopPixivWallFirstPage(
                ports = ports,
                mode = com.venera.compose.gallery.data.PixivRankingMode.DAILY,
                quality = galleryWallQuality,
                guard = wallGuardOf(),
                maskMode = maskModeOf(),
                blockAi = wallBlockAiOf(),
            )
        }.getOrNull()
        dailyGallery.homePixivCards = page?.cards.orEmpty()
    }
    LaunchedEffect(ports, wallRefreshTick, currentMaskMode, homeRetryTick) {
        if (dailyGallery.homeCards.isNotEmpty() && dailyGallery.homeLoadedTick.intValue == wallRefreshTick) return@LaunchedEffect
        dailyGallery.homeLoadedTick.intValue = wallRefreshTick
        // `onPartial` 每站到货先上屏，快站不必空等最慢那站。
        runCatching {
            desktopDailyHomeCards(
                ports = ports,
                seed = System.currentTimeMillis(),
                quality = galleryWallQuality,
                guard = wallGuardOf(),
                maskMode = maskModeOf(),
                blockAi = wallBlockAiOf(),
                onPartial = { partial ->
                    if (partial.isNotEmpty()) dailyGallery.homeCards = partial
                },
            )
        }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { dailyGallery.homeCards = it }
        dailyGallery.homeFailed = dailyGallery.homeCards.isEmpty()
    }
    LaunchedEffect(ports, galleryHomeDomain, galleryRankingMode, wallRefreshTick, currentMaskMode) {
        if (galleryHomeDomain != GalleryHomeDomain.PIXIV) return@LaunchedEffect
        val isPreviewRefresh = wallRefreshTick != lastAppliedPreviewTick
        if (isPreviewRefresh) {
            pixivPreviewCache.clear()
        }
        val cachedPreview = if (!isPreviewRefresh) pixivPreviewCache[galleryRankingMode] else null
        if (cachedPreview != null) {
            pixivPreviewLeg = cachedPreview
            return@LaunchedEffect
        }
        // 主人口径「榜首这里的卡片应该和下面的一样带有头像和标题」：整颗页拿回来，
        // avatars / titles 两张表跟卡一起走（匿名榜单腿本就带回，零额外往返）。
        // 刷新那一记 bypassCache = true：绕盘缓存真问站方，否则预览条永远摆 TTL 内的旧页。
        val preview = runCatching {
            desktopPixivWallFirstPage(
                ports = ports,
                mode = galleryRankingMode,
                quality = galleryWallQuality,
                guard = wallGuardOf(),
                maskMode = maskModeOf(),
                blockAi = wallBlockAiOf(),
                bypassCache = isPreviewRefresh,
            )
        }.getOrNull()?.let { page ->
            PixivPreviewLeg(page.cards.take(20), page.avatars, page.titles)
        } ?: PixivPreviewLeg(emptyList(), emptyMap(), emptyMap())
        pixivPreviewLeg = preview
        if (preview.cards.isNotEmpty()) {
            putBoundedMapEntry(pixivPreviewCache, galleryRankingMode, preview, maxEntries = 6)
        }
        lastAppliedPreviewTick = wallRefreshTick
    }

    LaunchedEffect(galleryHomeDomain, wallRefreshTick) {
        if (galleryHomeDomain != GalleryHomeDomain.PIXIV) return@LaunchedEffect
        galleryAuxUi.pixivisionArticles = com.venera.desktop.gallery.data.DesktopPixivisionFeed.fetch("zh")
            .getOrDefault(emptyList<com.venera.desktop.gallery.data.DesktopPixivisionArticle>())
    }

    // ── 批 B6：pixivision 文章详情（二级页）──
    // 文章数据住壳（路由 data object 只表「开着哪一屏」）；正文 effect 进页即取，失败 = 转圈不清屏。
    LaunchedEffect(galleryAuxUi.pixivisionArticleOpen) {
        val open = galleryAuxUi.pixivisionArticleOpen ?: return@LaunchedEffect
        galleryAuxUi.pixivisionArticleBody = null
        galleryAuxUi.pixivisionArticleLoading = true
        galleryAuxUi.pixivisionArticleBody = com.venera.desktop.gallery.data.DesktopPixivisionFeed
            .fetchArticle(open.url).getOrNull()
        galleryAuxUi.pixivisionArticleLoading = false
    }

    LaunchedEffect(tagDictionary, wallPage, galleryHomeDomain) {
        if (galleryHomeDomain != GalleryHomeDomain.BOORU) return@LaunchedEffect
        val posts = wallPage?.batch?.posts ?: emptyList()
        if (posts.isEmpty() || tagDictionary == null) {
            galleryAuxUi.tagDimensionGroups = emptyList()
            galleryAuxUi.tagDictReady = tagDictionary != null
            return@LaunchedEffect
        }
        val counts = linkedMapOf<String, Int>()
        for (post in posts) {
            for (raw in post.tagList) {
                val tag = raw.trim()
                if (tag.isNotEmpty()) counts[tag] = (counts[tag] ?: 0) + 1
            }
        }
        val names = counts.entries.sortedByDescending { it.value }.take(60).map { it.key }
        val titled = withContext(Dispatchers.IO) { tagDictionary.titleTags(names) }
        val titledMap = when (val read = titled) {
            is com.venera.desktop.gallery.data.DesktopDictRead.Hit -> read.value
            else -> emptyMap<String, com.venera.compose.gallery.domain.GalleryTitleTag>()
        }
        galleryAuxUi.tagDimensionGroups = desktopTagDimensionsOf(posts, titledMap)
        galleryAuxUi.tagDictReady = true
    }
    LaunchedEffect(
        ports,
        galleryHomeDomain,
        wallRefreshTick,
        ports.pixivAccount?.authState?.value,
    ) {
        if (galleryHomeDomain != GalleryHomeDomain.PIXIV) return@LaunchedEffect
        val account = ports.pixivAccount
        val bearer = account?.bearerOrNull()
        val pixiv = ports.pixiv
        if (bearer == null || pixiv == null) {
            pixivDiscoverLeg = PixivDiscoverLeg(emptyList(), emptySet(), emptyList(), emptyMap(), ready = true)
            return@LaunchedEffect
        }
        val allUsers = runCatching {
            pixiv.recommendedUsers(bearer).getOrNull()?.users.orEmpty()
        }.getOrDefault(emptyList())
        val startIndex = if (allUsers.size > 3) (wallRefreshTick * 3) % allUsers.size else 0
        val users = if (allUsers.isNotEmpty()) allUsers.drop(startIndex) + allUsers.take(startIndex) else emptyList()
        val following = runCatching {
            val works = pixiv.followIllustFeed(
                bearer,
                com.venera.compose.gallery.data.PixivRestrict.Public,
            ).getOrNull()?.works.orEmpty()
            val cards = desktopWallFollowingCards(
                posts = works.map { it.post },
                quality = galleryWallQuality,
                guard = wallGuardOf(),
                maskMode = maskModeOf(),
                blockAi = wallBlockAiOf(),
            )
            cards to works.mapNotNull { work ->
                work.authorAvatarUrl.takeIf { it.isNotBlank() }?.let { work.post.uid to it }
            }.toMap()
        }.getOrElse { emptyList<DesktopWallCardModel>() to emptyMap<String, String>() }
        pixivDiscoverLeg = PixivDiscoverLeg(
            users = users,
            followedIds = pixivLocalFollowed,
            followingCards = following.first,
            followingAvatars = following.second,
            ready = true,
        )
    }

    // 日榜二级页采用桌面编辑台壳 + 共享 GalleryDailyCore/GalleryDailyFeed/buildGalleryWall，滚动状态提升至壳层。
    LaunchedEffect(route, dailyGallery.pixivTab, wallRefreshTick) {
        if (route !is DesktopRoute.GalleryDaily) return@LaunchedEffect
        // 换档重置：卡序、两张表、页码账都归零，旧档的卡绝不许漏进新档。
        dailyGallery.pixivPage = 1
        dailyGallery.pixivHasMore = true
        dailyGallery.pixivLoading = true
        val page = runCatching {
            com.venera.desktop.gallery.ui.desktopPixivWallFirstPage(
                ports = ports,
                mode = DAILY_PIXIV_MODES[dailyGallery.pixivTab],
                quality = galleryWallQuality,
                guard = wallGuardOf(),
                maskMode = maskModeOf(),
                blockAi = wallBlockAiOf(),
            )
        }.getOrNull()
        dailyGallery.pixivCards = page?.cards.orEmpty()
        dailyGallery.pixivAvatars = page?.avatars.orEmpty()
        dailyGallery.pixivTitles = page?.titles.orEmpty()
        dailyGallery.pixivLoading = false
    }
    // pixiv 续页：扳机驱动（编辑台「查看更多」/滚到底），真问站方下一页再过守卫投影。
    LaunchedEffect(dailyGallery.pixivLoadTick) {
        if (dailyGallery.pixivLoadTick == 0) return@LaunchedEffect
        if (route !is DesktopRoute.GalleryDaily) return@LaunchedEffect
        if (!dailyGallery.pixivHasMore || dailyGallery.pixivLoading) return@LaunchedEffect
        dailyGallery.pixivLoading = true
        val nextPage = dailyGallery.pixivPage + 1
        val posts = runCatching {
            ports.pixiv?.ranking(DAILY_PIXIV_MODES[dailyGallery.pixivTab], page = nextPage)?.getOrThrow()
                ?.map { it.post }
        }.getOrNull()
        if (posts != null && posts.isNotEmpty()) {
            dailyGallery.pixivCards = dailyGallery.pixivCards +
                com.venera.desktop.gallery.ui.desktopWallGuardedWall(
                    posts, galleryWallQuality, wallGuardOf(), maskModeOf(), wallBlockAiOf(),
                ).first.filter { it !in dailyGallery.pixivCards }
            dailyGallery.pixivPage = nextPage
            dailyGallery.pixivHasMore = posts.size >= 30
        } else {
            dailyGallery.pixivHasMore = false
        }
        dailyGallery.pixivLoading = false
    }
    // Konachan 热门分腿（order:score 可分页）；进屏首取 + 扳机续页同走这一条。
    LaunchedEffect(route, dailyGallery.konachanPage, dailyGallery.konachanLoadTick, wallRefreshTick) {
        if (route !is DesktopRoute.GalleryDaily) return@LaunchedEffect
        if (dailyGallery.konachanLoading) return@LaunchedEffect
        dailyGallery.konachanLoading = true
        val posts = runCatching {
            ports.boards.konachan.searchPosts(
                "order:score", page = dailyGallery.konachanPage,
                limit = ports.boards.konachan.pageSize,
            ).getOrThrow()
        }.getOrNull().orEmpty()
        val fresh = com.venera.desktop.gallery.ui.desktopWallGuardedWall(
            posts, galleryWallQuality, wallGuardOf(), maskModeOf(), wallBlockAiOf(),
        ).first.filter { it !in dailyGallery.konachanCards }
        if (fresh.isNotEmpty()) dailyGallery.konachanCards = dailyGallery.konachanCards + fresh
        dailyGallery.konachanHasMore = posts.size >= 20
        dailyGallery.konachanLoading = false
    }
    // Yande 每日榜：原始池（dailyCore 榜单腿带回）过同一把守卫投影，与 pixiv/Konachan 卡同形。
    LaunchedEffect(dailyCore.pools, wallRefreshTick) {
        if (route !is DesktopRoute.GalleryDaily) return@LaunchedEffect
        val yande = dailyCore.pools[GallerySite.YANDERE].orEmpty()
        dailyGallery.yandeCards = if (yande.isEmpty()) emptyList() else
            com.venera.desktop.gallery.ui.desktopWallGuardedWall(
                yande, galleryWallQuality, wallGuardOf(), maskModeOf(), wallBlockAiOf(),
            ).first
    }
    // 进屏补一轮（与安卓二级页那条逐字同形：不做缓存判据 —— 用户主动进了这一页，
    // 屏上是缓存的旧一天就该补一次，而不是让他看着昨天的热门以为今天没更新）。
    LaunchedEffect(route, dailyCore) {
        if (route !is DesktopRoute.GalleryDaily) return@LaunchedEffect
        if (dailyCore.feedLoadedKey == "feed#${dailyCore.refreshTick}|${dailyCore.seed}") return@LaunchedEffect
        dailyCore.isLoadingFeed = true
        try {
            com.venera.compose.gallery.domain.GalleryDailyFeed(ports.boards)
                .loadDaily(seed = dailyCore.seed) { landed -> dailyCore.acceptPartial(landed) }
                .onSuccess { daily ->
                    dailyCore.accept(
                        list = daily.merged.posts,
                        day = daily.date,
                        notice = daily.failures.entries
                            .joinToString(" · ") { "${it.key.displayName}：${it.value}" }
                            .takeIf { daily.failures.isNotEmpty() },
                        failures = daily.failures,
                        pools = daily.pools,
                    )
                    dailyCore.feedLoadedKey = "feed#${dailyCore.refreshTick}|${dailyCore.seed}"
                }
                .onFailure { e ->
                    println("D_取数 日榜二级页失败 ${e::class.java.simpleName}: ${e.message?.take(200)}")
                    dailyCore.reject(e)
                }
        } finally {
            // 取消路径也要清：标志卡在 true 就是一个永久加载环 + 假按钮（安卓那份的同一条收口）。
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                dailyCore.isLoadingFeed = false
            }
        }
    }
}
