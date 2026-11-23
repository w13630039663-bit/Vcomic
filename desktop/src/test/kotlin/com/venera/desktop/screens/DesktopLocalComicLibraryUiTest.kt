package com.venera.desktop.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.desktop.downloads.DesktopLocalComicLibraryScreen
import com.venera.desktop.gallery.data.DesktopLocalComic
import com.venera.desktop.gallery.data.DesktopLocalComicLibraryScan
import com.venera.desktop.gallery.data.desktopLocalComicLibraryHeader
import com.venera.desktop.gallery.data.desktopLocalComicLibraryRowLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription

/**
 * 「本地漫画」那一域的**离屏语义树**判据。
 *
 * ## 只喂数据，不碰盘、不碰壳里那颗 effect
 *
 * 本级扫描、`runCatching` 兜底、取证读数全住在壳（`DesktopLocalComicsEntryTest` ⑧ 钉着那颗 effect 的形状）。
 * 这里量的是「屏吃成品表时念得出哪一句、按不落、有没有把两档塌成一句」—— 离屏装配级。
 */
@OptIn(ExperimentalTestApi::class)
class DesktopLocalComicLibraryUiTest {

    private val wide = WideScreenLayoutMode.Expanded

    @Composable
    private fun Stage(content: @Composable () -> Unit) {
        Box(Modifier.fillMaxSize()) { content() }
    }

    private fun one() = DesktopLocalComic("sid", "真题名", "jm", chapterCount = 3, totalPages = 9, "/abs/sid", 0)

    private fun scanFull() = DesktopLocalComicLibraryScan(listOf(one()), rootExists = true, error = null)

    /** 表头那句**逐字**上屏（屏不另写一句，只搬那颗纯函数的原话）。 */
    @Test
    fun 本地漫画屏表头念那颗纯函数的原话() = runComposeUiTest {
        val scan = scanFull()
        setContent {
            Stage {
                DesktopLocalComicLibraryScreen(mode = wide, scan = scan, note = null, onBack = {})
            }
        }
        onNodeWithText(desktopLocalComicLibraryHeader(scan)).assertExists()
        // 卡片从「一整行 rowLine」换成的卡座（题名 + 话/页），
        // 这里改量卡上那两格（题名一格、话页一格），不再量 rowLine 整串。
        onNodeWithText("真题名").assertExists()
        onNodeWithText("共 3 话 · 9 页").assertExists()
    }

    /** 还没数回来时念「正在数」，不改口成「本机没有」，也不摆行。 */
    @Test
    fun 还没数回来时念正在数而不改口成没有() = runComposeUiTest {
        setContent {
            Stage {
                DesktopLocalComicLibraryScreen(mode = wide, scan = null, note = null, onBack = {})
            }
        }
        onNodeWithText("正在数本机那一棵树…").assertExists()
        assertTrue(
            "把「还没读到」说成「本机没有」是本仓那一族假话：" +
                onAllNodesWithText("本机还没有认得出的本地漫画").fetchSemanticsNodes(),
            onAllNodesWithText("本机还没有认得出的本地漫画").fetchSemanticsNodes().isEmpty(),
        )
    }

    /** 数回来而确实空了：那句改口念「空」。 */
    @Test
    fun 空表念得出确实没有本地漫画() = runComposeUiTest {
        setContent {
            Stage {
                DesktopLocalComicLibraryScreen(
                    mode = wide,
                    scan = DesktopLocalComicLibraryScan(emptyList(), rootExists = true, error = null),
                    note = null, onBack = {},
                )
            }
        }
        onNodeWithText("本机还没有认得出的本地漫画").assertExists()
    }

    /** 数不出来时念那句「数不出来 + 成因」，不扮「确实空了」。 */
    @Test
    fun 塌了念数不出来而不是确实空了() = runComposeUiTest {
        setContent {
            Stage {
                DesktopLocalComicLibraryScreen(
                    mode = wide,
                    scan = DesktopLocalComicLibraryScan(emptyList(), rootExists = true, error = "IO boom"),
                    note = null, onBack = {},
                )
            }
        }
        onNodeWithText("数不出来：IO boom").assertExists()
        assertTrue(
            "数不出来时不许同时念「本机还没有」那一句（两档必须分得开）：",
            onAllNodesWithText("本机还没有认得出的本地漫画").fetchSemanticsNodes().isEmpty(),
        )
    }

    /** 「← 返回」按得到且落进那枚回调一次（那一屏不是单行道）。 */
    @Test
    fun 返回按得到且落一次() = runComposeUiTest {
        var backs = 0
        setContent {
            Stage {
                DesktopLocalComicLibraryScreen(mode = wide, scan = scanFull(), note = null, onBack = { backs++ })
            }
        }
        onNodeWithContentDescription("返回").performClick()
        assertEquals("返回按一次该恰好落一次：", 1, backs)
    }

    /** 本级那一屏**不摆**任何「点开这一本」的假把手（离线阅读那一跳还没接到本地树上，摆就是假门）。 */
    @Test
    fun 不摆那枚点开的假把手() = runComposeUiTest {
        setContent {
            Stage {
                DesktopLocalComicLibraryScreen(mode = wide, scan = scanFull(), note = null, onBack = {})
            }
        }
        // 屏上只有一枚「← 返回」，别的把手（点开 / 删除 / 导出）一颗都不该摆。
        assertEquals(
            "那一屏只许一枚「← 返回」，别的一律不摆：",
            1, onAllNodesWithContentDescription("返回").fetchSemanticsNodes().size,
        )
    }
}
