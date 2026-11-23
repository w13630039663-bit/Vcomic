package com.venera.desktop.settings

import com.venera.desktop.ui.theme.VeneraType
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.awt.Cursor

/**
 * 设置总览舱墙的玻璃皮肤件。
 *
 * 形状约定：
 *  - 舱 = 玻璃底 + 边框 + 顶部高光线 + 景深投影；hover 提亮边框不加缩放，选中才 1.02（spring 0.55 复用侧栏参数）。
 *  - 舱内三段式：标题行 → 内容槽（可视化/控件由调用方摆）→ 底部控件行。
 *  - 迷你图一律 Canvas 自绘，不引图表库；少于 4 个数据点用 [StatText]，不画线。
 *  - 文案尺照钉：标题 ≤6 字、副题 ≤15 字、无拟人无比喻。
 */

/** 舱体弹性参数（与侧栏双层玻璃轨同一颗 spring，参数不另起炉灶）。 */
private const val CAPSULE_SPRING_STIFFNESS = 380f
private const val CAPSULE_SPRING_DAMPING = 0.55f

/** 玻璃舱：设置总览的一格。 [selected] 时 1.02 放大 + 光晕由外层 halo 移动，hover 只提边框。 */
@Composable
fun GlassCapsule(
    icon: ImageVector,
    title: String,
    caption: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val glass = LocalSettingsGlass.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused = interaction.collectIsFocusedAsState().value
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.02f else 1f,
        animationSpec = spring(dampingRatio = CAPSULE_SPRING_DAMPING, stiffness = CAPSULE_SPRING_STIFFNESS),
        label = "capsuleScale",
    )
    val border = when {
        focused || selected -> glass.glassBorderFocus
        hovered -> glass.glassBorderFocus.copy(alpha = glass.glassBorderFocus.alpha * 0.7f)
        else -> glass.glassBorder
    }
    Column(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(20.dp))
            .background(glass.glassFill)
            .border(1.dp, border, RoundedCornerShape(20.dp))
            .drawBehind {
                // 顶部 1px 高光线 + 景深投影（blur 面只此一屏，画不起 RenderEffect 时这就是玻璃的最低档）。
                drawLine(
                    brush = Brush.horizontalGradient(
                        listOf(Color.Transparent, glass.glassHighlight, Color.Transparent),
                    ),
                    start = Offset(0f, 0.5f),
                    end = Offset(size.width, 0.5f),
                    strokeWidth = 1f,
                )
                if (hovered || selected) {
                    drawRoundRect(
                        color = glass.shadow,
                        topLeft = Offset(0f, 10f),
                        size = Size(size.width, size.height),
                        cornerRadius = CornerRadius(60f, 60f),
                    )
                }
            }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .focusable(interactionSource = interaction)
            .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)))
            .padding(horizontal = 16.dp, vertical = 13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = glass.primary,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = title,
                fontSize = VeneraType.bodySecondary.size, lineHeight = VeneraType.bodySecondary.lineHeight,
                fontWeight = VeneraType.cardTitle.weight,
                color = glass.textPrimary,
                maxLines = 1,
            )
        }
        Text(
            text = caption,
            fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
            color = glass.textSecondary,
            maxLines = 1,
            modifier = Modifier.padding(top = 1.dp),
        )
        content()
    }
}

/** 舱内控件行：左标签右控件，行高下沉到底部（[GlassCapsule] 的三段式第三段）。 */
@Composable
fun CapsuleRow(
    label: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    val glass = LocalSettingsGlass.current
    Row(
        modifier = modifier.fillMaxWidth().height(26.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
            color = glass.textSecondary,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

/** 舱内可视化行：沉在舱底，内容占满剩余宽（要撑满剩余高时调用方传 weight）。 */
@Composable
fun CapsuleViz(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        content()
    }
}

/** 大数字（stat 卡）：少于 4 个数据点的可视化替身。 */
@Composable
fun StatText(value: String, unit: String, modifier: Modifier = Modifier) {
    val glass = LocalSettingsGlass.current
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text(text = value, fontSize = VeneraType.statNumber.size, lineHeight = VeneraType.statNumber.lineHeight, fontWeight = VeneraType.cardTitle.weight, color = glass.textPrimary)
        Text(
            text = " $unit",
            fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
            color = glass.textTertiary,
            modifier = Modifier.padding(bottom = 2.dp),
        )
    }
}

/** 近 7 天迷你柱状图：<1000 点 Canvas 自绘；[activeIndex] 高亮今天。 */
@Composable
fun Bars7Chart(values: List<Float>, activeIndex: Int = -1, modifier: Modifier = Modifier) {
    val glass = LocalSettingsGlass.current
    val maxV = (values.maxOrNull() ?: 1f).coerceAtLeast(0.01f)
    Canvas(modifier = modifier.height(30.dp)) {
        if (values.isEmpty()) return@Canvas
        val gap = 3.dp.toPx()
        val barW = (size.width - gap * (values.size - 1)) / values.size
        values.forEachIndexed { i, v ->
            val h = (v / maxV) * size.height
            drawRoundRect(
                color = if (i == activeIndex) glass.primary else glass.primaryDim,
                topLeft = Offset(i * (barW + gap), size.height - h),
                size = Size(barW, h),
                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
            )
        }
    }
}

/** 单条堆叠构成条：缓存构成 / 规则构成那一路。 */
@Composable
fun StackBar(segments: List<Pair<Color, Float>>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(LocalSettingsGlass.current.primaryDim.copy(alpha = 0.35f)),
    ) {
        segments.forEach { (color, span) ->
            Box(Modifier.weight(span.coerceAtLeast(0.01f)).fillMaxHeight().background(color))
        }
    }
}

/** 启用状态点阵：总数格子里点亮前 [activeCount] 颗。 */
@Composable
fun DotMatrix(total: Int, activeCount: Int, modifier: Modifier = Modifier) {
    val glass = LocalSettingsGlass.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(total.coerceAtMost(24)) { i ->
            Box(
                Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (i < activeCount) glass.primary else glass.primaryDim.copy(alpha = 0.4f)),
            )
        }
    }
}

/** 凭据状态灯：三态（在 / 停 / 未配置）。 */
@Composable
fun StatusDot(state: CredentialLight, modifier: Modifier = Modifier) {
    val glass = LocalSettingsGlass.current
    val color = when (state) {
        CredentialLight.ON -> glass.ok
        CredentialLight.WARN -> glass.warn
        CredentialLight.OFF -> glass.textTertiary
    }
    Box(modifier.size(7.dp).clip(CircleShape).background(color))
}

/** 凭据状态灯的三档。 */
enum class CredentialLight { ON, WARN, OFF }

/** 最近收藏缩略位：真封面接进来前先摆玻璃槽位（Coil 封面在后续批喂）。 */
@Composable
fun MiniThumbs(count: Int, modifier: Modifier = Modifier) {
    val glass = LocalSettingsGlass.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) {
            Box(
                Modifier
                    .size(width = 26.dp, height = 36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(glass.primaryDim.copy(alpha = 0.3f))
                    .border(1.dp, glass.glassBorder, RoundedCornerShape(6.dp)),
            )
        }
    }
}

/** 加载骨架条：动态位三态的 loading 档（600ms 内到位就不闪）。 */
@Composable
fun SkelBar(modifier: Modifier = Modifier) {
    val glass = LocalSettingsGlass.current
    Box(
        modifier
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(Brush.horizontalGradient(listOf(glass.primaryDim.copy(alpha = 0.5f), glass.primaryDim))),
    )
}

/** 主题实时预览条：外观舱底部那条（深/浅双样 + 强调色条）。 */
@Composable
fun ThemePreviewStrip(darkOn: Boolean, modifier: Modifier = Modifier) {
    val glass = LocalSettingsGlass.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(glass.primaryDim.copy(alpha = 0.35f))
            .border(1.dp, glass.glassBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = "预览", fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight, color = glass.textSecondary)
        Box(
            Modifier
                .size(width = 34.dp, height = 22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (darkOn) Color(0xFF12141D) else Color(0xFFEEF0F8))
                .border(
                    width = if (darkOn) 2.dp else 1.dp,
                    color = if (darkOn) glass.primary else glass.glassBorder,
                    shape = RoundedCornerShape(6.dp),
                ),
        )
        Box(
            Modifier
                .size(width = 34.dp, height = 22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFFEEF0F8))
                .border(
                    width = if (!darkOn) 2.dp else 1.dp,
                    color = if (!darkOn) glass.primary else glass.glassBorder,
                    shape = RoundedCornerShape(6.dp),
                ),
        )
        Box(
            Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Brush.horizontalGradient(listOf(glass.primary, glass.primary.copy(alpha = 0.45f)))),
        )
    }
}
