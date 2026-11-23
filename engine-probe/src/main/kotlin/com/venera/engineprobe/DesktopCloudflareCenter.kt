package com.venera.engineprobe

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 桌面端全局 Cookie 共享与 Cloudflare 挑战自愈中心（供漫画引擎与图片加载共享）。
 *
 * 保留标准 JDK TLS（避免第三方 TLS 指纹库在 Hitomi 等严格校验套件的 CDN 上出现 `handshake_failure(40)`）；
 * 当接口或图床返回 Cloudflare / 浏览器校验挑战页时，按主机串行调用桌面侧注册的 [bypassHandler]
 * 通过真实 Edge 内核过盾并提取 `cf_clearance` 与配套 UA，写入共享 [sharedCookieJar] 后自动重试。
 */
object DesktopCloudflareCenter {

    data class CapturedCookie(
        val name: String,
        val value: String,
        val domain: String,
        val path: String = "/",
        val httpOnly: Boolean = false,
        val secure: Boolean = false,
    )

    data class BypassResult(
        val userAgent: String,
        val cookies: List<CapturedCookie>,
    )

    @Volatile
    private var bypassHandler: ((url: String) -> BypassResult?)? = null

    @Volatile
    private var sharedCookieJar: CookieJar? = null

    /**
     * 可选的交互式过盾豁免判据：返回 true 的请求仍可携带共享 CookieJar 与已过盾 UA，
     * 但在撞盾时直接返回原响应，不拉起浏览器验证窗口（如画廊首页并发静默请求）。
     */
    @Volatile
    private var interactiveBypassExempt: ((okhttp3.Request) -> Boolean)? = null

    /** 按主机记录通过挑战时的浏览器 UA，确保带 `cf_clearance` 重试时 UA 与 Cookie 完全匹配。 */
    private val clearedUserAgents = ConcurrentHashMap<String, String>()

    /** 每主机过盾锁，避免同一面墙多张并发图片同时拉起多个验证窗口。 */
    private val hostLocks = ConcurrentHashMap<String, Any>()

    /** 短时失败冷却（毫秒），避免用户取消或过盾失败后死循环弹窗。 */
    private val failedCooldownUntil = ConcurrentHashMap<String, Long>()
    private const val FAILED_COOLDOWN_MS = 60_000L

    fun registerCookieJar(cookieJar: CookieJar) {
        sharedCookieJar = cookieJar
    }

    fun currentCookieJar(): CookieJar? = sharedCookieJar

    fun registerBypassHandler(handler: ((url: String) -> BypassResult?)?) {
        bypassHandler = handler
    }

    fun registerInteractiveBypassExempt(predicate: ((okhttp3.Request) -> Boolean)?) {
        interactiveBypassExempt = predicate
    }

    fun clearedUserAgentFor(host: String): String? {
        val h = host.lowercase()
        return clearedUserAgents[h] ?: clearedUserAgents.entries
            .firstOrNull { h.endsWith(".${it.key}") || it.key.endsWith(".$h") }
            ?.value
    }

    /** 常规境内网络需走代理访问的海外站点根域名（含子域匹配）。 */
    private val proxyRequiredRootHosts = setOf(
        "pixiv.net",
        "pximg.net",
        "pixivision.net",
        "pixiv.org",
        "comic-walker.com",
        "comick.art",
        "comick.io",
        "e-hentai.org",
        "exhentai.org",
        "gelbooru.com",
        "h-comic.com",
        "hitomi.la",
        "jcomic.net",
        "mangadex.org",
        "18mh.org",
        "nhentai.net",
        "shonenjumpplus.com",
        "jumpsq.shueisha.co.jp",
    )

    fun isProxyRequiredHost(host: String): Boolean {
        val h = host.trim().lowercase()
        if (h.isEmpty()) return false
        return proxyRequiredRootHosts.any { root -> h == root || h.endsWith(".$root") }
    }

    internal fun newClientBuilder(
        cookieJar: CookieJar? = sharedCookieJar,
        proxy: String? = null,
        connectTimeoutSec: Long = 15L,
        readTimeoutSec: Long = 20L,
        callTimeoutSec: Long = 30L,
    ): OkHttpClient.Builder {
        val explicitDirect = proxy.equals("DIRECT", ignoreCase = true) || proxy.equals("NO_PROXY", ignoreCase = true)
        val hasManualProxy = !explicitDirect && proxy?.split(":")?.size == 2
        return OkHttpClient.Builder()
            .connectTimeout(connectTimeoutSec, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSec, TimeUnit.SECONDS)
            .callTimeout(callTimeoutSec, TimeUnit.SECONDS)
            .apply {
                if (cookieJar != null) {
                    cookieJar(cookieJar)
                }
                if (explicitDirect) {
                    proxy(Proxy.NO_PROXY)
                } else if (hasManualProxy) {
                    proxy?.split(":")?.takeIf { it.size == 2 }?.let { (h, p) ->
                        runCatching {
                            proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(h.trim(), p.trim().toInt())))
                        }
                    }
                }
                addInterceptor(ProxyFastFailInterceptor(hasManualProxy = hasManualProxy, isExplicitDirect = explicitDirect))
                addInterceptor(CloudflareRetryInterceptor())
            }
    }

    /**
     * 针对需代理站点的直连防卡死拦截器：
     * - 明确设为直连时秒拒，不发起连接；
     * - 未配手动代理且系统无可用代理时，压短连接与读取超时（4.5s/8s），失败后直接提示需配置代理。
     */
    class ProxyFastFailInterceptor(
        private val hasManualProxy: Boolean,
        private val isExplicitDirect: Boolean,
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val req = chain.request()
            val host = req.url.host
            if (!isProxyRequiredHost(host)) return chain.proceed(req)

            if (isExplicitDirect) {
                throw IOException("当前为直连模式，该站点需配置代理后访问")
            }

            if (!hasManualProxy && !hasSystemProxy(req.url.toUri())) {
                val fastChain = chain
                    .withConnectTimeout(4500, TimeUnit.MILLISECONDS)
                    .withReadTimeout(8000, TimeUnit.MILLISECONDS)
                return try {
                    fastChain.proceed(req)
                } catch (e: IOException) {
                    if (e.message?.contains("需配置代理") == true) throw e
                    throw IOException("直连该站点超时或受阻，需配置代理后重试", e)
                }
            }
            return chain.proceed(req)
        }

        private fun hasSystemProxy(uri: java.net.URI): Boolean {
            if (!System.getProperty("http.proxyHost").isNullOrBlank() ||
                !System.getProperty("https.proxyHost").isNullOrBlank()
            ) {
                return true
            }
            val selected = runCatching { ProxySelector.getDefault()?.select(uri) }.getOrNull().orEmpty()
            return selected.any { it.type() != Proxy.Type.DIRECT }
        }
    }

    /**
     * 基于现有配置好的 [base]（继承其代理、超时、Dispatcher 等），挂载共享 CookieJar 与 CF 过盾拦截器。
     */
    fun wrapClient(base: OkHttpClient): OkHttpClient {
        val builder = base.newBuilder()
        val jar = sharedCookieJar
        if (jar != null) {
            builder.cookieJar(jar)
        }
        if (builder.interceptors().none { it is CloudflareRetryInterceptor }) {
            builder.addInterceptor(CloudflareRetryInterceptor())
        }
        return builder.build()
    }

    /** 兼容旧调用点：为图片客户端挂载共享 CookieJar 与 CF 过盾拦截器。 */
    fun wrapImageClient(base: OkHttpClient): OkHttpClient = wrapClient(base)

    internal class CloudflareRetryInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val origRequest = chain.request()
            val host = origRequest.url.host.lowercase()

            if (isBypassExemptHost(host)) {
                return chain.proceed(origRequest)
            }

            // 若该域名已过盾拿到配套 UA，确保与 cf_clearance 同车发出（除非调用方已显式覆盖）。
            val ua = clearedUserAgentFor(host)
            val request = if (ua != null) {
                origRequest.newBuilder().header("User-Agent", ua).build()
            } else {
                origRequest
            }

            val resp = chain.proceed(request)
            if (resp.code != 403 && resp.code != 503) return resp

            val handler = bypassHandler ?: return resp
            val cfMitigated = resp.header("Cf-Mitigated")
            val rawBytes = runCatching { resp.peekBody(16_384).bytes() }.getOrDefault(ByteArray(0))
            val snippet = String(rawBytes, Charsets.UTF_8).lowercase()
            val isChallenge = "challenge" in cfMitigated.orEmpty().lowercase() ||
                "just a moment" in snippet ||
                "正在验证浏览器" in snippet ||
                "請稍候" in snippet ||
                "请稍候" in snippet ||
                "cf_chl_" in snippet ||
                "__cf_chl_" in snippet ||
                "cf-turnstile" in snippet ||
                "challenge-platform" in snippet ||
                "enable javascript and cookies to continue" in snippet ||
                "attention required" in snippet

            if (!isChallenge) return resp
            if (interactiveBypassExempt?.invoke(origRequest) == true) return resp

            val now = System.currentTimeMillis()
            val coolUntil = failedCooldownUntil[host] ?: 0L
            if (coolUntil > now) return resp

            val lock = hostLocks.computeIfAbsent(host) { Any() }
            val result = synchronized(lock) {
                // 排队拿锁期间若另一线程刚过完盾拿到新 UA，直接重试即可不再重复弹窗。
                val latestUa = clearedUserAgentFor(host)
                val coolNow = failedCooldownUntil[host] ?: 0L
                if (coolNow > System.currentTimeMillis()) {
                    null
                } else if (latestUa != null && latestUa != ua) {
                    BypassResult(latestUa, emptyList())
                } else {
                    println("D_过盾 检测到 Cloudflare 挑战 host=$host url=${origRequest.url.toString().take(100)}")
                    val got = runCatching { handler(origRequest.url.toString()) }.getOrNull()
                    if (got == null || got.cookies.isEmpty()) {
                        failedCooldownUntil[host] = System.currentTimeMillis() + FAILED_COOLDOWN_MS
                        println("D_过盾 未获取到凭据，进入 60s 冷却 host=$host")
                        null
                    } else {
                        saveBypassCookies(origRequest.url.toString(), got)
                        got
                    }
                }
            } ?: return resp

            resp.close()
            val retryReq = origRequest.newBuilder()
                .header("User-Agent", result.userAgent)
                .build()
            return chain.proceed(retryReq)
        }

        /**
         * jm（禁漫）全族域不参与过盾：它的接口与图床都不是 Cloudflare 盾——
         * 图床是 S3 预签名桶，403 = 签名失效，弹窗过盾永远拿不到凭据（运行日志反复
         * 「检测到挑战→未获取到凭据→冷却」循环），正确出口是源侧换新签名 URL，故直接豁免。
         * 域族与桌面侧 JmScramblePolicy 的已知段对齐，另含实证图床 jcomic.net。
         */
        private fun isBypassExemptHost(host: String): Boolean =
            host == "images.jcomic.net" ||
                host.endsWith(".jcomic.net") ||
                host.contains("jmapinode") ||
                host.contains("jmapic") ||
                host.contains("jmaproxy") ||
                host.contains("18comic") ||
                host.contains("cdntwice") ||
                host.contains("cdnsha") ||
                host.contains("cdnaspa") ||
                host.contains("cdnntr") ||
                host.contains("cdnhjk") ||
                host.contains("cdngwc") ||
                host.contains("cdn-msp") ||
                host.contains("asjmapic")

        private fun saveBypassCookies(url: String, result: BypassResult) {
            val httpUrl = url.toHttpUrlOrNull() ?: return
            val host = httpUrl.host.lowercase()
            if (result.userAgent.isNotBlank()) {
                clearedUserAgents[host] = result.userAgent
            }
            val jar = sharedCookieJar ?: return
            val okCookies = result.cookies.mapNotNull { c ->
                val cleanDomain = c.domain.trim().trimStart('.').lowercase().ifEmpty { host }
                runCatching {
                    Cookie.Builder()
                        .name(c.name)
                        .value(c.value)
                        .domain(cleanDomain)
                        .path(c.path.ifBlank { "/" })
                        .apply {
                            if (c.secure) secure()
                            if (c.httpOnly) httpOnly()
                        }
                        .build()
                }.getOrNull()
            }
            if (okCookies.isNotEmpty()) {
                jar.saveFromResponse(httpUrl, okCookies)
                val names = okCookies.joinToString(",") { it.name }
                println("D_过盾 已存入 ${okCookies.size} 枚 Cookie host=$host names=[$names]")
            }
        }
    }
}
