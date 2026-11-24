package com.venera.desktop.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.jetbrains.skia.Image as SkiaImage
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.app.DesktopRoute
import com.venera.desktop.gallery.data.DesktopAscii2dBridge
import com.venera.desktop.ui.DesktopPushButton
import com.venera.desktop.ui.components.DesktopPetCue
import com.venera.desktop.ui.components.DesktopPetCueBus
import com.venera.desktop.ui.components.DesktopPetPayload
import kotlinx.coroutines.launch
import java.awt.Rectangle
import java.awt.Window
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.roundToInt

/**
 * AWT 拖拽/剪贴板监听器只在首帧建一次；它们持有的必须是一枚**长活**引用，
 * 每次重组把最新 composition 的写入逻辑刷新进去，否则会写旧 state、
 * 用已离场的 rememberCoroutineScope（真窗报 "left the composition"）。
 */
private class ImageSink {
    var onImage: (Pair<ByteArray, String>) -> Unit = {}
    var onDrag: (Boolean) -> Unit = {}
}

/** 反搜输入链路诊断：逐跳落盘，主人跑一轮把这份日志发回即可定位。 */
private fun reverseLog(line: String) {
    runCatching {
        val dir = File(System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"), "venera/logs")
        dir.mkdirs()
        File(dir, "reverse-input.log").appendText(
            java.time.LocalDateTime.now().withNano(0).toString() + " " + line + System.lineSeparator(),
        )
    }
}

/** pixiv 作品页两种形状（www/sp）的作品 id 提取；提取失败走外链。 */
private val PIXIV_ILLUST_RE = Regex("""pixiv\.net/(?:artworks|i)/(\d+)""")

/** yande.re / Gelbooru 单页 id：命中结果里认出这两站就走软件内作品详情页。 */
private val BOORU_ID_RE = Regex("""(?:(?:yande\.re/post/show/)|(?:gelbooru\.com/index\.php\?page=post&s=view&id=))(\d+)""")

private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "gif", "webp")

/**
 * 命中页 URL → 站内原模型。只认 yande.re / Gelbooru 两站的单页地址，
 * 其余（含 pixiv 由调用点单独走 PixivWork）交 null 走外链。
 * 缩略图是页面内转好的 dataURL，直接当预览档喂详情页，不碰网络。
 */
private fun booruPostOf(pageUrl: String?, thumbnailUrl: String): com.venera.compose.gallery.data.GalleryPost? {
    val url = pageUrl ?: return null
    val site = if (url.contains("yande.re")) com.venera.compose.gallery.data.GallerySite.YANDERE
        else if (url.contains("gelbooru.com")) com.venera.compose.gallery.data.GallerySite.GELBOORU
        else return null
    val id = BOORU_ID_RE.find(url)?.groupValues?.get(1)?.toLongOrNull() ?: return null
    return com.venera.compose.gallery.data.GalleryPost(
        site = site,
        id = id,
        previewUrl = thumbnailUrl,
        largeUrl = thumbnailUrl,
        fileUrl = thumbnailUrl,
    )
}

/**
 * 桌面「以图搜图」一级页（reverse-search-primary 全量落地）。
 *
 * 无页头标题、内容全额铺满（令）。投放台四通道（Ctrl+V 粘贴 /
 * 拖拽 / 挑图 / URL）都先显示原图预览再发车；粘贴与拖拽走 Compose 输入系统 +
 * SwingPanel 的 AWT 拖拽目标（AWTEventListener / 直接 setDropTarget 会被 Compose 吞）。
 */
@Composable
internal fun DesktopReverseSearchScreen(
    mode: WideScreenLayoutMode,
    onBack: () -> Unit,
    onOpenRoute: (DesktopRoute) -> Unit,
    /** yande.re / Gelbooru 命中走软件内作品详情页（壳层统一开门那颗）。 */
    onOpenPost: (com.venera.desktop.gallery.ui.DesktopWallCardModel) -> Unit,
) {
    val tokens = VeneraTokens
    val bridge = remember { DesktopAscii2dBridge.getInstance() }
    val phase by bridge.phase.collectAsState()
    val scope = rememberCoroutineScope()

    // 这些 state 必须 remember：写 state 会触发重组，不 remember 就会被重新创建、
    // 值立刻丢回初始值 —— 「原图一直不显示」「URL 框打不了字」都是这一处。
    var input by remember { mutableStateOf(bridge.inputText) }
    var panelBounds by remember { mutableStateOf<Rectangle?>(null) }
    var dragOver by remember { mutableStateOf(false) }
    /** 已投进来的原图（PNG 字节）——挂在单例桥上，离屏再返回不丢。 */
    var pickedBytes by remember { mutableStateOf(bridge.pickedBytes) }
    // 输入写回桥：屏的 state 负责即时重组，桥负责跨屏存活。
    LaunchedEffect(input) { bridge.inputText = input }
    LaunchedEffect(pickedBytes) { bridge.pickedBytes = pickedBytes }

    val window: Window? = remember { java.awt.Frame.getFrames().firstOrNull() }
    // onPreviewKeyEvent 要焦点才收得到 —— 进屏即请求，Ctrl+V 才有得谈
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // 不 reset：单例桥上的结果与原图跨进屏保留（令「从其他页面返回不该全部重置」）。
        focusRequester.requestFocus()
    }

    fun runSearch(block: suspend () -> Unit) {
        scope.launch {
            runCatching { block() }.onFailure { e ->
                bridge.phase.value = DesktopAscii2dBridge.Phase.Failed(
                    com.venera.desktop.platform.desktopFailureNoteOf(e),
                )
            }
        }
    }

    fun searchBytes(bytes: ByteArray, name: String) {
        pickedBytes = bytes
        DesktopPetCueBus.emit(DesktopPetCue.LOADING, DesktopPetPayload(source = "以图搜图"))
        runSearch { bridge.searchByFile(bytes, name, window) { panelBounds } }
    }

    /**
     * AWT 拖拽 / 剪贴板监听器只在首帧建一次，它们捕获的 lambda 是**旧 composition** 的，
     * 直接调 searchBytes 会写旧 state / 用已离场的 rememberCoroutineScope
     *（真窗报 "rememberCoroutineScope left the composition" 就是这个）。
     * 所以所有外部输入只把字节写进这一枚 state，由**当前 composition** 的 LaunchedEffect 消费：
     * 立刻显示预览 + 用活着的 scope 发车。
     */
    /**
     * ⚠️ 预览显示**绝不走 LaunchedEffect**：之前写法在 effect 里先 `pendingImage = null`
     * 会立刻触发重组把自己取消，导致后面的 `pickedBytes = ...` 根本不执行
     *（五轮"原图"不变的真凶）。现在 sink 回调里同步写 pickedBytes（当前 composition，
     * 立即重组显示），发车另走一枚独立的请求 state，互不干扰。
     */
    var searchRequest by remember { mutableStateOf<Pair<ByteArray, String>?>(null) }

    /** AWT 监听器只建一次，通过这枚长活 holder 调**最新** composition 的写入逻辑。 */
    val imageSink = remember { ImageSink() }
    imageSink.onImage = { (bytes, name) ->
        reverseLog("sink.onImage bytes=${bytes.size} name=$name")
        pickedBytes = bytes
        searchRequest = bytes to name
    }
    imageSink.onDrag = { dragOver = it }

    LaunchedEffect(searchRequest) {
        val req = searchRequest ?: return@LaunchedEffect
        searchRequest = null
        DesktopPetCueBus.emit(DesktopPetCue.LOADING, DesktopPetPayload(source = "以图搜图"))
        runCatching { bridge.searchByFile(req.first, req.second, window) { panelBounds } }
            .onFailure { e ->
                bridge.phase.value = DesktopAscii2dBridge.Phase.Failed(
                    com.venera.desktop.platform.desktopFailureNoteOf(e),
                )
            }
    }

    // 软件窗口移动/缩放时，面板里的 Edge 窗要跟着走
    DisposableEffect(window) {
        val listener = object : ComponentAdapter() {
            override fun componentMoved(e: ComponentEvent) = reflow()
            override fun componentResized(e: ComponentEvent) = reflow()
            private fun reflow() {
                val rect = panelBounds ?: return
                bridge.attachPanel(rect)
            }
        }
        window?.addComponentListener(listener)
        onDispose { window?.removeComponentListener(listener) }
    }

    // 到站收面板 + 桌宠播报
    LaunchedEffect(phase) {
        when (val p = phase) {
            is DesktopAscii2dBridge.Phase.Done -> {
                bridge.hidePanel()
                if (p.hits.isEmpty()) {
                    DesktopPetCueBus.emit(DesktopPetCue.SEARCH_EMPTY, DesktopPetPayload(source = "以图搜图"))
                } else {
                    DesktopPetCueBus.emit(
                        DesktopPetCue.LOAD_DONE,
                        DesktopPetPayload(source = "以图搜图", tag = "${p.hits.size} 张相似"),
                    )
                }
            }
            is DesktopAscii2dBridge.Phase.Failed -> {
                bridge.hidePanel()
                DesktopPetCueBus.emit(DesktopPetCue.ERROR, null)
            }
            else -> Unit
        }
    }

    // Ctrl+V：走 Compose 输入系统（AWTEventListener 在 ComposeWindow 上收不到），
    // 但输入框有焦点时不抢文本粘贴 —— 由 URL 输入框自己处理。
    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown || ev.key != Key.V || !ev.isCtrlPressed) {
                    return@onPreviewKeyEvent false
                }
                // 剪贴板里是位图或图片文件才接管（文本留给 URL 框自己粘）
                reverseLog("Ctrl+V 按下")
                val req = clipboardImageRequest()
                reverseLog("剪贴板读取: ${if (req == null) "无图" else "${req.first.size} 字节"}")
                if (req == null) return@onPreviewKeyEvent false
                imageSink.onImage(req)
                true
            }
            .padding(horizontal = tokens.spacing.screenHorizontal, vertical = 18.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (phase is DesktopAscii2dBridge.Phase.WaitingChallenge) {
                val p = phase as DesktopAscii2dBridge.Phase.WaitingChallenge
                ChallengePanel(
                    hint = p.hint,
                    onBounds = { panelBounds = it; bridge.attachPanel(it) },
                )
            } else {
                // ── 大投放台 ──
                DropZone(
                    dragOver = dragOver,
                    pickedBytes = pickedBytes,
                    input = input,
                    onInput = { input = it },
                    imageSink = imageSink,
                    onSearchUrl = {
                        if (input.isNotBlank()) {
                            pickedBytes = null
                            DesktopPetCueBus.emit(DesktopPetCue.SEARCH, DesktopPetPayload(source = "以图搜图"))
                            runSearch { bridge.searchByUrl(input.trim(), window) { panelBounds } }
                        }
                    },
                    onPickFile = {
                        val path = desktopPickLocalImage(window as? java.awt.Frame) ?: return@DropZone
                        val file = File(path)
                        searchBytes(file.readBytes(), file.name)
                    },
                )

                // ── 引擎条 + 实时状态 ──
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    EnginePill("ascii2d", selected = true)
                    Spacer(Modifier.width(4.dp))
                    when (val p = phase) {
                        is DesktopAscii2dBridge.Phase.Navigating -> StatusDot("正在打开搜索页…", ok = false)
                        is DesktopAscii2dBridge.Phase.Polling -> StatusDot("轮询中：${p.note}", ok = false)
                        is DesktopAscii2dBridge.Phase.Reading -> StatusDot("正在读取结果…", ok = false)
                        is DesktopAscii2dBridge.Phase.Failed -> StatusDot("没成：${p.reason}", ok = false, failed = true)
                        else -> Unit
                    }
                }

                // ── 结果网格 / 空态 ──
                val hits = (phase as? DesktopAscii2dBridge.Phase.Done)?.hits.orEmpty()
                if (hits.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("命中结果", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = tokens.color.onSurface)
                        Spacer(Modifier.width(8.dp))
                        Text("${hits.size} 条", fontSize = 12.sp, color = tokens.color.onSurfaceVariant)
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(hits, key = { it.rank }) { hit ->
                            Ascii2dResultCard(
                                hit = hit,
                                onOpen = {
                                    val illustId = PIXIV_ILLUST_RE.find(hit.pageUrl.orEmpty())?.groupValues?.get(1)
                                    val booru = booruPostOf(hit.pageUrl, hit.thumbnailUrl)
                                    when {
                                        illustId != null -> {
                                            DesktopPetCueBus.emit(
                                                DesktopPetCue.OPEN_IMAGE,
                                                DesktopPetPayload(
                                                    title = hit.dateText.orEmpty(),
                                                    author = hit.artistName.orEmpty(),
                                                    source = "pixiv",
                                                ),
                                            )
                                            onOpenRoute(
                                                DesktopRoute.PixivWork(
                                                    illustId = illustId.toLong(),
                                                    title = hit.sizeText ?: "pixiv 作品",
                                                    coverUrl = hit.thumbnailUrl,
                                                    from = DesktopRoute.GalleryReverseSearch,
                                                ),
                                            )
                                        }
                                        booru != null -> {
                                            DesktopPetCueBus.emit(
                                                DesktopPetCue.OPEN_IMAGE,
                                                DesktopPetPayload(
                                                    title = hit.sizeText.orEmpty(),
                                                    author = hit.artistName.orEmpty(),
                                                    source = booru.site.displayName,
                                                ),
                                            )
                                            onOpenPost(
                                                com.venera.desktop.gallery.ui.desktopWallCardOf(
                                                    booru,
                                                    com.venera.desktop.gallery.ui.DESKTOP_WALL_QUALITY,
                                                ),
                                            )
                                        }
                                        else -> scope.launch { openInBrowser(hit.pageUrl.orEmpty()) }
                                    }
                                },
                                onExternal = {
                                    scope.launch { openInBrowser(hit.pageUrl.orEmpty()) }
                                },
                            )
                        }
                    }
                } else if (phase is DesktopAscii2dBridge.Phase.Idle) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "结果会列在这里",
                        fontSize = 12.5.sp,
                        color = tokens.color.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp, vertical = 20.dp),
                    )
                }
            }
        }
    }
}

/**
 * 剪贴板里取一张图 → (字节, 文件名)。两条都认：
 * ① `imageFlavor` = 截图工具直接复制的位图；② `javaFileListFlavor` = 资源管理器里
 * 「复制」的图片文件（主人真窗反馈"复制图片没反应"就是漏了这一档）。
 * 不是图交 null，让 URL 框自己处理文本粘贴。
 */
private fun clipboardImageRequest(): Pair<ByteArray, String>? = runCatching {
    val cb = java.awt.Toolkit.getDefaultToolkit().systemClipboard
    val content = cb.getContents(null) ?: return null

    // ① 位图（截图）
    if (content.isDataFlavorSupported(DataFlavor.imageFlavor)) {
        val img = content.getTransferData(DataFlavor.imageFlavor) as java.awt.Image
        val w = img.getWidth(null)
        val h = img.getHeight(null)
        if (w > 0 && h > 0) {
            val bimg = java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB)
            val g = bimg.createGraphics()
            g.drawImage(img, 0, 0, null)
            g.dispose()
            val bos = ByteArrayOutputStream()
            ImageIO.write(bimg, "png", bos)
            return bos.toByteArray() to "clipboard.png"
        }
    }

    // ② 图片文件（资源管理器复制的）
    if (content.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
        val files = content.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>
        val img = files?.filterIsInstance<File>()
            ?.firstOrNull { it.extension.lowercase() in IMAGE_EXT } ?: return null
        return img.readBytes() to img.name
    }
    null
}.getOrNull()

/**
 * 大投放台：左半是原图预览（有图显示真图、无图显示提示），右半是文案 + 三通道 chips。
 * AWT 拖拽目标挂在 [SwingPanel] 里的 JPanel 上 —— Compose 画布会吞掉直接挂在
 * ComposeWindow 上的 DropTarget，这一层 interop 是收得到文件拖入的可靠路。
 */
@Composable
private fun DropZone(
    dragOver: Boolean,
    pickedBytes: ByteArray?,
    input: String,
    onInput: (String) -> Unit,
    imageSink: ImageSink,
    onSearchUrl: () -> Unit,
    onPickFile: () -> Unit,
) {
    val tokens = VeneraTokens
    val borderColor = if (dragOver) tokens.color.primary else tokens.color.outlineVariant.copy(alpha = 0.7f)

    // 拖拽：DropTarget 挂在窗口的 AWT contentPane（Compose 画布的宿主），
    // 不新建任何可见 AWT 组件 ⇒ 不会盖住预览图或输入框。
    DisposableEffect(Unit) {
        val win = java.awt.Frame.getFrames().firstOrNull() as? javax.swing.JFrame
        val pane = win?.contentPane
        val target = if (pane != null) DropTarget(pane, object : DropTargetAdapter() {
            override fun dragEnter(e: DropTargetDragEvent) {
                imageSink.onDrag(true)
            }

            override fun dragExit(e: java.awt.dnd.DropTargetEvent) {
                imageSink.onDrag(false)
            }

            override fun drop(e: DropTargetDropEvent) {
                imageSink.onDrag(false)
                e.acceptDrop(DnDConstants.ACTION_COPY)
                val files = runCatching {
                    e.transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>
                }.getOrNull()?.filterIsInstance<File>()
                e.dropComplete(true)
                val img = files?.firstOrNull { it.extension.lowercase() in IMAGE_EXT } ?: return
                imageSink.onImage(img.readBytes() to img.name)
            }
        }) else null
        pane?.dropTarget = target
        onDispose { pane?.dropTarget = null }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(16.dp)),
    ) {
        // 预览框纯 Compose 独占，不与任何 AWT 重量组件重叠（前两版糊背景已根治）
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clickable { onPickFile() }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左：原图预览，纯 Compose 独占（不与 AWT 重量组件重叠，绝不糊）
            Box(
                modifier = Modifier
                    .size(width = 108.dp, height = 118.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(tokens.color.surfaceVariant)
                    .border(1.dp, tokens.color.outlineVariant, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                // 不能 .use{}：toComposeImageBitmap() 与 skia Image 共享原生像素，
                // 把 Image 关了 bitmap 底图就空了（灰块第二个真凶）。
                val bmp: ImageBitmap? = remember(pickedBytes) {
                    pickedBytes?.let { bytes ->
                        runCatching { SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()
                    }
                }
                if (bmp != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bmp,
                        contentDescription = "待搜索的图片",
                        modifier = Modifier.fillMaxSize().padding(4.dp),
                    )
                } else {
                    Text("原图", fontSize = 11.sp, color = tokens.color.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "把图放到这里",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.color.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "粘贴、拖入，或从本机挑一张",
                    fontSize = 12.5.sp,
                    color = tokens.color.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChannelChip("Ctrl+V", "粘贴截图", isHot = true)
                    ChannelChip("拖拽", "图片文件")
                    ChannelChip("挑图…", "本机选择")
                    // 可见粘贴按钮：不靠键盘焦点，点一下直接读剪贴板（位图或图片文件）
                    DesktopPushButton(onClick = {
                        reverseLog("粘贴按钮点击")
                        val req = clipboardImageRequest()
                        reverseLog("剪贴板读取: ${if (req == null) "无图" else "${req.first.size} 字节"}")
                        req?.let { imageSink.onImage(it) }
                    }) {
                        Text("粘贴", fontSize = 11.5.sp)
                    }
                }
                // 诊断读数：通道通没通、图有没有到，一眼可见
                if (pickedBytes != null) {
                    Text(
                        "已收到 ${pickedBytes!!.size / 1024} KB",
                        fontSize = 10.5.sp,
                        color = tokens.color.primary,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
        // URL 行贴底
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            com.venera.desktop.ui.components.DesktopInputField(
                value = input,
                onValueChange = onInput,
                modifier = Modifier.weight(1f),
                placeholder = "或粘贴图片链接 https:// …",
            )
            DesktopPushButton(onClick = onSearchUrl, enabled = input.isNotBlank()) {
                Text("搜索", fontSize = 12.sp)
            }
        }
    }
    }
}

@Composable
private fun ChannelChip(hot: String, label: String, isHot: Boolean = false) {
    val tokens = VeneraTokens
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (isHot) tokens.color.primary.copy(alpha = 0.14f) else tokens.color.surfaceContainerHigh)
            .border(
                1.dp,
                if (isHot) tokens.color.primary.copy(alpha = 0.45f) else tokens.color.outlineVariant,
                RoundedCornerShape(9.dp),
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(hot, fontSize = 11.5.sp, fontWeight = FontWeight.Medium, color = tokens.color.onSurface)
        Text(label, fontSize = 11.5.sp, color = tokens.color.onSurfaceVariant)
    }
}

/** 引擎 pill（反搜现只剩 ascii2d 一条腿）。 */
@Composable
private fun EnginePill(label: String, selected: Boolean) {
    val tokens = VeneraTokens
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) tokens.color.primary else tokens.color.surfaceContainerHigh)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) Color.White else tokens.color.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatusDot(text: String, ok: Boolean, failed: Boolean = false) {
    val tokens = VeneraTokens
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(
                    when {
                        failed -> tokens.color.error
                        ok -> Color(0xFF3BAE85)
                        else -> Color(0xFFE0A33A)
                    },
                ),
        )
        Text(
            text,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (failed) tokens.color.error else tokens.color.onSurfaceVariant,
        )
    }
}

/** 结果卡：双列大卡；pixiv 卡整卡描边高亮 + 主按钮「在软件内打开」。 */
@Composable
private fun Ascii2dResultCard(
    hit: DesktopAscii2dBridge.Ascii2dHit,
    onOpen: () -> Unit,
    onExternal: () -> Unit,
) {
    val tokens = VeneraTokens
    val isPixiv = hit.pageUrl.orEmpty().contains("pixiv.net")
    val isInApp = isPixiv || booruPostOf(hit.pageUrl, hit.thumbnailUrl) != null
    val cardBorder = when {
        isInApp -> Color(0xFF0096FA).copy(alpha = 0.4f)
        else -> tokens.color.outlineVariant.copy(alpha = 0.6f)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(tokens.color.surface)
            .border(1.dp, cardBorder, RoundedCornerShape(12.dp))
            .clickable(enabled = hit.pageUrl != null) { onOpen() },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.55f)
                .background(tokens.color.surfaceVariant),
        ) {
            AsyncImage(
                model = hit.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().padding(8.dp),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(if (hit.rank == 1) tokens.color.primary else tokens.color.surfaceContainerHigh.copy(alpha = 0.85f))
                    .border(
                        1.dp,
                        if (hit.rank == 1) Color.Transparent else tokens.color.primary.copy(alpha = 0.45f),
                        RoundedCornerShape(99.dp),
                    )
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            ) {
                Text(
                    "#${hit.rank}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (hit.rank == 1) Color.White else tokens.color.onSurface,
                )
            }
            if (!hit.siteLabel.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(tokens.color.surfaceContainerHigh.copy(alpha = 0.85f))
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(if (isPixiv) Color(0xFF0096FA) else tokens.color.onSurfaceVariant),
                    )
                    Text(hit.siteLabel, fontSize = 11.sp, color = tokens.color.onSurfaceVariant)
                }
            }
        }
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                hit.sizeText ?: "尺寸未知",
                fontSize = 12.sp,
                color = tokens.color.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(hit.artistName, hit.dateText).joinToString(" · ").ifBlank { "出处未知" },
                fontSize = 12.sp,
                color = tokens.color.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(tokens.color.surfaceVariant.copy(alpha = 0.5f))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (isInApp) {
                DesktopPushButton(
                    onClick = onOpen,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("在软件内打开", fontSize = 12.sp, color = Color(0xFF0096FA))
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
            DesktopPushButton(
                onClick = onExternal,
                enabled = hit.pageUrl != null,
            ) {
                Text("外链", fontSize = 12.sp)
            }
        }
    }
}

/** 挑战面板：真 Edge 就嵌在这一块里；矩形换算屏幕物理像素后交桥。 */
@Composable
private fun ChallengePanel(hint: String, onBounds: (Rectangle) -> Unit) {
    val tokens = VeneraTokens
    val window: Window? = remember { java.awt.Frame.getFrames().firstOrNull() }
    var panelPx by remember { mutableStateOf(IntSize.Zero) }
    var panelOrigin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

    fun publish() {
        val win = window ?: return
        if (panelPx.width == 0 || panelPx.height == 0) return
        val cfg = win.graphicsConfiguration
        val scale = cfg.defaultTransform.scaleX.toDouble().takeIf { it > 0 } ?: 1.0
        val screenX = win.locationOnScreen.x + (panelOrigin.x * scale).roundToInt()
        val screenY = win.locationOnScreen.y + (panelOrigin.y * scale).roundToInt()
        onBounds(
            Rectangle(
                screenX, screenY,
                (panelPx.width * scale).roundToInt(),
                (panelPx.height * scale).roundToInt(),
            ),
        )
    }

    Column {
        Text(
            hint,
            fontSize = 12.sp,
            color = tokens.color.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tokens.color.surface)
                .border(
                    1.dp,
                    tokens.color.outlineVariant,
                    RoundedCornerShape(12.dp),
                )
                .onGloballyPositioned { coords ->
                    panelPx = coords.size
                    panelOrigin = coords.positionInWindow()
                    publish()
                },
        )
    }
    LaunchedEffect(panelOrigin, panelPx) { publish() }
}
