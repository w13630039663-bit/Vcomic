package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.domain.GalleryPreload
import com.venera.compose.gallery.domain.GalleryPreloadMode
import com.venera.compose.gallery.domain.GalleryPreviewQuality

/**
 * 预取计划（重建）：`urls` = 这一轮真要发的地址；`wanted` = 档位窗口里的张数
 * （把"翻到底了"与"被挡住"分开的那一枚）；`blocked` = 被分级挡掉的；`noUrl` = 站方没给地址的。
 * 四枚合在一起，[desktopPreloadZeroCause] 的四种缺席才分得开。
 */
internal data class DesktopPreloadPlan(
    val urls: List<String>,
    val wanted: Int,
    val blocked: Int = 0,
    val noUrl: Int = 0,
)

/**
 * 预取计划构建器（重建）：候选 = **不含当前**、按近到远、同距离先向前、夹在墙范围内
 * （窗口大小吃共享 [GalleryPreload.beyondPages]）；被遮罩、站方没给那档地址的不进计划，
 * 同一地址只取一遍 —— 三条都有 `DesktopGalleryPreloadTest` 钉着。地址档吃
 * [desktopDetailTierUrl]（与浮层同一个缓存键，换档不换键就会点下去照样重取）。
 */
internal fun desktopPreloadPlan(
    mode: GalleryPreloadMode,
    index: Int,
    cards: List<DesktopWallCard?>,
    tier: DesktopDetailTier,
): DesktopPreloadPlan {
    val candidates = when (mode) {
        // 「不提前取」：一张都不排（连候选都不建 —— 那一档是摆设就摆得明明白白）。
        GalleryPreloadMode.OFF -> emptyList()
        // 「取下一张」：**只有**当前之后那一张（DesktopGalleryPreloadTest ② 钉死，不许顺手带上前面）。
        GalleryPreloadMode.NEXT -> listOfNotNull(cards.getOrNull(index + 1))
        // 「前后各两张」：按近到远、同距离先向前（+1,-1,+2,-2），夹在墙范围内。
        GalleryPreloadMode.BOTH_TWO -> buildList {
            for (d in 1..2) {
                cards.getOrNull(index + d)?.let { add(it) }
                cards.getOrNull(index - d)?.let { add(it) }
            }
        }
    }
    if (mode == GalleryPreloadMode.OFF) {
        return DesktopPreloadPlan(urls = emptyList(), wanted = candidates.size)
    }
    var blocked = 0
    var noUrl = 0
    val urls = mutableListOf<String>()
    candidates.forEach { card ->
        when {
            card == null -> noUrl++
            card.masked -> blocked++
            else -> {
                val url = desktopDetailTierUrl(card.post, tier)
                if (url.isBlank()) {
                    noUrl++
                } else if (url !in urls) {
                    urls += url
                }
            }
        }
    }
    return DesktopPreloadPlan(urls = urls, wanted = candidates.size, blocked = blocked, noUrl = noUrl)
}

/**
 * 发那一行的读数：**这一轮排了几张、吃的是哪一档**。
 *
 * 那枚开关屏上能改、消费点也接了，可 stdout 上关于它的字一直是零 —— 而这一族偏偏最容易"说了而没做"：
 * `enqueue` 不吃组合那层的取消（批 49 那条），少一格收手就是几十张没人看的图。**没有读数，这类缺陷
 * 只能等它变成流量账单。** 一次一行是刻意的：`LaunchedEffect` 每换页/换档/换模式重开一次就念一行，
 * 刷屏与静默一样坏。
 *
 * 形参里**没有地址**：站方那一路常带 `api_key`/`user_id`，做成结构上带不出，比"记得脱敏"硬
 * （与批 54 那颗 `desktopCopyImageReadout` 同一条纪律）。
 *
 * `排=0` 那一句要跟着半句实话：OFF 那一档、与"开了但前后几张都被挡着"，屏上都是"什么都没发生"，
 * 读数不许把这两种压成同一句哑巴（「没取到 / 没有 / 没接」是三句话那条）。
 */
internal fun desktopPreloadReading(
    mode: GalleryPreloadMode,
    plan: DesktopPreloadPlan,
    tier: DesktopDetailTier,
): String =
    "D_预取 档=$mode 排=${plan.urls.size} 取=$tier" +
        if (plan.urls.isEmpty()) "（这一轮一笔都不发：${desktopPreloadZeroCause(mode, plan)}）" else ""

/**
 * `排=0` 那一句的**因**。四种缺席在屏上同形，一句只许认一种：
 * 档设成了不预取 / 前后没有那几张了（翻到底、或墙本来就是空的）/ 前后那几张被分级挡住 / 站方没给可取的地址。
 *
 * 次序是判式的一部分：**先认档，再认"计划本身是空的"** —— 不预取那一档同样算出 `wanted=0`，
 * 反过来念就会把"人关掉了"报成"翻到底了"（判据 ② 钉这一条）。
 *
 * ⚠️ 后两种可以同时成立，那时两枚计数都念（只报第一枚 = 把第二种缺席藏起来，判据 ⑧）。
 * 走到这里时 `urls` 必空 ⇒ 窗口里那几张必被这两道 filter 之一吃掉，`挡 + 无址 = 计划` 恒大于 0，不留兜底句。
 */
internal fun desktopPreloadZeroCause(mode: GalleryPreloadMode, plan: DesktopPreloadPlan): String {
    if (mode == GalleryPreloadMode.OFF) return "档设成了不预取"
    if (plan.wanted == 0) return "前后没有那几张了"
    return buildString {
        if (plan.blocked > 0) append("挡=${plan.blocked}")
        if (plan.blocked > 0 && plan.noUrl > 0) append(" ")
        if (plan.noUrl > 0) append("无址=${plan.noUrl}")
    }
}

/**
 * 收手那一行的读数：这一批不再要了，`dispose` 掉几笔。
 *
 * ⚠️ 调用点必须排在 `dispose()` **之后** —— 先念"收手=3"再停手，就是拿请求当结果（批 49 钉过发与收的
 * 顺序，这一条是那枚钉的另一半：顺序不许只钉一半）。
 */
internal fun desktopPreloadStopReading(disposed: Int): String =
    "D_预取 收手=$disposed（这一批不再要了）"
