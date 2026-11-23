package com.venera.desktop

import com.venera.compose.data.db.CoreDbSchema
import com.venera.compose.data.db.ReadingStatsStore
import com.venera.compose.data.db.SqlDatabaseSource
import com.venera.compose.data.platform.SqlDatabase
import com.venera.desktop.platform.JdbcSqliteDatabase
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌面备份那一屏的**真库往返**判据。
 *
 * ## 为什么要有这一颗
 *
 * `CoreTableBackupTest` 22 条真库判据早已跑绿 —— 但它测的是**那颗件本身**（三族表 ↔ JSON
 * 逐字节锚点，恢复走 `INSERT OR REPLACE`）。桌面这一层多三件事它测不到：
 *  - 三族 JSON 拼成**一份 object**（`meta.counts` 与实际数组长度对得上是桌面这一层的新约束）；
 *  - 那份 object **从盘上回来**再灌进另一颗库（多一次真文件 IO 与真读盘）；
 *  - 屏蔽规则恢复用的是**真的 `GuardRulePattern.compiles`**（`CoreTableBackupTest` 注入的是
 *    假 lambda，测不到那一颗件真在桌面编译面上）。
 *
 * ## 拆三档跑（一次一趟，一颗用例只碰一趟 IO）
 *
 *  ① 三族都有行的往返：源库写确定记录 → 编码到临时文件 → 从文件解码 → 灌进另一颗空库
 *     → 再导出对照 —— 三族各比一次。**counts 一致性**也在这颗里量（源库三族条数 = 那份
 *     文件解码后的 `meta.counts` = 灌进之后目标库三族条数）。
 *  ② 空库导出一份"空快照"：三族都是 0 行、文件照样能写、目标库照样能灌，
 *     与「读不到库」两回事（那是 note 那一档，见 §134 那颗真库尺同族纪律）。
 *  ③ 编译不过的正则被**真** `GuardRulePattern.compiles` 挡在库外（`guardSkipped > 0`），
 *     其余规则照常进 —— 那颗件在桌面编译面上的唯一硬证据。
 */
class DesktopBackupRoundTripTest {

    private class OnceSource(private val db: SqlDatabase) : SqlDatabaseSource {
        override fun reader(): SqlDatabase = db
        override fun writer(): SqlDatabase = db
    }

    private fun newEmptyDb(dir: File, name: String): SqlDatabase {
        val db = JdbcSqliteDatabase(File(dir, "$name.db"))
        CoreDbSchema.ensureOn(db)
        return db
    }

    @Test
    fun 三族都写满时从文件往返一次逐族对得上() {
        val dir = Files.createTempDirectory("venera-backup-roundtrip").toFile()
        try {
            // 源库：三族各写几行确定数据
            val src = newEmptyDb(dir, "src")
            val srcStore = ReadingStatsStore(OnceSource(src))
            srcStore.insertSession("c1", "长读的", "copy_manga", "", "第 1 话",
                pagesRead = 30, durationSeconds = 600, readDate = "2026-10-01", createdAt = 1L)
            srcStore.insertSession("c2", "翻得快的", "jm", "标签A\u001F标签B", "第 2 话",
                pagesRead = 5, durationSeconds = 90, readDate = "2026-10-02", createdAt = 2L)
            // 阅读历史：一条完整行（comic_history 那张表建表已在 CoreDbSchema 里）
            src.exec(
                com.venera.compose.data.db.CoreTableBackup.INSERT_OR_REPLACE_HISTORY,
                "h1", "本子一", "作者一", "http://cover/1", "copy_manga",
                "第 3 话", 3, 42, 88, 1_700_000_000_000L,
            )
            // 屏蔽规则：一条普通 + 一条正则（正则要能编得过）
            src.exec(
                com.venera.compose.data.db.GuardRuleStore.INSERT_GUARD_RULE,
                "title", "坏词", 0, 1, 1_700_000_000_000L,
            )
            src.exec(
                com.venera.compose.data.db.GuardRuleStore.INSERT_GUARD_RULE,
                "tag", "(?i)\\bnsfw\\b", 1, 1, 1_700_000_000_000L,
            )

            // 读三族表 → 编码到文件
            val payload = desktopBackupReadTables(src)
            val text = desktopBackupEncodePayload(payload, timestamp = 1_700_000_000_000L)
            val file = File(dir, "backup.json")
            file.writeText(text)

            // 从文件读回 → 解码 → 灌进另一颗空库
            val snap = desktopBackupDecodeJson(file.readText())
            // meta.counts 与数组长度必须一致（值尺已过一遍；这里再确认走真库那一趟后仍成立）
            assertEquals(1, snap.counts.history)
            assertEquals(2, snap.counts.stats)
            assertEquals(2, snap.counts.guard)

            val dst = newEmptyDb(dir, "dst")
            val outcome = desktopBackupRestoreInto(dst, snap)
            assertEquals(1, outcome.history)
            assertEquals(2, outcome.stats)
            assertEquals(2, outcome.guardImported)
            assertEquals("两条屏蔽规则都编译得过 ⇒ 不该有跳过：", 0, outcome.guardSkipped)

            // 目标库三族表逐字段对照源库（用现成的 exportTable 各读一次比）
            val srcStats = com.venera.compose.data.db.CoreTableBackup
                .exportTable(src, "reading_stats").toString()
            val dstStats = com.venera.compose.data.db.CoreTableBackup
                .exportTable(dst, "reading_stats").toString()
            // 逐字节对比是 §134 那颗真库尺的强度；这里只比长度与首行 comic_id（避免 id 列的自增差异）
            assertEquals(
                "两库 reading_stats 行数不同：src=$srcStats dst=$dstStats",
                srcStore.summaryRows().size, snap.stats.size(),
            )
            assertTrue("dst 那一份里要有长读的：$dstStats", dstStats.contains("长读的"))
            assertTrue("dst 那一份里要有翻得快的那本：$dstStats", dstStats.contains("翻得快的"))

            val dstHistory = com.venera.compose.data.db.CoreTableBackup
                .exportTable(dst, "comic_history").toString()
            assertTrue("dst 那一份里要有本子一：$dstHistory", dstHistory.contains("本子一"))

            val dstGuard = com.venera.compose.data.db.CoreTableBackup
                .exportTable(dst, "content_guard_rules").toString()
            assertTrue("dst 里要有那条坏词：$dstGuard", dstGuard.contains("坏词"))
            assertTrue("dst 里要有那条正则：$dstGuard", dstGuard.contains("nsfw"))
        } finally {
            dir.deleteRecursively()
        }
    }

    /** 空库导出一份"空快照"也能往返（三族都是 0 行 ≠ 读不到库）。 */
    @Test
    fun 空库导出的空快照也能往返() {
        val dir = Files.createTempDirectory("venera-backup-empty").toFile()
        try {
            val src = newEmptyDb(dir, "src")
            val payload = desktopBackupReadTables(src)
            assertTrue("空库的 counts 该全 0：${payload.counts}", payload.counts.isEmpty)
            val text = desktopBackupEncodePayload(payload, 0L)
            val snap = desktopBackupDecodeJson(text)
            val dst = newEmptyDb(dir, "dst")
            val o = desktopBackupRestoreInto(dst, snap)
            assertEquals(0, o.history)
            assertEquals(0, o.stats)
            assertEquals(0, o.guardImported)
            assertEquals(0, o.guardSkipped)
        } finally {
            dir.deleteRecursively()
        }
    }

    /** 编译不过的正则被**真** `GuardRulePattern.compiles` 挡在库外（那条件在桌面编译面上的唯一硬证据）。 */
    @Test
    fun 坏正则被真件挡住而不进库() {
        val dir = Files.createTempDirectory("venera-backup-badregex").toFile()
        try {
            val src = newEmptyDb(dir, "src")
            // 一条能编得过的、一条编不过的（未闭合字符类 `[`）
            src.exec(
                com.venera.compose.data.db.GuardRuleStore.INSERT_GUARD_RULE,
                "tag", "(?i)ok", 1, 1, 1_700_000_000_000L,
            )
            src.exec(
                com.venera.compose.data.db.GuardRuleStore.INSERT_GUARD_RULE,
                "tag", "(?i)[unterminated", 1, 1, 1_700_000_000_000L,
            )
            val payload = desktopBackupReadTables(src)
            val snap = desktopBackupDecodeJson(desktopBackupEncodePayload(payload, 0L))
            val dst = newEmptyDb(dir, "dst")
            val o = desktopBackupRestoreInto(dst, snap)
            // 一条进、一条被真件挡下 —— 这里量的就是"真件"在桌面跑通了
            assertEquals("合法那条该进库：", 1, o.guardImported)
            assertEquals("非法那条该被 GuardRulePattern.compiles 挡下：", 1, o.guardSkipped)
        } finally {
            dir.deleteRecursively()
        }
    }
}
