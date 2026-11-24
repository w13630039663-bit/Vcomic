package com.venera.desktop.gallery.data

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.LocalAwtWindow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.venera.desktop.ui.theme.VeneraType
import java.io.File
import java.net.URI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import li.songe.compose.webview2.WebView
import li.songe.compose.webview2.WebView2Runtime
import li.songe.compose.webview2.WebViewLifecycle
import li.songe.compose.webview2.WebViewSettings
import li.songe.compose.webview2.WebViewState
import li.songe.compose.webview2.rememberWebViewState

/**
 * konachan.com（R18 库）的过盾桥 —— WebView2 Evergreen 页内 fetch 取 JSON。
 *
 * ## 为什么必须真浏览器内核
 *
 * 站方对 HTML/API 路径整域挂 CF managed challenge（实测 `/post.json` 等纯代码恒 403，
 * 优选 IP 换不了）。WebView2 = 真 Edge 内核，指纹自动放行（spike 实测
 * Edge 同引擎免点勾），页内 fetch 带着站方发的 `cf_clearance` 把端点原样取回。
 * 图床**不在盾内**（实测 preview/原图无凭据直连 200），Coil 直连 —— 桥只管 JSON。
 *
 * ## 宿主形状：帧捕获，不是 HWND 嵌入
 *
 * 浏览器由壳根挂载的 [DesktopKonachanWebViewHost] 持有（[mountRequested] 驱动挂/卸），
 * 帧画进自家 Compose 场景 —— 无原生可见 surface、不进任务栏；撞人工挑战时把宿主盒
 * 放大亮出，验证框直接在自家面板里点。空闲 60 秒卸载浏览器（clearance 落在
 * userDataDirectory 里不随卸载死），进程不常驻 —— Edge+CDP 版「后台常驻几个浏览器进程」
 * 的那笔账在这版不再存在。
 *
 * @see DesktopKonachanWebViewHost 壳根那一格挂载点。
 */
internal object DesktopKonachanBridge {

    const val ORIGIN = "https://konachan.com"
    private const val IDLE_UNMOUNT_MS = 300_000L
    private const val CHALLENGE_WAIT_MS = 120_000L

    /** 首次挂载导航的目标地址（按首个请求域名动态设定为 /robots.txt 轻量页，避免加载整张首页图片）。 */
    @Volatile
    internal var initialUrl: String = "$ORIGIN/robots.txt"

    private val fetchMutex = Mutex()

    /** 壳根挂载组件把活会话写进来；null = 还没挂（[mountRequested] 驱动壳挂载）。 */
    var state: WebViewState? by mutableStateOf(null)
        internal set

    /** 首次 R18 取数置真：壳根据此挂载 WebView 组件（浏览器随挂载创建）。 */
    val mountRequested = mutableStateOf(false)

    /** 撞人工挑战时亮真：宿主盒从隐形放大到面板让用户点验证框。 */
    val challengeVisible = mutableStateOf(false)

    private var idleJob: Job? = null

    /** 空闲卸载计时器的作用域：object 生命周期即进程，但归属明确、不再寄生在 GlobalScope。 */
    private val idleScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * 页内 fetch 一条 JSON。支持多站点（konachan.com / konachan.net / safebooru.donmai.us）：
     * 先确保 WebView2 停在目标站同源轻量页（`/robots.txt`）上再发同源 XHR；
     * 若 XHR 仍回 403/503（说明 WAF 只拦主站/API），自动跳到首页 HTML 亮出过盾面板，过完再重发。
     *
     * @param url 绝对地址（查询串已编码）。
     * @return 站方原始响应体（JSON 文本）。
     * @throws IllegalStateException WebView2 运行时没装 / 挂载超时 / fetch 失败 / 挑战超时未过。
     */
    suspend fun fetchJson(url: String): String = fetchMutex.withLock {
        if (WebView2Runtime.getAvailableVersion() == null) {
            error("WebView2 运行时没装（Evergreen 系统组件），无法过盾访问")
        }
        val uri = URI.create(url)
        val origin = "${uri.scheme}://${uri.host}"
        val host = uri.host.orEmpty()
        idleJob?.cancel()
        withContext(Dispatchers.Main.immediate) {
            if (!mountRequested.value) {
                initialUrl = "$origin/robots.txt"
            }
            mountRequested.value = true
        }
        val st = awaitActive()
        ensureOrigin(st, origin, host, "$origin/robots.txt")
        val first = evalFetch(st, url, host)
        if (first.status == 200) {
            scheduleIdleUnmount()
            return first.body
        }
        if (first.status != 403 && first.status != 503) {
            scheduleIdleUnmount()
            error("$host 返回 ${first.status}")
        }
        // robots.txt 未过盾（站方仅对主站/API 挂盾）或 clearance 过期：跳首页亮出过盾面板再重发。
        ensureOrigin(st, origin, host, "$origin/", forceNavigate = true)
        if (isChallenge(st.title)) {
            withContext(Dispatchers.Main.immediate) { challengeVisible.value = true }
            try {
                awaitChallengeCleared(st, host)
            } finally {
                withContext(Dispatchers.Main.immediate) { challengeVisible.value = false }
            }
        }
        val second = evalFetch(st, url, host)
        scheduleIdleUnmount()
        if (second.status != 200) error("$host 过盾后仍回 ${second.status}")
        return second.body
    }

    private class FetchResult(val status: Int, val body: String)

    /**
     * 页内取一条 JSON。⚠️ 这颗库的 `evaluateJavaScript` **不 await Promise**——async IIFE
     * 回的 Promise 被 JSON 编码成 `{}`（探针 v3 实测），fetch 路线整条死；而**同步 XHR 实测
     * 200**（同源+clearance 都在）。所以用异步 XHR + `window.__kon` 槽：发车脚本同步返回
     * 槽位号，Kotlin 侧轮询小同步脚本取回——每次 evaluate 都短，不顶库内 5 秒超时。
     *
     * @param url [ORIGIN] 下的绝对地址（查询串已编码）。
     */
    private suspend fun evalFetch(st: WebViewState, url: String, host: String): FetchResult {
        val slot = "k" + System.nanoTime().toString(36)
        val safeUrl = url.replace("'", "\\'")
        val slotVal = Json.parseToJsonElement(
            st.evaluateJavaScript(
                "(window.__kon = window.__kon || {}, " +
                    "(() => { const x = new XMLHttpRequest(); " +
                    "x.open('GET', '$safeUrl'); x.withCredentials = true; " +
                    "x.onload = () => { window.__kon['$slot'] = JSON.stringify({s: x.status, b: x.responseText}); }; " +
                    "x.onerror = () => { window.__kon['$slot'] = JSON.stringify({s: 0, b: 'XHR_ERR'}); }; " +
                    "x.send(); })(), '$slot')",
            ),
        ).jsonPrimitive.content
        val deadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < deadline) {
            delay(120)
            val raw = st.evaluateJavaScript("window.__kon['$slotVal'] || ''")
            val content = runCatching { Json.parseToJsonElement(raw).jsonPrimitive.content }.getOrNull().orEmpty()
            if (content.isEmpty()) continue
            runCatching { st.evaluateJavaScript("delete window.__kon['$slotVal']") }
            val inner = runCatching { Json.parseToJsonElement(content).jsonObject }
                .getOrElse { error("页内 XHR 回包不是 JSON：${content.take(120)}") }
            val status = inner["s"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            val body = inner["b"]?.jsonPrimitive?.content ?: ""
            if (status == 0) error("$host 网络连接失败，直连受阻时请开启代理")
            return FetchResult(status, body)
        }
        error("$host 请求超时（30s 无回包），请检查网络或代理设置")
    }

    /** 等壳根挂载完成且浏览器到 Active；若底层报错立即带出 st.error，超时也输出完整状态读数。 */
    private suspend fun awaitActive(): WebViewState {
        val deadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < deadline) {
            val st = state
            if (st != null) {
                if (st.lifecycle == WebViewLifecycle.Active) return st
                st.error?.let { err ->
                    println("D_WebView 初始化失败 lifecycle=${st.lifecycle} error=$err")
                    error("WebView2 初始化失败：$err")
                }
            }
            delay(150)
        }
        val snapshot = state
        println(
            "D_WebView 挂载超时 hasState=${snapshot != null} " +
                "lifecycle=${snapshot?.lifecycle} error=${snapshot?.error} mountRequested=${mountRequested.value}",
        )
        error("WebView 挂载超时")
    }

    /** 页必须停在 [origin] 同源上；跨站或强制刷新时导到 [navTarget]（轻量页 /robots.txt 不强求标题非空）。 */
    private suspend fun ensureOrigin(
        st: WebViewState,
        origin: String,
        host: String,
        navTarget: String,
        forceNavigate: Boolean = false,
    ) {
        if (forceNavigate || !st.url.startsWith(origin)) {
            withContext(Dispatchers.Main.immediate) {
                st.loadUrl(navTarget)
            }
            delay(200)
        }
        val deadline = System.currentTimeMillis() + 20_000
        while (System.currentTimeMillis() < deadline) {
            if (!st.isLoading && st.url.startsWith(origin) && !isChallenge(st.title)) {
                return
            }
            if (isChallenge(st.title)) {
                withContext(Dispatchers.Main.immediate) { challengeVisible.value = true }
                try {
                    awaitChallengeCleared(st, host)
                } finally {
                    withContext(Dispatchers.Main.immediate) { challengeVisible.value = false }
                }
                return
            }
            delay(150)
        }
        error("$host 页面加载超时，直连受阻时请检查代理设置")
    }

    /**
     * CF 质询页的页题**多语言**：zh-CN locale 是「请稍候…」，en 是 "Just a moment..."
     * （探针实测 157.0.4341.46 中文档回「请稍候…」）——只认英文会在中文系统上整条漏判。
     * 兜底再认 "Attention Required"（CF 的拦截档页题）。
     */
    private fun isChallenge(title: String): Boolean {
        val t = title.trim().lowercase()
        return "just a moment" in t || "请稍候" in t || "attention required" in t
    }

    private suspend fun awaitChallengeCleared(st: WebViewState, host: String) {
        val deadline = System.currentTimeMillis() + CHALLENGE_WAIT_MS
        while (System.currentTimeMillis() < deadline) {
            delay(500)
            if (!isChallenge(st.title) && !st.isLoading) return
        }
        error("$host 人机挑战超时未过")
    }

    private fun scheduleIdleUnmount() {
        idleJob?.cancel()
        idleJob = idleScope.launch {
            delay(IDLE_UNMOUNT_MS)
            withContext(Dispatchers.Main.immediate) {
                mountRequested.value = false
            }
        }
    }
}

/**
 * 壳根挂载点（[com.venera.desktop.app.VeneraDesktopLargeScreen] 在主题层挂**一次**）：
 *
 * - 平时隐形：1dp 小盒 + 全透明 —— 浏览器活着供页内 fetch，CPU 占用趋近零，
 *   也不占布局不拦点击；
 * - [DesktopKonachanBridge.challengeVisible] 亮真：盒放大到居中面板，帧画进自家场景，
 *   验证框在**自家面板里**点（无原生 surface，没有任务栏/Alt-Tab 露脸问题）。
 *
 * 会话写回 [DesktopKonachanBridge.state] 供取数侧 await；卸载由空闲计时驱动
 * （[DesktopKonachanBridge.mountRequested] 翻假 → 本组件离场 → 浏览器进程随挂载面退出）。
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun DesktopKonachanWebViewHost() {
    val st = rememberWebViewState(DesktopKonachanBridge.initialUrl)
    val settings = remember {
        // requestNativeFocus 关掉：隐形挂载时不许抢主窗键盘焦点（挑战框是鼠标件，够用）。
        WebViewSettings(
            userDataDirectory = File(
                System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"),
                "venera/konachan-webview2",
            ).absolutePath,
            requestNativeFocus = false,
        )
    }
    DisposableEffect(st) {
        println("D_WebView 宿主已挂载 initialUrl=${DesktopKonachanBridge.initialUrl}")
        DesktopKonachanBridge.state = st
        onDispose { DesktopKonachanBridge.state = null }
    }
    val challenge by DesktopKonachanBridge.challengeVisible
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(if (challenge) 100f else -1f)
            .then(
                if (challenge) {
                    Modifier
                        .background(Color(0x99000000))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        )
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = if (challenge) 760.dp else 1.dp, height = if (challenge) 620.dp else 1.dp)
                .alpha(if (challenge) 1f else 0f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White),
        ) {
            val awtWindow = LocalAwtWindow.current ?: remember {
                java.awt.Frame.getFrames().firstOrNull { it.isVisible }
                    ?: java.awt.Frame.getFrames().firstOrNull()
            }
            if (awtWindow != null) {
                CompositionLocalProvider(LocalAwtWindow provides awtWindow) {
                    WebView(st, Modifier.fillMaxSize(), settings = settings)
                }
            } else {
                WebView(st, Modifier.fillMaxSize(), settings = settings)
            }
        }
        if (challenge) {
            Text(
                text = "请完成人机验证，过完自动继续",
                fontSize = VeneraType.bodySecondary.size, lineHeight = VeneraType.bodySecondary.lineHeight,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp)
                    .background(Color(0xCC141A24), RoundedCornerShape(6.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}
