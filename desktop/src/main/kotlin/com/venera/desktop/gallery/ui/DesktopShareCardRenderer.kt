package com.venera.desktop.gallery.ui

import java.awt.image.BufferedImage
import kotlin.math.roundToInt
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Color4f
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Data
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.Gradient
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.RRect
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Shader
import org.jetbrains.skia.Surface
import org.jetbrains.skia.paragraph.FontCollection
import org.jetbrains.skia.paragraph.ParagraphBuilder
import org.jetbrains.skia.paragraph.ParagraphStyle
import org.jetbrains.skia.paragraph.TextStyle
import org.jetbrains.skia.paragraph.TypefaceFontProvider

/**
 * 使用 Skia 离屏绘制分享卡片并返回 `TYPE_INT_RGB` 位图，保证剪贴板粘贴兼容无黑角锯齿。
 */
internal fun renderDesktopShareCardImage(
    spec: DesktopShareCardSpec,
    coverBytes: ByteArray,
    avatarBytes: ByteArray? = null,
): BufferedImage {
    Image.makeFromEncoded(coverBytes).use { coverImage ->
        val avatarImage = avatarBytes?.takeIf { it.isNotEmpty() }?.let { bytes ->
            runCatching { Image.makeFromEncoded(bytes) }.getOrNull()
        }
        try {
            return drawCardToBufferedImage(spec, coverImage, avatarImage)
        } finally {
            avatarImage?.close()
        }
    }
}

private fun drawCardToBufferedImage(
    spec: DesktopShareCardSpec,
    coverImage: Image,
    avatarImage: Image?,
): BufferedImage {
    val outerMargin = 20f
    val cardW = 520f
    val trayPad = 8f
    val coverW = cardW - trayPad * 2f
    val naturalAspect = if (coverImage.width > 0 && coverImage.height > 0) {
        coverImage.width.toFloat() / coverImage.height.toFloat()
    } else {
        spec.aspect
    }
    val coverH = (coverW / naturalAspect.coerceIn(0.55f, 1.85f)).roundToInt().coerceIn(260, 920).toFloat()
    val cardH = trayPad + coverH + 126f
    val totalW = (cardW + outerMargin * 2f).roundToInt()
    val totalH = (cardH + outerMargin * 2f).roundToInt()

    return Surface.makeRasterN32Premul(totalW, totalH).use { surface ->
        drawShareCardScene(
            canvas = surface.canvas,
            spec = spec,
            coverImage = coverImage,
            avatarImage = avatarImage,
            cardX = outerMargin,
            cardY = outerMargin,
            cardW = cardW,
            cardH = cardH,
            trayPad = trayPad,
            coverW = coverW,
            coverH = coverH,
        )
        surface.flushAndSubmit()
        surface.makeImageSnapshot().use { snapshot ->
            skiaImageToBufferedRgb(snapshot)
        }
    }
}

private fun drawShareCardScene(
    canvas: Canvas,
    spec: DesktopShareCardSpec,
    coverImage: Image,
    avatarImage: Image?,
    cardX: Float,
    cardY: Float,
    cardW: Float,
    cardH: Float,
    trayPad: Float,
    coverW: Float,
    coverH: Float,
) {
    val isDark = spec.isDark
    canvas.clear(if (isDark) 0xFF0E121B.toInt() else 0xFFF1F4F9.toInt())

    val cardRRect = RRect.makeXYWH(cardX, cardY, cardW, cardH, 28f)
    Paint().use { paint ->
        val topCol = if (isDark) 0xFF212B3C.toInt() else 0xFFFFFFFF.toInt()
        val botCol = if (isDark) 0xFF131823.toInt() else 0xFFF5F7FB.toInt()
        val grad = Gradient(Gradient.Colors(arrayOf(Color4f(topCol), Color4f(botCol)), tileMode = FilterTileMode.CLAMP))
        Shader.makeLinearGradient(cardX, cardY, cardX, cardY + cardH, grad).use { sh ->
            paint.shader = sh
            canvas.drawRRect(cardRRect, paint)
        }
    }
    drawStrokeRRect(canvas, cardRRect, 1.5f, if (isDark) 0x38FFFFFF else 0x2817202E)

    Paint().use { paint ->
        val shineW = cardW * 0.7f
        val shineX = cardX + (cardW - shineW) / 2f
        val shineColor = if (isDark) 0x57FFFFFF else 0xB3FFFFFF.toInt()
        val grad = Gradient(
            Gradient.Colors(
                arrayOf(Color4f(0x00FFFFFF), Color4f(shineColor), Color4f(0x00FFFFFF)),
                tileMode = FilterTileMode.CLAMP,
            ),
        )
        Shader.makeLinearGradient(shineX, cardY + 2f, shineX + shineW, cardY + 2f, grad).use { sh ->
            paint.shader = sh
            canvas.drawRect(Rect.makeXYWH(shineX, cardY + 1.5f, shineW, 2f), paint)
        }
    }

    val coverX = cardX + trayPad
    val coverY = cardY + trayPad
    val coverRRect = RRect.makeXYWH(coverX, coverY, coverW, coverH, 20f)
    Paint().use { paint ->
        paint.color = if (isDark) 0xFF1B2130.toInt() else 0xFFE6EBF2.toInt()
        canvas.drawRRect(coverRRect, paint)
    }

    canvas.save()
    canvas.clipRRect(coverRRect, true)
    drawCenterCroppedImage(canvas, coverImage, coverX, coverY, coverW, coverH, blurred = spec.masked)
    if (spec.masked) {
        Paint().use { maskPaint ->
            maskPaint.color = 0x550B0F17
            canvas.drawRect(Rect.makeXYWH(coverX, coverY, coverW, coverH), maskPaint)
        }
    }
    canvas.restore()
    drawStrokeRRect(canvas, coverRRect, 1.5f, if (isDark) 0x1AFFFFFF else 0x1A17202E)

    val fonts = shareFontCollection
    if (spec.pageCount > 1) {
        drawPill(canvas, fonts, coverX + 14f, coverY + 14f, "${spec.pageCount}P", 19f, 0xFFF2F5FA.toInt(), 0xC40A0D12.toInt(), 0x33FFFFFF, 10f)
    }

    val infoX = cardX + trayPad + 12f
    val infoW = cardW - (trayPad + 12f) * 2f
    val titleY = coverY + coverH + 20f
    drawSingleLineText(canvas, fonts, spec.title, infoX, titleY, infoW, 26f, if (isDark) 0xFFF3F6FB.toInt() else 0xFF18202C.toInt(), semiBold = true)

    val rowY = titleY + 44f
    val pillW = if (spec.sourceLabel.isNotBlank()) {
        val accent = if (isDark) 0xFF70ACFF.toInt() else 0xFF0A63F6.toInt()
        val bg = if (isDark) 0x226BA8FF else 0x1A0A63F6
        val border = if (isDark) 0x486BA8FF else 0x400A63F6
        val w = measurePillWidth(fonts, spec.sourceLabel, 18f)
        drawPill(canvas, fonts, infoX + infoW - w, rowY + 1f, spec.sourceLabel, 18f, accent, bg, border, 9f)
        w
    } else {
        0f
    }

    var artistX = infoX
    var artistMaxW = infoW - (if (pillW > 0f) pillW + 14f else 0f)
    if (avatarImage != null) {
        val avatarSize = 36f
        val avatarRRect = RRect.makeXYWH(artistX, rowY, avatarSize, avatarSize, avatarSize / 2f)
        canvas.save()
        canvas.clipRRect(avatarRRect, true)
        drawCenterCroppedImage(canvas, avatarImage, artistX, rowY, avatarSize, avatarSize, blurred = false)
        canvas.restore()
        drawStrokeRRect(canvas, avatarRRect, 1.5f, if (isDark) 0x40FFFFFF else 0x2617202E)
        artistX += avatarSize + 10f
        artistMaxW -= (avatarSize + 10f)
    }
    if (spec.artist.isNotBlank() && artistMaxW > 20f) {
        drawSingleLineText(
            canvas, fonts, spec.artist, artistX, rowY + 4f, artistMaxW, 23f,
            if (isDark) 0xFFA6B1C2.toInt() else 0xFF566376.toInt(), semiBold = false,
        )
    }
}

private fun drawStrokeRRect(canvas: Canvas, rrect: RRect, strokeWidth: Float, color: Int) {
    Paint().use { paint ->
        paint.mode = PaintMode.STROKE
        paint.strokeWidth = strokeWidth
        paint.color = color
        canvas.drawRRect(rrect, paint)
    }
}

private fun drawCenterCroppedImage(
    canvas: Canvas,
    image: Image,
    dstX: Float,
    dstY: Float,
    dstW: Float,
    dstH: Float,
    blurred: Boolean,
) {
    val iw = image.width.toFloat().coerceAtLeast(1f)
    val ih = image.height.toFloat().coerceAtLeast(1f)
    val scale = maxOf(dstW / iw, dstH / ih)
    val cropW = dstW / scale
    val cropH = dstH / scale
    val src = Rect.makeXYWH((iw - cropW) / 2f, (ih - cropH) / 2f, cropW, cropH)
    val dst = Rect.makeXYWH(dstX, dstY, dstW, dstH)
    Paint().use { paint ->
        if (blurred) {
            ImageFilter.makeBlur(30f, 30f, FilterTileMode.CLAMP).use { blur ->
                paint.imageFilter = blur
                canvas.drawImageRect(image, src, dst, SamplingMode.MITCHELL, paint, true)
            }
        } else {
            canvas.drawImageRect(image, src, dst, SamplingMode.MITCHELL, paint, true)
        }
    }
}

private fun measurePillWidth(fonts: FontCollection, label: String, fontSize: Float): Float =
    buildParagraph(fonts, label, fontSize, 0xFFFFFFFF.toInt(), semiBold = true, maxWidth = 160f).use { para ->
        para.maxIntrinsicWidth.coerceAtLeast(20f) + 20f
    }

private fun drawPill(
    canvas: Canvas,
    fonts: FontCollection,
    x: Float,
    y: Float,
    label: String,
    fontSize: Float,
    textColor: Int,
    bgColor: Int,
    borderColor: Int,
    radius: Float,
) {
    buildParagraph(fonts, label, fontSize, textColor, semiBold = true, maxWidth = 160f).use { para ->
        val w = para.maxIntrinsicWidth.coerceAtLeast(20f) + 20f
        val h = 34f
        val rrect = RRect.makeXYWH(x, y, w, h, radius)
        Paint().use { p ->
            p.color = bgColor
            canvas.drawRRect(rrect, p)
        }
        drawStrokeRRect(canvas, rrect, 1.5f, borderColor)
        para.paint(canvas, x + 10f, y + (h - para.height) / 2f)
    }
}

private fun drawSingleLineText(
    canvas: Canvas,
    fonts: FontCollection,
    text: String,
    x: Float,
    y: Float,
    maxWidth: Float,
    fontSize: Float,
    color: Int,
    semiBold: Boolean,
) {
    buildParagraph(fonts, text, fontSize, color, semiBold = semiBold, maxWidth = maxWidth).use { para ->
        para.paint(canvas, x, y)
    }
}

private fun buildParagraph(
    fonts: FontCollection,
    text: String,
    fontSize: Float,
    color: Int,
    semiBold: Boolean,
    maxWidth: Float,
) = ParagraphStyle().let { pStyle ->
    pStyle.maxLinesCount = 1
    pStyle.ellipsis = "…"
    val tStyle = TextStyle().apply {
        this.fontSize = fontSize
        this.color = color
        this.fontStyle = if (semiBold) FontStyle.BOLD else FontStyle.NORMAL
        this.fontFamilies = arrayOf("MiSans", "Microsoft YaHei UI", "Segoe UI", "Malgun Gothic", "Yu Gothic UI", "sans-serif")
    }
    ParagraphBuilder(pStyle, fonts).use { builder ->
        builder.pushStyle(tStyle)
        builder.addText(text)
        builder.popStyle()
        builder.build().apply { layout(maxWidth.coerceAtLeast(1f)) }
    }
}

private val shareFontCollection: FontCollection by lazy {
    FontCollection().apply {
        val defaultMgr = FontMgr.default
        setDefaultFontManager(defaultMgr)
        val provider = TypefaceFontProvider()
        val fontPaths = listOf(
            "/fonts/MiSans-Regular.ttf",
            "/fonts/MiSans-Medium.ttf",
            "/fonts/MiSans-SemiBold.ttf",
            "/fonts/MiSans-Bold.ttf",
        )
        var loadedAny = false
        for (path in fontPaths) {
            val bytes = DesktopShareCardSpec::class.java.getResourceAsStream(path)?.use { it.readBytes() }
                ?: continue
            runCatching {
                Data.makeFromBytes(bytes).use { data ->
                    defaultMgr.makeFromData(data)?.let { tf ->
                        provider.registerTypeface(tf, "MiSans")
                        loadedAny = true
                    }
                }
            }
        }
        if (loadedAny) {
            setAssetFontManager(provider)
        }
    }
}

private fun skiaImageToBufferedRgb(image: Image): BufferedImage {
    val w = image.width
    val h = image.height
    Bitmap().use { bitmap ->
        val info = ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.PREMUL)
        bitmap.allocPixels(info)
        check(image.readPixels(bitmap, 0, 0)) { "Skia readPixels failed" }
        val raw = bitmap.readPixels(info, w * 4, 0, 0) ?: error("Bitmap readPixels returned null")
        val pixels = IntArray(w * h)
        var i = 0
        var p = 0
        while (p < pixels.size) {
            val b = raw[i].toInt() and 0xFF
            val g = raw[i + 1].toInt() and 0xFF
            val r = raw[i + 2].toInt() and 0xFF
            pixels[p] = (r shl 16) or (g shl 8) or b
            i += 4
            p++
        }
        return BufferedImage(w, h, BufferedImage.TYPE_INT_RGB).apply {
            setRGB(0, 0, w, h, pixels, 0, w)
        }
    }
}
