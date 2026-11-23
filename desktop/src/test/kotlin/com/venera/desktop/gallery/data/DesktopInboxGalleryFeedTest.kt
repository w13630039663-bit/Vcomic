package com.venera.desktop.gallery.data

import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.data.GalleryTagLexicon
import com.venera.compose.gallery.data.GalleryTagSuggestion
import com.venera.compose.gallery.domain.GalleryArtistFollow
import com.venera.compose.gallery.domain.GalleryTitleTag
import com.venera.desktop.gallery.ui.ScriptedBoards
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 图库收件箱取数层的尺：tag type 过滤、离线兜底、失败分账（code 收取）、
 * 水位判新与跨标签去重。KONACHAN_R18 不进名单 —— 那条腿是真网络腿，桩绕不过。
 */
class DesktopInboxGalleryFeedTest {

    private fun post(site: GallerySite, id: Long, name: String) = GalleryPost(
        site = site,
        id = id,
        tags = name,
        author = "uploader",
        previewUrl = "https://example.test/$site-$id.jpg",
    )

    private fun suggestion(site: GallerySite, name: String, category: Int) =
        GalleryTagSuggestion(site = site, name = name, count = 10, category = category, deprecated = false)

    /** 脚本化词典：artistNames 与 titleTags 两槽位分开钉。 */
    private class ScriptedLexicon(
        private val artistHits: Set<String> = emptySet(),
        private val artistThrows: Boolean = false,
        private val titles: Map<String, GalleryTitleTag>? = null,
    ) : GalleryTagLexicon {
        val artistAsked = mutableListOf<List<String>>()

        override suspend fun artistNames(names: List<String>): Set<String> {
            artistAsked += names
            if (artistThrows) throw IllegalStateException("词典没读出来")
            return artistHits
        }

        override suspend fun translations(names: List<String>): Map<String, String> = emptyMap()

        override suspend fun fetchTagCategories(pageUrl: String): Map<String, Int>? = null

        override suspend fun titleTags(names: List<String>): Map<String, GalleryTitleTag>? = titles
    }

    private fun boards(
        pages: Map<GallerySite, List<Result<List<GalleryPost>>>>,
        tagScripts: Map<GallerySite, (String) -> Result<List<GalleryTagSuggestion>>> = emptyMap(),
    ): ScriptedBoards = ScriptedBoards(
        pageSize = pages.keys.associateWith { 20 },
        pages = pages,
        tagScripts = tagScripts,
    )

    private fun yandereFollow(name: String = "kantoku") =
        GalleryArtistFollow(GallerySite.YANDERE, name, addedAt = 0L)

    private fun watermarks(vararg pairs: Pair<String, Long>) = pairs.toMap()

    @Test
    fun `tag全等命中画师档照常取数`() = runBlocking {
        val lexicon = ScriptedLexicon()
        val scripted = boards(
            pages = mapOf(GallerySite.YANDERE to listOf(Result.success(listOf(post(GallerySite.YANDERE, 100, "kantoku"))))),
            tagScripts = mapOf(GallerySite.YANDERE to { term -> Result.success(listOf(suggestion(GallerySite.YANDERE, term, 1))) }),
        )
        val snapshot = DesktopInboxGalleryFeed(scripted, lexicon).fetch(listOf(yandereFollow()), watermarks())
        assertEquals(1, snapshot.batches.size)
        assertEquals(0, snapshot.failures.size)
        assertEquals(listOf(100L), snapshot.batches[0].posts.map { it.id })
        // 取数参数：首屏第 1 页、limit=20
        assertEquals(1, scripted.calls.size)
        assertEquals(1, scripted.calls[0].page)
        assertEquals(20, scripted.calls[0].limit)
    }

    @Test
    fun `角色同名确认缺席且不进失败账`() = runBlocking {
        val lexicon = ScriptedLexicon()
        val scripted = boards(
            pages = mapOf(GallerySite.YANDERE to listOf(Result.success(emptyList()))),
            tagScripts = mapOf(GallerySite.YANDERE to { term -> Result.success(listOf(suggestion(GallerySite.YANDERE, term, 4))) }),
        )
        val snapshot = DesktopInboxGalleryFeed(scripted, lexicon).fetch(listOf(yandereFollow()), watermarks())
        assertEquals(0, snapshot.batches.size)
        assertEquals(0, snapshot.failures.size)
    }

    @Test
    fun `tag端点失败走离线画师档兜底命中照常取数`() = runBlocking {
        val lexicon = ScriptedLexicon(artistHits = setOf("kantoku"))
        val scripted = boards(
            pages = mapOf(GallerySite.YANDERE to listOf(Result.success(listOf(post(GallerySite.YANDERE, 101, "kantoku"))))),
            tagScripts = mapOf(GallerySite.YANDERE to { _ -> Result.failure(IOException("yande.re 返回 500")) }),
        )
        val snapshot = DesktopInboxGalleryFeed(scripted, lexicon).fetch(listOf(yandereFollow()), watermarks())
        assertEquals(1, snapshot.batches.size)
        assertEquals(0, snapshot.failures.size)
    }

    @Test
    fun `tag失败且词典空集记TAG_CHECK不静默缺席`() = runBlocking {
        val lexicon = ScriptedLexicon(artistHits = emptySet())
        val scripted = boards(
            pages = mapOf(GallerySite.YANDERE to listOf(Result.success(emptyList()))),
            tagScripts = mapOf(GallerySite.YANDERE to { _ -> Result.failure(IOException("yande.re 返回 500")) }),
        )
        val snapshot = DesktopInboxGalleryFeed(scripted, lexicon).fetch(listOf(yandereFollow()), watermarks())
        assertEquals(0, snapshot.batches.size)
        assertEquals(1, snapshot.failures.size)
        assertEquals(GalleryInboxFailureKind.TAG_CHECK, snapshot.failures[0].kind)
        assertNull(snapshot.failures[0].code)
        // 判缺席的那条路不该再去拉墙
        assertEquals(0, scripted.calls.size)
    }

    @Test
    fun `tag与离线兜底双双失败同账出声`() = runBlocking {
        val lexicon = ScriptedLexicon(artistThrows = true)
        val scripted = boards(
            pages = mapOf(GallerySite.YANDERE to listOf(Result.success(emptyList()))),
            tagScripts = mapOf(GallerySite.YANDERE to { _ -> Result.failure(IOException("yande.re 返回 500")) }),
        )
        val snapshot = DesktopInboxGalleryFeed(scripted, lexicon).fetch(listOf(yandereFollow()), watermarks())
        assertEquals(0, snapshot.batches.size)
        assertEquals(1, snapshot.failures.size)
        assertEquals(GalleryInboxFailureKind.TAG_CHECK, snapshot.failures[0].kind)
    }

    @Test
    fun `safebooru不发tag查询直接取数`() = runBlocking {
        val lexicon = ScriptedLexicon()
        val scripted = boards(
            pages = mapOf(GallerySite.SAFEBOORU to listOf(Result.success(listOf(post(GallerySite.SAFEBOORU, 77, "kantoku"))))),
        )
        val snapshot = DesktopInboxGalleryFeed(scripted, lexicon).fetch(
            listOf(GalleryArtistFollow(GallerySite.SAFEBOORU, "kantoku", 0L)),
            watermarks(),
        )
        assertEquals(1, snapshot.batches.size)
        assertEquals(0, snapshot.failures.size)
        // 该站没有脚本腿：一旦发了 tag 查询当场抛，走到这里即未发
        assertTrue(scripted.tagCalls.none { it.site == GallerySite.SAFEBOORU })
    }

    @Test
    fun `id超水位判新作且同步不推落盘水位`() = runBlocking {
        val lexicon = ScriptedLexicon()
        val scripted = boards(
            pages = mapOf(GallerySite.YANDERE to listOf(Result.success(listOf(post(GallerySite.YANDERE, 100, "kantoku"), post(GallerySite.YANDERE, 80, "kantoku"))))),
            tagScripts = mapOf(GallerySite.YANDERE to { term -> Result.success(listOf(suggestion(GallerySite.YANDERE, term, 1))) }),
        )
        val feed = DesktopInboxGalleryFeed(scripted, lexicon)
        val marks = watermarks("yandere:kantoku" to 90L)
        val snapshot = feed.fetch(listOf(yandereFollow()), marks)
        assertEquals(listOf(100L), snapshot.batches[0].newPosts.map { it.id })
        assertEquals(listOf(80L), snapshot.batches[0].oldPosts.map { it.id })
        // 再同步一次：落盘水位没动过，分侧读数不变（内存候选只在全部已读时落盘）
        val again = feed.fetch(listOf(yandereFollow()), marks)
        assertEquals(listOf(100L), again.batches[0].newPosts.map { it.id })
        assertEquals(90L, marks.getValue("yandere:kantoku"))
        // 水位推进之后（全部已读落盘），同批产物全落旧作侧
        val afterViewed = feed.fetch(listOf(yandereFollow()), watermarks("yandere:kantoku" to 100L))
        assertEquals(0, afterViewed.batches[0].newPosts.size)
        assertEquals(2, afterViewed.batches[0].oldPosts.size)
    }

    @Test
    fun `跨标签按GalleryPostUid去重`() = runBlocking {
        val lexicon = ScriptedLexicon()
        val same = listOf(post(GallerySite.YANDERE, 100, "kantoku"))
        val scripted = boards(
            pages = mapOf(
                GallerySite.YANDERE to listOf(Result.success(same)),
                GallerySite.GELBOORU to listOf(Result.success(listOf(post(GallerySite.GELBOORU, 100, "kantoku")))),
            ),
            tagScripts = mapOf(
                GallerySite.YANDERE to { term -> Result.success(listOf(suggestion(GallerySite.YANDERE, term, 1))) },
                GallerySite.GELBOORU to { term -> Result.success(listOf(suggestion(GallerySite.GELBOORU, term, 1))) },
            ),
        )
        val snapshot = DesktopInboxGalleryFeed(scripted, lexicon).fetch(
            listOf(yandereFollow("kantoku"), GalleryArtistFollow(GallerySite.GELBOORU, "kantoku", 0L)),
            watermarks(),
        )
        // 同号不同站是两份（uid 带 routeKey）；同站同号跨标签才并
        assertEquals(2, snapshot.batches.size)
    }

    @Test
    fun `同站同号两标签只收一份`() = runBlocking {
        val lexicon = ScriptedLexicon()
        val scripted = boards(
            pages = mapOf(
                GallerySite.YANDERE to listOf(Result.success(listOf(post(GallerySite.YANDERE, 100, "kantoku")))),
                GallerySite.TBIB to listOf(Result.success(emptyList())),
                GallerySite.KONACHAN to listOf(Result.success(emptyList())),
            ),
            tagScripts = mapOf(
                GallerySite.YANDERE to { term -> Result.success(listOf(suggestion(GallerySite.YANDERE, term, 1))) },
            ),
        )
        val snapshot = DesktopInboxGalleryFeed(scripted, lexicon).fetch(
            listOf(yandereFollow("kantoku"), yandereFollow("hime")),
            watermarks(),
        )
        assertEquals(1, snapshot.batches.size)
        assertEquals("kantoku", snapshot.batches[0].name)
        assertEquals(1, snapshot.batches[0].posts.size)
    }

    @Test
    fun `取数失败401从文案收code进失败账`() = runBlocking {
        val lexicon = ScriptedLexicon()
        val scripted = boards(
            pages = mapOf(GallerySite.GELBOORU to listOf(Result.failure(IOException("gelbooru 返回 401")))),
            tagScripts = mapOf(GallerySite.GELBOORU to { term -> Result.success(listOf(suggestion(GallerySite.GELBOORU, term, 1))) }),
        )
        val snapshot = DesktopInboxGalleryFeed(scripted, lexicon).fetch(
            listOf(GalleryArtistFollow(GallerySite.GELBOORU, "kantoku", 0L)),
            watermarks(),
        )
        assertEquals(0, snapshot.batches.size)
        assertEquals(1, snapshot.failures.size)
        assertEquals(GalleryInboxFailureKind.FETCH, snapshot.failures[0].kind)
        assertEquals(401, snapshot.failures[0].code)
    }

    @Test
    fun `403失败收出限流码给屏面映射图库限流`() = runBlocking {
        val lexicon = ScriptedLexicon()
        val scripted = boards(
            pages = mapOf(GallerySite.YANDERE to listOf(Result.failure(IOException("yande.re 返回 403")))),
            tagScripts = mapOf(GallerySite.YANDERE to { term -> Result.success(listOf(suggestion(GallerySite.YANDERE, term, 1))) }),
        )
        val snapshot = DesktopInboxGalleryFeed(scripted, lexicon).fetch(listOf(yandereFollow()), watermarks())
        assertEquals(GalleryInboxFailureKind.FETCH, snapshot.failures[0].kind)
        assertEquals(403, snapshot.failures[0].code)
        // 屏面映射口径：failures 含 FETCH 且 code==403 → 图库限流（只认 code，不抠文案）
        assertTrue(
            snapshot.failures.any { it.kind == GalleryInboxFailureKind.FETCH && it.code == 403 },
        )
    }
}
