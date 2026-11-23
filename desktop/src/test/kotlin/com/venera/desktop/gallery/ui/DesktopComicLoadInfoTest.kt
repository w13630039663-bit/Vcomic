package com.venera.desktop.gallery.ui

import com.venera.engineprobe.parseComicLoadInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批 80 · `loadInfo` 那一坨答复在桌面这一侧怎么读成「标题 + 简介 + 章节表」。
 *
 * 判据住在桌面测试里而实现住在 `engine-probe`：那一颗模块是桌面与探针共用的取数层，
 * 而**解析语义的权威是 Android 侧 `JsComicSource.getComicDetails`**（同一份 JS 答复，两处读成两种形状
 * 就是"桌面看到的章节比手机少"那一族缺陷的成因），所以这里逐条回核它，而不是自造一套。
 */
class DesktopComicLoadInfoTest {

    @Test
    fun `chapters 是 Map 时 key 是话号而 value 是话名`() {
        // ⚠️ 这一条从前被写过一次反的（`EngineSession.details` 的注释：「我第一版写反了，
        // loadEp 当场报 Invalid Data」）。反了的样子在屏上完全正常 —— 列表照摆，只有点开是错的，
        // 所以这一格必须由键值对逐字钉住，不许靠"看起来对"。
        val info = parseComicLoadInfo(
            "9",
            mapOf(
                "success" to true,
                "data" to mapOf(
                    "title" to "某本漫画",
                    "description" to "一句话简介",
                    "chapters" to mapOf("1" to "第 1 话", "2" to "第 2 话"),
                ),
            ),
        )
        assertEquals("某本漫画", info.title)
        assertEquals("一句话简介", info.description)
        assertEquals(listOf("第 1 话" to "1", "第 2 话" to "2"), info.episodes.map { it.name to it.id })
        assertTrue("话表非空时不许掺占位章", info.episodes.none { it.placeholder })
    }

    @Test
    fun `chapters 是分组表时按外层分组次序摊平`() {
        val info = parseComicLoadInfo(
            "9",
            mapOf(
                "success" to true,
                "data" to mapOf(
                    "title" to "T",
                    "chapters" to mapOf(
                        "第一卷" to mapOf("a" to "1 话", "b" to "2 话"),
                        "第二卷" to mapOf("c" to "3 话"),
                    ),
                ),
            ),
        )
        assertEquals(listOf("1 话", "2 话", "3 话"), info.episodes.map { it.name })
        assertEquals(listOf("a", "b", "c"), info.episodes.map { it.id })
    }

    @Test
    fun `chapters 是 List 时取 title 与 id 而缺项各自有兜底`() {
        val info = parseComicLoadInfo(
            "9",
            mapOf(
                "success" to true,
                "data" to mapOf(
                    "title" to "T",
                    "episodes" to listOf(
                        mapOf("title" to "有名字", "id" to "x"),
                        mapOf("id" to "y"),
                        mapOf("title" to "没给号"),
                    ),
                ),
            ),
        )
        assertEquals(listOf("有名字" to "x", "第 1 话" to "y", "没给号" to "2"), info.episodes.map { it.name to it.id })
    }

    @Test
    fun `完全没有章节表时补一章占位而话号沿用漫画号`() {
        val info = parseComicLoadInfo("9", mapOf("success" to true, "data" to mapOf("title" to "单本")))
        assertEquals(1, info.episodes.size)
        assertEquals("9", info.episodes.single().id)
        assertEquals("", info.episodes.single().name)
        // 占位那一档不是装饰：`pages` 拿它决定第三个实参交 `null` 还是交货号，漏了就取不到页。
        assertTrue(info.episodes.single().placeholder)
    }

    @Test
    fun `缺 description 与 null description 都读成空串而不是没有这一栏`() {
        val absent = parseComicLoadInfo("9", mapOf("success" to true, "data" to mapOf("title" to "T")))
        val nulled = parseComicLoadInfo(
            "9",
            mapOf("success" to true, "data" to mapOf("title" to "T", "description" to null)),
        )
        assertEquals("", absent.description)
        assertEquals("", nulled.description)
    }

    @Test
    fun `答复里的 cover 读成详情封面而缺栏与 null 都是空串`() {
        // 批 82.6：这一栏从前没读，于是桌面写进 `comic_history` 的封面恒空 —— 而那一格是两端共用的。
        val given = parseComicLoadInfo(
            "9",
            mapOf("success" to true, "data" to mapOf("title" to "T", "cover" to "https://cdn.example/c.jpg")),
        )
        assertEquals("https://cdn.example/c.jpg", given.cover)
        val absent = parseComicLoadInfo("9", mapOf("success" to true, "data" to mapOf("title" to "T")))
        val nulled = parseComicLoadInfo(
            "9",
            mapOf("success" to true, "data" to mapOf("title" to "T", "cover" to null)),
        )
        assertEquals("缺这一栏读成空串而不是「没有这一格」", "", absent.cover)
        assertEquals("", nulled.cover)
    }


    @Test
    fun `站方明说失败时抛出去而不是交回一张空详情`() {
        // 这条与「降级不许静默」同族：`success=false` 读成"这本没有章节"，
        // 用户看到的是一句谎话（"源没给目录"）而不是一句故障。
        val failure = runCatching {
            parseComicLoadInfo("9", mapOf("success" to false, "error" to "Invalid Authorization"))
        }.exceptionOrNull()
        assertTrue("站方回 success=false 时必须抛，不能交回空详情", failure != null)
        assertTrue(
            "抛出去的那句要带上站方的原话，否则排障时只剩一个类型名：${failure?.message}",
            failure?.message?.contains("Invalid Authorization") == true,
        )
    }

    @Test
    fun `答复里没有 data 时那句点名是信封缺货而不是这本漫画没内容`() {
        val failure = runCatching { parseComicLoadInfo("9", mapOf("success" to true)) }.exceptionOrNull()
        assertTrue("没有 data 必须抛", failure != null)
        assertTrue(
            "那一句要念得出缺的是哪一层：${failure?.message}",
            failure?.message?.contains("data") == true,
        )
    }

    // ───────────── ：信息区那几栏（对齐安卓详情） ─────────────

    @Test
    fun `author 缺栏时落 uploader 而 uploader 再缺才是空串`() {
        // 对岸 `:466` 那一序：`author ?: uploader ?: ""`。
        val both = parseComicLoadInfo(
            "9",
            mapOf("success" to true, "data" to mapOf("title" to "T", "author" to "A", "uploader" to "U")),
        )
        assertEquals("A", both.author)
        assertEquals("U", both.uploader)
        val onlyUploader = parseComicLoadInfo(
            "9",
            mapOf("success" to true, "data" to mapOf("title" to "T", "uploader" to "U")),
        )
        assertEquals("author 缺栏时由 uploader 顶位（对岸同一序）", "U", onlyUploader.author)
        val none = parseComicLoadInfo("9", mapOf("success" to true, "data" to mapOf("title" to "T")))
        assertEquals("", none.author)
        // uploader 缺栏时作者顶位（对岸 `:586`：`uploader ?: author`）。
        val onlyAuthor = parseComicLoadInfo(
            "9",
            mapOf("success" to true, "data" to mapOf("title" to "T", "author" to "A")),
        )
        assertEquals("uploader 缺栏时由 author 顶位（对岸同一序）", "A", onlyAuthor.uploader)
        assertEquals("", none.uploader)
    }

    @Test
    fun `tags 两种形状都摊平而分组名不进表`() {
        val listForm = parseComicLoadInfo(
            "9",
            mapOf("success" to true, "data" to mapOf("title" to "T", "tags" to listOf("热血", "战斗"))),
        )
        assertEquals(listOf("热血", "战斗"), listForm.tags)
        val mapForm = parseComicLoadInfo(
            "9",
            mapOf(
                "success" to true,
                "data" to mapOf(
                    "title" to "T",
                    "tags" to mapOf("题材" to listOf("热血"), "作者" to listOf("尾田")),
                ),
            ),
        )
        assertEquals("Map 形摊平各组的值，组名本身不进表（对岸 :476-486 同一形）", listOf("热血", "尾田"), mapForm.tags)
        val junk = parseComicLoadInfo(
            "9",
            mapOf("success" to true, "data" to mapOf("title" to "T", "tags" to "热血")),
        )
        assertEquals("既非 List 也非 Map = 空表（不猜）", emptyList<String>(), junk.tags)
    }

    @Test
    fun `stars 与 likesCount 没报时是可空而不是冒充零`() {
        val none = parseComicLoadInfo("9", mapOf("success" to true, "data" to mapOf("title" to "T")))
        assertEquals("源没报评分 ⇒ null（屏上那一格不摆；0 分与没报是两句话）", null, none.stars)
        assertEquals(null, none.likesCount)
        val given = parseComicLoadInfo(
            "9",
            mapOf("success" to true, "data" to mapOf("title" to "T", "stars" to 4.5, "likesCount" to 1234)),
        )
        assertEquals(4.5f, given.stars)
        assertEquals(1234, given.likesCount)
    }

    @Test
    fun `recommend 只收带 id 的那几笔而题名封面缺栏成空串`() {
        val info = parseComicLoadInfo(
            "9",
            mapOf(
                "success" to true,
                "data" to mapOf(
                    "title" to "T",
                    "recommend" to listOf(
                        mapOf("id" to "r1", "title" to "推荐一本", "cover" to "https://x/r1.jpg"),
                        mapOf("title" to "没有 id 不收"),
                        mapOf("id" to "r3"),
                    ),
                ),
            ),
        )
        assertEquals(2, info.recommend.size)
        assertEquals("r1", info.recommend[0].id)
        assertEquals("推荐一本", info.recommend[0].title)
        assertEquals("缺栏成空串而不是 null（列表渲染不猜）", "", info.recommend[1].title)
    }

}
