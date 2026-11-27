package com.venera.desktop.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 桌面主题模式（档二）：深色 / 浅色 / 跟随系统。
 *
 * 为什么是单例流而不是只读盘：设置行写完要**热切**（修订第 19 条），主壳与
 * 设置中枢要吃同一颗内存流；落盘走同一份 JsonKeyValueStore（主 prefs 文件、同名键），
 * 冷启动由主壳读盘回填 [flow]。
 */
object DesktopThemeMode {

    /** 档名（也是盘上的值；对齐文案尺，三档全部 ≤4 字）。 */
    const val DARK = "深色"
    const val LIGHT = "浅色"
    const val SYSTEM = "跟随系统"

    private val _mode = MutableStateFlow(DARK)
    private var boundStore: com.venera.compose.data.platform.JsonKeyValueStore? = null

    /** 当前档（主壳冷启动回填后即为真值；设置行直接写它+落盘）。 */
    val mode: StateFlow<String> = _mode.asStateFlow()

    /** 主壳冷启动绑定并回填（盘上没值时保持深色——桌面出厂档）。 */
    fun bind(store: com.venera.compose.data.platform.JsonKeyValueStore, saved: String) {
        boundStore = store
        restore(saved)
    }

    /** 主壳冷启动回填（盘上没值时保持深色——桌面出厂档）。 */
    fun restore(saved: String) {
        if (saved == DARK || saved == LIGHT || saved == SYSTEM) _mode.value = saved
    }

    /** 设置行的写侧：改流（热切）+落盘（冷启动）。 */
    fun write(store: com.venera.compose.data.platform.JsonKeyValueStore, value: String) {
        boundStore = store
        _mode.value = value
        store.put("pref_desktop_theme_mode", value)
    }

    /** 侧栏等无需显式持 store 的调用点：热切并通过已绑定 store 落盘。 */
    fun writeBound(value: String) {
        if (value != DARK && value != LIGHT && value != SYSTEM) return
        _mode.value = value
        boundStore?.put("pref_desktop_theme_mode", value)
    }

    /** 主壳消费：浅色档判式（跟随系统时读 Windows 的应用明暗设置）。 */
    fun isDark(mode: String): Boolean = when (mode) {
        LIGHT -> false
        SYSTEM -> !isSystemLight()
        else -> true
    }

    /** Windows 应用明暗（注册表 AppsUseLightTheme）；读不到按深色算。 */
    private fun isSystemLight(): Boolean = runCatching {
        val p = ProcessBuilder(
            "reg", "query",
            "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
            "/v", "AppsUseLightTheme",
        ).start()
        val out = p.inputStream.readBytes().toString(Charsets.UTF_8)
        p.waitFor()
        out.contains("0x1")
    }.getOrDefault(false)
}

/**
 * 标签译文档位（档二 5b-1）：两档真生效（跟随系统=有译名并列原名 / 不显示=回站方原名）。
 *
 * 对岸那四档（SIMPLIFIED/TRADITIONAL 查库换列）桌面**今天摆不出**：随包词典表只有
 * `cn` 一列（`data/db/TagDictionaryStore.kt:14` 建表串可查），繁简共用一份——
 * 摆三档假能切、实际同值，就是"画不出来的形状"（假开关口径），所以只留两档真生效。
 * 单例热流同 [DesktopThemeMode] 形状；消费点在词典出口 [applyMap]。
 */
object DesktopTagTranslation {

    const val SYSTEM = "跟随系统"
    const val OFF = "不显示"

    private val _mode = MutableStateFlow(SYSTEM)
    val mode: StateFlow<String> = _mode.asStateFlow()

    fun restore(saved: String) {
        if (saved == SYSTEM || saved == OFF) _mode.value = saved
    }

    fun write(store: com.venera.compose.data.platform.JsonKeyValueStore, value: String) {
        _mode.value = value
        store.put("pref_desktop_tag_translation", value)
    }

    /** 档位出口：OFF ⇒ 空表（所有标签回站方原名），其余原样（"译名 (原名)" 并列档）。 */
    fun applyMap(
        translations: Map<String, String>,
        mode: String = _mode.value,
    ): Map<String, String> = if (mode == OFF) emptyMap() else translations
}
