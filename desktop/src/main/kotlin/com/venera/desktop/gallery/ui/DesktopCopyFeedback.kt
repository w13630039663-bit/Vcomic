package com.venera.desktop.gallery.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 「复制图片」按钮的四种状态。 */
internal enum class DesktopCopyState {
    /** 空闲，可以按。 */
    IDLE,

    /** 正在取字节与写剪贴板。 */
    BUSY,

    /** 已写进剪贴板。 */
    DONE,

    /** 没写成（地址缺、字节取不到、解不出位图、剪贴板写不进去）。 */
    FAILED,
}

/**
 * 复制按钮的状态与执行入口。
 *
 * @property state 当前展示态。
 * @property run 执行一次复制：block 返回 true 记成功，返回 false 或抛异常记失败。
 */
internal class DesktopCopyFeedback(
    val state: DesktopCopyState,
    val run: (suspend () -> Boolean) -> Unit,
)

/**
 * 构造并记忆复制反馈。
 *
 * 复制要取图与解码，按下到有结果会隔几百毫秒；不摆「复制中」用户会以为没反应而连按。
 * [DesktopCopyState.BUSY] 期间再次触发直接忽略，避免叠出多趟请求。
 *
 * @param holdMs 成功与失败态停留多久后回到空闲。
 */
@Composable
internal fun rememberDesktopCopyFeedback(holdMs: Long = 1_800L): DesktopCopyFeedback {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(DesktopCopyState.IDLE) }
    val running = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    return DesktopCopyFeedback(
        state = state,
        run = { block ->
            if (running.compareAndSet(false, true)) {
                scope.launch {
                    state = DesktopCopyState.BUSY
                    val ok = try {
                        block()
                    } catch (t: Throwable) {
                        false
                    }
                    state = if (ok) DesktopCopyState.DONE else DesktopCopyState.FAILED
                    delay(holdMs)
                    state = DesktopCopyState.IDLE
                    running.set(false)
                }
            }
        },
    )
}

/**
 * 复制状态的图标：空闲复制、复制中转圈、成功对勾、失败叉号。
 *
 * @param state 当前复制状态。
 * @param idleTint 空闲与复制中那一档的颜色。
 * @param doneTint 成功那一档的颜色。
 * @param failedTint 失败那一档的颜色。
 * @param modifier 尺寸与位置由调用方给（各屏按钮大小不同）。
 */
@Composable
internal fun DesktopCopyGlyph(
    state: DesktopCopyState,
    idleTint: Color,
    doneTint: Color,
    failedTint: Color,
    modifier: Modifier = Modifier,
) {
    // 只有真在复制才建无限动画：空闲也建会把帧钟钉在 60fps，整窗每帧重画。
    val angle = if (state == DesktopCopyState.BUSY) {
        val spin = rememberInfiniteTransition(label = "copySpin")
        val a by spin.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
            label = "copySpinAngle",
        )
        a
    } else {
        0f
    }
    Icon(
        imageVector = when (state) {
            DesktopCopyState.BUSY -> Icons.Outlined.Sync
            DesktopCopyState.DONE -> Icons.Rounded.Check
            DesktopCopyState.FAILED -> Icons.Outlined.Close
            DesktopCopyState.IDLE -> Icons.Outlined.ContentCopy
        },
        contentDescription = when (state) {
            DesktopCopyState.BUSY -> "正在复制"
            DesktopCopyState.DONE -> "已复制"
            DesktopCopyState.FAILED -> "复制失败"
            DesktopCopyState.IDLE -> "复制图片"
        },
        tint = when (state) {
            DesktopCopyState.BUSY, DesktopCopyState.IDLE -> idleTint
            DesktopCopyState.DONE -> doneTint
            DesktopCopyState.FAILED -> failedTint
        },
        modifier = modifier.graphicsLayer {
            rotationZ = angle
        },
    )
}
