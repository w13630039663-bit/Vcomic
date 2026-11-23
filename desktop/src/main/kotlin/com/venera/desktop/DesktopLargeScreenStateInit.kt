package com.venera.desktop
import androidx.compose.ui.input.key.key
import com.venera.compose.feature.ComicDetailCore
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.venera.desktop.platform.DesktopPixivCallback
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import androidx.compose.ui.unit.Dp
import com.venera.compose.components.VeneraNavTab
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.compose.components.sideBarModeFor
import com.venera.compose.gallery.domain.GalleryHomeDomain
import com.venera.compose.components.wideScreenLayoutMode
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.desktop.search.DesktopComicSearchOutcome
import com.venera.desktop.search.DesktopComicSearchMore
import com.venera.desktop.search.desktopComicSearchEmptyNote
import com.venera.desktop.search.desktopComicSearchFailedFooter
import com.venera.desktop.search.desktopComicSearchHistoryChips
import com.venera.desktop.search.desktopComicSearchMoreState
import com.venera.desktop.gallery.ui.DesktopGallerySavedView
import com.venera.desktop.gallery.ui.DESKTOP_PIXIV_SEARCH_WORKS
import com.venera.desktop.gallery.ui.desktopGallerySearchSourceOptions
import com.venera.desktop.gallery.ui.desktopRankingChoices
import com.venera.desktop.gallery.ui.desktopRankingLanding
import com.venera.desktop.gallery.ui.desktopRankingAnchorReading
import com.venera.desktop.gallery.ui.desktopRankingStep
import com.venera.desktop.gallery.data.DesktopComicDownloadQueue
import com.venera.desktop.gallery.ui.DesktopWallCardModel
import com.venera.desktop.gallery.ui.DesktopWallPage
import com.venera.desktop.gallery.ui.DesktopGalleryRatingFilter
import com.venera.desktop.gallery.ui.DesktopWallDensity
import com.venera.desktop.gallery.ui.desktopFilteredWallPage
import com.venera.desktop.gallery.ui.DesktopGalleryMorePhase
import com.venera.desktop.gallery.ui.DesktopGalleryWallFooter
import com.venera.desktop.gallery.ui.desktopGalleryWallFooter
import androidx.compose.runtime.mutableStateMapOf
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GallerySearchEntry
import com.venera.compose.gallery.domain.GallerySearchSource
import com.venera.desktop.gallery.ui.desktopGelbooruConfigured
import com.venera.desktop.gallery.ui.DesktopGallerySort
import com.venera.desktop.gallery.data.DesktopLocalComicLibraryScan
import com.venera.desktop.gallery.ui.desktopMergeSuggestionLegs
import com.venera.desktop.gallery.data.DesktopComicPage
import com.venera.desktop.gallery.data.desktopFavoriteFolderChoice
import com.venera.desktop.gallery.data.desktopFavoriteFolderNames
import com.venera.desktop.gallery.data.desktopFavoriteFolderOf
import com.venera.desktop.gallery.data.desktopFavoriteFolderCountNote
import com.venera.desktop.gallery.data.desktopFavoriteFolderFilterNote
import com.venera.desktop.gallery.data.desktopFavoriteFolderTabChoice
import com.venera.desktop.gallery.data.desktopFavoriteFolderTabRows
import com.venera.desktop.gallery.data.desktopFavoriteFoldersShown
import com.venera.desktop.gallery.data.desktopFavoriteRecordToWrite
import com.venera.desktop.gallery.data.desktopFavoriteRowOf


@Composable
internal fun rememberDesktopLargeScreenState(
    windowWidth: Dp,
    ports: GalleryPorts,
    modifier: Modifier = Modifier,
    maskModeOf: () -> String = { "BLUR" },
    blockedByUserOf: (sourceKey: String, title: String, author: String, tags: List<String>) -> Boolean =
        { _, _, _, _ -> false },
    wallGuardOf: () -> com.venera.compose.gallery.data.GalleryContentGuard? = { null },
    wallBlockAiOf: () -> Boolean = { false },
    engine: com.venera.engineprobe.EngineSession? = null,
    onWindowMinimize: (() -> Unit)? = null,
    onWindowToggleMaximize: (() -> Unit)? = null,
    windowMaximized: Boolean = false,
    onShellBackdropChange: (String?, Boolean) -> Unit = { _, _ -> },
    onWindowClose: (() -> Unit)? = null,
    windowDragModifier: Modifier = Modifier,
    writeHistory: (suspend (com.venera.compose.data.db.HistoryRecord) -> Unit)? = null,
    readHistoryRow: (suspend (String) -> com.venera.compose.data.db.HistoryRecord?)? = null,
    setMaskMode: ((String) -> Boolean)? = null,
    setBlockAi: ((Boolean) -> Unit)? = null,
    readSources: (() -> List<com.venera.desktop.sourcemanage.DesktopSourceRowUi>)? = null,
    writeSourceEnabled: ((String, Boolean) -> Unit)? = null,
    writeSourceOrder: ((List<String>) -> Unit)? = null,
    readSourceText: ((String) -> String?)? = null,
    saveSourceText: (suspend (key: String, content: String) -> String?)? = null,
    clearSourceOverride: (suspend (String) -> Unit)? = null,
    loginWithCookies: (suspend (key: String, values: List<String>) -> String)? = null,
    loginWithPassword: (suspend (key: String, account: String, password: String) -> String)? = null,
    loginWithWebview: (suspend (key: String) -> String)? = null,
    readFavorites: (suspend () -> Pair<List<com.venera.compose.data.db.FavoriteRecord>, Boolean>)? = null,
    readFavoriteFolders: (suspend () -> List<String>)? = null,
    createFavoriteFolder: (suspend (String) -> Unit)? = null,
    deleteFavoriteFolder: (suspend (String) -> Unit)? = null,
    onOpenCardArtist: ((com.venera.desktop.gallery.ui.DesktopWallCardModel) -> Unit)? = null,
    removeFavorite: (suspend (String) -> Unit)? = null,
    isFavoriteOf: (suspend (String) -> Boolean)? = null,
    addFavorite: (suspend (com.venera.compose.data.db.FavoriteRecord) -> Unit)? = null,
    searchComics: (suspend (String, Int, String, List<String?>) -> DesktopComicSearchOutcome)? = null,
    downloadsRoot: java.io.File? = null,
    readerPrefs: com.venera.desktop.gallery.data.DesktopComicReaderPreferences? = null,
    readerModeSeed: com.venera.desktop.gallery.data.DesktopReaderMode? = null,
    initialTab: VeneraNavTab = VeneraNavTab.HOME,
    initialRoute: DesktopRoute = DesktopRoute.Tab,
    openFirstGalleryPost: Boolean = false,
    pixivLoginCallback: StateFlow<DesktopPixivCallback?> = kotlinx.coroutines.flow.MutableStateFlow(null),
    galleryPaths: com.venera.compose.data.platform.PathProvider? = null,
    galleryPrefs: com.venera.desktop.gallery.data.DesktopGalleryPreferences? = null,
    galleryGuard: com.venera.desktop.gallery.data.DesktopGalleryContentGuard? = null,
    decoPrefs: com.venera.desktop.gallery.data.DesktopDecoTierPreferences? = null,
    assetDir: java.io.File? = null,
    overrideDir: java.io.File? = null,
): DesktopLargeScreenState {
    // 墙卡在壳层用 produceState 拉取（初值空列表避免假完成），依赖键仅取 ports 避免改窗宽重发请求；
    // 首页统计与历史直接读库，冷启动首帧保持 Loading。


    // 仅两层页面，使用 sealed 接口 + remember 管理路由，不引入导航库。
    val scope = rememberCoroutineScope()
    val routeState = remember { mutableStateOf<DesktopRoute>(initialRoute) }
    var route by routeState
    // 设置是盖在底层页面之上的一层：route 仍记它，但内容分派吃下面这颗「底层路由」，
    // 弹窗收起时回到用户打开设置前那一页，而不是被踢回首页。
    val routeBeforeSettingsState = remember { mutableStateOf<DesktopRoute>(DesktopRoute.Tab) }
    var routeBeforeSettings by routeBeforeSettingsState
    val settingsOpen = route is DesktopRoute.Settings
    val contentRoute: DesktopRoute = if (settingsOpen) routeBeforeSettings else route
    fun openSettings() {
        if (!settingsOpen) routeBeforeSettings = route
        route = DesktopRoute.Settings
    }

    // ── 口径「图库卡片进图库作品详情页」──
    // 点一张 pixiv 卡 = Pixiv作品详情页；点一张 booru 卡 = 图库作品详情页；
    // 详情页里点击大画布才进入沉浸大图播放器。主墙、搜索墙、收藏 hub、每日热门共用这一条判据。
    fun openGalleryPostAny(post: com.venera.compose.gallery.data.GalleryPost) =
        openGalleryPostHelper(post, galleryPrefs, routeState)

    val openWallCardAny: (DesktopWallCardModel) -> Unit = { card ->
        openWallCardHelper(card, galleryPrefs, routeState)
    }
    // 侧栏角色位事件已抽为同包顶层函数 petImage / petWork / petSearch / petSource / petPost / petFilter / petSwitchPage。

    val pixivCallback by pixivLoginCallback.collectAsState()
    // 画廊浏览历史状态：browseRows 为 null 表示尚未读盘；存储实例在 Dispatchers.IO 延迟获取，避免组合期同步读盘。
    val browseClearUi = remember { BrowseClearUiState() }

    // 合并页的三枚视图状态（档 / 搜索词 / 站点筛选）打包成一颗：合成层的捕获表已贴着 255 槽上限，
    // 分开摆就是三枚捕获。进哪条路由就落哪一档。
    val savedUiState = remember { mutableStateOf(SavedUiState(DesktopGallerySavedView.FAVORITES, "", null)) }
    var savedUi by savedUiState

    // ⚠️ 「记一行」那一路的**写侧 effect 不在这里**：它要吃那两面墙的批
    // （`wallPage` / `gallerySearchPage`，声明在下面一百二十多行之后），所以住在它们后面
    // （§124 那族「取数键与消费键同层」的排法）。读侧这一颗不需要它们，留在原地。

    // 备份与还原那一屏的状态：成因、上一次导/恢摘要、二次确认档、忙标、落点路径。
    // 打成一颗是因为组合顶层那圈 provider 的 lambda 已贴 JVM 的 255 槽顶，多一枚都可能编得过而跑不起来。
    val backupUi = remember { BackupUiState() }

    // 详情评论那一屏的状态：view 是**成品状态**（`DesktopCommentsView` 由壳的取数
    // effect 算好 —— 屏只搬，与 §134 统计屏 / §135 备份屏同一族拆法）。
    val detailUi = remember { DetailAndReaderUiState() }
    // 详情核按本 LRU 缓存，必须活在**路由切换之外**：进阅读器时详情分支离场，
    // 缓存若跟着销毁，返回就得重新拉章节目录与缩略图（骨架屏闪一次）。
    // 上限 4 本，逐出时取消作用域；退出整个屏时才统一收手。
    val detailCoreCache = remember {
        mutableMapOf<String, Pair<CoroutineScope, com.venera.compose.feature.ComicDetailCore>>()
    }
    // 「再读一页」扳一下这枚计数，取数 effect 的键里有它（与 `exploreTick` / `searchTick` 同族）。
    val commentsTickState = remember { mutableIntStateOf(0) }
    var commentsTick by commentsTickState

    // 详情/阅读器要的数据：当前这一本 + 这一话。
    // 详情页那枚收藏钮的三态：`null` = 还没查回来（**不许**当"没收过"渲染）。
    // 按下去到写落之间那一档：屏上念「正在写…」并禁用，避免连点把两条 INSERT 打进同一格。
    // 写完一次就 bump 一下，让下面那颗 effect 重查真值（乐观反映会被它校正回来）。
    // 详情页阅读进度三态：null 表示尚未读回（按钮按开始阅读展示），Result.failure 时隐藏按钮；由壳层查库避免屏内碰库。
    // 批量下载弹窗状态置于壳层并加入本地扫描 effect 键，确保弹窗开启期间落盘新页能触发重数。
    val readerPagesState = remember {
        mutableStateOf<List<com.venera.desktop.gallery.data.DesktopComicPage>>(emptyList())
    }
    var readerPages by readerPagesState


    // ── 探索屏的状态──
    val exploreSectionsState = remember { mutableStateOf<List<com.venera.engineprobe.EngineSession.Section>>(emptyList()) }
    var exploreSections by exploreSectionsState
    val exploreNoteState = remember { mutableStateOf<String?>(null) }
    var exploreNote by exploreNoteState
    // 重取一次的触发器：`reloadTick` 变一次，下面那个 LaunchedEffect 就重跑一次。
    val exploreTickState = remember { mutableIntStateOf(0) }
    var exploreTick by exploreTickState
    // 探索源切换状态置于壳层（null 走默认源），换源由数据面重拉第一页，首页推荐漫画同步复用该结果。
    val exploreSourceKeyState = remember { mutableStateOf<String?>(null) }
    var exploreSourceKey by exploreSourceKeyState
    // ── 探索模式（「探索页面…筛选和分类每个源都不一样的」）──
    // 每颗源自己的 `explore` 数组就是它的探索方式清单（禁漫/拷贝/E站各不同），
    // 名字取自 JS 的 title/name；`exploreModeIndex` 是当前那一格（换源时按新源收敛）。
    val exploreModesState = remember { mutableStateOf<List<String>>(emptyList()) }
    var exploreModes by exploreModesState
    val exploreModeIndexState = remember { mutableIntStateOf(0) }
    var exploreModeIndex by exploreModeIndexState
    // ── 探索分类面板（「和安卓一样有这些分类选项，每个源都不一样」）──
    // 分组与排行榜档位来自源 JS 的 `category`；点一枚 chip → 原地取该分类第一页。
    val exploreCategoryBoardState = remember {
        mutableStateOf(com.venera.engineprobe.EngineSession.CategoryBoard(emptyList(), emptyList()))
    }
    var exploreCategoryBoard by exploreCategoryBoardState
    val explorePickUi = remember { ExplorePickUiState() }
    val explorePickTickState = remember { mutableIntStateOf(0) }
    var explorePickTick by explorePickTickState
    val explorePickMoreTickState = remember { mutableIntStateOf(0) }
    var explorePickMoreTick by explorePickMoreTickState
    val explorePickPageNoState = remember { mutableIntStateOf(1) }
    var explorePickPageNo by explorePickPageNoState

    // ── 首页推荐漫画（基于阅读统计 Top 标签从禁漫天堂取数）──
    val homeRecommendComicsState = remember { mutableStateOf<List<com.venera.engineprobe.EngineSession.ComicCard>>(emptyList()) }
    var homeRecommendComics by homeRecommendComicsState
    val homeRecommendNoteState = remember { mutableStateOf<String?>(null) }
    var homeRecommendNote by homeRecommendNoteState


    // ── 收藏 / 搜索 / 源管理 的状态──
    val favoritesState = remember { mutableStateOf<List<com.venera.compose.data.db.FavoriteRecord>>(emptyList()) }
    var favorites by favoritesState
    val favoritesLoadedState = remember { mutableStateOf(false) }
    var favoritesLoaded by favoritesLoadedState
    val favoritesNoteState = remember { mutableStateOf<String?>(null) }
    var favoritesNote by favoritesNoteState
    // 详情页选中的收藏夹置于壳层（null 跟随当前所在夹），便于屏组件无状态离屏测试。
    val favoriteFolderPickedState = remember { mutableStateOf<String?>(null) }
    var favoriteFolderPicked by favoriteFolderPickedState


    // 收藏屏筛选夹（null 表示全部，不可用空串或「默认」代替）；与详情页写入夹分开存储，避免跨屏串扰。
    val favoritesTabPickedState = remember { mutableStateOf<String?>(null) }
    var favoritesTabPicked by favoritesTabPickedState

    // 收藏屏三路并列分段，默认选中本地收藏（开窗即就绪，网络收藏需等待引擎）。
    val favoritesSourceKeyState = rememberSaveable {
        mutableStateOf(com.venera.desktop.favorites.FAVORITES_SOURCE_LOCAL)
    }
    var favoritesSourceKey by favoritesSourceKeyState
    /** 自建空夹名单（建/删夹后 favoriteTick 重读）。 */
    val favoriteFoldersState = remember { mutableStateOf<List<String>>(emptyList()) }
    var favoriteFolders by favoriteFoldersState
    // ── 网络收藏段(引擎 favorites 三跳)──
    // 手风琴"单开"的两枚:哪颗源展开 / 各源展开后的页与夹态(键 = 源 key)。
    // sources 在用户**第一次切到网络段**时探测(引一段引擎跳;平时零开销)。
    val netFavUi = remember { NetFavUiState() }
    // 网络收藏续页统一状态：游标型看 netFavNext 非空，页码型看 netFavPageNo < netFavMaxPage。
    /** 「加载更多」扳一下这枚计数（与 `searchTick` 同族：键取按了几次而不是取页码）。 */
    /** 「刷新此源」扳一下这枚计数（主从布局里点同一颗源不再"收起"，而是原地重取首屏）。 */
    // 「清空」那两步住在壳而不是屏（批 77 那颗件的两枚纯函数 + 那颗 runner 都在 IO 上）。
    // 投卡腿（守卫起口转交）的成品：投影走 IO（存档读 + 守卫问），塌了留一句成因而不是摆空墙。
    val galleryWallCardsState = remember {
        mutableStateOf<List<com.venera.desktop.gallery.ui.DesktopWallCardModel>>(emptyList())
    }
    var galleryWallCards by galleryWallCardsState
    val galleryWallBlockedState = remember { mutableIntStateOf(0) }
    var galleryWallBlocked by galleryWallBlockedState
    val galleryWallUnrenderableState = remember { mutableStateOf("") }
    var galleryWallUnrenderable by galleryWallUnrenderableState
    val galleryWallTickState = remember { mutableIntStateOf(0) }
    var galleryWallTick by galleryWallTickState

    val searchQueryState = remember { mutableStateOf("") }
    var searchQuery by searchQueryState
    // 视图档与速览选中态留在 SEARCH 局部 remember 以控制顶层 Composable 槽位数；搜索/探索切换态置于壳层供面包屑读取。
    val discoverViewExploreState = remember { mutableStateOf(false) }
    var discoverViewExplore by discoverViewExploreState
    // pixiv 两屏的页签状态住壳里（进二级页返回总落回「我的作品」——
    // 屏内 remember 在离开路由重组时归零；提到壳层，壳活着状态就活着）。
    val pixivAccountTabState = remember { mutableStateOf(0) }
    var pixivAccountTab by pixivAccountTabState
    val pixivArtistSectionState = remember { mutableStateOf(com.venera.compose.gallery.data.PixivArtistSection.ILLUST) }
    var pixivArtistSection by pixivArtistSectionState
    val lastPixivArtistIdState = remember { mutableStateOf<Long?>(null) }
    var lastPixivArtistId by lastPixivArtistIdState
    val searchResultsState = remember { mutableStateOf<List<com.venera.engineprobe.EngineSession.ComicCard>>(emptyList()) }
    var searchResults by searchResultsState
    val searchingState = remember { mutableStateOf(false) }
    var searching by searchingState
    val searchTickState = remember { mutableIntStateOf(0) }
    var searchTick by searchTickState

    // 搜索源选择置于壳层（null 走默认源）；界面展示源显示名，未匹配时回退显示键名。
    val searchSourceKeyState = remember { mutableStateOf<String?>(null) }
    var searchSourceKey by searchSourceKeyState
    // 聚合搜索全部状态收进一枚 holder，控制壳顶层 lambda 捕获槽数量。
    val aggregateSearch = remember { com.venera.desktop.search.DesktopAggregateSearchHolder() }
    // 从详情页点 tag 进搜索时记下的**返回目标**（搜索要能返回详情页）。
    // 只在这一条进搜索的路上写；从侧栏 / 首页正常进搜索时它是 null，返回键维持原语义。
    val searchReturnRouteState = remember { mutableStateOf<DesktopRoute?>(null) }
    var searchReturnRoute by searchReturnRouteState

    // 按源动态筛选组与默认值由引擎 search.optionList 现读，换源重置；多选编码为 JSON 数组串，未选为 null。
    val searchFilterGroupsState = remember { mutableStateOf<List<com.venera.engineprobe.EngineSession.SearchOption>>(emptyList()) }
    var searchFilterGroups by searchFilterGroupsState
    val searchFilterValuesState = remember { mutableStateOf<List<String?>>(emptyList()) }
    var searchFilterValues by searchFilterValuesState

    // 搜索页码属于数据面状态，置于壳层管理，屏组件不直接访问引擎。
    val searchPageState = remember { mutableIntStateOf(1) }
    var searchPage by searchPageState
    // 源自己声明的总页数（`null` = 这颗源不声明，游标型那 7 颗）。只由取数那一趟写。
    val searchMaxPageState = remember { mutableStateOf<Int?>(null) }
    var searchMaxPage by searchMaxPageState
    // 翻下一页那一路本身在跑（与 `searching` 分开：那一枚说的是****）。
    val loadingMoreState = remember { mutableStateOf(false) }
    var loadingMore by loadingMoreState
    // 页码推进过之后才算"翻得动"，而这一格由纯件算好存在这里，屏只搬。
    val searchCanLoadMoreState = remember { mutableStateOf(false) }
    var searchCanLoadMore by searchCanLoadMoreState
    // 「这一轮真发过车没」：与"输入框里有字"是两件事（空结果那一句靠它分开三档）。
    val hasSearchedState = remember { mutableStateOf(false) }
    var hasSearched by hasSearchedState
    val searchHistoryState = remember { mutableStateOf<List<String>>(emptyList()) }
    var searchHistory by searchHistoryState
    // 写盘那一趟塌了要留话；非空时那一排**整格不摆**（摆一排而按下去不落盘是假开关）。
    val searchHistoryNoteState = remember { mutableStateOf<String?>(null) }
    var searchHistoryNote by searchHistoryNoteState
    // 单一搜索失败原因状态：有结果时展示在页尾，无结果时展示在空态，避免两处文案冲突。
    val searchCauseState = remember { mutableStateOf<String?>(null) }
    var searchCause by searchCauseState
    // 翻下一页那一跳的触发器（与 `searchTick` 同一手法：键取"按了几次"而不是取页码，
    // 否则失败重按同一页时那颗 effect 不会重跑）。
    val loadMoreTickState = remember { mutableIntStateOf(0) }
    var loadMoreTick by loadMoreTickState

    // 画廊标签搜索复用画廊墙分页、守卫与页尾组件；搜索历史独立存放在 venera_gallery_search，不与漫画搜索混淆。
    val gallerySearchQueryState = remember { mutableStateOf("") }
    var gallerySearchQuery by gallerySearchQueryState
    // 来源档下标（与 `GallerySearchSource.options` 同源：0 = 全部，其后逐站）。
    val gallerySearchSourceState = remember { mutableStateOf(0) }
    var gallerySearchSource by gallerySearchSourceState
    val gallerySearchPageState = remember { mutableStateOf<DesktopWallPage?>(null) }
    var gallerySearchPage by gallerySearchPageState
    val gallerySearchPhaseState = remember {
        mutableStateOf<DesktopGalleryMorePhase>(DesktopGalleryMorePhase.Idle)
    }
    var gallerySearchPhase by gallerySearchPhaseState
    val gallerySearchSearchingState = remember { mutableStateOf(false) }
    var gallerySearchSearching by gallerySearchSearchingState
    // 首轮取数塌了：与「站方回了 0 条」分开，空态要给重试出口而不是猜原因。
    val gallerySearchFailedState = remember { mutableStateOf(false) }
    var gallerySearchFailed by gallerySearchFailedState
    // 历史读回来的是「串 + 发过的腿」那一条记录（不是裸词），点它要按它当初的来源档落点。
    val gallerySearchHistoryState = remember { mutableStateOf<List<GallerySearchEntry>>(emptyList()) }
    var gallerySearchHistory by gallerySearchHistoryState
    // 读盘 / 写盘塌了要留话；非空时那一排**整颗不摆**（摆一排而按下去不落盘是假开关）。
    val gallerySearchHistoryNoteState = remember { mutableStateOf<String?>(null) }
    var gallerySearchHistoryNote by gallerySearchHistoryNoteState
    val gallerySearchTickState = remember { mutableIntStateOf(0) }
    var gallerySearchTick by gallerySearchTickState
    val gallerySearchMoreTickState = remember { mutableIntStateOf(0) }
    var gallerySearchMoreTick by gallerySearchMoreTickState
    // 在途画廊搜索 Job：新一轮发车前取消上一轮，堵住连点/tick 累加时旧结果落账覆盖新结果。
    // 取数跑在壳层 scope（换 route 不被组合取消），所以这里显式持有句柄自己管取消。
    val gallerySearchJobState = remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var gallerySearchJob by gallerySearchJobState

    // 画廊搜索双模式：0 为图库（含排行日期窗），1 为 Pixiv 专属搜索（含五段筛选）。
    val gallerySearchModeState = remember { mutableStateOf(0) }
    var gallerySearchMode by gallerySearchModeState
    // 图库域的排行档与期次锚点（锚点只有月/年两档带历史期才有值）。
    val gallerySearchRankingState = remember { mutableStateOf(com.venera.compose.gallery.domain.GalleryRanking.NEWEST) }
    var gallerySearchRanking by gallerySearchRankingState
    val gallerySearchRankingAnchorState = remember { mutableStateOf<java.time.LocalDate?>(null) }
    var gallerySearchRankingAnchor by gallerySearchRankingAnchorState
    // Pixiv 域的状态（词 / 筛选 / 维度 / 页与相位，与图库域那组一一对应，互不串账）。
    val pixivSearchTermState = remember { mutableStateOf("") }
    var pixivSearchTerm by pixivSearchTermState
    val pixivSearchFilterState = remember { mutableStateOf(com.venera.compose.gallery.domain.PixivSearchFilter.DEFAULT) }
    var pixivSearchFilter by pixivSearchFilterState
    val pixivSearchKindState = remember { mutableIntStateOf(DESKTOP_PIXIV_SEARCH_WORKS) }
    var pixivSearchKind by pixivSearchKindState
    val pixivSearchPageState = remember { mutableStateOf<DesktopWallPage?>(null) }
    var pixivSearchPage by pixivSearchPageState
    val pixivSearchPhaseState = remember {
        mutableStateOf<DesktopGalleryMorePhase>(DesktopGalleryMorePhase.Idle)
    }
    var pixivSearchPhase by pixivSearchPhaseState
    val pixivSearchSearchingState = remember { mutableStateOf(false) }
    var pixivSearchSearching by pixivSearchSearchingState
    val pixivSearchTickState = remember { mutableIntStateOf(0) }
    var pixivSearchTick by pixivSearchTickState
    val pixivSearchMoreTickState = remember { mutableIntStateOf(0) }
    var pixivSearchMoreTick by pixivSearchMoreTickState
    // 画师维度的结果（searchUser 直出的用户表 + 游标）—— 它不吃图墙，另立一份账。
    val pixivSearchUsersState = remember {
        mutableStateOf<List<com.venera.compose.gallery.data.PixivUser>>(emptyList())
    }
    var pixivSearchUsers by pixivSearchUsersState
    val pixivSearchUsersCursorState = remember { mutableStateOf<String?>(null) }
    var pixivSearchUsersCursor by pixivSearchUsersCursorState
    // 珍藏册维度（目标下拉第三项）的成品账——合成一枚 holder：
    // 壳捕获面那把尺有槽位预算，三枚散装 `by remember` 会各吃一颗真槽。
    // 字段是 holder 类里的 mutableStateOf（不占壳的捕获槽），读它的组合自动订阅。
    val pixivCollectionSearch = remember { PixivCollectionSearchLeg() }

    // 画廊标签联想补全：按当前输入词与可用来源腿防抖查询 searchTags，经并表复检后展示下拉建议。
    val galleryAuxUi = remember { GalleryAuxUiState() }
    val gallerySuggestCandidatesState = remember {
        mutableStateOf<List<com.venera.compose.gallery.data.GalleryTagSuggestion>>(emptyList())
    }
    var gallerySuggestCandidates by gallerySuggestCandidatesState
    // 那一排该补的那句成品（加载中 / 失败点名 / 零条点名，全在纯件里，屏不重拼）。

    val sourceUi = remember { SourceManageUiState() }
    val sourceTickState = remember { mutableIntStateOf(0) }
    var sourceTick by sourceTickState
    // 源导入与更新均先校验可装载性再原子写入覆盖层目录，置于壳层以直接复用引擎与目录状态。

    val sourceScope = rememberCoroutineScope()

    /** 在线源列表的开关（那是一个独立 Dialog，不占主区域）。 */

    /**
     * 验装载 + 落改写层。交回 null = 成功，非 null = 失败成因。
     *
     * 导入 .js 与在线源安装共用这一条链：验不过不写盘 —— 半截脚本落进去，下次启动时
     * 会被静默丢弃，那种损坏最难回头查。
     */
    val installSourceText: suspend (fileKey: String, text: String) -> String? = { fileKey, text ->
        val dir = overrideDir
        if (dir == null) {
            "改写层目录建不出来，装不进去。"
        } else if (!com.venera.desktop.gallery.data.DesktopSourceOverrides.isSafeKey(fileKey)) {
            "key 不合法（只许字母数字与 _ -）：$fileKey"
        } else {
            desktopEngineCall(engine) { engine?.checkLoadable(fileKey, text) }.fold(
                onSuccess = {
                    com.venera.desktop.gallery.data.DesktopSourceOverrides.writeText(dir, fileKey, text)
                    sourceTick++
                    null
                },
                onFailure = { "验装载没过：${it.message?.take(140)}" },
            )
        }
    }

    val runSourceImport: () -> Unit = {
        runSourceImportHelper(installSourceText, sourceUi, scope) { sourceTick++ }
    }
    val performSourceUpdateCheck: suspend (announce: Boolean) -> Unit = { announce ->
        performSourceUpdateCheckHelper(announce, sourceUi, overrideDir, engine) { sourceTick++ }
    }
    // 启动 8 秒后后台检查源更新，避免抢占首屏带宽；仅实际更新时弹窗提示，拉取失败仅记日志。
    // 这是站点更新唯一的入口：屏里不再摆「检查更新」把手。

    // 源管理详情面板选中键（null 表示未选），清单加载后默认选中首个已启用源。
    // 选中那一颗的源设置：定义从脚本文本里扫（DesktopSourceSettings 纯解析），值经引擎读。
    // `null` = 还没读到 / 这颗源没有设置块 / 读口没接（三种塌成同一格，屏上整张卡不摆）。
    // 设置写/callback 那一圈的回话（成品句，屏只搬）。
    // 选中那一颗的账号读数（登录态 + 罐里的账号字串）；`null` = 读口没接 ⇒ 账号卡整颗不摆。
    // 换选中 / 写完设置 / 跑完回调 / 登录回来都要重读详情那一圈。

    // 源编辑原文状态：null 表示尚未读取，不可折叠为空串以免脏检查误报未保存。
    // 读原文塌了的原因（非空时那一屏念它，而不是念一篇空脚本）。
    // 换过一次原文就读一次（同一颗源两次进编辑也要重读 —— 中间可能被"撤回"改回出厂那一篇）。

    // 手填凭据提交快照：仅在点击提交时写入以触发登录 effect，避免输入过程每次重组重复请求。
    // 账号密码那一趟的入场券；与 cookie 那格互斥，按下哪一边就填哪一格。
    // 按了几次提交（与上面那格**一起**当键，理由见那颗 effect 的注释）。
    // 那一趟的那句话（三档由 `DesktopCookieLogin.screenNote` 判，屏一个字都不自己拼）。

    // ── 「本机已下载」那一屏的状态──
    //
    // 那一棵树数回来的结果。`null` = **还没数回来**（屏上念另一句，不念"本机没有"）。
    val filesUi = remember { FilesAuxUiState() }
    // 卡上那一枚「已下载 / 离线不全」用的那张表（站 + 号 → 两档之一）。
    // **由读盘那一趟一起算好存在状态里**，而不是每次重组现算：探索墙一次重组几十张卡，
    // 每帧把整棵树重聚一遍正是刚拆掉的那类空转（同一族，只是这次在渲染侧）。
    // 上一次按下的果（补齐排进队 / 删完了 / 删不动）。
    // 删除是**同步**改盘的（返回就是删完了）⇒ 按完立刻自己重读一次盘，不等队列那张表。
    val downloadsTickState = remember { mutableIntStateOf(0) }
    var downloadsTick by downloadsTickState
    // 本地漫画扫描结果（null 表示尚未扫描）；与带清单的话级 downloadsListing 分开扫描，避免混淆两种空态。
    val localComicScanState = remember { mutableStateOf<DesktopLocalComicLibraryScan?>(null) }
    var localComicScan by localComicScanState
    // 上一次按下的果（导入成败原话等；与 `downloadsNote` 分开存，
    // 免得详情/下载那几屏的 note 串到这一屏来）。
    // ── 导入 CBZ/ZIP（tool-suite 稿⑤拍板）──
    // 三枚状态：进行中（把手禁用，防同一颗包双击导入两份）/ 待导入的文件（effect 消费）/
    // 重数 tick（成功后让扫描那一趟重跑，扫出刚落盘的那一本）。
    val localComicImportTickState = remember { mutableStateOf(0) }
    var localComicImportTick by localComicImportTickState
    // 历史刷新计数：进入阅读历史屏时递增以重读最新记录，避免沿用启动时的旧快照。
    val historyTickState = remember { mutableIntStateOf(0) }
    var historyTick by historyTickState
    // 二次确认删除的武装行键置于壳层，屏组件仅接收 armedKey 以便无头测试验证。
    val armedDeleteKeyState = remember { mutableStateOf<String?>(null) }
    var armedDeleteKey by armedDeleteKeyState


    // 打码判据：与墙上同一条链（`LocalUxPlatformServices`），不另立一套。
    // 档位判定吃**窗口宽**（不是物理屏宽）：桌面没有"整块屏"这个概念，
    // 而 `WideScreenPolicy` 的注释本来就写着"宽度传窗口宽"。
    val layoutMode = wideScreenLayoutMode(windowWidth)
    // 侧栏是否真的接管导航。桌面恒接管（安卓侧还要看 `LIQUID_GLASS` 那条样式轴，
    // 桌面上没有玻璃底栏，所以那一轴恒为 false ⇒ 侧栏接管）。
    val sideBarActive = layoutMode != WideScreenLayoutMode.Compact
    val effectiveMode = sideBarModeFor(layoutMode, sideBarActive)
    // 全屏页将侧栏外壳切至 Compact 隐藏导航栏，内容区仍按实际窗宽保持响应式布局。
    val fullscreenChrome = route is DesktopRoute.Reader || route is DesktopRoute.GalleryPostViewer
    val shellMode = if (fullscreenChrome) WideScreenLayoutMode.Compact else effectiveMode


    val currentTabState = remember { mutableStateOf(initialTab) }
    var currentTab by currentTabState

    // 首页源状态测速账（已启用漫画源 + Pixiv/yande.re），进首页按 host 60s 冷却自动探测并刷新。
    val homeLatencyByHost = remember { mutableStateMapOf<String, Long>() }
    val homeFailedHosts = remember { mutableStateMapOf<String, Long>() }
    val homeProbingHosts = remember { mutableStateListOf<String>() }
    val homeProbeStamp = remember { mutableMapOf<String, Long>() }
    val homeScope = rememberCoroutineScope()

    val homeEnabledHosts: List<Pair<String, String>> = remember(sourceUi.sourceRows, assetDir, overrideDir) {
        sourceUi.sourceRows.filter { it.enabled }.mapNotNull { row ->
            val script = assetDir?.let { dir ->
                com.venera.desktop.gallery.data.DesktopSourceNames.scriptOf(dir, row.key, overrideDir)
            }
            val host = script
                ?.let { com.venera.desktop.gallery.data.DesktopSourceNames.apiHostOf(it) }
                ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            (row.title.ifBlank { row.key }) to host
        }
    }
    val homeVisible = currentTab == VeneraNavTab.HOME && route == DesktopRoute.Tab
    val homeLines: List<Pair<String, String>> = remember(homeEnabledHosts, homeVisible) {
        if (!homeVisible) {
            emptyList()
        } else {
            // pixiv / yande.re 固定两行（这两颗恒显延迟，与启用清单无关）。
            listOf("pixiv" to "www.pixiv.net", "yande.re" to "yande.re") + homeEnabledHosts
        }
    }
    val homeSourceRows: List<com.venera.desktop.DesktopHomeSourceStatusRow> = homeLines.map { (name, host) ->
        val latency = homeLatencyByHost[host]
        com.venera.desktop.DesktopHomeSourceStatusRow(
            name = name,
            host = host,
            probing = host in homeProbingHosts,
            latencyMs = latency,
            failed = latency == null && homeFailedHosts.containsKey(host),
        )
    }

    // 当前源显示名保持三态（有显示名 / 清单无此源给空串 / 清单未就绪给 null），避免清单未加载时误判为异源记录。
    val historySourceName: String? = sourceUi.sourceRows.firstOrNull { it.key == DESKTOP_EXPLORE_SOURCE }?.title
        ?: if (sourceUi.sourceRows.isEmpty()) null else ""

    // 章节下载队列泵使用独立 IO 作用域（换屏不断、退窗才停）；onDispose 仅取消该作用域，不可关闭常驻引擎。
    // 详情 / 章节 / 页表的磁盘缓存（「阅读器和漫画详情页应该做数据持久化」）。
    // 只用 `cacheRoot`（真缓存目录），不往数据根里塞；没有数据根 = 不缓存（返回 null）。
    val comicCache = remember(galleryPaths) {
        galleryPaths?.let { com.venera.desktop.cache.DesktopComicCache(it.cacheRoot) }
    }
    // 并发档冷启动读盘一次（设置「网络」区改档、重启生效）；默认 1 = 旧恒串行形状。
    val queueConcurrency = remember(galleryPaths) {
        galleryPaths?.let {
            com.venera.compose.data.platform.JsonKeyValueStore(
                com.venera.compose.data.platform.PreferenceKeys.PREFS_NAME,
                it,
            ).getInt("pref_download_threads", 1).coerceIn(1, 16)
        } ?: 1
    }
    // 侧栏展开态「记住选择」（安卓侧零改动）。
    // 没有数据根 = 不落盘，那一档退回会话内临时展开的旧形状。
    val railPrefs = remember(galleryPaths) {
        galleryPaths?.let {
            com.venera.compose.data.platform.JsonKeyValueStore(
                com.venera.compose.data.platform.PreferenceKeys.PREFS_NAME,
                it,
            )
        }
    }
    // 悬浮搜索（批）：查询级成人内容排除的总闸。盘上记的是"允许 R18"一枚 int
    //（默认 0 = 全年龄档），开关在顶栏搜索浮层里；真正拼进查询串发生在画廊搜索首轮那处。
    val gallerySearchR18State = remember {
        mutableStateOf((railPrefs?.getInt("pref_gallery_search_r18", 0) ?: 0) == 1)
    }
    var gallerySearchR18 by gallerySearchR18State
    val railExpandedSavedState = remember(railPrefs) {
        mutableStateOf(railPrefs?.getBoolean("pref_sidebar_expanded", false) ?: false)
    }
    var railExpandedSaved by railExpandedSavedState
    // ── 宽屏双源批：推荐 R18 档（railPrefs 持久化；键声明在 railPrefs 定义之后 ✓）──
    val homeRecommendR18State = rememberSaveable(railPrefs) {
        mutableStateOf(railPrefs?.getBoolean("pref_home_recommend_r18", false) ?: false)
    }
    var homeRecommendR18 by homeRecommendR18State
    val homeRecommendFailedState = remember { mutableStateOf(false) }
    var homeRecommendFailed by homeRecommendFailedState
    // 首页重试扳机（11-07 批）：R18/源档切换与失败重试都扳它让两腿重取。
    val homeRetryTickState = remember { mutableStateOf(0) }
    var homeRetryTick by homeRetryTickState
    // 那张表与"读回来过没有"由壳 collect 下来递下去（屏自己不碰那颗进程级单例）。
    val queueTasks by DesktopComicDownloadQueue.tasks.collectAsState()
    val queuePrimed by DesktopComicDownloadQueue.primed.collectAsState()
    // 全部下完接线。只在「本轮真在跑过 → 全停且至少一笔 DONE」的跃迁发一次，
    // 不因每次重组复读（去时间节制后更要靠跃迁闸门，不能靠定时）。
    /** 「下载与本地」合并页当前那一档（三档选块 ·设计稿 v3：域+档两层）。 */
    val filesSegmentState = remember { mutableStateOf(com.venera.desktop.downloads.DesktopFilesSegment.QUEUE) }
    var filesSegment by filesSegmentState

    // ── 设计稿 v3：图片库那格账（扫/删都在 IO 线程，屏只吃算好的表）──

    // ── 队列行展开详情：展开那一笔的目录清单现数（屏不碰盘的老口径，壳 IO 数完递表）──

    // 阅读模式优先级：当场选择 > 启动种子 > 持久化偏好；无 prefs 时复用 remembered 常量流避免每帧重建收集协程。
    val noPrefsReaderMode = remember {
        kotlinx.coroutines.flow.MutableStateFlow(com.venera.desktop.gallery.data.DesktopReaderMode.VERTICAL_CONTINUOUS)
    }
    val storedReaderMode by (readerPrefs?.readingMode ?: noPrefsReaderMode).collectAsState()
    val readerModeChoiceState = remember {
        mutableStateOf<com.venera.desktop.gallery.data.DesktopReaderMode?>(null)
    }
    var readerModeChoice by readerModeChoiceState
    val readerMode = readerModeChoice
        ?: com.venera.desktop.gallery.ui.desktopReaderModeOf(readerModeSeed, storedReaderMode)
    // 那句实话只在**没人当场挑过**时念：挑过之后屏上档已经不是种子顶的那一档了，再念就是假话。
    val readerModeNote = if (readerModeChoice == null) {
        com.venera.desktop.gallery.ui.desktopReaderModeSeedNote(readerModeSeed, storedReaderMode)
    } else {
        null
    }


    val ux = com.venera.compose.components.LocalUxPlatformServices.current

    // 当前遮罩档值**取出来一次**，
    // 供 5 个取数副作用与判据对象当 key —— 切档即重算，不再"改了设置要手动刷一遍才生效"。
    val currentMaskMode = maskModeOf()


    val homeHistory by produceState(initialValue = DesktopHomeHistorySnapshot.Loading, ports, historyTick) {
        value = DesktopHomeHistorySource.read()
    }


    val homeStats by produceState(initialValue = DesktopHomeStats.Empty, ports) {
        value = DesktopHomeStatsSource.read()
    }

    // 首页关注画师栏直接订阅 Store StateFlow 并经 desktopFollowedArtistsRowReadout 投影，不开额外 IO。
    val galleryFollows by ports.follows.follows.collectAsState()
    val galleryFavorites by ports.favorites.favorites.collectAsState()
    val homeArtists = remember(galleryFollows, galleryFavorites) {
        com.venera.desktop.gallery.ui.desktopFollowedArtistsRowReadout(galleryFollows, galleryFavorites)
    }

    // 收藏屏画廊墙经唯一守卫起口投影；拉取失败时保留上一轮卡片并提示原因，不清空墙面。
    // （该批副作用已迁至 DesktopGalleryWallEffects）

    // 阅读统计屏按 route 每次进入现读 14 天趋势与 Top 10，与首页统计分开查询，屏组件零 IO。
    val readingStats by produceState(initialValue = DesktopReadingStats.Loading, route) {
        value = if (route is DesktopRoute.ReadingStats) {
            DesktopReadingStatsSource.read()
        } else {
            DesktopReadingStats.Loading
        }
    }

    // 统计那一屏的取证读数。键里带 `readingStats` 整枚 —— 那张表读回来时
    // 才重打（与历史屏 ⑨ 同一条纪律：只挂 route 会在 loaded 还没翻真时打出 `趋=0 榜=0` 的旧账）。
    // 只在「进到那一屏且真读回来」时打，回底栏不打。
    fun desktopShellBackWithSearch(setRoute: (DesktopRoute) -> Unit): (() -> Unit)? {
        desktopShellBack(route) { setRoute(it) }?.let { return it }
        if (route != DesktopRoute.Tab || currentTab != VeneraNavTab.SEARCH) return null
        val target = searchReturnRoute ?: return null
        return {
            searchReturnRoute = null
            setRoute(target)
        }
    }

    // 源清单 effect 键必须包含 route，确保从探索屏切入源管理时能触发刷新。
    fun onDownloadsDelete(chapter: com.venera.desktop.gallery.data.DesktopComicDownloadedChapter, forceDirect: Boolean = false) =
        onDownloadsDeleteHelper(chapter, forceDirect, downloadsRoot, filesUi, armedDeleteKeyState, scope) { downloadsTick++ }
    val flyState = remember { com.venera.desktop.DesktopFlyState() }
    val flyAnchor = remember { com.venera.compose.components.VeneraSideBarDownloadAnchor() }
    val flyPointer = remember { mutableStateOf<Offset?>(null) }
    val flyHostTopLeftState = remember { mutableStateOf(Offset.Zero) }
    var flyHostTopLeft by flyHostTopLeftState
    fun flyToDownloadCenter(coverUrl: String?, isComic: Boolean) =
        flyToDownloadCenterHelper(coverUrl, isComic, flyPointer, flyHostTopLeft, flyState)
    fun enqueueOneEpisode(
        r: DesktopRoute.Detail,
        ep: com.venera.engineprobe.EngineSession.Episode,
    ): Boolean {
        val root = downloadsRoot ?: return false
        val order = detailUi.detailInfo?.episodes?.indexOfFirst { it.id == ep.id } ?: -1
        if (order < 0) {
            detailUi.detailNote = "这一话没在章节表里认出来 ⇒ 没排队（话号取自表里的行号）"
            return false
        }
        DesktopComicDownloadQueue.enqueue(
            root = root,
            sourceKey = r.sourceKey,
            comicId = r.comicId,
            comicTitle = r.title,
            comicCover = detailUi.detailInfo?.cover?.ifBlank { r.cover } ?: r.cover,
            chapterId = ep.id,
            chapterTitle = ep.name,
            chapterOrder = order,
            placeholder = ep.placeholder,
        )
        detailUi.detailNote = null
        // 排上了才飞：封面档（详情页头那枚，空则回退路由卡上的封面）化作胶囊飞进下载中心。
        flyToDownloadCenter(
            detailUi.detailInfo?.cover?.takeIf { it.isNotBlank() } ?: r.cover.takeIf { it.isNotBlank() },
            isComic = true,
        )
        return true
    }

    /**
     * 批量弹窗那几行要吃的那份盘上账（归一在 `gallery/data/DesktopComicBulkDownload.kt` 里做）。
     *
     * 交回 `null` 有两档，各是一回事而**都不许当成「本机都没有」**：
     *  - 还没数回来（`downloadsListing == null`）；
     *  - 数**不出来**（读盘整趟抛了）—— 这一档若当成空账，弹窗会把整本念成「未下载」并默认全选，
     *    用户按下就是明知故犯地重下一遍；此时屏上念那句带成因的话。
     */
    fun bulkDownloadChapters(): List<com.venera.desktop.gallery.data.DesktopComicDownloadedChapter>? {
        val listing = filesUi.downloadsListing ?: return null
        if (listing.error != null) return null
        return listing.groups.flatMap { it.chapters }
    }

    /**
     * 那一本写进 `comic_favorite` 的那一条：**只有夹名**跟着挑中的那一格走。
     *
     * 两个理由，都得靠这一颗共用件：
     * - 收藏钮与「换夹」那一路各拼一遍的话，「号 / 源 / 封面取自哪一格」就能漂成两种（与
     *    `enqueueOneEpisode` 被单话与批量共用同一格理由；那一列漂成显示名会撞 `DesktopFavoriteWriteSideTest`
     *    ④ 钉的共享表口径）；
     *  - 那颗 DAO 是 `INSERT OR REPLACE`（整行替换），所以挪夹时**不许**把库里那一行的
     *    `tags` / `has_update` / `latest_chapter` / `created_at` 一起打回默认值 —— 那件事由
     *    [desktopFavoriteRecordToWrite] 守住，这里只负责"新拼的那一份长什么样"。
     */
    fun favoriteRecordFor(
        r: DesktopRoute.Detail,
        folderName: String,
    ): com.venera.compose.data.db.FavoriteRecord {
        val fresh =
            com.venera.compose.data.db.FavoriteRecord(
                comicId = r.comicId,
                title = r.title,
                author = "",
                coverUrl = detailUi.detailInfo?.cover?.ifBlank { r.cover } ?: r.cover,
                // 写 key 而不是显示名：与桌面写历史那一行同一条口径，
                // 也与对岸 `HomeViewModel.toLegacyRecord`（`sourceName = sourceKey`）一致。
                sourceName = r.sourceKey,
            )
        // 八格里只有 `folderName` 跟着挑中的那一格走，其余七格
        // 从**已读回的那一行**原样留着（那颗 DAO 是整行替换，见那颗件的 KDoc）。
        return desktopFavoriteRecordToWrite(
            existing = desktopFavoriteRowOf(r.comicId, favorites),
            fresh = fresh,
            folderName = folderName,
        )
    }

    // 阅读翻页写历史（首页统计与历史唯一上游）：必须 distinctUntilChanged 防滚动每帧写库，并记录翻页间隔时长。
    val lastPageTurnAtState = remember { mutableLongStateOf(0L) }
    var lastPageTurnAt by lastPageTurnAtState
    val favoriteFolderComicId = (route as? DesktopRoute.Detail)?.comicId
    // 夹名单（macOS 皮批）：自建空夹名单 ∪ 记录推导，建/删夹后 favoriteTick 重读。
    val favoriteFolderNames = (favoriteFolders + desktopFavoriteFolderNames(favorites)).distinct()
    val favoriteFolderCurrent = desktopFavoriteFolderOf(
        favoriteFolderComicId ?: "", favorites, favoriteFolderComicId != null && favoritesLoaded,
    )
    val favoriteFolderSelected = desktopFavoriteFolderChoice(
        favoriteFolderPicked, favoriteFolderCurrent, favoriteFolderNames,
    )

    // 收藏屏筛选夹状态在壳层现算并复用 favoriteFolderNames，保持单一数据源。
    val favoritesTabSelected = desktopFavoriteFolderTabChoice(favoritesTabPicked, favoriteFolderNames)
    val favoritesShown = desktopFavoriteFoldersShown(favoritesTabSelected, favorites)
    val favoritesFolderTabs = desktopFavoriteFolderTabRows(
        names = favoriteFolderNames,
        selected = favoritesTabSelected,
        favorites = favorites,
        countsKnown = favoritesLoaded,
    )
    val favoritesCountNote = desktopFavoriteFolderCountNote(
        folder = favoritesTabSelected,
        total = favorites.size,
        shown = favoritesShown.size,
        countsKnown = favoritesLoaded,
    )
    val favoritesFilterNote = desktopFavoriteFolderFilterNote(
        folder = favoritesTabSelected,
        total = favorites.size,
        shown = favoritesShown.size,
    )

    // 仅在详情页开启且收藏表已读回时输出收藏夹取证读数，避免未就绪时误报未收藏。

    // 搜索屏四格状态在壳层现算后传入屏组件，避免判据在壳与屏之间分叉。
    val isAggregateTarget = aggregateSearch.target is com.venera.desktop.search.DesktopSearchTarget.Aggregate
    val searchHistoryChips = desktopComicSearchHistoryChips(
        history = searchHistory,
        query = searchQuery,
        searching = if (isAggregateTarget) aggregateSearch.searching else searching,
        resultCount = if (isAggregateTarget) aggregateSearch.lanes.values.sumOf { it.cards.size } else searchResults.size,
    )
    val searchMoreState = desktopComicSearchMoreState(
        loadingMore = loadingMore,
        canLoadMore = searchCanLoadMore,
        hasSearched = hasSearched,
        resultsEmpty = searchResults.isEmpty(),
        reason = searchCause,
    )
    val searchFailedFooter =
        if (searchMoreState == DesktopComicSearchMore.Failed) {
            desktopComicSearchFailedFooter(searchCause)
        } else {
            null
        }
    val searchEmptyNote = desktopComicSearchEmptyNote(
        query = searchQuery.trim(),
        sourceKey = DESKTOP_EXPLORE_SOURCE,
        searched = hasSearched,
        searching = searching,
        resultCount = searchResults.size,
        reason = searchCause,
    )

    // 画廊墙账本（null 表示尚未加载）；首屏 effect 键不含 wallMoreTick，避免翻页时重置整面墙。
    val wallPageState = remember { mutableStateOf<DesktopWallPage?>(null) }
    var wallPage by wallPageState
    // 画廊首页内容域切换（BOORU 混排 vs PIXIV 榜单/推荐），置于壳层并作为首屏 effect 键以在切域时整墙重拉。
    val galleryHomeDomainState = remember { mutableStateOf(GalleryHomeDomain.BOORU) }
    var galleryHomeDomain by galleryHomeDomainState
    // Pixiv 排行榜档位（默认日榜），作为 PIXIV 墙取数参数与 effect 键；BOORU 域下不触发重拉。
    val galleryRankingModeState = remember {
        mutableStateOf(com.venera.compose.gallery.data.PixivRankingMode.DAILY)
    }
    var galleryRankingMode by galleryRankingModeState
    // BOORU 域主墙的排序档（推荐 / 最新 / 最热），默认**推荐**。
    // 与 `galleryHomeDomain` 同一族纪律：状态住壳（切档 = 换真取数腿），三档都是真腿
    //（判据住在 `gallery/ui/DesktopGallerySort.kt`）。PIXIV 域不读这一枚（那一域切的是 rankingMode）。
    val gallerySortState = remember { mutableStateOf(DesktopGallerySort.RECOMMENDED) }
    var gallerySort by gallerySortState
    // 墙上与每日热门取哪一档清晰度：设置流的**当前值**，取数腿现取现用（`collectAsState` 都行，
    // 但键里不放它 ——用户的持久化口径：改清晰度不该把已经摆在屏上的整墙重取一轮，
    // 那一刀由下面那枚手动刷新扳机负责）。
    val galleryWallQuality = currentWallQuality(galleryPrefs)
    // 会话级墙缓存按 (域, pixiv 档, booru 排序) 缓存整页对象，切回直接复用；wallRefreshTick 强制重取并覆盖缓存。
    val wallCache = remember { mutableStateMapOf<String, DesktopWallPage>() }
    val wallRefreshTickState = remember { mutableIntStateOf(0) }
    var wallRefreshTick by wallRefreshTickState
    val lastAppliedRefreshTickState = remember { mutableIntStateOf(-1) }
    var lastAppliedRefreshTick by lastAppliedRefreshTickState
    // 刷新进行中：只驱动一条细进度指示，旧墙照常留屏（与首屏"页也空+转圈"分开）。
    val wallRefreshingState = remember { mutableStateOf(false) }
    var wallRefreshing by wallRefreshingState
    // 将 Pixiv 登录态纳入组合与缓存键（区分 REC/RANK），确保登录态变化时自动切换推荐流与榜单流。
    val pixivAuthState by (
        ports.pixivAccount?.authState
            ?: constFlow(com.venera.compose.gallery.data.PixivAuthState.Anonymous)
        ).collectAsState()
    // 用户第 5 点：侧栏设置上方图标和账号页显示登录 Pixiv 账号的真实头像
    val pixivAccountAvatarUrl by produceState<String?>(null, pixivAuthState, ports.pixiv) {
        val signed = pixivAuthState as? com.venera.compose.gallery.data.PixivAuthState.SignedIn
        val uid = signed?.userId?.toLongOrNull()
        value = if (uid != null && ports.pixiv != null) {
            withContext(Dispatchers.IO) {
                ports.pixiv?.userHead(uid)?.getOrNull()?.avatarUrl
            }
        } else null
    }
    // 墙缓存键仅在 PIXIV 域拼接 REC/RANK 后缀（防 token 续期误刷 BOORU 墙），且必须包含当前遮罩档。
    val wallCacheKey = remember(galleryHomeDomain, galleryRankingMode, gallerySort, pixivAuthState, currentMaskMode) {
        val leg = if (galleryHomeDomain == GalleryHomeDomain.PIXIV) {
            if (pixivAuthState is com.venera.compose.gallery.data.PixivAuthState.SignedIn) "REC" else "RANK"
        } else {
            "-"
        }
        "${galleryHomeDomain}|${galleryRankingMode}|${gallerySort}|$leg|$currentMaskMode"
    }
    val cards: List<DesktopWallCardModel> = wallPage?.cards.orEmpty()
    // 看图屏 ←/→ 的队列：只收主墙当前这轮**点得开**的卡。搜索 / 收藏 / 日榜里的卡若也
    // 恰好在这面墙上，就沿用同一队列；不在时看图屏退回单张（由下面路由支路判）。
    val galleryViewerQueue = remember(wallPage) { cards.filter { it.openable } }

    // 「下一页」那一笔自己的相位（**不进**账本：那是 UI 的等待形状，批 23 定过的那条口径）。
    val wallPhaseState = remember { mutableStateOf<DesktopGalleryMorePhase>(DesktopGalleryMorePhase.Idle) }
    var wallPhase by wallPhaseState
    // 按了几次「下一页」，而不是"翻到第几页"—— 那颗教训：失败重按同一页时
    // 页码没变 ⇒ 靠页码做键那颗 effect 不会重跑，那枚把手按了没反应。
    val wallMoreTickState = remember { mutableIntStateOf(0) }
    var wallMoreTick by wallMoreTickState
    // 壳层只保留轻量滚动锚点（两个 Int），Lazy*State 放到各自路由/Tab 分支内创建并离场销毁，避免锁死整棵离场节点树与 Skia 对象。
    val wallListAnchor = remember { DesktopScrollAnchor() }
    val wallStaggeredAnchor = remember { DesktopScrollAnchor() }
    val searchResultAnchor = remember { DesktopScrollAnchor() }
    val searchArtistAnchor = remember { DesktopScrollAnchor() }
    val exploreAnchor = remember { DesktopScrollAnchor() }
    // pixiv 画师页作品墙：进作品返回后按锚点恢复滚动位（换画师/换段由分支里 reset）。
    val artistProfileAnchor = remember { DesktopScrollAnchor() }
    // pixiv 多图作品的播放器页队列与返回页号：详情页点大图喂全页表（左右翻同一篇其他页），
    // 播放器退出把翻到的页号写回，详情页中央大图接着停在那一张。
    val pixivWorkViewerQueueState = remember {
        mutableStateOf(emptyList<com.venera.desktop.gallery.ui.DesktopWallCardModel>())
    }
    var pixivWorkViewerQueue by pixivWorkViewerQueueState
    val pixivWorkResumePageState = remember { mutableIntStateOf(0) }
    var pixivWorkResumePage by pixivWorkResumePageState
    val wallLayout by (galleryPrefs?.wallLayout
        ?: constFlow(com.venera.compose.gallery.domain.GalleryWallLayout.Staggered)).collectAsState()


    // 页尾那三格**成品**（判据住在 `gallery/ui/DesktopGalleryWallFooter.kt`，屏只搬）——
    // 与搜索那四格、收藏那四格同一手法。
    val wallFooter = desktopGalleryWallFooter(
        batch = wallPage?.batch,
        onScreen = cards.size,
        phase = wallPhase,
        unit = "张",
    )

    // 首页每日热门两源账本（图库与 Pixiv 独立分栏展示），合并状态对象以控制顶层 Composable 槽位数。
    val dailyGallery = remember(railPrefs) { DailyGalleryUiState(railPrefs?.getBoolean("pref_daily_home_pixiv", false) ?: false) }
    // 取到过非空一轮就不再重取；只有刷新或重试扳机把读数与两账一起清掉才重发。

    // Pixiv 榜单预览条走独立匿名榜单腿并按档缓存，与主墙（登录后切推荐流）账本分离。
    val pixivPreviewLegState = remember {
        mutableStateOf(PixivPreviewLeg(emptyList(), emptyMap(), emptyMap()))
    }
    var pixivPreviewLeg by pixivPreviewLegState
    val pixivPreviewCache = remember {
        mutableStateMapOf<com.venera.compose.gallery.data.PixivRankingMode, PixivPreviewLeg>()
    }
    val lastAppliedPreviewTickState = remember { mutableIntStateOf(-1) }
    var lastAppliedPreviewTick by lastAppliedPreviewTickState
    // ── 图库站：筛选架的状态（评分 / 子源；BOORU 域专用）──
    // 过滤是**投影之后**的浏览层过滤（desktopFilteredWallPage），不重发站方取数；
    // 排序三档沿用既有 DesktopGallerySort（真取数腿）。子源胶囊在 scaffold 就地按枚举生成。
    val booruRatingFilterState = remember { mutableStateOf(DesktopGalleryRatingFilter.All) }
    var booruRatingFilter by booruRatingFilterState
    val booruSiteFilterState = remember { mutableStateOf<GallerySite?>(null) }
    var booruSiteFilter by booruSiteFilterState
    // 行高密度三档（盘上记忆；ordinal 落键）——行对齐墙（两域共用渲染层）吃这一档。
    val wallDensityState = remember(railPrefs) {
        mutableStateOf(
            DesktopWallDensity.entries.getOrElse(
                railPrefs?.getInt("pref_wall_density", 1) ?: 1,
            ) { DesktopWallDensity.Standard },
        )
    }
    var wallDensity by wallDensityState
    // 主画廊墙的多选账（Ctrl/Cmd 点、左上勾钮、右键「加入选卡」三处入口都落这里）。
    // 住壳根：切 tab 不丢；悬停坞的计数与批量动作都从它读。
    val wallSelectedUids = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    // Pixiv 发现带与关注新作仅在登录后拉取（未登录返回空表不占位），作品经唯一守卫起口投影，随刷新扳机与登录态重取。
    val pixivDiscoverLegState = remember {
        mutableStateOf(PixivDiscoverLeg(emptyList(), emptySet(), emptyList(), emptyMap(), ready = false))
    }
    var pixivDiscoverLeg by pixivDiscoverLegState
    // 关注钮的**本会话内**账（点过才记；全量关注名单住账号页那屏，首页不拉 —— 精确态不冒充）。
    val pixivLocalFollowedState = remember { mutableStateOf(emptySet<Long>()) }
    var pixivLocalFollowed by pixivLocalFollowedState
    // pixivision 最新文章（匿名腿，不走画廊预算体系）：失败/改版 = 空表，那一节如实不摆。
    // ── 图库站：标签维度舱（词典腿，BOORU 域）──
    // 计数取当轮墙账（真数得出），翻译与四组分类取词典 titleTags 单源；IO 里查（读盘不在组合期）。
    // 词典没接盘 = 空表 + 未接入措辞 —— 舱照常摆（通用组兜底），翻译一个不编。
    val tagDictionary = remember(galleryPaths) {
        galleryPaths?.let { com.venera.desktop.gallery.data.DesktopGalleryTagDictionary(it) }
    }
    val dailyCore = remember(ports) {
        com.venera.compose.gallery.ui.GalleryDailyCore(
            readSnapshot = {
                val paths = galleryPaths
                if (paths == null) null else com.venera.desktop.gallery.data.DesktopGalleryDailySnapshotStore.read(paths)
            },
            writeSnapshot = { snapshot ->
                val paths = galleryPaths
                if (paths != null) {
                    com.venera.desktop.gallery.data.DesktopGalleryDailySnapshotStore.write(paths, snapshot)
                }
            },
        ).also { core ->
            core.maskMode = maskModeOf()
            core.blockAi = wallBlockAiOf()
            val guard = wallGuardOf()
            if (guard != null) {
                core.blockedRuleOf = { post -> guard.blockedGalleryRule(author = post.author, tags = post.tagList)?.pattern }
            }
        }
    }
    // 聚合页的 pixiv 腿：三档真榜单（日 / 周 / 月），与图库三站同屏；不复用首页那颗布尔开关。
    // 四枚与档位表打成一颗：同 §每日热门账本，provider lambda 贴 255 槽顶。

    // 画廊标签搜索三组 effect（读历史、首屏、续页）复用画廊墙分页与守卫组件，状态置于壳层。
    val gallerySearchSourceOptions = desktopGallerySearchSourceOptions()

    // ── ：图库域的排行档表（按当前来源腿算真档）与窗口/期次读数 ──
    // Gelbooru 时间窗拦下的档根本不在表里（摆了就是假开关）；档兑现不了时 landing 落回默认。
    val gallerySearchToday = java.time.LocalDate.now(java.time.ZoneOffset.UTC)
    val gallerySearchLegs = gallerySearchSourceOptions[gallerySearchSource.coerceIn(0, gallerySearchSourceOptions.lastIndex)]
        .availableLegs(desktopGelbooruConfigured(ports))
    val gallerySearchRankingChoices = desktopRankingChoices(gallerySearchLegs, gallerySearchToday)
    val gallerySearchRankingLanding = desktopRankingLanding(gallerySearchLegs, gallerySearchRanking)
    val gallerySearchPeriodReading = desktopRankingAnchorReading(
        gallerySearchRankingLanding, gallerySearchRankingAnchor, gallerySearchToday,
    ).takeIf { it.isNotEmpty() }
    val gallerySearchCanPeriodBack = desktopRankingStep(
        gallerySearchRankingLanding, gallerySearchRankingAnchor, backwards = true, todayUtc = gallerySearchToday,
    ) != null
    val gallerySearchCanPeriodForward = desktopRankingStep(
        gallerySearchRankingLanding, gallerySearchRankingAnchor, backwards = false, todayUtc = gallerySearchToday,
    ) != null

    // 画廊标签联想补全：仅在搜索屏输入非空且未发车时防抖 250ms 查询可用腿，经 desktopMergeSuggestionLegs 合并。
    val pixivSearchCards: List<DesktopWallCardModel> = pixivSearchPage?.cards.orEmpty()
    val pixivSearchFooter = desktopGalleryWallFooter(
        batch = pixivSearchPage?.batch,
        onScreen = pixivSearchCards.size,
        phase = pixivSearchPhase,
        unit = "张",
    )
    val gallerySearchCards: List<DesktopWallCardModel> = gallerySearchPage?.cards.orEmpty()
    val gallerySearchFooter = desktopGalleryWallFooter(
        batch = gallerySearchPage?.batch,
        onScreen = gallerySearchCards.size,
        phase = gallerySearchPhase,
        unit = "张",
    )

    // 进入作品详情/看图屏时记录浏览历史：遮罩卡片与无法还原完整帖的条目不记，并同步更新内存 browseRows。






    // 具名共享 DesktopUxPlatformServices 实例，确保 LocalUxPlatformServices 与详情共享屏端口使用同一遮罩判据。
    val desktopUx = remember(maskModeOf, blockedByUserOf, currentMaskMode) {
        DesktopUxPlatformServices(
            maskMode = maskModeOf,
            blockedByUser = blockedByUserOf,
        )
    }

    // 详情页那 17 族端口的装配句柄。三枚读数缺任何一枚 ⇒ `null`：详情支路如实念
    // "装配没接上"，不拿空端口假装能取数（与"没有写口就没有那枚钮"同一条口径）。
    val latestDownloadsRoot by androidx.compose.runtime.rememberUpdatedState(downloadsRoot)
    val businessHandle = remember(desktopUx, engine, assetDir, overrideDir, galleryPaths, galleryGuard) {
        val paths = galleryPaths
        val guard = galleryGuard
        val dir = assetDir
        if (paths == null || guard == null || dir == null) {
            null
        } else {
            com.venera.desktop.business.DesktopBusinessPorts.Handle(
                engine = engine,
                assetDir = dir,
                overrideDir = overrideDir,
                paths = paths,
                downloadsRoot = { latestDownloadsRoot },
                coverMask = { sourceKey, title, author, tags, comicId ->
                    desktopUx.coverMaskStateOf(sourceKey, title, author, tags, comicId, "")
                },
                rules = guard,
                guardMaskMode = guard.nsfwMaskMode,
            )
        }
    }


    val homeSkinLight = com.venera.desktop.settings.DesktopHomeSkin.mode.collectAsState().value ==
        com.venera.desktop.settings.DesktopHomeSkin.LIGHT
    val pixivSuggestEngine = remember(galleryPaths) {
        galleryPaths?.let { com.venera.desktop.gallery.data.DesktopGalleryHandle.of(it).engine }
    }
    val desktopShellBackFn: ((DesktopRoute) -> Unit) -> (() -> Unit)? = ::desktopShellBackWithSearch
    return DesktopLargeScreenState(
        layoutMode = layoutMode,
        scope = scope,
        desktopUx = desktopUx,
        businessHandle = businessHandle,
        queueTasks = queueTasks,
        queueConcurrency = queueConcurrency,
        queuePrimed = queuePrimed,
        favoritesLoaded = favoritesLoaded,
        favoritesState = favoritesState,
        favoriteFolderNames = favoriteFolderNames,
        localComicScan = localComicScan,
        readerMode = readerMode,
        readerModeNote = readerModeNote,
        detailCoreCache = detailCoreCache,
        sourceUi = sourceUi,
        filesUi = filesUi,
        detailUi = detailUi,
        aggregateSearch = aggregateSearch,
        runSourceImport = runSourceImport,
        openSettings = ::openSettings,
        onDownloadsDelete = { ch, forceDirect -> onDownloadsDelete(ch, forceDirect) },
        flyToDownloadCenter = ::flyToDownloadCenter,
        routeState = routeState,
        currentTabState = currentTabState,
        commentsTickState = commentsTickState,
        readerPagesState = readerPagesState,
        searchReturnRouteState = searchReturnRouteState,
        sourceTickState = sourceTickState,
        downloadsTickState = downloadsTickState,
        localComicImportTickState = localComicImportTickState,
        armedDeleteKeyState = armedDeleteKeyState,
        readerModeChoiceState = readerModeChoiceState,
        searchQueryState = searchQueryState,
        searchSourceKeyState = searchSourceKeyState,
        searchTickState = searchTickState,
        wallDensity = wallDensity,
        artistProfileAnchor = artistProfileAnchor,
        dailyCore = dailyCore,
        dailyGallery = dailyGallery,
        galleryAuxUi = galleryAuxUi,
        openWallCardAny = openWallCardAny,
        pixivAccountTabState = pixivAccountTabState,
        pixivArtistSectionState = pixivArtistSectionState,
        lastPixivArtistIdState = lastPixivArtistIdState,
        pixivSearchKindState = pixivSearchKindState,
        pixivSearchTermState = pixivSearchTermState,
        pixivSearchTickState = pixivSearchTickState,
        gallerySearchModeState = gallerySearchModeState,
        pixivWorkViewerQueueState = pixivWorkViewerQueueState,
        pixivWorkResumePageState = pixivWorkResumePageState,
        railPrefs = railPrefs,
        overrideDir = overrideDir,
        historySourceName = historySourceName,
        readingStats = readingStats,
        galleryFavorites = galleryFavorites,
        galleryWallQuality = galleryWallQuality,
        galleryWallCards = galleryWallCards,
        galleryWallBlocked = galleryWallBlocked,
        galleryWallUnrenderable = galleryWallUnrenderable,
        galleryViewerQueue = galleryViewerQueue,
        gallerySearchToday = gallerySearchToday,
        gallerySearchSourceOptions = gallerySearchSourceOptions,
        gallerySearchRankingChoices = gallerySearchRankingChoices,
        gallerySearchRankingLanding = gallerySearchRankingLanding,
        gallerySearchPeriodReading = gallerySearchPeriodReading,
        gallerySearchCanPeriodBack = gallerySearchCanPeriodBack,
        gallerySearchCanPeriodForward = gallerySearchCanPeriodForward,
        gallerySearchCards = gallerySearchCards,
        gallerySearchFooter = gallerySearchFooter,
        pixivSearchCards = pixivSearchCards,
        pixivSearchFooter = pixivSearchFooter,
        pixivCollectionSearch = pixivCollectionSearch,
        searchResultAnchor = searchResultAnchor,
        searchArtistAnchor = searchArtistAnchor,
        backupUi = backupUi,
        browseClearUi = browseClearUi,
        openGalleryPostAny = ::openGalleryPostAny,
        savedUiState = savedUiState,
        historyTickState = historyTickState,
        galleryWallTickState = galleryWallTickState,
        gallerySearchQueryState = gallerySearchQueryState,
        gallerySearchSourceState = gallerySearchSourceState,
        gallerySearchTickState = gallerySearchTickState,
        gallerySearchMoreTickState = gallerySearchMoreTickState,
        gallerySearchR18State = gallerySearchR18State,
        gallerySearchPageState = gallerySearchPageState,
        gallerySearchSearchingState = gallerySearchSearchingState,
        gallerySearchFailedState = gallerySearchFailedState,
        gallerySearchHistoryState = gallerySearchHistoryState,
        gallerySearchHistoryNoteState = gallerySearchHistoryNoteState,
        gallerySearchRankingState = gallerySearchRankingState,
        gallerySearchRankingAnchorState = gallerySearchRankingAnchorState,
        pixivSearchMoreTickState = pixivSearchMoreTickState,
        pixivSearchFilterState = pixivSearchFilterState,
        pixivSearchPageState = pixivSearchPageState,
        pixivSearchSearchingState = pixivSearchSearchingState,
        pixivSearchUsersState = pixivSearchUsersState,
        gallerySuggestCandidatesState = gallerySuggestCandidatesState,
        currentTab = currentTab,
        homeSkinLight = homeSkinLight,
        pixivSuggestEngine = pixivSuggestEngine,
        exploreCategoryBoard = exploreCategoryBoard,
        explorePickUi = explorePickUi,
        favoritesNote = favoritesNote,
        netFavUi = netFavUi,
        searchFilterGroups = searchFilterGroups,
        homeSourceRows = homeSourceRows,
        homeHistory = homeHistory,
        homeStats = homeStats,
        homeArtists = homeArtists,
        favoritesShown = favoritesShown,
        favoritesFolderTabs = favoritesFolderTabs,
        favoritesCountNote = favoritesCountNote,
        favoritesFilterNote = favoritesFilterNote,
        searchHistoryChips = searchHistoryChips,
        searchMoreState = searchMoreState,
        searchFailedFooter = searchFailedFooter,
        searchEmptyNote = searchEmptyNote,
        wallListAnchor = wallListAnchor,
        wallStaggeredAnchor = wallStaggeredAnchor,
        exploreAnchor = exploreAnchor,
        wallLayout = wallLayout,
        wallFooter = wallFooter,
        pixivPreviewLeg = pixivPreviewLeg,
        wallSelectedUids = wallSelectedUids,
        exploreSectionsState = exploreSectionsState,
        exploreNoteState = exploreNoteState,
        exploreTickState = exploreTickState,
        exploreSourceKeyState = exploreSourceKeyState,
        exploreModesState = exploreModesState,
        exploreModeIndexState = exploreModeIndexState,
        explorePickTickState = explorePickTickState,
        explorePickMoreTickState = explorePickMoreTickState,
        explorePickPageNoState = explorePickPageNoState,
        homeRecommendComicsState = homeRecommendComicsState,
        homeRecommendNoteState = homeRecommendNoteState,
        favoritesTabPickedState = favoritesTabPickedState,
        favoritesSourceKeyState = favoritesSourceKeyState,
        discoverViewExploreState = discoverViewExploreState,
        searchResultsState = searchResultsState,
        searchingState = searchingState,
        searchFilterValuesState = searchFilterValuesState,
        searchMaxPageState = searchMaxPageState,
        searchCanLoadMoreState = searchCanLoadMoreState,
        hasSearchedState = hasSearchedState,
        searchHistoryState = searchHistoryState,
        searchHistoryNoteState = searchHistoryNoteState,
        searchCauseState = searchCauseState,
        loadMoreTickState = loadMoreTickState,
        homeRecommendR18State = homeRecommendR18State,
        homeRecommendFailedState = homeRecommendFailedState,
        homeRetryTickState = homeRetryTickState,
        filesSegmentState = filesSegmentState,
        wallPageState = wallPageState,
        galleryHomeDomainState = galleryHomeDomainState,
        galleryRankingModeState = galleryRankingModeState,
        gallerySortState = gallerySortState,
        wallRefreshTickState = wallRefreshTickState,
        wallRefreshingState = wallRefreshingState,
        wallMoreTickState = wallMoreTickState,
        booruRatingFilterState = booruRatingFilterState,
        booruSiteFilterState = booruSiteFilterState,
        wallDensityState = wallDensityState,
        pixivDiscoverLegState = pixivDiscoverLegState,
        pixivLocalFollowedState = pixivLocalFollowedState,
        comicCache = comicCache,
        exploreSections = exploreSections,
        favoriteFolderComicId = favoriteFolderComicId,
        favoriteFolderCurrent = favoriteFolderCurrent,
        favoriteFolderSelected = favoriteFolderSelected,
        favoritesTabSelected = favoritesTabSelected,
        favoritesLoadedState = favoritesLoadedState,
        favoritesNoteState = favoritesNoteState,
        favoriteFolderPickedState = favoriteFolderPickedState,
        localComicScanState = localComicScanState,
        lastPageTurnAtState = lastPageTurnAtState,
        currentMaskMode = currentMaskMode,
        pixivAuthState = pixivAuthState,
        wallCache = wallCache,
        wallCacheKey = wallCacheKey,
        cards = cards,
        pixivPreviewCache = pixivPreviewCache,
        tagDictionary = tagDictionary,
        galleryWallCardsState = galleryWallCardsState,
        galleryWallBlockedState = galleryWallBlockedState,
        galleryWallUnrenderableState = galleryWallUnrenderableState,
        lastAppliedRefreshTickState = lastAppliedRefreshTickState,
        wallPhaseState = wallPhaseState,
        pixivPreviewLegState = pixivPreviewLegState,
        lastAppliedPreviewTickState = lastAppliedPreviewTickState,
        wallPage = wallPage,
        searchFilterGroupsState = searchFilterGroupsState,
        searchPageState = searchPageState,
        loadingMoreState = loadingMoreState,
        gallerySearchPhaseState = gallerySearchPhaseState,
        gallerySearchJobState = gallerySearchJobState,
        pixivSearchPhaseState = pixivSearchPhaseState,
        pixivSearchUsersCursorState = pixivSearchUsersCursorState,
        exploreCategoryBoardState = exploreCategoryBoardState,
        pixivCallback = pixivCallback,
        homeLines = homeLines,
        homeLatencyByHost = homeLatencyByHost,
        homeProbeStamp = homeProbeStamp,
        homeProbingHosts = homeProbingHosts,
        homeFailedHosts = homeFailedHosts,
        homeScope = homeScope,
        performSourceUpdateCheck = performSourceUpdateCheck,
        favoriteFoldersState = favoriteFoldersState,
        routeBeforeSettingsState = routeBeforeSettingsState,
        settingsOpen = settingsOpen,
        contentRoute = contentRoute,
        desktopShellBackWithSearch = desktopShellBackFn,
        shellMode = shellMode,
        fullscreenChrome = fullscreenChrome,
        railExpandedSavedState = railExpandedSavedState,
        pixivAccountAvatarUrl = pixivAccountAvatarUrl,
        flyState = flyState,
        flyAnchor = flyAnchor,
        flyHostTopLeftState = flyHostTopLeftState,
        flyPointer = flyPointer,
    )
}
