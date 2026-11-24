package com.venera.desktop.platform

import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopProtocolLinuxTest {

    @Test fun `生成的 Linux desktop 文件包含合法格式与 StartupWMClass 与 Scheme 关联`() {
        val text = DesktopProtocolRegistrar.linuxDesktopEntryText("/opt/venera/venera-desktop")
        assertTrue(text.contains("[Desktop Entry]"))
        assertTrue(text.contains("Exec=\"/opt/venera/venera-desktop\" %u") || text.contains("Exec=/opt/venera/venera-desktop %u"))
        assertTrue(text.contains("MimeType=x-scheme-handler/pixiv;x-scheme-handler/venera;"))
        assertTrue(text.contains("StartupWMClass=venera-desktop"))
        assertTrue(text.contains("Categories=Utility;Graphics;Viewer;"))
    }
}
