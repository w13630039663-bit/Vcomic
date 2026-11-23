package com.venera.desktop

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

/**
 * 轻量滚动位置锚点：只保存首可见项索引与像素偏移两个整型，不持有 Compose LayoutNode 或 Skia 对象，
 * 避免像把 LazyListState 直接 hoist 到顶层壳那样在离开页面后锁死整棵离场节点树。
 */
internal class DesktopScrollAnchor(
    var index: Int = 0,
    var offset: Int = 0,
) {
    /**
     * 记录当前首可见位置与像素偏移。
     *
     * @param firstIndex 首可见条目索引（小于 0 时钳制为 0）。
     * @param firstOffset 首可见条目滚动偏移像素（小于 0 时钳制为 0）。
     */
    fun save(firstIndex: Int, firstOffset: Int) {
        index = firstIndex.coerceAtLeast(0)
        offset = firstOffset.coerceAtLeast(0)
    }

    /** 切换内容域或源时归零滚动锚点。 */
    fun reset() {
        index = 0
        offset = 0
    }
}

/**
 * 在当前分支作用域内创建并维护 [LazyListState]：入场从 [anchor] 恢复滚动位，
 * 分支退出组合时写回锚点并随组合树释放自身与关联的 LayoutNode。
 *
 * @param anchor 住在父级壳层中的轻量滚动锚点。
 * @return 当前分支存活期内的 [LazyListState]。
 */
@Composable
internal fun rememberRetainedLazyListState(anchor: DesktopScrollAnchor): LazyListState {
    val state = rememberLazyListState(
        initialFirstVisibleItemIndex = anchor.index,
        initialFirstVisibleItemScrollOffset = anchor.offset,
    )
    DisposableEffect(state, anchor) {
        onDispose {
            anchor.save(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset)
        }
    }
    return state
}

/**
 * 在当前分支作用域内创建并维护 [LazyGridState]，离场写回 [anchor] 后随组合释放。
 *
 * @param anchor 住在父级壳层中的轻量滚动锚点。
 * @return 当前分支存活期内的 [LazyGridState]。
 */
@Composable
internal fun rememberRetainedLazyGridState(anchor: DesktopScrollAnchor): LazyGridState {
    val state = rememberLazyGridState(
        initialFirstVisibleItemIndex = anchor.index,
        initialFirstVisibleItemScrollOffset = anchor.offset,
    )
    DisposableEffect(state, anchor) {
        onDispose {
            anchor.save(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset)
        }
    }
    return state
}

/**
 * 在当前分支作用域内创建并维护 [LazyStaggeredGridState]，离场写回 [anchor] 后随组合释放。
 *
 * @param anchor 住在父级壳层中的轻量滚动锚点。
 * @return 当前分支存活期内的 [LazyStaggeredGridState]。
 */
@Composable
internal fun rememberRetainedLazyStaggeredGridState(anchor: DesktopScrollAnchor): LazyStaggeredGridState {
    val state = rememberLazyStaggeredGridState(
        initialFirstVisibleItemIndex = anchor.index,
        initialFirstVisibleItemScrollOffset = anchor.offset,
    )
    DisposableEffect(state, anchor) {
        onDispose {
            anchor.save(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset)
        }
    }
    return state
}

/**
 * 向可变缓存表写入键值并限制最大容量；超限时逐出最先插入的旧键，防止长期切换域后无限增长。
 *
 * @param map 目标状态映射表。
 * @param key 缓存键。
 * @param value 缓存值。
 * @param maxEntries 允许保留的最大条目数（至少为 1）。
 */
internal fun <K, V> putBoundedMapEntry(
    map: MutableMap<K, V>,
    key: K,
    value: V,
    maxEntries: Int,
) {
    val limit = maxEntries.coerceAtLeast(1)
    if (!map.containsKey(key)) {
        while (map.size >= limit) {
            val oldestKey = map.keys.firstOrNull() ?: break
            map.remove(oldestKey)
        }
    }
    map[key] = value
}
