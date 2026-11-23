package com.venera.desktop.search

/**
 * 右侧速览的壳层状态。合成一枚 holder 而不是四枚独立 remember ——
 * 壳顶层 Composable lambda 的捕获表已接近 JVM 方法描述符 253 槽上限，
 * 多一枚 MutableState 就会在起窗时抛 ClassFormatError（编译不报）。
 */
internal data class DesktopPeekUiState(
    val open: Boolean = false,
    val comicId: String? = null,
    val title: String = "",
    val cover: String = "",
    /** 卡片归属源键；null 时回退单源屏的 currentSearchKey，聚合模式下防止跨源串线。 */
    val sourceKey: String? = null,
    /** 卡片归属源显示名；null 时回退单源屏的 sourceLabel。 */
    val sourceLabel: String? = null,
    /**
     * 已取到详情的作品 id → 更新日期。搜索结果不带时间，日期只在详情页；
     * 点开速览后按 id 回灌，未点开的卡不摆徽标，避免为整页结果逐本取详情。
     */
    val updateTimes: Map<String, String> = emptyMap(),
)
