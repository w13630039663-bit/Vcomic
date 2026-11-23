# 漫画搜索：源选择重做与自定义多源聚合 — 逆向分析（Design Analysis）

- 对应方案稿：`desktop/docs/designs/comic-search-aggregate-2026-11-20/ui-mockup.html`
- 全局令牌正典：`desktop/docs/designs/desktop-ui-standard-2026-11-05/README.md`（只引用不复制）
- 标注口径：`【确定 · 出处】`、`【估算 · 截图】`、`【规格值】`

## 1. 页面结构与区域解构

本页承接 `DesktopDiscoverScreen`（`DesktopDiscoverScreen.kt:58`）下的「搜索」视图，页面采用纵向四层骨架 + 内容区双栏（主结果流 + 右侧速览栏）：

| 区域 | 定位与职责 | 高度 / 宽度 | 口径 |
|---|---|---|---|
| 顶栏工具行 | 左「搜索 / 探索」分段 + 中搜索输入框 + 右 **源选择触发器**（仅搜索页） | 固定高 `60dp`，全宽，`spacedBy(10dp)` | 【确定 · `DesktopDiscoverScreen.kt:157`】+【规格值】 |
| 源选择弹出面板 | 锚定于源选择触发器右下缘，`Popup` 浮层，含「单源 / 多源聚合」双档切换、过滤输入框、源列表、多选底部动作条 | 宽 `336dp`，列表最大高 `310dp`，距触发器 `6dp` | 【规格值】 |
| 页头标题行 | 左主标题「漫画搜索」（≤6字）+ 灰副标题（单源：`源名 · N 本`；聚合：`全网聚合/已选 X 源 · N 本`，≤15字） | 顶 `4dp`、底 `10dp`，单行基线对齐 | 【确定 · `DesktopDiscoverScreen.kt:190-215`】 |
| 聚合态：源状态锚点条 | 替代单源「筛选条件」面板：左进度读数 + 中可横滑源状态胶囊（点击滚至泳道）+ 右「仅看有结果」开关 | 最小高 `48dp`，全宽，内距 `14×10dp` | 【规格值】 |
| 单源态：筛选面板 | 保留 `SearchFilterPanel`，**彻底移除「来源」行**；仅在 `filterGroups.isNotEmpty()` 时展示 | 收起态 `52dp`（内距 `16×11dp`） | 【确定 · `DesktopSearchScreen.kt:1059-1122`】+【规格值】 |
| 主工作双栏 | 左栏 `weight(1f)`（聚合泳道流或单源网格/行卡）+ 右栏 `DesktopComicPeekPane`（展开 `380dp`，内含 `18dp` 左距） | 占满剩余垂直空间 `weight(1f)` | 【确定 · `DesktopSearchScreen.kt:384` / `DesktopComicPeekPane.kt:98`】 |

## 2. 聚合泳道分层与信息密度

聚合模式下，左侧 `LazyColumn`（`spacedBy(12dp)`）按四态分层排布各源泳道，确保高价值命中优先呈现、异常与空态不侵占主视野：

1. **有结果（`Ready`，置顶）**：完整泳道卡（内距 `14×16dp`）—— 组头（`20dp` 站点图标 + 源名 + 条数 + 左右翻页小钮 `26dp` + 「查看全部 →」按钮）+ 横滑 `LazyRow` 卡片轨（每张卡宽 `138dp`，封面 `0.72f` 纵横比，标题 2 行 + 作者 1 行，间距 `12dp`）。
2. **检索中（`Loading`，次之）**：组头（站点图标 + 源名 + 「正在检索…」）+ 6 张横排局部扫光骨架卡（`VeneraShimmer(emphasized = true)`）。
3. **失败（`Failed`，沉底）**：紧凑单行卡（内距 `11×16dp`）—— 站点图标 + 源名 + 简明失败原因 + 右侧独立「单源重试」按钮（只重跑该源）。
4. **无结果（`Empty`，最底）**：紧凑单行卡（内距 `11×16dp`）—— 站点图标 + 源名 + 「该源未找到相关漫画」。

## 3. 视觉层次与表面材质

- **底色递进**：页面底 `VeneraTokens.color.background` → 锚点条/泳道/筛选卡/速览栏 `palette.elevated` → 泳道内卡片/控件槽 `palette.card` / `palette.pill` → 浮层弹出面板 `palette.elevated` + 浮层投影（浅色 `vShadow`、深色 `1dp` 描边，暗色不恢复 Skia 投影）。
- **无多余装饰色**：主强调色唯一为 `VeneraTokens.color.primary`（深 `#0A84FF` / 浅 `#0A63F6`）；泳道状态圆点采用中性灰阶与既有 `ToneSafe` / `error` 语义槽，徽章背景克制低饱和，文字靠字重与灰阶建立层级。
- **同心圆几何**：弹出面板 `r12` 与内距 `8dp` 内含 `r8` 行/输入框（`r12 ≈ r8 + 4dp` 视觉嵌套不打架）；泳道卡 `r12` 与内距 `14dp` 内含 `r10` 卡片及顶部贴边封面。

## 4. 响应式视口规则

| 窗口可用宽度 | 源触发器表现 | 聚合泳道卡片轨 | 右侧速览栏 |
|---|---|---|---|
| `≥ 1150dp`（宽窗） | 完整图标组 + 源名 + 计数徽章，最大宽 `220dp` | 同屏可见 5~7 张横滑卡（速览展开时 4~5 张） | 并排常驻展示 `380dp` |
| `900dp ~ 1149dp`（标准窗） | 名称超长 `TextOverflow.Ellipsis`，最大宽 `190dp` | 同屏可见 3~5 张横滑卡 | 并排展示，左栏 `weight(1f)` 自动收缩 |
| `< 900dp`（窄窗） | 保持触发器可点，搜索框 `weight(1f)` 优先缩让 | 横滑卡轨不变形，借助左右翻页钮与滚轮浏览 | 沿用既有 `AnimatedVisibility` 展开行为 |

## 5. 保留既有业务逻辑与入口声明

- 保留单源按源动态 `search.optionList` 读写、默认值重置与多选 JSON 编码（`desktopMultiSelectKeys` / `desktopMultiSelectEncode`）。
- 保留搜索历史落盘、搜索框聚焦历史下拉、`historyChips` 与清空操作。
- 保留单源单列 / 双列 / 网格视图切换（`DesktopSearchViewMode`）、自动触底续页（`DesktopGridAutoLoadEffect`）与回顶钮。
- 保留封面打码守卫单源判据（`desktopUx.coverMaskStateOf`），聚合态按每张卡片归属 `sourceKey` 精确判定。
- 保留卡片右键菜单（`DesktopComicCardMenuArea`）与探索页右键搜标签自动切回搜索页的既有链路。
