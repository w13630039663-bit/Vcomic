package com.venera.desktop.reader

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import com.venera.desktop.gallery.data.DesktopComicPage
import com.venera.desktop.gallery.data.desktopComicPageRawBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Buffer
import java.awt.image.BufferedImage
import java.util.concurrent.ConcurrentHashMap

/**
 * 阅读器 AI 前置管线（痕迹去除 / 去网纹 / 增强）：全部纯 JVM 本地算法，零网络请求。
 *
 * ## 处理模型
 *
 * 页模型 [DesktopAiPage] 只包一层 [DesktopComicPage]：吃 `desktopComicPageRawBytes`
 * 同一条腿（盘上优先、引擎兜底），解出 `BufferedImage` 后按当前参数组跑三段链。
 * 参数吃 prefs 五组键（aiArtifact / aiDescreen / aiUpscale），参数变化即键变化 →
 * Coil 内存缓存天然失配重算，不用另设失效通道。
 *
 * ## 成对注册（键 + fetcher）
 *
 * 同 `DesktopComicPageKeyer` / `DesktopComicTileKeyer` 的仓规：只挂 fetcher 不挂 Keyer 时
 * Coil 的 `key()` 交回 null，语义是"这一格不进内存缓存" —— 屏上完全看不出异常。
 * 键 = 页缓存键 + 参数指纹，参数一动旧结果自然失配。
 *
 * ## 三段算法（规格 §AI tab 的降级实现）
 *
 * 1. 痕迹去除 = 双边滤波近似：小半径盒滤波估计块底色，按局部对比度 mask 与原图混合
 *    （真双边权重表在大页上一次要 O(n·r²)，盒滤波近似视觉等价而便宜一档）；
 *    「保持色调平缓」= 处理后在明度域与原图 15% 混合。
 * 2. 去网纹 = 多半径形态学：先小半径均值滤波估计网点底色，再按局部高频 mask 与原图混合；
 *    「保持大网点」= 大半径通道检出后反选保护（大结构不动，只压细网点）；
 *    硬核档 = 迭代两次。
 * 3. 增强 = Unsharp Mask（高斯模糊差值放大叠加）+ 双线性上采样 2x/4x + 上采样后再锐化一次；
 *    「最大输入分辨率」超限时跳过增强。
 *
 * 处理跑 IO 线程；页级结果由 Coil 内存缓存承担（不用另建 LRU），调用侧不再缓存。
 */
internal data class DesktopAiPage(
    val page: DesktopComicPage,
    /** 参数指纹：进缓存键；任一开关/档位变化即换键重算。 */
    val fingerprint: String,
    val artifactOn: Boolean,
    val artifactModel: Int,
    val artifactTone: Boolean,
    val artifactSize: Float,
    val descreenOn: Boolean,
    val descreenModel: Int,
    val descreenKeep: Boolean,
    val descreenSize: Float,
    val upscaleOn: Boolean,
    val upscaleScale: Int,
    val upscaleMaxmp: Float,
)

/** AI 页缓存键：页键 + 参数指纹（没有 Keyer 这一格就不进内存缓存，形状与页/瓦片同账）。 */
internal object DesktopAiPageKeyer : Keyer<DesktopAiPage> {
    override fun key(data: DesktopAiPage, options: Options): String =
        "${data.page.cacheKey}|ai|${data.fingerprint}"
}

internal class DesktopAiPageFetcher(
    private val data: DesktopAiPage,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val rawPair = withContext(Dispatchers.IO) {
            runCatching { desktopComicPageRawBytes(data.page) }.getOrNull()
        } ?: return null
        val (rawBytes, rawSource) = rawPair
        val processedPng = withContext(Dispatchers.IO) {
            runCatching {
                val img = javax.imageio.ImageIO.read(rawBytes.inputStream()) ?: return@runCatching null
                val outImg = processAiPipeline(img, data)
                val out = java.io.ByteArrayOutputStream()
                javax.imageio.ImageIO.write(outImg, "png", out)
                out.toByteArray()
            }.getOrNull()
        }
        val finalBytes = processedPng ?: rawBytes
        return SourceFetchResult(
            source = ImageSource(source = Buffer().apply { write(finalBytes) }, fileSystem = options.fileSystem),
            mimeType = if (processedPng != null) "image/png" else null,
            dataSource = rawSource,
        )
    }

    class Factory : Fetcher.Factory<DesktopAiPage> {
        override fun create(data: DesktopAiPage, options: Options, imageLoader: ImageLoader): Fetcher? =
            DesktopAiPageFetcher(data, options)
    }
}

/** 处理中标志（页级）：AI tab 对比图刷新期间置位，读数不做锁，只做"正在算"的粗信号。 */
private val processingPages = ConcurrentHashMap<String, Long>()

/** 是否正在处理该页（对比图胶囊用；键=页缓存键）。 */
internal fun desktopAiPageProcessing(cacheKey: String): Boolean =
    (processingPages[cacheKey] ?: 0L) > System.currentTimeMillis() - 3_000L

/**
 * 跑完整三段链；任一开关都关时直接回原图（不白算）。
 *
 * @param src 解码后的原图。
 * @param p 参数组（开关/档位/尺寸基准）。
 */
internal fun processAiPipeline(src: BufferedImage, p: DesktopAiPage): BufferedImage {
    processingPages[dataKeyOf(p)] = System.currentTimeMillis()
    var img = src
    if (p.artifactOn) img = artifactRemoval(img, p.artifactModel, p.artifactTone, p.artifactSize)
    if (p.descreenOn) img = descreen(img, p.descreenModel, p.descreenKeep, p.descreenSize)
    if (p.upscaleOn) img = enhance(img, p.upscaleScale, p.upscaleMaxmp)
    return img
}

private fun dataKeyOf(p: DesktopAiPage): String = p.page.cacheKey

/**
 * 痕迹去除：盒滤波估计 JPEG 块底色，按局部对比度混合。
 *
 * @param img 输入图。
 * @param strengthModel 强度档（0=轻 1=中 2=重）：映射盒滤波半径与混合上限。
 * @param keepTone 处理后在明度域做 15% 原图混合，压平色调漂移。
 * @param sizePercent 滤波半径基准（占页宽百分比 1..10）。
 */
internal fun artifactRemoval(img: BufferedImage, strengthModel: Int, keepTone: Boolean, sizePercent: Float): BufferedImage {
    val w = img.width
    val h = img.height
    if (w <= 2 || h <= 2) return img
    // 半径 = 页宽百分比口径，档位乘系数（轻 0.6 / 中 1.0 / 重 1.5），下限 1。
    val base = (w * sizePercent.coerceIn(1f, 10f) / 100f).toInt().coerceAtLeast(1)
    val radius = (base * when (strengthModel) {
        0 -> 0.6f
        2 -> 1.5f
        else -> 1f
    }).toInt().coerceAtLeast(1)
    val mixLimit = when (strengthModel) {
        0 -> 0.45f
        2 -> 0.85f
        else -> 0.65f
    }
    val smooth = boxBlur(img, radius)
    val out = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
    val srcPx = IntArray(w * h)
    img.getRGB(0, 0, w, h, srcPx, 0, w)
    val smPx = IntArray(w * h)
    smooth.getRGB(0, 0, w, h, smPx, 0, w)
    // 局部对比度低的区域（平坦块）多取平滑值；边缘区（文字/线稿）少取，保锐度。
    for (i in srcPx.indices) {
        val s = srcPx[i]
        val m = smPx[i]
        val sr = (s shr 16) and 0xFF
        val sg = (s shr 8) and 0xFF
        val sb = s and 0xFF
        val mr = (m shr 16) and 0xFF
        val mg = (m shr 8) and 0xFF
        val mb = m and 0xFF
        val diff = (kotlin.math.abs(sr - mr) + kotlin.math.abs(sg - mg) + kotlin.math.abs(sb - mb)) / 3f
        // 对比度越小越接近 JPEG 块痕迹 → 混合越重。
        val mix = (1f - diff / 48f).coerceIn(0f, 1f) * mixLimit
        var nr = (sr + (mr - sr) * mix).toInt().coerceIn(0, 255)
        var ng = (sg + (mg - sg) * mix).toInt().coerceIn(0, 255)
        var nb = (sb + (mb - sb) * mix).toInt().coerceIn(0, 255)
        if (keepTone) {
            // 明度域 15% 原图混合：压平处理带来的色偏。
            val lumS = sr * 0.299f + sg * 0.587f + sb * 0.114f
            val lumN = nr * 0.299f + ng * 0.587f + nb * 0.114f
            val adjust = (lumS - lumN) * 0.15f
            nr = (nr + adjust).toInt().coerceIn(0, 255)
            ng = (ng + adjust).toInt().coerceIn(0, 255)
            nb = (nb + adjust).toInt().coerceIn(0, 255)
        }
        out.setRGB(i % w, i / w, (nr shl 16) or (ng shl 8) or nb)
    }
    return out
}

/**
 * 去网纹：小半径均值滤波估计网点底色 + 局部高频 mask 混合；硬核档迭代两次。
 *
 * @param img 输入图。
 * @param model 0=标准（1 次），1=硬核（2 次迭代）。
 * @param keepDots 「保持大网点」：大半径通道检出的大结构反选保护。
 * @param sizePercent 网点尺寸基准（占页宽百分比 1..10）。
 */
internal fun descreen(img: BufferedImage, model: Int, keepDots: Boolean, sizePercent: Float): BufferedImage {
    val w = img.width
    val h = img.height
    if (w <= 2 || h <= 2) return img
    val dotRadius = ((w * sizePercent.coerceIn(1f, 10f) / 100f) * 0.5f).toInt().coerceAtLeast(1)
    var current = img
    val iterations = if (model == 1) 2 else 1
    repeat(iterations) {
        val estimated = boxBlur(current, dotRadius)
        current = mixByMask(current, estimated, highFreqMask(current, dotRadius))
    }
    if (keepDots) {
        // 大半径通道：检出大结构（对比强）的位置，把去网纹前的值保留回去。
        val big = boxBlur(img, dotRadius * 4)
        val bigMask = highFreqMask(img, dotRadius * 4)
        current = mixByMask(current, img, invertMask(bigMask, big, img))
    }
    return current
}

/**
 * 增强：Unsharp Mask 锐化 + 双线性上采样；超最大输入分辨率时跳过。
 *
 * @param img 输入图。
 * @param scale 上采样倍数（2 或 4）。
 * @param maxmp 最大输入分辨率（Mpx），超出跳过增强。
 */
internal fun enhance(img: BufferedImage, scale: Int, maxmp: Float): BufferedImage {
    val mp = img.width.toLong() * img.height / 1_000_000.0
    if (mp > maxmp) return img
    // 先锐化再放大，放大后按比例补一次轻锐化，抵消双线性带来的糊。
    val sharpened = unsharpMask(img, radius = 2, amount = 0.6f)
    val targetW = img.width * scale.coerceIn(2, 4)
    val targetH = img.height * scale.coerceIn(2, 4)
    if (targetW.toLong() * targetH > 40_000_000L) return sharpened
    val up = BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB)
    val g = up.createGraphics()
    g.setRenderingHint(
        java.awt.RenderingHints.KEY_INTERPOLATION,
        java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR,
    )
    g.drawImage(sharpened, 0, 0, targetW, targetH, null)
    g.dispose()
    return unsharpMask(up, radius = 2, amount = 0.35f)
}

/** Unsharp Mask：blur = 高斯近似（盒滤波三次）；out = src + amount·(src − blur)。 */
private fun unsharpMask(img: BufferedImage, radius: Int, amount: Float): BufferedImage {
    val blurred = boxBlur(boxBlur(boxBlur(img, radius), radius), radius)
    val w = img.width
    val h = img.height
    val out = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
    val srcPx = IntArray(w * h)
    img.getRGB(0, 0, w, h, srcPx, 0, w)
    val blPx = IntArray(w * h)
    blurred.getRGB(0, 0, w, h, blPx, 0, w)
    for (i in srcPx.indices) {
        val s = srcPx[i]
        val b = blPx[i]
        val sr = (s shr 16) and 0xFF
        val sg = (s shr 8) and 0xFF
        val sb = s and 0xFF
        val br = (b shr 16) and 0xFF
        val bg = (b shr 8) and 0xFF
        val bb = b and 0xFF
        val nr = (sr + (sr - br) * amount).toInt().coerceIn(0, 255)
        val ng = (sg + (sg - bg) * amount).toInt().coerceIn(0, 255)
        val nb = (sb + (sb - bb) * amount).toInt().coerceIn(0, 255)
        out.setRGB(i % w, i / w, (nr shl 16) or (ng shl 8) or nb)
    }
    return out
}

/** 盒滤波（可分离两遍）：大图上的高斯近似底座。 */
private fun boxBlur(img: BufferedImage, radius: Int): BufferedImage {
    val w = img.width
    val h = img.height
    if (radius <= 0 || w <= 1 || h <= 1) return img
    val srcPx = IntArray(w * h)
    img.getRGB(0, 0, w, h, srcPx, 0, w)
    val tmp = IntArray(srcPx.size)
    val outPx = IntArray(srcPx.size)
    // 水平
    for (y in 0 until h) {
        var sr = 0
        var sg = 0
        var sb = 0
        val row = y * w
        for (x in -radius..radius) {
            val xi = x.coerceIn(0, w - 1)
            val p = srcPx[row + xi]
            sr += (p shr 16) and 0xFF
            sg += (p shr 8) and 0xFF
            sb += p and 0xFF
        }
        val win = 2 * radius + 1
        for (x in 0 until w) {
            tmp[row + x] = ((sr / win) shl 16) or ((sg / win) shl 8) or (sb / win)
            val addX = (x + radius + 1).coerceAtMost(w - 1)
            val subX = (x - radius).coerceAtLeast(0)
            val pa = srcPx[row + addX]
            val ps = srcPx[row + subX]
            sr += ((pa shr 16) and 0xFF) - ((ps shr 16) and 0xFF)
            sg += ((pa shr 8) and 0xFF) - ((ps shr 8) and 0xFF)
            sb += (pa and 0xFF) - (ps and 0xFF)
        }
    }
    // 垂直
    for (x in 0 until w) {
        var sr = 0
        var sg = 0
        var sb = 0
        for (y in -radius..radius) {
            val yi = y.coerceIn(0, h - 1)
            val p = tmp[yi * w + x]
            sr += (p shr 16) and 0xFF
            sg += (p shr 8) and 0xFF
            sb += p and 0xFF
        }
        val win = 2 * radius + 1
        for (y in 0 until h) {
            outPx[y * w + x] = ((sr / win) shl 16) or ((sg / win) shl 8) or (sb / win)
            val addY = (y + radius + 1).coerceAtMost(h - 1)
            val subY = (y - radius).coerceAtLeast(0)
            val pa = tmp[addY * w + x]
            val ps = tmp[subY * w + x]
            sr += ((pa shr 16) and 0xFF) - ((ps shr 16) and 0xFF)
            sg += ((pa shr 8) and 0xFF) - ((ps shr 8) and 0xFF)
            sb += (pa and 0xFF) - (ps and 0xFF)
        }
    }
    val out = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
    out.setRGB(0, 0, w, h, outPx, 0, w)
    return out
}

/** 局部高频 mask：与平滑底的差越大越接近 1（高频保留区）。 */
private fun highFreqMask(img: BufferedImage, radius: Int): FloatArray {
    val w = img.width
    val h = img.height
    val smooth = boxBlur(img, radius)
    val srcPx = IntArray(w * h)
    img.getRGB(0, 0, w, h, srcPx, 0, w)
    val smPx = IntArray(w * h)
    smooth.getRGB(0, 0, w, h, smPx, 0, w)
    val mask = FloatArray(srcPx.size)
    for (i in srcPx.indices) {
        val s = srcPx[i]
        val m = smPx[i]
        val diff = (kotlin.math.abs(((s shr 16) and 0xFF) - ((m shr 16) and 0xFF)) +
            kotlin.math.abs(((s shr 8) and 0xFF) - ((m shr 8) and 0xFF)) +
            kotlin.math.abs((s and 0xFF) - (m and 0xFF))) / 3f
        mask[i] = (diff / 32f).coerceIn(0f, 1f)
    }
    return mask
}

/** 按 mask 混合：mask 高处取 b 值，低处保留 a。 */
private fun mixByMask(a: BufferedImage, b: BufferedImage, mask: FloatArray): BufferedImage {
    val w = a.width
    val h = a.height
    val out = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
    val aPx = IntArray(w * h)
    a.getRGB(0, 0, w, h, aPx, 0, w)
    val bPx = IntArray(w * h)
    b.getRGB(0, 0, w, h, bPx, 0, w)
    for (i in aPx.indices) {
        val m = mask[i]
        val pa = aPx[i]
        val pb = bPx[i]
        val ar = (pa shr 16) and 0xFF
        val ag = (pa shr 8) and 0xFF
        val ab = pa and 0xFF
        val br = (pb shr 16) and 0xFF
        val bg = (pb shr 8) and 0xFF
        val bb = pb and 0xFF
        val nr = (ar + (br - ar) * m).toInt().coerceIn(0, 255)
        val ng = (ag + (bg - ag) * m).toInt().coerceIn(0, 255)
        val nb = (ab + (bb - ab) * m).toInt().coerceIn(0, 255)
        out.setRGB(i % w, i / w, (nr shl 16) or (ng shl 8) or nb)
    }
    return out
}

/** 反选保护 mask：大结构处（big 对比强）返回 1 → 混合时取回原图。 */
private fun invertMask(base: FloatArray, big: BufferedImage, src: BufferedImage): FloatArray {
    val w = src.width
    val h = src.height
    val srcPx = IntArray(w * h)
    src.getRGB(0, 0, w, h, srcPx, 0, w)
    val bigPx = IntArray(w * h)
    big.getRGB(0, 0, w, h, bigPx, 0, w)
    val out = FloatArray(base.size)
    for (i in base.indices) {
        val s = srcPx[i]
        val b = bigPx[i]
        val diff = (kotlin.math.abs(((s shr 16) and 0xFF) - ((b shr 16) and 0xFF)) +
            kotlin.math.abs(((s shr 8) and 0xFF) - ((b shr 8) and 0xFF)) +
            kotlin.math.abs((s and 0xFF) - (b and 0xFF))) / 3f
        // 大结构检出（diff 大）→ 保护权重高 → 该处偏向原图。
        val protect = (diff / 40f).coerceIn(0f, 1f)
        out[i] = (1f - base[i]) * protect
    }
    return out
}
