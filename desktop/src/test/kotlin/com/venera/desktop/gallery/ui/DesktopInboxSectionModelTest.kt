package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 收件箱装配纯判据的尺：条目序列、分界线与段头文案、分源量词、同步时刻与水位分侧。
 */
class DesktopInboxSectionModelTest {

    private fun cardItem(site: GallerySite, id: Long, author: String = "author") = ArtistInboxCardItem(
        model = desktopWallCardOf(
            GalleryPost(site = site, id = id, author = author, previewUrl = "https://example.test/$id.jpg"),
        ),
    )

    private fun gallerySegment(newCount: Int = 2, oldCount: Int = 1, unread: Int = 3) = ArtistInboxSegment(
        source = ArtistFeedSource.GALLERY,
        label = inboxSectionHeadLabel("图库", newCount + oldCount, "张"),
        unreadCount = unread,
        newCards = (1..newCount).map { cardItem(GallerySite.YANDERE, it.toLong()) },
        oldCards = (100 until 100 + oldCount).map { cardItem(GallerySite.YANDERE, it.toLong()) },
    )

    private fun pixivSegment(newCount: Int = 1, oldCount: Int = 2, unread: Int = 3) = ArtistInboxSegment(
        source = ArtistFeedSource.PIXIV,
        label = inboxSectionHeadLabel("pixiv", newCount + oldCount, "篇"),
        unreadCount = unread,
        newCards = (200 until 200 + newCount).map { cardItem(GallerySite.PIXIV, it.toLong()) },
        oldCards = (300 until 300 + oldCount).map { cardItem(GallerySite.PIXIV, it.toLong()) },
    )

    @Test
    fun `全部视图图库段头在前pixiv段头在后两段各恰一条分界线`() {
        val entries = artistInboxEntries(ArtistFeedSource.ALL, gallerySegment(), pixivSegment(), lastReadTimestamp = 0L)
        val heads = entries.filterIsInstance<ArtistInboxEntry.SectionHead>()
        assertEquals(2, heads.size)
        assertEquals(ArtistFeedSource.GALLERY, heads[0].source)
        assertEquals(ArtistFeedSource.PIXIV, heads[1].source)
        assertTrue(entries.indexOf(heads[0]) < entries.indexOf(heads[1]))
        // 图库分界线在两段头之间；pixiv 分界线在 pixiv 段头之后
        // （定位用引用比较：两条分界线在未查看时同文案，等值比较会撞行）
        val dividers = entries.filterIsInstance<ArtistInboxEntry.Divider>()
        assertEquals(2, dividers.size)
        val firstDividerAt = entries.indexOfFirst { it === dividers[0] }
        val secondDividerAt = entries.indexOfFirst { it === dividers[1] }
        val galleryHeadAt = entries.indexOfFirst { it === heads[0] }
        val pixivHeadAt = entries.indexOfFirst { it === heads[1] }
        assertTrue(firstDividerAt > galleryHeadAt)
        assertTrue(firstDividerAt < pixivHeadAt)
        assertTrue(secondDividerAt > pixivHeadAt)
    }

    @Test
    fun `段头文案张篇分源`() {
        assertEquals("图库 12 张", inboxSectionHeadLabel("图库", 12, "张"))
        assertEquals("pixiv 8 篇", inboxSectionHeadLabel("pixiv", 8, "篇"))
        val entries = artistInboxEntries(ArtistFeedSource.ALL, gallerySegment(), pixivSegment(), 0L)
        val heads = entries.filterIsInstance<ArtistInboxEntry.SectionHead>()
        assertEquals("图库 3 张", heads[0].label)
        assertEquals("pixiv 3 篇", heads[1].label)
    }

    @Test
    fun `顶栏未读量词分源`() {
        assertEquals("未读 5 项", inboxUnreadLabel(ArtistFeedSource.ALL, 5))
        assertEquals("未读 3 张", inboxUnreadLabel(ArtistFeedSource.GALLERY, 3))
        assertEquals("未读 2 篇", inboxUnreadLabel(ArtistFeedSource.PIXIV, 2))
    }

    @Test
    fun `同步时刻时分格式与未同步兜底`() {
        // 09:05 本机时区 → 注入 now 保证格式判定稳定
        val zone = java.time.ZoneId.systemDefault()
        val at = java.time.LocalDateTime.of(2026, 11, 18, 9, 5).atZone(zone).toInstant().toEpochMilli()
        assertEquals("09:05 同步", inboxSyncLabel(at, nowMillis = at + 60_000L))
        assertEquals("尚未同步", inboxSyncLabel(0L, nowMillis = at))
        // 站方时刻晚于本机 → 按未同步处理，不摆未来的钟
        assertEquals("尚未同步", inboxSyncLabel(at + 10_000L, nowMillis = at))
    }

    @Test
    fun `分界线文案图库带日期pixiv不带`() {
        val zone = java.time.ZoneId.systemDefault()
        val at = java.time.LocalDateTime.of(2026, 11, 18, 20, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals("上次查看 · 11月18日", inboxDividerLabel(showDate = true, lastReadTimestamp = at))
        assertEquals("上次查看", inboxDividerLabel(showDate = false, lastReadTimestamp = at))
        // 从未查看过：图库侧也不摆日期
        assertEquals("上次查看", inboxDividerLabel(showDate = true, lastReadTimestamp = 0L))
        val entries = artistInboxEntries(ArtistFeedSource.ALL, gallerySegment(), pixivSegment(), at)
        val dividers = entries.filterIsInstance<ArtistInboxEntry.Divider>()
        assertEquals("上次查看 · 11月18日", dividers[0].label)
        assertEquals("上次查看", dividers[1].label)
    }

    @Test
    fun `单源视图只有分界线无段头`() {
        val galleryEntries = artistInboxEntries(ArtistFeedSource.GALLERY, gallerySegment(), pixivSegment(), 0L)
        assertEquals(0, galleryEntries.filterIsInstance<ArtistInboxEntry.SectionHead>().size)
        assertEquals(1, galleryEntries.filterIsInstance<ArtistInboxEntry.Divider>().size)
        val pixivEntries = artistInboxEntries(ArtistFeedSource.PIXIV, gallerySegment(), pixivSegment(), 0L)
        assertEquals(0, pixivEntries.filterIsInstance<ArtistInboxEntry.SectionHead>().size)
        assertEquals(1, pixivEntries.filterIsInstance<ArtistInboxEntry.Divider>().size)
    }

    @Test
    fun `新作在分界线上方旧作在下方`() {
        val entries = artistInboxEntries(ArtistFeedSource.ALL, gallerySegment(), pixivSegment(), 0L)
        val dividerIndex = entries.indexOfFirst { it is ArtistInboxEntry.Divider }
        val galleryNewIndexes = entries.mapIndexedNotNull { index, entry ->
            if (entry is ArtistInboxEntry.Card && (entry.model.sourcePost?.id ?: 0L) < 100L) index else null
        }
        val galleryOldIndexes = entries.mapIndexedNotNull { index, entry ->
            if (entry is ArtistInboxEntry.Card && (entry.model.sourcePost?.id ?: 0L) >= 100L) index else null
        }
        assertTrue(galleryNewIndexes.all { it < dividerIndex })
        assertTrue(galleryOldIndexes.all { it > dividerIndex })
    }

    @Test
    fun `落盘水位推进后新作旧作分侧正确`() {
        // 三张卡：id 100 已落水位、101 刚超水位、99 更旧
        val cards = listOf(
            cardItem(GallerySite.YANDERE, 101),
            cardItem(GallerySite.YANDERE, 100),
            cardItem(GallerySite.YANDERE, 99),
        )
        val (newCards, oldCards) = artistInboxSplitByWatermark(cards, watermark = 100L)
        assertEquals(listOf(101L), newCards.map { it.model.sourcePost?.id })
        assertEquals(listOf(100L, 99L), oldCards.map { it.model.sourcePost?.id })
        // 无出处的夹具卡不进新作区
        val fixture = ArtistInboxCardItem(DesktopWallCardModel(title = "x", url = "u", aspect = 1f, masked = false))
        val (newWithFixture, oldWithFixture) = artistInboxSplitByWatermark(listOf(fixture), 0L)
        assertEquals(0, newWithFixture.size)
        assertEquals(1, oldWithFixture.size)
    }

    @Test
    fun `空段整段省略`() {
        val entries = artistInboxEntries(ArtistFeedSource.ALL, gallery = null, pixiv = pixivSegment(), lastReadTimestamp = 0L)
        assertTrue(entries.filterIsInstance<ArtistInboxEntry.SectionHead>().none { it.source == ArtistFeedSource.GALLERY })
        assertEquals(1, entries.filterIsInstance<ArtistInboxEntry.Divider>().size)
        assertEquals(0, artistInboxEntries(ArtistFeedSource.ALL, null, null, 0L).size)
    }
}
