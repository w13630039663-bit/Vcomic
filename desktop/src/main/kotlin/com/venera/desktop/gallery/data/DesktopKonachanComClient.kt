package com.venera.desktop.gallery.data

import com.venera.compose.data.platform.HttpEngine
import com.venera.compose.data.platform.NoInteractiveBypassTag
import com.venera.compose.gallery.data.GALLERY_TAG_SUGGESTION_LIMIT
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.data.GalleryTagSuggestion
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Request

/**
 * konachan.com（R18 库）的读侧客户端：Moebooru 接口与 [DesktopKonachanClient] 同形。
 *
 * 走 [HttpEngine] + 全局 `DesktopCloudflareCenter`：首次或凭据过期撞 403 CF 盾时自动通过
 * Edge 过盾提取 `cf_clearance` 与配套 UA 并落盘，后续请求直接复用 OkHttp 连接池，
 * 避免每次冷启动 WebView2 加载完整首页导致的 20s+ 慢查询与超时。
 *
 * [GalleryPost.site] 必须写 [GallerySite.KONACHAN_R18]：与 `.net` 同库同 id，站别不同
 * 收藏/缓存键就静默交错。DTO 与 `.net` 那颗共用（同一套字段，只换站别）。
 */
internal class DesktopKonachanComClient private constructor(private val engine: HttpEngine) {

    suspend fun searchPosts(tags: String, page: Int, limit: Int = POOL_SIZE): Result<List<GalleryPost>> =
        withContext(Dispatchers.IO) {
            runCatching { requestPosts("$BASE/post.json?tags=${enc(tags)}&limit=$limit&page=$page") }
        }

    /** 单条同样走 `tags=id:N`（`/post/{id}.json` 在这一族是 404）。 */
    suspend fun fetchById(id: Long): Result<GalleryPost?> = withContext(Dispatchers.IO) {
        runCatching {
            requestPosts("$BASE/post.json?tags=${enc("id:$id")}")
                .firstOrNull { it.id == id }
        }
    }

    suspend fun searchTags(term: String, limit: Int = GALLERY_TAG_SUGGESTION_LIMIT): Result<List<GalleryTagSuggestion>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = executeGet(
                    "$BASE/tag.json?name=${enc(term.trim() + "*")}&order=count&limit=$limit",
                    allowInteractiveBypass = false,
                )
                json.decodeFromString<List<KonachanTagDto>>(body).map { it.toSuggestion(GallerySite.KONACHAN_R18) }
            }
        }

    private suspend fun requestPosts(url: String): List<GalleryPost> {
        val body = DesktopKonachanBridge.fetchJson(url)
        return json.decodeFromString<List<KonachanPostDto>>(body).map { it.toPost(GallerySite.KONACHAN_R18) }
    }

    private fun executeGet(url: String, allowInteractiveBypass: Boolean): String {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .apply {
                if (!allowInteractiveBypass) {
                    tag(NoInteractiveBypassTag::class.java, NoInteractiveBypassTag())
                }
            }
            .build()
        try {
            engine.okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException("Konachan R18 返回 ${response.code}")
                }
                return body
            }
        } catch (e: SocketTimeoutException) {
            throw IOException("Konachan R18 请求超时，请检查代理节点或应用代理设置", e)
        } catch (e: InterruptedIOException) {
            throw IOException("Konachan R18 请求超时，请检查代理节点或应用代理设置", e)
        }
    }

    companion object {
        const val POOL_SIZE = 100

        private const val BASE = "https://konachan.com"

        private val json = Json { ignoreUnknownKeys = true }

        private fun enc(v: String): String = URLEncoder.encode(v, "UTF-8")

        @Volatile
        private var installedEngine: HttpEngine? = null

        @Volatile
        private var INSTANCE: DesktopKonachanComClient? = null

        fun installEngine(engine: HttpEngine) {
            installedEngine = engine
            INSTANCE = null
        }

        fun getInstance(engine: HttpEngine? = null): DesktopKonachanComClient {
            if (engine != null) return DesktopKonachanComClient(engine)
            val resolved = installedEngine
                ?: error("DesktopKonachanComClient 尚未绑定 HttpEngine")
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DesktopKonachanComClient(resolved).also { INSTANCE = it }
            }
        }
    }
}
