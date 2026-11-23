# 报错提醒与问题反馈系统 — 组件规格

规格名:`error-feedback-system-2026-11-22` · 日期:2026-11-22 · 配套:`design-analysis.md` / `design-tokens.md`
实施文件（确认后）:`DesktopErrorReport.kt`、顶栏窗口控制组件、14 处报错上报接入点、新增 `DesktopFeedbackPanel.kt`。本轮**未改代码**。

## 1. `DesktopErrorCapsule` — 顶部报错胶囊通知栏

**解构**: `Box(居中锚点)` → `Row(胶囊壳)` → `[红点指示] + [AnimatedVisibility(大白话摘要)] + [未读数徽标] + [下拉箭头]`。

**视觉参数**: 高 `28.dp`，圆角 `999.dp`，`surfacePrimary` 玻璃底 + `1.dp outlineHairline` 描边 + `vShadow`。

**状态**:

| 状态 | 表现 |
|---|---|
| 无错误 | 彻底隐藏（不占位、不可点击） |
| 刚触发新报错 | 顶部滑入展开摘要文案，持续 4 秒后自动缩成角标 |
| 已缩为角标 | 仅保留红点 + 未读数 + 箭头，紧凑贴合顶栏 |
| 悬停 | 描边提亮为 `controlAccent @ 0.42`，底色提亮 |

**动画**: `AnimatedVisibility(slideInVertically + fadeIn + scaleIn, 240ms)` 与 `animateContentSize(240ms)` 控制摘要向角标的平滑收敛。

**Compose 映射**: 消费 `DesktopErrorBus.state`，新增 `capsuleMode`（`Hidden / ExpandedBanner / BadgeOnly`）由 `LaunchedEffect` 4 秒定时收敛；无全屏遮罩。

## 2. `DesktopErrorDropdownPanel` — 报错轻量下拉面板

**解构**: `Column(16.dp 圆角玻璃卡)` → `[Header(标题/全部复制/清空/关闭)]` + `[FilterRow(全部/漫画/图库/系统)]` + `[LazyColumn(ErrorRecordCard)]`。

**视觉参数**: 宽 `540.dp`（窄窗自适应），最大高 `520.dp`，外层 `16.dp` / 内嵌卡片 `10.dp`、`surfaceNested` 底色。

**状态**:

| 状态 | 表现 |
|---|---|
| 展开 / 收起 | 顶部胶囊正下方展开，点击外部或关闭按钮收起 |
| 分类筛选 | 选中 Chip 使用 `accentSoft` 底色 + `controlAccent` 文字与边框 |
| 卡片详情 | 默认折叠技术栈与日志路径；点击「详情」平滑展开等宽代码块 |

**动画**: 面板整体 `AnimatedVisibility(fade + scale(0.96->1) + slideY, 220ms)`；卡片详情区域 `AnimatedVisibility(expandVertically + fadeIn, 210ms)`。

**Compose 映射**: 替换原 `DesktopErrorReport.kt` 中 `DesktopErrorOverlay` 的居中白框；每张卡片增加 `category: ErrorCategory`（`COMIC / GALLERY / SYSTEM`）、`humanReason: String` 与「去反馈」回调。

## 3. `DesktopFeedbackTrigger` & `DesktopFeedbackPanel` — 顶栏反馈入口与双 Tab 面板

**解构**: 顶栏最小化按钮左侧 `Row(28.dp 高图标文字按钮)`；展开面板 `Column(16.dp 圆角玻璃卡)` → `[Header]` + `[SegmentedTabs(带滑动指示器)]` + `[关联报错下拉(仅 Bug Tab)]` + `[标题/描述输入框]` + `[截图附件区(本地选图+Ctrl+V)]` + `[提交行]`。

**视觉参数**: 面板宽 `440.dp`，最大高 `590.dp`，圆角 `16.dp`，输入槽 `9.dp` 圆角 `inputBg` + `1.dp` 聚焦边框；`SegmentedTabs` 外 `10.dp` / 滑块 `7.dp` + `3.dp` 内距。

**状态**:

| 状态 | 表现 |
|---|---|
| Tab 切换 | 滑块在「问题反馈」与「新功能建议」间平滑移动；关联报错选择器同步平滑折叠/展开 |
| 截图附件 | 支持 `Ctrl+V` 监听剪贴板图片及文件选择器选图（最多 3 张预览，支持单张删除） |
| 提交 | 复制首张/选中截图到系统剪贴板并弹轻提示，调用系统浏览器打开 GitHub `issues/new` 预填 URL |

**动画**: 面板从右上角锚点 `220ms` 展开；Tab 滑块 `animateDpAsState(220ms)`；关联报错框 `AnimatedVisibility(220ms)`。

**Compose 映射**: 复用 `DesktopWindowChrome` / `DesktopTitleBar` 窗口控制区；配置常量 `GITHUB_ISSUE_REPO` 集中定义，便于后续填入目标仓库地址。
