package com.venera.desktop.gallery.data

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「本地漫画」那一域的**本级**读侧（数盘上那棵认裸目录的树）。
 *
 * ## 这一批评的是「看得见」那一半，口径跟着对岸 `LocalComicManager.getLocalComics`
 *
 * 对岸那一屏认的是**盘上所有攒下来的本子**：`<下载根>/<站>/<号>/<话目录>/` 里有图就认，题名走
 * 「目录名兜底 + `comic_info.json.title` 覆盖」，数页吃的是 `isPageImageFile`（扩展名 ∈
 * {jpg,jpeg,png,webp} 且非空）—— 而不是那把 `NNNN.jpg` + 魔数的 `desktopComicPageLooksWhole`。
 * 桌面那颗话级「本机已下载」（批 115）**要求每话带 `chapter.json` 清单**，于是安卓同机 / CBZ 导进来的裸目录
 * 在它那里一颗不认。本件补的就是那棵**本级**树。
 *
 * ## 为什么这是「照对岸」而不是「偷加第四把尺」
 *
 * 本仓那道「整页只有一把尺子」钉的是**桌面自己下的那棵树**（`已写/应写`），而 `DesktopComicPageWholenessTest`
 * 把它钉死成「声明 + 五处调用共六处」。本地那棵树的对岸读侧本就是**第二把尺**（`isPageImageFile`），
 * 批 97 的 KDoc 早记着「两端两颗读侧本就不同口径」。这里复用 `isPageImageFile` 不碰那颗魔数尺，
 * 所以既不改六处那一账，也不另写第四种「齐不齐」。
 */
class DesktopLocalComicLibraryTest {

    private fun tempRoot(): File = Files.createTempDirectory("b137local").toFile()

    /** 造一本：`根/<站>/<号>/<话名>/` 里写 [pages] 颗**算一页**的图桩（扩展名认得且非空）。 */
    private fun comicOnDisk(
        root: File, sourceKey: String, comicId: String, chapterDir: String = "ch1", pages: Int,
        comicInfoJson: String? = null, chapterInfoJson: String? = null,
    ): File {
        val chDir = File(File(File(root, sourceKey), comicId), chapterDir)
        chDir.mkdirs()
        for (index in 1..pages) File(chDir, "%03d.jpg".format(index)).writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1))
        if (comicInfoJson != null) File(File(File(root, sourceKey), comicId), "comic_info.json").writeText(comicInfoJson)
        if (chapterInfoJson != null) File(chDir, "chapter.json").writeText(chapterInfoJson)
        return chDir
    }

    /** ① 裸目录里真有图就认：一本一行，念得出题名的目录名兜底，页/话数从盘上数。 */
    @Test
    fun 裸目录里认得出本子而题名退目录名() {
        val root = tempRoot()
        try {
            comicOnDisk(root, "jm", "998877", pages = 3)
            val comics = desktopLocalComics(root)
            assertEquals("只认这一本", 1, comics.size)
            val c = comics.single()
            assertEquals("本级 id 是目录名", "998877", c.id)
            assertEquals("没有 comic_info.json 时题名退目录名", "998877", c.title)
            assertEquals("来源退上一级目录名", "jm", c.sourceKey)
            assertEquals("一话", 1, c.chapterCount)
            assertEquals("三页", 3, c.totalPages)
        } finally { root.deleteRecursively() }
    }

    /** ② `comic_info.json.title` 覆盖目录名（本级域的招牌就是念得出真题名）。 */
    @Test
    fun comic_info的title覆盖目录名() {
        val root = tempRoot()
        try {
            comicOnDisk(root, "jm", "111", pages = 2,
                comicInfoJson = "{\"title\":\"某本真名\",\"sourceKey\":\"weibo\"}")
            val c = desktopLocalComics(root).single()
            assertEquals("题名走 comic_info.json", "某本真名", c.title)
            assertEquals("sourceKey 也走 comic_info.json", "weibo", c.sourceKey)
        } finally { root.deleteRecursively() }
    }

    /** ③ `comic_info.json` 只有 sourceKey 而无 title 时：sourceKey 认下、题名仍退目录名（两格独立兜底）。 */
    @Test
    fun 缺title时sourceKey仍独立认下而题名退目录名() {
        val root = tempRoot()
        try {
            comicOnDisk(root, "jm", "222", pages = 1,
                comicInfoJson = "{\"sourceKey\":\"exh\"}")
            val c = desktopLocalComics(root).single()
            assertEquals("没 title ⇒ 题名退目录名", "222", c.title)
            assertEquals("有 sourceKey ⇒ 独立认下，不被一起退掉", "exh", c.sourceKey)
        } finally { root.deleteRecursively() }
    }

    /** ④ 不认「只有一张裸图」：图必须落在话目录里，`<号>` 那层直接散放的裸图不算一话。 */
    @Test
    fun 散放在本级目录的裸图不算一话() {
        val root = tempRoot()
        try {
            val comicDir = File(File(root, "jm"), "333")
            comicDir.mkdirs()
            // 一本没有任何子目录里有图，只在本级目录直接放两张图 —— 对岸也是先取 chapterDirs，所以不认。
            File(comicDir, "cover.jpg").writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1))
            assertEquals("本级散图不构成本子", 0, desktopLocalComics(root).size)
        } finally { root.deleteRecursively() }
    }

    /** ⑤ 话级题名走 `chapter.json.title` 覆盖目录名（与本级题名同一条兜底口径）。 */
    @Test
    fun 话级题名走chapter_json的title() {
        val root = tempRoot()
        try {
            val ch = comicOnDisk(root, "jm", "444", chapterDir = "0001_x", pages = 2,
                chapterInfoJson = "{\"title\":\"第1話\"}")
            val chapters = desktopLocalComicChapters(ch.parentFile!!)
            assertEquals("一话", 1, chapters.size)
            assertEquals("话名走清单", "第1話", chapters.single().title)
        } finally { root.deleteRecursively() }
    }

    /** ⑥ 0 字节的桩**不算一页**（`isPageImageFile` 要非空），全 0 页的那一话不进表。 */
    @Test
    fun 空文件不算一页而全空的那话不进表() {
        val root = tempRoot()
        try {
            val ch = comicOnDisk(root, "jm", "555", pages = 0)
            // pages=0 时 comicOnDisk 一颗都不写；那一话目录里没有图 ⇒ 不认。
            assertEquals("没图的一话不数", 0, desktopLocalComicChapters(ch.parentFile!!).size)
            assertEquals("整本不认", 0, desktopLocalComics(root).size)
        } finally { root.deleteRecursively() }
    }

    /** ⑦ 扩展名白名单：`.gif` 不算一页（与对岸同一把尺，刻意没扩名单）。 */
    @Test
    fun gif那一档不算一页() {
        val root = tempRoot()
        try {
            val ch = File(File(File(root, "jm"), "666"), "ch1")
            ch.mkdirs()
            File(ch, "001.gif").writeBytes(byteArrayOf(0x47, 0x49, 0x46))
            File(ch, "002.png").writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))
            val chapters = desktopLocalComicChapters(ch.parentFile!!)
            assertEquals("只认那张 png，gif 不数", 1, chapters.single().pageCount)
        } finally { root.deleteRecursively() }
    }

    /** ⑧ 排序按 updatedAt 降序（对岸 `sortedByDescending`）。 */
    @Test
    fun 按最后修改时间降序排() {
        val root = tempRoot()
        try {
            comicOnDisk(root, "jm", "old", pages = 1)
            comicOnDisk(root, "jm", "new", pages = 1)
            File(File(root, "jm"), "old").setLastModified(1_000L)
            File(File(root, "jm"), "new").setLastModified(2_000L)
            val ids = desktopLocalComics(root).map { it.id }
            assertEquals("新的在前", listOf("new", "old"), ids)
        } finally { root.deleteRecursively() }
    }

    /** ⑨ 根不存在 → 空表（不是抛）。 */
    @Test
    fun 根不在时交回空表而不是抛() {
        assertEquals(0, desktopLocalComics(File("Z:/no/such/tree/at/all")).size)
    }

    /** ⑩ 表头那句四档各一句（error / 根不在 / 空 / 有货），出自同一次扫描。 */
    @Test
    fun 表头四档各念各的话() {
        val err = DesktopLocalComicLibraryScan(emptyList(), rootExists = true, error = "Boom")
        assertTrue("塌了要念数不出来+成因：" + desktopLocalComicLibraryHeader(err),
            desktopLocalComicLibraryHeader(err).startsWith("数不出来：Boom"))
        val noRoot = DesktopLocalComicLibraryScan(emptyList(), rootExists = false, error = null)
        assertTrue(desktopLocalComicLibraryHeader(noRoot).contains("还没有本机漫画那一棵树"))
        val empty = DesktopLocalComicLibraryScan(emptyList(), rootExists = true, error = null)
        assertTrue(desktopLocalComicLibraryHeader(empty).contains("本机还没有认得出的本地漫画"))
        val c = DesktopLocalComic("id", "题", "jm", chapterCount = 2, totalPages = 5, "/x", 0)
        val full = DesktopLocalComicLibraryScan(listOf(c), rootExists = true, error = null)
        assertTrue(desktopLocalComicLibraryHeader(full).contains("本地漫画 1 本 · 共 2 话 5 页"))
    }

    /** ⑪ 还没数回来那一档念另一句，不改口成「本机没有」。 */
    @Test
    fun 还没数回来那一句不改口成没有() {
        assertTrue(desktopLocalComicLibraryPendingLine().contains("正在数"))
    }

    /** ⑫ 行内那句念的是题名（本级域卖点），不是号。 */
    @Test
    fun 行内那句念题名而号只进id不进句() {
        val c = DesktopLocalComic("sid", "真题名", "jm", 3, 9, "/abs", 0)
        val line = desktopLocalComicLibraryRowLine(c)
        assertTrue("念得出题名：" + line, line.contains("真题名"))
        assertTrue("带话/页/源：" + line, line.contains("3 话 9 页") && line.contains("源 jm"))
    }

    /** ⑬ 取证读数那一句：本/话/页/根/塌五格，塌了带成因而不扮 0。 */
    @Test
    fun 取证读数分得出塌了与确实空() {
        val ok = DesktopLocalComicLibraryScan(
            listOf(DesktopLocalComic("a", "甲", "jm", 2, 7, "/a", 0)), rootExists = true, error = null,
        )
        assertEquals("D_本地漫画 本=1 话=2 页=7 根=1", desktopLocalComicLibraryReading(ok))
        val broke = DesktopLocalComicLibraryScan(emptyList(), rootExists = true, error = "IO")
        assertTrue(desktopLocalComicLibraryReading(broke).endsWith("塌=IO"))
    }
}
