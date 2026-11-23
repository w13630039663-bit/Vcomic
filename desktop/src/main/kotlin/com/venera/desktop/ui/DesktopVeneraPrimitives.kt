package com.venera.desktop.ui

import com.venera.desktop.ui.theme.VeneraType
import com.venera.desktop.gallery.ui.GalleryTokens
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composeunstyled.AnchorAlignment
import com.composeunstyled.AnchorSide
import com.composeunstyled.DisclosedContent
import com.composeunstyled.DisclosureButton
import com.composeunstyled.Modal
import com.composeunstyled.ModalHost
import com.composeunstyled.Scrim
import com.composeunstyled.Tab
import com.composeunstyled.TabList
import com.composeunstyled.Track
import com.composeunstyled.UnstyledCheckbox
import com.composeunstyled.UnstyledDisclosure
import com.composeunstyled.UnstyledProgress
import com.composeunstyled.UnstyledSwitch
import com.composeunstyled.UnstyledTabGroup
import com.composeunstyled.UnstyledTooltip
import com.composeunstyled.UnstyledTriStateCheckbox
import com.composeunstyled.rememberModalState
import com.venera.compose.ui.tokens.VeneraTokens

/**
 * 包装 Composables UI (Compose Unstyled) 无样式原语的 Venera 皮肤层。
 *
 * 遵循.1：
 *  - 无样式原语（headless）处理键盘、焦点、无障碍与状态机；
 *  - 视觉表现严格穿 Venera 主题/设计稿双层皮肤，屏上不直接引用外部库裸 API。
 */

/**
 * 复选框（薄壳，控件）：几何与色档全在 [DesktopCheckbox]。
 */
@Composable
fun VeneraCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    DesktopCheckbox(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
    )
}



/** ①-2 无样式三态复选框（全选/部分半选/未选） */
@Composable
fun VeneraTriStateCheckbox(
    state: ToggleableState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val shape = RoundedCornerShape(6.dp)

    UnstyledTriStateCheckbox(
        value = state,
        onClick = onClick,
        modifier = modifier.size(19.dp),
        enabled = enabled,
    ) {
        Box(
            modifier = Modifier
                .size(19.dp)
                .clip(shape)
                .then(
                    when (state) {
                        ToggleableState.On -> Modifier.background(primaryColor)
                        ToggleableState.Indeterminate -> Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .border(1.6.dp, primaryColor, shape)
                        ToggleableState.Off -> Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                            .border(1.6.dp, outlineColor, shape)
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                ToggleableState.On -> {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp),
                    )
                }
                ToggleableState.Indeterminate -> {
                    Box(
                        modifier = Modifier
                            .size(width = 9.dp, height = 2.dp)
                            .background(primaryColor, shape = RoundedCornerShape(1.dp))
                    )
                }
                ToggleableState.Off -> {
                    // 空白未选中
                }
            }
        }
    }
}

/** ② 无样式 TabGroup（设计稿 chipset 胶囊分段器驱动） */
@Composable
fun <T> VeneraTabGroup(
    selected: T,
    onSelect: (T) -> Unit,
    items: List<T>,
    modifier: Modifier = Modifier,
    itemLabel: (T) -> String,
) {
    val tabActiveBg = MaterialTheme.colorScheme.primary // 主档归全局主色（阶段 2，紫钮族最后一处）
    val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)

    UnstyledTabGroup(
        selectedTab = selected,
        onSelectedTabChange = onSelect,
        tabs = items,
        modifier = modifier,
    ) {
        TabList(
            orientation = Orientation.Horizontal,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .border(1.dp, outlineColor, RoundedCornerShape(10.dp))
                .padding(3.dp),
        ) {
            items.forEach { item ->
                Tab(key = item) {
                    val isSelected = selected == item
                    val textSecondary = VeneraTokens.color.textSecondary
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .then(
                                if (isSelected) {
                                    Modifier.background(tabActiveBg)
                                } else {
                                    Modifier
                                }
                            )
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = itemLabel(item),
                            fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) Color.White else textSecondary,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** ③ 无样式折叠分组（Disclosure） */
@Composable
fun VeneraDisclosure(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    UnstyledDisclosure(
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        modifier = modifier,
    ) {
        DisclosureButton(
            modifier = Modifier.fillMaxWidth(),
        ) {
            header()
        }
        DisclosedContent {
            content()
        }
    }
}

/** ④ 无样式进度条（ProgressIndicator） */
@Composable
fun VeneraProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
) {
    val clamped = progress.coerceIn(0f, 1f)
    UnstyledProgress(
        progress = clamped,
        modifier = modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(trackColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(clamped)
                .height(5.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(color)
        )
    }
}

/**
 * 开关（薄壳，控件）：几何与色档全在 [DesktopSwitch]，
 * 这里只保留原签名让调用点零改动。
 */
@Composable
fun VeneraSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    DesktopSwitch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
    )
}



/** ⑥ 无样式模态删除/确认对话框（Modal + Scrim + Alert Dialog 语义） */
@Composable
fun VeneraModalDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    cancelText: String = "取消",
    isDanger: Boolean = true,
) {
    if (!visible) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.key == Key.Escape) {
                    onDismiss()
                    true
                } else false
            },
        contentAlignment = Alignment.Center,
    ) {
        // 背景 Scrim 遮罩
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(onClick = onDismiss),
        )

        // 居中模态卡片
        Column(
            modifier = modifier
                .widthIn(max = 420.dp)
                .padding(20.dp)
                .clip(GalleryTokens.Shape.Large)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), GalleryTokens.Shape.Large)
                .padding(20.dp)
        ) {
            Text(
                text = title,
                fontSize = VeneraType.cardTitle.size, lineHeight = VeneraType.cardTitle.lineHeight,
                fontWeight = VeneraType.cardTitle.weight,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                fontSize = VeneraType.aux.size,
                color = VeneraTokens.color.textSecondary,
                lineHeight = 18.sp,
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .semantics(mergeDescendants = true) { role = Role.Button }
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    Text(
                        text = cancelText,
                        fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                val dangerColor = com.venera.desktop.gallery.ui.GalleryTokens.Color.ToneDanger
                val confirmBg = if (isDanger) dangerColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primary
                val confirmTextClr = if (isDanger) dangerColor else Color.White
                val confirmBorder = if (isDanger) dangerColor.copy(alpha = 0.45f) else Color.Transparent
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(confirmBg)
                        .border(1.dp, confirmBorder, RoundedCornerShape(8.dp))
                        .semantics(mergeDescendants = true) { role = Role.Button }
                        .clickable {
                            onConfirm()
                            onDismiss()
                        }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    Text(
                        text = confirmText,
                        fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                        fontWeight = VeneraType.cardTitle.weight,
                        color = confirmTextClr,
                    )
                }
            }
        }
    }
}

/** ⑦ 悬停气泡提示（Tooltip） */
@Composable
fun VeneraTooltip(
    text: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (text.isBlank()) {
        content()
        return
    }
    UnstyledTooltip(
        enabled = true,
        side = AnchorSide.Top,
        alignment = AnchorAlignment.Center,
        panel = {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xEE12141D))
                    .border(1.dp, Color(0xFF2A2F42), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = text,
                    fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                    color = Color(0xFFE9EBF3),
                )
            }
        },
        anchor = {
            content()
        },
    )
}
