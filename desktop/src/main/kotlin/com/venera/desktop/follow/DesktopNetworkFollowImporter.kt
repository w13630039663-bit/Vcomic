package com.venera.desktop.follow

import com.venera.compose.data.db.ComicFollowDao
import com.venera.compose.data.db.ComicFollowOrigin
import com.venera.compose.data.db.ComicFollowRecord
import com.venera.compose.data.db.FollowSyncSourceRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/** 网站收藏里的一本：导入只需要这三格（章节与更新时间留给首次巡检去建基线）。 */
data class FollowNetComic(
    val comicId: String,
    val title: String,
    val coverUrl: String = "",
    val author: String = "",
)

/**
 * 网站收藏的一页。[next] 是下一页令牌（游标或页码，由取数口自己解释），
 * [hasMore] 与 [next] 分开摆是因为有的源只给 `maxPage` 不给游标。
 */
data class FollowNetPage(
    val comics: List<FollowNetComic>,
    val next: String? = null,
    val hasMore: Boolean = false,
    /** 站方原话的错误（非空即视为本页不完整，导入到此为止）。 */
    val error: String? = null,
)

/** 一次导入的进度；[summary] 只在跑完后非空，[notice] 放中途的可读提示。 */
data class NetFollowImportProgress(
    val running: Boolean = false,
    val sourceName: String = "",
    val folderName: String = "",
    val pages: Int = 0,
    val fetched: Int = 0,
    val inserted: Int = 0,
    val skipped: Int = 0,
    val summary: String = "",
    val notice: String = "",
)

/** 导入请求：往哪一夹导、记账时用哪个名义。 */
data class NetFollowImportRequest(
    val sourceName: String,
    val folderId: String,
    val folderName: String,
)

/**
 * 网站收藏夹 → 追更账本的批量导入。
 *
 * 只做"登记"，不拉详情：导入的每本 `baselineInitialized = false`，
 * 首轮巡检拉到详情后建基线、不误报红点。整批写库是 DAO 的单个事务，
 * 已存在的条目（同 `(comicId, sourceName)`）原样跳过、保留其基线。
 *
 * @param maxComics 单次导入上限（计划口径 200 本），到顶即停并如实说明。
 * @param loadPage 取页口：`null` 令牌 = 首页，之后按上一页给的 [FollowNetPage.next] 续。
 */
class DesktopNetworkFollowImporter(
    private val dao: ComicFollowDao,
    private val maxComics: Int = DEFAULT_MAX_COMICS,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    private val _progress = MutableStateFlow(NetFollowImportProgress())
    val progress: StateFlow<NetFollowImportProgress> = _progress.asStateFlow()

    private val running = AtomicBoolean(false)

    private var job: Job? = null

    fun start(
        scope: CoroutineScope,
        request: NetFollowImportRequest,
        loadPage: suspend (token: String?) -> FollowNetPage,
        onClose: () -> Unit = {},
    ): Job? {
        if (!running.compareAndSet(false, true)) {
            runCatching { onClose() }
            return null
        }
        _progress.value = NetFollowImportProgress(
            running = true,
            sourceName = request.sourceName,
            folderName = request.folderName,
        )
        val started = scope.launch(Dispatchers.IO) {
            var token: String? = null
            var pages = 0
            var fetched = 0
            val collected = mutableListOf<FollowNetComic>()
            var stopNote = ""
            var capHit = false
            try {
                while (fetched < maxComics) {
                    currentCoroutineContext().ensureActive()
                    val page = try {
                        loadPage(token)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        println("D_追更导入 取页失败 page=${pages + 1} ${e.javaClass.simpleName}: ${e.message?.take(160) ?: ""}")
                        com.venera.desktop.data.DesktopErrorBus.report(
                            category = "漫画",
                            headline = "网络收藏导入取数失败",
                            humanReason = "导入到第 ${pages + 1} 页时断了，已取到的前几页仍在；可稍后重试。",
                            detail = "${request.sourceName}: ${e.javaClass.simpleName}: ${e.message.orEmpty()}",
                            dedupKey = "comic:follow_import:${request.sourceName}",
                        )
                        stopNote = "第 ${pages + 1} 页取数失败：${com.venera.desktop.platform.desktopFailureNoteOf(e)}"
                        break
                    }
                    if (page.error != null && page.comics.isEmpty()) {
                        stopNote = com.venera.desktop.platform.desktopFailureNoteOfText(page.error)
                        break
                    }
                    if (page.comics.isEmpty()) break
                    pages += 1
                    val room = maxComics - fetched
                    val take = page.comics.take(room)
                    collected += take
                    fetched += take.size
                    if (take.size < page.comics.size) capHit = true
                    _progress.value = _progress.value.copy(pages = pages, fetched = fetched)
                    if (page.error != null) {
                        stopNote = com.venera.desktop.platform.desktopFailureNoteOfText(page.error)
                        break
                    }
                    if (fetched >= maxComics) {
                        capHit = capHit || page.hasMore
                        break
                    }
                    if (!page.hasMore) break
                    token = page.next
                }

                val inserted: Int
                val skipped: Int
                if (collected.isEmpty()) {
                    inserted = 0
                    skipped = 0
                } else {
                    val records = collected.map { comic ->
                        ComicFollowRecord(
                            comicId = comic.comicId,
                            sourceName = request.sourceName,
                            title = comic.title,
                            author = comic.author,
                            coverUrl = comic.coverUrl,
                            originType = ComicFollowOrigin.NET_FOLDER,
                            originFolderId = request.folderId,
                            originFolderName = request.folderName,
                            baselineInitialized = false,
                            createdAt = clock(),
                        )
                    }
                    val result = dao.importBatch(records)
                    inserted = result.inserted
                    skipped = result.skipped
                    // 只要真拉到过东西就记订阅关系；空的夹不记，免得空订阅一直被同步。
                    runCatching {
                        dao.upsertSyncSource(
                            FollowSyncSourceRecord(
                                sourceName = request.sourceName,
                                networkFolderId = request.folderId,
                                networkFolderName = request.folderName,
                                lastSyncedAt = clock(),
                                lastSyncedCount = collected.size,
                            ),
                        )
                    }
                }

                val summary = buildSummary(
                    inserted = inserted,
                    skipped = skipped,
                    fetched = fetched,
                    pages = pages,
                    capHit = capHit,
                    stopNote = stopNote,
                )
                _progress.value = _progress.value.copy(
                    running = false,
                    inserted = inserted,
                    skipped = skipped,
                    summary = summary,
                    notice = if (stopNote.isBlank()) "" else stopNote,
                )
            } finally {
                runCatching { onClose() }
                running.set(false)
                if (_progress.value.running) {
                    _progress.value = _progress.value.copy(running = false)
                }
                job = null
            }
        }
        job = started
        return started
    }

    private fun buildSummary(
        inserted: Int,
        skipped: Int,
        fetched: Int,
        pages: Int,
        capHit: Boolean,
        stopNote: String,
    ): String {
        val head = if (fetched == 0) {
            "未拉到可导入的收藏"
        } else {
            "新增 $inserted 本追更，已有 $skipped 本跳过（共拉取 $fetched 本 / $pages 页）"
        }
        val cap = if (capHit) "，已达单次上限 $maxComics 本，可再次同步继续导入" else ""
        val tail = if (stopNote.isBlank()) "" else "；$stopNote"
        return head + cap + tail
    }

    /** 停掉本轮（协程取消，已写入的批次保留，不回溯）。 */
    fun cancel() {
        job?.cancel()
        job = null
        running.set(false)
        _progress.value = _progress.value.copy(
            running = false,
            summary = _progress.value.summary.ifBlank { "已停止导入" },
        )
    }

    companion object {
        const val DEFAULT_MAX_COMICS = 200
    }
}
