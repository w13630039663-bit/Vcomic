package com.venera.desktop.app

import com.venera.desktop.ui.theme.VeneraType
import androidx.compose.ui.input.key.key

import androidx.compose.foundation.clickable
import com.venera.compose.feature.ComicDetailCore
import androidx.compose.foundation.layout.size
// 主墙列数吃**实测内容宽**（量这一格，不从窗宽反推侧栏）。
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import com.venera.desktop.platform.DesktopPixivCallback
import com.venera.desktop.ui.components.DesktopPetCue
import com.venera.desktop.ui.components.DesktopPetCueBus
import com.venera.desktop.ui.components.DesktopPetPayload
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
// 桌面自绘的首页 chrome 排 / 站源读数排 / 主墙节头（域切换器仍复用安卓同一颗件）。
import com.venera.desktop.gallery.ui.DesktopGalleryRatingFilter
import com.venera.desktop.gallery.ui.DesktopWallDensity
import com.venera.desktop.gallery.ui.desktopFilteredWallPage
import com.venera.desktop.gallery.ui.DesktopGalleryMorePhase
import com.venera.desktop.gallery.ui.DesktopGalleryWallFooter
import com.venera.desktop.gallery.ui.desktopGalleryWallFooter
import androidx.compose.runtime.mutableStateMapOf
// ── ：画廊那一域的标签搜索 ──
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GallerySearchEntry
import com.venera.compose.gallery.domain.GallerySearchSource
import com.venera.desktop.gallery.ui.desktopGelbooruConfigured
// 「最近搜索那份记不住」那句话的唯一出处（那颗件）：画廊搜索复用同一颗，
// 不在这里另写一份同样的串（两处各写就会漂成两套措辞）。
// BOORU 主墙的三档排序（推荐 / 最新 / 最热）与那颗首屏件，壳按档分派真取数腿。
import com.venera.desktop.gallery.ui.DesktopGallerySort
// 把「点标签回搜」那一枚把手接上看图那一屏 —— 消费者在 `DesktopGalleryPostViewer`，
// 落点这一侧靠 `desktopSearchSeedFor` 认所属站、壳里这一侧 provide 交接槽 + 装回搜索那一屏的两档。
import com.venera.desktop.gallery.ui.LocalDesktopSearchHandoff
import com.venera.desktop.gallery.ui.desktopSearchSeedFor
import com.venera.desktop.gallery.data.DesktopLocalComicLibraryScan
// ── ：画廊帖子的浏览历史（门 + 写侧 + 回原卡）──
import com.venera.desktop.gallery.data.DesktopGalleryBrowseHistory
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
import com.venera.desktop.gallery.ui.desktopComicDeleteChapter
import com.venera.desktop.gallery.ui.desktopComicDeleteRowKey
import com.venera.desktop.gallery.ui.desktopComicDeleteScreenLine
import com.venera.desktop.gallery.ui.desktopComicDeleteShouldFire
import com.venera.desktop.gallery.ui.desktopComicDeleteNextKey
// 第 140 批 · 主题层：桌面第一次吃到安卓那一套 token 与色板（薄壳见 `DesktopThemeBridge.kt`）。
import com.venera.desktop.gallery.ui.DesktopVeneraTheme


/**
 * 桌面大屏壳 —— **复用安卓大屏那一份 UI**，不是另做一套。
 *
 * ## 为什么现在才这样写
 *
 * 此前桌面侧有两条"平行线"：一条是 Fluent 风格的 `desktop/gallery/ui/`（72 颗），
 * 一条是照着大屏断点重画的 `desktop/ui/`（9 颗 pane）。两条都在**维护第二份 UI**，
 * 正是用户最初说「这样维护太麻烦了」要解决的问题。
 *
 * 这一版把两条都换成「同一份源码两端编译」：
 *  - 侧栏 = `components/VeneraSideBar.kt`（安卓大屏那一颗，逐字同一份）；
 *  - 断点 = `components/WideScreenPolicy.kt` 的 `wideScreenLayoutMode` / `sideBarModeFor`；
 *  - 网格列数 = `imageWallColumnCount` + `imageWallHorizontalPadding`（与安卓同一把尺）；
 *  - 卡片 = `components/venera/VeneraCard.kt` + `VeneraCover.kt`；
 *  - 色板 / 间距 / 字号 = `ui/tokens/`（两端同一份 token）。
 *
 * 桌面侧**自持**的只剩这一颗装配文件（窗口、进程、数据接线那些平台事），
 * UI 本身一行没抄。
 *
 * ## 窗口宽怎么进来
 *
 * `LocalWindowWidth`（`components/LocalWindowWidth.kt`）由这里 provide —— 值取自
 * Compose `Window` 的实际尺寸。Android 侧由 `MainActivity` provide `screenWidthDp`。
 * 两端同一条调用式、各自供给，这就是方案文档 §1.5 说的"收口"。
 */
@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
internal fun VeneraDesktopLargeScreen(
    windowWidth: Dp,
    ports: GalleryPorts,
    modifier: Modifier = Modifier,
    /**
     * 分级遮罩档的当前值（`OFF` / `BLUR` / `HIDE`），由装配点递进来。
     *
     * 这两个形参是为修验收发现的 R3 加的 —— `LocalUxPlatformServices`
     * 此前**从未 provide**，桌面一直吃 `NoOpUxPlatformServices`，它的
     * `coverMaskStateOf(...)` 恒返回 `"VISIBLE"` ⇒ 共享零件里的封面**全不打码**。
     *
     * 为什么不在 UI 里自己去取：`DesktopUiIsolationTest` ② 那条「UI 里不许找静态口」。
     * 装配点（`VeneraDesktop.kt`）手上有 `DesktopGalleryHandle`，让它递进来就对。
     */
    maskModeOf: () -> String = { "BLUR" },
    /** 用户黑名单是否命中该条（KEYWORD / TAG / AUTHOR 规则）。命中 ⇒ `HIDDEN`。 */
    blockedByUserOf: (sourceKey: String, title: String, author: String, tags: List<String>) -> Boolean =
        { _, _, _, _ -> false },
    /**
     * 取数层要的那颗守卫（它自己调 `blockedGalleryRule`）。
     *
     * 修安全评审 **B**：`DesktopGalleryWallPolicy.of` 此前在 `desktop/src/main`
     * 里**零调用点**，而 `desktopWallCardOf` 的 `masked` 默认 `false` ⇒
     * **墙上无论档位如何，一张图都不遮**。
     *
     * 缺省 `null` 时取数层会打印 `D_守卫 未判` —— 不静默，但也不假装判过。
     */
    wallGuardOf: () -> com.venera.compose.gallery.data.GalleryContentGuard? = { null },
    /** `prefs.galleryBlockAi` —— 墙上的 AI 角标那一档。 */
    wallBlockAiOf: () -> Boolean = { false },
    /**
     * 源脚本引擎。详情与阅读器都要它：`details()` 给章节表、`pages()` 给页表。
     *
     * `null` 时详情/阅读器会如实说"引擎没起来"，不假装有内容。
     */
    engine: com.venera.engineprobe.EngineSession? = null,
    // ── 自绘标题栏的窗控三跳(侧栏视图④)──
    // 窗口操作是**装配点**的事(那颗 Window 作用域在手),壳只管把顶栏摆出来。
    // 三枚可空:装配点没递(如探针窗)⇒ 窗控三钮整组不摆(摆了按不动 = 假门)。
    onWindowMinimize: (() -> Unit)? = null,
    onWindowToggleMaximize: (() -> Unit)? = null,
    windowMaximized: Boolean = false,
    /**
     * 整窗糊图背板的图源交给装配点。
     *
     * 详情页的背板必须在**窗口根**铺一次：铺在内容槽里会被侧栏那 24dp 内边距与
     * 46dp 顶栏切成两条硬边；铺两次（页内 + 顶栏）则两套取景对不齐。
     * 非空 ⇒ 窗口根画这一张，壳内各层不再自绘；null ⇒ 恢复各自原样。
     */
    onShellBackdropChange: (String?, Boolean) -> Unit = { _, _ -> },
    onWindowClose: (() -> Unit)? = null,
    /** 自绘标题栏的拖拽修饰；由装配点提供宿主对应的实现（AWT/Nucleus）。 */
    windowDragModifier: Modifier = Modifier,
    /** 写阅读历史那一跳。页码一变就调它 —— **这是首页统计/历史的唯一上游**。 */
    writeHistory: (suspend (com.venera.compose.data.db.HistoryRecord) -> Unit)? = null,
    /**
     * 读这一本的阅读进度（`comic_history` 那一行）。`null` ⇒ 详情那一屏那枚「开始/继续阅读」
     * 胶囊**整颗不摆**（与收藏钮同一条口径：没有读口就不留一枚按了没反应的把手）。
     *
     * 为什么递单条读口而不是把 DAO 递进 UI：屏不该能读全表（`VeneraDesktop.kt` 那条
     * "只递 saveHistory 这一个动作"的口径），而这里要的就是单条。
     */
    readHistoryRow: (suspend (String) -> com.venera.compose.data.db.HistoryRecord?)? = null,
    /**
     * 写分级遮罩档。
     *
     * 安全评审 **H4**：`DesktopGalleryContentGuard.setNsfwMaskMode` 此前**零调用点**
     * ⇒ 用户没有任何入口能改这个档 ⇒ 遮罩永远停在默认 `OFF`（= 什么都不遮）。
     *
     * 这一格的**界面**从源管理屏挪进了设置中枢（「探索与卡片」组）——
     * 分级遮罩与「源」无关，源管理那一屏如今是主从双栏的源详情面。写口本身照旧经这里。
     */
    setMaskMode: ((String) -> Boolean)? = null,
    /** 写「屏蔽 AI 图」。同一个理由，同一个入口（设置中枢 · 画廊与浏览组）。 */
    setBlockAi: ((Boolean) -> Unit)? = null,
    /** 读源清单（key + 显示名 + 是否启用）。 */
    readSources: (() -> List<com.venera.desktop.sourcemanage.DesktopSourceRowUi>)? = null,
    /** 写某颗源的启停。 */
    writeSourceEnabled: ((String, Boolean) -> Unit)? = null,
    /** 写站点墙拖出来的顺序（key 有序表）。null = 拖拽排序整条腿不摆。 */
    writeSourceOrder: ((List<String>) -> Unit)? = null,
    /**
     * 读某一颗源**现在实际会用到的那一篇**原文（源编辑）。
     *
     * 口径必须是"覆盖层优先"而不是"随包那一篇"：用户改过一次之后再进编辑器，
     * 屏上摆出厂原文而他改的那几行看不见，下一次保存就会把改动整笔打回。
     * 装配点用 `DesktopSourceNames.scriptOf(assetDir, key, overrideDir)` 取，与装载同一个次序。
     */
    readSourceText: ((String) -> String?)? = null,
    /**
     * 校验并落盘一篇改写。交回 `null` = 成了；非 null = 失败成因（屏上原地念，**不关页**）。
     *
     * 校验必须在写之前（对岸那课：退出即写会把坏脚本静默装进下次启动），
     * 而校验要用引擎 —— 所以这一颗由装配点拿着 `uiEngine` 造，UI 只管"按下并等回话"。
     */
    saveSourceText: (suspend (key: String, content: String) -> String?)? = null,
    /** 撤掉某一颗的改写（回出厂那一篇）。`null` = 这一路没接上 ⇒ 那枚把手不摆。 */
    clearSourceOverride: (suspend (String) -> Unit)? = null,
    /**
     * 跑手填凭据那一趟（「网页登录」那一形）。交回**要上屏的那一句话**，
     * 三档（没登上 / 源认了而盘上一枚没落 / 真登上了）全在装配点那一侧判
     * （`DesktopCookieLogin.screenNote`），屏一个字都不自己拼。
     *
     * `null` = 装配点没接上这一路 ⇒ 源管理屏那枚「登录」把手不摆（§119）。
     * 为什么是一根 lambda 而不是把引擎递进壳：引擎/数据目录那颗罐都住在装配点，
     * 而屏侧连"回核要读哪颗文件"都不该知道（§143/§144 同一族分层）。
     */
    loginWithCookies: (suspend (key: String, values: List<String>) -> String)? = null,
    /**
     * 账号密码登录。与 [loginWithCookies] 同一条分层：引擎和源数据罐留在装配点，
     * 屏只交用户名/密码并接收一句成品结果。
     */
    loginWithPassword: (suspend (key: String, account: String, password: String) -> String)? = null,
    /**
     * 网页登录（调用 Edge 独立窗口登录并自动同步 Cookie）。
     */
    loginWithWebview: (suspend (key: String) -> String)? = null,
    /** 读收藏（返回 Pair：列表, 是否已读回）。 */
    readFavorites: (suspend () -> Pair<List<com.venera.compose.data.db.FavoriteRecord>, Boolean>)? = null,
    /** 读自建空夹名单（主 prefs stringset；空夹落点，macOS 皮批）。 */
    readFavoriteFolders: (suspend () -> List<String>)? = null,
    /** 新建本地夹（判重/落盘在装配点）。 */
    createFavoriteFolder: (suspend (String) -> Unit)? = null,
    /** 删除本地夹（成员先移回「默认」再删名单，拍板）。 */
    deleteFavoriteFolder: (suspend (String) -> Unit)? = null,
    /** 墙卡画师名点击（11-06 卡）。null = 用壳内搜索交接槽缺省（全站搜画师名）。 */
    onOpenCardArtist: ((com.venera.desktop.gallery.ui.DesktopWallCardModel) -> Unit)? = null,
    /** 移出收藏。 */
    removeFavorite: (suspend (String) -> Unit)? = null,
    /**
     * 这一本收没收过（详情页那枚钮的三态里 `null` 与 `false` 就靠它分开）。
     *
     * 为什么不用 `FavoriteDao.toggleFavorite`：那颗读的是 `_favoritesFlow.value` 判"存不存在"，
     * 而那颗流是 `scope.launch` 发起即返回的（与 `readFavorites` 踩过同一个坑）⇒
     * 冷启动头几按下它会把"还没读回来"当成"没收过"，于是**再加一遍**而不是取消。
     * 这里由壳拿着真查库的结果显式选 `add` / `remove`，那颗坑就绕过去了。
     */
    isFavoriteOf: (suspend (String) -> Boolean)? = null,
    /** 收进收藏。`null` 而 `isFavoriteOf` 有值 = 只能看不能改 ⇒ 屏上那枚钮**不摆**。 */
    addFavorite: (suspend (com.venera.compose.data.db.FavoriteRecord) -> Unit)? = null,
    /** 搜索：给关键词，回结果。 */
    /**
     * 搜索：给关键词、**页码**与**按源动态筛选值表**（按该源 `search.optionList` 次序，null =
     * 该格未选），回那一趟的答复。
     *
     * 把返回类型从 `List<ComicCard>` 换成 [DesktopComicSearchOutcome]，为的是
     * 那一趟带的**另外两格**别再被装配点咽掉：源声明的总页数（翻页判据要用）与站方信封里那句
     * `__err`（「该源没有 search」从前被 `getOrDefault(emptyList())` 整笔抹掉，屏上就念成
     * 「这个词没搜到」—— 那是把**我们的失败**说成**站方的空结果**）。
     *
     * 第四枚 `List<String?>` 是**按源动态筛选**的值表（「筛选和分类每个源都不
     * 一样的，你这个定死了」）——由 `EngineSession.searchOptions()` 摊平、屏上选块改完回传；
     * 空表 = 走源默认（等价 `search()`）。
     */
    searchComics: (suspend (String, Int, String, List<String?>) -> DesktopComicSearchOutcome)? = null,
    /**
     * 漫画章节下载那一格的**根目录**（装配点给的是 `数据根/downloads`）。
     *
     * 为什么可空：这一路的写口只有 `DesktopComicDownloadQueue.enqueue(root, …)` 一颗，
     * 而它必须要绝对根。拿不到根 ⇒ 详情那一屏的「下载这一话」**整颗不摆**、
     * 那一圈泵也**不起** —— 与「没有写口就没有那枚钮」同一条口径（评审 R6 那一族）。
     *
     * 泵为什么挂在这颗壳上而不是详情那一屏：详情那一屏的 runner 从前挂在
     * `rememberCoroutineScope()` 上 ⇒ **离开那一屏就死**，跑到一半那一话既不完成也不留痕。
     * 挂在这一层（与窗口同寿命的那颗 `engine` 同一个作用域）才是"换屏不断"。
     */
    downloadsRoot: java.io.File? = null,
    /**
     * 阅读那一屏的模式偏好（从前算式与盘上档都在，屏上没有那排 chips）。
     *
     * `null` = 装配点没接这一路 ⇒ 模式 chips **整排不摆**（假开关那条），屏照纵向连读渲。
     */
    readerPrefs: com.venera.desktop.gallery.data.DesktopComicReaderPreferences? = null,
    /**
     * `--read-mode=` 取证种子（从前装配点判读了它却**没人消费** ——
     * 死变量那一族，正是本仓尺子反复咬的形状）。只顶本次，不写盘（批 87 那条口径）。
     */
    readerModeSeed: com.venera.desktop.gallery.data.DesktopReaderMode? = null,
    /**
     * 起窗直达种子：`--open=` 认出的**底栏那一格**初值。
     *
     * 默认 `HOME` 与从前一字未变；只有无人值守取证带 `--open=<底栏格>` 时装配点才递非默认值。
     * 这枚与 [initialRoute] 二选一（判读层 `OpenScreenReadout.Resolved` 保证恰一枚非空）。
     */
    initialTab: VeneraNavTab = VeneraNavTab.HOME,
    /**
     * 起窗直达种子：`--open=` 认出的**二级页**初值。
     *
     * 默认 `DesktopRoute.Tab`（停在底栏那一格 = 从前形状）；带 `--open=<二级页>` 时装配点递那一屏，
     * 起窗直接渲它。为什么不是"种子里塞 `comicId` 直进详情"：`Detail`/`Reader` 要带真数据形参，
     * 这条线今天还没有把 `--comic` 喂进 route 的链路（那正是发现的那条贯穿缺口，单列后续），
     * 所以这一枚只种**不需要外部数据就能渲出来**的那些屏，绝不为凑"直达详情"造一帧假数据。
     */
    initialRoute: DesktopRoute = DesktopRoute.Tab,
    /**
     * `--open=GALLERYVIEWER` 的附加种子：画廊首屏数据到货后自动打开第一张可点卡。
     * 只用于无人值守实窗取证，不改变正常起窗路径。
     */
    openFirstGalleryPost: Boolean = false,
    /**
     * Browser callback forwarded by the primary process. The flow carries an increasing id, so
     * pressing login twice with the same redirect shape still runs the exchange again.
     */
    pixivLoginCallback: StateFlow<DesktopPixivCallback?> = kotlinx.coroutines.flow.MutableStateFlow(null),
    /**
     * 那份「画廊浏览历史」档落在哪一层。
     *
     * 只递这一枚而不把 `DesktopGalleryBrowseHistory` 那颗件递进来：那颗的 `getInstance` 在**构造时同步读一次盘**
     * （批 86 那条「第一帧就是对的」的口径），而组合期不该碰盘（§134/§135 立下的拆法）⇒ 由壳在那一圈
     * `Dispatchers.IO` 上取一次。`null` = 这台机器还没定下数据根 ⇒ 那一屏念得出成因，
     * 而**门照摆**（与 §119「永远摆，不按盘上有没有历史行」同一条口径）。
     */
    galleryPaths: com.venera.compose.data.platform.PathProvider? = null,
    /**
     * 设置那一屏要的两枚**具体件**：八枚偏好的写侧（`setGalleryXxx`）与分级遮罩档的
     * 读流 + `setNsfwMaskMode` 只在这两枚上，共享接口只有读面。装配点各取一次（`DesktopGalleryPorts
     * .preferences/contentGuard`）递进来 —— 与 `wallBlockAiOf`、源管理屏那道 `setMaskMode` 同一条形状。
     * `null` 时那一支**整块不渲**（没有写口就没有那枚能改的屏，比摆一堆按不动的档诚实）。
     */
    galleryPrefs: com.venera.desktop.gallery.data.DesktopGalleryPreferences? = null,
    galleryGuard: com.venera.desktop.gallery.data.DesktopGalleryContentGuard? = null,
    /**
     * 设置中枢要的装饰档偏好。`null` = 「通用与外观」那一组不摆。
     * （阅读模式那枚件与阅读屏共用上面那枚 `readerPrefs`，不另加第二枚。）
     */
    decoPrefs: com.venera.desktop.gallery.data.DesktopDecoTierPreferences? = null,
    /**
     * 随包源目录与用户改写层（详情页共享屏复用 D′-3 要的两枚**纯路径读数**）。
     *
     * `ComicDetailCore` 经 `DesktopBusinessPorts.Handle` 问源清单（脚本名 / cookieFields 都住在
     * 这两层里），装配点手上有真值；`null` ⇒ 详情支路如实念"装配没接上"，不假装能取数。
     */
    assetDir: java.io.File? = null,
    overrideDir: java.io.File? = null,
    /** 追更账本；null = 装配点没接 ⇒ 详情页追更按钮与追更视图都不摆。 */
    followDao: com.venera.compose.data.db.ComicFollowDao? = null,
) {
    val state = rememberDesktopLargeScreenState(
        windowWidth = windowWidth,
        ports = ports,
        modifier = modifier,
        maskModeOf = maskModeOf,
        blockedByUserOf = blockedByUserOf,
        wallGuardOf = wallGuardOf,
        wallBlockAiOf = wallBlockAiOf,
        engine = engine,
        onWindowMinimize = onWindowMinimize,
        onWindowToggleMaximize = onWindowToggleMaximize,
        windowMaximized = windowMaximized,
        onShellBackdropChange = onShellBackdropChange,
        onWindowClose = onWindowClose,
        windowDragModifier = windowDragModifier,
        writeHistory = writeHistory,
        readHistoryRow = readHistoryRow,
        setMaskMode = setMaskMode,
        setBlockAi = setBlockAi,
        readSources = readSources,
        writeSourceEnabled = writeSourceEnabled,
        writeSourceOrder = writeSourceOrder,
        readSourceText = readSourceText,
        saveSourceText = saveSourceText,
        clearSourceOverride = clearSourceOverride,
        loginWithCookies = loginWithCookies,
        loginWithPassword = loginWithPassword,
        loginWithWebview = loginWithWebview,
        readFavorites = readFavorites,
        readFavoriteFolders = readFavoriteFolders,
        createFavoriteFolder = createFavoriteFolder,
        deleteFavoriteFolder = deleteFavoriteFolder,
        onOpenCardArtist = onOpenCardArtist,
        removeFavorite = removeFavorite,
        isFavoriteOf = isFavoriteOf,
        addFavorite = addFavorite,
        searchComics = searchComics,
        downloadsRoot = downloadsRoot,
        readerPrefs = readerPrefs,
        readerModeSeed = readerModeSeed,
        initialTab = initialTab,
        initialRoute = initialRoute,
        openFirstGalleryPost = openFirstGalleryPost,
        pixivLoginCallback = pixivLoginCallback,
        galleryPaths = galleryPaths,
        galleryPrefs = galleryPrefs,
        galleryGuard = galleryGuard,
        decoPrefs = decoPrefs,
        assetDir = assetDir,
        overrideDir = overrideDir,
    )
    var route by state.routeState
    var currentTab by state.currentTabState
    var filesSegment by state.filesSegmentState
    var flyHostTopLeft by state.flyHostTopLeftState
    var historyTick by state.historyTickState
    var sourceTick by state.sourceTickState

    DesktopLargeScreenAllEffects(
        state = state,
        ports = ports,
        maskModeOf = maskModeOf,
        wallGuardOf = wallGuardOf,
        wallBlockAiOf = wallBlockAiOf,
        engine = engine,
        writeHistory = writeHistory,
        readHistoryRow = readHistoryRow,
        readSources = readSources,
        readSourceText = readSourceText,
        loginWithCookies = loginWithCookies,
        loginWithPassword = loginWithPassword,
        loginWithWebview = loginWithWebview,
        readFavorites = readFavorites,
        readFavoriteFolders = readFavoriteFolders,
        isFavoriteOf = isFavoriteOf,
        searchComics = searchComics,
        downloadsRoot = downloadsRoot,
        openFirstGalleryPost = openFirstGalleryPost,
        galleryPaths = galleryPaths,
        galleryPrefs = galleryPrefs,
    )

    DesktopLargeScreenProviders(
        windowWidth = windowWidth,
        fullscreenChrome = state.fullscreenChrome,
        readerChromeController = state.readerChromePolicy.chromeController,
        ports = ports,
        galleryPaths = galleryPaths,
        galleryPrefs = galleryPrefs,
        desktopUx = state.desktopUx,
        wallLayout = state.wallLayout,
        wallDensity = state.wallDensityState.value,
        addFavorite = addFavorite,
        removeFavorite = removeFavorite,
        gallerySearchSourceOptions = state.gallerySearchSourceOptions,
        gallerySearchQueryState = state.gallerySearchQueryState,
        gallerySearchSourceState = state.gallerySearchSourceState,
        gallerySearchTickState = state.gallerySearchTickState,
        routeState = state.routeState,
    ) {
        val detailBackdropUrl = when (val cur = route) {
            is DesktopRoute.PixivWork -> cur.coverUrl
            is DesktopRoute.GalleryPostDetail -> cur.post.url
            else -> null
        }
        val shellBackdropUrl: String? =
            if (!state.fullscreenChrome && detailBackdropUrl != null) detailBackdropUrl else null
        DesktopLargeScreenChromeScaffold(
            route = route,
            onRouteChange = { route = it },
            currentTab = currentTab,
            onTabChange = { currentTab = it },
            onPetSwitchPage = { petSwitchPage() },
            onOpenSettings = state.openSettings,
            backActionProvider = state.desktopShellBackWithSearch,
            modifier = modifier.then(
                // 设置弹窗打开时对整个底层壳（侧栏 + 顶栏 + 主内容区）统一做全模糊，精简特效档除外。
                if (state.settingsOpen && !com.venera.desktop.ui.DesktopEffects.reduce) {
                    Modifier.blur(12.dp, edgeTreatment = BlurredEdgeTreatment.Rectangle)
                } else {
                    Modifier
                },
            ),
            shellMode = state.shellMode,
            fullscreenChrome = state.fullscreenChrome,
            discoverViewExplore = state.discoverViewExploreState.value,
            windowMaximized = windowMaximized,
            onWindowMinimize = onWindowMinimize,
            onWindowToggleMaximize = onWindowToggleMaximize,
            onWindowClose = onWindowClose,
            windowDragModifier = windowDragModifier,
            onShellBackdropChange = onShellBackdropChange,
            railExpandedSaved = state.readerChromePolicy.effectiveRailExpanded,
            onRailExpandedChange = state.readerChromePolicy.onRailExpandedChange,
            filesSegment = filesSegment,
            onOpenFilesSegment = { seg ->
                state.filesUi.filesDomain = com.venera.desktop.downloads.DesktopFilesDomain.COMIC
                filesSegment = seg
                route = DesktopRoute.Files
            },
            queueTasks = state.queueTasks,
            favoritesLoaded = state.favoritesLoadedState.value,
            favoritesCount = state.favoritesState.value.size,
            pixivAuthState = state.pixivAuthState,
            pixivAccountAvatarUrl = state.pixivAccountAvatarUrl,
            flyState = state.flyState,
            flyAnchor = state.flyAnchor,
            flyHostTopLeft = flyHostTopLeft,
            onFlyHostTopLeftChange = { flyHostTopLeft = it },
            flyPointer = state.flyPointer,
        ) {
            DesktopLargeScreenRouterWiring(
                state = state,
                ports = ports,
                maskModeOf = maskModeOf,
                wallGuardOf = wallGuardOf,
                wallBlockAiOf = wallBlockAiOf,
                engine = engine,
                writeHistory = writeHistory,
                writeSourceEnabled = writeSourceEnabled,
                writeSourceOrder = writeSourceOrder,
                readSourceText = readSourceText,
                clearSourceOverride = clearSourceOverride,
                loginWithCookies = loginWithCookies,
                loginWithPassword = loginWithPassword,
                loginWithWebview = loginWithWebview,
                createFavoriteFolder = createFavoriteFolder,
                deleteFavoriteFolder = deleteFavoriteFolder,
                removeFavorite = removeFavorite,
                addFavorite = addFavorite,
                searchComics = searchComics,
                downloadsRoot = downloadsRoot,
                readerPrefs = readerPrefs,
                galleryPaths = galleryPaths,
                galleryPrefs = galleryPrefs,
                galleryGuard = galleryGuard,
                assetDir = assetDir,
                overrideDir = overrideDir,
                shellBackdropUrl = shellBackdropUrl,
                followDao = followDao,
            )
        }
        DesktopLargeScreenOverlays(
            settingsOpen = state.settingsOpen,
            route = route,
            routeBeforeSettings = state.routeBeforeSettingsState.value,
            onRouteChange = { route = it },
            onHistoryTickIncrement = { historyTick++ },
            onSourceTickIncrement = { sourceTick++ },
            layoutMode = state.layoutMode,
            ports = ports,
            galleryPrefs = galleryPrefs,
            galleryGuard = galleryGuard,
            readerPrefs = readerPrefs,
            galleryPaths = galleryPaths,
            wallDensity = state.wallDensityState.value,
            onPickWallDensity = { next ->
                state.wallDensityState.value = next
                state.railPrefs?.put("pref_wall_density", next.ordinal)
            },
            sourceEditText = state.sourceUi.sourceEditText,
            saveSourceText = saveSourceText,
            scope = state.scope,
        )
    }
}
