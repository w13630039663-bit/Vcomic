# 站点墙源仓库切换（接入 laincat/vensrc）— 设计分析

规格名:`source-repo-switch-2026-11-27` · 日期:2026-11-27
标注口径:**【确定】= 规范文档 / 代码直接可读**;**【估算】= 观感推算**,禁止把估算伪装成确定。

## 1. 页面结构

- 根容器:沿用窗口底色 `VeneraTokens.color.surface`,外层 `BoxWithConstraints` 承载主内容列与右侧抽屉叠加层【确定 · `DesktopSourceManageScreen.kt:180-182`】
  - 岛屿 A（`WallHeader` 页头工具岛）:标题「站点」+ 副行计数「N 个已开启 · M 个未启用」;右侧工具组为「搜站点」+ 三档分段(`全部 / 已开启 / 未启用`) + 主按钮「添加站点」及其下拉菜单【确定 · `DesktopSourceManageScreen.kt:427-490`】
  - 岛屿 B（`SourceRepoBar` 新增源仓库控制条）:紧接 `WallHeader` 下方,水平对齐卡片网格(`horizontal = 28.dp`),承载「源仓库」切换器(`官方源` ↔ `社区源 · laincat/vensrc`)、社区二级轨道切换(`主订阅` ↔ `扩展订阅`)、来源胶囊(`github.com/laincat/vensrc`)、仓库摘要计数与两枚动作按钮(`浏览源库` / `检查同步`)【规格值】
  - 岛屿 C（消息提示与站点墙主网格）:状态回话(`updateNote` / `note`),前 3 站海报大卡(`SiteHeroCard`),其余开启站普通卡(`SiteCard`) + 补位虚线卡(`AddSiteCard`),底部「未启用 N 个」4 列索引带(`IdleIndexItem`)【确定 · `DesktopSourceManageScreen.kt:198-370`】
  - 岛屿 D（`SiteDrawer` 右侧管理抽屉）:覆盖全高右贴边面板,包含头部标题/来源与版本徽章/关闭钮、启停卡片、账号卡片、站点设置卡片、脚本与来源维护卡片【确定 · `DesktopSourceManageScreen.kt:878-1134`】
  - 岛屿 E（`DesktopSourceStore` 在线站点弹窗）:居中使用 `Dialog(usePlatformDefaultWidth = false)`,包含弹窗头(标题 + 仓库摘要 + 刷新/关闭)、仓库切换分段与本地搜筛条、可滚动在线站点玻璃列表行【确定 · `DesktopSourceStore.kt:88-181`;升级对齐 `glass-card-spec.md`】

## 2. 区域尺寸与比例

| 区域 | 尺寸 / 占比 | 标注 |
|---|---|---|
| 左侧导航空隙 | 沿用壳层 66dp 玻璃轨 | 确定 · `site-sources-rework/proposal.md §3.1` |
| 页面水平内边距 | 左右各 `28.dp`,底部 `32.dp` | 确定 · `DesktopSourceManageScreen.kt:202-203, 442` |
| `WallHeader` 上下内边距 | 上 `22.dp`,下 `14.dp`（略收紧以容纳仓库条） | 确定 · 原 `20.dp`,本规格调整下边距至 `14.dp` |
| `SourceRepoBar` 仓库条 | 全宽(扣除左右 `28.dp`),高度随内容自适应(单行最小 `46.dp`),内边距 `14.dp × 10.dp`,底距 `14.dp` | 规格值 |
| 已开启海报大卡 (`SiteHeroCard`) | 3 列均分(`weight(1f)`),间距 `14.dp`,固定高 `206.dp` | 确定 · `DesktopSourceManageScreen.kt:234-263, 552` |
| 已开启普通卡 (`SiteCard`) / 补位卡 | 3 列均分(`weight(1f)`),间距 `14.dp`,固定高 `96.dp` | 确定 · `DesktopSourceManageScreen.kt:274-315, 632, 709` |
| 未启用索引带 (`IdleIndexItem`) | 4 列均分(`weight(1f)`),间距 `10.dp`,固定高 `44.dp` | 确定 · `DesktopSourceManageScreen.kt:337-368, 746` |
| 右侧抽屉 (`SiteDrawer`) | 宽 `424.dp`（视口 `< 1100.dp` 时 `390.dp`） | 确定 · `DesktopSourceManageScreen.kt:181` |
| 在线站点弹窗 (`DesktopSourceStore`) | 最大宽 `820.dp`,最大高 `660.dp`,四周留白 `24.dp` | 规格值（原 `widthIn(max = 760.dp)` 全屏填充改为居中浮窗卡片） |

## 3. 视觉层次

1. **基底与透底玻璃档**:
   - 窗口主背景沿用窗口底板;所有一级/二级卡片遵循 [glass-card-spec.md](file:///g:/venera-desktop/desktop/docs/designs/desktop-ui-standard-2026-11-05/glass-card-spec.md) 的透底与实底双档规则,严格按 `.background` → `.border` → `.clip` 顺序叠加,暗色模式不画 Skia `shadow`。
   - `SourceRepoBar` 采用二级内嵌玻璃材质(`Nested`:透底时 `surfaceContainerHigh @ 0.35`,实底时 `surfaceContainerHigh`,描边 `1.dp outlineVariant @ 0.18`),视觉上弱于前方 `SiteHeroCard` 海报卡(`Primary`),不喧宾夺主。
2. **浮层与遮罩**:
   - 右侧抽屉遮罩与在线站点弹窗遮罩均使用纯黑半透(`Color.Black.copy(alpha = 0.52f)`);弹窗与抽屉面采用高不透明度抬升底(`tokens.color.surface` / `surfaceContainerHigh @ 0.98`),配合 `1.dp outlineVariant @ 0.30` 描边确立前景层次。
3. **色彩克制**:
   - 全站唯一装饰色为 accent 蓝(浅 `#0A63F6` / 深 `#0A84FF`),仅用于激活主按钮(`添加站点`、`检查同步`有更新时、`安装/升级` 按钮)与卡片悬停描边;
   - 来源胶囊(`laincat/vensrc` / `官方源` / `内置站点` / `本地脚本`)一律走中性灰底 `surfaceContainerHigh @ 0.60` + `textSecondary/textTertiary`,不新增品牌彩色;「已登录」沿用既有 `GalleryTokens.Color.StatusSuccess`。

## 4. 逐元素设计参数

| 元素 | 颜色 | 字阶 | 圆角 | 其他 | 标注 |
|---|---|---|---|---|---|
| 仓库条外框 | `DesktopGlassCardLevel.Nested` | — | `12.dp` | `1.dp` 描边,内距 `14×10.dp` | 规格值 · 对齐 `glass-card-spec` |
| 仓库分段切换 (`官方源` / `社区源 · laincat/vensrc`) | 轨 `segmentTrack`,滑块 `card` | `12.sp / Medium~SemiBold` | 外 `7.dp` / 内 `5.5.dp` | 复用 `DesktopSegmented` | 确定 · `controls-standard-spec §2` |
| 轨道切换 (`主订阅 · 全绿` / `扩展 · 核心可读`) | 同上 | `12.sp / Medium~SemiBold` | 外 `7.dp` / 内 `5.5.dp` | 仅选中社区源时出现 | 规格值 |
| 来源标注胶囊 (`laincat/vensrc`) | 底 `surfaceContainerHigh @ 0.60`,字 `textSecondary` | `11.sp`,行高 `14.sp` | `999.dp` | `1.dp` 描边 `outlineVariant @ 0.25` | 规格值 |
| 动作按钮 (`浏览源库` / `检查同步`) | `Secondary` / `Primary` | `VeneraType.aux` | `6.dp` | 复用 `DesktopButton(Compact, 26.dp)` | 确定 · `controls-standard-spec §3.1` |
| 站点卡片来源徽章 | 底 `surfaceContainerHigh @ 0.60`,字 `textTertiary` | `10.sp`,行高 `12.sp` | `999.dp` | 替换原硬编码 `"内置站点"` | 确定 · `DesktopSourceManageScreen.kt:856-874` |
| 在线站点弹窗外框 | `tokens.color.surface` | — | `16.dp` | `1.dp` 描边 `outlineVariant @ 0.28` | 规格值 |
| 在线站点列表行 | `DesktopGlassCardLevel.Nested` | 站名 `body`,副行 `micro` | `10.dp` | 内距 `12×10.dp`,行距 `8.dp` | 规格值 |

## 5. 响应式视口规则

| 视口档 | 断点 | 重排规则 |
|---|---|---|
| 宽档 | ≥ `1100.dp` | `SourceRepoBar` 左右区单行并排;抽屉宽 `424.dp`;在线源弹窗宽 `820.dp` |
| 窄档 | < `1100.dp` | `SourceRepoBar` 使用 `FlowRow`（或允许右区自动折到第二行,左对齐不截断 `laincat/vensrc` 标签）;抽屉降为 `390.dp`;弹窗宽自适应 `maxWidth - 48.dp` |
