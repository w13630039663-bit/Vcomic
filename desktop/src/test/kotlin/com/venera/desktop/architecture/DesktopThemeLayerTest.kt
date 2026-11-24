package com.venera.desktop.architecture

import com.venera.desktop.app.DesktopLargeScreenChromeScaffold
import com.venera.desktop.app.DesktopLargeScreenProviders
import com.venera.desktop.app.VeneraDesktopLargeScreen
import com.venera.desktop.gallery.ui.DesktopSourceTree
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 第 140 批 · 桌面**主题层**的接线尺。
 *
 * ## 这一族为什么必须有尺
 *
 * 桌面与安卓"UI 差距过大"的真根因不是缺件 —— §126 起两端连的都是**逐字同一份**共享件
 * （`VeneraSideBar` / `VeneraCard` / `VeneraCover`）。缺的是**主题层**：安卓那边由
 * `feature/VeneraTheme.kt` 同时 provide 五枚 Local 并把色板喂进 `MiuixTheme(colors=…)`，
 * 而桌面外层那枚 `MiuixTheme` 是**空参**调用 ⇒ 那些件全在穿"miuix 库默认衣服"。
 *
 * 这一格最坏的坏法不是红，而是**静默回到旧形状**：把那一行换回 `MiuixTheme {`，
 * 编译照过、全量照绿（色板不参与任何语义树判据），只有人眼看得出来。所以判据只能钉源码形状。
 *
 * ## 四条各挡一种坏写法
 *
 *  ① 壳里那一行是 `DesktopVeneraTheme`，而空参 `MiuixTheme {` 一颗不剩（挡"改回旧的更省事"）；
 *  ② 主题包住内容：那一行必须**早于** `VeneraSideBar(`（挡"provide 写在内容之后＝白接"）；
 *  ③ 薄壳只做转发：不许出现色值字面量、不许自己算明暗（挡它长成第二套主题 ——
 *     与 `DesktopSurfaceLiterals` 那条"色板只有一处出处"同一纪律，只是宿主换成主题层）；
 *  ④ 壳里**不出现第二处** token/材质 provide（挡"两处各 provide 一枚，早晚分叉"）。
 */
class DesktopThemeLayerTest {

    private val shellText = DesktopSourceTree.desktopLargeScreenCode()
    private val shellCode = DesktopSourceTree.desktopLargeScreenCode()
    private val bridge = DesktopSourceTree.desktopSourceOf("gallery/ui/DesktopThemeBridge.kt")
    private val bridgeCode = DesktopSourceTree.codeText(bridge)

    /** ① 主题壳换了名字，而旧那枚空参调用一颗不剩。 */
    @Test fun 壳里吃的是安卓那一份主题而空参MiuixTheme一颗不剩() {
        assertEquals("`DesktopVeneraTheme {` 应恰一处（多一处就是第二层壳）：",
            1, DesktopSourceTree.desktopLargeScreenOccurrences( "DesktopVeneraTheme {"))
        assertEquals("旧的**空参** `MiuixTheme {` 必须一颗不剩（留着＝色板又回库默认）：",
            0, DesktopSourceTree.desktopLargeScreenOccurrences( "MiuixTheme {"))
        assertEquals("壳里不许还 import 那枚没人用的 `MiuixTheme`：",
            0, DesktopSourceTree.desktopLargeScreenOccurrences( "import top.yukonga.miuix.kmp.theme.MiuixTheme"))
        assertTrue("壳必须 import 薄壳本体（不 import 就接不上）：",
            DesktopSourceTree.desktopLargeScreenOccurrences("import com.venera.desktop.gallery.ui.DesktopVeneraTheme") >= 1)
    }

    /**
     * ② 顺序判据：主题包住内容，不是排在内容之后。
     *
     * 为什么不用"出现次数"而用**行位置**：`CompositionLocalProvider` 与主题壳是两层，
     * 把 `DesktopVeneraTheme {` 挪到 `VeneraSideBar(` 之后照样是一处、照样编译过，
     * 但侧栏那一圈就吃不到色板 —— 与本仓 §139 那条"装 tee 早于读数"同一族量法。
     */
    @Test fun 主题壳包住了侧栏而不是排在它后面() {
        val mainShell = DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("app/VeneraDesktopLargeScreen.kt"))
        val themeAt = mainShell.lines().indexOfFirst { it.contains("DesktopLargeScreenProviders(") }
        val sideBarAt = mainShell.lines().indexOfFirst { it.contains("DesktopLargeScreenChromeScaffold(") }
        assertTrue("量具失效：壳里找不到主题那一行或侧栏那一行 theme=$themeAt sidebar=$sideBarAt",
            themeAt >= 0 && sideBarAt >= 0)
        assertTrue("主题壳必须早于侧栏（否则侧栏吃不到色板＝接了个寂寞）：$themeAt vs $sideBarAt",
            themeAt < sideBarAt)
    }

    /** ③ 薄壳只做转发：不写色值、不自己算明暗、不另起一套 token。 */
    @Test fun 薄壳只做转发不许长成第二套主题() {
        assertEquals("薄壳里不许出现色值字面量（色板只有一处出处）：",
            0, DesktopSourceTree.codeOccurrences(bridge, "Color(0x"))
        assertEquals("薄壳里不许自己算系统明暗（明暗只转交 DesktopThemeMode 的读数）：",
            0, DesktopSourceTree.codeOccurrences(bridge, "isSystemInDarkTheme"))
        // 涟漪禁令必须住在主题之下、内容之上：两套主题各自重挂按压指示器会把窗根那道禁令盖掉。
        // 所以这一层只许那一枚 provide；主题 token / 材质仍一枚不许在这里长出来。
        val providers = DesktopSourceTree.codeLinesOf(bridge).filter { it.contains(" provides ") }
        assertTrue("薄壳只许涟漪禁令那一枚 provide，多一枚就是第二套主题：$providers",
            providers.size == 1 && providers.single().contains("LocalRippleConfiguration"))
        // 正向：三枚实参必须**显式**递，且被复用的是共享件而不是第二份色板。
        assertEquals("复用共享件恰一处：",
            1, DesktopSourceTree.codeOccurrences(bridge, "VeneraPreviewTheme("))
        // 校验明暗状态仅从 DesktopThemeMode.isDark 转发，桥层不自行计算或硬编码主题。
        assertEquals("明暗转交恰一处（来源只有 DesktopThemeMode.isDark）：",
            1, DesktopSourceTree.codeOccurrences(bridge, "DesktopThemeMode.isDark("))
        for (literal in listOf(
            "appearance = AppearanceStyle.MD3",
            "dark = dark",
            "material = SurfaceMaterial.SOLID",
        )) {
            assertEquals("$literal 应恰一处（材质与风格仍钉死；明暗只转交偏好）：",
                1, DesktopSourceTree.codeOccurrences(bridge, literal))
        }
    }

    /**
     * ④ 壳里那一族的 provide 只有一条路：token/材质住薄壳，`LocalVeneraDarkTheme` 留壳里。
     *
     * ⚠️ 最后一条是**如实登记的一处不整齐**，不是漏做：被复用那颗件的类注释自己写着
     * "这里没有 provide `LocalVeneraDarkTheme`（它声明在 `feature/VeneraTheme.kt`，而 `ui/tokens`
     * 从不反向 import `feature`）"。所以桌面只能在壳里单独递它（第 1857 行那一枚，恒 true）。
     * 判据钉的是"这个例外只有一处"，而不是"没有例外"—— 后者要把 `app/` 那侧的 import 面改掉，
     * 属受保域，一字未动。
     */
    @Test fun token那一族的provide只有一条路() {
        for (literal in listOf(
            "LocalVeneraTokens provides",
            "LocalVeneraColorTokens provides",
            "LocalSurfaceMaterial provides",
        )) {
            assertEquals("壳里不许出现 $literal（由薄壳那一条路提供，两处各 provide 早晚分叉）：",
                0, DesktopSourceTree.desktopLargeScreenOccurrences( literal))
        }
        assertEquals("`LocalVeneraDarkTheme` 那一枚例外应恰一处（薄壳不递它，见其类注释）：",
            1, DesktopSourceTree.desktopLargeScreenOccurrences( "LocalVeneraDarkTheme provides"))
    }

    /**
     * ⑤ 离屏台架与运行时**同后端**（量出来的一条真账，也是最大的一块改动）。
     *
     * `VeneraControls.kt` 那 8 处 `useMiuixWidgets` 分派读 `VeneraTokens.appearance`（`:62`），
     * 而 `LocalVeneraTokens` 的**默认值**是 `MIUIX`（`VeneraTokens.kt:133-141`，它自己的注释写着
     * "默认值保证预览/单测无需主题也能取到合法 Token"）⇒ 台架不套主题壳就等于**永远量 miuix 那一档**，
     * 而桌面运行时从起钉 `MD3` ⇒ 走 `Md3Button`/`Md3AlertDialog`/`Md3Checkbox` 那一档。
     * 后果不是红，而是本仓最恨的那族：**台架全绿而它量的是运行时不用的那一档**（假证据）。
     *
     * ## 判据形状：数**裸 `setContent {`** 而不是数套壳
     *
     * 白名单之外一颗不许留。为什么这么数：`setContentThemed {` 不含字面量 `setContent {`
     * （中间多了 `Themed `），所以"漏了一颗"与"用了入口"这两件事**天然分得开**，不需要维护行号清单。
     *
     * ## 白名单共 23 颗 = 本尺自身 + 入口本体 + 9 颗刻意例外 + 12 颗待迁移历史测试
     *
     * 每颗的理由写在这里 —— 不许当成"待办"顺手清掉。
     *
     *  - `DesktopTestTheme.kt` —— 入口**本体**，它体内那句就是 `setContent { DesktopVeneraTheme(…) }`。
     *  - `DesktopSharedProbeTest.kt` —— Compose **版本升级哨兵**：要量的是"裸 API 还跑不跑得动"，加一层就废了哨兵。
     *  - `DesktopAccentFillTest.kt` —— 量的是 `composefluent` 那家**库的 accent 现状**（`FluentTheme` 是它的被测量），
     *    套壳会改变被测量本身。
     *  - `DesktopZoomableOffsetTest.kt` / `DesktopSnapshotFlowObservationTest.kt` /
     *    `DesktopReaderPagingTest.kt` / `DesktopReaderKeyTest.kt` / `DesktopReaderMagnifierLensTest.kt` /
     *    `DesktopReaderViewportSizeTest.kt` —— **机制探针**（自己的 `ZoomState`/`PagerState`/快照流/镜片几何/
     *    视口尺寸），只摆被测件、不渲染桌面屏，也就没有"后端档位"这回事；套壳反而会改变被测量本身。
     *  - `DesktopGalleryFavoritesClearSemanticsTest.kt` —— 自带最小被测件（`BasicText` + `clickable`），
     *    不消费 `VeneraControls` 那一族。
     * ## 为什么只扫**含 `runComposeUiTest` 的文件**
     *
     * 两个理由。① 语义：这条判据管的是"离屏台架量哪一档后端"，源码尺与仓库层用例根本不摆屏，
     * 把它们列进白名单是往名单里塞噪音。② 更实际的一格：本文件自己的判式与白名单里**就写着**
     * `"setContent {"` 那串字面量 —— 扫全树会把这把尺自己打成红（"扫描类判据把自身口径喂饱"
     * 那一族，本仓记过不止一次）。⚠️ 只加"含 runComposeUiTest"那一道**挡不住**：本文件那两行
     * `.filter { … "runComposeUiTest" … }` / `.filter { … "setContent {" … }` 本身就是**代码行**、
     * 里面就带着这两个被数字面量（第一版就是被自己打成红的，读数 `DesktopThemeLayerTest.kt:2`）。
     * 所以本文件名必须进白名单 —— 这是"判式所在处"的固有例外，不是漏做，也不许靠"改成数正则"糊过去。
     */
    @Test fun 台架不许量运行时不用的那一档后端() {
        val whiteList = setOf(
            "DesktopThemeLayerTest.kt",
            "DesktopTestTheme.kt",
            "DesktopSharedProbeTest.kt",
            "DesktopAccentFillTest.kt",
            "DesktopZoomableOffsetTest.kt",
            "DesktopSnapshotFlowObservationTest.kt",
            "DesktopReaderPagingTest.kt",
            "DesktopReaderKeyTest.kt",
            "DesktopReaderMagnifierLensTest.kt",
            "DesktopReaderViewportSizeTest.kt",
            "DesktopGalleryFavoritesClearSemanticsTest.kt",
            // 待迁移至 setContentThemed 的历史测试白名单：新增测试禁止入列，迁移时须连续两遍全量测试通过后再逐个移除。
            "DesktopBackupScreenUiTest.kt",
            "DesktopComicContinueReadingUiTest.kt",
            "DesktopComicHistoryScreenUiTest.kt",
            "DesktopComicSearchUiTest.kt",
            "DesktopCommentsScreenUiTest.kt",
            "DesktopFavoriteFoldersUiTest.kt",
            "DesktopGallerySavedScreenUiTest.kt",
            "DesktopGallerySearchUiTest.kt",
            "DesktopGalleryWallUiTest.kt",
            "DesktopLocalComicLibraryUiTest.kt",
            "DesktopNewScreensSemanticsTest.kt",
            "DesktopReadingStatsUiTest.kt",
        )
        val testRoot = File(DesktopSourceTree.repoRoot, "desktop/src/test")
        check(testRoot.isDirectory) { "读不到测试树：${testRoot.path}" }
        val offenders = testRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name !in whiteList }
            .filter { DesktopSourceTree.codeOccurrences(it, "runComposeUiTest") > 0 }
            .filter { DesktopSourceTree.codeOccurrences(it, "setContent {") > 0 }
            .map { it.relativeTo(DesktopSourceTree.repoRoot).path + ":" + DesktopSourceTree.codeOccurrences(it, "setContent {") }
            .toList()
        // ⚠️ `.toList()` 那一句不是风格问题：`assertEquals(emptyList(), <Sequence>)` **永远不等**，
        // 那会让这条判据在"台架全干净"时也红（红得没有信息量，与本仓那条"量具坏了"同族）。
        assertEquals(
            "这些台架还在用裸 setContent（= 量的是 miuix 那一档，而运行时走 MD3；" +
                "要么换成 setContentThemed，要么把理由写进上面的白名单再改判）：",
            emptyList<String>(),
            offenders,
        )
    }

    /**
     * ⑥ 统一入口那颗 helper 真把**运行时同一枚**壳套上（不许它自己长成第三套主题）。
     *
     * 挡两种坏法：入口被改成"另配一套色板"（那两端就又分叉了，白做）；
     * 或入口里的壳被删掉、只剩 `setContent`（那 ⑤ 那条还会绿，但"同后端"这件事已经悄悄没了）。
     */
    @Test fun 那枚入口用的就是运行时同一颗壳() {
        val entry = File(DesktopSourceTree.repoRoot, "desktop/src/test/kotlin/com/venera/desktop/DesktopTestTheme.kt")
        check(entry.isFile) { "读不到台架入口：${entry.path}" }
        assertEquals("入口必须恰一处套上运行时那枚壳：",
            1, DesktopSourceTree.codeOccurrences(entry, "DesktopVeneraTheme("))
        assertEquals("入口自己不许写色值（色板只有一处出处）：",
            0, DesktopSourceTree.codeOccurrences(entry, "Color(0x"))
        // 反向：不许在测试树里出现第二份"自己拼 MiuixTheme/colors=" 的入口。
        assertEquals("入口不许自己调 MiuixTheme（那是被复用那颗件的活）：",
            0, DesktopSourceTree.codeOccurrences(entry, "MiuixTheme"))
    }
}
