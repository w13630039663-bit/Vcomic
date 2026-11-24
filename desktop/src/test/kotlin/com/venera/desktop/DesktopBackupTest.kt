package com.venera.desktop

import com.venera.desktop.data.DESKTOP_BACKUP_KEY_GUARD
import com.venera.desktop.data.DESKTOP_BACKUP_KEY_HISTORY
import com.venera.desktop.data.DESKTOP_BACKUP_KEY_STATS
import com.venera.desktop.data.DESKTOP_BACKUP_VERSION
import com.venera.desktop.data.DesktopBackupCounts
import com.venera.desktop.data.DesktopRestoreOutcome
import com.venera.desktop.data.desktopBackupCountsNote
import com.venera.desktop.data.desktopBackupDecodeJson
import com.venera.desktop.data.desktopBackupDefaultFileName
import com.venera.desktop.data.desktopBackupEncodeJson
import com.venera.desktop.data.desktopRestoreOutcomeNote
import com.venera.compose.data.db.parseJsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 备份那一屏的**纯件值尺**。
 *
 * 只管 encode/decode/文案那几颗算式，不碰库也不碰 AWT（那两件事各有其尺）。
 *
 * ⚠️ 关于键序：`desktopBackupEncodeJson` 用 gson 的 `JsonObject()` 手动 add 三族键，而 gson
 * 默认**不保证**插入序（不同版本会重排）。这里刻意**只做 round-trip**（encode → decode 回来
 * 按字段名取），不锁逐字节文本 —— 因为桌面这一份是**单文件 JSON、只给本机自己还原**，
 * 对岸那颗 `.venera` ZIP 才有"跨设备逐字节不许变"的硬约束（那颗用 `BackupManager` 里
 * `jsonObject(fields)` 那颗 LinkedHashMap 保序件，语义不同）。锁字节序会把桌面钉死在一个
 * gson 实现细节上，而功能正确性（counts 一致、恢复读得到）与键序无关。
 */
class DesktopBackupTest {

    private fun arr(text: String) = parseJsonArray(text)

    /** 三族都有行时，encode → decode 回来条数与各族长度一致、counts 与数组对得上。 */
    @Test
    fun 三族往返后条数与计数对得上() {
        val text = desktopBackupEncodeJson(
            version = DESKTOP_BACKUP_VERSION,
            timestamp = 1_700_000_000_000L,
            history = arr("""[{"comic_id":"a"},{"comic_id":"b"}]"""),
            stats = arr("""[{"read_date":"2026-10-01"}]"""),
            guard = arr("[]"),
        )
        val snap = desktopBackupDecodeJson(text)
        assertEquals(DESKTOP_BACKUP_VERSION, snap.version)
        assertEquals(2, snap.counts.history)
        assertEquals(1, snap.counts.stats)
        assertEquals(0, snap.counts.guard)
        assertEquals(2, snap.history.size())
        assertEquals(1, snap.stats.size())
    }

    /** 全空的一份快照：counts 三样都是 0，往返仍成立（空表能导 ≠ 读不到库）。 */
    @Test
    fun 空快照往返成立() {
        val text = desktopBackupEncodeJson(
            DESKTOP_BACKUP_VERSION, 0L, arr("[]"), arr("[]"), arr("[]"),
        )
        val snap = desktopBackupDecodeJson(text)
        assertTrue("空快照的 counts 该全 0：${snap.counts}", snap.counts.isEmpty)
    }

    /** `meta.counts` 与实际数组长度**对不上**那一档：半截/手工改过的文件 ⇒ 抛（宁可拒收）。 */
    @Test
    fun 计数与数组不符时抛而不静默少灌() {
        // counts 声明 history=5，实际数组只有 2 行 —— 正是"字段名都对而内容短一截"那种坏档。
        val bad = """
            {"meta":{"version":$DESKTOP_BACKUP_VERSION,"counts":{"history":5,"stats":0,"guard":0}},
             "$DESKTOP_BACKUP_KEY_HISTORY":[{"comic_id":"a"},{"comic_id":"b"}],
             "$DESKTOP_BACKUP_KEY_STATS":[],"$DESKTOP_BACKUP_KEY_GUARD":[]}
        """.trimIndent()
        val e = assertThrows(IllegalStateException::class.java) { desktopBackupDecodeJson(bad) }
        assertTrue("成因要念得出对不上的两数：${e.message}", e.message!!.contains("2") && e.message!!.contains("5"))
    }

    /** 版本比桌面这一颗新 ⇒ 抛（不许拿未来的备份往今天的库里灌）。 */
    @Test
    fun 未来的版本不认() {
        val future = """
            {"meta":{"version":${DESKTOP_BACKUP_VERSION + 1},"counts":{"history":0,"stats":0,"guard":0}},
             "$DESKTOP_BACKUP_KEY_HISTORY":[],"$DESKTOP_BACKUP_KEY_STATS":[],"$DESKTOP_BACKUP_KEY_GUARD":[]}
        """.trimIndent()
        assertThrows(IllegalStateException::class.java) { desktopBackupDecodeJson(future) }
    }

    /** 缺三族键里任意一个 ⇒ 抛（不是一枚默认空数组糊过去）。 */
    @Test
    fun 缺一族键时抛() {
        val missing = """
            {"meta":{"version":$DESKTOP_BACKUP_VERSION,"counts":{"history":0,"stats":0,"guard":0}},
             "$DESKTOP_BACKUP_KEY_STATS":[],"$DESKTOP_BACKUP_KEY_GUARD":[]}
        """.trimIndent()
        assertThrows(IllegalStateException::class.java) { desktopBackupDecodeJson(missing) }
    }

    /** 默认文件名那颗：带 `.json` 后缀、把时间戳嵌进去（与对岸 `.venera` 那条区分开）。 */
    @Test
    fun 默认文件名带json后缀() {
        val name = desktopBackupDefaultFileName(1_234L)
        assertTrue("后缀该是 .json：$name", name.endsWith(".json"))
        assertTrue("该嵌进时间戳：$name", name.contains("1234"))
    }

    /** 导出的三句文案：有数那一档点名三族条数，空那一档念另一句（不冒充"0 条=失败"）。 */
    @Test
    fun 导出文案分得开空与非空() {
        val nonEmpty = desktopBackupCountsNote(DesktopBackupCounts(history = 3, stats = 1, guard = 2))
        assertTrue("非空那句该点名三族：$nonEmpty", nonEmpty.contains("3") && nonEmpty.contains("1") && nonEmpty.contains("2"))
        val empty = desktopBackupCountsNote(DesktopBackupCounts(0, 0, 0))
        assertTrue("空快照那句要明说这是成功而非失败：$empty", empty.contains("空的"))
    }

    /** 还原的文案：有跳过数那一档必须念得出跳了几条（否则用户以为全灌进去了）。 */
    @Test
    fun 还原文案带跳过数() {
        val withSkip = desktopRestoreOutcomeNote(
            DesktopRestoreOutcome(history = 4, stats = 2, guardImported = 1, guardSkipped = 3),
        )
        assertTrue("跳过数得上屏：$withSkip", withSkip.contains("3"))
        val noSkip = desktopRestoreOutcomeNote(
            DesktopRestoreOutcome(history = 4, stats = 2, guardImported = 1, guardSkipped = 0),
        )
        assertTrue("没跳过时不念那句括号：$noSkip", "没收" !in noSkip)
    }
}
