# 画廊关键体验与视觉设计系统令牌 (Design Tokens)

> **设计基准**：`docs/designs/gallery-booru-desktop-2026-11-05.html`、`docs/designs/gallery-home-desktop-v3-2026-11-05.html`  
> **归档位置**：`docs/designs/gallery-detail-tag-spec-2026-11-05/design-tokens.md`  

---

## 一、色彩令牌 (Color Tokens)

```kotlin
internal object GalleryDetailTagTokens {
    object Color {
        // ── 四大标签维度指示色 ──
        val DimensionArtist = androidx.compose.ui.graphics.Color(0xFFE26D8A)   // 画师·粉红
        val DimensionCharacter = androidx.compose.ui.graphics.Color(0xFF4D9FFF)// 角色·明蓝
        val DimensionSeries = androidx.compose.ui.graphics.Color(0xFF3ECF8E)   // 系列·翠绿
        val DimensionGeneral = androidx.compose.ui.graphics.Color(0xFFE8B04B)  // 通用·琥珀橙

        // ── 标签芯片表面 ──
        val TagChipSurface = androidx.compose.ui.graphics.Color(0xFF161B26)
        val TagChipHoverBorder = androidx.compose.ui.graphics.Color(0xFF0096FA)

        // ── 关注按钮状态色 ──
        val FollowActiveBg = androidx.compose.ui.graphics.Color(0x1F98A2B6)
        val FollowInactiveBg = androidx.compose.ui.graphics.Color(0xFF0096FA)
    }
}
```

---

## 二、尺寸与间距令牌 (Size & Spacing Tokens)

| Token 键名 | 物理数值 (Dp) | 作用目标与设计依据 |
| :--- | :--- | :--- |
| `TagChipHeight` | `24.dp` | 标签维度内微型芯片高度 |
| `TagChipPaddingHorizontal` | `10.dp` | 芯片内衬水平填充 |
| `TagGridGap` | `12.dp` | 4 列网格之间的列间距 |
| `ArticleImageMinHeight` | `320.dp` | 文章大图展示最小高度限制 |
| `ArticleImageMaxHeight` | `560.dp` | 文章大图展示最大高度限制（彻底杜绝纵向失控与横向压扁） |
| `ArticleLeadMaxWidth` | `820.dp` | 文章导读段落最大阅读视线宽度 |
| `ArtistBarHeight` | `52.dp` | 作品单元底部画师条高度 |

---

## 三、圆角与排印令牌 (Shape & Typography Tokens)

| Token 键名 | 规格值 | 使用场景与视觉依据 |
| :--- | :--- | :--- |
| `TagChipShape` | `RoundedCornerShape(12.dp)` | 胶囊形小巧标签芯片 |
| `ArticleImageShape` | `RoundedCornerShape(14.dp)` | 特辑大图高质感微圆角 |
| `TagChineseText` | `11.sp`, Medium | 双语芯片中的中文译名 |
| `TagOriginalText` | `9.5.sp`, Normal, `#657088` | 双语芯片中的英文/日文原词 |
