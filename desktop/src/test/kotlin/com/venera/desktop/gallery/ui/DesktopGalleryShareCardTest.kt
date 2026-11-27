package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopGalleryShareCardTest {

    private fun samplePngBytes(width: Int, height: Int, rgbTop: Color, rgbBottom: Color): ByteArray {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        try {
            g.color = rgbTop
            g.fillRect(0, 0, width, height / 2)
            g.color = rgbBottom
            g.fillRect(0, height / 2, width, height - height / 2)
        } finally {
            g.dispose()
        }
        val out = ByteArrayOutputStream()
        ImageIO.write(img, "png", out)
        return out.toByteArray()
    }

    private fun samplePixivPost(): GalleryPost = GalleryPost(
        id = 12345678L,
        site = GallerySite.PIXIV,
        previewUrl = "https://i.pximg.net/img-master/sample_p0.jpg",
        largeUrl = "https://i.pximg.net/img-master/sample_large.jpg",
        fileUrl = "https://i.pximg.net/img-original/sample.png",
        author = "Garu kim",
        tags = "catgirl original",
        width = 800,
        height = 1200,
        pageCount = 3,
        source = "https://www.pixiv.net/artworks/12345678",
    )

    @Test
    fun `分享规格正确保留覆写标题、画师头像与多图数`() {
        val post = samplePixivPost()
        val card = desktopWallCardOf(post)
        val spec = desktopShareCardSpecOf(
            card = card,
            titleOverride = "나나리",
            avatarUrl = "https://i.pximg.net/user-profile/avatar.jpg",
            authorOverride = null,
            hideSourceBadge = true,
            isDark = true,
        )
        assertEquals("나나리", spec.title)
        assertEquals("Garu kim", spec.artist)
        assertEquals("", spec.sourceLabel)
        assertEquals(3, spec.pageCount)
        assertEquals(post.previewUrl, spec.coverUrl)
        assertEquals("https://i.pximg.net/user-profile/avatar.jpg", spec.avatarUrl)
        assertTrue(spec.isDark)
    }

    @Test
    fun `离屏绘制分享卡片在深浅色与打码下均生成合法RGB位图`() {
        val coverBytes = samplePngBytes(300, 420, Color(0xD9, 0x4A, 0x6A), Color(0x30, 0x50, 0x90))
        val avatarBytes = samplePngBytes(64, 64, Color(0xFF, 0xCC, 0x88), Color(0x55, 0x88, 0xCC))
        val card = desktopWallCardOf(samplePixivPost())

        for (dark in listOf(true, false)) {
            for (masked in listOf(false, true)) {
                val spec = desktopShareCardSpecOf(
                    card = card.copy(masked = masked),
                    titleOverride = "나나리 · テスト作品",
                    avatarUrl = "https://example.com/avatar.png",
                    hideSourceBadge = false,
                    isDark = dark,
                )
                val rendered = renderDesktopShareCardImage(spec, coverBytes, avatarBytes)
                assertEquals("粘贴剪贴板需为 TYPE_INT_RGB", BufferedImage.TYPE_INT_RGB, rendered.type)
                assertEquals(560, rendered.width)
                assertTrue("卡片高度应包含封面与底栏", rendered.height > 400)
                // 确认画面不全是单色背景
                val corner = rendered.getRGB(5, 5)
                val center = rendered.getRGB(rendered.width / 2, rendered.height / 3)
                assertNotEquals("封面中心应与画布边角颜色不同", corner, center)
            }
        }
    }

    @Test(expected = RuntimeException::class)
    fun `损坏的封面字节显式抛错而非产出空白图`() {
        val spec = desktopShareCardSpecOf(desktopWallCardOf(samplePixivPost()))
        renderDesktopShareCardImage(spec, byteArrayOf(1, 2, 3, 4), null)
    }
}
