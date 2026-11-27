package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GallerySite
import com.venera.desktop.gallery.data.DesktopGalleryPreferences
import com.venera.desktop.platform.FakePaths
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class DesktopGallerySearchPaginationTest {

    private fun fakeWallPage(page: Int): DesktopWallPage = DesktopWallPage(
        batch = DesktopGalleryBatch(
            seed = page.toLong(),
            pageBySite = mapOf(GallerySite.YANDERE to page),
            queries = mapOf(GallerySite.YANDERE to "sky"),
        ),
        cards = emptyList(),
    )

    @Test
    fun 页码输入解析只接受合法正整数() {
        assertEquals(1, parseSearchJumpPage("1"))
        assertEquals(12, parseSearchJumpPage(" 12 "))
        assertNull(parseSearchJumpPage(""))
        assertNull(parseSearchJumpPage("0"))
        assertNull(parseSearchJumpPage("-3"))
        assertNull(parseSearchJumpPage("2a"))
        assertNull(parseSearchJumpPage("10000"))
    }

    @Test
    fun 页缓存命中且上下文切换自动清空() {
        val cache = DesktopSearchPageCache(maxEntries = 3)
        cache.bindContext("sky|0")
        val p1 = fakeWallPage(1)
        val p2 = fakeWallPage(2)
        cache.put(1, p1)
        cache.put(2, p2)

        assertSame(p1, cache.get(1))
        assertSame(p2, cache.get(2))
        assertNull(cache.get(3))

        // 同上下文重复 bind 不清空
        cache.bindContext("sky|0")
        assertSame(p1, cache.get(1))

        // 切换上下文立刻清空
        cache.bindContext("ocean|0")
        assertNull(cache.get(1))
        assertNull(cache.get(2))
    }

    @Test
    fun 搜索翻页方式偏好落盘并在重启后恢复() {
        val dir = File(System.getProperty("java.io.tmpdir"), "venera-search-page-${System.nanoTime()}")
        try {
            val prefs = DesktopGalleryPreferences(FakePaths(dir))
            assertEquals(DesktopSearchPaginationMode.INFINITE, prefs.searchPaginationMode.value)

            prefs.setSearchPaginationMode(DesktopSearchPaginationMode.PAGED)
            assertEquals(DesktopSearchPaginationMode.PAGED, prefs.searchPaginationMode.value)

            val reopened = DesktopGalleryPreferences(FakePaths(dir))
            assertEquals(DesktopSearchPaginationMode.PAGED, reopened.searchPaginationMode.value)
        } finally {
            dir.deleteRecursively()
        }
    }
}
