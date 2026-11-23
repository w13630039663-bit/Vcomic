package com.venera.compose.feature

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.venera.compose.data.api.BusinessPorts
import com.venera.compose.data.db.HistoryRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 历史页的 **平台无关内核**（阶段二 · 阅读历史收敛复用）。
 *
 * 形状照详情页那颗 [ComicDetailCore]：把"端口从哪来"交给构造参数，core 自己只认
 * [BusinessPorts] 的接口（这里真吃的只有 `history` / `favorites` 两族），
 * 于是同一份逻辑两端共编——安卓由薄壳 [HistoryViewModel] 用 `viewModelScope` 建一颗，
 * 桌面由壳直接用 `remember` 出来的作用域建一颗。
 *
 * 不 import 任何 Android 件（无 `Application` / `AndroidViewModel` / `LocalContext`）：
 * 这是它能上桌面编译面的唯一前提，也是 `:desktop:test` 能真跑到这族判据的根据。
 *
 * 对齐原版 `pages/history_page.dart`：多选删除 + 清空（全部 / 仅未收藏）。
 */
class HistoryCore(
    ports: BusinessPorts,
    private val scope: CoroutineScope,
) {

    private val dao = ports.history
    private val favorites = ports.favorites

    val history: StateFlow<List<HistoryRecord>> = dao.historyFlow

    var multiSelectMode: Boolean by mutableStateOf(false)
        private set

    /** 选中项：`(comicId, sourceName)`，与 v2 主键一致。 */
    var selected: Set<Pair<String, String>> by mutableStateOf(emptySet())
        private set

    fun toggleSelect(record: HistoryRecord) {
        val key = record.comicId to record.sourceName
        selected = if (key in selected) selected - key else selected + key
        if (selected.isEmpty()) multiSelectMode = false
    }

    fun enterMultiSelect(record: HistoryRecord) {
        multiSelectMode = true
        selected = selected + (record.comicId to record.sourceName)
    }

    fun selectAll(records: List<HistoryRecord>) {
        selected = records.map { it.comicId to it.sourceName }.toSet()
    }

    fun exitMultiSelect() {
        multiSelectMode = false
        selected = emptySet()
    }

    fun deleteSelected() {
        val records = history.value.filter { (it.comicId to it.sourceName) in selected }
        scope.launch {
            dao.batchDeleteHistory(records)
            exitMultiSelect()
        }
    }

    fun deleteRecords(records: List<HistoryRecord>) {
        if (records.isEmpty()) return
        val keys = records.mapTo(HashSet(records.size)) { it.comicId to it.sourceName }
        scope.launch {
            dao.batchDeleteHistory(records)
            selected = selected - keys
            if (selected.isEmpty()) multiSelectMode = false
        }
    }

    fun deleteSingle(record: HistoryRecord) {
        scope.launch { dao.deleteHistory(record.comicId, record.sourceName) }
    }

    fun clearAll() {
        scope.launch {
            dao.clearAll()
            exitMultiSelect()
        }
    }

    /** 只清空「未被收藏」的历史（官方 `clearUnfavoritedHistory`）。 */
    fun clearUnfavorited() {
        scope.launch {
            val all = history.value
            val toDelete = all.filter { record ->
                !favorites.isExist(record.comicId, record.sourceName)
            }
            dao.batchDeleteHistory(toDelete)
            exitMultiSelect()
        }
    }
}
