package com.venera.desktop.gallery.ui

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 圆角档位的看守尺（阶段收尾 · VDDS §3.3 形状列）。
 *
 * ## 档位口径
 *
 * 合法档 = 令牌档（6 / 10 / 12 / 16，GalleryTokens.Shape）+ MD3 档（4 / 8）+ 微档（2 / 3 / 5 / 7，
 * 徽标与细件的刻意小档）+ 胶囊（99 / 999）+ 设置域页型特色（20 / 24，Bento 玻璃舱）。
 *
 * 档外值（11 / 13 / 14 / 15 / 17 / 18 / 19 / 21）是从前「随手一挡」的产物 ——
 * 已把现存的 11/14/15 全部归档（33 处），本尺钉住**不再新增**：它们与令牌档只差 1dp，
 * 差的那 1dp 从来不是设计意图，是漂移。真实底色那批（%% 语法 / Shape.Pill / CircleShape）不在扫描范围。
 *
 * ## 为什么扫代码行
 *
 * 与 [DesktopSurfaceLiterals] 同一条纪律：判「不该存在的形状」没有可跑的对应物，只能扫源码。
 */
class DesktopShapeLiteralTest {

    private val offScale = listOf(
        "RoundedCornerShape(11.dp)", "RoundedCornerShape(13.dp)", "RoundedCornerShape(14.dp)",
        "RoundedCornerShape(15.dp)", "RoundedCornerShape(17.dp)", "RoundedCornerShape(18.dp)",
        "RoundedCornerShape(19.dp)", "RoundedCornerShape(21.dp)",
    )

    @Test
    fun cornerRadiusStaysOnScale() {
        val hits = DesktopSourceTree.desktopOwnSources()
            .filter { it.name !in setOf("GalleryTokens.kt", "DesktopTheme.kt") }
            // 安装向导（installer/）是独立视觉层：60/64dp 图标容器按设计稿取 18dp，
            // 与漫画 UI 的圆角令牌不是一套账（24afb1d0 起如此），不在本尺管辖面。
            .filterNot { it.parentFile?.name == "installer" }
            .flatMap { file ->
                DesktopSourceTree.codeLinesOf(file).mapNotNull { line ->
                    offScale.firstOrNull { line.contains(it) }?.let { file.name to it }
                }
            }
        assertTrue(
            "圆角只许用令牌档（6/10/12/16）+ MD3（4/8）+ 微档（2/3/5/7）+ 胶囊 + 设置特色（20/24），" +
                "档外值是漂移不是设计（2026-11-06 已归档 33 处）。回潮：\n" +
                hits.joinToString("\n") { (file, v) -> "  $file: $v" },
            hits.isEmpty(),
        )
    }
}
