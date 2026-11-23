package com.venera.desktop.gallery.data

import com.venera.desktop.platform.FakePaths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 钉住「用户改写那几颗源脚本」那一层（源编辑）。
 *
 * ## 这一把尺为什么必须存在
 *
 * 桌面上"编辑源脚本"最容易出的**不是崩溃而是假绿**：写盘写进了 `assetDir`，屏上念"已保存"，
 * 而装载读的仍是 jar 内那一份（`EngineAssets.readText` 是包内优先）。要出这口错误面必须**安静地**
 * 成立 —— 而安静正是这套判据盯的东西。
 *
 * ## 判据（七格）
 *
 * ① 写进去的能读回来，且读回来的是**改写那一篇**而不是随包那一篇；
 * ② 空内容不算命中（半截写入不许被当成"用户把这颗改成空脚本"）；
 * ③ 键名清洗：`..` / 带斜杠 / 空串一律进不了文件系统；
 * ④ 撤回之后读不到（覆盖层消失 ⇒ 装载自然回落到包内）；
 * ⑤ 装载侧**先问覆盖层**（源码尺：`EngineSession.loadText` 里 overrideDir 在 readText 之前）；
 * ⑥ 显示名那一读也吃覆盖层（同一篇原文，两处不许漂）；
 * ⑦ 写走临时文件再整体替换（与本仓 `JsonKeyValueStore` 同一判据族）。
 */
class DesktopSourceOverridesTest {

    private fun tempRoot(name: String): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "venera-overrides-$name-${System.nanoTime()}")
        assertTrue("建不出临时目录 $dir", dir.mkdirs())
        dir.deleteOnExit()
        return dir
    }

    /** ① + ④ + ⑦ 那一族：写 → 读 → 撤 → 读不到，且盘上不落 tmp。 */
    @Test
    fun 写进去的读得回来而撤回之后读不到() {
        val paths = FakePaths(tempRoot("write"))
        val dir = DesktopSourceOverrides.dirFor(paths)
        assertTrue("改写层目录名必须自证：" + dir.path, dir.name == "source_overrides")

        val path = DesktopSourceOverrides.writeText(dir, "jm", "class A extends ComicSource {}")
        assertTrue("落点要在改写层目录里：$path", File(path).parentFile == File(dir, "sources"))
        assertEquals("class A extends ComicSource {}", DesktopSourceOverrides.textOf(dir, "jm"))
        assertEquals(setOf("jm"), DesktopSourceOverrides.overriddenKeys(dir))

        // 写走"临时文件 → 整体替换"：结束时不许有 .tmp 留在盘上。
        assertFalse(
            "写完不许留临时文件（半截那一路的判据就在这枚 rename 上）",
            File(File(path).parentFile, "jm.js.tmp").exists(),
        )

        assertTrue(DesktopSourceOverrides.clear(dir, "jm"))
        assertNull(DesktopSourceOverrides.textOf(dir, "jm"))
        assertEquals(emptySet<String>(), DesktopSourceOverrides.overriddenKeys(dir))
        // 再撤一次：交回 true —— `clear` 说的是**结果**（撤完之后没有改写层），不是"我删掉了什么"。
        // 这一档由 `DesktopSourceOverrides.clear` 的 KDoc 定；反过来判的话，装配点那句
        // 「撤不掉」就会说给一个其实已经撤成功的用户听。
        assertTrue("撤回必须是幂等的（重复撤不念失败）", DesktopSourceOverrides.clear(dir, "jm"))
        // 不合法的键名才是真失败那一档（它连文件都不该去碰）。
        assertFalse(DesktopSourceOverrides.clear(dir, "../esc"))
    }

    /** ② 空内容不算命中 —— 那是半截写入的形状，认了它装载会报"未找到类声明"。 */
    @Test
    fun 空内容不算命中() {
        val dir = DesktopSourceOverrides.dirFor(FakePaths(tempRoot("blank")))
        DesktopSourceOverrides.writeText(dir, "x", "")
        assertNull("空那一篇必须读不到（交回 null ⇒ 装载回落到包内）", DesktopSourceOverrides.textOf(dir, "x"))
        // 「已改写」那一列与「能不能撤」都吃这一份名单：把空文件算进去，屏上就会在一颗
        // **实际还跑着包内那一份**的源上标「已改写」、还摆一枚按下去只会念"撤不掉"的把手。
        assertEquals(
            "空文件不许被认成改写层（与 textOf 同一口径）",
            emptySet<String>(),
            DesktopSourceOverrides.overriddenKeys(dir),
        )
    }

    /** ③ 键名清洗：这是进文件系统路径前的那道（评审 F1 那一族）。 */
    @Test
    fun 不合法的键名进不了文件系统() {
        listOf("..", "a/b", "a\\b", "", "a".repeat(65), "jm.js", "含中文").forEach { bad ->
            assertFalse("这一串当键名必须被拒：[$bad]", DesktopSourceOverrides.isSafeKey(bad))
            assertNull("读那一颗不合法的键必须安静交回 null：[$bad]", DesktopSourceOverrides.textOf(File("."), bad))
        }
        // 合法面：随包那 34 颗的形状（小写 + 下划线 + 数字）
        listOf("jm", "copy_manga", "manhuagui", "ehentai", "nhentai", "18h").forEach { good ->
            assertTrue("这一串该被认：[$good]", DesktopSourceOverrides.isSafeKey(good))
        }
        // 拒在 require 而不是默默拼路径：写与撤都要出声
        val dir = DesktopSourceOverrides.dirFor(FakePaths(tempRoot("clean")))
        assertThrowsIllegal { DesktopSourceOverrides.writeText(dir, "../esc", "x") }
        assertFalse(DesktopSourceOverrides.clear(dir, "../esc"))
    }

    private fun assertThrowsIllegal(block: () -> Unit) {
        val err = runCatching(block).exceptionOrNull()
        assertTrue("应为 IllegalArgumentException，实际 ${err?.javaClass?.name}", err is IllegalArgumentException)
    }

    /** ⑤ 装载侧的取用次序：覆盖层在 readText 之前（源码尺，行为尺够不到真引擎）。 */
    @Test
    fun 装载侧先问覆盖层再问包内() {
        val session = com.venera.desktop.gallery.ui.DesktopSourceTree
            .codeText(com.venera.desktop.gallery.ui.DesktopSourceTree.engineProbeSource("EngineSession.kt"))
        val body = session.substringAfter("fun loadText(").substringBefore("fun checkLoadable(")
        val overrideAt = body.indexOf("overrideFile(")
        val packageAt = body.indexOf("EngineAssets.readText(")
        assertTrue("loadText 里没有问覆盖层那一站：$body", overrideAt >= 0)
        assertTrue("loadText 里没有包内/仓库那一站：$body", packageAt >= 0)
        assertTrue(
            "次序反了 —— 先读包内就等于改写永远不生效（那正是本批要防的那种假绿）：\n$body",
            overrideAt < packageAt,
        )
        // 装载本体必须走 loadText，而不是留着那条直读 EngineAssets 的旧路。
        val load = session.substringAfter("fun load(sourceKey: String)").substringBefore("fun loadText(")
        assertTrue("load() 还在直读 EngineAssets.readText（绕过了改写层）：\n$load", "loadText(sourceKey)" in load)
        assertFalse("load() 里不该再出现第二处 readText：\n$load", "EngineAssets.readText(" in load)
    }

    /** ⑥ 显示名与 cookie 名单那一读也吃覆盖层（两处共用一条次序，不许漂）。 */
    @Test
    fun 清单那一读也吃覆盖层() {
        val catalog = com.venera.desktop.gallery.ui.DesktopSourceTree
            .codeText(com.venera.desktop.gallery.ui.DesktopSourceTree.desktopSourceOf("gallery/data/DesktopSourceCatalog.kt"))
        val body = catalog.substringAfter("fun scriptOf(").substringBefore("/** 按 key 现取那一颗的显示名")
        val overrideAt = body.indexOf("DesktopSourceOverrides.textOf(")
        val fileAt = body.indexOf("val file = File(assetDir")
        assertTrue("scriptOf 没问覆盖层：\n$body", overrideAt >= 0)
        assertTrue("scriptOf 里覆盖层不在仓库兜底之前：\n$body", overrideAt < fileAt)
        // 另两读必须把同一枚目录递下去（签名里没有 = 那一读还吃出厂那一篇）
        assertTrue(
            "displayNameOfKey 没带 overrideDir —— 装载吃新脚本而清单念旧名字，就是" +
                "「看着还是那一颗、跑的已经不是它」",
            catalog.contains("fun displayNameOfKey(assetDir: File, key: String, overrideDir: File? = null)"),
        )
        assertTrue(
            "cookieFieldsOfKey 没带 overrideDir —— 用户改的正是那份 cookie 字段名单",
            catalog.contains("fun cookieFieldsOfKey(assetDir: File, key: String, overrideDir: File? = null)"),
        )
    }
}
