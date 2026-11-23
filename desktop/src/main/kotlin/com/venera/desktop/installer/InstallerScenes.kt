package com.venera.desktop.installer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.desktop.ui.theme.VeneraFonts

/** 安装失败原因枚举。 */
enum class InstallErrorKind(
    val title: String,
    val desc: String,
    val code: String,
    val label: String,
) {
    DISK_FULL(
        title = "磁盘空间不足",
        desc = "目标磁盘剩余空间不足 184 MB，无法完成组件解压，请清理空间或更换目录",
        code = "ERR_DISK_FULL (0x80070070)",
        label = "磁盘不足",
    ),
    ACCESS_DENIED(
        title = "目标目录无写入权限",
        desc = "当前用户对所选系统目录缺少写入权限，建议改用默认用户目录安装",
        code = "ERR_ACCESS_DENIED (0x80070005)",
        label = "权限受限",
    ),
    FILE_LOCKED(
        title = "部分文件正被占用",
        desc = "旧版本核心文件被后台进程锁定，请先关闭相关窗口后点击重试",
        code = "ERR_SHARING_VIOLATION (0x80070020)",
        label = "文件被占用",
    ),
}

/**
 * 启动页：31 片标志 800ms 逐段生长 -> 300ms 字标放大淡入 -> 1250ms 起 300ms 收拢至主界面顶部。
 */
@Composable
fun BoxScope.SplashScene(
    introMs: Long,
    applyIntro: Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = LocalInstallerPalette.current
    val effectiveMs = if (applyIntro) introMs else InstallerTimeline.SPLASH_TOTAL_MS
    val emblemProgress = InstallerTimeline.emblemProgress(effectiveMs)
    val wordProgress = InstallerTimeline.wordmarkProgress(effectiveMs)
    val tagProgress = InstallerTimeline.taglineProgress(effectiveMs)
    val settle = InstallerTimeline.splashFadeProgress(effectiveMs)

    val badgeSize = (76f - 24f * settle).dp
    val badgeRadius = (20f - 6f * settle).dp
    val topPadding = (76f - 72f * settle).dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 12.dp),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = topPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            InstallerEmblem(
                progress = emblemProgress,
                elapsedMs = if (applyIntro) effectiveMs else null,
                cornerRadius = badgeRadius,
                modifier = Modifier.size(badgeSize),
            )
            Spacer(Modifier.height((14f - 4f * settle).dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.graphicsLayer {
                    alpha = wordProgress * (1f - 0.1f * settle)
                    val s = 0.92f + 0.08f * wordProgress
                    scaleX = s
                    scaleY = s
                },
            ) {
                Text(
                    text = InstallerCopy.Brand,
                    style = TextStyle(
                        fontFamily = VeneraFonts.family,
                        fontSize = (24f - 4f * settle).sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp,
                        color = palette.textPrimary,
                    ),
                )
                InstallerPill(InstallerCopy.VersionBadge)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = InstallerCopy.Tagline,
                modifier = Modifier.graphicsLayer {
                    alpha = tagProgress * (1f - settle)
                },
                style = TextStyle(
                    fontFamily = VeneraFonts.family,
                    fontSize = 13.sp,
                    color = palette.textSecondary,
                ),
            )
        }
    }
}

/**
 * 选项页：收拢后的水平品牌头 + 覆盖升级提示 + 摘要卡片 + 300ms 展开目录面板 + 操作行。
 *
 * @param installPath 当前安装目录。
 * @param onInstallPathChange 目录输入框改值回调。
 * @param isUpgrade 是否覆盖升级模式。
 * @param onBrowse 浏览按钮回调，由宿主弹目录框。
 * @param onStartInstall 开始安装回调。
 * @param onPathPanelHeight 目录面板实测高度（px），宿主据此同步窗口高度。
 * @param onPathPanelExpandedChange 目录面板展开/收起回调，宿主收尾时把高度归零。
 * @param modifier 外层修饰。
 */
@Composable
fun BoxScope.OptionsScene(
    installPath: String,
    onInstallPathChange: (String) -> Unit,
    isUpgrade: Boolean = false,
    onBrowse: () -> Unit,
    onStartInstall: () -> Unit = {},
    onStartInstallWithOptions: ((desktopShortcut: Boolean, startMenuShortcut: Boolean) -> Unit)? = null,
    onRequestUninstall: (() -> Unit)? = null,
    onPathPanelHeight: (Int) -> Unit = {},
    onPathPanelExpandedChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val palette = LocalInstallerPalette.current
    var expanded by remember { mutableStateOf(false) }
    var desktopShortcut by remember { mutableStateOf(true) }
    var startMenuShortcut by remember { mutableStateOf(true) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 28.dp, end = 28.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // 收拢后的水平品牌头
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                InstallerEmblem(
                    progress = 1f,
                    cornerRadius = 14.dp,
                    modifier = Modifier.size(52.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = InstallerCopy.Brand,
                            style = TextStyle(
                                fontFamily = VeneraFonts.family,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.textPrimary,
                            ),
                        )
                        InstallerPill(InstallerCopy.VersionBadge)
                    }
                    Text(
                        text = InstallerCopy.Tagline,
                        style = TextStyle(
                            fontFamily = VeneraFonts.family,
                            fontSize = 13.sp,
                            color = palette.textSecondary,
                        ),
                    )
                }
            }

            if (isUpgrade) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(InstallerSpec.InnerCardShape)
                        .background(palette.warnBg)
                        .border(1.dp, palette.warnBorder, InstallerSpec.InnerCardShape)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = InstallerCopy.UpgradeNotice,
                        modifier = Modifier.weight(1f),
                        style = TextStyle(
                            fontFamily = VeneraFonts.family,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = palette.warnText,
                        ),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "安全升级",
                        style = TextStyle(
                            fontFamily = VeneraFonts.family,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.warnText,
                        ),
                    )
                }
            }

            // 摘要卡片
            InstallerSurfaceCard {
                SummaryRow(
                    title = "目标安装目录",
                    desc = "免管理员权限安装至当前用户目录",
                    trailing = {
                        Box(
                            modifier = Modifier
                                .width(240.dp)
                                .clip(InstallerSpec.ControlShape)
                                .background(palette.surfaceSubtle)
                                .border(1.dp, palette.borderSubtle, InstallerSpec.ControlShape)
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        ) {
                            Text(
                                text = installPath.ifBlank { InstallerCopy.DefaultPathHint },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.5.sp,
                                    color = palette.textSecondary,
                                ),
                            )
                        }
                    },
                )
                SummaryRow(
                    title = "数据隔离说明",
                    desc = "收藏与阅读记录独立存放，升级或卸载不受影响",
                    trailing = { InstallerPill("独立存储") },
                )
            }

            // 300ms 目录面板展开
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    animationSpec = tween(InstallerTimeline.PATH_EXPAND_MS.toInt(), easing = FastOutSlowInEasing),
                ) + fadeIn(tween(240)),
                exit = shrinkVertically(
                    animationSpec = tween(InstallerTimeline.PATH_EXPAND_MS.toInt(), easing = FastOutSlowInEasing),
                ) + fadeOut(tween(200)),
            ) {
                InstallerSurfaceCard(
                    modifier = Modifier.onSizeChanged { onPathPanelHeight(it.height) },
                    padding = 14.dp,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "安装路径",
                            style = TextStyle(
                                fontFamily = VeneraFonts.family,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.textPrimary,
                            ),
                        )
                        Text(
                            text = "所需 184 MB · 可用 128.4 GB",
                            style = TextStyle(
                                fontFamily = VeneraFonts.family,
                                fontSize = 11.5.sp,
                                color = palette.textTertiary,
                            ),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        com.venera.desktop.ui.components.DesktopInputField(
                            value = installPath,
                            onValueChange = onInstallPathChange,
                            modifier = Modifier.weight(1f),
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = palette.textPrimary,
                            ),
                            colors = com.venera.desktop.ui.components.DesktopSearchFieldColors(
                                fieldBg = palette.surfaceElevated,
                                fieldBgFocused = palette.surfaceElevated,
                                fieldBorder = palette.borderStrong,
                                fieldBorderHover = palette.accent,
                                fieldBorderFocus = palette.accent,
                                iconTint = palette.textTertiary,
                                textColor = palette.textPrimary,
                                placeholderColor = palette.textTertiary,
                                hintColor = palette.textTertiary,
                                cursor = palette.accent,
                            ),
                        )
                        InstallerSecondaryButton(
                            text = InstallerCopy.Browse,
                            height = 34.dp,
                            onClick = onBrowse,
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        InstallerCheckbox(
                            checked = desktopShortcut,
                            label = "创建桌面快捷方式",
                            onToggle = { desktopShortcut = !desktopShortcut },
                        )
                        InstallerCheckbox(
                            checked = startMenuShortcut,
                            label = "添加到开始菜单",
                            onToggle = { startMenuShortcut = !startMenuShortcut },
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(palette.borderSubtle),
                    )
                    Text(
                        text = InstallerCopy.DataDirHint,
                        style = TextStyle(
                            fontFamily = VeneraFonts.family,
                            fontSize = 11.5.sp,
                            color = palette.textTertiary,
                        ),
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InstallerGhostButton(
                text = if (expanded) "收起目录" else InstallerCopy.CustomPath,
                onClick = {
                    expanded = !expanded
                    onPathPanelExpandedChange(expanded)
                },
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isUpgrade && onRequestUninstall != null) {
                    InstallerSecondaryButton(
                        text = "卸载原程序",
                        onClick = onRequestUninstall,
                    )
                }
                InstallerPrimaryButton(
                    text = if (isUpgrade) InstallerCopy.UpgradeNow else InstallerCopy.InstallNow,
                    onClick = {
                        val cb = onStartInstallWithOptions
                        if (cb != null) cb(desktopShortcut, startMenuShortcut) else onStartInstall()
                    },
                )
            }
        }
    }
}

@Composable
private fun SummaryRow(
    title: String,
    desc: String,
    trailing: @Composable () -> Unit,
) {
    val palette = LocalInstallerPalette.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = TextStyle(
                    fontFamily = VeneraFonts.family,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.textPrimary,
                ),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = desc,
                style = TextStyle(
                    fontFamily = VeneraFonts.family,
                    fontSize = 12.sp,
                    color = palette.textSecondary,
                ),
            )
        }
        trailing()
    }
}

@Composable
private fun InstallerCheckbox(
    checked: Boolean,
    label: String,
    onToggle: () -> Unit,
) {
    val palette = LocalInstallerPalette.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle,
            )
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            modifier = Modifier
                .size(15.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (checked) palette.accent else palette.surfaceElevated)
                .border(
                    1.dp,
                    if (checked) palette.accent else palette.borderStrong,
                    RoundedCornerShape(4.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Canvas(Modifier.size(9.dp)) {
                    drawLine(
                        color = Color.White,
                        start = Offset(size.width * 0.15f, size.height * 0.52f),
                        end = Offset(size.width * 0.42f, size.height * 0.80f),
                        strokeWidth = 1.8.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = Color.White,
                        start = Offset(size.width * 0.42f, size.height * 0.80f),
                        end = Offset(size.width * 0.86f, size.height * 0.22f),
                        strokeWidth = 1.8.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
        Text(
            text = label,
            style = TextStyle(
                fontFamily = VeneraFonts.family,
                fontSize = 12.5.sp,
                color = palette.textSecondary,
            ),
        )
    }
}

/**
 * 安装进行中：2s 纵向 ±33dp 浮动 + 6s 五幕分层玻璃矢量插画轮播 + 12s 单调进度条。
 */
@Composable
fun BoxScope.InstallScene(
    pageElapsed: Long,
    clockMs: Long,
    modifier: Modifier = Modifier,
    progressOverride: Float? = null,
    statusTextOverride: String? = null,
) {
    val palette = LocalInstallerPalette.current
    var manualSlideOverride by remember { mutableStateOf<Int?>(null) }

    val progress = (progressOverride ?: InstallerTimeline.installProgress(pageElapsed)).coerceIn(0f, 1f)
    val percent = InstallerTimeline.percentLabel(progress)
    val autoScene = InstallerTimeline.sceneIndex(pageElapsed)
    val activeScene = manualSlideOverride ?: autoScene
    val sceneAlpha = if (manualSlideOverride != null) 1f else InstallerTimeline.sceneAlpha(pageElapsed)
    val bobOffset = InstallerTimeline.bobOffsetDp(clockMs)
    val item = InstallerCopy.installScenes[activeScene.coerceIn(0, InstallerCopy.installScenes.lastIndex)]

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 28.dp, end = 28.dp, top = 6.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // 中央浮动与轮播舞台（含上下 ±33dp 净空）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(274.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .offset(y = bobOffset.dp)
                    .graphicsLayer { alpha = sceneAlpha },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                InstallIllustration(
                    sceneIndex = activeScene,
                    modifier = Modifier.size(196.dp, 114.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = item.title,
                    style = TextStyle(
                        fontFamily = VeneraFonts.family,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.textPrimary,
                    ),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = item.subtitle,
                    textAlign = TextAlign.Center,
                    style = TextStyle(
                        fontFamily = VeneraFonts.family,
                        fontSize = 13.sp,
                        color = palette.textSecondary,
                    ),
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // 五幕可点击指示点
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(InstallerTimeline.SCENE_COUNT) { idx ->
                    val isSelected = idx == activeScene
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .width(if (isSelected) 18.dp else 6.dp)
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) palette.accent else palette.borderStrong)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { manualSlideOverride = idx },
                            ),
                    )
                }
            }

            // 12s 单调进度卡片
            InstallerSurfaceCard(padding = 12.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = statusTextOverride ?: InstallerCopy.progressStageText(percent),
                        style = TextStyle(
                            fontFamily = VeneraFonts.family,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = palette.textPrimary,
                        ),
                    )
                    Text(
                        text = "$percent%",
                        style = TextStyle(
                            fontFamily = VeneraFonts.family,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.accent,
                        ),
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(palette.surfaceSubtle),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress)
                            .clip(CircleShape)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(palette.accent, Color(0xFF38BDF8)),
                                ),
                            ),
                    )
                }
            }
        }
    }
}

/**
 * 安装完成页。
 */
@Composable
fun BoxScope.CompleteScene(
    clockMs: Long,
    installPath: String = InstallerCopy.DefaultPathHint,
    onExperience: () -> Unit = {},
    onExperienceWithLaunch: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val palette = LocalInstallerPalette.current
    var launchOnFinish by remember { mutableStateOf(true) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 28.dp, end = 28.dp, top = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(palette.successSoft)
                    .border(1.dp, palette.success.copy(alpha = 0.30f), RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(28.dp)) {
                    val p = Path().apply {
                        moveTo(size.width * 0.18f, size.height * 0.52f)
                        lineTo(size.width * 0.42f, size.height * 0.74f)
                        lineTo(size.width * 0.84f, size.height * 0.28f)
                    }
                    drawPath(
                        path = p,
                        color = palette.success,
                        style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }
            }
            Text(
                text = InstallerCopy.CompleteTitle,
                style = TextStyle(
                    fontFamily = VeneraFonts.family,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary,
                ),
            )
            Text(
                text = InstallerCopy.CompleteSubtitle,
                style = TextStyle(
                    fontFamily = VeneraFonts.family,
                    fontSize = 13.sp,
                    color = palette.textSecondary,
                ),
            )

            Spacer(Modifier.height(4.dp))
            InstallerSurfaceCard {
                SummaryKeyValRow("安装目录", installPath.ifBlank { InstallerCopy.DefaultPathHint })
                SummaryKeyValRow("用户数据", "%LOCALAPPDATA%\\venera（已独立保留）")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InstallerCheckbox(
                checked = launchOnFinish,
                label = InstallerCopy.LaunchOnFinish,
                onToggle = { launchOnFinish = !launchOnFinish },
            )
            InstallerPrimaryButton(
                text = InstallerCopy.StartUsing,
                onClick = {
                    val cb = onExperienceWithLaunch
                    if (cb != null) cb(launchOnFinish) else onExperience()
                },
            )
        }
    }
}

/** 扩展态 1：旧版 MSI 迁移确认。 */
@Composable
fun BoxScope.MigrationScene(
    onCancel: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalInstallerPalette.current
    ExtendedStageShell(
        badgeBg = palette.warnBg,
        badgeBorder = palette.warnBorder,
        badgeColor = palette.warnText,
        title = InstallerCopy.MigrationTitle,
        desc = InstallerCopy.MigrationDesc,
        modifier = modifier,
        cardContent = {
            SummaryKeyValRow("旧版来源", "venera-desktop (Windows Installer)")
            SummaryKeyValRow("数据迁移", "%LOCALAPPDATA%\\venera（自动保留，不受影响）")
        },
        actions = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InstallerSecondaryButton("取消", onClick = onCancel)
                Spacer(Modifier.width(10.dp))
                InstallerPrimaryButton(InstallerCopy.MigrationConfirm, onClick = onContinue)
            }
        },
    )
}

/** 扩展态 2：进程占用冲突提示。 */
@Composable
fun BoxScope.ConflictScene(
    onCancel: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalInstallerPalette.current
    ExtendedStageShell(
        badgeBg = palette.accentSoft,
        badgeBorder = palette.accent.copy(alpha = 0.25f),
        badgeColor = palette.accent,
        title = InstallerCopy.ConflictTitle,
        desc = InstallerCopy.ConflictDesc,
        modifier = modifier,
        cardContent = {
            SummaryKeyValRow("占用进程", "venera-desktop.exe")
            SummaryKeyValRow("处理方式", "安全退出主窗口后继续覆写文件")
        },
        actions = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InstallerSecondaryButton("暂不安装", onClick = onCancel)
                Spacer(Modifier.width(10.dp))
                InstallerPrimaryButton(InstallerCopy.ConflictConfirm, onClick = onContinue)
            }
        },
    )
}

/** 扩展态 3：安装失败 / 异常回退（支持三态原因）。 */
@Composable
fun BoxScope.ErrorScene(
    errorKind: InstallErrorKind,
    onChangePath: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalInstallerPalette.current
    ExtendedStageShell(
        badgeBg = palette.dangerSoft,
        badgeBorder = palette.dangerBorder,
        badgeColor = palette.danger,
        title = errorKind.title,
        desc = errorKind.desc,
        modifier = modifier,
        cardContent = {
            SummaryKeyValRow("错误代码", errorKind.code)
            SummaryKeyValRow("数据安全", "已自动回滚临时写入，原有数据未改动")
        },
        actions = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InstallerGhostButton("更换安装目录", onClick = onChangePath)
                InstallerPrimaryButton("重试安装", onClick = onRetry)
            }
        },
    )
}

/** 扩展态 4：修复 / 卸载维护态。 */
@Composable
fun BoxScope.UninstallScene(
    onBack: () -> Unit,
    onRepair: () -> Unit,
    onUninstall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalInstallerPalette.current
    var removeSelected by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 28.dp, end = 28.dp, top = 18.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(palette.accentSoft)
                    .border(1.dp, palette.accent.copy(alpha = 0.25f), RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(24.dp)) {
                    drawCircle(
                        color = palette.accent,
                        radius = size.minDimension * 0.30f,
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            }
            Text(
                text = InstallerCopy.MaintTitle,
                style = TextStyle(
                    fontFamily = VeneraFonts.family,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary,
                ),
            )
            Text(
                text = InstallerCopy.MaintDesc,
                style = TextStyle(
                    fontFamily = VeneraFonts.family,
                    fontSize = 13.sp,
                    color = palette.textSecondary,
                ),
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MaintOptionCard(
                    title = "修复程序文件",
                    desc = "重新校验并补全运行组件，保留全部设置与收藏",
                    selected = !removeSelected,
                    danger = false,
                    modifier = Modifier.weight(1f),
                    onClick = { removeSelected = false },
                )
                MaintOptionCard(
                    title = "卸载 VComic",
                    desc = "移除程序与快捷方式，保留 %LOCALAPPDATA%\\venera 数据",
                    selected = removeSelected,
                    danger = true,
                    modifier = Modifier.weight(1f),
                    onClick = { removeSelected = true },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InstallerGhostButton("返回安装页", onClick = onBack)
            InstallerPrimaryButton(
                text = if (removeSelected) "确认卸载" else "开始修复",
                danger = removeSelected,
                onClick = if (removeSelected) onUninstall else onRepair,
            )
        }
    }
}

/** 中途取消确认内嵌毛玻璃遮罩弹层。 */
@Composable
fun BoxScope.CancelConfirmOverlay(
    visible: Boolean,
    onKeepInstall: () -> Unit,
    onConfirmCancel: () -> Unit,
) {
    val palette = LocalInstallerPalette.current
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(220),
        label = "cancel-overlay-alpha",
    )
    if (!visible && alpha <= 0.01f) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha }
            .background(palette.scrim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(380.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(palette.surfaceElevated)
                .border(1.dp, palette.borderStrong, RoundedCornerShape(16.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = InstallerCopy.CancelModalTitle,
                style = TextStyle(
                    fontFamily = VeneraFonts.family,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary,
                ),
            )
            Text(
                text = InstallerCopy.CancelModalDesc,
                style = TextStyle(
                    fontFamily = VeneraFonts.family,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = palette.textSecondary,
                ),
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InstallerSecondaryButton(InstallerCopy.CancelModalKeep, height = 34.dp, onClick = onKeepInstall)
                Spacer(Modifier.width(8.dp))
                InstallerPrimaryButton(InstallerCopy.CancelModalAbort, danger = true, onClick = onConfirmCancel)
            }
        }
    }
}

@Composable
private fun MaintOptionCard(
    title: String,
    desc: String,
    selected: Boolean,
    danger: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val palette = LocalInstallerPalette.current
    val bg = when {
        selected && danger -> palette.dangerSoft
        selected -> palette.accentSoft
        else -> palette.surface
    }
    val border = when {
        selected && danger -> palette.danger
        selected -> palette.accent
        else -> palette.borderSubtle
    }
    Column(
        modifier = modifier
            .clip(InstallerSpec.InnerCardShape)
            .background(bg)
            .border(1.5.dp, border, InstallerSpec.InnerCardShape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            style = TextStyle(
                fontFamily = VeneraFonts.family,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = palette.textPrimary,
            ),
        )
        Text(
            text = desc,
            style = TextStyle(
                fontFamily = VeneraFonts.family,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = palette.textSecondary,
            ),
        )
    }
}

@Composable
private fun ExtendedStageShell(
    badgeBg: Color,
    badgeBorder: Color,
    badgeColor: Color,
    title: String,
    desc: String,
    modifier: Modifier = Modifier,
    cardContent: @Composable () -> Unit,
    actions: @Composable () -> Unit,
) {
    val palette = LocalInstallerPalette.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 28.dp, end = 28.dp, top = 18.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(badgeBg)
                    .border(1.dp, badgeBorder, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(24.dp)) {
                    drawCircle(
                        color = badgeColor,
                        radius = size.minDimension * 0.36f,
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            }
            Text(
                text = title,
                style = TextStyle(
                    fontFamily = VeneraFonts.family,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary,
                ),
            )
            Text(
                text = desc,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(440.dp),
                style = TextStyle(
                    fontFamily = VeneraFonts.family,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = palette.textSecondary,
                ),
            )
            Spacer(Modifier.height(4.dp))
            InstallerSurfaceCard {
                cardContent()
            }
        }
        actions()
    }
}

@Composable
private fun SummaryKeyValRow(label: String, value: String) {
    val palette = LocalInstallerPalette.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontFamily = VeneraFonts.family,
                fontSize = 12.5.sp,
                color = palette.textSecondary,
            ),
        )
        Text(
            text = value,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = palette.textPrimary,
            ),
        )
    }
}

/** 五幕介绍对应的分层玻璃矢量插画。 */
@Composable
private fun InstallIllustration(
    sceneIndex: Int,
    modifier: Modifier = Modifier,
) {
    val palette = LocalInstallerPalette.current
    Canvas(modifier) {
        val sx = size.width / 196f
        val sy = size.height / 114f
        val rCard = CornerRadius(12f * sx, 12f * sy)

        when (sceneIndex % InstallerTimeline.SCENE_COUNT) {
            0 -> {
                // 幕 1：多源聚合阅读
                drawRoundRect(palette.surfaceElevated, Offset(26f * sx, 22f * sy), Size(60f * sx, 72f * sy), rCard)
                drawRoundRect(palette.borderStrong, Offset(26f * sx, 22f * sy), Size(60f * sx, 72f * sy), rCard, style = Stroke(1.2.dp.toPx()))
                drawRoundRect(palette.surfaceElevated, Offset(110f * sx, 22f * sy), Size(60f * sx, 72f * sy), rCard)
                drawRoundRect(palette.borderStrong, Offset(110f * sx, 22f * sy), Size(60f * sx, 72f * sy), rCard, style = Stroke(1.2.dp.toPx()))

                drawRoundRect(palette.windowBg, Offset(64f * sx, 10f * sy), Size(68f * sx, 92f * sy), CornerRadius(13f * sx, 13f * sy))
                drawRoundRect(palette.accent, Offset(64f * sx, 10f * sy), Size(68f * sx, 92f * sy), CornerRadius(13f * sx, 13f * sy), style = Stroke(1.6.dp.toPx()))
                drawRoundRect(palette.accentSoft, Offset(74f * sx, 22f * sy), Size(48f * sx, 36f * sy), CornerRadius(8f * sx, 8f * sy))
                val check = Path().apply {
                    moveTo(88f * sx, 40f * sy)
                    lineTo(95f * sx, 47f * sy)
                    lineTo(109f * sx, 33f * sy)
                }
                drawPath(check, palette.accent, style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            1 -> {
                // 幕 2：双页沉浸视图
                drawRoundRect(palette.surfaceElevated, Offset(28f * sx, 12f * sy), Size(140f * sx, 90f * sy), rCard)
                drawRoundRect(palette.borderStrong, Offset(28f * sx, 12f * sy), Size(140f * sx, 90f * sy), rCard, style = Stroke(1.2.dp.toPx()))
                drawRoundRect(palette.surfaceSubtle, Offset(38f * sx, 22f * sy), Size(56f * sx, 70f * sy), CornerRadius(8f * sx, 8f * sy))
                drawRoundRect(palette.accentSoft, Offset(102f * sx, 22f * sy), Size(56f * sx, 70f * sy), CornerRadius(8f * sx, 8f * sy))
                drawRoundRect(palette.accent.copy(alpha = 0.25f), Offset(110f * sx, 32f * sy), Size(40f * sx, 32f * sy), CornerRadius(6f * sx, 6f * sy))
            }
            2 -> {
                // 幕 3：标签筛选检索
                drawRoundRect(palette.surfaceElevated, Offset(34f * sx, 16f * sy), Size(128f * sx, 82f * sy), rCard)
                drawRoundRect(palette.borderStrong, Offset(34f * sx, 16f * sy), Size(128f * sx, 82f * sy), rCard, style = Stroke(1.2.dp.toPx()))
                drawRoundRect(palette.surfaceSubtle, Offset(46f * sx, 28f * sy), Size(104f * sx, 22f * sy), CornerRadius(7f * sx, 7f * sy))
                drawCircle(palette.accent, 4.5f * sx, Offset(58f * sx, 39f * sy), style = Stroke(1.6.dp.toPx()))
                drawRoundRect(palette.accentSoft, Offset(46f * sx, 58f * sy), Size(38f * sx, 16f * sy), CornerRadius(8f * sx, 8f * sy))
                drawRoundRect(palette.surfaceSubtle, Offset(90f * sx, 58f * sy), Size(42f * sx, 16f * sy), CornerRadius(8f * sx, 8f * sy))
            }
            3 -> {
                // 幕 4：本地离线缓存
                drawRoundRect(palette.surfaceElevated, Offset(42f * sx, 16f * sy), Size(112f * sx, 82f * sy), rCard)
                drawRoundRect(palette.borderStrong, Offset(42f * sx, 16f * sy), Size(112f * sx, 82f * sy), rCard, style = Stroke(1.2.dp.toPx()))
                drawRoundRect(palette.accentSoft, Offset(54f * sx, 28f * sy), Size(88f * sx, 44f * sy), CornerRadius(9f * sx, 9f * sy))
                drawLine(palette.accent, Offset(98f * sx, 37f * sy), Offset(98f * sx, 57f * sy), 2.2.dp.toPx(), StrokeCap.Round)
                drawLine(palette.accent, Offset(98f * sx, 57f * sy), Offset(90f * sx, 49f * sy), 2.2.dp.toPx(), StrokeCap.Round)
                drawLine(palette.accent, Offset(98f * sx, 57f * sy), Offset(106f * sx, 49f * sy), 2.2.dp.toPx(), StrokeCap.Round)
                drawLine(palette.accent, Offset(84f * sx, 63f * sy), Offset(112f * sx, 63f * sy), 2.dp.toPx(), StrokeCap.Round)
            }
            else -> {
                // 幕 5：本地数据独立
                drawRoundRect(palette.surfaceElevated, Offset(46f * sx, 18f * sy), Size(104f * sx, 78f * sy), rCard)
                drawRoundRect(palette.borderStrong, Offset(46f * sx, 18f * sy), Size(104f * sx, 78f * sy), rCard, style = Stroke(1.2.dp.toPx()))
                val shield = Path().apply {
                    moveTo(98f * sx, 31f * sy)
                    lineTo(124f * sx, 41f * sy)
                    lineTo(124f * sx, 59f * sy)
                    cubicTo(124f * sx, 72.5f * sy, 112.8f * sx, 82.8f * sy, 98f * sx, 86f * sy)
                    cubicTo(83.2f * sx, 82.8f * sy, 72f * sx, 72.5f * sy, 72f * sx, 59f * sy)
                    lineTo(72f * sx, 41f * sy)
                    close()
                }
                drawPath(shield, palette.accentSoft)
                drawPath(shield, palette.accent, style = Stroke(1.8.dp.toPx(), join = StrokeJoin.Round))
                val check = Path().apply {
                    moveTo(89f * sx, 58f * sy)
                    lineTo(95f * sx, 64f * sy)
                    lineTo(108f * sx, 51f * sy)
                }
                drawPath(check, palette.accent, style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}
