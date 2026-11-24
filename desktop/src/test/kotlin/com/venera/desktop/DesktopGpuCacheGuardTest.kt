package com.venera.desktop

import com.venera.desktop.app.DesktopGpuCacheGuard
import com.venera.desktop.app.LocalGpuCachePurge
import com.venera.desktop.ui.VeneraLightRailSkin
import com.venera.desktop.gallery.ui.DesktopSourceTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 验证 Tao 后端 GPU 资源缓存上限解析与冗余 angle-natives 移除接线。
 */
class DesktopGpuCacheGuardTest {

    @Test
    fun `parseLimitBytes handles units and falls back on invalid inputs`() {
        val mb = 1024L * 1024L
        assertEquals(32L * mb, DesktopGpuCacheGuard.parseLimitBytes("32M"))
        assertEquals(64L * mb, DesktopGpuCacheGuard.parseLimitBytes(" 64mb "))
        assertEquals(512L * 1024L, DesktopGpuCacheGuard.parseLimitBytes("512K"))
        assertEquals(1024L * mb, DesktopGpuCacheGuard.parseLimitBytes("1G"))
        assertEquals(123456L, DesktopGpuCacheGuard.parseLimitBytes("123456"))
        assertEquals(DesktopGpuCacheGuard.DEFAULT_LIMIT_BYTES, DesktopGpuCacheGuard.parseLimitBytes(null))
        assertEquals(DesktopGpuCacheGuard.DEFAULT_LIMIT_BYTES, DesktopGpuCacheGuard.parseLimitBytes(""))
        assertEquals(DesktopGpuCacheGuard.DEFAULT_LIMIT_BYTES, DesktopGpuCacheGuard.parseLimitBytes("0M"))
        assertEquals(DesktopGpuCacheGuard.DEFAULT_LIMIT_BYTES, DesktopGpuCacheGuard.parseLimitBytes("-16M"))
        assertEquals(DesktopGpuCacheGuard.DEFAULT_LIMIT_BYTES, DesktopGpuCacheGuard.parseLimitBytes("abc"))
    }

    @Test
    fun `build gradle drops conflicting angle natives and VeneraDesktop wires tao gpu guard`() {
        val gradleText = DesktopSourceTree.codeText(File(DesktopSourceTree.repoRoot, "desktop/build.gradle.kts"))
        assertFalse(
            "不应继续外挂冲突的 nucleus.angle-natives，避免双份 libGLESv2.dll",
            gradleText.contains("nucleus.angle-natives"),
        )

        val mainText = listOf(
            "desktop/src/main/kotlin/com/venera/desktop/app/VeneraDesktop.kt",
            "desktop/src/main/kotlin/com/venera/desktop/app/DesktopWindowHooks.kt",
        ).joinToString("\n") { File(DesktopSourceTree.repoRoot, it).readText() }
        assertTrue(mainText.contains("rememberTaoGpuRenderContext()"))
        assertTrue(mainText.contains("DesktopGpuCacheGuard.enforceLimit"))
        assertTrue(mainText.contains("LocalGpuCachePurge provides purgeGpuCache"))
        assertTrue(mainText.contains("evictLargeMemoryCache"))

        val railCode = DesktopSourceTree.codeText(
            File(DesktopSourceTree.repoRoot, "desktop/src/main/kotlin/com/venera/desktop/ui/VeneraLightRailSkin.kt"),
        )
        assertFalse("侧栏桌宠 PetFloat 无限浮动动画应保持注释停用", railCode.contains("PetFloat"))

        val detailCode = DesktopSourceTree.codeText(
            File(
                DesktopSourceTree.repoRoot,
                "desktop/src/main/kotlin/com/venera/desktop/gallery/ui/DesktopGalleryPostDetailScreen.kt",
            ),
        )
        assertTrue(detailCode.contains("gallery_detail_dispose"))
    }

    @Test
    fun `evictLargeMemoryCacheEntries removes large images while keeping small thumbnails`() {
        val cache = coil3.memory.MemoryCache.Builder()
            .maxSizeBytes(32L * 1024L * 1024L)
            .build()
        val thumbKey = coil3.memory.MemoryCache.Key("thumb")
        val bigKey = coil3.memory.MemoryCache.Key("big")
        val thumbImage = object : coil3.Image {
            override val size: Long = 200L * 1024L
            override val width: Int = 200
            override val height: Int = 250
            override val shareable: Boolean = true
            override fun draw(canvas: coil3.Canvas) = Unit
        }
        val bigImage = object : coil3.Image {
            override val size: Long = 8L * 1024L * 1024L
            override val width: Int = 1600
            override val height: Int = 1280
            override val shareable: Boolean = true
            override fun draw(canvas: coil3.Canvas) = Unit
        }
        cache[thumbKey] = coil3.memory.MemoryCache.Value(thumbImage)
        cache[bigKey] = coil3.memory.MemoryCache.Value(bigImage)

        val freed = com.venera.desktop.gallery.data.DesktopImagePipeline.evictLargeMemoryCacheEntries(
            cache = cache,
            reason = "unit_test",
        )
        assertEquals(8L * 1024L * 1024L, freed)
        assertTrue("小封面缩略图应保留", cache[thumbKey] != null)
        assertFalse("大图位图应被立即清除", cache[bigKey] != null)
    }
}
