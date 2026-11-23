package com.venera.desktop.gallery.data

import com.google.gson.Gson
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Rectangle
import java.awt.Window
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit

/**
 * ascii2d 反搜桥 ——「真浏览器过盾 + 软件内展示」的桌面件。
 *
 * ## 为什么必须真浏览器
 *
 * ascii2d 的 Cloudflare WAF **按路径设卡**（三轮探针终审）：主页宽进，
 * `/search/` 路径无论 GET/POST 都要求先跑过 JS 挑战的 `cf_clearance`，且按 TLS 指纹拦
 * 非浏览器客户端 —— impersonator 把 JA4/H2 全伪装成真 Chrome 也拦（纯指纹压不过，
 * 它要的是「挑战跑过」这个行为证据）。JVM 里跑不了挑战 JS，唯一活路 = 真 Edge。
 *
 * ## 形状：嵌入面板，不是外置浏览器
 *
 * Edge 以 app 模式拉起（无地址栏），经 Win32 `SetParent` 嵌进宿主窗口预留的验证面板里 ——
 * 用户在**软件内**点那一两个验证框；挑战过完（轮询 `document.title` 离开
 * "Just a moment"）自动收起面板、经 CDP 读结果页 DOM，命中交给软件自己的列表渲染。
 * 对用户而言"外置浏览器"不可感知。
 *
 * ## 会话与 clearance 复用
 *
 * user-data-dir 固定在数据根 `ascii2d-edge/` —— 挑战过一次，cookie 在档，有效期内
 * 后续搜索**不再弹验证**，面板一闪而过。进程长驻复用（同端口回环 CDP）。
 *
 * ## 搜索入口
 *
 * URL 反搜走官方 Firefox 扩展同款 GET 入口（`/search/url/{enc}?type=color`），
 * 免 CSRF token 免表单。本机文件反搜后续批次接 `/search/file`。
 */
class DesktopAscii2dBridge private constructor() {

    /** 一条命中。站方不给相似度分数 —— [rank] 就是结果页的顺序名次（越前越像）。 */
    data class Ascii2dHit(
        val rank: Int,
        val thumbnailUrl: String,
        val sizeText: String?,
        val siteLabel: String?,
        val pageUrl: String?,
        val artistName: String?,
        val artistUrl: String?,
        val dateText: String?,
        val hash: String?,
    )

    /** 桥的相位 —— 反搜界面照它渲染：等待面板出现 / 请点验证 / 正在读 / 完成。 */
    sealed interface Phase {
        data object Idle : Phase
        data class Navigating(val target: String) : Phase
        /** 等结果轮询的实时读数 —— 卡在哪一环用户屏上直接可见。 */
        data class Polling(val note: String) : Phase
        /** 挑战挂起中 —— 界面要把嵌入面板亮出来让用户点框。 */
        data class WaitingChallenge(val hint: String) : Phase
        data object Reading : Phase
        data class Done(val hits: List<Ascii2dHit>) : Phase
        data class Failed(val reason: String) : Phase
    }

    val phase = kotlinx.coroutines.flow.MutableStateFlow<Phase>(Phase.Idle)

    /**
     * 跨进屏保留的原图与 URL 框输入 —— 令「从其他页面返回不该全部重置」。
     * 桥是单例，结果在 [phase] 里、输入在这一对里，都与屏的 composition 同寿不同源，
     * 离屏再回来不会丢。只有用户主动发车新一轮才覆写。
     */
    var pickedBytes: ByteArray? = null
    var inputText: String = ""

    private val http = HttpClient.newHttpClient()
    private var port: Int = 0
    private var edgeProc: Process? = null
    private var cmdSeq = 0

    // ── 进程与会话 ────────────────────────────────────────────────

    private fun profileDir(): File {
        val root = File(System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"), "venera")
        return File(root, "ascii2d-edge")
    }

    private fun edgeExe(): File = listOf(
        File(System.getenv("ProgramFiles(x86)") ?: "", "Microsoft/Edge/Application/msedge.exe"),
        File(System.getenv("ProgramFiles") ?: "", "Microsoft/Edge/Application/msedge.exe"),
    ).firstOrNull { it.isFile } ?: error("系统里没有 Edge")

    /** CDP 端口就绪判定：活着就复用（clearance/会话不折腾）。 */
    private suspend fun cdpAlive(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            http.send(
                java.net.http.HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port/json/version")).GET().build(),
                java.net.http.HttpResponse.BodyHandlers.ofString(),
            ).statusCode() == 200
        }.getOrDefault(false)
    }

    suspend fun ensureLaunched() {
        if (port != 0 && cdpAlive()) return
        port = 19422
        if (!cdpAlive()) {
            launchEdge(port)
        }
    }

    /**
     * 杀掉所有挂着咱们 profile 的残留 Edge 进程。
     *
     * ⚠️ 不能 taskkill /IM 一刀切 —— 主人的日常 Edge 也在跑。只按命令行匹配
     * `ascii2d-edge` 这一个标记（只有我们启动的实例会带它）。残留实例锁着 profile
     * 时，新实例的启动参数（含调试端口）会被转发给它然后自己退出 —— 那正是
     * 真窗反馈"浏览器直接弹了独立窗 + ConnectException"的根因。
     */
    private fun killStaleEdge() {
        runCatching {
            ProcessBuilder(
                "powershell", "-NoProfile", "-Command",
                "Get-CimInstance Win32_Process -Filter \"name='msedge.exe'\" | " +
                    "Where-Object { \$_.CommandLine -match 'ascii2d-edge' } | " +
                    "ForEach-Object { Stop-Process -Id \$_.ProcessId -Force }",
            ).start().waitFor(15, TimeUnit.SECONDS)
        }
    }

    private suspend fun launchEdge(port: Int) {
        edgeProc?.destroyForcibly()
        killStaleEdge()
        val profile = profileDir().apply { mkdirs() }
        val args = mutableListOf(
            edgeExe().absolutePath,
            "--app=about:blank",
            "--remote-debugging-port=$port",
            "--user-data-dir=${profile.absolutePath}",
            "--no-first-run", "--no-default-browser-check",
            // 后台瘦身：不加载扩展/不同步/不跑后台应用 —— 挂着过盾会话也尽量安静
            "--disable-extensions", "--disable-background-networking",
            "--disable-sync", "--disable-default-apps",
            "--window-size=760,620",
            // 初始位置屏外 —— 免挑战时窗口全程不可见，结果直接回软件内；
            // 只有撞了挑战才由 attachPanel 把窗拉进验证面板。
            "--window-position=-32000,-32000",
        )
        edgeProc = ProcessBuilder(args).start()
        // msedge.exe 启动主进程后会退出，须立即快照进程树后代 PID 用于后续窗口匹配。
        descendantPids = runCatching {
            setOf(edgeProc!!.pid()) + edgeProc!!.descendants().map { it.pid() }.toList().toSet()
        }.getOrDefault(emptySet<Long>())
        // 等调试端口就绪（ExAuthProbe 同款轮询）
        for (i in 0 until 60) {
            if (cdpAlive()) {
                positionOffscreen()
                return
            }
            delay(250)
        }
        error("Edge 调试端口没就绪")
    }

    /**
     * 把顶级窗钉到屏幕外兜底 —— `--window-position` 在转发/会话恢复时可能被忽略，
     * 免挑战路径绝不能让 Edge 窗在主人桌面上弹出来。窗口在屏外时 SetParent/MoveWindow
     * 照样找得到它，挑战一撞就拉回面板。
     */
    private fun positionOffscreen() {
        val hwnd = findEdgeWindow() ?: run {
            logLine("屏外定位：没找到 Edge 顶级窗（进程树 pid=${descendantPids.size} 枚）")
            return
        }
        val u = com.sun.jna.platform.win32.User32.INSTANCE
        u.MoveWindow(hwnd, -32000, -32000, 760, 620, true)
        // 真「隐形」：摘 APPWINDOW、加 TOOLWINDOW —— 任务栏/Alt-Tab/桌面三处都不露脸。
        // 进程仍挂着（过盾会话的载体，clearance 在罐），应用退出时 shutdown 清理。
        // WS_EX_TOOLWINDOW=0x80（任务栏/Alt-Tab 隐藏）、WS_EX_APPWINDOW=0x40000（强制任务栏按钮）
        val ex = u.GetWindowLong(hwnd, WinUser.GWL_EXSTYLE)
        val newEx = ((ex.toInt() or 0x80) and 0x40000.inv()).toInt()
        u.SetWindowLong(hwnd, WinUser.GWL_EXSTYLE, newEx)
        logLine("屏外定位+TOOLWINDOW 完成")
    }

    /** 应用退出时收尸（回写字典损坏无所谓，重要的是别留孤儿进程）。 */
    fun shutdown() {
        idleJob?.cancel()
        idleScope.cancel()
        runCatching { edgeProc?.destroyForcibly() }
        edgeProc = null
        port = 0
        edgeHwnd = null
        killStaleEdge()
    }

    // ── CDP 基语 ─────────────────────────────────────────────────

    private suspend fun cdpEval(wsUrl: String, expression: String, awaitPromise: Boolean = false): String? =
        withContext(Dispatchers.IO) {
            val f = CompletableFuture<String>()
            val ws = try {
                http.newWebSocketBuilder().buildAsync(URI.create(wsUrl), object : WebSocket.Listener {
                    private val sb = StringBuilder()
                    override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
                        sb.append(data)
                        if (last) f.complete(sb.toString())
                        return super.onText(webSocket, data, last)
                    }
                }).get(8, TimeUnit.SECONDS)
            } catch (e: Exception) {
                logLine("eval 建连失败: ${e.javaClass.simpleName}: ${e.message?.take(120)} ws=${wsUrl.take(50)}")
                return@withContext null
            }
            ws.sendText(Gson().toJson(mapOf("id" to ++cmdSeq, "method" to "Runtime.evaluate", "params" to mapOf(
                "expression" to expression, "returnByValue" to true, "awaitPromise" to awaitPromise,
            ))), true)
            val raw = try {
                f.get(30, TimeUnit.SECONDS)
            } catch (e: Exception) {
                logLine("eval 响应超时/失败: ${e.javaClass.simpleName} expr=${expression.take(60)}")
                runCatching { ws.abort() }
                return@withContext null
            }
            runCatching { ws.abort() }
            val root = Gson().fromJson(raw, Map::class.java) as? Map<*, *>
            if (root == null) {
                logLine("eval 解析失败 raw=${raw.take(160)}")
                return@withContext null
            }
            val result = root["result"] as? Map<*, *>
            if (result == null) {
                logLine("eval 无 result raw=${raw.take(160)}")
                return@withContext null
            }
            // Runtime.evaluate 的形状：外层 result 里套着内层 result（值在那里），
            // subtype/className 在**内层** —— 查外层永远查不到，JS 异常会被吞成 null
            val inner = (result["result"] as? Map<*, *>) ?: result
            if (inner["subtype"] == "error" || inner["className"] != null) {
                logLine("eval JS 异常 raw=${raw.take(220)}")
                return@withContext null
            }
            val value = inner["value"] ?: result["value"]
            if (value == null) logLine("eval 无 value raw=${raw.take(220)}")
            value as? String
        }

    /**
     * 桥的诊断日志：数据根 logs 下这一份。打包版没有控制台 —— CDP/嵌窗这些
     * Windows 装配环节哪一跳死了，只有落盘才回得了头。
     */
    private fun logLine(line: String) {
        runCatching {
            val dir = File(System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"), "venera/logs")
            dir.mkdirs()
            File(dir, "ascii2d-bridge.log").appendText(
                "${java.time.LocalTime.now().withNano(0)} $line\n",
            )
        }
    }

    private suspend fun cdpPageNavigate(wsUrl: String, url: String): Boolean = withContext(Dispatchers.IO) {
        val f = CompletableFuture<String>()
        val ws = http.newWebSocketBuilder().buildAsync(URI.create(wsUrl), object : WebSocket.Listener {
            private val sb = StringBuilder()
            override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
                sb.append(data)
                if (last) f.complete(sb.toString())
                return super.onText(webSocket, data, last)
            }
        }).get(8, TimeUnit.SECONDS)
        ws.sendText(Gson().toJson(mapOf("id" to ++cmdSeq, "method" to "Page.navigate", "params" to mapOf("url" to url))), true)
        val raw = try { f.get(30, TimeUnit.SECONDS) } catch (e: Exception) { runCatching { ws.abort() }; return@withContext false }
        runCatching { ws.abort() }
        raw.contains("\"frameId\"")
    }

    private suspend fun cdpPageEnable(wsUrl: String) {
        // Page.enable 并不必要：Runtime.evaluate 与 Page.navigate 各自独立成话。
        // 留一个空档位是为了以后要听 Page 事件时不用改调用形状。
    }

    private suspend fun currentPageWsUrl(): String? = withContext(Dispatchers.IO) {
        runCatching {
            val body = http.send(
                java.net.http.HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port/json")).GET().build(),
                java.net.http.HttpResponse.BodyHandlers.ofString(),
            ).body()
            val pages = runCatching {
                Gson().fromJson(body, List::class.java) as? List<Map<String, Any?>>
            }.getOrNull().orEmpty()
            pages.firstOrNull { it["type"] == "page" }?.get("webSocketDebuggerUrl") as? String
        }.getOrNull()
    }

    // ── 嵌入面板（Win32）─────────────────────────────────────────

    private var edgeHwnd: WinDef.HWND? = null

    private var hostWindow: Window? = null

    /** 本轮启动的 Edge 进程树（launcher + browser 主进程 + 子进程）的 pid 快照。 */
    private var descendantPids: Set<Long> = emptySet()

    /** 最近一次 UI 交进来的面板矩形 —— 嵌入失败时轮询里靠它重试。 */
    private var lastPanelRect: Rectangle? = null

    private fun rememberHost(w: Window) {
        hostWindow = w
    }

    /**
     * UI 把面板矩形交进来的那一刻：还没嵌就嵌（SetParent 一次），嵌过就重对齐。
     * Edge 顶级窗还没建出来（导航刚发车）时 findEdgeWindow 扑空 ⇒ 返回 false，
     * 等待循环里会拿 [lastPanelRect] 重试 —— 不用调用方管时序。
     */
    fun attachPanel(rect: Rectangle): Boolean {
        hostWindow?.let { rememberHost(it) }
        lastPanelRect = rect
        return if (edgeHwnd == null) {
            val host = hostWindow ?: return false
            embedInto(host, rect)
        } else {
            movePanel(rect)
        }
    }

    /**
     * 把 Edge 的顶级窗口收进宿主窗口：摘标题栏 → SetParent → 定位。
     * [panelRect] 是面板区域在**屏幕坐标系**里的物理像素矩形（UI 侧负责换算）。
     * 手艺与 [com.venera.desktop.DesktopDwmBackdrop] 同源（JNA user32，32 位读写）。
     */
    fun embedInto(hostWindow: Window, panelRect: Rectangle): Boolean {
        val edgeHwnd = findEdgeWindow() ?: return false
        val host = WinDef.HWND(Native.getWindowPointer(hostWindow))
        val u = com.sun.jna.platform.win32.User32.INSTANCE

        // 摘标题栏/可缩边（黑带战役同款位运算，32 位 Get/Set）
        val style = u.GetWindowLong(edgeHwnd, WinUser.GWL_STYLE)
        u.SetWindowLong(
            edgeHwnd, WinUser.GWL_STYLE,
            style and (WinUser.WS_CAPTION or WinUser.WS_THICKFRAME).inv(),
        )
        u.SetParent(edgeHwnd, host)
        this.edgeHwnd = edgeHwnd
        return movePanel(panelRect)
    }

    /** 面板区域变化（软件窗口移动/缩放）时重对齐。 */
    fun movePanel(panelRect: Rectangle): Boolean {
        val hwnd = edgeHwnd ?: return false
        val u = com.sun.jna.platform.win32.User32.INSTANCE
        u.ShowWindow(hwnd, WinUser.SW_SHOWNOACTIVATE)
        return u.MoveWindow(
            hwnd,
            panelRect.x, panelRect.y, panelRect.width, panelRect.height, true,
        )
    }

    /** 面板收起（挑战过完/搜索完成）—— 窗口隐藏，进程留着复用。 */
    fun hidePanel() {
        val hwnd = edgeHwnd ?: return
        com.sun.jna.platform.win32.User32.INSTANCE.ShowWindow(hwnd, WinUser.SW_HIDE)
    }

    private fun findEdgeWindow(): WinDef.HWND? {
        val u = com.sun.jna.platform.win32.User32.INSTANCE
        if (descendantPids.isEmpty()) return null
        var found: WinDef.HWND? = null
        u.EnumWindows(object : WinUser.WNDENUMPROC {
            override fun callback(hWnd: WinDef.HWND, data: Pointer?): Boolean {
                val buf = CharArray(64)
                u.GetClassName(hWnd, buf, 64)
                val cls = String(buf).trimEnd(Char(0))
                if (cls == "Chrome_WidgetWin_1" && u.IsWindowVisible(hWnd)) {
                    val pidRef = com.sun.jna.ptr.IntByReference()
                    u.GetWindowThreadProcessId(hWnd, pidRef)
                    // launcher 已退出 —— 按**进程树快照**匹配，不是按某一个 pid
                    if (pidRef.value.toLong() in descendantPids) {
                        found = hWnd
                        return false
                    }
                }
                return true
            }
        }, null)
        return found
    }

    // ── 反搜主流程 ────────────────────────────────────────────────

    /**
     * URL 反搜。宿主窗口与面板取矩形回调由 UI 侧给 —— 面板坐标要吃
     * 软件窗口在屏幕上的位置 + DPI 缩放，那一层只有 UI 知道。
     */
    suspend fun searchByUrl(
        imageUrl: String,
        hostWindow: Window?,
        panelRectProvider: (() -> Rectangle?)? = null,
    ): List<Ascii2dHit> {
        idleJob?.cancel()
        ensureLaunched()
        val target = "https://ascii2d.net/search/url/" +
            URLEncoder.encode(imageUrl, "UTF-8") + "?type=color"
        phase.value = Phase.Navigating(target)
        val ws = currentPageWsUrl() ?: run {
            phase.value = Phase.Failed("Edge 页面没就绪，再试一次")
            return emptyList()
        }
        cdpPageEnable(ws)
        cdpPageNavigate(ws, target)

        // 宿主窗口先记着；嵌入只在撞挑战时发生（免挑战时 Edge 屏外隐形，
        // 结果直接回软件内 —— 主人要的就是这个形状）。
        hostWindow?.let { rememberHost(it) }

        return finishWithPanel(ws)
    }

    /**
     * 本机文件反搜：官方 Firefox 扩展同款 —— 页面内 `fetch` FormData POST `/search/file`，
     * 拿跳转后的结果页 URL 再走与 URL 反搜共用的「等挑战 + 读页」尾段。
     *
     * 为什么文件路不做成导航：`/search/file` 的表单控件在站方主页里被 JS 接管，
     * 直接给隐藏 input 塞字节不如手搓 FormData 可控（extension 同款形状，实测形状见探针）。
     * 挑战必须先过 —— 所以先导航到主页当**过盾跳板**，`cf_clearance` 落罐后再发 POST。
     *
     * 文件字节以 base64 注进页面 —— 普通截图/封面的量级（几 MB）CDP evaluate 吃得下；
     * 超大文件（>20MB）不承诺，界面侧不该把那种东西放进来。
     */
    suspend fun searchByFile(
        bytes: ByteArray,
        fileName: String,
        hostWindow: Window?,
        panelRectProvider: (() -> Rectangle?)? = null,
    ): List<Ascii2dHit> {
        idleJob?.cancel()
        ensureLaunched()
        phase.value = Phase.Navigating("https://ascii2d.net/")
        val ws = currentPageWsUrl() ?: run {
            phase.value = Phase.Failed("Edge 页面没就绪，再试一次")
            return emptyList()
        }
        cdpPageEnable(ws)
        cdpPageNavigate(ws, "https://ascii2d.net/")

        hostWindow?.let { rememberHost(it) }

        // 主页当过盾跳板：等到非挑战页且 complete 才发上传（clearance 在罐里才有得谈）
        if (!awaitPageReady(ws)) {
            phase.value = Phase.Failed("等验证超时（180 秒）—— 挑战没点完")
            hidePanel()
            return emptyList()
        }

        phase.value = Phase.Reading
        val mime = mimeOf(fileName)
        val b64 = java.util.Base64.getEncoder().encodeToString(bytes)
        // 上传走主页 file 表单提交而非 fetch POST：WAF 会拦截 Sec-Fetch-Mode=cors 请求，表单导航可携带 CSRF token 放行。
        val uploadJs = """
        (() => {
            try {
                const bytes = Uint8Array.from(atob('$b64'), c => c.charCodeAt(0));
                const dt = new DataTransfer();
                dt.items.add(new File([bytes], ${Gson().toJson(fileName)}, {type: '$mime'}));
                const form = document.querySelector('form[action="/search/file"]');
                if (!form) return JSON.stringify({error: '主页没有文件表单'});
                const input = form.querySelector('input[type=file]');
                if (!input) return JSON.stringify({error: '文件表单没有 input'});
                input.files = dt.files;
                form.submit();
                return JSON.stringify({ok: true});
            } catch (e) { return JSON.stringify({error: String(e)}); }
        })()
        """.trimIndent()
        val resp = cdpEval(ws, uploadJs)
        if (resp == null || resp.contains("error")) {
            phase.value = Phase.Failed("上传没成（${resp?.take(120) ?: "无响应"}）")
            hidePanel()
            return emptyList()
        }
        // form.submit() 触发整页导航 —— 与 URL 反搜共用「等挑战 + 读页」尾段
        return finishWithPanel(ws)
    }

    /** 共用尾段：等挑战过完 + 到结果页读 DOM。null = 超时。 */
    private suspend fun finishWithPanel(ws: String): List<Ascii2dHit> {
        // 等结果：挑战页 title 是 "Just a moment"；到达 /search/color/ 且 complete 才算到站
        var hits: List<Ascii2dHit>? = null
        var challengeAnnounced = false
        var embedTries = 0
        var deadPolls = 0
        var readFails = 0
        val deadline = System.currentTimeMillis() + 180_000L
        while (System.currentTimeMillis() < deadline) {
            delay(600)
            // 撞过挑战的面板要留在页面上（用户可能还得点）；没嵌上且最近有矩形就重试
            if (edgeHwnd == null && lastPanelRect != null && embedTries++ % 2 == 0) {
                attachPanel(lastPanelRect!!)
            }
            val state = cdpEval(
                ws,
                "JSON.stringify({t: document.title, u: location.href, rs: document.readyState})",
            )
            if (state == null) {
                // CDP 读不上：连续多次就别再空转，把病念出来
                if (++deadPolls >= 6) {
                    phase.value = Phase.Failed("与浏览器的调试连接读不上 —— 再试一次；一直如此就重启应用")
                    hidePanel()
                    return emptyList()
                }
                continue
            }
            deadPolls = 0
            if (state.contains("moment")) {
                if (!challengeAnnounced) {
                    challengeAnnounced = true
                    phase.value = Phase.WaitingChallenge("请在上方面板完成人机验证")
                }
                continue
            }
            // 实时读数上屏：卡在哪一环一眼可见
            val where = if (state.contains("/search/color/")) "结果页" else "搜索跳转页"
            val rs = if (state.contains("\"rs\":\"complete\"")) "已就绪" else "加载中"
            phase.value = Phase.Polling("$where · $rs")
            if (state.contains("/search/color/") && state.contains("\"rs\":\"complete\"")) {
                phase.value = Phase.Reading
                // READ_PAGE_JS 是 async IIFE（等条目 + 并发抓缩略图转 dataURL）—— 必须 await。
                // 读失败不再无限 continue：连着几次读不上就把病念出来，别让屏上永远「读取结果」。
                val json = cdpEval(ws, READ_PAGE_JS, awaitPromise = true)
                if (json == null) {
                    if (++readFails >= 3) {
                        phase.value = Phase.Failed("读取结果失败 —— 再试一次")
                        hidePanel()
                        return emptyList()
                    }
                    continue
                }
                hits = parseHits(json)
                break
            }
        }
        hidePanel()
        val result = hits
        if (result == null) {
            phase.value = Phase.Failed("等结果超时（180 秒）—— 挑战没点完或网络太慢")
            scheduleIdleKill()
            return emptyList()
        }
        phase.value = Phase.Done(result)
        scheduleIdleKill()
        return result
    }

    /** 等当前页变成"非挑战且 complete"（过盾跳板用）。 */
    private suspend fun awaitPageReady(ws: String): Boolean {
        val deadline = System.currentTimeMillis() + 180_000L
        var challengeAnnounced = false
        var embedTries = 0
        while (System.currentTimeMillis() < deadline) {
            delay(1500)
            if (edgeHwnd == null && lastPanelRect != null && embedTries++ % 2 == 0) {
                attachPanel(lastPanelRect!!)
            }
            val state = cdpEval(
                ws,
                "JSON.stringify({t: document.title, rs: document.readyState})",
            ) ?: continue
            if (state.contains("moment")) {
                if (!challengeAnnounced) {
                    challengeAnnounced = true
                    phase.value = Phase.WaitingChallenge("请在上方面板完成人机验证")
                }
                continue
            }
            if (state.contains("\"rs\":\"complete\"")) return true
        }
        return false
    }

    private fun mimeOf(fileName: String): String = when (fileName.lowercase().substringAfterLast('.')) {
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        else -> "image/jpeg"
    }

    /**
     * 空闲收兵：一轮搜索完 5 分钟没新动作就把 Edge 进程整棵清掉 —— 平时真正做到
     * 零进程（主人明令不挂后台）。clearance 落在 profile 磁盘上，不随进程死，
     * 下次冷启动（约 3 秒）照样免挑战。
     */
    private fun scheduleIdleKill() {
        idleJob?.cancel()
        idleJob = idleScope.launch {
            delay(5 * 60_000L)
            runCatching { edgeProc?.destroyForcibly() }
            edgeProc = null
            port = 0
            edgeHwnd = null
            logLine("空闲 5 分钟，Edge 进程已收兵")
        }
    }

    private var idleJob: kotlinx.coroutines.Job? = null

    /** 空闲收尸计时器的作用域：随 [shutdown] 取消，不寄生在 GlobalScope。 */
    private val idleScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun parseHits(json: String): List<Ascii2dHit> {
        val rows = runCatching {
            Gson().fromJson(json, List::class.java) as? List<Map<String, Any?>>
        }.getOrNull().orEmpty()
        return rows.mapIndexedNotNull { idx, row ->
            val str = { k: String -> (row[k] as? String)?.takeIf { it.isNotBlank() } }
            Ascii2dHit(
                rank = idx + 1,
                thumbnailUrl = str("thumbData") ?: str("thumb") ?: return@mapIndexedNotNull null,
                sizeText = str("size"),
                siteLabel = str("siteName"),
                pageUrl = str("page"),
                artistName = str("artistName"),
                artistUrl = str("artistUrl"),
                dateText = str("date"),
                hash = str("hash"),
            )
        }
    }

    companion object {
        /**
         * 结果页解析：选择器是探针从真结果页逆向出来的
         * （`div.row.item-box` + 缩略图 `/thumbnail/` + `.detail-box h6` 三件套）。
         * 站方改版时这里先碎 —— 报错形状是"条数为 0"，UI 要能念出"解析不到条目"。
         */
        /**
         * 缩略图为什么不交 URL 给 Coil：ascii2d.net 的图也被同一套 CF 按 TLS 指纹拦
         * （真窗灰块复证）—— 而过盾的浏览器自己抓一张几 KB 的小图毫无压力。
         * 所以这里在页面内 fetch 每张缩略图转 dataURL 随 JSON 一并回来，
         * Coil 直接吃 data URI，不碰网络。
         */
        private val READ_PAGE_JS = """
        (async () => {
            // 结果由页面 JS 异步注入：complete 后短轮询等条目出现，替代外面死等 4 秒。
            let rows = [];
            for (let i = 0; i < 24; i++) {
                rows = Array.from(document.querySelectorAll('div.row.item-box'));
                if (rows.length) break;
                await new Promise(r => setTimeout(r, 150));
            }
            // 单张缩略图抓取：AbortController 超时，任何一张卡住都不拖垮整批。
            const grab = async (thumb) => {
                if (!thumb) return '';
                const ctrl = new AbortController();
                const timer = setTimeout(() => ctrl.abort(), 6000);
                try {
                    const resp = await fetch(thumb.getAttribute('src'), {
                        credentials: 'include', signal: ctrl.signal,
                    });
                    const blob = await resp.blob();
                    return await new Promise(res => {
                        const fr = new FileReader();
                        fr.onload = () => res(fr.result);
                        fr.onerror = () => res('');
                        fr.readAsDataURL(blob);
                    });
                } catch (e) { return ''; }
                finally { clearTimeout(timer); }
            };
            // 并发，不串行：几十张串行 await 会顶穿 CDP 的 30 秒响应窗口，
            // 被吞成 null 后外面无限重试，屏上就永远「正在读取结果…」。
            const out = await Promise.all(rows.map(async (r) => {
                const info = r.querySelector('.info-box');
                const size = info ? (info.querySelector('small.text-muted') || {}).textContent : '';
                const hash = info ? (info.querySelector('.hash') || {}).textContent : '';
                const h6 = (r.querySelector('.detail-box') || {}).querySelector ? r.querySelector('.detail-box h6') : null;
                const anchors = h6 ? Array.from(h6.querySelectorAll('a[target=_blank]')) : [];
                const page = anchors.length ? anchors[0].href : '';
                const date = anchors.length ? anchors[0].textContent.trim() : '';
                const artist = anchors.length > 1 ? anchors[1] : null;
                const siteName = h6 ? ((h6.querySelector('small.text-muted') || {}).textContent || '') : '';
                const iconAlt = h6 ? ((h6.querySelector('img') || {}).alt || '') : '';
                const thumb = r.querySelector('img[src*="/thumbnail/"]');
                const thumbData = await grab(thumb);
                return {
                    size: size || '', hash: hash || '',
                    siteName: siteName,
                    iconAlt: iconAlt,
                    page: page, date: date,
                    artistUrl: artist ? artist.href : '', artistName: artist ? artist.textContent.trim() : '',
                    thumb: thumb ? 'https://ascii2d.net' + thumb.getAttribute('src') : '',
                    thumbData: thumbData
                };
            }));
            return JSON.stringify(out);
        })()
        """.trimIndent()

        @Volatile
        private var INSTANCE: DesktopAscii2dBridge? = null

        fun getInstance(): DesktopAscii2dBridge =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: DesktopAscii2dBridge().also { INSTANCE = it }
            }
    }
}
