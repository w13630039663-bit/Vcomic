package com.venera.desktop.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 视口尺寸的量法：`padding` 之后的 `onSizeChanged` 必须报内容区尺寸。
 *
 * 放大镜的内容层尺寸、页宽、锚点原点全挂在这个读数上；它一旦报成含 padding 的外框，
 * 镜内每页就会比主视口宽一圈，同一滚动/页位置落到别处。
 */
@OptIn(ExperimentalTestApi::class)
class DesktopReaderViewportSizeTest {

    @Test
    fun `padding 后的 onSizeChanged 报内容区尺寸`() = runComposeUiTest {
        var size = IntSize.Zero
        setContent {
            Box(Modifier.size(800.dp, 600.dp)) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(start = 120.dp)
                        .padding(horizontal = 16.dp)
                        .onSizeChanged { size = it },
                )
            }
        }

        // 800 - 120（左栏）- 16*2（左右页边距）= 648
        assertEquals("宽度应为去掉左栏与页边距后的内容区", 648, size.width)
        assertEquals("高度不含纵向 padding，应等于父高", 600, size.height)
    }
}
