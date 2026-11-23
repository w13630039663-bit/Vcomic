package com.venera.desktop.search

import com.venera.compose.data.platform.JsonKeyValueStore
import com.venera.compose.data.platform.PathProvider
import com.venera.desktop.settings.DesktopI18n

/** 漫画搜索结果的三档视图。 [storageValue] 是落盘值，改它会让旧偏好读不回来。 */
internal enum class DesktopSearchViewMode(val storageValue: String) {
    Single("1"),
    Double("2"),
    Grid("g"),
    ;

    val label: String
        get() = when (this) {
            Single -> DesktopI18n.tr("单列", "Single")
            Double -> DesktopI18n.tr("双列", "Double")
            Grid -> DesktopI18n.tr("网格", "Grid")
        }

    companion object {
        internal const val KEY = "pref_desktop_search_view"
        internal const val STORE = "venera_desktop_window"

        /** 未知值退回单列，不让脏档把分段器顶到空选态。 */
        internal fun of(value: String?): DesktopSearchViewMode =
            entries.firstOrNull { it.storageValue == value } ?: Single
    }
}

/**
 * 读「默认视图」档；[paths] 为空（数据根未定）时退回单列，不落盘。
 *
 * @param paths 桌面数据根，决定偏好落在哪个用户目录下。
 */
internal fun readSearchViewMode(paths: PathProvider?): DesktopSearchViewMode =
    if (paths == null) DesktopSearchViewMode.Single
    else DesktopSearchViewMode.of(
        JsonKeyValueStore(DesktopSearchViewMode.STORE, paths)
            .getString(DesktopSearchViewMode.KEY, DesktopSearchViewMode.Single.storageValue),
    )

/**
 * 写「默认视图」档；[paths] 为空时只回传档位，不落盘。
 *
 * @param paths 桌面数据根，与读侧同一处 store。
 * @param mode 用户新选的档位。
 */
internal fun writeSearchViewMode(paths: PathProvider?, mode: DesktopSearchViewMode) {
    if (paths == null) return
    JsonKeyValueStore(DesktopSearchViewMode.STORE, paths).put(DesktopSearchViewMode.KEY, mode.storageValue)
}
