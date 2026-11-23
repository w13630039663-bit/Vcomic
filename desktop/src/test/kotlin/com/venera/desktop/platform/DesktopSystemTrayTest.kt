package com.venera.desktop.platform

import java.awt.Rectangle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopSystemTrayTest {

    @Test
    fun `下载进行中与窗口可见时生成隐藏到托盘和暂停全部下载`() {
        val entries = buildDesktopTrayMenuEntries(
            snapshot = DesktopTraySnapshot(
                windowVisible = true,
                runningCount = 1,
                pendingCount = 2,
                stoppedCount = 0,
                failedCount = 0,
                maskEnabled = true,
                hasDownloadsDir = true,
                canCheckFollow = true,
            ),
            callbacks = DesktopTrayShellCallbacks(),
        )
        val header = entries.first() as DesktopTrayMenuEntry.Header
        val actions = entries.filterIsInstance<DesktopTrayMenuEntry.Action>()

        assertTrue(header.title.contains("正在下载 1 项"))
        assertTrue(header.title.contains("2 排队"))
        assertTrue(actions.any { it.id == "hide_window" && it.label == "隐藏到托盘" })
        assertTrue(actions.any { it.id == "pause_all" && it.label == "暂停全部下载 (3)" })
        val mask = actions.first { it.id == "toggle_mask" }
        assertEquals(true, mask.checked)
        assertEquals("隐私遮罩：已开启", mask.label)
    }

    @Test
    fun `窗口最小化且仅存在失败或暂停任务时显示唤醒和继续重试项`() {
        val entries = buildDesktopTrayMenuEntries(
            snapshot = DesktopTraySnapshot(
                windowVisible = false,
                runningCount = 0,
                pendingCount = 0,
                stoppedCount = 1,
                failedCount = 2,
                maskEnabled = false,
                hasDownloadsDir = false,
                canCheckFollow = false,
            ),
            callbacks = DesktopTrayShellCallbacks(),
        )
        val header = entries.first() as DesktopTrayMenuEntry.Header
        val actions = entries.filterIsInstance<DesktopTrayMenuEntry.Action>()

        assertEquals("下载异常 2 项", header.title)
        assertTrue(actions.any { it.id == "show_window" && it.label == "显示主窗口" })
        assertTrue(actions.any { it.id == "resume_all" && it.label == "重试 / 继续下载 (3)" })
        assertFalse(actions.first { it.id == "open_download_dir" }.enabled)
        assertFalse(actions.first { it.id == "check_follow" }.enabled)
        assertEquals(false, actions.first { it.id == "toggle_mask" }.checked)
    }

    @Test
    fun `右下角任务栏呼出时菜单自动向上左对齐且不超出工作区`() {
        val workArea = Rectangle(0, 0, 1920, 1032)
        val origin = computeTrayPopupOrigin(
            clickX = 1880,
            clickY = 1020,
            menuWidth = 236,
            menuHeight = 290,
            workArea = workArea,
        )
        assertTrue("X 不得超出右边界：${origin.x}", origin.x + 236 <= workArea.width)
        assertTrue("Y 不得超出底边界：${origin.y}", origin.y + 290 <= workArea.height)
        assertTrue("X/Y 不得小于安全边距：${origin.x},${origin.y}", origin.x >= 8 && origin.y >= 8)
    }
}
