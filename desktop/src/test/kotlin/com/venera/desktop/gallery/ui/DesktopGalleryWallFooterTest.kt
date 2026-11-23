package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 画廊那面墙**页尾那一格**的判据。
 *
 * ## 这一批评的是"那一面墙只有第一页"
 *
 * 续页那颗 `fetchWallPage` 自批 23 就在跑，批 31 还拿真脚本端口把三本账跑通过
 * （`DesktopWallLedgerTest` 六条），而 `desktop/src/main` 里它的**调用点是零** ——
 * 「有逻辑没有门」那一族（批 114 / 115 / 118 / 119 / 120 同形，而那一族的成因由
 * 记在台账里：`DesktopUnusedInternalTest` ①③ 只有 KDoc 而没有 `@Test` ⇒ 惰）。
 * 这一批接门（壳里那两处 effect），而门要能被量：下面这十档各挡一种坏形状。
 *
 * ## 为什么判据住在纯件而不在屏（与屏那两条无头判据不是重复）
 *
 * 纯件量"算得对不对"，屏量"真摆上屏、真按得动"（六颗纯函数绿着
 * 而屏上一行都不渲）。所以这里只算，无头那一族在 `screens/DesktopGalleryWallUiTest.kt`，
 * 而**台架喂原始状态**（喂写死的成品 ⇒ 撤判据一条都不红）。
 */
class DesktopGalleryWallFooterTest {

    private val yandere = GallerySite.YANDERE
    private val safebooru = GallerySite.SAFEBOORU

    private fun post(site: GallerySite, id: Long) = GalleryPost(
        site = site,
        id = id,
        tags = "t",
        previewUrl = "https://img/${site.routeKey}-$id.jpg",
        fileExt = "jpg",
    )

    /** 到货了、两腿各落第 1 页、都还能再发的那一份账。 */
    private fun arrived(onScreen: Int = 12) = DesktopGalleryBatch(
        seed = 7L,
        pageBySite = mapOf(yandere to 1, safebooru to 1),
        queries = mapOf(yandere to "", safebooru to ""),
        posts = (1L..20L).map { post(if (it % 2 == 0L) safebooru else yandere, it) },
    ).let { it to onScreen }

    /** ① 「还没到货」与「到货了而零张」是两句话：前者整颗把手都不摆。 */
    @Test
    fun 还没到货时那枚把手不摆而到货零张照摆() {
        val empty = desktopGalleryWallFooter(null, onScreen = 0, DesktopGalleryMorePhase.Idle, unit = "张")
        assertNull(
            "账本还没到货就摆那枚把手 ⇒ 按下去发一笔空续页（reducer 对空 queries 交回原样一颗）= 假开关",
            empty.moreLabel,
        )
        assertEquals("那几句也不许凭空念（账本不在手，谁都没法说）", emptyList<String>(), empty.lines)
        // 对照：到货了、账本里有两腿、只是这一屏一张都没摆 ⇒ 那一枚照摆（那是出路）
        val arrived = desktopGalleryWallFooter(
            DesktopGalleryBatch(seed = 1L, queries = mapOf(yandere to "")),
            onScreen = 0,
            DesktopGalleryMorePhase.Idle,
            unit = "张",
        )
        assertTrue("到货而零张时那枚把手不见了（用户就没有出路）：" + arrived.moreLabel, arrived.moreLabel != null)
    }

    /** ② 到底那一档不摆把手，而**必须念得出为什么**（复用现成那句，屏不另拼）。 */
    @Test
    fun 到底那一档不摆把手而那句念得出() {
        val (batch, onScreen) = arrived()
        val done = batch.copy(sitesDone = setOf(yandere, safebooru))
        assertTrue("两腿都到底而 atEnd 为假（页尾那句就念不出来）", done.atEnd)
        val footer = desktopGalleryWallFooter(done, onScreen, DesktopGalleryMorePhase.Idle, unit = "张")
        assertNull("到底了还摆那枚把手 = 屏上写着到底而下面还能点（假读数那一族）", footer.moreLabel)
        assertEquals(
            "到底那句少了站名或换了措辞（那句住在 desktopExhaustedReading，屏不许另拼一遍）：",
            listOf("没有更多：yande.re、Safebooru"),
            footer.lines,
        )
    }

    /** ③ 一笔都没发出去（`queries` 空）时不叫到底，也不摆把手。 */
    @Test
    fun 一笔都没发出去时既不叫到底也不摆把手() {
        val none = DesktopGalleryBatch(seed = 3L)
        assertFalse("空 queries 被判成到底（那是空屏不是看完）", none.atEnd)
        val footer = desktopGalleryWallFooter(none, onScreen = 0, DesktopGalleryMorePhase.Idle, unit = "张")
        assertNull("一笔都没发出去而摆那枚把手 ⇒ 发了等于没发（页码账不推进、屏上不动）", footer.moreLabel)
        assertEquals("那一档页尾一句都不念：" + footer.lines, emptyList<String>(), footer.lines)
    }

    /**
     * ④ **失败那一档照摆**那一枚把手（这一批真正的取舍）。
     *
     * 的扰动实验把「Failed 折成 End」抓到过 2 红，同一件事在这里是第二次量：
     * 撤掉那一枚把手，用户就没有重试的路，而屏上那些卡还在（追加语义只掉这一笔）。
     */
    @Test
    fun 失败那一档把手照摆而成因上屏() {
        val (batch, onScreen) = arrived()
        val failed = desktopGalleryWallFooter(
            batch,
            onScreen,
            DesktopGalleryMorePhase.Failed("超过 12s 没返回"),
            unit = "张",
        )
        assertTrue("失败那一档把把手撤了（用户就没有重试的路）：" + failed.moreLabel, failed.moreLabel != null)
        // 失败那句念归档短句（屏上不透原串），点名档位即可。
        assertTrue("那句没点名归档原因：" + failed.lines, failed.lines.first().contains("网络超时"))
        assertTrue("那句把原串透传上屏了：" + failed.lines, !failed.lines.first().contains("没返回"))
        assertTrue("那句没替已显示内容担保：" + failed.lines, failed.lines.first().contains("已显示内容保留"))
        // 到底 + 失败两档互斥：那一档的判据不许在这里重拼（`atEnd` 归持有件）。
        val both = desktopGalleryWallFooter(
            batch.copy(sitesDone = setOf(yandere, safebooru)),
            onScreen,
            DesktopGalleryMorePhase.Failed("超时"),
            unit = "张",
        )
        assertNull("都到底了而失败那一档又把把手摆回来：" + both.moreLabel, both.moreLabel)
        assertTrue("到底那句被失败那句挤掉了（两句各有各的账）：" + both.lines, both.lines.any { it.contains("没有更多") })
    }

    /** ⑤ 正在取那一档：把手让位给那一句（同一笔不许发两遍）。 */
    @Test
    fun 正在取那一档把手让位给那一句() {
        val (batch, onScreen) = arrived()
        val loading = desktopGalleryWallFooter(batch, onScreen, DesktopGalleryMorePhase.Loading, unit = "张")
        assertNull("还在取就把把手再摆一次 ⇒ 连点把两页并排贴上来而页码账只推进一次", loading.moreLabel)
        assertEquals("那句没数上屏的那些张：" + loading.lines, listOf("加载中…（已显示 12 张）"), loading.lines)
    }

    /** ⑥ 量词是入参而两种都喂过（同一族第 N 次：写死其一在一种值下不可分辨）。 */
    @Test
    fun 量词是入参而两种都喂过() {
        val zhang = requireNotNull(desktopGalleryWallFooter(arrived().first, 12, DesktopGalleryMorePhase.Idle, "张").moreLabel)
        val tiao = requireNotNull(desktopGalleryWallFooter(arrived().first, 12, DesktopGalleryMorePhase.Idle, "条").moreLabel)
        assertTrue("那枚把手没吃量词（张）：$zhang", zhang.contains("12 张"))
        assertTrue("那枚把手没吃量词（条）：$tiao", tiao.contains("12 条"))
        assertNotEqualsSame("两种单位算出同一句 = 那颗件写死了量词", zhang, tiao)
    }

    /**
     * ⑦ 那一枚把手数的是**上屏**那些张，不是**取回**那些张（批 13 那本账第五位消费者）。
     *
     * `batch.posts` 是过了混排去重的取回条目，而 `HIDDEN` 那一档不落屏 ⇒ 两个主语两个数。
     * 写 `batch.posts.size` 不崩不报错，只在页尾谎报张数（"已取 20 张"而墙上只有 12 张）。
     */
    @Test
    fun 把手那句数的是上屏那些而不是取回那些() {
        val (batch, onScreen) = arrived(onScreen = 12)
        val label = requireNotNull(desktopGalleryWallFooter(batch, onScreen, DesktopGalleryMorePhase.Idle, "张").moreLabel)
        assertTrue("那句没数上屏的 12 张：" + label, label.contains("12 张"))
        assertFalse("那句数到了取回的 20（两个主语塌成一个）：" + label, label.contains("20"))
        // 读数里两本账各念各的（对账才看得见"少了 8 张"）。
        val reading = desktopGalleryWallFooter(batch, onScreen, DesktopGalleryMorePhase.Idle, "张").reading
        assertTrue("读数没分开两本账：" + reading, reading.contains("取=20") && reading.contains("屏=12"))
    }

    /**
     * ⑧ 到底那几站的点名次序恒为站表序（并发取数回来的 `Set` 插入次序不稳）。
     *
     * 传入一份**反着写**的 `sitesDone`，交回的名单仍按 `GallerySite.entries` ——
     * 否则同一件事在两帧里换了位置（批 12 教过的那一条）。
     */
    @Test
    fun 到底名单跟着站表走而跟着集合插入次序不跟() {
        val (batch, onScreen) = arrived()
        val reversed = batch.copy(sitesDone = linkedSetOf(safebooru, yandere))
        val forward = desktopGalleryWallFooter(batch.copy(sitesDone = linkedSetOf(yandere, safebooru)), onScreen, DesktopGalleryMorePhase.Idle, "张")
        val backward = desktopGalleryWallFooter(reversed, onScreen, DesktopGalleryMorePhase.Idle, "张")
        assertEquals("反着插入那一站就把点名次序换了（同一件事在两帧里换了位置）：", forward.lines, backward.lines)
        assertTrue(
            "点名次序不是站表序（yande.re 在 Safebooru 之前）：" + backward.lines,
            backward.lines.single().indexOf("yande.re") < backward.lines.single().indexOf("Safebooru"),
        )
    }

    /** ⑨ 缺席那一句只念**这一轮的账**：并账的规则住在 `desktopFailureLedgerAfter`，这里不并、只念。 */
    @Test
    fun 缺席那一句点名发出去的站而不动并账() {
        val batch = DesktopGalleryBatch(
            seed = 5L,
            queries = mapOf(yandere to "", safebooru to ""),
            failures = mapOf(safebooru to "超过 12s 没返回", yandere to "没配 API 凭据"),
        )
        val lines = desktopGalleryWallFooter(batch, onScreen = 4, DesktopGalleryMorePhase.Idle, unit = "张").lines
        assertEquals(
            "两句缺席只点名站与短原因、不回显原串：",
            listOf("yande.re·无法访问；Safebooru·网络超时"),
            lines,
        )
        val silent = desktopGalleryWallFooter(
            DesktopGalleryBatch(seed = 6L, queries = mapOf(yandere to ""), failures = mapOf(GallerySite.GELBOORU to "没配账号")),
            onScreen = 1,
            DesktopGalleryMorePhase.Idle,
            unit = "张",
        ).lines
        assertEquals("未登录那一档只念短标签：", listOf("Gelbooru·未登录"), silent)
    }

    /**
     * ⑩ 读数三档各一词：「还没到货」／「到货零张」／「到底」。
     *
     * 那一格是包内取证对逐腿页码的唯一出处（手册 §0 坑 ㉓：少一批图只有对账抓得到），
     * 三档混成一词就等于没量。
     */
    @Test
    fun 取证那三档各念一词() {
        assertEquals(
            "「还没到货」那一档：" ,
            "D_画廊 翻页 腿=0 页=无 到底=无 取=还没到货 屏=0 剔重=0 相位=闲",
            desktopGalleryWallReading(null, onScreen = 0, DesktopGalleryMorePhase.Idle),
        )
        assertEquals(
            "「到货而零张」那一档（与上面那一句必须读得开）：",
            "D_画廊 翻页 腿=2 页=无 到底=否 取=0 屏=0 剔重=0 相位=取",
            desktopGalleryWallReading(
                DesktopGalleryBatch(seed = 1L, queries = mapOf(yandere to "", safebooru to "")),
                onScreen = 0,
                DesktopGalleryMorePhase.Loading,
            ),
        )
        assertEquals(
            "逐腿页码没念出来（翻页算错就查不到是哪一腿）：",
            "D_画廊 翻页 腿=2 页=YANDERE:2、SAFEBOORU:1 到底=是 取=12 屏=8 剔重=4 相位=败(超时)",
            desktopGalleryWallReading(
                DesktopGalleryBatch(
                    seed = 2L,
                    pageBySite = mapOf(yandere to 2, safebooru to 1),
                    queries = mapOf(yandere to "", safebooru to ""),
                    posts = (1L..12L).map { post(yandere, it) },
                    sitesDone = setOf(yandere, safebooru),
                    droppedDuplicates = 4,
                ),
                onScreen = 8,
                DesktopGalleryMorePhase.Failed("超时"),
            ),
        )
    }

    /**
     * ⑪ 「摆不摆」与「发不发」同一颗判据（屏与 effect 各判一遍一定漂）。
     *
     * 壳里那颗 effect 必须调 [desktopWallMoreAllowed]，不许自己拼 `atEnd` ——
     * 遮罩那一族（`DesktopMaskJudgementSingleSourceTest` ③ 数分布）钉的是同一件事，
     * 只是这次量的是翻页。
     */
    @Test
    fun 壳里那一路早退复用那颗件而不自拼() {
        val shell = DesktopSourceTree.desktopLargeScreenCode()
        // 那一次判据被两处吃（页尾与 effect），而"effect 没吃它"这件事只有扫壳才看得见。
        val effectGate = Regex("desktopWallMoreAllowed\\(page\\.batch").containsMatchIn(shell)
        assertTrue(
            "续页那颗 effect 没吃 `desktopWallMoreAllowed(那一颗件` ⇒ 屏与 effect 各判一遍，" +
                "改一处另一处不跟着改（摆出来的把手按不动 / 撤掉的把手还能发）",
            effectGate,
        )
        assertFalse("壳里自己拼了「到底」那一判（同一件事两个出处）：", Regex("""wallPage[?.]*\.batch\.atEnd""").containsMatchIn(shell))
        assertFalse("壳里自己拼了那枚把手（屏不许判摆不摆）：", Regex("""atEnd \|\||queries\.isEmpty\(\) \|\|""").containsMatchIn(shell))
        // 对照：那颗件真被两处吃（页尾 + effect），一处都没有就是假接线。
        val main = DesktopSourceTree.desktopMainSources().sumOf { DesktopSourceTree.codeOccurrences(it, "desktopWallMoreAllowed(") }
        assertTrue("两处消费者不见了（页尾与 effect 各要一处，实测 $main 处）", main >= 2)
        // 对照：续页那颗取数件从此有 main 里的调用点了（接的就是那一扇门）。
        val fetch = DesktopSourceTree.desktopMainSources().sumOf { DesktopSourceTree.codeOccurrences(it, "fetchWallPage(") }
        assertTrue(
            "`fetchWallPage` 在 main 里仍只有声明（$fetch 处 = 只有那一行声明 ⇒ 门还是没接）：批 23 立了逻辑而没人开门，本批的账不许再欠一批",
            fetch >= 2,
        )
    }

    private fun assertNotEqualsSame(why: String, a: String, b: String) {
        assertTrue("$why（两边都是「$a」）", a != b)
    }
}
