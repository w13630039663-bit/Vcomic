package com.venera.desktop.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import com.venera.desktop.gallery.data.DesktopComicPage
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 3D 翻页视口：两段式翻折——前半程旧页绕轴 rotateY 0→±90°，后半程新页自 ∓90°→0° 接力；
 * 支持点击、键盘/外部页码变更全路径动画、鼠标水平拖拽实时跟手与双页中缝翻折。
 *
 * @param pages 页面列表。
 * @param currentPage 当前逻辑页码。
 * @param isDoublePage 是否为双页并排模式。
 * @param doubleGroups 双页分组列表（含封面单页、横图独占与空白页）。
 * @param rightToLeft 是否为日漫右至左阅读顺序。
 * @param perspective 透视档（5..50，值越大透视越强）。
 * @param animSeconds 翻页动画时长（秒）。
 * @param tapZoneRatio 左右点击翻页热区比例（0.15..0.33）。
 * @param fit 页面适配模式。
 * @param zoom 缩放倍率。
 * @param colorFilter 图像滤镜。
 * @param filterQuality 缩放插值质量。
 * @param rotationDegrees 页面旋转角度。
 * @param aiParamsFor AI 前置管线参数工厂。
 * @param onImageSize 图片尺寸回调（用于记录宽高比）。
 * @param reloadTick 重新加载计数。
 * @param onPageChange 翻页回调。
 * @param onToggleHud 切换菜单呼出。
 */
@Composable
internal fun DesktopBookTurnViewport(
    pages: List<DesktopComicPage>,
    currentPage: Int,
    isDoublePage: Boolean,
    doubleGroups: List<List<Int>>,
    rightToLeft: Boolean,
    perspective: Int,
    animSeconds: Float,
    tapZoneRatio: Float,
    shadowOn: Boolean,
    shadowSize: Float,
    shadowOpacity: Float,
    shadowDisplace: Float,
    fit: ReaderFit,
    zoom: Float,
    colorFilter: ColorFilter?,
    filterQuality: FilterQuality,
    rotationDegrees: Int,
    aiParamsFor: (DesktopComicPage?) -> DesktopAiPage?,
    onImageSize: (Int, Int, Int) -> Unit,
    reloadTick: Int,
    onPageChange: (Int) -> Unit,
    onToggleHud: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (pages.isEmpty()) return

    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }

    fun groupOfPage(pageIdx: Int): Int {
        if (!isDoublePage || doubleGroups.isEmpty()) return pageIdx.coerceIn(0, pages.lastIndex)
        val found = doubleGroups.indexOfFirst { pageIdx in it }
        return if (found >= 0) found else 0
    }

    fun firstPageOfGroup(groupIdx: Int): Int {
        if (!isDoublePage || doubleGroups.isEmpty()) return groupIdx.coerceIn(0, pages.lastIndex)
        val g = doubleGroups.getOrNull(groupIdx).orEmpty()
        return g.firstOrNull { it >= 0 } ?: 0
    }

    val groupCount = if (isDoublePage && doubleGroups.isNotEmpty()) doubleGroups.size else pages.size
    val externalGroup = groupOfPage(currentPage)

    var displayGroup by remember { mutableIntStateOf(externalGroup) }
    var targetGroup by remember { mutableIntStateOf(externalGroup) }
    var pendingExternalGroup by remember { mutableStateOf<Int?>(null) }
    var turningForward by remember { mutableStateOf(true) }
    var isTurning by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var dragAccumX by remember { mutableStateOf(0f) }

    val animMs = (animSeconds * 1000f).toInt().coerceIn(80, 4000)

    fun startTurnTo(nextGroup: Int, notifyExternal: Boolean) {
        val clamped = nextGroup.coerceIn(0, (groupCount - 1).coerceAtLeast(0))
        if (clamped == displayGroup) return
        turningForward = clamped > displayGroup
        targetGroup = clamped
        isTurning = true
        scope.launch {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(animMs, easing = FastOutSlowInEasing))
            if (notifyExternal) {
                pendingExternalGroup = clamped
            }
            displayGroup = clamped
            isTurning = false
            progress.snapTo(0f)
            if (notifyExternal) {
                onPageChange(firstPageOfGroup(clamped))
            }
        }
    }

    // 外部页码变化（键盘方向键、顶栏按钮、进度条、自动翻页）统一走 3D 翻页动画
    LaunchedEffect(externalGroup, groupCount) {
        if (isDragging) return@LaunchedEffect
        val pending = pendingExternalGroup
        if (pending != null) {
            if (externalGroup == pending) {
                pendingExternalGroup = null
            }
            return@LaunchedEffect
        }
        if (externalGroup != displayGroup && (!isTurning || externalGroup != targetGroup)) {
            if (abs(externalGroup - displayGroup) > 3) {
                // 跨多页跳转直接落位，避免长距离动画错觉
                displayGroup = externalGroup
                targetGroup = externalGroup
                isTurning = false
                progress.snapTo(0f)
            } else {
                startTurnTo(externalGroup, notifyExternal = false)
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(groupCount, displayGroup, isTurning, rightToLeft, tapZoneRatio) {
                detectTapGestures(
                    onTap = { offset ->
                        if (isTurning || isDragging) return@detectTapGestures
                        val tapRatio = offset.x / size.width.coerceAtLeast(1)
                        val zone = tapZoneRatio.coerceIn(0.1f, 0.4f)
                        when {
                            tapRatio < zone -> {
                                val delta = if (rightToLeft) 1 else -1
                                startTurnTo(displayGroup + delta, notifyExternal = true)
                            }
                            tapRatio > 1f - zone -> {
                                val delta = if (rightToLeft) -1 else 1
                                startTurnTo(displayGroup + delta, notifyExternal = true)
                            }
                            else -> onToggleHud()
                        }
                    },
                )
            }
            .pointerInput(groupCount, displayGroup, isTurning, rightToLeft, zoom <= 1.01f) {
                if (zoom > 1.01f) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = {
                        if (isTurning) return@detectHorizontalDragGestures
                        isDragging = true
                        dragAccumX = 0f
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        if (!isDragging) return@detectHorizontalDragGestures
                        change.consume()
                        dragAccumX += dragAmount
                        val w = size.width.coerceAtLeast(1).toFloat()
                        // 向左拖（dragAccumX < 0）：LTR 为下一页，RTL 为上一页
                        val forward = if (rightToLeft) dragAccumX > 0f else dragAccumX < 0f
                        val candidate = (displayGroup + if (forward) 1 else -1)
                        if (candidate !in 0 until groupCount) {
                            scope.launch { progress.snapTo(0f) }
                            return@detectHorizontalDragGestures
                        }
                        turningForward = forward
                        targetGroup = candidate
                        val p = (abs(dragAccumX) / (w * 0.55f)).coerceIn(0f, 0.99f)
                        scope.launch { progress.snapTo(p) }
                    },
                    onDragEnd = {
                        if (isDragging) {
                            isDragging = false
                            val curP = progress.value
                            if (curP <= 0.001f) {
                                scope.launch { progress.snapTo(0f) }
                            } else {
                                val commit = curP >= 0.22f
                                val destGroup = targetGroup
                                isTurning = true
                                scope.launch {
                                    if (commit) {
                                        val remMs = ((1f - curP) * animMs).toInt().coerceAtLeast(60)
                                        progress.animateTo(1f, tween(remMs, easing = FastOutSlowInEasing))
                                        pendingExternalGroup = destGroup
                                        displayGroup = destGroup
                                        isTurning = false
                                        progress.snapTo(0f)
                                        onPageChange(firstPageOfGroup(destGroup))
                                    } else {
                                        val remMs = (curP * animMs).toInt().coerceAtLeast(60)
                                        progress.animateTo(0f, tween(remMs, easing = FastOutSlowInEasing))
                                        targetGroup = displayGroup
                                        isTurning = false
                                    }
                                }
                            }
                        }
                    },
                    onDragCancel = {
                        if (isDragging) {
                            isDragging = false
                            scope.launch {
                                progress.animateTo(0f, tween(150, easing = FastOutSlowInEasing))
                                targetGroup = displayGroup
                            }
                        }
                    },
                )
            },
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val p = progress.value
        val turningOrDragging = isTurning || isDragging
        val showOld = p < 0.5f

        // 透视视距：perspective 5..50 映射为 1320..240 * density，值越大透视越强
        val camDist = ((60 - perspective).coerceIn(10, 55) * 24f) * density

        // 翻页方向：turnRight=true 表示页面从右半边向左翻（或整页向左翻）
        val turnToRightSide = if (rightToLeft) !turningForward else turningForward
        val dir = if (turnToRightSide) -1f else 1f
        val oldAngle = dir * 90f * min(p * 2f, 1f)
        val newAngle = -dir * 90f * (1f - ((p - 0.5f).coerceAtLeast(0f) * 2f))
        // 翻转轴落在中缝那一侧：右半边起翻绕左缘，左半边起翻绕右缘。
        val originX = if (turnToRightSide) 0f else 1f

        /**
         * 画一格页面。
         *
         * @param seamOnRight 中缝落在本页右侧（即本页是左页）传 true；本页是右页传 false；
         *   单页模式传 null —— 只有双页才有「页面之间」的阴影。
         */
        @Composable
        fun RenderPage(pageIdx: Int, mod: Modifier, seamOnRight: Boolean? = null) {
            androidx.compose.runtime.key(pageIdx) {
                val page = pages.getOrNull(pageIdx)
                if (page == null) {
                    Box(mod.background(Color.Transparent))
                } else {
                    val shaded = if (seamOnRight != null && shadowOn && shadowSize > 0f && shadowOpacity > 0f) {
                        mod.drawWithContent {
                            drawContent()
                            // 中缝阴影画在页面自己靠中缝的那条边上，跟着 3D 旋转一起走。
                            // 画在视口中央的固定渐变会「浮」在翻动页之上，翻页时明暗跳变。
                            val w = size.width * (shadowSize / 100f).coerceIn(0.005f, 0.5f)
                            val alpha = (shadowOpacity / 100f).coerceIn(0f, 1f)
                            val fade = (shadowDisplace / 100f).coerceIn(0.01f, 0.99f)
                            val stops = if (seamOnRight) {
                                arrayOf(
                                    0f to Color.Transparent,
                                    (1f - fade) to Color.Black.copy(alpha = alpha * 0.5f),
                                    1f to Color.Black.copy(alpha = alpha),
                                )
                            } else {
                                arrayOf(
                                    0f to Color.Black.copy(alpha = alpha),
                                    fade to Color.Black.copy(alpha = alpha * 0.5f),
                                    1f to Color.Transparent,
                                )
                            }
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colorStops = stops,
                                    startX = if (seamOnRight) size.width - w else 0f,
                                    endX = if (seamOnRight) size.width else w,
                                ),
                                topLeft = Offset(if (seamOnRight) size.width - w else 0f, 0f),
                                size = Size(w, size.height),
                            )
                        }
                    } else {
                        mod
                    }
                    ReaderPagedCell(
                        page = page,
                        index = pageIdx,
                        fit = fit,
                        zoomTarget = zoom,
                        markInput = {},
                        colorFilter = colorFilter,
                        filterQuality = filterQuality,
                        aiParams = aiParamsFor(page),
                        reloadTick = reloadTick,
                        onImageSize = { w, h -> onImageSize(pageIdx, w, h) },
                        modifier = shaded.graphicsLayer { rotationZ = rotationDegrees.toFloat() },
                        fillCell = true,
                    )
                }
            }
        }

        if (isDoublePage && doubleGroups.isNotEmpty()) {
            val curGroupPages = doubleGroups.getOrNull(displayGroup).orEmpty()
            val nextGroupPages = doubleGroups.getOrNull(targetGroup).orEmpty()

            fun leftRightOf(g: List<Int>): Pair<Int, Int> {
                if (g.size <= 1) {
                    val single = g.firstOrNull() ?: BLANK_PAGE
                    return if (rightToLeft) BLANK_PAGE to single else single to BLANK_PAGE
                }
                return if (rightToLeft) g[1] to g[0] else g[0] to g[1]
            }

            val (curL, curR) = leftRightOf(curGroupPages)
            val (nextL, nextR) = leftRightOf(nextGroupPages)

            // 底层左右半区：每侧把「当前页 + 目标页」两格都挂上，翻页只切 alpha。
            // 挂载即取图 —— 翻页开始才新建 AsyncImage 的话首帧必空白，那正是闪的来源。
            val baseL = if (turningOrDragging && !turnToRightSide) nextL else curL
            val baseR = if (turningOrDragging && turnToRightSide) nextR else curR

            Box(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        listOf(curL, nextL).distinct().forEach { idx ->
                            androidx.compose.runtime.key(idx) {
                                RenderPage(
                                    idx,
                                    Modifier.fillMaxSize().graphicsLayer { alpha = if (idx == baseL) 1f else 0f },
                                    seamOnRight = true,
                                )
                            }
                        }
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        listOf(curR, nextR).distinct().forEach { idx ->
                            androidx.compose.runtime.key(idx) {
                                RenderPage(
                                    idx,
                                    Modifier.fillMaxSize().graphicsLayer { alpha = if (idx == baseR) 1f else 0f },
                                    seamOnRight = false,
                                )
                            }
                        }
                    }
                }
                // 中缝阴影不在这里画：它已经挂在每页靠中缝的那条边上（见 RenderPage），
                // 画在视口中央会「浮」在翻动页之上，翻页时明暗跳变。
                // 翻页期间底层暗化遮罩，增强立体层次
                if (turningOrDragging) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f * sinHalf(p))),
                    )
                }
            }

            // 3D 层：左右两侧各常驻一格，按翻起方向切 alpha。
            // 常驻而非随翻页分支新建 —— 新建的 AsyncImage 首帧必空白，起始帧就会闪。
            val halfWidthPx = widthPx / 2f
            val halfWidthDp = with(LocalDensity.current) { halfWidthPx.toDp() }
            val oldPageIdx = if (turnToRightSide) curR else curL
            val newPageIdx = if (turnToRightSide) nextL else nextR

            // 前半程翻起当前页
            listOf(curL to false, curR to true).distinctBy { it.first }.forEach { (idx, onRight) ->
                val visible = turningOrDragging && showOld && idx == oldPageIdx
                androidx.compose.runtime.key(idx) {
                    Box(
                        modifier = Modifier
                        .offset { IntOffset(if (onRight) halfWidthPx.roundToInt() else 0, 0) }
                        .width(halfWidthDp)
                        .fillMaxHeight()
                        .graphicsLayer {
                            alpha = if (visible) 1f else 0f
                            cameraDistance = camDist
                            rotationY = oldAngle
                            transformOrigin = TransformOrigin(if (onRight) 0f else 1f, 0.5f)
                        },
                    contentAlignment = Alignment.Center,
                    ) {
                        RenderPage(idx, Modifier.fillMaxSize(), seamOnRight = !onRight)
                    }
                }
            }

            // 后半程落下目标页
            listOf(nextL to false, nextR to true).distinctBy { it.first }.forEach { (idx, onRight) ->
                val visible = turningOrDragging && !showOld && idx == newPageIdx
                androidx.compose.runtime.key(idx) {
                    Box(
                        modifier = Modifier
                        .offset { IntOffset(if (onRight) halfWidthPx.roundToInt() else 0, 0) }
                        .width(halfWidthDp)
                        .fillMaxHeight()
                        .graphicsLayer {
                            alpha = if (visible) 1f else 0f
                            cameraDistance = camDist
                            rotationY = newAngle
                            transformOrigin = TransformOrigin(if (onRight) 0f else 1f, 0.5f)
                        },
                    contentAlignment = Alignment.Center,
                    ) {
                        RenderPage(idx, Modifier.fillMaxSize(), seamOnRight = !onRight)
                    }
                }
            }
        } else {
            // 单页模式：底层是常驻三格池（d-1 / d / d+1），翻页只切 alpha；
            // 3D 两格也常驻，不随翻页分支出现/消失 —— AsyncImage 新建必有 1~2 帧空白。
            val baseIdx = if (turningOrDragging) targetGroup else displayGroup
            val residentPages = remember(displayGroup, targetGroup, turningOrDragging, groupCount) {
                linkedSetOf<Int>().apply {
                    if (displayGroup - 1 >= 0) add(displayGroup - 1)
                    add(displayGroup)
                    if (displayGroup + 1 < groupCount) add(displayGroup + 1)
                    if (turningOrDragging) add(targetGroup.coerceIn(0, groupCount - 1))
                }.toList()
            }
            Box(modifier = Modifier.fillMaxSize()) {
                residentPages.forEach { idx ->
                    // 池子会随 d 平移，按页号做 key 才能让同一页的节点整体搬家而不是重建。
                    androidx.compose.runtime.key(idx) {
                        RenderPage(
                            idx,
                            Modifier.fillMaxSize().graphicsLayer { alpha = if (idx == baseIdx) 1f else 0f },
                        )
                    }
                }
                if (turningOrDragging) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f * sinHalf(p))),
                    )
                }
            }

            // 前半程：旧页翻走（常驻；静止时 alpha=0，由底层池显示当前页）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = if (turningOrDragging && showOld) 1f else 0f
                        cameraDistance = camDist
                        rotationY = oldAngle
                        transformOrigin = TransformOrigin(originX, 0.5f)
                    },
                contentAlignment = Alignment.Center,
            ) {
                RenderPage(displayGroup, Modifier.fillMaxSize())
            }

            // 后半程：新页翻入（常驻；前半程 alpha=0 的那段时间正好给图加载）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = if (turningOrDragging && !showOld) 1f else 0f
                        cameraDistance = camDist
                        rotationY = newAngle
                        transformOrigin = TransformOrigin(originX, 0.5f)
                    },
                contentAlignment = Alignment.Center,
            ) {
                RenderPage(targetGroup, Modifier.fillMaxSize())
            }
        }
    }
}

private fun sinHalf(p: Float): Float {
    return kotlin.math.sin(p.coerceIn(0f, 1f) * Math.PI).toFloat()
}
