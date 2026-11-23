# 画廊桌面端设计令牌系统规范 (Design Tokens)

> **版本**：2026-11-05 v3.3（设计规格审查修正版）  
> **适用技术栈**：Jetpack Compose / Compose Multiplatform (Desktop)  
> **设计语言**：克制、高密度、现代化暗色暗琉璃质感（Dark Glassmorphism）

---

## 一、 全局颜色令牌 (Global Color Tokens)

所有的颜色均按物理数值严格归档，严禁在 UI 实现中使用未定义的任意 Hex 硬编码。

```kotlin
package com.venera.desktop.gallery.ui.tokens

import androidx.compose.ui.graphics.Color

object GalleryColorTokens {
    // ── 基础画布层级（Surfaces）──
    val Background = Color(0xFF0A0D12)          // 视口主底色，深邃防眩光黑
    val SurfaceLevel1 = Color(0xFF12161F)       // Bento 舱体主底板、标准卡片底色
    val SurfaceLevel2 = Color(0xFF161B26)       // 凸起悬浮卡片、右键菜单、推荐用户卡底色、搜索框底色
    val SurfaceLevel3 = Color(0xFF1B2130)       // 次级交互卡片底色、未激活分段按钮底色
    val SurfaceGlass = Color(0xD912161F)        // 85% alpha 毛玻璃动作坞与悬浮条底色

    // ── 边框与描边层级（Outlines & Borders）──
    val OutlineHairline = Color(0x0FFFFFFF)     // 6% 白，用于容器外描边、默认卡片边框、细分割线
    val OutlineHover = Color(0x29FFFFFF)        // 16% 白，鼠标移入时的微发光边框
    val OutlineSelected = Color(0xFF4D9FFF)     // 100% 品牌主色，多选态选中边框
    val Divider = Color(0x14FFFFFF)             // 8% 白，用于菜单项内部水平分割线

    // ── 文字与内容层级（Content & Typography）──
    val TextPrimary = Color(0xFFE8ECF4)         // 92% 明度高对比文字：标题、作品名
    val TextSecondary = Color(0xFF98A2B6)       // 65% 明度辅助文字：作者名、副标题、标签、时间戳
    val TextTertiary = Color(0xFF6F7890)        // 45% 明度弱文字：快捷键徽标、统计数字
    val TextOnAccent = Color(0xFFFFFFFF)        // 纯白高对比：主色按钮文字、选中角标

    // ── 强调色与品牌色（Brand Accent）──
    val AccentPrimary = Color(0xFF4D9FFF)       // 标志性数码蓝：加关注、主动作、选中徽标
    val AccentSoft = Color(0x294D9FFF)          // 16% 柔光蓝：菜单项悬停背景、高亮提示底色

    // ── 语义与状态色（Status & Semantics）──
    val StatusFavorite = Color(0xFFE26D8A)      // 已收藏粉红爱心
    val StatusDanger = Color(0xFFFF6B6B)        // 危险与屏蔽警告红（屏蔽画师、限制级）
    val StatusSuccess = Color(0xFF3ECF8E)       // 上升热度绿色、在线指示点
    val StatusBadgeR18 = Color(0xC70A0D12)      // R-18 黑色微透标签底色 (78% alpha)
}
```

---

## 二、 间距与布局令牌 (Spacing & Layout Tokens)

基于 4dp/8dp 工业级网格步进系统，兼顾高密度鼠标指针交互。

```kotlin
package com.venera.desktop.gallery.ui.tokens

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object GallerySpacingTokens {
    // ── 基础度量步进 ──
    val Space1: Dp = 1.dp
    val Space2: Dp = 2.dp                       // 拼贴极细缝隙
    val Space4: Dp = 4.dp                       // 图标与文本水平内隙、动作坞按钮间距
    val Space6: Dp = 6.dp                       // 菜单项内部垂直微间距、标题上边距
    val Space8: Dp = 8.dp                       // 紧凑卡片横向间隙、小标题水平距离
    val Space10: Dp = 10.dp                     // 菜单项水平内边距、搜索框间隙
    val Space12: Dp = 12.dp                     // 滑轨横向卡片间隙、Bento 纵向标题下边距
    val Space16: Dp = 16.dp                     // 主墙等宽网格大间隔 (Gap)、Bento 外距
    val Space20: Dp = 20.dp                     // 窗口屏幕横向外边距 (ScreenHorizontal)
    val Space24: Dp = 24.dp                     // 大模块垂直隔离间隙、右下角浮动把手外距
    val Space30: Dp = 30.dp                     // 文章页各作品单元之间的纵向间隙

    // ── 核心构件尺寸度量 ──
    val TopbarHeight: Dp = 52.dp                // 桌面顶部指挥栏高度
    val BentoPaddingHorizontal: Dp = 16.dp      // Bento 舱体内部横向留白
    val BentoPaddingVertical: Dp = 14.dp        // Bento 舱体内部纵向留白
    val GridMinColumnWidth: Dp = 210.dp         // 主墙卡片自适应最小列宽
    val WallCardGap: Dp = 16.dp                 // 等宽网格横纵向呼吸大间隔
}
```

---

## 三、 圆角令牌 (Shape & Corner Tokens)

```kotlin
package com.venera.desktop.gallery.ui.tokens

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

object GalleryShapeTokens {
    val CornerNone = RoundedCornerShape(0.dp)
    val CornerSmall = RoundedCornerShape(6.dp)   // 标签胶囊、菜单项 Hover 底板、状态角标
    val CornerMedium = RoundedCornerShape(10.dp) // 右键菜单浮层、小卡片、搜索输入框
    val CornerLarge = RoundedCornerShape(12.dp)  // 主墙卡片、长图卡、排行榜卡、新作卡
    val CornerBento = RoundedCornerShape(16.dp)  // Bento 舱体容器外框、特辑文章主卡
    val CornerPill = CircleShape                 // 圆形按钮、左右翻页钮、动作坞药丸、浮动回顶钮
}
```

---

## 四、 字体层级令牌 (Typography Tokens)

针对桌面高分屏精准调校，补齐了特辑大标题与导读段落参数。

```kotlin
package com.venera.desktop.gallery.ui.tokens

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object GalleryTypeTokens {
    // ── 文章与展示阶梯 ──
    val ArticleTitle = TextStyle(
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 41.sp, // line-height: 1.3
        letterSpacing = (-0.4).sp,
        color = GalleryColorTokens.TextPrimary,
    )
    val ArticleLead = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 25.sp, // line-height: 1.8
        color = GalleryColorTokens.TextSecondary,
    )
    val FeatureTitle = TextStyle(
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        color = GalleryColorTokens.TextPrimary,
    )

    // ── 标题阶梯 ──
    val DisplayTitle = TextStyle(
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.3).sp,
        color = GalleryColorTokens.TextPrimary,
    )
    val SectionTitle = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        color = GalleryColorTokens.TextPrimary,
    )
    val SectionSub = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = GalleryColorTokens.TextTertiary,
    )

    // ── 卡片与内容阶梯 ──
    val CardTitle = TextStyle(
        fontSize = 12.5.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 16.sp,
        color = GalleryColorTokens.TextPrimary,
    )
    val CardSub = TextStyle(
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Normal,
        color = GalleryColorTokens.TextSecondary,
    )
    val ButtonLabel = TextStyle(
        fontSize = 11.5.sp,
        fontWeight = FontWeight.SemiBold,
        color = GalleryColorTokens.TextOnAccent,
    )
    val OverlineTag = TextStyle(
        fontSize = 9.5.sp,
        fontWeight = FontWeight.Bold,
        color = GalleryColorTokens.TextOnAccent,
    )
    val KeyHintMono = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
        fontWeight = FontWeight.Normal,
        color = GalleryColorTokens.TextTertiary,
    )
}
```

---

## 五、 动效与交互令牌 (Motion Tokens)

严格遵循 `better-ui` 规范：高频指针交互必须在 150ms 内响应。

```kotlin
package com.venera.desktop.gallery.ui.tokens

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween

object GalleryMotionTokens {
    val StandardEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f) // 快速启动，平滑减速
    val ExitEasing = CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f)     // 迅速离场

    const val DurationInstantMs = 100    // 菜单项 Hover 变色、按压微缩
    const val DurationFeedbackMs = 150   // 动作坞淡入、卡片上浮、多选徽标显隐
    const val DurationPanelMs = 220      // 右键菜单展开、抽屉滑出
    const val DurationScrollMs = 250     // 滑轨左右翻页平滑滚移

    val HoverElevationSpec = tween<Float>(
        durationMillis = DurationFeedbackMs,
        easing = StandardEasing,
    )
    val ActionDockFadeSpec = tween<Float>(
        durationMillis = DurationFeedbackMs,
        easing = StandardEasing,
    )
}
```

---

## 六、 专属组件级令牌（修正与补齐版）

### 6.1 发现带长图卡片 (HotTag Card)
- `Width`: `136.dp` [确定值]
- `Height`: `224.dp` [确定值]
- `CornerRadius`: `12.dp` [确定值]
- `Aspect`: 约 `1:1.65`（饱满长图比例，非细长电线杆）
- `TrendingBadge`: 左上角偏置 `8.dp`，字号 `10.sp` 绿色胶囊

### 6.2 推荐画师卡片 (Recommended User Card)
- `Width`: `300.dp` [确定值]
- `Height`: `224.dp` [确定值]（严格与左侧长图卡等高，消灭底部空白）
- `CornerRadius`: `12.dp` [确定值]
- `ThumbsHeight`: `160.dp` [确定值]（占总高 71%，最大化作品透出率）
- `ArtistBarHeight`: `64.dp` [确定值]（含 Padding）
- `AvatarSize`: `32.dp` [确定值]
- `FollowButtonHeight`: `28.dp` [确定值]，圆角 `14.dp`，字体 `11.5.sp`

### 6.3 关注新作与排行榜通用卡片 (Shelf Work Card) [尺寸纠正]
- `Width`: **172.dp** [确定值]（原估算 200dp 已修正）
- `CoverHeight`: **112.dp** [确定值]（原估算 130dp 已修正）
- `CornerRadius`: `12.dp` [确定值]
- `TitleStyle`: `CardTitle` (12sp)
- `SubtitleStyle`: `CardSub` (10.5sp 灰色)
- `RankBadgeSize`: `20.dp × 18.dp`（适用于排行榜，圆角 5dp）[确定值]

### 6.4 pixivision 特辑杂志主卡 (Magazine Feature Card) [尺寸纠正]
- `Width`: **640.dp** [确定值]（原估算 520dp 已修正）
- `Height`: **340.dp** [确定值]（原估算 200dp 已修正）
- `CornerRadius`: `16.dp` [确定值]
- `TitleStyle`: `FeatureTitle` (22sp 加粗白色)
- `BadgePadding`: `3.dp 10.dp`，主色背景，纯白文字

### 6.5 相关特辑推荐卡片 (Related Article Card) [补充遗漏]
- `Width`: **236.dp** [确定值]
- `CoverHeight`: **132.dp** [确定值]
- `CornerRadius`: `12.dp` [确定值]
- `TitleStyle`: `CardTitle` (12sp)

### 6.6 顶栏搜索框 (Topbar Search Input) [补充遗漏]
- `Height`: `36.dp` [确定值]
- `MinWidth`: `280.dp` [确定值]
- `CornerRadius`: `10.dp` [确定值]
- `BackgroundColor`: `GalleryColorTokens.SurfaceLevel2` (`#161B26`) [确定值]
- `Border`: `GalleryColorTokens.OutlineHairline` [确定值]
- `KeyBadge`: `Ctrl+K` 等宽胶囊，高度 `18.dp`，字号 `10.sp` [确定值]

### 6.7 浮动操作把手 (Floating Action Button) [补充遗漏]
- `Size`: `44.dp × 44.dp` 圆形 [确定值]
- `Offset`: 视口右下角固定偏移 `24.dp` [确定值]
- `Elevation`: `6.dp` 强阴影
- `BackgroundColor`: `GalleryColorTokens.AccentPrimary` (`#4D9FFF`) [确定值]
