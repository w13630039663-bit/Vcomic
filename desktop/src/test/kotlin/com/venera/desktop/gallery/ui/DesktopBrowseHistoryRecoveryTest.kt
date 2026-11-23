package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.desktop.gallery.data.DesktopBrowseView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「回原卡」那一路两颗纯件的**值尺**。
 *
 * ## 为什么这两颗值得单独一把尺
 *
 * 那五档文案是「回原卡」这一格对用户唯一的说话方式：按下去而拿不回来时，屏上念的必须是**那一种**坏，
 * 而不是合并成一句「拿不回来」。合并在哪儿最诱人？① 网络塌了 与 ② 站方说没有 都是"没拿到东西"，
 * 把 ① 折进 ② 就是把「我这头断了」谎报成「站方删了那张」—— 本仓记过一整族的"两档合流"（
 * 把 Failed 折进 End 那一档时红过两条，同一课）。
 *
 * 另一颗 `desktopBrowsePostOfUid` 钉的是**复合键同轨**：卡上的 `uid` 与帖子的 `uid` 必须同一把尺
 * （`站点routeKey:id`），而 `desktopWallCardsOf` 里那颗去重键写的是 `"${site}:${id}"`（枚举名 `YANDERE`）。
 * 拿错那一把就永远查不到，而单看每一颗都自洽 —— 手册里那条「复合键两端不同轨」的债就是这一形。
 * 所以这里有一条**反例夹具**：同一张图两种键形，认回必须走 `uid` 那一把而**认不出**去重键那一形。
 */
class DesktopBrowseHistoryRecoveryTest {

    private fun post(
        site: GallerySite = GallerySite.YANDERE,
        id: Long = 123L,
    ) = GalleryPost(site = site, id = id)

    private fun row(
        site: GallerySite = GallerySite.YANDERE,
        id: Long = 123L,
    ) = DesktopBrowseView(site = site, postId = id, thumbUrl = "https://p/$id.jpg", viewedAt = 1_000L)

    private fun card(masked: Boolean = false, uid: String = "yandere:123") = DesktopWallCardModel(
        title = "t",
        url = "https://p/123.jpg",
        aspect = 1f,
        masked = masked,
        uid = uid,
    )

    /** ① 网络塌了：念「取不回那一张」并带成因，绝不念成「站方说没有这一张」。 */
    @Test
    fun 取数塌了那一档点名成因() {
        val note = desktopBrowseRecoveryNote(
            row = row(),
            fetched = null,
            failure = IllegalStateException("conn reset"),
            card = null,
        )
        assertTrue("那一档没点名是取数塌了：$note", note!!.startsWith("取不回那一张："))
        assertTrue("那一句没带站方原话：$note", note.contains("conn reset"))
        assertTrue(
            "把「我这头断了」折成「站方说没有」是假话：$note",
            !note.contains("站方说没有这一张"),
        )
    }

    /** ② 站方答了而没有：念那一句而**不**带异常类名（那确实是站方的答案，不是我这头断了）。 */
    @Test
    fun 站方说没有那一张() {
        val note = requireNotNull(desktopBrowseRecoveryNote(row(), null, null, null))
        assertEquals("那一档该只念站方那句：", "那张拿不回来：站方说没有这一张", note)
    }

    /** ③ 串号：答回来的站或号与那一行不符 ⇒ 不许切进那一张的看图屏。 */
    @Test
    fun 答的不是那一行() {
        val note = requireNotNull(
            desktopBrowseRecoveryNote(row(id = 123L), post(id = 999L), null, card()),
        )
        assertTrue("那一档没念出答回来的是哪一张：$note", note.startsWith("站方答的不是那一行："))
        assertTrue("那一句没带答回来的号：$note", note.contains("#999"))
    }

    /** ④ 拿到了而投不出卡（被屏蔽规则藏了）：那句要说「藏」，不能说「站方没有」。 */
    @Test
    fun 投不出卡念本机屏蔽而不念站方没有() {
        val note = requireNotNull(desktopBrowseRecoveryNote(row(), post(), null, null))
        assertTrue("那一档没念本机那套规则：$note", note.contains("屏蔽规则"))
        assertTrue("不许把「本机藏了」念成「站方没有」：$note", !note.contains("站方说没有"))
    }

    /** ⑤ 投出来了而要遮：不放大成人图，切路由那一步不发生。 */
    @Test
    fun 要遮的那张不进看图屏() {
        val note = requireNotNull(desktopBrowseRecoveryNote(row(), post(), null, card(masked = true)))
        assertTrue("那一档没念「要遮着看」：$note", note.contains("要遮着看"))
        assertTrue("那一句要说明不放大成人图：$note", note.contains("不放大成人图"))
    }

    /** ⑥ 成功那一档交回 null（= 已经切进看图屏，屏上不留一句多余的话）。 */
    @Test
    fun 拿到了真卡就交回null而不念一句() {
        assertNull(
            "投得出可点的卡时不该念任何东西：",
            desktopBrowseRecoveryNote(row(), post(), null, card()),
        )
    }

    /** ⑦ 认回完整帖走 `uid` 那一把尺。 */
    @Test
    fun 按uid认回完整帖() {
        val p = post(id = 456L)
        assertEquals(p, desktopBrowsePostOfUid(listOf(post(id = 1L), p), "yandere:456"))
    }

    /** ⑧ 反例：拿 `desktopWallCardsOf` 那颗去重键形（枚举名 `YANDERE:123`）来查 ⇒ 认不出。
     *  这条钉的是"两把键不同轨"这件事本身：哪天有人把认回改成比 `site:name + id`，
     *  这里会先红，而界面上表现是「点了没反应」——那正是本仓最贵的那一族债。 */
    @Test
    fun 去重键那一形认不出() {
        assertNull(
            "把去重键形（枚举名）当 uid 用会永远查不到 —— 这条必须认不出：",
            desktopBrowsePostOfUid(listOf(post(id = 123L)), "YANDERE:123"),
        )
    }

    /** ⑨ 空串与查不到都交回 null 而不抛：那是「念得出成因」的事，不是崩窗的事。 */
    @Test
    fun 空uid与查不到都交回null而不抛() {
        assertNull(desktopBrowsePostOfUid(listOf(post()), ""))
        assertNull(desktopBrowsePostOfUid(listOf(post()), "safebooru:777"))
        assertNull(desktopBrowsePostOfUid(emptyList(), "yandere:123"))
    }
}
