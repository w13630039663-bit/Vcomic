package com.venera.desktop.follow

import com.venera.compose.data.db.ComicFollowRecord

/**
 * 一次详情取回后要参与比对的三份快照。
 *
 * [latestChapterId] / [latestChapterTitle] 取源返回章节表的**末项**（与详情屏同序）；
 * [chapterCount] 是总话数；[remoteUpdateTime] 只在无章节源里当副判据。
 */
data class FollowSnapshot(
    val latestChapterId: String,
    val latestChapterTitle: String,
    val chapterCount: Int,
    val remoteUpdateTime: String,
)

/** 比对结论：首次建基线、真有新更、没有变化。 */
enum class FollowDiffKind { BASELINE, UPDATED, UNCHANGED }

/**
 * 比对后的落库计划。
 *
 * [hasNewUpdate] 是**整条记录的红点终值**（不是"本次是否变化"）：没有变化时沿用旧红点，
 * 避免一次普通巡检把用户没读的未读标记抹掉。
 */
data class FollowDiffPlan(
    val kind: FollowDiffKind,
    val hasNewUpdate: Boolean,
    val newChaptersCount: Int,
    val detectedAt: Long,
    val snapshot: FollowSnapshot,
)

/**
 * 追更更新判定的纯逻辑：不碰库、不碰网络、不看真实时钟，判据直接喂假快照。
 *
 * 双因子：
 *  - 有章节：总话数增加、最新章节 ID 变化、或非空的最新话标题变化 ⇒ 有新更；
 *    话数减少（源删章）不算；最新章节字段本次为空（源没报全）不参与比较。
 *  - 无章节：`remote_update_time` 非空且与上次不同 ⇒ 有新更。
 */
object FollowUpdateDiff {

    /** 本次详情取数是否带章节信息（任一侧有章节快照即算章节型）。 */
    private fun chapterBased(previous: ComicFollowRecord, current: FollowSnapshot): Boolean =
        previous.chapterCount > 0 || previous.latestChapterId.isNotBlank() ||
            current.chapterCount > 0 || current.latestChapterId.isNotBlank()

    /**
     * 比对上次快照与本次详情。
     *
     * @param previous 库里现有记录；`baselineInitialized = false` 时走首次基线分支。
     * @param current 本次详情快照。
     * @param now 发现更新的时间（epoch 毫秒），只用于 `UPDATED` 的置顶排序。
     */
    fun plan(previous: ComicFollowRecord, current: FollowSnapshot, now: Long): FollowDiffPlan {
        if (!previous.baselineInitialized) {
            return FollowDiffPlan(
                kind = FollowDiffKind.BASELINE,
                hasNewUpdate = false,
                newChaptersCount = 0,
                detectedAt = 0L,
                snapshot = current,
            )
        }

        val changed = if (chapterBased(previous, current)) {
            val countGrew = current.chapterCount > previous.chapterCount
            // 话数减少（源删章）是源侧收缩，不是新更；此时末项变化也只是"最末话变成了别的章"。
            val countShrank = previous.chapterCount > 0 &&
                current.chapterCount in 1 until previous.chapterCount
            if (countShrank) {
                false
            } else {
                val idChanged = current.latestChapterId.isNotBlank() &&
                    current.latestChapterId != previous.latestChapterId
                val titleChanged = current.latestChapterTitle.isNotBlank() &&
                    current.latestChapterTitle != previous.latestChapterTitle
                countGrew || idChanged || titleChanged
            }
        } else {
            current.remoteUpdateTime.isNotBlank() &&
                current.remoteUpdateTime != previous.remoteUpdateTime
        }

        if (!changed) {
            return FollowDiffPlan(
                kind = FollowDiffKind.UNCHANGED,
                hasNewUpdate = previous.hasNewUpdate,
                newChaptersCount = previous.newChaptersCount,
                detectedAt = previous.lastUpdateDetectedAt,
                snapshot = current,
            )
        }

        // 未读红点还没消就再次发现更新时，新增话数在旧读数上累加，不用"本次变化量"把旧数覆盖掉。
        val carried = if (previous.hasNewUpdate) previous.newChaptersCount else 0
        val delta = (current.chapterCount - previous.chapterCount).coerceAtLeast(1)
        return FollowDiffPlan(
            kind = FollowDiffKind.UPDATED,
            hasNewUpdate = true,
            newChaptersCount = carried + delta,
            detectedAt = now,
            snapshot = current,
        )
    }
}
