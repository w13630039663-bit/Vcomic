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
 * TBIB 的读侧客户端（Gelbooru 系 DAPI，匿名可用）。
 *
 * 与 Gelbooru 的三处差异：`s=post&json=1` 回**裸数组**而非信封；不给现成图片地址，只能由
 * `directory` + `image` 拼（前缀取 image 词干，取 hash 会 404）；`s=tag` 回 XML。分页 `pid` 0 基。
 */
class DesktopTbibClient internal constructor(private val engine: HttpEngine) {

    suspend fun searchPosts(tags: String, page: Int, limit: Int = POOL_SIZE): Result<List<GalleryPost>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val pid = (page - 1).coerceAtLeast(0)
                requestPosts(
                    "$BASE?page=dapi&s=post&q=index&json=1&tags=${enc(tags)}&limit=$limit&pid=$pid"
                )
            }
        }

    suspend fun fetchById(id: Long): Result<GalleryPost?> = withContext(Dispatchers.IO) {
        runCatching {
            requestPosts("$BASE?page=dapi&s=post&q=index&json=1&tags=${enc("id:$id")}")
                .firstOrNull { it.id == id }
        }
    }

    suspend fun fetchTopScored(): Result<List<GalleryPost>> = withContext(Dispatchers.IO) {
        runCatching {
            val list = requestPosts(
                "$BASE?page=dapi&s=post&q=index&json=1&tags=${enc("sort:score:desc")}&limit=$POOL_SIZE&pid=0"
            )
            if (list.isEmpty()) throw IOException("TBIB 的高分池返回 0 条")
            list
        }
    }

    suspend fun searchTags(term: String, limit: Int = GALLERY_TAG_SUGGESTION_LIMIT): Result<List<GalleryTagSuggestion>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val pattern = enc(term.trim() + "%")
                val url = "$BASE?page=dapi&s=tag&q=index&name_pattern=$pattern&orderby=count&limit=$limit"
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/xml")
                    .tag(NoInteractiveBypassTag::class.java, NoInteractiveBypassTag())
                    .build()
                engine.okHttpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) throw IOException("TBIB 返回 ${response.code}")
                    TAG_PATTERN.findAll(body).map { m ->
                        GalleryTagSuggestion(
                            site = GallerySite.TBIB,
                            name = decodeXml(m.groupValues[1]),
                            count = m.groupValues[2].toIntOrNull() ?: 0,
                            category = m.groupValues[3].toIntOrNull() ?: 0,
                            deprecated = m.groupValues[4] == "true",
                        )
                    }.toList()
                }
            }
        }

    private fun requestPosts(url: String): List<GalleryPost> {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .tag(NoInteractiveBypassTag::class.java, NoInteractiveBypassTag())
            .build()
        engine.okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("TBIB 返回 ${response.code}")
            return decodeTbibPosts(body)
        }
    }

    companion object {
        const val POOL_SIZE = 100

        private const val BASE = "https://tbib.org/index.php"

        /** 站方 XML 的属性顺序不保证，逐枚按属性名抓更稳。 */
        private val TAG_PATTERN = Regex(
            """<tag\b(?=[^>]*\bname="([^"]*)")(?=[^>]*\bcount="(\d+)")""" +
                """(?=[^>]*\btype="(\d+)")(?=[^>]*\bambiguous="(true|false)")[^>]*/>"""
        )

        private fun enc(v: String): String = URLEncoder.encode(v, "UTF-8")

        private fun decodeXml(raw: String): String = raw
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&apos;", "'")

        @Volatile
        private var INSTANCE: DesktopTbibClient? = null

        fun getInstance(engine: HttpEngine): DesktopTbibClient = INSTANCE ?: synchronized(this) {
            INSTANCE ?: DesktopTbibClient(engine).also { INSTANCE = it }
        }
    }
}

/** 裸数组解成卡片。单独抽出是为了让判据能离线真跑这条解码路。 */
internal fun decodeTbibPosts(body: String): List<GalleryPost> =
    TBIB_JSON.decodeFromString<List<TbibPostDto>>(body).mapNotNull { it.toPost() }

private val TBIB_JSON = Json { ignoreUnknownKeys = true }

/** TBIB 的一条 post 的站方原始字段名（**没有**任何现成 URL 字段）。 */
@Serializable
private data class TbibPostDto(
    val id: Long,
    val directory: Int = 0,
    val image: String = "",
    val hash: String = "",
    val height: Int = 0,
    val width: Int = 0,
    val rating: String = "",
    val score: Int? = null,
    val tags: String = "",
    val owner: String = "",
    val change: Long = 0,
    val sample: Boolean = false,
    @SerialName("sample_width") val sampleWidth: Int = 0,
    @SerialName("sample_height") val sampleHeight: Int = 0,
    @SerialName("parent_id") val parentId: Long = 0,
) {
    fun toPost(): GalleryPost? {
        if (id <= 0L || image.isBlank() || directory <= 0) return null
        val stem = image.substringBeforeLast('.')
        val file = "$FILE_BASE/images/$directory/$image"
        val thumb = "$FILE_BASE/thumbnails/$directory/thumbnail_$stem.jpg"
        val sampled = "$FILE_BASE/samples/$directory/sample_$stem.jpg"
        val ext = image.substringAfterLast('.', "").lowercase()
        return GalleryPost(
            site = GallerySite.TBIB,
            id = id,
            tags = tags,
            rating = rating,
            score = score ?: 0,
            favCount = null,
            author = owner,
            source = "",
            width = width,
            height = height,
            previewUrl = thumb,
            // 站方不给缩略尺寸，sample 那一对是**样本**尺寸，不能拿来当预览比例；
            // 交原图比例由瀑布流按容器算（与 safebooru 缺档时同一口径）。
            previewWidth = 0,
            previewHeight = 0,
            fastUrl = thumb,
            largeUrl = if (sample) sampled else file,
            largeWidth = if (sample) sampleWidth else width,
            largeHeight = if (sample) sampleHeight else height,
            fileUrl = file,
            fileSize = 0,
            md5 = hash,
            fileExt = ext,
        )
    }

    /** 不能写成 `private companion`：inline reified 解码会跨类读 `Companion` 字段，private 则抛 IllegalAccessError。 */
    companion object {
        private const val FILE_BASE = "https://tbib.org"
    }
}
