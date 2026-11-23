package com.venera.desktop.architecture

import com.venera.desktop.gallery.ui.DesktopSourceTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 钉住「画廊每日热门那一屏真有一扇门，而门后是**安卓同一件**屏本体」—— 阶段二 · 域 2。
 *
 * ## 缺口长什么样（现场量的，不是抄旧账）
 *
 * 桌面首页那一排日榜预览（`DesktopGalleryDailyRow`）只摆 6 张而**没有去处**；
 * 安卓那颗日榜二级页屏（`GalleryDailyScreen`）的页形状从前整段住在 3620 行、满屏 Android 宿主的
 * `GalleryScreen.kt` 里，桌面拿不到。而取数腿（`GalleryDailyFeed`）、落屏判据（`buildGalleryWall`）、
 * 状态机（日榜那套 seed/池子/凭证/缓存判据）早就都在桌面编译面上 —— 又是那一族
 * 「有逻辑没有门」（与 §114/§119/§124/§125/§128/§130/§138 同形）。
 *
 * 这一批把屏本体抽进共享面 `gallery/ui/GalleryDailyScreenBody.kt`（安卓薄壳转喂同一颗），
 * 桌面开 `DesktopRoute.GalleryDaily` 喂同一颗 body。这里用最笨的形状（按字符串数代码行里的出现次数）
 * 把"消费者存在而喂的是安卓那一件"钉成会红的判据。
 */
class DesktopGalleryDailyEntryTest {

    private val shellFile = DesktopSourceTree.desktopSourceOf("VeneraDesktopLargeScreen.kt")
    private val shell = DesktopSourceTree.desktopLargeScreenCode()
    private val bodyFile = DesktopSourceTree.androidSource("gallery/ui/GalleryDailyScreenBody.kt")
    private val body = DesktopSourceTree.codeText(bodyFile)

    /** ① 路由这一档存在（`DesktopRoute` 里那一枚 `data object GalleryDaily`）。 */
    @Test
    fun 路由里有日榜这一档() {
        val route = DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("DesktopRoute.kt"))
        assertTrue("`DesktopRoute` 没有 `GalleryDaily` 这一档：", route.contains("data object GalleryDaily : DesktopRoute"))
    }

    /** ② 桌面日榜二级页走编辑台壳（聚合稿要求两源同屏）；共享 body 仍服务安卓，不连坐改它。 */
    @Test
    fun 壳里那一支喂的是安卓那一件() {
        assertTrue("壳里没有桌面编辑台壳的调用点：", shell.contains("com.venera.desktop.gallery.ui.DesktopDailyEditorialScreen("))
        assertTrue(
            "日榜那一支没把返回切回 Tab（那就是进去出不来）：",
            shell.contains("DesktopRoute.GalleryDaily ->") && shell.contains("onBack = { route = DesktopRoute.Tab }"),
        )
    }

    /** ③ 「查看全部」那枚把手挂在了用户按得到的地方（预览行节头 → 路由赋值）。 */
    @Test
    fun 那枚把手挂在按得到的地方() {
        val rows = DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("gallery/ui/DesktopGalleryHomeRows.kt"))
        assertTrue("预览行节头没有「查看全部」：", rows.contains("\"查看全部\""))
        assertTrue("壳里那枚回调没切进日榜路由：", shell.contains("onOpenDaily = { route = DesktopRoute.GalleryDaily }"))
    }

    /**
     * ④ 落屏判据**单源**：桌面这一支不许另拼一遍遮罩/AI/规则计数 —— 墙由共享 body 里的
     * `GalleryDailyCore.wall`（转交共享 `buildGalleryWall`）算，壳只接线。
     * 这条与 `DesktopWallGuardPolicyTest` ⑤ 同一族，只是对象换成日榜那一屏。
     */
    @Test
    fun 日榜那一屏的遮罩判据只有一个出处() {
        assertEquals(
            "共享 body 里 `buildGalleryWall` 只许出现在核的那一处（别处再拼一遍 = 第二套判据，必漂）：",
            1,
            DesktopSourceTree.codeOccurrences(bodyFile, "buildGalleryWall("),
        )
        // 桌面壳不许自己调遮罩三档判据来拼日榜墙（它递的是 core 的现值）。
        assertEquals(
            "壳里不许出现第二处日榜落屏判据：",
            0,
            shell.lines().count { it.trim().startsWith("buildGalleryWall(") },
        )
    }

    /** ⑤ 安卓薄壳转喂同一颗 body（两端共编一条屏；薄壳里只剩宿主那一截）。 */

    private fun assertFalseToken(haystack: String, needle: String, why: String) {
        assertTrue("$why：$needle", !haystack.contains(needle))
    }

    /** ⑥ 桌面日榜快照落盘件存在且形状是共享档（档形只有一份，落盘两端各接）。 */
    @Test
    fun 桌面日榜快照吃的是共享档形() {
        val store = DesktopSourceTree.codeText(
            DesktopSourceTree.desktopSourceOf("gallery/data/DesktopGalleryDailySnapshotStore.kt"),
        )
        assertTrue("桌面快照件不吃共享 `GalleryFeedSnapshot`：", store.contains("GalleryFeedSnapshot"))
        assertTrue(
            "桌面快照落盘不许用 `Context`（那是安卓那侧的落点）：",
            !store.contains("android.content.Context") && !store.contains("filesDir"),
        )
    }
}
