package com.venera.desktop.architecture

import com.venera.desktop.app.DesktopRoute
import com.venera.desktop.app.VeneraDesktopLargeScreen
import com.venera.desktop.gallery.ui.DesktopSourceTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 钉住「源管理那一屏真有一个入口，而且进去出得来」—— 进度清单 §2 那一格「没有入口 ⇒ 这屏打不开」。
 *
 * ## 缺口长什么样
 *
 * 那屏 256 行、启停与两档守卫设置都接上了（H4 靠它闭合），而**全仓没有一处 UI 会切到
 * `DesktopRoute.SourceManage`**：路由分了、屏渲了，可没人按得到它。清单原话是
 * 「属于 [值]（补一个入口很便宜），不是 [术]」—— 补完就把这三件事各钉一条：
 *
 *  ① 有一处把路由切过去（不是一句注释里提到那个名字）；
 *  ② 切过去之前那枚按钮真的接到屏上（`onOpenSourceManage` 是必传参数，不是带默认值的假开关）；
 *  ③ 那一屏有 `onBack` 且切回 `Tab`（入口接上前它根本没有返回，是单行道）。
 *
 * ## 为什么是源码尺而不是行为尺
 *
 * 离屏 `runComposeUiTest` 够不到壳里那颗 `when (route)`（那要起整只壳 + 真引擎 + 真库）。
 * 按不切得到路由由这里钉；那枚按钮按下落得到回调由
 * `DesktopNewScreensSemanticsTest.探索屏那枚源管理入口按一次恰好落一次` 钉。
 * 两把合起来才是"用户真进得去"。
 */
class DesktopSourceManageEntryTest {

    private val shell = DesktopSourceTree.desktopLargeScreenCode()
    private val explore = DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("explore/DesktopExploreScreen.kt"))
    private val manage = DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("sourcemanage/DesktopSourceManageScreen.kt"))

    @Test
    fun 有一处真把路由切到源管理() {
        // 仅扫描代码行：route = DesktopRoute.SourceManage 含入口与返回共 8 处，按回调名区分入口与返回路径。
        val cuts = shell.lines().filter { Regex("""(?:route\s*=|onRouteChange\()\s*DesktopRoute\.SourceManage\b""").containsMatchIn(it) }
        assertEquals(
            "切进源管理路由的赋值总数变了（口径：5 处入口（3 枚 onGoSourceManage + 2 枚 onOpenSourceManage）"
                + " + 2 处来自编辑弹窗的返回 + 1 处来自登录屏的返回）：",
            8, cuts.size,
        )
        val entries = cuts.filter {
            Regex("""onOpenSourceManage\s*=\s*\{\s*(?:route\s*=|onRouteChange\()\s*DesktopRoute\.SourceManage\b""").containsMatchIn(it)
        }
        assertEquals(
            "从 `onOpenSourceManage` 那枚回调切过去的那几处没了 ⇒ 入口不是用户按得到的，只是代码里过了一遍：\n" +
                cuts.joinToString("\n"),
            // 探索屏 + 设置中枢各一处（两处都按得到）。
            2, entries.size,
        )
    }

    @Test
    fun 探索屏那枚入口是必传参数而不是带默认值的假开关() {
        // 带 `{}` 默认值的参数等于"没接也不报错"—— 评审 R6 那一族的形状。
        assertTrue(
            "`onOpenSourceManage` 有了默认值：那壳里忘传也不会红，入口就又没了",
            "onOpenSourceManage: () -> Unit," in explore,
        )
        // 探索屏顶栏那排钮收进溢出菜单 —— 门还在（菜单项回调），不再钉按钮字面量。
        // 装配点递了形参（不是靠默认值）。
        assertTrue(
            "壳里没给探索屏递 `onOpenSourceManage` ⇒ 那枚按钮按不动",
            "onOpenSourceManage = {" in shell,
        )
    }

    /**
     * 那一屏有出路：`onBack` 是必传形参、顶栏真渲了那枚返回、壳里切回 `Tab`。
     *
     * ⚠️ 这一条是**不成立**的：那屏只有「源管理」四个字，从探索进来就出不去。
     */
    @Test
    fun 源管理屏有出路而不是单行道() {
        assertTrue("`onBack` 不是必传形参（`=> Unit =` 那种带默认值的不算）", "onBack: () -> Unit," in manage)
        assertTrue("顶栏没渲那枚返回按钮", "DesktopBackButton(onClick = onBack)" in manage)
        // 那一支的锚从 `DesktopRoute.SourceManage ->` 换成 `DesktopSourceManageScreen(` ——
        // 前者已不成立（源编辑那屏把源管理当**背景**渲，两支合成一支 `is SourceManage, is SourceEdit ->`），
        // 而这一条要钉的从来是"那一屏渲出来时带不带出路"，锚在屏名上比锚在路由写法上更贴本意。
        val back = shell.lines().dropWhile { "DesktopSourceManageScreen(" !in it }.take(60).joinToString("\n")
        assertTrue("壳里那一支没把返回切回 Tab（那就是进去出不来）：$back", "onBack = { route = DesktopRoute.Tab }" in back)
    }

    /** 源清单那颗 effect 的键里必须有 `route`（入口接通后 `currentTab` 那一路不再变）。 */
    @Test
    fun 源清单那颗effect的键里有route() {
        val line = shell.lines().firstOrNull { "LaunchedEffect(currentTab, route, sourceTick)" in it }
        assertTrue(
            "源清单那颗 effect 的键里没有 `route` ⇒ 从探索屏切进这一屏时不会重读，屏上永远念\"一个源都没有\"。" +
                "（与清单里 B2 那颗同一个形状：effect 读了哪一路状态，键里就得有哪一路。）",
            line != null,
        )
        val block = shell.lines().dropWhile { "LaunchedEffect(currentTab, route, sourceTick)" !in it }.take(4)
            .joinToString("\n")
        assertTrue("那颗 effect 的守卫里还得留着 FAVORITES 那一路（收藏格也读源清单）：$block", "VeneraNavTab.FAVORITES" in block)
    }

    /**
     * 入口只有一处（探索屏顶栏）。
     *
     * 为什么钉"恰好一处"：安卓那边源管理住在「设置」里，桌面这批**还没有设置屏**。
     * 将来补了设置屏，入口应该挪过去而不是留两处 —— 那时这一判会红，
     * 而红得有意义：提醒改的是"两处还是一个"这条口径，不是随手放宽成 `<=2`。
     */
    @Test
    fun 全仓入口只有一处() {
        // 设置中枢（DesktopSettingsHub）把源管理收进「源与账号」组 ——
        // 第二处接线点随之合法（那正是这条注里预告的"将来补了设置屏，入口挪过去"）。
        val sites = DesktopSourceTree.desktopMainGroupedOccurrences("onOpenSourceManage")
            .map { it.first }
            .filter { it != "DesktopExploreScreen.kt" && it != "DesktopDiscoverScreen.kt" }
            .sorted()
        assertEquals(
            "`onOpenSourceManage` 的接线点变了：",
            listOf("DesktopSettingsHub.kt", "VeneraDesktopLargeScreen.kt"),
            sites,
        )
    }
}
