package com.venera.desktop.platform

import com.venera.compose.data.platform.CacheDirs
import com.venera.compose.data.platform.HttpEngine
import com.venera.compose.data.platform.PathProvider
import java.util.concurrent.TimeUnit
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response

/**
 * `:desktop` 侧的 [HttpEngine] —— 画廊那十颗客户端在桌面上唯一取流的地方。
 *
 * ## 数值为什么必须与 Android 侧逐条对齐
 *
 * `GalleryLegGuard` 给每一站的那笔时间预算是 **12s**，而这个数是在"单站最坏 40s 级"这个
 * 前提下定的（readTimeout 20s → 限流重试一次 → 再 20s，callTimeout 45s 封顶）。三枚超时
 * 与 `retryOnConnectionFailure(false)` 全都照 `data/network/VeneraNetworkClient.kt:67-72`
 * 抄同一条：桌面把超时放宽或收紧，[GalleryLegGuard] 那条"慢的一站这一轮缺席、会被说出来"
 * 的取舍就不再是同一件事 —— 而它两边读的是同一颗 [com.venera.compose.gallery.domain.GalleryDailyFeed]。
 *
 * ## 这一层比 Android 侧少了什么（不许当成"等价的一层"）
 *
 * - **Cloudflare 过盾与共享 CookieJar**：已接入 [com.venera.engineprobe.DesktopCloudflareCenter]；
 *   日榜并发等打了 [com.venera.compose.data.platform.NoInteractiveBypassTag] 的请求会携带已有
 *   `cf_clearance` 与配套 UA，但撞盾时不弹交互验证框；单站主动搜索/详情可放开自动过盾。
 * - **没有 `HostCircuitBreaker`（每主机熔断）与 `RateLimitingInterceptor`（429 退避）**：
 *   那两颗今天住在 `data.network`，而 `data.network` 是桌面共享面的禁引族
 *   （见 `DesktopSharedFaceLedgerTest.FORBIDDEN`）。`HostCircuitBreaker` 本身其实只依赖
 *   okhttp（可以共享），但开它的 srcDir 会连带把同目录那三颗带 `Context`/`Log` 的文件一起
 *   拉进账本重判 —— 那是集成时该一次做完的事，不在这一颗里顺手做。
 *   所以这里只带一颗最小退避 [RetryAfterInterceptor]：**只对 429 等一次 `Retry-After`，
 *   等完仍失败就如实交出去**，不退化成"悄悄重试到成功"。
 * - 缓存与代理配置同为冷启动读盘 ⇒ 改完重启生效。
 *
 * 构造期不建 client：与 `AndroidHttpEngine` 同一条纪律，第一次真正取用才装配
 * （`okhttp3.Cache` 会打开磁盘目录，装配不该发生在接线那一刻）。
 */
class DesktopHttpEngine(private val paths: PathProvider) : HttpEngine {

    override val okHttpClient: OkHttpClient
        get() = sharedByRoot.computeIfAbsent(paths.cacheRoot.absolutePath) { build() }

    private fun build(): OkHttpClient {
        return OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SEC, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_SEC, TimeUnit.SECONDS)
        .callTimeout(CALL_TIMEOUT_SEC, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .addInterceptor(RetryAfterInterceptor())
        // HTTP 响应缓存属可清理临时数据，必须落在 cacheRoot 而非 dataRoot。
        .cache(
            okhttp3.Cache(
                CacheDirs.inCache(paths.cacheRoot, CACHE_DIR_NAME),
                // 档二：上限从偏好读（同键对岸 pref_http_cache_max_mb，默认 100MB）。
                // 装配按 cacheRoot 全局只一次 ⇒ 改档**重启生效**（行上已如实标注）。
                com.venera.compose.data.platform.JsonKeyValueStore(
                    com.venera.compose.data.platform.PreferenceKeys.PREFS_NAME,
                    paths,
                ).getInt("pref_http_cache_max_mb", 100) * 1024L * 1024L,
            ).also { sharedCache = it },
        )
        // 代理（档二落真）：直连=NO_PROXY、系统=跟随 ProxySelector.getDefault
        //（`-Pproxy=` 那条系统属性路也在"系统"档里生效）、手动=HTTP host:port。
        // 同为冷启动读盘 ⇒ 改完重启生效。键名照对岸 pref_proxy_type/host/port。
        .apply {
            val store = com.venera.compose.data.platform.JsonKeyValueStore(
                com.venera.compose.data.platform.PreferenceKeys.PREFS_NAME,
                paths,
            )
            val proxyType = store.getString("pref_proxy_type", "系统") ?: "系统"
            var manualProxyReady = false
            var explicitDirect = false
            when (proxyType) {
                "直连" -> {
                    explicitDirect = true
                    proxy(java.net.Proxy.NO_PROXY)
                }
                "手动" -> {
                    val host = store.getString("pref_proxy_host", "").orEmpty().trim()
                    val port = store.getInt("pref_proxy_port", 0)
                    if (host.isNotBlank() && port in 1..65535) {
                        runCatching {
                            proxy(java.net.Proxy(java.net.Proxy.Type.HTTP, java.net.InetSocketAddress(host, port)))
                            manualProxyReady = true
                        }.onFailure {
                            println("D_代理 手动档装配失败 因=${it.javaClass.simpleName}")
                            com.venera.desktop.data.DesktopErrorBus.report(
                                category = "系统",
                                headline = "手动代理配置无效",
                                humanReason = "无法按当前填写的代理地址（$host:$port）建立连接，请在设置中核对。",
                                detail = "${it.javaClass.simpleName}: ${it.message.orEmpty()}",
                                dedupKey = "proxy:manual_fail",
                            )
                        }
                    } else {
                        explicitDirect = true
                    }
                }
                // "系统"（默认）：不设，走 ProxySelector.getDefault() —— 与旧形状一致。
                else -> Unit
            }
            explicitDirectMode = explicitDirect
            addInterceptor(
                com.venera.engineprobe.DesktopCloudflareCenter.ProxyFastFailInterceptor(
                    hasManualProxy = manualProxyReady,
                    isExplicitDirect = explicitDirect,
                ),
            )
        }
        .build()
        .let { com.venera.engineprobe.DesktopCloudflareCenter.wrapClient(it) }
    }

    /**
     * 429 的最小退避：**等一次，然后照实交回**。
     *
     * 只等一次是刻意的上限。第二次仍被限流就不再挡路 —— 那一笔失败会顺着
     * [com.venera.compose.gallery.domain.GalleryLegOutcome] 的 `reason` 被界面说出来。
     * `Retry-After` 读不出来（没有该头、或不是秒数）时退回 [FALLBACK_WAIT_MS]，
     * 且上限压在时间预算之内：等满再失败，读到的仍是"这一轮没给内容"，不会变成永久转圈。
     */
    private class RetryAfterInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val first = chain.proceed(chain.request())
            if (first.code != HTTP_TOO_MANY_REQUESTS) return first
            val waitMs = retryWaitMs(first.header("Retry-After"))
            first.close()
            Thread.sleep(waitMs)
            return chain.proceed(chain.request())
        }
    }

    companion object {

        /**
         * 按 cacheRoot 收敛的唯一装配。DiskLruCache 跨实例无互斥：同一目录第二颗 `okhttp3.Cache`
         * 的 journal 校验会互相踩出 `corrupt … removing`（真窗刷过屏）。多个装配点再怎么各自
         * `DesktopHttpEngine(paths)`，取到的都是同一颗 client —— 「清缓存」与缓存上限两档口径
         * 也因此天然单源。
         */
        private val sharedByRoot = java.util.concurrent.ConcurrentHashMap<String, OkHttpClient>()

        /** 共享 client 那颗活缓存（设置中枢「清缓存」那枚把手 evict 它；null = 尚未装配）。 */
        @Volatile
        private var sharedCache: okhttp3.Cache? = null

        /** 最近一次装配是否处于明确直连（选「直连」或「手动」未填有效地址）。 */
        @Volatile
        private var explicitDirectMode: Boolean = false

        fun isExplicitDirect(): Boolean = explicitDirectMode

        fun latestSharedClient(): OkHttpClient? = sharedByRoot.values.firstOrNull()

        /**
         * 静态清缓存口：设置中枢「清理」那枚把手调它。没装配过 ⇒ 无事可清，回 false。
         */
        fun evictHttpCache(): Boolean {
            val cache = sharedCache ?: return false
            runCatching { cache.evictAll() }
                .onFailure { println("D_清缓存 okhttp evict 失败 因=${it::class.java.simpleName}") }
            return true
        }

        const val CONNECT_TIMEOUT_SEC = 10L
        const val READ_TIMEOUT_SEC = 20L
        const val WRITE_TIMEOUT_SEC = 20L
        const val CALL_TIMEOUT_SEC = 45L

        /** 目录名与 Android 侧 `File(context.cacheDir, "venera_http_cache")` 同串。 */
        const val CACHE_DIR_NAME = "venera_http_cache"
        const val HTTP_CACHE_MAX_MB = 100L
        const val HTTP_CACHE_MAX_BYTES = HTTP_CACHE_MAX_MB * 1024L * 1024L

        /** 这一层不设 UA：画廊那几颗客户端各自带（`GelbooruClient.API_USER_AGENT`、
         *  `SauceNao` 的 `User-Agent` 头），在这里再设一枚就是第二份口径、还会盖住站方要求的特例。 */
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val FALLBACK_WAIT_MS = 1_000L

        /** 压在 [com.venera.compose.gallery.domain.GalleryDailyFeed.PER_SITE_TIMEOUT_MS] 之下。 */
        private const val MAX_WAIT_MS = 5_000L

        /**
         * 429 之后等多久：纯计算，所以单独拿出来给用例钉（这一层没有 HTTP 服务可打，
         * 而"等多久"恰好是三个最容易漂的数：没给头、给了非秒数、给了一个超过预算的数）。
         */
        internal fun retryWaitMs(headerValue: String?): Long =
            headerValue?.toLongOrNull()?.times(1000L)?.coerceIn(0L, MAX_WAIT_MS) ?: FALLBACK_WAIT_MS
    }
}
