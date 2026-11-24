package com.venera.desktop.data

import com.venera.compose.data.db.DatabasePorts
import com.venera.compose.data.db.HistoryDao
import com.venera.compose.data.db.ReadingStatsStore
import com.venera.compose.data.db.SqlDatabaseSource
import com.venera.compose.data.db.StatsSummaryRow
import com.venera.compose.data.db.TopComicRow
import com.venera.compose.data.db.TrendRow
import com.venera.compose.stats.aggregateTagStats
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 桌面「阅读统计」那一屏的渲染输入 + 读源 + 判读纯件。
 *
 * ## 为什么是独立一屏，而不是把首页那三格扩一扩
 *
 * 首页那三格（`DesktopHomePane` 的 `item(key = "stats")`）读的是 `DesktopHomeStatsSource`，
 * 数字从批〔首页接真数据那一批〕起就在读。但安卓对岸还有一整屏看板
 * （`feature/StatsScreen.kt`：14 天趋势 + 常读漫画 Top 10），桌面从前**只有那三格、没有那一屏**
 * —— README 里那句「阅读统计：共享面在、桌面没有入口」量的就是这个。这一批把那一屏的门开上。
 *
 * ## 与安卓那颗 `StatsScreen` 的关系：不是直连，是自搭
 *
 * 对岸那颗屏 import `androidx.compose.material.icons.*` / `LocalContext` / `BusinessPorts`
 * （17 成员门面、175 个方法，绑 Context / assets），是 `docs/…reuse…` 里点名的 ③ 档（编不过）。
 * 所以这里**不搬那颗屏**，而是复用它底下那套已经在桌面编译面里的 SQL 读侧
 * （`ReadingStatsStore.summaryRows/trendRows/topComics/streakDays`），把渲染件（`StatCell`
 * / `StatBarRow`）拿过来摆成桌面版。数字与对岸同一张表、同一套 SQL，不在这里重算一遍。
 *
 * ## 拆法：读源、纯件、屏三层分家（与历史屏同一族）
 *
 * 判读（趋势填哪几天、时长怎么念、Top 榜取几颗）住在**纯件**里，能在 JVM 里直接测；
 * [DesktopReadingStatsSource] 只开库喂它们；屏只吃成品 [DesktopReadingStats]，零 IO。
 */

/** 14 天趋势里的一格（[label] 是 `MM-dd`；[works] 是当日去重本数，趋势第三序列的原料）。 */
data class ReadingStatsTrendCell(
    val label: String,
    val pages: Int,
    val seconds: Long = 0,
    val works: Int = 0,
)

/** 常读漫画榜里的一行（[seconds] 是排行进度条的那把尺：副注念「按阅读时长」就得按时长画）。 */
data class ReadingStatsTopCell(
    val comicId: String,
    val title: String,
    val sourceName: String,
    val pages: Int,
    /** 时长那句人话（由 [desktopReadingStatsDuration] 从秒数算，屏不自己拼）。 */
    val durationNote: String,
    val seconds: Long = 0,
    /** 封面地址（回查 comic_history；空串 = 没查到，屏回退首字母块）。 */
    val coverUrl: String = "",
)

/** 最近阅读记录表的一行（[atNote] 是「今天 22:14 / 11-12 23:08」那类相对时间，读源侧拼好）。 */
data class ReadingStatsRecentRow(
    val title: String,
    val sourceName: String,
    val pages: Int,
    val durationNote: String,
    val atNote: String,
    /** 封面地址（回查 comic_history；空串 = 没查到，屏回退首字母块）。 */
    val coverUrl: String = "",
)

/** 阅读习惯卡的三族读数（占位为 null 时屏上念「暂无数据」）。 */
data class ReadingStatsHabit(
    /** 最活跃的两小时桶起点（0-23），念成「21:00 – 23:00」。 */
    val topHourStart: Int?,
    /** 最活跃星期（1=周一 … 7=周日）。 */
    val topDow: Int?,
    /** 周一→周日各自的会话行数（7 柱迷你分布）。 */
    val dowDist: List<Int>,
)

/** KPI 环比：本期（最近 30 天）对上期（再前 30 天）的倍率；上期为 0 时 null（不摆百分比假账）。 */
data class ReadingStatsDelta(
    val secondsPct: Double?,
    val pagesPct: Double?,
    val comicsDelta: Int?,
)

/**
 * 那一屏的**全部渲染输入**。与首页那三格分开是刻意的：首页只要三个数，那一屏要趋势与榜单，
 * 合成一个 data class 会让首页也带上它用不上的字段。
 */
data class DesktopReadingStats(
    val todayPages: Int,
    val weekPages: Int,
    val streakDays: Int,
    val trend: List<ReadingStatsTrendCell>,
    val topComics: List<ReadingStatsTopCell>,
    val loaded: Boolean,
    val note: String?,
    val totalSeconds: Long = 0,
    val todaySeconds: Long = 0,
    val totalPages: Int = 0,
    val totalComics: Int = 0,
    val tagStats: com.venera.compose.stats.TagStats = com.venera.compose.stats.TagStats(),
    /** 最近 30 天里有阅读记录的天数（活跃天数那格的分母恒 30）。 */
    val activeDays: Int = 0,
    val delta: ReadingStatsDelta = ReadingStatsDelta(null, null, null),
    /** 热力格逐日页数，长度 = [DESKTOP_READING_STATS_HEAT_DAYS]，次序从最旧到最新。 */
    val heatPages: List<Int> = emptyList(),
    val habit: ReadingStatsHabit = ReadingStatsHabit(null, null, List(7) { 0 }),
    val recent: List<ReadingStatsRecentRow> = emptyList(),
) {
    companion object {
        /** 初值：**还没读**（loaded=false），不是「库里没有」。 */
        val Loading = DesktopReadingStats(
            todayPages = 0, weekPages = 0, streakDays = 0,
            trend = emptyList(), topComics = emptyList(),
            loaded = false, note = null,
            totalSeconds = 0, todaySeconds = 0, totalPages = 0, totalComics = 0,
            tagStats = com.venera.compose.stats.TagStats(),
        )
    }
}

/** 那一屏顶部三格与列表之间用的题名字符串（屏与判据共用同一颗，别各写一份会漂）。 */
internal const val DESKTOP_READING_STATS_SCREEN_TITLE: String = "阅读统计"
internal const val DESKTOP_READING_STATS_EMPTY_TITLE: String = "还没有阅读记录"
internal const val DESKTOP_READING_STATS_EMPTY_MESSAGE: String = "去随便翻两页，这里就有数了"

/** 趋势取最近多少天。对岸 `StatsScreen` 的 14 天口径（`HomeScreen` 顶三格另算本周 7 天）。 */
internal const val DESKTOP_READING_STATS_TREND_DAYS: Int = 14

/** 常读漫画榜取前几名。与对岸 Top 10 同一档。 */
internal const val DESKTOP_READING_STATS_TOP_LIMIT: Int = 10

/** 热力格覆盖的窗口天数（12 周整）。 */
internal const val DESKTOP_READING_STATS_HEAT_DAYS: Int = 371

/** 活跃天数格的统计窗口。 */
internal const val DESKTOP_READING_STATS_ACTIVE_WINDOW: Int = 30

/** 最近阅读记录表取几行。 */
internal const val DESKTOP_READING_STATS_RECENT_LIMIT: Int = 6

/**
 * 把 `trendRows`（库里从 [fromDate] 起、按天分组的合计）摊成最近 [days] 天**逐日**的格子：
 * 没读的那天填 0（不是缺格），日期次序从最旧到最新，[today] 落在最后一格。
 *
 * 为什么在纯件里补 0 而不在屏里补：屏里补就是「渲染层做数据加工」，与首页那格 `weekBar`
 * 在读源侧补 0 是同一族口径 —— 两处补法一旦不同，同一份库在首页与统计页会说两种话。
 */
internal fun desktopReadingStatsTrend(
    trendRows: List<TrendRow>,
    today: LocalDate,
    days: Int = DESKTOP_READING_STATS_TREND_DAYS,
): List<ReadingStatsTrendCell> {
    val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val label = DateTimeFormatter.ofPattern("MM-dd")
    val byDate = trendRows.associateBy({ it.readDate }, { it.pages })
    return (0 until days).map { offset ->
        // offset=0 是最旧那一格（today-(days-1)），最后一格是 today。
        val date = today.minusDays((days - 1 - offset).toLong())
        ReadingStatsTrendCell(
            label = date.format(label),
            pages = byDate[date.format(fmt)] ?: 0,
        )
    }
}

/**
 * 秒数 → 时长人话。三档各一形，不铺「0 秒」这种没读过的假账：
 *  - 不足一分钟 ⇒ 「不到一分钟」（读了但很少，不是没读）
 *  - 不足一小时 ⇒ 「N 分钟」
 *  - 其余 ⇒ 「N 小时 M 分」（分钟为 0 时省略那一段）
 */
internal fun desktopReadingStatsDuration(seconds: Long): String = when {
    seconds < 60 -> "不到一分钟"
    seconds < 3600 -> "${seconds / 60} 分钟"
    else -> {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        if (m == 0L) "$h 小时" else "$h 小时 $m 分"
    }
}

/**
 * 趋势格子（带秒数与去重本数）：把 [trendRows]（按天合计）与 [worksByDay]（去重本数）摊成
 * 最近 [days] 天，没读的天补 0。判据与 [desktopReadingStatsTrend] 同一条走法，只是格子更宽。
 */
internal fun desktopReadingStatsTrendCells(
    trendRows: List<TrendRow>,
    worksByDay: Map<String, Int>,
    today: LocalDate,
    days: Int = DESKTOP_READING_STATS_TREND_DAYS,
): List<ReadingStatsTrendCell> {
    val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val label = DateTimeFormatter.ofPattern("MM-dd")
    val byDate = trendRows.associateBy({ it.readDate }, { it })
    return (0 until days).map { offset ->
        val date = today.minusDays((days - 1 - offset).toLong())
        val key = date.format(fmt)
        val row = byDate[key]
        ReadingStatsTrendCell(
            label = date.format(label),
            pages = row?.pages ?: 0,
            seconds = row?.seconds ?: 0,
            works = worksByDay[key] ?: 0,
        )
    }
}

/** `TopComicRow` → 那一行的人话件（时长判读走 [desktopReadingStatsDuration]，不在屏里重算）。 */
internal fun desktopReadingStatsTopCells(rows: List<TopComicRow>): List<ReadingStatsTopCell> =
    rows.map {
        ReadingStatsTopCell(
            comicId = it.comicId,
            title = it.comicTitle,
            sourceName = it.sourceName,
            pages = it.pages,
            durationNote = desktopReadingStatsDuration(it.seconds),
            seconds = it.seconds,
        )
    }

/**
 * 热力格：把 [trendRows]（库里按天合计）摊成最近 [days]（[DESKTOP_READING_STATS_HEAT_DAYS]）天
 * 逐日页数，没读的天补 0，次序从最旧到最新（末位 = [today]）。
 */
internal fun desktopReadingStatsHeat(
    trendRows: List<TrendRow>,
    today: LocalDate,
    days: Int = DESKTOP_READING_STATS_HEAT_DAYS,
): List<Int> {
    val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val byDate = trendRows.associateBy({ it.readDate }, { it.pages })
    return (0 until days).map { offset ->
        byDate[today.minusDays((days - 1 - offset).toLong()).format(fmt)] ?: 0
    }
}

/**
 * 趋势第三序列（每日去重本数）：把 [rows] 按日去重（「哪算一本」= source+comic 组合，与题材占比
 * 同一把尺）摊成最近 [days] 天，没读的天补 0。键是 `yyyy-MM-dd`，与 [desktopReadingStatsTrend] 对齐。
 */
internal fun desktopReadingStatsWorksByDay(
    rows: List<StatsSummaryRow>,
    today: LocalDate,
    days: Int = DESKTOP_READING_STATS_TREND_DAYS,
): Map<String, Int> {
    val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val from = today.minusDays((days - 1).toLong()).format(fmt)
    val keys = (0 until days).map { today.minusDays((days - 1 - it).toLong()).format(fmt) }.toSet()
    return rows
        .filter { it.readDate >= from && it.readDate in keys }
        .groupBy { it.readDate }
        .mapValues { (_, dayRows) -> dayRows.map { "${it.sourceName}:${it.comicId}" }.distinct().size }
}

/** KPI 环比：本期（最近 [window] 天）对上期（再前 [window] 天）；上期总量为 0 ⇒ null，不摆百分比假账。 */
internal fun desktopReadingStatsDelta(
    rows: List<StatsSummaryRow>,
    today: LocalDate,
    window: Int = DESKTOP_READING_STATS_ACTIVE_WINDOW,
): ReadingStatsDelta {
    val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val curFrom = today.minusDays((window - 1).toLong()).format(fmt)
    val prevFrom = today.minusDays((window * 2 - 1).toLong()).format(fmt)
    fun inCur(d: String) = d >= curFrom
    fun inPrev(d: String) = d >= prevFrom && d < curFrom
    val curSec = rows.filter { inCur(it.readDate) }.sumOf { it.durationSeconds }
    val prevSec = rows.filter { inPrev(it.readDate) }.sumOf { it.durationSeconds }
    val curPages = rows.filter { inCur(it.readDate) }.sumOf { it.pagesRead }
    val prevPages = rows.filter { inPrev(it.readDate) }.sumOf { it.pagesRead }
    val curComics = rows.filter { inCur(it.readDate) }.map { "${it.sourceName}:${it.comicId}" }.distinct().size
    val prevComics = rows.filter { inPrev(it.readDate) }.map { "${it.sourceName}:${it.comicId}" }.distinct().size
    fun pct(cur: Long, prev: Long): Double? =
        if (prev <= 0L || cur == prev) null else (cur - prev).toDouble() / prev * 100.0
    return ReadingStatsDelta(
        secondsPct = pct(curSec, prevSec),
        pagesPct = pct(curPages.toLong(), prevPages.toLong()),
        comicsDelta = if (prevComics == 0 || curComics == prevComics) null else curComics - prevComics,
    )
}

/** 会话时刻 → 活跃时段的 2 小时桶起点（0/2/…/22）。 */
internal fun desktopReadingStatsHourBucket(createdAtMillis: Long): Int =
    (java.time.Instant.ofEpochMilli(createdAtMillis)
        .atZone(java.time.ZoneId.systemDefault()).hour / 2) * 2

/**
 * 活跃习惯三族读数：2 小时桶 Top1、星期分布 Top1 与 7 柱分布（1=周一…7=周日）。
 * 行数为 0 ⇒ 三族全 null/零，屏上念「暂无数据」。
 */
internal fun desktopReadingStatsHabit(createdAtRows: List<Long>): ReadingStatsHabit {
    if (createdAtRows.isEmpty()) return ReadingStatsHabit(null, null, List(7) { 0 })
    val zone = java.time.ZoneId.systemDefault()
    val hours = IntArray(12)
    val dows = IntArray(8) // 下标 1..7 对应周一..周日
    createdAtRows.forEach { millis ->
        val t = java.time.Instant.ofEpochMilli(millis).atZone(zone)
        hours[t.hour / 2]++
        dows[t.dayOfWeek.value]++
    }
    val topHour = hours.indices.maxByOrNull { hours[it] }?.takeIf { hours[it] > 0 }
    val topDow = (1..7).maxByOrNull { dows[it] }?.takeIf { dows[it] > 0 }
    return ReadingStatsHabit(
        topHourStart = topHour?.times(2),
        topDow = topDow,
        dowDist = (1..7).map { dows[it] },
    )
}

/** 两张排除表与共享归一化口径对齐：本模块没有那颗类的编译面，改动须同步。 */
private val TOPIC_EXCLUDED_NAMESPACES = setOf(
    "作者", "author", "authors", "artists", "artist", "画师", "插画",
    "状态", "status", "连载中", "更新", "update", "date", "时间", "热度",
    "view", "work", "misc", "语言", "language", "categories", "category",
    "分类", "分類", "group", "社团", "uploader", "上传", "上传者", "cosplayer", "reclass",
)
private val TOPIC_EXCLUDED_VALUES = setOf(
    "同人", "同人志", "同人誌", "短篇", "短篇集", "单行本", "画集", "画册", "cg", "cg集",
    "ai生成", "ai绘图", "ai-generated", "漫画", "杂志", "其他", "unknown",
    "系列", "连载中", "已完结", "完结", "全彩", "彩色", "无修", "无修正",
    "中文", "汉语", "国语", "繁体中文", "简体中文", "日语", "日文", "日本語",
    "英语", "英文", "english", "chinese", "japanese", "korean",
    "韩语", "韩文", "한국어", "translated",
    "汉化", "中文汉化", "个人汉化", "官方汉化", "禁漫汉化", "禁漫汉化组",
)

/**
 * 题材判读：原始标签 → 题材名；null = 非题材，避免分类词冒充题材。
 * @param plainTag 入库原始标签串（`namespace:tag`）；裸串一律丢弃。
 * @return 题材名；两表命中时返回 null。无字典编译面，故不做同义合并。
 */
internal fun desktopTopicOf(plainTag: String): String? {
    val idx = plainTag.indexOf(':')
    if (idx <= 0) return null
    val namespace = plainTag.substring(0, idx).trim().lowercase()
    val tag = plainTag.substring(idx + 1).trim()
    if (tag.isEmpty() || namespace in TOPIC_EXCLUDED_NAMESPACES) return null
    if (tag.lowercase() in TOPIC_EXCLUDED_VALUES) return null
    return tag
}

/** 落库时刻 → 「今天 22:14 / 昨天 21:37 / 11-12 23:08」三形之一（参考稿那列的口径）。 */
internal fun desktopReadingStatsAtNote(createdAtMillis: Long, today: LocalDate): String {
    val t = java.time.Instant.ofEpochMilli(createdAtMillis).atZone(java.time.ZoneId.systemDefault())
    val time = java.time.format.DateTimeFormatter.ofPattern("HH:mm").format(t)
    val date = t.toLocalDate()
    return when {
        date == today -> "今天 $time"
        date == today.minusDays(1) -> "昨天 $time"
        else -> java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm").format(t)
    }
}

/**
 * stdout 那一行取证读数（只进 stdout，不进界面）。`行=`/`榜=` 让包内复测的人一眼看出
 * 那一屏读到的是不是真数据（0 行且 loaded ⇒ 库里真没记录；-1 或键不在 ⇒ 读挂了）。
 */
internal fun desktopReadingStatsScreenReading(
    loaded: Boolean,
    note: String?,
    trendCount: Int,
    topCount: Int,
    todayPages: Int,
): String {
    val head = "D_统计 屏 读=${if (loaded) "是" else "未"} 今日=$todayPages 趋=$trendCount 榜=$topCount"
    return if (note == null) head else "$head 因=${note.replace('\n', ' ').replace('\r', ' ')}"
}

/**
 * 桌面统计屏的**真数据读源**：开库、取三族 SQL 行、喂给上面的纯件。
 *
 * 与 [DesktopHomeStatsSource] 分开两颗对象而不合并，是因为首页只要三格数字（本周 7 天），
 * 那一屏要 14 天趋势 + Top 10（对岸的口径）—— 合成一颗会让首页的 effect 白读那两族它用不上的行。
 * 两者读的是**同一张 `reading_stats` 表、同一套 `ReadingStatsStore` 方法**，不会漂。
 */
internal object DesktopReadingStatsSource {

    private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    suspend fun read(): DesktopReadingStats =
        // 与首页那颗同一兜法：`DatabasePorts.of` 未接线会**抛**，先兜住取 core 这一步，
        // 否则「读不到就如实报成因」在这条路上有洞（架构评审 R4 同族）。
        runCatching { DatabasePorts.of(null).core }.fold(
            onSuccess = { readFrom(it) },
            onFailure = { t ->
                // 原文只进日志，屏上念短句。
                println("D_阅读统计 读不到库 ${t::class.java.simpleName}: ${t.message?.take(160)}")
                DesktopReadingStats.Loading.copy(
                    loaded = true,
                    note = com.venera.desktop.platform.desktopErrorKindOf(t).label,
                )
            },
        )

    /** 从**指定库**读（[dbFile] 为 null 时用进程级 `DatabasePorts` 那颗）。摆这个入口是为了判据测得了。 */
    suspend fun readFrom(core: SqlDatabaseSource): DesktopReadingStats =
        withContext(Dispatchers.IO) {
            try {
                val store = ReadingStatsStore(core)
                val today = LocalDate.now()
                val weekStart = today.minusDays(today.dayOfWeek.value - 1L)

                val rows = store.summaryRows()
                val todayPages = rows.filter { it.readDate == today.format(DATE) }.sumOf { it.pagesRead }
                val weekPages = store.trendRows(weekStart.format(DATE)).sumOf { it.pages }

                val totalSec = rows.sumOf { it.durationSeconds }
                val todaySec = rows.filter { it.readDate == today.format(DATE) }.sumOf { it.durationSeconds }
                val totalP = rows.sumOf { it.pagesRead }
                val totalC = rows.map { "${it.sourceName}:${it.comicId}" }.distinct().size

                // 封面回查：走历史表自己的读法（键与 reading_stats 的「一本」同组合）。
                val coverMap = runCatching { HistoryDao(core).coverIndexSync() }.getOrDefault(emptyMap())

                val trendFrom = today.minusDays((DESKTOP_READING_STATS_TREND_DAYS - 1).toLong()).format(DATE)
                val worksByDay = desktopReadingStatsWorksByDay(rows, today)
                val trend = desktopReadingStatsTrendCells(store.trendRows(trendFrom), worksByDay, today)
                val top = desktopReadingStatsTopCells(store.topComics(DESKTOP_READING_STATS_TOP_LIMIT)).map {
                    it.copy(coverUrl = coverMap["${it.sourceName}:${it.comicId}"] ?: "")
                }

                val dates = store.distinctReadDates()
                val streak = store.streakDays(
                    dates, today.format(DATE), today.minusDays(1).format(DATE)
                ) { d -> dayBefore(d) }

                // 活跃天数：最近 30 天里有记录的日子数（trendRows 只为有记录的日子出行）。
                val activeFrom = today.minusDays((DESKTOP_READING_STATS_ACTIVE_WINDOW - 1).toLong()).format(DATE)
                val activeDays = store.trendRows(activeFrom).size

                // 热力格与环比、习惯、最近记录。
                val heatFrom = today.minusDays((DESKTOP_READING_STATS_HEAT_DAYS - 1).toLong()).format(DATE)
                val heatPages = desktopReadingStatsHeat(store.trendRows(heatFrom), today)
                val delta = desktopReadingStatsDelta(rows, today)
                val habit = desktopReadingStatsHabit(store.createdAtRows(activeFrom))
                // 按书去重：逐 session 行里同一本反复出现——
                // 每本只留最近一条，多取几行兜去重损耗。
                val recent = store.recentRows(DESKTOP_READING_STATS_RECENT_LIMIT * 4)
                    .map {
                        ReadingStatsRecentRow(
                            title = it.comicTitle,
                            sourceName = it.sourceName,
                            pages = it.pagesRead,
                            durationNote = desktopReadingStatsDuration(it.durationSeconds),
                            atNote = desktopReadingStatsAtNote(it.createdAt, today),
                            coverUrl = coverMap["${it.sourceName}:${it.comicId}"] ?: "",
                        )
                    }
                    .distinctBy { "${it.sourceName}:${it.title}" }
                    .take(DESKTOP_READING_STATS_RECENT_LIMIT)

                val taggedFrom = today.minusDays(365).format(DATE)
                val taggedRows = runCatching { store.taggedRows(taggedFrom) }.getOrDefault(emptyList())
                val tagStats = com.venera.compose.stats.aggregateTagStats(
                    taggedRows, 365, com.venera.compose.stats.ReadingContentRating.ALL
                ) { desktopTopicOf(it) }

                DesktopReadingStats(
                    todayPages = todayPages,
                    weekPages = weekPages,
                    streakDays = streak,
                    trend = trend,
                    topComics = top,
                    loaded = true,
                    note = null,
                    totalSeconds = totalSec,
                    todaySeconds = todaySec,
                    totalPages = totalP,
                    totalComics = totalC,
                    tagStats = tagStats,
                    activeDays = activeDays,
                    delta = delta,
                    heatPages = heatPages,
                    habit = habit,
                    recent = recent,
                )
            } catch (t: Throwable) {
                // 原文只进日志，屏上念短句。
                println("D_阅读统计 读不到库 ${t::class.java.simpleName}: ${t.message?.take(160)}")
                DesktopReadingStats.Loading.copy(
                    loaded = true,
                    note = com.venera.desktop.platform.desktopErrorKindOf(t).label,
                )
            }
        }

    private fun dayBefore(dateKey: String): String = try {
        LocalDate.parse(dateKey, DATE).minusDays(1).format(DATE)
    } catch (e: Exception) {
        dateKey
    }
}
