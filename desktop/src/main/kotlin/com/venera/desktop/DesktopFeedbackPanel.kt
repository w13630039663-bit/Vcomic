package com.venera.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.gallery.ui.GalleryTokens
import com.venera.desktop.settings.DesktopI18n
import com.venera.desktop.ui.DesktopSegmented
import com.venera.desktop.ui.components.DesktopGlassCardDefaults
import com.venera.desktop.ui.components.DesktopGlassCardLevel
import com.venera.desktop.ui.vShadow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Image
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.image.BufferedImage
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicLong
import javax.imageio.ImageIO

/** 目标 GitHub Issue 仓库（`owner/repo` 格式，可按需修改为上游或专用反馈仓）。 */
const val GITHUB_ISSUE_REPO = "w13630039663-bit/venera-miuix"

enum class DesktopFeedbackTab {
    BUG,
    FEATURE,
}

data class FeedbackScreenshotItem(
    val id: Long,
    val label: String,
    val awtImage: BufferedImage,
    val composeBitmap: ImageBitmap,
)

/**
 * 全局问题反馈与新功能建议状态总线。
 *
 * 负责维护顶栏反馈入口与报错下拉面板「去反馈」的双向联动，以及剪贴板带图跳转 GitHub Issue。
 */
object DesktopFeedbackBus {
    private const val MAX_SCREENSHOTS = 3
    private val shotIdGen = AtomicLong()

    private val _panelOpen = MutableStateFlow(false)
    val panelOpen: StateFlow<Boolean> = _panelOpen.asStateFlow()

    private val _activeTab = MutableStateFlow(DesktopFeedbackTab.BUG)
    val activeTab: StateFlow<DesktopFeedbackTab> = _activeTab.asStateFlow()

    private val _selectedIssueId = MutableStateFlow<Long?>(null)
    val selectedIssueId: StateFlow<Long?> = _selectedIssueId.asStateFlow()

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _description = MutableStateFlow("")
    val description: StateFlow<String> = _description.asStateFlow()

    private val _screenshots = MutableStateFlow<List<FeedbackScreenshotItem>>(emptyList())
    val screenshots: StateFlow<List<FeedbackScreenshotItem>> = _screenshots.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    fun togglePanel() {
        if (_panelOpen.value) {
            closePanel()
        } else {
            DesktopErrorBus.closePanel()
            if (_activeTab.value == DesktopFeedbackTab.BUG && _selectedIssueId.value == null) {
                val first = DesktopErrorBus.issues.value.firstOrNull()
                if (first != null) {
                    _selectedIssueId.value = first.id
                    if (_title.value.isBlank()) {
                        _title.value = first.headline
                    }
                }
            }
            _panelOpen.value = true
        }
    }

    fun openForIssue(issue: DesktopErrorBus.IssueRecord) {
        DesktopErrorBus.closePanel()
        _activeTab.value = DesktopFeedbackTab.BUG
        _selectedIssueId.value = issue.id
        _title.value = issue.headline
        _panelOpen.value = true
    }

    fun closePanel() {
        _panelOpen.value = false
    }

    fun switchTab(tab: DesktopFeedbackTab) {
        _activeTab.value = tab
    }

    fun selectIssue(issue: DesktopErrorBus.IssueRecord?) {
        _selectedIssueId.value = issue?.id
        if (issue != null && _title.value.isBlank()) {
            _title.value = issue.headline
        }
    }

    fun updateTitle(value: String) {
        _title.value = value
    }

    fun updateDescription(value: String) {
        _description.value = value
    }

    fun showToast(msg: String) {
        _toastMessage.value = msg
    }

    fun dismissToast() {
        _toastMessage.value = null
    }

    /** 从系统剪贴板读取图片并加入附件列表。 */
    fun pasteScreenshotFromClipboard(): Boolean {
        val clipboardImage = readImageFromClipboard() ?: return false
        return addScreenshot(clipboardImage, "剪贴板截图")
    }

    /** 通过系统文件框选择本地图片并加入附件列表。 */
    fun pickLocalScreenshot(dialogTitle: String = "选择截图") {
        val dialog = FileDialog(null as Frame?, dialogTitle, FileDialog.LOAD).apply {
            setFilenameFilter { _, name ->
                val lower = name.lowercase()
                lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp") || lower.endsWith(".bmp")
            }
            isVisible = true
        }
        val dir = dialog.directory ?: return
        val fileName = dialog.file ?: return
        val file = File(dir, fileName)
        val img = runCatching { ImageIO.read(file) }.getOrNull()
        if (img == null) {
            showToast("无法读取该图片文件")
            return
        }
        addScreenshot(img, file.name)
    }

    fun addScreenshot(image: BufferedImage, label: String): Boolean {
        val current = _screenshots.value
        if (current.size >= MAX_SCREENSHOTS) {
            showToast("最多附带 $MAX_SCREENSHOTS 张截图")
            return false
        }
        val bitmap = runCatching { image.toComposeImageBitmap() }.getOrNull() ?: return false
        val item = FeedbackScreenshotItem(
            id = shotIdGen.incrementAndGet(),
            label = label,
            awtImage = image,
            composeBitmap = bitmap,
        )
        _screenshots.value = current + item
        return true
    }

    fun removeScreenshot(id: Long) {
        _screenshots.value = _screenshots.value.filterNot { it.id == id }
    }

    /** 组装 GitHub Issue 预填地址并打开浏览器；若有截图则自动写入系统剪贴板方便网页端直接粘贴。 */
    fun submitToGithub(issues: List<DesktopErrorBus.IssueRecord>) {
        val tab = _activeTab.value
        val issueTitle = _title.value.trim().ifEmpty {
            if (tab == DesktopFeedbackTab.BUG) "问题反馈" else "新功能建议"
        }
        val userDesc = _description.value.trim()
        val linked = if (tab == DesktopFeedbackTab.BUG) {
            issues.firstOrNull { it.id == _selectedIssueId.value }
        } else {
            null
        }
        val shots = _screenshots.value
        val hasShot = shots.isNotEmpty()
        if (hasShot) {
            copyImageToClipboard(shots.first().awtImage)
        }

        val osInfo = "${System.getProperty("os.name")} ${System.getProperty("os.version")} (${System.getProperty("os.arch")})"
        val javaVer = System.getProperty("java.version") ?: "unknown"
        val body = buildString {
            if (tab == DesktopFeedbackTab.BUG) {
                append("### 问题描述\n")
                append(userDesc.ifEmpty { "（请补充复现步骤或现象）" })
                append("\n\n")
                if (linked != null) {
                    append("### 关联报错\n")
                    append("- **分类**: ").append(linked.category).append('\n')
                    append("- **摘要**: ").append(linked.headline).append('\n')
                    if (linked.humanReason.isNotBlank()) {
                        append("- **原因**: ").append(linked.humanReason).append('\n')
                    }
                    if (linked.detail.isNotBlank()) {
                        append("\n```text\n")
                        append(desktopScrubSecrets(linked.detail).take(1200))
                        append("\n```\n")
                    }
                }
            } else {
                append("### 功能介绍\n")
                append(userDesc.ifEmpty { "（请描述希望新增的功能与使用场景）" })
                append("\n\n")
            }
            append("### 运行环境\n")
            append("- 系统: ").append(osInfo).append('\n')
            append("- Java: ").append(javaVer).append('\n')
            if (hasShot) {
                append("\n> 已复制截图到剪贴板，请在此处按 `Ctrl+V` 粘贴截图（共选 ${shots.size} 张）。\n")
            }
        }

        val labels = if (tab == DesktopFeedbackTab.BUG) "bug" else "enhancement"
        val url = "https://github.com/$GITHUB_ISSUE_REPO/issues/new" +
            "?title=${urlEncode(issueTitle)}" +
            "&labels=${urlEncode(labels)}" +
            "&body=${urlEncode(body)}"

        val opened = runCatching {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI(url))
                true
            } else {
                false
            }
        }.getOrDefault(false)

        if (opened) {
            showToast(
                if (hasShot) {
                    "截图已复制，请在 GitHub 网页中 Ctrl+V 粘贴"
                } else {
                    "已打开 GitHub 问题提交页面"
                },
            )
            closePanel()
        } else {
            copyTextToClipboard(url)
            showToast("无法唤起浏览器，已复制预填链接到剪贴板")
        }
    }

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8)

    private fun readImageFromClipboard(): BufferedImage? {
        return runCatching {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            if (!clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor)) return null
            val awtImg = clipboard.getData(DataFlavor.imageFlavor) as? Image ?: return null
            if (awtImg is BufferedImage) return awtImg
            val w = awtImg.getWidth(null).coerceAtLeast(1)
            val h = awtImg.getHeight(null).coerceAtLeast(1)
            BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB).also { buf ->
                val g = buf.createGraphics()
                g.drawImage(awtImg, 0, 0, null)
                g.dispose()
            }
        }.getOrNull()
    }

    private fun copyImageToClipboard(image: BufferedImage) {
        runCatching {
            val transferable = object : Transferable {
                override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.imageFlavor)
                override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == DataFlavor.imageFlavor
                override fun getTransferData(flavor: DataFlavor): Any {
                    if (flavor == DataFlavor.imageFlavor) return image
                    throw UnsupportedFlavorException(flavor)
                }
            }
            Toolkit.getDefaultToolkit().systemClipboard.setContents(transferable, null)
        }
    }
}

/**
 * 顶栏最小化按钮左侧常驻的「反馈」入口按钮。
 *
 * @param tint 图标与文字基础色，随所在顶栏色板传入。
 * @param hoverBackground 悬停背景色。
 */
@Composable
internal fun DesktopFeedbackTriggerButton(
    tint: Color = VeneraTokens.color.textSecondary,
    hoverBackground: Color = VeneraTokens.color.surfaceVariant.copy(alpha = 0.6f),
) {
    val panelOpen by DesktopFeedbackBus.panelOpen.collectAsState()
    val tokens = VeneraTokens
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(8.dp)

    Row(
        modifier = Modifier
            .padding(end = 6.dp)
            .height(28.dp)
            .clip(shape)
            .background(
                when {
                    panelOpen -> tokens.color.primary.copy(alpha = 0.14f)
                    hovered -> hoverBackground
                    else -> Color.Transparent
                },
            )
            .border(
                width = 1.dp,
                color = if (panelOpen || hovered) tokens.color.outlineVariant.copy(alpha = 0.28f) else Color.Transparent,
                shape = shape,
            )
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null) {
                DesktopFeedbackBus.togglePanel()
            }
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            text = "💬",
            fontSize = 11.sp,
            color = if (panelOpen) tokens.color.primary else tint,
        )
        Text(
            text = DesktopI18n.t("反馈", "Feedback"),
            fontSize = 12.sp,
            fontWeight = if (panelOpen) FontWeight.SemiBold else FontWeight.Medium,
            color = if (panelOpen) tokens.color.primary else tint,
            maxLines = 1,
        )
    }
}

/**
 * 右上角问题反馈与新功能建议浮层面板，以及底部操作轻提示。
 */
@Composable
internal fun DesktopFeedbackPanelOverlay() {
    val panelOpen by DesktopFeedbackBus.panelOpen.collectAsState()
    val activeTab by DesktopFeedbackBus.activeTab.collectAsState()
    val selectedIssueId by DesktopFeedbackBus.selectedIssueId.collectAsState()
    val title by DesktopFeedbackBus.title.collectAsState()
    val description by DesktopFeedbackBus.description.collectAsState()
    val screenshots by DesktopFeedbackBus.screenshots.collectAsState()
    val toastMessage by DesktopFeedbackBus.toastMessage.collectAsState()
    val issues by DesktopErrorBus.issues.collectAsState()

    val tokens = VeneraTokens

    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            delay(2_600)
            DesktopFeedbackBus.dismissToast()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 点击空白处关闭反馈面板（无暗色全屏遮罩）
        if (panelOpen) {
            val outsideInteraction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(interactionSource = outsideInteraction, indication = null) {
                        DesktopFeedbackBus.closePanel()
                    },
            )
        }

        AnimatedVisibility(
            visible = panelOpen,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 50.dp, end = 124.dp),
            enter = fadeIn(tween(200)) +
                slideInVertically(tween(220, easing = FastOutSlowInEasing)) { -12 } +
                scaleIn(tween(220, easing = FastOutSlowInEasing), initialScale = 0.95f, transformOrigin = TransformOrigin(1f, 0f)),
            exit = fadeOut(tween(150)) +
                slideOutVertically(tween(160)) { -8 } +
                scaleOut(tween(160), targetScale = 0.96f, transformOrigin = TransformOrigin(1f, 0f)),
        ) {
            val panelShape = RoundedCornerShape(16.dp)
            val panelInteraction = remember { MutableInteractionSource() }
            val surfaceColor = tokens.color.surfaceContainerHigh.copy(alpha = 0.97f)
            val nestedColor = tokens.color.surface.copy(alpha = 0.95f)

            val pastedMsg = DesktopI18n.t("已粘贴剪贴板截图", "Pasted screenshot")
            Column(
                modifier = Modifier
                    .widthIn(min = 340.dp, max = 440.dp)
                    .heightIn(max = 590.dp)
                    .vShadow(12.dp, panelShape)
                    .background(surfaceColor, panelShape)
                    .border(1.dp, GalleryTokens.Color.OutlineHairline, panelShape)
                    .clip(panelShape)
                    .clickable(interactionSource = panelInteraction, indication = null) {}
                    .onPreviewKeyEvent { evt ->
                        if (evt.type == KeyEventType.KeyDown && evt.isCtrlPressed && evt.key == Key.V) {
                            if (DesktopFeedbackBus.pasteScreenshotFromClipboard()) {
                                DesktopFeedbackBus.showToast(pastedMsg)
                                return@onPreviewKeyEvent true
                            }
                        }
                        false
                    }
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 标题栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = DesktopI18n.t("反馈与建议", "Feedback & Suggestions"),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = tokens.color.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = DesktopI18n.t("关闭", "Close"),
                        tint = tokens.color.textSecondary,
                        modifier = Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .clickable { DesktopFeedbackBus.closePanel() }
                            .padding(2.dp),
                    )
                }

                // 分段 Tab + 滑动指示器
                FeedbackSegmentedTabs(
                    activeTab = activeTab,
                    nestedColor = nestedColor,
                    onSelect = { DesktopFeedbackBus.switchTab(it) },
                )

                // 关联报错下拉框（仅问题反馈显示，平滑折叠）
                AnimatedVisibility(
                    visible = activeTab == DesktopFeedbackTab.BUG,
                    enter = expandVertically(tween(220, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
                    exit = shrinkVertically(tween(180)) + fadeOut(tween(140)),
                ) {
                    IssueLinkDropdown(
                        issues = issues,
                        selectedIssueId = selectedIssueId,
                        onSelect = { DesktopFeedbackBus.selectIssue(it) },
                    )
                }

                // 标题输入
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        text = if (activeTab == DesktopFeedbackTab.BUG) {
                            DesktopI18n.t("问题标题", "Issue Title")
                        } else {
                            DesktopI18n.t("建议标题", "Feature Title")
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = tokens.color.textSecondary,
                    )
                    FeedbackInputBox(
                        value = title,
                        onValueChange = { DesktopFeedbackBus.updateTitle(it) },
                        placeholder = if (activeTab == DesktopFeedbackTab.BUG) {
                            DesktopI18n.t("一句话概括遇到的问题…", "Summarize the issue...")
                        } else {
                            DesktopI18n.t("希望新增什么功能…", "Describe the feature...")
                        },
                        singleLine = true,
                    )
                }

                // 描述输入
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        text = if (activeTab == DesktopFeedbackTab.BUG) {
                            DesktopI18n.t("补充说明", "Details")
                        } else {
                            DesktopI18n.t("功能介绍", "Description")
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = tokens.color.textSecondary,
                    )
                    FeedbackInputBox(
                        value = description,
                        onValueChange = { DesktopFeedbackBus.updateDescription(it) },
                        placeholder = DesktopI18n.t("复现步骤、期望结果或具体建议…", "Steps to reproduce or suggestion details..."),
                        singleLine = false,
                        minHeight = 76.dp,
                    )
                }

                // 截图附件区
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = DesktopI18n.t("截图附件（可选）", "Screenshots (Optional)"),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = tokens.color.textSecondary,
                        )
                        Text(
                            text = DesktopI18n.t("支持 Ctrl+V 粘贴", "Ctrl+V supported"),
                            fontSize = 11.sp,
                            color = tokens.color.textTertiary,
                        )
                    }
                    val dropShape = RoundedCornerShape(10.dp)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(nestedColor, dropShape)
                            .border(1.dp, GalleryTokens.Color.OutlineHairline, dropShape)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (screenshots.isNotEmpty()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                screenshots.forEach { shot ->
                                    Box(modifier = Modifier.size(width = 74.dp, height = 52.dp)) {
                                        Image(
                                            bitmap = shot.composeBitmap,
                                            contentDescription = shot.label,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(7.dp))
                                                .border(1.dp, GalleryTokens.Color.OutlineHairline, RoundedCornerShape(7.dp)),
                                        )
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .offset(x = 4.dp, y = (-4).dp)
                                                .size(16.dp)
                                                .clip(CircleShape)
                                                .background(tokens.color.error)
                                                .clickable { DesktopFeedbackBus.removeScreenshot(shot.id) },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text("×", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = DesktopI18n.t("提交时自动复制首张截图", "Auto-copies 1st shot on submit"),
                                fontSize = 11.sp,
                                color = tokens.color.textTertiary,
                            )
                            val noClipboardImgMsg = DesktopI18n.t("剪贴板里没有图片", "No image in clipboard")
                            val pickTitle = DesktopI18n.t("选择截图", "Select Screenshot")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = DesktopI18n.t("粘贴", "Paste"),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = tokens.color.textSecondary,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(5.dp))
                                        .clickable {
                                            if (!DesktopFeedbackBus.pasteScreenshotFromClipboard()) {
                                                DesktopFeedbackBus.showToast(noClipboardImgMsg)
                                            }
                                        }
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                                Text(
                                    text = DesktopI18n.t("+ 选择本地图片", "+ Local Image"),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = tokens.color.primary,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(5.dp))
                                        .clickable { DesktopFeedbackBus.pickLocalScreenshot(pickTitle) }
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }

                // 底部提交行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = DesktopI18n.t("将打开 GitHub 预填页面，Ctrl+V 贴图即可", "Opens GitHub Issue page"),
                        fontSize = 11.sp,
                        color = tokens.color.textTertiary,
                        modifier = Modifier.weight(1f),
                    )
                    val btnShape = RoundedCornerShape(9.dp)
                    Box(
                        modifier = Modifier
                            .height(34.dp)
                            .clip(btnShape)
                            .background(tokens.color.primary)
                            .clickable { DesktopFeedbackBus.submitToGithub(issues) }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = DesktopI18n.t("前往 GitHub 提交", "Open GitHub Issue"),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                    }
                }
            }
        }

        // 底部操作轻提示
        AnimatedVisibility(
            visible = toastMessage != null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp),
            enter = fadeIn(tween(200)) + slideInVertically(tween(220, easing = FastOutSlowInEasing)) { 18 },
            exit = fadeOut(tween(180)) + slideOutVertically(tween(180)) { 14 },
        ) {
            val pillShape = RoundedCornerShape(999.dp)
            Box(
                modifier = Modifier
                    .vShadow(8.dp, pillShape)
                    .background(tokens.color.surfaceContainerHigh.copy(alpha = 0.94f), pillShape)
                    .border(1.dp, tokens.color.primary.copy(alpha = 0.42f), pillShape)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = toastMessage.orEmpty(),
                    fontSize = 12.sp,
                    color = tokens.color.textPrimary,
                )
            }
        }
    }
}

@Composable
private fun FeedbackSegmentedTabs(
    activeTab: DesktopFeedbackTab,
    nestedColor: Color,
    onSelect: (DesktopFeedbackTab) -> Unit,
) {
    DesktopSegmented(
        options = listOf(
            DesktopI18n.t("问题反馈", "Bug Report"),
            DesktopI18n.t("新功能建议", "Feature Request"),
        ),
        selectedIndex = if (activeTab == DesktopFeedbackTab.BUG) 0 else 1,
        onSelect = { index ->
            onSelect(if (index == 0) DesktopFeedbackTab.BUG else DesktopFeedbackTab.FEATURE)
        },
        modifier = Modifier.fillMaxWidth(),
        fillWidth = true,
    )
}

@Composable
private fun IssueLinkDropdown(
    issues: List<DesktopErrorBus.IssueRecord>,
    selectedIssueId: Long?,
    onSelect: (DesktopErrorBus.IssueRecord?) -> Unit,
) {
    val tokens = VeneraTokens
    var expanded by remember { mutableStateOf(false) }
    val selected = issues.firstOrNull { it.id == selectedIssueId }
    val boxShape = RoundedCornerShape(9.dp)

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = DesktopI18n.t("附带最近报错", "Attach Recent Error"),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = tokens.color.textSecondary,
            )
            Text(
                text = DesktopI18n.t("自动带入环境与详情", "Auto-fills details"),
                fontSize = 11.sp,
                color = tokens.color.textTertiary,
            )
        }
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .background(tokens.color.surface.copy(alpha = 0.96f), boxShape)
                    .border(1.dp, GalleryTokens.Color.OutlineHairline, boxShape)
                    .clip(boxShape)
                    .clickable { expanded = true }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = selected?.headline ?: DesktopI18n.t("不附带报错记录", "None"),
                    fontSize = 12.sp,
                    color = if (selected != null) tokens.color.textPrimary else tokens.color.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowDown,
                    contentDescription = null,
                    tint = tokens.color.textTertiary,
                    modifier = Modifier.size(16.dp),
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(DesktopI18n.t("不附带报错记录", "None"), fontSize = 12.sp) },
                    onClick = {
                        onSelect(null)
                        expanded = false
                    },
                )
                issues.take(10).forEach { rec ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "[${rec.category}] ${rec.headline}",
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        onClick = {
                            onSelect(rec)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedbackInputBox(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean,
    minHeight: androidx.compose.ui.unit.Dp = 34.dp,
) {
    com.venera.desktop.ui.components.DesktopInputField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight),
        placeholder = placeholder,
        singleLine = singleLine,
    )
}
