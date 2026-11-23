package com.venera.desktop.gallery.ui

import com.venera.desktop.ui.theme.VeneraType
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.domain.GalleryTitleTag
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.gallery.ui.DesktopGalleryMetrics.wallGridGap

/** 一枚维度标签：原文 + 词典中文（`null` = 词典没这一行，卡上只摆原文 —— 不编翻译）。 */
internal data class DesktopDimensionTag(
    val name: String,
    val zh: String?,
    val count: Int,
)

/** 四组之一（`category` 即站方档位：1 画师 / 4 角色 / 3 系列 / 0 通用）。 */
internal data class DesktopTagDimension(
    val category: Int,
    val label: String,
    val tags: List<DesktopDimensionTag>,
)

/**
 * 维度聚合纯件（判据可扫）：计数取自当轮**已过守卫的墙账**（真数得出），
 * 翻译与分组取自词典 `titleTags` 那一格单源（[titled] 由壳在 IO 里查好递进来）。
 *
 * - 站方档位：1 画师 / 4 角色 / 3 系列；0 通用与**词典 miss** 都落通用组 ——
 *   「词典没收录」与「站方说是通用」在屏上是同一档（都是无中文原文直摆），不另立"未分类"；
 * - 词典整条腿缺席（[titled] 空 map）⇒ 全部落通用组、不画中文：如实降级，形状照常；
 * - 每组只摆前 [perGroup] 枚（按计数），余数折叠成「+N」。
 */
internal fun desktopTagDimensionsOf(
    posts: List<GalleryPost>,
    titled: Map<String, GalleryTitleTag>,
    perGroup: Int = 4,
): List<DesktopTagDimension> {
    if (posts.isEmpty()) return emptyList()
    val counts = linkedMapOf<String, Int>()
    for (post in posts) {
        for (raw in post.tagList) {
            val tag = raw.trim()
            if (tag.isEmpty()) continue
            counts[tag] = (counts[tag] ?: 0) + 1
        }
    }
    if (counts.isEmpty()) return emptyList()
    val ordered = counts.entries.sortedWith(
        compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key },
    )
    val groups = mutableMapOf<Int, MutableList<DesktopDimensionTag>>()
    for ((name, count) in ordered) {
        val row = titled[name]
        val category = row?.category ?: 0
        val list = groups.getOrPut(category) { mutableListOf() }
        list += DesktopDimensionTag(name = name, zh = row?.label?.takeIf { it != name }, count = count)
    }
    // 始终保持固定 4 组分类（画师、角色、系列、通用），绝不因为某项没有标签而坍塌单行
    val categories = listOf(1 to "画师", 4 to "角色", 3 to "系列", 0 to "通用")
    return categories.map { (category, label) ->
        val tags = groups[category].orEmpty()
        val head = tags.take(perGroup)
        val more = (tags.size - perGroup).coerceAtLeast(0)
        DesktopTagDimension(
            category = category,
            label = label,
            tags = if (head.isNotEmpty()) {
                head + if (more > 0) listOf(DesktopDimensionTag("+$more", null, 0)) else emptyList()
            } else {
                emptyList()
            },
        )
    }
}

/**
 * 标签维度舱（图库站 `.tag-grid`）：画师 / 角色 / 系列 / 通用四组双语标签。
 *
 * - 组内 chip 点下去 = 那串词交给画廊搜索（壳递进 [onOpenTag]，与发现带同一扇门）；
 * - 词典整腿缺席时舱头那句念「词典未接入」，四组照常摆（通用组兜底）—— 组不消失、翻译不编。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DesktopGalleryTagDimensions(
    groups: List<DesktopTagDimension>,
    dictionaryReady: Boolean,
    onOpenTag: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (groups.isEmpty()) return
    val tokens = VeneraTokens
    // 4 列并排网格栅格：画师 (#E26D8A) · 角色 (#4D9FFF) · 系列 (#3ECF8E) · 通用 (#E8B04B)
    val dotColorMap = mapOf(
        1 to Color(0xFFE26D8A),
        4 to Color(0xFF4D9FFF),
        3 to Color(0xFF3ECF8E),
        0 to Color(0xFFE8B04B),
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = tokens.spacing.screenHorizontal, vertical = 12.dp),
    ) {
        // 头部
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF4D9FFF).copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "🏷",
                    fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "标签维度",
                style = GalleryTokens.Typography.CardTitle,
                color = tokens.color.textPrimary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (dictionaryReady) "词典已接入 · 点击加词" else "词典未接入 · 原文直搜",
                fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                color = tokens.color.textTertiary,
            )
        }

        // 4 列并排网格 (grid-template-columns: 1fr 1fr 1fr 1fr)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            groups.forEach { group ->
                val dotColor = dotColorMap[group.category] ?: Color(0xFFE8B04B)
                val sumCount = group.tags.filter { it.count > 0 }.sumOf { it.count }
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    // 维度行头：彩色点 + 分类名 + 计数
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(dotColor),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = group.label,
                            fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                            fontWeight = VeneraType.cardTitle.weight,
                            color = tokens.color.textSecondary,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = if (sumCount > 0) String.format("%,d", sumCount) else "—",
                            fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                            color = tokens.color.textTertiary,
                        )
                    }

                    // 标签胶囊流
                    if (group.tags.isEmpty()) {
                        Text(
                            text = "暂无收录",
                            fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                            color = tokens.color.textTertiary.copy(alpha = 0.5f),
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            group.tags.forEach { tag ->
                                DimensionChip(
                                    primary = tag.name,
                                    secondary = tag.zh,
                                    onOpen = { onOpenTag(tag.name) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DimensionChip(
    primary: String,
    secondary: String?,
    onOpen: () -> Unit,
) {
    val tokens = VeneraTokens
    val isMoreChip = primary.startsWith("+")
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val shape = RoundedCornerShape(12.dp)

    Row(
        modifier = Modifier
            .clip(shape)
            .background(if (isHovered) tokens.color.primary.copy(alpha = 0.12f) else GalleryTokens.Color.SurfaceLevel2)
            .border(
                width = 1.dp,
                color = if (isHovered) tokens.color.primary.copy(alpha = 0.6f) else GalleryTokens.Color.OutlineHairline,
                shape = shape,
            )
            .then(
                if (!isMoreChip) {
                    Modifier.clickable(interactionSource = interactionSource, indication = null, onClick = onOpen)
                } else Modifier,
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (isMoreChip) {
            Text(
                text = primary,
                fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                fontWeight = VeneraType.cardTitle.weight,
                color = tokens.color.textTertiary,
            )
        } else if (secondary != null) {
            // 中文大字 + 原词小字
            Text(
                text = secondary,
                fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                fontWeight = VeneraType.control.weight,
                color = if (isHovered) tokens.color.primary else tokens.color.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "#$primary",
                fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                color = tokens.color.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            // 纯原词
            Text(
                text = primary,
                fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                color = if (isHovered) tokens.color.primary else tokens.color.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
