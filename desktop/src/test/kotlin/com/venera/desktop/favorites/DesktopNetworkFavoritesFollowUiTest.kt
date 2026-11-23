package com.venera.desktop.favorites

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.desktop.follow.NetFollowImportProgress
import com.venera.desktop.setContentThemed
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 网络收藏页「加入 / 同步追更」的离屏判据：按钮摆不摆、两段式确认、已追更角标、导入中停手。
 *
 * 屏本身零引擎调用；这里只递成品数据与回调，按语义树量结果。
 */
@OptIn(ExperimentalTestApi::class)
class DesktopNetworkFavoritesFollowUiTest {

    private val wide = WideScreenLayoutMode.Expanded

    private val source = NetFavSourceRow(key = "jm", name = "拷贝漫画", logged = true)

    private val page = NetFavPageState(
        comics = listOf(NetFavComicRow(sourceKey = "jm", comicId = "c1", title = "本一", cover = "")),
    )

    private var imports = 0
    private var cancels = 0

    private fun androidx.compose.ui.test.ComposeUiTest.stage(
        onFollowImport: (() -> Unit)? = null,
        onCancelFollowImport: (() -> Unit)? = null,
        subscribed: Boolean = false,
        following: Set<String> = emptySet(),
        progress: NetFollowImportProgress = NetFollowImportProgress(),
        onBatchUnfavorite: ((String, String?, List<NetFavComicRow>) -> Unit)? = null,
        onBatchFollow: ((List<NetFavComicRow>) -> Unit)? = null,
    ) {
        setContentThemed {
            Box(Modifier.fillMaxSize()) {
                DesktopNetworkFavoritesScreen(
                    mode = wide,
                    activeSource = source,
                    expandedPage = page,
                    folders = null,
                    onPickFolder = {},
                    maskedOf = { false },
                    onOpenComic = {},
                    onGoLogin = {},
                    onRefresh = {},
                    loadingMore = false,
                    onLoadMore = {},
                    followSubscribed = subscribed,
                    followComicIds = following,
                    followImportProgress = progress,
                    onFollowImport = onFollowImport,
                    onCancelFollowImport = onCancelFollowImport,
                    onBatchUnfavorite = onBatchUnfavorite,
                    onBatchFollow = onBatchFollow,
                    embedded = true,
                )
            }
        }
    }

    /** 没接写口 ⇒ 两档按钮都不摆（不放假把手）。 */
    @Test
    fun 未接写口时按钮整颗不摆() {
        runComposeUiTest {
            stage()
            onAllNodesWithText("加入追更").assertCountEquals(0)
            onAllNodesWithText("同步追更").assertCountEquals(0)
        }
    }

    /** 两段式：第一下只武装，第二下才真拉（防手滑把 200 本一次灌进账）。 */
    @Test
    fun 首次点只武装再点才导入() {
        imports = 0
        runComposeUiTest {
            stage(onFollowImport = { imports++ })
            onNodeWithText("加入追更").performClick()
            onNodeWithText("确认导入（上限 200 本）").assertExists()
            onAllNodesWithText("加入追更").assertCountEquals(0)
            onNodeWithText("确认导入（上限 200 本）").performClick()
            assertEquals(1, imports)
        }
    }

    /** 已订阅这夹 ⇒ 文案换成「同步追更」，同样两段式。 */
    @Test
    fun 已订阅时按钮念同步追更() {
        runComposeUiTest {
            stage(onFollowImport = {}, subscribed = true)
            onNodeWithText("同步追更").assertExists()
        }
    }

    /** 夹内已在账上 ⇒ 卡面右上角「追更中」只出现在那一张。 */
    @Test
    fun 已在追更的卡片带角标() {
        runComposeUiTest {
            stage(following = setOf("c1"))
            onAllNodesWithText("追更中").assertCountEquals(1)
        }
    }

    /** 导入进行中 ⇒ 按钮变「停止导入」并走取消，不再走二次确认。 */
    @Test
    fun 导入中按钮变停止并走取消() {
        cancels = 0
        runComposeUiTest {
            stage(
                onFollowImport = {},
                onCancelFollowImport = { cancels++ },
                progress = NetFollowImportProgress(running = true, pages = 2, fetched = 48),
            )
            onNodeWithText("停止导入").performClick()
            assertEquals(1, cancels)
        }
    }

    /** 网格与双列视图均可悬停选择卡片并触发多选栏批量加入追更与取消收藏。 */
    @Test
    fun 卡片多选栏可触发批量加入追更与取消收藏() {
        val followed = mutableListOf<String>()
        val unfaved = mutableListOf<String>()
        runComposeUiTest {
            stage(
                onBatchFollow = { rows -> rows.forEach { followed += it.comicId } },
                onBatchUnfavorite = { _, _, rows -> rows.forEach { unfaved += it.comicId } },
            )
            waitForIdle()
            onNodeWithText("本一").performMouseInput { moveTo(Offset(center.x, center.y - 80f)) }
            waitForIdle()
            onNodeWithContentDescription("选择 本一").performClick()
            onNodeWithText("已选 1").assertExists()
            onNodeWithText("加入追更 (1)").performClick()
            assertEquals(listOf("c1"), followed)

            // 切换到双列视图后悬停选择并取消收藏
            onNodeWithText("网格").performClick()
            waitForIdle()
            onNodeWithText("双列").performClick()
            waitForIdle()
            onNodeWithText("本一").performMouseInput { moveTo(center) }
            waitForIdle()
            onNodeWithContentDescription("选择 本一").performClick()
            onNodeWithText("取消收藏 (1)").performClick()
            assertEquals(listOf("c1"), unfaved)
        }
    }
}
