package com.venera.compose.data.platform

/**
 * 漫画指标（评分 / 赞数）的**写侧口** —— 。
 *
 * ## 为什么要这一颗
 *
 * 读侧口子早就收进了 `UxPlatformServices.cachedComicMetrics`，但写侧一直留在
 * `ComicDetailViewModel` 里那句 `ComicMetricsCache(getApplication()).put(...)` ——
 * `data.prefs` + `getApplication()` 两族禁引把那颗 VM 整个钉在 Android 面上。
 * 共享内核（`ComicDetailCore`）只认这一枚接口：
 *  - Android 薄壳用 `ComicMetricsCache` 实现它（逐字同值，缓存语义全在原类）；
 *  - 桌面装配点用它的 `KeyValueStore` 实现（同一张 `PreferenceKeys.PREFS_COMIC_METRICS`
 *    键表，两端读写口径由编译期锁死）。
 *
 * 形状照 `Logger`：住在 `data/platform`（共享 srcDir），零 Android import；
 * 成员只有一枚，因为调用点只有一处（详情取回成功后 `cacheMetrics`）——
 * 补成员之前先问"有没有调用点"。
 */
interface ComicMetricsSink {

    /** 记一笔源报回的指标。字段各自可空：没报的那一栏**不许**覆盖旧值（与对岸 `put` 同语义）。 */
    fun put(sourceKey: String, comicId: String, rating: Double?, likesCount: Int?)
}
