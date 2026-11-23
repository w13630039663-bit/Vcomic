package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 推荐档的三条本地判据：只留静态图、同画师配额打散、池子站表（Safebooru 出、Konachan 进）。
 *
 * 这三条都不联网、也不碰共享件，所以能直接钉在纯函数上。
 */
class DesktopRecommendDisperseTest {

    private fun post(id: Long, author: String, ext: String = "jpg") =
        GalleryPost(site = GallerySite.YANDERE, id = id, author = author, fileExt = ext)

    @Test
    fun `gif 与视频不进推荐档`() {
        assertTrue(desktopRecommendKeepsStatic(post(1, "a")))
        assertFalse(desktopRecommendKeepsStatic(post(2, "a", ext = "gif")))
        assertFalse(desktopRecommendKeepsStatic(post(3, "a", ext = "mp4")))
        assertFalse(desktopRecommendKeepsStatic(post(4, "a", ext = "webm")))
        // 站方大小写不统一：大写扩展名同样要挡住。
        assertFalse(desktopRecommendKeepsStatic(post(5, "a", ext = "GIF")))
    }

    @Test
    fun `同画师在前两轮里最多两张且不相邻`() {
        // 三位画师各三张（正是热门池里"同一人连着上榜"的形状）。
        val posts = (1L..3L).map { post(it, "alpha") } +
            (4L..6L).map { post(it, "beta") } +
            (7L..9L).map { post(it, "gamma") }
        val out = desktopRecommendDisperse(posts)

        assertEquals("打散只改次序，不许丢条目", posts.size, out.size)
        val head = out.take(6).map { recommendAuthorKey(it) }
        head.groupBy { it }.forEach { (author, group) ->
            assertTrue("$author 在前两轮里出现了 ${group.size} 张（上限 2）", group.size <= 2)
        }
        head.zipWithNext().forEach { (a, b) ->
            assertTrue("同一画师相邻：$a", a != b)
        }
    }

    @Test
    fun `单一画师刷屏时后位那张被推到别家之后 且一条不丢`() {
        // 六张同一人 + 三张别家：上限之外的必须离开首位区。
        val posts = (1L..6L).map { post(it, "himeno_nanako") } + (7L..9L).map { post(it, "other") }
        val out = desktopRecommendDisperse(posts)

        assertEquals(posts.size, out.size)
        val keys = out.map { recommendAuthorKey(it) }
        val thirdHimeno = keys.withIndex().filter { it.value == "himeno_nanako" }.map { it.index }[2]
        val firstOther = keys.indexOf("other")
        assertTrue("第 3 张 himeno 应排在别家之后，实际 index=$thirdHimeno", thirdHimeno > firstOther)
    }

    @Test
    fun `画师名空白时退回作品号 不会互相当成同一人`() {
        val a = post(11, "")
        val b = post(12, "")
        assertTrue(recommendAuthorKey(a) != recommendAuthorKey(b))
    }

    @Test
    fun `推荐池子换成 yande 加 Gelbooru 加 Konachan`() {
        assertTrue(desktopRecommendPoolSites.contains(GallerySite.KONACHAN))
        assertFalse("Safebooru 已从推荐池撤出", desktopRecommendPoolSites.contains(GallerySite.SAFEBOORU))
    }

    @Test
    fun `避让环记得住画师 不只记作品号`() {
        // 用不会与真数据撞的名字，免得污染同 JVM 里其它用例。
        val author = "test-author-avoid-ring"
        val uid = "test-uid-avoid-ring"
        assertFalse(DesktopRecommendedAvoidanceRing.isRecent(uid))
        assertFalse(DesktopRecommendedAvoidanceRing.isRecentAuthor(author))

        DesktopRecommendedAvoidanceRing.record(uids = listOf(uid), authorKeys = listOf(author))

        assertTrue(DesktopRecommendedAvoidanceRing.isRecent(uid))
        assertTrue("画师级避让必须生效：只记 uid 时换张新图就绕过了", DesktopRecommendedAvoidanceRing.isRecentAuthor(author))
    }
}
