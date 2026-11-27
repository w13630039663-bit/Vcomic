# 首页源状态收齐与站点延迟检测页 — 组件拆解规格

## 1. 组件清单与归属

| 组件名 | 文件 | 职责 |
|---|---|---|
| `selectHomeCompactSourceRows` & 汇总函数 | `DesktopHomePane.kt`（或复用 helper） | 将全量测速行按严重度排序并截断至最多 5 行，产出 `ok/slow/bad/untested` 汇总数 |
| `DesktopHomeSourceTable`（升级） | `DesktopHomePane.kt` | 经典首页左卡：标题 + 摘要胶囊 + 最多 5 行焦点行 + 底部收口入口（保留 `onGoSourceManage`，新增 `onOpenSourceLatency`） |
| `SourceStatusPanel`（升级） | `DesktopHomeLightScreen.kt` | 浅色首页左卡：与经典版同口径限高 ≤5 行 + 汇总药丸 + 「延迟检测 ›」与收口行 |
| `DesktopSourceLatencyItem` / `DesktopSourceLatencyScreen` | `sourcemanage/DesktopSourceLatencyScreen.kt`（新建） | 独立延迟检测页：页头、4 张 KPI 卡、筛选排序工具栏、5 列检测明细列表 |
| `DesktopSourceProbeEntry` / 测速协调 | `app/DesktopLargeScreenMiscEffects.kt` & `DesktopLargeScreenWallAndHomeState.kt` | 支持「首页自动探测（60s 冷却）」+「全量/单站主动重测」+「包含未启用站点探测」共享同一延迟状态池 |
| `DesktopRoute.SourceLatency` | `DesktopRoute.kt` & `DesktopComicAndSourceHost.kt` | 二级路由承载延迟检测页，支持从首页与站点管理双向进入与返回 |

## 2. 控件复用映射（严禁自造新控件）

| 原型组件 | 桌面正式组件 |
|---|---|
| 返回按钮 | `DesktopBackButton` |
| 「全部重测」「站点管理」「重测」「设置 ›」 | `DesktopButton` (`Medium` / `Compact`，`Primary` / `Secondary`) |
| 筛选分段「全部 / 正常 / 偏慢 / 异常 / 未测」 | `DesktopSegmented` |
| 排序选择框 | `DesktopPopUpButton` |
| 「包含未启用站点」开关 | `DesktopSwitch` |
| 搜索站点名或域名框 | `DesktopSearchField` |
| KPI 卡、工具卡、明细行外框 | `DesktopGlassCard(level = DesktopGlassCardLevel.Nested)` |

## 3. 状态与交互规则

1. **首页限高与焦点排序算法（纯函数可单测）**：
   - 排序优先级：`probing (0) < bad/failed/超过2500ms (1) < slow (2) < untested (3) < ok (4)`，同档保留原始顺序。
   - 截断 `take(5)`；剩余数量 `overflowCount = (rows.size - 5).coerceAtLeast(0)`。
   - 当 `overflowCount > 0` 时，卡片底部显示「另有 N 个站点已收拢到检测页」及「查看全部延迟详情 ›」。
2. **延迟检测页筛选与排序（纯函数可单测）**：
   - 状态筛选：全部 / 正常 / 偏慢 / 异常（含失败和超时）/ 未测。
   - 排序模式：异常优先（与首页排序一致）/ 延迟从高到低 / 延迟从低到高 / 按名称字典序。
   - 搜索过滤：不区分大小写匹配 `name`、`key`、`host`。
   - 数据开关：`includeDisabled = false`（默认只看画廊固定项 + 已启用站点）；开启后将未启用站点及其主机一并列出并允许主动测速。
3. **重测联动**：
   - 触发单站 `reprobeHost(host)`：清掉该 `host` 的失败/冷却标记，加入 `homeProbingHosts`，通过 `DesktopSourceLatencyProbe.probe(host)` 刷新 `homeLatencyByHost` 或 `homeFailedHosts`。
   - 触发 `reprobeAll(hosts)`：按当前列表顺序并发测速并实时刷新各行及顶部进度，测完自动恢复按钮态。
   - 所有测速状态直接写入同一份 `homeLatencyByHost` / `homeFailedHosts` / `homeProbingHosts`，返回首页即见最新汇总与焦点行。
4. **架构守卫兼容说明**：
   - 保留首页 `onGoSourceManage`（3 处）及全部 5 处到 `DesktopRoute.SourceManage` 的入口赋值不变，确保 `DesktopSourceManageEntryTest` 零破坏。
   - 不新增 `desktopEngineCall`，纯网络延迟探针继续走 `DesktopSourceLatencyProbe`，确保 `DesktopEngineCallCoverageTest` 零破坏。
   - 新 UI 组件测试使用 `setContentThemed`，不使用 `172.dp` 或 `200f`，确保 `DesktopThemeLayerTest` 与 `DesktopWidthCaliberDriftTest` 零破坏。
