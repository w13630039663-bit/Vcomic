package com.venera.desktop.ui

import com.venera.desktop.data.DesktopPixivWorkTrail
import com.venera.desktop.gallery.ui.selection.LocalSelectionDockHazeState
import com.venera.desktop.ui.DesktopPushButton
import com.venera.desktop.ui.theme.VeneraType
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.shadow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.compose.gallery.domain.GalleryHomeDomain
import com.venera.compose.gallery.domain.GalleryPreviewQuality
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.desktop.gallery.ui.DesktopWallCardModel
import com.venera.desktop.gallery.ui.DesktopWallPage
import com.venera.desktop.gallery.ui.DesktopGalleryMetrics
import com.venera.desktop.gallery.ui.DesktopGalleryChromeRow
import com.venera.desktop.gallery.ui.DesktopGalleryRatingFilter
import com.venera.desktop.gallery.ui.DesktopWallDensity
import com.venera.desktop.gallery.ui.columnsFor
import com.venera.desktop.gallery.ui.DesktopGalleryTagDimensions
import com.venera.desktop.gallery.ui.desktopFilteredWallPage
import com.venera.desktop.gallery.ui.desktopHotTagsOf
import com.venera.desktop.gallery.ui.DesktopGalleryWallHead
import com.venera.desktop.gallery.ui.DesktopGalleryWallFooter
import com.venera.desktop.gallery.ui.DesktopGalleryHomeRows
import com.venera.desktop.gallery.data.desktopResolveSaveDir
import com.venera.compose.gallery.data.GallerySite
import com.venera.desktop.gallery.ui.DesktopGallerySort
import com.venera.desktop.gallery.ui.LocalDesktopSearchHandoff

/**
 * 一面墙（列数、间距、卡片形态全走同一批 token 与判据）。
 *
 * 提出来不是为了少写几行，是为了让"哪些地方用同一把尺"在代码上成立：
 * 各写一遍的话，下一轮改列数就会只改一处，而那正是本仓记过的那类漂移。
 *
 * 阶段三 · 首屏共享批 T3：墙上那些卡**不再桌面自画** —— 网格那一格换喂安卓同一件
 * `GalleryForYouPage`（卡形、caption、缺席提示、空/忙两档、越界预取全在共享件那一份里）。
 * UI-2 修正后，hero 带 / chrome / 首页横栏 / 页尾说明全部住进共享页的同一个 header item，
 * 与主墙共用一条纵向滚动轴；不再出现"上面固定、只有下面瀑布流能滚"的半截页面。
 * 桌面仍保留一处已登记偏离：「下一页」是按钮而不是下拉刷新（桌面没有手势腿；
 * 页的 `pullToRefresh` 槽交 null）。
 * 取图腿：桌面不递 loader —— 共享卡里的 `VeneraCover` 空腿回落到 `LocalUxPlatformServices.imageLoader()`，
 * 而桌面那颗报 null ⇒ 吃 Coil 应用级单例（正是今天自绘墙卡的同一条链，`DesktopImagePipeline.install` 装的）。
 */
@Composable
internal fun DesktopWallScaffold(
    mode: WideScreenLayoutMode,
    /** 墙上预览清晰度档（设置流现值）。必传无默认值：漏传就静默退回常量，与假开关同形。 */
    previewQuality: com.venera.compose.gallery.domain.GalleryPreviewQuality,
    /** 整颗墙页的值（账 + 卡 + 落屏墙）；`null` = 还没到货（页里那一档转圈而不是空态）。 */
    wallPage: DesktopWallPage?,
    /** 手动刷新进行中：旧墙留屏、顶部亮细进度条；首屏加载态仍由 wallPage=null 表达。 */
    wallRefreshing: Boolean = false,
    /**
     * 页尾那三格（摆不摆那枚把手 / 那几句念什么 / 取证那一句）。**必传无默认值**：
     * 少传一次 = 那一面墙静默退回"只有第一页"，而那正是要修的缺口本身。
     */
    footer: DesktopGalleryWallFooter,
    onLoadMore: () -> Unit,
    /** 顶栏中段的真输入搜索场（pane 装好递进来，chrome 只认这一枚槽）。 */
    topSearch: @Composable () -> Unit,
    /** 标题行右侧那枚「收藏」把手（进安卓那颗收藏 hub）。 */
    onOpenBookmarks: () -> Unit,
    /** 标题行右侧那枚「历史」把手（进画廊帖子的浏览历史那一屏）。 */
    onOpenBrowseHistory: () -> Unit,
    /** 首页 chrome 那一排的内容域与点格。**必传无默认值**，同 pane 那条理由。 */
    domain: GalleryHomeDomain,
    onPickDomain: (GalleryHomeDomain) -> Unit,
    /** chrome 之下、墙之上的几节横栏。**必传无默认值**，同 pane 那条理由。 */
    followedArtists: com.venera.compose.gallery.domain.GalleryFollowedArtistsReadout?,
    onOpenArtist: (com.venera.compose.gallery.domain.FollowedArtistRef) -> Unit,
    dailyCards: List<DesktopWallCardModel>,
    rankingMode: com.venera.compose.gallery.data.PixivRankingMode,
    onPickRankingMode: (com.venera.compose.gallery.data.PixivRankingMode) -> Unit,
    /** 墙上那张可点卡点下去看大图。**必传无默认值**，同 [DesktopGalleryWallPane] 那条理由。 */
    onOpenPost: (DesktopWallCardModel) -> Unit,
    /** PIXIV 域：进「我的 pixiv」（账号页 + 登录）。**必传无默认值**，同 pane 那条理由。 */
    onOpenPixivAccount: () -> Unit,
    /** BOORU 域：日榜预览行节头那枚「查看全部」→ 安卓那颗日榜二级页屏（阶段二 · 域 2）。**必传无默认值**。 */
    onOpenDaily: () -> Unit,
    /**
     * BOORU 域主墙排序档与点档（chrome 那一排）。**必传无默认值**，同 pane 那条理由。
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
    /** 发现带 + 关注新作的登录腿成品（pane 原样递进）。 */
    pixivDiscover: PixivDiscoverLeg,
    /** 发现带标签卡 → 画廊搜索的交接门。 */
    onSearchTag: (String) -> Unit,
    /** 发现带用户卡 → 搜这位画师。 */
    onSearchUser: (com.venera.compose.gallery.data.PixivUser) -> Unit,
    /** 推荐用户的关注扳机。 */
    onTogglePixivFollow: (com.venera.compose.gallery.data.PixivUser) -> Unit,
    /** pixivision 最新文章（匿名腿解析成品；空表 = 那一节不摆）。 */
    pixivisionArticles: List<com.venera.desktop.gallery.data.DesktopPixivisionArticle>,
    /** 文章卡点下去 = 进二级页（壳存文章 + 切路由）。 */
    onOpenPixivisionArticle: (com.venera.desktop.gallery.data.DesktopPixivisionArticle) -> Unit,
    // ── 图库站：筛选架（BOORU 域；过滤在 scaffold 顶部遮蔽生效）──
    booruRatingFilter: DesktopGalleryRatingFilter,
    onPickBooruRating: (DesktopGalleryRatingFilter) -> Unit,
    booruSiteFilter: GallerySite?,
    onPickBooruSite: (GallerySite?) -> Unit,
    /** 标签维度舱的成品组（pane 原样递进）。 */
    tagDimensions: List<com.venera.desktop.gallery.ui.DesktopTagDimension>,
    tagDictionaryReady: Boolean,
    // ── 图库站：行高密度 + Ctrl 多选（scaffold 只递给行对齐墙）──
    wallDensity: DesktopWallDensity,
    onPickWallDensity: (DesktopWallDensity) -> Unit,
    wallSelectedUids: List<String>,
    onToggleWallSelect: (DesktopWallCardModel) -> Unit,
    onClearWallSelect: () -> Unit,
    onSetWallSelection: (Set<String>) -> Unit,
    galleryPaths: com.venera.compose.data.platform.PathProvider? = null,
    /** 下载目录偏好（批量下载与单卡存图同一条落点解析）。 */
    galleryPrefs: com.venera.desktop.gallery.data.DesktopGalleryPreferences? = null,
    /** 主墙布局两档：Grid = 等宽网格自绘 / Staggered = 共享页瀑布流。 */
    wallLayout: com.venera.compose.gallery.domain.GalleryWallLayout,
    onPickWallLayout: (com.venera.compose.gallery.domain.GalleryWallLayout) -> Unit,
    /** 瀑布流档滚态。 */
    staggeredState: androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState,
    /**
     * 那面墙的滚动状态。**必传无默认值**，同 pane 那条理由：这里"漏传"的具体形状是
     * `remember` 住在 scaffold 自己身上——scaffold 与 pane 同生同灭 ⇒ 一回看图屏离组合就丢，回来从头排起。
     */
    gridState: androidx.compose.foundation.lazy.LazyListState,
    /** 共享页要的业务端口（词典标题腿；装配点递，屏不找静态口 —— `DesktopUiIsolationTest` 那条）。 */
    ports: GalleryPorts,
    /** 设置里的网格列数档；AUTO 随内容区变宽加列，TWO / THREE 是显式覆盖。 */
    columnMode: com.venera.compose.gallery.domain.GalleryColumnMode,
    /** 「AI 角标」与 pixiv 画质两档的当前值（装配层从偏好流收上来；缺省与安卓那两枚 pref 默认档同值）。 */
    aiBadge: Boolean = true,
    pixivQuality: com.venera.compose.gallery.domain.PixivImageQuality =
        com.venera.compose.gallery.domain.PixivImageQuality.MEDIUM,
) {
    val tokens = VeneraTokens
    // 图库站：筛选架的**浏览层过滤**（投影之后的副本，cards 与 wall.cards 同步滤）——
    // 参数遮蔽：从这里往下 `wallPage` 一律是过滤副本；PIXIV 域与「全部+无子源」原样透传（零过滤成本）。
    val wallPage = if (domain == GalleryHomeDomain.BOORU) {
        desktopFilteredWallPage(wallPage, booruRatingFilter, booruSiteFilter)
    } else {
        wallPage
    }
    // 作品页左右箭头要翻的那一列 = 这面墙此刻的卡序（pixiv 且点得开的卡才入账）。
    // 只在点击那一刻被读一次；不进组合状态，也不引起重组。
    val trailCards = wallPage?.cards.orEmpty()
    LaunchedEffect(trailCards) { DesktopPixivWorkTrail.publishWallCards(trailCards) }
    // 按实际测量内容物理像素宽度计算主墙列数（4~7 列），首帧未测量前按窗宽兜底，避免系统缩放导致少列。
    val windowWidth = com.venera.compose.components.LocalWindowWidth.current ?: 1280.dp
    var measuredPx by remember { mutableIntStateOf(0) }
    val shellContentWidth = (
        if (measuredPx > 0) {
            measuredPx.toFloat()
        } else {
            with(androidx.compose.ui.platform.LocalDensity.current) {
                (windowWidth -
                    com.venera.desktop.gallery.ui.DesktopGalleryMetrics.railWidth -
                    com.venera.desktop.gallery.ui.DesktopGalleryMetrics.paneWidth).toPx()
            }
        }
    ).coerceAtLeast(0f)
    // 两档布局都走同一张档位表 + 密度偏置（[DesktopWallSpec.columnsFor]）；稿不设右宽，
    // 列数直接吃整条内容宽（不再拿 Browse 上限钳）。此前 Grid 档写死 `wallDensity.columns`
    // （7/6/5 常数），窗口拖宽一颗都不加列 —— 等宽网格因此恒停在 5 列（宽松档）。
    val columns = com.venera.desktop.gallery.ui.LocalDesktopWallSpec.current
        .columnsFor(shellContentWidth, columnMode)
    // 切换过渡：域 / 排序档 / 榜单档一变，整页从半透明淡回实（只淡入一层 alpha，
    // 不做两墙 crossfade —— 同一颗 hoist 的 gridState 挂两棵懒列表会炸，这是形状决定的下限档）。
    val wallTransitionKey = "${domain}|${sort}|${rankingMode}"
    val pageFade = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(wallTransitionKey) {
        pageFade.snapTo(0.45f)
        pageFade.animateTo(1f, androidx.compose.animation.core.tween(240))
    }
    // 浮动刷新钮的自转：按一记 `fabTurns++`，`animateFloatAsState` 把它转成一整圈（按压反馈的形状）。
    var fabTurns by remember { mutableIntStateOf(0) }
    val fabRotation by androidx.compose.animation.core.animateFloatAsState(
        targetValue = fabTurns * 360f,
        animationSpec = androidx.compose.animation.core.tween(500),
        label = "galleryFabSpin",
    )

    // ── 主墙多选坞：状态与批量动作都住 scaffold（选择账仍由壳根持有）──
    val batchScope = rememberCoroutineScope()
    val batchContext = coil3.compose.LocalPlatformContext.current
    val cardActions = com.venera.desktop.gallery.ui.LocalDesktopGalleryCardActions.current
    var batchDialogOpen by remember { mutableStateOf(false) }
    var batchBusy by remember { mutableStateOf(false) }
    var batchProgress by remember {
        mutableStateOf<com.venera.desktop.gallery.ui.selection.DesktopGalleryBatchProgress?>(null)
    }
    var pendingConfirm by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingConfirmCount by remember { mutableIntStateOf(0) }
    val selectedSet = wallSelectedUids.toSet()
    val visibleCards = wallPage?.cards ?: emptyList()
    val selectedCards = visibleCards.filter { card ->
        selectionIdentity(card) in selectedSet
    }
    val visibleUids = remember(visibleCards) {
        visibleCards.map(::selectionIdentity).filter { it.isNotBlank() }.toSet()
    }

    fun runBatchDownload(
        cards: List<DesktopWallCardModel>,
        quality: com.venera.compose.gallery.domain.PixivImageQuality,
        skipExisting: Boolean,
        byAuthor: Boolean,
    ) {
        if (batchBusy || cards.isEmpty()) return
        val paths = galleryPaths
        val prefs = galleryPrefs
        val dir = if (paths == null) null else com.venera.desktop.gallery.data.desktopResolveSaveDir(paths, prefs)
        if (paths == null || dir == null) {
            cardActions?.onNotify("未配置下载目录")
            return
        }
        batchBusy = true
        batchProgress = null
        batchScope.launch {
            val outcome = runCatching {
                com.venera.desktop.gallery.ui.selection.desktopGalleryBatchDownload(
                    context = batchContext,
                    request = com.venera.desktop.gallery.ui.selection.DesktopGalleryBatchDownloadRequest(
                        posts = cards.mapNotNull { it.sourcePost },
                        quality = quality,
                        skipExisting = skipExisting,
                        byAuthor = byAuthor,
                        dir = dir,
                    ),
                    paths = paths,
                    pixiv = ports.pixiv,
                    bearer = ports.pixivAccount?.bearerOrNull(),
                ) { progress -> batchProgress = progress }
            }.getOrElse { com.venera.desktop.gallery.ui.selection.DesktopGalleryBatchDownloadOutcome(0, 0, cards.size) }
            batchBusy = false
            batchProgress = null
            cardActions?.onNotify(outcome.reading)
            onClearWallSelect()
        }
    }

    fun runBatchFavorite(cards: List<DesktopWallCardModel>) {
        val actions = cardActions ?: return
        val posts = cards.mapNotNull { it.sourcePost }
        if (posts.isEmpty()) return
        batchScope.launch {
            val (added, failed) = com.venera.desktop.gallery.ui.selection.desktopGalleryBatchFavoriteAdd(
                posts = posts,
                isFavored = { post ->
                    if (post.site == com.venera.compose.gallery.data.GallerySite.PIXIV) {
                        post.id in actions.pixivBookmarkedIds
                    } else {
                        post.uid in actions.favoriteUids
                    }
                },
                addFavorite = actions.onAddFavorite,
            )
            actions.onNotify(
                buildString {
                    append("已加入 $added 项收藏")
                    if (failed > 0) append(" · 失败 $failed 项")
                },
            )
            onClearWallSelect()
        }
    }

    fun copyLinks(cards: List<DesktopWallCardModel>, kind: com.venera.desktop.gallery.ui.selection.DesktopGalleryLinkKind) {
        val posts = cards.mapNotNull { it.sourcePost }
        val text = if (posts.isNotEmpty()) {
            com.venera.desktop.gallery.ui.selection.desktopGalleryLinkText(posts, kind)
        } else {
            cards.joinToString("\n") { it.url }
        }
        if (text.isBlank()) return
        val sel = java.awt.Toolkit.getDefaultToolkit().systemClipboard
        sel.setContents(java.awt.datatransfer.StringSelection(text), null)
        cardActions?.onNotify("已复制 ${cards.size} 项${kind.label}")
    }

    fun confirmThen(count: Int, run: () -> Unit) {
        pendingConfirmCount = count
        pendingConfirm = run
    }

    // uid → 视图投影（共享页的 `onOpen` 只交回 `GalleryPost`，看图屏要的渲染输入从这查 ——
    // 两枚同源自同一颗 `wallPage`，这里不重判任何一档；打码那张的投影 `masked = true`，
    // `openable` 闸照旧在卡与看图屏两头都在，A 条口径不松动）。
    val modelByUid: Map<String, DesktopWallCardModel> =
        (wallPage?.cards ?: emptyList()).associateBy { it.uid }
    val dockHazeState = rememberHazeState()
    // 图库主墙近尾加载更多由 DesktopJustifiedWall 触发，仍受续页闸门控制。
    androidx.compose.runtime.CompositionLocalProvider(LocalSelectionDockHazeState provides dockHazeState) {
    Box(
        modifier = Modifier
            // 稿不设右宽：画廊主墙吃满整条内容轴（`widthIn(max=…)` 摘掉，主墙才能随窗口长到 7 列）。
            // 量这一格自己的宽 —— 它就是墙真正可用的宽（侧栏占多少、上限钳没钳，全都自然算进去）。
            .fillMaxSize()
            .onSizeChanged { measuredPx = it.width },
    ) {
        // 主墙复用共享 GalleryForYouPage（下拉刷新、飞行截帧与长按动作表在桌面传 null），外包 Box 承载切域淡入与悬浮多选栏模糊源。
        Box(Modifier.fillMaxSize().hazeSource(dockHazeState, zIndex = 0f)) {
        // 图库站：主墙布局双档（设置自选）—— 两档共用同一份 header 槽内容
        // （顶栏 / 筛选架 / 标签维度 / 首页横栏 / 墙头 / 页尾说明，随各自布局器整页滚）。
        val wallHeader: @Composable () -> Unit = {
                // 首页头部、chrome、关注画师与每日热门收进共享页 hero 槽随主墙同滚；chrome 复用 GalleryHomeDomainChips。
                val pixivSignedIn = ports.pixivAccount?.authState?.value is
                    com.venera.compose.gallery.data.PixivAuthState.SignedIn
                // 顶栏稿：筛选架与状态行全部收进 44dp 工具条（两站同构），
                // 标签维度舱降为内容区第一块 —— 顶部占高从图库站 144dp / Pixiv 80dp 统一到 44dp。
                val booruSites = remember { GallerySite.entries.filter { it != GallerySite.PIXIV } }
                val perSiteCounts = (wallPage?.batch?.posts ?: emptyList())
                    .groupBy { it.site }
                    .mapValues { (_, posts) -> posts.size }
                Column(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(tokens.spacing.space3))
                    DesktopGalleryChromeRow(
                        domain = domain,
                        onPickDomain = onPickDomain,
                        pixivSignedIn = pixivSignedIn,
                        topSearch = topSearch,
                        // 与右下浮动刷新钮同一记扳机。
                        onRefresh = onRefresh,
                        onOpenAccount = onOpenPixivAccount,
                        // 原筛选架三件过滤 → 三个 pop-up（子源按真接入的 booru 站动态生成）。
                        rating = booruRatingFilter,
                        onPickRating = onPickBooruRating,
                        sort = sort,
                        onPickSort = onPickSort,
                        booruSites = booruSites,
                        pickedSite = booruSiteFilter,
                        onPickSite = onPickBooruSite,
                        // 布局 / 密度不进顶栏：全仓只留设置页那一处（八枚 setter 的唯一落点）。
                        // 原状态行 → 工具条读数位（同一本 DesktopGalleryBatch 真账）。
                        perSiteCounts = perSiteCounts,
                        failures = wallPage?.batch?.failures ?: emptyMap(),
                        visibleCount = wallPage?.cards?.size ?: 0,
                        pixivWallRecommended = wallPage?.isRecommended == true,
                    )
                    if (domain == GalleryHomeDomain.BOORU) {
                        // 顶栏稿：标签维度舱不再占顶栏，降为内容区第一块
                        //（「尚未载入，点击加载」是空态文案，不该常驻工具条）。
                        Spacer(Modifier.height(tokens.spacing.space3))
                        DesktopGalleryTagDimensions(
                            groups = tagDimensions,
                            dictionaryReady = tagDictionaryReady,
                            onOpenTag = onSearchTag,
                        )
                        Spacer(Modifier.height(tokens.spacing.space2))
                    }
                    Spacer(Modifier.height(tokens.spacing.space3))
                    DesktopGalleryHomeRows(
                        domain = domain,
                        followedArtists = followedArtists,
                        onOpenArtist = onOpenArtist,
                        dailyCards = dailyCards,
                        dailyColumnCount = columns,
                        rankingMode = rankingMode,
                        onPickRankingMode = onPickRankingMode,
                        onOpenPost = onOpenPost,
                        onOpenAccount = onOpenPixivAccount,
                        onOpenDaily = onOpenDaily,
                        pixivSignedIn = pixivSignedIn,
                        // Pixiv 榜单预览条走**独立的匿名榜单腿**（壳里按档取、缓存）——
                        // 从前从主墙截 20 张，主墙换推荐腿后预览条会顶着「日榜预览」摆推荐（假读数）。
                        previewCards = previewCards,
                        previewAvatars = previewAvatars,
                        previewTitles = previewTitles,
                        // 发现带（标签聚合自当轮墙账）+ 关注新作登录腿。
                        hotTags = remember(wallPage) {
                            desktopHotTagsOf(wallPage?.batch?.posts ?: emptyList())
                        },
                        onOpenTag = onSearchTag,
                        recommendedUsers = if (pixivDiscover.ready && pixivDiscover.users.isNotEmpty()) {
                            pixivDiscover.users
                        } else {
                            null
                        },
                        followedUserIds = pixivDiscover.followedIds,
                        onFollowUser = onTogglePixivFollow,
                        onOpenUser = onSearchUser,
                        followingCards = pixivDiscover.followingCards,
                        followingAvatars = pixivDiscover.followingAvatars,
                        pixivisionArticles = pixivisionArticles,
                        onOpenPixivisionArticle = onOpenPixivisionArticle,
                    )
                    Spacer(Modifier.height(tokens.spacing.space2))
                    DesktopGalleryWallHead(
                        domain = domain,
                        rankingModeLabel = rankingMode.label,
                        columns = columns,
                        // PIXIV 墙头按真腿起题：推荐腿上墙 = 「为你推荐 FOR YOU」；
                        // 落回榜单 = 「{档名}作品墙 RANKING」——屏上认得出当前是哪条腿。
                        recommended = wallPage?.isRecommended == true,
                        layout = wallLayout,
                    )
                    if (wallRefreshing && wallPage != null) {
                        // 手动刷新：旧墙留屏，只在墙头下方亮一条细进度（首屏加载态不走这里）。
                        androidx.compose.material3.LinearProgressIndicator(
                            color = tokens.color.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = tokens.spacing.screenHorizontal,
                                    vertical = tokens.spacing.space1,
                                ),
                        )
                    }
                    footer.lines.forEach { line ->
                        Text(
                            text = line,
                            fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                            color = tokens.color.textTertiary,
                            modifier = Modifier.padding(
                                start = tokens.spacing.screenHorizontal,
                                end = tokens.spacing.screenHorizontal,
                                bottom = tokens.spacing.space1,
                            ),
                        )
                    }
                }
        }
        if (wallLayout == com.venera.compose.gallery.domain.GalleryWallLayout.Grid) {
            // Grid 档：等宽网格 · 大间隔（1:1 方图 + 横图跨双格）。
            // 组合期取交接槽句柄：卡上画师跳转的 lambda 不是 composable 上下文，不能在里面读 Local。
            val wallSearchHandoff = LocalDesktopSearchHandoff.current
            com.venera.desktop.gallery.ui.DesktopJustifiedWall(
                cards = wallPage?.cards ?: emptyList(),
                avatars = wallPage?.avatars ?: emptyMap(),
                titles = wallPage?.titles ?: emptyMap(),
                columns = columns,
                selectedUids = wallSelectedUids.toSet(),
                onToggleSelect = onToggleWallSelect,
                onOpenPost = onOpenPost,
                onReachEnd = { onLoadMore() },
                state = gridState,
                placeholder = {
                if (wallPage == null) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 96.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.material3.CircularProgressIndicator(color = tokens.color.primary)
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 96.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(tokens.spacing.space3),
                    ) {
                        Text(
                            text = "暂无内容",
                            fontSize = VeneraType.cardTitle.size, lineHeight = VeneraType.cardTitle.lineHeight,
                            fontWeight = tokens.type.weightBold,
                            color = tokens.color.textPrimary,
                        )
                        Text(
                            text = "稍后重试",
                            fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                            color = tokens.color.textTertiary,
                        )
                        if (footer.moreLabel != null) {
                            DesktopPushButton(onClick = onLoadMore) {
                                Text(footer.moreLabel)
                            }
                        }
                    }
                }
                },
                header = wallHeader,
                footer = footer,
                onLoadMore = onLoadMore,
                onSearchTag = onSearchTag,
                // 卡片画师名点击按所属站送入搜索交接槽（uid 为空时不接）。
                onOpenCardArtist = { card ->
                    if (card.uid.isNotBlank()) {
                        wallSearchHandoff(
                            com.venera.compose.gallery.data.GallerySite.entries.toList(),
                            card.title,
                        )
                    }
                },
            )
        } else {
            // 瀑布流档保留原图比例零裁切，组合期读取交接槽句柄供点击回调使用。
            val wallSearchHandoff = LocalDesktopSearchHandoff.current
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = pageFade.value }) {
            com.venera.desktop.gallery.ui.DesktopStaggeredWall(
                cards = wallPage?.cards ?: emptyList(),
                header = { wallHeader() },
                avatars = wallPage?.avatars ?: emptyMap(),
                titles = wallPage?.titles ?: emptyMap(),
                columns = columns,
                selectedUids = wallSelectedUids.toSet(),
                onToggleSelect = onToggleWallSelect,
                onOpenPost = onOpenPost,
                onSearchTag = onSearchTag,
                onOpenCardArtist = { card ->
                    if (card.uid.isNotBlank()) {
                        wallSearchHandoff(
                            com.venera.compose.gallery.data.GallerySite.entries.toList(),
                            card.title,
                        )
                    }
                },
                onReachEnd = { onLoadMore() },
                state = staggeredState,
                placeholder = {
                    if (wallPage == null) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 96.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            androidx.compose.material3.CircularProgressIndicator(color = tokens.color.primary)
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 96.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(tokens.spacing.space3),
                        ) {
                            Text(
                                text = "暂无内容",
                                fontSize = VeneraType.cardTitle.size, lineHeight = VeneraType.cardTitle.lineHeight,
                                fontWeight = tokens.type.weightBold,
                                color = tokens.color.textPrimary,
                            )
                            Text(
                                text = "稍后重试",
                                fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                                color = tokens.color.textTertiary,
                            )
                            if (footer.moreLabel != null) {
                                DesktopPushButton(onClick = onLoadMore) {
                                    Text(footer.moreLabel)
                                }
                            }
                        }
                    }
                },
                footer = footer,
                onLoadMore = onLoadMore,
            )
            }
        }
        // ── 多选悬浮坞：底部居中 24dp，动作全接真口（常驻挂载由内部 AnimatedVisibility 控显隐，保证 Haze 与退场动画稳定）──
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    bottom = com.venera.desktop.gallery.ui.selection.DesktopSelectionDockBottomInset,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val progress = batchProgress
            if (batchBusy && progress != null) {
                com.venera.desktop.gallery.ui.selection.DesktopBatchProgressLine(progress = progress)
            }
            com.venera.desktop.gallery.ui.selection.GalleryMultiSelectDock(
                selectedCount = selectedCards.size,
                totalCount = visibleUids.size,
                onSelectAll = {
                    if (visibleUids.isNotEmpty()) onSetWallSelection(selectedSet + visibleUids)
                },
                onInvertSelect = {
                    onSetWallSelection((selectedSet - visibleUids) + (visibleUids - selectedSet))
                },
                onClearSelect = onClearWallSelect,
                onBatchDownload = {
                    val run = {
                        runBatchDownload(
                            selectedCards,
                            com.venera.compose.gallery.domain.PixivImageQuality.ORIGINAL,
                            skipExisting = true,
                            byAuthor = false,
                        )
                    }
                    if (selectedCards.size >
                        com.venera.desktop.gallery.ui.selection.DESKTOP_GALLERY_CONFIRM_DOWNLOAD_THRESHOLD
                    ) {
                        confirmThen(selectedCards.size, run)
                    } else {
                        run()
                    }
                },
                onBatchFavorite = { runBatchFavorite(selectedCards) },
                onCopyLinks = {
                    val run = {
                        copyLinks(
                            selectedCards,
                            com.venera.desktop.gallery.ui.selection.DesktopGalleryLinkKind.SITE,
                        )
                    }
                    if (selectedCards.size >
                        com.venera.desktop.gallery.ui.selection.DESKTOP_GALLERY_CONFIRM_COPY_THRESHOLD
                    ) {
                        confirmThen(selectedCards.size, run)
                    } else {
                        run()
                    }
                },
                onOpenMoreModal = { batchDialogOpen = true },
            )
        }
        if (batchDialogOpen && selectedCards.isNotEmpty()) {
            com.venera.desktop.gallery.ui.selection.GalleryBatchActionDialog(
                cards = selectedCards,
                initialTab = 0,
                onDismiss = { batchDialogOpen = false },
                onRemoveCard = { uid ->
                    onSetWallSelection(selectedSet - uid)
                },
                onConfirmDownload = { quality, skipExisting, byAuthor ->
                    batchDialogOpen = false
                    val run = { runBatchDownload(selectedCards, quality, skipExisting, byAuthor) }
                    if (selectedCards.size >
                        com.venera.desktop.gallery.ui.selection.DESKTOP_GALLERY_CONFIRM_DOWNLOAD_THRESHOLD
                    ) {
                        confirmThen(selectedCards.size, run)
                    } else {
                        run()
                    }
                },
                onConfirmFavorite = {
                    batchDialogOpen = false
                    runBatchFavorite(selectedCards)
                },
                onCopySelectedUrls = { kind ->
                    batchDialogOpen = false
                    val run = { copyLinks(selectedCards, kind) }
                    if (selectedCards.size >
                        com.venera.desktop.gallery.ui.selection.DESKTOP_GALLERY_CONFIRM_COPY_THRESHOLD
                    ) {
                        confirmThen(selectedCards.size, run)
                    } else {
                        run()
                    }
                },
            )
        }
        pendingConfirm?.let { run ->
            com.venera.desktop.gallery.ui.selection.DesktopBatchConfirmDialog(
                title = "确认批量操作",
                message = "已选 $pendingConfirmCount 项，超过单次建议数量，继续吗？",
                confirmText = "继续",
                onConfirm = {
                    pendingConfirm = null
                    run()
                },
                onDismiss = { pendingConfirm = null },
            )
        }
        // 右下角浮动刷新钮触发 wallRefreshTick++ 强制重取当前墙与每日热门并覆盖缓存，点击自转一圈作反馈。
        GalleryRefreshFab(
            rotation = fabRotation,
            onClick = {
                fabTurns++
                onRefresh()
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = tokens.spacing.space10,
                    bottom = tokens.spacing.space10,
                ),
        )
        // 主人口径：滚过约两屏浮现的「回到顶部」悬浮钮（与刷新钮同一排，坐在它左边）。
        com.venera.desktop.ui.DesktopBackToTopFab(
            state = gridState,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = tokens.spacing.space10 + DesktopGalleryMetrics.refreshFabSize + tokens.spacing.space3,
                    bottom = tokens.spacing.space10,
                ),
        )
        }
    }
    }
}

/**
 * 画廊首页右下角那枚浮动刷新钮。
 *
 * 圆形玻璃钮：`surfaceContainerHigh` 底 + 发丝描边 + 投影，悬停时描边与图标提亮，
 * 按下一记 0.94 → 1 回弹（`rotation` 由调用点那颗 `animateFloatAsState` 喂，自转一整圈）。
 * 两域共用同一颗：图库站与 Pixiv 都需要"刷新当前档整墙"，落点各由调用点在壳里接好。
 */
@Composable
private fun GalleryRefreshFab(
    rotation: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        label = "galleryFabPress",
    )
    Box(
        modifier = modifier
            .size(DesktopGalleryMetrics.refreshFabSize)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(10.dp, CircleShape)
            .clip(CircleShape)
            .background(tokens.color.surfaceContainerHigh)
            .border(
                DesktopGalleryMetrics.hairline,
                if (hovered) tokens.color.outline else tokens.color.outlineVariant,
                CircleShape,
            )
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Refresh,
            contentDescription = "刷新",
            tint = if (hovered) tokens.color.textPrimary else tokens.color.textSecondary,
            modifier = Modifier.size(20.dp).rotate(rotation),
        )
    }
}
