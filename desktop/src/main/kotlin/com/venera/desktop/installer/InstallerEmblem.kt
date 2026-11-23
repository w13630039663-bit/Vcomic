package com.venera.desktop.installer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 徽记内部标记类型（保留兼容性）。 */
enum class EmblemMark { V, CHECK }

/**
 * 安装向导 VComic 徽记：白底圆角徽章 + 原版「圆笔黑 V + 摊开书 + 书脊白缝」。
 *
 * 启动时按 31 段连续粗圆笔分段错峰生长（总跨度 800ms），生长完成瞬间无缝贴合原版矢量轮廓。
 *
 * @param progress 总体生长进度（0f..1f），若传入 [elapsedMs] 则按 31 片独立错峰曲线绘制。
 * @param elapsedMs 启动页经过的毫秒数（非空时启用 31 片精确错峰）。
 * @param cornerRadius 外层白底徽章圆角。
 * @param mark 保留兼容字段。
 */
@Composable
fun InstallerEmblem(
    progress: Float,
    modifier: Modifier = Modifier,
    elapsedMs: Long? = null,
    cornerRadius: Dp = 20.dp,
    tint: Color = InstallerSpec.IconMarkStroke,
    halo: Float = 0f,
    mark: EmblemMark = EmblemMark.V,
) {
    val palette = LocalInstallerPalette.current
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .background(InstallerSpec.IconBadgeSurface, shape)
            .border(1.dp, palette.borderSubtle, shape),
    ) {
        Canvas(Modifier.fillMaxSize().padding(6.dp)) {
            drawVComic31Mark(
                progress = progress.coerceIn(0f, 1f),
                elapsedMs = elapsedMs,
                strokeColor = tint,
            )
        }
    }
}

private data class EmblemSegment(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val width: Float,
    val order: Int,
)

private val VCOMIC_31_SEGMENTS: List<EmblemSegment> by lazy {
    val list = ArrayList<EmblemSegment>(InstallerTimeline.EMBLEM_PIECES)

    fun sampleCubic(p0: Offset, p1: Offset, p2: Offset, p3: Offset, t: Float): Offset {
        val u = 1f - t
        val x = u * u * u * p0.x + 3f * u * u * t * p1.x + 3f * u * t * t * p2.x + t * t * t * p3.x
        val y = u * u * u * p0.y + 3f * u * u * t * p1.y + 3f * u * t * t * p2.y + t * t * t * p3.y
        return Offset(x, y)
    }

    // 1. V 字交汇底座 (1) + 左臂 (6) + 右臂 (6) = 13 段
    list.add(EmblemSegment(50f, 56f, 50f, 56.5f, 11.2f, 0))
    for (i in 0 until 6) {
        val t0 = i / 6f
        val t1 = (i + 1) / 6f
        list.add(
            EmblemSegment(
                50f + (32.5f - 50f) * t0, 56f + (28.5f - 56f) * t0,
                50f + (32.5f - 50f) * t1, 56f + (28.5f - 56f) * t1,
                10.6f, 1 + i * 2,
            ),
        )
        list.add(
            EmblemSegment(
                50f + (67.5f - 50f) * t0, 56f + (28.5f - 56f) * t0,
                50f + (67.5f - 50f) * t1, 56f + (28.5f - 56f) * t1,
                10.6f, 2 + i * 2,
            ),
        )
    }

    // 2. 左书页弧 9 段
    val lp0 = Offset(46.5f, 68.5f)
    val lp1 = Offset(40.0f, 65.0f)
    val lp2 = Offset(29.5f, 62.0f)
    val lp3 = Offset(23.0f, 64.5f)
    for (i in 0 until 9) {
        val a = sampleCubic(lp0, lp1, lp2, lp3, i / 9f)
        val b = sampleCubic(lp0, lp1, lp2, lp3, (i + 1) / 9f)
        list.add(EmblemSegment(a.x, a.y, b.x, b.y, 10.2f, 13 + i))
    }

    // 3. 右书页弧 9 段
    val rp0 = Offset(53.5f, 68.5f)
    val rp1 = Offset(60.0f, 65.0f)
    val rp2 = Offset(70.5f, 62.0f)
    val rp3 = Offset(77.0f, 64.5f)
    for (i in 0 until 9) {
        val a = sampleCubic(rp0, rp1, rp2, rp3, i / 9f)
        val b = sampleCubic(rp0, rp1, rp2, rp3, (i + 1) / 9f)
        list.add(EmblemSegment(a.x, a.y, b.x, b.y, 10.2f, 22 + i))
    }

    list
}

private fun DrawScope.drawVComic31Mark(
    progress: Float,
    elapsedMs: Long?,
    strokeColor: Color,
) {
    val sx = size.width / 100f
    val sy = size.height / 100f
    val scaleAvg = (sx + sy) * 0.5f

    val isComplete = if (elapsedMs != null) {
        elapsedMs >= InstallerTimeline.EMBLEM_DRAW_MS
    } else {
        progress >= 0.995f
    }

    if (isComplete) {
        // 原版「圆笔黑 V + 摊开书 + 中央书脊白缝」矢量轮廓
        val vPath = Path().apply {
            moveTo(32.5f * sx, 28.5f * sy)
            lineTo(50f * sx, 57.5f * sy)
            lineTo(67.5f * sx, 28.5f * sy)
        }
        drawPath(
            path = vPath,
            color = strokeColor,
            style = Stroke(width = 10.8f * scaleAvg, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        val leftWing = Path().apply {
            moveTo(48.2f * sx, 72.5f * sy)
            cubicTo(41.5f * sx, 68.5f * sy, 31.5f * sx, 66.5f * sy, 23.0f * sx, 64.5f * sy)
            cubicTo(21.0f * sx, 64.0f * sy, 21.5f * sx, 60.0f * sy, 26.0f * sx, 59.5f * sy)
            cubicTo(34.5f * sx, 58.5f * sy, 42.5f * sx, 61.0f * sy, 48.2f * sx, 65.5f * sy)
            close()
        }
        drawPath(leftWing, strokeColor)

        val rightWing = Path().apply {
            moveTo(51.8f * sx, 72.5f * sy)
            cubicTo(58.5f * sx, 68.5f * sy, 68.5f * sx, 66.5f * sy, 77.0f * sx, 64.5f * sy)
            cubicTo(79.0f * sx, 64.0f * sy, 78.5f * sx, 60.0f * sy, 74.0f * sx, 59.5f * sy)
            cubicTo(65.5f * sx, 58.5f * sy, 57.5f * sx, 61.0f * sy, 51.8f * sx, 65.5f * sy)
            close()
        }
        drawPath(rightWing, strokeColor)

        val leftArc = Path().apply {
            moveTo(23.0f * sx, 64.5f * sy)
            cubicTo(29.5f * sx, 62.0f * sy, 40.0f * sx, 65.0f * sy, 46.5f * sx, 68.5f * sy)
        }
        drawPath(leftArc, strokeColor, style = Stroke(width = 9.6f * scaleAvg, cap = StrokeCap.Round))

        val rightArc = Path().apply {
            moveTo(77.0f * sx, 64.5f * sy)
            cubicTo(70.5f * sx, 62.0f * sy, 60.0f * sx, 65.0f * sy, 53.5f * sx, 68.5f * sy)
        }
        drawPath(rightArc, strokeColor, style = Stroke(width = 9.6f * scaleAvg, cap = StrokeCap.Round))

        // 中央书脊白缝
        drawRoundRect(
            color = InstallerSpec.IconBadgeSurface,
            topLeft = Offset(48.5f * sx, 64.0f * sy),
            size = Size(3.0f * sx, 12.0f * sy),
            cornerRadius = CornerRadius(1.5f * scaleAvg, 1.5f * scaleAvg),
        )
        return
    }

    val ms = elapsedMs ?: (progress * InstallerTimeline.EMBLEM_DRAW_MS).toLong()
    for (seg in VCOMIC_31_SEGMENTS) {
        val p = InstallerTimeline.emblemPieceProgress(ms, seg.order)
        if (p <= 0.01f) continue
        val mx = (seg.x1 + seg.x2) * 0.5f
        val my = (seg.y1 + seg.y2) * 0.5f
        val scale = 0.4f + 0.6f * p
        val ax = mx + (seg.x1 - mx) * scale
        val ay = my + (seg.y1 - my) * scale
        val bx = mx + (seg.x2 - mx) * scale
        val by = my + (seg.y2 - my) * scale
        drawLine(
            color = strokeColor.copy(alpha = p),
            start = Offset(ax * sx, ay * sy),
            end = Offset(bx * sx, by * sy),
            strokeWidth = seg.width * scaleAvg * scale,
            cap = StrokeCap.Round,
        )
    }
}
