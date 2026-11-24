package com.venera.desktop

import com.venera.desktop.data.DesktopErrorBus
import com.venera.desktop.ui.DesktopFeedbackBus
import com.venera.desktop.ui.DesktopFeedbackTab
import com.venera.desktop.data.normalizeIssueCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.awt.image.BufferedImage

class DesktopErrorAndFeedbackTest {

    @Before
    fun setup() {
        DesktopErrorBus.clearHistory()
        DesktopFeedbackBus.closePanel()
    }

    @Test
    fun `normalizeIssueCategory maps sub-categories into comic gallery or system`() {
        assertEquals("漫画", normalizeIssueCategory("漫画"))
        assertEquals("漫画", normalizeIssueCategory("本地收藏"))
        assertEquals("图库", normalizeIssueCategory("图片加载"))
        assertEquals("图库", normalizeIssueCategory("视频"))
        assertEquals("系统", normalizeIssueCategory("程序异常"))
        assertEquals("系统", normalizeIssueCategory("引擎"))
    }

    @Test
    fun `report records humanReason and deduplicates within window`() {
        DesktopErrorBus.report(
            category = "漫画",
            headline = "本地收藏操作失败",
            humanReason = "未能更新收藏夹",
            detail = "SQLite error",
            dedupKey = "test:fav",
        )
        DesktopErrorBus.report(
            category = "漫画",
            headline = "本地收藏操作失败",
            humanReason = "未能更新收藏夹",
            detail = "SQLite error",
            dedupKey = "test:fav",
        )

        val issues = DesktopErrorBus.issues.value
        assertEquals(1, issues.size)
        assertEquals(2, issues.first().count)
        assertEquals("未能更新收藏夹", issues.first().humanReason)
    }

    @Test
    fun `openForIssue links bug tab with headline and opens feedback panel`() {
        DesktopErrorBus.report(
            category = "图库",
            headline = "视频起播失败",
            humanReason = "未检测到 VLC",
            dedupKey = "test:vlc",
        )
        val record = DesktopErrorBus.issues.value.first()
        DesktopFeedbackBus.openForIssue(record)

        assertTrue(DesktopFeedbackBus.panelOpen.value)
        assertFalse(DesktopErrorBus.panelOpen.value)
        assertEquals(DesktopFeedbackTab.BUG, DesktopFeedbackBus.activeTab.value)
        assertEquals(record.id, DesktopFeedbackBus.selectedIssueId.value)
        assertEquals("视频起播失败", DesktopFeedbackBus.title.value)
    }

    @Test
    fun `feedback screenshots enforce max limit of 3`() {
        val img = BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB)
        DesktopFeedbackBus.screenshots.value.forEach { DesktopFeedbackBus.removeScreenshot(it.id) }

        assertTrue(DesktopFeedbackBus.addScreenshot(img, "1"))
        assertTrue(DesktopFeedbackBus.addScreenshot(img, "2"))
        assertTrue(DesktopFeedbackBus.addScreenshot(img, "3"))
        assertFalse(DesktopFeedbackBus.addScreenshot(img, "4"))
        assertEquals(3, DesktopFeedbackBus.screenshots.value.size)

        DesktopFeedbackBus.screenshots.value.forEach { DesktopFeedbackBus.removeScreenshot(it.id) }
    }
}
