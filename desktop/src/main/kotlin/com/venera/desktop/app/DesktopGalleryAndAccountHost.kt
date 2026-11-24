package com.venera.desktop.app

import com.venera.desktop.ui.BackupUiState
import com.venera.desktop.ui.BrowseClearUiState
import com.venera.desktop.ui.DesktopScrollAnchor
import com.venera.desktop.ui.DetailAndReaderUiState
import com.venera.desktop.ui.FilesAuxUiState
import com.venera.desktop.ui.GalleryAuxUiState
import com.venera.desktop.ui.PixivCollectionSearchLeg
import com.venera.desktop.ui.SavedUiState
import com.venera.desktop.ui.SourceManageUiState
import com.venera.desktop.ui.constFlow
import com.venera.desktop.data.DesktopReadingStats
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.compose.data.platform.JsonKeyValueStore
import com.venera.compose.data.platform.PathProvider
import com.venera.compose.gallery.data.GalleryContentGuard
import com.venera.compose.gallery.data.GalleryFavorite
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.data.GalleryTagSuggestion
import com.venera.compose.gallery.domain.PixivImageQuality
import com.venera.compose.gallery.data.PixivUser
import com.venera.compose.gallery.domain.GalleryPreloadMode
import com.venera.compose.gallery.domain.GalleryPreviewQuality
import com.venera.compose.gallery.domain.GalleryRanking
import com.venera.compose.gallery.domain.GallerySearchEntry
import com.venera.compose.gallery.domain.GallerySearchSource
import com.venera.compose.gallery.domain.GalleryViewerBackdrop
import com.venera.compose.gallery.domain.PixivSearchFilter
import com.venera.desktop.business.DesktopBusinessPorts
import com.venera.desktop.gallery.data.DesktopGalleryPreferences
import com.venera.desktop.gallery.data.DesktopPixivImageBytes
import com.venera.desktop.gallery.data.desktopGallerySaveDir
import com.venera.desktop.gallery.data.desktopResolveSaveDir
import com.venera.desktop.gallery.data.desktopSaveImageRun
import com.venera.desktop.gallery.data.desktopSaveImageScreenReading
import com.venera.desktop.gallery.ui.DesktopGalleryPostViewer
import com.venera.desktop.gallery.ui.DesktopGalleryWallFooter
import com.venera.desktop.gallery.ui.DesktopRankingChoice
import com.venera.desktop.gallery.ui.DesktopWallCardModel
import com.venera.desktop.gallery.ui.DesktopWallPage
import com.venera.desktop.gallery.ui.desktopCopyImageUrl
import com.venera.desktop.gallery.ui.desktopViewerFullUrl
import com.venera.desktop.ui.components.DesktopPetCue
import com.venera.desktop.ui.components.DesktopPetCueBus
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 画廊与账号/历史/统计/备份/看大图二级路由总分发宿主。
 *
 * 子页面装配拆分至 [DesktopAccountAndBackupRoutesHost] 与 [DesktopGallerySearchAndDetailRoutesHost]，
 * 保留 [DesktopRoute.GalleryPostViewer] 就地返回接线。
 */
@Composable
internal fun DesktopGalleryAndAccountHost(
    r: DesktopRoute,
    layoutMode: WideScreenLayoutMode,
    ports: GalleryPorts,
    scope: CoroutineScope,
    galleryPaths: PathProvider?,
    galleryPrefs: DesktopGalleryPreferences?,
    railPrefs: JsonKeyValueStore?,
    assetDir: File?,
    overrideDir: File?,
    desktopUx: DesktopUxPlatformServices,
    businessHandle: DesktopBusinessPorts.Handle?,
    maskModeOf: () -> String,
    wallGuardOf: () -> GalleryContentGuard?,
    wallBlockAiOf: () -> Boolean,
    shellBackdropUrl: String?,
    historySourceName: String?,
    readingStats: DesktopReadingStats,
    galleryFavorites: List<GalleryFavorite>,
    galleryWallQuality: GalleryPreviewQuality,
    galleryWallCards: List<DesktopWallCardModel>,
    galleryWallBlocked: Int,
    galleryWallUnrenderable: String,
    galleryViewerQueue: List<DesktopWallCardModel>,
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
    backupUi: BackupUiState,
    browseClearUi: BrowseClearUiState,
    detailUi: DetailAndReaderUiState,
    filesUi: FilesAuxUiState,
    galleryAuxUi: GalleryAuxUiState,
    sourceUi: SourceManageUiState,
    openWallCardAny: (DesktopWallCardModel) -> Unit,
    openGalleryPostAny: (GalleryPost) -> Unit,
    searchHandoff: (List<GallerySite>, String) -> Unit,
    flyToDownloadCenter: (String?, Boolean) -> Unit,
    routeState: MutableState<DesktopRoute>,
    savedUiState: MutableState<SavedUiState>,
    historyTickState: MutableIntState,
    galleryWallTickState: MutableIntState,
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
    pixivWorkViewerQueueState: MutableState<List<DesktopWallCardModel>>,
    pixivWorkResumePageState: MutableIntState,
) {
    var route by routeState
    var pixivWorkViewerQueue by pixivWorkViewerQueueState
    var pixivWorkResumePage by pixivWorkResumePageState

    when (r) {
        DesktopRoute.GalleryReverseSearch,
        DesktopRoute.GalleryFavorites,
        DesktopRoute.GalleryBrowseHistory,
        DesktopRoute.FollowedArtists,
        -> DesktopGallerySavedAndReverseRoute(
            r = r,
            layoutMode = layoutMode,
            ports = ports,
            scope = scope,
            galleryPaths = galleryPaths,
            galleryPrefs = galleryPrefs,
            maskModeOf = maskModeOf,
            wallGuardOf = wallGuardOf,
            wallBlockAiOf = wallBlockAiOf,
            galleryFavorites = galleryFavorites,
            galleryWallQuality = galleryWallQuality,
            galleryWallCards = galleryWallCards,
            galleryWallBlocked = galleryWallBlocked,
            galleryWallUnrenderable = galleryWallUnrenderable,
            browseClearUi = browseClearUi,
            openWallCardAny = openWallCardAny,
            routeState = routeState,
            savedUiState = savedUiState,
            galleryWallTickState = galleryWallTickState,
        )

        DesktopRoute.History,
        DesktopRoute.ReadingStats,
        -> DesktopHistoryAndStatsRoute(
            r = r,
            layoutMode = layoutMode,
            desktopUx = desktopUx,
            businessHandle = businessHandle,
            historySourceName = historySourceName,
            readingStats = readingStats,
            detailUi = detailUi,
            sourceUi = sourceUi,
            routeState = routeState,
        )

        DesktopRoute.Backup -> DesktopBackupRoute(
            layoutMode = layoutMode,
            scope = scope,
            galleryPaths = galleryPaths,
            assetDir = assetDir,
            overrideDir = overrideDir,
            backupUi = backupUi,
            routeState = routeState,
        )

        DesktopRoute.GallerySearch -> DesktopGallerySearchRoute(
            layoutMode = layoutMode,
            ports = ports,
            scope = scope,
            railPrefs = railPrefs,
            gallerySearchToday = gallerySearchToday,
            gallerySearchSourceOptions = gallerySearchSourceOptions,
            gallerySearchRankingChoices = gallerySearchRankingChoices,
            gallerySearchRankingLanding = gallerySearchRankingLanding,
            gallerySearchPeriodReading = gallerySearchPeriodReading,
            gallerySearchCanPeriodBack = gallerySearchCanPeriodBack,
            gallerySearchCanPeriodForward = gallerySearchCanPeriodForward,
            gallerySearchCards = gallerySearchCards,
            gallerySearchFooter = gallerySearchFooter,
            pixivSearchCards = pixivSearchCards,
            pixivSearchFooter = pixivSearchFooter,
            pixivCollectionSearch = pixivCollectionSearch,
            searchResultAnchor = searchResultAnchor,
            searchArtistAnchor = searchArtistAnchor,
            galleryAuxUi = galleryAuxUi,
            openWallCardAny = openWallCardAny,
            routeState = routeState,
            gallerySearchQueryState = gallerySearchQueryState,
            gallerySearchSourceState = gallerySearchSourceState,
            gallerySearchTickState = gallerySearchTickState,
            gallerySearchMoreTickState = gallerySearchMoreTickState,
            gallerySearchModeState = gallerySearchModeState,
            gallerySearchR18State = gallerySearchR18State,
            gallerySearchPageState = gallerySearchPageState,
            gallerySearchSearchingState = gallerySearchSearchingState,
            gallerySearchFailedState = gallerySearchFailedState,
            gallerySearchHistoryState = gallerySearchHistoryState,
            gallerySearchHistoryNoteState = gallerySearchHistoryNoteState,
            gallerySearchRankingState = gallerySearchRankingState,
            gallerySearchRankingAnchorState = gallerySearchRankingAnchorState,
            pixivSearchTermState = pixivSearchTermState,
            pixivSearchKindState = pixivSearchKindState,
            pixivSearchTickState = pixivSearchTickState,
            pixivSearchMoreTickState = pixivSearchMoreTickState,
            pixivSearchFilterState = pixivSearchFilterState,
            pixivSearchPageState = pixivSearchPageState,
            pixivSearchSearchingState = pixivSearchSearchingState,
            pixivSearchUsersState = pixivSearchUsersState,
            gallerySuggestCandidatesState = gallerySuggestCandidatesState,
            searchQueryState = searchQueryState,
        )

        DesktopRoute.GalleryBookmarksHub,
        is DesktopRoute.GalleryPostDetail,
        -> DesktopGalleryBookmarksAndPostDetailRoute(
            r = r,
            ports = ports,
            galleryPaths = galleryPaths,
            galleryPrefs = galleryPrefs,
            desktopUx = desktopUx,
            shellBackdropUrl = shellBackdropUrl,
            galleryFavorites = galleryFavorites,
            filesUi = filesUi,
            openGalleryPostAny = openGalleryPostAny,
            searchHandoff = searchHandoff,
            flyToDownloadCenter = flyToDownloadCenter,
            routeState = routeState,
        )

        is DesktopRoute.GalleryPostViewer -> {
            val viewerQueue = when {
                pixivWorkViewerQueue.isNotEmpty() && pixivWorkViewerQueue.any { it.uid == r.post.uid } ->
                    pixivWorkViewerQueue
                galleryViewerQueue.any { it.uid == r.post.uid } -> galleryViewerQueue
                else -> listOf(r.post)
            }
            val viewerIndex = viewerQueue.indexOfFirst { it.uid == r.post.uid }.coerceAtLeast(0)
            val savePaths = galleryPaths
            val pixivZoomNow = (galleryPrefs?.pixivZoomQuality
                ?: constFlow(PixivImageQuality.LARGE)).collectAsState().value
            DesktopGalleryPostViewer(
                post = r.post,
                queue = viewerQueue,
                onExitIndex = { idx ->
                    if (r.from is DesktopRoute.PixivWork) {
                        pixivWorkViewerQueue = emptyList()
                        pixivWorkResumePage = idx
                    }
                },
                pixiv = ports.pixiv,
                paths = galleryPaths,
                zoomQuality = pixivZoomNow,
                onZoomQualityChange = { q -> galleryPrefs?.setPixivZoomQuality(q) },
                pixivBookmarkToggle = ports.pixiv?.let { p ->
                    { id: Long, add: Boolean ->
                        val bearer = ports.pixivAccount?.bearerOrNull()
                        if (bearer == null) false
                        else if (add) p.bookmarkAdd(bearer, id, private = false).isSuccess
                        else p.bookmarkRemove(bearer, id).isSuccess
                    }
                },
                onSavePixivImage = galleryPaths?.let { pp ->
                    { card: DesktopWallCardModel ->
                        val url = desktopViewerFullUrl(card, pixivZoomNow)
                        val suggestedName = "pixiv_${card.uid.substringAfter(':')}.jpg"
                        flyToDownloadCenter(card.url, false)
                        val receipt = if (galleryPrefs?.downloadUseDefaultDir?.value ?: true) {
                            DesktopPixivImageBytes.saveToDir(
                                paths = pp,
                                url = url,
                                suggestedName = suggestedName,
                                dir = desktopGallerySaveDir(pp, galleryPrefs?.downloadDir?.value),
                            )
                        } else {
                            DesktopPixivImageBytes.saveToFile(
                                paths = pp,
                                url = url,
                                suggestedName = suggestedName,
                            )
                        }
                        if (receipt.startsWith("已保存：")) {
                            File(receipt.removePrefix("已保存：")).parent?.let { parentDir ->
                                galleryPrefs?.addExtraImageDir(parentDir)
                            }
                            filesUi.imageScanTick++
                        }
                        receipt
                    }
                },
                onCopyImage = galleryPaths?.let { pp ->
                    { card: DesktopWallCardModel ->
                        runCatching {
                            withContext(Dispatchers.IO) {
                                val bytes = DesktopPixivImageBytes.fetch(paths = pp, url = desktopCopyImageUrl(card))
                                DesktopPixivImageBytes.copyToClipboard(bytes)
                            }
                        }.isSuccess
                    }
                },
                initialIndex = viewerIndex,
                favoriteUids = galleryFavorites.map { it.uid }.toSet(),
                autoPlaySec = (galleryPrefs?.galleryAutoPlaySec ?: constFlow(0)).collectAsState().value,
                preloadMode = (galleryPrefs?.galleryPreload ?: constFlow(GalleryPreloadMode.NEXT))
                    .collectAsState().value,
                backdropColor = (galleryPrefs?.galleryBackdrop ?: constFlow(GalleryViewerBackdrop.GLASS))
                    .collectAsState().value.let { backdrop -> Color(backdrop.argb ?: 0xFF000000) },
                onBack = { route = r.from ?: DesktopRoute.Tab },
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
            )
        }

        else -> Unit
    }
}
