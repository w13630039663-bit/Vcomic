package com.venera.desktop.architecture

import com.venera.desktop.gallery.ui.DesktopSourceTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 钉住「本地漫画那一域真有一扇门，而且门后那件事接着一颗真消费者」—— 。
 *
 * ## 缺口长什么样（与同形）
 *
 * 本级读侧 `desktopLocalComics` 这颗件、表头/行内/读数那三颗文案件都齐了，而"有逻辑没有门"是
 * 本仓最反复的那一族病（批 114/115/119/124 全是这形状）：路由枚举里没那一档、探索顶栏没那枚入口、
 * 壳里没有任何一屏渲它 ⇒ 等于那一屏没做，而且**一条判据都不会红**（旧那把惰尺子只扫算式不扫门）。
 * 这里用**最笨的形状**（按字符串数代码行里的出现次数）把"门 + 消费者"钉成会红判据。
 */
class DesktopLocalComicsEntryTest {

    private val shellFile = DesktopSourceTree.desktopSourceOf("VeneraDesktopLargeScreen.kt")
    private val shell = DesktopSourceTree.desktopLargeScreenCode()
    private val explore = DesktopSourceTree.codeText(
        DesktopSourceTree.desktopSourceOf("explore/DesktopExploreScreen.kt"),
    )
    private val screen = DesktopSourceTree.codeText(
        DesktopSourceTree.desktopSourceOf("downloads/DesktopLocalComicLibraryScreen.kt"),
    )
    private val data = DesktopSourceTree.codeText(
        DesktopSourceTree.desktopSourceOf("gallery/data/DesktopLocalComicLibrary.kt"),
    )

    /** 有一处真把路由切到「本地漫画」，而且切法与「本机已下载」同一判据（没根就不摆那扇门）。 */
    @Test
    fun 有一处真把路由切到本地漫画() {
        val lines = shell.lines()
        // 本地漫画入口收口至合并页 LOCAL 档（filesSegment 恰 2 处、侧栏门恰 1 处），阅读器返回切回 Files 合并页。
        val idx = lines.withIndex().filter { "route = DesktopRoute.LocalComics" in it.value }.map { it.index }
        assertEquals(
            "切进「本地漫画」独立路由已全部收编进 Files（赋值处数应为 0）：" +
                idx.map { lines[it] },
            0, idx.size,
        )

        val tierIdx = lines.withIndex()
            .filter { "filesSegment = com.venera.desktop.downloads.DesktopFilesSegment.LOCAL" in it.value }
            .map { it.index }
        // 宽屏双源批 2→3：首页新增第二套（宽屏双列）接线，本地漫画档多一处
        // `onOpenLocalComics`（窄版/宽版各一枚 + 探索屏一枚），三处都仍挂在「按下载根决定摆不摆」的门上。
        assertEquals(
            "切进合并页 LOCAL 档的入口赋值该恰三处（窄/宽两套首页 + 探索屏，都挂 onOpenLocalComics 门）：" +
                tierIdx.map { lines[it] }.joinToString("\n"),
            3, tierIdx.size,
        )
        tierIdx.forEach { at ->
            val above = lines.drop((at - 4).coerceAtLeast(0)).take(6).joinToString("\n")
            assertTrue(
                "档位入口没挂在 `onOpenLocalComics` 那枚按下载根决定摆不摆的门上：\n${lines[at]}",
                "onOpenLocalComics = if (downloadsRoot == null) null else {" in above,
            )
        }
        // .1 定案（「下载与本地和下载队列两个不要分离，就同一个就行，不能有两个入口」）：
        // 侧栏撤销独立「本地漫画」门，统一收纳进「下载与文库」流水线单门；本地导入与已下载漫画并入文库网格，
        // 并在文库段头保留「导入 CBZ/ZIP」把手；侧栏独立门 openFilesSegment(segLocal) 归 0。
        assertEquals(
            "侧栏「本地漫画」独立门在方案稿 v4.1 已撤并进下载与文库（openFilesSegment(segLocal) 期望为 0）：",
            0, lines.count { "openFilesSegment(segLocal)" in it },
        )
    }

    /** ② 那枚入口在屏侧是必传形参而不是假开关。 */
    @Test
    fun 那枚入口在屏侧是必传形参而不是假开关() {
        assertTrue("`onOpenLocalComics` 有了默认值", "onOpenLocalComics: (() -> Unit)?," in explore)
        // 主人「去掉更多按钮」后菜单整颗删掉，落点由侧栏「下载与本地」承载；
        // 屏侧不再画那扇门（形参仍必传无默认，装配点的 `if` 判据仍在壳里）。
        assertTrue(
            "那枚入口又回到屏上了（菜单该删，落点在侧栏）",
            "if (onOpenLocalComics != null) {" !in explore,
        )
    }

    /** ③ 那一屏有出路而不是单行道。 */
    @Test
    fun 本地漫画屏有出路而不是单行道() {
        assertTrue("`onBack` 不是必传形参", "onBack: () -> Unit," in screen)
        assertTrue("那一屏没有走统一页壳", "DesktopPageShell(" in screen)
        assertTrue("页壳没接返回回调", "onBack = onBack," in screen)
        // 列表主体从 `ListPageTemplate`（行卡）换成的封面卡网格
        // （`LazyVerticalGrid` + `LocalComicCard`）—— 判据从"用了统一行模板"改成"用了封面卡网格"。
        assertTrue("列表主体没有走封面卡网格", "LazyVerticalGrid(" in screen && "LocalComicCard(" in screen)
        val pageShell = DesktopSourceTree.codeText(
            DesktopSourceTree.desktopSourceOf("ui/DesktopPageTemplates.kt"),
        )
        assertTrue("统一页壳没渲那枚返回按钮", "DesktopBackButton(onClick = onBack)" in pageShell)
        val block = shell.lines().dropWhile { "DesktopRoute.LocalComics ->" !in it }.take(12).joinToString("\n")
        assertTrue("壳里那一支没把返回切回 Tab（那就是进去出不来）：$block", "route = DesktopRoute.Tab" in block)
    }

    /** ④ 本级扫描那颗件真有恰一处消费者：「一处声明（data 件） + 一处壳里按」。 */
    @Test
    fun 本级扫描那颗件只有一处声明与一处壳里按() {
        val sites = DesktopSourceTree.desktopMainGroupedOccurrences("desktopLocalComics(")
        assertEquals(
            "`desktopLocalComics(` 的消费者形状变了（期望恰好「DesktopLocalComicLibrary.kt 一处声明 + 壳里一处按」）：$sites",
            listOf("DesktopLocalComicLibrary.kt" to 1, "VeneraDesktopLargeScreen.kt" to 1),
            sites,
        )
    }

    /** ⑤ 那一屏零 IO 零 Fluent 零引擎（盘上的事全在壳里那颗独立 effect）。 */
    @Test
    fun 那一屏零IO零Fluent零引擎() {
        assertEquals(
            "那一屏出现 `withContext` ⇒ 盘上的事跑到屏里来了：",
            0,
            DesktopSourceTree.codeOccurrences(
                DesktopSourceTree.desktopSourceOf("downloads/DesktopLocalComicLibraryScreen.kt"),
                "withContext",
            ),
        )
        assertEquals(
            "那一屏引了 Fluent 组件 ⇒ 又开第二条平行线",
            0,
            DesktopSourceTree.codeOccurrences(
                DesktopSourceTree.desktopSourceOf("downloads/DesktopLocalComicLibraryScreen.kt"),
                "io.github.composefluent",
            ),
        )
        assertEquals(
            "那一屏碰了引擎 ⇒ 本级域不该有源站跳（这是读盘的域，不是问站的域）",
            0,
            DesktopSourceTree.codeOccurrences(
                DesktopSourceTree.desktopSourceOf("downloads/DesktopLocalComicLibraryScreen.kt"),
                "EngineSession",
            ) + DesktopSourceTree.codeOccurrences(
                DesktopSourceTree.desktopSourceOf("downloads/DesktopLocalComicLibraryScreen.kt"),
                "desktopEngineCall",
            ),
        )
    }

    /** ⑥ 本级那件吃的是第二把尺 `isPageImageFile`，而**绝不**吃那把魔数尺。 */
    @Test
    fun 本级件吃第二把尺而不碰魔数尺() {
        assertTrue("本级件没复用对岸那颗 `isPageImageFile`（那就是偷加第四把尺）：",
            "isPageImageFile" in data)
        assertEquals(
            "本级件不许吃 `desktopComicPageLooksWhole(`（那颗的账由 DesktopComicPageWholenessTest 钉死六处）",
            0,
            DesktopSourceTree.codeOccurrences(
                DesktopSourceTree.desktopSourceOf("gallery/data/DesktopLocalComicLibrary.kt"),
                "desktopComicPageLooksWhole(",
            ),
        )
    }

    /** ⑦ 取证读数那颗件真有消费者：一处声明 + 一处壳里打。 */
    @Test
    fun 读数件有一处在壳里打() {
        val sites = DesktopSourceTree.desktopMainGroupedOccurrences("desktopLocalComicLibraryReading(")
        assertEquals(
            "`desktopLocalComicLibraryReading(` 的消费者形状变了：$sites",
            listOf("DesktopLocalComicLibrary.kt" to 1, "VeneraDesktopLargeScreen.kt" to 1),
            sites,
        )
    }

    /** ⑧ 本级读取那颗 effect 只在进那一屏时读盘（键里有 route，早退只认 LocalComics 那一档）。 */
    @Test
    fun 本级读取effect只认那一档而不扰批115那颗() {
        // 本地漫画扫描使用独立 effect（不并入话级扫描），早退条件须精确锚定 LocalComics 与 Tab，防止放宽漏检。
        val guard = "route !is DesktopRoute.LocalComics && route !is DesktopRoute.Tab &&"
        assertEquals("壳里那颗本级 effect 的早退判式恰一处：", 1,
            shell.lines().count { it.trim() == guard })
        // 精确锚定 Files 的 LOCAL 档与 localComicImportTick/filesSegment 键，确保与话级 DOWNLOADED 扫描分离。
        val declIdx = shell.lines().indexOfFirst { it.contains("LaunchedEffect(route, filesSegment, downloadsTick, localComicImportTick)") }
        assertTrue("本级 effect 没单列一颗（应与话级那颗分开）", declIdx >= 0)
        val guardBody = shell.lines().drop(declIdx).take(14).joinToString("\n")
        assertEquals(
            "本级 effect 里多认合并页 LOCAL 档的那一支恰一处：",
            1, guardBody.lines().count { "filesSegment == com.venera.desktop.downloads.DesktopFilesSegment.LOCAL" in it },
        )
        // 本级那一支读盘用 desktopLocalComics，绝不用话级那颗 desktopComicDownloadedScan。
        val body = shell.lines().drop(declIdx).take(30).joinToString("\n")
        assertTrue("本级 effect 读的是本级件：", "desktopLocalComics(root)" in body)
        assertEquals("本级 effect 不许复用话级那颗扫描：", 0,
            body.lines().count { "desktopComicDownloadedScan(" in it })
    }
}
