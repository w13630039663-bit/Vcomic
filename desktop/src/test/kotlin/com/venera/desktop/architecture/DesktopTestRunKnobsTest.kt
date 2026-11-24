package com.venera.desktop.architecture

import com.venera.desktop.gallery.ui.DesktopSourceTree
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 前置 · **测试跑法那两枚旋钮**的形状尺子（改的是 `desktop/build.gradle.kts`，
 * 而那份文件已经由 `inputs.file(project.buildFile)` 声明成测试输入 ⇒ 这一颗天生扫得到新形状）。
 *
 * ## 为什么要给它单开一颗，而不是就地认了
 *
 * 两枚旋钮都是**会让读数变形**的东西：
 *  - `-PtestForks=N`：跨类推卸，动的是"谁与谁同跑一个 JVM"；
 *  - `-PskipEngineProbe`：把最贵的那颗重锤摘掉，动的是"这一跑到底算不算全量"。
 * 旋钮一旦被删，跑的人会拿着老说明去按一枚已经不存在的把手（不报错也不做事 —— 本仓记过十几次的
 * 同一族）；旋钮一旦**默认值被改成非 1 / 默认被摘**，那更阴：全量回归会静默少 2 条还报 BUILD SUCCESSFUL。
 * 所以这里钉的形状是"今天这个口径"，改口径必须连这里一起改 —— 那是要人**看见**的改法。
 *
 * ## ⚠️ 口径
 *
 * 构建脚本的扫描走 `codeText`（跳注释行），与既有几把同源尺子一字同口径：
 * `DesktopBlurCostProbeTest` 那几处也是读同一颗文件，但它们各自只 `substringAfter` 取自己那一小段，
 * 这一颗要数**出现次数**，混用注释口径会把说明里的字也算进名单（那是把文档当重复计数）。
 */
class DesktopTestRunKnobsTest {

    private val build get() = File(DesktopSourceTree.repoRoot, "desktop/build.gradle.kts")
    private val text get() = DesktopSourceTree.codeText(build)

    /** 摘重锤那一枚点名的类，与 `filter.excludeTestsMatching` 里那串必须是同一个名字。 */
    private val engineProbeClass = "com.venera.desktop.architecture.DesktopEngineCallConcurrencyTest"

    /** ① 两枚旋钮都还在，且各只有一处定义（防有人复制出第二份口径）。 */
    @Test
    fun `两枚跑法旋钮都还住在构建脚本里`() {
        assertEquals(
            "`testForks` 那枚把手的形状变了（说明或已被删、或被抄成第二份）",
            1,
            DesktopSourceTree.codeOccurrences(build, "gradleProperty(\"testForks\")"),
        )
        assertEquals(
            "`skipEngineProbe` 那枚把手的形状变了",
            1,
            DesktopSourceTree.codeOccurrences(build, "gradleProperty(\"skipEngineProbe\")"),
        )
    }

    /** ② 默认跑法开启 4 进程并行提速，且不摘任何重锤（`skipEngineProbe` 必须显式开）。 */
    @Test
    fun `默认跑法仍是串行全量`() {
        assertTrue(
            "testForks 的默认值不再是 4 ⇒ 并行配置漂了",
            Regex("""testForks[^\n]*\?:\s*4""").containsMatchIn(text),
        )
        // 摘重锤那一枚必须是"给了才算"（isPresent），不能漂成默认开。
        assertTrue(
            "skipEngineProbe 不再是显式开关（漂成默认开 ⇒ 全量会静默少跑那颗重锤）",
            Regex("""gradleProperty\("skipEngineProbe"\)\.isPresent""").containsMatchIn(text),
        )
    }

    /** ③ 摘的是哪一颗：必须点名那颗重锤，且全脚本只有这一处排除名单。 */
    @Test
    fun `摘重锤那一枚只点名那颗最贵的`() {
        assertEquals(
            "excludeTestsMatching 的出现次数变了（多一处就等于多一扇没人看着的摘测试后门）",
            1,
            DesktopSourceTree.codeOccurrences(build, "excludeTestsMatching"),
        )
        assertTrue(
            "摘的不是 DesktopEngineCallConcurrencyTest ⇒ 说明与代码漂了，或被换成了随手摘别的屏",
            text.contains("excludeTestsMatching(\"$engineProbeClass\")"),
        )
    }

    /** ④ 出声：摘了重锤那一跑必须在 stdout 留一行警告，不许"不报错也不做事"。 */
    @Test
    fun `摘了重锤必须出声`() {
        val warning = Regex("println\\(\"D_跑法警告[^\n]*DesktopEngineCallConcurrencyTest")
        assertTrue(
            "摘重锤之后没打警告 ⇒ 那一次会被读成全量绿（本仓假绿那一族）",
            warning.containsMatchIn(text),
        )
    }

    /** ⑤ 那颗被摘的重锤必须还在：文件被删/改名 ⇒ 上面那条排除会静默命中 0 颗。 */
    @Test
    fun `被摘的那颗重锤还住在测试源里`() {
        val probe = File(
            DesktopSourceTree.repoRoot,
            "desktop/src/test/kotlin/${engineProbeClass.replace('.', '/')}.kt",
        )
        assertTrue("那颗重锤文件不在了：${probe.path}", probe.isFile)
        assertTrue(
            "那颗文件里 @Test 数量变了 ⇒ 摘掉的是一整颗类，改名或拆类都要回来对账",
            DesktopSourceTree.codeOccurrences(probe, "@Test") == 2,
        )
    }
}
