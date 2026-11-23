package com.venera.desktop.follow

import com.venera.compose.data.db.ComicFollowDao
import com.venera.compose.data.db.ComicFollowOrigin
import com.venera.compose.data.db.ComicFollowRecord
import com.venera.compose.data.db.CoreDbSchema
import com.venera.compose.source.model.Comic
import com.venera.compose.source.model.ComicChapter
import com.venera.compose.source.model.ComicDetails
import com.venera.desktop.db.TestDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 详情页端口与追更账本的真库判据：读态、一键加入即建基线、进入详情消红点、再点取消。
 *
 * 不碰 UI、不碰网络；时钟注入假值。
 */
class DesktopFollowDetailPortTest {

    private lateinit var store: TestDatabase
    private lateinit var dao: ComicFollowDao
    private lateinit var port: DesktopFollowDetailPort

    @Before
    fun setUp() {
        store = TestDatabase("follow-detail-port")
        CoreDbSchema.ensureOn(store.db)
        dao = ComicFollowDao(store.source) { 5_000L }
        port = DesktopFollowDetailPort(dao) { 5_000L }
    }

    @After
    fun tearDown() {
        dao.close()
        store.close()
    }

    private fun snapshot(id: String = "ch-5", count: Int = 5) =
        FollowSnapshot(id, "第 $count 话", count, "2026-11-20")

    @Test
    fun `未追更时挂上读到没追`() {
        runBlocking {
            port.attach("c1", "拷贝漫画")
            assertEquals(false, port.state.value.following)
        }
    }

    @Test
    fun `点击加入以当前详情建基线且不标红`() {
        runBlocking {
            port.attach("c1", "拷贝漫画")
            val following = port.toggle(
                comicId = "c1",
                sourceName = "拷贝漫画",
                title = "某本漫画",
                author = "某人",
                coverUrl = "https://cover",
                snapshot = snapshot(),
            )

            assertTrue(following)
            assertEquals(true, port.state.value.following)
            val row = dao.get("c1", "拷贝漫画")!!
            assertTrue(row.baselineInitialized)
            assertEquals("ch-5", row.latestChapterId)
            assertEquals(5, row.chapterCount)
            assertEquals("2026-11-20", row.remoteUpdateTime)
            assertFalse(row.hasNewUpdate)
            assertEquals(0, row.newChaptersCount)
            assertEquals(ComicFollowOrigin.MANUAL, row.originType)
            assertEquals(5_000L, row.createdAt)
        }
    }

    @Test
    fun `挂上已追更的条目会顺手消红点`() {
        runBlocking {
            dao.addIfAbsent(
                ComicFollowRecord(
                    comicId = "c1",
                    sourceName = "拷贝漫画",
                    title = "某本漫画",
                    baselineInitialized = true,
                    latestChapterId = "ch-9",
                    latestChapterTitle = "第 9 话",
                    chapterCount = 9,
                    hasNewUpdate = true,
                    newChaptersCount = 2,
                    createdAt = 1L,
                )
            )
            port.attach("c1", "拷贝漫画")

            assertEquals(true, port.state.value.following)
            val row = dao.get("c1", "拷贝漫画")!!
            assertFalse(row.hasNewUpdate)
            assertEquals(0, row.newChaptersCount)
            // 章节读数不清：消的只是未读标记
            assertEquals("ch-9", row.latestChapterId)
            assertEquals(9, row.chapterCount)
        }
    }

    @Test
    fun `再点一次取消追更并删行`() {
        runBlocking {
            dao.addIfAbsent(
                ComicFollowRecord(
                    comicId = "c1",
                    sourceName = "拷贝漫画",
                    title = "某本漫画",
                    baselineInitialized = true,
                    createdAt = 1L,
                )
            )
            port.attach("c1", "拷贝漫画")
            val following = port.toggle("c1", "拷贝漫画", "某本漫画", "", "", snapshot())

            assertFalse(following)
            assertEquals(false, port.state.value.following)
            assertNull(dao.get("c1", "拷贝漫画"))
        }
    }

    @Test
    fun `详情压成快照时占位章不算章节`() {
        val normal = ComicDetails(
            comic = Comic("c1", "某本漫画", sourceKey = "jm"),
            chapters = listOf(
                ComicChapter("ch-1", "第 1 话"),
                ComicChapter("ch-2", "第 2 话"),
            ),
            updateTime = "2026-11-20",
        ).toFollowSnapshot()
        assertEquals("ch-2", normal.latestChapterId)
        assertEquals("第 2 话", normal.latestChapterTitle)
        assertEquals(2, normal.chapterCount)

        val placeholder = ComicDetails(
            comic = Comic("c2", "整本一章", sourceKey = "eh"),
            chapters = listOf(ComicChapter("c2", "")),
            updateTime = "2026-11-20",
        ).toFollowSnapshot()
        assertEquals(0, placeholder.chapterCount)
        assertEquals("", placeholder.latestChapterId)
        assertEquals("", placeholder.latestChapterTitle)
    }
}
