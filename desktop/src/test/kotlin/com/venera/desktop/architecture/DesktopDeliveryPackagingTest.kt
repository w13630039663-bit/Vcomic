package com.venera.desktop.architecture

import com.venera.desktop.gallery.ui.DesktopSourceTree
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 交付形态那一格的**接线尺**（B 档第一颗）。
 *
 * ## 这一族为什么必须有尺
 *
 * 打包配置是判据面最容易长绿的角落：开发跑用全 JDK、`:desktop:test` 不碰 exe，所以
 * 「包里少一枚 jlink 模块」这种病要等交付物装到别人机器上才露（批 82.9 的 `java.sql`、
 * S0-9 的 `jdk.unsupported`、包里取不到 sqlite 原生库，三课全在这一族）。
 * 而反过来也成立：**改错一处打包配置，今天一条判据都不会红**。所以这一把尺钉的正是
 * 那些"只有一份出处、掉了就静默分叉"的旋钮，而不是某个测试跑法。
 *
 * ## 三条反向禁的口径（本仓反复记账那一族）
 *
 * 探针期那几个字样（`venera-desktop-probe`、`console = true`）留在树上就会被下一批当成
 * "照旧写"的模板；`D_日志` 那句文案若允许 main 自己写一份，件里那句与屏上那句就各改各的。
 */
class DesktopDeliveryPackagingTest {

    private val gradle: File = File(DesktopSourceTree.repoRoot, "desktop/build.gradle.kts")

    /** 那一行 `packageName`；跳过注释行，免得口径注释自己把判式喂饱（与 [DesktopRuntimeModulesTest] 同一手法）。 */
    private fun gradleLine(needle: String): String {
        val line = gradle.readText().lines()
            .map { it.trim() }
            .filterNot { it.startsWith("//") }
            .firstOrNull { it.contains(needle) }
        return requireNotNull(line) { "构建脚本里找不到那一行 $needle —— 打包配置搬家了，这把尺子要跟着改判" }
    }

    /** ① 包名脱离探针期那一套，而旧名一颗不剩（留着就是给下一批当模板）。 */
    @Test fun 包名是正式那一串而探针名一颗不剩() {
        assertTrue(
            "packageName 必须是不带 -probe 的那一串：${gradleLine("packageName =")}",
            gradleLine("packageName =").contains("\"venera-desktop\""),
        )
        assertEquals(
            "探针期那个包名不许还留在构建脚本里：",
            0,
            DesktopSourceTree.codeOccurrences(gradle, "venera-desktop-probe"),
        )
    }

    /** ② 版本号读得到（升级发布必须递增它，读不到 = 装的是哪一版没有答案）。 */
    @Test fun 桌面版本号可读() {
        val desktopVersion = Regex("desktopPackageVersion\\s*=\\s*\"([^\"]+)\"")
            .find(gradle.readText())?.groupValues?.get(1)
        requireNotNull(desktopVersion) { "桌面侧读不到 packageVersion" }
    }

    /** ③ 控制台关掉，而 `true` 一颗不剩（那一枚布尔与"读数落盘"是同一件事的两半）。 */
    @Test fun 控制台那一枚已经扳下来() {
        assertTrue("windows { console = false } 必须是 false：${gradleLine("console =")}",
            gradleLine("console =").contains("console = false"))
        assertEquals("构建脚本里不许还留着 console = true：",
            0, DesktopSourceTree.codeOccurrences(gradle, "console = true"))
    }

    /** ④ 装 tee 排在**任何一句读数之前**，而那句读数只从件里搬。 */
    @Test fun 读数落盘排在读数之前只接一处() {
        val main = DesktopSourceTree.desktopSourceOf("app/VeneraDesktop.kt")
        val mainCode = DesktopSourceTree.codeText(main)
        assertEquals("装配那一步必须恰一处：",
            1, DesktopSourceTree.codeOccurrences(main, "installDesktopRunLog("))
        assertEquals("落点计算必须恰一处：",
            1, DesktopSourceTree.codeOccurrences(main, "desktopRunLogFile("))
        assertEquals("那句读数只能由件里那颗算，main 不许自己写文案：",
            1, DesktopSourceTree.codeOccurrences(main, "desktopRunLogReading("))
        // 反向：main 里若出现自写的 `D_日志 `，就是第二份口径（件里那句与屏上那句会各改各的）。
        assertEquals("main 里不许自己写 `D_日志 ` 那串文案：",
            0, DesktopSourceTree.codeOccurrences(main, "\"D_日志"))
        val installAt = mainCode.lines().indexOfFirst { it.contains("installDesktopRunLog(") }
        val startupAt = mainCode.lines().indexOfFirst { it.contains("D_启动") }
        assertTrue("找不到那句启动读数（量具失效）：main=$installAt startup=$startupAt", startupAt >= 0)
        assertTrue(
            "装 tee 必须早于 D_启动（否则盘上那一份天生缺头几行 = 最难回头查的假证据）：$installAt vs $startupAt",
            installAt in 0 until startupAt,
        )
    }

    /** ⑤ 落点只认数据根：不引第二种口径，也不许退回临时目录。 */
    @Test fun 那一颗件不另起落点口径() {
        val piece = DesktopSourceTree.desktopSourceOf("platform/DesktopRunLog.kt")
        val code = DesktopSourceTree.codeText(piece)
        // 反向禁两条：`java.io.tmpdir` 那一族是把「取不到就抛、绝不退回 tmp」从另一扇门绕回去；
        // `getAppStorageDir` 则是第二家数据根口径（同一台机器长出两棵树）。
        assertEquals("件里不许出现退回临时目录那一族：",
            0, DesktopSourceTree.codeOccurrences(piece, "java.io.tmpdir"))
        assertEquals("件里不许引第二家数据根口径：",
            0, DesktopSourceTree.codeOccurrences(piece, "getAppStorageDir"))
        assertTrue("落点必须由调用点递进来的 dataRoot 算：",
            code.contains("dataRoot: File"))
    }

    /**
     * ⑥ 三枚成员都活着：件里有声明而没人按 = 假开关那一族（批 115 那把惰尺子量过的那格）。
     *
     * 数的是**整个 `desktop/src/main`** 而不是单颗文件：声明住在件里、消费住在 main 里，
     * 只数一颗必然红（那种红不是发现，是量具坏了）。
     */
    @Test fun 那三枚成员都活着() {
        for (literal in listOf("desktopRunLogFile(", "installDesktopRunLog(", "desktopRunLogReading(")) {
            val hits = DesktopSourceTree.desktopMainSources().sumOf { DesktopSourceTree.codeOccurrences(it, literal) }
            assertEquals("$literal 应是「声明一处 + 活壳里按一处」共两处（现在是 $hits）：", 2, hits)
        }
    }
}
