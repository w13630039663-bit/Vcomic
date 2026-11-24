package com.venera.desktop

import com.venera.desktop.data.DesktopReadingStats
import com.venera.desktop.data.desktopReadingStatsDuration
import com.venera.desktop.data.desktopReadingStatsScreenReading
import com.venera.desktop.data.desktopReadingStatsTopCells
import com.venera.desktop.data.desktopReadingStatsTrend
import com.venera.desktop.data.desktopTopicOf
import com.venera.compose.data.db.TopComicRow
import com.venera.compose.data.db.TrendRow
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 阅读统计那一屏的**纯件值尺**。
 *
 * 判读全住在 [DesktopReadingStats] 外面那几颗纯件里（补 0、时长人话、Top 映射），
 * 这一文件只验算式，不开库也不渲屏（那两件事各有其尺）。
 */
class DesktopReadingStatsTest {

    // ── 趋势填格 ──

    /** 14 格、末格是今天、缺失天补 0（不是缺格）。 */
    @Test
    fun 趋势摊成逐日十四格而缺失天补零() {
        val today = LocalDate.of(2026, 10, 25)
        val rows = listOf(
            // 今天与三天前各有货，其余天缺。
            TrendRow("2026-10-25", pages = 30, seconds = 600),
            TrendRow("2026-10-22", pages = 12, seconds = 240),
        )
        val cells = desktopReadingStatsTrend(rows, today, days = 14)
        assertEquals("该摊成 14 格：", 14, cells.size)
        // 末格 = 今天，且吃到今天那行的页数。
        assertEquals("10-25", cells.last().label)
        assertEquals(30, cells.last().pages)
        // 最旧一格 = today-13。
        assertEquals("10-12", cells.first().label)
        // 三天前那格（today-3）落在下标 14-1-3 = 10。
        assertEquals(12, cells[10].pages)
        // 没有记录的那天是 0，不是缺格。
        assertEquals("没有记录的那天该是 0", 0, cells[5].pages)
    }

    /** 天数参数可变（首页那格用 7，这一屏用 14），且格数跟着走。 */
    @Test
    fun 天数参数决定格数() {
        val today = LocalDate.of(2026, 10, 25)
        val cells = desktopReadingStatsTrend(emptyList(), today, days = 7)
        assertEquals(7, cells.size)
        assertTrue("空表也要全补 0：", cells.all { it.pages == 0 })
    }

    // ── 时长人话 ──

    @Test
    fun 时长三档各一形() {
        assertEquals("不到一分钟", desktopReadingStatsDuration(0))
        assertEquals("不到一分钟", desktopReadingStatsDuration(59))
        assertEquals("1 分钟", desktopReadingStatsDuration(60))
        assertEquals("59 分钟", desktopReadingStatsDuration(3599))
        assertEquals("1 小时", desktopReadingStatsDuration(3600))
        assertEquals("1 小时 1 分", desktopReadingStatsDuration(3660))
        assertEquals("2 小时", desktopReadingStatsDuration(7200))
    }

    // ── Top 映射 ──

    /** `TopComicRow` → 人话件：时长判读走同一颗，不在映射里另算。 */
    @Test
    fun 榜行映射到成品件() {
        val rows = listOf(
            TopComicRow("c1", "第一本", "拷贝漫画", pages = 88, seconds = 7200),
            TopComicRow("c2", "第二本", "禁漫天堂", pages = 5, seconds = 30),
        )
        val cells = desktopReadingStatsTopCells(rows)
        assertEquals(2, cells.size)
        assertEquals("第一本", cells[0].title)
        assertEquals("拷贝漫画", cells[0].sourceName)
        assertEquals(88, cells[0].pages)
        assertEquals("2 小时", cells[0].durationNote)
        // 不足一分钟那一档也要落进榜行（读了但很少 ≠ 没读）。
        assertEquals("不到一分钟", cells[1].durationNote)
    }

    // ── 题材判读 ──

    /** 分类词、格式词与裸串都不算题材；带命名空间的真题材原样留。 */
    @Test
    fun 题材判读只认带命名空间的题材() {
        assertEquals("lolicon", desktopTopicOf("female:lolicon"))
        assertEquals("校服", desktopTopicOf("female:校服"))
        assertEquals(null, desktopTopicOf("全彩"))
        assertEquals(null, desktopTopicOf("other:短篇"))
        assertEquals(null, desktopTopicOf("language:中文"))
        assertEquals(null, desktopTopicOf("category:C101"))
        assertEquals(null, desktopTopicOf("author:某某"))
        assertEquals(null, desktopTopicOf(":lolicon"))
        assertEquals(null, desktopTopicOf("female:"))
    }

    // ── 取证读数 ──

    /** 读得到与读不到那两档，读数行要能分开（`因=` 只在该有成因时出现）。 */
    @Test
    fun 读数行分得开成因有无() {
        val ok = desktopReadingStatsScreenReading(
            loaded = true, note = null, trendCount = 14, topCount = 10, todayPages = 30,
        )
        assertTrue("读数该点名读到与否：$ok", "读=是" in ok)
        assertTrue("无成因时不该出现 `因=`：$ok", "因=" !in ok)
        val bad = desktopReadingStatsScreenReading(
            loaded = false, note = "库打不开", trendCount = 0, topCount = 0, todayPages = 0,
        )
        assertTrue("有成因该带上 `因=`：$bad", "因=库打不开" in bad)
    }
}
