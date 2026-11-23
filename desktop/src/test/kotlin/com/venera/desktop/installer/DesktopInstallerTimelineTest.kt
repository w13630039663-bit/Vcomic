package com.venera.desktop.installer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopInstallerTimelineTest {

    @Test
    fun `emblem 31 pieces complete strictly within 800ms`() {
        assertEquals(31, InstallerTimeline.EMBLEM_PIECES)
        assertEquals(800L, InstallerTimeline.EMBLEM_DRAW_MS)
        assertEquals(0f, InstallerTimeline.emblemPieceProgress(0L, 30))
        assertEquals(1f, InstallerTimeline.emblemPieceProgress(800L, 0))
        assertEquals(1f, InstallerTimeline.emblemPieceProgress(800L, 30))
    }

    @Test
    fun `splash wordmark and settle timings match spec`() {
        assertEquals(0f, InstallerTimeline.wordmarkProgress(800L))
        assertEquals(1f, InstallerTimeline.wordmarkProgress(1100L))
        assertEquals(0f, InstallerTimeline.splashFadeProgress(1250L))
        assertEquals(1f, InstallerTimeline.splashFadeProgress(1550L))
    }

    @Test
    fun `install progress over 12s is strictly monotonic`() {
        assertEquals(12_000L, InstallerTimeline.INSTALL_RUN_MS)
        var prev = InstallerTimeline.installProgress(0L)
        assertEquals(0f, prev)
        for (ms in 200L..12_000L step 200L) {
            val cur = InstallerTimeline.installProgress(ms)
            assertTrue("Expected monotonic increase at ${ms}ms: prev=$prev cur=$cur", cur > prev)
            prev = cur
        }
        assertEquals(1f, prev)
    }

    @Test
    fun `five install scenes cycle every 6s with 1s fade in, 4s hold, 1s fade out`() {
        assertEquals(5, InstallerCopy.installScenes.size)
        assertEquals(0, InstallerTimeline.sceneIndex(0L))
        assertEquals(4, InstallerTimeline.sceneIndex(24_000L))
        assertEquals(0, InstallerTimeline.sceneIndex(30_000L))

        assertEquals(0f, InstallerTimeline.sceneAlpha(0L))
        assertEquals(1f, InstallerTimeline.sceneAlpha(1_000L))
        assertEquals(1f, InstallerTimeline.sceneAlpha(5_000L))
        assertEquals(0f, InstallerTimeline.sceneAlpha(6_000L))

        InstallerCopy.installScenes.forEach { item ->
            assertTrue("主标题超长: ${item.title}", item.title.length <= 6)
            assertTrue("副标题超长: ${item.subtitle}", item.subtitle.length <= 15)
        }
    }

    @Test
    fun `bob amplitude and ambient cycles match spec`() {
        assertEquals(33f, InstallerTimeline.BOB_AMPLITUDE)
        assertEquals(2_000L, InstallerTimeline.BOB_CYCLE_MS)
        assertEquals(6_000L, InstallerTimeline.BACK_LIGHT_DRIFT_MS)
        assertEquals(10_000L, InstallerTimeline.BACK_LIGHT_BREATH_MS)
        assertEquals(300L, InstallerTimeline.PATH_EXPAND_MS)
        assertEquals(120, InstallerTimeline.BUTTON_PRESS_MS)
        assertEquals(0.97f, InstallerTimeline.BUTTON_PRESS_SCALE)
    }
}
