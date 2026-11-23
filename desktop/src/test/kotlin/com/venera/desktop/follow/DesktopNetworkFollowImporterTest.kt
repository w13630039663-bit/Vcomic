package com.venera.desktop.follow

import com.venera.compose.data.db.ComicFollowDao
import com.venera.compose.data.db.ComicFollowOrigin
import com.venera.compose.data.db.CoreDbSchema
import com.venera.desktop.db.TestDatabase
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 网站收藏导入的真库判据：分页串联、重复导入不覆盖基线、上限截断、失败保留已入库批次、
 * 订阅关系落表。取数与时钟全部注入假值，不碰网络。
 */
class DesktopNetworkFollowImporterTest {

    private lateinit var store: TestDatabase
    private lateinit var dao: ComicFollowDao
    private lateinit var importer: DesktopNetworkFollowImporter

    private val request = NetFollowImportRequest(
        sourceName = "拷贝漫画",
        folderId = "fav-1",
        folderName = "我的收藏",
    )

    @Before
    fun setUp() {
        store = TestDatabase("net-follow-import")
        CoreDbSchema.ensureOn(store.db)
        dao = ComicFollowDao(store.source) { 9_000L }
        importer = DesktopNetworkFollowImporter(dao, clock = { 9_000L })
    }

    @After
    fun tearDown() {
        dao.close()
        store.close()
    }

    private fun comic(id: String) = FollowNetComic(comicId = id, title = "本 $id")

    /** 假取页口：按令牌顺序吐页，令牌 = 页码字符串。 */
    private fun pages(vararg pages: List<FollowNetComic>): suspend (String?) -> FollowNetPage = { token ->
        val index = token?.toInt() ?: 0
        val list = pages.getOrNull(index).orEmpty()
        val hasMore = index < pages.lastIndex
        FollowNetPage(list, next = if (hasMore) (index + 1).toString() else null, hasMore = hasMore)
    }

    @Test
    fun 两页三本全入库且记订阅() {
        runBlocking {
            val job = importer.start(this, request, pages(listOf(comic("a"), comic("b")), listOf(comic("c"))))
            job?.join()
            val records = dao.listSync()
            assertEquals(3, records.size)
            assertTrue(records.all { it.originType == ComicFollowOrigin.NET_FOLDER })
            assertTrue(records.all { !it.baselineInitialized })
            assertTrue(records.all { it.originFolderName == "我的收藏" })
            val sync = dao.listSyncSources()
            assertEquals(1, sync.size)
            assertEquals(3, sync.single().lastSyncedCount)
            assertTrue(importer.progress.value.summary.contains("新增 3 本追更"))
            assertFalse(importer.progress.value.running)
        }
    }

    @Test
    fun 重复导入同夹只跳过不覆盖基线() {
        runBlocking {
            importer.start(this, request, pages(listOf(comic("a"), comic("b"))))?.join()
            // 模拟首轮巡检已建基线。
            dao.initializeBaseline(
                comicId = "a",
                sourceName = "拷贝漫画",
                latestChapterId = "ch-1",
                latestChapterTitle = "第 1 话",
                chapterCount = 1,
                remoteUpdateTime = "2026-11-20",
                checkedAt = 9_000L,
            )
            importer.start(this, request, pages(listOf(comic("a"), comic("b"), comic("c"))))?.join()
            assertEquals(3, dao.listSync().size)
            val a = dao.get("a", "拷贝漫画")
            assertTrue(a != null && a.baselineInitialized)
            assertTrue(importer.progress.value.summary.contains("新增 1 本追更"))
        }
    }

    @Test
    fun 到上限即停并如实说明() {
        runBlocking {
            val capped = DesktopNetworkFollowImporter(dao, maxComics = 3, clock = { 9_000L })
            capped.start(this, request, pages(listOf(comic("a"), comic("b")), listOf(comic("c"), comic("d"))))?.join()
            assertEquals(3, dao.listSync().size)
            assertTrue(capped.progress.value.summary.contains("已达单次上限 3 本"))
        }
    }

    @Test
    fun 第二页失败时第一页仍入库并给可读提示() {
        runBlocking {
            val throwing: suspend (String?) -> FollowNetPage = { token ->
                if (token != null) error("站点超时")
                FollowNetPage(listOf(comic("a")), next = "1", hasMore = true)
            }
            importer.start(this, request, throwing)?.join()
            assertEquals(1, dao.listSync().size)
            assertTrue(importer.progress.value.notice.contains("取数失败"))
            assertTrue(importer.progress.value.notice.contains("超时"))
        }
    }

    @Test
    fun 取消后立刻退出不再续页() {
        runBlocking {
            var loaded = 0
            val slow: suspend (String?) -> FollowNetPage = { token ->
                delay(30)
                loaded += 1
                FollowNetPage(listOf(comic("x$loaded")), next = (loaded).toString(), hasMore = true)
            }
            val job = importer.start(this, request, slow)
            delay(60)
            importer.cancel()
            job?.join()
            assertFalse(importer.progress.value.running)
            assertTrue(loaded <= 4)
        }
    }

    @Test
    fun 无游标按页码翻页多页全导入且结束触发释放回调() {
        runBlocking {
            var pageIdx = 0
            var closed = false
            val noCursor: suspend (String?) -> FollowNetPage = {
                pageIdx += 1
                when (pageIdx) {
                    1 -> FollowNetPage(listOf(comic("p1a"), comic("p1b")), next = null, hasMore = true)
                    2 -> FollowNetPage(listOf(comic("p2a")), next = null, hasMore = false)
                    else -> FollowNetPage(emptyList(), next = null, hasMore = false)
                }
            }
            val job = importer.start(this, request, noCursor, onClose = { closed = true })
            job?.join()
            assertEquals(3, dao.listSync().size)
            assertTrue(closed)
        }
    }
}
