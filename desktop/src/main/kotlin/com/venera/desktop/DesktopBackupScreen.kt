package com.venera.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.compose.components.venera.VeneraCheckbox
import com.venera.compose.components.venera.VeneraSwitch
import com.venera.compose.ui.tokens.LocalVeneraDarkTheme
import com.venera.compose.ui.tokens.VeneraMotion
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.gallery.ui.GalleryTokens
import com.venera.desktop.platform.DesktopWebDavSync
import com.venera.desktop.ui.DesktopPageShell
import com.venera.desktop.ui.DesktopPageWidth
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 右栏导入工作区三态。 */
enum class DesktopBackupImportStage {
    IDLE,
    PREVIEW,
    DONE,
}

/**
 * 桌面「备份与还原」工作台屏（纯展示，零 IO、零引擎、零 AWT、零 DatabasePorts）。
 *
 * @param mode 窗口布局档位。
 * @param assetCounts 本机各模块实时条数。
 * @param note 顶部操作反馈提示文本。
 * @param includeSourceAccounts 导出是否包含漫画源账号与设置。
 * @param importStage 右栏当前所处阶段（待选 / 预览 / 完成）。
 * @param preview 已解析的待导入归档预览对象。
 * @param selectedModules 预览态勾选的导入模块集合。
 * @param importStrategy 导入冲突策略（智能合并 / 清空覆盖）。
 * @param lastRestore 最近一次全量导入结果摘要。
 * @param stagingFileName 最近下载到云端中转区的文件名（可选）。
 * @param davConfig 当前 WebDAV 配置。
 * @param davState WebDAV 同步状态。
 * @param davLastSync 最近一次成功同步的归档信息（壳层从偏好读回）。
 * @param armed 覆盖模式二次确认武装标志。
 * @param busy 是否正在执行本地导出或导入任务。
 * @param canFileDialog 是否支持系统文件框（headless 下为 false）。
 * @param importDragOver 是否有文件正拖到待选区上方（壳层 AWT DropTarget 提供）。
 * @param onToggleIncludeAccounts 切换导出是否包含源账号。
 * @param onExport 点击打包导出。
 * @param onOpenDataDir 打开本机数据目录。
 * @param onPickImportFile 点击选取本地备份文件。
 * @param onInspectStagingFile 点击解析已暂存的云端文件。
 * @param onClearPreview 重选文件并回到待选态。
 * @param onSelectStrategy 切换智能合并或清空覆盖。
 * @param onToggleModule 勾选或取消单个导入模块。
 * @param onSelectAllModules 全选或全不选可用模块。
 * @param onArmRestore 覆盖模式第一次点击武装，不直接落写入。
 * @param onConfirmRestore 确认执行导入（智能合并或已武装后的覆盖）。
 * @param onResetImportDone 导入完成后重置回待选态。
 * @param onSaveDavConfig 保存 WebDAV 配置。
 * @param onTestDavConnection 测试 WebDAV 连接。
 * @param onDavUpload 上传通用 `.venera` 归档至云端。
 * @param onDavDownloadAndPreview 从云端拉取最新归档并解析预览。
 * @param onDismissNote 关闭顶部提示条。
 * @param onBack 返回上一页。
 */
@Composable
internal fun DesktopBackupScreen(
    mode: WideScreenLayoutMode,
    assetCounts: DesktopLocalAssetCounts,
    note: String?,
    includeSourceAccounts: Boolean,
    importStage: DesktopBackupImportStage,
    preview: DesktopArchivePreview?,
    selectedModules: Set<DesktopBackupModule>,
    importStrategy: DesktopImportStrategy,
    lastRestore: DesktopVeneraRestoreOutcome?,
    stagingFileName: String?,
    davConfig: DesktopWebDavSync.Config?,
    davState: DesktopWebDavSync.State,
    davLastSync: DesktopWebDavSync.LastSync?,
    armed: Boolean,
    busy: Boolean,
    canFileDialog: Boolean,
    importDragOver: Boolean,
    onToggleIncludeAccounts: (Boolean) -> Unit,
    onExport: () -> Unit,
    onOpenDataDir: () -> Unit,
    onPickImportFile: () -> Unit,
    onInspectStagingFile: () -> Unit,
    onClearPreview: () -> Unit,
    onSelectStrategy: (DesktopImportStrategy) -> Unit,
    onToggleModule: (DesktopBackupModule) -> Unit,
    onSelectAllModules: (Boolean) -> Unit,
    onArmRestore: () -> Unit,
    onConfirmRestore: () -> Unit,
    onResetImportDone: () -> Unit,
    onSaveDavConfig: (DesktopWebDavSync.Config) -> Unit,
    onTestDavConnection: (DesktopWebDavSync.Config) -> Unit,
    onDavUpload: () -> Unit,
    onDavDownloadAndPreview: () -> Unit,
    onDismissNote: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    val scroll = rememberScrollState()
    // WebDAV 卡始终占位：配置在壳层异步读回，首帧不能用 null 把整张卡藏掉。
    val dav = davConfig ?: DesktopWebDavSync.Config("", "", "", "/Venera/")

    DesktopPageShell(
        mode = mode,
        title = "备份与还原",
        subtitle = "跨端数据迁移、本机备份与云端同步",
        width = DesktopPageWidth.Browse,
        titleCard = false,
        onBack = onBack,
        actions = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(tokens.color.surfaceContainerHigh.copy(alpha = 0.45f))
                        .border(1.dp, tokens.color.outlineVariant.copy(alpha = 0.25f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(backupSafe()),
                    )
                    Text(
                        text = com.venera.desktop.settings.DesktopI18n.t("本机数据库就绪", "Local DB Ready"),
                        fontSize = 11.5.sp,
                        color = tokens.color.textSecondary,
                    )
                }
                BackupGhostButton(
                    label = com.venera.desktop.settings.DesktopI18n.t("打开数据目录", "Open Data Folder"),
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                        )
                    },
                    onClick = onOpenDataDir,
                )
            }
        },
        modifier = modifier,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val wideViewport = maxWidth >= 1016.dp
            val extraHorizontal = if (wideViewport) 16.dp else 4.dp
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = extraHorizontal)
                    .verticalScroll(scroll)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
            if (!note.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(tokens.color.primary.copy(alpha = 0.12f))
                        .border(1.dp, tokens.color.primary.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = note,
                        fontSize = 12.5.sp,
                        color = tokens.color.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = com.venera.desktop.settings.DesktopI18n.t("关闭", "Dismiss"),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = tokens.color.primary,
                        modifier = Modifier.clickable(onClick = onDismissNote),
                    )
                }
            }

            if (!canFileDialog) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(tokens.color.error.copy(alpha = 0.12f))
                        .border(1.dp, tokens.color.error.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.WarningAmber,
                        contentDescription = null,
                        tint = tokens.color.error,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = com.venera.desktop.settings.DesktopI18n.t("当前无法打开系统文件框，本地导出与选取文件功能已禁用。", "System file dialog unavailable; local export and file selection are disabled."),
                        fontSize = 12.5.sp,
                        color = tokens.color.error,
                    )
                }
            }

            BackupAssetStrip(counts = assetCounts, wideViewport = wideViewport)

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val wide = wideViewport
                if (wide) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(
                            modifier = Modifier.weight(0.92f),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            BackupExportPanel(
                                counts = assetCounts,
                                includeSourceAccounts = includeSourceAccounts,
                                busy = busy,
                                canFileDialog = canFileDialog,
                                onToggleIncludeAccounts = onToggleIncludeAccounts,
                                onExport = onExport,
                            )
                            BackupWebDavPanel(
                                config = dav,
                                davState = davState,
                                lastSync = davLastSync,
                                onSave = onSaveDavConfig,
                                onTest = onTestDavConnection,
                                onUpload = onDavUpload,
                                onDownloadAndPreview = onDavDownloadAndPreview,
                            )
                        }
                        Box(modifier = Modifier.weight(1.25f)) {
                            BackupImportWorkspace(
                                stage = importStage,
                                preview = preview,
                                selectedModules = selectedModules,
                                strategy = importStrategy,
                                lastRestore = lastRestore,
                                stagingFileName = stagingFileName,
                                armed = armed,
                                busy = busy,
                                canFileDialog = canFileDialog,
                                importDragOver = importDragOver,
                                onPickFile = onPickImportFile,
                                onInspectStaging = onInspectStagingFile,
                                onClearPreview = onClearPreview,
                                onSelectStrategy = onSelectStrategy,
                                onToggleModule = onToggleModule,
                                onSelectAll = onSelectAllModules,
                                onArmRestore = onArmRestore,
                                onConfirmRestore = onConfirmRestore,
                                onResetDone = onResetImportDone,
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        BackupExportPanel(
                            counts = assetCounts,
                            includeSourceAccounts = includeSourceAccounts,
                            busy = busy,
                            canFileDialog = canFileDialog,
                            onToggleIncludeAccounts = onToggleIncludeAccounts,
                            onExport = onExport,
                        )
                        BackupWebDavPanel(
                            config = dav,
                            davState = davState,
                            lastSync = davLastSync,
                            onSave = onSaveDavConfig,
                            onTest = onTestDavConnection,
                            onUpload = onDavUpload,
                            onDownloadAndPreview = onDavDownloadAndPreview,
                        )
                        BackupImportWorkspace(
                            stage = importStage,
                            preview = preview,
                            selectedModules = selectedModules,
                            strategy = importStrategy,
                            lastRestore = lastRestore,
                            stagingFileName = stagingFileName,
                            armed = armed,
                            busy = busy,
                            canFileDialog = canFileDialog,
                            importDragOver = importDragOver,
                            onPickFile = onPickImportFile,
                            onInspectStaging = onInspectStagingFile,
                            onClearPreview = onClearPreview,
                            onSelectStrategy = onSelectStrategy,
                            onToggleModule = onToggleModule,
                            onSelectAll = onSelectAllModules,
                            onArmRestore = onArmRestore,
                            onConfirmRestore = onConfirmRestore,
                            onResetDone = onResetImportDone,
                        )
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun BackupAssetStrip(counts: DesktopLocalAssetCounts, wideViewport: Boolean) {
    val items = listOf(
        Triple("阅读历史", counts.history.toString(), "comic_history"),
        Triple("漫画收藏", counts.favorites.toString(), "${counts.favoriteFolders} 个收藏夹"),
        Triple("插图收藏", counts.imageFavorites.toString(), "元信息与图链"),
        Triple("画廊收藏与关注", "${counts.galleryFavorites} / ${counts.galleryFollows}", "作品 / 画师"),
        Triple("阅读统计", counts.stats.toString(), "按日记录条目"),
        Triple("屏蔽规则", counts.guardRules.toString(), "关键词与正则"),
    )
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val cols = when {
            wideViewport -> 6
            maxWidth >= 608.dp -> 3
            else -> 2
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items.chunked(cols).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    rowItems.forEach { (title, value, sub) ->
                        BackupSurfaceCard(
                            modifier = Modifier.weight(1f).heightIn(min = 84.dp),
                            cornerRadius = 12.dp,
                            padding = 12.dp,
                        ) {
                            Text(title, fontSize = 11.5.sp, color = VeneraTokens.color.textSecondary)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = value,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = VeneraTokens.color.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(sub, fontSize = 11.sp, color = VeneraTokens.color.textTertiary, maxLines = 1)
                        }
                    }
                    repeat(cols - rowItems.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupExportPanel(
    counts: DesktopLocalAssetCounts,
    includeSourceAccounts: Boolean,
    busy: Boolean,
    canFileDialog: Boolean,
    onToggleIncludeAccounts: (Boolean) -> Unit,
    onExport: () -> Unit,
) {
    val tokens = VeneraTokens
    BackupSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BackupIconBox {
                    Icon(
                        imageVector = Icons.Outlined.Save,
                        contentDescription = null,
                        tint = tokens.color.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Column {
                    Text(com.venera.desktop.settings.DesktopI18n.t("打包导出", "Export Archive"), fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = tokens.color.textPrimary)
                    Text(com.venera.desktop.settings.DesktopI18n.t("生成通用 .venera 归档，可导入安卓端", "Create a universal .venera archive compatible with Android"), fontSize = 11.5.sp, color = tokens.color.textTertiary)
                }
            }
            BackupBadge(
                text = com.venera.desktop.settings.DesktopI18n.t("v5 互通格式", "v5 Format"),
                background = tokens.color.primary.copy(alpha = 0.14f),
                contentColor = tokens.color.primary,
            )
        }

        val scopeItems = listOf(
            com.venera.desktop.settings.DesktopI18n.t("阅读历史", "History") to counts.history,
            com.venera.desktop.settings.DesktopI18n.t("漫画收藏", "Comics") to counts.favorites,
            com.venera.desktop.settings.DesktopI18n.t("插图收藏", "Images") to counts.imageFavorites,
            com.venera.desktop.settings.DesktopI18n.t("画廊收藏", "Gallery") to counts.galleryFavorites,
            com.venera.desktop.settings.DesktopI18n.t("关注画师", "Artists") to counts.galleryFollows,
            com.venera.desktop.settings.DesktopI18n.t("统计与屏蔽", "Stats & Rules") to (counts.stats + counts.guardRules),
        )
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            scopeItems.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    row.forEach { (label, num) ->
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(tokens.color.surfaceContainerHigh.copy(alpha = 0.35f))
                                .border(1.dp, tokens.color.outlineVariant.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 9.dp, vertical = 7.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(label, fontSize = 11.5.sp, color = tokens.color.textSecondary, maxLines = 1)
                            Text(num.toString(), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = tokens.color.textPrimary)
                        }
                    }
                }
            }
        }

        val boxInteraction = remember { MutableInteractionSource() }
        val boxHovered by boxInteraction.collectIsHoveredAsState()
        val boxBg = when {
            includeSourceAccounts -> backupCaution().copy(alpha = 0.12f)
            boxHovered -> tokens.color.surfaceContainerHigh.copy(alpha = 0.50f)
            else -> tokens.color.surfaceContainerHigh.copy(alpha = 0.35f)
        }
        val boxBorder = if (includeSourceAccounts) backupCaution().copy(alpha = 0.35f) else tokens.color.outlineVariant.copy(alpha = 0.18f)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(boxBg)
                .border(1.dp, boxBorder, RoundedCornerShape(10.dp))
                .hoverable(boxInteraction)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(com.venera.desktop.settings.DesktopI18n.t("包含漫画源账号与设置", "Include source accounts & settings"), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = tokens.color.textPrimary)
                    Text(com.venera.desktop.settings.DesktopI18n.t("默认关闭；开启后写入已安装源的登录态与参数", "Off by default; writes login tokens and source config when enabled"), fontSize = 11.5.sp, color = tokens.color.textSecondary)
                }
                VeneraSwitch(
                    checked = includeSourceAccounts,
                    onCheckedChange = onToggleIncludeAccounts,
                )
            }
            AnimatedVisibility(
                visible = includeSourceAccounts,
                enter = fadeIn(tween(VeneraMotion.durationFast)),
                exit = fadeOut(tween(VeneraMotion.durationFast)),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.WarningAmber,
                        contentDescription = null,
                        tint = backupCaution(),
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = com.venera.desktop.settings.DesktopI18n.t("归档将包含源登录凭据与 Token，请妥善保管切勿外传。", "Archive will include source credentials and tokens. Keep it safe."),
                        fontSize = 11.5.sp,
                        color = backupCaution(),
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(com.venera.desktop.settings.DesktopI18n.t("不含图片缓存与源脚本文件", "Excludes image cache & source scripts"), fontSize = 11.5.sp, color = tokens.color.textTertiary)
            if (canFileDialog && !busy) {
                BackupPrimaryButton(
                    label = com.venera.desktop.settings.DesktopI18n.t("导出 .venera 归档", "Export .venera Archive"),
                    onClick = onExport,
                )
            } else if (busy) {
                Text(com.venera.desktop.settings.DesktopI18n.t("正在处理…", "Processing…"), fontSize = 12.sp, color = tokens.color.textSecondary)
            }
        }
    }
}

@Composable
private fun BackupWebDavPanel(
    config: DesktopWebDavSync.Config,
    davState: DesktopWebDavSync.State,
    lastSync: DesktopWebDavSync.LastSync?,
    onSave: (DesktopWebDavSync.Config) -> Unit,
    onTest: (DesktopWebDavSync.Config) -> Unit,
    onUpload: () -> Unit,
    onDownloadAndPreview: () -> Unit,
) {
    val tokens = VeneraTokens
    var serverUrl by remember(config.serverUrl) { mutableStateOf(config.serverUrl) }
    var username by remember(config.username) { mutableStateOf(config.username) }
    var password by remember(config.password) { mutableStateOf(config.password) }
    var remotePath by remember(config.remotePath) { mutableStateOf(config.remotePath) }
    var showPwd by remember { mutableStateOf(false) }

    val running = davState == DesktopWebDavSync.State.Running
    val editedConfig = DesktopWebDavSync.Config(serverUrl, username, password, remotePath)
    val lastSyncLine = lastSync?.let { info ->
        val time = if (info.timestamp > 0L) {
            SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date(info.timestamp))
        } else {
            "未知时间"
        }
        "上次同步：$time · ${info.fileName}"
    }
    // 正在跑或失败时先说当下；Done 与上次同步并存时两句都念，连接测试的反馈不被旧读数盖掉。
    val doneMessage = (davState as? DesktopWebDavSync.State.Done)?.message
    val statusText = when {
        davState is DesktopWebDavSync.State.Running || davState is DesktopWebDavSync.State.Failed ->
            DesktopWebDavSync.stateLine(davState)
        doneMessage != null && lastSyncLine != null -> "$doneMessage · $lastSyncLine"
        lastSyncLine != null -> lastSyncLine
        else -> DesktopWebDavSync.stateLine(davState)
    }

    BackupSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BackupIconBox {
                    Icon(
                        imageVector = Icons.Outlined.CloudQueue,
                        contentDescription = null,
                        tint = tokens.color.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Column {
                    Text(com.venera.desktop.settings.DesktopI18n.t("WebDAV 云端同步", "WebDAV Cloud Sync"), fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = tokens.color.textPrimary)
                    Text(com.venera.desktop.settings.DesktopI18n.t("上传或拉取云端归档并送入右侧预览", "Upload or pull cloud archives to preview"), fontSize = 11.5.sp, color = tokens.color.textTertiary)
                }
            }
            BackupBadge(
                text = if (config.serverUrl.isNotBlank()) com.venera.desktop.settings.DesktopI18n.t("已配置", "Configured") else com.venera.desktop.settings.DesktopI18n.t("未配置", "Not set"),
                background = if (config.serverUrl.isNotBlank()) backupSafe().copy(alpha = 0.14f) else tokens.color.surfaceContainerHigh.copy(alpha = 0.45f),
                contentColor = if (config.serverUrl.isNotBlank()) backupSafe() else tokens.color.textSecondary,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BackupTextField(
                label = com.venera.desktop.settings.DesktopI18n.t("服务器地址", "Server URL"),
                hint = com.venera.desktop.settings.DesktopI18n.t("https://… 根地址", "https://… base URL"),
                value = serverUrl,
                onValueChange = { serverUrl = it },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    BackupTextField(
                        label = com.venera.desktop.settings.DesktopI18n.t("账号", "Username"),
                        hint = com.venera.desktop.settings.DesktopI18n.t("用户名", "Username"),
                        value = username,
                        onValueChange = { username = it },
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    BackupTextField(
                        label = com.venera.desktop.settings.DesktopI18n.t("密码（本地明文）", "Password (stored locally)"),
                        hint = com.venera.desktop.settings.DesktopI18n.t("密码或应用专用密码", "Password or app password"),
                        value = password,
                        onValueChange = { password = it },
                        isPassword = !showPwd,
                        trailing = {
                            Text(
                                text = if (showPwd) com.venera.desktop.settings.DesktopI18n.t("隐藏", "Hide") else com.venera.desktop.settings.DesktopI18n.t("显示", "Show"),
                                fontSize = 11.sp,
                                color = tokens.color.primary,
                                modifier = Modifier.clickable { showPwd = !showPwd },
                            )
                        },
                    )
                }
            }
            BackupTextField(
                label = com.venera.desktop.settings.DesktopI18n.t("远端目录", "Remote Path"),
                hint = "/Venera/",
                value = remotePath,
                onValueChange = { remotePath = it },
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(tokens.color.surfaceContainerHigh.copy(alpha = 0.35f))
                .border(1.dp, tokens.color.outlineVariant.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = statusText,
                fontSize = 11.5.sp,
                color = if (davState is DesktopWebDavSync.State.Failed) tokens.color.error else tokens.color.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = com.venera.desktop.settings.DesktopI18n.t("保存配置", "Save"),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = tokens.color.primary,
                    modifier = Modifier.clickable(enabled = !running) { onSave(editedConfig) },
                )
                Text(
                    text = com.venera.desktop.settings.DesktopI18n.t("测试连接", "Test"),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = tokens.color.primary,
                    modifier = Modifier.clickable(enabled = !running) {
                        onSave(editedConfig)
                        onTest(editedConfig)
                    },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BackupGhostButton(
                label = if (running) "传输中…" else "上传最新归档",
                icon = { Icon(Icons.Outlined.CloudUpload, null, Modifier.size(14.dp)) },
                enabled = !running,
                onClick = {
                    onSave(editedConfig)
                    onUpload()
                },
                modifier = Modifier.weight(1f),
            )
            BackupGhostButton(
                label = if (running) "传输中…" else "拉取云端并预览",
                icon = { Icon(Icons.Outlined.CloudDownload, null, Modifier.size(14.dp)) },
                enabled = !running,
                onClick = {
                    onSave(editedConfig)
                    onDownloadAndPreview()
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun BackupImportWorkspace(
    stage: DesktopBackupImportStage,
    preview: DesktopArchivePreview?,
    selectedModules: Set<DesktopBackupModule>,
    strategy: DesktopImportStrategy,
    lastRestore: DesktopVeneraRestoreOutcome?,
    stagingFileName: String?,
    armed: Boolean,
    busy: Boolean,
    canFileDialog: Boolean,
    importDragOver: Boolean,
    onPickFile: () -> Unit,
    onInspectStaging: () -> Unit,
    onClearPreview: () -> Unit,
    onSelectStrategy: (DesktopImportStrategy) -> Unit,
    onToggleModule: (DesktopBackupModule) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onArmRestore: () -> Unit,
    onConfirmRestore: () -> Unit,
    onResetDone: () -> Unit,
) {
    val tokens = VeneraTokens
    BackupSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BackupIconBox {
                    Icon(
                        imageVector = Icons.Outlined.UploadFile,
                        contentDescription = null,
                        tint = tokens.color.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Column {
                    Text(com.venera.desktop.settings.DesktopI18n.t("导入与还原", "Import & Restore"), fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = tokens.color.textPrimary)
                    Text(com.venera.desktop.settings.DesktopI18n.t("自动识别安卓 .venera 归档与旧版桌面 JSON", "Auto-detects Android .venera archives & legacy desktop JSON"), fontSize = 11.5.sp, color = tokens.color.textTertiary)
                }
            }
            val badgeText = when (stage) {
                DesktopBackupImportStage.IDLE -> com.venera.desktop.settings.DesktopI18n.t("支持 .venera / .json", ".venera / .json")
                DesktopBackupImportStage.PREVIEW -> com.venera.desktop.settings.DesktopI18n.t("等待确认导入", "Ready to import")
                DesktopBackupImportStage.DONE -> com.venera.desktop.settings.DesktopI18n.t("导入完成", "Completed")
            }
            BackupBadge(
                text = badgeText,
                background = tokens.color.surfaceContainerHigh,
                contentColor = tokens.color.textSecondary,
            )
        }

        AnimatedContent(
            targetState = stage,
            transitionSpec = {
                fadeIn(tween(VeneraMotion.durationFast)) togetherWith fadeOut(tween(VeneraMotion.durationFast))
            },
            label = "import_stage",
        ) { current ->
            when (current) {
                DesktopBackupImportStage.IDLE -> {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        val hoverSource = remember { MutableInteractionSource() }
                        val hovered by hoverSource.collectIsHoveredAsState()
                        val dragActive = (hovered || importDragOver) && canFileDialog && !busy
                        val dashColor = if (dragActive) tokens.color.primary.copy(alpha = 0.45f)
                        else tokens.color.outlineVariant.copy(alpha = 0.30f)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 220.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (dragActive) tokens.color.primary.copy(alpha = 0.14f)
                                    else tokens.color.surfaceContainerHigh.copy(alpha = 0.30f),
                                )
                                .drawBehind {
                                    val stroke = 1.5.dp.toPx()
                                    val inset = stroke / 2f
                                    drawRoundRect(
                                        color = dashColor,
                                        topLeft = Offset(inset, inset),
                                        size = Size(size.width - stroke, size.height - stroke),
                                        cornerRadius = CornerRadius(12.dp.toPx() - inset),
                                        style = Stroke(
                                            width = stroke,
                                            pathEffect = PathEffect.dashPathEffect(
                                                floatArrayOf(6.dp.toPx(), 4.dp.toPx()),
                                            ),
                                        ),
                                    )
                                }
                                .hoverable(hoverSource)
                                .clickable(enabled = canFileDialog && !busy, onClick = onPickFile)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(tokens.color.surfaceContainerHigh.copy(alpha = 0.45f))
                                    .border(1.dp, tokens.color.outlineVariant.copy(alpha = 0.22f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.UploadFile,
                                    contentDescription = null,
                                    tint = tokens.color.primary,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(com.venera.desktop.settings.DesktopI18n.t("拖入备份文件到此处，或点击浏览本机", "Drop a backup file here, or click to browse"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = tokens.color.textPrimary)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = com.venera.desktop.settings.DesktopI18n.t("读取后将先展示包内数据明细与条数，确认后再写入数据库，不会直接覆盖当前数据。", "Archive contents will be previewed before writing to database."),
                                fontSize = 12.sp,
                                color = tokens.color.textSecondary,
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                BackupBadge(com.venera.desktop.settings.DesktopI18n.t("安卓 / 桌面通用 .venera", "Universal .venera"), tokens.color.primary.copy(alpha = 0.14f), tokens.color.primary)
                                BackupBadge(com.venera.desktop.settings.DesktopI18n.t("旧版桌面 .json", "Legacy .json"), tokens.color.surfaceContainerHigh.copy(alpha = 0.45f), tokens.color.textSecondary)
                            }
                        }

                        if (!stagingFileName.isNullOrBlank()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(tokens.color.surfaceContainerHigh.copy(alpha = 0.35f))
                                    .border(1.dp, tokens.color.outlineVariant.copy(alpha = 0.18f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("已暂存的云端归档：$stagingFileName", fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = tokens.color.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("来自 WebDAV 下载中转目录", fontSize = 11.sp, color = tokens.color.textTertiary)
                                }
                                BackupGhostButton(label = "解析此文件", enabled = !busy, onClick = onInspectStaging)
                            }
                        }
                    }
                }

                DesktopBackupImportStage.PREVIEW -> {
                    if (preview != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(tokens.color.surfaceContainerHigh.copy(alpha = 0.35f))
                                    .border(1.dp, tokens.color.outlineVariant.copy(alpha = 0.18f), RoundedCornerShape(10.dp))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(30.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(backupSafe().copy(alpha = 0.14f))
                                            .border(1.dp, backupSafe().copy(alpha = 0.35f), RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Check,
                                            contentDescription = null,
                                            tint = backupSafe(),
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(preview.fileName, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = tokens.color.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Spacer(Modifier.height(2.dp))
                                        val timeStr = if (preview.timestamp > 0L) {
                                            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(preview.timestamp))
                                        } else {
                                            "未知时间"
                                        }
                                        Text(
                                            text = "${preview.sourceApp} · ${preview.formatLabel} v${preview.version} · $timeStr",
                                            fontSize = 11.5.sp,
                                            color = tokens.color.textSecondary,
                                        )
                                    }
                                }
                                BackupGhostButton(label = "重选文件", enabled = !busy && canFileDialog, onClick = onClearPreview)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                StrategyOptionCard(
                                    title = "智能合并（推荐）",
                                    desc = "保留本机现有记录，补充缺失条目并更新进度",
                                    selected = strategy == DesktopImportStrategy.MERGE,
                                    danger = false,
                                    onClick = { onSelectStrategy(DesktopImportStrategy.MERGE) },
                                    modifier = Modifier.weight(1f),
                                )
                                StrategyOptionCard(
                                    title = "清空并覆盖",
                                    desc = "先清空所勾选模块的本机数据，以备份为准",
                                    selected = strategy == DesktopImportStrategy.OVERWRITE,
                                    danger = true,
                                    onClick = { onSelectStrategy(DesktopImportStrategy.OVERWRITE) },
                                    modifier = Modifier.weight(1f),
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("选择要导入的数据模块", fontSize = 12.sp, color = tokens.color.textSecondary)
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(
                                        text = "全选",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = tokens.color.primary,
                                        modifier = Modifier.clickable { onSelectAll(true) },
                                    )
                                    Text(
                                        text = "全不选",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = tokens.color.primary,
                                        modifier = Modifier.clickable { onSelectAll(false) },
                                    )
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                DesktopBackupModule.entries.forEach { module ->
                                    val available = preview.isModuleAvailable(module)
                                    val checked = available && module in selectedModules
                                    val rowInteraction = remember(module) { MutableInteractionSource() }
                                    val rowHovered by rowInteraction.collectIsHoveredAsState()
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(9.dp))
                                            .background(
                                                if (rowHovered && available && !busy) tokens.color.surfaceContainerHigh.copy(alpha = 0.50f)
                                                else tokens.color.surfaceContainerHigh.copy(alpha = 0.35f),
                                            )
                                            .border(1.dp, tokens.color.outlineVariant.copy(alpha = 0.18f), RoundedCornerShape(9.dp))
                                            .hoverable(rowInteraction)
                                            .clickable(enabled = available && !busy) { onToggleModule(module) }
                                            .padding(horizontal = 12.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            VeneraCheckbox(
                                                checked = checked,
                                                onCheckedChange = { if (available && !busy) onToggleModule(module) },
                                                enabled = available && !busy,
                                            )
                                            Column {
                                                Text(
                                                    text = module.label,
                                                    fontSize = 12.5.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = if (available) tokens.color.textPrimary else tokens.color.textTertiary,
                                                )
                                                Text(
                                                    text = module.note,
                                                    fontSize = 11.sp,
                                                    color = tokens.color.textTertiary,
                                                )
                                            }
                                        }
                                        Text(
                                            text = preview.countText(module),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (available) tokens.color.textSecondary else tokens.color.textTertiary,
                                        )
                                    }
                                }
                            }

                            if (strategy == DesktopImportStrategy.OVERWRITE) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(tokens.color.error.copy(alpha = 0.12f))
                                        .border(1.dp, tokens.color.error.copy(alpha = 0.35f), RoundedCornerShape(9.dp))
                                        .padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(Icons.Outlined.WarningAmber, null, tint = tokens.color.error, modifier = Modifier.size(15.dp))
                                    Text(
                                        text = if (armed) "再次点击右侧按钮将立即清空并覆盖所选数据。" else "覆盖模式将先删除已勾选模块的本机现有数据，且无法撤销。",
                                        fontSize = 12.sp,
                                        color = tokens.color.error,
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "已选 ${selectedModules.count { preview.isModuleAvailable(it) }} 项模块" +
                                        " · 共 ${preview.selectedRecordCount(selectedModules)} 条记录",
                                    fontSize = 11.5.sp,
                                    color = tokens.color.textTertiary,
                                )
                                if (!busy) {
                                    val hasSelected = selectedModules.any { preview.isModuleAvailable(it) }
                                    if (strategy == DesktopImportStrategy.OVERWRITE) {
                                        BackupPrimaryButton(
                                            label = if (armed) "确认覆盖所选数据" else "覆盖导入所选数据",
                                            danger = true,
                                            enabled = hasSelected,
                                            onClick = if (armed) onConfirmRestore else onArmRestore,
                                        )
                                    } else {
                                        BackupPrimaryButton(
                                            label = "开始合并导入",
                                            enabled = hasSelected,
                                            onClick = onConfirmRestore,
                                        )
                                    }
                                } else {
                                    Text("正在导入…", fontSize = 12.sp, color = tokens.color.textSecondary)
                                }
                            }
                        }
                    }
                }

                DesktopBackupImportStage.DONE -> {
                    if (lastRestore != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(backupSafe().copy(alpha = 0.14f))
                                    .border(1.dp, backupSafe().copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(tokens.color.surfaceContainerHigh.copy(alpha = 0.45f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Outlined.Check, null, tint = backupSafe(), modifier = Modifier.size(18.dp))
                                }
                                Column {
                                    val modeName = if (lastRestore.strategy == DesktopImportStrategy.MERGE) "智能合并" else "清空覆盖"
                                    Text("导入已完成（$modeName）", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = backupSafe())
                                    Text("来源：${lastRestore.fileName} · 数据已即时生效", fontSize = 12.sp, color = tokens.color.textSecondary)
                                }
                            }

                            val resultRows = listOf(
                                "阅读历史" to "${lastRestore.history} 条",
                                "漫画收藏" to "${lastRestore.favorites} 本 / ${lastRestore.folders} 夹",
                                "插图收藏" to "${lastRestore.imageFavorites} 张",
                                "画廊收藏与关注" to "${lastRestore.galleryFavorites} / ${lastRestore.galleryFollows} 项",
                                "阅读统计" to "${lastRestore.stats} 条",
                                "屏蔽规则" to "${lastRestore.guardImported} 条生效",
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                resultRows.chunked(2).forEach { pair ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        pair.forEach { (label, valStr) ->
                                            Row(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(9.dp))
                                                    .background(tokens.color.surfaceContainerHigh.copy(alpha = 0.35f))
                                                    .border(1.dp, tokens.color.outlineVariant.copy(alpha = 0.18f), RoundedCornerShape(9.dp))
                                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Text(label, fontSize = 12.sp, color = tokens.color.textSecondary)
                                                Text(valStr, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = tokens.color.textPrimary)
                                            }
                                        }
                                    }
                                }
                            }

                            if (lastRestore.guardSkipped > 0 || lastRestore.sourceAccountsSkipped > 0 || lastRestore.sourceAccountsImported > 0 || lastRestore.follows > 0) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(backupCaution().copy(alpha = 0.10f))
                                        .border(1.dp, backupCaution().copy(alpha = 0.30f), RoundedCornerShape(9.dp))
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    if (lastRestore.sourceAccountsImported > 0) {
                                        Text("• 已写入 ${lastRestore.sourceAccountsImported} 个漫画源账号配置", fontSize = 12.sp, color = tokens.color.textSecondary)
                                    }
                                    if (lastRestore.guardSkipped > 0) {
                                        Text("• 已跳过 ${lastRestore.guardSkipped} 条无效正则表达式的屏蔽规则", fontSize = 12.sp, color = backupCaution())
                                    }
                                    if (lastRestore.sourceAccountsSkipped > 0) {
                                        Text("• 已跳过 ${lastRestore.sourceAccountsSkipped} 个本机未安装的漫画源账号配置", fontSize = 12.sp, color = tokens.color.textSecondary)
                                    }
                                    if (lastRestore.follows > 0) {
                                        Text("• 已恢复 ${lastRestore.follows} 本追更（${lastRestore.followSyncs} 条网站收藏夹订阅）", fontSize = 12.sp, color = tokens.color.textSecondary)
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("本机资产概览已同步刷新", fontSize = 11.5.sp, color = tokens.color.textTertiary)
                                BackupGhostButton(label = "继续导入其他文件", onClick = onResetDone)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 成功态：深色取 `ToneSafe`（#78D6C0，与稿一致），浅色取现有主题化 `StatusSuccess`
 * （比固定 #78D6C0 在白底上可读）；`ToneSafe` 的浅色 #128766 对位在现有 token 里没有。
 */
@Composable
private fun backupSafe(): Color =
    if (LocalVeneraDarkTheme.current) GalleryTokens.Color.ToneSafe else GalleryTokens.Color.StatusSuccess

/**
 * 警告态：深色取 `ToneCaution`（#F2B87C，与稿一致）；浅色没有 #B4630B 对位，
 * 退到 MD3 警告文字槽 `onTertiaryContainer`，避免固定亮橙压白底发脏。
 */
@Composable
private fun backupCaution(): Color =
    if (LocalVeneraDarkTheme.current) GalleryTokens.Color.ToneCaution else VeneraTokens.color.onTertiaryContainer

/** 预览态「共 N 条记录」的计数：只数勾选且包内可用的模块，口径与各行的条数文本一致。 */
private fun DesktopArchivePreview.selectedRecordCount(selected: Set<DesktopBackupModule>): Int {
    fun on(module: DesktopBackupModule): Boolean = module in selected && isModuleAvailable(module)
    var total = 0
    if (on(DesktopBackupModule.HISTORY)) total += historyCount
    if (on(DesktopBackupModule.FAVORITES)) total += favoriteCount
    if (on(DesktopBackupModule.IMAGE_FAVORITES)) total += imageFavoriteCount
    if (on(DesktopBackupModule.GALLERY)) total += galleryFavoriteCount + galleryFollowCount
    if (on(DesktopBackupModule.STATS)) total += statsCount
    if (on(DesktopBackupModule.GUARD_RULES)) total += guardRuleCount
    if (on(DesktopBackupModule.SOURCE_ACCOUNTS)) total += sourceAccountCount
    return total
}

@Composable
private fun StrategyOptionCard(
    title: String,
    desc: String,
    selected: Boolean,
    danger: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    val activeColor = if (danger) tokens.color.error else tokens.color.primary
    val bg = when {
        selected -> activeColor.copy(alpha = 0.14f)
        hovered -> tokens.color.surfaceContainerHigh.copy(alpha = 0.50f)
        else -> tokens.color.surfaceContainerHigh.copy(alpha = 0.35f)
    }
    val border = if (selected) activeColor.copy(alpha = 0.55f) else tokens.color.outlineVariant.copy(alpha = 0.20f)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .hoverable(hoverSource)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = title,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected && danger) tokens.color.error else tokens.color.textPrimary,
        )
        Text(desc, fontSize = 11.sp, color = tokens.color.textSecondary, lineHeight = 15.sp)
    }
}

@Composable
private fun BackupSurfaceCard(
    modifier: Modifier = Modifier,
    cornerRadius: androidx.compose.ui.unit.Dp = 14.dp,
    padding: androidx.compose.ui.unit.Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    val tokens = VeneraTokens
    val shape = RoundedCornerShape(cornerRadius)
    Column(
        modifier = modifier
            .background(tokens.color.surfaceContainerHigh.copy(alpha = 0.45f), shape)
            .border(
                1.dp,
                tokens.color.outlineVariant.copy(alpha = 0.25f),
                shape,
            )
            .clip(shape)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        content()
    }
}

@Composable
private fun BackupIconBox(content: @Composable () -> Unit) {
    val tokens = VeneraTokens
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(tokens.color.surfaceContainerHigh.copy(alpha = 0.45f))
            .border(1.dp, tokens.color.outlineVariant.copy(alpha = 0.22f), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun BackupBadge(text: String, background: Color, contentColor: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = contentColor)
    }
}

@Composable
private fun BackupPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    enabled: Boolean = true,
) {
    com.venera.desktop.ui.DesktopButton(
        text = label,
        onClick = onClick,
        modifier = modifier,
        size = com.venera.desktop.ui.DesktopButtonSize.Medium,
        variant = if (danger) com.venera.desktop.ui.DesktopButtonVariant.Danger else com.venera.desktop.ui.DesktopButtonVariant.Primary,
        enabled = enabled,
        colors = com.venera.desktop.ui.DesktopButtonDefaults.colors(
            variant = if (danger) com.venera.desktop.ui.DesktopButtonVariant.Danger else com.venera.desktop.ui.DesktopButtonVariant.Primary,
            filledDanger = danger,
        ),
    )
}

@Composable
private fun BackupGhostButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: (@Composable () -> Unit)? = null,
) {
    com.venera.desktop.ui.DesktopButton(
        onClick = onClick,
        modifier = modifier,
        size = com.venera.desktop.ui.DesktopButtonSize.Medium,
        variant = com.venera.desktop.ui.DesktopButtonVariant.Secondary,
        enabled = enabled,
        horizontalPadding = 12.dp,
    ) {
        if (icon != null) {
            icon()
        }
        Text(
            text = label,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BackupTextField(
    label: String,
    hint: String,
    value: String,
    onValueChange: (String) -> Unit,
    isPassword: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val tokens = VeneraTokens
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 11.5.sp, color = tokens.color.textSecondary)
        com.venera.desktop.ui.components.DesktopInputField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = hint,
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            trailing = {
                if (trailing != null) {
                    Spacer(Modifier.width(8.dp))
                    trailing()
                }
            },
        )
    }
}
