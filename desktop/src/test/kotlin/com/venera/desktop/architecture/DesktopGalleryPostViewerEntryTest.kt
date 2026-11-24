package com.venera.desktop.architecture

import com.venera.desktop.app.DesktopGalleryAndAccountHost
import com.venera.desktop.app.DesktopRoute
import com.venera.desktop.app.VeneraDesktopLargeScreen
import com.venera.desktop.gallery.ui.DesktopSourceTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 看大图那一屏的**接线层**（架构尺）：墙上那张可点卡真有一枚把手切进那一屏，
 * 而那一屏渲的是桌面自搭那颗（不是又留一枚"按了没反应"的死按钮）。
 *
 * ⚠️ 看大图那枚落点有了**第二个消费者** —— 画廊标签搜索那面墙的
 * `WallResultCard` 也接上了**同一枚** `DesktopRoute.GalleryPostViewer`。原来钉"恰一处"的
 * ② 改成钉"恰两处"（墙一处 + 搜索一处），并把两处都现量在案。留"恰一处"会把合法的第二枚
 * 消费者扫红 —— 手册 §4.1 那条"把合法形状扫红的判据比没有判据更坏"。
 *
 * ⚠️ 再改口径：那枚落点有了**第三个消费者** —— 「画廊浏览历史」那一屏的「回原卡」
 * 把手（`onOpenRow` 现取整张 → 过唯一那颗守卫起口投成卡 → 切进**同一枚**看图屏）。② 的第一条计数
 * 跟着从两处改成**三处**，而**第二条仍钉两处**：它钉的是 `onOpenPost = { card -> … },` 那一整串
 * 「两面墙的把手逐字同一形状」，历史那一屏递的是 `onOpenRow`（回调形参名不同、且它在切路由之前
 * 先做了一次现取与投卡），把它塞进那一串的对账里就是把两件不同的事记成一件。
 * 第三处消费者的**形状**由 `DesktopGalleryBrowseHistoryEntryTest` ⑥ 单独钉（它必须走
 * `desktopWallGuardedCards` 那唯一一颗起口，不许自己拼卡）。
 *
 * 为什么这族判据扫源码而不跑无头 UI：lazy 网格在无头树里排不到卡那一行（`DesktopGalleryWallUiTest`
 * / `DesktopGallerySearchUiTest:184-186` 同一前提）—— 硬写一条点卡只会红在"找不到节点"，或误配到
 * 页尾那枚「下一页」把手而假绿。取档与 `openable` 那两族是纯判据，归值尺
 * `DesktopGalleryPostViewerTest`；这里只钉"接没接上、接了几处、是不是桌面那颗屏"。
 *
 * ## 每一条都会因"接线漂了"而红，不会因"读空"而恒真
 *
 * 每条正向 `contains` 都配一处 `codeOccurrences(...) == 1` 的计数式：把整段接线删空，计数落 0 当场红
 * （手册 §4.1 那条"扫描类判据读空会全部恒真"的防线，与 `DesktopGalleryBookmarksHubEntryTest` 同形）。
 */
class DesktopGalleryPostViewerEntryTest {

    private val shellFile = DesktopSourceTree.desktopSourceOf("app/VeneraDesktopLargeScreen.kt")
    private val shell get() = DesktopSourceTree.desktopLargeScreenCode()
    private val route get() = DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("app/DesktopRoute.kt"))
    private val screen get() = DesktopSourceTree.codeText(
        DesktopSourceTree.desktopSourceOf("gallery/ui/DesktopGalleryPostViewer.kt"),
    )
    // 卡片项统一（阶段三）后：主墙 / 搜索墙 / 收藏墙三处不再各自在文件里画 onClick，
    // 那枚 `card.openable` 把关**收成一颗共享卡** `gallery/ui/DesktopWallCard.kt`（唯一出处）。
    // 卡片规格收进唯一卡 DesktopGalleryCard.kt（DesktopWallCard 退成薄委托）。
    private val wallCardFile = DesktopSourceTree.desktopSourceOf("gallery/ui/DesktopGalleryCard.kt")

    /** ① 路由里有这一档（带 post），且它是 data class（要塞进那一张的身份）。 */
    @Test
    fun 路由那一档存在() {
        // 这一档加了 `from`（来路），
        // 签名从单行变多行 —— 字面量只认档名 + 起括号，post 字段还在（下一把尺另有形状判）。
        assertTrue("DesktopRoute 里没有 GalleryPostViewer 那一档：", route.contains("data class GalleryPostViewer("))
    }

    /** ② 恰好五处把手切进那一屏：墙一处 + 搜索墙一处 + 历史屏「回原卡」一处 + 取证种子 + pixiv 作品页中央大图。 */
    @Test
    fun 两面墙各一处把手切进同一枚看大图落点() {
        assertEquals(
            "把卡 route 进看大图那一屏的赋值式必须恰五处（统一入口 helper 的 booru 分支一处 + " +
                "看图取证种子一处 + pixiv 作品页中央大图一处（LARGE 档，进播放器）+ 历史屏回原卡一处 + " +
                "安卓日榜二级屏一处；现在是 " +
                "${DesktopSourceTree.desktopLargeScreenOccurrences( "route = DesktopRoute.GalleryPostViewer(")} 处）：",
            // 主墙、搜索墙、每日热门与收藏 hub 统一走 openGalleryPostAny 分派，直拼看图路由仅保留 5 处。
            5,
            DesktopSourceTree.desktopLargeScreenOccurrences( "route = DesktopRoute.GalleryPostViewer("),
        )
        // 对照：接线点**逐字同一形状** —— 都是 `onOpenPost = openWallCardAny,`（主墙 pane、搜索屏、
        // 探索每日热门、宽屏墙、浅色首页每日热门聚合行、关注动态收件箱——收件箱双源批起六处）。
        // 统一入口那颗闭包里折着「pixiv 进详情 / booru 进看图」的同一判据 —— 挡各处自写一份、判据分叉。
        assertEquals(
            "六处 `onOpenPost` 接线点必须逐字都是「统一入口 openWallCardAny」那一形，且恰六处（现在是 " +
                "${DesktopSourceTree.desktopLargeScreenOccurrences( "onOpenPost = openWallCardAny,")} 处）：",
            6,
            DesktopSourceTree.desktopLargeScreenOccurrences("onOpenPost = openWallCardAny,",
            ),
        )
        // 第五处（每日热门聚合编辑台批）：编辑台 pixiv 卡走同一颗统一入口，只是卡形参名按语义
        // 叫 `onOpenPixivCard` —— 单独钉一处，挡它退回空 lambda 或自拼落点。
        assertEquals(
            "编辑台卡统一入口 openWallCardAny 必须恰一处（现在是 " +
                "${DesktopSourceTree.desktopLargeScreenOccurrences( "onOpenCard = openWallCardAny,")} 处）：",
            1,
            DesktopSourceTree.desktopLargeScreenOccurrences("onOpenCard = openWallCardAny,",
            ),
        )
    }

    /** ③ 只给可点那一档接点击（安全：打码卡不放大，见 `openable` 那条 A 裁决）。 */
    @Test
    fun 点击只在可点那一档接上() {
        // 唯一卡 DesktopGalleryCard.kt 里两道闸（右键「查看大图」+ 点击）
        // 都吃 `if (card.openable)`，全仓仅此一份，子页薄委托不再各写一遍。
        assertEquals(
            "唯一卡 `DesktopGalleryCard.kt` 里 `if (card.openable)` 该恰两处（右键 + 点击）：",
            2,
            DesktopSourceTree.codeOccurrences(wallCardFile, "if (card.openable)"),
        )
        // 反向对照：壳与各子页不再自绘卡形 ⇒ 这串判据在壳里必须归零。
        assertEquals(
            "壳里不该再有自绘的墙卡 openable 闸（卡片项已统一进 DesktopGalleryCard）：",
            0,
            DesktopSourceTree.desktopLargeScreenOccurrences( "if (card.openable)"),
        )
    }

    /** ④ 渲的是**桌面自搭**那颗屏（`GalleryPostScreen` 是 ③ 档编不过，绝不可能直连安卓那颗）。 */
    @Test
    fun 渲的是桌面自搭那颗看大图屏() {
        assertEquals(
            "壳里调 `DesktopGalleryPostViewer(` 必须恰一处（现在是 ${DesktopSourceTree.desktopLargeScreenOccurrences( "DesktopGalleryPostViewer(")} 处）：",
            1,
            DesktopSourceTree.desktopLargeScreenOccurrences( "DesktopGalleryPostViewer("),
        )
        assertTrue(
            "那颗屏自己得声明 `internal fun DesktopGalleryPostViewer(`（有调用点却没定义＝读空假绿）：",
            screen.contains("internal fun DesktopGalleryPostViewer("),
        )
        // 反向对照：这一屏不许是"安卓那颗"冒充（安卓 GalleryPostScreen 在桌面根本编不过）。
        assertTrue("壳里不该出现 `GalleryPostScreen(` 那个安卓③档屏名：", !shell.contains("GalleryPostScreen("))
    }

    /** ⑤ 返回落回 Tab：那一面墙还在原处（不是把 route 塞成一个回不去的死档）。 */
    @Test
    fun 返回那一路落回那一面墙() {
        // `is DesktopRoute.GalleryPostViewer -> ...` 那一支里 `onBack` 收到 Tab。截那一支来看。
        val branch = DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("app/DesktopGalleryAndAccountHost.kt"))
            .substringAfter("is DesktopRoute.GalleryPostViewer ->")
        assertTrue(
            "看大图那一支的 onBack 没落回 Tab（那一面墙回不去）：",
            branch.contains("onBack = { route = r.from ?: DesktopRoute.Tab }") ||
                branch.contains("onBack = { route = DesktopRoute.Tab }"),
        )
    }
}
