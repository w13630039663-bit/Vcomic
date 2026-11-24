package com.venera.desktop

import com.venera.desktop.data.DesktopReadingStatsSource
import com.venera.compose.data.db.ReadingStatsStore
import com.venera.compose.data.db.SchemaSql
import com.venera.compose.data.db.SqlDatabaseSource
import com.venera.compose.data.platform.SqlDatabase
import com.venera.compose.data.platform.SqlRow
import com.venera.desktop.platform.JdbcSqliteDatabase
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 钉住「桌面统计屏的数字、趋势、榜来自**真库**」—— 。
 *
 * 与 [DesktopHomeStatsSourceTest] 同一族、同一台架：先往临时库写几条确定记录，再让
 * [DesktopReadingStatsSource.readFrom] 去读，断言读回来的与直接用 SQL 算的一致。
 * 首页那把只断三个标量；这一屏多了 14 天趋势与 Top 榜两族行，所以这里把它们也钉上。
 */
class DesktopReadingStatsSourceTest {

    private val FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd")

    private fun createSchema(db: SqlDatabase) {
        SchemaSql.V1_CREATE_STATEMENTS.forEach { db.exec(it) }
    }

    /** 一颗包住单个连接的 [SqlDatabaseSource]（与首页那颗同名不同文件，各自自包含）。 */
    private class OnceSource(private val db: SqlDatabase) : SqlDatabaseSource {
        override fun reader(): SqlDatabase = db
        override fun writer(): SqlDatabase = db
    }

    @Test
    fun 数字趋势与榜都对得上真库() = runBlocking {
        val dir = Files.createTempDirectory("venera-reading-stats").toFile()
        try {
            val today = LocalDate.now()
            val yesterday = today.minusDays(1)
            val dayBefore = today.minusDays(2)

            val dbFile = File(dir, "probe.db")
            val seedDb = JdbcSqliteDatabase(dbFile)
            createSchema(seedDb)
            val seedSource = OnceSource(seedDb)
            val seedStore = ReadingStatsStore(seedSource)
            // c1 两天、c2 一天，故意让时长排序与页数排序不同，看榜用的是哪一把尺。
            seedStore.insertSession("c1", "长读的", "探针源", "", "第 1 话",
                pagesRead = 30, durationSeconds = 600, readDate = today.format(FMT), createdAt = 1_000L)
            seedStore.insertSession("c1", "长读的", "探针源", "", "第 2 话",
                pagesRead = 20, durationSeconds = 3600, readDate = yesterday.format(FMT), createdAt = 1_001L)
            seedStore.insertSession("c2", "翻得快的", "探针源", "", "第 1 话",
                pagesRead = 5, durationSeconds = 90, readDate = dayBefore.format(FMT), createdAt = 1_002L)

            val got = DesktopReadingStatsSource.readFrom(seedSource)

            // 三格数字：与直接读 summaryRows 一致，且今日那格不该是 0（0 就是没读库）。
            val expectedToday = seedStore.summaryRows()
                .filter { it.readDate == today.format(FMT) }
                .sumOf { it.pagesRead }
            assertEquals("今日页数没从库里读对：", expectedToday, got.todayPages)
            assertTrue("今日页数不该是 0 —— 那就是没读库：${got.todayPages}", got.todayPages > 0)
            assertTrue("读成功不该有成因：${got.note}", got.note == null)
            assertTrue("读成功该是已读回来：", got.loaded)

            // 趋势：14 格，末格是今天且吃到今天那行。
            assertEquals("趋势该摊成 14 格：", 14, got.trend.size)
            assertEquals(today.format(LABEL), got.trend.last().label)
            assertEquals(expectedToday, got.trend.last().pages)
            // 前天那格（下标 14-1-2 = 11）该是 c2 那 5 页。
            assertEquals(5, got.trend[11].pages)

            // 榜：按**时长**降序（与对岸 `topComics` 的 ORDER BY 一致），c1（4200 秒）在 c2（90 秒）前面。
            assertEquals("榜该有两本：", 2, got.topComics.size)
            assertEquals("长读的", got.topComics.first().title)
            // c1 合计 600+3600=4200 秒 = 1 小时 10 分。
            assertEquals("1 小时 10 分", got.topComics.first().durationNote)
            // 连续天数：连着今天/昨/前 3 天。
            assertEquals(3, got.streakDays)
            // 三行 tags 全空：题材桶与分母都得是 0，不许编源名/连载/漫画充数。
            assertTrue("空 tags 不该编出题材桶：", got.tagStats.buckets.isEmpty())
            assertEquals("空 tags 不该编出题材本数：", 0, got.tagStats.taggedComics)
        } finally {
            dir.deleteRecursively()
        }
    }

    /** 只有带命名空间的题材成桶，分类词与格式词一律挡掉。 */
    @Test
    fun 题材只收库里的真题材标签() = runBlocking {
        val dir = Files.createTempDirectory("venera-reading-stats-tags").toFile()
        try {
            val dbFile = File(dir, "probe.db")
            val seedDb = JdbcSqliteDatabase(dbFile)
            createSchema(seedDb)
            val seedSource = OnceSource(seedDb)
            val seedStore = ReadingStatsStore(seedSource)
            seedStore.insertSession("c1", "带题材的", "探针源",
                "female:lolicon\u001F全彩\u001Fother:短篇", "第 1 话",
                pagesRead = 20, durationSeconds = 600,
                readDate = LocalDate.now().format(FMT), createdAt = 1_000L)
            val got = DesktopReadingStatsSource.readFrom(seedSource)
            assertEquals("只有真题材该成桶：", 1, got.tagStats.buckets.size)
            assertEquals("lolicon", got.tagStats.buckets.single().display)
            assertEquals("分母只数贡献了真题材的本：", 1, got.tagStats.taggedComics)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun 库打不开时如实报成因而不假装空() = runBlocking {
        val broken = OnceSource(object : SqlDatabase {
            override fun query(sql: String, vararg args: Any?): List<SqlRow> =
                throw IllegalStateException("库打不开（判据探针）")
            override fun exec(sql: String, vararg args: Any?) =
                throw IllegalStateException("库打不开（判据探针）")
            override fun insert(sql: String, vararg args: Any?): Long =
                throw IllegalStateException("库打不开（判据探针）")
            override fun inTransaction(block: () -> Unit) =
                throw IllegalStateException("库打不开（判据探针）")
            override fun close() = Unit
        })
        val got = DesktopReadingStatsSource.readFrom(broken)
        // 读失败：note 必须有话说，且 loaded=true（屏上靠 note 分档，loaded 只用于「读过了」）。
        assertTrue("读不到库时 note 必须有话说：${got.note}", got.note != null)
        assertTrue("读失败也算读回来了一趟（loaded 指「不再是初值」）：", got.loaded)
    }
}
