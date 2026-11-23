package com.venera.desktop.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.desktop.gallery.ui.DesktopSourceTree
import com.venera.desktop.search.DesktopComicSearchMore
import com.venera.desktop.search.desktopComicSearchEmptyNote
import com.venera.desktop.search.desktopComicSearchFailedFooter
import com.venera.desktop.search.desktopComicSearchHistoryChips
import com.venera.desktop.search.desktopComicSearchMoreState
import com.venera.desktop.search.DesktopSearchScreen
import com.venera.desktop.search.DESKTOP_COMIC_SEARCH_END_LABEL
import com.venera.desktop.search.DESKTOP_COMIC_SEARCH_LOADING_LABEL
import com.venera.desktop.search.DESKTOP_COMIC_SEARCH_MORE_LABEL
import com.venera.desktop.search.DESKTOP_COMIC_SEARCH_RETRY_LABEL
import com.venera.engineprobe.EngineSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 漫画搜索那一屏的**最近搜索**与**翻页那四档**（离屏语义树）。
 *
 * ## 这一层为什么不能只靠纯函数
 *
 * 那笔账：六颗纯函数绿着而屏上一行都不渲（唯一的 import 者是已拆的 Fluent 平行线）。
 * 纯件判据量的是"算得对不对"，这一颗量的是"真摆上屏、真按得动、按一次恰好落一次"。
 *
 * ## 台架的三格事实（与 `DesktopNewScreensSemanticsTest` 同一族，写在这儿省得再量）
 *
 *  - `onNodeWithText` 默认**子串**匹配：「加载更多」与「正在加载更多…」互为子串 ⇒
 *    挑那一枚把手一律用 `hasTextExactly and hasClickAction`（④ 反向钉这件事）；
 *  - 一颗测试体里只能有**一层** `runComposeUiTest`（嵌套两只 `runTest` 会把未捕获异常
 *    打到下一颗不相干的测试上，见 `DesktopAccentFillTest` 那笔莫名红）；
 *  - `enabled = false` 时 `performClick` 落不到回调 ⇒ 本文件只用前者。
 *
 * ## 判据要挡的三种坏写法
 *
 * 1. 那一排摆出来而按不动（评审 R6 那一族假开关）⇒ ①② 各按一次数落点；
 * 2. 有结果时那一排还挡在墙前面 ⇒ ③ 钉"结果来了整格让位"；
 * 3. 把**我们的失败**念成**站方的空结果** ⇒ ⑤⑥ 是这一批真正修的那格。
 */
@OptIn(ExperimentalTestApi::class)
class DesktopComicSearchUiTest {

    private val wide = WideScreenLayoutMode.Expanded

    private fun cards(n: Int) = (1..n).map {
        EngineSession.ComicCard("第${it}本", "c$it", "https://cover.invalid/$it.jpg")
    }

    @Composable
    private fun Stage(content: @Composable () -> Unit) {
        // 搜索空态的待机吉祥物每帧一跳，组合永不静止 ⇒ waitForIdle 等满整段超时；
        // 台架全批挂静止档，断言不需要动画跑着。
        androidx.compose.runtime.CompositionLocalProvider(
            com.venera.desktop.ui.components.LocalMascotStill provides true,
        ) {
            Box(Modifier.fillMaxSize()) { content() }
        }
    }

    /**
     * 台架。⚠️ 每一颗用例**显式**传自己要断言的那一路，其余走这里的默认值：
     * 默认值只服务于"这一颗不关心那一格"，而"断言落点"那一枚计数器（`picked` / `taps`）
     * 必须由调用点现传闭包 —— 记过：把要断言的那一路藏进默认参数，
     * 第二次读到的是注册那一刻的旧引用，读数会恒为 0 而测试照样绿。
     *
     * ## 台架喂的是**原始状态**而不是那四格成品（要点，扰动实验逼出来的）
     *
     * 那四格（`historyChips` / `moreState` / `failedFooter` / `emptyNote`）由那两颗纯件
     * **现算**，与壳那一层逐字同形。台架若直接收写死的成品，就绕过了整条链：
     * 「输入框有字 ⇒ 那一排让位」「有结果 ⇒ 中间那一格闭嘴」「成因只上一处」三类
     * 负向断言会**恒真**。第一版就是那么假绿的：把成因那一档改成 `reason != null -> null`
     * 之后，纯件那条红了而**八条 UI 一条都没红**（读数见台账 §123 扰动那一格）。
     */
    private fun androidx.compose.ui.test.ComposeUiTest.stageSearch(
        query: String = "猫",
        results: List<EngineSession.ComicCard> = emptyList(),
        searching: Boolean = false,
        /** 屏顶那枚 `note` 今天**只**吃"那一排记不住"那一格（成因不进那一格，见纯件 ⑤）。 */
        historyNote: String? = null,
        history: List<String> = emptyList(),
        onPickHistory: (String) -> Unit = {},
        onClearHistory: () -> Unit = {},
        loadingMore: Boolean = false,
        canLoadMore: Boolean = false,
        searched: Boolean = true,
        /** 这一轮的成因（塌了 / 翻页塌了 / 站方信封那句 `__err`）。 */
        cause: String? = null,
        onLoadMore: () -> Unit = {},
        // 源选择器那三枚：默认空表 = 那一排不摆，旧用例一条件都不碰它；
        // 测那一排的用例自己显式递 sourceOptions / onPickSource。
        sourceOptions: List<com.venera.desktop.search.DesktopSearchSourceOption> = emptyList(),
        onPickSource: (String) -> Unit = {},
        /** 下拉用的完整历史；默认取同一份，测"有结果仍看得见历史"时显式递满。 */
        dropdownHistory: List<String> = history,
    ) {
        // 与壳同形的四格现算：这四行就是 `VeneraDesktopLargeScreen.kt` 里那四颗 `val` 的形状，
        // 判据一颗都不在这里重述（这里只搬运，与屏同一纪律）。
        val moreState = desktopComicSearchMoreState(
            loadingMore = loadingMore,
            canLoadMore = canLoadMore,
            hasSearched = searched,
            resultsEmpty = results.isEmpty(),
            reason = cause,
        )
        setContent {
            Stage {
                DesktopSearchScreen(
                    mode = wide,
                    sourceKey = "jm",
                    sourceLabel = "禁用漫",
                    sourceOptions = sourceOptions,
                    onPickSource = onPickSource,
                    query = query,
                    onQueryChange = {},
                    onSearch = {},
                    results = results,
                    searching = searching,
                    note = historyNote,
                    maskedOf = { false },
                    onOpen = {},
                    historyChips = desktopComicSearchHistoryChips(
                        history = history,
                        query = query,
                        searching = searching,
                        resultCount = results.size,
                    ),
                    onPickHistory = onPickHistory,
                    onClearHistory = onClearHistory,
                    dropdownHistory = dropdownHistory,
                    moreState = moreState,
                    onLoadMore = onLoadMore,
                    failedFooter = if (moreState == DesktopComicSearchMore.Failed) {
                        desktopComicSearchFailedFooter(cause)
                    } else {
                        null
                    },
                    emptyNote = desktopComicSearchEmptyNote(
                        query = query.trim(),
                        sourceKey = "jm",
                        searched = searched,
                        searching = searching,
                        resultCount = results.size,
                        reason = cause,
                    ),
                )
            }
        }
    }

    /** ① 那一排上屏，而点某一枚交出的是**那一枚的词**。 */
    @Test
    fun 最近搜索那一排上屏而点某一枚交出那一枚的词() {
        var picked: List<String> = emptyList()
        runComposeUiTest {
            stageSearch(
                query = "",
                history = listOf("犬", "猫"),
                onPickHistory = { picked += it },
                searched = false,
            )
            onNodeWithText("最近搜索").assertExists()
            // 还没发过车时那一句是"请输入"而**不是**"没搜到"（另一颗假话，纯件 ④ 钉算法，这里钉上屏）。
            onNodeWithText("输入书名或作者，回车开始搜。").assertExists()
            // 两枚都要按得动（"摆着而按不动"= 评审 R6 那一族）。
            onNode(hasTextExactly("犬") and hasClickAction()).performClick()
            onNode(hasTextExactly("猫") and hasClickAction()).performClick()
        }
        assertEquals("按两枚落两次而词各归各：", listOf("犬", "猫"), picked)
    }

    /** ② 「清空」那枚按一次恰好落一次。 */
    @Test
    fun 清空那枚按一次恰好落一次() {
        var taps = 0
        runComposeUiTest {
            stageSearch(query = "", history = listOf("猫"), onClearHistory = { taps++ })
            onNode(hasTextExactly("清空")).performClick()
        }
        assertEquals("「清空」按一次落两次或不落：", 1, taps)
    }

    /** ③ 结果来了那一排让位；输入框有字时那一排不摆（那是纯件判的，这里量"屏真照做"）。 */
    @Test
    fun 结果来了那一排让位而标题也不摆() {
        runComposeUiTest {
            stageSearch(query = "", results = cards(2), history = listOf("猫"))
            onNodeWithText("第1本").assertExists()
            assertFalse(
                "有结果时那一排还挡在墙前面：",
                runCatching { onNodeWithText("最近搜索").assertExists() }.isSuccess,
            )
        }
        runComposeUiTest {
            // 有历史而输入框有字 ⇒ 台架把那份表交给纯件现算 ⇒ 那一排整格消失。
            stageSearch(query = "猫", history = listOf("猫"))
            assertFalse(
                "输入框有字时那一排还在（敲第二个字会把要点的那枚抽走，那正是对岸那一判的理由）：",
                runCatching { onNodeWithText("最近搜索").assertExists() }.isSuccess,
            )
        }
    }

    /** ④ 页尾那四档各念一句；主人口径改判：Button 态**不摆钮**只念读数（近尾自动续）。 */
    @Test
    fun 页尾那四档各念一句而把手按得动() {
        var taps = 0
        runComposeUiTest {
            stageSearch(
                results = cards(3),
                canLoadMore = true,
                onLoadMore = { taps++ },
            )
            // 子串匹配下「加载更多」会命中「正在加载更多…」⇒ 必须数枚数而不是只 assertExists。
            // Button 态现在只念「加载更多 · 继续下滑自动加载」这句读数，扳机归近尾观察器 ——
            // 屏上不该再有按得动的翻页钮（留一枚就是假开关：观察器已经也在扳）。
            onAllNodesWithText(DESKTOP_COMIC_SEARCH_MORE_LABEL, substring = true).assertCountEquals(1)
            onAllNodes(hasText(DESKTOP_COMIC_SEARCH_MORE_LABEL) and hasClickAction()).assertCountEquals(0)
        }
        assertEquals("Button 态不该有按得动的钮（自动续页了）：", 0, taps)

        runComposeUiTest {
            stageSearch(results = cards(3))
            onNodeWithText(DESKTOP_COMIC_SEARCH_END_LABEL).assertExists()
            // 到底那一档不许同时留一枚按得动的把手（"到底了而下面还能点"就是那一枚假开关）。
            onAllNodesWithText(DESKTOP_COMIC_SEARCH_MORE_LABEL).assertCountEquals(0)
        }
        runComposeUiTest {
            stageSearch(results = cards(3), loadingMore = true, canLoadMore = true)
            onNodeWithText(DESKTOP_COMIC_SEARCH_LOADING_LABEL).assertExists()
        }
        runComposeUiTest {
            // 还没发过车 ⇒ 那一格什么都不摆（那句"到底了"没资格出现）。
            stageSearch(results = cards(3), searched = false)
            onAllNodesWithText(DESKTOP_COMIC_SEARCH_END_LABEL).assertCountEquals(0)
            onAllNodesWithText(DESKTOP_COMIC_SEARCH_RETRY_LABEL).assertCountEquals(0)
        }
    }

    /** ⑤ 失败那一档：成因上屏 + 一枚「再搜一次」按得动。 */
    @Test
    fun 失败那一档成因上屏而那一枚再搜一次按得动() {
        var taps = 0
        runComposeUiTest {
            stageSearch(
                results = cards(3),
                cause = "该源没有 search",
                onLoadMore = { taps++ },
            )
            onNodeWithText("这一页没翻过来：该源没有 search").assertExists()
            // 有结果时那句成因**只**在页尾那一格：屏顶那行与中间那一格都不许念它（念两处 = 让人以为坏了两次）。
            // ⚠️ 这里必须拿**整句**数：`onAllNodesWithText` 默认全等匹配，拿半句数会恒数到 0 而假绿。
            onAllNodesWithText("这一页没翻过来：该源没有 search").assertCountEquals(1)
            onAllNodesWithText("该源没有 search").assertCountEquals(0)
            onNode(hasTextExactly(DESKTOP_COMIC_SEARCH_RETRY_LABEL) and hasClickAction()).performClick()
        }
        assertEquals("「再搜一次」按一次落一次：", 1, taps)
    }

    /**
     * ⑥ 这一批真正修的那格：**成因不许冒充"没搜到"**。
     *
     * 屏上有过两句话时（顶部念落盘成因、中间念这一趟成因）必须各念各的；
     * 而只有成因时那句「「猫」在「jm」里没搜到。」一个字都不许出现。
     */
    @Test
    fun 有成因时不许念成没搜到() {
        runComposeUiTest {
            stageSearch(query = "猫", cause = "该源没有 search")
            onNodeWithText("该源没有 search").assertExists()
            assertFalse(
                "把我们的失败说成站方的空结果（那一档从前就这么念）：",
                runCatching { onNodeWithText("「猫」在「jm」里没搜到。").assertExists() }.isSuccess,
            )
        }
        // 对照：同一枚输入去掉成因 ⇒ 那一档才念"没搜到"。少了这条对照，上面那句负向断言
        // 在"那一格恒不摆"的坏实现下也会绿（扰动实验就是这么抓到台架喂错对象的）。
        runComposeUiTest {
            stageSearch(query = "猫")
            onNodeWithText("「猫」在「jm」里没搜到。").assertExists()
        }
    }

    /** ⑦ 中间那一格只搬成品：那句原话上屏而一个字都不改，且屏不再自己写那三句。 */
    @Test
    fun 中间那一格只搬成品而不重写措辞() {
        runComposeUiTest {
            stageSearch(query = "猫")
            onNodeWithText("「猫」在「jm」里没搜到。").assertExists()
        }
        runComposeUiTest {
            stageSearch(query = "猫", searching = true)
            onNodeWithText("正在搜索…").assertExists()
            // 发车时中间那一格闭嘴（那句"没搜到"与"正在搜索"同屏是另一格假话）。
            onAllNodesWithText("「猫」在「jm」里没搜到。").assertCountEquals(0)
            // 发车时那一排也必须让位（纯件判过，这里量屏真照做）。
            onAllNodesWithText("最近搜索").assertCountEquals(0)
        }
        // 对照：那三句成品话的唯一出处是那两颗件，屏里零处硬写（写了就是两个出处）。
        val screen = DesktopSourceTree.codeText(
            DesktopSourceTree.desktopSourceOf("search/DesktopSearchScreen.kt"),
        )
        listOf("没搜到。", "输入书名或作者").forEach { needle ->
            assertFalse("屏里自己写了一句「$needle」（判据与措辞只许有一处出处）：", needle in screen)
        }
        // 反向对照：那两句确在那颗成品件里（被挪进屏时上面那条会假绿）。
        val own = DesktopSourceTree.codeText(
            DesktopSourceTree.desktopSourceOf("search/DesktopComicSearchOutcome.kt"),
        )
        assertTrue(
            "对照组失效：那两句不在成品件里（挪去别处了？上面那条负向判式就没对象）：",
            "没搜到。" in own && "输入书名或作者" in own,
        )
        assertTrue("屏没搬那两枚成品的常量：", "DESKTOP_COMIC_SEARCH_HISTORY_TITLE" in screen)
    }

    /** ⑧ 空表时那一排整格不摆（连标题也不留一行「最近搜索」孤句）。 */
    @Test
    fun 没历史时那一排整格不摆而不留孤句() {
        runComposeUiTest {
            stageSearch(query = "", history = emptyList(), searched = false)
            onAllNodesWithText("最近搜索").assertCountEquals(0)
            onAllNodesWithText("清空").assertCountEquals(0)
            onNodeWithText("输入书名或作者，回车开始搜。").assertExists()
        }
    }

    /** ⑩ 结果已上屏时那一排收起，但聚焦检索场仍弹得出历史（搜索完再点框要看得见历史）。 */
    @Test
    fun 结果已出聚焦检索场仍弹得出历史() {
        runComposeUiTest {
            stageSearch(
                query = "犬",
                results = cards(2),
                history = listOf("猫", "鸟"),
                searched = true,
            )
            onAllNodes(hasSetTextAction())[0].performClick()
            onNodeWithText("最近搜索").assertExists()
            onNodeWithText("猫").assertExists()
            onNodeWithText("鸟").assertExists()
            onNodeWithText("第1本").assertExists()
        }
    }

    /** ⑨ 屏顶那一行只念"那一排记不住"，永远不偷念这一趟的成因（两处各一句会读成坏了两次）。 */
    @Test
    fun 屏顶那一行只念落盘成因而不与那一趟的成因撞() {
        runComposeUiTest {
            stageSearch(
                query = "猫",
                historyNote = "历史记录不可用",
                cause = "该源没有 search",
                searched = false,
            )
            // 两句说的是**不同的塌**（一层是那一排的落盘，一层是这一趟的发车），所以两句都在、各一枚。
            onAllNodesWithText("历史记录不可用").assertCountEquals(1)
            onAllNodesWithText("该源没有 search").assertCountEquals(1)
            assertFalse(
                "落盘那一句被折成了「没搜到」（两件不同的事塌成一句 = 本仓那一族）：",
                runCatching { onNodeWithText("「猫」在「jm」里没搜到。").assertExists() }.isSuccess,
            )
        }
    }
}
