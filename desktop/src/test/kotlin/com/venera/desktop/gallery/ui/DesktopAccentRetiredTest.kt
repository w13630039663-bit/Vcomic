package com.venera.desktop.gallery.ui

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 撤强调色档的判据（阶段 0 · 主人明令「只保留深浅模式」）。
 *
 * ## 钉什么
 *
 * 撤档不是删几个文件就完事——那类「半残开关」最擅长回潮：某次改 UI 顺手加回一档强调色、
 * 或新屏直接写死一档色值，编译过、单测绿，主人切浅色时才发现又只有半屏跟随。所以：
 *
 * 1. **档位零回潮**：桌面 main 里不许再出现 `DesktopAccentMode` / `pref_desktop_accent_mode`
 *    —— 物理删除之后，这两个名字只该活在 git 历史里（扫代码行，注释里的历史叙述不算回潮）；
 * 2. **固定主色单源**：`DesktopFixedAccent` 全仓恰一处定义（`SettingsGlass.kt`，色值之家），
 *    主题薄桥（`DesktopThemeBridge.kt`）必须从它取种子 —— 桥自身不出色值
 *    （`DesktopThemeLayerTest` ③ 的口径），撤档后「色板出处只有一处」继续成立。
 *
 * 原计划的「GalleryTokens 语义槽必须来自 VeneraTokens」对**深色档不成立**：深色值是
 * 基准页视觉冻结（主人屏上原样），与 MaterialKolor 派生值刻意不同。撤档判据以本文件
 * 这两条为准（阶段 0 登记的口径修正）。
 */
class DesktopAccentRetiredTest {

    private fun mainLines(): List<Pair<String, String>> =
        DesktopSourceTree.desktopMainSources().flatMap { file ->
            DesktopSourceTree.codeLinesOf(file).map { line -> file.name to line }
        }

    /** 强调色档零回潮：类名与偏好键不得再现于桌面 main 代码行。 */
    @Test
    fun accentModeStaysRetired() {
        val hits = mainLines().filter { (_, line) ->
            line.contains("DesktopAccentMode") || line.contains("pref_desktop_accent_mode")
        }
        assertTrue(
            "强调色档已撤（2026-11-05 主人明令），以下行回潮了强调色档：\n" +
                hits.joinToString("\n") { (file, line) -> "  $file: ${line.trim()}" },
            hits.isEmpty(),
        )
    }

    /** 固定主色单源：DesktopFixedAccent 恰一处定义，且薄桥取种子于它。 */
    @Test
    fun fixedAccentIsSingleSource() {
        val lines = mainLines()
        val defining = lines.filter { (_, line) -> line.contains("val DesktopFixedAccent") }
        assertTrue(
            "DesktopFixedAccent 应恰在色值之家（SettingsGlass.kt）定义一处，现量 ${defining.size} 处：\n" +
                defining.joinToString("\n") { (file, line) -> "  $file: ${line.trim()}" },
            defining.size == 1,
        )
        val bridge = lines
            .filter { (file, _) -> file == "DesktopThemeBridge.kt" }
            .filter { (_, line) -> line.contains("DesktopFixedAccent") }
        assertTrue(
            "主题薄桥必须从 DesktopFixedAccent 取种子（撤档后全窗主色单源），桥内零引用",
            bridge.isNotEmpty(),
        )
    }
}
