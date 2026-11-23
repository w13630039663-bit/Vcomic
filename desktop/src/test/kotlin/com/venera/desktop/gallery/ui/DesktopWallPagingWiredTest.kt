package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GalleryContentGuard
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.security.guard.GuardRule
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 那扇门**整条真跑**（首屏攒账 → 页尾 → 续页 → 到底）。
 *
 * ## 这一颗补的是哪一格
 *
 * `DesktopWallLedgerTest`（批 31）跑的是 `fetchWallPage` 那一截，`DesktopGalleryWallFooterTest`
 * 跑的是页尾那一格 —— 中间那一段（**首屏到底有没有把账本建立起来**）两边都不覆盖，而它恰好是
 * 唯一的新增事实：首屏从前交回一面墙而不交回账，所以续页那颗 reducer 一直没有出处。
 * 「首屏没攒账」那一族不崩、不红字，屏上只是"翻不动"，而页码那三本账没人会去数（手册 §0 坑 ㉓）。
 *
 * ## 三条不显然的核对
 *
 * ① 首屏交回的账必须带 `queries`（否则"还发得动吗"与那三句话都无依据）；
 * ② 上屏那些张 ≠ 取回那些条（`HIDE` 档那一段不落屏），两本账都要读得出（批 13 那本账的第五位消费者）；
 * ③ 续页只补**新到的那一段**卡（整批重过守卫会把遮罩读数「入/出」翻倍，而 LazyGrid 会整面重排）。
 */
class DesktopWallPagingWiredTest {

    private val yandere = GallerySite.YANDERE
    private val safebooru = GallerySite.SAFEBOORU

    /** 成人档按 [mode] 处理（`HIDE` 收起 / `BLUR` 遮），黑名单恒不命中 —— 够测"两个主语"那一格。 */
    private class FakeGuard(private val mode: String = "HIDE") : GalleryContentGuard {
        override val nsfwMaskMode: StateFlow<String> = MutableStateFlow(mode)
        override val rules: StateFlow<List<GuardRule>> = MutableStateFlow(emptyList())
        override fun blockedGalleryRule(author: String, tags: List<String>): GuardRule? = null
        override suspend fun addRule(type: String, pattern: String, isRegex: Boolean): Long = -1
    }

    private fun post(site: GallerySite, id: Long, rating: String = "s") = GalleryPost(
        site = site,
        id = id,
        rating = rating,
        tags = "t",
        author = "a",
        previewUrl = "https://img/$site-$id.jpg",
        fileExt = "jpg",
    )

    private val legs = mapOf(yandere to "", safebooru to "")

    /**
     * 两腿 pageSize=2：yandere 三页（2 / 2 / 1，第一页里那张成人档给 ② 用）、
     * Safebooru 两页（2 / 1 ⇒ 第二笔它就到底）。
     */
    private fun boards() = ScriptedBoards(
        pageSize = mapOf(yandere to 2, safebooru to 2),
        pages = mapOf(
            yandere to listOf(
                Result.success(listOf(post(yandere, 1), post(yandere, 2, rating = "e"))),
                Result.success(listOf(post(yandere, 3), post(yandere, 4))),
                Result.success(listOf(post(yandere, 5))),
            ),
            safebooru to listOf(
                Result.success(listOf(post(safebooru, 11), post(safebooru, 12))),
                Result.success(listOf(post(safebooru, 13))),
            ),
        ),
    )

    private fun firstPage(ports: GalleryPorts, guard: GalleryContentGuard?) =
        runBlocking { desktopWallFirstPage(ports, legs, guard = guard, maskMode = "HIDE") }

    private fun more(page: DesktopWallPage, ports: GalleryPorts, guard: GalleryContentGuard?, mask: String) =
        desktopWallPageAfter(
            page,
            runBlocking { fetchWallPage(ports, page.batch, page.batch.queries).getOrThrow() },
            guard, mask, false,
        )

    /**
     * ① 首屏**真把那本账建立起来**（唯一的新增事实）。
     *
     * 少任何一格续页就无从可算：`queries` 空 ⇒ 页尾与"还发得动吗"没依据；`pageBySite` 空 ⇒
     * 下一笔仍发第 1 页（同一批图摆两遍）；`posts` 空 ⇒ 去重键没建立。
     */
    @Test
    fun 首屏交回的是账本而不只是那面墙() {
        val ports = fakeGalleryPorts(boards())
        val page = firstPage(ports, null)
        assertEquals("首屏没把发出去的腿记进账（续页那句因此无依据）：", legs.keys, page.batch.queries.keys)
        assertEquals("首屏没记逐腿页码：", mapOf(yandere to 1, safebooru to 1), page.batch.pageBySite)
        // 没给守卫 ⇒ 不判遮罩（那一档走 `D_守卫 未判`），四张取回四张上屏
        assertEquals("四张取回而四张上屏：", 4, page.cards.size)
        assertEquals("两本账在没守卫那一档应当相等：", page.batch.posts.size, page.cards.size)
        // 对照：那颗落地件真在首屏那趟被走过（而不是新造第二条落地路）
        val code = DesktopSourceTree.codeText(File(DesktopSourceTree.repoRoot, FIRST_PAGE_FILE))
        assertTrue(
            "首屏不走那颗落地件（页码 / 到底 / 张数三本账就会被定两遍）：看不到 desktopWallBatchAfter(",
            code.contains("desktopWallBatchAfter("),
        )
    }

    /**
     * ② `HIDE` 档下**上屏的比取回的少**，而两本账都读得出。
     *
     * 页尾那句数的是上屏的那些；写成数 `batch.posts` 不崩不报错，只在屏上谎报张数
     * （`DesktopWallCountSourceTest` 批 13 在搜索那屏抓过同一颗，这次换到页尾那一格）。
     */
    @Test
    fun 遮罩挡掉的那些张不算进页尾那句() {
        val page = firstPage(fakeGalleryPorts(boards()), FakeGuard("HIDE"))
        assertEquals("取回的仍是四张（那是第一本账）：", 4, page.batch.posts.size)
        assertEquals("成人档那张在 HIDE 档不该上屏：", 3, page.cards.size)
        val footer = desktopGalleryWallFooter(page.batch, page.cards.size, DesktopGalleryMorePhase.Idle, unit = "张")
        assertEquals("那句数的必须是上屏的三张而不是取回的四张：", "下一页（已取 3 张）", footer.moreLabel)
        assertTrue(
            "读数没把两本账各念一词：" + footer.reading,
            footer.reading.contains("取=4") && footer.reading.contains("屏=3"),
        )
    }

    /** ③ 续页只补**新到的那一段**（整批重过守卫 = 遮罩读数翻倍 + 整面墙重排）。 */
    @Test
    fun 续页那一趟只补新到的那一段() {
        val boards = boards()
        val ports = fakeGalleryPorts(boards)
        val first = firstPage(ports, null)
        val page = more(first, ports, null, "OFF")
        assertEquals("墙上该是四张 + 三张：", 7, page.cards.size)
        assertEquals("屏上出现了重复条目（去重键没吃上一批已有的）：", 7, page.cards.map { it.url }.distinct().size)
        assertEquals("页码账没逐腿推进：", mapOf(yandere to 2, safebooru to 2), page.batch.pageBySite)
        // 档位从上一批那枚取（不是调用点给）：翻之前翻之后同一档，否则那面墙会"翻页换清晰度"。
        assertEquals("续页换了缩略档（两屏各挑一档就会漂）：", first.quality, page.quality)
        assertTrue("Safebooru 只给一张而没被记到底：" + page.batch.sitesDone, safebooru in page.batch.sitesDone)
        assertEquals(
            "发出去的页码对不上账（逐腿各算那一判的读数面）：",
            mapOf(yandere to listOf(1, 2), safebooru to listOf(1, 2)),
            boards.calls.groupBy { it.site }.mapValues { (_, cs) -> cs.map { it.page }.sorted() },
        )
    }

    /** ④ 到底那一腿不再发车，而两腿都到底时页尾那句改口、把手撤掉。 */
    @Test
    fun 两腿都到底时那一格改口而不再摆把手() {
        val boards = boards()
        val ports = fakeGalleryPorts(boards)
        var page = firstPage(ports, null)
        repeat(2) { page = more(page, ports, null, "OFF") }
        assertTrue("第三笔之后 yandere 那张不满页而没被记到底：" + page.batch.sitesDone, yandere in page.batch.sitesDone)
        assertEquals("三笔之后墙上该是 4 + 3 + 1 张：", 8, page.cards.size)
        val footer = desktopGalleryWallFooter(page.batch, page.cards.size, DesktopGalleryMorePhase.Idle, unit = "张")
        assertNull("都到底了而那一枚把手还在：" + footer.moreLabel, footer.moreLabel)
        assertTrue("到底那句没念出来：" + footer.lines, footer.lines.any { it.contains("没有更多") })
        assertFalse(
            "到底那一站被再问了一次（屏上写着到底而底下还在发车）：",
            boards.calls.filter { it.site == safebooru }.any { it.page >= 3 },
        )
        // 对照：那一屏的读数仍然数得出逐腿页码（翻页算错只有对账抓得到）
        assertTrue("读数没念逐腿页码：" + footer.reading, footer.reading.contains("页=YANDERE:3、SAFEBOORU:2"))
    }

    /** ⑤ 全腿没答上：墙是空的而那本账带着原因，那一枚把手照摆（那是唯一的出路）。 */
    @Test
    fun 全腿没答上时空墙仍带着出路() {
        val ports = fakeGalleryPorts(
            ScriptedBoards(
                pageSize = mapOf(yandere to 2, safebooru to 2),
                pages = mapOf(
                    yandere to listOf(Result.failure(IllegalStateException("这一站超时了"))),
                    safebooru to listOf(Result.failure(IllegalStateException("这一站 403"))),
                ),
            ),
        )
        val page = firstPage(ports, null)
        assertTrue("账本没到货（那本账必须存在，否则页尾无从可说）：", page.batch.queries.isNotEmpty())
        assertEquals("两站都没答上而墙上摆了东西：", 0, page.cards.size)
        val footer = desktopGalleryWallFooter(page.batch, page.cards.size, DesktopGalleryMorePhase.Idle, unit = "张")
        assertNotNull("空屏时把唯一的出路撤了：" + footer.moreLabel, footer.moreLabel)
        assertTrue(
            "那两句缺席没逐站点名：" + footer.lines,
            footer.lines.single().contains("yande.re·网络超时") && footer.lines.single().contains("Safebooru·无法访问"),
        )
        assertFalse("全腿没答上被判成到底（那是空屏不是看完了）：", page.batch.atEnd)
    }

    /** ⑥ 两屏共用那一颗守卫件，而整棵树只许**一处**起口。 */
    @Test
    fun 两屏共用那一颗守卫件而只有一处起口() {
        val code = DesktopSourceTree.codeText(File(DesktopSourceTree.repoRoot, FIRST_PAGE_FILE))
        assertTrue("取数路不调 policy ⇒ 墙上一张都不遮（安全评审 B）：", code.contains("DesktopGalleryWallPolicy.of("))
        // 整棵树只许一处调它：多一处 = 第二条取数路自己拼了一遍遮罩（一定漂）
        val calls = DesktopSourceTree.desktopOwnSources()
            .sumOf { DesktopSourceTree.codeOccurrences(it, "DesktopGalleryWallPolicy.of(") }
        assertEquals("遮罩判据的起口分布变了（现状：那颗件一处，续页转交同一颗）：$calls", 1, calls)
        // 对照：件本体不算调用点（否则上面那条恒真于"一处"）；文件读空也恒真 ⇒ 两样都钉
        val policyFile = File(DesktopSourceTree.repoRoot, "desktop/src/main/kotlin/com/venera/desktop/gallery/ui/DesktopGalleryWallPolicy.kt")
        assertEquals("对照组失效：policy 文件里出现了调用式", 0, DesktopSourceTree.codeOccurrences(policyFile, "DesktopGalleryWallPolicy.of("))
        assertTrue(
            "对照组失效：policy 文件没读到内容（读空会让上面两条恒真）",
            DesktopSourceTree.codeText(policyFile).contains("fun of("),
        )
        // 对照：那一扇门真开了（接的就是这两处调用）
        val shell = DesktopSourceTree.desktopLargeScreenCode()
        assertTrue("壳里没开门（`fetchWallPage` 在 main 里仍是零调用点）：", shell.contains("fetchWallPage(ports"))
        assertTrue("壳里续页不落地（翻回来的那一批被丢了）：", shell.contains("desktopWallPageAfter("))
    }

    /**
     * ⑦ 一笔都不该发车而原因是「没配账号」时（画廊标签搜索选了 Gelbooru 而没账号）：
     * 那本账仍要带着缺席原因回来，页尾要念得出「没发车」，而那一枚把手**不许摆**。
     *
     * 这一条钉的是现量到的真 bug。坏写法有两种，各红一处：
     *  - 把那腿留在 `queries` 里 ⇒ 它永远进不了 `sitesDone`，`moreLabel` 恒在而恒发不出货
     *    （`GallerySearchSource.availableLegs` 的注释自陈的正是这一病）；
     *  - 剔了腿而不交 `silent` ⇒ 空账不带原因，屏上只剩空态那句「那一站今天没回图」，
     * 把**我们的闸**说成**站方的空结果**（修掉的那族假话）。
     */
    @Test
    fun 一笔都不该发车时那本账仍带着原因而把手不摆() {
        val boards = ScriptedBoards(pageSize = mapOf(yandere to 2), pages = emptyMap())
        val silent = mapOf(GallerySite.GELBOORU to "没配账号")
        val page = runBlocking { desktopWallFirstPage(fakeGalleryPorts(boards), queries = emptyMap(), silent = silent) }
        assertTrue("一笔都没发出去而那本账不存在（页尾无从可说）：", page.batch.queries.isEmpty())
        assertEquals("剔掉的腿没把原因带进那本账：", silent, page.batch.failures)
        assertEquals("发车前剔腿却还是发了（白给一笔注定 401 的请求）：", 0, boards.calls.size)
        assertFalse("一笔都没发出去被判成到底（那是没发车不是看完了）：", page.batch.atEnd)
        val footer = desktopGalleryWallFooter(page.batch, page.cards.size, DesktopGalleryMorePhase.Idle, unit = "张")
        assertNull("一笔都没发出去而那一枚把手还摆着：" + footer.moreLabel, footer.moreLabel)
        assertTrue(
            "页尾那句没念得出「没发车——为什么」（那就只剩空态那句谎话）：" + footer.lines,
            footer.lines.any { it.contains("Gelbooru·未登录") },
        )
    }

    private companion object {
        const val FIRST_PAGE_FILE = "desktop/src/main/kotlin/com/venera/desktop/gallery/ui/DesktopWallFirstPage.kt"
        const val SHELL_FILE = "desktop/src/main/kotlin/com/venera/desktop/VeneraDesktopLargeScreen.kt"
    }
}
