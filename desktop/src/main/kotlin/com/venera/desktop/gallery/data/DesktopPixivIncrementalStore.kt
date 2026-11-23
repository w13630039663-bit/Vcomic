package com.venera.desktop.gallery.data

import com.google.gson.Gson
import com.venera.compose.gallery.data.PixivAppPage
import com.venera.compose.gallery.data.PixivRestrict
import com.venera.compose.gallery.data.PixivUser
import com.venera.compose.gallery.data.PixivUserPage
import com.venera.compose.gallery.data.PixivWork
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Pixiv 关注画师与云端书签的本地全量索引与增量同步存储。
 *
 * @param dir 索引文件落盘目录。
 * @param nowMs 当前毫秒时钟提供器。
 * @param pageSize 每次切分给界面的单页条数。
 * @param maxIncrementalPages 增量刷新时向后探测的最大页数上限。
 */
internal class DesktopPixivIncrementalStore(
    dir: File,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
    private val maxIncrementalPages: Int = MAX_INCREMENTAL_PAGES,
) {

    private data class BookmarkIndex(
        val ts: Long,
        val key: String,
        val works: List<PixivWork>,
        val tailNextUrl: String?,
    )

    private data class FollowingIndex(
        val ts: Long,
        val key: String,
        val users: List<PixivUser>,
        val tailNextUrl: String?,
    )

    private val gson = Gson()
    private val dir: File = dir.also { it.mkdirs() }
    private val locks = ConcurrentHashMap<String, Mutex>()

    private fun lockOf(key: String): Mutex = locks.getOrPut(key) { Mutex() }

    private fun fileOf(key: String): File {
        // 摘要只作文件名，不参与完整性校验；旧版 SHA-1 命名的缓存不再命中，首轮自动重建。
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
        val name = digest.joinToString("") { "%02x".format(it) }
        return File(dir, "$name.json")
    }

    /**
     * 读取当前本地缓存过的全部书签作品编号，用于冷启动回填初始收藏状态。
     */
    fun cachedBookmarkIds(): Set<Long> {
        val out = HashSet<Long>()
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }.orEmpty()
        for (file in files) {
            val idx = runCatching { gson.fromJson(file.readText(), BookmarkIndex::class.java) }.getOrNull()
                ?: continue
            if (!idx.key.startsWith("bookmarks|")) continue
            idx.works.forEach { out.add(it.post.id) }
        }
        return out
    }

    /**
     * 判断游标是否为本地索引分页游标。
     *
     * @param nextUrl 待检查的分页游标。
     */
    fun isLocalCursor(nextUrl: String?): Boolean =
        nextUrl != null && nextUrl.startsWith(LOCAL_CURSOR_PREFIX)

    /**
     * 同步或分页读取云端书签。
     *
     * @param userId 当前登录用户的数字编号。
     * @param restrict 公开或私密书签档位。
     * @param nextUrl 下一页游标，传空表示首屏同步。
     * @param ttlMs 索引新鲜期时长（毫秒）。
     * @param fresh 是否忽略新鲜期强制向站方增量对账。
     * @param fetchPage 向站方请求单页数据的回调，传空表示请求第一页。
     */
    suspend fun loadBookmarks(
        userId: String,
        restrict: PixivRestrict,
        nextUrl: String?,
        ttlMs: Long,
        fresh: Boolean,
        fetchPage: suspend (String?) -> Result<PixivAppPage>,
    ): Result<PixivAppPage> {
        val key = bookmarkKey(userId, restrict)
        return lockOf(key).withLock {
            if (isLocalCursor(nextUrl)) {
                val offset = parseCursorOffset(nextUrl!!)
                val index = readBookmarks(key)
                if (index != null && offset < index.works.size) {
                    return@withLock Result.success(sliceBookmarks(key, index, offset))
                }
                val remoteCursor = index?.tailNextUrl
                    ?: return@withLock Result.success(PixivAppPage(emptyList(), null))
                return@withLock fetchAndAppendBookmarks(key, index, remoteCursor, fetchPage)
            }

            if (nextUrl != null) {
                val existing = readBookmarks(key)
                return@withLock fetchAndAppendBookmarks(key, existing, nextUrl, fetchPage)
            }

            val existing = readBookmarks(key)
            if (!fresh && existing != null && existing.works.isNotEmpty() && nowMs() - existing.ts <= ttlMs) {
                return@withLock Result.success(sliceBookmarks(key, existing, 0))
            }

            val firstRes = fetchPage(null)
            val firstPage = firstRes.getOrNull()
            if (firstPage == null) {
                if (!fresh && existing != null && existing.works.isNotEmpty()) {
                    return@withLock Result.success(sliceBookmarks(key, existing, 0))
                }
                return@withLock firstRes
            }

            val updated = if (existing == null || existing.works.isEmpty()) {
                BookmarkIndex(
                    ts = nowMs(),
                    key = key,
                    works = deduplicate(firstPage.works) { it.post.id },
                    tailNextUrl = firstPage.nextUrl,
                )
            } else {
                val collected = mutableListOf(firstPage)
                val existingIds = existing.works.mapTo(HashSet()) { it.post.id }
                var cursor = firstPage.nextUrl
                while (
                    collected.size < maxIncrementalPages &&
                    cursor != null &&
                    collected.last().works.none { it.post.id in existingIds }
                ) {
                    val nextPage = fetchPage(cursor).getOrNull() ?: break
                    collected.add(nextPage)
                    cursor = nextPage.nextUrl
                }
                val merged = mergeHead(
                    existing = existing.works,
                    fetchedBatches = collected.map { it.works },
                    existingTailNextUrl = existing.tailNextUrl,
                    lastBatchNextUrl = collected.last().nextUrl,
                    idOf = { it.post.id },
                )
                BookmarkIndex(
                    ts = nowMs(),
                    key = key,
                    works = merged.first,
                    tailNextUrl = merged.second,
                )
            }
            writeBookmarks(updated)
            Result.success(sliceBookmarks(key, updated, 0))
        }
    }

    /**
     * 同步或分页读取已关注画师列表。
     *
     * @param userId 当前登录用户的数字编号。
     * @param restrict 公开或私密关注档位。
     * @param nextUrl 下一页游标，传空表示首屏同步。
     * @param ttlMs 索引新鲜期时长（毫秒）。
     * @param fresh 是否忽略新鲜期强制向站方增量对账。
     * @param fetchPage 向站方请求单页数据的回调，传空表示请求第一页。
     */
    suspend fun loadFollowing(
        userId: String,
        restrict: PixivRestrict,
        nextUrl: String?,
        ttlMs: Long,
        fresh: Boolean,
        fetchPage: suspend (String?) -> Result<PixivUserPage>,
    ): Result<PixivUserPage> {
        val key = followingKey(userId, restrict)
        return lockOf(key).withLock {
            if (isLocalCursor(nextUrl)) {
                val offset = parseCursorOffset(nextUrl!!)
                val index = readFollowing(key)
                if (index != null && offset < index.users.size) {
                    return@withLock Result.success(sliceFollowing(key, index, offset))
                }
                val remoteCursor = index?.tailNextUrl
                    ?: return@withLock Result.success(PixivUserPage(emptyList(), null))
                return@withLock fetchAndAppendFollowing(key, index, remoteCursor, fetchPage)
            }

            if (nextUrl != null) {
                val existing = readFollowing(key)
                return@withLock fetchAndAppendFollowing(key, existing, nextUrl, fetchPage)
            }

            val existing = readFollowing(key)
            if (!fresh && existing != null && existing.users.isNotEmpty() && nowMs() - existing.ts <= ttlMs) {
                return@withLock Result.success(sliceFollowing(key, existing, 0))
            }

            val firstRes = fetchPage(null)
            val firstPage = firstRes.getOrNull()
            if (firstPage == null) {
                if (!fresh && existing != null && existing.users.isNotEmpty()) {
                    return@withLock Result.success(sliceFollowing(key, existing, 0))
                }
                return@withLock firstRes
            }

            val updated = if (existing == null || existing.users.isEmpty()) {
                FollowingIndex(
                    ts = nowMs(),
                    key = key,
                    users = deduplicate(firstPage.users) { it.id },
                    tailNextUrl = firstPage.nextUrl,
                )
            } else {
                val collected = mutableListOf(firstPage)
                val existingIds = existing.users.mapTo(HashSet()) { it.id }
                var cursor = firstPage.nextUrl
                while (
                    collected.size < maxIncrementalPages &&
                    cursor != null &&
                    collected.last().users.none { it.id in existingIds }
                ) {
                    val nextPage = fetchPage(cursor).getOrNull() ?: break
                    collected.add(nextPage)
                    cursor = nextPage.nextUrl
                }
                val merged = mergeHead(
                    existing = existing.users,
                    fetchedBatches = collected.map { it.users },
                    existingTailNextUrl = existing.tailNextUrl,
                    lastBatchNextUrl = collected.last().nextUrl,
                    idOf = { it.id },
                )
                FollowingIndex(
                    ts = nowMs(),
                    key = key,
                    users = merged.first,
                    tailNextUrl = merged.second,
                )
            }
            writeFollowing(updated)
            Result.success(sliceFollowing(key, updated, 0))
        }
    }

    /**
     * 取消收藏后原地移除本地索引条目，避免整份列表缓存失效。
     *
     * @param illustId 被取消收藏的作品数字编号。
     */
    suspend fun removeBookmark(illustId: Long) {
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }.orEmpty()
        for (file in files) {
            val idx = runCatching { gson.fromJson(file.readText(), BookmarkIndex::class.java) }.getOrNull()
                ?: continue
            if (!idx.key.startsWith("bookmarks|")) continue
            lockOf(idx.key).withLock {
                val latest = readBookmarks(idx.key) ?: return@withLock
                val filtered = latest.works.filterNot { it.post.id == illustId }
                if (filtered.size != latest.works.size) {
                    writeBookmarks(latest.copy(works = filtered))
                }
            }
        }
    }

    /**
     * 取消关注后原地移除本地索引条目，避免整份列表缓存失效。
     *
     * @param userId 被取消关注的画师数字编号。
     */
    suspend fun removeFollowing(userId: Long) {
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }.orEmpty()
        for (file in files) {
            val idx = runCatching { gson.fromJson(file.readText(), FollowingIndex::class.java) }.getOrNull()
                ?: continue
            if (!idx.key.startsWith("following|")) continue
            lockOf(idx.key).withLock {
                val latest = readFollowing(idx.key) ?: return@withLock
                val filtered = latest.users.filterNot { it.id == userId }
                if (filtered.size != latest.users.size) {
                    writeFollowing(latest.copy(users = filtered))
                }
            }
        }
    }

    /**
     * 将匹配前缀的索引标记为过期，下次进入仅做轻量增量同步而不丢失历史数据。
     *
     * @param prefix 索引键前缀（如 `bookmarks|` 或 `following|`）。
     */
    suspend fun markStaleByPrefix(prefix: String) {
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }.orEmpty()
        for (file in files) {
            if (prefix.startsWith("bookmarks")) {
                val idx = runCatching { gson.fromJson(file.readText(), BookmarkIndex::class.java) }.getOrNull()
                    ?: continue
                if (!idx.key.startsWith(prefix)) continue
                lockOf(idx.key).withLock {
                    readBookmarks(idx.key)?.let { writeBookmarks(it.copy(ts = 0L)) }
                }
            } else if (prefix.startsWith("following")) {
                val idx = runCatching { gson.fromJson(file.readText(), FollowingIndex::class.java) }.getOrNull()
                    ?: continue
                if (!idx.key.startsWith(prefix)) continue
                lockOf(idx.key).withLock {
                    readFollowing(idx.key)?.let { writeFollowing(it.copy(ts = 0L)) }
                }
            }
        }
    }

    private suspend fun fetchAndAppendBookmarks(
        key: String,
        existing: BookmarkIndex?,
        remoteCursor: String,
        fetchPage: suspend (String?) -> Result<PixivAppPage>,
    ): Result<PixivAppPage> {
        val res = fetchPage(remoteCursor)
        val page = res.getOrNull() ?: return res
        val merged = if (existing == null) {
            deduplicate(page.works) { it.post.id }
        } else {
            deduplicate(existing.works + page.works) { it.post.id }
        }
        writeBookmarks(
            BookmarkIndex(
                ts = existing?.ts ?: nowMs(),
                key = key,
                works = merged,
                tailNextUrl = page.nextUrl,
            ),
        )
        return Result.success(page)
    }

    private suspend fun fetchAndAppendFollowing(
        key: String,
        existing: FollowingIndex?,
        remoteCursor: String,
        fetchPage: suspend (String?) -> Result<PixivUserPage>,
    ): Result<PixivUserPage> {
        val res = fetchPage(remoteCursor)
        val page = res.getOrNull() ?: return res
        val merged = if (existing == null) {
            deduplicate(page.users) { it.id }
        } else {
            deduplicate(existing.users + page.users) { it.id }
        }
        writeFollowing(
            FollowingIndex(
                ts = existing?.ts ?: nowMs(),
                key = key,
                users = merged,
                tailNextUrl = page.nextUrl,
            ),
        )
        return Result.success(page)
    }

    private fun sliceBookmarks(key: String, index: BookmarkIndex, offset: Int): PixivAppPage {
        val end = minOf(offset + pageSize, index.works.size)
        val slice = if (offset < end) index.works.subList(offset, end).toList() else emptyList()
        val next = if (end < index.works.size) localCursor(key, end) else index.tailNextUrl
        return PixivAppPage(works = slice, nextUrl = next)
    }

    private fun sliceFollowing(key: String, index: FollowingIndex, offset: Int): PixivUserPage {
        val end = minOf(offset + pageSize, index.users.size)
        val slice = if (offset < end) index.users.subList(offset, end).toList() else emptyList()
        val next = if (end < index.users.size) localCursor(key, end) else index.tailNextUrl
        return PixivUserPage(users = slice, nextUrl = next)
    }

    private fun readBookmarks(key: String): BookmarkIndex? = runCatching {
        val f = fileOf(key)
        if (!f.isFile) return null
        gson.fromJson(f.readText(), BookmarkIndex::class.java)?.takeIf { it.key == key }
    }.getOrNull()

    private fun writeBookmarks(index: BookmarkIndex) {
        runCatching {
            fileOf(index.key).writeText(gson.toJson(index))
        }
    }

    private fun readFollowing(key: String): FollowingIndex? = runCatching {
        val f = fileOf(key)
        if (!f.isFile) return null
        gson.fromJson(f.readText(), FollowingIndex::class.java)?.takeIf { it.key == key }
    }.getOrNull()

    private fun writeFollowing(index: FollowingIndex) {
        runCatching {
            fileOf(index.key).writeText(gson.toJson(index))
        }
    }

    private fun <T, K> mergeHead(
        existing: List<T>,
        fetchedBatches: List<List<T>>,
        existingTailNextUrl: String?,
        lastBatchNextUrl: String?,
        idOf: (T) -> K,
    ): Pair<List<T>, String?> {
        val fetchedAll = deduplicate(fetchedBatches.flatten(), idOf)
        val fetchedIds = fetchedAll.mapTo(HashSet(), idOf)
        val lastOverlapIdx = existing.indexOfLast { idOf(it) in fetchedIds }
        if (lastOverlapIdx < 0) {
            // 新拉批次与旧索引完全脱节时，放弃陈旧尾巴避免形成断层。
            return fetchedAll to lastBatchNextUrl
        }
        val tail = existing.subList(lastOverlapIdx + 1, existing.size).filter { idOf(it) !in fetchedIds }
        return deduplicate(fetchedAll + tail, idOf) to existingTailNextUrl
    }

    private fun <T, K> deduplicate(items: List<T>, idOf: (T) -> K): List<T> {
        val seen = HashSet<K>(items.size)
        val out = ArrayList<T>(items.size)
        for (item in items) {
            if (seen.add(idOf(item))) out.add(item)
        }
        return out
    }

    private fun bookmarkKey(userId: String, restrict: PixivRestrict): String =
        "bookmarks|$userId|${restrict.name}"

    private fun followingKey(userId: String, restrict: PixivRestrict): String =
        "following|$userId|${restrict.name}"

    private fun localCursor(key: String, offset: Int): String =
        "$LOCAL_CURSOR_PREFIX$key|$offset"

    private fun parseCursorOffset(cursor: String): Int =
        cursor.substringAfterLast('|', "0").toIntOrNull()?.coerceAtLeast(0) ?: 0

    companion object {
        private const val LOCAL_CURSOR_PREFIX = "venera-local://"
        private const val DEFAULT_PAGE_SIZE = 30
        private const val MAX_INCREMENTAL_PAGES = 3
    }
}
