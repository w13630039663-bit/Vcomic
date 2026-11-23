package com.venera.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.venera.compose.ui.tokens.VeneraTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * 「无限下滑自动续页 + 右下角回到顶部」那族共用件（批）。
 *
 * ## 为什么要收成一颗
 *
 * 主墙（DesktopWallScaffold）先前自己写过一颗近尾观察器；口径「去掉下一页这种
 * 按钮、改成无限下滑 + 右下角回到顶部悬浮钮」把这一形推到**全项目**（探索 / 网络收藏 / 漫画搜索 /
 * Pixiv 账号 / Pixiv 画师 / 画廊搜索…）。九个页面各抄一遍扳机就是九处漂，所以三种懒容器各一颗
 * 观察器 + 一颗 FAB（三容器重载），判据只有这一份。
 *
 * ## 扳机的两条纪律（主墙那颗的原文照搬）
 *
 * - 近尾 = 最后可见项距总项不足两屏（列数 × 2 行）—— 提前两行发不算病，续页那颗自己的闸
 *   （到底 / 在飞 / 没腿可发）会挡住其余情况；调用点的 `enabled` 就该喂那把「页尾还有没有下一页」
 *   的判据（`footer.moreLabel != null` 或游标非空）。
 * - 键取「扳过几次」而不是页码的口径由调用点的 tick 保持，这里只把"滚到近尾"翻译成同一记扳机。
 */

/**
 * 瀑布流（LazyVerticalStaggeredGrid）容器的近尾观察器。
 *
 * `cooldownMs > 0` 时两页之间强制间隔（pixiv 站方对高频翻页会限流，参考 pixez 的队列口径：
 * 一次答复落地后至少歇这一 durations 才允许发下一跳）；到点若仍在近尾且仍 enabled 才补发。
 */
@Composable
fun DesktopStaggeredAutoLoadEffect(
    state: LazyStaggeredGridState,
    columns: Int,
    enabled: Boolean,
    cooldownMs: Long = 0L,
    onLoadMore: () -> Unit,
) {
    var lastFiredAt by remember(state) { mutableLongStateOf(0L) }
    LaunchedEffect(state, columns, enabled, cooldownMs) {
        snapshotFlow {
            val info = state.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= 0 &&
                info.totalItemsCount > columns &&
                lastVisible >= info.totalItemsCount - columns * 2
        }.distinctUntilChanged().collect latest@{ near ->
            if (!near || !enabled) return@latest
            val wait = if (lastFiredAt == 0L) 0L else cooldownMs - (System.currentTimeMillis() - lastFiredAt)
            if (wait > 0) {
                delay(wait)
                // 冷却期间可能已离尾 / 已禁用 —— 重新读一次实况再决定发不发。
                val info = state.layoutInfo
                val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                val stillNear = lastVisible >= 0 &&
                    info.totalItemsCount > columns &&
                    lastVisible >= info.totalItemsCount - columns * 2
                if (!stillNear) return@latest
            }
            lastFiredAt = System.currentTimeMillis()
            onLoadMore()
        }
    }
}

/** 定列网格（LazyVerticalGrid）容器的近尾观察器。 */
@Composable
fun DesktopGridAutoLoadEffect(
    state: LazyGridState,
    columns: Int,
    enabled: Boolean,
    onLoadMore: () -> Unit,
) {
    LaunchedEffect(state, columns, enabled) {
        snapshotFlow {
            val info = state.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= 0 &&
                info.totalItemsCount > columns &&
                lastVisible >= info.totalItemsCount - columns * 2
        }.distinctUntilChanged().collect { near ->
            if (near && enabled) onLoadMore()
        }
    }
}

/** 纵向列表（LazyColumn）容器的近尾观察器。 */
@Composable
fun DesktopListAutoLoadEffect(
    state: LazyListState,
    enabled: Boolean,
    onLoadMore: () -> Unit,
) {
    LaunchedEffect(state, enabled) {
        snapshotFlow {
            val info = state.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= 0 &&
                info.totalItemsCount > 0 &&
                lastVisible >= info.totalItemsCount - 4
        }.distinctUntilChanged().collect { near ->
            if (near && enabled) onLoadMore()
        }
    }
}

/** 滚动超过这一距离才浮现「回到顶部」（约两屏，刚滚一屏内不值得给钮）。 */
private val BackToTopThresholdPx = 900f

/** 三种容器各自的"滚过了没有"读数 + 回顶动作，FAB 那颗画法只写一遍。 */
@Composable
private fun BackToTopFabBody(
    visible: Boolean,
    onBack: suspend () -> Unit,
    modifier: Modifier,
    size: Dp,
) {
    val tokens = VeneraTokens
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, label = "backToTopPress")
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .shadow(10.dp, CircleShape)
                .clip(CircleShape)
                .background(tokens.color.surfaceContainerHigh)
                .border(
                    1.dp,
                    if (hovered) tokens.color.outline else tokens.color.outlineVariant,
                    CircleShape,
                )
                .hoverable(interaction)
                .clickable(interactionSource = interaction, indication = null) {
                    scope.launch { onBack() }
                },
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(
                imageVector = Icons.Outlined.ArrowUpward,
                contentDescription = "回到顶部",
                tint = if (hovered) tokens.color.textPrimary else tokens.color.textSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** 瀑布流容器的「回到顶部」悬浮钮：滚过约两屏才浮现。 */
@Composable
fun DesktopBackToTopFab(
    state: LazyStaggeredGridState,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    val offset by animateFloatAsState(
        if (state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > BackToTopThresholdPx) 1f else 0f,
        label = "backToTopStaggered",
    )
    BackToTopFabBody(
        visible = offset > 0.5f,
        onBack = { state.scrollToItem(0) },
        modifier = modifier.alpha(offset),
        size = size,
    )
}

/** 定列网格容器的「回到顶部」悬浮钮。 */
@Composable
fun DesktopBackToTopFab(
    state: LazyGridState,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    val offset by animateFloatAsState(
        if (state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > BackToTopThresholdPx) 1f else 0f,
        label = "backToTopGrid",
    )
    BackToTopFabBody(
        visible = offset > 0.5f,
        onBack = { state.scrollToItem(0) },
        modifier = modifier.alpha(offset),
        size = size,
    )
}

/** 纵向列表容器的「回到顶部」悬浮钮。 */
@Composable
fun DesktopBackToTopFab(
    state: LazyListState,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    val offset by animateFloatAsState(
        if (state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > BackToTopThresholdPx) 1f else 0f,
        label = "backToTopList",
    )
    BackToTopFabBody(
        visible = offset > 0.5f,
        onBack = { state.scrollToItem(0) },
        modifier = modifier.alpha(offset),
        size = size,
    )
}
