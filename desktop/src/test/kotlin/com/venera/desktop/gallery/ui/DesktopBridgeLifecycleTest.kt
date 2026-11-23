package com.venera.desktop.gallery.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P1-D 的源码守护：桥里的空闲计时器必须挂在自持作用域上，
 * Edge 网页登录的调试端口必须交给系统分配。
 *
 * 这两条都是"形状"判据：真跑一遍要拉起 Edge/WebView2 窗口，代价高且不可判定；
 * 但退回旧写法会立刻被这里拦下。
 */
class DesktopBridgeLifecycleTest {

    private val ascii2d = DesktopSourceTree.desktopSourceOf("gallery/data/DesktopAscii2dBridge.kt")
    private val konachan = DesktopSourceTree.desktopSourceOf("gallery/data/DesktopKonachanBridge.kt")
    private val edgeLogin = DesktopSourceTree.desktopSourceOf("sourcemanage/DesktopEdgeWebLogin.kt")

    @Test
    fun `空闲计时器不寄生在 GlobalScope`() {
        listOf(ascii2d, konachan).forEach { file ->
            val text = file.readText()
            assertFalse("${file.name} 仍直接启动 GlobalScope 协程", "GlobalScope." in text)
            assertTrue("${file.name} 应自持 idleScope", "idleScope" in text)
        }
    }

    @Test
    fun `Edge 登录端口交给系统分配`() {
        val text = edgeLogin.readText()
        assertFalse("端口仍在 19200 段手算", "19200 +" in text)
        assertTrue("应由 ServerSocket(0) 取空闲端口", "ServerSocket(0)" in text)
    }
}
