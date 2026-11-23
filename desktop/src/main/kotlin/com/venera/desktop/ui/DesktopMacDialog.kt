package com.venera.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.venera.desktop.gallery.ui.LocalChromePalette
import com.venera.desktop.ui.theme.VeneraType

/**
 * 弹窗正文的**统一行盒**。
 *
 * 从前同一颗弹窗里混着两套：按钮 / 分段 / 搜索占位是「12sp + 默认行盒」，
 * 章节行 / 计数是「13sp + [VeneraType.aux] 的 20sp 行盒」。行盒高度不同，
 * 定高容器里 `Alignment.Center` 居中之后字形仍然高低不齐。这里把正文全收口到
 * 13sp 字 / 18sp 行盒：行盒只比字高 5sp，既不会被字体度量的上下留白顶偏，
 * 也不会像 20sp 那样把定高行撑虚。
 */
internal val MacDialogBodyLineHeight = 18.sp

/** 卡片可用高（宿主 Dialog 窗口高减上下各 16dp 留白），长列表据此自己收口，避免弹窗溢出屏外。 */
internal val LocalMacDialogMaxCardHeight = compositionLocalOf<Dp> { 10000.dp }

/**
 * 桌面端自绘 mac 浮层对话框：**交互层零 Material3 控件、零 ripple**。
 *
 * 弹窗底子不要 MD3，也不要水波纹。
 * - 容器：`LocalChromePalette` 色板 + r10 + 0.5dp 发丝 + 顶缘 0.5dp 高光，
 *   不用 `Md3MaterialTheme.colorScheme`，也不裸用低 elevation `Modifier.shadow`
 *   （项目 Compose Desktop 定案：浅底上会渲染成硬边灰框）。
 * - 保留独立 `Dialog` 窗口形态 ⇒ 点外部关闭 / Esc / 焦点语义不变；遮罩与卡片是兄弟节点，
 *   点卡片不会冒泡触发关闭。跨窗口 blur 取不到宿主 RenderNode，故不叠玻璃采样层。
 * - 全部点击走 [macNoRippleClickable] / `clickable(indication = null)`，按下无缩放，
 * 悬停靠 hoverable 即时变色（mac 桌面语义，见 controls-unify-）。
 */
@Composable
fun DesktopMacDialog(
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    /** 卡片宽度；null = 按窗口收口的上限宽度（宽表用）。窄清单类弹窗可钉一个固定宽。 */
    width: Dp? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = LocalChromePalette.current
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // 取宿主窗口尺寸，长列表 / 宽卡片据此收口 —— 放大后不留「卡片比窗口还大、底栏被裁」的口子。
        // 920dp 是**上限**不是定值：主人截图那扇窗只有约 770dp 宽，写死会把右半边推出屏外。
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val roomH = maxHeight - 32.dp
            val maxCardHeight = if (roomH > 240.dp) roomH else 240.dp
            val roomW = maxWidth - 32.dp
            val maxCardWidth = if (roomW > 920.dp) 920.dp else roomW
            CompositionLocalProvider(LocalMacDialogMaxCardHeight provides maxCardHeight) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val scrimInteraction = remember { MutableInteractionSource() }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.42f))
                            .clickable(
                                interactionSource = scrimInteraction,
                                indication = null,
                                onClick = onDismissRequest,
                            ),
                    )
                    val shape = RoundedCornerShape(10.dp)
                    Column(
                        modifier = modifier
                            .width(width?.coerceAtMost(maxCardWidth) ?: maxCardWidth)
                            .heightIn(max = maxCardHeight)
                            // 卡片已在遮罩内居中，不加外层 horizontal padding 以免裁切正文宽度。
                            .clip(shape)
                            .background(palette.elevated)
                            .border(0.5.dp, palette.hairline, shape),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(0.5.dp)
                                .background(Color.White.copy(alpha = 0.14f)),
                        )
                        Column(Modifier.padding(20.dp)) {
                            Text(
                                text = com.venera.desktop.settings.DesktopI18n.t(title),
                                fontSize = VeneraType.cardTitle.size,
                                fontWeight = VeneraType.cardTitle.weight,
                                color = palette.ink1,
                            )
                            Spacer(Modifier.height(12.dp))
                            content()
                        }
                    }
                }
            }
        }
    }
}

/** 无 ripple 的点击：悬停即时变色由各控件自己负责，这里只负责关掉 indication。 */
@Composable
fun Modifier.macNoRippleClickable(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this
        .hoverable(interaction)
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
}

/** 自绘文字钮：24dp / r6；primary 走 accent 实底白字，桌面语义按下无缩放、无涟漪。 */
@Composable
fun MacDialogTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
) {
    val palette = LocalChromePalette.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val face = when {
        primary -> palette.accent
        hovered && enabled -> Color.White.copy(alpha = 0.08f)
        else -> Color.Transparent
    }
    val ink = when {
        !enabled -> palette.ink3.copy(alpha = 0.45f)
        primary -> Color.White
        else -> palette.ink2
    }
    Box(
        modifier = modifier
            .height(24.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(face)
            .hoverable(interaction)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = VeneraType.aux.size,
            lineHeight = MacDialogBodyLineHeight,
            color = ink,
        )
    }
}

/** 自绘复选：14dp / r4；选中 accent 实底 + 10dp 白勾，无 M3 Checkbox 的涟漪。 */
@Composable
fun MacDialogCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalChromePalette.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier = modifier
            .size(14.dp)
            .clip(shape)
            .background(
                when {
                    checked -> palette.accent
                    hovered -> Color.White.copy(alpha = 0.10f)
                    else -> Color.White.copy(alpha = 0.06f)
                },
            )
            .border(0.5.dp, if (checked) Color.Transparent else palette.hairline, shape)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null) {
                onCheckedChange(!checked)
            },
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(10.dp),
            )
        }
    }
}

/** 分段选择器的对话窗入口：与画廊顶栏同一颗 [DesktopSegmented]，不自绘。 */
@Composable
fun MacDialogSegmented(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    DesktopSegmented(
        options = items,
        selectedIndex = selectedIndex,
        onSelect = onSelect,
        modifier = modifier,
    )
}
