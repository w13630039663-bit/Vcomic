package com.venera.desktop

import com.venera.desktop.gallery.data.desktopComicAtomicWriteTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 备份落盘的替换语义：新档没就位之前，旧档必须一直在。
 */
class DesktopBackupAtomicWriteTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `替换已存在的备份后不留临时档`() {
        val target = tmp.newFile("backup.json")
        target.writeText("旧内容", Charsets.UTF_8)

        desktopComicAtomicWriteTo(target, "新内容".toByteArray(Charsets.UTF_8))

        assertEquals("新内容", target.readText(Charsets.UTF_8))
        val leftovers = target.parentFile.listFiles { file -> file.name.endsWith(".tmp") }.orEmpty()
        assertTrue("临时档必须清理干净：${leftovers.map { it.name }}", leftovers.isEmpty())
    }

    @Test
    fun `替换失败时旧备份原样保留`() {
        val target = tmp.newFile("backup.json")
        target.writeText("旧内容", Charsets.UTF_8)
        // 只读档挡住覆盖改名，等价于"新档写不进去"这一档。
        assumeTrue("本机不支持把文件置为只读", target.setWritable(false))
        try {
            val failed = runCatching {
                desktopComicAtomicWriteTo(target, "新内容".toByteArray(Charsets.UTF_8))
            }
            assertTrue("覆盖失败必须抛出来，不能静默丢旧档", failed.isFailure)
            assertEquals("旧备份必须原样保留", "旧内容", target.readText(Charsets.UTF_8))
        } finally {
            target.setWritable(true)
        }
    }
}
