package com.venera.desktop.search

import com.venera.desktop.gallery.ui.DesktopSourceTree
import org.junit.Assert.assertEquals
import org.junit.Test

/** 钉住漫画搜索结果布局：三档列数由 viewMode 决定、右缘不叠渐隐带。 */
class DesktopComicSearchLayoutTest {

    private val screen = DesktopSourceTree.codeText(
        DesktopSourceTree.desktopSourceOf("search/DesktopSearchScreen.kt"),
    )

    @Test
    fun 三档列数由viewMode决定() {
        listOf(
            "DesktopSearchViewMode.Single -> GridCells.Fixed(1)",
            "DesktopSearchViewMode.Double -> GridCells.Fixed(doubleCols)",
            "DesktopSearchViewMode.Grid -> GridCells.Fixed(gridCols)",
        ).forEach { spec ->
            assertEquals(
                "缺少视图档位列数映射：$spec",
                1,
                DesktopSourceTree.codeOccurrences(
                    DesktopSourceTree.desktopSourceOf("search/DesktopSearchScreen.kt"),
                    spec,
                ),
            )
        }
    }

    @Test
    fun 右缘没有渐隐带() {
        assertEquals(
            "这一屏不该再挂 hScrollEndFade（右缘会浮出偏色竖条）：",
            0,
            DesktopSourceTree.codeOccurrences(
                DesktopSourceTree.desktopSourceOf("search/DesktopSearchScreen.kt"),
                "hScrollEndFade(",
            ),
        )
    }
}
