package com.venera.desktop

import com.venera.desktop.ui.theme.VeneraType
import com.venera.desktop.ui.DesktopPushButton
import com.venera.compose.components.venera.hScrollEndFade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.venera.compose.data.db.HistoryRecord
import com.venera.compose.feature.HistoryCardHome
import com.venera.compose.components.LocalUxPlatformServices
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.components.venera.VeneraCoverMask
import com.venera.compose.ui.tokens.VeneraTokens

/**
 * 首页「历史记录」分区 —— 与安卓 `HomeScreen.kt:335-357` 对应。
 *
 * ## 为什么分区**恒定存在**（这是桌面侧的决定，不是抄安卓）
 *
 * ⚠️ **更正**：本文件早先在这里引 `HomeScreen.kt:171-174` 说"安卓踩过这个坑"。
 * 那条引证是**错的**（架构评审 R5 指出，本喵复核成立）—— 那段注释在
 * `item(key = "stats")` **正上方**，讲的是**第 0 项**的锚位跳动；而历史在列表中部，
 * LazyList 锚的是首个可见项、不是第 0 项，同一机制不成立。
 * 更要紧：**安卓对历史就是条件渲染的**（`if (historyList.isNotEmpty()) { item { … } }`），
 * 它在这件事上没有"正解"可抄。留着那条引证会误导下一个人去改安卓。
 *
 * 真实的理由是**桌面自己的竞态**：
 * 历史数据由 `produceState(initialValue = DesktopHomeHistorySnapshot.Loading)` 起步，
 * 一次异步读之后才到值 ⇒ 这一格会经历「Loading → 有值 / 空」两态。若用 `if` 包 item：
 *   第一帧 item 不存在 → 第二帧 item 出现并插在列表中部 → **它下面的分区整片下移一次**。
 *
 * 所以桌面这里刻意**偏离安卓**：item 恒定存在，只在**内容**上判空。
 * 这是桌面的选择，安卓侧**一个字都不动**。
 * 空历史显示一行说明，而不是让整格消失。
 *
 * ## 数据怎么来
 *
 * `HistoryDao`（`data/db`，已在桌面编译面）。它是 `getHistory(comicId)` 单条 +
 * `historyFlow: StateFlow` 两条口 —— 首屏要列表，只能读 `historyFlow`，
 * 而它的初值是 `emptyList()`、由 `init { refresh() }` 异步填。
 * 这正说明「空」与「还没读到」同形，必须靠"格子恒定存在"来化解。
 */
@Composable
internal fun DesktopHomeHistorySection(
    history: List<HistoryRecord>,
    /** 读到没有（`false` = 还没读回来，与"读了但确实没有记录"要分开说）。 */
    loaded: Boolean,
    /**
     * 读失败时的成因。
     *
     * **必须有这一条形参**：`DesktopHomeHistorySource` 在读失败与 2s 超时两条路径都会
     * 填 `note`，但如果这里不接，屏上就落到 `else -> "还没有阅读记录…"` ——
     * 把「库打不开」谎报成「你没读过书」。那是本文件 KDoc 自己禁止的事，
     * 也是架构与安全两位独立点到的同一处（评审 R3）。
     */
    note: String? = null,
    onOpen: (HistoryRecord) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    // 与安卓同一口径：最多 4 张，横滑。
    val strip = remember(history) { history.take(4) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = com.venera.desktop.settings.DesktopI18n.t("历史记录", "Reading History"),
            fontSize = VeneraType.sectionTitle.size, lineHeight = VeneraType.sectionTitle.lineHeight,
            color = tokens.color.textPrimary,
        )
        Spacer(Modifier.height(tokens.spacing.space2))

        when {
            strip.isNotEmpty() -> {
                // ── UI-3：「继续阅读大卡」──
                // 最近读的那本放大成横幅卡（封面 + 标题/作者 + 读到哪 + 继续阅读钮），
                // 其余记录照旧横滑小卡 —— 首屏一屏能同时看到统计 + 大卡 + 内容排。
                val latest = strip.first()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(tokens.shape.large))
                        .background(tokens.color.surfaceContainerHigh)
                        .clickable { onOpen(latest) }
                        .padding(tokens.spacing.space4),
                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space4),
                ) {
                    Box(
                        modifier = Modifier
                            .width(112.dp)
                            .height(152.dp)
                            .clip(RoundedCornerShape(tokens.shape.medium)),
                    ) {
                        VeneraCover(
                            url = latest.coverUrl,
                            contentDescription = latest.title,
                            mask = VeneraCoverMask.Visible,
                            shimmerWhileLoading = false,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = com.venera.desktop.settings.DesktopI18n.t("继续阅读", "Continue Reading"),
                            fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                            color = tokens.color.primary,
                        )
                        Text(
                            text = latest.title,
                            fontSize = VeneraType.pageTitle.size, lineHeight = VeneraType.pageTitle.lineHeight,
                            fontWeight = VeneraType.cardTitle.weight,
                            color = tokens.color.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                        )
                        if (latest.author.isNotBlank()) {
                            Text(
                                text = latest.author,
                                fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                                color = tokens.color.textSecondary,
                            )
                        }
                        val isEn = com.venera.desktop.settings.LocalDesktopLanguage.current == com.venera.desktop.settings.DesktopLanguage.EN
                        val progress = if (latest.totalPages > 0) {
                            if (isEn) {
                                "Reading ${latest.lastChapterTitle.ifBlank { "Ch. ${latest.lastChapterIndex + 1}" }}" +
                                    " · P. ${latest.lastPageIndex + 1} / ${latest.totalPages}"
                            } else {
                                "读到 ${latest.lastChapterTitle.ifBlank { "第 ${latest.lastChapterIndex + 1} 章" }}" +
                                    " · 第 ${latest.lastPageIndex + 1} / ${latest.totalPages} 页"
                            }
                        } else {
                            latest.lastChapterTitle.ifBlank { null }
                        }
                        progress?.let {
                            Text(
                                text = it,
                                fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                                color = tokens.color.textTertiary,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        Spacer(Modifier.height(tokens.spacing.space2))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DesktopPushButton(
                                onClick = { onOpen(latest) },
                            ) { Text(com.venera.desktop.settings.DesktopI18n.t("继续阅读 →", "Continue →")) }
                        }
                    }
                }
                if (strip.size > 1) {
                    Spacer(Modifier.height(tokens.spacing.space3))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()).hScrollEndFade(VeneraTokens.color.background),
                        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space4),
                    ) {
                        strip.drop(1).forEach { record ->
                            HistoryCardHome(
                                record = record,
                                // 共享零件要 `ux`：内容守卫那条链在它上面。桌面没有 provider 时
                                // 会是 NoOp（遮罩恒 VISIBLE）—— 那个缺口由
                                // `DesktopUxServicesProvidedTest` 钉住，这里只管要。
                                ux = LocalUxPlatformServices.current,
                                onClick = { onOpen(record) },
                            )
                        }
                    }
                }
            }

            // 优先判失败（note 非空），再判未加载完成（!loaded），最后才是空记录，避免停在假加载态。
            note != null -> Text(
                text = note,
                fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                color = tokens.color.textTertiary,
            )

            // 「还没读回来」与「读了，确实没有」是两件事，屏上要分开说。
            // 合成一句话就等于把"库还没开"说成"你没读过书"。
            !loaded -> Text(
                text = com.venera.desktop.settings.DesktopI18n.t("正在读本地历史…", "Loading local history…"),
                fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                color = tokens.color.textTertiary,
            )

            else -> Text(
                text = com.venera.desktop.settings.DesktopI18n.t("还没有阅读记录。读一本之后，这里会出现最近 4 部。", "No reading history yet. Your last 4 comics will appear here."),
                fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                color = tokens.color.textTertiary,
            )
        }
    }
}
