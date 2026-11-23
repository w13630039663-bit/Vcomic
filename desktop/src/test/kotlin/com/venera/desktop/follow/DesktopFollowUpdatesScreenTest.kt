package com.venera.desktop.follow

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.compose.data.db.ComicFollowRecord
import com.venera.compose.data.db.ComicFollowStatus
import com.venera.desktop.setContentThemed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 追更中心的离屏判据：档位筛选与状态行在纯函数上钉，屏只看"摆没摆、按了有没有落到回调"。
 *
 * 台架是 `DesktopFollowUpdatesScreen` 本体，账本与进度都直接构造，不碰库、不碰引擎。
 */
@OptIn(ExperimentalTestApi::class)
class DesktopFollowUpdatesScreenTest {

    private val wide = WideScreenLayoutMode.Expanded

    private fun record(
        id: String,
        title: String = "某本",
        status: ComicFollowStatus = ComicFollowStatus.ACTIVE,
        baseline: Boolean = true,
        hasNew: Boolean = false,
        newCount: Int = 0,
        latestTitle: String = "",
        lastError: String = "",
    ) = ComicFollowRecord(
        comicId = id,
        sourceName = "jm",
        title = title,
        author = "作者",
        coverUrl = "",
        status = status,
        baselineInitialized = baseline,
        latestChapterTitle = latestTitle,
        chapterCount = if (baseline) 12 else 0,
        hasNewUpdate = hasNew,
        newChaptersCount = newCount,
        lastError = lastError,
        createdAt = 1000L,
    )

    private val records = listOf(
        record("a", "更新的", hasNew = true, newCount = 2, latestTitle = "第 12 话"),
        record("b", "正常的"),
        record("c", "暂停的", status = ComicFollowStatus.PAUSED),
        record("d", "完结的", status = ComicFollowStatus.COMPLETED),
        record("e", "出错的", lastError = "超时"),
        record("f", "未建基线", baseline = false),
    )

    // ── 纯判据 ──

    @Test
    fun 筛选档各按各的口径命中() {
        assertEquals(6, desktopFollowFiltered(records, DesktopFollowFilter.ALL).size)
        assertEquals(1, desktopFollowFiltered(records, DesktopFollowFilter.UPDATED).size)
        // 异常那本也是 ACTIVE（只挂错误、没被暂停），照样在「追更中」里。
        assertEquals(4, desktopFollowFiltered(records, DesktopFollowFilter.ACTIVE).size)
        assertEquals(2, desktopFollowFiltered(records, DesktopFollowFilter.FINISHED).size)
        assertEquals(1, desktopFollowFiltered(records, DesktopFollowFilter.ERROR).size)
        assertEquals(1, desktopFollowUnreadCount(records))
    }

    @Test
    fun 状态行按异常新更基线依次给话() {
        assertEquals("更新至：第 12 话（+2 话）", desktopFollowStatusLine(records[0]))
        assertEquals("暂无新更", desktopFollowStatusLine(records[1]))
        assertEquals("检查失败：超时", desktopFollowStatusLine(records[4]))
        assertEquals("首次检查将建立基线", desktopFollowStatusLine(records[5]))
    }

    @Test
    fun 有更新但无章节名时拿话数兜底() {
        val unknown = record("g", hasNew = true, newCount = 1, latestTitle = "")
        assertTrue(desktopFollowStatusLine(unknown).startsWith("更新至："))
        assertTrue(desktopFollowStatusLine(unknown).contains("+1"))
    }

    // ── 屏 ──

    private var checks = 0
    private var cancels = 0
    private var opens = 0
    private var reads = 0
    private var statuses = mutableListOf<ComicFollowStatus>()
    private var removes = 0

    private var batchReads = mutableListOf<String>()
    private var batchStatuses = mutableListOf<Pair<String, ComicFollowStatus>>()
    private var batchRetries = mutableListOf<String>()
    private var batchRemoves = mutableListOf<String>()

    private fun androidx.compose.ui.test.ComposeUiTest.stage(
        list: List<ComicFollowRecord> = records,
        progress: FollowCheckProgress = FollowCheckProgress(),
        withRetry: Boolean = true,
    ) {
        setContentThemed {
            Box(Modifier.fillMaxSize()) {
                DesktopFollowUpdatesScreen(
                    mode = wide,
                    records = list,
                    progress = progress,
                    onCheck = { checks++ },
                    onCancelCheck = { cancels++ },
                    onOpen = { _ -> opens++ },
                    onMarkRead = { rec ->
                        reads++
                        batchReads.add(rec.comicId)
                    },
                    onSetStatus = { rec, status ->
                        statuses.add(status)
                        batchStatuses.add(rec.comicId to status)
                    },
                    onRetry = if (withRetry) ({ _ -> }) else null,
                    onBatchRetry = if (withRetry) ({ targets -> targets.forEach { batchRetries.add(it.comicId) } }) else null,
                    onRemove = { rec ->
                        removes++
                        batchRemoves.add(rec.comicId)
                    },
                    embedded = false,
                )
            }
        }
    }

    @Test
    fun 空账本摆空态且检查按钮不可按() {
        checks = 0
        cancels = 0
        runComposeUiTest {
            stage(list = emptyList())
            onNodeWithText("还没有追更的漫画").assertExists()
            onNodeWithText("检查更新").performClick()
            assertEquals(0, checks)
            assertEquals(0, cancels)
        }
    }

    @Test
    fun 有账本时检查按钮落到回调() {
        checks = 0
        runComposeUiTest {
            stage()
            onNodeWithText("检查更新").performClick()
            assertEquals(1, checks)
        }
    }

    @Test
    fun 巡检中按钮变停止并走取消() {
        cancels = 0
        runComposeUiTest {
            stage(progress = FollowCheckProgress(running = true, total = 6, completed = 2, currentSource = "jm"))
            onNodeWithText("停止检查").performClick()
            assertEquals(1, cancels)
        }
    }

    @Test
    fun 有更新的条目带标为已读且其余不带() {
        reads = 0
        runComposeUiTest {
            stage()
            // 6 条里只有「更新的」那一本有红点 ⇒ 「标为已读」只出现一次。
            onAllNodesWithText("标为已读").assertCountEquals(1)
            onNodeWithText("标为已读").performClick()
            assertEquals(1, reads)
        }
    }

    @Test
    fun 未接巡检器时不菜单本重查() {
        runComposeUiTest {
            stage(withRetry = false)
            onAllNodesWithText("单本重查").assertCountEquals(0)
        }
    }

    @Test
    fun 取消追更落到回调() {
        removes = 0
        runComposeUiTest {
            stage()
            // 清单里每本都有一颗；按第一颗即可（LazyColumn 只组合可见项，不数总数）。
            onAllNodesWithText("取消追更").onFirst().performClick()
            assertEquals(1, removes)
        }
    }

    @Test
    fun 网格与双列视图均可选中卡片并触发底部多选栏() {
        batchRetries.clear()
        batchStatuses.clear()
        runComposeUiTest {
            stage(list = records.take(2))
            // 网格视图悬停选中第一本
            onNodeWithText("更新的").performMouseInput { moveTo(center) }
            waitForIdle()
            onNodeWithContentDescription("选择 更新的").performClick()
            onNodeWithText("已选 1").assertExists()
            onNodeWithText("重查 (1)").performClick()
            assertEquals(listOf("a"), batchRetries)

            // 切到双列视图后仍可选中并执行批量取消追更
            onNodeWithText("网格").performClick()
            waitForIdle()
            onNodeWithText("双列").performClick()
            waitForIdle()
            onNodeWithText("正常的").performMouseInput { moveTo(center) }
            waitForIdle()
            onNodeWithContentDescription("选择 正常的").performClick()
            onNodeWithText("已选 1").assertExists()
            onNodeWithText("取消追更 (1)").performClick()
            assertEquals(listOf("b"), batchRemoves)
        }
    }
}
