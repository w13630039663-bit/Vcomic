# 窗底材质 — 组件规格

> 每个组件：结构 / 参数 / 状态 / 动画 / Compose 实现映射。参数值引用 `design-tokens.md`。

## 1. 窗根（WindowRoot）

- **结构**：Nucleus 窗 → `WindowsBackdrop(material)` → 根 `Column`（clip + border）→ scrim `Box` → 内容。
- **参数**：`material` = Mica（默认）/ Acrylic（可选）；scrim 档按 route（文本页 0.40 / 图片墙 0.20）。
- **状态**：材质激活 → 根 `background = Color.Transparent`；未激活 → 经典不透明 `background`。
- **动画**：材质切换 150ms alpha。
- **映射**：`VeneraDesktop.kt` 的 `WindowsBackdrop(WindowsBackdropStyle.Acrylic)` 改为按偏好给 `Mica` / `Acrylic`；`VeneraDesktopLargeScreen.kt` 根 `.background(...)` 分支在材质档时改 `Color.Transparent`，并在其下加一层 scrim `Box`。

## 2. 标题栏（TitleBar）

- **结构**：整窗顶条，左面包屑 / 中搜索胶囊 / 右窗控三钮。
- **参数**：`chrome.fill@0.62` + 底部 1dp `border.hairline`；不投影。
- **状态**：hover 项提亮（沿用现有）。
- **映射**：`DesktopWindowTitleBar` 底色分支加材质档读取。

## 3. 侧栏轨（Rail）

- **结构**：浮动玻璃轨，展开 264dp / 折叠 76dp，外层 6dp 水平内距。
- **参数**：`chrome.fill`（0.62）+ `rail.radius 20dp` + 1dp 描边 + `shadow.float 18dp`。
- **状态**：选中滑块 spring（dampingRatio 0.55）；hover 提亮（沿用）。
- **映射**：`VeneraSideBar` 轨身 `.background(surfaceContainerHigh.copy(alpha = 0.82f))` → 材质档改读 `chrome.fill`（0.62）。

## 4. 页面容器（PageContainer）

- **结构**：内容区整片。
- **参数**：`scrim.pageText` / `scrim.pageWall`；页面不再自铺不透明 `surface`。
- **映射**：以下屏的页面级 `.background(tokens.color.surface)` 在材质档下改 `Color.Transparent` 或 scrim token（点名）：探索、搜索、文件、本地库、下载管道、队列等。图片墙保留 `scrim.pageWall`。

## 5. 卡片 / 图块（Card / Tile）

- **结构**：封面 + 信息块。
- **参数**：`panel.fill@0.80`；图片本体保持不透明；`card.radius 16dp`。
- **状态**：hover 抬升 / 按压 0.96（沿用）。
- **映射**：卡片底统一走 `panel.fill`。

## 6. 浮层菜单 / 弹层（Popover / Dialog）

- **结构**：锚定或居中浮层。
- **参数**：`overlay.fill@0.86` + `blur.panel 24dp` + `shadow.float 18dp` + 1dp `border.hairline`。
- **映射**：`DropdownMenu` / 设置弹层底统一走 `overlay.fill` + haze 模糊。

## 7. 输入与胶囊（Input / Chip）

- **参数**：`panel.fill`；`chip.radius 10dp`；focus 环 `border.hairlineStrong`。
- **映射**：沿用现有 primitives（`DesktopVeneraPrimitives`），只换底色令牌。

## 8. 设置面板（Settings）

- **结构**：bento / glass 卡片。
- **参数**：`SettingsGlassPalette.Deep.glassFill`（#1D2330）改半透明（估算 alpha 0.62），其余字段不变。
- **映射**：`SettingsGlass.kt` 增加材质档供给；`SettingsCards` / `SettingsBento` 读 `LocalSettingsGlass` 保持不变。

## 9. 降级档

- **触发**：材质不可用（非 Win11 / 远程桌面 / 关闭特效 / 非 Windows）。
- **表现**：所有 `chrome.fill` / `panel.fill` 回不透明实值；窗根回 opaque `background`；不请求 `WindowsBackdrop`。
- **映射**：一个 `backdropActive` 布尔贯穿窗根与各表面令牌读取点，false 时走经典档。
