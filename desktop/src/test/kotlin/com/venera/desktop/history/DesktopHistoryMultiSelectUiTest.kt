package com.venera.desktop.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.compose.data.db.HistoryRecord
import com.venera.desktop.setContentThemed
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 阅读历史屏多选栏的离屏语义树测试：悬停选择卡片、展示底部多选栏、批量删除回调落位。
 */
@OptIn(ExperimentalTestApi::class)
class DesktopHistoryMultiSelectUiTest {

    private fun record(id: String, title: String) = HistoryRecord(
        comicId = id,
        title = title,
        author = "作者",
        coverUrl = "",
        sourceName = "jm",
        lastChapterTitle = "第 1 话",
        lastChapterIndex = 0,
        lastPageIndex = 2,
        totalPages = 20,
        updatedAt = System.currentTimeMillis(),
    )

    @Test
    fun 悬停选择历史卡片出现多选栏并触发批量删除() {
        val deleted = mutableListOf<String>()
        val items = listOf(record("c1", "历史甲"), record("c2", "历史乙"))
        runComposeUiTest {
            setContentThemed {
                Box(Modifier.fillMaxSize()) {
                    DesktopHistoryScreen(
                        mode = WideScreenLayoutMode.Expanded,
                        records = items,
                        loaded = true,
                        note = null,
                        countNote = "2 条",
                        maskedOf = { false },
                        openableOf = { true },
                        noteOf = { null },
                        onOpen = {},
                        onClearAll = {},
                        onDeleteRecords = { list -> list.forEach { deleted += it.comicId } },
                        onBack = {},
                    )
                }
            }
            onNodeWithText("历史甲").performMouseInput { enter(center) }
            waitForIdle()
            onNodeWithContentDescription("选择 历史甲").performClick()
            onNodeWithText("已选 1").assertExists()
            onNodeWithText("删除记录 (1)").performClick()
            assertEquals(listOf("c1"), deleted)
        }
    }
}
