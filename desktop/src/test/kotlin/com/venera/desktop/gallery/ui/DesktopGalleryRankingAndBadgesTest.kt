package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.data.PixivFeedParse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 排行榜头像解析与卡片右上角 R18 / AI 角标判据测试。
 */
class DesktopGalleryRankingAndBadgesTest {

    @Test
    fun `pixiv ranking parses profile_img as authorAvatarUrl`() {
        val sampleJson = """
            {
              "contents": [
                {
                  "illust_id": 12345678,
                  "title": "Test Ranking Work",
                  "user_id": 998877,
                  "user_name": "ArtistA",
                  "profile_img": "https://i.pximg.net/user-profile/img/2026/01/01/test_50.png",
                  "url": "https://i.pximg.net/c/240x480/img-master/img/2026/01/01/12345678_p0_master1200.jpg",
                  "width": 1000,
                  "height": 1400,
                  "illust_page_count": "1",
                  "illust_type": "0",
                  "rank": 1,
                  "is_masked": false,
                  "tags": ["オリジナル"]
                }
              ]
            }
        """.trimIndent()

        val works = PixivFeedParse.ranking(sampleJson)
        assertEquals(1, works.size)
        assertEquals(
            "https://i.pximg.net/user-profile/img/2026/01/01/test_50.png",
            works[0].authorAvatarUrl,
        )
    }

    @Test
    fun `top right badges match explicit r18 and ai flag`() {
        val r18Post = GalleryPost(
            site = GallerySite.PIXIV,
            id = 1L,
            tags = "girl",
            rating = "r18",
            siteAiFlag = true,
        )
        val safePost = GalleryPost(
            site = GallerySite.PIXIV,
            id = 2L,
            tags = "girl",
            rating = "general",
            siteAiFlag = false,
        )
        val booruExplicit = GalleryPost(
            site = GallerySite.GELBOORU,
            id = 3L,
            tags = "girl",
            rating = "explicit",
        )
        val r15Post = GalleryPost(
            site = GallerySite.PIXIV,
            id = 4L,
            tags = "girl",
            rating = "r15",
        )
        assertTrue(desktopCardShowR18Badge(r18Post))
        assertTrue(desktopCardShowR18Badge(booruExplicit))
        assertFalse(desktopCardShowR18Badge(safePost))
        assertFalse(desktopCardShowR18Badge(r15Post))
        assertTrue(aiBadgeFor(r18Post, badgeOn = true))
        assertFalse(aiBadgeFor(r18Post, badgeOn = false))
        assertFalse(aiBadgeFor(safePost, badgeOn = true))
    }

    @Test
    fun `cards and home rows wire top right badges`() {
        val cardCode = DesktopSourceTree.codeText(
            java.io.File(DesktopSourceTree.repoRoot, "desktop/src/main/kotlin/com/venera/desktop/gallery/ui/DesktopGalleryCard.kt"),
        )
        val homeRowsCode = DesktopSourceTree.codeText(
            java.io.File(DesktopSourceTree.repoRoot, "desktop/src/main/kotlin/com/venera/desktop/gallery/ui/DesktopGalleryHomeRows.kt"),
        )
        assertTrue(cardCode.contains("DesktopGalleryCardTopRightBadges("))
        assertTrue(homeRowsCode.contains("DesktopGalleryCardTopRightBadges("))
    }
}
