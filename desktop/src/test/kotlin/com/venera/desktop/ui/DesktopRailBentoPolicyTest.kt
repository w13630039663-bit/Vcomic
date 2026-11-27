package com.venera.desktop.ui

import com.venera.compose.components.VeneraNavTab
import com.venera.desktop.gallery.ui.DesktopChromeColors
import com.venera.desktop.gallery.ui.DesktopSourceTree
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌面浅皮轨 2×2 Bento 磁贴与悬浮底坞护栏单测。
 */
class DesktopRailBentoPolicyTest {

    @Test
    fun `primary tabs keep 2x2 order without explore`() {
        val tabs = desktopRailPrimaryTabs()
        assertEquals(
            listOf(VeneraNavTab.HOME, VeneraNavTab.SEARCH, VeneraNavTab.GALLERY, VeneraNavTab.FAVORITES),
            tabs,
        )
        assertEquals("首页", desktopRailTabTitle(VeneraNavTab.HOME))
        assertEquals("漫画搜索", desktopRailTabTitle(VeneraNavTab.SEARCH))
        assertEquals("画廊", desktopRailTabTitle(VeneraNavTab.GALLERY))
        assertEquals("收藏", desktopRailTabTitle(VeneraNavTab.FAVORITES))
    }

    @Test
    fun `glass surface stays translucent when rail blur active and falls back when solid`() {
        val light = DesktopChromeColors.Light
        val dark = DesktopChromeColors.Dark

        val lightGlassSelected = desktopRailGlassSurface(light, glassActive = true, selected = true, hovered = false)
        val darkGlassSelected = desktopRailGlassSurface(dark, glassActive = true, selected = true, hovered = false)
        assertTrue(lightGlassSelected.alpha in 0.1f..0.45f)
        assertTrue(darkGlassSelected.alpha in 0.05f..0.25f)

        val lightSolidSelected = desktopRailGlassSurface(light, glassActive = false, selected = true, hovered = false)
        assertEquals(light.railSelected, lightSolidSelected)
    }

    @Test
    fun `light rail skin delegates bento grid and floating dock without colored group badges`() {
        val railCode = DesktopSourceTree.codeText(
            File(DesktopSourceTree.repoRoot, "desktop/src/main/kotlin/com/venera/desktop/ui/VeneraLightRailSkin.kt"),
        )
        assertTrue(railCode.contains("DesktopRailBentoGrid("))
        assertTrue(railCode.contains("DesktopRailFloatingDock("))
        assertTrue(railCode.contains("DesktopRailMonoNavRow("))
        assertTrue(railCode.contains("hazeSource("))
        assertFalse(railCode.contains("LightGroupBadgeIcon"))
        assertFalse(railCode.contains("DesktopRailBadgeColors"))
        assertFalse(railCode.contains("MacFooterBar"))
    }
}
