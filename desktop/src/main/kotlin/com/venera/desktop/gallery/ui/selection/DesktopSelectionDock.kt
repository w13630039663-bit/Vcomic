package com.venera.desktop.gallery.ui.selection

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.venera.desktop.gallery.ui.LocalChromePalette
import com.venera.desktop.ui.DesktopButton
import com.venera.desktop.ui.DesktopButtonSize
import com.venera.desktop.ui.DesktopButtonVariant
import com.venera.desktop.ui.DesktopEffects
import com.venera.desktop.ui.vShadow
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur

/**
 * 为悬浮多选栏提供背景模糊采样的 Haze 源。
 * 页面内容容器通过 `hazeSource(hazeState)` 登记后，经由本 Local 提供给底部多选坞。
 */
internal val LocalSelectionDockHazeState = staticCompositionLocalOf<HazeState?> { null }

/**
 * 双域共用的悬浮多选栏（52dp 高 / r16 玻璃岛，底部居中，距底 24dp）。
 *
 * 1. 材质：开启底板模糊且具备 [hazeState] 时叠加实时毛玻璃模糊；关闭模糊或未接源时降级为同色系实底/半透底。
 * 2. 按钮：内部全选、反选、关闭及右侧操作按钮统一使用全仓标准 [DesktopButton]（Medium 32dp / r8）。
 *
 * @param selectedCount 当前选中条目数，为 0 时整条不渲染
 * @param totalCount 当前可见条目总数（用于禁用已满状态下的全选）
 * @param onSelectAll 全选回调
 * @param onInvertSelect 反选回调
 * @param onClearSelect 清空/关闭多选回调
 * @param hazeState 背景内容模糊源，默认从 [LocalSelectionDockHazeState] 读取
 * @param actions 右侧动作区槽位
 */
@Composable
internal fun DesktopSelectionDock(
    selectedCount: Int,
    totalCount: Int,
    onSelectAll: () -> Unit,
    onInvertSelect: () -> Unit,
    onClearSelect: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = LocalSelectionDockHazeState.current,
    actions: @Composable RowScope.() -> Unit,
) {
    val palette = LocalChromePalette.current
    val blurActive = !DesktopEffects.reduce
    val shape = RoundedCornerShape(16.dp)
    val surface = when {
        hazeState != null && blurActive ->
            if (palette.dark) palette.raised.copy(alpha = 0.48f) else palette.card.copy(alpha = 0.64f)
        blurActive ->
            if (palette.dark) palette.raised.copy(alpha = 0.84f) else palette.card.copy(alpha = 0.88f)
        else -> palette.elevated
    }
    val border = if (palette.dark) Color.White.copy(alpha = 0.16f) else palette.frame
    val blurModifier = if (hazeState != null && blurActive) {
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

    AnimatedVisibility(
        visible = selectedCount > 0,
        modifier = modifier.zIndex(1f),
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
        ) + fadeIn(tween(180)),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(150),
        ) + fadeOut(tween(150)),
    ) {
        Row(
            modifier = Modifier
                .height(52.dp)
                .vShadow(12.dp, shape)
                .clip(shape)
                .then(blurModifier)
                .background(surface)
                .border(1.dp, border, shape)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 计数牌：与内部 Medium 按钮同高（32dp / r8），保持几何整齐。
            Box(
                modifier = Modifier
                    .height(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.accent.copy(alpha = 0.14f))
                    .border(0.5.dp, palette.accent.copy(alpha = 0.28f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = selectedCount,
                    transitionSpec = { fadeIn(tween(120)) togetherWith fadeOut(tween(90)) },
                    label = "dockCount",
                ) { count ->
                    Text(
                        text = "已选 $count",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.accent,
                    )
                }
            }
            DockTextAction(
                text = "全选",
                enabled = selectedCount < totalCount,
                onClick = onSelectAll,
            )
            DockTextAction(text = "反选", onClick = onInvertSelect)
            DockIconAction(
                icon = Icons.Outlined.Close,
                description = "清空选择",
                danger = true,
                onClick = onClearSelect,
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(20.dp)
                    .background(palette.hairline),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                content = actions,
            )
        }
    }
}

/** 坞内文字按钮（全选 / 反选）：统一走 Medium 档 Ghost 变体。 */
@Composable
internal fun RowScope.DockTextAction(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    DesktopButton(
        text = text,
        onClick = onClick,
        size = DesktopButtonSize.Medium,
        variant = DesktopButtonVariant.Ghost,
        enabled = enabled,
        horizontalPadding = 10.dp,
    )
}

/** 坞内图标按钮（32×32dp 方圆角 / r8）：统一走 Medium 档标准按钮。 */
@Composable
internal fun DockIconAction(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    danger: Boolean = false,
    enabled: Boolean = true,
) {
    DesktopButton(
        onClick = onClick,
        size = DesktopButtonSize.Medium,
        variant = if (danger) DesktopButtonVariant.Danger else DesktopButtonVariant.Secondary,
        enabled = enabled,
        horizontalPadding = 8.dp,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** 坞内主/次动作按钮（32dp 高 / r8）：与全仓按钮保持同一质感。 */
@Composable
internal fun RowScope.DockActionPill(
    text: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    primary: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true,
) {
    val variant = when {
        danger -> DesktopButtonVariant.Danger
        primary -> DesktopButtonVariant.Primary
        else -> DesktopButtonVariant.Secondary
    }
    DesktopButton(
        text = text,
        onClick = onClick,
        size = DesktopButtonSize.Medium,
        variant = variant,
        leadingIcon = icon,
        enabled = enabled,
    )
}

/** 坞与屏幕底边的距离（设计稿：24dp）。 */
internal val DesktopSelectionDockBottomInset = 24.dp

/**
 * 卡片封面上的圆形多选勾选钮（悬停或已选中时显示）。
 *
 * @param selected 当前是否处于选中态
 * @param onToggle 点击切换选中状态
 * @param description 无障碍语义描述
 * @param modifier 外部对齐与边距修饰符
 * @param size 圆形直径，网格视图默认 24dp，双列小封面可传 22dp
 */
@Composable
internal fun DesktopCardSelectBadge(
    selected: Boolean,
    onToggle: () -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    val palette = LocalChromePalette.current
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                if (selected) palette.accent else Color.Black.copy(alpha = 0.56f),
            )
            .border(
                1.5.dp,
                if (selected) palette.accent else Color.White.copy(alpha = 0.72f),
                CircleShape,
            )
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Check,
            contentDescription = description,
            tint = Color.White.copy(alpha = if (selected) 1f else 0.78f),
            modifier = Modifier.size(if (size <= 22.dp) 13.dp else 14.dp),
        )
    }
}
