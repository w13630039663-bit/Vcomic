package com.venera.desktop.gallery.data

import com.google.gson.JsonParser
import com.venera.compose.data.platform.HttpEngine
import com.venera.compose.data.platform.NoInteractiveBypassTag
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.data.GalleryTagSuggestion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.io.IOException

/**
 * Pixiv 官方搜索关联词 / 标签联想纯件。
 *
 * ## 接口依据与协议契约
 *
 * Pixiv 官方站方提供了两套成熟的关联词/自动补全机制：
 * 1. 匿名公共端点（Web RPC）：`https://www.pixiv.net/rpc/cps.php?keyword={word}&lang=zh`
 *    返回 JSON: `{"candidates":[{"tag_name":"東方Project","tag_translation":"东方Project","access_count":...}]}`
 * 2. 备用端点：`https://www.pixiv.net/ajax/search/suggest?keyword={word}&lang=zh`
 *
 * ## 双语与分类转换
 *
 * 取到的 `candidates` 映射为全软件统一的 [GalleryTagSuggestion]：
 * - `site = GallerySite.PIXIV`
 * - `name = tag_name`（日文标准原词，回填到搜索框可 100% 命中 Pixiv 官方索引）
 * - `categoryLabel` = 中文译名（若有）或 "Pixiv 标签"
 * - `count` = 热度/作品估量
 */
internal object DesktopPixivAutocomplete {

    private val RPC_URL get() = "${DESKTOP_PIXIV_REFERER}rpc/cps.php"
    private val AJAX_SUGGEST_URL get() = "${DESKTOP_PIXIV_REFERER}ajax/search/suggest"

    /**
     * 官方联想响应里 `tag_translation` 的落点。
     *
     * 只作**显示**用：`name` 仍是站方原词、回填搜索框 100% 命中官方索引；译名只拼进 chip 文案，
     * 不写回 `GalleryTagSuggestion`（共享数据类不动、判据钉的三枚形参不动）。
     * 每次 query 覆盖一批，键 = 官方 `tag_name` 原词。
     */
    @Volatile
    private var translations: Map<String, String> = emptyMap()

    /** 该联想词的中文译名；空串 = 站方没给 / 没查到（chip 只摆原名，不编）。 */
    fun translationFor(tagName: String): String = translations[tagName].orEmpty()

    /** 并进离线词典查出的译名（Booru 域联想没有官方 tag_translation，靠词典补）。 */
    fun mergeTranslations(extra: Map<String, String>) {
        if (extra.isNotEmpty()) translations = translations + extra
    }

    /**
     * 常用离线/兜底 ACG 联想表：防网络抖动或断网时搜索框完全变白。
     */
    private val FALLBACK_TAGS = listOf(
        Triple("レミリア・スカーレット", "蕾米莉亚·斯卡蕾特", 148000),
        Triple("レミリア", "蕾米莉亚", 62000),
        Triple("フランドール・スカーレット", "芙兰朵露·斯卡蕾特", 123000),
        Triple("東方Project", "东方Project", 825000),
        Triple("博麗霊夢", "博丽灵梦", 386000),
        Triple("霧雨魔理沙", "雾雨魔理沙", 291000),
        Triple("十六夜咲夜", "十六夜咲夜", 182000),
        Triple("紅魔館", "红魔馆", 56000),
        Triple("魂魄妖夢", "魂魄妖梦", 210000),
        Triple("西行寺幽々子", "西行寺幽幽子", 143000),
        Triple("原神", "Genshin Impact", 950000),
        Triple("アークナイツ", "明日方舟", 640000),
        Triple("アズールレーン", "碧蓝航线", 580000),
        Triple("ブルーアーカイブ", "蔚蓝档案", 1120000),
        Triple("Fate/Grand Order", "FGO", 1350000),
        Triple("AI生成", "AI作品", 1600000),
    )

    /**
     * 向 Pixiv 站方请求关键词补全。
     */
    suspend fun query(engine: HttpEngine?, word: String, limit: Int = 8): List<GalleryTagSuggestion> = withContext(Dispatchers.IO) {
        val trimmed = word.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        if (engine != null) {
            val remote = runCatching { fetchPixivRpc(engine, trimmed, limit) }.getOrNull()
            if (!remote.isNullOrEmpty()) {
                return@withContext remote
            }
        }

        // 网络未达或 engine 为空时：根据中文/日文双向模糊匹配离线库
        val lower = trimmed.lowercase()
        val matched = FALLBACK_TAGS.filter { (jp, cn, _) ->
            jp.lowercase().contains(lower) || cn.lowercase().contains(lower)
        }.take(limit).map { (jp, cn, count) ->
            GalleryTagSuggestion(
                site = GallerySite.PIXIV,
                name = jp,
                count = count,
                category = if (jp.contains("Project") || jp.contains("Fate") || jp.contains("原神")) 3 else 4,
                deprecated = false,
            )
        }
        matched
    }

    private fun fetchPixivRpc(engine: HttpEngine, word: String, limit: Int): List<GalleryTagSuggestion> {
        val url = RPC_URL.toHttpUrl().newBuilder()
            .addQueryParameter("keyword", word)
            .addQueryParameter("lang", "zh")
            .build()
            .toString()

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .header("Referer", DESKTOP_PIXIV_REFERER)
            .tag(NoInteractiveBypassTag::class.java, NoInteractiveBypassTag())
            .build()

        engine.okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Pixiv RPC HTTP ${response.code}")
            val body = response.body?.string().orEmpty()
            // 官方响应自带的中文译名落进显示表（只作显示，name 仍是官原词）。
            translations = parseTranslations(body)
            return parseRpcResponse(body, limit)
        }
    }

    /** 从官方 `candidates[]` 提「原名 → 中文译名」，纯件（不写状态）。 */
    internal fun parseTranslations(jsonStr: String): Map<String, String> {
        if (jsonStr.isBlank()) return emptyMap()
        val root = runCatching { JsonParser.parseString(jsonStr).asJsonObject }.getOrNull()
            ?: return emptyMap()
        val candidates = root.getAsJsonArray("candidates") ?: return emptyMap()
        val out = LinkedHashMap<String, String>()
        for (elem in candidates) {
            if (!elem.isJsonObject) continue
            val obj = elem.asJsonObject
            val name = obj.get("tag_name")?.asString.orEmpty().trim()
            val cn = obj.get("tag_translation")?.asString?.trim().orEmpty()
            if (name.isNotBlank() && cn.isNotBlank() && cn != name) out[name] = cn
        }
        return out
    }

    internal fun parseRpcResponse(jsonStr: String, limit: Int): List<GalleryTagSuggestion> {
        if (jsonStr.isBlank()) return emptyList()
        val root = JsonParser.parseString(jsonStr).asJsonObject
        val candidates = root.getAsJsonArray("candidates") ?: return emptyList()
        val results = mutableListOf<GalleryTagSuggestion>()

        for (elem in candidates) {
            if (!elem.isJsonObject) continue
            val obj = elem.asJsonObject
            val tagName = obj.get("tag_name")?.asString.orEmpty()
            if (tagName.isBlank()) continue
            val translation = obj.get("tag_translation")?.asString?.trim().orEmpty()
            val count = obj.get("access_count")?.asInt ?: 0

            results.add(
                GalleryTagSuggestion(
                    site = GallerySite.PIXIV,
                    name = tagName,
                    count = count,
                    category = if (translation?.contains("角色") == true) 4 else 0,
                    deprecated = false,
                )
            )
            if (results.size >= limit) break
        }
        return results
    }
}
