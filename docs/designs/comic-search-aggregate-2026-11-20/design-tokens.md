# 漫画搜索：源选择重做与自定义多源聚合 — 设计令牌（Design Tokens）

> 全局令牌正典 = `desktop/docs/designs/desktop-ui-standard-2026-11-05/README.md`，以下全部映射至既有 `VeneraTokens` / `LocalChromePalette` / `GalleryTokens` / `VeneraType`，**不新建任何平行 `*Tokens` 对象，不写死裸色 Hex**。

## 1. 颜色映射表

| 语义角色 | Compose 映射 | 适用位置 |
|---|---|---|
| 页面底 | `VeneraTokens.color.background` | 整屏背景 |
| 卡片 / 锚点条 / 泳道 / 浮层底 | `LocalChromePalette.current.elevated` | 源状态锚点条、泳道容器、源选择弹出面板、筛选卡 |
| 次级卡片 / 泳道漫画卡底 | `LocalChromePalette.current.card` | 泳道内竖卡、单源行卡 |
| 控件槽 / 胶囊常态底 | `LocalChromePalette.current.pill` | 源触发按钮、锚点胶囊、面板内搜索框、左右翻页钮 |
| 细描边（常态） | `GalleryTokens.Color.OutlineHairline` | 卡片、按钮、弹出面板外框、内部分隔线 |
| 悬停描边 / 悬停底 | `GalleryTokens.Color.OutlineHover` / `LocalChromePalette.current.hover` | 按钮/菜单行/锚点胶囊悬停 |
| 唯一主强调色 | `VeneraTokens.color.primary`（深 `#0A84FF` / 浅 `#0A63F6`） | 选中态勾标、多选复选框、确认主钮、「查看全部」、选中卡片边框 |
| 主强调浅底 | `VeneraTokens.color.primary.copy(alpha = 0.14f)` | 弹出框当前选中行、触发器展开态、计数徽标、「查看全部」轻底 |
| 状态：有结果 / 加载中 / 失败 / 空 | `GalleryTokens.Color.ToneSafe` / `VeneraTokens.color.primary` / `VeneraTokens.color.error` / `VeneraTokens.color.textTertiary` | 锚点胶囊 6dp 状态圆点、单源失败文字与重试按钮 |
| 文字三级 | `VeneraTokens.color.textPrimary` / `textSecondary` / `textTertiary` | 泳道名、卡片标题、作者、源键、条数读数 |

## 2. 尺寸与间距令牌

| 项 | 确定 / 规格值 | 口径 |
|---|---|---|
| 工具栏高度 | `60.dp` | 【确定 · `DesktopDiscoverScreen.kt:157`】 |
| 工具栏元素间距 | `10.dp` | 【确定 · `DesktopDiscoverScreen.kt:159`】 |
| 源触发按钮高 / 最大宽 | `36.dp` / `220.dp`（内距左 `9dp` 右 `11dp`） | 【规格值】 |
| 站点图标尺寸（单源 / 多源叠放 / 泳道头） | `20.dp` / `18.dp`（叠放负距 `-6.dp`） / `20.dp` | 【规格值】 |
| 弹出面板宽 / 列表最大高 / 内距 | `336.dp` / `310.dp` / `10.dp` | 【规格值】 |
| 弹出面板行高 / 内部搜索框高 | `36.dp` / `32.dp` | 【规格值】 |
| 聚合锚点条内距 / 胶囊高 / 间距 | `14.dp × 10.dp` / `28.dp` / `6.dp` | 【规格值】 |
| 聚合泳道卡间距 / 内距（展开 / 紧凑） | `12.dp` / `16.dp × 14.dp` / `16.dp × 11.dp` | 【规格值】 |
| 泳道漫画卡宽 / 封面比例 / 卡距 | `138.dp` / `0.72f` / `12.dp` | 【规格值】 |
| 泳道翻页钮尺寸 | `26.dp × 26.dp` | 【规格值】 |
| 右侧速览栏宽 / 左距 | `380.dp` / `18.dp` | 【确定 · `DesktopComicPeekPane.kt:98-100`】 |

## 3. 圆角令牌（同心圆几何 `Outer = Inner + Padding`）

| 元件 | 圆角 | 几何校验 |
|---|---|---|
| 源选择弹出面板外框 | `12.dp`（`GalleryTokens.Shape.Large`） | 内距 `10dp` 内含 `8dp` 列表行/输入框、`9dp` 分段外槽（含 `3dp` 内距 + `6dp` 段块） |
| 锚点条 / 泳道卡 / 筛选面板 | `12.dp`（`GalleryTokens.Shape.Large`） | 泳道内距 `14dp` 内含 `10dp` 漫画卡 |
| 顶栏源触发按钮 | `10.dp` | 与 `DesktopSearchField` 视觉圆角同阶 |
| 泳道漫画卡 | `10.dp` | 封面顶角随外卡裁切贴边（r0 内嵌套） |
| 锚点胶囊 / 列表行 / 小按钮 | `8.dp` / `7.dp` | 紧凑操作件 |
| 站点图标 / 复选框 | `5.dp` / `4.dp` | 微小方正标识 |

## 4. 字阶与动效令牌

- **字阶单源**：全部消费 `VeneraType`（`statNumber` 页头标题、`cardTitle` 泳道名/筛选标题、`bodySecondary` 行卡标题、`control` 源按钮/列表项、`aux` 锚点/元数据、`micro` 源键/条数/标签），计数位保持等宽可读。
- **阴影规则**：常规泳道卡在深色下零阴影（靠 `1dp OutlineHairline` + `elevated` 表面分层），浅色走 `vShadow(1.dp, shape)`；弹出面板浮层使用 `vShadow(12.dp, shape)`（暗色不裸用低 elevation `Modifier.shadow`）。
- **动效规则**：
  - 桌面按压无缩放，悬停即时变色（`collectIsHoveredAsState`）；
  - 弹出面板开关直接走 `Popup` 瞬显/轻淡入，不引入弹性弹簧；
  - 泳道锚点跳转使用 `LazyListState.animateScrollToItem`；
  - 泳道横滑箭头使用 `LazyListState.animateScrollBy`（步长 `420px` 对应密度换算）。
