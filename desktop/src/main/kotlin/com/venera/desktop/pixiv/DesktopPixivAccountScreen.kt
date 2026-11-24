package com.venera.desktop.pixiv

import com.venera.desktop.ui.theme.VeneraType
import com.venera.desktop.ui.DesktopPushButton
import com.venera.desktop.app.LocalShellBackHandled

import com.venera.desktop.gallery.ui.GalleryTokens
import com.venera.desktop.gallery.ui.columnsFor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.venera.compose.components.venera.VeneraCard
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.components.venera.VeneraCoverMask
import com.venera.compose.gallery.data.GalleryPixivWorks
import com.venera.desktop.gallery.data.DesktopGalleryPreferences
import com.venera.compose.components.venera.VeneraDialog
import com.venera.compose.gallery.domain.PixivImageQuality
import com.venera.compose.gallery.data.PixivAppPage
import com.venera.compose.gallery.data.PixivCredentials
import com.venera.compose.gallery.data.PixivRestrict
import com.venera.compose.gallery.data.PixivWork
import com.venera.compose.gallery.domain.GalleryHomeDomain
import com.venera.compose.gallery.domain.GalleryPreviewQuality
import com.venera.compose.gallery.domain.PixivReadScope
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.gallery.ui.desktopWallCardOf
import com.venera.desktop.ui.GridPageTemplate
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.fillMaxHeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.venera.compose.gallery.data.PixivUserHead

/**
 * 桌面 pixiv 那三屏共用的**取数与渲染底座**（主人 goal「pixiv 所有界面和登录也要做好」）。
 *
 * ## 为什么桌面自搭而不是转交对岸那三颗屏
 *
 * 安卓 `PixivAccountScreen` / `PixivArtistScreen` / `PixivWorkScreen` 共 6251 行，
 * 全部绑 `Context` / `Toast` / `PredictiveBackHandler` / `data.prefs`（③ 档，桌面编不过，
 * `desktop/build.gradle.kts` 的排除清单里逐颗点名）。判据面能共享的共享
 * （`PixivAccountGate` / `PixivReadScope.legs` / `mergePixivLegs` / `PixivWorkPage` 那批纯件
 * 都在桌面编译面），**UI 骨架**桌面自搭：账号页三段主档 + 画师页头 + 作品页，
 * 全部只吃形参（`DesktopUiIsolationTest` ② 那条），数据从 `GalleryPorts.pixiv` /
 * `pixivAccount` 那两枚已接线的端口现取。
 *
 * ## 打码
 *
 * pixiv 作品没有 booru 那种 `rating` 值，遮罩判据只有「登录态缺票据 / 站方 x_restrict」
 * 那几道 —— 桌面这批**不新造判据**：卡照 `desktopWallCardOf` 投影，`masked` 由调用点递
 * （壳里那条 `ux.coverMaskStateOf` 链）；墙卡点开走与图库同一个 `GalleryPostViewer` 路由。
 */

/** 一面 pixiv 作品墙要的渲染输入（已经按档投影好的卡）。 */
internal data class DesktopPixivWallCard(
    val uid: String,
    val title: String,
    val author: String,
    val url: String,
    val aspect: Float,
    val masked: Boolean,
    val openable: Boolean,
    /** 画师头像（墙卡也带头像）。空串 = 站方没给，退纯文字行。 */
    val avatarUrl: String = "",
    /** 站方原模型（点开要拼 `GalleryPostViewer` 路由）。 */
    val post: com.venera.compose.gallery.data.GalleryPost,
)

/** `PixivWork` → 卡。只投影，不判遮罩（`masked` 由调用点给）。 */
internal fun desktopPixivCardOf(
    work: PixivWork,
    quality: GalleryPreviewQuality = GalleryPreviewQuality.PREVIEW,
    masked: Boolean = false,
): DesktopPixivWallCard {
    val card = desktopWallCardOf(work.post, quality, masked)
    return DesktopPixivWallCard(
        uid = work.post.uid,
        title = work.title.ifBlank { work.post.author.ifBlank { "pixiv #${work.post.id}" } },
        author = work.post.author,
        url = card.url,
        aspect = card.aspect,
        masked = masked,
        openable = true,
        avatarUrl = work.authorAvatarUrl,
        post = work.post,
    )
}


/**
 * 「全部 = 公开 + 非公开两趟再合」那一条腿（判据吃共享的 `PixivReadScope.legs` 与
 * `mergePixivLegs`，这里只把 `PixivAppPage` 那一层摊开）。
 */
internal suspend fun desktopPixivScopedFeed(
    bearer: String,
    scope: PixivReadScope,
    fetch: suspend (PixivRestrict, String?) -> Result<PixivAppPage>,
): Result<List<PixivWork>> = coroutineScope {
    val results = scope.legs.map { restrict -> async { fetch(restrict, null) } }.map { it.await() }
    val firstErr = results.firstOrNull { it.isFailure }?.exceptionOrNull()
    if (results.all { it.isFailure } && firstErr != null) {
        return@coroutineScope Result.failure(firstErr)
    }
    val merged = com.venera.compose.gallery.domain.mergePixivLegs(
        results.map { it.getOrNull()?.works.orEmpty() },
    ) { it.post.uid }
    Result.success(merged)
}

/**
 * 账号页的**闸门句**。判据吃共享那颗 `pixivAccountGate`（internal 于 `gallery/domain`，
 * 同一编译面内直接可调），这里只把它摊成屏上要的三格（句子 / 要不要登录钮 / 要不要重试）。
 */
internal data class DesktopPixivGateLine(val line: String?, val showLogin: Boolean, val showRetry: Boolean)

internal fun desktopPixivGateLine(
    signedIn: Boolean,
    userId: String?,
    bearerReady: Boolean,
): DesktopPixivGateLine = when {
    !signedIn -> DesktopPixivGateLine(
        "登录 pixiv 账号之后才有。你关注的人的动态、你的收藏、你的关注列表都存在 pixiv 账号里。",
        showLogin = true,
        showRetry = false,
    )
    userId.isNullOrBlank() -> DesktopPixivGateLine(
        "这一版没记下你的 pixiv 用户号，重新登录一次就能读出来。",
        showLogin = true,
        showRetry = false,
    )
    !bearerReady -> DesktopPixivGateLine(
        "pixiv 这会儿没接上，稍后再试。如果一直这样，再重新登录一次。",
        showLogin = false,
        showRetry = true,
    )
    else -> DesktopPixivGateLine(null, showLogin = false, showRetry = false)
}

/**
 * pixiv 作品墙（账号页三段 / 画师页作品段共用同一颗渲染件）。
 *
 * 瀑布流（staggered）+ 作者行卡 —— 对齐安卓大屏「我的 pixiv」截图那面墙。
 */
@Composable
internal fun DesktopPixivWall(
    cards: List<DesktopPixivWallCard>,
    /** 点一张卡（壳接路由进看图屏）。 */
    onOpen: (DesktopPixivWallCard) -> Unit,
    /** 页尾那枚「下一页」；`null` = 到底 / 没有下一页 ⇒ 不摆。 */
    moreLabel: String? = null,
    onLoadMore: (() -> Unit)? = null,
    /** 空墙时念的那一句（成因/闸门句）。`null` 且 cards 空 = 念「这里还没有内容」。 */
    emptyLine: String? = null,
    /**
     * 页头槽：画师页的页头（返回/名/关注/横幅/统计/段位）作为**网格的第 0 号
     * FullLine item** 进同一颗懒容器 —— 不能包在外层 `Column.verticalScroll` 里再嵌懒网格，
     * 那会拿到无限高约束直接炸（主人报的独立画师页 Error 弹窗就是这一形）。
     * `null` = 无页头（账号页三段自带头部，与从前一致）。
     */
    header: (@Composable () -> Unit)? = null,
    /**
     * 点卡上画师名 → 进画师详情页（桌内跳转）；null = 回退浏览器开画师主页
     *（DesktopGalleryCard 的既有回退）。画师 id 从 post.source 的 users/{id} 提，
     * 提不出那一格回退浏览器，不进死跳。
     */
    onOpenArtist: ((Long, String) -> Unit)? = null,
    /**
     * 滚动状态外置：画师页传壳层 retained 锚点 state，进作品返回不丢位；
     * 不传时内部自建（账号页等无返回恢复诉求的用法）。
     */
    gridState: androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState =
        androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState(),
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    // 作品页左右箭头要翻的那一列 = 这面 pixiv 墙此刻的卡序（打码 / 点不开的卡不入账）。
    androidx.compose.runtime.LaunchedEffect(cards) {
        com.venera.desktop.data.DesktopPixivWorkTrail.publishPixivWallCards(cards)
    }
    val windowWidth = com.venera.compose.components.LocalWindowWidth.current ?: 1280.dp
    // pixiv 账号墙也吃统一规格的密度（列数偏置 + 列距）。
    val wallSpec = com.venera.desktop.gallery.ui.LocalDesktopWallSpec.current
    // 列数改走统一网格密度档（固定 x行x列 钉死 / 自适应 3-6 列）。
    val columns = wallSpec.columnsFor(
        with(androidx.compose.ui.platform.LocalDensity.current) { windowWidth.toPx() },
        com.venera.compose.gallery.domain.GalleryColumnMode.AUTO,
    ).coerceAtLeast(2)
    val searchHandoff = com.venera.desktop.gallery.ui.LocalDesktopSearchHandoff.current
    // 主人口径：页尾「下一页」钮撤掉 —— 近尾自动续（下面那颗观察器）+ 右下角回顶钮。
    // 主人点名 pixiv 拿得太快：续页之间加 800ms 冷却（对齐 pixez 的队列节流口径），
    // 快速滚动时不会一秒内连发好几跳 app-api。
    com.venera.desktop.ui.DesktopStaggeredAutoLoadEffect(
        state = gridState,
        columns = columns,
        enabled = moreLabel != null && onLoadMore != null,
        cooldownMs = 800L,
        onLoadMore = { onLoadMore?.invoke() },
    )
        androidx.compose.foundation.layout.Box(modifier = modifier.fillMaxSize()) {
        GridPageTemplate(
            items = cards,
            columns = columns,
            itemKey = { it.uid },
            state = gridState,
            modifier = Modifier,
            header = header,
            contentPadding = PaddingValues(
                start = tokens.spacing.screenHorizontal,
                end = tokens.spacing.screenHorizontal,
                bottom = tokens.spacing.space6,
            ),
            spacing = wallSpec.wallGap,
            emptyContent = {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = emptyLine ?: "这里还没有内容。",
                        fontSize = VeneraType.body.size, lineHeight = VeneraType.body.lineHeight,
                        color = tokens.color.textTertiary,
                        textAlign = TextAlign.Center,
                    )
                }
            },
        ) { card ->
            // 入场浮动：新一页的卡片落地时自下轻浮 + 淡入；
            // 已缓存的图这一跳 260ms 内完成，观感就是"加载完了浮上来"。
            var rise by remember(card.uid) { mutableStateOf(false) }
            androidx.compose.runtime.LaunchedEffect(card.uid) { rise = true }
            val riseProgress by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (rise) 1f else 0f,
                animationSpec = androidx.compose.animation.core.tween(260),
                label = "pixiv-card-rise",
            )
            // pixiv 账号墙退役自绘卡，走 DesktopGalleryCard 唯一规格 ——
            // 浮动 / 右键 / 三态描边 / 信息栏与主墙、搜索、收藏、日榜逐项对齐。
            com.venera.desktop.gallery.ui.DesktopGalleryCard(
                card = com.venera.desktop.gallery.ui.desktopWallCardOf(
                    card.post,
                    com.venera.desktop.gallery.ui.DESKTOP_WALL_QUALITY,
                    masked = card.masked,
                ),
                // 站方原题（主人对齐网页版）：post 的 title 在收藏/动态腿上是空的，
                // 不覆盖就会落「Pixiv #编号」占位 —— `desktopPixivCardOf` 里那份真题在这一格接上。
                titleOverride = card.title.takeIf { it.isNotBlank() && !it.startsWith("pixiv #") },
                aspect = if (wallSpec.layout ==
                    com.venera.compose.gallery.domain.GalleryWallLayout.Grid
                ) {
                    if (com.venera.desktop.gallery.ui.desktopGridCardIsWide(
                            com.venera.desktop.gallery.ui.desktopWallCardOf(card.post)
                        )
                    ) 2f else 1f
                } else {
                    card.aspect
                },
                onOpenPost = { if (card.openable) onOpen(card) },
                onSearchArtist = { label ->
                    searchHandoff(
                        listOf(card.post.site),
                        label,
                    )
                },
                // 点画师名进桌内画师详情页；source 里提不出画师 id 的卡回退浏览器
                //（DesktopGalleryCard 的 onOpenArtist=null 语义）。
                onOpenArtist = onOpenArtist?.let { cb ->
                    val artistId = com.venera.compose.gallery.domain.GalleryArtistLinks
                        .pixivUserId(card.post.source)
                    if (artistId != null) {
                        { cb(artistId, card.author.ifBlank { card.title }.trim()) }
                    } else {
                        null
                    }
                },
                onToggleSelect = null,
                avatarUrl = card.avatarUrl.takeIf { it.isNotBlank() },
                modifier = Modifier.graphicsLayer {
                    alpha = riseProgress
                    translationY = (1f - riseProgress) * 26.dp.toPx()
                },
            )
        }
        // 滚过约两屏浮现的「回到顶部」悬浮钮。
        com.venera.desktop.ui.DesktopBackToTopFab(
            state = gridState,
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.BottomEnd)
                .padding(tokens.spacing.space4),
        )
        }
    }

/**
 * 「我的 pixiv」账号页（对应安卓 `PixivAccountScreen` 的三段主档）。
 *
 * 屏只吃形参与**端口接口**（`GalleryPixivWorks` / `PixivCredentials`），不取静态口；
 * 三段各一条 `produceState`：关注动态 / 云端收藏 / 我的关注。
 * 顶栏可见性下拉照安卓 `PixivReadScope` 三档；「全部」= 两趟合（判据在共享面）。
 */
@Composable
internal fun DesktopPixivAccountScreen(
    pixiv: GalleryPixivWorks,
    account: PixivCredentials,
    onBack: () -> Unit,
    onOpenLogin: () -> Unit,
    onSignOut: () -> Unit,
    onOpenPost: (com.venera.compose.gallery.data.GalleryPost) -> Unit,
    onOpenArtist: (Long, String) -> Unit,
    /** 画质四类偏好（四类 × 三档；写口在 prefs，弹窗读写）。 */
    qualityPrefs: DesktopGalleryPreferences? = null,
    /**
     * 页签成品值 + 改签回调（主人报「进二级页返回总落回我的作品」）：
     * 状态住壳不 resident 在屏里 —— 离开路由重组时屏内 remember 归零，壳活着状态就活着。
     */
    tabState: Int,
    onTabChanged: (Int) -> Unit,
    /**
     * 珍藏册端口：`null` = 没装，「珍藏册」块退空态。
     * 数据腿是**登录腿** `myBookmarked`（语义 = 收藏的珍藏册；创建的珍藏册无端点）。
     */
    collections: com.venera.compose.gallery.data.GalleryPixivCollections? = null,
    /** 迷你卡点下去进珍藏册详情（必传无默认值）。 */
    onOpenCollection: (com.venera.compose.gallery.data.PixivCollectionCard) -> Unit,
) {
    val tokens = VeneraTokens
    val auth by account.authState.collectAsState()
    var scope by remember { mutableStateOf(PixivReadScope.All) }
    // 用户第 3、4 点：0=我的作品（图二主页），1=关注动态，2=我的收藏，3=已关注画师（图三）
    val tab = tabState
    var qualityOpen by remember { mutableStateOf(false) }

    // 三段各一个 tick（「刷新」/ 切可见档重跑）。
    var tick by remember { mutableStateOf(0) }
    // 内容流是否正在重拉（网页版刷新口径：拉新期间**旧内容留在屏上**，到了再换）。
    var feedRefreshing by remember { mutableStateOf(false) }
    // 最近一次取数成功的时刻（左栏底部状态卡用；`null` = 本会话还没成功过）。
    var lastRefreshAt by remember { mutableStateOf<Long?>(null) }

    val bearerState = produceState<Pair<Boolean, Boolean>>(false to false, auth, tick) {
        // 第一格：量过了没；第二格：量出来的 bearer 在不在。
        val bearer = account.bearerOrNull()
        value = true to (bearer != null)
    }
    val measured = bearerState.value.first
    val bearerReady = bearerState.value.second

    val signed = auth is com.venera.compose.gallery.data.PixivAuthState.SignedIn
    val userId = (auth as? com.venera.compose.gallery.data.PixivAuthState.SignedIn)?.userId
    val gate = desktopPixivGateLine(signedIn = signed, userId = userId, bearerReady = bearerReady && measured)

    // 登录用户的画师头（头像、背景横幅、handle）——用于真头像与个人主页横幅展示
    val userHead = produceState<PixivUserHead?>(null, userId, signed, tick) {
        val uid = userId?.toLongOrNull()
        value = if (signed && uid != null) {
            withContext(Dispatchers.IO) { pixiv.userHead(uid).getOrNull() }
        } else null
    }

    // ── 四段数据（我的作品 / 关注动态 / 云端收藏 / 已关注画师）──
    data class PixivFeedLeg(
        val cards: List<DesktopPixivWallCard> = emptyList(),
        val users: List<com.venera.compose.gallery.data.PixivUser> = emptyList(),
        val nextUrl: String? = null,
        val failed: Boolean = false,
        /**
         * 失败成因的**短句**：`null` = 走屏上那句通用兜底。
         * pixiv 域在国内裸连时是 TLS 握手被重置（`DesktopErrorKind.BLOCKED`），
         * 填进来后屏上直接念「网络受阻 · 需配置代理后重试」，不用用户猜。
         */
        val note: String? = null,
    )
    // `leg` 不吃 tick：按「刷新」= 网页版口径，旧内容留在屏上、后台拉新换上；
    // 换档 / 换可见范围 / 登录态变才是真的换内容，那一刻才清空。
    var leg by remember(tab, scope, bearerReady, signed) { mutableStateOf<PixivFeedLeg?>(null) }
    var moreBusy by remember { mutableStateOf(false) }

    // 账号卡统计四格（登录腿 userDetail，对自己同样成立：插画/漫画/粉丝/收藏；没给的格不摆）。
    val profileDetail = produceState<com.venera.compose.gallery.data.PixivUserDetail?>(null, signed, tick) {
        value = if (!signed || userId == null) {
            null
        } else {
            account.bearerOrNull()?.let { pixiv.userDetail(it, userId).getOrNull() }
        }
    }

    androidx.compose.runtime.LaunchedEffect(tab, scope, tick, bearerReady, signed) {
        feedRefreshing = true
        try {
            if (!signed || userId == null) return@LaunchedEffect
        val bearer = account.bearerOrNull() ?: return@LaunchedEffect
        val restrict = if (scope == PixivReadScope.Private) PixivRestrict.Private else PixivRestrict.Public
        // 刷新口径（网页版对齐）：屏上已有旧内容的那一跳 = 用户按了「刷新」→ 绕盘缓存直拉站方；
        // 首进 / 换档（leg 为空）照常吃缓存，防限流。
        val refreshRound = leg != null
        leg = when (tab) {
            0 -> {
                // 我的作品：画师自己发布的作品（图二样式主页）
                pixiv.userIllusts(bearer, userId, "illust", fresh = refreshRound).getOrNull()?.let { page ->
                    PixivFeedLeg(cards = page.works.map { desktopPixivCardOf(it) }, nextUrl = page.nextUrl)
                } ?: PixivFeedLeg(failed = true)
            }
            1 -> if (scope == PixivReadScope.All) {
                desktopPixivScopedFeed(bearer, scope) { r, next ->
                    pixiv.followIllustFeed(bearer, r, next, fresh = refreshRound)
                }.fold(
                    onSuccess = { works -> PixivFeedLeg(cards = works.map { desktopPixivCardOf(it) }) },
                    onFailure = { PixivFeedLeg(failed = true) },
                )
            } else {
                pixiv.followIllustFeed(bearer, restrict, null, fresh = refreshRound).getOrNull()?.let { page ->
                    PixivFeedLeg(cards = page.works.map { desktopPixivCardOf(it) }, nextUrl = page.nextUrl)
                } ?: PixivFeedLeg(failed = true)
            }
            2 -> pixiv.bookmarkedIllusts(bearer, userId, restrict, fresh = refreshRound).getOrNull()?.let { page ->
                PixivFeedLeg(cards = page.works.map { desktopPixivCardOf(it) }, nextUrl = page.nextUrl)
            } ?: PixivFeedLeg(failed = true)
            else -> pixiv.following(bearer, userId, restrict, fresh = refreshRound).getOrNull()?.let { page ->
                PixivFeedLeg(users = page.users, nextUrl = page.nextUrl)
            } ?: PixivFeedLeg(failed = true)
        }
        if (leg?.failed == false) lastRefreshAt = System.currentTimeMillis()
        } finally {
            feedRefreshing = false
        }
    }

    // 布局档（顶栏切换器要回来）—— 与主墙同一颗偏好，写回 galleryPrefs。
    val fallbackLayout = remember {
        kotlinx.coroutines.flow.MutableStateFlow(
            com.venera.compose.gallery.domain.GalleryWallLayout.Staggered,
        )
    }
    val wallLayout = (qualityPrefs?.wallLayout ?: fallbackLayout).collectAsState().value

    // ── 融合稿（pixiv-fuse-）：左身份 ｜ 中内容（右栏选中面板已撤）──
    // 左栏身份卡吃原来「账号信息栏」那一段的读数；未登录时左栏换成登录引导卡。
    val displayName = (auth as? com.venera.compose.gallery.data.PixivAuthState.SignedIn)?.userName
        ?: userHead.value?.name
        ?: "pixiv 账号"
    val handle = userHead.value?.account?.takeIf { it.isNotBlank() }
    val railStats = listOfNotNull(
        profileDetail.value?.totalFollowers?.let { "粉丝" to "$it" },
        profileDetail.value?.totalIllusts?.let { "插画" to "$it" },
        profileDetail.value?.totalManga?.let { "漫画" to "$it" },
        profileDetail.value?.totalBookmarksPublic?.let { "收藏" to "$it" },
    )

    Row(modifier = Modifier.fillMaxSize()) {
        // 左栏底部状态卡的真实读数（右下选中面板撤掉，左栏底部别空着）
        val feedStateLabel = when {
            !signed -> "未登录"
            leg == null -> "正在取…"
            feedRefreshing -> "刷新中…"
            leg?.failed == true -> "未连接"
            else -> "已连接"
        }
        val lastRefreshLabel = lastRefreshAt?.let {
            java.time.LocalTime.ofInstant(
                java.time.Instant.ofEpochMilli(it),
                java.time.ZoneId.systemDefault(),
            ).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
        }
        PixivIdentityRail(
            signed = signed,
            displayName = displayName,
            handle = handle,
            avatarUrl = userHead.value?.avatarUrl,
            stats = railStats,
            tab = tab,
            onTabChanged = onTabChanged,
            onOpenLogin = onOpenLogin,
            onSignOut = onSignOut,
            onOpenQuality = if (qualityPrefs != null) ({ qualityOpen = true }) else null,
            feedStateLabel = feedStateLabel,
            lastRefreshLabel = lastRefreshLabel,
            onRefresh = { tick++ },
        )
        Column(modifier = Modifier.weight(1f)) {
        // ── 顶栏：返回 + 标题 + 可见性 + 重新取 ──
        Row(
            modifier = Modifier.fillMaxWidth().padding(
                horizontal = tokens.spacing.screenHorizontal,
                vertical = tokens.spacing.space3,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space3),
        ) {
            // 一屏一返回：壳层已摆返回时屏内这枚隐去（全屏页 local=false ⇒ 保留）。
            if (!LocalShellBackHandled.current) {
                com.venera.desktop.ui.DesktopBackButton(onClick = onBack)
            }
            Text(
                text = "我的 pixiv",
                fontSize = VeneraType.pageTitle.size, lineHeight = VeneraType.pageTitle.lineHeight,
                fontWeight = tokens.type.weightBold,
                color = tokens.color.textPrimary,
                modifier = Modifier.weight(1f),
            )
            // 布局切换（等宽 / 瀑布流）：与主墙同一颗偏好；写口在 prefs。
            if (qualityPrefs != null) {
                PixivLayoutSwitch(
                    current = wallLayout,
                    onPick = { qualityPrefs.setWallLayout(it) },
                )
            }
            // 可见性切换在作品/收藏/关注动态时有意义
            if (tab != 0) {
                PixivScopeDropdown(scope = scope, onPick = { scope = it })
            }
        }

        // ── 四段主档 tabs（我的作品 / 关注动态 / 我的收藏 / 已关注）──

        // 画质调节弹窗（四类 × 三档 = 列表预览 / 插画详情 / 漫画详情 / 大图缩放，
        // 各 中等·大图·原图；写画廊偏好，当场推流生效）。
        if (qualityOpen && qualityPrefs != null) {
            VeneraDialog(
                show = true,
                onDismissRequest = { qualityOpen = false },
                title = "画质调节",
            ) {
                val listQ = qualityPrefs.pixivListQuality.collectAsState().value
                val illustQ = qualityPrefs.pixivIllustQuality.collectAsState().value
                val mangaQ = qualityPrefs.pixivMangaQuality.collectAsState().value
                val zoomQ = qualityPrefs.pixivZoomQuality.collectAsState().value
                val rows = listOf(
                    "列表预览" to listQ to { q: PixivImageQuality -> qualityPrefs.setPixivPreviewQuality(q) },
                    "插画详情页" to illustQ to { q: PixivImageQuality -> qualityPrefs.setPixivIllustQuality(q) },
                    "漫画详情页" to mangaQ to { q: PixivImageQuality -> qualityPrefs.setPixivMangaQuality(q) },
                    "大图缩放" to zoomQ to { q: PixivImageQuality -> qualityPrefs.setPixivZoomQuality(q) },
                )
                Column {
                    rows.forEach { (titleAndQ, set) ->
                        val (title, cur) = titleAndQ
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = title,
                                fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                                color = tokens.color.textPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            listOf(PixivImageQuality.MEDIUM, PixivImageQuality.LARGE, PixivImageQuality.ORIGINAL).forEach { q ->
                                Text(
                                    text = when (q) {
                                        PixivImageQuality.SMALL -> "低画质"
                                        PixivImageQuality.MEDIUM -> "中等"
                                        PixivImageQuality.LARGE -> "大图"
                                        PixivImageQuality.ORIGINAL -> "原图"
                                    },
                                    fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                                    fontWeight = if (cur == q) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (cur == q) tokens.color.primary else tokens.color.textTertiary,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(
                                            if (cur == q) tokens.color.primary.copy(alpha = 0.16f)
                                            else androidx.compose.ui.graphics.Color.Transparent,
                                        )
                                        .clickable { set(q) }
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        // ── 闸门句 / 登录把手 ──
        if (gate.line != null) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.space4),
            ) {
                Text(
                    text = gate.line,
                    fontSize = VeneraType.body.size, lineHeight = VeneraType.body.lineHeight,
                    color = tokens.color.textSecondary,
                    textAlign = TextAlign.Center,
                )
                if (gate.showLogin) {
                    com.venera.desktop.ui.DesktopActionButton(
                        text = "登录 pixiv",
                        onClick = onOpenLogin,
                    )
                } else if (gate.showRetry) {
                    com.venera.desktop.ui.DesktopActionButton(
                        text = "重试",
                        onClick = { tick++ },
                    )
                }
            }
            return@Column
        }

        val coroutine = rememberCoroutineScope()
        // 无限下滑（口径）：站方 next_url 游标续；「全部」档没有单腿游标，moreLabel 自然不摆。
        val loadMore: () -> Unit = loadMore@{
            val current = leg ?: return@loadMore
            if (!moreBusy && current.nextUrl != null) {
                moreBusy = true
                coroutine.launch {
                    val bearer = account.bearerOrNull()
                    val restrict = if (scope == PixivReadScope.Private) PixivRestrict.Private else PixivRestrict.Public
                    when (tab) {
                        0 -> bearer?.let {
                            pixiv.userIllusts(it, userId ?: "", "illust", current.nextUrl).getOrNull()
                        }?.let { page ->
                            leg = current.copy(
                                cards = current.cards + page.works.map { desktopPixivCardOf(it) },
                                nextUrl = page.nextUrl,
                            )
                        }
                        1 -> bearer?.let {
                            pixiv.followIllustFeed(it, restrict, current.nextUrl).getOrNull()
                        }?.let { page ->
                            leg = current.copy(
                                cards = current.cards + page.works.map { desktopPixivCardOf(it) },
                                nextUrl = page.nextUrl,
                            )
                        }
                        2 -> bearer?.let {
                            pixiv.bookmarkedIllusts(it, userId ?: "", restrict, current.nextUrl).getOrNull()
                        }?.let { page ->
                            leg = current.copy(
                                cards = current.cards + page.works.map { desktopPixivCardOf(it) },
                                nextUrl = page.nextUrl,
                            )
                        }
                        else -> bearer?.let {
                            pixiv.following(it, userId ?: "", restrict, current.nextUrl).getOrNull()
                        }?.let { page ->
                            leg = current.copy(users = current.users + page.users, nextUrl = page.nextUrl)
                        }
                    }
                    moreBusy = false
                }
            }
        }

        val current = leg
        when (tab) {
            0, 1, 2 -> {
                when {
                    current == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("正在取…", fontSize = VeneraType.body.size, lineHeight = VeneraType.body.lineHeight, color = tokens.color.textTertiary)
                    }
                    current.failed -> PixivFeedFailure(
                        // pixiv 域在国内裸连是 TLS 握手被重置，这句要让人知道该配代理。
                        note = current.note ?: "加载失败",
                        onRetry = { tick++ },
                    )
                    else -> Column(Modifier.fillMaxSize()) {
                        // tab 2 顶部内嵌「珍藏册」块：横排迷你拼贴卡，
                        // 不嵌滚动容器（外面墙是懒网格，verticalScroll 套懒网格会炸高约束）。
                        if (tab == 2) {
                            DesktopPixivAccountCollectionsBlock(
                                collections = collections,
                                userId = userId?.toLongOrNull(),
                                onOpenCollection = onOpenCollection,
                            )
                        }
                        Box(Modifier.weight(1f)) {
                            DesktopPixivWall(
                                cards = current.cards,
                                onOpen = { onOpenPost(it.post) },
                                onOpenArtist = onOpenArtist,
                                moreLabel = if (current.nextUrl != null) {
                                    if (moreBusy) "取更多中…" else "更多"
                                } else {
                                    null
                                },
                                onLoadMore = if (current.nextUrl != null) loadMore else null,
                                emptyLine = when (tab) {
                                    0 -> "还没有发布过作品（或这一档没有公开作品）。"
                                    1 -> "这一档关注动态还没有内容。"
                                    else -> if (scope == PixivReadScope.Private) "这一档还没有内容（私密的本来就少）。" else "这一档还没有收藏。"
                                },
                            )
                        }
                    }
                }
            }
            else -> {
                // ── 用户第 3 点：已关注画师页面改造为图三卡片样式（上 3 图大预览，下头像/名字/关注钮）──
                when {
                    current == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("正在取…", fontSize = VeneraType.body.size, lineHeight = VeneraType.body.lineHeight, color = tokens.color.textTertiary)
                    }
                    current.failed -> PixivFeedFailure(
                        note = current.note ?: "加载失败",
                        onRetry = { tick++ },
                    )
                    current.users.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("还没有关注任何画师。", fontSize = VeneraType.body.size, lineHeight = VeneraType.body.lineHeight, color = tokens.color.textTertiary)
                    }
                    else -> {
                        // ── ：官网**单列行流**（一行一画师 + 右侧作品条×4），
                        // 替换上一轮的三图大卡网格。滚动到底自动续页。
                        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                        androidx.compose.runtime.LaunchedEffect(listState, current) {
                            androidx.compose.runtime.snapshotFlow {
                                val info = listState.layoutInfo
                                (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 2
                            }.distinctUntilChanged().collect { near ->
                                if (near) loadMore()
                            }
                        }
                        androidx.compose.foundation.lazy.LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                horizontal = tokens.spacing.screenHorizontal,
                                vertical = tokens.spacing.space3,
                            ),
                            verticalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
                        ) {
                            lazyItems(current.users, key = { it.id }) { user ->
                                DesktopPixivFollowingRow(
                                    user = user,
                                    pixiv = pixiv,
                                    account = account,
                                    signed = signed,
                                    onOpenArtist = { onOpenArtist(user.id, user.name) },
                                    onOpenPost = onOpenPost,
                                    onUnfollowSuccess = {
                                        leg = leg?.let { cur ->
                                            cur.copy(users = cur.users.filterNot { it.id == user.id })
                                        }
                                    },
                                )
                            }
                            if (current.nextUrl != null) {
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = tokens.spacing.space3),
                                        horizontalArrangement = Arrangement.Center,
                                    ) {
                                        DesktopPushButton(enabled = !moreBusy, onClick = loadMore) {
                                            Text(if (moreBusy) "取更多中…" else "更多")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        }
    }
}


/**
 * 已关注**单列行**（desktop-pixiv-following-artist-.html，
 * 照 pixiv 官网「关注用户」页那一形）：一行一画师 ——
 * 左：头像 + 名字 + @handle + 「已关注」胶囊（真取关，写穿缓存）；
 * 右：该画师最新 **4 张**作品条（吃缓存层，30 分钟内二次进页 0 API；点击进作品详情）。
 * 整行点击进画师主页。站方列表接口不带简介（comment），简介那一格不编。
 */
@Composable
private fun DesktopPixivFollowingRow(
    user: com.venera.compose.gallery.data.PixivUser,
    pixiv: GalleryPixivWorks,
    account: PixivCredentials,
    signed: Boolean,
    onOpenArtist: () -> Unit,
    onOpenPost: (com.venera.compose.gallery.data.GalleryPost) -> Unit,
    onUnfollowSuccess: () -> Unit,
) {
    val tokens = VeneraTokens
    val scope = rememberCoroutineScope()
    var followBusy by remember { mutableStateOf(false) }

    // 该画师最新 4 张（userIllusts 已过缓存层：TTL 内直接回缓存，不真发 API）。
    val previewsState = produceState<List<com.venera.compose.gallery.data.PixivWork>>(emptyList(), user.id) {
        val bearer = account.bearerOrNull()
        if (bearer != null) {
            value = withContext(Dispatchers.IO) {
                pixiv.userIllusts(bearer, user.id.toString(), "illust").getOrNull()?.works?.take(4) ?: emptyList()
            }
        }
    }
    val previews = previewsState.value

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GalleryTokens.Shape.Large)
            .background(tokens.color.surfaceContainerHigh.copy(alpha = 0.55f))
            .clickable(onClick = onOpenArtist)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space4),
    ) {
        // ── 左：画师身份 ──
        Column(modifier = Modifier.width(264.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(tokens.color.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    if (user.avatarUrl.isNotBlank()) {
                        VeneraCover(
                            url = user.avatarUrl,
                            contentDescription = user.name,
                            mask = VeneraCoverMask.Visible,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text(
                            user.name.take(1).uppercase(),
                            fontSize = VeneraType.cardTitle.size, lineHeight = VeneraType.cardTitle.lineHeight,
                            fontWeight = VeneraType.cardTitle.weight,
                            color = tokens.color.onPrimaryContainer,
                        )
                    }
                }
                Spacer(Modifier.width(tokens.spacing.space3))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = user.name,
                        fontSize = VeneraType.cardTitle.size, lineHeight = VeneraType.cardTitle.lineHeight,
                        fontWeight = VeneraType.cardTitle.weight,
                        color = tokens.color.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (user.account.isNotBlank()) {
                        Text(
                            text = "@${user.account}",
                            fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                            color = tokens.color.textTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.height(tokens.spacing.space3))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (signed) {
                    // 「已关注」胶囊 = **真取关**（followRemove；成功后本行从列表摘除 + 缓存写穿）。
                    Text(
                        text = if (followBusy) "处理中…" else "已关注",
                        fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                        fontWeight = VeneraType.cardTitle.weight,
                        color = if (followBusy) tokens.color.textTertiary else tokens.color.textPrimary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(tokens.color.surfaceContainerHigh)
                            .border(1.dp, tokens.color.textTertiary.copy(alpha = 0.24f), RoundedCornerShape(50))
                            .androidClickableRow(!followBusy) {
                                followBusy = true
                                scope.launch {
                                    val bearer = account.bearerOrNull()
                                    val removed = if (bearer != null) {
                                        withContext(Dispatchers.IO) {
                                            pixiv.followRemove(bearer, user.id.toString()).isSuccess
                                        }
                                    } else false
                                    followBusy = false
                                    if (removed) onUnfollowSuccess()
                                }
                            }
                            .padding(horizontal = 20.dp, vertical = 7.dp),
                    )
                }
            }
        }
        // ── 右：作品条 ×4（定比 1:1，谁先到谁先显示）──
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space3),
        ) {
            previews.take(4).forEach { work ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(tokens.shape.small))
                        .clickable { onOpenPost(work.post) },
                ) {
                    VeneraCover(
                        url = work.post.previewUrl.ifBlank { work.post.fastUrl }.ifBlank { work.post.largeUrl },
                        contentDescription = work.title,
                        mask = VeneraCoverMask.Visible,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            repeat(4 - previews.take(4).size) {
                Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
            }
        }
    }
}

/** 行内胶囊的点击闸（busy 时不挂 clickable，与"灰行不许留在屏上"同一条口径的禁用形）。 */
private fun Modifier.androidClickableRow(enabled: Boolean, onClick: () -> Unit): Modifier =
    if (enabled) this.then(Modifier.clickable(onClick = onClick)) else this

@Composable
private fun AvatarPlaceholder(name: String) {
    val tokens = VeneraTokens
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(tokens.color.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.take(1).uppercase(),
            fontSize = VeneraType.cardTitle.size, lineHeight = VeneraType.cardTitle.lineHeight,
            fontWeight = VeneraType.cardTitle.weight,
            color = tokens.color.onPrimaryContainer,
        )
    }
}

/** 可见性三档下拉（照安卓 `PixivReadScope`：全部 / 公开 / 非公开）。 */
@Composable
private fun PixivScopeDropdown(scope: PixivReadScope, onPick: (PixivReadScope) -> Unit) {
    com.venera.desktop.ui.DesktopPopUpButton(
        label = null,
        value = scope.screenLabel,
        options = PixivReadScope.entries.map { it.screenLabel },
        selectedIndex = PixivReadScope.entries.indexOf(scope),
        onSelect = { i -> onPick(PixivReadScope.entries[i]) },
    )
}

/**
 * 段落胶囊底色（非 Composable 的纯函数：色值由调用点传入，
 * 里面读 `VeneraTokens` 会踩「非组合位读主题」那把尺）。
 */
private fun Modifier.androidBackground(selected: Boolean, highlight: androidx.compose.ui.graphics.Color): Modifier =
    this.background(
        if (selected) highlight.copy(alpha = 0.16f) else androidx.compose.ui.graphics.Color.Transparent,
        RoundedCornerShape(50),
    )

