package com.venera.compose.feature.explore

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.venera.compose.components.ComicCardContextMenu
import com.venera.compose.components.ComicSharedTransition
import com.venera.compose.components.ComicTileDetailed
import com.venera.compose.components.coverSharedElement
import com.venera.compose.components.venera.VeneraCard
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.components.venera.VeneraCoverMask
import com.venera.compose.components.venera.VeneraSourceBadge
import com.venera.compose.feature.ComicItem
import com.venera.compose.source.model.Comic
import com.venera.compose.source.model.ExplorePagePart
import com.venera.compose.source.model.PageJumpTarget
import com.venera.compose.ui.tokens.VeneraTokens
import top.yukonga.miuix.kmp.basic.Text

/**
 * 探索网格卡片层（共享件）。
 *
 * 从 `UnifiedExploreScreen.kt` 抽离：Android 壳与桌面端复用同一套探索网格卡，不再各画一套高度。
 * 打码判据不在这里算 —— 由调用方注入 `maskOf`（Android 壳里仍是 `ContentGuardManager.coverMaskStateFor`，
 * 守卫本体与它的归属账留在原屏文件，本件因此零 Android 运行时）。
 */

/** 网格行（列数按可用宽推）：一个 Row 就是一个 LazyItem，参与行级回收。 */
@Composable
internal fun ExploreCardRow(
    row: List<Comic>,
    columns: Int,
    sourceName: String,
    maskOf: (Comic) -> String,
    onSelectComic: (ComicItem) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(VeneraTokens.spacing.gridGap),
    ) {
        row.forEach { comic ->
            Box(Modifier.weight(1f)) {
                ExploreComicCard(comic, sourceName, maskOf, onSelectComic)
            }
        }
        // 末行不足时补等宽空位，否则尾卡被拉伸
        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
    }
}

/** 详细模式的行卡按列数并排（宽窗每 360dp 一列，同 master）。 */
@Composable
internal fun ExploreDetailedRow(
    row: List<Comic>,
    columns: Int,
    sourceName: String,
    maskOf: (Comic) -> String,
    onSelectComic: (ComicItem) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(VeneraTokens.spacing.gridGap),
    ) {
        row.forEach { comic ->
            Box(Modifier.weight(1f)) {
                ExploreDetailedCard(comic, sourceName, maskOf, onSelectComic)
            }
        }
        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
    }
}

/** 单列大卡（detailed）：复用 ComicTileDetailed，打码状态同步传给封面。 */
@Composable
private fun ExploreDetailedCard(
    comic: Comic,
    sourceName: String,
    maskOf: (Comic) -> String,
    onSelectComic: (ComicItem) -> Unit,
) {
    // 打码读数由调用方注入（Android 壳里就是守卫内部 LRU 判定缓存）；每次组合重算保证规则/模式变化即时生效。
    val maskState = maskOf(comic)
    val openComic = {
        onSelectComic(
            ComicItem(
                id = comic.id,
                title = comic.title,
                author = comic.subTitle,
                coverUrl = comic.cover,
                tags = comic.tags,
                description = comic.description,
                sourceName = sourceName,
                rating = comic.rating?.toString().orEmpty(),
                likesCount = comic.likesCount,
                updateTime = comic.updateTime,
            )
        )
    }
    // 长按菜单在两种显示模式下都要在 —— 只有双列能长按是另一种假一致。
    var cardMenu by remember { mutableStateOf(false) }
    Box {
    ComicTileDetailed(
        title = comic.title,
        coverUrl = comic.cover,
        subtitle = comic.subTitle,
        description = comic.description,
        tags = comic.tags,
        rating = comic.rating?.toDouble(),
        likesCount = comic.likesCount,
        badge = sourceName,
        coverMaskState = maskState,
        // key 用交给详情页 ComicItem 的那个 sourceName（本页传的是显示名），两端必然同串；打码命中不飞。
        coverModifier = Modifier.coverSharedElement(
            key = ComicSharedTransition.coverKey(sourceName, comic.id),
            allowFly = maskState == "VISIBLE",
        ),
        onClick = openComic,
        onLongClick = { cardMenu = true },
    )
        ComicCardContextMenu(
            comic = comic,
            expanded = cardMenu,
            onDismiss = { cardMenu = false },
            onOpenDetail = openComic,
        )
    }
}

@Composable
private fun ExploreComicCard(
    comic: Comic,
    sourceName: String,
    maskOf: (Comic) -> String,
    onSelectComic: (ComicItem) -> Unit,
) {
    // 打码读数由调用方注入（Android 壳里就是守卫内部 LRU 判定缓存）；每次组合重算保证规则/模式变化即时生效。
    val maskState = maskOf(comic)
    val openComic = {
        onSelectComic(
            ComicItem(
                id = comic.id,
                title = comic.title,
                author = comic.subTitle,
                coverUrl = comic.cover,
                tags = comic.tags,
                description = comic.description,
                sourceName = sourceName,
                rating = comic.rating?.toString().orEmpty(),
                likesCount = comic.likesCount,
                updateTime = comic.updateTime,
            )
        )
    }
    var cardMenu by remember { mutableStateOf(false) }
    VeneraCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = openComic,
        onLongClick = { cardMenu = true },
    ) {
        ComicCardContextMenu(
            comic = comic,
            expanded = cardMenu,
            onDismiss = { cardMenu = false },
            onOpenDetail = openComic,
        )
        VeneraCover(
            url = comic.cover,
            contentDescription = comic.title,
            modifier = Modifier.coverSharedElement(
                key = ComicSharedTransition.coverKey(sourceName, comic.id),
                allowFly = maskState == "VISIBLE",
            ),
            mask = if (maskState == "VISIBLE") VeneraCoverMask.Visible else VeneraCoverMask.Masked,
        ) {
            VeneraSourceBadge(name = sourceName)
        }
        Spacer(Modifier.height(VeneraTokens.spacing.cardCoverGap))
        Text(
            text = comic.title,
            fontSize = VeneraTokens.type.caption,
            fontWeight = VeneraTokens.type.weightSemibold,
            color = VeneraTokens.color.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        val sub = comic.subTitle.ifBlank { comic.tags.take(2).joinToString(" · ") }
        if (sub.isNotBlank()) {
            Spacer(Modifier.height(VeneraTokens.spacing.space1))
            Text(
                text = sub,
                fontSize = VeneraTokens.type.overline,
                color = VeneraTokens.color.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 分区标题 + R3「查看更多 ›」（仅 category 型 target 能被 SourceSectionRoute 承载）。 */
@Composable
internal fun PartHeader(part: ExplorePagePart, onViewMore: (PageJumpTarget) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = part.title.ifBlank { "推荐" },
            fontSize = VeneraTokens.type.itemTitle,
            fontWeight = VeneraTokens.type.weightBold,
            color = VeneraTokens.color.textPrimary,
            modifier = Modifier.weight(1f),
        )
        val target = part.viewMore
        if (target != null && target.page == "category") {
            Text(
                text = "查看更多 ›",
                fontSize = VeneraTokens.type.caption,
                color = VeneraTokens.color.primary,
                modifier = Modifier
                    .padding(start = VeneraTokens.spacing.space2)
                    .clickable { onViewMore(target) },
            )
        }
    }
}

/**
 * R1 触底 footer：加载中转圈；失败可点击重试；静默态给出上滑提示。
 *
 * `loadingRing` 是插槽（与 `ComicDetailScreenBody` 同一条形状）：默认标准环，桌面吃缺省；
 * 安卓调用点递 `CircularWavyProgressIndicator`。桌面 material3 jar 没有波浪环（交接 §3.2），
 * 缺省那档就是"少一档动画的降级读数"，不是自造桌面视觉。
 */
@Composable
internal fun LoadMoreFooter(
    isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    loadingRing: @Composable (modifier: Modifier, color: androidx.compose.ui.graphics.Color, trackColor: androidx.compose.ui.graphics.Color) -> Unit =
        { modifier, color, _ -> CircularProgressIndicator(modifier = modifier, color = color) },
) {
    Box(
        Modifier.fillMaxWidth().padding(vertical = VeneraTokens.spacing.space5),
        contentAlignment = Alignment.Center,
    ) {
        when {
            isLoading -> loadingRing(
                // 用 loaderInline(28dp) 而不是 badgeSize —— 环直径小于 24dp 会糊成一团。
                Modifier.size(VeneraTokens.spacing.loaderInline),
                VeneraTokens.color.primary,
                VeneraTokens.color.surfaceVariant,
            )
            error != null -> Text(
                text = "这一段没读出来",
                fontSize = VeneraTokens.type.caption,
                color = VeneraTokens.color.primary,
                modifier = Modifier.clickable(onClick = onRetry),
            )
            else -> Text(
                text = "上滑加载更多",
                fontSize = VeneraTokens.type.overline,
                color = VeneraTokens.color.textTertiary,
            )
        }
    }
}
