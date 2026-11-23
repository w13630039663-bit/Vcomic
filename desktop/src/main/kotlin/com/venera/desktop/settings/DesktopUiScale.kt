package com.venera.desktop.settings

import com.venera.compose.data.platform.JsonKeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 界面缩放档位。通过覆写根级 Density 实现整体尺寸缩放。
 */
object DesktopUiScale {

    const val SCALE_80 = "80%"
    const val SCALE_100 = "100%"
    const val SCALE_125 = "125%"
    const val SCALE_150 = "150%"

    val TIERS = listOf(SCALE_80, SCALE_100, SCALE_125, SCALE_150)
    private val FACTORS = listOf(0.8f, 1.0f, 1.25f, 1.5f)

    private val _tier = MutableStateFlow(SCALE_100)
    val tier: StateFlow<String> = _tier.asStateFlow()

    private val _scale = MutableStateFlow(1.0f)
    val scale: StateFlow<Float> = _scale.asStateFlow()

    /**
     * 从持久化存储还原缩放档位。
     * @param saved 存储的档位字符串，未命中时保持默认 100%。
     */
    fun restore(saved: String?) {
        val index = TIERS.indexOf(saved)
        if (index >= 0) {
            _tier.value = TIERS[index]
            _scale.value = FACTORS[index]
        }
    }

    /**
     * 写入缩放档位并触发内存流更新。
     * @param store 目标键值存储
     * @param value 选中的档位显示名称
     */
    fun write(store: JsonKeyValueStore, value: String) {
        val index = TIERS.indexOf(value).coerceAtLeast(0)
        _tier.value = TIERS[index]
        _scale.value = FACTORS[index]
        store.put("pref_desktop_ui_scale", TIERS[index])
    }
}
