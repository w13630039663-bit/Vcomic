package com.venera.desktop.gallery.data

import com.venera.compose.gallery.data.PixivCollectionsParse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopPixivCollectionsParseTest {

    @Test
    fun embedThumbnailUrlAppendsFormatPngWhenMissing() {
        val body = """
            {
              "error": false,
              "body": {
                "page": {
                  "recommendCollectionIds": ["83110198961523844750", "61930885660774662563"],
                  "everyoneCollectionIds": [],
                  "tagRecommendCollectionIds": []
                },
                "thumbnails": {
                  "collection": [
                    {
                      "id": "83110198961523844750",
                      "userId": "12345",
                      "userName": "tester",
                      "title": "c1",
                      "tags": [],
                      "caption": "",
                      "thumbnailImageUrl": "https://embed.pixiv.net/next/collection/83110198961523844750/8bc49c144791f07b/2/288x288/thumbnail",
                      "bookmarkCount": 1
                    },
                    {
                      "id": "61930885660774662563",
                      "userId": "12345",
                      "userName": "tester",
                      "title": "c2",
                      "tags": [],
                      "caption": "",
                      "thumbnailImageUrl": "https://embed.pixiv.net/next/collection/61930885660774662563/69dda4537b21cade/2/288x288/thumbnail?format=png",
                      "bookmarkCount": 2
                    }
                  ]
                }
              }
            }
        """.trimIndent()

        val top = PixivCollectionsParse.top(body)
        assertEquals(2, top.recommend.size)
        assertEquals(
            "https://embed.pixiv.net/next/collection/83110198961523844750/8bc49c144791f07b/2/288x288/thumbnail?format=png",
            top.recommend[0].thumbnailImageUrl,
        )
        assertEquals(
            listOf("https://embed.pixiv.net/next/collection/83110198961523844750/8bc49c144791f07b/2/288x288/thumbnail?format=png"),
            top.recommend[0].coverUrls,
        )
        assertEquals(
            "https://embed.pixiv.net/next/collection/61930885660774662563/69dda4537b21cade/2/288x288/thumbnail?format=png",
            top.recommend[1].thumbnailImageUrl,
        )
    }

    @Test
    fun userBookmarksEmptyArrayReturnsEmptyPageInsteadOfThrowing() {
        val body = """
            {
              "error": false,
              "body": {
                "collections": []
              }
            }
        """.trimIndent()

        val page = PixivCollectionsParse.userBookmarks(body, offset = 0)
        assertTrue(page.cards.isEmpty())
        assertEquals(0, page.total)
        assertNull(page.nextOffset)
    }

    @Test
    fun detailParsesWorkUrlsProfileAvatarAndCreatorCollections() {
        val body = """
            {
              "error": false,
              "body": {
                "data": {
                  "detail": {
                    "tags": ["tag1"],
                    "tiles": [
                      {
                        "id": "t1",
                        "type": "Work",
                        "workId": "145332609",
                        "workType": "illust",
                        "layout": {
                          "position": { "x": 0, "y": 0 },
                          "size": { "x": 6, "y": 2 }
                        }
                      }
                    ]
                  },
                  "userCollections": {
                    "83110198961523844750": {
                      "id": "83110198961523844750",
                      "userId": "98653832",
                      "userName": "creator",
                      "title": "self",
                      "tags": [],
                      "caption": "",
                      "thumbnailImageUrl": "https://embed.pixiv.net/next/collection/83110198961523844750/abc/2/288x288/thumbnail",
                      "bookmarkCount": 1
                    },
                    "99999999999999999999": {
                      "id": "99999999999999999999",
                      "userId": "98653832",
                      "userName": "creator",
                      "title": "other",
                      "tags": [],
                      "caption": "",
                      "thumbnailImageUrl": "https://embed.pixiv.net/next/collection/99999999999999999999/def/2/288x288/thumbnail",
                      "bookmarkCount": 3
                    }
                  }
                },
                "thumbnails": {
                  "collection": [
                    {
                      "id": "83110198961523844750",
                      "userId": "98653832",
                      "userName": "creator",
                      "title": "self",
                      "tags": [],
                      "caption": "desc",
                      "thumbnailImageUrl": "https://embed.pixiv.net/next/collection/83110198961523844750/abc/2/288x288/thumbnail",
                      "bookmarkCount": 1
                    }
                  ],
                  "illust": [
                    {
                      "id": "145332609",
                      "title": "work title",
                      "url": "https://i.pximg.net/c/250x250_80_a2/img-master/145332609_square1200.jpg",
                      "userId": "98653832",
                      "userName": "artist",
                      "width": 1844,
                      "height": 1949,
                      "pageCount": 2,
                      "profileImageUrl": "https://i.pximg.net/user-profile/img/avatar_50.jpg",
                      "urls": {
                        "540x540": "https://i.pximg.net/c/540x540_70/img-master/145332609_square1200.jpg",
                        "1200x1200": "https://i.pximg.net/img-master/145332609_master1200.jpg"
                      }
                    }
                  ]
                }
              }
            }
        """.trimIndent()

        val detail = PixivCollectionsParse.detail("83110198961523844750", body)
        assertEquals(1, detail.works.size)
        val work = detail.works[0]
        assertEquals("https://i.pximg.net/c/540x540_70/img-master/145332609_square1200.jpg", work.mediumUrl)
        assertEquals("https://i.pximg.net/img-master/145332609_master1200.jpg", work.largeUrl)
        assertEquals("https://i.pximg.net/user-profile/img/avatar_50.jpg", work.profileImageUrl)
        assertEquals(1844, work.width)
        assertEquals(1949, work.height)
        assertEquals(1, detail.creatorCollections.size)
        assertEquals("99999999999999999999", detail.creatorCollections[0].id)
        assertEquals(
            "https://embed.pixiv.net/next/collection/99999999999999999999/def/2/288x288/thumbnail?format=png",
            detail.creatorCollections[0].thumbnailImageUrl,
        )
    }
}
