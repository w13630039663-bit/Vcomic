package com.venera.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.venera.desktop.settings.DesktopCanvasFrame

/**
 * 作品画布的承托壳：柔和落影 + 暗内衬 + 高光边 + 内衬留白。
 *
 * 落影自绘而不用 `Modifier.shadow`：低 elevation 投影在模糊亮底上会渲染成硬边灰框
 * （画廊详情页的既有结论）。调用方还需对图片自身做 [DesktopCanvasFrame.Spec.radiusDp] 圆角裁剪。
 */
internal fun Modifier.canvasFrameShell(spec: DesktopCanvasFrame.Spec): Modifier {
    val frameRadius = RoundedCornerShape((spec.radiusDp + spec.padDp).dp)
    return this
        .drawBehind { drawSoftShadow(spec) }
        .clip(frameRadius)
        .background(Color.Black.copy(alpha = 0.16f))
        .border(1.dp, Color.White.copy(alpha = 0.10f), frameRadius)
        .padding(spec.padDp.dp)
}

/**
 * 画布环境光：画作同图放大重糊，只在画布范围内向边缘衰减。
 *
 * 强度为 0 时调用方不该摆这一层——Coil 命中缓存也要走一次绘制，
 * 默认关掉就不必让每张详情页都付这份代价。
 */
@Composable
internal fun CanvasAmbientGlow(
    url: String?,
    intensity: Float,
    modifier: Modifier = Modifier,
) {
    if (intensity <= 0f || url.isNullOrBlank()) return
    val platformCtx = coil3.compose.LocalPlatformContext.current
    val glowModel = androidx.compose.runtime.remember(url, platformCtx) {
        coil3.request.ImageRequest.Builder(platformCtx)
            .data(url)
            // 44dp 重模糊背景用 256px 缩略图即可，避免原图进离屏 GPU 纹理。
            .size(256, 256)
            .build()
    }
    AsyncImage(
        model = glowModel,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = intensity * 0.62f
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .blur(44.dp)
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.Black, Color.Transparent),
                        center = center,
                        radius = size.minDimension * 0.74f,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            },
    )
}

/** 多层放大矩形叠出的柔影：外层大而淡、内层小而浓，近似高斯扩散。 */
private fun DrawScope.drawSoftShadow(spec: DesktopCanvasFrame.Spec) {
    val strength = spec.shadow
    if (strength <= 0f) return
    // 内衬为 0 时仍留一档扩散，否则「无框」档会连影子一起消失。
    val spread = spec.padDp.coerceAtLeast(6f) * 3.2f
    val offsetY = spec.padDp.coerceAtLeast(4f) * 2.2f
    val cornerRadiusPx = (spec.radiusDp + spec.padDp).dp.toPx()
    val layers = 12
    for (i in layers downTo 1) {
        val t = i / layers.toFloat()
        val grow = spread.dp.toPx() * t
        drawRoundRect(
            color = Color.Black.copy(alpha = strength * (1f - t) * (1f - t) * 0.12f),
            topLeft = Offset(-grow, -grow + offsetY.dp.toPx()),
            size = Size(size.width + grow * 2f, size.height + grow * 2f),
            cornerRadius = CornerRadius(cornerRadiusPx + grow, cornerRadiusPx + grow),
        )
    }
}
