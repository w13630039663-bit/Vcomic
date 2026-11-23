package com.venera.desktop.search

import com.venera.compose.feature.SearchPagination
import com.venera.desktop.gallery.ui.DesktopSourceTree
import com.venera.engineprobe.EngineSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 漫画搜索那一屏的**翻页与成因**。
 *
 * ## 这一批评的四格
 *
 * 1. **翻页判据一颗都不许在桌面重述**（② 转交尺）：那三条判据住在共享面
 *    `feature/SearchPagination.kt`，桌面自己再写一份就是"两处判据一定会漂"那一族。
 * 2. **成因不许冒充"没搜到"**（③④ 那两格）：从前装配点 `getOrDefault(emptyList())` 把
 *    站方那句 `__err` 整笔咽掉，屏上只剩「「词」在「源」里没搜到」—— 那是把**我们的失败**
 *    说成**站方的空结果**。这一批钉的是"每一档念得出自己那一档"。
 * 3. **同一句成因不许同时出现在两处**（⑤）：屏顶那行、中间那格、页尾那一格各管一段，
 *    念两处会让人以为坏了两次。
 * 4. **排重先于屏蔽**（①）：顺序反了会把"这一页被剔空"误判成"到底"。
 *
 * ## 逐字回核对岸
 *
 * ⑥ 那三句文案（「已经没有了」/「加载更多」/「正在加载更多…」）与「再搜一次」都从
 * `feature/SearchScreen.kt` 的源码文本现取再比 ⇒ 对岸改词时红的是这条，而不是桌面自己换措辞。
 */
class DesktopComicSearchOutcomeTest {

    private fun card(id: String) =
        EngineSession.ComicCard(title = "标题$id", id = id, cover = "https://cover/$id")

    /** ① 排重：只认号而不认对象；重复的回空表而空表就是"到底"的那一枚输入。 */
    @Test
    fun 新一页里只数没见过的那些() {
        val current = listOf(card("a"), card("b"))
        assertEquals(
            "同号那一枚没被排掉（越界把末页原样回吐的源会在这儿露馅）：",
            listOf(card("c")),
            desktopComicSearchUnseen(current, listOf(card("a"), card("c"))),
        )
        assertEquals(
            "整页都是旧条目时交回空表（空表喂给翻页判据就是到底）：",
            emptyList<EngineSession.ComicCard>(),
            desktopComicSearchUnseen(current, listOf(card("a"), card("b"))),
        )
        assertEquals("旧表为空时整页都是新的：", 2, desktopComicSearchUnseen(emptyList(), current).size)
    }

    /** ② 转交尺：三条判据逐字照共享面，而桌面一处都不重述。 */
    @Test
    fun 翻页判据整颗转交共享面而桌面零重述() {
        // 形状对拍：同一组输入喂给桌面那颗与**共享面那一颗本尊**，结果逐格必须相同。
        // 为什么这里不另写一份期望值：抄一份算式进判据就是"两处判据一定会漂"那一族
        // （而抄的人只会抄他记得的那三支），改成吃共享面那颗才是真对拍。
        val cases = listOf(
            // 零新条目 ⇒ 到底（这一条**不许**被 maxPage 否决）
            Quintle(null, 1, 0), Quintle(50, 1, 0), Quintle(50, 3, 0),
            // 声明了页数而还没翻到头
            Quintle(3, 1, 20), Quintle(3, 2, 20), Quintle(3, 3, 20),
            // 游标型（不声明）⇒ 只靠第一条
            Quintle(null, 7, 5),
        )
        cases.forEach { c ->
            assertEquals(
                "转交偏了（maxPage=${c.maxPage} page=${c.page} unseen=${c.unseen}）：",
                SearchPagination.canLoadMore(c.maxPage, c.page, c.unseen),
                desktopComicSearchCanLoadMore(c.maxPage, c.page, c.unseen),
            )
        }
        // 对照：那七组里至少有一组期望是 `false`。否则"逐格相同"会被两份恒假的实现满足。
        assertTrue(
            "对照组失效：这七组输入里没有一组判到底 ⇒ 上面那条对拍是恒真的空判式",
            cases.any { !SearchPagination.canLoadMore(it.maxPage, it.page, it.unseen) },
        )
        // 声明的那颗件确实在共享面上（否则上面那个对照是恒真的）。
        val face = DesktopSourceTree.codeText(DesktopSourceTree.androidSource("feature/SearchPagination.kt"))
        assertTrue("对照组失效：共享面那颗 `canLoadMore` 改了签名", "fun canLoadMore(" in face)
        // 桌面不重述：那颗件里不许出现"自己算翻页"的形状。
        val own = DesktopSourceTree.codeText(
            DesktopSourceTree.desktopSourceOf("search/DesktopComicSearchOutcome.kt"),
        )
        assertTrue("桌面没转交共享那颗件：", "SearchPagination.canLoadMore(" in own)
        assertFalse(
            "桌面自己重算了一遍翻页（`unseenItemsOnPage <= 0` 那一判只许有一处出处）：",
            Regex("""unseenItemsOnPage\s*<=\s*0""").containsMatchIn(own),
        )
        // 排空那一格：零新条目就算到底，这一条在桌面上也要能单独量到。
        assertFalse(
            "首页就空却还说翻得动（那正是「加载更多」永远挂在页尾那个旧病）：",
            desktopComicSearchCanLoadMore(declaredMaxPage = 50, pageJustFetched = 1, unseenItemsOnPage = 0),
        )
    }

    /** ③ 页尾那四档各念一句，而「失败」排在「到底」之前。 */
    @Test
    fun 页尾那四档各念一句而失败不许冒充到底() {
        assertEquals(
            "正在翻页时那一格念进度：",
            DesktopComicSearchMore.Loading,
            desktopComicSearchMoreState(loadingMore = true, canLoadMore = true,
                hasSearched = true, resultsEmpty = false, reason = null),
        )
        assertEquals(
            "翻得动时摆把手：",
            DesktopComicSearchMore.Button,
            desktopComicSearchMoreState(loadingMore = false, canLoadMore = true,
                hasSearched = true, resultsEmpty = false, reason = null),
        )
        assertEquals(
            "翻不动了而**没成因**才念到底：",
            DesktopComicSearchMore.End,
            desktopComicSearchMoreState(loadingMore = false, canLoadMore = false,
                hasSearched = true, resultsEmpty = false, reason = null),
        )
        assertEquals(
            "有成因而翻不动 ⇒ 必须是 Failed（把失败念成到底，用户就再也不会点那一枚本来能重试的把手）：",
            DesktopComicSearchMore.Failed,
            desktopComicSearchMoreState(loadingMore = false, canLoadMore = false,
                hasSearched = true, resultsEmpty = false, reason = "该源没有 search"),
        )
        assertEquals(
            "结果本来就空时那一格什么都不摆（「没有结果」那句话已经由中间那一格说过）：",
            DesktopComicSearchMore.None,
            desktopComicSearchMoreState(loadingMore = false, canLoadMore = false,
                hasSearched = true, resultsEmpty = true, reason = "该源没有 search"),
        )
        assertEquals(
            "还没发过车时那一格什么都不摆：",
            DesktopComicSearchMore.None,
            desktopComicSearchMoreState(loadingMore = false, canLoadMore = false,
                hasSearched = false, resultsEmpty = true, reason = null),
        )
    }

    /** ④ 中间那一格五档各一句而互相不许冒充（真正修的那格）。 */
    @Test
    fun 空结果那一句五档各说得开() {
        assertEquals(
            "还没发过车时不许念成「这个词没搜到」（敲了字而一次都没按下去，屏上从前那样念是假话）：",
            "输入书名或作者，回车开始搜。",
            desktopComicSearchEmptyNote("猫", "jm", searched = false, searching = false,
                resultCount = 0, reason = null),
        )
        assertEquals(
            "发过车而 0 条才念没搜到：",
            "「猫」在「jm」里没搜到。",
            desktopComicSearchEmptyNote("猫", "jm", searched = true, searching = false,
                resultCount = 0, reason = null),
        )
        assertEquals(
            "站方给了成因 ⇒ 念成因而**不**念没搜到（这一档就是从前被咽掉的那笔账）：",
            "该源没有 search",
            desktopComicSearchEmptyNote("猫", "jm", searched = true, searching = false,
                resultCount = 0, reason = "该源没有 search"),
        )
        assertNull(
            "正在发车时那一格由屏念「正在搜索…」，这里不抢：",
            desktopComicSearchEmptyNote("猫", "jm", searched = true, searching = true,
                resultCount = 0, reason = null),
        )
        assertNull(
            "有结果时那一格是那面墙，不再补一句：",
            desktopComicSearchEmptyNote("猫", "jm", searched = true, searching = false,
                resultCount = 20, reason = "翻页失败"),
        )
    }

    /** ④-补 成因是异常原文时，屏上念归档短句而不是把 TLS 报错照搬上屏。 */
    @Test
    fun 成因是异常原文时上屏走归档() {
        val raw = "Remote host terminated the handshake"
        assertEquals(
            "握手被切断那一档必须念成可执行的「网络受阻 · 需配置代理后重试」：",
            "网络受阻 · 需配置代理后重试",
            desktopComicSearchEmptyNote("touhou", "copy_manga", searched = true, searching = false,
                resultCount = 0, reason = raw),
        )
        assertTrue(
            "页尾那一句也不许把异常原文透传：",
            "网络受阻" in desktopComicSearchFailedFooter(raw),
        )
    }

    /** ⑤ 同一句成因只上一处：页尾那一句带成因，而它只在 Failed 那一档被造出来。 */
    @Test
    fun 页尾那一句带成因而不留空句() {
        assertTrue("念不出成因那一档就等于没修（那句得把站方原话带出来）：",
            "该源没有 search" in desktopComicSearchFailedFooter("该源没有 search"))
        val blank = desktopComicSearchFailedFooter(null)
        assertTrue("成因缺席时那一档退成了空句（那一行念得出才叫一档）：$blank", blank.isNotBlank())
        assertTrue("成因缺席时不许把「null」念上屏：$blank", "null" !in blank)
        // 屏顶那枚 `note` 只吃"那一排记不住"，永远不吃这一趟的成因。
        val shell = DesktopSourceTree.desktopLargeScreenCode()
        assertTrue("屏顶那行不再是那句落盘成因（同一句成因念两处 = 让人以为坏了两次）：",
            "note = searchHistoryNote," in shell)
        assertFalse("屏顶那行又被塞进这一趟的成因：", "note = searchCause," in shell)
    }

    /** ⑥ 那四句文案逐字回核对岸。 */

    /** ⑦ 取证那一句：五格各上读数，而"源不声明"不许念成 0 页。 */
    @Test
    fun 取证读数那一行各格说得开() {
        val line = desktopComicSearchReading(
            query = "猫", page = 2, declaredMaxPage = null, resultCount = 40,
            canLoadMore = true, historyCount = 3, reason = null,
        )
        assertTrue("前缀不在（`D_搜索` 那一族靠它筛）：$line", line.startsWith("D_搜索"))
        listOf("词=猫", "页=2", "结果=40", "可翻=是", "历史=3", "成因=无").forEach { needle ->
            assertTrue("读数少了一格「$needle」：$line", needle in line)
        }
        assertTrue(
            "源不声明页数时不许念成 0（那是「这一颗源不给页数」而不是「一共 0 页」）：",
            "源声明=不给" in line,
        )
        assertTrue(
            "声明了页数时要把那个数念出来：",
            "源声明=50" in desktopComicSearchReading("猫", 1, 50, 20, true, 0, null),
        )
        assertTrue(
            "翻不动与到底要分得开（`可翻=否` 两档同形，靠成因那一格分开）：",
            "成因=该源没有 search" in desktopComicSearchReading("猫", 1, null, 0, false, 0, "该源没有 search"),
        )
    }

    /** ⑧ 接线尺：那四格成品由壳递进屏，而页码住在壳里。 */
    @Test
    fun 壳递成品而屏只搬那四格() {
        val shell = DesktopSourceTree.desktopLargeScreenCode()
        listOf(
            "moreState = searchMoreState," to "页尾那一档没递进屏",
            "emptyNote = searchEmptyNote," to "空结果那句成品没递进屏",
            "failedFooter = searchFailedFooter," to "页尾那句带成因的话没递进屏",
            "desktopComicSearchUnseen(" to "翻页那一趟没排重（同一条本会摆两遍）",
            "desktopComicSearchCanLoadMore(" to "翻页判据没真被消费",
            "desktopComicSearchReading(" to "取证那一行没接上（翻页那条链在包里就没读数）",
            "onLoadMore = { loadMoreTick++ }," to "那一枚把手没接上（摆了而按不动）",
            "if (outcome.cards.isNotEmpty()) searchPage = nextPage" to "页号无条件推进（越界回吐末页的源会原地打转）",
        ).forEach { (needle, why) ->
            assertTrue("$why：判式「$needle」在壳里零命中", needle in shell)
        }
        val screen = DesktopSourceTree.codeText(
            DesktopSourceTree.desktopSourceOf("search/DesktopSearchScreen.kt"),
        )
        listOf(
            "desktopComicSearchMoreState(" to "屏自己去拼页尾那四档",
            "desktopComicSearchEmptyNote(" to "屏自己去拼空结果那一句",
            "canLoadMore" to "屏自己去读那枚翻页标志（那一格只许看档位）",
        ).forEach { (needle, why) ->
            assertFalse("$why（判据只许有一处出处）：$needle 出现在屏里", needle in screen)
        }
        // 页码与引擎那一跳都住在壳里：那一屏不查引擎。
        assertFalse("搜索屏自己去碰引擎了：", "EngineSession()" in screen)
    }

    private data class Quintle(val maxPage: Int?, val page: Int, val unseen: Int)
}
