package com.venera.engineprobe

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

/**
 * ascii2d.net 探针：验证"无官方 API"的网页接口能不能走通。
 *
 * 全流程：
 * 1. GET 主页抠两枚 Rails CSRF token（uri 表单一枚、file 表单一枚）；
 * 2. POST /search/uri（URL 反搜）→ 跟 302 到 /search/color/{hash}；
 * 3. GET /search/color/{hash} 解析色合检索结果；
 * 4. GET /search/bovw/{hash} 解析特征检索结果；
 * 5. 下载同一张图 POST /search/file（multipart，文件反搜）。
 *
 * 判读：
 * - POST 回 403 + "Just a moment" ⇒ Cloudflare 拦写请求，这条路要过盾才通；
 * - 200 + 解析出条目 ⇒ 接入可行，这里的选择器就是以后正式件的雏形。
 *
 * 测试图从 Safebooru 现抓一张（ascii2d 收录了 booru 图，必命中）。
 */
fun main() {
    val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(false) // 302 手动跟：hash 要从 Location 里抠出来复用
        .cookieJar(object : CookieJar {
            private val store = LinkedHashMap<HttpUrl, List<Cookie>>()
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                store[url] = cookies
            }
            override fun loadForRequest(url: HttpUrl): List<Cookie> =
                store.flatMap { it.value }.filter { it.matches(url) }
        })
        .build()

    val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"

    fun get(url: String, referer: String? = null): Response =
        http.newCall(
            Request.Builder().url(url)
                .header("User-Agent", UA)
                .apply { referer?.let { header("Referer", it) } }
                .build()
        ).execute()

    // ---- 0. 测试图：Safebooru 现抓 ----
    println("[0] 从 Safebooru 抓测试图…")
    val postsBody = get("https://safebooru.org/index.php?page=dapi&s=post&q=index&json=1&limit=3").use {
        it.body?.string().orEmpty()
    }
    val post = Regex("\\{[^{}]+\\}").findAll(postsBody).firstOrNull { m ->
        "\"file_url\"".toRegex().containsMatchIn(m.value)
    }?.value ?: error("safebooru 没吐出带 file_url 的条目：${postsBody.take(300)}")
    val fileUrl = Regex("\"file_url\":\"([^\"]+)\"").find(post)!!.groupValues[1]
        .replace("\\/", "/")
        .let { if (it.startsWith("http")) it else "https://safebooru.org$it" }
    println("    测试图：$fileUrl")
    val imageBytes = get(fileUrl).use { resp ->
        require(resp.isSuccessful) { "下载测试图失败 ${resp.code}" }
        resp.body!!.bytes()
    }
    println("    图字节：${imageBytes.size}")

    // ---- 1. 主页 + CSRF token ----
    println("[1] GET ascii2d 主页抠 token…")
    val homeResp = get("https://ascii2d.net/")
    val homeBody = homeResp.use { it.body?.string().orEmpty() }
    println("    主页状态：${homeResp.code}  body=${homeBody.length}B")
    if (homeResp.code != 200) {
        println("    ⚠️ 主页都进不去：${homeBody.take(200)}")
        return
    }
    val home = Jsoup.parse(homeBody)
    val uriToken = home.selectFirst("form[action=/search/uri] input[name=authenticity_token]")?.attr("value")
    val fileToken = home.selectFirst("form[action=/search/file] input[name=authenticity_token]")?.attr("value")
    println("    uriToken=${uriToken?.take(16)}… fileToken=${fileToken?.take(16)}…")
    if (uriToken.isNullOrBlank() || fileToken.isNullOrBlank()) {
        println("    ⚠️ 抠不到 token（页面结构变了或被挑战）：${homeBody.take(300)}")
        return
    }

    fun bodyOrChallenge(resp: Response, tag: String): String? {
        val text = resp.use { it.body?.string().orEmpty() }
        if (resp.code == 403 || text.contains("Just a moment") || text.contains("challenge-platform")) {
            println("    ❌ $tag 被 Cloudflare 拦（${resp.code}）：${text.take(200)}")
            return null
        }
        println("    $tag 状态 ${resp.code} body=${text.length}B")
        if (resp.code != 200) {
            println("    ⚠️ 非 200：${text.take(200)}")
            return null
        }
        return text
    }

    // ---- 2. URL 反搜 POST /search/uri ----
    println("[2] POST /search/uri …")
    val postResp = http.newCall(
        Request.Builder().url("https://ascii2d.net/search/uri")
            .header("User-Agent", UA)
            .header("Referer", "https://ascii2d.net/")
            .post(
                FormBody.Builder()
                    .add("utf8", "✓")
                    .add("authenticity_token", uriToken)
                    .add("uri", fileUrl)
                    .build()
            )
            .build()
    ).execute()
    println("    POST 状态：${postResp.code}  Location: ${postResp.header("Location") ?: "-"}")
    val colorHash = postResp.header("Location")
        ?.substringAfter("/search/color/", "")
        ?.takeIf { it.isNotBlank() }
    if (postResp.code !in 300..399 || colorHash == null) {
        bodyOrChallenge(postResp, "uri 反搜")
        println("    ⚠️ 没拿到 302/hash，走不通")
        return
    }
    println("    colorHash=$colorHash")

    // ---- 3. 色合检索结果页解析 ----
    println("[3] GET /search/color/$colorHash …")
    val colorBody = bodyOrChallenge(get("https://ascii2d.net/search/color/$colorHash", "https://ascii2d.net/"), "色合结果页") ?: return
    val colorDoc = Jsoup.parse(colorBody)
    val externals = colorDoc.select("a.external")
    println("    a.external 条数：${externals.size}")
    if (externals.isEmpty()) {
        // 结构认不得就 dump 一条原始行，以后正式件按这个定选择器
        val sample = colorDoc.body()?.html()?.take(2500) ?: ""
        println("    ⚠️ 没解析出条目，原始结构片段：\n$sample")
    } else {
        externals.take(3).forEach { a ->
            val row = a.closest("div.row") ?: a.parents().firstOrNull()
            println("    —— 条目 ——")
            println("      外链: ${a.attr("href")}")
            println("      外链文字: ${a.text().take(80)}")
            row?.select("img")?.firstOrNull()?.let {
                println("      缩略图: ${it.absUrl("src")}")
            }
            row?.select("small")?.map { it.text() }?.take(3)?.forEach {
                println("      small: ${it.take(100)}")
            }
        }
    }

    // ---- 4. 特征检索 bovw ----
    println("[4] GET /search/bovw/$colorHash …")
    val bovwBody = bodyOrChallenge(get("https://ascii2d.net/search/bovw/$colorHash", "https://ascii2d.net/"), "特征结果页") ?: return
    val bovwDoc = Jsoup.parse(bovwBody)
    println("    bovw a.external 条数：${bovwDoc.select("a.external").size}")

    // ---- 5. 文件反搜 POST /search/file ----
    println("[5] POST /search/file（multipart 上传同一张图）…")
    val fileResp = http.newCall(
        Request.Builder().url("https://ascii2d.net/search/file")
            .header("User-Agent", UA)
            .header("Referer", "https://ascii2d.net/")
            .post(
                MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("utf8", "✓")
                    .addFormDataPart("authenticity_token", fileToken)
                    .addFormDataPart(
                        "file", "probe.jpg",
                        imageBytes.toRequestBody("image/jpeg".toMediaType()),
                    )
                    .build()
            )
            .build()
    ).execute()
    println("    POST 状态：${fileResp.code}  Location: ${fileResp.header("Location") ?: "-"}")
    if (fileResp.code in 300..399) {
        val hash2 = fileResp.header("Location")?.substringAfter("/search/color/", "")
        if (!hash2.isNullOrBlank()) {
            val body2 = bodyOrChallenge(get("https://ascii2d.net/search/color/$hash2", "https://ascii2d.net/"), "文件反搜结果页") ?: return
            println("    文件反搜 a.external 条数：${Jsoup.parse(body2).select("a.external").size}")
        }
    } else {
        bodyOrChallenge(fileResp, "file 反搜")
    }

    println("[完] 探针跑完，判读见上面各段")
}
