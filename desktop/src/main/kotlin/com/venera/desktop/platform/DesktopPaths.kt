package com.venera.desktop.platform

import com.venera.compose.data.platform.PathProvider
import java.io.File
import java.io.IOException

/**
 * 桌面侧的数据目录门面：Windows 来源为 `%LOCALAPPDATA%\venera`；Linux 遵循 XDG 规范。
 *
 * S0-9 的判据是"数据落 `%LOCALAPPDATA%`"（Linux 下落 XDG 数据目录）。这条要么成立要么当场炸，
 * 不能"取不到就退回临时目录"—— 退回 tmp 等于把判据假通过，且用户数据会在清临时目录时蒸发。
 */
class DesktopPaths private constructor(override val dataRoot: File, override val cacheRoot: File) : PathProvider {
    companion object {
        /**
         * 兼容现有单参数尾随 lambda 调用的便利重载。
         */
        fun create(envProvider: (String) -> String?): PathProvider =
            create(
                envProvider = envProvider,
                osName = System.getProperty("os.name", ""),
                userHome = System.getProperty("user.home", ""),
            )

        /**
         * 创建桌面路径提供器。
         *
         * @param envProvider 环境变量提供器，测试可注入模拟无变量情形。
         * @param osName 操作系统标识，默认取系统属性 os.name。
         * @param userHome 用户主目录路径，默认取系统属性 user.home。
         */
        fun create(
            envProvider: (String) -> String? = System::getenv,
            osName: String = System.getProperty("os.name", ""),
            userHome: String = System.getProperty("user.home", ""),
        ): PathProvider {
            val isWindows = osName.contains("win", ignoreCase = true)
            val (root, cache) = if (isWindows) {
                val base = envProvider("LOCALAPPDATA")?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("环境里没有 LOCALAPPDATA，桌面数据目录无从谈起")
                val r = File(base, "venera")
                val c = File(r, "cache")
                r to c
            } else {
                val home = userHome.takeIf { it.isNotBlank() }
                val dataBase = envProvider("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }
                    ?: home?.let { File(it, ".local/share").path }
                    ?: throw IllegalStateException("环境里没有 XDG_DATA_HOME 且拿不到 user.home，Linux 桌面数据目录无从谈起")
                val cacheBase = envProvider("XDG_CACHE_HOME")?.takeIf { it.isNotBlank() }
                    ?: home?.let { File(it, ".cache").path }
                    ?: dataBase
                File(dataBase, "venera") to File(cacheBase, "venera")
            }

            if (!root.mkdirs() && !root.isDirectory) throw IllegalStateException("建不出目录 $root")
            val probe = File(root, ".write-probe")
            try {
                probe.writeText("ok")
                // "写得进却读不出"不设文件系统注入缝：writeText 成功后回读内容仍对不上，真机构造不出来，为它引抽象不值。
                if (probe.readText() != "ok") throw IllegalStateException("$root 写得进却读不出")
            } catch (e: IOException) {
                // 真机实证：打包 exe 在探测写入上抛过 FileNotFoundException（拒绝访问），错误面统一收敛为 IllegalStateException
                throw IllegalStateException("探测读写失败 $root：${e.message}", e)
            } finally {
                probe.delete()
            }
            return DesktopPaths(root, cache)
        }
    }
}
