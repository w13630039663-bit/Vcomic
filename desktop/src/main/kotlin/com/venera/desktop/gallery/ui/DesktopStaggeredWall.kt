package com.venera.desktop.gallery.ui

import com.venera.desktop.ui.DesktopPushButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.ui.components.registerPixelBuddyCard
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 主墙 · 瀑布流（桌面自绘）。
 *
 * 从前这一档直接挂共享页 `GalleryForYouPage`，卡走共享 `GalleryPostCard` —— 那颗卡在共享层，
 * 挂不上桌面的右键件，也没有悬停浮起，与等宽网格那颗自绘卡不是一个规格。这一颗把瀑布流
 * 收回桌面自绘：`LazyVerticalStaggeredGrid` 铺原比例零裁切的卡，但每张卡一律走
 * [DesktopGalleryCard] 唯一规格 —— 浮起、三态描边、胶囊动作坞、右键菜单、信息栏与 Grid 档逐项对齐。
 *
 * header（顶栏 / 筛选架 / 首页横栏 / 墙头 / 页尾说明）用通栏 item 收进同一根滚动轴，
 * 与 Grid 档 `DesktopJustifiedWall` 同滚、同 near-end 扳机。
 */
@Composable
internal fun DesktopStaggeredWall(
    cards: List<DesktopWallCardModel>,
    header: @Composable () -> Unit,
    avatars: Map<String, String>,
    titles: Map<String, String>,
    columns: Int,
    selectedUids: Set<String>,
    onToggleSelect: (DesktopWallCardModel) -> Unit,
    onOpenPost: (DesktopWallCardModel) -> Unit,
    onSearchTag: (String) -> Unit,
    onOpenCardArtist: (DesktopWallCardModel) -> Unit,
    onReachEnd: () -> Unit,
    state: LazyStaggeredGridState,
    placeholder: (@Composable () -> Unit)?,
    footer: DesktopGalleryWallFooter,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    val wallSpec = LocalDesktopWallSpec.current
    val gap = wallSpec.wallGap
    val nearEnd = remember {
        derivedStateOf {
            val info = state.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= 0 && last >= info.totalItemsCount - 2 && info.totalItemsCount > 1
        }
    }
    // 墙没铺满两屏前只留页尾把手给出路，不许近尾观察器抢着自动续（无头树里会与点击叠成两记扳机）。
    // 真窗口里滚过两屏之后观察器照常接管，与 Grid 档同一条续页纪律。
    LaunchedEffect(nearEnd, cards.size, columns) {
        if (cards.size < columns * 2) return@LaunchedEffect
        androidx.compose.runtime.snapshotFlow { nearEnd.value }
            .distinctUntilChanged()
            .collect { reached -> if (reached) onReachEnd() }
    }
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(columns.coerceAtLeast(1)),
        state = state,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = tokens.spacing.space6),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalItemSpacing = gap,
    ) {
        item(key = "gallery-header", span = StaggeredGridItemSpan.FullLine) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 0.dp)) { header() }
        }
        if (cards.isEmpty() && placeholder != null) {
            item(key = "gallery-placeholder", span = StaggeredGridItemSpan.FullLine) { placeholder() }
        }
        itemsIndexed(
            cards,
            // 键 = 身份（uid/url），不许掺内容哈希：卡上任何字段一变就是整卡销毁重建，
            // 悬停态与取图槽全丢。两个身份都空的病卡才退到哈希兜底。
            key = { _, it -> it.uid.ifBlank { it.url }.ifBlank { it.hashCode().toString() } },
            contentType = { _, _ -> "card" },
        ) { index, card ->
            val cUid = card.uid.ifBlank { card.sourcePost?.uid.orEmpty() }
            val buddyKey = "wall-${cUid.ifBlank { card.url }}"
            Box(Modifier.padding(horizontal = 0.dp)) {
                DesktopGalleryCard(
                    card = card,
                    index = index,
                    // 瀑布流：原比例零裁切。
                    aspect = card.aspect,
                    onOpenPost = onOpenPost,
                    onSearchArtist = onSearchTag,
                    onOpenArtist = { onOpenCardArtist(card) },
                    titleOverride = card.sourcePost?.let { titles[it.uid] },
                    avatarUrl = card.sourcePost?.let { avatars[it.uid] },
                    selected = cUid.isNotBlank() && cUid in selectedUids,
                    onToggleSelect = { onToggleSelect(card) },
                    modifier = Modifier.registerPixelBuddyCard(buddyKey),
                )
            }
        }
        item(key = "gallery-footer", span = StaggeredGridItemSpan.FullLine) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = tokens.spacing.screenHorizontal, vertical = tokens.spacing.space3),
            ) {
                if (cards.isNotEmpty() && footer.moreLabel != null) {
                    DesktopPushButton(onClick = onLoadMore) { Text(footer.moreLabel) }
                }
            }
        }
    }
}
