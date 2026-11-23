package com.venera.desktop.architecture

import com.venera.desktop.gallery.ui.DesktopSourceTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 钉住「详情页评论那一屏真有一扇门，而门后那一跳真接上了引擎」。
 *
 * ## 缺口长什么样（现场量的）
 *
 * 评论那几颗源早随包进桌面编译面，`ComicSource.kt:107-146` 那套桥协议、对岸 15 颗真源实现 `comic.loadComments`
 * 都在 —— 而桌面这条渲染路里 `commentCount` / `loadComments` **零消费者**（本喵扫过 `desktop/src/main`
 * 三处 token 全部命中 0），于是"能读、能翻、能收藏、评论那一格看不见"就是与 §114/§115/§119/§124/§134/§135
 * 完全同一族那件事 —— 「有逻辑，没有门」。把门开上，用最笨的字符串计数把"消费者存在"钉成会红的判据。
 *
 * ## 为什么不是"章节评论屏"
 *
 * 本喵现量过：Android 侧 `JsComicSource` **没有**重写 `loadChapterComments` / `sendChapterComment`
 * （那两枚住在 `ComicSource.kt:125-132` 的接口默认恒回空表），随包几颗源脚本里的 `chapterComments`
 * 只是字段声明、没有任何 Kotlin 桥去调它们 —— 做"章节评论"得到的就是一扇永远念"还没评论"的**假门**，
 * 而本仓明令禁假把手（评审 R6 那一族）。真被引擎桥接、有 15 颗真源支持的是**详情评论**（三档兜底：
 * `s.comic.loadComments` / `s.comments.load` / `s.comments`），桌面默认源 `copy_manga` 就在名单里。
 * 这一族事实由下面的 ⑥ 与 ⑦ 钉住（引擎跳只调 detail 那三档、绝不调 chapter 那两档），别哪天有人
 * 顺手把它"改成章节评论"，源码尺会立刻红，逼着那一层决定重新过一次证据面。
 */
class DesktopCommentsEntryTest {

    private val shellFile = DesktopSourceTree.desktopSourceOf("VeneraDesktopLargeScreen.kt")
    private val shell = DesktopSourceTree.desktopLargeScreenCode()
    private val shellLines = DesktopSourceTree.desktopLargeScreenLines().filter { it.isNotEmpty() }
    private val detail = DesktopSourceTree.codeText(
        DesktopSourceTree.desktopSourceOf("detail/DesktopComicDetailScreen.kt"),
    )
    private val screen = DesktopSourceTree.codeText(
        DesktopSourceTree.desktopSourceOf("detail/DesktopCommentsScreen.kt"),
    )
    private val pure = DesktopSourceTree.codeText(
        DesktopSourceTree.desktopSourceOf("detail/DesktopComicComments.kt"),
    )
    private val route = DesktopSourceTree.codeText(
        DesktopSourceTree.desktopSourceOf("DesktopRoute.kt"),
    )
    private val session = DesktopSourceTree.codeText(DesktopSourceTree.engineProbeSource("EngineSession.kt"))

    /** ① 路由档在（`data class ComicComments`，而不是 `data object` —— 那四样路由参数缺一不可）。 */
    @Test
    fun 路由枚举里有详情评论那一档() {
        assertTrue("DesktopRoute 里少了 ComicComments：", "data class ComicComments(" in route)
        // 四样参数一格都不许省 —— comicId/subId 缺一条评论那一跳就取不到数据；
        // sourceKey 缺一条连 `e.load` 都发不出去；cover 缺一条返回详情就拼不回封面。
        listOf("comicId: String", "subId: String?", "title: String", "sourceKey: String", "cover: String")
            .forEach { field -> assertTrue("路由档少了 $field：", field in route) }
    }

    /** ② 切进评论路由恰一处赋值，且挂在按得到的把手上。 */
    @Test
    fun 切进评论路由那一处挂在按得到的把手上() {
        // 详情页复用共享 ComicDetailScreenBody 后，评论改为校验共享屏 sheet 入口与桌面适配器 loadCommentsPage 实现。
        val body = DesktopSourceTree.codeText(DesktopSourceTree.androidSource("feature/ComicDetailScreen.kt"))
        assertTrue("共享屏里那枚「评论」把手没开 sheet：", "showCommentSheet = true" in body)
        assertTrue("共享屏没经核读评论（loadComments 那条腿断了）：", "viewModel.loadComments(" in body)
        val sourcePort = DesktopSourceTree.codeText(
            DesktopSourceTree.desktopSourceOf("business/DesktopEngineComicSource.kt"),
        )
        assertTrue("桌面源适配器没落实 loadCommentsPage（评论在桌面没接上）：", "override suspend fun loadCommentsPage(" in sourcePort)
    }

    /**
     * ③ 详情页里评论这条链**真接着核**（改口径 · 详情页重建）。
     *
     * 旧稿钉的是旧仿制屏那枚「评论」把手的必传形参。重建后评论改成**屏内评论区**：
     * 进页拉第一页（`core.loadComments`），发送走 `core.sendComment` —— 取数/发送都是
     * 真核调用，不是摆一枚入口钮假装有去处。
     */
    @Test
    fun 详情页评论那条链接着核() {
        assertEquals(
            "进页拉评论那一步没了（评论区就是空壳）：",
            1,
            DesktopSourceTree.codeOccurrences(
                DesktopSourceTree.desktopSourceOf("detail/DesktopComicDetailScreen.kt"),
                "core.loadComments()",
            ),
        )
        assertEquals(
            "评论区「加载更多」没接核（只显示首页就是这条）：",
            1,
            DesktopSourceTree.codeOccurrences(
                DesktopSourceTree.desktopSourceOf("detail/DesktopComicDetailScreen.kt"),
                "core.loadComments(loadMore = true)",
            ),
        )
        assertEquals(
            "发送那一跳没接核（输入框就是假把手）：",
            1,
            DesktopSourceTree.codeOccurrences(
                DesktopSourceTree.desktopSourceOf("detail/DesktopComicDetailScreen.kt"),
                "core.sendComment(",
            ),
        )
    }

    /** ④ 那一屏有出路：`onBack` 必传、壳里那一支把 route 切回 `Detail`。 */
    @Test
    fun 那一屏有出路而返回落回详情那一屏() {
        assertTrue("`onBack` 不是必传形参：", "onBack: () -> Unit," in screen)
        assertTrue("评论屏没有走统一页壳：", "DesktopPageShell(" in screen)
        assertTrue("统一页壳没接返回回调：", "onBack = onBack," in screen)
        val pageShell = DesktopSourceTree.codeText(
            DesktopSourceTree.desktopSourceOf("ui/DesktopPageTemplates.kt"),
        )
        assertTrue("统一页壳没渲那枚返回按钮：", "DesktopBackButton(onClick = onBack)" in pageShell)
        val block = shellLines.dropWhile { "is DesktopRoute.ComicComments -> DesktopCommentsScreen" !in it }.take(20).joinToString("\n")
        assertTrue("壳里那一支的 onBack 没把 route 切回 Detail（评论屏成了单行道）：$block", "route = DesktopRoute.Detail(" in block)
    }

    /** ⑤ 屏只吃形参：零引擎、零 AWT、零协程 —— 那三样全住在壳与 `EngineSession` 里。 */
    @Test
    fun 那一屏零引擎零AWT零协程() {
        listOf(
            "io.github.composefluent" to "又开出第二条平行线",
            "java.awt" to "评论那一屏不该开文件框",
            "EngineSession" to "屏不许自己拿引擎",
            "e.load" to "装载由壳做（一次锁一通路）",
            "comments(" to "评论那一跳由壳的 engineCall 里发（`DesktopCommentsScreen` 里不该出现）",
            "withContext" to "屏里不许有 IO",
            "LaunchedEffect" to "屏里不许有协程",
        ).forEach { (banned, why) ->
            assertEquals(
                "$why：",
                0,
                DesktopSourceTree.codeOccurrences(
                    DesktopSourceTree.desktopSourceOf("detail/DesktopCommentsScreen.kt"),
                    banned,
                ),
            )
        }
    }

    /**
     * ⑥ 评论区**恒摆**，不由源能力收走（改口径 · 详情页重建）。
     *
     * 旧稿钉的是「评论」入口圆钮的邻域形状。重建后评论区是屏内那张卡：副题那行
     * `desktopCommentsHandleLabel(data.commentCount)` 是它的锚 —— **评论区本体**不许包在
     * `if (canSend…)` 里面（对岸那枚把手从不按源支不支持来收，桌面同一口径）；
     * 源能力（`canSendComment`）只许收**发送框**那一格，且恰好一处。
     */
    @Test
    fun 评论区恒摆而源能力只收发送框那一格() {
        val detailLines = DesktopSourceTree.desktopComicDetailLines()
        val idx = detailLines.indexOfFirst { "desktopCommentsHandleLabel(data.commentCount)" in it }
        assertTrue("详情页里没找到评论区那张卡：$idx", idx >= 0)
        // 窗口取 [idx-2, idx+5)（7 行）：副题上面两行若是 `if (data.commentCount` /
        // `if (canSend` 就是被源能力收走的那一族（评审 R6 假开关）。
        val from = (idx - 2).coerceAtLeast(0)
        val neighborhood = detailLines.drop(from).take(7).joinToString("\n")
        assertEquals(
            "评论区被 if 收走那一族绕开不许出现：$neighborhood",
            0,
            Regex("if \\(data\\.commentCount").findAll(neighborhood).count() +
                Regex("if \\(canSend").findAll(neighborhood).count(),
        )
        // 源能力只收发送框：那一格闸门恰好一处（多了就是把评论区本体也收走了）。
        assertEquals(
            "`canSendComment` 的闸门只许落在发送框那一格：",
            1,
            detailLines.count { "if (data.canSendComment) {" in it },
        )
    }

    /** ⑦ 引擎那一跳只调详情评论三档（`comic.loadComments` / `comments.load` / `comments`），**绝不**调"章节评论"那两档。 */
    @Test
    fun 引擎那一跳只走详情评论三档而绝不走章节评论那两档() {
        // 详情评论三档兜底逐字照对岸 JsComicSource.loadCommentsPage（`:1444-1446`）
        listOf("s.comic.loadComments", "s.comments.load", "s.comments.apply").forEach { shape ->
            assertTrue("引擎那一跳少了详情评论兜底档 $shape：", shape in session)
        }
        // 反向：不许出现"章节评论"那两档（对岸 JsComicSource 从来没桥它们；现量已定案）
        listOf("loadChapterComments", "sendChapterComment", "chapterComments").forEach { banned ->
            assertEquals(
                "章节评论那两档不该在桌面引擎桥里出现（对岸零实现，接过来就是假门）$banned：",
                0,
                DesktopSourceTree.codeOccurrences(DesktopSourceTree.engineProbeSource("EngineSession.kt"), banned),
            )
        }
    }

    /** ⑧ 那一屏的取证读数真有调用点（`desktopCommentsScreenReading` 从前是零调用点那一族）。 */
    @Test
    fun 那一屏的读数真有调用点() {
        val sites = DesktopSourceTree.desktopMainGroupedOccurrences("desktopCommentsScreenReading(")
        // 壳里那一颗 effect **三档各打一次**（引擎没起来 / 成功 / 失败）—— 三档在包内日志里必须分得开，
        // 少了任何一档都是"某一类错误没有证人"（§119 那颗历史读数同一族纪律）。件文件里那一处是**声明**。
        assertEquals(
            "读数件的消费者形状变了（期望「件文件一处声明 + 壳里三处打：起手/成功/失败」）：$sites",
            listOf("DesktopComicComments.kt" to 1, "VeneraDesktopLargeScreen.kt" to 3),
            sites,
        )
        val idx = shellLines.indexOfFirst { "LaunchedEffect(route, commentsTick)" in it }
        assertTrue("取数那颗 effect 的键里没带 commentsTick ⇒ 「再读一页」扳一下不会重取：$idx", idx >= 0)
    }

    /** ⑨ 起手状态**不许**预支"该源未提供"那一句（那是问完源之后的结论）。 */
    @Test
    fun 起手状态不许念成未提供评论() {
        // 起手值：isLoading=true, canLoad=false。这两格一旦漂到 loading=false + canLoad=false，
        // `desktopCommentsStatusNote` 立刻会在还没问源之前念「该源未提供评论加载功能」= 假话。
        val idx = pure.indexOf("fun desktopCommentsInitialView")
        assertTrue("找不到起手件：$idx", idx >= 0)
        val body = pure.substring(idx, minOf(pure.length, idx + 500))
        assertTrue("起手件没把 isLoading 置真：$body", "isLoading = true" in body)
        assertTrue("起手件没把 canLoad 置假：$body", "canLoad = false" in body)
        assertTrue("起手件没把 loaded 置假（起手绝不宣布“读过”）：$body", "loaded = false" in body)
    }
}
