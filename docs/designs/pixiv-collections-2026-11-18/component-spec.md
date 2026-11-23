# 珍藏册组件规格 — 2026-11-18

落地目标文件族:`com.venera.desktop.gallery.ui`(pixiv 域),路由挂 `DesktopRoute`。

## 1. CollectionFilterBar(筛选标签行)
- 左端「筛选」胶囊(带漏斗图标,32px 高);右接横向滚动标签带,右端 64px 渐隐遮罩。
- 标签胶囊:高 32px,padding 12px,圆角 full;常态 hairline 描边 + SurfaceLevel2 底;选中 accent 描边 + AccentSoft 底。
- 交互:选中即刷新墙;高频反馈 ≤150ms。

## 2. CollectionCoverCard(rail 卡 / 封面拼贴卡)
- 结构:底托(SurfaceLevel1,圆角 14,内衬 4)→ 拼贴(圆角 10,高 160px):左大图占 62% 高满,右上 1 张、右下 1 张(间隔 4px)。
- 右上浮收藏钮:28px 圆钮,恒深玻璃坞族(SurfaceGlass),心形 16px;未收藏白描线,已收藏 StatusFavorite 填充。恒深玻璃上白图标,深浅同口径。
- 下方:标题 1 行省略(CardTitle);创建者行:24px 头像圆 + 名(TextSecondary,12px)。
- hover:底托描边 OutlineHover + 抬升 -2px + 阴影;按压 0.96。
- 参数:`collection(title, covers[3], creator(name, avatar), liked, likeTarget, onClick, onToggleLike, onOpenCreator)`。

## 3. CollectionWallTile(详情墙格)
- 方形封面(等宽 3 列);左下角画师胶囊:恒深玻璃胶囊,16px 头像 + 画师名 12px,贴边 8px。
- hover:封面 1.03 缩放(150ms)+ 胶囊常显(hover 前常显亦可,移动无 hover——本仓桌面口径:常显)。
- 点击 → DesktopRoute.PixivWork。角标场景:多页作品右下「N页」小徽(仅数值,禁隐喻)。

## 4. CollectionDetailPane(详情页双列)
- 左:标题白卡条(SurfaceLevel1,圆角 14,标题居中)→ 封面墙网格。
- 右栏 380px,纵向:
  1. 描述块:标题(ArticleTitle 缩档 20px)+ 描述(TextSecondary)。
  2. 标签行:#标签胶囊流式换行。
  3. 数据行:心形 + 收藏数、分享钮、更多钮(…)。
  4. 日期行(TextTertiary 12px)。
  5. 创建者卡:头像 40px + 名 + 关注钮(accent 胶囊,高 32);下挂「TA 的珍藏册」2 列迷你拼贴,点击回详情页。
  6. 评论区:SurfaceLevel3 输入框(圆角 full)+ 发送钮;评论列表 = 24px 头像 + 名 + 内容。
- 滚动:整页共用一个滚动容器,右栏随之滚动(与 pixiv 作品详情页口径一致)。

## 5. 账号页「珍藏册」块(DesktopPixivAccountScreen 内)
- 节头「珍藏册」+「新建珍藏册」虚线卡(高 96,加号图标)。
- 个人珍藏册横排:迷你拼贴卡 176px 宽,点击进详情;右上收藏钮不出现(自己的)。

## 6. PixivSearchTargetMenu(pixiv 搜索目标下拉,屏4)
- 位置:pixiv 搜索指挥栏右端(现指挥栏在 `DesktopGallerySearchScreen.kt`,pixiv 引擎激活时)。
- 收起态:胶囊(高 32,padding 13,SurfaceLevel2 底 + hairline),文案 = 当前目标(插画·漫画 / 小说 / 珍藏册 / 用户)+ 下箭头 13px。
- 展开浮层:SurfaceLevel1 卡,圆角 12,padding 5,阴影 hover 档;选项行高 34、圆角 8(同心 12-5)、左勾槽 15px。
- 选中态:AccentSoft 底 + accent 文字 + accent 勾;hover SurfaceLevel2 底;切换即刷新结果轴,反馈 ≤150ms。
- 结果区映射:插画·漫画 → 现有作品墙;珍藏册 → CollectionCoverCard 卡带(宽 240);用户 → 现有用户结果;小说 → 官方同类目标,本仓暂无数据源,稿上画位,实现阶段单独拍板。

## 7. 路由接线(实现阶段)
- 新增 `DesktopRoute.PixivCollections(发现页)` 与 `DesktopRoute.PixivCollectionDetail(id)`;发现页入口 = pixiv 域页签;账号页入口直接压详情路由。
- 返回把手:沿用 VeneraBackHandle。
