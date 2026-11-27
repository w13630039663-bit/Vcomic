# 站点墙源仓库切换（接入 laincat/vensrc）— 设计 Token

规格名:`source-repo-switch-2026-11-27` · 日期:2026-11-27
全部取 `VeneraTokens`、`LocalChromePalette` 与既有规范正典,**不造新色**。
标注口径:**【确定】= 规范文档 / 代码直接可读**;**【规格值】= 本方案新设定值**。

## 1. 全局颜色 Token

| Token | 浅色 | 深色 | 用途 | 标注 |
|---|---|---|---|---|
| `tokens.color.primary` | `#0A63F6` | `#0A84FF` | 主按钮(`添加站点`、有升级时的`检查同步`、`安装/升级`)、悬停描边高亮 | 确定 · 全站唯一装饰色 |
| `DesktopGlassCardDefaults` (`Primary`) | `surface` / `surfaceContainerHigh` | `surfaceContainerHigh @ 0.45 / 0.60` | `SiteHeroCard` / 弹窗前景底 | 确定 · `glass-card-spec.md §1` |
| `DesktopGlassCardDefaults` (`Nested`) | `surfaceContainerHigh` / `surfaceVariant` | `surfaceContainerHigh @ 0.35 / 0.50` | `SourceRepoBar`、在线源列表行、`DrawerCard` | 确定 · `glass-card-spec.md §1` |
| `palette.segmentTrack` | `LocalChromePalette.segmentTrack` | `LocalChromePalette.segmentTrack` | `DesktopSegmented` 仓库与轨道分段底 | 确定 · `controls-standard-spec §2` |
| `palette.card` | `LocalChromePalette.card` | `LocalChromePalette.card` | `DesktopSegmented` 选中滑块底 | 确定 · `controls-standard-spec §2` |
| `tokens.color.textPrimary` | `textPrimary` | `textPrimary` | 页标题、弹窗标题、站点名 | 确定 · `VeneraTokens` |
| `tokens.color.textSecondary` | `textSecondary` | `textSecondary` | `laincat/vensrc` 来源标签字色、分段未选态 | 确定 · `VeneraTokens` |
| `tokens.color.textTertiary` | `textTertiary` | `textTertiary` | 副行版本/计数、胶囊徽章文字 | 确定 · `VeneraTokens` |
| `GalleryTokens.Color.StatusSuccess` | `#12915F` | `#34D399` | 仅「已登录」正态徽章复用,不作他用 | 确定 · `DesktopSourceManageScreen.kt:859` |

## 2. 尺寸 Token

| 件 | 值 | 标注 |
|---|---|---|
| 仓库控制条外边距 | 水平 `28.dp`,底距 `14.dp` | 确定 · 与 `DesktopSourceManageScreen.kt:202` 对齐 |
| 仓库控制条内容内距 | 水平 `14.dp`,垂直 `10.dp` | 规格值 |
| 分段控件高 (`DesktopSegmented`) | 标准 `28.dp`（选项 `24.dp`） | 确定 · `controls-standard-spec §2` |
| 工具栏与列表行按钮 (`DesktopButton Compact`) | 高 `26.dp` | 确定 · `controls-standard-spec §3.1` |
| 弹窗头按钮 (`DesktopButton Medium`) | 高 `32.dp` | 确定 · `controls-standard-spec §3.1` |
| 搜索框 (`DesktopSearchField`) | 最小高 `34.dp`,窄 `172.dp` / 弹窗 `220.dp` | 确定 · `controls-standard-spec §1` |
| 在线站点弹窗尺寸 | 宽 `min(820.dp, 屏幕 - 48.dp)`,高 `min(660.dp, 屏幕 - 48.dp)` | 规格值 |

## 3. 间距 Token

| 位置 | 值 | 标注 |
|---|---|---|
| `SourceRepoBar` 内部控件间距 | 同行 `8.dp` ~ `10.dp`,换行行距 `8.dp` | 规格值 |
| 站点墙卡片网格间距 | `14.dp` | 确定 · `DesktopSourceManageScreen.kt:235, 275` |
| 在线站点列表行垂直间距 | `8.dp` | 规格值 |
| 抽屉头部徽章间距 | `7.dp` | 确定 · `DesktopSourceManageScreen.kt:922` |

## 4. 字阶 Token

| 元素 | 字号/字重 | 灰阶 | 标注 |
|---|---|---|---|
| 弹窗主标题(`在线站点`) | `VeneraType.pageTitle` (`SemiBold`) | `textPrimary` | 确定 · `DesktopSourceStore.kt:109` |
| 仓库条前导标签 / 状态摘要 | `VeneraType.aux` | `textTertiary` + `tabular` | 确定 · `VeneraType` |
| 站点列表行主标题 | `VeneraType.body` / `SemiBold` | `textPrimary` | 确定 · `DesktopSourceStore.kt:204` |
| 来源胶囊徽章 | `10.sp`（卡片）/ `11.sp`（仓库条） | `textSecondary` / `textTertiary` | 确定 · `DesktopSourceManageScreen.kt:872` |
| 版本与仓库说明副行 | `VeneraType.micro` | `textTertiary` | 确定 · `DesktopSourceStore.kt:217` |

## 5. 圆角 Token(同心圆几何)

| 件 | 外层圆角 | 内层圆角 | 关系(外 = 内 + 内距) | 标注 |
|---|---|---|---|---|
| 仓库控制条 (`SourceRepoBar`) | `12.dp` | `7.dp` / `6.dp` | `12` 外框配 `10dp` 垂直内距与内部 `28dp/26dp` 控件 (`r7`/`r6`) 视觉和谐 | 规格值 |
| 分段控件 (`DesktopSegmented`) | `7.dp` | `5.5.dp` | `7 - 2 ≈ 5`（标准件固定 `5.5.dp`） | 确定 · `controls-standard-spec §2` |
| 在线站点弹窗外壳 | `16.dp` | `10.dp`（列表行） | 内距 `18.dp`、`8.dp` 间距 | 规格值 |
| 在线站点列表行 | `10.dp` | `6.dp`（行尾 Compact 按钮） | 垂直内距 `10.dp`,同心协调 | 规格值 |
| 来源胶囊 | `999.dp` | — | 全圆角胶囊 | 确定 · `DesktopSourceManageScreen.kt:868` |

## 6. 阴影与动效 Token

| 项 | 值 | 标注 |
|---|---|---|
| 卡片与仓库条阴影 | 暗色无投影;仅在线弹窗浮层按 `vShadow` 规范处理 | 确定 · `notation-rules §3.1` |
| 状态色过渡 | `VeneraMotion.tweenFast()`（`150ms`） | 确定 · `controls-standard-spec §3.3` |
| 按钮按压反馈 | 复用 `DesktopButton`（按压 `0.96f` 标准件内置）;仓库条本身不可整体按压 | 确定 · `controls-standard-spec §3.3` |
| 社区轨道条件显隐 | `AnimatedVisibility(fadeIn + expandHorizontally, VeneraMotion.tweenFast())` | 规格值 |
