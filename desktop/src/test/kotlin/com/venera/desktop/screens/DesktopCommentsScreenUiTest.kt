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
import com.venera.desktop.detail.DesktopCommentsScreen
import com.venera.desktop.detail.DesktopCommentsView
import com.venera.engineprobe.DesktopComment
import org.junit.Assert.assertEquals
import org.junit.Test
import androidx.compose.ui.test.onNodeWithContentDescription

/**
 * 「详情页评论」那一屏的**离屏语义树**判据。
 *
 * 台架纪律沿用 §134/§135：一颗用例只一层 `runComposeUiTest`；屏只吃形参（喂现成的 view，不发引擎、
 * 不开协程 —— 那两件事由源码尺 ⑤ 钉在壳里）。这一批判的是**五档分别**：加载中 / 未提供 / 读失败 /
 * 读了确实没有 / 有货摆列表，外加「再读一页」只在 `hasMore` 时摆、按一次落一次。
 */
@OptIn(ExperimentalTestApi::class)
class DesktopCommentsScreenUiTest {

    private val wide = WideScreenLayoutMode.Expanded

    private var loadMores = 0
    private var backs = 0

    @Composable
    private fun Stage(content: @Composable () -> Unit) {
        Box(Modifier.fillMaxSize()) { content() }
    }

    private fun c(id: String, user: String, content: String, time: String? = null, replies: Int? = null) =
        DesktopComment(
            id = id, userName = user, avatar = null, content = content, time = time,
            replyCount = replies, score = 0, voteStatus = 0, isLiked = false,
        )

    /** 台架：喂一颗现成的 view，两个回调各数一次（写成 ComposeUiTest 扩展函数才够得着 setContent）。 */
    private fun androidx.compose.ui.test.ComposeUiTest.stage(view: DesktopCommentsView) {
        loadMores = 0
        backs = 0
        setContent {
            Stage {
                DesktopCommentsScreen(
                    mode = wide,
                    title = "某本漫画",
                    view = view,
                    onLoadMore = { loadMores++ },
                    onBack = { backs++ },
                )
            }
        }
    }

    private fun view(
        items: List<DesktopComment> = emptyList(),
        isLoading: Boolean = false,
        error: String? = null,
        canLoad: Boolean = true,
        hasMore: Boolean = false,
        loaded: Boolean = true,
    ) = DesktopCommentsView(
        items = items, isLoading = isLoading, error = error, page = if (items.isEmpty()) 0 else 1,
        maxPage = null, canLoad = canLoad, hasMore = hasMore, loaded = loaded,
    )

    @Test
    fun `加载中念那句而不念未提供`() = runComposeUiTest {
        stage(view(isLoading = true, canLoad = false, loaded = false))
        onNodeWithText("评论加载中…").assertExists()
        // 起手不能同时念「未提供」（那是问完源的结论）
        onNodeWithText("该源未提供评论加载功能").assertDoesNotExist()
    }

    @Test
    fun `未提供那一档念那句实话`() = runComposeUiTest {
        stage(view(canLoad = false, loaded = true))
        onNodeWithText("该源未提供评论加载功能").assertExists()
        // 未提供时不摆「这一本还没有评论」（那是"支持而确实没"的档位）
        onNodeWithText("这一本还没有评论").assertDoesNotExist()
    }

    @Test
    fun `读了确实没有评论才念那一句`() = runComposeUiTest {
        stage(view(items = emptyList(), canLoad = true, loaded = true))
        onNodeWithText("这一本还没有评论").assertExists()
    }

    @Test
    fun `读失败把成因原话念出来`() = runComposeUiTest {
        stage(view(error = "读不到评论：站方挂了", loaded = true))
        onNodeWithText("读不到评论：站方挂了").assertExists()
    }

    @Test
    fun `有货时逐条摆正文与作者而不念状态句`() = runComposeUiTest {
        stage(view(items = listOf(c("a", "甲", "第一条", "2026-10-01", 3), c("b", "乙", "第二条"))))
        onNodeWithText("第一条").assertExists()
        onNodeWithText("甲").assertExists()
        onNodeWithText("第二条").assertExists()
        // 回复数那一档：甲有 3 条 ⇒ 念；乙没有 ⇒ 不拿 0 冒充
        onNodeWithText("3 条回复").assertExists()
        onNodeWithText("0 条回复").assertDoesNotExist()
        // 有货时那句"还没有评论"绝不念
        onNodeWithText("这一本还没有评论").assertDoesNotExist()
    }

    @Test
    fun `到底了不摆按不动的那枚再读一页`() = runComposeUiTest {
        stage(view(items = listOf(c("a", "甲", "只有一条")), hasMore = false))
        onNodeWithText("再读一页").assertDoesNotExist()
    }

    @Test
    fun `还有下一页时摆那枚而按一次落一次`() = runComposeUiTest {
        stage(view(items = listOf(c("a", "甲", "第一条")), hasMore = true))
        onNodeWithText("再读一页").assertExists().performClick()
        assertEquals(1, loadMores)
    }

    @Test
    fun `返回按得到`() = runComposeUiTest {
        stage(view(items = listOf(c("a", "甲", "第一条"))))
        onNodeWithContentDescription("返回").assertExists().performClick()
        assertEquals(1, backs)
    }

    @Test
    fun `再读一页那一枚全局至多一枚`() = runComposeUiTest {
        // 顶栏只该摆那一枚续页把手，不许列表里每条评论各挂一枚（数量级错乱那一族）。
        stage(view(items = List(4) { c("id$it", "u", "正文$it") }, hasMore = true))
        onAllNodesWithText("再读一页").assertCountEquals(1)
    }
}
