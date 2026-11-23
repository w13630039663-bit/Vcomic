package com.venera.desktop.platform

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「那一串读数落哪一份文件」的值尺。
 *
 * 五颗件全是纯计算或真临时目录，**不需要窗口也不需要网络**。这里最要紧的一格是
 * ③④⑤ 那三条只问盘上与流上**真有什么**，而不问"函数返回了个对象" ——
 * 本仓记过一整族"打印了路径而盘上没那颗文件"（批 90 为它专门加过盘上数文件那一验），
 * 所以那三条各自由跑一趟再**从盘上读回来**。
 */
class DesktopRunLogTest {

    private fun tempRoot(): File = File(System.getProperty("java.io.tmpdir"), "b139-runlog-${System.nanoTime()}")

    /** ① 名字里同时带 pid 与时刻：两跑同名互相覆盖就是把两份证据折成一份。 */
    @Test fun `同一分钟的两跑各占一份而互不覆盖`() {
        val root = tempRoot()
        val at = LocalDateTime.of(2026, 10, 25, 14, 55, 0)
        val a = desktopRunLogFile(root, at, 21784)
        val b = desktopRunLogFile(root, at, 90210)
        // 按**值**比：assertNotSame 比的是引用，两颗不同 File 的 path 恒不相似 ⇒ 那条判式恒真。
        assertNotEquals(a.path, b.path)
        assertTrue("落点必须在数据根下的 logs：${a.path}", a.path.contains(File.separator + "logs" + File.separator))
        assertTrue("名字里要点名 pid：${a.name}", a.name.contains("21784"))
        // 时刻精确到秒：同一分钟的两跑也必须各占一份（"换一分钟换一份"由 ② 独立验）。
        assertTrue("名字里要点名到秒的时刻：${a.name}", a.name.contains("2026-10-25-145500"))
    }

    /** ② 换一分钟换一份（时间戳在名字里而不是"同一颗文件往后追加"）。 */
    @Test fun `换一分钟就是换一份`() {
        val root = tempRoot()
        val early = desktopRunLogFile(root, LocalDateTime.of(2026, 10, 25, 14, 55, 0), 1)
        val late = desktopRunLogFile(root, LocalDateTime.of(2026, 10, 25, 14, 56, 0), 1)
        assertNotEquals(early.path, late.path)
    }

    /** ③ 装好之后那句 println **不关流也要在盘上**（`exitProcess` 不冲刷流，取证那一跑读完就退）。 */
    @Test fun `那句读数不关流也已经落在盘上`() {
        val root = tempRoot()
        val originalOut = System.out
        val originalErr = System.err
        try {
            val file = desktopRunLogFile(root, LocalDateTime.now(), 4242)
            val install = installDesktopRunLog(file)
            assertTrue("装不上那份文件：$install", install is DesktopRunLogInstall.Opened)
            println(desktopRunLogReading(install, file.path))
            println("D_收藏 命中=2 明细=取证那一跑")
            // 故意不 close()：读盘验的就是"逐笔 flush"这件事本身。
            val onDisk = file.readText(StandardCharsets.UTF_8)
            assertTrue("盘上那一份没有那句读数（= 逐笔 flush 没做）：$onDisk", onDisk.contains("D_收藏 命中=2"))
            assertTrue("装好那句读数也要在盘上：$onDisk", onDisk.contains("D_日志 ${file.path}"))
        } finally {
            System.setOut(originalOut)
            System.setErr(originalErr)
            root.deleteRecursively()
        }
    }

    /** ④ 盘上那一份是 UTF-8：中文读数不许变成品字（批 90 实测包内 stdout 就是 GBK，每句都碎）。 */
    @Test fun `中文读数在盘上还是原样`() {
        val root = tempRoot()
        val originalOut = System.out
        val originalErr = System.err
        try {
            val file = desktopRunLogFile(root, LocalDateTime.now(), 4243)
            val install = installDesktopRunLog(file)
            assertTrue("装不上那份文件：$install", install is DesktopRunLogInstall.Opened)
            println("D_数据目录 写入回读已验 打包=true")
            val bytes = file.readBytes()
            val asUtf8 = String(bytes, StandardCharsets.UTF_8)
            assertTrue("UTF-8 读不回原样：$asUtf8", asUtf8.contains("写入回读已验"))
            // 用显式转义而不是裸字符：U+FFFD 在编辑链路里会被吃掉，吃掉之后判式恒真（本仓那条"红得没有信息量"）。
            assertTrue("盘上出现替换符（= 编码在某一层被换过）：$asUtf8", !asUtf8.contains('\uFFFD'))
        } finally {
            System.setOut(originalOut)
            System.setErr(originalErr)
            root.deleteRecursively()
        }
    }

    /** ⑤ 两路都留：控制台那一份一字不动（这一颗是**抄**一份，不是换一路）。 */
    @Test fun `控制台那一份也收到同一句`() {
        val root = tempRoot()
        val originalOut = System.out
        val originalErr = System.err
        try {
            // 先把 stdout 换成一份内存缓冲，再装 tee ⇒ 缓冲里有的那一份就是"控制台那一路"。
            val console = ByteArrayOutputStream()
            val fakeConsole = PrintStream(console, true, StandardCharsets.UTF_8)
            System.setOut(fakeConsole)
            val file = desktopRunLogFile(root, LocalDateTime.now(), 4244)
            val install = installDesktopRunLog(file)
            assertTrue("装不上那份文件：$install", install is DesktopRunLogInstall.Opened)
            println("D_取证参数 --favcheck 按开处理")
            fakeConsole.flush()
            val consoleText = String(console.toByteArray(), StandardCharsets.UTF_8)
            val diskText = file.readText(StandardCharsets.UTF_8)
            assertTrue("控制台那一路丢了这句：$consoleText", consoleText.contains("--favcheck"))
            assertTrue("盘上那一路丢了这句：$diskText", diskText.contains("--favcheck"))
        } finally {
            System.setOut(originalOut)
            System.setErr(originalErr)
            root.deleteRecursively()
        }
    }

    /** ⑥ 落不进那一档：成因由装那一趟**带出来**，而 `System.out` 一字未动。 */
    @Test fun `落不进时成因是真读出来的`() {
        val tmp = tempRoot()
        tmp.mkdirs()
        // 真实手段触发"开不了那份文件"：把父目录让给一颗普通文件（与 DesktopPathsTest 同一手法）。
        val blocking = File(tmp, "not-a-directory")
        blocking.writeText("x")
        val before = System.out
        val install = installDesktopRunLog(File(blocking, "venera-1-2026-10-25-000000.log"))
        assertTrue("这一档该读成落不进：$install", install is DesktopRunLogInstall.Failed)
        val cause = (install as DesktopRunLogInstall.Failed).cause
        assertTrue("成因里要点名异常类型而不是一句空话：$cause", cause.contains("Exception"))
        assertTrue("成因不许塌成猜的那一句：$cause", !cause.contains("建不出"))
        // 没抄成 ⇒ 那一趟的证据仍在控制台：stdout 必须还是同一枚对象。
        assertSame("装失败也把 stdout 换掉了：控制台那一路会被静默换成一份空文件", before, System.out)
        tmp.deleteRecursively()
    }

    /** ⑦ 两句不合流：成功那句不许念"落不进"，失败那句不许把路径念成证据。 */
    @Test fun `成功与落不进那两句各念各的`() {
        val opened = desktopRunLogReading(
            DesktopRunLogInstall.Opened(PrintStream(ByteArrayOutputStream())),
            "C:\\x\\venera-1.log",
        )
        val failed = desktopRunLogReading(DesktopRunLogInstall.Failed("AccessDeniedException: 拒绝访问"), "C:\\x\\venera-1.log")
        assertEquals("D_日志 C:\\x\\venera-1.log", opened)
        assertTrue("失败那句要念得出成因：$failed", failed.contains("AccessDeniedException"))
        assertTrue("失败那句要点名落不进：$failed", failed.contains("落不进"))
        assertTrue("成功那句不许带出失败的字：$opened", !opened.contains("落不进"))
    }
}
