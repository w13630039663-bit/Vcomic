# Pixiv 站点首页 Bento 布局 · 组件规格说明书 (Component Spec)

> 依据：图二架构线框图与 `better-ui` 准则。
> 约束：零过度拟人比喻，无损保留所有既有交互接口，主标题 ≤ 6 字，副标题 ≤ 15 字。

---

## 1. 顶部左看板：`PixivBentoFeaturedCard` (精选作品 / Pixivision)

### 1.1 组件解构
- **外层容器**：Bento 外壳（底色 `--surface-bento`，描边 1dp `--border-subtle`，圆角 16dp，内边距 14dp）。
- **容器头部**：
  - 标题文本：「精选作品 / Pixivision」（16sp Bold）。
  - 右侧操作项：小箭头或「更多」入口，点击跳转 Pixivision 完整归档。
- **内部上部分：Hero 专题主视觉大卡 (`PixivBentoHeroCard`)**：
  - 尺寸：高度固定 280dp，宽度 100% 撑满。
  - 背景：第一篇精选特辑的大尺寸头图（`AsyncImage` 铺满，`ContentScale.Crop`）。
  - 遮罩：底部覆盖渐变黑色暗层（高度约 100dp），防止浅色背景导致文字失真。
  - 内容：左下角展示特辑分类徽标（如「插画」/「特辑」）+ 主题大标题（18sp Bold 白字，最大 2 行）。
- **内部下部分：双副特辑卡 (`PixivBentoSubCardRow`)**：
  - 排布：`Row(horizontalArrangement = Arrangement.spacedBy(10.dp))` 均分两列。
  - 单卡结构：高度 130dp，宽自适应 50%，内圆角 10dp。展示第 2、第 3 篇 Pixivision 特辑封面与底部标题（13sp Medium，单行省略）。

### 1.2 交互与状态
| 状态 | 视觉表现 | 动效规格 |
|---|---|---|
| **默认态** | 1dp 描边 `rgba(255,255,255,0.08)`，无位移 | - |
| **悬停态** | 卡片 Y 轴上浮 `-2dp`，描边提亮至 `rgba(255,255,255,0.18)` | 150ms `ease-out` |
| **按压态** | 整体缩放至 `0.96` | 150ms `cubic-bezier(0.2, 0, 0, 1)` |
| **点击出口** | 触发 `onOpenPixivisionArticle(article)` 打开对应二级页 | 即时响应 |

---

## 2. 顶部右上看板：`PixivBentoCreatorsCard` (推荐画师)

### 2.1 组件解构
- **外层容器**：Bento 外壳（高度 264dp，圆角 16dp，内留白 14dp）。
- **头部**：标题「推荐画师」（16sp Bold）+ 右侧换一批/刷新微型图标。
- **内容区**：纵向展示 3 组推荐创作者行 (`PixivBentoCreatorRow`)，行间距 10dp。
- **单行解构 (`PixivBentoCreatorRow`)**：
  - 左侧：画师圆形/圆角方块头像（尺寸 44×44dp，圆角 8dp，带 1dp 外环）。
  - 中间：双行文本列（`Column`）：
    - 昵称（14sp SemiBold，单行截断）。
    - 简介或关注者统计（11sp 次要灰，如 `@handle · 12.8万粉丝`）。
  - 右侧：轻量关注胶囊按钮（`PillButton`，高 28dp，内距水平 12dp）：
    - 未关注：高亮蓝底白字「关注」。
    - 已关注：透明深灰底 + 细边框「已关注」。

### 2.2 交互与状态
- 鼠标移入整行：行底色微高亮 `rgba(255,255,255,0.04)`。
- 点击头像/昵称：触发 `onOpenUser(user)` 导航到画师作品主页。
- 点击关注按钮：触发 `onFollowUser(user)`，即时乐观更新选中态，按钮缩放 0.96 反馈。

---

## 3. 顶部右下看板：`PixivBentoTagsCard` (热门标签)

### 3.1 组件解构
- **外层容器**：Bento 外壳（高度 186dp，圆角 16dp，内留白 14dp）。
- **头部**：标题「热门标签」（16sp Bold）。
- **内容区**：`FlowRow` 自动折行排列标签芯片（`PillTagChip`），展示 6~8 枚当前全站高频热搜标签。
- **单个标签芯片 (`PillTagChip`)**：
  - 尺寸：高度 32dp，圆角 16dp（完全胶囊形）。
  - 结构：左侧浅绿/浅蓝微圆点徽标 + 标签文本（12sp Medium，如 `#女の子`、`#東方Project`）。
  - 背景：`rgba(255,255,255,0.06)`，1dp 描边 `rgba(255,255,255,0.08)`。

### 3.2 交互与状态
- 悬停：背景加深至 `rgba(255,255,255,0.12)`，描边高亮。
- 按压：缩放至 `0.96`。
- 点击：触发 `onOpenTag(tag.name)` 交付全局搜索，直接检索该标签作品。

---

## 4. 中部通栏区：`PixivFollowingRowCard` (关注新作 / 榜单)

### 4.1 组件解构
- **外层容器**：横跨 100% 视口宽度的独立卡片外壳（圆角 16dp，底色 `--surface-bento`，Padding 14dp）。
- **头部控制栏**：
  - 左侧标题：「关注新作」（16sp Bold）。
  - 右侧分段器：支持在「关注新作」与「全站日榜」之间单键切换，无损承载原图一中的榜单数据。
- **卡片栅格行**：
  - 单行固定显示 5 张等宽 16:9 比例作品卡（`DesktopGalleryCard` 紧凑变体）。
  - 单卡包含：16:9 封面 + 底部悬浮画师微头像 + 作品标题。

---

## 5. 底部瀑布流区：`PixivRecommendWaterfallSection` (为你推荐)

### 5.1 组件解构
- **外层容器**：无多余外框，融入页面底色，保证作品最大画幅张力。
- **头部**：标题「为你推荐」（18sp Bold）。
- **多列瀑布流 (`VerticalStaggeredGrid`)**：
  - 依据窗口宽度自适应 4~6 列。
  - 卡片高度根据插画真实纵横比（竖图 3:4、横图 16:9、方图 1:1）自然舒展，还原图二参差错落的高质感排布。
  - 底部中心：圆环向下指示按钮（`Icon(ArrowDownward)`，40dp 圆形），提示更多内容或点击平滑滚屏。

---

## 6. Compose Multiplatform 落地映射表

| UI 组件名 | 现有源码位置 / 推荐新构文件 | 依赖的共享模型 / 状态 |
|---|---|---|
| `PixivBentoHeroCard` | `desktop/src/main/kotlin/com/venera/desktop/pixiv/DesktopPixivBento.kt` | `DesktopPixivisionArticle` |
| `PixivBentoCreatorsCard` | `desktop/src/main/kotlin/com/venera/desktop/pixiv/DesktopPixivBento.kt` | `PixivUser`, `followedUserIds` |
| `PixivBentoTagsCard` | `desktop/src/main/kotlin/com/venera/desktop/pixiv/DesktopPixivBento.kt` | `DesktopHotTag` |
| `PixivFollowingSection` | `desktop/src/main/kotlin/com/venera/desktop/pixiv/DesktopPixivBento.kt` | `followingCards: List<DesktopWallCardModel>` |
| 整体看板装配 | `DesktopGalleryHomeRows.kt` 中的 `GalleryHomeDomain.PIXIV` 分支 | 现有的参数表 100% 直连装配 |
