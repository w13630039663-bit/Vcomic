package com.venera.desktop.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerScope
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Dimension
import coil3.toBitmap
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.gallery.data.DesktopComicPage
import com.venera.desktop.gallery.data.DesktopReaderMode
import com.venera.desktop.gallery.ui.desktopReaderReverses
import com.venera.desktop.settings.DesktopI18n
import com.venera.desktop.ui.components.DesktopContextMenuArea
import com.venera.desktop.ui.components.DesktopMenuEntry
import com.venera.desktop.ui.theme.VeneraType
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JFileChooser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.skiko.toBufferedImage

/**
 * 分页类阅读器主视口（包含 3D 硬翻、分格导览视图、常规 HorizontalPager 单/双页及中缝阴影）。
 * 同时返回给放大镜使用的同步镜像视口 lambda。
 */
@Composable
internal fun ReaderPagedViewport(
    pages: List<DesktopComicPage>,
    currentPage: Int,
    currentPageNow: Int,
    readerMode: DesktopReaderMode,
    prefs: DesktopReaderPrefsState,
    fit: ReaderFit,
    isDouble: Boolean,
    doubleGroups: List<List<Int>>,
    pageRatios: MutableMap<Int, Float>,
    guidedSpreadPages: List<Int>,
    guidedSpreadRatios: List<Float>,
    guidedPanelIndex: Int,
    guidedViewFocused: Boolean,
    onGuidedStepCountChange: (Int) -> Unit,
    onGuidedPanelStep: (Int) -> Unit,
    onGuidedExitFocus: () -> Unit,
    pagerState: PagerState,
    zoom: Float,
    zoomTarget: Float,
    pan: Offset,
    onPanDelta: (Offset, Float, Float) -> Unit,
    rotationDegrees: Int,
    canvasBg: Color,
    masked: Boolean,
    activeColorFilter: ColorFilter?,
    cellFilterQuality: FilterQuality,
    tapZoneNow: Float,
    reloadTick: Int,
    onReloadTickIncrement: () -> Unit,
    readerImageLoader: ImageLoader?,
    scope: CoroutineScope,
    onRecordPageMetrics: (Int, Int, Int) -> Unit,
    onHorizontalRotation: (Int) -> Float,
    onJumpToLogical: (Int) -> Unit,
    onToggleHud: () -> Unit,
    onViewportSize: (IntSize) -> Unit,
    onViewportOrigin: (Offset) -> Unit,
    onProvideLensViewport: (@Composable () -> Unit) -> Unit,
) {
    val lensRtl = desktopReaderReverses(readerMode)

    DesktopContextMenuArea(
        items = {
            listOf(
                DesktopMenuEntry.Item(
                    label = DesktopI18n.tr("保存当前页到本地", "Save current page"),
                    onClick = {
                        val targetPage = pages.getOrNull(currentPage) ?: return@Item
                        scope.launch(Dispatchers.IO) {
                            saveReaderPageToFile(readerImageLoader, targetPage, currentPage)
                        }
                    },
                ),
                DesktopMenuEntry.Item(
                    label = DesktopI18n.tr("复制当前页图片", "Copy current page image"),
                    onClick = {
                        val targetPage = pages.getOrNull(currentPage) ?: return@Item
                        scope.launch(Dispatchers.IO) {
                            copyReaderPageToClipboard(readerImageLoader, targetPage)
                        }
                    },
                ),
                DesktopMenuEntry.Item(
                    label = DesktopI18n.tr("重新加载当前页", "Reload current page"),
                    onClick = onReloadTickIncrement,
                ),
            )
        },
        modifier = Modifier
            .fillMaxSize()
            .padding(start = LEFT_STRIP_WIDTH)
            .padding(horizontal = prefs.marginH.dp)
            .onSizeChanged(onViewportSize)
            .onGloballyPositioned { coords: LayoutCoordinates -> onViewportOrigin(coords.positionInRoot()) },
    ) {
        when (prefs.turnEffect) {
            4 -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .readerZoomDragPan(zoomTarget, onPanDelta)
                        .clipToBounds()
                        .graphicsLayer {
                            scaleX = zoom
                            scaleY = zoom
                            translationX = pan.x
                            translationY = pan.y
                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                        },
                ) {
                    DesktopBookTurnViewport(
                        pages = pages,
                        currentPage = currentPage,
                        isDoublePage = readerMode == DesktopReaderMode.DOUBLE_PAGE,
                        doubleGroups = doubleGroups,
                        rightToLeft = lensRtl,
                        perspective = prefs.turnPerspective,
                        animSeconds = prefs.turnAnimSeconds,
                        tapZoneRatio = tapZoneNow,
                        shadowOn = prefs.shadowOn,
                        shadowSize = prefs.shadowSize,
                        shadowOpacity = prefs.shadowOpacity,
                        shadowDisplace = prefs.shadowDisplace,
                        fit = fit,
                        zoom = zoom,
                        colorFilter = activeColorFilter,
                        filterQuality = cellFilterQuality,
                        rotationDegrees = rotationDegrees,
                        aiParamsFor = { buildReaderAiPage(it, prefs) },
                        onImageSize = onRecordPageMetrics,
                        reloadTick = reloadTick,
                        onPageChange = onJumpToLogical,
                        onToggleHud = onToggleHud,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                onProvideLensViewport {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = zoom
                                scaleY = zoom
                                translationX = pan.x
                                translationY = pan.y
                                transformOrigin = TransformOrigin(0.5f, 0.5f)
                            },
                    ) {
                        DesktopBookTurnViewport(
                            pages = pages,
                            currentPage = currentPageNow,
                            isDoublePage = isDouble,
                            doubleGroups = doubleGroups,
                            rightToLeft = lensRtl,
                            perspective = prefs.turnPerspective,
                            animSeconds = prefs.turnAnimSeconds,
                            tapZoneRatio = 0f,
                            shadowOn = prefs.shadowOn,
                            shadowSize = prefs.shadowSize,
                            shadowOpacity = prefs.shadowOpacity,
                            shadowDisplace = prefs.shadowDisplace,
                            fit = fit,
                            zoom = 1f,
                            colorFilter = activeColorFilter,
                            filterQuality = cellFilterQuality,
                            rotationDegrees = rotationDegrees,
                            aiParamsFor = { buildReaderAiPage(it, prefs) },
                            onImageSize = { _, _, _ -> },
                            reloadTick = reloadTick,
                            onPageChange = {},
                            onToggleHud = {},
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
            2 -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .readerZoomDragPan(zoomTarget, onPanDelta)
                        .clipToBounds()
                        .graphicsLayer {
                            scaleX = zoom
                            scaleY = zoom
                            translationX = pan.x
                            translationY = pan.y
                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                        },
                ) {
                    DesktopGuidedViewViewport(
                        pages = pages,
                        currentPage = currentPage,
                        spreadPages = guidedSpreadPages,
                        spreadRatios = guidedSpreadRatios,
                        rightToLeft = lensRtl,
                        style = prefs.guidedViewStyle,
                        maskStyle = prefs.guidedViewMaskStyle,
                        paddingPx = prefs.guidedViewPadding.toFloat(),
                        maxZoom = prefs.guidedViewMaxZoom,
                        showFullFirst = prefs.guidedViewShowFullFirst,
                        showFullLast = prefs.guidedViewShowFullLast,
                        animSeconds = prefs.turnAnimSeconds,
                        panelIndex = guidedPanelIndex,
                        onStepCountChange = onGuidedStepCountChange,
                        onPanelStep = onGuidedPanelStep,
                        onExitFocus = onGuidedExitFocus,
                        focused = guidedViewFocused,
                        fit = fit,
                        colorFilter = activeColorFilter,
                        filterQuality = cellFilterQuality,
                        aiParamsFor = { buildReaderAiPage(it, prefs) },
                        onImageSize = onRecordPageMetrics,
                        reloadTick = reloadTick,
                        onPageChange = onJumpToLogical,
                        onToggleHud = onToggleHud,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                onProvideLensViewport {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = zoom
                                scaleY = zoom
                                translationX = pan.x
                                translationY = pan.y
                                transformOrigin = TransformOrigin(0.5f, 0.5f)
                            },
                    ) {
                        DesktopGuidedViewViewport(
                            pages = pages,
                            currentPage = currentPageNow,
                            spreadPages = guidedSpreadPages,
                            spreadRatios = guidedSpreadRatios,
                            rightToLeft = lensRtl,
                            style = prefs.guidedViewStyle,
                            maskStyle = prefs.guidedViewMaskStyle,
                            paddingPx = prefs.guidedViewPadding.toFloat(),
                            maxZoom = prefs.guidedViewMaxZoom,
                            showFullFirst = prefs.guidedViewShowFullFirst,
                            showFullLast = prefs.guidedViewShowFullLast,
                            animSeconds = prefs.turnAnimSeconds,
                            panelIndex = guidedPanelIndex,
                            onStepCountChange = {},
                            onPanelStep = {},
                            onExitFocus = {},
                            focused = guidedViewFocused,
                            fit = fit,
                            colorFilter = activeColorFilter,
                            filterQuality = cellFilterQuality,
                            aiParamsFor = { buildReaderAiPage(it, prefs) },
                            onImageSize = { _, _, _ -> },
                            reloadTick = reloadTick,
                            onPageChange = {},
                            onToggleHud = {},
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
            else -> {
                val pagerPageContent: @Composable PagerScope.(Int) -> Unit = { group ->
                    val turnEffect = prefs.turnEffect
                    val turnSign = if (lensRtl) -1f else 1f
                    val cellModifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            if (turnEffect != 0) {
                                val off = ((pagerState.currentPage - pagerState.currentPageOffsetFraction) - group)
                                    .coerceIn(-1f, 1f)
                                val near = 1f - kotlin.math.abs(off)
                                when (turnEffect) {
                                    3 -> alpha = (1f - kotlin.math.abs(off)).coerceIn(0f, 1f)
                                    4 -> {
                                        val away = kotlin.math.abs(off)
                                        cameraDistance = prefs.turnPerspective.toFloat() * density
                                        rotationY = (-off * 80f * turnSign).coerceIn(-85f, 85f)
                                        transformOrigin = if (turnSign * off > 0) {
                                            TransformOrigin(0f, 0.5f)
                                        } else {
                                            TransformOrigin(1f, 0.5f)
                                        }
                                        alpha = 1f - 0.2f * away
                                    }
                                    else -> alpha = 0.5f + 0.5f * near
                                }
                            }
                        }
                        .drawWithContent {
                            drawContent()
                            if (turnEffect == 4) {
                                val off = ((pagerState.currentPage - pagerState.currentPageOffsetFraction) - group)
                                    .coerceIn(-1f, 1f)
                                val weight = (kotlin.math.abs(off) * 0.85f).coerceIn(0f, 1f)
                                if (weight > 0.01f) {
                                    val w = 96.dp.toPx()
                                    val edge = Color.Black
                                    drawRect(
                                        brush = Brush.horizontalGradient(
                                            listOf(edge.copy(alpha = weight), Color.Transparent),
                                            0f,
                                            w,
                                        ),
                                        size = Size(w, size.height),
                                    )
                                    drawRect(
                                        brush = Brush.horizontalGradient(
                                            listOf(Color.Transparent, edge.copy(alpha = weight)),
                                            size.width - w,
                                            size.width,
                                        ),
                                        topLeft = Offset(size.width - w, 0f),
                                        size = Size(w, size.height),
                                    )
                                }
                            }
                        }
                        .clipToBounds()
                        .then(if (masked) Modifier.blur(24.dp) else Modifier)

                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .readerZoomDragPan(zoomTarget, onPanDelta)
                            .clipToBounds(),
                    ) {
                        val quarterTurn = rotationDegrees % 180 != 0
                        val rotFit = if (quarterTurn && maxWidth > 0.dp && maxHeight > 0.dp) {
                            minOf(maxWidth / maxHeight, maxHeight / maxWidth)
                        } else {
                            1f
                        }
                        val groupRotH = if (isDouble) {
                            doubleGroups.getOrNull(group).orEmpty()
                                .firstOrNull { it >= 0 && (pageRatios[it] ?: 0f) > 1f }
                                ?.let { onHorizontalRotation(it) } ?: 0f
                        } else {
                            onHorizontalRotation(group)
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = zoom * rotFit
                                    scaleY = zoom * rotFit
                                    rotationZ = rotationDegrees.toFloat() + groupRotH
                                    translationX = pan.x
                                    translationY = pan.y
                                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                                },
                        ) {
                            if (isDouble) {
                                val groupPages = doubleGroups.getOrNull(group).orEmpty()
                                if (groupPages.size == 1) {
                                    val idx = groupPages[0]
                                    ReaderPagedCell(
                                        page = pages.getOrNull(idx),
                                        index = idx,
                                        fit = fit,
                                        zoomTarget = zoomTarget,
                                        markInput = {},
                                        colorFilter = activeColorFilter,
                                        filterQuality = cellFilterQuality,
                                        aiParams = buildReaderAiPage(pages.getOrNull(idx), prefs),
                                        reloadTick = reloadTick,
                                        onImageSize = { w, h -> onRecordPageMetrics(idx, w, h) },
                                        modifier = cellModifier,
                                        fillCell = true,
                                    )
                                } else {
                                    DoubleSpreadGroupCell(
                                        pages = pages,
                                        groupPages = groupPages,
                                        pageRatios = pageRatios,
                                        prefs = prefs,
                                        fit = fit,
                                        zoomTarget = zoomTarget,
                                        canvasBg = canvasBg,
                                        cellModifier = cellModifier,
                                        activeColorFilter = activeColorFilter,
                                        cellFilterQuality = cellFilterQuality,
                                        reloadTick = reloadTick,
                                        onRecordPageMetrics = onRecordPageMetrics,
                                    )
                                }
                            } else {
                                ReaderPagedCell(
                                    page = pages.getOrNull(group),
                                    index = group,
                                    fit = fit,
                                    zoomTarget = zoomTarget,
                                    markInput = {},
                                    colorFilter = activeColorFilter,
                                    filterQuality = cellFilterQuality,
                                    aiParams = buildReaderAiPage(pages.getOrNull(group), prefs),
                                    reloadTick = reloadTick,
                                    onImageSize = { w, h -> onRecordPageMetrics(group, w, h) },
                                    modifier = cellModifier,
                                    fillCell = true,
                                )
                            }
                        }
                    }
                }
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    reverseLayout = lensRtl,
                    userScrollEnabled = zoomTarget <= 1.01f,
                    beyondViewportPageCount = 1,
                    key = { it },
                    pageContent = pagerPageContent,
                )
                onProvideLensViewport {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        reverseLayout = lensRtl,
                        userScrollEnabled = false,
                        beyondViewportPageCount = 1,
                        key = { it },
                        pageContent = pagerPageContent,
                    )
                }
            }
        }
    }
}

@Composable
private fun DoubleSpreadGroupCell(
    pages: List<DesktopComicPage>,
    groupPages: List<Int>,
    pageRatios: MutableMap<Int, Float>,
    prefs: DesktopReaderPrefsState,
    fit: ReaderFit,
    zoomTarget: Float,
    canvasBg: Color,
    cellModifier: Modifier,
    activeColorFilter: ColorFilter?,
    cellFilterQuality: FilterQuality,
    reloadTick: Int,
    onRecordPageMetrics: (Int, Int, Int) -> Unit,
) {
    val l0 = groupPages.getOrNull(0) ?: BLANK_PAGE
    val l1 = groupPages.getOrNull(1) ?: BLANK_PAGE
    val tightFit = fit == ReaderFit.FIT_SCREEN
    val r0 = if (l0 >= 0) pageRatios[l0] ?: TRIPLE_RATIO_FALLBACK else TRIPLE_RATIO_FALLBACK
    val r1 = if (l1 >= 0) pageRatios[l1] ?: TRIPLE_RATIO_FALLBACK else TRIPLE_RATIO_FALLBACK

    Box(modifier = cellModifier.background(canvasBg)) {
        if (tightFit) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val naturalWidth = maxHeight * (r0 + r1)
                val groupHeight = if (naturalWidth > maxWidth && naturalWidth > 0.dp) {
                    maxHeight * (maxWidth / naturalWidth)
                } else {
                    maxHeight
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .height(groupHeight)
                        .width(groupHeight * (r0 + r1)),
                ) {
                    ReaderPagedCell(
                        page = pages.getOrNull(l0), index = l0, fit = fit,
                        zoomTarget = zoomTarget, markInput = {},
                        colorFilter = activeColorFilter, filterQuality = cellFilterQuality,
                        aiParams = buildReaderAiPage(pages.getOrNull(l0), prefs), reloadTick = reloadTick,
                        onImageSize = { w, h -> onRecordPageMetrics(l0, w, h) },
                        modifier = Modifier.weight(r0),
                    )
                    ReaderPagedCell(
                        page = pages.getOrNull(l1), index = l1, fit = fit,
                        zoomTarget = zoomTarget, markInput = {},
                        colorFilter = activeColorFilter, filterQuality = cellFilterQuality,
                        aiParams = buildReaderAiPage(pages.getOrNull(l1), prefs), reloadTick = reloadTick,
                        onImageSize = { w, h -> onRecordPageMetrics(l1, w, h) },
                        modifier = Modifier.weight(r1),
                    )
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                ReaderPagedCell(
                    page = pages.getOrNull(l0), index = l0, fit = fit,
                    zoomTarget = zoomTarget, markInput = {},
                    colorFilter = activeColorFilter, filterQuality = cellFilterQuality,
                    aiParams = buildReaderAiPage(pages.getOrNull(l0), prefs), reloadTick = reloadTick,
                    onImageSize = { w, h -> onRecordPageMetrics(l0, w, h) },
                    modifier = Modifier.weight(1f),
                )
                ReaderPagedCell(
                    page = pages.getOrNull(l1), index = l1, fit = fit,
                    zoomTarget = zoomTarget, markInput = {},
                    colorFilter = activeColorFilter, filterQuality = cellFilterQuality,
                    aiParams = buildReaderAiPage(pages.getOrNull(l1), prefs), reloadTick = reloadTick,
                    onImageSize = { w, h -> onRecordPageMetrics(l1, w, h) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (prefs.shadowOn && l0 >= 0 && l1 >= 0 && fit != ReaderFit.ORIGINAL) {
            val dark = prefs.shadowOpacity / 100f
            val shift = prefs.shadowDisplace / 100f
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxHeight()
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colorStops = (0.5f - shift * 0.4f).coerceIn(0.05f, 0.95f).let { center ->
                                val w = (prefs.shadowSize / 100f * 0.3f).coerceIn(0.02f, 0.3f)
                                arrayOf(
                                    (center - w).coerceAtLeast(0f) to Color.Transparent,
                                    (center - w * 0.4f).coerceIn(0f, 1f) to Color.Black.copy(alpha = dark * 0.5f),
                                    center to Color.Black.copy(alpha = dark),
                                    (center + w * 0.4f).coerceIn(0f, 1f) to Color.Black.copy(alpha = dark * 0.5f),
                                    (center + w).coerceAtMost(1f) to Color.Transparent,
                                )
                            },
                        ),
                    ),
            )
        }
    }
}

/** 横向连续（无吸附）阅读器主视口。 */
@Composable
internal fun ReaderHorizontalContinuousViewport(
    pages: List<DesktopComicPage>,
    rowState: LazyListState,
    prefs: DesktopReaderPrefsState,
    fit: ReaderFit,
    zoomTarget: Float,
    activeColorFilter: ColorFilter?,
    cellFilterQuality: FilterQuality,
    reloadTick: Int,
    onRecordPageMetrics: (Int, Int, Int) -> Unit,
    onViewportSize: (IntSize) -> Unit,
    onViewportOrigin: (Offset) -> Unit,
    onProvideLensViewport: (@Composable () -> Unit) -> Unit,
) {
    val rowPageContent: LazyListScope.() -> Unit = {
        items(pages.size) { i ->
            ReaderPagedCell(
                page = pages.getOrNull(i),
                index = i,
                fit = fit,
                zoomTarget = zoomTarget,
                markInput = {},
                colorFilter = activeColorFilter,
                filterQuality = cellFilterQuality,
                aiParams = buildReaderAiPage(pages.getOrNull(i), prefs),
                reloadTick = reloadTick,
                onImageSize = { w, h -> onRecordPageMetrics(i, w, h) },
                modifier = Modifier.fillParentMaxWidth().fillMaxHeight(),
                fillCell = true,
            )
        }
    }
    LazyRow(
        state = rowState,
        modifier = Modifier
            .fillMaxSize()
            .padding(start = LEFT_STRIP_WIDTH)
            .onSizeChanged(onViewportSize)
            .onGloballyPositioned { onViewportOrigin(it.positionInRoot()) },
        content = rowPageContent,
    )
    onProvideLensViewport {
        LazyRow(
            state = rowState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = false,
            content = rowPageContent,
        )
    }
}

/** 纵向连读（条漫/竖向滚）阅读器主视口。 */
@Composable
internal fun ReaderVerticalStripViewport(
    pages: List<DesktopComicPage>,
    listState: LazyListState,
    pageRatios: MutableMap<Int, Float>,
    prefs: DesktopReaderPrefsState,
    webtoonNow: Boolean,
    viewportSize: IntSize,
    zoom: Float,
    zoomTarget: Float,
    pan: Offset,
    onPanDelta: (Offset, Float, Float) -> Unit,
    rotationDegrees: Int,
    masked: Boolean,
    activeColorFilter: ColorFilter?,
    cellFilterQuality: FilterQuality,
    onHorizontalRotation: (Int) -> Float,
    onViewportSize: (IntSize) -> Unit,
    onViewportOrigin: (Offset) -> Unit,
    onProvideLensViewport: (@Composable () -> Unit) -> Unit,
) {
    val tokens = VeneraTokens
    val listPageContent: LazyListScope.() -> Unit = {
        items(pages.size) { i ->
            val ratio = pageRatios[i] ?: TRIPLE_RATIO_FALLBACK
            val widthFraction = (prefs.stripMaxWidthPercent / 100f).coerceIn(0.5f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth(widthFraction)
                    .then(
                        if (prefs.singleInScroll) {
                            Modifier.heightIn(max = with(LocalDensity.current) { viewportSize.height.toDp() })
                        } else {
                            Modifier
                        },
                    )
                    .aspectRatio(ratio)
                    .then(if (masked) Modifier.blur(24.dp) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                val stripPlatformCtx = LocalPlatformContext.current
                val stripMaxWidthPx = if (viewportSize.width > 0) {
                    (viewportSize.width * widthFraction).toInt().coerceIn(800, 1920)
                } else {
                    1600
                }
                val stripModel = remember(pages[i], stripMaxWidthPx, stripPlatformCtx) {
                    ImageRequest.Builder(stripPlatformCtx)
                        .data(pages[i])
                        .size(Dimension.Pixels(stripMaxWidthPx), Dimension.Undefined)
                        .build()
                }
                AsyncImage(
                    model = stripModel,
                    contentDescription = DesktopI18n.t("第 ${i + 1} 页", "Page ${i + 1}"),
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            rotationZ = rotationDegrees.toFloat() + onHorizontalRotation(i)
                        },
                    contentScale = ContentScale.Fit,
                    colorFilter = activeColorFilter,
                    filterQuality = cellFilterQuality,
                    onSuccess = { state ->
                        val img = state.result.image
                        if (img.width > 0 && img.height > 0) {
                            pageRatios[i] = img.width.toFloat() / img.height.toFloat()
                        }
                    },
                )
            }
        }
        item {
            Spacer(Modifier.height(tokens.spacing.space6))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = DesktopI18n.t("— 本话完 —", "— End of Chapter —"),
                    fontSize = VeneraType.aux.size,
                    lineHeight = VeneraType.aux.lineHeight,
                    color = Color(0xFF888888),
                    modifier = Modifier.padding(bottom = tokens.spacing.space6),
                )
            }
        }
    }

    val padding = PaddingValues(
        horizontal = prefs.marginH.dp,
        vertical = if (webtoonNow) 0.dp else prefs.marginV.dp,
    )
    val arrangement = Arrangement.spacedBy(
        if (webtoonNow) {
            if (prefs.stripPageGap > 0) prefs.stripPageGap.dp else tokens.spacing.space1
        } else {
            prefs.marginV.dp
        },
    )

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(start = LEFT_STRIP_WIDTH)
            .onSizeChanged(onViewportSize)
            .onGloballyPositioned { onViewportOrigin(it.positionInRoot()) }
            .readerZoomDragPan(zoomTarget, onPanDelta)
            .clipToBounds()
            .graphicsLayer {
                scaleX = zoom
                scaleY = zoom
                translationX = pan.x
                translationY = pan.y
                transformOrigin = TransformOrigin(0.5f, 0.5f)
            },
        contentPadding = padding,
        verticalArrangement = arrangement,
        horizontalAlignment = Alignment.CenterHorizontally,
        content = listPageContent,
    )
    onProvideLensViewport {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    translationX = pan.x
                    translationY = pan.y
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                },
            userScrollEnabled = false,
            contentPadding = padding,
            verticalArrangement = arrangement,
            horizontalAlignment = Alignment.CenterHorizontally,
            content = listPageContent,
        )
    }
}

private fun Modifier.readerZoomDragPan(
    zoomTarget: Float,
    onPanDelta: (drag: Offset, maxX: Float, maxY: Float) -> Unit,
): Modifier = pointerInput(zoomTarget > 1.01f) {
    if (zoomTarget <= 1.01f) return@pointerInput
    detectDragGestures { change, dragAmount ->
        change.consume()
        val maxX = size.width * (zoomTarget - 1f) / 2f
        val maxY = size.height * (zoomTarget - 1f) / 2f
        onPanDelta(dragAmount, maxX, maxY)
    }
}

private suspend fun saveReaderPageToFile(
    loader: ImageLoader?,
    targetPage: DesktopComicPage,
    currentPage: Int,
) {
    val activeLoader = loader ?: return
    val req = ImageRequest.Builder(PlatformContext.INSTANCE).data(targetPage).build()
    val res = activeLoader.execute(req)
    val img = (res as? SuccessResult)?.image ?: return
    val bmp = img.toBitmap().toBufferedImage()
    val chooser = JFileChooser().apply { selectedFile = File("page_${currentPage + 1}.png") }
    if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
        ImageIO.write(bmp, "png", chooser.selectedFile)
    }
}

private suspend fun copyReaderPageToClipboard(
    loader: ImageLoader?,
    targetPage: DesktopComicPage,
) {
    val activeLoader = loader ?: return
    val req = ImageRequest.Builder(PlatformContext.INSTANCE).data(targetPage).build()
    val res = activeLoader.execute(req)
    val img = (res as? SuccessResult)?.image ?: return
    val bmp = img.toBitmap().toBufferedImage()
    val transferable = object : Transferable {
        override fun getTransferDataFlavors() = arrayOf(DataFlavor.imageFlavor)
        override fun isDataFlavorSupported(flavor: DataFlavor) = flavor == DataFlavor.imageFlavor
        override fun getTransferData(flavor: DataFlavor): Any = bmp
    }
    Toolkit.getDefaultToolkit().systemClipboard.setContents(transferable, null)
}
