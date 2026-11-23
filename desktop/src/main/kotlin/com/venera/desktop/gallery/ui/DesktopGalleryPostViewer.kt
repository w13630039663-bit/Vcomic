package com.venera.desktop.gallery.ui

import com.venera.desktop.ui.vShadow
import com.venera.desktop.ui.theme.VeneraType
import com.venera.desktop.ui.DesktopPushButton
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import com.venera.compose.components.LocalUxPlatformServices
import com.venera.compose.components.venera.VeneraTagChip
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryPreloadMode
import com.venera.compose.gallery.domain.buildGalleryTagBuckets
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.gallery.ui.DesktopWallCard as DesktopWallCardEntry
import com.venera.desktop.ui.ViewerPageTemplate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 桌面画廊**看大图那一屏**（灵动玻璃坞与信息抽屉彻底重构）。
 *
 * ##重大重构：
 * 1. **中央漂浮式灵动玻璃坞 (Floating Glass Dock)**：
 *    - 彻底移除原先密密麻麻堆在底部遮挡原图的 24 个标签 chips；
 *    - 底中改为精致圆角毛玻璃药丸坞（20dp 圆角 + 阴影 + 高光边框），悬浮在画面底部 16dp 处，两侧完全透明；
 *    - 包含画质切换（中/大/原）、视口缩放与翻页、核心功能按钮（收藏/下载/复制/抽屉）。
 * 2. **右侧滑出式信息抽屉 (Inspector Drawer)**：
 *    - 标签与全量图片事实收归于此，点击右上角或控制坞的 `ℹ` 按钮平滑滑出；
 *    - 保持画布 100% 极简纯净，不看时收起，原图无阻挡。
 * 3. **隐形极简悬浮顶栏**：
 *    - 统一毛玻璃 `VeneraBackHandle (Glass)`，带 Esc 退出提示与优雅微标。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DesktopGalleryPostViewer(
    post: DesktopWallCardModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    queue: List<DesktopWallCardModel> = listOf(post),
    initialIndex: Int = 0,
    /** 退出那一刻的页号回传（详情页「返回记住翻到哪张」的货源）；null 形同没有。 */
    onExitIndex: ((Int) -> Unit)?,
    favoriteUids: Set<String> = emptySet(),
    onToggleFavorite: (suspend (DesktopWallCardModel) -> Boolean)?,
    onSaveImage: (suspend (DesktopWallCardModel) -> String)?,
    /** 「自动连播间隔」偏好。 */
    autoPlaySec: Int = 0,
    /** 「提前加载」档。 */
    preloadMode: GalleryPreloadMode = GalleryPreloadMode.NEXT,
    /** 「大图缩放」画质档（medium/large/original）。 */
    zoomQuality: com.venera.compose.gallery.domain.PixivImageQuality = com.venera.compose.gallery.domain.PixivImageQuality.LARGE,
    /** 画质档写口。 */
    onZoomQualityChange: ((com.venera.compose.gallery.domain.PixivImageQuality) -> Unit)?,
    /** pixiv 收藏腿。 */
    pixivBookmarkToggle: (suspend (Long, Boolean) -> Boolean)?,
    /** pixiv 存原图腿。 */
    onSavePixivImage: (suspend (DesktopWallCardModel) -> String)?,
    /** 复制图片腿；返回是否写进了剪贴板。 */
    onCopyImage: (suspend (DesktopWallCardModel) -> Boolean)?,
    /** pixiv 逐帧包取数腿（ugoira 就地播放用）；null ⇒ 动图退静帧。 */
    pixiv: com.venera.compose.gallery.data.GalleryPixivWorks? = null,
    /** 取 gif 字节的落盘根（booru animated gif 逐帧解码用）；null ⇒ gif 退静帧。 */
    paths: com.venera.compose.data.platform.PathProvider? = null,
    backdropColor: Color = Color.Black,
) {
    val handoff = LocalDesktopSearchHandoff.current
    val tokens = VeneraTokens
    val safeQueue = remember(post.uid, queue) {
        val openable = queue.filter { it.openable }
        if (openable.any { it.uid == post.uid }) openable else openable + post
    }
    var pageIndex by remember(post.uid, safeQueue) {
        mutableIntStateOf(initialIndex.coerceIn(0, safeQueue.lastIndex.coerceAtLeast(0)))
    }
    val current = safeQueue[pageIndex.coerceIn(0, safeQueue.lastIndex.coerceAtLeast(0))]
    val tagSite = desktopViewerTagSite(current)

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    // 退出统一走这里：先回传此刻页号再弹路由，详情页才知道「返回记住翻到哪张」。
    val exit: () -> Unit = {
        onExitIndex?.invoke(pageIndex)
        onBack()
    }
    val openUrl = desktopViewerOpenUrl(current)
    val fullUrl = desktopViewerFullUrl(current, zoomQuality)
    val hasFullLayer = fullUrl.isNotBlank() && fullUrl != openUrl

    // ── 视频/动图就地分流（逐张：翻到哪张算哪张的 current.sourcePost）──
    val currentSource = current.sourcePost
    val currentIsVideo = currentSource?.isVideo == true
    val currentIsGif = currentSource?.fileExt?.equals("gif", ignoreCase = true) == true
    val currentIsAnimated = currentIsVideo || currentIsGif
    val currentUgoiraId = currentSource?.id?.takeIf {
        currentSource.site == com.venera.compose.gallery.data.GallerySite.PIXIV && currentSource.ugoira
    }
    val ugoiraPlayback = if (currentUgoiraId != null) {
        rememberDesktopUgoiraPlayback(
            pixiv,
            currentUgoiraId,
            original = zoomQuality == com.venera.compose.gallery.domain.PixivImageQuality.ORIGINAL,
            retryKey = pageIndex,
        )
    } else {
        null
    }

    var openOk by remember(openUrl) { mutableStateOf(false) }
    var openError by remember(openUrl) { mutableStateOf(false) }
    var fullOk by remember(fullUrl) { mutableStateOf(false) }
    var fullError by remember(fullUrl) { mutableStateOf(false) }
    val loader = LocalUxPlatformServices.current.imageLoader()
    val tags = current.tags.split(' ').map { it.trim() }.filter { it.isNotEmpty() }
    val nothingShown = openUrl.isBlank() || (openError && (fullUrl.isNullOrBlank() || fullError))
    val canPrev = pageIndex > 0
    val canNext = pageIndex < safeQueue.lastIndex
    val scope = rememberCoroutineScope()
    var chromeVisible by remember(post.uid) { mutableStateOf(true) }
    var favorite by remember(current.uid) { mutableStateOf(current.uid in favoriteUids) }
    var saving by remember(current.uid) { mutableStateOf(false) }
    var notice by remember(current.uid) { mutableStateOf<String?>(null) }
    // 动图/视频内嵌播放态（逐张：翻到哪张重置；点 dock 起播，关/结束回静图）。
    var videoPlaying by remember(current.uid) { mutableStateOf(false) }
    // 播放中就让画布静图让位（避免动图与静图叠加错位）；ugoira 与视频互斥。
    val videoCoversStatic = currentIsAnimated && videoPlaying

    // ⭐️ 详细信息滑出抽屉状态
    var drawerOpen by remember { mutableStateOf(false) }

    // 缩放与平移
    var zoom by remember(current.uid) { mutableStateOf(1f) }
    var pan by remember(current.uid) { mutableStateOf(Offset.Zero) }
    var boxPx by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val zoomed = zoom > 1.01f

    LaunchedEffect(current.uid) { zoom = 1f; pan = Offset.Zero }

    fun toggleZoom(at: Offset) {
        if (zoomed) {
            zoom = 1f
            pan = Offset.Zero
        } else {
            zoom = 2f
            pan = Offset((boxPx.width / 2f - at.x), (boxPx.height / 2f - at.y))
        }
    }

    var pixivBusy by remember(current.uid) { mutableStateOf(false) }
    var pixivFav by remember(current.uid) { mutableStateOf(false) }
    val pixivIllustId = current.uid.takeIf { it.startsWith("pixiv:") }?.substringAfter(':')?.toLongOrNull()

    LaunchedEffect(current.uid, favoriteUids) { favorite = current.uid in favoriteUids }
    LaunchedEffect(chromeVisible, current.uid) {
        if (chromeVisible && !drawerOpen) {
            delay(6000)
            chromeVisible = false
        }
    }

    fun step(direction: Int) {
        val next = desktopViewerStepIndex(pageIndex, safeQueue.size, direction)
        if (next != pageIndex) {
            pageIndex = next
            notice = null
        }
    }

    // 自动连播
    LaunchedEffect(autoPlaySec, current.uid, pageIndex) {
        if (autoPlaySec > 0 && pageIndex < safeQueue.lastIndex) {
            delay(autoPlaySec * 1000L)
            step(1)
        }
    }

    // 提前加载：句柄必须自己留着 —— 换页/离屏 dispose 掉，否则请求尾跑完还按原图
    // 分辨率解码进内存缓存（PreloadPolicy 契约里的"收手"就是靠这个句柄收的）。
    val pendingPreloads = remember { mutableListOf<coil3.request.Disposable>() }
    val purgeGpuCache = com.venera.desktop.LocalGpuCachePurge.current
    DisposableEffect(purgeGpuCache) {
        onDispose {
            pendingPreloads.forEach { it.dispose() }
            pendingPreloads.clear()
            purgeGpuCache?.invoke("viewer_dispose")
        }
    }
    LaunchedEffect(preloadMode, safeQueue, pageIndex, boxPx) {
        if (preloadMode != GalleryPreloadMode.OFF) {
            // 等待容器量出视口尺寸，确保预载与 ViewerImage 的 ConstraintsSizeResolver 命中同一缓存键，避免 2048x2048 双份解码。
            if (boxPx.width <= 0 || boxPx.height <= 0) return@LaunchedEffect
            val targetW = boxPx.width.coerceAtMost(2048)
            val targetH = boxPx.height.coerceAtMost(2048)
            val plan = desktopPreloadPlan(
                mode = preloadMode,
                index = pageIndex,
                cards = safeQueue.map { it.sourcePost?.let { p -> DesktopWallCardEntry(p, it.masked) } },
                tier = DesktopDetailTier.SAMPLE,
            )
            val coilLoader = loader ?: return@LaunchedEffect
            pendingPreloads.forEach { it.dispose() }
            pendingPreloads.clear()
            plan.urls.forEach { url ->
                runCatching {
                    pendingPreloads += coilLoader.enqueue(
                        coil3.request.ImageRequest.Builder(coil3.PlatformContext.INSTANCE)
                            .data(url)
                            .size(targetW, targetH)
                            .build(),
                    )
                }
            }
        }
    }

    com.venera.desktop.ui.RegisterDesktopShortcutHandler(
        pageIndex,
        safeQueue.size,
        drawerOpen,
        priority = 300,
    ) { signal ->
        when (com.venera.desktop.ui.matchViewerExtraShortcut(signal)) {
            com.venera.desktop.ui.DesktopViewerExtraAction.ZOOM_IN -> {
                zoom = (zoom * 1.25f).coerceIn(1f, 4f)
                chromeVisible = true
                return@RegisterDesktopShortcutHandler true
            }
            com.venera.desktop.ui.DesktopViewerExtraAction.ZOOM_OUT -> {
                val next = (zoom / 1.25f).coerceIn(1f, 4f)
                zoom = next
                if (next <= 1.01f) pan = Offset.Zero
                chromeVisible = true
                return@RegisterDesktopShortcutHandler true
            }
            com.venera.desktop.ui.DesktopViewerExtraAction.ZOOM_RESET,
            com.venera.desktop.ui.DesktopViewerExtraAction.FIT_SCREEN -> {
                zoom = 1f
                pan = Offset.Zero
                chromeVisible = true
                return@RegisterDesktopShortcutHandler true
            }
            else -> Unit
        }
        when (signal.keyCode) {
            java.awt.event.KeyEvent.VK_ESCAPE -> {
                if (drawerOpen) drawerOpen = false else exit()
                true
            }
            java.awt.event.KeyEvent.VK_LEFT, java.awt.event.KeyEvent.VK_PAGE_UP -> {
                step(-1); chromeVisible = true; true
            }
            java.awt.event.KeyEvent.VK_RIGHT, java.awt.event.KeyEvent.VK_PAGE_DOWN -> {
                step(1); chromeVisible = true; true
            }
            java.awt.event.KeyEvent.VK_SPACE -> {
                step(if (signal.isShift) -1 else 1); chromeVisible = true; true
            }
            else -> false
        }
    }

    ViewerPageTemplate(
        title = current.author.ifBlank { current.title },
        subtitle = current.title.takeIf { it != current.author },
        onBack = exit,
        backdropColor = backdropColor,
        modifier = modifier
            .pointerInput(current.uid) {
                awaitPointerEventScope {
                    while (true) {
                        val ev = awaitPointerEvent()
                        when (ev.type) {
                            androidx.compose.ui.input.pointer.PointerEventType.Scroll -> {
                                if (ev.keyboardModifiers.isCtrlPressed) {
                                    val dy = ev.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                    if (dy != 0f) {
                                        zoom = (zoom * (if (dy < 0f) 1.15f else 0.87f)).coerceIn(1f, 4f)
                                        if (!zoomed) pan = Offset.Zero
                                        ev.changes.forEach { it.consume() }
                                    }
                                    chromeVisible = true
                                }
                            }
                            androidx.compose.ui.input.pointer.PointerEventType.Move -> chromeVisible = true
                            else -> Unit
                        }
                    }
                }
            }
            .pointerInput(current.uid) {
                detectTapGestures(
                    onTap = {
                        if (drawerOpen) {
                            drawerOpen = false
                        } else {
                            chromeVisible = !chromeVisible
                        }
                    },
                    onDoubleTap = { at -> toggleZoom(at) },
                )
            }
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { ev ->
                val handled = onDesktopOverlayKey(
                    signal = ev.asDesktopKeySignal(),
                    viewerOpen = true,
                    onCloseViewer = exit,
                    onClose = exit,
                    onStep = ::step,
                )
                if (handled) chromeVisible = true
                handled
            },
        chromeVisible = chromeVisible,
        actions = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
            ) {
                Text(
                    text = current.uid,
                    fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                    color = Color.White.copy(alpha = 0.65f),
                )
                // ⭐️ 右上角详细信息抽屉开关
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (drawerOpen) tokens.color.primary.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.5f))
                        .border(1.dp, if (drawerOpen) tokens.color.primary else Color.White.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                        .clickable { drawerOpen = !drawerOpen }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = if (drawerOpen) tokens.color.primary else Color.White,
                            modifier = Modifier.size(15.dp),
                        )
                        Text(
                            text = if (drawerOpen) "收起" else "详细信息",
                            fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                            fontWeight = VeneraType.cardTitle.weight,
                            color = if (drawerOpen) tokens.color.primary else Color.White,
                        )
                    }
                }
            }
        },
        bottomBar = {
            // ⭐️ 核心改进：中央漂浮式灵动玻璃坞 (Floating Glass Dock)
            // 彻底移除之前挤占底部的成排标签 chips！
            Box(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    modifier = Modifier
                        .vShadow(24.dp, RoundedCornerShape(20.dp))
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xDD0E141A))
                        .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // 1. 画质三档胶囊
                    if (onZoomQualityChange != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            listOf(
                                com.venera.compose.gallery.domain.PixivImageQuality.MEDIUM to "中等",
                                com.venera.compose.gallery.domain.PixivImageQuality.LARGE to "大图",
                                com.venera.compose.gallery.domain.PixivImageQuality.ORIGINAL to "原图",
                            ).forEach { (q, label) ->
                                val on = q == zoomQuality
                                Text(
                                    text = label,
                                    fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                                    color = if (on) tokens.color.primary else Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (on) tokens.color.primary.copy(alpha = 0.22f) else Color.Transparent)
                                        .clickable { onZoomQualityChange(q) }
                                        .padding(horizontal = 7.dp, vertical = 4.dp),
                                )
                            }
                        }

                        Box(modifier = Modifier.width(1.dp).height(16.dp).background(Color.White.copy(alpha = 0.18f)))
                    }

                    // 2. 翻页与视口缩放控制
                    if (safeQueue.size > 1) {
                        DesktopPushButton(onClick = { step(-1) }, enabled = canPrev) { Text("←") }
                        Text(
                            text = "${pageIndex + 1} / ${safeQueue.size}",
                            fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                            fontWeight = VeneraType.cardTitle.weight,
                            color = Color.White,
                        )
                        DesktopPushButton(onClick = { step(1) }, enabled = canNext) { Text("→") }
                    }

                    Text(
                        text = if (zoomed) "${(zoom * 100).toInt()}%" else "适应窗口",
                        fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                        fontWeight = VeneraType.cardTitle.weight,
                        color = if (zoomed) tokens.color.primary else Color.White.copy(alpha = 0.85f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                if (zoomed) {
                                    zoom = 1f
                                    pan = Offset.Zero
                                } else {
                                    zoom = 2f
                                }
                            }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                    )

                    Box(modifier = Modifier.width(1.dp).height(16.dp).background(Color.White.copy(alpha = 0.18f)))

                    // 3. 核心功能按钮
                    // 收藏
                    val canFavBooru = current.sourcePost != null && onToggleFavorite != null
                    val canFavPixiv = pixivIllustId != null && pixivBookmarkToggle != null
                    if (canFavBooru || canFavPixiv) {
                        val active = favorite || (canFavPixiv && pixivFav)
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (active) Color(0xFFF28B82).copy(alpha = 0.22f) else Color.Transparent)
                                .clickable {
                                    scope.launch {
                                        when {
                                            canFavBooru -> favorite = onToggleFavorite(current)
                                            canFavPixiv -> {
                                                pixivBusy = true
                                                val add = !pixivFav
                                                val ok = pixivBookmarkToggle?.invoke(pixivIllustId!!, add) == true
                                                pixivBusy = false
                                                if (ok) {
                                                    pixivFav = add
                                                    notice = if (add) "已加入 pixiv 收藏。" else "已移出 pixiv 收藏。"
                                                }
                                            }
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = if (active) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = "收藏",
                                tint = if (active) Color(0xFFF28B82) else Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }

                    // 下载（支持 Booru 原图与 Pixiv 存图）
                    val canSave = (current.sourcePost != null && onSaveImage != null) || onSavePixivImage != null
                    if (canSave) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    scope.launch {
                                        saving = true
                                        notice = runCatching {
                                            if (current.sourcePost != null && onSaveImage != null) {
                                                onSaveImage(current)
                                            } else if (onSavePixivImage != null) {
                                                onSavePixivImage(current)
                                            } else {
                                                "无法存图"
                                            }
                                        }.getOrElse { "存图失败：${it::class.java.simpleName}" }
                                        saving = false
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FileDownload,
                                contentDescription = "下载原图",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }

                    // 复制图片：转圈 → 对勾 / 叉号，结果同时落到下方读数行。
                    if (onCopyImage != null) {
                        val copyFeedback = rememberDesktopCopyFeedback()
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    copyFeedback.run {
                                        val ok = runCatching { onCopyImage(current) }.getOrDefault(false)
                                        notice = if (ok) {
                                            "已复制图片到剪贴板。"
                                        } else {
                                            "复制失败：这张图没能放进剪贴板。"
                                        }
                                        ok
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            DesktopCopyGlyph(
                                state = copyFeedback.state,
                                idleTint = Color.White,
                                doneTint = Color(0xFF7FE0B4),
                                failedTint = Color(0xFFFF8A8A),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }

                    // ── 动图/视频（mp4/webm/gif）：切换内嵌播放；视频无 VLC 由播放器如实回报，gif 走 skia 逐帧 ──
                    if (currentIsAnimated) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (videoPlaying) tokens.color.primary.copy(alpha = 0.25f) else Color.Transparent)
                                .clickable { videoPlaying = !videoPlaying },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = if (videoPlaying) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                                contentDescription = if (videoPlaying) "停止播放" else "内嵌播放动图",
                                tint = if (videoPlaying) tokens.color.primary else Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }

                    // 抽屉展开按钮
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (drawerOpen) tokens.color.primary.copy(alpha = 0.25f) else Color.Transparent)
                            .clickable { drawerOpen = !drawerOpen },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Tag,
                            contentDescription = "详细标签",
                            tint = if (drawerOpen) tokens.color.primary else Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            if (notice != null) {
                Text(
                    text = notice.orEmpty(),
                    fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                    color = Color.White.copy(alpha = 0.82f),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
        },
    ) {
        // 画布区域
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { boxPx = it }
                    .clipToBounds()
                    .graphicsLayer {
                        scaleX = zoom
                        scaleY = zoom
                        translationX = pan.x
                        translationY = pan.y
                    }
                    .pointerInput(current.uid) {
                        detectTapGestures(
                            onTap = {
                                if (drawerOpen) drawerOpen = false else chromeVisible = !chromeVisible
                            },
                            onDoubleTap = { at -> toggleZoom(at) },
                        )
                    }
                    .pointerInput(current.uid, zoomed) {
                        if (zoomed) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                pan += dragAmount
                            }
                        }
                    },
            ) {
                if (!videoCoversStatic) {
                    ViewerImage(
                        url = openUrl,
                        loader = loader,
                        description = current.title,
                        onSuccess = { openOk = true; openError = false },
                        onError = { openError = true },
                    )
                    if (hasFullLayer) {
                        ViewerImage(
                            url = fullUrl,
                            loader = loader,
                            description = current.title,
                            onSuccess = { fullOk = true; fullError = false },
                            onError = { fullError = true },
                        )
                    }
                }
                // ── ugoira 就地逐帧（叠在静帧之上；未就绪/失败仍留静帧，不白屏）──
                val viewerReadyClip = (ugoiraPlayback as? DesktopUgoiraState.Ready)?.clip
                if (viewerReadyClip != null && !videoCoversStatic) {
                    DesktopUgoiraFrameRenderer(
                        clip = viewerReadyClip,
                        paused = false,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            // ── 视频**回调面内嵌播放**（主人定案）：帧解码到内存、Compose 画进画面、不吃画布缩放 ──
            if (currentIsVideo && videoPlaying) {
                DesktopVideoPlayer(
                    mediaUrl = currentSource?.videoUrl.orEmpty(),
                    headers = desktopVideoHeadersFor(currentSource?.videoUrl.orEmpty()),
                    engineKey = current.uid,
                    onDismiss = { videoPlaying = false },
                    onUnavailable = {
                        videoPlaying = false
                        notice = "本机没有 VLC，装一个才能播视频。"
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // ── booru animated gif：skia Codec 逐帧就地播（不进 VLC），叠在静帧之上自动循环 ──
            if (currentIsGif && videoPlaying) {
                DesktopAnimatedImage(
                    url = currentSource?.fileUrl.orEmpty(),
                    paths = paths,
                    key = current.uid,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                    onError = {},
                )
            }

            if (zoomed) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "${(zoom * 100).toInt()}% · 双击复位 · Ctrl+滚轮缩放",
                        fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 64.dp)
                            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(50))
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }

            if (nothingShown) {
                Text(
                    text = "这一张没加载出来：站方给的图档取不到，或那一格本来就是空的。",
                    fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                    color = Color.White,
                    modifier = Modifier.padding(tokens.spacing.space4),
                )
            }

            // ⭐️ 右侧滑入式信息与标签抽屉 (Inspector Drawer)
            AnimatedVisibility(
                visible = drawerOpen,
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                enter = slideInHorizontally { it } + fadeIn(),
                exit = slideOutHorizontally { it } + fadeOut(),
            ) {
                Column(
                    modifier = Modifier
                        .width(360.dp)
                        .fillMaxHeight()
                        .vShadow(28.dp, androidx.compose.ui.graphics.RectangleShape)
                        .background(Color(0xF00A0F14))
                        .border(1.dp, Color.White.copy(alpha = 0.14f))
                        .verticalScroll(rememberScrollState())
                        .padding(tokens.spacing.space4),
                    verticalArrangement = Arrangement.spacedBy(tokens.spacing.space3),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "图片参数与全量标签",
                            fontSize = VeneraType.body.size, lineHeight = VeneraType.body.lineHeight,
                            fontWeight = VeneraType.cardTitle.weight,
                            color = Color.White,
                        )
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp).clickable { drawerOpen = false },
                        )
                    }

                    // 参数事实
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(text = "编号: ${current.uid}", fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight, color = Color.White.copy(alpha = 0.8f))
                        if (current.author.isNotBlank()) {
                            Text(text = "画师: ${current.author}", fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight, color = tokens.color.primary)
                        }
                    }

                    // 全量标签墙
                    if (tags.isNotEmpty()) {
                        Text(
                            text = "标签列表 (${tags.size}) · 点击检索",
                            fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                            fontWeight = VeneraType.cardTitle.weight,
                            color = Color.White.copy(alpha = 0.7f),
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space1),
                            verticalArrangement = Arrangement.spacedBy(tokens.spacing.space1),
                        ) {
                            tags.forEach { tag ->
                                VeneraTagChip(
                                    text = tag,
                                    onClick = if (tagSite != null) {
                                        {
                                            drawerOpen = false
                                            handoff(listOf(tagSite), tag)
                                        }
                                    } else {
                                        null
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 一张铺满槽位的图。 */
@Composable
private fun ViewerImage(
    url: String,
    loader: ImageLoader?,
    description: String,
    onSuccess: () -> Unit,
    onError: () -> Unit,
) {
    if (url.isBlank()) return
    if (loader != null) {
        AsyncImage(
            model = url,
            imageLoader = loader,
            contentDescription = description,
            contentScale = ContentScale.Fit,
            onSuccess = { onSuccess() },
            onError = { onError() },
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        AsyncImage(
            model = url,
            contentDescription = description,
            contentScale = ContentScale.Fit,
            onSuccess = { onSuccess() },
            onError = { onError() },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** 开门那一档。 */
internal fun desktopViewerOpenUrl(post: DesktopWallCardModel): String =
    post.fastUrl.ifBlank { post.url }.ifBlank { post.largeUrl }

/** 目标那一档。 */
internal fun desktopViewerFullUrl(
    post: DesktopWallCardModel,
    tier: com.venera.compose.gallery.domain.PixivImageQuality = com.venera.compose.gallery.domain.PixivImageQuality.LARGE,
): String = when (tier) {
    // 低画质：只换 pximg 的尺寸前缀，路径不动。
    com.venera.compose.gallery.domain.PixivImageQuality.SMALL ->
        com.venera.compose.gallery.domain.pixivSmallUrlOf(post.fastUrl.ifBlank { post.largeUrl })
            .ifBlank { post.url }
    com.venera.compose.gallery.domain.PixivImageQuality.MEDIUM ->
        post.fastUrl.ifBlank { post.largeUrl }.ifBlank { post.url }
    com.venera.compose.gallery.domain.PixivImageQuality.LARGE ->
        post.largeUrl.ifBlank { post.fastUrl }.ifBlank { post.url }
    com.venera.compose.gallery.domain.PixivImageQuality.ORIGINAL ->
        post.fileUrl.ifBlank { post.largeUrl }.ifBlank { post.fastUrl }.ifBlank { post.url }
}

/** 看图那一屏上「点标签回搜」那一路的所属站判据。 */
internal fun desktopViewerTagSite(post: DesktopWallCardModel): GallerySite? =
    GallerySite.fromRouteKey(post.uid.substringBefore(':', missingDelimiterValue = ""))
        ?.takeIf { it != GallerySite.PIXIV }

/** 播放器操作条的一枚。 */
@Composable
private fun ViewerCircleAction(
    glyph: String,
    label: String,
    onClick: () -> Unit,
    accent: Boolean = false,
    enabled: Boolean = true,
) {
    val tokens = VeneraTokens
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.let { if (enabled) it.then(Modifier.clickable(onClick = onClick)) else it },
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    if (accent) tokens.color.primary.copy(alpha = 0.30f) else Color.Black.copy(alpha = 0.55f),
                    RoundedCornerShape(50),
                )
                .border(1.dp, if (accent) tokens.color.primary else Color.White.copy(alpha = 0.22f), RoundedCornerShape(50)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = glyph,
                fontSize = VeneraType.body.size, lineHeight = VeneraType.body.lineHeight,
                color = if (accent) tokens.color.primary else Color.White,
            )
        }
        Text(
            text = label,
            fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
            color = if (accent) tokens.color.primary else Color.White.copy(alpha = 0.72f),
        )
    }
}
