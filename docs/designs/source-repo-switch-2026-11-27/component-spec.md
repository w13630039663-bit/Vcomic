# 站点墙源仓库切换（接入 laincat/vensrc）— 组件规格

规格名:`source-repo-switch-2026-11-27` · 日期:2026-11-27 · 配套:`proposal.md` / `design-analysis.md` / `design-tokens.md`
实施文件(确认后):`DesktopSourceUpdate.kt`、`DesktopSourceUpdater.kt`、`DesktopSourceCatalog.kt`、`DesktopSourceManageScreen.kt`、`DesktopSourceStore.kt`、`DesktopEngineHostActions.kt`、`DesktopLargeScreenHelpers.kt`、`DesktopLargeScreenSearchAndSourceState.kt`、`DesktopLargeScreenMiscEffects.kt`、`VeneraDesktop.kt`。本轮**未改代码**。

## 1. `SourceRepoBar` — 源仓库切换与同步控制条

**解构**:`DesktopGlassCard(Nested, r12)` → `FlowRow`（左区与右区自动两端对齐,窄屏允许折行）
- 左区:`Text("源仓库")` → `DesktopSegmented(["官方源", "社区源 · laincat/vensrc"])` → 条件显隐 `DesktopSegmented(["主订阅 (25)", "扩展 (71)"])` → 来源标注胶囊(`github.com/laincat/vensrc` 或 `venera-app/venera-configs`)
- 右区:状态摘要文本 → `DesktopButton("浏览源库", Compact, Secondary)` → `DesktopButton("检查同步", Compact, Primary/Secondary, loading = updateBusy)`

**视觉参数**:引用 `design-tokens §1-§5`（`Nested` 玻璃底、`r12`、内距 `14×10.dp`、按钮 `26.dp`/`r6`、胶囊 `r999`）。

**状态**:

| 状态 | 表现 |
|---|---|
| 默认 | 显示当前仓库选中项与源数摘要,「检查同步」为 `Secondary`（检测到新版本/未同步可升 `Primary`） |
| 悬停 | 分段未选项叠 `palette.hover`,按钮走 `DesktopButton` 悬停态 |
| 按压 | `DesktopButton` 按压 `0.96f`;分段切换无缩放 |
| 激活 / 选中 | 选中 `社区源 · laincat/vensrc` 时展开二级轨道分段与 `github.com/laincat/vensrc` 标注 |
| 禁用 / 加载 | `updateBusy = true` 时「检查同步」进入 `loading = true`,「浏览源库」与仓库切换锁定防并发错乱 |

**动画**:轨道分段显隐使用 `AnimatedVisibility` + `VeneraMotion.tweenFast()`;其余色面过渡由 `DesktopSegmented` / `DesktopButton` 内置完成。

**Compose 映射**:复用 `DesktopGlassCard`、`DesktopSegmented`、`DesktopButton`、`StatusBadge`;选中仓库 `repoId` 由偏好存储(`JsonKeyValueStore`)持久化并透传至 `DesktopSourceManageScreen`。

## 2. `WallHeader`「添加站点」菜单与提示区 — 入口补全与反馈可见

**解构**:`WallHeader` 右侧 `DesktopButton("添加站点", Medium, Primary)` → `DropdownMenu`（三项:「浏览源库（含当前仓库标注）」「导入站点脚本 (.js)」「检查同步更新」）;页面提示区统一展示 `updateNote` 与 `note`（解决导入反馈错写到 `sourceSettingsNote` 导致不可见的 Bug）。

**视觉参数**:沿用 `DesktopSourceManageScreen` 既有页头参数(`design-tokens §2`)。

**状态**:

| 状态 | 表现 |
|---|---|
| 默认 | 按钮启用,菜单收起 |
| 悬停 / 按压 | 复用 `DesktopButton(Medium, Primary)` |
| 禁用 | `updateBusy = true` 时按钮置禁用态 |

**动画**:沿用 M3 `DropdownMenu` 默认弹入动画。

**Compose 映射**:`DesktopSourceManageScreen` 新增可选回调 `onOpenSourceStore`、`onCheckSourceUpdate` 与 `repoUiState`;导入脚本成功/失败改写 `sourceNote`（并在导入后同步写入来源标记与自动开启/刷新清单）。

## 3. `DesktopSourceStore` — 在线站点与仓库浏览弹窗

**解构**:`Dialog` 半透遮罩 → 居中圆角容器(`r16`, `tokens.color.surface`, `1.dp` 描边) → 顶栏(标题「在线站点」+ 仓库说明副行 + `刷新` / `关闭` `Medium` 按钮) → 控制栏(`DesktopSegmented` 三档仓库切换 + `只看未装/可升级` 切换 + `DesktopSearchField` 搜在线站点) → `LazyColumn`（每个条目用 `DesktopGlassCard(Nested, r10)` 包裹:左区名称 + 来源徽章 `laincat/vensrc`/`官方源` + 版本/作者说明;右区 `Compact 26.dp` 按钮或「已是最新」文本）。

**视觉参数**:引用 `design-tokens §1-§5`（`r16` 弹窗、`r10` 列表行、`DesktopSearchField` 34dp、`DesktopButton` Medium/Compact）。

**状态**:

| 状态 | 表现 |
|---|---|
| 加载中 | 列表区居中显示进度指示与「正在拉取仓库清单…」 |
| 空 / 失败 | 显示具体失败成因（含代理提示）与 `重试` 按钮 |
| 单行安装中 | 对应条目按钮切 `loading = true`,其余条目禁止并发写入 |
| 已装最新 / 可升级 / 未安装 | 分别显示 `已是最新` 三级字 / `升级`(`Primary Compact`) / `安装`(`Secondary` 或 `Primary Compact`) |

**动画**:列表行与按钮过渡采用 `VeneraMotion.tweenFast()`。

**Compose 映射**:重构 `DesktopSourceStore.kt`,接收当前选中仓库 `repoId` 与切换回调 `onSelectRepo`,复用 `DesktopSegmented`、`DesktopSearchField`、`DesktopButton`、`DesktopGlassCard`。

## 4. `SiteHeroCard` / `SiteCard` / `SiteDrawer` — 来源标注扩展

**解构**:在 `DesktopSourceRowUi` 新增 `origin: DesktopSourceOrigin`（枚举:`BUILTIN` / `OFFICIAL` / `VENSRC` / `LOCAL`）与 `originLabel: String`;在 `SiteHeroCard`、`SiteCard` 副行和 `SiteDrawer` 头部及「脚本」卡中把原本硬编码的 `"内置站点"` 替换为按真实来源渲染的 `StatusBadge`。

**视觉参数**:复用现有 `StatusBadge(text, positive = false)`（中性胶囊 `r999`,`10.sp`,不新增颜色）。

**状态**:

| 状态 | 表现 |
|---|---|
| 随包原版 | 徽章念 `内置站点` |
| 官方仓库同步版 | 徽章念 `官方源`（抽屉另显示 `已更新`） |
| 社区巡检版 (`laincat/vensrc`) | 徽章念 `laincat/vensrc`（抽屉脚本卡标明 `来源：laincat/vensrc`，若为同名升级可点「恢复内置脚本」，若为新增源可点「移除脚本」） |
| 本地导入 / 手工修改 | 徽章念 `本地脚本` 或 `已修改` |

**动画**:沿用既有卡片悬停 `160ms` 与抽屉 `260ms` 滑入。

**Compose 映射**:修改 `DesktopSourceRowUi`（保持向后兼容默认参数 `originLabel = "内置站点"` 不破坏现有单测构造）、`DesktopSourceUpdater` 清单增记来源仓库、`DesktopEngineHostActions` 装配来源标签。
