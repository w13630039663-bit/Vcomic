package com.venera.desktop.app

import com.venera.desktop.ui.DetailAndReaderUiState
import com.venera.desktop.ui.FilesAuxUiState
import com.venera.desktop.ui.SourceManageUiState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.venera.compose.components.VeneraNavTab
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.compose.data.db.ComicFollowDao
import com.venera.compose.data.db.FavoriteRecord
import com.venera.compose.data.db.HistoryRecord
import com.venera.compose.data.platform.PathProvider
import com.venera.compose.feature.ComicDetailCore
import com.venera.compose.feature.ComicItem
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.business.DesktopBusinessPorts
import com.venera.desktop.detail.DesktopComicDetailScreen
import com.venera.desktop.detail.DesktopCommentsScreen
import com.venera.desktop.follow.DesktopFollowDetailPort
import com.venera.desktop.gallery.data.DesktopComicDownloadedChapter
import com.venera.desktop.gallery.data.DesktopComicPage
import com.venera.desktop.gallery.data.DesktopComicQueueTask
import com.venera.desktop.gallery.data.DesktopComicReaderPreferences
import com.venera.desktop.gallery.data.DesktopDirChooser
import com.venera.desktop.gallery.data.DesktopGalleryContentGuard
import com.venera.desktop.gallery.data.DesktopGalleryPreferences
import com.venera.desktop.gallery.data.DesktopLocalComicLibraryScan
import com.venera.desktop.gallery.data.DesktopReaderMode
import com.venera.desktop.reader.DesktopReaderScreen
import com.venera.desktop.search.DesktopAggregateSearchHolder
import com.venera.desktop.search.DesktopComicSearchOutcome
import com.venera.desktop.search.DesktopSearchTarget
import com.venera.desktop.settings.DesktopBlockingRulesScreen
import com.venera.desktop.ui.DesktopBackButton
import com.venera.desktop.ui.components.DesktopPetCue
import com.venera.desktop.ui.components.DesktopPetCueBus
import com.venera.engineprobe.EngineSession
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 漫画详情/在线阅读/评论/屏蔽规则及源管理、下载管线二级路由宿主。
 *
 * 源管理与登录页面拆至 [DesktopSourceManageAndLoginRoute]，
 * 文件管理与本地漫画阅读器拆至 [DesktopFilesAndLocalReaderRoutesHost]。
 */
@Composable
internal fun DesktopComicAndSourceHost(
    r: DesktopRoute,
    layoutMode: WideScreenLayoutMode,
    engine: EngineSession?,
    ports: GalleryPorts,
    scope: CoroutineScope,
    downloadsRoot: File?,
    galleryPaths: PathProvider?,
    galleryPrefs: DesktopGalleryPreferences?,
    galleryGuard: DesktopGalleryContentGuard?,
    readerPrefs: DesktopComicReaderPreferences?,
    desktopUx: DesktopUxPlatformServices,
    businessHandle: DesktopBusinessPorts.Handle?,
    queueTasks: List<DesktopComicQueueTask>,
    queueConcurrency: Int,
    queuePrimed: Boolean,
    favoritesLoaded: Boolean,
    favoritesState: MutableState<List<FavoriteRecord>>,
    favoriteFolderNames: List<String>,
    localComicScan: DesktopLocalComicLibraryScan?,
    readerMode: DesktopReaderMode,
    readerModeNote: String?,
    detailCoreCache: MutableMap<String, Pair<CoroutineScope, ComicDetailCore>>,
    sourceUi: SourceManageUiState,
    filesUi: FilesAuxUiState,
    detailUi: DetailAndReaderUiState,
    aggregateSearch: DesktopAggregateSearchHolder,
    runSourceImport: () -> Unit,
    writeSourceEnabled: ((String, Boolean) -> Unit)?,
    writeSourceOrder: ((List<String>) -> Unit)?,
    readSourceText: ((String) -> String?)?,
    clearSourceOverride: (suspend (String) -> Unit)?,
    loginWithCookies: (suspend (String, List<String>) -> String)?,
    loginWithPassword: (suspend (String, String, String) -> String)?,
    loginWithWebview: (suspend (String) -> String)?,
    writeHistory: (suspend (HistoryRecord) -> Unit)?,
    addFavorite: (suspend (FavoriteRecord) -> Unit)?,
    removeFavorite: (suspend (String) -> Unit)?,
    createFavoriteFolder: (suspend (String) -> Unit)?,
    deleteFavoriteFolder: (suspend (String) -> Unit)?,
    openSettings: () -> Unit,
    searchComics: (suspend (String, Int, String, List<String?>) -> DesktopComicSearchOutcome)?,
    onDownloadsDelete: (DesktopComicDownloadedChapter, Boolean) -> Unit,
    flyToDownloadCenter: (String?, Boolean) -> Unit,
    routeState: MutableState<DesktopRoute>,
    currentTabState: MutableState<VeneraNavTab>,
    commentsTickState: MutableIntState,
    readerPagesState: MutableState<List<DesktopComicPage>>,
    searchReturnRouteState: MutableState<DesktopRoute?>,
    sourceTickState: MutableIntState,
    downloadsTickState: MutableIntState,
    localComicImportTickState: MutableState<Int>,
    armedDeleteKeyState: MutableState<String?>,
    readerModeChoiceState: MutableState<DesktopReaderMode?>,
    searchQueryState: MutableState<String>,
    searchSourceKeyState: MutableState<String?>,
    searchTickState: MutableIntState,
    followDao: ComicFollowDao? = null,
) {
    val favorites by favoritesState
    var route by routeState
    var currentTab by currentTabState
    var commentsTick by commentsTickState
    var readerPages by readerPagesState
    var searchReturnRoute by searchReturnRouteState
    var readerModeChoice by readerModeChoiceState
    var searchQuery by searchQueryState
    var searchSourceKey by searchSourceKeyState
    var searchTick by searchTickState

    when (r) {
        is DesktopRoute.SourceManage,
        is DesktopRoute.SourceEdit,
        is DesktopRoute.SourceLogin,
        -> DesktopSourceManageAndLoginRoute(
            r = r,
            layoutMode = layoutMode,
            engine = engine,
            scope = scope,
            sourceUi = sourceUi,
            runSourceImport = runSourceImport,
            writeSourceEnabled = writeSourceEnabled,
            writeSourceOrder = writeSourceOrder,
            readSourceText = readSourceText,
            clearSourceOverride = clearSourceOverride,
            loginWithCookies = loginWithCookies,
            loginWithPassword = loginWithPassword,
            loginWithWebview = loginWithWebview,
            routeState = routeState,
            sourceTickState = sourceTickState,
        )

        is DesktopRoute.Detail -> {
            val handle = businessHandle
            if (handle == null) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(VeneraTokens.spacing.screenHorizontal),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("无法打开详情", color = VeneraTokens.color.textSecondary)
                    Spacer(Modifier.height(VeneraTokens.spacing.space4))
                    DesktopBackButton(onClick = { route = DesktopRoute.Tab })
                }
            } else {
                val detailCacheKey = "${r.sourceKey}|${r.comicId}"
                val detailCached = detailCoreCache[detailCacheKey]
                val detailScope = detailCached?.first ?: CoroutineScope(SupervisorJob() + Dispatchers.IO)
                val detailCore = detailCached?.second
                    ?: DesktopBusinessPorts.createDetailCore(handle, detailScope)
                if (detailCached == null) {
                    detailCoreCache[detailCacheKey] = detailScope to detailCore
                    while (detailCoreCache.size > 4) {
                        val oldest = detailCoreCache.entries.firstOrNull { it.key != detailCacheKey } ?: break
                        oldest.value.first.cancel()
                        detailCoreCache.remove(oldest.key)
                    }
                }
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
                val comic = ComicItem(
                    id = r.comicId,
                    title = r.title,
                    author = "",
                    coverUrl = r.cover,
                    sourceName = r.sourceKey,
                )
                val desktopFavAddedFolders = favorites
                    .filter { it.comicId == r.comicId }
                    .map { it.folderName }
                    .toSet()
                DesktopComicDetailScreen(
                    downloadDirLabel = downloadsRoot?.absolutePath,
                    onPickDownloadDir = galleryPrefs?.let { prefs ->
                        {
                            val picked = DesktopDirChooser.pickDirectory(
                                "选择漫画下载目录",
                                downloadsRoot?.absolutePath,
                            )
                            if (picked != null) prefs.setComicDownloadDir(picked)
                        }
                    },
                    followPort = remember(detailCacheKey, followDao) {
                        followDao?.let { DesktopFollowDetailPort(it) }
                    },
                    localFavoriteFolders = favoriteFolderNames,
                    localFavoriteAdded = desktopFavAddedFolders,
                    localFavorited = if (!favoritesLoaded) null else desktopFavAddedFolders.isNotEmpty(),
                    onLocalFavoriteToggle = { folder, added ->
                        DesktopPetCueBus.emit(if (added) DesktopPetCue.REMOVE_FAVORITE else DesktopPetCue.ADD_BOOKSHELF)
                        scope.launch {
                            if (added) {
                                runCatching { removeFavorite?.invoke(r.comicId) }
                            } else {
                                runCatching {
                                    addFavorite?.invoke(
                                        FavoriteRecord(
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
                    onLocalFavoriteFolderCreate = createFavoriteFolder?.let { create ->
                        { name ->
                            scope.launch {
                                runCatching { create.invoke(name) }
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
                        val chapter = session.chapters.getOrNull(session.initialChapterIndex)
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
                                episode = EngineSession.Episode(
                                    name = chapter.title,
                                    id = chapter.id,
                                    placeholder = chapter.id == session.comicId,
                                ),
                                startPage = session.initialPageIndex,
                            )
                        }
                    },
                    onSearchTag = { tag ->
                        if (searchComics != null) {
                            val tagRow = sourceUi.sourceRows.firstOrNull {
                                it.key.equals(r.sourceKey, ignoreCase = true) ||
                                    it.title.equals(r.sourceKey, ignoreCase = true)
                            }
                            if (tagRow != null) {
                                searchSourceKey = tagRow.key
                                aggregateSearch.target = DesktopSearchTarget.Single(tagRow.key)
                            } else {
                                aggregateSearch.target = DesktopSearchTarget.Single(
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
            startPage = r.startPage,
            episodeKey = r.episode.id,
            comicTitle = r.title,
            sourceKey = r.sourceKey,
            onPageChange = { DesktopPetCueBus.emit(DesktopPetCue.TURN_PAGE); detailUi.readerPage = it },
            onBack = { route = r.from ?: DesktopRoute.Detail(r.comicId, r.title, r.cover, r.sourceKey) },
            readerMode = readerMode,
            seedNote = readerModeNote,
            onPickMode = readerPrefs?.let { prefs ->
                { m ->
                    prefs.setReadingMode(m)
                    readerModeChoice = m
                }
            },
            episodes = detailUi.detailInfo?.episodes?.map { it.name.ifBlank { it.id } },
            chapterIndex = detailUi.detailInfo?.episodes?.indexOfFirst { it.id == r.episode.id } ?: -1,
            onGoChapter = if (detailUi.detailInfo == null) null else { index ->
                val ep = detailUi.detailInfo?.episodes?.getOrNull(index)
                if (ep != null) {
                    detailUi.readerPage = 0
                    readerPages = emptyList()
                    detailUi.readerNote = "正在取这一话的页…"
                    route = r.copy(episode = ep, startPage = 0)
                }
            },
        )

        DesktopRoute.Files,
        DesktopRoute.Queue,
        DesktopRoute.Downloads,
        DesktopRoute.LocalComics,
        -> DesktopFilesAndQueueRoutes(
            r = r,
            layoutMode = layoutMode,
            scope = scope,
            downloadsRoot = downloadsRoot,
            galleryPaths = galleryPaths,
            galleryPrefs = galleryPrefs,
            desktopUx = desktopUx,
            queueTasks = queueTasks,
            queueConcurrency = queueConcurrency,
            queuePrimed = queuePrimed,
            localComicScan = localComicScan,
            filesUi = filesUi,
            openSettings = openSettings,
            onDownloadsDelete = onDownloadsDelete,
            routeState = routeState,
            downloadsTickState = downloadsTickState,
            localComicImportTickState = localComicImportTickState,
            armedDeleteKeyState = armedDeleteKeyState,
        )

        is DesktopRoute.LocalComicReader -> DesktopLocalComicReaderRoute(
            r = r,
            downloadsRoot = downloadsRoot,
            writeHistory = writeHistory,
            detailUi = detailUi,
            routeState = routeState,
        )

        is DesktopRoute.ComicComments -> DesktopCommentsScreen(
            mode = layoutMode,
            title = r.title,
            view = detailUi.commentsView,
            onLoadMore = { commentsTick++ },
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

        is DesktopRoute.BlockingRules -> DesktopBlockingRulesScreen(
            type = r.type,
            guard = galleryGuard,
            onBack = { route = DesktopRoute.Settings },
        )

        else -> Unit
    }
}
