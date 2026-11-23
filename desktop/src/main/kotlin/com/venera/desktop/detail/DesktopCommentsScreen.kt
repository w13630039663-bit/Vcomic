package com.venera.desktop.detail

import com.venera.desktop.ui.theme.VeneraType
import com.venera.desktop.ui.DesktopPushButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.compose.components.venera.VeneraCard
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.compose.ui.tokens.StatusColors
import com.venera.engineprobe.DesktopComment
import com.venera.desktop.ui.DesktopPageShell
import com.venera.desktop.ui.ListPageTemplate

/**
 * 桌面「详情页评论」那一屏：**屏只吃形参**，零引擎、零 IO、零 AWT。
 *
 * 壳把 [DesktopCommentsView]（成品状态）连同 `onLoadPage` / `onBack` 递进来；取数那一跳（`load(key)` +
 * `comments(...)`）、能力位那一跳、与读回来的摊合全在壳里（与 §134/§135 同一族拆法）。
 *
 * ## 五档分别（对齐对岸 `CommentLoadStatus` + 空态那两句）
 *
 *  - 顶部：返回 + 题名 + 「再读一页」把手（仅 `hasMore` 且非 loading 时摆）；
 *  - `statusNote` 非空 ⇒ 念那一句（加载中 / 成因 / 该源未提供评论）；
 *  - `emptyNote` 非空 ⇒ 念「读了确实没有评论」那一句（"还没读回来"绝不在这里念，那是 `statusNote` 的事）；
 *  - `items` 非空 ⇒ 逐条摆一张评论卡（作者 / 时间 / 正文 / 回复数）；
 * - 这一批**只读不写**：对岸 `canSend` 那枚「写评论」输入框**不在**（发送评论要再开一条 `sendComment` 引擎跳，
 * 且它会把"读到的列表"与"发出去的那一条"接成一条乐观流 —— 那是另一整块，单列在后面，不冒充已接）。
 */
@Composable
internal fun DesktopCommentsScreen(
    mode: WideScreenLayoutMode,
    /** 那一本的题名（顶栏念，与详情屏同一颗串）。 */
    title: String,
    /** 评论页的成品状态（壳算好递下来，屏不查引擎、不自己拼句子）。 */
    view: DesktopCommentsView,
    /** 「再读一页」：把 `loadMore = true` 落回壳（只有 `hasMore` 时才摆这枚把手）。 */
    onLoadMore: () -> Unit,
    /** 回详情那一屏（二级页必传的出路）。 */
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    val statusNote = desktopCommentsStatusNote(view)
    val emptyNote = desktopCommentsEmptyNote(view)

    DesktopPageShell(
        mode = mode,
        title = title,
        subtitle = statusNote,
        subtitleColor = if (view.error != null) StatusColors.Failing else null,
        onBack = onBack,
        modifier = modifier,
        actions = {
            // 「再读一页」只在真读到底之前摆：hasMore=false 还摆一枚 = 按了没反应（评审 R6 那一族）。
            if (view.hasMore && !view.isLoading) {
                DesktopPushButton(onClick = onLoadMore) { Text(com.venera.desktop.settings.DesktopI18n.t("再读一页", "Load more"), fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight) }
            }
        },
    ) {
        ListPageTemplate(
            items = view.items,
            itemKey = { it.id.ifBlank { it.userName + it.content.hashCode() } },
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.space4),
            emptyContent = {
                if (emptyNote != null) {
                    Text(
                        text = emptyNote,
                        fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                        color = tokens.color.textSecondary,
                    )
                }
            },
        ) { comment ->
            CommentCard(comment = comment)
        }
    }
}

/** 一条评论一张卡：作者 + 时间 + 正文 + 回复数（回复数缺栏就不念，不拿 0 冒充"没人回复"）。 */
@Composable
private fun CommentCard(comment: DesktopComment) {
    val tokens = VeneraTokens
    VeneraCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(tokens.spacing.space4), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = comment.userName,
                    fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                    color = tokens.color.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // 存进局部 val 才 smart-cast 得动（comment.time 是另一模块 data class 的属性，
                // 跨模块不可智能转换 —— 与对岸把 replyCount ?: 0 存成 rc 同一手法）。
                val shownTime = comment.time
                if (shownTime != null) {
                    Text(
                        text = shownTime,
                        fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                        color = tokens.color.textTertiary,
                    )
                }
            }
            Text(
                text = comment.content,
                fontSize = VeneraType.body.size, lineHeight = VeneraType.body.lineHeight,
                color = tokens.color.textPrimary,
            )
            val rc = comment.replyCount
            if (rc != null && rc > 0) {
                Text(
                    text = com.venera.desktop.settings.DesktopI18n.t("$rc 条回复", "$rc replies"),
                    fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                    color = tokens.color.textSecondary,
                )
            }
        }
    }
}
