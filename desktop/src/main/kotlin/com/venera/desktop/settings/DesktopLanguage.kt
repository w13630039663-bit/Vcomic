package com.venera.desktop.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 桌面语言设置：中文 / English。
 *
 * 形状照 [DesktopThemeMode]：单例热流（设置行写完界面热切）+ 主 prefs 文件同名键落盘，
 * 冷启动由主壳读盘回填 [mode]。
 */
object DesktopLanguage {

    const val ZH = "zh"
    const val EN = "en"

    const val LABEL_ZH = "简体中文"
    const val LABEL_EN = "English"

    private val _mode = MutableStateFlow(ZH)

    /** 当前语言代号（"zh" 或 "en"）。 */
    val mode: StateFlow<String> = _mode.asStateFlow()

    /** 快速判断当前是否为英文。 */
    fun isEnglish(): Boolean = _mode.value == EN

    /** 主壳冷启动回填（盘上没值时保持中文出厂档）。 */
    fun restore(saved: String) {
        if (saved == ZH || saved == EN) {
            _mode.value = saved
        }
    }

    /** 设置行的写侧：改流（热切）+落盘（冷启动）。 */
    fun write(store: com.venera.compose.data.platform.JsonKeyValueStore, value: String) {
        val next = if (value == EN || value == LABEL_EN) EN else ZH
        _mode.value = next
        store.put("pref_desktop_language", next)
    }

    /** 辅助函数：根据当前语言取对应文案。 */
    fun tr(zh: String, en: String): String = if (isEnglish()) en else zh
}
