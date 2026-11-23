package com.venera.desktop.gallery.ui

import com.venera.desktop.ui.DesktopButton
import com.venera.desktop.ui.DesktopButtonSize
import com.venera.desktop.ui.DesktopButtonVariant
import com.venera.desktop.ui.DesktopPopUpButton
import com.venera.desktop.ui.DesktopSegmented
import com.venera.desktop.ui.DesktopSwitch
import com.venera.desktop.ui.vShadow
import com.venera.desktop.ui.theme.VeneraType
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.data.GalleryTagSuggestion
import com.venera.compose.gallery.domain.GalleryRanking
import com.venera.compose.gallery.domain.GallerySearchEntry
import com.venera.compose.gallery.domain.PixivBookmark
import com.venera.compose.gallery.domain.PixivRatio
import com.venera.compose.gallery.domain.PixivSearchFilter
import com.venera.compose.gallery.domain.PixivSearchOrder
import com.venera.compose.gallery.domain.PixivTagMode
import com.venera.compose.gallery.domain.PixivWorkType
import com.venera.compose.ui.tokens.LocalVeneraDarkTheme
import java.time.LocalDate

@Composable
internal fun GallerySearchDropdownFlyout(
    history: List<GallerySearchEntry>,
    historyNote: String?,
    onPickHistory: (GallerySearchEntry) -> Unit,
    onClearHistory: () -> Unit,
    suggestCandidates: List<GalleryTagSuggestion>,
    suggestLine: String?,
    onPickSuggestion: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GalleryTokens.Color
    val isDark = LocalVeneraDarkTheme.current
    val flyoutShape = RoundedCornerShape(12.dp)
    val itemShape = RoundedCornerShape(6.dp)

    Box(
        modifier = modifier
            .then(if (!isDark) Modifier.vShadow(16.dp, flyoutShape) else Modifier)
            .clip(flyoutShape)
            .background(colors.SurfaceLevel1)
            .border(
                1.dp,
                if (isDark) colors.OutlineHover else colors.OutlineHairline,
                flyoutShape,
            )
            .padding(8.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // 1. 最近搜索
            if (historyNote != null) {
                Text(
                    text = historyNote,
                    fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                    color = colors.TextTertiary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                )
            } else if (history.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(24.dp).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "最近搜索",
                        fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.TextTertiary,
                        modifier = Modifier.weight(1f),
                    )
                    val clearInteraction = remember { MutableInteractionSource() }
                    val clearHovered by clearInteraction.collectIsHoveredAsState()
                    Text(
                        text = "清空",
                        fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                        color = if (clearHovered) colors.ToneDanger else colors.TextTertiary,
                        modifier = Modifier
                            .hoverable(clearInteraction)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (clearHovered) colors.SurfaceLevel2 else Color.Transparent)
                            .clickable(
                                interactionSource = clearInteraction,
                                indication = null,
                                onClick = onClearHistory,
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                history.forEach { entry ->
                    val rowInteraction = remember { MutableInteractionSource() }
                    val rowHovered by rowInteraction.collectIsHoveredAsState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                            .hoverable(rowInteraction)
                            .clip(itemShape)
                            .background(if (rowHovered) colors.SurfaceLevel2 else Color.Transparent)
                            .clickable(
                                interactionSource = rowInteraction,
                                indication = null,
                            ) { onPickHistory(entry) }
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.History,
                            contentDescription = null,
                            tint = colors.TextTertiary,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = entry.query,
                            fontSize = VeneraType.bodySecondary.size,
                            lineHeight = VeneraType.bodySecondary.lineHeight,
                            color = colors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // 分割线（两部分都存在时摆出）
            if (history.isNotEmpty() && (suggestCandidates.isNotEmpty() || suggestLine != null)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                        .height(1.dp)
                        .background(colors.Divider),
                )
            }

            // 2. 标签实时联想
            if (suggestCandidates.isNotEmpty() || suggestLine != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(24.dp).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "标签联想",
                        fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.TextTertiary,
                    )
                    Spacer(Modifier.width(8.dp))
                    suggestLine?.let { line ->
                        Text(
                            text = line,
                            fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                            color = colors.TextTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (suggestCandidates.isNotEmpty()) {
                    suggestCandidates.forEach { suggestion ->
                        val siteLabel = if (suggestion.site == GallerySite.PIXIV) "Pixiv" else suggestion.site.displayName
                        val cn = com.venera.desktop.gallery.data.DesktopPixivAutocomplete
                            .translationFor(suggestion.name)
                        val catColor = when (suggestion.categoryLabel) {
                            "画师" -> colors.ToneCaution
                            "角色" -> colors.ToneSafe
                            "作品", "版权" -> colors.AccentPrimary
                            else -> colors.TextTertiary
                        }
                        val rowInteraction = remember { MutableInteractionSource() }
                        val rowHovered by rowInteraction.collectIsHoveredAsState()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(34.dp)
                                .hoverable(rowInteraction)
                                .clip(itemShape)
                                .background(if (rowHovered) colors.SurfaceLevel2 else Color.Transparent)
                                .clickable(
                                    interactionSource = rowInteraction,
                                    indication = null,
                                ) { onPickSuggestion(suggestion.name) }
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(catColor),
                            )
                            Text(
                                text = if (cn.isNotBlank() && cn != suggestion.name) "$cn (${suggestion.name})" else suggestion.name,
                                fontSize = VeneraType.bodySecondary.size,
                                lineHeight = VeneraType.bodySecondary.lineHeight,
                                color = colors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (suggestion.count > 0) {
                                Text(
                                    text = suggestion.count.toString(),
                                    fontSize = VeneraType.micro.size,
                                    lineHeight = VeneraType.micro.lineHeight,
                                    color = colors.TextTertiary,
                                    maxLines = 1,
                                )
                            }
                            Text(
                                text = "${suggestion.categoryLabel} · $siteLabel",
                                fontSize = VeneraType.micro.size,
                                lineHeight = VeneraType.micro.lineHeight,
                                color = colors.TextTertiary,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// 顶部指挥栏与精简高级筛选面板
// ═══════════════════════════════════════════════════════════════════

/** 模式分段器：统一收编至标准 `DesktopSegmented`（图库 Booru ↔ Pixiv 专属 + 站徽色点）。 */
@Composable
internal fun GallerySearchModeSwitch(searchMode: Int, onPickMode: (Int) -> Unit) {
    DesktopSegmented(
        options = listOf("图库 (Booru)", "Pixiv 专属"),
        selectedIndex = searchMode.coerceIn(0, 1),
        onSelect = { index -> if (index != searchMode) onPickMode(index) },
        leadingDots = listOf(GalleryStationDot, PixivStationDot),
        large = true,
    )
}

/** 集成式主搜索框：与画廊顶栏同一颗（34dp / r8 / 描边随悬停聚焦分档）。 */
@Composable
internal fun GallerySearchInputBox(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    onClick: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    com.venera.desktop.ui.components.DesktopSearchField(
        value = value,
        onValueChange = onValueChange,
        onSearch = { if (value.isNotBlank()) onSearch() },
        modifier = modifier,
        placeholder = placeholder,
        onClear = { onValueChange("") },
        onClick = onClick,
        onFocusChanged = onFocusChanged,
    )
}

/** 主「搜索」按钮：统一走标准 `DesktopButton(Medium / Primary)`。 */
@Composable
internal fun GallerySearchSubmitButton(
    searching: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    DesktopButton(
        text = if (searching) "搜索中…" else "搜索",
        onClick = onClick,
        size = DesktopButtonSize.Medium,
        variant = DesktopButtonVariant.Primary,
        leadingIcon = if (!searching) Icons.Outlined.Search else null,
        enabled = enabled,
        loading = searching,
    )
}

/** 「高级筛选」开合钮：统一走标准 `DesktopButton(Medium / Secondary)`。 */
@Composable
internal fun GallerySearchInspectorToggle(
    open: Boolean,
    activeFilters: Int,
    onClick: () -> Unit,
) {
    val palette = LocalChromePalette.current
    val highlighted = open || activeFilters > 0
    DesktopButton(
        onClick = onClick,
        size = DesktopButtonSize.Medium,
        variant = DesktopButtonVariant.Secondary,
        selected = highlighted,
        horizontalPadding = 12.dp,
    ) {
        Icon(
            imageVector = Icons.Outlined.Tune,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
        )
        Text(
            text = "高级筛选",
            fontSize = VeneraType.aux.size,
            lineHeight = VeneraType.aux.lineHeight,
            fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
        if (activeFilters > 0) {
            Box(
                modifier = Modifier
                    .height(18.dp)
                    .widthIn(min = 18.dp)
                    .clip(CircleShape)
                    .background(palette.accent)
                    .padding(horizontal = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = activeFilters.toString(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
            }
        }
    }
}

/** 桌面统一紧凑方圆角筛选钮：收编至 `DesktopButton(Compact)`（保持单 Text 语义纯洁）。 */
@Composable
internal fun GalleryFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    DesktopButton(
        text = label,
        onClick = onClick,
        size = DesktopButtonSize.Compact,
        variant = DesktopButtonVariant.Secondary,
        selected = selected,
        horizontalPadding = 10.dp,
    )
}

/** 快捷条聚合 Pop-up 下拉芯片：统一收编至标准 `DesktopPopUpButton`。 */
@Composable
internal fun <T> QuickStripDropdownChip(
    prefix: String,
    currentLabel: String,
    highlighted: Boolean,
    items: List<Pair<String, T>>,
    selectedValue: T,
    onSelect: (T) -> Unit,
) {
    DesktopPopUpButton(
        label = prefix,
        value = currentLabel,
        options = items.map { it.first },
        selectedIndex = items.indexOfFirst { it.second == selectedValue }.coerceAtLeast(0),
        onSelect = { index ->
            items.getOrNull(index)?.let { onSelect(it.second) }
        },
    )
}

@Composable
internal fun QuickStripLabel(text: String) {
    val colors = GalleryTokens.Color
    Text(
        text = text,
        fontSize = 11.5.sp,
        color = colors.TextTertiary,
        maxLines = 1,
    )
}

@Composable
internal fun QuickStripDivider() {
    val colors = GalleryTokens.Color
    Box(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .width(1.dp)
            .height(14.dp)
            .background(colors.Divider),
    )
}

@Composable
internal fun GalleryCompactSwitch(
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    DesktopSwitch(checked = checked, onCheckedChange = onToggle)
}

/** 收敛后的快捷筛选条：自动流式换行（宽屏单行不溢出，彻底移除右侧渐隐块）。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GallerySearchQuickStrip(
    searchMode: Int,
    sources: List<String>,
    selectedSource: Int,
    onPickSource: (Int) -> Unit,
    rankingChoices: List<DesktopRankingChoice>,
    selectedRanking: GalleryRanking,
    onPickRanking: (GalleryRanking) -> Unit,
    searchR18: Boolean,
    onPickSearchR18: (Boolean) -> Unit,
    pixivFilter: PixivSearchFilter,
    onPixivFilter: (PixivSearchFilter) -> Unit,
    pixivKind: Int,
    onPickPixivKind: (Int) -> Unit,
) {
    val colors = GalleryTokens.Color
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        if (searchMode == 1) {
            QuickStripLabel("维度")
            GalleryFilterChip(
                label = "作品",
                selected = pixivKind == DESKTOP_PIXIV_SEARCH_WORKS,
                onClick = { onPickPixivKind(DESKTOP_PIXIV_SEARCH_WORKS) },
            )
            GalleryFilterChip(
                label = "画师",
                selected = pixivKind == DESKTOP_PIXIV_SEARCH_ARTIST,
                onClick = { onPickPixivKind(DESKTOP_PIXIV_SEARCH_ARTIST) },
            )
            GalleryFilterChip(
                label = "珍藏册",
                selected = pixivKind == DESKTOP_PIXIV_SEARCH_COLLECTION,
                onClick = { onPickPixivKind(DESKTOP_PIXIV_SEARCH_COLLECTION) },
            )
            QuickStripDivider()
            QuickStripDropdownChip(
                prefix = "排序",
                currentLabel = pixivFilter.order.label + if (pixivFilter.order.needsPremium) "（需登录）" else "",
                highlighted = pixivFilter.order != PixivSearchFilter.DEFAULT.order,
                items = PixivSearchOrder.entries.reversed().map { candidate ->
                    (candidate.label + if (candidate.needsPremium) "（需登录）" else "") to candidate
                },
                selectedValue = pixivFilter.order,
                onSelect = { onPixivFilter(pixivFilter.copy(order = it)) },
            )
            QuickStripDropdownChip(
                prefix = "收藏",
                currentLabel = pixivFilter.bookmarks.label,
                highlighted = pixivFilter.bookmarks != PixivBookmark.ALL,
                items = PixivBookmark.entries.map { it.label to it },
                selectedValue = pixivFilter.bookmarks,
                onSelect = { onPixivFilter(pixivFilter.copy(bookmarks = it)) },
            )
            if (pixivFilter.excludeAi) {
                DesktopButton(
                    onClick = { onPixivFilter(pixivFilter.copy(excludeAi = false)) },
                    size = DesktopButtonSize.Compact,
                    variant = DesktopButtonVariant.Secondary,
                    selected = true,
                    horizontalPadding = 10.dp,
                ) {
                    Text(
                        text = "排除 AI",
                        fontSize = VeneraType.aux.size,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "关闭排除 AI",
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        } else {
            QuickStripLabel("来源")
            sources.forEachIndexed { index, label ->
                GalleryFilterChip(
                    label = label,
                    selected = index == selectedSource,
                    onClick = { onPickSource(index) },
                )
            }
            if (rankingChoices.size > 1) {
                QuickStripDivider()
                val curChoice = rankingChoices.firstOrNull { it.ranking == selectedRanking } ?: rankingChoices.first()
                QuickStripDropdownChip(
                    prefix = "排行",
                    currentLabel = curChoice.label,
                    highlighted = selectedRanking != GalleryRanking.NEWEST,
                    items = rankingChoices.map { it.label to it.ranking },
                    selectedValue = selectedRanking,
                    onSelect = onPickRanking,
                )
            }
            QuickStripDivider()
            val r18Shape = RoundedCornerShape(6.dp)
            Row(
                modifier = Modifier
                    .height(28.dp)
                    .clip(r18Shape)
                    .background(colors.SurfaceLevel1)
                    .border(1.dp, colors.OutlineHairline, r18Shape)
                    .clickable { onPickSearchR18(!searchR18) }
                    .padding(start = 10.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "全年龄过滤",
                    fontSize = 12.sp,
                    color = colors.TextSecondary,
                    maxLines = 1,
                )
                GalleryCompactSwitch(!searchR18) { onPickSearchR18(!it) }
            }
        }
    }
}

/** 高级筛选抽屉分组卡片容器：12dp 圆角、SurfaceLevel1 底、浅色带轻投影。 */
@Composable
internal fun GalleryInspectorCard(
    title: String,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = GalleryTokens.Color
    val isDark = LocalVeneraDarkTheme.current
    val shape = RoundedCornerShape(12.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (!isDark) Modifier.vShadow(4.dp, shape) else Modifier)
            .clip(shape)
            .background(colors.SurfaceLevel1)
            .border(1.dp, colors.OutlineHairline, shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(12.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.AccentPrimary),
            )
            Text(
                text = title,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            if (actionLabel != null && onAction != null) {
                Text(
                    text = actionLabel,
                    fontSize = 11.5.sp,
                    color = colors.AccentPrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(onClick = onAction)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        content()
    }
}

@Composable
internal fun GalleryCompactDateField(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    com.venera.desktop.ui.components.DesktopInputField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = placeholder,
    )
}

/** Pixiv 域高级筛选面板：卡片化分组，去掉冗余英文副标与原生白框输入框。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DesktopPixivFilterInspector(
    filter: PixivSearchFilter,
    onFilter: (PixivSearchFilter) -> Unit,
) {
    GalleryInspectorCard(
        title = "标签匹配",
        actionLabel = if (filter != PixivSearchFilter.DEFAULT) "重置筛选" else null,
        onAction = { onFilter(PixivSearchFilter.DEFAULT) },
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PixivTagMode.entries.forEach { candidate ->
                GalleryFilterChip(
                    label = "${candidate.label}一致",
                    selected = candidate == filter.tagMode,
                    onClick = { onFilter(filter.copy(tagMode = candidate)) },
                )
            }
        }
    }

    GalleryInspectorCard(title = "过滤属性", actionLabel = null, onAction = null) {
        PixivFilterChipRow("生成来源") {
            GalleryFilterChip(
                label = "全部",
                selected = !filter.excludeAi,
                onClick = { onFilter(filter.copy(excludeAi = false)) },
            )
            GalleryFilterChip(
                label = "排除 AI",
                selected = filter.excludeAi,
                onClick = { onFilter(filter.copy(excludeAi = true)) },
            )
        }
        PixivFilterChipRow("作品形式") {
            PixivWorkType.entries.forEach { candidate ->
                GalleryFilterChip(
                    label = candidate.label,
                    selected = candidate == filter.workType,
                    onClick = { onFilter(filter.copy(workType = candidate)) },
                )
            }
        }
        PixivFilterChipRow("画幅比例") {
            PixivRatio.entries.forEach { candidate ->
                GalleryFilterChip(
                    label = candidate.label,
                    selected = candidate == filter.ratio,
                    onClick = { onFilter(filter.copy(ratio = candidate)) },
                )
            }
        }
    }

    GalleryInspectorCard(title = "发布时间区间", actionLabel = null, onAction = null) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PixivSearchFilter.DATE_PRESETS.forEach { days ->
                val label = when (days) {
                    null -> "不限"
                    7 -> "1周内"
                    30 -> "1月内"
                    else -> "1年内"
                }
                GalleryFilterChip(
                    label = label,
                    selected = currentPresetDays(filter) == days,
                    onClick = {
                        onFilter(
                            if (days == null) {
                                filter.copy(from = null, to = null)
                            } else {
                                val today = LocalDate.now()
                                filter.copy(from = today.minusDays((days - 1).toLong()), to = today)
                            },
                        )
                    },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GalleryCompactDateField(
                value = filter.from?.toString().orEmpty(),
                placeholder = "起始 yyyy-MM-dd",
                onValueChange = { text ->
                    val day = runCatching { LocalDate.parse(text.trim()) }.getOrNull()
                    onFilter(filter.copy(from = day))
                },
                modifier = Modifier.weight(1f),
            )
            GalleryCompactDateField(
                value = filter.to?.toString().orEmpty(),
                placeholder = "截至 yyyy-MM-dd",
                onValueChange = { text ->
                    val day = runCatching { LocalDate.parse(text.trim()) }.getOrNull()
                    onFilter(filter.copy(to = day))
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

