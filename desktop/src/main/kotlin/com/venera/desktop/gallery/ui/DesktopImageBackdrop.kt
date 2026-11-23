package com.venera.desktop.gallery.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import coil3.compose.LocalPlatformContext
import com.venera.compose.ui.tokens.LocalVeneraDarkTheme
import com.venera.desktop.gallery.data.desktopCoilBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import org.jetbrains.skia.Image as SkiaImage

/**
 * 全页糊图背板（九调：一次烘焙）。
 *
 * ## 为什么不再用 `Modifier.blur`
 *
 * 八调是 `AsyncImage` 全窗原尺寸 `.blur(80.dp)`：`Modifier.blur` 是**每次绘制都重跑**的
 * RenderEffect，窗口只要出一帧（哪怕只是一枚小图标在转）就重糊一遍 1700×1200、σ≈110px。
 * 静置占用 8% GPU 的大头在这里。
 *
 * 现在改成**素材**：进页解码一张 1/8 采样图 → Skia 糊一次 → 存成 `ImageBitmap`，
 * 之后每帧只做一次贴图。缓存键 = 图 + 尺寸档 + 主题；换图 / 跨档 resize / 切深浅才重烘。
 *
 * 页底色只垫底，图片未加载时不会空；作品详情页与画廊详情页共用这一颗，改一处两处同时生效。
 */

/**
 * 糊图之上的**压暗**罩：把底图的亮部按下去，前景玻璃与文字才有对比。
 *
 * 九调之前是白罩（提亮 5–6%）—— 碰到浅色画（白底漫画页、粉调插画）时整窗发灰发亮，
 * 卡片的白玻璃与底图糊成一片（用户报「背板大图糊图需要压暗，现在太亮了」）。
 * 换黑罩之后亮画落回中灰，暗画不会被压死（画本身已按 [Image] 的 alpha 减过一半以上）。
 */
internal fun backdropVeilOf(dark: Boolean): Color =
    Color.Black.copy(alpha = if (dark) 0.34f else 0.24f)

/** 采样：窗口短边 / 这个数。8 ⇒ 1440 宽窗口只烘约 180px 的板。 */
private const val PLATE_DIVISOR = 8f

/** 烘焙结果的进程级缓存（LRU 8）。一张 200px 板约 110KB，八张不到 1MB。 */
private object DesktopBackdropPlates {
    private val cache = object : LinkedHashMap<String, ImageBitmap>(6, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?) =
            size > 8
    }

    @Synchronized
    fun get(key: String): ImageBitmap? = cache[key]

    @Synchronized
    fun put(key: String, bitmap: ImageBitmap) {
        cache[key] = bitmap
    }
}

/**
 * 解码 + 一次高斯 + 转成 `ImageBitmap`。
 *
 * 取样在 IO 线程，烘焙在 Default（纯 CPU、几十毫秒），不占 UI 线程。
 * 拿不到字节就交回 null —— 背板退回纯页底色，与"图还没到"是同一句话。
 */
private suspend fun bakeBackdropPlate(
    context: coil3.PlatformContext,
    url: String,
    targetPx: Int,
    sigma: Float,
): ImageBitmap? {
    val bytes = desktopCoilBytes(context, url) ?: return null
    return withContext(Dispatchers.Default) {
        runCatching {
            SkiaImage.makeFromEncoded(bytes).use { src ->
                val w = src.width
                val h = src.height
                if (w <= 0 || h <= 0) return@runCatching null
                // 按短边缩到目标尺寸：保留原图纵横比，之后 Crop 填满窗口不会变形。
                val scale = targetPx.toFloat() / minOf(w, h)
                val tw = (w * scale).toInt().coerceAtLeast(1)
                val th = (h * scale).toInt().coerceAtLeast(1)
                val surface = Surface.makeRasterN32Premul(tw, th)
                val paint = Paint().apply {
                    imageFilter = ImageFilter.makeBlur(sigma, sigma, FilterTileMode.CLAMP)
                    isAntiAlias = true
                }
                surface.canvas.drawImageRect(
                    src,
                    Rect.makeWH(tw.toFloat(), th.toFloat()),
                    paint,
                )
                surface.makeImageSnapshot().toComposeImageBitmap()
            }
        }.getOrNull()
    }
}

/**
 * 全页糊图背板。
 *
 * @param url 作品图地址，空串或 null 时只留页底色。
 * @param modifier 背板的外层修饰。
 */
@Composable
internal fun DesktopImageBackdrop(
    url: String?,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalVeneraDarkTheme.current
    val pageColor = if (isDark) Color(0xFF2C2C34) else Color(0xFFE9E9EE)
    val context = LocalPlatformContext.current
    BoxWithConstraints(modifier = modifier.background(pageColor)) {
        val density = LocalDensity.current
        val shortPx = with(density) { minOf(maxWidth, maxHeight).toPx() }
        // 尺寸档：短边每 160px 一档；跨档才重烘，拖动窗口途中不会一帧一次。
        val bucket = (shortPx / 160f).toInt().coerceAtLeast(1)
        val targetPx = (shortPx / PLATE_DIVISOR).toInt()
            .coerceIn(96, 320)
        // σ 按采样后的像素算：8 倍放大回屏上 ≈ 80dp 那一档的糊度。
        val sigma = targetPx / 14f
        val key = "$url|$bucket|$isDark"
        var plate by remember(key) { mutableStateOf(DesktopBackdropPlates.get(key)) }

        LaunchedEffect(key) {
            if (plate != null || url.isNullOrBlank()) return@LaunchedEffect
            val baked = bakeBackdropPlate(context, url, targetPx, sigma)
            if (baked != null) {
                DesktopBackdropPlates.put(key, baked)
                plate = baked
            }
        }

        val bitmap = plate
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    // 只做缩放/透明度，不再挂任何重绘效果。
                    // 亮度直接在这里减：暗皮 0.62、浅皮 0.46 —— 再叠一层黑罩（见下），
                    // 亮画不至于把整窗刷白，也给右栏玻璃留出层级差。
                    .graphicsLayer { alpha = if (isDark) 0.62f else 0.46f },
            )
            // 提亮罩与顶栏那份必须同档同色，两处差一档会在 y=46dp 露出横向分界。
            Box(Modifier.matchParentSize().background(backdropVeilOf(isDark)))
        }
    }
}
