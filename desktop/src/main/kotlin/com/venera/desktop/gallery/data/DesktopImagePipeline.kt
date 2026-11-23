package com.venera.desktop.gallery.data

import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.decode.DataSource
import coil3.disk.DiskCache
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.Options
import com.venera.compose.data.platform.HttpEngine
import com.venera.compose.data.platform.ImageHeaderPolicy
import com.venera.compose.data.platform.JmScramblePolicy
import com.venera.engineprobe.JvmImageOps
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Path.Companion.toPath

/**
 * 桌面取图片字节那一跳的**防盗链头**（不碰 `app/`）。
 *
 * ## 这一格断在哪
 *
 * 批 43 三帧读数：pixiv 那一档把头像地址取到并落了档，而屏上仍是字母座。断点不在链里，在**取字节**那一层 ——
 * pixiv 图床无 `Referer` 恒 403（对岸注释里留着成对读数：无 = 146 字节错误页 / 带 = 200 的 5,149 字节真图），
 * 而桌面那一路走的是 Coil 的**默认单例客户端**：`DesktopHttpEngine` 那台只管画廊那十颗接口客户端，
 * `AsyncImage` 背后那台谁都没配过。
 *
 * ## 为什么是"抄一枚"而不是"接那张表"
 *
 * 那张表住在 `app/…/data/network/ImageHeaderPolicy.kt`，而 `data/network` 整目录不在桌面编译面里
 * （禁引集合那本账见 `DesktopSharedFaceLedgerTest`）。A 路是把对岸那两处的常量改成转交自纯 JVM 那颗，
 * 那要点头；没点到，所以这里走 B 路：**桌面自持同一枚值**。
 *
 * ⚠️ B 路的代价是"两份会漂"，所以把抄的范围钉到最小，并由判据看着：
 * 只抄 `pximg.net` 那一枚（⑤ 机械扫其余 host 一颗都不许多写）、值逐字回读对岸那一行核对（③）、
 * 客户端从引擎那台**派生**而不是新造（②）、装的那一刻出声（④）。
 * 对岸改了那枚值而这里没跟上时，红的是判据 ③ 而不是屏上那片字母座。
 */
internal const val DESKTOP_PIXIV_IMAGE_HOST: String = "pximg.net"

/** 与对岸 `"pximg.net" to mapOf("Referer" to …)` 那一行**逐字同值**（判据 ③ 回读核对）。 */
internal const val DESKTOP_PIXIV_REFERER: String = "https://www.pixiv.net/"

/**
 * 防盗链头拦截器（改口径 · 详情页重建 EH 预览拉不到的终审）：
 * 从前这里只手抄 `pximg.net` 一枚 —— 而 EH 家族（`ehgt.org` / `e-hentai.org` / `exhentai.org` /
 * `hath.network`）的缩略图同样要 Referer + 浏览器 UA，详情页预览因此整排空白。
 * 现在改吃 `ImageHeaderPolicy.headersFor(url)`（`data/platform` 那颗**在桌面编译面里**，
 * 内置表 EH 四 host + pixiv 全都有，源 `onThumbnailLoad` 发布的运行时头也从这里出）——
 * 一处问表、处处生效，不再逐站手抄。host 匹配的点边界在策略那头收（"cdn.notpximg.net 不是
 * pximg.net"那条对照由判据 ① 继续钉着）；`DESKTOP_PIXIV_*` 两枚常量留给判据 ③ 逐字回读。
 */
internal class DesktopImageHeaderInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val headers = ImageHeaderPolicy.headersFor(request.url.toString())
        val builder = request.newBuilder()
        headers.forEach { (name, value) -> builder.header(name, value) }
        return chain.proceed(builder.build())
    }
}

/**
 * 将 i.pximg.net 请求改写到用户配置的反代域名；反代异常时回退直连原请求。
 */
internal class DesktopPximgMirrorInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val origRequest = chain.request()
        val origUrl = origRequest.url.toString()
        val rewritten = com.venera.desktop.settings.DesktopPximgMirror.rewrite(origUrl)
        if (rewritten == origUrl) {
            return chain.proceed(origRequest)
        }
        val mirrorRequest = origRequest.newBuilder().url(rewritten).build()
        val resp = try {
            chain.proceed(mirrorRequest)
        } catch (_: java.io.IOException) {
            return chain.proceed(origRequest)
        }
        if (resp.isSuccessful) return resp
        resp.close()
        return chain.proceed(origRequest)
    }
}


/**
 * 漫画图床的缩放档 404 回退：解析层给的地址是站方缩放档（`xxx.jpg.c1200x.webp`），
 * 站方随内容变更会让该档失效，而**原图档（剥掉缩放后缀）通常仍在** ——
 * 404 时剥后缀重取一次，墙/阅读器不再整屏黑图。
 */
internal class DesktopMangaScaleFallbackInterceptor : Interceptor {
    private val scaleSuffix = Regex("""\.c\d+x\w+\.webp$""")

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val resp = chain.proceed(request)
        if (resp.code != 404) return resp
        val url = request.url.toString()
        val stripped = url.replace(scaleSuffix, "")
        if (stripped == url) return resp
        resp.close()
        println("D_图错 404 回退 原档=$url")
        return chain.proceed(request.newBuilder().url(stripped).build())
    }
}

/**
 * 图床 429 限流冷却拦截器：同一 host 收到 429 后按 Retry-After（默认 10s，上限 60s）
 * 进入短时冷却，冷却期内直接拦截不再往站方发请求，避免一屏多图并发轰炸加重封锁。
 */
internal class DesktopImageRateLimitInterceptor(
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) : Interceptor {
    private val cooldownByHost = ConcurrentHashMap<String, Long>()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host.lowercase()
        val now = nowMs()
        val until = cooldownByHost[host] ?: 0L
        if (until > now) {
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(429)
                .message("Too Many Requests (cooldown)")
                .body(ByteArray(0).toResponseBody(null))
                .build()
        }
        val resp = chain.proceed(request)
        if (resp.code == 429) {
            val retryAfterSec = resp.header("Retry-After")?.trim()?.toLongOrNull()
            val cooldownMs = ((retryAfterSec ?: DEFAULT_COOLDOWN_SEC) * 1000L)
                .coerceIn(MIN_COOLDOWN_MS, MAX_COOLDOWN_MS)
            cooldownByHost[host] = nowMs() + cooldownMs
            println("D_图限流 host=$host 冷却=${cooldownMs / 1000}s")
        }
        return resp
    }

    private companion object {
        const val DEFAULT_COOLDOWN_SEC = 10L
        const val MIN_COOLDOWN_MS = 5_000L
        const val MAX_COOLDOWN_MS = 60_000L
    }
}

internal object DesktopImagePipeline {

    /** 桌宠「图加载失败」提醒的节流锚（任意线程上动，10s 一条）。 */
    @Volatile private var lastLoadFailedCueMs = 0L

    /**
     * 图片盘缓存的目录名。与 http 那颗同族（`DesktopHttpEngine.CACHE_DIR_NAME = "venera_http_cache"`），
     * 落点也同一条路：`CacheDirs.inCache(paths.cacheRoot, …)`（那颗顺手建目录）。
     */
    internal const val IMAGE_CACHE_DIR_NAME = "venera_image_cache"

    /** 盘缓存默认预算（设置「图片缓存上限」档的缺省值；装管道那一刻读档覆盖）。 */
    internal const val DEFAULT_DISK_CACHE_BYTES = 512L * 1024 * 1024

    /**
     * 图片那一台 = 引擎那台 + 恰好一枚拦截器（代理与超时全继承：`--proxy=` 对图片这一路也得管用）。
     *
     * 主人点名 pixiv 拿得太快：**换一台自持的 Dispatcher 限并发**（okhttp 的
     * `newBuilder()` 默认共享 base 那台 dispatcher，动它会连引擎的 API 跳一起改，所以这里递新实例）——
     * 全局 8 条、单主机 4 条（pixez 的下载队列默认才 2~6 并发；一屏 20+ 张齐发会被 pximg CDN
     * 判成批量抓取）。排队而非断流：图照样都会到，只是不再同一毫秒挤出去。
     */
    internal fun clientForImages(base: OkHttpClient): OkHttpClient {
        val dispatcher = okhttp3.Dispatcher().apply {
            maxRequests = 8
            maxRequestsPerHost = 3
        }
        return base.newBuilder()
            .dispatcher(dispatcher)
            .cache(null)
            .addInterceptor(DesktopImageRateLimitInterceptor())
            .addInterceptor(DesktopImageHeaderInterceptor())
            .addInterceptor(DesktopPximgMirrorInterceptor())
            .addInterceptor(DesktopMangaScaleFallbackInterceptor())
            .build()
    }

    /**
     * 装上 Coil 的单例供体：**应用根一次**，且必须早于任何取图。
     *
     * 不装单例的话，屏上那些 `AsyncImage` 各吃 Coil 的默认客户端，头仍然盖不上；
     * 而预取那一族（批 47）吃的就是这同一份单例 —— 另起一枚 ImageLoader 就是"两条轨"那一族。
     *
     * ## 盘缓存为什么要自己递一颗目录进来（批 90，包内量到的）
     *
     * 从前这里**没配过** `diskCache` ⇒ Coil 用它自己的默认落点：`java.io.tmpdir/coil3_disk_cache`，
     * 也就是**系统 Temp**，与数据目录不同轨。两笔后果：
     * - 隔离失效：起窗脚本把 `LOCALAPPDATA` 指到 `_qa/…_local` 也管不到它（`java.io.tmpdir` 由 TMP 定，
     *   不由 LOCALAPPDATA 定），所以"这一趟没往真人那份数据里写"那句实话**只对数据根成立而对图片缓存不成立**；
     *   开发那一跑与交付包那一跑**共用同一颗 journal**，而 Coil 的 `DiskCache` 不是多进程安全的。
     * - 包内那一趟实测两笔同键失败（`_qa/b90_pkgA.log`、`_qa/b90_pkgB.log`）：
     *   `D_大图 取图失败 failed to delete …\Temp\coil3_disk_cache\083e…d900.0 档=SAMPLE`，
     *   于是 `D_存图 未落盘 因=NO_BYTES` 与 `D_分块 退化 因=缓存没它` 同帧出声。
     *   ⚠️ **那条 delete 失败的成因未定论**（"上一实例还活着按住那颗"这一条已被
     *   `_qa/who_holds_java.ps1` 量掉：当时活着的两颗 java 都是构建侧 daemon）。
     *   这一搬**不是**那条失败的证明性修复，它修的是"两跑共用一份缓存 + 隔离管不到"这一族；
     *   搬完之后包内那一格有没有真的不再失败，只有**重跑包内那两趟**算数（rounds 里按读数记账）。
     *
     * 上限吃形参（调用点从设置的盘缓存档递，默认 [DEFAULT_DISK_CACHE_BYTES]）：
     * 盘预算与数据目录那一格同一处决策，不在这里写死第二份 MB 数。
     */
    fun install(engine: HttpEngine, cacheDir: File, maxSizeBytes: Long = DEFAULT_DISK_CACHE_BYTES) {
        // 派生图片客户端并在管道安装点包装 Chrome TLS 指纹 + 共享 CookieJar + Cloudflare 过盾重试。
        val imageClient = com.venera.engineprobe.DesktopCloudflareCenter.wrapImageClient(
            clientForImages(engine.okHttpClient),
        )
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
                .diskCache(
                    DiskCache.Builder()
                        .directory(cacheDir.path.toPath())
                        // 显式立账：盘占用与数据目录预算对齐（预算档在设置里，装管道那一刻读）。
                        .maxSizeBytes(maxSizeBytes)
                        .build(),
                )
                // 内存缓存 64MB：配合阅读器收紧后的前后预载（最多 6 页视口图约 30-45MB），足够翻页不重解。
                .memoryCache(
                    coil3.memory.MemoryCache.Builder()
                        .maxSizeBytes(64L * 1024 * 1024)
                        .build(),
                )
                .eventListenerFactory { _ ->
                    // EH 排查：解码/装载失败念成因（Coil 的 error 不进缓存，
                    // 重组就重取 —— 同一 URL 反复出现在 D_图头 里就是这条路上有病）。
                    object : coil3.EventListener() {
                        // 大图取样：一张 16MP 的位图上到 GPU 就是 64MB 显存映射，而它在前台只占
                        // 一块格子。只念 4MP 以上的，用来揪出那些没走尺寸约束的取图点。
                        override fun onSuccess(
                            request: coil3.request.ImageRequest,
                            result: coil3.request.SuccessResult,
                        ) {
                            val image = result.image
                            val pixels = image.width.toLong() * image.height
                            if (pixels >= 4_000_000L) {
                                println(
                                    "D_大图 ${image.width}x${image.height} 约${pixels / 1_000_000}MP " +
                                        "url=${request.data.toString().take(90)}",
                                )
                            }
                        }

                        override fun onError(request: coil3.request.ImageRequest, result: coil3.request.ErrorResult) {
                            println(
                                "D_图错 url=${request.data.toString().take(110)} " +
                                    "因=${result.throwable::class.java.simpleName}: " +
                                    "${result.throwable.message?.take(160)}",
                            )
                            com.venera.desktop.DesktopErrorBus.reportImageError(
                                request.data.toString(),
                                result.throwable,
                            )
                            // 失败要让用户看见：桌宠 LOAD_FAILED 台词（节流 10s，一墙全挂时不连念）。
                            val now = System.currentTimeMillis()
                            if (now - lastLoadFailedCueMs > 10_000L) {
                                lastLoadFailedCueMs = now
                                runCatching {
                                    com.venera.desktop.ui.components.DesktopPetCueBus.emit(
                                        com.venera.desktop.ui.components.DesktopPetCue.LOAD_FAILED,
                                    )
                                }
                            }
                        }
                    }
                }
                .components {
                    add(DesktopSpriteImageKeyer)
                    add(DesktopSpriteImageFetcher.Factory(callFactory = { imageClient }))
                    add(DesktopJmImageFetcher.Factory(callFactory = { imageClient }))
                    add(OkHttpNetworkFetcherFactory(callFactory = { imageClient }))
                    // 阅读那一屏的一页（批 81）：字节由引擎取（含解混淆），所以走自定义 fetcher。
                    // ⚠️ 这两行**成对**：只挂 fetcher 不挂 Keyer 时 Coil 的 `key()` 交回 null，
                    // 语义是"这一格不进内存缓存" —— 屏上完全看不出异常，而每次滚回来都重新解混淆一遍。
                    add(DesktopComicPageKeyer)
                    add(DesktopComicPageFetcher.Factory())
                    // 超大图分块（阅读器重做批·二轮）：放大后的 2×2 高清瓦片，同样成对（键+fetcher）。
                    add(DesktopComicTileKeyer)
                    add(DesktopComicTileFetcher.Factory())
                    // AI 前置管线页（阅读器对齐批）：纯 JVM 本地算法页，同样成对（键+fetcher）。
                    add(com.venera.desktop.reader.DesktopAiPageKeyer)
                    add(com.venera.desktop.reader.DesktopAiPageFetcher.Factory())
                }
                .build()
        }
        // 读数带 ASCII 半句 `cache=`：包内 stdout 的中文会成 U+FFFD（批 87·下半 那一课），
        // 而"缓存到底落在哪"正是批 90 要量那一格，念不出等于没量到。
        println(
            "D_取图 装=防盗链一枚 站=$DESKTOP_PIXIV_IMAGE_HOST 客户端=引擎那台派生 页取数=挂了两枚(键+fetcher) " +
                "cache=${cacheDir.path}",
        )
    }

    /** 离场时认定为大图并主动清除的最小位图体积（1 MB，保留瀑布流小封面避免返墙闪烁）。 */
    const val LARGE_IMAGE_EVICT_MIN_BYTES: Long = 1024L * 1024L

    /**
     * 从全局 Coil 内存缓存中清除单张体积达到 [minBytes] 的大图条目。
     *
     * @param reason 触发清除的场景标签，用于运行读数日志。
     * @param minBytes 判定大图的最小解码字节数，默认 1 MB。
     * @return 本次实际从内存缓存移除的大图估算总字节数。
     */
    fun evictLargeMemoryCache(
        reason: String,
        minBytes: Long = LARGE_IMAGE_EVICT_MIN_BYTES,
    ): Long {
        val cache = runCatching {
            SingletonImageLoader.get(coil3.PlatformContext.INSTANCE).memoryCache
        }.getOrNull()
        if (cache == null) {
            println("D_图片缓存 跳过清除大图 reason=$reason 因=MemoryCache为空")
            return 0L
        }
        return evictLargeMemoryCacheEntries(cache, reason, minBytes)
    }

    /**
     * 遍历 [cache] 并移除所有单张体积不少于 [minBytes] 的条目，保留小尺寸缩略图。
     *
     * @param cache Coil 内存缓存实例。
     * @param reason 触发清除的场景标签。
     * @param minBytes 判定大图的最小解码字节数。
     * @return 本次移除条目的字节总计。
     */
    fun evictLargeMemoryCacheEntries(
        cache: coil3.memory.MemoryCache,
        reason: String,
        minBytes: Long = LARGE_IMAGE_EVICT_MIN_BYTES,
    ): Long {
        val beforeBytes = cache.size
        var removedCount = 0
        var removedBytes = 0L
        for (key in cache.keys.toList()) {
            val bytes = cache[key]?.image?.size ?: 0L
            if (bytes >= minBytes && cache.remove(key)) {
                removedCount++
                removedBytes += bytes
            }
        }
        if (removedCount > 0) {
            val afterBytes = cache.size
            val freed = (beforeBytes - afterBytes).coerceAtLeast(removedBytes)
            println(
                "D_图片缓存 清除大图 reason=$reason 张数=$removedCount " +
                    "释放=${freed / (1024L * 1024L)}MB 剩余=${afterBytes / (1024L * 1024L)}MB",
            )
            return freed
        }
        return 0L
    }
}

/**
 * 针对禁漫 (JM) 切片混淆图片的去混淆 Fetcher。
 * 详情页预览（thumbnails）等普通网络图直接通过 Coil URL 请求时，由本件拦截并调用 JvmImageOps.descrambleBlocks 解混淆。
 */
internal class DesktopJmImageFetcher(
    private val url: String,
    private val options: Options,
    private val callFactory: Call.Factory,
) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        val num = JmScramblePolicy.getScrambleNum(url)
        if (num <= 1) return null
        val request = Request.Builder().url(url).build()
        val response = withContext(Dispatchers.IO) { callFactory.newCall(request).execute() }
        val bytes = response.body?.bytes() ?: return null
        val descrambled = runCatching {
            val img = JvmImageOps.decode(bytes)
            JvmImageOps.descrambleBlocks(img, num)
        }.getOrNull() ?: bytes
        return desktopComicPageResult(descrambled, options, DataSource.NETWORK)
    }

    class Factory(private val callFactory: () -> Call.Factory) : Fetcher.Factory<coil3.Uri> {
        override fun create(data: coil3.Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val str = data.toString()
            if (!JmScramblePolicy.isJmPhotoUrl(str) || JmScramblePolicy.getScrambleNum(str) <= 1) return null
            return DesktopJmImageFetcher(str, options, callFactory())
        }
    }
}

/** 针对带 `@x=` / `@y=` 裁剪参数的图片提供独立内存缓存键。 */
internal object DesktopSpriteImageKeyer : coil3.key.Keyer<coil3.Uri> {
    override fun key(data: coil3.Uri, options: Options): String? {
        val str = data.toString()
        if (str.contains("@x=") || str.contains("@y=") || str.contains("s.exhentai.org")) {
            return str
        }
        return null
    }
}

/**
 * 针对 EH / EX 等雪碧图（Sprite Sheet）裁剪缩略图的 Fetcher。
 * 当 URL 包含 `@x=` 或 `@y=` 时，本件拦截并拉取雪碧原图，根据坐标用 BufferedImage 裁剪出局部切片。
 * 若 host 为 s.exhentai.org，自动重写为 ehgt.org 避免无 cookie 导致 403。
 */
internal class DesktopSpriteImageFetcher(
    private val url: String,
    private val options: Options,
    private val callFactory: Call.Factory,
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val hasSpriteCrop = url.contains("@x=") || url.contains("@y=")
        val cleanUrl = (if (hasSpriteCrop) url.substringBefore('@') else url)
            .replace("://s.exhentai.org/", "://ehgt.org/")
        val cropRange = if (hasSpriteCrop) JmScramblePolicy.parseCropRange(url.substringAfter('@')) else null

        val bytes = withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url(cleanUrl)
                .header("Referer", "https://e-hentai.org/")
                .build()
            val resp = callFactory.newCall(req).execute()
            if (!resp.isSuccessful) {
                resp.close()
                null
            } else {
                resp.body?.bytes()
            }
        } ?: return null

        if (cropRange == null) {
            return desktopComicPageResult(bytes, options, DataSource.NETWORK)
        }

        val croppedBytes = withContext(Dispatchers.IO) {
            runCatching {
                val fullImg = JvmImageOps.decode(bytes)
                val iw = fullImg.width
                val ih = fullImg.height
                val x = cropRange.x1.coerceIn(0, (iw - 1).coerceAtLeast(0))
                val y = cropRange.y1.coerceIn(0, (ih - 1).coerceAtLeast(0))
                val w = (cropRange.x2 - cropRange.x1).coerceIn(1, (iw - x).coerceAtLeast(1))
                val h = (cropRange.y2 - cropRange.y1).coerceIn(1, (ih - y).coerceAtLeast(1))
                // 先将 getSubimage 拷贝为独立 ARGB 位图再编码 PNG：直接将共享光栅或带 alpha 图像喂给 JPEG 写器会损坏数据。
                val srcSub = fullImg.getSubimage(x, y, w, h)
                val sub = java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB)
                val g = sub.createGraphics()
                g.drawImage(srcSub, 0, 0, null)
                g.dispose()
                val baos = java.io.ByteArrayOutputStream()
                javax.imageio.ImageIO.write(sub, "png", baos)
                baos.toByteArray().takeIf { it.isNotEmpty() }
                    ?: error("PNG 写器没写出字节")
            }.getOrElse {
                println("D_精灵裁切 塌了 ${it::class.java.simpleName}: ${it.message?.take(140)}")
                null
            }
        } ?: bytes

        return desktopComicPageResult(croppedBytes, options, DataSource.NETWORK)
    }

    class Factory(private val callFactory: () -> Call.Factory) : Fetcher.Factory<coil3.Uri> {
        override fun create(data: coil3.Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val str = data.toString()
            val hasCrop = str.contains("@x=") || str.contains("@y=")
            val isEx = str.contains("s.exhentai.org")
            if (!hasCrop && !isEx) return null
            return DesktopSpriteImageFetcher(str, options, callFactory())
        }
    }
}
