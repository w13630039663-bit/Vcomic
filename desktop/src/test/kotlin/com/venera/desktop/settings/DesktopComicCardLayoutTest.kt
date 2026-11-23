package com.venera.desktop.settings

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 漫画卡片全局布局算式：自适应、自定义 x 行 × y 列、双列覆盖与边界钳制。 */
class DesktopComicCardLayoutTest {

    @Test
    fun `自适应模式按窗宽算网格列数且双列固定2列`() {
        val auto = DesktopComicCardLayoutConfig(mode = DesktopComicCardLayoutMode.AUTO)
        assertFalse(auto.isCustom)
        assertEquals(2, auto.resolveDoubleColumns())
        assertEquals(3, auto.resolveGridColumns(400.dp, 150.dp, 3, 6))
        assertEquals(5, auto.resolveGridColumns(800.dp, 150.dp, 3, 6))
        assertEquals(6, auto.resolveGridColumns(1200.dp, 150.dp, 3, 6))
        assertEquals(0.75f, auto.resolveCoverAspect(0.75f), 0.001f)
    }

    @Test
    fun `自定义行列同时覆盖网格与双列并钳制越界`() {
        val custom = DesktopComicCardLayoutConfig(
            mode = DesktopComicCardLayoutMode.CUSTOM,
            rows = 4,
            cols = 7,
        )
        assertTrue(custom.isCustom)
        assertEquals(7, custom.resolveGridColumns(400.dp))
        assertEquals(7, custom.resolveDoubleColumns())
        assertEquals(1.25f, custom.resolveCoverAspect(0.75f), 0.001f)

        val clamped = DesktopComicCardLayoutConfig(
            mode = DesktopComicCardLayoutMode.CUSTOM,
            rows = 99,
            cols = 0,
        )
        assertEquals(1, clamped.resolveGridColumns(1000.dp))
        assertEquals(1, clamped.resolveDoubleColumns())
        assertEquals(1.65f, clamped.resolveCoverAspect(0.75f), 0.001f)
    }
}
