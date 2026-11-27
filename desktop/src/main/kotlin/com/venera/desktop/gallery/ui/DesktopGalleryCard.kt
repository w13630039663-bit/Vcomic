package com.venera.desktop.gallery.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.PlatformContext
import coil3.compose.LocalPlatformContext
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.components.venera.VeneraCoverMask
import com.venera.compose.data.platform.PathProvider
import com.venera.compose.gallery.data.GalleryFavorites
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.ui.tokens.LocalVeneraDarkTheme
import com.venera.compose.ui.tokens.VeneraMotion
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.compose.ui.tokens.rememberCardEnterProgress
import com.venera.desktop.gallery.data.DesktopGalleryPixivWorks
import com.venera.desktop.gallery.data.DesktopGalleryPreferences
import com.venera.desktop.gallery.data.DesktopSaveImageOutcome
import com.venera.desktop.gallery.data.desktopResolveSaveDir
import com.venera.desktop.gallery.data.desktopSaveImageRun
import com.venera.desktop.gallery.data.desktopSaveImageScreenReading
import com.venera.desktop.ui.components.DesktopContextMenuArea
import com.venera.desktop.ui.components.DesktopContextMenuPopup
import com.venera.desktop.ui.components.DesktopMenuEntry
import com.venera.desktop.ui.vShadow
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 画廊卡片动作真口集合，由壳层通过 [LocalDesktopGalleryCardActions] 注入，避免在墙组件中直接依赖业务单例。
 *
 * @param favoriteUids 当前本地收藏作品的唯一身份集合（`site:id`）。
 * @param pixivBookmarkedIds 当前已确认在 Pixiv 云端收藏的作品编号集合。
 * @param onToggleFavorite 切换收藏状态（Pixiv 同步云端书签，其余图库写入本地收藏）并返回新状态与提示文本。
 * @param onAddFavorite 只补缺失的收藏；批量收藏吃它，逐张 toggle 会把已收藏的取消掉。
 * @param onDownload 执行存盘并返回成功标识与提示文本。
 * @param onNotify 发送动作完成或失败提示文本。
 */
internal class DesktopGalleryCardActions(
    val favoriteUids: Set<String>,
    val pixivBookmarkedIds: Set<Long> = emptySet(),
    val onToggleFavorite: suspend (GalleryPost) -> Pair<Boolean, String>,
    val onAddFavorite: suspend (GalleryPost) -> Pair<Boolean, String>,
    val onDownload: suspend (PlatformContext, GalleryPost) -> Pair<Boolean, String>,
    val onNotify: (String) -> Unit,
)

/** 画廊卡片快捷动作的全局提供槽；未接入时不渲染需业务口的按钮。 */
internal val LocalDesktopGalleryCardActions = staticCompositionLocalOf<DesktopGalleryCardActions?> { null }

/**
 * 构造并记忆卡片动作集合：Pixiv 作品同步网络收藏，其余图库站写入本地收藏库。
 *
 * @param ports 画廊业务端口，提供本地收藏与 Pixiv 网络接口。
 * @param paths 桌面数据目录提供器。
 * @param prefs 画廊偏好设置，用于读取自定义下载目录与直存开关。
 * @param onNotify 状态反馈提示回调。
 */
@Composable
internal fun rememberDesktopGalleryCardActions(
    ports: GalleryPorts?,
    paths: PathProvider?,
    prefs: DesktopGalleryPreferences?,
    onNotify: (String) -> Unit,
): DesktopGalleryCardActions? {
    val favorites: GalleryFavorites? = ports?.favorites
    if (favorites == null && paths == null && ports?.pixiv == null) return null
    val emptyFavFlow = remember { MutableStateFlow(emptyList<com.venera.compose.gallery.data.GalleryFavorite>()) }
    val favList by (favorites?.favorites ?: emptyFavFlow).collectAsState()
    val favUids = remember(favList) { favList.mapTo(HashSet()) { it.uid } }

    val emptyPixivFlow = remember { MutableStateFlow(emptySet<Long>()) }
    val pixivFlow = (ports?.pixiv as? DesktopGalleryPixivWorks)?.bookmarkedIds ?: emptyPixivFlow
    val pixivIds by pixivFlow.collectAsState()

    return remember(favUids, pixivIds, ports, paths, prefs, onNotify) {
        DesktopGalleryCardActions(
            favoriteUids = favUids,
            pixivBookmarkedIds = pixivIds,
            onToggleFavorite = { post ->
                if (post.site == GallerySite.PIXIV) {
                    val pixiv = ports?.pixiv
                    val bearer = ports?.pixivAccount?.bearerOrNull()
                    if (pixiv == null || bearer == null) {
                        false to "请先登录 Pixiv 账号"
                    } else {
                        val wasFav = post.id in pixivIds
                        val res = if (wasFav) {
                            pixiv.bookmarkRemove(bearer, post.id)
                        } else {
                            pixiv.bookmarkAdd(bearer, post.id, private = false)
                        }
                        if (res.isSuccess) {
                            !wasFav to if (wasFav) "已取消 Pixiv 收藏" else "已同步收藏到 Pixiv"
                        } else {
                            wasFav to "Pixiv 收藏操作失败"
                        }
                    }
                } else {
                    val nowFav = favorites?.toggle(post) ?: false
                    nowFav to if (nowFav) "已加入图库收藏" else "已取消图库收藏"
                }
            },
            onAddFavorite = { post ->
                val alreadyFav = if (post.site == GallerySite.PIXIV) {
                    post.id in pixivIds
                } else {
                    post.uid in favUids
                }
                if (alreadyFav) {
                    true to "已在收藏中"
                } else if (post.site == GallerySite.PIXIV) {
                    val pixiv = ports?.pixiv
                    val bearer = ports?.pixivAccount?.bearerOrNull()
                    if (pixiv == null || bearer == null) {
                        false to "请先登录 Pixiv 账号"
                    } else {
                        val res = pixiv.bookmarkAdd(bearer, post.id, private = false)
                        res.isSuccess to if (res.isSuccess) "已同步收藏到 Pixiv" else "Pixiv 收藏操作失败"
                    }
                } else {
                    val favorites = ports?.favorites
                    if (favorites == null) {
                        false to "未接入图库收藏"
                    } else {
                        // 只在缺失时调用：本回调在批量里逐张执行，不能走 toggle。
                        val nowFav = favorites.toggle(post)
                        nowFav to if (nowFav) "已加入图库收藏" else "收藏失败"
                    }
                }
            },
            onDownload = { context, post ->
                if (paths == null) {
                    false to "未配置下载目录"
                } else {
                    val dir = desktopResolveSaveDir(paths, prefs)
                    if (dir == null) {
                        false to "已取消保存"
                    } else {
                        val effectivePost = if (post.fileUrl.isNotBlank()) {
                            post
                        } else {
                            val fallbackUrl = post.largeUrl.ifBlank { post.fastUrl }.ifBlank { post.previewUrl }
                            if (fallbackUrl.isBlank()) post else post.copy(fileUrl = fallbackUrl)
                        }
                        val outcome = withContext(Dispatchers.IO) {
                            desktopSaveImageRun(context, dir, effectivePost, paths)
                        }
                        (outcome is DesktopSaveImageOutcome.Saved) to desktopSaveImageScreenReading(outcome)
                    }
                }
            },
            onNotify = onNotify,
        )
    }
}

/**
 * 桌面画廊唯一作品卡片：采用同心圆角实体底托画框、顶部受光渐变边与悬停阶梯弹出动作药丸。
 *
 * @param card 投影后的卡片渲染模型。
 * @param aspect 封面显示比例；网格模式传固定比例，瀑布流传原图比例。
 * @param index 卡片在列表中的序号，用于入场阶梯延迟。
 * @param onOpenPost 点击可打开卡片时触发的回调。
 * @param onSearchArtist Booru 右键或点击画师时的搜画师回调。
 * @param onOpenArtist Pixiv 查看画师主页的可选回调，为空时回退浏览器用户页。
 * @param titleOverride 覆盖显示的站方原题文本。
 * @param avatarUrl 画师头像地址，为空时不占位不渲染默认头像。
 * @param selected 当前是否处于多选选中态。
 * @param onToggleSelect 切换多选状态的回调，为空时不显示多选操作与按钮。
 * @param modifier 外部布局修饰符。
 */
@Composable
internal fun DesktopGalleryCard(
    card: DesktopWallCardModel,
    aspect: Float,
    index: Int = 0,
    onOpenPost: (DesktopWallCardModel) -> Unit,
    onSearchArtist: (String) -> Unit,
    onOpenArtist: (() -> Unit)?,
    titleOverride: String? = null,
    avatarUrl: String? = null,
    /** 画师名覆盖（收件箱图库卡传关注标签名）；null = 走既有判据。 */
    authorOverride: String? = null,
    /** 不摆站徽标（pixiv 收件箱卡无站源胶囊）；false = 保留。 */
    hideSourceBadge: Boolean = false,
    selected: Boolean = false,
    onToggleSelect: (() -> Unit)?,
    modifier: Modifier = Modifier,
    showInfo: Boolean = true,
    extraMenuItems: List<DesktopMenuEntry> = emptyList(),
) {
    val tokens = VeneraTokens
    val outerRadius = 14.dp
    val trayPadding = 4.dp
    val innerRadius = 10.dp
    val outerShape = RoundedCornerShape(outerRadius)
    val innerShape = RoundedCornerShape(innerRadius)
    val isDark = LocalVeneraDarkTheme.current
    val uriHandler = LocalUriHandler.current
    val platformContext = LocalPlatformContext.current
    val actions = LocalDesktopGalleryCardActions.current
    val scope = rememberCoroutineScope()
    val post = card.sourcePost
    val isPixiv = post?.site == com.venera.compose.gallery.data.GallerySite.PIXIV ||
        card.url.contains("pixiv.net") || (post != null && post.source.contains("pixiv.net"))
    val artistLabel = (if (isPixiv) {
        (card.author.ifBlank { post?.author.orEmpty() }).trim()
    } else {
        desktopExtractRealArtistTag(post, card.author.ifBlank { post?.author.orEmpty() })?.trim().orEmpty()
    }).ifBlank { post?.author?.trim().orEmpty() }

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val cardKey = card.uid.ifBlank { card.url }.ifBlank { card.title }
    val entrance = rememberCardEnterProgress(index, cardKey)

    var isPressed by remember { mutableStateOf(false) }
    var localMousePos by remember { mutableStateOf<Offset?>(null) }
    var isPointerInside by remember { mutableStateOf(false) }
    var cardSize by remember { mutableStateOf(IntSize.Zero) }
    var menuExpanded by remember { mutableStateOf(false) }
    var downloadBusy by remember { mutableStateOf(false) }
    var downloadDone by remember { mutableStateOf(false) }
    val copyFeedback = rememberDesktopCopyFeedback()
    val copyBusy = copyFeedback.state == DesktopCopyState.BUSY
    val copyDone = copyFeedback.state == DesktopCopyState.DONE
    val copyFailed = copyFeedback.state == DesktopCopyState.FAILED

    val isCardHovered = (isHovered || isPointerInside) && card.openable
    val isFavorite = post != null && actions != null && if (post.site == GallerySite.PIXIV) {
        post.id in actions.pixivBookmarkedIds
    } else {
        post.uid in actions.favoriteUids
    }
    val hasActiveFeedback = downloadBusy || downloadDone || copyBusy || copyDone || copyFailed || menuExpanded

    val favPulse = remember { Animatable(1f) }
    LaunchedEffect(downloadDone) {
        if (downloadDone) {
            delay(1100L)
            downloadDone = false
        }
    }

    val percentX = remember(localMousePos, cardSize) {
        val pos = localMousePos ?: return@remember 0f
        if (cardSize.width == 0) 0f
        else ((pos.x - cardSize.width / 2f) / (cardSize.width / 2f)).coerceIn(-1f, 1f)
    }
    val percentY = remember(localMousePos, cardSize) {
        val pos = localMousePos ?: return@remember 0f
        if (cardSize.height == 0) 0f
        else ((pos.y - cardSize.height / 2f) / (cardSize.height / 2f)).coerceIn(-1f, 1f)
    }

    val animRotX by animateFloatAsState(
        targetValue = if (isCardHovered && !isPressed) percentY * -5f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "cardTiltX",
    )
    val animRotY by animateFloatAsState(
        targetValue = if (isCardHovered && !isPressed) percentX * 5f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "cardTiltY",
    )
    val animScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else if (isCardHovered) 1.022f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "cardScale",
    )
    val liftOffset by animateDpAsState(
        targetValue = if (isCardHovered) (-7).dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "cardLift",
    )
    val shadowElevation by animateDpAsState(
        targetValue = when {
            // 浅底上投影视觉放大近一倍，悬停档要压下来，否则摊成一片灰。
            isCardHovered -> if (isDark) 18.dp else 8.dp
            isDark -> 6.dp
            else -> 3.dp
        },
        animationSpec = tween(durationMillis = 200),
        label = "cardShadow",
    )
    val glintAlpha by animateFloatAsState(
        targetValue = if (isCardHovered && localMousePos != null) (if (isDark) 0.16f else 0.30f) else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "cardGlint",
    )
    // 悬停时在卡片下方铺开可控色弥散光：暗色不画 Skia 投影，纵深靠它表达。
    val ambientAlpha by animateFloatAsState(
        targetValue = if (isCardHovered) 0.34f else 0f,
        animationSpec = tween(durationMillis = 260),
        label = "cardAmbient",
    )
    val trayTop by animateColorAsState(
        targetValue = if (isDark) {
            if (isCardHovered) Color(0xFF222C3D) else Color(0xFF1B2231)
        } else {
            Color.White
        },
        animationSpec = tween(durationMillis = 200),
        label = "cardTrayTop",
    )
    val trayBottom by animateColorAsState(
        targetValue = if (isDark) {
            if (isCardHovered) Color(0xFF171D29) else Color(0xFF12161F)
        } else {
            if (isCardHovered) Color(0xFFFDFEFF) else Color(0xFFF7F9FC)
        },
        animationSpec = tween(durationMillis = 200),
        label = "cardTrayBottom",
    )

    val panYPercent = remember(localMousePos, cardSize) {
        val pos = localMousePos ?: return@remember 0.5f
        val imgH = if (cardSize.height > 0) cardSize.height * 0.72f else 220f
        (pos.y / imgH.coerceAtLeast(1f)).coerceIn(0f, 1f)
    }
    val panXPercent = remember(localMousePos, cardSize) {
        val pos = localMousePos ?: return@remember 0.5f
        if (cardSize.width == 0) 0.5f else (pos.x / cardSize.width.toFloat()).coerceIn(0f, 1f)
    }
    val panSpec = spring<androidx.compose.ui.unit.Dp>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow,
    )
    val animPanOffsetY by animateDpAsState(
        targetValue = if (isCardHovered && !card.masked) ((0.5f - panYPercent) * 13).dp else 0.dp,
        animationSpec = panSpec,
        label = "cardPan",
    )
    val animPanOffsetX by animateDpAsState(
        targetValue = if (isCardHovered && !card.masked) ((0.5f - panXPercent) * 13).dp else 0.dp,
        animationSpec = panSpec,
        label = "cardPanX",
    )
    // 前景角标/胶囊与封面反向微移，拉开三层景深；只走 offset 不进测量。
    val animUiOffsetX by animateDpAsState(
        targetValue = if (isCardHovered) (percentX * 2.8f).dp else 0.dp,
        animationSpec = panSpec,
        label = "cardUiPanX",
    )
    val animUiOffsetY by animateDpAsState(
        targetValue = if (isCardHovered) (percentY * 2.8f).dp else 0.dp,
        animationSpec = panSpec,
        label = "cardUiPanY",
    )
    val animImgScale by animateFloatAsState(
        targetValue = if (isCardHovered && !card.masked) 1.09f else 1.03f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "cardImgScale",
    )

    val triggerFavorite: () -> Unit = {
        if (post != null && actions != null && !card.masked) {
            scope.launch {
                favPulse.snapTo(1f)
                favPulse.animateTo(1.30f, spring(stiffness = Spring.StiffnessHigh))
                favPulse.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
            }
            scope.launch {
                val (_, msg) = actions.onToggleFavorite(post)
                actions.onNotify(msg)
            }
        }
    }

    val triggerDownload: () -> Unit = {
        if (post != null && actions != null && !downloadBusy && !card.masked) {
            downloadBusy = true
            scope.launch {
                val (ok, msg) = actions.onDownload(platformContext, post)
                downloadBusy = false
                if (ok) {
                    downloadDone = true
                }
                actions.onNotify(msg)
            }
        }
    }

    // 复制的是图片本身（大图档），链接走右键菜单；取图与解码落在 IO 上，按钮用三态回执。
    val triggerCopy: () -> Unit = {
        copyFeedback.run {
            val outcome = runCatching {
                desktopCopyImageRun(platformContext, desktopCopyImageUrl(card))
            }.getOrNull()
            val ok = outcome is DesktopCopyImageOutcome.Copied
            actions?.onNotify(
                if (ok) {
                    "图片已复制到剪贴板"
                } else {
                    val cause = (outcome as? DesktopCopyImageOutcome.Refused)?.cause
                        ?: DesktopCopyImageCause.NO_BYTES
                    "复制失败：" + desktopCopyImageFallback(cause)
                },
            )
            ok
        }
    }

    // 右键「复制链接」复制的是作品页地址；复制图片本身留在悬停动作坞那一枚（两件事两个出口）。
    val triggerCopyLink: () -> Unit = {
        val link = post?.pageUrl?.takeIf { it.isNotBlank() } ?: card.url
        if (link.isNotBlank()) {
            val sel = Toolkit.getDefaultToolkit().systemClipboard
            sel.setContents(StringSelection(link), null)
            actions?.onNotify("已复制作品链接")
        }
    }

    var shareBusy by remember { mutableStateOf(false) }

    // 分享卡片：直接在内存中合成卡片位图写入系统剪贴板，不依赖屏幕截图与窗口坐标。
    val triggerShareCard: () -> Unit = {
        if (!shareBusy) {
            shareBusy = true
            scope.launch {
                try {
                    desktopShareGalleryCard(
                        context = platformContext,
                        card = card,
                        titleOverride = titleOverride,
                        avatarUrl = avatarUrl,
                        authorOverride = authorOverride,
                        hideSourceBadge = hideSourceBadge,
                        isDark = isDark,
                    )
                } finally {
                    shareBusy = false
                }
            }
        }
    }

    val openArtistPage: () -> Unit = {
        if (isPixiv && artistLabel.isNotBlank()) {
            if (onOpenArtist != null) {
                onOpenArtist.invoke()
            } else {
                val pixivId = post?.let {
                    com.venera.compose.gallery.domain.GalleryArtistLinks.pixivUserId(it.source)
                } ?: com.venera.compose.gallery.domain.GalleryArtistLinks.pixivUserId(card.url)
                val target = if (pixivId != null) {
                    "${com.venera.desktop.gallery.data.DESKTOP_PIXIV_REFERER}users/$pixivId"
                } else card.url
                uriHandler.openUri(target)
            }
        } else if (artistLabel.isNotBlank()) {
            onSearchArtist(artistLabel)
        }
    }

    val buildMenuEntries: () -> List<DesktopMenuEntry> = {
        listOfNotNull(
            DesktopMenuEntry.Item(
                label = "查看大图",
                shortcut = "Enter",
                icon = Icons.Outlined.PhotoLibrary,
                onClick = { if (card.openable) onOpenPost(card) },
            ),
            if (post != null && actions != null) {
                DesktopMenuEntry.Item(
                    label = if (isFavorite) "取消收藏" else "收藏作品",
                    icon = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    onClick = triggerFavorite,
                )
            } else null,
            if (post != null && actions != null) {
                DesktopMenuEntry.Item(
                    label = "保存原图",
                    icon = Icons.Outlined.Download,
                    onClick = triggerDownload,
                )
            } else null,
            if (onToggleSelect != null) {
                DesktopMenuEntry.Item(
                    label = if (selected) "取消选卡" else "加入选卡",
                    shortcut = "Ctrl+点",
                    icon = Icons.Rounded.Check,
                    onClick = onToggleSelect,
                )
            } else null,
            DesktopMenuEntry.Item(
                label = "复制链接",
                shortcut = "Ctrl+C",
                icon = Icons.Outlined.Link,
                onClick = triggerCopyLink,
            ),
            if (isPixiv) {
                DesktopMenuEntry.Item(
                    label = "分享卡片",
                    icon = Icons.Outlined.Share,
                    onClick = triggerShareCard,
                )
            } else null,
            DesktopMenuEntry.Divider,
            if (card.url.isNotBlank()) {
                DesktopMenuEntry.Item(
                    label = "在站方打开",
                    icon = Icons.Outlined.Language,
                    onClick = { uriHandler.openUri(card.url) },
                )
            } else null,
            if (isPixiv && artistLabel.isNotBlank()) {
                DesktopMenuEntry.Item(
                    label = "查看画师: $artistLabel",
                    icon = Icons.Outlined.Person,
                    onClick = openArtistPage,
                )
            } else if (!isPixiv && artistLabel.isNotBlank()) {
                DesktopMenuEntry.Item(
                    label = "搜画师: $artistLabel",
                    icon = Icons.Outlined.Search,
                    onClick = openArtistPage,
                )
            } else null,
            if (artistLabel.isNotBlank()) {
                DesktopMenuEntry.Item(
                    label = "屏蔽画师: $artistLabel",
                    icon = Icons.Outlined.Block,
                    isDanger = true,
                    onClick = {
                        val sel = Toolkit.getDefaultToolkit().systemClipboard
                        sel.setContents(StringSelection(artistLabel), null)
                    },
                )
            } else null,
        ) + if (extraMenuItems.isNotEmpty()) {
            listOf(DesktopMenuEntry.Divider) + extraMenuItems
        } else {
            emptyList()
        }
    }

    // 悬停抬升时提高同级渲染层，确保浮动投影不被邻卡边界截断。
    DesktopContextMenuArea(
        items = buildMenuEntries,
        modifier = modifier
            .fillMaxWidth()
            .zIndex(if (isCardHovered || menuExpanded) 1f else 0f),
    ) {
        val baseBorder = when {
            selected -> SolidColor(GalleryTokens.Color.OutlineSelected)
            isCardHovered -> Brush.verticalGradient(
                0f to if (isDark) Color(0x5CFFFFFF) else Color(0x590A63F6),
                0.5f to if (isDark) Color(0x1EFFFFFF) else GalleryTokens.Color.OutlineHover,
                1f to if (isDark) Color(0x0AFFFFFF) else GalleryTokens.Color.OutlineHairline,
            )
            else -> Brush.verticalGradient(
                0f to if (isDark) Color(0x33FFFFFF) else Color(0x2417202E),
                0.45f to if (isDark) Color(0x14FFFFFF) else Color(0x1C17202E),
                1f to if (isDark) Color(0x08FFFFFF) else GalleryTokens.Color.OutlineHairline,
            )
        }
        // 悬停且指针在卡内时改用径向追光：近鼠侧点亮，远端回落到常态描边色。
        val localSpan = with(LocalDensity.current) { 210.dp.toPx() }
        val baseTop = if (isDark) Color(0x33FFFFFF) else Color(0x2417202E)
        val borderBrush = when {
            selected -> baseBorder
            isCardHovered && localMousePos != null -> Brush.radialGradient(
                colors = listOf(
                    if (isDark) Color(0x85FFFFFF) else Color(0x7A0A63F6),
                    if (isDark) Color(0x1EFFFFFF) else Color(0x1C17202E),
                    baseTop,
                ),
                center = localMousePos!!,
                radius = localSpan,
            )
            else -> baseBorder
        }

        Box(
            modifier = Modifier
                .drawBehind {
                    if (ambientAlpha > 0.001f) {
                        val w = size.width
                        val h = size.height
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    if (isDark) Color(0x2E4D9FFF) else Color(0x2417202E),
                                ),
                                startY = h * 0.45f,
                                endY = h + 16.dp.toPx(),
                            ),
                            topLeft = Offset(w * 0.08f, h * 0.4f),
                            size = androidx.compose.ui.geometry.Size(w * 0.84f, h * 0.6f + 16.dp.toPx()),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(24.dp.toPx()),
                            alpha = ambientAlpha,
                        )
                    }
                }
                .onGloballyPositioned { coordinates ->
                    cardSize = coordinates.size
                }
                .pointerInput(card.openable) {
                    if (!card.openable) return@pointerInput
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            when (event.type) {
                                PointerEventType.Move -> {
                                    event.changes.firstOrNull()?.position?.let {
                                        localMousePos = it
                                        isPointerInside = true
                                    }
                                }
                                PointerEventType.Enter -> {
                                    isPointerInside = true
                                }
                                PointerEventType.Exit -> {
                                    isPointerInside = false
                                    localMousePos = null
                                }
                            }
                        }
                    }
                }
                .graphicsLayer {
                    alpha = entrance.value
                    translationY = (1f - entrance.value) * VeneraMotion.enterTranslationY.toPx()
                    val baseScale = VeneraMotion.enterBaseScale + (1f - VeneraMotion.enterBaseScale) * entrance.value
                    scaleX = baseScale * animScale
                    scaleY = baseScale * animScale
                    rotationX = animRotX
                    rotationY = animRotY
                    cameraDistance = 14f * density
                }
                .offset(y = liftOffset)
                .vShadow(
                    elevation = shadowElevation,
                    shape = outerShape,
                )
                .clip(outerShape)
                .background(Brush.verticalGradient(listOf(trayTop, trayBottom)))
                .border(
                    width = if (selected) 1.5.dp else 1.dp,
                    brush = borderBrush,
                    shape = outerShape,
                )
                .semantics {
                    contentDescription = card.title
                    onClick(label = card.title) {
                        if (!card.openable) return@onClick false
                        onOpenPost(card)
                        true
                    }
                }
                .hoverable(interactionSource = interactionSource)
                .pointerInput(onOpenPost, onToggleSelect, card) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        if (currentEvent.buttons.isSecondaryPressed) return@awaitEachGesture
                        isPressed = true
                        val ctrl = currentEvent.keyboardModifiers.isCtrlPressed ||
                            currentEvent.keyboardModifiers.isMetaPressed
                        val up = waitForUpOrCancellation()
                        isPressed = false
                        if (up != null && !currentEvent.buttons.isSecondaryPressed) {
                            if (ctrl && onToggleSelect != null) onToggleSelect.invoke()
                            else if (card.openable) onOpenPost(card)
                        }
                    }
                },
        ) {
            // 顶边受光高光刃：宽约 70%，描出卡片上沿的物理厚度。
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 1.dp)
                    .fillMaxWidth(0.7f)
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                if (isDark) Color(0x57FFFFFF) else Color(0xB3FFFFFF),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )
            Column(modifier = Modifier.padding(trayPadding)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(aspect)
                        .clip(innerShape)
                        .background(if (isDark) Color(0xFF1B2130) else GalleryTokens.Color.SurfaceLevel2),
                ) {
                    val coverUrl = post?.let {
                        if (it.isVideo) it.videoPosterUrl.ifBlank { it.previewUrl }
                        else if (it.site == com.venera.compose.gallery.data.GallerySite.PIXIV) {
                            it.previewUrl.ifBlank { it.largeUrl }.ifBlank { it.fileUrl }
                        } else {
                            it.previewUrl.ifBlank { it.largeUrl }.ifBlank { it.fastUrl }
                        }
                    }?.takeIf { it.isNotBlank() } ?: card.url
                    val innerRingColor = if (isDark) Color(0x14FFFFFF) else GalleryTokens.Color.ImageHairline

                    VeneraCover(
                        url = coverUrl,
                        contentDescription = null,
                        mask = if (card.masked) VeneraCoverMask.Masked else VeneraCoverMask.Visible,
                        shimmerWhileLoading = true,
                        preserveAspectRatio = false,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = animImgScale
                                scaleY = animImgScale
                                translationX = animPanOffsetX.toPx()
                                translationY = animPanOffsetY.toPx()
                            },
                    )

                    if (innerRingColor != Color.Transparent) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .border(1.dp, innerRingColor, innerShape),
                        )
                    }

                    // 左上角区域：多选圆钮与作品媒体属性微标，与右上角动作药丸物理隔离。
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .offset(x = animUiOffsetX, y = animUiOffsetY),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (onToggleSelect != null) {
                            androidx.compose.animation.AnimatedVisibility(
                                visible = selected || isCardHovered,
                                enter = fadeIn(tween(160)) + scaleIn(
                                    initialScale = 0.78f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMedium,
                                    ),
                                ),
                                exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.8f),
                            ) {
                                val selBg by animateColorAsState(
                                    targetValue = if (selected) tokens.color.primary else Color(0x8F0C1018),
                                    animationSpec = tween(150),
                                    label = "selectBg",
                                )
                                val selBorder by animateColorAsState(
                                    targetValue = if (selected) tokens.color.primary else Color(0xA6FFFFFF),
                                    animationSpec = tween(150),
                                    label = "selectBorder",
                                )
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(selBg)
                                        .border(1.5.dp, selBorder, CircleShape)
                                        .pointerHoverIcon(PointerIcon.Hand)
                                        .clickable { onToggleSelect.invoke() },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = if (selected) "已选中" else "选择",
                                        tint = Color.White.copy(alpha = if (selected) 1f else 0.72f),
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        }

                        DesktopGalleryCardTopLeftBadges(post = post)
                    }

                    // 右上角常态角标区（R18 / AI + 已收藏红心），悬停时无缝淡出让位给三联动作药丸。
                    androidx.compose.animation.AnimatedVisibility(
                        visible = !isCardHovered && !hasActiveFeedback,
                        enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.88f),
                        exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.88f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .offset(x = animUiOffsetX, y = animUiOffsetY),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            DesktopGalleryCardTopRightBadges(
                                card = card,
                                showAiBadge = LocalDesktopGalleryAiBadge.current,
                            )
                            if (isFavorite) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xB810141C))
                                        .border(1.dp, Color(0x66E26D8A), CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Favorite,
                                        contentDescription = "已收藏",
                                        tint = GalleryTokens.Color.StatusFavorite,
                                        modifier = Modifier.size(13.dp),
                                    )
                                }
                            }
                        }
                    }

                    // 右上角弹出式三联玻璃动作药丸（收藏 · 下载 · 复制）。
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isCardHovered || hasActiveFeedback,
                        enter = fadeIn(tween(170)) +
                            slideInVertically(
                                initialOffsetY = { -it / 3 },
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMediumLow,
                                ),
                            ) +
                            scaleIn(
                                initialScale = 0.90f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMediumLow,
                                ),
                            ),
                        exit = fadeOut(tween(130)) +
                            slideOutVertically(targetOffsetY = { -it / 4 }) +
                            scaleOut(targetScale = 0.92f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .offset(x = animUiOffsetX, y = animUiOffsetY),
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(GalleryTokens.Color.SurfaceGlass)
                                .border(1.dp, Color(0x2EFFFFFF), CircleShape)
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val pillVisible = isCardHovered || hasActiveFeedback
                            if (post != null && actions != null) {
                                val favColor by animateColorAsState(
                                    targetValue = if (isFavorite) GalleryTokens.Color.StatusFavorite else Color(0xFFE8ECF4),
                                    animationSpec = tween(160),
                                    label = "favTint",
                                )
                                val favBg by animateColorAsState(
                                    targetValue = if (isFavorite) Color(0x2EE26D8A) else Color.Transparent,
                                    animationSpec = tween(160),
                                    label = "favBg",
                                )
                                PopActionButton(
                                    visible = pillVisible,
                                    delayMs = 60,
                                    icon = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                    iconTint = favColor,
                                    activeContainer = favBg,
                                    iconScale = favPulse.value,
                                    contentDescription = if (isFavorite) "取消收藏" else "收藏",
                                    onClick = triggerFavorite,
                                )

                                val dlTint by animateColorAsState(
                                    targetValue = if (downloadDone) GalleryTokens.Color.StatusSuccess else Color(0xFFE8ECF4),
                                    animationSpec = tween(160),
                                    label = "dlTint",
                                )
                                val dlBg by animateColorAsState(
                                    targetValue = if (downloadDone) Color(0x2E3ECF8E) else Color.Transparent,
                                    animationSpec = tween(160),
                                    label = "dlBg",
                                )
                                PopActionButton(
                                    visible = pillVisible,
                                    delayMs = 30,
                                    icon = if (downloadDone) Icons.Rounded.Check else Icons.Outlined.Download,
                                    iconTint = dlTint,
                                    activeContainer = dlBg,
                                    iconAlpha = if (downloadBusy) 0.55f else 1f,
                                    contentDescription = "下载原图",
                                    onClick = triggerDownload,
                                )
                            }

                            val cpTint by animateColorAsState(
                                targetValue = when {
                                    copyDone -> GalleryTokens.Color.StatusSuccess
                                    copyFailed -> GalleryTokens.Color.StatusDanger
                                    copyBusy -> Color(0xFFB9C3D4)
                                    else -> Color(0xFFE8ECF4)
                                },
                                animationSpec = tween(160),
                                label = "cpTint",
                            )
                            val cpBg by animateColorAsState(
                                targetValue = when {
                                    copyDone -> Color(0x2E3ECF8E)
                                    copyFailed -> Color(0x2EE26D8A)
                                    else -> Color.Transparent
                                },
                                animationSpec = tween(160),
                                label = "cpBg",
                            )
                            PopActionButton(
                                visible = pillVisible,
                                delayMs = 0,
                                icon = when {
                                    copyDone -> Icons.Rounded.Check
                                    copyFailed -> Icons.Outlined.Close
                                    else -> Icons.Outlined.ContentCopy
                                },
                                iconTint = cpTint,
                                activeContainer = cpBg,
                                iconAlpha = if (copyBusy) 0.7f else 1f,
                                spinIcon = copyBusy,
                                contentDescription = when {
                                    copyBusy -> "正在复制"
                                    copyDone -> "已复制"
                                    copyFailed -> "复制失败"
                                    else -> "复制图片"
                                },
                                onClick = triggerCopy,
                            )
                        }
                    }
                }

                if (showInfo) {
                    DesktopGalleryCardInfo(
                        card = card,
                        modifier = Modifier,
                        titleOverride = titleOverride,
                        avatarUrl = avatarUrl,
                        authorOverride = authorOverride,
                        hideSourceBadge = hideSourceBadge,
                        highlightSource = isCardHovered || menuExpanded,
                        onClickArtist = if (artistLabel.isNotBlank() && card.openable) openArtistPage else null,
                        trailingAction = {
                            // 槽位恒占测量，悬停显隐只动 AnimatedVisibility，不牵动格子重排
                            Box(modifier = Modifier.size(22.dp)) {
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = isCardHovered || menuExpanded,
                                    enter = fadeIn(tween(150)) + slideInHorizontally(initialOffsetX = { it / 2 }),
                                    exit = fadeOut(tween(120)) + slideOutHorizontally(targetOffsetX = { it / 2 }),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (menuExpanded) tokens.color.surfaceVariant else Color.Transparent)
                                            .pointerHoverIcon(PointerIcon.Hand)
                                            .clickable { menuExpanded = !menuExpanded },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.MoreHoriz,
                                            contentDescription = "更多操作",
                                            tint = tokens.color.textSecondary,
                                            modifier = Modifier.size(15.dp),
                                        )
                                    }
                                }
                                if (menuExpanded) {
                                    val menuItems = remember(card, selected, isFavorite, extraMenuItems) { buildMenuEntries() }
                                    if (menuItems.isNotEmpty()) {
                                        DesktopContextMenuPopup(
                                            items = menuItems,
                                            offset = IntOffset(-140, 26),
                                            onDismissRequest = { menuExpanded = false },
                                        )
                                    }
                                }
                            }
                        },
                    )
                }
            }

            if (glintAlpha > 0.005f && localMousePos != null) {
                val glarePos = localMousePos!!
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(outerShape)
                        .drawBehind {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(Color.White.copy(alpha = glintAlpha), Color.Transparent),
                                    center = glarePos,
                                    radius = 220.dp.toPx(),
                                ),
                                center = glarePos,
                                radius = 220.dp.toPx(),
                            )
                        },
                )
            }
        }
    }
}

/**
 * 悬浮动作药丸内的单颗弹性矢量按钮：支持阶梯错峰弹入、独立悬停放大、按压回弹与图标切换动画。
 */
@Composable
private fun PopActionButton(
    visible: Boolean,
    delayMs: Int,
    icon: ImageVector,
    iconTint: Color,
    activeContainer: Color,
    contentDescription: String,
    iconScale: Float = 1f,
    iconAlpha: Float = 1f,
    /** 图标原地旋转：复制这类要等结果的动作用它当「进行中」。 */
    spinIcon: Boolean = false,
    onClick: () -> Unit,
) {
    val enterProgress = remember { Animatable(0f) }
    // 只有"进行中"才建无限旋转：空闲也建会把帧钟钉在 60fps，整窗每帧重画。
    val spinAngle = if (spinIcon) {
        val spin = rememberInfiniteTransition(label = "popSpin")
        val a by spin.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
            label = "popSpinAngle",
        )
        a
    } else {
        0f
    }
    LaunchedEffect(visible) {
        if (visible) {
            enterProgress.snapTo(0f)
            if (delayMs > 0) delay(delayMs.toLong())
            enterProgress.animateTo(
                1f,
                spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
            )
        } else {
            enterProgress.animateTo(0f, tween(110))
        }
    }

    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val interactiveScale by animateFloatAsState(
        targetValue = when {
            pressed -> 0.88f
            hovered -> 1.12f
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "popBtnScale",
    )
    val bgColor by animateColorAsState(
        targetValue = when {
            activeContainer != Color.Transparent -> activeContainer
            hovered -> Color(0x2EFFFFFF)
            else -> Color.Transparent
        },
        animationSpec = tween(140),
        label = "popBtnBg",
    )

    Box(
        modifier = Modifier
            .size(28.dp)
            .graphicsLayer {
                val p = enterProgress.value
                alpha = p.coerceIn(0f, 1f)
                val s = (0.76f + 0.24f * p) * interactiveScale
                scaleX = s
                scaleY = s
                translationY = (1f - p) * -6f
            }
            .clip(CircleShape)
            .background(bgColor)
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = icon,
            transitionSpec = {
                (fadeIn(tween(140)) + scaleIn(initialScale = 0.72f)) togetherWith
                    (fadeOut(tween(100)) + scaleOut(targetScale = 0.72f))
            },
            label = "popBtnIcon",
        ) { targetIcon ->
            Icon(
                imageVector = targetIcon,
                contentDescription = contentDescription,
                tint = iconTint.copy(alpha = iconAlpha),
                modifier = Modifier
                    .size(15.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                        rotationZ = spinAngle
                    },
            )
        }
    }
}
