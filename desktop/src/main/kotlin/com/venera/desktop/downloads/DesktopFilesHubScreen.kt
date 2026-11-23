package com.venera.desktop.downloads

import com.venera.desktop.ui.theme.VeneraType
import com.venera.desktop.gallery.ui.GalleryTokens
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.ui.DesktopPageShell
import com.venera.desktop.ui.DesktopPageWidth

/**
 * 「下载与本地」合并页（合并 ·设计稿 v3 逐区对齐）。
 *
 * 设计稿的两层头部照搬：
 * - **tbar**：标题在页壳，**域段（漫画|图片）是连体胶囊分段器**，靠右（`tbar .topseg` 那一枚）；
 * - **sub2**：档位是**下划线 tab + 计数圆角胶囊**（不是选块），图片库那一档的行尾 gear 区挂
 *   **「分组方式」四档小分段器**（`sub2 .seg2`），组档状态在壳（换域/换档不丢）。
 *
 * 每档内容由壳转交原屏（`embedded = true`）——列表、删除两步、补齐入队、导入这些逻辑
 * 一条都不重写，避免"合并 = 复制一份"。
 */
internal enum class DesktopFilesDomain(val label: String) {
    COMIC("漫画"),
    IMAGE("图片"),
}

internal enum class DesktopFilesSegment(val label: String, val domain: DesktopFilesDomain) {
    QUEUE("下载队列", DesktopFilesDomain.COMIC),
    DOWNLOADED("本机已下载", DesktopFilesDomain.COMIC),
    LOCAL("本地漫画", DesktopFilesDomain.COMIC),
    /** 图片腿今天没有队列（存图是同步落盘）——这一档如实点名缺位，不摆假空表。 */
    IMAGE_QUEUE("下载队列", DesktopFilesDomain.IMAGE),
    IMAGE_LIBRARY("本机已下载", DesktopFilesDomain.IMAGE),
}

@Composable
internal fun DesktopFilesHubScreen(
    mode: WideScreenLayoutMode,
    domain: DesktopFilesDomain,
    onPickDomain: (DesktopFilesDomain) -> Unit,
    segment: DesktopFilesSegment,
    onPickSegment: (DesktopFilesSegment) -> Unit,
    /** 档位那枚条数角（null = 不摆角；读数由壳给，屏不现算）。 */
    countOf: (DesktopFilesSegment) -> String?,
    /** 图片库分组档（仅 图片域·本机已下载 那一档在 tab 行尾摆四档分段器）。 */
    imageGroupMode: DesktopImageGroupMode,
    onPickImageGroupMode: (DesktopImageGroupMode) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val tokens = VeneraTokens
    DesktopPageShell(
        mode = mode,
        title = com.venera.desktop.settings.DesktopI18n.t("下载与本地", "Downloads & Local"),
        subtitle = null,
        onBack = onBack,
        modifier = modifier,
        width = DesktopPageWidth.Browse,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── tbar：域段靠右的连体分段器（设计稿 topseg）──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f))
                DomainSegmented(
                    options = DesktopFilesDomain.entries.map { com.venera.desktop.settings.DesktopI18n.t(it.label) },
                    selectedIndex = DesktopFilesDomain.entries.indexOf(domain),
                    onSelect = { i ->
                        val picked = DesktopFilesDomain.entries[i]
                        if (picked != domain) {
                            onPickDomain(picked)
                            // 换域落到该域第一档（档位不跨域串门）。
                            DesktopFilesSegment.entries.first { it.domain == picked }.let(onPickSegment)
                        }
                    },
                )
            }
            // ── sub2：下划线档位 tab + 计数角 + 行尾 gear（图片库档才挂分组分段器）──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = 1.dp, color = tokens.color.outlineVariant.copy(alpha = 0.35f)),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                DesktopFilesSegment.entries.filter { it.domain == domain }.forEach { tab ->
                    UnderlineTab(
                        label = com.venera.desktop.settings.DesktopI18n.t(tab.label),
                        count = countOf(tab),
                        selected = tab == segment,
                        onClick = { if (tab != segment) onPickSegment(tab) },
                    )
                }
                if (domain == DesktopFilesDomain.IMAGE && segment == DesktopFilesSegment.IMAGE_LIBRARY) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Text(
                            text = com.venera.desktop.settings.DesktopI18n.t("分组方式", "Group by"),
                            fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                            color = tokens.color.textTertiary,
                            modifier = Modifier.padding(end = 8.dp, bottom = 10.dp),
                        )
                        MiniSegmented(
                            options = DesktopImageGroupMode.entries.map { com.venera.desktop.settings.DesktopI18n.t(it.label) },
                            selectedIndex = DesktopImageGroupMode.entries.indexOf(imageGroupMode),
                            onSelect = { i -> onPickImageGroupMode(DesktopImageGroupMode.entries[i]) },
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                } else {
                    Box(modifier = Modifier.weight(1f))
                }
            }
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                content()
            }
        }
    }
}

/** 设计稿 `.topseg`：与画廊顶栏同一颗 [com.venera.desktop.ui.DesktopSegmented]。 */
@Composable
private fun DomainSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    com.venera.desktop.ui.DesktopSegmented(
        options = options,
        selectedIndex = selectedIndex,
        onSelect = onSelect,
    )
}

/** 设计稿 `.seg2`：与画廊顶栏同一颗 [com.venera.desktop.ui.DesktopSegmented]。 */
@Composable
private fun MiniSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    com.venera.desktop.ui.DesktopSegmented(
        options = options,
        selectedIndex = selectedIndex,
        onSelect = onSelect,
        modifier = modifier,
    )
}

/** 设计稿 `.tab`：文字 + 计数胶囊，选中压一道 2dp 主题色下划线。 */
@Composable
private fun UnderlineTab(
    label: String,
    count: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tokens = VeneraTokens
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(end = 2.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier.padding(top = 8.dp, bottom = 10.dp),
        ) {
            Text(
                text = label,
                fontSize = VeneraType.bodySecondary.size, lineHeight = VeneraType.bodySecondary.lineHeight,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) tokens.color.textPrimary else tokens.color.textTertiary,
            )
            if (!count.isNullOrBlank()) {
                Text(
                    text = count,
                    fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                    color = tokens.color.textSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(tokens.color.surfaceVariant.copy(alpha = 0.6f))
                        .padding(horizontal = 7.dp, vertical = 1.dp),
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(if (selected) tokens.color.primary else Color.Transparent),
        )
    }
}
