package com.venera.desktop.ui

import com.venera.desktop.gallery.ui.DesktopSourceTree
import com.venera.desktop.settings.DesktopRailWeatherMode
import com.venera.desktop.settings.DesktopRailWeatherService
import com.venera.desktop.settings.DesktopRailWeatherType
import com.venera.desktop.settings.DesktopThemeRevealPrefs
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 侧栏星穹天气开关、气象码映射与全窗转场护栏单测。
 */
class DesktopRailWeatherPolicyTest {

    @Test
    fun `wmo codes map to five weather buckets`() {
        assertEquals(DesktopRailWeatherType.CLEAR, DesktopRailWeatherService.mapWmoToWeatherType(0))
        assertEquals(DesktopRailWeatherType.CLEAR, DesktopRailWeatherService.mapWmoToWeatherType(1))
        assertEquals(DesktopRailWeatherType.CLOUDY, DesktopRailWeatherService.mapWmoToWeatherType(3))
        assertEquals(DesktopRailWeatherType.CLOUDY, DesktopRailWeatherService.mapWmoToWeatherType(45))
        assertEquals(DesktopRailWeatherType.RAIN, DesktopRailWeatherService.mapWmoToWeatherType(61))
        assertEquals(DesktopRailWeatherType.RAIN, DesktopRailWeatherService.mapWmoToWeatherType(80))
        assertEquals(DesktopRailWeatherType.SNOW, DesktopRailWeatherService.mapWmoToWeatherType(73))
        assertEquals(DesktopRailWeatherType.STORM, DesktopRailWeatherService.mapWmoToWeatherType(95))
    }

    @Test
    fun `theme reveal and weather options stay consistent`() {
        assertTrue(DesktopThemeRevealPrefs.STARBURST in DesktopThemeRevealPrefs.OPTIONS)
        assertTrue(DesktopThemeRevealPrefs.NONE in DesktopThemeRevealPrefs.OPTIONS)
        assertTrue(DesktopRailWeatherMode.AUTO in DesktopRailWeatherMode.OPTIONS)
        assertTrue(DesktopRailWeatherMode.CITY in DesktopRailWeatherMode.OPTIONS)
        assertTrue(DesktopRailWeatherMode.OFF in DesktopRailWeatherMode.OPTIONS)
    }

    @Test
    fun `light rail mounts theme switch in both expanded and collapsed rails`() {
        val railCode = DesktopSourceTree.codeText(
            File(DesktopSourceTree.repoRoot, "desktop/src/main/kotlin/com/venera/desktop/ui/VeneraLightRailSkin.kt"),
        )
        assertTrue(railCode.contains("DesktopRailThemeSwitch(expanded = true)"))
        assertTrue(railCode.contains("DesktopRailThemeSwitch("))
        assertTrue(railCode.contains("expanded = false"))
    }
}
