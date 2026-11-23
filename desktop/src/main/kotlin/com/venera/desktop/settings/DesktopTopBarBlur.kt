package com.venera.desktop.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 窗口标题栏玻璃模糊独立开关。
 *
 * 与底板档位、侧栏开关都正交：任一处要模糊，系统材质就得留着。
 */
object DesktopTopBarBlur {

    private const val PREF_KEY = "pref_desktop_topbar_blur"

    private val _visible = MutableStateFlow(true)

    /** 当前标题栏是否走玻璃模糊。 */
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    /**
     * 冷启动回填顶栏模糊状态。
     *
     * @param saved 持久化存储中的布尔值，未存过时默认开启
     */
    fun restore(saved: Boolean) {
        _visible.value = saved
    }

    /**
     * 切换并持久化顶栏模糊状态。
     *
     * @param store 主偏好键值存储实例
     * @param value 目标状态
     */
    fun write(store: com.venera.compose.data.platform.JsonKeyValueStore, value: Boolean) {
        _visible.value = value
        store.put(PREF_KEY, value)
    }
}
