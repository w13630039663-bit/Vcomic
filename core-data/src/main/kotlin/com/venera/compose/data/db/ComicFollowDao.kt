package com.venera.compose.data.db

import com.venera.compose.data.platform.SqlDatabase
import com.venera.compose.data.platform.SqlRow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 追更账本 `comic_follow` 与网站收藏夹绑定表 `comic_follow_sync_source` 的唯一读写方。
 *
 * 与 [FavoriteDao] 同一条口径：DAO 只认 [SqlDatabaseSource]，SQL 显式写、值一律参数绑定；
 * `followsFlow` 是 UI 的单一投影源，`comic_favorite` 的 `has_update` / `latest_chapter`
 * 不再由本功能回写（两张表各写各的，避免状态漂移）。
 *
 * @param source 库连接获取口；读写分开取，桌面侧交回同一实例。
 * @param clock 取当前 epoch 毫秒；测试传假时钟，生产用系统时间。
 */
class ComicFollowDao(
    private val source: SqlDatabaseSource,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _follows = MutableStateFlow<List<ComicFollowRecord>>(emptyList())

    /** 追更清单投影：写入后异步刷新，UI 只读这一条流。 */
    val followsFlow: StateFlow<List<ComicFollowRecord>> = _follows.asStateFlow()

    init {
        refresh()
    }

    /** 重新读全表推到 [followsFlow]；写操作内部已调，UI 一般不用直接调。 */
    fun refresh() {
        scope.launch { _follows.value = listSync() }
    }

    /** 取消后台刷新协程；窗口关闭时调，之后任何写操作都不该再发生。 */
    fun close() {
        scope.cancel()
    }

    /** 同步读全表：按「有更新优先、最近发现时间优先」排序，供导入判重与首帧投影。 */
    fun listSync(): List<ComicFollowRecord> {
        val db = source.reader()
        return db.query("SELECT * FROM comic_follow ORDER BY has_new_update DESC, last_update_detected_at DESC, created_at DESC")
            .map { it.toFollowRecord() }
    }

    /** 单条读：库里没有交回 null（调用方据此区分「没追」与「追了但没建基线」）。 */
    suspend fun get(comicId: String, sourceName: String): ComicFollowRecord? = withContext(Dispatchers.IO) {
        source.reader().query(
            "SELECT * FROM comic_follow WHERE comic_id = ? AND source_name = ? LIMIT 1",
            comicId,
            sourceName,
        ).firstOrNull()?.toFollowRecord()
    }

    /** 某本是否已在追更清单里。 */
    suspend fun isFollowing(comicId: String, sourceName: String): Boolean = withContext(Dispatchers.IO) {
        source.reader().query(
            "SELECT 1 FROM comic_follow WHERE comic_id = ? AND source_name = ? LIMIT 1",
            comicId,
            sourceName,
        ).isNotEmpty()
    }

    /**
     * 新增追更；库里已有同一 `(comicId, sourceName)` 时整条忽略、保留其基线。
     *
     * @return true = 本次真的插入；false = 已存在，什么都没改。
     */
    suspend fun addIfAbsent(record: ComicFollowRecord): Boolean = withContext(Dispatchers.IO) {
        val inserted = insertOne(source.writer(), record)
        if (inserted) refresh()
        inserted
    }

    /** 取消追更：按双主键删，不会带走其他源同 ID 的记录。 */
    suspend fun remove(comicId: String, sourceName: String) = withContext(Dispatchers.IO) {
        source.writer().exec(
            "DELETE FROM comic_follow WHERE comic_id = ? AND source_name = ?",
            comicId,
            sourceName,
        )
        refresh()
    }

    /** 改状态（暂停 / 恢复 / 标完结）；状态只影响巡检，不动已读水位。 */
    suspend fun setStatus(comicId: String, sourceName: String, status: ComicFollowStatus) =
        withContext(Dispatchers.IO) {
            source.writer().exec(
                "UPDATE comic_follow SET status = ? WHERE comic_id = ? AND source_name = ?",
                status.dbValue,
                comicId,
                sourceName,
            )
            refresh()
        }

    /** 消红点：清零未读标记与新增话数，保留最新章节读数。 */
    suspend fun markRead(comicId: String, sourceName: String) = withContext(Dispatchers.IO) {
        source.writer().exec(
            "UPDATE comic_follow SET has_new_update = 0, new_chapters_count = 0 " +
                "WHERE comic_id = ? AND source_name = ?",
            comicId,
            sourceName,
        )
        refresh()
    }

    /**
     * 以详情当前章节建基线（首次追更、或从未建基线的导入记录第一次拉到详情）。
     *
     * 只写快照、不标红：`has_new_update = 0`、新增话数清零，避免批量导入后满屏红点。
     */
    suspend fun initializeBaseline(
        comicId: String,
        sourceName: String,
        latestChapterId: String,
        latestChapterTitle: String,
        chapterCount: Int,
        remoteUpdateTime: String,
        checkedAt: Long = clock(),
    ) = withContext(Dispatchers.IO) {
        source.writer().exec(
            "UPDATE comic_follow SET baseline_initialized = 1, latest_chapter_id = ?, latest_chapter_title = ?, " +
                "chapter_count = ?, remote_update_time = ?, has_new_update = 0, new_chapters_count = 0, " +
                "last_check_at = ?, last_error = '', consecutive_failures = 0 " +
                "WHERE comic_id = ? AND source_name = ?",
            latestChapterId,
            latestChapterTitle,
            chapterCount,
            remoteUpdateTime,
            checkedAt,
            comicId,
            sourceName,
        )
        refresh()
    }

    /**
     * 巡检成功落读数：章节快照与红点由判据层算好整笔写入，同时清错误与连败计数。
     *
     * 已经建过基线的条目再次检查时，[FollowCheckOutcome] 的 `hasNewUpdate` / `newChaptersCount`
     * 可保留 0（没新更），不会把已有红点误清 —— 判据层在「没有新更」时传旧值。
     */
    suspend fun applyCheckSuccess(
        comicId: String,
        sourceName: String,
        outcome: FollowCheckOutcome,
    ) = withContext(Dispatchers.IO) {
        source.writer().exec(
            "UPDATE comic_follow SET latest_chapter_id = ?, latest_chapter_title = ?, chapter_count = ?, " +
                "remote_update_time = ?, baseline_initialized = 1, has_new_update = ?, new_chapters_count = ?, " +
                "last_update_detected_at = ?, last_check_at = ?, last_error = '', consecutive_failures = 0 " +
                "WHERE comic_id = ? AND source_name = ?",
            outcome.latestChapterId,
            outcome.latestChapterTitle,
            outcome.chapterCount,
            outcome.remoteUpdateTime,
            if (outcome.hasNewUpdate) 1 else 0,
            outcome.newChaptersCount,
            outcome.detectedAt,
            clock(),
            comicId,
            sourceName,
        )
        refresh()
    }

    /** 巡检失败落错误：只写检查时间、错误原话与连败计数，绝不覆盖已有章节快照与红点。 */
    suspend fun applyCheckFailure(
        comicId: String,
        sourceName: String,
        error: String,
        checkedAt: Long = clock(),
    ) = withContext(Dispatchers.IO) {
        source.writer().exec(
            "UPDATE comic_follow SET last_check_at = ?, last_error = ?, " +
                "consecutive_failures = consecutive_failures + 1 WHERE comic_id = ? AND source_name = ?",
            checkedAt,
            error,
            comicId,
            sourceName,
        )
        refresh()
    }

    /**
     * 批量导入：库里已有的 `(comicId, sourceName)` 一律跳过并保留原基线。
     *
     * 整批一个事务，失败即整批回滚；[records] 内部的重复也靠 `INSERT OR IGNORE` 收敛。
     *
     * @param records 待导入记录；`createdAt` 为 0 时按本次 [clock] 填。
     * @return 新增与跳过各自的条数。
     */
    suspend fun importBatch(records: List<ComicFollowRecord>): FollowImportResult = withContext(Dispatchers.IO) {
        if (records.isEmpty()) return@withContext FollowImportResult(0, 0)
        val now = clock()
        val db = source.writer()
        var inserted = 0
        db.inTransaction {
            records.forEach { raw ->
                val record = if (raw.createdAt > 0L) raw else raw.copy(createdAt = now)
                if (insertOne(db, record)) inserted++
            }
        }
        refresh()
        FollowImportResult(inserted = inserted, skipped = records.size - inserted)
    }

    /** 已绑定的网站收藏夹清单（导入器判「首导 / 同步」用）。 */
    suspend fun listSyncSources(): List<FollowSyncSourceRecord> = withContext(Dispatchers.IO) {
        source.reader().query("SELECT * FROM comic_follow_sync_source").map { it.toSyncSourceRecord() }
    }

    /**
     * 绑定或刷新一个网站收藏夹；`auto_import_new` 只在插入时生效，已有绑定保留原值。
     *
     * @param record 绑定信息；`lastSyncedAt` / `lastSyncedCount` 为 0 时不清空旧读数。
     */
    suspend fun upsertSyncSource(record: FollowSyncSourceRecord) = withContext(Dispatchers.IO) {
        val db = source.writer()
        val exists = db.query(
            "SELECT 1 FROM comic_follow_sync_source WHERE source_name = ? AND network_folder_id = ? LIMIT 1",
            record.sourceName,
            record.networkFolderId,
        ).isNotEmpty()
        if (exists) {
            db.exec(
                "UPDATE comic_follow_sync_source SET network_folder_name = ?, " +
                    "last_synced_at = CASE WHEN ? > 0 THEN ? ELSE last_synced_at END, " +
                    "last_synced_count = CASE WHEN ? > 0 THEN ? ELSE last_synced_count END " +
                    "WHERE source_name = ? AND network_folder_id = ?",
                record.networkFolderName,
                record.lastSyncedAt, record.lastSyncedAt,
                record.lastSyncedCount, record.lastSyncedCount,
                record.sourceName,
                record.networkFolderId,
            )
        } else {
            db.exec(
                "INSERT INTO comic_follow_sync_source " +
                    "(source_name, network_folder_id, network_folder_name, last_synced_at, last_synced_count, auto_import_new) " +
                    "VALUES (?, ?, ?, ?, ?, ?)",
                record.sourceName,
                record.networkFolderId,
                record.networkFolderName,
                record.lastSyncedAt,
                record.lastSyncedCount,
                if (record.autoImportNew) 1 else 0,
            )
        }
        refresh()
    }

    /** 解除一个网站收藏夹绑定；已导入的条目保留，用户可逐条取消追更。 */
    suspend fun removeSyncSource(sourceName: String, networkFolderId: String) = withContext(Dispatchers.IO) {
        source.writer().exec(
            "DELETE FROM comic_follow_sync_source WHERE source_name = ? AND network_folder_id = ?",
            sourceName,
            networkFolderId,
        )
        refresh()
    }

    private fun insertOne(db: SqlDatabase, record: ComicFollowRecord): Boolean {
        val before = countRows(db)
        db.exec(
            "INSERT OR IGNORE INTO comic_follow " +
                "(comic_id, source_name, title, author, cover_url, status, origin_type, " +
                "origin_folder_id, origin_folder_name, baseline_initialized, latest_chapter_id, " +
                "latest_chapter_title, chapter_count, remote_update_time, has_new_update, " +
                "new_chapters_count, last_update_detected_at, last_check_at, last_error, " +
                "consecutive_failures, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            record.comicId,
            record.sourceName,
            record.title,
            record.author,
            record.coverUrl,
            record.status.dbValue,
            record.originType.dbValue,
            record.originFolderId,
            record.originFolderName,
            if (record.baselineInitialized) 1 else 0,
            record.latestChapterId,
            record.latestChapterTitle,
            record.chapterCount,
            record.remoteUpdateTime,
            if (record.hasNewUpdate) 1 else 0,
            record.newChaptersCount,
            record.lastUpdateDetectedAt,
            record.lastCheckAt,
            record.lastError,
            record.consecutiveFailures,
            record.createdAt,
        )
        return countRows(db) > before
    }

    private fun countRows(db: SqlDatabase): Int =
        db.query("SELECT COUNT(*) AS c FROM comic_follow").firstOrNull()?.long("c")?.toInt() ?: 0

    private fun SqlRow.toFollowRecord(): ComicFollowRecord = ComicFollowRecord(
        comicId = requiredString("comic_id"),
        sourceName = requiredString("source_name"),
        title = requiredString("title"),
        author = optString("author"),
        coverUrl = optString("cover_url"),
        status = ComicFollowStatus.of(optString("status")),
        originType = ComicFollowOrigin.of(optString("origin_type")),
        originFolderId = optString("origin_folder_id"),
        originFolderName = optString("origin_folder_name"),
        baselineInitialized = optLongOrZero("baseline_initialized") == 1L,
        latestChapterId = optString("latest_chapter_id"),
        latestChapterTitle = optString("latest_chapter_title"),
        chapterCount = optInt("chapter_count"),
        remoteUpdateTime = optString("remote_update_time"),
        hasNewUpdate = optLongOrZero("has_new_update") == 1L,
        newChaptersCount = optInt("new_chapters_count"),
        lastUpdateDetectedAt = optLongOrZero("last_update_detected_at"),
        lastCheckAt = optLongOrZero("last_check_at"),
        lastError = optString("last_error"),
        consecutiveFailures = optInt("consecutive_failures"),
        createdAt = optLongOrZero("created_at"),
    )

    private fun SqlRow.toSyncSourceRecord(): FollowSyncSourceRecord = FollowSyncSourceRecord(
        sourceName = requiredString("source_name"),
        networkFolderId = requiredString("network_folder_id"),
        networkFolderName = optString("network_folder_name"),
        lastSyncedAt = optLongOrZero("last_synced_at"),
        lastSyncedCount = optInt("last_synced_count"),
        autoImportNew = optLongOrZero("auto_import_new") == 1L,
    )

    companion object {
        @Volatile
        private var INSTANCE: ComicFollowDao? = null

        /**
         * 进程内单例，与 [FavoriteDao.getInstance] 同一静态口口径。
         *
         * @param handle 平台句柄，原样转给 [DatabasePorts.of]；桌面侧传 `DesktopDatabasePorts.PLATFORM`。
         */
        fun getInstance(handle: Any?): ComicFollowDao =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: ComicFollowDao(DatabasePorts.of(handle).core).also { INSTANCE = it }
            }

        /** 仅在已建实例时取消后台刷新协程；未用过追更时不触发建库。 */
        fun closeIfInitialized() {
            INSTANCE?.close()
        }
    }
}
