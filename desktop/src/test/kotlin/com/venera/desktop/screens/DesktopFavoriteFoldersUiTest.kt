package com.venera.desktop.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.compose.data.db.FavoriteRecord
import com.venera.desktop.detail.DesktopComicDetailContent
import com.venera.desktop.detail.DesktopDetailChapter
import com.venera.desktop.detail.DesktopDetailViewData
import com.venera.desktop.detail.DesktopReadCapsuleData
import com.venera.desktop.favorites.DesktopFavoritesScreen
import com.venera.desktop.gallery.data.DESKTOP_FAVORITE_DEFAULT_FOLDER
import com.venera.desktop.gallery.data.desktopFavoriteFolderLine
import com.venera.desktop.gallery.data.desktopFavoriteFolderCountNote
import com.venera.desktop.gallery.data.desktopFavoriteFolderFilterNote
import com.venera.desktop.gallery.data.desktopFavoriteFolderNames
import com.venera.desktop.gallery.data.desktopFavoriteFolderTabLabel
import com.venera.desktop.gallery.data.desktopFavoriteFolderTabRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 收藏**写侧**的离屏语义树判据（详情页那一半 + 收藏屏那一半）。
 *
 * ##改口径（收藏弹窗清单化）
 *
 * 详情页那一半从旧仿制屏的「文件夹横排」换成**心形钮 + 收藏夹弹窗**（的形状）：
 * 心形钮扳开弹窗、点某一夹**即落笔**（`onToggleFavoriteFolder(夹名)`）且**弹窗不关** ——
 * 清单式勾选可以连续操作，关窗交给底部「关闭 / 完成」。旧版"点夹即关窗"的口径退役，
 * 但"按下去交出的是**被按的那一枚**"这条钉法原样。
 * 旧「没有写口 ⇒ 整排不摆」一格退役：写侧恒在核上（`core.toggleLocalFavorite`），
 * 没有夹清单那一形由弹窗里那句话如实说（④）。
 *
 * 收藏屏那一半（⑦ 起）一字未动。
 *
 * ## 台架的三格事实（沿用）
 *
 *  - `onNodeWithText` 默认**子串**匹配：弹窗里「已收藏（点这里移出）」含「收藏」两字，
 *    所以点心形钮一律在弹窗**开之前**点，且开窗后不再用「收藏」当把手；
 *  - `enabled = false` 时 `performClick` 落不到回调 ⇒ 本文件只用它钉"禁用时不落"。
 */
@OptIn(ExperimentalTestApi::class)
class DesktopFavoriteFoldersUiTest {

    private val wide = WideScreenLayoutMode.Expanded

    private val twoFolders = listOf(DESKTOP_FAVORITE_DEFAULT_FOLDER, "追更")

    private var picked: List<String> = emptyList()

    private fun reset() {
        picked = emptyList()
    }

    @Composable
    private fun Stage(content: @Composable () -> Unit) {
        Box(Modifier.fillMaxSize()) { content() }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.stageContent(
        favorited: Boolean? = false,
        favoriteBusy: Boolean = false,
        folders: List<String> = twoFolders,
        added: Set<String> = emptySet(),
        onPick: (String) -> Unit = { picked += it },
    ) {
        setContent {
            Stage {
                DesktopComicDetailContent(
                    data = DesktopDetailViewData(
                        title = "某本漫画", cover = "https://d.jpg", author = "某人", uploader = "",
                        rating = 0f, updateTime = "", likesCount = null, commentCount = null,
                        tagGroups = emptyList(), description = "一句话简介", thumbnails = emptyList(),
                        recommend = emptyList(),
                        chapters = listOf(DesktopDetailChapter("e1", "第 1 话", marked = false, offline = null)),
                        readCapsule = DesktopReadCapsuleData(hasHistory = false, chapterTitle = "", pageIndex = 0, progress = 0f),
                        favorited = favorited, favoriteBusy = favoriteBusy,
                        favoriteFolders = folders, favoriteAddedFolders = added,
                        comments = emptyList(), commentsLoading = false, canSendComment = false,
                        website = "", sourceKey = "jm",
                    ),
                    mode = wide, masked = false,
                    onBack = {},
                    onResumeRead = {},
                    onOpenChapter = {},
                    onBulkDownload = null,
                    onToggleFavoriteFolder = onPick,
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

    /** ① 心形钮真摆出来，扳开弹窗后点某一夹只进草稿且不显示已收藏，点完成才落笔。 */
    @Test
    fun 心形钮扳开弹窗而点某一夹即落笔() {
        reset()
        runComposeUiTest {
            stageContent()
            onNodeWithText("收藏").performClick()
            onNodeWithText("收藏到", substring = false).assertExists()
            onNodeWithText(DESKTOP_FAVORITE_DEFAULT_FOLDER).assertExists()
            onNodeWithText("追更").performClick()
            // 未收藏时新勾选只显示「待收藏」，不显示「已收藏」，且不提前落笔。
            onNodeWithText("待收藏").assertExists()
            onAllNodesWithText("已收藏").assertCountEquals(0)
            assertTrue("未点完成前不落笔：", picked.isEmpty())
            onNodeWithText(DESKTOP_FAVORITE_DEFAULT_FOLDER).performClick()
            onNodeWithText("完成").performClick()
        }
        assertEquals(
            "点完成后交出本次勾选的夹：",
            listOf("追更", DESKTOP_FAVORITE_DEFAULT_FOLDER),
            picked,
        )
    }

    /** ② 已收藏那一档：心形念「已收藏」，弹窗里那一夹取消勾选并点完成才移出。 */
    @Test
    fun 已收藏档弹窗里那一夹带移出口() {
        reset()
        runComposeUiTest {
            stageContent(favorited = true, added = setOf(DESKTOP_FAVORITE_DEFAULT_FOLDER))
            onNodeWithText("已收藏").assertExists()
            onNodeWithText("已收藏").performClick()
            onNodeWithText("收藏到", substring = false).assertExists()
            onNodeWithText(DESKTOP_FAVORITE_DEFAULT_FOLDER).performClick()
            onNodeWithText("将移出").assertExists()
            onNodeWithText("完成").performClick()
        }
        assertEquals("点完成后交出移出那一枚（移出语义由核判）：", listOf(DESKTOP_FAVORITE_DEFAULT_FOLDER), picked)
    }

    /** ③ 正在写那一档：心形禁用，按下去弹窗不开（连点双写那一族）。 */
    @Test
    fun 正在写那一档按不动而弹窗不开() {
        reset()
        runComposeUiTest {
            stageContent(favoriteBusy = true)
            onNodeWithText("正在写收藏…").assertExists()
            onNodeWithText("正在写收藏…").performClick()
            assertFalse("禁用档不许扳开弹窗：", runCatching { onNodeWithText("收藏到", substring = false).assertExists() }.isSuccess)
            assertTrue("禁用档不许落笔：$picked", picked.isEmpty())
        }
    }

    /** ④ 还没读回来那一档：心形念「正在读收藏…」且按不动（旧"按下去不写库"那条的延续）。 */
    @Test
    fun 还没读回来那一档按不动() {
        reset()
        runComposeUiTest {
            stageContent(favorited = null)
            onNodeWithText("正在读收藏…").assertExists()
            onNodeWithText("正在读收藏…").performClick()
            assertFalse("那一档必须按不动：写下去就是往共享表里塞一条用户没想要的收藏", runCatching { onNodeWithText("收藏到", substring = false).assertExists() }.isSuccess)
            assertTrue(picked.isEmpty())
            // 自检那一下：树真的渲出来了（否则上面是"什么都没发生"换来的绿）。
            onNodeWithText("第 1 话").assertExists()
        }
    }

    /** ⑤ 没有夹清单那一形：弹窗如实说，不摆假夹。 */
    @Test
    fun 没有夹清单时弹窗如实说() {
        reset()
        runComposeUiTest {
            stageContent(folders = emptyList())
            onNodeWithText("收藏").performClick()
            onNodeWithText("还没有收藏夹").assertExists()
            onAllNodesWithText("追更").assertCountEquals(0)
        }
    }

    /** ⑥ 点「关闭」放弃草稿：勾选后不点完成而点关闭，不落笔。 */
    @Test
    fun 两夹各交自己那一格() {
        reset()
        runComposeUiTest {
            stageContent()
            onNodeWithText("收藏").performClick()
            onNodeWithText("追更").performClick()
            onNodeWithText(DESKTOP_FAVORITE_DEFAULT_FOLDER).performClick()
            onNodeWithText("关闭").performClick()
        }
        assertTrue("点关闭放弃草稿，不应落笔：", picked.isEmpty())
    }

    /**
     * ⑦ 收藏那一屏每一格念得出**自己那一夹**（补的是显示，不是分组）。
     *
     * 为什么这条值得单独钉：详情那一屏能把一本收到别的夹里之后，这一屏若还只念题名与源名，
     * 那一次改动就落在一个没人看得见的地方 —— 用户唯一能核对的地方就是这个格子。
     * 空白那一格（库里真有那种行）念那句"没记夹名"而**不**猜成「默认」。
     */
    @Test
    fun 收藏那一屏每一格念得出自己那一夹而空白那格不猜默认() {
        reset()
        val rows = listOf(
            favCell("c1", "收在追更的那本", "追更"),
            favCell("c2", "收在默认的那本", DESKTOP_FAVORITE_DEFAULT_FOLDER),
            favCell("c3", "夹名是空的那本", ""),
        )
        runComposeUiTest {
            setContent {
                Stage {
                    DesktopFavoritesScreen(
                        mode = wide,
                        shownFavorites = rows,
                        folderTabs = desktopFavoriteFolderTabRows(
                            names = desktopFavoriteFolderNames(rows), selected = null,
                            favorites = rows, countsKnown = true,
                        ),
                        countNote = desktopFavoriteFolderCountNote(null, rows.size, rows.size, true),
                        filterNote = null, onFolderTabSelect = {},
                        loaded = true, note = null, maskedOf = { false },
                        onOpen = {}, onRemove = {},
                    )
                }
            }
            onNodeWithText(desktopFavoriteFolderLine("追更"), substring = false).assertExists()
            onNodeWithText(desktopFavoriteFolderLine(DESKTOP_FAVORITE_DEFAULT_FOLDER), substring = false)
                .assertExists()
            onNodeWithText(desktopFavoriteFolderLine(""), substring = false).assertExists()
            // 自检：三格真渲出来了（否则上面那三条可以靠"什么都没发生"绿掉）。
            // 卡面「移出」角标已按设计退役（移除走右键菜单），这里改数三格各自的题名。
            assertEquals(
                "三格三本题名：", 3,
                listOf("收在追更的那本", "收在默认的那本", "夹名是空的那本")
                    .count { onAllNodesWithText(it).fetchSemanticsNodes().size == 1 },
            )
        }
    }

    // ── ：那一排「只看哪一夹」──

    /** 那一排三枚把手（全部 + 两夹）各按一次恰好交出**自己那一档**（「全部」交出 null）。 */
    @Test
    fun 那一排三枚把手各按一次而全部那枚交出空值() {
        reset()
        val picked = mutableListOf<String?>()
        val rows = listOf(
            favCell("c1", "收在追更的那本", "追更"),
            favCell("c2", "收在默认的那本", DESKTOP_FAVORITE_DEFAULT_FOLDER),
        )
        runComposeUiTest {
            setContent {
                Stage {
                    DesktopFavoritesScreen(
                        mode = wide,
                        shownFavorites = rows,
                        folderTabs = desktopFavoriteFolderTabRows(
                            names = desktopFavoriteFolderNames(rows), selected = null,
                            favorites = rows, countsKnown = true,
                        ),
                        countNote = desktopFavoriteFolderCountNote(null, rows.size, rows.size, true),
                        filterNote = null,
                        onFolderTabSelect = { picked += it },
                        loaded = true, note = null, maskedOf = { false },
                        onOpen = {}, onRemove = {},
                    )
                }
            }
            // 三枚各点名自己那一夹的条数（「全部 2」「默认 1」「追更 1」）。
            onAllNodesWithText("全部 2 ✓").assertCountEquals(1)
            onAllNodesWithText(desktopFavoriteFolderTabLabel("默认", 1, false)).assertCountEquals(1)
            onAllNodesWithText(desktopFavoriteFolderTabLabel("追更", 1, false)).assertCountEquals(1)
            // 「全部」那一枚挑中时屏上只有两格（自检：那一排不是靠"什么都没渲"绿掉的）。
            assertEquals(
                "两格两本题名：", 2,
                listOf("收在追更的那本", "收在默认的那本")
                    .count { onAllNodesWithText(it).fetchSemanticsNodes().size == 1 },
            )

            onNodeWithText(desktopFavoriteFolderTabLabel("追更", 1, false)).performClick()
            onNodeWithText(desktopFavoriteFolderTabLabel("追更", 1, false)).performClick()
            onNodeWithText("全部 2 ✓").performClick()
            assertEquals(
                "按一次落一次而「全部」交出 null（不是交那个名字）：",
                listOf<String?>("追更", "追更", null), picked,
            )
        }
    }

    /**
     * 那一排上屏的是壳算好的那一批（**不是**整张表）。
     *
     * 这条钉的是这一批最坏的假形状：`items(favorites)` 没换成筛完那一批 —— 那一排摆着、条数念着
     * 「2 本中的 1 本」，而墙上是全表。屏上什么都对，只有筛等于没做。
     */
    @Test
    fun 挑中一夹时墙上只摆那一夹而表头念得出筛过() {
        reset()
        val all = listOf(
            favCell("c1", "收在追更的那本", "追更"),
            favCell("c2", "收在默认的那本", DESKTOP_FAVORITE_DEFAULT_FOLDER),
            favCell("c3", "也收在默认的那本", DESKTOP_FAVORITE_DEFAULT_FOLDER),
        )
        val shown = listOf(all[0])
        runComposeUiTest {
            setContent {
                Stage {
                    DesktopFavoritesScreen(
                        mode = wide,
                        shownFavorites = shown,
                        folderTabs = desktopFavoriteFolderTabRows(
                            names = desktopFavoriteFolderNames(all), selected = "追更",
                            favorites = all, countsKnown = true,
                        ),
                        countNote = desktopFavoriteFolderCountNote("追更", all.size, shown.size, true),
                        filterNote = desktopFavoriteFolderFilterNote("追更", all.size, shown.size),
                        onFolderTabSelect = {},
                        loaded = true, note = null, maskedOf = { false },
                        onOpen = {}, onRemove = {},
                    )
                }
            }
            onNodeWithText("收在追更的那本").assertExists()
            assertFalse(
                "筛过而墙上还摆着别夹的本子（那一排就是装饰）：",
                runCatching { onNodeWithText("收在默认的那本").assertExists() }.isSuccess,
            )
            assertFalse("同上，第三本也不该在：", runCatching { onNodeWithText("也收在默认的那本").assertExists() }.isSuccess)
            onNodeWithText("追更 那一夹 · 共 3 本中的 1 本", substring = false).assertExists()
            // 选中的那一枚带钩而别的档不带。
            onAllNodesWithText(desktopFavoriteFolderTabLabel("追更", 1, true)).assertCountEquals(1)
            onAllNodesWithText("全部 3 ✓").assertCountEquals(0)
        }
    }

    /**
     * 筛中一夹而那一格里没本子 ⇒ 念「这一格里还没有本子」而**不**念「还没有收藏」。
     *
     * 那一本明明收藏着，只是不在这一格里。沿用旧那句是假话，而它会劝用户去再收一遍。
     */
    @Test
    fun 筛空那一格时念的是那一格而绝不是还没收藏() {
        reset()
        val all = listOf(favCell("c1", "收在追更的那本", "追更"))
        runComposeUiTest {
            setContent {
                Stage {
                    DesktopFavoritesScreen(
                        mode = wide,
                        shownFavorites = emptyList(),
                        folderTabs = desktopFavoriteFolderTabRows(
                            names = desktopFavoriteFolderNames(all), selected = DESKTOP_FAVORITE_DEFAULT_FOLDER,
                            favorites = all, countsKnown = true,
                        ),
                        countNote = desktopFavoriteFolderCountNote(
                            DESKTOP_FAVORITE_DEFAULT_FOLDER, all.size, 0, true,
                        ),
                        filterNote = desktopFavoriteFolderFilterNote(
                            DESKTOP_FAVORITE_DEFAULT_FOLDER, all.size, 0,
                        ),
                        onFolderTabSelect = {},
                        loaded = true, note = null, maskedOf = { false },
                        onOpen = {}, onRemove = {},
                    )
                }
            }
            onNodeWithText("「默认」这一格里还没有本子。", substring = false).assertExists()
            assertFalse(
                "不许念成「还没有收藏」（那是假话而它会劝人再收一遍）：",
                runCatching { onNodeWithText("还没有收藏。在探索页点开一本，详情页可以收藏。").assertExists() }.isSuccess,
            )
            // 那一排的把手照样摆着（否则用户出不去那一档）。
            onAllNodesWithText("全部 1").assertCountEquals(1)
        }
    }

    /**
     * 还没读回来时那一排只念名字、表头一句都不念（与「正在读本地收藏…」那句同轨）。
     *
     * 这一档钉的是"把还没数念成 0"那一族：念「全部 0 ✓」会被读成库里真的一本都没有。
     */
    @Test
    fun 条数还没读回来时那一排不念零而表头一句都不念() {
        reset()
        runComposeUiTest {
            setContent {
                Stage {
                    DesktopFavoritesScreen(
                        mode = wide,
                        shownFavorites = emptyList(),
                        folderTabs = desktopFavoriteFolderTabRows(
                            names = desktopFavoriteFolderNames(emptyList()), selected = null,
                            favorites = emptyList(), countsKnown = false,
                        ),
                        countNote = desktopFavoriteFolderCountNote(null, 0, 0, false),
                        filterNote = null, onFolderTabSelect = {},
                        loaded = false, note = null, maskedOf = { false },
                        onOpen = {}, onRemove = {},
                    )
                }
            }
            onAllNodesWithText("全部 ✓").assertCountEquals(1)
            onAllNodesWithText("默认").assertCountEquals(1)
            assertEquals("不许念「全部 0」（还没数不等于 0 本）：", 0, onAllNodesWithText("全部 0 ✓").fetchSemanticsNodes().size)
            // 表头那一格是空串 ⇒ 一句都不念。
            assertEquals("还没读回来不许念「0 本」：", 0, onAllNodesWithText("0 本").fetchSemanticsNodes().size)
            // 骨架替掉了那句文案，语义上仍念「正在读本地收藏」——断言改钉语义，口径不变。
            onNodeWithContentDescription("正在读本地收藏").assertExists()
        }
    }

    private fun favCell(id: String, title: String, folder: String) = FavoriteRecord(
        comicId = id,
        title = title,
        author = "",
        coverUrl = "https://cover/$id",
        sourceName = "jm",
        folderName = folder,
    )
}



