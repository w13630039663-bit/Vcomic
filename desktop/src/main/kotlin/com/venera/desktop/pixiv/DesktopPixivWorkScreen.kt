package com.venera.desktop.pixiv

import com.venera.desktop.data.DesktopPixivWorkTrail
import com.venera.desktop.ui.theme.VeneraType
import com.venera.desktop.ui.DesktopPushButton
import com.venera.desktop.gallery.ui.GalleryTokens
import com.venera.desktop.gallery.ui.DesktopWallCardModel
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.rotate
import com.venera.desktop.ui.components.CanvasAmbientGlow
import com.venera.desktop.ui.components.DesktopContextMenuArea
import com.venera.desktop.ui.components.DesktopMenuEntry
import com.venera.desktop.ui.components.canvasFrameShell
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.venera.compose.components.LocalUxPlatformServices
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.components.venera.VeneraCoverMask
import com.venera.compose.gallery.data.GalleryPixivWorks
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.PixivCredentials
import com.venera.compose.gallery.data.PixivWorkDetail
import com.venera.compose.gallery.domain.PixivWorkPage
import com.venera.compose.gallery.domain.PixivImageQuality
import com.venera.desktop.gallery.data.DesktopGalleryPreferences
import com.venera.desktop.gallery.ui.DesktopUgoiraFrameRenderer
import com.venera.desktop.gallery.ui.DesktopUgoiraState
import com.venera.desktop.gallery.ui.rememberDesktopUgoiraPlayback
import com.venera.compose.ui.tokens.VeneraTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch



/**
 * 大图两侧的**作品级**翻页钮（上一作 / 下一作）：40dp 圆形玻璃底 + 单向箭头。
 *
 * 为什么"有没有"由调用点决定：入口没有列（下载记录 / 相关作品）时那两格就是 null，
 * 屏上干脆不摆 —— 摆一枚点了没反应的箭头，比不摆更坏。
 */
@Composable
private fun PixivWorkStepArrow(
    forward: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(
                tokens.color.surfaceContainerHigh.copy(alpha = if (hovered) 0.94f else 0.78f),
            )
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (forward) Icons.Outlined.ChevronRight else Icons.Outlined.ChevronLeft,
            contentDescription = if (forward) "下一个作品" else "上一个作品",
            tint = tokens.color.textPrimary,
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * 页坞两端的**页级**翻页钮（本作多图）：24dp 透明底 + 小箭头。
 *
 * 与作品级那两枚分开摆：一对贴着「多图预览」这一排管页，一对贴着画布管作品。
 * `enabled = false` = 到头：压到 35% 且不响应。
 */
@Composable
private fun PixivDockStepArrow(
    forward: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val tokens = VeneraTokens
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(tokens.color.textPrimary.copy(alpha = if (hovered && enabled) 0.14f else 0f))
            .hoverable(interaction, enabled = enabled)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (forward) Icons.Outlined.ChevronRight else Icons.Outlined.ChevronLeft,
            contentDescription = if (forward) "下一张" else "上一张",
            tint = tokens.color.textSecondary.copy(alpha = if (enabled) 1f else 0.35f),
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * 评论发送框两枚小钮（表情 / 贴纸）：30dp 圆形底，选中时染主色。
 * 与画布那两枚箭头分开 —— 这一对是"往输入框里塞东西"，不是翻页。
 */
@Composable
internal fun CommentGlyphButton(
    icon: ImageVector,
    description: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val tokens = VeneraTokens
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(
                when {
                    active -> tokens.color.primary.copy(alpha = 0.18f)
                    hovered -> tokens.color.surfaceContainerHigh.copy(alpha = 0.8f)
                    else -> tokens.color.surfaceContainerHigh.copy(alpha = 0.4f)
                },
            )
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (active) tokens.color.primary else tokens.color.textSecondary,
            modifier = Modifier.size(17.dp),
        )
    }
}

/**
 * 关注钮：未关注 = 幽灵钮（`+ 关注`），已关注 = 主色实心 + 勾。
 *
 * 关注成功那一下播一段**一次性**动效（勾弹起 + 一圈扩散 + 六点飞散，约 640ms）——
 * 点了要看得见成了。不挂无限动画：静置时这一屏必须零帧（同背板那条理由）。
 * 取消关注不播：那是收手，不是庆祝。
 */
@Composable
internal fun PixivFollowButton(
    followed: Boolean,
    busy: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val tokens = VeneraTokens
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val burst = remember { androidx.compose.animation.core.Animatable(0f) }
    var wasFollowed by remember { mutableStateOf(followed) }
    LaunchedEffect(followed) {
        if (followed && !wasFollowed) {
            burst.snapTo(0f)
            burst.animateTo(
                targetValue = 1f,
                animationSpec = androidx.compose.animation.core.tween(
                    durationMillis = 640,
                    easing = androidx.compose.animation.core.LinearOutSlowInEasing,
                ),
            )
        }
        wasFollowed = followed
    }
    val checkScale by animateFloatAsState(
        targetValue = if (followed) 1f else 0.6f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium,
        ),
        label = "followCheck",
    )
    val bg = if (followed) {
        tokens.color.primary
    } else {
        tokens.color.surfaceContainerHigh.copy(alpha = if (hovered) 0.85f else 0.5f)
    }
    val fg = if (followed) tokens.color.onPrimary else tokens.color.textPrimary
    Box(modifier = Modifier.graphicsLayer { alpha = if (busy) 0.6f else 1f }) {
        // 环与飞点画在按钮外圈：不参与测量，播完即摘（不会常驻一层绘制）。
        val progress = burst.value
        // 颜色提到绘制 lambda 外面：`VeneraTokens` 是组合期读数，绘制块里碰不得。
        val burstColor = tokens.color.primary
        if (progress > 0f && progress < 1f) {
            androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
                val half = size.minDimension / 2f
                drawCircle(
                    color = burstColor.copy(alpha = (1f - progress) * 0.55f),
                    radius = half * (1f + 1.5f * progress),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
                )
                repeat(6) { index ->
                    val angle = (-90f + index * 60f) * kotlin.math.PI.toFloat() / 180f
                    val distance = half * (1f + 1.9f * progress)
                    drawCircle(
                        color = burstColor.copy(alpha = (1f - progress) * 0.8f),
                        radius = 2.5.dp.toPx() * (1f - progress * 0.6f),
                        center = center + androidx.compose.ui.geometry.Offset(
                            kotlin.math.cos(angle) * distance,
                            kotlin.math.sin(angle) * distance,
                        ),
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(99.dp))
                .background(bg)
                .border(
                    0.5.dp,
                    if (followed) Color.Transparent else tokens.color.outlineVariant.copy(alpha = 0.7f),
                    RoundedCornerShape(99.dp),
                )
                .hoverable(interaction, enabled = !busy)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = !busy,
                ) { onToggle(!followed) }
                .height(28.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (followed) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = fg,
                    modifier = Modifier
                        .size(14.dp)
                        .graphicsLayer {
                            scaleX = checkScale
                            scaleY = checkScale
                        },
                )
            }
            Text(
                text = when {
                    busy -> "关注中…"
                    followed -> "已关注"
                    else -> "+ 关注"
                },
                fontSize = VeneraType.aux.size,
                lineHeight = VeneraType.aux.lineHeight,
                color = fg,
                maxLines = 1,
            )
        }
    }
}

/**
 * 评论里的"表情"在站方就是一段颜文字 —— 插进正文的是纯文本，没有图片腿。
 * 表是常用的那一批；站方哪天换词表，用户照样能自己敲。
 */
internal val PIXIV_COMMENT_EMOJI = listOf(
    "(´▽｀)", "(・∀・)", "(*´ω｀*)", "(๑•̀ㅂ•́)و✧", "(´；ω；`)", "ヽ(•̀ω•́ )ゝ",
    "(￣▽￣)", "(*/ω＼*)", "orz", "(´･ω･`)", "＼(^o^)／", "(笑)",
)

/**
 * pixiv 作品页（`desktop-pixiv-work-artist-2026-10-30.html`，稿）。
 *
 * ## 全高双栏
 *
 * 左：大图区 —— 单页摆位 + 顶栏浮层（返回 / 站方原题）+ 左右翻页 + 页码徽章 + 底部
 * 多页缩略图条。**点中央大图 → 看图播放器**（壳接 `GalleryPostViewer` 路由，缩放与
 * 全屏它都有，这里不重造播放器、也不摆缩放胶囊）。
 *
 * 右：信息栏 —— 画师行 / 计数格 / 标题 / 标签 / 评论。**点画师行 → 右栏就地切成
 * 画师面板**（不跳独立页；完整画师主页仍在面板底部那枚按钮）。
 *
 * ## 多图机制
 *
 * 详情腿一次带全 `pages`（不变式 `pages.size == pageCount` 由取数层保证），「当前页」
 * 一颗状态住本屏：大图、页码徽章、缩略图条、翻页钮共用，不会各翻各的。
 *
 * ## 禁假开关
 *
 * - related 相关作品：站方 app-api 没有这条腿 ⇒ 相关作品区**整块不摆**。
 * - 评论：站方评论腿**必须带票据**（无票据四形状全 404，判据在 `GalleryPixivWorks.illustComments`）——
 *   未登录只念一句「登录后可看 N 条」；作者关了评论（`commentsOff`）整节不摆。
 * - 收藏 / 关注：登录才摆（未登录摆了发不出去）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DesktopPixivWorkScreen(
    illustId: Long,
    title: String,
    pixiv: GalleryPixivWorks,
    account: PixivCredentials,
    /** 画质四类：详情中央图吃插画/漫画两档（按页数分），看图腿吃大图缩放档。 */
    qualityPrefs: DesktopGalleryPreferences? = null,
    /** 壳已在窗口根铺好整窗背板：本屏不再自绘，否则两份糊图在内容盒边界上错位成硬边。 */
    backdropFromShell: Boolean = false,
    onBack: () -> Unit,
    onOpenArtist: (Long, String) -> Unit,
    /**
     * 看大图（中央大图那一下 → 看图播放器，壳接 `GalleryPostViewer` 路由）：
     * 交**全页表卡 + 当前页号**——多图作品在播放器里左右翻的是同一篇的其他页，
     * 返回时按 [resumePage] 记住翻到哪张。
     */
    onOpenPage: (List<DesktopWallCardModel>, Int) -> Unit,
    /** 返回恢复：播放器退出时回传的页号（≥1 才有意义）；0 = 从头看。 */
    resumePage: Int = 0,
    /**
     * 上一作 / 下一作：来路那一列里紧挨着的两篇（壳从 `DesktopPixivWorkTrail` 取、装进路由带来）。
     * null = 这一列里没有前/后一篇，或入口压根没有列 ⇒ 那一侧箭头不摆。
     */
    onOpenPrevWork: (() -> Unit)? = null,
    onOpenNextWork: (() -> Unit)? = null,
    /**
     * 点画师的作品卡 → **另一篇作品详情**（pixiv 卡都进详情页；
     * 画师面板 / 信息栏「这位画师的其他作品」两个网格共用这一跳，壳带 `from = 当前作品页`）。
     */
    onOpenPost: (GalleryPost) -> Unit,
    /** 收藏 / 取消收藏（登录腿写站方）。`null` = 未登录 ⇒ 整枚钮不摆。 */
    onBookmarkToggle: (suspend (Long, Boolean) -> Result<Unit>)?,
    /** 下载原图或多图。 */
    onSavePixivWork: (suspend (PixivWorkDetail) -> String)? = null,
    /** 这一篇是否已经落过盘（按下载目录里的文件名判；null = 这一侧没有下载目录）。 */
    isWorkSaved: ((PixivWorkDetail) -> Boolean)? = null,
    /** 复制当前查看的大图到剪贴板（多图只复制当前页那一档）；返回是否写进剪贴板。null = 不摆复制入口。 */
    onCopyImage: (suspend (String) -> Boolean)? = null,
    /**
     * 点标签 chip → pixiv 源搜索：壳切 pixiv 搜索模式带词跳搜索屏。
     * 右键标签 = 复制原文 / 复制译名（走 `LocalUxPlatformServices.copyToClipboard`）。
     */
    onTagSearch: (String) -> Unit,
) {
    val tokens = VeneraTokens
    val ux = com.venera.compose.components.LocalUxPlatformServices.current
    val auth by account.authState.collectAsState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val signed = auth is com.venera.compose.gallery.data.PixivAuthState.SignedIn
    var current by remember(illustId) { mutableIntStateOf(0) }
    // 返回恢复：播放器退出回传的页号落回来，中央大图接着翻到离开时那张。
    androidx.compose.runtime.LaunchedEffect(resumePage) {
        if (resumePage > 0) current = resumePage
    }
    var showArtistPane by remember(illustId) { mutableStateOf(false) }
    // 右栏浮卡的视差源：指针相对窗口中心的偏移，归一化到 -0.5..0.5。
    // 写的是 State 不是局部量 —— 各卡在 graphicsLayer 里读，只重绘不重组。
    val paneParallax = remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    // 「点击看大图」提示 chip：进页先给一句，2.6s 后自淡（悬停语义桌面自会）。
    var canvasHintVisible by remember(illustId) { mutableStateOf(true) }
    LaunchedEffect(illustId) {
        delay(2600)
        canvasHintVisible = false
    }
    var bookmarkBusy by remember { mutableStateOf(false) }
    var bookmarkNote by remember { mutableStateOf<String?>(null) }
    var followBusy by remember { mutableStateOf(false) }
    var followNote by remember { mutableStateOf<String?>(null) }
    // 关注态就地覆盖：`detail` / `userDetail` 只在进场取一次，写成功之后不覆盖这一格，
    // 钮上就会一直停着进来那一刻的旧值（点了像没点上，成功动画也没得播）。
    var followOverride by remember(illustId) { mutableStateOf<Boolean?>(null) }

    // 优先让中央大图加载，右栏相关作品网格待其落定或 4s 超时后再启动，避免缩略图抢占带宽。
    var canvasSettled by remember(illustId, current) { mutableStateOf(false) }
    // 大图三态：加载中 / 失败。慢网或直连被限时，中央不许只是一片模糊背板 ——
    // 那与"图本来就这么糊"分不开，用户只能干等。
    var canvasLoading by remember(illustId, current) { mutableStateOf(true) }
    var canvasLoaded by remember(illustId, current) { mutableStateOf(false) }
    var canvasFailed by remember(illustId, current) { mutableStateOf(false) }
    var canvasRetry by remember(illustId, current) { mutableIntStateOf(0) }
    // 超时兜底：连接被挂住时转圈不许无限转，30s 没落定就当失败，把重试口给出来。
    LaunchedEffect(illustId, current, canvasRetry) {
        delay(30_000)
        if (canvasLoading) {
            canvasLoading = false
            canvasFailed = true
            canvasSettled = true
        }
    }
    LaunchedEffect(illustId, current) {
        delay(4_000)
        canvasSettled = true
    }

    // 右栏信息浮卡展开/收起：spring 非线性动画驱动宽度，画布 weight(1f) 每帧跟随重排 ——
    // 收起时大图放大吃满释放出来的空间，展开时缩回。每篇作品进页默认展开。
    // 宽度跟窗口分档：400dp 一档写死的话，宽屏上信息挤成一列、窄窗又把画布吃掉。
    var rootW by remember { mutableIntStateOf(0) }
    val paneBase = when {
        rootW >= 1760 -> 480f
        rootW >= 1440 -> 440f
        rootW >= 1120 -> 400f
        else -> 340f
    }
    var paneShown by rememberSaveable(illustId) { mutableStateOf(true) }
    val paneWidth by animateDpAsState(
        targetValue = if (paneShown) paneBase.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
            visibilityThreshold = 1.dp,
        ),
        label = "pixivPaneWidth",
    )
    val paneIconSpin by animateFloatAsState(
        targetValue = if (paneShown) 0f else 180f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "pixivPaneSpin",
    )
    var detailRetryTick by remember(illustId) { mutableStateOf(0) }
    val detailState = produceState<Result<PixivWorkDetail?>?>(null, illustId, detailRetryTick) {
        value = null
        val t0 = System.currentTimeMillis()
        value = pixiv.workDetail(illustId, account.bearerOrNull())
        println("D_取数 作品详情 耗时=${System.currentTimeMillis() - t0}ms")
    }
    val detail = detailState.value?.getOrNull()
    val detailError = detailState.value?.exceptionOrNull()
    // 画质：插画详情 / 漫画详情（按页数分：多页走漫画档）+ 看图播放器的大图缩放档。
    val illustQ = qualityPrefs?.pixivIllustQuality?.collectAsState()?.value ?: PixivImageQuality.MEDIUM
    val mangaQ = qualityPrefs?.pixivMangaQuality?.collectAsState()?.value ?: PixivImageQuality.MEDIUM
    val zoomQ = qualityPrefs?.pixivZoomQuality?.collectAsState()?.value ?: PixivImageQuality.LARGE
    val detailQ = if ((detail?.pageCount ?: 1) > 1) mangaQ else illustQ
    // 卡片预览那一档：相关作品网格与列表卡同吃这一枚，不自己挑尺寸。
    val listQ = qualityPrefs?.pixivPreviewQuality?.collectAsState()?.value ?: PixivImageQuality.MEDIUM
    // 多图机制的那一页列：一次带全，元素是折好的 GalleryPost（看图路由直接吃）。
    // 大图缩放档喂 pageWallOf 的 viewerQuality（每页 fileUrl = 该档地址）。
    val pages = remember(detail, zoomQ) { detail?.let { PixivWorkPage.pageWallOf(it, zoomQ) } ?: emptyList() }
    val page = pages.getOrNull(current.coerceIn(0, (pages.size - 1).coerceAtLeast(0)))
    // 播放器页队列：折好的逐页 post 各转一张卡（uid 天然带 :pN 页号），看图路由直接吃。
    val viewerPageCards = remember(pages) {
        pages.map {
            com.venera.desktop.gallery.ui.desktopWallCardOf(
                it,
                com.venera.compose.gallery.domain.GalleryPreviewQuality.LARGE,
            )
        }
    }
    // 画布当前那张的地址：中央大图与「复制图片」吃同一颗算式，多图只取当前页。
    val canvasImageUrl = page?.let { p ->
        when (detailQ) {
            PixivImageQuality.SMALL ->
                com.venera.compose.gallery.domain.pixivSmallUrlOf(p.previewUrl)
                    .ifBlank { p.largeUrl }.ifBlank { p.fastUrl }
            PixivImageQuality.MEDIUM -> p.previewUrl
            PixivImageQuality.LARGE -> p.largeUrl.ifBlank { p.fastUrl }.ifBlank { p.previewUrl }
            // 原图档就取真原图：`p.fileUrl` 是**看图缩放档**的地址（另一枚设置）。
            PixivImageQuality.ORIGINAL ->
                detail?.pages?.getOrNull(p.pageOrdinal ?: current)
                    ?.urlFor(PixivImageQuality.ORIGINAL)
                    ?.takeIf { it.isNotBlank() } ?: p.fileUrl
        }
    }
    // 复制取固定大图档：详情清晰度档只管画布，原图几十 MB 解成位图后剪贴板与内存都吃不消。
    val copyImageUrl = page?.let { p ->
        p.largeUrl.ifBlank { p.fastUrl }.ifBlank { p.previewUrl }
    }
    // 画布承托四枚（设置页可调，与画廊详情页同源）。改设置即时生效。
    val canvasFrame = com.venera.desktop.settings.DesktopCanvasFrame.current.collectAsState().value
    // 画作形状只认站方报的宽高；没报就不替站方编比例，交给父约束。
    val canvasRatio = page?.let { p ->
        if (p.width > 0 && p.height > 0) p.width.toFloat() / p.height else null
    }
    // 多图作品：当前页落定后顺手预取下一页的 1200 大图（按画布测量尺寸限制解码，避免原图大小进缓存）。
    val platformCtx = coil3.compose.LocalPlatformContext.current
    var canvasSizePx by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val purgeGpuCache = com.venera.desktop.app.LocalGpuCachePurge.current
    androidx.compose.runtime.DisposableEffect(purgeGpuCache) {
        onDispose { purgeGpuCache?.invoke("pixiv_work_dispose") }
    }
    LaunchedEffect(current, detailQ, canvasLoaded, pages.size, canvasSizePx) {
        if (!canvasLoaded) return@LaunchedEffect
        if (canvasSizePx.width <= 0 || canvasSizePx.height <= 0) return@LaunchedEffect
        val nextUrl = pages.getOrNull(current + 1)?.largeUrl
        if (nextUrl.isNullOrBlank()) return@LaunchedEffect
        coil3.SingletonImageLoader.get(platformCtx).enqueue(
            coil3.request.ImageRequest.Builder(platformCtx)
                .data(nextUrl)
                .size(canvasSizePx.width.coerceAtMost(1600), canvasSizePx.height.coerceAtMost(1600))
                .build(),
        )
    }

    // ── ：pixiv ugoira 动图在详情页即自动逐帧循环播放（不必点进大图）──
    val isUgoira = detail?.ugoira == true
    val ugoiraPlayback = if (isUgoira) {
        rememberDesktopUgoiraPlayback(
            pixiv,
            illustId,
            original = zoomQ == PixivImageQuality.ORIGINAL,
            retryKey = 0,
        )
    } else {
        null
    }
    val ugoiraReadyClip = (ugoiraPlayback as? DesktopUgoiraState.Ready)?.clip

    // 整页根层 = 放大模糊的作品图背板（B 站详情页式），
    // 右栏玻璃半透 tint 直接透已糊背板 ⇒ 真·玻璃态。与画廊详情页同口径。
    val backdropUrl = detail?.post?.backdropUrl

    Box(
        modifier = Modifier
            .fillMaxSize()
            // 只有宽度换档才写 state，resize 途中不至于每帧重组。
            .onSizeChanged { if (it.width != rootW) rootW = it.width }
            // 指针位置只喂视差：不消费事件，画布自己的悬停与点击照常。
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Exit) {
                            // 指针离开窗口就回正，否则卡片会停在最后一次偏移的位置歪着。
                            paneParallax.value = androidx.compose.ui.geometry.Offset.Zero
                            continue
                        }
                        if (event.type != PointerEventType.Move) continue
                        val pos = event.changes.firstOrNull()?.position ?: continue
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val h = size.height.toFloat().coerceAtLeast(1f)
                        paneParallax.value = androidx.compose.ui.geometry.Offset(
                            pos.x / w - 0.5f,
                            pos.y / h - 0.5f,
                        )
                    }
                }
            },
    ) {
        if (!backdropFromShell) {
            com.venera.desktop.gallery.ui.DesktopImageBackdrop(
                url = backdropUrl,
                modifier = Modifier.fillMaxSize(),
            )
        }
        CompositionLocalProvider(LocalPaneParallax provides paneParallax) {
        Row(modifier = Modifier.fillMaxSize()) {

        // ── 左：大图区（翻页钮常显，不再跟悬停走）──
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            when {
                detailError != null -> com.venera.desktop.ui.components.DesktopFailedState(
                    kind = com.venera.desktop.platform.desktopErrorKindOf(detailError),
                    title = "作品详情加载失败",
                    subtitle = com.venera.desktop.platform.desktopFailureNoteOf(detailError),
                    onRetry = { detailRetryTick++ },
                    modifier = Modifier.fillMaxSize(),
                )
                detail == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "正在取这一作的详情…",
                        fontSize = VeneraType.body.size, lineHeight = VeneraType.body.lineHeight,
                        color = tokens.color.textTertiary,
                        textAlign = TextAlign.Center,
                    )
                }
                page == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "这一作没有可摆的图页。",
                        fontSize = VeneraType.body.size, lineHeight = VeneraType.body.lineHeight,
                        color = tokens.color.textTertiary,
                        textAlign = TextAlign.Center,
                    )
                }
                else -> {
                    // 返回把手统一收口到 titlebar，画布不再重复绘制；中央大图采用 Fit 完整展示不裁剪。
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxSize()
                            .padding(top = 24.dp, bottom = if (pages.size > 1) 112.dp else 40.dp)
                            .padding(horizontal = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        // 中央图按画质档取 URL（与复制图片共用 canvasImageUrl），禁止用 page.fastUrl 顶替 LARGE。
                        val imgUrl = canvasImageUrl
                        // 原图档是几 MB 起步，等它下完才有画面太硬 —— 先摆 1200 大图做占位，原图到了淡入盖上去。
                        val upgradingToOriginal = detailQ == PixivImageQuality.ORIGINAL
                        val upgradeAlpha by androidx.compose.animation.core.animateFloatAsState(
                            targetValue = if (upgradingToOriginal && !canvasLoaded) 0f else 1f,
                            animationSpec = androidx.compose.animation.core.tween(220),
                            label = "pixivCanvasUpgrade",
                        )
                        // 撤长按/按住的安卓涟漪——indication=null + hover 微放大
                        //（桌面语义；与 gallery 详情画布同口径）。
                        val canvasInteraction = remember { MutableInteractionSource() }
                        val canvasImgHovered by canvasInteraction.collectIsHoveredAsState()
                        // 画作承托壳：按画作比例定形、四枚参数走设置页；环境光铺在壳内最底层。
                        Box(
                            modifier = Modifier
                                .then(
                                    if (canvasRatio != null) {
                                        Modifier.aspectRatio(canvasRatio, matchHeightConstraintsFirst = true)
                                    } else {
                                        Modifier.fillMaxSize()
                                    },
                                )
                                .onSizeChanged { canvasSizePx = it }
                                .canvasFrameShell(canvasFrame),
                        ) {
                        CanvasAmbientGlow(
                            url = canvasImageUrl,
                            intensity = canvasFrame.glow,
                        )
                        // 原图档的清晰占位层（1200 大图，先到先看）；原图落定后由上层淡入盖住。
                        if (upgradingToOriginal) {
                            AsyncImage(
                                model = page.largeUrl.ifBlank { page.previewUrl },
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(canvasFrame.radiusDp.dp)),
                            )
                        }
                        // 重试键变了就连整颗 AsyncImage 一起重建 —— coil 的失败结果不落盘，重建即真重发一次。
                        key(canvasRetry) {
                        // 画布右键 = 复制当前查看的大图（多图只复制当前页）。
                        com.venera.desktop.ui.components.DesktopContextMenuArea(
                            items = {
                                buildList {
                                    if (onCopyImage != null && copyImageUrl != null) {
                                        add(
                                            DesktopMenuEntry.Item(
                                                label = "复制图片",
                                                icon = Icons.Outlined.ContentCopy,
                                                onClick = {
                                                    scope.launch {
                                                        ux.toast("正在复制…")
                                                        val ok = runCatching { onCopyImage(copyImageUrl) }
                                                            .getOrDefault(false)
                                                        ux.toast(
                                                            if (ok) {
                                                                "图片已复制到剪贴板"
                                                            } else {
                                                                "复制失败：这张图没能放进剪贴板"
                                                            },
                                                        )
                                                    }
                                                },
                                            ),
                                        )
                                    }
                                }
                            },
                        ) {
                        AsyncImage(
                            model = imgUrl,
                            contentDescription = "${detail.title.ifBlank { title }} p${current + 1}",
                            contentScale = ContentScale.Fit,
                            // 大图落定（成功或失败）才放开右栏那些图，见上面 `canvasSettled` 那段。
                            onLoading = {
                                canvasLoading = true
                                canvasLoaded = false
                                canvasFailed = false
                            },
                            onSuccess = {
                                canvasLoading = false
                                canvasLoaded = true
                                canvasFailed = false
                                canvasSettled = true
                            },
                            onError = {
                                canvasLoading = false
                                canvasFailed = true
                                canvasSettled = true
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(canvasFrame.radiusDp.dp))
                                .graphicsLayer {
                                    val lift = if (canvasImgHovered) 1.01f else 1f
                                    scaleX = lift
                                    scaleY = lift
                                    alpha = upgradeAlpha
                                }
                                .hoverable(canvasInteraction)
                                .clickable(
                                    interactionSource = canvasInteraction,
                                    indication = null,
                                ) { onOpenPage(viewerPageCards, current) },
                        )
                        }
                        }
                        }
                        // 加载中：没有占位图就中央转圈；原图档已有 1200 大图铺着，只报一句进度，
                        // 别拿转圈盖住已经能看的内容。
                        if (canvasLoading) {
                            if (upgradingToOriginal) {
                                Text(
                                    text = "原图加载中",
                                    fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                                    color = tokens.color.textSecondary,
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = tokens.spacing.space4)
                                        .background(
                                            tokens.color.surfaceContainerHigh.copy(alpha = 0.9f),
                                            RoundedCornerShape(50),
                                        )
                                        .padding(horizontal = 12.dp, vertical = 3.dp),
                                )
                            } else {
                                Column(
                                    modifier = Modifier.matchParentSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    androidx.compose.material3.CircularProgressIndicator(
                                        modifier = Modifier.size(28.dp),
                                        color = tokens.color.primary,
                                        strokeWidth = 2.dp,
                                    )
                                    Text(
                                        text = "加载中",
                                        fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                                        color = tokens.color.textSecondary,
                                        modifier = Modifier.padding(top = tokens.spacing.space2),
                                    )
                                }
                            }
                        }
                        // 失败：说清是什么事 + 一个真重试口（不假装还在加载）。
                        // 原图档失败时大图还铺着，失败句收成一条底栏，不整屏盖住能看的那张。
                        if (canvasFailed && upgradingToOriginal) {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = tokens.spacing.space3)
                                    .background(
                                        tokens.color.surfaceContainerHigh.copy(alpha = 0.92f),
                                        RoundedCornerShape(50),
                                    )
                                    .padding(horizontal = 12.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
                            ) {
                                Text(
                                    text = "原图没下来，先看大图",
                                    fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                                    color = tokens.color.textSecondary,
                                )
                                DesktopPushButton(
                                    onClick = {
                                        canvasFailed = false
                                        canvasLoading = true
                                        canvasLoaded = false
                                        canvasRetry++
                                    },
                                ) {
                                    Text("重试")
                                }
                            }
                        } else if (canvasFailed) {
                            Column(
                                modifier = Modifier.matchParentSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    text = "这张图没下来",
                                    fontSize = VeneraType.cardTitle.size, lineHeight = VeneraType.cardTitle.lineHeight,
                                    color = tokens.color.textPrimary,
                                )
                                Text(
                                    text = "网络或站方限流，重试一次看看。",
                                    fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                                    color = tokens.color.textSecondary,
                                    modifier = Modifier.padding(top = tokens.spacing.space1),
                                )
                                DesktopPushButton(
                                    onClick = {
                                        canvasFailed = false
                                        canvasLoading = true
                                        canvasLoaded = false
                                        canvasRetry++
                                    },
                                    modifier = Modifier.padding(top = tokens.spacing.space3),
                                ) {
                                    Text("重试")
                                }
                            }
                        }
                        // ── ugoira 就绪即叠逐帧（自动循环；取帧失败则仍显静帧不空）──
                        val ugoiraClip = ugoiraReadyClip
                        if (ugoiraClip != null) {
                            DesktopUgoiraFrameRenderer(
                                clip = ugoiraClip,
                                paused = false,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .matchParentSize()
                                    .clip(RoundedCornerShape(tokens.shape.small))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) { onOpenPage(viewerPageCards, current) },
                            )
                        }
                    }
                    // 作品级翻页：来路那一列里的上一作 / 下一作（壳把两格装进路由带来）。
                    // 入口没有"列"（下载记录、相关作品）= 两格都 null = 这两枚不摆，不替用户编顺序。
                    onOpenPrevWork?.let { open ->
                        PixivWorkStepArrow(
                            forward = false,
                            onClick = open,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 12.dp),
                        )
                    }
                    onOpenNextWork?.let { open ->
                        PixivWorkStepArrow(
                            forward = true,
                            onClick = open,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 12.dp),
                        )
                    }
                    // 「点击看大图」hint：进页给一句，2.6s 自淡（桌面悬停语义靠光标）。
                    if (canvasHintVisible) {
                        Text(
                            text = if (isUgoira) "动图自动循环播放 · 点击进入大图" else "点击看大图",
                            fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                            color = tokens.color.textSecondary,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = if (pages.size > 1) 96.dp else 22.dp)
                                .clip(RoundedCornerShape(50))
                                .background(tokens.color.surfaceContainerHigh.copy(alpha = 0.72f))
                                .padding(horizontal = 12.dp, vertical = 5.dp),
                        )
                    }
                    if (pages.size > 1) {
                        // 底部玻璃页坞：页码 counter + 缩略图条合并成一枚，
                        // 取代旧的"左下 pill + 全宽缩略条"两套各画各的。
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 12.dp)
                                .clip(GalleryTokens.Shape.Bento)
                                .background(tokens.color.surfaceContainerHigh.copy(alpha = 0.72f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            // 本作多图翻页：左右各一枚小箭头，贴着「多图预览」这一排（页坞两端）。
                            // 与作品级那两枚大圈分开摆，一对管页、一对管作品，不再靠猜。
                            PixivDockStepArrow(
                                forward = false,
                                enabled = current > 0,
                                onClick = { if (current > 0) current-- },
                            )
                            Text(
                                text = "${current + 1} / ${pages.size}",
                                fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                                fontWeight = VeneraType.cardTitle.weight,
                                color = tokens.color.primary,
                                modifier = Modifier
                                    .clip(GalleryTokens.Shape.Medium)
                                    .background(tokens.color.primary.copy(alpha = 0.14f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                            LazyRow(
                                modifier = Modifier.widthIn(max = 430.dp),
                                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
                            ) {
                                itemsIndexed(pages) { index, p ->
                                    val selected = index == current
                                    Box(
                                        modifier = Modifier
                                            .width(52.dp)
                                            .height(40.dp)
                                            .clip(RoundedCornerShape(tokens.shape.small))
                                            .background(tokens.color.surfaceContainerHigh)
                                            .clickable { current = index },
                                    ) {
                                        VeneraCover(
                                            url = p.previewUrl.ifBlank { p.fastUrl }.ifBlank { p.fileUrl },
                                            contentDescription = "p${index + 1}",
                                            mask = VeneraCoverMask.Visible,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                        if (selected) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(tokens.color.primary.copy(alpha = 0.25f)),
                                            )
                                        }
                                        Text(
                                            text = "p${index + 1}",
                                            fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                                            color = Color.White,
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(3.dp)
                                                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                                                .padding(horizontal = 6.dp, vertical = 1.dp),
                                        )
                                    }
                                }
                            }
                            PixivDockStepArrow(
                                forward = true,
                                enabled = current < pages.lastIndex,
                                onClick = { if (current < pages.lastIndex) current++ },
                            )
                        }
                    }
                }
            }
        }

        // 分隔竖线已撤（二批）：右栏改玻璃浮卡，背后是根层模糊背板。

        // ── 右：信息栏 / 画师面板（点头像就地切面板，不跳独立页）──
        // 二批：玻璃浮卡（B 站式）——大圆角 + 半透玻璃 + 发丝描边，内缩浮起。
        Box(
            modifier = Modifier
                .width(paneWidth)
                .fillMaxHeight()
                .padding(
                    start = 4.dp * (paneWidth.value / paneBase),
                    end = 10.dp * (paneWidth.value / paneBase),
                    top = 6.dp,
                    bottom = 10.dp,
                ),
        ) {
            val authorId = detail?.authorId
            if (showArtistPane && authorId != null) {
                // 画师面板仍是一整页，自己保留玻璃底；信息栏那侧改由多张浮卡拼成。
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                        .background(com.venera.desktop.gallery.ui.GdGlass.panel())
                        .border(0.5.dp, tokens.color.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
                ) {
                DesktopPixivArtistSidePane(
                    userId = authorId,
                    fallbackName = detail.post.author.ifBlank { detail.authorAccount.ifBlank { "画师" } },
                    pixiv = pixiv,
                    account = account,
                    signed = signed,
                    followBusy = followBusy,
                    followNote = followNote,
                    followedOverride = followOverride,
                    onFollowToggle = { want ->
                        followBusy = true
                        followNote = null
                        scope.launch {
                            val bearer = account.bearerOrNull()
                            val result = if (bearer == null) {
                                Result.failure(IllegalStateException("拿不到票据"))
                            } else if (want) {
                                pixiv.followAdd(bearer, detail.authorId.toString(), private = false)
                            } else {
                                pixiv.followRemove(bearer, detail.authorId.toString())
                            }
                            followBusy = false
                            if (result.isSuccess) followOverride = want
                            followNote = result.fold(
                                onSuccess = { if (want) "已关注。" else "已取消关注。" },
                                onFailure = { t -> "没写上：${t.message?.take(120) ?: t.javaClass.simpleName}" },
                            )
                        }
                    },
                    onBackToInfo = { showArtistPane = false },
                    onOpenPost = onOpenPost,
                    onOpenArtistHome = { id, name -> onOpenArtist(id, name) },
                )
                }
            } else {
                DesktopPixivWorkInfoPane(
                    illustId = illustId,
                    title = title,
                    detail = detail,
                    pixiv = pixiv,
                    account = account,
                    signed = signed,
                    listQuality = listQ,
                    onOpenArtistPane = { if (detail?.authorId != null) showArtistPane = true },
                    followBusy = followBusy,
                    followNote = followNote,
                    followedOverride = followOverride,
                    onFollowToggle = { want ->
                        val authorId = detail?.authorId
                        if (authorId != null && !followBusy) {
                            followBusy = true
                            followNote = null
                            scope.launch {
                                val bearer = account.bearerOrNull()
                                val result = if (bearer == null) {
                                    Result.failure(IllegalStateException("拿不到票据"))
                                } else if (want) {
                                    pixiv.followAdd(bearer, authorId.toString(), private = false)
                                } else {
                                    pixiv.followRemove(bearer, authorId.toString())
                                }
                                followBusy = false
                                if (result.isSuccess) followOverride = want
                                followNote = result.fold(
                                    onSuccess = { if (want) "已关注。" else "已取消关注。" },
                                    onFailure = { t -> "没写上：${t.message?.take(120) ?: t.javaClass.simpleName}" },
                                )
                            }
                        }
                    },
                    onBookmarkToggle = onBookmarkToggle,
                    onSavePixivWork = onSavePixivWork,
                    isWorkSaved = isWorkSaved,
                    onCopyImage = onCopyImage,
                    canvasImageUrl = canvasImageUrl,
                    copyImageUrl = copyImageUrl,
                    onOpenPost = onOpenPost,
                    onOpenArtistHome = onOpenArtist,
                    onTagSearch = onTagSearch,
                )
            }
        }


        }
        }
        // ── 信息栏开关：右上角的工具栏式图标钮 ──
        // 桌面习惯：面板开关住在内容区顶端，不浮在栏缝里。它跟着栏的左缘走 ——
        // 栏展开时停在画布右上角（离开标题与标签），收起后滑到窗口右上角，仍是"展开"的入口。
        val handleInteraction = remember { MutableInteractionSource() }
        val handleHovered by handleInteraction.collectIsHoveredAsState()
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = -paneWidth)
                .padding(top = 12.dp, end = 12.dp)
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    tokens.color.surfaceContainerHigh.copy(
                        alpha = if (handleHovered) 0.95f else 0.72f,
                    ),
                )
                .border(
                    0.5.dp,
                    tokens.color.outlineVariant.copy(alpha = 0.45f),
                    RoundedCornerShape(8.dp),
                )
                .hoverable(handleInteraction)
                .clickable(
                    interactionSource = handleInteraction,
                    indication = null,
                ) { paneShown = !paneShown },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = if (paneShown) "收起信息栏" else "展开信息栏",
                tint = if (handleHovered) tokens.color.textPrimary else tokens.color.textSecondary,
                modifier = Modifier
                    .size(16.dp)
                    .rotate(paneIconSpin),
            )
        }
    }
}

