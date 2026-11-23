# 阅读器对齐重做 — 设计令牌

依据：主人三张 OpenComic 截图（2026-11-19）；色值从截图采样估算（标 ⚠估）。

## 颜色（深色模式唯一口径；浅色取反参照 VDDS）

| 令牌 | 值 | 用途 |
|---|---|---|
| sheetBg | Color(0xF0,0x1A,0x18,0x20) ⚠估 | 弹窗玻璃底（95% 不透明，复用现有玻璃底口径） |
| sheetStroke | Color(0x2E,0x3A,0x46) ⚠估 | 1dp 发丝描边 |
| rowFg | Color.White | 主文案 |
| rowFgDim | Color(0xB3,0xFF,0xFF,0xFF) ⚠估 | 弱化文案（禁用/说明） |
| cardSelectedBg | 0xFF3D5AFE @ 18% ⚠估 | 选中模式卡淡蓝底 |
| cardUnselectedStroke | Color(0x3A,0x44,0x52) ⚠估 | 未选卡描边 |
| tabSelectedBg | 同 cardSelectedBg | 选中 tab 胶囊底 |
| switchTrackOn | 0xFF3D5AFE ⚠估 | 开关开态轨 |
| dropdownBg | Color(0xFF,0x26,0x2B,0x33) ⚠估 | 下拉条底 |
| divider | Color(0x1F,0xFF,0xFF,0xFF) ⚠估 | 分隔线 |

蓝主色沿用仓内现有 seed 派生（MaterialKolor），不写死蓝值——上表蓝值仅作截图对照。

## 尺寸 / 圆角 / 间距

| 令牌 | 值 |
|---|---|
| 弹窗宽 | 600dp（现 300dp 加宽） |
| 弹窗圆角 | 16dp（Outer=Inner+Padding 法则：外 16 / 内容 12 / 小件 8） |
| 弹窗最大高 | 窗高 × 0.8 |
| tab 高 | 40dp，胶囊圆角 999 |
| 行高 | ≥52dp；条目行上下 pad 8dp |
| 分隔 | 上下各 12dp 留白 + 1dp divider |
| 模式卡 | 高 56dp，圆角 12dp，2 列网格，列距 12dp 行距 12dp |
| 图标钮 | 32dp 圆形 hover 底（顶栏沿用现口径） |
| 顶栏高 | 44dp（不变） |
| 分隔竖线 | 1dp × 20dp 高，色 divider |
| 下拉条 | 高 52dp，圆角 8dp，左 pad 12dp |
| 滑条数值 | 右上角 11sp，rowFgDim |
| 对比图 | 每格 240×120dp，圆角 8dp，两格间距 12dp |

## 字阶

| 令牌 | 值 |
|---|---|
| 行主文案 | 14sp / w500 |
| 行副文案·说明 | 11sp / w400 / rowFgDim |
| tab 文案 | 13sp / w500 |
| 下拉标签 | 10sp / rowFgDim；下拉值 13sp / w500 |
| 滑条数值 | 11sp / rowFgDim |

## 动效

| 令牌 | 值 |
|---|---|
| tab / 卡片选中态 | 150ms emphasized |
| 弹窗出入场 | 入 220ms fade+slide(up 8dp)，出 150ms |
| 开关/滑条 | M3 默认 |
| 页翻页动画时长 | prefs.turnAnimSeconds（默认 0.30，0.1–4.0，步进 0.05）——统一喂所有翻页档 |

## 禁令（照抄总原则）

1. 只统一视觉不重设计；深浅两模式都要成立。
2. 不引入 Mica/WinUI 库；不接带皮三方 UI。
3. UI 文案禁拟人/比喻；主标题 ≤6 字，副标题 ≤15 字。
4. 白卡用 Color.White；字面量 Color 避开假徽标黑名单尺的整颗写法。
