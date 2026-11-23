# 画廊关键体验与视觉逆向分析报告 (Design Analysis)

> **设计基准**：`docs/designs/gallery-booru-desktop-2026-11-05.html`、`docs/designs/gallery-home-desktop-v3-2026-11-05.html`  
> **涉及功能**：人气标签链路、推荐画师路由与关注、pixivision 详情排版、图库站 4 列标签维度舱  
> **分析日期**：2026-11-05  

---

## 一、体验痛点根因深度逆向分析

### 1. 人气标签图片不显示且点击路由错误
- **图片加载空白**：`DesktopPixivTrending` 内部的 `fallbackTags` 使用了占位的虚假 URL（`fallback_1.jpg` 导致 404）。当网络请求未完成或在匿名状态下时，图片全部失帧显示黑块；
  - *修复决策*：将当前主墙/推荐流中真实到货的高清插画封面（`wallPage.cards.map { it.url }`）动态注入热门标签作为高质量真实兜底图。
- **点击路由错误**：当前 `onSearchTag` 默认交给了全站搜索（`GallerySite.entries`），导致 Pixiv 的标签跑去 Booru 站搜索而没有结果；
  - *修复决策*：在 Pixiv 域时，提取纯正标签名（剔除括号译名）并交接给 `listOf(GallerySite.PIXIV)`，精准直达 Pixiv 官方标签搜索结果。

### 2. 推荐画师点击与关注按钮失效
- **点击跳转错误**：此前代码将 `onSearchUser` 错误映射为了名称搜索 `searchHandoff(...)`；
  - *修复决策*：必须映射到原生画师详情屏 `DesktopRoute.PixivArtist(userId = user.id, name = user.name, from = DesktopRoute.GalleryHome)`，完整展现画师主页与作品集。
- **关注按钮无法点击**：整张卡片外层带有 `clickable(onClick = onOpen)`，与关注按钮的手势产生冲突，且缺少即时乐观更新（网络等待期无任何反馈）；
  - *修复决策*：关注按钮内部使用独立的交互消费，点击即时触发本地状态乐观翻转（变蓝/变灰），并给出标准 Toast 提示。

### 3. pixivision 文章大图严重压扁裁切
- **严重视觉畸变**：当前代码为大图强制施加了 `.aspectRatio(16f / 10f)` 且 `preserveAspectRatio = false`。由于绝大多数 Pixiv 精选插画为人像立绘或纵向构图，16:10 强行中心裁切将画面顶部头部与底部躯干直接截断，整张图被压成一条狭窄的水平切片；
  - *修复决策*：必须改为**保留画面自然宽高比**（`preserveAspectRatio = true`），图片容器施加自适应高度钳制 `heightIn(min = 320.dp, max = 560.dp)`，图上浮动悬停操作栏（看大图 / 收藏 / 站方打开）。

### 4. 图库站标签维度舱单行简陋空旷
- **结构缺陷**：当前代码仅使用单行横排流动，当词典未能全部分组时所有词堆积在“通用”一行，右侧留下上千像素的死寂大黑块；
  - *修复决策*：严格对齐设计稿 `gallery-booru-desktop-2026-11-05.html` 的 **4 列网格架构 (`.tag-grid: 1fr 1fr 1fr 1fr`)**：
    1. 画师列（粉色圆点 `#E26D8A` + 读数 + 流式标签）
    2. 角色列（蓝色圆点 `#4D9FFF` + 读数 + 流式标签）
    3. 系列列（绿色圆点 `#3ECF8E` + 读数 + 流式标签）
    4. 通用列（橙色圆点 `#E8B04B` + 读数 + 流式标签）
    即使某列词数较少，也呈现四列平衡对称的高级 Bento 仪表感。
