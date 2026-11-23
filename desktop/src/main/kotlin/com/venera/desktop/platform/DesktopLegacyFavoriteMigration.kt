package com.venera.desktop.platform

import com.venera.compose.data.db.DatabasePorts
import com.venera.compose.data.db.requiredString
import java.time.LocalDateTime
import java.time.ZoneId

/** 迁移标记名；只认这一个键，重复启动不会二次搬运。 */
private const val MIGRATION_FLAG = "legacy_local_favorites_v1"

/** 旧库里不是收藏夹的表：元表 + SQLite 自带表。 */
private val LEGACY_META_TABLES = setOf(
    "folder_order",
    "folder_sync",
    "android_metadata",
    "sqlite_sequence",
    "migration_flags",
)

/**
 * 把旧库 `local_favorite.db`（一个收藏夹一张表）里的收藏搬进新库的 `comic_favorite`。
 *
 * 为什么要搬：收藏页只读新库，旧表里的条目不会自己出现，用户看到的就是"收藏全没了"。
 *
 * 幂等：新库里有 `migration_flags` 标记即跳过；搬运按 `(comic_id, source_name)` 去重，
 * 新库已有用户新加的收藏不会被覆盖或重复。
 *
 * 读不全按表隔离：某张表数不清或读不出就跳过并记一笔，可读的照搬，这轮**不落标记** ——
 * 下次启动再补那张坏表（主键去重，重跑不会写重）；不因一张坏表让整库旧收藏都进不来。
 *
 * @param ports 已接线的库端口；`localFavorites` 读旧库，`core` 写新库。
 * @return 实际搬过去的条数；跳过一次都没搬时返回 0。
 */
internal fun migrateLegacyFavorites(ports: DatabasePorts): Int {
    val core = ports.core.writer()
    core.exec(
        "CREATE TABLE IF NOT EXISTS migration_flags(name TEXT PRIMARY KEY, applied_at INTEGER NOT NULL)",
    )
    if (core.query("SELECT name FROM migration_flags WHERE name = ?", MIGRATION_FLAG).isNotEmpty()) return 0

    val legacy = ports.localFavorites.writer()
    val folders = legacy.query("SELECT name FROM sqlite_master WHERE type='table'")
        .map { it.requiredString("name") }
        .filter { it !in LEGACY_META_TABLES }
        // SQLite 内部表（ANALYZE 落下的 sqlite_stat1 等）有行也不代表有收藏。
        .filter { !it.startsWith("sqlite_") }
    // 表存在不等于库里有东西：桌面版先跑一次会自建出空壳「默认」表，骗过表级判据
    // 并把标记落死，之后拷入的真旧库就永远不搬了；逐表数行数才认数。
    // 任一张表读不全就记一笔 degraded：可读的照搬、这轮不落标记（下次再试，主键去重不重复）。
    var degraded = false
    val nonEmptyFolders = folders.filter { table ->
        val counted = runCatching {
            legacy.query("SELECT COUNT(*) AS c FROM ${quoteSqlIdentifier(table)}")
                .firstOrNull()?.long("c") ?: 0L
        }
        if (counted.isFailure) {
            degraded = true
            println("D_迁移 数不清 ${table} 因=${counted.exceptionOrNull()?.message?.take(120)}")
            false
        } else {
            (counted.getOrNull() ?: 0L) > 0L
        }
    }
    if (nonEmptyFolders.isEmpty()) {
        // 一行数据都数不出来：可能确实是新用户，也可能这次连接没看到表。
        // 不落标记 —— 免得一次误判就把迁移永久跳过；空库重跑只是一次廉价查询。
        return 0
    }

    // 逐表取行：读不出的那张跳过并记一笔，可读的照搬 —— 不因一张坏表让整库旧收藏都进不来。
    // 没有 id 列的表不是收藏表（旧库只有收藏夹表才带 id），整张跳过。
    val tableRows = mutableListOf<Pair<String, List<com.venera.compose.data.platform.SqlRow>>>()
    for (folder in nonEmptyFolders) {
        val columns = runCatching {
            legacy.query("PRAGMA table_info(${quoteSqlIdentifier(folder)})")
                .mapNotNull { it.string("name") }
        }.getOrNull()
        if (columns == null || "id" !in columns) continue
        val rows = runCatching { legacy.query("SELECT * FROM ${quoteSqlIdentifier(folder)}") }
        if (rows.isFailure) {
            degraded = true
            println("D_迁移 读不出 ${folder} 因=${rows.exceptionOrNull()?.message?.take(120)}")
            continue
        }
        tableRows += folder to rows.getOrThrow()
    }
    if (tableRows.isEmpty()) return 0

    var moved = 0
    var tablesWithNoUsableRows = 0
    core.inTransaction {
        tableRows.forEach { (folder, rows) ->
            var usable = 0
            rows.forEach { row ->
                // 单行取值全部兜底：缺列只让这一行作废，不许把整批搬回去重来。
                val comicId = safeValue(row, "id")?.takeIf { it.isNotBlank() }
                if (comicId == null) return@forEach
                usable++
                core.exec(
                    "INSERT OR IGNORE INTO comic_favorite(" +
                        "comic_id, title, author, cover_url, source_name, folder_name, tags, has_update, latest_chapter, created_at" +
                        ") VALUES (?, ?, ?, ?, ?, ?, ?, 0, NULL, ?)",
                    comicId,
                    safeValue(row, "name").orEmpty(),
                    safeValue(row, "author"),
                    safeValue(row, "cover_path").orEmpty(),
                    safeValue(row, "source_key").orEmpty(),
                    folder,
                    safeValue(row, "tags").orEmpty(),
                    parseLegacyTimestamp(safeValue(row, "time")),
                )
                moved++
            }
            // 有行却一条都取不出 id：多半是换了列名的老收藏表，不落标记，留着下次再试。
            if (rows.isNotEmpty() && usable == 0) {
                tablesWithNoUsableRows++
                println("D_迁移 ${folder} 有行但没有可用 id，本轮不落标记")
            }
        }
        if (!degraded && tablesWithNoUsableRows == 0) markMigrated(core)
    }
    return moved
}

/** 旧表取单列：缺列/取不出都归 null，只作废这一行。 */
private fun safeValue(row: com.venera.compose.data.platform.SqlRow, name: String): String? =
    runCatching { row.string(name) }.getOrNull()

private fun markMigrated(core: com.venera.compose.data.platform.SqlDatabase) {
    core.exec(
        "INSERT OR REPLACE INTO migration_flags(name, applied_at) VALUES (?, ?)",
        MIGRATION_FLAG,
        System.currentTimeMillis(),
    )
}

/** 旧库时间是 `yyyy-MM-dd HH:mm:ss` 本地时间；解析不了就记当下，不因此丢条。 */
private fun parseLegacyTimestamp(raw: String?): Long {
    if (raw.isNullOrBlank()) return System.currentTimeMillis()
    return runCatching {
        LocalDateTime.parse(raw.trim().replace(' ', 'T'))
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }.getOrDefault(System.currentTimeMillis())
}

private fun quoteSqlIdentifier(name: String): String = "\"" + name.replace("\"", "\"\"") + "\""
