package com.venera.desktop.gallery.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.base.State
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import uk.co.caprica.vlcj.player.embedded.videosurface.CallbackVideoSurface
import uk.co.caprica.vlcj.player.embedded.videosurface.VideoSurfaceAdapters
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import java.nio.ByteBuffer

/**
 * 桌面 booru **视频**（mp4 / webm）的**回调面解码引擎**（要嵌进界面、不要外挂浮窗）。
 *
 * ## 为什么是"回调面"而不是重量级原生窗
 * vlcj 默认的 `EmbeddedMediaPlayerComponent` 把 libvlc 渲染进一块**重量级原生画布**，而重量级组件
 * 在 Compose Desktop（单块 GL 纹理）里既盖住我们的轻量 UI、又进不了场景树 —— 前几轮"状态条被挡、
 * 独立黑窗、很外挂"全源于此。改成 [CallbackVideoSurface]：libvlc 把**每一帧解码成一块内存缓冲**交回，
 * 我们自己用 skia 转成 `ImageBitmap`、用普通 `Image` 画进 Compose ⇒ 视频成为界面的一部分。
 *
 * ## 解码仍依赖本机 VLC（复用系统库）
 * [MediaPlayerFactory] 构造时内置 `NativeDiscovery` 找 libvlc；**没装 VLC 这里就抛** ⇒ [start] 返回 false，
 * 调用点走"本机没有 VLC"提示路径，绝不假装在播。回调面只把"渲染"变成内嵌，不改变"解码靠 VLC"这一事实。
 *
 * ## 线程
 * `display` 在 libVLC 线程回调、`getFrame` 在 Compose 帧循环读 ⇒ 一把锁内拷一份字节、每帧新建位图，
 * 不把可变缓冲交出去，避免撕裂/竞态。`release` 幂等：先停再释放、工厂一并释放。
 *
 * 帧格式：向站方要 `RGBA`（单平面、pitch=w*4、top-down）。flipVertical 旗标真窗见上下颠倒就翻它（见下注释）。
 */
internal class DesktopVideoEngine(
    private val mediaUrl: String,
    private val headers: Map<String, String>,
    private val onPhase: (State) -> Unit,
) {
    private var factory: MediaPlayerFactory? = null
    private var player: EmbeddedMediaPlayer? = null

    private val lock = Any()
    private var buffer: ByteBuffer? = null
    @Volatile private var width = 0
    @Volatile private var height = 0
    /** 已交付的帧计数（libVLC 线程自增，UI 侧轮询对比，判断有没有新帧）。 */
    private val frameCounter = java.util.concurrent.atomic.AtomicLong()
    private var lastPolled = 0L

    /** 有没有比上次拉取更新的帧。 */
    fun hasNewFrame(): Boolean = frameCounter.get() != lastPolled

    /**
     * 把 libVLC 当前缓冲的像素转成 Compose 位图。**在 Dispatchers.Default 线程调用，绝不占 EDT**
     * （转换要 `installPixels`+`makeFromBitmap` 搬整块像素，放 UI 线程会每帧卡死整个软件）。
     * 拷贝进局部数组后即释放锁，skia 构造在锁外做，避免与 libVLC 回调线程争锁。
     */
    fun renderFrame(): ImageBitmap? {
        val (w, h, bytes) = synchronized(lock) {
            val w = width
            val h = height
            val buf = buffer ?: return null
            if (w <= 0 || h <= 0) return null
            lastPolled = frameCounter.get()
            val arr = ByteArray(w * h * 4)
            buf.rewind()
            buf.get(arr)
            Triple(w, h, arr)
        }
        return runCatching<ImageBitmap> {
            val bmp = Bitmap()
            bmp.setImageInfo(ImageInfo(w, h, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL))
            val installed = bmp.installPixels(bytes)
            if (!installed) error("installPixels 失败 ${w}x$h")
            org.jetbrains.skia.Image.makeFromBitmap(bmp).toComposeImageBitmap()
        }.getOrNull()
    }


    @Volatile
    var sawPlaying = false
        private set

    /**
     * 送给 libVLC 的地址归一：协议相对地址（`//host/…`，缺 scheme）会被 Windows 当成 **UNC 本地路径**打开
     * （正是 `\\img4.gelbooru.com\images\…mp4: moov atom not found` 那次的形态），补上 `https:` 让它走网络访问。
     * 已是 `http(s)://…` 的原样。
     */
    private val mrl: String get() = if (mediaUrl.startsWith("//")) "https:$mediaUrl" else mediaUrl


    /**
     * 工厂/播放器建不起来（本机无 VLC）⇒ 返回 false，调用点据此提示。
     * **必须在非 EDT 线程调用**（`libvlc_new` + 建 vout 是阻塞调用，放 UI 线程点下即冻整窗）。
     *
     * 工厂强制 `--vout=vmem`：libVLC 只用内存视频输出、把帧交给回调面，**绝不再尝试建窗口 vout**
     * （Failed to set on top` 就是它还在往 LWJGL 窗上贴子窗，
     * 链起不来 → h264 `get_buffer()/no frame` → 黑屏）。
     */
    fun start(): Boolean = runCatching {
        val f = MediaPlayerFactory("--vout=vmem")
        factory = f
        // flipVertical=false：RGBA 顶-down 直出；真窗若见上下颠倒，把这个旗标翻成 true 即可。
        val surface = CallbackVideoSurface(bufferFormatCallback, renderCallback, false, VideoSurfaceAdapters.getVideoSurfaceAdapter())
        val mp = f.mediaPlayers().newEmbeddedMediaPlayer()
        mp.videoSurface().set(surface)
        mp.events().addMediaPlayerEventListener(events)
        player = mp
        // 解码线程夹 1（避开 h264 `thread_get_buffer()/no frame` 多线程失败路径）+ `input-repeat=-1` 无限循环
        // （详情页动图/视频要自动循环播，不是播完就停）。
        mp.media().play(
            mrl,
            *(headers.toVlcOptions() + ":avcodec-threads=1" + ":input-repeat=-1"),
        )
        true
    }.getOrElse {
        com.venera.desktop.platform.DesktopLogger.warn("DesktopVideo", "起播失败(多半本机没装 VLC) media=$mediaUrl 因=${it.message}")
        com.venera.desktop.DesktopErrorBus.report(
            category = "图库",
            headline = "视频起播失败",
            humanReason = "未检测到本机 VLC 播放器或播放器组件异常，请安装 VLC 后重试。",
            detail = "media=$mediaUrl\n${it::class.java.simpleName}: ${it.message.orEmpty()}",
            dedupKey = "video:launch_fail",
        )
        releaseQuietly()
        false
    }

    fun togglePause() {
        val playing = runCatching { player?.status()?.isPlaying() == true }.getOrDefault(false)
        runCatching { player?.controls()?.setPause(playing) }
    }

    fun timeMs(): Long = runCatching { player?.status()?.time() ?: -1L }.getOrDefault(-1L)
    fun lengthMs(): Long = runCatching { player?.status()?.length() ?: -1L }.getOrDefault(-1L)

    /** 拖到某个绝对时刻（毫秒）；进度条点击/拖动 seek 用。 */
    fun seekTo(ms: Long) {
        val target = ms.coerceAtLeast(0L)
        runCatching { player?.controls()?.setTime(target) }
    }

    /**
     * 释放。**同步只摘引用**（让取帧/进度读侧立刻短路返回 null/-1），
     * **真正的 libVLC `stop()+release()` 交给一条守护线程**——它是阻塞调用，
     * 而本方法常在 `DisposableEffect.onDispose`（EDT/主线程）里被点画布/关窗触发，
     * 若留在 EDT 上执行会当场冻死整个软件（点画面任意处即卡死）。
     */
    fun release() {
        val toRelease: Pair<EmbeddedMediaPlayer?, MediaPlayerFactory?> = synchronized(lock) {
            val pair = player to factory
            player = null
            factory = null
            buffer = null
            width = 0
            height = 0
            sawPlaying = false
            pair
        }
        val (mp, f) = toRelease
        if (mp == null && f == null) return
        Thread({
            runCatching { mp?.controls()?.stop() }
            runCatching { mp?.release() }
            runCatching { f?.release() }
        }, "venera-vlc-release").apply { isDaemon = true }.start()
    }

    private fun releaseQuietly() {
        val pair = synchronized(lock) {
            val p = player to factory
            player = null
            factory = null
            p
        }
        val (mp, f) = pair
        Thread({
            runCatching { mp?.release() }
            runCatching { f?.release() }
        }, "venera-vlc-release").apply { isDaemon = true }.start()
    }


    private val bufferFormatCallback = object : BufferFormatCallback {
        override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
            val planes = intArrayOf(sourceWidth * 4)
            val lines = intArrayOf(sourceHeight)
            synchronized(lock) {
                width = sourceWidth
                height = sourceHeight
                val total = sourceWidth * sourceHeight * 4
                buffer = ByteBuffer.allocateDirect(total.coerceAtLeast(16))
            }
            return BufferFormat("RGBA", sourceWidth, sourceHeight, planes, lines)
        }

        override fun newFormatSize(sourceWidth: Int, sourceHeight: Int, displayWidth: Int, displayHeight: Int) {
            // 尺寸变化下一帧由 getBufferFormat 重新分配；这里无需动作。
        }

        override fun allocatedBuffers(buffers: Array<out ByteBuffer>) {
            // 缓冲由我们自己持有（getBufferFormat 里已建）。
        }
    }

    private val renderCallback = object : RenderCallback {
        override fun lock(mediaPlayer: MediaPlayer) { /* 单缓冲，无需加锁 */ }

        override fun display(
            mediaPlayer: MediaPlayer,
            nativeBuffers: Array<out ByteBuffer>,
            bufferFormat: BufferFormat?,
            displayWidth: Int,
            displayHeight: Int,
        ) {
            // 只在 libVLC 线程"拷一份像素快照 + 记序号"，绝不在这里做 skia 转换 / 不回调到 Compose。
            // 重活（installPixels+makeFromBitmap）在 UI 侧的 Dispatchers.Default 上做，UI 线程不卡死。
            synchronized(lock) {
                val nb = nativeBuffers.firstOrNull() ?: return@synchronized
                val dst = buffer ?: return@synchronized
                nb.rewind()
                dst.clear()
                dst.put(nb)
                dst.rewind()
            }
            frameCounter.incrementAndGet()
        }

        override fun unlock(mediaPlayer: MediaPlayer) { /* 无 */ }
    }

    private val events = object : MediaPlayerEventAdapter() {
        override fun opening(mp: MediaPlayer) { onPhase(State.OPENING) }
        override fun buffering(mp: MediaPlayer, preCache: Float) { onPhase(State.BUFFERING) }
        override fun playing(mp: MediaPlayer) {
            sawPlaying = true
            onPhase(State.PLAYING)
        }
        override fun paused(mp: MediaPlayer) { onPhase(State.PAUSED) }
        override fun stopped(mp: MediaPlayer) { onPhase(State.STOPPED) }
        override fun finished(mp: MediaPlayer) { onPhase(State.ENDED) }
        override fun error(mp: MediaPlayer) {
            com.venera.desktop.platform.DesktopLogger.warn("DesktopVideo", "投播出错 media=$mediaUrl")
            com.venera.desktop.DesktopErrorBus.report(
                category = "图库",
                headline = "视频投播出错",
                humanReason = "视频流解码或播放过程中断，请尝试重新打开或检查网络。",
                detail = "media=$mediaUrl",
                dedupKey = "video:play_error:$mediaUrl",
            )
            onPhase(State.ERROR)
        }
    }
}

/**
 * HTTP 头 → libvlc **逐媒体选项**（`media().play(mrl, options)` 要的形式）。
 *
 * ⚠️ 必须是**冒号前缀** `:http-referrer=` —— 这是 per-media option 语法。双横线 `--http-referrer=` 是
 * **实例选项**（`MediaPlayerFactory` 那层用），放到 `media().play` 的逐媒体 vararg 里会破坏 libVLC 对 MRL 的解析，
 * 让远程 https 地址被当成 Windows **文件/UNC 路径**打开（日志实见 `\\img4.gelbooru.com\images\…mp4: moov atom not found`）。
 * 只认这两个真影响防盗链的头，其余忽略。
 */
private fun Map<String, String>.toVlcOptions(): Array<String> = buildList {
    entries.forEach { (k, v) ->
        if (v.isNotBlank()) when (k.lowercase()) {
            "referer" -> add(":http-referrer=$v")
            "user-agent" -> add(":http-user-agent=$v")
        }
    }
}.toTypedArray()
