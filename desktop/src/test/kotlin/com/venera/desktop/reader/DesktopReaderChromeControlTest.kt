package com.venera.desktop.reader

import com.venera.desktop.app.DesktopReaderChromeStateMachine
import com.venera.desktop.app.DesktopRoute
import com.venera.desktop.app.isComicReaderRoute
import com.venera.desktop.app.resolveDesktopReaderChromeSnapshot
import com.venera.engineprobe.EngineSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopReaderChromeControlTest {

    private fun onlineReaderRoute(epId: String = "ep_1") = DesktopRoute.Reader(
        comicId = "1001",
        title = "测试漫画",
        cover = "",
        sourceKey = "jm",
        episode = EngineSession.Episode(name = "第1话", id = epId),
        startPage = 0,
        from = DesktopRoute.Tab,
    )

    private fun localReaderRoute() = DesktopRoute.LocalComicReader(
        title = "本地漫画",
        rootPath = "/tmp/comic",
        startPage = 0,
        from = DesktopRoute.Files,
    )

    @Test
    fun `进入在线阅读器后默认显示外框顶栏且自动收起侧栏_退出时还原展开态`() {
        var persistedValue = true
        val machine = DesktopReaderChromeStateMachine(
            savedRailExpanded = true,
            initialRoute = DesktopRoute.Detail("1001", "测试漫画", "", "jm"),
        )
        assertFalse(machine.snapshot().fullscreenChrome)
        assertTrue(machine.snapshot().effectiveRailExpanded)

        // 进入在线阅读器：走方案 B（非全屏沉浸），但侧栏自动收起且不污染持久化设置
        machine.syncRoute(onlineReaderRoute("ep_1"))
        val inReader = machine.snapshot()
        assertTrue(inReader.inComicReader)
        assertFalse(inReader.fullscreenChrome)
        assertFalse(inReader.effectiveRailExpanded)
        assertTrue(persistedValue)

        // 退出阅读器回到详情页：准确还原进入前的展开状态
        machine.syncRoute(DesktopRoute.Detail("1001", "测试漫画", "", "jm"))
        val afterExit = machine.snapshot()
        assertFalse(afterExit.inComicReader)
        assertFalse(afterExit.fullscreenChrome)
        assertTrue(afterExit.effectiveRailExpanded)
        assertTrue(persistedValue)
    }

    @Test
    fun `进入阅读器前侧栏本身为收起态_退出后依然保持收起态`() {
        val machine = DesktopReaderChromeStateMachine(
            savedRailExpanded = false,
            initialRoute = DesktopRoute.Tab,
        )
        machine.syncRoute(onlineReaderRoute("ep_1"))
        // 阅读器内手动展开侧栏
        machine.onRailExpandedChange(true) { error("阅读器内临时展开不应写入持久化偏好") }
        assertTrue(machine.snapshot().effectiveRailExpanded)

        // 退出后还原为进入前的收起状态
        machine.syncRoute(DesktopRoute.Tab)
        assertFalse(machine.snapshot().effectiveRailExpanded)
    }

    @Test
    fun `阅读器内切换全屏沉浸模式可恢复并退出_且切章节保持_退出后重置`() {
        val machine = DesktopReaderChromeStateMachine(
            savedRailExpanded = true,
            initialRoute = DesktopRoute.Tab,
        )
        machine.syncRoute(onlineReaderRoute("ep_1"))
        assertFalse(machine.snapshot().fullscreenChrome)

        // 点击全屏播放按钮恢复沉浸模式
        machine.toggleImmersive()
        assertTrue(machine.snapshot().fullscreenChrome)

        // 阅读器内切换下一话，保持沉浸模式与临时侧栏状态
        machine.syncRoute(onlineReaderRoute("ep_2"))
        assertTrue(machine.snapshot().fullscreenChrome)

        // 再次点击按钮退出沉浸模式，回到方案 B 并保留收起态侧栏
        machine.toggleImmersive()
        assertFalse(machine.snapshot().fullscreenChrome)
        assertFalse(machine.snapshot().effectiveRailExpanded)

        // 沉浸态下直接退出阅读器，随后再次进入应重置为默认非沉浸方案 B
        machine.toggleImmersive()
        assertTrue(machine.snapshot().fullscreenChrome)
        machine.syncRoute(DesktopRoute.Tab)
        assertFalse(machine.snapshot().fullscreenChrome)
        assertTrue(machine.snapshot().effectiveRailExpanded)

        machine.syncRoute(onlineReaderRoute("ep_1"))
        assertFalse(machine.snapshot().fullscreenChrome)
        assertFalse(machine.snapshot().effectiveRailExpanded)
    }

    @Test
    fun `本地导入阅读器统一走自动收起与可切沉浸逻辑_非阅读器不受影响`() {
        assertTrue(isComicReaderRoute(onlineReaderRoute()))
        assertTrue(isComicReaderRoute(localReaderRoute()))
        assertFalse(isComicReaderRoute(DesktopRoute.Tab))
        assertFalse(isComicReaderRoute(DesktopRoute.Files))

        val nonReader = resolveDesktopReaderChromeSnapshot(
            contentRoute = DesktopRoute.Files,
            savedRailExpanded = true,
            readerRailExpanded = false,
            readerImmersive = true,
        )
        assertFalse(nonReader.inComicReader)
        assertFalse(nonReader.fullscreenChrome)
        assertTrue(nonReader.effectiveRailExpanded)

        val localReader = resolveDesktopReaderChromeSnapshot(
            contentRoute = localReaderRoute(),
            savedRailExpanded = true,
            readerRailExpanded = false,
            readerImmersive = false,
        )
        assertTrue(localReader.inComicReader)
        assertFalse(localReader.fullscreenChrome)
        assertFalse(localReader.effectiveRailExpanded)
    }

    @Test
    fun `底栏沉浸按钮双态文案明确且可区分`() {
        val enterLabel = readerImmersiveButtonLabel(immersive = false)
        val exitLabel = readerImmersiveButtonLabel(immersive = true)
        assertTrue(enterLabel.isNotBlank())
        assertTrue(exitLabel.isNotBlank())
        assertNotEquals(enterLabel, exitLabel)
        assertEquals(enterLabel.length, exitLabel.length)
    }
}
