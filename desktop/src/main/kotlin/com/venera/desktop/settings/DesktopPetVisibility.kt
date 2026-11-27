package com.venera.desktop.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 侧栏桌宠（小鱼）显示开关。
 */
object DesktopPetVisibility {

    private const val PREF_KEY = "pref_desktop_pet_visible"

    private val _visible = MutableStateFlow(false)

    /** 当前是否显示侧栏桌宠。 */
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    /**
     * 冷启动回填桌宠显示状态。
     *
     * @param saved 持久化存储中的布尔值，未存过时默认关闭
     */
    fun restore(saved: Boolean) {
        _visible.value = saved
    }

    /**
     * 切换并持久化桌宠显示状态。
     *
     * @param store 主偏好键值存储实例
     * @param value 目标显示状态
     */
    fun write(store: com.venera.compose.data.platform.JsonKeyValueStore, value: Boolean) {
        _visible.value = value
        store.put(PREF_KEY, value)
    }
}
