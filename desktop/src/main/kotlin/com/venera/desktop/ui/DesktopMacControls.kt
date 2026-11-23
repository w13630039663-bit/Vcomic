package com.venera.desktop.ui

import com.venera.compose.ui.tokens.VeneraMotion
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.gallery.ui.DesktopTheme
import com.venera.desktop.gallery.ui.LocalChromePalette
import com.venera.desktop.ui.theme.VeneraType
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/**
 * macOS 皮控件套件（macos-style-稿 · 全站换皮）。
 *
 * 稿子的核心承诺：**同一细节全站只有一种做法**。分段 / 推压钮 / pop-up / 筛选 chip /
 * 图标钮 / 白卡架六颗件从这里取，禁再造形；选中态全站唯一口径 =
 * 分段与 chip 用**白滑块 / 白卡浮起**，列表与侧栏项用**系统蓝白字**。
 * 色值一律读 [DesktopChromePalette]（色值之家在 DesktopTheme.kt），本文件零字面量。
 * 影档：微影 sh-micro（0 1px 3px + 0.5px 描边）与浮层 sh-pop 两档，按稿封顶。
 */
private val MacRControl = RoundedCornerShape(6.dp)
private val MacRSegmentTrack = RoundedCornerShape(7.dp)
private val MacRSegmentThumb = RoundedCornerShape(5.5.dp)
private val MacRPop = RoundedCornerShape(9.dp)
private val MacMicroShadow = 1.dp
private val MacPopShadow = 8.dp

/** 按压 0.96 弹性反馈（全站按压口径，受控开关时启用）。 */
@Composable
private fun Modifier.macPressScale(pressed: Boolean): Modifier {
    val scale by animateFloatAsState(
        targetValue = if (pressed) VeneraTokens.current.chipPressedScale else 1f,
        animationSpec = VeneraMotion.tweenFast(),
        label = "macPress",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * 分段选择器（控件）：**一个轨 + 一个滑块**。
 *
 * 轨 28dp r7 `segmentTrack`（`large = true` 时 32dp），项 24dp r5.5（大档 28dp），
 * 选中 = 滑块浮起 + 0.5dp 发丝 + 浅色微影，底色与文字色均走 `VeneraMotion.tweenFast()` 过渡。
 */
@Composable
fun DesktopSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** 每个分段项前的 6dp 站徽色点（图库站青 / Pixiv 蓝）。空表 = 不画。 */
    leadingDots: List<Color?> = emptyList(),
    /** 兼容位：统一后轨恒 28dp / 项 24dp，本参数不再改变几何。 */
    compact: Boolean = false,
    /** 等分铺满（图片质量三档那类）；默认随内容收窄。 */
    fillWidth: Boolean = false,
    /** 大号档（设置页切换器要大）：轨 32 / 项 28 / 字 13。 */
    large: Boolean = false,
) {
    val trackHeight = if (large) 32.dp else 28.dp
    val itemHeight = if (large) 28.dp else 24.dp
    val itemPad = if (large) 14.dp else 12.dp
    val itemFont = if (large) 13.sp else 12.sp
    val palette = LocalChromePalette.current
    Row(
        modifier = modifier
            .then(if (fillWidth) Modifier.widthIn(max = 480.dp) else Modifier)
            .height(trackHeight)
            .clip(MacRSegmentTrack)
            .background(palette.segmentTrack)
            .padding(2.dp),
    ) {
        options.forEachIndexed { i, label ->
            val selected = i == selectedIndex
            val interaction = remember { MutableInteractionSource() }
            val hovered by interaction.collectIsHoveredAsState()
            val itemBg by animateColorAsState(
                targetValue = when {
                    selected -> palette.card
                    hovered -> palette.hover
                    else -> Color.Transparent
                },
                animationSpec = VeneraMotion.tweenFast(),
                label = "segBg",
            )
            val itemInk by animateColorAsState(
                targetValue = if (selected) palette.ink1 else palette.ink2,
                animationSpec = VeneraMotion.tweenFast(),
                label = "segInk",
            )
            Row(
                modifier = Modifier
                    .then(
                        if (fillWidth) Modifier.weight(1f)
                        else Modifier.widthIn(min = if (large) 64.dp else 56.dp),
                    )
                    .height(itemHeight)
                    .then(if (selected) Modifier.vShadow(MacMicroShadow, MacRSegmentThumb) else Modifier)
                    .clip(MacRSegmentThumb)
                    .background(itemBg)
                    .then(
                        if (selected) Modifier.border(0.5.dp, palette.hairline, MacRSegmentThumb)
                        else Modifier,
                    )
                    .hoverable(interaction)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable(interactionSource = interaction, indication = null) { onSelect(i) }
                    .padding(horizontal = itemPad),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val dot = leadingDots.getOrNull(i)
                if (dot != null) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(dot.copy(alpha = if (selected) 1f else 0.4f)),
                    )
                }
                Text(
                    text = label,
                    fontSize = itemFont,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = itemInk,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * 开关配色槽。默认走 [DesktopSwitchDefaults.colors]，独立暗色浮层（阅读器 HUD 等）可注入高对比配色。
 */
data class DesktopSwitchColors(
    val trackChecked: Color,
    val trackUnchecked: Color,
    val borderUnchecked: Color,
    val borderUncheckedHover: Color,
    val thumb: Color,
)

/** 开关配色工厂（全仓开关几何 36×20dp / 16dp 球不变，仅允许替换色槽）。 */
object DesktopSwitchDefaults {

    /** 常规桌面开关色槽（随 [LocalChromePalette] 深浅切换）。 */
    @Composable
    fun colors(): DesktopSwitchColors {
        val palette = LocalChromePalette.current
        return DesktopSwitchColors(
            trackChecked = palette.accent,
            trackUnchecked = palette.segmentTrack,
            borderUnchecked = palette.hairline,
            borderUncheckedHover = palette.ink3.copy(alpha = 0.35f),
            thumb = palette.card,
        )
    }

    /** 阅读器等暗色半透明浮层专用高对比色槽。 */
    fun hudDark(): DesktopSwitchColors = DesktopSwitchColors(
        trackChecked = DesktopTheme.AccentFill,
        trackUnchecked = Color.White.copy(alpha = 0.22f),
        borderUnchecked = Color.White.copy(alpha = 0.26f),
        borderUncheckedHover = Color.White.copy(alpha = 0.48f),
        thumb = Color.White,
    )
}

/**
 * 开关（全仓唯一标准件）：36×20 轨 + 16dp 球，140ms 平滑过渡，带悬停边框与禁用态。
 *
 * @param checked 当前开关状态
 * @param onCheckedChange 切换回调
 * @param enabled 是否可交互，false 时整件降至 45% 透明并关闭反馈
 * @param colors 色槽，默认取 [DesktopSwitchDefaults.colors]
 */
@Composable
fun DesktopSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: DesktopSwitchColors = DesktopSwitchDefaults.colors(),
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val knobX by animateDpAsState(
        targetValue = if (checked) 18.dp else 2.dp,
        animationSpec = tween(durationMillis = 140),
        label = "switchKnob",
    )
    val track by animateColorAsState(
        targetValue = if (checked) colors.trackChecked else colors.trackUnchecked,
        animationSpec = tween(durationMillis = 140),
        label = "switchTrack",
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            checked -> Color.Transparent
            hovered && enabled -> colors.borderUncheckedHover
            else -> colors.borderUnchecked
        },
        animationSpec = VeneraMotion.tweenFast(),
        label = "switchBorder",
    )
    Box(
        modifier = modifier
            .size(width = 36.dp, height = 20.dp)
            .clip(CircleShape)
            .background(track)
            .border(0.5.dp, borderColor, CircleShape)
            .then(if (enabled) Modifier.alpha(1f) else Modifier.alpha(0.45f))
            .hoverable(interaction, enabled = enabled)
            .then(if (enabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
            ) { onCheckedChange(!checked) },
    ) {
        Box(
            modifier = Modifier
                .offset(x = knobX, y = 2.dp)
                .size(16.dp)
                .clip(CircleShape)
                .background(colors.thumb)
                .vShadow(1.dp, CircleShape),
        )
    }
}

/**
 * 复选框（控件）：14dp / r4；选中 = accent 实底 + 白勾；悬停描边转 accent。
 * 全仓复选只有这一颗（`VeneraCheckbox` 是它的薄壳）。
 */
@Composable
fun DesktopCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val palette = LocalChromePalette.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier = modifier
            .size(14.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (checked) palette.accent else Color.Transparent)
            .border(
                0.5.dp,
                when {
                    checked -> Color.Transparent
                    hovered -> palette.accent
                    else -> palette.ink3.copy(alpha = 0.5f)
                },
                RoundedCornerShape(4.dp),
            )
            .then(if (enabled) Modifier.alpha(1f) else Modifier.alpha(0.45f))
            .hoverable(interaction, enabled = enabled)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
            ) { onCheckedChange(!checked) },
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


/**
 * 投影的唯一入口（黑边批）：**暗色档一律不画**。
 *
 * Skia 的 drop shadow 在暗底上就是一圈黑剪影 —— 用户报的「框后面的黑边」全是它：
 * 每张卡/胶囊各投一层，被父容器圆角一裁，就成了一列黑楔子。暗档的纵深交给
 * 0.5dp 发丝描边与色差（本仓视觉铁律），浅档保留投影（浅底上它才是"浮起"）。
 *
 * 用法：把 `Modifier.vShadow(e, shape)` 换成 `Modifier.vShadow(e, shape)`，参数不变。
 */
@Composable
fun Modifier.vShadow(elevation: androidx.compose.ui.unit.Dp, shape: androidx.compose.ui.graphics.Shape): Modifier =
    if (com.venera.compose.ui.tokens.LocalVeneraDarkTheme.current) {
        this
    } else {
        // 默认 shadow 是纯黑单层，浅底上会摊成一圈脏灰（悬停高 elevation 时尤甚）；
        // 换成极淡的环境/接触两色，"浮起"改由卡片自身描边承担。
        this.shadow(
            elevation = elevation,
            shape = shape,
            ambientColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.05f),
            spotColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.08f),
        )
    }

/**
 * 全仓统一按钮尺寸分档（保留详情页按钮质感，按场景适配高度与圆角）。
 *
 * - [Large]：40dp 高 / 10dp 圆角（详情页主行动、安装向导主动作、悬浮多选栏主/次行动）
 * - [Medium]：32dp 高 / 8dp 圆角（弹窗确认/取消、表单操作、设置行内按钮、多选栏常规操作）
 * - [Compact]：26dp 高 / 6dp 圆角（工具栏、卡片内动作、列表行尾、筛选菜单底栏）
 */
enum class DesktopButtonSize(
    val height: Dp,
    val cornerRadius: Dp,
    val defaultHorizontalPadding: Dp,
    val iconSize: Dp,
    val iconSpacing: Dp,
) {
    Large(height = 40.dp, cornerRadius = 10.dp, defaultHorizontalPadding = 14.dp, iconSize = 16.dp, iconSpacing = 6.dp),
    Medium(height = 32.dp, cornerRadius = 8.dp, defaultHorizontalPadding = 14.dp, iconSize = 15.dp, iconSpacing = 6.dp),
    Compact(height = 26.dp, cornerRadius = 6.dp, defaultHorizontalPadding = 12.dp, iconSize = 14.dp, iconSpacing = 5.dp),
}

/** 全仓统一按钮视觉变体。 */
enum class DesktopButtonVariant {
    /** 主色实底白字，无边框。 */
    Primary,
    /** 半透/抬升衬底 + 0.5dp 细描边（默认次钮）。 */
    Secondary,
    /** 危险红微底 + 危险细描边（支持 `filledDanger` 实底红）。 */
    Danger,
    /** 默认无框透明底，悬停浮出柔和衬底。 */
    Ghost,
}

/**
 * 按钮四态色槽，可通过 [DesktopButtonDefaults] 取常规色板或沉浸/独立叠层色板。
 */
data class DesktopButtonColors(
    val bg: Color,
    val bgHover: Color,
    val border: Color,
    val borderHover: Color,
    val content: Color,
    val contentHover: Color,
)

/** 全仓按钮配色工厂。 */
object DesktopButtonDefaults {

    /**
     * 标准桌面色槽（随 [LocalChromePalette] 深浅切换）。
     *
     * @param variant 按钮视觉变体
     * @param selected 是否处于激活/已选状态（如已收藏、已追更）
     * @param filledDanger 危险变体是否使用实底红填充（如确认卸载弹窗）
     */
    @Composable
    fun colors(
        variant: DesktopButtonVariant = DesktopButtonVariant.Secondary,
        selected: Boolean = false,
        filledDanger: Boolean = false,
    ): DesktopButtonColors {
        val palette = LocalChromePalette.current
        val primaryFill = if (palette.dark) DesktopTheme.AccentFill else palette.accent
        return when (variant) {
            DesktopButtonVariant.Primary -> DesktopButtonColors(
                bg = primaryFill,
                bgHover = primaryFill.copy(alpha = 0.88f),
                border = Color.Transparent,
                borderHover = Color.Transparent,
                content = Color.White,
                contentHover = Color.White,
            )
            DesktopButtonVariant.Danger -> if (filledDanger) {
                DesktopButtonColors(
                    bg = palette.danger,
                    bgHover = palette.danger.copy(alpha = 0.88f),
                    border = Color.Transparent,
                    borderHover = Color.Transparent,
                    content = Color.White,
                    contentHover = Color.White,
                )
            } else {
                DesktopButtonColors(
                    bg = palette.danger.copy(alpha = if (palette.dark) 0.14f else 0.10f),
                    bgHover = palette.danger.copy(alpha = if (palette.dark) 0.24f else 0.18f),
                    border = palette.danger.copy(alpha = 0.45f),
                    borderHover = palette.danger.copy(alpha = 0.72f),
                    content = palette.danger,
                    contentHover = palette.danger,
                )
            }
            DesktopButtonVariant.Ghost -> DesktopButtonColors(
                bg = Color.Transparent,
                bgHover = palette.hover,
                border = Color.Transparent,
                borderHover = palette.hairline,
                content = if (selected) palette.accent else palette.ink2,
                contentHover = if (selected) palette.accent else palette.ink1,
            )
            DesktopButtonVariant.Secondary -> if (selected) {
                DesktopButtonColors(
                    bg = primaryFill.copy(alpha = 0.14f),
                    bgHover = primaryFill.copy(alpha = 0.22f),
                    border = primaryFill.copy(alpha = 0.45f),
                    borderHover = primaryFill.copy(alpha = 0.68f),
                    content = palette.accent,
                    contentHover = palette.accent,
                )
            } else {
                DesktopButtonColors(
                    bg = if (palette.dark) Color.White.copy(alpha = 0.08f) else palette.elevated,
                    bgHover = if (palette.dark) Color.White.copy(alpha = 0.14f) else palette.card,
                    border = if (palette.dark) Color.White.copy(alpha = 0.16f) else palette.frame,
                    borderHover = if (palette.dark) Color.White.copy(alpha = 0.26f) else palette.ink3.copy(alpha = 0.35f),
                    content = palette.ink1,
                    contentHover = palette.ink1,
                )
            }
        }
    }

    /**
     * 深色沉浸大图 / 半透明浮层专用的详情页质感色槽（不受浅色页面模式翻白影响）。
     *
     * @param active 是否处于激活高亮态（如详情页已收藏/已点赞）
     * @param activeTint 激活高亮色，默认走当前主题危险/强调红
     */
    @Composable
    fun translucentOnDark(
        active: Boolean = false,
        activeTint: Color = LocalChromePalette.current.danger,
    ): DesktopButtonColors = if (active) {
        DesktopButtonColors(
            bg = activeTint.copy(alpha = 0.14f),
            bgHover = activeTint.copy(alpha = 0.24f),
            border = activeTint.copy(alpha = 0.45f),
            borderHover = activeTint.copy(alpha = 0.72f),
            content = activeTint,
            contentHover = activeTint,
        )
    } else {
        DesktopButtonColors(
            bg = Color.White.copy(alpha = 0.09f),
            bgHover = Color.White.copy(alpha = 0.16f),
            border = Color.White.copy(alpha = 0.16f),
            borderHover = Color.White.copy(alpha = 0.28f),
            content = Color.White.copy(alpha = 0.88f),
            contentHover = Color.White,
        )
    }
}

/**
 * 全仓唯一标准按钮（文本 + 可选前导图标版）。
 *
 * 具备完整五态（默认 / 悬停 / 按压 0.96 / 焦点 / 禁用）与异步加载锁定态，
 * 颜色与按压缩放均通过 [VeneraMotion.tweenFast] 平滑过渡。
 *
 * @param text 按钮文案
 * @param onClick 点击回调
 * @param size 尺寸分档（Large 40dp / Medium 32dp / Compact 26dp）
 * @param variant 视觉变体（Primary / Secondary / Danger / Ghost）
 * @param leadingIcon 可选前导图标
 * @param selected 是否为选中/激活状态
 * @param enabled false 时整体降至 45% 透明并关闭按压/悬停反馈
 * @param loading true 时锁定交互并展示进度圈
 * @param horizontalPadding 覆写水平内距；null 时按 [size] 自动取标准值
 * @param colors 颜色槽，默认由 [DesktopButtonDefaults.colors] 派生
 */
@Composable
fun DesktopButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: DesktopButtonSize = DesktopButtonSize.Medium,
    variant: DesktopButtonVariant = DesktopButtonVariant.Secondary,
    leadingIcon: ImageVector? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    loading: Boolean = false,
    horizontalPadding: Dp? = null,
    colors: DesktopButtonColors = DesktopButtonDefaults.colors(variant = variant, selected = selected),
) {
    val fontSize = if (size == DesktopButtonSize.Large) VeneraType.bodySecondary.size else VeneraType.aux.size
    val lineHeight = if (size == DesktopButtonSize.Large) VeneraType.bodySecondary.lineHeight else VeneraType.aux.lineHeight
    val fontWeight = when {
        variant == DesktopButtonVariant.Primary -> FontWeight.SemiBold
        size == DesktopButtonSize.Large -> FontWeight.Medium
        else -> VeneraType.control.weight
    }
    DesktopButton(
        onClick = onClick,
        modifier = modifier,
        size = size,
        variant = variant,
        selected = selected,
        enabled = enabled,
        loading = loading,
        horizontalPadding = horizontalPadding ?: if (variant == DesktopButtonVariant.Primary && size == DesktopButtonSize.Large) {
            18.dp
        } else if (leadingIcon != null && size == DesktopButtonSize.Large) {
            12.dp
        } else {
            size.defaultHorizontalPadding
        },
        colors = colors,
    ) {
        if (leadingIcon != null && !loading) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(size.iconSize),
            )
        }
        Text(
            text = text,
            fontSize = fontSize,
            lineHeight = lineHeight,
            fontWeight = fontWeight,
            maxLines = 1,
        )
    }
}

/**
 * 全仓唯一标准按钮（自定义槽版）。支持在内部组合文案、副标签或进度提示。
 *
 * @param onClick 点击回调
 * @param size 尺寸分档
 * @param variant 视觉变体
 * @param selected 是否为选中/激活状态
 * @param enabled 是否启用
 * @param loading 是否处于异步加载锁定状态
 * @param horizontalPadding 覆写水平内距
 * @param colors 颜色槽
 * @param content 行内内容，默认自动继承当前状态的文字/图标色
 */
@Composable
fun DesktopButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: DesktopButtonSize = DesktopButtonSize.Medium,
    variant: DesktopButtonVariant = DesktopButtonVariant.Secondary,
    selected: Boolean = false,
    enabled: Boolean = true,
    loading: Boolean = false,
    horizontalPadding: Dp = size.defaultHorizontalPadding,
    colors: DesktopButtonColors = DesktopButtonDefaults.colors(variant = variant, selected = selected),
    content: @Composable RowScope.() -> Unit,
) {
    val palette = LocalChromePalette.current
    val active = enabled && !loading
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(size.cornerRadius)

    val bgColor by animateColorAsState(
        targetValue = if (hovered && active) colors.bgHover else colors.bg,
        animationSpec = VeneraMotion.tweenFast(),
        label = "btnBg",
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            focused && active -> palette.accent
            hovered && active -> colors.borderHover
            else -> colors.border
        },
        animationSpec = VeneraMotion.tweenFast(),
        label = "btnBorder",
    )
    val contentColor by animateColorAsState(
        targetValue = if (hovered && active) colors.contentHover else colors.content,
        animationSpec = VeneraMotion.tweenFast(),
        label = "btnContent",
    )
    val borderWidth = if (focused && active) 1.5.dp else 0.5.dp

    Row(
        modifier = modifier
            .macPressScale(pressed && active)
            .then(
                if (variant == DesktopButtonVariant.Primary) {
                    Modifier.vShadow(2.dp, shape)
                } else if (variant == DesktopButtonVariant.Secondary) {
                    Modifier.vShadow(MacMicroShadow, shape)
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .background(bgColor)
            .then(
                if (borderColor != Color.Transparent) Modifier.border(borderWidth, borderColor, shape)
                else Modifier,
            )
            .then(if (enabled) Modifier.alpha(1f) else Modifier.alpha(0.45f))
            .focusable(enabled = active, interactionSource = interaction)
            .hoverable(interaction, enabled = active)
            .then(if (active) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
            .clickable(interactionSource = interaction, indication = null, enabled = active, onClick = onClick)
            .height(size.height)
            .padding(horizontal = horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(size.iconSpacing, Alignment.CenterHorizontally),
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(size.iconSize - 2.dp),
                    strokeWidth = 1.6.dp,
                    color = contentColor,
                )
            }
            content()
        }
    }
}

/**
 * 紧凑按钮（兼容入口）：26dp 高 / r6，已收编至 [DesktopButton] 视觉体系。
 */
@Composable
fun DesktopPushButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    /** 全仓钮归一：危险档（红字红图标 + 悬停红底），下载页删除类迁入。 */
    danger: Boolean = false,
) {
    val variant = when {
        danger -> DesktopButtonVariant.Danger
        primary -> DesktopButtonVariant.Primary
        else -> DesktopButtonVariant.Secondary
    }
    DesktopButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        size = DesktopButtonSize.Compact,
        variant = variant,
    )
}

/**
 * 全局返回钮（统一）：26dp r6 圆角钮 + `‹`，白底微影；
 * hover 5% 黑、按压 0.96。
 *
 * 为什么要独立一颗：浅皮命令带原来是 999 全圆胶囊（与「全圆只留头像」冲突），
 * 经典标题栏又走共享 `VeneraBackHandle` —— 两档**形不同**，用户认不出是同一个动作。
 * 现在两处共用这一颗，加上全屏页的浮动变体，全局返回只有一种形。
 * 一级页调用点传 `onBack == null` 时**整颗不摆**（不放死按钮）。
 */
@Composable
fun DesktopBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = "返回",
    /**
     * 全屏页（阅读器 / 看图）那一档：**32dp 圆形 + 暗玻璃 + 白箭头**，浮在画面左上。
     * 全屏页不摆命令带，所以返回不能挂在带子上 —— 这一档是同一颗件的第二种皮。
     */
    floating: Boolean = false,
) {
    val palette = LocalChromePalette.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val shape = if (floating) CircleShape else MacRControl
    Row(
        modifier = modifier
            .macPressScale(pressed)
            .size(if (floating) 32.dp else 26.dp)
            .then(if (floating) Modifier else Modifier.vShadow(MacMicroShadow, MacRControl))
            .clip(shape)
            .background(
                if (floating) {
                    Color(0x8C1C1C20)
                } else {
                    if (hovered) palette.hover else palette.elevated
                },
            )
            .border(
                0.5.dp,
                if (floating) Color.White.copy(alpha = 0.20f) else palette.hairline,
                shape,
            )
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = contentDescription,
            tint = if (floating) Color.White else palette.ink2,
            modifier = Modifier.size(if (floating) 15.dp else 14.dp),
        )
    }
}

/**
 * 紧凑按钮（内容槽兼容重载）：26dp 高 / r6，已收编至 [DesktopButton] 视觉体系。
 */
@Composable
fun DesktopPushButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val variant = if (danger) DesktopButtonVariant.Danger else DesktopButtonVariant.Secondary
    DesktopButton(
        onClick = onClick,
        modifier = modifier,
        size = DesktopButtonSize.Compact,
        variant = variant,
        enabled = enabled,
        content = content,
    )
}

/**
 * 下拉选择器（控件）：28dp / r6 / `elevated` 底 / 0.5dp 发丝 / `⌄` 单一把手。
 * 展开体复用 Mac 玻璃菜单（r9 + 26dp 行 + accent 胶囊），与右键菜单同源。
 * 旧 `⇅` 蓝双箭头退役 —— 全仓下拉只有这一种把手。
 */
@Composable
fun DesktopPopUpButton(
    label: String?,
    value: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 28.dp,
    optionEnabled: List<Boolean>? = null,
) {
    val palette = LocalChromePalette.current
    var expanded by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bgColor by animateColorAsState(
        targetValue = if (hovered) palette.card else palette.elevated,
        animationSpec = VeneraMotion.tweenFast(),
        label = "popupBg",
    )
    // 弹层锚点用按钮实测高度：Popup 的 alignment 是相对父布局的，
    // 旧值 BottomStart 会让菜单底边贴按钮底边、向上生长盖住按钮本身。
    var anchorHeightPx by remember { mutableIntStateOf(0) }
    Box(
        modifier = modifier.onGloballyPositioned { anchorHeightPx = it.size.height },
    ) {
        Row(
            modifier = Modifier
                .clip(MacRControl)
                .background(bgColor)
                .border(0.5.dp, palette.hairline, MacRControl)
                .hoverable(interaction)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(interactionSource = interaction, indication = null) { expanded = true }
                // 先定高再给水平内距（避免重演「padding 在前被 height 压扁」那一族）。
                .height(height)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!label.isNullOrBlank()) {
                Text(label, fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight, color = palette.ink3, maxLines = 1)
            }
            Text(
                text = value,
                fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                color = palette.ink2,
                maxLines = 1,
            )
            Icon(
                imageVector = Icons.Outlined.UnfoldMore,
                contentDescription = null,
                tint = palette.ink3,
                modifier = Modifier.size(12.dp),
            )
        }
        // 展开体走显式锚定 Popup 而不是 M3 DropdownMenu：后者把内容量在整窗约束里，
        // fillMaxWidth 的菜单项会顺着锚点链吃满整窗（设置页那枚撑爆即此因，IntrinsicSize 压不住）。
        // 对齐 = 选框正下方 TopStart：菜单从按钮下缘往下展开，不覆盖按钮。
        if (expanded) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(
                    x = 0,
                    y = anchorHeightPx + with(LocalDensity.current) { 4.dp.roundToPx() },
                ),
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
                onPreviewKeyEvent = { ev ->
                    if (ev.type == KeyEventType.KeyDown && ev.key == Key.Escape) {
                        expanded = false
                        true
                    } else false
                },
            ) {
                // `.background(color)` 不带 shape 会画出方角底，把 r9 圆角与 sh-pop 一起盖掉。
                androidx.compose.material3.Surface(
                    shape = MacRPop,
                    color = palette.elevated,
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, palette.hairline),
                    modifier = Modifier
                        .vShadow(MacPopShadow, MacRPop)
                        .background(palette.elevated, MacRPop),
                ) {
                    Column(
                        Modifier
                            .width(IntrinsicSize.Min)
                            .padding(vertical = 4.dp),
                    ) {
                        options.forEachIndexed { i, opt ->
                            val itemEnabled = optionEnabled?.getOrElse(i) { true } ?: true
                            MacMenuItem(
                                text = opt,
                                selected = i == selectedIndex,
                                enabled = itemEnabled,
                                onClick = {
                                    onSelect(i)
                                    expanded = false
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}



/** 菜单项高亮圆角（比菜单体 r9 小 4dp，同心）。 */
private val MacRMenuItem = RoundedCornerShape(5.dp)

/**
 * 菜单项（顶栏稿）：高 26dp、r5 内缩高亮。
 *
 * 为什么不用 Material3 `DropdownMenuItem`：它只能整行铺底色，选中态是一条**通到菜单边**的
 * 蓝矩形（用户原话"还是原生状态"）。这里改成内缩 5dp + r5 的蓝胶囊，与分段滑块同一套几何。
 */
@Composable
fun MacMenuItem(
    text: String,
    selected: Boolean = false,
    shortcut: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val palette = LocalChromePalette.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .height(26.dp)
            .clip(MacRMenuItem)
            .background(
                when {
                    selected -> palette.accent
                    hovered && enabled -> palette.hover
                    else -> Color.Transparent
                },
            )
            .then(if (enabled) Modifier.alpha(1f) else Modifier.alpha(0.45f))
            .hoverable(interaction, enabled = enabled)
            .then(if (enabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = if (selected) "✓" else "",
            fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
            color = if (selected) Color.White else Color.Transparent,
            modifier = Modifier.width(11.dp),
        )
        Text(
            text = text,
            fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) Color.White else palette.ink1,
            maxLines = 1,
        )
        if (shortcut != null) {
            Spacer(Modifier.weight(1f))
            Text(
                text = shortcut,
                fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                color = if (selected) Color.White.copy(alpha = 0.8f) else palette.ink3,
                maxLines = 1,
            )
        }
    }
}

/** 菜单分组头：10.5/700 ink3，字距 .04em（mac 菜单的分段标题）。 */
@Composable
fun MacMenuSection(title: String) {
    Text(
        text = title,
        fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
        fontWeight = VeneraType.cardTitle.weight,
        color = LocalChromePalette.current.ink3,
        letterSpacing = 0.4.sp,
        modifier = Modifier.padding(start = 11.dp, end = 11.dp, top = 6.dp, bottom = 4.dp),
    )
}

/** 菜单分隔线：0.5dp 发丝，左右内缩 9dp。 */
@Composable
fun MacMenuDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 9.dp, vertical = 4.dp)
            .height(0.5.dp)
            .background(LocalChromePalette.current.hairline),
    )
}

/**
 * 工具图标钮：32dp r6 无框，悬停 5.5% 黑。字形先把现有的单字符号（⟳ ⋯ ＋）收进来，
 * 后续接真图标库时只换内容不改壳。
 */
@Composable
fun DesktopIconToolButton(
    glyph: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DesktopIconToolButton(
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = modifier,
    ) {
        Text(glyph, fontSize = VeneraType.bodySecondary.size, lineHeight = VeneraType.bodySecondary.lineHeight, color = LocalChromePalette.current.ink2, maxLines = 1)
    }
}

/**
 * 图标钮的真图标档（顶栏稿）：壳与字形档完全一致，只把中间那颗字换成任意内容。
 * 顶栏的视图 / 刷新 / 更多 / 我的 四枚走这一档（Material 线性图标 14dp）。
 */
@Composable
fun DesktopIconToolButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val palette = LocalChromePalette.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier = modifier
            .macPressScale(pressed)
            // 「栏太小」二次复核：工具栏控件档 26 → 28 → 32，与 pop-up 同高。
            .size(32.dp)
            .clip(MacRControl)
            .background(if (hovered) palette.hover else Color.Transparent)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        content()
    }
}

/**
 * 筛选 chip：白底微影 r6；选中**浮起**（白卡 + sh-thumb + 发丝线 + 600），不用黑实底
 * （稿面控件套件明令「禁用黑实底」）。尾缀计数 10.5sp ink3。
 */
@Composable
fun DesktopFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: String? = null,
    /** 阅读器玻璃档：底改半透白（玻璃浮层上不能用实白卡底），其余三态同源。 */
    onGlass: Boolean = false,
) {
    val palette = LocalChromePalette.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier = modifier
            .then(if (selected) Modifier.vShadow(MacMicroShadow, MacRControl) else Modifier.macPressScale(pressed))
            .clip(MacRControl)
            // 严格还原设计稿：chip 底是**白渐**（linear-gradient(#fff,#F6F6F8)），
            // 不是平涂白。选中档更亮一档（#FBFBFD），靠白滑块 + 微影浮起，不用蓝实底。
            // onGlass 档（全仓 chip 归一）：阅读器浮层上半透白，不用实白。
            .then(
                if (onGlass) {
                    Modifier.background(Color.White.copy(alpha = if (selected) 0.22f else 0.12f))
                } else if (selected) {
                    Modifier.background(Brush.verticalGradient(listOf(palette.card, palette.card)))
                } else {
                    Modifier.background(Brush.verticalGradient(listOf(palette.card, palette.elevated)))
                },
            )
            .border(
                0.5.dp,
                when {
                    onGlass -> Color.White.copy(alpha = if (selected) 0.4f else 0.16f)
                    selected -> palette.ink2.copy(alpha = 0.35f)
                    else -> palette.hairline
                },
                MacRControl,
            )
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            text = text,
            fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = when {
                onGlass -> Color.White.copy(alpha = if (selected) 1f else 0.85f)
                selected -> palette.ink1
                else -> palette.ink2
            },
            maxLines = 1,
        )
        if (!count.isNullOrBlank()) {
            Text(
                count,
                fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                color = if (onGlass) Color.White.copy(alpha = 0.65f) else palette.ink3,
                maxLines = 1,
            )
        }
    }
}

/**
 * 白卡架（hero 带的全站替代形，首页/收藏共用）：elevated 白 + 微影 r10，
 * 左迷你封面堆叠 + 标题/副题 + 右端推压钮。`onAction == null` 时不摆钮（不放假把手）。
 */
@Composable
fun DesktopShelf(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    covers: List<@Composable () -> Unit> = emptyList(),
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val palette = LocalChromePalette.current
    Row(
        modifier = modifier
            .vShadow(MacMicroShadow, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .background(palette.elevated)
            .border(0.5.dp, palette.hairline, RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (covers.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                covers.take(4).forEach { it() }
            }
        }
        androidx.compose.foundation.layout.Column {
            Text(title, fontSize = VeneraType.bodySecondary.size, lineHeight = VeneraType.bodySecondary.lineHeight, fontWeight = VeneraType.cardTitle.weight, color = palette.ink1)
            Text(subtitle, fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight, color = palette.ink3)
        }
        if (actionText != null && onAction != null) {
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            DesktopPushButton(text = actionText, onClick = onAction, primary = true)
        }
    }
}

/**
 * 卡片微影（设计稿 `shMicro`：`0 1px 3px rgba(0,0,0,.10)` + `0 0 .5px rgba(0,0,0,.06)`）。
 *
 * Compose 没有 CSS 的 box-shadow（不支持"零偏移 + 0.5px 描边"那一层），
 * 用 1dp elevation + 调用点自带的发丝描边近似 —— 这是 Desktop 上最接近 Mac 卡片的组合。
 */
@Composable
fun Modifier.macCardShadow(shape: Shape): Modifier {
    // Compose Desktop 的 1dp drop shadow 在浅底上会渲染成硬边灰框，改用 0.5dp 发丝描边。
    val palette = LocalChromePalette.current
    return this.border(0.5.dp, palette.hairline, shape)
}
