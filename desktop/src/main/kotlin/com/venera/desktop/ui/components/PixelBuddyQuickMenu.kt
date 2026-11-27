package com.venera.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.venera.desktop.settings.DesktopPixelBuddyPrefs
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.ui.tokens.LocalVeneraDarkTheme
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.settings.DesktopPixelBuddyBus
import com.venera.desktop.settings.PixelBuddyCommand
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * 像素伙伴综合手势修饰符：
 * - 右键按下：打开快捷互动胶囊菜单；
 * - 左键短按（≤ 260ms）：开心蹦起并冒红心；
 * - 左键长按（≥ 260ms 不挪动）：进入顺毛挠下巴态，持续眯眼飘红心直到松手；
 * - 左键拖拽（位移 > 5dp）：拎起并随手势速度甩飞。
 */
internal fun Modifier.pixelBuddyInteractiveGestures(
    p: BuddyAgent,
    edgeX: Float,
    topY: Float,
    stageWProvider: () -> Float,
    stageHProvider: () -> Float,
    onOpenQuickMenu: (BuddyAgent, Offset) -> Unit,
): Modifier = this.pointerInput(p.id) {
    val slop = 5.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val startEvent = currentEvent
        if (startEvent.buttons.isSecondaryPressed) {
            down.consume()
            DesktopPixelBuddyBus.requestWake()
            onOpenQuickMenu(p, Offset(p.x, p.y))
            return@awaitEachGesture
        }
        DesktopPixelBuddyBus.requestWake()
        val downPos = down.position
        val downMs = System.currentTimeMillis()
        var dragging = false
        var petting = false
        var lastDx = 0f
        var lastDy = 0f
        while (true) {
            val event = awaitPointerEvent()
            val activeChange = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
            if (activeChange == null || !activeChange.pressed) {
                when {
                    dragging -> when {
                        p.y < topY + 36f && p.x > edgeX + 20f -> { p.wall = BuddyWall.TOP; p.y = topY; p.mode = BuddyMode.WALK }
                        p.x < edgeX + 32f -> { p.wall = BuddyWall.SIDE; p.x = edgeX; p.mode = BuddyMode.WALK }
                        else -> {
                            p.mode = BuddyMode.FALL
                            p.vx = (lastDx * 0.6f).coerceIn(-12f, 12f)
                            p.vy = (lastDy * 0.6f).coerceIn(-10f, 10f)
                            p.bounceCount = 0
                        }
                    }
                    petting -> {
                        p.mode = BuddyMode.HOP; p.hopPhase = 0f
                        p.icon = PixelIcon.HEART; p.iconTimer = 14
                    }
                    else -> {
                        p.mode = BuddyMode.HOP; p.hopPhase = 0f
                        p.icon = PixelIcon.HEART; p.iconTimer = 12
                    }
                }
                DesktopPixelBuddyBus.requestWake()
                break
            }
            val delta = activeChange.positionChange()
            val moved = hypot(activeChange.position.x - downPos.x, activeChange.position.y - downPos.y)
            if (!dragging && moved > slop) {
                dragging = true
                petting = false
                p.mode = BuddyMode.DRAG
                p.paintTargetKey = null
                p.stackHostId = -1
                p.hammerTimerMs = 0f
                DesktopPixelBuddyBus.requestWake()
            } else if (!dragging && !petting && System.currentTimeMillis() - downMs >= 260L) {
                petting = true
                p.mode = BuddyMode.PETTING
                p.petHeartAccumMs = 210f
                p.icon = PixelIcon.HEART
                p.iconTimer = 10
                DesktopPixelBuddyBus.requestWake()
            }
            if (dragging) {
                activeChange.consume()
                lastDx = delta.x; lastDy = delta.y
                p.x = (p.x + delta.x).coerceIn(edgeX, stageWProvider() - 20f)
                p.y = (p.y + delta.y).coerceIn(topY + 12f, stageHProvider() - 20f)
                DesktopPixelBuddyBus.requestWake()
            }
        }
    }
}

/**
 * 右键点击宠物原地弹出的极简胶囊快捷互动条。
 */
@Composable
internal fun DesktopPixelBuddyQuickMenu(
    anchor: Offset,
    target: BuddyAgent,
    activeCount: Int,
    hasPoop: Boolean,
    wandActive: Boolean,
    stageW: Float,
    stageH: Float,
    onClearPoops: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isDark = LocalVeneraDarkTheme.current
    val bg = if (isDark) Color(0xFF1F222A).copy(alpha = 0.96f) else Color(0xFFFAFBFD).copy(alpha = 0.96f)
    val border = if (isDark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.12f)
    val ink = if (isDark) Color(0xFFF1F3F8) else Color(0xFF1B1E26)
    val accent = VeneraTokens.color.primary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
    ) {
        val xPx = anchor.x.coerceIn(16f, (stageW - 310f).coerceAtLeast(16f))
        val yPx = (anchor.y - 46f).coerceIn(16f, (stageH - 60f).coerceAtLeast(16f))
        Row(
            modifier = Modifier
                .offset { IntOffset(xPx.roundToInt(), yPx.roundToInt()) }
                .clip(RoundedCornerShape(10.dp))
                .background(bg)
                .border(1.dp, border, RoundedCornerShape(10.dp))
                .padding(horizontal = 6.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QuickChip("喂鱼", ink, false) {
                DesktopPixelBuddyBus.trigger(PixelBuddyCommand.FEED); onDismiss()
            }
            if (activeCount >= 2) {
                QuickChip("叠起", ink, false) {
                    DesktopPixelBuddyBus.trigger(PixelBuddyCommand.STACK); onDismiss()
                }
            }
            QuickChip(if (wandActive) "收棒" else "逗猫棒", if (wandActive) accent else ink, wandActive) {
                DesktopPixelBuddyBus.toggleWandMode(); onDismiss()
            }
            QuickChip("打盹", ink, false) {
                target.stackHostId = -1; target.mode = BuddyMode.SLEEP; target.timer = 60
                target.icon = PixelIcon.ZZZ; target.iconTimer = 14
                onDismiss()
            }
            QuickChip(if (hasPoop) "铲屎" else "拉屎", ink, false) {
                if (hasPoop) onClearPoops() else DesktopPixelBuddyBus.trigger(PixelBuddyCommand.POOP)
                onDismiss()
            }
        }
    }
}

@Composable
private fun QuickChip(
    label: String,
    textColor: Color,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (highlighted) VeneraTokens.color.primary.copy(alpha = 0.16f) else Color.Transparent
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(text = label, fontSize = 11.5.sp, color = textColor, maxLines = 1)
    }
}

/**
 * 开启逗猫棒彩蛋时，把整窗软件内鼠标指针强制替换为大号像素逗猫棒。
 */
@Composable
internal fun Modifier.pixelBuddyWandHostCursor(): Modifier {
    val config by DesktopPixelBuddyPrefs.config.collectAsState()
    val wandMode by DesktopPixelBuddyBus.wandMode.collectAsState()
    if (!config.enabled || !wandMode) return this
    val wandIcon = remember { createPixelWandPointerIcon() } ?: return this
    return this.pointerHoverIcon(wandIcon, overrideDescendants = true)
}

/**
 * 供主窗体共同祖先容器挂载的非消费旁路光标观察者：只读上报窗口坐标（供好奇盯光标与逗猫棒追逐），绝不拦截子控件点击。
 */
@Composable
internal fun Modifier.pixelBuddyWandPointerObserver(): Modifier {
    val config by DesktopPixelBuddyPrefs.config.collectAsState()
    val wandMode by DesktopPixelBuddyBus.wandMode.collectAsState()
    if (!config.enabled) return this
    var originInWindow by remember { mutableStateOf(Offset.Zero) }
    return this
        .onGloballyPositioned { originInWindow = it.positionInWindow() }
        .pointerInput(wandMode) {
            awaitPointerEventScope {
                while (true) {
                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                    ev.changes.firstOrNull()?.let {
                        DesktopPixelBuddyBus.reportPointerPosition(originInWindow + it.position, syncState = wandMode)
                    }
                }
            }
        }
}
