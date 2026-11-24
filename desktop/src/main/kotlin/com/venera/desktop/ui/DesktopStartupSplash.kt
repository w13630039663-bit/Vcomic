package com.venera.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.ui.tokens.VeneraTokens

/**
 * 启动动画层：铺满窗口的实色遮罩，中央图标做一次弹入，随后整层淡出。
 *
 * 遮罩必须不透明 —— 窗口本身是透明的，留一点 alpha 会透出桌面。
 * 收起后从组合里移除，动画因此只在首次进入时播一次。
 *
 * @param visible false 时播 160ms 淡出；调用方负责在首帧稳定后把它置 false
 * @param dark 决定遮罩底色取深色档还是浅色档
 * @param modifier 只用于承载层级，调用方靠它把本层压在主界面之上
 */
@Composable
internal fun DesktopStartupSplash(
    visible: Boolean,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    // 缩放是分段回弹（冲过头再回落），透明度只在前 38% 里淡入，之后恒 1。
    val scale = remember { Animatable(0.72f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        alpha.animateTo(1f, animationSpec = tween(durationMillis = 274, easing = FastOutSlowInEasing))
    }

    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = keyframes {
                durationMillis = 720
                0.72f at 0 using FastOutSlowInEasing
                1.045f at 274
                0.985f at 418
                1.008f at 547
                1f at 720
            },
        )
    }

    AnimatedVisibility(
        visible = visible,
        enter = EnterTransition.None,
        exit = fadeOut(tween(durationMillis = 160)),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (dark) tokens.color.background else tokens.color.surface),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Image(
                    painter = androidx.compose.ui.res.painterResource("vcomic_icon.png"),
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    modifier = Modifier
                        .size(96.dp)
                        .graphicsLayer {
                            scaleX = scale.value
                            scaleY = scale.value
                            this.alpha = alpha.value
                        }
                        .clip(RoundedCornerShape(24.dp)),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "VCOMIC",
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 3.sp,
                    color = tokens.color.textTertiary.copy(alpha = 0.85f),
                    modifier = Modifier.graphicsLayer { this.alpha = alpha.value },
                )
            }
        }
    }
}
