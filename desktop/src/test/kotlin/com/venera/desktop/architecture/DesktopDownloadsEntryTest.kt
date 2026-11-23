package com.venera.desktop.architecture

import com.venera.desktop.gallery.ui.DesktopSourceTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 钉住「本机已下载那一屏真有一扇门，而且门后那四件事各自接着一颗 runner」—— 。
 *
 * ## 缺口长什么样
 *
 * 那一屏的四件事（数盘 / 缺页补齐 / 两步删除 / 什么时候该重读）从批 97 起就一件件接完并有单条判据
 * 盯着，而 `gallery/ui/DesktopComicDownloadsPane.kt` 的 `@Composable` 是 0：路由分了、纯函数在，
 * **没有任何一屏把它们摆出来**，用户看不见也改不了。这一批评的是"有逻辑没有门"。
 *
 * ## 为什么这一把尺子值得新写而不是复用 `DesktopUnusedInternalTest`
 *
 * 那把「零调用点机械尺子」今天只有 KDoc 而没有 `@Test`（① 与 ③ 都没落地），而且
 * `DesktopUnusedInternalScan.fromTree()` 全仓零调用 ⇒ 它是惰的。这就是"门"掉了 100 多批
 * 而一条判据都没红的成因。这里用**最笨的形状**（按字符串数代码行里的出现次数）把"消费者存在"
 * 钉成会红判据，而不是等那把惰尺子被修好。
 */
class DesktopDownloadsEntryTest {

    private val shellFile = DesktopSourceTree.desktopSourceOf("VeneraDesktopLargeScreen.kt")
    private val shell = DesktopSourceTree.desktopLargeScreenCode()
    private val explore = DesktopSourceTree.codeText(
        DesktopSourceTree.desktopSourceOf("explore/DesktopExploreScreen.kt"),
    )
    private val screen = DesktopSourceTree.codeText(
        DesktopSourceTree.desktopSourceOf("downloads/DesktopComicDownloadsScreen.kt"),
    )

    /** 那一屏唯一的入口：恰好一处把合并页切到「本机已下载」档，而且切法是「没有下载根目录就不摆那扇门」。 */
    @Test
    fun 有一处真把路由切到本机已下载() {
        val lines = shell.lines()
        // 旧 `DesktopRoute.Downloads` 的入口赋值撤了，
        // 门统一切「下载与本地」合并页的 DOWNLOADED 档；回调那枚 `onOpenDownloads` 的门与判据不变。
        val idx = lines.withIndex()
            .filter { "filesSegment = com.venera.desktop.downloads.DesktopFilesSegment.DOWNLOADED" in it.value }
            .map { it.index }
        assertEquals(
            "切进「本机已下载」档的赋值不止一处（那多半是有人另开了第二个入口而没写进这里的口径）或一处都没有：" +
                idx.map { lines[it] },
            1, idx.size,
        )
        // 回调是多行写法（`onOpenDownloads = if … else {` 换行 `{` 再逐行赋值），所以看它上面四行。
        val above = lines.drop((idx.single() - 4).coerceAtLeast(0)).take(6).joinToString("\n")
        assertTrue(
            "那一处不是挂在 `onOpenDownloads` 那枚回调上 ⇒ 入口不是用户按得到的，只是代码里过了一遍：\n$above",
            "onOpenDownloads = if (downloadsRoot == null) null else {" in above,
        )
    }

    /**
     * 那枚入口的**可空**是刻意的，且必须与「没有写口就不摆」同口径：
     * 屏侧不许有默认值（带默认值的开关等于"没接也不报错"），壳侧不许按任务数或盘上有没有东西摆。
     */
    @Test
    fun 那枚入口在屏侧是必传形参而不是假开关() {
        assertTrue(
            "`onOpenDownloads` 有了默认值：壳里忘传也不会红，那扇门就又没了",
            "onOpenDownloads: (() -> Unit)?," in explore,
        )
        // 探索屏已移除溢出菜单入口（统一收口至侧栏），此处校验屏侧不再渲染菜单项且必传回调形参保留。
        assertTrue(
            "那枚入口又回到屏上了（菜单该删，落点在侧栏）",
            "if (onOpenDownloads != null) {" !in explore,
        )
    }

    /** 那一屏有出路：`onBack` 是必传形参、顶栏真渲了那枚返回、壳里切回 `Tab`。 */
    @Test
    fun 本机已下载屏有出路而不是单行道() {
        assertTrue("`onBack` 不是必传形参", "onBack: () -> Unit," in screen)
        assertTrue("那一屏没有走统一页壳", "DesktopPageShell(" in screen)
        assertTrue("页壳没接返回回调", "onBack = onBack," in screen)
        // 列表主体从 ListPageTemplate 换成稿的行形
        // （DqThumb 缩略格 + LazyColumn 行 + 图标把手）；"屏不碰盘/不算武装态"那两条口径没动。
        assertTrue(
            "列表主体没有走稿 v3 的 rowq 件（DqThumb + LazyColumn）",
            "DqThumb(" in screen && "LazyColumn(" in screen,
        )
        val pageShell = DesktopSourceTree.codeText(
            DesktopSourceTree.desktopSourceOf("ui/DesktopPageTemplates.kt"),
        )
        assertTrue("统一页壳没渲那枚返回按钮", "DesktopBackButton(onClick = onBack)" in pageShell)
        val block = shell.lines().dropWhile { "DesktopRoute.Downloads ->" !in it }.take(30).joinToString("\n")
        assertTrue("壳里那一支没把返回切回 Tab（那就是进去出不来）：$block", "route = DesktopRoute.Tab" in block)
    }

    /**
     * 那五颗 runner 各自**真有消费者**（口径：代码行里恰好「一处声明 + 一处在壳里按」）。
     *
     * 为什么数得这么死：这一批的毛病不是"缺函数"而是"函数都在而没人调用"。放宽成 `>= 1`
     * 就回到那条惰尺子的下场（永远绿、永远不报）。将来真多一个消费者时它红，而红得有意义 ——
     * 该改的是这里的口径名单，不是随手把期望数调大。
     */
    @Test
    fun 那五颗runner各有一处在壳里按() {
        val declared = listOf(
            "listComicListing(" to "DesktopDownloadsDb.kt",
            "desktopComicDownloadedReading(" to "DesktopComicDownloadsPane.kt",
            "desktopComicDeleteChapter(" to "DesktopComicDownloadsDelete.kt",
            "desktopComicRepairChapter(" to "DesktopComicShelfRepair.kt",
            "desktopComicShelfRerollKey(" to "DesktopComicShelfReroll.kt",
        )
        for ((needle, declName) in declared) {
            val sites = DesktopSourceTree.desktopMainGroupedOccurrences(needle)
            assertEquals(
                "`$needle` 的消费者形状变了（期望恰好「$declName 里一处声明 + 壳里一处按」）：$sites",
                listOf(declName to 1, "VeneraDesktopLargeScreen.kt" to 1),
                sites,
            )
        }
    }

    /** 那一屏自己既不碰盘也不引第二套组件库。 */
    @Test
    fun 那一屏零IO零Fluent() {
        assertEquals(
            "那一屏里出现 `withContext` ⇒ 盘上的事跑到屏里来了（读树/删除/入队全在外面那几颗件里）：" +
                screen.lines().filter { "withContext" in it },
            0,
            DesktopSourceTree.codeOccurrences(
                DesktopSourceTree.desktopSourceOf("downloads/DesktopComicDownloadsScreen.kt"),
                "withContext",
            ),
        )
        assertEquals(
            "那一屏引了 Fluent 组件 ⇒ 又开出第二条平行线（Fluent 那批是已拆掉的残留，不引第二套）",
            0,
            DesktopSourceTree.codeOccurrences(
                DesktopSourceTree.desktopSourceOf("downloads/DesktopComicDownloadsScreen.kt"),
                "io.github.composefluent",
            ),
        )
    }

    /**
     * 删除那两步不许退化成一步：壳里"该不该真删"判为假时**只换武装那一格然后返回**。
     *
     * 这是批 98 那条纪律在门这侧的复读 —— 门一开，用户第一次按下就真删掉一话的落盘文件，
     * 那比"没有门"坏得多，所以必须钉住早退那两行还在同一个块里。
     */
    @Test
    fun 删除第一次按下只武装不动盘() {
        val lines = shell.lines()
        val idx = lines.indexOfFirst { "desktopComicDeleteShouldFire(armedDeleteKey" in it }
        assertTrue(
            "壳里找不到「该不该真删」那一步的守卫（它退到别处去了，或者干脆变成一步就删）",
            idx >= 0,
        )
        val block = lines.drop(idx).take(4).joinToString("\n")
        assertTrue("守卫为假时没把武装记到这一行：$block", ("armedDeleteKey = desktopComicDeleteNextKey(" in block || "armedDeleteKeyState.value = desktopComicDeleteNextKey(" in block))
        assertTrue(
            "守卫为假时没早退（那就是一次按下既武装又删）：$block",
            block.lines().map { it.trim() }.contains("return"),
        )
    }
}
