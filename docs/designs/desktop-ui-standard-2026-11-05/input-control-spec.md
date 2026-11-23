# 输入场 / 切换器 / 选择框 统一规格

> **状态**：定稿（2026-11-22）
> **适用范围**：`:desktop` 模块全部桌面自持 UI
> **上游**：[桌面端统一 UI 规范 README](README.md)、[按钮状态与过渡规格](button-state-spec.md)

---

## 1. 标准件（唯一入口）

| 类别 | 标准件 | 位置 |
|---|---|---|
| 文本输入 | `DesktopInputField` | `ui/components/DesktopSearchField.kt` |
| 搜索输入 | `DesktopSearchField`（`DesktopInputField` 薄壳，多放大镜与清除钮） | 同上 |
| 分段切换 | `DesktopSegmented` | `ui/DesktopMacControls.kt` |
| 下拉选择 | `DesktopPopUpButton` | 同上 |

禁再造形。新屏需要输入或切换时直接取上表四件；自绘同形控件一律视为不合格。

## 2. 几何（输入场）

| 项 | 值 |
|---|---|
| 高度 | 单行 34dp；多行最小 34dp，随内容生长 |
| 圆角 | 8dp |
| 描边 | 常态 1dp；聚焦 2dp |
| 水平内距 | 11dp |
| 前导图标 | 15dp，与文本间隔 8dp |
| 文本 | `VeneraType.bodySecondary`，单行垂直居中 |
| 清除钮 | 20dp 圆，图标 12dp |

## 3. 状态

| 状态 | 底色 | 描边 |
|---|---|---|
| 常态 | `segmentTrack` | `hairline` 1dp |
| 悬停 | `segmentTrack` | `frame` 1dp |
| 聚焦 | `elevated` | `accent` 2dp |
| 错误 | 同聚焦底色 | `errorBorder` 1dp（调用点传错误色） |
| 禁用 | 整框降透明，停止交互 | 不变 |

色面通过 `DesktopSearchFieldColors` 注入；常规屏用 `DesktopSearchFieldColors.chrome()`，设置玻璃用 `settingsGlass()`，独立视觉层（安装向导、阅读器 HUD、设置对话窗）在调用点映射自家色板，几何不随色面变。

## 4. 分段切换

`DesktopSegmented`：轨 28dp / r7，滑块 24dp / r5 白卡浮起，项随内容收窄（最小 56dp），需要等分时传 `fillWidth = true`，设置页大档传 `large = true`。站徽色点走 `leadingDots`。

## 5. 下拉选择

`DesktopPopUpButton`：触发面 28dp / r6；菜单从触发面**下缘**向下展开 4dp，锚点高度取实测值（`Popup` 的 alignment 相对父布局，禁用 `BottomStart`，否则菜单向上生长覆盖按钮）。菜单项 26dp、选中为 accent 胶囊 + ✓。

## 6. 例外（保留自定义外观，不参与统一）

- 代码编辑器：`SourceEditScreen` 的多行 JavaScript 编辑器（`fillMaxSize` + 等宽字体）。
- 独立视觉层：安装向导（`installer/`）、阅读器 HUD 叠层。输入场几何同上，色板取各自视觉层。
- 来源大卡选择器：探索页 `SourceDropdownField`（图标 + 双行说明的大卡），不是紧凑下拉。
- 共享 `compose` 包不得反向依赖 `desktop`：`VeneraTextField` / `VeneraSelectBox` 仅保留给共享屏使用，桌面自持屏不再调用。
