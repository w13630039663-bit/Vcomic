package com.venera.compose.gallery.domain

/**
 * 主墙布局两档（设置里自选，桌面双路径分派；安卓侧暂只用 [Staggered]）。
 *
 * - [Staggered]：共享页那套瀑布流（原比例零裁切，列数走既有 columnMode）；
 * - [Grid]：等宽网格 · 大间隔（1:1 方图裁切、横图跨双格、列数走密度三档）。
 */
enum class GalleryWallLayout(val label: String) {
    Staggered("瀑布流"),
    Grid("等宽网格"),
}
