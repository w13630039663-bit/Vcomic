package com.venera.desktop.reader

import com.venera.desktop.app.DesktopUxPlatformServices
import com.venera.desktop.ui.vShadow
import com.venera.desktop.ui.theme.VeneraType
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import coil3.toBitmap
import org.jetbrains.skiko.toBufferedImage
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.gallery.data.DesktopComicPage
import com.venera.desktop.gallery.data.DesktopComicTile

/**
 * 导览视图渲染视口：基于本地图像留白沟壑检测分格，支持平滑运镜、首尾整页与遮罩挖空。
 *
 * 双页模式下 [spreadPages] 是整组两页（左、右按渲染序），分格在跨页坐标里统一排队，
 * 视口只当它是"一张宽图"—— 运镜与挖空的几何因此不必分单双页两套。
 */
@Composable
internal fun DesktopGuidedViewViewport(
    pages: List<DesktopComicPage>,
    currentPage: Int,
    spreadPages: List<Int>,
    spreadRatios: List<Float>,
    rightToLeft: Boolean,
    style: Int,
    maskStyle: Int,
    paddingPx: Float,
    maxZoom: Float,
    showFullFirst: Boolean,
    showFullLast: Boolean,
    animSeconds: Float,
    panelIndex: Int,
    onStepCountChange: (Int) -> Unit,
    onPanelStep: (Int) -> Unit,
    onExitFocus: () -> Unit,
    focused: Boolean,
    fit: ReaderFit,
    colorFilter: androidx.compose.ui.graphics.ColorFilter?,
    filterQuality: androidx.compose.ui.graphics.FilterQuality,
    aiParamsFor: (DesktopComicPage?) -> DesktopAiPage?,
    onImageSize: (Int, Int, Int) -> Unit,
    reloadTick: Int,
    onPageChange: (Int) -> Unit,
    onToggleHud: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 桌面侧 `DesktopUxPlatformServices` 是 `by NoOpUxPlatformServices` 委托且没覆盖 `imageLoader()`，
    // 拿到的恒为 null；不兜到 Coil 进程单例，下面的 null 早退会让检测一行都跑不到，
    // 每一页都退回固定三分格 —— 屏上表现为"导览视图永远是三条横带"。
    val platformImageLoader = com.venera.compose.components.LocalUxPlatformServices.current.imageLoader()
    val singletonImageLoader = remember {
        runCatching { coil3.SingletonImageLoader.get(coil3.PlatformContext.INSTANCE) }.getOrNull()
    }
    val guidedImageLoader = platformImageLoader ?: singletonImageLoader
    val detectSide = DesktopGuidedViewEngine.DETECT_SIDE
    // 键带 reloadTick：AI 参数一变页面像素就变了，旧的分格结果必须一起作废。
    val detectedCache = remember(pages, rightToLeft, reloadTick) { mutableStateMapOf<Int, List<ComicPanelRect>>() }

    LaunchedEffect(spreadPages, rightToLeft, reloadTick, guidedImageLoader) {
        val loader = guidedImageLoader ?: return@LaunchedEffect
        for (idx in spreadPages) {
            if (detectedCache.containsKey(idx)) continue
            val pageObj = pages.getOrNull(idx) ?: continue
            val detected = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                runCatching {
                    val req = coil3.request.ImageRequest.Builder(coil3.PlatformContext.INSTANCE)
                        .data(aiParamsFor(pageObj) ?: pageObj)
                        .size(detectSide, detectSide)
                        .build()
                    val res = loader.execute(req)
                    val img = (res as? coil3.request.SuccessResult)?.image ?: return@runCatching null
                    val bmp = img.toBitmap().toBufferedImage()
                    DesktopGuidedViewEngine.detectPanels(bmp, rightToLeft)
                }.getOrNull()
            }
            if (detected != null) {
                detectedCache[idx] = detected
            }
        }
    }

    val spreadRatio = spreadRatios.sum().takeIf { it > 0.05f } ?: TRIPLE_RATIO_FALLBACK
    // 跨页坐标：每页自己的分格按它占的宽度平移缩放进来，之后全程只认这一套坐标。
    val pagePanels = spreadPages.mapIndexed { i, idx ->
        detectedCache[idx]
            ?: DesktopGuidedViewEngine.fallbackTriPanels(
                spreadRatios.getOrElse(i) { TRIPLE_RATIO_FALLBACK },
                rightToLeft,
            )
    }
    val rawPanels = DesktopGuidedViewEngine.composeSpreadPanels(pagePanels, spreadRatios)
    val steps = remember(rawPanels, showFullFirst, showFullLast) {
        DesktopGuidedViewEngine.buildGuidedSteps(rawPanels, showFullFirst, showFullLast)
    }
    LaunchedEffect(steps.size) {
        onStepCountChange(steps.size)
    }

    val mask = remember(style, maskStyle) { DesktopGuidedViewEngine.maskColorFor(style, maskStyle) }
    val animMs = (animSeconds * 1000f).toInt().coerceIn(80, 2000)

    BoxWithConstraints(
        modifier = modifier
            .pointerInput(focused, panelIndex, steps.size, rightToLeft) {
                detectTapGestures(
                    onTap = { offset ->
                        if (!focused) {
                            onToggleHud()
                            return@detectTapGestures
                        }
                        val ratioX = offset.x / size.width.coerceAtLeast(1)
                        if (ratioX in 0.35f..0.65f) {
                            onToggleHud()
                            return@detectTapGestures
                        }
                        val forward = if (rightToLeft) ratioX < 0.5f else ratioX > 0.5f
                        val next = panelIndex + if (forward) 1 else -1
                        if (next in steps.indices) {
                            onPanelStep(if (forward) 1 else -1)
                        } else {
                            // 整组跨页是一屏：翻页目标要落在组外，否则跳回组内同一屏等于没反应。
                            val target = if (forward) {
                                (spreadPages.maxOrNull() ?: currentPage) + 1
                            } else {
                                (spreadPages.minOrNull() ?: currentPage) - 1
                            }
                            onPageChange(target)
                        }
                    },
                    onDoubleTap = { if (focused) onExitFocus() },
                )
            },
    ) {
        val vpW = constraints.maxWidth.toFloat()
        val vpH = constraints.maxHeight.toFloat()
        val vpMaxWidth = maxWidth
        val vpMaxHeight = maxHeight
        val hasSpread = spreadPages.size > 1
        val activePanel = if (focused && spreadPages.isNotEmpty()) {
            steps[panelIndex.coerceIn(steps.indices)]
        } else {
            ComicPanelRect(0f, 0f, 1f, 1f)
        }

        val tx = remember(activePanel, vpW, vpH, spreadRatio, paddingPx, maxZoom, focused, hasSpread) {
            if (!focused || spreadPages.isEmpty()) {
                ReaderViewportTransform(1f, Offset.Zero)
            } else {
                DesktopGuidedViewEngine.computePanelFocusTransform(
                    panel = activePanel,
                    viewportWidth = vpW,
                    viewportHeight = vpH,
                    imageRatio = spreadRatio,
                    paddingPx = paddingPx,
                    maxZoom = maxZoom,
                )
            }
        }

        val animZoom by androidx.compose.animation.core.animateFloatAsState(
            targetValue = tx.zoom,
            animationSpec = tween(animMs, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        )
        val animPanX by androidx.compose.animation.core.animateFloatAsState(
            targetValue = tx.pan.x,
            animationSpec = tween(animMs, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        )
        val animPanY by androidx.compose.animation.core.animateFloatAsState(
            targetValue = tx.pan.y,
            animationSpec = tween(animMs, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        )

        // 计算图片在未缩放视口内的实际矩形，用于遮罩精确挖空当前分格
        val safeRatio = spreadRatio.coerceIn(0.2f, 5f)
        val vpRatio = if (vpH > 0f) vpW / vpH else 1f
        val imgW = if (safeRatio > vpRatio) vpW else vpH * safeRatio
        val imgH = if (safeRatio > vpRatio) vpW / safeRatio else vpH
        val imgLeft = (vpW - imgW) / 2f
        val imgTop = (vpH - imgH) / 2f

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = animZoom
                    scaleY = animZoom
                    translationX = animPanX
                    translationY = animPanY
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                },
        ) {
            if (!hasSpread) {
                val idx = spreadPages.firstOrNull() ?: currentPage
                ReaderPagedCell(
                    page = pages.getOrNull(idx),
                    index = idx,
                    fit = fit,
                    zoomTarget = animZoom,
                    markInput = {},
                    colorFilter = colorFilter,
                    filterQuality = filterQuality,
                    aiParams = aiParamsFor(pages.getOrNull(idx)),
                    reloadTick = reloadTick,
                    onImageSize = { w, h -> onImageSize(idx, w, h) },
                    modifier = Modifier.fillMaxSize(),
                    fillCell = true,
                )
            } else {
                // 整组按两页宽高比贴合后居中，和上面"整组当一张宽图"的运镜几何一致；
                // 各占半宽会让竖长页面在自己的格子里左右留白，两页之间看起来隔着一道沟。
                val groupHeight = run {
                    val natural = vpMaxHeight * spreadRatio
                    if (natural > vpMaxWidth && natural > 0.dp) vpMaxHeight * (vpMaxWidth / natural) else vpMaxHeight
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .height(groupHeight)
                        .width(groupHeight * spreadRatio),
                ) {
                    spreadPages.forEachIndexed { i, idx ->
                        ReaderPagedCell(
                            page = pages.getOrNull(idx),
                            index = idx,
                            fit = fit,
                            zoomTarget = animZoom,
                            markInput = {},
                            colorFilter = colorFilter,
                            filterQuality = filterQuality,
                            aiParams = aiParamsFor(pages.getOrNull(idx)),
                            reloadTick = reloadTick,
                            onImageSize = { w, h -> onImageSize(idx, w, h) },
                            modifier = Modifier.weight(spreadRatios.getOrElse(i) { TRIPLE_RATIO_FALLBACK }),
                        )
                    }
                }
            }

            // 隐藏/沉浸式遮罩：使用 Offscreen + DstOut 精确挖空当前分格
            if (focused && mask != null && !activePanel.isFullPage) {
                val holeLeft = imgLeft + activePanel.left * imgW
                val holeTop = imgTop + activePanel.top * imgH
                val holeW = activePanel.width * imgW
                val holeH = activePanel.height * imgH
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                        }
                        .drawWithContent {
                            drawRect(color = mask)
                            drawRect(
                                color = Color.Black,
                                topLeft = Offset(holeLeft, holeTop),
                                size = androidx.compose.ui.geometry.Size(holeW, holeH),
                                blendMode = androidx.compose.ui.graphics.BlendMode.DstOut,
                            )
                        },
                )
            }
        }
    }
}

/**
 * 整页翻的一格（单页 = 一格一页；对开 = 一格两页并排）。
 *
 * 缩放/平移用 graphicsLayer 画在内容上（`clipToBounds` 收边）；放大后的拖拽平移在
 * **Initial pass** 消费 —— 抢在子层 verticalScroll 之前，缩着的时候滚列表没有意义。
 *
 * ## 超大图分块加载（阅读器重做批·二轮）
 *
 * 放大过 1.6× 且首帧量到了原图尺寸后，整格切成 **2×2 高清瓦片**（每瓦 = 原图四分之一，
 * `DesktopComicTile` → fetcher 里 ImageIO 解码一次、缓存解码结果、按瓦裁切）——
 * 低清底图（Coil 按 fit 尺寸降采样那张）垫底，瓦片盖上来，放大不糊；
 * 未放大时只渲染底图，一张瓦片都不发。原图像素没到手前（第一帧）也只走底图。
 */
@Composable
internal fun ReaderPagedCell(
    page: DesktopComicPage?,
    index: Int,
    fit: ReaderFit,
    zoomTarget: Float,
    markInput: () -> Unit,
    modifier: Modifier = Modifier,
    fillCell: Boolean = false,
    colorFilter: androidx.compose.ui.graphics.ColorFilter? = null,
    reloadTick: Int = 0,
    onImageSize: ((Int, Int) -> Unit)? = null,
    filterQuality: androidx.compose.ui.graphics.FilterQuality? = null,
    /** AI 前置管线参数：非空时页面模型换成 [DesktopAiPage]（渲染前过本地算法链）。 */
    aiParams: DesktopAiPage? = null,
) {
    if (page == null) {
        Box(modifier)
        return
    }
    val inner: Modifier = when (fit) {
        ReaderFit.FIT_WIDTH -> Modifier.fillMaxWidth()
        ReaderFit.FIT_SCREEN -> if (fillCell) Modifier.fillMaxSize() else Modifier.fillMaxHeight()
        ReaderFit.ORIGINAL -> Modifier.wrapContentSize()
    }
    val scale: ContentScale = when (fit) {
        ReaderFit.FIT_WIDTH -> ContentScale.FillWidth
        ReaderFit.FIT_SCREEN -> ContentScale.Fit
        ReaderFit.ORIGINAL -> ContentScale.None
    }
    val scroll = if (fit == ReaderFit.FIT_WIDTH) Modifier.verticalScroll(rememberScrollState()) else Modifier
    Box(
        modifier = modifier
            .clipToBounds()
            .then(if (fit == ReaderFit.FIT_WIDTH) Modifier.fillMaxSize() else Modifier)
            .then(scroll),
        contentAlignment = Alignment.Center,
    ) {
            Box(
                modifier = Modifier.then(inner),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.runtime.key(reloadTick) {
                    AsyncImage(
                        // AI 前置管线：任一开关开时模型换 DesktopAiPage（键含参数指纹，
                        // Coil 内存缓存承担页级缓存；fetcher 内跑 IO 线程）。
                        model = aiParams ?: page,
                        contentDescription = com.venera.desktop.settings.DesktopI18n.t("第 ${index + 1} 页", "Page ${index + 1}"),
                        modifier = Modifier.then(inner),
                        contentScale = scale,
                        colorFilter = colorFilter,
                        // 显式插值质量（prefs.filterQualityIndex 映射）
                        filterQuality = filterQuality ?: androidx.compose.ui.graphics.FilterQuality.Medium,
                        onSuccess = { state ->
                            val img = state.result.image
                            if (img.width > 0 && img.height > 0) {
                                onImageSize?.invoke(img.width, img.height)
                            }
                        },
                    )
                }
            }
    }
}

/** 工具轨上的一枚图标钮（38dp，深色底+边框，悬停高亮）。 */
@Composable
internal fun RailIcon(glyph: String, highlight: Boolean = false, onClick: () -> Unit) {
    val tokens = VeneraTokens
    Box(
        modifier = Modifier
            .size(38.dp)
            .background(
                if (highlight) tokens.color.primary.copy(alpha = 0.35f) else Color(0x22FFFFFF),
                RoundedCornerShape(10.dp),
            )
            .border(
                1.dp,
                if (highlight) tokens.color.primary else Color(0x1AFFFFFF),
                RoundedCornerShape(10.dp),
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            fontSize = androidx.compose.ui.unit.TextUnit(16f, androidx.compose.ui.unit.TextUnitType.Sp),
            fontWeight = VeneraType.cardTitle.weight,
            color = if (highlight) tokens.color.primary else Color.White,
        )
    }
}

/** 热区（左右 26% 那两片）：仅点击生效，暗色半透微显。 */
@Composable
internal fun HotZone(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .background(Color(0x0D000000), RoundedCornerShape(10.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, fontSize = androidx.compose.ui.unit.TextUnit(12f, androidx.compose.ui.unit.TextUnitType.Sp), color = Color(0x55FFFFFF))
    }
}

internal val READER_STRIP_WIDTH = 72.dp
internal val READER_PANEL_WIDTH = 300.dp
internal val READER_FLOAT_EDGE = 16.dp
internal val READER_FLOAT_TOP = 76.dp
internal val READER_FLOAT_BOTTOM = 112.dp
internal val READER_FLOAT_RADIUS = 16.dp
internal val READER_THUMB_WIDTH = 56.dp
internal val READER_THUMB_HEIGHT = 76.dp
internal val READER_FLOAT_BG = Color(red = 30, green = 28, blue = 35, alpha = 199)
internal val READER_FLOAT_BORDER = Color(red = 255, green = 255, blue = 255, alpha = 36)

/**
 * 两个悬浮侧栏：左条是页缩略图，右板复用现有设置抽屉。
 *
 * 浮层不占布局宽，显隐只改画布内容的安全区，不重排根 Box。退场比入场短；
 * 用 AnimatedVisibility 让隐藏态真正退出组合，避免隐形层继续参与命中与几何。
 */
@Composable
internal fun ReaderSidePanels(
    pages: List<DesktopComicPage>,
    currentPage: Int,
    masked: Boolean,
    stripVisible: Boolean,
    panelVisible: Boolean,
    onJump: (Int) -> Unit,
    stripAlign: Modifier,
    panelAlign: Modifier,
    settings: @Composable (Modifier) -> Unit,
) {
    val shape = RoundedCornerShape(READER_FLOAT_RADIUS)
    val glass = Modifier
        .vShadow(12.dp, shape)
        .background(READER_FLOAT_BG, shape)
        .border(1.dp, READER_FLOAT_BORDER, shape)

    AnimatedVisibility(
        visible = stripVisible,
        enter = fadeIn(tween(220)) + slideInHorizontally(tween(220)) { -it / 8 },
        exit = fadeOut(tween(150)) + slideOutHorizontally(tween(150)) { -it / 8 },
        modifier = stripAlign
            .padding(start = READER_FLOAT_EDGE, top = READER_FLOAT_TOP, bottom = READER_FLOAT_BOTTOM),
    ) {
        ReaderPageStrip(
            pages = pages,
            currentPage = currentPage,
            masked = masked,
            onJump = onJump,
            modifier = Modifier.width(READER_STRIP_WIDTH).fillMaxHeight().then(glass),
        )
    }

    AnimatedVisibility(
        visible = panelVisible,
        enter = fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 8 },
        exit = fadeOut(tween(150)) + slideOutHorizontally(tween(150)) { it / 8 },
        modifier = panelAlign
            .padding(end = READER_FLOAT_EDGE, top = READER_FLOAT_TOP, bottom = READER_FLOAT_BOTTOM),
    ) {
        Box(modifier = Modifier.width(READER_PANEL_WIDTH).fillMaxHeight().then(glass)) {
            settings(Modifier.fillMaxSize())
        }
    }
}

/** 页缩略图条：与画布同一份 [pages]，点项走现有 [onJump]，当前页自动滚到可见。 */
@Composable
internal fun ReaderPageStrip(
    pages: List<DesktopComicPage>,
    currentPage: Int,
    masked: Boolean,
    onJump: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    val stripState = rememberLazyListState()
    LaunchedEffect(currentPage, pages.size) {
        if (pages.isNotEmpty()) stripState.animateScrollToItem(currentPage.coerceIn(0, pages.lastIndex))
    }
    Column(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0x12FFFFFF))
                .border(1.dp, Color(0x12FFFFFF))
                .padding(horizontal = 8.dp, vertical = 12.dp),
        ) {
            Text(
                text = com.venera.desktop.settings.DesktopI18n.t("页", "Page"),
                fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                color = Color(0xFF8A8692),
            )
            Text(
                text = if (pages.isEmpty()) "0 / 0" else "${currentPage + 1} / ${pages.size}",
                fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                fontWeight = VeneraType.control.weight,
                color = Color(0xFFC4C0CA),
            )
        }
        LazyColumn(
            state = stripState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
        ) {
            items(pages.size) { i ->
                val selected = i == currentPage
                Box(
                    modifier = Modifier
                        .width(READER_THUMB_WIDTH)
                        .height(READER_THUMB_HEIGHT)
                        .clipToBounds()
                        .background(Color(0xFF2A2A31), RoundedCornerShape(6.dp))
                        .then(
                            if (selected) Modifier.border(2.dp, tokens.color.primary, RoundedCornerShape(6.dp))
                            else Modifier.border(1.dp, Color(0x12FFFFFF), RoundedCornerShape(6.dp)),
                        )
                        .clickable { onJump(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = pages[i],
                        contentDescription = com.venera.desktop.settings.DesktopI18n.t("第 ${i + 1} 页", "Page ${i + 1}"),
                        modifier = Modifier.fillMaxSize().then(if (masked) Modifier.blur(12.dp) else Modifier),
                        contentScale = ContentScale.Crop,
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(4.dp)
                            .background(
                                if (selected) tokens.color.primary else Color(0x9E000000),
                                RoundedCornerShape(4.dp),
                            )
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    ) {
                        Text(
                            text = "${i + 1}",
                            fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                            color = if (selected) Color.White else Color(0xFFF2F0F5),
                        )
                    }
                }
            }
        }
    }
}
