package com.venera.desktop.gallery.ui

import com.venera.desktop.ui.copyTextToClipboard
import com.venera.desktop.ui.theme.VeneraType
import com.venera.desktop.ui.DesktopPushButton
import com.venera.desktop.gallery.ui.GalleryTokens
import com.venera.desktop.ui.components.DesktopContextMenuArea
import com.venera.desktop.ui.components.DesktopMenuEntry
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import com.venera.desktop.ui.components.CanvasAmbientGlow
import com.venera.desktop.ui.components.canvasFrameShell
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.venera.compose.components.LocalUxPlatformServices
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.ui.tokens.LocalVeneraDarkTheme
import com.venera.compose.gallery.data.GalleryTagLexicon
import com.venera.compose.gallery.domain.buildGalleryTagBuckets
import com.venera.compose.gallery.domain.ratingLabel
import com.venera.compose.gallery.domain.ratingToneOf
import com.venera.compose.ui.tokens.VeneraTokens
import kotlinx.coroutines.launch

/**
 * 图库作品详情页 (对齐 Pixiv 作品详情页与安卓端信息)。
 *
 * ## 设计思想与版面架构
 *
 * 点击图库网格卡片不再直接进入黑屏全屏播放器，而是先进入专业的【图库作品详情页】：
 * - **左侧核心画布**：大图预览 + 自适应环境光 + 悬浮全屏大图播放引导，点击直达播放器；
 * - **右侧检视面板**：完整呈现安卓端 `GalleryInfoSheet` 的五大块信息：
 *   1. 标题与状态行（来源站点徽标、分级胶囊、收藏/复制按钮）；
 *   2. 3 列图片参数矩阵（分辨率、体积、格式、评分、站点、分级）；
 *   3. 真实画师专栏卡片（精准提取画师标签，不再与上传者混淆）；
 *   4. 上游出处链接与一键复制；
 *   5. 严格五桶分类彩色标签（角色、原作、画师、属性、元数据），接通中英词典翻译，支持左键跳搜、右键复制原词与译名；
 *   6. 原图下载与外部打开操作。
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Suppress("UNUSED_PARAMETER") // onBack：返回已上收壳层，形参保留以不动全部调用点
@Composable
internal fun DesktopGalleryPostDetailScreen(
    post: DesktopWallCardModel,
    // 返回已上收壳层（经典档 titlebar / 浅皮档 ContentHead，一屏一返回）。
    // 保留形参只为不动所有调用点与跨平台签名，本屏不再消费它。
    onBack: () -> Unit,
    onOpenViewer: (DesktopWallCardModel) -> Unit,
    onSearchTag: (GallerySite, String) -> Unit,
    modifier: Modifier = Modifier,
    favoriteUids: Set<String> = emptySet(),
    lexicon: GalleryTagLexicon? = null,
    /** pixiv 逐帧包取数腿（ugoira 就地播放用）。null ⇒ ugoira 退静帧、不摆动图。 */
    pixiv: com.venera.compose.gallery.data.GalleryPixivWorks? = null,
    onToggleFavorite: (suspend (DesktopWallCardModel) -> Boolean)?,
    onSaveImage: (suspend (DesktopWallCardModel) -> String)?,
    onCopyImage: (suspend (DesktopWallCardModel) -> Boolean)?,
    /** 取 gif 字节的落盘根（animated gif 逐帧解码用）。null ⇒ gif 退静帧。 */
    paths: com.venera.compose.data.platform.PathProvider? = null,
    /** 图库关注名单（只读）。按钮亮灭只读这份流，屏内不另存 bool。 */
    followedArtists: List<com.venera.compose.gallery.domain.GalleryArtistFollow> = emptyList(),
    /** 关注 / 取关回调。null ⇒ 不摆按钮，不留按了没反应的死钮。 */
    onToggleFollow: (suspend (GallerySite, String) -> Boolean)?,
    /** 「动图逐帧播」档 != NEVER：false ⇒ gif 不自动起播、静帧原样透出（真设置，非假开关）。 */
    animatedAutoPlay: Boolean = true,
    /** 「视频自动播放」总闸：false ⇒ 进详情页不自动放视频（退静帧，仍可点大图手动播）。 */
    videoAutoPlay: Boolean = true,
    /** 详情页画布取图档（设置「详情页清晰度」）：预览/大图/原图，视频与空档自动退大图。 */
    detailQuality: DesktopDetailTier = DesktopDetailTier.SAMPLE,
    /** 详情右栏宽度档（设置「详情栏宽度」）：乘在右栏 weight 上。 */
    paneWidth: DesktopDetailPaneWidth = DesktopDetailPaneWidth.Standard,
    /** 右栏展开态初值（设置「详情栏默认展开」开着时壳递落盘值；页内收起不回写）。 */
    paneOpenDefault: Boolean = true,
    /** 展开态写回（落盘口径在壳）；设置关着记忆时壳递空口子。 */
    onPaneOpenPersist: (Boolean) -> Unit,
    /** 壳已在窗口根铺好整窗背板：本屏不再自绘，否则两份糊图在内容盒边界上错位成硬边。 */
    backdropFromShell: Boolean = false,
) {
    val tokens = VeneraTokens
    val ux = LocalUxPlatformServices.current
    val scope = rememberCoroutineScope()
    val purgeGpuCache = com.venera.desktop.app.LocalGpuCachePurge.current
    androidx.compose.runtime.DisposableEffect(purgeGpuCache) {
        onDispose { purgeGpuCache?.invoke("gallery_detail_dispose") }
    }

    var isFavorite by remember(post.uid, favoriteUids) {
        mutableStateOf(post.uid in favoriteUids)
    }
    LaunchedEffect(post.uid, favoriteUids) {
        isFavorite = post.uid in favoriteUids
    }
    var saving by remember(post.uid) { mutableStateOf(false) }
    var saveMessage by remember(post.uid) { mutableStateOf<String?>(null) }

    val sourcePost: GalleryPost? = post.sourcePost
    val site = sourcePost?.site ?: GallerySite.YANDERE

    // ── ：动图/视频在详情页即自动循环播放（不必点进大图）──
    // mp4/webm 走回调面 libVLC 播放器；booru animated gif 走 skia Codec 逐帧（VLC 解 gif 不稳，已撤）。
    val isVideo = sourcePost?.isVideo == true
    val isGif = sourcePost?.fileExt?.equals("gif", ignoreCase = true) == true
    // 任一"该自动动"的媒体：决定进页自动起播、且点画面不再跳大图。
    val isAnimatedMedia = isVideo || isGif
    // 视频受「视频自动播放」总闸管、gif 受「动图逐帧播」档管；任一 OFF/NEVER ⇒ 该类不自动起播（退静帧，仍可手动播）。
    var videoPlaying by remember(post.uid) {
        mutableStateOf((isVideo && videoAutoPlay) || (isGif && animatedAutoPlay))
    }
    // 动图/视频在播时让静帧图让位，避免叠加错位。
    val videoCoversStatic = isAnimatedMedia && videoPlaying
    val ugoiraIllustId = sourcePost?.id?.takeIf { sourcePost.site == GallerySite.PIXIV && sourcePost.ugoira }
    val ugoiraPlayback = if (ugoiraIllustId != null) {
        rememberDesktopUgoiraPlayback(pixiv, ugoiraIllustId, original = false, retryKey = 0)
    } else {
        null
    }

    val tagList = remember(post.tags, sourcePost) {
        sourcePost?.tagList ?: post.tags.split(' ').map { it.trim() }.filter { it.isNotEmpty() }
    }

    // ⭐️ 接通安卓端标签词典：中文译名表 + 站方/离线权威分类
    var tagTranslations by remember(post.uid) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var tagCategories by remember(post.uid) { mutableStateOf<Map<String, Int>?>(null) }
    var fallbackArtists by remember(post.uid) { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(post.uid, lexicon) {
        val lex = lexicon ?: return@LaunchedEffect
        // 1. 本地词典秒级提取：中英译名 + 离线画师识别
        val trans = runCatching { lex.translations(tagList) }.getOrDefault(emptyMap())
        val artists = runCatching { lex.artistNames(tagList) }.getOrDefault(emptySet())
        tagTranslations = trans
        fallbackArtists = artists

        // 2. 异步联网获取站方权威 tag-type-* 分类
        val p = sourcePost
        if (p != null && p.pageUrl.isNotBlank()) {
            val cats = runCatching { lex.fetchTagCategories(p.pageUrl) }.getOrNull()
            if (!cats.isNullOrEmpty()) {
                tagCategories = cats
                fallbackArtists = emptySet()
            }
        }
    }

    // 标签五桶分桶（调用共享纯函数 buildGalleryTagBuckets）
    val tagGroups = remember(tagList, tagCategories, fallbackArtists) {
        buildGalleryTagBuckets(tagList, tagCategories, fallbackArtists)
    }

    // ⭐️ 核心改进：精确提取画师（优先取画师桶标签或离线词典识别，绝不拿上传者顶替画师）
    val detectedArtist = remember(tagGroups, fallbackArtists) {
        tagGroups.firstOrNull { it.category == 1 }?.tags?.firstOrNull()
            ?: fallbackArtists.firstOrNull()
    }

    val displayTitle = remember(post, detectedArtist) {
        val authorName = detectedArtist ?: post.author.takeIf { it != "未知" }
        if (!authorName.isNullOrBlank()) {
            "$authorName · ${post.title.ifBlank { "作品 #${post.uid.substringAfter(':')}" }}"
        } else {
            post.title.ifBlank { "图库作品 #${post.uid.substringAfter(':')}" }
        }
    }

    // 整页根层 = 放大模糊的作品图背板（B 站详情页式），
    // 顶栏 / 右栏玻璃半透 tint 直接透已糊背板 ⇒ 真·玻璃态，无需 backdrop 实时模糊。
    Box(modifier = modifier.fillMaxSize()) {
        if (!backdropFromShell) {
            DesktopImageBackdrop(url = post.largeUrl.ifBlank { post.url }, modifier = Modifier.fillMaxSize())
        }
        Column(modifier = Modifier.fillMaxSize()) {
        // 顶部信息条仅展示标题与站点标识，不放返回按钮（全窗返回统一收口到壳层）。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(GdGlass.bar())
                .border(0.5.dp, tokens.color.outlineVariant.copy(alpha = 0.3f))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "作品详情",
                fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                fontWeight = VeneraType.cardTitle.weight,
                color = tokens.color.textPrimary,
            )

            Text(
                text = "· ${site.displayName} #${post.uid.substringAfter(':')}",
                fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                color = tokens.color.textTertiary,
            )

            Spacer(Modifier.weight(1f))

            val idText = post.uid.substringAfter(':')
            Text(
                text = "${site.displayName} · $idText",
                fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                fontWeight = VeneraType.control.weight,
                color = tokens.color.textSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(tokens.color.surfaceContainerHigh.copy(alpha = 0.5f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }

        // 右栏检视玻璃浮卡展开/收起：spring 非线性动画驱动 weight 因子，
        // 画布与面板随每帧重排换位 —— 收起时大图放大吃满释放出来的空间。
        var paneShown by rememberSaveable { mutableStateOf(paneOpenDefault) }
        // 展开态记忆：动一次落一次盘（设置关着记忆时壳递的空口子，落了也不读）。
        LaunchedEffect(paneShown) { onPaneOpenPersist(paneShown) }
        val paneFactor by animateFloatAsState(
            targetValue = if (paneShown) 1f else 0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
            label = "galleryPaneFactor",
        )
        val paneIconSpin by animateFloatAsState(
            targetValue = if (paneShown) 0f else 180f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
            label = "galleryPaneSpin",
        )
        // ══ ② 主体双列排布：左画布 + 右检视抽屉 ══
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
        Row(
            modifier = Modifier.fillMaxSize(),
        ) {
            // 画布承托四枚（设置页可调，与 pixiv 详情页同源）；形状只认卡片自己报的纵横比。
            val canvasFrame =
                com.venera.desktop.settings.DesktopCanvasFrame.current.collectAsState().value
            val canvasRatio = if (post.aspect > 0f) post.aspect else null
            // ── 左侧：沉浸大图画布（衬底/光晕已提到根层背板，这里透明直透） ──
            Box(
                modifier = Modifier
                    .weight(1.35f + 1.15f * paneWidth.weightScale * (1f - paneFactor))
                    .fillMaxHeight()
                    .clickable {
                        // 动图/视频正放时画布点击交给内嵌播放器（见 DesktopVideoPlayer），这里绝不在播放时拆播放器
                        // （曾因此点画面即冻死）。gif 与 mp4/webm 同属 isAnimatedMedia，一并跳过 onOpenViewer。
                        if (!isAnimatedMedia) onOpenViewer(post)
                    },
                contentAlignment = Alignment.Center,
            ) {
                // 承托壳：按画作比例定形，环境光铺在壳内最底层。
                Box(
                    modifier = Modifier
                        .padding(tokens.spacing.space4)
                        .then(
                            if (canvasRatio != null) {
                                Modifier.aspectRatio(canvasRatio, matchHeightConstraintsFirst = true)
                            } else {
                                Modifier.fillMaxSize()
                            },
                        )
                        .canvasFrameShell(canvasFrame),
                ) {
                CanvasAmbientGlow(
                    url = desktopDetailQualityUrl(post, detailQuality),
                    intensity = canvasFrame.glow,
                )
                // 中央大图在动图/视频未出画前作底座防闪白；画布点击禁用涟漪，悬停反馈由外层处理。
                if (!videoCoversStatic) {
                    val canvasInteraction = remember { MutableInteractionSource() }
                    val canvasHovered by canvasInteraction.collectIsHoveredAsState()
                    // 画布加载通知（对齐 pixiv 那颗）：慢网/原图档时中央给转圈，成功即收。
                    var canvasLoading by remember(post.uid, detailQuality) { mutableStateOf(true) }
                    AsyncImage(
                        model = desktopDetailQualityUrl(post, detailQuality),
                        contentDescription = post.title,
                        contentScale = ContentScale.Fit,
                        onLoading = { canvasLoading = true },
                        onSuccess = { canvasLoading = false },
                        onError = { canvasLoading = false },
                        modifier = Modifier
                            .fillMaxSize()
                            // 边缘交给外层承托壳（内衬 + 高光边 + 自绘柔影），
                            // 图上不再挂 Skia 投影或发丝描边。
                            .clip(RoundedCornerShape(canvasFrame.radiusDp.dp))
                            .graphicsLayer {
                                val lift = if (canvasHovered) 1.015f else 1f
                                scaleX = lift
                                scaleY = lift
                            }
                            .clickable(
                                interactionSource = canvasInteraction,
                                indication = null,
                            ) { onOpenViewer(post) },
                    )
                    if (canvasLoading) {
                        Column(
                            modifier = Modifier.matchParentSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator(
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

                // ── ugoira 就地逐帧：Ready 才叠在静帧之上，Failed/Pending 仍显静帧 ──
                val readyClip = (ugoiraPlayback as? DesktopUgoiraState.Ready)?.clip
                if (readyClip != null && !videoCoversStatic) {
                    DesktopUgoiraFrameRenderer(
                        clip = readyClip,
                        paused = false,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(canvasFrame.radiusDp.dp)),
                    )
                }

                // ── mp4/webm 走回调面 libVLC 播放器（进详情页即自动循环）；booru gif 走下面 skia 逐帧 ──
                if (isVideo && videoPlaying) {
                    DesktopVideoPlayer(
                        mediaUrl = sourcePost?.videoUrl.orEmpty(),
                        headers = desktopVideoHeadersFor(sourcePost?.videoUrl.orEmpty()),
                        engineKey = post.uid,
                        onDismiss = { videoPlaying = false },
                        onUnavailable = {
                            videoPlaying = false
                            ux.toast("本机没有 VLC，装一个才能播视频")
                        },
                        modifier = Modifier
                            .fillMaxSize(),
                    )
                }

                // ── booru animated gif：skia Codec 逐帧就地播（不经 VLC），盖在静帧之上自动循环 ──
                if (isGif && videoPlaying) {
                    DesktopAnimatedImage(
                        url = sourcePost?.fileUrl.orEmpty(),
                        paths = paths,
                        key = post.uid,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize(),
                        onError = {},
                    )
                }

                }
                // 悬浮全屏大图引导 pill（稿 `.pill` 玻璃变体——
                // 白 14% 透 + 0.5dp 白描边，不再深蓝实底；视频/动图各念各的实话）
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = tokens.spacing.space4)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.14f))
                        .border(0.5.dp, Color.White.copy(alpha = 0.22f), CircleShape)
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Fullscreen,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(15.dp),
                        )
                        Text(
                            text = when {
                                isAnimatedMedia -> "动图自动循环播放中 · 用底部控制条暂停"
                                ugoiraIllustId != null -> "逐帧动图就地自动播放 · 点击进入全屏看大图"
                                else -> "点击进入全屏大图播放器"
                            },
                            fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                            fontWeight = VeneraType.control.weight,
                            color = Color.White,
                        )
                    }
                }
            }

            // ── 右侧：检视玻璃浮卡（二批，B 站式）：大圆角 + 半透玻璃，
            // 背后就是根层模糊背板 ⇒ 玻璃态成立。内缩 10dp 浮起，不再通到窗边。──
            Column(
                modifier = Modifier
                    // weight 必须 >0：spring 收敛到 0 的那一帧会把整棵组合抛进 IllegalArgumentException
                    .weight((1.15f * paneWidth.weightScale * paneFactor).coerceAtLeast(0.001f))
                    .fillMaxHeight()
                    .padding(start = 4.dp * paneFactor, end = 10.dp * paneFactor, top = 6.dp, bottom = 10.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(GdGlass.panel())
                    .border(0.5.dp, tokens.color.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.space4),
            ) {
                // 1. 标题与状态栏（稿 `.tblock`：站徽胶囊 + 分级徽章 + 标题 + meta）
                Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.space2)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        // 站徽胶囊（稿 `.site`：色底白字 10.5/600）
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(GdGlass.siteColor(site))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text(
                                text = site.displayName,
                                fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                                fontWeight = VeneraType.cardTitle.weight,
                                color = Color.White,
                            )
                        }
                        // 分级徽章（稿沿用实现 ratingTone 色系，但改白字实底胶囊）
                        if (sourcePost != null) {
                            val rLabel = ratingLabel(sourcePost.rating)
                            val rColor = when (ratingToneOf(sourcePost.rating)) {
                                com.venera.compose.gallery.domain.GalleryRatingTone.Explicit -> com.venera.desktop.gallery.ui.GalleryTokens.Color.ToneDanger
                                com.venera.compose.gallery.domain.GalleryRatingTone.Caution -> com.venera.desktop.gallery.ui.GalleryTokens.Color.ToneCaution
                                else -> com.venera.desktop.gallery.ui.GalleryTokens.Color.ToneSafe
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(rColor)
                                    .padding(horizontal = 7.dp, vertical = 3.dp),
                            ) {
                                Text(
                                    text = rLabel,
                                    fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                                    fontWeight = VeneraType.cardTitle.weight,
                                    color = Color.White,
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                    }

                    Text(
                        text = displayTitle,
                        fontSize = VeneraType.cardTitle.size, lineHeight = VeneraType.cardTitle.lineHeight,
                        fontWeight = VeneraType.cardTitle.weight,
                        color = tokens.color.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // 快捷操作条
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
                ) {
                    // 收藏按钮
                    if (onToggleFavorite != null) {
                        DesktopPushButton(
                            onClick = {
                                scope.launch {
                                    val ok = onToggleFavorite(post)
                                    isFavorite = ok
                                    ux.toast(if (ok) "已加入图库收藏" else "已移出图库收藏")
                                }
                            },
                            modifier = Modifier.weight(1f).height(36.dp),
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = null,
                                tint = if (isFavorite) GalleryTokens.Color.StatusFavorite else tokens.color.textSecondary,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(if (isFavorite) "已收藏" else "收藏", fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight)
                        }
                    }

                    // 全屏大图按钮
                    DesktopPushButton(
                        onClick = { onOpenViewer(post) },
                        modifier = Modifier.weight(1f).height(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Fullscreen,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("全屏播放", fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight)
                    }

                    // ── 动图/视频 播放·停止切换（进页即自动播，这里可手动停/续）──
                    if (isAnimatedMedia) {
                        DesktopPushButton(
                            onClick = { videoPlaying = !videoPlaying },
                            modifier = Modifier.weight(1f).height(36.dp),
                        ) {
                            Icon(
                                imageVector = if (videoPlaying) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(if (videoPlaying) "停止播放" else "重新播放", fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight)
                        }
                    }

                    // 复制图片：三态回执（转圈 / 对勾 / 叉号），成功与失败各停一秒多。
                    if (onCopyImage != null) {
                        val copyFeedback = rememberDesktopCopyFeedback()
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(tokens.color.surfaceContainerHigh)
                                .clickable {
                                    copyFeedback.run {
                                        val ok = runCatching { onCopyImage(post) }.getOrDefault(false)
                                        ux.toast(if (ok) "已复制图片到剪贴板" else "复制失败：这张图没能放进剪贴板")
                                        ok
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            DesktopCopyGlyph(
                                state = copyFeedback.state,
                                idleTint = tokens.color.textSecondary,
                                doneTint = tokens.color.primary,
                                failedTint = GalleryTokens.Color.StatusDanger,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }

                // 2. 事实信息矩阵（稿 `.facts` 2 列网格玻璃格）
                DetailSectionBox(title = "图片参数", icon = Icons.Outlined.Info) {
                    val rows = remember(post, sourcePost, isFavorite) {
                        if (sourcePost != null) desktopDetailInfoRows(sourcePost, isFavorite) else emptyList()
                    }
                    if (rows.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            rows.chunked(2).forEach { rowPair ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    rowPair.forEach { (label, value) ->
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(GdGlass.card())
                                                .padding(horizontal = 9.dp, vertical = 7.dp),
                                        ) {
                                            Text(label, fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight, color = tokens.color.textTertiary)
                                            Text(
                                                text = value,
                                                fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                                                fontWeight = VeneraType.cardTitle.weight,
                                                color = tokens.color.textPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                    // 奇数行补位
                                    if (rowPair.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                // 3. ⭐️ 创作者名片（精准展示真画师，彻底与上传者区分）
                val hasArtist = detectedArtist != null
                val creatorName = detectedArtist ?: post.author.ifBlank { sourcePost?.author.orEmpty() }
                if (creatorName.isNotBlank() && creatorName != "未知") {
                    val artistCn = detectedArtist?.let { tagTranslations[it.lowercase()] }
                    val isUploader = !hasArtist && (site == GallerySite.YANDERE || site == GallerySite.GELBOORU)
                    val sectionTitle = if (hasArtist) "创作画师" else "上传者信息"
                    val roleLabel = when {
                        hasArtist -> "${site.displayName} 认证画师"
                        isUploader -> "${site.displayName} 上传者"
                        else -> "${site.displayName} 作者"
                    }
                    val buttonText = if (hasArtist) "搜画师" else "搜作品"
                    // 只有真画师标签才给关注入口：上传者不是画师，关注它会把名单记错人。
                    val following = hasArtist && followedArtists.any {
                        it.site == site && it.name.equals(creatorName, ignoreCase = true)
                    }
                    val toggleFollow = onToggleFollow
                    // yande.re 如果无画师tag且为上传者，需带 user: 前缀才能搜出作品
                    val searchWord = if (isUploader && site == GallerySite.YANDERE && !creatorName.startsWith("user:")) {
                        "user:$creatorName"
                    } else {
                        creatorName
                    }

                    DetailSectionBox(title = sectionTitle, icon = Icons.Outlined.AccountCircle) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(tokens.color.surfaceContainerHigh.copy(alpha = 0.6f))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(tokens.color.primary.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = creatorName.removePrefix("user:").take(1).uppercase(),
                                    fontSize = VeneraType.body.size, lineHeight = VeneraType.body.lineHeight,
                                    fontWeight = VeneraType.cardTitle.weight,
                                    color = tokens.color.primary,
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = creatorName.removePrefix("user:"),
                                        fontSize = VeneraType.bodySecondary.size, lineHeight = VeneraType.bodySecondary.lineHeight,
                                        fontWeight = VeneraType.cardTitle.weight,
                                        color = tokens.color.textPrimary,
                                    )
                                    if (!artistCn.isNullOrBlank() && artistCn != creatorName) {
                                        Text(
                                            text = "($artistCn)",
                                            fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                                            color = tokens.color.primary,
                                        )
                                    }
                                }
                                Text(
                                    text = roleLabel,
                                    fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                                    color = tokens.color.textTertiary,
                                )
                            }

                            // 关注入口：描边胶囊，与实底的「搜画师」分主次；亮灭只读名单流。
                            if (hasArtist && toggleFollow != null) {
                                Box(
                                    modifier = Modifier
                                        .clip(GalleryTokens.Shape.Large)
                                        .background(
                                            if (following) tokens.color.surfaceContainerHigh.copy(alpha = 0.55f)
                                            else Color.Transparent,
                                        )
                                        .border(1.dp, tokens.color.outline.copy(alpha = 0.45f), GalleryTokens.Shape.Large)
                                        .clickable {
                                            scope.launch { runCatching { toggleFollow(site, creatorName) } }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = desktopFollowLabel(following),
                                        fontSize = VeneraType.micro.size,
                                        lineHeight = VeneraType.micro.lineHeight,
                                        fontWeight = VeneraType.cardTitle.weight,
                                        color = if (following) tokens.color.textSecondary else tokens.color.primary,
                                    )
                                }
                            }

                            // ⭐️ 自适应胶囊动作按钮：彻底杜绝文字挤压与截断
                            Box(
                                modifier = Modifier
                                    .clip(GalleryTokens.Shape.Large)
                                    .background(tokens.color.primary)
                                    .clickable { onSearchTag(site, searchWord) }
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = buttonText,
                                    fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                                    fontWeight = VeneraType.cardTitle.weight,
                                    color = Color.White,
                                )
                            }
                        }
                    }
                }

                // 4. 来源出处与外链
                if (sourcePost != null && sourcePost.source.isNotBlank()) {
                    DetailSectionBox(title = "出处链接", icon = Icons.Outlined.Link) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(tokens.color.surfaceContainerHigh.copy(alpha = 0.45f))
                                .border(1.dp, tokens.color.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = sourcePost.source,
                                fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                                color = tokens.color.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = "复制链接",
                                tint = tokens.color.textTertiary,
                                modifier = Modifier
                                    .size(15.dp)
                                    .clickable {
                                        copyTextToClipboard(sourcePost.source)
                                        ux.toast("已复制出处链接")
                                    },
                            )
                        }
                    }
                }

                // 5. ⭐️ 严格五桶分类彩色标签（接入中英词典翻译与双语标签呈现）
                DetailSectionBox(title = "全量分类标签 (${tagList.size})", icon = Icons.Outlined.Tag) {
                    if (tagGroups.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.space2)) {
                            tagGroups.forEach { group ->
                                val (bucketColor, bucketName) = when (group.category) {
                                    4 -> Color(0xFF8AB4F8) to "角色 (CHARACTER)"
                                    3 -> Color(0xFFC3B4FF) to "作品版权 (COPYRIGHT)"
                                    1 -> Color(0xFFF2B87C) to "画师 (ARTIST)"
                                    5 -> Color(0xFFA8B8B6) to "元数据 (METADATA)"
                                    else -> com.venera.desktop.gallery.ui.GalleryTokens.Color.ToneSafe to "属性描写 (GENERAL)"
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = bucketName,
                                        fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                                        fontWeight = VeneraType.cardTitle.weight,
                                        color = bucketColor,
                                    )
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space1),
                                        verticalArrangement = Arrangement.spacedBy(tokens.spacing.space1),
                                    ) {
                                        group.tags.forEach { tag ->
                                            val cn = tagTranslations[tag.lowercase()]
                                            val tagDisplay = if (!cn.isNullOrBlank() && cn != tag) "$tag · $cn" else tag

                                            DesktopContextMenuArea(
                                                items = {
                                                    listOfNotNull(
                                                        DesktopMenuEntry.Item(
                                                            label = "搜索此标签",
                                                            icon = Icons.Outlined.Search,
                                                            onClick = { onSearchTag(site, tag) },
                                                        ),
                                                        DesktopMenuEntry.Item(
                                                            label = "复制英文原词",
                                                            shortcut = "Ctrl+C",
                                                            icon = Icons.Outlined.Description,
                                                            onClick = {
                                                                copyTextToClipboard(tag)
                                                                ux.toast("已复制标签: $tag")
                                                            },
                                                        ),
                                                        if (!cn.isNullOrBlank() && cn != tag) {
                                                            DesktopMenuEntry.Item(
                                                                label = "复制中文译名 ($cn)",
                                                                icon = Icons.Outlined.Translate,
                                                                onClick = {
                                                                    copyTextToClipboard(cn)
                                                                    ux.toast("已复制译名: $cn")
                                                                },
                                                            )
                                                        } else null,
                                                    )
                                                },
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(bucketColor.copy(alpha = 0.12f))
                                                        .border(1.dp, bucketColor.copy(alpha = 0.28f), RoundedCornerShape(6.dp))
                                                        .clickable { onSearchTag(site, tag) }
                                                        .padding(horizontal = 7.dp, vertical = 3.dp),
                                                ) {
                                                    Text(
                                                        text = tagDisplay,
                                                        fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                                                        color = bucketColor,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // 平铺展示
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            tagList.forEach { tag ->
                                val cn = tagTranslations[tag.lowercase()]
                                val tagDisplay = if (!cn.isNullOrBlank() && cn != tag) "$tag · $cn" else tag
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(tokens.color.surfaceContainerHigh)
                                        .clickable { onSearchTag(site, tag) }
                                        .padding(horizontal = 7.dp, vertical = 3.dp),
                                ) {
                                    Text(tagDisplay, fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight, color = tokens.color.textSecondary)
                                }
                            }
                        }
                    }
                }

                // 6. 原图下载按钮
                if (onSaveImage != null) {
                    DesktopPushButton(
                        onClick = {
                            scope.launch {
                                saving = true
                                val res = onSaveImage(post)
                                saving = false
                                saveMessage = res
                                ux.toast(res)
                            }
                        },
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth().height(38.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FileDownload,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (saving) "下载中…" else "下载原图", fontWeight = VeneraType.cardTitle.weight)
                    }
                }
            }
        }
        // ── 右缘信息栏开关：恒驻一枚玻璃圆钮，图标随展开态转半圈 ──
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 3.dp)
                .size(30.dp)
                .clip(CircleShape)
                .background(tokens.color.surfaceContainerHigh.copy(alpha = 0.85f))
                .border(0.5.dp, tokens.color.outlineVariant.copy(alpha = 0.5f), CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { paneShown = !paneShown },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = if (paneShown) "收起信息栏" else "展开信息栏",
                tint = tokens.color.textSecondary,
                modifier = Modifier
                    .size(18.dp)
                    .rotate(paneIconSpin),
            )
        }
        }
        }
    }
}

private fun copyTextToClipboard(text: String) {
    runCatching {
        val sel = java.awt.datatransfer.StringSelection(text)
        java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, null)
    }
}

@Composable
private fun DetailSectionBox(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit,
) {
    val tokens = VeneraTokens
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tokens.color.primary,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = title,
                fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                fontWeight = VeneraType.cardTitle.weight,
                color = tokens.color.textPrimary,
            )
        }
        content()
    }
}

/**
 * 玻璃批 token（稿 `docs/designs/macos-style-2026-11-07/detail-work-glass-2026-11-08/design-tokens.md`）。
 *
 * 详情页玻璃层次：画布环境光（作品图 blur60）之上的 chrome 半透层。VeneraTokens
 * 的深浅由壳层主题驱动 ⇒ 这里按 `isDark` 取档，浅档白系 / 深档 28,28,30 系，
 * 与 CSS 稿 `.cbar.glass / .info.glass / .gcard` 一一对应。
 * 画布内浮签（白 14% 透 + 0.5dp 白描边）直接写在用点（就两处，不抽）。
 */
internal object GdGlass {

    /** 命令带底：浅 rgba(250,250,252,.38) / 深 rgba(30,30,32,.42)。 */
    @Composable
    fun bar(): Color =
        if (LocalVeneraDarkTheme.current) Color(0xFF1E1E20).copy(alpha = 0.42f)
        else Color(0xFFFAFAFC).copy(alpha = 0.38f)

    /** 右信息栏底：浅 rgba(248,248,250,.58) / 深 rgba(28,28,30,.55)。 */
    @Composable
    fun panel(): Color =
        if (LocalVeneraDarkTheme.current) Color(0xFF1C1C1E).copy(alpha = 0.55f)
        else Color(0xFFF8F8FA).copy(alpha = 0.58f)

    /** 玻璃卡架：浅 rgba(255,255,255,.46) / 深 rgba(52,52,56,.46)。 */
    @Composable
    fun card(): Color =
        if (LocalVeneraDarkTheme.current) Color(0xFF343438).copy(alpha = 0.46f)
        else Color.White.copy(alpha = 0.46f)

    /** 站徽胶囊底色（稿 `.site`；色对账 gallery-pixiv-topbar 批站徽系）。 */
    fun siteColor(site: GallerySite): Color = when (site) {
        GallerySite.PIXIV -> Color(0xFF0096FA)
        GallerySite.GELBOORU -> Color(0xFF00B3B0)
        else -> Color(0xFFC51BCE)
    }
}
