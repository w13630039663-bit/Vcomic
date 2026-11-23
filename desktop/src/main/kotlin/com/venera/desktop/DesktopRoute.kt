package com.venera.desktop

import com.venera.engineprobe.EngineSession
import com.venera.desktop.gallery.ui.DesktopWallCardModel

/**
 * 桌面二级页路由。
 *
 * ## 为什么不用导航库
 *
 * 这一批只有两层（详情 → 阅读器），真正要的行为是"点进去、按返回回来"。
 * 导航库会附赠一堆此刻用不上的约定（深链、进程恢复、回栈序列化），
 * 而引进来之后每一处都要给出对应的实现才算数 —— 那正是本仓反复记过的
 * "接了一堆空壳"。所以这里就是一个 sealed 接口 + 一个 `remember`。
 *
 * ## 为什么 `Reader` 里要带 `info` 与 `episode`
 *
 * 阅读器需要两样东西才能取页：这一本的 `comicId`（要从 `info` 里拿章节表之外的元信息）
 * 和这一话的 `Episode`。把它们塞进路由，切屏回来时不必重新问一遍源站。
 */
internal sealed interface DesktopRoute {

    /** 停在底栏某一格上（`currentTab` 决定是哪一格）。 */
    data object Tab : DesktopRoute

    /**
     * 我的 pixiv（主人 goal「pixiv 所有界面和登录也要做好」）。
     *
     * 对应安卓 `PixivAccountScreen`：动态 / 收藏 / 已关注 三段主档 + 动态段的
     * 我发布的 / 关注动态 / 账号管理 副档。做成不带参的 object：登录态与取数都吃
     * 装配点已接线的 `DesktopPixivAccount`（`GalleryPorts.pixivAccount`），
     * 这一屏不需要外部数据就能进得去（没登录时它自己念闸口那一档）。
     */
    data object PixivAccount : DesktopRoute

    /**
     * pixiv 登录那一屏（goal「pixiv 登录也要做好」）。
     *
     * PKCE 授权链接 → 系统浏览器 → 回跳链接贴回 → `completeLogin`；
     * 备用路直接贴 token JSON / refresh token（`signInWithPasted`）。
     * 三条路的判据全在共享 `PixivCredentialState`，屏只收表单。
     */
    data object PixivLogin : DesktopRoute

    /**
     * pixiv 画师页。
     *
     * 对应安卓 `PixivArtistScreen`（头图 + 头像 + 名 + 关注药丸 + 统计 + 作品/漫画/收藏 三段 + 墙）。
     * `name` 是顶栏要念的名字 —— 带着走而不在屏里先问一次站方（与 `SourceEdit.title` 同一条理由：
     * 读到之前那一行也得有名可念）。`userId` 是取数主键。
     *
     * `from`（主人报「二三级页返回直接退回画廊主页」）：进入时的来路 ——
     * 从账号页进 = 账号页，从作品页的画师面板进 = 那一页作品。返回弹回 `from`，
     * 没带来路（旧调用）才落 `PixivAccount`。
     */
    data class PixivArtist(val userId: Long, val name: String, val from: DesktopRoute? = null) : DesktopRoute

    /**
     * pixiv 作品页。
     *
     * 对应安卓 `PixivWorkScreen`（竖排全部页 + 作者行 + 标签墙 + 统计详情）。
     * `title` / `coverUrl` 带着走同上一条理由；详情腿（分页、原题、标签）由屏的 effect 现问。
     *
     * `from`（返回口径）：进入时的来路（主墙 / 画师页 / 账号页 / 搜索 / 收藏），
     * 返回弹回它而不是固定一层 —— 顶栏那枚「返回」因此只指向真实上一层。
     *
     * `prevWork` / `nextWork`：来路那一列里紧挨着的两篇（账本见 `DesktopPixivWorkTrail`）。
     * 只带进页必需的几格；null = 这一列里没有前/后一篇（或入口压根没有列）⇒ 那一侧箭头不摆。
     */
    data class PixivWork(
        val illustId: Long,
        val title: String,
        val coverUrl: String,
        val from: DesktopRoute? = null,
        val prevWork: DesktopWorkNeighbor? = null,
        val nextWork: DesktopWorkNeighbor? = null,
    ) : DesktopRoute

    /**
     * 作品页左右箭头要跳的那一篇。
     *
     * 不带整颗 [com.venera.compose.gallery.data.GalleryPost]：路由只用来判断"往哪走 + 顶栏念什么"，
     * 正文 / 分页 / 标签由作品页进场现问 —— 路由里塞一份旧账就会和站方现状打架。
     */
    data class DesktopWorkNeighbor(
        val illustId: Long,
        val title: String,
        val coverUrl: String,
    )

    /**
     * 源管理。
     *
     * 为什么不放进底栏：安卓底栏也只有 5 格（首页/收藏/搜索/画廊/探索），
     * 源管理在安卓是**设置里的一项**。桌面这一批还没有设置屏，所以先做成路由，
     * 由探索屏顶栏那个入口进 —— 不硬塞进底栏去凑数。
     */
    data object SourceManage : DesktopRoute

    /**
     * 下载队列。
     *
     * 与源管理同一族：它在安卓不是底栏那一格（安卓把它藏在漫画域首页的一行里），
     * 桌面这批没有那一层域首页，所以做成二级页路由，由探索屏顶栏的入口进。
     *
     * 为什么这一屏**必须**进得来：第一百零五批起它只读不驱动的那一格已经改过（泵常驻），
     * 而用户要看得见"排了几笔、跑到第几页、哪一笔失败了"才停得下手。
     * 从前它只有代码没有门 —— 等于那一屏没做。
     */
    data object Queue : DesktopRoute

    /**
     * 本机已下载。
     *
     * 与 `Queue` 同一族、同一个入口位置（探索屏顶栏）：安卓把它藏在漫画域首页的一行里，
     * 桌面这批没有那一层域首页，所以做成二级页路由。
     *
     * 为什么这一档**必须**存在：读侧（数盘上那一棵树）、删除那两步（武装 → 再按才真删 + 回读盘定档）、
     * 补齐那一跳（只入队而不动盘）、以及"什么时候该再读一次盘"那一枚键，四件事从批 97 起就都有判据盯着，
     * 而当前这条渲染路里**没有一屏**把它们摆出来 —— 旧的那一屏属于已被拆掉的 Fluent 平行线。
     * 于是"盘上有什么 / 缺哪几页 / 怎么补 / 怎么删"用户今天看不见也改不了。这一批把门开上。
     */
    data object Downloads : DesktopRoute

    /**
     * 本地漫画那一域（A 档最后一颗纯功能缺口）。
     *
     * 与 `Downloads` / `Queue` / `History` 同一族：安卓把它藏在一整屏 `feature/LocalComicScreen`（由
     * `LocalComicManager.getLocalComics()` 喂），桌面这批没有那一层，所以做成二级页路由、由探索屏顶栏入口进。
     *
     * 为什么这一档**必须**存在而 `Downloads` 不够：`Downloads` 那颗屏吃的是**话级**读侧
     * （`DesktopComicDownloadedList.kt` 要求每话带 `chapter.json` 清单），而本地那一棵树里
     * 有整批「只有图、没有清单」的本子（安卓同机攒的书、CBZ 导进来的裸目录）—— 那些在 `Downloads`
     * 一颗都不认（只进 skipped 计数）。本级题名（这本叫什么）也只在 `Downloads` 念**号**而无题名。
     * 这一批开的是**另一棵树**（`DesktopLocalComicLibrary`，吃对岸那颗第二把尺 `isPageImageFile`），
     * 与 `Downloads` 那颗话级树各数各的、不混。
     */
    data object LocalComics : DesktopRoute

    /**
     * 「下载与本地」合并页（「合并侧边栏功能（下载队列和本机已下载和本地漫画），
     * 统一 ui 风格」）。
     *
     * 侧栏三项合成一项，页内用选块切三档（下载队列 / 本机已下载 / 本地漫画），三档内容直接复用
     * 原屏（`embedded = true` 不自画页壳），不重写第二份列表/删除/导入逻辑。
     * 旧的三条路由**保留**：探索屏溢出菜单与首页书脊带仍可直达对应一档，
     * 收藏/阅读器返回等既有判据也照旧。
     */
    data object Files : DesktopRoute

    /**
     * 导入本子的阅读器(tool-suite 稿⑤)。
     *
     * 归档自含页图,**不依赖引擎**:页表由壳按盘上 `.jpg` 页直接数出来,每页带 `localDir` 让
     * `DesktopComicPageFetcher` 走盘上那一路(的既有形状)。
     *
     * 为什么不复用 `Reader`:那一格吃 `EngineSession.Episode`(页键要问源站),导入的本子没有
     * 源站那一跳;硬塞一个假 episode 就是造数据。`rootPath` 是盘上话目录(`…/imported/<题名>/0001_全一话`)。
     */
    data class LocalComicReader(
        val title: String,
        val rootPath: String,
        /** 起点页(0 起)。从"阅读"把手进恒 0;续读等历史那一族接上后再带。 */
        val startPage: Int,
        /** 返回目标（默认退回下载与本地合并页）。 */
        val from: DesktopRoute? = null,
    ) : DesktopRoute

    /**
     * 画廊帖子的浏览历史（A 档最后一颗）。
     *
     * 与 `Downloads` / `Queue` / `History` / `LocalComics` 同一族：安卓把它藏在 `GalleryScreen`
     * 顶栏那一枚 🕘，桌面这批没有那一层，所以做成二级页路由、由**活画廊墙顶栏**的入口进。
     *
     * 为什么这一档**必须**存在而 `History` 不够：`History` 是**漫画阅读历史**（那张 `comic_history` 表），
     * 本档是**画廊帖子的浏览历史**（`gallery_browse_history.json` 那份档），两端各写各的，
     * 本仓记过把它们混成一件事的假话（§14 那一笔）。把那份档与那颗存储件立起来了，
     * 而活渲染路里**没有一屏**把它摆出来 —— `DesktopGalleryBrowseHistoryPane.kt` 是 Fluent 遗骸
     * （`@Composable` 是 0、带 `io.github.composefluent` import），`VeneraDesktopApp.kt` 里那一整排
     * 「画廊域首页行」也是同一族。这一批把门开上，并把 §130 那枚看图屏**复用**成「回原卡」的落点。
     */
    data object GalleryBrowseHistory : DesktopRoute

    /**
     * 画廊站点收藏（桌面端二级页）。
     *
     * 与 [GalleryBrowseHistory] 同族：画廊的东西都在画廊域里，收藏页只留漫画那两路。
     * 数据与守卫沿用既有那颗收藏墙，本路由只负责承载与分段条。
     */
    data object GalleryFavorites : DesktopRoute

    /**
     * 关注画师收件箱（作品流 + 状态分级）。
     */
    data object FollowedArtists : DesktopRoute

    /**
     * 阅读历史。
     *
     * 与 `Queue` / `Downloads` 同一族：安卓把它藏在漫画域首页的一行里（`feature/HistoryScreen.kt`
     * 是独立一屏，由首页那枚分区头点进去），桌面这批没有那一层域首页，所以做成二级页路由，
     * 由探索屏顶栏的入口进。
     *
     * 为什么这一档**必须**存在：读侧（`comic_history` 那张表）与写侧（阅读器逐页落库）从批 82 起
     * 就都有判据盯着，`gallery/ui/DesktopComicHistoryScreen.kt` 里那四颗纯函数也一直在，
     * 而当前这条渲染路里**没有一屏**把它们摆出来 —— 那颗文件的 `@Composable` 是 0，
     * 还带着已拆掉的 Fluent 平行线那一条 import。于是「读过哪几本 / 读到第几话第几页 /
     * 那一行点不点得动」用户今天看不见也进不去，只有首页那一排最多 4 张的横滑卡够得着。
     * 这一批把门开上，并把那颗文件的组件换成共享那一套（material3 `Text` + `VeneraTokens`）。
     */
    data object History : DesktopRoute

    /**
     * 画廊那一域的**标签搜索**。
     *
     * 与 `Queue` / `Downloads` / `History` 同一族：安卓把它藏在画廊首页那一行里，桌面这批没有那一层
     * 域首页，所以做成二级页路由，由画廊那面墙的顶栏入口进。
     *
     * 为什么这一档**必须**存在：`fetchSearchPage`（取一页，与续页同一颗）、`boardOf`（来源轴 → 板）、
     * 与 `DesktopSearchHistory` 那一整排纯件（读 / 写 / 落点 / 清）从桌面起就都在，
     * `DesktopSearchPagingTest` / `DesktopSearchHistoryTest` 也一直绿 —— 而 `desktop/src/main` 里
     * **没有任何一屏**调它们（那一族「有逻辑没有门」，与第一百一十四、一百一十五、一百二十四批同形）。
     * 于是「打一串标签 → 几站一起搜 → 翻页 → 下次接着看搜过的」这一整条用户在 rail 上点不到。
     * 这一批把门开上。
     */
    data object GallerySearch : DesktopRoute

    /**
     * 画廊域的**以图搜图**。
     *
     * 与 `GallerySearch` 同形：安卓把反搜藏在大图播放器/详情的局部动作里，桌面没有那一层，
     * 做成二级页路由，由 rail 画廊频道那条入口进。这一屏是反搜的**统一门面**：
     * SauceNAO（既有线，key 鉴权）与 ascii2d（新线，真浏览器过盾）两个引擎同屏可选，
     * 结果都渲染在软件内 —— ascii2d 那一路的过盾细节住
     * [com.venera.desktop.gallery.data.DesktopAscii2dBridge]，界面上只表现为一块
     * 「验证面板」的显隐。
     */
    data object GalleryReverseSearch : DesktopRoute

    /**
     * 收藏 hub（安卓大屏 `gallery/ui/GalleryBookmarksHubScreen.kt` 那颗屏**直连**进桌面）。
     *
     * 与 `GallerySearch` / `History` 同一族：它挂在 `DesktopRoute` 而不是底栏那一格，
     * 因为安卓那枚 🔖 入口本就藏在**画廊首页顶栏**（`GalleryScreen` 的 actions），不是底栏。
     *
     * 为什么这一档**必须**存在（与第 114/124/125 批同一形状）：hub 那两颗卡的出口里，
     * 「本地收藏」在桌面已有真落点（FAVORITES 那一格那面墙），而屏本身零 Android 宿主
     * 依赖（`TEXT_LINE_HEIGHT` 早在从 pixiv 脏件搬进干净件）—— 逻辑齐、
     * 编译面齐、唯独 `desktop/src/main` 里**没有一屏把它渲出来**。这一批把门开上。
     */
    data object GalleryBookmarksHub : DesktopRoute

    /**
     * 屏蔽规则管理子页（2.5）：按 [type]（KEYWORD/TAG/AUTHOR/COMIC_ID）列规则 + 增删。
     * 消费点真实：这些规则就是主墙 `desktopWallGuardedWall` 与封面遮罩判定吃的守卫账（`DesktopGalleryContentGuard`）。
     */
    data class BlockingRules(val type: String) : DesktopRoute

    /**
     * 阅读统计那一屏（桌面自搭 —— 安卓 `StatsScreen` 是 ③ 档编不过）。
     *
     * 与 `History` / `Queue` / `Downloads` 同一族：做成二级页路由，由探索屏顶栏的入口进。
     *
     * 为什么这一档**必须**存在：`reading_stats` 那张表的读侧（`ReadingStatsStore.summaryRows/
     * trendRows/topComics/streakDays`）早进桌面编译面，首页那三格数字（今日/本周/连续）也一直
     * 在读 —— 缺的是对岸 `StatsScreen` 那**一整屏看板**（14 天趋势 + 常读漫画 Top 10），桌面从前
     * 只有首页三格、没有那一屏。README 那句「阅读统计：共享面在、桌面没有入口」量的就是这一格。
     * 这一批把那一屏的门开上（入口在探索屏顶栏那枚「阅读统计」把手）。
     */
    data object ReadingStats : DesktopRoute

    /**
     * 备份与还原那一屏。
     *
     * 与 `History` / `Downloads` / `ReadingStats` 同一族：做成二级页路由，由探索屏顶栏的入口进。
     *
     * 为什么这一档**必须**存在：`data/db/CoreTableBackup` 三族表 ↔ JSON 那一栏早在桌面编译面里，
     * `CoreTableBackupTest` 22 条真库往返判据一直绿 —— 而 `desktop/src/main` 里**没有任何一屏**
     * 把「导一份本机 JSON / 从一份本机 JSON 灌回来」那两跳摆出来。于是「本机库塌了怎么留底」
     * 这件事在桌面上今天看不见也做不了（那一族「有逻辑没有门」，与 §114/§115/§119/§124 同形）。
     * 这一批把门开上。
     *
     * ⚠️ 与对岸那颗 `.venera` 归档**不是一件事**：那是 `sync/BackupManager`（③ 档，
     * 绑 `CacheDirs` / `ZipOutputStream` / `FavoriteImagesManager` 那套），桌面编不过；
     * 桌面这一份是**单文件 JSON、只覆盖三张核心表**（历史 / 阅读 / 屏蔽规则），
     * 屏上那句话也这么写，不冒充"手机读得了"。
     */
    data object Backup : DesktopRoute

    /**
     * 详情页**评论那一屏**（桌面自搭 —— 安卓 `ComicDetailScreen` 那评论区绑 Context/viewModel/
     * material-icons，是 ③ 档编不过）。
     *
     * 与 `History` / `ReadingStats` / `Backup` 同一族：做成二级页路由、由详情屏顶栏那枚「评论」把手进。
     *
     * 为什么要吃这四样（`comicId` / `subId` / `title` / `sourceKey`）而不是屏里现取：
     * `EngineSession.comments()` 那一跳的实参是 `(comicId, subId, page, replyTo)`，而 `subId` 只活在
     * `loadInfo` 那一次往返的答复里（见 [com.venera.engineprobe.ComicInfo.subId] 那颗注释）—— 壳取过详情
     * 才有 `subId`，屏不该再问一次源站。`title` 只给顶栏念题名用、`sourceKey` 给那一跳先 `load(key)` 用。
     *
     * ⚠️ **为什么不是"章节评论"**：现量过 —— Android 侧 `JsComicSource` **根本没有**重写
     * `loadChapterComments`/`sendChapterComment`（`ComicSource.kt:125-132` 那两行接口默认恒回空表），
     * 随包 `chapterComments` 那几颗只是源脚本里的字段声明、没有任何 Kotlin 桥去调它们。做"章节评论屏"
     * 得到的就是一扇永远念"这一章还没评论"的假门（本仓明令禁假把手）。真被引擎桥接、且有 15 颗真源支持的
     * 是**详情评论**（`s.comic.loadComments` / `s.comments.load` / `s.comments` 三档兜底），桌面默认源
     * `copy_manga` 就在名单里 —— 所以这一颗做的是**详情页评论面板**。
     */
    data class ComicComments(
        val comicId: String,
        val subId: String?,
        val title: String,
        val sourceKey: String,
        /**
         * 这一本的封面 —— 只为一件事：**返回时原样重建 `Detail` 路由**。桌面评论屏是独立二级页（不像对岸
         * 那枚 `showCommentSheet` 浮层背后详情还留着），按「← 返回」得把 `DesktopRoute.Detail` 拼回来，
         * 而那颗路由吃 `cover`。缺这一栏返回就只能拼一册没封面的详情。
         */
        val cover: String,
        /**
         * 来路 = 打开评论的那颗 `Detail` 路由**原对象**（含它自己的 from）——
         * 返回时直接弹回它，评论屏自己拼的 Detail 不再丢来路。
         */
        val from: DesktopRoute? = null,
    ) : DesktopRoute

    /**
     * hub 第二枚卡「Pixiv 书签」在桌面**还没接**的那块诚实报屏。
     *
     * 为什么单独一档而不是让那枚卡按了什么都不发生：本仓明令禁「按了没反应的假把手」，
     * 而桌面按主人裁决剥掉了 pixiv 源（`DESKTOP_EXPLORE_SOURCE` 那条 A 裁决 + 来源轴显式不挂 pixiv），
     * pixiv 云端书签根本没有落点。如实报「这一屏还没接」优于留一枚死按钮 —— 那正是
     * `DesktopNotWiredPane` 立起来要拦的同一条病（假装比空白更糟）。
     */
    data object PixivBookmarksUnwired : DesktopRoute

    /**
     * 图库作品详情页 (对齐 Pixiv 作品详情页与安卓端信息)。
     * 点击图库卡片先进入详情页，左侧大画布 + 右侧完整安卓端信息；点击大画布再进全屏播放器。
     */
    data class GalleryPostDetail(
        val post: DesktopWallCardModel,
        val from: DesktopRoute? = null,
    ) : DesktopRoute

    /**
     * 画廊**看大图那一屏**（桌面自搭 —— 安卓那颗 `GalleryPostScreen` 是 ③ 档编不过）。
     *
     * 与 `Detail` / `Reader` 同一族：把这一张的渲染输入 [post] 塞进路由，进去不必回头再问源站，
     * 返回时那一面墙还在原处。为什么这一档**必须**存在：桌面这条活渲染路从前整块没有看图落点 ——
     * 墙上那张卡走 `VeneraCard { … }`（那颗件有 `onClick` 形参）却从没递进去，于是"能翻、能搜、
     * 能收藏、却按不动的墙"，也正是 `Daily`/`History`/`Post` 那几颗安卓屏（出口全是 `onOpenPost`）
     * 接不进来的共同阻断。这一批把那枚落点造出来。
     *
     * ⚠️ 只装**可点那一档**的卡：能不能点开住在 `DesktopWallCardModel.openable`（有身份且没被打码），
     *   由调用点在按之前判 —— 打码的卡不该被放大（安全评审 A 条：桌面缺安卓那份成人词表，宁可漏判）。
     */
    data class GalleryPostViewer(
        val post: DesktopWallCardModel,
        /**
         * 主人第 5 点：播放器返回要回到**进来的那一页**（作品详情页进来的回详情页、
         * 墙上进来的回墙）—— 从前固定回画廊首页。构造点带 `from`，`desktopShellBack` 弹它。
         */
        val from: DesktopRoute? = null,
    ) : DesktopRoute

    /**
     * 设置那一屏（主人 goal「接着干设置」）。
     *
     * 为什么这一档**必须**存在：桌面活渲染路（`when (route)`）从前**根本没有设置屏** ——
     * `gallery/ui/DesktopGallerySettingsPane.kt` 那颗是已拆掉的 Fluent 平行线遗骸
     * （带 `io.github.composefluent` import、不在这条路上），于是八枚画廊偏好的写口、
     * 分级遮罩档、屏蔽 AI 这些**桌面件上早就接好的真口子**，用户一个都够不着。
     * 这一批把门开上：屏只吃形参（读流 + 写回调），写侧住在装配点递下来的具体件上，
     * 与源管理屏「没有写口就不摆那枚钮」同一条口径。
     */
    data object Settings : DesktopRoute

    /**
     * 源脚本编辑那一屏（主人 goal「源编辑」）。
     *
     * 为什么这一档**必须**存在：源管理屏从前只有启停/遮罩档/屏蔽 AI 三格，
     * 「改那颗源的脚本」这件事在桌面上**连门都没有** —— 而那正是上游 Dart 版有的一格
     * （`_EditFilePage`），也是对岸 `feature/sourcemanage/SourceEditScreen.kt` 那 200 行在等的调用方。
     *
     * `title` 带着而不是进到屏里再问：屏顶那一行「编辑 X」要在**读到原文之前**就有名字可念，
     * 而那一刻不该为了一颗名字再去读一遍 34 份 js（清单那一趟本来就已经把它算好了）。
     */
    data class SourceEdit(
        val key: String,
        val title: String,
    ) : DesktopRoute

    /**
     * 手填凭据那一屏（goal「网页登录」在桌面上成立的那一形）。
     *
     * 为什么这一档存在而「网页登录」四个字没做：§88 那份裁决现量过 —— 对岸的网页登录
     * **只有一形**是内嵌 WebView（`WebLoginScreen.kt:130` 从 WebView 自己的罐里抓 cookie），
     * 桌面没有那一层宿主；而对岸**另外两形**（手填 cookie / 账密）里，手填那一形纯 JVM 能做，
     * 接缝（桥里那三档 cookie、`isLogged`、那颗罐）今天就在。⇒ 这一扇是那一格**真能做的那一半**。
     *
     * `fields` 带着走而不是让屏自己去问：源管理那一屏刻意不起引擎，字段名住在脚本文本里，
     * 屏再读一遍就是第二条轨（批 83 那颗件的注释原话）。清单那一趟已经把它算好了。
     */
    data class SourceLogin(
        val key: String,
        val title: String,
        val fields: List<String>,
        val supportsPasswordLogin: Boolean = false,
        val supportsWebLogin: Boolean = false,
    ) : DesktopRoute

    /** 漫画详情。`sourceKey` 要带着：`EngineSession.details()` 前必须先 `load(sourceKey)`。 */
    data class Detail(
        val comicId: String,
        val title: String,
        val cover: String,
        val sourceKey: String,
        /**
         * 来路（「从二级页面点进去的其他页面，返回必须能回到原来的位置」）。
         * 详情页从历史 / 队列 / 收藏 / 搜索 / 探索 / 首页行都能进，返回按这颗弹回原页；
         * `null`（旧调用点没递）兜回 Tab —— 与旧的 `else -> Tab` 同档，不更差。
         */
        val from: DesktopRoute? = null,
    ) : DesktopRoute

    /** 阅读器。`info` 用它的 `title`/`cover` 写历史，`episode` 用来取页表。 */
    data class Reader(
        val comicId: String,
        val title: String,
        val cover: String,
        val sourceKey: String,
        val episode: EngineSession.Episode,
        /**
         * 进这一屏时**先落在哪一页**（0 起）。
         *
         * ⚠️ **必填、没有默认值**：起点是**调用方的决定**而不是这一屏的常识。默认 0 会让
         * "从库里那一页续读"这件事在调用点被静默抹掉 —— 而那一枚胶囊的文案承诺的正是那一页
         * （`desktopComicContinueCaption`），于是屏上念「继续 第 8 页」而人落在第一页。
         *
         * 今天两个消费者：详情那一列话的各行递 0（照对岸 `:1206`：行不带页），
         * 而「开始/继续阅读」那一枚胶囊递库里那一页。
         */
        val startPage: Int,
        /**
         * 返回时的目标路由；为空则默认退回 [Detail]（详情页）。
         * 从「下载与本地」打开时递 [Files]，按返回准确回到下载与本地页面。
         */
        val from: DesktopRoute? = null,
    ) : DesktopRoute

    /**
     * 画廊「每日热门」二级页（阶段二 · 域 2：安卓 `gallery/ui/GalleryDailyScreen.kt` 那颗屏的**屏本体**
     * 已抽进共享面 `GalleryDailyScreenBody.kt`，桌面喂的是同一颗 body，仿 `History`/`Detail` 那两条先例）。
     *
     * 为什么这一档**必须**存在（与 §114/§124/§125/§128 同一族"有逻辑没有门"）：桌面首页那一排
     * 日榜预览行（`DesktopGalleryDailyRow`）只摆 6 张、没有去处，三档空态/缺席提示/
     * 页尾读数这些对岸判据桌面拿不到；`GalleryDailyFeed` 与 `buildGalleryWall` 早在桌面编译面上，
     * 缺的是那一屏。入口 = 预览行节头右缘的「查看全部」。
     *
     * 与安卓的形状差一处并如实登记：加载环档（安卓 M3 Expressive 波浪环是 ③ 档，JetBrains material3
     * 没有那颗实验件）走 body 的 `loading` 槽位，桌面交自持指示器。
     */
    data object GalleryDaily : DesktopRoute
    /** 图库站 B6：pixivision 文章详情（文章数据住壳，路由只表「开着哪一屏」）。 */
    data object PixivisionArticle : DesktopRoute

    /**
     * 珍藏册发现页（pixiv 域页签入口）。
     * 不带参：三 rail（推荐 / 大家的 / 标签推荐）由屏吃 `GalleryPixivCollections.top()` 现取。
     */
    data object PixivCollections : DesktopRoute

    /**
     * 珍藏册详情页（左封面墙 + 右信息栏）。
     *
     * `collectionId` 是**全链路 String**：站方珍藏册 id 实测 19–20 位数字串，超 Long 上限，
     * 任何一环用 Long 都会溢出炸解析。`title` / `coverUrl` 带着走（详情到货前顶栏与标题条
     * 先念它，理由同 `PixivWork.title`）。`from` = 进入时的来路，返回弹回它。
     */
    data class PixivCollectionDetail(
        val collectionId: String,
        val title: String,
        val coverUrl: String?,
        val from: DesktopRoute? = null,
    ) : DesktopRoute
}


/**
 * 探索屏/详情/阅读器默认用哪一颗源。
 *
 * **主人裁决（A 条）**：值从 `"jm"` 改成 `"copy_manga"`。
 * 理由是内容安全，不是技术：安卓预设表里
 * `app/src/main/assets/source_content_warning.json` 明写着
 * `"jm": { "level": "nsfw" }`，而桌面默认遮罩档是 `OFF`（照安卓抄，
 * `DesktopGalleryContentGuard.kt:138`）⇒ 两件事叠加，成人封面就裸铺在探索屏上了。
 * `copy_manga` 在同一份表里是 `level: "safe"`（"拷贝漫画：综合向"），
 * 且 `app/src/main/assets/sources/copy_manga.js`（1108 行）确实带 `explore`，
 * 所以换过去不会换来一片空屏。
 *
 * ⚠️ 这条换的只是**桌面这一颗常量**，有三件事它管不到，别读成"安全侧都做完了"：
 *  1. **默认遮罩档仍按主人裁决保持 `OFF`**（不改，以免与安卓 `ContentGuardManager.kt:82-89`
 *     分叉）⇒ 这一格收口靠的是"源头换成 safe 站"，不是靠遮罩。
 *  2. **历史与收藏里已存在的 `jm` 条目不受影响** —— 它们仍按 `sourceKey` 各自的值走，
 *     只有**新**进探索/详情/阅读器的那一路跟着这颗常量换。
 *  3. **`VeneraDesktop.kt:199` 那枚 `startKey = "jm"` 不是这一颗** —— 它只服务
 *     无人值守取证那一路（`runAutofavEvidence` 与 `D_启动 起始=` 那句读数），
 *     与用户看的探索屏无关，所以主人这次裁决**不牵动它**。
 *     （旧注释写过"值取 jm 与 `D_启动 起始=jm` 一致"，那是把两枚旋钮说成一枚，本轮改掉。）
 *
 * ⚠️ 这是**桌面自持的决定**，不是从安卓抄的：安卓由用户偏好决定起始源，
 * 桌面还没有"选默认源"的 UI。源管理屏本轮已经接上入口，可那一屏现在只有
 * 「启停 + 遮罩档 + 屏蔽 AI」三格，**没有"设为默认源"这一格** ⇒ "改成读偏好"仍欠着；
 * 到时散在各处的硬编码会漂，所以现在仍收成这一颗。
 */
internal const val DESKTOP_EXPLORE_SOURCE: String = "copy_manga"

/**
 * 路由层级，导航动画的方向判据（唯一出处，消费点是壳里那颗路由 `AnimatedContent`）：
 * `Tab` = 0，二级 = 1，三级及以上 = 2+。
 *
 * - 带 `from` 的路由按来路链递归算深（来路是几级、自己就是几级+1）——
 *   同一颗 `PixivWork` 从墙进来是一级推进、从画师页进来是三级推进，返回时都严格反转；
 * - `SourceEdit` / `SourceLogin` 概念上是源管理的下一层，但桌面上它们的真身是
 *   **浮在源管理之上的独立 Dialog 窗口**，`when (route)` 里那一支只是"背景别换"的
 *   holder（与源管理渲染同一屏）——层级按**同级**算，否则同一屏背景会白播一段推进动画；
 * - `Reader` / `LocalComicReader` / `ComicComments` 恒从详情（一级）进，钉死三级；
 * - 其余全部二级。`Tab` 之间永远同级（0 对 0），由 Tab 分派处的同级淡化处理。
 */
internal fun desktopRouteLevel(route: DesktopRoute): Int = when (route) {
    DesktopRoute.Tab -> 0
    is DesktopRoute.PixivArtist -> 1 + desktopRouteLevel(route.from ?: DesktopRoute.PixivAccount)
    is DesktopRoute.PixivWork -> 1 + desktopRouteLevel(route.from ?: DesktopRoute.PixivAccount)
    is DesktopRoute.GalleryPostDetail -> 1 + desktopRouteLevel(route.from ?: DesktopRoute.Tab)
    is DesktopRoute.GalleryPostViewer -> 1 + desktopRouteLevel(route.from ?: DesktopRoute.Tab)
    is DesktopRoute.PixivCollectionDetail ->
        1 + desktopRouteLevel(route.from ?: DesktopRoute.PixivCollections)
    is DesktopRoute.SourceEdit, is DesktopRoute.SourceLogin -> 1
    is DesktopRoute.ComicComments,
    is DesktopRoute.Reader,
    is DesktopRoute.LocalComicReader,
    is DesktopRoute.BlockingRules,
    DesktopRoute.PixivLogin,
    -> 2
    else -> 1
}
