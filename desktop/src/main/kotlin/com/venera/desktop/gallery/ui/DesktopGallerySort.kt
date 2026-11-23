package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GallerySite

/**
 * 画廊首页主墙的排序档（`desktop-gallery-booru-pixiv-2026-10-29.html`
 * 的 chrome 那一排「推荐 / 最新 / 最热」）。
 *
 * 三档都是**真取数腿**，不是换皮：
 * - 推荐 = 每日热门那台合并机器（[com.venera.compose.gallery.domain.GalleryDailyFeed] 的三站池子
 * 混排首屏），与预览行同一腿——把"为你推荐"放在主墙位，而桌面今天唯一说得通
 *   "推荐"的出处就是这颗合并池（没有别的推荐算法，不许把默认序念成推荐）；
 * - 最新 = 各站默认序（空串查询，那条路，逐字不变）；
 * - 最热 = 各站高分池查询词（后缀逐字取自共享判据 [com.venera.compose.gallery.domain.GalleryRankings]
 *   里那三枚实测常量：`order:score` / `sort:score:desc`——两站写法**不能互相照抄**，
 *   `sort:score:desc` 在 yande.re 没验过而 `order:score` 在 Gelbooru 是 0 结果）。
 *
 * PIXIV 域不摆这一排（档名在那一域是**榜单模式**，长在 dashboard 那一格里，
 * 与 chrome 的排序档不是一个轴——两排并摆就是两套切换器）。
 */
internal enum class DesktopGallerySort(val label: String) {

    RECOMMENDED("推荐"),
    LATEST("最新"),
    HOTTEST("最热"),
    ;

    companion object {
        val OPTIONS: List<DesktopGallerySort> = entries.toList()
    }
}

/**
 * 这一档在每一站**发出去的查询词**（`desktopWallFirstPage(queries=…)` 的按站点名表）。
 *
 * - 推荐 = null：那一档不走 `searchPosts` 腿（走 [desktopWallRecommendedFirstPage] 那颗日榜
 *   合并件，池子有限、取完即到底）；
 * - 最新 = null：各站默认序（空串腿，那条路，逐字不变 = 今天首页那面墙）；
 * - 最热 = 逐站点名的高分后缀。
 */
internal fun desktopWallSortQueries(sort: DesktopGallerySort): Map<GallerySite, String>? = when (sort) {
    DesktopGallerySort.RECOMMENDED, DesktopGallerySort.LATEST -> null
    DesktopGallerySort.HOTTEST -> mapOf(
        GallerySite.YANDERE to "order:score",
        GallerySite.SAFEBOORU to "order:score",
        GallerySite.GELBOORU to "sort:score:desc",
        GallerySite.KONACHAN to "order:score",
        GallerySite.KONACHAN_R18 to "order:score",
        GallerySite.TBIB to "sort:score:desc",
    )
}
