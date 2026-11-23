package com.venera.desktop.search

import com.venera.desktop.sourcemanage.DesktopSourceRowUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * （改口）· 搜索屏源选择器"列哪些源"的值尺。
 *
 * ## 判据换成一条
 *
 * 从前那一版按预设表分级挑（只列 safe），改判：「源管理开了什么就有什么可以选择的」。
 * 于是这一闸只剩**enabled** 一条 —— 分级不再参与选源（那是遮罩那条链的事，另一颗件的尺子盯着）。
 * 这一批改的正是"选源被分级锁住"那条错：它把功能面与遮罩面绑在一颗闸上，开了 jm 却搜不了 jm。
 */
class DesktopSearchSourceFilterTest {

    private fun row(key: String, enabled: Boolean = true) =
        DesktopSourceRowUi(key = key, title = key, enabled = enabled, overridden = false, cookieFields = emptyList())

    /** ① 开着的都列，nsfw/mixed 也不例外（分级不参与选源）。 */
    @Test
    fun 开着的源全列而不按分级挑() {
        // copy_manga(safe) / ehentai(mixed) / jm(nsfw) 三档全开 ⇒ 三颗都该进选择器。
        // 从前那一版只交回 copy_manga，那正是被改判掉的行为。
        val out = desktopSearchSelectableSources(listOf(row("copy_manga"), row("ehentai"), row("jm")))
        assertEquals("开了就该能选，分级不锁功能门：", listOf("copy_manga", "ehentai", "jm"), out)
    }

    /** ② 顺序 = 清单原序，选择器不自己重排。 */
    @Test
    fun 列出的次序跟着清单原序走() {
        val out = desktopSearchSelectableSources(listOf(row("jm"), row("copy_manga"), row("baozi")))
        assertEquals(listOf("jm", "copy_manga", "baozi"), out)
    }

    /** ③ 关着的不列（停用是真停用，与探索/聚合同一份账）。 */
    @Test
    fun 关着的源不列() {
        val out = desktopSearchSelectableSources(listOf(row("jm"), row("copy_manga", enabled = false)))
        assertEquals("停用那颗不该还在选择器里：", listOf("jm"), out)
    }

    /** ④ 全关 ⇒ 空表（屏那边空表整排不摆）。 */
    @Test
    fun 全关时交回空表() {
        val out = desktopSearchSelectableSources(listOf(row("jm", enabled = false), row("copy_manga", enabled = false)))
        assertTrue("没有一颗开着就是空：$out", out.isEmpty())
    }

    /** ⑤ 空清单 ⇒ 空表（源清单没读回来那一趟，屏不摆半排）。 */
    @Test
    fun 空清单也是空表() {
        assertTrue(desktopSearchSelectableSources(emptyList()).isEmpty())
    }
}
