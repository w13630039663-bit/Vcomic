package com.venera.desktop.architecture

import com.venera.desktop.app.DesktopRoute
import com.venera.desktop.app.VeneraDesktopLargeScreen
import com.venera.desktop.ui.DesktopBackupScreen
import com.venera.desktop.data.desktopBackupExportTo
import com.venera.desktop.data.desktopBackupRestoreFrom
import com.venera.desktop.data.desktopBackupScreenReading
import com.venera.desktop.data.desktopPickBackupOpenPath
import com.venera.desktop.data.desktopPickBackupSavePath
import com.venera.desktop.data.desktopVeneraExportTo
import com.venera.desktop.data.desktopVeneraImportFromPreview
import com.venera.desktop.data.desktopVeneraInspectFile
import com.venera.desktop.gallery.ui.DesktopSourceTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 钉住「备份与还原那一屏真有一扇门，而门后那两跳各接着一颗 runner」—— 。
 *
 * ## 缺口长什么样（现场量的，不是抄旧账）
 *
 * `data/db/CoreTableBackup` 三族表 ↔ JSON 那一栏早进桌面编译面，`CoreTableBackupTest` 22 条
 * 真库往返判据一直绿 —— 而 `desktop/src/main` 里**没有任何一屏**把「导一份 / 灌回来」那两跳
 * 摆出来。于是「本机库塌了怎么留底」这件事桌面上看不见也做不了（与 §114/§115/§119/§124
 * 那一族「有逻辑没有门」同形）。这一批把门开上，用最笨的字符串计数把"消费者存在"钉成会红的判据。
 */
class DesktopBackupEntryTest {

    private val shellFile = DesktopSourceTree.desktopSourceOf("app/VeneraDesktopLargeScreen.kt")
    private val shell = DesktopSourceTree.desktopLargeScreenCode()
    private val shellLines = DesktopSourceTree.desktopLargeScreenLines().filter { it.isNotEmpty() }
    private val explore = DesktopSourceTree.codeText(
        DesktopSourceTree.desktopSourceOf("explore/DesktopExploreScreen.kt"),
    )
    private val screen = DesktopSourceTree.codeText(
        DesktopSourceTree.desktopSourceOf("ui/DesktopBackupScreen.kt"),
    )
    private val route = DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("app/DesktopRoute.kt"))

    /** ① 路由枚举里真有那一档。 */
    @Test
    fun 路由枚举里有备份那一档() {
        assertTrue(
            "`DesktopRoute` 里没有 `data object Backup` ⇒ 那一屏仍然进不去：",
            "data object Backup : DesktopRoute" in route,
        )
    }

    /** ② 切进那一路由**恰两处**：探索屏溢出菜单 + 设置中枢那枚「备份与还原」行。 */
    @Test
    fun 切进备份路由的那一处挂在按得到的把手上() {
        val cuts = shellLines.withIndex().filter { Regex("""(?:route\s*=|onRouteChange\()\s*DesktopRoute\.Backup\b""").containsMatchIn(it.value) }
        assertEquals(
            "切进备份路由的赋值数量变了（现状两处：探索屏溢出菜单 + 设置中枢那一行）：" + cuts.map { shellLines[it.index] },
            2,
            cuts.size,
        )
        // 探索屏顶栏那排钮收进溢出菜单 —— 门还在（菜单项回调），
        // 这一条从"按钮字面量"改钉"屏侧必传形参 + 壳里接线"（下一条仍钉必传）。
        assertTrue("壳里没给探索屏递 onOpenBackup：", "onOpenBackup = {" in shell)
    }

    /** ③ 那枚入口在屏侧是必传形参，而不是带默认值的假开关。 */
    @Test
    fun 那枚入口在屏侧是必传形参而不是假开关() {
        assertTrue("`onOpenBackup` 有了默认值那扇门就又没了", "onOpenBackup: () -> Unit," in explore)
        assertEquals(
            "不许出现带 `= {}` 的那一形：",
            0,
            DesktopSourceTree.codeOccurrences(
                DesktopSourceTree.desktopSourceOf("explore/DesktopExploreScreen.kt"),
                "onOpenBackup: () -> Unit = {}",
            ),
        )
    }

    /**
     * ④ 那一屏有出路，而**离开会把武装那一格交回去**。
     *
     * 与 §115 那族"停在已武装出去转一圈再回来，第二按就删的是当初没点过的那一话"同一条：
     * `onBack` 里必须有 `backupUi.armed = false`，否则出去灌了别的、回来再按就直接 REPLACE 三张表。
     */
    @Test
    fun 那一屏有出路而出屏时把武装交回去() {
        assertTrue("`onBack` 不是必传形参", "onBack: () -> Unit," in screen)
        assertTrue(
            "那一屏没有走统一页壳或设置页模板",
            "DesktopPageShell(" in screen || "SettingsPageTemplate(" in screen,
        )
        assertTrue("统一页壳没接返回回调", "onBack = onBack," in screen)
        val pageShell = DesktopSourceTree.codeText(
            DesktopSourceTree.desktopSourceOf("ui/DesktopPageTemplates.kt"),
        )
        assertTrue("统一页壳没渲那枚返回按钮", "DesktopBackButton(onClick = onBack)" in pageShell)
        // 工作台重做后这一支长到两百多行（拖放 / 预览 / WebDAV 全在同一支里接线），
        // 原来的「取 60 行」会截在 onBack 之前；改成一直取到下一支，口径不变、量程对齐。
        val block = shellLines
            .dropWhile { "DesktopRoute.Backup ->" !in it }
            .takeWhile { "is DesktopRoute.ComicComments ->" !in it }
            .joinToString("\n")
        assertTrue("壳里那一支没把返回切回 Tab（进去出不来）：$block", "route = DesktopRoute.Tab" in block)
        assertTrue(
            "出那一屏时没把武装交回 false ⇒ 回来第二按会真覆盖三张表：$block",
            "backupUi.armed = false" in block,
        )
    }

    /** ⑤ 屏只吃形参：零 IO、零引擎、零 DatabasePorts、零 AWT（那四样全住在壳与 runner 里）。 */
    @Test
    fun 那一屏零IO零AWT零引擎() {
        listOf(
            "io.github.composefluent" to "又开出第二条平行线",
            "java.awt" to "文件框那一跳不许跑进屏里（住在 DesktopBackup 那颗件 + 壳）",
            "DatabasePorts" to "屏不许自己开库",
            "CoreTableBackup" to "三族表读写住在 runner，不在屏里",
            "desktopBackupExportTo" to "runner 由壳调，不在屏里",
            "desktopBackupRestoreFrom" to "runner 由壳调，不在屏里",
            "desktopVeneraExportTo" to "全量导出 runner 由壳调，不在屏里",
            "desktopVeneraImportFromPreview" to "全量导入 runner 由壳调，不在屏里",
            "desktopVeneraInspectFile" to "解析 runner 由壳调，不在屏里",
            "withContext" to "屏里不许有 IO",
            "LaunchedEffect" to "屏里不许有协程",
        ).forEach { (banned, why) ->
            assertEquals(
                "$why：",
                0,
                DesktopSourceTree.codeOccurrences(
                    DesktopSourceTree.desktopSourceOf("ui/DesktopBackupScreen.kt"),
                    banned,
                ),
            )
        }
    }

    /**
     * ⑥ 恢复那两枚把手各挂一件事，而且**第一次按不落 runner**（两步确认）。
     *
     * 这一条钉的是最危险的形状：一枚把手一按就 REPLACE 三张表 = 用户误点一次就把现有库
     * 覆盖了。所以 `onArmRestore` 那一支里只许出现 `backupUi.armed = true`（武装）而**绝不**出现
     * `desktopVeneraImportFromPreview`；那跳 runner 只许挂在 `onConfirmRestore`（第二次按）里。
     */
    @Test
    fun 恢复那枚要两按才真灌第一次只武装() {
        // 第一次按那一支：只武装、不落 runner
        val armHead = shellLines.indexOfFirst { "onArmRestore = {" in it }
        assertTrue("壳里没有 onArmRestore 那一支（判据失效，不是通过）", armHead >= 0)
        val armBody = shellLines.drop(armHead).take(6).joinToString("\n")
        assertTrue("第一次按没把 backupUi.armed 置真：$armBody", "backupUi.armed = true" in armBody)
        assertEquals(
            "第一次按那一支里不许出现真灌那跳 runner（一按就覆盖三张表 = 破坏性假开关）：$armBody",
            0,
            Regex("desktopVeneraImportFromPreview").findAll(armBody).count(),
        )
        // 第二次按那一支：才落 runner
        val confirmHead = shellLines.indexOfFirst { "onConfirmRestore = {" in it }
        assertTrue("壳里没有 onConfirmRestore 那一支", confirmHead >= 0)
        val confirmBody = shellLines.drop(confirmHead).take(20).joinToString("\n")
        assertTrue("第二次按没落恢复 runner：", "desktopVeneraImportFromPreview(" in confirmBody)
    }

    /**
     * ⑦ 那两跳 runner 各在 main 里恰好「一处声明 + 一处壳里按」，别处另起一轨就红。
     *
     * ⚠️ 豁免登记（档二 5b-6）：`desktopVeneraExportTo(` 另有第三个消费者 ——
     * `DesktopWebDavSync.kt` 的上传腿（导出一份再 PUT 云端，复用桌面备份链而不是另建第二份导出）。
     * 本条的要害（壳里那一处不许丢、不许散成多处壳）由 VeneraDesktopLargeScreen 断言继续钉；
     * 新增的合法消费者必须逐枚点名，别处再冒一轨照红。
     */
    @Test
    fun 两跳runner各一处声明一处壳里按() {
        val exportSites = DesktopSourceTree.desktopMainGroupedOccurrences("desktopVeneraExportTo(")
        assertEquals(
            "desktopVeneraExportTo( 的消费者只有这三处（声明 + 壳里按 + WebDAV 上传腿）：$exportSites",
            listOf("DesktopVeneraArchive.kt", "DesktopWebDavSync.kt", "VeneraDesktopLargeScreen.kt"),
            exportSites.map { it.first },
        )
        listOf(
            "desktopVeneraImportFromPreview(",
            "desktopPickBackupSavePath(",
            "desktopPickBackupOpenPath(",
        ).forEach { call ->
            val sites = DesktopSourceTree.desktopMainGroupedOccurrences(call)
            // 声明那颗文件里也有一次 ⇒ 期望两处：声明 + 壳里按
            assertTrue(
                "$call 的消费者形状变了（期望两处：件里声明 + 壳里按）：$sites",
                sites.size == 2 && sites.any { it.first in DesktopSourceTree.desktopLargeScreenSources().map { f -> f.name } },
            )
        }
    }

    /** ⑧ 那一屏的取证读数真接上了（`desktopBackupScreenReading` 从前是零调用点那族）。 */
    @Test
    fun 那一屏的读数真有调用点() {
        val sites = DesktopSourceTree.desktopMainGroupedOccurrences("desktopBackupScreenReading(")
        assertEquals(
            "读数件的消费者形状变了（期望「件文件一处声明 + 壳里一处打」）：$sites",
            listOf("DesktopBackup.kt" to 1, "VeneraDesktopLargeScreen.kt" to 1),
            sites,
        )
        val idx = shellLines.indexOfFirst { "LaunchedEffect(route, backupUi.lastCounts" in it }
        assertTrue("打读数那颗 effect 的键里没带 counts ⇒ 跑完一趟不会重打：$idx", idx >= 0)
    }
}
