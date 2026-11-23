package com.venera.desktop.detail

import com.venera.engineprobe.DesktopComment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 详情评论那一屏的**判读纯件**（值尺）。
 *
 * 每一条判据都对着对岸那条**算式**（`ComicDetailViewModel.DetailCommentState.received` +
 * `CommentLoadStatus` + `DetailActionButton`），桌面不重发明那几条判式 —— 漂了就是一处翻得动、
 * 一处翻不动；一处念"未提供"、一处念"还没读"。
 */
class DesktopComicCommentsTest {

    private fun c(id: String, user: String = "读者", content: String = "内容") = DesktopComment(
        id = id, userName = user, avatar = null, content = content,
        time = null, replyCount = null, score = 0, voteStatus = 0, isLiked = false,
    )

    private fun initial() = desktopCommentsInitialView()

    @Test
    fun `起手念加载中而不提前念未提供`() {
        // 起手 isLoading=true && canLoad=false。对岸的 CommentLoadStatus 那一支: isLoading 分支**先**判,
        // 所以未提供那一句要等 loading 落下来才轮到 —— 起手若念"未提供"那是假话（还没问完源）。
        val v = initial()
        assertTrue(v.isLoading)
        assertFalse(v.loaded)
        assertEquals("评论加载中…", desktopCommentsStatusNote(v))
        // 空态那一句**绝不**在起手时念（那是"读了确实没有"的结论）。
        assertNull(desktopCommentsEmptyNote(v))
    }

    @Test
    fun `能力位判不支持后念那句实话`() {
        val v = initial().copy(isLoading = false, canLoad = false, loaded = true)
        assertEquals("该源未提供评论加载功能", desktopCommentsStatusNote(v))
    }

    @Test
    fun `摊合第 1 页是替换而续页是追加`() {
        val first = listOf(c("a"), c("b"))
        val v1 = desktopCommentsReceived(initial(), first, 1, null)
        assertEquals(first, v1.items)
        assertTrue(v1.loaded)
        assertFalse(v1.isLoading)
        val second = listOf(c("c"))
        val v2 = desktopCommentsReceived(v1, second, 2, null)
        assertEquals(listOf(c("a"), c("b"), c("c")), v2.items)
    }

    @Test
    fun `摊合 hasMore 三档`() {
        // 1) 第 1 页非空而 maxPage 未知 ⇒ hasMore=true（对岸: requestedPage=1 < Int.MAX_VALUE）
        val v1 = desktopCommentsReceived(initial(), listOf(c("a")), 1, null)
        assertTrue(v1.hasMore)
        // 2) 空 ⇒ hasMore=false（对岸: comments.isNotEmpty() 那一半先塌）
        val v2 = desktopCommentsReceived(initial(), emptyList(), 1, null)
        assertFalse(v2.hasMore)
        // 3) maxPage=3 而当前是第 3 页 ⇒ hasMore=false（对岸: 3 < 3 = false）
        val v3 = desktopCommentsReceived(initial().copy(page = 2), listOf(c("b")), 3, 3)
        assertFalse(v3.hasMore)
        // 4) maxPage=3 而当前是第 2 页 ⇒ hasMore=true
        val v4 = desktopCommentsReceived(initial().copy(page = 1), listOf(c("b")), 2, 3)
        assertTrue(v4.hasMore)
    }

    @Test
    fun `maxPage 续页时源没再报就沿用上一次的`() {
        // 对岸 received: `maxPage = maximumPage ?: if (requestedPage == 1) null else maxPage`
        val v1 = desktopCommentsReceived(initial(), listOf(c("a")), 1, 5)
        assertEquals(5, v1.maxPage)
        val v2 = desktopCommentsReceived(v1, listOf(c("b")), 2, null)
        assertEquals(5, v2.maxPage)
        // 第 1 页时源没报 ⇒ 起手 null（对岸同一支: `if (requestedPage == 1) null`）
        val v3 = desktopCommentsReceived(v2, listOf(c("x")), 1, null)
        assertNull(v3.maxPage)
    }

    @Test
    fun `发车只动 loading 而不动 items 与 page`() {
        val v0 = desktopCommentsReceived(initial(), listOf(c("a")), 1, 3)
        val loading = desktopCommentsLoading(v0)
        assertTrue(loading.isLoading)
        assertNull(loading.error)
        // 上一屏读到的 items / page / maxPage / hasMore / loaded 一格都不许闪没
        assertEquals(v0.items, loading.items)
        assertEquals(v0.page, loading.page)
        assertEquals(v0.maxPage, loading.maxPage)
        assertEquals(v0.hasMore, loading.hasMore)
        assertEquals(v0.loaded, loading.loaded)
    }

    @Test
    fun `那一趟塌了只停手加成因`() {
        val v0 = desktopCommentsReceived(initial(), listOf(c("a")), 1, null)
        val failed = desktopCommentsFailed(v0, "站方回的是过盾页")
        assertFalse(failed.isLoading)
        assertEquals("站方回的是过盾页", failed.error)
        assertEquals(v0.items, failed.items)
        // 状态句把成因原样念出来（对岸 CommentLoadStatus 里 thread.error 那一支就是原话 + 再试一次）
        assertEquals("站方回的是过盾页", desktopCommentsStatusNote(failed))
    }

    @Test
    fun `读了确实没评论才念那一句`() {
        val v = initial().copy(isLoading = false, canLoad = true, loaded = true)
        assertNull(desktopCommentsStatusNote(v)) // 无状态句要念
        assertEquals("这一本还没有评论", desktopCommentsEmptyNote(v))
    }

    @Test
    fun `详情把手文案两档`() {
        // 对岸 :681 `if (commentCount > 0) "评论 $commentCount" else "评论"`
        // 桌面把「null（源没报）」与「报了 0」都归入"评论"那一档：拿 0 冒充"没有评论"是假账，
        // 而源没报数**不许**桌面猜一个（§121 那条口径）。
        assertEquals("评论", desktopCommentsHandleLabel(null))
        assertEquals("评论", desktopCommentsHandleLabel(0))
        assertEquals("评论 7", desktopCommentsHandleLabel(7))
    }

    @Test
    fun `取证读数行分得开起手 未提供 塌了 三档`() {
        val start = desktopCommentsScreenReading("c1", initial())
        assertTrue("起手该念加载中", start.contains("加载=true"))
        assertTrue("起手条=0", start.contains("条=0"))
        val unread = desktopCommentsScreenReading("c1", initial().copy(isLoading = false, canLoad = false))
        assertTrue("未提供念能读=false", unread.contains("能读=false"))
        val failed = desktopCommentsScreenReading("c1", initial().copy(error = "站方挂了"))
        assertTrue("塌了带因", failed.contains("因=站方挂了"))
    }
}
