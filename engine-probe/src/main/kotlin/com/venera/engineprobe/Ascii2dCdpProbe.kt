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
 * ascii2d CDP 桥探针：okhttp/curl 的 POST 被 Cloudflare 按 TLS 指纹拦死（Ascii2dProbe 已证），
 * 唯一活路是**真浏览器环境**里发 POST。本探针拉真 Edge（CDP），页面内 fetch 走完整反搜流程：
 *
 * 1. 导航到主页 → 等 readyState complete（出现 CF 挑战时等人工点过）；
 * 2. 页面内抠 authenticity_token → fetch POST /search/uri → 302 自动跟随到结果页；
 * 3. 结果 HTML 里数 a.external、抽样本条目；
 * 4. 顺带 fetch /search/bovw/{hash}（特征检索）。
 *
 * 判读：
 * - `--headless=new` 能通 ⇒ 正式件可以后台无感跑（最优形状）；
 * - headless 拦、headed 通 ⇒ 正式件要挂一个最小化真浏览器会话；
 * - headed 也拦死 ⇒ 这条路放弃，降级成"外链打开"。
 */
fun main() {
    val imageUri = "https://safebooru.org/images/4439/6192c4ce5b6f7e383b735d2101406f3c.jpeg"
    val headless = true  // 临时硬编码：验证 headless 能否过 CF
    println("[a2d] headless=$headless 测试图=$imageUri")

    val edge = listOf(
        File(System.getenv("ProgramFiles(x86)") ?: "", "Microsoft/Edge/Application/msedge.exe"),
        File(System.getenv("ProgramFiles") ?: "", "Microsoft/Edge/Application/msedge.exe"),
    ).firstOrNull { it.isFile } ?: error("找不到 Edge")

    val port = 19322 + (System.currentTimeMillis() % 1000).toInt()
    val profile = File(System.getProperty("java.io.tmpdir"), "a2d-${UUID.randomUUID()}")
    val cmdArgs = mutableListOf(
        edge.absolutePath,
        "--app=about:blank",
        "--remote-debugging-port=$port",
        "--user-data-dir=${profile.absolutePath}",
        "--no-first-run", "--no-default-browser-check",
        "--window-size=900,700",
    )
    if (headless) cmdArgs.add("--headless=new")
    val proc = ProcessBuilder(cmdArgs).start()

    val http = HttpClient.newHttpClient()
    try {
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
        println("[a2d] CDP 就绪")

        var cmdId = 0
        fun cmd(method: String, params: Map<String, Any?>): Map<*, *> {
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
            ws.sendText(Gson().toJson(mapOf("id" to id, "method" to method, "params" to params)), true)
            val raw = f.get(30, TimeUnit.SECONDS)
            runCatching { ws.abort() }
            val root = Gson().fromJson(raw, Map::class.java) as Map<*, *>
            return (root["result"] as? Map<*, *>) ?: emptyMap<Any, Any>()
        }

        fun evalJs(expression: String, awaitPromise: Boolean = false): Any? {
            val result = cmd(
                "Runtime.evaluate",
                mapOf("expression" to expression, "returnByValue" to true, "awaitPromise" to awaitPromise),
            )
            val inner = result["result"] as? Map<*, *> ?: return null
            if (inner["subtype"] == "error" || inner["className"] != null) {
                println("    JS 异常: $inner")
                return null
            }
            return inner["value"]
        }

        cmd("Page.enable", emptyMap())
        cmd("Page.navigate", mapOf("url" to "https://ascii2d.net/"))

        // 等页面就绪：readyState complete 且不是 CF 挑战页；挑战页最多等 90s 人工
        var ready = false
        for (i in 0 until 30) {
            Thread.sleep(3000)
            val state = evalJs("JSON.stringify({rs: document.readyState, title: document.title})") as? String
            println("[a2d] 页面状态 $i: $state")
            if (state != null && state.contains("\"rs\":\"complete\"") && !state.contains("moment")) {
                ready = true
                break
            }
            if (state != null && state.contains("moment")) println("[a2d] ⚠️ CF 挑战页，请在弹出的窗口里点过人机验证…")
        }
        if (!ready) error("页面没就绪（挑战没过或超时）")

        // 全流程：真表单提交（不走 fetch——真 Edge 里 fetch POST 也被 403，WAF 按 Sec-Fetch-Mode 分流），
        // 表单导航才是浏览器扩展的同款形状。
        val jsSubmit = """
        (() => {
            try {
                location.href = '/search/url/' + encodeURIComponent('$imageUri') + '?type=color';
                return 'navigating';
            } catch (e) { return 'error:' + String(e); }
        })()
        """.trimIndent()
        val submitted = evalJs(jsSubmit) as? String
        println("[a2d] 表单提交：$submitted")

        // 等导航完成：结果页 URL 带 /search/color/；若是 CF 挑战页 title 是 Just a moment
        // color 检索站方现算，302 可能要等几十秒 —— 等待拉满 90s
        var r1: String? = null
        for (i in 0 until 30) {
            Thread.sleep(3000)
            val state = evalJs(
                "JSON.stringify({url: location.href, title: document.title, rs: document.readyState})",
            ) as? String ?: continue
            println("[a2d] 提交后状态 $i: $state")
            if (state.contains("moment")) {
                println("[a2d] ⚠️ 提交后撞 CF 挑战，请在窗口里点过…")
                continue
            }
            if (state.contains("/search/color/")) {
                // 结果条目 = 含缩略图 img 的 div.row（信息列是它的兄弟列）
                val jsRead = """
                (() => {
                    const rows = Array.from(document.querySelectorAll('div.row')).filter(
                        r => r.querySelector('img[src*="/thumbnail/"]'));
                    return JSON.stringify({
                        rows: rows.length,
                        sample: rows.length ? rows[0].outerHTML.slice(0, 2500) : ''
                    });
                })()
                """.trimIndent()
                Thread.sleep(4000) // 等结果 JS 注入
                r1 = evalJs(jsRead) as? String
                if (r1 != null && !r1.contains("\"rows\":0")) break
            }
        }
        println("[a2d] URL反搜结果：$r1")

        val finalUrl = (evalJs("location.href") as? String).orEmpty()
        val hash = finalUrl.substringAfter("/search/color/", "").substringBefore("?").takeIf { it.isNotBlank() }
        if (!hash.isNullOrBlank()) {
            evalJs("location.href = '/search/bovw/$hash'")
            var bovwCount = -1
            for (i in 0 until 25) {
                Thread.sleep(3000)
                val state = evalJs(
                    "JSON.stringify({url: location.href, title: document.title, n: document.querySelectorAll('a.external').length})",
                ) as? String ?: continue
                println("[a2d] bovw 状态 $i: $state")
                if (state.contains("moment")) continue
                if (state.contains("\"url\":\"https://ascii2d.net/search/bovw/")) {
                    val n = Regex("\"n\":(\\d+)").find(state)?.groupValues?.get(1)?.toInt() ?: 0
                    val complete = state.contains("\"rs\":\"complete\"")
                    // bovw 检索慢，结果由页面 JS 注入：等 complete 且条数涨出来，超时取最后一次读数
                    if (complete && n > 0 || i == 24) {
                        bovwCount = n
                        break
                    }
                }
            }
            println("[a2d] 特征检索条数：$bovwCount")
        }

        runCatching { cmd("Browser.close", emptyMap()) }
        println("[a2d] 探针跑完")
    } finally {
        proc.destroyForcibly()
        profile.deleteRecursively()
    }
}
