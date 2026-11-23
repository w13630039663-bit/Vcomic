package com.venera.desktop.gallery.data

import com.venera.compose.gallery.data.GalleryPixivCollections
import com.venera.compose.gallery.data.PixivAppClient
import com.venera.compose.gallery.data.PixivClient
import com.venera.compose.gallery.data.PixivCollectionsParse
import com.venera.compose.gallery.data.PixivCollectionsSearchPage
import com.venera.engineprobe.DesktopCookieJar
import java.io.File
import java.io.IOException
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 桌面侧的 pixiv **珍藏册**取数件（`GalleryPixivCollections`）。
 *
 * ## 两条腿，两颗出口（与 `DesktopGalleryPixivWorks` 匿名/带凭据分持同一条理由）
 *
 * - **匿名腿**（发现页 / 详情 / 相关 / 搜索 / 推荐标签）：走 [PixivClient] 的五条 GET，
 *   全挂 [requestGuard]（WWW 通道 600ms 节流 + 429 熔断）与 [DesktopPixivCache]
 *   （复用 pixiv 那棵缓存目录，键 `collection|…`），TTL 照 `DesktopGalleryPixivWorks` 的四档。
 * - **登录腿**（收藏三跳 + 用户的收藏列表）：`www.pixiv.net` **web 会话** —— 这与 pixiv
 *   OAuth 的 Bearer（app-api）不是一回事，Bearer 换不来 web 会话。会话材料只有两样：
 *   `DesktopCookieJar` 罐里那批 pixiv cookie + 页面 `__NEXT_DATA__` 里的 csrf token。
 *
 * ## 为什么登录腿的 client 是**每次现建**而不是类内单例（与的差异，理由如下）
 *
 * `DesktopCookieLogin.kt:13-17` 记过那一族：两颗 `DesktopCookieJar` 指向同一份文件，
 * 各自 init 读一份、各自 persist **整份覆盖** —— 用户在引擎那一侧刚登上的 cookie，
 * 会被一颗拿着旧内存份的罐在下一次 persist 时整笔盖掉。罐里今天还没有 pixiv 的桶
 * （实测），但用户随时可能去登；收藏动作又是低频的用户点击，所以每次登录腿现起
 * client + 现读罐（一次文件读），一趟内（收割 csrf → POST）共用同一颗实例，
 * 趟与趟之间不背旧内存份。这是 `DesktopCookieLogin` 那条「另起一颗只读实例」的同一口径。
 *
 * ## csrf 收割四步（M3 注释口径，匿名页也带 token，实测 32 位十六进制）
 *
 * ① GET pixiv 网页收藏页(域名见 DESKTOP_PIXIV_REFERER)（匿名 200）；② 正则切 `<script id="__NEXT_DATA__">`
 * —— 标签带 `type="application/json"` 属性，**写死 `">` 匹配不上**，必须放宽 `[^>]*`；
 * ③ 第一次解析取 `props.pageProps.serverSerializedPreloadedState` —— 它本身是
 * **JSON 字符串**（实测 type=str），要第二次解析；④ 二次解析后 `api.token` 可见。
 * 两步任一失败 = failure，不缓存坏值。
 *
 * ## 带会话行为全部未验证（如实）
 *
 * 罐里今天没有 pixiv cookie ⇒ 以下四枚的**登录后真实形状都无法实测**，只有端点形状
 * 来自 pixiv-web-next JS 与匿名探测：
 * - `POST /ajax/collections/bookmarks/add` 匿名 422（端点在）；`remove` 的表单键按
 *   pixiv web 删除收藏的惯例写成重复键 `bookmarkIds[]`（**未验证**）；
 * - `GET /ajax/collection/{id}/bookmarkData` 真实 id 匿名 404（同一 id detail 均 200）；
 * - `GET /ajax/user/{id}/collections/bookmarks` 匿名 400（带 limit/offset 也 400）。
 * 每一枚失败都如实 failure 回传，不假成功；罐里没有 pixiv cookie 时**不发请求**
 * 直接 failure「需要 pixiv 网页登录」。
 */
internal class DesktopPixivCollections(
    private val handle: DesktopGalleryHandle,
    private val requestGuard: DesktopPixivRequestGuard = DesktopPixivRequestGuard(),
) : GalleryPixivCollections {

    private val client: PixivClient get() = PixivClient.getInstance(handle.engine)

    /** 与 `DesktopGalleryPixivWorks` 同一棵缓存目录：同一站同一罐，坏一个文件只丢一格。 */
    private val pixivCache = DesktopPixivCache(File(handle.paths.cacheRoot, "pixiv_cache"))

    /** 列表类 TTL（检索/推荐：30 分钟）。 */
    private val TTL_LIST = 30L * 60 * 1000

    /** 详情类 TTL（详情：6 小时）。 */
    private val TTL_DETAIL = 6L * 60 * 60 * 1000

    /** 匿名腿 TTL（发现页/推荐标签：1 小时）。 */
    private val TTL_ANON = 60L * 60 * 1000

    /**
     * 收藏态落盘键（进 [pixivCache] 同一罐，无 TTL 恒读 stale）；重启后心形不回零，
     * 否则对站上已收藏的册子会重复发 add。初始值读盘，之后每次写穿同步落盘。
     */
    private val BOOKMARKED_KEY = "collection|bookmarkedIds"

    private val _bookmarkedIds = MutableStateFlow(
        pixivCache.getStale<Set<String>>(BOOKMARKED_KEY, tt<Set<String>>())?.toSet() ?: emptySet(),
    )

    /** 本机已确认收藏态（落盘）；收藏动作与 bookmarkState 成功写穿。 */
    override val bookmarkedIds: StateFlow<Set<String>> = _bookmarkedIds.asStateFlow()

    /** 写穿唯一口：改集合与落盘同走这里，两腿不各自拼。 */
    private fun syncBookmarked(id: String, on: Boolean) {
        val cur = _bookmarkedIds.value
        val next = if (on) cur + id else cur - id
        if (next != cur) {
            _bookmarkedIds.value = next
            runCatching {
                pixivCache.put(BOOKMARKED_KEY, next)
            }
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    // ── 匿名腿（`www`，不要任何凭据）──

    override suspend fun top(fresh: Boolean) =
        cached("collection|v3|top", TTL_ANON, PixivApiChannel.WWW, bypass = fresh) {
            client.collectionsTop()
        }

    override suspend fun recommendedTags() =
        cached("collection|v3|tags", TTL_ANON, PixivApiChannel.WWW) {
            client.collectionsRecommendedTags()
        }

    override suspend fun detail(id: String, fresh: Boolean) =
        cached("collection|v3|detail|$id", TTL_DETAIL, PixivApiChannel.WWW, bypass = fresh) {
            client.collectionById(id)
        }

    override suspend fun search(tag: String, mode: String, limit: Int, offset: Int) =
        cached("collection|v3|search|$tag|$mode|$offset", TTL_LIST, PixivApiChannel.WWW) {
            client.collectionsSearch(tag, mode, limit, offset)
        }

    override suspend fun recommend(id: String, fresh: Boolean) =
        cached("collection|v3|recommend|$id", TTL_LIST, PixivApiChannel.WWW, bypass = fresh) {
            client.collectionRecommend(id)
        }

    // ── 登录腿（www web 会话：罐里的 pixiv cookie + 页面 csrf token）──

    override suspend fun bookmarkState(id: String): Result<Boolean> =
        withWebSession("collectionBookmarkState|$id") { session ->
            val body = webGetJson(
                session,
                "$WEB_BASE/ajax/collection/$id/bookmarkData",
            )
            val state = bookmarkedFromBookmarkData(body)
            syncBookmarked(id, on = state)
            state
        }

    override suspend fun addBookmark(id: String, private0: Boolean): Result<Unit> =
        withWebSession("collectionBookmarkAdd|$id|$private0") { session ->
            val form = FormBody.Builder()
                .add("collectionId", id)
                .add("restrict", if (private0) "1" else "0")
                .build()
            requireAjaxOk(
                webPostJson(session, "$WEB_BASE/ajax/collections/bookmarks/add", form),
                "收藏珍藏册",
            )
            syncBookmarked(id, on = true)
        }

    override suspend fun removeBookmark(id: String): Result<Unit> =
        withWebSession("collectionBookmarkRemove|$id") { session ->
            // 表单键按 pixiv web 删收藏的惯例写成重复键 `bookmarkIds[]`（数组形状）；
            // 带会话行为未验证，站方不认就以 failure 如实回传，不试第二套键名。
            val form = FormBody.Builder()
                .add("bookmarkIds[]", id)
                .build()
            requireAjaxOk(
                webPostJson(session, "$WEB_BASE/ajax/collections/bookmarks/remove", form),
                "取消收藏珍藏册",
            )
            syncBookmarked(id, on = false)
        }

    override suspend fun myBookmarked(userId: Long, limit: Int, offset: Int): Result<PixivCollectionsSearchPage> =
        withWebSession("collectionMyBookmarked|$userId|$offset") { session ->
            val url = "$WEB_BASE/ajax/user/$userId/collections/bookmarks".toHttpUrl().newBuilder()
                .addQueryParameter("limit", limit.toString())
                .addQueryParameter("offset", offset.toString())
                .build()
            PixivCollectionsParse.userBookmarks(webGetJson(session, url.toString()), offset)
        }

    // ── web 会话件 ──

    /** 一趟登录腿共用的那一颗 client + 收割到的 csrf token。 */
    private class WebSession(
        val client: OkHttpClient,
        val csrfToken: String,
        val fetchedAtMs: Long,
        val cookieStamp: Long,
    )

    @Volatile
    private var cachedSession: WebSession? = null

    private class WebHttpStatusException(val code: Int, message: String) : IOException(message)

    private suspend fun <T> withWebSession(
        flightKey: String,
        block: suspend (WebSession) -> T,
    ): Result<T> {
        val session = webSession().getOrElse { return Result.failure(it) }
        val first = requestGuard.execute(flightKey, PixivApiChannel.WWW) {
            runCatching { block(session) }
        }
        val err = first.exceptionOrNull()
        if (err is WebHttpStatusException && (err.code == 401 || err.code == 403)) {
            cachedSession = null
            val refreshed = webSession(forceRefresh = true).getOrElse { return Result.failure(it) }
            return requestGuard.execute("$flightKey|retry", PixivApiChannel.WWW) {
                runCatching { block(refreshed) }
            }
        }
        return first
    }

    /**
     * 现起 client + 现读罐（理由见类注释「为什么每次现建」）。罐里没有 pixiv cookie 就
     * 直接 failure「需要 pixiv 网页登录」，**不发任何请求**。
     * 令牌短时缓存 10 分钟（cookie 文件变更即失效），避免每次操作连发两次网页请求。
     */
    private fun buildClient(jar: DesktopCookieJar): OkHttpClient = handle.engine.okHttpClient.newBuilder()
        .cookieJar(jar)
        .connectTimeout(Duration.ofSeconds(15))
        .readTimeout(Duration.ofSeconds(15))
        .build()

    private suspend fun webSession(forceRefresh: Boolean = false): Result<WebSession> {
        val cookieFile = DesktopCookieJar.fileIn(handle.paths.dataRoot)
        val stamp = cookieFile.lastModified()
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            val hit = cachedSession
            if (hit != null && hit.cookieStamp == stamp && now - hit.fetchedAtMs < SESSION_TTL_MS) {
                return Result.success(hit)
            }
        }
        val hasCookie = withContext(Dispatchers.IO) {
            val jar = DesktopCookieJar(cookieFile)
            jar.loadForRequest(DESKTOP_PIXIV_REFERER.toHttpUrl()).isNotEmpty()
        }
        if (!hasCookie) {
            cachedSession = null
            return Result.failure(IOException("需要 pixiv 网页登录"))
        }
        return requestGuard.execute("collectionWebSession", PixivApiChannel.WWW) {
            withContext(Dispatchers.IO) {
                runCatching {
                    val jar = DesktopCookieJar(cookieFile)
                    if (jar.loadForRequest(DESKTOP_PIXIV_REFERER.toHttpUrl()).isEmpty()) {
                        throw IOException("需要 pixiv 网页登录")
                    }
                    val client = buildClient(jar)
                    val page = client.newCall(
                        Request.Builder()
                            .url("$WEB_BASE/collection")
                            .header("Accept", "text/html,application/xhtml+xml")
                            .header("Accept-Language", PixivAppClient.PIXIV_TAG_LANGUAGE)
                            .build(),
                    ).execute().use { response ->
                        val body = response.body?.string().orEmpty()
                        if (response.code == 429) {
                            val retryAfter = response.header("Retry-After")?.trim()?.toLongOrNull()
                            val suffix = if (retryAfter != null && retryAfter > 0) "（Retry-After=${retryAfter}s）" else ""
                            throw IOException("pixiv 返回 429$suffix")
                        }
                        if (!response.isSuccessful) throw IOException("pixiv 网页返回 ${response.code}")
                        body
                    }
                    // ② 正切 __NEXT_DATA__：属性必须放宽（旧写法 '<script id="__NEXT_DATA__">' 第一脚就断）。
                    val nextData = NEXT_DATA_REGEX.find(page)?.groupValues?.getOrNull(1)
                        ?: throw IOException("页面里没有 __NEXT_DATA__，csrf 收不了（需要 pixiv 网页登录）")
                    // ③ serverSerializedPreloadedState 本身是 JSON 字符串，要第二次解析。
                    val preloaded = json.parseToJsonElement(nextData).jsonObject.let { root ->
                        ((root["props"] as? JsonObject)?.get("pageProps") as? JsonObject)
                            ?.get("serverSerializedPreloadedState")?.jsonPrimitive?.contentOrNull
                    } ?: throw IOException("__NEXT_DATA__ 里没有预载状态，csrf 收不了")
                    // ④ 二次解析后 api.token 可见。
                    val token = (((json.parseToJsonElement(preloaded).jsonObject["api"] as? JsonObject)
                        ?.get("token")) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
                        ?: throw IOException("预载状态里没有 api.token，csrf 收不了")
                    WebSession(client, token, System.currentTimeMillis(), cookieFile.lastModified()).also {
                        cachedSession = it
                    }
                }
            }
        }
    }

    private suspend fun webGetJson(session: WebSession, url: String): String = withContext(Dispatchers.IO) {
        session.client.newCall(
            Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("Accept-Language", PixivAppClient.PIXIV_TAG_LANGUAGE)
                .header("Referer", "$WEB_BASE/")
                .build(),
        ).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (response.code == 429) {
                val retryAfter = response.header("Retry-After")?.trim()?.toLongOrNull()
                val suffix = if (retryAfter != null && retryAfter > 0) "（Retry-After=${retryAfter}s）" else ""
                throw IOException("pixiv 返回 429$suffix")
            }
            if (!response.isSuccessful) throw WebHttpStatusException(response.code, "pixiv 返回 ${response.code}")
            body
        }
    }

    private suspend fun webPostJson(session: WebSession, url: String, form: FormBody): String =
        withContext(Dispatchers.IO) {
            session.client.newCall(
                Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .header("Accept-Language", PixivAppClient.PIXIV_TAG_LANGUAGE)
                    .header("Referer", "$WEB_BASE/collection")
                    .header("x-csrf-token", session.csrfToken)
                    .post(form)
                    .build(),
            ).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.code == 429) {
                    val retryAfter = response.header("Retry-After")?.trim()?.toLongOrNull()
                    val suffix = if (retryAfter != null && retryAfter > 0) "（Retry-After=${retryAfter}s）" else ""
                    throw IOException("pixiv 返回 429$suffix")
                }
                if (!response.isSuccessful) throw WebHttpStatusException(response.code, "pixiv 返回 ${response.code}")
                body
            }
        }

    /** ajax 信封：`error=true` 抛站方的话，调用方转 failure。 */
    private fun requireAjaxOk(body: String, who: String) {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }
            .getOrElse { throw IOException("$who 答复不是 JSON：${body.take(SAMPLE)}") }
        if (root["error"]?.jsonPrimitive?.contentOrNull?.lowercase() == "true") {
            val message = (root["message"] as? JsonPrimitive)?.contentOrNull.orEmpty().ifBlank { body.take(SAMPLE) }
            throw IOException("$who 失败：$message")
        }
    }

    /**
     * bookmarkData 的宽容读：`body` 是布尔（true/false）或对象里 `bookmarked` 布尔，两种都认；
     * 其它形状 = 抛（带会话行为未验证，认不出的形状不猜成"已收藏"）。
     */
    private fun bookmarkedFromBookmarkData(body: String): Boolean {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }
            .getOrElse { throw IOException("bookmarkData 答复不是 JSON：${body.take(SAMPLE)}") }
        if (root["error"]?.jsonPrimitive?.contentOrNull?.lowercase() == "true") {
            val message = (root["message"] as? JsonPrimitive)?.contentOrNull.orEmpty().ifBlank { body.take(SAMPLE) }
            throw IOException("查收藏态失败：$message")
        }
        val payload = root["body"]
        when (payload) {
            is JsonPrimitive -> payload.booleanOrNull?.let { return it }
            is JsonObject -> payload["bookmarked"]?.jsonPrimitive?.booleanOrNull?.let { return it }
            // 数组 / null 等未验证形状：落到底部的「读不懂」如实抛，不猜成已收藏。
            else -> {}
        }
        throw IOException("bookmarkData 答复形状读不懂（带会话行为未验证）：${body.take(SAMPLE)}")
    }

    // ── 缓存件（形状照 `DesktopGalleryPixivWorks.cachedImpl`，去掉收藏同步那半）──

    private suspend fun <T> cachedImpl(
        key: String,
        ttlMs: Long,
        type: java.lang.reflect.Type,
        channel: PixivApiChannel,
        priority: PixivApiPriority = PixivApiPriority.HIGH,
        bypass: Boolean = false,
        fetch: suspend () -> Result<T>,
    ): Result<T> {
        if (!bypass) {
            pixivCache.get<T>(key, ttlMs, type)?.let { return Result.success(it) }
            if (requestGuard.isInCooldown(channel)) {
                pixivCache.getStale<T>(key, type)?.let { return Result.success(it) }
            }
        }
        val flightKey = if (bypass) "fresh|$key" else key
        val res = requestGuard.execute(flightKey, channel, priority, fetch)
        val payload = res.getOrNull()
        if (payload != null) {
            pixivCache.put(key, payload)
        } else if (res.isFailure && !bypass) {
            pixivCache.getStale<T>(key, type)?.let { return Result.success(it) }
        }
        return res
    }

    private suspend inline fun <reified T> cached(
        key: String,
        ttlMs: Long,
        channel: PixivApiChannel,
        priority: PixivApiPriority = PixivApiPriority.HIGH,
        bypass: Boolean = false,
        noinline fetch: suspend () -> Result<T>,
    ): Result<T> = cachedImpl(key, ttlMs, tt<T>(), channel, priority, bypass, fetch)

    companion object {
        private const val WEB_BASE = "https://www.pixiv.net"

        /** 网页会话 CSRF 令牌缓存时长（10 分钟）。 */
        private const val SESSION_TTL_MS = 10L * 60 * 1000

        /** 报错消息里带多少原始答复（口径同 `PixivFeedParse.SAMPLE`）。 */
        private const val SAMPLE = 120

        /**
         * ⚠️ 标签带 `type="application/json"` 属性 —— 写死 `'<script id="__NEXT_DATA__">'`
         * 匹配不上（M3 实测），必须放宽到 `[^>]*`。
         */
        private val NEXT_DATA_REGEX =
            Regex("""<script id="__NEXT_DATA__"[^>]*>(.*?)</script>""", setOf(RegexOption.DOT_MATCHES_ALL))
    }
}
