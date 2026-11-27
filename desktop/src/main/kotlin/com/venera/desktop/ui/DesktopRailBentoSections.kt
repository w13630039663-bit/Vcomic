package com.venera.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.components.LocalUxPlatformServices
import com.venera.compose.components.VeneraNavTab
import com.venera.desktop.gallery.ui.DesktopChromePalette
import com.venera.desktop.gallery.ui.LocalChromePalette
import com.venera.desktop.settings.DesktopRailBlur

private val BentoTileShape = RoundedCornerShape(12.dp)
private val MonoNavShape = RoundedCornerShape(8.dp)

/**
 * 桌面侧栏展示用的核心导航 Tab 列表（过滤掉独立在二级入口展示的探索页）。
 */
internal fun desktopRailPrimaryTabs(): List<VeneraNavTab> =
    VeneraNavTab.entries.filter { it != VeneraNavTab.EXPLORE }

/**
 * 返回核心导航 Tab 在桌面侧栏的展示文案。
 */
internal fun desktopRailTabTitle(tab: VeneraNavTab): String =
    if (tab == VeneraNavTab.SEARCH) "漫画搜索" else tab.title

/**
 * 判断侧栏当前是否处于透视模糊玻璃模式（开启系统背景材质且侧栏模糊开关打开）。
 */
@Composable
internal fun rememberDesktopRailGlassActive(): Boolean {
    val railBlur by DesktopRailBlur.visible.collectAsState()
    return DesktopDwmBackdrop.active && railBlur
}

/**
 * 返回侧栏玻璃卡片的标准亮高光渐变边框画刷（上亮下收，模拟玻璃厚度斜边）。
 */
internal fun desktopRailGlassStrokeBrush(dark: Boolean): Brush = Brush.verticalGradient(
    colors = if (dark) {
        listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.05f))
    } else {
        listOf(Color.White.copy(alpha = 0.62f), Color.White.copy(alpha = 0.20f))
    },
)

/**
 * 返回侧栏通透状态胶囊/按钮的选中与悬停底色（保持透明玻璃感，不铺饱和实色底）。
 */
internal fun desktopRailGlassSurface(
    palette: DesktopChromePalette,
    glassActive: Boolean,
    selected: Boolean,
    hovered: Boolean,
): Color {
    if (!glassActive) {
        return when {
            selected -> palette.railSelected
            hovered -> palette.hover
            else -> Color.Transparent
        }
    }
    return if (palette.dark) {
        when {
            selected -> Color.White.copy(alpha = 0.13f)
            hovered -> Color.White.copy(alpha = 0.08f)
            else -> Color.Transparent
        }
    } else {
        when {
            selected -> Color.White.copy(alpha = 0.36f)
            hovered -> Color.White.copy(alpha = 0.22f)
            else -> Color.Transparent
        }
    }
}

/**
 * 桌面浅皮轨展开态顶部 2×2 Bento 核心磁贴导航区（全通透磨砂玻璃质感）。
 */
@Composable
internal fun DesktopRailBentoGrid(
    currentTab: VeneraNavTab?,
    anySecondarySelected: Boolean,
    onTabSelected: (VeneraNavTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalChromePalette.current
    val ux = LocalUxPlatformServices.current
    val glassActive = rememberDesktopRailGlassActive()
    val rows = remember { desktopRailPrimaryTabs().chunked(2) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        rows.forEach { rowTabs ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                rowTabs.forEach { tab ->
                    val selected = !anySecondarySelected && tab == currentTab
                    DesktopRailBentoTile(
                        tab = tab,
                        selected = selected,
                        palette = palette,
                        glassActive = glassActive,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (!selected) {
                                ux.hapticTap()
                                onTabSelected(tab)
                            }
                        },
                    )
                }
                if (rowTabs.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DesktopRailBentoTile(
    tab: VeneraNavTab,
    selected: Boolean,
    palette: DesktopChromePalette,
    glassActive: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    val targetBg = if (glassActive) {
        if (palette.dark) {
            when {
                selected -> Color.White.copy(alpha = 0.13f)
                hovered -> Color.White.copy(alpha = 0.095f)
                else -> Color.White.copy(alpha = 0.045f)
            }
        } else {
            when {
                selected -> Color.White.copy(alpha = 0.36f)
                hovered -> Color.White.copy(alpha = 0.28f)
                else -> Color.White.copy(alpha = 0.16f)
            }
        }
    } else {
        when {
            selected -> palette.railSelected
            hovered -> palette.hover
            else -> palette.card
        }
    }
    val bgColor by animateColorAsState(
        targetValue = targetBg,
        animationSpec = tween(durationMillis = 140),
        label = "BentoTileBg",
    )
    val dotAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "BentoTileDot",
    )

    val borderModifier = when {
        selected -> Modifier.border(
            width = 1.dp,
            color = palette.accent.copy(alpha = if (palette.dark) 0.55f else 0.45f),
            shape = BentoTileShape,
        )
        glassActive -> Modifier.border(
            width = 1.dp,
            brush = desktopRailGlassStrokeBrush(palette.dark),
            shape = BentoTileShape,
        )
        else -> Modifier.border(width = 1.dp, color = palette.hairline, shape = BentoTileShape)
    }

    val iconTint = when {
        selected -> palette.accent
        hovered -> palette.ink1
        else -> palette.ink2
    }
    val textTint = if (selected || hovered) palette.ink1 else palette.ink2

    Box(
        modifier = modifier
            .height(58.dp)
            .background(bgColor, BentoTileShape)
            .then(borderModifier)
            .clip(BentoTileShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (selected) tab.filledIcon else tab.outlinedIcon,
                    contentDescription = desktopRailTabTitle(tab),
                    tint = iconTint,
                    modifier = Modifier.size(17.dp),
                )
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .graphicsLayer { alpha = dotAlpha }
                        .clip(CircleShape)
                        .background(palette.accent),
                )
            }
            Text(
                text = desktopRailTabTitle(tab),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = textTint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 桌面浅皮轨展开态单色二级导航条目（通透选中态，无彩色方砖与按压缩放）。
 */
@Composable
internal fun DesktopRailMonoNavRow(
    icon: ImageVector,
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalChromePalette.current
    val glassActive = rememberDesktopRailGlassActive()
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    val bgColor by animateColorAsState(
        targetValue = desktopRailGlassSurface(palette, glassActive, selected, hovered),
        animationSpec = tween(durationMillis = 140),
        label = "MonoNavBg",
    )
    val selectedBorder = if (selected && glassActive) {
        Modifier.border(
            width = 0.75.dp,
            color = if (palette.dark) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.52f),
            shape = MonoNavShape,
        )
    } else {
        Modifier
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(31.dp)
            .background(bgColor, MonoNavShape)
            .then(selectedBorder)
            .clip(MonoNavShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = when {
                selected -> palette.accent
                hovered -> palette.ink1
                else -> palette.ink2
            },
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = title,
            fontSize = 12.5.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected || hovered) palette.ink1 else palette.ink2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
