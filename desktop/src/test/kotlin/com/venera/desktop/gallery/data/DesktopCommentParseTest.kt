package com.venera.desktop.gallery.data

import com.venera.engineprobe.parseCommentPage
import com.venera.engineprobe.parseComicLoadInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 评论那一坨答复在桌面这一侧怎么读成「列表 + maxPage」，以及 `loadInfo` 新读回的
 * `subId` / `commentCount` 两栏。
 *
 * ## 为什么判据住在桌面测试而实现住在 `engine-probe`
 *
 * 那一颗模块是桌面与探针共用的取数层，而**解析语义的权威是 Android 侧 `JsComicSource.loadCommentsPage`
 * （`:1444-1459`）与 `getComicDetails`（`:595-597`）**—— 同一份 JS 答复在两处读成两种形状，就是
 * 「桌面看到的评论比手机少 / 字段错位 / 拿 0 冒充没评论」那一族缺陷的成因。所以这里**逐条回核对岸键名**，
 * 而不是自造一套（与 `DesktopComicLoadInfoTest` 那颗同族做法）。
 */
class DesktopCommentParseTest {

    private fun env(data: Any?, success: Boolean = true): Map<String, Any?> =
        mapOf("success" to success, "data" to data)

    @Test
    fun `data 是 List 时那就是评论数组本身`() {
        val page = parseCommentPage(
            env(
                listOf(
                    mapOf("id" to "1", "userName" to "甲", "content" to "第一条"),
                    mapOf("id" to "2", "userName" to "乙", "content" to "第二条"),
                ),
            ),
        )
        assertEquals(2, page.comments.size)
        assertEquals("甲", page.comments[0].userName)
        assertEquals("第一条", page.comments[0].content)
        assertNull(page.maxPage)
    }

    @Test
    fun `data 是 Map 时认 comments 键`() {
        val page = parseCommentPage(
            env(
                mapOf(
                    "comments" to listOf(mapOf("id" to "1", "userName" to "甲", "content" to "第一条")),
                    "maxPage" to 5,
                ),
            ),
        )
        assertEquals(1, page.comments.size)
        assertEquals(5, page.maxPage)
    }

    @Test
    fun `data 是 Map 而没有 comments 时退 list 键`() {
        // 对岸 :1454 `(data["comments"] ?: data["list"])` —— 两键顺序不许反（反了带 comments 的源会被读空）。
        val page = parseCommentPage(
            env(mapOf("list" to listOf(mapOf("id" to "1", "userName" to "甲", "content" to "第一条")))),
        )
        assertEquals(1, page.comments.size)
    }

    @Test
    fun `comments 与 list 都没有时抛而不静默当空`() {
        val threw = runCatching { parseCommentPage(env(mapOf("other" to 1))) }.isFailure
        assertTrue("缺一族列表键必须抛（半截答复不能当'没有评论'静默过去）", threw)
    }

    @Test
    fun `success=false 直接抛`() {
        val threw = runCatching {
            parseCommentPage(env(emptyList<Any>(), success = false))
        }.isFailure
        assertTrue("源站报错信封不能当空评论列表（对岸 :1450 data 解不开那一路）", threw)
    }

    @Test
    fun `缺正文的那条不算一条评论`() {
        // 对岸 SourcePayloadParser.comment 以 `map["content"]?.toString()?.let{…}` 为存在判据：
        // 没有 content（或为空取到 null）就整条 mapNotNull 掉。桌面不许改成"没正文也摆一枚空卡"。
        val page = parseCommentPage(
            env(
                listOf(
                    mapOf("id" to "1", "userName" to "甲", "content" to "有货"),
                    mapOf("id" to "2", "userName" to "乙"), // 没 content
                ),
            ),
        )
        assertEquals(1, page.comments.size)
        assertEquals("甲", page.comments[0].userName)
    }

    @Test
    fun `maxPage 取不出或为负一律 null`() {
        // 对岸 :1458 `?.toString()?.toDoubleOrNull()?.toInt()?.takeIf { it >= 0 }`
        val str = parseCommentPage(env(mapOf("comments" to emptyList<Any>(), "maxPage" to "3")))
        assertEquals(3, str.maxPage)
        val neg = parseCommentPage(env(mapOf("comments" to emptyList<Any>(), "maxPage" to -1)))
        assertNull(neg.maxPage)
        val junk = parseCommentPage(env(mapOf("comments" to emptyList<Any>(), "maxPage" to "abc")))
        assertNull(junk.maxPage)
    }

    @Test
    fun `loadInfo 读回 subId 而缺栏交回 null`() {
        // 对岸 getComicDetails :597 `subId = res["subId"]?.toString()` —— 缺栏是 null 而不是猜空串。
        val with = parseComicLoadInfo(
            "9",
            env(mapOf("title" to "T", "chapters" to mapOf("1" to "第一话"), "subId" to "SUB-1")),
        )
        assertEquals("SUB-1", with.subId)
        val without = parseComicLoadInfo(
            "9",
            env(mapOf("title" to "T", "chapters" to mapOf("1" to "第一话"))),
        )
        assertNull("源没报 subId 时不许猜一串（评论那一跳会自报读不到）", without.subId)
    }

    @Test
    fun `loadInfo 读回 commentCount 而缺栏交回 null 不冒充 0`() {
        // 对岸 :595 `commentCount = (res["commentCount"] as? Number)?.toInt()`。缺栏必须是 null（屏上不摆数），
        // 而不是 0 —— 拿 0 冒充"这本没评论"是假账（§121 那条口径）。
        val with = parseComicLoadInfo(
            "9",
            env(mapOf("title" to "T", "chapters" to mapOf("1" to "第一话"), "commentCount" to 7)),
        )
        assertEquals(7, with.commentCount)
        val strCount = parseComicLoadInfo(
            "9",
            env(mapOf("title" to "T", "chapters" to mapOf("1" to "第一话"), "commentCount" to "12")),
        )
        assertEquals("数字串也认（对岸那栏有时是字符串）", 12, strCount.commentCount)
        val without = parseComicLoadInfo(
            "9",
            env(mapOf("title" to "T", "chapters" to mapOf("1" to "第一话"))),
        )
        assertNull("源没报评论数时交 null，屏上那枚把手不摆数", without.commentCount)
    }
}
