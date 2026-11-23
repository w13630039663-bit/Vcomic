package com.venera.compose.feature.explore

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import com.venera.compose.components.venera.VeneraChip
import com.venera.compose.components.venera.VeneraTagChip
import com.venera.compose.source.explore.ExploreMode
import com.venera.compose.source.explore.NativeEntry
import com.venera.compose.source.explore.NativeSection
import com.venera.compose.source.explore.SourceExploration
import com.venera.compose.source.explore.UnifiedTag
import com.venera.compose.ui.tokens.VeneraTokens
import top.yukonga.miuix.kmp.basic.Text

/**
 * 探索屏的通用 chrome（共享件）。
 *
 * 从 `UnifiedExploreScreen.kt` 抽离：本文件里 6 颗 composable 零 Android 运行时（无
 * `LocalContext` / `ContentGuardManager` / `ComicSourceManager` / `viewModel()` /
 * `CircularWavyProgressIndicator`），两端都能编。留在原屏的是 `CenteredLoader`
 * （桌面 material3 jar 没波浪环）与 `ExploreActionRow`（UiAuditBatch7Test 把
 * `if (filterCount > 0) {` 钉在 `UnifiedExploreScreen.kt` 的字面判式上）。
 *
 * 本件不承担跨平台重构 —— Android 那侧从 `private` 变成 `internal`，调用形态一字不动；
 * 桌面探索屏（`com.venera.desktop.explore.DesktopExploreScreen`）走自己那套网格形状，
 * 暂时不做二消费。抽出的收益是把 `UnifiedExploreScreen.kt` 从「1459 行巨壳」拆回
 * 「壳 + 卡片层 + chrome 层」的三段结构，也便于后续按需加消费点。
 */

/* ------------------------------------------------------------------ *
 * 第一层：漫画源选择器
 * ------------------------------------------------------------------ */

/**
 * 源选择器：大卡片锚点 + 自绘下拉（第二版，用户点名「改成这种比较大的显示」）。
 *
 * 为什么自绘而不是官方 `ExposedDropdownMenuBox`：那一件在深色主题下弹的是半透明
 * surface 菜单、锚点格也没有描边，与本仓其余 5 处 `DropdownMenu`（实底菜单 +
 * 描边锚点，见 SearchScreen 的 SearchDropdownOption）是两套视觉语言 —— 探索页是
 * 工具栏，违和写在脸上。
 *
 * 锚点是一张**两行卡片**：书本图标 + 源名（大号加粗）+ 「当前漫画源 · 点击切换」+ 箭头。
 * 一行细条读不出「这里能换源」（报「改成比较大的显示」），
 * 副标题把这一格的用途写死在脸上，源名过长时靠 `Ellipsis` 收尾，不换行、不撑高。
 *
 * 菜单宽度 = 锚点宽度（用户点名「点击后有和这个框一样宽度的下拉框」）：锚点实测宽经
 * `onSizeChanged` 记下，再按密度折回 dp 喂给 `DropdownMenu`。首帧还没量到时退回内容自适应，
 * 免得弹出一个 0dp 的空菜单。
 *
 * 34 颗源一屏放不下：菜单内容是纵向滚动的 Column，上限约 6 行源项
 * （`sheetActionHeight * 6`，吃既有 token，不造新数）。
 *
 * `enabled` 不是装饰：源列表还在拉时点它只会弹出一个空菜单，所以这一格直接置灰。
 */
@Composable
internal fun SourceDropdownField(
    sources: List<SourceExploration>,
    selectedKey: String?,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val current = sources.firstOrNull { it.sourceKey == selectedKey }
    // 锚点实测宽（px）。菜单要跟它等宽，见上面那条注释。
    var anchorWidthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    Box {
        Surface(
            shape = RoundedCornerShape(VeneraTokens.shape.large),
            color = VeneraTokens.color.surfaceContainerHigh,
            border = BorderStroke(VeneraTokens.spacing.hairlineThin, VeneraTokens.color.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { anchorWidthPx = it.width }
                .clickable(enabled = enabled) { expanded = true },
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = VeneraTokens.spacing.chipHorizontalPadding,
                    vertical = VeneraTokens.spacing.space6,
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(VeneraTokens.spacing.space6),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                    contentDescription = null,
                    tint = VeneraTokens.color.primary,
                    modifier = Modifier.size(VeneraTokens.spacing.space10),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(VeneraTokens.spacing.space1),
                ) {
                    Text(
                        text = current?.sourceName ?: "选择漫画源",
                        fontSize = VeneraTokens.type.itemTitle,
                        fontWeight = VeneraTokens.type.weightBold,
                        color = if (current != null) VeneraTokens.color.textPrimary else VeneraTokens.color.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "当前漫画源 · 点击切换",
                        fontSize = VeneraTokens.type.caption,
                        color = VeneraTokens.color.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    imageVector = Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = VeneraTokens.color.textTertiary,
                    modifier = Modifier.size(VeneraTokens.spacing.space9),
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            // 菜单与锚点等宽；锚点未量到时（首帧）不加宽度约束，退回内容自适应。
            modifier = if (anchorWidthPx > 0) {
                Modifier.width(with(density) { anchorWidthPx.toDp() })
            } else {
                Modifier
            },
            shape = RoundedCornerShape(VeneraTokens.shape.large),
            containerColor = VeneraTokens.color.surface,
            border = BorderStroke(VeneraTokens.spacing.hairlineThin, VeneraTokens.color.outlineVariant),
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = VeneraTokens.spacing.sheetActionHeight * 6)
                    .verticalScroll(rememberScrollState()),
            ) {
                sources.forEach { source ->
                    val selected = source.sourceKey == selectedKey
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    text = source.sourceName,
                                    color = if (selected) {
                                        VeneraTokens.color.primary
                                    } else {
                                        VeneraTokens.color.textPrimary
                                    },
                                )
                                // 探测失败的源在菜单里就标明身份：不是这颗源不能探索，
                                // 是这次没探测出来 —— 点进去有重试。
                                if (source.probeFailed) {
                                    Text(
                                        text = "暂不可达 · 点击进入可重试",
                                        fontSize = VeneraTokens.type.caption,
                                        color = VeneraTokens.color.textTertiary,
                                    )
                                }
                            }
                        },
                        onClick = {
                            expanded = false
                            if (!selected) onSelect(source.sourceKey)
                        },
                    )
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ *
 * 区块标题
 * ------------------------------------------------------------------ */

/**
 * 探索屏内的小节标题（25dp 上留白 + 加粗文本）。
 *
 * ⚠️ 与共享件 `feature/SectionHeader.kt` 里那颗 `MiuixSectionHeader` 不是一枚：
 * 那颗是「标题 + 右侧动作」两栏形状，恒一档由 UiAuditG3Test 钉着；
 * 这一枚只是探索屏内多处使用的一个字面标题，不吞动作位。
 */
@Composable
internal fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = VeneraTokens.type.itemTitle,
        fontWeight = VeneraTokens.type.weightBold,
        color = VeneraTokens.color.textPrimary,
        modifier = Modifier.padding(bottom = VeneraTokens.spacing.space2)
    )
}

/* ------------------------------------------------------------------ *
 * 第二层：探索方式
 * ------------------------------------------------------------------ */

@Composable
internal fun ExploreModeRow(
    modes: List<ExploreMode>,
    selectedId: String?,
    onSelect: (ExploreMode) -> Unit,
) {
    // ⚠️ 这里**不再有** `SectionHeader("探索方式")`：源名已经在副标题里，模式行摊在它下面
    // 人人都看得懂，再压一枚 25dp 的标题就是 chrome 里最贵的那一行（UiAuditBatch7Test 钉着）。
    // 整行的显隐见 `exploreModeRowVisible`：只有一枚模式时连行带题一起不渲染。
    LazyRow(horizontalArrangement = Arrangement.spacedBy(VeneraTokens.spacing.chipSpacing)) {
        items(modes, key = { it.id }) { mode ->
            val selected = mode.id == selectedId
            VeneraChip(
                text = mode.label,
                selected = selected,
                onClick = { if (!selected) onSelect(mode) },
            )
        }
    }
}

/* ------------------------------------------------------------------ *
 * 第三层：源原生分类 / Tag（原样呈现，不合并）
 * ------------------------------------------------------------------ */

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NativeSectionBlock(
    section: NativeSection,
    onEntryClick: (NativeEntry) -> Unit,
    /**
     * 已生效的那一枚入口。**只有页内生效的组**（排行榜）需要传：分类组的点击是跳走，
     * 本页没有"生效中"的概念，传了就是把一枚跳走的入口染成选中色谎报状态。
     */
    selectedEntry: NativeEntry? = null,
) {
    Column {
        SectionHeader(section.name)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(VeneraTokens.spacing.chipSpacing),
            verticalArrangement = Arrangement.spacedBy(VeneraTokens.spacing.chipSpacing),
            modifier = Modifier.fillMaxWidth(),
        ) {
            section.items.forEach { entry ->
                VeneraChip(
                    text = entry.label,
                    selected = entry == selectedEntry,
                    // 分类区的入口就是内容本身，读不全等于没有。卡片里那些标签仍留 1 行
                    // （整卡高度由标签区驱动），所以这是逐点开的名词，不是组件改默认值。
                    textMaxLines = 2,
                    onClick = { onEntryClick(entry) },
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ *
 * 辅助层：通用标签（VeneraTagChip 描边样式，与源原生标签明确区分）
 * ------------------------------------------------------------------ */

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun UnifiedTagBlock(
    tags: List<UnifiedTag>,
    selectedTag: UnifiedTag?,
    onTagClick: (UnifiedTag) -> Unit,
) {
    // ⚠️ 那行「跨源快捷筛选：点击即在下方筛当前源，再点一次取消。」提示**已删**
    // 整块改由动作行「筛选 · N」撑开，撑开即上下文，
    // 一行常驻说明文字是白占 21dp chrome。动作行右槽担起"现在筛的是什么"那句话。
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(VeneraTokens.spacing.chipSpacing),
        verticalArrangement = Arrangement.spacedBy(VeneraTokens.spacing.chipSpacing),
        modifier = Modifier.fillMaxWidth(),
    ) {
        tags.forEach { tag ->
            VeneraTagChip(
                text = tag.label,
                selected = tag == selectedTag,
                textMaxLines = 2,
                onClick = { onTagClick(tag) },
            )
        }
    }
}

/** 「本源分类」的展开 / 收起入口（默认只露前 8 个入口，避免首屏被分类占满）。 */
@Composable
internal fun SectionMoreButton(hiddenCount: Int, expanded: Boolean, onClick: () -> Unit) {
    Text(
        text = if (expanded) "收起分类" else "更多分类（还有 $hiddenCount 个）",
        fontSize = VeneraTokens.type.caption,
        fontWeight = VeneraTokens.type.weightSemibold,
        color = VeneraTokens.color.primary,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(VeneraTokens.shape.medium))
            .clickable(onClick = onClick)
            .padding(vertical = VeneraTokens.spacing.space3),
    )
}
