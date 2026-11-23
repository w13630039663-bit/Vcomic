package com.venera.desktop.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 侧栏玻璃模糊独立开关。
 *
 * 与底板档位正交：底板选实底时系统材质仍可留着供侧栏取模糊，
 * 因此本开关不能由 [DesktopBackdropMode] 推导。
 */
object DesktopRailBlur {

    private const val PREF_KEY = "pref_desktop_rail_blur"

    private val _visible = MutableStateFlow(true)

    /** 当前侧栏是否走玻璃模糊。 */
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    /**
     * 冷启动回填侧栏模糊状态。
     *
     * @param saved 持久化存储中的布尔值，未存过时默认开启
     */
    fun restore(saved: Boolean) {
        _visible.value = saved
    }

    /**
     * 切换并持久化侧栏模糊状态。
     *
     * @param store 主偏好键值存储实例
     * @param value 目标状态
     */
    fun write(store: com.venera.compose.data.platform.JsonKeyValueStore, value: Boolean) {
        _visible.value = value
        store.put(PREF_KEY, value)
    }
}
