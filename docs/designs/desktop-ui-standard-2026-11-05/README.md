# 桌面端统一 UI 规范

> **状态**：第一阶段（分析）· 待主人确认 · **代码零改动**
> **分析取样页**：图库主页（`DesktopGalleryWallPane`）—— 仅用于取样分析，**不是全端模板**
> **日期**：2026-11-05
> **适用范围**：`:desktop` 模块全部 Compose UI 页面
> **文档性质**：规范与报告，不是实现说明；确认后才进入第二阶段
> **补充定稿规范**：[全站透明玻璃卡片规范（glass-card-spec.md）](file:///g:/venera-desktop/desktop/docs/designs/desktop-ui-standard-2026-11-05/glass-card-spec.md)（2026-11-22 起作为新建/重做功能页默认卡片规范，对应共用原语 `DesktopGlassCard`）
> **补充定稿规范**：[按钮状态与过渡规格（button-state-spec.md）](file:///g:/venera-desktop/desktop/docs/designs/desktop-ui-standard-2026-11-05/button-state-spec.md)（新增按钮至少五态且每态有过渡；本文 §4.4 为该规格上游）
> **补充定稿规范**：[输入场 / 切换器 / 选择框统一规格（input-control-spec.md）](file:///g:/venera-desktop/desktop/docs/designs/desktop-ui-standard-2026-11-05/input-control-spec.md)（输入走 `DesktopInputField` / `DesktopSearchField`，切换走 `DesktopSegmented`，下拉走 `DesktopPopUpButton`）
> **补充定稿规范**：[基础控件与多选栏统一设计规范（controls-standard-spec.md）](file:///g:/venera-desktop/desktop/docs/designs/desktop-ui-standard-2026-11-05/controls-standard-spec.md)（2026-11-22 定稿：搜索框、分段选择框、三档按钮 `DesktopButton`、开关 `DesktopSwitch`、毛玻璃多选栏 `DesktopSelectionDock` 唯一属性与调用标准）

---

## 总原则（最高优先 · 主人 2026-11-05 明令）

> **这次工程只负责统一视觉系统，不负责重新设计产品。**

### 六条禁令

1. **不改功能布局。**
2. **不改业务逻辑。**
3. **不改路由和状态管理。**
4. **不拆巨型文件。**
5. **不随意改变页面原有特色。**
6. **不为了追求 Token 数量少而牺牲视觉表现。**

### 关键区别（决定全篇口径）

> **要做的不是让所有页面都像图库主页，而是让所有页面都像 Venera。**

| 容易做错 | 正确口径 |
|---|---|
| 把图库主页的布局、节顺序、卡片规格复制到别的页 | 只从它**取样**出 Venera 共有的视觉语言（表面、描边、字阶口径、状态），再按各页自己的型制落地 |
| 以「Token 数量少 / 档位少」当验收指标 | 视觉表现优先；档位该留就留，中间档若服务于观感就保留，不为数字好看强行合并 |
| 顺手修功能、改结构、重组页面 | 全部另案；本工程只换皮肤层 |
| 把每个页面抹成一个样 | 页面原有特色保留；统一的是「一眼认得出是 Venera」，不是「每页长一样」 |

### 主题口径

**只保留浅色与深色模式**，撤强调色档（主人 2026-11-05 明令）。

这是本工程**唯一一处由主人明令的产品层变更**，与「不重新设计产品」并列登记，不视为对六条禁令的破坏。落地口径（撤偏好键 / 保留键但固定为默认）需单独确认 —— 因为偏好键牵涉状态管理，不擅自决定，见 §8.3、§10。

### 优先级

本原则高于本文档其余各节。后续任何 Token 建议、组件清单、阶段任务、问题清单与之冲突时，以本原则为准；已按此原则修订 §1.1、§1.2、§2.4、§3、§5.3、§6.2、§7、§8。

### 目标形态（主人 2026-11-05 追加明令）

> 最终目标是形成一套 **Venera Desktop Design System（VDDS）**，而不是单纯把所有页面的颜色和圆角改得一样。

统一工程的范围按以下 12 域展开：

| 设计体系 | 主要内容 | 优先级 |
|---|---|---|
| Design Token | 颜色、字号、圆角、间距 | **P0** |
| Theme | 深浅主题、强调色 | **P0** |
| Layout | 对齐线、边距、网格、宽度 | **P0** |
| Components | 按钮、卡片、开关、菜单 | **P0** |
| Interaction | Hover、Focus、Pressed | **P0** |
| Iconography | 图标来源、尺寸、粗细 | P1 |
| Motion | 动画时长、曲线、转场 | P1 |
| Overlay | Dialog、Flyout、Tooltip | P1 |
| Content | 图片、标签、标题、列表 | P1 |
| Feedback | Loading、Empty、Error | P1 |
| Desktop UX | 鼠标、键盘、拖拽、滚动 | P1 |
| Responsive | 窗口缩放、窄屏布局 | P1 |

**实施优先级（主人明令）**：真正动手时优先处理 **布局、图标、状态、动画、弹层**；**内容展示与桌面端交互以规范补齐为主**，除非发现明显问题，否则不顺着工程顺手重做。本表映射进 §7 阶段任务：P0 五域即阶段 0–2 的主轴，P1 七域按此优先级排进阶段 2 各批与阶段 3。

---

## 0. 文档说明

### 0.1 结论来源

本文档全部结论来自只读检索（`rg` / `Read` / 行数统计），未修改任何源码、未新增任何 Design Token、未运行应用、未创建 UI 组件。每条结论带 `文件路径:行号`，便于复核。

**路径约定**：下文所有 `文件路径:行号` 均相对仓库根 `G:\venera-desktop\desktop\`。共享层路径前缀为 `app/src/main/java/com/venera/compose/`，桌面层为 `desktop/src/main/kotlin/com/venera/desktop/`。

### 0.2 数值口径

| 口径 | 含义 |
|---|---|
| **确定值** | 直接取自源码常量、令牌定义或既有规格文档，可复核 |
| **估算值** | 从截图或布局推算，本文档不用于规范取值，仅作对照时标注 |
| **统计值** | 只读计数结果，用于说明问题规模 |

本文档的规范取值一律用**确定值**；没有确定值的地方写「待定」，不伪装。

### 0.3 视觉验证状态

**Not verified**。未启动真窗、未跑浏览器；RTL 镜像、窄窗、200% 缩放、翻译增长均为静态推断。按项目约定，视觉验证由主人自供截图。

---

## 1. 目标与边界

### 1.1 要做什么

统一桌面端的**视觉系统**，让每个页面都像 Venera；建立一份可映射到实际 Compose 代码的规范，作为后续逐域迁移的验收依据。

图库主页是本次的**分析取样页**：从它读出现有的表面、描边、字阶口径、状态与动效，辨认哪些是 Venera 共有的视觉语言。**它不是全端模板**，布局与节顺序不外传（见 §5.3）。

### 1.2 本次任务边界（逐条对照主人约束）

| # | 约束 | 本文档如何处理 |
|---|---|---|
| 1 | 第一阶段只分析，不修改代码 | 全程零改动；本文档为唯一产出 |
| 2 | 不要擅自重新设计取样页 | §2 只记录现状，§2.7 登记脱节项但不提出改法 |
| 3 | 不要直接把取样页的布局复制到其他页面 | §5.3 页型矩阵明确「不得套用」列；总原则进一步明确「不是让所有页面都像图库主页」 |
| 4 | 不要为了统一而破坏不同页面原有的功能布局 | §5.4、§8.2 把布局与功能列为禁止变更 |
| 5 | 不要重复创建已有的公共组件 | §4.1 / §4.2 列实存件，§4.4 明确「收敛，不新建」 |
| 6 | 不要在未确认规范前批量修改页面 | §7 阶段 0 前置条件为「主人确认本规范」 |
| 7 | 规范必须能映射到实际 Compose 代码 | 每个 Token / 规则均给出写法或落点 |
| 8 | **只统一视觉系统，不重新设计产品**（总原则） | 六条禁令写进卷首，§8.2 逐条落为禁止变更 |
| 9 | **不随意改变页面原有特色**（总原则） | §5.3 每页型保留专用规则；§8.2 新增「页面特色」一行 |
| 10 | **不为 Token 数量少牺牲视觉表现**（总原则） | §3 改为「视觉表现优先」；撤回「禁用半档 / 收成一套」的强制口径 |
| 11 | **只保留浅色与深色模式**（总原则） | 卷首「主题口径」；§3.1 / §3.2 / §7.2 改按深浅两套板；撤强调色档属明令例外 |

### 1.3 规范生效条件

本规范在主人确认后生效。确认前，任何页面迁移、Token 映射、组件调整都不启动。

---

## 2. 取样页设计规范（图库主页 · 仅取样，非模板）

### 2.1 页面标识与装配链

| 层 | 落点 |
|---|---|
| 窗根 | `desktop/src/main/kotlin/com/venera/desktop/VeneraDesktopLargeScreen.kt:3894`，`Column(fillMaxSize)` + 12dp 裁切 + 1dp 描边 + `VeneraTokens.color.background` 底 |
| 窗口标题栏 | `desktop/src/main/kotlin/com/venera/desktop/ui/DesktopWindowTitleBar.kt:74`，46dp 自绘 |
| 侧栏 | `VeneraDesktopLargeScreen.kt:3967` → `app/src/main/java/com/venera/compose/components/VeneraSideBar.kt:167`；展开 264dp / 折叠 76dp（`VeneraDesktopLargeScreen.kt:4062`） |
| Tab 分派 | `VeneraDesktopLargeScreen.kt:5872` → `DesktopGalleryWallPane`（`:6925`） |
| 主装配 | `wallHeader` lambda，`VeneraDesktopLargeScreen.kt:7303-7439` |
| 主墙（等宽网格） | `VeneraDesktopLargeScreen.kt:7442` → `desktop/src/main/kotlin/com/venera/desktop/gallery/ui/DesktopJustifiedWall.kt:146` |
| 主墙（瀑布流） | `VeneraDesktopLargeScreen.kt:7494` → `app/src/main/java/com/venera/compose/gallery/ui/GalleryForYouScreen.kt:58` |
| 浮动按钮 | `GalleryRefreshFab`（`VeneraDesktopLargeScreen.kt:7581`）、`DesktopBackToTopFab`（`ui/DesktopAutoLoad.kt:211`） |

### 2.2 布局结构

`wallHeader` 内的节顺序（`VeneraDesktopLargeScreen.kt:7303-7439`）：

```
6dp
├─ DesktopGalleryChromeRow       顶栏 56dp           DesktopGalleryChrome.kt:67
├─ DesktopGallerySourceBar       状态行 24dp         DesktopGalleryChrome.kt:434
6dp
├─ DesktopGalleryFilterRow       筛选行 48dp         DesktopGalleryFilterRow.kt:102  （仅 BOORU）
4dp
├─ DesktopGalleryTagDimensions   标签舱              DesktopGalleryTagDimensions.kt:113（仅 BOORU）
4dp / 6dp
├─ DesktopGalleryHomeRows        首页横栏            DesktopGalleryHomeRows.kt:67
│   ├─ BOORU：画师行 → 每日热门
│   └─ PIXIV：发现带 → 关注新作 → 排行榜 → pixivision
4dp
├─ DesktopGalleryWallHead        墙头                DesktopGalleryChrome.kt:585
└─ 页尾说明（内联 Text）
```

**关键结构性质**：全页只有一条纵向滚动轴，header 随主墙一起滚动（`VeneraDesktopLargeScreen.kt:7303-7309`）；没有独立固定页头与内容滚动区的分层。这一条属于**本页性质**，不推广到其他页型（见 §5.3）。

节头画法在取样页内就有三种并存，作为不一致项登记在 §6.2，不在此合并：
`DesktopGalleryHomeRows.kt:307`（带 padding 与右槽）、`DesktopGalleryDiscoverRow.kt:267`（裸标题）、`DesktopGalleryChrome.kt:585`（右侧读数）。

### 2.3 视觉层次与表面效果

| 效果 | 现状 | 落点 |
|---|---|---|
| 页面底 | `VeneraTokens.color.background`（动态 MD3 派生） | `VeneraDesktopLargeScreen.kt:3899` |
| 卡片 / 控件槽底 | `GalleryTokens.Color.SurfaceLevel1/2/3` 三级 | `gallery/ui/GalleryTokens.kt:24-26`；消费见 `DesktopGalleryChrome.kt:112,169,243,322` |
| 旧件混用底 | `VeneraTokens.color.surfaceVariant.copy(alpha = 0.12f)` | `DesktopGalleryHomeRows.kt:241,365,393` |
| 描边 | 1dp `OutlineHairline`（`0x0FFFFFFF`）；hover `OutlineHover`；选中 `OutlineSelected` | `GalleryTokens.kt:29-31` |
| 顶栏 / 筛选行边界 | `drawBehind` 画 1dp **仅底边** 分隔线，无满宽底、无四边框 | `DesktopGalleryChrome.kt:91-99`、`DesktopGalleryFilterRow.kt:129-137` |
| 圆角 | `GalleryTokens.Shape` 6 / 10 / 12 / 16 / Pill | `GalleryTokens.kt:69-73` |
| 阴影 | 顶栏、控件、卡片一律 0；唯一例外右键浮层 `.shadow(16.dp)` | `ui/components/DesktopContextMenu.kt:177` |
| 渐变 | 仅两处图上文字护罩：人气标签底 100dp、pixivision 卡底 96dp | `DesktopGalleryDiscoverRow.kt:330-341`、`DesktopGalleryHomeRows.kt:258-269` |
| 模糊 | **无真模糊**；主题层 `material = SurfaceMaterial.SOLID` 钉死 | `gallery/ui/DesktopThemeBridge.kt:41-42,80` |
| 半透明浮面 | `SurfaceGlass = 0xD912161F`（hover 动作坞）、`0xD9161B26`（滑轨箭头） | `DesktopJustifiedWall.kt:418`、`DesktopGalleryDiscoverRow.kt:252` |

> 注：`DesktopJustifiedWall.kt:412` 注释称动作坞为「高斯模糊」，实际仅半透明底色，与 `DesktopThemeBridge.kt:80` 的 SOLID 设定冲突。登记为注释与实现不符，不推广该注释口径。

### 2.4 取样的视觉语言（Venera 共有部分）

以下六条是从取样页读出的**候选** Venera 视觉语言，与页面具体布局无关，可在各页型内落地。**是否适用于某一页，按该页型与原有特色判断**（§5.3、总原则第 5 条），不是逐页强制套用：

1. **Chrome 不做独立容器**：顶栏透明 = 页面底，仅一条 1dp 底分隔线暗示边界；控件靠浅槽辨识，不靠满宽底板。
2. **控件与内容同一张底**：分组靠空间（`spacedBy` / `Spacer`），不靠四边框；卡片只留 1dp 细描边。
3. **表面三级递进**：`SurfaceLevel1`（卡底）→ `SurfaceLevel2`（控件槽）→ `SurfaceLevel3`（选中 / 悬停），逐级抬亮，不叠加阴影。
4. **阴影分档**（2026-11-06 主人拍板修订：从「一律无阴影」改为分档分层）：**浅色档卡片用阴影浮起**（墙卡默认 4dp / hover 12dp，`spotColor` 走 `ShadowSpot` 槽，与设置卡同一套语言）；**深色档无阴影**，维持描边 + 表面抬亮分层。阴影只留给浮层与浅色卡片，不做装饰性重投影。
5. **渐变只用于图上文字护罩**：不用于主按钮、不用于整屏背景、不用于装饰。
6. **强调色限量**（现为**固定主色**，见卷首主题口径）：只出现在选中态、状态点、卡片徽标三处；不做渐变主钮、不做色块条。

> 这六条是「像 Venera」的判据，不是「像图库主页」。某页若因自身型制（如阅读器浮层、详情沉浸头）本就不该有底分隔线或卡片槽，保留其原样 —— 统一的是取值口径与质感，不是每页都长出同样的 chrome。

### 2.5 确定值表

| 项 | 值 | 来源 |
|---|---|---|
| 顶栏 / 筛选行 / 状态行高 | 56 / 48 / 24 dp | `DesktopGalleryChrome.kt:64`、`DesktopGalleryFilterRow.kt:128`、`DesktopGalleryChrome.kt:521` |
| 水平外边距 | 顶栏 20dp；内容轨 12dp；筛选行 12+10dp | `DesktopGalleryChrome.kt:100`、`app/src/main/java/com/venera/compose/ui/tokens/Spacing.kt:40`、`DesktopGalleryFilterRow.kt:127,138` |
| 组距 / 组内 | 20 / 10 dp | `DesktopGalleryChrome.kt:87` |
| 源段槽 / 档 / 字号 | 40dp / 34dp（实现 32dp）/ 14sp | `DesktopGalleryChrome.kt:108-153`、`docs/designs/header-integrate-2026-11-05/design-tokens.md:45-47` |
| 搜索框 | 42dp；min 280 / max 640dp；图标 18dp；14sp | `DesktopGalleryChrome.kt:159-205` |
| 方钮 / 文字钮 | 40 / 40 dp；图标 20dp | `DesktopGalleryChrome.kt:315` |
| 状态圆点 | 8dp | `DesktopGalleryChrome.kt:528` |
| 人气标签卡 / 推荐画师卡 | 136×224 / 300×224 dp | `GalleryTokens.kt:161,165` |
| 通用作品卡封面 / 信息栏 | 160dp / `heightIn(min = 42.dp)` | `DesktopGalleryHomeRows.kt:535`、`DesktopGalleryCardInfo.kt:122` |
| pixivision 主卡 / 相关卡 | `weight(1.35f) × 208dp` / `× 97dp` | `DesktopGalleryHomeRows.kt:210,221` |
| 主墙卡比例 | 1:1 方图 / 2:1 宽图 | `DesktopJustifiedWall.kt:375` |
| 网格间距 | 等宽网格 横 10 / 行底 16；瀑布流 横纵 14 dp | `DesktopGalleryMetrics.kt:225,234`、`DesktopJustifiedWall.kt:178,204` |
| 列数断点 | 4 / 5 / 6 / 7 档，阈值 1260 / 1560 / 1900 | `DesktopGalleryMetrics.kt:196-209` |
| 浮动按钮 | 刷新 56dp、回顶 44dp，右下 24dp | `DesktopGalleryMetrics.kt:342`、`DesktopAutoLoad.kt:176` |

### 2.6 状态与动效（Compose 映射）

| 状态 / 动效 | 实现写法 | 落点 |
|---|---|---|
| 悬停 | `MutableInteractionSource` + `collectIsHoveredAsState` 换底 / 换描边，`indication = null` 自绘 | `DesktopGalleryChrome.kt:125,262`、`DesktopGalleryFilterRow.kt:248` |
| 按压 | `collectIsPressedAsState` → `animateFloatAsState(0.96f)`；菜单项 0.98f | `DesktopGalleryChrome.kt:261`、`DesktopContextMenu.kt:216` |
| 卡片悬停位移 | `animateDpAsState(if (isHovered) (-3).dp else 0.dp)` + `Modifier.offset` | `DesktopJustifiedWall.kt:284,344` |
| 动作坞显隐 | `if (isHovered)` 条件组合，瞬时无过渡 | `DesktopJustifiedWall.kt:395-445` |
| Tab 切换 | `AnimatedContent` + `fadeIn/fadeOut(tween(180))` | `VeneraDesktopLargeScreen.kt:5872`、`DesktopNavMotion.kt:32,63` |
| 层级推进 / 返回 | fade + 水平 slide（32dp / 半距），220ms | `DesktopNavMotion.kt:35,41,47-60` |
| 切域淡入 | `Animatable` → `snapTo(0.45f)` → `animateTo(1f, tween(240))` | `VeneraDesktopLargeScreen.kt:7256-7261` |
| 刷新按钮自转 | `animateFloatAsState(..., tween(500))` + `Modifier.rotate` | `VeneraDesktopLargeScreen.kt:7263,7604` |
| 封面占位 | `VeneraShimmer` `infiniteRepeatable(tween(3000))` | `app/src/main/java/com/venera/compose/components/venera/VeneraShimmer.kt:52` |
| 横带翻页 | `LazyListState.animateScrollToItem` / `animateScrollBy` | `DesktopGalleryHomeRows.kt:584-599`、`DesktopGalleryDiscoverRow.kt:146-167` |
| 回到顶部 | `AnimatedVisibility(fadeIn/fadeOut)`，阈值 900px | `ui/DesktopAutoLoad.kt:135,147-152` |
| 右键菜单 | `DesktopContextMenuArea` 监听 `isSecondaryPressed` → `Popup` + 8dp 边缘避让 | `ui/components/DesktopContextMenu.kt:78-115` |

### 2.7 取样页内部不作为规范推广的脱节项

这些是取样页自身的现状问题，**不复制、不推广**；是否修、怎么修属于重新设计产品范畴，本工程原则上不碰（总原则六条禁令），仅登记供后续另案参考。**例外**：#1 的两套色板收敛属于视觉系统统一，纳入 §7 阶段 0。

| # | 问题 | 落点 |
|---|---|---|
| 1 | 两套色板同屏：`GalleryTokens.Color` 固定深蓝黑 vs `VeneraTokens.color` 动态 MD3 | `GalleryTokens.kt:23-46` / `DesktopGalleryHomeRows.kt:241,276` |
| 2 | `GalleryTokens.Component` / `Motion` / `Typography` 共 17+ 常量零消费；规格存在但代码不用 | `GalleryTokens.kt:140-192` |
| 3 | 顶栏私有常量 56dp 与 `GalleryTokens.Spacing.TopbarHeight = 52.dp` 不符 | `DesktopGalleryChrome.kt:64` / `GalleryTokens.kt:61` |
| 4 | `pageFade` 只套在瀑布流分支，默认等宽网格档切域无淡入 | `VeneraDesktopLargeScreen.kt:7493` vs `:7440-7490` |
| 5 | 顶栏 `sort` / `onPickSort` 形参未消费；`DesktopGallerySortChips` 无调用点 | `DesktopGalleryChrome.kt:71-72,381` |
| 6 | 注释称复用 `GalleryHomeDomainChips`，实际源段自绘 | `VeneraDesktopLargeScreen.kt:7311` vs `DesktopGalleryChrome.kt:122-153` |
| 7 | 四个旧壳只剩纯函数 / 零 Composable 调用 | `DesktopGalleryHero.kt:42`、`DesktopGalleryStatusBar.kt`、`DesktopGalleryPageHeader.kt:1-62`、`DesktopGalleryHomeSections.kt` |
| 8 | 46dp 窗条 + 56dp 内容顶栏双层，且对齐边不一致 | `DesktopWindowTitleBar.kt:84`(14dp) vs `DesktopGalleryChrome.kt:100`(20dp) |

---

## 3. Design Token 建议

### 3.0 本节前提（总原则第 6 条）

> **不为了追求 Token 数量少而牺牲视觉表现。**

必须先分清两件常被混为一谈的事：

| | 性质 | 本工程怎么做 |
|---|---|---|
| **同一语义，多处不同值** | 不一致（如 7 种蓝都表示「主色」、5 套卡片圆角） | ✅ 收敛到单一真源 —— 这正是「统一视觉系统」 |
| **多个不同语义档位** | 设计需要的层次（如 6/8/10/12/14/16dp 圆角、10.5/11.5/12.5sp 字阶、表面三级） | ❌ **不合并、不删档**。档位数量不是验收指标，观感才是 |

因此本文档**撤回**上一版里「禁用半档字」「收成一套档位」「档位越少越好」的强制口径。中间档若服务于排版节奏、密度层级或图像衬底，保留并收编到语义角色即可。验收判据是「同一语义是否只有一处取值」与「页面是否仍像 Venera」，不是「Token 总数降了多少」。

### 3.1 单一真源判定（按「只保留深浅模式」修订）

**结论：色值真源 = 深 / 浅两套中性板；不新增任何平行 `*Tokens` 对象。**

**现状链路**（供定位，主人已明令撤强调色档）：

```
DesktopAccentMode.index → PALETTE 种子        settings/SettingsGlass.kt:105-113
  → rememberDynamicColorScheme(seed, dark)   gallery/ui/DesktopThemeBridge.kt:74-81
  → VeneraPreviewTheme(colorScheme = ...)    ui/tokens/VeneraPreviewTheme.kt:36-90
  → VeneraTokens.color                        ui/tokens/VeneraTokens.kt:210-260
```

**撤档理由（如实登记）**：四套色源里只有共享组件这一支跟随强调色，桌面自制屏 93 处直读 `GalleryTokens` 固定 Hex，切档大半屏纹丝不动 —— 半残的开关不如不要（主人 2026-11-05 判断）。

**改后目标链路**：

```
DesktopThemeMode（深 / 浅 / 跟随系统）       settings/DesktopThemeMode.kt:14
  → 固定种子或中性色板（不再有 6 档 PALETTE）
  → VeneraPreviewTheme(colorScheme = ...)    ui/tokens/VeneraPreviewTheme.kt:36-90
  → VeneraTokens.color                       深 / 浅两套对位
```

`gallery/ui/GalleryTokens.kt` 降级为**语义映射层**（把固定 Hex 转成语义槽），不再作为色值真源。

**当前四套色源并存**（收敛目标：一套真源、深浅两套取值）：
1. `VeneraTokens.color` —— MaterialKolor 派生 MD3；改后固定种子，出深 / 浅两套
2. `GalleryTokens.Color` —— 固定深蓝黑，桌面自制屏 93 处消费；改后补浅色对位
3. `gallery/ui/DesktopTheme.kt:54-111` —— 五档中性灰 + 藤紫（旧稿），注释 `:9-20` 仍称「桌面没有主题切换」，与 `settings/DesktopThemeMode.kt:14` 事实不符
4. `settings/SettingsGlass.kt:17-93` 深浅玻璃板 + `FluentTheme` 库色板（`VeneraDesktop.kt:741`）

**如实登记的两笔代价**（不因拍板就藏起来）：
- 撤强调色**不会**让 342 处裸色自动统一 —— 那批散值本就不听它的话，收敛工作量不减。
- 桌面自制屏整套是深色固定值（深底 + 白字），浅色只在设置域有板子；「只留深浅」意味着**要给整套补一遍浅色对位**，比保留强调色更费。浅色底衬作品图的观感需主人截图判定，本文档不预设结论。

**落地口径待确认**：撤 `DesktopAccentMode` 偏好键属于状态管理变更，与总原则第 3 条冲突，不擅自决定 —— 可选「删键」或「保留键但固定为默认」，见 §8.3、§10。

### 3.2 色彩映射表（建议）

| 取样页现用 | 建议映射到 | Compose 写法 |
|---|---|---|
| `Background` | 窗 / 页面底 | `VeneraTokens.color.background`（深 / 浅各一套） |
| `SurfaceLevel1` | 卡片底 | `VeneraTokens.color.surface` |
| `SurfaceLevel2` | 控件槽 | `VeneraTokens.color.surfaceContainerLow` |
| `SurfaceLevel3` | 选中 / 悬停槽 | `VeneraTokens.color.surfaceContainer` |
| `OutlineHairline` | 细描边 | `VeneraTokens.color.outlineVariant.copy(alpha = 0.06f)` |
| `OutlineHover` | 悬停描边 | 同上提高 alpha |
| `OutlineSelected` | 选中描边 | 固定主色（见下） |
| `TextPrimary / Secondary / Tertiary` | 文字三级 | `onSurface` / `onSurfaceVariant` / 降 alpha；深浅各自对位 |
| `AccentPrimary` | **固定主色**（不再随档变化） | `VeneraTokens.color.primary`，单一确定值；深 / 浅两套分别定值 |
| `AccentSoft` | 菜单项悬停底 | `color.primary.copy(alpha = 0.16f)` |
| `StatusSuccess / StatusDanger` | 状态色 | 收进 `ui/tokens/Color.kt:186` 的 `StatusColors`；状态色不随主题档增减 |
| `SurfaceGlass` | 浮层半透明底 | 由 `SurfaceMaterialPolicy` 判定，不写死 Hex |
| **`OverlayScrim`**（2026-11-06 新槽） | 图上浮层深底（恒深，不随浅色翻白） | 动作坞 / 源徽标胶囊族；深色 `0xD007090C` 冻结 / 浅色 `0xD91B2130` |
| **`ToneDanger / ToneCaution / ToneSafe`**（2026-11-06 新槽） | 状态三色（恒定两档同值） | `F28B82 / F2B87C / 78D6C0`；消费：评分档、DqTone（已变转发件）、源管理启停 |
| `ImageHairline`（2026-11-06 新槽） | 图片 1px 淡框（浅色专属） | 深色 Transparent / 浅色 8% ink |

**阶段 2 收编成果（2026-11-06）**：紫主钮族 ×4 全灭（pixiv 主钮 / DqMiniButton / 双 tabActiveBg → 全局主色）；危险红 ×10（`EF4444`×7、`D9534F`×2、`F2B8B5`×2）→ `error` 语义槽；图上浮层三件套 ×5 组 → `OverlayScrim`；启用绿 `9CD290` → `ToneSafe`；收藏红心 → `StatusFavorite`。三套令牌文件的色值字面量为合法居所，其余桌面 main 由 `DesktopSurfaceLiterals` 前缀表看守（2026-11-06 扩展：白名单两文件 + 七个收编前缀）。

**收敛目标**：同一语义只有一处取值；当前发散的蓝（`#4D9FFF` / `#0096FA` / `#3880FF` / `#5B9CFF` / `#2563EB` / `#5A4FD0` / `#6B5CD8`）、红（7 值）、绿（5 值）、黄（4 值）统一到语义槽。**注意**：这里收敛的是「同一语义的多个值」，不是「把多种颜色合并成一种颜色」—— 主色、成功、危险、警告仍是各自独立语义，不为数量少而牺牲辨识度。

### 3.3 其他维度

| 维度 | 建议 | 可映射写法 | 现状问题 |
|---|---|---|---|
| 间距 | 单源 `VeneraSpacing`（2/4/6/8/12/16/20/24/30dp，`Spacing.kt:27-40`）；**一条内容左右边** | `VeneraTokens.spacing.*` | 12 / 14 / 16 / 20 / 22dp 五种左对齐边并存 |
| 圆角 | 单源 `VeneraShapeTokens`（`Spacing.kt:654-662`），类型统一为 `Dp`；同心圆 `Outer = Inner + Padding` | `RoundedCornerShape(VeneraTokens.shape.card)` | `GalleryTokens.Shape` 是 `RoundedCornerShape` 对象，类型不同；手写 302 处 / 23 种 N |
| 字阶 | 单源 `VeneraTypographyTokens`（`Typography.kt:21`）；**保留必要的中间档**（10.5/11.5/12.5sp 等），收编为语义角色，**不删档** | `VeneraTokens.type.caption` 等语义角色 | `Typography.kt:12` 的 A3.1 规则在桌面 385 处裸 `.sp` 前实际失效；问题在「裸值散落」不在「档位太多」 |
| 动效 | `VeneraMotion`（120/220/300/420ms，`VeneraMotion.kt:37-46`）+ 导航专属 `DesktopNavMotion`（180/220ms） | `VeneraMotion.tweenFast()`、`DesktopNavMotion.sameLevelEnter()` | 四套时长表并存；另有 `GalleryTokens.Motion` 零消费 |
| 表面 | `SurfaceMaterialPolicy`（`SurfaceMaterialPolicy.kt:82`）玻璃三档已存在，桌面被 `SOLID` 钉死属**未接线**而非不可复用 | `LocalSurfaceMaterial` | 是否启用留第二阶段决定 |
| 阴影 | 常规元素 0；浮层统一 `0 8dp 24dp` | `Modifier.shadow(16.dp)` 仅菜单 | 设置域 4/12dp、阅读器 12/16dp 与规格冲突 |
| **Iconography**（2026-11-06 声明） | 图标库单源 Material Symbols **Outlined**（103 处，Filled/Default 仅 20 处遗留）；**尺寸主档 18dp**（顶栏钮内 20dp） | `Icons.Outlined.*` + `Modifier.size(18.dp)` | 全窗逐处归一与组件布局强耦合，按「规范补齐为主」口径登记，不逐处强改 |

## 3.6 运动单源（2026-11-06 阶段收尾）

`GalleryTokens.Motion`（零消费）已删除；时长单源 = `VeneraMotion`（120/220/300/420）+ 导航专属 `DesktopNavMotion`。硬编码清理：切域淡入 `tween(240)` → `VeneraMotion.tweenNormal()`、刷新自转 `tween(500)` → `tweenSlow()`。`VeneraMotionTokens` 在共享冻结层不动。

### 3.4 界面文案规则（写入规范约束，不是新 Token）

- 主标题 ≤ 6 字，副标题 ≤ 15 字。
- 只使用克制的安卓与桌面端标准软件术语；禁用拟人化、比喻、文学修辞。
- 文案改动只影响界面串，不影响回调签名与判据口径（判据口径变化需单独登记）。

### 3.5 判据补强（建议，第二阶段执行）

| 判据 | 当前钉住 | 缺口 |
|---|---|---|
| `desktop/src/test/.../gallery/ui/DesktopSurfaceLiterals.kt:34-59` | 旧稿五档 + 文字 + 藤紫色值前缀白名单 | `GalleryTokens` / `SettingsGlass` 的 60+ 处裸色漏网；撤强调色后 `DesktopAccentMode.PALETTE` 一处随之失效 |
| `desktop/src/test/.../architecture/DesktopThemeLayerTest.kt:38-225` | 主题接线唯一、壳层顺序、色值字面量禁入薄桥 | 只钉接线，不钉取值一致性 |
| `desktop/src/test/.../gallery/ui/DesktopCallbackParamLedgerTest.kt:31-56` | 回调不许带默认值 | 与 UI 一致性无关 |
| `desktop/src/test/.../gallery/ui/DesktopPageChromeTest.kt:40-179` | 文案 / 结构 / 禁 `Ctrl K` 键帽 | 不碰色板 |
| `app/src/test/java/.../DesktopNoAndroidImportTest.kt:31-178` | 编译面边界 | 与 UI 一致性无关 |

**建议补一条**：`GalleryTokens` 的语义槽必须来自 `VeneraTokens`，防止规范写完继续漂移。补尺属于阶段 0 交付，不在本阶段实施。

---

## 4. 公共组件清单

### 4.1 通用共享件（优先复用，禁止重复创建）

| 组件 | 定义 | 取样页是否使用 |
|---|---|---|
| `VeneraCover` / `VeneraCoverMask` | `app/.../components/venera/VeneraCover.kt:86,37` | ✅ 唯一图像件 |
| `VeneraButton` / `VeneraTextButton` | `VeneraControls.kt:256,215` | ✅ 空墙 / 页尾 / 查看全部 |
| `VeneraCard` | `VeneraCard.kt:45` | ❌ 首页不用（瀑布流卡在用） |
| `VeneraChip` / `VeneraFilterPill` / `VeneraSegmentedButton` / `VeneraSelectBox` | `VeneraChip.kt:105` 等 | ❌ 搜索 / 设置屏在用 |
| `VeneraTextField` | `VeneraControls.kt:343` | ❌ 桌面全模块零引用 |
| `VeneraSwitch` / `VeneraCheckbox` / `VeneraSlider` / `VeneraDialog` | `VeneraControls.kt:71,283,106,428` | ❌ 他页在用 |
| `VeneraSideBar` | `components/VeneraSideBar.kt:167` | ✅ 壳层 |
| `VeneraBackHandle` | `components/VeneraBackHandle.kt:48` | ❌ 二级页在用 |
| `VeneraGlass` / `VeneraShimmer` / `VeneraHScrollFade` / `VeneraNotice` | `VeneraGlass.kt:161` 等 | ❌ 未接或他页 |
| `VeneraTokens` / `VeneraMotion` / `VeneraPreviewTheme` | `ui/tokens/` | ✅ |
| `GalleryForYouPage` / `GalleryWallGrid` / `PixivRankingModeChips` | `app/.../gallery/ui/`、`GalleryPixivHomePills.kt:41` | ✅ 瀑布流档 / 排行榜 |
| `DesktopPageShell` / `ListPageTemplate` / `GridPageTemplate` / `DetailPageTemplate` / `ViewerPageTemplate` / `SettingsPageTemplate` / `SettingsCardTemplate` / `SettingsRowTemplate` | `ui/DesktopPageTemplates.kt:64,150,186,275,323,438,588,673` | 桌面模板层，已存在 |
| `DesktopContextMenuArea` / `DesktopMenuEntry` | `ui/components/DesktopContextMenu.kt:78,61` | ✅ 主墙卡右键 |
| `DesktopBackToTopFab` / `DesktopAutoLoad` | `ui/DesktopAutoLoad.kt:211,59` | ✅ |

> **重要**：`VeneraContextMenuArea` / `VeneraMenuElement` 在全仓 `.kt` 中**零定义、零调用**。`docs/designs/context-menu-system-2026-11-05.html:564-566` 要求封装它，实际落地的是 `DesktopContextMenuArea` + `Popup` + `GalleryTokens`（`DesktopContextMenu.kt:54,150,172`）。规范以**实存单源**为准，不再创建同名共享件 —— 那属于重复创建。

### 4.2 页面 / 域专属件（保留，不提升为全局）

`DesktopGalleryChromeRow` / `DesktopGallerySourceBar` / `DesktopGalleryWallHead`（`DesktopGalleryChrome.kt`）、`DesktopGalleryFilterRow` + `FilterSegment` + `SourceChip`（`DesktopGalleryFilterRow.kt`）、`DesktopGalleryTagDimensions` + `DimensionChip`、`DesktopGalleryDiscoverRow` + `HotTagCard` + `RecommendedUserCard` + `RailArrowButton` + `DiscoverHead`、`DesktopGalleryHomeRows` + `DesktopGalleryArtistsRow` + `DesktopGalleryDailyRow` + `CarouselButton` + `DesktopGalleryPixivisionRow` + `PixivisionCard` + `DesktopGallerySectionHeader`、`DesktopGalleryCardInfo`、`DesktopJustifiedWall` + `GridCard`、`GalleryRefreshFab`（壳文件内）。

### 4.3 重复并行件（收敛，不新建）

| 语义 | 实现 A | 实现 B | 备注 |
|---|---|---|---|
| 复选框 / 开关 | `ui/DesktopVeneraPrimitives.kt:73,286` | `VeneraControls.kt:283,71` | 靠包名区分，桌面两种都在用 |
| 对话框 | `DesktopVeneraPrimitives.kt:322` `VeneraModalDialog` | `VeneraControls.kt:428` `VeneraDialog` | — |
| 右键菜单 | `ui/components/DesktopContextMenu.kt:78` | `app/.../components/ComicCardContextMenu.kt:27` | B 在桌面模块零调用 |
| 页面外壳 | `DesktopPageTemplates.kt:64` `DesktopPageShell` | `VeneraSideBar.kt:167` + `VeneraTopAppBar.kt:101` | — |
| 多选条 | `ui/DesktopSelectionBlock.kt:36` | `app/.../components/selection/VeneraMultiSelectBar.kt:61` | — |
| 玻璃材质 | `ui/tokens/SurfaceMaterialPolicy.kt:82` | `settings/SettingsGlass.kt:17` | 桌面桥钉死 `SOLID` |
| 下拉菜单 | 7 处散落 `DropdownMenu` / `Popup`（`DesktopGalleryChrome.kt:238,543` 等） | 统一形态 | 见 §6.2 |

### 4.4 组件状态契约（统一映射，不新增组件）

| 状态 | 统一实现 |
|---|---|
| 默认 | `SurfaceLevel2` 槽 / 1dp `OutlineHairline` / `TextSecondary` |
| 悬停 | `collectIsHoveredAsState` → `SurfaceLevel3` + `OutlineHover` |
| 按压 | `collectIsPressedAsState` → `animateFloatAsState(0.96f)`，150ms |
| 激活 | `color.primary` 或 `SurfaceLevel3` + `FontWeight.SemiBold` |
| 焦点 | 当前缺少可见焦点态；建议补 2dp `OutlineSelected`（全局审计 HIGH 项） |
| 禁用 | `TextTertiary` + `alpha`，按压反馈关闭 |

---

## 5. 页面布局规范

### 5.1 全窗骨架（壳层，非取样页专属）

`46dp 窗口标题栏 + 侧栏（264 / 76dp）+ 内容区`（`VeneraDesktopLargeScreen.kt:3894-3985`）。此层全应用共享，不从图库主页复制。

### 5.2 全页通用规则（可应用到所有页面）

以 better-layout 尺子逐条给出 Compose 映射：

| 规则 | Compose 落法 |
|---|---|
| 分组靠空间不靠线 | `Arrangement.spacedBy` + `Spacer`；`drawBehind` 底线只许 chrome 边界；卡片禁 `Modifier.border(四边)` |
| 控件与静态文字分家 | 可点控件必须有 `background` / `border` / 固定工具区；禁止裸 `Text` 当控件 |
| 对齐共享边 | 单一 `screenHorizontal`；顶栏 / 筛选行 / 卡片轨 / 页尾同值；`padding(horizontal = ...)` 不叠加两层 |
| 隐藏内容露边 | 横向 `LazyRow` / `horizontalScroll` 接 `VeneraHScrollFade`（`VeneraHScrollFade.kt:49`）或箭头披露 |
| 单主行动 / 顺序按重要性 | 一屏一个 `primary` 按钮；`Row` 声明顺序 = 视觉顺序；标题 leading、动作 trailing |
| 文字容器不钉死高 | `heightIn(min = ...)` 替 `height(...)`，配 `maxLines` + 展开 / tooltip |
| 响应式 | `BoxWithConstraints` 断点；文字 `min-height` + `max-width`；不钉死宽高 |

### 5.3 页型矩阵（哪些规则只能用于特定页面类型）

> **总原则第 5 条落地处**：不随意改变页面原有特色。下表「专用规则」列是**必须保留**的页面特色，不是待统一的差异项。迁移时只换这些特色的**取值口径**（色 / 圆角 / 字阶 / 状态），不换特色本身。

| 页型 | 页面 | 必须保留的原有特色 | **不得套用** |
|---|---|---|---|
| 内容浏览 | 图库主页、搜索、收藏、探索、Pixiv 作品墙 | 单纵滚动 + header 随内容滚、卡片轨、列数断点、露边提示、hover 动作坞 | 主从分栏、沉浸头 |
| 主从 | 源管理、设置 | 左右分栏、左列表右详情、`surfaceContainerHigh` 底 | 单纵滚动墙、卡片轨 |
| 详情 | 漫画详情、图库作品详情、pixivision 文章 | 沉浸头、双列、浮动 chrome | 顶栏底线、墙卡网格 |
| 阅读 / 播放 | 阅读器、大图播放器 | 全屏画布、点击显隐浮层、浮层控制条 | 任何标准 chrome、卡片语言 |
| 表单 / 设置 | 设置中枢各档 | 卡片分组、`SettingsCardTemplate` | 内容墙、横带 |

> 本节是「**不直接把取样页布局复制到其他页面**」的落点，也是「**让所有页面都像 Venera，而不是像图库主页**」的落点：只把 §5.2 的通用规则抽出，图库主页「发现带 → 关注新作 → 排行榜 → pixivision → 主墙」的节顺序**不外传**；各页特有的沉浸头、浮层控制条、主从分栏一律保留。

### 5.4 功能布局守线

统一只换皮肤层（色 / 圆角 / 字阶 / 间距 / 状态 / 动效）：页面节顺序、区域划分、入口数量、页面原有特色、`BusinessPorts` 17 族、`DesktopGalleryBatch`、路由与状态管理（`viewModel()` 冻结 17 处）、`LocalUxPlatformServices`、`LocalDesktopSearchHandoff`、偏好键一律不动。另按总原则：**不拆巨型文件**、**不重新设计产品**。

---

## 6. 现有 UI 不一致问题清单（报告）

### 6.1 规模统计（只读计数）

| 项 | 数值 |
|---|---|
| `Color(0x…)` 字面量 | 342 处（app 104 / desktop 238），134 个不同色值 |
| 裸 `.dp` / 裸 `.sp` | 2885 / 658 处 |
| 手写 `RoundedCornerShape` | 302 处 / 23 种 N |
| 手写 `fontSize` | 358 处 / 29 档 |
| 自定义 `*Tokens` 对象 | 11 个 |
| 1500 行以上巨型文件 | 4 个：`VeneraDesktopLargeScreen.kt`(7607)、`DesktopSettingsHub.kt`(1890)、`DesktopComicDetailScreen.kt`(1719)、`DesktopDownloadsPipelineScreen.kt`(1546) |

重灾域：`reader/DesktopReaderScreen.kt`(47 色)、`settings/SettingsGlass.kt`(41)、`downloads/DesktopDownloadsPipelineScreen.kt`、`DesktopHomePane.kt`、`app/.../feature/sourcemanage/ComicSourceScreen.kt`。

### 6.2 问题报告（better-layout 格式）

| Severity | Location | Before | After | Why |
|---|---|---|---|---|
| HIGH | `ui/DesktopWindowTitleBar.kt:141-154` | 上下文药丸 `clip(999.dp) + clickable`，无底无边 | 补 `background` / `border` 控件形状 | 与普通面包屑文字同形，无法辨识可点 |
| HIGH | `detail/DesktopComicDetailScreen.kt:330` | `Text("← 返回")` 纯文字 | 换 `VeneraBackHandle`（已存在） | 返回控件无形状、无固定工具区 |
| HIGH | `gallery/ui/DesktopGalleryFilterRow.kt:124-225` | 48dp 定高单行塞 6 组、`spacedBy(12.dp)` 无换行 | **仅补响应式容纳**（可换行 / 断点 / 文字容器 `heightIn`）；**不改分组、不删控件、不动筛选项** | 窄窗或翻译增长必然裁切；属视觉容纳问题，不属功能布局变更 |
| HIGH | 13 处 `horizontalScroll`：`search/DesktopSearchScreen.kt:186`、`explore/DesktopExploreScreen.kt:343`、`favorites/DesktopFavoritesScreen.kt:165`、`gallery/ui/DesktopGallerySearchScreen.kt:835`、`detail/DesktopComicDetailScreen.kt:986,1011,1245,1612` 等 | 零渐隐露边、零披露控件 | 接 `VeneraHScrollFade` | 横向还有内容却无提示 |
| HIGH | `gallery/ui/DesktopGalleryChrome.kt:251-254` | 「布局 / 密度 / 搜索标签 / 刷新」只 `onDismiss` | **本工程不处理**（属功能修复）；仅登记，另案 | 无实际动作的控件；接真动作会改业务逻辑，违反总原则第 2 条 |
| HIGH | `gallery/ui/DesktopWallCard.kt:118-127` | 「屏蔽画师」只写剪贴板 | **本工程不处理**（属功能修复）；仅登记，另案 | 标签与行为不符；同上 |
| MEDIUM | `DesktopWindowTitleBar.kt:84`(14) / `DesktopGalleryChrome.kt:100`(20) / `:517,599`(12) / `DesktopGalleryFilterRow.kt:127,138`(22) / `favorites/DesktopNetworkFavoritesScreen.kt:132`(16) | 五种左对齐边 | 单一 `screenHorizontal` | 对齐边不共享 |
| MEDIUM | `VeneraCard.kt:67`(16/18) / `DesktopJustifiedWall.kt:345`(12) / `settings/SettingsCards.kt:61`(24) / `settings/SettingsBento.kt:96`(20) | 卡片圆角 5 套、信息栏 3 种位置 | **同一语义归一**到 `VeneraShapeTokens`；页型确有不同档位则保留（总原则第 6 条），信息栏位置按各页特色保留 | 同类卡片取值不一致；不为档位数量牺牲各页特色 |
| MEDIUM | `GalleryTokens.kt:23-45` / `DesktopTheme.kt:54` / `SettingsGlass.kt:17` / `VeneraTokens.kt:210` | 四套色源；蓝色 7 值；强调色档只影响其中一支 | 单源 `VeneraTokens.color`，深 / 浅两套对位；撤强调色档 | 强调色是半残开关（主人明令撤），散值不随之自动统一 |
| MEDIUM | `ui/components/DesktopContextMenu.kt:78` ↔ `app/.../components/ComicCardContextMenu.kt:27` + 7 处散落 `DropdownMenu` | 三类菜单实现 | 单源 `DesktopContextMenuArea` | 菜单形态分裂 |
| MEDIUM | `VeneraDesktopLargeScreen.kt:3907`(46dp) + `:7321`(56dp) | 双层顶栏、双搜索起点 | **仅统一对齐边与取值口径**；合并与否属功能布局变更，本工程不做，留 §8.3 | 合计 94dp 且对齐边不齐；不重新设计产品 |
| MEDIUM | `settings/SettingsCards.kt:98`、`settings/SettingsBento.kt:99-115` | 卡片 4/12dp 阴影、自绘投影 | 收敛为浮层专用阴影 | 与「常规元素无阴影」冲突 |
| LOW | `DesktopGalleryHomeRows.kt:307` / `DesktopGalleryDiscoverRow.kt:267` / `DesktopGalleryChrome.kt:585` | 节头三种画法，padding 与右槽规则不一 | 统一**取值口径**（字阶 / 间距 / 颜色）；三种画法若服务于各节不同用途则保留（总原则第 5 条） | 同类元素取值不一致，不等于要抹成一个组件 |
| LOW | `GalleryTokens.kt:169-191`、`DesktopGalleryMetrics.kt:228`、`VeneraDesktopLargeScreen.kt:7311`、`DesktopJustifiedWall.kt:412` | 17+ 零引用令牌；注释与实现不符 | 零引用令牌**按视觉表现需要决定保留或落地**，不为数量少而批量删（总原则第 6 条）；注释对齐实现 | 规格存在但代码不用，与档位数量是两回事 |
| LOW | `gallery/ui/DesktopGalleryPageHeader.kt:1-62`（无 Composable）、`downloads/DesktopDownloadsChrome.kt:1-358`（名为 Chrome，实为卡片零件） | 文件名与内容不符 | **本工程不改名、不删文件**（属结构变更）；仅登记 | 增加检索成本，但清理属重新设计产品范畴 |

### 6.3 设计稿治理

`docs/designs/` 现存 18 份 HTML，其中 16 份零代码引用；8 份 `design-tokens.md` 之间至少 6 组确定值冲突：

| 冲突项 | 并存取值 |
|---|---|
| 页面 / 顶栏底色 | `#0A0D12` / `#07090E` / `#12161F` |
| 顶栏高 | 40 / 48 / 52 / 56dp |
| 搜索框宽 | 240-560 / 280-640 / 360-580dp |
| 搜索框高 | 30 / 36 / 42dp |
| 右侧方钮 | 32 / 40 / 42dp |
| 阴影 | 「一律无」/ 设置域 4dp 与 12dp |

`header-spec-2026-11-05/` 已被三份后续规格宣告不引用，文件仍在；`window-header-redesign-2026-11-05.html:116` 自述「只出方案，代码零改动」；`context-menu-system-2026-11-05.html:564-566` 的落地指引未执行。

**建议**：指定唯一真源并为其余稿加 `SUPERSEDED` 标记。**已执行（2026-11-06，主人批准按推荐做）**：18 份现存稿已全部插入状态标记 —— 9 份 `SUPERSEDED`（被取代留档）、5 份 `IMPLEMENTED`（已落地留档）、4 份 `ACTIVE`（现行引用或待议：`context-menu-system` / `desktop-header-unify` / `header-integrate` / `window-header-redesign`）。真源优先级：本规范（总纲）> `gallery-spec-2026-11-05/` 三件套（色 / 间距 / 圆角 / 字阶取样真源）> `header-integrate-2026-11-05/` 三件套（顶栏 04 档实现口径）> 各 HTML 稿（留档）。冲突时以实存代码 + 本规范为准。

### 6.4 审查结论与范围声明

**Block**（存在 5 类 HIGH 项）—— 这是对「当前界面质量」的审查结论，**不是本工程的待办清单**。

按总原则重新划分：

| 归属 | 条目 |
|---|---|
| ✅ 本工程处理（视觉系统） | 对齐边、卡片圆角 / 字阶取值、色源收敛与深浅对位、阴影归属、露边提示、焦点态、控件形状、菜单形态、表面质感 |
| ❌ 本工程不处理（功能 / 结构，另案） | 假菜单项接真动作、屏蔽画师行为、双层顶栏合并、旧壳改名删除、巨型文件拆分、路由与状态管理 |

**Not verified**：未跑真窗 / 浏览器；RTL 镜像、320dp、200% 缩放、翻译增长均为静态推断。视觉验证按项目约定由主人自供截图。

---

## 7. 后续页面迁移建议（阶段任务）

> **前置条件**：主人确认本规范。确认前不启动任何阶段。

### 7.1 阶段总表

| 阶段 | 名称 | 输入 | 主要动作 | 产出 | 验收 | 状态 |
|---|---|---|---|---|---|---|
| 0 | 地基 | 本规范 + 主人确认的主题落地口径 | 深浅两套色板定稿、`GalleryTokens` 降级为语义映射层、同一语义归一、判据补强、设计稿真源标注 | 色板 + 映射尺 + 真源声明 | `:desktop:compileKotlin` + 点名尺 | ✅ 2026-11-06（1610/0） |
| 1 | 全窗壳 | 阶段 0 | 窗条 / 侧栏 / 单一对齐边 / 右键菜单单源 / 焦点态 | 壳层一致性 | `:desktop:compileKotlin` + 点名尺 | ✅ 2026-11-06（1610/0） |
| 2 | 逐域迁移 | 阶段 1 | 按页型分批换皮肤层，**逐页保留原有特色** | 各域页面 | 每域 `compileKotlin` + 点名尺 | ✅ 2026-11-06（1610/0） |
| 3 | 收口 | 阶段 2 | 巨型件皮肤替换（**不拆**）、全量对账 | 全量报告 | `:desktop:test` 全量 **一次** | 部分完成（三颗巨型件已随域批覆盖） |

**阶段 2 实际交付（2026-11-06）**：内容浏览域（浮层归槽 / pixiv 主钮去紫 / 对齐边）、下载域（紫钮族 / 危险红 / 浮层）、设置域（危险红归槽；Bento 圆角 24/20 判定为页型特色保留）、详情域（状态三色升槽 / 源管理归槽 / 收藏红心单源）；附赠修复：Grid 档切域淡入缺失（基准页脱节项 #4）、顶栏三控件焦点环、筛选行焦点环、节头题字号归一（13→16sp）。

### 7.2 阶段 0：地基

> **本阶段受两条新口径约束**：① 只保留深浅模式，撤强调色档；② 不为 Token 数量少牺牲视觉表现。

**做什么**
1. 定深 / 浅两套中性色板，逐项给桌面自制屏补浅色对位（§3.1 两笔代价里的第二笔）。
2. 撤强调色档：`DesktopAccentMode.PALETTE` 6 档与偏好键的处置，按 §8.3 / §10 主人确认的口径执行 —— **删键或固定默认，不擅自定**。
3. 把 `GalleryTokens.Color.*` 的固定 Hex 改为读 `VeneraTokens.color` 的语义槽（§3.2 表）。
4. **同一语义归一**：7 种蓝 / 7 种红 / 5 种绿、5 套卡片圆角、29 档字号里的重复值 → 收编到语义角色；**不同语义的档位保留**（§3.0）。
5. 零引用常量（17+）按视觉表现需要决定「落地到实装」或「保留待用」，**不为数量少批量删**。
6. 补一条「语义槽必须来自 `VeneraTokens`」的映射尺。
7. 指定 `docs/designs/` 唯一真源，其余稿标 `SUPERSEDED`。

**能改变什么**：色板取值、Token 取值来源、同一语义的重复值、零引用常量处置、判据口径、文档状态标记、强调色档（主人明令）。
**不能改变什么**：任何页面视觉、任何组件签名、任何布局、路由、状态管理、巨型文件结构、各页原有特色。
**风险**：主题接线判据 `DesktopThemeLayerTest.kt:38-90` 对薄桥色值字面量敏感；`DesktopAccentMode` 牵涉偏好键与状态管理，改动需按主人确认口径，编译后核对。

### 7.3 阶段 1：全窗壳

**做什么**
1. 统一 `screenHorizontal`，收敛 12/14/16/20/22dp 五种对齐边。
2. 窗条上下文药丸补可辨识控件形状（HIGH 项）。
3. 裸 `Text("← 返回")` 换 `VeneraBackHandle`（HIGH 项）—— 换的是控件形状，**返回行为与路由不变**。
4. 右键 / 下拉菜单收敛到 `DesktopContextMenuArea` 单源（形态统一）；**不接真动作、不撤项**（属功能，另案，§6.4）。
5. 补全局可见焦点态。

**能改变什么**：壳层表面、控件形状、对齐边、菜单形态、焦点态。
**不能改变什么**：页内布局、节顺序、入口数量、回调签名、菜单项行为、路由与状态管理。
**收益**：所有页面同时受益，风险最低，先收口。

### 7.4 阶段 2：逐域迁移（按页型分批）

| 批次 | 页型 | 页面 | 动作 | 必须保留的原有特色 |
|---|---|---|---|---|
| 2.1 | 内容浏览 | 搜索、收藏、探索、Pixiv 作品墙 | 与取样页同类，先迁；只换皮肤层 | 各自的节顺序与卡片轨型制，**不套图库主页的节顺序** |
| 2.2 | 主从 / 表单 | 设置、下载、源管理 | 同语义圆角归一、阴影收敛、字阶归一 | 左右分栏、卡片分组、Bento 舱各自形态 |
| 2.3 | 详情 | 漫画详情、图库详情、pixivision 文章 | 表面与字阶归一 | 沉浸头、双列、浮动 chrome |
| 2.4 | 阅读 / 播放 | 阅读器、大图播放器 | 最后迁，浮层控制条单独处理 | 全屏画布、点击显隐、浮层控制条 |

**每批内动作**：色 → 圆角 → 字阶 → 间距 → 状态 → 动效，逐项替换**取值**；**不换结构**。
**每批验收**：`:desktop:compileKotlin` + 该域点名尺；**不跑全量测试**。
**能改变什么**：皮肤层六项的取值。
**不能改变什么**：页型专用规则与页面原有特色（§5.3）、功能布局、入口、回调、路由、状态管理、数据来源。
**判据**：「这一页现在像 Venera」且「它还是它自己」，不是「它像图库主页」。

### 7.5 阶段 3：收口

**做什么**：四颗巨型件只做皮肤替换，**不拆分**（总原则第 4 条）；跑全量对账。
**验收**：`:desktop:test` 全量一次（基线 1608 通过 / 0 失败），`node _tools/count_desktop_tests.js` 计数；判据类红允许拖到这一次一起收。
**能改变什么**：巨型件内的样式取值。
**不能改变什么**：巨型件结构 —— **本工程任何阶段都不拆**，此项已从「需另令」升级为硬禁令。

### 7.6 不迁移白名单

| 项 | 原因 |
|---|---|
| 阅读器浮层控制条 | 页型专用，留阶段 2.4 |
| pixivision 二级页作品图 | `VeneraCover.kt:86` 写死 `ContentScale.Crop`，该页用 `AsyncImage + ContentScale.Fit` 保原比例（`DesktopGalleryPixivisionArticleScreen.kt`） |
| `BusinessPorts` 17 族 / `LocalUxPlatformServices` | 业务数据边界 |
| `viewModel()` 冻结 17 处 | `ViewModelAssemblyGuardTest` |
| `LocalDesktopSearchHandoff` | 真交接，非 UI |
| 偏好键与判据口径 | 改动需单独登记 |

---

## 8. 变更边界：能改变什么 / 不能改变什么

### 8.1 允许改变（皮肤层 · 仅视觉系统）

> 前提：不改变产品，只统一视觉取值。每一项都不得成为「顺手改功能 / 改结构」的入口。

| 类别 | 具体项 | 边界 |
|---|---|---|
| 颜色 | 语义槽取值来源、表面三级、描边色、文字三级、状态色、**深 / 浅两套对位** | 只换取值；不为少而合并不同语义色 |
| 主题档 | **撤强调色档，只保留浅色 / 深色**（主人明令） | 唯一的产品层例外；偏好键处置需确认（§8.3） |
| 形状 | 圆角**取值**、裁切形状、同心圆几何 | 不同页型的档位保留 |
| 字阶 | 字号**取值**、字重、行高，收编到 `VeneraTypographyTokens` 语义角色 | **保留必要中间档，不删档** |
| 间距 | 内外边距、组距、对齐边（同一语义收敛为单值） | 不改区域划分 |
| 状态 | 默认 / 悬停 / 按压 / 激活 / **焦点** / 禁用 的视觉实现 | 不改触发条件与行为 |
| 表面 | 卡片底、控件槽底、浮层半透明底、阴影归属 | 不改卡片里的信息结构 |
| 动效 | 时长、缓动、显隐方式（收敛到 `VeneraMotion` / `DesktopNavMotion`） | 不改导航逻辑 |
| 菜单 | 形态、圆角、行高、危险项隔离（收敛为单源） | **不改菜单项行为** |
| 控件形状 | 可辨识背景 / 边框、焦点框 | 不改点击后的动作与路由 |
| 容纳 | 文字容器 `heightIn`、横滑露边提示、可换行容纳 | **不改分组、不删控件** |
| 界面文案 | 在 ≤6 / ≤15 字与禁用修辞规则内的措辞 | 不改判据口径（变化须登记） |
| 令牌与判据 | 同一语义归一、零引用令牌处置、映射尺新增、判据口径更新 | **不为 Token 数量少批量删档** |
| 文档状态 | 设计稿真源指定、`SUPERSEDED` 标记 | — |

### 8.2 禁止改变（总原则六条禁令 · 硬边界）

| # | 禁令 | 具体项 |
|---|---|---|
| 1 | **不改功能布局** | 页面节顺序、区域划分、主从 / 单纵 / 全屏的页型归属、双层顶栏合并与否 |
| 2 | **不改业务逻辑** | 入口数量与位置、真过滤 / 排序 / 布局切换 / 密度档、搜索与刷新动作、假菜单项接真动作、屏蔽行为 |
| 3 | **不改路由和状态管理** | 回调签名、`DesktopRoute`、默认参数禁令、`viewModel()` 冻结 17 处、偏好键（撤强调色档按确认口径例外） |
| 4 | **不拆巨型文件** | `VeneraDesktopLargeScreen.kt`、`DesktopSettingsHub.kt`、`DesktopComicDetailScreen.kt`、`DesktopDownloadsPipelineScreen.kt`，任何阶段都不拆 |
| 5 | **不随意改变页面原有特色** | 沉浸头、浮层控制条、主从分栏、Bento 舱、各页节顺序与卡片轨型制；旧壳改名 / 删除亦属结构变更 |
| 6 | **不为 Token 数量少牺牲视觉表现** | 不强制合并语义档位、不批量删零引用令牌、不禁用中间字阶 |
| — | 通用 | `BusinessPorts` 17 族、DAO / 缓存、`DesktopGalleryBatch`、真账读数、`LocalUxPlatformServices`、`LocalDesktopSearchHandoff`、新增平行 `*Tokens` 对象、重复创建已有公共组件 |
| — | 流程 | 未确认规范前批量修改页面；每阶段各跑一次全量测试 |

### 8.3 归属判定（按总原则重分类）

总原则落地后，原先「需主人单独确认」的七项已分三类 —— **被禁的不再列作待办**：

| 项 | 关联 | 判定 |
|---|---|---|
| 46dp 窗条与 56dp 内容顶栏是否合并 | `docs/designs/window-header-redesign-2026-11-05.html:116` | ❌ **禁**（禁令 1：改功能布局），本工程不做；仅统一对齐边与取值 |
| 巨型文件是否拆分 | §6.1 | ❌ **禁**（禁令 4），任何阶段都不拆；已从待确认移出 |
| 跨页型是否强行统一 | §5.3 | ❌ **禁**（禁令 5 + 关键区别），不做 |
| 四个旧壳与死代码是否删除 | §2.7 #5-7 | ❌ **禁**（禁令 5：结构变更），仅登记 |
| 右键菜单是否改名 / 共享化为 `VeneraContextMenuArea` | `context-menu-system-2026-11-05.html:564` | ❌ **禁**（重复创建已有公共组件）；以实存 `DesktopContextMenuArea` 为单源 |
| `SurfaceMaterial.SOLID` 是否改为启用玻璃材质 | `DesktopThemeBridge.kt:80` | ⚠️ **视觉系统内，需确认**：启用会改全窗质感，属视觉表现决策，交主人截图判定 |
| 设计稿唯一真源指定与其余稿废弃 | §6.3 | ⚠️ **需确认**：文档治理，不影响产品 |

**新增一项（由主题口径引入）**：

| 项 | 关联 | 判定 |
|---|---|---|
| 撤 `DesktopAccentMode` 偏好键：**删键** 还是 **保留键固定为默认** | `settings/SettingsGlass.kt:105-126`、§3.1 | ⚠️ **需主人确认**：牵涉状态管理（禁令 3），不擅自定。主人明令「只保留深浅模式」已定方向，落地口径待定 |

---

## 9. 验证节奏与验收基线

按项目工作约定：

- **中途**：改一颗模块跑 `:desktop:compileKotlin`；改 / 加一把尺子只 `--tests` 点名跑那一把。
- **收口**：全部功能域改完后，`:desktop:test` 全量**只跑一次**；跑红再逐条定位、修完复跑那一次。
- **计数**：`node _tools/count_desktop_tests.js`（工作目录 `G:\venera-desktop\desktop`）；当前基线 `{"tot":1608,"f":0,"e":0,"s":0,"classes":231}`。
- **视觉验证**：主人自供截图；不自行启动应用或驱动窗口。
- **编译门**：`:desktop:compileKotlin` 每域完工必须过；判据类红允许拖到全量一起收。

---

## 10. 待确认事项

> **2026-11-06 终盘**：阶段 0 / 1 / 2 / 3 全部执行完毕（含 13 项收尾清单），全量 **1611/0**（+1 圆角看守尺），起窗验证通过（窗口渲染 / 浅色主题 / 窗条 12dp / 卡片浮起 / 无崩溃，内容区按 R18 隔离口径未检视）。剩余全部为可选项：

1. ~~设置域卡片阴影收敛~~ **已办结（方向反转）**：画廊墙卡学设置卡 —— 浅色阴影浮起（4/12dp + `ShadowSpot` 槽），深色档维持描边分层。
2. ~~横滑露边提示~~ ✅ 13 处接 `hScrollEndFade`。
3. ~~筛选行响应式容纳~~ ✅ 改 `FlowRow` 可换行（`heightIn(min=48)`）。
4. ~~焦点环扩展~~ ✅ `DesktopActionButton`（搜索+探索共用）、`DqIconButton`（下载域）补齐；m3 库件自带焦点。
5. ~~Motion 单源~~ ✅ `GalleryTokens.Motion` 删除；切域淡入 / 刷新自转归 `VeneraMotion` 档。
6. ~~菜单行单源~~ ✅ `DesktopMenuRow` 提升（Chrome 收编）；`leadingIcon` 型 m3 正统结构保留为合法变体。
7. ~~圆角看守尺~~ ✅ `DesktopShapeLiteralTest`（档外 11/13/14/15/17/18/19/21 禁入，现存 39 处已归档）。
8. ~~瀑布流浅色阴影~~ ✅ `DesktopWallCard` 外层 shadow（共享件，安卓侧同受益）。
9. ~~图标域盘点~~ ✅ 声明入 §3.3（Outlined 单源 / 18dp 主档）。
10. ~~零引用令牌~~ ✅ 19 符号 + `Motion` 块删除；`sort` 死形参链（Chrome/VLS 五层）+ `SortChips` 死代码清除。
11. ~~「装饰层级」假开关~~ ✅ 整条死链删除（主路径本就不消费）。
12. ~~对齐边五值归一~~ ✅ 全部 12dp（顶栏 20 / 筛选行 22 / 窗条 14）。
13. ~~起窗验证~~ ✅ `:desktop:app` 起窗 + chrome 带截图核验。

**唯一剩余可选项**：`SurfaceMaterial.SOLID` 是否启用真玻璃（当前半透明底已稳定，启用需整套真窗调参）；浅色观感如需微调随时按 `GalleryColors.Light` 档走。

---

> **本阶段结束状态**：分析完成，代码零改动，总原则已写入卷首并贯穿全篇；等待主人确认与下一阶段实现任务。
