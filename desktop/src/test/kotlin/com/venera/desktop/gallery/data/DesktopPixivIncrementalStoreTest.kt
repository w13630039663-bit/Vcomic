package com.venera.desktop.gallery.data

import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.data.PixivAppPage
import com.venera.compose.gallery.data.PixivRestrict
import com.venera.compose.gallery.data.PixivUser
import com.venera.compose.gallery.data.PixivUserPage
import com.venera.compose.gallery.data.PixivWork
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopPixivIncrementalStoreTest {

    private fun tmpDir(tag: String) =
        File(System.getProperty("java.io.tmpdir"), "venera-inc-$tag-${System.nanoTime()}")

    private fun work(id: Long) = PixivWork(
        post = GalleryPost(
            site = GallerySite.PIXIV,
            id = id,
            width = 800,
            height = 600,
            previewUrl = "https://i.pximg.net/c/250x250_80_a2/img-master/img/${id}_p0.jpg",
            largeUrl = "https://i.pximg.net/img-master/img/${id}_p0_master1200.jpg",
            fileUrl = "https://i.pximg.net/img-original/img/${id}_p0.jpg",
            tags = "",
            rating = "s",
            fileExt = "jpg",
        ),
        title = "work-$id",
    )

    private fun user(id: Long) = PixivUser(
        id = id,
        name = "artist-$id",
        account = "acc-$id",
        avatarUrl = "",
        muted = false,
    )

    @Test
    fun `书签增量同步命中旧锚点即止且支持本地游标零网络翻页与原地移除`(): Unit = runBlocking {
        val dir = tmpDir("bookmarks")
        val clock = AtomicLong(1_000L)
        val store = DesktopPixivIncrementalStore(
            dir = dir,
            nowMs = { clock.get() },
            pageSize = 3,
            maxIncrementalPages = 3,
        )
        val calls = AtomicInteger(0)

        // 首次拉取：第一页 4 条 [103, 102, 101, 100]，带远端下一页。
        val p1 = store.loadBookmarks("1", PixivRestrict.Public, null, ttlMs = 60_000L, fresh = false) { cursor ->
            calls.incrementAndGet()
            assertNull(cursor)
            Result.success(PixivAppPage(listOf(work(103), work(102), work(101), work(100)), "remote-p2"))
        }.getOrThrow()
        assertEquals(1, calls.get())
        assertEquals(listOf(103L, 102L, 101L), p1.works.map { it.post.id })
        assertTrue(store.isLocalCursor(p1.nextUrl))

        // 第二页走本地游标，零网络请求，且返回尾部的远端游标。
        val p2 = store.loadBookmarks("1", PixivRestrict.Public, p1.nextUrl, ttlMs = 60_000L, fresh = false) {
            calls.incrementAndGet()
            error("本地游标不应发网络请求")
        }.getOrThrow()
        assertEquals(1, calls.get())
        assertEquals(listOf(100L), p2.works.map { it.post.id })
        assertEquals("remote-p2", p2.nextUrl)

        // 增量刷新：站方第 1 页新增 105、104，且在网页端删掉了 102，返回 [105, 104, 103, 101] + nextUrl=remote-p99。
        // 命中旧锚点 103/101 后应立刻停在 1 次请求，不再拉 remote-p99，同时保留 100 与 tail=remote-p2。
        val synced = store.loadBookmarks("1", PixivRestrict.Public, null, ttlMs = 60_000L, fresh = true) { cursor ->
            calls.incrementAndGet()
            assertNull(cursor)
            Result.success(PixivAppPage(listOf(work(105), work(104), work(103), work(101)), "remote-p99"))
        }.getOrThrow()
        assertEquals(2, calls.get())
        assertEquals(listOf(105L, 104L, 103L), synced.works.map { it.post.id })

        val syncedNext = store.loadBookmarks("1", PixivRestrict.Public, synced.nextUrl, ttlMs = 60_000L, fresh = false) {
            error("本地游标不应发网络请求")
        }.getOrThrow()
        assertEquals(listOf(101L, 100L), syncedNext.works.map { it.post.id })
        assertEquals("remote-p2", syncedNext.nextUrl)

        // 原地取消收藏 104：无需重建全表，直接消失。
        store.removeBookmark(104L)
        val afterRemove = store.loadBookmarks("1", PixivRestrict.Public, null, ttlMs = 60_000L, fresh = false) {
            error("新鲜期内应走本地缓存")
        }.getOrThrow()
        assertEquals(listOf(105L, 103L, 101L), afterRemove.works.map { it.post.id })

        dir.deleteRecursively()
    }

    @Test
    fun `关注列表远端翻页追加进本地索引并在取关时原地移除`(): Unit = runBlocking {
        val dir = tmpDir("following")
        val store = DesktopPixivIncrementalStore(dir = dir, pageSize = 2)
        val calls = AtomicInteger(0)

        val p1 = store.loadFollowing("1", PixivRestrict.Public, null, ttlMs = 60_000L, fresh = false) {
            calls.incrementAndGet()
            Result.success(PixivUserPage(listOf(user(10), user(20)), "remote-u2"))
        }.getOrThrow()
        assertEquals(listOf(10L, 20L), p1.users.map { it.id })
        assertEquals("remote-u2", p1.nextUrl)

        val p2 = store.loadFollowing("1", PixivRestrict.Public, p1.nextUrl, ttlMs = 60_000L, fresh = false) { cursor ->
            calls.incrementAndGet()
            assertEquals("remote-u2", cursor)
            Result.success(PixivUserPage(listOf(user(30)), null))
        }.getOrThrow()
        assertEquals(listOf(30L), p2.users.map { it.id })
        assertEquals(2, calls.get())

        // 原地取关 20 后，新鲜期内拉取应只剩 [10, 30] 且零请求。
        store.removeFollowing(20L)
        val pageAfter = store.loadFollowing("1", PixivRestrict.Public, null, ttlMs = 60_000L, fresh = false) {
            error("新鲜期内不应发请求")
        }.getOrThrow()
        assertEquals(listOf(10L, 30L), pageAfter.users.map { it.id })
        assertNull(pageAfter.nextUrl)

        dir.deleteRecursively()
    }
}
