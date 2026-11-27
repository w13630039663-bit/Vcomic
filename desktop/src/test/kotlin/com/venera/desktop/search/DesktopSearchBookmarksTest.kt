package com.venera.desktop.search

import com.venera.compose.data.platform.KeyValueStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopSearchBookmarksTest {

    private class InMemoryStore : KeyValueStore {
        private val data = mutableMapOf<String, Any>()
        override fun getString(k: String, d: String?): String? = data[k] as? String ?: d
        override fun getBoolean(k: String, d: Boolean): Boolean = data[k] as? Boolean ?: d
        override fun getInt(k: String, d: Int): Int = data[k] as? Int ?: d
        override fun getLong(k: String, d: Long): Long = data[k] as? Long ?: d
        override fun getFloat(k: String, d: Float): Float = data[k] as? Float ?: d
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(k: String): Set<String> = data[k] as? Set<String> ?: emptySet()
        override fun put(k: String, v: Any?) { if (v == null) data.remove(k) else data[k] = v }
        override fun remove(k: String) { data.remove(k) }
        override fun clear() { data.clear() }
        override fun keys(): Set<String> = data.keys.toSet()
    }

    @Test
    fun `add dedupes same search condition and persists via store`() {
        val store = InMemoryStore()
        val first = DesktopSearchBookmark(
            id = "b1",
            name = "EH · 中文同人",
            sourceKey = "ehentai",
            keyword = "language:chinese",
            selectedOptions = mapOf("Category" to listOf("1")),
            createdAt = 100L,
        )
        val duplicate = first.copy(id = "b2", name = "EH · 中文同人新版", createdAt = 200L)

        val list1 = desktopSearchBookmarksAdd(emptyList(), first)
        val list2 = desktopSearchBookmarksAdd(list1, duplicate)
        desktopSearchBookmarksWrite(store, list2)

        val loaded = desktopSearchBookmarksRead(store)
        assertEquals(1, loaded.size)
        assertEquals("b2", loaded.first().id)
        assertEquals("EH · 中文同人新版", loaded.first().name)
        assertEquals(listOf("1"), loaded.first().selectedOptions["Category"])
    }

    @Test
    fun `pin rename and delete behave deterministically`() {
        val a = DesktopSearchBookmark(id = "a", name = "A", sourceKey = "ehentai", keyword = "k1", createdAt = 100L)
        val b = DesktopSearchBookmark(id = "b", name = "B", sourceKey = "nhentai", keyword = "k2", createdAt = 200L)
        val initial = desktopSearchBookmarksSort(listOf(a, b))
        assertEquals(listOf("b", "a"), initial.map { it.id })

        val pinned = desktopSearchBookmarksTogglePin(initial, "a")
        assertEquals(listOf("a", "b"), pinned.map { it.id })
        assertTrue(pinned.first().pinned)

        val renamed = desktopSearchBookmarksRename(pinned, "a", "A-重命名")
        assertEquals("A-重命名", renamed.first().name)

        val deleted = desktopSearchBookmarksDelete(renamed, "a")
        assertEquals(listOf("b"), deleted.map { it.id })
        assertFalse(deleted.first().pinned)
    }

    @Test
    fun `default name formats single source and aggregate scopes`() {
        assertEquals("EH · parody:touhou", desktopSearchBookmarkDefaultName("parody:touhou", "EH", false))
        assertEquals("聚合 · 赛博朋克", desktopSearchBookmarkDefaultName("赛博朋克", "EH", true))
        assertEquals("EH · 筛选预设", desktopSearchBookmarkDefaultName("   ", "EH", false))
    }
}
