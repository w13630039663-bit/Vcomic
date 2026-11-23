# Pixiv 作品详情页空间画廊 · 组件规格说明书 (多图沉浸翻页版)

> 依据：`Spatial Gallery` 最新多图沉浸翻页版原型、`better-ui` 准则与 AGENTS.md 全仓铁律。
> 约束：零拟人修辞，无损保留原有业务逻辑与数据通道，主标题 ≤ 6 字，副标题 ≤ 15 字。

---

## 1. 主视觉多图沉浸轮播卡 (`SpatialMainImageCard`)

### 1.1 组件内部结构
- **外层画廊相框**：
  - 圆角 24dp，底色 `rgba(25, 25, 30, 0.65)`，内边距 `16dp`，景深系数 `depth = 5`。
  - 监听悬停状态 `isHovered`，驱动翻页按钮和缩略图导轨的淡入与上浮。
- **轮播视口 (`SpatialCarouselViewport`)**：
  - 双层结构：
    1. **底层防留白模糊背板 (`SlideBg`)**：当前页图片的放大高斯模糊底板（`blur 30dp`，明度 0.4），彻底消除横竖图在不同长宽比下的空白边缘。
    2. **顶层完整主图 (`MainImg`)**：采用 `ContentScale.Fit`，绝不裁剪画面；外附 `0 20px 40px rgba(0,0,0,0.6)` 高质感画框阴影与圆角 8dp。悬停微缩放 `1.02x`。
    3. 点击大图：触发 `onOpenPage(page)` 打开看图播放器（保留全屏/放大镜等完整播放能力）。
- **右上角页码指示胶囊 (`SpatialPageIndicator`)**：
  - 半透明深色磨砂底（`blur 10dp`），显示 `current + 1 / pages.size`。
- **悬浮翻页导航按钮 (`SpatialNavButtons`)**：
  - 左右两侧各一枚 50×50dp 圆形悬浮按钮，悬停主图卡时浮现（`opacity: 0 -> 1`）。
  - 点击左箭头触发 `goToPrev()`，右箭头触发 `goToNext()`，支持循环或边界保护。
  - 键盘监听：左右方向键联动。
- **底部缩略图导航导轨 (`SpatialThumbNav`)**：
  - 悬停时从底部 `20dp` 弹性浮动至 `30dp` 且透明度变为 1。
  - 水平排列各页 40×40dp 缩略图（圆角 8dp）。
  - 当前激活页缩略图高亮 2dp 白框并缩放 `1.1x`。点击直接切换到该页。

---

## 2. 空间 3D 动力学与其余四岛屿组件
保持与前版一致的物理动力学与业务数据接口：
- **画师卡 (`SpatialAuthorCard`, depth 15)**：头像 + 昵称 + 关注按钮 + 浏览/收藏/日期统计。
- **操作卡 (`SpatialActionsCard`, depth 12)**：标题 + 发布时间 + 标签流 + 下载/收藏/复制按钮。
- **评论卡 (`SpatialCommentsCard`, depth 8)**：跨列水平评论卡片流。
- **相关作品卡 (`SpatialRelatedCard`, depth 20)**：3×2 微网格缩略图。
- **全局环境大背景 (`SpatialAmbientBg`)**：全屏 100dp 高斯模糊，切页时联动切换背景图，15s 尺度缓慢呼吸。
