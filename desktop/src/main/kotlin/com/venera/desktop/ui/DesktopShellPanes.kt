package com.venera.desktop.ui

import com.venera.desktop.app.DesktopRoute
import com.venera.desktop.ui.theme.VeneraType
import com.venera.desktop.ui.DesktopPushButton
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.venera.desktop.ui.DesktopWindowTitleBar
import com.venera.compose.components.VeneraNavTab
import com.venera.compose.components.VeneraSideBarGroup
import com.venera.compose.components.VeneraSideBarItem
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.compose.gallery.domain.GalleryHomeDomain
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.desktop.gallery.ui.DesktopGalleryTopSearch
import com.venera.desktop.gallery.ui.DesktopWallCardModel
import com.venera.desktop.gallery.ui.DesktopWallPage
import com.venera.desktop.gallery.ui.DesktopGalleryRatingFilter
import com.venera.desktop.gallery.ui.DesktopWallDensity
import com.venera.desktop.gallery.ui.DesktopGalleryWallFooter
import com.venera.compose.gallery.data.GallerySite
import com.venera.desktop.gallery.ui.DesktopGallerySort

/**
 * 侧栏二级入口只收**今天真的有落点**的页。
 *
 * 这批不做"未来按钮"：每一条都能落到一个真实 `DesktopRoute`，
 * 选中态也直接由当前 route 算，不靠另存一份导航状态。
 */
internal fun desktopSideBarGroups(
    route: DesktopRoute,
    filesSegment: com.venera.desktop.downloads.DesktopFilesSegment,
    openFilesSegment: (com.venera.desktop.downloads.DesktopFilesSegment) -> Unit,
    open: (DesktopRoute) -> Unit,
): List<VeneraSideBarGroup> {
    fun item(
        key: String,
        title: String,
        icon: ImageVector,
        selected: Boolean,
        target: DesktopRoute,
        onClick: (() -> Unit)? = null,
    ) = VeneraSideBarItem(
        key = key,
        title = title,
        icon = icon,
        selected = selected,
        onClick = onClick ?: { open(target) },
    )

    val segLocal = com.venera.desktop.downloads.DesktopFilesSegment.LOCAL
    val segQueue = com.venera.desktop.downloads.DesktopFilesSegment.QUEUE
    return listOf(
        VeneraSideBarGroup(
            title = "画廊",
            items = listOf(
                item("reverse-search", "搜图", Icons.Outlined.Search, route is DesktopRoute.GalleryReverseSearch, DesktopRoute.GalleryReverseSearch),
                item("daily", "每日热门", Icons.Outlined.Image, route is DesktopRoute.GalleryDaily, DesktopRoute.GalleryDaily),
                // 收藏与浏览历史合成一条入口、一页两视图；旧路由仍收得到（见派发那两支）。
                item(
                    "gallery-saved",
                    "收藏与历史",
                    Icons.Outlined.BookmarkBorder,
                    route is DesktopRoute.GalleryFavorites || route is DesktopRoute.GalleryBrowseHistory,
                    DesktopRoute.GalleryFavorites,
                ),
                item(
                    "followed-artists",
                    "关注画师",
                    Icons.Outlined.Person,
                    route is DesktopRoute.FollowedArtists,
                    DesktopRoute.FollowedArtists,
                ),
                item(
                    "collections",
                    "珍藏册",
                    Icons.Outlined.Collections,
                    route is DesktopRoute.PixivCollections || route is DesktopRoute.PixivCollectionDetail,
                    DesktopRoute.PixivCollections,
                ),
                item("pixiv", "我的 pixiv", Icons.Outlined.AccountCircle, route is DesktopRoute.PixivAccount, DesktopRoute.PixivAccount),
            ),
        ),
        VeneraSideBarGroup(
            title = "漫画",
            items = listOf(
                item("history", "阅读历史", Icons.Outlined.History, route is DesktopRoute.History, DesktopRoute.History),
                item("stats", "阅读统计", Icons.Outlined.BarChart, route is DesktopRoute.ReadingStats, DesktopRoute.ReadingStats),
            ),
        ),
        VeneraSideBarGroup(
            title = "工具",
            items = listOf(
                item("sources", "站点", Icons.Outlined.Tune, route is DesktopRoute.SourceManage, DesktopRoute.SourceManage),
                item("backup", "备份与还原", Icons.Outlined.Settings, route is DesktopRoute.Backup, DesktopRoute.Backup),
            ),
        ),
    )
}

/**
 * 顶栏返回只指向真实上一层。
 *
 * 阅读器 / 看图器走 `fullscreenChrome`，不摆这条顶栏；其余二级页回到各自最自然的上一层，
 * 不把"返回"一律写成回首页 —— 那样从 pixiv 作品回来会丢掉账号上下文。
 */
internal fun desktopShellBack(
    route: DesktopRoute,
    setRoute: (DesktopRoute) -> Unit,
): (() -> Unit)? {
    val target = when (route) {
        DesktopRoute.Tab -> null
        // 搜图按一级页待 —— 不摆返回（侧栏直达，没有来路）。
        DesktopRoute.GalleryReverseSearch -> null
        DesktopRoute.FollowedArtists -> DesktopRoute.Tab
        is DesktopRoute.SourceEdit, is DesktopRoute.SourceLogin -> DesktopRoute.SourceManage
        // 返回口径：pixiv 线与看图播放器弹回来路（`from`）；登录页无参落账号页。
        is DesktopRoute.GalleryPostDetail -> route.from ?: DesktopRoute.Tab
        is DesktopRoute.GalleryPostViewer -> route.from ?: DesktopRoute.Tab
        is DesktopRoute.BlockingRules -> DesktopRoute.Settings
        DesktopRoute.PixivisionArticle -> DesktopRoute.Tab
        is DesktopRoute.PixivWork -> route.from ?: DesktopRoute.PixivAccount
        DesktopRoute.PixivLogin -> DesktopRoute.PixivAccount
        is DesktopRoute.PixivArtist -> route.from ?: DesktopRoute.PixivAccount
        // 详情弹回来路（发现页 / 搜索 / 账号页），发现页本身回画廊 Tab。
        is DesktopRoute.PixivCollectionDetail -> route.from ?: DesktopRoute.PixivCollections
        // 评论返回 = 弹回打开它的那颗 Detail **原对象**（含 from 链）。
        is DesktopRoute.ComicComments -> route.from ?: DesktopRoute.Detail(
            comicId = route.comicId,
            title = route.title,
            cover = route.cover,
            sourceKey = route.sourceKey,
        )
        is DesktopRoute.Detail -> route.from ?: DesktopRoute.Tab
        is DesktopRoute.Reader -> route.from ?: DesktopRoute.Detail(
            comicId = route.comicId,
            title = route.title,
            cover = route.cover,
            sourceKey = route.sourceKey,
        )
        is DesktopRoute.LocalComicReader -> route.from ?: DesktopRoute.Files
        else -> DesktopRoute.Tab
    }
    return target?.let { next -> { setRoute(next) } }
}

// 原 54dp 壳内顶栏 `DesktopShellTopBar` 整条并入自绘标题栏 `DesktopWindowTitleBar`
// （二级页不再出现第二条栏）。那颗件与它的 onSearch/onSettings 两枚
// 转交闭包随之退役；标题单一出处 `desktopShellTitle` 保留给新顶栏的面包屑吃。

internal fun blockingRuleTypeLabel(type: String): String = when (type) {
    "TAG" -> "标签"; "AUTHOR" -> "画师"; "COMIC_ID" -> "作品"; else -> "关键词"
}

internal fun desktopShellTitle(route: DesktopRoute, currentTab: VeneraNavTab, discoverViewExplore: Boolean = false): String {
    val raw = when (route) {
        DesktopRoute.Tab -> when (currentTab) {
            // 搜索格是选择器双页（搜索/探索），面包屑跟着选择器态念；
            // 桌面自持念「漫画搜索」（共享枚举 title 不动）。
            VeneraNavTab.SEARCH -> if (discoverViewExplore) "漫画搜索 · 探索" else "漫画搜索"
            else -> currentTab.title
        }
        DesktopRoute.SourceManage -> "站点"
        is DesktopRoute.SourceEdit -> "编辑源 · ${route.title}"
        is DesktopRoute.SourceLogin -> "登录源 · ${route.title}"
        DesktopRoute.Files -> "下载与本地"
        DesktopRoute.Queue -> "下载队列"
        DesktopRoute.Downloads -> "本机已下载"
        DesktopRoute.LocalComics -> "本地漫画"
        is DesktopRoute.LocalComicReader -> route.title
        DesktopRoute.History -> "阅读历史"
        DesktopRoute.GalleryBrowseHistory, DesktopRoute.GalleryFavorites -> "收藏与历史"
        DesktopRoute.FollowedArtists -> "关注画师"
        DesktopRoute.GalleryReverseSearch -> "搜图"
        DesktopRoute.GallerySearch -> "画廊搜索"
        DesktopRoute.GalleryBookmarksHub -> "画廊收藏"
        DesktopRoute.GalleryDaily -> "每日热门"
        DesktopRoute.PixivisionArticle -> "pixivision 文章"
        DesktopRoute.PixivCollections -> "珍藏册"
        is DesktopRoute.PixivCollectionDetail -> route.title
        DesktopRoute.ReadingStats -> "阅读统计"
        DesktopRoute.Backup -> "备份与还原"
        DesktopRoute.Settings -> "设置"
        DesktopRoute.PixivAccount -> "我的 pixiv"
        DesktopRoute.PixivLogin -> "登录 pixiv"
        is DesktopRoute.PixivArtist -> route.name
        is DesktopRoute.PixivWork -> route.title
        DesktopRoute.PixivBookmarksUnwired -> "pixiv 书签"
        is DesktopRoute.Detail -> "漫画详情"
        is DesktopRoute.Reader -> "阅读器"
        is DesktopRoute.ComicComments -> "评论"
        is DesktopRoute.GalleryPostDetail -> "作品详情 · ${route.post.uid}"
        is DesktopRoute.GalleryPostViewer -> "查看大图"
        is DesktopRoute.BlockingRules -> "屏蔽规则 · ${blockingRuleTypeLabel(route.type)}"
    }
    return com.venera.desktop.settings.DesktopI18n.tr(raw)
}


/**
 * 「这一屏还没接」—— 如实报，不拿别的屏顶上。
 *
 * ## 为什么需要这一颗
 *
 * 上一版把画廊那面图墙摆到了 HOME 位置，`else` 也兜回它 —— 于是五个 tab 里有四个
 * 长得一模一样，而用户一眼看出「你这个首页怎么和安卓端的完全不一样？」。
 * 安卓首页是**漫画推荐页**（`feature/HomeScreen.kt`，1124 行：阅读统计 / 可能你感兴趣 /
 * 历史分区），图片墙是 GALLERY 那一屏（`gallery/ui/GalleryScreen.kt`，3453 行）。
 *
 * 假装比空白更糟：空白会让人问"怎么没有"，假装会让人以为"内容全错了"。
 *
 * ## 为什么不直接渲染安卓那几屏
 *
 * 那四屏都 `viewModel()` 到 `AndroidViewModel(Application)` 上（`GalleryViewModel`、
 * `HomeViewModel`…），要 `BusinessPorts` 那 17 个成员门面 + `ComicSourceManager`
 * + `AndroidViewModelFactory`。那不是"再挂一个 srcDir"，是要给整个漫画业务层做一份
 * 桌面实现 —— 独立一批，不在伪装成已完成。
 */
/**
 * pixiv 三屏的**临时占位**。每一屏交付时把对应分支换成真屏，这颗整颗删掉
 * （留着 = 又一族「有门通到空房间」）。
 */
@Composable
internal fun DesktopPixivStubPane(name: String, onBack: () -> Unit) {
    val tokens = VeneraTokens
    Box(
        modifier = Modifier.fillMaxSize().padding(tokens.spacing.screenHorizontal),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = name, fontSize = VeneraType.pageTitle.size, lineHeight = VeneraType.pageTitle.lineHeight, color = tokens.color.textPrimary)
            Spacer(Modifier.height(tokens.spacing.space2))
            Text(text = "暂未开放", fontSize = VeneraType.body.size, lineHeight = VeneraType.body.lineHeight, color = tokens.color.textTertiary)
            Spacer(Modifier.height(tokens.spacing.space4))
            com.venera.desktop.ui.DesktopBackButton(onClick = onBack)
        }
    }
}

@Composable
internal fun DesktopNotWiredPane(tab: VeneraNavTab) {    val tokens = VeneraTokens
    Box(
        modifier = Modifier.fillMaxSize().padding(tokens.spacing.screenHorizontal),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = tab.title,
                fontSize = VeneraType.pageTitle.size, lineHeight = VeneraType.pageTitle.lineHeight,
                color = tokens.color.textPrimary,
            )
            Spacer(Modifier.height(tokens.spacing.space3))
            Text(
                text = "暂未开放",
                fontSize = VeneraType.body.size, lineHeight = VeneraType.body.lineHeight,
                color = tokens.color.textSecondary,
            )
        }
    }
}

/**
 * hub 第二枚卡「Pixiv 书签」点下去的那一屏。
 *
 * 安卓那一枚跳到 pixiv 账号页的收藏段（`Navigation.kt` 的 `onOpenPixivBookmarks`），而桌面按主人
 * 裁决**不带 pixiv 源**（来源轴显式剥掉 pixiv）⇒ 那一路在桌面结构性地没有落点。与其在 hub 上
 * 留一枚按了没反应的死卡，不如如实报「这一屏还没接」—— 与 [DesktopNotWiredPane] 同一条口径
 * （假装比空白更糟）。返回回到 hub，不把用户困在这一屏。
 */
@Composable
internal fun DesktopPixivBookmarksUnwiredPane(onBack: () -> Unit) {
    val tokens = VeneraTokens
    Box(
        modifier = Modifier.fillMaxSize().padding(tokens.spacing.screenHorizontal),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Pixiv 书签",
                fontSize = VeneraType.pageTitle.size, lineHeight = VeneraType.pageTitle.lineHeight,
                color = tokens.color.textPrimary,
            )
            Spacer(Modifier.height(tokens.spacing.space3))
            Text(
                text = "暂未开放",
                fontSize = VeneraType.body.size, lineHeight = VeneraType.body.lineHeight,
                color = tokens.color.textSecondary,
            )
            Spacer(Modifier.height(tokens.spacing.space4))
            DesktopPushButton(onClick = onBack) { Text("返回收藏页") }
        }
    }
}

/**
 * 画廊那一屏 —— 用**真实的** `VeneraCard` + `VeneraCover` 摆一面自适应墙。
 *
 * 这是今天桌面上**唯一**接了真数据的屏：`GalleryPorts` 拉 yande.re / safebooru
 * 的第一页（gelbooru 要账号，没配就不发车），混排去重后上墙。
 *
 * ⚠️ 它还不是安卓那一颗 `GalleryScreen`（3453 行：日榜三站 · 猜你喜欢 · 搜索 ·
 * 反向搜图 · pixiv 三屏 · 收藏夹 · 屏蔽规则那一整套）。今天它只画"最新那一格"，
 * 也就是那 3453 行里最小的一块 —— 复用的是**同一批零件**（`VeneraCard` /
 * `VeneraCover` / `VeneraTokens` / 列数尺），不是同一屏。
 *
 * ## 滚动位置**不住这一屏**
 *
 * 那一面墙的 `LazyStaggeredGridState` 住在壳那一层（`wallGridState` 那颗 `remember`），
 * pane 只把它递给 scaffold、scaffold 再递给 grid —— **不在这一屏 `remember`**。
 * 为什么：pane 是 `when(route).Tab.GALLERY` 那一支，点一张卡进 §130 看图屏时那一支整个离组合，
 * state 随之销毁（对岸 `PixivAccountSessionStore.kt:40` 逐字记过"state 活不过组合销毁"）⇒
 * 返回 Tab 时墙从头排起，§124 收尾点名"回看保住原来看到第几张"那一格就是这么坏的。
 * 接线三跳由源码尺 `DesktopWallScrollRetentionTest` 六条钉着（④ 那条反面对照专封"搬回屏里"）。
 */
/**
 * 发现带 + 关注新作的登录腿**整颗成品**（壳 effect 产出，pane/scaffold 原样递进）。
 *
 * - [users] = 站方推荐用户前 12 位；空表 + [ready] = 未登录或端口缺席（屏上整节不摆）；
 * - [followedIds] = **本会话内**点过关注的账（首页不拉全量关注名单，精确态住账号页）；
 * - [followingCards]/[followingAvatars] = 关注流第一页过守卫后的墙卡投影与画师头像表。
 */
internal data class PixivDiscoverLeg(
    val users: List<com.venera.compose.gallery.data.PixivUser>,
    val followedIds: Set<Long>,
    val followingCards: List<DesktopWallCardModel>,
    val followingAvatars: Map<String, String>,
    val ready: Boolean,
)

@Composable
internal fun DesktopGalleryWallPane(
    mode: WideScreenLayoutMode,
    /**
     * 整颗墙页的值（账 + 卡 + 落屏墙，阶段三 · 首屏共享批 T3）：pane 不再吃"投影后的卡表"，
     * 而是把这一颗递给共享页 `GalleryForYouPage` —— 墙上摆哪张、遮不遮、页尾念什么，
     * 判据与安卓首屏**同一颗文件同一颗函数**（`gallery/ui/GalleryWallGrid.kt`），桌面这一侧
     * 只留视图投影（`wallPage.cards`）当"点一张卡要递什么给看图屏"的查表。
     * `null` = 还没到货（页里那一档转圈，而不是空态那句"没有东西"）。
     */
    wallPage: DesktopWallPage?,
    /** 手动刷新进行中：旧墙留屏，仅顶部亮一条细进度。首屏加载仍走 wallPage=null 的转圈态。 */
    wallRefreshing: Boolean = false,
    footer: DesktopGalleryWallFooter,
    onLoadMore: () -> Unit,
    // ── 悬浮：顶栏中段换成真输入场，四样由壳递进来在这里装成一格 ──
    /**
     * 查询级成人内容排除总闸（true = 允许 R18 不排除；false = 全年龄）。状态住壳：
     * 检索屏首轮那处按它拼站方排除 tag，壳层改档要立刻影响下一轮发车。
     */
    searchR18: Boolean,
    /** 开关落盘回调（railPrefs 一枚 int，壳层唯一写点）。 */
    onPickSearchR18: (Boolean) -> Unit,
    /** 浮层发车回调：站源（null = booru 聚合，PIXIV = pixiv 专属轴）+ 词原样。壳层切路由进检索屏。 */
    onTopSearch: (GallerySite?, String) -> Unit,
    /** pixiv 联想腿的 JS 引擎（壳按 galleryPaths 算好递进来；null = AutoComplete 走兜底）。 */
    pixivSuggestEngine: com.venera.compose.data.platform.HttpEngine?,
    /** 标题行右侧那枚「收藏」把手（安卓画廊首页顶栏那枚 🔖，hub 那一屏的入口）。 */
    onOpenBookmarks: () -> Unit,
    /**
     * 标题行右侧那枚「历史」把手（画廊帖子的浏览历史那一屏的入口）。
     *
     * **必传无默认值**（评审 R8 那一族）：默认 `{}` 就是把「接了形参而没人递回调」那格病埋回去 ——
     * §138 修的正是那格（那份档与那颗存储件全在，而活渲染路里连一扇门都没有）。
     * 那一枚**恒摆**（与批 115/137 那几枚入口同一口径）：空历史也要进得去看「本机还没点开过图」那一档。
     */
    onOpenBrowseHistory: () -> Unit,
    /**
     * 首页 chrome 那一排选中的内容域（安卓大屏形态）。**必传无默认值**：
     * 给默认值 = 下一个调用点忘传时静默把切换器摘掉，而"整页只剩 Pixiv 那一池、切不回去"
     * 正是共享件那条注里记过的最贵形状。域状态住壳（与 `wallPage` 同层，切换要换真取数腿）。
     */
    domain: GalleryHomeDomain,
    /** 点那一排的一格。**必传无默认值**，同 [domain] 那条理由。 */
    onPickDomain: (GalleryHomeDomain) -> Unit,
    /**
     * 首页那几节横栏的成品读数与点档回调。**全必传无默认值**（评审 R8 那一族）：
     * 画师读数 `null` 说的是"还在读"（那一节不摆），`onPickRankingMode` 给的档直接换那面墙的腿 ——
     * 少接一枚就是把"行里的动作"埋成按不动的假把手（与 §130 `onOpenPost` 同一族教训）。
     */
    followedArtists: com.venera.compose.gallery.domain.GalleryFollowedArtistsReadout?,
    onOpenArtist: (com.venera.compose.gallery.domain.FollowedArtistRef) -> Unit,
    dailyCards: List<DesktopWallCardModel>,
    rankingMode: com.venera.compose.gallery.data.PixivRankingMode,
    onPickRankingMode: (com.venera.compose.gallery.data.PixivRankingMode) -> Unit,
    /**
     * 墙上那张卡点下去看大图。**必传无默认值**：给了默认 `{}` 就是把"接了形参而没人
     * 递回调"那格病重新埋回去（评审 R8 那一族）。传进来的卡一定是 `openable` 那一档，屏自己不再判打码。
     */
    onOpenPost: (DesktopWallCardModel) -> Unit,
    /** PIXIV 域：进「我的 pixiv」（账号页 + 登录）。**必传无默认值**，同上族纪律。 */
    onOpenPixivAccount: () -> Unit,
    /** BOORU 域：日榜预览行那枚「查看全部」的落点（阶段二 · 域 2，`GalleryDaily` 路由）。**必传无默认值**。 */
    onOpenDaily: () -> Unit,
    /**
     * BOORU 域主墙的排序档与点档（推荐 / 最新 / 最热）。
     * **必传无默认值**：档状态住壳（切档 = 换真取数腿，见首屏那颗 `LaunchedEffect`），
     * 给默认值就是把"切了档而墙没动"那格假开关埋回屏里。PIXIV 域这一排不渲。
     */
    sort: DesktopGallerySort,
    onPickSort: (DesktopGallerySort) -> Unit,
    /**
     * 右下角那枚浮动刷新钮的落点（两域都要刷新把手）。
     * 壳里 = `wallRefreshTick++`：强制重取**当前档**整墙并顶掉缓存；清晰度也是在这一记才生效
     *（设置流不再进首屏键——改设置不重刷，见 `galleryWallQuality` 那段）。**必传无默认值**。
     */
    onRefresh: () -> Unit,
    /**
     * PIXIV 域榜单预览条的卡（壳里按 rankingMode 取该档首页、
     * 过守卫、缓存；不再从主墙截——主墙是推荐腿时预览条若同源会顶着「日榜预览」摆推荐，假读数）。
     * BOORU 域不渲这一枚。**必传无默认值**。
     */
    previewCards: List<DesktopWallCardModel>,
    /** 预览条卡的画师头像 / 站方标题两张表（主人口径「榜首卡也带头像和标题」）。 */
    previewAvatars: Map<String, String> = emptyMap(),
    previewTitles: Map<String, String> = emptyMap(),
    /** 发现带 + 关注新作的登录腿成品（壳 effect 产出；未登录 = ready 空表）。 */
    pixivDiscover: PixivDiscoverLeg,
    /** 发现带标签卡点下去 → 那串词交给画廊搜索（与画师行同一扇交接门）。 */
    onSearchTag: (String) -> Unit,
    /** 发现带用户卡点下去 → 搜这位画师（pixiv 域名交接）。 */
    onSearchUser: (com.venera.compose.gallery.data.PixivUser) -> Unit,
    /** 推荐用户的关注扳机（站方 follow add/remove；成功才进本地账）。 */
    onTogglePixivFollow: (com.venera.compose.gallery.data.PixivUser) -> Unit,
    /** pixivision 最新文章（匿名腿解析成品；空表 = 那一节不摆）。 */
    pixivisionArticles: List<com.venera.desktop.gallery.data.DesktopPixivisionArticle>,
    /** 文章卡点下去 = 进二级页（壳存文章 + 切路由）。 */
    onOpenPixivisionArticle: (com.venera.desktop.gallery.data.DesktopPixivisionArticle) -> Unit,
    // ── 图库站：筛选架（BOORU 域）──
    booruRatingFilter: DesktopGalleryRatingFilter,
    onPickBooruRating: (DesktopGalleryRatingFilter) -> Unit,
    booruSiteFilter: GallerySite?,
    onPickBooruSite: (GallerySite?) -> Unit,
    /** 标签维度舱的成品组（壳词典腿产出；空表 = 那一节不摆）。 */
    tagDimensions: List<com.venera.desktop.gallery.ui.DesktopTagDimension>,
    tagDictionaryReady: Boolean,
    // ── 图库站：行高密度 + Ctrl 多选（pane 只递不存）──
    wallDensity: DesktopWallDensity,
    onPickWallDensity: (DesktopWallDensity) -> Unit,
    wallSelectedUids: List<String>,
    onToggleWallSelect: (DesktopWallCardModel) -> Unit,
    onClearWallSelect: () -> Unit,
    /** 整批替换选择（全选 / 反选按当前可见卡算，壳只落账）。 */
    onSetWallSelection: (Set<String>) -> Unit,
    /** 桌面数据目录（批量下载落点；null = 坞里不摆批量下载）。 */
    galleryPaths: com.venera.compose.data.platform.PathProvider? = null,
    /** 主墙布局两档（Grid = 行对齐自绘 / Staggered = 共享页瀑布流）。 */
    wallLayout: com.venera.compose.gallery.domain.GalleryWallLayout,
    onPickWallLayout: (com.venera.compose.gallery.domain.GalleryWallLayout) -> Unit,
    /** 瀑布流档的滚态（Grid 档吃 gridState 那颗；两颗都住壳）。 */
    staggeredState: androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState,
    /**
     * 那面墙的滚动位置。**必传无默认值**，与上面那枚 `onOpenPost` 同一族纪律：
     * 默认值就是把"漏传而屏不报错"那格病埋回去。而这里"漏传"具体是：pane 里各造各的 state ⇒ 一离组合
     * 就丢、回来从头排起 —— §124 收尾点名"回看保住原来看到第几张"那一格。state 必须住在**没被销毁**的
     * 那一层（壳），pane 只是把它递给 grid；不判、不改、不重置。
     */
    gridState: androidx.compose.foundation.lazy.LazyListState,
    /** 共享页要的业务端口（词典标题腿；装配点递，屏不找静态口 —— `DesktopUiIsolationTest` 那条）。 */
    ports: GalleryPorts,
    /**
     * 「AI 角标」与 pixiv 画质两档的**具体件偏好**（阶段三 T3 翻喂共享页新增）。
     * 流在这一层收（`collectAsState`），scaffold 只吃算好的值 —— 与"屏不找静态口"同一条纪律。
     * `null` = 这台还没接画廊偏好：两档退到与安卓 pref 默认档同值的 `true` / `MEDIUM`，
     * 墙上角标因此恒按"开"读（与设置页没接之前同形，不是假装读了偏好）。
     */
    galleryPrefs: com.venera.desktop.gallery.data.DesktopGalleryPreferences? = null,
) {
    val aiBadge by (galleryPrefs?.galleryAiBadge ?: constFlow(true)).collectAsState()
    val pixivQuality by (
        galleryPrefs?.pixivPreviewQuality
            ?: constFlow(com.venera.compose.gallery.domain.PixivImageQuality.MEDIUM)
        ).collectAsState()
    val columnMode by (
        galleryPrefs?.galleryColumnMode
            ?: constFlow(com.venera.compose.gallery.domain.GalleryColumnMode.AUTO)
        ).collectAsState()
    val wallQuality = currentWallQuality(galleryPrefs)
    // 悬浮搜索场在这里装成一格（壳递来的四样 + pane 手上的 ports），chrome 只认这一枚槽。
    val booruTopSearchSites = remember { GallerySite.entries.filter { it != GallerySite.PIXIV } }
    val topSearch: @Composable () -> Unit = {
        DesktopGalleryTopSearch(
            domain = domain,
            booruSites = booruTopSearchSites,
            ports = ports,
            pixivEngine = pixivSuggestEngine,
            searchR18 = searchR18,
            onPickSearchR18 = onPickSearchR18,
            onSearch = onTopSearch,
        )
    }
    DesktopWallScaffold(
        mode,
        previewQuality = wallQuality,
        wallPage = wallPage,
        wallRefreshing = wallRefreshing,
        footer = footer,
        onLoadMore = onLoadMore,
        topSearch = topSearch,
        onOpenBookmarks = onOpenBookmarks,
        onOpenBrowseHistory = onOpenBrowseHistory,
        domain = domain,
        onPickDomain = onPickDomain,
        followedArtists = followedArtists,
        onOpenArtist = onOpenArtist,
        dailyCards = dailyCards,
        rankingMode = rankingMode,
        onPickRankingMode = onPickRankingMode,
        onOpenPost = onOpenPost,
        onOpenPixivAccount = onOpenPixivAccount,
        onOpenDaily = onOpenDaily,
        sort = sort,
        onPickSort = onPickSort,
        onRefresh = onRefresh,
        previewCards = previewCards,
        previewAvatars = previewAvatars,
        previewTitles = previewTitles,
        pixivDiscover = pixivDiscover,
        onSearchTag = onSearchTag,
        onSearchUser = onSearchUser,
        onTogglePixivFollow = onTogglePixivFollow,
        pixivisionArticles = pixivisionArticles,
                        booruRatingFilter = booruRatingFilter,
                        onPickBooruRating = onPickBooruRating,
                        booruSiteFilter = booruSiteFilter,
                        onPickBooruSite = onPickBooruSite,
                        tagDimensions = tagDimensions,
                        tagDictionaryReady = tagDictionaryReady,
                        wallDensity = wallDensity,
                        onPickWallDensity = onPickWallDensity,
                        wallSelectedUids = wallSelectedUids,
                        onToggleWallSelect = onToggleWallSelect,
                        onClearWallSelect = onClearWallSelect,
                        onSetWallSelection = onSetWallSelection,
                        galleryPaths = galleryPaths,
                        galleryPrefs = galleryPrefs,
                        wallLayout = wallLayout,
                        onPickWallLayout = onPickWallLayout,
                        staggeredState = staggeredState,
                        gridState = gridState,
                        onOpenPixivisionArticle = onOpenPixivisionArticle,
        ports = ports,
        columnMode = columnMode,
        aiBadge = aiBadge,
        pixivQuality = pixivQuality,
    )
}
