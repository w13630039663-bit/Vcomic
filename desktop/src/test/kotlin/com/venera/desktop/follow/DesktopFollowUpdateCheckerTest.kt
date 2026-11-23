package com.venera.desktop.follow

import com.venera.compose.data.db.ComicFollowDao
import com.venera.compose.data.db.ComicFollowOrigin
import com.venera.compose.data.db.ComicFollowRecord
import com.venera.compose.data.db.ComicFollowStatus
import com.venera.compose.data.db.CoreDbSchema
import com.venera.desktop.db.TestDatabase
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 串行巡检器的行为：单飞行、首建基线不标红、有新更标红、冷却跳过、同源熔断、取消、失败不污染快照。
 *
 * 取数用假 [FollowDetailSource]（零网络、零 JS），时钟与节流全注入，用例不依赖真实时间。
 */
class DesktopFollowUpdateCheckerTest {

    private lateinit var store: TestDatabase
    private lateinit var dao: ComicFollowDao
    private var now = 10_000L

    @Before
    fun setUp() {
        store = TestDatabase("follow-checker")
        CoreDbSchema.ensureOn(store.db)
        dao = ComicFollowDao(store.source) { now }
    }

    @After
    fun tearDown() {
        dao.close()
        store.close()
    }

    private fun record(
        comicId: String,
        sourceName: String = "拷贝漫画",
        title: String = "漫画 $comicId",
        baselineInitialized: Boolean = true,
        latestChapterId: String = "ch-10",
        chapterCount: Int = 10,
        hasNewUpdate: Boolean = false,
        newChaptersCount: Int = 0,
        lastCheckAt: Long = 0L,
    ) = ComicFollowRecord(
        comicId = comicId,
        sourceName = sourceName,
        title = title,
        status = ComicFollowStatus.ACTIVE,
        originType = ComicFollowOrigin.MANUAL,
        baselineInitialized = baselineInitialized,
        latestChapterId = latestChapterId,
        latestChapterTitle = "第 $chapterCount 话",
        chapterCount = chapterCount,
        remoteUpdateTime = "2026-11-20",
        hasNewUpdate = hasNewUpdate,
        newChaptersCount = newChaptersCount,
        lastCheckAt = lastCheckAt,
        createdAt = 1L,
    )

    private fun checker(details: FollowDetailSource, cooldownMillis: Long = 60_000L) =
        DesktopFollowUpdateChecker(
            dao = dao,
            details = details,
            cooldownMillis = cooldownMillis,
            sameSourceDelayMillis = 0L,
            crossSourceDelayMillis = 0L,
            clock = { now },
        )

    private class FakeDetailSource(
        private val snapshots: Map<String, FollowSnapshot> = emptyMap(),
    ) : FollowDetailSource {
        val calls = mutableListOf<String>()
        val failing = mutableSetOf<String>()
        var closed = 0
        var onLoad: (suspend (String) -> Unit)? = null

        override suspend fun load(sourceName: String, comicId: String): FollowSnapshot {
            calls += comicId
            onLoad?.invoke(comicId)
            if (comicId in failing) error("模拟 403")
            return snapshots[comicId] ?: error("没有快照 $comicId")
        }

        override fun closeRound() {
            closed++
        }
    }

    @Test
    fun `首次未建基线只写基线不标红`() {
        runBlocking {
            dao.importBatch(listOf(record("c1", baselineInitialized = false, latestChapterId = "", chapterCount = 0)))
            val details = FakeDetailSource(mapOf("c1" to FollowSnapshot("ch-142", "第 142 话", 142, "2026-11-22")))
            val job = checker(details).start(this, dao.listSync())
            job?.join()

            val row = dao.get("c1", "拷贝漫画")!!
            assertTrue(row.baselineInitialized)
            assertEquals("ch-142", row.latestChapterId)
            assertEquals(142, row.chapterCount)
            assertFalse(row.hasNewUpdate)
            assertEquals(0, row.newChaptersCount)
        }
    }

    @Test
    fun `发现新章节标红并记增量`() {
        runBlocking {
            dao.addIfAbsent(record("c1", latestChapterId = "ch-10", chapterCount = 10))
            val details = FakeDetailSource(mapOf("c1" to FollowSnapshot("ch-12", "第 12 话", 12, "2026-11-22")))
            val local = checker(details)
            val job = local.start(this, dao.listSync())
            job?.join()

            val row = dao.get("c1", "拷贝漫画")!!
            assertTrue(row.hasNewUpdate)
            assertEquals(2, row.newChaptersCount)
            assertEquals(now, row.lastUpdateDetectedAt)
            assertEquals(1, local.progress.value.found)
            assertFalse(local.progress.value.running)
            assertTrue(local.progress.value.summary.contains("发现 1 本"))
        }
    }

    @Test
    fun `冷却期内跳过且不取数`() {
        runBlocking {
            dao.addIfAbsent(record("c1", lastCheckAt = now - 1_000L))
            val details = FakeDetailSource(mapOf("c1" to FollowSnapshot("ch-12", "第 12 话", 12, "2026-11-22")))
            val local = checker(details, cooldownMillis = 60_000L)
            val job = local.start(this, dao.listSync())
            job?.join()

            assertEquals(0, details.calls.size)
            assertEquals(1, local.progress.value.skipped)
        }
    }

    @Test
    fun `强制重查绕过冷却`() {
        runBlocking {
            dao.addIfAbsent(record("c1", lastCheckAt = now - 1_000L))
            val details = FakeDetailSource(mapOf("c1" to FollowSnapshot("ch-12", "第 12 话", 12, "2026-11-22")))
            val local = checker(details, cooldownMillis = 60_000L)
            val job = local.start(this, dao.listSync(), force = true)
            job?.join()

            assertEquals(listOf("c1"), details.calls)
            assertEquals(1, local.progress.value.found)
        }
    }

    @Test
    fun `同源连续三败熔断剩余且提示过盾`() {
        runBlocking {
            listOf("c1", "c2", "c3", "c4").forEach { dao.addIfAbsent(record(it)) }
            val details = FakeDetailSource().apply { failing += listOf("c1", "c2", "c3") }
            val local = checker(details)
            val job = local.start(this, dao.listSync())
            job?.join()

            // 前三本各取一次，熔断后第四本同一源不再取数
            assertEquals(listOf("c1", "c2", "c3"), details.calls)
            assertEquals(3, local.progress.value.failed)
            assertEquals(1, local.progress.value.skipped)
            assertTrue(local.progress.value.notice.contains("连续失败 3 次"))
        }
    }

    @Test
    fun `失败只落错误不覆盖已有章节与红点`() {
        runBlocking {
            dao.addIfAbsent(
                record("c1", latestChapterId = "ch-9", chapterCount = 9, hasNewUpdate = true, newChaptersCount = 2)
            )
            val details = FakeDetailSource().apply { failing += "c1" }
            val job = checker(details).start(this, dao.listSync())
            job?.join()

            val row = dao.get("c1", "拷贝漫画")!!
            assertEquals("ch-9", row.latestChapterId)
            assertEquals(9, row.chapterCount)
            assertTrue(row.hasNewUpdate)
            assertEquals(2, row.newChaptersCount)
            assertEquals("模拟 403", row.lastError)
            assertEquals(1, row.consecutiveFailures)
        }
    }

    @Test
    fun `同源成功一次后连败计数归零不误熔断`() {
        runBlocking {
            listOf("c1", "c2", "c3").forEach { dao.addIfAbsent(record(it)) }
            val details = FakeDetailSource(
                snapshots = mapOf(
                    "c1" to FollowSnapshot("ch-12", "第 12 话", 12, "2026-11-22"),
                    "c2" to FollowSnapshot("ch-12", "第 12 话", 12, "2026-11-22"),
                    "c3" to FollowSnapshot("ch-12", "第 12 话", 12, "2026-11-22"),
                )
            )
            val job = checker(details).start(this, dao.listSync())
            job?.join()

            assertEquals(3, details.calls.size)
            assertEquals(0, dao.get("c2", "拷贝漫画")!!.consecutiveFailures)
        }
    }

    @Test
    fun `中途取消立即停止且不跑完剩余条目`() {
        runBlocking {
            listOf("c1", "c2", "c3").forEach { dao.addIfAbsent(record(it)) }
            val details = FakeDetailSource(
                snapshots = mapOf("c1" to FollowSnapshot("ch-12", "第 12 话", 12, "2026-11-22"))
            ).apply {
                onLoad = { delay(60_000L) }
            }
            val local = checker(details)
            val job = local.start(this, dao.listSync())
            assertNotNull(job)
            delay(100L)
            local.cancel()
            job!!.join()

            assertEquals(1, details.calls.size)
            assertFalse(local.progress.value.running)
            assertTrue(local.progress.value.summary.contains("已停止"))
        }
    }

    @Test
    fun `重复启动幂等防重入`() {
        runBlocking {
            dao.addIfAbsent(record("c1"))
            val details = FakeDetailSource(mapOf("c1" to FollowSnapshot("ch-12", "第 12 话", 12, "2026-11-22"))).apply {
                onLoad = { delay(50L) }
            }
            val local = checker(details)
            local.start(this, dao.listSync())
            delay(10L)
            assertNull("第二轮必须在第一轮跑完前被拒", local.start(this, dao.listSync()))
            local.cancel()
        }
    }

    @Test
    fun `没有可检查条目时不占运行位`() {
        runBlocking {
            dao.addIfAbsent(record("c1").copy(status = ComicFollowStatus.COMPLETED))
            val local = checker(FakeDetailSource())
            assertNull(local.start(this, dao.listSync()))
            assertTrue(local.progress.value.summary.contains("没有可检查"))
        }
    }
}
