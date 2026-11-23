package com.venera.desktop.follow

import com.venera.compose.data.db.ComicFollowDao
import com.venera.compose.data.db.ComicFollowRecord
import com.venera.compose.source.model.ComicDetails
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 详情页追更按钮的可见状态：`following = null` 表示还没读回来（按钮置灰，不装成"没追"）。 */
data class FollowDetailUiState(
    val following: Boolean? = null,
    val busy: Boolean = false,
)

/**
 * 详情页与追更账本之间的唯一一层：屏只读 [state] 并调 [attach] / [toggle]，不直接碰 DAO。
 *
 * 状态是单本作用域（一个 port 实例只服务当前打开的那一本），换本时装配点重建。
 *
 * @param dao 追更账本读写口。
 * @param clock 取当前 epoch 毫秒；新加入的 `created_at` / `last_check_at` 用它。
 */
class DesktopFollowDetailPort(
    private val dao: ComicFollowDao,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    private val _state = MutableStateFlow(FollowDetailUiState())

    /** 详情页按钮态：装配点每本重建一颗 port，屏只订阅这一条。 */
    val state: StateFlow<FollowDetailUiState> = _state.asStateFlow()

    /**
     * 详情就绪后挂上这一本：读一次追更态；已追更且带未读红点时顺手消红。
     *
     * 进入详情即视为已看，与「读到最新话自动消红点」同一条口径，避免红点只能靠手动清。
     *
     * @param comicId 源内漫画 ID。
     * @param sourceName 源 key。
     */
    suspend fun attach(comicId: String, sourceName: String) {
        val row = runCatching { dao.get(comicId, sourceName) }.getOrNull()
        _state.value = FollowDetailUiState(following = row != null, busy = false)
        if (row != null && row.hasNewUpdate) {
            runCatching { dao.markRead(comicId, sourceName) }
        }
    }

    /**
     * 点击「追更 / 已追更」。
     *
     * 未追 ⇒ 以 [snapshot] 建基线（`baseline_initialized = 1`、不标红）；已追 ⇒ 删除该本。
     *
     * @param comicId 源内漫画 ID。
     * @param sourceName 源 key。
     * @param title 标题快照。
     * @param author 作者快照。
     * @param coverUrl 封面快照。
     * @param snapshot 详情当前章节快照，作为首次追更的基线。
     * @return 操作后的追更态（true = 已加入，false = 已取消）。
     */
    suspend fun toggle(
        comicId: String,
        sourceName: String,
        title: String,
        author: String,
        coverUrl: String,
        snapshot: FollowSnapshot,
    ): Boolean {
        _state.value = _state.value.copy(busy = true)
        return try {
            val existing = dao.get(comicId, sourceName)
            if (existing != null) {
                dao.remove(comicId, sourceName)
                _state.value = FollowDetailUiState(following = false, busy = false)
                false
            } else {
                val now = clock()
                dao.addIfAbsent(
                    ComicFollowRecord(
                        comicId = comicId,
                        sourceName = sourceName,
                        title = title,
                        author = author,
                        coverUrl = coverUrl,
                        baselineInitialized = true,
                        latestChapterId = snapshot.latestChapterId,
                        latestChapterTitle = snapshot.latestChapterTitle,
                        chapterCount = snapshot.chapterCount,
                        remoteUpdateTime = snapshot.remoteUpdateTime,
                        lastCheckAt = now,
                        createdAt = now,
                    ),
                )
                _state.value = FollowDetailUiState(following = true, busy = false)
                true
            }
        } catch (e: Throwable) {
            _state.value = _state.value.copy(busy = false)
            throw e
        }
    }
}

/**
 * 详情模型压成更新判据的快照：末章 ID / 标题 + 总话数 + 源更新时间。
 *
 * 占位章（整本一章的图库源）不计话数、不取标题，让判据走 `updateTime` 副判据。
 */
fun ComicDetails.toFollowSnapshot(): FollowSnapshot {
    val episodes = chapters.filter { it.id.isNotBlank() }
    val last = episodes.lastOrNull()
    val placeholder = chapters.size == 1 && last?.title.isNullOrBlank()
    return FollowSnapshot(
        latestChapterId = if (placeholder) "" else last?.id.orEmpty(),
        latestChapterTitle = if (placeholder) "" else last?.title.orEmpty(),
        chapterCount = if (placeholder) 0 else episodes.size,
        remoteUpdateTime = updateTime,
    )
}
