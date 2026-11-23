package com.venera.desktop

import com.venera.compose.data.db.DatabasePorts
import com.venera.compose.data.db.HistoryDao
import com.venera.compose.data.db.HistoryRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext

/**
 * 桌面首页「历史记录」的数据口。
 *
 * ## 为什么读 `historyFlow` 而不是 `getHistory`
 *
 * `HistoryDao` 只有两条读口：`getHistory(comicId)` 是**单条**（按 id 查一本），
 * `getAllHistorySync()` 是 **private**。首页要的是「最近几本」这个**列表**，
 * 唯一能拿到的口就是 `historyFlow: StateFlow<List<HistoryRecord>>`。
 *
 * ## `loaded` 这个标志为什么必须存在
 *
 * `historyFlow` 的初值是 `emptyList()`，由 `HistoryDao.init { refresh() }` **异步**填。
 * 于是 `historyList.isEmpty()` 同时代表两件事：
 *   - 库还没读回来（短暂，冷启动那一两帧）
 *   - 库读回来了，确实没有历史记录
 *
 * 屏上这两件事要说不同的话。合成一句"还没有阅读记录"就等于把「库还没开」
 * 谎报成「你没读过书」—— 这是本仓反复记过的那类"假完成"。
 *
 * @return `Pair<列表, 是否已读回>`
 */
internal object DesktopHomeHistorySource {

    /**
     * 读一次历史。**不吞异常**：库打不开时返回 `emptyList() to false`，
     * 让屏上说"正在读…"是错的 —— 所以这里其实返回 `to true` 并单独给成因。
     *
     * 简化处理：读失败时把成因拼进 [lastError]，屏上那格显示成因（不显示成"没有记录"）。
     */
    private var lastError: String? = null

    suspend fun read(): DesktopHomeHistorySnapshot = withContext(Dispatchers.IO) {
        try {
            val dao = HistoryDao(DatabasePorts.of(null).core)

            // refresh() 为异步发射，flow 初值为 emptyList()；
            // 必须用 first { it.isNotEmpty() } 跳过初值并配合 2s 超时兜底，避免空库时永久挂起。
            dao.refresh()

            val waited = withTimeoutOrNull(2_000) { dao.historyFlow.first { it.isNotEmpty() } }

            // 超时后再看一眼 .value：那时 refresh 的协程大概率已跑完。
            // 这一步是"再给一次机会"，不是"重复读"—— 它把"读得慢"与"确实没有"分开。
            val records = waited ?: dao.historyFlow.value

            lastError = null
            // `note` 只在**真的说不清**时给：超时了、且 .value 仍空。
            // 若超时但 .value 非空，说明只是慢，不当失败报。
            val note = if (waited == null && records.isEmpty()) {
                // 屏上短句，成因细节进 stdout。
                println("D_首页历史 读超时(2s)且仍空（库可能被占用，或确实还没有记录）")
                "历史记录读取超时"
            } else {
                null
            }
            DesktopHomeHistorySnapshot(records = records, loaded = true, note = note)
        } catch (t: Throwable) {
            // 原文只进日志，屏上念归档短句。
            println("D_首页历史 塌了 ${t::class.java.simpleName}: ${t.message?.take(160)}")
            lastError = com.venera.desktop.platform.desktopErrorKindOf(t).label
            // `loaded = true` 是刻意的：这说明"读过了，但失败了"，
            // 屏上要显示成因而不是"正在读…"（后者会一直停在假加载态）。
            DesktopHomeHistorySnapshot(records = emptyList(), loaded = true, note = lastError)
        }
    }
}

/** 历史那一格的数据：列表 + 是否已读回 + 读失败时的成因。 */
internal data class DesktopHomeHistorySnapshot(
    val records: List<HistoryRecord>,
    /** `false` = 还没读回来；`true` = 读过了（无论有没有记录）。 */
    val loaded: Boolean,
    /** 非空 = 读失败，屏上照原话说。 */
    val note: String?,
) {
    companion object {
        /** 冷启动第一帧：还没读。**不是**"没有历史"。 */
        val Loading = DesktopHomeHistorySnapshot(emptyList(), loaded = false, note = null)
    }
}
