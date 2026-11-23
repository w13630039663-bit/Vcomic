package com.venera.desktop.ui

import com.venera.compose.components.WideScreenLayoutMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopPageWidthPolicyTest {

    @Test
    fun browse_is_wider_than_detail_and_read() {
        val browse = desktopPageMaxWidth(DesktopPageWidth.Browse, WideScreenLayoutMode.Expanded)
        val detail = desktopPageMaxWidth(DesktopPageWidth.Detail, WideScreenLayoutMode.Expanded)
        val read = desktopPageMaxWidth(DesktopPageWidth.Read, WideScreenLayoutMode.Expanded)

        requireNotNull(browse)
        requireNotNull(detail)
        requireNotNull(read)
        assertTrue(browse > detail)
        assertTrue(detail > read)
    }

    @Test
    fun browse_keeps_wide_desktop_budget() {
        assertEquals(
            1680f,
            desktopPageMaxWidth(DesktopPageWidth.Browse, WideScreenLayoutMode.Medium)?.value ?: -1f,
            0.01f,
        )
    }

    @Test
    fun full_has_no_cap() {
        assertNull(desktopPageMaxWidth(DesktopPageWidth.Full, WideScreenLayoutMode.Expanded))
    }
}
