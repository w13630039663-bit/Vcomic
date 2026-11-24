package com.venera.desktop

import com.venera.desktop.ui.DesktopScrollAnchor
import com.venera.desktop.ui.putBoundedMapEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 验证轻量滚动锚点存取及有界缓存逐出逻辑。
 */
class DesktopScrollRetentionTest {

    @Test
    fun `scroll anchor saves clamps and resets indices`() {
        val anchor = DesktopScrollAnchor()
        assertEquals(0, anchor.index)
        assertEquals(0, anchor.offset)

        anchor.save(18, 240)
        assertEquals(18, anchor.index)
        assertEquals(240, anchor.offset)

        anchor.save(-5, -10)
        assertEquals(0, anchor.index)
        assertEquals(0, anchor.offset)

        anchor.save(7, 88)
        anchor.reset()
        assertEquals(0, anchor.index)
        assertEquals(0, anchor.offset)
    }

    @Test
    fun `putBoundedMapEntry evicts oldest entry when reaching cap`() {
        val cache = linkedMapOf<String, Int>()
        putBoundedMapEntry(cache, "a", 1, maxEntries = 3)
        putBoundedMapEntry(cache, "b", 2, maxEntries = 3)
        putBoundedMapEntry(cache, "c", 3, maxEntries = 3)
        putBoundedMapEntry(cache, "b", 20, maxEntries = 3)
        assertEquals(3, cache.size)
        assertEquals(20, cache["b"])

        putBoundedMapEntry(cache, "d", 4, maxEntries = 3)
        assertEquals(3, cache.size)
        assertFalse(cache.containsKey("a"))
        assertTrue(cache.containsKey("b"))
        assertTrue(cache.containsKey("c"))
        assertTrue(cache.containsKey("d"))
    }
}
