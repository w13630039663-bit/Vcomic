package com.venera.compose.data.db

/**
 * 追更条目的状态。
 *
 * `ACTIVE` 才参与巡检；`PAUSED` 停查但保留读数；`COMPLETED` 视为完结，不再巡检。
 * 落库值即枚举名，读回时不认识的旧值归 `ACTIVE`（`status` 列有默认值，不认识的写入只能来自手工改库）。
 */
enum class ComicFollowStatus(val dbValue: String) {
    ACTIVE("ACTIVE"),
    PAUSED("PAUSED"),
    COMPLETED("COMPLETED"),
    ;

    companion object {
        fun of(raw: String?): ComicFollowStatus =
            values().firstOrNull { it.dbValue == raw } ?: ACTIVE
    }
}

/** 追更条目的来源，仅用于展示与排查，不影响巡检。 */
enum class ComicFollowOrigin(val dbValue: String) {
    MANUAL("MANUAL"),
    LOCAL_FAV("LOCAL_FAV"),
    NET_FOLDER("NET_FOLDER"),
    ;

    companion object {
        fun of(raw: String?): ComicFollowOrigin =
            values().firstOrNull { it.dbValue == raw } ?: MANUAL
    }
}

/**
 * 追更账本的一行。
 *
 * [baselineInitialized] = false 表示还没拉到详情建基线（批量导入的初始态），
 * 首次巡检只写基线、不标红；[latestChapterId] / [chapterCount] / [remoteUpdateTime]
 * 是更新判据的三份快照，与 `FollowUpdateDiff` 一一对应。
 */
data class ComicFollowRecord(
    val comicId: String,
    val sourceName: String,
    val title: String,
    val author: String = "",
    val coverUrl: String = "",
    val status: ComicFollowStatus = ComicFollowStatus.ACTIVE,
    val originType: ComicFollowOrigin = ComicFollowOrigin.MANUAL,
    val originFolderId: String = "",
    val originFolderName: String = "",
    val baselineInitialized: Boolean = false,
    val latestChapterId: String = "",
    val latestChapterTitle: String = "",
    val chapterCount: Int = 0,
    val remoteUpdateTime: String = "",
    val hasNewUpdate: Boolean = false,
    val newChaptersCount: Int = 0,
    val lastUpdateDetectedAt: Long = 0L,
    val lastCheckAt: Long = 0L,
    val lastError: String = "",
    val consecutiveFailures: Int = 0,
    val createdAt: Long = 0L,
)

/** 网站收藏夹订阅绑定的一行：键 `(sourceName, networkFolderId)`，单夹源用空串作夹 ID。 */
data class FollowSyncSourceRecord(
    val sourceName: String,
    val networkFolderId: String,
    val networkFolderName: String = "",
    val lastSyncedAt: Long = 0L,
    val lastSyncedCount: Int = 0,
    val autoImportNew: Boolean = true,
)

/** 一次巡检成功后要落的读数；[newChaptersCount] 由判据层算好，DAO 不重算。 */
data class FollowCheckOutcome(
    val latestChapterId: String,
    val latestChapterTitle: String,
    val chapterCount: Int,
    val remoteUpdateTime: String,
    val hasNewUpdate: Boolean,
    val newChaptersCount: Int,
    val detectedAt: Long,
)

/** 批量导入的计数：`inserted` 为真正新增，`skipped` 为库里已有（保留其基线）。 */
data class FollowImportResult(
    val inserted: Int,
    val skipped: Int,
) {
    val total: Int get() = inserted + skipped
}
