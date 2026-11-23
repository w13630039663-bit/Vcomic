package com.venera.desktop.installer

import org.junit.Assert.assertEquals
import org.junit.Test

/** 宿主四态的解析与自动推进判据。 */
class DesktopInstallerHostTest {

    @Test
    fun `stage parse is case insensitive and falls back to splash`() {
        assertEquals(InstallerStage.SPLASH, installerStageOf(null))
        assertEquals(InstallerStage.SPLASH, installerStageOf(""))
        assertEquals(InstallerStage.SPLASH, installerStageOf("NOPE"))
        assertEquals(InstallerStage.OPTIONS, installerStageOf("options"))
        assertEquals(InstallerStage.INSTALLING, installerStageOf(" Installing "))
        assertEquals(InstallerStage.COMPLETE, installerStageOf("COMPLETE"))
    }

    @Test
    fun `splash advances to options at total duration`() {
        assertEquals(InstallerStage.SPLASH, installerAutoAdvance(InstallerStage.SPLASH, 0L))
        assertEquals(
            InstallerStage.SPLASH,
            installerAutoAdvance(InstallerStage.SPLASH, InstallerTimeline.SPLASH_TOTAL_MS - 1),
        )
        assertEquals(
            InstallerStage.OPTIONS,
            installerAutoAdvance(InstallerStage.SPLASH, InstallerTimeline.SPLASH_TOTAL_MS),
        )
    }

    @Test
    fun `installing advances to complete only after run and hold`() {
        val holdAt = InstallerTimeline.INSTALL_RUN_MS + InstallerTimeline.INSTALL_HOLD_MS
        assertEquals(InstallerStage.INSTALLING, installerAutoAdvance(InstallerStage.INSTALLING, 0L))
        assertEquals(
            InstallerStage.INSTALLING,
            installerAutoAdvance(InstallerStage.INSTALLING, InstallerTimeline.INSTALL_RUN_MS),
        )
        assertEquals(
            InstallerStage.COMPLETE,
            installerAutoAdvance(InstallerStage.INSTALLING, holdAt),
        )
    }

    @Test
    fun `options and complete never auto advance`() {
        val long = 60_000L
        assertEquals(InstallerStage.OPTIONS, installerAutoAdvance(InstallerStage.OPTIONS, long))
        assertEquals(InstallerStage.COMPLETE, installerAutoAdvance(InstallerStage.COMPLETE, long))
    }
}
