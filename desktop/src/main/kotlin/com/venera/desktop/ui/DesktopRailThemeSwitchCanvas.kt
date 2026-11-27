package com.venera.desktop.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.venera.desktop.settings.DesktopRailWeatherType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

private data class StarSpec(val xf: Float, val yf: Float, val rDp: Float, val delay: Float)
private data class DropletSpec(val xf: Float, val yf: Float, val rDp: Float)
private data class ParticleSpec(val xSeed: Float, val ySeed: Float, val sizeDp: Float, val speed: Float)

private val ExpandedStars = listOf(
    StarSpec(0.52f, 0.26f, 2.7f, 0.0f),
    StarSpec(0.64f, 0.66f, 2.0f, 0.10f),
    StarSpec(0.72f, 0.22f, 2.3f, 0.18f),
    StarSpec(0.46f, 0.74f, 1.7f, 0.25f),
    StarSpec(0.88f, 0.24f, 1.9f, 0.32f),
)
private val CompactStars = listOf(
    StarSpec(0.24f, 0.25f, 2.5f, 0.0f),
    StarSpec(0.76f, 0.72f, 1.9f, 0.2f),
)
private val GlassDroplets = listOf(
    DropletSpec(0.10f, 0.28f, 2.3f),
    DropletSpec(0.17f, 0.68f, 3.1f),
    DropletSpec(0.27f, 0.42f, 1.9f),
    DropletSpec(0.36f, 0.76f, 2.6f),
    DropletSpec(0.44f, 0.26f, 1.7f),
    DropletSpec(0.56f, 0.70f, 2.1f),
)
private val FineDrops = List(9) { i ->
    ParticleSpec(((i * 19) % 100) / 100f, ((i * 31) % 100) / 100f, 4.5f + (i % 3) * 1.8f, 0.72f + (i % 4) * 0.16f)
}
private val SnowFlakes = List(18) { i ->
    ParticleSpec(((i * 29) % 100) / 100f, ((i * 17) % 100) / 100f, 1.1f + (i % 3) * 0.65f, 0.22f + (i % 4) * 0.08f)
}
private val BtnSparks = List(12) { i ->
    val ang = ((2.0 * PI / 12.0) * i + 0.15).toFloat()
    Triple(ang, 10f + (i % 3) * 6f, 1.1f + (i % 2) * 0.7f)
}

/**
 * 绘制星穹实时天气昼夜舱的完整天象、天体残影、水珠细雨、雷暴云闪与星尘扫光。
 */
internal fun DrawScope.drawCelestialWeatherCanvas(
    progress: Float,
    sweep: Float,
    weather: DesktopRailWeatherType,
    timeSec: Float,
    hovered: Boolean,
    compact: Boolean,
) {
    val p = progress.coerceIn(0f, 1f)
    val w = size.width
    val h = size.height

    val (topC, midC, botC) = skyStops(p, weather)
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(topC, midC, botC),
            start = Offset.Zero,
            end = Offset(w * 0.45f, h),
        ),
    )

    if (weather == DesktopRailWeatherType.STORM) {
        drawStormFlash(timeSec, hovered)
    }
    drawHorizonPulse(p)
    drawStarfield(p, weather, timeSec, compact)
    drawSunArc(p, weather, compact)
    drawMoonArc(p, weather, compact)
    drawWeatherAtmosphere(p, weather, timeSec, hovered, compact)
    drawSweepAndSparks(p, sweep, compact)
}

private fun DrawScope.drawStormFlash(timeSec: Float, hovered: Boolean) {
    val cycle = timeSec % 2.4f
    if (!hovered || cycle >= 0.24f) return
    val flashAlpha = (1f - cycle / 0.24f) * 0.85f
    val lx = size.width * 0.26f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFAFC8FF).copy(alpha = flashAlpha * 0.55f), Color.Transparent),
            center = Offset(lx, size.height * 0.28f),
            radius = size.width * 0.45f,
        ),
        radius = size.width * 0.45f,
        center = Offset(lx, size.height * 0.28f),
    )
    val bolt = Path().apply {
        moveTo(lx, 4.dp.toPx())
        lineTo(lx - 4.dp.toPx(), size.height * 0.36f)
        lineTo(lx + 3.dp.toPx(), size.height * 0.48f)
        lineTo(lx - 3.dp.toPx(), size.height * 0.80f)
    }
    drawPath(bolt, color = Color(0xFFEBF5FF).copy(alpha = flashAlpha), style = Stroke(width = 1.4.dp.toPx()))
}

private fun DrawScope.drawHorizonPulse(p: Float) {
    val horizonPulse = (1f - abs(p - 0.5f) * 4.5f).coerceAtLeast(0f)
    if (horizonPulse <= 0.02f) return
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFFFD28C).copy(alpha = horizonPulse * 0.65f),
                Color(0xFFA78BFA).copy(alpha = horizonPulse * 0.36f),
                Color.Transparent,
            ),
            center = Offset(size.width * 0.5f, size.height * 0.78f),
            radius = size.width * 0.6f,
        ),
        radius = size.width * 0.6f,
        center = Offset(size.width * 0.5f, size.height * 0.78f),
    )
}

private fun DrawScope.drawStarfield(
    p: Float,
    weather: DesktopRailWeatherType,
    timeSec: Float,
    compact: Boolean,
) {
    val starDensity = when (weather) {
        DesktopRailWeatherType.CLEAR -> 1f
        DesktopRailWeatherType.CLOUDY -> 0.35f
        else -> 0.15f
    }
    if (p <= 0.35f || starDensity <= 0f) return
    val list = if (compact) CompactStars else ExpandedStars
    list.forEachIndexed { idx, s ->
        val sp = ((p - 0.40f - s.delay * 0.5f) / 0.40f).coerceIn(0f, 1f) * starDensity
        if (sp <= 0.02f) return@forEachIndexed
        val twinkle = 0.75f + 0.25f * sin(timeSec * 3.5f + idx * 1.9f)
        val r = s.rDp.dp.toPx() * twinkle * 1.35f
        val cx = s.xf * size.width
        val cy = s.yf * size.height
        val starPath = Path().apply {
            moveTo(cx, cy - r)
            quadraticTo(cx, cy, cx + r, cy)
            quadraticTo(cx, cy, cx, cy + r)
            quadraticTo(cx, cy, cx - r, cy)
            quadraticTo(cx, cy, cx, cy - r)
        }
        drawPath(starPath, color = Color.White.copy(alpha = 0.90f * sp * twinkle))
    }
}

private fun DrawScope.drawSunArc(p: Float, weather: DesktopRailWeatherType, compact: Boolean) {
    val sunT = (p / 0.55f).coerceIn(0f, 1f)
    if (sunT >= 0.99f || weather == DesktopRailWeatherType.STORM) return
    val w = size.width
    val h = size.height
    val sunR = (if (compact) 8.5f else 10.5f).dp.toPx()
    val dim = if (weather == DesktopRailWeatherType.CLEAR) 1f else if (weather == DesktopRailWeatherType.CLOUDY) 0.82f else 0.55f
    val st = easeInOutCubic(sunT)
    val sunX = if (compact) lerpF(w * 0.50f, w * 0.22f, st) else lerpF(26.dp.toPx(), 64.dp.toPx(), st)
    val sunY = if (compact) lerpF(h * 0.50f, h + 14.dp.toPx(), st) else lerpF(h * 0.50f, h + 18.dp.toPx(), st) - sin(st * PI).toFloat() * 14.dp.toPx()

    if (p > 0.02f && p < 0.52f) {
        for (step in 3 downTo 1) {
            val tr = easeInOutCubic(((p - step * 0.045f) / 0.55f).coerceIn(0f, 1f))
            val tx = if (compact) lerpF(w * 0.50f, w * 0.22f, tr) else lerpF(26.dp.toPx(), 64.dp.toPx(), tr)
            val ty = if (compact) lerpF(h * 0.50f, h + 14.dp.toPx(), tr) else lerpF(h * 0.50f, h + 18.dp.toPx(), tr) - sin(tr * PI).toFloat() * 14.dp.toPx()
            drawCircle(color = Color(0xFFFFD25A).copy(alpha = 0.14f * (4 - step) * dim), radius = sunR * 0.85f, center = Offset(tx, ty))
        }
    }
    drawCircle(
        brush = Brush.radialGradient(colors = listOf(Color(0xFFFFEEA0).copy(alpha = 0.80f * dim), Color.Transparent), center = Offset(sunX, sunY), radius = sunR * 2.5f),
        radius = sunR * 2.5f,
        center = Offset(sunX, sunY),
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFFFFBEB).copy(alpha = dim), Color(0xFFFFD447).copy(alpha = dim), Color(0xFFFF9914).copy(alpha = dim)),
            center = Offset(sunX - sunR * 0.25f, sunY - sunR * 0.25f),
            radius = sunR * 1.1f,
        ),
        radius = sunR,
        center = Offset(sunX, sunY),
    )
}

private fun DrawScope.drawMoonArc(p: Float, weather: DesktopRailWeatherType, compact: Boolean) {
    val moonRaw = ((p - 0.25f) / 0.75f).coerceIn(0f, 1f)
    if (moonRaw <= 0.01f || weather == DesktopRailWeatherType.STORM) return
    val w = size.width
    val h = size.height
    val mt = easeOutBack(moonRaw)
    val moonX = lerpF(if (compact) w * 0.78f else w - 58.dp.toPx(), if (compact) w * 0.50f else w - 28.dp.toPx(), moonRaw)
    val moonY = lerpF(h + 18.dp.toPx(), h * 0.50f, mt)
    val moonR = (if (compact) 8.5f else 10.5f).dp.toPx()
    val dim = if (weather == DesktopRailWeatherType.CLEAR) 1f else if (weather == DesktopRailWeatherType.CLOUDY) 0.8f else 0.55f

    drawCircle(
        brush = Brush.radialGradient(colors = listOf(Color(0xFFB4DAFF).copy(alpha = 0.45f * dim), Color.Transparent), center = Offset(moonX, moonY), radius = moonR * 2.3f),
        radius = moonR * 2.3f,
        center = Offset(moonX, moonY),
    )

    val layerRect = Rect(moonX - moonR * 1.5f, moonY - moonR * 1.5f, moonX + moonR * 1.5f, moonY + moonR * 1.5f)
    drawContext.canvas.saveLayer(layerRect, Paint())
    rotate(degrees = lerpF(-30f, -10f, moonRaw), pivot = Offset(moonX, moonY)) {
        drawCircle(
            brush = Brush.linearGradient(colors = listOf(Color.White.copy(alpha = dim), Color(0xFF8CC0FF).copy(alpha = dim)), start = Offset(moonX - moonR, moonY - moonR), end = Offset(moonX + moonR, moonY + moonR)),
            radius = moonR,
            center = Offset(moonX, moonY),
        )
        drawCircle(color = Color.Black, radius = moonR * 0.85f, center = Offset(moonX + moonR * 0.48f, moonY - moonR * 0.28f), blendMode = BlendMode.DstOut)
    }
    drawContext.canvas.restore()
}

private fun DrawScope.drawWeatherAtmosphere(
    p: Float,
    weather: DesktopRailWeatherType,
    timeSec: Float,
    hovered: Boolean,
    compact: Boolean,
) {
    val w = size.width
    val h = size.height
    val drift = sin(timeSec * 1.1f) * 4.dp.toPx()

    if (weather == DesktopRailWeatherType.CLEAR && !compact) {
        val cloudVis = 1f - (p / 0.55f).coerceIn(0f, 1f)
        if (cloudVis > 0.02f) {
            drawCloud(Offset(w * 0.34f + drift, h * 0.88f + (1f - cloudVis) * 18.dp.toPx()), 1.0f, Color.White.copy(alpha = 0.85f * cloudVis))
        }
    } else if (weather == DesktopRailWeatherType.CLOUDY || weather == DesktopRailWeatherType.RAIN || weather == DesktopRailWeatherType.STORM) {
        val cloudCol = if (weather == DesktopRailWeatherType.STORM) {
            lerp(Color(0xFF58607E), Color(0xFF1E223A), p).copy(alpha = 0.90f)
        } else {
            lerp(Color(0xFFFAFCFF), Color(0xFF404E72), p).copy(alpha = 0.78f)
        }
        if (!compact) {
            drawCloud(Offset(18.dp.toPx() + drift * 0.8f, 11.dp.toPx()), 1.15f, cloudCol)
            drawCloud(Offset(w * 0.45f - drift * 0.6f, 8.dp.toPx()), 1.25f, cloudCol)
            drawCloud(Offset(w * 0.78f + drift * 0.5f, 13.dp.toPx()), 1.0f, cloudCol)
        } else {
            drawCloud(Offset(8.dp.toPx(), 12.dp.toPx()), 0.9f, cloudCol)
        }
    }

    if (weather == DesktopRailWeatherType.RAIN || weather == DesktopRailWeatherType.STORM) {
        if (!compact) {
            GlassDroplets.forEach { dr ->
                val center = Offset(dr.xf * w, dr.yf * h)
                val r = dr.rDp.dp.toPx()
                drawCircle(color = Color(0xFF000A19).copy(alpha = 0.22f), radius = r, center = center + Offset(0.6f, 0.8f))
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.85f), Color(0xFFB9DCFF).copy(alpha = 0.30f), Color(0xFF78A5DC).copy(alpha = 0.45f)),
                        center = center - Offset(r * 0.3f, r * 0.3f),
                        radius = r * 1.1f,
                    ),
                    radius = r,
                    center = center,
                )
            }
        }
        if (hovered || (timeSec > 0f && (timeSec % 4.0f) < 1.2f)) {
            val activeCount = if (weather == DesktopRailWeatherType.STORM) 9 else 6
            val maxDropX = if (compact) w else w * 0.60f
            val wind = if (weather == DesktopRailWeatherType.STORM) -2.2.dp.toPx() else -1.0.dp.toPx()
            val col = if (p > 0.5f) Color(0xFFAAD7FF) else Color.White
            for (i in 0 until activeCount) {
                val fd = FineDrops[i]
                val dy = ((fd.ySeed + timeSec * fd.speed) % 1f) * h
                val dx = 8.dp.toPx() + fd.xSeed * (maxDropX - 14.dp.toPx())
                val fade = sin((dy / h) * PI).toFloat().coerceAtLeast(0f)
                drawLine(
                    color = col.copy(alpha = 0.45f * fade),
                    start = Offset(dx, dy),
                    end = Offset(dx + wind, dy + fd.sizeDp.dp.toPx()),
                    strokeWidth = 0.8.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
    }

    if (weather == DesktopRailWeatherType.SNOW) {
        val count = if (compact) 8 else SnowFlakes.size
        for (i in 0 until count) {
            val sf = SnowFlakes[i]
            val sy = ((sf.ySeed + timeSec * sf.speed) % 1f) * h
            val sx = ((sf.xSeed * w) + sin(timeSec * 2f + i) * 5.dp.toPx() + w) % w
            drawCircle(color = Color.White.copy(alpha = 0.82f), radius = sf.sizeDp.dp.toPx(), center = Offset(sx, sy))
        }
        drawRect(
            brush = Brush.verticalGradient(colors = listOf(Color.Transparent, Color(0xFFF5FAFF).copy(alpha = 0.50f)), startY = h - 6.dp.toPx(), endY = h),
            topLeft = Offset(0f, h - 6.dp.toPx()),
            size = Size(w, 6.dp.toPx()),
        )
    }
}

private fun DrawScope.drawSweepAndSparks(p: Float, sweep: Float, compact: Boolean) {
    if (sweep >= 0.99f) return
    val w = size.width
    val h = size.height
    val sx = lerpF(-w * 0.35f, w * 1.25f, sweep)
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.48f * (1f - sweep)), Color.Transparent),
            start = Offset(sx - 24.dp.toPx(), 0f),
            end = Offset(sx + 24.dp.toPx(), h),
        ),
    )
    val cx = if (p > 0.5f) (if (compact) w * 0.50f else w - 28.dp.toPx()) else (if (compact) w * 0.50f else 26.dp.toPx())
    val cy = h * 0.5f
    val sparkAlpha = (1f - sweep) * 0.9f
    val sparkCol = if (p > 0.5f) Color(0xFFB4DCFF) else Color(0xFFFFF0A0)
    BtnSparks.forEach { (ang, distDp, sizeDp) ->
        val r = lerpF(4.dp.toPx(), distDp.dp.toPx(), sweep)
        drawCircle(
            color = sparkCol.copy(alpha = sparkAlpha),
            radius = sizeDp.dp.toPx() * (1f - sweep * 0.4f),
            center = Offset(cx + cos(ang) * r, cy + sin(ang) * r),
        )
    }
}

private fun DrawScope.drawCloud(pos: Offset, scale: Float, color: Color) {
    val s = scale * 1.dp.toPx()
    drawCircle(color = color, radius = 9f * s, center = pos)
    drawCircle(color = color, radius = 11.5f * s, center = pos + Offset(10f * s, -3.5f * s))
    drawCircle(color = color, radius = 8.5f * s, center = pos + Offset(21f * s, 0.5f * s))
}

private fun skyStops(p: Float, weather: DesktopRailWeatherType): Triple<Color, Color, Color> {
    val palette = when (weather) {
        DesktopRailWeatherType.CLEAR -> listOf(
            Color(0xFF2DA5F5), Color(0xFF76CEFC), Color(0xFFE4F6FF),
            Color(0xFF0A0F24), Color(0xFF142048), Color(0xFF231C4A),
        )
        DesktopRailWeatherType.CLOUDY -> listOf(
            Color(0xFF6694C0), Color(0xFF9ABADA), Color(0xFFD4E2F0),
            Color(0xFF12182A), Color(0xFF202A44), Color(0xFF2E3650),
        )
        DesktopRailWeatherType.RAIN -> listOf(
            Color(0xFF406285), Color(0xFF6284A6), Color(0xFF94AEC6),
            Color(0xFF0B1222), Color(0xFF13223E), Color(0xFF1C3454),
        )
        DesktopRailWeatherType.STORM -> listOf(
            Color(0xFF2C344E), Color(0xFF424A68), Color(0xFF666C88),
            Color(0xFF090A16), Color(0xFF161430), Color(0xFF281A46),
        )
        DesktopRailWeatherType.SNOW -> listOf(
            Color(0xFF7CB2E0), Color(0xFFB2D6F2), Color(0xFFEBF5FF),
            Color(0xFF0E1830), Color(0xFF1A2A4E), Color(0xFF30426C),
        )
    }
    val tTop = Color(0xFF38276D)
    val tMid = Color(0xFFA85484)
    val tBot = Color(0xFFEE945A)
    return if (p < 0.5f) {
        val k = p * 2f
        Triple(lerp(palette[0], tTop, k), lerp(palette[1], tMid, k), lerp(palette[2], tBot, k))
    } else {
        val k = (p - 0.5f) * 2f
        Triple(lerp(tTop, palette[3], k), lerp(tMid, palette[4], k), lerp(tBot, palette[5], k))
    }
}

private fun lerpF(a: Float, b: Float, t: Float): Float = a + (b - a) * t
private fun easeInOutCubic(t: Float): Float = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3f) / 2f
private fun easeOutBack(t: Float): Float {
    val c1 = 1.65f
    val c3 = c1 + 1f
    return 1f + c3 * (t - 1f).pow(3f) + c1 * (t - 1f).pow(2f)
}
