package com.venera.desktop.architecture

import com.venera.desktop.ui.theme.VeneraFonts
import com.venera.desktop.ui.theme.VeneraType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 随包字体契约（字体）。
 *
 * 为什么值得一条判据：`VeneraFonts` 在字体缺失时**静默回退系统字体**——这是给 CI / 干净
 * checkout 的保命设计，但代价是「字体被误删」也照样编译通过、界面只是悄悄变回系统字体。
 * 这条把「四档必须真在 classpath 上」钉成会红的判据。
 */
class DesktopBundledFontTest {

    @Test
    fun `MiSans 四档必须随包且被真正启用`() {
        val missing = listOf("Regular", "Medium", "SemiBold", "Bold").filter { weight ->
            DesktopBundledFontTest::class.java.getResourceAsStream("/fonts/MiSans-$weight.ttf") == null
        }
        assertTrue(
            "这些字重没进 classpath（desktop/src/main/resources/fonts/）：$missing —— " +
                "缺档会让 VeneraFonts 整体回退系统字体，界面不会报错只是悄悄变样。",
            missing.isEmpty(),
        )
        assertTrue(
            "四档都在但 VeneraFonts 没启用（检查文件名大小写：必须是 MiSans-SemiBold.ttf）。",
            VeneraFonts.bundled,
        )
    }

    /** 阶梯钉死：给定的九个值，改数必须同步改这条。 */
    @Test
    fun `排版阶梯的九档与给定值一致`() {
        assertEquals("页面标题", 26f, VeneraType.pageTitle.size.value, 0.01f)
        assertEquals("区块标题", 18f, VeneraType.sectionTitle.size.value, 0.01f)
        assertEquals("卡片标题", 16f, VeneraType.cardTitle.size.value, 0.01f)
        assertEquals("正文", 15f, VeneraType.body.size.value, 0.01f)
        assertEquals("正文次要", 14f, VeneraType.bodySecondary.size.value, 0.01f)
        assertEquals("按钮与导航", 14f, VeneraType.control.size.value, 0.01f)
        assertEquals("辅助信息", 13f, VeneraType.aux.size.value, 0.01f)
        assertEquals("微型标签", 12f, VeneraType.micro.size.value, 0.01f)
        assertEquals("行高：页面标题 34", 34f, VeneraType.pageTitle.lineHeight.value, 0.01f)
        assertEquals("行高：正文 24", 24f, VeneraType.body.lineHeight.value, 0.01f)
    }
}
