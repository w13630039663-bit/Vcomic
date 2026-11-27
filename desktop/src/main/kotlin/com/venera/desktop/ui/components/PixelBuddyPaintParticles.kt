package com.venera.desktop.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.random.Random

internal class PaintCardSeed(
    val key: String,
    val colIdx: Int,
    var rect: Rect,
    var claimed: Boolean = false,
    var done: Boolean = false,
)

internal class PaintColumn(
    val cx: Float,
    val seeds: MutableList<PaintCardSeed> = mutableListOf(),
    val tail: MutableList<PaintCardSeed> = mutableListOf(),
    var beamLaunched: Boolean = false,
)

internal class ColumnLightBeam(
    val colCx: Float,
    var y: Float,
    val targetY: Float,
    val color: Color,
    val queue: MutableList<PaintCardSeed>,
)

internal class BrushTrailDot(val x: Float, val y: Float, val color: Color, var life: Float, val vertical: Boolean = false)
internal class PaintSpark(var x: Float, var y: Float, val vx: Float, val vy: Float, val color: Color, var life: Float)

/**
 * 卡片描绘过程中的画笔轨迹、列光流束与火花粒子辅助容器。
 */
internal class PixelBuddyPaintParticles {

    val trails = mutableListOf<BrushTrailDot>()
    val sparks = mutableListOf<PaintSpark>()

    fun addTrail(x: Float, y: Float, color: Color, life: Float = 18f, vertical: Boolean = false) {
        trails.add(BrushTrailDot(x, y, color, life, vertical))
    }

    fun emitSparks(x: Float, y: Float, color: Color, count: Int, density: Float) {
        repeat(count) {
            sparks.add(
                PaintSpark(
                    x = x,
                    y = y,
                    vx = (Random.nextFloat() - 0.5f) * 2.6f * density,
                    vy = (-Random.nextFloat() * 2.2f - 0.4f) * density,
                    color = color,
                    life = 16f,
                ),
            )
        }
    }

    fun step(dt: Float) {
        for (i in sparks.lastIndex downTo 0) {
            val s = sparks[i]
            s.x += s.vx * dt; s.y += s.vy * dt; s.life -= dt
            if (s.life <= 0f) sparks.removeAt(i)
        }
        for (i in trails.lastIndex downTo 0) {
            trails[i].life -= dt
            if (trails[i].life <= 0f) trails.removeAt(i)
        }
    }

    fun DrawScope.draw(cellPx: Float, beams: List<ColumnLightBeam>) {
        val trailW = cellPx * 2.5f
        for (tr in trails) {
            val a = (tr.life / 18f).coerceIn(0f, 1f) * 0.62f
            if (tr.vertical) {
                drawRect(
                    color = tr.color.copy(alpha = a),
                    topLeft = Offset(tr.x - cellPx * 0.5f, tr.y - cellPx * 2f),
                    size = Size(cellPx, cellPx * 4f),
                )
            } else {
                drawRect(
                    color = tr.color.copy(alpha = a),
                    topLeft = Offset(tr.x - trailW * 0.5f, tr.y),
                    size = Size(trailW, cellPx),
                )
            }
        }
        for (bm in beams) {
            drawRect(
                color = bm.color.copy(alpha = 0.85f),
                topLeft = Offset(bm.colCx - cellPx * 0.65f, bm.y - cellPx * 5f),
                size = Size(cellPx * 1.3f, cellPx * 6f),
            )
            drawRect(
                color = Color.White,
                topLeft = Offset(bm.colCx - cellPx * 0.35f, bm.y - cellPx * 1.3f),
                size = Size(cellPx * 0.7f, cellPx * 2f),
            )
        }
        for (s in sparks) {
            val a = (s.life / 16f).coerceIn(0f, 1f)
            drawRect(color = s.color.copy(alpha = a), topLeft = Offset(s.x, s.y), size = Size(cellPx, cellPx))
        }
    }
}
