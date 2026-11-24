package com.venera.desktop.architecture

import com.venera.desktop.app.DesktopRoute
import com.venera.desktop.app.VeneraDesktopLargeScreen
import com.venera.desktop.gallery.ui.DesktopSourceTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 钉住「安卓那面『收藏 hub』真接进了桌面窗口，而三枚出口都有落点」—— 。
 *
 * ## 缺口长什么样（现场量的）
 *
 * `GalleryBookmarksHubScreen` 那颗屏在 `app/src/main` 里早就存在（31 轮那颗，两颗卡 + 一行说明），
 * 把它卡住的那枚 `TEXT_LINE_HEIGHT` 从 pixiv 脏件搬进独立件之后，它剩下的依赖全是
 * 已接进编译面的干净叶子。可 `desktop/src/main` 里**没有任何一屏**渲它 —— 于是那两颗卡
 * 用户点不到。与 `DesktopHistoryEntryTest` / `DesktopGallerySearchEntryTest` 同一族：毛病不是"缺函数"
 * 而是"屏都在而没人渲、没人开门"。
 *
 * ## 这里钉的四件事
 *
 *  ① `DesktopRoute` 里有 hub 那一档；
 *  ② 恰好一处切进 hub，且挂在墙顶栏那枚**用户按得到**的「收藏」把手上（挡"代码里过一遍而没人能点"）；
 *  ③ 渲的是**安卓那一份** `GalleryBookmarksHubScreen`，不是桌面另写的壳（主人那条"直接接入"的正题）；
 *  ④ 三枚出口逐一对位，**尤其「Pixiv 书签」不留死按钮** —— 桌面按裁决剥了 pixiv，那一路走诚实报屏，
 *     而不是按了没反应（本仓明令禁假把手），也不冒充漫画收藏墙。
 */
class DesktopGalleryBookmarksHubEntryTest {

    private val shellFile = DesktopSourceTree.desktopSourceOf("app/VeneraDesktopLargeScreen.kt")
    private val shellLines = DesktopSourceTree.desktopLargeScreenLines().filter { it.isNotEmpty() }

    /** ① 路由枚举里有 hub 那一档，也有 pixiv 那枚诚实报屏那一档。 */
    @Test
    fun `DesktopRoute 里有 hub 那一档`() {
        val route = DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("app/DesktopRoute.kt"))
        assertTrue(
            "`DesktopRoute` 里没有 `data object GalleryBookmarksHub` ⇒ 那一屏进不去：",
            "data object GalleryBookmarksHub : DesktopRoute" in route,
        )
        assertTrue(
            "「Pixiv 书签」那一档没单独成路由 ⇒ 桌面剥了 pixiv 之后那枚卡无处可落（要么死按钮要么假墙）：",
            "data object PixivBookmarksUnwired : DesktopRoute" in route,
        )
    }

    /** ② 恰好一处切进 hub，且挂在墙顶栏那枚「收藏」把手上。 */
    @Test
    fun `入口恰好一处而挂在用户按得到的把手上`() {
        val cuts = shellLines.filter { "route = DesktopRoute.GalleryBookmarksHub" in it }
        // 两处：① 墙顶栏把手切进 hub；② pixiv 诚实屏的返回回到 hub。两处各有其位，不多不少。
        assertEquals(
            "切进 hub 的赋值不是恰好两处（门一处 + pixiv 返回一处）：$cuts",
            2,
            cuts.size,
        )
        assertTrue(
            "进 hub 那一处不是从 `onOpenBookmarks` 那枚把手里切出去的 ⇒ 那不是用户按得到的门：\n$cuts",
            cuts.any { "onOpenBookmarks = { route = DesktopRoute.GalleryBookmarksHub }" in it },
        )
    }

    /** ③ 渲的是安卓那一份屏（同 module 直调），不是桌面另写的壳。 */
    @Test
    fun `渲的是安卓大屏那一份屏`() {
        val shellCode = DesktopSourceTree.desktopLargeScreenCode()
        val render = shellLines.firstOrNull { "DesktopRoute.GalleryBookmarksHub ->" in it }
        assertTrue(
            "hub 那一支没渲安卓那颗 `GalleryBookmarksHubScreen`（桌面另搭一份就等于没直连）：$render",
            render != null && (
                "com.venera.compose.gallery.ui.GalleryBookmarksHubScreen(" in render ||
                    ("import com.venera.compose.gallery.ui.GalleryBookmarksHubScreen" in shellCode &&
                        "GalleryBookmarksHubScreen(" in render)
                ),
        )
    }

    /** ④ 三枚出口逐一对位，且都不静默。 */
    @Test
    fun `三枚出口都有真落点而 pixiv 那枚不是死按钮`() {
        val block = DesktopSourceTree.desktopLargeScreenCode().lines()
            .dropWhile { "DesktopRoute.GalleryBookmarksHub ->" !in it.trim() }
            .take(30)
            .joinToString("\n")
        // 收藏 hub 改为屏内直接展示本地收藏墙，点击卡片统一走 openGalleryPostAny（Pixiv 进详情页，booru 进看图屏）。
        assertTrue(
            "「本地收藏」那一格没有真落点（点一张图应当进作品详情 / 看图屏）：\n$block",
            "openGalleryPostAny(post)" in block,
        )
        // 收藏档必须由装配点递进来（必传形参；漏传就是编译红，这里再钉一次形状）。
        assertTrue("hub 没吃装配点递下来的收藏档：\n$block", "favorites = ports.favorites," in block)
        // 「Pixiv 书签」→落到**真屏**：pixiv 端口已接线，账号页（PixivAccount）
        // 的「收藏」段就是云端书签（`bookmarkedIllusts`），不再是"还没接"的诚实报屏。
        assertTrue(
            "「Pixiv 书签」那枚出口没落到账号页（真落点）：\n$block",
            "onOpenPixivBookmarks = { route = DesktopRoute.PixivAccount }" in block,
        )
        assertTrue(
            "hub 顶栏的返回没切回 Tab：\n$block",
            "onBack = { route = DesktopRoute.Tab }" in block,
        )
    }
}
