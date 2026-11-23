# 画廊搜索 UI 重做 — 组件规格

规格名：`gallery-search-redesign-2026-11-21` · 日期：2026-11-21 · 配套：`proposal.md` / `design-analysis.md` / `design-tokens.md`  
实施文件（待阶段 C 落地）：`DesktopGalleryTopSearch.kt`、`DesktopGallerySearchScreen.kt`、`VeneraDesktopLargeScreen.kt`、`DesktopGallerySearchUiTest.kt`。本阶段 B **未改代码**。

---

## 1. `DesktopGalleryTopSearch` — 首页顶栏悬浮搜索输入槽

**解构**：`Box(锚点)` → `Row(输入槽: 放大镜 + BasicTextField/占位 + 清空按钮 + Booru范围胶囊)` + `Popup(智能下拉浮层)`。

**视觉参数**：输入槽高 `34.dp`，`widthIn(220.dp..480.dp)`，圆角 `8.dp`，内距左 `11.dp` 右 `4.dp`。默认 `SurfaceLevel2` + `1.dp OutlineHairline`，聚焦 `SurfaceLevel1` + `2.dp AccentPrimary`。内嵌范围胶囊高 `26.dp`，圆角 `4.dp`（同心圆），`SurfaceLevel3` 底。

**状态**：
| 状态 | 表现 |
|---|---|
| 默认 | `SurfaceLevel2` 底，`1.dp OutlineHairline`，占位文本 `TextTertiary` |
| 悬停 | 边框提亮至 `OutlineHover` |
| 按压 | 无缩放（桌面语义），即时进入聚焦 |
| 激活 / 聚焦 | 切换 `SurfaceLevel1` 底，外框切为 `2.dp AccentPrimary` 并弹出浮层 |
| 禁用 | 不适用（顶栏常驻可用） |

**动画**：浮层显隐随聚焦即时/150ms 淡入过渡。

**Compose 映射**：保留 `DesktopGalleryTopSearch` 全部既有入参签名；状态 `text / suppressDropdown / pickedSite / history / suggestions / activeIdx` 原样保留。

---

## 2. 共享智能下拉浮层（顶栏浮层 & `GallerySearchDropdownFlyout`）

**解构**：`Popup` → `Column(浮层外壳: 12.dp 圆角, 8.dp 内距)` → [Booru 可选] 检索源流式换行区（`FlowRow`） + 36dp 全年龄开关行 + `1.dp Divider` → 最近搜索节（标题+清除 + 列表行） → `1.dp Divider` → 实时标签联想节（标题 + 列表行）。

**视觉参数**：浮层 `SurfaceLevel1`，浅色带 `vShadow(16.dp)` + `OutlineHairline`，深色带 `1.dp OutlineHover`。站点流芯片高 `28.dp`、圆角 `6.dp`、`6.dp` 间距；历史与联想列表行高 `34.dp`、圆角 `6.dp`。

**状态**：
| 状态 | 表现 |
|---|---|
| 默认 | 列表行透明底；站点芯片 `SurfaceLevel2` + `OutlineHairline` |
| 悬停 | 列表行切换 `SurfaceLevel2`；行内单删 `×` 按钮显形；站点芯片切换 `SurfaceLevel3` |
| 按压 | 无缩放，触发对应回填/删除/切换行为 |
| 激活 / 选中 | 键盘高亮行或选中站点：`AccentSoft` 底 + `AccentPrimary` 字色与描边 |
| 禁用 | 无历史且无联想时显示居中提示「输入关键词开始搜索」 |

**动画**：`150.ms` 淡入。

**Compose 映射**：
- 顶栏站点行把 `Row` 改为 `FlowRow`（`horizontalArrangement = spacedBy(6.dp)`, `verticalArrangement = spacedBy(6.dp)`），彻底消灭 `Ko` 裁断。
- 搜索页 `GallerySearchDropdownFlyout` **必须严格保留** `if (suggestCandidates.isNotEmpty() || suggestLine != null)`、`suggestCandidates.forEach` 及 `onPickSuggestion(suggestion.name)`，并将内部 `VeneraChip` 替换为结构化 `DropdownRow`（左分类色点 + 译名/原名 + 右分类标签与站点标），同时保留历史行点击触发 `onPickHistory(entry)` 与 `hasTextExactly(entry.query)` 语义节点以通过既有测试。

---

## 3. `GallerySearchCommandBar` — 搜索页透明指挥栏（第一行 + 第二行）

**解构**：`Column(透明底 = Background, horizontal padding 20.dp, bottom 1.dp Divider)` → `Row(第一行: 模式分段器 + 主搜索框 + [Pixiv目标下拉] + 搜索主按钮 + 高级筛选开关)` + `FlowRow/Row(第二行: 收敛快捷筛选条)`。

**视觉参数**：
- 第一行全部主件严格等高 `36.dp`、外圆角 `10.dp`、水平间距 `10.dp`：
  - `GallerySearchModeSwitch`：外轨 `SurfaceLevel2` + `1.dp OutlineHairline`，内距 `3.dp`，滑块高 `28.dp`、圆角 `7.dp`、选中底 `SurfaceLevel1` + 站徽色点 + `AccentPrimary`。
  - `GallerySearchInputBox`：高 `36.dp`，圆角 `10.dp`，默认 `SurfaceLevel2`，聚焦 `SurfaceLevel1` + `2.dp AccentPrimary`。
  - `PixivSearchTargetMenu`：废除硬编码 `GalleryTokens.Color` 药丸，统一改用高 `36.dp`、圆角 `10.dp`、`SurfaceLevel2` + `OutlineHairline` 按钮与 `10.dp` 弹出菜单（内项 `6.dp` 圆角），禁用项（「小说」）置灰 `TextTertiary` + `alpha 0.6`。
  - 主「搜索」按钮：高 `36.dp`、圆角 `10.dp`，可点态使用 `AccentPrimary` 实底 + 纯白字；禁用态使用 `SurfaceLevel2` + `OutlineHairline` + `TextTertiary`。
  - `GallerySearchInspectorToggle`：高 `36.dp`、圆角 `10.dp`，内嵌激活筛选角标（圆形 `Pill`）。
- 第二行 `GallerySearchQuickStrip`（高 `28.dp~30.dp`，用 `FlowRow` 替代会裁断的 `horizontalScroll` + `hScrollEndFade`）：
  - Booru：「来源」芯片组（`28.dp` 高，`6.dp` 圆角）+ 竖分隔线 + 「排行」Pop-up 下拉芯片 + 「全年龄过滤」紧凑开关胶囊（绑定 `searchR18` / `onPickSearchR18`）。
  - Pixiv：「维度（作品/画师/珍藏册）」芯片组 + 竖分隔线 + 「排序」Pop-up 下拉芯片 + 「收藏」Pop-up 下拉芯片 + 已激活筛选清除微标。

**状态**：
| 状态 | 表现 |
|---|---|
| 默认 | 芯片透明底 + `1.dp OutlineHairline` + `TextSecondary` |
| 悬停 | 背景切换 `SurfaceLevel2`，描边提亮 `OutlineHover`，字色 `TextPrimary` |
| 按压 | 无缩放，即时切换状态或弹出 Pop-up 菜单 |
| 激活 / 选中 | `AccentSoft` 底 + `1.dp OutlineSelected` + `AccentPrimary` 字色 |
| 禁用 | `SurfaceLevel2` 底 + `TextTertiary`，点击无响应 |

**动画**：Pop-up 菜单显隐走 `CollectionMotion.menuExpand`（150ms）；分段器切换即时换色。

**Compose 映射**：所有来源节点继续以独立点击文本呈现（满足 `DesktopGallerySearchUiTest` 对 `hasTextExactly(sourceLabels[i])` 的断言）；快捷条取消 `hScrollEndFade`，改用 `FlowRow` 自然折行与 Pop-up 聚合，确保宽屏单行、窄屏零溢出。

---

## 4. `GallerySearchInspectorDrawer` — 左侧高级筛选抽屉

**解构**：`AnimatedVisibility(expandHorizontally + fadeIn)` → `Column(宽 284.dp, 右侧 1.dp Divider, padding start=20 end=14)` → 多张分组卡片（`SurfaceLevel1`, `12.dp` 圆角, `12.dp` 内距）。

**视觉参数**：卡片带 `1.dp OutlineHairline`（浅色加 `vShadow(4.dp)`）；节标题 `12.5.sp SemiBold` 左配 `3×12.dp` `AccentPrimary` 竖条，删除原多余大写英文副标；内部筛选芯片统一高 `28.dp`、圆角 `6.dp`；日期输入框改自绘 `BasicTextField` 容器（高 `32.dp`，圆角 `6.dp`，`SurfaceLevel2` 底 + `1.dp OutlineHairline`）；周期步进器住进 `SurfaceLevel2` 紧凑槽（高 `32.dp`，圆角 `8.dp`）。

**状态**：
| 状态 | 表现 |
|---|---|
| 默认 | 抽屉收起（0dp）；卡片芯片默认透明底 + 细框 |
| 悬停 | 芯片切 `SurfaceLevel2`；步进箭头切 `SurfaceLevel3` |
| 按压 | 无缩放，点击即更新筛选 |
| 激活 / 选中 | 芯片切 `AccentSoft` + `AccentPrimary`；日期框聚焦切 `SurfaceLevel1` + `AccentPrimary` |
| 禁用 | 步进按钮边界不可点时置 `TextTertiary` |

**动画**：`spring(StiffnessMediumLow, 0.82f)` 水平展开/收起 + `fadeIn(220ms)/fadeOut(180ms)`。

**Compose 映射**：保留 `DesktopPixivFilterInspector` 与 `DesktopBooruFilterInspector` 现有逻辑，外加 Booru 「全年龄检索」同步开关卡片。

---

## 5. 结果区配套（画师卡片 / 空态引导）

**解构**：
- **画师行**：`SurfaceLevel1` 卡片（`12.dp` 圆角，`1.dp OutlineHairline`，内距 `12×14.dp`）→ `44.dp` 圆形头像（浅色加 `1.dp ImageHairline`）+ 姓名/账号列 + 右侧 `28.dp` 高「查看作品 →」轻按钮。
- **空白/加载/失败引导**：`Column(居中)` → `52.dp` 圆角图标徽（`14.dp` 圆角，`SurfaceLevel1`）+ 标题 + 克制副文案 + 错误重试钮。

**状态**：
| 状态 | 表现 |
|---|---|
| 默认 | 画师卡片 `SurfaceLevel1` + `OutlineHairline` |
| 悬停 | 切换 `SurfaceLevel2` + `OutlineHover`，右侧动作钮高亮为 `AccentSoft` + `AccentPrimary` |
| 按压 | 无缩放，点击进入画师主页 |
| 激活 / 选中 | 不适用 |
| 禁用 | 不适用 |

**动画**：不同结果流（图墙 / 画师 / 珍藏册）切换沿用 `AnimatedContent` 220ms 淡入 / 160ms 淡出。

**Compose 映射**：注意 `DesktopGallerySearchUiTest` 第 ⑥ 条严格断言搜索为空时显示 `"无结果"` 与 `"换个关键词再试"`、失败时显示 `"加载失败"` 与 `"请稍后重试"`；因此空墙仍复用或包装 `VeneraEmptyView` 文案口径，不可篡改断言文本，在外观、间距和图标框质感上按本规格精修。
