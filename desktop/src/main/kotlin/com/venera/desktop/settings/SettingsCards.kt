package com.venera.desktop.settings

import com.venera.desktop.ui.vShadow
import com.venera.desktop.ui.theme.VeneraType
import com.venera.desktop.ui.DesktopSegmented
import com.venera.desktop.ui.DesktopSwitch

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.foundation.Image
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.awt.Cursor

/**
 * 设置总览的浅色卡片栅格皮肤（六轮定案）。
 *
 * 与 [GlassCapsule] 同一套交互：hover 提亮 + 焦点描边，选中/悬停共用弹性缩放参数，
 * 所以两代皮肤看起来是「同一套浮动动画」。颜色一律从 [LocalSettingsGlass] 读，
 * 深浅两档只换调色板，不换组件。
 */

/** 与玻璃舱同一颗 spring，参数不另起炉灶。 */
private const val CARD_SPRING_STIFFNESS = 380f
private const val CARD_SPRING_DAMPING = 0.55f

/** 卡片圆角：设计稿 24dp，控件 12dp。 */
private val CardRadius = 24.dp
private val ControlRadius = 12.dp

/**
 * 卡片：白底 + 单层弱阴影 + 24dp 圆角；悬停上浮 2dp 并轻微放大，
 * 与玻璃舱的 1.02 浮动同源。
 */
@Composable
fun SettingsCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    contentPadding: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val glass = LocalSettingsGlass.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused = interaction.collectIsFocusedAsState().value
    val scale by animateFloatAsState(
        targetValue = if (selected || hovered) 1.02f else 1f,
        animationSpec = spring(dampingRatio = CARD_SPRING_DAMPING, stiffness = CARD_SPRING_STIFFNESS),
        label = "settingsCardScale",
    )
    val lift by animateFloatAsState(
        targetValue = if (hovered) -2f else 0f,
        animationSpec = spring(dampingRatio = CARD_SPRING_DAMPING, stiffness = CARD_SPRING_STIFFNESS),
        label = "settingsCardLift",
    )
    val border = when {
        focused || selected -> glass.glassBorderFocus
        hovered -> glass.glassBorderFocus.copy(alpha = glass.glassBorderFocus.alpha * 0.7f)
        else -> glass.glassBorder
    }
    Column(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale; translationY = lift }
            .vShadow(if (hovered) 12.dp else 4.dp, RoundedCornerShape(CardRadius))
            .clip(RoundedCornerShape(CardRadius))
            .background(glass.glassFill)
            .then(if (border.alpha > 0f) Modifier.border(1.dp, border, RoundedCornerShape(CardRadius)) else Modifier)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else {
                    Modifier.hoverable(interaction)
                },
            )
            .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)))
            .padding(contentPadding),
        content = content,
    )
}

/** 卡内标题行：线性图标 + 标题 + 可选尾部。 */
@Composable
fun CardTitle(
    icon: ImageVector,
    title: String,
    caption: String? = null,
    trailing: @Composable () -> Unit = {},
) {
    val glass = LocalSettingsGlass.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = glass.textSecondary,
            modifier = Modifier.size(17.dp),
        )
        Text(title, fontSize = VeneraType.body.size, lineHeight = VeneraType.body.lineHeight, fontWeight = VeneraType.cardTitle.weight, color = glass.textPrimary)
        trailing()
    }
    if (caption != null) {
        Text(caption, fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight, color = glass.textTertiary, modifier = Modifier.padding(top = 2.dp))
    }
}

/** 卡内主数字：34sp 锚点 + 弱色单位。 */
@Composable
fun CardStat(value: String, unit: String? = null, modifier: Modifier = Modifier) {
    val glass = LocalSettingsGlass.current
    Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
        Text(value, fontSize = VeneraType.statDisplay.size, lineHeight = VeneraType.statDisplay.lineHeight, fontWeight = VeneraType.cardTitle.weight, color = glass.textPrimary, letterSpacing = (-1).sp)
        if (unit != null) {
            Text(unit, fontSize = VeneraType.bodySecondary.size, lineHeight = VeneraType.bodySecondary.lineHeight, color = glass.textTertiary, modifier = Modifier.padding(start = 4.dp, bottom = 5.dp))
        }
    }
}

/** 7 天 sparkline：纯 Canvas 自绘，不引图表库。 */
@Composable
fun Sparkline(
    values: List<Float>,
    modifier: Modifier = Modifier,
    highlightIndex: Int = -1,
) {
    val glass = LocalSettingsGlass.current
    androidx.compose.foundation.Canvas(modifier = modifier.height(34.dp).fillMaxWidth()) {
        if (values.isEmpty()) return@Canvas
        val barWidth = (size.width - (values.size - 1) * 3.dp.toPx()) / values.size
        values.forEachIndexed { i, v ->
            val left = i * (barWidth + 3.dp.toPx())
            drawRoundRect(
                color = if (i == highlightIndex) glass.primary else glass.track,
                topLeft = androidx.compose.ui.geometry.Offset(left, size.height * (1f - v.coerceIn(0f, 1f))),
                size = androidx.compose.ui.geometry.Size(barWidth, size.height * v.coerceIn(0f, 1f)),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx()),
            )
        }
    }
}

/** 环形占比：conic 用分段弧自绘，中心留空显示主读数。 */
@Composable
fun Donut(
    segments: List<Pair<Color, Float>>,
    center: String,
    modifier: Modifier = Modifier,
) {
    val glass = LocalSettingsGlass.current
    Box(modifier = modifier.size(88.dp), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(modifier = Modifier.size(88.dp)) {
            var start = -90f
            val stroke = 14.dp.toPx()
            val inset = stroke / 2f
            segments.forEach { (color, fraction) ->
                val sweep = fraction * 360f
                drawArc(
                    color = color,
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                    size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke),
                )
                start += sweep
            }
        }
        Text(center, fontSize = VeneraType.sectionTitle.size, lineHeight = VeneraType.sectionTitle.lineHeight, fontWeight = VeneraType.cardTitle.weight, color = glass.textPrimary)
    }
}

/** 图例点。 */
@Composable
fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(label, fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight, color = LocalSettingsGlass.current.textSecondary, modifier = Modifier.padding(start = 6.dp))
    }
}

/** 状态点：正常 / 缓慢 / 超时。 */
@Composable
fun StatusDot(state: String, modifier: Modifier = Modifier) {
    val glass = LocalSettingsGlass.current
    val color = when (state) {
        "warn" -> glass.warn
        "fail" -> glass.danger
        else -> glass.ok
    }
    Box(modifier.size(8.dp).clip(CircleShape).background(color))
}

/** 状态标签胶囊。 */
@Composable
fun StatusTag(text: String, ok: Boolean = false, modifier: Modifier = Modifier) {
    val glass = LocalSettingsGlass.current
    val bg = if (ok) glass.ok.copy(alpha = 0.14f) else glass.glassFill
    val fg = if (ok) glass.ok else glass.textSecondary
    Text(
        text,
        fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
        fontWeight = VeneraType.control.weight,
        color = fg,
        modifier = modifier
            .clip(RoundedCornerShape(ControlRadius))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/**
 * 设置卡内分段（薄壳，控件）：几何与状态全在 [DesktopSegmented]。
 * 卡内这一档历来等分铺满（三档主题），故传 `fillWidth = true`。
 */
@Composable
fun CardSegmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    DesktopSegmented(
        options = options,
        selectedIndex = selected,
        onSelect = onSelect,
        modifier = modifier,
        // 设置页切换器要更大，且随内容收窄（不再拉满整行）。
        large = true,
    )
}



/** 设置卡内开关（薄壳，控件）：几何与色档全在 [DesktopSwitch]。 */
@Composable
fun CardSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    DesktopSwitch(checked = checked, onCheckedChange = onCheckedChange, modifier = modifier)
}



/** 源状态行：徽标缩写 + 名称/延迟 + 三态点。 */
@Composable
fun SourceRow(
    badge: String,
    name: String,
    detail: String,
    state: String,
    modifier: Modifier = Modifier,
) {
    val glass = LocalSettingsGlass.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ControlRadius))
            .background(glass.primaryDim.copy(alpha = 0.14f))
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.size(24.dp).clip(RoundedCornerShape(7.dp)).background(glass.glassFill),
            contentAlignment = Alignment.Center,
        ) {
            Text(badge, fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight, fontWeight = VeneraType.cardTitle.weight, color = glass.textSecondary)
        }
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight, fontWeight = VeneraType.cardTitle.weight, color = glass.textPrimary, maxLines = 1)
            Text(detail, fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight, color = glass.textTertiary, maxLines = 1)
        }
        StatusDot(state)
    }
}

/** 快捷入口行：线性图标 + 名称 + 说明 + 右箭头。 */
@Composable
fun QuickRow(
    icon: ImageVector,
    title: String,
    caption: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = LocalSettingsGlass.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (hovered) glass.primaryDim.copy(alpha = 0.18f) else Color.Transparent)
            .hoverable(interaction)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = glass.primary, modifier = Modifier.size(17.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = VeneraType.bodySecondary.size, lineHeight = VeneraType.bodySecondary.lineHeight, fontWeight = VeneraType.cardTitle.weight, color = glass.textPrimary, maxLines = 1)
            Text(caption, fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight, color = glass.textTertiary, maxLines = 1)
        }
        Text("›", fontSize = VeneraType.cardTitle.size, lineHeight = VeneraType.cardTitle.lineHeight, color = glass.textTertiary)
    }
}

/**
 * 贡献者头像：优先用内嵌的 GitHub 真头像（base64 → Skia 解码），
 * 取不到或解码失败才回落姓名首字色块。与 [GlassCapsule] 同一套 48dp 圆形。
 */
@Composable
fun ContributorAvatar(
    initials: String,
    color: Color,
    modifier: Modifier = Modifier,
    githubLogin: String? = null,
) {
    val bitmap: androidx.compose.ui.graphics.ImageBitmap? = remember(githubLogin) {
        githubLogin?.let { login ->
            ContributorAvatars.bytesOf(login)?.let { bytes ->
                runCatching {
                    org.jetbrains.skia.Image.makeFromEncoded(bytes).use { it.toComposeImageBitmap() }
                }.getOrNull()
            }
        }
    }
    Box(
        modifier.size(48.dp).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "$githubLogin 的头像",
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.size(48.dp),
            )
        } else {
            Text(initials, fontSize = VeneraType.body.size, lineHeight = VeneraType.body.lineHeight, fontWeight = VeneraType.cardTitle.weight, color = Color.White)
        }
    }
}
