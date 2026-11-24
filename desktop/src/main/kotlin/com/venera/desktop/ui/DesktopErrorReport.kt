package com.venera.desktop.ui

import com.venera.desktop.data.desktopCrashReading
import com.venera.desktop.data.desktopFailureType
import com.venera.desktop.data.desktopOneLineMessage
import com.venera.desktop.data.DesktopErrorBus
import com.venera.desktop.data.appendDesktopErrorLog
import com.venera.desktop.data.desktopScrubSecrets
import com.venera.desktop.data.normalizeIssueCategory
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

private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

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
