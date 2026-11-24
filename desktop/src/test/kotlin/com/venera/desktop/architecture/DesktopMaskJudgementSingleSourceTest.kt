package com.venera.desktop.architecture

import com.venera.desktop.app.DesktopUxPlatformServices
import com.venera.desktop.app.VeneraDesktopLargeScreen
import com.venera.desktop.gallery.ui.DesktopSourceTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 钉住「六颗新屏的打码判据只有壳里那一处」—— 阅读器那颗文件注释写的「两处判据一定会漂」。
 *
 * ## 漂了会是什么形状
 *
 * 这一族的成因在清单 §1/§2 与 `DesktopUxServicesProvidedTest` 都记过：桌面此前吃的是
 * `NoOpUxPlatformServices`（`coverMaskStateOf` 恒 `VISIBLE`），于是**共享零件里的封面全不打码** ——
 * 而它不让任何测试变红，因为那个返回值是合法值。修法是壳里 provide 真守卫 + 壳替每一屏问那一处。
 *
 * 现在的新风险是反方向的：**六屏各算一套**。屏自己算有两种坏法——
 *  ① 判据与守卫件那份档不一致（墙上遮着、详情不遮）；
 *  ② 有人在自己屏里读 `guard.nsfwMaskMode` 直接比字符串（那才是真的两份口径）。
 * 清单 A 条担心的正是这一族（`jm` 是探索默认源，`OFF` 档下成人封面直接铺出来）。
 *
 * ## 判据（数自现状，不是设想的红线）
 *
 *  - 六屏的文件里**零处**判据：只吃形参 `masked` / `maskedOf`，不 import 守卫件、不比档名；
 *  - 问 `coverMaskStateOf` 的地方只有壳（计数一并钉住）；
 *  - 装配点读的是守卫件那一条流，不是硬编码档名。
 */
class DesktopMaskJudgementSingleSourceTest {

    private val screens = listOf(
        "detail/DesktopComicDetailScreen.kt",
        "explore/DesktopExploreScreen.kt",
        "search/DesktopSearchScreen.kt",
        "favorites/DesktopFavoritesScreen.kt",
        "reader/DesktopReaderScreen.kt",
        "sourcemanage/DesktopSourceManageScreen.kt",
    )

    private fun text(rel: String) = DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf(rel))

    /** ① 六屏里不许出现「自己判该不该遮」的那几种写法。 */
    @Test
    fun 六屏只吃递进来的masked而不自判() {
        // 守卫件那三个名字 + 那个判据函数：屏里出现任何一个就意味着它自己算了一遍。
        val banned = listOf(
            "coverMaskStateOf",
            "DesktopGalleryContentGuard",
            "nsfwMaskMode",
            "setNsfwMaskMode",
            "GalleryContentGuard",
        )
        val offenders = screens.flatMap { rel ->
            banned.filter { it in text(rel) }.map { "$rel → $it" }
        }
        assertTrue(
            "这些屏自己算了一遍打码判据（两处判据一定会漂：漂成的样子是墙上遮着而详情不遮）：$offenders",
            offenders.isEmpty(),
        )
    }

    /** ② 形参形状按屏的粒度分两种：逐卡判的吃函数、整本判的吃单值。 */
    @Test
    fun 形参形状按屏的粒度分两种而不是一屏一套() {
        val perCard = listOf(
            "explore/DesktopExploreScreen.kt",
            "search/DesktopSearchScreen.kt",
            "favorites/DesktopFavoritesScreen.kt",
        )
        perCard.forEach { rel ->
            assertTrue("$rel 不再逐卡判（那三屏一屏几十本，每本的源与标签都不同）：", "maskedOf: (" in text(rel))
        }
        listOf("detail/DesktopComicDetailScreen.kt", "reader/DesktopReaderScreen.kt").forEach { rel ->
            assertTrue("$rel 变成逐卡判据了（那一本的档是按整本算的）：", "masked: Boolean," in text(rel))
            assertTrue("$rel 不该同时有两种形参：", "maskedOf: (" !in text(rel))
        }
    }

    /** ③ 判据的问法只有一处出处：壳（`DesktopUxPlatformServices.kt` 那 1 处是**定义**那颗）。 */
    @Test
    fun 判据只在壳里问() {
        val largeNames = DesktopSourceTree.desktopLargeScreenSources().map { it.name }.toSet()
        val counts = DesktopSourceTree.desktopOwnSources()
            .groupBy { if (it.name in largeNames) "VeneraDesktopLargeScreen.kt" else it.name }
            .mapValues { (_, files) -> files.sumOf { DesktopSourceTree.codeOccurrences(it, "coverMaskStateOf") } }
            .filterValues { it > 0 }
        assertEquals(
            "`coverMaskStateOf` 的分布变了。现状按**剥注释后的代码行**数：壳 6 处 = 详情 / 阅读 / 收藏 / 探索 / 搜索 " +
                "那五路各问一次（**墙**那一路不在这里 —— 它把 `guard` 与 `maskMode` 递进取数层判）" +
                " + §142 首页那一格一处（它问的是同一颗 `ux`，源键与探索屏同一条 —— 首页那批卡本来就是探索取数摊平的）" +
                " + `DesktopUxPlatformServices.kt` 1 处（那颗是判据的**定义**，不算另算一遍）。" +
                "新增一处要先答：这一屏的判据为什么不复用壳里那一条。\n" +
                "2026-10-30 tool-suite 稿① 壳 6→7：网络收藏段手风琴展开后每格也要问遮罩 —— 它问的是" +
                "**同一颗 `ux.coverMaskStateOf`**（源键/标题/作者/标签逐本现算），与探索/收藏同一条，不是另立判据；单源没破，只是多一个消费者。" +
                "\n2026-10-30 7→8：阅读历史换成桌面自制屏后，卡片遮罩仍问壳里同一颗 ux（历史屏**不自己判**）。" +
                "\n2026-11-01 8→9：详情页重建（下掉共享屏、桌面详情重做成独立页），详情支路在壳里自己问一次 —— " +
                "问的还是**同一颗 `ux.coverMaskStateOf`**（与阅读支路同一条腿），打码判据仍单源；" +
                "详情页只吃壳算好的 `masked` 成品位，不另立判据。" +
                "\n2026-11-07 宽屏双源批 9→10：首页第二套（宽屏双列）接线再问一次（与窄版同一颗 `desktopUx`、"
                + "同一源键 —— 两套首页是同一屏的两档布局，不是两套判据）。" +
                "\n2026-11-22 10→11：收藏 Hub 第四档「追更」接线再问一次 —— 它问的是**同一颗 `ux.coverMaskStateOf`**" +
                "（源键/标题/作者逐本现算，与本地收藏、网络收藏同一腿），追更屏只吃壳算好的 `maskedOf` 成品位，不另立判据。" +
                "\n2026-11-24 11→10：首页双皮肤（经典/浅色）收拢共享局部变量 `comicMaskedOf`，减少重复调用一次。",
            mapOf("VeneraDesktopLargeScreen.kt" to 10, "DesktopUxPlatformServices.kt" to 1),
            counts,
        )
    }

    /** ④ 那条链的源头是真守卫件（不是常量、不是默认档）。 */
    @Test
    fun 判据那条链的源头是真守卫() {
        val app = DesktopSourceTree.codeLinesOf(DesktopSourceTree.desktopSourceOf("app/VeneraDesktop.kt"))
        val maskLine = app.firstOrNull { it.startsWith("maskModeOf =") }
        assertTrue("装配点没递 `maskModeOf`（屏上判据会恒吃默认档）：$app", maskLine != null)
        // 源头是 `guard.nsfwMaskMode` 的订阅态，
        // maskModeOf 吃的是订阅值 guardMaskMode；裸读 .value 的普通 lambda 不触发重组。
        assertTrue(
            "遮罩档源头不是守卫件那条流（应订阅 guard.nsfwMaskMode）。实际：$app",
            app.any { "guard.nsfwMaskMode.collectAsState()" in it },
        )
        assertTrue(
            "maskModeOf 吃的不是订阅到的状态值。实际：$maskLine",
            "guardMaskMode" in maskLine!!,
        )

        val shell = DesktopSourceTree.desktopLargeScreenCode()
        assertTrue("`LocalUxPlatformServices provides` 那一句没了（R3 复发）：", "LocalUxPlatformServices provides" in shell)
        assertTrue("provide 的居然是 NoOp（与不 provide 等价）：", "DesktopUxPlatformServices(" in shell)
    }

    /**
     * ⑤ 那三档的字面量与写侧那颗件的读数同一套。
     *
     * 写口与读口分在两处时，漂的样子是「屏上能选 HIDE 而守卫件认不出来」——
     * 那比不遮更糟（用户以为开了最严的一档）。
     *
     * 这一格的**界面**从源管理屏挪进了设置中枢（「探索与卡片」组）——
     * 分级遮罩与「源」无关。屏上档名不再手写：中枢吃 `desktopMaskModeLabels()` 那张单源表，
     * 三档字面量（OFF/BLUR/HIDE）在那颗件里恰好各一处 ⇒ 改了表这里先红。
     */
    @Test
    fun 三档字面量在屏上与壳里的读数同一套() {
        // 那张单源表在（桌面上遮罩档位的**唯一**出处）。
        val labels = text("gallery/ui/DesktopGuardPolicy.kt")
        // 档位表住哪一颗都行，但全 main 里 `desktopMaskModeLabels` 只许一处**定义**。
        val defSites = DesktopSourceTree.desktopOwnSources()
            .map { it.name to DesktopSourceTree.codeOccurrences(it, "fun desktopMaskModeLabels") }
            .filter { it.second > 0 }
        assertEquals(
            "遮罩档名那张表的定义出处变了（单源那一格破了个洞）：$defSites",
            1, defSites.size,
        )
        // 那句写口失败读数仍点名这三档（写侧挪进设置中枢，
        // 成因句跟着挪进 DesktopSettingsHub —— 它仍是这一路的唯一成因出处，尺子照钉）。
        val settingsHubCode = listOf(
            "settings/DesktopSettingsHub.kt",
            "settings/DesktopSettingsCardsReaderAndExplore.kt",
        ).joinToString("\n") { DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf(it)) }
        assertTrue(
            "那句写口失败读数不再点名这三档（屏上选了而写不进去时，用户读不出为什么）：",
            "档位只接受 OFF/BLUR/HIDE" in settingsHubCode,
        )
        assertTrue("那张表是空的（解析那颗文件失败？）：", labels.isNotEmpty())
    }

    /** ⑥ 阅读器那一档的打码是 Modifier 层（blur），不另造一套文案；页格那颗兜底也还在。 */
    @Test
    fun 阅读器的打码不造第二套文案而页格兜底还在() {
        // 批 4：视口 / 页图格 / 侧栏条已拆到 DesktopReaderOverlays.kt，两条断言跨两颗成员文件读。
        val reader = text("reader/DesktopReaderScreen.kt") + "\n" +
            text("reader/DesktopReaderViewports.kt") + "\n" +
            text("reader/DesktopReaderOverlays.kt")
        assertTrue("那一档不再是 blur（改成藏起来会与墙上/封面的语义分家）：", "Modifier.blur(24.dp)" in reader)
        // 兜底比改吃单源常量 TRIPLE_RATIO_FALLBACK（书缝阴影/分组机制批把字面量收进常量，
        // Screen 里不再写字面量 0.7f）；约束语义保持：Screen 仍引用这一颗兜底，且不另写第二份字面量。
        assertTrue(
            "页格兜底比不再引用（退回手写字面量或彻底删掉都算分家）：",
            "TRIPLE_RATIO_FALLBACK" in reader,
        )
        assertEquals(
            "页格兜底比字面量又回到 Screen 里写第二份（清单 §1 因 2，兜底必须单源）：",
            0, reader.lines().count { "0.7f" in it },
        )
        // 页图吃的是对象 model，不是字符串（清单 §1 因 3）。
        assertTrue("页不再是 `pages[i]` 那个对象（退回字符串会被 Coil 送网络 fetcher ⇒ 一片黑）：", "model = pages[i]" in reader)
    }
}
