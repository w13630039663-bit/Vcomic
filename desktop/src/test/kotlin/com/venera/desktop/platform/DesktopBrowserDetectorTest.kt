package com.venera.desktop.platform

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DesktopBrowserDetectorTest {

    @Test fun `环境变量 VENERA_BROWSER_PATH 存在且有效时优先命中`() {
        val custom = File("/opt/custom/my-browser")
        val hit = DesktopBrowserDetector.findBrowserExecutable(
            envProvider = { if (it == DesktopBrowserDetector.ENV_CUSTOM_BROWSER_PATH) custom.path else null },
            fileChecker = { it.path == custom.path },
        )
        assertNotNull(hit)
        assertEquals(custom.path, hit!!.path)
    }

    @Test fun `Linux 环境下按优先级从 PATH 命中第一个存在的浏览器`() {
        val chrome = File("/usr/bin/google-chrome-stable")
        val chromium = File("/usr/bin/chromium")
        val existingFiles = setOf(chrome.path, chromium.path)

        val hit = DesktopBrowserDetector.findBrowserExecutable(
            envProvider = { if (it == "PATH") "/usr/bin" else null },
            osName = "Linux",
            fileChecker = { it.path in existingFiles },
            pathLookup = { cmd, _, _ ->
                if (cmd == "google-chrome-stable") chrome else if (cmd == "chromium") chromium else null
            },
        )
        assertNotNull(hit)
        assertEquals(chrome.path, hit!!.path)
    }

    @Test fun `Linux 环境下 PATH 无命令时回退到常见绝对路径`() {
        val brave = File("/usr/bin/brave-browser")
        val hit = DesktopBrowserDetector.findBrowserExecutable(
            envProvider = { null },
            osName = "Linux",
            fileChecker = { it.path == brave.path },
            pathLookup = { _, _, _ -> null },
        )
        assertNotNull(hit)
        assertEquals(brave.path, hit!!.path)
    }

    @Test fun `各平台均未安装任何浏览器时返回 null`() {
        val hit = DesktopBrowserDetector.findBrowserExecutable(
            envProvider = { null },
            osName = "Linux",
            fileChecker = { false },
            pathLookup = { _, _, _ -> null },
        )
        assertNull(hit)
    }
}
