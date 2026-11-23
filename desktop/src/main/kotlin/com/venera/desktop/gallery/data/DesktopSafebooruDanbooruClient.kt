package com.venera.desktop.gallery.data

import com.venera.compose.data.platform.HttpEngine
import com.venera.compose.data.platform.NoInteractiveBypassTag
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.SafebooruClient
import com.venera.compose.gallery.data.SafebooruPostDto
import java.io.IOException
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Request

/**
 * Safebooru 的读侧桌面代换：**posts 三腿走 danbooru 主站**。
 *
 * ## 为什么换 host
 *
 * `safebooru.donmai.us` 整域挂 CF（主页与 `posts.json` 经常 403，
 * 间歇性放行），而母站 `danbooru.donmai.us` 匿名直连全端点 200（实测 posts/order:rank/
 * 多标签均通）。Safebooru 本就是 danbooru 的全年龄镜像（同库同 id 同 schema），DTO
 * 逐字复用共享面那一份——查询串补 `rating:general` 锁住与原站等价的全年龄内容。
 *
 * 标签联想**不换**：danbooru 匿名 `tags.json` 的 name_matches 通配回空（探针实测），
 * 联想仍走 safebooru 原站——它间歇 403 时联想腿缺席即可，无害。
 *
 * [GalleryPost.site] 恒为 SAFEBOORU：同库同 id，收藏/缓存键与原站完全兼容。
 */
internal class DesktopSafebooruDanbooruClient private constructor(private val engine: HttpEngine) {

    suspend fun searchPosts(tags: String, page: Int, limit: Int = POOL_SIZE): Result<List<GalleryPost>> =
        withContext(Dispatchers.IO) {
            runCatching { requestList("$BASE/posts.json?tags=${enc(safeTags(tags))}&limit=$limit&page=$page") }
        }

    /** 单条同样走 `tags=id:N`（danbooru 的 `/posts/{id}.json` 要会员）。 */
    suspend fun fetchById(id: Long): Result<GalleryPost?> = withContext(Dispatchers.IO) {
        runCatching { requestList("$BASE/posts.json?tags=${enc("id:$id")}").firstOrNull { it.id == id } }
    }

    suspend fun fetchTopScored(): Result<List<GalleryPost>> = withContext(Dispatchers.IO) {
        runCatching { requestList("$BASE/posts.json?tags=${enc(safeTags("order:rank"))}&limit=$POOL_SIZE&page=1") }
    }

    private fun safeTags(tags: String): String =
        if (tags.contains("rating:")) tags.trim() else "${tags.trim()} rating:general".trim()

    private fun requestList(url: String): List<GalleryPost> {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .tag(NoInteractiveBypassTag::class.java, NoInteractiveBypassTag())
            .build()
        engine.okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("Safebooru(danbooru) 返回 ${response.code}")
            return json.decodeFromString<List<SafebooruPostDto>>(body).map { it.toPost() }
        }
    }

    companion object {
        const val POOL_SIZE = 100

        private const val BASE = "https://danbooru.donmai.us"

        private val json = Json { ignoreUnknownKeys = true }

        private fun enc(v: String): String = URLEncoder.encode(v, "UTF-8")

        @Volatile
        private var INSTANCE: DesktopSafebooruDanbooruClient? = null

        fun getInstance(engine: HttpEngine): DesktopSafebooruDanbooruClient = INSTANCE ?: synchronized(this) {
            INSTANCE ?: DesktopSafebooruDanbooruClient(engine).also { INSTANCE = it }
        }
    }
}
