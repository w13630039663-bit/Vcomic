package com.venera.desktop.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Venera Desktop 全局排版系统（字体）。
 *
 * ## 为什么要有这一层
 *
 * 的字号是散的：`VeneraTokens.type.*` 只给了 6 档尺寸、**没有行高/字距/字体族**，
 * 于是各屏自己写 `fontSize = 12.5.sp` / `FontWeight.Bold`，同一个「卡片标题」在画廊是 12.5、
 * 在详情页是 13、在设置是 14。这一层把「角色 → (字体族, 字号, 字重, 行高, 字距)」钉成一张表，
 * 页面只写角色，不再写数字。
 *
 * ## 规则（硬约束）
 *
 * 1. UI 代码里**不许**再出现裸 `fontSize = xx.sp` / `FontWeight.Bold`——取 [VeneraType] 的角色；
 * 2. 需要局部微调时用 `role.style().copy(...)`，并在注释里写清设计用途（唯一合法覆盖路径）；
 * 3. 数字读数（统计、计数）走 [VeneraType.statNumber]，不另造字号。
 *
 * ## 字体族
 *
 * 优先 MiSans Global（随包资源 `desktop/src/main/resources/fonts/MiSans-*.ttf`）。
 * **资源缺失时自动回退系统字体**（Windows: Segoe UI + 微软雅黑回退；macOS: 苹方），
 * 所以 CI / 干净 checkout 不会因为少字体而崩。字体授权说明见 `resources/fonts/README.md`
 * 与「关于与更新」页的第三方许可段。
 */
@Immutable
data class VeneraTextRole(
    val size: TextUnit,
    val weight: FontWeight,
    val lineHeight: TextUnit,
    val letterSpacing: TextUnit = 0.sp,
) {
    /** 生成本角色的 TextStyle（字体族由 [VeneraFonts.family] 单点提供）。 */
    fun style(): TextStyle = TextStyle(
        fontFamily = VeneraFonts.family,
        fontSize = size,
        fontWeight = weight,
        lineHeight = lineHeight,
        letterSpacing = letterSpacing,
    )
}

/**
 * 桌面端基础排版阶梯（主人给定值，不自行改数）。
 *
 * | 角色 | 字号 | 字重 | 行高 |
 * |---|---|---|---|
 * | 页面标题 pageTitle | 26 | 600 | 34 |
 * | 区块标题 sectionTitle | 18 | 600 | 26 |
 * | 卡片标题 cardTitle | 16 | 600 | 24 |
 * | 正文 body | 15 | 400 | 24 |
 * | 正文次要 bodySecondary | 14 | 400 | 22 |
 * | 按钮与导航 control | 14 | 500 | 22 |
 * | 辅助信息 aux | 13 | 400 | 20 |
 * | 微型标签 micro | 12 | 500 | 18 |
 * | 数字读数 statNumber | 20 | 600 | 26 |
 */
object VeneraType {

    /** 页面标题（一屏只有一个：设置 / 详情 / 播放器标题）。 */
    val pageTitle = VeneraTextRole(26.sp, FontWeight.SemiBold, 34.sp)

    /** 区块标题（分区头：设置分区、详情页段标题）。 */
    val sectionTitle = VeneraTextRole(18.sp, FontWeight.SemiBold, 26.sp)

    /** 卡片标题（卡头题名）。 */
    val cardTitle = VeneraTextRole(16.sp, FontWeight.SemiBold, 24.sp)

    /** 正文主要内容。 */
    val body = VeneraTextRole(15.sp, FontWeight.Normal, 24.sp)

    /** 正文次要内容（说明、列表副行）。 */
    val bodySecondary = VeneraTextRole(14.sp, FontWeight.Normal, 22.sp)

    /** 按钮与导航文字。 */
    val control = VeneraTextRole(14.sp, FontWeight.Medium, 22.sp)

    /** 辅助信息（时间、来源、计数、脚注）。 */
    val aux = VeneraTextRole(13.sp, FontWeight.Normal, 20.sp)

    /** 微型标签（角标、徽章、极小注）。 */
    val micro = VeneraTextRole(12.sp, FontWeight.Medium, 18.sp)

    /** 数字读数（统计大数、计数）。允许单独定义，但必须走这里，不许页面自造。 */
    val statNumber = VeneraTextRole(20.sp, FontWeight.SemiBold, 26.sp)

    /** 仪表盘大数字（设置卡里的主读数）。 */
    val statDisplay = VeneraTextRole(34.sp, FontWeight.SemiBold, 42.sp, letterSpacing = (-1).sp)

    /** Hero 题名（首页浅色档 / 详情页封面横幅上的作品名，比页面标题再大一档）。 */
    val heroTitle = VeneraTextRole(30.sp, FontWeight.Bold, 38.sp)
}

/**
 * 字体族单点。
 *
 * 加载顺序：`/fonts/MiSans-{Regular,Medium,SemiBold,Bold}.ttf` 四档齐 ⇒ 用 MiSans；
 * 任一缺 ⇒ [FontFamily.Default]（系统默认，Windows 上是 Segoe UI + 中文回退）。
 *
 * 为什么四档必须齐：只装一部分会让 SemiBold 被合成加粗（Skia 伪粗体），
 * 中文字形会糊——宁可整体回退，也不要半套。
 */
object VeneraFonts {

    private val weightFiles = listOf(
        "Regular" to FontWeight.Normal,
        "Medium" to FontWeight.Medium,
        "SemiBold" to FontWeight.SemiBold,
        "Bold" to FontWeight.Bold,
    )

    val family: FontFamily by lazy { loadBundled() ?: FontFamily.Default }

    /** 当前是否用上了随包字体（关于页如实念这一句，不假装）。 */
    val bundled: Boolean by lazy { loadBundled() != null }

    private fun loadBundled(): FontFamily? {
        val fonts = weightFiles.map { (name, weight) ->
            val path = "/fonts/MiSans-$name.ttf"
            if (VeneraFonts::class.java.getResourceAsStream(path) == null) return null
            Font(path, weight)
        }
        return FontFamily(fonts)
    }
}

/** 供预览/测试注入：默认即 [VeneraFonts.family]。 */
val LocalVeneraFontFamily = staticCompositionLocalOf { VeneraFonts.family }
