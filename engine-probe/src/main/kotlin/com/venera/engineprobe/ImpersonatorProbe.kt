package com.venera.engineprobe

import com.github.zhkl0228.impersonator.ImpersonatorFactory
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.OkHttpClientFactory
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

/**
 * impersonator（zhkl0228）指纹伪装探针：不用真浏览器，JVM 内直接过 CF。
 *
 * 伪装层：TLS/JA3/JA4（BouncyCastle 复刻 Chrome ClientHello）+ HTTP/2 指纹 +
 * 应用层头形状（Sec-Fetch 系按表单导航拼齐）。三层全真才像浏览器。
 *
 * 判读（对照 Ascii2dProbe 三层验证链）：
 * - POST /search/uri 200/302 ⇒ JVM 内过 CF 成立，正式件可直接顶替 HttpEngine 的 client；
 * - 仍 403 ⇒ 指纹库压不过 CF 的检测阈值，退回 CDP 真浏览器桥。
 * 顺带测 safebooru 图片直链（每日热门那路的 CF 敏感路径）。
 */
fun main() {
    val jar = object : CookieJar {
        private val store = LinkedHashMap<HttpUrl, List<Cookie>>()
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            store[url] = cookies
        }
        override fun loadForRequest(url: HttpUrl): List<Cookie> =
            store.flatMap { it.value }.filter { it.matches(url) }
    }
    val http: OkHttpClient = OkHttpClientFactory.create(ImpersonatorFactory.macChrome())
        .setConnectTimeout(20, TimeUnit.SECONDS)
        .setReadTimeout(120, TimeUnit.SECONDS)
        .newHttpClient()
        .newBuilder()
        .cookieJar(jar)
        .build()

    // Chrome 152（README 捕获版本）mac UA，与 macChrome profile 的 ClientHello 同版本
    val UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36"

    fun get(url: String): okhttp3.Response =
        http.newCall(
            Request.Builder().url(url)
                .header("sec-ch-ua", "\"Chromium\";v=\"152\", \"Not:A-Brand\";v=\"24\"")
                .header("sec-ch-ua-mobile", "?0")
                .header("sec-ch-ua-platform", "\"macOS\"")
                .header("upgrade-insecure-requests", "1")
                .header("User-Agent", UA)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .header("Sec-Fetch-Site", "none")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-User", "?1")
                .header("Sec-Fetch-Dest", "document")
                .header("Accept-Encoding", "gzip, deflate, br, zstd")
                .header("Accept-Language", "ja,zh-CN;q=0.9,en;q=0.8")
                .build()
        ).execute()

    // ---- 0. 指纹诊断：tls.peet.ws 回报我们实际发出的 JA3/JA4 ----
    println("[imp] ⓪ 指纹诊断…")
    get("https://tls.peet.ws/api/all").use { resp ->
        val body = resp.body?.string().orEmpty()
        println("    peetws 状态: ${resp.code}")
        listOf("\"ja3\"", "\"ja3_hash\"", "\"ja4\"", "\"akamai_fingerprint\"", "\"peetprint\"").forEach { key ->
            Regex("\"${key.substring(1, key.length - 1)}\"\\s*:\\s*\"[^\"]{0,80}\"").find(body)?.value?.let { println("    $it") }
        }
    }

    // ---- 1. Safebooru：API + 图片直链（每日热门的 CF 敏感路径）----
    println("[imp] ① Safebooru API…")
    val apiBody = get("https://safebooru.org/index.php?page=dapi&s=post&q=index&json=1&limit=3").use {
        println("    API 状态: ${it.code}")
        it.body?.string().orEmpty()
    }
    val fileUrl = Regex("\"file_url\":\"([^\"]+)\"").find(apiBody)?.groupValues?.get(1)
        ?.replace("\\/", "/")
        ?.let { if (it.startsWith("http")) it else "https://safebooru.org$it" }
    if (fileUrl == null) {
        println("    ⚠️ API 没吐图：${apiBody.take(200)}")
    } else {
        println("    测试图：$fileUrl")
        get(fileUrl).use {
            val bytes = if (it.isSuccessful) it.body!!.bytes().size.toLong() else -1L
            println("    图片直链状态: ${it.code}  ${bytes}B")
        }
    }

    // ---- 2. ascii2d 全流程：主页抠 token → POST 反搜 → 结果页 ----
    println("[imp] ② ascii2d 主页…")
    val homeBody = get("https://ascii2d.net/").use {
        println("    主页状态: ${it.code}")
        println("    set-cookie: ${it.headers.values("set-cookie").joinToString(" | ") { c -> c.substringBefore(';').take(50) }}")
        println("    alt-svc: ${it.header("alt-svc") ?: "-"}")
        it.body?.string().orEmpty()
    }
    println("    罐里 cookie: ${jar.loadForRequest("https://ascii2d.net/".toHttpUrlOrNull()!!).joinToString { it.name }}")
    val token = Jsoup.parse(homeBody)
        .selectFirst("form[action=/search/uri] input[name=authenticity_token]")?.attr("value")
    if (token.isNullOrBlank()) {
        println("    ⚠️ 抠不到 token：${homeBody.take(200)}")
        return
    }
    println("    token=${token.take(16)}…")

    val testUri = "https://safebooru.org/images/4439/6192c4ce5b6f7e383b735d2101406f3c.jpeg"

    // ---- 2.5 官方扩展同款入口：GET /search/url/{enc}?type=color（Chrome 扩展 onclick 就开这个页面）----
    println("[imp] ②.5 GET /search/url/ 官方扩展同款入口…")
    val extUrl = "https://ascii2d.net/search/url/" + java.net.URLEncoder.encode(testUri, "UTF-8") + "?type=color"
    get(extUrl).use { resp ->
        val finalUrl = resp.request.url.toString()
        println("    最终状态: ${resp.code}  最终URL: $finalUrl")
        val body = resp.body?.string().orEmpty()
        if (body.contains("Just a moment")) {
            println("    ❌ GET 入口被 CF 拦")
        } else {
            val rows = Jsoup.parse(body).select("div.row.item-box")
            println("    结果条目: ${rows.size}  hash=${finalUrl.substringAfter("/search/color/", "-")}")
            rows.take(2).forEach { row ->
                val size = row.selectFirst(".info-box small.text-muted")?.text()
                val link = row.selectFirst(".detail-box a[target=_blank]")?.attr("href")
                println("      —— ${size ?: "?"}  ${link?.take(70) ?: "-"}")
            }
        }
    }

    println("[imp] ③ POST /search/uri（对照：头顺序按 Chrome POST 导航逐位对齐）…")
    // Chrome 表单导航的 H2 字段顺序：content-type 紧跟 sec-ch-ua-platform 之后。
    // okhttp 的 FormBody 会把 content-type 补在末尾 —— 顺序露馅。改用无类型 body + 显式头。
    val form = "utf8=%E2%9C%93" +
        "&authenticity_token=" + java.net.URLEncoder.encode(token, "UTF-8") +
        "&uri=" + java.net.URLEncoder.encode(testUri, "UTF-8")
    val postResp = http.newCall(
        Request.Builder().url("https://ascii2d.net/search/uri")
            .header("Cache-Control", "max-age=0")
            .header("sec-ch-ua", "\"Chromium\";v=\"152\", \"Not:A-Brand\";v=\"24\"")
            .header("sec-ch-ua-mobile", "?0")
            .header("sec-ch-ua-platform", "\"macOS\"")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Upgrade-Insecure-Requests", "1")
            .header("User-Agent", UA)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
            .header("Origin", "https://ascii2d.net")
            .header("Sec-Fetch-Site", "same-origin")
            .header("Sec-Fetch-Mode", "navigate")
            .header("Sec-Fetch-User", "?1")
            .header("Sec-Fetch-Dest", "document")
            .header("Referer", "https://ascii2d.net/")
            .header("Accept-Encoding", "gzip, deflate, br, zstd")
            .header("Accept-Language", "ja,zh-CN;q=0.9,en;q=0.8")
            .post(form.toByteArray().toRequestBody(null))
            .build()
    ).execute()
    val finalBody = postResp.use { resp ->
        println("    最终状态: ${resp.code}  最终URL: ${resp.request.url}")
        resp.body?.string().orEmpty()
    }
    if (finalBody.contains("Just a moment")) {
        println("    ❌ POST 仍被 CF 拦（403 挑战页）")
        return
    }
    val doc = Jsoup.parse(finalBody)
    val rows = doc.select("div.row.item-box")
    println("    结果条目: ${rows.size}")
    rows.take(3).forEach { row ->
        val size = row.selectFirst(".info-box small.text-muted")?.text()
        val links = row.select(".detail-box a[target=_blank]").map { it.attr("href") }
        val site = row.selectFirst(".detail-box h6 small.text-muted")?.text()
        println("      —— ${size ?: "?"}  站点:${site ?: "?"}  外链:${links.firstOrNull()?.take(70) ?: "-"}")
    }
    println("[imp] 探针跑完")
}
