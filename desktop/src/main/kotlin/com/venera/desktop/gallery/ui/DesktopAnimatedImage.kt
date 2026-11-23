package com.venera.desktop.gallery.ui

import com.venera.desktop.ui.theme.VeneraType
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.data.platform.PathProvider
import com.venera.desktop.gallery.data.DesktopPixivImageBytes
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage

/**
 * 桌面 booru **animated GIF** 就地逐帧播放（主人定案）。
 *
 * ## 为什么不走视频那条回调面
 * 之前把 `fileExt=gif` 塞进 libVLC 播放器，但 VLC 解 gif 走 image/preview demuxer，不稳
 * （真窗日志 `heif Read error … truncated` 就是它拿图像解器在乱 probe）。GIF 是标准多帧图，逐帧解即可。
 *
 * ## 只用 `javax.imageio` 一条路
 * JVM skiko `Codec.readPixels(bmp, i)` 对 gif 颜色类型转换**必抛** `Invalid conversion`（本仓实测），
 * 且 skiko 未透出 `skipFrame` 步进接口 → 无解。ImageIO 的 GIF reader 能稳出多帧 + 每帧 DelayTime，
 * 每帧独立容错；解码失败/非动图 → 交回 null 让父层静帧 `AsyncImage` 兜底，绝不黑屏。
 *
 * ## 加载中提示（主人十二轮补令）
 * 5MB gif 解码要 1~2s，其间在画面上盖一枚小胶囊（`○ 正在加载动图…`）+ 进度环，避免"点了没反应"。
 *
 * @param key 变化即重取重解（传卡片 uid）。
 * @param autoPlay 「动图逐帧播」档（`galleryAnimated != NEVER`）：false ⇒ 根本不解码不铺帧，静帧原样透出（省内存与 CPU）。
 */
@Composable
internal fun DesktopAnimatedImage(
    url: String,
    paths: PathProvider?,
    key: Any,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    paused: Boolean = false,
    autoPlay: Boolean = true,
    onError: () -> Unit,
) {
    // 「不自动播」档：整颗退化成不画（父层静帧 AsyncImage 透出），也不起解码协程。真设置生效，非假开关。
    if (!autoPlay) return

    val frames = remember(key) { AnimatedFramesState() }
    var frameIndex by remember(key) { mutableIntStateOf(0) }
    var loading by remember(key) { mutableStateOf(url.isNotBlank() && paths != null) }

    LaunchedEffect(key, url, paths) {
        val p = paths
        if (url.isBlank() || p == null) return@LaunchedEffect
        loading = true
        val result = runCatching {
            withContext(Dispatchers.IO) {
                // gif 逐帧不走共享 okhttp 盘缓存（Windows 并发写同键 .tmp 会 corrupt 刷屏）。
                val bytes = DesktopPixivImageBytes.fetchWithoutCache(p, url)
                decodeAnimatedFrames(bytes)
                    ?: error("ImageIO 拿不到多帧（可能非动图 / 数据损坏）bytes=${bytes.size}")
            }
        }
        result.exceptionOrNull()?.let {
            com.venera.desktop.platform.DesktopLogger.warn(
                "DesktopAnimatedImage",
                "gif 逐帧失败 url=$url 因=${it.javaClass.simpleName}: ${it.message}",
            )
        }
        val decoded = result.getOrNull()
        loading = false
        if (decoded == null) {
            frames.list = null
            onError()
        } else {
            frames.list = decoded.bitmaps
            frames.delays = decoded.delays
        }
    }

    // 逐帧时钟：有帧且未暂停时，按各帧 duration 回卷推进帧号。
    if (!paused && frames.list != null) {
        LaunchedEffect(frames.list) {
            val list = frames.list ?: return@LaunchedEffect
            val delays = frames.delays
            var i = 0
            while (isActive && list.isNotEmpty()) {
                delay(delays.getOrElse(i) { 80 }.toLong())
                i = (i + 1) % list.size
                frameIndex = i
            }
        }
    }

    Box(modifier = modifier) {
        frames.list?.let { list ->
            list.getOrNull(frameIndex % list.size)?.let { bmp ->
                Image(
                    bitmap = bmp,
                    contentDescription = null,
                    contentScale = contentScale,
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
        if (loading) {
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xCC0C1218))
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircularProgressIndicator(color = Color(0xFF8B7DF7), strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                Text(
                    text = "正在加载动图…",
                    color = Color.White,
                    fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                    fontWeight = VeneraType.control.weight,
                )
            }
        }
    }
}

/** 解码产物（帧位图 + 每帧延时）。 */
private class FramesResult(val bitmaps: List<ImageBitmap>, val delays: IntArray)

/** 解码结果的可变持有（跨协程写、组合读）。 */
private class AnimatedFramesState {
    var list: List<ImageBitmap>? by mutableStateOf(null)
    var delays: IntArray = IntArray(0)
}

/**
 * 逐帧解 animated gif：`javax.imageio` 的 GIF reader 一条路。
 *
 * **不走 skia `Codec`**：JVM skiko 的 `Codec.readPixels(bmp, i)` 对 gif 颜色类型转换**必抛**
 * `IllegalArgumentException: Invalid conversion`（skiko 未透出 `skipFrame` 步进接口，本仓实测）。
 * ImageIO 的 GIF reader 能稳出多帧 + 每帧 delay，且**每帧独立容错**：单帧读失败不废整段。
 * IO 线程调用。
 */
/** 逐帧解 animated gif 的 OOM 护栏（A 批）：最多快照帧数与快照总字节预算。合成逐帧推进，但只按步进存快照。 */
private const val GIF_MAX_FRAMES = 220
private const val GIF_MEMORY_BUDGET_BYTES = 160L * 1024 * 1024 // 快照像素预算 ~160MB

private fun decodeAnimatedFrames(bytes: ByteArray): FramesResult? = decodeViaImageIo(bytes)

/**
 * `javax.imageio` GIF 逐帧 + **帧合成**（booru 动图主力）。
 *
 * JDK 的 GIF reader 只交**每帧自己的子图**（带偏移、可含透明像素），不做帧间合成——
 * 直接摆就是"子图 ↔ 全图"来回闪。真解 = 标准 GIF 合成：
 *  1. 从 stream 元数据拿 logical screen size 建一张 persistent canvas（TYPE_INT_ARGB）；
 *  2. 每帧：先执行**上一帧声明的 disposal**（2=擦回背景、3=还原快照、0/1=留着），
 *     再按本帧 `ImageDescriptor@LeftPosition/TopPosition` 把子图 drawImage 到 canvas 上（子图透明像素由
 *     reader 已带 alpha=0，SrcOver 会露出下面旧内容 —— 正是我们想要的"局部更新"）；
 *  3. 每帧把 canvas **深拷贝**成 ImageBitmap 存进列表（画布之后还会继续变）。
 *
 * **每帧独立容错**：单帧读/合成失败不废整段，凑够 ≥2 帧即成；两数不够 ⇒ 退静帧兜底。
 */
private fun decodeViaImageIo(bytes: ByteArray): FramesResult? {
    val r = runCatching<FramesResult?> {
        val reader: javax.imageio.ImageReader =
            ImageIO.getImageReadersByFormatName("gif").asSequence().firstOrNull()
                ?: error("JVM 未注册 gif reader")
        javax.imageio.stream.MemoryCacheImageInputStream(bytes.inputStream()).use { ins ->
            // ignoreMetadata=false：否则逐帧 getImageMetadata 拿不到 DelayTime/ImageDescriptor。
            reader.setInput(ins, /* seekForwardOnly = */ false, /* ignoreMetadata = */ false)
            val count: Int = reader.getNumImages(true)
            if (count < 2) {
                com.venera.desktop.platform.DesktopLogger.info("DesktopAnimatedImage", "ImageIO 报 frameCount=$count")
                return@use null
            }
            // 画布尺寸按流元数据 → reader 首帧尺寸 → 各帧 ImageDescriptor 最大外接顺序兜底。
            val canvasSize = resolveCanvasSize(reader, count)
            val canvasW = canvasSize.first
            val canvasH = canvasSize.second
            if (canvasW <= 0 || canvasH <= 0) {
                com.venera.desktop.platform.DesktopLogger.warn(
                    "DesktopAnimatedImage",
                    "三种来源都拿不到合法画布尺寸 ${canvasW}x${canvasH} → 放弃合成",
                )
                return@use null
            }
            val canvas = java.awt.image.BufferedImage(canvasW, canvasH, java.awt.image.BufferedImage.TYPE_INT_ARGB)
            val g = canvas.createGraphics()
            g.composite = java.awt.AlphaComposite.SrcOver

            // OOM 护栏：按内存预算抽样快照并累加跳过帧时长，合成仍逐帧推进以保证 disposal 正确；单帧超预算则退静帧。
            val bytesPerFrame = canvasW.toLong() * canvasH * 4L
            val budgetFramesByMem = (GIF_MEMORY_BUDGET_BYTES / bytesPerFrame.coerceAtLeast(1)).toInt()
            val maxSnapshots = minOf(GIF_MAX_FRAMES, budgetFramesByMem)
            if (maxSnapshots < 2) {
                g.dispose()
                com.venera.desktop.platform.DesktopLogger.info(
                    "DesktopAnimatedImage",
                    "画布 ${canvasW}x${canvasH} 过大（每帧 ${bytesPerFrame / 1024 / 1024}MB），退静帧",
                )
                return@use null
            }
            val step = ((count + maxSnapshots - 1) / maxSnapshots).coerceAtLeast(1)

            val bitmaps = ArrayList<ImageBitmap>(minOf(count, maxSnapshots))
            val delays = IntArray(minOf(count, maxSnapshots))
            var prevRect: GifRect? = null
            var prevDisposal = 0
            var prevSnapshot: java.awt.image.BufferedImage? = null
            var failedFrames = 0
            var pendingDelay = 0
            for (i in 0 until count) {
                val frame = runCatching { readGifFrame(reader, i) }
                    .onFailure { failedFrames++ }
                    .getOrNull() ?: continue
                // 1) 上一帧声明的 disposal，在画本帧**之前**执行。
                when (prevDisposal) {
                    2 -> prevRect?.let { g.clearRect(it.left, it.top, it.width, it.height) }
                    3 -> prevSnapshot?.let { snap -> prevRect?.let { g.drawImage(snap, it.left, it.top, null) } }
                }
                // 2) 若本帧声明 disposal=3（restore to previous），**在画之前**先快照要覆盖的矩形区，
                //    供下一帧开画前还原。JDK getSubimage 越界会抛 ⇒ 夹进画布范围。
                val region = clampRect(frame.rect, canvasW, canvasH)
                val beforeDraw = if (frame.disposal == 3 && region != null) {
                    canvas.getSubimage(region.left, region.top, region.width, region.height).copyToArgb()
                } else {
                    null
                }
                // 3) 把本帧子图按 LeftPosition/TopPosition 叠到 canvas（AlphaComposite.SrcOver ⇒ 透明像素露旧内容）。
                g.drawImage(frame.sub, frame.rect.left, frame.rect.top, null)
                // 4) 每 step 帧才从合成后的 canvas 快照一张；被跳过帧的时长累加，保节奏。
                pendingDelay += frame.delayMs.coerceAtLeast(20)
                val lastFrame = i == count - 1
                if (i % step == 0 || lastFrame) {
                    bitmaps.add(SkiaImage.makeFromEncoded(encodePng(canvas)).toComposeImageBitmap())
                    delays[bitmaps.size - 1] = pendingDelay.coerceAtLeast(20)
                    pendingDelay = 0
                }
                // 5) 记住本帧位置/尺寸/删除法/快照，供下一帧开头执行。
                prevRect = region ?: frame.rect
                prevDisposal = frame.disposal
                prevSnapshot = beforeDraw
            }
            g.dispose()
            if (bitmaps.size < 2) {
                com.venera.desktop.platform.DesktopLogger.warn(
                    "DesktopAnimatedImage",
                    "ImageIO 合成后可用帧 ${bitmaps.size}/$count（失败帧 $failedFrames）→ 退静帧",
                )
                return@use null
            }
            FramesResult(bitmaps, delays.copyOf(bitmaps.size))
        }
    }
    r.exceptionOrNull()?.let {
        com.venera.desktop.platform.DesktopLogger.warn(
            "DesktopAnimatedImage",
            "ImageIO 解码抛异常 因=${it.javaClass.simpleName}: ${it.message}",
        )
    }
    return r.getOrNull()
}

/** 一帧的合成分量：子图、位置、删除动作、延时（ms）。 */
private class GifFrame(val sub: java.awt.image.BufferedImage, val rect: GifRect, val disposal: Int, val delayMs: Int)
private class GifRect(val left: Int, val top: Int, val width: Int, val height: Int)

/**
 * 画布尺寸多源兜底：任一正数即用。全 0 才放弃（返回 0x0）。
 * 1) stream 元数据 `LogicalScreenDescriptor@Width/Height`；
 * 2) `reader.getWidth(0)/getHeight(0)`（JDK GIF reader 通常给 logical screen 尺寸）；
 * 3) 扫各帧 ImageDescriptor 取最大外接 `(left+width)/(top+height)`。
 */
private fun resolveCanvasSize(reader: javax.imageio.ImageReader, count: Int): Pair<Int, Int> {
    val lsd = fromLsd(reader)
    if (lsd != null && lsd.first > 0 && lsd.second > 0) return lsd
    runCatching {
        val w = reader.getWidth(0)
        val h = reader.getHeight(0)
        if (w > 0 && h > 0) return w to h
    }
    var maxW = 0
    var maxH = 0
    for (i in 0 until count) {
        runCatching {
            val tree = reader.getImageMetadata(i)?.getAsTree("javax_imageio_gif_image_1.0") as? org.w3c.dom.Element
                ?: return@runCatching
            val idn = tree.getElementsByTagName("ImageDescriptor").item(0) as? org.w3c.dom.Element ?: return@runCatching
            val l = idn.getAttribute("LeftPosition")?.toIntOrNull() ?: 0
            val t = idn.getAttribute("TopPosition")?.toIntOrNull() ?: 0
            val w = idn.getAttribute("ImageWidth")?.toIntOrNull() ?: 0
            val h = idn.getAttribute("ImageHeight")?.toIntOrNull() ?: 0
            maxW = maxOf(maxW, l + w)
            maxH = maxOf(maxH, t + h)
        }
    }
    return maxW to maxH
}

private fun fromLsd(reader: javax.imageio.ImageReader): Pair<Int, Int>? = runCatching {
    val root = reader.streamMetadata?.getAsTree("javax_imageio_gif_stream_1.0") as? org.w3c.dom.Element ?: return@runCatching null
    val lsd = root.getElementsByTagName("LogicalScreenDescriptor").item(0) as? org.w3c.dom.Element ?: return@runCatching null
    (lsd.getAttribute("Width")?.toIntOrNull() ?: 0) to (lsd.getAttribute("Height")?.toIntOrNull() ?: 0)
}.getOrNull()

/** 读第 [index] 帧子图 + 元数据（位置/Disposal/DelayTime）。 */
private fun readGifFrame(reader: javax.imageio.ImageReader, index: Int): GifFrame {
    val sub = reader.read(index)
    val tree = reader.getImageMetadata(index)?.getAsTree("javax_imageio_gif_image_1.0") as? org.w3c.dom.Element
    val gce = tree?.getElementsByTagName("GraphicControlExtension")?.item(0) as? org.w3c.dom.Element
    val idn = tree?.getElementsByTagName("ImageDescriptor")?.item(0) as? org.w3c.dom.Element
    val left = idn?.getAttribute("LeftPosition")?.toIntOrNull() ?: 0
    val top = idn?.getAttribute("TopPosition")?.toIntOrNull() ?: 0
    val w = idn?.getAttribute("ImageWidth")?.toIntOrNull() ?: sub.width
    val h = idn?.getAttribute("ImageHeight")?.toIntOrNull() ?: sub.height
    val disposal = gce?.getAttribute("DisposalMethod")?.toIntOrNull() ?: 0
    val hundredths = gce?.getAttribute("DelayTime")?.toIntOrNull() ?: 0
    return GifFrame(sub, GifRect(left, top, w, h), disposal, (hundredths * 10).coerceAtLeast(80))
}

/** 把帧矩形夹进画布范围（JDK `getSubimage` 越界会抛）；完全越界返回 null。 */
private fun clampRect(r: GifRect, canvasW: Int, canvasH: Int): GifRect? {
    val left = r.left.coerceIn(0, (canvasW - 1).coerceAtLeast(0))
    val top = r.top.coerceIn(0, (canvasH - 1).coerceAtLeast(0))
    val w = r.width.coerceAtMost(canvasW - left).coerceAtLeast(0)
    val h = r.height.coerceAtMost(canvasH - top).coerceAtLeast(0)
    if (w <= 0 || h <= 0) return null
    return GifRect(left, top, w, h)
}

/** BufferedImage 深拷贝成一张独立 ARGB 图（skia 侧要稳定像素，画布后续会再变）。 */
private fun java.awt.image.BufferedImage.copyToArgb(): java.awt.image.BufferedImage {
    val out = java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB)
    val og = out.createGraphics()
    og.drawImage(this, 0, 0, null)
    og.dispose()
    return out
}

/** 把一帧 BufferedImage 编成 PNG 字节，供 skia 造位图。 */
private fun encodePng(img: java.awt.image.BufferedImage): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    ImageIO.write(img, "png", out)
    return out.toByteArray()
}
