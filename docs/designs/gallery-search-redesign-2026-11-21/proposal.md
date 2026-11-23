# 画廊搜索 UI 重做 · 方案稿

规格名：`gallery-search-redesign-2026-11-21` · 日期：2026-11-21  
状态：**待批**。获批后进阶段 B 三件套，未批前不动码。

依据：
- 用户提供三张现网截图（首页顶栏悬浮搜索浮层 / 搜索页 Booru 态 / 搜索页 Pixiv 态）
- 源码现状：`DesktopGalleryTopSearch.kt`、`DesktopGallerySearchScreen.kt`、`GalleryTokens.kt`
- 全站规范：`desktop/docs/designs/desktop-ui-standard-2026-11-05/README.md`
- 视觉方案稿：`desktop/docs/designs/gallery-search-redesign-2026-11-21/index.html`

配套：`design-analysis.md` / `design-tokens.md` / `component-spec.md`（获批后补齐）

---

## 1. 结论

**全站视觉系统收敛 + 结构去重降噪方案**：以 `VDDS`（透明 Chrome + 1dp 底分隔线、三级递进实色表面、浅微影/深描边、同心圆几何）统一「首页顶栏悬浮搜索」与「独立画廊搜索结果页」，消除当前三套色板/圆角混战、顶栏浮层站点溢出截断、快捷条长串横向溢出与右侧渐隐断层，以及高级筛选/画师行/空白态粗糙割裂的问题。

| 层 | 目标 |
|---|---|
| 视觉 | 统一走 `GalleryTokens` / `VeneraTokens` 深浅双套体系，撤出独立搜索页顶部灰底板与写死色值，严格落实 `Outer = Inner + Padding` 同心圆圆角与图标线宽配平 |
| 交互 | 浮层站点改流式自适应换行消除裁断；快捷筛选条收敛高频主项、长选项收进 Pop-up 下拉芯片消灭横向滚动截断；Booru 全年龄开关同步露出；补齐四态空墙引导 |
| 数据 | 保留全部 `BusinessPorts`、`DesktopSearchHistory`、`DesktopPixivAutocomplete`、搜索词状态、回调签名与测试断言口径，零业务删减 |

保留全部业务逻辑、数据来源、状态管理与功能入口。

---

## 2. 现状问题

### 2.1 与全站规范的差距

| 项 | 现状 | 对标（VDDS / better-ui） | 差距 |
|---|---|---|---|
| 搜索页顶区底板 | 整块矩形半透明灰底 `surfaceContainerHigh.copy(alpha = 0.5f)`【确定 · `DesktopGallerySearchScreen.kt:239`】 | 顶栏透明 = `Background`，仅底边 1dp `Divider` 分隔【确定 · `README.md:186`】 | 顶部出现生硬色块断层，与图库主页视觉割裂 |
| 控件色板混用 | 一行内混用 `VeneraTokens`、`DesktopPushButton`、`VeneraChip` 药丸与写死深色 `GalleryTokens.Color` 的 `PixivSearchTargetMenu`【确定 · `DesktopGallerySearchScreen.kt:697-863,1267`】 | 同一语义单一真源，浅色/深色各自成套【确定 · `README.md:265`】 | 目标下拉黑底胶囊与周围浅灰控件形成明显拼贴感 |
| 圆角与高度 | 顶栏搜索框 32dp/6dp、浮层 12dp+8dp 内距配 4dp 行、搜索页分段器与框 38dp/Large、目标下拉 32dp/CircleShape【确定 · `DesktopGalleryTopSearch.kt:228-235,355`、`DesktopGallerySearchScreen.kt:700,748,1284`】 | 指挥栏控件统一 36dp/10dp、浮层 12dp/内项 6dp、严格同心圆 | 同屏控件高度参差不齐，胶囊与方圆角混杂 |
| 右侧渐隐块 | `hScrollEndFade(VeneraTokens.color.background)` 画在半透明灰底上【确定 · `DesktopGallerySearchScreen.kt:907`】 | 尽量免横向滚动；若需滚则底色同源【估算 · 截图 2/3】 | 截图右侧出现明显亮于灰底的不透明色块断层 |

### 2.2 体验与功能布局缺口

1. **首页浮层站点芯片严重截断**：`booruSites` 使用单行 `Row` 硬排未换行，第 6 个站源直接裁断成半个 `Ko`【估算 · 截图 1；确定 · `DesktopGalleryTopSearch.kt:361-371`】。
2. **两处下拉浮层体验割裂**：首页浮层为列表行，搜索页 `GallerySearchDropdownFlyout` 把历史和每条“译名+原名+分类+站点”全塞进 `VeneraChip` 药丸堆叠，换行后杂乱难扫读【确定 · `DesktopGallerySearchScreen.kt:613-683`】。
3. **快捷筛选条冗长且与抽屉重复**：Booru 的「排行」与 Pixiv 的「收藏 100+~10000+」全部平铺在第二行导致超宽溢出，且与右侧「高级筛选」重复【估算 · 截图 2/3；确定 · `DesktopGallerySearchScreen.kt:911-966`】。
4. **Booru 全年龄开关仅在首页可见**：搜索结果页内无法直接查看或切换当前的「全年龄」排除状态【确定 · `DesktopGalleryTopSearch.kt:377` vs `DesktopGallerySearchScreen.kt:1128`】。
5. **高级筛选抽屉缺乏分组容器与视觉整合**：280dp 裸 Column 内含大写英文小副标（`TAG MATCH` 等）与原生 `OutlinedTextField`，视觉与桌面主体格格不入【确定 · `DesktopGallerySearchScreen.kt:972-1117`】。

---

## 3. 方案明细

### 3.1 视觉层

- **双场景表面统一**：深色走 `Background(#0A0D12)` → `SurfaceLevel1(#12161F)` → `SurfaceLevel2(#161B26)` → `SurfaceLevel3(#1B2130)`，1dp `OutlineHairline` 分层、零重投影；浅色走 `Background(#F0F2F6)` → `SurfaceLevel1(#FFFFFF)` → `SurfaceLevel2(#E8ECF2)` → `SurfaceLevel3(#DBE2EC)`，卡片/浮层带 `ShadowSpot(#101828)` 柔阴影 + 清晰边框。
- **首页顶栏悬浮搜索（`DesktopGalleryTopSearch`）**：
  - 搜索槽高 34dp、圆角 8dp、最小宽 220dp 最大 480dp；内嵌 Booru 范围胶囊圆角 4dp（`8 - 4 = 4` 同心圆）。
  - 下拉浮层最小宽 380dp、外圆角 12dp、内边距 8dp；内部分为「检索源（流式换行芯片，圆角 6dp）」「全年龄开关行（34×20 物理滑块）」「1dp Divider」「最近搜索列表（34dp 高，圆角 6dp，右带来源标与单删按钮）」「实时联想列表（左分类色点 + 译名/原名 + 右对齐 `tabular-nums` 作品数）」。
- **独立搜索结果页（`DesktopGallerySearchScreen`）**：
  - **顶区指挥栏**：透明底 + `20dp` 水平外边距 + 底部 `1dp Divider`。第一行所有主控件（模式分段器、主搜索框、Pixiv 目标下拉钮、主「搜索」按钮、「高级筛选」开关）**严格等高 36dp、外圆角 10dp**；分段器滑块与内置徽章圆角 7dp（`10 - 3 = 7`）。唯一主色 `AccentPrimary` 赋给有效状态下的「搜索」主钮。
  - **第二行快捷条**：取消超宽横滚，改为 `32dp` 高紧凑单行：Booru 保留「来源」紧凑方圆角芯片（高 26dp、圆角 6dp）+ 竖线 + 「排行」Pop-up 下拉芯片 + 「全年龄」切换开关；Pixiv 保留「维度（作品/画师/珍藏册）」+ 竖线 + 「排序」Pop-up + 「收藏门槛」Pop-up + 激活筛选徽章（支持一键清除）。
  - **高级筛选抽屉**：宽 272dp，内部用 `SurfaceLevel1` 分组卡片（圆角 12dp、内边距 12dp、内芯片圆角 6dp）承载「标签匹配」「过滤属性」「时间区间 / 排行周期」，去掉全大写英文副标，日期框统一为 32dp 自绘细边框输入槽。
  - **下方结果区**：画师行改为 44dp 头像（1px pure-black/white 内框）+ 信息列 + 轻按钮；补全空输入/无结果/加载失败的桌面化居中引导面板。

### 3.2 交互层

| 能力 | 做法 | 效果 |
|---|---|---|
| 站点不裁断 | `Row` 改为 `FlowRow`（间距 6dp） | 6~8 个站点全量清晰展示，零 `Ko` 裁断 |
| 快捷条不溢出 | 枚举长轴（Pixiv 收藏/排序、Booru 排行）收为 Pop-up 菜单芯片 | 1280px 窗口下单行完整呈现，彻底移除右侧渐隐断层 |
| 浮层同源统一 | 搜索页浮层改用结构化行列表（译名 + 原名 + 分类点 + 计数） | 扫读效率大幅提升，保留“点选回填原名”机制 |
| 全年龄双向同步 | 搜索页 Booru 快捷条接入壳层 `searchR18` 状态 | 首页与搜索页安全过滤状态完全透明且实时联动 |
| 桌面态反馈 | 悬停变背景与边框不位移（卡片外），按压采用瞬时色调/轻微 0.98 反馈 | 符合桌面端克制手感 |

### 3.3 数据层

- 全部 `DesktopGallerySearchScreen` 参数签名与状态回调（`onQueryChange`、`onSearch`、`onPickSource`、`onPickRanking`、`onPixivFilter`、`onPickPixivKind`、`onOpenPost`、`onOpenArtist`、`onOpenCollection` 等）保持完整与无默认值约束（满足 `DesktopCallbackParamLedgerTest`）。
- 搜索历史读写（`desktopHistoryStore`）与联想节流（250ms 防抖）、Pixiv AutoComplete 译名映射（只显示译名，回填仍为站方原名）保持原样。
- 仅在搜索结果页与壳层接线中透传已有的 `searchR18` / `onPickSearchR18` 状态，不新增持久化键。

---

## 4. 验收口径

- 逐区域对照表：
  - 首页顶栏搜索及下拉浮层 → `DesktopGalleryTopSearch.kt`
  - 搜索页顶栏、快捷条、联想浮层 → `DesktopGallerySearchScreen.kt` 顶部与 Flyout 区
  - 高级筛选抽屉与结果区空态/画师行 → `DesktopGallerySearchScreen.kt` 抽屉与内容区
- 阶段 B 三件套归档后落码；完成后增量编译 `:desktop:compileKotlin` 及 `DesktopGallerySearchUiTest`、`DesktopGallerySearchEntryTest`、`DesktopGallerySuggestionEntryTest`、`DesktopCallbackParamLedgerTest` 全绿。
- 验收标准：整页呈现统一桌面质感、无溢出裁断、无拼贴色块，深浅主题切换均清晰自然。

---

## 5. 实施记录

- 状态：已落码并经 `:desktop:compileKotlin` 及 6 组 UI/架构测试验证通过。
- 与原稿差异及原因：
  1. `DesktopGalleryTopSearch` 继续基于 `LocalChromePalette` 对位（保持与 `DesktopGalleryChrome` 顶栏色板单源），并结合 `LocalVeneraDarkTheme` 实现浅色投影与深色描边切换；
  2. `DesktopGallerySearchScreen` 的历史行保留单 `Text(entry.query)` 与独立标签芯片，确保与现有测试 `DesktopGallerySearchUiTest` 的精确文本点击断言一致；
  3. 同步接通了搜索结果页的 `searchR18` 状态读写与重搜触发，`DesktopGallerySearchScreen` 新增的两个必传形参无默认值，通过 `DesktopCallbackParamLedgerTest` 检验。
