package com.venera.desktop.gallery.data

import com.venera.compose.data.platform.HttpEngine
import com.venera.compose.data.platform.NoInteractiveBypassTag
import com.venera.compose.gallery.data.GALLERY_TAG_SUGGESTION_LIMIT
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.data.GalleryTagSuggestion
import java.io.IOException
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Request

/**
 * Konachan 的读侧客户端（Moebooru，接口与 yande.re 同形，匿名直连）。
 *
 * [GalleryPost.site] 必须写 [GallerySite.KONACHAN]：两站 id 各自编号，同号是两张不同的图，
 * 串站会让收藏与缓存键静默交错。这一站没有日榜端点，也不接画师别名/外链。
 */
class DesktopKonachanClient internal constructor(private val engine: HttpEngine) {

    suspend fun searchPosts(
        tags: String,
        page: Int,
        limit: Int = POOL_SIZE,
        allowInteractiveBypass: Boolean = true,
    ): Result<List<GalleryPost>> =
        withContext(Dispatchers.IO) {
            runCatching {
                requestList(
                    "$BASE/post.json?tags=${enc(tags)}&limit=$limit&page=$page",
                    allowInteractiveBypass = allowInteractiveBypass,
                )
            }
        }

    /** 单条同样走 `tags=id:N`（`/post/{id}.json` 在这一族是 404）。 */
    suspend fun fetchById(id: Long): Result<GalleryPost?> = withContext(Dispatchers.IO) {
        runCatching { requestList("$BASE/post.json?tags=${enc("id:$id")}").firstOrNull { it.id == id } }
    }

    suspend fun searchTags(term: String, limit: Int = GALLERY_TAG_SUGGESTION_LIMIT): Result<List<GalleryTagSuggestion>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("$BASE/tag.json?name=${enc(term.trim() + "*")}&order=count&limit=$limit")
                    .header("Accept", "application/json")
                    .tag(NoInteractiveBypassTag::class.java, NoInteractiveBypassTag())
                    .build()
                engine.okHttpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) throw IOException("Konachan 返回 ${response.code}")
                    json.decodeFromString<List<KonachanTagDto>>(body).map { it.toSuggestion(GallerySite.KONACHAN) }
                }
            }
        }

    private suspend fun requestList(url: String, allowInteractiveBypass: Boolean = true): List<GalleryPost> {
        if (allowInteractiveBypass) {
            val body = DesktopKonachanBridge.fetchJson(url)
            return json.decodeFromString<List<KonachanPostDto>>(body).map { it.toPost(GallerySite.KONACHAN) }
        }
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .tag(NoInteractiveBypassTag::class.java, NoInteractiveBypassTag())
            .build()
        engine.okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("Konachan 返回 ${response.code}")
            return json.decodeFromString<List<KonachanPostDto>>(body).map { it.toPost(GallerySite.KONACHAN) }
        }
    }

    companion object {
        const val POOL_SIZE = 100

        private const val BASE = "https://konachan.net"

        private val json = Json { ignoreUnknownKeys = true }

        private fun enc(v: String): String = URLEncoder.encode(v, "UTF-8")

        @Volatile
        private var INSTANCE: DesktopKonachanClient? = null

        fun getInstance(engine: HttpEngine): DesktopKonachanClient = INSTANCE ?: synchronized(this) {
            INSTANCE ?: DesktopKonachanClient(engine).also { INSTANCE = it }
        }
    }
}

/** Moebooru 的一条 post：字段名与 yande.re 逐字同形，`.net`/`.com` 两颗客户端共用（站别参数化）。 */
@Serializable
internal data class KonachanPostDto(
    val id: Long,
    val tags: String = "",
    val rating: String = "",
    val score: Int = 0,
    val author: String = "",
    val source: String = "",
    @SerialName("file_ext") val fileExt: String = "",
    val width: Int = 0,
    val height: Int = 0,
    @SerialName("preview_url") val previewUrl: String = "",
    @SerialName("actual_preview_width") val actualPreviewWidth: Int = 0,
    @SerialName("actual_preview_height") val actualPreviewHeight: Int = 0,
    @SerialName("sample_url") val sampleUrl: String = "",
    @SerialName("sample_width") val sampleWidth: Int = 0,
    @SerialName("sample_height") val sampleHeight: Int = 0,
    @SerialName("jpeg_url") val jpegUrl: String = "",
    @SerialName("jpeg_width") val jpegWidth: Int = 0,
    @SerialName("jpeg_height") val jpegHeight: Int = 0,
    @SerialName("file_url") val fileUrl: String = "",
    @SerialName("file_size") val fileSize: Long = 0,
    val md5: String = "",
) {
    fun toPost(site: GallerySite): GalleryPost = GalleryPost(
        site = site,
        id = id,
        tags = tags,
        rating = rating,
        score = score,
        favCount = null,
        author = author,
        source = source,
        width = width,
        height = height,
        previewUrl = previewUrl,
        previewWidth = actualPreviewWidth,
        previewHeight = actualPreviewHeight,
        fastUrl = previewUrl,
        // 中档取 sample_url；空时退到同一张画的 jpeg 重编码（与 yande.re 那一支同口径）。
        largeUrl = sampleUrl.ifBlank { jpegUrl },
        largeWidth = if (sampleUrl.isNotBlank()) sampleWidth else jpegWidth,
        largeHeight = if (sampleUrl.isNotBlank()) sampleHeight else jpegHeight,
        fileUrl = fileUrl,
        fileSize = fileSize,
        md5 = md5,
        fileExt = fileExt,
    )
}

@Serializable
internal data class KonachanTagDto(
    val name: String = "",
    val count: Int = 0,
    val type: Int = 0,
    val ambiguous: Boolean = false,
) {
    fun toSuggestion(site: GallerySite): GalleryTagSuggestion =
        GalleryTagSuggestion(site, name, count, type, deprecated = ambiguous)
}
