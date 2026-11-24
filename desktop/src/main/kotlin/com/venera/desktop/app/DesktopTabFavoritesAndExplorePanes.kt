package com.venera.desktop.app

import com.venera.desktop.ui.BrowseClearUiState
import com.venera.desktop.data.DesktopErrorBus
import com.venera.desktop.ui.DesktopScrollAnchor
import com.venera.desktop.ui.DetailAndReaderUiState
import com.venera.desktop.ui.ExplorePick
import com.venera.desktop.ui.ExplorePickUiState
import com.venera.desktop.ui.FilesAuxUiState
import com.venera.desktop.ui.NetFavUiState
import com.venera.desktop.ui.SourceManageUiState
import com.venera.desktop.ui.rememberRetainedLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.compose.data.db.ComicFollowDao
import com.venera.compose.data.db.ComicFollowOrigin
import com.venera.compose.data.db.ComicFollowRecord
import com.venera.compose.data.db.FavoriteRecord
import com.venera.compose.data.platform.PathProvider
import com.venera.compose.gallery.data.GalleryFavorite
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.desktop.business.DesktopBusinessPorts
import com.venera.desktop.downloads.DesktopFilesDomain
import com.venera.desktop.downloads.DesktopFilesSegment
import com.venera.desktop.explore.DesktopExploreScreen
import com.venera.desktop.explore.ExploreSourceChoice
import com.venera.desktop.favorites.DesktopFavoritesHubScreen
import com.venera.desktop.favorites.FAVORITES_SOURCE_FOLLOW
import com.venera.desktop.favorites.FAVORITES_SOURCE_GALLERY
import com.venera.desktop.favorites.FAVORITES_SOURCE_LOCAL
import com.venera.desktop.favorites.FavWeekStats
import com.venera.desktop.favorites.NetFavComicRow
import com.venera.desktop.favorites.desktopFavWeekStats
import com.venera.desktop.follow.DesktopFollowUpdateChecker
import com.venera.desktop.follow.DesktopNetworkFollowImporter
import com.venera.desktop.follow.FollowCheckProgress
import com.venera.desktop.follow.NetFollowImportProgress
import com.venera.desktop.gallery.data.DesktopFavoriteFolderTab
import com.venera.desktop.gallery.data.desktopComicOfflineBadgeLabelOf
import com.venera.desktop.gallery.ui.DesktopFavoritesClearStage
import com.venera.desktop.gallery.ui.DesktopWallCardModel
import com.venera.desktop.gallery.ui.desktopClearGalleryFavorites
import com.venera.desktop.gallery.ui.desktopFavoritesClearLabel
import com.venera.desktop.gallery.ui.desktopFavoritesClearNext
import com.venera.desktop.gallery.ui.desktopFavoritesClearReading
import com.venera.desktop.gallery.ui.desktopFavoritesClearVisible
import com.venera.desktop.platform.desktopFailureNoteOf
import com.venera.desktop.platform.desktopFailureNoteOfText
import com.venera.desktop.settings.DesktopI18n
import com.venera.desktop.ui.components.DesktopPetCue
import com.venera.desktop.ui.components.DesktopPetCueBus
import com.venera.engineprobe.EngineSession
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 收藏中枢（FAVORITES）页签视图装配，统合本地收藏、画廊收藏、网络收藏与追更四路。
 */
@Composable
internal fun DesktopFavoritesTab(
    layoutMode: WideScreenLayoutMode,
    ports: GalleryPorts,
    galleryPaths: PathProvider?,
    scope: CoroutineScope,
    desktopUx: DesktopUxPlatformServices,
    businessHandle: DesktopBusinessPorts.Handle?,
    browseClearUi: BrowseClearUiState,
    detailUi: DetailAndReaderUiState,
    favoritesLoaded: Boolean,
    favoritesState: MutableState<List<FavoriteRecord>>,
    favoritesNote: String?,
    netFavUi: NetFavUiState,
    galleryWallCards: List<DesktopWallCardModel>,
    galleryWallBlocked: Int,
    galleryWallUnrenderable: String,
    galleryFavorites: List<GalleryFavorite>,
    favoritesShown: List<FavoriteRecord>,
    favoritesFolderTabs: List<DesktopFavoriteFolderTab>,
    favoritesCountNote: String,
    favoritesFilterNote: String?,
    removeFavorite: (suspend (String) -> Unit)?,
    createFavoriteFolder: (suspend (String) -> Unit)?,
    deleteFavoriteFolder: (suspend (String) -> Unit)?,
    routeState: MutableState<DesktopRoute>,
    favoritesTabPickedState: MutableState<String?>,
    favoritesSourceKeyState: MutableState<String>,
    galleryWallTickState: MutableIntState,
    followDao: ComicFollowDao?,
    followRecords: List<ComicFollowRecord>,
    followProgress: FollowCheckProgress,
    followImportProgress: NetFollowImportProgress,
    followSyncKeys: Set<String>,
    followChecker: DesktopFollowUpdateChecker?,
    followImporter: DesktopNetworkFollowImporter?,
    onStartFollowImport: ((sourceName: String, folderId: String, folderName: String) -> Unit)?,
) {
    var route by routeState
    var favorites by favoritesState
    var favoritesTabPicked by favoritesTabPickedState
    var favoritesSourceKey by favoritesSourceKeyState
    var galleryWallTick by galleryWallTickState

    DesktopFavoritesHubScreen(
        mode = layoutMode,
        selectedSource = favoritesSourceKey,
        onPickSource = { key ->
            petSource(key)
            if (key != favoritesSourceKey) {
                favoritesSourceKey = key
                if (key != FAVORITES_SOURCE_LOCAL &&
                    key != FAVORITES_SOURCE_GALLERY &&
                    key != FAVORITES_SOURCE_FOLLOW
                ) {
                    netFavUi.netFavExpandedKey = key
                    netFavUi.netFavPage = null
                    netFavUi.netFavFolders = null
                    netFavUi.netFavFolderPicked = null
                    netFavUi.netFavNext = null
                    netFavUi.netFavPageNo = 1
                    netFavUi.netFavMaxPage = null
                    netFavUi.netFavReloadTick++
                }
            }
        },
        shownFavorites = favoritesShown,
        folderTabs = favoritesFolderTabs,
        localFolderPicked = favoritesTabPicked,
        localSortMode = if (
            galleryPaths?.let {
                com.venera.desktop.business.DesktopComicPreferencesPort
                    .getInstance(it).favoriteSortOrder.collectAsState().value
            } == "title"
        ) 1 else 0,
        onPickLocalSortMode = { i ->
            galleryPaths?.let {
                com.venera.desktop.business.DesktopComicPreferencesPort
                    .getInstance(it).setFavoriteSortOrder(if (i == 1) "title" else "time")
            }
        },
        countNote = favoritesCountNote,
        localCount = if (favoritesLoaded) favorites.size else -1,
        localWeekStats = remember(favorites, favoritesLoaded) {
            if (favoritesLoaded) {
                desktopFavWeekStats(favorites, System.currentTimeMillis())
            } else {
                FavWeekStats.EMPTY
            }
        },
        loaded = favoritesLoaded,
        note = favoritesNote,
        filterNote = favoritesFilterNote,
        onFolderTabSelect = { folder -> petFilter(); favoritesTabPicked = folder },
        onCreateFolder = createFavoriteFolder?.let { create ->
            { name ->
                scope.launch {
                    runCatching { create.invoke(name) }
                    detailUi.favoriteTick++
                }
            }
        },
        onDeleteFolder = deleteFavoriteFolder?.let { del ->
            { name ->
                scope.launch {
                    runCatching { del.invoke(name) }
                    detailUi.favoriteTick++
                    if (favoritesTabPicked == name) favoritesTabPicked = null
                }
            }
        },
        maskedOf = { fav ->
            desktopUx.coverMaskStateOf(
                sourceKey = fav.sourceName,
                title = fav.title,
                author = fav.author,
                tags = fav.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                comicId = fav.comicId,
            ) != "VISIBLE"
        },
        onOpen = { fav ->
            petWork(fav.title, fav.author, fav.tags.substringBefore(',').trim())
            detailUi.detailInfo = null
            detailUi.detailNote = null
            route = DesktopRoute.Detail(
                from = route,
                comicId = fav.comicId,
                title = fav.title,
                cover = fav.coverUrl,
                sourceKey = fav.sourceName,
            )
        },
        onRemove = { fav ->
            DesktopPetCueBus.emit(DesktopPetCue.REMOVE_FAVORITE)
            favorites = favorites.filterNot { it.comicId == fav.comicId }
            scope.launch { runCatching { removeFavorite?.invoke(fav.comicId) } }
        },
        onSearchComicTag = null,
        onAddFollowFavorite = followDao?.let { dao ->
            { fav ->
                scope.launch {
                    runCatching {
                        dao.addIfAbsent(
                            ComicFollowRecord(
                                comicId = fav.comicId,
                                sourceName = fav.sourceName,
                                title = fav.title,
                                author = fav.author,
                                coverUrl = fav.coverUrl,
                                originType = ComicFollowOrigin.LOCAL_FAV,
                                createdAt = System.currentTimeMillis(),
                            ),
                        )
                    }.onSuccess { added ->
                        desktopUx.toast(
                            DesktopI18n.tr(
                                if (added) "已加入追更" else "已在追更清单中",
                                if (added) "Added to follow updates" else "Already in follow updates",
                            ),
                        )
                    }.onFailure { err ->
                        DesktopErrorBus.report(
                            category = "漫画",
                            headline = "加入追更失败",
                            humanReason = "这一本没写进追更账本，本地收藏本身不受影响；可再点一次。",
                            detail = "${err::class.java.simpleName}: ${err.message.orEmpty()}",
                            dedupKey = "comic:follow_add",
                        )
                        desktopUx.toastLong(
                            DesktopI18n.tr(
                                "加入追更失败：${desktopFailureNoteOf(err)}",
                                "Failed to follow: ${desktopFailureNoteOf(err)}",
                            ),
                        )
                    }
                }
            }
        },
        galleryLoaded = true,
        galleryTotal = galleryFavorites.size,
        galleryCards = galleryWallCards,
        galleryBlocked = galleryWallBlocked,
        galleryUnrenderableLine = galleryWallUnrenderable,
        galleryClearVisible = galleryFavorites.isNotEmpty() &&
            desktopFavoritesClearVisible(galleryFavorites.size),
        galleryClearLabel = desktopFavoritesClearLabel(browseClearUi.galleryClearStage, galleryFavorites.size),
        onGalleryClearClick = {
            val next = desktopFavoritesClearNext(
                stage = browseClearUi.galleryClearStage,
                countAtArm = browseClearUi.galleryClearAtArm,
                countNow = galleryFavorites.size,
            )
            if (next == DesktopFavoritesClearStage.FIRE) {
                browseClearUi.galleryClearStage = next
                scope.launch {
                    val outcome = desktopClearGalleryFavorites(ports.favorites, galleryFavorites.size)
                    browseClearUi.galleryClearNote = desktopFavoritesClearReading(outcome)
                    galleryWallTick++
                }
                browseClearUi.galleryClearStage = DesktopFavoritesClearStage.IDLE
            } else {
                browseClearUi.galleryClearAtArm = galleryFavorites.size
                browseClearUi.galleryClearStage = next
            }
        },
        galleryClearNote = browseClearUi.galleryClearNote,
        onOpenGalleryPost = { card -> petImage(card); route = DesktopRoute.GalleryPostViewer(card) },
        onGalleryRemoveSelected = { uids ->
            if (uids.isNotEmpty()) {
                scope.launch {
                    runCatching { ports.favorites.removeAll(uids) }
                    browseClearUi.galleryClearNote = "已取消收藏 ${uids.size} 张"
                }
            }
        },
        netSources = netFavUi.netFavSources.orEmpty(),
        netActiveSource = netFavUi.netFavSources.orEmpty().firstOrNull { it.key == favoritesSourceKey },
        netPage = netFavUi.netFavPage,
        netFolders = netFavUi.netFavFolders,
        onPickNetFolder = { folderId ->
            petFilter()
            if (netFavUi.netFavFolderPicked != folderId) {
                netFavUi.netFavFolderPicked = folderId
                netFavUi.netFavPage = null
                netFavUi.netFavNext = null
                netFavUi.netFavPageNo = 1
                netFavUi.netFavMaxPage = null
                netFavUi.netFavReloadTick++
            }
        },
        netMaskedOf = { row ->
            desktopUx.coverMaskStateOf(
                sourceKey = row.sourceKey,
                title = row.title,
                author = "",
                tags = emptyList(),
                comicId = row.comicId,
            ) != "VISIBLE"
        },
        onOpenNetComic = { row ->
            petWork(row.title)
            detailUi.detailInfo = null
            detailUi.detailNote = null
            route = DesktopRoute.Detail(
                from = route,
                comicId = row.comicId,
                title = row.title,
                cover = row.cover,
                sourceKey = row.sourceKey,
            )
        },
        onGoSourceManage = { route = DesktopRoute.SourceManage },
        onRefreshNet = {
            DesktopPetCueBus.emit(DesktopPetCue.LOADING)
            netFavUi.netFavPage = null
            netFavUi.netFavReloadTick++
        },
        netLoadingMore = netFavUi.netFavLoadingMore,
        onLoadNetMore = { DesktopPetCueBus.emit(DesktopPetCue.LOADING); netFavUi.netFavMoreTick++ },
        followRecords = followRecords,
        followProgress = followProgress,
        onCheckFollow = {
            DesktopPetCueBus.emit(DesktopPetCue.LOADING)
            followChecker?.start(scope, followRecords, force = false)
        },
        onCancelFollowCheck = { followChecker?.cancel() },
        onOpenFollow = { rec ->
            detailUi.detailInfo = null
            detailUi.detailNote = null
            route = DesktopRoute.Detail(
                from = route,
                comicId = rec.comicId,
                title = rec.title,
                cover = rec.coverUrl,
                sourceKey = rec.sourceName,
            )
        },
        onMarkFollowRead = { rec ->
            scope.launch { runCatching { followDao?.markRead(rec.comicId, rec.sourceName) } }
        },
        onSetFollowStatus = { rec, status ->
            scope.launch { runCatching { followDao?.setStatus(rec.comicId, rec.sourceName, status) } }
        },
        onRetryFollow = { rec -> followChecker?.start(scope, listOf(rec), force = true) },
        onBatchRetryFollow = { targets -> followChecker?.start(scope, targets, force = true) },
        onRemoveFollow = { rec ->
            scope.launch { runCatching { followDao?.remove(rec.comicId, rec.sourceName) } }
        },
        followMaskedOf = { rec ->
            desktopUx.coverMaskStateOf(
                sourceKey = rec.sourceName,
                title = rec.title,
                author = rec.author,
                tags = emptyList(),
                comicId = rec.comicId,
            ) != "VISIBLE"
        },
        followSubscribed = run {
            val key = netFavUi.netFavExpandedKey
            key != null && "${key}|${netFavUi.netFavFolders?.selectedId.orEmpty()}" in followSyncKeys
        },
        followComicIds = followRecords
            .filter { it.sourceName == netFavUi.netFavExpandedKey }
            .mapTo(HashSet()) { it.comicId },
        followImportProgress = followImportProgress,
        onFollowImport = if (
            netFavUi.netFavSources.orEmpty().firstOrNull { it.key == netFavUi.netFavExpandedKey }?.logged == true
        ) {
            onStartFollowImport?.let { start ->
                {
                    val key = netFavUi.netFavExpandedKey
                    if (key != null) {
                        val folderId = netFavUi.netFavFolders?.selectedId.orEmpty()
                        val folderName = netFavUi.netFavFolders?.let { st ->
                            st.folders.firstOrNull { it.id == st.selectedId }?.name
                        }
                        start(key, folderId, folderName ?: key)
                    }
                }
            }
        } else {
            null
        },
        onCancelFollowImport = { followImporter?.cancel() },
        onBatchUnfavoriteNetFavorites = businessHandle?.let { handle ->
            val sourceCatalog = remember(handle) {
                DesktopBusinessPorts.createSourceCatalog(handle)
            }
            val handler: (String, String?, List<NetFavComicRow>) -> Unit = { sourceKey, folderId, comics ->
                scope.launch {
                    val fn = sourceCatalog.getSource(sourceKey)?.favoriteData?.addOrDelFavorite
                    if (fn == null) {
                        desktopUx.toast(
                            DesktopI18n.tr(
                                "该源暂不支持取消网络收藏",
                                "This source does not support removing network favorites",
                            ),
                        )
                        return@launch
                    }
                    val removedIds = hashSetOf<String>()
                    var firstErr: String? = null
                    comics.forEach { row ->
                        val res = try {
                            fn(row.comicId, folderId.orEmpty(), false, null)
                        } catch (t: Throwable) {
                            Result.failure(t)
                        }
                        if (res.isSuccess) removedIds += row.comicId
                        else if (firstErr == null) firstErr = res.exceptionOrNull()?.message
                    }
                    if (removedIds.isNotEmpty() && netFavUi.netFavExpandedKey == sourceKey) {
                        netFavUi.netFavPage = netFavUi.netFavPage?.let { page ->
                            page.copy(comics = page.comics.filterNot { it.comicId in removedIds })
                        }
                    }
                    val okCount = removedIds.size
                    val failCount = comics.size - okCount
                    if (failCount > 0) {
                        DesktopErrorBus.report(
                            category = "漫画",
                            headline = "取消网络收藏失败",
                            humanReason = "这一批里有 $failCount 本没撤掉（多收藏集的源要连点多次才干净）；可稍后重试。",
                            detail = "$sourceKey: ${firstErr ?: "未知错误"}",
                            dedupKey = "comic:net_fav_unfav:$sourceKey",
                        )
                    }
                    desktopUx.toast(
                        when {
                            failCount == 0 -> DesktopI18n.tr(
                                "已取消 $okCount 本网络收藏",
                                "Removed $okCount from network favorites",
                            )
                            okCount == 0 -> DesktopI18n.tr(
                                "取消收藏失败：${desktopFailureNoteOfText(firstErr ?: "未知错误")}",
                                "Failed to unfavorite: ${desktopFailureNoteOfText(firstErr ?: "Unknown error")}",
                            )
                            else -> DesktopI18n.tr(
                                "已取消 $okCount 本 · 失败 $failCount 本",
                                "Removed $okCount · Failed $failCount",
                            )
                        },
                    )
                }
            }
            handler
        },
        onBatchFollowNetFavorites = followDao?.let { dao ->
            { comics ->
                scope.launch {
                    var added = 0
                    val now = System.currentTimeMillis()
                    comics.forEach { row ->
                        val ok = runCatching {
                            dao.addIfAbsent(
                                ComicFollowRecord(
                                    comicId = row.comicId,
                                    sourceName = row.sourceKey,
                                    title = row.title,
                                    author = "",
                                    coverUrl = row.cover,
                                    originType = ComicFollowOrigin.NET_FOLDER,
                                    createdAt = now,
                                ),
                            )
                        }.getOrDefault(false)
                        if (ok) added++
                    }
                    desktopUx.toast(
                        DesktopI18n.tr(
                            if (added > 0) "已加入 $added 本追更" else "所选条目已在追更清单中",
                            if (added > 0) "Added $added to follow updates" else "Already in follow updates",
                        ),
                    )
                }
            }
        },
    )
}

/**
 * 漫画探索（EXPLORE）页签视图装配。
 */
@Composable
internal fun DesktopExploreTab(
    layoutMode: WideScreenLayoutMode,
    scope: CoroutineScope,
    downloadsRoot: File?,
    desktopUx: DesktopUxPlatformServices,
    detailUi: DetailAndReaderUiState,
    exploreCategoryBoard: EngineSession.CategoryBoard,
    explorePickUi: ExplorePickUiState,
    sourceUi: SourceManageUiState,
    filesUi: FilesAuxUiState,
    exploreAnchor: DesktopScrollAnchor,
    favorites: List<FavoriteRecord>,
    addFavorite: (suspend (FavoriteRecord) -> Unit)?,
    openSettings: () -> Unit,
    routeState: MutableState<DesktopRoute>,
    exploreSectionsState: MutableState<List<EngineSession.Section>>,
    exploreNoteState: MutableState<String?>,
    exploreTickState: MutableIntState,
    exploreSourceKeyState: MutableState<String?>,
    exploreModesState: MutableState<List<String>>,
    exploreModeIndexState: MutableIntState,
    explorePickTickState: MutableIntState,
    explorePickMoreTickState: MutableIntState,
    explorePickPageNoState: MutableIntState,
    historyTickState: MutableIntState,
    filesSegmentState: MutableState<DesktopFilesSegment>,
) {
    var route by routeState
    var exploreSections by exploreSectionsState
    val exploreNote by exploreNoteState
    var exploreTick by exploreTickState
    var exploreSourceKey by exploreSourceKeyState
    val exploreModes by exploreModesState
    var exploreModeIndex by exploreModeIndexState
    var explorePickTick by explorePickTickState
    var explorePickMoreTick by explorePickMoreTickState
    var explorePickPageNo by explorePickPageNoState
    var historyTick by historyTickState
    var filesSegment by filesSegmentState

    DesktopExploreScreen(
        mode = layoutMode,
        gridState = rememberRetainedLazyGridState(exploreAnchor),
        sourceKey = exploreSourceKey ?: DESKTOP_EXPLORE_SOURCE,
        sourceChoices = sourceUi.sourceRows.filter { it.enabled }.map {
            ExploreSourceChoice(key = it.key, name = it.title)
        },
        onPickSource = { key ->
            petSource(sourceUi.sourceRows.firstOrNull { it.key == key }?.title.orEmpty())
            if (key != (exploreSourceKey ?: DESKTOP_EXPLORE_SOURCE)) {
                exploreAnchor.reset()
                exploreSourceKey = key
                exploreSections = emptyList()
                explorePickUi.pick = null
                explorePickUi.page = null
                explorePickPageNo = 1
                explorePickUi.maxPage = null
            }
        },
        modes = exploreModes,
        selectedMode = exploreModeIndex,
        onPickMode = { index ->
            if (index != exploreModeIndex) {
                exploreModeIndex = index
                exploreSections = emptyList()
                exploreTick++
            }
        },
        categoryParts = exploreCategoryBoard.parts,
        rankingChips = exploreCategoryBoard.ranking,
        onPickCategory = { partName, label, param, kind ->
            petFilter()
            explorePickUi.pick = ExplorePick(
                display = "$partName:$label",
                label = label,
                param = param,
                kind = kind,
            )
            explorePickTick++
        },
        onPickRanking = { key ->
            petFilter()
            val label = exploreCategoryBoard.ranking.firstOrNull { it.first == key }?.second ?: key
            explorePickUi.pick = ExplorePick(
                display = "排行榜:$label",
                label = label,
                param = key,
                kind = "ranking",
            )
            explorePickTick++
        },
        pickedLabel = explorePickUi.pick?.display,
        pickedPage = explorePickUi.page,
        pickedLoadingMore = explorePickUi.loading,
        pickedHasMore = (explorePickUi.maxPage ?: 1) > explorePickPageNo,
        onLoadMorePicked = { explorePickMoreTick++ },
        onClearPicked = {
            explorePickUi.pick = null
            explorePickUi.page = null
            explorePickPageNo = 1
            explorePickUi.maxPage = null
        },
        sections = exploreSections,
        note = exploreNote,
        maskedOf = { card ->
            desktopUx.coverMaskStateOf(
                sourceKey = DESKTOP_EXPLORE_SOURCE,
                title = card.title,
                author = "",
                tags = emptyList(),
                comicId = card.id,
            ) != "VISIBLE"
        },
        onOpen = { card ->
            detailUi.detailInfo = null
            detailUi.detailNote = null
            route = DesktopRoute.Detail(
                from = route,
                comicId = card.id,
                title = card.title,
                cover = card.cover,
                sourceKey = exploreSourceKey ?: DESKTOP_EXPLORE_SOURCE,
            )
        },
        onAddFavorites = addFavorite?.let { add ->
            { cards ->
                scope.launch {
                    val sourceKey = exploreSourceKey ?: DESKTOP_EXPLORE_SOURCE
                    val existing = favorites.map { "${it.sourceName}|${it.comicId}" }.toSet()
                    var added = 0
                    cards.forEach { card ->
                        val key = "$sourceKey|${card.id}"
                        if (key !in existing) {
                            val record = FavoriteRecord(
                                comicId = card.id,
                                title = card.title,
                                author = card.author,
                                coverUrl = card.cover,
                                sourceName = sourceKey,
                                tags = card.tags.joinToString(","),
                            )
                            if (runCatching { add(record) }.isSuccess) added++
                        }
                    }
                    detailUi.favoriteTick++
                    desktopUx.toast("已加入 $added 本收藏")
                }
            }
        },
        onReload = { exploreTick++ },
        onOpenSourceManage = { route = DesktopRoute.SourceManage },
        onOpenQueue = {
            filesUi.filesDomain = DesktopFilesDomain.COMIC
            filesSegment = DesktopFilesSegment.QUEUE
            route = DesktopRoute.Files
        },
        onOpenDownloads = if (downloadsRoot == null) null else {
            {
                filesUi.filesDomain = DesktopFilesDomain.COMIC
                filesSegment = DesktopFilesSegment.DOWNLOADED
                route = DesktopRoute.Files
            }
        },
        onOpenLocalComics = if (downloadsRoot == null) null else {
            {
                filesUi.filesDomain = DesktopFilesDomain.COMIC
                filesSegment = DesktopFilesSegment.LOCAL
                route = DesktopRoute.Files
            }
        },
        offlineBadgeOf = { card ->
            desktopComicOfflineBadgeLabelOf(
                badges = filesUi.comicCardBadges,
                sourceKey = DESKTOP_EXPLORE_SOURCE,
                comicId = card.id,
            )
        },
        onOpenHistory = {
            historyTick++
            route = DesktopRoute.History
        },
        onOpenStats = { route = DesktopRoute.ReadingStats },
        onOpenBackup = { route = DesktopRoute.Backup },
        onOpenSettings = { openSettings() },
    )
}
