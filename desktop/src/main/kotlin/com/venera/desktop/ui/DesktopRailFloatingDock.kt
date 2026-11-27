package com.venera.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewSidebar
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.components.LocalUxPlatformServices
import com.venera.compose.components.LocalVeneraSideBarDownloadAnchor
import com.venera.compose.components.VeneraSideBarAccountCard
import com.venera.compose.components.VeneraSideBarDownloadActivity
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.components.venera.VeneraCoverMask
import com.venera.desktop.gallery.ui.DesktopChromeColors
import com.venera.desktop.gallery.ui.DesktopChromePalette
import com.venera.desktop.gallery.ui.LocalChromePalette
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur

private val FloatingDockShape = RoundedCornerShape(12.dp)
private val DockInnerShape = RoundedCornerShape(8.dp)
private val BadgePillShape = RoundedCornerShape(999.dp)

/**
 * 桌面浅皮轨底部悬浮双层身份底坞（透光磨砂玻璃材质 + 穿过背部的 Haze 实时模糊）。
 */
@Composable
internal fun DesktopRailFloatingDock(
    downloadActivity: VeneraSideBarDownloadActivity?,
    onDownloadActivityClick: (() -> Unit)?,
    downloadsSelected: Boolean,
    accountCard: VeneraSideBarAccountCard?,
    onAccountCardClick: (() -> Unit)?,
    onSettingsSelected: (() -> Unit)?,
    settingsSelected: Boolean,
    onCollapse: () -> Unit,
    hazeState: HazeState? = null,
    modifier: Modifier = Modifier,
) {
    val palette = LocalChromePalette.current
    val ux = LocalUxPlatformServices.current
    val glassActive = rememberDesktopRailGlassActive()
    val blurActive = glassActive && !DesktopEffects.reduce
    val downloadAnchor = LocalVeneraSideBarDownloadAnchor.current
    val anchorPopKey = downloadAnchor?.popTick?.value ?: 0
    val anchorPopScale = remember { Animatable(1f) }
    LaunchedEffect(anchorPopKey) {
        if (anchorPopKey > 0) {
            anchorPopScale.animateTo(1.32f, tween(110))
            anchorPopScale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 850f))
        }
    }
    val flyEntryReporter = if (downloadAnchor != null) {
        Modifier.onGloballyPositioned { downloadAnchor.boundsInRoot.value = it.boundsInRoot() }
    } else {
        Modifier
    }

    val activeCount = downloadActivity?.activeCount ?: 0
    val dockSurface = when {
        glassActive -> if (palette.dark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.20f)
        else -> palette.card
    }
    val dividerColor = if (glassActive) {
        if (palette.dark) Color.White.copy(alpha = 0.10f) else Color(0xFF1A1D21).copy(alpha = 0.07f)
    } else {
        palette.hairline
    }
    val blurModifier = if (hazeState != null && blurActive) {
        val hazeTint = if (palette.dark) Color(0xFF14161C).copy(alpha = 0.14f) else Color.White.copy(alpha = 0.12f)
        val style = HazeBlurStyle {
            blurEnabled(true); blurRadius(26.dp); noiseFactor(0.03f)
            colorEffects(listOf(HazeColorEffect.tint(hazeTint)))
        }
        Modifier.hazeBlur(HazeInput.Sources(hazeState), style)
    } else {
        Modifier
    }
    val borderModifier = if (glassActive) {
        Modifier.border(width = 1.dp, brush = desktopRailGlassStrokeBrush(palette.dark), shape = FloatingDockShape)
    } else {
        Modifier.border(width = 1.dp, color = palette.hairline, shape = FloatingDockShape)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(FloatingDockShape)
                .then(blurModifier)
                .background(dockSurface)
                .then(borderModifier)
                .padding(6.dp),
        ) {
            if (accountCard != null) {
                DesktopRailDockAccountRow(
                    card = accountCard,
                    palette = palette,
                    glassActive = glassActive,
                    onClick = if (onAccountCardClick != null) {
                        { ux.hapticTap(); onAccountCardClick() }
                    } else {
                        null
                    },
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(dividerColor),
                )
                Spacer(Modifier.height(4.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (downloadActivity != null && onDownloadActivityClick != null) {
                    DesktopRailDockActionButton(
                        icon = Icons.Outlined.Download,
                        contentDescription = "下载与本地",
                        selected = downloadsSelected,
                        badgeCount = activeCount,
                        badgeScale = anchorPopScale.value,
                        palette = palette,
                        glassActive = glassActive,
                        modifier = Modifier.weight(1f).then(flyEntryReporter),
                        onClick = { ux.hapticTap(); onDownloadActivityClick() },
                    )
                }
                if (onSettingsSelected != null) {
                    DesktopRailDockActionButton(
                        icon = Icons.Outlined.Settings,
                        contentDescription = "设置",
                        selected = settingsSelected,
                        palette = palette,
                        glassActive = glassActive,
                        modifier = Modifier.weight(1f),
                        onClick = { ux.hapticTap(); onSettingsSelected() },
                    )
                }
                DesktopRailDockActionButton(
                    icon = Icons.AutoMirrored.Outlined.ViewSidebar,
                    contentDescription = "折叠导航栏",
                    selected = false,
                    palette = palette,
                    glassActive = glassActive,
                    modifier = Modifier.weight(1f),
                    onClick = { ux.hapticTap(); onCollapse() },
                )
            }
        }
    }
}

@Composable
private fun DesktopRailDockAccountRow(
    card: VeneraSideBarAccountCard,
    palette: DesktopChromePalette,
    glassActive: Boolean,
    onClick: (() -> Unit)?,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val hoverTarget = if (glassActive) {
        if (palette.dark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.24f)
    } else {
        palette.hover
    }
    val bgColor by animateColorAsState(
        targetValue = if (hovered && onClick != null) hoverTarget else Color.Transparent,
        animationSpec = tween(durationMillis = 140),
        label = "DockAccountBg",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor, DockInnerShape)
            .clip(DockInnerShape)
            .clickable(
                enabled = onClick != null,
                interactionSource = interaction,
                indication = null,
                onClick = { onClick?.invoke() },
            )
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DesktopRailDockAvatar(card = card, palette = palette)
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = card.name,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.ink1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (card.subtitle.isNotBlank()) {
                Text(
                    text = card.subtitle,
                    fontSize = 10.5.sp,
                    color = palette.ink3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onClick != null) {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = if (hovered) palette.ink1 else palette.ink3,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

@Composable
private fun DesktopRailDockAvatar(
    card: VeneraSideBarAccountCard,
    palette: DesktopChromePalette,
) {
    val dotHalo = if (palette.dark) Color(0xFF1C2028) else Color.White.copy(alpha = 0.72f)
    Box(modifier = Modifier.size(26.dp)) {
        if (!card.avatarUrl.isNullOrBlank()) {
            VeneraCover(
                url = card.avatarUrl,
                contentDescription = card.name,
                modifier = Modifier.size(24.dp).align(Alignment.Center).clip(CircleShape),
                mask = VeneraCoverMask.Visible,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF9C7CE8), Color(0xFF6B8EF4)),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = card.initial,
                    color = DesktopChromeColors.SurfaceWhite,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        if (card.logged) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(palette.ok)
                    .border(width = 1.dp, color = dotHalo, shape = CircleShape),
            )
        }
    }
}

@Composable
private fun DesktopRailDockActionButton(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    palette: DesktopChromePalette,
    glassActive: Boolean,
    badgeCount: Int = 0,
    badgeScale: Float = 1f,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bgColor by animateColorAsState(
        targetValue = desktopRailGlassSurface(palette, glassActive, selected, hovered),
        animationSpec = tween(durationMillis = 140),
        label = "DockActionBg",
    )
    val selectedBorder = if (selected) {
        Modifier.border(
            width = 1.dp,
            color = palette.accent.copy(alpha = if (palette.dark) 0.50f else 0.40f),
            shape = DockInnerShape,
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .height(30.dp)
            .background(bgColor, DockInnerShape)
            .then(selectedBorder)
            .clip(DockInnerShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = when {
                    selected -> palette.accent
                    hovered -> palette.ink1
                    else -> palette.ink2
                },
                modifier = Modifier.size(16.dp),
            )
            if (badgeCount > 0) {
                Spacer(Modifier.width(5.dp))
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = badgeScale
                            scaleY = badgeScale
                        }
                        .clip(BadgePillShape)
                        .background(palette.accent)
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (badgeCount > 99) "99+" else "$badgeCount",
                        color = DesktopChromeColors.SurfaceWhite,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 12.sp,
                        style = TextStyle(fontFeatureSettings = "tnum"),
                    )
                }
            }
        }
    }
}
