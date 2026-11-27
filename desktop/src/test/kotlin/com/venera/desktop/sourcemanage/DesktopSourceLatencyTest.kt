package com.venera.desktop.sourcemanage

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.venera.desktop.setContentThemed
import com.venera.desktop.ui.DesktopHomeSourceStatusRow
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class DesktopSourceLatencyTest {

    @Test
    fun `selectHomeCompactSourceRows限高4行并优先暴露异常与偏慢站`() {
        val rows = listOf(
            DesktopHomeSourceStatusRow("正常1", "a.com", latencyMs = 120),
            DesktopHomeSourceStatusRow("正常2", "b.com", latencyMs = 150),
            DesktopHomeSourceStatusRow("偏慢站", "c.com", latencyMs = 1400),
            DesktopHomeSourceStatusRow("正常3", "d.com", latencyMs = 200),
            DesktopHomeSourceStatusRow("异常站", "e.com", failed = true),
            DesktopHomeSourceStatusRow("正常4", "f.com", latencyMs = 240),
        )
        val summary = summarizeSourceStatus(rows)
        assertEquals(6, summary.total)
        assertEquals(4, summary.ok)
        assertEquals(1, summary.slow)
        assertEquals(1, summary.bad)

        val (compact, overflow) = selectHomeCompactSourceRows(rows)
        assertEquals(HOME_SOURCE_STATUS_MAX_ROWS, compact.size)
        assertEquals(2, overflow)
        assertEquals("异常站", compact[0].name)
        assertEquals("偏慢站", compact[1].name)
        assertEquals("正常1", compact[2].name)
        assertEquals("正常2", compact[3].name)
    }

    @Test
    fun `filterAndSortLatencyItems正确处理状态筛选排序与未启用开关`() {
        val items = listOf(
            DesktopSourceLatencyItem("a", "A站", "a.com", "内置站点", enabled = true, requiresProxy = false, probing = false, latencyMs = 200, failed = false, canOpenSiteSettings = true),
            DesktopSourceLatencyItem("b", "B站", "b.com", "laincat/vensrc", enabled = true, requiresProxy = true, probing = false, latencyMs = 1300, failed = false, canOpenSiteSettings = true),
            DesktopSourceLatencyItem("c", "C站", "c.com", "内置站点", enabled = false, requiresProxy = false, probing = false, latencyMs = null, failed = false, canOpenSiteSettings = true),
        )
        val onlyEnabled = filterAndSortLatencyItems(items, LatencyFilter.ALL, LatencySort.PRIORITY, includeDisabled = false, query = "")
        assertEquals(listOf("B站", "A站"), onlyEnabled.map { it.name })

        val withDisabled = filterAndSortLatencyItems(items, LatencyFilter.ALL, LatencySort.NAME, includeDisabled = true, query = "c.com")
        assertEquals(listOf("C站"), withDisabled.map { it.name })
    }

    @Test
    fun `DesktopSourceLatencyScreen展示KPI与明细并响应全部重测`() = runComposeUiTest {
        val reprobed = mutableListOf<List<String>>()
        val items = listOf(
            DesktopSourceLatencyItem("goda", "GoDa漫画", "godamh.com", "内置站点", enabled = true, requiresProxy = false, probing = false, latencyMs = 210, failed = false, canOpenSiteSettings = true),
            DesktopSourceLatencyItem("pixiv", "pixiv", "www.pixiv.net", "画廊固定", enabled = true, requiresProxy = true, probing = false, latencyMs = 1100, failed = false, canOpenSiteSettings = false),
            DesktopSourceLatencyItem("kavita", "Kavita", "127.0.0.1:5000", "内置站点", enabled = false, requiresProxy = false, probing = false, latencyMs = null, failed = false, canOpenSiteSettings = true),
        )
        setContentThemed {
            Box(Modifier.fillMaxSize()) {
                DesktopSourceLatencyScreen(
                    items = items,
                    onReprobeHosts = { reprobed += it },
                    onGoSourceManage = {},
                    onBack = {},
                )
            }
        }
        waitForIdle()
        onNodeWithText("站点延迟检测").assertExists()
        onNodeWithText("GoDa漫画").assertExists()
        onNodeWithText("godamh.com").assertExists()
        onAllNodesWithText("Kavita").fetchSemanticsNodes().let { assertEquals(0, it.size) }

        onNodeWithText("全部重测").performClick()
        waitForIdle()
        assertEquals(listOf(listOf("godamh.com", "www.pixiv.net")), reprobed)
    }

    @Test
    fun `apiHostOf跳过脚本更新CDN地址并提取真实站点域名`() {
        val ehentaiSample = """
            class Ehentai extends ComicSource {
                name = "ehentai"
                url = "https://cdn.jsdelivr.net/gh/venera-app/venera-configs@main/ehentai.js"
                settings = { domain: { default: "e-hentai.org" } }
            }
        """.trimIndent()
        assertEquals("e-hentai.org", com.venera.desktop.gallery.data.DesktopSourceNames.apiHostOf(ehentaiSample))

        val copySample = """
            class CopyManga extends ComicSource {
                url = "https://cdn.jsdelivr.net/gh/moonskycake/venera-next@main/copy_manga.js"
                static defaultApiUrl = 'api.manga2026.xyz'
            }
        """.trimIndent()
        assertEquals("api.manga2026.xyz", com.venera.desktop.gallery.data.DesktopSourceNames.apiHostOf(copySample))

        val godaSample = """
            class Goda extends ComicSource {
                url = "https://cdn.jsdelivr.net/gh/venera-app/venera-configs@main/goda.js"
                settings = { domains: { title: "域名", type: "input", default: "godamh.com" } }
            }
        """.trimIndent()
        assertEquals("godamh.com", com.venera.desktop.gallery.data.DesktopSourceNames.apiHostOf(godaSample))

        val dir = java.io.File(com.venera.desktop.gallery.ui.DesktopSourceTree.repoRoot, "desktop/src/main/resources/sources")
        val bundled = dir.listFiles { f -> f.isFile && f.extension == "js" }.orEmpty()
        assertEquals(true, bundled.isNotEmpty())
        bundled.forEach { file ->
            val host = com.venera.desktop.gallery.data.DesktopSourceNames.apiHostOf(file.readText())
            assertEquals("随包源 ${file.name} 错抓成更新仓库域：$host", false, host.contains("jsdelivr.net") || host.contains("githubusercontent.com"))
            assertEquals("随包源 ${file.name} 未提取出真实域名", true, host.isNotBlank())
        }
    }

    @Test
    fun `启动自动测速仅在首次出现时执行且不随回首页定时轮询重测`() {
        val misc = com.venera.desktop.gallery.ui.DesktopSourceTree.codeText(
            com.venera.desktop.gallery.ui.DesktopSourceTree.desktopSourceOf("app/DesktopLargeScreenMiscEffects.kt"),
        )
        assertEquals(true, misc.contains("!homeProbeStamp.containsKey(it)"))
        assertEquals(false, misc.contains("> 60_000L"))
    }
}
