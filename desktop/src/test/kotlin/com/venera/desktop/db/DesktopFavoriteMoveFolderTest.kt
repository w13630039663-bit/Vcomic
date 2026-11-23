package com.venera.desktop.db

import com.venera.compose.data.db.CoreDbSchema
import com.venera.compose.data.db.FavoriteDao
import com.venera.compose.data.db.FavoriteRecord
import com.venera.desktop.gallery.data.desktopFavoriteFolderNames
import com.venera.desktop.gallery.data.desktopFavoriteRecordToWrite
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 「换收藏夹」那一下写回**真库**之后的形状。
 *
 * ## 为什么这一颗必须吃真 sqlite 而不是纯函数
 *
 * 那一句「这一本会从 默认 移到 追更」承诺的是**只换那一列**。而那颗 DAO 用的是
 * `INSERT OR REPLACE`（`FavoriteDao.kt:76`），主键 `(comic_id, source_name)` ——
 * 它是整行替换，不是 UPDATE 那一列。所以"换个夹"到底会连带改到什么，只有真跑一趟写读才量得出来；
 * 纯函数那层（`gallery/data/DesktopFavoriteFolders.kt`）只能证明"我们递了哪一条过去"。
 *
 * ① 是那一格的**反面对照**：拿详情现拼的那一份直接写回去，标签、追更标记、最新话与
 *   `created_at` 会一起没了，而那本还会跳到收藏屏顶端（那一列按 `created_at DESC` 排）。
 *   这条今天没有尺子钉着的话，哪天有人把那颗件"顺手简化"成直接递 fresh，桌面会安静地
 *   清掉手机端攒的标签 —— 而屏上看不出任何异样。
 * ② 是那一排名字的**回核**：桌面的排序与对岸 `getFolders()` 那句 SQL 得是同一口径，
 *   否则同一台机器上两端列出不同的夹。
 */
class DesktopFavoriteMoveFolderTest {
    private lateinit var store: TestDatabase
    private lateinit var dao: FavoriteDao

    @Before
    fun setUp() {
        store = TestDatabase("favmove")
        CoreDbSchema.ensureOn(store.db)
        dao = FavoriteDao(store.source)
    }

    @After
    fun tearDown() {
        store.close()
    }

    private fun record(
        comicId: String,
        folder: String,
        tags: String = "",
        hasUpdate: Boolean = false,
        latestChapter: String = "",
        createdAt: Long = 1L,
    ) = FavoriteRecord(
        comicId = comicId,
        title = "标题 $comicId",
        author = "作者",
        coverUrl = "https://cover/$comicId",
        sourceName = "jm",
        folderName = folder,
        tags = tags,
        hasUpdate = hasUpdate,
        latestChapter = latestChapter,
        createdAt = createdAt,
    )

    /** 库里那一行的六格读数（对着列名取，不靠对象缓存）。 */
    private fun rowOf(comicId: String) = store.db
        .query("SELECT * FROM comic_favorite WHERE comic_id = ?", comicId)
        .single()

    /** ① 走那颗件写回的：夹名改了，其余格子逐字不动，而表里仍只有一行。 */
    @Test
    fun 换夹只改那一列而标签追更最新话与时间戳原样留着() {
        runBlocking {
            dao.addFavorite(
                record("c1", "默认", tags = "恋爱,校园", hasUpdate = true, latestChapter = "第 8 话", createdAt = 100L),
            )
        }
        awaitUntil("缓存里有 c1") { dao.favoritesFlow.value.any { it.comicId == "c1" } }
        val existing = dao.favoritesFlow.value.first { it.comicId == "c1" }
        // 装配点那份新拼的：只有号 / 题名 / 封面 / 源对得上，其余是默认值 + 现在的时刻。
        val fresh = record("c1", "默认", createdAt = System.currentTimeMillis())

        runBlocking { dao.addFavorite(desktopFavoriteRecordToWrite(existing, fresh, "追更")) }
        awaitUntil("缓存里 c1 已在追更") {
            dao.favoritesFlow.value.firstOrNull { it.comicId == "c1" }?.folderName == "追更"
        }

        val row = rowOf("c1")
        assertEquals("夹名要改：", "追更", row.string("folder_name"))
        assertEquals("标签不许被抹：", "恋爱,校园", row.string("tags"))
        assertEquals("追更标记不许被抹：", 1L, row.long("has_update"))
        assertEquals("最新话不许被抹：", "第 8 话", row.string("latest_chapter"))
        assertEquals("时间戳不许换成现在：", 100L, row.long("created_at"))
        assertEquals("换夹是改那一行而不是多出一行：", 1, store.db.query("SELECT comic_id FROM comic_favorite").size)
    }

    /**
     * ① 的反面（对照，证明上面那五条不是白给的）：**直接**把 fresh 写回去 ⇒ 那四格一起没了。
     *
     * 这一条不判"不许这么写"，它判的是"少用那颗件会真的坏"——没有这一条，① 那五条绿可能是
     * 因为库本来就保住了旧值（那就成了恒绿的空判式，本仓那一族坑）。
     */
    @Test
    fun 对照_整条重拼会把标签追更最新话与时间戳一起打掉() {
        runBlocking {
            dao.addFavorite(
                record("c1", "默认", tags = "恋爱,校园", hasUpdate = true, latestChapter = "第 8 话", createdAt = 100L),
            )
        }
        awaitUntil("缓存里有 c1") { dao.favoritesFlow.value.any { it.comicId == "c1" } }
        val fresh = record("c1", "追更", createdAt = 9_999L)

        runBlocking { dao.addFavorite(fresh) }
        awaitUntil("缓存里 c1 已在追更") {
            dao.favoritesFlow.value.firstOrNull { it.comicId == "c1" }?.folderName == "追更"
        }

        val row = rowOf("c1")
        assertEquals("夹名确实改了（这一条讲的是「改的同时把别的格子打回默认」）：", "追更", row.string("folder_name"))
        assertEquals("标签被抹了：", "", row.string("tags"))
        assertEquals("追更标记被抹了：", 0L, row.long("has_update"))
        assertEquals("最新话被抹了：", "", row.string("latest_chapter"))
        assertEquals("时间戳被换掉了：", 9_999L, row.long("created_at"))
    }

    /** ② 那一排的名字与对岸那句 `SELECT DISTINCT … ORDER BY … ASC` 同一口径。 */
    @Test
    fun 那一排的名字与对岸那句SQL同一口径() {
        runBlocking {
            dao.addFavorite(record("a", "追更"))
            dao.addFavorite(record("b", "收藏2"))
            dao.addFavorite(record("c", "追更"))
            dao.addFavorite(record("d", "默认"))
        }
        awaitUntil("缓存里有四本") { dao.favoritesFlow.value.size == 4 }
        val counterpart = runBlocking { dao.getFolders() }
        val desktop = desktopFavoriteFolderNames(dao.favoritesFlow.value)
        assertEquals(
            "两端列出的夹必须一模一样（顺序也算，那排把手是按这顺序摆的）。对岸=$counterpart 桌面=$desktop",
            counterpart, desktop,
        )
        assertTrue("「默认」恒在首位：$desktop", desktop.first() == "默认")
        assertEquals("同夹只列一次：", desktop.size, desktop.distinct().size)
    }
}
