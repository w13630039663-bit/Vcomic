package com.venera.compose.components

/**
 * 「读到某一页」的定位请求（机械摘自 `feature/Navigation.kt`）。
 *
 * 只有插图收藏这条来源：`favorite_images` 表存的是**章节标题**而不是章节 id，
 * 而章节 id 必须等源详情解析出目录才拿得到。所以这里只能带标题 + 页码过去，
 * 由详情页在目录就绪时按标题找回章节并打开阅读器 —— 见 ComicDetailScreen。
 *
 * 搬到共享层的理由：`AndroidComicDetailScreen` 的形参吃它，而那颗屏现在两端共编；
 * 原处那条 `constructor(item: FavoriteImageItem)` 副构造吃 Android 面的
 * `favoriteimages.FavoriteImageItem`（Bitmap 落盘那颗管理器），摘掉副构造、
 * 由调用点显式递四字段 —— 值逐字相同，钉住的只有构造形状。
 */
data class ReadTarget(
    val comicId: String,
    val sourceName: String,
    val chapterTitle: String,
    val pageIndex: Int,
)
