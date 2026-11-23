package com.venera.desktop.architecture

import com.venera.desktop.gallery.ui.DesktopSourceTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 钉住「搜索那一屏的源选择第一次有真后果」（对齐安卓源 chips）。
 *
 * ## 这一族为什么必须有尺
 *
 * 桌面搜索从前只搜硬编的那一颗默认源（`DESKTOP_EXPLORE_SOURCE`），而「换成读偏好」这一格
 * 在 `DesktopRoute.kt:321` 那颗常量的注释里记了好几批。这一批补的是**屏上挑源**那一形：
 * 排 chip、按下去换 `searchSourceKey`、发过车就原词重搜、详情按挑中的那颗装载。
 * 与 §114/§124/§138 那一族同形 —— 判据只扫算式扫不出"挑了源而取数仍问默认那颗"，所以钉接线。
 *
 * ## 四条各挡一种坏写法
 *
 *  ① 屏那三枚形参必传（sourceLabel / sourceOptions / onPickSource），空表整颗不摆那一排（清单没到货）；
 * ② 取数那一趟**真的吃挑中的键**（默认`，不许续页漂回默认）；
 *  ③ 详情落点递的是挑中的那颗键（从前硬编默认，换源搜出来的卡点进去会拿错源装载）；
 *  ④ 源清单那颗 effect 的早退条件加了 SEARCH 那一支（否则进搜索屏 `sourceOptions` 恒空 ⇒ 那一排永不摆）。
 */
class DesktopSearchSourceEntryTest {

    private val screenFile = File(
        DesktopSourceTree.repoRoot,
        "desktop/src/main/kotlin/com/venera/desktop/search/DesktopSearchScreen.kt",
    )
    private val shellFile = File(
        DesktopSourceTree.repoRoot,
        "desktop/src/main/kotlin/com/venera/desktop/VeneraDesktopLargeScreen.kt",
    )
    private val assemblyFile = File(
        DesktopSourceTree.repoRoot,
        "desktop/src/main/kotlin/com/venera/desktop/VeneraDesktop.kt",
    )

    private val pickerFile = File(
        DesktopSourceTree.repoRoot,
        "desktop/src/main/kotlin/com/venera/desktop/search/DesktopSearchSourcePicker.kt",
    )
    private val discoverFile = File(
        DesktopSourceTree.repoRoot,
        "desktop/src/main/kotlin/com/venera/desktop/search/DesktopDiscoverScreen.kt",
    )

    private fun codeOf(f: File): String {
        assertTrue("读不到 ${f.name}，判据失效（不是通过）", f.isFile)
        return DesktopSourceTree.codeText(f)
    }

    @Test
    fun `屏那三枚形参必传而空表不摆那一排`() {
        val screen = codeOf(screenFile)
        val picker = codeOf(pickerFile)
        val discover = codeOf(discoverFile)
        assertTrue("屏没有 sourceLabel 那枚必传形参：", screen.contains("sourceLabel: String,"))
        assertTrue(
            "屏没有 sourceOptions 那枚必传形参：",
            screen.contains("sourceOptions: List<DesktopSearchSourceOption>,"),
        )
        assertTrue("屏没有 onPickSource 那枚必传形参：", screen.contains("onPickSource: (String) -> Unit,"))
        // 空表 ⇒ 整颗不摆（摆一排空的源选择器 = 假把手）。
        assertTrue(
            "源选择器没有「空表不摆」那道 `if (sourceOptions.isNotEmpty())`：",
            screen.contains("if (sourceOptions.isNotEmpty())") && discover.contains("if (sourceOptions.isNotEmpty())"),
        )
        assertTrue(
            "选择器内部没有空表早退：",
            picker.contains("if (sourceOptions.isEmpty()) return"),
        )
        // 当前那颗单源按下去不动（不重发同一颗）。
        assertTrue(
            "单源切换前没有「键不等才发车」那一判：",
            picker.contains("if (key != activeSingle) onSelectSingle(key)"),
        )
    }

    @Test
    fun `取数那两趟都吃挑中的键`() {
        val lines = DesktopSourceTree.desktopLargeScreenLines()
        // 与续页两处：doSearch(...) 那一行里带 `searchSourceKey ?: DESKTOP_EXPLORE_SOURCE`。
        // ⚠️ 按"同一行两枚都在"数，不用跨括号的正则：续页那一行的实参里有 `searchQuery.trim()`，
        // `[^)]*` 会在第一个右括号截断而漏数（第一版就是这么假数成 1 的）。
        val wired = lines.count {
            it.contains("doSearch(") && it.contains("searchSourceKey ?: DESKTOP_EXPLORE_SOURCE")
        }
        assertEquals(
            "壳里「挑中的键喂给取数」那一式必须两处（第一轮 + 续页），少一处就是那一路漂回默认源：",
            2,
            wired,
        )
    }

    @Test
    fun `详情落点递挑中的那颗键`() {
        val shell = DesktopSourceTree.desktopLargeScreenCode()
        // 搜索屏那一支里，进详情的 sourceKey 是 currentSearchKey 而不是硬编默认。
        assertTrue(
            "搜索屏进详情没把挑中的键递下去（仍硬编默认 ⇒ 换源搜的卡拿错源装载）：",
            shell.contains("sourceKey = currentSearchKey,"),
        )
        assertTrue(
            "装配点装载那跳没吃形参 sourceKey（仍硬编默认）：",
            codeOf(assemblyFile).contains("e.load(sourceKey)"),
        )
    }

    /** 搜索两屏走全宽档：不设 1680 上限（漫画搜索页全宽）。 */
    @Test
    fun `搜索两屏走全宽档`() {
        listOf(screenFile, File(DesktopSourceTree.repoRoot, "desktop/src/main/kotlin/com/venera/desktop/search/DesktopDiscoverScreen.kt"))
            .forEach { f ->
                val code = codeOf(f)
                assertTrue(
                    "${f.name} 没走全宽档（Browse 会把搜索页限在 1680）：",
                    code.contains("desktopPageMaxWidth(DesktopPageWidth.Full"),
                )
            }
    }

    @Test
    fun `源清单 effect 认搜索那一格`() {
        val shell = DesktopSourceTree.desktopLargeScreenCode()
        assertTrue(
            "读源清单那颗 effect 的早退条件没有 SEARCH 那一支 ⇒ 进搜索屏清单恒空、那一排永不摆：",
            shell.contains("currentTab != VeneraNavTab.SEARCH &&"),
        )
    }
}
