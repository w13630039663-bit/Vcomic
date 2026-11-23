package com.venera.compose.feature

import com.venera.compose.source.model.Comment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 评论首屏必须「最新在前」。
 *
 * 各源的评论分页本身不保证时间序（禁漫按论坛楼层、哔咔按创建序，置顶/旧帖会混在首页），
 * 详情页就只能自己按时间排。判据钉三件事：时间倒序、写法不同也要认、没法排序的条目别丢。
 */
class DesktopCommentOrderTest {

    private fun c(id: String, time: String?): Comment =
        Comment(id = id, userName = "u$id", content = "c$id", time = time)

    @Test
    fun `按时间倒序重排`() {
        val ordered = commentsNewestFirst(
            listOf(
                c("old", "2024-01-02 03:04:05"),
                c("new", "2026-11-16 19:33:53"),
                c("mid", "2025-06-01 08:00:00"),
            ),
        )
        assertEquals(listOf("new", "mid", "old"), ordered.map { it.id })
    }

    @Test
    fun `ISO 带毫秒与空格分隔能混着比`() {
        val ordered = commentsNewestFirst(
            listOf(
                c("ms", "2026-11-16T19:33:53.699Z"),
                c("plain", "2026-11-16 19:33:54"),
            ),
        )
        assertEquals(listOf("plain", "ms"), ordered.map { it.id })
    }

    @Test
    fun `没有时间的条目保持原序并排在后面`() {
        val ordered = commentsNewestFirst(
            listOf(
                c("a", null),
                c("b", "2020-01-01 00:00:00"),
                c("d", null),
                c("e", "2021-01-01 00:00:00"),
            ),
        )
        assertEquals(listOf("e", "b", "a", "d"), ordered.map { it.id })
    }

    @Test
    fun `一条或全无时间时不动原序`() {
        val one = listOf(c("only", "2026-01-01 00:00:00"))
        assertEquals(listOf("only"), commentsNewestFirst(one).map { it.id })
        val unknown = listOf(c("x", null), c("y", "刚刚"))
        assertEquals(listOf("x", "y"), commentsNewestFirst(unknown).map { it.id })
    }

    @Test
    fun `时间键只认得出数字串`() {
        assertNull(commentTimeKey(null))
        assertNull(commentTimeKey("刚刚"))
        assertNull(commentTimeKey("2026-11"))
        assertEquals(20261116193353L, commentTimeKey("2026-11-16T19:33:53.699Z"))
        assertEquals(20261116000000L, commentTimeKey("2026-11-16"))
    }
}
