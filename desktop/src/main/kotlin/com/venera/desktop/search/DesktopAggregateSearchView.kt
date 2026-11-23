package com.venera.desktop.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.components.venera.VeneraCoverMask
import com.venera.compose.components.venera.VeneraShimmer
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.gallery.ui.GalleryTokens
import com.venera.desktop.gallery.ui.LocalChromePalette
import com.venera.desktop.settings.DesktopI18n
import com.venera.desktop.ui.components.DesktopComicCardActions
import com.venera.desktop.ui.components.DesktopComicCardMenuArea
import com.venera.desktop.ui.components.DesktopFloatingCard
import com.venera.desktop.ui.components.desktopCoverFloat
import com.venera.desktop.ui.theme.VeneraType
import com.venera.desktop.ui.vShadow
import com.venera.engineprobe.EngineSession
import kotlinx.coroutines.launch

/**
 * 聚合搜索顶部源状态锚点条：替代单源「筛选条件」面板。
 *
 * @param orderedLanes 已按「有结果→检索中→失败→空」分层排序的泳道快照。
 * @param onlyHits 是否仅展示有结果与检索中的泳道。
 * @param onToggleOnlyHits 切换「仅看有结果」开关。
 * @param onJumpToSource 点击源状态胶囊时平滑滚动到对应泳道。
 */
@Composable
internal fun DesktopAggregateStatusBar(
    orderedLanes: List<DesktopAggregateLaneState>,
    onlyHits: Boolean,
    onToggleOnlyHits: () -> Unit,
    onJumpToSource: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    val palette = LocalChromePalette.current
    val shape = GalleryTokens.Shape.Large

    val totalSources = orderedLanes.size
    val doneSources = orderedLanes.count { it.status != AggregateLaneStatus.Loading }
    val totalComics = orderedLanes.sumOf { it.cards.size }
    val allDone = doneSources == totalSources && totalSources > 0

    Row(
        modifier = modifier
            .fillMaxWidth()
            .vShadow(1.dp, shape)
            .clip(shape)
            .background(palette.elevated)
            .border(1.dp, GalleryTokens.Color.OutlineHairline, shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (allDone) GalleryTokens.Color.ToneSafe else tokens.color.primary),
            )
            Text(
                text = if (allDone) {
                    DesktopI18n.t("已完成 $doneSources/$totalSources 源 · 共 $totalComics 本", "Done $doneSources/$totalSources sources · $totalComics comics")
                } else {
                    DesktopI18n.t("检索中 $doneSources/$totalSources 源 · 共 $totalComics 本", "Searching $doneSources/$totalSources sources · $totalComics comics")
                },
                fontSize = VeneraType.aux.size,
                lineHeight = VeneraType.aux.lineHeight,
                fontWeight = VeneraType.cardTitle.weight,
                color = tokens.color.textPrimary,
                maxLines = 1,
            )
        }

        Box(
            modifier = Modifier
                .width(1.dp)
                .height(16.dp)
                .background(GalleryTokens.Color.OutlineHairline),
        )

        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            orderedLanes.forEach { lane ->
                AggregateAnchorPill(
                    lane = lane,
                    onClick = { onJumpToSource(lane.sourceKey) },
                )
            }
        }

        Box(
            modifier = Modifier
                .width(1.dp)
                .height(16.dp)
                .background(GalleryTokens.Color.OutlineHairline),
        )

        OnlyHitsSwitch(
            checked = onlyHits,
            onToggle = onToggleOnlyHits,
        )
    }
}

/** 聚合搜索主内容区：上方状态锚点条 + 左栏按源分组横滑泳道 + 右栏速览面板。 */
@Composable
internal fun DesktopAggregateSearchContent(
    orderedLanes: List<DesktopAggregateLaneState>,
    hasSearched: Boolean,
    onlyHits: Boolean,
    onToggleOnlyHits: () -> Unit,
    selectedCardId: String?,
    selectedCardSourceKey: String?,
    maskedOf: (sourceKey: String, card: EngineSession.ComicCard) -> Boolean,
    onSelectCard: (sourceKey: String, sourceLabel: String, card: EngineSession.ComicCard) -> Unit,
    onOpenDetail: (sourceKey: String, card: EngineSession.ComicCard) -> Unit,
    onSearchTagInSource: (sourceKey: String, tag: String) -> Unit,
    onDrillDownSource: (sourceKey: String) -> Unit,
    onRetrySource: (sourceKey: String) -> Unit,
    peekPane: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
    skippedSelfHosted: List<String> = emptyList(),
) {
    val tokens = VeneraTokens
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val visibleLanes = remember(orderedLanes, onlyHits) {
        if (onlyHits) {
            orderedLanes.filter {
                it.status == AggregateLaneStatus.Ready || it.status == AggregateLaneStatus.Loading
            }
        } else {
            orderedLanes
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (orderedLanes.isNotEmpty()) {
            DesktopAggregateStatusBar(
                orderedLanes = orderedLanes,
                onlyHits = onlyHits,
                onToggleOnlyHits = onToggleOnlyHits,
                onJumpToSource = { key ->
                    val targetIdx = visibleLanes.indexOfFirst { it.sourceKey == key }
                    if (targetIdx >= 0) {
                        scope.launch { listState.animateScrollToItem(targetIdx) }
                    } else if (onlyHits) {
                        onToggleOnlyHits()
                        val fullIdx = orderedLanes.indexOfFirst { it.sourceKey == key }
                        if (fullIdx >= 0) scope.launch { listState.animateScrollToItem(fullIdx) }
                    }
                },
            )
        }

        if (skippedSelfHosted.isNotEmpty()) {
            Text(
                text = DesktopI18n.t(
                    "已自动跳过未配置服务器地址的私服源：${skippedSelfHosted.joinToString("、")}（可在源管理中配置地址，或手动勾选启用）",
                    "Skipped unconfigured self-hosted sources: ${skippedSelfHosted.joinToString(", ")} (configure server URL in Sources or enable manually)",
                ),
                fontSize = VeneraType.micro.size,
                lineHeight = VeneraType.micro.lineHeight,
                color = tokens.color.textTertiary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(LocalChromePalette.current.pill)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }

        if (!hasSearched && orderedLanes.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = DesktopI18n.t("输入书名或作者，回车开始多源聚合搜索。", "Enter title or author and press Enter for multi-source search."),
                    fontSize = VeneraType.body.size,
                    lineHeight = VeneraType.body.lineHeight,
                    color = tokens.color.textTertiary,
                )
            }
            return
        }

        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                if (visibleLanes.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (onlyHits) {
                                DesktopI18n.t("暂无命中漫画源，可关闭「仅看有结果」查看完整状态。", "No matching sources yet. Turn off 'Hits only' to view all.")
                            } else {
                                DesktopI18n.t("暂无可用搜索源。", "No available search sources.")
                            },
                            fontSize = VeneraType.body.size,
                            lineHeight = VeneraType.body.lineHeight,
                            color = tokens.color.textTertiary,
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = tokens.spacing.space4),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(visibleLanes, key = { "agg-lane-${it.sourceKey}" }) { lane ->
                            DesktopAggregateLaneCard(
                                lane = lane,
                                selectedCardId = selectedCardId,
                                selectedCardSourceKey = selectedCardSourceKey,
                                maskedOf = { card -> maskedOf(lane.sourceKey, card) },
                                onSelectCard = { card -> onSelectCard(lane.sourceKey, lane.sourceLabel, card) },
                                onOpenDetail = { card -> onOpenDetail(lane.sourceKey, card) },
                                onSearchTagInSource = { tag -> onSearchTagInSource(lane.sourceKey, tag) },
                                onDrillDown = { onDrillDownSource(lane.sourceKey) },
                                onRetry = { onRetrySource(lane.sourceKey) },
                            )
                        }
                    }
                }
            }
            peekPane?.invoke()
        }
    }
}

@Composable
private fun AggregateAnchorPill(
    lane: DesktopAggregateLaneState,
    onClick: () -> Unit,
) {
    val tokens = VeneraTokens
    val palette = LocalChromePalette.current
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    val shape = RoundedCornerShape(8.dp)

    val dotColor = when (lane.status) {
        AggregateLaneStatus.Ready -> GalleryTokens.Color.ToneSafe
        AggregateLaneStatus.Loading -> tokens.color.primary
        AggregateLaneStatus.Failed -> tokens.color.error
        AggregateLaneStatus.Empty -> tokens.color.textTertiary
    }
    val countText = when (lane.status) {
        AggregateLaneStatus.Ready -> "${lane.cards.size}"
        AggregateLaneStatus.Loading -> "…"
        AggregateLaneStatus.Failed -> DesktopI18n.t("失败", "Failed")
        AggregateLaneStatus.Empty -> "0"
    }
    val borderColor = when {
        lane.status == AggregateLaneStatus.Failed -> tokens.color.error.copy(alpha = 0.34f)
        hovered -> GalleryTokens.Color.OutlineHover
        else -> GalleryTokens.Color.OutlineHairline
    }

    Row(
        modifier = Modifier
            .height(28.dp)
            .clip(shape)
            .background(if (hovered) palette.hover else palette.pill)
            .border(1.dp, borderColor, shape)
            .hoverable(hoverSource)
            .clickable(onClick = onClick)
            .padding(start = 8.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Text(
            text = lane.sourceLabel,
            fontSize = VeneraType.aux.size,
            lineHeight = VeneraType.aux.lineHeight,
            color = if (lane.status == AggregateLaneStatus.Failed) tokens.color.error else tokens.color.textPrimary,
            maxLines = 1,
        )
        Text(
            text = countText,
            fontSize = VeneraType.micro.size,
            lineHeight = VeneraType.micro.lineHeight,
            fontWeight = if (lane.status == AggregateLaneStatus.Ready) VeneraType.cardTitle.weight else FontWeight.Normal,
            color = if (lane.status == AggregateLaneStatus.Failed) tokens.color.error else tokens.color.textTertiary,
            maxLines = 1,
        )
    }
}

@Composable
private fun OnlyHitsSwitch(
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val tokens = VeneraTokens
    val palette = LocalChromePalette.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(
            text = DesktopI18n.t("仅看有结果", "Hits only"),
            fontSize = VeneraType.aux.size,
            lineHeight = VeneraType.aux.lineHeight,
            color = if (checked) tokens.color.textPrimary else tokens.color.textSecondary,
            maxLines = 1,
        )
        com.venera.desktop.ui.DesktopSwitch(checked = checked, onCheckedChange = { onToggle() })
    }
}

/** 单个漫画源在聚合结果流中的泳道卡片。 */
@Composable
private fun DesktopAggregateLaneCard(
    lane: DesktopAggregateLaneState,
    selectedCardId: String?,
    selectedCardSourceKey: String?,
    maskedOf: (EngineSession.ComicCard) -> Boolean,
    onSelectCard: (EngineSession.ComicCard) -> Unit,
    onOpenDetail: (EngineSession.ComicCard) -> Unit,
    onSearchTagInSource: (String) -> Unit,
    onDrillDown: () -> Unit,
    onRetry: () -> Unit,
) {
    val tokens = VeneraTokens
    val palette = LocalChromePalette.current
    val shape = GalleryTokens.Shape.Large
    val compact = lane.status == AggregateLaneStatus.Empty || lane.status == AggregateLaneStatus.Failed
    val railState = rememberLazyListState()
    val railScope = rememberCoroutineScope()
    val scrollStepPx = with(LocalDensity.current) { 420.dp.toPx() }
    var expandError by remember(lane.sourceKey, lane.error) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .vShadow(1.dp, shape)
            .clip(shape)
            .background(palette.elevated)
            .border(1.dp, GalleryTokens.Color.OutlineHairline, shape)
            .padding(horizontal = 16.dp, vertical = if (compact) 11.dp else 14.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 0.dp else 12.dp),
    ) {
        // 泳道头部
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            DesktopSearchSourceIcon(
                sourceKey = lane.sourceKey,
                label = lane.sourceLabel,
                size = 20.dp,
            )
            Text(
                text = lane.sourceLabel,
                fontSize = VeneraType.cardTitle.size,
                lineHeight = VeneraType.cardTitle.lineHeight,
                fontWeight = VeneraType.cardTitle.weight,
                color = tokens.color.textPrimary,
                maxLines = 1,
            )
            val metaText = when (lane.status) {
                AggregateLaneStatus.Ready -> DesktopI18n.t("${lane.cards.size} 本", "${lane.cards.size} comics")
                AggregateLaneStatus.Loading -> DesktopI18n.t("正在检索…", "Searching…")
                AggregateLaneStatus.Failed -> lane.error?.ifBlank { DesktopI18n.t("检索失败", "Search failed") } ?: DesktopI18n.t("检索失败", "Search failed")
                AggregateLaneStatus.Empty -> DesktopI18n.t("该源未找到相关漫画", "No comics found in this source")
            }
            val metaColor = when (lane.status) {
                AggregateLaneStatus.Loading -> tokens.color.primary
                AggregateLaneStatus.Failed -> tokens.color.error
                else -> tokens.color.textTertiary
            }
            Text(
                text = metaText,
                fontSize = VeneraType.aux.size,
                lineHeight = VeneraType.aux.lineHeight,
                color = metaColor,
                maxLines = if (lane.status == AggregateLaneStatus.Failed && expandError) 6 else 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (lane.status == AggregateLaneStatus.Failed) {
                            Modifier.clickable { expandError = !expandError }
                        } else {
                            Modifier
                        },
                    ),
            )

            when (lane.status) {
                AggregateLaneStatus.Ready -> {
                    RailStepButton(
                        isNext = false,
                        onClick = { railScope.launch { railState.animateScrollBy(-scrollStepPx) } },
                    )
                    RailStepButton(
                        isNext = true,
                        onClick = { railScope.launch { railState.animateScrollBy(scrollStepPx) } },
                    )
                    Box(
                        modifier = Modifier
                            .height(26.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(tokens.color.primary.copy(alpha = 0.14f))
                            .clickable(onClick = onDrillDown)
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = DesktopI18n.t("查看全部 →", "View All →"),
                            fontSize = VeneraType.aux.size,
                            fontWeight = VeneraType.cardTitle.weight,
                            color = tokens.color.primary,
                            maxLines = 1,
                        )
                    }
                }
                AggregateLaneStatus.Failed -> {
                    Box(
                        modifier = Modifier
                            .height(26.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(tokens.color.error.copy(alpha = 0.14f))
                            .border(1.dp, tokens.color.error.copy(alpha = 0.34f), RoundedCornerShape(7.dp))
                            .clickable(onClick = onRetry)
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = DesktopI18n.t("单源重试", "Retry"),
                            fontSize = VeneraType.aux.size,
                            fontWeight = VeneraType.cardTitle.weight,
                            color = tokens.color.error,
                            maxLines = 1,
                        )
                    }
                }
                else -> Unit
            }
        }

        when (lane.status) {
            AggregateLaneStatus.Ready -> {
                LazyRow(
                    state = railState,
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(lane.cards, key = { "${lane.sourceKey}:${it.id}" }) { card ->
                        val isSelected = selectedCardId == card.id &&
                            (selectedCardSourceKey == null || selectedCardSourceKey == lane.sourceKey)
                        val firstTag = card.tags.firstOrNull()
                        DesktopComicCardMenuArea(
                            actions = DesktopComicCardActions(
                                sourceKey = lane.sourceKey,
                                onOpenDetail = { onOpenDetail(card) },
                                onSearchInSource = onSearchTagInSource,
                                onCopyTitle = {
                                    val sel = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                                    sel.setContents(java.awt.datatransfer.StringSelection(card.title), null)
                                },
                            ),
                            tag = firstTag,
                        ) {
                            AggregateComicRailCard(
                                card = card,
                                masked = maskedOf(card),
                                selected = isSelected,
                                onClick = { onSelectCard(card) },
                            )
                        }
                    }
                }
            }
            AggregateLaneStatus.Loading -> {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    repeat(6) {
                        Column(
                            modifier = Modifier
                                .width(138.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(palette.card)
                                .border(1.dp, GalleryTokens.Color.OutlineHairline, RoundedCornerShape(10.dp))
                                .padding(7.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            VeneraShimmer(
                                modifier = Modifier.fillMaxWidth().aspectRatio(0.72f),
                                shape = RoundedCornerShape(6.dp),
                                emphasized = true,
                            )
                            VeneraShimmer(
                                modifier = Modifier.fillMaxWidth(0.85f).height(12.dp),
                                shape = RoundedCornerShape(4.dp),
                                emphasized = true,
                            )
                            VeneraShimmer(
                                modifier = Modifier.fillMaxWidth(0.55f).height(10.dp),
                                shape = RoundedCornerShape(4.dp),
                                emphasized = true,
                            )
                        }
                    }
                }
            }
            else -> Unit
        }
    }
}

@Composable
private fun RailStepButton(
    isNext: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalChromePalette.current
    val tokens = VeneraTokens
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    val shape = RoundedCornerShape(7.dp)
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(shape)
            .background(if (hovered) palette.hover else palette.pill)
            .border(1.dp, GalleryTokens.Color.OutlineHairline, shape)
            .hoverable(hoverSource)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isNext) Icons.AutoMirrored.Outlined.KeyboardArrowRight else Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
            contentDescription = if (isNext) DesktopI18n.t("向右翻页", "Next page") else DesktopI18n.t("向左翻页", "Previous page"),
            tint = if (hovered) tokens.color.textPrimary else tokens.color.textSecondary,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun AggregateComicRailCard(
    card: EngineSession.ComicCard,
    masked: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tokens = VeneraTokens
    val palette = LocalChromePalette.current
    DesktopFloatingCard(
        cornerRadius = 10.dp,
        surface = palette.card,
        selected = selected,
        selectedColor = tokens.color.primary,
        modifier = Modifier
            .width(138.dp)
            .clickable(onClick = onClick),
    ) { floatState ->
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.72f),
            ) {
                VeneraCover(
                    url = card.cover,
                    contentDescription = card.title,
                    mask = if (masked) VeneraCoverMask.Masked else VeneraCoverMask.Visible,
                    modifier = Modifier
                        .fillMaxSize()
                        .desktopCoverFloat(floatState),
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 9.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = card.title,
                    fontSize = VeneraType.aux.size,
                    lineHeight = VeneraType.aux.lineHeight,
                    fontWeight = VeneraType.control.weight,
                    color = tokens.color.textPrimary,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!card.author.isNullOrBlank()) {
                    Text(
                        text = card.author,
                        fontSize = VeneraType.micro.size,
                        lineHeight = VeneraType.micro.lineHeight,
                        color = tokens.color.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
