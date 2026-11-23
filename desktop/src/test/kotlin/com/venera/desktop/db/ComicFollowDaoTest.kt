package com.venera.desktop.db

import com.venera.compose.data.db.ComicFollowDao
import com.venera.compose.data.db.ComicFollowOrigin
import com.venera.compose.data.db.ComicFollowRecord
import com.venera.compose.data.db.ComicFollowStatus
import com.venera.compose.data.db.CoreDbSchema
import com.venera.compose.data.db.FollowCheckOutcome
import com.venera.compose.data.db.FollowSyncSourceRecord
import com.venera.compose.data.db.requiredString
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 追更账本在真 sqlite 上的读写口径：双主键隔离、基线不覆盖、失败不污染快照、批量导入去重。
 *
 * 时钟全部注入假值，读数断言不依赖真实时间。
 */
class ComicFollowDaoTest {
    private lateinit var store: TestDatabase
    private lateinit var dao: ComicFollowDao

    private var now = 1_000L

    @Before
    fun setUp() {
        store = TestDatabase("comic-follow")
        CoreDbSchema.ensureOn(store.db)
        dao = ComicFollowDao(store.source) { now }
    }

    @After
    fun tearDown() {
        dao.close()
        store.close()
    }

    private fun record(
        comicId: String = "c1",
        sourceName: String = "拷贝漫画",
        title: String = "标题",
        createdAt: Long = 0L,
        hasNewUpdate: Boolean = false,
        newChaptersCount: Int = if (hasNewUpdate) 2 else 0,
        latestChapterId: String = "ch-1",
        chapterCount: Int = 10,
        baselineInitialized: Boolean = true,
    ) = ComicFollowRecord(
        comicId = comicId,
        sourceName = sourceName,
        title = title,
        author = "作者",
        coverUrl = "https://cover/$comicId",
        status = ComicFollowStatus.ACTIVE,
        originType = ComicFollowOrigin.MANUAL,
        originFolderId = "",
        originFolderName = "",
        baselineInitialized = baselineInitialized,
        latestChapterId = latestChapterId,
        latestChapterTitle = "第 10 话",
        chapterCount = chapterCount,
        remoteUpdateTime = "2026-11-20",
        hasNewUpdate = hasNewUpdate,
        newChaptersCount = newChaptersCount,
        lastUpdateDetectedAt = if (hasNewUpdate) 900L else 0L,
        lastCheckAt = 800L,
        lastError = "",
        consecutiveFailures = 0,
        createdAt = createdAt,
    )

    @Test
    fun `新增后全部字段读回且流同步刷新`() {
        runBlocking {
            assertTrue(dao.addIfAbsent(record(createdAt = now)))
        }

        awaitUntil("followsFlow 出现 c1") { dao.followsFlow.value.any { it.comicId == "c1" } }
        val row = dao.followsFlow.value.single()
        assertEquals("拷贝漫画", row.sourceName)
        assertEquals("作者", row.author)
        assertEquals("https://cover/c1", row.coverUrl)
        assertEquals(ComicFollowOrigin.MANUAL, row.originType)
        assertEquals("ch-1", row.latestChapterId)
        assertEquals(10, row.chapterCount)
        assertEquals(1_000L, row.createdAt)
        assertEquals(1_000L, store.db.query("SELECT created_at FROM comic_follow").single().long("created_at"))
    }

    @Test
    fun `同主键重复新增被忽略且保留原基线`() {
        runBlocking {
            assertTrue(dao.addIfAbsent(record(latestChapterId = "baseline-1", chapterCount = 5)))
            assertFalse(
                dao.addIfAbsent(
                    record(latestChapterId = "overwrite-1", chapterCount = 99, createdAt = 2_000L)
                )
            )
            val row = dao.get("c1", "拷贝漫画")!!
            assertEquals("baseline-1", row.latestChapterId)
            assertEquals(5, row.chapterCount)
            // 两次都没传 createdAt ⇒ 默认 0，重复写入不改原行
            assertEquals(0L, row.createdAt)
        }
        assertEquals(1, store.db.query("SELECT comic_id FROM comic_follow").size)
    }

    @Test
    fun `不同源同 ID 各追各的`() {
        runBlocking {
            dao.addIfAbsent(record(comicId = "c1", sourceName = "拷贝漫画"))
            dao.addIfAbsent(record(comicId = "c1", sourceName = "哔咔漫画"))
            dao.remove("c1", "拷贝漫画")

            assertEquals("哔咔漫画", dao.get("c1", "哔咔漫画")?.sourceName)
            assertNull(dao.get("c1", "拷贝漫画"))
        }
    }

    @Test
    fun `建基线只写快照不标红`() {
        runBlocking {
            dao.addIfAbsent(record(baselineInitialized = false, hasNewUpdate = true, newChaptersCount = 3))
            dao.initializeBaseline(
                comicId = "c1",
                sourceName = "拷贝漫画",
                latestChapterId = "ch-142",
                latestChapterTitle = "第 142 话",
                chapterCount = 142,
                remoteUpdateTime = "2026-11-21",
                checkedAt = 5_000L,
            )

            val row = dao.get("c1", "拷贝漫画")!!
            assertEquals("ch-142", row.latestChapterId)
            assertEquals(142, row.chapterCount)
            assertEquals("2026-11-21", row.remoteUpdateTime)
            assertTrue(row.baselineInitialized)
            assertFalse(row.hasNewUpdate)
            assertEquals(0, row.newChaptersCount)
            assertEquals(5_000L, row.lastCheckAt)
        }
    }

    @Test
    fun `消红点只清未读标记不动章节读数`() {
        runBlocking {
            dao.addIfAbsent(record(hasNewUpdate = true))
            dao.markRead("c1", "拷贝漫画")

            val row = dao.get("c1", "拷贝漫画")!!
            assertFalse(row.hasNewUpdate)
            assertEquals(0, row.newChaptersCount)
            assertEquals("ch-1", row.latestChapterId)
            assertEquals(10, row.chapterCount)
        }
    }

    @Test
    fun `巡检成功整笔落读数并清错误`() {
        runBlocking {
            dao.addIfAbsent(record(baselineInitialized = false, chapterCount = 0, latestChapterId = ""))
            dao.applyCheckFailure("c1", "拷贝漫画", "timeout", checkedAt = 2_000L)
            now = 3_000L
            dao.applyCheckSuccess(
                "c1",
                "拷贝漫画",
                FollowCheckOutcome(
                    latestChapterId = "ch-12",
                    latestChapterTitle = "第 12 话",
                    chapterCount = 12,
                    remoteUpdateTime = "2026-11-22",
                    hasNewUpdate = true,
                    newChaptersCount = 2,
                    detectedAt = 2_500L,
                ),
            )

            val row = dao.get("c1", "拷贝漫画")!!
            assertTrue(row.baselineInitialized)
            assertEquals("ch-12", row.latestChapterId)
            assertEquals(12, row.chapterCount)
            assertTrue(row.hasNewUpdate)
            assertEquals(2, row.newChaptersCount)
            assertEquals(2_500L, row.lastUpdateDetectedAt)
            assertEquals(3_000L, row.lastCheckAt)
            assertEquals("", row.lastError)
            assertEquals(0, row.consecutiveFailures)
        }
    }

    @Test
    fun `巡检失败不覆盖章节快照与红点且连败累加`() {
        runBlocking {
            dao.addIfAbsent(record(hasNewUpdate = true, latestChapterId = "ch-9", chapterCount = 9))
            dao.applyCheckFailure("c1", "拷贝漫画", "403", checkedAt = 4_000L)
            dao.applyCheckFailure("c1", "拷贝漫画", "403", checkedAt = 5_000L)

            val row = dao.get("c1", "拷贝漫画")!!
            assertEquals("ch-9", row.latestChapterId)
            assertEquals(9, row.chapterCount)
            assertTrue(row.hasNewUpdate)
            assertEquals("403", row.lastError)
            assertEquals(2, row.consecutiveFailures)
            assertEquals(5_000L, row.lastCheckAt)
        }
    }

    @Test
    fun `批量导入去重并给零时间项补当前时间`() {
        val result = runBlocking {
            dao.addIfAbsent(record(comicId = "old", createdAt = 1L))
            now = 7_000L
            dao.importBatch(
                listOf(
                    record(comicId = "old", baselineInitialized = true, latestChapterId = "keep-me"),
                    record(comicId = "new-a", baselineInitialized = false, latestChapterId = ""),
                    record(comicId = "new-b", createdAt = 9_000L),
                    record(comicId = "new-b", createdAt = 9_000L),
                )
            )
        }

        assertEquals(2, result.inserted)
        assertEquals(2, result.skipped)
        assertEquals(3, store.db.query("SELECT comic_id FROM comic_follow").size)
        runBlocking {
            // 已存在的条目保留原基线，不被导入快照覆盖
            assertEquals("ch-1", dao.get("old", "拷贝漫画")!!.latestChapterId)
            // 新增条目 createdAt 为 0 时补本次时钟
            assertEquals(7_000L, dao.get("new-a", "拷贝漫画")!!.createdAt)
            assertEquals(9_000L, dao.get("new-b", "拷贝漫画")!!.createdAt)
        }
    }

    @Test
    fun `网站夹绑定插入后可刷新读数且重复绑定保留自动导入开关`() {
        runBlocking {
            dao.upsertSyncSource(
                FollowSyncSourceRecord(
                    sourceName = "拷贝漫画",
                    networkFolderId = "f1",
                    networkFolderName = "在看",
                    autoImportNew = false,
                )
            )
            dao.upsertSyncSource(
                FollowSyncSourceRecord(
                    sourceName = "拷贝漫画",
                    networkFolderId = "f1",
                    networkFolderName = "在看（改名）",
                    lastSyncedAt = 5_000L,
                    lastSyncedCount = 42,
                    autoImportNew = true,
                )
            )

            val row = dao.listSyncSources().single()
            assertEquals("在看（改名）", row.networkFolderName)
            assertEquals(5_000L, row.lastSyncedAt)
            assertEquals(42, row.lastSyncedCount)
            // 已有绑定保留首次写入的 autoImportNew
            assertFalse(row.autoImportNew)
        }
    }

    @Test
    fun `取消绑定时追更条目原样保留`() {
        runBlocking {
            dao.addIfAbsent(record())
            dao.upsertSyncSource(
                FollowSyncSourceRecord(sourceName = "拷贝漫画", networkFolderId = "f1", networkFolderName = "在看")
            )
            dao.removeSyncSource("拷贝漫画", "f1")
            assertEquals(0, dao.listSyncSources().size)
        }

        assertEquals(1, store.db.query("SELECT comic_id FROM comic_follow").size)
    }

    @Test
    fun `状态切换只改状态列`() {
        runBlocking {
            dao.addIfAbsent(record(hasNewUpdate = true))
            dao.setStatus("c1", "拷贝漫画", ComicFollowStatus.COMPLETED)

            val row = dao.get("c1", "拷贝漫画")!!
            assertEquals(ComicFollowStatus.COMPLETED, row.status)
            assertTrue(row.hasNewUpdate)
            assertEquals("ch-1", row.latestChapterId)
        }
    }

    @Test
    fun `双主键列在库里确实是 NOT NULL 的主键`() {
        val row = store.db.query(
            "SELECT sql FROM sqlite_master WHERE type='table' AND name='comic_follow'"
        ).single()
        val ddl = row.requiredString("sql")
        assertTrue("comic_id 必须是主键之一", ddl.contains("PRIMARY KEY (comic_id, source_name)"))
    }
}
