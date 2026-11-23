package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GallerySite
import org.junit.Assert.assertEquals
import org.junit.Test

/** 查询级成人内容排除的后缀判据（悬浮）：档对不对、漏站不注。 */
class DesktopSearchRatingSuffixTest {

    @Test
    fun 全年龄档给各站拼对排除tag() {
        assertEquals("-rating:e", desktopSearchRatingSuffix(GallerySite.YANDERE, allowExplicit = false))
        assertEquals("-rating:e", desktopSearchRatingSuffix(GallerySite.KONACHAN, allowExplicit = false))
        assertEquals("-rating:explicit", desktopSearchRatingSuffix(GallerySite.GELBOORU, allowExplicit = false))
    }

    @Test
    fun 允许档与不注的站恒回空串() {
        GallerySite.entries.forEach { site ->
            assertEquals("", desktopSearchRatingSuffix(site, allowExplicit = true))
        }
        // SAFEBOORU 天生全年龄；TBIB 站方 rating 格未实测不注（注错=整腿空结果）；PIXIV 不走 booru 轴。
        listOf(GallerySite.SAFEBOORU, GallerySite.TBIB, GallerySite.PIXIV).forEach { site ->
            assertEquals("", desktopSearchRatingSuffix(site, allowExplicit = false))
        }
    }
}
