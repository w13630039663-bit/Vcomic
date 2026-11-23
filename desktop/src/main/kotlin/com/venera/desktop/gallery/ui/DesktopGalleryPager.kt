package com.venera.desktop.gallery.ui

import com.venera.desktop.ui.theme.VeneraType
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.ui.tokens.VeneraTokens

/**
 * 图库站：现代桌面分页舱 (DesktopGalleryPager)。
 *
 * 用于 BOORU 域的确定性分页浏览：
 * - 上一页 / 页码序列 / 下一页；
 * - 每页数量分段切换（50 / 100 / 200）；
 * - 键盘快捷键提示（← / → 翻页）。
 */
@Composable
internal fun DesktopGalleryPager(
    currentPage: Int,
    totalPages: Int,
    pageSize: Int,
    onPickPage: (Int) -> Unit,
    onPickPageSize: (Int) -> Unit,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    val shape = GalleryTokens.Shape.Bento
    val canPrev = currentPage > 1
    val canNext = currentPage < totalPages

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = tokens.spacing.screenHorizontal)
            .clip(shape)
            .background(GalleryTokens.Color.SurfaceLevel1)
            .border(
                width = DesktopGalleryMetrics.hairline,
                color = GalleryTokens.Color.OutlineHairline,
                shape = shape,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 左段：翻页器序列
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // 上一页
                PagerNavButton(
                    text = "←",
                    enabled = canPrev,
                    onClick = onPrevPage,
                )

                // 智能页码生成
                val pageNumbers = calculatePageNumbers(currentPage, totalPages)
                pageNumbers.forEach { num ->
                    if (num == -1) {
                        Text(
                            text = "…",
                            color = tokens.color.textTertiary,
                            fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    } else {
                        val isCurrent = num == currentPage
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .then(
                                    if (isCurrent) {
                                        Modifier.background(tokens.color.primary)
                                    } else {
                                        Modifier
                                            .background(Color(0x0FFFFFFF))
                                            .clickable { onPickPage(num) }
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = num.toString(),
                                fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                color = if (isCurrent) Color.White else tokens.color.textSecondary,
                            )
                        }
                    }
                }

                // 下一页
                PagerNavButton(
                    text = "→",
                    enabled = canNext,
                    onClick = onNextPage,
                )
            }

            Spacer(Modifier.weight(1f))

            // 右段：每页数量切换 + 键盘提示
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 每页容量分段器
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x14FFFFFF))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    listOf(50, 100, 200).forEach { size ->
                        val selected = size == pageSize
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .then(if (selected) Modifier.background(tokens.color.primary) else Modifier)
                                .clickable { onPickPageSize(size) }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "每页 $size",
                                fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) Color.White else tokens.color.textTertiary,
                            )
                        }
                    }
                }

                // 键盘快捷键徽标
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    KbdBadge("←")
                    KbdBadge("→")
                    Text(
                        text = "键盘翻页",
                        fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                        color = tokens.color.textTertiary,
                    )
                }
            }
        }
    }
}

@Composable
private fun PagerNavButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val tokens = VeneraTokens
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0x14FFFFFF))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = VeneraType.bodySecondary.size, lineHeight = VeneraType.bodySecondary.lineHeight,
            fontWeight = VeneraType.cardTitle.weight,
            color = if (enabled) Color.White else tokens.color.textTertiary.copy(alpha = 0.38f),
        )
    }
}

@Composable
private fun KbdBadge(key: String) {
    val tokens = VeneraTokens
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0x1AFFFFFF))
            .border(1.dp, Color(0x29FFFFFF), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = key,
            fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
            fontFamily = FontFamily.Monospace,
            color = tokens.color.textSecondary,
        )
    }
}

private fun calculatePageNumbers(current: Int, total: Int): List<Int> {
    if (total <= 7) return (1..total.coerceAtLeast(1)).toList()
    val list = mutableListOf<Int>()
    list.add(1)
    if (current > 3) list.add(-1)
    val start = maxOf(2, current - 1)
    val end = minOf(total - 1, current + 1)
    for (i in start..end) {
        list.add(i)
    }
    if (current < total - 2) list.add(-1)
    if (total > 1) list.add(total)
    return list
}
