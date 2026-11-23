package com.venera.desktop.gallery.ui

import com.venera.desktop.ui.theme.VeneraType
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.ui.tokens.LocalVeneraDarkTheme

/**
 * 画廊色板（阶段 0 · 撤强调色档后改**深浅两套**）。
 *
 * - **深色档 `Deep` = 基准页现值逐项冻结**：主人屏上的样子不变，这里一个值都不许动；
 * - **浅色档 `Light` = 新定对位**（估算值，待主人截图验收）：按深色档的层级关系反转亮度，
 *   主色用 `0xFF0A63F6`（与全局固定主色同源、白底对比 ≈ 6.3:1）；深色档主色保持 `0xFF4D9FFF`
 *   不动（基准页视觉冻结，两档不同值是刻意的，不合并）；
 * - `StatusBadgeR18` 是深底徽章，不随主题反转（深底白字在浅色页上依旧清晰）；
 * - 消费点语法不变：`GalleryTokens.Color.X` 由 [GalleryTokens.Color] 的 @Composable getter
 * 按明暗取档，读数走 `LocalVeneraDarkTheme`（桌面壳接真偏好）。
 */
internal data class GalleryColors(
    val Background: Color,
    val SurfaceLevel1: Color,
    val SurfaceLevel2: Color,
    val SurfaceLevel3: Color,
    val SurfaceGlass: Color,
    val OutlineHairline: Color,
    val OutlineHover: Color,
    val OutlineSelected: Color,
    val Divider: Color,
    /** 图片自身的 1px 淡框（浅色档防白底画贴白卡融合；深色档 Transparent = 不画）。 */
    val ImageHairline: Color,
    /** 图上深色浮层底（阶段 2.1 收编）：深浅两档恒深、不随浅色翻白 ——
     *  跟 SurfaceGlass 的「图上浮层」同族语义（动作坞/角标/徽章胶囊）。
     *  消费者：漫画搜索、探索、网络收藏三屏的图上角标（从前各写一遍 0xD007090C）。 */
    val OverlayScrim: Color,
    /** 状态三色（阶段 2.3 收编）：危险 / 警告 / 安全 —— 评分档与下载域 DqTone
     *  的语义真身，从前 F28B82/F2B87C/78D6C0 各写一遍。两档同值恒定（状态色不随主题反转）。 */
    val ToneDanger: Color,
    val ToneCaution: Color,
    val ToneSafe: Color,
    /** 卡片投影色（设置卡的浮起效果用到画廊）——浅色档卡片阴影的
     *  spotColor（与设置域玻璃板 shadow 槽同值 0xFF101828）；深色档卡片无阴影，不消费。 */
    val ShadowSpot: Color,
    val TextPrimary: Color,
    val TextSecondary: Color,
    val TextTertiary: Color,
    val TextOnAccent: Color,
    val AccentPrimary: Color,
    val AccentSoft: Color,
    val StatusFavorite: Color,
    val StatusDanger: Color,
    val StatusSuccess: Color,
) {
    companion object {
        /** 深色档（基准页现值冻结，前的屏上原样）。 */
        val Deep = GalleryColors(
            Background = Color(0xFF0A0D12),
            SurfaceLevel1 = Color(0xFF12161F),
            SurfaceLevel2 = Color(0xFF161B26),
            SurfaceLevel3 = Color(0xFF1B2130),
            SurfaceGlass = Color(0xD912161F),
            OutlineHairline = Color(0x0FFFFFFF), // 6% white
            OutlineHover = Color(0x29FFFFFF),    // 16% white
            OutlineSelected = Color(0xFF4D9FFF),
            Divider = Color(0x14FFFFFF),         // 8% white
            ImageHairline = Color.Transparent,   // 深色下白画对深卡天然分明，不画图框（深色冻结）
            OverlayScrim = Color(0xD007090C),    // 图上浮层深底（基准页族现值冻结）
            ToneDanger = Color(0xFFF28B82),
            ToneCaution = Color(0xFFF2B87C),
            ToneSafe = Color(0xFF78D6C0),
            ShadowSpot = Color.Black,
            TextPrimary = Color(0xFFE8ECF4),
            TextSecondary = Color(0xFF98A2B6),
            TextTertiary = Color(0xFF6F7890),
            TextOnAccent = Color.White,
            AccentPrimary = Color(0xFF4D9FFF),
            AccentSoft = Color(0x294D9FFF),
            StatusFavorite = Color(0xFFE26D8A),
            StatusDanger = Color(0xFFFF6B6B),
            StatusSuccess = Color(0xFF3ECF8E),
        )

        /** 浅色档（验收二轮再校）。 */
        val Light = GalleryColors(
            // 底色压暗一档（主人验收「通风道要清晰」）：F3F5F9 与白卡色差太小，
            // 缝隙读不出来。保蓝灰族不转中性灰（与深色档 #0A0D12 同族色温）。
            Background = Color(0xFFF0F2F6),
            // 纯白卡底用具名常量：`Color(0xFFFFFFFF)` 字面量会撞 DesktopSurfaceLiterals 的白字前缀
            // （那把尺只放行 DesktopTheme.kt），Color.White 不在扫描范围且语义更准。
            SurfaceLevel1 = Color.White,
            SurfaceLevel2 = Color(0xFFE8ECF2),
            SurfaceLevel3 = Color(0xFFDBE2EC),
            // 图上浮层玻璃（唯一消费者：墙卡悬停动作坞）。恒深、不随浅色翻白 ——
            // 浮在画作上的控件跟 pixivision 卡的渐变护罩同一族：白玻璃叠白图标会糊成
            // 一团白（主人截图指认），浅色下也用深玻璃保白图标可读。
            SurfaceGlass = Color(0xD91B2130),
            // 描边两档（主人验收指认「卡片对比度太低」后加深）：
            // 白卡贴浅底只靠 6% 墨线边界发虚，常态提到 18%、hover 28%（保持 hover > 常态的层级差）。
            OutlineHairline = Color(0x2E17202E), // 18% ink
            OutlineHover = Color(0x4717202E),    // 28% ink
            OutlineSelected = Color(0xFF0A63F6),
            Divider = Color(0x1417202E),         // 8% ink
            ImageHairline = Color(0x1417202E),   // 8% ink ≈ 稿 #EAEAEA：图与白卡之间一圈淡框
            OverlayScrim = Color(0xD91B2130),    // 图上浮层恒深（不随浅色翻白，同 SurfaceGlass 口径）
            ToneDanger = Color(0xFFF28B82),
            ToneCaution = Color(0xFFF2B87C),
            ToneSafe = Color(0xFF78D6C0),
            ShadowSpot = Color(0xFF101828), // 与设置域玻璃板 shadow 槽同值（同一套卡片浮起语言）
            TextPrimary = Color(0xFF17202E),
            TextSecondary = Color(0xFF55617A),
            TextTertiary = Color(0xFF8791A6),
            TextOnAccent = Color.White,
            AccentPrimary = Color(0xFF0A63F6),
            AccentSoft = Color(0x292563EB),
            StatusFavorite = Color(0xFFC2436A),
            StatusDanger = Color(0xFFD64545),
            StatusSuccess = Color(0xFF1E9E63),
        )
    }
}

/**
 * 画廊字阶表（随色板两套：深色档绑深色文字，浅色档绑浅色文字）。
 * 字号 / 字重 / 行高两档**完全一致**，只有 color 跟档 —— 文字几何不随主题变。
 */
internal data class GalleryTypography(
    val ArticleTitle: TextStyle,
    val ArticleLead: TextStyle,
    val CardTitle: TextStyle,
    val ButtonLabel: TextStyle,
)

private fun galleryTypographyOf(c: GalleryColors): GalleryTypography = GalleryTypography(
    ArticleTitle = TextStyle(
        fontSize = VeneraType.statNumber.size,
        fontWeight = VeneraType.cardTitle.weight,
        lineHeight = 41.sp,
        letterSpacing = (-0.4).sp,
        color = c.TextPrimary,
    ),
    ArticleLead = TextStyle(
        fontSize = VeneraType.body.size,
        fontWeight = VeneraType.body.weight,
        lineHeight = 25.sp,
        color = c.TextSecondary,
    ),
    CardTitle = TextStyle(
        fontSize = VeneraType.aux.size,
        fontWeight = VeneraType.control.weight,
        lineHeight = 16.sp,
        color = c.TextPrimary,
    ),
    ButtonLabel = TextStyle(
        fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
        fontWeight = VeneraType.cardTitle.weight,
        color = c.TextOnAccent,
    ),
)

/**
 * 画廊设计系统全局令牌 (Gallery Design Tokens)。
 *
 * 严格按照设计规格 [gallery-spec-] 集中管理色彩、度量、圆角、文字与动效。
 *
 * 阶段 0（撤强调色档 · 只保留深浅模式）：`Color` 与 `Typography` 从静态 object
 * 改为**随明暗取档**的 @Composable getter（同 `MaterialTheme.colorScheme` 的形状）——
 * 消费点 `GalleryTokens.Color.X` 语法不变；`Spacing` / `Shape` / `Motion` / `Component`
 * 与主题无关，保持静态。
 */
internal object GalleryTokens {

    /** 色板随明暗取档（深色 = 基准页现值冻结；浅色 =对位新定）。 */
    val Color: GalleryColors
        @Composable
        @ReadOnlyComposable
        get() = if (LocalVeneraDarkTheme.current) GalleryColors.Deep else GalleryColors.Light

    object Spacing {
        val Space1: Dp = 1.dp
        val Space2: Dp = 2.dp
        val Space4: Dp = 4.dp
        val Space6: Dp = 6.dp
        val Space8: Dp = 8.dp
        val Space10: Dp = 10.dp
        val Space12: Dp = 12.dp
        val Space16: Dp = 16.dp
        val Space20: Dp = 20.dp
        val Space24: Dp = 24.dp
        val Space30: Dp = 30.dp

        val BentoPaddingHorizontal: Dp = 16.dp
        val BentoPaddingVertical: Dp = 14.dp
        val WallCardGap: Dp = 16.dp
    }

    object Shape {
        val Small = RoundedCornerShape(6.dp)
        val Medium = RoundedCornerShape(10.dp)
        val Large = RoundedCornerShape(12.dp)
        val Bento = RoundedCornerShape(16.dp)
        val Pill = CircleShape
    }

    /** 字阶随明暗取档（几何两档一致，只换绑色）。 */
    val Typography: GalleryTypography
        @Composable
        @ReadOnlyComposable
        get() = galleryTypographyOf(Color)


    object Component {
        // 人气标签长图卡（宽 136 × 高 224）
        val HotTagCardWidth = 136.dp
        val HotTagCardHeight = DesktopGalleryMetrics.paneWidth

        // 推荐画师卡（与左侧等高 224）
        val RecommendedUserCardWidth = 300.dp
        val RecommendedUserCardHeight = DesktopGalleryMetrics.paneWidth
        val RecommendedThumbsHeight = 160.dp
        val RecommendedArtistBarHeight = 64.dp
        val RecommendedAvatarSize = 32.dp

        // 滑轨翻页按钮
        val RailArrowButtonSize = 28.dp

    }
}
