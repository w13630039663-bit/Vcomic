package com.venera.desktop.ui

import com.venera.desktop.app.DesktopRoute
import com.venera.desktop.app.LocalShellBackHandled
import com.venera.desktop.data.DESKTOP_READING_STATS_ACTIVE_WINDOW
import com.venera.desktop.data.DESKTOP_READING_STATS_EMPTY_MESSAGE
import com.venera.desktop.data.DESKTOP_READING_STATS_EMPTY_TITLE
import com.venera.desktop.data.DESKTOP_READING_STATS_HEAT_DAYS
import com.venera.desktop.data.DESKTOP_READING_STATS_SCREEN_TITLE
import com.venera.desktop.data.DesktopReadingStats
import com.venera.desktop.data.ReadingStatsRecentRow
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.components.venera.VeneraCoverMask
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.ui.DesktopBackButton
import com.venera.desktop.settings.DesktopI18n
import com.venera.desktop.ui.theme.VeneraType
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 桌面「阅读统计」屏 · mac persona 自搭版（定稿，「不要共享屏」）。
 *
 * 不吃 `DesktopPageShell`：工具栏、页头（mac 大字 + 时段/星期 clock 件）全在本屏自画，
 * DesktopRoute 直挂。配色走 persona 定稿 v2（`docs/designs/history-source-filter-2026-11-09/`）：
 * accent #0A63F6（深面 #4C8DFF）、发丝线、6% 白面——本屏不引入正本之外的色。
 * 布局九区见 `docs/designs/stats-rework-2026-11-14/ui-mockup-macos.html`。
 *
 * 数据与判读全在数据件那侧（[DesktopReadingStats] 与纯件），本屏只搬成品，零 IO。
 *
 * ## 三档分别（与历史屏同一条纪律）
 *
 *  ① [DesktopReadingStats.note] 非空 ⇒ 读失败，念成因，**不改写成空态**；
 *  ② `!loaded` ⇒ 还没读回来，念「正在读…」；
 *  ③ loaded 而趋势全 0 且榜与记录都空 ⇒ 确实没记录 ⇒ 空态两句（KPI 的 0 是真话，摆）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DesktopReadingStatsScreen(
    mode: WideScreenLayoutMode,
    stats: DesktopReadingStats,
    /** 回底栏那一格。与历史/队列/源管理同一口径：二级页必须有出路，且必传、无默认值。 */
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    LazyColumn(
        // 不刷自己的页底，继承窗底（与历史页同底，无内嵌面板观感）。
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 30.dp, end = 30.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 页头：返回 + 页名 + mac 大字 + 时段/星期 clock 件（一屏一返回判据沿用壳那颗）。
        item(key = "head") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!LocalShellBackHandled.current) {
                    DesktopBackButton(onClick = onBack)
                }
                Text(
                    text = DesktopI18n.t(DESKTOP_READING_STATS_SCREEN_TITLE, "Reading Stats"),
                    fontSize = 13.sp, lineHeight = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.color.textPrimary,
                )
            }
        }
        item(key = "hero") {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column {
                    Text(
                        text = DesktopI18n.t(DESKTOP_READING_STATS_SCREEN_TITLE, "Reading Stats"),
                        fontSize = 30.sp, lineHeight = 38.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = tokens.color.textPrimary,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = if (!stats.loaded && stats.note == null) {
                            DesktopI18n.t("正在读本地阅读记录…", "Loading local reading history…")
                        } else {
                            DesktopI18n.t("回顾阅读，发现兴趣与习惯", "Review your reading habits & interests")
                        },
                        fontSize = 12.sp, lineHeight = 16.sp,
                        color = tokens.color.textTertiary,
                    )
                }
                Spacer(Modifier.weight(1f))
                HeadClock(
                    label = DesktopI18n.t("最活跃时段", "Peak Hours"),
                    value = stats.habit.topHourStart?.let { "%02d:00 – %02d:00".format(it, it + 2) } ?: DesktopI18n.t("暂无数据", "No data"),
                )
                HeadClock(
                    label = DesktopI18n.t("最活跃星期", "Peak Day"),
                    value = habitDowLabel(stats.habit.topDow),
                )
            }
        }

        // ① 读失败：只念成因，绝不摆 KPI/图表（摆出来是假账）。
        if (stats.note != null) {
            item(key = "note") {
                Text(
                    text = stats.note,
                    fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                    color = tokens.color.textTertiary,
                )
            }
            return@LazyColumn
        }
        // ② 还没读回来：页头副题已念「正在读…」，不改口成空态。
        if (!stats.loaded) return@LazyColumn

        item(key = "kpi") { KpiRow(stats, mode) }

        val noRecords = stats.trend.all { it.pages == 0 } &&
            stats.topComics.isEmpty() && stats.recent.isEmpty()
        if (noRecords) {
            item(key = "empty") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
                ) {
                    Text(
                        text = DesktopI18n.t(DESKTOP_READING_STATS_EMPTY_TITLE, "No reading records yet"),
                        fontSize = VeneraType.sectionTitle.size, lineHeight = VeneraType.sectionTitle.lineHeight,
                        color = tokens.color.textPrimary,
                    )
                    Text(
                        text = DesktopI18n.t(DESKTOP_READING_STATS_EMPTY_MESSAGE, "Start reading some comics to see your reading stats here."),
                        fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                        color = tokens.color.textSecondary,
                    )
                }
            }
            return@LazyColumn
        }

        item(key = "heat") { HeatCard(stats) }
        item(key = "insight") { InsightRow(stats, mode) }
        item(key = "topics") { TopicsRow(stats, mode) }
        if (stats.recent.isNotEmpty()) {
            item(key = "recent") { RecentTableCard(stats.recent) }
        }
    }
}

// ==================== persona 色与通用件 ====================

/** persona 正本 accent：深面用提亮档（本屏唯一允许的色值来源）。 */
private val AccentLift = Color(0xFF4C8DFF)
private val Accent = Color(0xFF0A63F6)
private val RankGold = Color(0xFFF5C242)
private val RankSilver = Color(0xFFB9C2CF)
private val RankBronze = Color(0xFFC98B5E)
private val DonutSeq = listOf(AccentLift, Color(0xFF8B7CF6), Color(0xFFFF7A5C), Color(0xFF3ED6C4), RankGold, Color(0xFF3ED6C4))

/** persona 面件：6% 白面 + 发丝线 r14（深浅主题下都成立）。 */
@Composable
private fun MacCard(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val tokens = VeneraTokens
    Column(
        // 形参必须接上（weight 全靠它）；卡面 6% 白底按意见恢复。
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(tokens.color.textPrimary.copy(alpha = 0.06f))
            .border(0.5.dp, tokens.color.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(18.dp),
        content = content,
    )
}

@Composable
private fun CardHead(title: String, sub: String?, trailing: (@Composable () -> Unit)? = null) {
    val tokens = VeneraTokens
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            fontSize = 14.sp, lineHeight = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = tokens.color.textPrimary,
        )
        if (sub != null) {
            Spacer(Modifier.width(10.dp))
            Text(
                text = sub,
                fontSize = 11.sp, lineHeight = 14.sp,
                color = tokens.color.textTertiary,
            )
        }
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
private fun HeadClock(label: String, value: String) {
    val tokens = VeneraTokens
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(tokens.color.textPrimary.copy(alpha = 0.06f))
            .border(0.5.dp, tokens.color.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column {
            Text(
                text = label,
                fontSize = 11.sp, lineHeight = 14.sp,
                color = tokens.color.textTertiary,
            )
            Text(
                text = value,
                fontSize = 15.sp, lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = tokens.color.textPrimary,
            )
        }
    }
}

@Composable
private fun habitDowLabel(topDow: Int?): String {
    val isEn = com.venera.desktop.settings.LocalDesktopLanguage.current == com.venera.desktop.settings.DesktopLanguage.EN
    return topDow?.let {
        if (isEn) listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")[it - 1]
        else "星期" + listOf("一", "二", "三", "四", "五", "六", "日")[it - 1]
    } ?: DesktopI18n.t("暂无数据", "No data")
}

// ==================== KPI 四格 ====================

@Composable
private fun KpiRow(stats: DesktopReadingStats, mode: WideScreenLayoutMode) {
    if (mode == WideScreenLayoutMode.Compact) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TotalTimeKpi(stats, Modifier.weight(1f))
                PagesKpi(stats, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ActiveDaysKpi(stats, Modifier.weight(1f))
                ComicsKpi(stats, Modifier.weight(1f))
            }
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            TotalTimeKpi(stats, Modifier.weight(1.2f))
            PagesKpi(stats, Modifier.weight(1f))
            ActiveDaysKpi(stats, Modifier.weight(1f))
            ComicsKpi(stats, Modifier.weight(1f))
        }
    }
}

/** 环比徽标：pct 为 null 不摆（上期为 0 时摆百分比是假账）。 */
@Composable
private fun DeltaBadge(pct: Double?, suffix: String) {
    if (pct == null) return
    val arrow = if (pct >= 0) "▲" else "▼"
    val tokens = VeneraTokens
    Text(
        text = "较上个 30 天 $arrow ${kotlin.math.abs(pct).let { (it * 10).roundToInt() / 10.0 }}%$suffix",
        fontSize = 11.sp, lineHeight = 14.sp,
        color = if (pct >= 0) AccentLift else tokens.color.textTertiary,
    )
}

@Composable
private fun KpiScaffold(
    label: String,
    value: @Composable () -> Unit,
    delta: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: (@Composable () -> Unit)? = null,
) {
    val tokens = VeneraTokens
    Box(modifier = modifier) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = label,
                fontSize = 12.sp, lineHeight = 15.sp,
                color = tokens.color.textSecondary,
            )
            Spacer(Modifier.height(3.dp))
            value()
            Spacer(Modifier.height(2.dp))
            delta?.invoke()
            content?.let {
                Spacer(Modifier.height(10.dp))
                it()
            }
        }
        Box(modifier = Modifier.align(Alignment.TopEnd)) { trailing?.invoke() }
    }
}

@Composable
private fun MacCardModifier(modifier: Modifier = Modifier): Modifier {
    val tokens = VeneraTokens
    return modifier
        .clip(RoundedCornerShape(12.dp))
        .background(tokens.color.textPrimary.copy(alpha = 0.06f))
        .border(0.5.dp, tokens.color.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
}

@Composable
private fun TotalTimeKpi(stats: DesktopReadingStats, modifier: Modifier = Modifier) {
    val hours = stats.totalSeconds / 3600
    val mins = (stats.totalSeconds % 3600) / 60
    val tenths = (mins / 6.0).roundToInt().coerceAtMost(9)
    val timeStr = if (tenths == 0) "$hours h" else "$hours.$tenths h"
    KpiScaffold(
        label = "累计阅读长",
        modifier = MacCardModifier(modifier),
        delta = { DeltaBadge(stats.delta.secondsPct, "") },
        value = {
            // persona 渐变字：白→淡蓝（大字日期同款）。
            Text(
                text = timeStr,
                style = TextStyle(
                    brush = Brush.horizontalGradient(listOf(Color(0xFFF5F5F7), Color(0xFF8FB1FF))),
                    fontSize = 30.sp, lineHeight = 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                ),
            )
        },
        content = {
            MiniTrendLine(
                values = stats.trend.map { it.seconds / 3600.0 },
                modifier = Modifier.fillMaxWidth().height(28.dp),
            )
        },
    )
}

@Composable
private fun PagesKpi(stats: DesktopReadingStats, modifier: Modifier = Modifier) {
    val tokens = VeneraTokens
    KpiScaffold(
        label = "阅读总量",
        modifier = MacCardModifier(modifier),
        delta = { DeltaBadge(stats.delta.pagesPct, "") },
        value = {
            Text(
                text = "${stats.totalPages}",
                fontSize = 30.sp, lineHeight = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                color = tokens.color.textPrimary,
            )
        },
    )
}

@Composable
private fun ActiveDaysKpi(stats: DesktopReadingStats, modifier: Modifier = Modifier) {
    val tokens = VeneraTokens
    KpiScaffold(
        label = "活跃天数",
        modifier = MacCardModifier(modifier),
        value = {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "${stats.activeDays}",
                    fontSize = 30.sp, lineHeight = 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = tokens.color.textPrimary,
                )
                Text(
                    text = " 天 / ${DESKTOP_READING_STATS_ACTIVE_WINDOW} 天",
                    fontSize = 11.sp, lineHeight = 14.sp,
                    color = tokens.color.textTertiary,
                    modifier = Modifier.padding(start = 2.dp, bottom = 3.dp),
                )
            }
        },
        trailing = {
            ProgressRing(
                progress = stats.activeDays.toFloat() / DESKTOP_READING_STATS_ACTIVE_WINDOW,
                modifier = Modifier
                    .padding(top = 14.dp, end = 16.dp)
                    .size(46.dp),
            )
        },
    )
}

@Composable
private fun ComicsKpi(stats: DesktopReadingStats, modifier: Modifier = Modifier) {
    val tokens = VeneraTokens
    KpiScaffold(
        label = "阅读作品",
        modifier = MacCardModifier(modifier),
        delta = { DeltaBadge(stats.delta.comicsDelta?.toDouble()?.takeIf { it != 0.0 }, " 部") },
        value = {
            Text(
                text = "${stats.totalComics}",
                fontSize = 30.sp, lineHeight = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                color = tokens.color.textPrimary,
            )
        },
    )
}

@Composable
private fun ProgressRing(progress: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 4.dp.toPx()
        val inset = stroke / 2
        val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
        drawArc(
            color = Color.White.copy(alpha = 0.10f), startAngle = 0f, sweepAngle = 360f, useCenter = false,
            topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke),
        )
        drawArc(
            color = AccentLift, startAngle = -90f,
            sweepAngle = 360f * progress.coerceIn(0f, 1f), useCenter = false,
            topLeft = Offset(inset, inset), size = arcSize,
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
    }
}

@Composable
private fun MiniTrendLine(values: List<Double>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        if (values.size < 2) return@Canvas
        val max = (values.maxOrNull() ?: 0.0).coerceAtLeast(0.5)
        val stepX = size.width / (values.size - 1)
        fun y(v: Double) = size.height - 2f - ((v / max) * (size.height - 4f)).toFloat()
        val pts = values.mapIndexed { i, v -> Offset(i * stepX, y(v)) }
        val area = Path().apply {
            moveTo(pts.first().x, size.height)
            pts.forEach { lineTo(it.x, it.y) }
            lineTo(pts.last().x, size.height)
            close()
        }
        drawPath(area, Accent.copy(alpha = 0.18f))
        val line = Path().apply {
            moveTo(pts.first().x, pts.first().y)
            pts.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(line, AccentLift, style = Stroke(2f, cap = StrokeCap.Round))
    }
}

// ==================== 热力条 ====================

/** 热力 5 档：0 / 1-19 / 20-59 / 60-119 / ≥120（与一致）。 */
private fun heatLevel(pages: Int): Float = when {
    pages <= 0 -> 0f
    pages < 20 -> 0.25f
    pages < 60 -> 0.5f
    pages < 120 -> 0.75f
    else -> 1f
}

@Composable
private fun HeatCard(stats: DesktopReadingStats) {
    val tokens = VeneraTokens
    MacCard {
        CardHead("阅读活跃度", "近一年 · 每日阅读情况") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = "少",
                    fontSize = 11.sp, lineHeight = 14.sp,
                    color = tokens.color.textTertiary,
                )
                listOf(0.25f, 0.5f, 0.75f).forEach { a ->
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .background(AccentLift.copy(alpha = a), RoundedCornerShape(2.5.dp)),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .background(AccentLift, RoundedCornerShape(2.5.dp)),
                )
                Text(
                    text = "多",
                    fontSize = 11.sp, lineHeight = 14.sp,
                    color = tokens.color.textTertiary,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(
                modifier = Modifier.width(18.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                    Text(
                        text = it,
                        fontSize = 9.sp, lineHeight = 11.sp,
                        color = tokens.color.textTertiary,
                    )
                }
            }
            // 7 行（星期）× 53 列（周），格尺寸按卡宽均分（Canvas 一次画完）。
            Canvas(modifier = Modifier.weight(1f).height(130.dp)) {
                val pages = stats.heatPages
                val weeks = (pages.size + 6) / 7
                val gap = 3.dp.toPx()
                val cellW = (size.width - (weeks - 1) * gap) / weeks
                val cellH = (size.height - 6 * gap) / 7
                val cell = min(cellW, cellH)
                for (w in 0 until weeks) {
                    for (d in 0 until 7) {
                        val p = pages.getOrNull(w * 7 + d) ?: -1
                        if (p < 0) continue
                        val left = w * (cell + gap)
                        val top = d * (cell + gap)
                        drawRoundRect(
                            color = if (p == 0) Color.White.copy(alpha = 0.06f)
                            else AccentLift.copy(alpha = heatLevel(p)),
                            topLeft = Offset(left, top),
                            size = androidx.compose.ui.geometry.Size(cell, cell),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.5.dp.toPx()),
                        )
                        if (heatLevel(p) == 1f) {
                            drawRoundRect(
                                color = AccentLift.copy(alpha = 0.35f),
                                topLeft = Offset(left - 2f, top - 2f),
                                size = androidx.compose.ui.geometry.Size(cell + 4f, cell + 4f),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        // 月份标签与网格**同几何**：x = 该月首日所在周的列起点。从前是 weight×12 均分整行，
        // 标签与 53 列网格根本没对上（数据簇看起来全落在错误的月份底下）。
        val labelMeasurer = rememberTextMeasurer()
        val labelStyle = TextStyle(fontSize = 9.sp, lineHeight = 11.sp, color = tokens.color.textTertiary)
        val heatToday = remember { java.time.LocalDate.now() }
        val monthStarts = remember(heatToday) {
            buildList {
                for (off in 0 until DESKTOP_READING_STATS_HEAT_DAYS) {
                    val d = heatToday.minusDays((DESKTOP_READING_STATS_HEAT_DAYS - 1 - off).toLong())
                    if (d.dayOfMonth == 1) add(off to d.monthValue)
                }
            }
        }
        Canvas(modifier = Modifier.padding(start = 24.dp).fillMaxWidth().height(14.dp)) {
            val gap = 3.dp.toPx()
            val weeks = (DESKTOP_READING_STATS_HEAT_DAYS + 6) / 7
            // 与上面网格同一套公式取格宽（含 min(cellW, cellH) 那一刀），标签才真的落在列上。
            val cellW = (size.width - (weeks - 1) * gap) / weeks
            val cellH = (130.dp.toPx() - 6 * gap) / 7
            val cell = min(cellW, cellH)
            var lastX = -60f
            for ((off, month) in monthStarts) {
                val x = (off / 7) * (cell + gap)
                if (x - lastX < 30.dp.toPx()) continue
                lastX = x
                drawText(
                    textMeasurer = labelMeasurer,
                    text = "$month 月",
                    topLeft = Offset(x, 0f),
                    style = labelStyle,
                )
            }
        }
    }
}

// ==================== 趋势 / 习惯 ====================

@Composable
private fun InsightRow(stats: DesktopReadingStats, mode: WideScreenLayoutMode) {
    if (mode == WideScreenLayoutMode.Compact) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            TrendCard(stats, Modifier.fillMaxWidth())
            HabitCard(stats, Modifier.fillMaxWidth())
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            TrendCard(stats, Modifier.weight(1.5f))
            HabitCard(stats, Modifier.weight(1f))
        }
    }
}

@Composable
private fun TrendCard(stats: DesktopReadingStats, modifier: Modifier = Modifier) {
    val tokens = VeneraTokens
    var series by remember { mutableIntStateOf(0) } // 0=时长 1=页数 2=作品
    val values = when (series) {
        0 -> stats.trend.map { it.seconds / 3600.0 }
        1 -> stats.trend.map { it.pages.toDouble() }
        else -> stats.trend.map { it.works.toDouble() }
    }
    val unit = if (series == 0) " h" else if (series == 1) " 页" else " 本"
    val peakIdx = values.indices.maxByOrNull { values[it] } ?: 0
    val peakText = (values.getOrNull(peakIdx) ?: 0.0).let {
        if (series == 0) "%.1f".format(it) + unit else "${it.roundToInt()}$unit"
    } + " · " + (stats.trend.getOrNull(peakIdx)?.label ?: "")
    MacCard(modifier = modifier) {
        CardHead("阅读趋势", null) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                listOf("阅读时长", "阅读页数", "作品数量").forEachIndexed { i, name ->
                    val selected = series == i
                    Text(
                        text = name,
                        fontSize = 12.sp, lineHeight = 15.sp,
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                        color = if (selected) Color.White else tokens.color.textTertiary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (selected) Brush.horizontalGradient(listOf(Color(0xFF3E7BFF), Accent))
                                else androidx.compose.ui.graphics.SolidColor(tokens.color.textPrimary.copy(alpha = 0.05f))
                            )
                            .clickable { series = i }
                            .padding(horizontal = 11.dp, vertical = 4.dp),
                    )
                }
            }
        }
        TrendCanvas(
            values = values,
            modifier = Modifier.fillMaxWidth().height(170.dp),
        )
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stats.trend.firstOrNull()?.label ?: "",
                fontSize = 11.sp, lineHeight = 14.sp,
                color = tokens.color.textTertiary,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "峰值 $peakText",
                fontSize = 11.sp, lineHeight = 14.sp,
                color = tokens.color.textSecondary,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = stats.trend.lastOrNull()?.label ?: "",
                fontSize = 11.sp, lineHeight = 14.sp,
                color = tokens.color.textTertiary,
            )
        }
    }
}

/** 平滑面积折线（中点二次贝塞尔），accent 渐变面积 + 提亮折线。 */
@Composable
private fun TrendCanvas(values: List<Double>, modifier: Modifier = Modifier) {
    // 颜色在组合期取好（tokens.color 是 Composable getter，绘制 lambda 里取不了）。
    val lineColor = VeneraTokens.color.outlineVariant
    Canvas(modifier = modifier) {
        if (values.size < 2) {
            drawLine(lineColor, Offset(0f, size.height - 2), Offset(size.width, size.height - 2), 2f)
            return@Canvas
        }
        val max = (values.maxOrNull() ?: 0.0).coerceAtLeast(0.5)
        val padL = 4f
        val stepX = (size.width - padL * 2) / (values.size - 1)
        fun pt(i: Int) = Offset(
            padL + i * stepX,
            size.height - 6f - ((values[i] / max) * (size.height - 16f)).toFloat(),
        )
        val pts = values.indices.map(::pt)
        val line = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) {
                val mid = Offset((pts[i - 1].x + pts[i].x) / 2, (pts[i - 1].y + pts[i].y) / 2)
                quadraticBezierTo(pts[i - 1].x, pts[i - 1].y, mid.x, mid.y)
            }
            lineTo(pts.last().x, pts.last().y)
        }
        val area = Path().apply {
            addPath(line)
            lineTo(pts.last().x, size.height)
            lineTo(pts.first().x, size.height)
            close()
        }
        drawPath(area, AccentLift.copy(alpha = 0.20f))
        drawPath(line, AccentLift, style = Stroke(4f, cap = StrokeCap.Round))
        val peak = values.indices.maxByOrNull { values[it] } ?: 0
        drawCircle(AccentLift, radius = 6f, center = pts[peak])
    }
}

@Composable
private fun HabitCard(stats: DesktopReadingStats, modifier: Modifier = Modifier) {
    val tokens = VeneraTokens
    MacCard(modifier = modifier) {
        CardHead("阅读习惯", null)
        HabitLine("连续阅读", "${stats.streakDays} 天", tokens) {
            MiniBars(stats.habit.dowDist, tokens)
        }
        HabitLine("本周页数", "${stats.weekPages} 页", tokens)
        HabitLine("今日页数", "${stats.todayPages} 页", tokens)
    }
}

@Composable
private fun HabitLine(label: String, value: String, tokens: VeneraTokens, trailing: (@Composable () -> Unit)? = null) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontSize = 11.sp, lineHeight = 14.sp,
                    color = tokens.color.textTertiary,
                )
                Text(
                    text = value,
                    fontSize = 15.sp, lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.color.textPrimary,
                )
            }
            trailing?.invoke()
        }
        Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(tokens.color.outlineVariant))
    }
}

@Composable
private fun MiniBars(dist: List<Int>, tokens: VeneraTokens) {
    val max = (dist.maxOrNull() ?: 0).coerceAtLeast(1)
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.5.dp)) {
        dist.forEach { v ->
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height((4 + v.toFloat() / max * 18).dp)
                    .background(
                        if (v == dist.maxOrNull()) AccentLift else AccentLift.copy(alpha = 0.35f),
                        RoundedCornerShape(2.dp),
                    ),
            )
        }
    }
}

// ==================== 题材 / 标签 / 排行榜 ====================

@Composable
private fun TopicsRow(stats: DesktopReadingStats, mode: WideScreenLayoutMode) {
    if (mode == WideScreenLayoutMode.Compact) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            TopicDonutCard(stats, Modifier.fillMaxWidth())
            TagCloudCard(stats, Modifier.fillMaxWidth())
            RankBoardCard(stats, Modifier.fillMaxWidth())
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            TopicDonutCard(stats, Modifier.weight(1.1f))
            TagCloudCard(stats, Modifier.weight(0.9f))
            RankBoardCard(stats, Modifier.weight(1.3f))
        }
    }
}

@Composable
private fun TopicDonutCard(stats: DesktopReadingStats, modifier: Modifier = Modifier) {
    val tokens = VeneraTokens
    val buckets = stats.tagStats.buckets.take(6)
    MacCard(modifier = modifier) {
        CardHead("题材偏好", "近 1 年 · ${stats.tagStats.taggedComics} 本")
        if (buckets.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "暂无可归类题材：多数源在列表里只给分类词，不带命名空间的标签不参与统计。",
                fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                color = tokens.color.textSecondary,
            )
            return@MacCard
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(112.dp)) {
                DonutCanvas(
                    counts = buckets.map { it.comics },
                    modifier = Modifier.fillMaxSize(),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${stats.tagStats.taggedComics} 本",
                        fontSize = 19.sp, lineHeight = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = tokens.color.textPrimary,
                    )
                    Text(
                        text = "总计",
                        fontSize = 10.sp, lineHeight = 13.sp,
                        color = tokens.color.textTertiary,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                val total = stats.tagStats.taggedComics.coerceAtLeast(1)
                buckets.forEachIndexed { i, b ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(DonutSeq[i % DonutSeq.size], RoundedCornerShape(2.5.dp)),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = b.display,
                            fontSize = 12.sp, lineHeight = 15.sp,
                            color = tokens.color.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${(b.comics.toFloat() / total * 100).roundToInt()}%",
                            fontSize = 12.sp, lineHeight = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = tokens.color.textSecondary,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                        Text(
                            text = "${b.comics} 本",
                            fontSize = 11.sp, lineHeight = 14.sp,
                            color = tokens.color.textTertiary,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DonutCanvas(counts: List<Int>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 13.dp.toPx()
        val inset = stroke / 2 + 1f
        val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke - 2, size.height - stroke - 2)
        drawArc(
            color = Color.White.copy(alpha = 0.10f), startAngle = 0f, sweepAngle = 360f, useCenter = false,
            topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke),
        )
        val rowSum = counts.sum().coerceAtLeast(1)
        var start = -90f
        counts.forEachIndexed { i, c ->
            val sweep = c.toFloat() / rowSum * 360f
            drawArc(
                color = DonutSeq[i % DonutSeq.size], startAngle = start, sweepAngle = sweep, useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Butt),
            )
            start += sweep
        }
    }
}

@Composable
private fun TagCloudCard(stats: DesktopReadingStats, modifier: Modifier = Modifier) {
    val tokens = VeneraTokens
    val buckets = stats.tagStats.buckets.take(12)
    val total = stats.tagStats.taggedComics.coerceAtLeast(1)
    MacCard(modifier = modifier) {
        CardHead("标签偏好", null)
        Spacer(Modifier.height(10.dp))
        if (buckets.isEmpty()) {
            Text(
                text = "暂无标签数据",
                fontSize = VeneraType.body.size, lineHeight = VeneraType.body.lineHeight,
                color = tokens.color.textSecondary,
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                buckets.forEachIndexed { i, b ->
                    val pct = (b.comics.toFloat() / total * 100).roundToInt()
                    val solid = i < 3
                    val base = if (solid) Modifier.background(
                        Brush.horizontalGradient(listOf(Color(0xFF3E7BFF), Accent)),
                        RoundedCornerShape(8.dp),
                    ) else Modifier.background(tokens.color.textPrimary.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = base
                            .border(
                                if (solid) 0.dp else 0.5.dp,
                                if (solid) Accent else tokens.color.outlineVariant,
                                RoundedCornerShape(8.dp),
                            )
                            .padding(horizontal = 13.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = "#${b.display}",
                            fontSize = 12.5.sp, lineHeight = 15.sp,
                            fontWeight = if (solid) FontWeight.Medium else FontWeight.Normal,
                            color = if (solid) Color.White else tokens.color.textPrimary,
                        )
                        Text(
                            text = " $pct%",
                            fontSize = 10.5.sp, lineHeight = 13.sp,
                            color = if (solid) Color.White.copy(alpha = 0.85f) else tokens.color.textTertiary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RankBoardCard(stats: DesktopReadingStats, modifier: Modifier = Modifier) {
    val tokens = VeneraTokens
    val rows = stats.topComics.take(3)
    MacCard(modifier = modifier) {
        CardHead("阅读排行榜", "按阅读时长")
        Spacer(Modifier.height(8.dp))
        if (rows.isEmpty()) {
            Text(
                text = "暂无排行数据",
                fontSize = VeneraType.body.size, lineHeight = VeneraType.body.lineHeight,
                color = tokens.color.textSecondary,
            )
        } else {
            val maxSeconds = rows.maxOf { it.seconds }.coerceAtLeast(1)
            rows.forEachIndexed { i, comic ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "${i + 1}",
                        fontSize = 14.sp, lineHeight = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (i) {
                            0 -> RankGold
                            1 -> RankSilver
                            else -> RankBronze
                        },
                        modifier = Modifier.width(16.dp),
                    )
                    CoverView(comic.coverUrl, comic.title, 40.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = comic.title,
                            fontSize = 12.5.sp, lineHeight = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = tokens.color.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(DonutSeq[i % DonutSeq.size], CircleShape),
                            )
                            Text(
                                text = comic.sourceName,
                                fontSize = 10.5.sp, lineHeight = 13.sp,
                                color = tokens.color.textTertiary,
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(3.dp)
                                    .background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(999.dp)),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth((comic.seconds.toFloat() / maxSeconds).coerceIn(0.02f, 1f))
                                        .height(3.dp)
                                        .background(
                                            Brush.horizontalGradient(listOf(AccentLift, Accent)),
                                            RoundedCornerShape(999.dp),
                                        ),
                                )
                            }
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = comic.durationNote,
                            fontSize = 11.5.sp, lineHeight = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = tokens.color.textPrimary,
                        )
                        Text(
                            text = "${comic.pages} 页",
                            fontSize = 10.5.sp, lineHeight = 13.sp,
                            color = tokens.color.textTertiary,
                        )
                    }
                }
                if (i < rows.size - 1) Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun CoverView(coverUrl: String, title: String, size: androidx.compose.ui.unit.Dp) {
    if (coverUrl.isNotEmpty()) {
        VeneraCover(
            url = coverUrl,
            contentDescription = title,
            mask = VeneraCoverMask.Visible,
            modifier = Modifier.size(size),
        )
    } else {
        val tokens = VeneraTokens
        Box(
            modifier = Modifier
                .size(size)
                .background(tokens.color.textPrimary.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
                .border(0.5.dp, tokens.color.outlineVariant, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title.trim().take(1),
                fontSize = 13.sp, lineHeight = 16.sp,
                fontWeight = FontWeight.Bold,
                color = tokens.color.textTertiary,
            )
        }
    }
}

// ==================== 最近阅读记录 ====================

@Composable
private fun RecentTableCard(rows: List<ReadingStatsRecentRow>) {
    val tokens = VeneraTokens
    MacCard {
        CardHead("最近阅读记录", null)
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
            listOf("作品名称", "来源", "阅读时长", "阅读量", "最近阅读").forEachIndexed { i, h ->
                Text(
                    text = h,
                    fontSize = 11.sp, lineHeight = 14.sp,
                    color = tokens.color.textTertiary,
                    modifier = Modifier.weight(if (i == 0) 1.6f else 1f),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(9.dp))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CoverView(row.coverUrl, row.title, 34.dp)
                Text(
                    text = row.title,
                    fontSize = 12.5.sp, lineHeight = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.color.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1.6f),
                )
                Text(
                    text = row.sourceName,
                    fontSize = 12.sp, lineHeight = 15.sp,
                    color = tokens.color.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = row.durationNote,
                    fontSize = 12.5.sp, lineHeight = 16.sp,
                    color = tokens.color.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${row.pages} 页",
                    fontSize = 12.5.sp, lineHeight = 16.sp,
                    color = tokens.color.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = row.atNote,
                    fontSize = 12.sp, lineHeight = 15.sp,
                    color = tokens.color.textSecondary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
