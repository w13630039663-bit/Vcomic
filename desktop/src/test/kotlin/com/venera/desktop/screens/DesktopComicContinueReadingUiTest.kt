package com.venera.desktop.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.compose.data.db.HistoryRecord
import com.venera.desktop.detail.DesktopComicDetailContent
import com.venera.desktop.detail.DesktopDetailChapter
import com.venera.desktop.detail.DesktopDetailViewData
import com.venera.desktop.detail.DesktopReadCapsuleData
import com.venera.desktop.detail.desktopDetailReadCapsule
import com.venera.desktop.detail.desktopDetailMarkedIndex
import com.venera.desktop.gallery.ui.DESKTOP_HISTORY_CHAPTER_MARK_DESCRIPTION
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 「开始/继续阅读」那一枚主胶囊的**离屏语义树**判据。
 *
 * ##改口径（详情页重建：下掉共享屏，桌面详情重做成核喂独立页）
 *
 * 展示件从旧仿制屏换成 [DesktopComicDetailContent]（吃 [DesktopDetailViewData] 成品数据，
 * 不吃核 —— 装载在 [com.venera.desktop.detail.DesktopComicDetailScreen]）。两格跟着搬家：
 *  - 三态里「还没读回来 / 读不成」两档由**装载态**（details == null 的整屏）盖住，
 *    Content 钉的是"没给胶囊（`readCapsule = null`）就整颗不摆"；
 *  - 「按下交出库里那两个数」由纯件 [desktopDetailReadCapsule] 钉（胶囊数据里带着那两个数），
 *    Content 钉"按一次恰好落一次续读"。
 *
 * 台架纪律沿用：一颗测试体只一层 `runComposeUiTest`；一律 `performClick` 不用
 * `performSemanticsAction(OnClick)`（后者绕过使能闸门，会钉出假绿）。
 */
@OptIn(ExperimentalTestApi::class)
class DesktopComicContinueReadingUiTest {

    private val wide = WideScreenLayoutMode.Expanded

    private val eps = listOf("第 1 话" to "e1", "第 2 话" to "e2", "第 3 话" to "e3")

    private fun record(chapterIndex: Int, chapterTitle: String, pageIndex: Int) = HistoryRecord(
        comicId = "1461316",
        title = "某本",
        author = "",
        coverUrl = "",
        sourceName = "copy_manga",
        lastChapterTitle = chapterTitle,
        lastChapterIndex = chapterIndex,
        lastPageIndex = pageIndex,
        totalPages = 89,
        updatedAt = 1_700_000_000_000L,
    )

    private fun chapters(markedIndex: Int? = null) = eps.mapIndexed { i, (name, id) ->
        DesktopDetailChapter(id = id, title = name, marked = markedIndex == i, offline = null)
    }

    private var resumes = 0
    private var opens: List<String> = emptyList()

    private fun reset() {
        resumes = 0
        opens = emptyList()
    }

    @Composable
    private fun Stage(content: @Composable () -> Unit) {
        Box(Modifier.fillMaxSize()) { content() }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.stageContent(
        readCapsule: DesktopReadCapsuleData?,
        markedIndex: Int? = null,
        chapters: List<DesktopDetailChapter> = chapters(markedIndex),
    ) {
        setContent {
            Stage {
                DesktopComicDetailContent(
                    data = DesktopDetailViewData(
                        title = "某本漫画", cover = "https://d.jpg", author = "某人", uploader = "",
                        rating = 0f, updateTime = "", likesCount = null, commentCount = null,
                        tagGroups = emptyList(), description = "一句话简介", thumbnails = emptyList(),
                        recommend = emptyList(), chapters = chapters, readCapsule = readCapsule,
                        favorited = false, favoriteBusy = false,
                        favoriteFolders = listOf("默认"), favoriteAddedFolders = emptySet(),
                        comments = emptyList(), commentsLoading = false, canSendComment = false,
                        website = "", sourceKey = "jm",
                    ),
                    mode = wide, masked = false,
                    onBack = {},
                    onResumeRead = { resumes++ },
                    onOpenChapter = { opens += it },
                    onBulkDownload = null,
                    onToggleFavoriteFolder = {},
        onOpenFavoritePanel = {},
        onToggleNetworkFavorite = { _, _ -> },
        onToggleLike = {},
                    onSearchTag = {},
                    onOpenRecommend = {},
                    onCopyWebsite = null,
                    onOpenWebsite = null,
                    onSendComment = { _, _ -> },
                    onRate = {},
                )
            }
        }
    }

    /** ⓪ 纯件：胶囊那两个数就是**库里那两个数**（越界行号被续读算式夹住）。 */
    @Test
    fun 胶囊数据交出库里那两个数() {
        val ids = eps.map { it.second }
        val capsule = desktopDetailReadCapsule(record(2, "第 3 话", 7), ids)!!
        assertEquals("有历史那一档：", true, capsule.hasHistory)
        assertEquals("起点页就是库里那一页（不是第一话第 0 页）：", 7, capsule.pageIndex)
        assertEquals("小字吃库里那一话的题名：", "第 3 话", capsule.chapterTitle)
        // 没历史：照摆（开始阅读，落第一话第 0 页）。
        val fresh = desktopDetailReadCapsule(null, ids)!!
        assertEquals(false, fresh.hasHistory)
        assertEquals(0, fresh.pageIndex)
        // 零章节：整颗不摆（按下没对象）。
        assertEquals(null, desktopDetailReadCapsule(record(0, "x", 0), emptyList()))
    }

    /** ① 有历史 ⇒ 念「继续阅读」+ 那行小字，按下恰好落一次续读。 */
    @Test
    fun 有历史时念继续阅读而按下恰好落一次() {
        reset()
        runComposeUiTest {
            stageContent(
                readCapsule = DesktopReadCapsuleData(hasHistory = true, chapterTitle = "第 3 话", pageIndex = 7, progress = 0.5f),
            )
            onNodeWithText("继续阅读").performClick()
            assertEquals("按那一枚胶囊只该落一次：", 1, resumes)
            onNodeWithText("继续 第 3 话 · 第 8 页").assertExists()
        }
    }

    /** ② 确实没历史 ⇒ 念「开始阅读」，按下也真落一次，而那行小字**不摆**（摆就是编的）。 */
    @Test
    fun 没历史时念开始阅读而那行小字不摆() {
        reset()
        runComposeUiTest {
            stageContent(readCapsule = DesktopReadCapsuleData(hasHistory = false, chapterTitle = "", pageIndex = 0, progress = 0f))
            onNodeWithText("开始阅读").performClick()
            assertEquals("按那一枚胶囊只该落一次：", 1, resumes)
            assertEquals(
                "没历史而摆一行「继续 …」就是替用户编一段进度：",
                0,
                onAllNodesWithText("继续").fetchSemanticsNodes().size,
            )
        }
    }

    /** ③ 没给胶囊（装载态盖住的「还没读回来 / 读不成」两档 + 零章节）⇒ 整颗不摆。 */
    @Test
    fun 没给胶囊时那一枚整颗不摆() {
        reset()
        runComposeUiTest {
            stageContent(readCapsule = null)
            listOf("开始阅读", "继续阅读").forEach { label ->
                assertEquals(
                    "没给胶囊时不许摆那一枚「$label」：",
                    0,
                    onAllNodesWithText(label).fetchSemanticsNodes().size,
                )
            }
        }
    }

    /** ④ 「上次读到这里」的行号 = **续读真会打开的那一行**（越界夹进表尾）—— 纯件钉。 */
    @Test
    fun 标记行号跟着续读落点走而越界夹进表尾() {
        val ids = eps.map { it.second }
        assertEquals("库里在第 3 行就挂第 3 行：", 2, desktopDetailMarkedIndex(record(2, "第 3 话", 7), ids))
        assertEquals(
            "越界那一档夹住之后的那一行（挂在按下真会打开的最后一行）：",
            2,
            desktopDetailMarkedIndex(record(9, "源站删了的那一话", 5), ids),
        )
        assertEquals("没历史而挂一枚标记 = 替用户编一段进度：", null, desktopDetailMarkedIndex(null, ids))
    }

    /** ⑤ 屏上那枚标记恰好挂一行，且不吃走那一行的点击。 */
    @Test
    fun 那枚标记只挂一行而不吃那一行的点击() {
        reset()
        runComposeUiTest {
            stageContent(
                readCapsule = DesktopReadCapsuleData(hasHistory = true, chapterTitle = "第 3 话", pageIndex = 7, progress = 0.5f),
                markedIndex = 2,
            )
            assertEquals(
                "三行章节里那枚标记只许挂一枚：",
                1,
                onAllNodesWithText(DESKTOP_HISTORY_CHAPTER_MARK_DESCRIPTION).fetchSemanticsNodes().size,
            )
            onNodeWithText("第 3 话").performClick()
            assertEquals("按那一行该进阅读器（标记只是文字，不挂自己的命中框）：", listOf("e3"), opens)
            assertEquals("而那一行不该顺带触发续读那一跳：", 0, resumes)
        }
    }

    /** ⑥ 两档文案互斥而都不许出现第二枚。 */
    @Test
    fun 两档文案互斥而都不许出现第二枚() {
        reset()
        runComposeUiTest {
            stageContent(readCapsule = DesktopReadCapsuleData(hasHistory = true, chapterTitle = "第 2 话", pageIndex = 3, progress = 0.4f))
            onAllNodesWithText("继续阅读").assertCountEquals(1)
            assertEquals("有历史时不许同时念「开始阅读」：", 0, onAllNodesWithText("开始阅读").fetchSemanticsNodes().size)
        }
        runComposeUiTest {
            stageContent(readCapsule = DesktopReadCapsuleData(hasHistory = false, chapterTitle = "", pageIndex = 0, progress = 0f))
            onAllNodesWithText("开始阅读").assertCountEquals(1)
            assertEquals("没历史时不许同时念「继续阅读」：", 0, onAllNodesWithText("继续阅读").fetchSemanticsNodes().size)
        }
    }

    /** ⑦ 一话都没有 ⇒ 胶囊不摆（纯件交回 null）而章节卡念得出空表那句话。 */
    @Test
    fun 一话都没有时那一枚胶囊不摆() {
        reset()
        runComposeUiTest {
            stageContent(readCapsule = desktopDetailReadCapsule(record(0, "x", 0), emptyList()), chapters = emptyList())
            listOf("开始阅读", "继续阅读").forEach { label ->
                assertEquals(
                    "没有章节时那一枚胶囊按下去没对象，不许摆「$label」：",
                    0,
                    onAllNodesWithText(label).fetchSemanticsNodes().size,
                )
            }
            onNodeWithText("暂无章节").assertExists()
        }
    }
}



