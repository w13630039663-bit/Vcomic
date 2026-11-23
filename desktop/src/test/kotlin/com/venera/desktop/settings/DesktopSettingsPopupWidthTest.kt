package com.venera.desktop.settings

import com.venera.desktop.gallery.ui.DesktopSourceTree
import org.junit.Assert.assertTrue
import org.junit.Test

/** 钉住设置弹窗下拉不撑成整窗长条：菜单列宽必须收成内容宽。 */
class DesktopSettingsPopupWidthTest {

    private val shell = DesktopSourceTree.codeText(
        DesktopSourceTree.desktopSourceOf("settings/SettingsDialogShell.kt"),
    )

    @Test
    fun 选项浮层列宽收成内容宽() {
        assertTrue(
            "DialogOptionsPopup 的列必须 .width(IntrinsicSize.Min)，否则菜单项 fillMaxWidth 会吃满整窗：",
            ".width(IntrinsicSize.Min)" in shell,
        )
        assertTrue(
            "IntrinsicSize 必须真导入，否则编译期就退化成无限宽：",
            "import androidx.compose.foundation.layout.IntrinsicSize" in shell,
        )
    }
}
