package com.venera.desktop.architecture

import com.venera.desktop.gallery.ui.DesktopSourceTree
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「贴边画框栏」搬进桌面的钉子尺。
 *
 * ## 这一族为什么必须有尺
 *
 * 桌面大屏的侧栏**不是桌面自持件** —— 它就是安卓共享面 `components/VeneraSideBar.kt`
 * （`desktop/build.gradle.kts:118` 把整颗 `components/` 挂进桌面 srcDir，壳里
 * `VeneraDesktopLargeScreen.kt` 直连调用）。所以"搬没搬过来"这件事的真相写在 `app/` 那侧的
 * 文本里，而 `app/` 归安卓那条线所有 —— 那一侧将来任何一次回退、任何一次对 `71cc3eaa`
 * 的改写，都会**静默地**把桌面刚拿到的展开/折叠画框栏换回旧的那一版（编译照过、屏照样渲得出东西，
 * 只是那颗按钮没了）。色板那族教训（§140）说过的话这里一字不改：**换形状这件事只有源码文本拦得住**。
 *
 * ## 五条各挡一种坏写法
 *
 *  ① 侧栏本体是 71cc3eaa 那一版：有 `userExpanded` 那枚手动展开/折叠状态、
 *     图标走 `AnimatedNavIcon`（弹跳微动效），不是旧版那个 `mode == Expanded` 才画题名的死侧栏；
 *  ② 新依赖 `AnimatedNavIcon.kt` 真在编译面里（它不住就不止是回退，是**编不过**——那要红得早，
 *     而这条把"文件在不在"先钉明白，红得早也好读）；
 * ③ tab 顺序与题名跟安卓那份一致（首页 ➔ 搜索 ➔ 画廊 ➔ 收藏 ➔ 探索）—— "跟着改"，
 *     枚举顺序是单一真相（`VeneraFloatingNavBar.kt` 里 `VeneraNavTab.entries`），旧版顺序
 *     （HOME/FAVORITES/SEARCH/…）回来就是漂回去；
 *  ④ 桌面壳里**不许**出现 `VeneraDockedNavBar` —— 那颗是安卓窄窗（Compact）的底栏形状，
 * 宽窗在两端都走左侧栏；把它接进桌面就是第二条导航路，明确不要。
 * ⑤补：浅皮轨不许把 `downloadActivity` 递 `null` —— 那一轨展开/折叠两态
 *     都以「非空」为渲染条件，递 null 会把「下载与本地」整枚门静默吞掉（编译照过、路由还在，
 *     只是按钮没了，正是本族开头说的那类"只有源码文本拦得住"的坏写法）。
 */
class DesktopNavRailPortTest {

    /** ① 侧栏本体是搬过来的那一版。 */
    @Test fun 侧栏吃的是可展开折叠的那一版而不是旧死侧栏() {
        val sideBar = DesktopSourceTree.codeText(DesktopSourceTree.androidSource("components/VeneraSideBar.kt"))
        assertTrue("侧栏里没有 `userExpanded`（展开/折叠那颗按钮的状态）—— 71cc3eaa 那一版没在：", sideBar.contains("userExpanded"))
        assertTrue("侧栏里没有 `AnimatedNavIcon(`（弹跳微动效图标）：", sideBar.contains("AnimatedNavIcon("))
        // 旧版形状的反面对照：旧侧栏是 `mode == Expanded` 的死档 + `sideBarWidthFor(mode)`，搬来后不再是。
        assertTrue("侧栏还按 `sideBarWidthFor(mode)` 定宽 = 回退成旧死侧栏：", sideBar.contains("sideBarWidthFor").not())
    }

    /** ② 新依赖在编译面里。 */
    @Test fun 弹跳图标那颗新依赖真在components里() {
        val icon = File(DesktopSourceTree.repoRoot, "desktop/src/main/kotlin/com/venera/compose/components/AnimatedNavIcon.kt")
        assertTrue("读不到 AnimatedNavIcon.kt —— 侧栏 import 它，没了这颗编译就炸，位置要钉：", icon.isFile)
        assertTrue("那颗件里没有 `fun AnimatedNavIcon(`：", icon.readText().contains("fun AnimatedNavIcon("))
    }

    /** ③ tab 顺序与题名跟安卓那一份（枚举 entries 是单一真相）。 */
    @Test fun tab顺序与题名跟着71cc3eaa那一份() {
        val floating = DesktopSourceTree.codeText(DesktopSourceTree.androidSource("components/VeneraFloatingNavBar.kt"))
        val block = floating.substringAfter("enum class VeneraNavTab")
            .substringBefore("}")
        // `(?m)`：多行文本里 `^` 默认只认串头，不开多行模式就只匹配到第一行（尺子首跑就是这么红的）。
        val order = Regex("""(?m)^\s*([A-Z_]+)\(""").findAll(block).map { it.groupValues[1] }.toList()
        assertEquals(
            "VeneraNavTab.entries 的顺序就是底栏/侧栏顺序（单一真相）。71cc3eaa 改判后应为" +
                " 首页➔搜索➔画廊➔收藏➔探索，实际读到：$order",
            listOf("HOME", "SEARCH", "GALLERY", "FAVORITES", "EXPLORE"),
            order,
        )
        assertTrue(
            "GALLERY 那一档的题名应是「画廊」（UI-1 修正了旧版的「图廊」错字）：",
            block.contains("GALLERY(\"画廊\""),
        )
    }

    /** ④ 桌面只有一条导航路：宽窗侧栏，不接安卓那颗底栏。 */
    @Test fun 桌面壳里不许出现第二导航路docked底栏() {
        val shell = DesktopSourceTree.desktopLargeScreenCode()
        assertTrue("桌面壳里出现 `VeneraDockedNavBar(` —— 那颗是安卓窄窗底栏形状，宽窗两端都走侧栏；接进来就是第二条导航路：", shell.contains("VeneraDockedNavBar(").not())
        // 反向自证：壳确实直连共享侧栏（①-③ 的前提；前提没了这三条就成了空转）。
        assertTrue("桌面壳不再调用共享 `VeneraSideBar(` —— 上面三条的前提失效，尺子要跟着搬走才许撤这一条：", shell.contains("VeneraSideBar("))
    }

    /** ⑤ 两皮共用同一颗下载状态条，浅皮轨不许再把入口递空。 */
    @Test fun 浅皮轨不许把下载与本地入口递空() {
        val shell = DesktopSourceTree.desktopLargeScreenCode()
        assertTrue(
            "壳里又出现 `downloadActivity = null` —— VeneraLightRailSkin 两态都以 " +
                "`downloadActivity != null` 为渲染条件，递 null 会把「下载与本地」整枚门静默吞掉：",
            shell.contains("downloadActivity = null").not(),
        )
        assertEquals(
            "经典皮与浅皮轨必须共用同一颗 `railDownloadActivity`（两处各算一份、或某一处漏递都会再漂）：",
            2,
            Regex("downloadActivity = railDownloadActivity").findAll(shell).count(),
        )
        assertTrue(
            "共享那一颗得真把空闲态「下载与本地」摆出来（它是导航门，不只是 RUNNING 状态条）：",
            shell.contains("title = \"下载与本地\""),
        )
    }
}
