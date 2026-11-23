# 窗底材质（Mica / Acrylic）透明模糊 — 结构分析

## 0. 结论

- **可行**。窗底材质通道在产品里已经接好（Nucleus Tao 宿主 + `WindowsBackdrop`），当前被深色档的**不透明根底**挡住，所以屏上是实底。
- 本方案只改「表面分层 + 透明度」，**不动窗口宿主、数据层与业务逻辑**。
- 默认 **Mica**（采样壁纸，低成本，适合整窗图片墙）；**Acrylic**（实时模糊桌面）作为可选档，建议只用于静止页/低负载场景。理由见 §7。

## 1. 现状读数（读码确定）

| 位置 | 现状 | 对材质的影响 |
|---|---|---|
| 起窗 | `VeneraDesktop.kt` 走 `nucleusApplication(backend = Tao)`，窗内已挂 `WindowsBackdrop(WindowsBackdropStyle.Acrylic)` | 材质已在窗后合成 |
| 窗根 | `VeneraDesktopLargeScreen.kt` 根 `Column` = `clip(12dp) + border(1dp) + background(VeneraTokens.color.background)`，深色档为不透明深色 | **完全盖住材质 = 实底** |
| 浅皮分支 | `homeSkinLight` 且 `DesktopDwmBackdrop.active` 时根底 = `Color.Transparent` | 已验证的透明路径，可扩到深色档 |
| 侧栏轨 | `VeneraSideBar` 轨身 = `surfaceContainerHigh @ 0.82` + 1dp 描边 + 18dp 投影 | 已半透明，但底不透明 ⇒ 看着接近实底 |
| 页面级底 | 探索/搜索/文件/本地库/下载/队列等屏在页面容器用不透明 `.background(tokens.color.surface)` | 即使窗根透明，这些页仍会盖住材质 |
| 卡片/面板 | 多处以 `surface` / `surfaceContainerHigh` 的 0.45–0.85 alpha 叠加 | 基础可复用，按 §4 收紧档位 |

## 2. 结构解耦（窗 → 岛 → 内容）

从外到内四层，每层可独立调透明度：

| 层 | 容器 | 覆盖范围 | 材质角色 |
|---|---|---|---|
| L0 窗底 | Nucleus 窗 + `WindowsBackdrop` | 整窗 | Mica 壁纸采样（默认）/ Acrylic 实时模糊（可选） |
| L1 页面底 | 根 `Column` 之下的 scrim `Box` | 内容区整片 | 低透明压暗层（保文字对比） |
| L2 常驻 chrome | 标题栏、侧栏轨、底部命令带、二级页头 | 岛屿/条带 | 玻璃填 + 发丝描边 |
| L3 内容件 | 卡片、面板、菜单、输入、胶囊 | 局部块 | 近实底 / 局部模糊浮层 |

岛屿圆角（同心，Outer = Inner + Padding）：窗 12dp（最大化 0）→ 侧栏轨 20dp → 卡片 16dp → 内部 chip 10dp。

## 3. 视觉层次

- **高斯模糊**：L0 由系统材质负责；L3 浮层（菜单、设置弹层）用 haze 局部模糊；**L2 不叠应用内模糊**，避免与窗底材质叠成双层糊。
- **描边**：所有玻璃/半透块 1dp 发丝线（`outlineVariant @ 0.5–0.6`），在深色材质上提供轮廓。
- **投影**：仅浮动件（轨、菜单、弹层）带 18dp 投影；贴附块不带。
- **压暗**：L1 scrim 是保障文字对比的**唯一**手段，按内容类型分档（图片墙最低、文本页最高）。

## 4. 设计参数（确定值 / 估算值严格标注）

| 参数 | 值 | 性质 |
|---|---|---|
| 窗圆角 | 12dp；最大化 0 | 确定（现有） |
| 窗描边 | 1dp `outlineVariant@0.6` | 确定（现有） |
| 侧栏轨填充 | `surfaceContainerHigh@0.62`（现 0.82） | 估算，待真窗校 |
| 页面 scrim（文本页） | `background@0.40` | 估算 |
| 页面 scrim（图片墙） | `background@0.20` | 估算 |
| 卡片填充 | `surface@0.80` | 估算 |
| 浮层模糊半径 | 24dp | 估算 |
| 发丝线 | 1dp `outlineVariant@0.5` | 确定（现有口径） |

## 5. 响应式视口

- 尺寸沿用 `DesktopGalleryMetrics.windowSizeFor`（按屏钳制），最小 1120×720。
- 材质与透明度**不随**窗口尺寸变化；圆角随最大化切换（沿用 `windowMaximized`）。

## 6. 降级

- 材质不可用（非 Win11 / 远程桌面 / 关闭特效 / 非 Windows）→ 不请求 `WindowsBackdrop`，根底回不透明深色（现有路径），scrim / 玻璃统一切「经典实底」档。
- 判活以宿主支持 + 回读为准，**不认「设了就当成功」**（既有探针铁律）。

## 7. 风险

- **Acrylic 实时模糊叠在滚动图片墙上会吃帧** → 默认 Mica。
- 亮壁纸下深色 scrim 要压得住文字 → scrim 档预留可调。
- 多显示器各屏分别采样壁纸，属系统行为，无需额外处理。
- 材质与本方案只对 Windows 生效；其它平台走降级档。
