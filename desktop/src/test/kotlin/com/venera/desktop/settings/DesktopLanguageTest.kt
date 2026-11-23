package com.venera.desktop.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌面语言设置与中英双语切换的测试。
 */
class DesktopLanguageTest {

    @Test
    fun `恢复与切换语言`() {
        // 恢复为英文
        DesktopLanguage.restore(DesktopLanguage.EN)
        assertEquals(DesktopLanguage.EN, DesktopLanguage.mode.value)
        assertTrue(DesktopLanguage.isEnglish())
        assertEquals("English text", DesktopLanguage.tr("中文文本", "English text"))

        // 恢复为中文
        DesktopLanguage.restore(DesktopLanguage.ZH)
        assertEquals(DesktopLanguage.ZH, DesktopLanguage.mode.value)
        assertFalse(DesktopLanguage.isEnglish())
        assertEquals("中文文本", DesktopLanguage.tr("中文文本", "English text"))

        // 无效值恢复时忽略
        DesktopLanguage.restore("invalid_lang")
        assertEquals(DesktopLanguage.ZH, DesktopLanguage.mode.value)
    }

    @Test
    fun `设置分区中英双语标题与分组`() {
        DesktopLanguage.restore(DesktopLanguage.ZH)
        assertEquals("通用与外观", DesktopSettingsSection.APPEARANCE.title)
        assertEquals("浏览", DesktopSettingsSection.APPEARANCE.group)

        DesktopLanguage.restore(DesktopLanguage.EN)
        assertEquals("Appearance", DesktopSettingsSection.APPEARANCE.title)
        assertEquals("Browse", DesktopSettingsSection.APPEARANCE.group)

        // 恢复回中文出厂设置
        DesktopLanguage.restore(DesktopLanguage.ZH)
    }

    @Test
    fun `国际化词典常用词条与复合串映射`() {
        DesktopLanguage.restore(DesktopLanguage.EN)
        assertEquals("Home", DesktopI18n.tr("首页"))
        assertEquals("Search", DesktopI18n.tr("搜索"))
        assertEquals("Settings", DesktopI18n.tr("设置"))
        assertEquals("Downloads", DesktopI18n.tr("下载与本地"))
        assertEquals("Favorites & History", DesktopI18n.tr("收藏与历史"))
        assertEquals("Reading History", DesktopI18n.tr("阅读历史"))
        assertEquals("Edit Source · 示例源", DesktopI18n.tr("编辑源 · 示例源"))
        assertEquals("Login Source · 示例源", DesktopI18n.tr("登录源 · 示例源"))
        assertEquals("No settings matching 'test'.", DesktopI18n.tr("没有匹配「test」的设置项。"))

        DesktopLanguage.restore(DesktopLanguage.ZH)
        assertEquals("首页", DesktopI18n.tr("首页"))
        assertEquals("设置", DesktopI18n.tr("设置"))
        assertEquals("编辑源 · 示例源", DesktopI18n.tr("编辑源 · 示例源"))
    }
}
