package com.venera.desktop.follow

import com.venera.engineprobe.EngineSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 网站收藏夹的生产取数口：按能力位（`loadComics` 页码 / `loadNext` 游标）折平成同一个 [loadPage]。
 *
 * 与 [EngineFollowDetailSource] 同一隔离口径：从主会话派生临时 JS 会话（复用 Cookie 罐与连接池），
 * 避免与前台切源或阅读并发抢锁；一轮导入结束后由调用方调 [close] 释放。
 */
class NetworkFavoritesFollowSource(
    private val baseSession: EngineSession,
    private val sourceName: String,
    folderId: String?,
) {

    private val resolvedFolderId: String? = folderId?.takeIf { it.isNotBlank() }

    private var ephemeral: EngineSession? = null

    private var caps: EngineSession.NetFavCaps? = null

    private var pageNo = 0

    private var cursor: String? = null

    private var maxPage: Int? = null

    suspend fun loadPage(token: String?): FollowNetPage = withContext(Dispatchers.IO) {
        val session = ensureSession()
        val cap = caps ?: return@withContext FollowNetPage(
            comics = emptyList(),
            hasMore = false,
            error = "该源未声明收藏能力",
        )
        // 游标优先（壳的续页口径），否则按页码走 loadComics；首页两家各取各的入口。
        val page = when {
            pageNo > 0 && cursor != null -> session.networkFavoritesLoadNext(cursor, resolvedFolderId)
            pageNo > 0 && cap.hasLoadComics -> session.networkFavoritesLoadComics(pageNo + 1, resolvedFolderId)
            cap.hasLoadComics -> session.networkFavoritesLoadComics(1, resolvedFolderId)
            cap.hasLoadNext -> session.networkFavoritesLoadNext(null, resolvedFolderId)
            else -> return@withContext FollowNetPage(
                comics = emptyList(),
                hasMore = false,
                error = "该源未声明 favorites.loadComics / favorites.loadNext",
            )
        }
        pageNo += 1
        cursor = page.next
        if (page.maxPage != null) maxPage = page.maxPage
        val more = page.next != null || pageNo < (maxPage ?: pageNo)
        FollowNetPage(
            comics = page.comics.map { card ->
                FollowNetComic(
                    comicId = card.id,
                    title = card.title,
                    coverUrl = card.cover,
                )
            },
            next = page.next ?: if (more && cap.hasLoadComics) (pageNo + 1).toString() else null,
            hasMore = more && page.error == null,
            error = page.error,
        )
    }

    private fun ensureSession(): EngineSession {
        ephemeral?.let { return it }
        val fork = baseSession.forkEphemeral()
        try {
            fork.load(sourceName)
            caps = fork.networkFavoritesCaps()
        } catch (e: Throwable) {
            runCatching { fork.close() }
            throw e
        }
        ephemeral = fork
        return fork
    }

    /** 释放本轮导入派生的临时 JS 会话。 */
    fun close() {
        val current = ephemeral
        ephemeral = null
        caps = null
        runCatching { current?.close() }
    }
}
