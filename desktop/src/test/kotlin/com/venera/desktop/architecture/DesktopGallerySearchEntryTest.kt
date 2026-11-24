package com.venera.desktop.architecture

import com.venera.desktop.app.DesktopRoute
import com.venera.desktop.app.VeneraDesktopLargeScreen
import com.venera.desktop.gallery.ui.DesktopSourceTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 钉住「画廊那一域的标签搜索真有一扇门，而且门后那一族纯件真被消费」—— 。
 *
 * ## 缺口长什么样（现场量的，不是抄旧账）
 *
 * `fetchSearchPage`（取一页，与续页同一颗）、`boardOf`（来源轴 → 哪一站的板）与
 * `DesktopSearchHistory` 那一整排纯件（读 / 写 / 落点 / 清）从桌面起就都在，
 * `DesktopSearchPagingTest` / `DesktopSearchHistoryTest` 也一直绿 —— 而 `desktop/src/main` 里
 * **没有任何一屏**调它们。于是「打一串标签 → 几站一起搜 → 翻页 → 下次接着看搜过的」这条
 * 用户在 rail 上点不到。与 `DesktopHistoryEntryTest` / `DesktopSourceManageEntryTest` 同一族：
 * 毛病不是"缺函数"而是"函数都在而没人调用"。
 *
 * ## 这里钉的五件事，各自挡一种坏写法
 *
 *  ① 路由枚举里有那一档，且切过去那一处挂在**用户按得到的把手**上（挡"代码里过了一遍"）；
 *  ② 那一族的纯件在壳里**真有调用点**（挡"接了参数而不消费"，也挡"门开着而门后是空的"）；
 *  ③ 屏只搬成品、不自己判分页（挡两处各算一定漂）；
 *  ④ Gelbooru 没配账号那一腿**发车前剔而交 silent**（挡"下一页恒在而恒不出货"那枚死按钮，
 * 与"把我们的闸说成站方的空结果"那族假话）——这条是现量到的真 bug 修出来的判据；
 * ⑤ `LocalDesktopSearchHandoff`（点标签搜同一标签）**接上了真消费者**（翻正）：
 *     看图那一屏（`DesktopGalleryPostViewer`）那几枚标签是这一枚交接的第一位消费者，认不出所属站那一片仍只读，
 *     而壳里 provides 那一支真装回 query / source / route / tick 四格 —— 挡"随手 provide 而没 route 过去"那枚假开关。
 */
class DesktopGallerySearchEntryTest {

    private val shellFile = DesktopSourceTree.desktopSourceOf("app/VeneraDesktopLargeScreen.kt")
    private val shellLines = DesktopSourceTree.desktopLargeScreenLines().filter { it.isNotEmpty() }
    private val screenFile = DesktopSourceTree.desktopSourceOf("gallery/ui/DesktopGallerySearchScreen.kt")
    private val screen = DesktopSourceTree.codeText(screenFile)
    // 卡片项统一（阶段三）：搜索墙不再自绘 `WallResultCard`，改与主墙 / 收藏墙共用同一颗
    // `gallery/ui/DesktopWallCard.kt`。于是"卡接上点击 + 只给可点那一档接"那枚不变量搬到共享那颗上盯。
    // 卡片规格收进唯一卡 DesktopGalleryCard.kt（DesktopWallCard 退成薄委托）。
    private val wallCardFile = DesktopSourceTree.desktopSourceOf("gallery/ui/DesktopGalleryCard.kt")

    /**
     * ① 路由枚举里有那一档，且壳里恰好**三处**切过去（悬浮改口径），各挂在用户按得到的把手上。
     *
     * 一处：`onTopSearch` 那支（墙顶栏换成真输入场，发车门换名；
     *   旧 `onOpenSearch = { route = … }` 那枚把手随假入口一起退役）
     * · 一处：`LocalDesktopSearchHandoff provides` 那一支里（看图那一屏点标签回搜那一路）
     * 一处：pixiv 作品页标签跳搜那一支
     * 防的仍是"provide / 回调一支随手加了而没 route 过去"——由末条 assert 兜住。
     */
    @Test
    fun 路由枚举里有那一档而两处入口各挂在两处把手上() {
        val route = DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("app/DesktopRoute.kt"))
        assertTrue(
            "`DesktopRoute` 里没有 `data object GallerySearch` ⇒ 那一屏进不去：",
            "data object GallerySearch : DesktopRoute" in route,
        )
        val cuts = shellLines.filter { "route = DesktopRoute.GallerySearch" in it }
        assertEquals(
            "切进画廊搜索路由的赋值不止三处或对不上（2026-11-14 改口径后三处：悬浮搜索发车那支 + " +
                "看图屏点标签回搜那一支 + pixiv 作品页标签跳搜那一支）：" + cuts,
            3,
            cuts.size,
        )
        // 正向钉各处**各挂在各的把手上**。onTopSearch 那支 route 行与回调不同行：回调在 +
        // 总数恰三（上一条 assert）就兜住"加了回调而壳没 route 过去"。
        assertTrue(
            "第一处不是从 `onTopSearch` 那支里切过去的 ⇒ 悬浮搜索发车那支不在：\n$cuts",
            shellLines.any { "onTopSearch = { site, term ->" in it },
        )
        // 交接那一支 —— §132 那一枚 chip 按得下去那一路。route 行是裸行区分不了支：
        // 支存在 + 总数恰三（上一条 assert）兜住"provide 了而没 route 过去"。
        assertTrue(
            "交接那一支不在 ⇒ 看图屏点标签回搜没 route 过去：\n$cuts",
            shellLines.any { "LocalDesktopSearchHandoff provides { sites, tag ->" in it },
        )
        // pixiv 作品页点标签 chip 直接跳 pixiv 源搜索那一支（改口径）：
        // route 那行与带词那行不同行，所以按"回调在 + 词真递进"两件事钉。
        assertTrue(
            "第三处 `onTagSearch` 回调没接上 ⇒ pixiv 标签跳搜没真 route 过去：\n$cuts",
            shellLines.any { "onTagSearch = { tag ->" in it } &&
                DesktopSourceTree.desktopLargeScreenOccurrences( "pixivSearchTerm = tag") >= 1,
        )
    }

    /** ② 那一族纯件真被壳消费（不是只 import 着）。每个名字至少落在壳里一次。 */
    @Test
    fun 那一族纯件在壳里真有调用点() {
        listOf(
            "desktopWallFirstPage(" to "第一轮取数那扇门的取数件",
            "fetchSearchPage(" to "续页那一枚件",
            "desktopWallPageAfter(" to "续页落地那枚 reducer",
            "desktopHistoryRead(" to "读那串最近搜索",
            "desktopHistoryPush(" to "写那串最近搜索",
            "desktopHistoryClearAll(" to "清那串最近搜索",
            "desktopHistoryLanding(" to "点一条历史的落点",
            "desktopSearchHistoryEntry(" to "该记的那一条",
        ).forEach { (call, what) ->
            val n = DesktopSourceTree.desktopLargeScreenOccurrences( call)
            assertTrue(
                "「$what」$call 在壳里没有调用点（$n）⇒ 门后是空的，本批要清的就是这一族孤儿",
                n >= 1,
            )
        }
    }

    /** ③ 屏只搬成品：屏里不许出现任何"自己算分页"的形状。 */
    @Test
    fun 屏只搬成品而不自算分页() {
        listOf(
            "atEnd" to "屏自己拼了一次「到底没」—— 那一判归 DesktopGalleryBatch",
            "queries" to "屏自己数了发出去几腿 —— 页尾那句的主语归那颗件",
            "desktopGalleryWallFooter(" to "页尾判据搬进屏里了 —— 屏只搬成品",
            "desktopWallFirstPage(" to "取数跑进屏里了",
            "fetchSearchPage(" to "取数跑进屏里了",
            "availableLegs" to "发车前剔腿那一判搬进屏里了（它归壳的 effect）",
        ).forEach { (banned, why) ->
            assertEquals(
                "$why：",
                0,
                DesktopSourceTree.codeOccurrences(screenFile, banned),
            )
        }
        // 正向：那三格与那颗扳机**真被消费**（只接参数不摆 = 假接线）。
        // 翻页把手由**近尾观察器**接替 ——
        // 屏里要有那颗共用观察器（DesktopStaggeredAutoLoadEffect），且它真吃到 onLoadMore。
        assertTrue("页尾那几句没真摆出来：", "footer.lines.forEach" in screen)
        assertTrue("近尾自动续页那颗观察器没接：", "DesktopStaggeredAutoLoadEffect(" in screen)
        assertTrue("续页扳机没吃到 onLoadMore：", "onLoadMore = onLoadMore" in screen)
    }

    /**
     * ④ Gelbooru 没配账号那一腿：壳里**发车前剔腿而把原因交给 silent**（现量修的真 bug）。
     *
     * 坏写法有两种，都会红在这里：把 `source.sites` 整个塞进 `queries`（那腿永远进不了
     * `sitesDone` ⇒「下一页」恒在而恒不出货），或剔了腿而不交 silent（屏上不念"为什么少了它"，
     * 只剩空态那句"那一站没回图" = 把我们的闸说成站方的空结果）。
     */
    @Test
    fun 剔Gelbooru那一腿而把原因交给页尾而不是塞满queries() {
        val idx = shellLines.indexOfFirst { "val legs = source.availableLegs(configured)" in it }
        assertTrue("壳里没走 `source.availableLegs(configured)` 剔腿（判据失效，不是通过）", idx >= 0)
        val block = shellLines.drop(idx).take(30).joinToString("\n")
        assertTrue("剔了腿却没交 missing 给 silent ⇒ 页尾念不出「没发车」：$block", "silent = missing" in block)
        assertTrue(
            "壳里还残留把整份 source.sites 塞进 queries 的旧写法 ⇒「下一页」恒挂：$block",
            "source.sites.associateWith" !in block,
        )
        assertTrue(
            "第一轮取数那一句应喂 legs（真发得动的腿）而不是 source.sites：$block",
            "queries = legs.associateWith" in block,
        )
    }

    /**
     * ⑤ `LocalDesktopSearchHandoff`（点标签回搜）**接上了真消费者**（翻正）。
     *
     * 从前这一条钉的是反面："仍零消费者"（挡"为凑功能伪造一条接上的路"）—— 因为看图那一屏那几枚
     * 标签是只读 chip、唯一真消费者本该在详情浮层而那浮层还没点亮。§130 造出看图落点、§131 让搜索墙
     * 也 route 进去之后，给看图那一屏那几枚 chip 接上了这枚把手 —— 于是那条旧判据若原样
     * 留着就又是手册 §4.1 那族病：**路已经真接上，而一把绿尺还在宣称"零消费者"**。按它头注当初的约定
     * （"若详情浮层那批真点亮了它，这条应连同 README 一起改"）翻成正向接线判据，三件事都钉恰一处：
     *  1. 壳里 `LocalDesktopSearchHandoff provides`（那一处、也是唯一一处 provide）；
     *  2. 看图那一屏 `LocalDesktopSearchHandoff.current`（消费者恰好这一颗屏，不是到处读）；
     *  3. 壳里 `desktopSearchSeedFor(`（provides 那一支真把「站点+标签」折成种子，而不是随手 provide
     *     一个空 lambda —— 那正是旧判据防的"随手 Provider 而没接把手"那枚假开关，翻正后仍防它）。
     * ⚠️ 认不出所属站（pixiv / 空 uid）时那颗屏**整片 chip 不接点击**（判据在 `desktopViewerTagSite`，
     *   由 `DesktopGalleryPostViewerTest` 值尺钉），所以这枚 provide 不是"永远有人按"的承诺，
     *   而是"能认站的那些按得动、认不出的整片只读"——比硬打全部更诚实。
     */
    @Test
    fun 点标签回搜那条接上了看图那一屏这个真消费者() {
        val shellText = DesktopSourceTree.desktopLargeScreenCode()
        val viewer = DesktopSourceTree.desktopSourceOf("gallery/ui/DesktopGalleryPostViewer.kt")
        assertEquals(
            "壳里 `LocalDesktopSearchHandoff provides` 该恰一处（多了就是第二处 provide，会分叉）：",
            1,
            DesktopSourceTree.desktopLargeScreenOccurrences( "LocalDesktopSearchHandoff provides"),
        )
        assertEquals(
            "看图那一屏读 `LocalDesktopSearchHandoff.current` 该恰一处（消费者就是这颗屏）：",
            1,
            DesktopSourceTree.codeOccurrences(viewer, "LocalDesktopSearchHandoff.current"),
        )
        // 假开关防线（翻正后仍守旧判据那条"随手 provide 而没接把手"）：provide 那一支真调折子纯件。
        assertEquals(
            "壳里 `desktopSearchSeedFor(` 该恰两处（2026-11-14 悬浮搜索批改口径：交接 provide 那支 + " +
                "顶栏真输入场发车那支，都真把站点+标签折成种子，而不是空 lambda）：",
            2,
            DesktopSourceTree.desktopLargeScreenOccurrences( "desktopSearchSeedFor("),
        )
        // 截取 provides 分支至 DesktopVeneraTheme 之前的代码段（codeText 已 trim 且去注释），锚点须随主题壳同步更新防静默放宽。
        val branch = DesktopSourceTree.desktopLargeScreenCode()
            .substringAfter("LocalDesktopSearchHandoff provides")
            .substringBefore("DesktopVeneraTheme")
        assertTrue("provide 那一支没把 seed.query 装回 gallerySearchQuery：\n$branch", "gallerySearchQuery = seed.query" in branch)
        assertTrue("provide 那一支没按 seed.source 切来源档：\n$branch", "gallerySearchSource = " in branch)
        assertTrue("provide 那一支没 route 进搜索那一屏：\n$branch", "route = DesktopRoute.GallerySearch" in branch)
        assertTrue("provide 那一支没另起一轮（gallerySearchTick++）——连点同一枚不会重跑：\n$branch", "gallerySearchTick++" in branch)
        // 反向自证闸：若截取结果包含 VeneraSideBar( 说明主题壳锚点失效，立即失败防止断言静默放宽。
        assertTrue(
            "锚失效：分支越过了主题壳那一行（`substringBefore` 没读到 `DesktopVeneraTheme`，" +
                "八成是壳名又改了 —— 同步改这一枚锚，别把闸关掉）：\n$branch",
            "VeneraSideBar" !in branch,
        )
    }

    /**
     * ⑥ 结果卡**接上了点击**（翻正）。
     *
     * 从前这一条钉的是**反面**："屏没有进详情那枚回调形参、所以卡不可能造假开关"（
     * 那档——桌面还没有看图落点，卡摆着不动才诚实）。造出 `DesktopRoute.GalleryPostViewer`
     * 那枚落点后，把这张结果卡接到了**同一枚**落点（与那面墙逐字同形）。于是那条旧判据
     * 若原样留着会变成手册 §4.1 那族最阴的病：**卡已经可点，而一把绿的尺子还在宣称"卡不可能可点"**
     * （假口径继续当有人盯着）。这里按旧判据头注当初的约定**主动翻正**为正向接线判据：
     * 卡片项统一后这三格改盯两处：
     *  1. 屏仍声明那枚**必传** `onOpenPost:` 形参（恰一处），且把回调**原样转发**给共享卡
     *     （`onOpenPost = onOpenPost` 恰一处 —— 形参接了没人转＝读空假绿，这条挡它）；
     *  2. 卡只对可点那一档接点击：`onClick = if (card.openable)` 现在只住在共享那颗 `DesktopWallCard.kt`
     *     恰一处（打码卡仍点不开，A 条）；反向钉屏里那串归零（不许留第二套卡形）；
     *  3. 那张卡真被递进落点：`onOpenPost(card)` 恰三处（右键 / 指针 / 语义），同样住在共享那颗里。
     * ⚠️ 仍禁 `onOpen:` / `combinedClickable` 那两串（屏与共享卡都禁）——接点击走的是
     * `VeneraCard(onClick=…)` 那枚现成形参，换成那两串就说明接线形状漂了，要连 ⑤ 与 README 一起回查。
     */
    @Test
    fun 结果卡接上了看大图那一枚落点且只给可点那一档接() {
        // ① 屏声明那枚必传回调形参恰一处，且原样转发进共享卡（不转发＝假开关）
        assertEquals(
            "屏里 `onOpenPost:` 形参声明该恰一处（卡片项统一后 WallResultCard 已撤，只剩屏自身那一枚）：",
            1,
            DesktopSourceTree.codeOccurrences(screenFile, "onOpenPost:"),
        )
        assertEquals(
            "屏里把回调转发进共享卡 `onOpenPost = onOpenPost` 该恰一处（形参接了而没人转＝读空假绿）：",
            1,
            DesktopSourceTree.codeOccurrences(screenFile, "onOpenPost = onOpenPost"),
        )
        // ② 只有可点那一档接点击（打码卡点不开，A 条）——改口径：唯一卡里
        // 两道闸（右键「查看大图」+ 点击）各吃一次 `if (card.openable)`，全仓仅此一份。
        assertEquals(
            "唯一卡 `DesktopGalleryCard.kt` 里 `if (card.openable)` 该恰两处（右键 + 点击）：",
            2,
            DesktopSourceTree.codeOccurrences(wallCardFile, "if (card.openable)"),
        )
        // ③ 那张卡真把回调消费出去（接了形参而不递＝读空假绿），同样住在唯一卡。
        // 右键「查看大图」+ 指针手势 + 无障碍语义节点（离屏测试走
        // performClick 只能命中语义，语义块自带 openable 反向闸）三处各递一次。
        assertEquals(
            "唯一卡里递出 `onOpenPost(card)` 该恰三处（右键 + 指针 + 语义）：",
            3,
            DesktopSourceTree.codeOccurrences(wallCardFile, "onOpenPost(card)"),
        )
        // 反向对照：搜索屏不再自绘第二套卡形（那枚 openable 判据只许住唯一卡）
        assertEquals(
            "搜索屏里不该再有自绘的 `if (card.openable)`（卡片项已统一进 DesktopGalleryCard）：",
            0,
            DesktopSourceTree.codeOccurrences(screenFile, "if (card.openable)"),
        )
        // 反向对照：接线形状仍走 VeneraCard(onClick=…)，屏与共享卡都不许换成 combinedClickable / onOpen:
        listOf("onOpen:", "combinedClickable").forEach { banned ->
            assertEquals(
                "接线形状漂了（冒出「$banned」，与那面墙的逐字同形断了，⑤ 与 README 要一起回查）：",
                0,
                DesktopSourceTree.codeOccurrences(screenFile, banned) +
                    DesktopSourceTree.codeOccurrences(wallCardFile, banned),
            )
        }
    }
}
