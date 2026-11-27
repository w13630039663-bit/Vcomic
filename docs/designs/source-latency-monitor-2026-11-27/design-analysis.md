# 首页源状态收齐与站点延迟检测页 — 设计分析

规格名：`source-latency-monitor-2026-11-27` · 日期：2026-11-27
标注口径：**【确定】= 规范文档 / 代码直接可读**；**【规格】= 经审定 HTML 设计稿约束值**。

## 1. 页面与联动结构

1. **首页双栏对齐区（经典深色首页 `DesktopHomePane.kt` & 浅色首页 `DesktopHomeLightScreen.kt`）**：
   - 外层两栏容器保留 `IntrinsicSize.Max` + `16.dp` 间距【确定 · `DesktopHomePane.kt:250-267`、`DesktopHomeLightScreen.kt:271-276`】。
   - 左侧「源状态」卡结构调整为四段：
     - **头部行**：主标题 `源状态` + 副标 `真测延迟 · 共 N 站` + 右侧蓝字入口 `延迟检测 ›`（与右卡 `最近阅读 · 全部 ›` 对称）。
     - **摘要药丸条**：动态汇总四态数量胶囊（`正常 N` / `偏慢 N` / `异常 N` / `未测 N`），数量为 0 的告警/未测胶囊不占位。
     - **焦点列表（最多 5 行）**：按严重程度排序（`测速中 > 异常 > 偏慢 > 未测 > 正常`），超出 5 行的已启用站点不再铺在首页卡片内，避免撑爆左侧高度。
     - **底部收口栏**：左侧辅助说明（`另有 N 个站点已收拢到检测页` / `已显示全部启用站点`），右侧动作 `查看全部延迟详情 ›` 点击进入完整检测页；保留原有 `站点 ›` 入口兼容性不碰 `DesktopSourceManageEntryTest` 既有 5 处 `onGoSourceManage` 计数。

2. **独立「站点延迟检测」页面（新增 `DesktopSourceLatencyScreen.kt` + 路由 `DesktopRoute.SourceLatency`）**：
   - 入口：① 首页「源状态」卡片（`延迟检测 ›` / `查看全部延迟详情 ›`）；② 「站点」管理页 `WallHeader` 副按钮 `延迟检测`（仅当回调接上时展示）。
   - 页面自上而下分五层：
     - **岛屿 A · 页头操作条**：返回按钮（遵循 `LocalShellBackHandled`）、主标题 `站点延迟检测`、副标阈值与代理说明、次按钮 `站点管理`、主按钮 `全部重测`（测速进行时 `loading = true`）。
     - **岛屿 B · 4 列 KPI 概览卡**：`监控站点`（含平均延迟）、`响应正常 (≤800ms)`（含占比）、`连接偏慢 (≤2500ms)`、`异常 / 未通`（四等分 `DesktopGlassCard(Nested)`）。
     - **岛屿 C · 筛选与排序工具卡**：五态分段 `DesktopSegmented`（`全部 (N)` / `正常 (N)` / `偏慢 (N)` / `异常 (N)` / `未测 (N)`）、排序下拉 `DesktopPopUpButton`（`异常 / 偏慢优先` / `延迟从高到低` / `延迟从低到高` / `按名称排序`）、`DesktopSwitch` 开关（`包含未启用站点`）、`DesktopSearchField` 搜索（支持按站名、key、主机域名搜索）。
     - **岛屿 D · 明细表头与可滚动行列表**：每站一条 `DesktopGlassCard(Nested)` 卡片行，展示站点首字徽章、站点名、来源标签（`画廊固定` / `内置站点` / `laincat/vensrc` / `官方源` / `本地脚本`）、`需代理` 与 `未启用` 标注、等宽主机域名、横向延迟比例条与精确毫秒文本、状态胶囊、单行 `重测` 钮与 `设置 ›` 跳转（画廊固定行仅支持 `重测`，漫画站可跳站点详情抽屉）。

## 2. 区域尺寸与比例

| 区域 | 尺寸 / 占比 | 标注 |
|---|---|---|
| 首页源状态最多行数 | `5` 行（每行 `padding(vertical = 6~7.dp)`） | 规格值（确保总高 ≤ 右侧 3~4 条最近阅读约 `290~320.dp`） |
| 延迟检测页水平边距 | 左右各 `28.dp`，底部 `32.dp` | 确定 · 对齐 `DesktopSourceManageScreen.kt:213` |
| 4 列 KPI 卡片网格 | 4 等分 `weight(1f)`，列间距 `12.dp`，卡片内距 `14×12.dp` | 规格值 · 对齐 `ui-mockup.html` |
| 工具栏卡片 | 全宽，内距 `14×10.dp`，元素间距 `10.dp` | 规格值 · `FlowRow` 窄屏可折行 |
| 明细行 5 列比例 | 站点与来源 `2.1f` : 探测域名 `1.6f` : 延迟条与数值 `1.5f` : 状态 `0.8f` : 操作 `1.2f` | 规格值 · 对齐 `ui-mockup.html` |
| 延迟进度条槽 | 固定最大宽 `100.dp`，高 `6.dp`，全圆角 `999.dp` | 规格值 |

## 3. 视觉层次与桌面语义约束

- 全站透明/实底玻璃走 `DesktopGlassCard(DesktopGlassCardLevel.Nested)`，暗色模式严禁裸叠 `Modifier.shadow`，由 `VeneraTokens` + `GalleryTokens` 统一供色。
- 全站唯一装饰色为 `tokens.color.primary`（浅 `#0A63F6` / 深 `#0A84FF`），用于主按钮 `全部重测`、测速中脉冲点、链接与激活控件。
- 状态语义色沿用全仓标准：`正常` = `GalleryTokens.Color.StatusSuccess`，`偏慢` = `GalleryTokens.Color.ToneCaution`，`异常` = `GalleryTokens.Color.StatusDanger`，`未测` = 中性灰 `outlineVariant @ 0.25`。
- 支持 `DesktopRoute` 双向来路返回：从首页进则返回首页 `Tab`，从站点管理页进则按需返回上一层或站点管理。
