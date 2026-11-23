package com.venera.desktop.history

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.hsl
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.components.venera.VeneraCoverMask
import com.venera.compose.data.db.HistoryRecord
import com.venera.compose.ui.tokens.VeneraCardEnter
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.compose.ui.tokens.LocalVeneraDarkTheme
import com.venera.desktop.gallery.ui.desktopComicHistoryProgressText
import com.venera.desktop.gallery.ui.selection.DesktopCardSelectBadge
import com.venera.desktop.gallery.ui.selection.DesktopSelectionDock
import com.venera.desktop.gallery.ui.selection.DesktopSelectionDockBottomInset
import com.venera.desktop.gallery.ui.selection.DockActionPill
import com.venera.desktop.gallery.ui.selection.DockIconAction
import com.venera.desktop.gallery.ui.selection.LocalSelectionDockHazeState
import com.venera.desktop.settings.DesktopI18n
import com.venera.desktop.settings.LocalDesktopLanguage
import com.venera.desktop.ui.DesktopActionButton
import com.venera.desktop.ui.DesktopGlobalNavAction
import com.venera.desktop.ui.DesktopListShortcutAction
import com.venera.desktop.ui.DesktopModifierState
import com.venera.desktop.ui.DesktopSegmented
import com.venera.desktop.ui.DesktopPageShell
import com.venera.desktop.ui.DesktopPageWidth
import com.venera.desktop.ui.RegisterDesktopShortcutHandler
import com.venera.desktop.ui.components.desktopCoverFloat
import com.venera.desktop.ui.components.desktopUiFloat
import com.venera.desktop.ui.computeShiftRangeSelection
import com.venera.desktop.ui.matchGlobalNavShortcut
import com.venera.desktop.ui.matchListShortcut
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.floor

/**
 * 桌面「阅读历史」屏 ·persona 定稿重做（个性档首例）。
 *
 * 规矩出处：`docs/designs/history-source-filter-2026-11-09/`（ui-mockup-persona + design-tokens v2
 * + component-spec）与 `docs/designs/personality-tier-policy-2026-11-09.md`：
 *  - 壳层不放开：仍走 [DesktopPageShell]（title/返回/页宽母稿），统计件只进 actions 槽；
 *  - 个性在内容区：阅读热力条、源筛选按钮（内嵌源身份色点）、时间轴分组、全幅封面流
 *    （文字压 scrim + 盖底进度条 + hover 继续层）、搁置标记、时段分布；
 *  - 判据一条不删：行门控仍吃壳里唯一判据（`openableOf`/`noteOf`/`maskedOf`），
 *    「清空全部」两按武装确认保留，右键按源搜标签（`onSearchComicTag`）保留。
 *
 * 数据派生全部来自既有 [HistoryRecord] 字段（updatedAt / lastPageIndex / totalPages / sourceName），
 * 不新增数据口；阅读时长未落库，节点摘要与时段分布先落「部数 / 最后阅读时刻」口径。
 */
@Composable
internal fun DesktopHistoryScreen(
    mode: WideScreenLayoutMode,
    records: List<HistoryRecord>,
    loaded: Boolean,
    note: String?,
    /** 兼容旧签名；屏内副题改用统计句（共 N 条 · 连续 M 天有阅读）。 */
    countNote: String,
    maskedOf: (HistoryRecord) -> Boolean,
    openableOf: (HistoryRecord) -> Boolean,
    noteOf: (HistoryRecord) -> String?,
    onOpen: (HistoryRecord) -> Unit,
    /** 历史卡右键按该记录自己的源搜标签（源键 + 词）。 */
    onSearchComicTag: ((sourceKey: String, tag: String) -> Unit)? = null,
    /** 「清空全部」（两按确认由本屏做，库写口仍只有壳那一颗）。 */
    onClearAll: (() -> Unit)?,
    /** 多选批量删除历史记录（为空时仅提供复制等非写操作）。 */
    onDeleteRecords: ((List<HistoryRecord>) -> Unit)? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    var clearArmed by remember { mutableStateOf(false) }
    // 空串 = 全部（rememberSaveable 不存 null）
    var selectedSourceRaw by rememberSaveable { mutableStateOf("") }
    var recentFirst by rememberSaveable { mutableStateOf(true) }
    var query by rememberSaveable { mutableStateOf("") }

    val today = remember(records) { LocalDate.now() }
    val sourceCounts = remember(records) { deriveSourceCounts(records) }
    val streak = remember(records, today) { computeStreak(records, today) }
    val hourBuckets = remember(records) { deriveHourBuckets(records) }

    val activeSource = selectedSourceRaw.takeIf { it.isNotEmpty() }
    val filtered = remember(records, selectedSourceRaw, recentFirst, query) {
        filterAndSort(records, activeSource, recentFirst, query)
    }
    val lang = LocalDesktopLanguage.current
    val sections = remember(filtered, today, lang) { groupByRecency(filtered, today) }

    fun historyRecordKey(record: HistoryRecord): String = "${record.comicId}|${record.sourceName}"
    val visibleKeys = remember(filtered) { filtered.map(::historyRecordKey) }
    val selectedKeys = remember { mutableStateListOf<String>() }
    val selectedSet = selectedKeys.toSet()
    var lastAnchorKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(visibleKeys) {
        val valid = visibleKeys.toHashSet()
        selectedKeys.removeAll { it !in valid }
    }

    fun toggleSelect(record: HistoryRecord) {
        val key = historyRecordKey(record)
        if (DesktopModifierState.isShiftDown && lastAnchorKey != null) {
            val next = computeShiftRangeSelection(
                orderedKeys = visibleKeys,
                anchorKey = lastAnchorKey,
                targetKey = key,
                currentSelected = selectedKeys.toSet(),
            )
            selectedKeys.clear()
            selectedKeys.addAll(next)
        } else {
            if (key in selectedKeys) selectedKeys.remove(key) else selectedKeys.add(key)
        }
        lastAnchorKey = key
    }

    val selectedRecords = remember(filtered, selectedSet) {
        filtered.filter { historyRecordKey(it) in selectedSet }
    }

    fun deleteSelectedRecords() {
        val targets = selectedRecords.toList()
        if (targets.isEmpty() || onDeleteRecords == null) return
        selectedKeys.clear()
        onDeleteRecords(targets)
    }

    RegisterDesktopShortcutHandler(visibleKeys.size, selectedSet.size, priority = 100) { signal ->
        when (matchListShortcut(signal)) {
            DesktopListShortcutAction.SELECT_ALL -> {
                if (visibleKeys.isEmpty()) return@RegisterDesktopShortcutHandler false
                selectedKeys.clear()
                selectedKeys.addAll(visibleKeys)
                true
            }
            DesktopListShortcutAction.DELETE_SELECTED -> {
                if (selectedSet.isEmpty() || onDeleteRecords == null) return@RegisterDesktopShortcutHandler false
                deleteSelectedRecords()
                true
            }
            else -> {
                if (matchGlobalNavShortcut(signal) == DesktopGlobalNavAction.ESCAPE &&
                    selectedSet.isNotEmpty()
                ) {
                    selectedKeys.clear()
                    true
                } else {
                    false
                }
            }
        }
    }

    DesktopPageShell(
        mode = mode,
        title = DesktopI18n.t("历史", "History"),
        subtitle = if (records.isEmpty()) {
            countNote.takeIf { it.isNotBlank() }
        } else {
            DesktopI18n.t("共 ${records.size} 条 · 连续 $streak 天有阅读", "${records.size} records · $streak-day streak")
        },
        note = note,
        onBack = onBack,
        modifier = modifier,
        width = DesktopPageWidth.Browse,
        titleCard = false,
        actions = {
            if (records.isNotEmpty()) {
                StreakRing(streak)
                Spacer(Modifier.width(10.dp))
                HourDistribution(hourBuckets)
            }
        },
    ) {
        if (records.isEmpty()) {
            when {
                !loaded -> CenterNote(DesktopI18n.t("正在读本地历史…", "Loading local history…"))
                note != null -> CenterNote(note)
                else -> CenterNote(DesktopI18n.t("还没有阅读记录。读一本之后，这里会出现最近读过的书。", "No reading history yet. Books you read will appear here."))
            }
        } else {
            val dockHazeState = rememberHazeState()
            CompositionLocalProvider(LocalSelectionDockHazeState provides dockHazeState) {
            Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(dockHazeState, zIndex = 0f)
                    .verticalScroll(rememberScrollState()),
            ) {
                HeatmapStrip(records = records, today = today)

                HistoryFilterRow(
                    sourceCounts = sourceCounts,
                    selectedSource = activeSource,
                    onSelectSource = { selectedSourceRaw = it.orEmpty() },
                    recentFirst = recentFirst,
                    onToggleSort = { recentFirst = !recentFirst },
                    query = query,
                    onQueryChange = { query = it },
                    clearArmed = clearArmed,
                    onClearClick = {
                        if (clearArmed) {
                            clearArmed = false
                            onClearAll?.invoke()
                        } else {
                            clearArmed = true
                        }
                    },
                    showClear = onClearAll != null && activeSource == null && query.isBlank(),
                )

                if (filtered.isEmpty()) {
                    PersonaEmptyNote(
                        title = DesktopI18n.t("此源暂无记录", "No records for this source"),
                        body = DesktopI18n.t("阅读后自动出现在这里", "Items appear here automatically after reading"),
                    )
                } else {
                    sections.forEach { section ->
                        TimelineSection(
                            section = section,
                            maskedOf = maskedOf,
                            openableOf = openableOf,
                            noteOf = noteOf,
                            selectedKeys = selectedSet,
                            hasSelection = selectedSet.isNotEmpty(),
                            keyOf = ::historyRecordKey,
                            onToggleSelect = ::toggleSelect,
                            onOpen = onOpen,
                            onSearchComicTag = onSearchComicTag,
                            today = today,
                        )
                    }
                }
                Spacer(
                    Modifier.height(
                        if (selectedSet.isNotEmpty()) DesktopSelectionDockBottomInset else tokens.spacing.space6,
                    ),
                )
            }
            DesktopSelectionDock(
                selectedCount = selectedSet.size,
                totalCount = visibleKeys.size,
                onSelectAll = {
                    selectedKeys.clear()
                    selectedKeys.addAll(visibleKeys)
                },
                onInvertSelect = {
                    selectedKeys.clear()
                    selectedKeys.addAll(visibleKeys.filter { it !in selectedSet })
                },
                onClearSelect = { selectedKeys.clear() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = DesktopSelectionDockBottomInset),
            ) {
                if (onDeleteRecords != null) {
                    DockActionPill(
                        text = DesktopI18n.t("删除记录 (${selectedSet.size})", "Delete (${selectedSet.size})"),
                        danger = true,
                        onClick = { deleteSelectedRecords() },
                    )
                }
                DockIconAction(
                    icon = Icons.Outlined.ContentCopy,
                    description = DesktopI18n.t("复制所选标题", "Copy titles"),
                    onClick = {
                        val titles = selectedRecords.mapNotNull { it.title.takeIf(String::isNotBlank) }.distinct()
                        if (titles.isNotEmpty()) {
                            java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(
                                java.awt.datatransfer.StringSelection(titles.joinToString("\n")),
                                null,
                            )
                        }
                    },
                )
            }
            }
            }
        }
    }
}

/* ── 数据派生（纯函数，判据不进这里） ────────────────────── */

private fun recordDate(record: HistoryRecord): LocalDate =
    Instant.ofEpochMilli(record.updatedAt).atZone(ZoneId.systemDefault()).toLocalDate()

private fun deriveSourceCounts(records: List<HistoryRecord>): List<Pair<String, Int>> =
    records
        .groupBy { it.sourceName }
        .map { (name, list) -> name to list.size }
        .sortedByDescending { it.second }

private fun filterAndSort(
    records: List<HistoryRecord>,
    selectedSource: String?,
    recentFirst: Boolean,
    query: String,
): List<HistoryRecord> {
    val q = query.trim()
    val step = records
        .filter { selectedSource == null || it.sourceName == selectedSource }
        .filter {
            q.isBlank() || it.title.contains(q, ignoreCase = true) ||
                it.sourceName.contains(q, ignoreCase = true)
        }
    return if (recentFirst) {
        step.sortedByDescending { it.updatedAt }
    } else {
        step.sortedBy { it.title }
    }
}

/** 今天 / 昨天 / 本周 / 更早 四段分组（组内保持传入顺序）。 */
private fun groupByRecency(
    records: List<HistoryRecord>,
    today: LocalDate,
): List<HistorySection> {
    val todayLabel = DesktopI18n.tr("今天", "Today")
    val yesterdayLabel = DesktopI18n.tr("昨天", "Yesterday")
    val thisWeekLabel = DesktopI18n.tr("本周", "This Week")
    val earlierLabel = DesktopI18n.tr("更早", "Earlier")
    val buckets = linkedMapOf(
        todayLabel to mutableListOf<HistoryRecord>(),
        yesterdayLabel to mutableListOf(),
        thisWeekLabel to mutableListOf(),
        earlierLabel to mutableListOf(),
    )
    records.forEach { record ->
        val days = today.toEpochDay() - recordDate(record).toEpochDay()
        val key = when {
            days <= 0L -> todayLabel
            days == 1L -> yesterdayLabel
            days <= 6L -> thisWeekLabel
            else -> earlierLabel
        }
        buckets.getValue(key).add(record)
    }
    return buckets.filter { it.value.isNotEmpty() }.map { (label, list) ->
        HistorySection(label = label, dotColor = majoritySourceColor(list), records = list)
    }
}

private data class HistorySection(
    val label: String,
    val dotColor: Color,
    val records: List<HistoryRecord>,
)

private fun majoritySourceColor(records: List<HistoryRecord>): Color {
    val top = records.groupBy { it.sourceName }.maxByOrNull { it.value.size }?.key.orEmpty()
    return personaSourceColor(top)
}

/** 连续阅读天数：从今天（今天没有则从昨天）往前数每天都有记录。 */
private fun computeStreak(records: List<HistoryRecord>, today: LocalDate): Int {
    val days = records.map { recordDate(it) }.toHashSet()
    var cursor = if (today in days) today else today.minusDays(1)
    var streak = 0
    while (cursor in days) {
        streak += 1
        cursor = cursor.minusDays(1)
    }
    return streak
}

/** 时段分布：按最后阅读时刻落 2 小时一桶，共 12 桶。 */
private fun deriveHourBuckets(records: List<HistoryRecord>): IntArray {
    val buckets = IntArray(12)
    records.forEach { record ->
        val hour = Instant.ofEpochMilli(record.updatedAt).atZone(ZoneId.systemDefault()).hour
        buckets[hour / 2] += 1
    }
    return buckets
}

/** 源身份色：已知源点名，其余按源键散列到 HSV 环（纯函数，不落库）。 */
internal fun personaSourceColor(sourceKey: String): Color = when {
    sourceKey.contains("copy", ignoreCase = true) -> Color(0xFFFF7A5C)
    sourceKey.contains("picseq", ignoreCase = true) -> Color(0xFF3ED6C4)
    else -> {
        val hue = abs(sourceKey.hashCode() % 360).toFloat()
        Color.hsl(hue, 0.62f, 0.62f)
    }
}

/* ── 壳 actions：连读环 + 时段分布 ────────────────────── */

@Composable
private fun StreakRing(streak: Int) {
    val accent = Color(0xFF4C8DFF)
    val track = Color.White.copy(alpha = 0.10f)
    val fraction = (streak.coerceAtMost(7)) / 7f
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(40.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                drawArc(
                    color = track,
                    startAngle = 0f, sweepAngle = 360f, useCenter = false,
                    style = stroke,
                )
                drawArc(
                    color = accent,
                    startAngle = -90f, sweepAngle = 360f * fraction, useCenter = false,
                    style = stroke,
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Column {
            Text(
                text = DesktopI18n.t("$streak 天", "${streak}d"),
                fontSize = 13.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold,
                color = VeneraTokens.color.textPrimary,
            )
            Text(text = DesktopI18n.t("连续阅读", "Streak"), fontSize = 10.sp, lineHeight = 12.sp, color = VeneraTokens.color.textTertiary)
        }
    }
}

@Composable
private fun HourDistribution(buckets: IntArray) {
    val accent = Color(0xFF4C8DFF)
    val peak = buckets.indices.maxByOrNull { buckets[it] } ?: 0
    val peakLabel = DesktopI18n.t("${peak * 2}–${peak * 2 + 2} 时", "${peak * 2}:00–${peak * 2 + 2}:00")
    val maxCount = buckets.max().coerceAtLeast(1)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier.height(34.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            buckets.forEachIndexed { index, count ->
                val height = (2.dp + 30.dp * count / maxCount)
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(height)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(if (index == peak && count > 0) accent else Color.White.copy(alpha = 0.16f)),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = peakLabel,
                fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold,
                color = VeneraTokens.color.textPrimary,
            )
            Text(text = DesktopI18n.t("时段分布", "Time of Day"), fontSize = 10.sp, lineHeight = 12.sp, color = VeneraTokens.color.textTertiary)
        }
    }
}

/* ── 内容区：阅读热力 ────────────────────── */

@Composable
private fun HeatmapStrip(records: List<HistoryRecord>, today: LocalDate) {
    val accent = Color(0xFF4C8DFF)
    val track = VeneraTokens.color.outlineVariant.copy(alpha = 0.35f)
    val dayCounts = remember(records, today) {
        val map = mutableMapOf<LocalDate, Int>()
        records.forEach { record -> map.merge(recordDate(record), 1, Int::plus) }
        map
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = DesktopI18n.t("阅读热力", "Reading Activity"),
            fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold,
            color = VeneraTokens.color.textSecondary,
            modifier = Modifier.width(58.dp).padding(top = 1.dp),
        )
        BoxWithConstraints(modifier = Modifier.weight(1f).height(84.dp)) {
            val step = 12.dp
            val cols = floor(maxWidth / step).toInt().coerceIn(8, 120)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cell = 9.dp.toPx()
                val gap = 3.dp.toPx()
                for (c in 0 until cols) {
                    for (r in 0..6) {
                        val daysBack = (cols - 1 - c) * 7 + (today.dayOfWeek.value - 1) - r
                        if (daysBack < 0) continue
                        val date = today.minusDays(daysBack.toLong())
                        val count = dayCounts[date] ?: 0
                        val color = when {
                            count == 0 -> track
                            count == 1 -> accent.copy(alpha = 0.25f)
                            count == 2 -> accent.copy(alpha = 0.50f)
                            count == 3 -> accent.copy(alpha = 0.75f)
                            else -> accent
                        }
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(c * (cell + gap), r * (cell + gap)),
                            size = Size(cell, cell),
                            cornerRadius = CornerRadius(2.5.dp.toPx()),
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier.padding(top = 1.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(0.25f, 0.5f, 0.75f, 1f).forEach { a ->
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(RoundedCornerShape(2.5.dp))
                        .background(accent.copy(alpha = a)),
                )
            }
        }
    }
}

/* ── 内容区：筛选条 ────────────────────── */

@Composable
private fun HistoryFilterRow(
    sourceCounts: List<Pair<String, Int>>,
    selectedSource: String?,
    onSelectSource: (String?) -> Unit,
    recentFirst: Boolean,
    onToggleSort: () -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    clearArmed: Boolean,
    onClearClick: () -> Unit,
    showClear: Boolean,
) {
    val tokens = VeneraTokens
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SourceButton(
                label = DesktopI18n.t("全部", "All"),
                count = sourceCounts.sumOf { it.second },
                dotColor = null,
                selected = selectedSource == null,
                onClick = { onSelectSource(null) },
            )
            sourceCounts.forEach { (name, count) ->
                SourceButton(
                    label = name,
                    count = count,
                    dotColor = personaSourceColor(name),
                    selected = selectedSource == name,
                    onClick = { onSelectSource(name) },
                )
            }
        }

        // 排序：mac 分段（最近阅读 / 标题）
        SegmentedSort(recentFirst = recentFirst, onToggle = onToggleSort)

        Spacer(Modifier.weight(1f))

        SearchField(query = query, onQueryChange = onQueryChange)

        if (showClear) {
            DesktopActionButton(
                text = if (clearArmed) DesktopI18n.t("再按一次确认清空", "Click again to confirm") else DesktopI18n.t("清空全部", "Clear All"),
                onClick = onClearClick,
            )
        }
        Spacer(Modifier.width(0.dp))
    }
}

@Composable
private fun SourceButton(
    label: String,
    count: Int,
    dotColor: Color?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val isDark = LocalVeneraDarkTheme.current
    val baseBg = if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.05f)
    val shape = RoundedCornerShape(7.dp)
    Row(
        modifier = Modifier
            .height(26.dp)
            .clip(shape)
            .then(
                if (selected) {
                    Modifier.background(
                        Brush.verticalGradient(listOf(Color(0xFF3E7BFF), Color(0xFF0A63F6))),
                        shape,
                    )
                } else {
                    Modifier.background(baseBg, shape)
                },
            )
            .border(
                0.5.dp,
                if (selected) Color.White.copy(alpha = 0.25f)
                else VeneraTokens.color.outlineVariant.copy(alpha = 0.5f),
                shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (dotColor != null) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (selected) Color.White else dotColor),
            )
        }
        Text(
            text = label,
            fontSize = 12.sp, lineHeight = 14.sp,
            fontWeight = FontWeight.Medium,
            color = if (selected) Color.White else VeneraTokens.color.textSecondary,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "$count",
            fontSize = 11.sp, lineHeight = 13.sp,
            color = if (selected) Color.White.copy(alpha = 0.75f) else VeneraTokens.color.textTertiary,
        )
    }
}

@Composable
private fun SegmentedSort(recentFirst: Boolean, onToggle: () -> Unit) {
    DesktopSegmented(
        options = listOf(
            DesktopI18n.t("最近阅读", "Recent"),
            DesktopI18n.t("标题", "Title"),
        ),
        selectedIndex = if (recentFirst) 0 else 1,
        onSelect = { index ->
            if ((index == 0) != recentFirst) onToggle()
        },
    )
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    com.venera.desktop.ui.components.DesktopSearchField(
        value = query,
        onValueChange = onQueryChange,
        onSearch = {},
        modifier = Modifier.width(190.dp),
        placeholder = DesktopI18n.t("在历史中查找", "Search history"),
    )
}

/* ── 内容区：时间轴 + 全幅封面流 ────────────────────── */

@Composable
private fun TimelineSection(
    section: HistorySection,
    maskedOf: (HistoryRecord) -> Boolean,
    openableOf: (HistoryRecord) -> Boolean,
    noteOf: (HistoryRecord) -> String?,
    selectedKeys: Set<String>,
    hasSelection: Boolean,
    keyOf: (HistoryRecord) -> String,
    onToggleSelect: (HistoryRecord) -> Unit,
    onOpen: (HistoryRecord) -> Unit,
    onSearchComicTag: ((sourceKey: String, tag: String) -> Unit)?,
    today: LocalDate,
) {
    // 右侧封面流含 SubcomposeLayout（BoxWithConstraints），不支持 IntrinsicSize.Min；
    // 轨线改用 matchParentSize + drawBehind 铺在底层随内容自适应高度。
    Box(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        // 轨线层：x≈10dp 一根竖线，上深下浅渐隐
        val lineBrush = Brush.verticalGradient(
            0f to VeneraTokens.color.outlineVariant.copy(alpha = 0.55f),
            1f to VeneraTokens.color.outlineVariant.copy(alpha = 0.12f),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawBehind {
                    drawLine(
                        brush = lineBrush,
                        start = Offset(10.dp.toPx(), 0f),
                        end = Offset(10.dp.toPx(), size.height),
                        strokeWidth = 1.dp.toPx(),
                    )
                },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // 左轨：节点 + 组标签（线已由轨线层统一画）
            Box(modifier = Modifier.width(64.dp)) {
            // 节点光晕 + 节点（色随当日主源）
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 4.dp, top = 3.dp)
                    .size(13.dp)
                    .clip(CircleShape)
                    .background(section.dotColor.copy(alpha = 0.22f)),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 7.dp, top = 6.dp)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(section.dotColor),
            )
            Row(
                modifier = Modifier.padding(start = 22.dp, top = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = section.label,
                    fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold,
                    color = VeneraTokens.color.textSecondary,
                )
                Text(
                    text = " ${section.records.size}",
                    fontSize = 10.sp, lineHeight = 12.sp,
                    color = VeneraTokens.color.textTertiary,
                )
            }
        }

        // 全幅封面流（非懒：桌面单屏条目量可控，换行列数吃容器宽）
        // 卡宽定值 150dp（对齐 persona 设计稿六列档），不满一行时左对齐——
        // 主人报「卡片太大」：单条记录吃 weight(1f) 会撑满整行。
        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            val cols = (maxWidth / (150.dp + 12.dp)).toInt().coerceIn(2, 8)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                section.records.chunked(cols).forEachIndexed { chunkIdx, rowRecords ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        rowRecords.forEachIndexed { i, record ->
                            VeneraCardEnter(
                                index = chunkIdx * cols + i,
                                cardKey = "${record.comicId}-${record.sourceName}",
                            ) {
                                val selected = keyOf(record) in selectedKeys
                                FullBleedCoverCard(
                                    record = record,
                                    masked = maskedOf(record),
                                    openable = openableOf(record),
                                    notOpenableNote = noteOf(record),
                                    stallDays = stallDaysOf(record, today),
                                    selected = selected,
                                    hasSelection = hasSelection,
                                    onToggleSelect = { onToggleSelect(record) },
                                    onClick = { if (openableOf(record)) onOpen(record) },
                                    onSearchComicTag = onSearchComicTag,
                                    modifier = Modifier.width(150.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

/** 搁置天数：超 14 天未读才挂标（读得再浅，没超期也不打扰）。 */
private fun stallDaysOf(record: HistoryRecord, today: LocalDate): Int? {
    val days = (today.toEpochDay() - recordDate(record).toEpochDay()).toInt()
    return if (days >= 14) days else null
}

@Composable
private fun FullBleedCoverCard(
    record: HistoryRecord,
    masked: Boolean,
    openable: Boolean,
    notOpenableNote: String?,
    stallDays: Int?,
    selected: Boolean,
    hasSelection: Boolean,
    onToggleSelect: () -> Unit,
    onClick: () -> Unit,
    onSearchComicTag: ((sourceKey: String, tag: String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    val tapInteraction = remember { MutableInteractionSource() }

    com.venera.desktop.ui.components.DesktopComicCardMenuArea(
        actions = com.venera.desktop.ui.components.DesktopComicCardActions(
            sourceKey = record.sourceName,
            onOpenDetail = if (openable) onClick else null,
            onSearchInSource = onSearchComicTag?.let { gate -> { tag: String -> gate(record.sourceName, tag) } },
            onCopyTitle = {
                val sel = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                sel.setContents(java.awt.datatransfer.StringSelection(record.title), null)
            },
        ),
        tag = record.title,
    ) {
        // 封面满幅档：卡面就是封面，底托整套不铺（tray = false），描边/高光刃/弥散光按
        // 封面比例罩满整卡；底部黑 scrim 与进度条照旧，只加弥散光、追光边与视差。
        Box(modifier = modifier.padding(top = 3.dp)) {
            com.venera.desktop.ui.components.DesktopFloatingCard(
                cornerRadius = 12.dp,
                surface = tokens.color.surface,
                tray = false,
                chromeAspect = 0.72f,
                hoverElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.72f)
                    .then(
                        if (selected) {
                            Modifier.border(1.5.dp, tokens.color.primary, RoundedCornerShape(12.dp))
                        } else {
                            Modifier
                        },
                    )
                    .then(
                        if (hasSelection) {
                            Modifier.clickable(interactionSource = tapInteraction, indication = null) { onToggleSelect() }
                        } else if (openable) {
                            Modifier.clickable(interactionSource = tapInteraction, indication = null) { onClick() }
                        } else {
                            Modifier
                        },
                    ),
            ) { floatState ->
                val showSelectBadge = selected || floatState.isHovered
                val overlayAlpha by animateFloatAsState(
                    targetValue = if (floatState.isHovered && openable && !hasSelection && !selected) 1f else 0f,
                    label = "personaCardOverlay",
                )
                Box(modifier = Modifier.fillMaxSize()) {
                    VeneraCover(
                        url = record.coverUrl,
                        contentDescription = record.title,
                        mask = if (masked) VeneraCoverMask.Masked else VeneraCoverMask.Visible,
                        modifier = Modifier
                            .fillMaxSize()
                            .desktopCoverFloat(floatState),
                    )

                    if (showSelectBadge) {
                        DesktopCardSelectBadge(
                            selected = selected,
                            onToggle = onToggleSelect,
                            description = DesktopI18n.t("选择 ${record.title}", "Select ${record.title}"),
                            size = 22.dp,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(6.dp)
                                .desktopUiFloat(floatState),
                        )
                    }

                    // 打不开：左上红字小标（门控判据仍在壳，屏内只如实念）；显示选择按钮时右移避让
                    if (!openable) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(start = if (showSelectBadge) 32.dp else 6.dp, top = 6.dp)
                                .desktopUiFloat(floatState)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xCC2A1F22))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = DesktopI18n.t("打不开", "Unavailable"),
                                fontSize = 9.sp, lineHeight = 11.sp,
                                color = tokens.color.error,
                            )
                        }
                    }

                    // 搁置标记：右上暗玻璃暖橘
                    if (stallDays != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .desktopUiFloat(floatState)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0x8C0A0A0E))
                                .border(0.3.dp, Color(0x73FF7A5C), RoundedCornerShape(4.dp))
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = DesktopI18n.t("搁置 $stallDays 天", "Idle ${stallDays}d"),
                                fontSize = 9.sp, lineHeight = 11.sp,
                                color = Color(0xFFFFC9A3),
                            )
                        }
                    }

                    // 底部 scrim：文字压面
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .fillMaxHeight(0.58f)
                            .background(
                                Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    0.75f to Color(0xE006060A),
                                ),
                            ),
                    )

                    val sourceEdge by androidx.compose.animation.animateColorAsState(
                        targetValue = if (floatState.isHovered) tokens.color.primary.copy(alpha = 0.6f)
                        else Color.White.copy(alpha = 0.28f),
                        animationSpec = androidx.compose.animation.core.tween(200),
                        label = "historySourceEdge",
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .desktopUiFloat(floatState)
                            .padding(horizontal = 9.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0x80100E12))
                                .border(0.3.dp, sourceEdge, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(personaSourceColor(record.sourceName)),
                            )
                            Text(
                                text = record.sourceName,
                                fontSize = 9.sp, lineHeight = 11.sp, fontWeight = FontWeight.Medium,
                                color = Color(0xFFEDEDF2),
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = record.title,
                            fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = metaLine(record, notOpenableNote),
                            fontSize = 10.sp, lineHeight = 12.sp,
                            color = Color.White.copy(alpha = 0.62f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }

                    // 盖底进度条
                    val fraction = ((record.lastPageIndex + 1f) / record.totalPages.coerceAtLeast(1)).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(Color.White.copy(alpha = 0.14f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .fillMaxHeight()
                                .background(
                                    Brush.horizontalGradient(listOf(Color(0xFF0A63F6), Color(0xFF6FA6FF))),
                                ),
                        )
                    }

                    // hover 继续层
                    if (overlayAlpha > 0.01f) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .alpha(overlayAlpha)
                                .background(Color(0x6608080E)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Brush.verticalGradient(listOf(Color(0xFF3E7BFF), Color(0xFF0A63F6))))
                                    .border(0.5.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = DesktopI18n.t("继续 ▸", "Continue ▸"),
                                    fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun metaLine(record: HistoryRecord, notOpenableNote: String?): String {
    val progress = desktopComicHistoryProgressText(record)
    return if (notOpenableNote.isNullOrBlank()) progress else "$progress · $notOpenableNote"
}

/* ── 空态 ────────────────────── */

@Composable
private fun PersonaEmptyNote(title: String, body: String) {
    val tokens = VeneraTokens
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold,
            color = tokens.color.textSecondary,
        )
        Text(
            text = body,
            fontSize = 12.sp, lineHeight = 14.sp,
            color = tokens.color.textTertiary,
        )
    }
}

@Composable
private fun CenterNote(text: String) {
    // 统一空态：此前是裸灰字，升级到与收藏/画廊同一张玻璃卡。文案仍是逐字钉着的成品句。
    com.venera.desktop.ui.DesktopEmptyState(text = text)
}
