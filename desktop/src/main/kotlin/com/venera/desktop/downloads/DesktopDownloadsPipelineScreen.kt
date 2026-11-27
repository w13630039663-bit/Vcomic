package com.venera.desktop.downloads

import com.venera.desktop.ui.theme.VeneraType
import com.venera.desktop.gallery.ui.GalleryTokens
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import com.venera.desktop.gallery.data.DesktopDownloadSpeedTracker
import com.venera.desktop.gallery.data.desktopFormatSpeed
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.gallery.data.DESKTOP_LOCAL_COMIC_IMPORTED_SOURCE
import com.venera.desktop.gallery.data.DesktopComicDownloadedChapter
import com.venera.desktop.gallery.data.DesktopComicQueueStatus
import com.venera.desktop.gallery.data.DesktopComicQueueTask
import com.venera.desktop.gallery.data.DesktopLocalComic
import com.venera.desktop.gallery.data.DesktopLocalComicLibraryScan
import com.venera.desktop.gallery.data.desktopComicSafePathKey
import com.venera.desktop.gallery.data.restoreSafeComicId
import com.venera.desktop.gallery.ui.DesktopComicDownloadListing
import com.venera.desktop.gallery.ui.desktopByteLabel
import com.venera.desktop.ui.DesktopPageShell
import com.venera.desktop.ui.DesktopPageWidth
import com.venera.desktop.ui.VeneraCheckbox
import com.venera.desktop.ui.VeneraModalDialog
import com.venera.desktop.ui.VeneraProgress
import java.io.File
import java.util.Locale



private const val RECENT_DONE_DISPLAY_MS = 4_000L

/** 本机内容展示档位：漫画 / 图片 */
internal enum class PipelineMediaTab(val label: String) {
    COMIC("漫画"),
    IMAGE("图片"),
}

/** 本机已下载漫画按作品聚合的模型（多目录/话合并展示） */
internal data class DownloadedComicGroup(
    val sourceKey: String,
    val comicId: String,
    val title: String,
    val coverFile: File?,
    val coverUrl: String?,
    val chapters: List<DesktopComicDownloadedChapter>,
) {
    val totalBytes: Long get() = chapters.sumOf { it.bytes }
    val totalPages: Long get() = chapters.sumOf { it.writtenPages.toLong() }
    val isComplete: Boolean get() = chapters.all { it.complete }
    val incompleteCount: Int get() = chapters.count { !it.complete }
}

/** 包装在网格展示中的漫画卡片模型 */
internal sealed class PipelineComicCard {
    abstract val key: String
    abstract val title: String
    abstract val subtitle: String
    abstract val bytes: Long
    abstract val coverModel: Any?
    abstract val pageCount: Int
    abstract val isComplete: Boolean

    /** 已下载漫画（作品聚合卡片：多话合并为一个卡位） */
    data class DownloadedComicBookCard(
        val group: DownloadedComicGroup,
    ) : PipelineComicCard() {
        override val key: String get() = "group_${group.sourceKey}_${group.comicId}"
        override val title: String get() = group.title
        override val subtitle: String get() = "${group.chapters.size} 话 · ${group.totalPages} 页 · ${desktopByteLabel(group.totalBytes)}"
        override val bytes: Long get() = group.totalBytes
        override val coverModel: Any? get() = group.coverFile ?: group.coverUrl
        override val pageCount: Int get() = group.totalPages.toInt()
        override val isComplete: Boolean get() = group.isComplete
    }

    data class LocalImportedComicCard(
        val comic: DesktopLocalComic,
        override val coverModel: Any?,
    ) : PipelineComicCard() {
        override val key: String get() = "local_${comic.id}_${comic.rootPath.hashCode()}"
        override val title: String get() = comic.title
        override val subtitle: String
            get() = if (comic.isLinked) {
                "本地文件夹 · ${comic.chapterCount} 话 · ${comic.totalPages} 页"
            } else {
                "本地导入 · ${comic.chapterCount} 话 · ${comic.totalPages} 页"
            }
        override val bytes: Long get() = 0L
        override val pageCount: Int get() = comic.totalPages
        override val isComplete: Boolean get() = true
    }
}

/** 从指定作品目录下探测 comic.json 中的标题。 */
private fun readTitleFromComicDir(comicDir: File): String? {
    val meta = File(comicDir, "comic.json")
    if (!meta.isFile) return null
    val txt = runCatching { meta.readText() }.getOrNull() ?: return null
    val t = Regex("\"title\"\\s*:\\s*\"([^\"]+)\"").find(txt)?.groupValues?.get(1)
    return t?.takeIf { it.isNotBlank() && !it.startsWith("http") }
}

/** 解析已下载作品的真实名称：优先台账记录，再双向命中原号/净化目录与真实磁盘 comic.json。 */
internal fun resolveChapterComicTitle(
    root: File?,
    sourceKey: String,
    comicId: String,
    titleMap: Map<String, String>,
    fallbackChapterTitle: String? = null,
    dbTitle: String? = null,
    comicRootDir: File? = null,
): String {
    if (!dbTitle.isNullOrBlank() && !dbTitle.startsWith("http___") && !dbTitle.startsWith("https___") && !dbTitle.startsWith("http://") && !dbTitle.startsWith("https://")) {
        return dbTitle
    }
    val fromMap = titleMap[comicId]
        ?: titleMap[restoreSafeComicId(comicId)]
        ?: titleMap[desktopComicSafePathKey(comicId)]
    if (!fromMap.isNullOrBlank() && !fromMap.startsWith("http___") && !fromMap.startsWith("https___") && !fromMap.startsWith("http://") && !fromMap.startsWith("https://")) {
        return fromMap
    }
    if (comicRootDir != null) {
        readTitleFromComicDir(comicRootDir)?.let { return it }
    }
    if (root != null) {
        val comicDir = File(File(root, sourceKey), comicId)
        readTitleFromComicDir(comicDir)?.let { return it }
    }
    if (!fallbackChapterTitle.isNullOrBlank() && !fallbackChapterTitle.startsWith("http")) {
        return fallbackChapterTitle
    }
    return "漫画作品"
}

private fun resolveCoverFromComicDir(comicDir: File): Pair<File?, String?>? {
    if (!comicDir.isDirectory) return null
    val directCover = File(comicDir, "cover.jpg").takeIf { it.isFile && it.length() > 0 }
        ?: File(comicDir, "cover.png").takeIf { it.isFile && it.length() > 0 }
        ?: File(comicDir, "cover.webp").takeIf { it.isFile && it.length() > 0 }
    if (directCover != null) return directCover to null

    val meta = File(comicDir, "comic.json")
    if (meta.isFile) {
        val txt = runCatching { meta.readText() }.getOrNull()
        if (txt != null) {
            val c = Regex("\"cover\"\\s*:\\s*\"([^\"]+)\"").find(txt)?.groupValues?.get(1)
            if (!c.isNullOrBlank()) return null to c
        }
    }
    return null
}

/** 优先获取漫画本身真实封面（优先真实作品目录本地 cover / 台账 URL / 队列 URL，最后兜底首张本地页）。 */
internal fun resolveComicCover(
    root: File?,
    sourceKey: String,
    comicId: String,
    coverMap: Map<String, String>,
    dbCoverUrl: String? = null,
    comicRootDir: File? = null,
): Pair<File?, String?> {
    if (comicRootDir != null) {
        resolveCoverFromComicDir(comicRootDir)?.let { return it }
    }
    if (!dbCoverUrl.isNullOrBlank()) {
        return null to dbCoverUrl
    }
    if (root != null) {
        val comicDir = File(File(root, sourceKey), comicId)
        resolveCoverFromComicDir(comicDir)?.let { return it }
    }
    val url = coverMap[comicId]
        ?: coverMap[restoreSafeComicId(comicId)]
        ?: coverMap[desktopComicSafePathKey(comicId)]
    if (!url.isNullOrBlank()) {
        return null to url
    }
    val fallbackDir = comicRootDir ?: root?.let { File(File(it, sourceKey), comicId) }
    if (fallbackDir != null && fallbackDir.isDirectory) {
        val firstPage = runCatching {
            val imageExts = setOf("jpg", "jpeg", "png", "webp", "gif", "avif")
            fallbackDir.listFiles { f -> f.isDirectory }?.sortedBy { it.name }
                ?.firstNotNullOfOrNull { chDir ->
                    chDir.listFiles { f ->
                        f.isFile && f.length() > 0 && f.extension.lowercase(Locale.ROOT) in imageExts
                    }?.minByOrNull { it.name }
                }
        }.getOrNull()
        if (firstPage != null) return firstPage to null
    }
    return null to null
}

/** 探测本地导入漫画目录里的第一张图片文件作为真实封面 */
internal fun resolveLocalComicCoverFile(comic: DesktopLocalComic): File? {
    return runCatching {
        val dir = File(comic.rootPath)
        if (!dir.exists()) return@runCatching null
        val directImages = dir.listFiles { f ->
            f.isFile && f.length() > 0 && f.extension.lowercase(Locale.ROOT) in setOf("jpg", "jpeg", "png", "webp", "gif", "avif")
        }?.sortedBy { it.name }
        if (!directImages.isNullOrEmpty()) return@runCatching directImages.first()
        val subDirs = dir.listFiles { f -> f.isDirectory }?.sortedBy { it.name }
        if (!subDirs.isNullOrEmpty()) {
            for (sub in subDirs) {
                val subImages = sub.listFiles { f ->
                    f.isFile && f.length() > 0 && f.extension.lowercase(Locale.ROOT) in setOf("jpg", "jpeg", "png", "webp", "gif", "avif")
                }?.sortedBy { it.name }
                if (!subImages.isNullOrEmpty()) return@runCatching subImages.first()
            }
        }
        null
    }.getOrNull()
}

/**
 * 「下载与本地」一体化页面：
 *  - 兼具漫画与图片两大核心媒体域；
 *  - 上半部：进行中流水线；
 *  - 下半部：本机内容（清爽流式画廊网格，不按文件夹分类，给真封面/图片预览）；
 *  - 切换采用精致连体胶囊（漫画 · X 本 | 图片 · Y 张），舒展不挤压；
 *  - 右侧提供「导入 CBZ/ZIP」或「打开保存目录」；
 *  - 阅读器返回准确直退回本屏。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DesktopDownloadsPipelineScreen(
    mode: WideScreenLayoutMode,
    downloadsRoot: File? = null,
    /** 进行中的漫画队列任务 */
    queueTasks: List<DesktopComicQueueTask>,
    queuePrimed: Boolean,
    queueNote: String?,
    onQueuePauseResume: ((DesktopComicQueueTask) -> Unit)? = null,
    onQueueRemove: ((DesktopComicQueueTask) -> Unit)? = null,
    /** 已下载的话级漫画树 */
    downloadedListing: DesktopComicDownloadListing?,
    downloadedNote: String?,
    onRepairDownloadedChapter: (DesktopComicDownloadedChapter) -> Unit,
    onDeleteDownloadedChapter: (DesktopComicDownloadedChapter) -> Unit,
    onOpenDownloadedChapterDir: (DesktopComicDownloadedChapter) -> Unit,
    onOpenDownloadedChapter: ((DesktopComicDownloadedChapter) -> Unit)? = null,
    /** 打开漫画源站详情页 */
    onOpenComicDetail: ((sourceKey: String, comicId: String, title: String, coverUrl: String?) -> Unit)? = null,
    /** 本地导入/本级漫画树 */
    localComicScan: DesktopLocalComicLibraryScan?,
    importedComics: List<DesktopLocalComic> = emptyList(),
    localComicNote: String?,
    onImportComics: (() -> Unit)? = null,
    onImportComicFolder: (() -> Unit)? = null,
    onOpenLocalComic: ((DesktopLocalComic) -> Unit)? = null,
    onOpenLocalComicChapter: ((title: String, chapterDir: File) -> Unit)? = null,
    onDeleteLocalComic: ((DesktopLocalComic) -> Unit)? = null,
    /** 本地图片库列表 */
    images: List<DesktopLocalImageItem> = emptyList(),
    imagesScanning: Boolean = false,
    imagesNote: String? = null,
    onOpenImageDir: ((DesktopLocalImageItem) -> Unit)? = null,
    onOpenImageSaveDir: (() -> Unit)? = null,
    onOpenImage: ((DesktopLocalImageItem) -> Unit)? = null,
    onOpenImageDetail: ((DesktopLocalImageItem) -> Unit)? = null,
    onCopyImageToClipboard: ((DesktopLocalImageItem) -> Unit)? = null,
    onDeleteImages: ((List<DesktopLocalImageItem>) -> Unit)? = null,
    /** 导航直跳设置中枢的下载卡 */
    onOpenSettingsDownloads: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens

    // ── 状态管理 ──
    var activeTab by remember { mutableStateOf(PipelineMediaTab.COMIC) }
    var isSelecting by remember { mutableStateOf(false) }
    val selectedKeys = remember { mutableStateListOf<String>() }
    var showDeleteModal by remember { mutableStateOf(false) }
    var expandedGroup by remember { mutableStateOf<DownloadedComicGroup?>(null) }
    var expandedLocalComic by remember { mutableStateOf<DesktopLocalComic?>(null) }

    // ── 队列任务按态切分 + 成功态短时保留 ──
    val seenNonDoneIds = remember { mutableMapOf<String, Boolean>() }
    val recentDoneRemainMs = remember { mutableStateMapOf<String, Long>() }
    val dismissedDoneTimes = remember { mutableMapOf<String, Long>() }

    LaunchedEffect(queueTasks) {
        val now = System.currentTimeMillis()
        val currentIds = queueTasks.mapTo(HashSet()) { it.taskId }
        seenNonDoneIds.keys.retainAll(currentIds)
        recentDoneRemainMs.keys.retainAll(currentIds)

        queueTasks.forEach { task ->
            if (task.status != DesktopComicQueueStatus.DONE) {
                seenNonDoneIds[task.taskId] = true
                recentDoneRemainMs.remove(task.taskId)
                dismissedDoneTimes.remove(task.taskId)
            } else {
                val wasActiveHere = seenNonDoneIds.remove(task.taskId) == true
                val ageMs = now - task.updateTime
                val justFinished = ageMs in 0L..RECENT_DONE_DISPLAY_MS &&
                    dismissedDoneTimes[task.taskId] != task.updateTime
                if ((wasActiveHere || justFinished) && task.taskId !in recentDoneRemainMs) {
                    val remain = if (wasActiveHere) {
                        RECENT_DONE_DISPLAY_MS
                    } else {
                        (RECENT_DONE_DISPLAY_MS - ageMs).coerceAtLeast(1_000L)
                    }
                    recentDoneRemainMs[task.taskId] = remain
                }
            }
        }
    }

    recentDoneRemainMs.keys.toList().forEach { doneTaskId ->
        androidx.compose.runtime.key(doneTaskId) {
            LaunchedEffect(doneTaskId) {
                val remain = recentDoneRemainMs[doneTaskId] ?: return@LaunchedEffect
                delay(remain)
                recentDoneRemainMs.remove(doneTaskId)
                queueTasks.firstOrNull { it.taskId == doneTaskId }?.let {
                    dismissedDoneTimes[doneTaskId] = it.updateTime
                }
            }
        }
    }

    val runningTasks = queueTasks.filter { it.status == DesktopComicQueueStatus.RUNNING }
    val pendingTasks = queueTasks.filter { it.status == DesktopComicQueueStatus.PENDING }
    val failedTasks = queueTasks.filter { it.status == DesktopComicQueueStatus.FAILED }
    val stoppedTasks = queueTasks.filter { it.status == DesktopComicQueueStatus.STOPPED }
    val visibleQueueTasks = queueTasks.filter {
        it.status != DesktopComicQueueStatus.DONE || it.taskId in recentDoneRemainMs
    }

    var liveSpeedBps by remember { mutableStateOf(0L) }
    LaunchedEffect(runningTasks.isNotEmpty()) {
        if (runningTasks.isEmpty()) {
            liveSpeedBps = 0L
            return@LaunchedEffect
        }
        while (true) {
            liveSpeedBps = DesktopDownloadSpeedTracker.currentBytesPerSec()
            delay(500L)
        }
    }
    val liveSpeedLabel = remember(liveSpeedBps) { desktopFormatSpeed(liveSpeedBps) }

    val chapters = downloadedListing?.groups?.flatMap { it.chapters }.orEmpty()
    val localComics = localComicScan?.comics.orEmpty()

    val comicCards = remember(chapters, localComics, importedComics, downloadsRoot, queueTasks) {
        val titleMap = HashMap<String, String>()
        val coverMap = HashMap<String, String>()
        queueTasks.forEach { t ->
            if (t.comicTitle.isNotBlank()) {
                titleMap[t.comicId] = t.comicTitle
                titleMap[desktopComicSafePathKey(t.comicId)] = t.comicTitle
                titleMap[restoreSafeComicId(t.comicId)] = t.comicTitle
            }
            if (t.comicCover.isNotBlank()) {
                coverMap[t.comicId] = t.comicCover
                coverMap[desktopComicSafePathKey(t.comicId)] = t.comicCover
                coverMap[restoreSafeComicId(t.comicId)] = t.comicCover
            }
        }
        val list = mutableListOf<PipelineComicCard>()
        val downloadedKeys = chapters.flatMap { ch ->
            listOf(
                ch.sourceKey to ch.comicId,
                ch.sourceKey to desktopComicSafePathKey(ch.comicId),
                ch.sourceKey to restoreSafeComicId(ch.comicId),
            )
        }.toSet()
        val activeOnlineKeys = queueTasks
            .filter { it.status != DesktopComicQueueStatus.DONE }
            .flatMap { t ->
                listOf(
                    t.sourceKey to t.comicId,
                    t.sourceKey to desktopComicSafePathKey(t.comicId),
                    t.sourceKey to restoreSafeComicId(t.comicId),
                )
            }
            .toSet()

        // 核心：按作品（sourceKey to comicId）聚合！同一部漫画的所有话合并为一个卡位
        val groups = chapters.groupBy { it.sourceKey to it.comicId }
        for ((key, chs) in groups) {
            val (sourceKey, comicId) = key
            val sortedChapters = chs.sortedBy { it.order }
            val firstChapter = sortedChapters.firstOrNull()
            val dbTitle = sortedChapters.firstNotNullOfOrNull { it.comicTitle.takeIf(String::isNotBlank) }
            val dbCoverUrl = sortedChapters.firstNotNullOfOrNull { it.coverUrl.takeIf(String::isNotBlank) }
            val comicRootDir = sortedChapters.firstNotNullOfOrNull { ch ->
                ch.localDir.takeIf(String::isNotBlank)?.let { File(it).parentFile }
            }
            val bookTitle = resolveChapterComicTitle(
                root = downloadsRoot,
                sourceKey = sourceKey,
                comicId = comicId,
                titleMap = titleMap,
                fallbackChapterTitle = firstChapter?.title,
                dbTitle = dbTitle,
                comicRootDir = comicRootDir,
            )
            val (coverFile, coverUrl) = resolveComicCover(
                root = downloadsRoot,
                sourceKey = sourceKey,
                comicId = comicId,
                coverMap = coverMap,
                dbCoverUrl = dbCoverUrl,
                comicRootDir = comicRootDir,
            )
            val group = DownloadedComicGroup(
                sourceKey = sourceKey,
                comicId = comicId,
                title = bookTitle,
                coverFile = coverFile,
                coverUrl = coverUrl,
                chapters = sortedChapters,
            )
            list += PipelineComicCard.DownloadedComicBookCard(group)
        }

        // 仅展示真正导入/关联的本地漫画（台账记录 + imported/ 目录），不把下载中的在线源目录误认为导入漫画。
        val mergedImports = buildList {
            val seenPaths = HashSet<String>()
            importedComics.forEach { lc ->
                if (seenPaths.add(lc.rootPath)) add(lc)
            }
            localComics.forEach { lc ->
                if (lc.sourceKey == DESKTOP_LOCAL_COMIC_IMPORTED_SOURCE && seenPaths.add(lc.rootPath)) {
                    add(lc)
                }
            }
        }
        mergedImports.forEach { lc ->
            val pairKey = lc.sourceKey to lc.id
            if (pairKey !in downloadedKeys && pairKey !in activeOnlineKeys) {
                val cover = resolveLocalComicCoverFile(lc)
                val mappedTitle = titleMap[lc.id]
                list += PipelineComicCard.LocalImportedComicCard(
                    comic = if (!mappedTitle.isNullOrBlank()) lc.copy(title = mappedTitle) else lc,
                    coverModel = cover,
                )
            }
        }
        list
    }

    val totalBytes = chapters.sumOf { it.bytes } + images.sumOf { it.bytes }
    val totalBytesLabel = desktopByteLabel(totalBytes)

    val throughputPages = if (runningTasks.isNotEmpty()) {
        (runningTasks.sumOf { it.downloadedPages.coerceAtLeast(1) } * 6).coerceAtLeast(6)
    } else 0

    DesktopPageShell(
        mode = mode,
        title = "下载与本地",
        subtitle = "管理已下载与本地导入的漫画与图片",
        onBack = onBack,
        modifier = modifier,
        width = DesktopPageWidth.Browse,
        titleCard = false,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // ── ① 顶栏操作区 ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Spacer(modifier = Modifier.weight(1f))
                if (isSelecting) {
                    val unitName = if (activeTab == PipelineMediaTab.COMIC) "本" else "张"
                    Text(
                        text = "已选 ${selectedKeys.size} $unitName",
                        fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                        fontWeight = VeneraType.cardTitle.weight,
                        color = tokens.color.primary,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                    DqMiniButton(
                        text = "取消",
                        onClick = {
                            isSelecting = false
                            selectedKeys.clear()
                        },
                    )
                    if (selectedKeys.isNotEmpty()) {
                        DqMiniButton(
                            text = "删除所选 (${selectedKeys.size})",
                            primary = true,
                            onClick = { showDeleteModal = true },
                        )
                    }
                } else {
                    DqMiniButton(
                        text = "选择",
                        onClick = { isSelecting = true },
                    )
                    if (failedTasks.isNotEmpty()) {
                        DqMiniButton(
                            text = "重试失败 (${failedTasks.size})",
                            onClick = {
                                failedTasks.forEach { onQueuePauseResume?.invoke(it) }
                            },
                        )
                    }
                    if (runningTasks.isNotEmpty() || pendingTasks.isNotEmpty()) {
                        DqMiniButton(
                            text = "全部暂停",
                            onClick = {
                                (runningTasks + pendingTasks).forEach { onQueuePauseResume?.invoke(it) }
                            },
                        )
                    } else if (stoppedTasks.isNotEmpty()) {
                        DqMiniButton(
                            text = "全部继续",
                            onClick = {
                                stoppedTasks.forEach { onQueuePauseResume?.invoke(it) }
                            },
                        )
                    }
                    DqMiniButton(
                        text = "下载设置",
                        onClick = onOpenSettingsDownloads,
                    )
                }
            }

            // ── ② 脉动统计带 ──
            PulseDashboard(
                runningCount = runningTasks.size,
                pendingCount = pendingTasks.size,
                speedLabel = liveSpeedLabel,
                failedCount = failedTasks.size,
                pausedCount = stoppedTasks.size,
                totalComicCount = comicCards.size,
                totalImageCount = images.size,
                totalBytesLabel = totalBytesLabel,
            )

            // ── ③ 上半段：下载队列 ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "下载队列",
                    fontSize = VeneraType.bodySecondary.size, lineHeight = VeneraType.bodySecondary.lineHeight,
                    fontWeight = VeneraType.cardTitle.weight,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                DqBadge(
                    label = visibleQueueTasks.size.toString(),
                    tone = if (failedTasks.isNotEmpty() && runningTasks.isEmpty() && pendingTasks.isEmpty()) DqTone.fail else tokens.color.primary,
                )
                Text(
                    text = "下载完成后将自动归入下方已下载内容",
                    fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                    color = tokens.color.textTertiary,
                )
            }

            if (visibleQueueTasks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(tokens.color.surfaceVariant.copy(alpha = 0.25f))
                        .border(1.dp, tokens.color.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(vertical = 22.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (!queuePrimed) "正在加载下载任务…" else "当前没有进行中的下载任务",
                            fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                            color = tokens.color.textSecondary,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "可在作品详情或阅读器中添加下载任务",
                            fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                            color = tokens.color.textTertiary,
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(tokens.color.surfaceVariant.copy(alpha = 0.2f))
                        .border(1.dp, tokens.color.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                ) {
                    visibleQueueTasks.forEach { task ->
                        PipelineTaskRow(
                            task = task,
                            liveSpeedLabel = liveSpeedLabel,
                            isSelecting = isSelecting,
                            isSelected = "queue_${task.taskId}" in selectedKeys,
                            onToggleSelect = {
                                val key = "queue_${task.taskId}"
                                if (key in selectedKeys) selectedKeys.remove(key) else selectedKeys.add(key)
                            },
                            onPauseResume = { onQueuePauseResume?.invoke(task) },
                            onRemove = { onQueueRemove?.invoke(task) },
                        )
                    }
                }
            }

            // ── ④ 下半段：本机内容（漫画 / 图片 双域清爽流式画廊，不搞文件夹分类）──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 28.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "本机内容",
                    fontSize = VeneraType.body.size, lineHeight = VeneraType.body.lineHeight,
                    fontWeight = VeneraType.cardTitle.weight,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                // 稳定并排、绝不吞 Tab 的连体分段条
                PipelineMediaTabBar(
                    selected = activeTab,
                    comicCount = comicCards.size,
                    imageCount = images.size,
                    onSelect = {
                        activeTab = it
                        selectedKeys.clear()
                    },
                )

                Spacer(modifier = Modifier.weight(1f))

                // 右侧动作按钮随当前域切换（打开图片目录即使列表为空也能呼出）
                when (activeTab) {
                    PipelineMediaTab.COMIC -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (onImportComicFolder != null) {
                                DqMiniButton(
                                    text = "导入文件夹",
                                    primary = false,
                                    onClick = onImportComicFolder,
                                )
                            }
                            DqMiniButton(
                                text = "导入 CBZ / ZIP",
                                primary = true,
                                onClick = { onImportComics?.invoke() },
                            )
                        }
                    }
                    PipelineMediaTab.IMAGE -> {
                        DqMiniButton(
                            text = "打开图片目录",
                            onClick = {
                                if (onOpenImageSaveDir != null) {
                                    onOpenImageSaveDir.invoke()
                                } else {
                                    images.firstOrNull()?.let { onOpenImageDir?.invoke(it) }
                                }
                            },
                        )
                    }
                }
            }

            localComicNote?.let {
                Text(
                    text = it,
                    fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                    color = tokens.color.textTertiary,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            imagesNote?.let {
                Text(
                    text = it,
                    fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                    color = tokens.color.textTertiary,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }

            // 内容网格
            when (activeTab) {
                PipelineMediaTab.COMIC -> {
                    val comicListErr = downloadedListing?.error ?: downloadedNote
                    if (comicCards.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = when {
                                    comicListErr != null -> "读取已下载列表失败：$comicListErr"
                                    downloadedListing == null -> "正在加载漫画列表…"
                                    else -> "暂无已下载或导入的漫画"
                                },
                                fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                                color = tokens.color.textTertiary,
                            )
                        }
                    } else {
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            comicCards.forEach { card ->
                                ComicBookGridCard(
                                    card = card,
                                    isSelecting = isSelecting,
                                    isSelected = card.key in selectedKeys,
                                    onToggleSelect = {
                                        if (card.key in selectedKeys) selectedKeys.remove(card.key) else selectedKeys.add(card.key)
                                    },
                                    onClick = {
                                        if (isSelecting) {
                                            if (card.key in selectedKeys) selectedKeys.remove(card.key) else selectedKeys.add(card.key)
                                        } else {
                                            when (card) {
                                                is PipelineComicCard.DownloadedComicBookCard -> {
                                                    if (card.group.chapters.size == 1) {
                                                        onOpenDownloadedChapter?.invoke(card.group.chapters.first())
                                                    } else {
                                                        expandedGroup = card.group
                                                    }
                                                }
                                                is PipelineComicCard.LocalImportedComicCard -> {
                                                    if (card.comic.chapterCount > 1 && onOpenLocalComicChapter != null) {
                                                        expandedLocalComic = card.comic
                                                    } else {
                                                        onOpenLocalComic?.invoke(card.comic)
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    onRepair = {
                                        if (card is PipelineComicCard.DownloadedComicBookCard) {
                                            card.group.chapters.firstOrNull { !it.complete }?.let { onRepairDownloadedChapter(it) }
                                        }
                                    },
                                    onOpenDetail = if (card is PipelineComicCard.DownloadedComicBookCard) {
                                        {
                                            onOpenComicDetail?.invoke(card.group.sourceKey, card.group.comicId, card.group.title, card.group.coverUrl)
                                        }
                                    } else {
                                        null
                                    },
                                    onOpenFolder = {
                                        when (card) {
                                            is PipelineComicCard.DownloadedComicBookCard ->
                                                card.group.chapters.firstOrNull()?.let { onOpenDownloadedChapterDir(it) }
                                            is PipelineComicCard.LocalImportedComicCard ->
                                                runCatching { java.awt.Desktop.getDesktop().open(File(card.comic.rootPath)) }
                                        }
                                    },
                                    onDelete = {
                                        if (card is PipelineComicCard.DownloadedComicBookCard) {
                                            card.group.chapters.forEach { ch -> onDeleteDownloadedChapter(ch) }
                                        } else if (card is PipelineComicCard.LocalImportedComicCard) {
                                            if (onDeleteLocalComic != null) onDeleteLocalComic.invoke(card.comic)
                                            else if (!card.comic.isLinked) runCatching { File(card.comic.rootPath).deleteRecursively() }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }

                PipelineMediaTab.IMAGE -> {
                    if (images.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (imagesScanning) "正在加载图片列表…" else "暂无已下载的画廊图片（可在作品详情或画廊点击下载）",
                                fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                                color = tokens.color.textTertiary,
                            )
                        }
                    } else {
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            images.forEach { img ->
                                val imgKey = "img_${img.path}"
                                ImageGridCard(
                                    image = img,
                                    isSelecting = isSelecting,
                                    isSelected = imgKey in selectedKeys,
                                    onToggleSelect = {
                                        if (imgKey in selectedKeys) selectedKeys.remove(imgKey) else selectedKeys.add(imgKey)
                                    },
                                    onClick = {
                                        if (isSelecting) {
                                            if (imgKey in selectedKeys) selectedKeys.remove(imgKey) else selectedKeys.add(imgKey)
                                        } else {
                                            onOpenImage?.invoke(img)
                                        }
                                    },
                                    onOpenDetail = { onOpenImageDetail?.invoke(img) },
                                    onCopy = { onCopyImageToClipboard?.invoke(img) },
                                    onOpenFolder = { onOpenImageDir?.invoke(img) },
                                    onDelete = { onDeleteImages?.invoke(listOf(img)) },
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(36.dp))
        }
    }

    // ── ⑤ 漫画多目录展开对话框 ──
    expandedGroup?.let { group ->
        ComicChaptersDialog(
            group = group,
            onDismiss = { expandedGroup = null },
            onOpenChapter = { ch ->
                expandedGroup = null
                onOpenDownloadedChapter?.invoke(ch)
            },
            onRepairChapter = { ch -> onRepairDownloadedChapter(ch) },
            onOpenChapterDir = { ch -> onOpenDownloadedChapterDir(ch) },
            onDeleteChapter = { ch ->
                onDeleteDownloadedChapter(ch)
                if (group.chapters.size <= 1) expandedGroup = null
            },
            onOpenComicDetail = {
                expandedGroup = null
                onOpenComicDetail?.invoke(group.sourceKey, group.comicId, group.title, group.coverUrl)
            },
            onDeleteGroup = {
                group.chapters.forEach { ch -> onDeleteDownloadedChapter(ch) }
                expandedGroup = null
            },
        )
    }

    expandedLocalComic?.let { localComic ->
        LocalComicChaptersDialog(
            comic = localComic,
            onDismiss = { expandedLocalComic = null },
            onOpenChapter = { ch ->
                expandedLocalComic = null
                onOpenLocalComicChapter?.invoke("${localComic.title} · ${ch.title}", ch.dir)
            },
            onOpenFolder = {
                runCatching { java.awt.Desktop.getDesktop().open(File(localComic.rootPath)) }
            },
        )
    }

    // ── ⑥ 模态批量删除确认框 ──
    if (showDeleteModal) {
        val countTotal = selectedKeys.size
        val targetName = if (activeTab == PipelineMediaTab.COMIC) "漫画话册" else "图片文件"
        val hasLinkedSelected = activeTab == PipelineMediaTab.COMIC && comicCards.any {
            it.key in selectedKeys && it is PipelineComicCard.LocalImportedComicCard && it.comic.isLinked
        }
        val deleteMessage = if (hasLinkedSelected) {
            "将删除所选 $countTotal 项中的本地落盘文件，并移除关联文件夹记录（关联的硬盘原文件夹不会被删除）。"
        } else {
            "将从本地磁盘永久删除选中的 $countTotal 项落盘文件，删除后不可恢复。"
        }

        VeneraModalDialog(
            visible = showDeleteModal,
            onDismiss = { showDeleteModal = false },
            title = "删除所选 $countTotal 项$targetName？",
            message = deleteMessage,
            confirmText = "删除这 $countTotal 项",
            isDanger = true,
            onConfirm = {
                if (activeTab == PipelineMediaTab.COMIC) {
                    comicCards.filter { it.key in selectedKeys }.forEach { card ->
                        when (card) {
                            is PipelineComicCard.DownloadedComicBookCard -> {
                                card.group.chapters.forEach { ch -> onDeleteDownloadedChapter(ch) }
                            }
                            is PipelineComicCard.LocalImportedComicCard -> {
                                if (onDeleteLocalComic != null) {
                                    onDeleteLocalComic.invoke(card.comic)
                                } else if (!card.comic.isLinked) {
                                    runCatching { java.io.File(card.comic.rootPath).deleteRecursively() }
                                }
                            }
                        }
                    }
                } else {
                    val selectedImgs = images.filter { "img_${it.path}" in selectedKeys }
                    if (selectedImgs.isNotEmpty()) {
                        onDeleteImages?.invoke(selectedImgs)
                    }
                }
                selectedKeys.clear()
                isSelecting = false
            },
        )
    }
}

/** 脉动四格统计组件 */
@Composable
private fun PulseDashboard(
    runningCount: Int,
    pendingCount: Int,
    speedLabel: String,
    failedCount: Int,
    pausedCount: Int,
    totalComicCount: Int,
    totalImageCount: Int,
    totalBytesLabel: String,
) {
    val tokens = VeneraTokens
    val dividerColor = tokens.color.outlineVariant.copy(alpha = 0.28f)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulseCell(
            label = "下载任务",
            value = if (runningCount + pendingCount > 0) "${runningCount + pendingCount} 个任务" else "空闲",
            kicker = if (runningCount + pendingCount > 0) "$runningCount 下载中 · $pendingCount 等待中" else "暂无排队任务",
            modifier = Modifier.weight(1f),
        )
        Box(Modifier.width(0.5.dp).height(44.dp).background(dividerColor))
        PulseCell(
            label = "传输速度",
            value = if (runningCount > 0) speedLabel else "0 KB/s",
            kicker = if (runningCount > 0) "实时网络下行速率" else "当前无网络传输",
            modifier = Modifier.weight(1f),
        )
        Box(Modifier.width(0.5.dp).height(44.dp).background(dividerColor))
        PulseCell(
            label = "任务状态",
            value = if (failedCount > 0) "$failedCount 项失败" else if (pausedCount > 0) "$pausedCount 项暂停" else "运行正常",
            kicker = "失败 $failedCount · 暂停 $pausedCount",
            valueColor = when {
                failedCount > 0 -> DqTone.fail
                pausedCount > 0 -> DqTone.warn
                else -> MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f),
        )
        Box(Modifier.width(0.5.dp).height(44.dp).background(dividerColor))
        PulseCell(
            label = "本地存储",
            value = "$totalComicCount 本漫画 · $totalImageCount 张图片",
            kicker = "共 $totalBytesLabel",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PulseCell(
    label: String,
    value: String,
    kicker: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val tokens = VeneraTokens
    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(
            text = label,
            fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
            color = tokens.color.textTertiary,
        )
        Text(
            text = value,
            fontSize = VeneraType.sectionTitle.size, lineHeight = VeneraType.sectionTitle.lineHeight,
            fontWeight = VeneraType.cardTitle.weight,
            color = valueColor,
            modifier = Modifier.padding(vertical = 2.dp),
        )
        Text(
            text = kicker,
            fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
            color = tokens.color.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 队列单行任务：覆盖进行中、等待态、警告态（暂停）、失败态（报错展开）、短时成功态五种展示态。 */
@Composable
private fun PipelineTaskRow(
    task: DesktopComicQueueTask,
    liveSpeedLabel: String,
    isSelecting: Boolean,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onPauseResume: () -> Unit,
    onRemove: () -> Unit,
) {
    val tokens = VeneraTokens
    val frac = when {
        task.status == DesktopComicQueueStatus.DONE -> 1f
        task.totalPages > 0 -> (task.downloadedPages.toFloat() / task.totalPages).coerceIn(0f, 1f)
        else -> 0f
    }
    val percent = (frac * 100).toInt()
    val displayTitle = if (task.chapterTitle.isNotBlank()) {
        "${task.comicTitle} · ${task.chapterTitle}"
    } else {
        task.comicTitle
    }

    val (badgeLabel, badgeTone, badgeFilled) = when (task.status) {
        DesktopComicQueueStatus.RUNNING -> Triple("下载中", tokens.color.primary, false)
        DesktopComicQueueStatus.PENDING -> Triple("等待中", DqTone.wait, false)
        DesktopComicQueueStatus.STOPPED -> Triple("已暂停", DqTone.warn, false)
        DesktopComicQueueStatus.FAILED -> Triple("下载失败", DqTone.fail, false)
        DesktopComicQueueStatus.DONE -> Triple("下载成功", DqTone.ok, true)
    }

    val barColor = when (task.status) {
        DesktopComicQueueStatus.RUNNING -> tokens.color.primary
        DesktopComicQueueStatus.PENDING -> DqTone.wait
        DesktopComicQueueStatus.STOPPED -> DqTone.warn
        DesktopComicQueueStatus.FAILED -> DqTone.fail
        DesktopComicQueueStatus.DONE -> DqTone.ok
    }

    val rowTint = when (task.status) {
        DesktopComicQueueStatus.FAILED -> DqTone.fail.copy(alpha = 0.04f)
        DesktopComicQueueStatus.STOPPED -> DqTone.warn.copy(alpha = 0.03f)
        DesktopComicQueueStatus.DONE -> DqTone.ok.copy(alpha = 0.05f)
        else -> Color.Transparent
    }

    val statusText = when (task.status) {
        DesktopComicQueueStatus.RUNNING -> {
            if (liveSpeedLabel != "0 KB/s") {
                "${task.downloadedPages} / ${task.totalPages} 页 · $percent% · $liveSpeedLabel"
            } else {
                "${task.downloadedPages} / ${task.totalPages} 页 · $percent%"
            }
        }
        DesktopComicQueueStatus.PENDING -> {
            if (task.totalPages > 0) {
                "${task.downloadedPages} / ${task.totalPages} 页 · $percent% · 等待中"
            } else {
                "等待队列空闲后开始"
            }
        }
        DesktopComicQueueStatus.STOPPED -> {
            if (task.totalPages > 0) {
                "已暂停 · 已保存 ${task.downloadedPages} / ${task.totalPages} 页 · $percent%"
            } else {
                "已暂停 · 点击继续下载"
            }
        }
        DesktopComicQueueStatus.FAILED -> {
            if (task.totalPages > 0) {
                "${task.downloadedPages} / ${task.totalPages} 页 · 下载中断"
            } else {
                "下载中断，请检查报错后重试"
            }
        }
        DesktopComicQueueStatus.DONE -> {
            val donePages = task.downloadedPages.coerceAtLeast(task.totalPages)
            if (donePages > 0) "已完成 · 共 $donePages 页" else "已完成 · 已归入本机内容"
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowTint)
            .border(width = 0.5.dp, color = tokens.color.outlineVariant.copy(alpha = 0.2f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (isSelecting) {
            VeneraCheckbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelect() },
            )
        }

        DqThumb(url = task.comicCover)

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = displayTitle,
                    fontSize = VeneraType.bodySecondary.size, lineHeight = VeneraType.bodySecondary.lineHeight,
                    fontWeight = VeneraType.cardTitle.weight,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                DqBadge(
                    label = "漫画",
                    tone = tokens.color.primary,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DqBadge(
                    label = badgeLabel,
                    tone = badgeTone,
                    filled = badgeFilled,
                )
                Text(
                    text = statusText,
                    fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                    color = if (task.status == DesktopComicQueueStatus.FAILED) DqTone.fail else tokens.color.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            VeneraProgress(
                progress = frac,
                color = barColor,
            )
            if (task.status == DesktopComicQueueStatus.FAILED) {
                Spacer(modifier = Modifier.height(8.dp))
                DqInlineErrorBanner(
                    message = task.error.ifBlank { "下载失败，请重试或检查源站与网络设置" },
                )
            }
        }

        if (task.status != DesktopComicQueueStatus.DONE) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val actionIcon = when (task.status) {
                    DesktopComicQueueStatus.RUNNING,
                    DesktopComicQueueStatus.PENDING,
                    -> Icons.Default.Pause
                    DesktopComicQueueStatus.FAILED -> Icons.Default.Refresh
                    DesktopComicQueueStatus.STOPPED,
                    DesktopComicQueueStatus.DONE,
                    -> Icons.Default.PlayArrow
                }
                val actionDesc = when (task.status) {
                    DesktopComicQueueStatus.RUNNING,
                    DesktopComicQueueStatus.PENDING,
                    -> "暂停"
                    DesktopComicQueueStatus.FAILED -> "重试"
                    DesktopComicQueueStatus.STOPPED,
                    DesktopComicQueueStatus.DONE,
                    -> "继续"
                }
                DqIconButton(
                    icon = actionIcon,
                    description = actionDesc,
                    onClick = onPauseResume,
                )
                DqIconButton(
                    icon = Icons.Default.Close,
                    description = "移除",
                    onClick = onRemove,
                    danger = true,
                )
            }
        }
    }
}


/**
 * 专为「下载与本地」打造的稳固横向双 Tab 连体分段条：
 * - 绝不依赖无头组件内部折叠测量，确保「漫画」与「图片」双 Tab 永远 100% 并排完整展现；
 * - 激活项：深色微透 `#5A4FD0` 实底搭配纯白粗体；
 * - 未激活项：柔和次级文字，带圆角与点击波纹，绝不挤压。
 */
@Composable
internal fun PipelineMediaTabBar(
    selected: PipelineMediaTab,
    comicCount: Int,
    imageCount: Int,
    onSelect: (PipelineMediaTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    val tabActiveBg = tokens.color.primary // 主档归全局主色（阶段 2.2，撤强调色后主钮一族不再独紫）
    val outlineColor = tokens.color.outlineVariant.copy(alpha = 0.35f)

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .border(1.dp, outlineColor, RoundedCornerShape(9.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 漫画 Tab
        val comicSelected = selected == PipelineMediaTab.COMIC
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(7.dp))
                .then(
                    if (comicSelected) {
                        Modifier.background(tabActiveBg)
                    } else {
                        Modifier.clickable { onSelect(PipelineMediaTab.COMIC) }
                    }
                )
                .padding(horizontal = 13.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "漫画 ($comicCount)",
                fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                fontWeight = if (comicSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (comicSelected) Color.White else tokens.color.textSecondary,
                maxLines = 1,
            )
        }

        // 图片 Tab
        val imageSelected = selected == PipelineMediaTab.IMAGE
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(7.dp))
                .then(
                    if (imageSelected) {
                        Modifier.background(tabActiveBg)
                    } else {
                        Modifier.clickable { onSelect(PipelineMediaTab.IMAGE) }
                    }
                )
                .padding(horizontal = 13.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "图片 ($imageCount)",
                fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                fontWeight = if (imageSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (imageSelected) Color.White else tokens.color.textSecondary,
                maxLines = 1,
            )
        }
    }
}

