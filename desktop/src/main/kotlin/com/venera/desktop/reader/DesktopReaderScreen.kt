package com.venera.desktop.reader

import com.venera.desktop.ui.theme.VeneraType
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import coil3.toBitmap
import org.jetbrains.skiko.toBufferedImage
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.isCtrlPressed
import kotlin.math.roundToInt
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.gallery.data.DesktopComicPage
import com.venera.desktop.gallery.data.DesktopReaderMode
import com.venera.desktop.gallery.data.desktopComicTileCacheClear
import com.venera.desktop.gallery.ui.DESKTOP_READER_MODE_CHOICES
import com.venera.desktop.gallery.ui.desktopApplyReaderStartPage
import com.venera.desktop.gallery.ui.desktopApplyReaderStartPagePager
import com.venera.desktop.gallery.ui.desktopReaderIsPaged
import com.venera.desktop.gallery.ui.desktopReaderReverses
import com.venera.desktop.gallery.ui.desktopReaderStepTarget
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.Cursor
import java.awt.Point
import java.awt.image.BufferedImage




/**
 * 桌面漫画阅读器（阅读器重做批）：按
 * `docs/designs/desktop-reader-2026-10-31.html` 重写 UI，旧三行控制岛整批退役。
 * 模式语义的出处是 Suwayomi-JUI 的 `Direction/ImageScale/NavigationMode`（MPL-2.0，
 * 只借语义不搬实现——取图走我们自己的 `DesktopComicPageFetcher` 链）。
 *
 * ## 参数面一字未动
 *
 * 壳的接线（取页/写历史那两枚 effect 与渲染分支）、`DesktopComicReader.kt` 的纯函数件、
 * 落起点那圈 effect 全部原样：这一批换的只是 **chrome 与交互**。
 *
 * ## 这一批新增
 *
 * - **对开双页上屏**：`DOUBLE_PAGE` 进档位表（算式批 87 就留着），一组两页、组首对齐；
 * 对开只做左至右序（枚举里没有 RTL 变体，不造半套）。
 * - **点击热区**：左右各 26% 翻页（RTL 自动镜像），中央双击缩放；抽屉里一枚开关可整个关掉。
 * - **沉浸态**：鼠标/按键静止 2 秒 → 上下栏淡出 + 光标隐藏；任何输入立即唤回。
 * - **缩放三档 + 自由缩放**：适应屏幕（默认）/ 适应宽度 / 原始；滚轮自由缩放（0.5×–4×，
 *   以光标为锚），双击 200% 往返，放大后拖拽平移。
 * - **设置抽屉**（右侧 300dp，阅读区收窄不遮画面）：模式 / 方向 / 适配 / 背景色三档 /
 *   亮度压暗 / 热区开关。**没有实现的那几行（预取数、侧键翻页、过渡动画）整行不摆** —— 假开关禁令。
 * - **快捷键扩展**：<kbd>A/D</kbd> 翻页、<kbd>Home/End</kbd> 首/末页、<kbd>[ ]</kbd> 与 <kbd>, .</kbd> 切话、
 *   <kbd>Space/Shift+Space</kbd> 整页步进或滚动一屏。
 *
 * ## 刻意没做（记在账上，不装做了）
 *
 * 背景色/适配档只活在本会话（没有偏好写口，不造第二颗盘上账）；双页"封面独占右页"的位移对齐、
 * 跨页合并（源未标记 wide 页）未做；音量键/侧键翻页是 Android 交互，桌面没有那两个键位。
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
    /** 作品题名（顶栏面包屑那格；装配点从路由递，缺省只剩话名不空报）。 */
    comicTitle: String? = null,
    /** 源名（面包屑尾格）。 */
    sourceKey: String? = null,
    /** 读到本话末尾时预加载下一话的可选回调。 */
    onPreloadNextChapter: (() -> Unit)? = null,
) {
    val tokens = VeneraTokens
    val prefs by DesktopReaderPreferences.state.collectAsState()
    // 双页总开关：关=每次只显示一页（对开档降级为单页横读）。
    val readerModeNow = when {
        !prefs.doublePageOn && readerMode == DesktopReaderMode.DOUBLE_PAGE -> DesktopReaderMode.HORIZONTAL_LTR
        else -> readerMode
    }
    // 方向由档位自己说，「倒序」只在横向翻页档上取反。旧口径是反向强制（非倒序就把 RTL 折回 LTR），
    // 存下来的 RTL 档因此永远不生效，日漫右至左成了选不中的档。
    val readerMode = when {
        !prefs.reverseReading -> readerModeNow
        readerModeNow == DesktopReaderMode.HORIZONTAL_LTR -> DesktopReaderMode.HORIZONTAL_RTL
        readerModeNow == DesktopReaderMode.HORIZONTAL_RTL -> DesktopReaderMode.HORIZONTAL_LTR
        else -> readerModeNow
    }
    val paged = desktopReaderIsPaged(readerMode)
    // 条漫排版细节（版芯宽度、页间隙）只认垂直连续档，不再另占一个开关去压档位。
    val webtoonNow = readerMode == DesktopReaderMode.VERTICAL_CONTINUOUS
    // 平滑档：横向连续、不吸附。与翻页档的区别只在松手停不停在页边界。
    // 该档未进 DESKTOP_READER_MODE_CHOICES，屏上摆不出，这条路径当前不可达；算式留着对齐档位表。
    val horizontalContinuous = readerMode == DesktopReaderMode.HORIZONTAL_CONTINUOUS
    // 切档一律先清倒序：档位自己带方向，倒序只在横向档上取反。
    // 不清掉它，刚写进去的 RTL 会被再翻一次落成 LTR，屏上表现就是点了方向不变。
    val pickModeResolved: ((DesktopReaderMode) -> Unit)? = remember(onPickMode) {
        onPickMode?.let { pick ->
            { target: DesktopReaderMode ->
                DesktopReaderPreferences.update { it.copy(reverseReading = false) }
                pick(target)
            }
        }
    }
    val rowState = rememberLazyListState(initialFirstVisibleItemIndex = startPage.coerceAtLeast(0))
    val isDouble = readerMode == DesktopReaderMode.DOUBLE_PAGE
    val lastIndex = pages.lastIndex
    val filterPresets by DesktopReaderPreferences.filterPresets.collectAsState()
    val bookmarksVersion by DesktopReaderPreferences.bookmarksVersion.collectAsState()
    val pageRatios = remember(pages) { mutableStateMapOf<Int, Float>() }
    // 宽图集合在下面按当前 prefs 现算：keepWideSingle / rotateH 任一变化都要重建双页分组才生效。
    val doubleGroups = remember(pages.size, prefs.coverSinglePage, pageRatios, prefs.alignNextHorizontal, prefs.blankPage, prefs.keepWideSingle, prefs.rotateH) {
        buildDoublePageGroups(
            pageCount = pages.size,
            coverSinglePage = prefs.coverSinglePage,
            widePageIndices = pageRatios.entries
                .filter { (_, ratio) ->
                    // 原始横竖（ratio>1）按 rotateH 档翻转：90°（档 1/2）后视觉宽高对调；正方形不判宽。
                    val rotatedWide = if (prefs.rotateH == 1 || prefs.rotateH == 2) ratio < 1f else ratio > 1f
                    prefs.keepWideSingle && rotatedWide
                }
                .mapTo(mutableSetOf()) { it.key },
            alignWithNextHorizontal = prefs.alignNextHorizontal,
            leadingBlankPage = prefs.blankPage,
        )
    }
    val pagerGroupCount = if (isDouble) doubleGroups.size else pages.size
    // 导览视图把整组当一屏：单页就是自己，双页是组内两页（按渲染序，左在前）。
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
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startPage.coerceAtLeast(0))
    val focus = remember { FocusRequester() }
    val initialGroup = if (isDouble) {
        doubleGroups.indexOfFirst { startPage.coerceAtLeast(0) in it }.coerceAtLeast(0)
    } else {
        startPage.coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(
        initialPage = initialGroup,
        pageCount = { pagerGroupCount },
    )
    val scope = rememberCoroutineScope()

    // 瓦片缓存跟屏走；绑定当前漫画专属偏好，离屏自动复位全局
    val currentBookKey = if (!sourceKey.isNullOrBlank() && !comicTitle.isNullOrBlank()) "$sourceKey:$comicTitle" else null
    DisposableEffect(currentBookKey) {
        DesktopReaderPreferences.bindBook(currentBookKey)
        onDispose {
            desktopComicTileCacheClear()
            DesktopReaderPreferences.bindBook(null)
        }
    }

    // ── chrome / 按需面板（默认全屏纯享画布，无常驻侧栏）──
    // 面板互斥由类型保证：始终只可能有一个 ReaderPanel，不再用手写 if 互相清零。
    var chromeVisible by remember { mutableStateOf(true) }
    var panel by remember { mutableStateOf(ReaderPanel.None) }
    // 设置弹窗定位 tab（0=电子书 1=页 2=筛选 3=AI）：顶栏四段钮直接落对应段。
    var settingsTab by remember { mutableIntStateOf(1) }
    var jumpDialogOpen by remember { mutableStateOf(false) }
    // 放大镜开关落进偏好：进阅读器时按上次的状态恢复，不再每次都是关的。
    val magnifierOn = prefs.magnifierOn
    var pointerPos by remember { mutableStateOf(Offset.Zero) }
    var viewportSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    // 验收期临时读数：根画布在窗口里的位置，与镜框窗口位置对账用，验收通过即删。
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    // 视口内容层在根画布里的真实位置。放大镜的锚点换算只认这个实测值，
    // 不再按 strip/marginH 手工推导——padding 一改，推导就和实际错开。
    var viewportOriginInRoot by remember { mutableStateOf(Offset.Zero) }
    // 旋转落进偏好：转过一次之后，下次进阅读器仍按这个角度显示。
    val rotationDegrees = (prefs.rotationIndex and 3) * 90
    var reloadTick by remember { mutableIntStateOf(0) }
    var boundaryHint by remember { mutableStateOf<String?>(null) }
    var boundaryDirection by remember { mutableIntStateOf(0) }
    var boundaryHintAt by remember { mutableLongStateOf(0L) }

    // ── 持久化偏好 ──
    // 适配档位是唯一真源：原始档就是「按原始像素显示」，不再另设一个总闸去否掉档位。
    val fit = ReaderFit.entries.getOrElse(prefs.fitIndex) { ReaderFit.FIT_SCREEN }
    // 镜内重挂用：各渲染分支就地赋值（见渲染段），镜头浮层读取。
    var lensViewportRef: (@Composable () -> Unit)? = null
    val lensRtl = desktopReaderReverses(readerMode)
    val prefBackground by com.venera.desktop.settings.DesktopReaderBackground.index.collectAsState()
    var background by remember(prefBackground) { mutableIntStateOf(prefBackground) }
    val arrowKeysOn = prefs.arrowKeysOn
    var autoPlay by remember { mutableStateOf(false) }
    val autoPlaySecs = prefs.autoPlaySecs
    // 新口径滤镜链（筛选 tab）：0–400% 三滑条 + 色相/sepia 强度 + 黑白/负片，
    // remember 一次算好单一 ColorMatrix 喂现有滤镜路径（规格：禁止每页重复计算）。
    val colorMatrix = remember(
        prefs.brightness,
        prefs.contrast,
        prefs.saturation,
        prefs.filterHue,
        prefs.filterSepia,
        prefs.filterInvert,
        prefs.filterNegative,
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
    val activeColorFilter = remember(colorMatrix) { colorMatrix?.let { androidx.compose.ui.graphics.ColorFilter.colorMatrix(it) } }
    // AI 前置管线参数工厂：任一开关开时对逻辑页构造带参数指纹的 DesktopAiPage；
    // 全关时回 null（走普通页模型，不白算）。指纹进 Coil 缓存键，参数一动旧结果失配重算。
    val aiAnyOn = prefs.aiArtifact || prefs.aiDescreen || prefs.aiUpscale
    fun aiParamsFor(page: DesktopComicPage?): DesktopAiPage? {
        if (!aiAnyOn || page == null) return null
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
    // 插值质量映射一次算好；全部页格与连读 AsyncImage 共用（3.7 假开关转真口径）。
    val cellFilterQuality = remember(prefs.filterQualityIndex) { resolveFilterQuality(prefs.filterQualityIndex) }
    val bookmarks = remember(episodeKey, bookmarksVersion) { DesktopReaderPreferences.bookmarksOf(episodeKey) }
    val isCurrentBookmarked = currentPage in bookmarks
    // 倍率走插值（双击/滚轮放大是"动画过去"的）；平移**不插值**：
    // 拖拽每帧写目标，再叠一层 150ms 补间就会永远追不上鼠标，手感发黏。
    var zoomTarget by remember { mutableFloatStateOf(1f) }
    var panTarget by remember { mutableStateOf(Offset.Zero) }
    val zoom by androidx.compose.animation.core.animateFloatAsState(
        targetValue = zoomTarget,
        animationSpec = androidx.compose.animation.core.tween(150),
    )
    val pan = panTarget

    // 翻页动画只跟滚动进度走：落定后再补一段会变成"停住又缩一下"，
    // 所以不再单独维护落定进度，由 jumpToLogical 给近距离翻页固定时长的过渡。

    // 真实尺寸只有加载完才知道：宽高比决定双页贴合的宽度，横图决定独占分组。
    // 宽图集合不在写入处定死：recordPageMetrics 只存原始比例，消费处按当前 keepWideSingle / rotateH 现算。
    fun recordPageMetrics(idx: Int, w: Int, h: Int) {
        if (idx < 0 || w <= 0 || h <= 0) return
        pageRatios[idx] = w.toFloat() / h.toFloat()
    }

    // 横图旋转档（rotateH）：仅作用于宽>高的页；0/1/2/3 = 0°/90°左/90°右/180°。
    fun horizontalPageRotation(idx: Int): Float {
        if (prefs.rotateH == 0) return 0f
        val wide = (pageRatios[idx] ?: 0f) > 1f
        if (!wide) return 0f
        return when (prefs.rotateH) {
            1 -> -90f
            2 -> 90f
            3 -> 180f
            else -> 0f
        }
    }

    val canvasBg = com.venera.desktop.settings.DesktopReaderBackground.colorOf(background)

    // 落点账（原样保留）：本话落过一次起点后，换档重跑都落回人现在站的那一页。
    var landedForThisEpisode by remember(episodeKey) { mutableStateOf(false) }

    val canPrevChapter = episodes != null && onGoChapter != null && chapterIndex > 0
    val canNextChapter = episodes != null && onGoChapter != null && chapterIndex in 0 until episodes.lastIndex

    // 自动翻页等**长命 effect** 里读的当前页要吃最新值（捕获的形参会停在发射那一刻）。
    val currentPageNow by androidx.compose.runtime.rememberUpdatedState(currentPage)
    val tapZoneNow by androidx.compose.runtime.rememberUpdatedState(prefs.tapZonePercent / 100f)
    // 导览视图聚焦态：true=分格聚焦，false=整页。切页/退出后复位。
    var guidedViewFocused by remember { mutableStateOf(true) }
    var guidedPanelIndex by remember { mutableIntStateOf(0) }
    var guidedStepCount by remember { mutableIntStateOf(1) }
    LaunchedEffect(currentPage, prefs.turnEffect) {
        guidedPanelIndex = 0
        guidedViewFocused = true
    }

    // 双向预加载（前后至少各 6 页），读到末尾 3 页内触发下一话预加载。
    // 桌面侧 `DesktopUxPlatformServices` 是 `by NoOpUxPlatformServices` 委托且没覆盖 `imageLoader()`，
    // 拿到的恒为 null；不兜到 Coil 进程单例，下面那句 null 早退会让预加载一行都跑不到 ——
    // 而显示侧的 `AsyncImage` 走的正是这颗单例，两条轨不同源等于没预取。
    val platformImageLoader = com.venera.compose.components.LocalUxPlatformServices.current.imageLoader()
    val singletonImageLoader = remember {
        runCatching { coil3.SingletonImageLoader.get(coil3.PlatformContext.INSTANCE) }.getOrNull()
    }
    val readerImageLoader = platformImageLoader ?: singletonImageLoader
    val purgeGpuCache = com.venera.desktop.LocalGpuCachePurge.current
    DisposableEffect(purgeGpuCache) {
        onDispose { purgeGpuCache?.invoke("reader_dispose") }
    }
    LaunchedEffect(currentPage, pages, prefs.preloadCount, viewportSize, aiAnyOn) {
        val coilLoader = readerImageLoader ?: return@LaunchedEffect
        // 尺寸没量到就先不取：Coil 的缓存键含解码尺寸，拿兜底尺寸预取等于换一把键，
        // 显示侧真量到之后仍会重新解码。`viewportSize` 在 key 里，量到会自然重跑。
        if (viewportSize.width <= 0 || viewportSize.height <= 0) return@LaunchedEffect
        // 预取必须带上与显示一致的尺寸与模型（含 AI 管线），否则翻页时缓存不命中会重新加载。
        val sizeW = viewportSize.width
        val sizeH = viewportSize.height
        computePreloadIndices(
            currentPage = currentPage,
            totalPages = pages.size,
            preloadAhead = prefs.preloadCount.coerceIn(1, 4),
            preloadBehind = 2,
        ).forEach { idx ->
            val model: Any = aiParamsFor(pages[idx]) ?: pages[idx]
            coilLoader.enqueue(
                coil3.request.ImageRequest.Builder(coil3.PlatformContext.INSTANCE)
                    .data(model)
                    .size(sizeW, sizeH)
                    .build(),
            )
        }
        if (pages.isNotEmpty() && currentPage >= pages.size - 3 && canNextChapter) {
            onPreloadNextChapter?.invoke()
        }
    }

    // 末页/首页二次确认提示 2 秒后自动消失。
    LaunchedEffect(boundaryHint, boundaryHintAt) {
        if (boundaryHint != null) {
            kotlinx.coroutines.delay(2_000)
            boundaryHint = null
            boundaryDirection = 0
        }
    }

    // 工具栏无操作自动隐藏（4 秒），鼠标移动或抽屉展开时重置计时。
    var lastPointerMoveTick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(chromeVisible, panel, jumpDialogOpen, lastPointerMoveTick) {
        if (chromeVisible && panel == ReaderPanel.None && !jumpDialogOpen) {
            kotlinx.coroutines.delay(4_000)
            chromeVisible = false
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
                // 近距离翻页动画档固定走一段过渡：Pager 默认弹跳一闪而过，等于没播。
                // 远距离滚过几十页会拖得难受，仍瞬切；无动画档才按"平滑翻页"开关走。
                val nearGroup = kotlin.math.abs(targetGroup - pagerState.currentPage) <= 3
                val turnSpec = androidx.compose.animation.core.tween<Float>(
                    durationMillis = (prefs.turnAnimSeconds * 1000f).roundToInt().coerceAtLeast(1),
                )
                when {
                    nearGroup && prefs.turnEffect != 0 -> pagerState.animateScrollToPage(
                        targetGroup,
                        animationSpec = turnSpec,
                    )
                    nearGroup && prefs.smoothTurning -> pagerState.animateScrollToPage(targetGroup)
                    else -> pagerState.scrollToPage(targetGroup)
                }
            } else if (horizontalContinuous) {
                if (prefs.smoothTurning && kotlin.math.abs(clamped - rowState.firstVisibleItemIndex) <= 3) {
                    rowState.animateScrollToItem(clamped)
                } else {
                    rowState.scrollToItem(clamped)
                }
            } else {
                if (prefs.smoothTurning && kotlin.math.abs(clamped - listState.firstVisibleItemIndex) <= 3) {
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
                desktopReaderStepTarget(currentPageNow, delta, readerMode, lastIndex)
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
                boundaryHint = com.venera.desktop.settings.DesktopI18n.tr("已到本话末页，再按一次进入下一话", "End of chapter, press again for next")
            }
        } else if (delta < 0 && canPrevChapter) {
            if (boundaryDirection == -1 && now - boundaryHintAt <= 2_000L) {
                boundaryHint = null
                boundaryDirection = 0
                onGoChapter?.invoke(chapterIndex - 1)
            } else {
                boundaryDirection = -1
                boundaryHintAt = now
                boundaryHint = com.venera.desktop.settings.DesktopI18n.tr("已到本话首页，再按一次返回上一话", "Start of chapter, press again for previous")
            }
        }
    }

    // ── 落起点（键与口径原样：页表从空到有那一刻落一次；切话必落）──
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
        landedForThisEpisode = true
    }

    LaunchedEffect(prefs.pageSoundOn) {
        DesktopReaderPageSound.setEnabled(prefs.pageSoundOn)
    }
    LaunchedEffect(prefs.soundVolume) {
        DesktopReaderPageSound.setVolume(prefs.soundVolume)
    }

    // ── 滚动/翻页 → 当前页（settled 口径原样；对开换算回逻辑页号）──
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
                    // 翻页复位几何：否则下一页会继承上一页的放大倍率与偏移，看起来像"跳歪了"。
                    if (zoomTarget > 1.01f || panTarget != Offset.Zero) {
                        zoomTarget = 1f
                        panTarget = Offset.Zero
                    }
                }
            }
    }

    // ── 光标沉浸控制：当控制器和抽屉全部收起时隐藏鼠标指针；展开时恢复 ──
    val blankCursor = remember {
        runCatching {
            java.awt.Toolkit.getDefaultToolkit().createCustomCursor(
                BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), Point(0, 0), "venera-blank",
            )
        }.getOrNull()
    }
    LaunchedEffect(chromeVisible, panel) {
        runCatching {
            val awt = java.awt.Window.getWindows().firstOrNull { it.isShowing } ?: return@runCatching
            if (blankCursor != null) {
                awt.cursor = if (!chromeVisible && panel == ReaderPanel.None) blankCursor else Cursor.getDefaultCursor()
            }
        }
    }

    // ── 自动翻页播放：整页翻档步进、连读档滚一屏；到底/开抽屉即停 ──
    LaunchedEffect(autoPlay, autoPlaySecs, readerMode, pages.size, episodeKey) {
        if (!autoPlay || pages.isEmpty()) return@LaunchedEffect
        while (true) {
            delay(autoPlaySecs * 1000L)
            if (panel != ReaderPanel.None) continue
            if (paged) {
                if (desktopReaderStepTarget(currentPageNow, 1, readerMode, pages.lastIndex) == null) {
                    autoPlay = false
                    break
                }
                step(1)
            } else {
                if (!listState.canScrollForward) {
                    autoPlay = false
                    break
                }
                listState.dispatchRawDelta(480f)
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    // 切走再切回时 Compose 焦点不会自己回来；窗口获焦即把键盘焦点还给画布。
    DisposableEffect(Unit) {
        val w = java.awt.Window.getWindows().firstOrNull { it.isShowing }
        val l = object : java.awt.event.WindowFocusListener {
            override fun windowGainedFocus(e: java.awt.event.WindowEvent) {
                runCatching { focus.requestFocus() }
            }

            override fun windowLostFocus(e: java.awt.event.WindowEvent) = Unit
        }
        w?.addWindowFocusListener(l)
        onDispose { w?.removeWindowFocusListener(l) }
    }

    // 安全区不是可选余量：浮层悬浮后双页按最大宽展开会钻到玻璃底下，这里按显隐态实时收缩。
    com.venera.desktop.ui.RegisterDesktopShortcutHandler(
        pages.size,
        readerMode,
        arrowKeysOn,
        panel,
        jumpDialogOpen,
        magnifierOn,
        chromeVisible,
        chapterIndex,
        priority = 300,
    ) { signal ->
        when (com.venera.desktop.ui.matchViewerExtraShortcut(signal)) {
            com.venera.desktop.ui.DesktopViewerExtraAction.ZOOM_IN -> {
                zoomTarget = (zoomTarget * 1.25f).coerceIn(READER_MIN_ZOOM, if (prefs.noEnlarge) 1f else READER_MAX_ZOOM)
                return@RegisterDesktopShortcutHandler true
            }
            com.venera.desktop.ui.DesktopViewerExtraAction.ZOOM_OUT -> {
                val next = (zoomTarget / 1.25f).coerceIn(READER_MIN_ZOOM, if (prefs.noEnlarge) 1f else READER_MAX_ZOOM)
                zoomTarget = next
                if (next <= 1.01f) panTarget = Offset.Zero
                return@RegisterDesktopShortcutHandler true
            }
            com.venera.desktop.ui.DesktopViewerExtraAction.ZOOM_RESET -> {
                zoomTarget = 1f
                panTarget = Offset.Zero
                return@RegisterDesktopShortcutHandler true
            }
            com.venera.desktop.ui.DesktopViewerExtraAction.FIT_SCREEN -> {
                // 只在前两档之间轮：原始档会让整页按像素显示，在 4K 屏上看着像"缩水了"，放进设置里选。
                val nextIdx = if (prefs.fitIndex == 0) 1 else 0
                DesktopReaderPreferences.update { it.copy(fitIndex = nextIdx) }
                zoomTarget = 1f
                panTarget = Offset.Zero
                return@RegisterDesktopShortcutHandler true
            }
            com.venera.desktop.ui.DesktopViewerExtraAction.ROTATE_OR_MODE -> {
                DesktopReaderPreferences.update { it.copy(rotationIndex = (it.rotationIndex + 1) and 3) }
                return@RegisterDesktopShortcutHandler true
            }
            null -> Unit
        }
        when (signal.keyCode) {
            java.awt.event.KeyEvent.VK_M -> { DesktopReaderPreferences.update { it.copy(magnifierOn = !it.magnifierOn) }; true }
            java.awt.event.KeyEvent.VK_G -> { if (pages.isNotEmpty()) jumpDialogOpen = !jumpDialogOpen; true }
            java.awt.event.KeyEvent.VK_B -> {
                if (pages.isNotEmpty()) DesktopReaderPreferences.toggleBookmark(episodeKey, currentPageNow)
                true
            }
            java.awt.event.KeyEvent.VK_RIGHT, java.awt.event.KeyEvent.VK_DOWN -> {
                if (arrowKeysOn && panel == ReaderPanel.None && !jumpDialogOpen && !magnifierOn && !signal.isAlt) {
                    step(1)
                    true
                } else {
                    false
                }
            }
            java.awt.event.KeyEvent.VK_LEFT, java.awt.event.KeyEvent.VK_UP -> {
                if (arrowKeysOn && panel == ReaderPanel.None && !jumpDialogOpen && !magnifierOn && !signal.isAlt) {
                    step(-1)
                    true
                } else {
                    false
                }
            }
            java.awt.event.KeyEvent.VK_PAGE_DOWN, java.awt.event.KeyEvent.VK_D -> { step(1); true }
            java.awt.event.KeyEvent.VK_PAGE_UP, java.awt.event.KeyEvent.VK_A -> { step(-1); true }
            java.awt.event.KeyEvent.VK_HOME -> { if (pages.isNotEmpty()) jumpToLogical(0); true }
            java.awt.event.KeyEvent.VK_END -> { if (pages.isNotEmpty()) jumpToLogical(lastIndex); true }
            java.awt.event.KeyEvent.VK_SPACE -> {
                val d = if (signal.isShift) -1 else 1
                if (paged) { step(d) } else {
                    val t = (currentPage + d).coerceIn(0, maxOf(lastIndex, 0))
                    if (t != currentPage) jumpToLogical(t)
                }
                true
            }
            java.awt.event.KeyEvent.VK_ESCAPE -> {
                // 导览聚焦态先退回整页，再走常规 Esc 链。
                if (prefs.turnEffect == 2 && guidedViewFocused) {
                    guidedViewFocused = false
                    runCatching { focus.requestFocus() }
                    true
                } else if (jumpDialogOpen || magnifierOn || panel != ReaderPanel.None) {
                    jumpDialogOpen = false
                    DesktopReaderPreferences.update { it.copy(magnifierOn = false) }
                    panel = ReaderPanel.None
                    runCatching { focus.requestFocus() }
                    true
                } else if (chromeVisible) {
                    chromeVisible = false
                    runCatching { focus.requestFocus() }
                    true
                } else {
                    onBack()
                    true
                }
            }
            java.awt.event.KeyEvent.VK_COMMA -> {
                if (onGoChapter != null && canPrevChapter) onGoChapter(chapterIndex - 1); true
            }
            java.awt.event.KeyEvent.VK_PERIOD -> {
                if (onGoChapter != null && canNextChapter) onGoChapter(chapterIndex + 1); true
            }
            else -> false
        }
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(canvasBg)
            // 方向键在根节点预览阶段截走：顶栏按钮、底部进度条这些控件自己也会吃方向键，
            // 焦点一旦落在它们身上，画布上的处理器就收不到，表现为"按键时灵时不灵"。
            .onPreviewKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown && arrowKeysOn && panel == ReaderPanel.None &&
                    !jumpDialogOpen && !magnifierOn
                ) {
                    when (ev.key) {
                        Key.DirectionRight, Key.DirectionDown -> {
                            step(1)
                            true
                        }
                        Key.DirectionLeft, Key.DirectionUp -> {
                            step(-1)
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
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
            onToggleBookmark = {
                if (pages.isNotEmpty()) DesktopReaderPreferences.toggleBookmark(episodeKey, currentPage)
            },
            onOpenBookmarkList = {
                panel = if (panel == ReaderPanel.Chapters) ReaderPanel.None else ReaderPanel.Chapters
            },
            onOpenSettingsTab = { tab ->
                settingsTab = tab
                panel = if (panel == ReaderPanel.Settings && settingsTab == tab) ReaderPanel.None else ReaderPanel.Settings
            },
            reverseReading = prefs.reverseReading,
            onToggleReverse = {
                DesktopReaderPreferences.update { it.copy(reverseReading = !it.reverseReading) }
            },
            autoPlaySecs = prefs.autoPlaySecs,
            onCycleAutoPlay = {
                DesktopReaderPreferences.update {
                    it.copy(autoPlaySecs = when (it.autoPlaySecs) {
                        2 -> 4
                        4 -> 8
                        else -> 2
                    })
                }
            },
            onToggleShowPageNumber = {
                DesktopReaderPreferences.update { it.copy(showPageNumber = !it.showPageNumber) }
            },
            onHideTopBar = { chromeVisible = false },
            // 顶栏交互后把焦点拉回画布：否则焦点留在按钮上，按键翻页会失灵。
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
            // 按键统一走窗口级总线（RegisterDesktopShortcutHandler），避免焦点落在顶栏/侧栏时方向键失效。
            // 桌面标准滚轮：滚轮以光标为中心缩放（切页只走方向键/点击热区/翻页钮）；移动鼠标重置 HUD 自动隐藏计时
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
                                // 浮层开着、指针落在左侧缩略条上时一律放行，绝不 consume，
                                // 否则设置面板、缩略图栏和章节列表都无法滚轮滑动。
                                val overlayOpen = panel != ReaderPanel.None || jumpDialogOpen || magnifierOn
                                val onCanvas = change != null && change.position.x >= stripPx
                                if (!overlayOpen && onCanvas && change != null && pages.isNotEmpty()) {
                                    val delta = change.scrollDelta.y
                                    if (delta != 0f) {
                                        val ctrlHeld = ev.keyboardModifiers.isCtrlPressed
                                        if (paged || ctrlHeld) {
                                            // 分页模式（导览视图/淡出/翻页）直接滚轮缩放；连续模式按住 Ctrl+滚轮缩放
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
            // 放大后的拖拽平移由每页内容容器统一处理（见 HorizontalPager 的页外层），
            // 这里不再重复挂手势：两层同时写 pan 会互相打架。
            // 单击左右 15% 边缘翻页、中间切换 HUD；双击以光标位置为锚点快速放大/还原
            .pointerInput(paged, pages.size, readerMode) {
                // 内容区 = 根 Box 去掉左侧栏；边缘判定与缩放锚点都换算到内容坐标。
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
                                val rtl = desktopReaderReverses(readerMode)
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
            // 边缘拖拽跟随翻页（dragTurnOn）：paged 档左右 15% 热区内拖拽增量按 0.5 系数
            // 映射 pager offset，松手过半翻页、否则弹回；放大态与浮层打开时不抢手势。
            .pointerInput(paged, prefs.dragTurnOn, pages.size, readerMode) {
                if (!paged || !prefs.dragTurnOn || pages.isEmpty()) return@pointerInput
                val stripPx = LEFT_STRIP_WIDTH.toPx()
                val contentWidth = (size.width - stripPx).coerceAtLeast(1f)
                var dragAccum = 0f
                var inHotzone = false
                var zoneSign = 0
                awaitPointerEventScope {
                    while (true) {
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                        val change = ev.changes.firstOrNull() ?: continue
                        when (ev.type) {
                            PointerEventType.Press -> {
                                dragAccum = 0f
                                inHotzone = false
                                zoneSign = 0
                            }
                            PointerEventType.Move -> {
                                if (!change.pressed) continue
                                if (zoomTarget > 1.01f || panel != ReaderPanel.None ||
                                    jumpDialogOpen || magnifierOn
                                ) continue
                                val localX = change.position.x - stripPx
                                val ratio = localX / contentWidth
                                if (!inHotzone) {
                                    if (ratio < 0.15f) {
                                        inHotzone = true; zoneSign = -1
                                    } else if (ratio > 0.85f) {
                                        inHotzone = true; zoneSign = 1
                                    }
                                }
                                if (inHotzone) {
                                    val d = change.positionChange().x
                                    if (d != 0f) {
                                        dragAccum += d
                                        // 系数 0.5：拖 2 页宽走 1 页 offset
                                        val rtl = desktopReaderReverses(readerMode)
                                        val pageWidth = contentWidth
                                        scope.launch {
                                            pagerState.scrollToPage(
                                                pagerState.currentPage - (dragAccum / pageWidth * 0.5f)
                                                    .let { if (rtl) -it else it }.toInt(),
                                            )
                                        }
                                        change.consume()
                                    }
                                }
                            }
                            PointerEventType.Release -> {
                                if (inHotzone && zoomTarget <= 1.01f && panel == ReaderPanel.None) {
                                    val rtl = desktopReaderReverses(readerMode)
                                    val movedPages = dragAccum / contentWidth * 0.5f
                                    if (kotlin.math.abs(movedPages) > 0.5f) {
                                        val dir = if (movedPages > 0f) 1 else -1
                                        step(if (rtl) -dir else dir)
                                    } else {
                                        scope.launch {
                                            pagerState.animateScrollToPage(pagerState.currentPage)
                                        }
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
        // 常驻左栏：占真实宽度，画布内容按 LEFT_STRIP_WIDTH 让位（见下方 padding）。
        ReaderLeftStrip(
            visible = pages.isNotEmpty(),
            pages = pages,
            currentPage = currentPage,
            onSelectPage = { idx -> jumpToLogical(idx) },
            modifier = Modifier.align(Alignment.CenterStart),
        )
        if (pages.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = note ?: com.venera.desktop.settings.DesktopI18n.t("暂无页面", "No pages"),
                    fontSize = VeneraType.body.size, lineHeight = VeneraType.body.lineHeight,
                    color = Color(0xFFCCCCCC),
                )
            }
        } else if (paged) {
            com.venera.desktop.ui.components.DesktopContextMenuArea(
                items = {
                    listOf(
                        com.venera.desktop.ui.components.DesktopMenuEntry.Item(
                            label = com.venera.desktop.settings.DesktopI18n.tr("保存当前页到本地", "Save current page"),
                            onClick = {
                                val targetPage = pages.getOrNull(currentPage) ?: return@Item
                                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    val loader = readerImageLoader ?: return@launch
                                    val req = coil3.request.ImageRequest.Builder(coil3.PlatformContext.INSTANCE)
                                        .data(targetPage)
                                        .build()
                                    val res = loader.execute(req)
                                    val img = (res as? coil3.request.SuccessResult)?.image ?: return@launch
                                    val bmp = img.toBitmap().toBufferedImage()
                                    val chooser = javax.swing.JFileChooser().apply {
                                        selectedFile = java.io.File("page_${currentPage + 1}.png")
                                    }
                                    if (chooser.showSaveDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
                                        javax.imageio.ImageIO.write(bmp, "png", chooser.selectedFile)
                                    }
                                }
                            },
                        ),
                        com.venera.desktop.ui.components.DesktopMenuEntry.Item(
                            label = com.venera.desktop.settings.DesktopI18n.tr("复制当前页图片", "Copy current page image"),
                            onClick = {
                                val targetPage = pages.getOrNull(currentPage) ?: return@Item
                                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    val loader = readerImageLoader ?: return@launch
                                    val req = coil3.request.ImageRequest.Builder(coil3.PlatformContext.INSTANCE)
                                        .data(targetPage)
                                        .build()
                                    val res = loader.execute(req)
                                    val img = (res as? coil3.request.SuccessResult)?.image ?: return@launch
                                    val bmp = img.toBitmap().toBufferedImage()
                                    val transferable = object : java.awt.datatransfer.Transferable {
                                        override fun getTransferDataFlavors() = arrayOf(java.awt.datatransfer.DataFlavor.imageFlavor)
                                        override fun isDataFlavorSupported(flavor: java.awt.datatransfer.DataFlavor) =
                                            flavor == java.awt.datatransfer.DataFlavor.imageFlavor
                                        override fun getTransferData(flavor: java.awt.datatransfer.DataFlavor): Any = bmp
                                    }
                                    java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(transferable, null)
                                }
                            },
                        ),
                        com.venera.desktop.ui.components.DesktopMenuEntry.Item(
                            label = com.venera.desktop.settings.DesktopI18n.tr("重新加载当前页", "Reload current page"),
                            onClick = { reloadTick += 1 },
                        ),
                    )
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = LEFT_STRIP_WIDTH)
                    // paged 档每页水平外边距（prefs.marginH）
                    .padding(horizontal = prefs.marginH.dp)
                    .onSizeChanged { viewportSize = it }
                    .onGloballyPositioned { viewportOriginInRoot = it.positionInRoot() },
            ) {
                if (prefs.turnEffect == 4) {
                    // 翻页档独占 3D 硬翻视口；外层统一承接缩放与放大后拖拽平移。
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(zoomTarget > 1.01f) {
                                if (zoomTarget <= 1.01f) return@pointerInput
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val maxX = size.width * (zoomTarget - 1f) / 2f
                                    val maxY = size.height * (zoomTarget - 1f) / 2f
                                    panTarget = Offset(
                                        (panTarget.x + dragAmount.x).coerceIn(-maxX, maxX),
                                        (panTarget.y + dragAmount.y).coerceIn(-maxY, maxY),
                                    )
                                }
                            }
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
                            rightToLeft = desktopReaderReverses(readerMode),
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
                            aiParamsFor = ::aiParamsFor,
                            onImageSize = ::recordPageMetrics,
                            reloadTick = reloadTick,
                            onPageChange = { jumpToLogical(it) },
                            onToggleHud = { chromeVisible = !chromeVisible },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                } else if (prefs.turnEffect == 2) {
                    // 导览视图：DesktopGuidedViewEngine 驱动的分格聚焦渲染，外层承接缩放与平移。
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(zoomTarget > 1.01f) {
                                if (zoomTarget <= 1.01f) return@pointerInput
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val maxX = size.width * (zoomTarget - 1f) / 2f
                                    val maxY = size.height * (zoomTarget - 1f) / 2f
                                    panTarget = Offset(
                                        (panTarget.x + dragAmount.x).coerceIn(-maxX, maxX),
                                        (panTarget.y + dragAmount.y).coerceIn(-maxY, maxY),
                                    )
                                }
                            }
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
                            rightToLeft = desktopReaderReverses(readerMode),
                            style = prefs.guidedViewStyle,
                            maskStyle = prefs.guidedViewMaskStyle,
                            paddingPx = prefs.guidedViewPadding.toFloat(),
                            maxZoom = prefs.guidedViewMaxZoom,
                            showFullFirst = prefs.guidedViewShowFullFirst,
                            showFullLast = prefs.guidedViewShowFullLast,
                            animSeconds = prefs.turnAnimSeconds,
                            panelIndex = guidedPanelIndex,
                            onStepCountChange = { guidedStepCount = it },
                            onPanelStep = { delta -> guidedPanelIndex += delta },
                            onExitFocus = { guidedViewFocused = false },
                            focused = guidedViewFocused,
                            fit = fit,
                            colorFilter = activeColorFilter,
                            filterQuality = cellFilterQuality,
                            aiParamsFor = ::aiParamsFor,
                            onImageSize = ::recordPageMetrics,
                            reloadTick = reloadTick,
                            onPageChange = { jumpToLogical(it) },
                            onToggleHud = { chromeVisible = !chromeVisible },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                } else {
                    // pager 页内容抽成共享 lambda：主视口与镜内重挂的 Pager 都喂它，排版单一出处。
                    val pagerPageContent: @Composable androidx.compose.foundation.pager.PagerScope.(Int) -> Unit = { group ->
                    val turnEffect = prefs.turnEffect
                    // 右至左档页序反向，滞后位移的正负也要跟着翻，否则纸页往反方向跑。
                    val turnSign = if (desktopReaderReverses(readerMode)) -1f else 1f
                    val cellModifier = Modifier
                        .fillMaxSize()
                        // 翻页动画按"这页离视口的距离"算，读在 layer 里所以每帧只重绘不重组。
                        // 连续页号 = currentPage - 偏移量：滚动过半时 currentPage 才跳变，
                        // 直接用它会在这瞬间把 near 从半档摔到零，动画看着一卡。
                        .graphicsLayer {
                            if (turnEffect != 0) {
                                val off = ((pagerState.currentPage - pagerState.currentPageOffsetFraction) - group)
                                    .coerceIn(-1f, 1f)
                                val near = 1f - kotlin.math.abs(off)
                                when (turnEffect) {
                                    // 3=淡出：纯 alpha 交叉（读 offset 线性，无缓动）——
                                    // 出视口页 1→0、入视口页 0→1，同帧交叉对齐原版 fade。
                                    3 -> {
                                        alpha = (1f - kotlin.math.abs(off)).coerceIn(0f, 1f)
                                    }
                                    // 4=3D 翻页 (Beta)
                                    4 -> {
                                        val away = kotlin.math.abs(off)
                                        cameraDistance = prefs.turnPerspective.toFloat() * density
                                        val angle = (-off * 80f * turnSign).coerceIn(-85f, 85f)
                                        rotationY = angle
                                        transformOrigin = if (turnSign * off > 0) {
                                            TransformOrigin(0f, 0.5f)
                                        } else {
                                            TransformOrigin(1f, 0.5f)
                                        }
                                        alpha = 1f - 0.2f * away
                                    }
                                    // 5=平滑翻页 (Alpha) 的卷缝实现已退场：档位加载钳到 2..4，
                                    // 没有入口能选到它，留着就是一段永远不执行的路径。
                                    else -> alpha = 0.5f + 0.5f * near
                                }
                            }
                        }
                        // 翻页档的书页阴影：两侧压暗折痕
                        // （预模糊手法：按进度插值的渐变带替代 backdrop 取样）。
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
                                            listOf(edge.copy(alpha = weight), Color.Transparent), 0f, w,
                                        ),
                                        size = androidx.compose.ui.geometry.Size(w, size.height),
                                    )
                                    drawRect(
                                        brush = Brush.horizontalGradient(
                                            listOf(Color.Transparent, edge.copy(alpha = weight)), size.width - w, size.width,
                                        ),
                                        topLeft = Offset(size.width - w, 0f),
                                        size = androidx.compose.ui.geometry.Size(w, size.height),
                                    )
                                }
                            }
                        }
                        .clipToBounds()
                        .then(if (masked) Modifier.blur(24.dp) else Modifier)
                    // 变换层：单页与双页都在这层缩放平移，双页因此作为整体一起放大、一起拖动。
                    // 手势必须挂在 graphicsLayer **外层**：挂在里面时指针坐标会被缩放层反向变换，
                    // 拖 100px 只写入 100/倍率，表现为"越放大越拖不动"。
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(zoomTarget > 1.01f) {
                                if (zoomTarget <= 1.01f) return@pointerInput
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    // 钳制按"缩放后内容比视口多出的那一半"算，保证图不整体离开边框。
                                    val maxX = size.width * (zoomTarget - 1f) / 2f
                                    val maxY = size.height * (zoomTarget - 1f) / 2f
                                    panTarget = Offset(
                                        (panTarget.x + dragAmount.x).coerceIn(-maxX, maxX),
                                        (panTarget.y + dragAmount.y).coerceIn(-maxY, maxY),
                                    )
                                }
                            }
                            .clipToBounds(),
                    ) {
                    // 旋转竖排页面后，内容的宽高会互换；按较短边再收一次，免得 90°/270° 时两侧被裁掉。
                    val quarterTurn = rotationDegrees % 180 != 0
                    val rotFit = if (quarterTurn && maxWidth > 0.dp && maxHeight > 0.dp) {
                        minOf(maxWidth / maxHeight, maxHeight / maxWidth)
                    } else {
                        1f
                    }
                    // 本组横图旋转角（rotateH）：取组内任一逻辑页判定宽图。
                    val groupRotH = if (isDouble) {
                        doubleGroups.getOrNull(group).orEmpty()
                            .firstOrNull { it >= 0 && (pageRatios[it] ?: 0f) > 1f }
                            ?.let { horizontalPageRotation(it) } ?: 0f
                    } else {
                        horizontalPageRotation(group)
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
                                page = pages.getOrNull(idx), index = idx, fit = fit,
                                zoomTarget = zoomTarget, markInput = {},
                                colorFilter = activeColorFilter,
                                filterQuality = cellFilterQuality,
                                aiParams = aiParamsFor(pages.getOrNull(idx)),
                                reloadTick = reloadTick,
                                onImageSize = { w, h -> recordPageMetrics(idx, w, h) },
                                modifier = cellModifier,
                                fillCell = true,
                            )
                        } else {
                            val l0 = groupPages.getOrNull(0) ?: BLANK_PAGE
                            val l1 = groupPages.getOrNull(1) ?: BLANK_PAGE
                            // 适应屏幕时若各占半宽，竖长页面在自己的格子里左右留白，
                            // 两页看起来隔着一道沟；改成整组按两页宽高比贴合、整体居中。
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
                                                colorFilter = activeColorFilter,
                                                filterQuality = cellFilterQuality,
                                                aiParams = aiParamsFor(pages.getOrNull(l0)),
                                                reloadTick = reloadTick,
                                                onImageSize = { w, h -> recordPageMetrics(l0, w, h) },
                                                modifier = Modifier.weight(r0),
                                            )
                                            ReaderPagedCell(
                                                page = pages.getOrNull(l1), index = l1, fit = fit,
                                                zoomTarget = zoomTarget, markInput = {},
                                                colorFilter = activeColorFilter,
                                                filterQuality = cellFilterQuality,
                                                aiParams = aiParamsFor(pages.getOrNull(l1)),
                                                reloadTick = reloadTick,
                                                onImageSize = { w, h -> recordPageMetrics(l1, w, h) },
                                                modifier = Modifier.weight(r1),
                                            )
                                        }
                                    }
                                } else {
                                    Row(modifier = Modifier.fillMaxSize()) {
                                        ReaderPagedCell(
                                            page = pages.getOrNull(l0), index = l0, fit = fit,
                                            zoomTarget = zoomTarget, markInput = {},
                                            colorFilter = activeColorFilter,
                                            filterQuality = cellFilterQuality,
                                            aiParams = aiParamsFor(pages.getOrNull(l0)),
                                            reloadTick = reloadTick,
                                            onImageSize = { w, h -> recordPageMetrics(l0, w, h) },
                                            modifier = Modifier.weight(1f),
                                        )
                                        ReaderPagedCell(
                                            page = pages.getOrNull(l1), index = l1, fit = fit,
                                            zoomTarget = zoomTarget, markInput = {},
                                            colorFilter = activeColorFilter,
                                            filterQuality = cellFilterQuality,
                                            aiParams = aiParamsFor(pages.getOrNull(l1)),
                                            reloadTick = reloadTick,
                                            onImageSize = { w, h -> recordPageMetrics(l1, w, h) },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                                if (prefs.shadowOn && l0 >= 0 && l1 >= 0 && fit != ReaderFit.ORIGINAL) {
                                    // 中缝阴影吃 prefs 四键：size=宽度%（占组宽），
                                    // opacity=黑度%，displace=偏移%（正=往左页侧让位）。
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
                    } else {
                        ReaderPagedCell(
                            page = pages.getOrNull(group), index = group, fit = fit,
                            zoomTarget = zoomTarget, markInput = {},
                            colorFilter = activeColorFilter,
                            filterQuality = cellFilterQuality,
                            aiParams = aiParamsFor(pages.getOrNull(group)),
                            reloadTick = reloadTick,
                            onImageSize = { w, h -> recordPageMetrics(group, w, h) },
                            modifier = cellModifier,
                            fillCell = true,
                        )
                    }
                    } // 缩放层
                    } // 变换层约束
                }
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        reverseLayout = desktopReaderReverses(readerMode),
                        // 放大后拖动归变换层，Pager 不再吃手势；否则位移被两方分摊，拖起来发涩。
                        userScrollEnabled = zoomTarget <= 1.01f,
                        // 前后各留一页：默认只组合当前页，滑走就销毁、回来要重新解码，翻来翻去每页都像首次打开。
                        beyondViewportPageCount = 1,
                        key = { it },
                        pageContent = pagerPageContent,
                    )
                    // 镜内重挂：主视口同款 Pager；3D/导览档换同款特化视口，外层变换由包裹层复刻。
                    lensViewportRef = when (prefs.turnEffect) {
                        4 -> {
                            {
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
                                        aiParamsFor = ::aiParamsFor,
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
                            {
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
                                        // 聚焦态必须跟主视口一致：主视口聚焦到某一格时，
                                        // 镜内传 false 会显示整页，镜里就成了另一幅画面。
                                        focused = guidedViewFocused,
                                        fit = fit,
                                        colorFilter = activeColorFilter,
                                        filterQuality = cellFilterQuality,
                                        aiParamsFor = ::aiParamsFor,
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
                            {
                                HorizontalPager(
                                    state = pagerState,
                                    modifier = Modifier.fillMaxSize(),
                                    reverseLayout = desktopReaderReverses(readerMode),
                                    userScrollEnabled = false,
                                    beyondViewportPageCount = 1,
                                    key = { it },
                                    pageContent = pagerPageContent,
                                )
                            }
                        }
                    }
                } // 闭合 else HorizontalPager
            }
        } else if (horizontalContinuous) {
            // 平滑档：横向连续，不吸附，松手停在哪儿算哪儿。
            // 页内容抽成共享：主视口与镜内重挂的 LazyRow 都喂它。
            val rowPageContent: LazyListScope.() -> Unit = {
                items(pages.size) { i ->
                    ReaderPagedCell(
                        page = pages.getOrNull(i), index = i, fit = fit,
                        zoomTarget = zoomTarget, markInput = {},
                        colorFilter = activeColorFilter,
                        filterQuality = cellFilterQuality,
                        aiParams = aiParamsFor(pages.getOrNull(i)),
                        reloadTick = reloadTick,
                        onImageSize = { w, h -> recordPageMetrics(i, w, h) },
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
                    .onSizeChanged { viewportSize = it }
                    .onGloballyPositioned { viewportOriginInRoot = it.positionInRoot() },
                content = rowPageContent,
            )
            // 镜内重挂：主视口同款横向连续列表（禁手势）。
            // 宽度必须与主列表同宽：viewportSize 量的是主列表扣掉 strip 之后的宽度，
            // 镜内再扣一次 strip 会让每页窄一截，共享的滚动像素偏移就落到别的页上。
            lensViewportRef = {
                LazyRow(
                    state = rowState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = false,
                    content = rowPageContent,
                )
            }
        } else {
            // 纵向连读：原样保留（0.7 兜底比例 + 真实宽高比写回 —— 那把语义尺钉着）。
            // 页内容抽成共享：主视口与镜内重挂的 LazyColumn 都喂它。
            val listPageContent: LazyListScope.() -> Unit = {
                items(pages.size) { i ->
                    val ratio = pageRatios[i] ?: TRIPLE_RATIO_FALLBACK
                    val widthFraction = (prefs.stripMaxWidthPercent / 100f).coerceIn(0.5f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(widthFraction)
                            // singleInScroll（仅垂直连续档）：页高=视口高钳原始高，一屏一页。
                            // 视口尺寸来自 onSizeChanged，是像素；直接 .dp 在高 DPI 下钳位会放得过宽。
                            .then(
                                if (prefs.singleInScroll) {
                                    Modifier.heightIn(
                                        max = with(LocalDensity.current) { viewportSize.height.toDp() },
                                    )
                                } else {
                                    Modifier
                                },
                            )
                            .aspectRatio(ratio)
                            .then(if (masked) Modifier.blur(24.dp) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        val stripPlatformCtx = coil3.compose.LocalPlatformContext.current
                        val stripMaxWidthPx = if (viewportSize.width > 0) {
                            (viewportSize.width * widthFraction).toInt().coerceIn(800, 1920)
                        } else {
                            1600
                        }
                        val stripModel = remember(pages[i], stripMaxWidthPx, stripPlatformCtx) {
                            coil3.request.ImageRequest.Builder(stripPlatformCtx)
                                .data(pages[i])
                                .size(
                                    coil3.size.Dimension.Pixels(stripMaxWidthPx),
                                    coil3.size.Dimension.Undefined,
                                )
                                .build()
                        }
                        AsyncImage(
                            model = stripModel,
                            contentDescription = com.venera.desktop.settings.DesktopI18n.t("第 ${i + 1} 页", "Page ${i + 1}"),
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    rotationZ = rotationDegrees.toFloat() + horizontalPageRotation(i)
                                },
                            contentScale = ContentScale.Fit,
                            colorFilter = activeColorFilter,
                            filterQuality = cellFilterQuality,
                            onSuccess = { state ->
                                val img = state.result.image
                                val w = img.width
                                val h = img.height
                                if (w > 0 && h > 0) pageRatios[i] = w.toFloat() / h.toFloat()
                            },
                        )
                    }
                }
                item {
                    Spacer(Modifier.height(tokens.spacing.space6))
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = com.venera.desktop.settings.DesktopI18n.t("— 本话完 —", "— End of Chapter —"),
                            fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                            color = Color(0xFF888888),
                            modifier = Modifier.padding(bottom = tokens.spacing.space6),
                        )
                    }
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = LEFT_STRIP_WIDTH)
                    .onSizeChanged { viewportSize = it }
                    .onGloballyPositioned { viewportOriginInRoot = it.positionInRoot() }
                    .pointerInput(zoomTarget > 1.01f) {
                        if (zoomTarget <= 1.01f) return@pointerInput
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val maxX = size.width * (zoomTarget - 1f) / 2f
                            val maxY = size.height * (zoomTarget - 1f) / 2f
                            panTarget = Offset(
                                (panTarget.x + dragAmount.x).coerceIn(-maxX, maxX),
                                (panTarget.y + dragAmount.y).coerceIn(-maxY, maxY),
                            )
                        }
                    }
                    .clipToBounds()
                    .graphicsLayer {
                        scaleX = zoom
                        scaleY = zoom
                        translationX = pan.x
                        translationY = pan.y
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                    },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = prefs.marginH.dp,
                    vertical = if (webtoonNow) 0.dp else prefs.marginV.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(
                    if (webtoonNow) {
                        if (prefs.stripPageGap > 0) prefs.stripPageGap.dp else tokens.spacing.space1
                    } else {
                        prefs.marginV.dp
                    },
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = listPageContent,
            )
            // 镜内重挂：主视口同款纵向连续列表（禁手势）。
            // 与横向连续档同理：镜内不再叠一次 strip，宽度对齐主列表。
            lensViewportRef = {
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
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = prefs.marginH.dp,
                        vertical = if (webtoonNow) 0.dp else prefs.marginV.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(
                        if (webtoonNow) {
                            if (prefs.stripPageGap > 0) prefs.stripPageGap.dp else tokens.spacing.space1
                        } else {
                            prefs.marginV.dp
                        },
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    content = listPageContent,
                )
            }
        }

        // ── 局部放大镜浮层 ──
        // 镜内重挂主视口同款视口（lensViewportRef 由各渲染分支就地赋值，见下）。
        // 锚点吃实测的视口原点：视口原点与根画布原点都在根坐标系里量出来再相减，
        // 与 pointerPos 同源。按 strip/marginH 手工推导时，推导和真实 padding
        // 只要差一处，镜内就整体偏一段，放大倍率再把这偏移成倍放大。
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

        // ── 末页/首页二次确认切话提示胶囊 ──
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

        // 顶栏已由 ReaderTopBar 常驻承担；旧的悬浮 TopHud 退场，避免两套顶栏重叠。

        ReaderBottomHud(
            visible = chromeVisible || panel == ReaderPanel.Thumbs,
            currentPage = currentPage,
            totalPages = pages.size,
            readerMode = readerMode,
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
            onSelectPage = { idx -> jumpToLogical(idx) },
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
            aiPageForComparison = aiParamsFor(pages.getOrNull(currentPage)),
            onPickFit = { picked ->
                DesktopReaderPreferences.update { it.copy(fitIndex = ReaderFit.entries.indexOf(picked).coerceAtLeast(0)) }
                zoomTarget = 1f
                panTarget = Offset.Zero
            },
            onPickBackground = { idx ->
                background = idx
                com.venera.desktop.settings.DesktopReaderBackground.writeIndex(idx)
            },
            onUpdatePrefs = { transform -> DesktopReaderPreferences.update(transform) },
            onResetFilters = { DesktopReaderPreferences.resetFilters() },
            presets = filterPresets,
            onSavePreset = {
                DesktopReaderPreferences.saveFilterPreset(com.venera.desktop.settings.DesktopI18n.tr("预设 ${filterPresets.size + 1}", "Preset ${filterPresets.size + 1}"))
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
    } // Column：顶栏 + 内容区
}

/**
 * 阅读器互斥面板。
 *
 * 同一时刻只可能打开一个，互斥由类型保证；旧实现用三个布尔互相清零。
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

    val label: String get() = com.venera.desktop.settings.DesktopI18n.tr(labelZh, labelEn)
}

