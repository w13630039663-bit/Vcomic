package com.venera.compose.gallery.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.venera.compose.components.VeneraEmptyTone
import com.venera.compose.components.VeneraEmptyView
import com.venera.compose.components.venera.VeneraTopAppBar
import com.venera.compose.components.venera.VeneraTopBarPill
import com.venera.compose.components.venera.blurBackdropSource
import com.venera.compose.components.venera.rememberTopBarBackdrop
import com.venera.compose.components.venera.rememberVeneraTopAppBarBehavior
import com.venera.compose.gallery.data.GalleryFeedSnapshot
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.data.bySite
import com.venera.compose.gallery.data.toFavorite
import com.venera.compose.gallery.data.toPost
import com.venera.compose.gallery.data.toSiteRows
import com.venera.compose.gallery.domain.GalleryEmptyRound
import com.venera.compose.gallery.domain.GalleryFeedRefreshPolicy
import com.venera.compose.gallery.domain.GalleryMerge
import com.venera.compose.gallery.domain.GalleryWallFeed
import com.venera.compose.gallery.domain.galleryNotices
import com.venera.compose.ui.tokens.LocalBottomBarClearance
import com.venera.compose.ui.tokens.VeneraSpacing
import com.venera.compose.ui.tokens.VeneraTokens
import java.time.LocalDate
import java.time.ZoneOffset
import top.yukonga.miuix.kmp.blur.LayerBackdrop

/**
 * 每日热门那一屏的**屏本体 + 状态核**（阶段二 · 域 2：画廊二级页共享）。
 *
 * 形状照 `feature/HistoryScreenBody.kt` 那一条：
 *  - [GalleryDailyCore]：日榜状态机（屏上池 / 原始池 / 种子 / 按站失败 / 缓存判据 / remix / accept /
 *    reject），**逐字**提自安卓 `GalleryViewModel` 的日榜腿（那颗 VM 还管首页与猜你喜欢，本轮不动它，
 *    安卓装配把同一份字段交进这颗核）。零 `android.*`：离线快照读写交注入的两枚函数
 *    （安卓接 `GalleryFeedCache`，桌面接 `gallery/data/DesktopGalleryDailySnapshotStore`）。
 *  - [GalleryDailyScreenBody]：屏本体。页形状（`GalleryDailyPage` 的三档空态、缺席提示、页尾读数、
 *    顶栏）从前住在 3620 行、满屏 Android 宿主的 `GalleryScreen.kt` 里，桌面拿不到只能另抄一套 ——
 *    这里把它提进共享面：安卓调用点转交、桌面壳喂同一颗 body。
 *  - **墙那一格是槽位**（`cards` / `loading`）：安卓交 `GalleryCardsGrid`（它的长按动作表、共享元素
 *    飞行、词典标题、预取都绑 Android 宿主，搬它 = 连坐搬整颗 GalleryScreen —— 那是阶段三
 *    「瀑布流卡片项与布局统一」那一批的事）；桌面交自持的瀑布流。三档空态、失败归因、页尾、
 *    取数状态机这些**判据与话术只有一份**，槽位的只是"卡怎么摆、环怎么转"。
 *
 * 打码 / AI / 屏蔽落屏判据走共享 [buildGalleryWall]（与书签 hub 墙、安卓日榜同一把尺，单源）。
 */

/**
 * 日榜屏的**渲染输入快照**（一次重组一造，字段全是读安卓/桌面各自状态件的现值）。
 *
 * 刻意做成 plain data 而不是让 body 认 `GalleryDailyCore`：安卓的日榜状态活在既有
 * `GalleryViewModel` 里（那颗还管首页与猜你喜欢，不许为复用连坐拆它），字段本身是 Compose 状态；
 * 桌面才用 [GalleryDailyCore]。body 吃这颗快照 ⇒ 两端各自的装配把现值摊平递进来即可。
 * [wall] 由**装配层**算（判据单源 [buildGalleryWall]，与从前屏里那一次 `remember` 同一把尺）。
 */
internal data class GalleryDailyUiState(
    val posts: List<GalleryPost>,
    val wall: GalleryWall,
    val date: String,
    val sourceNotice: String?,
    val failures: Map<GallerySite, String>,
    val isLoading: Boolean,
    val error: String?,
)

/**
 * 日榜屏的状态核（桌面装配用）。字段与方法逐条对安卓 `GalleryViewModel` 的日榜腿（注释里的历史教训
 * 都是那颗的）：导航条目被覆盖即销毁组合，这些状态必须活过"点进大图再返回"；`pools` 刻意不进离线快照。
 * 安卓装配不用这颗（它直接把 VM 摊平成 [GalleryDailyUiState]），但两端共享这颗核的**算法**由
 * VM 那侧逐字同步维护 —— 谁改了 accept/reject/remix 的语义，`GalleryDailyCoreTest` 会先红。
 *
 * @param readSnapshot 冷启动**同步**读离线档（安卓 = `GalleryFeedCache.read`；首帧的 [shouldAutoLoad]
 *   要有答案可用，所以必须是同步读而不是 suspend）。
 * @param writeSnapshot 成功一轮后的落盘（安卓 = `GalleryFeedCache.write` 那条 IO 腿的包装）。
 */
internal class GalleryDailyCore(
    private val readSnapshot: () -> GalleryFeedSnapshot?,
    private val writeSnapshot: (GalleryFeedSnapshot) -> Unit,
) {
    var posts by mutableStateOf<List<GalleryPost>>(emptyList())
        internal set

    /** 两站**滤之前的原始池**（[remix] 的唯一原料；得活过组合重建，不然第二下「换一批」变真请求）。 */
    var pools by mutableStateOf<Map<GallerySite, List<GalleryPost>>>(emptyMap())
        internal set

    var isLoadingFeed by mutableStateOf(false)
    var feedError by mutableStateOf<String?>(null)

    /** 本轮抽样与打乱用的种子。**只在这里生成**，组合重建后重跑 `mix` 得到同一个序列。 */
    var seed by mutableLongStateOf(System.currentTimeMillis())
        internal set

    /** 实际请求的那一天（`yyyy-MM-dd`），给页面把"看的是哪天的热门"说出来。 */
    var date by mutableStateOf("")
        internal set

    /** 「这一轮只有一站给了内容」之类的提示串（仍供空态整段说明用；网格首行吃 [failures] 的结构）。 */
    var sourceNotice by mutableStateOf<String?>(null)

    /** 「这一轮哪几站没给内容、各是什么原因」，按站一份（整轮没成时 [reject] 也要写它）。 */
    var failures by mutableStateOf<Map<GallerySite, String>>(emptyMap())
        internal set

    /** 手动刷新计数：参与 [feedLoadedKey] 的比对口径，没有它「刷新」就是假按钮。 */
    var refreshTick by mutableIntStateOf(0)

    /** 「这一轮已经成功加载过」的凭证，只在成功后写（失败不写，从错误态重试才放行）。 */
    var feedLoadedKey by mutableStateOf<String?>(null)

    private var hydratedFromCache by mutableStateOf(false)
    private var cachedOnEpochDay by mutableStateOf<Long?>(null)

    /** 打码档（装配层从各自偏好件喂：安卓 `contentGuard.nsfwMaskMode` 的流值，桌面 `maskModeOf()`）。 */
    var maskMode by mutableStateOf("")

    /** AI 屏蔽开关（`prefs.galleryBlockAi`）。 */
    var blockAi by mutableStateOf(false)

    /** 用户屏蔽规则的命中问句（装配层给；与安卓日榜、书签 hub 同一对入参口径）。 */
    var blockedRuleOf by mutableStateOf<((GalleryPost) -> String?)>({ null })

    /** 落屏墙：三档判据单源 [buildGalleryWall]，两端不许各拼一遍。 */
    val wall: GalleryWall get() = buildGalleryWall(posts, maskMode, blockAi, blockedRuleOf)

    /** 摊平成 body 吃的快照；[wall] 默认取本核那颗单源墙（装配层不重拼判据）。 */
    fun uiState(wall: GalleryWall = this.wall) = GalleryDailyUiState(
        posts = posts,
        wall = wall,
        date = date,
        sourceNotice = sourceNotice,
        failures = failures,
        isLoading = isLoadingFeed,
        error = feedError,
    )

    init {
        val snapshot = readSnapshot()
        if (snapshot != null) {
            // 认不出站点键的行当场摘掉：留着它就是一格永远点不开的卡。
            posts = snapshot.posts.mapNotNull { fav -> fav.site?.let { fav.toPost(it) } }
            date = snapshot.date
            sourceNotice = snapshot.notice
            failures = snapshot.failures.bySite()
            cachedOnEpochDay = snapshot.savedEpochDay
            hydratedFromCache = posts.isNotEmpty()
        }
    }

    /** 页面问"这一轮要不要自己去联网取"：缓存屏且就是今天存的 ⇒ 不取（判据在 [GalleryFeedRefreshPolicy]）。 */
    fun shouldAutoLoad(todayEpochDay: Long = LocalDate.now(ZoneOffset.UTC).toEpochDay()): Boolean =
        !hydratedFromCache || GalleryFeedRefreshPolicy.shouldRefetch(cachedOnEpochDay, todayEpochDay)

    /** 「换一批」退回真取的那一档：清屏换种子（这一档要的就是"从新一批开始"）。 */
    fun refresh() {
        refreshTick++
        seed = System.currentTimeMillis()
        posts = emptyList()
        pools = emptyMap()
        date = ""
        sourceNotice = null
        failures = emptyMap()
        feedLoadedKey = null
        // 用户明确要"换一批"：缓存那一屏当场作废，下一次判断必须放行去取。
        hydratedFromCache = false
        cachedOnEpochDay = null
    }

    /** 下拉刷新那一档：换种子放行下一次取，但**不清屏**（旧屏留着，落地时由 [accept] 整屏替换）。 */
    fun refreshKeepingContent() {
        refreshTick++
        seed = System.currentTimeMillis()
        feedLoadedKey = null
        hydratedFromCache = false
        cachedOnEpochDay = null
    }

    /** 某一站刚回来的那一笔：先到先上屏；不写凭证、不落盘（中途那一屏不是最终结果）。 */
    fun acceptPartial(pools: Map<GallerySite, List<GalleryPost>>) {
        if (pools.isEmpty()) return
        val merged = GalleryMerge.mix(pools, seed).getOrNull() ?: return
        this.pools = pools
        posts = merged.posts
    }

    /** 收下本轮产出（整屏替换）；只有拿到内容那一轮才落盘 —— 错误态与空屏不存，否则冷启动铺一屏空。 */
    fun accept(
        list: List<GalleryPost>,
        day: String,
        notice: String?,
        pools: Map<GallerySite, List<GalleryPost>>,
        failures: Map<GallerySite, String> = emptyMap(),
    ) {
        posts = list
        date = day
        sourceNotice = notice
        this.failures = failures
        this.pools = pools
        hydratedFromCache = false
        cachedOnEpochDay = null
        if (list.isEmpty()) return
        writeSnapshot(
            GalleryFeedSnapshot(
                savedEpochDay = LocalDate.now(ZoneOffset.UTC).toEpochDay(),
                date = day,
                notice = notice,
                failures = failures.toSiteRows(),
                posts = list.map { it.toFavorite() },
            ),
        )
    }

    /** 收下整轮失败（与 [accept] 对称：一轮只落地一种结果）；`GalleryEmptyRound` 的原因必须一起收下。 */
    fun reject(e: Throwable) {
        // 屏上不出现原始异常：各站失败原因已由 [failures] 那份结构分档表达。
        feedError = "这次没读到内容 🥲 稍后再试一次"
        (e as? GalleryEmptyRound)?.reasons?.let { failures = it }
    }

    /** 「换一批」= 只在本地重排。`false` = 手上没池（冷启动缓存屏 / 这轮没取成），调用方必须退回真取。 */
    fun remix(): Boolean {
        if (!GalleryMerge.canRemix(pools)) return false
        seed = System.currentTimeMillis()
        // 判据炸了（给了内容却滤光）也回 false：那一档交回调用方去真取一次，
        // 不能静默把旧的一屏原样交回去 —— 用户会以为"换一批"生效了。
        val merged = GalleryMerge.mix(pools, seed).getOrNull() ?: return false
        posts = merged.posts
        return true
    }
}

/**
 * 「每日热门」二级页的屏本体（安卓薄壳与桌面壳共喂）。
 *
 * @param state 渲染输入快照（安卓从 `GalleryViewModel` 摊平、桌面从 [GalleryDailyCore] 摊平；
 *   字段背后的容器是 Compose 状态，body 读它们照样随取数落地而重组）
 * @param onOpenPost 点开一张卡（大图页导航由各自装配层解释）
 * @param onBack 返回
 * @param onRemix 顶栏「换一批」与错误态「重试」共用这一枚把手（装配层：先本地重排、拿不到池退回真取 + 清熔断；
 *   安卓那侧触感也收进这一枚回调，body 不碰 `LocalView`）
 * @param cards 墙槽位：摆卡交装配层（安卓 `GalleryCardsGrid` / 桌面瀑布流），交的是**落屏判据算好的墙**
 *   （`GalleryCard` 已带 `masked`，`contentPadding`/`header`/`footer` 一体递过去）。缺席提示与页尾
 *   由本体造，两端的包卡容器都得支持整行槽位。
 * @param loading 整页加载环槽位（安卓 `CircularWavyProgressIndicator`；桌面 JetBrains material3
 *   没有那颗实验件，交自己的指示器 —— 档位差记在 `GalleryViewerToolbar` 那条排除注释里）。
 */
@Composable
internal fun GalleryDailyScreenBody(
    state: GalleryDailyUiState,
    onOpenPost: (GalleryPost) -> Unit,
    onBack: () -> Unit,
    onRemix: () -> Unit,
    cards: @Composable (List<GalleryCard>, LazyStaggeredGridState, PaddingValues, @Composable () -> Unit, @Composable () -> Unit) -> Unit,
    loading: @Composable () -> Unit,
) {
    val tokens = VeneraTokens
    val topBarBehavior = rememberVeneraTopAppBarBehavior()
    val topBarBackdrop = rememberTopBarBackdrop()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val gridState = rememberLazyStaggeredGridState()
    val wall = state.wall
    // 二级页与主 Tab 同一条**顶栏避让地板**（口径出处见冻结声明）；
    // 它没有常驻 chrome（搜索条在首页那一屏），所以只需地板本身。
    val topPadding = statusBarTop + VeneraSpacing.topBarFloor

    // 根节点一层 Box：内容与顶栏是**兄弟**，顶栏靠 align(TopCenter) 叠在内容之上
    //（页内自治：外壳不挂 TopAppBar；形状与 GalleryScreen 那一屏逐字同构）。
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(topBarBehavior.nestedScrollConnection),
        ) {
            GalleryDailyPage(
                hasPosts = state.posts.isNotEmpty(),
                wall = wall,
                date = state.date,
                sourceNotice = state.sourceNotice,
                failures = state.failures,
                isLoading = state.isLoading,
                error = state.error,
                state = gridState,
                topPadding = topPadding,
                backdrop = topBarBackdrop,
                cards = cards,
                loading = loading,
                onOpen = onOpenPost,
                onRetry = onRemix,
            )
        }

        VeneraTopAppBar(
            title = GalleryWallFeed.DAILY.label,
            largeTitle = GalleryWallFeed.DAILY.label,
            scrollBehavior = topBarBehavior,
            backdrop = topBarBackdrop,
            navigationIcon = {
                VeneraTopBarPill(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "返回",
                        tint = tokens.color.textPrimary,
                    )
                }
            },
            actions = {
                VeneraTopBarPill(onClick = onRemix) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        // 它干的是"从两站原始池里再抽一批" —— 与首页那枚同一个动作、同一句读数。
                        contentDescription = "换一批",
                        tint = tokens.color.textPrimary,
                    )
                }
            },
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

/**
 * 日榜那一面墙的**页形状**（原样移自 `GalleryScreen.kt` 的 `GalleryDailyPage`；卡与加载环换成槽位）。
 *
 * 三档"没有行"各念一句、读不成不许改写成空态、页尾归因只按实际剔掉的张数说 ——
 * 每条判据与它的历史注释同批随件迁移，谁改这一屏这些话说什么就得跟着红。
 */
@Composable
internal fun GalleryDailyPage(
    hasPosts: Boolean,
    wall: GalleryWall,
    date: String,
    sourceNotice: String?,
    failures: Map<GallerySite, String>,
    isLoading: Boolean,
    error: String?,
    state: LazyStaggeredGridState,
    topPadding: Dp,
    backdrop: LayerBackdrop?,
    cards: @Composable (List<GalleryCard>, LazyStaggeredGridState, PaddingValues, @Composable () -> Unit, @Composable () -> Unit) -> Unit,
    loading: @Composable () -> Unit,
    onOpen: (GalleryPost) -> Unit,
    onRetry: () -> Unit,
) {
    val tokens = VeneraTokens
    val wallCards = wall.cards
    val contentPadding = PaddingValues(
        start = tokens.spacing.screenHorizontal,
        end = tokens.spacing.screenHorizontal,
        // 顶栏避让地板（这一页没有常驻 chrome，避让就是地板本身）。
        top = topPadding,
        bottom = LocalBottomBarClearance.current,
    )
    val wallModifier = Modifier
        .fillMaxSize()
        .blurBackdropSource(backdrop)
    // 缺席提示（恒在槽位，有没有话由内部判）与页尾读数（日榜口径专属，别的墙不摆）随页形状留本体。
    val header: @Composable () -> Unit = {
        GalleryNotices(
            notices = remember(failures) { galleryNotices(failures) },
            compact = true,
            onRetry = onRetry,
            modifier = Modifier.padding(bottom = tokens.spacing.space3),
        )
    }
    val footer: @Composable () -> Unit = { GalleryFeedEnd(wall, date) }
    when {
        isLoading && !hasPosts -> Box(wallModifier, contentAlignment = Alignment.Center) {
            // 整页级加载统一走宿主提供的加载环（安卓 M3 Expressive 波浪环，全站口径；桌面没有那颗件）。
            loading()
        }

        error != null && !hasPosts -> Box(
            wallModifier.padding(horizontal = tokens.spacing.screenHorizontal),
            contentAlignment = Alignment.Center,
        ) {
            VeneraEmptyView(
                title = "最新流没读出来",
                message = error,
                // 「加载失败」不许配**内容**图标（这里原来是 `Icons.Outlined.Image`，
                // 那枚图标的意思是"这儿有图"）。语义档自己去挑一枚说"没取回来"的。
                tone = VeneraEmptyTone.Failed,
                actionText = "重新加载",
                onAction = onRetry,
            )
        }

        wallCards.isEmpty() && !isLoading -> Box(
            wallModifier.padding(horizontal = tokens.spacing.screenHorizontal),
            contentAlignment = Alignment.Center,
        ) {
            // 空态归因严格按实际剔除张数区分「被规则/分级挡住」与「站方未返回内容」，避免误导用户修改规则。
            val blocked = wall.blockedCount
            val hidden = wall.hiddenByRating
            VeneraEmptyView(
                title = when {
                    blocked > 0 || hidden > 0 -> "这一屏被你的规则挡完了"
                    else -> "这一轮没有可摆的图"
                },
                message = buildString {
                    if (blocked > 0) {
                        append("$blocked 张命中屏蔽规则：${wall.blockedRules.joinToString("、")}")
                        append("。图站的标签是 long_hair、tail 这类下划线写法，")
                        append("短关键字很容易整站命中 —— 想看到内容就把那条规则收掉或改长。")
                    }
                    if (hidden > 0) {
                        if (blocked > 0) append("\n")
                        append("$hidden 张被「成人内容处理」收起。想看到内容，去设置里改成「封面打码」或「不过滤」。")
                    }
                    if (blocked == 0 && hidden == 0) {
                        // 不说"两站都没有"：哪几站参与了的实情由下面那行 sourceNotice 念。
                        append("站方这一轮没有回内容。一张也没被剔掉，所以不是屏蔽规则的事 —— ")
                        append("稍后再刷一次，或换一天（日榜是按站方那一天算的）。")
                    }
                    if (sourceNotice != null) append("\n$sourceNotice")
                },
                icon = Icons.Outlined.Image,
                // 仅在站方未返回内容时提供重试；命中屏蔽或分级收起时重试结果不变，避免假开关。
                actionText = if (blocked == 0 && hidden == 0) "重试" else null,
                onAction = if (blocked == 0 && hidden == 0) onRetry else null,
            )
        }

        else -> cards(
            wallCards,
            state,
            contentPadding,
            header,
            // 页尾只在**有过一轮内容**时摆（hasPosts）：日榜的"已全部显示"不能说缓存都没读到的那一屏。
            { if (hasPosts) footer() },
        )
    }
}

/**
 * 页尾那一行读数（原样移自 `GalleryScreen.kt` 的 private `GalleryFeedEnd`，改 internal 给本体用）。
 *
 * ⚠️ **日期的措辞要小心**：那两站的"热门"口径不同（yande.re 是官方日榜，
 * Gelbooru 是 `sort:score` 的全站高分池、**没有按天的视图**）。
 * 所以这里写「$date 的热门」时不含站名 —— 一旦写成"Gelbooru 这一天的热门"，
 * 就是在描述一个站方根本不提供的东西。日期只对日榜那一路成立。
 */
@Composable
internal fun GalleryFeedEnd(wall: GalleryWall, date: String) {
    val tokens = VeneraTokens
    val perSite = GallerySite.entries.joinToString(" · ") { site ->
        "${site.displayName} ${wall.cards.count { it.post.site == site }}"
    }
    val videos = wall.cards.count { it.post.isVideo }
    Text(
        text = buildString {
            append(if (date.isNotBlank()) "${date} 的热门已全部显示" else "当天热门已全部显示")
            append("（$perSite")
            if (videos > 0) append("，含 $videos 个视频")
            if (wall.blockedCount > 0) {
                append("；另有 ${blockedByRulesFragment(wall.blockedCount, wall.blockedRules)}")
            }
            if (wall.hiddenByRating > 0) {
                append("；${ratingHiddenFragment(wall.hiddenByRating)}")
            }
            append("）")
        },
        fontSize = tokens.type.overline,
        color = tokens.color.textTertiary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = tokens.spacing.space6),
    )
}
