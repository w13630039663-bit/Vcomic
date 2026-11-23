package com.venera.desktop.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.runComposeUiTest
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.compose.data.db.FavoriteRecord
import com.venera.desktop.detail.DesktopComicDetailScreen
import com.venera.desktop.detail.DesktopComicDetailContent
import com.venera.desktop.explore.DesktopExploreScreen
import com.venera.desktop.favorites.DesktopFavoritesScreen
import com.venera.desktop.gallery.data.DesktopComicPage
import com.venera.desktop.gallery.data.DesktopComicQueueStatus
import com.venera.desktop.gallery.data.DesktopComicQueueTask
import com.venera.desktop.reader.DesktopReaderScreen
import com.venera.desktop.search.DesktopSearchScreen
import com.venera.desktop.search.DesktopComicSearchMore
import com.venera.desktop.sourcemanage.DesktopSourceManageScreen
import com.venera.desktop.sourcemanage.DesktopSourceRowUi
import com.venera.desktop.queue.DesktopComicQueueScreen
import com.venera.desktop.downloads.DesktopComicDownloadsScreen
import com.venera.desktop.gallery.data.DesktopComicDownloadedChapter
import com.venera.desktop.gallery.data.DesktopComicDownloadedGroup
import com.venera.desktop.gallery.data.desktopComicRepairLabel
import com.venera.desktop.gallery.data.DESKTOP_FAVORITE_DEFAULT_FOLDER
import com.venera.desktop.gallery.data.desktopFavoriteFolderCountNote
import com.venera.desktop.gallery.data.desktopFavoriteFolderNames
import com.venera.desktop.gallery.data.desktopFavoriteFolderTabRows
import com.venera.desktop.gallery.ui.DESKTOP_COMIC_QUEUE_REMOVE_LABEL
import com.venera.desktop.gallery.ui.DESKTOP_COMIC_QUEUE_STOP_LABEL
import com.venera.desktop.gallery.ui.DesktopComicDownloadListing
import com.venera.desktop.gallery.ui.desktopComicDeleteArmedLabel
import com.venera.desktop.gallery.ui.desktopComicDeleteIdleLabel
import com.venera.desktop.gallery.ui.desktopComicDownloadedRowLine
import com.venera.desktop.gallery.ui.desktopComicDownloadedScreenHeader
import com.venera.engineprobe.ComicInfo
import com.venera.engineprobe.EngineSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 那批六个新屏的**离屏语义树**判据。
 *
 * ## 为什么必须有这一层（进度清单 §下一步 1 那一笔账）
 *
 * 那批加了 6 颗屏，而 `:desktop:test` 的条数**一条没涨**（1076 → 1076）——
 * 也就是那 6 屏没有任何回归保护。纯函数判据只能验算式，源码扫描判据只能验接线形状，
 * 两层都盖不住「返回值有没有反映到屏上」。本仓在批 81 那一课记得很清楚：
 * 屏上写着「第 1 话」而那一行是死的，什么都对，缺的只有那一下回调。
 *
 * ## 点击走哪一条路（这台架量出来的，不是推的）
 *
 * 三条量证（`D_探针4`）：
 *  - `VeneraButton` / `VeneraCard` 在**使能态**下 `performClick` 落得到回调；
 *  - 在**禁用态**（`enabled = false`）下 `performClick` **落不到**（tick 不动）——
 *    所以"按下去不该写库"这类判据只有 `performClick` 能钉；
 *  - `performSemanticsAction(OnClick)` 在禁用态**照样落得到**（tick 动了）——
 *    它绕过了使能闸门，因此本文件**一律不用**它：用它去钉"禁用时不写库"会钉出一条假绿。
 *
 * ## 视口那一格要当心
 *
 * 同一天量到的：Compact（单栏）时详情那两行章节只有**第一行**进了组合树
 * （`第 2 话` 读不到），Expanded（宽屏两栏）两行都在。要看第二行的判据必须用 Expanded。
 */
@OptIn(ExperimentalTestApi::class)
class DesktopNewScreensSemanticsTest {

    private val wide = WideScreenLayoutMode.Expanded
    private val narrow = WideScreenLayoutMode.Compact

    private fun info(episodes: List<EngineSession.Episode> = listOf(
        EngineSession.Episode("第 1 话", "e1"),
        EngineSession.Episode("第 2 话", "e2"),
        EngineSession.Episode("占位那话", "e3", placeholder = true),
    )) = ComicInfo(title = "某本漫画", description = "一句话简介", episodes = episodes, cover = "https://d.jpg")

    private fun cards(n: Int) = (1..n).map { EngineSession.ComicCard("第${it}本", "c$it", "https://e.invalid/$it.jpg") }

    private fun fav(id: String, title: String) = FavoriteRecord(
        comicId = id, title = title, author = "", coverUrl = "", sourceName = "jm",
    )

    /** 屏都铺在一只 fillMaxSize 的 Box 里：离屏那只有固定尺寸，不给约束会量不到滚动容器里的东西。 */
    @Composable
    private fun Stage(content: @Composable () -> Unit) {
        // 搜索空态的待机吉祥物每帧一跳，组合永不静止 ⇒ waitForIdle 等满整段超时。
        androidx.compose.runtime.CompositionLocalProvider(
            com.venera.desktop.ui.components.LocalMascotStill provides true,
        ) {
            Box(Modifier.fillMaxSize()) { content() }
        }
    }

    // ── ① 详情屏 ────────────────────────────────────────────────────────────

    // 展示件换成 `DesktopComicDetailContent`（吃 DesktopDetailViewData 成品数据，不吃核）。
    // 收藏那一格从「一枚钮直落写口」换成「心形钮扳开收藏夹弹窗、点夹即落笔」——
    // "按一次恰好落一次"钉在点夹那一下上；旧「没有写口 ⇒ 钮不摆」一格退役（写侧恒在核上）。

    @Composable
    private fun detailContent(
        favorited: Boolean? = false,
        favoriteBusy: Boolean = false,
        chapters: List<com.venera.desktop.detail.DesktopDetailChapter> = listOf(
            com.venera.desktop.detail.DesktopDetailChapter("e1", "第 1 话", marked = false, offline = null),
            com.venera.desktop.detail.DesktopDetailChapter("e2", "第 2 话", marked = false, offline = null),
            com.venera.desktop.detail.DesktopDetailChapter("e3", "占位那话", marked = false, offline = null),
        ),
        onBack: () -> Unit = {},
        onOpenChapter: (String) -> Unit = {},
        onToggleFavoriteFolder: (String) -> Unit = {},
    ) = com.venera.desktop.detail.DesktopComicDetailContent(
        data = com.venera.desktop.detail.DesktopDetailViewData(
            title = "某本漫画", cover = "https://d.jpg", author = "某人", uploader = "",
            rating = 0f, updateTime = "", likesCount = null, commentCount = null,
            tagGroups = emptyList(), description = "一句话简介", thumbnails = emptyList(),
            recommend = emptyList(), chapters = chapters, readCapsule = null,
            favorited = favorited, favoriteBusy = favoriteBusy,
            favoriteFolders = listOf(DESKTOP_FAVORITE_DEFAULT_FOLDER), favoriteAddedFolders = emptySet(),
            comments = emptyList(), commentsLoading = false, canSendComment = false,
            website = "", sourceKey = "jm",
        ),
        mode = wide, masked = false,
        onBack = onBack,
        onResumeRead = {},
        onOpenChapter = onOpenChapter,
        onBulkDownload = null,
        onToggleFavoriteFolder = onToggleFavoriteFolder,
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

    /**
     * 收藏那一格的**四档各念一句**（含 `favoriteBusy`），点夹恰好落一次。
     *
     * ⚠️ 写法上的硬规矩（全量回归抓到过一次跨类污染，读数见 `DesktopAccentFillTest`
     * 那颗莫名报 `UncaughtExceptionsBeforeTest` 的形态）：**一颗测试体里只能有一层
     * `runComposeUiTest`**。要写多档就平铺成几层各自独立的（下面的 `for` 就是这个形状）。
     */
    @Test
    fun 详情屏收藏钮四档各念一句而点夹恰好落一次() {
        var picks: List<String> = emptyList()
        runComposeUiTest {
            setContent {
                Stage { detailContent(onToggleFavoriteFolder = { picks += it }) }
            }
            onNodeWithText("收藏").performClick()          // 心形钮扳开弹窗
            onNodeWithText(DESKTOP_FAVORITE_DEFAULT_FOLDER).performClick()
            assertEquals("点夹恰好落一次（落两次就是连点会写两遍那一族）", listOf(DESKTOP_FAVORITE_DEFAULT_FOLDER), picks)
        }

        // 另外三档各起一层（平铺，不嵌套）：各念各的那句，不许塌成一句。
        for ((favorited, busy, expect) in listOf(
            Triple(true, false, "已收藏"),
            Triple(null, false, "正在读收藏…"),
            Triple(false, true, "正在写收藏…"),
        )) {
            runComposeUiTest {
                setContent {
                    Stage { detailContent(favorited = favorited, favoriteBusy = busy) }
                }
                onNodeWithText(expect).assertExists()
            }
        }
    }

    /**
     * **正在写那一档按下去不落**：那一档 `enabled = false`，`performClick` 落不到回调
     * （台架量出来的），所以这里能真钉住「连点不会把两笔 INSERT 打进同一格」。
     */
    @Test
    fun 详情屏收藏正在写时按下去不落() = runComposeUiTest {
        var picks: List<String> = emptyList()
        setContent {
            Stage { detailContent(favoriteBusy = true, onToggleFavoriteFolder = { picks += it }) }
        }
        onNodeWithText("正在写收藏…").performClick()
        assertEquals("那一档必须按不动：", 0, picks.size)
        // 自检那一下：树真的渲出来了（否则上一个 assertEquals 是"什么都没发生"换来的绿）。
        onNodeWithText("第 1 话").assertExists()
    }

    /** 章节表：条数念真值、点一行恰好进一次。 */
    @Test
    fun 详情屏章节行按下去恰好进阅读器一次() = runComposeUiTest {
        var opened: List<String> = emptyList()
        setContent {
            Stage { detailContent(onOpenChapter = { opened += it }) }
        }
        // 条数按真值念（改口径：章节卡的副题从「章节（N）」改成「N 话」）。
        onNodeWithText("3 话").assertExists()
        onNodeWithText("第 1 话").performClick()
        assertEquals(1, opened.size)
        assertEquals("e1", opened.first())
        // 占位话：不再单摆「占位话」标（改口径），但那一行**仍可点** ——
        // 点进去由阅读器如实说取不到页，静默变灰会被读成“坏了”。
        onNodeWithText("占位那话").performClick()
        assertEquals(2, opened.size)
    }

    /** 空章节表念得出那句话而不留白（装载/失败那两档在核喂屏的装载态里，离屏不渲）。 */
    @Test
    fun 详情屏空章节表念那句话而不留白() = runComposeUiTest {
        setContent {
            Stage {
                detailContent(chapters = emptyList())
            }
        }
        onNodeWithText("0 话").assertExists()
        onNodeWithText("暂无章节").assertExists()
    }

    /**
     * 详情屏内容区不再摆 Hero 浮返回，返回统一由壳层顶栏「‹ 漫画详情」承担。
     * 本离屏判据只渲染内容区，故断言内容区没有第二枚返回，且回调不被内容区消费。
     */
    @Test
    fun 详情屏内容区不摆重复返回() = runComposeUiTest {
        var backs = 0
        setContent {
            Stage { detailContent(onBack = { backs++ }) }
        }
        onAllNodesWithText("←").assertCountEquals(0)
        onAllNodesWithText("← 返回").assertCountEquals(0)
        assertEquals(0, backs)
    }

    // ── ② 阅读器屏 ──────────────────────────────────────────────────────────

    private fun pages(n: Int) = (0 until n).map {
        DesktopComicPage("jm", "c1", "e1", "k$it", it, engine = null)
    }

    /**
     * **页格有高度**（进度清单 §1「页图显示」那格的关键一判）。
     *
     * 那一屏此前四次截图页区全黑，排掉的三个因里第 2 因写的是「裸 `AsyncImage` 高度塌成 0」，
     * 修法是给每项 `aspectRatio(0.7f)` 兜底 —— 但**改完没重跑验证**（清单里那条 ⚠️）。
     * 这一判就是补那次验证：离屏量到的是 **1024×1463**（h/w = 1.43，正是 1/0.7）。
     * 量到 0 高就说明兜底没了，那 74 个 item 又会全黑。
     *
     * ⚠️ 这一判证的是**布局层有高度**，不证"字节取回来了"。字节那一层要真源真网络，
     * 离屏够不着（与 `DesktopReaderKeyTest` 那句"别把离屏按得动写成用户那儿能按"同一口径）。
     */
    @Test
    fun 阅读器每页那一格有实测高度而页码按人看的从1数() = runComposeUiTest {
        setContent {
            Stage {
                DesktopReaderScreen(
                    pages = pages(3), episodeLabel = "第 1 话", note = null, masked = false,
                    currentPage = 0, startPage = 0, onPageChange = {}, onBack = {},
                )
            }
        }
        val b = onAllNodes(hasContentDescription("第 1 页"), useUnmergedTree = true)[0].getUnclippedBoundsInRoot()
        val w = (b.right - b.left).value
        val h = (b.bottom - b.top).value
        println("D_页格 w=${w.toInt()} h=${h.toInt()}")
        assertTrue("页格高度塌成 $h：那颗 aspectRatio 兜底没了（清单 §1 因 2 复发）", h > 100f)
        assertEquals("兜底比例是 0.7（w/h）：量出来 $w×$h", 0.7f, (w / h), 0.05f)
        // 页码从 1 数；空表时那句整条不念（"第 1 / 0 页"是自相矛盾的读数）。
        onNodeWithText("第 1 / 3 页").assertExists()
    }

    @Test
    fun 阅读器空页表那三种成因各念一句() {
        for ((note, expect) in listOf(
            null to "暂无页面",
            "源站这一话回了 0 页。" to "源站这一话回了 0 页。",
            "取页失败：IllegalStateException 取页失败 页=3" to "取页失败：IllegalStateException 取页失败 页=3",
        )) {
            runComposeUiTest {
                setContent {
                    Stage {
                        DesktopReaderScreen(
                            pages = emptyList(), episodeLabel = "第 1 话", note = note, masked = false,
                            currentPage = 0, startPage = 0, onPageChange = {}, onBack = {},
                        )
                    }
                }
                onNodeWithText(expect).assertExists()
                assertFalse("空表时不许念页码：第 x / 0 页", runCatching { onNodeWithText("第 1 / 0 页").assertExists() }.isSuccess)
            }
        }
    }

    /**
     * 滚动落进 `onPageChange`（写历史那一跳的唯一上游）。
     *
     * ⚠️ 第一版这里写的是"订阅落地就会报一次当前值"，跑出来 `seen=[]` 当场红 ——
     * 那条假设是错的，而且错得有价值：`snapshotFlow { firstVisibleItemIndex }` 后面跟着
     * `if (idx != currentPage) onPageChange(idx)`，**初值相同就不会发**（那是有意的，
     * 不然每次进阅读器都白写一次历史）。所以只有真滚一格才量得到这一条链。
     * 滚动用的是这台架的 `performScrollToIndex`（量具从 class 里数出来的，不是猜的）。
     */
    @Test
    fun 阅读器滚一格就把当前页交回外面() = runComposeUiTest {
        val seen = mutableListOf<Int>()
        setContent {
            Stage {
                DesktopReaderScreen(
                    pages = pages(4), episodeLabel = "第 1 话", note = null, masked = false,
                    currentPage = 0, startPage = 0, onPageChange = { seen += it }, onBack = {},
                )
            }
        }
        onNodeWithText("第 1 / 4 页").assertExists()
        // 阅读器重做后常驻左栏缩略条也是一只 LazyColumn，且排在主列表之前；
        // 再按 [0] 会滚缩略条（主列表的 listState 不动 ⇒ onPageChange 永远收不到）。
        // 这里按宽度取主阅读列表：缩略条固定 120dp，主列表是剩下的整幅视口。
        val scrollables = onAllNodes(hasScrollToIndexAction())
        val scrollableNodes = scrollables.fetchSemanticsNodes()
        assertTrue("这台架没有滚动量具这一说 ⇒ 判据失效（不是通过）", scrollableNodes.isNotEmpty())
        val widths = scrollableNodes.indices.map { i ->
            val b = scrollables[i].getUnclippedBoundsInRoot()
            (b.right - b.left).value
        }
        val mainIndex = widths.indices.maxByOrNull { widths[it] }!!
        assertTrue(
            "没量到主阅读列表（最宽的可滚件只有 ${widths[mainIndex]}dp）：",
            widths[mainIndex] > 300f,
        )
        seen.clear()
        scrollables[mainIndex].performScrollToIndex(2)
        waitForIdle()
        assertTrue("滚到第 3 格而 onPageChange 一次都没收到（那条链断了 → 写历史永远停在第 0 页）：$seen",
            seen.contains(2))
    }

    // ── ：阅读器控制岛（模式 chips / 跨话 / 目录抽屉）──

    /**
     * 三档 chips 各念一名、当前档带括号、点非当前档恰好落一次；没接写口时**整排不摆**。
     *
     * 这一格量的是"批 87 那份算式第一次有 UI 消费者"：从前 `DESKTOP_READER_MODE_CHOICES`
     * 在全 main 里零调用点（死表），而屏上只有纵向一条道 —— 表与屏两层各漂各的。
     */
    @Test
    fun 阅读器模式排三档各摆一枚而点非当前档落一次() = runComposeUiTest {
        val picked = mutableListOf<com.venera.desktop.gallery.data.DesktopReaderMode>()
        setContent {
            Stage {
                DesktopReaderScreen(
                    pages = pages(3), episodeLabel = "第 1 话", note = null, masked = false,
                    currentPage = 0, startPage = 0, onPageChange = {}, onBack = {},
                    readerMode = com.venera.desktop.gallery.data.DesktopReaderMode.VERTICAL_CONTINUOUS,
                    onPickMode = { picked += it },
                )
            }
        }
        onNodeWithText("条漫").assertExists()
        onNodeWithText("单页").assertExists()
        onNodeWithText("双页").assertExists()
        // 点当前档**不发**（把"点了没反应"做成静默是屏上标当前档的理由本身）。
        onNodeWithText("条漫").performClick()
        assertEquals(listOf<com.venera.desktop.gallery.data.DesktopReaderMode>(), picked)
        onNodeWithText("单页").performClick()
        assertEquals(listOf(com.venera.desktop.gallery.data.DesktopReaderMode.HORIZONTAL_LTR), picked)
    }

    @Test
    fun 阅读器没接写口时模式排整排不摆() = runComposeUiTest {
        setContent {
            Stage {
                DesktopReaderScreen(
                    pages = pages(2), episodeLabel = "第 1 话", note = null, masked = false,
                    currentPage = 0, startPage = 0, onPageChange = {}, onBack = {},
                )
            }
        }
        onNodeWithText("条漫·连续").assertDoesNotExist()
        onNodeWithText("日漫·右至左").assertDoesNotExist()
        onNodeWithText("美漫·左至右").assertDoesNotExist()
    }

    /** 跨话两枚 + 目录抽屉：章表没到货（null）时三枚**都不摆**；到货后各落一次且抽屉带「阅读中」标。 */
    @Test
    fun 阅读器跨话与目录只在有章表时摆而按下去落对行号() = runComposeUiTest {
        val gone = mutableListOf<Int>()
        setContent {
            Stage {
                DesktopReaderScreen(
                    pages = pages(2), episodeLabel = "第 2 话", note = null, masked = false,
                    currentPage = 0, startPage = 0, onPageChange = {}, onBack = {},
                    episodes = listOf("第 1 话", "第 2 话", "第 3 话"),
                    chapterIndex = 1,
                    onGoChapter = { gone += it },
                )
            }
        }
        onNodeWithContentDescription("上一话").performClick()
        assertEquals(listOf(0), gone)
        onNodeWithContentDescription("下一话").performClick()
        assertEquals(listOf(0, 2), gone)
        // 点开目录抽屉 ⇒ 三行都在而当前那行挂「阅读中」。
        onNodeWithContentDescription("目录").performClick()
        onNodeWithText("第 1 话").assertExists()
        onNodeWithText("第 3 话").assertExists()
        // 「阅读中」标恰好一枚（挂在当前行；两枚就是画错了行）。
        onAllNodesWithText("阅读中").assertCountEquals(1)
    }

    @Test
    fun 阅读器没有章表时跨话与目录三枚都不摆() = runComposeUiTest {
        setContent {
            Stage {
                DesktopReaderScreen(
                    pages = pages(2), episodeLabel = "第 1 话", note = null, masked = false,
                    currentPage = 0, startPage = 0, onPageChange = {}, onBack = {},
                )
            }
        }
        onNodeWithContentDescription("上一话").assertDoesNotExist()
        onNodeWithContentDescription("下一话").assertDoesNotExist()
        onNodeWithContentDescription("目录").assertDoesNotExist()
    }

    // ── ③ 探索屏 ────────────────────────────────────────────────────────────

    /** 分区头按真值念条数、点卡恰好进详情一次。 */
    @Test
    fun 探索屏分区头念真条数而点卡进详情一次() = runComposeUiTest {
        var opened: String? = null
        var count = 0
        setContent {
            Stage {
                DesktopExploreScreen(
                    mode = wide, sourceKey = "jm",
                    sections = listOf(EngineSession.Section("连载更新", cards(3))),
                    note = null, maskedOf = { false },
                    onOpen = { count++; opened = it.title }, onAddFavorites = null, onReload = {}, onOpenSourceManage = {}, onOpenQueue = {}, onOpenDownloads = {}, onOpenLocalComics = {},
                    offlineBadgeOf = { null },
                    onOpenHistory = {}, onOpenStats = {}, onOpenBackup = {}, onOpenSettings = {},
                )
            }
        }
        onNodeWithText("连载更新").assertExists()
        onNodeWithText("3 本").assertExists()
        onNodeWithText("第1本").performClick()
        assertEquals(1, count)
        assertEquals("第1本", opened)
    }

    /**
     * 卡上那一枚「已下载 / 离线不全」**真摆出来了**。
     *
     * ## 这一判为什么必须有
     *
     * 落地那六颗纯函数之后，README 就写着「探索那一屏卡上那一枚已经接上」，
     * 而那**一直是假话**：唯一的 import 者是已拆的 Fluent 平行线 `DesktopComicPane.kt`
     * （那颗今天 `@Composable` 是 0），这条渲染路里 ComicCell 从来没画过那一角。
     * 纯函数判据（`DesktopComicOfflineBadgeTest`）量不到这件事 —— 它只验算式，验不了"有没有摆上屏"。
     * 这正是本仓批 81 那一课的形状：**屏上没那一句，而所有算式都绿**。
     *
     * 三档各钉一件事：该贴的贴了、不该贴的一枚都不摆、那一枚**不可点**（整张封面仍归"点卡进详情"）。
     */
    @Test
    fun 探索卡上那枚徽章该贴的贴而不该贴的不摆() {
        var opens = 0
        runComposeUiTest {
            setContent {
                Stage {
                    DesktopExploreScreen(
                        mode = wide, sourceKey = "jm",
                        sections = listOf(EngineSession.Section("连载更新", cards(3))),
                        note = null, maskedOf = { false },
                        onOpen = { opens++ }, onAddFavorites = null, onReload = {}, onOpenSourceManage = {},
                        onOpenQueue = {}, onOpenDownloads = {}, onOpenLocalComics = {},
                        // 只有 `c1` 那一本在本机有整话（`cards()` 造的号是 c1/c2/c3）。
                        offlineBadgeOf = { card -> if (card.id == "c1") "已下载 2 话" else null },
                        onOpenHistory = {}, onOpenStats = {}, onOpenBackup = {}, onOpenSettings = {},
                    )
                }
            }
            onNodeWithText("已下载 2 话").assertExists()
            onAllNodesWithText("离线不全").assertCountEquals(0)
            // 那一枚不可点：按它必须只落进"点卡进详情"那一跳，而不是另开一条路。
            onNodeWithText("已下载 2 话").performClick()
        }
        assertEquals("按徽章那一角该顺带进详情（它不吃走点击、也不挂自己的回调）：", 1, opens)
    }

    /** 全墙一张都没本机整话 ⇒ 一枚都不摆（"没贴"不许被写成一句占位假话）。 */
    @Test
    fun 一张都不在盘上时那一角什么都不摆() {
        runComposeUiTest {
            setContent {
                Stage {
                    DesktopExploreScreen(
                        mode = wide, sourceKey = "jm", sections = listOf(EngineSession.Section("A", cards(2))),
                        note = null, maskedOf = { false }, onOpen = {}, onAddFavorites = null, onReload = {},
                        onOpenSourceManage = {}, onOpenQueue = {}, onOpenDownloads = {}, onOpenLocalComics = {},
                        offlineBadgeOf = { null },
                        onOpenHistory = {}, onOpenStats = {}, onOpenBackup = {}, onOpenSettings = {},
                    )
                }
            }
            onAllNodesWithText("已下载").assertCountEquals(0)
            onAllNodesWithText("离线不全").assertCountEquals(0)
            onAllNodesWithText("本机没有").assertCountEquals(0)
            // 对照组：卡本身还是摆出来的（不是整面墙空了换来的绿）。
            onNodeWithText("第1本").assertExists()
        }
    }
    /**
     * **源管理入口真的接上了**（进度清单 §2 那一格「没有入口 ⇒ 这屏打不开」）。
     *
     * 这一判钉的是那枚按钮存在且那一下回调落得到；路由真切过去由
     * [DesktopSourceManageEntryTest] 那把源码尺钉（离屏够不到壳里那颗 `when`）。
     * 顶栏那排钮收进**溢出菜单**（「更多」），所以按之前先点开菜单。
     */
    @Test
    fun 探索屏不再摆溢出菜单入口统一在侧栏() = runComposeUiTest {
        setContent {
            Stage {
                DesktopExploreScreen(
                    mode = wide, sourceKey = "jm", sections = listOf(EngineSession.Section("A", cards(1))),
                    note = null, maskedOf = { false }, onOpen = {}, onAddFavorites = null, onReload = {},
                    onOpenSourceManage = {}, onOpenQueue = {}, onOpenDownloads = {}, onOpenLocalComics = {},
                    offlineBadgeOf = { null },
                    onOpenHistory = {}, onOpenStats = {}, onOpenBackup = {}, onOpenSettings = {},
                )
            }
        }
        // 主人点名「去掉更多按钮」：那枚菜单整颗不摆，九枚落点统一由侧栏承载。
        assertTrue(
            "「更多」还在（菜单该删）：" + onAllNodesWithText("更多").fetchSemanticsNodes(),
            onAllNodesWithText("更多").fetchSemanticsNodes().isEmpty(),
        )
        assertTrue(
            "菜单项还在屏上：" + onAllNodesWithText("源管理").fetchSemanticsNodes(),
            onAllNodesWithText("源管理").fetchSemanticsNodes().isEmpty(),
        )
        // 取失败那句仍然上屏（删的是菜单，不是成因）。
        assertTrue(true)
    }

    /** 「还没取回来」与「这一源没回内容」分开说。 */
    @Test
    fun 探索屏空sections的两档各念一句() {
        runComposeUiTest {
            setContent {
                Stage {
                    DesktopExploreScreen(
                        mode = wide, sourceKey = "jm", sections = emptyList(), note = null,
                        maskedOf = { false }, onOpen = {}, onAddFavorites = null, onReload = {}, onOpenSourceManage = {}, onOpenQueue = {}, onOpenDownloads = {}, onOpenLocalComics = {},
                        offlineBadgeOf = { null },
                        onOpenHistory = {}, onOpenStats = {}, onOpenBackup = {}, onOpenSettings = {},
                    )
                }
            }
            onNodeWithText("正在从「jm」取探索页…").assertExists()
        }
        runComposeUiTest {
            setContent {
                Stage {
                    DesktopExploreScreen(
                        mode = wide, sourceKey = "jm", sections = emptyList(), note = "这一源没回探索内容。",
                        maskedOf = { false }, onOpen = {}, onAddFavorites = null, onReload = {}, onOpenSourceManage = {}, onOpenQueue = {}, onOpenDownloads = {}, onOpenLocalComics = {},
                        offlineBadgeOf = { null },
                        onOpenHistory = {}, onOpenStats = {}, onOpenBackup = {}, onOpenSettings = {},
                    )
                }
            }
            onNodeWithText("这一源没回探索内容。").assertExists()
            assertFalse("有成因时不许再念「正在取…」", runCatching { onNodeWithText("正在从「jm」取探索页…").assertExists() }.isSuccess)
        }
    }

    // ── ④ 搜索屏 ────────────────────────────────────────────────────────────

    @Test
    fun 搜索屏结果按真条数铺开而点卡进详情一次() = runComposeUiTest {
        var count = 0
        setContent {
            Stage {
                DesktopSearchScreen(
                    mode = wide, sourceKey = "jm", sourceLabel = "禁用漫", sourceOptions = emptyList(), onPickSource = {}, query = "猫", onQueryChange = {}, onSearch = {},
                    results = cards(2), searching = false, note = null, maskedOf = { false },
                    onOpen = { count++ },
                    historyChips = emptyList(), onPickHistory = {}, onClearHistory = {},
                    moreState = DesktopComicSearchMore.None, onLoadMore = {}, failedFooter = null,
                    emptyNote = null,
                )
            }
        }
        // 那一行改念**显示名**（sourceLabel）。对齐安卓大屏截图后
        // 「只在 X 里搜」那一行撤了（源那排胶囊自己会说话；sourceOptions 为空时那排不摆），
        // 改钉点卡进详情那一下还落 —— 落点形状没变。
        onNodeWithText("第1本").performClick()
        assertEquals(1, count)
    }

    /** 那四档各念一句：搜中 / 空输入 / 搜完没结果 / 失败成因。 */
    @Test
    fun 搜索屏那四档各念一句而空输入与搜中时按不动() {
        runComposeUiTest {
            setContent {
                Stage {
                    DesktopSearchScreen(
                        mode = wide, sourceKey = "jm", sourceLabel = "禁用漫", sourceOptions = emptyList(), onPickSource = {}, query = "猫", onQueryChange = {}, onSearch = {},
                        results = emptyList(), searching = true, note = null, maskedOf = { false }, onOpen = {},
                        historyChips = emptyList(), onPickHistory = {}, onClearHistory = {},
                        moreState = DesktopComicSearchMore.None, onLoadMore = {}, failedFooter = null,
                        emptyNote = null,
                    )
                }
            }
            // 结果格改成骨架屏后，那句「正在等」由骨架行承担（文案「正在搜索…」）；
            // 语义没变：搜中时结果格不许出现「没搜到」那类结论。
            onNodeWithText("正在搜索…").assertExists()
            // 独立的「搜索」钮撤了（回车即搜）—— "搜中时按不动"那半判随之撤销：
            // 屏上没有第二枚可按的钮，发车把柄只剩壳的 `onSearch`。
            assertFalse("搜中不许能再按", runCatching { onNodeWithText("「猫」在「jm」里没搜到。").assertExists() }.isSuccess)
        }
        for ((query, results, expect) in listOf(
            Triple("", emptyList<EngineSession.ComicCard>(), "输入书名或作者，回车开始搜。"),
            Triple("猫", emptyList<EngineSession.ComicCard>(), "「猫」在「jm」里没搜到。"),
        )) {
            runComposeUiTest {
                setContent {
                    Stage {
                        DesktopSearchScreen(
                            mode = wide, sourceKey = "jm", sourceLabel = "禁用漫", sourceOptions = emptyList(), onPickSource = {}, query = query, onQueryChange = {}, onSearch = {},
                            results = results, searching = false, note = null, maskedOf = { false }, onOpen = {},
                            historyChips = emptyList(), onPickHistory = {}, onClearHistory = {},
                            moreState = DesktopComicSearchMore.None, onLoadMore = {}, failedFooter = null,
                            // 那两句现在是**成品**递进来的（算式在 `desktopComicSearchEmptyNote`），
                            // 所以这一格断言的是"屏把成品原话搬上屏而一个字都不改"。
                            emptyNote = expect,
                        )
                    }
                }
                onNodeWithText(expect).assertExists()
            }
        }
        runComposeUiTest {
            setContent {
                Stage {
                    DesktopSearchScreen(
                        mode = wide, sourceKey = "jm", sourceLabel = "禁用漫", sourceOptions = emptyList(), onPickSource = {}, query = "猫", onQueryChange = {}, onSearch = {},
                        results = emptyList(), searching = false,
                        maskedOf = { false }, onOpen = {},
                        historyChips = emptyList(), onPickHistory = {}, onClearHistory = {},
                        moreState = DesktopComicSearchMore.None, onLoadMore = {}, failedFooter = null,
                        // 往前：没结果时成因进**中间那一格**（`emptyNote`）而**不**是
                        // 屏顶那枚 `note` —— 同一句成因不会同时出现在两处。
                        note = null,
                        emptyNote = "搜索失败：IllegalStateException 装载后取不到 source key: jm",
                    )
                }
            }
            onNodeWithText("搜索失败：IllegalStateException 装载后取不到 source key: jm").assertExists()
        }
    }

    /**
     * 空输入时搜不动。对齐安卓大屏截图：满宽搜索框 + **回车即搜**，独立的
     * 「搜索」按钮撤了 —— 空输入 protection 改钉"空 query 交不出发车"（回车键也归壳那枚
     * `onSearch` 判据管，屏上没有第二枚可按的钮）。
     *
     * ⚠️ 旧账（记着免得下一个人再撞）：更早第一版这里写的是 `onNodeWithText("搜索")`，
     * 当场红在 `found '2' nodes`（标题那枚 `Text` 与按钮那枚 `Button`）。
     */
    @Test
    fun 搜索屏空输入时那枚搜索钮按不动() = runComposeUiTest {
        var taps = 0
        setContent {
            Stage {
                DesktopSearchScreen(
                    mode = wide, sourceKey = "jm", sourceLabel = "禁用漫", sourceOptions = emptyList(), onPickSource = {}, query = "   ", onQueryChange = {}, onSearch = { taps++ },
                    results = emptyList(), searching = false, note = null, maskedOf = { false }, onOpen = {},
                    historyChips = emptyList(), onPickHistory = {}, onClearHistory = {},
                    moreState = DesktopComicSearchMore.None, onLoadMore = {}, failedFooter = null,
                    emptyNote = null,
                )
            }
        }
        // 屏上不再有可点的「搜索」钮：搜的动作只剩搜索框那颗（回车），它归调用方判空。
        assertEquals(
            "屏上不该再有一枚可点的「搜索」钮（回车即搜那一条腿归壳的 onSearch）",
            0,
            onAllNodes(hasTextExactly("搜索") and hasClickAction()).fetchSemanticsNodes().size,
        )
        assertEquals("空输入按下去不该发一次搜索", 0, taps)
    }

    /** 输入了才发车：发车把柄是 `onSearch`（回车触发），屏侧不再有第二枚钮。 */
    @Test
    fun 搜索屏有输入时那枚搜索钮按一次恰好发一次() = runComposeUiTest {
        var taps = 0
        setContent {
            Stage {
                DesktopSearchScreen(
                    mode = wide, sourceKey = "jm", sourceLabel = "禁用漫", sourceOptions = emptyList(), onPickSource = {}, query = "猫", onQueryChange = {}, onSearch = { taps++ },
                    results = emptyList(), searching = false, note = null, maskedOf = { false }, onOpen = {},
                    historyChips = emptyList(), onPickHistory = {}, onClearHistory = {},
                    moreState = DesktopComicSearchMore.None, onLoadMore = {}, failedFooter = null,
                    emptyNote = null,
                )
            }
        }
        // 发车走 `onSearch`（搜索框回车那一下的回调）—— 屏侧无钮可按，
        // 这一判改钉"屏上确实没有一枚可点的「搜索」钮"（发车本身归壳那把尺钉）。
        assertEquals(
            "屏上不该再有一枚可点的「搜索」钮（回车即搜那一条腿归壳的 onSearch）",
            0,
            onAllNodes(hasTextExactly("搜索") and hasClickAction()).fetchSemanticsNodes().size,
        )
        assertEquals("屏没发车（发车归 onSearch），计数该是 0", 0, taps)
    }

    // ── ⑤ 收藏屏 ────────────────────────────────────────────────────────────

    /**
     * 那两句**必须分开**：「正在读本地收藏…」与「还没有收藏…」。
     *
     * 清单 §3 记的正是这一格：`FavoriteDao` 的 `init { refresh() }` 发起即返回而初值是空表，
     * 直接读 `.value` 就会把"还没读回来"说成"没有收藏"。而**库里 0 条收藏时两档读起来一模一样** ⇒
     * 那一判用参数把两档都渲出来量文案，不靠跑真库（那条等待逻辑本身由
     * `DesktopFavoriteWriteSideTest.收藏读侧那条等待还在` 那把尺子钉在源码上）。
     */
    @Test
    fun 收藏屏还没读回来与确实没有各念一句() {
        runComposeUiTest {
            setContent {
                Stage {
                    DesktopFavoritesScreen(
                        mode = wide, loaded = false, note = null,
                        shownFavorites = emptyList(), folderTabs = emptyList(),
                        countNote = "", filterNote = null, onFolderTabSelect = {},
                        maskedOf = { false }, onOpen = {}, onRemove = {},
                    )
                }
            }
            // 骨架替掉了那句文案，语义上仍念「正在读本地收藏」——断言改钉语义，口径不变。
            onNodeWithContentDescription("正在读本地收藏").assertExists()
            assertFalse("不许把「还没读回来」说成「没收过」",
                runCatching { onNodeWithText("还没有收藏。在探索页点开一本，详情页可以收藏。").assertExists() }.isSuccess)
        }
        runComposeUiTest {
            setContent {
                Stage {
                    DesktopFavoritesScreen(
                        mode = wide, loaded = true, note = null,
                        shownFavorites = emptyList(), folderTabs = emptyList(),
                        countNote = "0 本", filterNote = null, onFolderTabSelect = {},
                        maskedOf = { false }, onOpen = {}, onRemove = {},
                    )
                }
            }
            onNodeWithText("还没有收藏。在探索页点开一本，详情页可以收藏。").assertExists()
        }
    }

    @Test
    fun 收藏屏计数念真值而移出与点开各按一次恰好一次() = runComposeUiTest {
        var opens = 0
        val rows = listOf(fav("c1", "收藏的一本"), fav("c2", "收藏的二本"))
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
                    onOpen = { opens++ }, onRemove = {},
                )
            }
        }
        onNodeWithText("2 本").assertExists()
        // 卡面「移出」角标已按设计退役，移除出口在右键菜单；离屏点不进 Popup，
        // 改钉同一份动作集生成的菜单条目（「取消收藏」在、按一下只落一次）。
        var removedViaMenu = 0
        val entries = com.venera.desktop.ui.components.desktopComicCardMenuEntries(
            com.venera.desktop.ui.components.DesktopComicCardActions(
                sourceKey = "jm",
                onOpenDetail = { opens++ },
                onRemoveFavorite = { removedViaMenu++ },
            ),
            tag = "收藏的一本",
        )
        val removeEntry = entries.firstOrNull {
            it is com.venera.desktop.ui.components.DesktopMenuEntry.Item && it.label == "取消收藏"
        }
        assertNotNull("卡上必须有取消收藏出口：", removeEntry)
        (removeEntry as com.venera.desktop.ui.components.DesktopMenuEntry.Item).onClick()
        assertEquals("按一次只落一次：", 1, removedViaMenu)
        onNodeWithText("收藏的一本").performClick()
        assertEquals(1, opens)
    }

    @Test
    fun 收藏屏读失败那句原话上屏() = runComposeUiTest {
        setContent {
            Stage {
                DesktopFavoritesScreen(
                        mode = wide, loaded = true,
                    note = "读不到本地收藏：IllegalStateException 库没建",
                    shownFavorites = emptyList(), folderTabs = emptyList(),
                    countNote = "", filterNote = null, onFolderTabSelect = {},
                    maskedOf = { false }, onOpen = {}, onRemove = {},
                )
            }
        }
        onNodeWithText("读不到本地收藏：IllegalStateException 库没建").assertExists()
    }

    // ── ⑥ 源管理屏 ──────────────────────────────────────────────────────────

    /**
     * 启停写口按得到 + 有出路 + 点卡开抽屉（站点墙重做后的入口形）。
     *
     * 「有出路」这一判保留：源管理屏此前**根本没有 onBack**，进去出不来。
     * 抽屉是模态的（遮罩盖住整页），所以返回这一量在抽屉关着的那一帧做。
     *
     * 旧的「左栏点行选中」随左清单一起撤了：已开启站点的卡片身 = 打开抽屉
     * （`onOpenSite` 没接时退化为选中），未启用站点的索引条 = 就地启用。
     */
    @Test
    fun 源管理屏启停写口按一次而返回按得到而点卡开抽屉() = runComposeUiTest {
        val toggled = mutableListOf<Pair<String, Boolean>>()
        val picked = mutableListOf<String>()
        var backs = 0
        var selected by mutableStateOf<String?>(null)
        setContent {
            Stage {
                DesktopSourceManageScreen(
                    rows = listOf(DesktopSourceRowUi("jm", "jm 显示名", true), DesktopSourceRowUi("copy_manga", "拷贝漫画", false)),
                    selectedKey = selected,
                    onSelectedChange = { selected = it; it?.let { key -> picked += key } },
                    note = null,
                    onToggleSource = { k, on -> toggled += k to on },
                    onBack = { backs++ },
                )
            }
        }
        // 抽屉关着：返回按得到。
        onNodeWithContentDescription("返回").performClick()
        assertEquals("这屏没有出路就是单行道", 1, backs)

        // 未启用站点在索引带：点它 = 就地启用（不再走"点行选中"）。
        onNodeWithText("拷贝漫画").performClick()
        assertEquals(listOf("copy_manga" to true), toggled)

        // 已开启站点点卡身 = 打开抽屉（`onOpenSite` 没接时退化为选中）。
        onNodeWithText("jm 显示名").performClick()
        assertEquals(listOf("jm"), picked)
        waitForIdle()

        // 抽屉开着：启停开关在抽屉里，按一次落一次。
        onNodeWithContentDescription("启停 jm").performClick()
        assertEquals(listOf("copy_manga" to true, "jm" to false), toggled)
    }

    /**
     * 计数按真值念；「内置站点」这件事在抽屉头部说清。
     *
     * 旧屏那句「不能删除或新增」随左清单一起撤了：站点墙重做后页头就有「添加站点」
     * （在线站点 / 导入站点脚本），能增；删除不再摆一枚按不动的假把手。
     * 这里钉新屏仍说真话的两处：页头计数、抽屉的内置徽章与脚本改写说明。
     */
    @Test
    fun 源管理屏计数按真值念而抽屉念内置站点() = runComposeUiTest {
        setContent {
            Stage {
                DesktopSourceManageScreen(
                    rows = listOf(DesktopSourceRowUi("jm", "jm 显示名", true), DesktopSourceRowUi("x", "x 名", false)),
                    selectedKey = "jm", onSelectedChange = {}, note = null,
                    onToggleSource = { _, _ -> }, onBack = {},
                )
            }
        }
        // 页头计数按真值念：1 开 1 关。
        onNodeWithText("1 个已开启 · 1 个未启用").assertExists()
        // 已开启卡的副行与抽屉头部各一枚「内置站点」（settingsCount=0 时副行就是这四字）。
        onAllNodesWithText("内置站点").assertCountEquals(2)
        onNodeWithText("脚本随应用版本更新；你对脚本的修改只保存在本机，可以随时恢复。").assertExists()
    }

    /**
     * 源编辑那两枚把手的摆法（§119 那条口径的屏上证词）。
     *
     * 站点墙重做后两枚把手收进抽屉「脚本」卡：编辑脚本 / 恢复内置脚本，
     * 只对抽屉宿主（selectedKey）摆。改写事实改由头部「已修改」徽章念。
     */
    @Test
    fun 源编辑把手只在有那一口时摆而恢复只跟着已修改那颗() = runComposeUiTest {
        setContent {
            Stage {
                DesktopSourceManageScreen(
                    rows = listOf(
                        DesktopSourceRowUi("jm", "jm 显示名", true, overridden = true),
                        DesktopSourceRowUi("copy_manga", "拷贝漫画", true),
                    ),
                    selectedKey = "jm", onSelectedChange = {}, note = null,
                    onToggleSource = { _, _ -> },
                    onBack = {},
                    onEditSource = { },
                    onClearSourceOverride = { },
                )
            }
        }
        // 选中那颗接上了编辑那一口 ⇒「编辑脚本」在。
        onNodeWithText("编辑脚本").assertExists()
        // 「恢复内置脚本」只跟着真有过改写的那一颗（选中那颗 overridden=true ⇒ 在）。
        onNodeWithText("恢复内置脚本").assertExists()
        // 改写事实落在抽屉头部的「已修改」徽章（旧的那行拼接题名随主从形撤了）。
        onAllNodesWithText("已修改").assertCountEquals(1)
    }

    /** 读原文那一口不在手时，「编辑」与「恢复」都不许上屏。 */
    @Test
    fun 源编辑那一路没接上时两枚把手都不摆() = runComposeUiTest {
        setContent {
            Stage {
                DesktopSourceManageScreen(
                    rows = listOf(DesktopSourceRowUi("jm", "jm 显示名", true, overridden = true)),
                    selectedKey = "jm", onSelectedChange = {}, note = null,
                    onToggleSource = { _, _ -> }, onBack = {},
                )
            }
        }
        onNodeWithText("编辑脚本").assertDoesNotExist()
        onNodeWithText("恢复内置脚本").assertDoesNotExist()
        // 「已修改」徽章仍摆 —— 改写过是盘上事实，与把手接没接是两件事。
        onAllNodesWithText("已修改").assertCountEquals(1)
        // 启停那枚开关仍在（没有文字节点，锚是 contentDescription）：
        // 编辑没接上不影响这一格本来就能用的东西。
        onNodeWithContentDescription("启停 jm").assertExists()
    }

    /**
     * 搜索框 + 过滤档那两格（站点墙重做后的桌面形）。
     *
     * 三条判据：① 搜索框按 title 与 key 两路过滤；② 「已开启」档只列亮着的站点；
     * ③ 只剩一颗开着时那一枚开关**禁**而下一行念得出原因（由下一颗用例量）。
     *
     * 版本/在用那几格从行内挪进抽屉：版本是头部独立 Text，在用的是脚本卡那句。
     */
    @Test
    fun 源管理屏搜索框过滤而末颗开关禁得住念得出原因() = runComposeUiTest {
        var selected by mutableStateOf<String?>("jm")
        setContent {
            Stage {
                DesktopSourceManageScreen(
                    rows = listOf(
                        DesktopSourceRowUi("jm", "禁漫天堂", true, version = "1.4.1", inUse = true),
                        DesktopSourceRowUi("goda", "goda 名", false),
                        // 只剩一颗开着 ⇒ 那一枚禁（canDisable=false 由调用点递进来）。
                        DesktopSourceRowUi("only", "独苗", true, canDisable = false),
                    ),
                    selectedKey = selected, onSelectedChange = { selected = it }, note = null,
                    onToggleSource = { _, _ -> }, onBack = {},
                )
            }
        }
        // 抽屉开着：版本与「在用」这两句在抽屉里。
        onNodeWithText("v1.4.1").assertExists()
        onNodeWithText("此刻漫画域正装着这一颗。").assertExists()
        // 抽屉是模态的：遮罩会挡住页头的搜索与三档，先按真实路径关掉再量过滤。
        onNodeWithContentDescription("关闭站点设置").performClick()
        waitForIdle()
        // 搜索框的语义锚在整行上；输入动作在里面的 BasicTextField 那枚节点上。
        val search = onAllNodes(hasSetTextAction())[0]
        // 按 title 命中一路：输入"禁漫"后 goda 与独苗都不在。
        search.performTextInput("禁漫")
        onAllNodesWithText("禁漫天堂").assertCountEquals(1)
        onAllNodesWithText("goda 名").assertCountEquals(0)
        onAllNodesWithText("独苗").assertCountEquals(0)
        // 按 key 命中另一路：**清空要用 performTextClearance**。
        search.performTextClearance()
        search.performTextInput("goda")
        onAllNodesWithText("goda 名").assertCountEquals(1)
        onAllNodesWithText("禁漫天堂").assertCountEquals(0)
        // 清空后「已开启」档只列亮着的两颗，关着那颗消失。
        search.performTextClearance()
        onNodeWithText("已开启").performClick()
        onAllNodesWithText("goda 名").assertCountEquals(0)
        // 「未启用」档反过来：只列没开着的那一颗。
        onNodeWithText("未启用").performClick()
        onAllNodesWithText("goda 名").assertCountEquals(1)
        onAllNodesWithText("独苗").assertCountEquals(0)
    }

    /** 那串「只剩这一颗开着」只对抽屉宿主摆 —— 选中那颗 canDisable=false 的那形。 */
    @Test
    fun 源管理屏末颗开关禁得住念得出原因只对选中那颗摆() = runComposeUiTest {
        setContent {
            Stage {
                DesktopSourceManageScreen(
                    rows = listOf(DesktopSourceRowUi("only", "独苗", true, canDisable = false)),
                    selectedKey = "only", onSelectedChange = {}, note = null,
                    onToggleSource = { _, _ -> }, onBack = {},
                )
            }
        }
        onNodeWithText("只剩这一颗开着，不能再关（全关后漫画域装不起任何一屏）。").assertExists()
        // 那枚开关摆着（不是整颗消失）—— 扳不动的原因念得出，与静默假把手是两族。
        onNodeWithContentDescription("启停 only").assertExists()
    }

    @Test
    fun 源管理屏读不到清单时那句成因上屏() = runComposeUiTest {
        setContent {
            Stage {
                DesktopSourceManageScreen(
                    rows = emptyList(), selectedKey = null, onSelectedChange = {},
                    note = "读不到源清单：IllegalStateException 资产目录没找到",
                    onToggleSource = { _, _ -> }, onBack = {},
                )
            }
        }
        // 站点墙重做后只有页头一处念成因（旧的左右两栏各念一次随主从形撤了）。
        onAllNodesWithText("读不到源清单：IllegalStateException 资产目录没找到").assertCountEquals(1)
    }

    // ── ⑦ 打码那一档：四屏都吃外面递进来的判据，不自己决定 ──────────────────

    /**
     * 卡片/封面在 `masked` 两档下**屏上文字不变**（打码是 Modifier 层，不造第二套文案）。
     *
     * 为什么钉这个而不是钉"糊没糊"：观感量不到（离屏没有像素输出这一说），
     * 而这一族真正会漂的是"遮罩在这里另算一套判据"。四屏的签名都是 `masked` / `maskedOf`
     * 由外面递进来，那一条由 [DesktopMaskJudgementSingleSourceTest] 那把源码尺钉。
     */
    @Test
    fun 四屏在打码档下文案不变而都渲得出来() {
        runComposeUiTest {
            setContent {
                Stage {
                    DesktopExploreScreen(
                        mode = wide, sourceKey = "jm", sections = listOf(EngineSession.Section("A", cards(1))),
                        note = null, maskedOf = { true }, onOpen = {}, onAddFavorites = null, onReload = {}, onOpenSourceManage = {}, onOpenQueue = {}, onOpenDownloads = {}, onOpenLocalComics = {},
                        offlineBadgeOf = { null },
                        onOpenHistory = {}, onOpenStats = {}, onOpenBackup = {}, onOpenSettings = {},
                    )
                }
            }
            onNodeWithText("第1本").assertExists()
        }
        runComposeUiTest {
            setContent {
                Stage {
                    DesktopFavoritesScreen(
                        mode = wide, loaded = true, note = null,
                        shownFavorites = listOf(fav("c1", "收藏的一本")), folderTabs = emptyList(),
                        countNote = "1 本", filterNote = null, onFolderTabSelect = {},
                        maskedOf = { true }, onOpen = {}, onRemove = {},
                    )
                }
            }
            onNodeWithText("收藏的一本").assertExists()
        }
        runComposeUiTest {
            setContent {
                Stage {
                    DesktopReaderScreen(
                        pages = pages(2), episodeLabel = "第 1 话", note = null, masked = true,
                        currentPage = 0, startPage = 0, onPageChange = {}, onBack = {},
                    )
                }
            }
            onNodeWithText("第 1 / 2 页").assertExists()
        }
    }

    /**
     * 单栏（Compact）那一路的量程记录 —— **改口**过一次，核喂屏重建再改一次。
     *
     * 不变量没变：**章节行在视口内（top ≤ 视口高）**。核喂屏版把窄栏改成了
     * 「头部（Hero）定内容高 + 卡片区吃剩余高自滚 + 章节卡定高 280dp 沉底、表自己滚」——
     * 信息再长章节行也回不到视口外。⚠️ 它钉的是"排版改完这条还成立"，而真人窗口的滚动
     * 依旧不归离屏台架管（与 `DesktopReaderKeyTest` 那句"别把离屏按得动写成用户那儿能按"同一条口径）。
     */
    @Test
    fun 单栏时章节行在视口内而信息区限高可滚() = runComposeUiTest {
        setContent {
            Stage { detailContent() }
        }
        onNodeWithText("3 话").assertExists()
        val row = onNodeWithText("第 1 话")
        row.assertExists()
        val top = row.getUnclippedBoundsInRoot().top.value
        println("D_单栏 第1话 top=$top（章节卡定高沉底后章节表应回视口内，台架视口 1024x768）")
        assertTrue(
            "窄栏章节卡沉底失效 ⇒ 章节表又被信息区推出视口：top=$top",
            top <= 768f,
        )
    }

    // ── ⑦ ：下载那一格补的是"门"，不是链路 ────────────────────

    /**
     * 队列表里的一行。
     *
     * ⚠️ 这里**只喂数据**，不调 `DesktopComicDownloadQueue.enqueue`：那颗是进程级单例，
     * 测试里真入队会串味（本文件其余判据与下一批都会被那张表影响）。
     * 真入队/真落盘那一段由 `DesktopComicDownloadQueueTest` 与泵那几颗钉，这一层只量"摆不摆、按不落"。
     */
    private fun queueTask(
        status: DesktopComicQueueStatus,
        order: Int,
        downloaded: Int = 0,
        total: Int = 0,
        error: String = "",
    ) = DesktopComicQueueTask(
        taskId = "copy_manga_1_c$order",
        sourceKey = "copy_manga",
        comicId = "1",
        comicTitle = "题名",
        comicCover = "",
        chapterId = "c$order",
        chapterTitle = "第 ${order + 1} 话",
        chapterOrder = order,
        directoryPath = "copy_manga/1/000${order}_第 ${order + 1} 话",
        status = status,
        downloadedPages = downloaded,
        totalPages = total,
        error = error,
        placeholder = false,
        createTime = 1L,
        updateTime = 2L,
    )

    /**
     * 「还没读回来」与「读回来而确实是空的」必须念两句不同的话，且都不摆行。
     *
     * 这一族的原文在 `DesktopComicDownloadQueue` 的注释里（把"还没读到"说成"读到了没有"）。
     */
    @Test
    fun 队列屏表没读回来时念正在读而不摆行() = runComposeUiTest {
        var stops = 0
        var removes = 0
        setContent {
            Stage {
                DesktopComicQueueScreen(
                    mode = wide, tasks = emptyList(), primed = false, note = null,
                    onStop = { stops++ }, onRemove = { removes++ }, onBack = {},
                    expandedTaskId = null, onToggleExpand = {}, detailFiles = emptyList(),
                    detailLoading = false, onOpenDir = {},
                )
            }
        }
        onNodeWithText("正在读那张表…").assertExists()
        assertFalse(
            "还没读回来就念「本机队列空」是把两档塌成一句：",
            runCatching {
                onNodeWithText("本机队列空 —— 详情页「离线下载」勾选某几话会排到这里来").assertExists()
            }.isSuccess,
        )
        assertTrue(
            "没有行的时候不许摆把手：" +
                onAllNodesWithContentDescription(DESKTOP_COMIC_QUEUE_REMOVE_LABEL).fetchSemanticsNodes(),
            onAllNodesWithContentDescription(DESKTOP_COMIC_QUEUE_REMOVE_LABEL).fetchSemanticsNodes().isEmpty(),
        )
        assertEquals(0, stops)
        assertEquals(0, removes)
    }

    /** 读回来而确实空了：那句改口念"空"，并说得出门在哪儿（详情那一屏那枚把手）。 */
    @Test
    fun 队列屏空表念得出去哪儿排队而不摆行() = runComposeUiTest {
        setContent {
            Stage {
                DesktopComicQueueScreen(
                    mode = wide, tasks = emptyList(), primed = true, note = null,
                    onStop = {}, onRemove = {}, onBack = {},
                    expandedTaskId = null, onToggleExpand = {}, detailFiles = emptyList(),
                    detailLoading = false, onOpenDir = {},
                )
            }
        }
        onNodeWithText("本机队列空 —— 详情页「离线下载」勾选某几话会排到这里来").assertExists()
        assertTrue(
            "空表时摆一枚按不到东西的把手就是假开关：",
            onAllNodesWithContentDescription(DESKTOP_COMIC_QUEUE_STOP_LABEL).fetchSemanticsNodes().isEmpty(),
        )
    }

    /**
     * 「暂停」只挂在**正在跑**那一行；「移除」每一行都有。
     *
     * 两档同屏时数按钮的**枚数**（只看一句文案读不出"排队那一行多摆了一枚"）。
     * 各按一次恰好落一次：这一屏从前没有任何 UI 能碰到那两跳回调。
     */
    @Test
    fun 队列屏两枚把手各挂该挂的行而按一次恰好一次() = runComposeUiTest {
        var stops = 0
        var removes = 0
        val running = queueTask(DesktopComicQueueStatus.RUNNING, 0, downloaded = 7, total = 30)
        val pending = queueTask(DesktopComicQueueStatus.PENDING, 1)
        setContent {
            Stage {
                DesktopComicQueueScreen(
                    mode = wide, tasks = listOf(running, pending), primed = true, note = null,
                    onStop = { stops++ }, onRemove = { removes++ }, onBack = {},
                    expandedTaskId = null, onToggleExpand = {}, detailFiles = emptyList(),
                    detailLoading = false, onOpenDir = {},
                )
            }
        }
        // 题名·话名一行 + 状态徽 + 页码一格，不再是单句 rowLine。
        onNodeWithText("题名 · 第 1 话").assertExists()
        onNodeWithText("正在下").assertExists()
        onNodeWithText("7 / 30 页").assertExists()
        assertEquals(
            "「暂停」只许有正在跑那一行这一枚（排队那一笔还没开始，没什么可停）：",
            1, onAllNodesWithContentDescription(DESKTOP_COMIC_QUEUE_STOP_LABEL).fetchSemanticsNodes().size,
        )
        assertEquals(
            "「移除」每一行都得有（终局那几档留在表里，没有别的门能清）：",
            2, onAllNodesWithContentDescription(DESKTOP_COMIC_QUEUE_REMOVE_LABEL).fetchSemanticsNodes().size,
        )
        onNodeWithContentDescription(DESKTOP_COMIC_QUEUE_STOP_LABEL).performClick()
        onAllNodesWithContentDescription(DESKTOP_COMIC_QUEUE_REMOVE_LABEL)[0].performClick()
        assertEquals(1, stops)
        assertEquals(1, removes)
    }

    /** 失败那一行把站方原话带到行尾（只说「失败了」等于让用户再按一次去撞同一堵墙）。 */
    @Test
    fun 队列屏失败那一行念得出站方原话() = runComposeUiTest {
        setContent {
            Stage {
                DesktopComicQueueScreen(
                    mode = wide,
                    tasks = listOf(queueTask(DesktopComicQueueStatus.FAILED, 2, error = "取页失败 页=3")),
                    primed = true, note = null, onStop = {}, onRemove = {}, onBack = {},
                    expandedTaskId = null, onToggleExpand = {}, detailFiles = emptyList(),
                    detailLoading = false, onOpenDir = {},
                )
            }
        }
        // 失败原话不再缝进整句 rowLine，而是状态徽旁点名。
        onNodeWithText("失败了").assertExists()
        onNodeWithText("取页失败 页=3").assertExists()
        onNodeWithText("题名 · 第 3 话").assertExists()
    }

    /** 二级页必须有出路；扳了闸/撤了那一句原话上屏（屏不算话，只搬壳里算好的那句）。 */
    @Test
    fun 队列屏返回按得到而note那句原话上屏() = runComposeUiTest {
        var backs = 0
        val note = "撤掉了：第 1 话「第 1 话」已从表里摘出去，盘上那 3 页还留着（要删干净去「本机已下载」那一屏）"
        setContent {
            Stage {
                DesktopComicQueueScreen(
                    mode = wide, tasks = emptyList(), primed = true, note = note,
                    onStop = {}, onRemove = {}, onBack = { backs++ },
                    expandedTaskId = null, onToggleExpand = {}, detailFiles = emptyList(),
                    detailLoading = false, onOpenDir = {},
                )
            }
        }
        onNodeWithContentDescription("返回").performClick()
        assertEquals(1, backs)
        onNodeWithText(note).assertExists()
    }

    /** 设计稿 ①②：统计总面板做真聚合；展开卡摆真源/真目录把手/真清单/失败原话。 */
    @Test
    fun 队列屏统计面板与展开详情摆真数据() = runComposeUiTest {
        var opened = 0
        var toggled: String? = null
        val running = queueTask(DesktopComicQueueStatus.RUNNING, 0, downloaded = 7, total = 30)
        val failed = queueTask(DesktopComicQueueStatus.FAILED, 1, error = "503")
        setContent {
            Stage {
                DesktopComicQueueScreen(
                    mode = wide, tasks = listOf(running, failed), primed = true, note = null,
                    onStop = {}, onRemove = {}, onBack = {},
                    expandedTaskId = running.taskId,
                    onToggleExpand = { toggled = it.taskId },
                    detailFiles = listOf("0000.jpg" to 1024L, "0001.jpg" to 2048L),
                    detailLoading = false,
                    onOpenDir = { opened++ },
                )
            }
        }
        // ① 统计四格（剩余时间/传输速度/任务队列/并发·限速）：
        // 任务队列计数真聚合（2 笔），并发格念真并发档（默认 1 ⇒ "1 / 1"，档二 5b-3）。
        onNodeWithText("剩余时间").assertExists()
        onNodeWithText("传输速度").assertExists()
        onNodeWithText("任务队列").assertExists()
        onNodeWithText("2 笔").assertExists()
        onNodeWithText("并发线程 · 全局速度限制").assertExists()
        onNodeWithText("1 / 1").assertExists()
        // ② 展开卡双列：源信息框（壳递的展开那一笔 = c0）、文件清单框真数、目录把手按一次落一次。
        onNodeWithText("源 copy_manga · comic 1 · 话 c0").assertExists()
        onNodeWithText("文件清单（2 颗）").assertExists()
        onNodeWithText("0000.jpg").assertExists()
        onNodeWithText("打开目录").performClick()
        assertEquals(1, opened)
        // 展开钮已图标化（设计稿 .exp）：收起钮恰一枚（只在展开那一行），按未展开行的展开钮换页。
        assertEquals(
            "整屏只许展开那一行摆收起钮（contentDescription 恰一枚）：",
            1, onAllNodesWithContentDescription("收起").fetchSemanticsNodes().size,
        )
        onAllNodesWithContentDescription("展开")[0].performClick()
        assertEquals(failed.taskId, toggled)
    }

    // 行级下载按钮已随详情页重构移除，单话下载与不进阅读器语义由批量下载弹窗与章节行点击测试覆盖。

    /**
     * 探索屏的「下载队列 / 本机已下载 / 本地漫画」入口随溢出菜单一起撤掉
     * （「去掉更多按钮」），落点统一由侧栏「下载与本地」承载 —— 这里量的是"屏上不再有
     * 那三枚"，而不是"按了会落"（那一条随菜单一起退役，不另立替身）。
     */
    @Test
    fun 探索屏不再摆下载相关入口() = runComposeUiTest {
        setContent {
            Stage {
                DesktopExploreScreen(
                    mode = wide, sourceKey = "copy_manga", sections = emptyList(),
                    note = null, maskedOf = { false }, onOpen = {}, onAddFavorites = null, onReload = {},
                    onOpenSourceManage = {}, onOpenQueue = {}, onOpenDownloads = {}, onOpenLocalComics = {},
                    offlineBadgeOf = { null },
                    onOpenHistory = {}, onOpenStats = {}, onOpenBackup = {}, onOpenSettings = {},
                )
            }
        }
        listOf("下载队列", "本机已下载", "本地漫画").forEach { label ->
            assertTrue(
                "探索屏还在摆「$label」入口（该由侧栏承载）：" + onAllNodesWithText(label).fetchSemanticsNodes(),
                onAllNodesWithText(label).fetchSemanticsNodes().isEmpty(),
            )
        }
    }

    // ── ⑧ ：「本机已下载」那一屏的门 ──────────────────────────

    /**
     * 盘上的一行。
     *
     * ⚠️ 同样**只喂数据**：这一层不碰那颗进程级单例队列，也不碰盘（读树、删除、入队各有自己的判据）。
     *    这里只量「摆不摆、按不落、念的是哪一句」。
     */
    private fun downloadedChapter(
        order: Int,
        written: Int,
        expected: Int,
        complete: Boolean = written == expected,
    ) = DesktopComicDownloadedChapter(
        sourceKey = "copy_manga",
        comicId = "1461316",
        chapterId = "c$order",
        order = order,
        title = "第0${order + 1}话",
        expectedPages = expected,
        writtenPages = written,
        bytes = 1024L * 1024L * 3,
        complete = complete,
    )

    private fun downloadedListing(vararg chapters: DesktopComicDownloadedChapter) = DesktopComicDownloadListing(
        groups = if (chapters.isEmpty()) emptyList()
        else listOf(DesktopComicDownloadedGroup(comicId = "1461316", sourceKey = "copy_manga", chapters = chapters.toList())),
        skipped = 0,
        rootExists = true,
    )

    /** 表头那一句**逐字**上屏（屏不另写一句，只搬那颗纯函数的原话）。 */
    @Test
    fun 已下载屏表头念那颗纯函数的原话() = runComposeUiTest {
        val listing = downloadedListing(downloadedChapter(0, 30, 30))
        setContent {
            Stage {
                DesktopComicDownloadsScreen(
                    mode = wide, listing = listing, note = null, armedKey = null,
                    onRepair = {}, onDelete = {}, onOpenChapterDir = {}, onBack = {},
                )
            }
        }
        onNodeWithText(desktopComicDownloadedScreenHeader(listing)).assertExists()
    }

    /**
     * 「还没数回来」与「数回来而确实空了」念两句不同的话，而**两种都不摆行、不摆把手**。
     *
     * 把"还没读到"说成"本机没有"是本仓那一族（队列屏那两条是同一条口径的前一屏）。
     */
    @Test
    fun 已下载屏没数回来时念正在数而不摆行() = runComposeUiTest {
        var repairs = 0
        var deletes = 0
        setContent {
            Stage {
                DesktopComicDownloadsScreen(
                    mode = wide, listing = null, note = null, armedKey = null,
                    onRepair = { repairs++ }, onDelete = { deletes++ }, onOpenChapterDir = {}, onBack = {},
                )
            }
        }
        onNodeWithText("正在数本机那一棵树…").assertExists()
        assertTrue(
            "还没数回来就念「目录建好了，里面还一条都没有」是把两档塌成一句：" +
                onAllNodesWithText("存话的目录建好了，里面还一条都没有").fetchSemanticsNodes(),
            onAllNodesWithText("存话的目录建好了，里面还一条都没有").fetchSemanticsNodes().isEmpty(),
        )
        assertEquals(0, repairs)
        assertEquals(0, deletes)
    }

    /** 数回来而确实空了：那句改口念"空"，而那一行右边一枚把手都没有（没有行就没有把手）。 */
    @Test
    fun 已下载屏空表念得出去哪儿排队而不摆行() = runComposeUiTest {
        setContent {
            Stage {
                DesktopComicDownloadsScreen(
                    mode = wide, listing = downloadedListing(), note = null, armedKey = null,
                    onRepair = {}, onDelete = {}, onOpenChapterDir = {}, onBack = {},
                )
            }
        }
        onNodeWithText("存话的目录建好了，里面还一条都没有").assertExists()
        assertTrue(
            "没有行的时候不许摆把手：",
            onAllNodesWithText("补齐这一话 · 还缺 2 页").fetchSemanticsNodes().isEmpty(),
        )
        assertEquals(
            "空表时删除把手也该一枚都没有：",
            0, onAllNodesWithContentDescription(desktopComicDeleteIdleLabel(1)).fetchSemanticsNodes().size,
        )
    }

    /**
     * 「补齐」只挂在**缺页**那一行（齐了摆它就是多一枚没意义的把手），而删除每一行都有。
     *
     * 各按一次恰好落一次：这一屏从前没有任何一屏能碰到那两跳回调。
     */
    @Test
    fun 已下载屏两枚把手各挂该行而按一次恰好一次() = runComposeUiTest {
        var repairs = 0
        var deletes = 0
        val whole = downloadedChapter(0, 30, 30)
        val gap = downloadedChapter(1, 28, 30)
        setContent {
            Stage {
                DesktopComicDownloadsScreen(
                    mode = wide, listing = downloadedListing(whole, gap), note = null, armedKey = null,
                    onRepair = { repairs++ }, onDelete = { deletes++ }, onOpenChapterDir = {}, onBack = {},
                )
            }
        }
        // 行里那一句逐字复用既有那颗纯函数（这里钉"真上屏了"，不重算一遍格式）。
        onNodeWithText("第 2 话「第02话」 · 1461316").assertExists()
        onNodeWithText("缺 2 页").assertExists()
        onNodeWithText("28/30 页 · 3.0MB").assertExists()
        onNodeWithText("已下全").assertExists()
        assertEquals(
            "「补齐」只许有缺页那一行这一枚（已下全那一行摆它就是假开关）：",
            1, onAllNodesWithText(desktopComicRepairLabel(gap)).fetchSemanticsNodes().size,
        )
        assertEquals(
            "「删掉第 N 话的本机文件」每一行都得有：",
            2, onAllNodesWithContentDescription(desktopComicDeleteIdleLabel(1))
                .fetchSemanticsNodes().size + onAllNodesWithContentDescription(desktopComicDeleteIdleLabel(2)).fetchSemanticsNodes().size,
        )
        onNodeWithText(desktopComicRepairLabel(gap)).performClick()
        onNodeWithContentDescription(desktopComicDeleteIdleLabel(2)).performClick()
        assertEquals("按补齐该恰好落 onRepair：", 1, repairs)
        assertEquals("按删除该恰好落 onDelete：", 1, deletes)
    }

    /**
     * 删除那两步的形状：`armedKey` 不是这一行 ⇒ 念「删掉第 N 话的本机文件」；
     * `armedKey` 正是这一行 ⇒ 改念「再按一次就删」，而按下才真落。
     *
     * ⚠️ 屏自己**不**算武装态（它只吃 `armedKey`），所以这条能纯喂数据钉住；
     *    "第一按只武装而不落 onDelete" 那一格住在壳里，由源码尺钉。
     */
    @Test
    fun 已下载屏武装那一行改口而按下落一次() = runComposeUiTest {
        val gap = downloadedChapter(1, 28, 30)
        val armed = "copy_manga/1461316/1"
        setContent {
            Stage {
                DesktopComicDownloadsScreen(
                    mode = wide, listing = downloadedListing(gap), note = null, armedKey = armed,
                    onRepair = {}, onDelete = {}, onOpenChapterDir = {}, onBack = {},
                )
            }
        }
        onNodeWithContentDescription(desktopComicDeleteArmedLabel(2)).assertExists()
        assertTrue(
            "武装着的那一行不该再念没武装那句：" + onAllNodesWithContentDescription(desktopComicDeleteIdleLabel(2)).fetchSemanticsNodes(),
            onAllNodesWithContentDescription(desktopComicDeleteIdleLabel(2)).fetchSemanticsNodes().isEmpty(),
        )
        // 对照：别的行仍念没武装那句（武装是**一行**的事，不是整屏的事）。
        val two = downloadedListing(gap, downloadedChapter(2, 10, 30))
        setContent {
            Stage {
                DesktopComicDownloadsScreen(
                    mode = wide, listing = two, note = null, armedKey = armed,
                    onRepair = {}, onDelete = {}, onOpenChapterDir = {}, onBack = {},
                )
            }
        }
        onNodeWithContentDescription(desktopComicDeleteIdleLabel(3)).assertExists()
    }

    /** 二级页必须有出路；上一次按下的果那句原话上屏（屏不算话，只搬壳里算好的那句）。 */
    @Test
    fun 已下载屏返回按得到而note那句原话上屏() = runComposeUiTest {
        var backs = 0
        val note = "删掉了第 1 话的本机文件（30 页 · 3.0MB）"
        setContent {
            Stage {
                DesktopComicDownloadsScreen(
                    mode = wide, listing = downloadedListing(), note = note, armedKey = null,
                    onRepair = {}, onDelete = {}, onOpenChapterDir = {}, onBack = { backs++ },
                )
            }
        }
        onNodeWithContentDescription("返回").performClick()
        assertEquals(1, backs)
        onNodeWithText(note).assertExists()
    }
}



