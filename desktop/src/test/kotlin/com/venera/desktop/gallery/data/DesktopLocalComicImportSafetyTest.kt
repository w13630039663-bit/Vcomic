package com.venera.desktop.gallery.data

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 上线前全检 BUG-01 的回归尺：CBZ 导入题名净化与失败删除护栏。
 *
 * 旧实现把 `...zip` 的 `nameWithoutExtension`（= `..`）原样拼进 `File(imported, title)`，
 * 那个 File 会回指**下载根本身**；导入失败时 `targetDir.deleteRecursively()` 会把
 * 全部已下载漫画一起删掉。这把尺钉住两件：题名不许穿出去、失败清理不许动根。
 */
class DesktopLocalComicImportSafetyTest {

    private fun tempRoot(): File = Files.createTempDirectory("b145import").toFile()

    /** 造一颗只装文本（没有页图）的坏归档：导入必走「没有认得出的页图」那一支失败。 */
    private fun badArchive(file: File) {
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("readme.txt"))
            zip.write("not a comic".toByteArray())
            zip.closeEntry()
        }
    }

    @Test
    fun `点点题名不回指下载根`() {
        assertEquals("_", desktopLocalComicImportSafeTitle(".."))
        assertEquals("_", desktopLocalComicImportSafeTitle("..."))
        assertEquals("未命名", desktopLocalComicImportSafeTitle("."))
        assertFalse("any/.. 不许留成路径分隔符", desktopLocalComicImportSafeTitle("a/../b").contains("/"))
    }

    @Test
    fun `坏归档导入失败后下载根不被删`() {
        val root = tempRoot()
        // 先在下载根下摆一本「已下载」的本子，模拟用户已有的数据。
        val existing = File(root, "copy_manga/comic-1/0001_全一话/0001.jpg")
        existing.parentFile!!.mkdirs()
        existing.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1))

        val zip = File(root, "...zip") // nameWithoutExtension = ".."
        badArchive(zip)
        val outcome = desktopLocalComicImport(zip, root)

        assertTrue("坏归档必须判失败：${outcome.error}", outcome.error != null)
        assertTrue("下载根本体还在", root.isDirectory)
        assertTrue("已有的已下载本子不能被删", existing.isFile)
        // 净化后的落点是 imported/_ 之类，绝不能是下载根自己。
        val imported = File(root, "imported")
        if (imported.exists()) {
            assertTrue("imported 下不该留半截目录", imported.listFiles()?.isEmpty() != false)
        }
    }

    @Test
    fun `正常题名不受影响`() {
        assertEquals("我的漫画", desktopLocalComicImportSafeTitle("我的漫画"))
        assertEquals("a_b", desktopLocalComicImportSafeTitle("a/b"))
    }
}
