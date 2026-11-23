# 超大文件拆分调查（P3）

> 2026-11-22 只读调查。结论用于后续分批执行；本文档本身不含任何代码改动。
> 口径遵循 `docs/CODE_REVIEW.md` 4.7：只堵增量，不做一次性大重构。

## 1. 结论摘要

- 触发线定为 **1500 行**：当前 11 颗文件越过该线，最大一颗 9562 行，单颗 Composable 函数最长 7755 行。
- 拆分不是"按行数切文件"，而是**按功能区块切**：11 颗里 6 颗有天然的独立 overlay / 卡片 / 弹窗边界，可低风险先拆；3 颗屏级主体是单个巨型 Composable，必须先抽子 Composable 才能见行数；2 颗属于装配/进程入口，放最后。
- 拆分前有两件事要先判定，不能边拆边判：
  1. `compose/feature/ComicDetailScreen.kt` 已被桌面自持详情页替代，生产调用为零，仅两颗测试按文本断言它。**先判定是否下线，再谈拆分**。
  2. `VeneraDesktop.kt` 的 `main` 是 1352 行的进程入口，拆它等于改启动路径，必须有可回滚的单批提交。
- 测试面耦合大：11 颗文件在 `src/test` 里有 **215 处引用、67 个测试类**，其中大量是源码文本扫描（`DesktopSourceTree.desktopSourceOf(...)` 按精确路径读文件）。每拆一颗都要同步路径与断言目标，否则测试红在"读不到文件"。

## 2. 现状

| 文件 | 行数 | 主体跨度 | 主要区块 |
|---|---:|---|---|
| `desktop/VeneraDesktopLargeScreen.kt` | 9562 | `VeneraDesktopLargeScreen` 346–8101（7755 行） | 侧栏/顶栏/探索/搜索/详情接线/画廊墙/备份/Pixiv tab/下载中心 |
| `desktop/settings/DesktopSettingsHub.kt` | 2820 | `DesktopSettingsHub` 237–2495（2258 行） | 外观/阅读/下载/网络/账号/数据备份/关于 七个分区 |
| `desktop/pixiv/DesktopPixivWorkScreen.kt` | 2797 | 主屏 396–1172（776 行）+ 信息面板 1174–2432（1258 行） | 大图区/ugoira/信息栏/评论/相关作品/画师面板 |
| `desktop/detail/DesktopComicDetailScreen.kt` | 2524 | 主屏 275–558 + 内容 560–939 | 骨架/Hero/预览图/章节卡/评论/收藏弹窗 |
| `desktop/reader/DesktopReaderScreen.kt` | 2431 | 主屏 151–1917（1766 行） | 画布/翻页/滚动/自动播放/局部放大/切话提示 |
| `compose/feature/ComicDetailScreen.kt` | 2270 | `ComicDetailScreenBody` 112–1753（1641 行） | 封面/主操作/标签/简介/预览/章节/推荐/评论/收藏面板 |
| `desktop/gallery/ui/DesktopGallerySearchScreen.kt` | 2066 | 主屏 126–582；其余按区块 | 命令栏/筛选检视栏/联想下拉/快速条/Pixiv 过滤 |
| `desktop/reader/DesktopReaderHud.kt` | 2035 | 多个独立 overlay | 顶/底 HUD、缩略图、目录、设置浮层、五个设置 tab、两个对话框、放大镜 |
| `desktop/DesktopHomePane.kt` | 1847 | 主屏 122–312；卡片族 314–1428 | Hero/发现/画师带/源状态/最近阅读/本地下载/马赛克 |
| `desktop/VeneraDesktop.kt` | 1692 | `main` 246–1598（1352 行） | 窗口创建/路由装配/持久化关闭/取证/网页登录 |
| `desktop/downloads/DesktopDownloadsPipelineScreen.kt` | 1597 | 主屏 239–795（556 行）；卡片 969–1372 | 队列/脉动统计/本机内容/目录弹窗/删除确认 |

## 3. 分批方案

排序原则：先拆"独立展示件、零状态耦合、测试只需改路径"的，再拆屏级主体，最后动装配入口。

### 批 1：独立展示件（低风险，先拿收益）

| 文件 | 拆法 | 目标文件 |
|---|---|---|
| `DesktopReaderHud.kt` | 按 overlay 边界切：顶/底 HUD、缩略图条、目录、设置浮层（含五个 tab）、两个配置对话框、放大镜 | `DesktopReaderHudTop.kt`、`DesktopReaderHudBottom.kt`、`DesktopReaderToc.kt`、`DesktopReaderSettingsOverlay.kt`、`DesktopReaderDialogs.kt`、`DesktopReaderMagnifier.kt` |
| `DesktopDownloadsPipelineScreen.kt` | 主屏保留；统计带/队列行/漫画卡/图片卡/目录弹窗/删除确认分文件 | `DownloadsPipelineCards.kt`、`DownloadsPipelineDialogs.kt`、`DownloadsPipelineStats.kt` |
| `DesktopHomePane.kt` | 主屏保留；Hero/发现卡族/马赛克/源状态表分文件 | `DesktopHomeHero.kt`、`DesktopHomeCards.kt`、`DesktopHomeMosaic.kt` |

验收：每颗拆完 `compileKotlin` + 点名相关测试 + 单批提交；目标单文件 ≤1500 行。

### 批 2：屏级主体抽子 Composable（中风险）

| 文件 | 拆法 | 风险点 |
|---|---|---|
| `DesktopGallerySearchScreen.kt` | 命令栏、筛选检视栏、联想下拉、快速条、Pixiv 过滤各成文件；主屏只留状态与装配 | 主屏向子件传 20+ 参数；不要把状态搬进 `LocalXxx`（`DesktopUiIsolationTest` 禁止 UI 找静态口） |
| `DesktopPixivWorkScreen.kt` | 主屏（大图/ugoira）与信息面板拆开；评论行、相关作品、画师面板再分 | 信息面板与主屏共享 `PixivWorkPage` 与若干动画状态，先抽"纯展示子件"再抽有状态件 |
| `DesktopSettingsHub.kt` | 按七个分区抽 `SettingsSectionXxx`；搜索框、卡片壳、用户卡独立 | 每个分区读同一批 prefs 流；抽子件时传值不传整颗 prefs，避免隐式共享 |

验收：点跑各屏的语义测试（`DesktopGallerySearchUiTest`、`DesktopNewScreensSemanticsTest`、`DesktopSettingsEntryTest` 等），行为不变。

### 批 3：详情页族（中高风险）

| 文件 | 拆法 | 前置 |
|---|---|---|
| `DesktopComicDetailScreen.kt` | 骨架屏、Hero、预览图、章节卡、评论区、收藏弹窗分文件；主屏只留数据与装配 | 先确认 `ComicDetailCore` 的入口边界，避免把读写核拆散 |
| `compose/feature/ComicDetailScreen.kt` | **先判定下线**，不拆 | 生产调用为零；两颗测试按文本守护它。若确认桌面不再使用，整体下线比拆分更符合"只堵增量" |

### 批 4：装配/入口（高风险，单独提交）

| 文件 | 拆法 | 风险点 |
|---|---|---|
| `DesktopReaderScreen.kt` | 引导视口、侧栏面板、分页单元抽出；主屏保留手势与状态机 | 翻页/滚动状态机是核心，抽件时不要复制状态 |
| `VeneraDesktopLargeScreen.kt` | 先把主屏按路由区块抽成 `DesktopSideBarShell`、`DesktopTopBar`、`DesktopExploreHost`、`DesktopSearchHost`、`DesktopDetailHost`、`DesktopWallHost`、`DesktopDownloadsHost`、`DesktopPixivHost`；主屏只留路由分发 | 7755 行单函数，状态量最大；每抽一块编译一次，不做"一次性重写" |
| `VeneraDesktop.kt` | `main` 抽 `createWindow`、`wireRoutes`、`closePersistence`、`runForensics`；`main` 只留参数解析与顺序 | 启动路径，改完必须真起一次窗（人工或 `-Pshot`）+ 全量测试 |

## 4. 统一约束

1. **不改行为**：每批只做"移动 + 显式传参"，不顺带修 bug、不改文案、不动样式。
2. **参数显式**：跨文件子 Composable 一律形参传入；禁止用 `LocalXxx` 或单例把状态"绕"过去。
3. **测试同步**：拆文件时同步 `DesktopSourceTree` 调用与文本扫描断言；不允许把断言改成"随便哪颗文件里有这行字"。
4. **源码尺**：`DesktopShellLayoutTest` 扫 `VeneraDesktop.kt`、`DesktopPageTemplateUsageTest` 扫共享详情屏，拆到哪颗就把尺子指到哪颗。
5. **提交节奏**：一颗文件一批，编译 + 点名测试 + 独立 commit（不 push）；全量 `:desktop:test` 只在批末跑一次。
6. **目标线**：单文件 ≤1500 行；主 Composable ≤800 行。达不到就继续按区块抽，不强行合并。

## 5. 不建议做的事

- 不为了行数把无状态纯函数硬塞进 `utils` 文件；拆分的单位是"屏幕区块/overlay/卡片族"，不是"行数"。
- 不在拆分批次里做全量格式化（存量 1700+ 条 import 排序/未用引入告警，属独立批次）。
- 不动 `compose` → `desktop` 的依赖方向，不为拆文件新增跨层引用。

## 6. 执行记录

### 批 3（详情页族）· 已完成

- `DesktopComicDetailScreen.kt` 2524 → 803 行，按区块拆成六颗成员文件：
  `DesktopComicDetailScreen.kt`（数据装配与主屏）、`DesktopComicDetailModels.kt`（视图数据与胶囊模型）、
  `DesktopComicDetailSkeleton.kt`（装载骨架）、`DesktopComicDetailHero.kt`（信息头）、
  `DesktopComicDetailSections.kt`（正文分区与纯判据）、`DesktopComicDetailFavoriteDialog.kt`（收藏夹弹窗）。
- 测试面同步：`DesktopSourceTree` 新增 `desktopComicDetailSources()` 成员表（**显式列举**，不扫目录）与
  `desktopComicDetailCode/Lines/Occurrences` 三枚合并尺；收藏写侧、评论区两条断言改按成员表读，
  其余断言仍指具体文件。变更批次：`11c7fc48`（弹窗）、`477d6a13`（四颗成员）。
- `compose/feature/ComicDetailScreen.kt` **下线判定结论：可以下线，但不在本轮执行**。
  证据：`ComicDetailScreenBody` 在 desktop 生产源码里除定义外零出现（其余全是注释/KDoc 引用），
  本仓无 `android` 源集，整仓无生产调用方。未执行原因：整屏下线属独立批次（4.1 不改行为、4.5 一颗一批），
  且它是共享层入口，删除会牵动 `DesktopPageTemplateUsageTest`/`DesktopCommentsEntryTest` 的守护尺。
  待下线时按"删文件 + 同步两把尺 + 全量测试"单独提交。

### 批 1（独立展示件）· 已完成

| 文件 | 原行数 | 现行数 | 新成员文件 |
|---|---:|---:|---|
| `DesktopReaderHud.kt` | 2035 | 806 | `DesktopReaderSettingsOverlay.kt`（1285） |
| `DesktopDownloadsPipelineScreen.kt` | 1597 | 1023 | `DownloadsPipelineCards.kt`（611） |
| `DesktopHomePane.kt` | 1847 | 1117 | `DesktopHomeCards.kt`（766） |

### 批 2（屏级主体）· 已完成

| 文件 | 原行数 | 现行数 | 新成员文件 |
|---|---:|---:|---|
| `DesktopGallerySearchScreen.kt` | 2066 | 992 | `GallerySearchControls.kt`（1130） |
| `DesktopPixivWorkScreen.kt` | 2797 | 1130 | `DesktopPixivWorkInfoPane.kt`（1352）、`DesktopPixivPaneParts.kt`（423） |
| `DesktopSettingsHub.kt` | 2820 | 2461 | `SettingsHubControls.kt`（387） |

**未达标项（显式记下，不假装做完）**：`DesktopSettingsHub.kt` 主屏仍是约 2225 行**单函数**，
外围控件已切出但主 Composable 未降线。它要按七个分区抽 `SettingsSectionXxx` 子 Composable，
每段形参显式传值（禁止传整颗 prefs / 禁止 `LocalXxx` 绕状态），属独立批次；
按 4.6 "达不到就继续按区块抽，不强行合并"处理。

**口径**：跨文件的顶层声明一律 `internal`（同包内可见），未新增任何跨层引用；
`DesktopWidthCaliberDriftTest` 的成员豁免按拆分后的文件名同步（`200f` 滑杆、`72dp` 骨架块）。

### 批 4（装配/入口）· 部分完成

| 文件 | 原行数 | 现行数 | 新成员文件 |
|---|---:|---:|---|
| `DesktopReaderScreen.kt` | 2431 | 1892 | `DesktopReaderOverlays.kt`（581：导览视口、页格、侧栏面板、页条） |
| `VeneraDesktop.kt` | 1692 | 1447 | `DesktopAppSupport.kt`（154：收藏/历史/自定义收藏夹/取证常量）、`DesktopSelfShot.kt`（109：自截图与抬窗） |
| `VeneraDesktopLargeScreen.kt` | 9562 | 576 | 批 4c：`DesktopShellPanes.kt`（567）、`DesktopWallScaffold.kt`（781）、`DesktopWallUiStates.kt`（266）<br>批 4d：`DesktopLargeScreenState.kt`（534）、`DesktopLargeScreenStateInit.kt`（1322）、`DesktopLargeScreenWiring.kt`（758）、`DesktopLargeScreenChrome.kt`（627）、`DesktopLargeScreenRouter.kt`（868）、`DesktopLargeScreenMiscEffects.kt`（477）、`DesktopLargeScreenHelpers.kt`（603）、`DesktopComicAndSourceHost.kt`（1213）、`DesktopPixivRoutesHost.kt`（751）、`DesktopGalleryAndAccountHost.kt`（1367）、`DesktopSearchAndExploreEffects.kt`（1346）、`DesktopGalleryWallEffects.kt`（1034）、`DesktopComicAndFilesEffects.kt`（1048）、`DesktopTabHost.kt`（1343）、`DesktopSearchTabHost.kt`（774） |

**批 4d 达标情况**：`VeneraDesktopLargeScreen.kt` 已降至 **576 行**（主 Composable 函数体 ~448 行，全员 19 颗成员文件均 ≤ 1500 行），满足 4.6 目标线。`DesktopReaderScreen.kt`（1892 行）与 `DesktopSettingsHub.kt`（2461 行）仍为待续拆的未达标项。

**测试面同步**：`DesktopSourceTree` 维护 `desktopLargeScreenSources` 19 颗成员表及 `desktopMainGroupedOccurrences` 合并计数尺；`DesktopTabOwnershipTest`、`DesktopShellStateCaptureTest`、入口/单源/路由/收藏夹等 30 余颗源码扫描测试已同步按成员表与新成员文件读取。变更批次：`批4a`（阅读器屏）、`d4da3270`（入口支持件）、`20e0080b`（大屏外围件）、`eae9cf4b`–`批4d`（大屏主函数第 1–3 刀及状态/接线收口）。


