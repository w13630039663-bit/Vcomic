package com.venera.desktop.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 漫画单格区域定义（归一化坐标 0f..1f）。
 *
 * @property left 左边界比例。
 * @property top 上边界比例。
 * @property right 右边界比例。
 * @property bottom 下边界比例。
 */
internal data class ComicPanelRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = (right - left).coerceAtLeast(0.01f)
    val height: Float get() = (bottom - top).coerceAtLeast(0.01f)
    val isFullPage: Boolean get() = left <= 0.02f && top <= 0.02f && right >= 0.98f && bottom >= 0.98f
}

/**
 * 引导阅读分格计算引擎。
 */
internal object DesktopGuidedViewEngine {

    /**
     * 检测解码边长。请求侧必须用同一个值：解码尺寸大于它时这里会先降采样，
     * 细沟壑在降采样中会消失，分格会整片粘连。
     */
    const val DETECT_SIDE = 640

    private const val MIN_GUTTER_PX = 2
    private const val GUTTER_TOLERANCE = 22
    private const val GUTTER_ROW_FRACTION = 0.94f
    private const val MAX_SPLIT_DEPTH = 7
    private const val MIN_SPLIT_FRACTION = 0.06f
    private const val MIN_PANEL_AREA_FRACTION = 0.012f
    private const val MIN_PANEL_SIDE_FRACTION = 0.06f
    private const val MERGE_MAX_GAP = 0.02f

    /**
     * 一刀切下去每条带至少要占父区域的这个比例，否则不认这一刀。
     *
     * 画格内部的大片留白常能凑出"整行留白"，照切就把一格切成几条细缝 ——
     * 宁可少切一刀让这一格取景偏大，也不要切碎。
     */
    private const val MIN_SPLIT_SHARE = 0.22f

    /**
     * 基于本地图像留白沟壑投影（XY-Cut）检测漫画分格，并按阅读方向排序。
     *
     * @param image 原始页面位图。
     * @param rightToLeft 是否为日漫右至左阅读顺序。
     */
    fun detectPanels(image: BufferedImage, rightToLeft: Boolean): List<ComicPanelRect> {
        val srcW = image.width
        val srcH = image.height
        if (srcW < 32 || srcH < 32) return listOf(ComicPanelRect(0f, 0f, 1f, 1f))

        val scale = min(1f, DETECT_SIDE.toFloat() / max(srcW, srcH))
        val w = (srcW * scale).roundToInt().coerceAtLeast(32)
        val h = (srcH * scale).roundToInt().coerceAtLeast(32)

        val luma = IntArray(w * h)
        for (y in 0 until h) {
            val sy = (y / scale).toInt().coerceIn(0, srcH - 1)
            for (x in 0 until w) {
                val sx = (x / scale).toInt().coerceIn(0, srcW - 1)
                val rgb = image.getRGB(sx, sy)
                val r = (rgb ushr 16) and 0xFF
                val g = (rgb ushr 8) and 0xFF
                val b = rgb and 0xFF
                luma[y * w + x] = (r * 77 + g * 150 + b * 29) ushr 8
            }
        }

        val bounds = contentBounds(luma, w, h)
        val candidates = LinkedHashSet<Int>()
        estimateBackgroundLuma(luma)?.let { candidates.add(it) }
        brightBackgroundLuma(luma)?.let { candidates.add(it) }

        // 底色猜错时整页只认墨色为留白，分格会退成整页；两个候选各跑一遍取分高的，
        // 比在单点上猜"纸色还是画面色"稳。
        var best: List<ComicPanelRect>? = null
        var bestScore = -Float.MAX_VALUE
        for (bg in candidates) {
            val panels = detectWith(luma, w, h, bounds, bg, rightToLeft)
            val coverage = panels.sumOf { (it.width * it.height).toDouble() }.toFloat()
            // 重叠要重罚：底色猜错时也会切出一堆格子，但它们互相压着，取景没法用。
            val score = panels.size * 2f + coverage - overlapArea(panels) * 4f
            if (score > bestScore) {
                bestScore = score
                best = panels
            }
        }
        val panels = best ?: emptyList()
        if (panels.size <= 1) {
            return listOf(ComicPanelRect(0f, 0f, 1f, 1f))
        }
        return panels
    }

    private fun detectWith(
        luma: IntArray,
        w: Int,
        h: Int,
        bounds: IntArray,
        bgLuma: Int,
        rightToLeft: Boolean,
    ): List<ComicPanelRect> {
        val rawBoxes = mutableListOf<IntArray>()
        splitRegion(
            luma, w, h,
            bounds[0], bounds[1], bounds[2], bounds[3],
            bgLuma, depth = 0, rightToLeft = rightToLeft, out = rawBoxes,
        )
        if (rawBoxes.isEmpty()) return emptyList()

        val rects = rawBoxes.mapNotNull { box ->
            val trimmed = trimBox(luma, w, box[0], box[1], box[2], box[3], bgLuma)
            val bw = trimmed[2] - trimmed[0]
            val bh = trimmed[3] - trimmed[1]
            if (bw <= 0 || bh <= 0) {
                null
            } else {
                ComicPanelRect(
                    left = (trimmed[0].toFloat() / w).coerceIn(0f, 1f),
                    top = (trimmed[1].toFloat() / h).coerceIn(0f, 1f),
                    right = (trimmed[2].toFloat() / w).coerceIn(0f, 1f),
                    bottom = (trimmed[3].toFloat() / h).coerceIn(0f, 1f),
                )
            }
        }
        return mergeFragments(rects)
    }

    /**
     * 把每页的分格拼进跨页坐标。
     *
     * 双页是一屏：两页按各自宽高比分配宽度，各自的分格平移缩放进来，
     * 之后运镜与遮罩只认这一套坐标，不必分单双页两套几何。
     *
     * @param pages 每页的分格列表，按渲染序（左在前）。
     * @param ratios 每页宽高比，与 [pages] 等长。
     */
    fun composeSpreadPanels(pages: List<List<ComicPanelRect>>, ratios: List<Float>): List<ComicPanelRect> {
        if (pages.size <= 1) return pages.firstOrNull().orEmpty()
        val total = ratios.sum().takeIf { it > 0.05f } ?: pages.size.toFloat()
        val result = ArrayList<ComicPanelRect>()
        var offset = 0f
        for (i in pages.indices) {
            val share = (ratios.getOrElse(i) { 1f } / total).coerceIn(0.01f, 1f)
            pages[i].forEach { p ->
                result.add(ComicPanelRect(offset + p.left * share, p.top, offset + p.right * share, p.bottom))
            }
            offset += share
        }
        return result
    }

    /**
     * 按首尾整页开关组装最终导览步骤序列。
     *
     * @param panels 检测或降级得到的分格列表。
     * @param showFullFirst 是否在首格前先展示整页。
     * @param showFullLast 是否在尾格后展示整页。
     */
    fun buildGuidedSteps(
        panels: List<ComicPanelRect>,
        showFullFirst: Boolean,
        showFullLast: Boolean,
    ): List<ComicPanelRect> {
        if (panels.isEmpty()) return listOf(ComicPanelRect(0f, 0f, 1f, 1f))
        val full = ComicPanelRect(0f, 0f, 1f, 1f)
        val result = ArrayList<ComicPanelRect>(panels.size + 2)
        if (showFullFirst && !panels.first().isFullPage) {
            result.add(full)
        }
        result.addAll(panels)
        if (showFullLast && !panels.last().isFullPage) {
            result.add(full)
        }
        return result
    }

    /**
     * 为单页生成默认备用分格（降级策略：四象限顺次浏览）。
     *
     * @param rightToLeft 是否为日漫右至左阅读顺序。
     */
    fun fallbackQuadrants(rightToLeft: Boolean = true): List<ComicPanelRect> {
        return if (rightToLeft) {
            listOf(
                ComicPanelRect(0.5f, 0f, 1f, 0.5f),
                ComicPanelRect(0f, 0f, 0.5f, 0.5f),
                ComicPanelRect(0.5f, 0.5f, 1f, 1f),
                ComicPanelRect(0f, 0.5f, 0.5f, 1f),
            )
        } else {
            listOf(
                ComicPanelRect(0f, 0f, 0.5f, 0.5f),
                ComicPanelRect(0.5f, 0f, 1f, 0.5f),
                ComicPanelRect(0f, 0.5f, 0.5f, 1f),
                ComicPanelRect(0.5f, 0.5f, 1f, 1f),
            )
        }
    }

    /**
     * 计算聚焦到指定分格时的缩放倍率和平移偏移量。
     *
     * 先按 [imageRatio] 扣除 `ContentScale.Fit` 在视口内的黑边，再将分格映射到实际图片矩形，
     * 结合 [paddingPx] 留白与 [maxZoom] 上限算出居中变换。
     *
     * @param panel 当前画格区域（归一化坐标）。
     * @param viewportWidth 视口宽度（px）。
     * @param viewportHeight 视口高度（px）。
     * @param imageRatio 图片真实宽高比（宽/高）。
     * @param paddingPx 分格四周留白（px）。
     * @param maxZoom 最大缩放倍率上限。
     */
    fun computePanelFocusTransform(
        panel: ComicPanelRect,
        viewportWidth: Float,
        viewportHeight: Float,
        imageRatio: Float = 0.7f,
        paddingPx: Float = 0f,
        maxZoom: Float = 3.5f,
    ): ReaderViewportTransform {
        if (viewportWidth <= 0f || viewportHeight <= 0f) {
            return ReaderViewportTransform(1f, Offset.Zero)
        }
        if (panel.isFullPage) {
            return ReaderViewportTransform(1f, Offset.Zero)
        }

        val safeRatio = imageRatio.coerceIn(0.2f, 5f)
        val vpRatio = viewportWidth / viewportHeight
        val imgW: Float
        val imgH: Float
        if (safeRatio > vpRatio) {
            imgW = viewportWidth
            imgH = viewportWidth / safeRatio
        } else {
            imgH = viewportHeight
            imgW = viewportHeight * safeRatio
        }
        val imgLeft = (viewportWidth - imgW) / 2f
        val imgTop = (viewportHeight - imgH) / 2f

        val panelW = (panel.width * imgW).coerceAtLeast(1f)
        val panelH = (panel.height * imgH).coerceAtLeast(1f)
        val availW = (viewportWidth - paddingPx * 2f).coerceAtLeast(viewportWidth * 0.35f)
        val availH = (viewportHeight - paddingPx * 2f).coerceAtLeast(viewportHeight * 0.35f)

        val targetScaleX = availW / panelW
        val targetScaleY = availH / panelH
        val targetZoom = minOf(targetScaleX, targetScaleY, maxZoom.coerceAtLeast(1f)).coerceAtLeast(1f)

        val panelCenterX = imgLeft + ((panel.left + panel.right) / 2f) * imgW
        val panelCenterY = imgTop + ((panel.top + panel.bottom) / 2f) * imgH

        val offsetX = (viewportWidth / 2f - panelCenterX) * targetZoom
        val offsetY = (viewportHeight / 2f - panelCenterY) * targetZoom

        return ReaderViewportTransform(
            zoom = targetZoom,
            pan = Offset(offsetX, offsetY),
        )
    }

    /**
     * 无分格模型时的降级分格：按页宽高比选轴的固定三分格。
     *
     * @param pageRatio 页宽高比（宽/高）。
     * @param rightToLeft 横向三带时是否按右至左排序。
     */
    fun fallbackTriPanels(pageRatio: Float, rightToLeft: Boolean): List<ComicPanelRect> {
        return if (pageRatio < 1f) {
            listOf(
                ComicPanelRect(0f, 0f, 1f, 1f / 3f),
                ComicPanelRect(0f, 1f / 3f, 1f, 2f / 3f),
                ComicPanelRect(0f, 2f / 3f, 1f, 1f),
            )
        } else {
            val panels = listOf(
                ComicPanelRect(0f, 0f, 1f / 3f, 1f),
                ComicPanelRect(1f / 3f, 0f, 2f / 3f, 1f),
                ComicPanelRect(2f / 3f, 0f, 1f, 1f),
            )
            if (rightToLeft) panels.reversed() else panels
        }
    }

    /**
     * 隐藏/沉浸式样式遮罩色。
     *
     * @param style 导览视图模式：0=聚焦（无遮罩），1=隐藏（非当前格压遮罩），2=沉浸式（暗色挖空）。
     * @param maskStyle 遮罩色档：0=白，1=黑，2=模糊。
     */
    fun maskColorFor(style: Int, maskStyle: Int): Color? {
        return when (style) {
            1 -> when (maskStyle) {
                0 -> Color.White.copy(alpha = 0.92f)
                2 -> Color.Black.copy(alpha = 0.68f)
                else -> Color.Black.copy(alpha = 0.92f)
            }
            2 -> Color.Black.copy(alpha = 0.82f)
            else -> null
        }
    }

    /** 取占面积最大的明度当底色候选；窗口内取均值削掉网点抖动。 */
    private fun estimateBackgroundLuma(luma: IntArray): Int? {
        val hist = IntArray(256)
        for (v in luma) hist[v]++
        val mode = dominantLuma(hist) ?: return null
        var sum = 0L
        var count = 0L
        for (v in max(0, mode - 8)..min(255, mode + 8)) {
            sum += v.toLong() * hist[v]
            count += hist[v]
        }
        return if (count == 0L) mode else (sum / count).toInt()
    }

    /**
     * 取页面偏亮一侧的众数当底色候选。
     *
     * 画面占多数时直方图峰会落在画面色上，而纸色通常仍是整页最亮的那一簇；
     * 两个候选各跑一遍取分高的，比在单点上赌哪一侧对更稳。
     */
    private fun brightBackgroundLuma(luma: IntArray): Int? {
        val hist = IntArray(256)
        for (v in luma) hist[v]++
        var total = 0L
        for (v in 0..255) total += hist[v]
        if (total == 0L) return null
        var acc = 0L
        var p95 = 255
        for (v in 0..255) {
            acc += hist[v]
            if (acc * 100L >= total * 95L) {
                p95 = v
                break
            }
        }
        val hist2 = IntArray(256)
        for (v in max(0, p95 - 8)..min(255, p95 + 8)) hist2[v] = hist[v]
        return dominantLuma(hist2)
    }

    private fun dominantLuma(hist: IntArray): Int? {
        var mode = 0
        for (v in 1..255) {
            if (hist[v] > hist[mode]) mode = v
        }
        return if (hist[mode] == 0) null else mode
    }

    /**
     * 切掉页面最外圈的纯色带（留白或扫描黑边）。
     *
     * 不先切掉，黑边会被当成一条通栏内容，检测完多出一个细长条分格。
     * 每侧最多切掉四分之一：再多说明整页就是纯色，边界不可信。
     */
    private fun contentBounds(luma: IntArray, w: Int, h: Int): IntArray {
        var top = 0
        var bottom = h
        var left = 0
        var right = w
        val limitY = h / 4
        val limitX = w / 4
        while (top < limitY && isUniformRow(luma, w, top, left, right)) top++
        while (bottom > h - limitY && isUniformRow(luma, w, bottom - 1, left, right)) bottom--
        while (left < limitX && isUniformCol(luma, w, left, top, bottom)) left++
        while (right > w - limitX && isUniformCol(luma, w, right - 1, top, bottom)) right--
        if (right - left < w * 0.2f || bottom - top < h * 0.2f) {
            return intArrayOf(0, 0, w, h)
        }
        return intArrayOf(left, top, right, bottom)
    }

    private fun isUniformRow(luma: IntArray, w: Int, y: Int, x0: Int, x1: Int): Boolean {
        var mn = 255
        var mx = 0
        val rowOffset = y * w
        for (x in x0 until x1) {
            val v = luma[rowOffset + x]
            if (v < mn) mn = v
            if (v > mx) mx = v
            if (mx - mn > GUTTER_TOLERANCE) return false
        }
        return true
    }

    private fun isUniformCol(luma: IntArray, w: Int, x: Int, y0: Int, y1: Int): Boolean {
        var mn = 255
        var mx = 0
        for (y in y0 until y1) {
            val v = luma[y * w + x]
            if (v < mn) mn = v
            if (v > mx) mx = v
            if (mx - mn > GUTTER_TOLERANCE) return false
        }
        return true
    }

    private fun isGutterRow(luma: IntArray, w: Int, y: Int, x0: Int, x1: Int, bgLuma: Int): Boolean {
        val len = x1 - x0
        if (len <= 0) return true
        var match = 0
        val rowOffset = y * w
        for (x in x0 until x1) {
            if (abs(luma[rowOffset + x] - bgLuma) <= GUTTER_TOLERANCE) match++
        }
        return match.toFloat() / len >= GUTTER_ROW_FRACTION
    }

    private fun isGutterCol(luma: IntArray, w: Int, x: Int, y0: Int, y1: Int, bgLuma: Int): Boolean {
        val len = y1 - y0
        if (len <= 0) return true
        var match = 0
        for (y in y0 until y1) {
            if (abs(luma[y * w + x] - bgLuma) <= GUTTER_TOLERANCE) match++
        }
        return match.toFloat() / len >= GUTTER_ROW_FRACTION
    }

    private fun splitRegion(
        luma: IntArray,
        w: Int,
        h: Int,
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        bgLuma: Int,
        depth: Int,
        rightToLeft: Boolean,
        out: MutableList<IntArray>,
    ) {
        val rw = x1 - x0
        val rh = y1 - y0
        if (depth >= MAX_SPLIT_DEPTH || rw < w * MIN_SPLIT_FRACTION || rh < h * MIN_SPLIT_FRACTION) {
            out.add(intArrayOf(x0, y0, x1, y1))
            return
        }

        val hBands = absorbThinBands(
            findContentSpans(y0, y1) { y -> !isGutterRow(luma, w, y, x0, x1, bgLuma) },
            (rh * MIN_SPLIT_SHARE).toInt(),
        )
        if (hBands.size > 1) {
            for (band in hBands) {
                splitRegion(luma, w, h, x0, band[0], x1, band[1], bgLuma, depth + 1, rightToLeft, out)
            }
            return
        }

        val vBands = absorbThinBands(
            findContentSpans(x0, x1) { x -> !isGutterCol(luma, w, x, y0, y1, bgLuma) },
            (rw * MIN_SPLIT_SHARE).toInt(),
        )
        if (vBands.size > 1) {
            // 阅读顺序在递归里定死。出去再排序得靠比较器判同行，而"同行"不满足传递性，
            // 一页里既有通栏长条又有并排小格时顺序会错乱。
            val ordered = if (rightToLeft) vBands.asReversed() else vBands
            for (band in ordered) {
                splitRegion(luma, w, h, band[0], y0, band[1], y1, bgLuma, depth + 1, rightToLeft, out)
            }
            return
        }

        out.add(intArrayOf(x0, y0, x1, y1))
    }

    private inline fun findContentSpans(start: Int, end: Int, isContent: (Int) -> Boolean): List<IntArray> {
        val spans = mutableListOf<IntArray>()
        var inSpan = false
        var spanStart = start
        var gutterCount = 0
        for (i in start until end) {
            if (isContent(i)) {
                if (!inSpan) {
                    inSpan = true
                    spanStart = i
                }
                gutterCount = 0
            } else if (inSpan) {
                gutterCount++
                if (gutterCount >= MIN_GUTTER_PX) {
                    val spanEnd = i - gutterCount + 1
                    if (spanEnd > spanStart) {
                        spans.add(intArrayOf(spanStart, spanEnd))
                    }
                    inSpan = false
                    gutterCount = 0
                }
            }
        }
        if (inSpan) {
            val spanEnd = end - gutterCount
            if (spanEnd > spanStart) {
                spans.add(intArrayOf(spanStart, spanEnd))
            }
        }
        return spans
    }

    /**
     * 把过薄的带并进相邻带，剩下的才当作切分结果。
     *
     * 画格内部的大片留白常能凑出"整行留白"，照切就把一格切成几条细缝；
     * 而跨越沟壑的杂线又会在真沟壑中间插进一条细带。两种都由这一步吸收掉。
     */
    private fun absorbThinBands(bands: List<IntArray>, minLen: Int): List<IntArray> {
        if (bands.size < 2) return bands
        val out = ArrayList<IntArray>(bands.size)
        var pendingStart = -1
        for (b in bands) {
            if (b[1] - b[0] < minLen) {
                if (out.isEmpty()) {
                    if (pendingStart < 0) pendingStart = b[0]
                } else {
                    val last = out[out.size - 1]
                    out[out.size - 1] = intArrayOf(last[0], b[1])
                }
            } else if (pendingStart >= 0) {
                out.add(intArrayOf(pendingStart, b[1]))
                pendingStart = -1
            } else {
                out.add(b)
            }
        }
        return if (out.size > 1) out else emptyList()
    }

    private fun trimBox(luma: IntArray, w: Int, x0: Int, y0: Int, x1: Int, y1: Int, bgLuma: Int): IntArray {
        var top = y0
        var bottom = y1
        var left = x0
        var right = x1
        while (top < bottom - 1 && isGutterRow(luma, w, top, left, right, bgLuma)) top++
        while (bottom > top + 1 && isGutterRow(luma, w, bottom - 1, left, right, bgLuma)) bottom--
        while (left < right - 1 && isGutterCol(luma, w, left, top, bottom, bgLuma)) left++
        while (right > left + 1 && isGutterCol(luma, w, right - 1, top, bottom, bgLuma)) right--
        return intArrayOf(left, top, right, bottom)
    }

    /**
     * 把过小的碎块并进并集最小的相邻格。
     *
     * 直接丢弃会让这些像素在任何一步里都看不到 —— 读者以为漏了内容，
     * 而并进邻格只是那一格的取景略大，不影响内容完整。
     */
    private fun mergeFragments(rects: List<ComicPanelRect>): List<ComicPanelRect> {
        if (rects.isEmpty()) return rects
        val kept = ArrayList<ComicPanelRect>(rects.size)
        val fragments = ArrayList<ComicPanelRect>()
        for (r in rects) {
            val big = r.width * r.height >= MIN_PANEL_AREA_FRACTION &&
                r.width >= MIN_PANEL_SIDE_FRACTION &&
                r.height >= MIN_PANEL_SIDE_FRACTION
            if (big) kept.add(r) else fragments.add(r)
        }
        if (kept.isEmpty()) {
            val largest = rects.maxByOrNull { it.width * it.height } ?: return rects
            return listOf(largest)
        }
        for (f in fragments) {
            var target = -1
            var smallest = Float.MAX_VALUE
            for (i in kept.indices) {
                // 只并给挨着的邻格：并给远处的格子会把那一格撑成跨整页的长条，
                // 结果是两个分格互相重叠，取景全乱。
                if (gapBetween(kept[i], f) > MERGE_MAX_GAP) continue
                val u = unionRect(kept[i], f)
                val area = u.width * u.height
                if (area < smallest) {
                    smallest = area
                    target = i
                }
            }
            if (target >= 0) kept[target] = unionRect(kept[target], f)
        }
        return kept
    }

    private fun overlapArea(panels: List<ComicPanelRect>): Float {
        var total = 0f
        for (i in panels.indices) {
            for (j in i + 1 until panels.size) {
                val a = panels[i]
                val b = panels[j]
                val dx = min(a.right, b.right) - max(a.left, b.left)
                val dy = min(a.bottom, b.bottom) - max(a.top, b.top)
                if (dx > 0f && dy > 0f) total += dx * dy
            }
        }
        return total
    }

    private fun gapBetween(a: ComicPanelRect, b: ComicPanelRect): Float {
        val dx = max(0f, max(a.left, b.left) - min(a.right, b.right))
        val dy = max(0f, max(a.top, b.top) - min(a.bottom, b.bottom))
        return max(dx, dy)
    }

    private fun unionRect(a: ComicPanelRect, b: ComicPanelRect): ComicPanelRect = ComicPanelRect(
        left = min(a.left, b.left),
        top = min(a.top, b.top),
        right = max(a.right, b.right),
        bottom = max(a.bottom, b.bottom),
    )
}
