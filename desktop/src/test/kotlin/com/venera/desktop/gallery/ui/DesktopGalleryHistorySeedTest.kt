package com.venera.desktop.gallery.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批 86 · 图库浏览历史那条链的接线判据。
 *
 * 这一颗不管业务口径（那颗在 `DesktopGalleryBrowseHistoryTest`），只管**屏上每句断言背后那一层真在**：
 * 注册表那一行转正、那一份档的落点与那颗存储件各只有一处、时刻格式两屏同串、README 那两句与代码同步。
 *
 * ## ⚠️ 改口径（从前那几条"前提"是假的）
 *
 * 批 86 写下这把尺子时，它假设的活渲染路是「详情浮层里记一行 + `DesktopGalleryBrowseHistoryPane` 那一屏
 * + `VeneraDesktopApp`（今 `DesktopDomainRows.kt`）那一排首页行派发」。到现量：**那三处全是已拆 Fluent 平行线的遗骸**
 * —— `DesktopGalleryBrowseHistoryPane.kt` 的 `@Composable` 是 0，`DesktopDomainRows.kt` 不在唯一那条渲染路上，
 * 而 `recordView` 在 `desktop/src/main` 里**零调用点**。也就是说：那些前提描述的是「一扇从没开过的门」，
 * 而一把钉前提的尺子可以长绿一百多批（本仓那把惰尺子 `DesktopUnusedInternalTest` ①③ 是同一课）。
 * 所以这一批把**假前提从这把尺子里摘掉**（接线判据搬到新那颗 `DesktopGalleryBrowseHistoryEntryTest`，
 * 它钉的是今天真在的活壳），这里只留「形状与枚数」那几格——它们与渲染路无关，仍然有效。
 *
 * ## 为什么每颗尺子都数"出现次数"而不是问"存不存在"
 *
 * 接线类判据红过的那一族是：假动作存在（排完立刻 dispose、参数递了但没人读），存在性判式全绿。
 * 数次数至少管住两件事 —— 该在的一处不少，不该多的地方不多（同一句话写第二份就会漂）。
 */
class DesktopGalleryHistorySeedTest {

    /** 起**活的那一屏**（旧那三处遗骸不再作为判据对象，见上面那段改口径）。 */
    private val screen = DesktopSourceTree.desktopSourceOf("gallery/ui/DesktopGallerySavedScreen.kt")
    private val saver = DesktopSourceTree.desktopSourceOf("gallery/data/DesktopGalleryBrowseHistory.kt")

    private fun text(file: File) = DesktopSourceTree.codeText(file)

    // ① 首页那一行**转正**：带上了内容件，reason 撤下来（那一行不再是"未实现"形状）。
    @Test
    fun 历史那一行已经点亮() {
        val row = desktopGalleryHomeRows().first { it.key == DesktopGalleryHomeSectionKey.HISTORY }
        assertEquals("那一行要拿到内容件", DesktopGallerySectionContent.BrowseHistory, row.content)
        assertNull("点亮了就不许再带缺席理由", row.reason)
        assertTrue("标题还是那两个字", row.title == "历史")
    }

    // ② 六行今天**全都有**内容件。谁新加一行不带内容件，这条会红 —— 那是本仓唯一的"未实现"豁免位，
    //    加一行要先想清楚（手册 §0 记着这条定点豁免的由来）。
    @Test
    fun 首页那六行今天全有内容件() {
        val absent = desktopGalleryHomeRows().filter { it.content == null }
        assertEquals(
            "首页又长出未实现的行了：${absent.map { it.title }} —— 要么把那一屏补上，要么当场写明为什么留空",
            0,
            absent.size,
        )
    }

    // 扫描整个 gallery/ui/ 目录禁止出现过期空态文案，并校验历史屏不持有建目录能力。
    @Test
    fun 那一屏不建目录() {
        assertEquals("那一屏里出现了 mkdirs：看一眼不许在盘上多出目录", 0, DesktopSourceTree.codeOccurrences(screen, "mkdirs"))
        assertEquals(
            "那一屏拿到了 PathProvider（那等于递进来一条能建目录的手）",
            0,
            DesktopSourceTree.codeOccurrences(screen, "PathProvider"),
        )
        // 读盘、写侧、回原卡那三跳全住在壳：屏自己不许有协程与网络口。
        assertEquals("那一屏自己起了协程：", 0, DesktopSourceTree.codeOccurrences(screen, "withContext"))
        assertEquals("那一屏自己取网络：", 0, DesktopSourceTree.codeOccurrences(screen, "fetchById"))
    }

    // 时刻格式串 yyyy-MM-dd HH:mm 在桌面主源里全部同串：浏览历史、下载列表、本地图片库、备份预览四处
    // （备份那一处是 4d6cabe1 三屏重做时加的）。枚数只能守住「这四处没被改口、没被删」；
    // 谁新加一处别种写法的日期扫不进来 —— 这一条要靠人看，枚数不替它兜。
    @Test
    fun 各屏的时刻格式同串() {
        // 精确到字面量的闭合引号：带秒的日志戳（yyyy-MM-dd HH:mm:ss）不是这一族口径，不该被子串算进来。
        val hits = DesktopSourceTree.desktopMainSources().flatMap { file ->
            DesktopSourceTree.codeLinesOf(file).filter { it.contains("\"yyyy-MM-dd HH:mm\"") }
        }
        assertEquals("时刻格式串的枚数变了：${hits.map { it.take(40) }}", 4, hits.size)
    }

    // ⑫ 归档那一格**不买库**：桌面构建脚本与版本目录里不许出现 zip4j / commons-compress。
    //     现量与理由在 rounds 文档 §1.1（对岸两处已经在用 `java.util.zip`，交付包镜像里 43 条 zip 类）。
    @Test
    fun 没为归档引第三方库() {
        val build = File(DesktopSourceTree.repoRoot, "desktop/build.gradle.kts").readText()
        val catalog = File(DesktopSourceTree.repoRoot, "gradle/libs.versions.toml").readText()
        listOf("zip4j", "commons-compress").forEach { needle ->
            assertEquals("构建脚本里出现了 $needle", 0, build.lines().count { !it.trim().startsWith("//") && it.contains(needle) })
            assertEquals("版本目录里出现了 $needle", 0, catalog.lines().count { !it.trim().startsWith("#") && it.contains(needle) })
        }
        // 对照组：这两份文件真的被读到了（否则上面那些禁串是恒真的空判式）。
        assertTrue("对照组失效：构建脚本没读到", build.contains("providers.gradleProperty(\"save\")"))
        assertTrue("对照组失效：版本目录没读到", catalog.contains("coil3"))
    }

    // ⑬ 落点那一颗只许一处算它：声明 + 那颗件自己用它建实例，两处之外再算一遍就是两份账。
    @Test
    fun 落点那颗只有一处算它() {
        val total = DesktopSourceTree.desktopMainSources()
            .sumOf { DesktopSourceTree.codeOccurrences(it, "desktopBrowseHistoryFile(") }
        assertEquals(
            "落点的出处变了（声明一处 + getInstance 一处 = 2）：实际 $total 处",
            2,
            total,
        )
    }

    // ⑭ 两份 README 那一格同轨：旧那句不许留，新那句两边都得有（改一处必须同步另一处，本仓口径）。
    @Test
    fun 两份README的那一条同轨() {
        val zh = File(DesktopSourceTree.repoRoot, "README.md").readText()
        val en = File(DesktopSourceTree.repoRoot, "README.en.md").readText()
        assertFalse("中文那份还写着旧那句", zh.contains("那一行仍未点亮"))
        assertFalse("英文那份还写着旧那句", en.contains("row is still not lit up"))
        // ⚠️ 从前那两句钉的是「在**详情浮层**里记一行」，而那是已拆 Fluent 那条线
        // 的形状 —— 接的是活壳里点卡那一刻（`LaunchedEffect(route)`），桌面今天**没有**
        //    详情浮层这一层。旧那句现在反过来是**假话**，所以禁串跟着翻正（留着它就是拿假口径逼人改代码）。
        assertFalse("中文那份还写着「详情浮层里记一行」（那条线已拆）", zh.contains("详情浮层里记一行"))
        assertFalse("英文那份还写着 detail overlay 那一形", en.contains("records one row from the detail overlay"))
        assertTrue("中文那份没写点开就记一行", zh.contains("记入浏览历史"))
        assertTrue("英文那份没写点开就记一行", en.contains("recorded in browsing history"))
        assertTrue("中文那份没写回原卡", zh.contains("回原卡"))
        assertTrue("英文那份没写回原卡", en.contains("jump back to its card"))
        // 挡住那一档不记这件事两边都得念到 —— 它是这一格唯一的行为边界。
        assertTrue("中文那份没念遮挡的那一档不记", zh.contains("被屏蔽的帖子不记"))
        assertTrue("英文那份没念遮挡的那一档不记", en.contains("masked posts are not recorded"))
        // 新落的那一格：两份都得点名「回原卡」是**按下现取**而不是把地址存进档。
        assertTrue("中文那份没念按下现取", zh.contains("按下现取"))
        assertTrue("英文那份没念按下现取", en.contains("fetches on demand"))
    }

    // ⑮ 那颗存储件只许有一颗：全桌面主源里一行类声明。
    //     ⚠️ 它是 `internal` 的 —— 那颗件交出的是 internal 的那一行类型，公开出来就红
    //     （public 暴露 internal 返回类型是编译错误，本仓记过那一课）。
    @Test
    fun 那颗存储件只有一颗() {
        val declared = DesktopSourceTree.desktopMainSources().flatMap { file ->
            DesktopSourceTree.codeLinesOf(file).filter { it.startsWith("internal class DesktopGalleryBrowseHistory") }
        }
        assertEquals("声明处不止一颗，或那颗改成了公开：$declared", 1, declared.size)
    }

    // 历史屏仅接收 rows 列表，不直接持有存储实例或 PathProvider，避免组合期同步读盘。
}
