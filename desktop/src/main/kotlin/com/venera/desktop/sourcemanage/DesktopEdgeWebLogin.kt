package com.venera.desktop.sourcemanage

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.venera.engineprobe.DesktopCookieJar
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.WebSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * 桌面端网页登录执行器（调用系统 Edge 独立 App 弹窗 + CDP 自动化捕获凭据）。
 *
 * ## 原理与安全约束
 *
 * 1. 零第三方大依赖：Windows 10/11 系统内置 Microsoft Edge（Chromium 内核环境）；
 * 2. 独立应用外观：以 `--app=URL` 模式启动，无浏览器地址栏、标签栏，外观表现为独立窗口；
 * 3. 登录拦截与凭据抓取：
 *    - 通过 CDP HTTP 接口轮询当前 Page 的 URL 与 Title，调用源脚本 `loginWithWebview.checkStatus(url, title)`；
 *    - 判定命中后，通过 CDP WebSocket 执行 `Network.getCookies` 取回完整 Cookie（含 HttpOnly / Secure）；
 *    - 自动关闭 Edge 窗口并把 Cookie 灌入 [DesktopCookieJar] 持久化存盘。
 * 4. 架构红线合规：
 *    - 全程仅作为 HTTP/WebSocket **Client** 连向 127.0.0.1，**绝不起 ServerSocket 或本地 HTTP 服务**（严格遵守 [DesktopWebLoginVerdictTest]）；
 *    - 纯 JVM 实现，不包含 `android.webkit`。
 */
internal object DesktopEdgeWebLogin {

    private val gson = Gson()
    private val mapType = object : TypeToken<Map<String, Any?>>() {}.type
    private val listMapType = object : TypeToken<List<Map<String, Any?>>>() {}.type

    data class WebCookie(
        val name: String,
        val value: String,
        val domain: String,
        val path: String = "/",
        val expiresAt: Long = 0L,
        val httpOnly: Boolean = false,
        val secure: Boolean = false,
    )

    /** 查找本地 Edge 可执行文件路径。 */
    fun findEdgeExecutable(): File? {
        val candidates = listOfNotNull(
            System.getenv("ProgramFiles(x86)")?.let { File(it, "Microsoft/Edge/Application/msedge.exe") },
            System.getenv("ProgramFiles")?.let { File(it, "Microsoft/Edge/Application/msedge.exe") },
            System.getenv("LOCALAPPDATA")?.let { File(it, "Microsoft/Edge/Application/msedge.exe") },
            File("C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"),
            File("C:/Program Files/Microsoft/Edge/Application/msedge.exe"),
        )
        return candidates.firstOrNull { it.exists() && it.isFile }
    }

    /** 是否支持网页登录（本机有 Edge 即支持）。 */
    val isSupported: Boolean
        get() = findEdgeExecutable() != null

    /**
     * 启动 Edge 独立网页登录窗口并自动监听登录完成。
     *
     * @param url 登录入口网页地址
     * @param onCheckStatus 检查是否已成功登录（由源脚本判定）
     */
    suspend fun launchWebLogin(
        url: String,
        onCheckStatus: suspend (currentUrl: String, title: String) -> Boolean,
    ): Result<List<WebCookie>> = withContext(Dispatchers.IO) {
        val edgeExe = findEdgeExecutable()
            ?: return@withContext Result.failure(IllegalStateException("本机未找到 Microsoft Edge 浏览器，无法启动网页登录"))

        // 端口交给系统挑：19200–19799 既不是保留段，也常和别的程序（含上次残留）撞。
        val port = java.net.ServerSocket(0).use { it.localPort }
        val tempDir = File(System.getProperty("java.io.tmpdir"), "venera-edge-login-" + System.currentTimeMillis())
        tempDir.mkdirs()

        val process = try {
            ProcessBuilder(
                edgeExe.absolutePath,
                "--app=$url",
                "--remote-debugging-port=$port",
                "--user-data-dir=${tempDir.absolutePath}",
                "--no-first-run",
                "--no-default-browser-check",
            ).start()
        } catch (e: Exception) {
            tempDir.deleteRecursively()
            return@withContext Result.failure(e)
        }

        val client = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(3)).build()
        val startTime = System.currentTimeMillis()
        val timeoutMs = 5 * 60 * 1000L // 5 分钟超时

        var targetWsUrl: String? = null
        var lastUrl = ""
        var lastTitle = ""
        var successCookies: List<WebCookie>? = null

        try {
            while (process.isAlive && System.currentTimeMillis() - startTime < timeoutMs) {
                delay(500)

                // 1. 查询当前 Page 状态
                val pagesJson = runCatching {
                    val req = HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:$port/json"))
                        .GET()
                        .build()
                    client.send(req, HttpResponse.BodyHandlers.ofString()).body()
                }.getOrNull() ?: continue

                val targets = runCatching {
                    gson.fromJson<List<Map<String, Any?>>>(pagesJson, listMapType)
                }.getOrNull().orEmpty()

                val page = targets.firstOrNull { it["type"] == "page" } ?: continue
                val currentUrl = page["url"]?.toString().orEmpty()
                val currentTitle = page["title"]?.toString().orEmpty()
                val wsUrl = page["webSocketDebuggerUrl"]?.toString()

                if (wsUrl != null) {
                    targetWsUrl = wsUrl
                }

                if (currentUrl.isNotBlank() && (currentUrl != lastUrl || currentTitle != lastTitle)) {
                    lastUrl = currentUrl
                    lastTitle = currentTitle

                    // 2. 检查是否登录成功
                    val hit = runCatching { onCheckStatus(currentUrl, currentTitle) }.getOrDefault(false)
                    if (hit && targetWsUrl != null) {
                        // 3. 提取全部 Cookies
                        val cookies = extractCookiesViaCdp(client, targetWsUrl)
                        successCookies = cookies
                        break
                    }
                }
            }

            // 4. 关闭 Edge 窗口
            if (targetWsUrl != null) {
                runCatching {
                    closeEdgeViaCdp(client, targetWsUrl)
                }
            }
            if (process.isAlive) {
                process.destroy()
            }
        } finally {
            runCatching { tempDir.deleteRecursively() }
        }

        if (successCookies != null) {
            Result.success(successCookies)
        } else if (!process.isAlive) {
            Result.failure(Exception("登录窗口已关闭"))
        } else {
            Result.failure(Exception("网页登录等待超时"))
        }
    }

    /** 通过 CDP WebSocket 获取当前浏览器中所有 Cookie。 */
    private fun extractCookiesViaCdp(client: HttpClient, wsUrl: String): List<WebCookie> {
        val future = CompletableFuture<String>()
        val ws = client.newWebSocketBuilder().buildAsync(URI.create(wsUrl), object : WebSocket.Listener {
            private val sb = StringBuilder()
            override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
                sb.append(data)
                if (last) {
                    future.complete(sb.toString())
                }
                return super.onText(webSocket, data, last)
            }
        }).get(5, TimeUnit.SECONDS)

        ws.sendText("{\"id\": 1, \"method\": \"Network.getCookies\"}", true)
        val reply = future.get(5, TimeUnit.SECONDS)

        val env = runCatching { gson.fromJson<Map<String, Any?>>(reply, mapType) }.getOrNull()
        val result = env?.get("result") as? Map<*, *>
        val cookieList = result?.get("cookies") as? List<*> ?: return emptyList()

        return cookieList.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            val name = map["name"]?.toString() ?: return@mapNotNull null
            val value = map["value"]?.toString() ?: return@mapNotNull null
            val domain = map["domain"]?.toString() ?: ""
            val path = map["path"]?.toString() ?: "/"
            val expires = (map["expires"] as? Number)?.toLong() ?: 0L
            val httpOnly = map["httpOnly"] == true
            val secure = map["secure"] == true
            WebCookie(name, value, domain, path, expires, httpOnly, secure)
        }
    }

    /** 发送命令让 Edge 自动关闭。 */
    private fun closeEdgeViaCdp(client: HttpClient, wsUrl: String) {
        val ws = client.newWebSocketBuilder().buildAsync(URI.create(wsUrl), object : WebSocket.Listener {}).get(3, TimeUnit.SECONDS)
        ws.sendText("{\"id\": 999, \"method\": \"Browser.close\"}", true)
    }

    /** 将抓取的 WebCookie 写入 [DesktopCookieJar]。 */
    fun applyCookiesToJar(jar: DesktopCookieJar, targetUrlStr: String, cookies: List<WebCookie>): Int {
        if (cookies.isEmpty()) return 0
        val targetHttpUrl = (if (targetUrlStr.startsWith("http")) targetUrlStr else "https://$targetUrlStr").toHttpUrlOrNull()
            ?: return 0
        val farFuture = System.currentTimeMillis() + 365L * 24 * 3600 * 1000

        var count = 0
        val byDomain = cookies.groupBy {
            var d = it.domain.trim().lowercase()
            while (d.startsWith(".")) d = d.substring(1)
            d.ifBlank { targetHttpUrl.host }
        }

        for ((host, list) in byDomain) {
            val url = "https://$host".toHttpUrlOrNull() ?: continue
            val okCookies = list.mapNotNull { c ->
                try {
                    Cookie.Builder()
                        .name(c.name)
                        .value(c.value)
                        .domain(host)
                        .path(c.path.ifBlank { "/" })
                        .expiresAt(if (c.expiresAt > 0) c.expiresAt * 1000 else farFuture)
                        .apply {
                            if (c.httpOnly) httpOnly()
                            if (c.secure) secure()
                        }
                        .build()
                } catch (e: Exception) {
                    null
                }
            }
            if (okCookies.isNotEmpty()) {
                jar.saveFromResponse(url, okCookies)
                count += okCookies.size
            }
        }
        return count
    }
}
