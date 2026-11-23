package com.venera.desktop.business

import com.venera.compose.feature.sourcemanage.SourceAccountInfo
import com.venera.compose.source.ComicSource
import com.venera.compose.source.model.ChapterPages
import com.venera.compose.source.model.Comic
import com.venera.compose.source.model.ComicDetails
import com.venera.compose.source.model.CommentCapabilities
import com.venera.compose.source.model.CommentPage
import com.venera.compose.source.model.SearchPage
import com.venera.desktop.gallery.data.DesktopSourceNames
import com.venera.desktop.desktopEngineCall
import com.venera.engineprobe.EngineSession
import java.io.File

/**
 * 共享 `ComicSource` 的**桌面实现**（D′-1）：把常驻 `EngineSession` 包成一颗源，喂给
 * `ComicDetailCore`（屏经 `ports.sources.getSource(key)` 拿它问评论能力 / 读评论 / 账号态）。
 *
 * ## 一颗实例只认一颗 key
 * `key` 固定，每次问引擎的跳各自先 `load(key)` 上锁（与 `DesktopComicContentApi` 同纪律）。
 * `name` / `version` 从随包脚本头部正则现读（`DesktopSourceNames`，与源管理屏同一本名字账）。
 *
 * ## 写侧（发评论 / 点赞 / 评分）真问引擎
 * 三件事各自一跳：`load(key)` 后调 `sendComment` / `starRating` / `likeComic`。
 * 引擎回 `false` = 这颗源没声明那条腿（核据此念「源不支持」）；抛错 = 源站自己拒绝
 * （未登录、令牌缺失等），核翻成对应文案，不在这里吞。
 *
 * ⚠️ `getAccountInfo()` 是**非挂起**方法、在调用方的 IO 块里被同步取（核 :336/:395）：
 * 这里**绝不调引擎**，只同步读脚本的 cookieFields + 本机数据罐的 isLogged。
 */
internal class DesktopEngineComicSource(
    override val key: String,
    private val engine: EngineSession?,
    private val assetDir: File,
    private val overrideDir: File?,
) : ComicSource {

    private val script: String?
        get() = DesktopSourceNames.scriptOf(assetDir, key, overrideDir)

    override val name: String
        get() = script?.let { DesktopSourceNames.displayNameOf(it) } ?: key

    override val version: String
        get() = script?.let { DesktopSourceNames.versionOf(it) }.orEmpty()

    // 详情链用不到的抽象成员：如实 failure，点名「详情不吃」。ping 交 -1（源声明的失败口径）。
    override suspend fun ping(): Long = -1L

    override suspend fun search(keyword: String, page: Int, options: List<String?>?): Result<SearchPage> =
        Result.failure(UnsupportedOperationException("详情页不吃搜索这一族（桌面搜索走壳的 searchComics）"))

    override suspend fun getComicDetails(comicId: String): Result<ComicDetails> {
        val e = engine ?: return Result.failure(IllegalStateException("引擎没起来"))
        return desktopEngineCall(e, sourceKey = key) {
            e.load(key)
            e.details(comicId)
        }.map { it.toComicDetails(comicId, key) }
    }

    override suspend fun getChapterPages(comicId: String, chapterId: String): Result<ChapterPages> {
        val e = engine ?: return Result.failure(IllegalStateException("引擎没起来"))
        val placeholder = chapterId == comicId
        return desktopEngineCall(e, sourceKey = key) {
            e.load(key)
            e.pages(comicId, EngineSession.Episode("", chapterId, placeholder))
        }.map { keys -> engineChapterPages(comicId, chapterId, "", keys) }
    }

    override suspend fun getExploreComics(page: Int): Result<List<Comic>> =
        Result.failure(UnsupportedOperationException("详情页不吃探索这一族（桌面探索走壳的 explore 链）"))

    // ── 评论：读侧真问引擎，写侧如实报不支持 ──

    override suspend fun getCommentCapabilities(): CommentCapabilities {
        val e = engine ?: return CommentCapabilities()
        return desktopEngineCall(e) {
            e.load(key)
            e.commentCapabilities()
        }.getOrNull()?.toSharedCapabilities() ?: CommentCapabilities()
    }

    override suspend fun loadCommentsPage(
        comicId: String,
        subId: String?,
        page: Int,
        replyId: String?,
    ): Result<CommentPage> {
        val e = engine ?: return Result.failure(IllegalStateException("引擎没起来"))
        return desktopEngineCall(e, sourceKey = key) {
            e.load(key)
            e.comments(comicId, subId, page, replyId)
        }.map { it.toSharedCommentPage() }
    }

    // 抽象成员 loadComments：核不直接吃它（走 loadCommentsPage），但接口要求落实。如实交 failure ——
    // 桌面详情读评论只用 loadCommentsPage 那一腿。
    override suspend fun loadComments(
        comicId: String,
        subId: String?,
        page: Int,
        replyId: String?,
    ): Result<List<com.venera.compose.source.model.Comment>> =
        loadCommentsPage(comicId, subId, page, replyId).map { it.comments }

    // 发评论真问引擎；回 false = 该源没声明发送入口（正常已由 canSend 挡在屏外）。
    override suspend fun sendComment(
        comicId: String,
        subId: String?,
        content: String,
        replyId: String?,
    ): Result<Boolean> {
        val e = engine ?: return Result.failure(IllegalStateException("引擎没起来"))
        return desktopEngineCall(e) {
            e.load(key)
            e.sendComment(comicId, subId, content, replyId)
        }
    }

    // 章节评论仍无引擎调用口：桌面没有章节评论入口，交回 failure 保持"未接通"的实情。
    override suspend fun sendChapterComment(comicId: String, chapterId: String, content: String): Result<Boolean> =
        Result.failure(UnsupportedOperationException("桌面还没接这一条链（发章节评论）"))

    // 作品点赞真问引擎；false = 源没声明点赞入口。
    override suspend fun likeComic(comicId: String, isLike: Boolean): Result<Boolean> {
        val e = engine ?: return Result.failure(IllegalStateException("引擎没起来"))
        return desktopEngineCall(e) {
            e.load(key)
            e.likeComic(comicId, isLike)
        }
    }

    // 打分真问引擎；false = 源没声明评分入口。引擎内已把 5 星换算成源规范的 0–10。
    override suspend fun starRating(comicId: String, rating: Float): Result<Boolean> {
        val e = engine ?: return Result.failure(IllegalStateException("引擎没起来"))
        return desktopEngineCall(e) {
            e.load(key)
            e.starRating(comicId, rating)
        }
    }

    // 能力位真问引擎：详情屏据 canRate / canLike 摆不摆那两枚把手。
    override suspend fun getWriteCapabilities(): com.venera.compose.source.model.WriteCapabilities {
        val e = engine ?: return com.venera.compose.source.model.WriteCapabilities()
        return desktopEngineCall(e) {
            e.load(key)
            e.writeCapabilities()
        }.getOrNull()?.toSharedWriteCapabilities() ?: com.venera.compose.source.model.WriteCapabilities()
    }

    /**
     * 同步读账号三档：`hasAccount` 取脚本声明的 cookieFields、`isLogged` 取本机数据罐。
     * 绝不调引擎（方法非挂起、在调用方 IO 块内被同步取）。
     */
    override fun getAccountInfo(): SourceAccountInfo {
        val raw = script
        return desktopAccountInfo(
            cookieFields = raw?.let { DesktopSourceNames.cookieFieldsOf(it) }.orEmpty(),
            isLogged = engine?.host?.isLogged(key) == true,
            supportsAccountLogin = raw != null && (
                DesktopSourceNames.supportsPasswordLoginOf(raw) ||
                    DesktopSourceNames.supportsWebLoginOf(raw)
                ),
        )
    }

    override val favoriteData: com.venera.compose.source.FavoriteData?
        get() {
            val e = engine ?: return null
            val meta = e.favoriteMeta()
            if (!meta.exists) return null

            return com.venera.compose.source.FavoriteData(
                key = key,
                title = name,
                multiFolder = meta.multiFolder,
                singleFolderForSingleComic = meta.singleFolderForSingleComic,
                isOldToNewSort = meta.isOldToNewSort,
                loadFolders = if (meta.multiFolder) {
                    { comicId ->
                        desktopEngineCall(e) {
                            e.load(key)
                            val res = e.networkFavoritesFoldersWithComic(comicId)
                            com.venera.compose.source.FavFolders(res.folders, res.favorited)
                        }
                    }
                } else null,
                addFolder = if (meta.multiFolder && meta.hasAddFolder) {
                    { folderName ->
                        desktopEngineCall(e) {
                            e.load(key)
                            e.networkFavoritesAddFolder(folderName)
                        }
                    }
                } else null,
                deleteFolder = if (meta.multiFolder && meta.hasDeleteFolder) {
                    { folderId ->
                        desktopEngineCall(e) {
                            e.load(key)
                            e.networkFavoritesDeleteFolder(folderId)
                        }
                    }
                } else null,
                addOrDelFavorite = { comicId, folderId, isAdding, favoriteId ->
                    desktopEngineCall(e) {
                        e.load(key)
                        if (isAdding) {
                            e.networkFavoritesAddOrDel(comicId, folderId, true, favoriteId)
                        } else {
                            // 有的源（JM 那族）的收藏接口是 toggle：本地「已收藏」与源站不同步时，
                            // 点一次取消会反向加回 —— 表现就是「点了取消还在、要多点几次」。
                            // 所以取消后必须读回校验，仍在收藏就再补一次（最多两跳），以最终读回为准；
                            // 两跳仍不干净就如实报失败，不假装成功。
                            var stillFavorited = false
                            var attempts = 0
                            while (attempts < 2) {
                                e.networkFavoritesAddOrDel(comicId, folderId, false, favoriteId)
                                stillFavorited = if (meta.multiFolder) {
                                    e.networkFavoritesFoldersWithComic(comicId).favorited.isNotEmpty()
                                } else {
                                    false
                                }
                                if (!stillFavorited) break
                                attempts++
                            }
                            if (stillFavorited) error("取消收藏没有生效，源站仍显示已收藏")
                        }
                    }
                },
            )
        }
}
