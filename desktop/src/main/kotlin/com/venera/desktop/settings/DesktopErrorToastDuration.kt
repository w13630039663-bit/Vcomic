package com.venera.desktop.settings

import com.venera.compose.data.platform.JsonKeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 错误弹窗自动消失时间设置。
 *
 * 默认 3 秒自动收起为右下角计数胶囊；`0L` 表示不自动关闭，需手动关闭或点开面板。
 */
object DesktopErrorToastDuration {

    const val PREF_KEY = "pref_desktop_error_toast_duration_ms"
    const val DEFAULT_MS = 3_000L

    /** 可选档位（毫秒）：2秒 / 3秒 / 5秒 / 8秒 / 不自动关闭。 */
    val PRESETS_MS: List<Long> = listOf(2_000L, 3_000L, 5_000L, 8_000L, 0L)

    private val _durationMs = MutableStateFlow(DEFAULT_MS)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    /** 冷启动回填；非法或未知值回退到默认 3 秒。 */
    fun restore(raw: String?) {
        val parsed = raw?.toLongOrNull()
        _durationMs.value = if (parsed != null && parsed in PRESETS_MS) parsed else DEFAULT_MS
    }

    /** 写入热流并持久化到主 prefs。 */
    fun write(store: JsonKeyValueStore, ms: Long) {
        val next = if (ms in PRESETS_MS) ms else DEFAULT_MS
        _durationMs.value = next
        store.put(PREF_KEY, next.toString())
    }

    /** 当前档位在 [PRESETS_MS] 中的索引。 */
    fun indexOf(ms: Long): Int = PRESETS_MS.indexOf(ms).takeIf { it >= 0 } ?: 1

    /** 档位展示文案。 */
    fun optionLabels(isEnglish: Boolean): List<String> = if (isEnglish) {
        listOf("2s", "3s", "5s", "8s", "Manual")
    } else {
        listOf("2 秒", "3 秒", "5 秒", "8 秒", "不自动关闭")
    }
}
