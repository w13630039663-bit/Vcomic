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
import com.venera.desktop.detail.DESKTOP_BULK_DOWNLOAD_LABEL
import com.venera.desktop.detail.DesktopComicDetailContent
import com.venera.desktop.detail.DesktopDetailChapter
import com.venera.desktop.detail.DesktopDetailViewData
import com.venera.desktop.gallery.data.DesktopComicBulkOffline
import com.venera.desktop.setContentThemed
import com.venera.engineprobe.EngineSession
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 「离线下载」那一枚批量弹窗的**离屏语义树**判据。
 *
 * ##改口径（详情页重建）
 *
 * 展示件从旧仿制屏换成 [DesktopComicDetailContent]。两格跟着变：
 *  - **弹窗开关搬进 Content 里**（`showBulkDialog` 是弹窗自己的状态，旧稿的
 *    `bulkOpen`/`onBulkOpenChange` 两枚形参退役）—— 判据从"数回调落了几次"改成
 *    "量弹窗内容在语义树里出现/消失"，量的还是同一件事（入口扳得开、确认/取消真关窗）；
 *  - **空表那两档（⑪⑫）退役**：旧稿的 `bulkRows`/`bulkEmptyNote` 是壳"数本机那一棵树"
 *    的两档，核喂屏之后章节表来自详情响应、恒非空 ⇒ 没有章节就没有入口
 *    （「没有写口」② 同格盖住），空表那两句话没有摆的地方了。
 *
 * 台架事实仍成立：弹窗与背后那面章节表在离屏里共用一棵合并树（⑨ 钉着），
 * 所以「已下载」那枚徽章**只**在弹窗里出现（章行不摆下载徽章）——⑥ 靠它认弹窗那一行。
 */
@OptIn(ExperimentalTestApi::class)
class DesktopComicBulkDownloadUiTest {

    private val wide = WideScreenLayoutMode.Expanded

    private val eps = listOf(
        EngineSession.Episode("第 1 话", "e1"),
        EngineSession.Episode("第 2 话", "e2"),
        EngineSession.Episode("第 3 话", "e3"),
    )

    /** 三档各一枚：齐了 / 半截 / 没下过 ⇒ 默认选中两枚（e2、e3）。 */
    private val threeChapters = listOf(
        DesktopDetailChapter("e1", "第 1 话", marked = false, offline = DesktopComicBulkOffline.DOWNLOADED),
        DesktopDetailChapter("e2", "第 2 话", marked = false, offline = DesktopComicBulkOffline.PARTIAL),
        DesktopDetailChapter("e3", "第 3 话", marked = false, offline = DesktopComicBulkOffline.NONE),
    )

    private var bulkGot: List<EngineSession.Episode> = emptyList()
    private var opensEpisodes = 0

    private fun reset() {
        bulkGot = emptyList()
        opensEpisodes = 0
    }

    @Composable
    private fun Stage(content: @Composable () -> Unit) {
        Box(Modifier.fillMaxSize()) { content() }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.stageContent(
        chapters: List<DesktopDetailChapter> = threeChapters,
        onBulkDownload: ((List<EngineSession.Episode>) -> Unit)? = { bulkGot = it },
    ) {
        setContentThemed {
            Stage {
                DesktopComicDetailContent(
                    data = DesktopDetailViewData(
                        title = "某本漫画", cover = "https://d.jpg", author = "某人", uploader = "",
                        rating = 0f, updateTime = "", likesCount = null, commentCount = null,
                        tagGroups = emptyList(), description = "一句话简介", thumbnails = emptyList(),
                        recommend = emptyList(), chapters = chapters, readCapsule = null,
                        favorited = false, favoriteBusy = false,
                        favoriteFolders = listOf("默认"), favoriteAddedFolders = emptySet(),
                        comments = emptyList(), commentsLoading = false, canSendComment = false,
                        website = "", sourceKey = "jm",
                    ),
                    mode = wide, masked = false,
                    onBack = {},
                    onResumeRead = {},
                    onOpenChapter = { opensEpisodes++ },
                    onBulkDownload = onBulkDownload,
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

    /** ① 有写口 ⇒ 入口摆出来，按一下真把弹窗扳开（默认选中 = 全部未下载 ⇒ 扳机念「全不选」）。 */
    @Test
    fun 入口按一下真把弹窗扳开() {
        reset()
        runComposeUiTest {
            stageContent()
            onNodeWithText(DESKTOP_BULK_DOWNLOAD_LABEL).performClick()
            onNodeWithText("全不选").assertExists()
        }
    }

    /** ② 没有写口 ⇒ 入口与那扇窗**整颗都不摆**（评审 R6 那一族）。 */
    @Test
    fun 没有写口时入口与弹窗整颗不摆() {
        reset()
        runComposeUiTest {
            stageContent(onBulkDownload = null)
            onAllNodesWithText(DESKTOP_BULK_DOWNLOAD_LABEL).assertCountEquals(0)
            onAllNodesWithText("全选未下载").assertCountEquals(0)
            onAllNodesWithText("取消").assertCountEquals(0)
        }
    }

    /** ③ 三行各念一句（合并树：章行 + 弹窗行），而徽章只有弹窗里那两枚。 */
    @Test
    fun 三行各念一句而徽章只有弹窗里那两枚() {
        reset()
        runComposeUiTest {
            stageContent()
            onNodeWithText(DESKTOP_BULK_DOWNLOAD_LABEL).performClick()
            eps.forEach { onAllNodesWithText(it.name).assertCountEquals(2) }
            onAllNodesWithText("已下载").assertCountEquals(1)
            onAllNodesWithText("离线不全").assertCountEquals(1)
            // 「未下载」那一档不存在（对岸 `else -> Unit`）：没下过的不需要一枚徽章。
            onAllNodesWithText("未下载").assertCountEquals(0)
        }
    }

    /** ④ 默认只选中未下载那两话（齐了的不进），枚数直接上屏。 */
    @Test
    fun 默认只选中未下载而枚数上屏() {
        reset()
        runComposeUiTest {
            stageContent()
            onNodeWithText(DESKTOP_BULK_DOWNLOAD_LABEL).performClick()
            onNodeWithText("开始下载 (2)").assertExists()
        }
    }

    /** ⑤ 按下确认：交出选中那两话并把窗关掉。 */
    @Test
    fun 按确认交出选中的那两话并关窗() {
        reset()
        runComposeUiTest {
            stageContent()
            onNodeWithText(DESKTOP_BULK_DOWNLOAD_LABEL).performClick()
            onNodeWithText("开始下载 (2)").performClick()
            onAllNodesWithText("全选未下载").assertCountEquals(0)
        }
        assertEquals("交出该是 e2、e3 两话：", listOf("e2", "e3"), bulkGot.map { it.id })
    }

    /** ⑥ 用徽章那一串当把手点某一行的勾选框（合并树里只有它认得出弹窗那一行）。 */
    @Test
    fun 点某一行的把手翻它的勾选() {
        reset()
        runComposeUiTest {
            stageContent()
            onNodeWithText(DESKTOP_BULK_DOWNLOAD_LABEL).performClick()
            // e1 本来齐了 ⇒ 没被选中；点它那一行（把手 = 它唯一的「已下载」徽章）⇒ 加进来。
            onNodeWithText("已下载").performClick()
            onNodeWithText("开始下载 (3)").assertExists()
            // 再点一次撤掉，回到默认那两枚。
            onNodeWithText("已下载").performClick()
            onNodeWithText("开始下载 (2)").assertExists()
        }
    }

    /** ⑦ 交出去的那一串**跟着章节表的原次序**，而不是勾选的先后（队列是 FIFO）。 */
    @Test
    fun 交出那一串跟章节表次序而非勾选次序() {
        reset()
        runComposeUiTest {
            stageContent()
            onNodeWithText(DESKTOP_BULK_DOWNLOAD_LABEL).performClick()
            // 起手默认选中 e2、e3（e1 齐了没进），再手动把 **e1 最后**加进来 ⇒
            // 勾选的先后是 e2、e3、e1，而交出去的次序必须是章节表的 e1、e2、e3。
            onNodeWithText("开始下载 (2)").assertExists()
            onNodeWithText("已下载").performClick()           // +e1 → (3)
            onNodeWithText("开始下载 (3)").performClick()
        }
        assertEquals(
            "交出的次序该跟章节表（e1、e2、e3），而不是勾选的先后（e2、e3、e1）：",
            listOf("e1", "e2", "e3"), bulkGot.map { it.id },
        )
    }

    /** ⑧ 零枚时确认**按不动**（`performClick` 落不到回调），而「取消」永远按得动。 */
    @Test
    fun 零枚时确认按不动而取消仍按得动() {
        reset()
        runComposeUiTest {
            stageContent()
            onNodeWithText(DESKTOP_BULK_DOWNLOAD_LABEL).performClick()
            onNodeWithText("全不选").performClick()       // 洗成空集
            onNodeWithText("开始下载 (0)").assertExists()
            onNodeWithText("开始下载 (0)").performClick() // 该落不到
            assertEquals("零枚时不许排出任何东西：", emptyList<String>(), bulkGot.map { it.id })
            onNodeWithText("取消").performClick()
            onAllNodesWithText("全选未下载").assertCountEquals(0)
        }
    }

    /**
     * ⑨ 台架那一格事实：弹窗与背后那面章节表**共用一棵合并树**。
     *
     * 为什么把它钉成判据：本文件挑把手的口径全建在这条上（题名两枚、徽章一枚）。哪天独立窗口
     * 真的不与宿主合并了，这条会红，那时可以把 ⑥ 换回题名那一枚把手 —— 而不是让人在这儿
     * 随手 `onNodeWithText("第 1 话")` 撞到两枚后改成 `[0]` 了事（那会挑到背后那一面的行）。
     */
    @Test
    fun 台架事实弹窗与章节表在同一棵合并树里() {
        reset()
        runComposeUiTest {
            stageContent()
            onNodeWithText(DESKTOP_BULK_DOWNLOAD_LABEL).performClick()
            onAllNodesWithText("第 1 话").assertCountEquals(2)   // 章节表一枚 + 弹窗一枚
            onAllNodesWithText("已下载").assertCountEquals(1)    // 只有弹窗画徽章
        }
    }

    /** ⑩ 只有那一枚占位话时：标题念「下载整本」，行念「整本」而不是空串或那枚长号。 */
    @Test
    fun 无章那一本念下载整本而行念整本() {
        reset()
        val chapters = listOf(
            DesktopDetailChapter("1461316", "", marked = false, offline = DesktopComicBulkOffline.NONE, placeholder = true),
        )
        runComposeUiTest {
            stageContent(chapters = chapters)
            onNodeWithText(DESKTOP_BULK_DOWNLOAD_LABEL).performClick()
            onNodeWithText("下载整本").assertExists()
            onAllNodesWithText("选择下载章节").assertCountEquals(0)
            // ⚠️ 必须**整串相等**：`下载整本` 那一句含 `整本`，子串匹配会撞到两枚。
            onNodeWithText("整本", substring = false).assertExists()
            onNodeWithText("开始下载 (1)").assertExists()
            // 真按一次：占位那一话没有别的门（既不在章节表里挂把手，也只有这一枚可勾的行）。
            onNodeWithText("开始下载 (1)").performClick()
        }
        assertEquals("整本那一档也交得出去（那一级只有这一扇门）：", listOf("1461316"), bulkGot.map { it.id })
    }
}



