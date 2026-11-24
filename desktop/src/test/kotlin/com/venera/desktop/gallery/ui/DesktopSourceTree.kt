package com.venera.desktop.gallery.ui

import com.venera.desktop.app.DesktopComicAndFilesEffects
import com.venera.desktop.app.DesktopComicAndSourceHost
import com.venera.desktop.app.DesktopGalleryAndAccountHost
import com.venera.desktop.app.DesktopGalleryWallEffects
import com.venera.desktop.app.DesktopLargeScreenMiscEffects
import com.venera.desktop.app.DesktopLargeScreenRouter
import com.venera.desktop.app.DesktopLargeScreenState
import com.venera.desktop.app.DesktopPixivRoutesHost
import com.venera.desktop.app.DesktopSearchAndExploreEffects
import com.venera.desktop.app.DesktopTabHost
import com.venera.desktop.app.ForensicFlags
import com.venera.desktop.app.VeneraDesktopLargeScreen
import com.venera.desktop.ui.DesktopWallScaffold
import java.io.File

/**
 * 只读源码树的位置 —— 两条"文本核对"的用例都靠它，所以它本身必须与工作目录无关。
 *
 * Gradle 跑测试时的工作目录是**模块目录**（`desktop/`），不是仓库根；这里不假设那一点，
 * 而是从当前目录向上找到 `settings.gradle.kts` 所在的那一层。这样 `:desktop:test` 无论
 * 从 worktree 还是从主检出跑、无论工作目录是模块根还是仓库根，读到的都是同一棵树。
 */
internal object DesktopSourceTree {

    val repoRoot: File = run {
        var cursor: File? = File("").absoluteFile
        while (cursor != null) {
            if (File(cursor, "settings.gradle.kts").isFile) return@run cursor
            cursor = cursor.parentFile
        }
        error("没找到仓库根（向上都没有 settings.gradle.kts）：起点 ${File("").absolutePath}")
    }

    /** `desktop/src/main` 及拆分出的 `:core-data`、`:gallery-core` 下的全部 .kt（隔离与口径漂移两条尺子都扫这一片）。 */
    fun desktopMainSources(): List<File> = listOf("desktop/src/main", "core-data/src/main", "gallery-core/src/main")
        .flatMap { rel -> walkFiles(File(repoRoot, rel)) }

    /**
     * 桌面**自有**片（`com.venera.desktop` 包）—— 壳与自制屏那一族判据的对象。
     * 共享面（`com.venera.compose` 包）自持进树后也在 desktop/main 里，但它们的账
     * 不归"桌面壳不许 X"这一族管（那正是从对岸原样搬来的形态），扫描类判据要用这片。
     */
    fun desktopOwnSources(): List<File> =
        walkFiles(File(repoRoot, "desktop/src/main/kotlin/com/venera/desktop"))

    /**
     * 回核目标：桌面自持树优先（共享面已整批搬进 `desktop/src/main/kotlin/com/venera/compose`）。
     * 只在 app 树里存在的对岸文件随 `:app` 模块退役 —— 引用它们的回核判据一并退役，
     * 不在这里留兜底去掩盖"判据对象已不存在"这件事。
     */
    fun androidSource(relative: String): File =
        listOf("desktop/src/main", "core-data/src/main", "gallery-core/src/main")
            .map { File(repoRoot, "$it/kotlin/com/venera/compose/$relative") }
            .firstOrNull { it.isFile }
            ?: error("回核目标已不在桌面自持树（对岸退役后判据应一并退役）：$relative")

    /** `app/src/main` 下的全部 .kt；`:app` 退役后目录不存在 ⇒ 返回空，反向隔离断言自然失效。 */
    fun androidMainSources(): List<File> =
        File(repoRoot, "app/src/main").takeIf { it.isDirectory }?.let { walkFiles(it) } ?: emptyList()

    /** 桌面那颗同名文件的绝对路径，用于负向断言（"这颗文件里不许出现某个 token"）。 */
    fun desktopUiSource(name: String): File =
        File(repoRoot, "desktop/src/main/kotlin/com/venera/desktop/gallery/ui/$name").also {
            check(it.isFile) { "读不到桌面 UI 文件：${it.path}" }
        }

    /**
     * `desktop/src/main` 下按**包内相对路径**点名一颗文件。
     *
     * 为什么不只留 [desktopUiSource]：接线类的判据要核的是数据层与集成方那几颗
     * （`gallery/data` 下那几颗与 `VeneraDesktop.kt`），把它们塞进 `gallery/ui/` 那颗的名字里只会读不到。
     */
    fun desktopSourceOf(relative: String): File =
        File(repoRoot, "desktop/src/main/kotlin/com/venera/desktop/$relative").also {
            check(it.isFile) { "读不到桌面文件：${it.path}" }
        }

    /**
     * `src/probe/kotlin` 下点名一颗探针文件。
     *
     * 探针已从生产源集移出：它们不进安装包，但仍与 main 关联编译，读 internal 符号。
     */
    fun desktopProbeSource(name: String): File =
        File(repoRoot, "desktop/src/probe/kotlin/com/venera/desktop/$name").also {
            check(it.isFile) { "读不到探针文件：${it.path}" }
        }

    /**
     * `:engine-probe` 里点名的那颗宿主文件。
     *
     * 为什么单开一颗而不并进 [desktopMainSources]：登录那一族的凭据落点实际住在 `:engine-probe`
     * （桥里那三档 cookie 处理与那颗 in-memory 罐），而"扫 `desktop/src/main`"那批既有尺子的
     * 对象是桌面自己那一片；把那颗文件塞进它们的名单，等于让一堆不相干的尺子突然多扫一个模块。
     */
    fun engineProbeSource(name: String): File =
        File(repoRoot, "engine-probe/src/main/kotlin/com/venera/engineprobe/$name").also {
            check(it.isFile) { "读不到 :engine-probe 的文件：${it.path}" }
        }

    /** `:engine-probe` 那一整片（"树上不许有第二颗罐"这类两轨判据要扫全片而不是一颗文件）。 */
    fun engineProbeSourceDir(): List<File> =
        walkFiles(File(repoRoot, "engine-probe/src/main"))

    /**
     * `gallery/ui/` 下的**全部**桌面界面件。
     *
     * 文案类的尺子用这一颗而不是手写名单：名单会把"屏上有没有这东西"读成"我记不记得把它列进来"，
     * 而列错一个文件名（它 `check(isFile)` 直接抛）会让整条用例红在一次拼写上，红得没有信息量。
     */
    fun desktopUiSources(): List<File> =
        walkFiles(File(repoRoot, "desktop/src/main/kotlin/com/venera/desktop/gallery/ui"))

    /**
     * **代码行**里某个字面量的出现次数：跳过注释行。
     *
     * 为什么要跳注释：口径账的注释里**必须**能点名对岸那个数（`ExpandedSideBarWidth = 224.dp`），
     * 含进注释就会把"唯一出处"这条尺子读成 2 处，逼着后人把说明删掉 —— 那是把文档当重复计数。
     */
    fun codeOccurrences(file: File, literal: String): Int =
        codeLines(file).sumOf { line -> line.split(literal).size - 1 }

    /**
     * 去掉注释行之后的文件正文。
     *
     * 「这颗件里不许出现某个手势修饰」这类负向断言必须只看代码：口径注释里**本来就该**能点名
     * 那个被禁的 token（比如"连 `clickable` 都没 import"），含进注释就把说明当成了违例。
     */
    fun codeText(file: File): String = codeLines(file).joinToString("\n")

    /**
     * 去注释后的代码行 —— 对外可见（色值扫描器 [DesktopSurfaceLiterals] 复用同一口径，
     * 别处再写一份"怎么算代码行"就会与这里漂）。
     */
    fun codeLinesOf(file: File): List<String> = codeLines(file)

    /**
     * 详情屏的成员表（批 3 拆分：主屏 + 数据模型 + 骨架 + 信息头 + 正文分区 + 收藏弹窗）。
     *
     * 显式列举而不是扫目录：成员表本身就是拆分契约，新增或再拆一颗要同步改这里 ——
     * 否则"随便哪颗文件里有这行字"会把判据悄悄放宽（`docs/02-architecture/oversized-file-split-plan.md` 4.3）。
     */
    fun desktopComicDetailSources(): List<File> = listOf(
        "detail/DesktopComicDetailScreen.kt",
        "detail/DesktopComicDetailModels.kt",
        "detail/DesktopComicDetailSkeleton.kt",
        "detail/DesktopComicDetailHero.kt",
        "detail/DesktopComicDetailSections.kt",
        "detail/DesktopComicDetailFavoriteDialog.kt",
    ).map { desktopSourceOf(it) }

    fun engineProbeSessionSources(): List<File> = listOf(
        "EngineSession.kt",
        "EngineSessionExploreAndSearch.kt",
        "EngineSessionNetworkFavorites.kt",
        "EngineSessionComicAndComments.kt",
        "EngineSessionAuthAndSettings.kt",
    ).map { engineProbeSource(it) }

    /** 成员文件的正文合并；跨文件的接线断言用这颗，单点形状仍指到具体文件。 */
    fun desktopComicDetailCode(): String = desktopComicDetailSources().joinToString("\n") { codeText(it) }

    /** 成员文件的行合并；按邻域断言的尺子用这颗。 */
    fun desktopComicDetailLines(): List<String> = desktopComicDetailSources().flatMap { codeLinesOf(it) }

    /** 成员集上的逐字出现次数（一处缺口要在哪颗文件里补都算一处）。 */
    fun desktopComicDetailOccurrences(literal: String): Int =
        desktopComicDetailSources().sumOf { codeOccurrences(it, literal) }

    /**
     * 大屏的成员表（批 4 拆分：主函数 + 壳展示件 + 墙脚手架 + 壳侧状态）。
     *
     * 显式列举而不是扫目录，理由同 [desktopComicDetailSources]；未拆前这里只有一颗主文件。
     */
    fun desktopLargeScreenSources(): List<File> = listOf(
        "app/VeneraDesktopLargeScreen.kt",
        "app/DesktopLargeScreenState.kt",
        "app/DesktopLargeScreenStateInit.kt",
        "app/DesktopLargeScreenSearchAndSourceState.kt",
        "app/DesktopLargeScreenWallAndHomeState.kt",
        "app/DesktopLargeScreenWiring.kt",
        "app/DesktopLargeScreenChrome.kt",
        "app/DesktopLargeScreenRouter.kt",
        "app/DesktopLargeScreenMiscEffects.kt",
        "app/DesktopLargeScreenHelpers.kt",
        "app/DesktopComicAndSourceHost.kt",
        "app/DesktopSourceManageRoutesHost.kt",
        "app/DesktopFilesAndLocalReaderRoutesHost.kt",
        "app/DesktopPixivRoutesHost.kt",
        "app/DesktopGalleryAndAccountHost.kt",
        "app/DesktopAccountAndBackupRoutesHost.kt",
        "app/DesktopGallerySearchAndDetailRoutesHost.kt",
        "app/DesktopSearchAndExploreEffects.kt",
        "app/DesktopGalleryWallEffects.kt",
        "app/DesktopComicAndFilesEffects.kt",
        "app/DesktopTabHost.kt",
        "app/DesktopTabGalleryAndHomePanes.kt",
        "app/DesktopTabFavoritesAndExplorePanes.kt",
        "app/DesktopSearchTabHost.kt",
        "ui/DesktopShellPanes.kt",
        "ui/DesktopWallScaffold.kt",
        "ui/DesktopWallUiStates.kt",
    ).map { desktopSourceOf(it) }

    /** 大屏成员文件的正文合并；跨文件的接线断言用这颗，单点形状仍指到具体文件。 */
    fun desktopLargeScreenCode(): String = desktopLargeScreenSources().joinToString("\n") { codeText(it) }

    /** 大屏成员文件的行合并；按邻域断言的尺子用这颗。 */
    fun desktopLargeScreenLines(): List<String> = desktopLargeScreenSources().flatMap { codeLinesOf(it) }

    /** 大屏成员集上的逐字出现次数。 */
    fun desktopLargeScreenOccurrences(literal: String): Int =
        desktopLargeScreenSources().sumOf { codeOccurrences(it, literal) }

    /** 桌面主入口与装配根成员表（启动偏好/取证引导、窗口钩子、引擎宿主回调）。 */
    fun desktopEntrySources(): List<File> = listOf(
        "app/VeneraDesktop.kt",
        "app/DesktopStartupBootstrap.kt",
        "app/DesktopWindowHooks.kt",
        "data/DesktopEngineHostActions.kt",
    ).map { desktopSourceOf(it) }

    fun desktopEntryCode(): String = desktopEntrySources().joinToString("\n") { codeText(it) }

    fun desktopEntryLines(): List<String> = desktopEntrySources().flatMap { codeLinesOf(it) }

    /** 取证启动标志及其子种子定义成员表。 */
    fun forensicFlagsSources(): List<File> = listOf(
        "app/ForensicFlags.kt",
        "app/ForensicFlagsScreenSeeds.kt",
        "app/ForensicFlagsComicSeeds.kt",
    ).map { desktopSourceOf(it) }

    fun forensicFlagsCode(): String = forensicFlagsSources().joinToString("\n") { codeText(it) }

    /** 按主源码统计出现次数，并将大屏与入口拆分成员分别合并归入各自根文件。 */
    fun desktopMainGroupedOccurrences(literal: String): List<Pair<String, Int>> {
        val largeNames = desktopLargeScreenSources().map { it.name }.toSet()
        val entryNames = desktopEntrySources().map { it.name }.toSet()
        return desktopMainSources()
            .groupBy {
                when (it.name) {
                    in largeNames -> "VeneraDesktopLargeScreen.kt"
                    in entryNames -> "VeneraDesktop.kt"
                    else -> it.name
                }
            }
            .mapValues { (_, files) -> files.sumOf { codeOccurrences(it, literal) } }
            .filterValues { it > 0 }
            .entries
            .map { it.key to it.value }
            .sortedBy { it.first }
    }

    private fun codeLines(file: File): List<String> =
        file.readText().lines()
            .map { it.trim() }
            .filterNot { it.startsWith("//") || it.startsWith("*") || it.startsWith("/*") }

    private fun walkFiles(root: File): List<File> {
        check(root.isDirectory) { "源码目录不存在：${root.path}" }
        return root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }
}
