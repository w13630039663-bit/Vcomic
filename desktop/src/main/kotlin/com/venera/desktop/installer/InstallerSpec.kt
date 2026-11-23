package com.venera.desktop.installer

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 安装向导的浅/深色主题色板。
 *
 * 强调色严格对齐官方桌面体系：浅色 `#0A63F6`、深色 `#0A84FF`。
 */
@Immutable
data class InstallerPalette(
    val isDark: Boolean,
    val accent: Color,
    val accentHover: Color,
    val accentSoft: Color,
    val windowBg: Color,
    val windowBorder: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceSubtle: Color,
    val surfaceHover: Color,
    val borderSubtle: Color,
    val borderStrong: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val badgeBg: Color,
    val badgeText: Color,
    val warnBg: Color,
    val warnText: Color,
    val warnBorder: Color,
    val danger: Color,
    val dangerHover: Color,
    val dangerSoft: Color,
    val dangerBorder: Color,
    val success: Color,
    val successSoft: Color,
    val orbPrimary: Color,
    val orbSecondary: Color,
    val scrim: Color,
)

val LocalInstallerPalette = compositionLocalOf { InstallerSpec.LightPalette }

/**
 * 安装向导 v3（原生云母玻璃风）的尺寸、圆角与双主题颜色常量。
 *
 * 规格出处：`docs/designs/installer-wizard-v3-2026-11-22/design-tokens.md`。
 */
object InstallerSpec {

    val CardWidth = 640.dp
    val CardHeight = 440.dp
    val ContentWidth = 584.dp
    val ContentHeight = 400.dp

    val CardShape = RoundedCornerShape(20.dp)
    val InnerCardShape = RoundedCornerShape(12.dp)
    val ControlShape = RoundedCornerShape(8.dp)
    val PrimaryButtonShape = RoundedCornerShape(10.dp)

    val TitleBarHeight = 40.dp
    val TitleBarDragHeight = 40.dp
    val PrimaryButtonWidth = 148.dp
    val PrimaryButtonHeight = 38.dp
    val SecondaryButtonHeight = 34.dp

    val LightPalette = InstallerPalette(
        isDark = false,
        accent = Color(0xFF0A63F6),
        accentHover = Color(0xFF0854D6),
        accentSoft = Color(0x1A0A63F6),
        // 系统材质在窗后合成，这层纱压到半透：既透得出云母的底色，又压得住背后的桌面
        // 系统材质在窗后合成，这层纱保持半透：透得出云母底色，也压得住背后的桌面
        windowBg = Color(0xA6F9FBFE),
        windowBorder = Color(0xD9FFFFFF),
        surface = Color(0xB8FFFFFF),
        surfaceElevated = Color(0xE6FFFFFF),
        surfaceSubtle = Color(0x090F172A),
        surfaceHover = Color(0x0F0F172A),
        borderSubtle = Color(0x140F172A),
        borderStrong = Color(0x240F172A),
        textPrimary = Color(0xFF0F172A),
        textSecondary = Color(0xFF475569),
        textTertiary = Color(0xFF64748B),
        badgeBg = Color(0x170A63F6),
        badgeText = Color(0xFF0A63F6),
        warnBg = Color(0x1FF59E0B),
        warnText = Color(0xFFB45309),
        warnBorder = Color(0x3DF59E0B),
        danger = Color(0xFFEF4444),
        dangerHover = Color(0xFFDC2626),
        dangerSoft = Color(0x1FEF4444),
        dangerBorder = Color(0x42EF4444),
        success = Color(0xFF10B981),
        successSoft = Color(0x1F10B981),
        orbPrimary = Color(0x290A63F6),
        orbSecondary = Color(0x2438BDF8),
        scrim = Color(0x470F172A),
    )

    val DarkPalette = InstallerPalette(
        isDark = true,
        accent = Color(0xFF0A84FF),
        accentHover = Color(0xFF2E96FF),
        accentSoft = Color(0x290A84FF),
        // 深色同样半透：材质降级到实底时靠这条保持可读
        windowBg = Color(0xA0161B24),
        windowBorder = Color(0x1CFFFFFF),
        surface = Color(0xAD222936),
        surfaceElevated = Color(0xD92A3242),
        surfaceSubtle = Color(0x0CFFFFFF),
        surfaceHover = Color(0x14FFFFFF),
        borderSubtle = Color(0x17FFFFFF),
        borderStrong = Color(0x29FFFFFF),
        textPrimary = Color(0xFFF1F5F9),
        textSecondary = Color(0xFF94A3B8),
        textTertiary = Color(0xFF64748B),
        badgeBg = Color(0x290A84FF),
        badgeText = Color(0xFF60A5FA),
        warnBg = Color(0x24F59E0B),
        warnText = Color(0xFFFBBF24),
        warnBorder = Color(0x40F59E0B),
        danger = Color(0xFFF87171),
        dangerHover = Color(0xFFEF4444),
        dangerSoft = Color(0x26F87171),
        dangerBorder = Color(0x47F87171),
        success = Color(0xFF34D399),
        successSoft = Color(0x2434D399),
        orbPrimary = Color(0x330A84FF),
        orbSecondary = Color(0x1F38BDF8),
        scrim = Color(0x85000000),
    )

    // 原版图标定稿色（浅深色统一白底黑笔）
    val IconBadgeSurface = Color(0xFFFFFFFF)
    val IconMarkStroke = Color(0xFF0B0D11)
    val OnAccent = Color(0xFFFFFFFF)
    val CloseHover = Color(0x24EF4444)
    val CloseForeground = Color(0xFFEF4444)
}
