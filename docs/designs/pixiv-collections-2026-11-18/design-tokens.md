# 珍藏册设计令牌 — 2026-11-18

色值全部取自 `GalleryTokens.kt` 深浅两档真值(GalleryColors.Deep / .Light),不得新造色。

## 色板

| 令牌 | 深档 | 浅档 | 用途 |
| --- | --- | --- | --- |
| Background | #0A0D12 | #F0F2F6 | 页底 |
| SurfaceLevel1 | #12161F | #FFFFFF | 白卡条/信息栏卡 |
| SurfaceLevel2 | #161B26 | #E8ECF2 | 分组面 |
| SurfaceLevel3 | #1B2130 | #DBE2EC | 输入框/嵌入面 |
| SurfaceGlass | 85% #12161F | 85% #1B2130(恒深) | 图上胶囊坞 |
| OutlineHairline | 6% 白 | 18% #17202E | 描边 |
| OutlineHover | 16% 白 | 28% #17202E | hover 描边 |
| Divider | 8% 白 | 8% #17202E | 分隔线 |
| TextPrimary/2/3 | #E8ECF4 / #98A2B6 / #6F7890 | #17202E / #55617A / #8791A6 | 文字三级 |
| AccentPrimary | #4D9FFF | #0A63F6 | 关注钮/选中 |
| AccentSoft | 16% #4D9FFF | 16% #2563EB | 选中底 |
| StatusFavorite | #E26D8A | #C2436A | 心形收藏 |
| TextOnAccent | #FFFFFF | #FFFFFF | 钮上文字 |

## 形与距

| 令牌 | 值 |
| --- | --- |
| 卡片底托圆角 | 14px(内衬 4px,内图 10px)|
| 胶囊圆角 | full;同心:胶囊 padding 12 → 外径 = 内径+padding |
| 节间距 | 40px;卡带卡间距 12px |
| 信息栏宽 | 380px 固定,左侧墙自适应 |
| 详情墙 | 3 列等宽方图,列距 12px,行距 12px |
| rail 卡 | 宽 260px,拼贴高 160px(约 3:2)|

## 字阶

沿用 GalleryTypography:页题 ArticleTitle 族;卡题 CardTitle 族;钮文 ButtonLabel 族。方案稿近似:标题 28px/700、卡题 14px/600、正文 13px、辅助 12px。

## 动效(VeneraMotion 档)

| 场景 | 值 |
| --- | --- |
| 卡片 hover 抬升 | translateY(-2px) + 阴影增强,150ms ease-out |
| 按压 | scale 0.96,150ms ease-out |
| 页签切换 | crossfade 200ms |
| 高频反馈 | ≤150ms,只动 opacity/background |
| 守门 | prefers-reduced-motion 时全部退化为 opacity 过渡 |
