package com.venera.engineprobe

/**
 * 详情评论：`comic.loadComments` 那一坨答复读回来的实体与纯解析。
 *
 * ## 为什么住在 `engine-probe` 而不复用 Android 那颗 `source/model/Comment`
 * 桌面只把 `data/platform` 与 `data/db` 两目录 `srcDir` 进编译面（见 `desktop/build.gradle.kts`），
 * `source/model/ComicSourceModels.kt` 那颗 `Comment` 不在桌面编译面里。评论实体与解析就得跟
 * [ComicInfo] / [parseComicLoadInfo] 同一族住在这一层 —— 同一份 JS 答复在两处读成两种形状，
 * 就是「桌面看到的评论比手机少/字段错位」那一族缺陷的成因，所以这里**逐条回核** Android 侧
 * `SourcePayloadParser.comment`（键名、缺栏兜底、`comments`/`list` 双候选、`maxPage` 取数），
 * 而不是自造一套。
 */
data class DesktopComment(
    val id: String,
    val userName: String,
    val avatar: String?,
    val content: String,
    val time: String?,
    val replyCount: Int?,
    val score: Int,
    val voteStatus: Int,
    val isLiked: Boolean,
)

/** 一页评论 + 源站报的最大页（读不出来为 `null`，与对岸 `CommentPage` 同一口径）。 */
data class DesktopCommentPage(
    val comments: List<DesktopComment>,
    val maxPage: Int?,
)

/**
 * 这一颗源支不支持「读评论 / 发评论」两件事（对齐对岸 `CommentCapabilities`）。
 *
 * 为什么是**能力位**而不是"点了再说"：随包 34 份脚本里 19 份根本没声明评论那一形，
 * 摆一枚点了只会抛「当前源暂不支持加载评论」的把手 = 本仓明令禁的假把手（评审 R6 那一族）。
 * 判式逐字对齐对岸 `JsComicSource.getCommentCapabilities`（`s.comic.loadComments` / `s.comments.load`
 * / `s.comments` 三条腿任一为函数 = 能读；`s.comic.sendComment` / `s.comments.send` 任一为函数 = 能发）。
 */
data class DesktopCommentCapabilities(
    val canLoad: Boolean = false,
    val canSend: Boolean = false,
)

/**
 * 这一颗源支不支持「打分 / 作品点赞」（详情屏摆不摆那两枚把手读它）。
 * 与 [DesktopCommentCapabilities] 同一姿势：能力位如实，不靠"点了再抛错"探路。
 */
data class DesktopWriteCapabilities(
    val canRate: Boolean = false,
    val canLike: Boolean = false,
)

/**
 * 把一枚评论对象读成 [DesktopComment]。**缺正文（`content` 为空/无）就不算一条评论** ——
 * 逐字对齐对岸 `SourcePayloadParser.comment`（那颗以 `content` 为存在判据：`map["content"]?...?.let{...}`）。
 */
private fun comment(map: Map<*, *>): DesktopComment? =
    (map["content"]?.toString())?.let { content ->
        DesktopComment(
            id = map["id"]?.toString().orEmpty(),
            userName = map["userName"]?.toString() ?: "读者",
            avatar = map["avatar"]?.toString(),
            content = content,
            time = map["time"]?.toString(),
            replyCount = (map["replyCount"] as? Number)?.toInt(),
            score = (map["score"] as? Number)?.toInt() ?: 0,
            voteStatus = (map["voteStatus"] as? Number)?.toInt() ?: 0,
            isLiked = map["isLiked"] == true,
        )
    }

/**
 * 纯解析：`comic.loadComments` 的信封 → [DesktopCommentPage]。**发那一次 JS 留在 [EngineSession.comments]，
 * 这里只吃 `Map`**，判据能直接喂假信封进来而不用起 GraalJS / 真网络（与 [parseComicLoadInfo] 同一抽法）。
 *
 * 三档对齐对岸 `JsComicSource.loadCommentsPage`：
 *  - `success == false` ⇒ 抛（源站自己报了错，不能当空评论列表静默过去）；
 *  - `data` 是 `List` ⇒ 那就是评论数组本身；是 `Map` ⇒ 认 `comments` 再退 `list`，两键都没有 ⇒ 抛；
 *  - `maxPage` 从 `data["maxPage"]` 取，非 Map / 取不出 / 为负一律 `null`（对岸 `.takeIf { it >= 0 }`）。
 */
fun parseCommentPage(env: Map<*, *>): DesktopCommentPage {
    if (env["success"] == false) error("loadComments 失败: ${env["error"]}")
    val data = env["data"]
    val rawList = when (data) {
        is List<*> -> data
        is Map<*, *> -> (data["comments"] ?: data["list"]) as? List<*> ?: error("评论响应缺少列表")
        else -> error("评论响应格式错误")
    }
    val comments = rawList.mapNotNull { (it as? Map<*, *>)?.let(::comment) }
    val maxPage = (data as? Map<*, *>)?.get("maxPage")?.toString()?.toDoubleOrNull()?.toInt()?.takeIf { it >= 0 }
    return DesktopCommentPage(comments, maxPage)
}
