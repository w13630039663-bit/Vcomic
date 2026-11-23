# 画廊搜索 UI 重做 — 设计分析

规格名：`gallery-search-redesign-2026-11-21` · 日期：2026-11-21  
标注口径：**【确定】= 规范文档 / 源码直接可读**；**【规格值】= 本设计稿新设定参数**；**【估算】= 截图推算**。

---

## 1. 页面结构

本规格覆盖画廊搜索的两个发生地，统一为同一套桌面级实色表面与层级结构：

- **场景 A：首页顶栏悬浮搜索（`DesktopGalleryTopSearch`）**
  - 根锚点：`Box`（顶栏中段权重槽，对齐 58dp/60dp 透明顶栏）
    - 岛屿 A（搜索输入槽）：`Row`（左放大镜图标 + 输入区 + 可选清空按钮 + Booru 范围胶囊 `聚合 ⌄`）
    - 岛屿 B（智能下拉浮层 `Popup`）：输入框下缘 + 8dp 处浮出
      - 检索源范围区（仅 Booru）：标题行 + 自适应流式换行芯片组（`FlowRow`）
      - 内容分级开关行（仅 Booru）：34×20 滑块开关 + 「全年龄」+ 简短说明
      - 分隔线：1dp `Divider`
      - 历史 / 实时联想列表区：标题行（含「清除」操作）+ 结构化行列表（历史带时间图标/来源标签/单删叉；联想带分类色点/译名对照/右对齐作品数/站点标记）

- **场景 B & C：独立画廊搜索结果页（`DesktopGallerySearchScreen`）**
  - 根容器：`Column(fillMaxSize)`，`Background` 纯色底
    - 岛屿 A（透明顶部指挥栏）：无独立灰底板，仅底边画 1dp `Divider`
      - 第一行主栏：域分段器（图库 ↔ Pixiv 专属） + 主搜索框（内嵌同款下拉浮层） + Pixiv 目标下拉菜单 + 主「搜索」按钮 + 「高级筛选」抽屉开关
      - 第二行收敛快捷条：高频项平铺 + 竖向分隔线 + Pop-up 下拉聚合芯片 + Booru 「全年龄」快速开关（无超宽横滚、无右侧渐隐断层）
    - 岛屿 B（主体工作区 `Row`）：
      - 左侧高级筛选抽屉（284dp，可平滑展开/收起）：右缘 1dp `Divider` + 若干张 `SurfaceLevel1` 分组卡片（排行周期 / 分级过滤 / 标签匹配 / 过滤属性 / 时间区间）
      - 右侧结果舞台：顶部元信息栏 + 结果流（瀑布流作品墙 / 双列画师卡片 / 珍藏册横带 / 四态空白与重试引导）+ 回顶悬浮钮

---

## 2. 区域尺寸与比例

| 区域 | 尺寸 / 占比 | 标注 |
|---|---|---|
| 首页顶栏搜索槽宽 | min 220dp / max 480dp，高 34dp | 规格值（原 160–480dp / 32dp【确定 · `DesktopGalleryTopSearch.kt:226-228`】） |
| 首页下拉浮层宽 | 随搜索槽宽，min 380dp / max 560dp | 规格值（原 min 320dp【确定 · `DesktopGalleryTopSearch.kt:354`】） |
| 搜索页顶区左右边距 | 20dp（对齐全站 `screenHorizontal`） | 确定 · `README.md:200` |
| 搜索页第一行主控件高 | 统一 36dp（分段器/输入框/目标钮/搜索钮/筛选钮） | 规格值 |
| 搜索页第二行快捷条高 | 30dp ~ 32dp（流式/紧凑单行） | 规格值 |
| 独立搜索页联想浮层宽 | 随搜索框宽，min 400dp / max 560dp | 规格值 |
| 左侧高级筛选抽屉宽 | 284dp（收起 0dp） | 规格值（原 280dp【确定 · `DesktopGallerySearchScreen.kt:370`】） |
| 右侧结果墙列数 | 随可用宽度自适应 4~7 列，间距 `wallGap` | 确定 · `DesktopGallerySearchScreen.kt:214-219` |
| 画师结果卡片网格 | 宽窗双列 / 窄窗单列，单卡 padding 12×14dp | 规格值 |

---

## 3. 视觉层次

自上而下严格遵循 `VDDS` 四层表面模型：

1. **页面底（`Background`）**：深色 `#0A0D12` / 浅色 `#F0F2F6`。顶部指挥栏与高级筛选抽屉底色直接透明透出页面底，靠空间间距与 1dp `Divider` 分界，撤除原先脏灰半透明块。
2. **第一抬升面（`SurfaceLevel1`）**：深色 `#12161F` / 浅色 `#FFFFFF`。承载高级筛选分组卡片、结果作品卡、画师行卡片、输入框聚焦态、分段器选中滑块、以及下拉浮层面板。
   - **深色态**：零重投影，靠 `1dp OutlineHairline`（浮层使用 `OutlineHover`）界定轮廓；
   - **浅色态**：卡片使用轻阴影 `ShadowSpot(#101828)` + `1dp OutlineHairline`，浮层使用 `vShadow(16.dp)` 抬起。
3. **第二抬升面（`SurfaceLevel2`）**：深色 `#161B26` / 浅色 `#E8ECF2`。承载默认态搜索输入框槽、分段器轨道、二级按钮（高级筛选未开态、目标下拉、Pop-up 芯片）、日期输入框。
4. **第三抬升面（`SurfaceLevel3`）**：深色 `#1B2130` / 浅色 `#DBE2EC`。承载控件悬停底、输入框内嵌范围胶囊底。
5. **图上边缘处理**：作品缩略图与画师头像在浅色模式下加 1dp 内嵌框 `ImageHairline`（`#1417202E`），深色下透明。

---

## 4. 逐元素设计参数

| 元素 | 颜色 | 字阶 | 圆角 | 其他 | 标注 |
|---|---|---|---|---|---|
| 顶栏输入槽 | 默认 `SurfaceLevel2` / 聚焦 `SurfaceLevel1` + 2dp `AccentPrimary` 描边 | 输入 13sp / 占位 `TextTertiary` | 8dp | 高 34dp，左图标 15dp，内嵌范围胶囊 4dp | 规格值 |
| 搜索页主输入槽 | 默认 `SurfaceLevel2` / 聚焦 `SurfaceLevel1` + 2dp `AccentPrimary` 描边 | 输入 13sp / 占位 `TextTertiary` | 10dp | 高 36dp，1dp `OutlineHairline` | 规格值 |
| 域分段器 | 轨 `SurfaceLevel2` / 选中滑块 `SurfaceLevel1` + `AccentPrimary` 字色 | 12.5sp SemiBold(选中)/Normal | 外 10dp / 滑块 7dp | 高 36dp，内距 3dp，含站徽 6dp 圆点 | 规格值 |
| 主「搜索」按钮 | 启用 `AccentPrimary` 实底白字 / 禁用 `SurfaceLevel2` + `TextTertiary` | 13sp SemiBold | 10dp | 高 36dp，horizontal padding 18dp | 规格值 |
| 高级筛选开关 | 默认 `SurfaceLevel2` / 展开 `AccentSoft` + `OutlineSelected` | 12.5sp Medium/SemiBold | 10dp | 高 36dp，激活数徽章 `Pill` 18dp | 规格值 |
| 快捷筛选芯片 | 未选透明底 + `OutlineHairline` / 选中 `AccentSoft` + `OutlineSelected` | 12sp Medium/SemiBold | 6dp | 高 28dp，padding horizontal 10dp | 规格值 |
| 下拉浮层面板 | `SurfaceLevel1` + 1dp 细描边 + `vShadow` | 节标题 11sp / 行文 13sp / 计数 11sp | 外 12dp / 内项 6dp | 内距 8dp，行高 34dp | 规格值 |
| 筛选分组卡片 | `SurfaceLevel1` + 1dp `OutlineHairline` | 标题 12.5sp SemiBold / 子标签 11.5sp | 12dp | 内距 12dp，左 3×12dp `AccentPrimary` 竖条 | 规格值 |

---

## 5. 响应式视口规则

| 视口档 | 断点 | 重排规则 |
|---|---|---|
| 宽档 | ≥ 1180dp | 指挥栏单行完整呈现；快捷筛选条不换行且零溢出；结果墙 5~7 列，画师卡片双列排布 |
| 中档 | 860dp ~ 1179dp | 快捷筛选条内项使用 `FlowRow` 自动换行为紧凑两行，不出现横向滚动条与右侧渐隐遮罩；结果墙 4 列 |
| 窄档 | < 860dp | 指挥栏主行可收缩搜索框至 min 180dp，次级文字自适应缩略；高级筛选抽屉打开时结果墙自动让位至 2~3 列，画师列表切为单列 |
