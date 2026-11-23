package com.venera.desktop.gallery.data

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.venera.compose.data.platform.NoInteractiveBypassTag
import com.venera.desktop.gallery.ui.DesktopHotTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Pixiv 官方热门标签 (Trending Tags) 真实数据源服务。
 *
 * 彻底消除本地伪造的计数标签，直连 Pixiv 官方 `/v1/trending-tags/illust?filter=for_android` 接口，
 * 携带官方标签代表作 (illust) 缩略图，自带内存与多级回退兜底。
 */
internal object DesktopPixivTrending {

    private val gson = Gson()

    @Volatile
    private var memoryCache: List<DesktopHotTag>? = null

    private val defaultClient by lazy { OkHttpClient.Builder().build() }

    /**
     * 官方标签代表作封面为空时的**多级回退**：只在同一颗官方 illust 的 image_urls 里挑，
     * 不拿当轮墙卡替站方编图（人气标签必须吃 Pixiv 官方图片）。
     * 全空 = 卡退纯面板底，如实不摆图。
     */
    private fun officialCover(imageMap: JsonObject?): String =
        imageMap?.get("square_medium")?.asString
            ?: imageMap?.get("medium")?.asString
            ?: imageMap?.get("large")?.asString
            ?: imageMap?.get("original")?.asString
            ?: ""

    /**
     * @param coverForTag 撞图/无图标签的**补图器**：传入官方标签名，回一张该标签自己的
     *   Pixiv 官方作品封面（由壳用 `ports.pixiv.searchIllust` 接）。空串 = 搜不到，卡退纯底。
     *   数据件不认端口，保持纯；不拿主墙卡补图、不编图。
     */
    suspend fun fetchTrendingTags(
        client: OkHttpClient? = null,
        bearer: String? = null,
        force: Boolean = false,
        coverForTag: (suspend (String) -> String)? = null,
    ): List<DesktopHotTag> = withContext(Dispatchers.IO) {
        val cached = memoryCache
        if (!force && cached != null && cached.isNotEmpty()) return@withContext cached

        val http = client ?: defaultClient
        val url = "https://app-api.pixiv.net/v1/trending-tags/illust?filter=for_android"
        val requestBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", "PixivAndroidApp/5.0.234 (Android 11; Pixel 5)")
            .header("Accept-Language", "zh-cn")
            .tag(NoInteractiveBypassTag::class.java, NoInteractiveBypassTag())

        if (!bearer.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $bearer")
        }

        try {
            http.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string().orEmpty()
                val parsed = parseTrendingBody(body).toMutableList()
                // 主人再报「还是太多重复图」：上一版撞图就置空，只把重复图
                // 变成一排空卡。这里改成给撞图/无图的标签**用该标签名搜一张自己的官方作品图**
                // （仍走 Pixiv 官方，不编图、不复用主墙）。搜不到才留空串，卡退纯底。
                if (coverForTag != null) {
                    val used = parsed.map { it.coverUrl }.filter { it.isNotBlank() }.toMutableSet()
                    for (i in parsed.indices) {
                        val tag = parsed[i]
                        if (tag.coverUrl.isNotBlank()) continue
                        val fallback = runCatching {
                            coverForTag(tag.name.substringBefore(" (").trim())
                        }.getOrDefault("")
                        if (fallback.isNotBlank() && used.add(fallback)) {
                            parsed[i] = tag.copy(coverUrl = fallback)
                        }
                    }
                }
                // 无网 / 站方改版 = 空表，那一节如实不摆；绝不回落到本地伪造标签。
                if (parsed.isNotEmpty()) memoryCache = parsed
                parsed
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun parseTrendingBody(body: String): List<DesktopHotTag> {
        val root = try {
            gson.fromJson(body, JsonObject::class.java)
        } catch (_: Throwable) {
            return emptyList()
        }
        val trendArray = root.getAsJsonArray("trend_tags") ?: return emptyList()
        val result = mutableListOf<DesktopHotTag>()
        // 按封面 URL 去重，仅保留排名最前标签的封面，后续撞图标签置空 coverUrl 退回纯面板底。
        val seenCovers = mutableSetOf<String>()

        trendArray.forEachIndexed { index, el ->
            val obj = el.asJsonObject ?: return@forEachIndexed
            val tag = obj.get("tag")?.asString?.trim().orEmpty()
            if (tag.isEmpty()) return@forEachIndexed

            val trans = obj.get("translated_name")?.asString?.trim().orEmpty()
            val displayName = if (trans.isNotEmpty() && trans != tag) "$tag ($trans)" else tag

            val illust = obj.getAsJsonObject("illust")
            val rawCover = officialCover(illust?.getAsJsonObject("image_urls"))
            val cover = if (rawCover.isNotBlank() && seenCovers.add(rawCover)) rawCover else ""

            // count 只作官方数组顺序的排序权重，界面不显示 —— 不替站方编热度数字。
            result += DesktopHotTag(name = displayName, count = trendArray.size() - index, coverUrl = cover)
        }
        return result
    }
}
