package com.venera.desktop.architecture

import com.venera.desktop.gallery.ui.DesktopSourceTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 钉住「收藏屏第一次有分段，画廊收藏那一档第一次上了墙」。
 *
 * ## 这一族为什么必须有尺
 *
 * `projectFavorites`、「清空」那三枚纯件 + runner（批 77）与那颗 `clear` 写口
 * 全在编译面里绿着，而活渲染路**没有一屏**消费它们 —— 与 §114/§115/§118/§124/§138 逐批记过的
 * 「有逻辑没有门」同一形状，成因也同一条：旧判据只扫算式本身，扫不出"有没有人按"。
 * 这一把尺钉的是**接线**，门掉了它就红。
 *
 * ## 五条各挡一种坏写法
 *
 * ① hub 那颗屏真存在、真按档渲三屏（本地转交原屏 / 画廊渲新墙 / 网络是坦白页不是空壳）；
 * ② 壳里 hub 恰一处被 call（新门开了却没人渲 = §118 那一族）；
 * ③ 投卡腿 `desktopGalleryFavoritesWallCards` 里**只有转交**，守卫起口仍是壳外那颗
 *   （壳里 `desktopWallGuardedCards(` 仍恰一处 —— §150 数过的那把账不许被这条新腿漂成两处）；
 * ④ 两步清空逐枚点到批 77 那三枚纯件与那颗 runner（摆枚按下去不落盘的清空 = 假开关）；
 * ⑤ 投卡 effect 的键里有分段与存档条数（不按键投 = 存档变了墙不改口）。
 */
class DesktopGalleryFavoritesWallEntryTest {

    private val hubFile = File(
        DesktopSourceTree.repoRoot,
        "desktop/src/main/kotlin/com/venera/desktop/favorites/DesktopFavoritesHubScreen.kt",
    )
    private val wallFile = File(
        DesktopSourceTree.repoRoot,
        "desktop/src/main/kotlin/com/venera/desktop/gallery/ui/DesktopGalleryFavoritesWall.kt",
    )
    private val shellFile = File(
        DesktopSourceTree.repoRoot,
        "desktop/src/main/kotlin/com/venera/desktop/VeneraDesktopLargeScreen.kt",
    )

    private fun codeOf(f: File): String {
        assertTrue("读不到 ${f.name}，判据失效（不是通过）", f.isFile)
        return DesktopSourceTree.codeText(f)
    }

    @Test
    fun `hub 按档渲三屏`() {
        val hub = codeOf(hubFile)
        assertTrue("hub 没渲本地那颗原屏：", hub.contains("DesktopFavoritesScreen("))
        assertTrue("hub 没渲画廊那一档那颗新墙：", hub.contains("DesktopGalleryFavoritesWallScreen("))
        // 口径改：
        // 旧页壳 DesktopPageShell 白卡标题条退役，页头改内容页小标题 + 源面板主从；
        // 出路（返回）本就由壳层接管（主 Tab 那枚把手从未上过屏），hub 不再接 onBack。
        assertTrue("hub 没走主从源面板：", hub.contains("FavoritesSourcePanel("))
        assertTrue("hub 没接源选中回调：", hub.contains("onPickSource = onPickSource,"))
        assertTrue(
            "网络那一档现在渲真手风琴屏（2026-10-30 tool-suite 稿①：引擎桥补了 favorites 三跳，" +
                "桌面接真了，不再念「还没接」那句坦白话；屏里 sources 为空仍那句出路）：",
            hub.contains("DesktopNetworkFavoritesScreen("),
        )
        // 三档名逐一点名（分段器按 entries 画，档少一枚就是屏上缺一路）。
        listOf("LOCAL", "GALLERY", "NETWORK").forEach { name ->
            assertTrue("hub 缺 $name 那一档：", hub.contains("$name("))
        }
    }

    @Test
    fun `壳里 hub 恰一处被渲`() {
        assertEquals(
            "壳里调 `DesktopFavoritesHubScreen(` 恰一处（零=门开了没人渲，两=长出第二颗 hub）：",
            1,
            DesktopSourceTree.desktopLargeScreenOccurrences("DesktopFavoritesHubScreen("),
        )
        // 原先 FAVORITES 那一格直连原屏；hub 上位后那一处调用必须**搬进 hub 里**而壳里只余转交形参。
        assertTrue(
            "壳里 FAVORITES 那一支没把本地九枚原样转交（shownFavorites 一枚都不许少）：",
            DesktopSourceTree.desktopLargeScreenOccurrences("shownFavorites = favoritesShown") == 1,
        )
    }

    @Test
    fun `投卡腿只转交 壳里守卫起口仍恰一处`() {
        val wall = codeOf(wallFile)
        assertTrue(
            "投卡腿必须转交那颗唯一守卫起口（不转交=另起一条遮罩路，§150 那条 B 族最忌）：",
            wall.contains("desktopWallGuardedCards(posts, quality, guard, maskMode, blockAi)"),
        )
        assertEquals(
            "壳里 `desktopWallGuardedCards(` 必须仍恰一处（画廊收藏走的是转交腿而不是壳里直调）：",
            1,
            DesktopSourceTree.desktopLargeScreenOccurrences("desktopWallGuardedCards("),
        )
    }

    @Test
    fun `两步清空逐枚接上批七十七那几颗件`() {
        val shell = DesktopSourceTree.desktopLargeScreenCode()
        listOf(
            "desktopFavoritesClearNext(",
            "desktopFavoritesClearLabel(",
            "desktopFavoritesClearVisible(",
            "desktopClearGalleryFavorites(",
            "desktopFavoritesClearReading(",
        ).forEach { sym ->
            assertTrue("壳里没按 $sym —— 清空摆着而落不了盘或念不出读回：", shell.contains(sym))
        }
        // 真清那一笔后扳 tick 重投（清完墙不改口 = 屏上仍念着已删掉的那些）。
        assertTrue(
            "清空落定后没有 galleryWallTick++ —— 墙不会跟着存档改口：",
            shell.contains("galleryWallTick++"),
        )
    }

    @Test
    fun `投卡 effect 的键里有分段与存档条数`() {
        val shell = DesktopSourceTree.desktopLargeScreenCode()
        // 收藏屏按**源键**分档（`FAVORITES_SOURCE_GALLERY`），
        // 键从 `favoritesSegment` 换成 `favoritesSourceKey`；「只在画廊那一档才投」的守卫
        // 仍在 effect 体内（下面第二条断言钉它）。存档条数与 tick 两枚主键不许缺。
        val investLine = shell.lineSequence().firstOrNull { it.contains("LaunchedEffect(favoritesSourceKey") }
        assertTrue(
            "投卡 effect 键里必须同时有源键、存档条数与 tick（缺一条就有对应的不改口病）：",
            investLine != null &&
                listOf("favoritesSourceKey", "galleryFavorites.size", "galleryWallTick").all { investLine!!.contains(it) },
        )
        assertTrue(
            "没站进画廊那一档不许投卡（白跑一趟守卫）：",
            shell.contains("favoritesSourceKey != com.venera.desktop.favorites.FAVORITES_SOURCE_GALLERY"),
        )
    }
}
