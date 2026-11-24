package com.venera.desktop.reader

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import com.venera.compose.components.LocalUxPlatformServices
import com.venera.desktop.app.LocalGpuCachePurge
import com.venera.desktop.gallery.data.DesktopComicPage
import com.venera.desktop.gallery.data.DesktopReaderMode
import com.venera.desktop.gallery.data.desktopComicTileCacheClear
import com.venera.desktop.gallery.ui.desktopApplyReaderStartPage
import com.venera.desktop.gallery.ui.desktopApplyReaderStartPagePager
import com.venera.desktop.gallery.ui.desktopReaderStepTarget
import com.venera.desktop.settings.DesktopI18n
import java.awt.Cursor
import java.awt.Point
import java.awt.Window
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener
import java.awt.image.BufferedImage
import kotlinx.coroutines.delay

/**
 * 阅读器互斥面板。
 *
 * 同一时刻只可能打开一个，互斥由类型保证。
 */
internal enum class ReaderPanel {
    None,
    Thumbs,
    Chapters,
    Settings,
}

/** 适配档：适应屏幕（整页可见）/ 适应宽度（纵向滚）/ 原始尺寸。会话内偏好。 */
internal enum class ReaderFit(private val labelZh: String, private val labelEn: String) {
    FIT_SCREEN("适应屏幕", "Fit screen"),
    FIT_WIDTH("适应宽度", "Fit width"),
    ORIGINAL("原始", "Original");

    val label: String get() = DesktopI18n.tr(labelZh, labelEn)
}

/** 构造带 AI 管线参数指纹的页面模型；所有 AI 开关全关时返回 null 以免重复计算。 */
internal fun buildReaderAiPage(
    page: DesktopComicPage?,
    prefs: DesktopReaderPrefsState,
): DesktopAiPage? {
    if (page == null || (!prefs.aiArtifact && !prefs.aiDescreen && !prefs.aiUpscale)) return null
    return DesktopAiPage(
        page = page,
        fingerprint = "a${prefs.aiArtifact}${prefs.aiArtifactModel}${prefs.aiArtifactTone}${prefs.aiArtifactSize}" +
            "d${prefs.aiDescreen}${prefs.aiDescreenModel}${prefs.aiDescreenKeep}${prefs.aiDescreenSize}" +
            "u${prefs.aiUpscale}${prefs.aiUpscaleModel}${prefs.aiUpscaleMaxmp}${prefs.aiUpscaleAuto}${prefs.aiUpscaleScale}",
        artifactOn = prefs.aiArtifact,
        artifactModel = prefs.aiArtifactModel,
        artifactTone = prefs.aiArtifactTone,
        artifactSize = prefs.aiArtifactSize,
        descreenOn = prefs.aiDescreen,
        descreenModel = prefs.aiDescreenModel,
        descreenKeep = prefs.aiDescreenKeep,
        descreenSize = prefs.aiDescreenSize,
        upscaleOn = prefs.aiUpscale,
        upscaleScale = if (prefs.aiUpscaleAuto && prefs.aiUpscaleScale == 4) 4 else prefs.aiUpscaleScale,
        upscaleMaxmp = prefs.aiUpscaleMaxmp,
    )
}

/** 集中管理阅读器长生命周期副作用：预加载、起点落位、翻页回写、沉浸光标、自动播放与窗口获焦还焦。 */
@Composable
internal fun DesktopReaderLifecycleEffects(
    currentBookKey: String?,
    episodeKey: String,
    pages: List<DesktopComicPage>,
    currentPage: Int,
    startPage: Int,
    prefs: DesktopReaderPrefsState,
    readerMode: DesktopReaderMode,
    paged: Boolean,
    horizontalContinuous: Boolean,
    isDouble: Boolean,
    doubleGroups: List<List<Int>>,
    pagerGroupCount: Int,
    pagerState: PagerState,
    listState: LazyListState,
    rowState: LazyListState,
    viewportSize: IntSize,
    canNextChapter: Boolean,
    onPreloadNextChapter: (() -> Unit)?,
    panel: ReaderPanel,
    chromeVisible: Boolean,
    jumpDialogOpen: Boolean,
    lastPointerMoveTick: Long,
    boundaryHint: String?,
    boundaryHintAt: Long,
    autoPlay: Boolean,
    autoPlaySecs: Int,
    landedForThisEpisode: Boolean,
    focus: FocusRequester,
    onPageChange: (Int) -> Unit,
    onResetZoomPan: () -> Unit,
    onClearBoundaryHint: () -> Unit,
    onHideChrome: () -> Unit,
    onLanded: () -> Unit,
    onAutoPlayChange: (Boolean) -> Unit,
    onStepNext: () -> Unit,
    onReaderImageLoaderReady: (coil3.ImageLoader?) -> Unit,
) {
    val currentPageNow by rememberUpdatedState(currentPage)
    val aiAnyOn = prefs.aiArtifact || prefs.aiDescreen || prefs.aiUpscale

    DisposableEffect(currentBookKey) {
        DesktopReaderPreferences.bindBook(currentBookKey)
        onDispose {
            desktopComicTileCacheClear()
            DesktopReaderPreferences.bindBook(null)
        }
    }

    val platformImageLoader = LocalUxPlatformServices.current.imageLoader()
    val singletonImageLoader = remember {
        runCatching { SingletonImageLoader.get(PlatformContext.INSTANCE) }.getOrNull()
    }
    val readerImageLoader = platformImageLoader ?: singletonImageLoader
    onReaderImageLoaderReady(readerImageLoader)

    val purgeGpuCache = LocalGpuCachePurge.current
    DisposableEffect(purgeGpuCache) {
        onDispose { purgeGpuCache?.invoke("reader_dispose") }
    }

    LaunchedEffect(currentPage, pages, prefs.preloadCount, viewportSize, aiAnyOn) {
        val coilLoader = readerImageLoader ?: return@LaunchedEffect
        if (viewportSize.width <= 0 || viewportSize.height <= 0) return@LaunchedEffect
        val sizeW = viewportSize.width
        val sizeH = viewportSize.height
        computePreloadIndices(
            currentPage = currentPage,
            totalPages = pages.size,
            preloadAhead = prefs.preloadCount.coerceIn(1, 4),
            preloadBehind = 2,
        ).forEach { idx ->
            val model: Any = buildReaderAiPage(pages[idx], prefs) ?: pages[idx]
            coilLoader.enqueue(
                ImageRequest.Builder(PlatformContext.INSTANCE)
                    .data(model)
                    .size(sizeW, sizeH)
                    .build(),
            )
        }
        if (pages.isNotEmpty() && currentPage >= pages.size - 3 && canNextChapter) {
            onPreloadNextChapter?.invoke()
        }
    }

    LaunchedEffect(boundaryHint, boundaryHintAt) {
        if (boundaryHint != null) {
            delay(2_000)
            onClearBoundaryHint()
        }
    }

    LaunchedEffect(chromeVisible, panel, jumpDialogOpen, lastPointerMoveTick) {
        if (chromeVisible && panel == ReaderPanel.None && !jumpDialogOpen) {
            delay(4_000)
            onHideChrome()
        }
    }

    LaunchedEffect(pages.size, readerMode, episodeKey) {
        if (pages.isEmpty()) return@LaunchedEffect
        val carried = if (landedForThisEpisode) currentPage else startPage
        if (paged) {
            val groupStart = if (isDouble) {
                doubleGroups.indexOfFirst { carried in it }.coerceAtLeast(0)
            } else {
                carried
            }
            println(desktopApplyReaderStartPagePager(pagerState, groupStart, pagerGroupCount))
        } else if (horizontalContinuous) {
            println(desktopApplyReaderStartPage(rowState, carried, pages.size))
        } else {
            println(desktopApplyReaderStartPage(listState, carried, pages.size))
        }
        onLanded()
    }

    LaunchedEffect(prefs.pageSoundOn) {
        DesktopReaderPageSound.setEnabled(prefs.pageSoundOn)
    }
    LaunchedEffect(prefs.soundVolume) {
        DesktopReaderPageSound.setVolume(prefs.soundVolume)
    }

    LaunchedEffect(listState, pages.size, paged) {
        if (paged) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { idx ->
                if (idx != currentPageNow) {
                    onPageChange(idx)
                    DesktopReaderPageSound.play()
                }
            }
    }
    LaunchedEffect(rowState, pages.size, horizontalContinuous) {
        if (!horizontalContinuous) return@LaunchedEffect
        snapshotFlow { rowState.firstVisibleItemIndex }
            .collect { idx ->
                if (idx != currentPageNow) {
                    onPageChange(idx)
                    DesktopReaderPageSound.play()
                }
            }
    }
    LaunchedEffect(pagerState, pages.size, paged, isDouble, doubleGroups) {
        if (!paged) return@LaunchedEffect
        snapshotFlow { pagerState.settledPage }
            .collect { group ->
                val logical = if (isDouble) {
                    doubleGroups.getOrNull(group)?.firstOrNull { it != BLANK_PAGE } ?: 0
                } else {
                    group
                }
                if (logical != currentPage) {
                    onPageChange(logical)
                    DesktopReaderPageSound.play()
                    onResetZoomPan()
                }
            }
    }

    val blankCursor = remember {
        runCatching {
            java.awt.Toolkit.getDefaultToolkit().createCustomCursor(
                BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB),
                Point(0, 0),
                "venera-blank",
            )
        }.getOrNull()
    }
    LaunchedEffect(chromeVisible, panel) {
        runCatching {
            val awt = Window.getWindows().firstOrNull { it.isShowing } ?: return@runCatching
            if (blankCursor != null) {
                awt.cursor = if (!chromeVisible && panel == ReaderPanel.None) blankCursor else Cursor.getDefaultCursor()
            }
        }
    }

    LaunchedEffect(autoPlay, autoPlaySecs, readerMode, pages.size, episodeKey) {
        if (!autoPlay || pages.isEmpty()) return@LaunchedEffect
        while (true) {
            delay(autoPlaySecs * 1000L)
            if (panel != ReaderPanel.None) continue
            if (paged) {
                if (desktopReaderStepTarget(currentPageNow, 1, readerMode, pages.lastIndex) == null) {
                    onAutoPlayChange(false)
                    break
                }
                onStepNext()
            } else {
                if (!listState.canScrollForward) {
                    onAutoPlayChange(false)
                    break
                }
                listState.dispatchRawDelta(480f)
            }
        }
    }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    DisposableEffect(Unit) {
        val w = Window.getWindows().firstOrNull { it.isShowing }
        val l = object : WindowFocusListener {
            override fun windowGainedFocus(e: WindowEvent) {
                runCatching { focus.requestFocus() }
            }

            override fun windowLostFocus(e: WindowEvent) = Unit
        }
        w?.addWindowFocusListener(l)
        onDispose { w?.removeWindowFocusListener(l) }
    }
}
