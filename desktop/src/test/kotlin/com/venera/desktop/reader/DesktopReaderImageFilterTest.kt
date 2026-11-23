package com.venera.desktop.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DesktopReaderImageFilterTest {

    @Test
    fun `新口径滤镜默认参数返回 null 避免额外渲染开销`() {
        assertNull(
            buildReaderFilterMatrix(
                brightness = 1f,
                contrast = 1f,
                saturation = 1f,
            ),
        )
        assertNotNull(
            buildReaderFilterMatrix(
                brightness = 1.5f,
                contrast = 1f,
                saturation = 1f,
            ),
        )
        assertNotNull(
            buildReaderFilterMatrix(
                brightness = 1f,
                contrast = 1f,
                saturation = 1f,
                grayscale = true,
                negative = true,
            ),
        )
    }

    @Test
    fun `色温与色相滑条滤镜生效且插值模式正确解析`() {
        assertNotNull(
            buildReaderFilterMatrix(
                brightness = 1f,
                contrast = 1f,
                saturation = 1f,
                sepiaStrength = 60f,
            ),
        )
        assertEquals(androidx.compose.ui.graphics.FilterQuality.Medium, resolveFilterQuality(0))
        assertEquals(androidx.compose.ui.graphics.FilterQuality.High, resolveFilterQuality(1))
        assertEquals(androidx.compose.ui.graphics.FilterQuality.None, resolveFilterQuality(2))
    }

    @Test
    fun `双页智能分组支持封面独占与宽图自动独占`() {
        assertEquals(
            listOf(listOf(0), listOf(1, 2), listOf(3), listOf(4, 5)),
            buildDoublePageGroups(pageCount = 6, coverSinglePage = true, widePageIndices = setOf(3)),
        )
        assertEquals(
            listOf(listOf(0, 1), listOf(2), listOf(3, 4)),
            buildDoublePageGroups(pageCount = 5, coverSinglePage = false, widePageIndices = setOf(2)),
        )
    }

    @Test
    fun `双页分组让宽图落在跨页起始位并补空白占位`() {
        // 宽图前面只凑到半对时补一个空位，宽图因此总在跨页的左始位；末尾单页同理。
        assertEquals(
            listOf(listOf(0, BLANK_PAGE), listOf(1), listOf(2, 3), listOf(4, BLANK_PAGE)),
            buildDoublePageGroups(pageCount = 5, coverSinglePage = false, widePageIndices = setOf(1)),
        )
        // 关掉对齐后不插空位，宽图前那半对原地成组。
        assertEquals(
            listOf(listOf(0), listOf(1), listOf(2, 3), listOf(4)),
            buildDoublePageGroups(
                pageCount = 5,
                coverSinglePage = false,
                widePageIndices = setOf(1),
                alignWithNextHorizontal = false,
            ),
        )
    }

    @Test
    fun `双向预加载索引优先向后并包含前一页`() {
        assertEquals(
            listOf(3, 4, 5, 6, 1),
            computePreloadIndices(currentPage = 2, totalPages = 10, preloadAhead = 4, preloadBehind = 1),
        )
        assertEquals(
            listOf(1, 2),
            computePreloadIndices(currentPage = 0, totalPages = 3, preloadAhead = 4, preloadBehind = 1),
        )
    }
}
