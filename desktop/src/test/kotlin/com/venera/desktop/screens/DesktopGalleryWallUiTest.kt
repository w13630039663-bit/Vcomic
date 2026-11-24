package com.venera.desktop.screens

import com.venera.desktop.ui.DesktopWallScaffold
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.compose.gallery.domain.GalleryHomeDomain
import com.venera.desktop.ui.DesktopGalleryWallPane
import com.venera.desktop.gallery.ui.DesktopGalleryBatch
import com.venera.desktop.gallery.ui.DesktopGalleryMorePhase
import com.venera.desktop.gallery.ui.DesktopSourceTree
import com.venera.desktop.gallery.ui.DesktopWallPage
import com.venera.desktop.gallery.ui.DesktopWallCardModel
import com.venera.desktop.gallery.ui.ScriptedBoards
import com.venera.desktop.gallery.ui.fakeGalleryPorts
import com.venera.desktop.gallery.ui.desktopGalleryWallFooter
import com.venera.desktop.gallery.ui.DesktopGallerySort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 画廊那面墙的**页尾那一格真上屏**（离屏语义树）。
 *
 * ## 为什么纯件绿了还要这一颗
 *
 * 那笔账：六颗纯函数在 main 里唯一的 import 者是已拆的 Fluent 平行线 ⇒
 * 算式全对而屏上一行都不渲。`DesktopGalleryWallFooterTest` 量"算得对不对"，这一颗量
 * "真摆上屏、真按得动、按一次恰好落一次"。
 *
 * ## 台架喂的是**原始状态**而不是那三格成品（的台架教训）
 *
 * 那三格（`moreLabel` / `lines` / `reading`）由纯件**现算**，与壳里那一枚 `val` 逐字同形。
 * 台架若直接收写死的成品就绕过了整条链：撤掉判据里任何一条时这里**一条都不会红**。
 * 所以那三格由 [desktopGalleryWallFooter] 现算 —— 与 `DesktopComicSearchUiTest` 的台架同一纪律。
 */
@OptIn(ExperimentalTestApi::class)
class DesktopGalleryWallUiTest {

    private val wide = WideScreenLayoutMode.Expanded

    private fun cards(n: Int) = (1..n).map {
        DesktopWallCardModel(
            title = "第$it 张",
            url = "https://img.invalid/$it.jpg",
            aspect = 1f,
            masked = false,
        )
    }

    // 无头树中懒网格未布局到卡片行，卡片点击接线与门控由 DesktopGalleryPostViewerEntryTest 校验，避免此处误点页尾假绿。

    /** 到货而两腿都还能再发的那一份账（原始状态，不是成品）。 */
    private fun batch(onScreen: Int = 3, atEnd: Boolean = false) = DesktopGalleryBatch(
        seed = 1L,
        pageBySite = mapOf(
            com.venera.compose.gallery.data.GallerySite.YANDERE to 1,
            com.venera.compose.gallery.data.GallerySite.SAFEBOORU to 1,
        ),
        queries = mapOf(
            com.venera.compose.gallery.data.GallerySite.YANDERE to "",
            com.venera.compose.gallery.data.GallerySite.SAFEBOORU to "",
        ),
        posts = (1L..20L).map {
            com.venera.compose.gallery.data.GalleryPost(
                site = if (it % 2 == 0L) com.venera.compose.gallery.data.GallerySite.SAFEBOORU
                else com.venera.compose.gallery.data.GallerySite.YANDERE,
                id = it,
                tags = "t",
                previewUrl = "https://img.invalid/$it.jpg",
                fileExt = "jpg",
            )
        },
        sitesDone = if (atEnd) {
            setOf(
                com.venera.compose.gallery.data.GallerySite.YANDERE,
                com.venera.compose.gallery.data.GallerySite.SAFEBOORU,
            )
        } else {
            emptySet()
        },
    ).let { it to onScreen }

    /**
     * 台架：喂**原始状态**（账本 / 上屏张数 / 相位 / 量词），三格由纯件现算。
     * `onLoadMore` 的落点由调用点现传闭包（藏进默认参数会读到注册那一刻的旧引用）。
     */
    private fun androidx.compose.ui.test.ComposeUiTest.stageWall(
        cards: List<DesktopWallCardModel>,
        batch: DesktopGalleryBatch?,
        onScreen: Int = cards.size,
        phase: DesktopGalleryMorePhase = DesktopGalleryMorePhase.Idle,
        unit: String = "张",
        onLoadMore: () -> Unit = {},
        // 墙上那张卡新添的必传形参。台架递空实现即可 —— 这一把尺测的是把手与页尾那几格，
        // 不测卡可点（那格在无头树里排不到卡，见上面那条注释，交架构源码尺）。
        onOpenPost: (DesktopWallCardModel) -> Unit = {},
        // 那一枚「历史」把手新添的必传形参。台架同样递空实现 —— 这一把尺不测它的落点
        // （那格由 `DesktopGalleryBrowseHistoryEntryTest` 的源码尺 + `DesktopGalleryBrowseHistoryScreenUiTest` 钉）。
        onOpenBrowseHistory: () -> Unit = {},
    ) {
        val footer = desktopGalleryWallFooter(batch, onScreen, phase, unit)
        setContent {
            DesktopGalleryWallPane(
                mode = wide,
                wallPage = batch?.let { DesktopWallPage(batch = it, cards = cards) },
                footer = footer,
                onLoadMore = onLoadMore,
                // 悬浮：顶栏中段换真输入场后的四枚必传形参。台架递默认档与空实现 ——
                // 这一把尺测的是把手与页尾那几格，不测搜索场的落点（发车与落盘在壳层，由源码尺钉）。
                searchR18 = false,
                onPickSearchR18 = {},
                onTopSearch = { _, _ -> },
                pixivSuggestEngine = null,
                onOpenBookmarks = {},
                onOpenBrowseHistory = onOpenBrowseHistory,
                // 那一排「图库站 / Pixiv」新添的两枚必传形参。台架同样递空实现 ——
                // 这一把尺测的是把手与页尾那几格，不测切换器的落点（那格是"切过去换真取数腿"，
                // 在无头树里要的是端口，交壳侧的取数件单测与源码尺钉）。
                domain = GalleryHomeDomain.BOORU,
                onPickDomain = {},
                // 首页那几节横栏新添的五枚必传形参。台架同样递空读数 ——
                // 这一把尺不测横栏（画师/每日热门两节的落点由壳的 effect 与取数件钉，
                // 排行榜那排的档位分派同形）。`dailyCards` 给空表 = 那一节不摆，不干扰本页尾判据。
                followedArtists = null,
                onOpenArtist = {},
                dailyCards = emptyList(),
                rankingMode = com.venera.compose.gallery.data.PixivRankingMode.DAILY,
                onPickRankingMode = {},
                onOpenPost = onOpenPost,
                // pixiv 域那枚「我的 pixiv」入口新添的必传形参。台架递空实现 —— 这一把尺
                // 测的是把手与页尾那几格，不测入口落点（那格在无头树里排不到：它挂在 PIXIV 域那一支）。
                onOpenPixivAccount = {},
                // 阶段二 · 域 2：日榜预览行那枚「查看全部」新添的必传形参。台架递空实现 —— 这一把尺
                // 测的是把手与页尾那几格，那一扇门的接线由 `DesktopGalleryDailyEntryTest` ③ 源码尺钉。
                onOpenDaily = {},
                // BOORU 主墙排序档新添的两枚必传形参。台架递**默认档 + 空实现** ——
                // 这一把尺测的是把手与页尾那几格，不测三档分派（那格由壳首屏那颗 effect 与
                // `desktopWallSortQueries` 那颗纯件钉；档真换腿见 `DesktopWallSortLegsTest`）。
                sort = DesktopGallerySort.LATEST,
                onPickSort = {},
                // 浮动刷新钮新添的必传形参。台架递空实现 —— 这一把尺测的是把手与页尾
                // 那几格，不测刷新落点（那一记 `wallRefreshTick++` 在壳层，无头树里量不到；接线形状由源码尺钉）。
                onRefresh = {},
                // PIXIV 榜单预览条改独立榜单腿后的必传形参。台架递空表（这一把尺
                // 测的是把手与页尾那几格；预览条的取数腿由壳的 effect 与源码尺钉）。
                previewCards = emptyList(),
                // 发现带 / 关注新作 / pixivision 三腿与新动作门。台架全递空
                //（这一把尺不测那几节；取数与接线形状由壳的 effect 与各自源码尺钉）。
                pixivDiscover = com.venera.desktop.ui.PixivDiscoverLeg(
                    users = emptyList(),
                    followedIds = emptySet(),
                    followingCards = emptyList(),
                    followingAvatars = emptyMap(),
                    ready = true,
                ),
                onSearchTag = {},
                onSearchUser = {},
                onTogglePixivFollow = {},
                // 22:31：`onOpenCardArtist` 是另一路未完成改动留下的半截引用
                //（`DesktopGalleryWallPane` 没有该形参），撤掉以恢复编译；画师交接槽
                // 仍由墙件内部按卡自己的站内联兜底。
                pixivisionArticles = emptyList(),
                // 图库站：筛选架四件。台架递默认档与空实现（这一把尺不测筛选）。
                booruRatingFilter = com.venera.desktop.gallery.ui.DesktopGalleryRatingFilter.All,
                onPickBooruRating = {},
                booruSiteFilter = null,
                onPickBooruSite = {},
                // 图库站：标签维度舱与行高密度 / 多选。台架全递空（这一把尺不测那几节）。
                tagDimensions = emptyList(),
                tagDictionaryReady = true,
                wallDensity = com.venera.desktop.gallery.ui.DesktopWallDensity.Standard,
                onPickWallDensity = {},
                wallSelectedUids = emptyList(),
                onToggleWallSelect = {},
                // 图库站：墙布局双档。台架递瀑布流档（这一把尺不测分派）。
                wallLayout = com.venera.compose.gallery.domain.GalleryWallLayout.Staggered,
                onPickWallLayout = {},
                staggeredState = androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState(),
                onClearWallSelect = {},
                onSetWallSelection = {},
                onOpenPixivisionArticle = {},
                // 那面墙新添的必传形参（滚动状态住在壳那层）。行对齐自绘后
                // 类型换 LazyListState（台架在 setContent 里现造一颗即可 —— 这一把尺测的是把手与页尾
                // 那几格，不测滚动是否跨 route 存活；那格交源码尺 + §130/132 那族"接线形状"尺钉）。
                gridState = androidx.compose.foundation.lazy.rememberLazyListState(),
                // 共享墙的词典腿只在这把尺里走空板，避免无头测试碰真库/真网络。
                ports = fakeGalleryPorts(ScriptedBoards(emptyMap(), emptyMap())),
            )
        }
    }

    /** ① 那一枚把手真上屏而按一次恰好落一次（"摆着而按不动"= 评审 R6 那一族假开关）。 */
    @Test
    fun 那一枚把手上屏而按一次恰好落一次() {
        var taps = 0
        runComposeUiTest {
            val (b, n) = batch(onScreen = 3)
            stageWall(cards(3), b, onScreen = n, onLoadMore = { taps++ })
            // 瀑布流卡高：页尾把手在视口外（离屏 item 不进语义树）——先滚到底再点。
            onNode(hasScrollToIndexAction()).performScrollToIndex(4)
            waitForIdle()
            onNode(hasTextExactly("下一页（已取 3 张）") and hasClickAction()).performClick()
        }
        assertEquals("「下一页」按一次落两次或不落：", 1, taps)
    }

    /** ② 还没到货（账本为 null）时那一格什么都不摆（撤掉判据里那一枚 `batch != null` 会红在这里）。 */
    @Test
    fun 还没到货时那一格整颗不摆() {
        runComposeUiTest {
            stageWall(cards(3), null)
            onAllNodesWithText("下一页（已取 3 张）").assertCountEquals(0)
        }
        runComposeUiTest {
            // 一笔都没发出去那一档同理（`queries` 空 ⇒ 发了等于没发）。
            stageWall(cards(3), DesktopGalleryBatch(seed = 1L))
            onAllNodesWithText("下一页（已取 3 张）").assertCountEquals(0)
        }
    }

    /** ③ 到底那一档不摆把手而那句上屏；失败那一档把手照摆而成因上屏。 */
    @Test
    fun 到底那句上屏而失败那一档把手照摆() {
        var taps = 0
        runComposeUiTest {
            val (b, n) = batch(onScreen = 3, atEnd = true)
            stageWall(cards(3), b, onScreen = n)
            onNode(hasScrollToIndexAction()).performScrollToIndex(4)
            waitForIdle()
            onNodeWithText("没有更多：yande.re、Safebooru")
                .assertExists()
            onAllNodesWithText("下一页（已取 3 张）").assertCountEquals(0)
        }
        runComposeUiTest {
            val (b, n) = batch(onScreen = 3)
            stageWall(
                cards(3),
                b,
                onScreen = n,
                phase = DesktopGalleryMorePhase.Failed("超过 12s 没返回"),
                onLoadMore = { taps++ },
            )
            onNode(hasScrollToIndexAction()).performScrollToIndex(4)
            waitForIdle()
            // 改口径：屏上念归档短句（屏上不透原串）。
            onNodeWithText("加载失败：网络超时；已显示内容保留。").assertExists()
            // 把手照摆且按得动（撤掉它用户就没有重试的路 —— 那一条在纯件 ④ 里，这里量屏真照做）。
            println(
                "D_失败档 scrollIdx=" + onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes().size +
                    " scroll=" + onAllNodes(hasScrollAction()).fetchSemanticsNodes().size +
                    " 下一页=" + onAllNodesWithText("下一页（已取 3 张）").fetchSemanticsNodes().size,
            )
            onNode(hasTextExactly("下一页（已取 3 张）") and hasClickAction()).performClick()
        }
        assertEquals("失败那一档的把手按一次落一次：", 1, taps)
    }

    /**
     * ④ 那一句数的是**上屏**那些张而不是**取回**那些（两个主语，屏不许塌成一个）。
     *
     * 台架把 `batch.posts`（20 张取回）与 `onScreen`（3 张上屏）各交一份，
     * 谁把页尾那句改成数 `batch.posts.size`，这里会数不到那三张而红。
     */
    @Test
    fun 那一枚把手数上屏的那些而不数取回的那些() {
        val (b, n) = batch(onScreen = 3)
        assertEquals("夹具自己先要对得上（否则下面那条恒真）：", 20, b.posts.size)
        runComposeUiTest {
            stageWall(cards(3), b, onScreen = n)
            onNodeWithText("下一页（已取 3 张）").assertExists()
            onAllNodesWithText("下一页（已取 20 张）").assertCountEquals(0)
        }
    }

    /** ⑤ 缺席那句上屏：只点名站与短原因，不回显原串。 */
    @Test
    fun 缺席那两句各念各的而不互相冒充() {
        runComposeUiTest {
            stageWall(
                cards(3),
                DesktopGalleryBatch(
                    seed = 1L,
                    queries = mapOf(com.venera.compose.gallery.data.GallerySite.YANDERE to ""),
                    failures = mapOf(
                        com.venera.compose.gallery.data.GallerySite.YANDERE to "超过 12s 没返回",
                        com.venera.compose.gallery.data.GallerySite.GELBOORU to "没配账号",
                    ),
                ),
                onScreen = 3,
            )
            onNodeWithText("yande.re·网络超时；Gelbooru·未登录").assertExists()
        }
    }

    /** ⑥ 空墙而账本说还发得动时那一枚把手照摆（空屏最该给出路，撤掉就是死屏）。 */
    @Test
    fun 空墙时那一枚把手还在() {
        var taps = 0
        runComposeUiTest {
            val b = DesktopGalleryBatch(
                seed = 1L,
                queries = mapOf(com.venera.compose.gallery.data.GallerySite.YANDERE to ""),
            )
            stageWall(cards(0), b, onScreen = 0, onLoadMore = { taps++ })
            onNodeWithText("暂无内容").assertExists()
            onNode(hasTextExactly("下一页（已取 0 张）") and hasClickAction()).performClick()
        }
        assertEquals("空墙上那枚把手按一次落一次：", 1, taps)
    }

    /**
     * ⑦ 屏**不自己判、不自己拼**：那三格只许从纯件进来（⑦ 同一把尺）。
     *
     * 这里扫的是屏所在那颗文件的**代码行**：屏里出现 `desktopGalleryWallFooter(` 就是判据搬进了 UI，
     * 出现 `.atEnd` 就是屏自己拼了一次"到底没"（两处各算一定漂，而漂成的样子是把手与那句互相冒充）。
     */
    @Test
    fun 屏里不许出现那三格的算式() {
        // 批 4：屏段已拆到成员文件，按大屏成员表合并读。
        val shell = DesktopSourceTree.desktopLargeScreenCode()
        // 屏那一段 = `DesktopGalleryWallPane(` 起到文件尾（那两颗 composable + 那枚把手）。
        val screen = shell.substringAfter("internal fun DesktopGalleryWallPane(")
        assertTrue("对照组失效：没读到那两颗屏（扫空了会恒真）：" + screen.length, screen.length > 400)
        // 负向：屏里出现任何一种"自己算"的形状都算违例（一次列全，不短路）。
        val banned = listOf(
            "atEnd" to "屏自己拼了一次「到底没」—— 那一判归 DesktopGalleryBatch（两处各算一定漂）",
            "queries" to "屏自己数了发出去几腿 —— 页尾那句的主语归那颗件",
            "desktopGalleryWallFooter(" to "判据搬进屏里了 —— 屏只搬成品",
            "desktopGalleryMoreLabel(" to "那枚把手的文案被屏另拼了一遍",
            "desktopMissingReading(" to "那句缺席被屏另拼了一遍",
            "desktopExhaustedReading(" to "那句到底被屏另拼了一遍",
        )
        val offenders = banned.filter { (token, _) -> token in screen }.map { (token, why) -> "$token ⇒ $why" }
        assertEquals("那面墙的屏里自己算了这几样：\n${offenders.joinToString("\n")}", emptyList<String>(), offenders)
        // 正向：那三格与那枚把手**真被消费**（只接参数不摆 = 假接线，`DesktopCorruptNoticeTest` ③ 同族）。
        assertTrue("那一格没把 footer 递给 scaffold（接了参数而不消费）：", screen.contains("footer = footer"))
        // scaffold 里自动续页那格判据吃成品 moreLabel。
        assertTrue("近尾自动续页没吃成品 moreLabel：", screen.contains("footer.moreLabel != null"))
        assertTrue("页尾那几句没真摆出来：", screen.contains("footer.lines.forEach"))
        assertTrue("那枚把手没吃到 onLoadMore：", screen.contains("onLoadMore = onLoadMore"))
    }

    /**
     * ⑧ 画廊首页必须整页同滚：hero、chrome、画师/每日热门与页尾说明都住在共享页
     * `GalleryForYouPage` 的 header 槽里，而不是钉在瀑布流外面。
     *
     * 这一条防的是"看起来只改了布局，实际又回到上面固定、只有下面能滚"：
     * 旧形状里 `DesktopGalleryHero(` 出现在 `GalleryForYouPage(` 之前；现在它必须出现在
     * `hero = {` 之后，且同一 header 里还要能看到 `footer.lines.forEach`。
     */
    @Test
    fun 画廊首页整页共用一个滚动轴() {
        // 批 4：屏段已拆到成员文件，按大屏成员表合并读。
        val shell = DesktopSourceTree.desktopLargeScreenCode()
        val scaffold = shell.substringAfter("internal fun DesktopWallScaffold(")
        assertTrue("对照组失效：没读到 DesktopWallScaffold", scaffold.length > 800)
        // 图库主墙使用 DesktopJustifiedWall 并将 wallHeader 传入 header 槽实现整页同滚。
        val page = scaffold.substringAfter("val wallHeader: @Composable () -> Unit = {")
        val heroSlot = page.indexOf("header = wallHeader,")
        // 232dp 的 `DesktopGalleryHero` 横幅退役，顶栏工具条
        // `DesktopGalleryChromeRow`（源段 / 满宽搜索 / 刷新 / 主钮）直接领头 —— 同滚断言换它钉。
                val topbar = page.indexOf("DesktopGalleryChromeRow(")
        val dispatch = page.indexOf("header = wallHeader,")
        val footerLines = page.indexOf("footer.lines.forEach")
        assertTrue("行对齐墙没有 header 槽：", heroSlot >= 0)
        assertTrue("顶栏不在壳里：", topbar > 0)
        assertTrue("页尾说明不在壳里：", footerLines > 0)
        assertTrue("顶栏不在 header 槽内容里（wallHeader 定义内）：", topbar > 0 && topbar < dispatch)
        assertTrue("页尾说明不在 header 槽内容里：", footerLines > topbar && footerLines < dispatch)
        // Grid 与 Staggered 两档均使用桌面自绘墙组件并共用同一 wallHeader（恰两处引用）。
        assertEquals(
            "两档布局的 header 引用该恰一处（Grid 档吃 header 槽）：",
            1,
            DesktopSourceTree.codeOccurrences(scaffoldFile(), "header = wallHeader,"),
        )
        assertEquals(
            "两档布局的 header 引用该恰一处（Staggered 档吃 header 槽）：",
            1,
            DesktopSourceTree.codeOccurrences(scaffoldFile(), "header = { wallHeader() }"),
        )
        assertEquals(
            "hero 退役后不许有残屏（现在是 ${DesktopSourceTree.codeOccurrences(scaffoldFile(), "DesktopGalleryHero(")} 处）：",
            0,
            DesktopSourceTree.codeOccurrences(scaffoldFile(), "DesktopGalleryHero("),
        )
        assertEquals(
            "顶栏只许有一处上屏：",
            1,
            DesktopSourceTree.codeOccurrences(scaffoldFile(), "DesktopGalleryChromeRow("),
        )
    }

    /** ⑨ 桌面画廊统一 14dp 列/行间距，卡片信息栏走「标题 + 站源 + 画师」同一形。 */
    @Test
    fun 画廊网格间距与卡片信息栏统一() {
        val scaffold = DesktopSourceTree.codeText(scaffoldFile()).substringAfter("internal fun DesktopWallScaffold(")
        // 图库站改口径：瀑布流换行对齐自绘 —— 14dp 单源由 DesktopJustifiedWall
        // 的 `gap` 形参 / 常量钉（行间与卡间都是那同一枚 token）。
        val wall = DesktopSourceTree.codeText(
            DesktopSourceTree.desktopSourceOf("gallery/ui/DesktopJustifiedWall.kt"),
        )
        val card = DesktopSourceTree.codeText(
            DesktopSourceTree.desktopSourceOf("gallery/ui/DesktopGalleryCard.kt"),
        )
        // 列距改吃统一规格的密度（DesktopWallSpec.wallGap），三档 10/16/24dp；
        // 宽松档仍取 DesktopGalleryMetrics.wallGridGapLoose 那一枚 token。
        assertTrue(
            "行对齐墙的间距没走统一规格的 wallGap：",
            wall.contains("wallSpec.wallGap"),
        )
        assertTrue(
            "统一规格的宽松档没接 wallGridGapLoose token：",
            DesktopSourceTree.codeText(
                DesktopSourceTree.desktopSourceOf("gallery/ui/DesktopWallSpec.kt"),
            ).contains("DesktopGalleryMetrics.wallGridGapLoose"),
        )
        // 卡片形状收进唯一卡 DesktopGalleryCard.kt，信息栏随卡走 ——
        // 这几格改扫唯一卡的源（行对齐墙只保留装行与列数接线）。
        assertTrue("没有接桌面统一信息栏：", card.contains("DesktopGalleryCardInfo("))
        assertTrue(
            "信息栏没接画师头像腿：",
            card.contains("avatarUrl = avatarUrl"),
        )
        // 等宽网格档采用 1:1 方图与横图跨双格布局，替代原行对齐贪心算法。
        assertTrue(
            "等宽网格的装行纯件不在（横图双格靠它）：",
            wall.contains("desktopGridRows(cards, columns)"),
        )
        assertTrue(
            "横图没有跨双格（weight(2) 不在）：",
            wall.contains("Modifier.weight(span.toFloat())"),
        )
        // 比例由唯一卡的 `aspect` 形参收，Grid 档调用点递 1f/2f。
        assertTrue(
            "Grid 档没把 1:1 / 2:1 比例递给唯一卡：",
            wall.contains("aspect = if (span == 2) 2f else 1f"),
        )
        assertTrue(
            "唯一卡没按 aspect 形参定封面比例：",
            card.contains(".aspectRatio(aspect)"),
        )
        assertTrue("每日热门预览行没跟主墙共用列数：", scaffold.contains("dailyColumnCount = columns"))

        val info = DesktopSourceTree.codeText(
            DesktopSourceTree.desktopSourceOf("gallery/ui/DesktopGalleryCardInfo.kt"),
        )
        assertTrue("信息栏没有站源：", info.contains("post.site.displayName"))
        assertTrue("信息栏没有画师：", info.contains("post.author"))
        assertTrue("信息栏没有标题：", info.contains("displayTitle"))
    }

    /**
     * ⑩ 每日热门那一节改横向滚带。
     *
     * 旧形状是"跟主墙同列数的两排小瀑布"（`desktopDailyMasonryColumns` 那颗纯件），
     * 稿把它换成一根 LazyRow 滚带 + 左右两枚圆钮 + 一根进度条。这一条源码尺钉新形状：
     *  ① 那一节仍吃 `scaffold` 递来的 `dailyColumnCount`（一屏摆几张 = 主墙列数）；
     *  ② 卡仍走 `DesktopGalleryCardInfo` 那一颗统一信息栏（不另画一份）；
     *  ③ 滚带用 `LazyRow` + `rememberLazyListState`（圆钮的逐张滚依赖它）。
     */
    @Test
    fun 每日热门改横向滚带仍吃主墙列数与统一信息栏() {
        val rows = DesktopSourceTree.codeText(
            DesktopSourceTree.desktopSourceOf("gallery/ui/DesktopGalleryHomeRows.kt"),
        )
        assertTrue("滚带不是 LazyRow：", rows.contains("LazyRow("))
        assertTrue("没有为逐张滚准备的列表状态：", rows.contains("rememberLazyListState"))
        // 一屏摆几张仍连回主墙列数（scaffold 那句 `dailyColumnCount = columns` 的下游）。
        assertTrue("横带没接到主墙列数：", rows.contains("visibleCount = dailyColumnCount"))
        assertTrue("横带没接统一信息栏：", rows.contains("DesktopGalleryCardInfo("))
        // 打码卡点不开（openable 闸从旧的瀑布搬进横带，A 条口径不松动）。
        assertTrue("横带没守住 openable 闸：", rows.contains("clickable(enabled = card.openable)"))
    }

    /** 卡片跳位两刀的判据：尾槽恒占测量（悬停不再牵动格子重排）+ 入场动画滚动回收后不重放。 */
    @Test
    fun 卡片尾槽恒占测量且入场不重放() {
        val card = DesktopSourceTree.desktopUiSource("DesktopGalleryCard.kt")
        assertEquals(
            "尾槽外层 Box 必须带恒定尺寸——悬停显隐只许动 AnimatedVisibility，不许牵动格子测量（现在是 " +
                "${DesktopSourceTree.codeOccurrences(card, "Box(modifier = Modifier.size(22.dp)) {")} 处）：",
            1,
            DesktopSourceTree.codeOccurrences(card, "Box(modifier = Modifier.size(22.dp)) {"),
        )
        // 标准入场已单源进 VeneraMotion：防重放标记只住在工具里，卡片本体只许吃工具。
        val motion = DesktopSourceTree.androidSource("ui/tokens/VeneraMotion.kt")
        assertEquals(
            "防重放标记必须单源住在 VeneraMotion（现在是 " +
                "${DesktopSourceTree.codeOccurrences(motion, "rememberSaveable(cardKey) { mutableStateOf(false) }")} 处）：",
            1,
            DesktopSourceTree.codeOccurrences(motion, "rememberSaveable(cardKey) { mutableStateOf(false) }"),
        )
        assertEquals(
            "卡片入场必须吃标准工具 rememberCardEnterProgress（现在是 " +
                "${DesktopSourceTree.codeOccurrences(card, "rememberCardEnterProgress(index, cardKey)")} 处）：",
            1,
            DesktopSourceTree.codeOccurrences(card, "rememberCardEnterProgress(index, cardKey)"),
        )
    }

    private fun scaffoldFile() = DesktopSourceTree.desktopSourceOf("ui/DesktopWallScaffold.kt")
}
