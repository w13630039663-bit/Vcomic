package com.venera.desktop.installer

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.desktop.ui.DesktopButton
import com.venera.desktop.ui.DesktopButtonColors
import com.venera.desktop.ui.DesktopButtonSize
import com.venera.desktop.ui.DesktopButtonVariant
import com.venera.desktop.ui.theme.VeneraFonts

/**
 * 原生云母玻璃窗口外壳：640dp 宽、基础 440dp 高，展开目录面板时 300ms 平滑延展。
 *
 * @param isDark 是否启用深色主题；默认为浅色。
 */
@Composable
fun BoxScope.InstallerCard(
    isDark: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val palette = if (isDark) InstallerSpec.DarkPalette else InstallerSpec.LightPalette
    CompositionLocalProvider(LocalInstallerPalette provides palette) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(InstallerSpec.CardWidth)
                .heightIn(min = InstallerSpec.CardHeight)
                .animateContentSize(
                    animationSpec = tween(
                        durationMillis = InstallerTimeline.PATH_EXPAND_MS.toInt(),
                        easing = FastOutSlowInEasing,
                    ),
                )
                .clip(InstallerSpec.CardShape)
                .background(palette.windowBg, InstallerSpec.CardShape)
                .border(1.dp, palette.windowBorder, InstallerSpec.CardShape),
            content = content,
        )
    }
}

/**
 * 极简 40dp 窗口标题栏：左侧状态点与元数据（Splash 期隐藏），右侧最小化 / 关闭按钮。
 *
 * @param alpha 元数据入场透明度，Splash 阶段为 0。
 * @param onMinimize 最小化回调。
 * @param onClose 关闭回调。
 */
@Composable
fun InstallerTitleBar(
    alpha: Float,
    onMinimize: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalInstallerPalette.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(InstallerSpec.TitleBarHeight)
            .padding(start = 18.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.graphicsLayer { this.alpha = alpha },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(palette.accent),
            )
            Text(
                text = InstallerCopy.AppTitle,
                style = TextStyle(
                    fontFamily = VeneraFonts.family,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = palette.textTertiary,
                ),
            )
        }
        Spacer(Modifier.weight(1f))
        TitleBarButton("—", onClick = onMinimize)
        Spacer(Modifier.width(4.dp))
        TitleBarButton("×", onClick = onClose, danger = true)
    }
}

@Composable
private fun TitleBarButton(label: String, onClick: () -> Unit, danger: Boolean = false) {
    val palette = LocalInstallerPalette.current
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) InstallerTimeline.BUTTON_PRESS_SCALE else 1f,
        animationSpec = tween(InstallerTimeline.BUTTON_PRESS_MS),
        label = "title-btn-press",
    )
    val background = when {
        danger && hovered -> InstallerSpec.CloseHover
        hovered -> palette.surfaceHover
        else -> Color.Transparent
    }
    val fg = when {
        danger && hovered -> InstallerSpec.CloseForeground
        hovered -> palette.textPrimary
        else -> palette.textSecondary
    }
    Box(
        modifier = Modifier
            .size(28.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(InstallerSpec.ControlShape)
            .background(background)
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontFamily = VeneraFonts.family,
                fontSize = 13.sp,
                color = fg,
            ),
        )
    }
}

/**
 * 通用内层玻璃卡片容器（12dp 圆角，满足 Outer 20 = Inner 12 + Padding 8 同心圆几何）。
 */
@Composable
fun InstallerSurfaceCard(
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = LocalInstallerPalette.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(InstallerSpec.InnerCardShape)
            .background(palette.surface, InstallerSpec.InnerCardShape)
            .border(1.dp, palette.borderSubtle, InstallerSpec.InnerCardShape)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/**
 * 主按钮：全仓标准 Large（40dp / r10）+ 安装向导主色板。
 */
@Composable
fun InstallerPrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val palette = LocalInstallerPalette.current
    DesktopButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minWidth = InstallerSpec.PrimaryButtonWidth),
        size = DesktopButtonSize.Large,
        variant = if (danger) DesktopButtonVariant.Danger else DesktopButtonVariant.Primary,
        horizontalPadding = 18.dp,
        colors = DesktopButtonColors(
            bg = if (danger) palette.danger else palette.accent,
            bgHover = if (danger) palette.dangerHover else palette.accentHover,
            border = Color.Transparent,
            borderHover = Color.Transparent,
            content = InstallerSpec.OnAccent,
            contentHover = InstallerSpec.OnAccent,
        ),
    ) {
        Text(
            text = text,
            style = TextStyle(
                fontFamily = VeneraFonts.family,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = InstallerSpec.OnAccent,
            ),
        )
    }
}

/**
 * 次级按钮：全仓标准按钮（Large 40dp / Medium 32dp）+ 安装向导半透明玻璃面。
 */
@Composable
fun InstallerSecondaryButton(
    text: String,
    modifier: Modifier = Modifier,
    height: Dp = InstallerSpec.PrimaryButtonHeight,
    onClick: () -> Unit,
) {
    val palette = LocalInstallerPalette.current
    val size = if (height <= 34.dp) DesktopButtonSize.Medium else DesktopButtonSize.Large
    DesktopButton(
        onClick = onClick,
        modifier = modifier,
        size = size,
        variant = DesktopButtonVariant.Secondary,
        horizontalPadding = 14.dp,
        colors = DesktopButtonColors(
            bg = palette.surface,
            bgHover = palette.surfaceHover,
            border = palette.borderSubtle,
            borderHover = palette.borderStrong,
            content = palette.textPrimary,
            contentHover = palette.textPrimary,
        ),
    ) {
        Text(
            text = text,
            style = TextStyle(
                fontFamily = VeneraFonts.family,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                color = palette.textPrimary,
            ),
        )
    }
}

/**
 * 幽灵按钮：全仓标准 Large 幽灵变体 + 安装向导文字色板。
 */
@Composable
fun InstallerGhostButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val palette = LocalInstallerPalette.current
    DesktopButton(
        onClick = onClick,
        modifier = modifier,
        size = DesktopButtonSize.Large,
        variant = DesktopButtonVariant.Ghost,
        horizontalPadding = 12.dp,
        colors = DesktopButtonColors(
            bg = Color.Transparent,
            bgHover = palette.surfaceSubtle,
            border = Color.Transparent,
            borderHover = Color.Transparent,
            content = palette.textSecondary,
            contentHover = palette.textPrimary,
        ),
    ) {
        Text(
            text = text,
            style = TextStyle(
                fontFamily = VeneraFonts.family,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

/** 版本 / 状态小胶囊。 */
@Composable
fun InstallerPill(
    text: String,
    modifier: Modifier = Modifier,
) {
    val palette = LocalInstallerPalette.current
    Box(
        modifier = modifier
            .height(20.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(palette.badgeBg)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TextStyle(
                fontFamily = VeneraFonts.family,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.badgeText,
            ),
        )
    }
}
