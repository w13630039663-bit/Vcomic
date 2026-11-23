package com.venera.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowExceptionHandler
import androidx.compose.ui.window.WindowExceptionHandlerFactory
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.gallery.ui.GalleryTokens
import com.venera.desktop.settings.DesktopI18n
import com.venera.desktop.ui.components.DesktopGlassCardDefaults
import com.venera.desktop.ui.components.DesktopGlassCardLevel
import com.venera.desktop.ui.theme.VeneraType
import com.venera.desktop.ui.vShadow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicLong

/**
 * 全局报错总线与界面报错提示：
 * 1. 保留 [DesktopErrorBus.latest] 供桌宠与崩溃落盘消费；
 * 2. 新增 [DesktopErrorBus.activeToast] 与 [DesktopErrorBus.issues]，把静默日志（图片加载失败、
 *    引擎启动失败、搜索/探索/设置接口异常、未捕获异常）同步展示到界面右下角提示条与错误详情面板。
 */
object DesktopErrorBus {

    private const val MAX_HISTORY = 40
    private const val DEDUP_WINDOW_MS = 12_000L

    /** 未捕获异常的桌宠订阅条目。 */
    data class Entry(
        val id: Long,
        val thread: String,
        val headline: String,
        val reading: String,
        val logPath: Path?,
        val atMs: Long,
    )

    /** 面向界面展示的可读错误条目（已做凭据脱敏与同类计数聚合）。 */
    data class IssueRecord(
        val id: Long,
        val category: String,
        val headline: String,
        val humanReason: String = "",
        val detail: String,
        val count: Int = 1,
        val atMs: Long = System.currentTimeMillis(),
        val dedupKey: String = "",
    )

    private val counter = AtomicLong()
    private val issueCounter = AtomicLong()

    private val _latest = MutableStateFlow<Entry?>(null)
    val latest: StateFlow<Entry?> = _latest.asStateFlow()

    private val _activeToast = MutableStateFlow<IssueRecord?>(null)
    val activeToast: StateFlow<IssueRecord?> = _activeToast.asStateFlow()

    private val _issues = MutableStateFlow<List<IssueRecord>>(emptyList())
    val issues: StateFlow<List<IssueRecord>> = _issues.asStateFlow()

    private val _panelOpen = MutableStateFlow(false)
    val panelOpen: StateFlow<Boolean> = _panelOpen.asStateFlow()

    private val lock = Any()

    /** 启动阶段首页封面报错只收进右下角小胶囊，不主动弹 Toast 打扰；由应用入口开启，离开首页后解除。 */
    @Volatile
    var suppressStartupHomeImageToast: Boolean = false

    /** handler 调：塞最新一笔未捕获异常并同步写入界面报错中心。 */
    fun push(thread: String, error: Throwable, logPath: Path?) {
        val headline = runCatching {
            "${desktopFailureType(error)}: ${desktopOneLineMessage(error).take(120)}"
        }.getOrDefault(desktopFailureType(error))
        val reading = desktopCrashReading(thread, error, counter.get().toInt() + 1)
        _latest.value = Entry(
            id = counter.incrementAndGet(),
            thread = thread,
            headline = headline,
            reading = reading,
            logPath = logPath,
            atMs = System.currentTimeMillis(),
        )
        val detail = buildString {
            append(reading)
            if (logPath != null) append("\n日志路径: ").append(logPath.toAbsolutePath())
        }
        report(
            category = "系统",
            headline = headline,
            humanReason = "程序运行中遇到未捕获异常，可展开详情或复制提交反馈。",
            detail = detail,
            dedupKey = "crash:${desktopFailureType(error)}:${headline.take(40)}",
        )
    }

    /** 业务侧主动上报错误到界面提示与最近错误列表（自动做凭据脱敏与时间窗聚合）。 */
    fun report(
        category: String,
        headline: String,
        detail: String = "",
        dedupKey: String? = null,
        showToast: Boolean = true,
        humanReason: String = "",
    ) {
        val cleanCategory = normalizeIssueCategory(category)
        val cleanHeadline = desktopScrubSecrets(headline).trim().ifEmpty { "操作失败" }
        val cleanReason = desktopScrubSecrets(humanReason).trim()
        val cleanDetail = desktopScrubSecrets(detail).trim()
        val now = System.currentTimeMillis()
        val key = dedupKey ?: "$cleanCategory:$cleanHeadline"

        synchronized(lock) {
            val currentList = _issues.value
            val existingIdx = currentList.indexOfFirst {
                it.dedupKey == key && (now - it.atMs) <= DEDUP_WINDOW_MS
            }
            if (existingIdx >= 0) {
                val existing = currentList[existingIdx]
                val updated = existing.copy(
                    count = existing.count + 1,
                    atMs = now,
                    humanReason = cleanReason.ifEmpty { existing.humanReason },
                    detail = cleanDetail.ifEmpty { existing.detail },
                )
                val nextList = currentList.toMutableList().apply {
                    removeAt(existingIdx)
                    add(0, updated)
                }
                _issues.value = nextList
                if (showToast) _activeToast.value = updated
            } else {
                val record = IssueRecord(
                    id = issueCounter.incrementAndGet(),
                    category = cleanCategory,
                    headline = cleanHeadline,
                    humanReason = cleanReason,
                    detail = cleanDetail,
                    count = 1,
                    atMs = now,
                    dedupKey = key,
                )
                _issues.value = (listOf(record) + currentList).take(MAX_HISTORY)
                if (showToast) _activeToast.value = record
            }
        }
    }

    /** 图床单张拉图失败只留日志，不往顶部胶囊刷屏推送（避免 HTTP 403/404 噪音干扰）。 */
    fun reportImageError(url: String, error: Throwable) {
        // 保留入口签名以兼容既有调用方；详细错误已由 DesktopImagePipeline 写入运行日志。
    }

    fun dismissToast() {
        _activeToast.value = null
    }

    fun openPanel() {
        _panelOpen.value = true
    }

    fun closePanel() {
        _panelOpen.value = false
    }

    fun clearHistory() {
        synchronized(lock) {
            _issues.value = emptyList()
            _activeToast.value = null
            _panelOpen.value = false
        }
    }

    /** 桌宠消费后清理最新未捕获事件。 */
    fun clear() {
        _latest.value = null
    }
}

private fun humanizeImageError(error: Throwable, url: String = ""): String {
    val msg = runCatching {
        generateSequence(error) { it.cause }
            .mapNotNull { it.message }
            .joinToString(" | ")
    }.getOrDefault(error.message.orEmpty())
    val lower = msg.lowercase()
    val urlLower = url.lowercase()
    val isPresignedUrl = "x-amz-algorithm=" in urlLower || "x-amz-date=" in urlLower
    return when {
        "403" in lower || "forbidden" in lower -> when {
            isPresignedUrl -> "HTTP 403 图片签名链接已过期（请刷新或重进详情）"
            "jcomic.net" in urlLower || "jmapinode" in urlLower || "18comic" in urlLower ->
                "HTTP 403 站方防盗链或地址已失效"
            else -> "HTTP 403 被站方防盗链或盾拦截"
        }
        "404" in lower || "not found" in lower -> "HTTP 404 图片地址已失效"
        "400" in lower -> "HTTP 400 请求参数异常"
        Regex("""\b5\d\d\b""").containsMatchIn(lower) -> "图床服务器异常（5xx）"
        "timeout" in lower || "timed out" in lower -> "拉取超时"
        "makefromencoded" in lower || "decode" in lower || "unsupported" in lower -> "图片解码失败（可能已加密或损坏）"
        "ssl" in lower || "certificate" in lower || "handshake" in lower -> "HTTPS 证书或 TLS 握手失败"
        "unknownhost" in lower || "connect" in lower -> "无法连接到图床服务器"
        else -> "${desktopFailureType(error)} 加载失败"
    }
}

/** 窗口期异常工厂：接管框架异常并落盘、上报界面报错条、同步给桌宠。 */
@androidx.compose.ui.ExperimentalComposeUiApi
internal object DesktopWindowExceptionHandlerFactory : WindowExceptionHandlerFactory {
    override fun exceptionHandler(window: java.awt.Window): WindowExceptionHandler =
        WindowExceptionHandler { error ->
            val thread = Thread.currentThread().name
            println(desktopCrashReading(thread, error, 0))
            error.printStackTrace()
            val logPath = appendDesktopErrorLog(thread, error)
            DesktopErrorBus.push(thread, error, logPath)
        }
}

/** 整段 stack 追加落盘，避免报错通道本身崩溃。 */
internal fun appendDesktopErrorLog(threadName: String, error: Throwable): Path? = runCatching {
    val dir = Path.of(System.getProperty("user.dir"), "logs")
    Files.createDirectories(dir)
    val file = dir.resolve("desktop-error.log")
    val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
    val stack = java.io.StringWriter().also { error.printStackTrace(java.io.PrintWriter(it)) }.toString()
    val block = buildString {
        append("==== ").append(stamp).append(" 线程=").append(threadName).append(" ====\n")
        append(desktopCrashReading(threadName, error, 0)).append('\n')
        append(stack).append('\n')
    }
    Files.write(
        file,
        block.toByteArray(Charsets.UTF_8),
        StandardOpenOption.CREATE,
        StandardOpenOption.APPEND,
    )
    file
}.getOrNull()

private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

internal fun normalizeIssueCategory(raw: String): String {
    val t = raw.trim()
    return when {
        t.contains("漫画") || t.contains("收藏") || t.contains("导入") || t.contains("详情") || t.contains("搜索") -> "漫画"
        t.contains("图库") || t.contains("图片") || t.contains("画廊") || t.contains("视频") || t.contains("头像") -> "图库"
        else -> "系统"
    }
}

internal fun copyTextToClipboard(text: String) {
    runCatching {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    }
}

/**
 * 桌面端全局报错胶囊通知栏、轻量下拉面板与问题反馈叠层：
 * - 平时无错误彻底隐藏；新报错从顶部平滑滑出摘要，数秒后自动缩成紧凑角标；
 * - 点击展开与当前深浅主题一致的玻璃磨砂下拉面板，支持「全部 / 漫画 / 图库 / 系统」筛选；
 * - 技术栈与日志路径默认折叠，每条卡片支持直接「去反馈」带入 GitHub Issue 表单。
 */
@Composable
internal fun DesktopErrorToastOverlay() {
    val latest by DesktopErrorBus.latest.collectAsState()
    val activeToast by DesktopErrorBus.activeToast.collectAsState()
    val issues by DesktopErrorBus.issues.collectAsState()
    val panelOpen by DesktopErrorBus.panelOpen.collectAsState()
    val toastDurationMs by com.venera.desktop.settings.DesktopErrorToastDuration.durationMs.collectAsState()

    LaunchedEffect(latest) {
        if (latest != null) {
            delay(1_000)
            DesktopErrorBus.clear()
        }
    }

    LaunchedEffect(activeToast?.id, activeToast?.count, toastDurationMs) {
        if (activeToast != null) {
            val waitMs = if (toastDurationMs > 0L) toastDurationMs else 4_000L
            delay(waitMs)
            DesktopErrorBus.dismissToast()
        }
    }

    val tokens = VeneraTokens
    var selectedFilter by remember { mutableStateOf("全部") }

    Box(modifier = Modifier.fillMaxSize()) {
        // 顶部居中报错胶囊（平时彻底隐藏，有错时滑出摘要并自动缩成角标）
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp),
        ) {
            AnimatedVisibility(
                visible = issues.isNotEmpty(),
                enter = fadeIn(tween(200)) +
                    slideInVertically(tween(240, easing = FastOutSlowInEasing)) { -16 } +
                    scaleIn(tween(240, easing = FastOutSlowInEasing), initialScale = 0.92f),
                exit = fadeOut(tween(180)) +
                    slideOutVertically(tween(180)) { -12 } +
                    scaleOut(tween(180), targetScale = 0.92f),
            ) {
                val capsuleInteraction = remember { MutableInteractionSource() }
                val capsuleHovered by capsuleInteraction.collectIsHoveredAsState()
                val pillShape = RoundedCornerShape(999.dp)
                val capsuleBg = if (capsuleHovered) {
                    tokens.color.surfaceVariant.copy(alpha = 0.98f)
                } else {
                    tokens.color.surfaceContainerHigh.copy(alpha = 0.96f)
                }
                val showBanner = activeToast != null && !panelOpen
                val bannerText = activeToast?.headline ?: issues.firstOrNull()?.headline.orEmpty()

                Row(
                    modifier = Modifier
                        .height(28.dp)
                        .widthIn(max = 420.dp)
                        .vShadow(6.dp, pillShape)
                        .background(capsuleBg, pillShape)
                        .border(
                            width = 1.dp,
                            color = if (capsuleHovered || panelOpen) tokens.color.primary.copy(alpha = 0.45f) else GalleryTokens.Color.OutlineHairline,
                            shape = pillShape,
                        )
                        .clip(pillShape)
                        .hoverable(capsuleInteraction)
                        .clickable(interactionSource = capsuleInteraction, indication = null) {
                            DesktopFeedbackBus.closePanel()
                            if (panelOpen) DesktopErrorBus.closePanel() else DesktopErrorBus.openPanel()
                        }
                        .animateContentSize(tween(240, easing = FastOutSlowInEasing))
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(tokens.color.error),
                    )
                    if (showBanner && bannerText.isNotBlank()) {
                        Text(
                            text = bannerText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = tokens.color.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(tokens.color.error.copy(alpha = 0.14f))
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    ) {
                        Text(
                            text = "${issues.size}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = tokens.color.error,
                        )
                    }
                    Icon(
                        imageVector = Icons.Outlined.KeyboardArrowDown,
                        contentDescription = null,
                        tint = tokens.color.textTertiary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }

        // 点击外部空白处收起下拉面板（无全屏暗色遮罩）
        if (panelOpen) {
            val outsideInteraction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(interactionSource = outsideInteraction, indication = null) {
                        DesktopErrorBus.closePanel()
                    },
            )
        }

        // 报错轻量下拉面板
        AnimatedVisibility(
            visible = panelOpen,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 46.dp, start = 16.dp, end = 16.dp),
            enter = fadeIn(tween(200)) +
                slideInVertically(tween(220, easing = FastOutSlowInEasing)) { -12 } +
                scaleIn(tween(220, easing = FastOutSlowInEasing), initialScale = 0.96f, transformOrigin = TransformOrigin(0.5f, 0f)),
            exit = fadeOut(tween(150)) +
                slideOutVertically(tween(160)) { -8 } +
                scaleOut(tween(160), targetScale = 0.96f, transformOrigin = TransformOrigin(0.5f, 0f)),
        ) {
            val modalShape = RoundedCornerShape(16.dp)
            val modalInteraction = remember { MutableInteractionSource() }
            val panelBg = tokens.color.surfaceContainerHigh.copy(alpha = 0.97f)
            val filteredIssues = remember(issues, selectedFilter) {
                if (selectedFilter == "全部") issues else issues.filter { normalizeIssueCategory(it.category) == selectedFilter }
            }

            Column(
                modifier = Modifier
                    .widthIn(min = 380.dp, max = 540.dp)
                    .heightIn(max = 520.dp)
                    .vShadow(12.dp, modalShape)
                    .background(panelBg, modalShape)
                    .border(1.dp, GalleryTokens.Color.OutlineHairline, modalShape)
                    .clip(modalShape)
                    .clickable(interactionSource = modalInteraction, indication = null) {}
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = DesktopI18n.t("异常与操作提醒（${issues.size}）", "Errors & Alerts (${issues.size})"),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = tokens.color.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    val copiedAllMsg = DesktopI18n.t("已复制全部诊断信息", "Copied all diagnostics")
                    ToastMiniAction(
                        text = DesktopI18n.t("全部复制", "Copy All"),
                        color = tokens.color.primary,
                        onClick = {
                            val text = issues.joinToString("\n\n") { rec ->
                                val time = TIME_FMT.format(Instant.ofEpochMilli(rec.atMs))
                                val cnt = if (rec.count > 1) " (×${rec.count})" else ""
                                "[$time][${rec.category}]$cnt ${rec.headline}" +
                                    (if (rec.humanReason.isNotBlank()) "\n${rec.humanReason}" else "") +
                                    if (rec.detail.isNotBlank()) "\n${rec.detail}" else ""
                            }
                            copyTextToClipboard(text)
                            DesktopFeedbackBus.showToast(copiedAllMsg)
                        },
                    )
                    ToastMiniAction(
                        text = DesktopI18n.t("清空", "Clear"),
                        color = tokens.color.error,
                        onClick = { DesktopErrorBus.clearHistory() },
                    )
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = DesktopI18n.t("关闭", "Close"),
                        tint = tokens.color.textSecondary,
                        modifier = Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .clickable { DesktopErrorBus.closePanel() }
                            .padding(2.dp),
                    )
                }

                // 分类筛选胶囊
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val cats = listOf("全部", "漫画", "图库", "系统")
                    cats.forEach { cat ->
                        val count = if (cat == "全部") issues.size else issues.count { normalizeIssueCategory(it.category) == cat }
                        val selected = selectedFilter == cat
                        val chipShape = RoundedCornerShape(999.dp)
                        val chipBg = if (selected) {
                            tokens.color.primary.copy(alpha = 0.16f)
                        } else {
                            DesktopGlassCardDefaults.backgroundColor(level = DesktopGlassCardLevel.Nested)
                        }
                        val chipBorder = if (selected) {
                            tokens.color.primary.copy(alpha = 0.42f)
                        } else {
                            GalleryTokens.Color.OutlineHairline
                        }
                        Box(
                            modifier = Modifier
                                .clip(chipShape)
                                .background(chipBg, chipShape)
                                .border(1.dp, chipBorder, chipShape)
                                .clickable { selectedFilter = cat }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = "$cat ($count)",
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (selected) tokens.color.primary else tokens.color.textSecondary,
                            )
                        }
                    }
                }

                if (filteredIssues.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(110.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = DesktopI18n.t("当前分类暂无错误记录", "No errors in this category"),
                            fontSize = VeneraType.body.size,
                            color = tokens.color.textTertiary,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                        contentPadding = PaddingValues(vertical = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(filteredIssues, key = { it.id }) { rec ->
                            ErrorRecordCard(rec = rec)
                        }
                    }
                }
            }
        }

        // 挂载右上角问题反馈浮层与轻提示
        DesktopFeedbackPanelOverlay()
    }
}

@Composable
private fun ErrorRecordCard(rec: DesktopErrorBus.IssueRecord) {
    val tokens = VeneraTokens
    val itemShape = RoundedCornerShape(10.dp)
    val cardInteraction = remember { MutableInteractionSource() }
    val cardHovered by cardInteraction.collectIsHoveredAsState()
    var techExpanded by remember(rec.id) { mutableStateOf(false) }

    val cardBg = if (cardHovered) {
        tokens.color.surfaceVariant.copy(alpha = 0.96f)
    } else {
        tokens.color.surface.copy(alpha = 0.95f)
    }
    val cat = normalizeIssueCategory(rec.category)
    val catColor = when (cat) {
        "漫画" -> tokens.color.error
        "图库" -> Color(0xFFF5A623)
        else -> Color(0xFF7C8CFF)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(cardBg, itemShape)
            .border(1.dp, GalleryTokens.Color.OutlineHairline, itemShape)
            .clip(itemShape)
            .hoverable(cardInteraction)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(catColor.copy(alpha = 0.14f))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            ) {
                Text(
                    text = cat,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = catColor,
                )
            }
            Text(
                text = TIME_FMT.format(Instant.ofEpochMilli(rec.atMs)),
                fontSize = 11.sp,
                color = tokens.color.textTertiary,
            )
            if (rec.count > 1) {
                Text(
                    text = "×${rec.count}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.color.textSecondary,
                )
            }
            Spacer(Modifier.weight(1f))
            if (rec.detail.isNotBlank()) {
                CardTinyButton(
                    text = if (techExpanded) DesktopI18n.t("收起", "Hide") else DesktopI18n.t("详情", "Details"),
                    onClick = { techExpanded = !techExpanded },
                )
            }
            val copiedOneMsg = DesktopI18n.t("已复制该条诊断信息", "Copied diagnostic info")
            CardTinyButton(
                text = DesktopI18n.t("复制", "Copy"),
                onClick = {
                    val body = "[${rec.category}] ${rec.headline}" +
                        (if (rec.humanReason.isNotBlank()) "\n${rec.humanReason}" else "") +
                        (if (rec.detail.isNotBlank()) "\n${rec.detail}" else "")
                    copyTextToClipboard(body)
                    DesktopFeedbackBus.showToast(copiedOneMsg)
                },
            )
            CardTinyButton(
                text = DesktopI18n.t("去反馈", "Report"),
                accent = true,
                onClick = { DesktopFeedbackBus.openForIssue(rec) },
            )
        }

        Text(
            text = rec.headline,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = tokens.color.textPrimary,
        )
        if (rec.humanReason.isNotBlank()) {
            Text(
                text = rec.humanReason,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = tokens.color.textSecondary,
            )
        }
        AnimatedVisibility(
            visible = techExpanded && rec.detail.isNotBlank(),
            enter = expandVertically(tween(210, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
            exit = shrinkVertically(tween(180)) + fadeOut(tween(140)),
        ) {
            val techShape = RoundedCornerShape(7.dp)
            Text(
                text = rec.detail,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = tokens.color.textTertiary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .background(tokens.color.surface.copy(alpha = 0.65f), techShape)
                    .border(1.dp, GalleryTokens.Color.OutlineHairline, techShape)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun CardTinyButton(
    text: String,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    com.venera.desktop.ui.DesktopButton(
        text = text,
        onClick = onClick,
        size = com.venera.desktop.ui.DesktopButtonSize.Compact,
        variant = if (accent) com.venera.desktop.ui.DesktopButtonVariant.Primary else com.venera.desktop.ui.DesktopButtonVariant.Secondary,
        horizontalPadding = 10.dp,
    )
}

@Composable
private fun ToastMiniAction(
    text: String,
    color: Color,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
