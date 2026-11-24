package com.venera.desktop.app

import com.venera.desktop.ui.DetailAndReaderUiState
import com.venera.desktop.ui.FilesAuxUiState
import com.venera.desktop.ui.pixivWorkRouteOf
import com.venera.desktop.data.DesktopPixivWorkTrail
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.compose.data.db.HistoryRecord
import com.venera.compose.data.platform.PathProvider
import com.venera.desktop.downloads.DesktopComicDownloadsScreen
import com.venera.desktop.downloads.DesktopDownloadsDb
import com.venera.desktop.downloads.DesktopDownloadsPipelineScreen
import com.venera.desktop.downloads.DesktopLocalComicLibraryScreen
import com.venera.desktop.downloads.deleteDesktopLocalImages
import com.venera.desktop.downloads.desktopImageDeleteReceipt
import com.venera.desktop.gallery.data.DesktopComicDownloadQueue
import com.venera.desktop.gallery.data.DesktopComicDownloadedChapter
import com.venera.desktop.gallery.data.DesktopComicPage
import com.venera.desktop.gallery.data.DesktopComicQueueStatus
import com.venera.desktop.gallery.data.DesktopComicQueueTask
import com.venera.desktop.gallery.data.DesktopComicShelfRawIdIndex
import com.venera.desktop.gallery.data.DesktopDirChooser
import com.venera.desktop.gallery.data.DesktopGalleryPreferences
import com.venera.desktop.gallery.data.DesktopLocalComicLibraryScan
import com.venera.desktop.gallery.data.DesktopPixivImageBytes
import com.venera.desktop.gallery.data.DesktopReadingProgress
import com.venera.desktop.gallery.data.desktopComicLocalChapter
import com.venera.desktop.gallery.data.desktopComicQueueDir
import com.venera.desktop.gallery.data.desktopComicRepairChapter
import com.venera.desktop.gallery.data.desktopGallerySaveDir
import com.venera.desktop.gallery.data.desktopWriteReadingHistory
import com.venera.desktop.gallery.data.restoreSafeComicId
import com.venera.desktop.gallery.ui.desktopComicQueueRemovedLine
import com.venera.desktop.gallery.ui.desktopComicQueueStoppedLine
import com.venera.desktop.platform.desktopPickFile
import com.venera.desktop.queue.DesktopComicQueueScreen
import com.venera.desktop.reader.DesktopReaderScreen
import com.venera.desktop.ui.components.DesktopPetCue
import com.venera.desktop.ui.components.DesktopPetCueBus
import com.venera.engineprobe.EngineSession
import java.awt.Desktop
import java.awt.FileDialog
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 下载管线、下载队列、本机已下载与本地漫画库路由实现（[DesktopRoute.Files] / [DesktopRoute.Queue] /
 * [DesktopRoute.Downloads] / [DesktopRoute.LocalComics]）。
 */
@Composable
internal fun DesktopFilesAndQueueRoutes(
    r: DesktopRoute,
    layoutMode: WideScreenLayoutMode,
    scope: CoroutineScope,
    downloadsRoot: File?,
    galleryPaths: PathProvider?,
    galleryPrefs: DesktopGalleryPreferences?,
    desktopUx: DesktopUxPlatformServices,
    queueTasks: List<DesktopComicQueueTask>,
    queueConcurrency: Int,
    queuePrimed: Boolean,
    localComicScan: DesktopLocalComicLibraryScan?,
    filesUi: FilesAuxUiState,
    openSettings: () -> Unit,
    onDownloadsDelete: (DesktopComicDownloadedChapter, Boolean) -> Unit,
    routeState: MutableState<DesktopRoute>,
    downloadsTickState: MutableIntState,
    localComicImportTickState: MutableState<Int>,
    armedDeleteKeyState: MutableState<String?>,
) {
    var route by routeState
    var downloadsTick by downloadsTickState
    var localComicImportTick by localComicImportTickState
    var armedDeleteKey by armedDeleteKeyState
    val ux = com.venera.compose.components.LocalUxPlatformServices.current

    val localImportPickAction: (() -> Unit)? = downloadsRoot?.let { root ->
        {
            val startDir = File(root, "imported").takeIf { it.isDirectory }?.absolutePath
            val picked = desktopPickFile(
                owner = null,
                mode = FileDialog.LOAD,
                title = "导入 CBZ / ZIP 漫画包",
                initialDir = startDir,
                filterPattern = "*.cbz;*.zip;*.CBZ;*.ZIP",
            )?.let { File(it) }
            picked?.let {
                filesUi.localComicImportPick = it
                filesUi.localComicNote = null
            }
        }
    }

    val downloadsRepairAction: (DesktopComicDownloadedChapter) -> Unit = { chapter ->
        val root = downloadsRoot
        filesUi.downloadsNote = if (root == null) {
            "下载根目录拿不到 ⇒ 这一话补不了"
        } else {
            desktopComicRepairChapter(root, chapter, DesktopComicShelfRawIdIndex.of(queueTasks))
        }
    }

    val openDownloadedChapterDir: (DesktopComicDownloadedChapter) -> Unit = { ch ->
        val root = downloadsRoot
        if (root == null) {
            filesUi.downloadsNote = "下载根目录拿不到 ⇒ 目录开不了"
        } else {
            scope.launch {
                val dir = withContext(Dispatchers.IO) {
                    DesktopDownloadsDb.comicChapterDirOf(ch.sourceKey, ch.comicId, ch.chapterId)
                        ?.takeIf { it.isDirectory }
                        ?: desktopComicLocalChapter(root, ch.sourceKey, ch.comicId, ch.chapterId)?.dir
                }
                if (dir == null) {
                    filesUi.downloadsNote = "盘上没再认出这一话 ⇒ 目录开不了"
                } else {
                    runCatching { Desktop.getDesktop().open(dir) }
                }
            }
        }
    }

    when (r) {
        DesktopRoute.Files -> DesktopDownloadsPipelineScreen(
            mode = layoutMode,
            downloadsRoot = downloadsRoot,
            queueTasks = queueTasks,
            queuePrimed = queuePrimed,
            queueNote = filesUi.queueNote,
            onQueuePauseResume = { task ->
                val root = downloadsRoot
                when (task.status) {
                    DesktopComicQueueStatus.RUNNING -> {
                        DesktopComicDownloadQueue.requestStop(task.taskId)
                        filesUi.queueNote = desktopComicQueueStoppedLine(task)
                    }
                    DesktopComicQueueStatus.PENDING -> if (root != null) {
                        DesktopComicDownloadQueue.pause(root, task.taskId)
                        filesUi.queueNote = desktopComicQueueStoppedLine(task)
                    }
                    DesktopComicQueueStatus.STOPPED,
                    DesktopComicQueueStatus.FAILED,
                    -> if (root != null) {
                        DesktopComicDownloadQueue.resume(root, task.taskId)
                        filesUi.queueNote = if (task.status == DesktopComicQueueStatus.FAILED) {
                            "已重试下载：${task.comicTitle} · ${task.chapterTitle}"
                        } else {
                            "已恢复下载：${task.comicTitle} · ${task.chapterTitle}"
                        }
                    }
                    DesktopComicQueueStatus.DONE -> Unit
                }
            },
            onQueueRemove = { task ->
                val root = downloadsRoot
                filesUi.queueNote = if (root == null) {
                    "下载根目录拿不到 ⇒ 这一笔撤不掉"
                } else {
                    desktopComicQueueRemovedLine(task, DesktopComicDownloadQueue.drop(root, task.taskId))
                }
            },
            downloadedListing = filesUi.downloadsListing,
            downloadedNote = filesUi.downloadsNote,
            onRepairDownloadedChapter = downloadsRepairAction,
            onDeleteDownloadedChapter = { onDownloadsDelete(it, true) },
            onOpenDownloadedChapterDir = openDownloadedChapterDir,
            onOpenDownloadedChapter = { ch ->
                val rawIndex = DesktopComicShelfRawIdIndex.of(queueTasks)
                val rawComicId = rawIndex.rawIdOf(ch.sourceKey, ch.comicId) ?: restoreSafeComicId(ch.comicId)
                val mappedTask = queueTasks.firstOrNull { it.comicId == rawComicId || it.comicId == ch.comicId }
                val effectiveTitle = ch.comicTitle.takeIf { it.isNotBlank() && !it.startsWith("http") }
                    ?: mappedTask?.comicTitle?.takeIf { it.isNotBlank() && !it.startsWith("http") }
                    ?: ch.title
                val effectiveCover = ch.coverUrl.ifBlank { mappedTask?.comicCover.orEmpty() }
                route = DesktopRoute.Reader(
                    comicId = rawComicId,
                    title = effectiveTitle,
                    cover = effectiveCover,
                    sourceKey = ch.sourceKey,
                    episode = EngineSession.Episode(name = ch.title, id = ch.chapterId),
                    startPage = 0,
                    from = DesktopRoute.Files,
                )
            },
            onOpenComicDetail = { sourceKey, comicId, title, cover ->
                petWork(title)
                val rawIndex = DesktopComicShelfRawIdIndex.of(queueTasks)
                val rawComicId = rawIndex.rawIdOf(sourceKey, comicId) ?: restoreSafeComicId(comicId)
                route = DesktopRoute.Detail(
                    from = route,
                    comicId = rawComicId,
                    title = title,
                    cover = cover ?: "",
                    sourceKey = sourceKey,
                )
            },
            localComicScan = localComicScan,
            importedComics = filesUi.importedComics,
            localComicNote = filesUi.localComicNote,
            onImportComics = localImportPickAction,
            onImportComicFolder = {
                val picked = DesktopDirChooser.pickDirectory(
                    "选择要导入的漫画文件夹",
                    downloadsRoot?.absolutePath,
                )
                if (picked != null) {
                    filesUi.localFolderImportPick = File(picked)
                    filesUi.localComicNote = null
                }
            },
            onOpenLocalComic = { comic ->
                route = DesktopRoute.LocalComicReader(
                    title = comic.title,
                    rootPath = comic.rootPath,
                    startPage = 0,
                    from = DesktopRoute.Files,
                )
            },
            onOpenLocalComicChapter = { title, chapterDir ->
                route = DesktopRoute.LocalComicReader(
                    title = title,
                    rootPath = chapterDir.absolutePath,
                    startPage = 0,
                    from = DesktopRoute.Files,
                )
            },
            onDeleteLocalComic = { comic ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        DesktopDownloadsDb.deleteImportedComic(comic.rootPath)
                        if (!comic.isLinked) {
                            runCatching { File(comic.rootPath).deleteRecursively() }
                        }
                    }
                    filesUi.localComicNote = if (comic.isLinked) {
                        "已移除文件夹关联「${comic.title}」（硬盘原文件夹未删除）"
                    } else {
                        null
                    }
                    downloadsTick++
                    localComicImportTick++
                }
            },
            images = filesUi.imageItems.orEmpty(),
            imagesScanning = false,
            imagesNote = filesUi.imageNote,
            onOpenImageDir = { img ->
                scope.launch { runCatching { Desktop.getDesktop().open(File(img.path).parentFile) } }
            },
            onOpenImageSaveDir = {
                scope.launch {
                    runCatching {
                        val dir = galleryPaths?.let { pp ->
                            desktopGallerySaveDir(pp, galleryPrefs?.downloadDir?.value)
                        } ?: downloadsRoot
                        if (dir != null) {
                            dir.mkdirs()
                            Desktop.getDesktop().open(dir)
                        }
                    }
                }
            },
            onOpenImage = { img ->
                scope.launch { runCatching { Desktop.getDesktop().open(File(img.path)) } }
            },
            onOpenImageDetail = { img ->
                if (img.fileName.startsWith("pixiv_")) {
                    val stem = img.fileName.substringBeforeLast('.')
                    val id = if ("_p" in stem) stem.substringBefore("_p").removePrefix("pixiv_") else stem.removePrefix("pixiv_")
                    val illustId = id.toLongOrNull()
                    if (illustId != null) {
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
                            val bytes = File(img.path).readBytes()
                            DesktopPixivImageBytes.copyToClipboard(bytes)
                            ux.toast("已复制图片到系统剪贴板")
                        }.onFailure { ux.toast("复制失败：${it.message}") }
                    }
                }
            },
            onDeleteImages = { list ->
                scope.launch {
                    val pathsToDelete = list.flatMap { it.allPaths }
                    val (ok, bad) = withContext(Dispatchers.IO) {
                        val res = deleteDesktopLocalImages(pathsToDelete)
                        DesktopDownloadsDb.deleteImagesByPaths(pathsToDelete)
                        res
                    }
                    filesUi.imageItems = withContext(Dispatchers.IO) { DesktopDownloadsDb.listImageItems() }
                    filesUi.imageNote = desktopImageDeleteReceipt(ok, bad)
                }
            },
            onOpenSettingsDownloads = openSettings,
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
                filesUi.queueNote = if (root == null) {
                    "下载根目录拿不到 ⇒ 这一笔撤不掉"
                } else {
                    desktopComicQueueRemovedLine(task, DesktopComicDownloadQueue.drop(root, task.taskId))
                }
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
                    runCatching { Desktop.getDesktop().open(desktopComicQueueDir(root, t)) }
                }
            },
        )

        DesktopRoute.Downloads -> DesktopComicDownloadsScreen(
            mode = layoutMode,
            listing = filesUi.downloadsListing,
            note = filesUi.downloadsNote,
            armedKey = armedDeleteKey,
            onRepair = downloadsRepairAction,
            onDelete = { ch -> onDownloadsDelete(ch, false) },
            onOpenChapterDir = openDownloadedChapterDir,
            onBack = {
                armedDeleteKey = null
                route = DesktopRoute.Tab
            },
        )

        DesktopRoute.LocalComics -> DesktopLocalComicLibraryScreen(
            mode = layoutMode,
            scan = localComicScan,
            note = filesUi.localComicNote,
            onBack = { route = DesktopRoute.Tab },
            onImportPick = localImportPickAction,
            busy = filesUi.localComicImportBusy,
            onOpenImported = { comic ->
                route = DesktopRoute.LocalComicReader(
                    title = comic.title,
                    rootPath = comic.rootPath,
                    startPage = 0,
                )
            },
        )

        else -> Unit
    }
}

/**
 * 导入本地漫画直读播放器路由（[DesktopRoute.LocalComicReader]）实现。
 */
@Composable
internal fun DesktopLocalComicReaderRoute(
    r: DesktopRoute.LocalComicReader,
    downloadsRoot: File?,
    writeHistory: (suspend (HistoryRecord) -> Unit)?,
    detailUi: DetailAndReaderUiState,
    routeState: MutableState<DesktopRoute>,
) {
    var route by routeState
    val (effectiveDir, pageFiles) = remember(r.rootPath) {
        val base = File(r.rootPath)
        if (!base.isDirectory) return@remember null to emptyList()
        val imageExts = setOf("jpg", "jpeg", "png", "webp", "gif", "avif")
        val rootImages = base.listFiles { f ->
            f.isFile && f.length() > 0 && f.extension.lowercase(Locale.ROOT) in imageExts
        }?.sortedBy { it.name }.orEmpty()
        if (rootImages.isNotEmpty()) return@remember base to rootImages
        val subDirs = base.listFiles { f -> f.isDirectory }?.sortedBy { it.name }.orEmpty()
        for (sub in subDirs) {
            val subImages = sub.listFiles { f ->
                f.isFile && f.length() > 0 && f.extension.lowercase(Locale.ROOT) in imageExts
            }?.sortedBy { it.name }.orEmpty()
            if (subImages.isNotEmpty()) return@remember sub to subImages
        }
        null to emptyList()
    }
    val localPages = pageFiles.size
    LaunchedEffect(r.rootPath, detailUi.readerPage, localPages) {
        val write = writeHistory ?: return@LaunchedEffect
        if (localPages == 0) return@LaunchedEffect
        val coverFile = effectiveDir?.let { dir ->
            listOf("cover.jpg", "cover.png", "cover.webp")
                .map { File(dir, it) }
                .firstOrNull { it.isFile }?.absolutePath
        } ?: pageFiles.firstOrNull()?.absolutePath.orEmpty()
        val progress = DesktopReadingProgress(
            comicId = r.title,
            title = r.title,
            sourceKey = "imported",
            chapterTitle = effectiveDir?.name ?: "全一话",
            chapterIndex = 0,
            pageIndex = detailUi.readerPage,
            totalPages = localPages,
            coverUrl = coverFile,
        )
        desktopWriteReadingHistory(
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
        note = if (downloadsRoot == null) "下载目录不可用" else if (localPages == 0) "未找到漫画页面图片" else null,
        masked = false,
        currentPage = detailUi.readerPage,
        startPage = r.startPage,
        onPageChange = { DesktopPetCueBus.emit(DesktopPetCue.TURN_PAGE); detailUi.readerPage = it },
        onBack = { route = r.from ?: DesktopRoute.Files },
    )
}
