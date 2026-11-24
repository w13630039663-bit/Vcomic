package com.venera.desktop.architecture

import com.venera.desktop.app.DesktopRoute
import com.venera.desktop.app.VeneraDesktopLargeScreen
import com.venera.desktop.ui.desktopSideBarGroups
import com.venera.desktop.gallery.ui.DesktopSourceTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「画廊浏览历史」那条链的**活壳接线**尺。
 *
 * ## 它替下的是哪六条假前提
 *
 * `DesktopGalleryHistorySeedTest` ⑤⑥⑦⑧⑨⑩ 从前钉的是「详情浮层写 + Fluent 那一屏 + `VeneraDesktopApp`
 * （今 `DesktopDomainRows.kt`）派发」，而那条渲染路在一百二十几批前就拆了 —— 现量：`recordView` 在 `desktop/src/main` 里**零调用点**
 * 而那一把尺长绿。这正是本仓记过最狠的那一格（批 115 量到的那把惰尺子 `DesktopUnusedInternalTest` ①③
 * 是同一课）：**钉前提的尺子不会因前提散了而红**。所以这里的每一条都数**今天真在的活壳**里的次数，
 * 并把「零处」也判红（删空接线 = 计数落 0 = 红，手册 §4.1 那条"扫描类判据读空会全部恒真"的防线）。
 *
 * ## 为什么形状用"数出现次数"而不是问"存不存在"
 *
 * 接线类判据红过的那一族是：假动作存在（形参递了但没人读、门摆着而回调是 `{}`）。数次数至少管住两件事 ——
 * 该在的一处不少，不该多的地方不多（同一件事写第二份就一定会漂）。
 */
class DesktopGalleryBrowseHistoryEntryTest {

    private val shellFile = DesktopSourceTree.desktopSourceOf("app/VeneraDesktopLargeScreen.kt")

    /** 批 4：侧栏分组已拆到成员文件；"壳里有那一处"按大屏成员表合并读，装配点计数仍指主文件。 */
    private val shell get() = DesktopSourceTree.desktopLargeScreenCode()
    private val route get() = DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("app/DesktopRoute.kt"))
    private val screenFile = DesktopSourceTree.desktopSourceOf("gallery/ui/DesktopGallerySavedScreen.kt")
    private val screen get() = DesktopSourceTree.codeText(screenFile)

    /** ① 路由里有那一档，而壳里切进它**恰一处**（那枚把手按下去真改 route，不是只念一句）。 */
    @Test
    fun 那一档路由存在而切进去恰一处() {
        assertTrue("DesktopRoute 里没有 GalleryBrowseHistory 那一档：", route.contains("data object GalleryBrowseHistory"))
        // 合并页后：顶栏那枚「历史」把手直连路由，派发那一支是两条路由合写的 when 分支。
        assertEquals(
            "切进历史那一档的赋值必须恰一处（现在是 " +
                "${DesktopSourceTree.desktopLargeScreenOccurrences( "= DesktopRoute.GalleryBrowseHistory")} 处）：",
            1,
            DesktopSourceTree.desktopLargeScreenOccurrences( "= DesktopRoute.GalleryBrowseHistory"),
        )
    }

    /**
     * ② 那一枚门挂在**按得到的把手**上，而不是藏在某处没人调。
     *
     * 232dp 的 `DesktopGalleryHero` 横幅退役，「历史」把手从
     * 头图带那颗 `VeneraButton` 挪到**侧栏分组**那一行（`desktopSideBarGroups` 的
     * 「浏览历史」item 自带 `DesktopRoute.GalleryBrowseHistory` 路由判据，门恒摆没有 if 档）；
     * 装配点那记 `onOpenBrowseHistory = {` 仍由 pane 链递进 —— 门从"横幅按钮"改住"侧栏项"，
     * 门本身不许消失。
     */
    @Test
    fun 那一枚门挂在按得到的把手上() {
        assertTrue("活壳丢了侧栏「浏览历史」那一项：", shell.contains("DesktopRoute.GalleryBrowseHistory,"))
        assertTrue(
            "装配点没把历史门接上 pane（`onOpenBrowseHistory = {` 恰一处，" +
                "现在是 ${DesktopSourceTree.desktopLargeScreenOccurrences( "onOpenBrowseHistory = {")} 处）：",
            DesktopSourceTree.desktopLargeScreenOccurrences( "onOpenBrowseHistory = {") == 1,
        )
        // 写侧那条早退（打码卡不计入历史）跟门同族，原样钉着。
        assertTrue(
            "写侧没有对 `masked` 早退 ⇒ 打码卡会被记成「看过」（假历史）：",
            shell.lines().any { "if (card?.masked == true) return@LaunchedEffect" in it },
        )
    }

    /** ③ 那一屏的形参是**必传非假开关**（无默认值 ⇒ 漏传就编译不过，而不是 `{}` 蒙过去）。 */
    @Test
    fun 那一屏的把手是必传形参而不是假开关() {
        for (decl in listOf(
            "onOpenHistory: (DesktopBrowseView) -> Unit,",
            "onRemoveHistory: (DesktopBrowseView) -> Unit,",
            "onOpenFavorite: (DesktopWallCardModel) -> Unit,",
            "onRemoveFavorite: (DesktopWallCardModel) -> Unit,",
            "onClear: () -> Unit,",
            "onBack: () -> Unit,",
        )) {
            assertTrue("屏侧缺了必传形参 `$decl`：", screen.contains(decl))
            assertTrue("屏侧给 `$decl` 配了默认值（假开关）：", !screen.contains(decl.dropLast(1) + " = "))
        }
    }

    /** ④ 那一屏有出路而不是单行道。 */
    @Test
    fun 历史屏有出路而不是单行道() {
        assertTrue("那一屏没有走统一页壳：", screen.contains("DesktopPageShell("))
        assertTrue("统一页壳没接返回回调：", screen.contains("onBack = onBack,"))
        val pageShell = DesktopSourceTree.codeText(
            DesktopSourceTree.desktopSourceOf("ui/DesktopPageTemplates.kt"),
        )
        assertTrue("统一页壳没渲那枚返回按钮：", pageShell.contains("DesktopBackButton(onClick = onBack)"))
        // 合并页后派发是一支双路由的 when 分支，从那一行往下数 12 行找返回口。
        val block = shell.lines()
            .dropWhile { "DesktopRoute.GalleryFavorites, DesktopRoute.GalleryBrowseHistory ->" !in it }
            .take(160)
            .joinToString("\n")
        assertTrue("壳里那一支没把返回切回 Tab（那就是进去出不来）：$block", "route = DesktopRoute.Tab" in block)
    }

    /**
     * ⑤ 写侧**恰一处**：`recordView` 在活壳里被调一次，而挡住的那一档**不记**。
     *
     * 这一条是整把尺子的牙 —— 到之间它一直是 0 处，而没有任何一条判据红过。
     * 进作品详情页即记：图库作品页 / pixiv 作品页 / 大图播放器三档共用这一圈 effect。
     */
    @Test
    fun 写侧恰一处而不记挡住那张() {
        val largeNames = DesktopSourceTree.desktopLargeScreenSources().map { it.name }.toSet()
        val sites = DesktopSourceTree.desktopOwnSources()
            .groupBy { if (it.name in largeNames) "VeneraDesktopLargeScreen.kt" else it.name }
            .mapValues { (_, files) -> files.sumOf { DesktopSourceTree.codeOccurrences(it, "recordView(") } }
            .filterValues { it > 0 }
            .entries
            .map { it.key to it.value }
            .sortedBy { it.first }
        assertEquals(
            "`recordView(` 的消费者形状变了（期望「那颗件一处声明 + 活壳一处按」）：$sites",
            listOf("DesktopGalleryBrowseHistory.kt" to 1, "VeneraDesktopLargeScreen.kt" to 1),
            sites,
        )
        // 挡住不记：那一行早退必须**写在代码里**（不是只在注释里承诺）。
        assertTrue(
            "写侧没有对 `masked` 早退 ⇒ 打码卡会被记成「看过」（假历史）：",
            shell.lines().any { "if (card?.masked == true) return@LaunchedEffect" in it },
        )
        // 三档详情页都得进这一圈 effect：从前只认播放器，进详情页不算「看过」。
        for (branch in listOf(
            "is DesktopRoute.GalleryPostDetail -> r.post",
            "is DesktopRoute.GalleryPostViewer -> r.post",
            "(r as? DesktopRoute.PixivWork)?.illustId",
        )) {
            assertTrue("写侧漏了 `$branch` 那一路 ⇒ 进详情页没记成浏览：", shell.contains(branch))
        }
    }

    /** ⑥ 「回原卡」必须走**唯一那颗**守卫起口，绝不自拼卡（两屏唯一的遮罩起口，同 §124 那条纪律）。 */
    @Test
    fun 回原卡走唯一那颗守卫起口() {
        assertEquals(
            "壳里调 `desktopWallGuardedCards(` 恰一处（零=那一路没投卡，两=长出第二条投影路）：",
            1,
            DesktopSourceTree.desktopLargeScreenOccurrences( "desktopWallGuardedCards("),
        )
        assertEquals(
            "壳里认回完整帖那颗件恰一处按（`desktopBrowsePostOfUid(`）：",
            1,
            DesktopSourceTree.desktopLargeScreenOccurrences( "desktopBrowsePostOfUid("),
        )
        assertEquals(
            "壳里那五档成因判读恰一处按（`desktopBrowseRecoveryNote(`）：",
            1,
            DesktopSourceTree.desktopLargeScreenOccurrences( "desktopBrowseRecoveryNote("),
        )
        // 反面对照：不许绕开守卫起口去调那颗裸投影（`masked` 默认 false = 一张都不遮，安全评审 B 的原缺口）。
        assertEquals(
            "壳里出现了裸投影 `desktopWallCardsOf(` ⇒ 那一趟没遮罩判据：",
            0,
            DesktopSourceTree.desktopLargeScreenOccurrences( "desktopWallCardsOf("),
        )
    }

    /** ⑦ 那颗件按数据根**只建一颗**：`getInstance` 在活壳里恰两处（读侧一趟 + 写侧一趟），不许有第三处出处。 */
    @Test
    fun 读数件在活壳里的消费形状() {
        assertEquals(
            "壳里 `DesktopGalleryBrowseHistory.getInstance` 该恰两处（读侧 + 写侧，同一颗数据根下拿到的是同一对象）：" +
                "现在是 ${DesktopSourceTree.desktopLargeScreenOccurrences( "DesktopGalleryBrowseHistory.getInstance")} 处",
            2,
            DesktopSourceTree.desktopLargeScreenOccurrences( "DesktopGalleryBrowseHistory.getInstance"),
        )
        assertEquals(
            "装配点 `VeneraDesktop.kt` 递 `galleryPaths` 恰一处（漏了这一跳 = 那扇门永远念「这台机器还没定下数据根」）：",
            1,
            DesktopSourceTree.codeOccurrences(
                DesktopSourceTree.desktopSourceOf("app/VeneraDesktop.kt"),
                "galleryPaths = paths,",
            ),
        )
    }

    /** ⑧ 那一屏零 IO 零网络零协程零 Fluent（盘上/网络的事全在壳里那两圈 effect）。 */
    @Test
    fun 那一屏零IO零网络零Fluent() {
        for (lit in listOf("withContext", "launch", "getInstance", "fetchById", "VeneraCover", "io.github.composefluent")) {
            assertEquals(
                "那一屏出现 `$lit` ⇒ 该住在壳的跳跑到屏里来了：",
                0,
                DesktopSourceTree.codeOccurrences(screenFile, lit),
            )
        }
    }

    /** ⑨ 屏上那两句念的是**纯件原话**（表头与行内各恰一处搬，不在屏里另拼一句）。 */
    @Test
    fun 那两句念的是纯件原话() {
        // 声明一颗 + 屏里搬一次 = 每串两处（旧遗骸那颗 `DesktopGalleryBrowseHistoryPane.kt` 也在 `gallery/ui/`
        // 里，所以这里**只数活的那一屏**，全 main 计数交 ⑩ 与 §138 那把值尺，别拿遗骸当消费者）。
        assertEquals(
            "屏里搬表头那句恰一处：",
            1,
            DesktopSourceTree.codeOccurrences(screenFile, "desktopBrowseHeaderReading(history)"),
        )
        assertEquals(
            "屏里搬行内那句恰两处（卡面无障碍描述 + 筛选框那句原文匹配，都不另拼）：",
            2,
            DesktopSourceTree.codeOccurrences(screenFile, "desktopBrowseRowLine(row)"),
        )
    }

    /** ⑩ 取证种子认得那一屏（漏了这一枚 = 那一格只有主人鼠标点得动，而鼠标自验是禁的）。 */
    @Test
    fun 取证种子认得历史那一屏() {
        val flags = DesktopSourceTree.forensicFlagsCode()
        assertTrue("`--open=` 没登记 galleryhistory 那一档：", flags.contains("\"galleryhistory\" -> DesktopRoute.GalleryBrowseHistory"))
        // 可选值那一句要点名它，否则落空那一次用户读不到"能打什么"。
        assertTrue("落空那句的可选值清单里没有 GALLERYHISTORY：", flags.contains("GALLERYHISTORY"))
    }
}
