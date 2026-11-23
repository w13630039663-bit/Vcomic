package com.venera.desktop.architecture

import com.venera.desktop.gallery.ui.DesktopSourceTree
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 钉住「桌面活路径第一次有了设置那一屏」（主人 goal「接着干设置」）。
 *
 * ## 这一把尺为什么必须存在（本仓那一族「代码有而门没开」的账）
 *
 * 八枚画廊偏好的读写口（`DesktopGalleryPreferences` 的 setter 与读流）与分级遮罩档
 * （`DesktopGalleryContentGuard.setNsfwMaskMode`）从批 46/52/61/63 起就都有别的判据盯着，
 * 而**当前这条活渲染路里没有任何一屏能把它们摆出来** —— 唯一含那些把手的
 * `DesktopGallerySettingsPane.kt` 是已拆掉的 Fluent 平行线遗骸（`io.github.composefluent` import、
 * `@Composable` 在这条路上零调用）。于是「账上记着能改」与「用户够得着」之间没有线。
 * §143 那批补上屏 + 门 + 接线，这一把尺钉住那根线**不许再断回去**（§118 那一次
 * 「README 说接上了而屏上从没画过」的教训同形）。
 *
 * ## 判据（四格各钉一件事）
 *
 * 1. 路由档真存在（`DesktopRoute.Settings`）；
 * 2. 壳里那一支真**渲**那颗屏（`when (route)` 的分支不是 `else` 兜出来的，本仓逐一点名那条）；
 * 3. 屏内逐枚点到八枚 setter —— 与 `DesktopGallerySettingsSpec.editable` **同源**：
 *    账上加一枚而不接线，这里就红；屏上加一行而账上没有，`DesktopGallerySettingsSpecTest` ①③ 红；
 * 4. 探索屏顶栏那颗入口摆出了「设置」那两个字（与「备份与还原」同族：门恒摆，
 *    装配点没递上件时那一支自己念成因，不是让门按数据消失）。
 */
class DesktopSettingsEntryTest {

    private val routeFile = File(
        DesktopSourceTree.repoRoot,
        "desktop/src/main/kotlin/com/venera/desktop/DesktopRoute.kt",
    )
    private val shellFile = File(
        DesktopSourceTree.repoRoot,
        "desktop/src/main/kotlin/com/venera/desktop/VeneraDesktopLargeScreen.kt",
    )
    // 设置屏换成主从双栏中枢（DesktopSettingsHub）。
    // 八枚 setter 的调用点从旧屏（DesktopGallerySettingsScreen，仍在编译面里当规格件）挪进了中枢。
    private val screenFile = File(
        DesktopSourceTree.repoRoot,
        "desktop/src/main/kotlin/com/venera/desktop/settings/DesktopSettingsHub.kt",
    )
    private val exploreFile = File(
        DesktopSourceTree.repoRoot,
        "desktop/src/main/kotlin/com/venera/desktop/explore/DesktopExploreScreen.kt",
    )

    private fun codeOf(f: File): List<String> {
        assertTrue("读不到 ${f.name}，判据失效（不是通过）", f.isFile)
        // `codeLinesOf` 自己去注释行（屏的 KDoc 里本来就该点名被禁写法 `GalleryPorts.of(`，
        // 含进判据就是把说明当成违例），这里不再叠一层。
        return DesktopSourceTree.codeLinesOf(f)
    }

    @Test
    fun `路由那一档存在`() {
        assertTrue(
            "DesktopRoute 里没有 Settings 那一档 —— §143 的路由被撤了",
            codeOf(routeFile).any { it.contains("data object Settings : DesktopRoute") },
        )
    }

    @Test
    fun `壳里那一支真渲那颗屏`() {
        val shell = DesktopSourceTree.desktopLargeScreenLines()
        assertTrue(
            "壳里没有 `DesktopRoute.Settings ->` 那一支 —— 门没开在 when 上",
            shell.any { it.contains("DesktopRoute.Settings ->") },
        )
        assertTrue(
            "那一支没渲 DesktopSettingsHub —— 屏写好了却没人 call 它（§118 那一族的形状）",
            shell.any { it.contains("DesktopSettingsHub(") },
        )
        // 装配点那两枚件递到壳（没有形参就渲不出那八枚能改的档）
        assertTrue(
            "壳没有 galleryPrefs 那枚形参 —— 八枚写口进不了屏",
            shell.any { it.contains("galleryPrefs:") },
        )
        assertTrue(
            "壳没有 galleryGuard 那枚形参 —— 分级遮罩档的读流与写口进不了屏",
            shell.any { it.contains("galleryGuard:") },
        )
    }

    @Test
    fun `屏里逐枚点到七枚 setter 与 spec 同源`() {
        val screen = codeOf(screenFile)
        // editable 的 setter 名住在 spec 账上；屏内逐枚出现才算线接上。
        // 列数档已由网格密度档覆盖，设置行撤除，不再是这里的写口。
        val expected = listOf(
            "setGalleryPreviewQuality",
            "setGalleryBlockAi",
            "setGalleryAiBadge",
            "setGalleryAutoPlaySec",
            "setGalleryPreload",
            "setGalleryAnimated",
            "setGalleryBackdrop",
            // 分级遮罩档那颗守卫写口（不在 spec.editable 里，与源管理屏共享同一道）
            "setNsfwMaskMode",
        )
        val missing = expected.filter { sym -> screen.none { it.contains("$sym(") } }
        assertTrue("设置屏缺这几枚写口的调用：$missing —— 屏摆出来了而按下去改不动", missing.isEmpty())
        // 对照组：读侧同样逐枚 collect（写了 setter 而不读那枚流，选中态永远是默认档那一枚亮点）
        val readers = listOf(
            "galleryPreviewQuality",
            "galleryBlockAi",
            "galleryAiBadge",
            "galleryAutoPlaySec",
            "galleryPreload",
            "galleryAnimated",
            "galleryBackdrop",
            "nsfwMaskMode",
        )
        val missingReads = readers.filter { sym -> screen.none { it.contains("$sym.collectAsState()") } }
        assertTrue("设置屏缺这几枚读流的 collect：$missingReads —— 写了而看不见自己写的值", missingReads.isEmpty())
    }

    @Test
    fun `探索屏顶栏那颗入口摆出了那两个字`() {
        val explore = codeOf(exploreFile)
        // 主人「去掉更多按钮」后探索屏不再摆溢出菜单，设置那扇门改由
        // 侧栏底部那格「设置」承载（`VeneraSideBar` 的 `onSettingsClick`）。所以这里改为：
        // ① 探索屏仍必传 `onOpenSettings`（装配口径不塌）；② 侧栏真有那格设置。
        assertTrue(
            "那颗形参不再是必填、无默认值（默认 {} 就是假开关，119 批那一族）",
            explore.any { it.contains("onOpenSettings: () -> Unit,") },
        )
        val sidebar = DesktopSourceTree.codeText(
            DesktopSourceTree.androidSource("components/VeneraSideBar.kt"),
        )
        assertTrue(
            "侧栏没有「设置」那格（探索屏菜单删了之后，这里就是唯一入口）",
            sidebar.contains("设置"),
        )
    }

    @Test
    fun `屏不许去取静态口`() {
        // DesktopUiIsolationTest ② 扫的是整棵 gallery/ui 那颗目录里 `GalleryPorts.of(`；
        // 这一条把同一禁令点名到这颗新屏自己的文件（新文件漏扫那一族的老账，§130 记过一次）。
        val offenders = codeOf(screenFile).filter { it.contains("GalleryPorts.of(") }
        // 用 assertTrue(isEmpty) 而不是 assertFalse(isNotEmpty)：assertFalse 在条件为真时才失败，
        // 传 offenders.isEmpty() 会把"干净"读成"违例"（本喵这一批第一次就把它写反，靠红抓到）。
        assertTrue("设置屏里出现了 GalleryPorts.of(...)：$offenders", offenders.isEmpty())
    }
}
