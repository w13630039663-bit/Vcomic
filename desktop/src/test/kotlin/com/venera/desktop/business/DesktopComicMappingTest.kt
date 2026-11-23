package com.venera.desktop.business

import com.venera.compose.source.model.ChapterPages
import com.venera.engineprobe.ComicInfo
import com.venera.engineprobe.ComicRecommend
import com.venera.engineprobe.DesktopComment
import com.venera.engineprobe.DesktopCommentCapabilities
import com.venera.engineprobe.DesktopCommentPage
import com.venera.engineprobe.DesktopWriteCapabilities
import com.venera.engineprobe.EngineSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D′-1 换算层的字段映射判据：假 `ComicInfo` / `DesktopCommentPage` 直接喂进来，逐条核对形状，
 * **不碰真引擎、不跑 GraalJS**（解析信封的判据留在引擎那侧 `ComicLoadInfoParseTest`）。
 */
class DesktopComicMappingTest {

    private fun info(episodes: List<EngineSession.Episode>, tags: List<String> = emptyList()) = ComicInfo(
        title = "标题",
        description = "简介",
        episodes = episodes,
        cover = "cover.jpg",
        tags = tags,
    )

    @Test
    fun `章节表摊平成 ComicChapter 而 order 是摊平序`() {
        val d = info(
            listOf(
                EngineSession.Episode("第一话", "e1"),
                EngineSession.Episode("第二话", "e2"),
            ),
        ).toComicDetails(comicId = "c1", sourceKey = "jm")
        assertEquals(listOf("e1", "e2"), d.chapters.map { it.id })
        assertEquals(listOf("第一话", "第二话"), d.chapters.map { it.title })
        assertEquals(listOf(0, 1), d.chapters.map { it.order })
        assertEquals("jm", d.sourceKey)
        assertEquals("c1", d.comic.id)
    }

    @Test
    fun `占位章话号沿用漫画号且不改写`() {
        // parseComicLoadInfo 交回 Episode("", comicId, placeholder=true)：映射层原样保留那一格 id。
        val d = info(listOf(EngineSession.Episode("", "c1", placeholder = true)))
            .toComicDetails(comicId = "c1", sourceKey = "jm")
        assertEquals(1, d.chapters.size)
        assertEquals("c1", d.chapters.single().id)
    }

    @Test
    fun `评分与点赞数没报时可空而不冒充零`() {
        val d = info(emptyList()).toComicDetails(comicId = "c1", sourceKey = "jm")
        assertNull(d.comic.rating)
        assertNull(d.comic.likesCount)
        // ComicDetails 那两栏是非空基本型：没报落到 0f / 0（对岸 stars = rating 同形）。
        assertEquals(0.0, d.rating.toDouble(), 0.0)
        assertEquals(0, d.likesCount)
        assertEquals(0, d.commentCount)
    }

    @Test
    fun `评分报了则进可空栏且非空栏同值`() {
        val d = info(emptyList()).copy(stars = 4.5f, likesCount = 30, commentCount = 7)
            .toComicDetails(comicId = "c1", sourceKey = "jm")
        assertEquals(4.5, d.comic.rating!!.toDouble(), 0.0)
        assertEquals(30, d.comic.likesCount)
        assertEquals(4.5, d.rating.toDouble(), 0.0)
        assertEquals(30, d.likesCount)
        assertEquals(7, d.commentCount)
    }

    @Test
    fun `分组标签进 tagMap 而扁平 tags 进 comic_tags`() {
        val d = info(emptyList()).copy(
            tags = listOf("a", "b"),
            tagGroups = listOf("原作" to listOf("甲"), "出演" to listOf("乙", "丙")),
        ).toComicDetails(comicId = "c1", sourceKey = "jm")
        assertEquals(listOf("a", "b"), d.comic.tags)
        assertEquals(mapOf("原作" to listOf("甲"), "出演" to listOf("乙", "丙")), d.tagMap)
        // plainTags 从 tagMap 重建 namespace:tag（对岸打平口径）。
        assertTrue(d.plainTags.contains("原作:甲"))
        assertTrue(d.plainTags.contains("出演:丙"))
    }

    @Test
    fun `List 形状无组时 tagMap 空`() {
        val d = info(emptyList()).copy(tags = listOf("x")).toComicDetails("c1", "jm")
        assertTrue(d.tagMap.isEmpty())
        assertEquals(listOf("x"), d.comic.tags)
    }

    @Test
    fun `sourceFavorite 改名进 isFavorite`() {
        assertTrue(info(emptyList()).copy(sourceFavorite = true).toComicDetails("c1", "jm").comic.isFavorite)
        assertFalse(info(emptyList()).toComicDetails("c1", "jm").comic.isFavorite)
    }

    @Test
    fun `recommend 每项带上当前源键`() {
        val d = info(emptyList()).copy(recommend = listOf(ComicRecommend("r1", "推荐一", "rc.jpg")))
            .toComicDetails("c1", "jm")
        assertEquals("r1", d.recommend.single().id)
        assertEquals("jm", d.recommend.single().sourceKey)
    }

    @Test
    fun `website 进 url 且 subId 原样`() {
        val d = info(emptyList()).copy(website = "https://x", subId = "s9").toComicDetails("c1", "jm")
        assertEquals("https://x", d.url)
        assertEquals("s9", d.subId)
    }

    @Test
    fun `页表标 useOnImageLoad 且 headers 空`() {
        val pages = engineChapterPages("c1", "e1", "第一话", listOf("k0", "k1"))
        assertEquals(ChapterPages(comicId = "c1", chapterId = "e1", chapterTitle = "第一话", pages = listOf("k0", "k1"), headers = emptyMap(), useOnImageLoad = true), pages)
    }

    @Test
    fun `评论实体逐字段搬运`() {
        val page = DesktopCommentPage(
            comments = listOf(
                DesktopComment(id = "m1", userName = "某", avatar = "a.png", content = "正文", time = "12:00", replyCount = 2, score = 3, voteStatus = 1, isLiked = true),
            ),
            maxPage = 4,
        ).toSharedCommentPage()
        val c = page.comments.single()
        assertEquals("m1", c.id)
        assertEquals("某", c.userName)
        assertEquals("正文", c.content)
        assertEquals(2, c.replyCount)
        assertEquals(3, c.score)
        assertEquals(1, c.voteStatus)
        assertTrue(c.isLiked)
        assertEquals(4, page.maxPage)
    }

    @Test
    fun `评论能力位三档`() {
        val caps = DesktopCommentCapabilities(canLoad = true, canSend = false).toSharedCapabilities()
        assertTrue(caps.canLoad)
        assertFalse(caps.canSend)
    }

    @Test
    fun `写能力位映射两位不混`() {
        val caps = DesktopWriteCapabilities(canRate = true, canLike = false).toSharedWriteCapabilities()
        assertTrue(caps.canRate)
        assertFalse(caps.canLike)
    }

    @Test
    fun `账号三档靠 cookieFields 与 isLogged`() {
        assertTrue(desktopAccountInfo(listOf("ipb_member_id"), false).hasAccount)
        assertTrue(desktopAccountInfo(listOf("k"), true).isLogged)
        assertFalse(desktopAccountInfo(emptyList(), true).hasAccount)
        // 桌面只走手填 cookie 那一路：supportsCookieLogin 真、密码/网页登录未接 = false。
        assertTrue(desktopAccountInfo(listOf("k"), false).supportsCookieLogin)
        assertFalse(desktopAccountInfo(listOf("k"), false).supportsPasswordLogin)
        assertFalse(desktopAccountInfo(listOf("k"), false).supportsWebLogin)
    }
}
