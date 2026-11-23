package com.venera.desktop.reader

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopReaderViewportTest {

    @Test
    fun `光标锚点滚轮缩放支持放大缩小与归零重置平移`() {
        val zoomedIn = computeCursorAnchoredZoom(
            currentZoom = 1f,
            currentPan = Offset.Zero,
            scrollDeltaY = -1f,
            cursorPosition = Offset(800f, 450f),
            viewportWidth = 1200f,
            viewportHeight = 900f,
        )
        assertTrue(zoomedIn.zoom > 1f)
        assertTrue(zoomedIn.pan.x < 0f)

        // 缩小允许越过 1x，下限是 READER_MIN_ZOOM；到下限时平移归零，缩小的内容不会再被拖出边框。
        val shrunk = computeCursorAnchoredZoom(
            currentZoom = 1.05f,
            currentPan = Offset(40f, -20f),
            scrollDeltaY = 1f,
            cursorPosition = Offset(600f, 450f),
            viewportWidth = 1200f,
            viewportHeight = 900f,
        )
        assertTrue(shrunk.zoom < 1f)
        assertEquals(Offset.Zero, shrunk.pan)

        val floor = computeCursorAnchoredZoom(
            currentZoom = READER_MIN_ZOOM,
            currentPan = Offset(40f, -20f),
            scrollDeltaY = 1f,
            cursorPosition = Offset(600f, 450f),
            viewportWidth = 1200f,
            viewportHeight = 900f,
        )
        assertEquals(READER_MIN_ZOOM, floor.zoom, 0.001f)
        assertEquals(Offset.Zero, floor.pan)
    }

    @Test
    fun `双击画布在 1x 与 2x 之间切换`() {
        val zoomed = toggleDoubleTapZoom(
            currentZoom = 1f,
            tapPosition = Offset(900f, 450f),
            viewportWidth = 1200f,
            viewportHeight = 900f,
        )
        assertEquals(2f, zoomed.zoom, 0.001f)
        assertTrue(zoomed.pan.x < 0f)

        val restored = toggleDoubleTapZoom(
            currentZoom = 2f,
            tapPosition = Offset(900f, 450f),
            viewportWidth = 1200f,
            viewportHeight = 900f,
        )
        assertEquals(1f, restored.zoom, 0.001f)
        assertEquals(Offset.Zero, restored.pan)
    }

    // 口径（2026-11-19 缩放下沉到页外层）：容器围绕中心缩放，
    // 可平移极限 = 视口 × (倍率 − 1) / 2，不再是凭经验系数。
    @Test
    fun `平移边界钳位在未放大时归零且放大时等于多出的那一半`() {
        assertEquals(
            Offset.Zero,
            clampPanOffset(Offset(500f, 500f), zoom = 1f, viewportWidth = 1000f, viewportHeight = 800f),
        )
        val clamped = clampPanOffset(
            pan = Offset(99999f, -99999f),
            zoom = 2f,
            viewportWidth = 1000f,
            viewportHeight = 800f,
        )
        assertEquals(500f, clamped.x, 0.01f)
        assertEquals(-400f, clamped.y, 0.01f)
    }
}
