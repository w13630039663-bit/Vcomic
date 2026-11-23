package com.venera.engineprobe

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.define
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.venera.compose.engine.JsConvertHandler
import com.venera.compose.engine.JsHtmlHandler
import com.venera.compose.engine.JsHttpHandler
import com.venera.compose.engine.JsSourceDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch

/**
 * 桌面侧 JS 宿主（S0-3 探针）：用 GraalJS 顶替 Android 那边的 WebView 载体，
 * 桥协议与 [com.venera.compose.engine.VeneraJsEngine] 逐条对齐，handler 复用同一批源文件。
 *
 * 与 Android 侧的**唯一**结构性差异：WebView 的 `@JavascriptInterface` 全在单一
 * JavaBridge 线程上串行，所以那边必须把 http 拆成异步通道；GraalJS 的上下文本来就
 * 独占一个线程，这里 `sendMessageAsync` 直接内联执行完再回调 `window.__veneraResolve`，
 * JS 侧看到的语义（Promise resolve 后 takeAsyncResult 必命中）不变。
 */
class DesktopJsHost(
    assetDir: File,
    dataDir: File,
    proxy: String?,
    /**
     * `--cookie=<host>:<名>=<值>` 那几枚取证种子（批 88·下半）。**必填、不给默认值**：
     * 漏传的形状是「`-Pcookie=…` 递上而请求照旧不带凭据」，与要防的那一族同形（同一颗纪律）。
     *
     * 只并进**本次装载**：那几枚进的是 [DesktopCookieJar] 的种子层，永远不进盘上那份 ——
     * 盘上那份是用户在这台机器上的登录态，取证参数把它改掉等于量具动了被量的东西（批 83.1 同口径）。
     */
    seedCookies: List<String>,
    sharedCookieJar: DesktopCookieJar? = null,
    sharedClient: OkHttpClient? = null,
    sharedDataStore: JsSourceDataStore? = null,
) : AutoCloseable {

    internal val configuredAssetDir: File = assetDir
    internal val configuredDataDir: File = dataDir
    internal val configuredProxy: String? = proxy

    private val gson = Gson()
    private val bridgeGson = GsonBuilder().serializeNulls().create()
    private val mapType = object : TypeToken<Map<String, Any?>>() {}.type

    // 罐要**公开**给装配点：网页登录抓回的 Cookie 必须写进**这一颗**（引擎请求真正携带的那颗），
    // 另造一颗新实例写同一份文件的话，两颗罐各自整罐快照落盘，后写的会把先写的整罐抹掉
    // ——实测：登录成功后随便一发请求，刚抓的 ehentai 凭据就被引擎内存里的旧快照盖没了。
    val cookieJar: DesktopCookieJar = (sharedCookieJar ?: DesktopCookieJar(DesktopCookieJar.fileIn(dataDir))).also {
        if (DesktopCloudflareCenter.currentCookieJar() == null) {
            DesktopCloudflareCenter.registerCookieJar(it)
        }
    }

    private val client: OkHttpClient = sharedClient ?: DesktopCloudflareCenter.newClientBuilder(
        cookieJar = cookieJar,
        proxy = proxy,
    ).build()

    internal val httpClient: OkHttpClient get() = client

    private val htmlHandler = JsHtmlHandler()
    private val convertHandler = JsConvertHandler()
    private val httpHandler = JsHttpHandler({ client })
    private val dataStore: JsSourceDataStore = sharedDataStore ?: JsSourceDataStore(File(dataDir, "comic_source"))

    internal val sourceDataStore: JsSourceDataStore get() = dataStore

    private val quickJs: QuickJs = QuickJs.create(Dispatchers.Default)

    private val pendingAsyncResults = ConcurrentHashMap<String, String>()
    private val readyResolveIds = ConcurrentLinkedQueue<String>()
    private val pendingCallbacks = ConcurrentHashMap<String, (String) -> Unit>()

    init {
        quickJs.define("_venera") {
            function("sendMessage") { args ->
                processMessage(args.firstOrNull()?.toString().orEmpty())
            }
            function("sendMessageAsync") { args ->
                val reqId = args.getOrNull(0)?.toString().orEmpty()
                val jsonArgs = args.getOrNull(1)?.toString().orEmpty()
                handleSendMessageAsync(reqId, jsonArgs)
            }
            function("takeAsyncResult") { args ->
                val reqId = args.firstOrNull()?.toString().orEmpty()
                pendingAsyncResults.remove(reqId)
            }
            function("postAsyncResult") { args ->
                val callbackId = args.getOrNull(0)?.toString().orEmpty()
                val jsonResult = args.getOrNull(1)?.toString().orEmpty()
                pendingCallbacks.remove(callbackId)?.invoke(jsonResult)
            }
            function("log") { args ->
                val msg = args.joinToString(" ") { it?.toString().orEmpty() }
                println("[js] $msg")
            }
            function("getVersion") { "probe" }
            function("btoa") { args ->
                val s = args.firstOrNull()?.toString().orEmpty()
                EngineProbeBase64.encodeToString(ByteArray(s.length) { s[it].code.toByte() })
            }
            function("atob") { args ->
                val s = args.firstOrNull()?.toString().orEmpty()
                String(EngineProbeBase64.decode(s).map { (it.toInt() and 0xFF).toChar() }.toCharArray())
            }
        }

        // shim 需要 window 与定时器；QuickJS 默认已带 console，用 globalThis 覆写避免重声明报错。
        exec(
            """
            globalThis.window = globalThis;
            window.__timers = [];
            window.setTimeout = function(fn, ms) {
                window.__timers.push({ fn: fn, due: Date.now() + (ms || 0) });
                return window.__timers.length;
            };
            window.clearTimeout = function () {};
            window.__pumpTimers = function (now) {
                var due = [], keep = [];
                window.__timers.forEach(function (t) { (t.due <= now ? due : keep).push(t); });
                window.__timers = keep;
                due.forEach(function (t) { t.fn(); });
                return due.length;
            };
            globalThis.console = {
                log: function () { _venera.log('log', Array.prototype.slice.call(arguments).join(' ')); },
                info: function () { _venera.log('log', Array.prototype.slice.call(arguments).join(' ')); },
                warn: function () { _venera.log('warn', Array.prototype.slice.call(arguments).join(' ')); },
                error: function () { _venera.log('error', Array.prototype.slice.call(arguments).join(' ')); },
            };
            globalThis.btoa = function (s) { return _venera.btoa(s); };
            globalThis.atob = function (s) { return _venera.atob(s); };
            """.trimIndent(),
        )

        // 装载脚本是多语句源码，必须原样 eval —— 顺序与内容一字未动
        exec(EngineAssets.readText(assetDir, "venera-shim.js"))
        exec(EngineAssets.readText(assetDir, "venera-init.js"))
        val patched = evaluate("_veneraApplyPostInitPatches()")
        if (patched?.contains("true") != true) {
            println("[probe] WARN post-init patches not applied: $patched")
        }
        applySeedCookies(seedCookies)
    }

    /**
     * 把取证种子那几枚并进本次装载，并念一行点名**真吃下几枚**。
     *
     * 解析与落罐都在 [DesktopCookieJar.seed] 里（那一颗件才懂 host 与域那两形的口径），
     * 这里只数"报出去的那一句是不是实话"：applied 而不是 parsed。
     */
    private fun applySeedCookies(seeds: List<String>) {
        if (seeds.isEmpty()) return
        val applied = seeds.count { cookieJar.seed(it) }
        // ASCII 那半是给交付包内留的（包内中文 stdout 全成乱码，批 87·下半那一课）
        println("D_凭据罐 种子=$applied/${seeds.size} 盘=未写 seeded=$applied/${seeds.size}")
    }

    /**
     * 源登录成功之后把「已登录」那一格落到盘上。
     *
     * 口径**照对岸** `ComicSourceManager.markLoggedIn`（`dataStore.saveAccount(key, "ok")`），
     * 桌面不另造一套：那一格是 `isLogged` 那一问在读的东西，两轨就会长成"屏上说登录过而源脚本说没有"。
     */
    fun markLoggedIn(sourceKey: String) = dataStore.saveAccount(sourceKey, "ok")

    /** 桥后面那颗罐此刻的登录态（屏上「已登录」那一行读它，不读界面自己记的那笔）。 */
    fun isLogged(sourceKey: String): Boolean = dataStore.isLogged(sourceKey)

    /**
     * 撤掉某一颗源的登录那一格（「账号」段的写侧）。
     *
     * 口径**照对岸** `ComicSourceManager` 那颗罐自带的 `logout`（同一份存储、同一颗动作），
     * 桌面不另造一套。只清「已登录」那一格，不碰凭据本体与数据 —— 与对岸同形。
     */
    fun logoutSource(sourceKey: String) = dataStore.logout(sourceKey)

    /**
     * 设置值落在的那颗罐（「源设置」段）。
     *
     * 为什么递出私有件：设置读/写那两跳住在 `EngineSession`（它才认 `key`），而罐归 host 持有 ——
     * 转手这一颗比"再建第二份罐"诚实（两份罐 = 两处各读一遍，读成两种形状正是本仓反复记过的那族）。
     * 仅限 engine-probe 内部消费，不进任何 UI 的 import 面。
     */
    fun dataStoreOf(): JsSourceDataStore = dataStore

    /** 原样执行（装载 shim/init/源脚本用），不做返回值包装 */
    fun exec(script: String) {
        runBlocking { quickJs.evaluate<Any?>(script) }
        if (readyResolveIds.isNotEmpty()) drainPendingResolves()
    }

    /**
     * 求值并返回完成值的字符串形式。
     *
     * 不能像 Android 侧那样把脚本塞进 `return (...)` —— 源装载脚本以 `;` 结尾，
     * 塞进括号就是语法错误。GraalJS 的 eval 本身就返回 completion value。
     */
    /**
     * 探针专用：按 onImageLoad 给的 headers 取图字节。
     *
     * 源给的 headers 里带 `Accept-Encoding: gzip`，而 OkHttp 一旦看到调用方自己设了这个头
     * 就**关掉透明解压** —— Android 侧由 ImagePipelinePolicy.decodeBody 兜这条，这里同语义兜。
     */
    fun fetchBytes(url: String, headers: Map<String, String>): Pair<Int, ByteArray> {
        val req = okhttp3.Request.Builder().url(url).apply {
            headers.forEach { (k, v) ->
                if (!k.equals("Accept-Encoding", ignoreCase = true)) {
                    runCatching { header(k, v) }
                }
            }
        }.build()
        client.newCall(req).execute().use { r ->
            val raw = r.body?.bytes() ?: ByteArray(0)
            val encoding = r.header("Content-Encoding")
            val gzipped = raw.size > 2 && raw[0] == 0x1F.toByte() && raw[1] == 0x8B.toByte()
            val body = if (encoding?.contains("gzip", true) == true || (encoding == null && gzipped)) {
                java.util.zip.GZIPInputStream(raw.inputStream()).use { it.readBytes() }
            } else raw
            return r.code to body
        }
    }

    fun evaluate(script: String): String? = try {
        val raw = runBlocking { quickJs.evaluate<Any?>(script) }
        if (readyResolveIds.isNotEmpty()) drainPendingResolves()
        decode(raw)
    } catch (e: Exception) {
        println("[probe] eval error: ${e.message?.take(300)}")
        null
    }

    /**
     * 求值并**把失败如实交回**（源编辑的装载校验要用）。
     *
     * 为什么要有这一颗而不能用 [evaluate]：后者把异常咽成 `null`，只在 stdout 留一行。
     * 编辑器需要向用户念出「为什么这篇脚本装不起来」—— 而引擎报语法错时的消息带着
     * 行号与片段，正是那一句话。咽掉之后屏上只剩「取不到 source key」，
     * 那是把**用户的笔误**说成**源的毛病**，与「该源没有 search」被念成「这个词没搜到」同族。
     */
    fun evaluateResult(script: String): Result<String?> = try {
        val raw = runBlocking { quickJs.evaluate<Any?>(script) }
        if (readyResolveIds.isNotEmpty()) drainPendingResolves()
        Result.success(decode(raw))
    } catch (e: Exception) {
        Result.failure(e)
    }

    fun registerDefaultSettings(key: String, defs: Map<String, Any?>) {
        dataStore.registerDefaultSettings(key, defs)
    }

    /** 对齐 Android 侧的 evaluateAsync：返回 `{"success":..,"data":..}` 信封原文 */
    fun evaluateAsync(script: String, timeoutMs: Long = 90_000): String {
        val cbId = UUID.randomUUID().toString()
        val latch = CountDownLatch(1)
        val out = arrayOfNulls<String>(1)
        pendingCallbacks[cbId] = { res -> out[0] = res; latch.countDown() }
        exec(
            """
            (function() {
                try {
                    var _val = (function() { $script })();
                    _runAsync('$cbId', _val);
                } catch (e) {
                    _venera.postAsyncResult('$cbId', JSON.stringify({ success: false, error: String(e) }));
                }
            })();
            """.trimIndent(),
        )
        val deadline = System.currentTimeMillis() + timeoutMs
        while (latch.count > 0L && System.currentTimeMillis() < deadline) {
            drainPendingResolves()
            runDueTimers()
            if (latch.count > 0L && readyResolveIds.isEmpty()) {
                Thread.sleep(2L)
            }
        }
        pendingCallbacks.remove(cbId)
        return out[0] ?: """{"success":false,"error":"evaluateAsync timeout after ${timeoutMs}ms"}"""
    }

    private fun runDueTimers() {
        val now = System.currentTimeMillis()
        runBlocking {
            runCatching { quickJs.evaluate<Any?>("window.__pumpTimers($now)") }
        }
    }

    private fun drainPendingResolves() {
        while (true) {
            val reqId = readyResolveIds.poll() ?: break
            val quoted = bridgeGson.toJson(reqId)
            runBlocking {
                runCatching { quickJs.evaluate<Any?>("window.__veneraResolve($quoted)") }
                    .onFailure { println("[probe] WARN resolve failed: $it") }
            }
        }
    }

    private fun decode(v: Any?): String? = v?.toString()

    private fun handleSendMessageAsync(reqId: String, jsonArgs: String) {
        val map = runCatching { gson.fromJson<Map<String, Any?>>(jsonArgs, mapType) }.getOrNull()
        if (map == null) {
            publishAsyncResult(reqId, bridgeGson.toJson(mapOf("__error__" to "invalid async args")))
            return
        }
        val payload = try {
            bridgeGson.toJson(mapOf("__result__" to httpHandler.handle(map)))
        } catch (e: Exception) {
            bridgeGson.toJson(mapOf("__error__" to (e.message ?: e.javaClass.simpleName)))
        }
        publishAsyncResult(reqId, payload)
    }

    private fun publishAsyncResult(reqId: String, payload: String) {
        pendingAsyncResults[reqId] = payload
        // 投递到待唤醒队列，在 QuickJS evaluate 返回后的外层事件循环安全执行 __veneraResolve，避免同步宿主回调重入死锁。
        readyResolveIds.add(reqId)
    }

    /** 与 Android 侧 processMessage 同一张方法表 */
    private fun processMessage(jsonArgs: String): String = try {
        val map = gson.fromJson<Map<String, Any?>>(jsonArgs, mapType)
            ?: return bridgeGson.toJson(mapOf("error" to "invalid args"))
        val method = map["method"] as? String ?: return bridgeGson.toJson(mapOf("error" to "no method"))
        when (method) {
            "log" -> {
                println("[js:${map["level"] ?: "info"}:${map["title"]}] ${map["content"]}")
                bridgeGson.toJson(mapOf("__result__" to null))
            }
            "load_data" -> result(dataStore.loadData(str(map, "key"), str(map, "data_key")))
            "save_data" -> {
                val err = dataStore.saveData(str(map, "key"), str(map, "data_key"), map["data"])
                if (err != null) bridgeGson.toJson(mapOf("error" to err)) else result(null)
            }
            "delete_data" -> {
                dataStore.deleteData(str(map, "key"), str(map, "data_key")); result(null)
            }
            "load_setting" -> result(dataStore.loadSetting(str(map, "key"), str(map, "setting_key")))
            "save_setting" -> {
                dataStore.saveSetting(str(map, "key"), str(map, "setting_key"), map["value"]); result(null)
            }
            "isLogged" -> result(dataStore.isLogged(str(map, "key")))
            "http" -> result(httpHandler.handle(map))
            "html" -> result(htmlHandler.handle(map))
            "convert" -> result(convertHandler.handle(map))
            "random" -> {
                val min = (map["min"] as? Number)?.toDouble() ?: 0.0
                val max = (map["max"] as? Number)?.toDouble() ?: 1.0
                val raw = min + Math.random() * (max - min)
                result(if (map["type"] == "double") raw else kotlin.math.floor(raw).toInt())
            }
            "uuid" -> result(UUID.randomUUID().toString())
            "getLocale" -> result("${Locale.getDefault().language}_${Locale.getDefault().country}")
            // 桌面侧继续报 android：源脚本里有平台分支，报 windows 会走进没验过的路径
            "getPlatform" -> result("android")
            "delay" -> {
                val ms = (map["time"] as? Number)?.toLong() ?: 0L
                if (ms > 0) Thread.sleep(ms)
                result(null)
            }
            "cookie" -> result(handleCookie(map))
            "UI" -> result(handleUi(map))
            "setClipboard" -> result(null)
            "getClipboard" -> result("")
            else -> bridgeGson.toJson(mapOf("error" to "unknown method: $method"))
        }
    } catch (e: Exception) {
        println("[probe] processMessage error: $e")
        bridgeGson.toJson(mapOf("error" to (e.message ?: e.javaClass.simpleName)))
    }

    private fun result(v: Any?): String = bridgeGson.toJson(mapOf("__result__" to v))

    private fun str(map: Map<String, Any?>, key: String): String = map[key] as? String ?: ""

    private fun handleCookie(data: Map<String, Any?>): Any? {
        val fn = data["function"] as? String ?: return null
        val urlStr = data["url"] as? String ?: return null
        val httpUrl = urlStr.toHttpUrlOrNull() ?: "https://$urlStr".toHttpUrlOrNull() ?: return null
        return when (fn) {
            "get" -> cookieJar.loadForRequest(httpUrl).map {
                mapOf(
                    "name" to it.name, "value" to it.value, "domain" to it.domain,
                    "path" to it.path, "expires" to it.expiresAt, "secure" to it.secure,
                    "httpOnly" to it.httpOnly,
                )
            }
            "set" -> {
                val list = data["cookies"] as? List<Map<String, Any?>> ?: return null
                cookieJar.saveFromResponse(httpUrl, list.mapNotNull { buildCookie(it, httpUrl.host) })
                null
            }
            "delete" -> {
                cookieJar.clearForUrl(httpUrl); null
            }
            else -> null
        }
    }

    /** 域名归一化与逐条容错：与 Android 侧 buildJsCookie 同语义（前导点、脏域、整批不能废） */
    private fun buildCookie(m: Map<String, Any?>, fallbackHost: String): Cookie? {
        val name = m["name"] as? String ?: return null
        val value = m["value"] as? String ?: return null
        if (name.isEmpty()) return null
        val domain = (m["domain"] as? String)?.trim()?.lowercase()?.let { raw ->
            var d = raw
            while (d.startsWith(".")) d = d.substring(1)
            if (d.isEmpty() ||
                d.any { it == ' ' || it == '/' || it == ':' || it == ';' || it == ',' || it == '@' } ||
                d.all { it.isDigit() || it == '.' }
            ) null else d
        }
        return runCatching {
            val b = Cookie.Builder().name(name).value(value).domain(domain ?: fallbackHost)
            (m["path"] as? String)?.takeIf { it.isNotEmpty() }?.let { b.path(it) }
            if (m["secure"] == true) b.secure()
            if (m["httpOnly"] == true) b.httpOnly()
            b.build()
        }.onFailure { println("[probe] skip invalid cookie: $name domain=${m["domain"]}") }.getOrNull()
    }

    private fun handleUi(data: Map<String, Any?>): Any? {
        // 与 Android 侧同一处既存缺陷（方案文档 5.2）：对话框类返回 null 让源走取消分支。
        // 探针阶段照抄，是为了让两侧的返回形状可比；修它属于另一轮。
        return when (data["function"] as? String) {
            "showMessage" -> {
                println("[js UI] ${data["message"]}"); null
            }
            "launchUrl" -> {
                println("[js UI] launchUrl ${data["url"]}（探针不打开浏览器）"); null
            }
            "showLoading" -> 1
            else -> null
        }
    }

    override fun close() {
        runCatching { quickJs.close() }
    }
}

private object EngineProbeBase64 {
    fun encodeToString(bytes: ByteArray): String = java.util.Base64.getEncoder().encodeToString(bytes)
    fun decode(text: String): ByteArray = java.util.Base64.getMimeDecoder().decode(text)
}
