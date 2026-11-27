package com.venera.desktop.pixiv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pixiv 作品详情页画框比例回退与实测修正单测。
 */
class DesktopPixivWorkCanvasRatioTest {

    @Test
    fun `returns null when neither decoded nor reported size is available`() {
        assertNull(desktopResolvePixivCanvasRatio(decodedRatio = null, reportedWidth = 0, reportedHeight = 0))
        assertNull(desktopResolvePixivCanvasRatio(decodedRatio = Float.NaN, reportedWidth = 0, reportedHeight = 800))
    }

    @Test
    fun `falls back to reported size before image decoding finishes`() {
        val ratio = desktopResolvePixivCanvasRatio(decodedRatio = null, reportedWidth = 1000, reportedHeight = 1400)
        assertEquals(1000f / 1400f, ratio!!, 0.0001f)
    }

    @Test
    fun `decoded pixel ratio overrides mismatched or missing reported ratio`() {
        val overridden = desktopResolvePixivCanvasRatio(decodedRatio = 16f / 9f, reportedWidth = 800, reportedHeight = 1200)
        assertEquals(16f / 9f, overridden!!, 0.0001f)

        val missingReported = desktopResolvePixivCanvasRatio(decodedRatio = 1200f / 800f, reportedWidth = 0, reportedHeight = 0)
        assertEquals(1.5f, missingReported!!, 0.0001f)
    }
}
