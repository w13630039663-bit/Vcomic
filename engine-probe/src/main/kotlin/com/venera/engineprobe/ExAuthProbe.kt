package com.venera.engineprobe

import com.google.gson.Gson
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit

/**
 * exhentai 空响应的终审探针：真 Edge + 注入数据罐里的 Cookie + 访问 exhentai.org 量响应体。
 *
 * 判读：真 Edge 出内容 ⇒ 客户端指纹被 Cloudflare 风控（okhttp/curl 都过不去）；
 * 真 Edge 仍空 ⇒ 罐里这套会话本身没有里站权限（抓取的账号/会话不对，须重抓）。
 */
fun main() {
    val jar = DesktopCookieJar(
        DesktopCookieJar.fileIn(File(System.getenv("LOCALAPPDATA") ?: error("没有 LOCALAPPDATA"), "venera")),
    )
    val url = okhttp3.HttpUrl.Builder().scheme("https").host("exhentai.org").build()
    val cookies = jar.loadForRequest(url)
    println("[exauth] 罐里 exhentai 相关 Cookie: ${cookies.joinToString { it.name }}")

    val edge = listOf(
        File(System.getenv("ProgramFiles(x86)") ?: "", "Microsoft/Edge/Application/msedge.exe"),
        File(System.getenv("ProgramFiles") ?: "", "Microsoft/Edge/Application/msedge.exe"),
    ).firstOrNull { it.isFile } ?: error("找不到 Edge")

    val port = 19222 + (System.currentTimeMillis() % 1000).toInt()
    val profile = File(System.getProperty("java.io.tmpdir"), "exauth-${UUID.randomUUID()}")
    val proc = ProcessBuilder(
        edge.absolutePath,
        "--app=about:blank",
        "--remote-debugging-port=$port",
        "--user-data-dir=${profile.absolutePath}",
        "--no-first-run", "--no-default-browser-check",
        "--window-size=800,600",
    ).start()

    try {
        val http = HttpClient.newHttpClient()
        // 等调试端口就绪
        var wsUrl: String? = null
        for (i in 0 until 40) {
            runCatching {
                val body = http.send(
                    java.net.http.HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port/json")).GET().build(),
                    java.net.http.HttpResponse.BodyHandlers.ofString(),
                ).body()
                val pages = runCatching {
                    @Suppress("UNCHECKED_CAST")
                    Gson().fromJson(body, List::class.java) as? List<Map<String, Any?>>
                }.getOrNull().orEmpty()
                wsUrl = pages.firstOrNull { it["type"] == "page" }?.get("webSocketDebuggerUrl") as? String
            }.onSuccess { if (wsUrl != null) return@onSuccess }
            if (wsUrl != null) break
            Thread.sleep(250)
        }
        wsUrl ?: error("CDP 端口没就绪")
        println("[exauth] CDP 就绪 ws=${wsUrl!!.take(60)}")

        var cmdId = 0
        fun cmd(method: String, params: Map<String, Any?>): String {
            val id = ++cmdId
            val f = CompletableFuture<String>()
            val ws = http.newWebSocketBuilder().buildAsync(URI.create(wsUrl!!), object : WebSocket.Listener {
                private val sb = StringBuilder()
                override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
                    sb.append(data)
                    if (last) f.complete(sb.toString())
                    return super.onText(webSocket, data, last)
                }
            }).get(5, TimeUnit.SECONDS)
            val body = Gson().toJson(mapOf("id" to id, "method" to method, "params" to params))
            ws.sendText(body, true)
            val r = try {
                f.get(15, TimeUnit.SECONDS)
            } catch (e: Exception) {
                println("[exauth] 命令超时/失败: $method 因=${e.message}")
                throw e
            }
            println("[exauth] ok: $method")
            runCatching { ws.abort() }
            return r
        }

        // 注入 Cookie（exhentai.org 与 e-hentai.org 两个域）
        // min 模式（-Dexauth.min=1）：只带 igneous + ipb 两件套，剔除陈旧的会话 Cookie
        // （sk/ipb_session_id/event/cf_clearance），判「陈旧会话 Cookie 毒死整单」还是「另有变量」。
        val inject = if (System.getenv("EXAUTH_MIN") == "1") {
            cookies.filter { it.name in setOf("igneous", "ipb_member_id", "ipb_pass_hash") }
        } else {
            cookies
        }
        println("[exauth] 注入 ${inject.size} 枚: ${inject.joinToString { it.name }}")
        cmd("Network.enable", emptyMap())
        inject.forEach { c ->
            cmd(
                "Network.setCookie",
                mapOf(
                    "name" to c.name, "value" to c.value,
                    "domain" to c.domain, "path" to (c.path.ifBlank { "/" }),
                    "secure" to c.secure, "httpOnly" to c.httpOnly,
                ),
            )
        }
        cmd("Page.enable", emptyMap())
        cmd("Page.navigate", mapOf("url" to (System.getenv("EXAUTH_URL") ?: "https://exhentai.org/")))
        Thread.sleep(6_000)
        val eval = cmd(
            "Runtime.evaluate",
            mapOf(
                "expression" to "JSON.stringify({len: document.documentElement.outerHTML.length, type: document.contentType, title: document.title, head: document.documentElement.outerHTML.slice(0,120)})",
                "returnByValue" to true,
            ),
        )
        println("[exauth] 真Edge结果=$eval")
        runCatching { cmd("Browser.close", emptyMap()) }
    } finally {
        proc.destroyForcibly()
        profile.deleteRecursively()
    }
}
