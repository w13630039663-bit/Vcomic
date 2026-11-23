package com.venera.desktop.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.desktop.DESKTOP_READING_STATS_EMPTY_MESSAGE
import com.venera.desktop.DESKTOP_READING_STATS_EMPTY_TITLE
import com.venera.desktop.DESKTOP_READING_STATS_SCREEN_TITLE
import com.venera.desktop.DesktopReadingStats
import com.venera.desktop.DesktopReadingStatsScreen
import com.venera.desktop.ReadingStatsTopCell
import com.venera.desktop.ReadingStatsTrendCell
import org.junit.Assert.assertEquals
import org.junit.Test
import androidx.compose.ui.test.onNodeWithContentDescription

/**
 * 「阅读统计」那一屏的**离屏语义树**判据（按复刻版九组件布点，
 * 四格 KPI 取代旧三格「今日页数/本周累计/连续天数」，其余判据族不动）。
 *
 * ## 台架纪律（沿用 `DesktopComicHistoryScreenUiTest` 那几条）
 *
 *  - 一颗用例**只一层** `runComposeUiTest`（嵌套会把未捕获异常打到下一颗不相干的测试上）。
 *  - 屏只吃形参：喂现成的 [DesktopReadingStats]，不开库、不查表（那两件事由源码尺钉在屏侧文件上）。
 *  - 三档各一形：读失败 / 还没读回来 / 读了确实没记录 —— 屏上必须念不同的话。
 */
@OptIn(ExperimentalTestApi::class)
class DesktopReadingStatsUiTest {

    private val wide = WideScreenLayoutMode.Expanded

    private var backs = 0
    private fun reset() { backs = 0 }

    @Composable
    private fun Stage(content: @Composable () -> Unit) {
        Box(Modifier.fillMaxSize()) { content() }
    }

    /** 有数据的一屏：四格 KPI + 活跃度/趋势/习惯 + 榜 + 记录表都摆出来。 */
    private val populated = DesktopReadingStats(
        todayPages = 30,
        weekPages = 55,
        streakDays = 3,
        trend = listOf(
            ReadingStatsTrendCell("10-24", 0),
            ReadingStatsTrendCell("10-25", 30, seconds = 1800, works = 2),
        ),
        topComics = listOf(
            ReadingStatsTopCell(
                comicId = "c1", title = "长读的", sourceName = "拷贝漫画",
                pages = 88, durationNote = "1 小时 10 分", seconds = 4200,
            ),
        ),
        loaded = true,
        note = null,
        totalSeconds = 4200,
        totalPages = 88,
        totalComics = 1,
        activeDays = 1,
        heatPages = List(84) { 0 },
        recent = listOf(
            com.venera.desktop.ReadingStatsRecentRow("雨季的第五页", "JM", 380, "42 分钟", "今天 22:14"),
        ),
        tagStats = com.venera.compose.stats.TagStats(
            buckets = listOf(
                com.venera.compose.stats.TagStatBucket("校服", 60, 1, "female", "校服"),
            ),
            taggedComics = 2,
        ),
    )

    @Test
    fun 有数据时四格题名趋势与榜都在而返回按得到() = runComposeUiTest {
        reset()
        setContent {
            Stage {
                DesktopReadingStatsScreen(mode = wide, stats = populated, onBack = { backs++ })
            }
        }
        // 自搭页头后「阅读统计」在工具栏页名与 mac 大字各一枚（改口径）。
        onAllNodesWithText(DESKTOP_READING_STATS_SCREEN_TITLE).assertCountEquals(2)
        // 四格 KPI 各念自己的标签。
        onNodeWithText("累计阅读长").assertExists()
        onNodeWithText("阅读总量").assertExists()
        onNodeWithText("活跃天数").assertExists()
        onNodeWithText("阅读作品").assertExists()
        // 榜与记录表在 LazyColumn 视口之外：先滚过去，语义树才会组合它们。
        onNode(hasScrollAction()).performScrollToNode(hasText("长读的"))
        // 榜那一行都在（时长那句与源名拼在同一行，用子串匹配）。
        onNodeWithText("1 小时 10 分", substring = true).assertExists()
        onNodeWithText("长读的").assertExists()
        // 记录表那行的时间读数（读数出自 ReadingStatsRecentRow.atNote）。
        onNode(hasScrollAction()).performScrollToNode(hasText("今天 22:14"))
        onNodeWithText("今天 22:14").assertExists()
        // 题材/标签两卡：桶名、百分比按 taggedComics 现算（1/2 = 50%）。
        onNode(hasScrollAction()).performScrollToNode(hasText("题材偏好"))
        onNodeWithText("题材偏好").assertExists()
        onNodeWithText("50%").assertExists()
        onNodeWithText("#校服", substring = true).assertExists()
        // 返回：按一次落一次（先滚回顶部——滚到底后返回钮已出视口）。
        onNode(hasScrollAction()).performScrollToNode(hasText("回顾阅读，发现兴趣与习惯"))
        onNodeWithContentDescription("返回").performClick()
        assertEquals(1, backs)
    }

    /** 读失败那一档：只念成因，绝不摆四格/趋势/榜（那几族此时全是 0，摆出来是假账）。 */
    @Test
    fun 读失败只念成因不摆假数据() = runComposeUiTest {
        reset()
        setContent {
            Stage {
                DesktopReadingStatsScreen(
                    mode = wide,
                    stats = DesktopReadingStats.Loading.copy(loaded = true, note = "读不到阅读统计库：IllegalState 表不存在"),
                    onBack = {},
                )
            }
        }
        onNodeWithText("读不到阅读统计库", substring = true).assertExists()
        // 四格一个都不摆。
        onNodeWithText("累计阅读长").assertDoesNotExist()
        onNodeWithText("阅读作品").assertDoesNotExist()
    }

    /** 还没读回来那一档：念「正在读…」而不改口成空态那句。 */
    @Test
    fun 还没读回来念正在读而不说没记录() = runComposeUiTest {
        setContent {
            Stage {
                DesktopReadingStatsScreen(mode = wide, stats = DesktopReadingStats.Loading, onBack = {})
            }
        }
        onNodeWithText("正在读本地阅读记录…").assertExists()
        // 那句空态标题此时绝不能出现。
        onNodeWithText(DESKTOP_READING_STATS_EMPTY_TITLE).assertDoesNotExist()
    }

    /** 读了确实没记录那一档：空态那两句上屏（KPI 的 0 是真话，摆；图表族不摆）。 */
    @Test
    fun 读过而确实没记录摆空态那两句() = runComposeUiTest {
        val emptyRead = DesktopReadingStats(
            todayPages = 0, weekPages = 0, streakDays = 0,
            trend = (0 until 14).map { ReadingStatsTrendCell("d$it", 0) },
            topComics = emptyList(),
            loaded = true, note = null,
        )
        setContent {
            Stage { DesktopReadingStatsScreen(mode = wide, stats = emptyRead, onBack = {}) }
        }
        onNodeWithText(DESKTOP_READING_STATS_EMPTY_TITLE).assertExists()
        onNodeWithText(DESKTOP_READING_STATS_EMPTY_MESSAGE).assertExists()
        // 空态那两行之上，四格 KPI 仍摆（它们是 0，而"读了确实 0"是真话，与"没读"不同）。
        onNodeWithText("累计阅读长").assertExists()
    }

    /** 四格 KPI 是四枚独立题名，各恰一枚，不重复。 */
    @Test
    fun 四格题名不重复摆() {
        runComposeUiTest {
            setContent {
                Stage { DesktopReadingStatsScreen(mode = wide, stats = populated, onBack = {}) }
            }
            onAllNodesWithText("累计阅读长").assertCountEquals(1)
            onAllNodesWithText("阅读总量").assertCountEquals(1)
            onAllNodesWithText("活跃天数").assertCountEquals(1)
            onAllNodesWithText("阅读作品").assertCountEquals(1)
        }
    }
}
