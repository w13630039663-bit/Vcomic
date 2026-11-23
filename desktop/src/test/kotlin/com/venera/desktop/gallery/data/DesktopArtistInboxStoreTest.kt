package com.venera.desktop.gallery.data

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 收件箱存储的尺：迁移口径（旧文件读新键）、recordSync 与 markAllViewed 的分侧规格。
 */
class DesktopArtistInboxStoreTest {

    private fun tmpFile(tag: String): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "venera-inbox-$tag-${System.nanoTime()}")
        dir.mkdirs()
        return File(dir, "artist_inbox_state.json")
    }

    @Test
    fun `旧格式状态读入后新键取默认值且残留键解析安全`(): Unit = runBlocking {
        val file = tmpFile("legacy")
        // 旧格式的落盘形状：只有四枚旧键（残留 starred/snoozed 一并保留，ignoreUnknownKeys 不炸）
        file.writeText(
            """
            {"lastReadTimestamp":123,"readUids":["a","b"],"starredArtists":["kantoku"],"snoozedArtists":["hime"]}
            """.trimIndent(),
        )
        val store = DesktopArtistInboxStore(file)
        val state = store.state.value
        assertEquals(123L, state.lastReadTimestamp)
        assertEquals(setOf("a", "b"), state.readUids)
        assertEquals(setOf("kantoku"), state.starredArtists)
        assertEquals(setOf("hime"), state.snoozedArtists)
        // 迁移口径：无新键 → 水位空表，首屏全判新入库
        assertEquals(emptyMap<String, Long>(), state.galleryWatermarks)
        assertEquals(emptyMap<String, Long>(), state.pixivWatermarks)
        assertEquals(0L, state.lastSyncAt)
    }

    @Test
    fun `recordSync 只写 lastSyncAt 不碰水位与已读`(): Unit = runBlocking {
        val file = tmpFile("sync")
        val store = DesktopArtistInboxStore(file)
        store.markAllViewed(
            uids = listOf("yandere:kantoku"),
            galleryMax = mapOf("yandere:kantoku" to 100L),
            pixivMax = mapOf("kantoku" to 55L),
        )
        val before = store.state.value
        store.recordSync()
        val after = store.state.value
        assertTrue(after.lastSyncAt >= before.lastSyncAt && after.lastSyncAt > 0L)
        assertEquals(before.lastReadTimestamp, after.lastReadTimestamp)
        assertEquals(before.readUids, after.readUids)
        assertEquals(before.galleryWatermarks, after.galleryWatermarks)
        assertEquals(before.pixivWatermarks, after.pixivWatermarks)
    }

    @Test
    fun `markAllViewed 两参都非空时并集加全量水位推进`(): Unit = runBlocking {
        val file = tmpFile("both")
        val store = DesktopArtistInboxStore(file)
        store.markAllViewed(listOf("yandere:a"), mapOf("yandere:a" to 10L), mapOf("画师甲" to 7L))
        store.markAllViewed(
            listOf("yandere:a", "gelbooru:b"),
            mapOf("yandere:a" to 20L, "gelbooru:b" to 9L),
            mapOf("画师甲" to 30L, "画师乙" to 5L),
        )
        val state = store.state.value
        assertEquals(setOf("yandere:a", "gelbooru:b"), state.readUids)
        assertEquals(mapOf("yandere:a" to 20L, "gelbooru:b" to 9L), state.galleryWatermarks)
        // 每画师各取 max，只前进不后退
        assertEquals(mapOf("画师甲" to 30L, "画师乙" to 5L), state.pixivWatermarks)
        assertTrue(state.lastReadTimestamp > 0L)
    }

    @Test
    fun `markAllViewed 段内已读只推本侧不动另一源水位`(): Unit = runBlocking {
        val file = tmpFile("side")
        val store = DesktopArtistInboxStore(file)
        // 先把两源都立起水位
        store.markAllViewed(listOf("yandere:a"), mapOf("yandere:a" to 10L), mapOf("画师甲" to 40L))
        // 图库段内已读：pixivMax 传 null → pixivWatermarks 原样保留
        store.markAllViewed(listOf("yandere:b"), mapOf("yandere:b" to 88L), null)
        var state = store.state.value
        assertEquals(mapOf("yandere:a" to 10L, "yandere:b" to 88L), state.galleryWatermarks)
        assertEquals(mapOf("画师甲" to 40L), state.pixivWatermarks)
        // pixiv 段内已读：galleryMax 传 null → 图库水位原样保留；只推已读画师的水位
        store.markAllViewed(listOf("pixiv:x"), null, mapOf("画师乙" to 90L))
        state = store.state.value
        assertEquals(mapOf("yandere:a" to 10L, "yandere:b" to 88L), state.galleryWatermarks)
        assertEquals(mapOf("画师甲" to 40L, "画师乙" to 90L), state.pixivWatermarks)
        assertEquals(setOf("yandere:a", "yandere:b", "pixiv:x"), state.readUids)
    }
}
