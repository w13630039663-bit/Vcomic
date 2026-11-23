package com.venera.desktop.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.desktop.detail.DesktopComicDetailContent
import com.venera.desktop.detail.DesktopDetailChapter
import com.venera.desktop.detail.DesktopDetailViewData
import com.venera.desktop.follow.FollowDetailUiState
import com.venera.desktop.setContentThemed
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 详情页追更按钮的离屏语义树判据：三档文案、按下真触发、没接端口时整颗不摆。
 *
 * 只渲纯展示件 [DesktopComicDetailContent]，装载与写库在 `DesktopFollowDetailPort`（另有真库判据）。
 */
@OptIn(ExperimentalTestApi::class)
class DesktopComicDetailFollowTest {

    private val wide = WideScreenLayoutMode.Expanded

    private fun data() = DesktopDetailViewData(
        title = "某本漫画", cover = "https://d.jpg", author = "某人", uploader = "",
        rating = 0f, updateTime = "", likesCount = null, commentCount = null,
        tagGroups = emptyList(), description = "一句话简介", thumbnails = emptyList(),
        recommend = emptyList(),
        chapters = listOf(DesktopDetailChapter("e1", "第 1 话", marked = false, offline = null)),
        readCapsule = null,
        favorited = false, favoriteBusy = false,
        favoriteFolders = listOf("默认"), favoriteAddedFolders = emptySet(),
        comments = emptyList(), commentsLoading = false, canSendComment = false,
        website = "", sourceKey = "jm",
    )

    private fun androidx.compose.ui.test.ComposeUiTest.stage(
        followState: FollowDetailUiState?,
        onToggleFollow: (() -> Unit)?,
    ) {
        setContentThemed {
            Box(Modifier.fillMaxSize()) {
                DesktopComicDetailContent(
                    data = data(),
                    mode = wide,
                    masked = false,
                    onBack = {},
                    onResumeRead = {},
                    onOpenChapter = {},
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
                    followState = followState,
                    onToggleFollow = onToggleFollow,
                )
            }
        }
    }

    @Test
    fun 未追更时按钮念追更并按下触发() {
        var taps = 0
        runComposeUiTest {
            stage(FollowDetailUiState(following = false, busy = false), onToggleFollow = { taps++ })
            onNodeWithText("追更").performClick()
            assertEquals(1, taps)
        }
    }

    @Test
    fun 已追更时按钮念已追更并按下触发取消() {
        var taps = 0
        runComposeUiTest {
            stage(FollowDetailUiState(following = true, busy = false), onToggleFollow = { taps++ })
            onNodeWithText("已追更").performClick()
            assertEquals(1, taps)
        }
    }

    @Test
    fun 还没读回来时按钮置灰且按不动() {
        var taps = 0
        runComposeUiTest {
            stage(FollowDetailUiState(following = null, busy = false), onToggleFollow = { taps++ })
            onNodeWithText("正在读追更…").performClick()
            assertEquals("following 未读回时不许把点击当成没追", 0, taps)
        }
    }

    @Test
    fun 没接端口时追更按钮整颗不摆() {
        runComposeUiTest {
            stage(followState = null, onToggleFollow = null)
            onAllNodesWithText("追更").assertCountEquals(0)
            onAllNodesWithText("已追更").assertCountEquals(0)
            onAllNodesWithText("正在读追更…").assertCountEquals(0)
        }
    }

    @Test
    fun 写入中按钮念正在写且按不动() {
        var taps = 0
        runComposeUiTest {
            stage(FollowDetailUiState(following = true, busy = true), onToggleFollow = { taps++ })
            onNodeWithText("正在写追更…").performClick()
            assertEquals(0, taps)
        }
    }
}



