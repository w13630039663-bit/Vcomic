package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GallerySite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 看大图那一屏的**取档与可点**两族判据（值尺，真跑取值，不靠起窗才验得到）。
 *
 * 为什么用值尺而不是无头 UI：lazy 网格在无头树里排不到卡那一行（`DesktopGallerySearchUiTest:184-186`
 * 那把墙尺同一前提，它自己也从不针对卡内容断言）—— 硬写一条点卡只会红在"找不到节点"或误配到页尾把手
 * 而假绿。取档与"这张点不点得开"都是**纯判据**，能直接取值，就用最便宜也最硬的那把尺钉。
 * 接线那一族（路由档 / 恰好一处把手 / 只给 openable 接点击 / 渲桌面那颗屏）归架构尺
 * `DesktopGalleryPostViewerEntryTest`。
 */
class DesktopGalleryPostViewerTest {

    private fun card(
        uid: String = "yande.re:1",
        url: String = "https://img.invalid/preview.jpg",
        fastUrl: String = "",
        largeUrl: String = "",
        masked: Boolean = false,
    ) = DesktopWallCardModel(
        title = "t",
        url = url,
        aspect = 1f,
        masked = masked,
        uid = uid,
        fastUrl = fastUrl,
        largeUrl = largeUrl,
    )

    // ── 取档：开门档 fast 先行、目标档 large 先行，逐级回退不编假地址 ──

    /** ① 三档齐时：开门取 fast、目标取 large（fast 与墙上 preview 多数同址，开门不闪白）。 */
    @Test
    fun 三档齐时开门取fast而目标取large() {
        val c = card(url = "https://i/p.jpg", fastUrl = "https://i/f.jpg", largeUrl = "https://i/l.jpg")
        assertEquals("开门档", "https://i/f.jpg", desktopViewerOpenUrl(c))
        assertEquals("目标档", "https://i/l.jpg", desktopViewerFullUrl(c))
    }

    /** ② 站方没给 fast（缺档留空串）时：开门退到卡上那张、目标仍取 large。绝不返回空、也绝不编一个。 */
    @Test
    fun 缺fast时开门退到卡上那张() {
        val c = card(url = "https://i/p.jpg", fastUrl = "", largeUrl = "https://i/l.jpg")
        assertEquals("开门档回退到 url", "https://i/p.jpg", desktopViewerOpenUrl(c))
        assertEquals("目标档照取 large", "https://i/l.jpg", desktopViewerFullUrl(c))
    }

    /** ③ 站方没给 large（小图条目 large 为空）时：目标退到 fast，fast 也没有则退 url。 */
    @Test
    fun 缺large时目标逐级回退() {
        val withFast = card(url = "https://i/p.jpg", fastUrl = "https://i/f.jpg", largeUrl = "")
        assertEquals("缺 large 时目标退 fast", "https://i/f.jpg", desktopViewerFullUrl(withFast))
        val onlyUrl = card(url = "https://i/p.jpg", fastUrl = "", largeUrl = "")
        assertEquals("连 fast 也没有时退 url", "https://i/p.jpg", desktopViewerFullUrl(onlyUrl))
        assertEquals("两档都缺时开门也退 url", "https://i/p.jpg", desktopViewerOpenUrl(onlyUrl))
    }

    // ── 可点判据：有身份、且没被打码，两点齐了才点得开（安全：打码卡不放大）──

    /** ④ 有 uid 且未打码 ⇒ 点得开。这是"墙上一张正常图"那一档。 */
    @Test
    fun 有身份而没打码的那张点得开() {
        assertTrue("正常卡应当可点", card(uid = "safebooru:7", masked = false).openable)
    }

    /** ⑤ 打码的卡**点不开**（A 条：桌面缺安卓那份成人词表、宁可漏判，也不把已判要遮的图放大铺满屏）。 */
    @Test
    fun 打码的那张一律点不开() {
        assertFalse(
            "被打码的卡有身份也不许点开（否则等于主动放大成人图）",
            card(uid = "yande.re:9", masked = true).openable,
        )
    }

    /** ⑥ 没身份（uid 空）的卡点不开：无头尺里的夹具卡正是这一档 ⇒ 不会摆出一枚按了进空看图的死按钮。 */
    @Test
    fun 没有身份的卡点不开() {
        assertFalse("空 uid 不可点", card(uid = "").openable)
        assertFalse("空白串 uid 不可点", card(uid = "   ").openable)
    }

    /**
     * ⑦ 对照组（扫描/取值类判据最贵的那类假绿防线）：④⑤⑥ 三条不是"恒真"。
     *
     * 若 `openable` 被改成恒 `true`，⑤⑥ 会红；若被改成恒 `false`，④ 会红。这里显式钉"三种取值互不相同"，
     * 让"判式整颗失效"这件事在第一时间露出来，而不是等三条各自 `assertTrue/assertFalse` 有一条恰好还绿。
     */
    @Test
    fun openable三档取值互不相同而不是恒真恒假() {
        val open = card(uid = "yande.re:1", masked = false).openable
        val masked = card(uid = "yande.re:1", masked = true).openable
        val noId = card(uid = "", masked = false).openable
        assertEquals("可点 / 打码 / 无身份 三档的取值：", listOf(true, false, false), listOf(open, masked, noId))
    }

    // ── ：「点标签回搜」那一路的所属站判据（纯函数，值尺直接取值）──

    private fun uidCard(uid: String) = card(uid = uid)

    /** ⑧ booru 三站认得出所属站（uid 前缀 routeKey → 那一枚站）。 */
    @Test
    fun booru三站的uid认得出所属站() {
        assertEquals("yandere 前缀", GallerySite.YANDERE, desktopViewerTagSite(uidCard("yandere:42")))
        assertEquals("gelbooru 前缀", GallerySite.GELBOORU, desktopViewerTagSite(uidCard("gelbooru:7")))
        assertEquals("safebooru 前缀", GallerySite.SAFEBOORU, desktopViewerTagSite(uidCard("safebooru:9")))
    }

    /** ⑨ pixiv 虽在站表里、却**不在桌面来源轴里** ⇒ 交回 null（接上去就是拿日文 tag 去 booru 搜，那堵墙与画无关）。 */
    @Test
    fun pixiv不接点击而认不出的uid也不接() {
        assertNull(
            "pixiv 卡不能认出一个能落的来源档（来源轴 §125 选型⑤剥了它）",
            desktopViewerTagSite(uidCard("pixiv:1")),
        )
        // 认不出/坏形状一律 null —— 这些是无头尺里夹具卡那一档，绝不摆按了没反应（或按错一站）的把手。
        assertNull("空 uid", desktopViewerTagSite(uidCard("")))
        assertNull("没有冒号的坏形状", desktopViewerTagSite(uidCard("nonsense")))
        assertNull("前缀不在站表", desktopViewerTagSite(uidCard("unknownsite:1")))
    }

    /** ⑩ 对照组：⑧⑨ 不是恒真/恒假 —— 认得出与认不出必须各取各的，判式没整颗失效。 */
    @Test
    fun 认得出与认不出各取而判式非恒() {
        val recognized = desktopViewerTagSite(uidCard("yandere:1"))
        val rejected = desktopViewerTagSite(uidCard("pixiv:1"))
        assertNotNull("有 booru 前缀时该认出一枚站", recognized)
        assertNull("pixiv 该被排除", rejected)
        assertTrue("两者取值必须不同（否则⑧⑨其中一条是恒真的空判式）", recognized != rejected)
    }

    // ── UI-2：看图屏 ←/→ 的队列步进 ──

    /** ⑪ 中间页左右各走一步；两端夹住，不越界。 */
    @Test
    fun 看图屏步进在两端夹住() {
        assertEquals("第一张按 ← 仍停第一张", 0, desktopViewerStepIndex(0, 4, -1))
        assertEquals("中间按 → 到下一张", 2, desktopViewerStepIndex(1, 4, 1))
        assertEquals("最后一张按 → 仍停最后一张", 3, desktopViewerStepIndex(3, 4, 1))
    }

    /** ⑫ 空队列交回 0，而不是让调用点拿 -1 去索引。 */
    @Test
    fun 空队列的步进落点为零() {
        assertEquals(0, desktopViewerStepIndex(0, 0, 1))
        assertEquals(0, desktopViewerStepIndex(9, 0, -1))
    }
}
