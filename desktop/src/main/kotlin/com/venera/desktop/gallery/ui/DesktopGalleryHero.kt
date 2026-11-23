package com.venera.desktop.gallery.ui

import com.venera.desktop.ui.theme.VeneraType
import com.venera.desktop.ui.DesktopPushButton
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.venera.compose.components.venera.VeneraTextButton
import com.venera.compose.gallery.domain.GalleryHomeDomain
import com.venera.compose.ui.tokens.VeneraTokens

private val DesktopGalleryHeroHeight = 232.dp

/**
 * UI-2 的画廊头图带。
 *
 * 这版不再用“一条 60dp 的紫色渐变 + 标题”冒充 hero：左侧给标题、说明与真实动作，
 * 右侧给这一轮已到货的读数。读数只搬调用点递进来的成品（墙卡数与今日精选数），
 * 取不到时就说“正在取数”，不在 UI 里猜一个漂亮数字。
 */
@Composable
internal fun DesktopGalleryHero(
    domain: GalleryHomeDomain,
    wallCount: Int?,
    dailyCount: Int,
    onOpenSearch: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenBrowseHistory: () -> Unit,
    /**
     * PIXIV 域主按钮「打开我的 pixiv」的落点（1247`）。
     * **必传无默认值**（评审 R8 那一族）：BOORU 域这一枚不渲，但调用点照样得接 ——
     * 给默认 `{}` 就是把"切到 Pixiv 点主按钮什么都不动"埋回屏里。
     */
    onOpenPixivAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    val shape = RoundedCornerShape(tokens.shape.large)
    val heroBrush = Brush.linearGradient(
        listOf(
            tokens.color.background,
            tokens.color.surfaceVariant,
            tokens.color.surfaceContainerHigh,
        ),
    )
    val sourceLabel = when (domain) {
        GalleryHomeDomain.BOORU -> "CURATED NIGHT · 夜间精选"
        GalleryHomeDomain.PIXIV -> "PIXIV NIGHT · 榜单精选"
    }
    val subtitle = when (domain) {
        // `:1158` / `:1244` 两句副标逐字搬入（口径：来源/画师/收藏收在卡上，
        // 主墙随窗口宽 4 → 7 列；上一版那句"都收在下一排"说的 chrome 行已改形）。
        GalleryHomeDomain.BOORU -> "作品先被看见。来源、画师与收藏动作收在同一排，窗口越宽，主墙自动从 4 列增长到 7 列。"
        GalleryHomeDomain.PIXIV -> "日榜、周榜、关注动态与作品墙共用同一套密度。主墙按列数随窗口扩展，卡图按站方原比例摆。"
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(DesktopGalleryHeroHeight)
            .clip(shape)
            .background(heroBrush),
    ) {
        // 右侧两团很淡的夜海光斑。它们是装饰，不承载任何读数或点击。
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = tokens.spacing.space9, end = tokens.spacing.space10)
                .size(220.dp)
                .background(
                    Brush.radialGradient(
                        listOf(tokens.color.primary.copy(alpha = 0.2f), Color.Transparent),
                    ),
                    CircleShape,
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = tokens.spacing.space8, end = tokens.spacing.space11)
                .size(150.dp)
                .background(
                    Brush.radialGradient(
                        listOf(tokens.color.tertiaryContainer.copy(alpha = 0.24f), Color.Transparent),
                    ),
                    CircleShape,
                ),
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = tokens.spacing.space10, vertical = tokens.spacing.space9),
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space10),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = sourceLabel,
                    fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                    fontWeight = tokens.type.weightSemibold,
                    color = Color.White.copy(alpha = 0.72f),
                )
                Spacer(Modifier.height(tokens.spacing.space3))
                Text(
                    // 两域各有自己的 h1（图库站 = 画廊，Pixiv = Pixiv）。
                    // 从前恒写「画廊」而 Pixiv 域只有 kicker 跟着换，稿的页头是两条独立版面。
                    text = if (domain == GalleryHomeDomain.PIXIV) "Pixiv" else "画廊",
                    fontSize = VeneraType.statNumber.size, lineHeight = VeneraType.statNumber.lineHeight,
                    fontWeight = tokens.type.weightBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(tokens.spacing.space2))
                Text(
                    text = subtitle,
                    fontSize = VeneraType.body.size, lineHeight = VeneraType.body.lineHeight,
                    color = Color.White.copy(alpha = 0.78f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(tokens.spacing.space6))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // `:1161` / `:1247`：主按钮随域换落点
                    // （图库站「搜索作品」/ Pixiv「打开我的 pixiv」），两枚 ghost（收藏 / 历史）
                    // 两域共用 —— 稿上它们本来就在同一排，且两域的落点是同两扇门。
                    if (domain == GalleryHomeDomain.PIXIV) {
                        DesktopPushButton(onClick = onOpenPixivAccount) { Text("打开我的 pixiv") }
                    } else {
                        DesktopPushButton(onClick = onOpenSearch) { Text("搜索作品") }
                    }
                    VeneraTextButton(text = "收藏", onClick = onOpenBookmarks)
                    DesktopPushButton(onClick = onOpenBrowseHistory) { Text("历史") }
                }
            }
            Column(
                modifier = Modifier.width(250.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = if (domain == GalleryHomeDomain.PIXIV) "PIXIV NIGHT" else "NIGHT SEA",
                    fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                    fontWeight = tokens.type.weightSemibold,
                    color = Color.White.copy(alpha = 0.58f),
                )
                Spacer(Modifier.height(tokens.spacing.space3))
                Text(
                    text = wallCount?.let { "$it 张已入墙" } ?: "正在铺开今天的墙",
                    fontSize = VeneraType.cardTitle.size, lineHeight = VeneraType.cardTitle.lineHeight,
                    fontWeight = tokens.type.weightSemibold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(tokens.spacing.space1))
                Text(
                    // 第二行随域念各自真知道的事：booru 有每日精选池（dailyCount 是成品读数），
                    // pixiv 那面墙取的是日榜档 —— "今日精选"那句在 Pixiv 域不成立，不许借。
                    text = if (domain == GalleryHomeDomain.PIXIV) {
                        "榜单取数 · 全宽自适应"
                    } else {
                        "$dailyCount 张今日精选 · 全宽自适应"
                    },
                    fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                )
            }
        }
    }
}

