package com.venera.desktop.architecture

import com.venera.desktop.gallery.ui.DesktopSourceTree
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 校验 Windows SegmentHeap 清单注入脚本与构建接线。
 */
class DesktopSegmentHeapConfigTest {

    @Test
    fun `segment heap script and gradle wiring stay in place`() {
        val script = File(DesktopSourceTree.repoRoot, "scripts/enable-segment-heap.ps1")
        assertTrue("缺少 SegmentHeap 注入脚本：${script.path}", script.isFile)
        val scriptText = script.readText()
        assertTrue(scriptText.contains("SegmentHeap"))
        assertTrue(scriptText.contains("PrepareJdkLauncher"))

        val gradle = DesktopSourceTree.codeText(File(DesktopSourceTree.repoRoot, "desktop/build.gradle.kts"))
        assertTrue(gradle.contains("prepareSegmentHeapLauncher"))
        assertTrue(gradle.contains("executable(segmentHeapJavaExe.get().asFile.absolutePath)"))
        assertTrue(gradle.contains("createDistributable"))
    }
}
