# 桌面端基础控件与多选栏统一设计规范

> **状态**：定稿（2026-11-22）
> **适用范围**：`:desktop` 模块全部桌面自持 Compose UI（含设置弹窗、安装向导、阅读器 HUD 叠层）
> **上游总纲**：[桌面端统一 UI 规范 README](README.md) · [按钮状态与过渡规格](button-state-spec.md) · [输入场 / 切换器 / 选择框统一规格](input-control-spec.md)
> **硬约束**：所有新增与重做 UI 必须直接复用本规范定义的唯一标准组件；禁止在业务屏内私自绘制裸 `Box` 按钮、M3 原生 `Switch` / `Button` / `TextField` 或无模糊的孤立多选条。

---

## 0. 标准件总览（唯一调用入口）

| 类别 | 对标视觉原型 | 唯一标准组件 | 源码落点 |
|---|---|---|---|
| 搜索框 / 输入框 | 画廊顶栏「搜索作品」 | `DesktopSearchField` / `DesktopInputField` | `ui/components/DesktopSearchField.kt` |
| 分段选择框 | 画廊顶栏「图库站 \| Pixiv」 | `DesktopSegmented` | `ui/DesktopMacControls.kt` |
| 下拉选择框 | 画廊顶栏「评分 / 排序 / 子源」 | `DesktopPopUpButton` | `ui/DesktopMacControls.kt` |
| 按钮（三档尺寸） | 漫画详情页「开始阅读 / 离线下载 / 收藏」 | `DesktopButton`（及兼容委托 `DesktopPushButton`） | `ui/DesktopMacControls.kt` |
| 开关 | 设置页「底板模糊」 | `DesktopSwitch`（及桌面 `VeneraSwitch` 转发） | `ui/DesktopMacControls.kt` |
| 悬浮多选栏 | 底部居中毛玻璃多选坞 | `DesktopSelectionDock` + `DockActionButton` | `gallery/ui/selection/DesktopSelectionDock.kt` |

---

## 1. 搜索框与输入框（`DesktopSearchField` / `DesktopInputField`）

继承并锁定 [input-control-spec.md](input-control-spec.md) 全部几何与状态要求：

| 属性 | 标准值 | 说明 |
|---|---|---|
| 高度 | 单行最小 `34.dp`；多行随内容生长 | 不硬锁 `height`，使用 `defaultMinSize(minHeight = 34.dp)` |
| 圆角 | `8.dp` | `RoundedCornerShape(8.dp)` |
| 描边 | 常态 `1.dp` / 聚焦 `2.dp` | 常态 `fieldBorder`，悬停 `fieldBorderHover`，聚焦 `fieldBorderFocus` |
| 内边距 | 水平 `11.dp`；多行垂直 `9.dp` | 单行垂直居中 |
| 前导图标 | `15.dp`，右侧间距 `8.dp` | 聚焦时图标染主色，其余 `iconTint` |
| 清空钮 | `20.dp` 圆形 + `12.dp` `Close` 图标 | 值非空且传入 `onClear` 时显示 |
| 文本字阶 | `VeneraType.bodySecondary` | 占位词同字阶，走 `placeholderColor` |
| 色面注入 | `DesktopSearchFieldColors` | 常规屏 `.chrome()`，设置玻璃 `.settingsGlass()` |

---

## 2. 分段选择框（`DesktopSegmented`）

| 属性 | 标准档（工具栏/顶栏） | 大档（`large = true`，设置页） |
|---|---|---|
| 轨道高度 | `28.dp` | `32.dp` |
| 选项高度 | `24.dp` | `28.dp` |
| 轨道外圆角 / 内边距 | `7.dp` / `2.dp` | 同左（内缩同心圆：`2dp` 内边距对应滑块 `5.5.dp`） |
| 滑块圆角 | `5.5.dp` | `5.5.dp` |
| 选项水平内边距 | `12.dp`（最小项宽 `56.dp`） | `14.dp`（最小项宽 `64.dp`） |
| 字阶与字重 | `12.sp`；选中 `SemiBold`，未选 `Medium` | `13.sp`；选中 `SemiBold`，未选 `Medium` |
| 站徽色点（`leadingDots`） | `6.dp` 圆点 + `6.dp` 间距 | 未选中降为 `0.4f` 透明 |
| 轨道底色 | `palette.segmentTrack` | 同左 |
| 选中滑块 | `palette.card` + `0.5.dp` `palette.hairline` + 浅色 `1.dp` 微影 | 底色与文字色走 `VeneraMotion.tweenFast()` 过渡 |
| 悬停态 | 未选中项叠 `palette.hover` | 平滑过渡，禁止瞬切 |

---

## 3. 按钮体系（`DesktopButton` · 漫画详情页质感 + 三档尺寸）

以漫画详情页主/次操作钮的视觉质感为全仓唯一标准，按页面密度分为三档尺寸，**不得将 40dp 尺寸强行套入紧凑表格或工具栏**。

### 3.1 尺寸分档

| 档位 (`DesktopButtonSize`) | 高度 | 圆角 | 水平内边距 | 图标 / 间距 | 字体 | 适用场景 |
|---|---|---|---|---|---|---|
| `Large` | `40.dp` | `10.dp` | 主钮 `18.dp` / 常规 `14.dp`（含图标 `12.dp`） | `16.dp` / `6.dp` | `VeneraType.bodySecondary`（次按钮图标配 `VeneraType.aux` / `micro`） | 详情页主行动、安装向导主动作、悬浮多选栏主/次操作 |
| `Medium` | `32.dp` | `8.dp` | `14.dp` | `15.dp` / `6.dp` | `VeneraType.aux`，`Medium`/`SemiBold` | 对话窗确认/取消、表单提交、设置页操作钮、多选栏次按钮 |
| `Compact` | `26.dp` | `6.dp` | `12.dp` | `14.dp` / `5.dp` | `VeneraType.aux`，`Medium` | 工具栏、卡片内操作、列表行尾、筛选底部重置/应用 |

### 3.2 视觉变体（`DesktopButtonVariant`）

| 变体 | 默认底色 | 描边 | 文字与图标色 | 悬停态 |
|---|---|---|---|---|
| `Primary`（主色实底） | 饱和主色（深色 `#2563EB` / 浅色 `#0A63F6`，可经 `colors` 注入） | 无（`Color.Transparent`） | `Color.White` | 底色温和提亮 / 叠加高光遮罩 |
| `Secondary`（半透衬底 + 细描边，默认） | 常规浅色/深色 `palette.elevated` 或半透衬底；深色沉浸图头走 `Color.White.copy(0.09f)` | `0.5.dp` 细描边（`palette.frame` / 沉浸白 `0.16f`） | `palette.ink1`（沉浸场景 `Color.White.copy(0.86f)`） | 底色抬升一档，描边柔和加亮 |
| `Danger`（危险操作） | `palette.danger.copy(alpha = 0.12f)`（支持 `filledDanger = true` 实底红） | `0.5.dp` `palette.danger.copy(alpha = 0.45f)` | `palette.danger`（实底红时 `Color.White`） | 危险底色加深至 `0.20f`（浅色 `0.18f`） |
| `Ghost`（轻量透明动作） | `Color.Transparent` | 无 | `palette.ink2` | 淡入 `palette.hover` 底色，文字转 `palette.ink1` |

### 3.3 状态与过渡规则

- **状态过渡**：背景色、描边色、文字色均使用 `animateColorAsState(VeneraMotion.tweenFast())`。
- **按压反馈**：`pressed && enabled && !loading` 时缩放到 `0.96f`（`animateFloatAsState`，`VeneraMotion.tweenFast()`）。
- **激活 / 选中态（`selected = true`）**：次按钮可切换为强调/警示微底 + 对应描边（如详情页「已收藏 / 已追更」）。
- **禁用态（`enabled = false`）**：整体透明度降至 `0.45f`，关闭悬停与按压反馈。
- **加载态（`loading = true`）**：锁定点击与按压反馈，保留稳定宽度或内联显示加载指示，禁止瞬切尺寸。

---

## 4. 开关（`DesktopSwitch`）

| 属性 | 标准值 | 说明 |
|---|---|---|
| 轨道尺寸 | `36.dp × 20.dp` | `CircleShape` 全圆角药丸 |
| 滑块尺寸 | `16.dp × 16.dp` | `CircleShape`，距轨道内缘 `2.dp` |
| 开启态（`checked = true`） | 轨道 `accent`（默认 `palette.accent`），无描边 | 滑块水平偏移 `18.dp`，滑块背景白色/卡片色 |
| 关闭态（`checked = false`） | 轨道 `palette.segmentTrack` | `0.5.dp` 描边（常规 `palette.hairline`，悬停转 `palette.ink3.copy(0.35f)`） |
| 滑块阴影 | 浅色模式 `1.dp` 微影（`vShadow`）；深色模式无阴影 | 遵循暗色不画 Skia 投影铁律 |
| 动画过渡 | `140ms` (`tween(140)`) 滑块位移 + 轨色平滑过渡 | — |
| 禁用态 | 整体 `alpha = 0.45f`，禁止交互 | — |
| 特殊色面 | 阅读器 HUD 等独立暗色叠层可通过 `DesktopSwitchColors` 注入对比度达标的轨色与边框，几何不变 | 禁止回退为 M3 原生 `Switch` |

---

## 5. 悬浮多选栏（`DesktopSelectionDock`）

| 属性 | 标准值 | 说明 |
|---|---|---|
| 定位与外框 | 底部居中，距底 `24.dp`；高 `52.dp`；外圆角 `16.dp`（或胶囊 `26.dp`，同心圆内距匹配） | 本规范统一为 **`16.dp` 圆角玻璃岛**（`padding(horizontal = 12.dp, vertical = 6.dp)`），与桌面玻璃卡片族呼应，避免内部 `r10`/`r8` 按钮与 `26dp` 大圆外框产生几何打架 |
| 模糊透明材质 | 开启模糊时叠加 `hazeBlur`（模糊半径 `28.dp` + `palette.glassHazeTint` + 半透玻璃底） | 由页面主内容区挂 `hazeSource(hazeState)` 并经参数或 `LocalSelectionDockHazeState` 传入；关闭底板模糊时降级为 `DesktopGlassCardDefaults` 实底 |
| 外框描边与投影 | `1.dp` 细描边（深色 `Color.White.copy(0.14f)` / 浅色 `palette.frame`）；浅色叠加 `12.dp` `vShadow` | 暗色不投黑影 |
| 计数标签 | `height(32.dp)`、`RoundedCornerShape(8.dp)`、主色 `0.14f` 微底 + `0.5.dp` 主色 `0.28f` 描边、`12.sp Bold` 主色字 | 内嵌 `AnimatedContent` 数字淡入淡出 |
| 内部操作按钮 | **一律走 `DesktopButton(size = DesktopButtonSize.Medium)`（`32.dp` 高 / `r8`）** | 「全选 / 反选」用 `Ghost` 变体；「关闭」用 `32.dp × 32.dp` `Ghost`/`Danger` 图标钮；右侧主动作（「收藏 (N)」等）用 `Primary`；危险动作（「取消收藏 (N)」「删除所选」）用 `Danger` |

---

## 6. 新增 UI 提交前必查清单

1. 搜索/输入是否使用了 `DesktopSearchField` / `DesktopInputField`？
2. 2~3 项切换是否使用了 `DesktopSegmented`？下拉是否使用了 `DesktopPopUpButton`？
3. 所有按钮是否使用 `DesktopButton`（或已委托的 `DesktopPushButton`）且只选用了 `Large (40dp) / Medium (32dp) / Compact (26dp)` 三档之一？是否具备五态过渡？
4. 所有开关是否使用了 `DesktopSwitch`（或桌面 `VeneraSwitch`）？
5. 悬浮多选栏是否已接入 `HazeState` 模糊透明，且内部按钮均为统一按钮体系？
