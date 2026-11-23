package com.venera.desktop.search

/**
 * 搜索屏源选择器**列哪些源**（跟开启账走，不再按分级挑）。
 *
 * ## 从前那一版与为什么作废
 *
 * 那一版把选择器收成"预设表逐条写着 `safe` 且启用"的白名单，理由是
 * 桌面默认遮罩档 OFF 时摆 nsfw 站 = 递一枚"成人封面裸铺上屏"的把手。改判：
 * 「搜索页面的源不是固定 3 个的，是按照源管理开启的，源管理开了什么就有什么可以选择的」——
 * 选源是**功能面**的事，分级是**遮罩面**的事；把两者绑在一颗闸上，用户开了 jm 却搜不了 jm，
 * 那是拿遮罩的账去锁功能的门。分级那条链（遮罩档 OFF/BLUR/HIDE、源级预设、条目判据）一字未动，
 * 它照旧管"搜出来的封面糊不糊"，而不再管"这颗源进不进选择器"。
 *
 * ## 判据（只剩一条）
 *
 * `rows.filter { it.enabled }` —— 用户开着的都列，关着的都不列（与探索/聚合那几路同一份账：
 * 停用是真停用，见 `DesktopSourceSelection.resolve`）。顺序 = 清单原序，选择器不重排。
 */
internal fun desktopSearchSelectableSources(
    rows: List<com.venera.desktop.sourcemanage.DesktopSourceRowUi>,
): List<String> = rows.filter { it.enabled }.map { it.key }
