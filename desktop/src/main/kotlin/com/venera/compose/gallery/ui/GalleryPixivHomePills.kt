package com.venera.compose.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.venera.compose.components.venera.VeneraPressFeedback
import com.venera.compose.gallery.data.PixivRankingMode
import com.venera.compose.ui.tokens.VeneraTokens
import top.yukonga.miuix.kmp.basic.Text

/**
 * 排行榜档名那一排 / 「换一批」/ 「查看全部」这三枚药丸 ——从 `GalleryPixivHome.kt` 搬出来的。
 *
 * ## 为什么单独一颗文件
 *
 * 与 `GalleryHomeDomainChips`（`TEXT_LINE_HEIGHT`、那颗分段器）
 * 同一手法：`GalleryPixivHome.kt` 是 A+B 双类成因（`android.*` + CustomTabs + 一堆被排除的同包兄弟），
 * 整颗在桌面编译面外。而这三枚药丸自己**零禁引** —— 只吃 `PixivRankingMode`（在干净的 `gallery/data`）、
 * `VeneraTokens` / `TEXT_LINE_HEIGHT` / `VeneraPressFeedback`（全是已接进编译面的叶子）。
 * 搬出来之后，桌面那颗 pixiv 首页的「排行榜 / 推荐」两节才不必整颗拖进 `GalleryPixivHome.kt` 这坨脏件。
 *
 * 搬的时候**函数体一字未改**（只补了 import、把那枚 `[GalleryPixivHome.RANKING_EXPANDED_ITEMS]` 的
 * KDoc 链接换成散文 —— 那颗 object 留在被排除的脏件里，链接不解析只是 Dokka 警告、不影响编译），
 * 所以屏上观感与搬之前逐字相同。
 */

/** 排行榜的档名那一排（日榜 / 周榜 / 月榜 / 新人 / 原创）。 */
@Composable
internal fun PixivRankingModeChips(
    selected: PixivRankingMode,
    onPick: (PixivRankingMode) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * 是否吃满整行宽：安卓端它坐在整行（默认 `true`，行为逐字不变）；
     * 桌面把它塞进节头右槽时必须传 `false` —— `fillMaxWidth` 会把同槽的「N 张」读数
     * 压成一字一行（竖排），截图指认。
     */
    fill: Boolean = true,
    /** 放大档：桌面排行榜节头右侧需要更大的可点药丸。 */
    large: Boolean = false,
) {
    // 五档都是现量匿名 200 的那五档；R-18 两档匿名 403 ⇒ 一枚都不摆。
    PixivPillRow(
        items = PixivRankingMode.entries.toList(),
        selected = selected,
        labelOf = { it.label },
        onPick = onPick,
        modifier = modifier,
        fill = fill,
        large = large,
    )
}

/**
 * 「换一批」那枚单枚药丸。
 *
 * 视觉上就是 [PixivPillRow] 里的**一枚**：同样的 `percent = 50` 圆角、同样的底色与内衬档，
 * 所以它与同屏那两排分段器读起来是同一套控件 —— 一个尺寸不同的动作胶囊会被读成另一种东西。
 *
 * 取数里那一档才是"能不能摆"的前提，理由写在 `PIXIV_RECOMMEND` 那一节里。
 */
@Composable
internal fun PixivRemixChip(
    loading: Boolean,
    onRemix: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(tokens.color.surfaceVariant)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = VeneraPressFeedback.indication,
                enabled = !loading,
            ) { onRemix() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (loading) "正在换" else "换一批",
            // 字号与内衬都放大一档。
            // 它是节头那一行的**唯一动作**，与同屏那两排分段器不是同一档东西 ——
            // 分段器是筛选（一排摆五档），这枚是单个把手，缩成筛选器那一档就找不到了。
            fontSize = tokens.type.itemTitle,
            // 行高同样钉住（与那两排同源：它坐在定高白卡旁的节头行里）。
            lineHeight = TEXT_LINE_HEIGHT.em,
            fontWeight = tokens.type.weightMedium,
            color = if (loading) tokens.color.textTertiary else tokens.color.textSecondary,
            maxLines = 1,
            modifier = Modifier.padding(
                horizontal = tokens.spacing.space6,
                vertical = tokens.spacing.space3,
            ),
        )
    }
}

/** 两排药丸共用的一颗：选中那枚换填充，未选中那枚走 `surfaceVariant`（不画线，描边那版早被否）。 */
@Composable
private fun <T> PixivPillRow(
    items: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onPick: (T) -> Unit,
    modifier: Modifier = Modifier,
    fill: Boolean = true,
    large: Boolean = false,
) {
    val tokens = VeneraTokens
    Row(
        modifier = if (fill) modifier.fillMaxWidth() else modifier,
        horizontalArrangement = Arrangement.spacedBy(if (large) 8.dp else tokens.spacing.space3),
    ) {
        items.forEach { item ->
            val isOn = item == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(if (isOn) tokens.color.primaryContainer else tokens.color.surfaceVariant)
                    .clickable { onPick(item) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = labelOf(item),
                    fontSize = if (large) 13.5.sp else tokens.type.caption,
                    // 行高写死：这一枚切换时字重跳，行高必须钉住（理由见 `TEXT_LINE_HEIGHT` 的注）。
                    lineHeight = TEXT_LINE_HEIGHT.em,
                    fontWeight = if (isOn) tokens.type.weightSemibold else tokens.type.weightRegular,
                    color = if (isOn) tokens.color.onPrimaryContainer else tokens.color.textPrimary,
                    maxLines = 1,
                    modifier = Modifier.padding(
                        horizontal = if (large) 15.dp else tokens.spacing.space4,
                        vertical = if (large) 6.dp else tokens.spacing.space1,
                    ),
                )
            }
        }
    }
}

/**
 * 「排行榜」节头右上的那一枚把手。
 *
 * 它**不是**二级页入口（这一批没有新路由），但行为是真的：点下去这一排从三张扩到
 * 收起态那一档上限张数（`GalleryPixivHome.RANKING_EXPANDED_ITEMS`），再点收回。稿上那句「查看全部」
 * 先按"展开"实现，等接了 pixiv 榜单二级页再换成跳页 —— 那时这一枚改一个字就行。
 */
@Composable
internal fun PixivRankingShowAll(
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    // 「查看全部 ›」：节头 trailing 现在贴右缘（见 `MiuixSectionHeader`），
    // 配一枚 › 才读成"去另一个地方"，否则那段灰字像标题的注脚。
    Row(
        modifier = modifier.clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space1),
    ) {
        Text(
            text = if (expanded) "收起" else "查看全部",
            fontSize = tokens.type.caption,
            color = tokens.color.textTertiary,
        )
        // 收起态不摆箭头：那一下是"原地收回"，不是"去某处"。
        if (!expanded) {
            Text(
                text = "›",
                fontSize = tokens.type.chevron,
                color = tokens.color.textTertiary,
            )
        }
    }
}
