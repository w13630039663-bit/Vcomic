package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.data.PixivUser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DesktopWallCardArtistDispatchTest {

    @Test
    fun `Pixiv主墙卡片点击画师直达应用内画师页而不是跳搜索`() {
        val post = GalleryPost(
            site = GallerySite.PIXIV,
            id = 987654L,
            tags = "original",
            author = "令时婉夏KiriAINa",
            source = "https://www.pixiv.net/users/424242",
            previewUrl = "https://i.pximg.net/c/250x250_80_a2/img-master/p0.jpg",
        )
        val card = desktopWallCardOf(post)
        var openedArtist: PixivUser? = null
        var searched: Pair<List<GallerySite>, String>? = null

        desktopDispatchWallCardArtist(
            card = card,
            onOpenPixivArtist = { openedArtist = it },
            onSearchArtist = { sites, term -> searched = sites to term },
        )

        assertEquals(424242L, openedArtist?.id)
        assertEquals("令时婉夏KiriAINa", openedArtist?.name)
        assertNull("Pixiv卡片命中画师号时不应触发搜索", searched)
    }

    @Test
    fun `Booru主墙卡片点击画师按真实画师名走对应站点搜索`() {
        val post = GalleryPost(
            site = GallerySite.YANDERE,
            id = 112233L,
            tags = "artist_name blue_eyes",
            author = "yande.re",
            source = "",
            previewUrl = "https://files.yande.re/sample.jpg",
        )
        val card = desktopWallCardOf(post)
        var openedArtist: PixivUser? = null
        var searched: Pair<List<GallerySite>, String>? = null

        desktopDispatchWallCardArtist(
            card = card,
            onOpenPixivArtist = { openedArtist = it },
            onSearchArtist = { sites, term -> searched = sites to term },
        )

        assertNull(openedArtist)
        assertEquals(listOf(GallerySite.YANDERE), searched?.first)
        assertEquals("artist_name", searched?.second)
    }
}
