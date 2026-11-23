package com.venera.desktop.platform

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.venera.desktop.DesktopErrorBus
import com.venera.desktop.sourcemanage.DesktopEdgeWebLogin
import com.venera.engineprobe.DesktopCloudflareCenter
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.WebSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 桌面端 Cloudflare 通用过盾桥（复用系统内置 Edge + CDP）：
 *
 * 1. 先以无头模式（`--headless=new`）在后台静默尝试自动过 JS 盾（6 秒内通过即零弹窗）；
 * 2. 若遇 Turnstile 等需人工点勾的挑战，自动切到独立应用小窗供用户点一次验证；
 * 3. 通过 CDP `Network.getCookies` 与 `navigator.userAgent` 抓回包含 `HttpOnly` 的完整 `cf_clearance`
 *    与配套浏览器 UA，写回共享 Cookie 罐供漫画引擎与图片加载同时生效。
 */
internal object DesktopCloudflareBridge {

    private val gson = Gson()
    private val mapType = object : TypeToken<Map<String, Any?>>() {}.type
    private val listMapType = object : TypeToken<List<Map<String, Any?>>>() {}.type
    private val portSeq = AtomicInteger(19500)

    /** 可选的 Edge 代理命令行参数提供器（对齐应用内直连/手动代理设置）。 */
    @Volatile
    var proxyArgsProvider: (() -> List<String>)? = null

    fun bypass(url: String): DesktopCloudflareCenter.BypassResult? {
        val edgeExe = DesktopEdgeWebLogin.findEdgeExecutable() ?: run {
            DesktopErrorBus.report(
                category = "过盾",
                headline = "未找到 Microsoft Edge，无法自动过 Cloudflare 盾",
                detail = url,
                dedupKey = "cf:no-edge",
            )
            return null
        }

        val navUrl = challengeNavigationUrl(url)

        // 第一阶段：无头模式先静默尝试 6.5 秒，纯 JS 盾可无感自动通过
        val headlessAttempt = runEdgePass(edgeExe, navUrl, headless = true, timeoutMs = 6_500L)
        if (headlessAttempt != null) {
            return headlessAttempt
        }

        // 第二阶段：需要人工点击人机框，拉出独立小窗并给出友好提示
        val host = navUrl.substringAfter("://", "").substringBefore('/').ifBlank { navUrl }
        DesktopErrorBus.report(
            category = "过盾验证",
            headline = "站点 $host 需要人机验证，请在弹出窗口勾选后等待自动继续",
            detail = navUrl,
            dedupKey = "cf:prompt:$host",
        )
        return runEdgePass(edgeExe, navUrl, headless = false, timeoutMs = 90_000L)
    }

    /** API 端点（如 .json/.xml）在浏览器打开常直接回 403 文本而非完整挑战页，过盾改走同域根路径 HTML。 */
    internal fun challengeNavigationUrl(url: String): String {
        val uri = runCatching { URI.create(url) }.getOrNull() ?: return url
        val path = uri.path.orEmpty().lowercase()
        val isApiEndpoint = path.endsWith(".json") || path.endsWith(".xml")
        return if (isApiEndpoint && !uri.scheme.isNullOrBlank() && !uri.host.isNullOrBlank()) {
            "${uri.scheme}://${uri.host}/"
        } else {
            url
        }
    }

    private fun runEdgePass(
        edgeExe: File,
        url: String,
        headless: Boolean,
        timeoutMs: Long,
    ): DesktopCloudflareCenter.BypassResult? {
        val port = 19500 + (portSeq.getAndIncrement() % 300)
        val tempDir = File(
            System.getProperty("java.io.tmpdir"),
            "venera-cf-${System.currentTimeMillis()}-$port",
        )
        tempDir.mkdirs()

        val args = mutableListOf(
            edgeExe.absolutePath,
            "--remote-debugging-port=$port",
            "--user-data-dir=${tempDir.absolutePath}",
            "--no-first-run",
            "--no-default-browser-check",
        )
        proxyArgsProvider?.invoke()?.let { args += it }
        if (headless) {
            args += "--headless=new"
            args += "--disable-gpu"
            args += url
        } else {
            args += "--app=$url"
            args += "--window-size=680,580"
        }

        val process = try {
            ProcessBuilder(args).start()
        } catch (e: Exception) {
            tempDir.deleteRecursively()
            return null
        }

        val http = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(3)).build()
        val deadline = System.currentTimeMillis() + timeoutMs
        var lastNonClearanceResult: DesktopCloudflareCenter.BypassResult? = null

        try {
            while (process.isAlive && System.currentTimeMillis() < deadline) {
                Thread.sleep(450L)
                val pagesJson = runCatching {
                    val req = HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:$port/json"))
                        .GET()
                        .build()
                    http.send(req, HttpResponse.BodyHandlers.ofString()).body()
                }.getOrNull() ?: continue

                val pages = runCatching {
                    gson.fromJson<List<Map<String, Any?>>>(pagesJson, listMapType)
                }.getOrNull() ?: continue

                val page = pages.firstOrNull { (it["type"] as? String) == "page" } ?: continue
                val currentUrl = (page["url"] as? String).orEmpty()
                val title = (page["title"] as? String).orEmpty()
                val wsUrl = (page["webSocketDebuggerUrl"] as? String).orEmpty()

                if (currentUrl.isBlank() || currentUrl.startsWith("about:") || currentUrl.startsWith("edge:")) {
                    continue
                }
                if (!isChallengeTitle(title) && title.isNotBlank() && wsUrl.isNotBlank()) {
                    // 等短暂稳定后抓取完整 HttpOnly Cookie 与同会话 UA
                    Thread.sleep(500L)
                    val extracted = extractCookiesAndUaViaCdp(wsUrl, url)
                    if (extracted != null && extracted.cookies.any { it.name == "cf_clearance" }) {
                        return extracted
                    }
                    if (extracted != null && extracted.cookies.isNotEmpty()) {
                        lastNonClearanceResult = extracted
                    }
                }
            }

            // 无头模式必须拿到 cf_clearance 才算成功，否则交由交互小窗处理；有头模式兜底返回最后抓到的 Cookie
            return if (headless) null else lastNonClearanceResult
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            return null
        } catch (_: Exception) {
            return null
        } finally {
            runCatching { closeEdgeViaCdp(http, port) }
            runCatching {
                if (!process.waitFor(2, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                }
            }
            runCatching { tempDir.deleteRecursively() }
        }
    }

    private fun isChallengeTitle(title: String): Boolean {
        val t = title.trim().lowercase()
        return "just a moment" in t ||
            "请稍候" in t ||
            "attention required" in t ||
            "checking your browser" in t
    }

    private fun closeEdgeViaCdp(client: HttpClient, port: Int) {
        val pagesJson = runCatching {
            val req = HttpRequest.newBuilder().uri(URI.create("http://127.0.0.1:$port/json")).GET().build()
            client.send(req, HttpResponse.BodyHandlers.ofString()).body()
        }.getOrNull() ?: return
        val pages = runCatching { gson.fromJson<List<Map<String, Any?>>>(pagesJson, listMapType) }.getOrNull() ?: return
        pages.firstNotNullOfOrNull { it["webSocketDebuggerUrl"] as? String }?.let { wsUrl ->
            val future = CompletableFuture<Void>()
            val wsClient = HttpClient.newHttpClient()
            wsClient.newWebSocketBuilder().buildAsync(
                URI.create(wsUrl),
                object : WebSocket.Listener {
                    override fun onOpen(webSocket: WebSocket) {
                        webSocket.sendText("""{"id":99,"method":"Browser.close"}""", true)
                        future.complete(null)
                    }
                },
            ).get(2, TimeUnit.SECONDS)
            future.get(2, TimeUnit.SECONDS)
        }
    }

    private fun extractCookiesAndUaViaCdp(
        wsUrl: String,
        targetUrl: String,
    ): DesktopCloudflareCenter.BypassResult? {
        val cookieFuture = CompletableFuture<String>()
        val uaFuture = CompletableFuture<String>()
        val buf1 = StringBuilder()
        val buf2 = StringBuilder()

        val wsClient = HttpClient.newHttpClient()
        val ws = wsClient.newWebSocketBuilder().buildAsync(
            URI.create(wsUrl),
            object : WebSocket.Listener {
                override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
                    val text = data.toString()
                    // 按命令 id 分流或先拼接判断
                    synchronized(this) {
                        val combined = if (!cookieFuture.isDone) {
                            buf1.append(text)
                            val s = buf1.toString()
                            if (last) {
                                val map = runCatching { gson.fromJson<Map<String, Any?>>(s, mapType) }.getOrNull()
                                val id = (map?.get("id") as? Number)?.toInt()
                                if (id == 1) {
                                    cookieFuture.complete(s)
                                    buf1.clear()
                                } else if (id == 2) {
                                    uaFuture.complete(s)
                                    buf1.clear()
                                }
                            }
                            s
                        } else {
                            buf2.append(text)
                            val s = buf2.toString()
                            if (last) {
                                uaFuture.complete(s)
                                buf2.clear()
                            }
                            s
                        }
                        combined.length
                    }
                    webSocket.request(1)
                    return null
                }

                override fun onError(webSocket: WebSocket, error: Throwable) {
                    cookieFuture.completeExceptionally(error)
                    uaFuture.completeExceptionally(error)
                }
            },
        ).get(5, TimeUnit.SECONDS)

        val origin = runCatching {
            val u = URI.create(targetUrl)
            "${u.scheme}://${u.host}"
        }.getOrDefault(targetUrl)

        ws.sendText("""{"id":1,"method":"Network.getCookies","params":{"urls":["$targetUrl","$origin/"]}}""", true)
        ws.sendText("""{"id":2,"method":"Runtime.evaluate","params":{"expression":"navigator.userAgent","returnByValue":true}}""", true)

        val cookieJson = cookieFuture.get(5, TimeUnit.SECONDS)
        val uaJson = runCatching { uaFuture.get(4, TimeUnit.SECONDS) }.getOrNull().orEmpty()
        runCatching { ws.sendClose(WebSocket.NORMAL_CLOSURE, "done") }

        val cookieMap = gson.fromJson<Map<String, Any?>>(cookieJson, mapType) ?: return null
        val resultObj = cookieMap["result"] as? Map<*, *> ?: return null
        val rawCookies = resultObj["cookies"] as? List<*> ?: return null
        val parsedCookies = rawCookies.mapNotNull { item ->
            val m = item as? Map<*, *> ?: return@mapNotNull null
            val name = m["name"] as? String ?: return@mapNotNull null
            val value = m["value"] as? String ?: return@mapNotNull null
            val domain = m["domain"] as? String ?: return@mapNotNull null
            DesktopCloudflareCenter.CapturedCookie(
                name = name,
                value = value,
                domain = domain,
                path = (m["path"] as? String).orEmpty().ifBlank { "/" },
                httpOnly = (m["httpOnly"] as? Boolean) ?: false,
                secure = (m["secure"] as? Boolean) ?: false,
            )
        }

        val uaMap = runCatching { gson.fromJson<Map<String, Any?>>(uaJson, mapType) }.getOrNull()
        val uaVal = (((uaMap?.get("result") as? Map<*, *>)?.get("result") as? Map<*, *>)?.get("value") as? String).orEmpty()
        return DesktopCloudflareCenter.BypassResult(
            userAgent = uaVal,
            cookies = parsedCookies,
        )
    }
}
