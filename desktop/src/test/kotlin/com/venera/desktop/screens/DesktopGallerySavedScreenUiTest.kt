package com.venera.desktop.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.compose.gallery.data.GallerySite
import com.venera.desktop.setContentThemed
import com.venera.desktop.gallery.data.DESKTOP_BROWSE_PENDING_READING
import com.venera.desktop.gallery.data.DesktopBrowseView
import com.venera.desktop.gallery.data.desktopBrowseHeaderReading
import com.venera.desktop.gallery.data.desktopBrowseRowLine
import com.venera.desktop.gallery.ui.DesktopGallerySavedScreen
import com.venera.desktop.gallery.ui.DesktopGallerySavedView
import com.venera.desktop.gallery.ui.DesktopWallCardModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「收藏与历史」合并页的**离屏语义树**判据。
 *
 * 只喂成品：收藏卡已过守卫起口、历史行已排好；读写档、取消收藏、现取整张全在壳。
 * 量的是「两视图各念各的话、分类行跟随后台站轴列全、点一张落那一张、清空要两次」。
 *
 * ⚠️ 一颗测试体只许一层 `runComposeUiTest`，要多档就平铺。
 */
@OptIn(ExperimentalTestApi::class)
class DesktopGallerySavedScreenUiTest {

    private val wide = WideScreenLayoutMode.Expanded
    private val axis = listOf(GallerySite.YANDERE, GallerySite.GELBOORU, GallerySite.PIXIV)

    @Composable
    private fun Stage(content: @Composable () -> Unit) {
        Box(Modifier.fillMaxSize()) { content() }
    }

    private fun row(site: GallerySite = GallerySite.YANDERE, id: Long = 123L, at: Long = 1_000L) =
        DesktopBrowseView(site = site, postId = id, thumbUrl = "https://p/$id.jpg", viewedAt = at)

    private fun card(uid: String = "yandere:1", title: String = "作者甲") = DesktopWallCardModel(
        title = title,
        url = "https://p/thumb.jpg",
        aspect = 1f,
        masked = false,
        uid = uid,
    )

    @Composable
    private fun StageSaved(
        view: DesktopGallerySavedView,
        favorites: List<DesktopWallCardModel> = emptyList(),
        history: List<DesktopBrowseView> = emptyList(),
        historyLoaded: Boolean = true,
        query: String = "",
        siteKey: String? = null,
        clearCount: Int = 0,
        onPickView: (DesktopGallerySavedView) -> Unit = {},
        onOpenFavorite: (DesktopWallCardModel) -> Unit = {},
        onRemoveFavorite: (DesktopWallCardModel) -> Unit = {},
        onOpenHistory: (DesktopBrowseView) -> Unit = {},
        onRemoveHistory: (DesktopBrowseView) -> Unit = {},
        onRemoveFavorites: ((List<DesktopWallCardModel>) -> Unit)? = null,
        onRemoveHistories: ((List<DesktopBrowseView>) -> Unit)? = null,
        onClear: () -> Unit = {},
    ) {
        DesktopGallerySavedScreen(
            mode = wide,
            view = view,
            onPickView = onPickView,
            query = query,
            onQueryChange = {},
            siteKey = siteKey,
            onPickSite = {},
            axis = axis,
            favorites = favorites,
            favoriteTotal = favorites.size,
            favoriteBlocked = 0,
            history = history,
            historyLoaded = historyLoaded,
            clearText = if (view == DesktopGallerySavedView.FAVORITES) "清空收藏" else "清空记录",
            clearCount = clearCount,
            onClear = onClear,
            clearNote = null,
            note = null,
            onOpenFavorite = onOpenFavorite,
            onRemoveFavorite = onRemoveFavorite,
            onOpenHistory = onOpenHistory,
            onRemoveHistory = onRemoveHistory,
            onRemoveFavorites = onRemoveFavorites,
            onRemoveHistories = onRemoveHistories,
            onBack = {},
        )
    }

    /** ① 历史档的表头念纯件原话，每张卡的描述是那一行的原话。 */
    @Test
    fun 历史表头与卡面各念纯件原话() = runComposeUiTest {
        val rows = listOf(row())
        setContent { Stage { StageSaved(view = DesktopGallerySavedView.HISTORY, history = rows) } }
        onNodeWithText(desktopBrowseHeaderReading(rows)).assertExists()
        assertEquals(
            "每张历史卡该带一句行内原话当无障碍描述：",
            1,
            onAllNodesWithContentDescription(desktopBrowseRowLine(row())).fetchSemanticsNodes().size,
        )
    }

    /** ② 空历史念「本机还没点开过图」那一档，而不是把空档扮成读不到。 */
    @Test
    fun 空历史念还没点开过图而不出卡() = runComposeUiTest {
        setContent { Stage { StageSaved(view = DesktopGallerySavedView.HISTORY, history = emptyList()) } }
        onNodeWithText("还没有浏览记录").assertExists()
        assertTrue(
            "空档里不该摆卡：",
            onAllNodesWithContentDescription(desktopBrowseRowLine(row())).fetchSemanticsNodes().isEmpty(),
        )
    }

    /** ③ 点一张历史卡落的是**那一张**。 */
    @Test
    fun 点一张历史卡落的是那一张() = runComposeUiTest {
        var opened: List<DesktopBrowseView> = emptyList()
        val a = row(id = 11L)
        val b = row(id = 22L)
        setContent {
            Stage {
                StageSaved(
                    view = DesktopGallerySavedView.HISTORY,
                    history = listOf(a, b),
                    onOpenHistory = { opened = opened + it },
                )
            }
        }
        onNodeWithContentDescription(desktopBrowseRowLine(b)).performClick()
        assertEquals("按下该恰好一次落在**那一张**：", listOf(b), opened)
    }

    /** ④ 还没读回来时念「还没读到」，且不改口成「本机还没点开过图」。 */
    @Test
    fun 还没读回来时不把未知念成没有() = runComposeUiTest {
        setContent {
            Stage { StageSaved(view = DesktopGallerySavedView.HISTORY, historyLoaded = false) }
        }
        assertTrue(
            "「还没读到」那一档该上屏：",
            onAllNodesWithText(DESKTOP_BROWSE_PENDING_READING).fetchSemanticsNodes().isNotEmpty(),
        )
        assertTrue(
            "把「还没读到」念成「本机还没点开过图」是假话：",
            onAllNodesWithText(desktopBrowseHeaderReading(emptyList())).fetchSemanticsNodes().isEmpty(),
        )
    }

    /** ⑤ 分类行跟着**后台全部站源**走：当前视图里没条目的站也照摆，数字记 0。 */
    @Test
    fun 分类行跟随后台全部站源() = runComposeUiTest {
        setContent { Stage { StageSaved(view = DesktopGallerySavedView.FAVORITES, favorites = listOf(card())) } }
        onNodeWithText("全部 1").assertExists()
        onNodeWithText("${GallerySite.YANDERE.displayName} 1").assertExists()
        // 没条目的站照样在轴上摆着，数字记 0。
        onNodeWithText("${GallerySite.GELBOORU.displayName} 0").assertExists()
        onNodeWithText("${GallerySite.PIXIV.displayName} 0").assertExists()
    }

    /** ⑥ 选中站点后只剩那一站的卡。 */
    @Test
    fun 选中站点只剩那一站的卡() = runComposeUiTest {
        val yande = card(uid = "yandere:1", title = "甲")
        val gel = card(uid = "gelbooru:2", title = "乙")
        setContent {
            Stage {
                StageSaved(
                    view = DesktopGallerySavedView.FAVORITES,
                    favorites = listOf(yande, gel),
                    siteKey = GallerySite.GELBOORU.routeKey,
                )
            }
        }
        assertTrue(
            "选中 gelbooru 后 yande.re 那张不该在：",
            onAllNodesWithContentDescription("甲").fetchSemanticsNodes().isEmpty(),
        )
        assertEquals("gelbooru 那张该在：", 1, onAllNodesWithContentDescription("乙").fetchSemanticsNodes().size)
    }

    /** ⑦ 清空要按两次：第一次只武装，第二次才真清。 */
    @Test
    fun 清空要按两次() = runComposeUiTest {
        var cleared = 0
        setContent {
            Stage {
                StageSaved(
                    view = DesktopGallerySavedView.FAVORITES,
                    favorites = listOf(card()),
                    clearCount = 1,
                    onClear = { cleared++ },
                )
            }
        }
        onNodeWithText("清空收藏").performClick()
        assertEquals("第一次按下不该真清：", 0, cleared)
        onNodeWithText("确认清空 1 条").performClick()
        assertEquals("第二次按下才清：", 1, cleared)
    }

    /** ⑧ 两枚视图把手把目标档交回去。 */
    @Test
    fun 视图把手交回目标档() = runComposeUiTest {
        var picked: DesktopGallerySavedView? = null
        setContent {
            Stage {
                StageSaved(
                    view = DesktopGallerySavedView.FAVORITES,
                    onPickView = { picked = it },
                )
            }
        }
        onNodeWithText("浏览历史").performClick()
        assertEquals("视图把手该交回历史档：", DesktopGallerySavedView.HISTORY, picked)
    }

    /** ⑨ 卡面不把地址念上屏（地址常带站方密钥）。 */
    @Test
    fun 卡面不念地址() = runComposeUiTest {
        val leaky = DesktopBrowseView(
            site = GallerySite.YANDERE,
            postId = 7L,
            thumbUrl = "https://img.example/7.jpg?api_key=SECRET",
            viewedAt = 1_000L,
        )
        setContent {
            Stage { StageSaved(view = DesktopGallerySavedView.HISTORY, history = listOf(leaky)) }
        }
        assertTrue(
            "那一屏把地址念上屏了：",
            onAllNodesWithText("api_key=SECRET", substring = true).fetchSemanticsNodes().isEmpty(),
        )
        assertTrue(
            "那一屏不许出现 https:// 那一串：",
            onAllNodesWithText("https://", substring = true).fetchSemanticsNodes().isEmpty(),
        )
    }

    /** ⑩ 收藏视图多选栏触发批量取消收藏。 */
    @Test
    fun 收藏视图多选栏可批量取消收藏() = runComposeUiTest {
        val removed = mutableListOf<String>()
        val target = card(uid = "yandere:101", title = "收藏卡一")
        setContentThemed {
            Stage {
                StageSaved(
                    view = DesktopGallerySavedView.FAVORITES,
                    favorites = listOf(target),
                    onRemoveFavorites = { list -> list.forEach { removed += it.uid } },
                )
            }
        }
        onNodeWithContentDescription("收藏卡一").performMouseInput { enter(center) }
        waitForIdle()
        onNodeWithContentDescription("选择").performClick()
        onNodeWithText("已选 1").assertExists()
        onNodeWithText("取消收藏 (1)").performClick()
        assertEquals(listOf("yandere:101"), removed)
    }

    /** ⑪ 浏览历史视图多选栏触发批量删除记录。 */
    @Test
    fun 浏览历史视图多选栏可批量删除记录() = runComposeUiTest {
        val removed = mutableListOf<Long>()
        val item = row(id = 77L)
        setContentThemed {
            Stage {
                StageSaved(
                    view = DesktopGallerySavedView.HISTORY,
                    history = listOf(item),
                    onRemoveHistories = { list -> list.forEach { removed += it.postId } },
                )
            }
        }
        onNodeWithContentDescription(desktopBrowseRowLine(item)).performMouseInput { enter(center) }
        waitForIdle()
        onNodeWithContentDescription("选择").performClick()
        onNodeWithText("已选 1").assertExists()
        onNodeWithText("删除记录 (1)").performClick()
        assertEquals(listOf(77L), removed)
    }
}
