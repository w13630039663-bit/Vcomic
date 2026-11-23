package com.venera.desktop.follow

import com.venera.compose.data.db.ComicFollowRecord
import com.venera.compose.data.db.ComicFollowStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 更新判定的纯逻辑：双因子命中、首次基线保护、未读累加、删章不误报。
 *
 * 全部用假快照与固定时间，不碰库、不碰网络。
 */
class FollowUpdateDiffTest {

    private fun previous(
        latestChapterId: String = "ch-100",
        latestChapterTitle: String = "第 100 话",
        chapterCount: Int = 100,
        remoteUpdateTime: String = "2026-11-20",
        baselineInitialized: Boolean = true,
        hasNewUpdate: Boolean = false,
        newChaptersCount: Int = 0,
        lastUpdateDetectedAt: Long = 0L,
    ) = ComicFollowRecord(
        comicId = "c1",
        sourceName = "拷贝漫画",
        title = "标题",
        status = ComicFollowStatus.ACTIVE,
        baselineInitialized = baselineInitialized,
        latestChapterId = latestChapterId,
        latestChapterTitle = latestChapterTitle,
        chapterCount = chapterCount,
        remoteUpdateTime = remoteUpdateTime,
        hasNewUpdate = hasNewUpdate,
        newChaptersCount = newChaptersCount,
        lastUpdateDetectedAt = lastUpdateDetectedAt,
    )

    @Test
    fun `首次未建基线只建基线不标红`() {
        val plan = FollowUpdateDiff.plan(
            previous(baselineInitialized = false, chapterCount = 0, latestChapterId = ""),
            FollowSnapshot("ch-142", "第 142 话", 142, "2026-11-22"),
            now = 9_000L,
        )

        assertEquals(FollowDiffKind.BASELINE, plan.kind)
        assertFalse(plan.hasNewUpdate)
        assertEquals(0, plan.newChaptersCount)
        assertEquals(0L, plan.detectedAt)
        assertEquals(142, plan.snapshot.chapterCount)
    }

    @Test
    fun `总话数增加命中且新增话数按差值算`() {
        val plan = FollowUpdateDiff.plan(
            previous(chapterCount = 100, latestChapterId = "ch-100"),
            FollowSnapshot("ch-102", "第 102 话", 102, "2026-11-22"),
            now = 9_000L,
        )

        assertEquals(FollowDiffKind.UPDATED, plan.kind)
        assertTrue(plan.hasNewUpdate)
        assertEquals(2, plan.newChaptersCount)
        assertEquals(9_000L, plan.detectedAt)
    }

    @Test
    fun `话数不变但最新章节 ID 变化也命中`() {
        val plan = FollowUpdateDiff.plan(
            previous(latestChapterId = "ch-a", latestChapterTitle = "第 100 话"),
            FollowSnapshot("ch-b", "第 100 话", 100, "2026-11-20"),
            now = 9_000L,
        )

        assertEquals(FollowDiffKind.UPDATED, plan.kind)
        assertEquals(1, plan.newChaptersCount)
    }

    @Test
    fun `最新话标题变化命中`() {
        val plan = FollowUpdateDiff.plan(
            previous(latestChapterTitle = "第 100 话"),
            FollowSnapshot("ch-100", "第 100 话 后篇", 100, "2026-11-20"),
            now = 9_000L,
        )

        assertEquals(FollowDiffKind.UPDATED, plan.kind)
    }

    @Test
    fun `内容完全相同不误报且保留旧红点`() {
        val plan = FollowUpdateDiff.plan(
            previous(hasNewUpdate = true, newChaptersCount = 3, lastUpdateDetectedAt = 5_000L),
            FollowSnapshot("ch-100", "第 100 话", 100, "2026-11-20"),
            now = 9_000L,
        )

        assertEquals(FollowDiffKind.UNCHANGED, plan.kind)
        assertTrue("普通巡检不能抹掉未读标记", plan.hasNewUpdate)
        assertEquals(3, plan.newChaptersCount)
        assertEquals(5_000L, plan.detectedAt)
    }

    @Test
    fun `未读红点未消时再次更新在旧读数上累加`() {
        val plan = FollowUpdateDiff.plan(
            previous(chapterCount = 100, hasNewUpdate = true, newChaptersCount = 2),
            FollowSnapshot("ch-103", "第 103 话", 103, "2026-11-22"),
            now = 9_000L,
        )

        assertEquals(FollowDiffKind.UPDATED, plan.kind)
        assertEquals(5, plan.newChaptersCount)
    }

    @Test
    fun `无章节源走更新时间副判据`() {
        val plan = FollowUpdateDiff.plan(
            previous(latestChapterId = "", chapterCount = 0, remoteUpdateTime = "2026-11-20"),
            FollowSnapshot("", "", 0, "2026-11-22"),
            now = 9_000L,
        )

        assertEquals(FollowDiffKind.UPDATED, plan.kind)
        assertEquals(1, plan.newChaptersCount)
    }

    @Test
    fun `无章节源更新时间不变不命中`() {
        val plan = FollowUpdateDiff.plan(
            previous(latestChapterId = "", chapterCount = 0, remoteUpdateTime = "2026-11-20"),
            FollowSnapshot("", "", 0, "2026-11-20"),
            now = 9_000L,
        )

        assertEquals(FollowDiffKind.UNCHANGED, plan.kind)
    }

    @Test
    fun `源删章导致话数减少不算更新`() {
        val plan = FollowUpdateDiff.plan(
            previous(chapterCount = 100, latestChapterId = "ch-100", latestChapterTitle = "第 100 话"),
            FollowSnapshot("ch-90", "第 90 话", 90, "2026-11-20"),
            now = 9_000L,
        )

        assertEquals(FollowDiffKind.UNCHANGED, plan.kind)
        assertFalse(plan.hasNewUpdate)
    }

    @Test
    fun `本次章节快照为空且旧有章节时不按更新时间误报`() {
        val plan = FollowUpdateDiff.plan(
            previous(chapterCount = 100, latestChapterId = "ch-100", remoteUpdateTime = "2026-11-20"),
            FollowSnapshot("", "", 0, "2026-11-22"),
            now = 9_000L,
        )

        assertEquals(FollowDiffKind.UNCHANGED, plan.kind)
    }
}
