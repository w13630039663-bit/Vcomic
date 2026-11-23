package com.venera.desktop.gallery.data

import com.venera.compose.gallery.data.GallerySite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TBIB 裸数组 → 卡片的解码路。
 *
 * 必须真解一次：`decodeFromString` 是 inline reified，DTO 的 companion 若是 private，
 * 编译全绿而运行期读不到 `Companion` 字段（IllegalAccessError）。
 * 顺带钉住地址拼法：前缀取 image 词干（取 hash 会 404），`sample` 为 false 时大图落原图。
 */
class DesktopTbibParseTest {

    private val payload = """
        [
          {"id": 6234567, "directory": 42, "image": "abc123def456.png", "hash": "deadbeef",
           "height": 2400, "width": 1600, "rating": "s", "score": 12, "tags": "1girl solo",
           "owner": "someone", "change": 1700000000, "sample": true,
           "sample_width": 1200, "sample_height": 1800, "parent_id": 0},
          {"id": 6234568, "directory": 42, "image": "nosample.jpg", "hash": "cafe",
           "height": 900, "width": 600, "rating": "q", "score": 3, "tags": "landscape",
           "owner": "", "change": 1700000001, "sample": false,
           "sample_width": 0, "sample_height": 0, "parent_id": 0},
          {"id": 0, "directory": 0, "image": "", "hash": "bad", "tags": "残缺"},
          {"id": 6234569, "directory": 42, "image": "extra.jpg", "hash": "x", "some_new_field": 1}
        ]
    """.trimIndent()

    @Test
    fun 裸数组解得开而且地址按目录与图片名拼() {
        val posts = decodeTbibPosts(payload)

        assertEquals("残缺那条（id=0 / image 空）该丢掉而不是拼出空地址", 3, posts.size)
        assertTrue("站别串了：TBIB 的 id 与别站同号是两张不同的图", posts.all { it.site == GallerySite.TBIB })
        assertEquals(listOf(6234567L, 6234568L, 6234569L), posts.map { it.id })

        val sampled = posts[0]
        assertEquals(
            "缩略图前缀不是 image 去扩展名（按 hash 拼实测 404）",
            "https://tbib.org/thumbnails/42/thumbnail_abc123def456.jpg",
            sampled.previewUrl,
        )
        assertEquals("sample=true 时中档该走 samples 那张", "https://tbib.org/samples/42/sample_abc123def456.jpg", sampled.largeUrl)
        assertEquals("中档尺寸该取样本尺寸", 1200, sampled.largeWidth)
        assertEquals("原图地址该是 images/{directory}/{image}", "https://tbib.org/images/42/abc123def456.png", sampled.fileUrl)
        assertEquals("png", sampled.fileExt)
        assertEquals("deadbeef", sampled.md5)

        val plain = posts[1]
        assertEquals("sample=false 时中档直接落原图", "https://tbib.org/images/42/nosample.jpg", plain.largeUrl)
        assertEquals("sample=false 时中档尺寸该取原图尺寸", 900, plain.largeHeight)
        assertEquals(
            "sample=false 时缩略图仍走 thumbnail_ 前缀",
            "https://tbib.org/thumbnails/42/thumbnail_nosample.jpg",
            plain.previewUrl,
        )
    }

    @Test
    fun 空数组给空表而不是抛() {
        assertEquals(emptyList<com.venera.compose.gallery.data.GalleryPost>(), decodeTbibPosts("[]"))
    }
}
