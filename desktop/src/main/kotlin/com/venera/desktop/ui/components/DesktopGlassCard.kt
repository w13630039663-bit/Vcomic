package com.venera.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.settings.DesktopBackdropMode

/**
 * 全站透明玻璃卡片材质层级（源自站点页卡片规范）。
 *
 * - [Primary]：页面级主卡片 / 功能块（如站点卡、统计卡、主面板容器）
 * - [Nested]：卡片内部嵌套的子格 / 输入槽 / 状态条 / 列表项
 */
enum class DesktopGlassCardLevel {
    Primary,
    Nested,
}

/**
 * 桌面端玻璃卡片的统一配色与修饰符工厂。
 *
 * 设计口径：
 * 1. 当系统材质透底激活（[DesktopBackdropMode.isTransparentActive]）时采用半透明玻璃档；
 * 2. 当用户主动关闭底板模糊切换为实底模式时，自动抬高为实色表面以保证文字与边界对比度；
 * 3. 禁止叠加低 elevation 的 `Modifier.shadow`，并始终先绘制背景与描边再 `.clip(shape)`，
 *    避免 1dp 描边被圆角裁剪掉一半。
 */
object DesktopGlassCardDefaults {

    val PrimaryShape: Shape = RoundedCornerShape(16.dp)
    val NestedShape: Shape = RoundedCornerShape(10.dp)

    @Composable
    fun backgroundColor(
        level: DesktopGlassCardLevel = DesktopGlassCardLevel.Primary,
        hovered: Boolean = false,
        transparent: Boolean = DesktopBackdropMode.isTransparentActive(),
    ): Color {
        val c = VeneraTokens.color
        return if (transparent) {
            when (level) {
                DesktopGlassCardLevel.Primary ->
                    c.surfaceContainerHigh.copy(alpha = if (hovered) 0.60f else 0.45f)
                DesktopGlassCardLevel.Nested ->
                    c.surfaceContainerHigh.copy(alpha = if (hovered) 0.50f else 0.35f)
            }
        } else {
            when (level) {
                DesktopGlassCardLevel.Primary ->
                    if (hovered) c.surfaceContainerHigh else c.surface
                DesktopGlassCardLevel.Nested ->
                    if (hovered) c.surfaceVariant else c.surfaceContainerHigh
            }
        }
    }

    @Composable
    fun borderColor(
        level: DesktopGlassCardLevel = DesktopGlassCardLevel.Primary,
        hovered: Boolean = false,
        interactive: Boolean = false,
        transparent: Boolean = DesktopBackdropMode.isTransparentActive(),
    ): Color {
        val c = VeneraTokens.color
        if (interactive && hovered) {
            return when (level) {
                DesktopGlassCardLevel.Primary ->
                    c.primary.copy(alpha = if (transparent) 0.38f else 0.42f)
                DesktopGlassCardLevel.Nested ->
                    c.primary.copy(alpha = if (transparent) 0.35f else 0.38f)
            }
        }
        return if (transparent) {
            when (level) {
                DesktopGlassCardLevel.Primary -> c.outlineVariant.copy(alpha = 0.25f)
                DesktopGlassCardLevel.Nested -> c.outlineVariant.copy(alpha = 0.18f)
            }
        } else {
            when (level) {
                DesktopGlassCardLevel.Primary -> c.outlineVariant.copy(alpha = 0.22f)
                DesktopGlassCardLevel.Nested -> c.outlineVariant.copy(alpha = 0.14f)
            }
        }
    }

    @Composable
    fun surfaceModifier(
        modifier: Modifier = Modifier,
        level: DesktopGlassCardLevel = DesktopGlassCardLevel.Primary,
        shape: Shape = if (level == DesktopGlassCardLevel.Primary) PrimaryShape else NestedShape,
        borderWidth: Dp = 1.dp,
        hovered: Boolean = false,
        interactive: Boolean = false,
        transparent: Boolean = DesktopBackdropMode.isTransparentActive(),
    ): Modifier = modifier
        .background(backgroundColor(level = level, hovered = hovered, transparent = transparent), shape)
        .border(borderWidth, borderColor(level = level, hovered = hovered, interactive = interactive, transparent = transparent), shape)
        .clip(shape)
}

/**
 * 新建与重做功能页默认使用的标准玻璃卡片容器。
 *
 * 自动根据当前底板材质状态在「半透明玻璃」与「实色高对比」间切换；
 * 传入 [onClick] 时自动启用悬停变色与亮框反馈，遵循桌面端“按下无缩放”语义。
 */
@Composable
fun DesktopGlassCard(
    modifier: Modifier = Modifier,
    level: DesktopGlassCardLevel = DesktopGlassCardLevel.Primary,
    shape: Shape = if (level == DesktopGlassCardLevel.Primary) {
        DesktopGlassCardDefaults.PrimaryShape
    } else {
        DesktopGlassCardDefaults.NestedShape
    },
    contentPadding: PaddingValues = PaddingValues(if (level == DesktopGlassCardLevel.Primary) 20.dp else 12.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(12.dp),
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interactive = onClick != null && enabled
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val hovered = interactive && isHovered

    val surfaceModifier = DesktopGlassCardDefaults.surfaceModifier(
        modifier = modifier,
        level = level,
        shape = shape,
        hovered = hovered,
        interactive = interactive,
    )
    val clickModifier = if (onClick != null) {
        Modifier
            .hoverable(interactionSource, enabled = enabled)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
    } else {
        Modifier
    }

    Column(
        modifier = surfaceModifier
            .then(clickModifier)
            .padding(contentPadding),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}
