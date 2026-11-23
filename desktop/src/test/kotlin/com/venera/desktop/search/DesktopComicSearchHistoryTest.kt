package com.venera.desktop.search

import com.venera.compose.data.platform.JsonKeyValueStore
import com.venera.compose.data.platform.KeyValueStore
import com.venera.compose.data.platform.PathProvider
import com.venera.desktop.gallery.ui.DesktopSourceTree
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 漫画搜索那一屏的**查询历史**。
 *
 * ## 能真跑的全真跑（与画廊那颗 `DesktopSearchHistoryTest` 同一口径）
 *
 * ④⑤ 用临时目录里那颗**真 store** 走完整圈（写盘 → 新实例读回 → 拆串），因为这一族最坏的
 * 失败形状是"内存里看着对、盘上那份不是对岸那份"：prefs 名、键名、分隔符、清空语义四样里
 * 任何一样偏了，用户在手机上搜过的历史在桌面上就是看不见，而两端各自看起来都正常工作。
 *
 * ## 那三样逐字回核 Android（不靠这里抄一遍）
 *
 * ① 从 `feature/SearchViewModel.kt` 的**源码文本**现取 prefs 名 / 键名 / 封顶数 / 分隔符，
 * 再与桌面对拍 ⇒ 对岸改了那几枚，红的是这条判据，而不是桌面悄悄存进一份没人读的档。
 * ② 同一招用在词上：「最近搜索」与「清空」从 `feature/SearchScreen.kt` 现取。
 *
 * ## 每条负向扫描都配一条"锚点真在"的对照
 *
 * ③ 那条钉"对岸那两行确实抓到了"，否则正则写漂时判式恒真（批 8 记过的规矩）。
 */
class DesktopComicSearchHistoryTest {

    private class TempPaths(root: File) : PathProvider {
        override val dataRoot: File = root
        override val cacheRoot: File = File(root, "cache").also { it.mkdirs() }
    }

    /** 临时目录里那颗真 store（每次一颗新目录，测试之间不串味）。 */
    private fun freshStore(): Pair<KeyValueStore, File> {
        val root = Files.createTempDirectory("comic-search-history").toFile().also { it.deleteOnExit() }
        return JsonKeyValueStore(DESKTOP_COMIC_SEARCH_PREFS, TempPaths(root)) to root
    }

    /** 重开一份实例（模拟下一次启动读盘，不吃任何内存态）。 */
    private fun reopen(root: File): KeyValueStore =
        JsonKeyValueStore(DESKTOP_COMIC_SEARCH_PREFS, TempPaths(root))

    /** ① prefs 名 / 键名 / 封顶 / 分隔符四样逐字回核对岸。 */

    /** ② 那一排的两句话与那枚 `take(5)` 逐字回核对岸（从那颗 `SearchHistory` 的**函数体**里取）。 */

    /** ③ 追加算式三格各钉一条：新的排最前、同词去重、封顶 20。 */
    @Test
    fun 追加那三格少一格都会长出坏把手() {
        val seeded = listOf("猫", "犬")
        assertEquals(
            "新词没排最前（那一排最左那枚该是刚搜过的）：",
            listOf("兔", "猫", "犬"), desktopComicSearchHistoryPush(seeded, "兔"),
        )
        assertEquals(
            "同一个词搜两次摆两枚一模一样的把手（对岸那一枚 `distinct()` 就是这个理由）：",
            listOf("猫", "犬"), desktopComicSearchHistoryPush(seeded, "猫"),
        )
        val twenty = (1..20).map { "词$it" }
        val capped = desktopComicSearchHistoryPush(twenty, "新词")
        assertEquals("封顶没生效（第 21 条挤进来，对岸的账就变了）：", 20, capped.size)
        assertEquals("掉的是最旧那一条而不是新的：", "新词", capped.first())
        // 那张表是**新词在前**（`listOf(query) + readHistory()`），所以最旧的是表尾那一枚。
        assertFalse("最旧那条没被挤出去（表尾那枚才是旧的）：", capped.contains("词20"))
        assertTrue("新的那条挤掉了次新的一条（只该掉表尾）：", capped.contains("词1"))
        // 空串那一档：原表交回，一条都不记（屏上不发车，而记进去就是一枚看不见的把手）。
        assertEquals("空串进了历史：", seeded, desktopComicSearchHistoryPush(seeded, "   "))
        // 两端空白会被裁掉再记：记下来的是"兔"而不是" 兔 "，否则下一次去重认不出同一条。
        assertEquals("没裁两端空白：", listOf("兔"), desktopComicSearchHistoryPush(emptyList<String>(), " 兔 "))
    }

    /** ④ 那一排摆哪几枚：四条判据各挡一次，而"摆几枚"跟着对岸那枚 `take(5)`。 */
    @Test
    fun 那一排摆不摆与摆几枚是一处出处() {
        val seven = (1..7).map { "词$it" }
        val noResult = 0
        assertEquals("输入框有字时那一排不许还在（敲第二个字就把要点的那枚抽走）：",
            emptyList<String>(), desktopComicSearchHistoryChips(seven, "猫", searching = false, resultCount = noResult))
        assertEquals("正在发车时那一排不许还在（那一排会在结果出来那一刻跳走）：",
            emptyList<String>(), desktopComicSearchHistoryChips(seven, "", searching = true, resultCount = noResult))
        assertEquals("没历史时整格不摆：",
            emptyList<String>(), desktopComicSearchHistoryChips(emptyList<String>(), "", searching = false, resultCount = noResult))
        // 第四条是桌面特有的（对岸那一排与结果同屏共存，桌面的结果是一面 fillMaxSize 的墙）：
        // 有结果时那一排整格让位，而不是挡在墙前面把墙往下挤。
        assertEquals("有结果时那一排还摆（桌面那一屏的墙是 fillMaxSize，那一排挡在它上面碍事）：",
            emptyList<String>(), desktopComicSearchHistoryChips(seven, "", searching = false, resultCount = 20))
        assertEquals(
            "屏上只摆最近 5 枚（对岸同一数，那句注释写着「不新增独立历史页面」）：",
            listOf("词1", "词2", "词3", "词4", "词5"),
            desktopComicSearchHistoryChips(seven, "", searching = false, resultCount = noResult),
        )
    }

    /** ⑤ 三个动作真落到盘上：写回、重开读回、清空走 remove。 */
    @Test
    fun 那三样动作真落到盘上而清空是删键() {
        val (store, root) = freshStore()
        assertEquals("新机器上读历史不该抛：", emptyList<String>(), desktopComicSearchHistoryRead(store))

        val one = desktopComicSearchHistoryAppend(store, "猫")
        assertEquals("append 之后内存里没拿到新表：", listOf("猫"), one)
        // 关键一格：拿**新实例**读盘。内存里看着对而盘上那份不对，本仓那一族就是这么漏的。
        assertEquals("盘上那份读不回来（键名或分隔符偏了）：", listOf("猫"), desktopComicSearchHistoryRead(reopen(root)))

        // 现读盘再追加：壳里那份本地态**没参与**，所以会并上盘上那份而不是抹掉它。
        val two = desktopComicSearchHistoryAppend(store, "犬")
        assertEquals("新词没排在最前：", listOf("犬", "猫"), two)
        assertEquals("上一趟那条没并进盘上那份（append 吃了内存态而不是现读盘）：",
            listOf("犬", "猫"), desktopComicSearchHistoryRead(reopen(root)))

        // 空串那一趟一个字节都不写（写一次等于白碰一趟盘，还可能把盘上那份按空表重排）。
        val untouched = desktopComicSearchHistoryAppend(store, "  ")
        assertEquals("空串那趟把盘上那份改了：", listOf("犬", "猫"), untouched)

        val cleared = desktopComicSearchHistoryClearAll(store)
        assertEquals("清空之后内存里还剩东西：", emptyList<String>(), cleared)
        assertFalse(
            "清空写成了空串而不是删掉那个键（对岸 `prefs.remove` 的语义被换了：「用户清过」与「从来没存过」是两件事）：",
            reopen(root).keys().contains(DESKTOP_COMIC_SEARCH_HISTORY_KEY),
        )
    }

    /** ⑥ 接线尺：那一排四格成品由壳递进屏，屏不碰盘也不自己拼那三条件。 */
    @Test
    fun 壳递成品而屏只搬() {
        val shell = DesktopSourceTree.desktopLargeScreenCode()
        listOf(
            "desktopComicSearchHistoryAppend(" to "那一排没真落盘（写了个按了记不住的把手）",
            "desktopComicSearchHistoryRead(" to "那一排没读盘（屏上永远念「没搜过东西」）",
            "desktopComicSearchHistoryClearAll(" to "那枚「清空」没接上落盘那一跳",
            "historyChips = searchHistoryChips," to "那一排的成品没递进屏",
            "onPickHistory = { word ->" to "那一排的把手没接上（摆了而按不动 = 评审 R6 那一族）",
            "onClearHistory = {" to "「清空」那枚没接上",
        ).forEach { (needle, why) ->
            assertTrue("$why：判式「$needle」在壳里零命中", needle in shell)
        }
        // 屏只搬：那三条件一颗都不许住在屏里（两处判据一定漂）。
        val screen = DesktopSourceTree.codeText(
            DesktopSourceTree.desktopSourceOf("search/DesktopSearchScreen.kt"),
        )
        listOf(
            "desktopComicSearchHistoryChips(" to "屏自己去算那一排摆不摆",
            "DESKTOP_COMIC_SEARCH_HISTORY_MAX" to "屏自己去数封顶几条",
            "DESKTOP_COMIC_SEARCH_HISTORY_SHOWN" to "屏自己去数摆几枚",
            // 屏从前自己拼过这一句 ⇒ 那一排"什么时候摆"就有了两个出处（纯件第四条一改，
            // 屏那一半不改就会挡住结果墙）。
            "results.isEmpty() && historyChips" to "屏自己又拼了一遍那一排的条件",
        ).forEach { (needle, why) ->
            assertFalse("$why（判据只许有一处出处）：$needle 出现在屏里", needle in screen)
        }
        // 对照：屏确实搬了那两句话，而不是自己写了一份措辞。
        assertTrue("屏没搬对岸那句「最近搜索」：", "DESKTOP_COMIC_SEARCH_HISTORY_TITLE" in screen)
        assertTrue("屏没搬对岸那枚「清空」：", "DESKTOP_COMIC_SEARCH_HISTORY_CLEAR" in screen)
        // 点一枚 = 装回那个词**并立刻再搜一遍**（只填不发车就是按了要再按第二次的把手）。
        val pick = shell.substringAfter("onPickHistory = { word ->").substringBefore("},")
        assertTrue("点那一枚没装回输入框：", "searchQuery = word" in pick)
        assertTrue("点那一枚没另起一轮（连点同一条会被键不变那颗 effect 挡掉）：", "searchTick++" in pick)
    }
}
