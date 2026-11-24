package com.venera.desktop.architecture

import com.venera.desktop.app.VeneraDesktopLargeScreen
import com.venera.desktop.ui.DesktopGalleryWallPane
import com.venera.desktop.ui.DesktopScrollAnchor
import com.venera.desktop.ui.rememberRetainedLazyListState
import com.venera.desktop.gallery.ui.DesktopSourceTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 画廊那面墙的**滚动位置住在壳那层**（源码尺）。
 *
 * ## 缺口长什么样（现量，不是猜）
 *
 * `DesktopGalleryWallPane` 是 `when(route)` 里 `Tab -> GALLERY` 那一支；点开一张卡进 §130 那枚
 * `GalleryPostViewer` 时那一支整个**离组合**，`LazyStaggeredGridState` 自己活不过组合销毁
 * （对岸在 `PixivAccountSessionStore.kt:40` 逐字记过这条）—— 于是按返回回到 Tab 时墙永远从头排起。
 * §124 收尾点名「回看时保住原来看到第几张」那一格正是这一件事，而**没有一把尺盯着 state 住哪一层**：
 * 撤掉上提那一行（把 `remember` 搬回 pane 里）不会红任何一颗既有判据。这是「假口径继续当有人盯着」
 * 那族病（手册 §4.1），现量到的时候是**接了形参而没人递 state** 的镜像 —— 传了而住在错的层。
 *
 * ## 为什么不用无头 UI 尺
 *
 * 无头台架里"离开组合再回来"要跨两次 `setContent`，中间那一次销毁掉的正是"回来时的落点"本身，
 * 硬测就会测到"新的 state 从 0 起"而不是"旧的 state 回来了" —— 那条测得了的形状与要钉的东西正交。
 * 交源码尺钉接线（与卡可点、handoff 那两格同一族），无头 UI 只测把手与页尾那几格（`DesktopGalleryWallUiTest`）。
 *
 * ## 判据（每一条都会因"接线漂了"而红，不会因"读空"而恒真）
 *
 *  ① 壳层 `val wallListState = androidx.compose.foundation.lazy.rememberLazyListState()` 恰一处（state 住在**装 `when(route)` 那一层**）；
 *  ② 壳层 `gridState = wallListState` 恰一处（调用点真把它递给 pane，而不是各造各的）；
 *  ③ pane → scaffold 与 scaffold → grid 那两跳各恰一处（`gridState = gridState` / `state = gridState`）；
 *  ④ pane 与 scaffold 两颗函数体里 `rememberLazyStaggeredGridState()` **零处** —— 这是**反面对照**，
 *     少了这一条整族判据就是恒真（把 state 搬回 pane 里，①②③④ 一起绿而缺口原封不动回来）；
 *  ⑤ pane 与 scaffold 两颗签名 `gridState: androidx.compose.foundation.lazy.LazyListState,` 各恰一处 —— 与 ④ 一对，钉住
 *     "state 只能住在调用点那侧"，形参带默认值（`DesktopCallbackParamLedgerTest` 兜不住这种形状：
 *     那把尺只盯**函数类型**默认值，非函数类型的 state 默认值它会放行）。
 */
class DesktopWallScrollRetentionTest {

    private val shellFile = DesktopSourceTree.desktopSourceOf("app/VeneraDesktopLargeScreen.kt")
    /** 批 4：pane 与 scaffold 已拆到成员文件；跨文件接线按大屏成员表合并读，壳层那两处仍指主文件。 */
    private val shell get() = DesktopSourceTree.desktopLargeScreenCode()
    private val shellLines get() = DesktopSourceTree.desktopLargeScreenLines().filter { it.isNotEmpty() }

    /** 屏体那一段：`internal fun DesktopGalleryWallPane(` 起到文件尾（pane + scaffold 两颗 composable）。 */
    private val paneBody get() = shell.substringAfter("internal fun DesktopGalleryWallPane(")

    /** ① 壳层只持轻量锚点 `val wallListAnchor = remember { DesktopScrollAnchor() }`，不在壳层直接长驻 LazyListState 锁死离场节点树。 */
    @Test
    fun state住在壳那一层而不是屏里() {
        assertEquals(
            "壳里 `val wallListAnchor = remember { DesktopScrollAnchor() }` 该恰一处：",
            1,
            DesktopSourceTree.desktopLargeScreenOccurrences( "val wallListAnchor = remember { DesktopScrollAnchor() }"),
        )
        assertEquals(
            "壳层不应直接长驻 `rememberLazyListState()`（会锁死离场 LayoutNode 子树与 Skia 对象）：",
            0,
            DesktopSourceTree.desktopLargeScreenOccurrences( "rememberLazyListState()"),
        )
    }

    /** ② 调用点在 GALLERY 分支内经 `rememberRetainedLazyListState(wallListAnchor)` 构造并递给 pane。 */
    @Test
    fun 调用点把壳层state递给pane() {
        assertEquals(
            "壳里 `gridState = rememberRetainedLazyListState(wallListAnchor)` 该恰一处（`when(route).Tab.GALLERY` 那一支）：",
            1,
            DesktopSourceTree.desktopLargeScreenOccurrences( "gridState = rememberRetainedLazyListState(wallListAnchor)"),
        )
    }

    /** ③ pane → scaffold → grid 那两跳各恰一处（中间一没递 = 形参接了而没消费 = 读空假绿）。 */
    @Test
    fun pane与scaffold各一跳递下去到grid() {
        assertEquals(
            "pane 递 scaffold 那一行 `gridState = gridState` 该恰一处：",
            1,
            DesktopSourceTree.desktopLargeScreenOccurrences("gridState = gridState"),
        )
        // 改口径 1→2：右下角「回到顶部」悬浮钮（DesktopBackToTopFab）也吃这同一颗
        // hoist 的 state（`state = gridState`）—— 它是读点不是第二份 state，与观察器同族。
        assertEquals(
            "scaffold 递 grid 那一行 `state = gridState` 该恰两处（GalleryForYouPage 一处 + 回顶 FAB 一处；" +
                "少了就是形参接而不喂，state 白拿；阶段二 · 域 2 日榜那一支递的是自己 hoist 的 " +
                "`dailyGridState`，不占这一格账）：",
            2,
            DesktopSourceTree.desktopLargeScreenOccurrences("state = gridState"),
        )
    }

    /**
     * ④ 反面对照（这一条最贵，缺了它整族判据都是恒真）：pane 与 scaffold 两颗函数体里
     * `rememberLazyStaggeredGridState()` **零处**。
     *
     * 若哪天有人把 state 搬回屏里（`val g = rememberLazyStaggeredGridState()` 那种），①②③⑤ 依然全绿
     * —— 因为那四把钉的是"壳里有一处"与"形参接得上"，而那些形状都还在 —— 而缺口原封不动回来。
     * 这一条把那种"形参接了而屏里又自己造一颗"的绕过封住。
     */
    @Test
    fun pane与scaffold两颗函数体里绝不自己造state() {
        assertEquals(
            "`internal fun DesktopGalleryWallPane(` 起到文件尾那一段（含 pane 与 scaffold 两颗函数体）里，" +
                "出现 `rememberLazyStaggeredGridState()` 就是 state 从壳层搬回屏里、" +
                "一回看图屏离组合就丢的那格病回来了：",
            0,
            paneBody.split("rememberLazyStaggeredGridState()").size - 1,
        )
    }

    /**
     * ⑤ 两颗签名各有一枚**必传** `gridState: androidx.compose.foundation.lazy.LazyListState,` —— 恰两处（pane + scaffold 各一处）。
     *
     * 逗号收尾这一形**排除带默认值那种写法**（`gridState: LazyStaggeredGridState = remember…` 不含逗号结尾
     * 的精确子串，`codeOccurrences == 2` 会掉到 <2）。而 `DesktopCallbackParamLedgerTest` 只管**函数类型**
     * 默认值（那条判式是 `名字: (…) -> … = …`），非函数类型的 state 默认值它会放行 —— 那一族的漏项在这里补上。
     */
    @Test
    fun 两颗签名各持一枚必传gridState() {
        assertEquals(
            "`gridState: androidx.compose.foundation.lazy.LazyListState,` 该恰两处（pane + scaffold 各一处；一处都没有=接线整段没做，" +
                "两处而多带 `=` 默认值=逗号形状不匹配、这里掉数会先红）：",
            2,
            DesktopSourceTree.desktopLargeScreenOccurrences("gridState: androidx.compose.foundation.lazy.LazyListState,"),
        )
    }

    /** ⑥ 三处赋值逐字都在**壳层那颗 state** 的下游（不是各造各的 state 名字在漂）。 */
    @Test
    fun 三处接线都指回同一颗state() {
        val refs = shellLines.filter { "gridState" in it && "LazyStaggeredGridState" !in it }
        // 校验 7 处滚动状态传递与 2 处近尾续页读点均复用壳层提升的 gridState，禁止屏内自建第二份状态。
        assertTrue("接线少一处或多一处（形参接而不递=读空假绿）：$refs", refs.size == 7)
        assertTrue("调用点那一处必须真指到 `wallListAnchor` 恢复出的状态：$refs", refs.any { "gridState = rememberRetainedLazyListState(wallListAnchor)" in it })
        assertTrue("pane 递 scaffold 那一处必须原样转：$refs", refs.any { "gridState = gridState" in it })
        assertTrue("scaffold 递 grid 那一处必须喂给 LazyGrid 的 state= 参数：$refs", refs.any { "state = gridState" in it })
    }

    /** ⑦ 日榜那一支随聚合编辑台批改成两栏纵向滚动：滚动量由 EditorialScreen 递进来，壳里不许再留 grid state 孤儿。 */
    @Test
    fun 日榜那一支的滚动量由编辑台递进来() {
        val editorial = DesktopSourceTree.codeText(
            DesktopSourceTree.desktopSourceOf("gallery/ui/DesktopDailyEditorialScreen.kt"),
        )
        assertTrue(
            "编辑台的续页观察器没吃递进来的 scroll（续页与滚动脱钩）：",
            editorial.contains("androidx.compose.foundation.ScrollState") && editorial.contains("snapshotFlow { scroll.value }"),
        )
        assertEquals(
            "壳里又长出了日榜 grid state 的孤儿定义（编辑台批后这一格不该再有）：",
            0,
            DesktopSourceTree.desktopLargeScreenOccurrences( "val dailyGrid = rememberLazyStaggeredGridState()"),
        )
    }
}
