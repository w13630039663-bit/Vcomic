package com.venera.desktop.db

import com.venera.compose.data.db.CoreDbSchema
import com.venera.compose.data.db.DatabasePorts
import com.venera.compose.data.db.LocalFavoriteDbSchema
import com.venera.compose.data.db.LocalFavoriteDatabase
import com.venera.compose.data.platform.quoteIdentifier
import com.venera.desktop.platform.migrateLegacyFavorites
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 钉住旧收藏迁移的**空壳库判据**。
 *
 * 桌面版先跑一次会在旧库位置自建出空壳「默认」表（[LocalFavoriteDbSchema] 的 create）。
 * 若迁移只数表名不看行数，就会以为"旧库有收藏"、把标记落死；之后用户把真旧库拷进来，
 * 迁移因标记存在永久跳过 —— 旧收藏全不可见且无自救提示。
 */
class DesktopLegacyFavoriteMigrationTest {

    private val flagName = "legacy_local_favorites_v1"
    private val opened = mutableListOf<TestDatabase>()

    private fun newDb(hint: String): TestDatabase =
        TestDatabase(hint).also { opened += it }

    @After
    fun tearDown() {
        opened.forEach { it.close() }
    }

    private fun markCount(core: TestDatabase): Int =
        core.db.query("SELECT name FROM migration_flags WHERE name = ?", flagName).size

    /** 空壳「默认」表（零行）不许算数：不搬、也**不落**标记。 */
    @Test
    fun 空壳默认表不落迁移标记() {
        val core = newDb("c1-core")
        val legacy = newDb("c1-legacy")
        CoreDbSchema.ensureOn(core.db)
        // 与桌面版先跑一次留下的形状一致：folder 元表 + 空「默认」表。
        LocalFavoriteDbSchema.ensureOn(legacy.db)
        val ports = DatabasePorts(core.source, legacy.source, FakeFavoritesPreferences())

        val moved = migrateLegacyFavorites(ports)

        assertEquals("空壳库没有可搬的：", 0, moved)
        assertEquals("空壳表不算数，标记不许落（否则真旧库拷进来永久跳过）：", 0, markCount(core))
    }

    /** 新库里已经有用户新加的收藏，之后才拷入真旧库：旧收藏照样要搬进来。 */
    @Test
    fun 新库已有收藏时旧库那批也要搬() {
        val core = newDb("c1-core3")
        val legacy = newDb("c1-legacy3")
        CoreDbSchema.ensureOn(core.db)
        LocalFavoriteDbSchema.ensureOn(legacy.db)
        core.db.exec(
            "INSERT INTO comic_favorite(comic_id, title, cover_url, source_name, folder_name, created_at) " +
                "VALUES (?, ?, ?, ?, '默认', ?)",
            "new-comic", "新版里加的那本", "", "jm", System.currentTimeMillis(),
        )
        legacy.db.exec(
            "INSERT INTO ${quoteIdentifier(LocalFavoriteDatabase.DEFAULT_FOLDER)}" +
                "(id, name, author, type, source_key, tags, cover_path, time, display_order) " +
                "VALUES (?, ?, ?, 0, ?, ?, ?, ?, 0)",
            "old-comic", "旧库那本", "作者", "jm", "tag", "", "2026-01-01 10:00:00",
        )
        val ports = DatabasePorts(core.source, legacy.source, FakeFavoritesPreferences())

        val moved = migrateLegacyFavorites(ports)

        assertEquals("旧库那条必须搬：", 1, moved)
        val ids = core.db.query("SELECT comic_id FROM comic_favorite").mapNotNull { it.string("comic_id") }.sorted()
        assertEquals("两本都在（新版那条不许被覆盖）：", listOf("new-comic", "old-comic"), ids)
        assertEquals("搬过就该落标记：", 1, markCount(core))
    }

    /** 表里有行却没有 id 列：那几行不算数，旁边可读表的收藏照样搬、标记照落。 */
    @Test
    fun 缺id列的杂表不拖累可读表() {
        val core = newDb("c1-core4")
        val legacy = newDb("c1-legacy4")
        CoreDbSchema.ensureOn(core.db)
        LocalFavoriteDbSchema.ensureOn(legacy.db)
        legacy.db.exec("CREATE TABLE 杂表(whatever TEXT)")
        legacy.db.exec("INSERT INTO 杂表(whatever) VALUES (?)", "随便")
        legacy.db.exec(
            "INSERT INTO ${quoteIdentifier(LocalFavoriteDatabase.DEFAULT_FOLDER)}" +
                "(id, name, author, type, source_key, tags, cover_path, time, display_order) " +
                "VALUES (?, ?, ?, 0, ?, ?, ?, ?, 0)",
            "old-comic", "旧库那本", "作者", "jm", "tag", "", "2026-01-01 10:00:00",
        )
        val ports = DatabasePorts(core.source, legacy.source, FakeFavoritesPreferences())

        val moved = migrateLegacyFavorites(ports)

        assertEquals("可读那表的一条要搬：", 1, moved)
        assertEquals("搬完该落标记：", 1, markCount(core))
    }

    /** 真有数据的旧库照常搬、照常落标记（防止把判据收得过头）。 */
    @Test
    fun 有数据的旧库照搬并落标记() {
        val core = newDb("c1-core2")
        val legacy = newDb("c1-legacy2")
        CoreDbSchema.ensureOn(core.db)
        LocalFavoriteDbSchema.ensureOn(legacy.db)
        legacy.db.exec(
            "INSERT INTO ${quoteIdentifier(LocalFavoriteDatabase.DEFAULT_FOLDER)}" +
                "(id, name, author, type, source_key, tags, cover_path, time, display_order) " +
                "VALUES (?, ?, ?, 0, ?, ?, ?, ?, 0)",
            "comic-1", "收在默认的那本", "作者", "jm", "tag", "", "2026-01-01 10:00:00",
        )
        val ports = DatabasePorts(core.source, legacy.source, FakeFavoritesPreferences())

        val moved = migrateLegacyFavorites(ports)

        assertEquals("那一条必须搬过去：", 1, moved)
        assertEquals("搬过就该落标记：", 1, markCount(core))
        val names = core.db.query("SELECT comic_id FROM comic_favorite").map { it.string("comic_id") }
        assertTrue("搬过去的是那一条：", names == listOf("comic-1"))
    }
}
