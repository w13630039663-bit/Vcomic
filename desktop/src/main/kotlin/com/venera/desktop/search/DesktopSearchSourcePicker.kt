package com.venera.desktop.search

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.gallery.data.DesktopSourceIcons
import com.venera.desktop.gallery.ui.GalleryTokens
import com.venera.desktop.gallery.ui.LocalChromePalette
import com.venera.desktop.settings.DesktopI18n
import com.venera.desktop.ui.theme.VeneraType
import com.venera.desktop.ui.vShadow

/**
 * 漫画搜索顶栏右侧的源选择器：触发按钮 + 双档（「单源 / 多源聚合」）弹出面板。
 *
 * @param target 当前已生效的搜索目标（单源或多源聚合）。
 * @param sourceOptions 源管理中已启用的漫画源清单；空表时整颗不摆。
 * @param onSelectSingle 单源档单击立刻切换并关闭面板。
 * @param onSelectMulti 多源聚合档点击确认后提交选中的源键集合。
 * @param modifier 外层布局修饰符。
 */
@Composable
internal fun DesktopSearchSourcePicker(
    target: DesktopSearchTarget,
    sourceOptions: List<DesktopSearchSourceOption>,
    onSelectSingle: (String) -> Unit,
    onSelectMulti: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (sourceOptions.isEmpty()) return

    val tokens = VeneraTokens
    val palette = LocalChromePalette.current
    val density = LocalDensity.current
    var open by remember { mutableStateOf(false) }
    var triggerHeightPx by remember { mutableIntStateOf(0) }

    val triggerHoverSource = remember { MutableInteractionSource() }
    val triggerHovered by triggerHoverSource.collectIsHoveredAsState()

    val triggerShape = RoundedCornerShape(10.dp)
    val triggerBg = when {
        open -> tokens.color.primary.copy(alpha = 0.14f)
        triggerHovered -> palette.hover
        else -> palette.pill
    }
    val triggerBorder = when {
        open -> tokens.color.primary.copy(alpha = 0.42f)
        triggerHovered -> GalleryTokens.Color.OutlineHover
        else -> GalleryTokens.Color.OutlineHairline
    }

    val enabledKeys = remember(sourceOptions) { sourceOptions.map { it.key } }
    val optionByKey = remember(sourceOptions) { sourceOptions.associateBy { it.key } }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .height(36.dp)
                .widthIn(min = 104.dp, max = 230.dp)
                .onGloballyPositioned { triggerHeightPx = it.size.height }
                .clip(triggerShape)
                .background(triggerBg)
                .border(1.dp, triggerBorder, triggerShape)
                .hoverable(triggerHoverSource)
                .clickable { open = !open }
                .padding(start = 9.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (target) {
                is DesktopSearchTarget.Single -> {
                    val opt = optionByKey[target.sourceKey] ?: sourceOptions.first()
                    DesktopSearchSourceIcon(
                        sourceKey = opt.key,
                        label = opt.label,
                        size = 20.dp,
                    )
                    Text(
                        text = opt.label,
                        fontSize = VeneraType.control.size,
                        lineHeight = VeneraType.control.lineHeight,
                        fontWeight = VeneraType.cardTitle.weight,
                        color = tokens.color.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                is DesktopSearchTarget.Aggregate -> {
                    val activeKeys = if (target.allEnabled || target.selectedKeys.isEmpty()) {
                        enabledKeys
                    } else {
                        enabledKeys.filter { it in target.selectedKeys }.ifEmpty { enabledKeys }
                    }
                    val topIcons = activeKeys.take(3).mapNotNull { optionByKey[it] }
                    val isAll = activeKeys.size == enabledKeys.size
                    val aggLabel = describeAggregateLabel(activeKeys, sourceOptions)
                    StackedSourceIcons(options = topIcons)
                    Text(
                        text = aggLabel,
                        fontSize = VeneraType.control.size,
                        lineHeight = VeneraType.control.lineHeight,
                        fontWeight = VeneraType.cardTitle.weight,
                        color = tokens.color.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (isAll || aggLabel != DesktopI18n.t("已选 ${activeKeys.size} 源", "${activeKeys.size} Selected")) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .background(tokens.color.primary.copy(alpha = 0.14f))
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                        ) {
                            Text(
                                text = "${activeKeys.size}",
                                fontSize = VeneraType.micro.size,
                                lineHeight = VeneraType.micro.lineHeight,
                                fontWeight = VeneraType.cardTitle.weight,
                                color = tokens.color.primary,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            Icon(
                imageVector = if (open) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                tint = if (open) tokens.color.primary else tokens.color.textTertiary,
                modifier = Modifier.size(16.dp),
            )
        }

        if (open) {
            val offsetY = triggerHeightPx + with(density) { 6.dp.roundToPx() }
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, offsetY),
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                SourcePickerPopupBody(
                    target = target,
                    sourceOptions = sourceOptions,
                    onPickSingle = { key ->
                        open = false
                        val activeSingle = (target as? DesktopSearchTarget.Single)?.sourceKey
                        if (key != activeSingle) onSelectSingle(key)
                    },
                    onConfirmMulti = { keys ->
                        open = false
                        onSelectMulti(keys)
                    },
                    onDismiss = { open = false },
                )
            }
        }
    }
}

/** 弹出面板主体：两档分段（单源 / 多源聚合）+ 搜索与分级过滤 + 分组列表 + 多选底部动作条。 */
@Composable
private fun SourcePickerPopupBody(
    target: DesktopSearchTarget,
    sourceOptions: List<DesktopSearchSourceOption>,
    onPickSingle: (String) -> Unit,
    onConfirmMulti: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = VeneraTokens
    val palette = LocalChromePalette.current
    val enabledKeys = remember(sourceOptions) { sourceOptions.map { it.key } }
    val safeKeys = remember(sourceOptions) {
        sourceOptions.filter { !isR18SearchSource(it.key) }.map { it.key }.toSet()
    }
    val r18Keys = remember(sourceOptions) {
        sourceOptions.filter { isR18SearchSource(it.key) }.map { it.key }.toSet()
    }

    var pickerTabMulti by remember(target) { mutableStateOf(target is DesktopSearchTarget.Aggregate) }
    var filterText by remember { mutableStateOf("") }
    var filterTab by remember { mutableStateOf(DesktopSearchSourceFilterTab.ALL) }
    var highlightIdx by remember { mutableIntStateOf(0) }
    var draftKeys by remember(target, sourceOptions) {
        mutableStateOf(
            when (target) {
                is DesktopSearchTarget.Single -> setOf(target.sourceKey)
                is DesktopSearchTarget.Aggregate ->
                    if (target.allEnabled || target.selectedKeys.isEmpty()) enabledKeys.toSet()
                    else enabledKeys.filter { it in target.selectedKeys }.toSet().ifEmpty { enabledKeys.toSet() }
            },
        )
    }

    val grouped = remember(sourceOptions, filterText, filterTab) {
        filterAndGroupSearchSources(sourceOptions, filterText, filterTab)
    }
    val filtered = grouped.flat
    val showGroupHeaders = filterTab == DesktopSearchSourceFilterTab.ALL &&
        grouped.safe.isNotEmpty() &&
        grouped.r18.isNotEmpty()

    val activeSingleKey = (target as? DesktopSearchTarget.Single)?.sourceKey
    val popupShape = GalleryTokens.Shape.Large

    Column(
        modifier = Modifier
            .width(356.dp)
            .vShadow(12.dp, popupShape)
            .clip(popupShape)
            .background(palette.elevated)
            .border(1.dp, GalleryTokens.Color.OutlineHover, popupShape)
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (ev.key) {
                    Key.Escape -> {
                        onDismiss()
                        true
                    }
                    Key.DirectionDown -> {
                        if (filtered.isNotEmpty()) highlightIdx = (highlightIdx + 1).coerceAtMost(filtered.lastIndex)
                        true
                    }
                    Key.DirectionUp -> {
                        if (filtered.isNotEmpty()) highlightIdx = (highlightIdx - 1).coerceAtLeast(0)
                        true
                    }
                    Key.Enter -> {
                        val hit = filtered.getOrNull(highlightIdx)
                        if (hit != null && !pickerTabMulti) {
                            onPickSingle(hit.key)
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            }
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 顶部两档分段：单源 / 多源聚合
        val segShape = RoundedCornerShape(9.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(segShape)
                .background(palette.pill)
                .border(1.dp, GalleryTokens.Color.OutlineHairline, segShape)
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            PickerModeTab(
                label = DesktopI18n.t("单源", "Single"),
                selected = !pickerTabMulti,
                onClick = { pickerTabMulti = false },
                modifier = Modifier.weight(1f),
            )
            PickerModeTab(
                label = DesktopI18n.t("多源聚合", "Multi-Source"),
                selected = pickerTabMulti,
                onClick = {
                    pickerTabMulti = true
                    if (draftKeys.isEmpty() && activeSingleKey != null) {
                        draftKeys = setOf(activeSingleKey)
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }

        // 内置过滤输入框
        com.venera.desktop.ui.components.DesktopInputField(
            value = filterText,
            onValueChange = {
                filterText = it
                highlightIdx = 0
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = DesktopI18n.t("搜索已启用漫画源…", "Filter enabled sources…"),
            leadingIcon = Icons.Outlined.Search,
        )

        // 方案 2：顶部分级过滤条（仅在同时存在正常源与 R18 源时显示）
        if (grouped.totalSafeCount > 0 && grouped.totalR18Count > 0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SourceCategoryChip(
                    label = DesktopI18n.t("全部 (${sourceOptions.size})", "All (${sourceOptions.size})"),
                    selected = filterTab == DesktopSearchSourceFilterTab.ALL,
                    onClick = { filterTab = DesktopSearchSourceFilterTab.ALL; highlightIdx = 0 },
                )
                SourceCategoryChip(
                    label = DesktopI18n.t("正常源 (${grouped.totalSafeCount})", "Safe (${grouped.totalSafeCount})"),
                    selected = filterTab == DesktopSearchSourceFilterTab.SAFE,
                    onClick = { filterTab = DesktopSearchSourceFilterTab.SAFE; highlightIdx = 0 },
                )
                SourceCategoryChip(
                    label = DesktopI18n.t("R18源 (${grouped.totalR18Count})", "R18 (${grouped.totalR18Count})"),
                    selected = filterTab == DesktopSearchSourceFilterTab.R18,
                    onClick = { filterTab = DesktopSearchSourceFilterTab.R18; highlightIdx = 0 },
                )
            }
        }

        // 方案 1：多源快捷勾选操作行（支持一键只选正常 / 只选R18 / 全选 / 清空）
        if (pickerTabMulti) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = DesktopI18n.t("已选 ${draftKeys.size}/${sourceOptions.size}", "Selected ${draftKeys.size}/${sourceOptions.size}"),
                    fontSize = VeneraType.micro.size,
                    lineHeight = VeneraType.micro.lineHeight,
                    color = tokens.color.textTertiary,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                if (safeKeys.isNotEmpty() && r18Keys.isNotEmpty()) {
                    BulkQuickAction(
                        label = DesktopI18n.t("只选正常", "Safe only"),
                        active = draftKeys == safeKeys,
                        onClick = { draftKeys = safeKeys },
                    )
                    BulkQuickAction(
                        label = DesktopI18n.t("只选R18", "R18 only"),
                        active = draftKeys == r18Keys,
                        onClick = { draftKeys = r18Keys },
                    )
                }
                BulkQuickAction(
                    label = DesktopI18n.t("全选", "All"),
                    active = draftKeys.size == enabledKeys.size,
                    onClick = { draftKeys = enabledKeys.toSet() },
                )
                BulkQuickAction(
                    label = DesktopI18n.t("清空", "Clear"),
                    active = false,
                    muted = true,
                    onClick = { draftKeys = emptySet() },
                )
            }
        }

        // 源清单（按正常源 / R18 源分组呈现）
        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(72.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = DesktopI18n.t("无匹配漫画源", "No matching sources"),
                    fontSize = VeneraType.aux.size,
                    color = tokens.color.textTertiary,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 310.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (showGroupHeaders) {
                    item(key = "hdr-safe") {
                        SourceGroupHeader(DesktopI18n.t("正常源 (${grouped.safe.size})", "Safe (${grouped.safe.size})"))
                    }
                    itemsIndexed(grouped.safe, key = { _, opt -> opt.key }) { idx, opt ->
                        val checked = opt.key in draftKeys
                        val selectedSingle = !pickerTabMulti && opt.key == activeSingleKey
                        SourcePickerOptionRow(
                            option = opt,
                            isR18 = false,
                            multiMode = pickerTabMulti,
                            checked = checked,
                            selectedSingle = selectedSingle,
                            keyboardHighlighted = !pickerTabMulti && idx == highlightIdx,
                            onClick = {
                                if (pickerTabMulti) {
                                    draftKeys = if (checked) draftKeys - opt.key else draftKeys + opt.key
                                } else {
                                    onPickSingle(opt.key)
                                }
                            },
                        )
                    }
                    item(key = "hdr-r18") {
                        SourceGroupHeader(DesktopI18n.t("R18 源 (${grouped.r18.size})", "R18 (${grouped.r18.size})"))
                    }
                    itemsIndexed(grouped.r18, key = { _, opt -> opt.key }) { idx, opt ->
                        val flatIndex = grouped.safe.size + idx
                        val checked = opt.key in draftKeys
                        val selectedSingle = !pickerTabMulti && opt.key == activeSingleKey
                        SourcePickerOptionRow(
                            option = opt,
                            isR18 = true,
                            multiMode = pickerTabMulti,
                            checked = checked,
                            selectedSingle = selectedSingle,
                            keyboardHighlighted = !pickerTabMulti && flatIndex == highlightIdx,
                            onClick = {
                                if (pickerTabMulti) {
                                    draftKeys = if (checked) draftKeys - opt.key else draftKeys + opt.key
                                } else {
                                    onPickSingle(opt.key)
                                }
                            },
                        )
                    }
                } else {
                    itemsIndexed(filtered, key = { _, opt -> opt.key }) { index, opt ->
                        val checked = opt.key in draftKeys
                        val selectedSingle = !pickerTabMulti && opt.key == activeSingleKey
                        SourcePickerOptionRow(
                            option = opt,
                            isR18 = isR18SearchSource(opt.key),
                            multiMode = pickerTabMulti,
                            checked = checked,
                            selectedSingle = selectedSingle,
                            keyboardHighlighted = !pickerTabMulti && index == highlightIdx,
                            onClick = {
                                if (pickerTabMulti) {
                                    draftKeys = if (checked) draftKeys - opt.key else draftKeys + opt.key
                                } else {
                                    onPickSingle(opt.key)
                                }
                            },
                        )
                    }
                }
            }
        }

        // 多源底部确认条
        if (pickerTabMulti) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(GalleryTokens.Color.OutlineHairline),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = when (draftKeys.size) {
                        0 -> DesktopI18n.t("至少选 1 个漫画源", "Select at least 1 source")
                        1 -> DesktopI18n.t("仅选 1 源将切入单源", "1 source switches to single mode")
                        else -> DesktopI18n.t("各源按默认排序检索", "Searches each source with default sort")
                    },
                    fontSize = VeneraType.micro.size,
                    lineHeight = VeneraType.micro.lineHeight,
                    color = tokens.color.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .height(30.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = DesktopI18n.t("取消", "Cancel"),
                        fontSize = VeneraType.aux.size,
                        color = tokens.color.textSecondary,
                    )
                }
                val canConfirm = draftKeys.isNotEmpty()
                val draftLabel = describeAggregateLabel(
                    activeKeys = enabledKeys.filter { it in draftKeys },
                    allOptions = sourceOptions,
                )
                val confirmLabel = when {
                    draftKeys.size == 1 -> DesktopI18n.t("进入单源", "Use Single")
                    draftKeys.size == sourceOptions.size && sourceOptions.size > 1 -> DesktopI18n.t("全网聚合 (${draftKeys.size})", "All Sources (${draftKeys.size})")
                    draftLabel == DesktopI18n.t("正常源聚合", "Safe Sources") || draftLabel == DesktopI18n.t("R18 聚合", "R18 Sources") -> "$draftLabel (${draftKeys.size})"
                    else -> DesktopI18n.t("确认聚合 (${draftKeys.size})", "Search (${draftKeys.size})")
                }
                Box(
                    modifier = Modifier
                        .height(30.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(
                            if (canConfirm) tokens.color.primary
                            else tokens.color.primary.copy(alpha = 0.35f),
                        )
                        .clickable(enabled = canConfirm) { onConfirmMulti(draftKeys) }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = confirmLabel,
                        fontSize = VeneraType.aux.size,
                        fontWeight = VeneraType.cardTitle.weight,
                        color = tokens.color.onPrimary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun PickerModeTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    val palette = LocalChromePalette.current
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = modifier
            .height(28.dp)
            .clip(shape)
            .background(if (selected) palette.card else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = VeneraType.aux.size,
            lineHeight = VeneraType.aux.lineHeight,
            fontWeight = if (selected) VeneraType.cardTitle.weight else FontWeight.Normal,
            color = if (selected) tokens.color.textPrimary else tokens.color.textSecondary,
            maxLines = 1,
        )
    }
}

@Composable
private fun SourceCategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tokens = VeneraTokens
    val palette = LocalChromePalette.current
    val shape = RoundedCornerShape(7.dp)
    Box(
        modifier = Modifier
            .height(25.dp)
            .clip(shape)
            .background(if (selected) tokens.color.primary.copy(alpha = 0.14f) else palette.pill)
            .border(
                width = 1.dp,
                color = if (selected) tokens.color.primary.copy(alpha = 0.42f) else GalleryTokens.Color.OutlineHairline,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = VeneraType.micro.size,
            lineHeight = VeneraType.micro.lineHeight,
            fontWeight = if (selected) VeneraType.cardTitle.weight else FontWeight.Normal,
            color = if (selected) tokens.color.primary else tokens.color.textSecondary,
            maxLines = 1,
        )
    }
}

@Composable
private fun BulkQuickAction(
    label: String,
    active: Boolean,
    muted: Boolean = false,
    onClick: () -> Unit,
) {
    val tokens = VeneraTokens
    val shape = RoundedCornerShape(5.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (active) tokens.color.primary.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = label,
            fontSize = VeneraType.micro.size,
            lineHeight = VeneraType.micro.lineHeight,
            fontWeight = if (active) VeneraType.cardTitle.weight else FontWeight.Normal,
            color = when {
                active -> tokens.color.primary
                muted -> tokens.color.textTertiary
                else -> tokens.color.textSecondary
            },
            maxLines = 1,
        )
    }
}

@Composable
private fun SourceGroupHeader(title: String) {
    Text(
        text = title,
        fontSize = VeneraType.micro.size,
        lineHeight = VeneraType.micro.lineHeight,
        fontWeight = VeneraType.cardTitle.weight,
        color = VeneraTokens.color.textTertiary,
        modifier = Modifier.padding(start = 6.dp, top = 6.dp, bottom = 2.dp),
        maxLines = 1,
    )
}

@Composable
private fun SourcePickerOptionRow(
    option: DesktopSearchSourceOption,
    isR18: Boolean,
    multiMode: Boolean,
    checked: Boolean,
    selectedSingle: Boolean,
    keyboardHighlighted: Boolean,
    onClick: () -> Unit,
) {
    val tokens = VeneraTokens
    val palette = LocalChromePalette.current
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    val shape = RoundedCornerShape(8.dp)
    val rowBg = when {
        selectedSingle -> tokens.color.primary.copy(alpha = 0.14f)
        hovered || keyboardHighlighted -> palette.hover
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(shape)
            .background(rowBg)
            .hoverable(hoverSource)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        if (multiMode) {
            val boxShape = RoundedCornerShape(4.dp)
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(boxShape)
                    .background(if (checked) tokens.color.primary else Color.Transparent)
                    .border(
                        width = 1.5.dp,
                        color = if (checked) tokens.color.primary else GalleryTokens.Color.OutlineHover,
                        shape = boxShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (checked) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = tokens.color.onPrimary,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
        DesktopSearchSourceIcon(
            sourceKey = option.key,
            label = option.label,
            size = 20.dp,
        )
        Text(
            text = option.label,
            fontSize = VeneraType.control.size,
            lineHeight = VeneraType.control.lineHeight,
            fontWeight = if (selectedSingle) VeneraType.cardTitle.weight else FontWeight.Normal,
            color = tokens.color.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (isR18) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(palette.pill)
                    .border(1.dp, GalleryTokens.Color.OutlineHairline, RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            ) {
                Text(
                    text = "R18",
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    color = tokens.color.textSecondary,
                    maxLines = 1,
                )
            }
        }
        Text(
            text = option.key,
            fontSize = VeneraType.micro.size,
            lineHeight = VeneraType.micro.lineHeight,
            color = tokens.color.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 96.dp),
        )
        if (!multiMode && selectedSingle) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = tokens.color.primary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** 叠放显示前 3 个已选漫画源的小图标。 */
@Composable
private fun StackedSourceIcons(options: List<DesktopSearchSourceOption>) {
    val palette = LocalChromePalette.current
    val count = options.size.coerceAtMost(3)
    if (count == 0) return
    val totalWidth = 18.dp + 12.dp * (count - 1)
    Box(
        modifier = Modifier.width(totalWidth).height(18.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        options.take(3).forEachIndexed { index, opt ->
            Box(
                modifier = Modifier
                    .offset(x = (index * 12).dp)
                    .size(18.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(palette.elevated)
                    .padding(1.dp),
            ) {
                DesktopSearchSourceIcon(
                    sourceKey = opt.key,
                    label = opt.label,
                    size = 16.dp,
                )
            }
        }
    }
}

/**
 * 漫画源站点图标：优先解码内置真实图标，缺失时优雅退回首字标识，不留空白块。
 *
 * @param sourceKey 漫画源内部键。
 * @param label 漫画源显示名，解码缺失时取首字展示。
 * @param size 图标槽边长。
 */
@Composable
internal fun DesktopSearchSourceIcon(
    sourceKey: String,
    label: String,
    size: Dp = 20.dp,
) {
    val tokens = VeneraTokens
    val bitmap = remember(sourceKey) { decodeSourceIconBitmap(sourceKey) }
    val shape = RoundedCornerShape(5.dp)
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(tokens.color.surfaceContainerHigh.copy(alpha = 0.6f))
            .border(1.dp, GalleryTokens.Color.OutlineHairline, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            val monogram = if (label.isEmpty()) "?" else {
                val c = label[0]
                if (c.isLetter() && c.code < 128) label.take(2) else c.toString()
            }
            Text(
                text = monogram,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                color = tokens.color.textSecondary,
                maxLines = 1,
            )
        }
    }
}

private fun decodeSourceIconBitmap(key: String): ImageBitmap? =
    DesktopSourceIcons.bytesOf(key)?.let { bytes ->
        runCatching {
            org.jetbrains.skia.Image.makeFromEncoded(bytes).use { it.toComposeImageBitmap() }
        }.getOrNull()
    }
