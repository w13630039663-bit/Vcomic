# 漫画搜索：源选择重做与自定义多源聚合 — 组件规格（Component Spec）

## 1. `DesktopSearchSourcePicker`（新 · `search/DesktopSearchSourcePicker.kt`）

### 组成解构
1. **触发按钮（`SourcePickerTrigger`）**：
   - 单源态：`[20dp 站点图标] + [源显示名，最多 1 行省略] + [14dp 下拉箭头]`。
   - 聚合态：`[前 3 源 18dp 叠放图标，间距 -6dp] + [「全网聚合」或「已选 N 源」] + [可选数量胶囊] + [14dp 下拉箭头]`。
   - 状态：默认 `palette.pill` + `OutlineHairline`；悬停 `palette.hover` + `OutlineHover`；展开 `primary 14%` 底 + `primary 42%` 边 + 箭头旋转 180°。
2. **弹出面板（`Popup`，右对齐触发器下 `6dp`，宽 `356dp`）**：
   - **顶部两档分段**：`单源` / `多源聚合`（复用 `DesktopSegmented` 风格，等宽两栏）。
   - **面板内搜索框**：`32dp` 高，实时按 `label` / `key` 大小写不敏感过滤已启用源列表。
   - **分级过滤条（方案 2）**：搜索框下提供 `全部 (N)` / `正常源 (A)` / `R18源 (B)` 三档胶囊过滤（同时存在两类源时展示），快速缩短超长源列表。
   - **分组呈现与一键快捷勾选（方案 1）**：
     - 「全部」过滤态下若两类源均有命中，列表自动分「正常源」与「R18 源」两段带组头展示，R18 行右侧附中性 `R18` 小标（分级单源复用 `DesktopSourceContentPresets`）。
     - 单源档：整行点击立即触发 `onSelectSingle(key)` 并关闭面板；支持上下方向键与回车选中、Esc 关闭。
     - 多源聚合档：批量行提供 `只选正常` / `只选R18` / `全选` / `清空` 一键快捷操作 + 复选列表 + 底部确认条（仅勾 1 源确认自动切入单源；刚好全选正常源/R18 源时触发器与副标题分别显示「正常源聚合 / R18 聚合」）。

### 站点图标复用
- 复用既有站点图标加载机制（桌面内置 `source_icons/<key>.png` 或回退首字圆角徽标），封装为轻量的 `DesktopSearchSourceIcon(sourceKey, label, size)`，避免缺失图标时白块。

---

## 2. `DesktopAggregateStatusBar`（新 · `search/DesktopAggregateSearchView.kt`）

### 组成解构
- 外框：`elevated` 底 + `1dp OutlineHairline` + `r12` + `14×10dp` 内距。
- **左侧进度区**：状态圆点（进行中 `primary`，全部完成 `ToneSafe`）+ 文案「检索中 X/Y 源 · 共 N 本」/「已完成 Y/Y 源 · 共 N 本」，右侧 `1dp` 竖分隔线。
- **中间源锚点轨**：可横滑 `Row`（`spacedBy(6.dp)`），每个已选源一枚 `28dp` 胶囊：
  - `6dp` 状态圆点（`Ready`=`ToneSafe` / `Loading`=`primary` / `Failed`=`error` / `Empty`=`textTertiary`）+ 源名 + 计数（条数 / `…` / `失败` / `0`）；
  - 点击回调 `onJumpToSource(sourceKey)`，若当前开启了「仅看有结果」且目标源为空/失败，自动关闭筛选并滚至目标泳道。
- **右侧过滤开关**：`仅看有结果` 标签 + 紧凑开关（点击切换 `onlyHits`，仅保留 `Ready` 与 `Loading` 泳道）。

---

## 3. `DesktopAggregateLane`（新 · `search/DesktopAggregateSearchView.kt`）

### 四态渲染规格
| 状态 | 布局形态 | 交互动作 |
|---|---|---|
| `Ready` | 完整泳道卡：`[20dp 图标 + 源名 + "N 本"]` + 右侧 `[‹] [›] [查看全部 →]` + 下方 `LazyRow` 卡片轨（每张卡 `138dp` 宽，`0.72f` 封面 + 2 行标题 + 1 行作者） | 左右箭头平滑滚轨；点击卡触发 `onSelectCard(card, lane.sourceKey, lane.sourceLabel)` 打开右侧速览；右键菜单按 `lane.sourceKey` 搜标签/查看详情；点击「查看全部 →」触发 `onDrillDownSource(lane.sourceKey)` 切进单源深搜 |
| `Loading` | 组头（图标 + 源名 + `正在检索…`）+ 6 张 `138dp` 宽局部 `VeneraShimmer` 骨架卡 | 只读占位，不阻塞其他已就绪泳道交互 |
| `Failed` | 紧凑单行卡：图标 + 源名 + `error` 简明原因 + 右侧「单源重试」按钮 | 点击触发 `onRetrySource(lane.sourceKey)`，只重跑该源并局部刷新 |
| `Empty` | 紧凑单行卡：图标 + 源名 + `该源未找到相关漫画` | 无多余占位高度 |

---

## 4. `SearchFilterPanel` 瘦身（改 · `search/DesktopSearchScreen.kt`）

- 删除面板内的「来源」`SearchFilterGroupRow` 整段代码（源选择完全收归顶栏 `DesktopSearchSourcePicker`）。
- 显隐规则：
  - 聚合模式：整块不渲染（由 `DesktopAggregateStatusBar` 占据同位）。
  - 单源模式：当 `filterGroups.isEmpty()` 时不渲染空面板；有筛选项时保持既有默认收起、展开显示 `Sort` / 分类 / 多选与「重置 / 应用筛选」交互。

---

## 5. `DesktopComicPeekPane` 跨源适配（改 · `search/DesktopComicPeekPane.kt`）

- `DesktopPeekUiState` 新增 `sourceKey: String? = null`、`sourceLabel: String? = null`。
- `desktopSearchPeekPaneOf` 解析实际生效源：`val activeSourceKey = state.sourceKey ?: sourceKey`，详情加载、收藏/阅读入口与「查看详情」路由统一使用 `activeSourceKey`，保证在聚合模式下连续点击不同源的卡片时，速览栏始终准确展示并拉取该卡片归属源的数据。
