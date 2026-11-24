package com.venera.desktop

import com.venera.desktop.app.DesktopRoute
import com.venera.desktop.app.ForensicFlags
import com.venera.desktop.app.OpenScreenReadout
import com.venera.desktop.app.VeneraDesktopLargeScreen
import com.venera.compose.components.VeneraNavTab
import com.venera.desktop.gallery.ui.DesktopSourceTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `--open=` 那颗起窗直达种子的判读尺。
 *
 * ## 这一枚为什么另起而不复用 `--start-domain`
 *
 * 后者认的是**已拆掉的 Fluent 线**那套 `DesktopDomain`（7 枚），而桌面今天唯一的渲染路是
 * `VeneraDesktopLargeScreen`，它的落点是 `VeneraNavTab`（底栏五格）+ `DesktopRoute`（二级页）。
 * 那批 landing/domain 在 main 里**只 println、没有一个喂进 LargeScreen** —— 这条线上全哑。
 * 把 `--start-domain=COMIC` 硬译成 route 会造出"stdout 念了落点而屏上一步没动"的新假开关。
 * 所以这一枚直接返回那条线**真落得下去**的 `DesktopRoute` / `VeneraNavTab`。
 *
 * ## 钉的坏写法
 *  - 认不出塌成某一屏（会产出一张不是用户点名的屏当证据）；
 *  - 只给名字不带值被当成 HOME（那是"给了却什么都不发生"的假开关）；
 * - 判读层接了而集成方漏喂进 route 初值（这条线要修的就是这一族，必须钉消费点）；
 *  - 认得出的种子没登记进 KNOWN（带它起窗会同时念"未识别"与"认成…"，两条自相矛盾的读数）。
 */
class DesktopOpenSeedTest {

    /** ① 没给：NotGiven、不念一句、不塌进任何屏。 */
    @Test
    fun 没给是NotGiven而不塌进任何屏() {
        assertTrue(ForensicFlags.open(listOf("jm", "--shot=a.png")) is OpenScreenReadout.NotGiven)
        assertNull("没给这一枚时一句都不该说", ForensicFlags.openNote(OpenScreenReadout.NotGiven))
    }

    /** ② 底栏那一格：枚举名（大小写不敏感）与中文题名两种都认，交回 tab 那一枚、route 为空。 */
    @Test
    fun 底栏格认枚举名与中文题名() {
        val byName = ForensicFlags.open(listOf("--open=FAVORITES")) as OpenScreenReadout.Resolved
        assertEquals(VeneraNavTab.FAVORITES, byName.tab)
        assertNull("底栏格这一枚不该同时塞 route", byName.route)
        val byCase = ForensicFlags.open(listOf("--open=gallery")) as OpenScreenReadout.Resolved
        assertEquals(VeneraNavTab.GALLERY, byCase.tab)
        val byTitle = ForensicFlags.open(listOf("--open=探索")) as OpenScreenReadout.Resolved
        assertEquals(VeneraNavTab.EXPLORE, byTitle.tab)
        assertNull("认得出时不该再念落空那句", ForensicFlags.openNote(byName))
    }

    /** ③ 二级页：认那条线真能渲出来的 ASCII 别名，交回 route 那一枚、tab 为空。 */
    @Test
    fun 二级页认稳定别名而交回route() {
        val history = ForensicFlags.open(listOf("--open=HISTORY")) as OpenScreenReadout.Resolved
        assertEquals(DesktopRoute.History, history.route)
        assertNull("二级页这一枚不该同时塞 tab", history.tab)
        val hub = ForensicFlags.open(listOf("--open=bookmarkshub")) as OpenScreenReadout.Resolved
        assertEquals(DesktopRoute.GalleryBookmarksHub, hub.route)
        val search = ForensicFlags.open(listOf("--open=GallerySearch")) as OpenScreenReadout.Resolved
        assertEquals(DesktopRoute.GallerySearch, search.route)
        val settings = ForensicFlags.open(listOf("--open=SETTINGS")) as OpenScreenReadout.Resolved
        assertEquals(DesktopRoute.Settings, settings.route)
    }

    /** ③′ UI-2 取证种子：先落画廊，再由壳在首屏卡到货后打开第一张。 */
    @Test
    fun 画廊看图取证种子先落画廊再等首卡() {
        val viewer = ForensicFlags.open(listOf("--open=GALLERYVIEWER")) as OpenScreenReadout.Resolved
        assertEquals(VeneraNavTab.GALLERY, viewer.tab)
        assertNull("这一枚不直接塞 route：首卡要等真数据到货", viewer.route)
        assertTrue("没把“到货后打开首卡”的附加种子带上", viewer.openFirstGalleryPost)
    }

    /** ④ 认不出：不中止、落回默认屏，但必须念一句实话并列出可选值（绝不猜一屏）。 */
    @Test
    fun 认不出不中止而念一句并给可选值() {
        val r = ForensicFlags.open(listOf("--open=nonsense"))
        assertTrue("认不出该是 Unresolved 而不是塌进某一屏：", r is OpenScreenReadout.Unresolved)
        val note = requireNotNull(ForensicFlags.openNote(r))
        assertTrue("那句实话要点名认不出：", "认不出" in note)
        assertTrue("那句实话要列出可选屏名（否则用户不知道能打什么）：", "BOOKMARKSHUB" in note)
    }

    /** ⑤ 只给名字不带值算没落点（Unresolved），不猜成 HOME。空串同理。 */
    @Test
    fun 不带值的open算认不出而不是猜默认屏() {
        assertTrue(ForensicFlags.open(listOf("--open")) is OpenScreenReadout.Unresolved)
        assertTrue(ForensicFlags.open(listOf("--open=")) is OpenScreenReadout.Unresolved)
    }

    /** ⑥ 登记进 KNOWN（漏登记会被 unknown 念成拼错），且认得出的值不被念成不认识。 */
    @Test
    fun open登记进KNOWN() {
        assertTrue("--open 没登记进 KNOWN", ForensicFlags.KNOWN.contains("--open"))
        assertEquals(
            "--open=HISTORY 被念成不认识的参数",
            emptyList<String>(),
            ForensicFlags.unknown(listOf("--open=HISTORY")),
        )
    }

    /**
     * ⑦ 集成方真把这一枚喂进 route/currentTab 初值（要修的就是"判读层接了而这条线没消费"那一族）。
     *
     * 三处逐个钉：`main` 读 `ForensicFlags.open(` 并把认出的 tab/route 递给 `LargeScreen`；
     * `LargeScreen` 的 `route` / `currentTab` 初值**吃形参而不是写死**；`:app` 任务把 `-Popen` 透传成 `--open`。
     */
    @Test
    fun 集成方把它喂进route初值而非只在main念读数() {
        val main = DesktopSourceTree.desktopEntryLines()
        assertTrue("main 没读这一枚判读：", main.any { "ForensicFlags.open(" in it })
        assertTrue("main 没把它喂进 LargeScreen 的 initialRoute：", main.any { "initialRoute = forensic.initialRoute" in it || "initialRoute = initialRoute" in it })
        assertTrue("main 没把它喂进 LargeScreen 的 initialTab：", main.any { "initialTab = forensic.initialTab" in it || "initialTab = initialTab" in it })
        // LargeScreen：route 初值不再写死 DesktopRoute.Tab，而是吃 initialRoute。
        val shell = DesktopSourceTree.desktopLargeScreenCode()
        assertTrue(
            "route 初值还写死 DesktopRoute.Tab（没吃形参 = 种子里念了而屏上不动，本批要修的正是这一族）：",
            "mutableStateOf<DesktopRoute>(initialRoute)" in shell,
        )
        assertTrue(
            "currentTab 初值还写死 VeneraNavTab.HOME：",
            "mutableStateOf(initialTab)" in shell,
        )
    }

    /** ⑧ `:app` 任务把 `-Popen` 透传成 `--open`（漏这一跳就是"不报错也不做事"的假开关）。 */
    @Test
    fun gradle任务把Popen透传成open参数() {
        val gradle = java.io.File(DesktopSourceTree.repoRoot, "desktop/build.gradle.kts")
        assertTrue("读不到 :app 那颗构建脚本，判据失效（不是通过）：${gradle.path}", gradle.isFile)
        val text = gradle.readText()
        // 透传那一行literal：`providers.gradleProperty("open").orNull?.let { "--open=$it" }`。
        // 用纯子串 `-Popen` 的下游形状 `--open=` 匹配，避开 Kotlin 模板对 `$it` 的解析。
        assertTrue(":app 任务没把 -Popen 透传成 --open：", "--open=" in text)
        assertTrue(":app 任务没读 open 那枚 gradleProperty：", "gradleProperty(\"open\")" in text)
    }
}
