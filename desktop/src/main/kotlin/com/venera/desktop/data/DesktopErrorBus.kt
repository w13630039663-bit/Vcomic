package com.venera.desktop.data

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

internal fun normalizeIssueCategory(raw: String): String {
    val t = raw.trim()
    return when {
        t.contains("漫画") || t.contains("收藏") || t.contains("导入") || t.contains("详情") || t.contains("搜索") -> "漫画"
        t.contains("图库") || t.contains("图片") || t.contains("画廊") || t.contains("视频") || t.contains("头像") -> "图库"
        else -> "系统"
    }
}

