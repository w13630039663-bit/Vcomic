package com.venera.desktop.gallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopDirChooserLinuxTest {

    @Test fun `Linux 环境下 zenity 存在并返回有效路径时优先命中`() {
        var calledCmd: List<String>? = null
        val hit = DesktopDirChooser.pickLinuxNativeOrSwing(
            title = "选择下载目录",
            initialDir = "/home/user/Downloads",
            commandRunner = { cmd ->
                calledCmd = cmd
                if (cmd.first() == "zenity") "/home/user/Pictures/Manga" else null
            },
        )
        assertEquals("/home/user/Pictures/Manga", hit)
        assertTrue(calledCmd!!.first() == "zenity")
        assertTrue(calledCmd!!.any { it.contains("选择下载目录") })
    }

    @Test fun `Linux 环境下无 zenity 但有 kdialog 时命中 kdialog`() {
        val hit = DesktopDirChooser.pickLinuxNativeOrSwing(
            title = "选择保存目录",
            initialDir = "/home/user/Pictures",
            commandRunner = { cmd ->
                if (cmd.first() == "kdialog") "/home/user/Pictures/Saved" else null
            },
        )
        assertEquals("/home/user/Pictures/Saved", hit)
    }

    @Test fun `Linux 环境下 zenity 与 kdialog 均不可用时能正常调用到兜底逻辑`() {
        var fallbackCalled = false
        val hit = DesktopDirChooser.pickLinuxNativeOrSwing(
            title = "测试",
            initialDir = null,
            commandRunner = { null },
            swingFallback = { _, _ ->
                fallbackCalled = true
                "/fallback/path"
            },
        )
        assertTrue(fallbackCalled)
        assertEquals("/fallback/path", hit)
    }
}
