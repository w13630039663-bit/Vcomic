# 画廊关键体验与视觉组件规格书 (Component Specification)

> **设计基准**：`docs/designs/gallery-booru-desktop-2026-11-05.html`、`docs/designs/gallery-home-desktop-v3-2026-11-05.html`  
> **归档位置**：`docs/designs/gallery-detail-tag-spec-2026-11-05/component-spec.md`  

---

## 一、组件拆解与交互实现规格

### 1. 人气标签卡片与搜索契约 (HotTagCard & Search Contract)
- **封面动态注入**：
  若官方趋势接口未返回代表图或处于匿名/冷启动状态，不再回退到死链，而是从当轮已加载的高清作品库提取真实封面（`card.url`）按序填充，确保每一张长图卡都有精美的真插画封面；
- **点击交互路由**：
  在 Pixiv 域点击热门标签时，解析纯正标签名（剔除括号内的人工译名），并锁定站点为 `listOf(GallerySite.PIXIV)`，直接跳转 Pixiv 标签搜索；在 Booru 域点击则保持 Booru 标签交接。

### 2. 推荐画师主页跳转与关注闭环 (RecommendedUserCard)
- **卡片整体点击**：
  必须直接路由至原生画师详情屏：
  ```kotlin
  DesktopRoute.PixivArtist(userId = user.id, name = user.name, from = DesktopRoute.GalleryHome)
  ```
- **关注按钮独立手势**：
  - 关注按钮使用独立的 `Box` 与 `clickable`，阻止点击向外扩散导致误触发画师主页跳转；
  - 点击时优先执行本地乐观更新（即时将状态置为“已关注”，视觉背景变暗色微边框），并在后台异步与站方 API 通讯，若通讯失败则回滚并 Toast 提示。

### 3. pixivision 文章详情单元重构 (Pixivision Work Unit)
- **告别横向压扁**：
  - 彻底移除 `.aspectRatio(16f / 10f)` 强行裁切；
  - 容器采用 `fillMaxWidth().heightIn(min = 320.dp, max = 560.dp)`，内部 `VeneraCover` 启用自然比例保持（`preserveAspectRatio = true`，居中填充）；
- **浮动操作坞**：
  - 图片右上角/右下角放置浮动轻量动作条：`⤢ 看大图`、`♡ 收藏`、`↗ 站方打开`；
- **底部画师信息条**：
  - 高度 `52dp`，圆形头像 36dp，画师姓名，右侧保留 `🔍 搜画师` 与 `站方作品` 两个真动作。

### 4. 图库站 4 列标签维度舱 (Tag Dimension Deck)
- **整体架构**：
  容器为标准 Bento 卡片（深色半透明背景，圆角 12dp，极细边框）；
- **4 列自适应网格**：
  采用 `Row` 嵌套 4 组等宽列（每列 `Modifier.weight(1f)`），分为：
  1. `画师`（指示圆点 `#E26D8A`，词数角标）
  2. `角色`（指示圆点 `#4D9FFF`，词数角标）
  3. `系列`（指示圆点 `#3ECF8E`，词数角标）
  4. `通用`（指示圆点 `#E8B04B`，词数角标）
- **列内标签流 (Tag Flow)**：
  每列内部使用 `FlowRow`（垂直间隙 6dp，水平间隙 5dp），排列微型胶囊 `tw-chip`：
  中文译名 + `small(#原词)`，点击直接带词检索！
