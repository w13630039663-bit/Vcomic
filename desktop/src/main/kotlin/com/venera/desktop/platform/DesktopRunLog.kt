package com.venera.desktop.platform

import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 包里那一串读数第一次**落在盘上**（B 档·交付形态第一颗）。
 *
 * ## 为什么必须先做这一格，而不是先改包名
 *
 * `desktop/build.gradle.kts` 里那句 `console = true` 自带一条注释写着「正式产物要改成写日志文件」，
 * 而它一直没做 —— 原因是**读数只有 stdout 这一条路**（全 `desktop/src/main` 里 `println` 193 处、
 * `D_*` 读数 292 处，`System.setOut` 零处）。所以那一枚布尔**不是能单独按的开关**：
 * 关掉它等于把 `desktop/build.gradle.kts:565` 自己预告的那句话当做法，
 * 而那句话说的是"先有日志文件，再去控制台"，次序反了就是把整族包内取证打哑 ——
 * 交付包里那三课（缺 `jdk.unsupported` 打死 GraalJS、缺 `java.sql` 打死读库、
 * 包里取不到 sqlite 原生库）**全都只有 stdout 那一条读数路**，判据与 dev 起窗一概抓不到。
 *
 * ## 落点为什么在数据根，而不是 `java.io.tmpdir`
 *
 * 批 90 量过同一族的形状：交付包里的日志**确实落过 `%TEMP%`**，而那一趟的判据是把
 * `LOCALAPPDATA` 指走，验"没往真人那份里写"——**只有图片缓存那颗因此搬家**（与数据根不同轨是病）；
 * 日志那份当时没人判它错，但它同样落在指走的 TEMP 里。把它收回**数据根**这一条轨：
 * 与库文件、词典、下载那几样同一个根，隔离实验时一起被隔离，用户按日期找得到。
 * ⚠️ 落点是 `DesktopPaths.dataRoot` 下面那一棵 `logs`，与库文件/缓存**同一个根**：
 * `DesktopPaths` 的类注释自己写着「唯一来源是 `%LOCALAPPDATA%\venera`」，而它的判据是
 * 「取不到 LOCALAPPDATA 就当场抛，绝不退回 java.io.tmpdir」（退回 tmp 等于把判据假通过，
 * 用户数据会在清临时目录时蒸发）。本颗**不另起第二种落点口径**：数据根从外面递进来，
 * 递不进（`null`）就念"没定下数据根"那一档，不去编一个默认目录 —— 编一个默认目录就是把
 * `DesktopPaths` 那条"不许退到别处"从另一扇门绕回去。
 *
 * ## 写失败不许静默，也不许把启动拖死
 *
 * 读数落不进盘 = 那一跑的证据没了；而为了一份日志让启动当场抛 = 把用户的窗换成一堆证据。
 * 两档都不接受，所以这里是**两态**（成了 / 落不进），而不是 `runCatching` 折成布尔 ——
 * 折成布尔就答不出"哪一步坏的"。⚠️ 这里**没有**"还没定下数据根"那一档：`DesktopPaths.create()`
 * 的判据是取不到 `LOCALAPPDATA` 就**启动即抛**，所以活路径里递进来的数据根恒不为空，
 * 给它加一档就是钉一条走不到的分支（与批 115 那把"门掉了一百多批而一条判据没红"的惰尺子是同一课的反面：
 * 判据也不许钉死档）。可测性靠**形参从外面递**保住，不靠多一档。
 */

/** 那串读数的落点目录名（住在数据根下，与 `downloads` / `cache` 同级）。 */
const val DESKTOP_RUN_LOG_DIR_NAME = "logs"

/** 一次启动一份，且名字里带 PID：上一轮被 `timeout` 杀掉的跑会留下文件，同名覆盖就是把两跑混成一份。 */
private val DESKTOP_RUN_LOG_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")

/**
 * 纯计算那颗：数据根 + 时刻 + PID → 那一串读数该落哪。
 *
 * 三样都从外面递进来而不是自取，为的是让判据能问得出「时刻换一分钟会不会换一份」与
 * 「两跑同名会不会互相覆盖」—— 而 `LocalDateTime.now()` 与 `ProcessHandle.current().pid()`
 * 在测试里造不出来。
 */
fun desktopRunLogFile(dataRoot: File, at: LocalDateTime, pid: Long): File =
    File(File(dataRoot, DESKTOP_RUN_LOG_DIR_NAME), "venera-$pid-${at.format(DESKTOP_RUN_LOG_STAMP)}.log")

/**
 * 装那两跳的果：成了交回那份流，落不进交回**成因本身**。
 *
 * ⚠️ 为什么不是布尔：调用点若只知"没成"，那句读数里的成因就只能现场编一句
 * （"建不出目录或开不了文件"）—— 那是把屏上一句实话写成猜的，与批 115 / §137 记过的
 * 「还没数回来塌成本机没有」同一族。所以成因由装那一趟**带出来**。
 */
sealed class DesktopRunLogInstall {
    /** 那棵目录建出来了且那份文件写得进。 */
    data class Opened(val stream: PrintStream) : DesktopRunLogInstall()

    /** 建目录或开文件流失败（包内进程被拒写的那一族，批 90 量过它的邻居）。 */
    data class Failed(val cause: String) : DesktopRunLogInstall()
}

/** 那一行读数（措辞只在这一处，源码尺钉「声明一处 + 消费一处」）。 */
fun desktopRunLogReading(install: DesktopRunLogInstall, path: String): String = when (install) {
    is DesktopRunLogInstall.Opened -> "D_日志 $path"
    is DesktopRunLogInstall.Failed ->
        "D_日志 落不进 $path（${install.cause}）—— 这一跑的读数只剩控制台那一份"
}

/**
 * 把 stdout/stderr 抄一份进那份文件，**原样两路都留**（控制台那份一字不动）。
 *
 * ⚠️ 两个细节是刻意的，别顺手"简化"：
 * - **UTF-8 写文件**：批 90 实测包内 stdout 是 GBK，每一句中文读数变成 U+FFFD，量具得靠
 *   一次编码自适应才读得出（`_qa/b89_check_frame_alignment.js` 那段）。盘上这一份直接锁 UTF-8，
 *   以后 grep 不用猜码页。
 * - **逐笔落盘靠的是下面构造那枚 `autoFlush = true`**：`exitProcess()` 不会冲刷流，取证那一跑
 *   靠 `--favcheck` 这类种子读完就退，盘上那一份若是缓冲的就会是空的 —— 而"文件在而里面没字"
 * 正是这一族最坏的假证据。⚠️ 扰动量到一件事：`override fun flush` 里那两句**不是**
 *   这件事的负责人（撤掉 `target.flush()` 判据照样绿，因为 target 自己就是 autoFlush 的流），
 *   它们只是 `System.out.flush()` 被显式调用时的双保险。口径住在**构造那枚布尔**上，
 *   注释若指着另一枚旋钮，下一批就会去改错的那一颗。
 * - **字节直写 original**：不走 `original.println`，否则中文Windows默认（GBK）下**再编一次**，
 *   两路都坏。original 收的是已经编好的字节，它自己不再编码。
 *
 * 没抄成时交回 [DesktopRunLogInstall.Failed] 而**不去动 `System.out`**：那一趟的证据仍在控制台，
 * 屏上也念得出为什么盘上没有 —— 静默换一路（把 stdout 换成一份空文件）比不落盘更坏。
 */
fun installDesktopRunLog(file: File): DesktopRunLogInstall {
    val opened = runCatching {
        file.parentFile?.mkdirs()
        PrintStream(FileOutputStream(file), true, StandardCharsets.UTF_8)
    }
    val target = opened.getOrNull()
        ?: return DesktopRunLogInstall.Failed(
            // 类名 + 消息：只有消息的话，"目录占位"与"权限拒绝"念成同一句（批 53 那条纪律）。
            opened.exceptionOrNull().let { e ->
                "${e?.javaClass?.simpleName ?: "未知"}: ${e?.message?.take(140) ?: "读不出"}"
            },
        )
    val original = System.out
    val tee = PrintStream(object : OutputStream() {
        override fun write(b: Int) {
            original.write(b)
            runCatching { target.write(b) }
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            original.write(b, off, len)
            runCatching { target.write(b, off, len) }
        }

        override fun flush() {
            // 两路都刷：只刷盘上那一份的话，`:desktop:app` 那一跑的控制台读数会推迟出现
            // （包内 console=false 时没人管，但开发跑与包内跑要同一条路 —— 批 90 定的那条纪律）。
            runCatching { original.flush() }
            runCatching { target.flush() }
        }

        override fun close() {
            // 只收盘上那一份：`System.out` 不归本颗管，关掉它等于把进程的 stdout 掐了。
            runCatching { target.close() }
        }
    }, true, StandardCharsets.UTF_8)
    System.setOut(tee)
    System.setErr(tee)
    return DesktopRunLogInstall.Opened(tee)
}
