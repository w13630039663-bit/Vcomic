package com.venera.desktop.reader

import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import kotlin.math.abs

/**
 * 新口径滤镜链（筛选 tab 与全局设置同源）：亮度/对比度/饱和度 0–4 倍率 + 色相旋转 + sepia 强度 + 黑白/负片开关。
 *
 * 依序相乘为单一矩阵，调用方 remember 一次算好，禁止每页重算。
 *
 * @param brightness 亮度倍率（0f..4f，1=原样）。
 * @param contrast 对比度倍率（0f..4f，1=原样）。
 * @param saturation 饱和度倍率（0f..4f，1=原样）。
 * @param hueDegrees 色相旋转角度（0..360）。
 * @param sepiaStrength sepia 色温强度（0..100，按强度与单位阵插值）。
 * @param grayscale 黑白（灰度）开关（饱和度 0 矩阵）。
 * @param negative 负片开关（RGB 取反、保持 alpha）。
 */
internal fun buildReaderFilterMatrix(
    brightness: Float,
    contrast: Float,
    saturation: Float,
    hueDegrees: Float = 0f,
    sepiaStrength: Float = 0f,
    grayscale: Boolean = false,
    negative: Boolean = false,
): ColorMatrix? {
    val isDefault = abs(brightness - 1f) < 0.005f &&
        abs(contrast - 1f) < 0.005f &&
        abs(saturation - 1f) < 0.005f &&
        hueDegrees % 360f < 0.005f &&
        sepiaStrength < 0.005f &&
        !grayscale &&
        !negative
    if (isDefault) return null

    val result = ColorMatrix()
    // 负片放最前（对原图取反），后续叠加在负片之上。
    if (negative) {
        result.timesAssign(negativeMatrix())
    }
    if (grayscale) {
        result.timesAssign(ColorMatrix().apply { setToSaturation(0f) })
    }
    if (abs(saturation - 1f) >= 0.005f) {
        result.timesAssign(ColorMatrix().apply { setToSaturation(saturation.coerceIn(0f, 4f)) })
    }
    if (abs(contrast - 1f) >= 0.005f || abs(brightness - 1f) >= 0.005f) {
        val c = contrast.coerceIn(0f, 4f)
        val b = (brightness.coerceIn(0f, 4f) - 1f) * 127.5f
        val t = (1f - c) * 127.5f + b
        result.timesAssign(
            ColorMatrix(
                floatArrayOf(
                    c, 0f, 0f, 0f, t,
                    0f, c, 0f, 0f, t,
                    0f, 0f, c, 0f, t,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
    }
    if (hueDegrees % 360f >= 0.005f) {
        result.timesAssign(hueRotateMatrix(hueDegrees))
    }
    if (sepiaStrength >= 0.005f) {
        result.timesAssign(sepiaInterpolated(sepiaStrength.coerceIn(0f, 100f) / 100f))
    }
    return result
}

/** 负片：RGB 通道取反、alpha 保持。 */
private fun negativeMatrix(): ColorMatrix = ColorMatrix(
    floatArrayOf(
        -1f, 0f, 0f, 0f, 255f,
        0f, -1f, 0f, 0f, 255f,
        0f, 0f, -1f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f,
    ),
)

/** 标准 20 元素 hueRotate 矩阵（SVG feColorMatrix hueRotate 同式）。 */
internal fun hueRotateMatrix(degrees: Float): ColorMatrix {
    val rad = Math.toRadians(degrees.toDouble())
    val cos = kotlin.math.cos(rad).toFloat()
    val sin = kotlin.math.sin(rad).toFloat()
    // lumR/lumG/lumB = ITU-R 709 亮度系数（0.213/0.715/0.072）。
    val lumR = 0.213f
    val lumG = 0.715f
    val lumB = 0.072f
    return ColorMatrix(
        floatArrayOf(
            lumR + cos * (1 - lumR) + sin * -lumR, lumG + cos * -lumG + sin * -lumG, lumB + cos * -lumB + sin * (1 - lumB), 0f, 0f,
            lumR + cos * -lumR + sin * 0.143f, lumG + cos * (1 - lumG) + sin * 0.140f, lumB + cos * -lumB + sin * -0.283f, 0f, 0f,
            lumR + cos * -lumR + sin * -(1 - lumR), lumG + cos * -lumG + sin * lumG, lumB + cos * (1 - lumB) + sin * lumB, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ),
    )
}

/** sepia 标准矩阵按强度插值：strength=0 退回单位阵，1 全 sepia。 */
private fun sepiaInterpolated(strength: Float): ColorMatrix {
    val s = strength.coerceIn(0f, 1f)
    fun mix(id: Float, sepia: Float) = id + (sepia - id) * s
    return ColorMatrix(
        floatArrayOf(
            mix(1f, 0.393f), mix(0f, 0.769f), mix(0f, 0.189f), 0f, 0f,
            mix(0f, 0.349f), mix(1f, 0.686f), mix(0f, 0.168f), 0f, 0f,
            mix(0f, 0.272f), mix(0f, 0.534f), mix(1f, 0.131f), 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ),
    )
}

/**
 * 将设置中的插值质量档位映射为 Compose [FilterQuality]。
 *
 * @param qualityIndex 质量档位：0=平滑双线性，1=高质量双三次，2=像素近邻。
 */
internal fun resolveFilterQuality(qualityIndex: Int): FilterQuality {
    return when (qualityIndex) {
        0 -> FilterQuality.Medium
        2 -> FilterQuality.None
        else -> FilterQuality.High
    }
}

/**
 * 双页智能分组：封面独占、宽图（`width > height`）独占一屏，并按需补空白占位。
 *
 * 占位用 `-1` 表示：渲染侧取 `pages.getOrNull(-1)` 得到 null，画成空页。
 *
 * @param pageCount 总页数。
 * @param coverSinglePage 封面是否独占一页。
 * @param widePageIndices 已识别为宽图的页码索引集合。
 * @param alignWithNextHorizontal 宽图前若只凑到半对，补一个空白页占位，
 *   让宽图总落在跨页的起始位；末尾同理，避免最后一页被单独甩在一边。
 * @param leadingBlankPage 在首页前插一个空白页，使第 1 页单独落右侧（日漫开本对齐）。
 */
internal fun buildDoublePageGroups(
    pageCount: Int,
    coverSinglePage: Boolean,
    widePageIndices: Set<Int>,
    alignWithNextHorizontal: Boolean = true,
    leadingBlankPage: Boolean = false,
): List<List<Int>> {
    if (pageCount <= 0) return emptyList()
    val groups = mutableListOf<List<Int>>()
    var i = 0
    if (leadingBlankPage) {
        // 首页前插空白：分组从"空白+第0页"起算，第 1 页落右侧位。
        groups.add(listOf(BLANK_PAGE, 0))
        i = 1
    } else if (coverSinglePage) {
        groups.add(listOf(0))
        i = 1
    }
    var pending = mutableListOf<Int>()
    fun flush() {
        if (pending.isNotEmpty()) {
            groups.add(pending.toList())
            pending = mutableListOf()
        }
    }
    while (i < pageCount) {
        if (i in widePageIndices) {
            if (alignWithNextHorizontal && pending.size == 1) pending.add(BLANK_PAGE)
            flush()
            groups.add(listOf(i))
            i += 1
        } else {
            pending.add(i)
            if (pending.size == 2) flush()
            i += 1
        }
    }
    if (alignWithNextHorizontal && pending.size == 1) pending.add(BLANK_PAGE)
    flush()
    return groups
}

/** 分组里的空白占位索引；渲染侧读不到这一页，画成空位。 */
internal const val BLANK_PAGE = -1

/**
 * 页格宽高比兜底值：首帧（真实尺寸未到手）按竖长页口径占位。
 * 判据尺钉这一行——改口径须同步 `DesktopMaskJudgementSingleSourceTest`。
 */
internal const val TRIPLE_RATIO_FALLBACK = 0.7f

/**
 * 计算双向预加载页码索引列表（优先向后预加载，再向前预加载）。
 *
 * @param currentPage 当前页索引（0 基）。
 * @param totalPages 总页数。
 * @param preloadAhead 向后预加载页数。
 * @param preloadBehind 向前预加载页数。
 */
internal fun computePreloadIndices(
    currentPage: Int,
    totalPages: Int,
    preloadAhead: Int,
    preloadBehind: Int = 1,
): List<Int> {
    if (totalPages <= 0) return emptyList()
    val result = mutableListOf<Int>()
    for (offset in 1..preloadAhead.coerceAtLeast(0)) {
        val idx = currentPage + offset
        if (idx in 0 until totalPages) result.add(idx)
    }
    for (offset in 1..preloadBehind.coerceAtLeast(0)) {
        val idx = currentPage - offset
        if (idx in 0 until totalPages) result.add(idx)
    }
    return result
}
