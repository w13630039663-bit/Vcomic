package com.venera.desktop.gallery.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.venera.compose.gallery.domain.GalleryColumnMode
import com.venera.compose.gallery.domain.GalleryWallLayout

/**
 * 卡片大小三档：在密度之上再对单卡做一次缩放。实现是**缩放列宽阈值**——
 * 同一扇窗，偏小档按 1.3 倍宽去算列数（列多卡小），偏大档按 0.76 倍（列少卡大）。
 */
internal enum class DesktopCardSize(val widthScale: Float) {
    Small(1.3f),
    Standard(1.0f),
    Large(0.76f),
}

/**
 * 全部作品墙的**统一规格**。
 *
 * 从前「作品墙布局」与「密度」只作用于图库首页主墙，搜索 / 收藏 / 日榜 / pixiv 账号各摆
 * 各的卡、各算各的列 —— 同一档布局在不同子页不是一个规格。这一颗把两档收成单一真源，
 * 挂在装配点的 [androidx.compose.runtime.CompositionLocalProvider] 上，所有作品墙同读：
 *
 * - [layout]：瀑布流（原比例零裁切）/ 等宽网格（方图 + 宽图跨双格）；
 * - [density]：紧凑 / 标准 / 宽松 —— 真驱动列数（[columnBias] / [columnsFor]）与列距（[wallGap]），
 *   不再是「定义了却全仓零消费」的死档。
 *
 * 默认值 = **等宽网格 + 宽松**（定案）。
 */
internal data class DesktopWallSpec(
    val layout: GalleryWallLayout = GalleryWallLayout.Grid,
    val density: DesktopWallDensity = DesktopWallDensity.Loose,
    /** 卡片大小档：只对 AUTO 列数生效（显式列数档 = 用户显式覆盖，尊重之不缩放）。 */
    val cardSize: DesktopCardSize = DesktopCardSize.Standard,
    /** 封面画幅比例档。 */
    val coverAspect: com.venera.desktop.settings.DesktopCoverAspect = com.venera.desktop.settings.DesktopCoverAspect.ORIGINAL,
    /** 网格密度档：自适应或固定「x 行 × x 列」（全部作品墙同读）。 */
    val gridDensity: com.venera.desktop.settings.DesktopGridDensity = com.venera.desktop.settings.DesktopGridDensity.AUTO,
    /**
     * 生效行数（非空时封面裁切比例由它定，见 [com.venera.desktop.settings.DesktopGridDensity.rowsToRatio]）：
     * 固定档 = 枚举行数；自适应档 = 装配点按窗宽算（2-3-4 行随窗宽放大）。
     */
    val rows: Int? = null,
) {
    /** 密度对响应式列数的偏置：紧凑列多卡小（+1），宽松列少卡大（-1）。 */
    val columnBias: Int
        get() = when (density) {
            DesktopWallDensity.Compact -> 1
            DesktopWallDensity.Standard -> 0
            DesktopWallDensity.Loose -> -1
        }

    /** 密度对列距的取值：紧凑 10 / 标准 16 / 宽松 24dp。 */
    val wallGap: Dp
        get() = when (density) {
            DesktopWallDensity.Compact -> 10.dp
            DesktopWallDensity.Standard -> 16.dp
            DesktopWallDensity.Loose -> DesktopGalleryMetrics.wallGridGapLoose
        }
}

/**
 * 作品墙列数：AUTO 档吃响应式宽尺再叠 [DesktopWallSpec.columnBias]（密度生效）；
 * TWO / THREE 是用户显式覆盖，不受密度偏置 —— 尊重「窗口再宽也不许跟着动」那一条。
 *
 * [widthDp] 传**物理像素宽**，档位阈值见
 * [com.venera.desktop.gallery.ui.DesktopGalleryMetrics.immersiveWallColumnCount]；
 * 上限 10 给 4K 窗留余量，下限 2 只为兜住显式覆盖档。
 */
internal fun DesktopWallSpec.columnsFor(widthDp: Float, mode: GalleryColumnMode): Int {
    // 固定「x 行 × x 列」档：列数钉死，密度偏置与卡片大小档都不参与。
    // 墙头的显式列覆盖（TWO/THREE）仍优先 —— 用户当下那一下的手选最重。
    if (mode == GalleryColumnMode.AUTO) gridDensity.cols?.let { return it }
    // 卡片大小档：把实测宽按档缩放后再过阈值表（偏大卡=阈值表以为窗更窄=列更少）。
    val base = desktopImmersiveWallColumnCount(widthDp * cardSize.widthScale, mode)
    return if (mode == GalleryColumnMode.AUTO) {
        (base + columnBias).coerceIn(2, 10)
    } else {
        base
    }
}

internal val LocalDesktopWallSpec = compositionLocalOf { DesktopWallSpec() }
