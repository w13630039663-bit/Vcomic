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
internal fun DesktopPixivRoutesHost(
    r: DesktopRoute,
    ports: GalleryPorts,
    galleryPaths: com.venera.compose.data.platform.PathProvider?,
    galleryPrefs: com.venera.desktop.gallery.data.DesktopGalleryPreferences?,
    shellBackdropUrl: String?,
    wallDensity: DesktopWallDensity,
    artistProfileAnchor: DesktopScrollAnchor,
    dailyCore: com.venera.compose.gallery.ui.GalleryDailyCore,
    dailyGallery: DailyGalleryUiState,
    filesUi: FilesAuxUiState,
    galleryAuxUi: GalleryAuxUiState,
    openWallCardAny: (DesktopWallCardModel) -> Unit,
    searchHandoff: (List<GallerySite>, String) -> Unit,
    flyToDownloadCenter: (String?, Boolean) -> Unit,
    routeState: MutableState<DesktopRoute>,
    pixivAccountTabState: MutableState<Int>,
    pixivArtistSectionState: MutableState<com.venera.compose.gallery.data.PixivArtistSection>,
    lastPixivArtistIdState: MutableState<Long?>,
    pixivSearchKindState: MutableIntState,
    pixivSearchTermState: MutableState<String>,
    pixivSearchTickState: MutableIntState,
    gallerySearchModeState: MutableState<Int>,
    pixivWorkViewerQueueState: MutableState<List<DesktopWallCardModel>>,
    pixivWorkResumePageState: MutableIntState,
) {
    var route by routeState
    var pixivAccountTab by pixivAccountTabState
    var pixivArtistSection by pixivArtistSectionState
    var lastPixivArtistId by lastPixivArtistIdState
    var pixivSearchKind by pixivSearchKindState
    var pixivSearchTerm by pixivSearchTermState
    var pixivSearchTick by pixivSearchTickState
    var gallerySearchMode by gallerySearchModeState
    var pixivWorkViewerQueue by pixivWorkViewerQueueState
    var pixivWorkResumePage by pixivWorkResumePageState
    when (r) {
                    DesktopRoute.PixivAccount -> {
                        val pixiv = ports.pixiv
                        val account = ports.pixivAccount
                        if (pixiv == null || account == null) {
                            DesktopPixivStubPane("我的 pixiv", onBack = { route = DesktopRoute.Tab })
                        } else {
                            com.venera.desktop.pixiv.DesktopPixivAccountScreen(
                                pixiv = pixiv,
                                account = account,
                                onBack = { route = DesktopRoute.Tab },
                                onOpenLogin = { route = DesktopRoute.PixivLogin },
                                onSignOut = { account.signOut() },
                                tabState = pixivAccountTab,
                                qualityPrefs = galleryPrefs,
                                onTabChanged = { pixivAccountTab = it },
                                onOpenPost = { post ->
                                    petPost(post.author)
                                    route = pixivWorkRouteOf(
                                        illustId = post.id,
                                        title = post.author.ifBlank { "pixiv #${post.id}" },
                                        coverUrl = post.previewUrl,
                                        from = DesktopRoute.PixivAccount,
                                    )
                                },
                                onOpenArtist = { id, name ->
                                    route = DesktopRoute.PixivArtist(
                                        userId = id,
                                        name = name,
                                        from = DesktopRoute.PixivAccount,
                                    )
                                },
                                collections = ports.pixivCollections,
                                onOpenCollection = { card ->
                                    route = DesktopRoute.PixivCollectionDetail(
                                        collectionId = card.id,
                                        title = card.title,
                                        coverUrl = card.thumbnailImageUrl,
                                        from = DesktopRoute.PixivAccount,
                                    )
                                },
                            )
                        }
                    }
                    is DesktopRoute.PixivArtist -> {
                        val pixiv = ports.pixiv
                        val account = ports.pixivAccount
                        if (pixiv == null || account == null) {
                            DesktopPixivStubPane("pixiv 画师", onBack = { route = DesktopRoute.Tab })
                        } else {
                            LaunchedEffect(r.userId) {
                                if (lastPixivArtistId != r.userId) {
                                    lastPixivArtistId = r.userId
                                    pixivArtistSection = com.venera.compose.gallery.data.PixivArtistSection.ILLUST
                                    artistProfileAnchor.reset()
                                }
                            }
                            com.venera.desktop.pixiv.DesktopPixivArtistScreen(
                                userId = r.userId,
                                name = r.name,
                                pixiv = pixiv,
                                account = account,
                                sectionState = pixivArtistSection,
                                onSectionChanged = {
                                    pixivArtistSection = it
                                    artistProfileAnchor.reset()
                                },
                                wallState = rememberRetainedLazyStaggeredGridState(artistProfileAnchor),
                                wallAnchor = artistProfileAnchor,
                                onOpenArtist = { id, name ->
                                    route = DesktopRoute.PixivArtist(
                                        userId = id,
                                        name = name,
                                        from = r,
                                    )
                                },
                                // 返回口径：弹回来路（账号页 / 作品页），不是固定一层。
                                onBack = { route = r.from ?: DesktopRoute.PixivAccount },
                                onOpenPost = { post ->
                                    petPost(post.author)
                                    route = pixivWorkRouteOf(
                                        illustId = post.id,
                                        title = post.author.ifBlank { "pixiv #${post.id}" },
                                        coverUrl = post.previewUrl,
                                        from = r,
                                    )
                                },
                            )
                        }
                    }
                    is DesktopRoute.PixivWork -> {
                        val pixiv = ports.pixiv
                        val account = ports.pixivAccount
                        if (pixiv == null || account == null) {
                            DesktopPixivStubPane("pixiv 作品", onBack = { route = DesktopRoute.Tab })
                        } else {
                            com.venera.desktop.pixiv.DesktopPixivWorkScreen(
                                illustId = r.illustId,
                                title = r.title,
                                pixiv = pixiv,
                                resumePage = pixivWorkResumePage,
                                account = account,
                                qualityPrefs = galleryPrefs,
                                backdropFromShell = shellBackdropUrl != null,
                                // 上一作 / 下一作：路由里那两格（来路那一列里紧挨着的两篇）。
                                // 走同一个 `pixivWorkRouteOf`，所以连翻时前后格会跟着现算，不会越翻越偏。
                                onOpenPrevWork = r.prevWork?.let { nb ->
                                    {
                                        route = pixivWorkRouteOf(
                                            illustId = nb.illustId,
                                            title = nb.title,
                                            coverUrl = nb.coverUrl,
                                            from = r.from,
                                        )
                                    }
                                },
                                onOpenNextWork = r.nextWork?.let { nb ->
                                    {
                                        route = pixivWorkRouteOf(
                                            illustId = nb.illustId,
                                            title = nb.title,
                                            coverUrl = nb.coverUrl,
                                            from = r.from,
                                        )
                                    }
                                },
                                // 已下载判据只认落盘文件（文件名与下面存图那支**同一套**）——
                                // 内存账在重启或换下载目录之后就会说谎。
                                isWorkSaved = galleryPaths?.let { pp ->
                                    { detail ->
                                        val dir = com.venera.desktop.gallery.data.desktopGallerySaveDir(
                                            pp,
                                            galleryPrefs?.downloadDir?.value,
                                        )
                                        val pages = detail.pages.size
                                        if (pages > 1) {
                                            (0 until pages).all { idx ->
                                                java.io.File(dir, "pixiv_${detail.post.id}_p$idx.jpg").isFile
                                            }
                                        } else {
                                            java.io.File(dir, "pixiv_${detail.post.id}.jpg").isFile
                                        }
                                    }
                                },
                                // 复制当前查看的大图：取字节与看图屏同一条 Referer 路，多图由屏侧传当前页地址。
                                onCopyImage = galleryPaths?.let { pp ->
                                    { url: String ->
                                        runCatching {
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                com.venera.desktop.gallery.data.DesktopPixivImageBytes.copyToClipboard(
                                                    com.venera.desktop.gallery.data.DesktopPixivImageBytes.fetch(
                                                        paths = pp,
                                                        url = url,
                                                    ),
                                                )
                                            }
                                        }.isSuccess
                                    }
                                },
                                // 返回口径：弹回来路（主墙 / 画师页 / 账号页 / 搜索 / 收藏）。
                                onBack = { route = r.from ?: DesktopRoute.PixivAccount },
                                onOpenArtist = { id, name ->
                                    route = DesktopRoute.PixivArtist(
                                        userId = id,
                                        name = name,
                                        from = r,
                                    )
                                },
                                onOpenPage = { queueCards, startPage ->
                                    pixivWorkViewerQueue = queueCards
                                    route = DesktopRoute.GalleryPostViewer(
                                        queueCards[startPage.coerceIn(0, queueCards.lastIndex)],
                                        // 播放器返回弹回来路：详情页中央大图进来的回详情页（主人第 5 点）。
                                        from = r,
                                    )
                                },
                                // 点画师面板 / 信息栏「其他作品」的卡 = 另一篇作品详情
                                // （pixiv 卡都进详情页），来路 = 当前这一页。
                                onOpenPost = { post ->
                                    petPost(post.author)
                                    route = pixivWorkRouteOf(
                                        illustId = post.id,
                                        title = post.author.ifBlank { "pixiv #${post.id}" },
                                        coverUrl = post.previewUrl,
                                        from = r,
                                    )
                                },
                                // 收藏钮只在已登录时摆（未登录摆了发不出去，是假把手）：
                                // 判据读 `pixivAccount.authState` 那条流的当前值。
                                onBookmarkToggle = if (account.authState.value
                                        is com.venera.compose.gallery.data.PixivAuthState.SignedIn
                                ) {
                                    { id, want ->
                                        val bearer = account.bearerOrNull()
                                        if (bearer == null) {
                                            Result.failure(IllegalStateException("拿不到票据"))
                                        } else if (want) {
                                            pixiv.bookmarkAdd(bearer, id, private = false)
                                        } else {
                                            pixiv.bookmarkRemove(bearer, id)
                                        }
                                    }
                                } else {
                                    null
                                },
                                onSavePixivWork = galleryPaths?.let { pp ->
                                    { detail ->
                                        // 点击下载那一刻立即触发飞入动效
                                        flyToDownloadCenter(detail.post.previewUrl, false)
                                        // 与播放器存图同一条目录策略：默认目录静默存，「每次询问」时弹目录框。
                                        val dir = com.venera.desktop.gallery.data.desktopResolveSaveDir(pp, galleryPrefs)
                                        val pages = detail.pages
                                        if (dir == null) {
                                            return@let "已取消保存（没选目录）。"
                                        }
                                        var savedCount = 0
                                        var lastPath = ""
                                        withContext(Dispatchers.IO) {
                                            pages.forEachIndexed { idx, page ->
                                                val url = page.originalUrl.ifBlank { page.largeUrl }
                                                val suggestedName = if (pages.size > 1) {
                                                    "pixiv_${detail.post.id}_p$idx.jpg"
                                                } else {
                                                    "pixiv_${detail.post.id}.jpg"
                                                }
                                                val res = com.venera.desktop.gallery.data.DesktopPixivImageBytes.saveToDir(
                                                    paths = pp,
                                                    url = url,
                                                    suggestedName = suggestedName,
                                                    dir = dir,
                                                )
                                                if (res.startsWith("已保存")) {
                                                    savedCount++
                                                    lastPath = res.substringAfter("：")
                                                }
                                            }
                                        }
                                        if (savedCount > 0) {
                                            filesUi.imageScanTick++
                                            if (pages.size > 1) "已保存全部 $savedCount 页到本机。" else "已保存到本机：$lastPath"
                                        } else {
                                            "保存失败（未落盘）。"
                                        }
                                    }
                                },
                                // 点标签 chip = 直接跳 pixiv 源搜索：
                                // 切 pixiv 搜索模式带词，重打 tick 触发首屏 effect，再进搜索屏。
                                onTagSearch = { tag ->
                                    gallerySearchMode = 1
                                    pixivSearchTerm = tag
                                    pixivSearchKind = DESKTOP_PIXIV_SEARCH_WORKS
                                    pixivSearchTick++
                                    route = DesktopRoute.GallerySearch
                                },
                            )
                        }
                    }
                    DesktopRoute.PixivLogin -> {
                        val account = ports.pixivAccount
                        if (account == null) {
                            DesktopPixivStubPane("登录 pixiv", onBack = { route = DesktopRoute.Tab })
                        } else {
                            com.venera.desktop.pixiv.DesktopPixivLoginScreen(
                                account = account,
                                onBack = { route = DesktopRoute.PixivAccount },
                            )
                        }
                    }
                    DesktopRoute.PixivBookmarksUnwired -> DesktopPixivBookmarksUnwiredPane(
                        onBack = { route = DesktopRoute.GalleryBookmarksHub },
                    )

                    // ── 2.5 屏蔽规则管理子页（守卫账真消费：规则即主墙/遮罩判定吃的）──
                    DesktopRoute.PixivCollections -> {
                        val port = ports.pixivCollections
                        if (port == null) {
                            DesktopPixivStubPane("珍藏册", onBack = { route = DesktopRoute.Tab })
                        } else {
                            com.venera.desktop.gallery.ui.DesktopPixivCollectionsScreen(
                                collections = port,
                                onBack = { route = DesktopRoute.Tab },
                                onOpenCollection = { card ->
                                    route = DesktopRoute.PixivCollectionDetail(
                                        collectionId = card.id,
                                        title = card.title,
                                        coverUrl = card.thumbnailImageUrl,
                                        from = DesktopRoute.PixivCollections,
                                    )
                                },
                                onOpenCreator = { card ->
                                    route = DesktopRoute.PixivArtist(
                                        userId = card.userId,
                                        name = card.userName,
                                        from = DesktopRoute.PixivCollections,
                                    )
                                },
                            )
                        }
                    }
                    is DesktopRoute.PixivCollectionDetail -> {
                        val port = ports.pixivCollections
                        if (port == null) {
                            DesktopPixivStubPane(
                                "珍藏册",
                                onBack = { route = r.from ?: DesktopRoute.PixivCollections },
                            )
                        } else {
                            com.venera.desktop.gallery.ui.DesktopPixivCollectionDetailScreen(
                                collections = port,
                                collectionId = r.collectionId,
                                initialTitle = r.title,
                                initialCover = r.coverUrl,
                                onBack = { route = r.from ?: DesktopRoute.PixivCollections },
                                onOpenWork = { work ->
                                    route = pixivWorkRouteOf(
                                        illustId = work.id,
                                        title = work.title,
                                        coverUrl = work.url.orEmpty(),
                                        from = r,
                                    )
                                },
                                onOpenCollection = { card ->
                                    route = DesktopRoute.PixivCollectionDetail(
                                        collectionId = card.id,
                                        title = card.title,
                                        coverUrl = card.thumbnailImageUrl,
                                        from = r,
                                    )
                                },
                                onOpenCreator = { card ->
                                    route = DesktopRoute.PixivArtist(
                                        userId = card.userId,
                                        name = card.userName,
                                        from = r,
                                    )
                                },
                                onOpenDiscover = { route = DesktopRoute.PixivCollections },
                            )
                        }
                    }
                    DesktopRoute.PixivisionArticle -> {
                        val open = galleryAuxUi.pixivisionArticleOpen
                        val pixivisionUri = androidx.compose.ui.platform.LocalUriHandler.current
                        if (open == null) {
                            route = DesktopRoute.Tab
                        } else {
                            DesktopGalleryPixivisionArticleScreen(
                                article = open,
                                body = galleryAuxUi.pixivisionArticleBody,
                                loading = galleryAuxUi.pixivisionArticleLoading,
                                onBack = { route = DesktopRoute.Tab },
                                onSearchArtist = { name ->
                                    searchHandoff(
                                        com.venera.compose.gallery.data.GallerySite.entries.toList(),
                                        name,
                                    )
                                },
                                onOpenWork = { url -> pixivisionUri.openUri(url) },
                                onOpenRelated = { rel -> pixivisionUri.openUri(rel.url) },
                                onOpenSite = { pixivisionUri.openUri(open.url) },
                            )
                        }
                    }
                    DesktopRoute.GalleryDaily -> {
                        com.venera.desktop.gallery.ui.DesktopDailyEditorialScreen(
                            yandeCards = dailyGallery.yandeCards,
                            yandeLoading = dailyCore.isLoadingFeed,
                            yandeError = dailyCore.feedError,
                            konachanCards = dailyGallery.konachanCards,
                            konachanLoading = dailyGallery.konachanLoading,
                            konachanHasMore = dailyGallery.konachanHasMore,
                            onKonachanLoadMore = { dailyGallery.konachanLoadTick++ },
                            pixivCards = dailyGallery.pixivCards,
                            pixivAvatars = dailyGallery.pixivAvatars,
                            pixivTitles = dailyGallery.pixivTitles,
                            pixivLoading = dailyGallery.pixivLoading,
                            pixivHasMore = dailyGallery.pixivHasMore,
                            onPixivLoadMore = { dailyGallery.pixivLoadTick++ },
                            onPickPixivTab = { dailyGallery.pixivTab = it },
                            pixivTab = dailyGallery.pixivTab,
                            wallGap = com.venera.desktop.gallery.ui.DesktopWallSpec(density = wallDensity).wallGap,
                            date = dailyCore.date,
                            error = dailyCore.feedError,
                            onOpenCard = openWallCardAny,
                            onSearchTag = { tag ->
                                searchHandoff(com.venera.compose.gallery.data.GallerySite.entries.toList(), tag)
                            },
                            onRemix = {
                                if (!dailyCore.remix()) {
                                    // 池子不在手（冷启动铺的缓存屏 / 这轮没取成）才真取一次；
                                    // 先清熔断，否则按下去必然还是"熔断中"，是一枚假按钮（安卓同条）。
                                    ports.hygiene.resetAllSiteBreakers()
                                    dailyCore.refresh()
                                }
                            },
                            onRetry = {
                                ports.hygiene.resetAllSiteBreakers()
                                dailyCore.refresh()
                            },
                        )
                    }

                    // 一级 Tab 间采用 180ms 同级交叉淡化（无位移），侧栏与标题栏位于 AnimatedContent 之外不参与淡变。
        else -> Unit
    }
}
