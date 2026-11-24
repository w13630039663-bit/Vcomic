package com.venera.desktop.reader

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import com.venera.desktop.gallery.data.DesktopComicPage
import com.venera.desktop.gallery.data.DesktopReaderMode
import com.venera.desktop.gallery.ui.desktopReaderIsPaged
import com.venera.desktop.gallery.ui.desktopReaderReverses
import com.venera.desktop.gallery.ui.desktopReaderStepTarget
import com.venera.desktop.settings.DesktopI18n
import com.venera.desktop.settings.DesktopReaderBackground
import com.venera.desktop.ui.DesktopViewerExtraAction
import com.venera.desktop.ui.RegisterDesktopShortcutHandler
import com.venera.desktop.ui.matchViewerExtraShortcut
import com.venera.desktop.ui.theme.VeneraType
import java.awt.event.KeyEvent
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * 桌面漫画阅读器主屏入口：统筹状态、键盘与指针手势分发、顶栏与各按需浮层。
 * 渲染视口已拆至 `DesktopReaderViewports.kt`，长生命副作用已拆至 `DesktopReaderModelsAndEffects.kt`。
 */
@Composable
internal fun DesktopReaderScreen(
    pages: List<DesktopComicPage>,
    episodeLabel: String,
    note: String?,
    masked: Boolean,
    currentPage: Int,
    startPage: Int,
    onPageChange: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    readerMode: DesktopReaderMode = DesktopReaderMode.VERTICAL_CONTINUOUS,
    onPickMode: ((DesktopReaderMode) -> Unit)? = null,
    episodes: List<String>? = null,
    chapterIndex: Int = -1,
    onGoChapter: ((Int) -> Unit)? = null,
    seedNote: String? = null,
    episodeKey: String = "",
    comicTitle: String? = null,
    sourceKey: String? = null,
    onPreloadNextChapter: (() -> Unit)? = null,
) {
    val prefs by DesktopReaderPreferences.state.collectAsState()
    val readerModeNow = when {
        !prefs.doublePageOn && readerMode == DesktopReaderMode.DOUBLE_PAGE -> DesktopReaderMode.HORIZONTAL_LTR
        else -> readerMode
    }
    val resolvedMode = when {
        !prefs.reverseReading -> readerModeNow
        readerModeNow == DesktopReaderMode.HORIZONTAL_LTR -> DesktopReaderMode.HORIZONTAL_RTL
        readerModeNow == DesktopReaderMode.HORIZONTAL_RTL -> DesktopReaderMode.HORIZONTAL_LTR
        else -> readerModeNow
    }
    val paged = desktopReaderIsPaged(resolvedMode)
    val webtoonNow = resolvedMode == DesktopReaderMode.VERTICAL_CONTINUOUS
    val horizontalContinuous = resolvedMode == DesktopReaderMode.HORIZONTAL_CONTINUOUS
    val pickModeResolved: ((DesktopReaderMode) -> Unit)? = remember(onPickMode) {
        onPickMode?.let { pick ->
            { target: DesktopReaderMode ->
                DesktopReaderPreferences.update { it.copy(reverseReading = false) }
                pick(target)
            }
        }
    }

    val rowState = rememberLazyListState(initialFirstVisibleItemIndex = startPage.coerceAtLeast(0))
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startPage.coerceAtLeast(0))
    val isDouble = resolvedMode == DesktopReaderMode.DOUBLE_PAGE
    val lastIndex = pages.lastIndex
    val filterPresets by DesktopReaderPreferences.filterPresets.collectAsState()
    val bookmarksVersion by DesktopReaderPreferences.bookmarksVersion.collectAsState()
    val pageRatios = remember(pages) { mutableStateMapOf<Int, Float>() }
    val doubleGroups = remember(
        pages.size, prefs.coverSinglePage, pageRatios,
        prefs.alignNextHorizontal, prefs.blankPage, prefs.keepWideSingle, prefs.rotateH,
    ) {
        buildDoublePageGroups(
            pageCount = pages.size,
            coverSinglePage = prefs.coverSinglePage,
            widePageIndices = pageRatios.entries
                .filter { (_, ratio) ->
                    val rotatedWide = if (prefs.rotateH == 1 || prefs.rotateH == 2) ratio < 1f else ratio > 1f
                    prefs.keepWideSingle && rotatedWide
                }
                .mapTo(mutableSetOf()) { it.key },
            alignWithNextHorizontal = prefs.alignNextHorizontal,
            leadingBlankPage = prefs.blankPage,
        )
    }
    val pagerGroupCount = if (isDouble) doubleGroups.size else pages.size
    val guidedSpreadPages = remember(isDouble, currentPage, doubleGroups) {
        if (!isDouble) {
            listOf(currentPage)
        } else {
            doubleGroups.getOrNull(doubleGroups.indexOfFirst { currentPage in it })
                ?.filter { it != BLANK_PAGE }
                ?.takeIf { it.isNotEmpty() }
                ?: listOf(currentPage)
        }
    }
    val guidedSpreadRatios = guidedSpreadPages.map { pageRatios[it] ?: TRIPLE_RATIO_FALLBACK }
    val focus = remember { FocusRequester() }
    val initialGroup = if (isDouble) {
        doubleGroups.indexOfFirst { startPage.coerceAtLeast(0) in it }.coerceAtLeast(0)
    } else {
        startPage.coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(initialPage = initialGroup, pageCount = { pagerGroupCount })
    val scope = rememberCoroutineScope()
    val currentBookKey = if (!sourceKey.isNullOrBlank() && !comicTitle.isNullOrBlank()) "$sourceKey:$comicTitle" else null

    var chromeVisible by remember { mutableStateOf(true) }
    var panel by remember { mutableStateOf(ReaderPanel.None) }
    var settingsTab by remember { mutableIntStateOf(1) }
    var jumpDialogOpen by remember { mutableStateOf(false) }
    val magnifierOn = prefs.magnifierOn
    var pointerPos by remember { mutableStateOf(Offset.Zero) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    var viewportOriginInRoot by remember { mutableStateOf(Offset.Zero) }
    val rotationDegrees = (prefs.rotationIndex and 3) * 90
    var reloadTick by remember { mutableIntStateOf(0) }
    var boundaryHint by remember { mutableStateOf<String?>(null) }
    var boundaryDirection by remember { mutableIntStateOf(0) }
    var boundaryHintAt by remember { mutableLongStateOf(0L) }
    var lastPointerMoveTick by remember { mutableLongStateOf(0L) }

    val fit = ReaderFit.entries.getOrElse(prefs.fitIndex) { ReaderFit.FIT_SCREEN }
    var lensViewportRef: (@Composable () -> Unit)? = null
    val prefBackground by DesktopReaderBackground.index.collectAsState()
    var background by remember(prefBackground) { mutableIntStateOf(prefBackground) }
    val arrowKeysOn = prefs.arrowKeysOn
    var autoPlay by remember { mutableStateOf(false) }
    val autoPlaySecs = prefs.autoPlaySecs

    val colorMatrix = remember(
        prefs.brightness, prefs.contrast, prefs.saturation,
        prefs.filterHue, prefs.filterSepia, prefs.filterInvert, prefs.filterNegative,
    ) {
        buildReaderFilterMatrix(
            brightness = prefs.brightness,
            contrast = prefs.contrast,
            saturation = prefs.saturation,
            hueDegrees = prefs.filterHue,
            sepiaStrength = prefs.filterSepia,
            grayscale = prefs.filterInvert,
            negative = prefs.filterNegative,
        )
    }
    val activeColorFilter = remember(colorMatrix) { colorMatrix?.let { ColorFilter.colorMatrix(it) } }
    val cellFilterQuality = remember(prefs.filterQualityIndex) { resolveFilterQuality(prefs.filterQualityIndex) }
    val bookmarks = remember(episodeKey, bookmarksVersion) { DesktopReaderPreferences.bookmarksOf(episodeKey) }
    val isCurrentBookmarked = currentPage in bookmarks

    var zoomTarget by remember { mutableFloatStateOf(1f) }
    var panTarget by remember { mutableStateOf(Offset.Zero) }
    val zoom by animateFloatAsState(targetValue = zoomTarget, animationSpec = tween(150))
    val pan = panTarget
    val canvasBg = DesktopReaderBackground.colorOf(background)
    var landedForThisEpisode by remember(episodeKey) { mutableStateOf(false) }
    val canPrevChapter = episodes != null && onGoChapter != null && chapterIndex > 0
    val canNextChapter = episodes != null && onGoChapter != null && chapterIndex in 0 until episodes.lastIndex
    val currentPageNow by rememberUpdatedState(currentPage)
    val tapZoneNow by rememberUpdatedState(prefs.tapZonePercent / 100f)
    var guidedViewFocused by remember { mutableStateOf(true) }
    var guidedPanelIndex by remember { mutableIntStateOf(0) }
    var guidedStepCount by remember { mutableIntStateOf(1) }
    var readerImageLoader by remember { mutableStateOf<ImageLoader?>(null) }

    androidx.compose.runtime.LaunchedEffect(currentPage, prefs.turnEffect) {
        guidedPanelIndex = 0
        guidedViewFocused = true
    }

    fun recordPageMetrics(idx: Int, w: Int, h: Int) {
        if (idx >= 0 && w > 0 && h > 0) pageRatios[idx] = w.toFloat() / h.toFloat()
    }

    fun horizontalPageRotation(idx: Int): Float {
        if (prefs.rotateH == 0 || (pageRatios[idx] ?: 0f) <= 1f) return 0f
        return when (prefs.rotateH) {
            1 -> -90f
            2 -> 90f
            3 -> 180f
            else -> 0f
        }
    }

    fun jumpToLogical(target: Int) {
        val clamped = target.coerceIn(0, maxOf(lastIndex, 0))
        onPageChange(clamped)
        scope.launch {
            if (paged) {
                val targetGroup = if (isDouble) {
                    doubleGroups.indexOfFirst { clamped in it }.coerceAtLeast(0)
                } else {
                    clamped
                }
                val nearGroup = abs(targetGroup - pagerState.currentPage) <= 3
                val turnSpec = tween<Float>(durationMillis = (prefs.turnAnimSeconds * 1000f).roundToInt().coerceAtLeast(1))
                when {
                    nearGroup && prefs.turnEffect != 0 -> pagerState.animateScrollToPage(targetGroup, animationSpec = turnSpec)
                    nearGroup && prefs.smoothTurning -> pagerState.animateScrollToPage(targetGroup)
                    else -> pagerState.scrollToPage(targetGroup)
                }
            } else if (horizontalContinuous) {
                if (prefs.smoothTurning && abs(clamped - rowState.firstVisibleItemIndex) <= 3) {
                    rowState.animateScrollToItem(clamped)
                } else {
                    rowState.scrollToItem(clamped)
                }
            } else {
                if (prefs.smoothTurning && abs(clamped - listState.firstVisibleItemIndex) <= 3) {
                    listState.animateScrollToItem(clamped)
                } else {
                    listState.scrollToItem(clamped)
                }
            }
        }
    }

    fun step(delta: Int) {
        if (pages.isEmpty()) return
        if (paged && prefs.turnEffect == 2 && guidedViewFocused && guidedStepCount > 1) {
            val nextPanel = guidedPanelIndex + delta
            if (nextPanel in 0 until guidedStepCount) {
                guidedPanelIndex = nextPanel
                return
            }
        }
        val nextTarget = if (paged) {
            if (isDouble) {
                val curGroup = doubleGroups.indexOfFirst { currentPageNow in it }.coerceAtLeast(0)
                val nextGroup = curGroup + delta
                if (nextGroup in doubleGroups.indices) doubleGroups[nextGroup].firstOrNull { it != BLANK_PAGE } else null
            } else {
                desktopReaderStepTarget(currentPageNow, delta, resolvedMode, lastIndex)
            }
        } else {
            val t = currentPageNow + delta
            if (t in 0..lastIndex) t else null
        }
        if (nextTarget != null) {
            boundaryHint = null
            boundaryDirection = 0
            jumpToLogical(nextTarget)
            return
        }
        val now = System.currentTimeMillis()
        if (delta > 0 && canNextChapter) {
            if (boundaryDirection == 1 && now - boundaryHintAt <= 2_000L) {
                boundaryHint = null
                boundaryDirection = 0
                onGoChapter?.invoke(chapterIndex + 1)
            } else {
                boundaryDirection = 1
                boundaryHintAt = now
                boundaryHint = DesktopI18n.tr("已到本话末页，再按一次进入下一话", "End of chapter, press again for next")
            }
        } else if (delta < 0 && canPrevChapter) {
            if (boundaryDirection == -1 && now - boundaryHintAt <= 2_000L) {
                boundaryHint = null
                boundaryDirection = 0
                onGoChapter?.invoke(chapterIndex - 1)
            } else {
                boundaryDirection = -1
                boundaryHintAt = now
                boundaryHint = DesktopI18n.tr("已到本话首页，再按一次返回上一话", "Start of chapter, press again for previous")
            }
        }
    }

    DesktopReaderLifecycleEffects(
        currentBookKey = currentBookKey,
        episodeKey = episodeKey,
        pages = pages,
        currentPage = currentPage,
        startPage = startPage,
        prefs = prefs,
        readerMode = resolvedMode,
        paged = paged,
        horizontalContinuous = horizontalContinuous,
        isDouble = isDouble,
        doubleGroups = doubleGroups,
        pagerGroupCount = pagerGroupCount,
        pagerState = pagerState,
        listState = listState,
        rowState = rowState,
        viewportSize = viewportSize,
        canNextChapter = canNextChapter,
        onPreloadNextChapter = onPreloadNextChapter,
        panel = panel,
        chromeVisible = chromeVisible,
        jumpDialogOpen = jumpDialogOpen,
        lastPointerMoveTick = lastPointerMoveTick,
        boundaryHint = boundaryHint,
        boundaryHintAt = boundaryHintAt,
        autoPlay = autoPlay,
        autoPlaySecs = autoPlaySecs,
        landedForThisEpisode = landedForThisEpisode,
        focus = focus,
        onPageChange = onPageChange,
        onResetZoomPan = {
            if (zoomTarget > 1.01f || panTarget != Offset.Zero) {
                zoomTarget = 1f
                panTarget = Offset.Zero
            }
        },
        onClearBoundaryHint = {
            boundaryHint = null
            boundaryDirection = 0
        },
        onHideChrome = { chromeVisible = false },
        onLanded = { landedForThisEpisode = true },
        onAutoPlayChange = { autoPlay = it },
        onStepNext = { step(1) },
        onReaderImageLoaderReady = { readerImageLoader = it },
    )

    RegisterDesktopShortcutHandler(
        pages.size, resolvedMode, arrowKeysOn, panel, jumpDialogOpen, magnifierOn, chromeVisible, chapterIndex,
        priority = 300,
    ) { signal ->
        when (matchViewerExtraShortcut(signal)) {
            DesktopViewerExtraAction.ZOOM_IN -> {
                zoomTarget = (zoomTarget * 1.25f).coerceIn(READER_MIN_ZOOM, if (prefs.noEnlarge) 1f else READER_MAX_ZOOM)
                return@RegisterDesktopShortcutHandler true
            }
            DesktopViewerExtraAction.ZOOM_OUT -> {
                val next = (zoomTarget / 1.25f).coerceIn(READER_MIN_ZOOM, if (prefs.noEnlarge) 1f else READER_MAX_ZOOM)
                zoomTarget = next
                if (next <= 1.01f) panTarget = Offset.Zero
                return@RegisterDesktopShortcutHandler true
            }
            DesktopViewerExtraAction.ZOOM_RESET -> {
                zoomTarget = 1f
                panTarget = Offset.Zero
                return@RegisterDesktopShortcutHandler true
            }
            DesktopViewerExtraAction.FIT_SCREEN -> {
                val nextIdx = if (prefs.fitIndex == 0) 1 else 0
                DesktopReaderPreferences.update { it.copy(fitIndex = nextIdx) }
                zoomTarget = 1f
                panTarget = Offset.Zero
                return@RegisterDesktopShortcutHandler true
            }
            DesktopViewerExtraAction.ROTATE_OR_MODE -> {
                DesktopReaderPreferences.update { it.copy(rotationIndex = (it.rotationIndex + 1) and 3) }
                return@RegisterDesktopShortcutHandler true
            }
            null -> Unit
        }
        when (signal.keyCode) {
            KeyEvent.VK_M -> { DesktopReaderPreferences.update { it.copy(magnifierOn = !it.magnifierOn) }; true }
            KeyEvent.VK_G -> { if (pages.isNotEmpty()) jumpDialogOpen = !jumpDialogOpen; true }
            KeyEvent.VK_B -> { if (pages.isNotEmpty()) DesktopReaderPreferences.toggleBookmark(episodeKey, currentPageNow); true }
            KeyEvent.VK_RIGHT, KeyEvent.VK_DOWN -> {
                if (arrowKeysOn && panel == ReaderPanel.None && !jumpDialogOpen && !magnifierOn && !signal.isAlt) {
                    step(1); true
                } else false
            }
            KeyEvent.VK_LEFT, KeyEvent.VK_UP -> {
                if (arrowKeysOn && panel == ReaderPanel.None && !jumpDialogOpen && !magnifierOn && !signal.isAlt) {
                    step(-1); true
                } else false
            }
            KeyEvent.VK_PAGE_DOWN, KeyEvent.VK_D -> { step(1); true }
            KeyEvent.VK_PAGE_UP, KeyEvent.VK_A -> { step(-1); true }
            KeyEvent.VK_HOME -> { if (pages.isNotEmpty()) jumpToLogical(0); true }
            KeyEvent.VK_END -> { if (pages.isNotEmpty()) jumpToLogical(lastIndex); true }
            KeyEvent.VK_SPACE -> {
                val d = if (signal.isShift) -1 else 1
                if (paged) step(d) else {
                    val t = (currentPage + d).coerceIn(0, maxOf(lastIndex, 0))
                    if (t != currentPage) jumpToLogical(t)
                }
                true
            }
            KeyEvent.VK_ESCAPE -> {
                when {
                    prefs.turnEffect == 2 && guidedViewFocused -> {
                        guidedViewFocused = false
                        runCatching { focus.requestFocus() }
                    }
                    jumpDialogOpen || magnifierOn || panel != ReaderPanel.None -> {
                        jumpDialogOpen = false
                        DesktopReaderPreferences.update { it.copy(magnifierOn = false) }
                        panel = ReaderPanel.None
                        runCatching { focus.requestFocus() }
                    }
                    chromeVisible -> {
                        chromeVisible = false
                        runCatching { focus.requestFocus() }
                    }
                    else -> onBack()
                }
                true
            }
            KeyEvent.VK_COMMA -> { if (onGoChapter != null && canPrevChapter) onGoChapter(chapterIndex - 1); true }
            KeyEvent.VK_PERIOD -> { if (onGoChapter != null && canNextChapter) onGoChapter(chapterIndex + 1); true }
            else -> false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(canvasBg)
            .onPreviewKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown && arrowKeysOn && panel == ReaderPanel.None &&
                    !jumpDialogOpen && !magnifierOn
                ) {
                    when (ev.key) {
                        Key.DirectionRight, Key.DirectionDown -> { step(1); true }
                        Key.DirectionLeft, Key.DirectionUp -> { step(-1); true }
                        else -> false
                    }
                } else false
            },
    ) {
        ReaderTopBar(
            title = comicTitle.orEmpty(),
            episodeLabel = episodeLabel,
            currentPage = currentPage,
            totalPages = pages.size,
            canPrevChapter = canPrevChapter,
            canNextChapter = canNextChapter,
            hasChapters = !episodes.isNullOrEmpty(),
            isBookmarked = isCurrentBookmarked,
            magnifierOn = magnifierOn,
            settingsOpen = panel == ReaderPanel.Settings,
            settingsTab = settingsTab,
            showPageNumber = prefs.showPageNumber,
            onBack = onBack,
            onPrevChapter = { if (onGoChapter != null && canPrevChapter) onGoChapter(chapterIndex - 1) },
            onNextChapter = { if (onGoChapter != null && canNextChapter) onGoChapter(chapterIndex + 1) },
            onFirstPage = { if (pages.isNotEmpty()) jumpToLogical(0) },
            onLastPage = { if (pages.isNotEmpty()) jumpToLogical(lastIndex) },
            onPrevPage = { step(-1) },
            onNextPage = { step(1) },
            onZoomIn = { zoomTarget = (zoomTarget * 1.25f).coerceIn(READER_MIN_ZOOM, if (prefs.noEnlarge) 1f else READER_MAX_ZOOM) },
            onZoomOut = {
                val next = (zoomTarget / 1.25f).coerceIn(READER_MIN_ZOOM, if (prefs.noEnlarge) 1f else READER_MAX_ZOOM)
                zoomTarget = next
                if (next <= 1.01f) panTarget = Offset.Zero
            },
            onResetZoom = { zoomTarget = 1f; panTarget = Offset.Zero },
            onCycleFit = {
                val nextIdx = if (prefs.fitIndex == 0) 1 else 0
                DesktopReaderPreferences.update { it.copy(fitIndex = nextIdx) }
                zoomTarget = 1f
                panTarget = Offset.Zero
            },
            onToggleMagnifier = { DesktopReaderPreferences.update { it.copy(magnifierOn = !it.magnifierOn) } },
            onOpenJumpDialog = { if (pages.isNotEmpty()) jumpDialogOpen = true },
            onToggleBookmark = { if (pages.isNotEmpty()) DesktopReaderPreferences.toggleBookmark(episodeKey, currentPage) },
            onOpenBookmarkList = { panel = if (panel == ReaderPanel.Chapters) ReaderPanel.None else ReaderPanel.Chapters },
            onOpenSettingsTab = { tab ->
                settingsTab = tab
                panel = if (panel == ReaderPanel.Settings && settingsTab == tab) ReaderPanel.None else ReaderPanel.Settings
            },
            reverseReading = prefs.reverseReading,
            onToggleReverse = { DesktopReaderPreferences.update { it.copy(reverseReading = !it.reverseReading) } },
            autoPlaySecs = prefs.autoPlaySecs,
            onCycleAutoPlay = {
                DesktopReaderPreferences.update {
                    it.copy(autoPlaySecs = when (it.autoPlaySecs) { 2 -> 4; 4 -> 8; else -> 2 })
                }
            },
            onToggleShowPageNumber = { DesktopReaderPreferences.update { it.copy(showPageNumber = !it.showPageNumber) } },
            onHideTopBar = { chromeVisible = false },
            onInteract = { runCatching { focus.requestFocus() } },
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(canvasBg)
                .onGloballyPositioned { rootOrigin = it.positionInRoot() }
                .focusRequester(focus)
                .focusable()
                .pointerInput(paged, horizontalContinuous, prefs.turnEffect, pages.size, panel, jumpDialogOpen, magnifierOn) {
                    val stripPx = LEFT_STRIP_WIDTH.toPx()
                    awaitPointerEventScope {
                        while (true) {
                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                            when (ev.type) {
                                PointerEventType.Move -> {
                                    lastPointerMoveTick = System.currentTimeMillis()
                                    ev.changes.firstOrNull()?.position?.let { pointerPos = it }
                                }
                                PointerEventType.Scroll -> {
                                    val change = ev.changes.firstOrNull()
                                    val overlayOpen = panel != ReaderPanel.None || jumpDialogOpen || magnifierOn
                                    val onCanvas = change != null && change.position.x >= stripPx
                                    if (!overlayOpen && onCanvas && change != null && pages.isNotEmpty()) {
                                        val delta = change.scrollDelta.y
                                        if (delta != 0f) {
                                            if (paged || ev.keyboardModifiers.isCtrlPressed) {
                                                val next = computeCursorAnchoredZoom(
                                                    currentZoom = zoomTarget,
                                                    currentPan = panTarget,
                                                    scrollDeltaY = delta,
                                                    cursorPosition = Offset(change.position.x - stripPx, change.position.y),
                                                    viewportWidth = (size.width - stripPx).coerceAtLeast(1f),
                                                    viewportHeight = size.height.toFloat(),
                                                )
                                                zoomTarget = next.zoom
                                                panTarget = next.pan
                                                change.consume()
                                            } else if (horizontalContinuous) {
                                                scope.launch { rowState.scrollBy(delta * 260f) }
                                                change.consume()
                                            }
                                        }
                                    }
                                }
                                else -> {}
                            }
                        }
                    }
                }
                .pointerInput(paged, pages.size, resolvedMode) {
                    val stripPx = LEFT_STRIP_WIDTH.toPx()
                    val contentWidth = (size.width - stripPx).coerceAtLeast(1f)
                    detectTapGestures(
                        onTap = { offset ->
                            if (panel != ReaderPanel.None) {
                                panel = ReaderPanel.None
                                runCatching { focus.requestFocus() }
                            } else if ((paged || horizontalContinuous) && pages.isNotEmpty() && size.width > 0) {
                                val localX = offset.x - stripPx
                                if (localX >= 0f) {
                                    val ratio = localX / contentWidth
                                    val rtl = desktopReaderReverses(resolvedMode)
                                    val zone = tapZoneNow
                                    when {
                                        ratio < zone -> step(if (rtl) 1 else -1)
                                        ratio > 1f - zone -> step(if (rtl) -1 else 1)
                                        else -> chromeVisible = !chromeVisible
                                    }
                                }
                            } else {
                                chromeVisible = !chromeVisible
                            }
                        },
                        onDoubleTap = { offset ->
                            if (paged) {
                                val next = toggleDoubleTapZoom(
                                    currentZoom = zoomTarget,
                                    tapPosition = Offset(offset.x - stripPx, offset.y),
                                    viewportWidth = contentWidth,
                                    viewportHeight = size.height.toFloat(),
                                )
                                zoomTarget = next.zoom
                                panTarget = next.pan
                            }
                        },
                    )
                }
                .pointerInput(paged, prefs.dragTurnOn, pages.size, resolvedMode) {
                    if (!paged || !prefs.dragTurnOn || pages.isEmpty()) return@pointerInput
                    val stripPx = LEFT_STRIP_WIDTH.toPx()
                    val contentWidth = (size.width - stripPx).coerceAtLeast(1f)
                    var dragAccum = 0f
                    var inHotzone = false
                    awaitPointerEventScope {
                        while (true) {
                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                            val change = ev.changes.firstOrNull() ?: continue
                            when (ev.type) {
                                PointerEventType.Press -> {
                                    dragAccum = 0f
                                    inHotzone = false
                                }
                                PointerEventType.Move -> {
                                    if (!change.pressed || zoomTarget > 1.01f || panel != ReaderPanel.None ||
                                        jumpDialogOpen || magnifierOn
                                    ) continue
                                    val ratio = (change.position.x - stripPx) / contentWidth
                                    if (!inHotzone && (ratio < 0.15f || ratio > 0.85f)) inHotzone = true
                                    if (inHotzone) {
                                        val d = change.positionChange().x
                                        if (d != 0f) {
                                            dragAccum += d
                                            val rtl = desktopReaderReverses(resolvedMode)
                                            scope.launch {
                                                pagerState.scrollToPage(
                                                    pagerState.currentPage - (dragAccum / contentWidth * 0.5f)
                                                        .let { if (rtl) -it else it }.toInt(),
                                                )
                                            }
                                            change.consume()
                                        }
                                    }
                                }
                                PointerEventType.Release -> {
                                    if (inHotzone && zoomTarget <= 1.01f && panel == ReaderPanel.None) {
                                        val rtl = desktopReaderReverses(resolvedMode)
                                        val movedPages = dragAccum / contentWidth * 0.5f
                                        if (abs(movedPages) > 0.5f) {
                                            val dir = if (movedPages > 0f) 1 else -1
                                            step(if (rtl) -dir else dir)
                                        } else {
                                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage) }
                                        }
                                    }
                                    inHotzone = false
                                    dragAccum = 0f
                                }
                                else -> {}
                            }
                        }
                    }
                },
        ) {
            ReaderLeftStrip(
                visible = pages.isNotEmpty(),
                pages = pages,
                currentPage = currentPage,
                onSelectPage = ::jumpToLogical,
                modifier = Modifier.align(Alignment.CenterStart),
            )

            val onPanDelta: (Offset, Float, Float) -> Unit = { drag, maxX, maxY ->
                panTarget = Offset(
                    (panTarget.x + drag.x).coerceIn(-maxX, maxX),
                    (panTarget.y + drag.y).coerceIn(-maxY, maxY),
                )
            }

            if (pages.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = note ?: DesktopI18n.t("暂无页面", "No pages"),
                        fontSize = VeneraType.body.size,
                        lineHeight = VeneraType.body.lineHeight,
                        color = Color(0xFFCCCCCC),
                    )
                }
            } else if (paged) {
                ReaderPagedViewport(
                    pages = pages,
                    currentPage = currentPage,
                    currentPageNow = currentPageNow,
                    readerMode = resolvedMode,
                    prefs = prefs,
                    fit = fit,
                    isDouble = isDouble,
                    doubleGroups = doubleGroups,
                    pageRatios = pageRatios,
                    guidedSpreadPages = guidedSpreadPages,
                    guidedSpreadRatios = guidedSpreadRatios,
                    guidedPanelIndex = guidedPanelIndex,
                    guidedViewFocused = guidedViewFocused,
                    onGuidedStepCountChange = { guidedStepCount = it },
                    onGuidedPanelStep = { delta -> guidedPanelIndex += delta },
                    onGuidedExitFocus = { guidedViewFocused = false },
                    pagerState = pagerState,
                    zoom = zoom,
                    zoomTarget = zoomTarget,
                    pan = pan,
                    onPanDelta = onPanDelta,
                    rotationDegrees = rotationDegrees,
                    canvasBg = canvasBg,
                    masked = masked,
                    activeColorFilter = activeColorFilter,
                    cellFilterQuality = cellFilterQuality,
                    tapZoneNow = tapZoneNow,
                    reloadTick = reloadTick,
                    onReloadTickIncrement = { reloadTick += 1 },
                    readerImageLoader = readerImageLoader,
                    scope = scope,
                    onRecordPageMetrics = ::recordPageMetrics,
                    onHorizontalRotation = ::horizontalPageRotation,
                    onJumpToLogical = ::jumpToLogical,
                    onToggleHud = { chromeVisible = !chromeVisible },
                    onViewportSize = { viewportSize = it },
                    onViewportOrigin = { viewportOriginInRoot = it },
                    onProvideLensViewport = { lensViewportRef = it },
                )
            } else if (horizontalContinuous) {
                ReaderHorizontalContinuousViewport(
                    pages = pages,
                    rowState = rowState,
                    prefs = prefs,
                    fit = fit,
                    zoomTarget = zoomTarget,
                    activeColorFilter = activeColorFilter,
                    cellFilterQuality = cellFilterQuality,
                    reloadTick = reloadTick,
                    onRecordPageMetrics = ::recordPageMetrics,
                    onViewportSize = { viewportSize = it },
                    onViewportOrigin = { viewportOriginInRoot = it },
                    onProvideLensViewport = { lensViewportRef = it },
                )
            } else {
                ReaderVerticalStripViewport(
                    pages = pages,
                    listState = listState,
                    pageRatios = pageRatios,
                    prefs = prefs,
                    webtoonNow = webtoonNow,
                    viewportSize = viewportSize,
                    zoom = zoom,
                    zoomTarget = zoomTarget,
                    pan = pan,
                    onPanDelta = onPanDelta,
                    rotationDegrees = rotationDegrees,
                    masked = masked,
                    activeColorFilter = activeColorFilter,
                    cellFilterQuality = cellFilterQuality,
                    onHorizontalRotation = ::horizontalPageRotation,
                    onViewportSize = { viewportSize = it },
                    onViewportOrigin = { viewportOriginInRoot = it },
                    onProvideLensViewport = { lensViewportRef = it },
                )
            }

            val fallbackOrigin = with(LocalDensity.current) {
                if (paged) Offset(LEFT_STRIP_WIDTH.toPx() + prefs.marginH.dp.toPx(), 0f) else Offset(LEFT_STRIP_WIDTH.toPx(), 0f)
            }
            val lensOrigin = if (viewportOriginInRoot == Offset.Zero) fallbackOrigin else viewportOriginInRoot - rootOrigin
            ReaderMagnifierLens(
                visible = magnifierOn && pages.isNotEmpty(),
                pointerPosition = pointerPos,
                layerOrigin = lensOrigin,
                rootOrigin = rootOrigin,
                viewportWidthPx = viewportSize.width.toFloat(),
                viewportHeightPx = viewportSize.height.toFloat(),
                viewportContent = lensViewportRef ?: {},
                scale = prefs.magnifierScale,
            )

            if (boundaryHint != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 92.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xE616181D))
                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = boundaryHint.orEmpty(),
                        fontSize = VeneraType.aux.size,
                        color = Color(0xFFF3F4F6),
                    )
                }
            }

            ReaderBottomHud(
                visible = chromeVisible || panel == ReaderPanel.Thumbs,
                currentPage = currentPage,
                totalPages = pages.size,
                readerMode = resolvedMode,
                zoom = zoomTarget,
                onJumpToPage = ::jumpToLogical,
                onOpenJumpDialog = { jumpDialogOpen = true },
                onPickMode = pickModeResolved,
                onResetZoom = { zoomTarget = 1f; panTarget = Offset.Zero },
                modifier = Modifier.align(Alignment.BottomCenter),
            )

            ReaderThumbnailStripOverlay(
                visible = panel == ReaderPanel.Thumbs,
                pages = pages,
                currentPage = currentPage,
                onSelectPage = ::jumpToLogical,
                modifier = Modifier.align(Alignment.BottomCenter),
            )

            ReaderTocOverlay(
                visible = panel == ReaderPanel.Chapters,
                episodes = episodes.orEmpty(),
                currentChapter = chapterIndex,
                bookmarks = bookmarks,
                onSelectChapter = { idx ->
                    panel = ReaderPanel.None
                    onGoChapter?.invoke(idx)
                },
                onSelectBookmarkPage = { pageIdx ->
                    panel = ReaderPanel.None
                    jumpToLogical(pageIdx)
                },
                modifier = Modifier.align(Alignment.TopEnd),
            )

            ReaderSettingsOverlay(
                visible = panel == ReaderPanel.Settings,
                initialTab = settingsTab,
                fit = fit,
                backgroundIndex = background,
                prefs = prefs,
                autoPlay = autoPlay,
                verticalOnly = webtoonNow,
                onPickMode = pickModeResolved,
                currentPage = pages.getOrNull(currentPage),
                aiPageForComparison = buildReaderAiPage(pages.getOrNull(currentPage), prefs),
                onPickFit = { picked ->
                    DesktopReaderPreferences.update { it.copy(fitIndex = ReaderFit.entries.indexOf(picked).coerceAtLeast(0)) }
                    zoomTarget = 1f
                    panTarget = Offset.Zero
                },
                onPickBackground = { idx ->
                    background = idx
                    DesktopReaderBackground.writeIndex(idx)
                },
                onUpdatePrefs = { transform -> DesktopReaderPreferences.update(transform) },
                onResetFilters = { DesktopReaderPreferences.resetFilters() },
                presets = filterPresets,
                onSavePreset = {
                    DesktopReaderPreferences.saveFilterPreset(DesktopI18n.tr("预设 ${filterPresets.size + 1}", "Preset ${filterPresets.size + 1}"))
                },
                onApplyPreset = { DesktopReaderPreferences.applyFilterPreset(it) },
                onToggleAutoPlay = { autoPlay = it },
                onToggleBookCustomized = currentBookKey?.let { _ ->
                    { customized -> DesktopReaderPreferences.toggleBookCustomized(customized) }
                },
                modifier = Modifier.align(Alignment.TopEnd),
            )

            ReaderJumpPageDialog(
                visible = jumpDialogOpen,
                currentPage = currentPage,
                totalPages = pages.size,
                onDismiss = { jumpDialogOpen = false },
                onConfirm = ::jumpToLogical,
            )
        }
    }
}
