package com.venera.engineprobe

/**
 * `comic.loadInfo` 那一坨答复读回来的四样东西。
 *
 * 为什么不做成 `Pair<String, List<Episode>>`（它从前就是那一形）：详情那一屏要摆**简介**，
 * 而简介就在同一份 `data` 里 —— 再发一次 `loadInfo` 只为补那一栏等于把同一次 JS 往返跑两遍，
 * 而"两遍之间源换了答复"是量不出来的那种错。
 *
 * [cover] 那一栏是批 82.6 补的：桌面从前没读它，于是写进 `comic_history` 的封面恒为空，
 * 而那张表两端共用（键名与"缺栏算空串"的口径都逐字照 Android 侧 `JsComicSource.getComicDetails`，
 * 判据钉着那一处，桌面不许自己多认一个候选键）。
 */
data class ComicInfo(
    val title: String,
    val description: String,
    val episodes: List<EngineSession.Episode>,
    val cover: String,
    /**
     * 评论那一跳（`comic.loadComments(comicId, subId, page, replyTo)`）要的**第二枚号**。
     *
     * 为什么要从 `loadInfo` 一起读回来（而不是进评论屏再问一次）：拷贝漫画那类源的真查询参数用的是
     * `subId` 而不是 `comicId`（`copy_manga.js` 里 `loadComments` 拼的 `comic_id=` 那一格取自 `subId`），
     * 而 `subId` 只活在 `loadInfo` 那次往返的答复里。逐字对齐 Android 侧 `JsComicSource.getComicDetails`
     * （`:597` 那句 `subId = res["subId"]?.toString()`），缺栏交回 null 而不是猜一串。
     */
    val subId: String? = null,
    /**
     * 源在详情里报的评论条数（对岸 `:595` 同键 `commentCount`）。`null` = 源没报这一栏 ⇒ 屏上那枚
     * 入口**不摆数**（对岸 `PixivWorkDetail` 那一族口径：给不出就宁可不念，不拿 0 冒充「没有评论」）。
     */
    val commentCount: Int? = null,
    // ── ：详情页信息区缺的那几栏，键名/缺栏兜底逐条照对岸 ──
    /**
     * 作者（对岸 `:466`：`author ?: uploader ?: ""` —— 两键一序，桌面不另认第三个候选）。
     * 空串 = 源没报 ⇒ 屏上那一行**不摆**（摆一行「作者：（空）」比没有更坏）。
     */
    val author: String = "",
    /** 上传者（对岸 `:586`：`uploader ?: author` —— 缺 uploader 栏时作者顶位，同一序）。 */
    val uploader: String = "",
    /**
     * 标签（对岸 `:468-488`：`tags` 是 List 直接取；是 Map 摊平各组分组的值；其余形状 = 空表）。
     * 分组名（`tagMap`）桌面不单独存：桌面详情屏没有"按分组摆标签"那一形，摊平后的顺序 = 分组序。
     */
    val tags: List<String> = emptyList(),
    /** 副题（对岸 `:463`：`subTitle ?: subtitle ?: ""`，大小写两键都认，同一序）。 */
    val subTitle: String = "",
    /**
     * 评分（对岸 `:580` 的 `stars` 栏：`(res["stars"] as? Number)?.toFloat() ?: 0f`）。
     * 桌面用**可空**而不是 `?: 0f`：0 分与"源没报评分"在屏上必须是两句话 ——
     * 把没报念成 0 分是「不拿 0 冒充」那一条（commentCount 同族）。
     */
    val stars: Float? = null,
    /** 点赞数（对岸 `:592`：`likesCount` 栏；对岸卡片线还认 `likes` 候选，详情线只认这一键 —— 桌面照详情线）。 */
    val likesCount: Int? = null,
    /** 源站自己报的"这本书已在收藏夹"（对岸 `:594`：`isFavorite == true`）。桌面收藏态吃**本地库**，这一栏只作对照读数。 */
    val sourceFavorite: Boolean = false,
    /** 详情给的推荐列表（对岸 `:546-552`：每项 {id,title,cover}；桌面本轮只搬 id+题名，点推荐开推荐是下一格）。 */
    val recommend: List<ComicRecommend> = emptyList(),
    /** 图源给的缩略图列表（对岸 `:555`：`thumbnails`）。空表 = 源没报。 */
    val thumbnails: List<String> = emptyList(),
    /**
     * 标签**按组**那份账（「标签与分类」卡）。桌面此前只留摊平值
     * （[tags]），组名扔掉了 —— 截图里「标签: / 原作: / 出演:」那种分组就是这一栏。
     * 源按 List 报（没有组）时这一栏为空表，屏上退回摊平那串；两栏都空 = 源没报标签。
     */
    val tagGroups: List<Pair<String, List<String>>> = emptyList(),
    /** 更新时间（对岸 `:588`：`updateTime ?: ""`）。 */
    val updateTime: String = "",
    /** 详情给的网站链接（对岸 `:589`：`url ?: ""`）。「在浏览器打开」那枚把手吃它，空串 ⇒ 把手不摆。 */
    val website: String = "",
)

/**
 * 详情推荐的一位（对岸 `:546-552` 的 `{id,title,cover}` 三元）。
 *
 * ⚠️ 这一形留在**桌面自己**这里而不是塞进 `EngineSession`：它是详情的附属，不是探索的卡。
 */
data class ComicRecommend(val id: String, val title: String, val cover: String)

/**
 * 标签**按组**那份账：Map 形状的 `tags` 照组收（组名原样，不翻译不编名）；
 * List 形状没有组，交回空表（屏上退回摊平那串）。与 [parseComicLoadInfo] 的摊平分支
 * 同一形状判式 —— 两处各写一遍就会漂，所以摊平在那颗、分组在这一颗，同一文件里贴着。
 */
private fun tagGroupsOf(rawTags: Any?): List<Pair<String, List<String>>> = when (rawTags) {
    is Map<*, *> -> rawTags.entries.mapNotNull { (k, v) ->
        val group = k.toString().takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val items = when (v) {
            is List<*> -> v.mapNotNull { it?.toString() }
            else -> listOfNotNull(v?.toString())
        }.filter { it.isNotBlank() }
        if (items.isEmpty()) null else group to items
    }
    else -> emptyList()
}

/**
 * 纯解析：信封 → [ComicInfo]。**解析语义逐条对齐 Android 侧 `JsComicSource.getComicDetails`**
 * （同一份 JS 答复在两处读成两种形状，就是"桌面看到的章节比手机少/反"那一族缺陷的成因）。
 *
 * 抽成顶层纯函数的理由：`details` 那一层只剩"发一次 JS"，判据能直接喂假信封进来。
 * 留在方法里就只能起 GraalJS，而那要真源、真网络，判据一条也写不出来。
 */
fun parseComicLoadInfo(comicId: String, env: Map<*, *>): ComicInfo {
    if (env["success"] == false) error("loadInfo 失败: ${env["error"]}")
    val data = env["data"] as? Map<*, *> ?: error("loadInfo 没有 data")
    val eps = mutableListOf<EngineSession.Episode>()
    when (val raw = data["chapters"] ?: data["episodes"]) {
        // Map 时 **key=话号、value=话名**（这一条从前被写过一次反的：屏上列表照摆，只有点开是错的）；
        // 值还是 Map 的就是分组表，按外层次序摊平。
        is Map<*, *> -> raw.forEach { (groupKey, groupVal) ->
            if (groupVal is Map<*, *>) {
                groupVal.forEach { (k, v) -> eps.add(EngineSession.Episode(v.toString(), k.toString())) }
            } else {
                eps.add(EngineSession.Episode(groupVal.toString(), groupKey.toString()))
            }
        }

        is List<*> -> raw.forEachIndexed { i, item ->
            if (item is Map<*, *>) {
                eps.add(
                    EngineSession.Episode(
                        item["title"]?.toString() ?: "第 $i 话",
                        item["id"]?.toString() ?: i.toString(),
                    ),
                )
            }
        }
    }
    // 一章都没有时补一枚占位章而**话号沿用漫画号**：`pages` 吃 `placeholder` 决定第三个实参交 `null`
    // 还是交货号，留空表等于那一本永远翻不出页。
    if (eps.isEmpty()) eps.add(EngineSession.Episode("", comicId, placeholder = true))
    // ── 信息区那几栏：键名与缺栏兜底**逐条**照 Android 侧 `getComicDetails` ──
    val author = data["author"]?.toString() ?: (data["uploader"]?.toString() ?: "")
    val rawTags = data["tags"]
    val tags = when (rawTags) {
        is List<*> -> rawTags.mapNotNull { it?.toString() }
        // Map 时摊平各组（对岸 `:476-486` 同一形：组内 List 逐条、单值也收；组名本身不进表）。
        is Map<*, *> -> rawTags.values.flatMap { v ->
            when (v) {
                is List<*> -> v.mapNotNull { it?.toString() }
                else -> listOfNotNull(v?.toString())
            }
        }
        else -> emptyList()
    }
    return ComicInfo(
        title = data["title"]?.toString().orEmpty(),
        description = data["description"]?.toString().orEmpty(),
        episodes = eps,
        // 只认 `cover` 这一栏，与对岸 `getComicDetails` 同一键名、同一兜底（缺栏 = 空串）。
        cover = data["cover"]?.toString().orEmpty(),
        // 评论那一跳要的 `subId` 与「摆不摆那一枚评论入口」要的 `commentCount`，同一次往返一起读回，
        // 键名 / 缺栏兜底逐字对齐对岸 `getComicDetails`（`:595-597`）：subId 缺栏 = null（不猜），
        // commentCount 取不出 = null（不拿 0 冒充「没有评论」）。
        subId = data["subId"]?.toString(),
        commentCount = (data["commentCount"] as? Number)?.toInt()
            ?: data["commentCount"]?.toString()?.toIntOrNull(),
        // 副题：`subTitle` 与 `subtitle` 两键都认（对岸 `:463` 同一序，先大后小）。
        subTitle = data["subTitle"]?.toString() ?: (data["subtitle"]?.toString() ?: ""),
        author = author,
        uploader = data["uploader"]?.toString() ?: author,
        tags = tags,
        // stars：对岸 `(res["stars"] as? Number)?.toFloat() ?: 0f`；桌面把"没报"与 0 分开 ⇒ 可空。
        // 字符串数字也收（一些源把数字塞成串，对岸那形 toFloat() 吃不到而这里 toFloatOrNull 吃得到 ——
        // 这是**宽一档**，方向是"多念出一栏真的数"而不是"少一栏"，可接受且屏上有数可回核）。
        stars = (data["stars"] as? Number)?.toFloat()
            ?: data["stars"]?.toString()?.toFloatOrNull()?.takeIf { it.isFinite() && it >= 0f },
        likesCount = (data["likesCount"] as? Number)?.toInt()
            ?: data["likesCount"]?.toString()?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 && it <= Int.MAX_VALUE }?.toInt(),
        sourceFavorite = data["isFavorite"] == true,
        recommend = (data["recommend"] as? List<*>)?.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            val id = map["id"]?.toString() ?: return@mapNotNull null
            ComicRecommend(id, map["title"]?.toString().orEmpty(), map["cover"]?.toString().orEmpty())
        }.orEmpty(),
        thumbnails = (data["thumbnails"] as? List<*>)?.mapNotNull { it?.toString() }.orEmpty(),
        tagGroups = tagGroupsOf(data["tags"]),
        updateTime = data["updateTime"]?.toString() ?: "",
        website = data["url"]?.toString() ?: "",
    )
}
