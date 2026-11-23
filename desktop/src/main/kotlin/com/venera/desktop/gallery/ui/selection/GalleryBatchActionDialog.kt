package com.venera.desktop.gallery.ui.selection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.rounded.Check
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.components.venera.VeneraCoverMask
import com.venera.compose.gallery.domain.PixivImageQuality
import com.venera.desktop.gallery.ui.DesktopWallCardModel
import com.venera.desktop.gallery.ui.GalleryTokens
import com.venera.desktop.gallery.ui.LocalChromePalette
import com.venera.desktop.ui.DesktopEffects
import com.venera.desktop.ui.DesktopGlobalNavAction
import com.venera.desktop.ui.DesktopPushButton
import com.venera.desktop.ui.RegisterDesktopShortcutHandler
import com.venera.desktop.ui.matchGlobalNavShortcut
import com.venera.desktop.ui.vShadow
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur

/**
 * 画廊批量管理弹窗：已选缩略图排 + 三档页签（保存 / 收藏 / 链接导出）。
 *
 * 几何照 `design-tokens.md:26-30`：宽 560dp、最大高 640dp、外框 18dp、内衬 24dp、
 * 缩略条高 76dp。动作只做真有的三件事；没有"按了没反应"的开关。
 */
@Composable
internal fun GalleryBatchActionDialog(
    cards: List<DesktopWallCardModel>,
    initialTab: Int = 0,
    onDismiss: () -> Unit,
    onRemoveCard: (String) -> Unit,
    onConfirmDownload: (PixivImageQuality, Boolean, Boolean) -> Unit,
    onConfirmFavorite: () -> Unit,
    onCopySelectedUrls: (DesktopGalleryLinkKind) -> Unit,
) {
    if (cards.isEmpty()) return
    var tab by remember { mutableStateOf(initialTab.coerceIn(0, 2)) }
    var quality by remember { mutableStateOf(PixivImageQuality.ORIGINAL) }
    var skipExisting by remember { mutableStateOf(true) }
    var byAuthor by remember { mutableStateOf(false) }
    var linkKind by remember { mutableStateOf(DesktopGalleryLinkKind.SITE) }
    GalleryGlassModalOverlay(
        onDismiss = onDismiss,
        cardModifier = Modifier
            .width(560.dp)
            .heightIn(max = 640.dp)
            .padding(24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "批量操作",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = GalleryTokens.Color.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "已选 ${cards.size} 项",
                    fontSize = 12.sp,
                    color = GalleryTokens.Color.TextSecondary,
                )
            }
            Spacer(Modifier.height(12.dp))

            // 顶部缩略图排（悬停浮出移除钮）。
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().height(76.dp),
            ) {
                items(cards, key = { it.uid.ifBlank { it.url } }) { card ->
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(GalleryTokens.Color.SurfaceLevel2),
                    ) {
                        VeneraCover(
                            url = card.url,
                            contentDescription = card.title,
                            mask = if (card.masked) VeneraCoverMask.Masked else VeneraCoverMask.Visible,
                            preserveAspectRatio = false,
                            modifier = Modifier.fillMaxHeight().fillMaxWidth(),
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.62f))
                                .pointerHoverIcon(PointerIcon.Hand)
                                .clickable { onRemoveCard(card.uid) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "移出这一项",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("批量保存", "批量收藏", "链接导出").forEachIndexed { index, label ->
                    TabPill(label = label, selected = tab == index, onClick = { tab = index })
                }
            }
            Spacer(Modifier.height(16.dp))

            Column(modifier = Modifier.weight(1f, fill = false)) {
                when (tab) {
                    0 -> {
                        Text("画质", fontSize = 12.sp, color = GalleryTokens.Color.TextSecondary)
                        Spacer(Modifier.height(6.dp))
                        listOf(
                            PixivImageQuality.ORIGINAL,
                            PixivImageQuality.LARGE,
                            PixivImageQuality.MEDIUM,
                        ).forEach { option ->
                            ChoiceRow(
                                label = option.dockLabel,
                                selected = quality == option,
                                onClick = { quality = option },
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        CheckRow(
                            label = "跳过已存在的图片",
                            checked = skipExisting,
                            onClick = { skipExisting = !skipExisting },
                        )
                        CheckRow(
                            label = "按作者建子文件夹",
                            checked = byAuthor,
                            onClick = { byAuthor = !byAuthor },
                        )
                    }
                    1 -> {
                        Text(
                            text = "加入图库收藏。已收藏的作品会自动跳过。",
                            fontSize = 12.sp,
                            color = GalleryTokens.Color.TextSecondary,
                        )
                    }
                    else -> {
                        Text("复制类型", fontSize = 12.sp, color = GalleryTokens.Color.TextSecondary)
                        Spacer(Modifier.height(6.dp))
                        DesktopGalleryLinkKind.entries.forEach { option ->
                            ChoiceRow(
                                label = option.label,
                                selected = linkKind == option,
                                onClick = { linkKind = option },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                DesktopPushButton(text = "取消", onClick = onDismiss)
                when (tab) {
                    0 -> DesktopPushButton(
                        text = "保存 ${cards.size} 项",
                        primary = true,
                        onClick = { onConfirmDownload(quality, skipExisting, byAuthor) },
                    )
                    1 -> DesktopPushButton(
                        text = "收藏 ${cards.size} 项",
                        primary = true,
                        onClick = onConfirmFavorite,
                    )
                    else -> DesktopPushButton(
                        text = "复制 ${cards.size} 项",
                        primary = true,
                        onClick = { onCopySelectedUrls(linkKind) },
                    )
                }
            }
    }
}

@Composable
private fun TabPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) GalleryTokens.Color.AccentPrimary else GalleryTokens.Color.AccentPrimary.copy(alpha = 0.10f)
    val fg = if (selected) Color.White else GalleryTokens.Color.AccentPrimary
    Text(
        text = label,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = fg,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .border(
                    1.5.dp,
                    if (selected) GalleryTokens.Color.AccentPrimary else GalleryTokens.Color.OutlineHover,
                    CircleShape,
                )
                .padding(3.dp),
        ) {
            if (selected) {
                Box(Modifier.fillMaxWidth().fillMaxHeight().clip(CircleShape).background(GalleryTokens.Color.AccentPrimary))
            }
        }
        Text(label, fontSize = 12.sp, color = GalleryTokens.Color.TextPrimary)
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    if (checked) GalleryTokens.Color.AccentPrimary
                    else GalleryTokens.Color.AccentPrimary.copy(alpha = 0.08f),
                )
                .border(
                    1.5.dp,
                    if (checked) GalleryTokens.Color.AccentPrimary else GalleryTokens.Color.OutlineHover,
                    RoundedCornerShape(4.dp),
                ),
        ) {
            if (checked) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(11.dp),
                )
            }
        }
        Text(label, fontSize = 12.sp, color = GalleryTokens.Color.TextPrimary)
    }
}

/**
 * 数量超门槛时的二次确认（>20 项下载 / >10 条链接）。
 * 文案只说事实与后果，不解释设计。
 */
@Composable
internal fun DesktopBatchConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    GalleryGlassModalOverlay(
        onDismiss = onDismiss,
        cardModifier = Modifier
            .widthIn(min = 360.dp, max = 440.dp)
            .padding(20.dp),
    ) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = GalleryTokens.Color.TextPrimary)
        Spacer(Modifier.height(8.dp))
        Text(message, fontSize = 12.sp, color = GalleryTokens.Color.TextSecondary)
        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            DesktopPushButton(text = "取消", onClick = onDismiss)
            DesktopPushButton(text = confirmText, primary = true, onClick = onConfirm)
        }
    }
}

/**
 * 宿主内居中毛玻璃浮层容器：替代独立 Window Dialog，才能直接采样主墙 Haze 源并渲染模糊背景。
 * 点击半暗遮罩或按 Esc 关闭，弹窗卡本体拦截点击避免误触。
 */
@Composable
private fun GalleryGlassModalOverlay(
    onDismiss: () -> Unit,
    cardModifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    RegisterDesktopShortcutHandler(priority = 300) { signal ->
        if (matchGlobalNavShortcut(signal) == DesktopGlobalNavAction.ESCAPE) {
            onDismiss()
            true
        } else {
            false
        }
    }
    val palette = LocalChromePalette.current
    val hazeState = LocalSelectionDockHazeState.current
    val blurEnabled = hazeState != null && !DesktopEffects.reduce
    val shape = RoundedCornerShape(16.dp)
    val surface = if (blurEnabled) {
        if (palette.dark) palette.raised.copy(alpha = 0.62f) else palette.card.copy(alpha = 0.78f)
    } else {
        GalleryTokens.Color.SurfaceLevel1
    }
    val border = if (blurEnabled) {
        if (palette.dark) Color.White.copy(alpha = 0.16f) else palette.frame
    } else {
        GalleryTokens.Color.OutlineHairline
    }
    val blurModifier = if (blurEnabled) {
        val style = HazeBlurStyle {
            blurEnabled(true)
            blurRadius(32.dp)
            noiseFactor(0.04f)
            colorEffects(listOf(HazeColorEffect.tint(palette.glassHazeTint)))
        }
        Modifier.hazeBlur(HazeInput.Sources(hazeState), style)
    } else {
        Modifier
    }

    val scrimInteraction = remember { MutableInteractionSource() }
    val cardInteraction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(20f)
            .background(Color.Black.copy(alpha = 0.34f))
            .clickable(
                interactionSource = scrimInteraction,
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .vShadow(24.dp, shape)
                .clip(shape)
                .then(blurModifier)
                .background(surface)
                .border(1.dp, border, shape)
                .clickable(
                    interactionSource = cardInteraction,
                    indication = null,
                    onClick = {},
                )
                .then(cardModifier),
            content = content,
        )
    }
}

/** 批量执行的进度条（下载进行中时贴在坞上方）。 */
@Composable
internal fun DesktopBatchProgressLine(
    progress: DesktopGalleryBatchProgress,
    modifier: Modifier = Modifier,
) {
    val text = "正在保存 ${progress.done}/${progress.total} · 成功 ${progress.saved}"
    Text(
        text = text,
        fontSize = 11.sp,
        color = GalleryTokens.Color.TextSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}
