package com.venera.desktop.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import kotlin.math.abs

/**
 * 放大镜的锚点换算：内容层经布局与镜头变换后，光标对应的内容点在根坐标系里的真实落点必须等于光标本身。
 *
 * 刻意选用非中心光标与超大视口，避免外层 offset 与内容层居中偏移正负抵消造成假绿。
 */
@OptIn(ExperimentalTestApi::class)
class DesktopReaderMagnifierLensTest {

    @Test
    fun `非对称光标处的内容严格落在镜框中心`() = runComposeUiTest {
        assertPointerAnchorsToLensCenter(
            viewportW = 1924f,
            viewportH = 1363f,
            origin = Offset(168f, 0f),
            pointer = Offset(1544f, 387f),
            scale = 2.0f,
        )
    }

    @Test
    fun `左上角与不同倍率下光标内容同样落在镜框中心`() = runComposeUiTest {
        assertPointerAnchorsToLensCenter(
            viewportW = 1400f,
            viewportH = 900f,
            origin = Offset(120f, 40f),
            pointer = Offset(260f, 180f),
            scale = 2.5f,
        )
    }

    private fun androidx.compose.ui.test.ComposeUiTest.assertPointerAnchorsToLensCenter(
        viewportW: Float,
        viewportH: Float,
        origin: Offset,
        pointer: Offset,
        scale: Float,
    ) {
        var contentCoords: LayoutCoordinates? = null
        var contentSize = IntSize.Zero

        setContent {
            Box(Modifier.size(2200.dp, 1500.dp)) {
                ReaderMagnifierLens(
                    visible = true,
                    pointerPosition = pointer,
                    layerOrigin = origin,
                    viewportWidthPx = viewportW,
                    viewportHeightPx = viewportH,
                    viewportContent = {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .onGloballyPositioned {
                                    contentCoords = it
                                    contentSize = it.size
                                },
                        )
                    },
                    scale = scale,
                )
            }
        }

        val coords = contentCoords
        assertNotNull("内容层未完成布局", coords)
        assertEquals("内容层宽度应为视口宽", viewportW.toInt(), contentSize.width)
        assertEquals("内容层高度应为视口高", viewportH.toInt(), contentSize.height)

        val localPoint = Offset(pointer.x - origin.x, pointer.y - origin.y)
        val renderedPointInRoot = coords!!.localToRoot(localPoint)
        assertEquals(
            "光标内容落点 X 偏移：实测=$renderedPointInRoot 期望=$pointer",
            0f,
            abs(renderedPointInRoot.x - pointer.x),
            1.5f,
        )
        assertEquals(
            "光标内容落点 Y 偏移：实测=$renderedPointInRoot 期望=$pointer",
            0f,
            abs(renderedPointInRoot.y - pointer.y),
            1.5f,
        )
    }
}
