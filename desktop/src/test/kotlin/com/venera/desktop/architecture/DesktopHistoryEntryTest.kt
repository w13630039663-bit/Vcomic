package com.venera.desktop.architecture

import com.venera.desktop.app.DESKTOP_EXPLORE_SOURCE
import com.venera.desktop.app.DesktopRoute
import com.venera.desktop.app.VeneraDesktopLargeScreen
import com.venera.desktop.ui.DesktopHomePane
import com.venera.desktop.gallery.ui.DesktopSourceTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 钉住「阅读历史那一屏真有一扇门，而且门后那几件事各自接着一颗 runner」—— 。
 *
 * ## 缺口长什么样（现场量的，不是抄旧账）
 *
 * `comic_history` 那张表的**写侧**（阅读器逐页落库）与**读侧**（那颗等 flow 出值 + 2s 超时兜底的件）
 * 从批 82 起就一件件接完并有判据盯着，而 `gallery/ui/DesktopComicHistoryScreen.kt` 的
 * `@Composable` 是 **0**，路由枚举里也没有「历史」这一档，那颗文件还带着已拆掉的 Fluent 平行线
 * （`io.github.composefluent.component.Text`）那一条 import。于是「读过哪几本 / 读到第几话第几页 /
 * 那一行点不点得动」用户今天看不见也进不去 —— 只有首页那一排**最多 4 张**的横滑卡够得着，
 * 而那 4 张卡按下去从前是 `onOpenHistory: (HistoryRecord) -> Unit = {}`，**一颗假开关**。
 *
 * 与 `DesktopSourceManageEntryTest` / `DesktopDownloadsEntryTest` 同一族：这一批的毛病从来
 * 不是"缺函数"而是"函数都在而没人调用"。所以这里用最笨的形状（按字符串数代码行里的出现次数）
 * 把"消费者存在"钉成会红的判据，而不是等 `DesktopUnusedInternalTest` 那把惰尺子被修好
 * （它 ①③ 只有 KDoc 而没有 `@Test`，这就是"门"掉了 100 多批而一条判据都没红的成因）。
 */
class DesktopHistoryEntryTest {

    private val shellFile = DesktopSourceTree.desktopSourceOf("app/VeneraDesktopLargeScreen.kt")
    private val shell = DesktopSourceTree.desktopLargeScreenCode()
    private val shellLines = DesktopSourceTree.desktopLargeScreenLines().filter { it.isNotEmpty() }
    private val explore = DesktopSourceTree.codeText(
        DesktopSourceTree.desktopSourceOf("explore/DesktopExploreScreen.kt"),
    )
    private val homePane = DesktopSourceTree.codeText(
        DesktopSourceTree.desktopSourceOf("ui/DesktopHomePane.kt"),
    )

    /**
     * ① 切进那一路由的那几处，**每一处都挂在用户按得到的把手上**。
     *
     * 为什么这里数到 **2** 而不是像 `DesktopSourceManageEntryTest` ① 那样数到 1：
     * 历史这一屏确实有两条进得去的路，而两条都是"按下某一枚东西"——
     *  - 探索屏顶栏那枚「阅读历史」（那一屏唯一的入口把手）；
     *  - 首页那一排历史卡里**开不动的那一张**（共享件 `HistoryCardHome` 没有"这一行开不动"的形状，
     *    恒给点击，所以这里把它送去历史那一屏，让那一行自己带着"为什么按不动"那一句）。
     * 把第二条读成"第二个入口没写进账"是错的，写成 1 才是假话。这一条钉的是更强的那件事：
     * **两处都必须在某枚 `onOpenHistory` 的回调体里** —— 多出来的第三处、或某处被挪到回调外面
     * （那等于"代码里过了一遍而没人按得到"）都会红。
     */
    @Test
    fun 切进那一路由的每一处都挂在按得到的把手上() {
        val cuts = shellLines.withIndex().filter { Regex("""(?:route\s*=|onRouteChange\()\s*DesktopRoute\.History\b""").containsMatchIn(it.value) }.map { it.index }
        // 历史入口共 5 处（探索菜单、设置行、首页历史卡去向（浅/宽首页共用）、窄/宽首页「全部 ›」），均走 historyTick++ + route = History。
        assertEquals(
            "切进「阅读历史」路由的赋值数量变了：" +
                cuts.map { shellLines[it] },
            5,
            cuts.size,
        )
        // 回调共 6 处：onOpenHistory 2 处 + 首页共享 openHistory 1 处 + onOpenAllHistory 2 处 + 设置/合并页「收藏与历史」回原卡 1 处。
        val heads = shellLines.withIndex()
            .filter {
                it.value.startsWith("onOpenHistory = {") ||
                    it.value.startsWith("onOpenAllHistory = {") ||
                    it.value.startsWith("val openHistory:")
            }
            .map { it.index }
        assertEquals(
            "壳里那些回调的头部读不全（判据失效，不是通过）：" + heads.map { shellLines[it] },
            6,
            heads.size,
        )
        val covered = heads.flatMap { callbackBody(it) }.toSet()
        assertEquals(
            "这些「切进历史路由」的赋值不在任何一枚 `onOpenHistory` 回调体里 ⇒ 那不是用户按得到的门：" +
                cuts.filter { it !in covered }.map { shellLines[it] },
            cuts.toSet(),
            cuts.filter { it in covered }.toSet(),
        )
    }

    /** 从 `onOpenHistory = {` 那一行起到配对的 `}` 为止（大括号深度归零）。 */
    private fun callbackBody(from: Int): List<Int> {
        val out = mutableListOf<Int>()
        var depth = 0
        for (i in from until shellLines.size) {
            out += i
            depth += shellLines[i].count { it == '{' } - shellLines[i].count { it == '}' }
            if (depth <= 0) break
        }
        return out
    }

    /** ② 路由枚举里真有那一档（`when (route)` 那一支的前提）。 */
    @Test
    fun 路由枚举里有历史那一档() {
        val route = DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("app/DesktopRoute.kt"))
        assertTrue(
            "`DesktopRoute` 里没有 `data object History` ⇒ 那一屏仍然进不去：",
            "data object History : DesktopRoute" in route,
        )
    }

    /** ③ 屏侧那枚入口是必传形参，而不是带默认值的假开关。 */
    @Test
    fun 那枚入口在屏侧是必传形参而不是假开关() {
        // 探索屏顶栏那排钮收进溢出菜单 —— 门还在（菜单项回调），这一条改钉形参与接线。
        assertTrue(
            "`onOpenHistory` 有了默认值：壳里忘传也不会红，那扇门就又没了",
            "onOpenHistory: () -> Unit," in explore,
        )
        assertTrue("壳里没给探索屏递 `onOpenHistory`", "onOpenHistory = {" in shell)
    }

    /**
     * ④ 首页那一排历史卡那颗假开关**撤掉了**（这条是最该红的形状）。
     *
     * `onOpenHistory: (HistoryRecord) -> Unit = {}` 从前就住在 `DesktopHomePane` 的形参上：
     * 卡摆着、按下去什么都不做，而"漏传"连编译红都不给（`DesktopCallbackParamLedgerTest` 头注
     * 记的那一族）。那颗尺子只扫 `gallery/ui/` 那一片，而这一颗住在 `com.venera.desktop` 顶层，
     * 所以这里单独钉 —— 撤掉那条默认值才算把"假开关"这笔账收掉。
     */
    @Test
    fun 首页那颗假开关撤掉了而壳真递了回调() {
        assertTrue(
            "`DesktopHomePane.onOpenHistory` 又带回默认值了 ⇒ 漏传不报错而卡按下去没反应：$homePane",
            "onOpenHistory: (com.venera.compose.data.db.HistoryRecord) -> Unit," in homePane,
        )
        assertEquals(
            "那颗形参不许出现带 `= {}` 的那一形：",
            0,
            DesktopSourceTree.codeOccurrences(
                DesktopSourceTree.desktopSourceOf("ui/DesktopHomePane.kt"),
                "onOpenHistory: (com.venera.compose.data.db.HistoryRecord) -> Unit = {}",
            ),
        )
        assertTrue(
            "壳里没给首页那排卡递 `onOpenHistory`：",
            "onOpenHistory = openHistory" in shell || "onOpenHistory = { record ->" in shell,
        )
    }

    /**
     * ⑤ 那一屏有出路：`onBack` 是必传形参、顶栏真接了返回、壳里切回 `Tab`。
     *
     * 仿制屏退役后屏本体在共享面 `feature/HistoryScreenBody.kt`（顶栏那颗是 `VeneraTopBarPill`，
     * 多选态下动作是退出多选、非多选才调 `onBack()`），这里改钉那一边 + 壳的接线。
     */
    @Test
    fun 那一屏有出路而不是单行道() {
        val body = DesktopSourceTree.codeText(DesktopSourceTree.androidSource("feature/HistoryScreenBody.kt"))
        assertTrue("共享屏本体的 `onBack` 不是必传形参", "onBack: () -> Unit," in body)
        assertTrue("顶栏那颗返回没接 onBack（多选态退出多选、否则回调装配层）：", "else onBack()" in body)
        val block = shellLines.dropWhile { "DesktopRoute.History ->" !in it }.take(30).joinToString("\n")
        assertTrue("壳里那一支没把返回切回 Tab（那就是进去出不来）：$block", "route = DesktopRoute.Tab" in block)
    }

    /**
     * ⑥ 装载用的那颗键**只在壳里取一次**，而取的是当前源那颗常量。
     *
     * 这一条钉的是最容易写错又不会红的一笔：`comic_history.source_name` 两端写的不是同一种东西
     * （手机写显示名、桌面写键），拿库里那一枚去 `EngineSession.load()` 就撞
     * `装载后取不到 source key` ⇒ 屏上是"从历史点进去一本都打不开"。算式本身由
     * `DesktopComicHistoryScreenTest` ⑩ 钉；这里钉的是**接线形状**：
     *  ① 全 main 里那颗取键件只有一个消费者文件（壳），别处另起一轨就红；
     *  ② 壳里两路（历史屏那一支 + 首页那排卡）都问它，而不是各自拼 `DesktopRoute.Detail(...)`。
     */
    @Test
    fun 取键只有一处出处而壳里两路都问它() {
        val sites = DesktopSourceTree.desktopMainGroupedOccurrences("desktopComicHistoryOpenSourceOf(")
        assertEquals(
            "`desktopComicHistoryOpenSourceOf` 的消费者形状变了（期望「屏侧那颗文件里一处声明 + 壳里两处按（首页共享 + 历史屏）」）：$sites",
            listOf("DesktopComicHistoryScreen.kt" to 1, "VeneraDesktopLargeScreen.kt" to 2),
            sites,
        )
        // 两路都必须带着当前源那枚常量（而不是库里那一枚 / 不是硬编码一颗键）。
        val calls = shellLines.filter { "desktopComicHistoryOpenSourceOf(" in it }
        assertEquals("壳里那两路各问一次：", 2, calls.size)
        val blocks = calls.map { c -> shellLines.drop(shellLines.indexOf(c)).take(4).joinToString("\n") }
        blocks.forEach { block ->
            assertTrue(
                "壳里有一路没把 `currentSourceKey` 取成那枚常量（硬编码或取库里那一枚都会漂）：$block",
                "currentSourceKey = DESKTOP_EXPLORE_SOURCE" in block,
            )
            assertTrue("名字那一格取的是壳里那一枚状态而不是现编：$block", "currentSourceName = historySourceName" in block)
        }
    }

    /** ⑦ 那一屏零库零引擎零 Fluent；壳里那一支零 IO（读表的是外面那颗件）。 */
    @Test
    fun 那一屏零IO零Fluent而壳里那一支也不碰库() {
        listOf(
            "io.github.composefluent" to "又开出第二条平行线（Fluent 那批是已拆掉的残留）",
            "HistoryDao" to "库口跑到屏里来了",
            "EngineSession" to "屏不许问引擎：那一跳的账在壳与那颗锁上",
        ).forEach { (banned, why) ->
            assertEquals(
                "$why：",
                0,
                DesktopSourceTree.codeOccurrences(
                    DesktopSourceTree.desktopSourceOf("gallery/ui/DesktopComicHistoryScreen.kt"),
                    banned,
                ),
            )
        }
        // 壳里那一支（历史屏到 `onBack` 那一段）不许出现 IO：那张表由 `homeHistory` 读回来递下去。
        val block = shellLines.dropWhile { "DesktopRoute.History ->" !in it }.take(30).joinToString("\n")
        assertTrue("壳里那一支出现 `withContext` ⇒ 读库跑进了渲染分派里：$block", "withContext" !in block)
        assertTrue("壳里那一支不许自己去开库：$block", "HistoryDao" !in block)
    }

    /**
     * ⑧ 源清单那颗 effect 的早退条件里有那一屏（否则手机写的那几行永远"名字不在手"）。
     *
     * 与 `DesktopSourceManageEntryTest` ④ 同一判法（那里钉的是"键里得有 `route`"）：入口接通之后
     * 用户是从探索屏**切进这一路由**的，而 `currentTab` 那一路没变 ⇒ 光靠 `currentTab + sourceTick`
     * 那一趟不会重跑，名字永远读不到，那一屏就永远念"名字不在手"。
     */
    @Test
    fun 源清单那一趟也认历史那一屏() {
        val idx = shellLines.indexOfFirst { "LaunchedEffect(currentTab, route, sourceTick)" in it }
        assertTrue("源清单那颗 effect 的签名变了（判据失效，不是通过）", idx >= 0)
        val guard = shellLines.drop(idx).take(14).joinToString("\n")
        assertTrue(
            "那颗 effect 的早退条件里没留 `History` 那一档 ⇒ 进那一屏不会去读源清单，手机写的那几行念不出名字：$guard",
            "route !is DesktopRoute.History" in guard,
        )
        assertTrue("FAVORIES 那一路还得留着（收藏屏也读这一趟）：$guard", "VeneraNavTab.FAVORITES" in guard)
        assertTrue("源管理那一路还得留着：$guard", "route !is DesktopRoute.SourceManage" in guard)
    }

    /** ⑨ 那一屏的取证读数真接上了（`D_历史 屏 …` 那颗件从前零调用点）。 */
    @Test
    fun 那一屏的读数真有调用点() {
        val sites = DesktopSourceTree.desktopMainGroupedOccurrences("desktopComicHistoryScreenReading(")
        assertEquals(
            "`desktopComicHistoryScreenReading` 的消费者形状变了（期望「屏侧一处声明 + 壳里一处打」）：$sites",
            listOf("DesktopComicHistoryScreen.kt" to 1, "VeneraDesktopLargeScreen.kt" to 1),
            sites,
        )
        // 那颗 effect 的键里得有 `homeHistory`：读了那一路，键里就得有那一路（B2 那一族的既有口径）。
        val idx = shellLines.indexOfFirst { "LaunchedEffect(route, homeHistory, historySourceName)" in it }
        assertTrue(
            "打读数那颗 effect 的键里没有 `homeHistory` ⇒ 那张表读回来时不会重打，`行=几` 永远是旧账",
            idx >= 0,
        )
        val block = shellLines.drop(idx).take(6).joinToString("\n")
        assertTrue("那颗 effect 没挡住「还没读回来」那一档（会打出 `行=0` 的假读数）：$block", "!homeHistory.loaded" in block)
    }

    /**
     * ⑩ 槽位迁移（仿制屏退役批的）：共享屏那一格的**门控形状**必须在。
     *
     * 桌面一次只装载一颗源，而共享屏 `HistoryScreenBody` 从前**行行恒可点** ——
     * 库里别源那几行按下去被壳里 `desktopComicHistoryOpenSourceOf(...)?.let{}` 静默吞掉，
     * 成"点了没反应"的**假死点击**（本仓「灰行不许留在屏上」那一族）。退役仿制屏前必须先把
     * 仿制件独有的那一格（开不动 + 那句"为什么"）**迁进共享面**，安卓不传 ⇒ 恒开逐字不变。
     *
     * ## 判式为什么落在这颗账上而不是行为测
     *
     * 行为级证据要把 17 族端口做成测试替身 —— 而 `BusinessPorts.install("desktop")` 先到先得、
     * `HistoryDao`/`LocalFavoritesManager` 是进程级单例（那颗文件 KDoc 明写"静态口不清零"），
     * 全局装一次会污染其余台架。假端口台架属退役批本体（那批要连着把 4 颗 UI 测转喂共享屏），
     * 这一条先把**接线形状**钉住：谁把插槽撤了、谁让壳里的判据改走第二套算式，都先红在这把尺上。
     */
    @Test
    fun 共享屏那一行的门控插槽在而壳里只问唯一那颗判据() {
        val body = DesktopSourceTree.androidSource("feature/HistoryScreenBody.kt").readText()
        // ① 插槽两颗存在（带缺省 ⇒ 安卓装配不传就恒开，行为等价）。
        assertTrue("共享屏没有 `rowGate` 插槽（别源那几行会回到假死点击）：", "rowGate: (HistoryRecord) -> Boolean = { true }" in body)
        assertTrue("共享屏没有 `rowNote` 插槽（开不动少了那句为什么）：", "rowNote: (HistoryRecord) -> String? = { null }" in body)
        // ② 门控真的接进行为里：导航那一手被 gate 看着，而不是照旧无条件 `onOpenRecord(record)`。
        assertTrue("gate 没接进导航那一步（插槽白传）：", "if (rowGate(record)) onOpenRecord(record)" in body)
        // ③ 空位反向钉：壳里那两路不许自己写第二套"是不是当前源"的算式（口径仍只有 `desktopComicHistoryRowGate`）。
        assertEquals(
            "壳里 `desktopComicHistoryRowGate(` 的出现次数变了（期望两处调用 + 三处 import 行不算：rowGate/rowNote 各一问）：",
            2,
            shellLines.count { "desktopComicHistoryRowGate(" in it && "import " !in it },
        )
        // 喂插槽那两路 + 原先 `onOpenRecord` 里那一路取键，都带着当前源那枚常量（口径不许现编）。
        assertTrue(
            "壳里 `currentSourceKey = DESKTOP_EXPLORE_SOURCE` 的出现次数变了：",
            shellLines.count { "DESKTOP_EXPLORE_SOURCE," in it } >= 2,
        )
    }
}
