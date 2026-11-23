package com.venera.desktop
import com.venera.compose.data.platform.PathProvider
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.domain.GalleryWallLayout
import com.venera.desktop.gallery.data.DesktopComicDownloadedChapter
import com.venera.desktop.gallery.data.DesktopGalleryPreferences
import com.venera.desktop.gallery.ui.desktopWallCardOf
import com.venera.desktop.gallery.ui.desktopComicDeleteRowKey
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
import com.venera.desktop.gallery.ui.desktopComicDeleteScreenLine
import com.venera.desktop.gallery.ui.desktopComicDeleteShouldFire
import com.venera.desktop.gallery.ui.desktopComicDeleteNextKey
import com.venera.desktop.gallery.ui.desktopComicDownloadedReading
import com.venera.desktop.gallery.ui.DesktopVeneraTheme
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState


@Composable
internal fun DesktopLargeScreenProviders(
    windowWidth: Dp,
    fullscreenChrome: Boolean,
    ports: GalleryPorts,
    galleryPaths: PathProvider?,
    galleryPrefs: DesktopGalleryPreferences?,
    desktopUx: DesktopUxPlatformServices,
    wallLayout: GalleryWallLayout,
    wallDensity: DesktopWallDensity,
    addFavorite: (suspend (com.venera.compose.data.db.FavoriteRecord) -> Unit)?,
    removeFavorite: (suspend (String) -> Unit)?,
    gallerySearchSourceOptions: List<GallerySearchSource>,
    gallerySearchQueryState: MutableState<String>,
    gallerySearchSourceState: MutableState<Int>,
    gallerySearchTickState: androidx.compose.runtime.MutableIntState,
    routeState: MutableState<DesktopRoute>,
    content: @Composable () -> Unit,
) {
    var gallerySearchQuery by gallerySearchQueryState
    var gallerySearchSource by gallerySearchSourceState
    var gallerySearchTick by gallerySearchTickState
    var route by routeState

    CompositionLocalProvider(
        com.venera.compose.components.LocalWindowWidth provides windowWidth,
        com.venera.compose.ui.tokens.LocalVeneraDarkTheme provides
            com.venera.desktop.settings.DesktopThemeMode.isDark(
                com.venera.desktop.settings.DesktopThemeMode.mode.collectAsState().value,
            ),
        com.venera.desktop.LocalShellBackHandled provides !fullscreenChrome,
        com.venera.desktop.gallery.ui.LocalChromePalette provides
            com.venera.desktop.gallery.ui.DesktopChromeColors.of(
                dark = com.venera.desktop.settings.DesktopThemeMode.isDark(
                    com.venera.desktop.settings.DesktopThemeMode.mode.collectAsState().value,
                ),
            ),
        androidx.compose.foundation.LocalIndication provides
            com.venera.desktop.ui.components.DesktopNoIndication,
        androidx.compose.material3.LocalTextStyle provides
            androidx.compose.ui.text.TextStyle(
                fontFamily = com.venera.desktop.ui.theme.VeneraFonts.family,
            ),
        com.venera.desktop.ui.components.LocalDesktopComicFavorites provides
            com.venera.desktop.ui.components.DesktopComicFavorites(addFavorite, removeFavorite),
        com.venera.desktop.gallery.ui.LocalDesktopGalleryCardActions provides
            com.venera.desktop.gallery.ui.rememberDesktopGalleryCardActions(
                ports = ports,
                paths = galleryPaths,
                prefs = galleryPrefs,
                onNotify = desktopUx::toast,
            ),
        com.venera.desktop.gallery.ui.LocalDesktopWallSpec provides run {
            val gridTier = com.venera.desktop.settings.DesktopGridDensity.tier.collectAsState().value
            val autoRows = when {
                windowWidth >= 1740.dp -> 4
                windowWidth >= 1440.dp -> 3
                else -> 2
            }
            com.venera.desktop.gallery.ui.DesktopWallSpec(
                wallLayout,
                wallDensity,
                (galleryPrefs?.cardSize
                    ?: constFlow(com.venera.desktop.gallery.ui.DesktopCardSize.Standard)).collectAsState().value,
                coverAspect = com.venera.desktop.settings.DesktopCoverAspect.tier.collectAsState().value,
                gridDensity = gridTier,
                rows = gridTier.rows ?: autoRows,
            )
        },
        com.venera.compose.components.LocalUxPlatformServices provides desktopUx,
        LocalDesktopSearchHandoff provides { sites, tag ->
            val seed = desktopSearchSeedFor(sites, tag, gallerySearchTick + 1)
            gallerySearchQuery = seed.query
            gallerySearchSource = gallerySearchSourceOptions.indexOf(seed.source).coerceAtLeast(0)
            route = DesktopRoute.GallerySearch
            gallerySearchTick++
        },
    ) {
        DesktopVeneraTheme {
            content()
        }
    }
}

internal fun openGalleryPostHelper(
    post: GalleryPost,
    galleryPrefs: DesktopGalleryPreferences?,
    routeState: MutableState<DesktopRoute>,
) {
    val origin = routeState.value
    routeState.value = if (post.site == GallerySite.PIXIV) {
        pixivWorkRouteOf(
            illustId = post.id,
            title = post.author.ifBlank { "pixiv #${post.id}" },
            coverUrl = post.previewUrl,
            from = origin,
        )
    } else {
        DesktopRoute.GalleryPostDetail(
            desktopWallCardOf(post, currentWallQuality(galleryPrefs)),
            from = origin,
        )
    }
}

internal fun openWallCardHelper(
    card: DesktopWallCardModel,
    galleryPrefs: DesktopGalleryPreferences?,
    routeState: MutableState<DesktopRoute>,
) {
    DesktopPetCueBus.emit(
        DesktopPetCue.OPEN_IMAGE,
        DesktopPetPayload(
            title = card.title,
            author = card.author,
            tag = card.tags.substringBefore(' ').trim(),
            masked = card.masked,
        ),
    )
    val post = card.sourcePost
    if (post != null) {
        openGalleryPostHelper(post, galleryPrefs, routeState)
    } else {
        val site = GallerySite.fromRouteKey(card.uid.substringBefore(':'))
        val id = card.uid.substringAfter(':', "").toLongOrNull()
        if (site == GallerySite.PIXIV && id != null) {
            routeState.value = pixivWorkRouteOf(
                illustId = id,
                title = card.title.ifBlank { "pixiv #$id" },
                coverUrl = card.url,
                from = routeState.value,
            )
        } else {
            routeState.value = DesktopRoute.GalleryPostDetail(card, from = routeState.value)
        }
    }
}

internal fun runSourceImportHelper(
    installSourceText: (suspend (String, String) -> String?)?,
    sourceUi: SourceManageUiState,
    scope: CoroutineScope,
    onSuccess: (String) -> Unit,
) {
    val install = installSourceText
    if (install == null) {
        sourceUi.sourceSettingsNote = "脚本目录未就绪，导入没跑起来"
        return
    }
    val picked = com.venera.desktop.platform.desktopPickOpenFile(null, "导入站点脚本")
        ?.let { java.io.File(it) } ?: return
    val text = runCatching { picked.readText() }.getOrElse { t ->
        sourceUi.sourceSettingsNote = "读文件失败：${t.message ?: t.javaClass.simpleName}"
        return
    }
    val key = com.venera.desktop.gallery.data.DesktopSourceNames.keyOf(text) ?: run {
        sourceUi.sourceSettingsNote = "脚本里找不到源 key"
        return
    }
    scope.launch {
        val why = install(key, text)
        if (why == null) {
            sourceUi.sourceSelectedKey = key
            sourceUi.sourceSettingsNote = "已导入「$key」，即刻生效"
            onSuccess(key)
        } else {
            sourceUi.sourceSettingsNote = why
        }
    }
}

internal suspend fun performSourceUpdateCheckHelper(
    announce: Boolean,
    sourceUi: SourceManageUiState,
    overrideDir: java.io.File?,
    engine: com.venera.engineprobe.EngineSession?,
    onTick: () -> Unit,
) {
    if (sourceUi.sourceUpdateBusy) return
    sourceUi.sourceUpdateBusy = true
    sourceUi.sourceUpdateNote = null
    val proxy = System.getProperty("venera.proxy")?.takeIf { it.isNotBlank() }
    val entries = com.venera.engineprobe.DesktopSourceUpdate.fetchIndex(proxy)
    if (entries.isEmpty()) {
        sourceUi.sourceUpdateNote = "上游清单拉不到（网络受阻？），检查代理后重试。"
        sourceUi.sourceUpdateBusy = false
        return
    }
    val local = sourceUi.sourceRows.associate { it.key to it.version }
    val dir = overrideDir
    val pending = mutableListOf<String>()
    val updatables = entries.mapNotNull { entry ->
        val fileKey = entry.fileName.removeSuffix(".js")
        val localVersion = local[entry.key] ?: local[fileKey]
        if (entry.version.isBlank()) return@mapNotNull null
        if (localVersion == null) return@mapNotNull null
        if (!com.venera.engineprobe.DesktopSourceUpdate.isNewer(entry.version, localVersion)) {
            return@mapNotNull null
        }
        if (dir != null &&
            !com.venera.desktop.gallery.data.DesktopSourceUpdater
                .canAutoOverwrite(dir, fileKey, localVersion)
        ) {
            pending += entry.name
            return@mapNotNull null
        }
        com.venera.engineprobe.DesktopSourceUpdate.Updatable(entry, localVersion)
    }
    var ok = 0
    val done = mutableListOf<String>()
    val fails = mutableListOf<String>()
    updatables.forEach { up ->
        val text = com.venera.engineprobe.DesktopSourceUpdate.download(up.entry.fileName, proxy)
        if (text == null) {
            fails += "${up.entry.name}（下载失败）"
            return@forEach
        }
        val fileKey = up.entry.fileName.removeSuffix(".js")
        desktopEngineCall(engine) { engine?.checkLoadable(fileKey, text) }.fold(
            onSuccess = {
                val d = overrideDir
                if (d == null) {
                    fails += "${up.entry.name}（改写层不在手）"
                } else {
                    com.venera.desktop.gallery.data.DesktopSourceOverrides.writeText(d, fileKey, text)
                    com.venera.desktop.gallery.data.DesktopSourceUpdater
                        .markAutoUpdated(d, fileKey, up.entry.version)
                    ok += 1
                    done += up.entry.name
                }
            },
            onFailure = { t ->
                fails += "${up.entry.name}（${t.message ?: t.javaClass.simpleName}）"
            },
        )
    }
    sourceUi.sourceUpdateNote = buildString {
        append("更新完成：成功 $ok 颗")
        if (pending.isNotEmpty()) append("；跳过本地改写 ${pending.size} 颗：${pending.take(3).joinToString("、")}")
        if (fails.isNotEmpty()) append("；失败 ${fails.size} 颗：${fails.take(3).joinToString("、")}")
    }
    if (announce && ok > 0) {
        com.venera.desktop.platform.DesktopNotifier.notify(
            "源已更新 $ok 个",
            done.joinToString("、"),
        )
    }
    if (ok > 0) onTick()
    sourceUi.sourceUpdateBusy = false
}

internal fun onDownloadsDeleteHelper(
    chapter: DesktopComicDownloadedChapter,
    forceDirect: Boolean,
    downloadsRoot: java.io.File?,
    filesUi: FilesAuxUiState,
    armedDeleteKeyState: MutableState<String?>,
    scope: CoroutineScope,
    onDone: () -> Unit,
) {
    val root = downloadsRoot
    if (root == null) {
        filesUi.downloadsNote = "下载根目录拿不到 ⇒ 这一话删不了"
        return
    }
    if (!forceDirect) {
        val key = desktopComicDeleteRowKey(chapter)
        if (!desktopComicDeleteShouldFire(armedDeleteKeyState.value, key)) {
            armedDeleteKeyState.value = desktopComicDeleteNextKey(armedDeleteKeyState.value, key)
            return
        }
        armedDeleteKeyState.value = null
    }
    scope.launch {
        val outcome = desktopComicDeleteChapter(root, chapter)
        filesUi.downloadsNote = desktopComicDeleteScreenLine(chapter, outcome)
        onDone()
    }
}

internal fun flyToDownloadCenterHelper(
    coverUrl: String?,
    isComic: Boolean,
    flyPointer: MutableState<Offset?>,
    flyHostTopLeft: Offset,
    flyState: DesktopFlyState,
) {
    DesktopPetCueBus.emit(DesktopPetCue.DOWNLOADING)
    val origin = flyPointer.value ?: Offset(500f, 300f)
    flyState.launch(origin, coverUrl, isComic)
}
