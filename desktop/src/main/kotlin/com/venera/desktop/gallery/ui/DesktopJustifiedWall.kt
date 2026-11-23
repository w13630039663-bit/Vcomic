package com.venera.desktop.gallery.ui

import com.venera.desktop.ui.DesktopPushButton
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.venera.desktop.ui.components.DesktopContextMenuArea
import com.venera.desktop.ui.components.DesktopMenuEntry
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.components.venera.VeneraCoverMask
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.compose.ui.tokens.LocalVeneraDarkTheme
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 行高密度三档（紧凑 / 标准 / 宽松）：等宽网格下经 [DesktopWallSpec.columnBias] 调列数
 * （紧凑 +1 / 标准 0 / 宽松 -1），另经 [DesktopWallSpec.wallGap] 调列距 —— 列数本身随内容宽
 * 自适应，档位只做偏置，所以窗口拖宽永远加列。
 */
internal enum class DesktopWallDensity(val label: String) {
    Compact("紧凑"),
    Standard("标准"),
    Loose("宽松"),
}

/**
 * 等宽网格装行纯件（判据可扫）：**横图占两格**——按格容量贪心装行，行满（或放不下宽卡）就断；
 * 空格不补（渲染层用 Spacer 摊掉剩余 weight）。
 */
internal fun desktopGridRows(
    cards: List<DesktopWallCardModel>,
    columns: Int,
): List<List<DesktopWallCardModel>> {
    if (cards.isEmpty() || columns < 1) return emptyList()
    val rows = mutableListOf<List<DesktopWallCardModel>>()
    val pool = cards.toMutableList()

    while (pool.isNotEmpty()) {
        val row = mutableListOf<DesktopWallCardModel>()
        var remaining = columns

        while (pool.isNotEmpty() && remaining > 0) {
            val candidate = pool[0]
            val wantsWide = candidate.aspect >= 1.15f

            if (remaining >= 2 && wantsWide) {
                row += candidate.copy(gridSpan = 2)
                pool.removeAt(0)
                remaining -= 2
            } else if (remaining == 1 && wantsWide) {
                // 仅剩 1 格，但当前卡是宽卡：优先从后续池中查找单格竖图/方图补位填满
                val singleIndex = pool.indexOfFirst { it.aspect < 1.15f }
                if (singleIndex >= 0) {
                    val singleCard = pool.removeAt(singleIndex)
                    row += singleCard.copy(gridSpan = 1)
                    remaining -= 1
                    break
                } else {
                    // 后续全是宽图：将当前图降级为单格 1:1 展示，严密填满本行，杜绝右侧空缺
                    row += candidate.copy(gridSpan = 1)
                    pool.removeAt(0)
                    remaining -= 1
                    break
                }
            } else {
                row += candidate.copy(gridSpan = 1)
                pool.removeAt(0)
                remaining -= 1
            }
        }

        if (row.isNotEmpty()) {
            rows += row
        }
    }
    return rows
}

/**
 * 横图判定（跨双格档）：若显式指定了 gridSpan 则遵从；否则原比例 ≥ 1.15 算横图 ——
 * 立绘/人像（0.7–1.0）单格 1:1 裁切最自然，风景/全景/漫画页（≥1.15）单格里裁得最狠，双格兜住。
 */
internal fun desktopGridCardIsWide(card: DesktopWallCardModel): Boolean =
    if (card.gridSpan > 0) card.gridSpan == 2 else card.aspect >= 1.15f

/**
 * 主墙 · 等宽网格 · 大间隔（对齐 pixiv 官网「推荐作品」的排法）。
 *
 * ## 形状
 *
 * - **等宽列**（列数 = 密度三档 7/6/5），封面**统一 1:1 方图中心裁** —— 横竖图同排宽差归零，
 *   扫视节奏最稳（pixiv 官网同款取舍：浏览发现的整齐感 > 构图完整，看全图交给大图屏）；
 * - **横图跨双格**（[desktopGridCardIsWide] 的卡 `GridItemSpan(2)`，图高仍一格）：
 *   16:9 的裁切从单格 1:1 的 ~44% 降到 ~16% —— 方形对横图最狠的那一档由双格兜住
 *   （Instagram / 商店货架同模式）；
 * - **间隔放大**：横 16dp / 纵 20dp（稿口径「参考 pixiv 官方的间隔」——比瀑布流的 14dp 松一档）；
 * - 卡 = 方图 + 统一信息栏（[DesktopGalleryCardInfo] 一字不改：词典标题 / 站源 / 画师）。
 *
 * ## 为什么做成开关而不是替换
 *
 * 设置里自选「瀑布流 / 等宽网格」—— 瀑布流那套（共享页，原比例零裁切）
 * 原样保留在壳的 Staggered 分支里，本颗只服务 Grid 档；选卡（Ctrl）/ 页尾把手 / 占位骨架照旧。
 */
@Composable
internal fun DesktopJustifiedWall(
    cards: List<DesktopWallCardModel>,
    /** 头图槽：顶栏 / 筛选架 / 标签维度 / 首页横栏 / 页尾说明整体收进来，随整页一起滚。 */
    header: @Composable () -> Unit,
    avatars: Map<String, String>,
    titles: Map<String, String>,
    /** 列数（内容宽档位 + 密度偏置的结果；「紧凑」= 列多卡小）。 */
    columns: Int,
    selectedUids: Set<String>,
    onToggleSelect: (DesktopWallCardModel) -> Unit,
    onOpenPost: (DesktopWallCardModel) -> Unit,
    /** 右键「搜画师」的门（与发现带/画师行同一扇交接门；装配点递）。 */
    onSearchTag: (String) -> Unit,
    /** 右键 Pixiv「查看画师」的门（直达画师详情页）。 */
    onOpenCardArtist: (DesktopWallCardModel) -> Unit,
    onReachEnd: () -> Unit,
    /** 滚动状态仍住壳（Grid 布局的列表态；与瀑布流档的 Staggered 态是两颗，各配各的布局器）。 */
    state: LazyListState,
    /** 页形占位：取数中 = 转圈；空墙 = 出路卡（壳画，标题/消息/按钮都吃真账）。 */
    placeholder: (@Composable () -> Unit)?,
    /**
     * 页尾三格的成品（沿用原语义：「下一页」把手照摆 —— 滚得慢的人仍看得见出口，与近尾自动
     * 扳机两头共用一种相位不互踩；`moreLabel == null` = 把手整格不摆，缺席/到底那几句照念）。
     */
    footer: DesktopGalleryWallFooter,
    /** 页尾把手与近尾观察器**同一颗扳机**（壳里 `onLoadMore` = `wallMoreTick++`）。 */
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    val wallSpec = LocalDesktopWallSpec.current
    // 列距吃密度（紧凑 10 / 标准 16 / 宽松 24dp）——从前是常量 wallGridGapLoose，切密度不动。
    val hGap = wallSpec.wallGap
    val nearEnd = remember {
        derivedStateOf {
            val info = state.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= 0 && last >= info.totalItemsCount - 2 && info.totalItemsCount > 1
        }
    }
    LaunchedEffect(nearEnd) {
        androidx.compose.runtime.snapshotFlow { nearEnd.value }
            .distinctUntilChanged()
            .collect { reached -> if (reached) onReachEnd() }
    }
    Box(modifier.fillMaxSize()) {
        // 等宽网格：卡按列数切行（横图跨双格 = 在行内 weight(2)，缺格补空），滚态与近尾观察同族。
        val gridRows = remember(cards, columns) { desktopGridRows(cards, columns) }
        LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
            item(key = "gallery-header") { header() }
            if (cards.isEmpty() && placeholder != null) {
                item(key = "gallery-placeholder") { placeholder() }
            }
            // 行键 = 行首卡的身份：内容哈希会让行内容任何字段一变就整行销毁重建。
            // 两个身份都空的病卡才退到行哈希兜底。
            itemsIndexed(
                gridRows,
                key = { _, row ->
                    val first = row.first()
                    "row#" + first.uid.ifBlank { first.url }.ifBlank { row.hashCode().toString() }
                },
                contentType = { _, _ -> "row" },
            ) { rowIndex, row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = tokens.spacing.screenHorizontal)
                        .padding(bottom = 32.dp), // 纵向行距（26→32，对齐 pixiv 官网行距；深浅同调）
                    horizontalArrangement = Arrangement.spacedBy(hGap),
                ) {
                    val usedCells = row.sumOf { if (desktopGridCardIsWide(it)) 2 else 1 }
                    row.forEachIndexed { colIndex, card ->
                        val span = if (desktopGridCardIsWide(card)) 2 else 1
                        Box(Modifier.weight(span.toFloat())) {
                            val cUid = card.uid.ifBlank { card.sourcePost?.uid.orEmpty() }
                            // Grid 档退役私有 GridCard，转交唯一卡规格 —— 浮动 / 右键 /
                            // 选中 / 胶囊坞 / 信息栏与瀑布流、各子页同一份，不再各画一遍。
                            DesktopGalleryCard(
                                card = card,
                                index = rowIndex * columns + colIndex,
                                aspect = if (span == 2) 2f else 1f,
                                onOpenPost = onOpenPost,
                                onSearchArtist = onSearchTag,
                                onOpenArtist = { onOpenCardArtist(card) },
                                titleOverride = card.sourcePost?.let { titles[it.uid] },
                                avatarUrl = card.sourcePost?.let { avatars[it.uid] },
                                selected = cUid.isNotBlank() && cUid in selectedUids,
                                onToggleSelect = { onToggleSelect(card) },
                            )
                        }
                    }
                    val free = columns - usedCells
                    if (free > 0) {
                        Spacer(Modifier.weight(free.toFloat()))
                    }
                }
            }
            item(key = "gallery-footer") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = tokens.spacing.screenHorizontal, vertical = tokens.spacing.space3),
                ) {
                    // 页尾把手只在**有墙**时摆（空墙档的出路住 placeholder —— 把手不两处同屏）；
                    // 缺席/到底那几句由 header 槽的 footer.lines 念（两头不许各摆一份 —— 桌面偏离②）。
                    if (cards.isNotEmpty() && footer.moreLabel != null) {
                        DesktopPushButton(onClick = onLoadMore) {
                            Text(footer.moreLabel)
                        }
                    }
                }
            }
        }
    }
}
