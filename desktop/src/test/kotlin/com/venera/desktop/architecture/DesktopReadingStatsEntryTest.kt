package com.venera.desktop.architecture

import com.venera.desktop.app.DesktopRoute
import com.venera.desktop.app.VeneraDesktopLargeScreen
import com.venera.desktop.ui.DesktopReadingStatsScreen
import com.venera.desktop.data.DesktopHomeStatsSource
import com.venera.desktop.data.DesktopReadingStats
import com.venera.desktop.data.DesktopReadingStatsSource
import com.venera.desktop.data.desktopReadingStatsScreenReading
import com.venera.desktop.gallery.ui.DesktopSourceTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 钉住「阅读统计那一屏真有一扇门，而门后那件事接着一颗读源」—— 。
 *
 * ## 缺口长什么样（现场量的）
 *
 * `reading_stats` 那张表的读侧（`ReadingStatsStore.summaryRows/trendRows/topComics/streakDays`）
 * 早进桌面编译面，首页那三格数字也一直从 [DesktopHomeStatsSource] 在读 —— 缺的是对岸 `StatsScreen`
 * 那一**整屏看板**（14 天趋势 + 常读漫画 Top 10）。而 `feature/StatsScreen.kt` 是 ③ 档
 * （import `LocalContext` / `BusinessPorts` / material-icons，桌面编不过），所以**桌面自搭**，
 * 不复用那颗屏。这一族与「有逻辑没有门」同形，只是这一颗是「有数字没有那一屏」。
 *
 * 与 `DesktopHistoryEntryTest` / `DesktopDownloadsEntryTest` 同一族用最笨的字符串计数把
 * "消费者存在"钉成会红的判据，而不是等那把惰尺子被修好。
 */
class DesktopReadingStatsEntryTest {

    private val shellFile = DesktopSourceTree.desktopSourceOf("app/VeneraDesktopLargeScreen.kt")
    private val shell = DesktopSourceTree.desktopLargeScreenCode()
    private val shellLines = DesktopSourceTree.desktopLargeScreenLines().filter { it.isNotEmpty() }
    private val explore = DesktopSourceTree.codeText(
        DesktopSourceTree.desktopSourceOf("explore/DesktopExploreScreen.kt"),
    )
    private val screen = DesktopSourceTree.codeText(
        DesktopSourceTree.desktopSourceOf("ui/DesktopReadingStatsScreen.kt"),
    )
    private val route = DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("app/DesktopRoute.kt"))

    /** ① 路由枚举里真有那一档。 */
    @Test
    fun 路由枚举里有统计那一档() {
        assertTrue(
            "`DesktopRoute` 里没有 `data object ReadingStats` ⇒ 那一屏仍然进不去：",
            "data object ReadingStats : DesktopRoute" in route,
        )
    }

    /** ② 切进那一路由**恰两处**：探索屏溢出菜单 + 设置中枢那枚「阅读统计」行。 */
    @Test
    fun 切进统计路由的那一处挂在按得到的把手上() {
        val cuts = shellLines.withIndex().filter { Regex("""(?:route\s*=|onRouteChange\()\s*DesktopRoute\.ReadingStats\b""").containsMatchIn(it.value) }
        assertEquals(
            "切进统计路由的赋值数量变了（现状两处：探索屏溢出菜单 + 设置中枢那一行）：" + cuts.map { shellLines[it.index] },
            2,
            cuts.size,
        )
        // 探索屏顶栏那排钮收进溢出菜单 —— 门还在（菜单项回调），不再钉按钮字面量。
        assertTrue("壳里没给探索屏递 onOpenStats：", "onOpenStats = {" in shell)
    }

    /** ③ 那枚入口在屏侧是必传形参，而不是带默认值的假开关。 */
    @Test
    fun 那枚入口在屏侧是必传形参而不是假开关() {
        assertTrue("`onOpenStats` 有了默认值那扇门就又没了", "onOpenStats: () -> Unit," in explore)
        assertEquals(
            "不许出现带 `= {}` 的那一形：",
            0,
            DesktopSourceTree.codeOccurrences(
                DesktopSourceTree.desktopSourceOf("explore/DesktopExploreScreen.kt"),
                "onOpenStats: () -> Unit = {}",
            ),
        )
    }

    /** ④ 那一屏有出路：`onBack` 必传、顶栏真渲那枚返回、壳里切回 Tab。 */
    @Test
    fun 那一屏有出路而不是单行道() {
        assertTrue("`onBack` 不是必传形参", "onBack: () -> Unit," in screen)
        // 统计屏自搭、不吃统一页壳（mac persona 版），
        // 返回钮判据随之移到屏源码——自搭不等于单行道。
        assertTrue("那一屏自搭页头没渲那枚返回按钮", "DesktopBackButton(onClick = onBack)" in screen)
        val block = shellLines.dropWhile { "DesktopRoute.ReadingStats ->" !in it }.take(20).joinToString("\n")
        assertTrue("壳里那一支没把返回切回 Tab（进去出不来）：$block", "route = DesktopRoute.Tab" in block)
    }

    /** ⑤ 那一屏只吃形参：零 IO、零引擎、零 Fluent（读表住在壳里那颗 produceState）。 */
    @Test
    fun 那一屏零IO零引擎零Fluent() {
        listOf(
            "io.github.composefluent" to "又开出第二条平行线",
            "ReadingStatsStore" to "库口跑到屏里来了（读侧该在 DesktopReadingStatsSource）",
            "EngineSession" to "屏不许问引擎",
            "DatabasePorts" to "屏不许自己开库",
            "withContext" to "屏里不许有 IO",
            "LaunchedEffect" to "读表那颗 effect 该住在壳里，不在屏里",
        ).forEach { (banned, why) ->
            assertEquals(
                "$why：",
                0,
                DesktopSourceTree.codeOccurrences(
                    DesktopSourceTree.desktopSourceOf("ui/DesktopReadingStatsScreen.kt"),
                    banned,
                ),
            )
        }
    }

    /** ⑥ 数据那颗 produceState 的键里有 `route`（读那一路，键里就得有那一路）。 */
    @Test
    fun 数据读源键里有路由而只挂统计那一档() {
        val idx = shellLines.indexOfFirst { "val readingStats by produceState" in it }
        assertTrue(
            "那颗 produceState 的签名变了（判据失效，不是通过）：$idx",
            idx >= 0,
        )
        assertTrue(
            "那颗 produceState 的键里没有 `route` ⇒ 进那一屏不会重读：${shellLines[idx]}",
            "produceState(initialValue = DesktopReadingStats.Loading, route)" in shellLines[idx],
        )
        val block = shellLines.drop(idx).take(8).joinToString("\n")
        assertTrue(
            "那颗 produceState 没挡住「不是统计屏就不读」那一档（会在别的屏白读一趟库）：$block",
            "route is DesktopRoute.ReadingStats" in block,
        )
    }

    /** ⑦ 那一屏的取证读数真接上了（`desktopReadingStatsScreenReading` 那颗件有消费者）。 */
    @Test
    fun 那一屏的读数真有调用点() {
        val sites = DesktopSourceTree.desktopMainGroupedOccurrences("desktopReadingStatsScreenReading(")
        assertEquals(
            "读数件的消费者形状变了（期望「件文件一处声明 + 壳里一处打」）：$sites",
            listOf("DesktopReadingStats.kt" to 1, "VeneraDesktopLargeScreen.kt" to 1),
            sites,
        )
        val idx = shellLines.indexOfFirst { "LaunchedEffect(route, readingStats)" in it }
        assertTrue("打读数那颗 effect 的键里没有 readingStats：$idx", idx >= 0)
        val block = shellLines.drop(idx).take(4).joinToString("\n")
        assertTrue("那颗 effect 没挡住「还没读回来」（会打假读数）：$block", "s.loaded" in block)
    }
}
