package com.venera.desktop.platform

import java.io.File

/**
 * Registers the Pixiv callback scheme in the current Windows user's registry.
 *
 * This mirrors PixEz desktop: the browser returns to `pixiv://account...`, Windows starts the app
 * with that URI, and [DesktopSingleInstance] forwards it to the running window. No administrator
 * rights are required because the key lives under `HKCU\Software\Classes`.
 */
internal object DesktopProtocolRegistrar {

    private const val MAIN_CLASS = "com.venera.desktop.VeneraDesktopKt"

    /** 停用哨兵文件名（设置中枢「桌面」区的开关写侧；数据根下）。 */
    const val DISABLED_SENTINEL = "venera-protocols.disabled"

    fun registerIfNeeded(dataRoot: File) {
        if (!System.getProperty("os.name").orEmpty().contains("win", ignoreCase = true)) return
        // 停用开关（档三）：哨兵文件在 ⇒ 本次启动不注册。用文件当闸是因为
        // 这里只拿得到 dataRoot:File，没有 PathProvider（写侧在设置中枢「桌面」区）。
        if (File(dataRoot, DISABLED_SENTINEL).isFile) {
            println("D_pixiv 协议回调注册已停用（设置中枢「桌面」区可重新开启）")
            return
        }
        val command = resolveLaunchCommand(dataRoot)
        if (command == null) {
            println("D_pixiv 协议回调未注册：拿不到当前启动命令")
            return
        }
        val regFile = File(dataRoot, "venera-protocols.reg")
        runCatching {
            regFile.writeText(protocolRegFile(listOf("pixiv", "venera"), command), Charsets.UTF_8)
            importRegFile(regFile)
        }.onFailure {
            println("D_pixiv 协议回调注册失败 ${it.javaClass.simpleName}: ${it.message.orEmpty()}")
            com.venera.desktop.DesktopErrorBus.report(
                category = "系统",
                headline = "Pixiv 登录回调注册失败",
                humanReason = "系统 URL 协议注册受限，浏览器登录授权后可能无法自动跳回本软件。",
                detail = "${it.javaClass.simpleName}: ${it.message.orEmpty()}",
                dedupKey = "protocol:pixiv_reg_fail",
            )
        }
    }

    private fun resolveLaunchCommand(dataRoot: File): String? {
        val jpackage = System.getProperty("jpackage.app-path")?.trim()?.takeIf { it.isNotBlank() }
        if (jpackage != null && File(jpackage).isFile) {
            return "\"$jpackage\" \"%1\""
        }

        val processCommand = runCatching {
            ProcessHandle.current().info().command().orElse(null)
        }.getOrNull()?.trim()
        if (!processCommand.isNullOrBlank() &&
            !processCommand.endsWith("java.exe", ignoreCase = true) &&
            !processCommand.endsWith("javaw.exe", ignoreCase = true) &&
            File(processCommand).isFile
        ) {
            return "\"$processCommand\" \"%1\""
        }

        if (processCommand.isNullOrBlank()) return null
        val javaExe = processCommand
        val javaw = File(javaExe).resolveSibling("javaw.exe").takeIf { it.isFile } ?: File(javaExe)
        val classPath = System.getProperty("java.class.path")?.takeIf { it.isNotBlank() } ?: return null
        val argsFile = File(dataRoot, "pixiv-callback-java.args")
        val argsFileText = buildString {
            appendLine("-Dfile.encoding=UTF-8")
            appendLine("-Dstdout.encoding=UTF-8")
            appendLine("-Dstderr.encoding=UTF-8")
            appendLine("-cp")
            appendLine(quoteArgFileValue(classPath))
            appendLine(MAIN_CLASS)
        }
        runCatching { argsFile.writeText(argsFileText, Charsets.UTF_8) }.getOrElse { return null }
        return "\"${javaw.absolutePath}\" @\"${argsFile.absolutePath}\" \"%1\""
    }

    private fun protocolRegFile(schemes: List<String>, command: String): String = buildString {
        appendLine("Windows Registry Editor Version 5.00")
        appendLine()
        schemes.forEach { scheme ->
            appendLine("[HKEY_CURRENT_USER\\Software\\Classes\\$scheme]")
            appendLine("@=${regString("URL:Venera $scheme Protocol")}")
            appendLine("\"URL Protocol\"=\"1\"")
            appendLine()
            appendLine("[HKEY_CURRENT_USER\\Software\\Classes\\$scheme\\shell\\open\\command]")
            appendLine("@=${regString(command)}")
            appendLine()
        }
    }

    private fun importRegFile(file: File) {
        val process = ProcessBuilder("reg.exe", "import", file.absolutePath)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader(Charsets.UTF_8).readText()
        val exit = process.waitFor()
        check(exit == 0) { "reg.exe exit=$exit ${output.take(240)}" }
    }

    private fun regString(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private fun quoteArgFileValue(value: String): String =
        if (value.any { it.isWhitespace() || it == '"' }) {
            "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        } else {
            value
        }
}
