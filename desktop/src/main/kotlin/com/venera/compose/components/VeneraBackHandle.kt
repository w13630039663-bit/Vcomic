package com.venera.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.ui.tokens.VeneraTokens

/**
 * 全软件统一的返回把手。
 *
 * ## 一颗件 · 两种规格 · 一屏一枚
 *
 * - [VeneraBackHandleStyle.Compact]（高 26）：住自绘标题栏（`DesktopWindowTitleBar`）与
 *   页头卡（`DesktopPageShell`）—— titlebar 可见的页面，返回都在这一层。
 * - [VeneraBackHandleStyle.Glass]（高 34，blur + 投影）：住沉浸层（阅读器 / 看图播放器画布）
 *   —— titlebar 退位成拖动热条时，返回落画布左上。
 *
 * 视觉基因：玻璃 chip 底 + 紫色左箭头 + "返回"二字；hover 描边转紫、底提亮、箭头左移 1px；
 * 按下下沉 1px 不缩放（离开类动作，桌面克制口径）。**回跳逻辑不在这颗件里** —— 它只管长相，
 * "回到哪"由调用点的 `onBack`（壳里 `desktopShellBack` / `from` 回跳那族）决定。
 */
enum class VeneraBackHandleStyle { Compact, Glass }

@Composable
fun VeneraBackHandle(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: VeneraBackHandleStyle = VeneraBackHandleStyle.Compact,
) {
    val tokens = VeneraTokens
    val ux = LocalUxPlatformServices.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val glass = style == VeneraBackHandleStyle.Glass
    val shape = RoundedCornerShape(11.dp)

    Row(
        modifier = modifier
            .hoverable(interactionSource = interaction)
            .clip(shape)
            .background(
                when {
                    hovered -> tokens.color.surfaceContainerHigh.copy(alpha = 0.95f)
                    else -> tokens.color.surfaceContainerHigh.copy(alpha = 0.72f)
                },
            )
            .border(
                1.dp,
                if (hovered) {
                    tokens.color.primary.copy(alpha = 0.55f)
                } else {
                    tokens.color.outlineVariant.copy(alpha = 0.6f)
                },
                shape,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
            ) {
                ux.hapticTap()
                onClick()
            }
            .then(if (pressed) Modifier.offset(y = 1.dp) else Modifier)
            .padding(horizontal = if (glass) 15.dp else 10.dp, vertical = if (glass) 7.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = null,
            tint = tokens.color.primary,
            modifier = Modifier.size(if (glass) 14.dp else 12.dp),
        )
        Text(
            text = "返回",
            fontSize = if (glass) 12.5.sp else 12.sp,
            fontWeight = if (glass) FontWeight.SemiBold else FontWeight.Medium,
            color = tokens.color.textPrimary,
        )
    }
}
