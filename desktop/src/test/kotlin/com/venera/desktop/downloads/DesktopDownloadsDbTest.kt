package com.venera.desktop.downloads

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DesktopDownloadsDbTest {
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("desktop-dl-db-test").toFile()
        DesktopDownloadsDb.installFile(File(tempDir, "desktop_downloads.db"))
    }

    @After
    fun tearDown() {
        DesktopDownloadsDb.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun `漫画下载台账写入读取与删除不依赖扫盘`() {
        val customChapterDir = File(tempDir, "custom_manga/jm/100/000_ch1").apply { mkdirs() }
        DesktopDownloadsDb.recordComicChapter(
            sourceKey = "jm",
            comicId = "100",
            comicTitle = "测试漫画",
            coverUrl = "https://example.com/cover.jpg",
            chapterId = "ch-1",
            chapterOrder = 0,
            chapterTitle = "第 1 话",
            expectedPages = 10,
            writtenPages = 10,
            bytes = 2048L,
            complete = true,
            sourceUrl = "jm://100",
            localDir = customChapterDir.absolutePath,
            downloadedAt = 1000L,
        )

        val listing = DesktopDownloadsDb.listComicListing()
        assertEquals(null, listing.error)
        assertEquals(1, listing.groups.size)
        assertEquals("测试漫画", DesktopDownloadsDb.comicTitleOf("jm", "100"))
        val ch = listing.groups.first().chapters.single()
        assertEquals(customChapterDir.absolutePath, DesktopDownloadsDb.comicChapterDirOf("jm", "100", "ch-1")?.absolutePath)
        assertEquals("测试漫画", ch.comicTitle)
        assertEquals("https://example.com/cover.jpg", ch.coverUrl)
        assertEquals(customChapterDir.absolutePath, ch.localDir)
        assertTrue(ch.complete)

        // 删除目录后仍可从台账清除记录
        customChapterDir.deleteRecursively()
        DesktopDownloadsDb.deleteComicChapter("jm", "100", "ch-1")
        assertTrue(DesktopDownloadsDb.listComicListing().groups.isEmpty())
    }

    @Test
    fun `图片下载台账支持跨目录写入Pixiv聚合与删除`() {
        val dirA = File(tempDir, "dirA").apply { mkdirs() }
        val dirB = File(tempDir, "dirB").apply { mkdirs() }
        DesktopDownloadsDb.recordImage(
            site = "pixiv",
            postId = "999",
            title = "画师A",
            fileName = "pixiv_999_p0.jpg",
            bytes = 1000L,
            dimensions = "800×1200",
            sourceUrl = "https://i.pximg.net/999_p0.jpg",
            localPath = File(dirA, "pixiv_999_p0.jpg").absolutePath,
            downloadedAt = 100L,
        )
        DesktopDownloadsDb.recordImage(
            site = "pixiv",
            postId = "999",
            title = "画师A",
            fileName = "pixiv_999_p1.jpg",
            bytes = 1500L,
            dimensions = "800×1200",
            sourceUrl = "https://i.pximg.net/999_p1.jpg",
            localPath = File(dirA, "pixiv_999_p1.jpg").absolutePath,
            downloadedAt = 200L,
        )
        DesktopDownloadsDb.recordImage(
            site = "yande",
            postId = "555",
            title = "图库图",
            fileName = "yande_555.png",
            bytes = 3000L,
            dimensions = "1920×1080",
            sourceUrl = "https://yande.re/555.png",
            localPath = File(dirB, "yande_555.png").absolutePath,
            downloadedAt = 300L,
        )

        val items = DesktopDownloadsDb.listImageItems()
        assertEquals(2, items.size)
        assertEquals("yande_555.png", items[0].fileName)
        assertEquals(2, items[1].pageCount)
        assertEquals(2500L, items[1].bytes)

        DesktopDownloadsDb.deleteImagesByPaths(items[1].allPaths)
        assertEquals(1, DesktopDownloadsDb.listImageItems().size)
    }

    @Test
    fun `本地文件夹原地关联支持单话多话写入查询与安全解除关联`() {
        val singleFolder = File(tempDir, "单话测试本").apply { mkdirs() }
        File(singleFolder, "001.jpg").writeBytes(byteArrayOf(1, 2, 3))
        File(singleFolder, "002.png").writeBytes(byteArrayOf(4, 5, 6))

        val multiFolder = File(tempDir, "多话测试本").apply { mkdirs() }
        File(multiFolder, "第1话").apply { mkdirs() }.also { ch ->
            File(ch, "p1.jpg").writeBytes(byteArrayOf(1, 2))
        }
        File(multiFolder, "第2话").apply { mkdirs() }.also { ch ->
            File(ch, "p1.webp").writeBytes(byteArrayOf(3, 4))
            File(ch, "p2.webp").writeBytes(byteArrayOf(5, 6))
        }

        val r1 = com.venera.desktop.gallery.data.desktopLinkLocalComicFolder(singleFolder)
        val r2 = com.venera.desktop.gallery.data.desktopLinkLocalComicFolder(multiFolder)
        assertEquals(null, r1.error)
        assertEquals(2, r1.pages)
        assertEquals(null, r2.error)
        assertEquals(3, r2.pages)

        val imported = DesktopDownloadsDb.listImportedComics()
        assertEquals(2, imported.size)
        assertTrue(imported.all { it.isLinked })
        val multi = imported.first { it.title == "多话测试本" }
        assertEquals(2, multi.chapterCount)
        assertEquals(3, multi.totalPages)

        // 移除关联只删除台账记录，不删除硬盘原文件夹
        DesktopDownloadsDb.deleteImportedComic(singleFolder.canonicalPath)
        assertTrue(singleFolder.isDirectory)
        assertEquals(1, DesktopDownloadsDb.listImportedComics().size)
    }
}
