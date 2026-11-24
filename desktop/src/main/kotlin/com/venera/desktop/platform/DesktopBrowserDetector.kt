package com.venera.desktop.platform

import java.io.File

/**
 * 跨平台 Chromium 浏览器探测器：供 Cloudflare 过盾与网页登录通过 CDP 交互。
 *
 * ## 探测优先级
 * 1. 环境变量 [ENV_CUSTOM_BROWSER_PATH]（支持用户手动指定便携版或非标路径）；
 * 2. Windows 平台：优先检测系统 Microsoft Edge，次选系统 Chrome / Brave；
 * 3. Linux 平台：遍历 PATH 与常见路径，覆盖 google-chrome(-stable)、chromium(-browser)、brave(-browser)、microsoft-edge(-stable)；
 * 4. macOS 平台：检测 /Applications 下的对应 .app 启动可执行体。
 */
object DesktopBrowserDetector {

    /** 允许用户自定义浏览器路径的环境变量名。 */
    const val ENV_CUSTOM_BROWSER_PATH = "VENERA_BROWSER_PATH"

    /** Linux 下常见 Chromium 系命令名列表（按探测优先级排序）。 */
    val LINUX_BROWSER_COMMANDS = listOf(
        "google-chrome",
        "google-chrome-stable",
        "chromium",
        "chromium-browser",
        "brave-browser",
        "brave",
        "microsoft-edge-stable",
        "microsoft-edge",
    )

    /** Linux 下常见绝对路径兜底。 */
    val LINUX_COMMON_PATHS = listOf(
        "/usr/bin/google-chrome",
        "/usr/bin/google-chrome-stable",
        "/usr/bin/chromium",
        "/usr/bin/chromium-browser",
        "/usr/bin/brave-browser",
        "/usr/bin/brave",
        "/usr/bin/microsoft-edge-stable",
        "/usr/bin/microsoft-edge",
        "/snap/bin/chromium",
        "/var/lib/flatpak/exports/bin/org.chromium.Chromium",
        "/var/lib/flatpak/exports/bin/com.google.Chrome",
    )

    /**
     * 查找适合 CDP 自动化控制的 Chromium 浏览器可执行文件。
     *
     * @param envProvider 环境变量提供者，测试可注入。
     * @param osName 操作系统标识，默认系统属性。
     * @param fileChecker 文件存在与合法性检查器，测试可注入。
     * @param pathLookup PATH 查找函数，测试可注入。
     */
    fun findBrowserExecutable(
        envProvider: (String) -> String? = System::getenv,
        osName: String = System.getProperty("os.name", ""),
        fileChecker: (File) -> Boolean = { it.exists() && it.isFile },
        pathLookup: (String, (String) -> String?, (File) -> Boolean) -> File? = ::findInPath,
    ): File? {
        // 1. 检查环境变量手动指定的自定义路径
        val customPath = envProvider(ENV_CUSTOM_BROWSER_PATH)?.trim()?.takeIf { it.isNotBlank() }
        if (customPath != null) {
            val customFile = File(customPath)
            if (fileChecker(customFile)) return customFile
        }

        val isWindows = osName.contains("win", ignoreCase = true)
        val isMac = osName.contains("mac", ignoreCase = true)

        return when {
            isWindows -> findWindowsBrowser(envProvider, fileChecker)
            isMac -> findMacBrowser(fileChecker)
            else -> findLinuxBrowser(envProvider, fileChecker, pathLookup)
        }
    }

    private fun findWindowsBrowser(
        envProvider: (String) -> String?,
        fileChecker: (File) -> Boolean,
    ): File? {
        val programFilesX86 = envProvider("ProgramFiles(x86)")
        val programFiles = envProvider("ProgramFiles")
        val localAppData = envProvider("LOCALAPPDATA")

        val candidates = listOfNotNull(
            programFilesX86?.let { File(it, "Microsoft/Edge/Application/msedge.exe") },
            programFiles?.let { File(it, "Microsoft/Edge/Application/msedge.exe") },
            localAppData?.let { File(it, "Microsoft/Edge/Application/msedge.exe") },
            File("C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"),
            File("C:/Program Files/Microsoft/Edge/Application/msedge.exe"),
            programFiles?.let { File(it, "Google/Chrome/Application/chrome.exe") },
            programFilesX86?.let { File(it, "Google/Chrome/Application/chrome.exe") },
            localAppData?.let { File(it, "Google/Chrome/Application/chrome.exe") },
            programFiles?.let { File(it, "BraveSoftware/Brave-Browser/Application/brave.exe") },
        )
        return candidates.firstOrNull { fileChecker(it) }
    }

    private fun findLinuxBrowser(
        envProvider: (String) -> String?,
        fileChecker: (File) -> Boolean,
        pathLookup: (String, (String) -> String?, (File) -> Boolean) -> File?,
    ): File? {
        // 依次从 PATH 中查找命令
        for (cmd in LINUX_BROWSER_COMMANDS) {
            val hit = pathLookup(cmd, envProvider, fileChecker)
            if (hit != null) return hit
        }
        // 常见路径兜底
        return LINUX_COMMON_PATHS.map { File(it) }.firstOrNull { fileChecker(it) }
    }

    private fun findMacBrowser(fileChecker: (File) -> Boolean): File? {
        val candidates = listOf(
            File("/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge"),
            File("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"),
            File("/Applications/Brave Browser.app/Contents/MacOS/Brave Browser"),
            File("/Applications/Chromium.app/Contents/MacOS/Chromium"),
        )
        return candidates.firstOrNull { fileChecker(it) }
    }

    private fun findInPath(
        commandName: String,
        envProvider: (String) -> String?,
        fileChecker: (File) -> Boolean,
    ): File? {
        val pathEnv = envProvider("PATH") ?: return null
        val sep = if (System.getProperty("os.name", "").contains("win", ignoreCase = true)) ";" else ":"
        val dirs = pathEnv.split(sep).filter { it.isNotBlank() }
        for (dir in dirs) {
            val candidate = File(dir, commandName)
            if (fileChecker(candidate)) return candidate
        }
        return null
    }
}
