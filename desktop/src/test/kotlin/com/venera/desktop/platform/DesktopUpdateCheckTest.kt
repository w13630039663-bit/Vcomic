package com.venera.desktop.platform

import com.venera.compose.data.platform.JsonKeyValueStore
import com.venera.compose.data.platform.PathProvider
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopUpdateCheckTest {

    @Test
    fun `parseDesktopRelease extracts changelog and prefers exe installer`() {
        val json = """
            {
              "tag_name": "v2.2.0",
              "html_url": "https://github.com/w13630039663-bit/Vcomic/releases/tag/v2.2.0",
              "body": "## 更新内容\n- 优化收藏弹窗\n- 新增一键安装",
              "assets": [
                {
                  "name": "VComic-2.2.0-portable.zip",
                  "size": 102400,
                  "browser_download_url": "https://example.com/VComic.zip"
                },
                {
                  "name": "VComic-2.2.0-setup.exe",
                  "size": 204800,
                  "digest": "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                  "browser_download_url": "https://example.com/VComic-setup.exe"
                }
              ]
            }
        """.trimIndent()

        val parsed = DesktopUpdateCheck.parseDesktopRelease(json)
        assertNotNull(parsed)
        assertEquals("2.2.0", parsed!!.version)
        assertTrue(parsed.changelog.contains("优化收藏弹窗"))
        assertEquals("VComic-2.2.0-setup.exe", parsed.installer?.name)
        assertEquals(204800L, parsed.installer?.size)
        assertEquals(
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            parsed.installer?.sha256,
        )
    }

    @Test
    fun `auto check toggle and 24h throttle persist in store`() {
        val tempRoot = Files.createTempDirectory("vcomic_update_test").toFile()
        try {
            val provider = object : PathProvider {
                override val dataRoot: File = File(tempRoot, "data").apply { mkdirs() }
                override val cacheRoot: File = File(tempRoot, "cache").apply { mkdirs() }
            }
            val store = JsonKeyValueStore("venera_desktop_window", provider)
            assertTrue(DesktopUpdateCheck.isAutoCheckEnabled(store))
            DesktopUpdateCheck.setAutoCheckEnabled(store, false)
            assertFalse(DesktopUpdateCheck.isAutoCheckEnabled(store))

            assertTrue(DesktopUpdateCheck.shouldCheckOnStartup(store))
            DesktopUpdateCheck.markChecked(store)
            assertFalse(DesktopUpdateCheck.shouldCheckOnStartup(store))
        } finally {
            tempRoot.deleteRecursively()
        }
    }
}
