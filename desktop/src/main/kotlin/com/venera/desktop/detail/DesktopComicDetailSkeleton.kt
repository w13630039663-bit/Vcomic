package com.venera.desktop.detail

import com.venera.desktop.ui.DesktopPushButton
import com.venera.compose.components.venera.VeneraShimmer
import com.venera.desktop.gallery.ui.LocalChromePalette
import com.venera.desktop.platform.desktopErrorKindOfText
import com.venera.desktop.ui.components.DesktopPetCueBus
import com.venera.desktop.ui.components.petCueForError
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.venera.compose.ui.tokens.VeneraTokens

// ════════════════════════════ 装载骨架 ════════════════════════════

/** 基础占位块：走统一扫光骨架的强调档，静态色块看着像卡死。 */
@Composable
internal fun SkeletonBlock(modifier: Modifier = Modifier) {
    val palette = LocalChromePalette.current
    val shape = RoundedCornerShape(4.dp)
    VeneraShimmer(
        modifier = modifier.border(0.5.dp, palette.hairline, shape),
        shape = shape,
        emphasized = true,
    )
}

/** 顶部横幅骨架：作品名照 route 念真值，其余槽位占位。 */
@Composable
internal fun SkeletonHero(
    title: String,
    note: String,
    error: String?,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 详情页装载失败是共享层自绘的骨架 + 短句，不走桌面大失败位；
    // 在这里按归档文案补一次归类，内容取不到 / 无网络都落到角色 ERROR / WARNING。
    LaunchedEffect(error) {
        if (!error.isNullOrBlank()) {
            DesktopPetCueBus.emit(petCueForError(desktopErrorKindOfText(error.orEmpty())))
        }
    }
    val tokens = VeneraTokens
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = tokens.spacing.space3),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space4),
    ) {
        SkeletonBlock(Modifier.width(92.dp).height(128.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.space3),
        ) {
            Text(
                text = title,
                fontSize = VeneraTokens.type.screenTitle,
                color = tokens.color.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            SkeletonBlock(Modifier.fillMaxWidth(0.6f).height(8.dp))
            SkeletonBlock(Modifier.fillMaxWidth(0.4f).height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2)) {
                SkeletonBlock(Modifier.width(96.dp).height(26.dp))
                SkeletonBlock(Modifier.width(88.dp).height(26.dp))
                SkeletonBlock(Modifier.width(72.dp).height(26.dp))
            }
            Text(
                text = error ?: note,
                fontSize = VeneraTokens.type.caption,
                color = if (error != null) tokens.color.textSecondary else tokens.color.textTertiary,
            )
            // 返回由壳层顶栏承担，内容区只在失败时摆重试。
            if (error != null) {
                DesktopPushButton(onClick = onRetry) {
                    Text(com.venera.desktop.settings.DesktopI18n.t("重试", "Retry"), fontSize = VeneraTokens.type.body)
                }
            }
        }
    }
}

/** 主列骨架：预览图集 / 评论输入 / 评论行 / 推荐区。 */
@Composable
internal fun SkeletonMainColumn(modifier: Modifier = Modifier) {
    val tokens = VeneraTokens
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        SkeletonBlock(Modifier.fillMaxWidth().height(180.dp))
        SkeletonBlock(Modifier.fillMaxWidth().height(40.dp))
        SkeletonBlock(Modifier.fillMaxWidth(0.7f).height(8.dp))
        SkeletonBlock(Modifier.fillMaxWidth(0.5f).height(8.dp))
        SkeletonBlock(Modifier.fillMaxWidth().height(96.dp))
        Spacer(Modifier.height(tokens.spacing.space3))
    }
}

/** 侧列章节目录骨架：固定 8 行，不由未到位的真实章节数驱动组合量。 */
@Composable
internal fun SkeletonChapterList(
    columns: Int,
    maxHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    Column(
        modifier = modifier.fillMaxWidth().heightIn(max = maxHeight),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
    ) {
        SectionHead(title = com.venera.desktop.settings.DesktopI18n.t("章节目录", "Chapters"))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(8) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    repeat(columns) {
                        SkeletonBlock(Modifier.weight(1f).height(30.dp))
                    }
                }
            }
        }
    }
}

/** 装载态整页骨架：与详情屏同构，数据到达时骨架就地换真值。 */
@Composable
internal fun ComicDetailSkeleton(
    title: String,
    note: String,
    error: String?,
    wide: Boolean,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = VeneraTokens.spacing.screenHorizontal),
    ) {
        // 返回统一由壳层顶栏承担，装载态内容区不摆第二枚返回。
        SkeletonHero(
            title = title,
            note = note,
            error = error,
            onRetry = onRetry,
            onBack = onBack,
            modifier = Modifier.fillMaxWidth(),
        )
        // 失败是终态：只留错误与重试，骨架一起收起 —— 否则看着像还在加载。
        if (error != null) return
        Spacer(Modifier.height(tokens.spacing.space3))
        if (wide) {
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(30.dp),
            ) {
                SkeletonMainColumn(Modifier.weight(1f))
                Column(
                    modifier = Modifier.width(340.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    SkeletonChapterList(columns = 2, maxHeight = WIDE_CHAPTER_LIST_MAX_HEIGHT)
                    SkeletonBlock(Modifier.fillMaxWidth().height(64.dp))
                    SkeletonBlock(Modifier.fillMaxWidth().height(96.dp))
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                SkeletonMainColumn(Modifier.weight(1f))
                Spacer(Modifier.height(tokens.spacing.space3))
                Column(modifier = Modifier.height(NARROW_CHAPTER_CARD_HEIGHT).fillMaxWidth()) {
                    SkeletonChapterList(columns = 3, maxHeight = NARROW_CHAPTER_CARD_HEIGHT)
                }
            }
        }
    }
}
