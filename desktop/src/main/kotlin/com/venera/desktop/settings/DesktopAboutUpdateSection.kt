package com.venera.desktop.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.InstallDesktop
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.data.update.UpdateDownload
import com.venera.compose.ui.tokens.StatusColors
import com.venera.compose.ui.tokens.VeneraMotion
import com.venera.desktop.platform.DesktopUpdateCheck
import com.venera.desktop.ui.DesktopButton
import com.venera.desktop.ui.DesktopButtonSize
import com.venera.desktop.ui.DesktopButtonVariant
import com.venera.desktop.ui.DesktopSwitch
import java.io.File

/**
 * 「关于与更新」专属美化面板：
 * 1. VComic 软件介绍 Hero 卡片（应用徽章、版本胶囊、定位简介、特性标签）
 * 2. 自动检查更新开关 + 手动检查更新 + 发现新版时的更新说明 & 一键下载安装区
 * 3. 项目主页与历史发行版快捷入口
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DesktopAboutUpdateContent(
    palette: SettingsDialogPalette,
    autoCheckEnabled: Boolean,
    onToggleAutoCheck: (Boolean) -> Unit,
    canCheckUpdate: Boolean,
    onCheckUpdate: () -> Unit,
    onDownloadInstaller: (com.venera.compose.data.update.ReleaseAsset) -> Unit,
    onLaunchInstaller: (File) -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val updateResult by DesktopUpdateCheck.state.collectAsState()
    val downloadState by DesktopUpdateCheck.downloadState.collectAsState()
    val isEn = DesktopLanguage.isEnglish()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ── 1. 软件介绍 Hero 卡片 ──
        AboutHeroCard(
            palette = palette,
            isEn = isEn,
            onOpenHomepage = { onOpenUrl(DesktopUpdateCheck.HOMEPAGE_URL) },
        )

        // ── 2. 应用更新与自动检查卡片 ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(palette.field)
                .border(0.75.dp, palette.divider, RoundedCornerShape(12.dp)),
        ) {
            // 自动检查更新开关行
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.focus.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = null,
                        tint = palette.focus,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = if (isEn) "Automatic update check" else "应用启动时自动检查更新",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = palette.text1,
                    )
                    Text(
                        text = if (isEn) {
                            "Quietly checks for new releases on startup (at most once every 24h)"
                        } else {
                            "启动后静默检查新版本（每 24 小时最多一次，不打扰阅读）"
                        },
                        fontSize = 11.sp,
                        color = palette.text2,
                    )
                }
                DesktopSwitch(
                    checked = autoCheckEnabled,
                    onCheckedChange = onToggleAutoCheck,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.75.dp)
                    .background(palette.divider),
            )

            // 当前更新状态 + 手动检查按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val (statusIcon, statusTint) = when (updateResult) {
                    is DesktopUpdateCheck.Result.Available -> Icons.Outlined.NewReleases to palette.focus
                    DesktopUpdateCheck.Result.UpToDate -> Icons.Outlined.CheckCircle to StatusColors.Healthy
                    DesktopUpdateCheck.Result.Failed -> Icons.Outlined.ErrorOutline to palette.danger
                    else -> Icons.Outlined.CloudDownload to palette.text2
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(statusTint.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = null,
                        tint = statusTint,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = if (isEn) "Software Update" else "版本更新",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = palette.text1,
                    )
                    Text(
                        text = if (!canCheckUpdate) {
                            if (isEn) "Network client unavailable" else "网络引擎未就绪，暂无法检查"
                        } else {
                            DesktopUpdateCheck.readoutOf(updateResult, DESKTOP_APP_VERSION)
                        },
                        fontSize = 11.sp,
                        color = if (updateResult is DesktopUpdateCheck.Result.Available) palette.focus else palette.text2,
                    )
                }
                val checking = updateResult == DesktopUpdateCheck.Result.Checking
                DesktopButton(
                    text = when {
                        checking -> if (isEn) "Checking…" else "检查中…"
                        updateResult is DesktopUpdateCheck.Result.Available -> if (isEn) "Recheck" else "重新检查"
                        else -> if (isEn) "Check now" else "检查更新"
                    },
                    onClick = onCheckUpdate,
                    enabled = canCheckUpdate && !checking && downloadState !is UpdateDownload.Running,
                    variant = if (updateResult !is DesktopUpdateCheck.Result.Available) {
                        DesktopButtonVariant.Primary
                    } else {
                        DesktopButtonVariant.Secondary
                    },
                    size = DesktopButtonSize.Medium,
                )
            }

            // 发现新版时展开：更新说明 + 一键下载安装
            AnimatedVisibility(
                visible = updateResult is DesktopUpdateCheck.Result.Available,
                enter = fadeIn(VeneraMotion.tweenNormal()) + expandVertically(VeneraMotion.tweenNormal()),
                exit = fadeOut(VeneraMotion.tweenFast()) + shrinkVertically(VeneraMotion.tweenFast()),
            ) {
                val available = updateResult as? DesktopUpdateCheck.Result.Available ?: return@AnimatedVisibility
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.75.dp)
                            .background(palette.divider),
                    )

                    // 更新说明区
                    ReleaseNotesBox(
                        version = available.remote,
                        changelog = available.changelog,
                        palette = palette,
                        isEn = isEn,
                    )

                    // 一键下载与安装控制区
                    InstallerActionPanel(
                        available = available,
                        downloadState = downloadState,
                        palette = palette,
                        isEn = isEn,
                        onDownload = { asset -> onDownloadInstaller(asset) },
                        onLaunch = onLaunchInstaller,
                        onOpenReleasePage = { onOpenUrl(available.releaseUrl) },
                    )
                }
            }
        }

        // ── 3. 相关链接卡片 ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(palette.field)
                .border(0.75.dp, palette.divider, RoundedCornerShape(12.dp)),
        ) {
            AboutLinkRow(
                icon = Icons.Outlined.Public,
                title = if (isEn) "Project Homepage" else "项目主页",
                subtitle = DesktopUpdateCheck.HOMEPAGE_URL,
                palette = palette,
                onClick = { onOpenUrl(DesktopUpdateCheck.HOMEPAGE_URL) },
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.75.dp)
                    .background(palette.divider),
            )
            AboutLinkRow(
                icon = Icons.Outlined.Description,
                title = if (isEn) "Releases & Changelogs" else "历史版本与更新日志",
                subtitle = if (isEn) "Open GitHub Releases in browser" else "在系统浏览器中查看完整发行版与安装包",
                palette = palette,
                onClick = { onOpenUrl(DesktopUpdateCheck.RELEASES_URL) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AboutHeroCard(
    palette: SettingsDialogPalette,
    isEn: Boolean,
    onOpenHomepage: () -> Unit,
) {
    val accent = palette.focus
    val secondaryAccent = Color(0xFF7C5CFF)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        accent.copy(alpha = 0.14f),
                        secondaryAccent.copy(alpha = 0.10f),
                        palette.field,
                    ),
                ),
            )
            .border(0.75.dp, accent.copy(alpha = 0.28f), RoundedCornerShape(16.dp))
            .padding(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                @Suppress("DEPRECATION")
                Image(
                    painter = painterResource("vcomic_icon.png"),
                    contentDescription = "VComic",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "VComic",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.text1,
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(accent.copy(alpha = 0.16f))
                                .border(0.5.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = "v$DESKTOP_APP_VERSION",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = accent,
                            )
                        }
                    }
                    Text(
                        text = if (isEn) {
                            "A modern desktop comic & gallery reader with multi-source extensions, local library management, and offline downloads."
                        } else {
                            "现代化桌面端漫画与画廊阅览中枢，支持多源扩展插件、本地收藏库管理、离线批量下载与沉浸式双页阅读。"
                        },
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = palette.text2,
                    )
                }
                DesktopButton(
                    text = if (isEn) "GitHub" else "主页",
                    onClick = onOpenHomepage,
                    variant = DesktopButtonVariant.Secondary,
                    size = DesktopButtonSize.Compact,
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val tags = if (isEn) {
                    listOf("Compose Desktop", "Multi-Source", "Offline Library", "Keyboard Shortcuts")
                } else {
                    listOf("Compose 桌面原生", "多源脚本扩展", "本地与离线管理", "快捷键与双页阅读")
                }
                tags.forEach { tag ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(palette.bg.copy(alpha = 0.65f))
                            .border(0.5.dp, palette.dividerStrong, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = tag,
                            fontSize = 10.sp,
                            color = palette.text2,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReleaseNotesBox(
    version: String,
    changelog: String,
    palette: SettingsDialogPalette,
    isEn: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(palette.bg.copy(alpha = 0.7f))
            .border(0.5.dp, palette.divider, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = if (isEn) "What's new in v$version" else "v$version 更新说明",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.text1,
        )
        val lines = remember(changelog) {
            changelog.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .take(40)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 180.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (lines.isEmpty()) {
                Text(
                    text = if (isEn) "No release notes provided." else "该版本暂未附带更新说明。",
                    fontSize = 11.sp,
                    color = palette.text3,
                )
            } else {
                lines.forEach { raw ->
                    val cleaned = raw.removePrefix("### ").removePrefix("## ").removePrefix("# ")
                        .removePrefix("- ").removePrefix("* ")
                    val isHeading = raw.startsWith("#")
                    val isBullet = raw.startsWith("- ") || raw.startsWith("* ")
                    Text(
                        text = if (isBullet) "• $cleaned" else cleaned,
                        fontSize = if (isHeading) 12.sp else 11.sp,
                        fontWeight = if (isHeading) FontWeight.SemiBold else FontWeight.Normal,
                        lineHeight = 16.sp,
                        color = if (isHeading) palette.text1 else palette.text2,
                    )
                }
            }
        }
    }
}

@Composable
private fun InstallerActionPanel(
    available: DesktopUpdateCheck.Result.Available,
    downloadState: UpdateDownload,
    palette: SettingsDialogPalette,
    isEn: Boolean,
    onDownload: (com.venera.compose.data.update.ReleaseAsset) -> Unit,
    onLaunch: (File) -> Unit,
    onOpenReleasePage: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (downloadState) {
            UpdateDownload.Idle -> {
                val asset = available.installer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = asset?.name ?: if (isEn) "Visit release page to download" else "请前往发布页手动下载安装包",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = palette.text1,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (asset != null && asset.size > 0L) {
                            Text(
                                text = formatBytes(asset.size) + if (asset.sha256 != null) " · SHA-256" else "",
                                fontSize = 11.sp,
                                color = palette.text2,
                            )
                        }
                    }
                    DesktopButton(
                        text = if (isEn) "Release page" else "发布页",
                        onClick = onOpenReleasePage,
                        variant = DesktopButtonVariant.Secondary,
                        size = DesktopButtonSize.Medium,
                    )
                    if (asset != null) {
                        DesktopButton(
                            text = if (isEn) "Download & Install" else "一键下载安装",
                            onClick = { onDownload(asset) },
                            variant = DesktopButtonVariant.Primary,
                            size = DesktopButtonSize.Medium,
                        )
                    }
                }
            }
            is UpdateDownload.Running -> {
                val fraction = if (downloadState.totalBytes > 0L) {
                    (downloadState.downloadedBytes.toFloat() / downloadState.totalBytes.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }
                val animatedFraction by animateFloatAsState(
                    targetValue = fraction,
                    animationSpec = VeneraMotion.tweenFast(),
                    label = "UpdateProgress",
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        val pct = (animatedFraction * 100).toInt()
                        Text(
                            text = if (isEn) "Downloading… $pct%" else "正在下载安装包… $pct%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = palette.text1,
                        )
                        Text(
                            text = "${formatBytes(downloadState.downloadedBytes)} / ${formatBytes(downloadState.totalBytes)} (${formatBytes(downloadState.speedBytesPerSec)}/s)",
                            fontSize = 11.sp,
                            color = palette.text2,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(palette.divider),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedFraction.coerceAtLeast(0.02f))
                                .height(6.dp)
                                .clip(CircleShape)
                                .background(palette.focus),
                        )
                    }
                }
            }
            UpdateDownload.Verifying -> {
                Text(
                    text = if (isEn) "Verifying SHA-256 checksum…" else "下载完成，正在校验安装包完整性 (SHA-256)…",
                    fontSize = 12.sp,
                    color = palette.focus,
                )
            }
            is UpdateDownload.Ready -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.InstallDesktop,
                        contentDescription = null,
                        tint = StatusColors.Healthy,
                        modifier = Modifier.size(18.dp),
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = if (isEn) "Installer ready: ${downloadState.file.name}" else "安装包已就绪：${downloadState.file.name}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = StatusColors.Healthy,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (downloadState.verified) {
                                if (isEn) "SHA-256 verified · ${formatBytes(downloadState.size)}" else "已通过 SHA-256 校验 · ${formatBytes(downloadState.size)}"
                            } else {
                                if (isEn) "Unverified (no digest from release) · ${formatBytes(downloadState.size)}" else "发行版未提供校验摘要 · ${formatBytes(downloadState.size)}"
                            },
                            fontSize = 11.sp,
                            color = palette.text2,
                        )
                    }
                    DesktopButton(
                        text = if (isEn) "Install Now" else "立即安装",
                        onClick = { onLaunch(downloadState.file) },
                        variant = DesktopButtonVariant.Primary,
                        size = DesktopButtonSize.Medium,
                    )
                }
            }
            is UpdateDownload.Failed -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = downloadState.reason,
                        fontSize = 11.sp,
                        color = palette.danger,
                        modifier = Modifier.weight(1f),
                    )
                    DesktopButton(
                        text = if (isEn) "Release page" else "发布页",
                        onClick = onOpenReleasePage,
                        variant = DesktopButtonVariant.Secondary,
                        size = DesktopButtonSize.Compact,
                    )
                    available.installer?.let { asset ->
                        DesktopButton(
                            text = if (isEn) "Retry" else "重试",
                            onClick = { onDownload(asset) },
                            variant = DesktopButtonVariant.Primary,
                            size = DesktopButtonSize.Compact,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutLinkRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    palette: SettingsDialogPalette,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val bg by animateColorAsState(
        targetValue = when {
            pressed -> palette.pressed
            hovered -> palette.hover
            else -> Color.Transparent
        },
        animationSpec = VeneraMotion.tweenFast(),
        label = "AboutLinkBg",
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed && !VeneraMotion.reduceMotion) 0.99f else 1f,
        animationSpec = VeneraMotion.tweenFast(),
        label = "AboutLinkScale",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .background(bg)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = palette.text2,
            modifier = Modifier.size(18.dp),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = palette.text1,
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = palette.text3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
            contentDescription = null,
            tint = palette.text3,
            modifier = Modifier.size(15.dp),
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = 1024.0
    val mb = kb * 1024.0
    return when {
        bytes >= mb -> "%.1f MB".format(bytes / mb)
        bytes >= kb -> "%.0f KB".format(bytes / kb)
        else -> "$bytes B"
    }
}
