package com.venera.desktop.app

import com.venera.desktop.ui.BackupUiState
import com.venera.desktop.ui.BrowseClearUiState
import com.venera.desktop.ui.DesktopBackupImportStage
import com.venera.desktop.ui.DesktopBackupScreen
import com.venera.desktop.ui.DesktopReadingStatsScreen
import com.venera.desktop.ui.DetailAndReaderUiState
import com.venera.desktop.ui.SavedUiState
import com.venera.desktop.ui.SourceManageUiState
import com.venera.desktop.ui.pixivWorkRouteOf
import com.venera.desktop.data.DesktopBackupCounts
import com.venera.desktop.data.DesktopBackupModule
import com.venera.desktop.data.DesktopImportStrategy
import com.venera.desktop.data.DesktopPixivWorkTrail
import com.venera.desktop.data.DesktopReadingStats
import com.venera.desktop.data.DesktopRestoreOutcome
import com.venera.desktop.data.DesktopVeneraExportOptions
import com.venera.desktop.data.desktopCanSaveBackupFile
import com.venera.desktop.data.desktopPickBackupOpenPath
import com.venera.desktop.data.desktopPickBackupSavePath
import com.venera.desktop.data.desktopVeneraDefaultFileName
import com.venera.desktop.data.desktopVeneraExportTo
import com.venera.desktop.data.desktopVeneraImportFromPreview
import com.venera.desktop.data.desktopVeneraInspectFile
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.compose.data.platform.PathProvider
import com.venera.compose.gallery.data.GalleryContentGuard
import com.venera.compose.gallery.data.GalleryFavorite
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryPreviewQuality
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.business.DesktopBusinessPorts
import com.venera.desktop.gallery.data.DesktopGalleryPreferences
import com.venera.desktop.gallery.ui.DesktopArtistInboxScreen
import com.venera.desktop.gallery.ui.DesktopGallerySavedScreen
import com.venera.desktop.gallery.ui.DesktopGallerySavedView
import com.venera.desktop.gallery.ui.DesktopReverseSearchScreen
import com.venera.desktop.gallery.ui.DesktopWallCardModel
import com.venera.desktop.gallery.ui.boardOf
import com.venera.desktop.gallery.ui.desktopBrowseRecoveryNote
import com.venera.desktop.gallery.ui.desktopClearGalleryFavorites
import com.venera.desktop.gallery.ui.desktopComicHistoryOpenSourceOf
import com.venera.desktop.gallery.ui.DesktopComicHistoryRowGate
import com.venera.desktop.gallery.ui.desktopComicHistoryRowGate
import com.venera.desktop.gallery.ui.desktopComicHistoryRowGateNote
import com.venera.desktop.gallery.ui.desktopFavoritesClearReading
import com.venera.desktop.gallery.ui.desktopWallGuardedCards
import com.venera.desktop.history.DesktopHistoryScreen
import com.venera.desktop.platform.DesktopWebDavSync
import com.venera.desktop.ui.DesktopBackButton
import com.venera.desktop.ui.components.DesktopPetCue
import com.venera.desktop.ui.components.DesktopPetCueBus
import java.awt.Desktop
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import java.awt.dnd.DropTargetEvent
import java.io.File
import javax.swing.JFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 画廊反向以图搜图、收藏历史合并页与关注画师新作箱路由实现。
 */
@Composable
internal fun DesktopGallerySavedAndReverseRoute(
    r: DesktopRoute,
    layoutMode: WideScreenLayoutMode,
    ports: GalleryPorts,
    scope: CoroutineScope,
    galleryPaths: PathProvider?,
    galleryPrefs: DesktopGalleryPreferences?,
    maskModeOf: () -> String,
    wallGuardOf: () -> GalleryContentGuard?,
    wallBlockAiOf: () -> Boolean,
    galleryFavorites: List<GalleryFavorite>,
    galleryWallQuality: GalleryPreviewQuality,
    galleryWallCards: List<DesktopWallCardModel>,
    galleryWallBlocked: Int,
    galleryWallUnrenderable: String,
    browseClearUi: BrowseClearUiState,
    openWallCardAny: (DesktopWallCardModel) -> Unit,
    routeState: MutableState<DesktopRoute>,
    savedUiState: MutableState<SavedUiState>,
    galleryWallTickState: MutableIntState,
) {
    var route by routeState
    var savedUi by savedUiState
    var galleryWallTick by galleryWallTickState

    when (r) {
        DesktopRoute.GalleryReverseSearch -> DesktopReverseSearchScreen(
            mode = layoutMode,
            onBack = { route = DesktopRoute.Tab },
            onOpenRoute = { route = it },
            onOpenPost = openWallCardAny,
        )

        DesktopRoute.GalleryFavorites, DesktopRoute.GalleryBrowseHistory -> DesktopGallerySavedScreen(
            mode = layoutMode,
            view = savedUi.view,
            onPickView = { savedUi = savedUi.copy(view = it) },
            query = savedUi.query,
            onQueryChange = { savedUi = savedUi.copy(query = it) },
            siteKey = savedUi.siteKey,
            onPickSite = { savedUi = savedUi.copy(siteKey = it) },
            axis = GallerySite.entries,
            favorites = galleryWallCards,
            favoriteTotal = galleryFavorites.size,
            favoriteBlocked = galleryWallBlocked,
            history = browseClearUi.browseRows.orEmpty(),
            historyLoaded = browseClearUi.browseRows != null,
            clearText = if (savedUi.view == DesktopGallerySavedView.FAVORITES) "清空收藏" else "清空记录",
            clearCount = if (savedUi.view == DesktopGallerySavedView.FAVORITES) {
                galleryFavorites.size
            } else {
                browseClearUi.browseRows.orEmpty().size
            },
            onClear = {
                if (savedUi.view == DesktopGallerySavedView.FAVORITES) {
                    scope.launch {
                        val outcome = desktopClearGalleryFavorites(ports.favorites, galleryFavorites.size)
                        browseClearUi.galleryClearNote = desktopFavoritesClearReading(outcome)
                        galleryWallTick++
                    }
                } else {
                    val store = browseClearUi.browseStore
                    if (store == null) {
                        browseClearUi.browseNote = "那份档还没读回来，清不了"
                    } else {
                        scope.launch {
                            runCatching { store.clear() }
                                .onSuccess {
                                    browseClearUi.browseRows = store.rows.value
                                    browseClearUi.browseNote = null
                                }
                                .onFailure { t -> browseClearUi.browseNote = "没清掉：${t.javaClass.simpleName}" }
                        }
                    }
                }
            },
            clearNote = if (savedUi.view == DesktopGallerySavedView.FAVORITES) browseClearUi.galleryClearNote else null,
            note = if (savedUi.view == DesktopGallerySavedView.FAVORITES) {
                galleryWallUnrenderable.takeIf { it.isNotEmpty() }
            } else {
                browseClearUi.browseNote
            },
            onOpenFavorite = { card -> openWallCardAny(card) },
            onRemoveFavorite = { card ->
                DesktopPetCueBus.emit(DesktopPetCue.REMOVE_FAVORITE)
                card.sourcePost?.let { post ->
                    scope.launch { runCatching { ports.favorites.toggle(post) } }
                }
            },
            onRemoveFavorites = { cards ->
                DesktopPetCueBus.emit(DesktopPetCue.REMOVE_FAVORITE)
                val uids = cards.mapNotNull { it.sourcePost?.uid ?: it.uid.takeIf(String::isNotBlank) }
                if (uids.isNotEmpty()) {
                    scope.launch {
                        runCatching { ports.favorites.removeAll(uids) }
                            .onSuccess { galleryWallTick++ }
                    }
                }
            },
            onOpenHistory = { row ->
                if (row.site == GallerySite.PIXIV) {
                    browseClearUi.browseNote = null
                    DesktopPixivWorkTrail.clear()
                    route = pixivWorkRouteOf(
                        illustId = row.postId,
                        title = "pixiv #${row.postId}",
                        coverUrl = row.thumbUrl,
                        from = route,
                    )
                } else {
                    scope.launch {
                        val outcome = withContext(Dispatchers.IO) {
                            runCatching { boardOf(ports, row.site).fetchById(row.postId).getOrThrow() }
                        }
                        val fetched = outcome.getOrNull()
                        val failure = outcome.exceptionOrNull()
                        val card = if (fetched == null) null else withContext(Dispatchers.IO) {
                            desktopWallGuardedCards(
                                listOf(fetched),
                                galleryWallQuality,
                                wallGuardOf(),
                                maskModeOf(),
                                wallBlockAiOf(),
                            ).firstOrNull()
                        }
                        val recovery = desktopBrowseRecoveryNote(row, fetched, failure, card)
                        if (recovery == null && card != null) {
                            browseClearUi.browseNote = null
                            route = DesktopRoute.GalleryPostViewer(card, from = route)
                        } else {
                            browseClearUi.browseNote = recovery
                        }
                    }
                }
            },
            onRemoveHistory = { row ->
                val store = browseClearUi.browseStore
                if (store == null) {
                    browseClearUi.browseNote = "那份档还没读回来，删不了这一条"
                } else {
                    scope.launch {
                        runCatching { store.removeOne(row.site.routeKey, row.postId) }
                            .onSuccess {
                                browseClearUi.browseRows = store.rows.value
                                browseClearUi.browseNote = null
                            }
                            .onFailure { t -> browseClearUi.browseNote = "这一条没删掉：${t.javaClass.simpleName}" }
                    }
                }
            },
            onRemoveHistories = { rows ->
                val store = browseClearUi.browseStore
                if (store == null) {
                    browseClearUi.browseNote = "那份档还没读回来，删不了这些"
                } else {
                    scope.launch {
                        runCatching { store.removeMany(rows.map { it.site.routeKey to it.postId }) }
                            .onSuccess {
                                browseClearUi.browseRows = store.rows.value
                                browseClearUi.browseNote = null
                            }
                            .onFailure { t -> browseClearUi.browseNote = "没删掉：${t.javaClass.simpleName}" }
                    }
                }
            },
            onBack = { route = DesktopRoute.Tab },
        )

        DesktopRoute.FollowedArtists -> DesktopArtistInboxScreen(
            mode = layoutMode,
            ports = ports,
            paths = galleryPaths,
            prefs = galleryPrefs,
            guard = wallGuardOf(),
            maskMode = maskModeOf(),
            blockAi = wallBlockAiOf(),
            onOpenPost = openWallCardAny,
            onBack = { route = DesktopRoute.Tab },
            onOpenPixivAccount = { route = DesktopRoute.PixivAccount },
            onOpenArtist = null,
        )

        else -> Unit
    }
}

/**
 * 漫画阅读历史与阅读统计路由实现。
 */
@Composable
internal fun DesktopHistoryAndStatsRoute(
    r: DesktopRoute,
    layoutMode: WideScreenLayoutMode,
    desktopUx: DesktopUxPlatformServices,
    businessHandle: DesktopBusinessPorts.Handle?,
    historySourceName: String?,
    readingStats: DesktopReadingStats,
    detailUi: DetailAndReaderUiState,
    sourceUi: SourceManageUiState,
    routeState: MutableState<DesktopRoute>,
) {
    var route by routeState
    when (r) {
        DesktopRoute.History -> {
            val handle = businessHandle
            if (handle == null) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(VeneraTokens.spacing.screenHorizontal),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("无法打开历史", color = VeneraTokens.color.textSecondary)
                    Spacer(Modifier.height(VeneraTokens.spacing.space4))
                    DesktopBackButton(onClick = { route = DesktopRoute.Tab })
                }
            } else {
                val historyScope = remember(handle) { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
                DisposableEffect(historyScope) {
                    onDispose { historyScope.cancel() }
                }
                val historyCore = remember(handle) {
                    DesktopBusinessPorts.createHistoryCore(handle, historyScope)
                }
                val historyRecords = historyCore.history.collectAsState().value
                DesktopHistoryScreen(
                    mode = layoutMode,
                    records = historyRecords,
                    loaded = true,
                    note = null,
                    countNote = if (historyRecords.isEmpty()) "" else "${historyRecords.size} 条",
                    maskedOf = { record ->
                        desktopUx.coverMaskStateOf(
                            sourceKey = record.sourceName,
                            title = record.title,
                            author = record.author,
                            comicId = record.comicId,
                        ) != "VISIBLE"
                    },
                    openableOf = { record ->
                        val gate = desktopComicHistoryRowGate(record, DESKTOP_EXPLORE_SOURCE, historySourceName)
                        gate == DesktopComicHistoryRowGate.OPEN || (record.sourceName.isNotBlank() && sourceUi.sourceRows.any {
                            it.key.equals(record.sourceName, ignoreCase = true) ||
                                it.title.equals(record.sourceName, ignoreCase = true)
                        })
                    },
                    noteOf = { record ->
                        val isRecognized = record.sourceName.isNotBlank() && sourceUi.sourceRows.any {
                            it.key.equals(record.sourceName, ignoreCase = true) ||
                                it.title.equals(record.sourceName, ignoreCase = true)
                        }
                        if (isRecognized) null else desktopComicHistoryRowGateNote(
                            desktopComicHistoryRowGate(record, DESKTOP_EXPLORE_SOURCE, historySourceName),
                        )
                    },
                    onOpen = { record ->
                        petWork(record.title, record.author)
                        val key = desktopComicHistoryOpenSourceOf(
                            record = record,
                            currentSourceKey = DESKTOP_EXPLORE_SOURCE,
                            currentSourceName = historySourceName,
                        ) ?: sourceUi.sourceRows.firstOrNull { src ->
                            src.key.equals(record.sourceName, ignoreCase = true) ||
                                src.title.equals(record.sourceName, ignoreCase = true)
                        }?.key ?: record.sourceName.takeIf { it.isNotBlank() }
                        key?.let { effectiveKey ->
                            detailUi.detailInfo = null
                            detailUi.detailNote = null
                            route = DesktopRoute.Detail(
                                from = route,
                                comicId = record.comicId,
                                title = record.title,
                                cover = record.coverUrl,
                                sourceKey = effectiveKey,
                            )
                        }
                    },
                    onClearAll = { historyCore.clearAll() },
                    onDeleteRecords = { list -> historyCore.deleteRecords(list) },
                    onBack = { route = DesktopRoute.Tab },
                )
            }
        }

        DesktopRoute.ReadingStats -> DesktopReadingStatsScreen(
            mode = layoutMode,
            stats = readingStats,
            onBack = { route = DesktopRoute.Tab },
        )

        else -> Unit
    }
}

/**
 * 备份与还原工作台（本地 .venera 归档 + WebDAV 同步）路由实现。
 */
@Composable
internal fun DesktopBackupRoute(
    layoutMode: WideScreenLayoutMode,
    scope: CoroutineScope,
    galleryPaths: PathProvider?,
    assetDir: File?,
    overrideDir: File?,
    backupUi: BackupUiState,
    routeState: MutableState<DesktopRoute>,
) {
    var route by routeState
    val davState by DesktopWebDavSync.state.collectAsState()
    val davLastSync by DesktopWebDavSync.lastSync.collectAsState()
    val inspectAndPreview: (String) -> Unit = { targetPath ->
        backupUi.lastPath = targetPath
        backupUi.busy = true
        backupUi.armed = false
        backupUi.note = null
        scope.launch {
            desktopVeneraInspectFile(targetPath).fold(
                onSuccess = { parsed ->
                    backupUi.preview = parsed
                    backupUi.selectedModules = DesktopBackupModule.entries
                        .filter { m -> parsed.isModuleAvailable(m) && m != DesktopBackupModule.SOURCE_ACCOUNTS }
                        .toSet()
                    backupUi.importStrategy = DesktopImportStrategy.MERGE
                    backupUi.importStage = DesktopBackupImportStage.PREVIEW
                },
                onFailure = { t ->
                    backupUi.note = "解析失败：${t.message?.take(160) ?: t.javaClass.simpleName}"
                },
            )
            backupUi.busy = false
        }
    }

    DisposableEffect(backupUi.importStage) {
        if (backupUi.importStage != DesktopBackupImportStage.IDLE || GraphicsEnvironment.isHeadless()) {
            backupUi.dragOver = false
            return@DisposableEffect onDispose {}
        }
        val pane = (Frame.getFrames().firstOrNull { it is JFrame && it.isVisible } as? JFrame)?.contentPane
        val target = pane?.let { host ->
            DropTarget(host, object : DropTargetAdapter() {
                override fun dragEnter(e: DropTargetDragEvent) {
                    if (e.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        backupUi.dragOver = true
                        e.acceptDrag(DnDConstants.ACTION_COPY)
                    } else {
                        backupUi.dragOver = false
                        e.rejectDrag()
                    }
                }

                override fun dragExit(e: DropTargetEvent) {
                    backupUi.dragOver = false
                }

                override fun drop(e: DropTargetDropEvent) {
                    backupUi.dragOver = false
                    if (!e.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        e.dropComplete(false)
                        return
                    }
                    e.acceptDrop(DnDConstants.ACTION_COPY)
                    val files = runCatching {
                        e.transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>
                    }.getOrNull()?.filterIsInstance<File>().orEmpty()
                    e.dropComplete(true)
                    val archive = files.firstOrNull {
                        it.isFile && (it.name.endsWith(".venera", true) || it.name.endsWith(".json", true))
                    } ?: return
                    inspectAndPreview(archive.absolutePath)
                }
            })
        }
        pane?.dropTarget = target
        onDispose {
            backupUi.dragOver = false
            pane?.dropTarget = null
        }
    }

    DesktopBackupScreen(
        mode = layoutMode,
        assetCounts = backupUi.assetCounts,
        note = backupUi.note,
        includeSourceAccounts = backupUi.includeSourceAccounts,
        importStage = backupUi.importStage,
        preview = backupUi.preview,
        selectedModules = backupUi.selectedModules,
        importStrategy = backupUi.importStrategy,
        lastRestore = backupUi.veneraRestore,
        stagingFileName = backupUi.stagingFile?.name,
        davConfig = backupUi.davConfig,
        davState = davState,
        davLastSync = davLastSync,
        armed = backupUi.armed,
        busy = backupUi.busy,
        canFileDialog = desktopCanSaveBackupFile(),
        importDragOver = backupUi.dragOver,
        onToggleIncludeAccounts = { backupUi.includeSourceAccounts = it },
        onExport = {
            val path = desktopPickBackupSavePath(null, desktopVeneraDefaultFileName(System.currentTimeMillis()))
            if (path == null) {
                backupUi.note = "未导出"
            } else {
                backupUi.lastPath = path
                backupUi.busy = true
                backupUi.note = null
                scope.launch {
                    desktopVeneraExportTo(
                        path = path,
                        paths = galleryPaths,
                        assetDir = assetDir,
                        overrideDir = overrideDir,
                        options = DesktopVeneraExportOptions(backupUi.includeSourceAccounts),
                    ).fold(
                        onSuccess = { c ->
                            backupUi.lastCounts = DesktopBackupCounts(c.history, c.stats, c.guardRules)
                            backupUi.note = "已导出 .venera 归档：${File(path).name}"
                        },
                        onFailure = { t ->
                            backupUi.note = "导出失败：${t.message?.take(160) ?: t.javaClass.simpleName}"
                        },
                    )
                    backupUi.busy = false
                }
            }
        },
        onOpenDataDir = {
            val dir = galleryPaths?.dataRoot
            if (dir != null) {
                runCatching {
                    if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(dir)
                }.onFailure {
                    backupUi.note = "无法打开目录：${dir.absolutePath}"
                }
            }
        },
        onPickImportFile = {
            val path = desktopPickBackupOpenPath(null)
            if (path != null) inspectAndPreview(path)
        },
        onInspectStagingFile = {
            val f = backupUi.stagingFile
            if (f != null && f.isFile) inspectAndPreview(f.absolutePath)
            else backupUi.note = "中转文件不存在，请重新拉取或从本机选取"
        },
        onClearPreview = {
            backupUi.armed = false
            backupUi.preview = null
            backupUi.importStage = DesktopBackupImportStage.IDLE
        },
        onSelectStrategy = { s ->
            backupUi.importStrategy = s
            backupUi.armed = false
        },
        onToggleModule = { m ->
            backupUi.armed = false
            backupUi.selectedModules = if (m in backupUi.selectedModules) {
                backupUi.selectedModules - m
            } else {
                backupUi.selectedModules + m
            }
        },
        onSelectAllModules = { all ->
            backupUi.armed = false
            val p = backupUi.preview
            backupUi.selectedModules = if (all && p != null) {
                DesktopBackupModule.entries.filter { p.isModuleAvailable(it) }.toSet()
            } else {
                emptySet()
            }
        },
        onArmRestore = {
            backupUi.armed = true
            backupUi.note = "再次点击「确认覆盖所选数据」将先清空所选模块后写入"
        },
        onConfirmRestore = {
            val parsed = backupUi.preview
            if (parsed != null) {
                backupUi.busy = true
                backupUi.armed = false
                backupUi.note = null
                scope.launch {
                    desktopVeneraImportFromPreview(
                        preview = parsed,
                        selected = backupUi.selectedModules,
                        strategy = backupUi.importStrategy,
                        paths = galleryPaths,
                        assetDir = assetDir,
                    ).fold(
                        onSuccess = { outcome ->
                            backupUi.veneraRestore = outcome
                            backupUi.lastRestore = DesktopRestoreOutcome(
                                history = outcome.history,
                                stats = outcome.stats,
                                guardImported = outcome.guardImported,
                                guardSkipped = outcome.guardSkipped,
                            )
                            backupUi.importStage = DesktopBackupImportStage.DONE
                            backupUi.assetRefreshTick++
                        },
                        onFailure = { t ->
                            backupUi.note = "导入失败：${t.message?.take(160) ?: t.javaClass.simpleName}"
                        },
                    )
                    backupUi.busy = false
                }
            }
        },
        onResetImportDone = {
            backupUi.armed = false
            backupUi.preview = null
            backupUi.importStage = DesktopBackupImportStage.IDLE
        },
        onSaveDavConfig = { cfg ->
            galleryPaths?.let { p ->
                DesktopWebDavSync.saveConfig(p, cfg)
                backupUi.davConfig = DesktopWebDavSync.readConfig(p)
                backupUi.note = "WebDAV 配置已保存"
            }
        },
        onTestDavConnection = { cfg ->
            scope.launch { DesktopWebDavSync.testConnection(cfg) }
        },
        onDavUpload = {
            galleryPaths?.let { p ->
                scope.launch {
                    DesktopWebDavSync.upload(
                        paths = p,
                        assetDir = assetDir,
                        overrideDir = overrideDir,
                        includeSourceAccounts = backupUi.includeSourceAccounts,
                    )
                }
            }
        },
        onDavDownloadAndPreview = {
            galleryPaths?.let { p ->
                scope.launch {
                    val st = DesktopWebDavSync.downloadLatest(p)
                    val downloaded = DesktopWebDavSync.lastDownloadedFile.value
                    if (st is DesktopWebDavSync.State.Done && downloaded != null) {
                        backupUi.stagingFile = downloaded
                        inspectAndPreview(downloaded.absolutePath)
                    }
                }
            }
        },
        onDismissNote = { backupUi.note = null },
        onBack = {
            backupUi.armed = false
            route = DesktopRoute.Tab
        },
    )
}
