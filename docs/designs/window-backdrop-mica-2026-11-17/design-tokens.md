# 窗底材质 — 设计令牌

> 所有颜色均为**材质之上的叠加色**；材质本体（Mica/Acrylic）由系统合成，不在此定义色值。

## 1. 颜色令牌

### 1.1 表面层级

| 令牌 | 值 | 用途 |
|---|---|---|
| `scrim.pageText` | `background.copy(alpha = 0.40f)` | 文本页压暗 |
| `scrim.pageWall` | `background.copy(alpha = 0.20f)` | 图片墙压暗 |
| `chrome.fill` | `surfaceContainerHigh.copy(alpha = 0.62f)` | 标题栏 / 侧栏轨 / 命令带 |
| `chrome.fillSolid` | `surfaceContainerHigh` | 降级档（材质不可用） |
| `panel.fill` | `surface.copy(alpha = 0.80f)` | 卡片 / 面板 |
| `overlay.fill` | `surface.copy(alpha = 0.86f)` | 菜单 / 弹层（配模糊） |
| `border.hairline` | `outlineVariant.copy(alpha = 0.50f)` | 玻璃/半透块轮廓 |
| `border.hairlineStrong` | `outlineVariant.copy(alpha = 0.60f)` | 窗框、浮动轨 |
| `tint.primaryWash` | `primary.copy(alpha = 0.12f)` | 选中/强调洗底 |

### 1.2 文字（不变）

- `textPrimary` / `textSecondary` / `textTertiary` 沿用现有 token 原值，材质不改变文字槽位。

## 2. 尺寸令牌

| 令牌 | 值 |
|---|---|
| `window.radius` | 12dp |
| `window.radiusMax` | 0dp |
| `rail.radius` | 20dp |
| `card.radius` | 16dp |
| `card.radiusSmall` | 12dp |
| `chip.radius` | 10dp |
| `hairline.width` | 1dp |

**同心几何（Outer = Inner + Padding）**：rail 20 = card 16 + 内距 4；card 16 = chip 10 + 内距 6（内距按实际组件 padding 取整）。

## 3. 间距令牌

- 沿用现有 `Spacing`（space1..space6），本文不新增。

## 4. 模糊令牌

| 令牌 | 值 | 说明 |
|---|---|---|
| `blur.panel` | 24dp | haze 局部模糊，仅浮层（菜单/弹层） |
| `blur.rail` | 0dp | 靠窗底材质，不叠应用内模糊 |
| `blur.fallback` | 24dp | 材质不可用时，可用应用内模糊兜底玻璃块 |

## 5. 阴影 / 微光

| 令牌 | 值 | 用途 |
|---|---|---|
| `shadow.float` | 18dp | 浮动轨 / 菜单 / 弹层 |
| `shadow.none` | — | 贴附块不带 |

## 6. 动效

| 场景 | 时长 / 曲线 |
|---|---|
| 材质 / 透明度切换 | 150ms 淡入淡出 |
| 按压 | 0.96 弹性（沿用 `VeneraPressFeedback`） |
| 主题/材质切换 | 只做 alpha 过渡，不做位移 |

## 7. 圆角几何法则

`Outer = Inner + Padding`：窗 12 → 内距 8 → 内容视觉 4；其余按组件内距显式满足，禁止出现内外圆角不同心。
