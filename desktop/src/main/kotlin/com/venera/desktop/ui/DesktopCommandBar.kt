package com.venera.desktop.ui

import com.venera.desktop.ui.theme.VeneraType
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.desktop.gallery.ui.DesktopChromePalette

// 桌面内容区顶部命令带（44dp 占位高度，不吃水平内边距以使窗控贴右缘）；颜色统一取自 LocalChromePalette。

private val CommandPill = RoundedCornerShape(999.dp)

/**
 * 桌面内容区顶部命令带：承载返回按钮、路径面包屑、拖拽区与窗口控制按钮。
 *
 * @param palette 桌面壳层双档色板
 * @param breadcrumb 当前路由路径标题
 * @param onBack 上一级返回回调，一级页传 null 时不绘制返回按钮
 * @param onMinimize 窗口最小化回调
 * @param onToggleMaximize 窗口最大化/还原切换回调
 * @param maximized 当前是否处于最大化状态
 * @param onClose 窗口关闭回调
 * @param dragModifier 窗口拖拽区的宿主修饰符
 * @param transparentBackdrop 为 true 时使用半透背景以透出窗底系统材质
 */
@Composable
internal fun DesktopCommandBar(
    palette: DesktopChromePalette,
    breadcrumb: String,
    onBack: (() -> Unit)?,
    onMinimize: (() -> Unit)?,
    onToggleMaximize: (() -> Unit)?,
    maximized: Boolean,
    onClose: (() -> Unit)?,
    dragModifier: Modifier = Modifier,
    transparentBackdrop: Boolean = false,
    /**
     * 详情页：整窗背板已经铺在窗根，本栏不画自己的 page 底。
     * 只压一道上沿渐变保住面包屑可读性 —— 画实底就是在这里重新拉出一条 44dp 横线。
     */
    bareBackdrop: Boolean = false,
) {
    val glassSurface = com.venera.desktop.gallery.ui.DesktopGlass.currentSurface(palette)
    val barBg = if (transparentBackdrop) {
        glassSurface
    } else {
        palette.page
    }
    val barBrush: Brush = if (bareBackdrop) {
        Brush.verticalGradient(
            listOf(
                glassSurface,
                Color.Transparent,
            ),
        )
    } else {
        SolidColor(barBg)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(barBrush)
            .padding(end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // ── 左段：返回钮+ 面包屑（统一）──
        // 钮只承担动作、面包屑只念路径 —— 不再把上一级名塞进 999 胶囊（与「全圆只留头像」冲突）。
        Row(
            modifier = Modifier.padding(start = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (onBack != null) {
                DesktopBackButton(onClick = onBack)
            }
            Text(
                text = breadcrumb,
                fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                fontWeight = VeneraType.cardTitle.weight,
                color = palette.ink2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // ── 中段：拖动区（三钮在它右边，各自先收事件）──
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .then(dragModifier),
        )

        // ── 右段：反馈入口 + 三枚真窗控 ──
        com.venera.desktop.ui.DesktopFeedbackTriggerButton(
            tint = palette.chrome,
            hoverBackground = palette.hover,
        )
        if (onMinimize != null) {
            CommandButton(palette = palette, onClick = onMinimize) {
                Box(
                    Modifier
                        .width(10.dp)
                        .height(1.dp)
                        .background(palette.chrome),
                )
            }
        }
        if (onToggleMaximize != null) {
            CommandButton(palette = palette, onClick = onToggleMaximize) {
                if (maximized) {
                    MaximizeGlyphRestore(palette)
                } else {
                    MaximizeGlyph(palette)
                }
            }
        }
        if (onClose != null) {
            CommandButton(palette = palette, onClick = onClose, closeHover = true) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "关闭",
                    tint = palette.chrome,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

/** B 案返回胶囊：整枚可按，箭头 + 当前页题名，与面包屑同层。 */
@Composable
private fun CommandBackCapsule(
    palette: DesktopChromePalette,
    title: String,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        modifier = Modifier
            .height(30.dp)
            .widthIn(max = 260.dp)
            .clip(CommandPill)
            .background(if (hovered) palette.raised else palette.pill)
            .border(1.dp, palette.hairline, CommandPill)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = null,
            tint = palette.ink1,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = title,
            fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
            fontWeight = VeneraType.cardTitle.weight,
            color = palette.ink1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CommandButton(
    palette: DesktopChromePalette,
    onClick: () -> Unit,
    closeHover: Boolean = false,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .size(40.dp, 32.dp)
            .clip(RoundedCornerShape(0.dp))
            .background(
                when {
                    closeHover && hovered -> palette.danger
                    hovered -> palette.hover
                    else -> Color.Transparent
                }
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** 最大化（单方框描边）。 */
@Composable
private fun MaximizeGlyph(palette: DesktopChromePalette) {
    Box(
        Modifier
            .size(10.dp)
            .border(1.dp, palette.chrome),
    )
}

/** 还原（两枚错位小方框）。后一枚用实底盖住前一枚左下角，深/浅两档都读得出来。 */
@Composable
private fun MaximizeGlyphRestore(palette: DesktopChromePalette) {
    Box(contentAlignment = Alignment.BottomStart) {
        Box(
            Modifier
                .padding(start = 3.dp)
                .size(7.dp)
                .border(1.dp, palette.chrome),
        )
        Box(
            Modifier
                .padding(bottom = 3.dp)
                .size(7.dp)
                .background(palette.page)
                .border(1.dp, palette.chrome),
        )
    }
}
