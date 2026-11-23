package com.venera.desktop.settings

import androidx.compose.ui.graphics.Color
import com.venera.compose.data.platform.JsonKeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 阅读器画布背景档位。供设置中枢与阅读器右侧抽屉双向读写。
 */
object DesktopReaderBackground {

    const val BLACK = "纯黑"
    const val DARK_GRAY = "深灰"
    const val PAPER_WHITE = "纸白"

    val OPTIONS = listOf(BLACK, DARK_GRAY, PAPER_WHITE)

    private val _index = MutableStateFlow(0)
    val index: StateFlow<Int> = _index.asStateFlow()

    private var persistentStore: JsonKeyValueStore? = null

    /**
     * 绑定持久化存储并还原偏好。
     * @param store 目标键值存储
     */
    fun bind(store: JsonKeyValueStore) {
        persistentStore = store
        val saved = store.getString("pref_desktop_reader_background", null)
        val idx = OPTIONS.indexOf(saved)
        if (idx >= 0) _index.value = idx
    }

    /**
     * 写入背景档位索引。
     * @param idx 目标选项索引
     */
    fun writeIndex(idx: Int) {
        val clamped = idx.coerceIn(0, OPTIONS.lastIndex)
        _index.value = clamped
        persistentStore?.put("pref_desktop_reader_background", OPTIONS[clamped])
    }

    /**
     * 写入背景档位名称。
     * @param store 目标键值存储
     * @param option 选项名称
     */
    fun write(store: JsonKeyValueStore, option: String) {
        persistentStore = store
        val idx = OPTIONS.indexOf(option).coerceAtLeast(0)
        _index.value = idx
        store.put("pref_desktop_reader_background", OPTIONS[idx])
    }

    /**
     * 档位索引换算对应画布背景色。
     * @param idx 档位索引
     */
    fun colorOf(idx: Int): Color = when (idx) {
        1 -> Color(0xFF1D1B20)
        2 -> Color(0xFFE8E2F0)
        else -> Color.Black
    }
}
