package com.venera.desktop.settings

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.venera.compose.data.platform.JsonKeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 漫画卡片全局布局模式：自适应（随窗口宽度自动排布）或自定义 x 行 × y 列。
 *
 * 覆盖所有漫画卡片页面（本地收藏、网络收藏、追漫、搜索、探索、本地漫画库、阅读历史）
 * 的网格模式与双列模式。
 */
enum class DesktopComicCardLayoutMode(val labelCn: String, val labelEn: String) {
    AUTO("自适应", "Auto"),
    CUSTOM("自定义行列", "Custom Grid"),
}

/**
 * 漫画卡片布局配置。
 *
 * @param mode 自适应或自定义行列。
 * @param rows 自定义行数（1..6）：决定网格卡片封面宽高比，行数越多卡越紧凑。
 * @param cols 自定义列数（1..8）：决定网格与双列模式下每行排列几张卡片。
 */
data class DesktopComicCardLayoutConfig(
    val mode: DesktopComicCardLayoutMode = DesktopComicCardLayoutMode.AUTO,
    val rows: Int = DEFAULT_ROWS,
    val cols: Int = DEFAULT_COLS,
) {
    val isCustom: Boolean get() = mode == DesktopComicCardLayoutMode.CUSTOM

    /**
     * 计算网格模式生效列数。
     *
     * @param availableWidth 当前网格容器可用宽度。
     * @param minCellWidth 自适应模式下单卡最小宽度阈值。
     * @param minAutoCols 自适应模式下最小列数。
     * @param maxAutoCols 自适应模式下最大列数。
     */
    fun resolveGridColumns(
        availableWidth: Dp,
        minCellWidth: Dp = 156.dp,
        minAutoCols: Int = 3,
        maxAutoCols: Int = 6,
    ): Int = if (isCustom) {
        cols.coerceIn(MIN_COLS, MAX_COLS)
    } else {
        (availableWidth / minCellWidth).toInt().coerceIn(minAutoCols, maxAutoCols)
    }

    /**
     * 计算双列模式生效列数：自适应时为 [defaultCols]（默认 2），自定义时走 [cols]。
     */
    fun resolveDoubleColumns(defaultCols: Int = 2): Int = if (isCustom) {
        cols.coerceIn(MIN_COLS, MAX_COLS)
    } else {
        defaultCols
    }

    /**
     * 网格封面宽高比：自适应走 [defaultRatio]，自定义时按 [rows] 映射，保证一屏可容纳对应行数。
     */
    fun resolveCoverAspect(defaultRatio: Float = 0.72f): Float = if (isCustom) {
        rowsToAspect(rows)
    } else {
        defaultRatio
    }

    companion object {
        const val MIN_ROWS = 1
        const val MAX_ROWS = 6
        const val MIN_COLS = 1
        const val MAX_COLS = 8
        const val DEFAULT_ROWS = 3
        const val DEFAULT_COLS = 5

        fun rowsToAspect(rows: Int): Float = when (rows.coerceIn(MIN_ROWS, MAX_ROWS)) {
            1 -> 0.65f
            2 -> 0.75f
            3 -> 1.0f
            4 -> 1.25f
            5 -> 1.45f
            else -> 1.65f
        }
    }
}

/** 全局漫画卡片布局配置单例（与 [DesktopGridDensity] 同口径：冷启动读盘，设置页即改即生效）。 */
object DesktopComicCardLayout {
    private const val KEY_MODE = "pref_comic_card_layout_mode"
    private const val KEY_ROWS = "pref_comic_card_layout_rows"
    private const val KEY_COLS = "pref_comic_card_layout_cols"

    private val _config = MutableStateFlow(DesktopComicCardLayoutConfig())
    val config: StateFlow<DesktopComicCardLayoutConfig> = _config.asStateFlow()

    fun restore(store: JsonKeyValueStore) {
        val rawMode = store.getString(KEY_MODE, null)
        val mode = DesktopComicCardLayoutMode.entries.firstOrNull { it.name == rawMode }
            ?: DesktopComicCardLayoutMode.AUTO
        val rows = store.getInt(KEY_ROWS, DesktopComicCardLayoutConfig.DEFAULT_ROWS)
            .coerceIn(DesktopComicCardLayoutConfig.MIN_ROWS, DesktopComicCardLayoutConfig.MAX_ROWS)
        val cols = store.getInt(KEY_COLS, DesktopComicCardLayoutConfig.DEFAULT_COLS)
            .coerceIn(DesktopComicCardLayoutConfig.MIN_COLS, DesktopComicCardLayoutConfig.MAX_COLS)
        _config.value = DesktopComicCardLayoutConfig(mode = mode, rows = rows, cols = cols)
    }

    fun write(store: JsonKeyValueStore, next: DesktopComicCardLayoutConfig) {
        val normalized = DesktopComicCardLayoutConfig(
            mode = next.mode,
            rows = next.rows.coerceIn(DesktopComicCardLayoutConfig.MIN_ROWS, DesktopComicCardLayoutConfig.MAX_ROWS),
            cols = next.cols.coerceIn(DesktopComicCardLayoutConfig.MIN_COLS, DesktopComicCardLayoutConfig.MAX_COLS),
        )
        _config.value = normalized
        store.put(KEY_MODE, normalized.mode.name)
        store.put(KEY_ROWS, normalized.rows)
        store.put(KEY_COLS, normalized.cols)
    }
}
