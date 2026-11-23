package com.venera.desktop

import com.venera.desktop.ui.vShadow
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.venera.compose.components.VeneraSideBarDownloadAnchor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 「卡片 → 侧栏下载中心」飞入动效（设计稿 v3 §fly 那一曲）。
 *
 * ## 为什么是 `Animatable` 而不是 `animate*AsState`
 * 这是一张**一次性单程票**：按下"下载/存图成功"才存在，落地即销毁 —— 没有"目标态"可以跟着走。
 * 目标态 API 全都要一个持续存在的状态值，这里塞进去就得再造"票的存在性"那枚布尔，反而更绕。
 * `Animatable` 的命令式一趟（snapTo → animateTo → 完成回调）正好就是这件事的形状。
 *
 * ## 坐标的三段账（哪段在哪个空间，混一段就是歪的）
 * - 起点：宿主 Box 上的 pointer 观察者记下的**本容器局部**点击位（不吞事件，`requireUnconsumed=false`）；
 * - 终点：侧栏共享件经 [VeneraSideBarDownloadAnchor] 发布的下载中心入口 **composition root** bounds
 *   （桌面单窗，root 坐标即窗口坐标）—— 叠层里换算成本容器局部（减宿主 root 原点，那原点由壳
 *   在 `onGloballyPositioned` 里量好递进来）；
 * - 叠加层自己：`BoxScope` 扩展（放错容器直接编不过）；坐宿主 Box 的**第一个孩子**，
 *   靠 `zIndex(100f)` 浮到全部孩子之上（Compose 的 zIndex 同时管绘制序与命中序，
 *   而叠层自己一颗指针都不吃 —— 胶囊飞过之处下面的按钮照样能按）。
 *
 * ## 减弱动效
 * 主人批 7 会开那枚全局开关；在那之前不自造一枚没人读的闸（假开关纪律），曲线上手的就是设计稿那一条：
 * `cubic-bezier(.5, 0, .15, 1)` 560ms + 二次贝塞尔弧线 + 尾段缩到 20% 没入。
 */
/** 一张在飞的票。origin = 宿主容器局部坐标；coverUrl 空 = 纯色胶囊。 */
data class DesktopFlyTicket(
    val id: Long,
    val origin: Offset,
    val coverUrl: String?,
    val isComic: Boolean,
)

/** 飞入状态：同一时刻只飞一张，新票换入（连点 = latest wins，尾迹不叠尾迹）。 */
@Stable
internal class DesktopFlyState {
    var current by mutableStateOf<DesktopFlyTicket?>(null)
        private set
    private var lastId = 0L

    fun launch(origin: Offset, coverUrl: String?, isComic: Boolean) {
        current = DesktopFlyTicket(++lastId, origin, coverUrl, isComic)
    }

    /** 只撤自己那一张（动画收尾与"换票"撞车时，旧票不许撤掉新票）。 */
    fun finish(id: Long) {
        if (current?.id == id) current = null
    }
}

private const val FLY_DURATION_MS = 560
private val FLY_CURVE = CubicBezierEasing(0.5f, 0f, 0.15f, 1f)

/** 二次贝塞尔取点。 */
private fun quadraticPoint(p0: Offset, p1: Offset, p2: Offset, t: Float): Offset {
    val inv = 1f - t
    return Offset(
        inv * inv * p0.x + 2f * inv * t * p1.x + t * t * p2.x,
        inv * inv * p0.y + 2f * inv * t * p1.y + t * t * p2.y,
    )
}

/**
 * 叠层本体（宿主 Box 最后一个孩子）。锚没报到（侧栏那一格还没组合）⇒ 这张票直接作废，
 * 不演"不知道飞去哪"的动画；落地 ⇒ [onLanded]（壳拿它加 [VeneraSideBarDownloadAnchor.popTick]，
 * 角标弹跳由共享件自己吃那枚 tick）。
 */
@Composable
internal fun BoxScope.DesktopFlyOverlay(
    state: DesktopFlyState,
    anchor: VeneraSideBarDownloadAnchor,
    hostTopLeftInRoot: Offset,
    onLanded: () -> Unit,
) {
    val ticket = state.current ?: return
    val bounds = anchor.boundsInRoot.value
    val progress = remember(ticket.id) { Animatable(0f) }
    LaunchedEffect(ticket.id, bounds) {
        if (bounds == null) {
            state.finish(ticket.id)
            return@LaunchedEffect
        }
        progress.animateTo(1f, tween(FLY_DURATION_MS, easing = FLY_CURVE))
        onLanded()
        state.finish(ticket.id)
    }
    if (bounds == null) return
    val t = progress.value
    val start = ticket.origin
    val end = bounds.center.minus(hostTopLeftInRoot)
    val dx = end.x - start.x
    val dy = end.y - start.y
    val arcRise = (hypot(dx, dy) * 0.16f).coerceIn(48f, 168f)
    val control = Offset((start.x + end.x) / 2f, min(start.y, end.y) - arcRise)
    val pos = quadraticPoint(start, control, end, t)
    val scale = 1f - 0.8f * t
    val alpha = if (t < 0.86f) 1f else ((1f - t) / 0.14f).coerceIn(0f, 1f)
    Box(modifier = Modifier.matchParentSize().zIndex(100f)) {
        val shape = RoundedCornerShape(if (ticket.isComic) 14.dp else 12.dp)
        Box(
            modifier = Modifier
                // 孩子默认钉在容器 (0,0) —— 位置整趟走 graphicsLayer：平移到"中心=pos"再原地缩。
                .graphicsLayer {
                    translationX = pos.x - size.width / 2f
                    translationY = pos.y - size.height / 2f
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                }
                .vShadow(12.dp, shape)
                .clip(shape)
                .background(Color(0xFF15202B))
                .size(
                    width = if (ticket.isComic) 124.dp else 92.dp,
                    height = if (ticket.isComic) 78.dp else 92.dp,
                ),
        ) {
            if (ticket.coverUrl != null) {
                // 同一张封面/预览刚在卡上画过 —— coil 内存缓存命中，第一帧就是真图，不闪白。
                AsyncImage(
                    model = ticket.coverUrl,
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
            }
        }
    }
}
