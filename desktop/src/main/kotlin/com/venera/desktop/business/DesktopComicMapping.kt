package com.venera.desktop.business

import com.venera.compose.feature.sourcemanage.SourceAccountInfo
import com.venera.compose.source.model.ChapterPages
import com.venera.compose.source.model.Comic
import com.venera.compose.source.model.ComicChapter
import com.venera.compose.source.model.ComicDetails
import com.venera.compose.source.model.Comment
import com.venera.compose.source.model.CommentCapabilities
import com.venera.compose.source.model.CommentPage
import com.venera.compose.source.model.WriteCapabilities
import com.venera.engineprobe.ComicInfo
import com.venera.engineprobe.DesktopComment
import com.venera.engineprobe.DesktopCommentCapabilities
import com.venera.engineprobe.DesktopCommentPage
import com.venera.engineprobe.DesktopWriteCapabilities

/**
 * 引擎答复（`:engine-probe` 的 `ComicInfo` / `DesktopCommentPage`）→ 共享业务模型
 * （`:app` 的 `source.model.*`）的**纯换算层**（D′-1）。
 *
 * ## 为什么住桌面而不搬解析件进 `:app`
 * `:desktop` 已 `implementation(project(":engine-probe"))`（`desktop/build.gradle.kts:472`），
 * 于是这一颗文件同时看得见引擎那侧的 `ComicInfo` 与共享那侧的 `ComicDetails` ——
 * 换算在这一层做完即可，**不必把 `ComicLoadInfo.kt` / `DesktopComments.kt` 搬进 `:app`
 * 的 `source/js/`**。搬运会让 `:app` 与 `:engine-probe` 两侧都改引用点、还得同步回安卓主线仓；
 * 而换算层是**纯新增、`:app` 零改动**。
 * 解析判据逐条仍在引擎那侧（`ComicLoadInfoParseTest` / `DesktopCommentParseTest`），这里不重钉。
 *
 * 字段映射的**缺栏兜底 / 可空性**逐条照对岸 `JsComicSource.getComicDetails`，与
 * `ComicLoadInfo.kt` 文件头"同一份 JS 答复两处读成两种形状就是缺陷成因"同源。
 * 桌面只做形状换算，不再重新解析信封，因此不会漂出第三种形状。
 */

/**
 * `ComicInfo` → `ComicDetails`。
 *
 * - `episodes`（引擎 `Episode(name,id,placeholder)`）摊平成共享 `ComicChapter`：`id` 原样、
 *   `title` 取 `name`、`order` = 摊平序（对岸 `chapterGroups` 空时屏走 `chapters` 腿，方案风险④）。
 *   占位章的 `id` 就是漫画号本身（`parseComicLoadInfo` 交 `Episode("", comicId, placeholder=true)`），
 *   这里不改那一格，取页时由 `ComicSource.getChapterPages` 判占位决定第三实参。
 * - `tagGroups`（仅 Map 形状有）→ `ComicDetails.tagMap`，屏上 `plainTags` 由它重建 `namespace:tag`；
 *   List 形状（无组）时 `tagMap` 空、扁平 `tags` 仍进 `comic.tags`（对岸同一口径）。
 * - `stars` / `likesCount` 维持可空：`Comic` 那两栏是 `Float?` / `Int?`，"没报" ≠ 0（对岸同族）。
 *   `ComicDetails.rating` / `likesCount` 是非空基本型，取不到给 0f / 0（对岸 `stars = rating` 同形）。
 * - `sourceFavorite` → `Comic.isFavorite`（详情自带的源端收藏种子，对岸 `isFavorite == true`）。
 */
internal fun ComicInfo.toComicDetails(
    comicId: String,
    sourceKey: String,
): ComicDetails {
    val chapterList = episodes.mapIndexed { index, ep ->
        ComicChapter(id = ep.id, title = ep.name, order = index, updatedAt = updateTime)
    }
    // 分组表只在源按 Map 报标签时存在；List 形状时 tagMap 空，屏退回扁平 tags 那串。
    val tagMap = if (tagGroups.isEmpty()) emptyMap() else tagGroups.toMap()
    val comic = Comic(
        id = comicId,
        title = title,
        subTitle = subTitle,
        cover = cover,
        sourceKey = sourceKey,
        tags = tags,
        description = description,
        updateTime = updateTime,
        isFavorite = sourceFavorite,
        rating = stars,
        likesCount = likesCount,
    )
    return ComicDetails(
        comic = comic,
        author = author,
        // 引擎没读"作品状态"这一栏 → 空串，屏不显示该标签（对岸"绝不臆造连载中"同口径）。
        status = "",
        rating = stars ?: 0f,
        chapters = chapterList,
        chapterGroups = emptyList(),
        thumbnails = thumbnails,
        recommend = recommend.map { rec ->
            Comic(id = rec.id, title = rec.title, cover = rec.cover, sourceKey = sourceKey)
        },
        tagMap = tagMap,
        uploader = uploader,
        updateTime = updateTime,
        url = website,
        likesCount = likesCount ?: 0,
        commentCount = commentCount ?: 0,
        subId = subId,
        sourceKey = sourceKey,
    )
}

/**
 * 页表：引擎 `pages()` 交回的是**页键**（`EngineSession.pages` 的 KDoc：源站标识、非可 GET 地址）。
 * `useOnImageLoad = true` 如实标注 —— 桌面取图走 Coil fetcher 逐页问 `pageImage`（引擎内部按需
 * `onImageLoad`），与对岸"声明了 onImageLoad 则每页须解析"同形。headers 交空：真实防盗头由
 * `EngineSession.pageImage` 在取字节那一步自己带（`desktopImageRequestOf`），不在页表层发布。
 */
internal fun engineChapterPages(
    comicId: String,
    chapterId: String,
    chapterTitle: String,
    keys: List<String>,
): ChapterPages = ChapterPages(
    comicId = comicId,
    chapterId = chapterId,
    chapterTitle = chapterTitle,
    pages = keys,
    headers = emptyMap(),
    useOnImageLoad = true,
)

/** 评论实体换算：`DesktopComment` → 共享 `Comment`，字段逐条同值（两颗本就同一本账，只是分住两模块）。 */
internal fun DesktopComment.toSharedComment(): Comment = Comment(
    id = id,
    userName = userName,
    avatar = avatar,
    content = content,
    time = time,
    replyCount = replyCount,
    isLiked = isLiked,
    score = score,
    voteStatus = voteStatus,
)

internal fun DesktopCommentPage.toSharedCommentPage(): CommentPage = CommentPage(
    comments = comments.map { it.toSharedComment() },
    maxPage = maxPage,
)

internal fun DesktopCommentCapabilities.toSharedCapabilities(): CommentCapabilities =
    CommentCapabilities(canLoad = canLoad, canSend = canSend)

internal fun DesktopWriteCapabilities.toSharedWriteCapabilities(): WriteCapabilities =
    WriteCapabilities(canRate = canRate, canLike = canLike)

/**
 * 账号三档读数换算（**不跑引擎**）：`hasAccount` = 该源声明了手填 cookie 字段；
 * `isLogged` = 本机数据罐里这颗源有登录态。两栏分开喂：屏上"要不要摆登录把手"与"已登录否"是两件事。
 *
 * 用户名 / 信息项 / 密码登录 / 网页登录那几档桌面今天读不到（引擎没这些调用口），如实留默认 ——
 * 屏据此只走手填 cookie 那一路，不摆按不动的假把手（`sourceSupportsLogin` 靠 `hasAccount`）。
 */
internal fun desktopAccountInfo(
    cookieFields: List<String>,
    isLogged: Boolean,
    supportsAccountLogin: Boolean = false,
): SourceAccountInfo =
    SourceAccountInfo(
        hasAccount = cookieFields.isNotEmpty() || supportsAccountLogin,
        isLogged = isLogged,
        cookieFields = cookieFields,
    )
