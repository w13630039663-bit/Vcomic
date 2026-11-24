package com.venera.desktop.ui

// ⚠️ 废弃勿动：本组件只服务「经典」皮肤档，该档已停用（见 DesktopHomeSkin.CLASSIC）。
// 现行顶栏是 DesktopCommandBar；改外观、加开关一律去那边，不要在本文件上继续投入。

import com.venera.desktop.ui.theme.VeneraType
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.ui.tokens.VeneraTokens

/**
 * 自绘标题栏（系统白条退役）。
 *
 * ## 结构（深海夜航一族,46dp）
 *
 * `Logo + VENERA / 面包屑 …（空区整条可拖）… 上下文药丸 | 窗控三钮`
 *
 * - **整条是拖动区**（`WindowDraggableArea` 包住除了按钮以外的全部）；
 * 窗控三钮各自消费点击,不进拖动（窗控必真）。
 * - 面包屑吃装配点现成的 `desktopShellTitle`（单一出处,二级页不再有第二条 54dp 壳内顶栏 ——
 *   那条整条并进这一条）。
 * - 双击空白 = 最大化/还原（Windows 习惯）,由装配点递 `onToggleMaximize`。
 *
 * ## 为什么窗控在这里而状态在装配点
 *
 * 最小化/最大化/关闭三枚吃的是 **AWT window** 那一族（`window.isMaximized` 等）,
 * 这颗组件只管摆;真窗口操作在装配点（`VeneraDesktop.kt` 那颗 `Window` 作用域）递进来 ——
 * 组件不持窗口句柄（与「屏不碰盘」同一族纪律）。
 */
@Composable
internal fun DesktopWindowTitleBar(
    /** 面包屑正文（`desktopShellTitle(route, currentTab)` 的成品;装配点算好,这里不判路由）。 */
    breadcrumb: String,
    /** 左侧返回药丸;`null` = 主层级不摆。 */
    onBack: (() -> Unit)?,
    /** 右侧上下文药丸（搜索/设置）;空表不摆。 */
    actions: List<DesktopTitleBarAction>,
    /**
     * 全局搜索入口**并进标题栏中段**（field 造型 chip，点击跳搜索页）。
     * `null` = 不摆（中段整条仍是拖动区）。摆了它，装配点的「⌕ 搜索」上下文药丸就别再重复摆同一扇门。
     */
    onOpenSearch: (() -> Unit)? = null,
    /** 窗控三钮。逐枚可空 = 「今天没有真落点就不摆」（红线①:不摆装饰钮）。 */
    onMinimize: (() -> Unit)?,
    onToggleMaximize: (() -> Unit)?,
    maximized: Boolean,
    onClose: (() -> Unit)?,
    modifier: Modifier = Modifier,
    /**
     * 壳已在窗口根铺好整窗背板：本栏只是浮在它上面的一层，不再自绘实底。
     * 只留上沿压色保住面包屑可读性 —— 底色由窗口背板给，接缝无从谈起。
     */
    backdropFromShell: Boolean = false,
    /** 标题栏拖动区的宿主修饰（AWT/Nucleus 各自提供）。 */
    dragModifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp),
    ) {
        if (backdropFromShell) {
            TitleBarTopScrim(com.venera.compose.ui.tokens.LocalVeneraDarkTheme.current)
        } else {
            val topBarBlur by com.venera.desktop.settings.DesktopTopBarBlur.visible.collectAsState()
            val backdropTransparent = com.venera.desktop.ui.DesktopDwmBackdrop.active && topBarBlur
            // 与内容区/侧栏同源（page）+ 同一阶梯，三块玻璃才不会一深一浅。
            val palette = com.venera.desktop.gallery.ui.LocalChromePalette.current
            val titleBg = if (backdropTransparent) {
                com.venera.desktop.gallery.ui.DesktopGlass.surface(palette)
            } else {
                palette.page
            }
            Box(Modifier.matchParentSize().background(titleBg))
        }
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        // 左段:logo + 面包屑(可拖)
        Box(modifier = Modifier.fillMaxHeight().then(dragModifier)) {
            Row(
                modifier = Modifier.fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 左缘统一 12dp（全窗单一左缘，顶栏/窗条/内容轨同值）。
                Spacer(Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(tokens.color.primary.copy(alpha = 0.32f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "V",
                        fontSize = VeneraType.bodySecondary.size, lineHeight = VeneraType.bodySecondary.lineHeight,
                        fontWeight = VeneraType.heroTitle.weight,
                        color = tokens.color.primary,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "VENERA",
                    fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                    fontWeight = VeneraType.cardTitle.weight,
                    letterSpacing = 1.2.sp,
                    color = tokens.color.textTertiary,
                )
                Text(
                    text = " / ",
                    fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                    color = tokens.color.textTertiary,
                )
                if (onBack != null) {
                    // 统一：与浅皮命令带共用同一颗 26dp 圆角返回钮 ——
                    // 两档形一致，用户才认得出是同一个动作（旧共享件 VeneraBackHandle 退役）。
                    DesktopBackButton(onClick = onBack)
                }
                Text(
                    text = com.venera.desktop.settings.DesktopI18n.t(breadcrumb),
                    fontSize = VeneraType.bodySecondary.size, lineHeight = VeneraType.bodySecondary.lineHeight,
                    fontWeight = VeneraType.cardTitle.weight,
                    color = tokens.color.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 12.dp),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        // 中段:全局搜索入口（不可拖的 chip）+ 其余占满(可拖;双击最大化由装配点整条监)。
        if (onOpenSearch != null) {
            DesktopTitleBarSearchEntry(onOpenSearch = onOpenSearch)
            Spacer(Modifier.width(8.dp))
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .then(dragModifier),
        )
        // 右段上下文药丸与窗控（不可拖）：常驻淡槽 + hover 提亮 + 按压 0.96，与静态文字区分可点击性。
        actions.forEach { action ->
            val pillInteraction = remember { MutableInteractionSource() }
            val pillHovered by pillInteraction.collectIsHoveredAsState()
            val pillPressed by pillInteraction.collectIsPressedAsState()
            val pillScale by animateFloatAsState(if (pillPressed) 0.96f else 1f)
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = pillScale
                        scaleY = pillScale
                    }
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        tokens.color.surfaceVariant.copy(alpha = if (pillHovered) 0.9f else 0.45f),
                    )
                    .hoverable(pillInteraction)
                    .clickable(interactionSource = pillInteraction, indication = null) { action.onClick() }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = com.venera.desktop.settings.DesktopI18n.t(action.label),
                    fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                    color = tokens.color.textSecondary,
                )
            }
        }
        com.venera.desktop.ui.DesktopFeedbackTriggerButton(
            tint = tokens.color.textSecondary,
            hoverBackground = tokens.color.surfaceVariant.copy(alpha = 0.6f),
        )
        // 三枚齐全才摆窗控组（缺任一枚 = 装配点没接那一跳,整组不摆,不摆按不动的装饰钮）。
        if (onMinimize != null && onToggleMaximize != null && onClose != null) {
            WindowControlButtons(
                onMinimize = onMinimize,
                onToggleMaximize = onToggleMaximize,
                maximized = maximized,
                onClose = onClose,
            )
        }
    }
}
}

/** 顶栏右侧的一枚上下文药丸(装配点按所在页递;空表不摆)。 */
internal data class DesktopTitleBarAction(
    val label: String,
    val onClick: () -> Unit,
)

/** 标题栏中段的搜索入口（field 造型 chip，与宽屏首页设计稿 titlebar 同形；点击跳搜索页，不可拖）。 */
@Composable
private fun DesktopTitleBarSearchEntry(onOpenSearch: () -> Unit) {
    val tokens = VeneraTokens
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .width(220.dp)
            .height(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(tokens.color.surfaceVariant.copy(alpha = if (hovered) 0.85f else 0.45f))
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onOpenSearch)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "⌕",
                fontSize = VeneraType.bodySecondary.size, lineHeight = VeneraType.bodySecondary.lineHeight,
                color = tokens.color.textTertiary,
            )
            Text(
                text = com.venera.desktop.settings.DesktopI18n.t("搜索漫画 / 画廊", "Search Comics / Gallery"),
                fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                color = tokens.color.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 窗控三钮(Windows 标准落位;关闭 hover 红 —— 红 #E81123 是 Windows 惯例色,不进 token)。 */
@Composable
private fun WindowControlButtons(
    onMinimize: () -> Unit,
    onToggleMaximize: () -> Unit,
    maximized: Boolean,
    onClose: () -> Unit,
) {
    val tokens = VeneraTokens
    val symbolColor = tokens.color.textSecondary
    Row(modifier = Modifier.fillMaxHeight()) {
        WindowControlButton(symbol = "—", contentDescription = "最小化", tint = symbolColor, onClick = onMinimize)
        WindowControlButton(
            symbol = if (maximized) "❐" else "□",
            contentDescription = if (maximized) "还原" else "最大化",
            tint = symbolColor,
            onClick = onToggleMaximize,
        )
        WindowControlButton(
            symbol = "✕",
            contentDescription = "关闭",
            tint = symbolColor,
            hoverBackground = Color(0xFFE81123),
            hoverTint = Color.White,
            onClick = onClose,
        )
    }
}

@Composable
private fun WindowControlButton(
    symbol: String,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    hoverBackground: Color = Color.White.copy(alpha = 0.08f),
    hoverTint: Color = Color.White,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(46.dp)
            .background(if (hovered) hoverBackground else Color.Transparent)
            .hoverable(interaction)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = symbol,
            fontSize = VeneraType.bodySecondary.size, lineHeight = VeneraType.bodySecondary.lineHeight,
            color = if (hovered) hoverTint else tint,
        )
    }
}

/**
 * 顶栏上沿的可读性压色。
 *
 * 只压上沿、下沿全透明：下面接的是同一张糊图（壳铺的窗口级背板），
 * 半透明以外的任何实底都会在 y=46dp 重新画出一条横线。
 */
@Composable
private fun BoxScope.TitleBarTopScrim(darkTheme: Boolean) {
    Box(
        Modifier.matchParentSize().background(
            Brush.verticalGradient(
                listOf(
                    if (darkTheme) Color.Black.copy(alpha = 0.42f) else Color(0xFFF4F4F7).copy(alpha = 0.62f),
                    Color.Transparent,
                ),
            ),
        ),
    )
}
