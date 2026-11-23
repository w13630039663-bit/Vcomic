package com.venera.compose.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.venera.compose.components.LocalUxPlatformServices
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.components.venera.VeneraCoverMask
import com.venera.compose.components.venera.veneraAvatarRing
import com.venera.compose.components.venera.VeneraPressFeedback
import com.venera.compose.components.selection.SelectableCardFrame
import com.venera.compose.components.coverSharedElement
import com.venera.compose.data.platform.Logger
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryAi
import com.venera.compose.gallery.domain.GalleryArtistLinks
import com.venera.compose.gallery.domain.GalleryCardTitle
import com.venera.compose.gallery.domain.GalleryPreviewQuality
import com.venera.compose.gallery.domain.GallerySettingsModel
import com.venera.compose.gallery.domain.GalleryTitleTag
import com.venera.compose.gallery.domain.PixivImageQuality
import com.venera.compose.gallery.domain.cardBoxRatio
import com.venera.compose.gallery.domain.cardBoxRatioClamped
import com.venera.compose.ui.tokens.LocalBottomBarClearance
import com.venera.compose.ui.tokens.StatusColors
import com.venera.compose.ui.tokens.VeneraTokens
import kotlinx.coroutines.flow.distinctUntilChanged
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text

// 画廊墙共享本体：触感、飞行截帧/落点、长按动作表、画师跳转、画质与站标均通过参数或平台服务注入，不直连宿主。

/** 墙首的一节：key 定死（它是 grid 的 item key），内容随状态换。 */
internal data class GalleryGridSection(val key: String, val content: @Composable () -> Unit)

/**
 * 「这一面墙上有一段内容坐在一张卡面上」。
 *
 * 用户拍板原话：「推荐区包卡，但是还是目前这样的瀑布流，然后卡面会有图片的很低的模糊」。
 *
 * ## 为什么卡面只能画在网格**外面**
 *
 * 推荐那一节是「节头 + 脚下整面瀑布流」，而 `LazyVerticalStaggeredGrid` 里**没有"行"**
 * —— 卡片逐列往下排、高度各不相同。所以"给这一段包一张卡"不能逐 item 画：
 * 相邻两列的卡片结束位置不同，逐 item 画出来的卡面会在先结束那一列旁边露出**阶梯缺口**。
 * 唯一成立的位置是网格**下面**的一整层（[SectionSurfaceLayer]）。
 *
 * ## 与「节背板」那条被否的方案不是一回事
 *
 * `GalleryHomeSection` 的头注记着：M7 给每节套一块 `VeneraCard` 背板被撤了，因为
 * "节卡 + 条目卡 = 卡中卡，两层颜色只差一档，实测条目卡面比页面底只亮 +7，糊成一层"。
 * 这里能成立是因为**卡面上有图**：一层 `blur` 过的图给出纹理，卡面与卡片的分界不再只靠那 +7 的色差。
 * 纯色档（`imageUrl = null`）只用于"这一池没好图"的兜底，那种处境下它确实会偏弱。
 *
 * @param sectionKey 从**这一节的节头顶**开始铺卡面（含节头本身），一路延伸到视口底。
 * @param inset 卡面左右内缩（递页面的水平内边距）—— 卡面不贴屏幕边。
 * @param imageUrl 卡面上的模糊图。⚠️ null 不是"忘了传"，见 [SectionSurfaceLayer] 那条。
 */
internal data class GallerySectionSurface(
    val sectionKey: String,
    val inset: Dp,
)

/**
 * [GallerySectionSurface] 的那层卡面 —— 画在网格**下面**。
 *
 * ## 定位（全部在 **draw 阶段**读，零重组零重布局）
 *
 * 卡面顶端 = 节头 item 的 `offset.y`：
 * - 节头还在可见集合里 ⇒ 直接取它的 y（跟随滚动）；
 * - 节头已滚到视口上方（可见首项的 index 比它大）⇒ 顶端取 0，卡面铺满整个视口；
 * - 节头还没进入视口 ⇒ 这一屏不该有卡面，什么都不画。
 *
 * 底端恒为视口底：推荐段后面只有页尾，卡面"延伸出屏"正是这类设计的常态。
 * 圆角只有上两角 —— 下沿在屏外，画了也看不见。
 *
 * ## 卡面是**纯色**，不再挂模糊底图（推翻本日的做法）
 *
 * 那一轮给这层铺过「首图模糊 32dp + 压 86% 卡面色」，理由是"纯色卡中卡只差 +7 色差会糊，
 * 用模糊图给纹理拉开分界"。真机反馈是**反的**：用户报「整个推荐面会有这种模糊效果」——
 * 86% 的不透明挡不住底图的颜色透出来，那一大片就读成"这一区雾蒙蒙的"，
 * 而上面两节（画师 / 排行榜）是干净的实色白卡，三节放一起反而是推荐这一节脏。
 *
 * 所以撤掉底图、只铺 `surface` 实色：三节用**同一种**包裹，卡面只负责"这段属于这一节"。
 * ⚠️ 这条同时把 M7 那个顾虑又请回来了（卡中卡只差一档色差）——
 * 但那是取舍，不是缺陷：用户要的是一致，不是一个节另有一套材质。
 */
@Composable
private fun SectionSurfaceLayer(
    spec: GallerySectionSurface,
    state: LazyStaggeredGridState,
    sectionIndex: Int,
) {
    val tokens = VeneraTokens
    val density = LocalDensity.current
    val insetPx = with(density) { spec.inset.toPx() }
    val cornerPx = with(density) { tokens.shape.large.toPx() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawWithContent {
                val top = sectionSurfaceTopPx(state, sectionIndex) ?: return@drawWithContent
                // 节头在屏下方（top ≥ 视口高）时矩形是退化/反向的，`RoundRect` 会炸；
                // 极窄窗口下左右内缩吃光整宽也一样 ⇒ 两种都直接不画。
                if (top >= size.height || size.width <= insetPx * 2f) return@drawWithContent
                val surfacePath = Path().apply {
                    addRoundRect(
                        RoundRect(
                            rect = Rect(insetPx, top, size.width - insetPx, size.height),
                            topLeft = CornerRadius(cornerPx),
                            topRight = CornerRadius(cornerPx),
                            bottomRight = CornerRadius.Zero,
                            bottomLeft = CornerRadius.Zero,
                        ),
                    )
                }
                clipPath(surfacePath) { this@drawWithContent.drawContent() }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(tokens.color.surface),
        )
    }
}

/**
 * 卡面顶端在**视口坐标**里的 y；null = 这一屏不该画卡面。
 *
 * ⚠️ 只许在 draw 阶段调用（读 `state.layoutInfo`）—— 在组合期读它就是每帧重组。
 */
private fun sectionSurfaceTopPx(
    state: LazyStaggeredGridState,
    sectionIndex: Int,
): Float? {
    val items = state.layoutInfo.visibleItemsInfo
    items.firstOrNull { it.index == sectionIndex }?.let { return it.offset.y.toFloat() }
    // 节头不在可见集合里只剩两种处境：已滚到视口上方（卡面铺满），或还没滚进来（不画）。
    val firstVisible = items.firstOrNull()?.index ?: return null
    return if (firstVisible > sectionIndex) 0f else null
}


/**
 * 那面错落屏的墙 —— 主墙与搜索结果共用（用户拍板搜索层后抽出来的，
 * 目的不是省事：key 带站点、比例夹 0.4~2.5、点卡片要截"滑入那一帧"这三条口径
 * 一旦分叉，搜索页就会出现"点这张飞过去那张"或"进场没有占位"）。
 */
@Composable
internal fun GalleryCardsGrid(
    cards: List<GalleryCard>,
    /**
     * 取图器。null = 各平台自己的应用级单例（阶段三 T3：桌面没有安卓那颗"画廊独立缓存预算"的
     * `GalleryImageLoader`，它的墙吃 `DesktopImagePipeline` 装的 Coil 单例 —— 显式传与不传
     * 在安卓侧语义不变，桌面侧从此不必为喂墙另造第二份缓存）。
     */
    imageLoader: ImageLoader?,
    columnCount: Int,
    state: LazyStaggeredGridState,
    contentPadding: PaddingValues,
    horizontalSpacing: Dp = 10.dp,
    verticalSpacing: Dp = 12.dp,
    onOpen: (GalleryPost) -> Unit,
    modifier: Modifier = Modifier,
    /** 长按一张卡。收藏页用它拉出「从收藏移除」，主墙/搜索不传（长按在那儿没有任何事可做）。 */
    onLongPress: ((GalleryPost) -> Unit)? = null,
    /**
     * 非空 = 这面墙**处于多选态**（眼下只有收藏页会传）。此时：
     * 卡片右上角摆那枚统一选择框、点卡片改成勾选，`onLongPress` 的语义由调用方切成"区间反选"。
     *
     * null = 非多选态，卡片行为与从前**逐字一致**（点开大图页）—— 主墙与搜索墙不传，
     * 它们的交互因此零改动。判据用"传没传这个函数"而不是再加一个布尔开关：
     * 两个参数表达同一件事就有对不上的可能，一个参数不会。
     */
    selection: ((GalleryPost) -> Boolean)? = null,
    /** 点选择框，以及多选态下点整张卡。 */
    onToggleSelect: ((GalleryPost) -> Unit)? = null,
    /** 卡片上的补充内容。null 时与从前一致。 */
    overlay: (@Composable BoxScope.(GalleryPost) -> Unit)? = null,
    /**
     * 墙首那一行（日榜的"只有一站给了内容"说明条）。
     *
     * ⚠️ 传进来的槽位**存在与否不能跟着内容变**：懒列表在第 0 项之后插一个新项，
     * 视口锚点会漂到第 1 项，于是内容画进 `contentPadding` 里（首页顶栏重叠那次真机缺陷的根因，
     * 见记忆「首页顶栏重叠的真根因：锚定漂移」）。所以判空要**移到槽位内部**：
     * 这一行恒在，里面有没有话由数据决定。
     */
    header: (@Composable () -> Unit)? = null,
    /**
     * 首页那几节（画师 / 每日热门预览 / 猜你喜欢节头）。
     * 每一项都是墙里一个**恒定存在**的 FullLine 项，"这一节有没有货"由项内部判 ——
     * 节的数量不许跟着数据变（锚定漂移那条，见 [GalleryGridSection]）。
     */
    sections: List<GalleryGridSection> = emptyList(),
    footer: (@Composable () -> Unit)? = null,
    /**
     * 这一面墙上的卡**是否参与封面共享元素**，以及用哪个 key。
     *
     * 默认 null = 不挂。只有「每日热门」二级页那一处传（它的对面是首页预览行那张同 uid 的横卡）——
     * 首页主墙 / 搜索墙 / 收藏墙的对面是**跨 Activity** 的大图页，那边没有同 key 的落地槽位，
     * 挂了就是白挂：飞行体找不到去处，而两端各自淡入淡出，看起来像"动画没做完"。
     * 为什么不做成布尔开关而要一个函数：key 的身份是 `uid`（带站键），这层判断留在调用方更清楚。
     */
    sharedElementKey: ((GalleryPost) -> String)? = null,
    /**
     * 是否禁掉这面墙的**过度滚动**。
     *
     * 有头图的那面墙（首页主墙）传 true：到顶继续下拉，`stretch` 会整张带头图顶下去、
     * 上方露出一大片页面背景 —— 观感很糟。没有头图的墙保持默认，回弹是正常的触感反馈。
     *
     * ⚠️ 走 `overscrollEffect = null` 这条**组件参数**，不是包一层 CompositionLocal：
     * 这一面墙就是那颗滚动容器本身，参数是最短的路径，也不必让"禁没禁"这件事
     * 依赖读者去别处找一个 provider。
     */
    disableOverscroll: Boolean = false,
    /**
     * 站方自己给的标题（`uid → 标题`），**盖过**下面那批由标签算出来的标题。
     *
     * 出处是用户那句「推荐下面的图标题没了，直接用 p 站自己的标题就行」：
     * pixiv 每行都带 `title`，而 booru 三站没有 —— 后者的标题是 `GalleryCardTitle`
     * 从标签里**算**的，前者被算成空（那一池的标签是日文，词典里没有）。
     *
     * ⚠️ 只在"这一格真的有字"时覆盖：`PixivWork.title` 可能为空串（站方给了但空），
     * 那时那一行整行不画（同 [GalleryCardCaption] 的 `title == null` 那档），
     * **不拿标签算出来的那批顶上去** —— 那会让"站方标题"与"我们猜的标题"在屏上混成同一层。
     */
    stationTitles: Map<String, String> = emptyMap(),
    /**
     * 作者头像（`uid → 地址`），与 [stationTitles] 同一条透传路径 —— 同理也是
     * "整批算好再摊平"，而不是卡内各查一次（那要开网络/查库）。
     * 空 map = 这一面墙不画头像（其余五处调用点逐像素不变）；非空 ⇒ 卡片文字区走
     * 「头像 + 标题/画师名」两行（见 [GalleryCardCaption]）。
     */
    authorAvatars: Map<String, String> = emptyMap(),
    /**
     * 非空 = 这一面墙上**有一段内容坐在一张卡面上**（[GallerySectionSurface]，眼下只有推荐那一节）。
     *
     * null = 与从前逐字一致（其余四处调用点都不传）。**不做成布尔开关**：
     * "哪一段"与"卡面长什么样"是同一件事的两半，拆成两个参数就会对不上。
     */
    sectionSurface: GallerySectionSurface? = null,
    /**
     * 预览清晰度两档（画廊 booru / pixiv）：**调用点整屏读一次**递进来。
     * 从前住在卡里按卡各读一次 `GalleryPorts.of(LocalContext)`（注释见 `GalleryPostCard` 原头注），
     * "整面墙唯一消费点、跟着设置实时变"的语义由调用点的 `collectAsState` 原样保持 ——
     * 搬的只是 handle：那颗不能再长在墙里（桌面没有 `LocalContext`）。
     */
    quality: GalleryPreviewQuality,
    pixivQuality: PixivImageQuality,
    /** 「AI 角标」开关（同上一条透传理由：从前按卡各读 `LocalContext`，现在整屏读一次）。 */
    aiBadge: Boolean,
    /**
     * 词典标题腿的端口与日志（同上一条：`rememberGalleryCardTitles` 从前直连
     * `GalleryPorts.of(LocalContext)` + `android.util.Log`，现在吃注入）。
     */
    ports: GalleryPorts,
    logger: Logger,
    /**
     * 长按一张卡 → 那张动作表的**宿主实现**工厂（安卓交 `GalleryCardActionSheet`：下载 / 复制 /
     * 分享 / 收藏 / 画师页五项，绑剪贴板·Toast·Intent —— 那颗表还要"进大图页"的当次回调，
     * 所以槽位是 `(onOpenDetail) -> 表composable` 这一形，墙把这一张的打开腿交回宿主）。
     * null = 长按不做事（桌面今天那一档，桌面对应动作表接上时换这一枚，墙零改动）。
     * 墙自己仍管"哪张被长按"的状态。
     */
    actionSheet: ((onOpenDetail: (GalleryPost) -> Unit) -> @Composable (post: GalleryPost, onDismiss: () -> Unit) -> Unit)? = null,
    /**
     * 点卡时**趁卡还在屏上截那一帧**（安卓装配交 `GalleryFlyIn.capture` 那一跳）；
     * null = 不截不飞（桌面）。参数是封面在窗口坐标系里的矩形 —— 起点与大图页画面框同构那条
     * 口径的注释随槽位留在调用点。
     */
    flyCapture: ((coverBounds: Rect) -> Unit)? = null,
    /** 登记这一张的落点（安卓 `GalleryFlyIn.noteCardBounds`）；null = 不登记。 */
    noteFlyBounds: ((viewerKey: String, full: Rect, visible: Rect) -> Unit)? = null,
    /** 卡片离场时撤落点（安卓 `GalleryFlyIn.forgetCardBounds`）；null = 不挂这一跳 effect。 */
    forgetFlyBounds: ((viewerKey: String) -> Unit)? = null,
    /**
     * 点署名头像 = 进画师页（**宿主导航**：安卓接 `openPixivArtistPage`，桌面接路由）。
     * 墙只负责"这一张是不是 pixiv、有没有用户号"的判据（`GalleryArtistLinks`），
     * 落地那一跳交调用方。null = 头像不可点。
     */
    onOpenArtistPage: ((userId: Long, name: String) -> Unit)? = null,
    /** 框被夹取时的取证读数（安卓接 `Log.d(CARD_RATIO_TAG,…)`）；null = 不念。 */
    onRatioClamped: ((post: GalleryPost, ratio: Float) -> Unit)? = null,
    /** 站标槽位（透传给 `[GalleryCardCaption]`；桌面 null = 那一行只有标题）。 */
    sourceMark: (@Composable (site: GallerySite) -> Unit)? = null,
    /**
     * 这一张点不点得开（阶段三 T3，形状照 `HistoryScreenBody.rowGate` 先例）：缺省恒开 ⇒
     * 安卓逐字不变。桌面交 `masked` / 无身份那一档 false —— A 条红线"打码卡不放大成人图"
     * 是**平台侧裁决**，不许长在共享墙里，但点击腿必须由它把关：false ⇒ 那一格**没有那一下
     * 动作**（不是按了没反应的假按钮，与 `DesktopWallCard.openable` 今天同一口径）。
     */
    openableOf: ((GalleryPost) -> Boolean)? = null,
    /** 自定义信息栏；null = 走共享 `GalleryCardCaption`。 */
    cardInfo: (@Composable (post: GalleryPost, title: String?) -> Unit)? = null,
    /** true = 图片按站方原始比例铺满，不夹取极端长条。 */
    preserveNaturalImageHeight: Boolean = false,
) {
    val tokens = VeneraTokens
    val ux = LocalUxPlatformServices.current
    // 卡面那一节的 item index：`header` 占一格，之后按 [sections] 的顺序排。
    // 找不到那个 key（调用方传了本节没有的 key）⇒ null ⇒ 不画 —— 静默退化成"没包卡"，
    // 而不是把卡面画到错的节上。
    val surfaceSectionIndex = remember(sectionSurface, sections, header != null) {
        sectionSurface?.let { spec ->
            val order = sections.indexOfFirst { it.key == spec.sectionKey }
            if (order < 0) null else order + (if (header != null) 1 else 0)
        }
    }
    // 平台默认的过度滚动效果。⚠️ 必须**无条件**先 remember 出来再按开关取舍：
    // 把 `rememberOverscrollEffect()` 写进 `if` 的分支里就是条件调用 Composable，会崩。
    val platformOverscrollEffect = rememberOverscrollEffect()
    // 长按动作表状态；调用方传入 onLongPress 时优先使用（如多选区间反选），不额外增加布尔开关。
    var actionCard by remember { mutableStateOf<GalleryCard?>(null) }
    // 整批查询离线词典标题，再由 stationTitles 覆盖；空串覆盖视为站方已给空标题，折成 null 整行不画。
    val dictTitles = rememberGalleryCardTitles(cards.map { it.post }, ports, logger)
    val titles = remember(dictTitles, stationTitles) {
        if (stationTitles.isEmpty()) {
            dictTitles
        } else {
            // 覆盖值是空串也算"站方给了"：那颗 uid 从 dict 那批里摘掉、又不进结果
            // ⇒ 卡上取到 null ⇒ 那一行整行不画（同 `GalleryCardCaption` 的 null 档）。
            val merged = HashMap<String, String>(dictTitles.size + stationTitles.size)
            dictTitles.forEach { (uid, title) -> if (uid !in stationTitles) merged[uid] = title }
            stationTitles.forEach { (uid, title) -> if (title.isNotBlank()) merged[uid] = title }
            merged
        }
    }
    // 滚动越界预取（「预览能不能更快」那轮）：见 [rememberGalleryPreviewPrefetch]。
    // 没递取图器的那一侧（桌面吃应用级单例）不预取 —— 预取要的是"画廊那颗独立缓存"，
    // 对单例暖缓存是另一笔账（桌面有自己的单例口径），不在这里替它决定。
    if (imageLoader != null) {
        rememberGalleryPreviewPrefetch(
            cards = cards,
            imageLoader = imageLoader,
            platformContext = ux.platformContext(),
            state = state,
            leadingItemCount = (if (header != null) 1 else 0) + sections.size,
        )
    }
    // 卡面层置于网格下方避免遮挡卡片；modifier 挂在外层 Box，网格 fillMaxSize 铺满。
    Box(modifier = modifier.fillMaxSize()) {
        if (sectionSurface != null && surfaceSectionIndex != null) {
            SectionSurfaceLayer(
                spec = sectionSurface,
                state = state,
                sectionIndex = surfaceSectionIndex,
            )
        }
        LazyVerticalStaggeredGrid(
            state = state,
            columns = StaggeredGridCells.Fixed(columnCount),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
            verticalItemSpacing = verticalSpacing,
            // 头图那面墙禁掉过度滚动（见 [disableOverscroll]）；其余墙保持平台默认。
            overscrollEffect = if (disableOverscroll) null else platformOverscrollEffect,
            modifier = Modifier.fillMaxSize(),
        ) {
            header?.let { slot ->
                item(key = "grid-header", span = StaggeredGridItemSpan.FullLine) { slot() }
            }
            // 节头（首页三节）。**恒定项**（数量不许跟着数据变，理由见 [GalleryGridSection]）。
            sections.forEach { section ->
                item(key = section.key, span = StaggeredGridItemSpan.FullLine) { section.content() }
            }
            // key 必须带站点：两站 id 各自编号，同 id 是两张不同的图，撞 key 会直接崩。
            items(cards, key = { it.post.uid }) { card ->
                GalleryPostCard(
                    card = card,
                    imageLoader = imageLoader,
                    titles = titles,
                    authorAvatar = authorAvatars[card.post.uid],
                    onOpen = { bounds ->
                        ux.hapticTap()
                        // 整页向上滑入时垫在最上面的那一帧：趁卡片还在屏上截下来（宿主槽位 `flyCapture`）。
                        flyCapture?.invoke(bounds)
                        onOpen(card.post)
                    },                    onLongPress = onLongPress?.let { handler ->
                        {
                            ux.hapticLongPress()
                            handler(card.post)
                        }
                    } ?: (
                        if (actionSheet != null) {
                            {
                                ux.hapticLongPress()
                                actionCard = card
                            }
                        } else {
                            // 宿主没有动作表那一档（桌面今天）：长按**什么都不做才是真话** ——
                            // 挂着"把卡置成选中"的默认动作而没有任何后果，就是本仓那一族假开关。
                            null
                        }
                    ),
                    selected = selection?.invoke(card.post) == true,
                    selecting = selection != null,
                    onToggleSelect = onToggleSelect?.let { handler -> { handler(card.post) } },
                    overlay = overlay,
                    sharedElementKey = sharedElementKey?.invoke(card.post),
                    // 仅 Pixiv 卡片支持点击头像进画师页（booru 画师为标签名），跳转由宿主 onOpenArtistPage 执行。
                    onOpenArtist = if (card.post.site == GallerySite.PIXIV && onOpenArtistPage != null) {
                        val id = GalleryArtistLinks.pixivUserId(card.post.source)
                        if (id != null) {
                            { onOpenArtistPage(id, card.post.author) }
                        } else {
                            null
                        }
                    } else {
                        null
                    },
                    quality = quality,
                    pixivQuality = pixivQuality,
                    aiBadge = aiBadge,
                    flyCapture = flyCapture,
                    noteFlyBounds = noteFlyBounds,
                    forgetFlyBounds = forgetFlyBounds,
                    onRatioClamped = onRatioClamped,
                    sourceMark = sourceMark,
                    openable = openableOf?.invoke(card.post) ?: true,
                    cardInfo = cardInfo,
                    preserveNaturalImageHeight = preserveNaturalImageHeight,
                )
            }
            footer?.let { slot ->
                item(key = "grid-footer", span = StaggeredGridItemSpan.FullLine) { slot() }
            }
        }
    }

    actionCard?.let { card ->
        actionSheet?.invoke(onOpen)?.invoke(card.post) { actionCard = null }
    }
}


/** 预取窗口：视野之外再往前暖 [GALLERY_PREFETCH_AHEAD] 张缩略图。 */
private const val GALLERY_PREFETCH_AHEAD = 24

/**
 * 画廊墙的**滚动越界预取**（「预览能不能更快」那轮加的）。
 *
 * 病：懒网格只对"进入视野的项"发请求 —— 卡片组合的那一刻 `AsyncImage` 才开跑，
 * 用户以正常速度上滑时，图开始下载的那一刻他已经看到空卡 + shimmer 了。
 * 两站缩略图都很小（本机实测 yande.re ~13 KB、Gelbooru ~25 KB），下载本身只要一两百毫秒，
 * **"看到才开始下"这一拍浪费的才是大头**。
 *
 * 做法：盯着网格最后一个可见项（`snapshotFlow` + `distinctUntilChanged`，滚动停下才判一次），
 * 从可见末尾往前看 [GALLERY_PREFETCH_AHEAD] 张，用 `imageLoader.enqueue` 把没请求过的
 * 缩略图**异步**暖进缓存。与网格正式请求是同一条命中链路：
 * - `enqueue` 跑完整引擎管线，成功后位图进**内存缓存**；就算之后被挤掉，原始字节也已落
 * **磁盘缓存** —— 两站缩略 CDN 都回 `max-age=315360000`（本机 HEAD 实测），
 *   Coil 的 DiskCache 只由 NetworkFetcher 写，预取恰恰走它，回看零请求；
 * - 内存缓存 key 在无 transformations 时不带尺寸、compose 侧精度恒 INEXACT
 *   （coil 3.6.2 `AsyncImagePainter.updateRequest` 字节码实测），预取解好的位图网格直接命中
 *   —— **不重解码、不重复请求**；同 key 的并发请求由引擎自己合并，不会双发。
 *
 * 为什么不挂 target：挂了会参与视图生命周期与可见性裁剪，而"滚太快"时恰恰是
 * 卡片离场、请求被取消的窗口 —— 预取要的就是无主请求，落地进缓存即可。
 *
 * 收口（防三件事）：
 * 1. **判重按 uid 存 `HashSet`（普通字段）而不是靠重组**：请求过就跳过，预取是网络层的事，
 *    重发一笔比多重组贵得多；集合跟着 `cards` 的实例换（新一轮墙）一起重置；
 * 2. **只取 preview 档**（[GalleryPost.previewUrl]）：用户开了「预览清晰度 = 样例」时卡片自己
 *    会走 LARGE，那档一张几百 KB~几 MB，自动预取它就是拿用户的流量下赌注 ——
 *    大档永远只在用户真正看见时才取；
 * 3. **协程挂在 `LaunchedEffect(cards)`**：组合销毁 / 换一批换墙即取消，在途预取随之断流。
 *
 * @param state 调用方那把网格滚动状态（自建一把读不到滚动位置 —— 第 0 版在这上面栽过）。
 * @param leadingItemCount 卡片之前恒定存在的整行项数（header + 节头）：
 *   懒列表的 item index 是"整行项 + 卡片"混排的，不减掉它就会把节头当卡片、
 *   整个预取窗口向后错位同样的格数。
 */
@Composable
private fun rememberGalleryPreviewPrefetch(
    cards: List<GalleryCard>,
    imageLoader: ImageLoader,
    platformContext: coil3.PlatformContext?,
    state: LazyStaggeredGridState,
    leadingItemCount: Int,
) {
    // 预取过的 uid 集合。普通字段（非状态）：它只服务判重，变了不需要触发重组。
    val requested = remember { HashSet<String>() }
    // 卡片位次 → 预览地址。key 用 uid（两站 id 各自编号，同 id 是两张不同的图）。
    val urlsByUid = remember(cards) {
        HashMap<String, String>(cards.size).apply {
            cards.forEach { card -> put(card.post.uid, card.post.previewUrl) }
        }
    }
    LaunchedEffect(cards, leadingItemCount) {
        requested.clear()
        snapshotFlow {
            // 读进 derivedStateOf 语义的块里：只有"最后一个可见项"变了才重新判，
            // 滚动中 layoutInfo 每帧都在变，不加这层就是每帧白算一次窗口。
            state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        }
            .distinctUntilChanged()
            .collect { lastVisibleIndex ->
                if (lastVisibleIndex < 0) return@collect
                val lastCardIndex = lastVisibleIndex - leadingItemCount
                if (lastCardIndex < 0) return@collect
                val from = (lastCardIndex + 1).coerceAtMost(cards.size)
                val to = (lastCardIndex + 1 + GALLERY_PREFETCH_AHEAD).coerceAtMost(cards.size)
                for (i in from until to) {
                    val post = cards[i].post
                    if (post.uid in requested) continue
                    val url = post.previewUrl
                    if (url.isBlank()) continue
                    requested += post.uid
                    // 请求上下文由装配层报"本平台那颗"（`LocalUxPlatformServices.platformContext`）——
                    // 从前这里直连 `LocalContext`，是墙里长出的第二枚 Android handle；null = 这一腿不发
                    //（宁可少暖一张缓存，不在没有上下文的平台上编一个假请求）。
                    val requestContext = platformContext ?: continue
                    imageLoader.enqueue(
                        ImageRequest.Builder(requestContext)
                            .data(url)
                            // 不挂 target：无主请求，落地进缓存即完成（理由见上）。
                            // 内存缓存写回由引擎默认档（ENABLED）承担。
                            .build(),
                    )
                }
            }
    }
}


/**
 * 卡片比例取证那一条日志的标签（用法与为什么只加读数见 `[GalleryPostCard]` 内那一处）。
 * 取走：`adb logcat -d -s GalleryCardRatio`。**安卓装配调 `onRatioClamped` 槽位时用它**，
 * 所以这颗常量留在共享面（同包安卓件也在读，指针不漂）。
 */
internal const val CARD_RATIO_TAG = "GalleryCardRatio"

/**
 * 这一批条目的**卡片标题**（按 uid 索引）—— 原样移自 `GalleryCardCaption.kt`，
 * 只换两枚长在手里的 Android handle 为注入（阶段三 T1）：
 * `GalleryPorts.of(LocalContext)` → 形参 `ports`；`android.util.Log.w` → 形参 `logger.warn`。
 * "整批摊平去重查一次""null 与空表要分开响"两条判据逐字未动（原注全文随件迁移）。
 */
@Composable
internal fun rememberGalleryCardTitles(
    posts: List<GalleryPost>,
    ports: GalleryPorts,
    logger: Logger,
): Map<String, String> {
    // 摊平这批图的所有标签去重：它是查询键，也是 remember 的键 ——
    // 键错了就会拿着上一批的名字画这一批的标题（"卡上写着 A 串、点进去是 B"那类返祖）。
    val names = remember(posts) { posts.flatMap { it.tagList }.map { it.lowercase() }.distinct() }
    var labels by remember(names) { mutableStateOf<Map<String, GalleryTitleTag>>(emptyMap()) }
    LaunchedEffect(names) {
        if (names.isEmpty()) {
            labels = emptyMap()
            return@LaunchedEffect
        }
        val found = ports.lexicon.titleTags(names)
        if (found == null) {
            // 词典打不开：这一批卡都不会有标题行。这条必须响 —— 静默的话它和
            // "这些图没有作品标签"完全同形（理由见 `GalleryTagDictionary.openDatabase` 那条口径）。
            logger.warn(CARD_TITLE_TAG, "画廊标签词典打不开，卡片标题这一批全部不可用（${names.size} 个名字）")
            labels = emptyMap()
        } else {
            labels = found
        }
    }
    // 只有"这一批图 + 这一份词典"都定了才重算，避免每次重组重拼字符串。
    return remember(posts, labels) {
        if (labels.isEmpty()) {
            emptyMap()
        } else {
            posts.mapNotNull { post ->
                GalleryCardTitle.titleOf(post.tagList, labels)?.let { post.uid to it }
            }.toMap()
        }
    }
}

private const val CARD_TITLE_TAG = "GalleryCardTitle"

/**
 * 卡片封面下方那一块：**来源标识 + 标题**（对齐参考图的形态）。
 *
 * 来源摆**这一行**（不再压在封面角上）：参考图里那枚站标与标题同处封面下方的一行，
 * 而压角方案在两站混排时要用户从图的四个角里找，反而更难读。
 *
 * 标题为 `null` 时**整行不画**（不是画一行空文字）：回退链四档（作品·角色 → 单档 → 画师名 →
 * 通用标签译名，见 `GalleryCardTitle`）都没命中的图就不该有那一行，
 * 摆一行空的会在横滑行里把封面高度参差拉开。
 *
 * ## 两档形状，按「传没传头像地址」分
 *
 * - **`authorAvatar != null`**：头像 + 两行（标题 / 画师名）—— pixiv 推荐墙与云端书签墙
 * 走这一档。
 * - **否则**：原来那一行（站标 + 标题），其余五处调用点逐像素不变。
 *
 * ⚠️ 判据是**传没传头像地址**、不是「有没有画师名」：画师名五面墙都非空，拿它判就会把
 * 主墙 / 搜索 / 收藏那几卡的形状一起改掉（那几处不传头像表，`authorAvatars[uid]` 是 null）。
 *
 * ## 浮层那一档已退役
 *
 * 起，pixiv 推荐墙的文字区曾**浮在封面下沿**（半透明毛玻璃压在图上）。
 * 用户原话：「我之前说的做这个浮层，改成图片能在外面预览全部显示那种不要浮层，
 * 这样飞行动画也会好很多」—— 浮层把封面底沿压掉一条，图在网格里看不全；且飞行起点
 * （PixelCopy 截的封面那一块）带着那条 UI，要额外做「两段画 + 随进度淡出」才接得上画面框。
 * 现在文字区回到封面**下方**：图全部显示，起点与落点同构（都是纯图），飞行动画不再需要
 * 那道减退机理（`GalleryFlyIn.noteCaptionHeight` 那一套随之退役）。
 *
 * @param title 已经算好的标题（见 [rememberGalleryCardTitles]）；null = 这一张没有可显示的标题。
 * @param authorAvatar 作者头像地址。非 null = 走「头像 + 两行」那一档（见上）；
 *   null = 不画头像（其余调用点逐像素不变）。
 * @param authorName 作者名。`null`/空串 = 不画第二行。
 * @param onOpenArtist 点**署名行那颗头像**（「改成点击画师头像进入画师页」）。
 *   null = 头像不可点（图库三站的墙不传这张表，天然不受影响）。
 *
 * ⚠️ 只有**头像那颗圆**吃掉点击，整行不吃：这一行的右边紧跟着卡片的点击区，
 * 把整行做成可点就会与"点卡进大图"抢手势 —— 而"点脸去看这个人"是用户点名的那一下。
 */
@Composable
internal fun GalleryCardCaption(
    site: com.venera.compose.gallery.data.GallerySite,
    title: String?,
    authorAvatar: String? = null,
    authorName: String? = null,
    imageLoader: coil3.ImageLoader? = null,
    onOpenArtist: (() -> Unit)? = null,
    /**
     * 「站标 + 标题」那一档的站标槽位（阶段三 T1：`R.drawable` 那四枚真图标是 Android 资源面，
     * 墙不许钉死在它上面）。**null = 不画站标位** —— 桌面 `DesktopWallCard` 今天就不画角标，
     * 这一档与它同轨；安卓调用点交 `VeneraGallerySourceMark` 那颗（逐字同一形状）。
     */
    sourceMark: (@Composable (site: GallerySite) -> Unit)? = null,
    /** 站标那一格摆不摆（画师行那族 `GalleryArtistRows` 的默认档 = 摆，零改动）。 */
    showSourceMark: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    // ── 「头像 + 两行」那一档（递了头像表的墙：pixiv 推荐墙 / 云端书签墙）──
    if (authorAvatar != null) {
        Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
        ) {
            // 头像固定 24dp（space10）：再大（32dp）会超过标题与作者名两行总高并挤压文字区。
            Box(
                modifier = Modifier
                    .size(tokens.spacing.space10)
                    .veneraAvatarRing(RoundedCornerShape(tokens.shape.small))
                    .background(tokens.color.surfaceContainerHigh)
                    // 点脸 = 进这一位的画师页；不传回调就不挂点击（不给假把手）。
                    .then(
                        if (onOpenArtist != null) {
                            Modifier.clickable(onClick = onOpenArtist)
                        } else {
                            Modifier
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (authorAvatar.isNotBlank() && imageLoader != null) {
                    AsyncImage(
                        model = authorAvatar,
                        contentDescription = authorName,
                        imageLoader = imageLoader,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    // 墙级传入头像表但该项为空串时退回首字母座，保持同墙卡片文字区高度一致。
                    Text(
                        text = authorName.orEmpty().take(1),
                        fontSize = tokens.type.caption,
                        color = tokens.color.textSecondary,
                        maxLines = 1,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                if (title != null) {
                    Text(
                        text = title,
                        fontSize = tokens.type.caption,
                        // 行高写死（`TEXT_LINE_HEIGHT` 那一档，1.2f 照抄 miuix paragraph）：
                        // 不指定时行高随字形走（拉丁/CJK/emoji 差 1~2px），网格里就是卡高参差。
                        lineHeight = TEXT_LINE_HEIGHT.em,
                        color = tokens.color.textPrimary,
                        maxLines = 1,
                        // 一张画可能挂着好几个作品/角色档的标签，标题会很长 —— 省略号收口。
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!authorName.isNullOrBlank()) {
                    Text(
                        text = authorName,
                        fontSize = tokens.type.badge,
                        lineHeight = TEXT_LINE_HEIGHT.em,
                        color = tokens.color.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = tokens.spacing.space1),
                    )
                }
            }
        }
        return
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
    ) {
        if (showSourceMark) {
            if (sourceMark != null) {
                sourceMark(site)
            } else {
                // 槽位没递 = 这一格不摆（桌面那一档；标题自己顶上来，不占空位）。
            }
        }
        if (title != null) {
            Text(
                text = title,
                // 标题字号使用 caption，与 18dp 站标保持同一视觉量级。
                fontSize = tokens.type.caption,
                color = tokens.color.textPrimary,
                maxLines = 1,
                // 一张画可能挂着好几个作品/角色档的标签，标题会很长 —— 省略号收口，
                // 卡片宽度是定死的（横滑行里每一枚都同宽）。
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}


// 卡片角标胶囊（internal 供同包 Pixiv 首页卡片复用，跨包保持私有）。

/**
 * 视频角标：一个「▶」，站方给了时长就带上秒数。
 *
 * 摆右下角。给视频打标不是装饰 ——
 * 站方那档 preview 是**静帧 jpg**，不打标它就是一张看着正常、点开什么也不会动的图，
 * 用户没法知道那张可以播。时长在 yande.re 那侧拿不到（实测字段表里没有），
 * 所以只在有的时候显示，不写"0s"（秒数单位的口径见 `GalleryPost.durationLabel`）。
 *
 * ⚠️**左下角那枚来源胶囊撤了**：来源搬到封面下方那一行
 * （与标题并排，见 [GalleryCardCaption]）。所以这里的"与来源分角不抢位"那条理由
 * 已经不存在，四个角只剩视频与 AI 两枚。
 */
@Composable
internal fun BoxScope.GalleryVideoPill(durationLabel: String) {
    GalleryCornerPill(if (durationLabel.isEmpty()) "▶" else "▶ $durationLabel", Alignment.BottomEnd)
}

/**
 * AI 角标：一枚「AI」。
 *
 * 摆**左上角**。判据与"没有信号就不摆"这条硬口径同一条：站方没打 AI 标签的条目一律不摆，
 * 不靠"画风像 AI"编一个读数出来（判据见 [com.venera.compose.gallery.domain.GalleryAi]）。
 *
 * ⚠️ 摆不摆由**画廊自己那枚开关**管（`prefs.galleryAiBadge`），与漫画守卫页的 block_ai 是两把。
 * 屏蔽开着时这条根本不会上屏，所以角标的真正用途是"我不屏蔽，但我要看得出来"。
 */
@Composable
internal fun BoxScope.GalleryAiPill() {
    GalleryCornerPill("AI", Alignment.TopStart)
}

/**
 * 多页角标：一枚「4P」（「要能在图片卡片上另加看出这张有几页，而不是点进作品才知道」）。
 *
 * 摆**右下角**：左上是 AI，而 pixiv 首页那一族卡把右上给了名次或 AI —— 右下是两族卡都空着的那一格。
 * 与视频那一枚同角但**不会共存**：pixiv 的卡永不是视频（站方给的是 jpg/png 地址），
 * 而 booru 永不带页数（一张 = 一幅，字段表里没这一格）—— 判据在 [GalleryPost.pagesBadge]，
 * 「站方没给」与「只有一页」都不摆，所以这一枚永远只有一个出处。
 */
@Composable
internal fun BoxScope.GalleryPagesPill(badge: String) {
    GalleryCornerPill(badge, Alignment.BottomEnd)
}

/**
 * 卡片角上那枚小胶囊。
 *
 * 不复用 [com.venera.compose.components.venera.VeneraSourceBadge]：那枚是**左上角小圆角**、
 * 且被漫画侧 6 个页面共用，改它等于一起动别人。这里的口径是"胶囊 + 更小"，
 * 但**尺寸全部取现成 token**（badge 字号 / badge 内边距 / extraLarge 圆角），不新造数字。
 * 底板仍用固定深色：封面颜色不可控，只有固定底板能保证任何图上都可读。
 *
 * 刻意**不可点**：点下去要的是"只看这一站"，本仓还没有筛选态的出口，做了就是假按钮。
 *
 * 它只剩视频 / AI 两枚在用（来源那一枚换成了
 * [VeneraGallerySourceMark] 图形标识），所以这里只留形状，不再有"源名"这个用途。
 */
@Composable
internal fun BoxScope.GalleryCornerPill(text: String, alignment: Alignment) {
    val tokens = VeneraTokens
    Text(
        text = text,
        fontSize = tokens.type.badge,
        fontWeight = tokens.type.weightMedium,
        color = StatusColors.OnBadgeSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .align(alignment)
            .padding(tokens.spacing.badgeInset)
            .background(
                StatusColors.BadgeSurface.copy(alpha = 0.72f),
                RoundedCornerShape(tokens.shape.extraLarge),
            )
            .padding(
                horizontal = tokens.spacing.badgeHorizontalPadding,
                vertical = tokens.spacing.badgeVerticalPadding / 2,
            ),
    )
}


@Composable
internal fun GalleryPostCard(
    card: GalleryCard,
    /** 取图器；null = 各平台应用级单例（见 [GalleryCardsGrid] 同名参数那条）。 */
    imageLoader: ImageLoader?,
    /**
     * 这一张的标题（`uid → 标题`，由调用方整批算好）。
     *
     * 透传而不是在卡内各查一次：标题要开 SQLite 查离线词典，几十张卡各查一次就是
     * 几十趟查询（理由与"整批摊平去重"的做法见 `rememberGalleryCardTitles`）。
     */
    titles: Map<String, String>,
    /**
     * 把这张卡**封面那一块**在窗口坐标系里的矩形一起交出去 —— 只用来裁出滑入期间飞进来的那一帧
     * （见 [GalleryFlyIn]）。**不是整张卡**：落点是大图页的画面框，起点必须与它同构才不闪，
     * 理由写在卡内 [coverBounds] 那处头注里。
     */
    onOpen: (Rect) -> Unit,
    onLongPress: (() -> Unit)? = null,
    /** 这一张是否已选中（仅在 [selecting] 为真时有意义）。 */
    selected: Boolean = false,
    /** 是否处于多选态：右上角摆统一选择框，点卡片改成勾选。 */
    selecting: Boolean = false,
    /** 点选择框 / 多选态下点卡片。 */
    onToggleSelect: (() -> Unit)? = null,
    overlay: (@Composable BoxScope.(GalleryPost) -> Unit)? = null,
    /** 非空 = 这张卡的封面参与共享元素飞行，值就是那对两端逐字相同的 key（见 [GalleryCardsGrid]）。 */
    sharedElementKey: String? = null,
    /**
     * 非空 = **不按原图比例**，一律按这个比例摆（画师介绍页的「热门作品」用）。
     *
     * 取数回来的那几张比例各不相同，按原比例摆就是一行三张高矮不齐 —— 而它们是**按行**摆的
     * （`Row + weight`，行与行必然对齐），于是矮的那两张底下留出空洞（真机截图里能直接看到）。
     * 钉死比例之后一行三张等高，"参差"这件事从屏上消失。
     *
     * 代价如实写在这里：**原图会被裁**（ContentScale.Crop）。这是"整齐"换"信息完整"，
     * 不是白拿的 —— 首页主墙那种瀑布流不该传它（那里列与列各自往下排，错落是刻意的）。
     */
    fixedRatio: Float? = null,
    /**
     * 作者头像地址（`uid → 地址`，与 `titles` 同一条透传路径）。
     * `null`（map 里没这一项）= 文字区走「站标 + 标题」那一行；
     * 非 null = 「头像 + 标题/画师名」两行（见 [GalleryCardCaption]）。
     */
    authorAvatar: String? = null,
    /**
     * 点**署名行那颗头像** = 进这位作者的画师页（「改成点击画师头像进入画师页」）。
     *
     * `null` ⇒ 头像不可点（图库三站的墙不传头像表，天然走不到这一档）。
     * 与整卡点击**分开**：整卡是"看这张图"，头像那一下是"看这个人"。
     */
    onOpenArtist: (() -> Unit)? = null,
    /** 预览清晰度两档 + AI 角标开关：由 `[GalleryCardsGrid]` 从调用点整屏读一次透传下来。 */
    quality: GalleryPreviewQuality,
    pixivQuality: PixivImageQuality,
    aiBadge: Boolean,
    /** 截那一帧 / 登记落点 / 撤落点（宿主槽位，见 `[GalleryCardsGrid]` 同名参数）。 */
    flyCapture: ((coverBounds: Rect) -> Unit)? = null,
    noteFlyBounds: ((viewerKey: String, full: Rect, visible: Rect) -> Unit)? = null,
    forgetFlyBounds: ((viewerKey: String) -> Unit)? = null,
    /** 框被夹取时的取证读数回调（安卓 `Log.d`）；null = 不念。 */
    onRatioClamped: ((post: GalleryPost, ratio: Float) -> Unit)? = null,
    /** 站标槽位与开关（透传给 `[GalleryCardCaption]`；桌面 null/false = 那一行只有标题）。 */
    sourceMark: (@Composable (site: GallerySite) -> Unit)? = null,
    /** 这一张给不给"点开"那一下动作（缺省给；`GalleryCardsGrid.openableOf` 算出来递进来）。 */
    openable: Boolean = true,
    /** 自定义信息栏；null = 共享 `GalleryCardCaption`。 */
    cardInfo: (@Composable (post: GalleryPost, title: String?) -> Unit)? = null,
    /** true = 不夹取图片比例，按站方原始比例铺满。 */
    preserveNaturalImageHeight: Boolean = false,
) {
    val tokens = VeneraTokens
    val post = card.post
    // 预览清晰度由调用点整屏读取并透传，保持随设置实时更新且不依赖平台 Context。
    /**
     * 飞行体的**起点矩形**：卡片里**封面那一块**的窗口矩形，**不是整张卡**。
     *
     * 12:00 用户报"卡片整个飞进去了，内容包裹还是会闪一下"，成因就在这里：
     * 落点是大图页里"按真实比例定框"的**画面框**（只有图、没有下面那条标题），
     * 而整卡像素里还带着标题条与卡片面板的圆角 —— 飞行末段等于把整块"包裹"一起放大铺进画面框
     * （卡宽约半屏、画面框约一屏，包裹被放大近两倍），交棒那一刻包裹整片消失，读起来就是闪。
     *
     * 共享元素的起点与终点**必须同构**：两份画面都该是"同一比例的一张图"。这也正是页内那对
     * 真共享元素的口径 —— `coverSharedElement` 挂的本来就是封面（见 [GalleryPosterRow]）。
     */
    var coverBounds by remember { mutableStateOf(Rect.Zero) }
    // 卡片回收时注销飞行落点，防止返回动画飞向过期矩形；键统一用 viewerKey，无落点表时不挂 effect。
    if (forgetFlyBounds != null) {
        DisposableEffect(post.viewerKey) {
            val key = post.viewerKey
            onDispose { forgetFlyBounds(key) }
        }
    }
    // 比例夹取统一走 cardBoxRatio，保证墙卡与作品页飞行两端比例一致；fixedRatio 为设计值不夹取。
    val ratio = fixedRatio?.takeIf { it > 0f }
        ?: post.cardRatio.takeIf { preserveNaturalImageHeight && it > 0f }
        ?: cardBoxRatio(post, tokens.spacing.coverAspectRatio)
    // 比例被夹取时上报读数，便于排查 Crop 裁剪导致的画面显示不全。
    val clamped = !preserveNaturalImageHeight && cardBoxRatioClamped(post, tokens.spacing.coverAspectRatio)
    LaunchedEffect(post.uid, clamped, ratio) {
        if (clamped) {
            onRatioClamped?.invoke(post, ratio)
        }
    }
    // 选中态与那枚选择框交给 SelectableCardFrame —— 四处收藏面板同一份实现
    // （洗底不透明度、框的位置与画法全在那一处，这里不再各画一遍）。
    SelectableCardFrame(
        selecting = selecting,
        selected = selected,
        onToggleSelect = { onToggleSelect?.invoke() },
        modifier = Modifier.fillMaxWidth(),
    ) {
    Card(
        // 使用 miuix Card 可长按重载并显式对齐 tokens.shape.card 圆角，避免 VeneraCard 固定内边距在封面两侧留缝。
        cornerRadius = tokens.shape.card,
        modifier = Modifier.fillMaxWidth(),
        // 按下仅缩放卡面（0.94），不缩放外层 SelectableCardFrame，防止多选框在按压时相对卡角滑移。
        pressFeedbackType = VeneraPressFeedback.card,
        // 多选态下点整张卡 = 勾选（只让人去够右上角那个小圈太费劲）；非多选态才是点开大图页。
        // 阶段三 T3：`openable = false` 的那一档**不给那一下动作**（与 `onLongPress` 传 null
        // 同一口径 —— 没有的动作不摆成死点击，A 条"打码卡不放大"由装配层的 `openableOf` 判）。
        onClick = {
            if (selecting) {
                onToggleSelect?.invoke()
            } else if (openable) {
                onOpen(coverBounds)
            }
        },
        onLongPress = onLongPress,
    ) {
        // 封面地址取一次：存成局部量，免得两次调用之间设置项变化
        // （那会让封面与文字区里的头像不同源）。
        val wallUrl = if (post.site == GallerySite.PIXIV) {
            GallerySettingsModel.pixivWallUrl(post, pixivQuality)
        } else {
            GallerySettingsModel.wallUrl(post, quality)
        }
        @Composable
        fun CoverNode() {
            VeneraCover(
                url = wallUrl,
                contentDescription = "${post.site.displayName} #${post.id}",
                // 宽高由外部 aspectRatio 定死，封面再自持 3:4 会在槽位里横向留缝（轮播那条同因）。
                preserveAspectRatio = false,
                mask = if (card.masked) VeneraCoverMask.Masked else VeneraCoverMask.Visible,
                imageLoader = imageLoader,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(ratio)
                    .onGloballyPositioned {
                        // 截像素那一路**必须**吃裁过的那一把（PixelCopy 只能读窗口内的表面）：显式写出来。
                        val rect = it.boundsInWindow(clipBounds = true)
                        // 批 B §3 地基：定位那一把要**没裁过**的（`clipBounds = false`）。
                        // pixiv 竖长条页常年有几屏高住在窗口外，裁过的那一把只配截像素、不配当位置。
                        val full = it.boundsInWindow(clipBounds = false)
                        // 仅在 Rect 结构变化时更新 coverBounds：boundsInWindow 每次返回新实例，无条件写入会导致滚动每帧重组。
                        if (rect != coverBounds) coverBounds = rect
                        // 顺手给**返回程**留一个落点（大图页离场时按 uid 来查）。
                        // ⚠️ 打码卡**不回写** —— 红线是"打码那张两边都不飞"，
                        // 而"表里压根没有它"是这条红线最不容易被绕过的一种落法。
                        if (!card.masked) noteFlyBounds?.invoke(post.viewerKey, full, rect)
                    }
                    // 不挂飞行时压根不调那一层（而不是传一个空 key 进去）：
                    // key 两端必须逐字相同才触发，空串是个"看起来挂了其实永远对不上"的假状态。
                    .let { base ->
                        val key = sharedElementKey ?: return@let base
                        base.coverSharedElement(key = key, allowFly = !card.masked)
                    },
            ) {
                // 视频唯一的例外：不标出来，它就是一张"加载失败的图"（缩略图是站方的静帧 jpg）。
                // 摆右下角。**来源不再压角**：它搬到封面下方那一行与标题并排，
                // 压角方案在两站混排时要用户从图的四个角里找，而参考图那枚就在标题左边。
                if (post.isVideo) GalleryVideoPill(post.durationLabel)
                // AI 角标按站方硬字段优先、画廊标签词表兜底，开关由调用点透传。
                if (aiBadge && GalleryAi.isAiMarked(post.siteAiFlag, post.tagList)) GalleryAiPill()
                // 多页角标：站方报了页数且大于一才摆，判据在 `GalleryPost.pagesBadge`。
                post.pagesBadge?.let { GalleryPagesPill(it) }
                overlay?.invoke(this, post)
            }
        }
        @Composable
        fun CaptionNode(mod: Modifier) {
            if (cardInfo != null) {
                Box(mod) {
                    cardInfo(post, titles[post.uid])
                }
            } else {
                GalleryCardCaption(
                    site = post.site,
                    // 没有作品/角色标签时给 null ⇒ 那一行只画来源标识，不画空文字。
                    title = titles[post.uid],
                    authorAvatar = authorAvatar,
                    authorName = post.author.takeIf { it.isNotBlank() },
                    imageLoader = imageLoader,
                    onOpenArtist = onOpenArtist,
                    sourceMark = sourceMark,
                    modifier = mod,
                )
            }
        }
        // 统一采用封面在上、文字区在下的 Column 布局，避免浮层遮挡封面底沿并简化飞行动画衔接。
        Column(modifier = Modifier.fillMaxWidth()) {
            CoverNode()
            CaptionNode(
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = tokens.spacing.space4,
                        end = tokens.spacing.space4,
                        top = tokens.spacing.space3,
                        bottom = tokens.spacing.space4,
                    ),
            )
        }
    }
    }
}

