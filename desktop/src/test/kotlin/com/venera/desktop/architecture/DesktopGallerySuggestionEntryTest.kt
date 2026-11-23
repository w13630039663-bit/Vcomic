package com.venera.desktop.architecture

import com.venera.desktop.gallery.ui.DesktopSourceTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 钉住「画廊搜索的标签联想补全第一次有门」。
 *
 * ## 这一族为什么必须有尺
 *
 * `desktopMergeSuggestionLegs` / `desktopSuggestionReading` 两颗纯件从画廊就在，
 * `DesktopSearchSuggestionTest` 也一直绿 —— 而 `desktop/src/main` 里**没有任何一屏**调它们
 *（那一族「有逻辑没有门」，§114/§124/§125/§138 逐批记过同形）。§125 那次把搜索那扇门开上时
 * 补全这格没跟着开，正是因为腿的取数还没接。这一批把腿接上并开屏，这一把尺钉那根线**不许断回去**。
 *
 * ## 四条各挡一种坏写法
 *
 *  ① 屏那三枚形参必传（`suggestCandidates: List<GalleryTagSuggestion>` 等，无默认值 —— 台账尺扫的就是这族）；
 *  ② 屏真**渲**那一排（`if (suggestCandidates.isNotEmpty() || suggestLine != null)` 那道闸 + 逐枚 chip +
 *    点一枚交回 name），而不是收了三枚形参一行都不画；
 *  ③ 壳里那颗 effect 真按 `gallerySearchQuery` 走、真问 `searchTags`、真落两颗既有件（不在屏里重拼判据）；
 *  ④ effect 里 Gelbooru 没配账号那腿**不发**（`availableLegs`），与取结果那趟剔同一腿同口径。
 */
class DesktopGallerySuggestionEntryTest {

    private val screenFile = File(
        DesktopSourceTree.repoRoot,
        "desktop/src/main/kotlin/com/venera/desktop/gallery/ui/DesktopGallerySearchScreen.kt",
    )

    /** 批 2 起下拉浮层与控件族在另一颗成员文件里；成员表显式列举（不是扫目录）。 */
    private val controlsFile = File(
        DesktopSourceTree.repoRoot,
        "desktop/src/main/kotlin/com/venera/desktop/gallery/ui/GallerySearchControls.kt",
    )
    private val shellFile = File(
        DesktopSourceTree.repoRoot,
        "desktop/src/main/kotlin/com/venera/desktop/VeneraDesktopLargeScreen.kt",
    )

    private fun codeOf(f: File): String {
        assertTrue("读不到 ${f.name}，判据失效（不是通过）", f.isFile)
        return DesktopSourceTree.codeText(f)
    }

    @Test
    fun `屏那三枚形参必传`() {
        val screen = codeOf(screenFile) + "`n" + codeOf(controlsFile)
        assertTrue("屏没有 suggestCandidates 那枚必传形参：", screen.contains("suggestCandidates: List<GalleryTagSuggestion>,"))
        assertTrue("屏没有 suggestLine 那枚必传形参：", screen.contains("suggestLine: String?,"))
        assertTrue("屏没有 onPickSuggestion 那枚必传形参：", screen.contains("onPickSuggestion: (String) -> Unit,"))
        // 三枚都不许带默认值（`= emptyList()` / `= null` / `= {}`）—— 台账尺扫 `名字: (…) -> … = 默认值`。
        assertTrue(
            "onPickSuggestion 带了默认值（假开关那一族）：",
            !screen.contains("onPickSuggestion: (String) -> Unit ="),
        )
    }

    @Test
    fun `屏真渲那一排`() {
        val screen = codeOf(screenFile) + "`n" + codeOf(controlsFile)
        assertTrue(
            "那一排没有「空表且无读数就不摆」那道闸：",
            screen.contains("if (suggestCandidates.isNotEmpty() || suggestLine != null)"),
        )
        assertTrue(
            "屏没按 `suggestCandidates.forEach` 逐枚摆 chip：",
            screen.contains("suggestCandidates.forEach"),
        )
        assertTrue(
            "点一枚候选没交回 name（对岸「选一枚补全即填那枚标签」同形）：",
            screen.contains("onPickSuggestion(suggestion.name)"),
        )
    }

    @Test
    fun `壳那颗effect真接上腿与两颗既有件`() {
        val shell = DesktopSourceTree.desktopLargeScreenCode()
        assertTrue("壳没有那颗按输入词走的补全 effect：", shell.contains("LaunchedEffect(gallerySearchQuery, gallerySearchSource, route)"))
        assertTrue("壳没真问 `searchTags`：", shell.contains(".searchTags(nowWord)"))
        assertTrue("壳没落并表复检那颗件：", shell.contains("desktopMergeSuggestionLegs(nowWord"))
        assertTrue("壳没落那句读数那颗件：", shell.contains("desktopSuggestionReading(nowWord"))
        // 补全三枚成品真从壳递进屏。
        assertEquals(
            "壳里递 suggestCandidates 恰一处：",
            1,
            DesktopSourceTree.desktopLargeScreenOccurrences("suggestCandidates = gallerySuggestCandidates,"),
        )
    }

    @Test
    fun `补全腿剔掉没配账号的Gelbooru`() {
        val shell = DesktopSourceTree.desktopLargeScreenCode()
        // 补全那颗 effect 里用的是 availableLegs（与取结果那趟同一判据），而不是 source.sites（不剔那腿）。
        assertTrue(
            "补全没走 `availableLegs`（Gelbooru 没配账号那腿会白发一笔注定 401 的补全）：",
            shell.contains("source.availableLegs(desktopGelbooruConfigured(ports))"),
        )
    }
}
