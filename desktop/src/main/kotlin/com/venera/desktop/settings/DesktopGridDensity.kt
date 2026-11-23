package com.venera.desktop.settings

import com.venera.compose.data.platform.JsonKeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 全部作品墙的网格密度档：自适应（列数随窗宽 3-4-5-6、行数随窗宽 2-3-4）
 * 或固定「x 行 × x 列」组合，图库 / 搜索 / pixiv 全部卡片统一同读。
 *
 * 行数的落点是**封面裁切比例**——列数定下卡宽就定，行数只能靠比例调卡高：
 * 2 行 = 3:4 竖、3 行 = 1:1 方、4 行 = 4:3 扁（[rowsToRatio]）。
 */
enum class DesktopGridDensity(
    val storageValue: String,
    val label: String,
    /** 固定档的行数；自适应档为 null（行数随窗宽）。 */
    val rows: Int?,
    /** 固定档的列数；自适应档为 null（列数走响应式阈值表）。 */
    val cols: Int?,
) {
    AUTO("auto", "自适应", null, null),
    R2C3("2x3", "2 行 × 3 列", 2, 3),
    R2C4("2x4", "2 行 × 4 列", 2, 4),
    R3C5("3x5", "3 行 × 5 列", 3, 5),
    R3C6("3x6", "3 行 × 6 列", 3, 6),
    R4C6("4x6", "4 行 × 6 列", 4, 6),
    ;

    companion object {
        private const val KEY = "pref_desktop_grid_density"
        private val _tier = MutableStateFlow(AUTO)
        val tier: StateFlow<DesktopGridDensity> = _tier.asStateFlow()

        /** 行数 → 封面宽高比（宽/高）：2=3:4 竖、3=1:1 方、4=4:3 扁。 */
        fun rowsToRatio(rows: Int): Float = when (rows) {
            2 -> 0.75f
            3 -> 1.0f
            else -> 4f / 3f
        }

        /**
         * 从持久化存储还原密度档。
         * @param saved 存储的枚举名或显示名
         */
        fun restore(saved: String?) {
            _tier.value = entries.firstOrNull { it.name == saved || it.label == saved } ?: AUTO
        }

        /**
         * 写入密度档。
         * @param store 目标键值存储
         * @param density 目标档位
         */
        fun write(store: JsonKeyValueStore, density: DesktopGridDensity) {
            _tier.value = density
            store.put(KEY, density.name)
        }
    }
}
