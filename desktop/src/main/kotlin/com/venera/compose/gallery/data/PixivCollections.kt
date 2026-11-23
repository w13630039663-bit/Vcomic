package com.venera.compose.gallery.data

import java.io.IOException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * pixiv **珍藏册**（collection）那四条匿名腿的模型与形状解析。
 *
 * 端点全部是 `www.pixiv.net` 的 web-ajax（pixiv-web-next 那一侧），匿名可读：
 *
 * - `GET /ajax/top/collection` —— 发现页三节（推荐 / 大家的 / 标签推荐）；
 * - `GET /ajax/collection/{id}` —— 详情（卡 + tile 墙 + 作品投影）；
 * - `GET /ajax/collections/search` —— 搜索（⚠️ 键必须是重复的 `tags[]=`，单个 `tags=` 稳定 400）；
 * - `GET /ajax/collection/{id}/recommend/init` —— 相关珍藏册（只收第一页 `collections`，`nextIds` 丢弃）；
 * - `GET /ajax/collections/search/recommended_tags` —— 搜索框的推荐标签。
 *
 * ## 第一纪律：珍藏册 id 全链路 [String]
 *
 * 实测 id 是 **19–20 位数字串**（现量 `30328767231486195493` = 3.0e19、
 * `92197714148298580447` = 9.2e19），**超出 Long.MAX（9.223e18）**。Gson 读 Long 溢出
 * 直接抛 `JsonSyntaxException`，所以卡 / tile / 接口 / 路由 / 缓存键一律 String；
 * 只有 [PixivCollectionTile.workId]（作品号，8–9 位）与用户号落 Long ——
 * 且这两个在 JSON 里也是字符串形状（`workId` 实测 `"149260338"`），解析走宽容读。
 *
 * ## 解析为什么手写逐字段、不给 Gson DTO
 *
 * 与 `PixivFeedParse` 同一条理由：同一种意思在不同腿里类型相反（`id` 是 string、
 * `userId` 是 string、`bookmarkCount` 是 number、`workId` 是 string），DTO 押错一种形状
 * 就是静默解成默认值，屏上读起来像"这一站没有珍藏册"。口径也只有两条：
 * **整份答复读不懂 = 抛**；**站方给了 id 表却一张卡都对不上 = 抛**（站方改键的表现是
 * "整墙消失"，那与"真的没有"必须能区分开）。
 *
 * ## 卡实体键集（三处缩略卡现量对齐，21 键）
 *
 * `bookmarkCount / bookmarkData / caption / citedDataHash / commentOff / id / isBookmarkable /
 * isSpoiler / language / profileImageUrl / publishedDateTime / sl / status / tags /
 * thumbnailImageUrl / title / userId / userName / viewCount / visibilityScope / xRestrict`。
 * **没有 `type` 键**（"collection" 字样不在卡上），也没有多图封面键 —— 站方只给
 * `thumbnailImageUrl` 一张，[PixivCollectionCard.coverUrls] 由此投影，长度恒 1
 * （站方将来给拼贴多图再扩，不编地址）。
 *
 * ## 详情页 description 的唯一来源
 *
 * `body.data` 里实测只有 `{citations[], detail:{tags,tiles}, translation:null, userCollections[]}`
 * —— **没有独立描述字段**。描述块唯一来源 = 缩略卡 `caption`；空串就交空串，
 * UI 退「暂无描述」空态，不编文字。
 */
data class PixivCollectionCard(
    val id: String,
    val userId: Long,
    val userName: String,
    val profileImageUrl: String?,
    val title: String,
    val tags: List<String>,
    val caption: String,
    val thumbnailImageUrl: String?,
    /** 拼贴封面；站方今天只给 1 张（`thumbnailImageUrl`），列表长度恒 1。 */
    val coverUrls: List<String>,
    val bookmarkCount: Int,
    val status: String?,
    val publishedDateTime: String?,
)

/**
 * 详情页 tile 墙的一格。⚠️ 路径在 `body.data.detail.tiles`（**不是** `body.page`）；
 * `x/y` = `layout.position.x/y`，`w/h` = `layout.size.x/y`。
 * 只收 `type == "Work"`（外链/文本格 `quotePage` 未见真实实例，形状未验证 —— 非 Work 格丢弃如实退空）。
 */
data class PixivCollectionTile(
    val id: String,
    /** 作品号。JSON 里是字符串（实测 `"149260338"`），宽容读成 Long。 */
    val workId: Long,
    val workType: String,
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
)

data class PixivWorkCard(
    val id: Long,
    val title: String,
    val url: String?,
    val userId: Long,
    val userName: String,
    val pageCount: Int,
    val mediumUrl: String? = null,
    val largeUrl: String? = null,
    val profileImageUrl: String? = null,
    val width: Int = 0,
    val height: Int = 0,
)

/** 详情页右栏的一整份。[description] 唯一来源 = 卡上 `caption`（见类注释）。 */
data class PixivCollectionDetail(
    val card: PixivCollectionCard,
    val description: String,
    val tiles: List<PixivCollectionTile>,
    /** 按 tiles 序、经 `thumbnails.illust` 投影；缺图的 tile 跳过（实测 18 tile / 17 illust）。 */
    val works: List<PixivWorkCard>,
    /** 站方详情接口自带的创建者珍藏册（`body.data.userCollections`，匿名可读）。 */
    val creatorCollections: List<PixivCollectionCard> = emptyList(),
)

data class PixivTagCollectionGroup(
    val tag: String,
    val cards: List<PixivCollectionCard>,
)

/** 发现页三节。id 表在 `body.page` 三键，× `body.thumbnails.collection` 按 id 对齐。 */
data class PixivCollectionsTop(
    val recommend: List<PixivCollectionCard>,
    val everyone: List<PixivCollectionCard>,
    val tagRecommended: List<PixivTagCollectionGroup>,
)

/** 搜索一页（`body.data.{ids,total}` + `thumbnails.collection` 对齐）。 */
data class PixivCollectionsSearchPage(
    val cards: List<PixivCollectionCard>,
    val total: Int,
    /** 下一页 offset；`offset + cards.size >= total` 时为 null（没有下一页）。 */
    val nextOffset: Int?,
)

/**
 * 珍藏册四路取数 + 收藏动作的契约面。
 *
 * 匿名四路全真数据；**收藏三腿是 www web 会话腿**（与 OAuth Bearer 的 app-api 不是一回事，
 * 见实现类注释）：无会话/未登录一律 `failure`，消息念「需要 pixiv 网页登录」，不静默成功。
 */
interface GalleryPixivCollections {

    /** 发现页三节。[fresh] = 手动刷新，绕过盘缓存直接问站方（口径同 `GalleryPixivWorks.ranking`）。 */
    suspend fun top(fresh: Boolean = false): Result<PixivCollectionsTop>

    /** 搜索框的推荐标签（`/ajax/collections/search/recommended_tags`，匿名 200 已验）。 */
    suspend fun recommendedTags(): Result<List<String>>

    /** 详情：卡 + tile 墙 + 作品投影。 */
    suspend fun detail(id: String, fresh: Boolean = false): Result<PixivCollectionDetail>

    /**
     * 搜索。[mode] = `"all"|"safe"|"r18"`（前两档匿名 200 已验，r18 未单测）；
     * [tag] 空串 = 大家的那一览（⚠️ 空串时不发 `tags[]` 参数，该形状未验证）。
     */
    suspend fun search(tag: String, mode: String, limit: Int = 30, offset: Int = 0): Result<PixivCollectionsSearchPage>

    /**
     * 相关珍藏册。`/ajax/collection/{id}/recommend/init` 回 `{collections, nextIds}` ——
     * **只收第一页 collections，nextIds 丢弃**（相关珍藏册只取第一页，截断口径在此注明）。
     */
    suspend fun recommend(id: String, fresh: Boolean = false): Result<List<PixivCollectionCard>>

    /**
     * 当前用户对该珍藏册的收藏态。⚠️ `GET /ajax/collection/{id}/bookmarkData` 真实 id 匿名 404
     * （同一 id 的 detail / recommend/init 均 200）—— 端点存在性与带会话行为均未验证，
     * 失败路径如实报错，不假成功。
     *
     * 成功即**写穿**进 [bookmarkedIds]（true 加 / false 减）；匿名无会话时 failure，
     * 调用点对失败不念成因（回填腿静默，收藏动作腿才 toast）。
     */
    suspend fun bookmarkState(id: String): Result<Boolean>

    /** 收藏。[private0] = true 走私密档（restrict=1）。 */
    suspend fun addBookmark(id: String, private0: Boolean): Result<Unit>

    /** 取消收藏。 */
    suspend fun removeBookmark(id: String): Result<Unit>

    /**
     * 该用户**收藏的**珍藏册（语义钉死：不是 TA 创建的 —— 创建珍藏册无端点，实测
     * `/ajax/user/{id}/collections` 与 `/collections/all` 均 404）。登录腿，行为未验证。
     */
    suspend fun myBookmarked(userId: Long, limit: Int = 30, offset: Int = 0): Result<PixivCollectionsSearchPage>

    /**
     * 本机已确认收藏态；收藏动作与 [bookmarkState] 成功写穿，实现类**落盘**（重启不失）。
     * 站上册子本机没见过时不在集合里 —— 打开详情触发 [bookmarkState] 回填才是站方真态。
     */
    val bookmarkedIds: StateFlow<Set<String>>
}

/**
 * 五条腿的形状解析。所有取值宽容读（缺键退默认，不崩），只有"整份读不懂"与
 * "id 表全对不上"两种情况抛。
 */
object PixivCollectionsParse {

    private val json = Json { ignoreUnknownKeys = true }

    /** 报错消息里带多少原始答复：够认出是哪一种坏，又不至于把整份答复抄进日志。 */
    private const val SAMPLE = 120

    fun top(body: String): PixivCollectionsTop {
        val b = envelope(body, "pixiv 珍藏册发现页")
        val cardsById = cardTable(b)
        val page = b["page"] as? JsonObject
            ?: throw IOException("pixiv 珍藏册发现页答复里没有 page：${body.take(SAMPLE)}")

        fun ids(key: String): List<String> =
            (page[key] as? JsonArray).orEmpty().mapNotNull { it.textOrNull() }

        val recommendIds = ids("recommendCollectionIds")
        val everyoneIds = ids("everyoneCollectionIds")
        val groups = (page["tagRecommendCollectionIds"] as? JsonArray).orEmpty().mapNotNull { element ->
            val record = element as? JsonObject ?: return@mapNotNull null
            val tag = record["tag"].textOrNull() ?: return@mapNotNull null
            val cards = (record["ids"] as? JsonArray).orEmpty()
                .mapNotNull { it.textOrNull() }.mapNotNull { cardsById[it] }
            if (cards.isEmpty()) null else PixivTagCollectionGroup(tag, cards)
        }
        val recommend = recommendIds.mapNotNull { cardsById[it] }
        val everyone = everyoneIds.mapNotNull { cardsById[it] }
        // 「对不上的 id 丢弃」只是兜底（评审复验 115/115 全命中）；站方改键的表现是全表丢光，
        // 那与"真的没有"必须能区分 —— 有 id 却一张卡都不剩 = 抛。
        val recognized = recommend.size + everyone.size + groups.sumOf { it.cards.size }
        if (recommendIds.size + everyoneIds.size + groups.size > 0 && recognized == 0) {
            throw IOException("pixiv 珍藏册发现页 id 表一张卡都对不上：${body.take(SAMPLE)}")
        }
        return PixivCollectionsTop(recommend, everyone, groups)
    }

    fun detail(id: String, body: String): PixivCollectionDetail {
        val b = envelope(body, "pixiv 珍藏册详情")
        val card = (b["thumbnails"] as? JsonObject)?.get("collection").asArrayOrNull()
            ?.firstNotNullOfOrNull { it.asObjectOrNull()?.let(::cardOf) }
            ?: throw IOException("pixiv 珍藏册 $id 详情答复里没有卡：${body.take(SAMPLE)}")
        val illustById = ((b["thumbnails"] as? JsonObject)?.get("illust")).asArrayOrNull()
            .orEmpty().mapNotNull { element ->
                element.asObjectOrNull()?.let(::workCardOf)
            }.associateBy { it.id }
        // ⚠️ tiles 在 body.data.detail.tiles（不是 body.page）；非 Work 格丢弃如实退空。
        val tiles = ((((b["data"] as? JsonObject)?.get("detail")) as? JsonObject)?.get("tiles")).asArrayOrNull()
            .orEmpty().mapNotNull { element ->
                val record = element.asObjectOrNull() ?: return@mapNotNull null
                if (record["type"].textOrNull() != "Work") return@mapNotNull null
                val layout = record["layout"].asObjectOrNull() ?: return@mapNotNull null
                val position = layout["position"].asObjectOrNull() ?: return@mapNotNull null
                val size = layout["size"].asObjectOrNull() ?: return@mapNotNull null
                val workId = record["workId"].numberOrStringOrNull()?.toLongOrNull() ?: return@mapNotNull null
                PixivCollectionTile(
                    id = record["id"].textOrNull().orEmpty(),
                    workId = workId,
                    workType = record["workType"].textOrNull().orEmpty(),
                    x = position["x"].numberOrStringOrNull()?.toIntOrNull() ?: 0,
                    y = position["y"].numberOrStringOrNull()?.toIntOrNull() ?: 0,
                    w = size["x"].numberOrStringOrNull()?.toIntOrNull() ?: 0,
                    h = size["y"].numberOrStringOrNull()?.toIntOrNull() ?: 0,
                )
            }
        val works = tiles.mapNotNull { tile -> illustById[tile.workId] }
        val creatorCollections = ((b["data"] as? JsonObject)?.get("userCollections") as? JsonObject)
            ?.values
            .orEmpty()
            .mapNotNull { it.asObjectOrNull()?.let(::cardOf) }
            .filter { it.id != id }
        return PixivCollectionDetail(card, card.caption, tiles, works, creatorCollections)
    }

    fun search(body: String, offset: Int): PixivCollectionsSearchPage {
        val b = envelope(body, "pixiv 珍藏册搜索")
        val data = b["data"] as? JsonObject
            ?: throw IOException("pixiv 珍藏册搜索答复里没有 data：${body.take(SAMPLE)}")
        val ids = (data["ids"] as? JsonArray).orEmpty().mapNotNull { it.textOrNull() }
        val total = data["total"].numberOrStringOrNull()?.toIntOrNull() ?: 0
        val cardsById = cardTable(b)
        val cards = ids.mapNotNull { cardsById[it] }
        if (ids.isNotEmpty() && cards.isEmpty()) {
            throw IOException("pixiv 珍藏册搜索 id 表一张卡都对不上：${body.take(SAMPLE)}")
        }
        val next = offset + cards.size
        return PixivCollectionsSearchPage(cards, total, if (next < total && cards.isNotEmpty()) next else null)
    }

    /** `/ajax/collection/{id}/recommend/init` 的 `body.collections`（nextIds 丢弃：只取第一页）。 */
    fun recommend(body: String): List<PixivCollectionCard> {
        val b = envelope(body, "pixiv 相关珍藏册")
        return (b["collections"] as? JsonArray).orEmpty().mapNotNull { it.asObjectOrNull()?.let(::cardOf) }
    }

    /** `/ajax/collections/search/recommended_tags` 的 `body.recommendedTags[]`。 */
    fun recommendedTags(body: String): List<String> {
        val b = envelope(body, "pixiv 珍藏册推荐标签")
        return (b["recommendedTags"] as? JsonArray).orEmpty().mapNotNull { it.textOrNull() }
    }

    /**
     * `GET /ajax/user/{id}/collections/bookmarks`（登录腿）。⚠️ 匿名 400、带会话行为未验证 ——
     * 形状按两种已知的页形宽容读：搜索形（`data.{ids,total}` + `thumbnails.collection`）或
     * 卡数组形（`collections[]`）；两种都认不出 = 抛，不编。
     */
    fun userBookmarks(body: String, offset: Int): PixivCollectionsSearchPage = runCatching {
        search(body, offset)
    }.getOrElse { searchFailure ->
        runCatching {
            val b = envelope(body, "pixiv 用户收藏的珍藏册")
            val arr = b["collections"] as? JsonArray
                ?: throw IOException("pixiv 用户收藏的珍藏册答复读不出卡：${body.take(SAMPLE)}")
            val cards = arr.mapNotNull { it.asObjectOrNull()?.let(::cardOf) }
            if (arr.isNotEmpty() && cards.isEmpty()) {
                throw IOException("pixiv 用户收藏的珍藏册答复读不出卡：${body.take(SAMPLE)}")
            }
            PixivCollectionsSearchPage(cards, cards.size, null)
        }.getOrElse { throw searchFailure }
    }

    // ── 公共件 ──

    /** ajax 信封：`error=true` 抛站方的话；`body` 不是对象抛。 */
    private fun envelope(body: String, who: String): JsonObject {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }
            .getOrElse { throw IOException("$who 答复不是 JSON：${body.take(SAMPLE)}") }
        if (root["error"]?.textOrNull()?.lowercase() == "true") {
            throw IOException("$who 站方回 error：${root["message"]?.textOrNull().orEmpty().ifBlank { body.take(SAMPLE) }}")
        }
        return root["body"].asObjectOrNull()
            ?: throw IOException("$who 答复里没有 body：${body.take(SAMPLE)}")
    }

    /** `body.thumbnails.collection` → 按 id 索引的卡表（读不出的条目跳过）。 */
    private fun cardTable(b: JsonObject): Map<String, PixivCollectionCard> =
        (b["thumbnails"] as? JsonObject)?.get("collection").asArrayOrNull()
            .orEmpty().mapNotNull { element -> element.asObjectOrNull()?.let(::cardOf) }
            .associateBy { it.id }

    private fun cardOf(record: JsonObject): PixivCollectionCard? {
        val id = record["id"].textOrNull() ?: return null
        val thumbnail = normalizeCollectionThumbnailUrl(record["thumbnailImageUrl"].textOrNull())
        return PixivCollectionCard(
            id = id,
            userId = record["userId"].numberOrStringOrNull()?.toLongOrNull() ?: 0L,
            userName = record["userName"].textOrNull().orEmpty(),
            profileImageUrl = record["profileImageUrl"].textOrNull(),
            title = record["title"].textOrNull().orEmpty(),
            tags = record["tags"].asArrayOrNull().orEmpty().mapNotNull { it.textOrNull() },
            caption = record["caption"].textOrNull().orEmpty(),
            thumbnailImageUrl = thumbnail,
            coverUrls = listOfNotNull(thumbnail),
            bookmarkCount = record["bookmarkCount"].numberOrStringOrNull()?.toIntOrNull() ?: 0,
            status = record["status"].textOrNull(),
            publishedDateTime = record["publishedDateTime"].textOrNull(),
        )
    }

    private fun workCardOf(record: JsonObject): PixivWorkCard? {
        val id = record["id"].numberOrStringOrNull()?.toLongOrNull() ?: return null
        val url250 = record["url"].textOrNull()
        val urlsObj = record["urls"].asObjectOrNull()
        val url540 = urlsObj?.get("540x540").textOrNull() ?: urlsObj?.get("360x360").textOrNull()
        val url1200 = urlsObj?.get("1200x1200").textOrNull()
        return PixivWorkCard(
            id = id,
            title = record["title"].textOrNull().orEmpty(),
            url = url250,
            userId = record["userId"].numberOrStringOrNull()?.toLongOrNull() ?: 0L,
            userName = record["userName"].textOrNull().orEmpty(),
            pageCount = record["pageCount"].numberOrStringOrNull()?.toIntOrNull() ?: 0,
            mediumUrl = url540 ?: url250,
            largeUrl = url1200 ?: url540 ?: url250,
            profileImageUrl = record["profileImageUrl"].textOrNull(),
            width = record["width"].numberOrStringOrNull()?.toIntOrNull() ?: 0,
            height = record["height"].numberOrStringOrNull()?.toIntOrNull() ?: 0,
        )
    }

    /** 站方缩略图地址缺少 format 参数时会返回 400，补齐 format=png。 */
    private fun normalizeCollectionThumbnailUrl(url: String?): String? {
        val raw = url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val path = raw.substringBefore('?')
        if (!raw.contains("embed.pixiv.net/", ignoreCase = true) || !path.endsWith("/thumbnail")) {
            return raw
        }
        val query = raw.substringAfter('?', missingDelimiterValue = "")
        if (query.split('&').any { it.startsWith("format=", ignoreCase = true) }) {
            return raw
        }
        val sep = if ('?' in raw) '&' else '?'
        return "${raw}${sep}format=png"
    }

    private fun JsonElement?.asObjectOrNull(): JsonObject? = this as? JsonObject

    private fun JsonElement?.asArrayOrNull(): JsonArray? = this as? JsonArray

    private fun JsonElement?.textOrNull(): String? =
        (this as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    /** 数字与字符串两种形状都吃（同一件事在两腿里类型相反，见 `PixivFeedParse` 那张表）。 */
    private fun JsonElement?.numberOrStringOrNull(): String? = textOrNull()
}
