package com.venera.compose.feature

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.venera.compose.components.ComicCardContextMenu
import com.venera.compose.components.ComicRowCard
import com.venera.compose.components.ComicRowMetadata
import com.venera.compose.components.ComicSharedTransition
import com.venera.compose.components.coverSharedElement
import com.venera.compose.components.searchVisibleTags
import com.venera.compose.components.venera.VeneraCard
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.components.venera.VeneraCoverMask
import com.venera.compose.components.venera.VeneraSourceBadge
import com.venera.compose.components.venera.VeneraTagChip
import com.venera.compose.source.model.Comic
import com.venera.compose.ui.tokens.VeneraTokens
import top.yukonga.miuix.kmp.basic.Text

/**
 * 搜索结果卡片层（共享件）。
 *
 * 从 SearchScreen.kt 抽离：Android 壳与桌面端复用同一套结果卡，不再各画一套高度。
 * 只依赖 components/、source.model.Comic、ui.tokens 与 miuix Text，不含任何 Android 运行时。
 */

/** 网格中的一行：等宽卡片 + 尾部补空，保证最后一行左对齐。 */
@Composable
internal fun ComicGridRow(
    row: List<Comic>,
    columnCount: Int,
    mask: (Comic) -> VeneraCoverMask,
    tagLabel: (String) -> String,
    onSelect: (Comic) -> Unit,
) {
    val tokens = VeneraTokens
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.gridGap),
    ) {
        row.forEach { comic ->
            Box(Modifier.weight(1f)) {
                SearchResultCard(
                    comic = comic,
                    sourceName = comic.sourceKey,
                    mask = mask(comic),
                    tagLabel = tagLabel,
                    onClick = { onSelect(comic) },
                )
            }
        }
        // 末行不足时用等宽 Spacer 占位，避免卡片被拉伸成不等宽
        repeat(columnCount - row.size) {
            Spacer(Modifier.weight(1f))
        }
    }
}

/** 详细模式的行卡按列数并排；补位规则与 [ComicGridRow] 一致。 */
@Composable
internal fun ComicDetailedRow(
    row: List<Comic>,
    columnCount: Int,
    mask: (Comic) -> VeneraCoverMask,
    tagLabel: (String) -> String,
    onSelect: (Comic) -> Unit,
) {
    val tokens = VeneraTokens
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.gridGap),
    ) {
        row.forEach { comic ->
            Box(Modifier.weight(1f)) {
                SearchResultRowItem(
                    comic = comic,
                    mask = mask(comic),
                    tagLabel = tagLabel,
                    onClick = { onSelect(comic) },
                )
            }
        }
        repeat(columnCount - row.size) {
            Spacer(Modifier.weight(1f))
        }
    }
}
/* ================================================================== *
 * 结果卡片与状态
 * ================================================================== */

/**
 * 漫画结果卡。
 *
 * 信息优先级：标题 > 作者·来源 > 可选 Metadata > 最多两个 Tag。
 * 缺失字段直接不渲染（不显示 0 likes / — / N/A / 空占位）。
 */
@Composable
internal fun SearchResultCard(
    comic: Comic,
    sourceName: String,
    mask: VeneraCoverMask,
    tagLabel: (String) -> String,
    onClick: () -> Unit,
) {
    val tokens = VeneraTokens
    var cardMenu by remember { mutableStateOf(false) }
    VeneraCard(onClick = onClick, onLongClick = { cardMenu = true }) {
        ComicCardContextMenu(
            comic = comic,
            expanded = cardMenu,
            onDismiss = { cardMenu = false },
            onOpenDetail = onClick,
        )
        VeneraCover(
            url = comic.cover,
            contentDescription = comic.title,
            // 双列网格卡也参与飞行；打码命中不飞（见 Modifier.coverSharedElement）。
            modifier = Modifier.coverSharedElement(
                key = ComicSharedTransition.coverKey(comic.sourceKey, comic.id),
                allowFly = mask == VeneraCoverMask.Visible,
            ),
            mask = mask,
        ) {
            VeneraSourceBadge(name = sourceName.ifBlank { comic.sourceKey })
        }
        Spacer(Modifier.height(tokens.spacing.cardCoverGap))
        Text(
            text = comic.title,
            fontSize = tokens.type.caption,
            fontWeight = tokens.type.weightMedium,
            color = tokens.color.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val byline = listOf(comic.subTitle, sourceName)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" · ")
        if (byline.isNotBlank()) {
            Text(
                text = byline,
                fontSize = tokens.type.overline,
                color = tokens.color.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        OptionalMetadata(comic)
        val tags = searchVisibleTags(comic.tags, emptyList(), tagLabel)
        if (tags.isNotEmpty()) {
            Spacer(Modifier.height(tokens.spacing.space2))
            Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2)) {
                tags.take(2).forEach { tag ->
                    VeneraTagChip(text = tag, onClick = null)
                }
            }
        }
    }
}

/**
 * 列表模式的单行卡：左封面 + 右信息。
 *
 * 实现已抽到 `components.ComicRowCard`（逐行原样搬走，尺寸/颜色未改），
 * 因为收藏页两条路径也要同一形态 —— 各自画一套就会同页多种高度。
 */
@Composable
internal fun SearchResultRowItem(
    comic: Comic,
    mask: VeneraCoverMask,
    tagLabel: (String) -> String,
    onClick: () -> Unit,
) {
    // 列表模式也要长按：只有网格能长按是另一种假一致。
    var cardMenu by remember { mutableStateOf(false) }
    Box {
    ComicRowCard(
        title = comic.title,
        coverUrl = comic.cover,
        subtitle = comic.subTitle,
        tags = comic.tags,
        mask = mask,
        onClick = onClick,
        onLongClick = { cardMenu = true },
        likesCount = comic.likesCount,
        rating = comic.rating,
    updateTime = comic.updateTime,
    tagLabel = tagLabel,
    coverModifier = Modifier.coverSharedElement(
        key = ComicSharedTransition.coverKey(comic.sourceKey, comic.id),
        allowFly = mask == VeneraCoverMask.Visible,
    ),
    )
        ComicCardContextMenu(
            comic = comic,
            expanded = cardMenu,
            onDismiss = { cardMenu = false },
            onOpenDetail = onClick,
        )
    }
}

/**
 * 可选 Metadata 行。
 *
 * 只渲染**确实存在**的字段；任一字段缺失即整段省略，
 * 绝不输出 0 likes / — / N/A / 空占位符。实现与 [ComicRowCard] 共用一份。
 */
@Composable
internal fun OptionalMetadata(comic: Comic) = ComicRowMetadata(
    likesCount = comic.likesCount,
    rating = comic.rating,
    updateTime = comic.updateTime,
)
