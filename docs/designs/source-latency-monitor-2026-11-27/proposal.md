# 方案稿：首页源状态卡片限高齐平 + 独立「站点延迟检测」页面

- 目录：`desktop/docs/designs/source-latency-monitor-2026-11-27/`
- 视觉交互稿：[ui-mockup.html](file:///g:/venera-desktop/desktop/docs/designs/source-latency-monitor-2026-11-27/ui-mockup.html)
- 档位：大型重做（跨首页卡片改版 + 新增独立二级页面与路由入口，先出 HTML 交互稿审定，批准并完成阶段 B 三件套前不落业务 UI 代码）

## 1. 现状问题
1. **【确定 · `DesktopHomePane.kt:249-267`、`DesktopHomePane.kt:861-987`、`DesktopHomeLightScreen.kt:721-766`】**
   首页底部「源状态 | 最近阅读」两栏当前在 `IntrinsicSize.Max` 行中直接渲染全部 `homeSourceRows`（`pixiv`、`yande.re` + 全部已启用漫画源）。当用户启用 30+ 站点时，左卡被拉长至千余像素，右侧仅有 3~4 条记录的「最近阅读」留出大面积空白（见主人提供截图）。
2. **【确定 · `DesktopHomePane.kt:881-888`、`DesktopHomeLightScreen.kt:751-764`】**
   首页「源状态」卡片仅显示静态列表和一个「站点 ›」入口，无法按延迟排序、按正常/偏慢/异常筛选，也无法单站或一键全量重测，或查看每个站具体在测哪个域名。

## 2. 方案分层

### 2.1 视觉布局（两块联动）
1. **首页「源状态」卡片收齐高度**：
   - 在经典深色与浅色两套首页皮肤下，左侧「源状态」卡片不再铺出所有站点。
   - 卡片头部保留 `源状态` + `真测延迟`，右侧入口升级为 `全部测速 ›`（直接进入「站点延迟检测」页），副栏展示紧凑的四档汇总数（例如 `正常 29 · 偏慢 2 · 异常 1`）。
   - 正文最多展示 **4～5 条精选行**（排序优先级：异常/偏慢靠前提醒 > 常驻核心站），使左侧卡片自然内容高度与右侧「最近阅读」（3～4 本，约 280～310dp）大致齐平甚至更 compact，不再出现半边空洞。
   - 卡片底部收口一行轻量摘要入口：`还有 N 个站点已开启 · 查看完整延迟检测 ›`（≤5 个站点时自动不展示多余折叠行）。
2. **新增「站点延迟检测」独立页面（二级路由 `DesktopRoute.SourceLatency`）**：
   - 入口：① 首页「源状态」卡片右上角 `全部测速 ›` 与底部收口行；② 「站点」管理页页头副钮 `延迟检测`。
   - 顶部栏：`← 返回` + 标题 `站点延迟检测` + 代理模式小字提示 + 主色按钮 `全部重测`（测速中显示实时完成进度）+ 次级按钮 `站点管理`。
   - 四张状态统计卡：总监控站点数、正常（≤800ms）、偏慢（801~2500ms）、异常/未通（含失败或 >2500ms）。
   - 控制工具条：`DesktopSegmented` 筛选（`全部` / `正常` / `偏慢` / `异常` / `未测`）、排序切换（异常优先/慢速优先/快速优先/按名称）、开关「仅看已启用 / 包含未启用站点」、站名与域名搜索框 `DesktopSearchField`。
   - 检测明细行（复用 `DesktopGlassCard(Nested)`）：左侧站点名 + 来源标记（`内置站点` / `laincat/vensrc` / `需代理`），中间显示实测主机域名（如 `godamh.com`）与延迟比例进度条 + 毫秒值，右侧显示三态胶囊、`重测` 单站按钮与 `设置/配置 ›` 跳转。

### 2.2 交互与数据约定（保留原有真测探针与状态源）
- 复用现有 `DesktopSourceLatencyProbe`（≤800ms 正常 / ≤2500ms 偏慢 / 其余或失败异常）和主机去重缓存 `homeLatencyByHost` / `homeFailedHosts` / `homeProbingHosts`。
- 在首页、源管理页、延迟检测页之间共享同一份内存延迟表：任何一处触发重测，首页和检测页同步刷新。
- 不改变任何现有业务逻辑、源开启账本与探针规则。

## 3. 实施记录（与初稿差异）
1. **首页限高行数定为 `4` 行（初稿估算 `4~5` 行）**：对照截图右侧 3 条「最近阅读」卡片真实高度（约 `280~290.dp`），在 [DesktopSourceLatencyScreen.kt](file:///g:/venera-desktop/desktop/desktop/src/main/kotlin/com/venera/desktop/sourcemanage/DesktopSourceLatencyScreen.kt) 将 `HOME_SOURCE_STATUS_MAX_ROWS` 定为 `4`，并在 [DesktopHomeLightScreen.kt](file:///g:/venera-desktop/desktop/desktop/src/main/kotlin/com/venera/desktop/ui/DesktopHomeLightScreen.kt) 与 [DesktopHomePane.kt](file:///g:/venera-desktop/desktop/desktop/src/main/kotlin/com/venera/desktop/ui/DesktopHomePane.kt) 为双栏容器统一挂上 `IntrinsicSize.Max` + `fillMaxHeight()`，确保左右卡片底边齐平、绝不拖高。
2. **回调命名保持架构尺子兼容**：延迟检测页跳转站点管理的回调命名为 `onGoSourceManage`，保留既有 `onOpenSourceManage` 两处官方入口计数规则不变（`DesktopSourceManageEntryTest` 同步登记延迟检测页至 `DesktopRoute.SourceManage` 的 2 处正规路由切换）。
3. **测试范围与状态**：定向测试套件已编译并全绿通过，待主人界面实机截图确认后统一补写 `docs/CHANGELOG.md` 并 commit。
