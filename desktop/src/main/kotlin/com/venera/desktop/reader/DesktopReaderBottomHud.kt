package com.venera.desktop.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.ZoomOutMap
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.app.LocalReaderChromeController
import com.venera.desktop.gallery.data.DesktopReaderMode
import com.venera.desktop.settings.DesktopI18n
import com.venera.desktop.ui.theme.VeneraType
import kotlin.math.roundToInt

/**
 * 阅读器底部悬浮进度胶囊（含页码跳转、模式切换、缩放复位与沉浸模式开关）。
 */
@Composable
internal fun ReaderBottomHud(
    visible: Boolean,
    currentPage: Int,
    totalPages: Int,
    readerMode: DesktopReaderMode,
    zoom: Float,
    onJumpToPage: (Int) -> Unit,
    onOpenJumpDialog: () -> Unit,
    onPickMode: ((DesktopReaderMode) -> Unit)?,
    onResetZoom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    val chromeController = LocalReaderChromeController.current

    AnimatedVisibility(
        visible = visible && totalPages > 0,
        enter = fadeIn(tween(HUD_ENTER_MS)) + slideInVertically(tween(HUD_ENTER_MS)) { it / 2 },
        exit = fadeOut(tween(HUD_EXIT_MS)) + slideOutVertically(tween(HUD_EXIT_MS)) { it / 2 },
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 22.dp, vertical = 18.dp)
                .widthIn(max = 840.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(HudSurface)
                .border(1.dp, HudBorder, RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onOpenJumpDialog)
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            ) {
                val displayPage = if (totalPages > 0) (currentPage + 1).coerceIn(1, totalPages) else 0
                Text(
                    text = DesktopI18n.t(
                        "第 $displayPage / $totalPages 页",
                        "Page $displayPage / $totalPages",
                    ),
                    fontSize = VeneraType.aux.size,
                    fontWeight = FontWeight.Medium,
                    color = HudTextPrimary,
                )
            }

            Slider(
                value = currentPage.coerceIn(0, (totalPages - 1).coerceAtLeast(0)).toFloat(),
                onValueChange = { onJumpToPage(it.roundToInt().coerceIn(0, (totalPages - 1).coerceAtLeast(0))) },
                valueRange = 0f..(totalPages - 1).coerceAtLeast(1).toFloat(),
                enabled = totalPages > 1,
                colors = SliderDefaults.colors(
                    thumbColor = tokens.color.primary,
                    activeTrackColor = tokens.color.primary,
                    inactiveTrackColor = Color(0x44FFFFFF),
                ),
                modifier = Modifier.weight(1f).height(22.dp),
            )

            if (onPickMode != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    HudModeChip(
                        label = DesktopI18n.t("条漫", "Strip"),
                        selected = readerMode == DesktopReaderMode.VERTICAL_CONTINUOUS,
                        onClick = {
                            if (readerMode != DesktopReaderMode.VERTICAL_CONTINUOUS) {
                                onPickMode(DesktopReaderMode.VERTICAL_CONTINUOUS)
                            }
                        },
                    )
                    HudModeChip(
                        label = DesktopI18n.t("单页", "Single"),
                        selected = readerMode == DesktopReaderMode.HORIZONTAL_LTR ||
                            readerMode == DesktopReaderMode.HORIZONTAL_RTL,
                        onClick = {
                            if (readerMode != DesktopReaderMode.HORIZONTAL_LTR &&
                                readerMode != DesktopReaderMode.HORIZONTAL_RTL
                            ) {
                                onPickMode(DesktopReaderMode.HORIZONTAL_LTR)
                            }
                        },
                    )
                    if (readerMode == DesktopReaderMode.HORIZONTAL_LTR ||
                        readerMode == DesktopReaderMode.HORIZONTAL_RTL
                    ) {
                        HudModeChip(
                            label = if (readerMode == DesktopReaderMode.HORIZONTAL_RTL) "RTL" else "LTR",
                            selected = readerMode == DesktopReaderMode.HORIZONTAL_RTL,
                            onClick = {
                                onPickMode(
                                    if (readerMode == DesktopReaderMode.HORIZONTAL_RTL) {
                                        DesktopReaderMode.HORIZONTAL_LTR
                                    } else {
                                        DesktopReaderMode.HORIZONTAL_RTL
                                    },
                                )
                            },
                        )
                    }
                    HudModeChip(
                        label = DesktopI18n.t("双页", "Double"),
                        selected = readerMode == DesktopReaderMode.DOUBLE_PAGE,
                        onClick = {
                            if (readerMode != DesktopReaderMode.DOUBLE_PAGE) {
                                onPickMode(DesktopReaderMode.DOUBLE_PAGE)
                            }
                        },
                    )
                }
            }

            if (zoom > 1.02f) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x26FFFFFF))
                        .clickable(onClick = onResetZoom)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ZoomOutMap,
                        contentDescription = DesktopI18n.t("重置缩放", "Reset zoom"),
                        tint = HudTextPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = "${(zoom * 100).roundToInt()}%",
                        fontSize = VeneraType.aux.size,
                        color = HudTextPrimary,
                    )
                }
            }

            if (chromeController != null) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(16.dp)
                        .background(HudDivider),
                )
                ReaderImmersiveToggleChip(
                    immersive = chromeController.immersive,
                    onClick = chromeController.onToggle,
                )
            }
        }
    }
}

/** 底栏全屏沉浸切换胶囊（固定双字宽，防止点击切换时滑块横向跳变）。 */
@Composable
internal fun ReaderImmersiveToggleChip(
    immersive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    val label = readerImmersiveButtonLabel(immersive)
    val contentDesc = if (immersive) {
        DesktopI18n.t("退出沉浸模式", "Exit immersive mode")
    } else {
        DesktopI18n.t("全屏沉浸播放", "Enter immersive mode")
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (immersive) tokens.color.primary.copy(alpha = 0.25f) else Color(0x1AFFFFFF))
            .border(
                width = 1.dp,
                color = if (immersive) tokens.color.primary else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = if (immersive) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
            contentDescription = contentDesc,
            tint = if (immersive) tokens.color.primary else HudTextPrimary,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = label,
            fontSize = VeneraType.aux.size,
            color = if (immersive) tokens.color.primary else HudTextPrimary,
        )
    }
}

internal fun readerImmersiveButtonLabel(immersive: Boolean): String =
    if (immersive) DesktopI18n.tr("沉浸", "Exit") else DesktopI18n.tr("全屏", "Full")
