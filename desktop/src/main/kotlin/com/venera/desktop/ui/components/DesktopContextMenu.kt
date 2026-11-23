package com.venera.desktop.ui.components

import com.venera.desktop.ui.theme.VeneraType
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import com.venera.desktop.gallery.ui.LocalChromePalette

/**
 * 右键菜单 Mac 化玻璃化（稿 `docs/designs/macos-style-2026-11-07/context-menu-mac-2026-11-08/`）。
 *
 * 全项目唯一的右键菜单件：容器 r9 玻璃底（elevated 92%）+ 0.5dp 发丝描边（不画 shadow，视觉铁律）、
 * 行 26dp / r5 内缩胶囊高亮、图标 material 矢量 14dp 单色随态换色、悬停 accent 实底白字（mac 原生行为）、
 * 危险项 danger 红隔离。色板单源 [LocalChromePalette]，深浅随窗根换档。
 * 右键监听与边缘避让保持版不动。
 */
sealed interface DesktopMenuEntry {
    data class Item(
        val label: String,
        /** material 矢量图标（`Icons.Outlined.*`），14dp 单色渲染；null = 无图标。 */
        val icon: ImageVector? = null,
        val shortcut: String? = null,
        val isDanger: Boolean = false,
        /** 无网址 / 无进度 / 正在忙时置灰，不摆无实现的项（点了没反应比不摆更坏）。 */
        val enabled: Boolean = true,
        val onClick: () -> Unit,
    ) : DesktopMenuEntry

    object Divider : DesktopMenuEntry
}

/**
 * 包装目标区域，监听鼠标右键事件并弹出 [DesktopContextMenuPopup]。
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun DesktopContextMenuArea(
    items: () -> List<DesktopMenuEntry>,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var clickOffset by remember { mutableStateOf(IntOffset.Zero) }

    Box(
        modifier = modifier.pointerInput(items) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val isRightClick = event.buttons.isSecondaryPressed || event.button == PointerButton.Secondary
                    if (isRightClick && (event.type == PointerEventType.Press || event.type == PointerEventType.Release)) {
                        val change = event.changes.firstOrNull() ?: continue
                        clickOffset = IntOffset(change.position.x.toInt(), change.position.y.toInt())
                        expanded = true
                        change.consume()
                    }
                }
            }
        },
    ) {
        content()

        if (expanded) {
            val menuItems = remember(items) { items() }
            if (menuItems.isNotEmpty()) {
                DesktopContextMenuPopup(
                    items = menuItems,
                    offset = clickOffset,
                    onDismissRequest = { expanded = false },
                )
            }
        }
    }
}

/**
 * 统一右键浮动菜单浮层，具备智能边缘避让（防止超出屏幕右侧或下边缘）。
 */
@Composable
fun DesktopContextMenuPopup(
    items: List<DesktopMenuEntry>,
    offset: IntOffset,
    onDismissRequest: () -> Unit,
) {
    val positionProvider = remember(offset) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                var x = anchorBounds.left + offset.x
                var y = anchorBounds.top + offset.y

                // 右侧避让
                if (x + popupContentSize.width > windowSize.width) {
                    x = (windowSize.width - popupContentSize.width - 8).coerceAtLeast(0)
                }
                // 底部避让
                if (y + popupContentSize.height > windowSize.height) {
                    y = (windowSize.height - popupContentSize.height - 8).coerceAtLeast(0)
                }
                return IntOffset(x, y)
            }
        }
    }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismissRequest,
        properties = androidx.compose.ui.window.PopupProperties(focusable = true),
    ) {
        DesktopContextMenuContent(
            items = items,
            onDismissRequest = onDismissRequest,
        )
    }
}

/** 容器圆角（稿 `.menu` border-radius 9）。 */
private val MenuRContainer = RoundedCornerShape(9.dp)

/** 菜单项高亮圆角（同心圆法则：9 − 4 = 5）。 */
private val MenuRItem = RoundedCornerShape(5.dp)

/**
 * 菜单主面板（稿 `.menu`）：定宽 220dp，r9 玻璃底，0.5dp 发丝，内距 4dp。
 *
 * 为什么定宽不用 min-width：Popup 内容按窗口约束测量，容器放宽约束 + 行 `fillMaxWidth`
 * 会把菜单撑满整窗（真窗实锤）；mac 菜单的"随内容收窄"要 IntrinsicSize
 * 配 weight 才成立，不值得，定宽 + label 省略号即可。长条目（屏蔽画师: xxx）超宽走省略。
 * 不画 shadow —— Skia drop shadow 低 elevation 渲染成硬边灰框（视觉铁律），
 * 浮层分离感由 elevated 半透底 + 发丝描边承担。
 */
@Composable
fun DesktopContextMenuContent(
    items: List<DesktopMenuEntry>,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalChromePalette.current

    Column(
        modifier = modifier
            .width(220.dp)
            .clip(MenuRContainer)
            .background(palette.elevated)
            .border(0.5.dp, palette.hairline, MenuRContainer)
            .padding(4.dp),
    ) {
        items.forEach { entry ->
            when (entry) {
                is DesktopMenuEntry.Divider -> {
                    // 分隔线：0.5dp 发丝，左右内缩 9dp（与 MacMenuDivider 同一几何）。
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                            .height(0.5.dp)
                            .background(palette.hairline),
                    )
                }
                is DesktopMenuEntry.Item -> {
                    DesktopContextMenuItemView(
                        item = entry,
                        onClick = {
                            if (entry.enabled) {
                                onDismissRequest()
                                entry.onClick()
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DesktopContextMenuItemView(
    item: DesktopMenuEntry.Item,
    onClick: () -> Unit,
) {
    val palette = LocalChromePalette.current
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    // 三态色面（稿 component-spec）：常态 / 悬停 accent 实底白字 / 危险项 danger 隔离；置灰 ink3 50%。
    val bg = when {
        !item.enabled -> Color.Transparent
        isHovered && item.isDanger -> palette.danger.copy(alpha = if (palette.dark) 0.22f else 0.12f)
        isHovered -> palette.accent
        else -> Color.Transparent
    }
    val contentColor = when {
        !item.enabled -> palette.ink3.copy(alpha = 0.5f)
        item.isDanger -> palette.danger
        isHovered -> Color.White
        else -> palette.ink1
    }
    val iconColor = when {
        !item.enabled -> palette.ink3.copy(alpha = 0.5f)
        item.isDanger -> palette.danger
        isHovered -> Color.White
        else -> palette.ink2
    }
    val shortcutColor = when {
        !item.enabled -> palette.ink3.copy(alpha = 0.5f)
        item.isDanger -> palette.danger.copy(alpha = 0.7f)
        isHovered -> Color.White.copy(alpha = 0.8f)
        else -> palette.ink3
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .height(26.dp)
            .clip(MenuRItem)
            .background(bg)
            .then(if (item.enabled) Modifier.hoverable(interactionSource) else Modifier)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = item.enabled,
                onClick = onClick,
            )
            .padding(horizontal = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (item.icon != null) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            text = item.label,
            fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
            fontWeight = VeneraType.body.weight,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (!item.shortcut.isNullOrBlank()) {
            Text(
                text = item.shortcut,
                fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                color = shortcutColor,
                maxLines = 1,
            )
        }
    }
}
