package com.venera.desktop.gallery.data

import com.venera.compose.data.platform.PathProvider
import com.venera.desktop.gallery.ui.DesktopSourceTree
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * （翻向）·「哪几颗源被显式开启」落在盘上的那一格。
 *
 * ## 为什么账记的是「开启」而不是「停用」
 *
 * 主人的裁决：「现在的源管理大部分连登录都没，这种应该是手动开启而不是全部开的」。
 * 旧形状（默认全开、记停用）已被作废；类注释里写了为什么旧账没法互推成新账。
 * 与安卓的**刻意不同轨**这一条照旧成立（摘要：安卓那张 `comic_source` 表是"手机上装过哪些源"
 * 的元数据，这一份是"这台机器上哪几颗参与选源"）。
 */
class DesktopSourceStateTest {

    private class TempPaths(root: File) : PathProvider {
        override val dataRoot: File = root
        override val cacheRoot: File = File(root, "cache")
    }

    private fun tempRoot(name: String): File =
        File(System.getProperty("java.io.tmpdir"), "b155_$name${System.nanoTime()}").apply { mkdirs() }

    @Test
    fun 开启清单写下去再换新实例读得回来() {
        val paths = TempPaths(tempRoot("roundtrip"))
        val state = DesktopSourceState(paths)
        state.setEnabledKeys(setOf("jm", "goda"))
        assertEquals(
            "读写必须同轨（不同轨的表现是屏上开了而重启又关着）",
            setOf("goda", "jm"),
            DesktopSourceState(paths).enabledKeys(),
        )
    }

    @Test
    fun 全新数据读到的是null而不是空表() {
        // 翻向后"没这份文件"与"用户把开关全关了"必须分得开：前者落默认档，后者就是全关。
        val state = DesktopSourceState(TempPaths(tempRoot("fresh")))
        assertNull("没有这份文件 = 从没动过开关 ⇒ null（由调用点落默认档），不是空表", state.enabledKeys())
    }

    @Test
    fun 空表与从没写过分得开() {
        val paths = TempPaths(tempRoot("empty"))
        val state = DesktopSourceState(paths)
        state.setEnabledKeys(emptySet())
        assertEquals(
            "真写过空数组 = 用户账（全关着），不许塌回「从没动过开关」那一档",
            emptySet<String>(),
            DesktopSourceState(paths).enabledKeys(),
        )
    }

    @Test
    fun 默认档是随包五颗含goda而交集只吃清单里有的() {
        val keys = listOf("baozi", "copy_manga", "goda", "jm", "manga_dex", "nhentai")
        assertEquals(
            "没用户账时落默认五颗（含 goda）",
            setOf("baozi", "copy_manga", "goda", "jm", "manga_dex"),
            DesktopSourceState.effectiveEnabled(keys, null),
        )
        // 随包清单哪天少了 jm ⇒ 默认档不许指向一颗不存在的源
        val short = listOf("baozi", "copy_manga")
        assertEquals("默认档与清单求交后用（清单里没有的那颗不许进）", setOf("baozi", "copy_manga"), DesktopSourceState.effectiveEnabled(short, null))
        // 用户账原样生效，默认档不再插手
        assertEquals(setOf("nhentai"), DesktopSourceState.effectiveEnabled(keys, setOf("nhentai")))
    }

    @Test
    fun 老用户账首次读入自动补开一次goda且允许后续手动关闭() {
        val paths = TempPaths(tempRoot("backfill"))
        val raw = com.venera.compose.data.platform.JsonKeyValueStore(DesktopSourceState.PREFS_NAME, paths)
        raw.putAll(mapOf("enabled_sources_init" to true, "enabled_sources" to setOf("baozi")))

        val firstRead = DesktopSourceState(paths).enabledKeys()
        assertEquals(
            "老用户账首次升级应自动补开 goda",
            setOf("baozi", "goda"),
            firstRead,
        )

        val state = DesktopSourceState(paths)
        state.setEnabledKeys(setOf("baozi"))
        assertEquals(
            "补开仅执行一次，用户手动关闭 goda 后不得再次复开",
            setOf("baozi"),
            DesktopSourceState(paths).enabledKeys(),
        )
    }


    @Test
    fun 落盘文件名自证是桌面本地那份而不是共享表() {
        val paths = TempPaths(tempRoot("name"))
        DesktopSourceState(paths).setEnabledKeys(setOf("jm"))
        val file = File(paths.subDir("prefs"), "venera_desktop_sources.json")
        assertTrue("开关状态要落在桌面自己那份文件里（共享表是另一件事）：${paths.dataRoot.absolutePath}", file.isFile)
    }

    @Test
    fun 那句读数念得出开启几颗与档位来源() {
        val fromDefault = DesktopSourceState.reading(setOf("jm"), fromDefault = true, total = 34)
        assertTrue("要念得出开启数：$fromDefault", fromDefault.contains("开启=1"))
        assertTrue("要念得出清单总数：$fromDefault", fromDefault.contains("清单=34"))
        assertTrue("没动过开关那一档要念得出默认档：$fromDefault", fromDefault.contains("默认"))
        val userOwn = DesktopSourceState.reading(setOf("jm", "goda"), fromDefault = false, total = 34)
        assertTrue("用户账那一档要念得出用户账：$userOwn", userOwn.contains("用户账"))
        assertTrue("要把「这一份只影响桌面」写进读数，免得被读成两端不同步的 bug：$userOwn", userOwn.contains("桌面本地"))
    }
}
