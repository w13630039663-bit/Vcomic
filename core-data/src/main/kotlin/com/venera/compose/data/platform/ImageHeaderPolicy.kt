package com.venera.compose.data.platform

import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.ConcurrentHashMap

/**
 * 防盗链请求头策略中心。
 *
 * 原版由源解析层把 ImageLoadingConfig（Referer / UA / Cookie）交给宿主侧图片加载器；
 * 本项目此前 ChapterPages.headers 生成了却零消费者，导致拷贝漫画、包子等站的
 * 封面与内页必然 403。这里把「按 host 生效的请求头」集中起来，交给 Coil3 使用的
 * OkHttp 客户端统一注入。
 *
 * 用法：拿到章节/详情的 headers 后调用 publishForUrls，之后命中同 host 的图片请求自动带上。
 */
object ImageHeaderPolicy {

    /** 运行时发布的规则：host(或后缀) -> 请求头，优先级高于内置 */
    private val perHost = ConcurrentHashMap<String, Map<String, String>>()

    /**
     * 桌面 Chrome 串，E-Hentai 家族四个 host 共用。四行逐字相同是刻意的 ——
     * `ehgt.org` / `e-hentai.org` / `exhentai.org` / `hath.network` 是同一家族的四颗 host，
     * 站方按 UA 放行，任何一条改歪就成"封面出、内页不出"那种半残状态。
     *
     * ⚠️ 与 `engine/JsHttpHandler.kt:62` 那颗 **Chrome/119** 是两枚不同的值、两处不同的链路，
     * 不是漏收口，别合。
     */
    private const val DESKTOP_BROWSER_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

    /** 内置规则：不依赖解析结果的通用防盗链 */
    private val builtin: Map<String, Map<String, String>> = mapOf(
        "mangadex.org" to mapOf("Referer" to "https://mangadex.org/"),
        "mangadex.cc" to mapOf("Referer" to "https://mangadex.org/"),
        "copymanga.com" to mapOf("Referer" to "https://www.copymanga.com/"),
        "copymanga.org" to mapOf("Referer" to "https://www.copymanga.org/"),
        "copymanhua.com" to mapOf("Referer" to "https://www.copymanhua.com/"),
        "baozimh.org" to mapOf("Referer" to "https://baozimh.org/"),
        "uc1zali8.com" to mapOf("Referer" to "https://baozimh.org/"),
        "jmapinodeudzn.net" to mapOf(
            "Referer" to "https://localhost/",
            "X-Requested-With" to "com.example.app"
        ),
        "18comic.vip" to mapOf(
            "Referer" to "https://localhost/",
            "X-Requested-With" to "com.example.app"
        ),
        "18comic.org" to mapOf(
            "Referer" to "https://localhost/",
            "X-Requested-With" to "com.example.app"
        ),
        "hamreus.com" to mapOf("Referer" to "https://www.manhuagui.com/"),
        "manhuagui.com" to mapOf("Referer" to "https://www.manhuagui.com/"),
        "manhuaren.com" to mapOf("Referer" to "https://www.manhuaren.com/"),
        "komiic.com" to mapOf("Referer" to "https://komiic.com/"),
        "wnacg.com" to mapOf("Referer" to "https://www.wnacg.com/"),
        "wnacg.org" to mapOf("Referer" to "https://www.wnacg.org/"),
        "hitomi.la" to mapOf("Referer" to "https://hitomi.la/"),
        "nhentai.net" to mapOf("Referer" to "https://nhentai.net/"),
        "picacg.com" to mapOf("User-Agent" to "okhttp/3.8.1"),
        // cdn.donmai.us 在 Cloudflare 下会拦截移动端浏览器 UA（返回 403 挑战页），必须使用非浏览器 UA。
        "donmai.us" to mapOf("User-Agent" to UserAgentStrings.APP_USER_AGENT),
        // i.pximg.net 取图必须带站内 Referer，否则返回 403（/ajax 接口不受此限）。
        "pximg.net" to mapOf("Referer" to "https://www.pixiv.net/"),
        // Gelbooru 图片 CDN（thumbnails/samples/images）开启防盗链，无站内 Referer 会 302 跳 hotlink 页；
        // UA 仅用于与接口侧保持一致，非必需。
        "gelbooru.com" to mapOf(
            "Referer" to "https://gelbooru.com/",
            "User-Agent" to UserAgentStrings.APP_USER_AGENT,
        ),
        "ehgt.org" to mapOf(
            "Referer" to "https://e-hentai.org/",
            "User-Agent" to DESKTOP_BROWSER_UA
        ),
        "e-hentai.org" to mapOf(
            "Referer" to "https://e-hentai.org/",
            "User-Agent" to DESKTOP_BROWSER_UA
        ),
        "exhentai.org" to mapOf(
            "Referer" to "https://exhentai.org/",
            "User-Agent" to DESKTOP_BROWSER_UA
        ),
        "hath.network" to mapOf(
            "Referer" to "https://e-hentai.org/",
            "User-Agent" to DESKTOP_BROWSER_UA
        ),
    )

    /** 从一批图片 URL 推出各自的 host，并绑定这批请求头 */
    fun publishForUrls(urls: List<String>, headers: Map<String, String>) {
        if (headers.isEmpty()) return
        for (u in urls) {
            val host = hostOf(u)?.lowercase() ?: continue
            perHost[host] = mergeHeadersCaseInsensitive(perHost[host].orEmpty(), headers)
        }
    }

    fun publish(host: String, headers: Map<String, String>) {
        val h = host.lowercase().removePrefix("www.")
        if (h.isEmpty() || headers.isEmpty()) return
        perHost[h] = mergeHeadersCaseInsensitive(perHost[h].orEmpty(), headers)
    }

    fun clear() = perHost.clear()

    private fun mergeHeadersCaseInsensitive(
        base: Map<String, String>,
        override: Map<String, String>,
    ): Map<String, String> {
        if (base.isEmpty()) return override
        if (override.isEmpty()) return base
        val out = LinkedHashMap<String, String>(base.size + override.size)
        val keyByLower = HashMap<String, String>(base.size + override.size)
        base.forEach { (k, v) ->
            out[k] = v
            keyByLower[k.lowercase()] = k
        }
        override.forEach { (k, v) ->
            val existing = keyByLower[k.lowercase()]
            if (existing != null && existing != k) {
                out.remove(existing)
            }
            out[k] = v
            keyByLower[k.lowercase()] = k
        }
        return out
    }

    /**
     * 取某个 URL 应附加的请求头（运行时发布优先于内置；长后缀赢）。
     *
     * **`Accept-Encoding` 在这里一律剔除**，因为这张表的产物只有一类消费者：
     * 往 OkHttp 请求上贴头。而 OkHttp 的透明解压有个前提 —— 只有当这个头是**它自己**补的，
     * 它才会在响应侧拆掉 `Content-Encoding`；调用方（这里是源 JS 照抄的浏览器头）自己写上，
     * OkHttp 就当作"编码你自己负责"，把 **gzip 字节原样交出来**。
     * 禁漫正文页首 4 字节 `1f8b08`、`inJustDecodeBounds` 读出 `-1x-1`，
     * 于是每张图都倒在"去混淆失败"那一句上 —— 病根从来不在混淆算法。
     * 剔除放在这个函数而不是三个贴头点各写一遍：漏一处就复发一次（下载侧 2026 修过同类病，
     * 当时取图侧没跟着修，就是"各写一遍"的代价）。
     */
    fun headersFor(url: String): Map<String, String> {
        val host = hostOf(url)?.lowercase() ?: return emptyMap()
        val runtime = perHost.entries
            .filter { host == it.key || host.endsWith(".${it.key}") }
            .sortedByDescending { it.key.length }
            .firstOrNull()?.value
        val base = builtin.entries
            .filter { host == it.key || host.endsWith(".${it.key}") }
            .sortedByDescending { it.key.length }
            .firstOrNull()?.value
        val merged = mergeHeadersCaseInsensitive(base.orEmpty(), runtime.orEmpty())
        return merged.filterKeys { !it.equals("Accept-Encoding", ignoreCase = true) }
    }

    /**
     * 从 URL 取 host。
     *
     * ⚠️ 这里曾经有一个**让整张内置表全部失效**的 bug，留个记录免得再犯：
     * 旧写法是 `substringAfter("://").substringBefore("/").substringBefore("?", "")` ——
     * Kotlin 的 `substringBefore(delim, missingDelimiterValue)` 在**找不到分隔符时返回
     * 第二个参数**，而 host 在 `substringBefore("/")` 之后**不可能再含 `?`**
     * （`?` 属于 query，早被那个 `/` 一起截掉了）。于是这对**所有**正常 URL
     * 都返回 `""`，`takeIf { isNotEmpty() }` 再把它变成 null ——
     * 结果 `headersFor` 恒回空表，**整张防盗链表从来没生效过**。
     * 真机「Gelbooru 搜得出图、屏上全是灰卡」就是它：
     * 图片请求没带 Referer，站方 302 到 hotlink.php 返回 HTML，Coil 解不出图。
     *
     * 现在按「`?` 或 `/` **谁先出现就截到那里**」取，两种 URL 形态都对：
     * - `https://host/path/x.jpg` → host
     * - `https://host?x=1`（无路径）→ host
     */
    private fun hostOf(url: String): String? {
        val afterScheme = url.substringAfter("://", "")
        if (afterScheme.isEmpty()) return null
        val cut = listOf(afterScheme.indexOf('/'), afterScheme.indexOf('?'))
            .filter { it >= 0 }
            .minOrNull() ?: afterScheme.length
        return afterScheme.substring(0, cut).takeIf { it.isNotEmpty() }
    }
}

/**
 * 把 ImageHeaderPolicy 落到 OkHttp 请求链上。
 *
 * 只补「请求里还没有」的头，因此 API/脚本显式设置的头永远不会被覆盖
 * （对齐原版 Network.get 的头语义，也为后续 UA 策略改造铺路）。
 */
class ImageHeaderInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val extra = ImageHeaderPolicy.headersFor(request.url.toString())
        if (extra.isEmpty()) return chain.proceed(request)
        val builder = request.newBuilder()
        extra.forEach { (name, value) ->
            if (request.header(name) == null) builder.header(name, value)
        }
        return chain.proceed(builder.build())
    }
}
