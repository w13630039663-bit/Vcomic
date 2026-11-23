package com.venera.desktop.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.gallery.data.DesktopComicPage
import com.venera.desktop.ui.theme.VeneraType

private val LeftStripSurface = Color(0xF2171218)
private val LeftStripBorder = Color(0x1CFFFFFF)
private val LeftStripTextPrimary = Color(0xFFF3F4F6)
private val LeftStripTextSecondary = Color(0xFFA1A7B3)

/**
 * 常驻左侧缩略条。
 *
 * 占用真实布局宽度，画布按 [LEFT_STRIP_WIDTH] 让位；缩略图与底部页码随当前页联动，
 * 单击缩略图跳页。翻页后自动滚到当前页附近。
 */
@Composable
internal fun ReaderLeftStrip(
    visible: Boolean,
    pages: List<DesktopComicPage>,
    currentPage: Int,
    onSelectPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible || pages.isEmpty()) return
    val tokens = VeneraTokens
    val listState = rememberLazyListState()
    LaunchedEffect(currentPage, pages.size) {
        if (currentPage in pages.indices) {
            listState.animateScrollToItem((currentPage - 2).coerceAtLeast(0))
        }
    }
    val fraction = if (pages.size > 1) currentPage.toFloat() / (pages.size - 1) else 1f
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(LEFT_STRIP_WIDTH)
            .background(LeftStripSurface)
            .drawBehind {
                drawLine(
                    color = LeftStripBorder,
                    start = Offset(size.width - 0.5f, 0f),
                    end = Offset(size.width - 0.5f, size.height),
                    strokeWidth = 1f,
                )
            },
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(pages) { index, page ->
                val selected = index == currentPage
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.7f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (selected) tokens.color.primary.copy(alpha = 0.22f) else Color.Transparent)
                        .padding(2.dp)
                        .clickable { onSelectPage(index) },
                ) {
                    AsyncImage(
                        model = page,
                        contentDescription = com.venera.desktop.settings.DesktopI18n.t("第 ${index + 1} 页", "Page ${index + 1}"),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxHeight().aspectRatio(0.7f).clip(RoundedCornerShape(5.dp)),
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(3.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xB3000000))
                            .padding(horizontal = 3.dp, vertical = 1.dp),
                    ) {
                        Text(
                            text = "${index + 1}",
                            fontSize = VeneraType.aux.size,
                            color = if (selected) tokens.color.primary else LeftStripTextPrimary,
                        )
                    }
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawLine(
                        color = LeftStripBorder,
                        start = Offset(0f, 0.5f),
                        end = Offset(size.width, 0.5f),
                        strokeWidth = 1f,
                    )
                }
                .padding(horizontal = 10.dp, vertical = 9.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${currentPage + 1} / ${pages.size}",
                    fontSize = VeneraType.aux.size,
                    fontWeight = FontWeight.Medium,
                    color = LeftStripTextPrimary,
                )
                Text(
                    text = "${(fraction * 100).toInt()}%",
                    fontSize = VeneraType.aux.size,
                    color = LeftStripTextSecondary,
                )
            }
            Spacer(Modifier.height(7.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(Color(0x33FFFFFF)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction.coerceIn(0.01f, 1f))
                        .fillMaxHeight()
                        .background(tokens.color.primary),
                )
            }
        }
    }
}

/** 左侧缩略条占位宽度；画布按同值让位，避免遮挡。 */
internal val LEFT_STRIP_WIDTH = 120.dp
