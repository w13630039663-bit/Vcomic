package com.venera.desktop.sourcemanage

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.app.LocalShellBackHandled
import com.venera.desktop.gallery.ui.GalleryTokens
import com.venera.desktop.platform.DesktopSourceLatencyProbe
import com.venera.desktop.ui.DesktopBackButton
import com.venera.desktop.ui.DesktopButton
import com.venera.desktop.ui.DesktopButtonSize
import com.venera.desktop.ui.DesktopButtonVariant
import com.venera.desktop.ui.DesktopHomeSourceStatusRow
import com.venera.desktop.ui.DesktopPopUpButton
import com.venera.desktop.ui.DesktopSegmented
import com.venera.desktop.ui.DesktopSwitch
import com.venera.desktop.ui.components.DesktopGlassCard
import com.venera.desktop.ui.components.DesktopGlassCardLevel
import com.venera.desktop.ui.components.DesktopSearchField
import com.venera.desktop.ui.desktopHomeSourceState
import com.venera.desktop.ui.theme.VeneraType

/** 首页「源状态」卡片最多展示的行数（保证左卡高度不超过并齐平右侧「最近阅读」）。 */
internal const val HOME_SOURCE_STATUS_MAX_ROWS: Int = 4

/** 首页源状态各档汇总统计。 */
internal data class DesktopSourceStatusSummary(
    val total: Int,
    val ok: Int,
    val slow: Int,
    val bad: Int,
    val untested: Int,
)

/** 站点延迟检测列表项。 */
internal data class DesktopSourceLatencyItem(
    val key: String,
    val name: String,
    val host: String,
    val originLabel: String,
    val enabled: Boolean,
    val requiresProxy: Boolean,
    val probing: Boolean,
    val latencyMs: Long?,
    val failed: Boolean,
    val canOpenSiteSettings: Boolean,
) {
    fun asHomeStatusRow(): DesktopHomeSourceStatusRow = DesktopHomeSourceStatusRow(
        name = name,
        host = host,
        probing = probing,
        latencyMs = latencyMs,
        failed = failed,
    )
}

internal enum class LatencyFilter(val label: String) {
    ALL("全部"),
    OK("正常"),
    SLOW("偏慢"),
    BAD("异常"),
    UNTESTED("未测"),
}

internal enum class LatencySort(val label: String) {
    PRIORITY("异常 / 偏慢优先"),
    LAT_DESC("延迟从高到低"),
    LAT_ASC("延迟从低到高"),
    NAME("按名称排序"),
}

private fun statusSeverityRank(state: String): Int = when (state) {
    "测速中" -> 0
    "异常" -> 1
    "偏慢" -> 2
    "未测" -> 3
    else -> 4
}

/** 计算首页「源状态」各档分布。 */
internal fun summarizeSourceStatus(rows: List<DesktopHomeSourceStatusRow>): DesktopSourceStatusSummary {
    var ok = 0
    var slow = 0
    var bad = 0
    var untested = 0
    rows.forEach { row ->
        when (desktopHomeSourceState(row)) {
            "正常" -> ok++
            "偏慢" -> slow++
            "异常" -> bad++
            else -> untested++
        }
    }
    return DesktopSourceStatusSummary(rows.size, ok, slow, bad, untested)
}

/**
 * 为首页卡片精选最多 [maxRows] 条源状态（测速中 / 异常 / 偏慢置顶，其余保留顺序），
 * 返回 `(展示行, 收拢进延迟检测页的剩余条数)`。
 */
internal fun selectHomeCompactSourceRows(
    rows: List<DesktopHomeSourceStatusRow>,
    maxRows: Int = HOME_SOURCE_STATUS_MAX_ROWS,
): Pair<List<DesktopHomeSourceStatusRow>, Int> {
    if (rows.isEmpty() || maxRows <= 0) return emptyList<DesktopHomeSourceStatusRow>() to rows.size
    val picked = rows.withIndex()
        .sortedWith(compareBy({ statusSeverityRank(desktopHomeSourceState(it.value)) }, { it.index }))
        .take(maxRows)
        .map { it.value }
    val overflow = (rows.size - picked.size).coerceAtLeast(0)
    return picked to overflow
}

/** 按筛选、启用开关、关键词与排序规则整理延迟检测页列表。 */
internal fun filterAndSortLatencyItems(
    items: List<DesktopSourceLatencyItem>,
    filter: LatencyFilter,
    sort: LatencySort,
    includeDisabled: Boolean,
    query: String,
): List<DesktopSourceLatencyItem> {
    val q = query.trim()
    val filtered = items.filter { item ->
        if (!includeDisabled && !item.enabled) return@filter false
        val state = desktopHomeSourceState(item.asHomeStatusRow())
        val matchFilter = when (filter) {
            LatencyFilter.ALL -> true
            LatencyFilter.OK -> state == "正常"
            LatencyFilter.SLOW -> state == "偏慢"
            LatencyFilter.BAD -> state == "异常"
            LatencyFilter.UNTESTED -> state == "未测" || state == "测速中"
        }
        if (!matchFilter) return@filter false
        if (q.isBlank()) return@filter true
        item.name.contains(q, ignoreCase = true) ||
            item.key.contains(q, ignoreCase = true) ||
            item.host.contains(q, ignoreCase = true)
    }
    return when (sort) {
        LatencySort.PRIORITY -> filtered.sortedWith(
            compareBy<DesktopSourceLatencyItem> {
                statusSeverityRank(desktopHomeSourceState(it.asHomeStatusRow()))
            }.thenByDescending { it.latencyMs ?: -1L },
        )
        LatencySort.LAT_DESC -> filtered.sortedByDescending {
            when {
                it.failed -> Long.MAX_VALUE - 1
                it.latencyMs != null -> it.latencyMs
                else -> -1L
            }
        }
        LatencySort.LAT_ASC -> filtered.sortedBy {
            when {
                it.latencyMs != null -> it.latencyMs
                it.failed -> Long.MAX_VALUE - 1
                else -> Long.MAX_VALUE
            }
        }
        LatencySort.NAME -> filtered.sortedBy { it.name.lowercase() }
    }
}

/**
 * 独立「站点延迟检测」全页：集中查看所有站点主机探测结果、分档筛选与单站/全部重测。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DesktopSourceLatencyScreen(
    items: List<DesktopSourceLatencyItem>,
    onReprobeHosts: ((List<String>) -> Unit)?,
    onGoSourceManage: (() -> Unit)? = null,
    onOpenSiteSettings: ((String) -> Unit)? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    var filter by remember { mutableStateOf(LatencyFilter.ALL) }
    var sort by remember { mutableStateOf(LatencySort.PRIORITY) }
    var includeDisabled by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    val pool = remember(items, includeDisabled) {
        if (includeDisabled) items else items.filter { it.enabled }
    }
    val summary = remember(pool) {
        summarizeSourceStatus(pool.map { it.asHomeStatusRow() })
    }
    val avgMs = remember(pool) {
        val valid = pool.mapNotNull { it.latencyMs }
        if (valid.isEmpty()) null else valid.average().toLong()
    }
    val anyProbing = pool.any { it.probing }
    val filteredItems = remember(items, filter, sort, includeDisabled, query) {
        filterAndSortLatencyItems(items, filter, sort, includeDisabled, query)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp)
            .padding(top = 22.dp, bottom = 24.dp),
    ) {
        if (!LocalShellBackHandled.current) {
            DesktopBackButton(onClick = onBack)
            Spacer(Modifier.height(8.dp))
        }

        // 页头：标题 + 全部重测 / 站点管理
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "站点延迟检测",
                    fontSize = VeneraType.pageTitle.size,
                    lineHeight = VeneraType.pageTitle.lineHeight,
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.color.textPrimary,
                )
                Text(
                    text = "实时探测各漫画站与画廊主机延迟（自动沿用当前网络代理 · ≤${DesktopSourceLatencyProbe.GOOD_MS}ms 正常 · ≤${DesktopSourceLatencyProbe.SLOW_MS}ms 偏慢）",
                    fontSize = VeneraType.aux.size,
                    lineHeight = VeneraType.aux.lineHeight,
                    color = tokens.color.textTertiary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (onGoSourceManage != null) {
                DesktopButton(
                    text = "站点管理",
                    onClick = onGoSourceManage,
                    size = DesktopButtonSize.Medium,
                    variant = DesktopButtonVariant.Secondary,
                )
            }
            if (onReprobeHosts != null) {
                val hosts = pool.mapNotNull { it.host.takeIf(String::isNotBlank) }.distinct()
                DesktopButton(
                    text = if (anyProbing) "全量测速中…" else "全部重测",
                    onClick = { if (hosts.isNotEmpty()) onReprobeHosts(hosts) },
                    size = DesktopButtonSize.Medium,
                    variant = DesktopButtonVariant.Primary,
                    enabled = !anyProbing && hosts.isNotEmpty(),
                    loading = anyProbing,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // 四格 KPI 概览
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LatencyKpiCard(
                label = "监控站点",
                value = "${summary.total}",
                foot = if (avgMs != null) "平均延迟 $avgMs ms" else "等待探测结果",
                valueColor = tokens.color.textPrimary,
                modifier = Modifier.weight(1f),
            )
            LatencyKpiCard(
                label = "响应正常 (≤${DesktopSourceLatencyProbe.GOOD_MS}ms)",
                value = "${summary.ok}",
                foot = "占比 ${if (summary.total > 0) summary.ok * 100 / summary.total else 0}%",
                valueColor = GalleryTokens.Color.StatusSuccess,
                modifier = Modifier.weight(1f),
            )
            LatencyKpiCard(
                label = "连接偏慢 (≤${DesktopSourceLatencyProbe.SLOW_MS}ms)",
                value = "${summary.slow}",
                foot = "建议检查代理节点",
                valueColor = GalleryTokens.Color.ToneCaution,
                modifier = Modifier.weight(1f),
            )
            LatencyKpiCard(
                label = "异常 / 未通",
                value = "${summary.bad}",
                foot = "可重测或切站点线路",
                valueColor = GalleryTokens.Color.StatusDanger,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(12.dp))

        // 筛选与排序工具卡
        DesktopGlassCard(
            level = DesktopGlassCardLevel.Nested,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                DesktopSegmented(
                    options = listOf(
                        "全部 (${summary.total})",
                        "正常 (${summary.ok})",
                        "偏慢 (${summary.slow})",
                        "异常 (${summary.bad})",
                        "未测 (${summary.untested})",
                    ),
                    selectedIndex = filter.ordinal,
                    onSelect = { filter = LatencyFilter.entries[it] },
                )
                DesktopPopUpButton(
                    label = null,
                    value = sort.label,
                    options = LatencySort.entries.map { it.label },
                    selectedIndex = sort.ordinal,
                    onSelect = { sort = LatencySort.entries[it] },
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    DesktopSwitch(
                        checked = includeDisabled,
                        onCheckedChange = { includeDisabled = it },
                    )
                    Text(
                        text = "包含未启用站点",
                        fontSize = VeneraType.aux.size,
                        color = tokens.color.textSecondary,
                    )
                }
                Spacer(Modifier.weight(1f, fill = false))
                DesktopSearchField(
                    value = query,
                    onValueChange = { query = it },
                    onSearch = {},
                    collapsedWidth = 180.dp,
                    expandedWidth = 220.dp,
                    placeholder = "搜索站点或域名…",
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // 表头
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("站点与来源", fontSize = VeneraType.micro.size, color = tokens.color.textTertiary, modifier = Modifier.weight(2.1f))
            Text("探测主机域名", fontSize = VeneraType.micro.size, color = tokens.color.textTertiary, modifier = Modifier.weight(1.6f))
            Text("响应延迟", fontSize = VeneraType.micro.size, color = tokens.color.textTertiary, modifier = Modifier.weight(1.5f))
            Text("状态", fontSize = VeneraType.micro.size, color = tokens.color.textTertiary, modifier = Modifier.weight(0.8f))
            Text("操作", fontSize = VeneraType.micro.size, color = tokens.color.textTertiary, modifier = Modifier.weight(1.2f))
        }

        if (filteredItems.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (items.isEmpty()) "暂时没有可检测的站点。" else "当前筛选条件下没有匹配的站点。",
                    fontSize = VeneraType.body.size,
                    color = tokens.color.textTertiary,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                items(filteredItems, key = { "${it.key}:${it.host}" }) { item ->
                    LatencyRowCard(
                        item = item,
                        onReprobe = onReprobeHosts?.let { reprobe ->
                            if (item.host.isBlank()) null else { { reprobe(listOf(item.host)) } }
                        },
                        onOpenSettings = onOpenSiteSettings?.let { open ->
                            if (item.canOpenSiteSettings) { { open(item.key) } } else null
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LatencyKpiCard(
    label: String,
    value: String,
    foot: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    DesktopGlassCard(
        level = DesktopGlassCardLevel.Nested,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, fontSize = VeneraType.micro.size, color = tokens.color.textTertiary)
            Text(value, fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, color = valueColor)
            Text(foot, fontSize = VeneraType.micro.size, color = tokens.color.textSecondary)
        }
    }
}

@Composable
private fun LatencyRowCard(
    item: DesktopSourceLatencyItem,
    onReprobe: (() -> Unit)?,
    onOpenSettings: (() -> Unit)?,
) {
    val tokens = VeneraTokens
    val state = desktopHomeSourceState(item.asHomeStatusRow())
    val (pillFg, pillBg) = when (state) {
        "正常" -> GalleryTokens.Color.StatusSuccess to GalleryTokens.Color.StatusSuccess.copy(alpha = 0.16f)
        "偏慢" -> GalleryTokens.Color.ToneCaution to GalleryTokens.Color.ToneCaution.copy(alpha = 0.16f)
        "异常" -> GalleryTokens.Color.StatusDanger to GalleryTokens.Color.StatusDanger.copy(alpha = 0.16f)
        "测速中" -> tokens.color.primary to tokens.color.primary.copy(alpha = 0.15f)
        else -> tokens.color.textTertiary to tokens.color.outlineVariant.copy(alpha = 0.25f)
    }
    val barColor = when (state) {
        "正常" -> GalleryTokens.Color.StatusSuccess
        "偏慢" -> GalleryTokens.Color.ToneCaution
        "异常" -> GalleryTokens.Color.StatusDanger
        else -> tokens.color.textTertiary.copy(alpha = 0.4f)
    }
    val barFraction = when {
        item.failed -> 1f
        item.latencyMs != null -> (item.latencyMs / 1500f).coerceIn(0.08f, 1f)
        else -> 0f
    }
    val latText = when {
        item.probing -> "测速中…"
        item.failed -> "连接超时"
        item.latencyMs != null -> "${item.latencyMs} ms"
        item.host.isBlank() -> "无主机"
        else -> "未测"
    }

    DesktopGlassCard(
        level = DesktopGlassCardLevel.Nested,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.weight(2.1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SiteIconSlot(
                    key = item.key,
                    title = item.name,
                    size = 30.dp,
                    innerSize = 20.dp,
                    radius = 8.dp,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Text(
                            text = item.name,
                            fontSize = VeneraType.bodySecondary.size,
                            fontWeight = FontWeight.SemiBold,
                            color = tokens.color.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        RowMicroChip(item.originLabel)
                        if (item.requiresProxy) RowMicroChip("需代理")
                        if (!item.enabled) RowMicroChip("未启用")
                    }
                }
            }

            Text(
                text = item.host.ifBlank { "未声明域名" },
                fontFamily = FontFamily.Monospace,
                fontSize = 11.5.sp,
                color = if (item.host.isBlank()) tokens.color.textTertiary else tokens.color.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1.6f),
            )

            Row(
                modifier = Modifier.weight(1.5f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .width(88.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(tokens.color.outlineVariant.copy(alpha = 0.22f)),
                ) {
                    if (barFraction > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(barFraction)
                                .height(6.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(barColor),
                        )
                    }
                }
                Text(
                    text = latText,
                    fontSize = VeneraType.aux.size,
                    color = if (item.failed) GalleryTokens.Color.StatusDanger else tokens.color.textPrimary,
                    maxLines = 1,
                )
            }

            Box(modifier = Modifier.weight(0.8f), contentAlignment = Alignment.CenterStart) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(pillBg)
                        .padding(horizontal = 9.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (item.probing) {
                        val pulse = rememberInfiniteTransition(label = "latPulse")
                        val a by pulse.animateFloat(
                            initialValue = 0.3f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse),
                            label = "latPulseAlpha",
                        )
                        Box(Modifier.size(5.dp).clip(CircleShape).background(pillFg.copy(alpha = a)))
                    }
                    Text(state, fontSize = 11.sp, color = pillFg, fontWeight = FontWeight.Medium)
                }
            }

            Row(
                modifier = Modifier.weight(1.2f),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onReprobe != null) {
                    DesktopButton(
                        text = if (item.probing) "测速中" else "重测",
                        onClick = onReprobe,
                        size = DesktopButtonSize.Compact,
                        variant = DesktopButtonVariant.Secondary,
                        enabled = !item.probing,
                        loading = item.probing,
                    )
                }
                if (onOpenSettings != null) {
                    DesktopButton(
                        text = "设置 ›",
                        onClick = onOpenSettings,
                        size = DesktopButtonSize.Compact,
                        variant = DesktopButtonVariant.Secondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun RowMicroChip(text: String) {
    val tokens = VeneraTokens
    Box(
        modifier = Modifier
            .background(tokens.color.surfaceContainerHigh.copy(alpha = 0.65f), RoundedCornerShape(999.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(text = text, fontSize = 10.sp, lineHeight = 12.sp, color = tokens.color.textTertiary)
    }
}
