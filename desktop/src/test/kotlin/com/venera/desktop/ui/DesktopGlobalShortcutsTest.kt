package com.venera.desktop.ui

import java.awt.event.KeyEvent as AwtKeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopGlobalShortcutsTest {

    @Test
    fun `全局导航快捷键匹配 Esc Ctrl+F F11 与 Ctrl+1到5`() {
        assertEquals(
            DesktopGlobalNavAction.ESCAPE,
            matchGlobalNavShortcut(DesktopShortcutSignal(AwtKeyEvent.VK_ESCAPE)),
        )
        assertEquals(
            DesktopGlobalNavAction.ESCAPE,
            matchGlobalNavShortcut(DesktopShortcutSignal(AwtKeyEvent.VK_LEFT, isAlt = true)),
        )
        assertEquals(
            DesktopGlobalNavAction.FOCUS_SEARCH,
            matchGlobalNavShortcut(DesktopShortcutSignal(AwtKeyEvent.VK_F, isCtrlOrMeta = true)),
        )
        assertEquals(
            DesktopGlobalNavAction.TOGGLE_FULLSCREEN,
            matchGlobalNavShortcut(DesktopShortcutSignal(AwtKeyEvent.VK_F11)),
        )
        assertEquals(
            DesktopGlobalNavAction.TAB_1,
            matchGlobalNavShortcut(DesktopShortcutSignal(AwtKeyEvent.VK_1, isCtrlOrMeta = true)),
        )
        assertEquals(
            DesktopGlobalNavAction.TAB_5,
            matchGlobalNavShortcut(DesktopShortcutSignal(AwtKeyEvent.VK_5, isCtrlOrMeta = true)),
        )
        assertNull(matchGlobalNavShortcut(DesktopShortcutSignal(AwtKeyEvent.VK_1)))
    }

    @Test
    fun `列表快捷键匹配 Ctrl+A Delete Enter`() {
        assertEquals(
            DesktopListShortcutAction.SELECT_ALL,
            matchListShortcut(DesktopShortcutSignal(AwtKeyEvent.VK_A, isCtrlOrMeta = true)),
        )
        assertEquals(
            DesktopListShortcutAction.DELETE_SELECTED,
            matchListShortcut(DesktopShortcutSignal(AwtKeyEvent.VK_DELETE)),
        )
        assertEquals(
            DesktopListShortcutAction.OPEN_SELECTED,
            matchListShortcut(DesktopShortcutSignal(AwtKeyEvent.VK_ENTER)),
        )
        assertNull(matchListShortcut(DesktopShortcutSignal(AwtKeyEvent.VK_A)))
    }

    @Test
    fun `阅读器与大图扩展快捷键匹配缩放适应与旋转`() {
        assertEquals(
            DesktopViewerExtraAction.ZOOM_IN,
            matchViewerExtraShortcut(DesktopShortcutSignal(AwtKeyEvent.VK_EQUALS)),
        )
        assertEquals(
            DesktopViewerExtraAction.ZOOM_OUT,
            matchViewerExtraShortcut(DesktopShortcutSignal(AwtKeyEvent.VK_MINUS)),
        )
        assertEquals(
            DesktopViewerExtraAction.ZOOM_RESET,
            matchViewerExtraShortcut(DesktopShortcutSignal(AwtKeyEvent.VK_0)),
        )
        assertEquals(
            DesktopViewerExtraAction.FIT_SCREEN,
            matchViewerExtraShortcut(DesktopShortcutSignal(AwtKeyEvent.VK_F)),
        )
        assertEquals(
            DesktopViewerExtraAction.ROTATE_OR_MODE,
            matchViewerExtraShortcut(DesktopShortcutSignal(AwtKeyEvent.VK_R)),
        )
        assertNull(matchViewerExtraShortcut(DesktopShortcutSignal(AwtKeyEvent.VK_F, isCtrlOrMeta = true)))
    }

    @Test
    fun `快捷键总线按优先级与后注册优先分发`() {
        val bus = DesktopGlobalShortcutBus()
        val trace = mutableListOf<String>()
        bus.register("shell", priority = 0) {
            trace += "shell"
            true
        }
        bus.register("list", priority = 100) {
            trace += "list"
            false
        }
        bus.register("reader", priority = 300) {
            trace += "reader"
            true
        }

        assertTrue(bus.dispatch(DesktopShortcutSignal(AwtKeyEvent.VK_RIGHT)))
        assertEquals(listOf("reader"), trace)

        trace.clear()
        bus.unregister("reader")
        assertTrue(bus.dispatch(DesktopShortcutSignal(AwtKeyEvent.VK_RIGHT)))
        assertEquals(listOf("list", "shell"), trace)

        bus.unregister("shell")
        bus.unregister("list")
        assertFalse(bus.dispatch(DesktopShortcutSignal(AwtKeyEvent.VK_RIGHT)))
    }

    @Test
    fun `Shift 区间连选支持正向反向与无锚点退化`() {
        val ordered = listOf("a", "b", "c", "d", "e")
        assertEquals(
            setOf("b", "c", "d"),
            computeShiftRangeSelection(ordered, anchorKey = "b", targetKey = "d", currentSelected = setOf("b")),
        )
        assertEquals(
            setOf("b", "c", "d"),
            computeShiftRangeSelection(ordered, anchorKey = "d", targetKey = "b", currentSelected = setOf("d")),
        )
        assertEquals(
            setOf("a", "c"),
            computeShiftRangeSelection(ordered, anchorKey = "missing", targetKey = "c", currentSelected = setOf("a")),
        )
    }
}
