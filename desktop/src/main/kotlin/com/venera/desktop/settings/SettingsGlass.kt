package com.venera.desktop.settings

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 设置中枢的玻璃双档令牌。
 *
 * 深浅两档一套形状：舱墙/玻璃面板只认这份调色板，不直接摸色值 ——
 * 浅色模式（档二落真 appearancePrefs）接进来时只换 [LocalSettingsGlass] 的供给，
 * 皮肤件一个字不用改。主色在浅色档加深（#6f5fe0）以保 4.5:1 对比。
 */
data class SettingsGlassPalette(
    /** 舱/面板的玻璃底。 */
    val glassFill: Color,
    /** 玻璃边框。 */
    val glassBorder: Color,
    /** 聚焦/悬停时的边框提亮。 */
    val glassBorderFocus: Color,
    /** 顶部 1px 高光线。 */
    val glassHighlight: Color,
    /** 景深投影色。 */
    val shadow: Color,
    /** 景深底三段（radial 中心 → 中段 → 边缘）。 */
    val bgCore: Color,
    val bgMid: Color,
    val bgEdge: Color,
    /** 焦点光晕。 */
    val halo: Color,
    /** 主色（浅色档为加深档）。 */
    val primary: Color,
    /** 主色的淡档（柱图底/开关轨）。 */
    val primaryDim: Color,
    /** 中性轨道 / 输入框底 / 未选中数据柱（不铺蓝）。 */
    val track: Color,
    /** 文字三档。 */
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    /** 语义色。 */
    val ok: Color,
    val warn: Color,
    val danger: Color,
) {
    companion object {
        /** 深色档（当前默认）。 */
        val Deep = SettingsGlassPalette(
            glassFill = Color(0xFF1D2330),
            glassBorder = Color(0x00000000),
            glassBorderFocus = Color(0xFF5B9CFF).copy(alpha = 0.60f),
            glassHighlight = Color(0xFF2C3648),
            shadow = Color(0xFF000000),
            bgCore = Color(0xFF0A0D14),
            bgMid = Color(0xFF0A0D14),
            bgEdge = Color(0xFF0A0D14),
            halo = Color(0xFF5B9CFF).copy(alpha = 0.14f),
            primary = Color(0xFF5B9CFF),
            primaryDim = Color(0xFF5B9CFF).copy(alpha = 0.20f),
            track = Color(0xFF252E3E),
            textPrimary = Color(0xFFEAF0FA),
            textSecondary = Color(0xFFA9B6CC),
            textTertiary = Color(0xFF73839C),
            ok = Color(0xFF4ADE80),
            warn = Color(0xFFFBBF24),
            danger = Color(0xFFF87171),
        )

        /** 浅色档（档二接浅色模式时由外壳喂入）。 */
        val Light = SettingsGlassPalette(
            glassFill = Color.White,
            glassBorder = Color(0x00000000),
            glassBorderFocus = Color(0xFF0A63F6).copy(alpha = 0.55f),
            glassHighlight = Color(0xFFE2E8F0),
            shadow = Color(0xFF101828),
            bgCore = Color(0xFFE9ECF0),
            bgMid = Color(0xFFE9ECF0),
            bgEdge = Color(0xFFE9ECF0),
            halo = Color(0xFF0A63F6).copy(alpha = 0.10f),
            primary = Color(0xFF0A63F6),
            primaryDim = Color(0xFF0A63F6).copy(alpha = 0.18f),
            track = Color(0xFFEEF2F7),
            textPrimary = Color(0xFF1E293B),
            textSecondary = Color(0xFF64748B),
            textTertiary = Color(0xFF94A3B8),
            ok = Color(0xFF16A34A),
            warn = Color(0xFFEA580C),
            danger = Color(0xFFDC2626),
        )
    }
}

/** 玻璃令牌的供给点：外壳按主题模式喂 Deep/Light，皮肤件只从这里读。 */
val LocalSettingsGlass = staticCompositionLocalOf { SettingsGlassPalette.Deep }

/**
 * 桌面固定主色（主人明令撤强调色档，只保留深浅模式）。
 *
 * 取原六档的出厂蓝档（`0xFF0A63F6`）：撤档前主人屏上就是它，撤档后主色恒定、视觉零变化。
 * 色值住在本文件（色值之家），主题薄桥只引用常量不出色值（`DesktopThemeLayerTest` ③ 的口径）。
 */
val DesktopFixedAccent = Color(0xFF0A63F6)

/** 按主题明暗取玻璃令牌；强调色档已撤，不再注入合成。 */
fun settingsGlassFor(dark: Boolean): SettingsGlassPalette =
    if (dark) SettingsGlassPalette.Deep else SettingsGlassPalette.Light
