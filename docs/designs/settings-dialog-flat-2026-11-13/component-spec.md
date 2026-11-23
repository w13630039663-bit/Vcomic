# 设置弹窗扁平化 · 组件规格

> 组件与 `DesktopSettingRow` 六类一一映射；所有组件只读现有数据模型，不新增业务状态。
> 每个组件给出内部解构、视觉参数、状态变化、动画要求与 Compose 实现映射。

## C1 弹窗壳 `SettingsDialog`

**内部解构**：`Dialog` → 遮罩 → 圆角容器（24dp）→ 竖向 `Column`〔顶栏 C2 / 横向 `Row`〔左轨 C3 / 竖分隔线 / 右栏 C5〕〕。

**视觉参数**：`760×790dp`，`sizeIn(min 720×480, max 960×860)`，底 `--dlg-bg`，1dp 外描边 `--dlg-divider-strong`，投影 `--dlg-shadow`。左轨与右栏之间 1dp `--dlg-divider` 竖线，无卡面。

**状态**：出现 / 消失（220ms，scale `.98→1` + opacity）；窄高视口下按 `sizeIn` 收缩，两栏各自滚。

**动画**：只动 transform / opacity；减少动态效果时时长归零。

**Compose 映射**：`androidx.compose.ui.window.Dialog(onDismissRequest = onClose)` + `Box`/`Column`/`Row` + `clip(RoundedCornerShape(24.dp))`；不复用 `SettingsPageTemplate`（它服务源管理详情）。

## C2 顶栏 `SettingsDialogTopBar`

**内部解构**：三槽 `Row`：左 `Box(width=200dp)` 装搜索场；中 `Box(weight=1f)` 居中标题；右 `Box(width=56dp)` 装关闭钮。

**视觉参数**：高 56dp，底与弹窗同色，下缘 1dp `--dlg-divider`。搜索场 172×32dp、圆角 8dp、底 `--dlg-field`、1dp 描边、放大镜 16dp + 「搜索」12sp 占位。标题 14sp Medium。关闭 18dp 单色图标，点击区 44dp，带内容描述「关闭」。

**状态**：搜索场默认 / 悬停（描边提亮）/ 聚焦（2dp `--dlg-focus` 环）/ 有值（显清除钮）。标题随分区切换换文字，不做动画。关闭钮悬停底 `--dlg-hover`。

**动画**：150ms 底色过渡。

**Compose 映射**：`Row` + `BasicTextField`（或复用 `DesktopSearchField` 的紧凑形态，去掉 shortcutHint）；`IconButton` 内 `Icons.Filled.Close`。Ctrl+K 焦点快捷键沿用现有 `onPreviewKeyEvent` 处理口径。

## C3 左轨 `SettingsDialogRail`

**内部解构**：`Column`〔用户卡 C4 / 8dp / `Column(weight=1f, verticalScroll)` 装 11 个 C3a / 12dp / 版本尾注两行〕。宽 200dp，左右内距 12dp。

**视觉参数**：底与弹窗同色，无独立卡面；尾注 11sp `--dlg-text-2`，两行居中，写本仓产品名与版本。

**状态**：窄窗（<720dp 可用宽）收至 64dp，只留图标块 + 选中条，文字与尾注隐藏。

**动画**：切换分区时选中条 150ms 底色过渡。

**Compose 映射**：`Column` + `verticalScroll(rememberScrollState())` + `BoxWithConstraints` 判宽。

### C3a 分区项 `SettingsDialogNavItem`

**内部解构**：`Row`〔24dp 彩色圆角块（内 14dp 线性图标）/ 12dp / 14sp 文字（weight 1f，单行省略）/ 可选 12dp ›〕。

**视觉参数**：行高 40dp、圆角 8dp、左右内距 12dp；图标块圆角 7dp、纯色（按分区取 `--ico-*`），块内图标 `--dlg-on-accent`；点击区 ≥44dp（行高 40dp 时以 44dp 透明命中框兜底）。

**状态**：默认透明底 + `--dlg-text-2` 文字；悬停 `--dlg-hover`；按压 `--dlg-pressed` + scale `.96`；选中 `--dlg-selected` + `--dlg-text-1` + Medium；焦点 2dp `--dlg-focus` 环；禁用 `--dlg-text-3` + `--ico-slate`。

**动画**：150ms；按压 spring 无过冲。

**Compose 映射**：`Row` + `clip` + `background` + `clickable(interactionSource, indication = null)` 自绘反馈（避免 M3 默认涟漪破坏扁平感）；`hoverable` + `collectIsHoveredAsState`。图标沿用 `settingsSectionIcon()` 那 11 个材料图标，包进彩色块。

### C4 用户卡 `SettingsDialogUserCard`

**内部解构**：`Row`〔40dp 圆形头像 / 10dp / `Column`〔14sp 昵称 / 11sp 副行〕〕，高 56dp，圆角 8dp。

**视觉参数**：头像底取 `--ico-blue` 18% 透明 + 首字母；昵称 `--dlg-text-1`，副行 `--dlg-text-2`；左右内距 8dp。

**状态**：有落点（已登录/未登录各有 onOpen）时才可点，悬停 `--dlg-hover`、按压 `.96`；无落点纯展示，不摆箭头。数据沿用 `SettingsUserCard` 的 `signedIn / userId / systemUser` 判式。

**动画**：150ms。

**Compose 映射**：`Row` + `clip(RoundedCornerShape(50))` 头像 + `clickable(enabled = onClick != null)`。

## C5 右栏 `SettingsDialogContent`

**内部解构**：`Column(verticalScroll)` 内按 `DesktopSettingsCard` 逐组渲染：组标题 C5a → 行列表 C6 → 组间 20dp。

**视觉参数**：左右留白 24dp，顶部 8dp、底部 24dp；宽 = 弹窗宽 − 200dp − 1dp 线。组前留白 20dp，组标题下 8dp。

**状态**：空态居中显示「这一组还没有设置项。」/「没有匹配「x」的设置项。」（沿用现有文案）。搜索态标题为「搜索结果」，行按分区前缀归属。

**动画**：分区切换时内容 180ms 淡入（不滑移，避免与滚动位置打架）。

**Compose 映射**：`Column` + `verticalScroll` + `AnimatedContent`（可选，先做淡入）；空态 `Box(contentAlignment = Center)`。

### C5a 分组标题 `SettingsDialogGroupLabel`

**视觉参数**：12sp / 18sp，Medium，`--dlg-text-2`，单行省略。卡 `caption` 不显示（截图无副题位）。

**状态**：无交互态。

**Compose 映射**：`Text`。

## C6 扁平行 `SettingsDialogRow`

**内部解构**：`Row`〔24dp 彩色块（内 14dp 图标）/ 12dp / `Column(weight=1f)`〔14sp 标题 / 可选 11sp 副文〕/ 右控件槽〕；高 48dp、圆角 8dp、左右内距 12dp；行间 1dp `--dlg-divider`（首行前、末行后不画）。

**图标块配色**：按分组取 `--ico-*`（同一组共用一色，避免每行乱色），不可用行取 `--ico-slate`。图标按行类型选：开关→电源/勾类，选项→滑块类，入口→箭头类，文本→编辑类，动作→对应语义图标；一套线性图标，1.6–1.8px。

**状态**：默认 / 悬停 / 按压 / 焦点 / 禁用，同 C3a；`Entry.clickable=false` 与 `Absent` 行不摆箭头、不响应点击。

**动画**：150ms 反馈；按压 `.96`。

**Compose 映射**：`Row` + `clickable`（仅可点行）+ `hoverable`；`contentDescription` 只给纯图标控件，标题行已有文字。

### C6a 行控件映射（按 `DesktopSettingRow`）

| 类型 | 右槽内容 | 交互 |
|---|---|---|
| `Toggle` | `VeneraSwitch`（轨色 `--switch-on` / `--switch-off`） | 原 `onToggle`；`enabled=false` 时禁用 |
| `Options` | 当前值 12sp `--dlg-text-2` + 12dp › | 点整行弹选择浮层（列 `options`，选中打勾），`onSelect` 原样回调；**不再用分段器/下拉** |
| `Entry` | `value` 12sp + 12dp ›（仅 `clickable=true`） | 原 `onOpen`；纯展示行只显值 |
| `Action` | 文字按钮（`buttonLabel`，色取 `buttonColor ?: --ico-blue`，危险色 `--danger`） | 原 `onAction`；`enabled=false` 禁用 |
| `Text` | 180dp 行内输入框（圆角 8dp，底 `--dlg-field`） | 700ms 防抖 `onCommit`，沿用现有实现 |
| `Absent` | 「不可用」小签（1dp 描边 `--dlg-text-3`，圆角 5dp） | 无 |

`Options` 浮层：`DropdownMenu` 风格但用扁平深色底 + 1dp 描边 + 圆角 8dp，每行 36dp，选中行底 `--dlg-selected` + 勾；点击外部或 Esc 关闭。

## C7 凭据对话框（沿用，不在本稿改动）

Gelbooru 登录与 SauceNAO 凭据两个 `Dialog` 保持现有实现与形状，不随本批重做；只保证它们叠在新弹窗之上仍可读（沿用系统 `Dialog` 层级）。

## 实现映射总表

| 新增文件 | 承载组件 |
|---|---|
| `settings/SettingsDialogShell.kt` | `SettingsDialogPalette` + C1/C2/C3/C3a/C4/C5/C5a/C6/C6a |
| `settings/DesktopSettingsHub.kt` | 只改 1371–1455 装配分支：把 `panelContent` 换成 `SettingsDialogShell(...)`，数据 `visibleCards` / `cardsOf` / `searchCards` 全部照传 |
| `ui/DesktopPageTemplates.kt` | **不改**（源管理详情共用） |
| `settings/SettingsGlass.kt` | **不改**（新色板自持，避免波及舱墙皮肤） |
| `settings/SettingsCards.kt` / `SettingsBento.kt` / `SettingsOverview.kt` | **不改** |

## 验收清单（逐项对照）

- [ ] 顶栏三槽横跨整窗，搜索在左、标题居中、关闭在右
- [ ] 左轨 200dp、11 分区、彩色圆角图标块、选中整行浅底、版本尾注
- [ ] 右栏无卡片：分组小标题 + 扁平行 + 1dp 行分隔线
- [ ] 六类行控件全部按 C6a 摆出，无假把手、无「N 项」徽标、无 uppercase 副标题
- [ ] 11 分区可切换，搜索跨分区合并，空态文案沿用
- [ ] 全部读写口、二级入口、两个凭据对话框零删减
- [ ] 焦点环 / 44dp 点击区 / 对比度 / 减少动态效果
- [ ] 编译过；点名跑 `DesktopSettingsEntryTest` 等受影响测试类全绿
- [ ] 只暂存本次动过的文件并提交
