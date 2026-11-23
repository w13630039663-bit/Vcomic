package com.venera.desktop.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Color
import java.awt.Graphics2D
import java.awt.image.BufferedImage

class DesktopGuidedViewEngineTest {

    private fun page(width: Int, height: Int, block: Graphics2D.() -> Unit): BufferedImage {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = Color.WHITE
        g.fillRect(0, 0, width, height)
        g.block()
        g.dispose()
        return img
    }

    private fun Graphics2D.panel(x: Int, y: Int, w: Int, h: Int) {
        color = Color(140, 140, 140)
        fillRect(x, y, w, h)
        color = Color.BLACK
        drawRect(x, y, w - 1, h - 1)
    }

    private fun assertRect(expected: FloatArray, actual: ComicPanelRect, delta: Float = 0.01f) {
        assertEquals("left", expected[0], actual.left, delta)
        assertEquals("top", expected[1], actual.top, delta)
        assertEquals("right", expected[2], actual.right, delta)
        assertEquals("bottom", expected[3], actual.bottom, delta)
    }

    @Test
    fun `日漫四象限降级策略按右上左上右下左下顺次浏览`() {
        val panels = DesktopGuidedViewEngine.fallbackQuadrants(rightToLeft = true)
        assertEquals(4, panels.size)
        // 第一格右上
        assertEquals(0.5f, panels[0].left)
        assertEquals(0f, panels[0].top)
        // 第二格左上
        assertEquals(0f, panels[1].left)
        assertEquals(0f, panels[1].top)
        // 第三格右下
        assertEquals(0.5f, panels[2].left)
        assertEquals(0.5f, panels[2].top)
        // 第四格左下
        assertEquals(0f, panels[3].left)
        assertEquals(0.5f, panels[3].top)
    }

    @Test
    fun `计算画格聚焦平移量居中视口`() {
        val panel = ComicPanelRect(0.25f, 0.25f, 0.75f, 0.75f)
        val transform = DesktopGuidedViewEngine.computePanelFocusTransform(
            panel = panel,
            viewportWidth = 1000f,
            viewportHeight = 800f,
        )
        assertEquals(2f, transform.zoom)
        // 中心点重合，位移应为 0
        assertEquals(0f, transform.pan.x)
        assertEquals(0f, transform.pan.y)
    }

    @Test
    fun `右至左时四宫格按右上左上右下左下返回`() {
        val img = page(480, 640) {
            panel(20, 20, 208, 288)
            panel(252, 20, 208, 288)
            panel(20, 332, 208, 288)
            panel(252, 332, 208, 288)
        }
        val panels = DesktopGuidedViewEngine.detectPanels(img, rightToLeft = true)
        assertEquals(4, panels.size)
        assertRect(floatArrayOf(0.525f, 0.031f, 0.958f, 0.481f), panels[0])
        assertRect(floatArrayOf(0.042f, 0.031f, 0.475f, 0.481f), panels[1])
        assertRect(floatArrayOf(0.525f, 0.519f, 0.958f, 0.969f), panels[2])
        assertRect(floatArrayOf(0.042f, 0.519f, 0.475f, 0.969f), panels[3])
    }

    @Test
    fun `左至右时四宫格按左上右上左下右下返回`() {
        val img = page(480, 640) {
            panel(20, 20, 208, 288)
            panel(252, 20, 208, 288)
            panel(20, 332, 208, 288)
            panel(252, 332, 208, 288)
        }
        val panels = DesktopGuidedViewEngine.detectPanels(img, rightToLeft = false)
        assertEquals(4, panels.size)
        assertRect(floatArrayOf(0.042f, 0.031f, 0.475f, 0.481f), panels[0])
        assertRect(floatArrayOf(0.525f, 0.031f, 0.958f, 0.481f), panels[1])
        assertRect(floatArrayOf(0.042f, 0.519f, 0.475f, 0.969f), panels[2])
        assertRect(floatArrayOf(0.525f, 0.519f, 0.958f, 0.969f), panels[3])
    }

    @Test
    fun `带黑色扫描边框的页面仍按纸色找沟壑`() {
        val img = page(480, 640) {
            color = Color.BLACK
            fillRect(0, 0, 480, 12)
            fillRect(0, 628, 480, 12)
            fillRect(0, 0, 12, 640)
            fillRect(468, 0, 12, 640)
            panel(20, 20, 208, 288)
            panel(252, 20, 208, 288)
            panel(20, 332, 208, 288)
            panel(252, 332, 208, 288)
        }
        val panels = DesktopGuidedViewEngine.detectPanels(img, rightToLeft = true)
        assertEquals(4, panels.size)
        assertRect(floatArrayOf(0.525f, 0.031f, 0.958f, 0.481f), panels[0])
    }

    @Test
    fun `整页通栏无沟壑时退回整页`() {
        val img = BufferedImage(480, 640, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until 640) {
            for (x in 0 until 480) {
                val dark = ((x / 8) + (y / 8)) % 2 == 0
                img.setRGB(x, y, if (dark) 0x303030 else 0xC0C0C0)
            }
        }
        val panels = DesktopGuidedViewEngine.detectPanels(img, rightToLeft = true)
        assertEquals(1, panels.size)
        assertTrue(panels[0].isFullPage)
    }

    @Test
    fun `竖向三条按自上而下返回`() {
        val img = page(480, 640) {
            panel(20, 20, 440, 184)
            panel(20, 228, 440, 184)
            panel(20, 436, 440, 184)
        }
        val panels = DesktopGuidedViewEngine.detectPanels(img, rightToLeft = true)
        assertEquals(3, panels.size)
        assertTrue("首条在上", panels[0].top < panels[1].top)
        assertTrue("末条在下", panels[1].top < panels[2].top)
    }

    @Test
    fun `跨页拼接把两页分格映射到各自半区`() {
        val left = listOf(ComicPanelRect(0f, 0f, 1f, 0.5f))
        val right = listOf(ComicPanelRect(0f, 0.5f, 1f, 1f))
        val merged = DesktopGuidedViewEngine.composeSpreadPanels(
            pages = listOf(left, right),
            ratios = listOf(0.7f, 0.7f),
        )
        assertEquals(2, merged.size)
        assertEquals(0f, merged[0].left, 0.001f)
        assertEquals(0.5f, merged[0].right, 0.001f)
        assertEquals(0.5f, merged[1].left, 0.001f)
        assertEquals(1f, merged[1].right, 0.001f)
    }

    @Test
    fun `跨页拼接按各页宽高比分配宽度`() {
        val wide = listOf(ComicPanelRect(0f, 0f, 1f, 1f))
        val narrow = listOf(ComicPanelRect(0f, 0f, 1f, 1f))
        val merged = DesktopGuidedViewEngine.composeSpreadPanels(
            pages = listOf(wide, narrow),
            ratios = listOf(1.5f, 0.5f),
        )
        assertEquals(2, merged.size)
        assertEquals(0.75f, merged[0].right, 0.001f)
        assertEquals(0.75f, merged[1].left, 0.001f)
        assertEquals(1f, merged[1].right, 0.001f)
    }

    @Test
    fun `跨越沟壑的杂线不会把真沟壑切碎`() {
        // 真沟壑中间常有一条画到框外的杂线，沟壑被切成两段，两段都短于最小连续行数；
        // 不吸收的话这条线会让整页退成 1 个分格。
        val img = page(480, 640) {
            panel(20, 20, 440, 280)
            panel(20, 340, 440, 280)
            color = Color.BLACK
            fillRect(20, 302, 440, 4)
        }
        val panels = DesktopGuidedViewEngine.detectPanels(img, rightToLeft = true)
        assertEquals(2, panels.size)
        assertTrue("上格在上", panels[0].top < panels[1].top)
    }
}
