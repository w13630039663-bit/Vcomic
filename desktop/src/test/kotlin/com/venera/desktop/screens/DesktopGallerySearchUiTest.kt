package com.venera.desktop.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GallerySearchEntry
import com.venera.compose.gallery.domain.GallerySearchSource
import com.venera.desktop.gallery.ui.DesktopGalleryMorePhase
import com.venera.desktop.gallery.ui.DesktopGallerySearchScreen
import com.venera.desktop.gallery.ui.DesktopWallCardModel
import com.venera.desktop.gallery.ui.DesktopSourceTree
import com.venera.desktop.search.desktopComicSearchHistoryNoteText
import com.venera.desktop.gallery.ui.desktopGalleryWallFooter
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 画廊那一域的标签搜索那一屏（离屏语义树）。
 *
 * ## 为什么纯件绿了还要这一颗
 *
 * 那笔账：纯件算得全对而屏上一行都不渲（唯一的 import 者是已拆的 Fluent 平行线）。
 * `DesktopSearchPagingTest` / `DesktopSearchHistoryTest` 量"算得对不对"，这一颗量
 * "真摆上屏、真按得动、按一次恰好落一次"。
 *
 * ## 台架喂的是**原始状态**而不是那三格成品（的台架教训）
 *
 * 页尾那三格（`moreLabel` / `lines` / `reading`）由 [desktopGalleryWallFooter] **现算**，与壳里
 * 那一枚 `val` 逐字同形。台架若直接收写死的成品就绕过了整条链：撤掉判据里任何一条时这里
 * **一条都不会红**。
 *
 * ## 判据要挡的四种坏写法
 *
 * 1. 那一排来源档摆出来而按不动 ⇒ ① 按一枚数一次落点；
 * 2. 那串最近搜索摆出来而按回去的是别的词 ⇒ ② 各按一枚数各归各；
 * 3. 落盘塌了那一排还照摆（按下去不落盘 = 假开关）⇒ ③ 钉"note 非空整颗不摆"；
 * 4. 结果卡那一枚落点接没接上、是不是只给可点那一档接 ⇒ 归源码尺 `DesktopGallerySearchEntryTest` ⑥
 * （那一格在无头树里仍排不到卡那一行，见下面 ④ 之后那段）。
 */
@OptIn(ExperimentalTestApi::class)
class DesktopGallerySearchUiTest {

    private val wide = WideScreenLayoutMode.Expanded

    private val sourceLabels = GallerySearchSource.options.map { it.label }

    private fun cards(n: Int) = (1..n).map {
        DesktopWallCardModel(
            title = "第$it 张",
            url = "https://img.invalid/$it.jpg",
            aspect = 1f,
            masked = false,
        )
    }

    /** 到货而还发得动的那一份账（原始状态，不是成品）。 */
    private fun batch(onScreen: Int = 3, atEnd: Boolean = false) = com.venera.desktop.gallery.ui.DesktopGalleryBatch(
        seed = 1L,
        queries = mapOf(GallerySite.YANDERE to "犬", GallerySite.SAFEBOORU to "犬"),
        posts = (1L..20L).map {
            com.venera.compose.gallery.data.GalleryPost(
                site = if (it % 2 == 0L) GallerySite.SAFEBOORU else GallerySite.YANDERE,
                id = it,
                tags = "t",
                previewUrl = "https://img.invalid/$it.jpg",
                fileExt = "jpg",
            )
        },
        sitesDone = if (atEnd) setOf(GallerySite.YANDERE, GallerySite.SAFEBOORU) else emptySet(),
    ).let { it to onScreen }

    /**
     * 台架。⚠️ 要断言落点的那一路（`pickedSource` / `pickedHistory` / `taps` / `cleared` / `backed`）
     * 一律由调用点**现传闭包**，不许藏进默认值 —— 记过：藏进默认参数读到的
     * 是注册那一刻的旧引用，计数会恒为 0 而测试照样绿。
     */
    private fun androidx.compose.ui.test.ComposeUiTest.stageSearch(
        query: String = "",
        searching: Boolean = false,
        selectedSource: Int = 0,
        history: List<GallerySearchEntry> = emptyList(),
        historyNote: String? = null,
        cards: List<DesktopWallCardModel> = emptyList(),
        batch: com.venera.desktop.gallery.ui.DesktopGalleryBatch? = null,
        onScreen: Int = cards.size,
        phase: DesktopGalleryMorePhase = DesktopGalleryMorePhase.Idle,
        onPickSource: (Int) -> Unit = {},
        onPickHistory: (GallerySearchEntry) -> Unit = {},
        onClearHistory: () -> Unit = {},
        onLoadMore: () -> Unit = {},
        // 结果卡新添的必传形参。台架递空实现即可 —— 这一把尺测的是把手那几格，
        // 不测卡可点（那格在无头树里排不到卡那一行，与 `DesktopGalleryWallUiTest` 同一前提，交源码尺 ⑥）。
        onOpenPost: (DesktopWallCardModel) -> Unit = {},
    ) {
        val footer = desktopGalleryWallFooter(batch, onScreen, phase, "张")
        setContent {
            DesktopGallerySearchScreen(
                mode = wide,
                query = query,
                onQueryChange = {},
                onSearch = {},
                searching = searching,
                failed = false,
                onRetry = {},
                sources = sourceLabels,
                selectedSource = selectedSource,
                onPickSource = onPickSource,
                history = history,
                onPickHistory = onPickHistory,
                onClearHistory = onClearHistory,
                historyNote = historyNote,
                cards = cards,
                footer = footer,
                onLoadMore = onLoadMore,
                // 联想那一排默认空表 + 无读数 ⇒ 整块不摆，旧用例一条件都不碰它；
                // 测那一排的用例自己显式递 suggestCandidates / suggestLine / onPickSuggestion。
                suggestCandidates = emptyList(),
                suggestLine = null,
                onPickSuggestion = {},
                onOpenPost = onOpenPost,
                // 双模式与两域控件。台架默认落「图库」那一域的空档 ——
                // 旧用例一条件都不碰新模式；测新域的用例自己显式递。
                searchMode = 0,
                onPickMode = {},
                rankingChoices = emptyList(),
                selectedRanking = com.venera.compose.gallery.domain.GalleryRanking.NEWEST,
                onPickRanking = {},
                periodReading = null,
                canPeriodBack = false,
                canPeriodForward = false,
                onPeriodBack = {},
                onPeriodForward = {},
                pixivTerm = "",
                onPixivTermChange = {},
                pixivFilter = com.venera.compose.gallery.domain.PixivSearchFilter.DEFAULT,
                onPixivFilter = {},
                pixivKind = 0,
                onPickPixivKind = {},
                pixivUsers = emptyList(),
                onOpenArtist = {},
                collections = null,
                pixivCollectionCards = emptyList(),
                pixivCollectionTotal = 0,
                pixivCollectionHasMore = false,
                onLoadMoreCollections = {},
                onOpenCollection = {},
                avatars = emptyMap(),
                titles = emptyMap(),
                searchR18 = true,
                onPickSearchR18 = {},
                // 滚动位住壳——台架在 setContent 里现造即可，本尺不测滚动。
                resultGridState = androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState(),
                artistListState = androidx.compose.foundation.lazy.rememberLazyListState(),
                onOpenCardArtist = null,
                initialDropdownOpen = history.isNotEmpty() || historyNote != null,
            )
        }
    }

    /** ① 来源轴那一排上屏，而按某一枚交出的是**那一枚的下标**。 */
    @Test
    fun 来源轴那一排上屏而按某一枚交出那一枚的下标() {
        var picked: List<Int> = emptyList()
        runComposeUiTest {
            stageSearch(onPickSource = { picked += it })
            sourceLabels.forEach { onNodeWithText(it).assertExists() }
            onNode(hasTextExactly(sourceLabels[1]) and hasClickAction()).performClick()
            onNode(hasTextExactly(sourceLabels[2]) and hasClickAction()).performClick()
        }
        assertEquals("按两枚落两次而下标各归各：", listOf(1, 2), picked)
    }

    /** ② 那串最近搜索上屏，而按某一枚交出的是**那一枚那条记录**（不是别的词）。 */
    @Test
    fun 最近搜索那一排上屏而按某一枚交出那一枚的记录() {
        val a = GallerySearchEntry(GallerySite.YANDERE, "犬")
        val b = GallerySearchEntry(GallerySite.SAFEBOORU, "猫")
        var picked: List<String> = emptyList()
        runComposeUiTest {
            stageSearch(history = listOf(a, b), onPickHistory = { picked += it.query })
            onNodeWithText("最近搜索").assertExists()
            onNode(hasTextExactly("犬") and hasClickAction()).performClick()
            onNode(hasTextExactly("猫") and hasClickAction()).performClick()
        }
        assertEquals("按两枚落两次而词各归各：", listOf("犬", "猫"), picked)
    }

    /** ③ 落盘塌了那一排**整颗不摆**（摆一排而按下去不落盘是假开关）。 */
    @Test
    fun 那份记不住时那一排整颗不摆() {
        runComposeUiTest {
            stageSearch(
                history = listOf(GallerySearchEntry(GallerySite.YANDERE, "犬")),
                historyNote = desktopComicSearchHistoryNoteText("IllegalStateException 盘上那份读不出来"),
            )
            onAllNodesWithText("最近搜索").assertCountEquals(0)
            onAllNodesWithText("清空").assertCountEquals(0)
            onNodeWithText("历史记录不可用").assertExists()
            // 对照组：那一枚的词没被吞（不摆的是那一排，不是整屏）
            onAllNodesWithText("犬").assertCountEquals(0)
        }
    }

    /**
     * ④ 「清空」按一次恰好落一次。
     *
     * 本屏内那条重复的「← 返回 + 画廊搜索」
     * 与窗口 titlebar 的面包屑重复（同屏两个返回），已撤 —— 返回由 titlebar 的 `VeneraBackHandle`
     * 承担（一屏一返回），屏的 `onBack` 形参一并删除。所以这里只剩「清空」这一格。
     */
    @Test
    fun 清空按一次恰好落一次() {
        var cleared = 0
        runComposeUiTest {
            stageSearch(
                history = listOf(GallerySearchEntry(GallerySite.YANDERE, "犬")),
                onClearHistory = { cleared++ },
            )
            onNode(hasTextExactly("清空")).performClick()
        }
        assertEquals("「清空」按一次落两次或不落：", 1, cleared)
    }

    // 无头树中懒网格未布局到卡片行，卡片点击接线由源码测试 DesktopGallerySearchEntryTest 校验，避免此处误点页尾假绿。

    /** ⑥ 空墙那句分得清「正在搜」与「搜完了没图」。 */
    @Test
    fun 空墙那两档各念各的() {
        runComposeUiTest {
            stageSearch(query = "犬", searching = true)
            onAllNodesWithText("换个关键词再试").assertCountEquals(0)
            onAllNodesWithText("无结果").assertCountEquals(0)
        }
        runComposeUiTest {
            stageSearch(query = "犬")
            onNodeWithText("无结果").assertExists()
            onNodeWithText("换个关键词再试").assertExists()
            onAllNodesWithText("日志里有 D_取数 那一行。", substring = true).assertCountEquals(0)
        }
    }

    /** ⑦ 屏不自己算：屏所在那颗文件里不许出现那三格的算式（源码尺同族，这里量"真没搬进来"）。 */
    @Test
    fun 屏里不许出现那三格的算式() {
        val text = DesktopSourceTree.codeText(
            java.io.File(DesktopSourceTree.repoRoot, "desktop/src/main/kotlin/com/venera/desktop/gallery/ui/DesktopGallerySearchScreen.kt"),
        )
        listOf(
            "desktopGalleryWallFooter(" to "页尾判据搬进屏里了",
            "fetchSearchPage(" to "取数搬进屏里了",
            "atEnd" to "屏自己拼了一次「到底没」",
        ).forEach { (banned, why) ->
            assertEquals("$why：", 0, text.split(banned).size - 1)
        }
    }
}

