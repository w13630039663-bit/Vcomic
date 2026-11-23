package com.venera.compose.data.db

/**
 * VeneraDatabase 的全部建表/迁移 DDL，原样搬自 `VeneraDatabase.kt`（v1 onCreate、v2 DROP 重建、v3 补表）。
 *
 * 这里只放纯字符串常量、零 `android.*` 依赖 —— 桌面侧（`:desktop`）与 `:desktop` 的 JVM 测试
 * 都引用这份，Android 的 `VeneraDatabase` 也逐条执行这里的语句：**两端共用同一棵源，不许出现第二份拷贝**。
 *
 * ⚠️ 用户设备上已有这份 schema，逐字照抄是底线：改缩进、加索引、改类型、调列序都算跨端 schema 漂移。
 * 要动 DDL 请先过迁移评审，并同步 v 号与 onUpgrade 分支。
 */
object SchemaSql {

    /**
     * v4 追更主表与索引。主键 `(comic_id, source_name)`：同一本漫画在不同源各追各的。
     *
     * 为何同时塞进 [V1_CREATE_STATEMENTS]：`CoreDbSchema.upgrade` 的 `oldVersion < 2` 分支
     * DROP 重建后**提前 return**，不再跑后续版本语句；只有 v1 全集也含这两张表，
     * v1 → v4 才会得到与全新库同构的库（哨兵见 `CoreDbMigrationTest`）。
     */
    private val FOLLOW_UPDATE_STATEMENTS: List<String> = listOf(
        """
        CREATE TABLE IF NOT EXISTS comic_follow (
            comic_id TEXT NOT NULL,
            source_name TEXT NOT NULL,
            title TEXT NOT NULL,
            author TEXT NOT NULL DEFAULT '',
            cover_url TEXT NOT NULL DEFAULT '',
            status TEXT NOT NULL DEFAULT 'ACTIVE',
            origin_type TEXT NOT NULL DEFAULT 'MANUAL',
            origin_folder_id TEXT NOT NULL DEFAULT '',
            origin_folder_name TEXT NOT NULL DEFAULT '',
            baseline_initialized INTEGER NOT NULL DEFAULT 0,
            latest_chapter_id TEXT NOT NULL DEFAULT '',
            latest_chapter_title TEXT NOT NULL DEFAULT '',
            chapter_count INTEGER NOT NULL DEFAULT 0,
            remote_update_time TEXT NOT NULL DEFAULT '',
            has_new_update INTEGER NOT NULL DEFAULT 0,
            new_chapters_count INTEGER NOT NULL DEFAULT 0,
            last_update_detected_at INTEGER NOT NULL DEFAULT 0,
            last_check_at INTEGER NOT NULL DEFAULT 0,
            last_error TEXT NOT NULL DEFAULT '',
            consecutive_failures INTEGER NOT NULL DEFAULT 0,
            created_at INTEGER NOT NULL,
            PRIMARY KEY (comic_id, source_name)
        );
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS idx_follow_status_updated ON comic_follow(status, has_new_update DESC, last_update_detected_at DESC);",
        "CREATE INDEX IF NOT EXISTS idx_follow_source ON comic_follow(source_name);",
    )

    /** v4 网站收藏夹订阅绑定表：一次导入记一条，供「同步追更」判重与展示夹名。 */
    private val FOLLOW_SYNC_STATEMENTS: List<String> = listOf(
        """
        CREATE TABLE IF NOT EXISTS comic_follow_sync_source (
            source_name TEXT NOT NULL,
            network_folder_id TEXT NOT NULL,
            network_folder_name TEXT NOT NULL DEFAULT '',
            last_synced_at INTEGER NOT NULL DEFAULT 0,
            last_synced_count INTEGER NOT NULL DEFAULT 0,
            auto_import_new INTEGER NOT NULL DEFAULT 1,
            PRIMARY KEY (source_name, network_folder_id)
        );
        """.trimIndent(),
    )

    /** v1 `onCreate` 全集：6 张建表语句 + 紧邻的索引语句，顺序与 VeneraDatabase.onCreate 逐条一致；v4 两表附尾。 */
    val V1_CREATE_STATEMENTS: List<String> = listOf(
        // 1. 阅读历史表
        """
        CREATE TABLE IF NOT EXISTS comic_history (
            comic_id TEXT NOT NULL,
            title TEXT NOT NULL,
            author TEXT,
            cover_url TEXT NOT NULL,
            source_name TEXT NOT NULL,
            last_chapter_title TEXT NOT NULL,
            last_chapter_index INTEGER NOT NULL,
            last_page_index INTEGER NOT NULL,
            total_pages INTEGER NOT NULL,
            updated_at INTEGER NOT NULL,
            -- v2：主键必须含 source_name，否则不同源的同 ID 漫画会互相覆盖阅读进度
            PRIMARY KEY (comic_id, source_name)
        );
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS idx_history_updated_at ON comic_history(updated_at DESC);",

        // 2. 收藏夹表
        """
        CREATE TABLE IF NOT EXISTS comic_favorite (
            comic_id TEXT NOT NULL,
            title TEXT NOT NULL,
            author TEXT,
            cover_url TEXT NOT NULL,
            source_name TEXT NOT NULL,
            folder_name TEXT NOT NULL DEFAULT '默认',
            tags TEXT NOT NULL DEFAULT '',
            has_update INTEGER NOT NULL DEFAULT 0,
            latest_chapter TEXT,
            created_at INTEGER NOT NULL,
            -- v2：同样按 (comic_id, source_name) 唯一，收藏不再串源
            PRIMARY KEY (comic_id, source_name)
        );
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS idx_favorite_folder ON comic_favorite(folder_name);",
        "CREATE INDEX IF NOT EXISTS idx_favorite_created ON comic_favorite(created_at DESC);",

        // 3. 漫画源元数据表
        """
        CREATE TABLE IF NOT EXISTS comic_source (
            source_id TEXT PRIMARY KEY,
            name TEXT NOT NULL,
            version TEXT NOT NULL,
            icon_url TEXT,
            is_enabled INTEGER NOT NULL DEFAULT 1,
            sort_order INTEGER NOT NULL DEFAULT 0,
            config_json TEXT NOT NULL DEFAULT '{}'
        );
        """.trimIndent(),

        // 4. 阅读统计表
        """
        CREATE TABLE IF NOT EXISTS reading_stats (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            comic_id TEXT NOT NULL,
            comic_title TEXT NOT NULL,
            source_name TEXT NOT NULL,
            tags TEXT NOT NULL DEFAULT '',
            chapter_title TEXT NOT NULL,
            pages_read INTEGER NOT NULL DEFAULT 0,
            duration_seconds INTEGER NOT NULL DEFAULT 0,
            read_date TEXT NOT NULL,
            created_at INTEGER NOT NULL
        );
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS idx_reading_stats_date ON reading_stats(read_date);",
        "CREATE INDEX IF NOT EXISTS idx_reading_stats_comic ON reading_stats(comic_id, source_name);",

        // 5. 单页/图片收藏表
        """
        CREATE TABLE IF NOT EXISTS favorite_images (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            comic_id TEXT NOT NULL,
            comic_title TEXT NOT NULL,
            source_name TEXT NOT NULL,
            chapter_title TEXT NOT NULL,
            page_index INTEGER NOT NULL,
            image_url TEXT NOT NULL,
            local_path TEXT,
            created_at INTEGER NOT NULL
        );
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS idx_favorite_images_created ON favorite_images(created_at DESC);",

        // 6. 屏蔽过滤规则表
        """
        CREATE TABLE IF NOT EXISTS content_guard_rules (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            rule_type TEXT NOT NULL,
            pattern TEXT NOT NULL,
            is_regex INTEGER NOT NULL DEFAULT 0,
            is_enabled INTEGER NOT NULL DEFAULT 1,
            created_at INTEGER NOT NULL
        );
        """.trimIndent(),
    ) + FOLLOW_UPDATE_STATEMENTS + FOLLOW_SYNC_STATEMENTS

    /** v4 迁移补表：追更账本与网站收藏夹绑定表；纯增量，不碰任何旧表列与存量数据。 */
    val V4_CREATE_STATEMENTS: List<String> = FOLLOW_UPDATE_STATEMENTS + FOLLOW_SYNC_STATEMENTS

    /** v2 迁移：DROP 重建（语义照搬 onUpgrade 的 oldVersion < 2 分支，DROP 之后重跑 [V1_CREATE_STATEMENTS]）。 */
    val V2_DROP_STATEMENTS: List<String> = listOf(
        "DROP TABLE IF EXISTS comic_history;",
        "DROP TABLE IF EXISTS comic_favorite;",
        "DROP TABLE IF EXISTS comic_source;",
    )

    /** v3 迁移补表：reading_stats / favorite_images / content_guard_rules 三张新表及其索引，顺序与 onUpgrade 一致。 */
    val V3_CREATE_STATEMENTS: List<String> = listOf(
        """
        CREATE TABLE IF NOT EXISTS reading_stats (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            comic_id TEXT NOT NULL,
            comic_title TEXT NOT NULL,
            source_name TEXT NOT NULL,
            tags TEXT NOT NULL DEFAULT '',
            chapter_title TEXT NOT NULL,
            pages_read INTEGER NOT NULL DEFAULT 0,
            duration_seconds INTEGER NOT NULL DEFAULT 0,
            read_date TEXT NOT NULL,
            created_at INTEGER NOT NULL
        );
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS idx_reading_stats_date ON reading_stats(read_date);",
        "CREATE INDEX IF NOT EXISTS idx_reading_stats_comic ON reading_stats(comic_id, source_name);",

        """
        CREATE TABLE IF NOT EXISTS favorite_images (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            comic_id TEXT NOT NULL,
            comic_title TEXT NOT NULL,
            source_name TEXT NOT NULL,
            chapter_title TEXT NOT NULL,
            page_index INTEGER NOT NULL,
            image_url TEXT NOT NULL,
            local_path TEXT,
            created_at INTEGER NOT NULL
        );
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS idx_favorite_images_created ON favorite_images(created_at DESC);",

        """
        CREATE TABLE IF NOT EXISTS content_guard_rules (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            rule_type TEXT NOT NULL,
            pattern TEXT NOT NULL,
            is_regex INTEGER NOT NULL DEFAULT 0,
            is_enabled INTEGER NOT NULL DEFAULT 1,
            created_at INTEGER NOT NULL
        );
        """.trimIndent(),
    )
}
