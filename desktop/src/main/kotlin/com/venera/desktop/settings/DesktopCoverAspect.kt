package com.venera.desktop.settings

import com.venera.compose.data.platform.JsonKeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 画廊与作品列表的封面画幅比例档位。
 */
enum class DesktopCoverAspect(val label: String, val ratio: Float?) {
    ORIGINAL("原始比例", null),
    PORTRAIT("3:4 竖图", 0.75f),
    SQUARE("1:1 方图", 1.0f),
    WIDE("16:9 横图", 16f / 9f);

    companion object {
        private val _tier = MutableStateFlow(ORIGINAL)
        val tier: StateFlow<DesktopCoverAspect> = _tier.asStateFlow()

        /**
         * 从持久化存储还原画幅档位。
         * @param saved 存储的枚举名或显示名称
         */
        fun restore(saved: String?) {
            val matched = entries.firstOrNull { it.name == saved || it.label == saved } ?: ORIGINAL
            _tier.value = matched
        }

        /**
         * 写入画幅档位。
         * @param store 目标键值存储
         * @param aspect 目标画幅枚举
         */
        fun write(store: JsonKeyValueStore, aspect: DesktopCoverAspect) {
            _tier.value = aspect
            store.put("pref_desktop_cover_aspect", aspect.name)
        }
    }
}
