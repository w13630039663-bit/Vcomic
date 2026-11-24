package com.venera.desktop.gallery.ui

import com.venera.desktop.app.VeneraDesktopLargeScreen
import com.venera.desktop.ui.DailyGalleryUiState
import com.venera.desktop.ui.DesktopHomePane
import com.venera.desktop.ui.DesktopNotWiredPane
import com.venera.desktop.ui.DesktopWallScaffold
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 钉住「桌面首页用的是安卓那一批零件」——用户第二次抓到的那一颗。
 *
 * ## 两次越界的形状
 *
 * 1. 第一版：把**画廊图片墙**摆在首页位置（五个 tab 四个同形）→ 用户：
 *    「你这个首页怎么和安卓端的完全不一样？」
 * 2. 第二版（本条钉的这一颗）：改成 `DesktopNotWiredPane` 如实报未接 —— 诚实了，
 *    但首页**仍然是桌面自己画的**，一个零件都没复用。
 *
 * 现在的形状：`DesktopHomePane` 用 `feature/HomeHomeWidgets.kt`（与安卓**同一份源码**）
 * 摆出统计格 / 源状态 / 分区头 —— 结构是安卓的，数据口子是待接的。
 *
 * ## 判据
 *
 * 扫 `desktop/.../DesktopHomePane.kt` 的**代码行**（非注释）：
 *  - 必须 import 至少 4 个 `com.venera.compose.feature.*` 的共享零件；
 *  - 不许出现 `DesktopWallScaffold` / `GalleryWallPane` —— 那是**画廊那面墙本身的脚手架**，
 * 出现在首页就是「拿图片墙冒充首页」（用户抓到的那一颗）。
 *
 * ## ⚠️翻正的一格：`VeneraCover` 从禁列撤下
 *
 * 从前这一条把 `VeneraCover(` 一起禁了，那是**过宽**：它当时假设首页只有统计格与源状态。
 * 而安卓新版首页（`HomeHomeWidgets.kt` 自己就 import 了 `VeneraCover`）本来就有**带封面的分区** ——
 * 「推荐漫画」「画廊每日热门」「关注画师」三格全画封面。桌面 142 批接的正是那四格，
 * 若继续禁 `VeneraCover`，等于把"首页要和安卓一样"与"首页不许画封面"钉成互相矛盾的两条。
 * 墙脚手架（`DesktopWallScaffold`/`GalleryWallPane`）仍在禁列：**冒充首页的是那面墙，不是"画封面"这件事**。
 */
class DesktopHomeUsesSharedWidgetsTest {

    private fun homePane(): File {
        val f = File(
            DesktopSourceTree.repoRoot,
            "desktop/src/main/kotlin/com/venera/desktop/ui/DesktopHomePane.kt",
        )
        assertTrue("读不到桌面首页文件，判据失效（不是通过）：${f.path}", f.isFile)
        return f
    }

    /** 非注释的代码行。 */
    private fun codeLines(file: File): List<String> =
        file.readText().lines().filterNot { ln ->
            val s = ln.trim()
            s.startsWith("//") || s.startsWith("*") || s.startsWith("/*")
        }

    @Test
    fun `首页用的是安卓那一批共享零件 不是自己画的`() {
        val imports = codeLines(homePane()).filter { it.trim().startsWith("import com.venera.compose.feature.") }
        assertTrue(
            "桌面首页一个安卓共享零件都没 import —— 那就是自己画了一份。实际 import：" +
                codeLines(homePane()).filter { it.trim().startsWith("import ") }.joinToString("\n  "),
            imports.size >= 4,
        )
        // 统计大卡退役，数字改由 Hero 玻璃胶囊
        // 直排 —— `StatCell`/`StatDivider` 从"必须引用"名单撤下（安卓侧继续用，不受影响）。
        // 现在最能说明「零件与安卓同源」的是分区头/书脊/源状态/健康度这四颗，少一颗 = 有人重画。
        val must = listOf("HomeTrackHeader", "HomeBookSpines", "SourceStatusRowHome", "healthOf")
        val missing = must.filter { sym -> imports.none { it.endsWith(".$sym") } }
        assertTrue("首页缺这几颗共享零件（多半是重画了）：$missing", missing.isEmpty())
    }

    @Test
    fun `首页不许再出现画廊与封面墙的零件`() {
        val bad = codeLines(homePane()).filter {
            it.contains("DesktopWallScaffold") || it.contains("GalleryWallPane")
        }
        assertTrue(
            "桌面首页里出现了图片墙的零件：$bad —— 那正是 2026-10-20 用户抓到的那一颗" +
                "（「你这个首页怎么和安卓端的完全不一样？」）",
            bad.isEmpty(),
        )
    }

    @Test
    fun `共享零件文件本身零 Android 导入`() {
        val f = File(
            DesktopSourceTree.repoRoot,
            "desktop/src/main/kotlin/com/venera/compose/feature/HomeHomeWidgets.kt",
        )
        assertTrue("读不到 HomeHomeWidgets.kt：${f.path}", f.isFile)
        val offenders = codeLines(f).filter {
            it.trim().startsWith("import android.") ||
                it.contains("LocalContext") ||
                it.contains("viewModel(") ||
                it.contains("ComicSourceManager")
        }
        assertTrue(
            "HomeHomeWidgets.kt 又被平台耦合了（它就进不了桌面编译面了）：$offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    fun `壳把首页四格的真数据递进了屏`() {
        // 四格的货必须由壳递进来（屏不自己取数）。
        // 扫壳里 HOME 那一支的四枚具名实参 —— 少一枚就是那一格又回到了"摆着但没货"。
        val shell = File(
            DesktopSourceTree.repoRoot,
            "desktop/src/main/kotlin/com/venera/desktop/app/VeneraDesktopLargeScreen.kt",
        )
        assertTrue("读不到壳文件，判据失效（不是通过）：${shell.path}", shell.isFile)
        val lines = DesktopSourceTree.desktopLargeScreenLines()
        val missing = listOf(
            // 每日热门使用独立 DailyGalleryUiState holder 并按栏头档位选腿，避免切档不换数。
            "val dailyCards = if (dailyGallery.homePixivPick) dailyGallery.homePixivCards else dailyGallery.homeCards",
            "daily = dailyCards,",
            "followedArtists = homeArtists,",
            "recommendComics =",
            "localComicCount = localComicScan?.comics?.size,",
        ).filter { needle -> lines.none { it.contains(needle) } }
        assertTrue("壳递进首页四格的实参缺了：$missing", missing.isEmpty())
    }
}
