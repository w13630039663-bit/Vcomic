# 备份与还原 — 组件规格

规格名:`backup-restore-rework-2026-11-20` · 日期:2026-11-20 · 配套:`proposal.md` / `design-analysis.md` / `design-tokens.md`
实施文件(确认后):`DesktopBackupScreen.kt`、`DesktopBackup.kt`、`DesktopWebDavSync.kt`、`VeneraDesktopLargeScreen.kt`、以及配套单测。本轮**未改代码**。

## 1. `BackupAssetStrip` — 本机数据资产概览条

**解构**:`BoxWithConstraints` 容器 → 宽档单行 6 卡 `Row` / 窄档多行网格 → 单卡 `Column`（标题 + 大数值 + 副说明）。
**视觉参数**:卡圆角 `12.dp`，面色 `surface`，1dp 描边 `outlineVariant(0.08f)`，内距 `12.dp×14.dp`，数值 `20.sp Bold`。
**状态**:

| 状态 | 表现 |
|---|---|
| 默认 | 展示本机实时统计计数（初始化或刷新后更新） |
| 悬停 | 无交互（纯展示指标） |
| 按压 | 无 |
| 激活 / 选中 | 无 |
| 禁用 | 尚未加载完成时显示 `0` 或 `-` |

**动画**:无。
**Compose 映射**:纯展示函数，入参 `DesktopLocalAssetCounts`（由壳层 `LaunchedEffect` 异步只读查询后传入，屏本身零 `DatabasePorts` 引用）。

## 2. `BackupExportPanel` — 打包导出工作卡

**解构**:`Surface` 主卡 → 头部（图标槽 + 标题副标题 + `v5 互通格式` 徽标）→ 3×2 范围标签网格 → 源账号安全开关框 → 底部说明 + `导出 .venera 归档` 主按钮。
**视觉参数**:主卡圆角 `14.dp`，内距 `16.dp`，开关框圆角 `10.dp`，主按钮高 `36.dp` 使用 `primary` 色。
**状态**:

| 状态 | 表现 |
|---|---|
| 默认 | 源账号开关关闭，按钮可点击 |
| 悬停 | 主按钮高光、开关行微亮 |
| 按压 | 桌面无缩放语义，即时反馈 |
| 激活 / 选中 | 源账号开关打开时，开关框底色切 `caution` 浅底并显示「内含登录凭据，切勿外传」警示行 |
| 禁用 | `busy = true` 或 `!canFileDialog` 时禁用导出按钮（或隐藏文件框按钮并提示无头环境） |

**动画**:警示条显隐走 `AnimatedVisibility(fadeIn/fadeOut)`。
**Compose 映射**:复用 `VeneraSwitch` / `DesktopPushButton`（或统一主按钮样式），回调 `onToggleIncludeAccounts`、`onExport`。

## 3. `BackupWebDavPanel` — 内嵌 WebDAV 同步卡

**解构**:`Surface` 主卡 → 头部（云图标 + 标题 + 配置状态徽标）→ 四格输入表单（URL / 账号 / 密码带显隐切换 / 远端目录）→ 状态与测试连接行 → 双操作按钮（上传最新 / 拉取并预览）。
**视觉参数**:输入槽圆角 `8.dp`，高 `34.dp`，底色 `surfaceContainerLow`，聚焦描边 `primary(0.45f)`。
**状态**:

| 状态 | 表现 |
|---|---|
| 默认 | 展示已保存的配置与上次同步状态 |
| 悬停 | 次级按钮背景切 `surfaceContainer` |
| 按压 | 即时底色变化 |
| 激活 / 选中 | 输入框获焦时呈现主色细框 |
| 禁用 | `davBusy` 时上传/拉取按钮禁用并显示「传输中…」 |

**动画**:无。
**Compose 映射**:表单编辑态本地 `remember` 托管并在失焦/提交时回调 `onSaveWebDavConfig`，上传/拉取/测试连接由壳层调 `DesktopWebDavSync`。

## 4. `BackupImportWorkspace` — 三态导入与还原工作区

**解构**:`Surface` 主卡 → 头部（图标 + 标题 + 右侧格式状态徽标）→ 三态切换容器：
- 待选文件态：虚线框拖拽/选包区 + 支持格式徽标 + 云端中转最近文件快捷卡片；
- 解析预览态：包信息头（文件名/来源/版本/时间/重选）→ 策略分段器（智能合并 / 清空覆盖）→ 全选控制 + 7 项模块 `Checkbox` 清单 → 危险提示条 → 汇总计数 + 执行按钮；
- 完成报告态：成功横幅 + 6 格结果数 + 跳过明细提示条 + 重置按钮。
**视觉参数**:主卡 `14.dp` 圆角，拖拽区 `12.dp` 圆角虚线描边，清单行 `9.dp` 圆角，`surfaceContainerLow` 底色。
**状态**:

| 状态 | 表现 |
|---|---|
| 默认 | 待选态显示拖放指引；预览态默认选中「智能合并」并勾选包内存在的非敏感模块 |
| 悬停 | 拖拽区悬停或文件拖入时切 `primary(0.14f)` 浅底与主色边框；清单行悬停切 `surfaceContainer` |
| 按压 | 即时底色变化 |
| 激活 / 选中 | 策略选中项带主色边框（覆盖模式带红色边框与危险横幅）；二次确认时按钮文案变为「确认覆盖所选数据」 |
| 禁用 | `busy = true` 时禁用所有导入按钮；包内缺失某模块（如无 `source_accounts.json` 或旧 JSON 无收藏）时该行置灰不可选 |

**动画**:三态切换使用 `AnimatedContent(fadeIn/fadeOut, 120ms)`。
**Compose 映射**:保留既有 `armed` / `busy` / `canFileDialog` 安全语义与 `onArmRestore` / `onConfirmRestore` 纪律（覆盖模式下必须两步确认才落写入），全部状态经纯数据模型从壳层传入，屏本体零 IO、零数据库引用。
