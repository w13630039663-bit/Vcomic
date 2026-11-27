package com.venera.desktop.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.components.LocalUxPlatformServices
import com.venera.compose.ui.tokens.VeneraMotion
import com.venera.desktop.gallery.ui.LocalChromePalette
import com.venera.desktop.settings.DesktopLanguage
import com.venera.desktop.settings.DesktopRailWeatherService
import com.venera.desktop.settings.DesktopRailWeatherSnapshot
import com.venera.desktop.settings.DesktopThemeMode
import com.venera.desktop.settings.LocalDesktopLanguage
import com.venera.desktop.ui.components.DesktopPetSlot
import kotlin.math.abs

private val CelestialExpandedShape = RoundedCornerShape(12.dp)
private val CelestialCompactShape = RoundedCornerShape(10.dp)
private val SweepEasing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

/**
 * 桌宠停靠区（置于侧栏底部天象开关与用户底坞正上方）。
 */
@Composable
internal fun DesktopRailPetZone(modifier: Modifier = Modifier) {
    val palette = LocalChromePalette.current
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        Box(
            modifier = Modifier
                .padding(bottom = 4.dp)
                .size(width = 96.dp, height = 20.dp)
                .background(
                    Brush.radialGradient(colors = listOf(palette.accent.copy(alpha = 0.24f), Color.Transparent)),
                    shape = CircleShape,
                ),
        )
        DesktopPetSlot()
    }
}

/**
 * 侧栏用户卡片上方的星穹实时天气昼夜开关（支持展开 `224×48dp` 与折叠 `40×40dp` 双态）。
 */
@Composable
internal fun DesktopRailThemeSwitch(
    expanded: Boolean,
    modifier: Modifier = Modifier,
) {
    val ux = LocalUxPlatformServices.current
    val themeMode by DesktopThemeMode.mode.collectAsState()
    val weatherSnap by DesktopRailWeatherService.snapshot.collectAsState()
    val isEn = LocalDesktopLanguage.current == DesktopLanguage.EN
    val isDark = DesktopThemeMode.isDark(themeMode)
    val themeTrigger = LocalDesktopThemeTrigger.current

    val progress = remember { Animatable(if (isDark) 1f else 0f) }
    val sweepProgress = remember { Animatable(1f) }
    var switchOrigin by remember { mutableStateOf<Offset?>(null) }

    LaunchedEffect(isDark) {
        val target = if (isDark) 1f else 0f
        if (VeneraMotion.reduceMotion) {
            progress.snapTo(target)
            return@LaunchedEffect
        }
        if (abs(progress.value - target) > 0.01f) {
            sweepProgress.snapTo(0f)
            progress.animateTo(target, tween(durationMillis = 720, easing = LinearEasing))
        }
    }
    LaunchedEffect(sweepProgress.value) {
        if (sweepProgress.value < 0.01f && !VeneraMotion.reduceMotion) {
            sweepProgress.animateTo(1f, tween(durationMillis = 620, easing = SweepEasing))
        }
    }

    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.975f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "CelestialPressScale",
    )

    val animTimeSec = rememberRailSwitchClock(
        active = !VeneraMotion.reduceMotion && (progress.isRunning || sweepProgress.isRunning || hovered),
    )

    fun switchTo(nextMode: String) {
        ux.hapticTap()
        if (themeTrigger != null) {
            themeTrigger.switchMode(nextMode, switchOrigin)
        } else {
            DesktopThemeMode.writeBound(nextMode)
        }
    }

    if (!expanded) {
        CelestialSwitchBody(
            progress = progress.value,
            sweep = sweepProgress.value,
            weather = weatherSnap,
            timeSec = animTimeSec,
            hovered = hovered,
            compact = true,
            isEn = isEn,
            interaction = interaction,
            onGloballyPositioned = { switchOrigin = it },
            onClick = { switchTo(if (isDark) DesktopThemeMode.LIGHT else DesktopThemeMode.DARK) },
            modifier = modifier
                .size(40.dp)
                .graphicsLayer { scaleX = pressScale; scaleY = pressScale },
        )
        return
    }

    CelestialSwitchBody(
        progress = progress.value,
        sweep = sweepProgress.value,
        weather = weatherSnap,
        timeSec = animTimeSec,
        hovered = hovered,
        compact = false,
        isEn = isEn,
        interaction = interaction,
        onGloballyPositioned = { switchOrigin = it },
        onClick = { switchTo(if (isDark) DesktopThemeMode.LIGHT else DesktopThemeMode.DARK) },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .height(48.dp)
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale },
    )
}

@Composable
private fun rememberRailSwitchClock(active: Boolean): Float {
    var animTimeSec by remember { mutableFloatStateOf(0f) }
    var lastFrameNanos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(active) {
        if (!active) {
            lastFrameNanos = 0L
            return@LaunchedEffect
        }
        while (true) {
            withFrameNanos { nanos ->
                if (lastFrameNanos != 0L) {
                    animTimeSec += ((nanos - lastFrameNanos).coerceAtMost(50_000_000L)) / 1_000_000_000f
                }
                lastFrameNanos = nanos
            }
        }
    }
    return animTimeSec
}

@Composable
private fun CelestialSwitchBody(
    progress: Float,
    sweep: Float,
    weather: DesktopRailWeatherSnapshot,
    timeSec: Float,
    hovered: Boolean,
    compact: Boolean,
    isEn: Boolean,
    interaction: MutableInteractionSource,
    onGloballyPositioned: (Offset) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = if (compact) CelestialCompactShape else CelestialExpandedShape
    val strokeColor = if (hovered) Color.White.copy(alpha = 0.44f) else Color.White.copy(alpha = 0.24f)

    Box(
        modifier = modifier
            .onGloballyPositioned { onGloballyPositioned(it.boundsInRoot().center) }
            .clip(shape)
            .border(1.dp, strokeColor, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCelestialWeatherCanvas(
                progress = progress,
                sweep = sweep,
                weather = weather.type,
                timeSec = timeSec,
                hovered = hovered,
                compact = compact,
            )
        }
        if (!compact) {
            CelestialTextOverlay(progress = progress, weather = weather, isEn = isEn)
        }
    }
}

@Composable
private fun CelestialTextOverlay(
    progress: Float,
    weather: DesktopRailWeatherSnapshot,
    isEn: Boolean,
) {
    val shadow = TextStyle(shadow = Shadow(color = Color.Black.copy(alpha = 0.45f), blurRadius = 6f))
    val nightTitle = if (isEn) "${weather.type.nightLabelEn} · Dark" else "${weather.type.nightLabelZh} · 深色"
    val dayTitle = if (isEn) "${weather.type.dayLabelEn} · Light" else "${weather.type.dayLabelZh} · 浅色"
    val subLine = buildString {
        if (weather.city.isNotBlank()) {
            append(weather.city)
            weather.tempC?.let { append(" · ${it}°C") }
        } else {
            append(if (isEn) "Tap to switch" else "点击切换昼夜")
        }
    }
    val nightAlpha = ((progress - 0.35f) / 0.65f).coerceIn(0f, 1f)
    val dayAlpha = ((0.65f - progress) / 0.65f).coerceIn(0f, 1f)

    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.graphicsLayer {
                alpha = nightAlpha
                translationX = (1f - nightAlpha) * -10.dp.toPx()
            },
        ) {
            Text(nightTitle, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, style = shadow)
            Text(subLine, fontSize = 9.5.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.86f), style = shadow)
        }
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.graphicsLayer {
                alpha = dayAlpha
                translationX = (1f - dayAlpha) * 10.dp.toPx()
            },
        ) {
            Text(dayTitle, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, style = shadow)
            Text(subLine, fontSize = 9.5.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.86f), style = shadow)
        }
    }
}
