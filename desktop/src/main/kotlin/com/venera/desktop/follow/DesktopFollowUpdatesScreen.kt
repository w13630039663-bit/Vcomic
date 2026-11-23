package com.venera.desktop.follow

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.components.venera.VeneraCoverMask
import com.venera.compose.data.db.ComicFollowRecord
import com.venera.compose.data.db.ComicFollowStatus
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.gallery.ui.LocalChromePalette
import com.venera.desktop.gallery.ui.selection.DesktopCardSelectBadge
import com.venera.desktop.gallery.ui.selection.DesktopSelectionDock
import com.venera.desktop.gallery.ui.selection.DesktopSelectionDockBottomInset
import com.venera.desktop.gallery.ui.selection.DockActionPill
import com.venera.desktop.gallery.ui.selection.LocalSelectionDockHazeState
import com.venera.desktop.settings.DesktopComicCardLayout
import com.venera.desktop.settings.DesktopI18n
import com.venera.desktop.ui.DesktopButton
import com.venera.desktop.ui.DesktopButtonSize
import com.venera.desktop.ui.DesktopButtonVariant
import com.venera.desktop.ui.DesktopGlobalNavAction
import com.venera.desktop.ui.DesktopListShortcutAction
import com.venera.desktop.ui.DesktopModifierState
import com.venera.desktop.ui.DesktopPopUpButton
import com.venera.desktop.ui.RegisterDesktopShortcutHandler
import com.venera.desktop.ui.components.DesktopFloatingCard
import com.venera.desktop.ui.components.desktopCoverFloat
import com.venera.desktop.ui.components.desktopUiFloat
import com.venera.desktop.ui.computeShiftRangeSelection
import com.venera.desktop.ui.matchGlobalNavShortcut
import com.venera.desktop.ui.matchListShortcut
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Text

/** 追更视图的筛选档；顺序即胶囊顺序。 */
enum class DesktopFollowFilter(val labelCn: String, val labelEn: String) {
    ALL("全部", "All"),
    UPDATED("有更新", "Updated"),
    ACTIVE("追更中", "Active"),
    FINISHED("已完结·暂停", "Paused · Finished"),
    ERROR("异常", "Failed"),
}

/** 按档筛选；「有更新」只留有未读红点的条目，「异常」只看最近一次失败。 */
fun desktopFollowFiltered(records: List<ComicFollowRecord>, filter: DesktopFollowFilter): List<ComicFollowRecord> =
    when (filter) {
        DesktopFollowFilter.ALL -> records
        DesktopFollowFilter.UPDATED -> records.filter { it.hasNewUpdate }
        DesktopFollowFilter.ACTIVE -> records.filter { it.status == ComicFollowStatus.ACTIVE }
        DesktopFollowFilter.FINISHED -> records.filter {
            it.status == ComicFollowStatus.PAUSED || it.status == ComicFollowStatus.COMPLETED
        }
        DesktopFollowFilter.ERROR -> records.filter { it.lastError.isNotBlank() }
    }

/** 未读更新条数：左侧书库入口角标与页头共用这一颗。 */
fun desktopFollowUnreadCount(records: List<ComicFollowRecord>): Int = records.count { it.hasNewUpdate }

/**
 * 卡片主状态行：异常优先（说成因），其次新更（说更到哪一话），再次"还没建基线"。
 *
 * 没新更且无异常时只说"暂无新更"，不拿更新时间冒充"有更新"。
 */
fun desktopFollowStatusLine(record: ComicFollowRecord): String = when {
    record.lastError.isNotBlank() ->
        DesktopI18n.tr("检查失败：${record.lastError}", "Check failed: ${record.lastError}")
    record.hasNewUpdate -> {
        val title = record.latestChapterTitle.ifBlank {
            DesktopI18n.tr("第 ${record.chapterCount} 话", "Chapter ${record.chapterCount}")
        }
        if (record.newChaptersCount > 0) {
            DesktopI18n.tr("更新至：$title（+${record.newChaptersCount} 话）", "Updated to: $title (+${record.newChaptersCount})")
        } else {
            DesktopI18n.tr("更新至：$title", "Updated to: $title")
        }
    }
    !record.baselineInitialized ->
        DesktopI18n.tr("首次检查将建立基线", "First check will set the baseline")
    else -> DesktopI18n.tr("暂无新更", "No new chapters")
}

/**
 * 追更中心：筛选胶囊 + 手动检查进度 + 更新置顶清单 + 单本动作。
 *
 * 屏是纯展示件：追更清单、检查进度、打码判据与全部写动作都由装配点递进来。
 *
 * @param mode 宽窄断点，只影响列表行高与列数。
 * @param records 全量追更清单（已按有更新优先排序）。
 * @param progress 巡检器当前进度；`running` 为真时按钮变「停止检查」。
 * @param maskedOf 封面是否打码（壳的 `ux.coverMaskStateOf` 现算）。
 * @param onCheck 手动检查更新（冷却期内的条目会自动跳过）。
 * @param onCancelCheck 停止本轮巡检。
 * @param onOpen 打开详情。
 * @param onMarkRead 手动标为已读（清红点）。
 * @param onSetStatus 暂停 / 恢复 / 标完结。
 * @param onRetry 单本强制重查；null = 巡检器没接，按钮不摆。
 * @param onRemove 取消追更（删该本账）。
 */
@Composable
internal fun DesktopFollowUpdatesScreen(
    mode: WideScreenLayoutMode,
    records: List<ComicFollowRecord>,
    progress: FollowCheckProgress,
    onCheck: () -> Unit,
    onCancelCheck: () -> Unit,
    onOpen: (ComicFollowRecord) -> Unit,
    onMarkRead: (ComicFollowRecord) -> Unit,
    onSetStatus: (ComicFollowRecord, ComicFollowStatus) -> Unit,
    onRetry: ((ComicFollowRecord) -> Unit)?,
    onRemove: (ComicFollowRecord) -> Unit,
    maskedOf: (ComicFollowRecord) -> Boolean = { false },
    onBatchRetry: ((List<ComicFollowRecord>) -> Unit)? = null,
    embedded: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val palette = LocalChromePalette.current
    var filter by remember { mutableStateOf(DesktopFollowFilter.ALL) }
    var viewMode by rememberSaveable { mutableStateOf(0) } // 0 = 网格, 1 = 双列
    val cardLayout by DesktopComicCardLayout.config.collectAsState()
    val unread = desktopFollowUnreadCount(records)
    val shown = desktopFollowFiltered(records, filter)

    fun followKey(record: ComicFollowRecord): String = "${record.sourceName}|${record.comicId}"
    val selectedKeys = remember { mutableStateListOf<String>() }
    val selectedSet = selectedKeys.toSet()
    var lastAnchorKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(shown) {
        val valid = shown.mapTo(HashSet()) { followKey(it) }
        selectedKeys.removeAll { it !in valid }
    }

    fun toggleFollow(record: ComicFollowRecord) {
        val key = followKey(record)
        if (DesktopModifierState.isShiftDown && lastAnchorKey != null) {
            val ordered = shown.map { followKey(it) }
            val next = computeShiftRangeSelection(
                orderedKeys = ordered,
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

    val keyedShown = remember(shown) { shown.associateBy { followKey(it) } }
    val selectedRecords = remember(shown, selectedSet) {
        shown.filter { followKey(it) in selectedSet }
    }

    RegisterDesktopShortcutHandler(shown.size, selectedSet.size, priority = 100) { signal ->
        when (matchListShortcut(signal)) {
            DesktopListShortcutAction.SELECT_ALL -> {
                if (shown.isEmpty()) return@RegisterDesktopShortcutHandler false
                selectedKeys.clear()
                selectedKeys.addAll(shown.map { followKey(it) })
                true
            }
            DesktopListShortcutAction.DELETE_SELECTED -> {
                if (selectedRecords.isEmpty()) return@RegisterDesktopShortcutHandler false
                selectedKeys.clear()
                selectedRecords.forEach(onRemove)
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

    val dockHazeState = rememberHazeState()
    CompositionLocalProvider(LocalSelectionDockHazeState provides dockHazeState) {
    Box(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(dockHazeState, zIndex = 0f),
        ) {
        // ── 页头：只在本屏独立出现时摆（嵌进收藏 hub 时由 hub 页头承担）──
        if (!embedded) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = DesktopI18n.t("追更", "Follow Updates"),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.ink1,
                )
                Text(
                    text = if (records.isEmpty()) {
                        DesktopI18n.t("共 0 本", "0 comics")
                    } else if (unread > 0) {
                        DesktopI18n.t("共 ${records.size} 本 · $unread 本有更新", "${records.size} comics · $unread updated")
                    } else {
                        DesktopI18n.t("共 ${records.size} 本", "${records.size} comics")
                    },
                    fontSize = 12.sp,
                    color = palette.ink3,
                    modifier = Modifier.padding(start = 12.dp, bottom = 3.dp),
                )
            }
        }

        // ── 操作行：检查 / 停止 + 进度与结果摘要 ──
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ActionPill(
                label = if (progress.running) {
                    DesktopI18n.t("停止检查", "Stop")
                } else {
                    DesktopI18n.t("检查更新", "Check updates")
                },
                enabled = progress.running || records.isNotEmpty(),
                accent = !progress.running,
                onClick = { if (progress.running) onCancelCheck() else onCheck() },
            )
            val note = when {
                progress.running ->
                    DesktopI18n.t(
                        "正在检查 ${progress.completed} / ${progress.total}${if (progress.currentSource.isBlank()) "" else " · ${progress.currentSource}"}",
                        "Checking ${progress.completed} / ${progress.total}${if (progress.currentSource.isBlank()) "" else " · ${progress.currentSource}"}",
                    )
                progress.summary.isNotBlank() -> progress.summary
                else -> ""
            }
            if (note.isNotBlank()) {
                Text(
                    text = note,
                    fontSize = 12.sp,
                    color = palette.ink3,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            DesktopPopUpButton(
                label = DesktopI18n.t("视图", "View"),
                value = if (viewMode == 1) DesktopI18n.t("双列", "2-Col") else DesktopI18n.t("网格", "Grid"),
                options = listOf(DesktopI18n.t("网格", "Grid"), DesktopI18n.t("双列", "2-Col")),
                selectedIndex = viewMode,
                onSelect = { viewMode = it },
            )
        }
        if (progress.notice.isNotBlank()) {
            Text(
                text = progress.notice,
                fontSize = 12.sp,
                color = palette.danger,
                maxLines = 2,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        // ── 筛选胶囊 ──
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            DesktopFollowFilter.entries.forEach { item ->
                val count = when (item) {
                    DesktopFollowFilter.ALL -> records.size
                    DesktopFollowFilter.UPDATED -> unread
                    else -> desktopFollowFiltered(records, item).size
                }
                FilterChip(
                    text = DesktopI18n.t("${item.labelCn}" + if (count > 0) " $count" else "", "${item.labelEn}" + if (count > 0) " $count" else ""),
                    selected = filter == item,
                    onClick = { filter = item },
                )
            }
        }

        // ── 清单 / 空态 ──
        when {
            records.isEmpty() -> EmptyFollowState(
                title = DesktopI18n.t("还没有追更的漫画", "No comics followed yet"),
                hint = DesktopI18n.t(
                    "在漫画详情页点「追更」，或在网站收藏页把当前夹加入追更。",
                    "Press Follow on a comic detail page, or import a network favorites folder.",
                ),
            )
            shown.isEmpty() -> EmptyFollowState(
                title = DesktopI18n.t("暂时没有新更新", "Nothing new right now"),
                hint = if (progress.summary.isNotBlank()) {
                    progress.summary
                } else {
                    DesktopI18n.t("可点「检查更新」再查一轮。", "Press Check updates to run another round.")
                },
            )
            else -> BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val columnsCount = if (viewMode == 1) {
                    cardLayout.resolveDoubleColumns(2)
                } else {
                    cardLayout.resolveGridColumns(this.maxWidth, 160.dp, 2, 6)
                }
                val coverAspect = cardLayout.resolveCoverAspect(0.75f)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columnsCount),
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = if (selectedSet.isNotEmpty()) 88.dp else 16.dp),
                ) {
                    itemsIndexed(shown, key = { _, it -> "${it.sourceName}|${it.comicId}" }) { index, record ->
                        val identity = "${record.sourceName}|${record.comicId}"
                        var appeared by remember(identity) { mutableStateOf(false) }
                        LaunchedEffect(identity) {
                            delay((index.coerceAtMost(14) * 32).toLong())
                            appeared = true
                        }
                        val enterAlpha by animateFloatAsState(
                            targetValue = if (appeared) 1f else 0f,
                            animationSpec = tween(durationMillis = 220),
                            label = "follow-enter-alpha",
                        )
                        val enterShift by animateFloatAsState(
                            targetValue = if (appeared) 0f else 16f,
                            animationSpec = tween(durationMillis = 260),
                            label = "follow-enter-shift",
                        )
                        Box(
                            modifier = Modifier.graphicsLayer {
                                alpha = enterAlpha
                                translationY = enterShift
                            },
                        ) {
                            FollowCard(
                                record = record,
                                doubleColumn = viewMode == 1,
                                coverAspect = coverAspect,
                                wide = mode != WideScreenLayoutMode.Compact,
                                masked = maskedOf(record),
                                checkRunning = progress.running,
                                selected = identity in selectedSet,
                                onToggleSelect = { toggleFollow(record) },
                                onOpen = { onOpen(record) },
                                onMarkRead = { onMarkRead(record) },
                                onSetStatus = { status -> onSetStatus(record, status) },
                                onRetry = onRetry?.let { retry -> { retry(record) } },
                                onRemove = { onRemove(record) },
                            )
                        }
                    }
                }
            }
        }
        }
        DesktopSelectionDock(
            selectedCount = selectedRecords.size,
            totalCount = shown.size,
            onSelectAll = {
                selectedKeys.clear()
                selectedKeys.addAll(shown.map { followKey(it) })
            },
            onInvertSelect = {
                val all = shown.map { followKey(it) }
                selectedKeys.clear()
                selectedKeys.addAll(all.filter { it !in selectedSet })
            },
            onClearSelect = { selectedKeys.clear() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = DesktopSelectionDockBottomInset),
        ) {
            val unreadTargets = selectedRecords.filter { it.hasNewUpdate }
            if (unreadTargets.isNotEmpty()) {
                DockActionPill(
                    text = DesktopI18n.t("标为已读 (${unreadTargets.size})", "Mark read (${unreadTargets.size})"),
                    primary = true,
                    onClick = {
                        selectedKeys.clear()
                        unreadTargets.forEach(onMarkRead)
                    },
                )
            }
            val activeTargets = selectedRecords.filter { it.status == ComicFollowStatus.ACTIVE }
            val retryAction = onBatchRetry ?: onRetry?.let { single -> { list: List<ComicFollowRecord> -> list.forEach(single) } }
            if (retryAction != null && activeTargets.isNotEmpty()) {
                DockActionPill(
                    text = DesktopI18n.t("重查 (${activeTargets.size})", "Recheck (${activeTargets.size})"),
                    enabled = !progress.running,
                    onClick = {
                        selectedKeys.clear()
                        retryAction(activeTargets)
                    },
                )
            }
            if (activeTargets.isNotEmpty()) {
                DockActionPill(
                    text = DesktopI18n.t("暂停", "Pause"),
                    onClick = {
                        selectedKeys.clear()
                        activeTargets.forEach { onSetStatus(it, ComicFollowStatus.PAUSED) }
                    },
                )
            }
            val pausedTargets = selectedRecords.filter { it.status == ComicFollowStatus.PAUSED }
            if (pausedTargets.isNotEmpty()) {
                DockActionPill(
                    text = DesktopI18n.t("恢复", "Resume"),
                    onClick = {
                        selectedKeys.clear()
                        pausedTargets.forEach { onSetStatus(it, ComicFollowStatus.ACTIVE) }
                    },
                )
            }
            val unfinishedTargets = selectedRecords.filter { it.status != ComicFollowStatus.COMPLETED }
            if (unfinishedTargets.isNotEmpty()) {
                DockActionPill(
                    text = DesktopI18n.t("标完结", "Finish"),
                    onClick = {
                        selectedKeys.clear()
                        unfinishedTargets.forEach { onSetStatus(it, ComicFollowStatus.COMPLETED) }
                    },
                )
            }
            DockActionPill(
                text = DesktopI18n.t("取消追更 (${selectedRecords.size})", "Unfollow (${selectedRecords.size})"),
                danger = true,
                onClick = {
                    val targets = selectedRecords.toList()
                    selectedKeys.clear()
                    targets.forEach(onRemove)
                },
            )
        }
    }
    }
}

@Composable
private fun EmptyFollowState(title: String, hint: String) {
    val palette = LocalChromePalette.current
    Column(
        modifier = Modifier.fillMaxSize().padding(top = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = title, fontSize = 15.sp, color = palette.ink2)
        Text(text = hint, fontSize = 12.sp, color = palette.ink3, maxLines = 3)
    }
}

@Composable
private fun ActionPill(
    label: String,
    enabled: Boolean,
    accent: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalChromePalette.current
    val ink = if (accent) palette.accent else palette.ink1
    Box(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (accent) palette.accent.copy(alpha = 0.12f) else palette.elevated)
            .border(0.5.dp, if (accent) palette.accent.copy(alpha = 0.4f) else palette.hairline, RoundedCornerShape(8.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, fontSize = 12.sp, color = if (enabled) ink else palette.ink3)
    }
}

@Composable
private fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    com.venera.desktop.ui.DesktopButton(
        text = text,
        onClick = onClick,
        size = com.venera.desktop.ui.DesktopButtonSize.Compact,
        variant = com.venera.desktop.ui.DesktopButtonVariant.Secondary,
        selected = selected,
        horizontalPadding = 11.dp,
    )
}

@Composable
private fun FollowCard(
    record: ComicFollowRecord,
    doubleColumn: Boolean,
    coverAspect: Float,
    wide: Boolean,
    masked: Boolean,
    checkRunning: Boolean,
    selected: Boolean,
    onToggleSelect: () -> Unit,
    onOpen: () -> Unit,
    onMarkRead: () -> Unit,
    onSetStatus: (ComicFollowStatus) -> Unit,
    onRetry: (() -> Unit)?,
    onRemove: () -> Unit,
) {
    val palette = LocalChromePalette.current
    val tokens = VeneraTokens
    val shape = RoundedCornerShape(12.dp)
    DesktopFloatingCard(
        cornerRadius = 12.dp,
        surface = palette.card,
        tray = false,
        chromeAspect = null,
        liftDp = 4.dp,
        hoverElevation = 10.dp,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (selected) Modifier.border(1.5.dp, palette.accent, shape) else Modifier)
            .clickable(onClick = onOpen),
    ) { floatState ->
        if (doubleColumn) {
            val coverWidth = if (wide) 82.dp else 68.dp
            val coverHeight = if (wide) 110.dp else 92.dp
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.card)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                FollowCoverBox(
                    record = record,
                    masked = masked,
                    hovered = floatState.isHovered,
                    selected = selected,
                    onToggleSelect = onToggleSelect,
                    compactSelect = true,
                    modifier = Modifier.width(coverWidth).height(coverHeight),
                    coverModifier = Modifier.fillMaxSize().desktopCoverFloat(floatState),
                    badgeModifier = Modifier.desktopUiFloat(floatState),
                    showSourceOnCover = false,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = record.title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.ink1,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (record.hasNewUpdate) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(palette.danger),
                            )
                        }
                    }
                    Text(
                        text = listOfNotNull(
                            record.author.takeIf { it.isNotBlank() },
                            record.sourceName.takeIf { it.isNotBlank() },
                            when (record.status) {
                                ComicFollowStatus.PAUSED -> DesktopI18n.tr("已暂停", "Paused")
                                ComicFollowStatus.COMPLETED -> DesktopI18n.tr("已完结", "Finished")
                                ComicFollowStatus.ACTIVE -> null
                            },
                        ).joinToString("  ·  "),
                        fontSize = 11.sp,
                        color = palette.ink3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                    Text(
                        text = desktopFollowStatusLine(record),
                        fontSize = 12.sp,
                        color = when {
                            record.lastError.isNotBlank() -> palette.danger
                            record.hasNewUpdate -> palette.accent
                            else -> palette.ink2
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 5.dp),
                    )
                    FollowActionsRow(
                        record = record,
                        checkRunning = checkRunning,
                        onOpen = onOpen,
                        onMarkRead = onMarkRead,
                        onSetStatus = onSetStatus,
                        onRetry = onRetry,
                        onRemove = onRemove,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.card)
                    .padding(7.dp),
            ) {
                FollowCoverBox(
                    record = record,
                    masked = masked,
                    hovered = floatState.isHovered,
                    selected = selected,
                    onToggleSelect = onToggleSelect,
                    compactSelect = false,
                    modifier = Modifier.fillMaxWidth().aspectRatio(coverAspect),
                    coverModifier = Modifier.fillMaxSize().desktopCoverFloat(floatState),
                    badgeModifier = Modifier.desktopUiFloat(floatState),
                    showSourceOnCover = true,
                )
                Text(
                    text = record.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.color.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    text = desktopFollowStatusLine(record),
                    fontSize = 11.sp,
                    color = when {
                        record.lastError.isNotBlank() -> palette.danger
                        record.hasNewUpdate -> palette.accent
                        else -> palette.ink2
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
                FollowActionsRow(
                    record = record,
                    checkRunning = checkRunning,
                    onOpen = onOpen,
                    onMarkRead = onMarkRead,
                    onSetStatus = onSetStatus,
                    onRetry = onRetry,
                    onRemove = onRemove,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun FollowCoverBox(
    record: ComicFollowRecord,
    masked: Boolean,
    hovered: Boolean,
    selected: Boolean,
    onToggleSelect: () -> Unit,
    compactSelect: Boolean,
    modifier: Modifier,
    coverModifier: Modifier,
    badgeModifier: Modifier,
    showSourceOnCover: Boolean,
) {
    val palette = LocalChromePalette.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(7.dp))
            .background(palette.segmentTrack),
    ) {
        VeneraCover(
            url = record.coverUrl,
            contentDescription = record.title,
            mask = if (masked) VeneraCoverMask.Masked else VeneraCoverMask.Visible,
            modifier = coverModifier,
        )
        // 书脊暗缝（与本地/网络书卡同一形）
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(6.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.22f),
                            Color.Black.copy(alpha = 0.25f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        if (record.hasNewUpdate) {
            Box(
                modifier = badgeModifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(palette.danger)
                    .padding(horizontal = 5.dp, vertical = 1.5.dp),
            ) {
                Text(
                    text = DesktopI18n.t("NEW", "NEW"),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                )
            }
        } else if (record.status != ComicFollowStatus.ACTIVE) {
            Box(
                modifier = badgeModifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.56f))
                    .padding(horizontal = 5.dp, vertical = 1.5.dp),
            ) {
                Text(
                    text = if (record.status == ComicFollowStatus.PAUSED) {
                        DesktopI18n.tr("已暂停", "Paused")
                    } else {
                        DesktopI18n.tr("已完结", "Finished")
                    },
                    fontSize = 9.sp,
                    color = Color.White,
                    maxLines = 1,
                )
            }
        }
        if (showSourceOnCover && record.sourceName.isNotBlank()) {
            Text(
                text = record.sourceName,
                fontSize = 10.sp,
                color = Color.White,
                maxLines = 1,
                modifier = badgeModifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(palette.glassDark, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 1.5.dp),
            )
        }
        if (selected || hovered) {
            DesktopCardSelectBadge(
                selected = selected,
                onToggle = onToggleSelect,
                description = if (selected) {
                    DesktopI18n.t("已选中 ${record.title}", "Selected ${record.title}")
                } else {
                    DesktopI18n.t("选择 ${record.title}", "Select ${record.title}")
                },
                size = if (compactSelect) 22.dp else 24.dp,
                modifier = badgeModifier
                    .align(Alignment.BottomStart)
                    .padding(if (compactSelect) 5.dp else 6.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FollowActionsRow(
    record: ComicFollowRecord,
    checkRunning: Boolean,
    onOpen: () -> Unit,
    onMarkRead: () -> Unit,
    onSetStatus: (ComicFollowStatus) -> Unit,
    onRetry: (() -> Unit)?,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        RowAction(DesktopI18n.t("打开", "Open"), onOpen)
        if (record.hasNewUpdate) {
            RowAction(DesktopI18n.t("标为已读", "Mark read"), onMarkRead)
        }
        if (onRetry != null && record.status == ComicFollowStatus.ACTIVE) {
            RowAction(
                DesktopI18n.t("单本重查", "Recheck"),
                onRetry,
                enabled = !checkRunning,
            )
        }
        when (record.status) {
            ComicFollowStatus.ACTIVE ->
                RowAction(DesktopI18n.t("暂停", "Pause"), { onSetStatus(ComicFollowStatus.PAUSED) })
            ComicFollowStatus.PAUSED ->
                RowAction(DesktopI18n.t("恢复", "Resume"), { onSetStatus(ComicFollowStatus.ACTIVE) })
            ComicFollowStatus.COMPLETED -> Unit
        }
        if (record.status != ComicFollowStatus.COMPLETED) {
            RowAction(DesktopI18n.t("标完结", "Finish"), { onSetStatus(ComicFollowStatus.COMPLETED) })
        }
        RowAction(DesktopI18n.t("取消追更", "Unfollow"), onRemove, danger = true)
    }
}

@Composable
private fun RowAction(text: String, onClick: () -> Unit, enabled: Boolean = true, danger: Boolean = false) {
    DesktopButton(
        text = text,
        onClick = onClick,
        size = DesktopButtonSize.Compact,
        variant = if (danger) DesktopButtonVariant.Danger else DesktopButtonVariant.Secondary,
        enabled = enabled,
        horizontalPadding = 8.dp,
    )
}
