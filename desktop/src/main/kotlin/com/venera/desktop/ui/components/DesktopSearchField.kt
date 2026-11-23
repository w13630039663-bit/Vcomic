package com.venera.desktop.ui.components

import com.venera.desktop.ui.theme.VeneraType
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/**
 * 全仓唯一输入场取色。
 *
 * 几何与画廊顶栏搜索场一致：34dp 高 / r8 / 1dp 描边，
 * 悬停转 [fieldBorderHover]、聚焦转 [fieldBorderFocus]（2dp）。形状不随色面变，只有色源可插。
 */
data class DesktopSearchFieldColors(
    val fieldBg: Color,
    val fieldBgFocused: Color,
    val fieldBorder: Color,
    val fieldBorderHover: Color,
    val fieldBorderFocus: Color,
    val iconTint: Color,
    val textColor: Color,
    val placeholderColor: Color,
    val hintColor: Color,
    val cursor: Color,
) {
    companion object {

        /** 常规档：画廊顶栏口径（ChromePalette 派生，随深浅换档）。 */
        @Composable
        fun chrome(): DesktopSearchFieldColors {
            val palette = com.venera.desktop.gallery.ui.LocalChromePalette.current
            return DesktopSearchFieldColors(
                fieldBg = palette.segmentTrack,
                fieldBgFocused = palette.elevated,
                fieldBorder = palette.hairline,
                fieldBorderHover = palette.frame,
                fieldBorderFocus = palette.accent,
                iconTint = palette.ink3,
                textColor = palette.ink1,
                placeholderColor = palette.ink3,
                hintColor = palette.ink3,
                cursor = palette.accent,
            )
        }

        /** 设置玻璃档：SettingsGlassPalette 派生（设置中枢 / 设置总览）。 */
        @Composable
        fun settingsGlass(): DesktopSearchFieldColors {
            val glass = com.venera.desktop.settings.LocalSettingsGlass.current
            return DesktopSearchFieldColors(
                fieldBg = glass.track,
                fieldBgFocused = glass.glassFill,
                fieldBorder = glass.glassBorder,
                fieldBorderHover = glass.glassBorderFocus,
                fieldBorderFocus = glass.glassBorderFocus,
                iconTint = glass.textTertiary,
                textColor = glass.textPrimary,
                placeholderColor = glass.textTertiary,
                hintColor = glass.textTertiary,
                cursor = glass.primary,
            )
        }
    }
}

/**
 * 统一单行/多行输入场（视觉核心）。搜索场与各类命名、账号、来源编辑框都走这一颗，
 * 外形固定：34dp / r8 / 1dp 描边，悬停 [DesktopSearchFieldColors.fieldBorderHover]、
 * 聚焦 [DesktopSearchFieldColors.fieldBorderFocus] 且描边加粗到 2dp。
 *
 * @param placeholder 占位词
 * @param leadingIcon 前导图标（搜索场传放大镜；命名框可不传）
 * @param trailing 尾部附加内容（如快捷键提示、计数）
 * @param singleLine 单行；多行时高度随内容生长，最小仍为 34dp
 * @param enabled false = 整框降透明并停止交互
 * @param visualTransformation 密码遮字等文本变换
 * @param focusRequester 调用点需要主动聚焦时传入
 * @param interactionSource 调用点需要观察焦点时传入；不传则内部自建
 */
@Composable
fun DesktopInputField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leadingIcon: ImageVector? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    enabled: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    focusRequester: FocusRequester? = null,
    interactionSource: MutableInteractionSource? = null,
    onClick: (() -> Unit)? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    colors: DesktopSearchFieldColors = DesktopSearchFieldColors.chrome(),
    textSize: TextUnit = VeneraType.bodySecondary.size,
    textStyle: TextStyle? = null,
    isError: Boolean = false,
    errorBorder: Color? = null,
) {
    val fieldShape = RoundedCornerShape(8.dp)
    val ownInteraction = remember { MutableInteractionSource() }
    val source = interactionSource ?: ownInteraction
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()

    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 34.dp)
            .clip(fieldShape)
            .background(if (focused) colors.fieldBgFocused else colors.fieldBg)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = when {
                    isError -> errorBorder ?: colors.fieldBorderFocus
                    focused -> colors.fieldBorderFocus
                    hovered -> colors.fieldBorderHover
                    else -> colors.fieldBorder
                },
                shape = fieldShape,
            )
            .hoverable(source, enabled = enabled)
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = source, indication = null, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .then(if (enabled) Modifier else Modifier.alpha(0.55f))
            .padding(horizontal = 11.dp, vertical = if (singleLine) 0.dp else 9.dp),
        verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = if (focused) colors.fieldBorderFocus else colors.iconTint,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .then(if (onFocusChanged != null) Modifier.onFocusChanged { onFocusChanged(it.isFocused) } else Modifier)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
            enabled = enabled,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            textStyle = textStyle ?: TextStyle(
                fontSize = textSize,
                lineHeight = VeneraType.bodySecondary.lineHeight,
                color = colors.textColor,
            ),
            cursorBrush = SolidColor(colors.cursor),
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            interactionSource = source,
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        fontSize = textSize,
                        lineHeight = VeneraType.bodySecondary.lineHeight,
                        color = colors.placeholderColor,
                        maxLines = 1,
                    )
                }
                inner()
            },
        )
        if (trailing != null) trailing()
    }
}

/**
 * 统一搜索场：画廊顶栏那颗的完整形态（放大镜 + 清除钮 + 可选快捷键提示）。
 *
 * 与 [DesktopInputField] 同皮，只多搜索语义；各屏占位词自念各的话。
 *
 * @param shortcutHint 右端快捷键提示（"Ctrl K"）；null = 不摆
 * @param onClear 点清除钮（null = 没有清除钮；有值且非空时才显）
 * @param searching 检索中标记（保留给调用点做进度展示，本件不改变几何）
 */
@Composable
fun DesktopSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "搜索",
    shortcutHint: String? = null,
    onClear: (() -> Unit)? = null,
    searching: Boolean = false,
    colors: DesktopSearchFieldColors = DesktopSearchFieldColors.chrome(),
    focusRequester: FocusRequester? = null,
    interactionSource: MutableInteractionSource? = null,
    onClick: (() -> Unit)? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null,
) {
    DesktopInputField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = placeholder,
        leadingIcon = Icons.Outlined.Search,
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        focusRequester = focusRequester,
        interactionSource = interactionSource,
        onClick = onClick,
        onFocusChanged = onFocusChanged,
        colors = colors,
        trailing = {
            if (onClear != null && value.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(colors.fieldBorder.copy(alpha = 0.65f))
                        .clickable { onClear() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "清除",
                        tint = colors.iconTint,
                        modifier = Modifier.size(12.dp),
                    )
                }
                Spacer(Modifier.width(6.dp))
            }
            if (!shortcutHint.isNullOrBlank()) {
                Text(
                    text = shortcutHint,
                    fontSize = VeneraType.micro.size,
                    lineHeight = VeneraType.micro.lineHeight,
                    color = colors.hintColor,
                )
            }
        },
    )
}
