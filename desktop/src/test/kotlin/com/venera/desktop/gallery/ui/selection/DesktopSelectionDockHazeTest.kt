package com.venera.desktop.gallery.ui.selection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.ui.tokens.LocalVeneraDarkTheme
import com.venera.desktop.gallery.ui.DesktopChromeColors
import com.venera.desktop.gallery.ui.DesktopWallCardModel
import com.venera.desktop.gallery.ui.LocalChromePalette
import com.venera.desktop.setContentThemed
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class DesktopSelectionDockHazeTest {

    @Test
    fun 悬浮多选栏即使在常驻挂载与动态选中后仍真实模糊背景() = runComposeUiTest {
        var selectedCount by mutableStateOf(0)
        setContentThemed {
            CompositionLocalProvider(
                LocalVeneraDarkTheme provides true,
                LocalChromePalette provides DesktopChromeColors.Dark,
            ) {
                val dockHazeState = rememberHazeState()
                CompositionLocalProvider(LocalSelectionDockHazeState provides dockHazeState) {
                    Box(Modifier.fillMaxSize().background(Color(0xFF0A0D12))) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .hazeSource(dockHazeState, zIndex = 0f),
                        ) {
                            Box(Modifier.width(600.dp).fillMaxHeight().background(Color.Black))
                            Box(Modifier.weight(1f).fillMaxHeight().background(Color.White))
                        }
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = DesktopSelectionDockBottomInset),
                        ) {
                            DesktopSelectionDock(
                                selectedCount = selectedCount,
                                totalCount = 10,
                                onSelectAll = {},
                                onInvertSelect = {},
                                onClearSelect = {},
                            ) {
                                Spacer(Modifier.width(360.dp))
                            }
                        }
                    }
                }
            }
        }
        waitForIdle()
        selectedCount = 3
        waitForIdle()
        mainClock.advanceTimeBy(400)
        waitForIdle()

        val map = onRoot().captureToImage().toPixelMap()
        val y = map.height - 50
        var maxDelta = 0f
        for (x in 520 until 700) {
            val c1 = map[x - 1, y]
            val c2 = map[x, y]
            val d = abs(c2.red - c1.red) + abs(c2.green - c1.green) + abs(c2.blue - c1.blue)
            if (d > maxDelta) maxDelta = d
        }
        assertTrue("多选栏中心玻璃区域应平滑模糊，存在未模糊跳变 maxDelta=$maxDelta", maxDelta < 0.12f)
    }

    @Test
    fun 批量操作弹窗应作为宿主内毛玻璃浮层模糊背景并完整展示() = runComposeUiTest {
        val cards = listOf(
            DesktopWallCardModel(
                title = "Konachan #117146",
                url = "",
                aspect = 0.75f,
                masked = false,
                uid = "konachan:117146",
                author = "poo-san",
                sourcePost = GalleryPost(
                    site = GallerySite.KONACHAN,
                    id = 117146L,
                    width = 400,
                    height = 500,
                ),
            ),
        )
        setContentThemed {
            CompositionLocalProvider(
                LocalVeneraDarkTheme provides true,
                LocalChromePalette provides DesktopChromeColors.Dark,
            ) {
                val dockHazeState = rememberHazeState()
                CompositionLocalProvider(LocalSelectionDockHazeState provides dockHazeState) {
                    Box(Modifier.fillMaxSize().background(Color(0xFF0A0D12))) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .hazeSource(dockHazeState, zIndex = 0f),
                        ) {
                            Box(Modifier.width(512.dp).fillMaxHeight().background(Color.Black))
                            Box(Modifier.weight(1f).fillMaxHeight().background(Color.White))
                        }
                        GalleryBatchActionDialog(
                            cards = cards,
                            initialTab = 0,
                            onDismiss = {},
                            onRemoveCard = {},
                            onConfirmDownload = { _, _, _ -> },
                            onConfirmFavorite = {},
                            onCopySelectedUrls = {},
                        )
                    }
                }
            }
        }
        waitForIdle()
        onNodeWithText("批量操作").assertIsDisplayed()
        onNodeWithText("保存 1 项").assertIsDisplayed()

        val map = onRoot().captureToImage().toPixelMap()
        // 扫描弹窗中央横向穿过 x=512 黑白分界线处（仅在无文字/缩略图的留白行 y = height/2 + 80）
        val y = map.height / 2 + 80
        var maxDelta = 0f
        for (x in 460 until 570) {
            val c1 = map[x - 1, y]
            val c2 = map[x, y]
            val d = abs(c2.red - c1.red) + abs(c2.green - c1.green) + abs(c2.blue - c1.blue)
            if (d > maxDelta) maxDelta = d
        }
        assertTrue("批量操作弹窗卡片背景应平滑模糊背景分界线，maxDelta=$maxDelta", maxDelta < 0.12f)
    }
}
