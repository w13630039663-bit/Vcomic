package com.venera.desktop.installer

import com.sun.jna.Function
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.Shell32Util
import com.sun.jna.platform.win32.ShlObj
import com.sun.jna.platform.win32.WinReg
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.AccessDeniedException
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.coroutines.coroutineContext

/**
 * 安装向导启动参数解析结果。
 *
 * 兼容两类调用方式：
 * 1. 演示预览：`gradlew :desktop:installer [-PinstallerPage=...] [-PinstallerTheme=dark]`（位置参数，`sourceDir = null`）
 * 2. 真实安装/卸载：`--source=<dir> [--version=<ver>]` 或 `--uninstall [--target=<dir>]`
 */
internal data class InstallerLaunchConfig(
    val startStage: InstallerStage,
    val dark: Boolean,
    val sourceDir: File? = null,
    val appVersion: String = "1.0.1",
    val uninstallMode: Boolean = false,
    val uninstallTargetDir: File? = null,
)

internal fun parseInstallerLaunchArgs(args: Array<String>): InstallerLaunchConfig {
    var sourceDir: File? = null
    var appVersion = "1.0.1"
    var uninstallMode = false
    var uninstallTargetDir: File? = null
    val positional = mutableListOf<String>()

    for (raw in args) {
        val arg = raw.trim()
        when {
            arg.startsWith("--source=", ignoreCase = true) -> {
                val p = arg.substringAfter("=").trim().trim('"')
                if (p.isNotEmpty()) sourceDir = File(p).absoluteFile
            }
            arg.startsWith("--version=", ignoreCase = true) -> {
                val v = arg.substringAfter("=").trim().trim('"')
                if (v.isNotEmpty()) appVersion = v
            }
            arg.equals("--uninstall", ignoreCase = true) -> {
                uninstallMode = true
            }
            arg.startsWith("--target=", ignoreCase = true) -> {
                val p = arg.substringAfter("=").trim().trim('"')
                if (p.isNotEmpty()) uninstallTargetDir = File(p).absoluteFile
            }
            else -> positional.add(arg)
        }
    }

    val startStage = installerStageOf(positional.getOrNull(0))
    val dark = positional.getOrNull(1)?.equals("dark", ignoreCase = true) == true
    return InstallerLaunchConfig(
        startStage = startStage,
        dark = dark,
        sourceDir = sourceDir,
        appVersion = appVersion,
        uninstallMode = uninstallMode,
        uninstallTargetDir = uninstallTargetDir,
    )
}

internal data class InstallProgress(
    val fraction: Float,
    val statusText: String,
)

internal class InstallFailureException(
    val kind: InstallErrorKind,
    cause: Throwable? = null,
) : RuntimeException(kind.title, cause)

/**
 * 桌面端全自研安装与卸载引擎（纯 JVM + JNA Win32 API，零 PowerShell）。
 *
 * 原则：
 * - 用户数据固定独立保存于 `%LOCALAPPDATA%\venera`，安装、覆盖升级与卸载一律保留不删；
 * - 所有注册表与快捷方式均落在当前用户（HKCU / APPDATA / Desktop），无需管理员权限；
 * - 禁止调用 `powershell.exe`，避免触发杀软「隐藏执行 PowerShell」拦截。
 */
internal object InstallerEngine {

    private const val UNINSTALL_REG_SUBKEY = "Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\VComic"
    private const val LEGACY_INNO_REG_SUBKEY =
        "Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\{8C4F2E6A-1B7D-4E93-A5C2-9F6B0D7E4A31}_is1"
    private const val UNINSTALL_SCRIPT_NAME = "uninstall-vcomic.cmd"

    private const val CLSID_SHELL_LINK = "00021401-0000-0000-C000-000000000046"
    private const val IID_ISHELL_LINK_W = "000214F9-0000-0000-C000-000000000046"
    private const val IID_IPERSIST_FILE = "0000010b-0000-0000-C000-000000000046"
    private const val COINIT_APARTMENTTHREADED = 0x2
    private const val CLSCTX_INPROC_SERVER = 0x1
    private const val SW_SHOWMINNOACTIVE = 7

    private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun log(message: String) {
        runCatching {
            val local = System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }
                ?: File(System.getProperty("user.home"), "AppData\\Local").path
            val logDir = File(local, "venera\\logs").apply { mkdirs() }
            val line = "[${LocalDateTime.now().format(timeFmt)}] $message\r\n"
            File(logDir, "installer.log").appendText(line, Charsets.UTF_8)
        }
    }

    fun logError(tag: String, t: Throwable) {
        val stack = t.stackTraceToString().lineSequence().take(12).joinToString(" | ")
        log("ERROR [$tag] ${t::class.java.simpleName}: ${t.message} :: $stack")
    }

    suspend fun performInstall(
        sourceDir: File,
        targetDir: File,
        version: String,
        createDesktopShortcut: Boolean = true,
        createStartMenuShortcut: Boolean = true,
        onProgress: (InstallProgress) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val normSource = runCatching { sourceDir.canonicalFile }.getOrElse { sourceDir.absoluteFile }
        val normTarget = runCatching { targetDir.canonicalFile }.getOrElse { targetDir.absoluteFile }
        log("开始安装 source=${normSource.path} target=${normTarget.path} version=$version")
        if (!normSource.isDirectory || !File(normSource, "venera-desktop.exe").isFile) {
            log("安装源缺少 venera-desktop.exe")
            throw InstallFailureException(InstallErrorKind.FILE_LOCKED)
        }

        val filesToCopy = collectPayloadFiles(normSource)
        val totalBytes = filesToCopy.sumOf { it.length().coerceAtLeast(0L) }.coerceAtLeast(1L)
        log("扫描安装包完成 files=${filesToCopy.size} totalBytes=$totalBytes")

        // 检查目标磁盘剩余空间（至少预留包体大小 + 64MB）
        val probeDir = generateSequence(normTarget) { it.parentFile }
            .firstOrNull { it.exists() }
        val usableSpace = probeDir?.usableSpace ?: Long.MAX_VALUE
        if (usableSpace < totalBytes + 64L * 1024L * 1024L) {
            log("磁盘空间不足 usable=$usableSpace needed=${totalBytes + 64L * 1024L * 1024L}")
            throw InstallFailureException(InstallErrorKind.DISK_FULL)
        }

        onProgress(InstallProgress(0.03f, "正在准备安装环境…"))
        coroutineContext.ensureActive()

        // 仅精准关闭目标安装目录下的旧进程，绝不误杀位于临时目录的当前安装向导进程
        log("步骤1: 检查目标目录旧进程")
        terminateTargetAppProcess(normTarget)
        Thread.sleep(200)

        onProgress(InstallProgress(0.08f, "正在检查并迁移旧版本…"))
        coroutineContext.ensureActive()
        log("步骤2: 检查并迁移旧版本")
        cleanupLegacyMsiIfPresent()
        deleteRegistryTreeQuietly(WinReg.HKEY_CURRENT_USER, LEGACY_INNO_REG_SUBKEY)

        // 准备目标目录并清理旧运行时与旧 jar（避免内容哈希 jar 重复累积）
        log("步骤3: 清理旧组件并准备目标目录")
        try {
            if (!normTarget.exists() && !normTarget.mkdirs()) {
                throw AccessDeniedException(normTarget.path)
            }
            val appDir = File(normTarget, "app")
            val runtimeDir = File(normTarget, "runtime")
            if (appDir.exists()) appDir.deleteRecursively()
            if (runtimeDir.exists()) runtimeDir.deleteRecursively()
        } catch (e: CancellationException) {
            throw e
        } catch (e: AccessDeniedException) {
            logError("prepare-target", e)
            throw InstallFailureException(InstallErrorKind.ACCESS_DENIED, e)
        } catch (e: FileSystemException) {
            logError("prepare-target", e)
            throw InstallFailureException(InstallErrorKind.FILE_LOCKED, e)
        } catch (e: Exception) {
            logError("prepare-target", e)
            throw InstallFailureException(InstallErrorKind.ACCESS_DENIED, e)
        }

        // 复制应用文件（0.10 -> 0.88）
        log("步骤4: 开始写入文件")
        var copiedBytes = 0L
        val sourceRoot = normSource.toPath().toAbsolutePath().normalize()
        val targetRoot = normTarget.toPath().toAbsolutePath().normalize()
        for (srcFile in filesToCopy) {
            coroutineContext.ensureActive()
            val rel = sourceRoot.relativize(srcFile.toPath().toAbsolutePath().normalize())
            val dst = targetRoot.resolve(rel)
            try {
                dst.parent?.let { Files.createDirectories(it) }
                Files.copy(
                    srcFile.toPath(),
                    dst,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.COPY_ATTRIBUTES,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: AccessDeniedException) {
                logError("copy-${srcFile.name}", e)
                throw InstallFailureException(InstallErrorKind.ACCESS_DENIED, e)
            } catch (e: FileSystemException) {
                logError("copy-${srcFile.name}", e)
                throw InstallFailureException(InstallErrorKind.FILE_LOCKED, e)
            } catch (e: Exception) {
                logError("copy-${srcFile.name}", e)
                val msg = e.message?.lowercase().orEmpty()
                if ("space" in msg || "disk" in msg) {
                    throw InstallFailureException(InstallErrorKind.DISK_FULL, e)
                }
                throw InstallFailureException(InstallErrorKind.FILE_LOCKED, e)
            }
            copiedBytes += srcFile.length().coerceAtLeast(0L)
            val copyFrac = (copiedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
            onProgress(InstallProgress(0.10f + 0.78f * copyFrac, "正在写入组件…"))
        }

        coroutineContext.ensureActive()
        onProgress(InstallProgress(0.92f, "正在创建快捷方式与卸载入口…"))
        log("步骤5: 创建快捷方式与注册表")

        runCatching {
            writeUninstallScript(normTarget)
            createShortcutsAndRegistry(
                targetDir = normTarget,
                version = version,
                estimatedKb = (totalBytes / 1024L).coerceAtLeast(1L),
                createDesktopShortcut = createDesktopShortcut,
                createStartMenuShortcut = createStartMenuShortcut,
            )
        }.onFailure { t ->
            logError("shortcuts-registry", t)
        }

        log("安装成功完成 target=${normTarget.path}")
        onProgress(InstallProgress(1f, "安装完成，正在收尾…"))
    }

    /** 卸载指定目录下的原程序，清理快捷方式与卸载注册表，保留 `%LOCALAPPDATA%\venera` 用户数据。 */
    suspend fun performUninstall(targetDir: File) = withContext(Dispatchers.IO) {
        val normTarget = runCatching { targetDir.canonicalFile }.getOrElse { targetDir.absoluteFile }
        log("开始卸载 target=${normTarget.path}")
        terminateTargetAppProcess(normTarget)
        Thread.sleep(200)

        runCatching {
            val desktopDir = Shell32Util.getFolderPath(ShlObj.CSIDL_DESKTOPDIRECTORY)
            val programsDir = Shell32Util.getFolderPath(ShlObj.CSIDL_PROGRAMS)
            File(desktopDir, "VComic.lnk").delete()
            File(desktopDir, "Venera.lnk").delete()
            File(programsDir, "VComic").deleteRecursively()
            File(programsDir, "Venera").deleteRecursively()
        }.onFailure { t -> logError("uninstall-shortcuts", t) }

        cleanupLegacyMsiIfPresent()
        deleteRegistryTreeQuietly(WinReg.HKEY_CURRENT_USER, UNINSTALL_REG_SUBKEY)
        deleteRegistryTreeQuietly(WinReg.HKEY_CURRENT_USER, LEGACY_INNO_REG_SUBKEY)
        deleteRegistryTreeQuietly(WinReg.HKEY_CURRENT_USER, "Software\\Classes\\pixiv")
        deleteRegistryTreeQuietly(WinReg.HKEY_CURRENT_USER, "Software\\Classes\\venera")

        if (normTarget.exists()) {
            val ok = runCatching { normTarget.deleteRecursively() }.getOrDefault(false)
            log("删除原程序目录结果 ok=$ok path=${normTarget.path}")
        }
    }

    fun launchInstalledApp(targetDir: File) {
        val exe = File(targetDir, "venera-desktop.exe")
        if (!exe.isFile) {
            log("启动目标不存在：${exe.absolutePath}")
            return
        }
        runCatching {
            ProcessBuilder(exe.absolutePath)
                .directory(targetDir)
                .start()
        }.onFailure { t ->
            logError("launch-app", t)
        }
    }

    private fun terminateTargetAppProcess(targetDir: File) {
        val selfPid = ProcessHandle.current().pid()
        val targetExe = runCatching { File(targetDir, "venera-desktop.exe").canonicalPath }
            .getOrElse { File(targetDir, "venera-desktop.exe").absolutePath }
        runCatching {
            ProcessHandle.allProcesses().forEach { ph ->
                if (ph.pid() == selfPid) return@forEach
                val cmd = ph.info().command().orElse("")
                if (cmd.isEmpty()) return@forEach
                val cmdPath = runCatching { File(cmd).canonicalPath }.getOrElse { cmd }
                if (cmdPath.equals(targetExe, ignoreCase = true)) {
                    log("关闭目标目录旧进程 pid=${ph.pid()} cmd=$cmdPath")
                    ph.destroyForcibly()
                }
            }
        }.onFailure { t ->
            logError("kill-old", t)
        }
    }

    private fun collectPayloadFiles(sourceDir: File): List<File> =
        sourceDir.walkTopDown()
            .filter { file ->
                file.isFile &&
                    !file.name.startsWith("sfx-", ignoreCase = true) &&
                    !file.name.equals("payload.7z", ignoreCase = true)
            }
            .toList()

    private fun writeUninstallScript(targetDir: File) {
        val targetEsc = targetDir.absolutePath
        val script = """
            @echo off
            setlocal
            taskkill /F /IM venera-desktop.exe >nul 2>&1
            del /f /q "%USERPROFILE%\Desktop\VComic.lnk" >nul 2>&1
            del /f /q "%USERPROFILE%\Desktop\Venera.lnk" >nul 2>&1
            rmdir /s /q "%APPDATA%\Microsoft\Windows\Start Menu\Programs\VComic" >nul 2>&1
            rmdir /s /q "%APPDATA%\Microsoft\Windows\Start Menu\Programs\Venera" >nul 2>&1
            reg delete "HKCU\$UNINSTALL_REG_SUBKEY" /f >nul 2>&1
            reg delete "HKCU\Software\Classes\pixiv" /f >nul 2>&1
            reg delete "HKCU\Software\Classes\venera" /f >nul 2>&1
            start "" /b cmd /c "ping -n 2 127.0.0.1 >nul & rmdir /s /q ${'"'}$targetEsc${'"'}"
            exit /b 0
        """.trimIndent().replace("\n", "\r\n")
        File(targetDir, UNINSTALL_SCRIPT_NAME).writeText(script, Charsets.UTF_8)
    }

    private fun createShortcutsAndRegistry(
        targetDir: File,
        version: String,
        estimatedKb: Long,
        createDesktopShortcut: Boolean,
        createStartMenuShortcut: Boolean,
    ) {
        val exePath = File(targetDir, "venera-desktop.exe").absolutePath
        val uninstPath = File(targetDir, UNINSTALL_SCRIPT_NAME).absolutePath

        val desktopDir = Shell32Util.getFolderPath(ShlObj.CSIDL_DESKTOPDIRECTORY)
        val programsDir = Shell32Util.getFolderPath(ShlObj.CSIDL_PROGRAMS)

        // 清理旧品牌 Venera 快捷方式残留
        File(desktopDir, "Venera.lnk").delete()
        File(programsDir, "Venera").deleteRecursively()

        if (createDesktopShortcut) {
            createWindowsShortcut(
                lnkPath = File(desktopDir, "VComic.lnk").absolutePath,
                targetPath = exePath,
                workingDir = targetDir.absolutePath,
                iconPath = exePath,
            )
        }
        if (createStartMenuShortcut) {
            val menuDir = File(programsDir, "VComic").apply { mkdirs() }
            createWindowsShortcut(
                lnkPath = File(menuDir, "VComic.lnk").absolutePath,
                targetPath = exePath,
                workingDir = targetDir.absolutePath,
                iconPath = exePath,
            )
            createWindowsShortcut(
                lnkPath = File(menuDir, "卸载 VComic.lnk").absolutePath,
                targetPath = "cmd.exe",
                arguments = "/c \"$uninstPath\"",
                workingDir = targetDir.absolutePath,
                iconPath = exePath,
                showCmd = SW_SHOWMINNOACTIVE,
            )
        }

        // 写入当前用户卸载注册表项（无需管理员权限）
        Advapi32Util.registryCreateKey(WinReg.HKEY_CURRENT_USER, UNINSTALL_REG_SUBKEY)
        Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, UNINSTALL_REG_SUBKEY, "DisplayName", "VComic")
        Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, UNINSTALL_REG_SUBKEY, "DisplayVersion", version)
        Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, UNINSTALL_REG_SUBKEY, "Publisher", "VComic")
        Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, UNINSTALL_REG_SUBKEY, "InstallLocation", targetDir.absolutePath)
        Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, UNINSTALL_REG_SUBKEY, "DisplayIcon", exePath)
        Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, UNINSTALL_REG_SUBKEY, "UninstallString", "cmd.exe /c \"$uninstPath\"")
        Advapi32Util.registrySetIntValue(WinReg.HKEY_CURRENT_USER, UNINSTALL_REG_SUBKEY, "NoModify", 1)
        Advapi32Util.registrySetIntValue(WinReg.HKEY_CURRENT_USER, UNINSTALL_REG_SUBKEY, "NoRepair", 1)
        Advapi32Util.registrySetIntValue(WinReg.HKEY_CURRENT_USER, UNINSTALL_REG_SUBKEY, "EstimatedSize", estimatedKb.toInt())
    }

    /** 通过 JNA 直调 IShellLinkW + IPersistFile 创建 `.lnk` 快捷方式（零外部脚本进程）。 */
    private fun createWindowsShortcut(
        lnkPath: String,
        targetPath: String,
        arguments: String? = null,
        workingDir: String? = null,
        iconPath: String? = null,
        showCmd: Int? = null,
    ) {
        val comInit = ComLib.INSTANCE.CoInitializeEx(null, COINIT_APARTMENTTHREADED)
        val comOwnedHere = comInit == 0
        try {
            val linkRef = PointerByReference()
            val hr = ComLib.INSTANCE.CoCreateInstance(
                Guid.GUID.fromString(CLSID_SHELL_LINK),
                null,
                CLSCTX_INPROC_SERVER,
                Guid.GUID.fromString(IID_ISHELL_LINK_W),
                linkRef,
            )
            if (hr != 0 || linkRef.value == null) {
                log("CoCreateInstance IShellLinkW 失败 hr=$hr")
                return
            }
            val shellLink = linkRef.value
            try {
                // IShellLinkW::SetPath = 20
                vtableFn(shellLink, 20).invokeInt(arrayOf(shellLink, WString(targetPath)))
                if (!arguments.isNullOrEmpty()) {
                    // IShellLinkW::SetArguments = 11
                    vtableFn(shellLink, 11).invokeInt(arrayOf(shellLink, WString(arguments)))
                }
                if (!workingDir.isNullOrEmpty()) {
                    // IShellLinkW::SetWorkingDirectory = 9
                    vtableFn(shellLink, 9).invokeInt(arrayOf(shellLink, WString(workingDir)))
                }
                if (!iconPath.isNullOrEmpty()) {
                    // IShellLinkW::SetIconLocation = 17
                    vtableFn(shellLink, 17).invokeInt(arrayOf(shellLink, WString(iconPath), 0))
                }
                if (showCmd != null) {
                    // IShellLinkW::SetShowCmd = 15
                    vtableFn(shellLink, 15).invokeInt(arrayOf(shellLink, showCmd))
                }

                val persistRef = PointerByReference()
                // IUnknown::QueryInterface = 0
                val qiHr = vtableFn(shellLink, 0).invokeInt(
                    arrayOf(shellLink, Guid.GUID.fromString(IID_IPERSIST_FILE), persistRef),
                )
                if (qiHr == 0 && persistRef.value != null) {
                    val persistFile = persistRef.value
                    try {
                        // IPersistFile::Save = 6
                        val saveHr = vtableFn(persistFile, 6).invokeInt(arrayOf(persistFile, WString(lnkPath), 1))
                        if (saveHr != 0) log("IPersistFile::Save 失败 lnk=$lnkPath hr=$saveHr")
                    } finally {
                        vtableFn(persistFile, 2).invokeInt(arrayOf(persistFile))
                    }
                }
            } finally {
                vtableFn(shellLink, 2).invokeInt(arrayOf(shellLink))
            }
        } finally {
            if (comOwnedHere) ComLib.INSTANCE.CoUninitialize()
        }
    }

    private fun cleanupLegacyMsiIfPresent() {
        val roots = listOf(
            WinReg.HKEY_CURRENT_USER to "Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall",
            WinReg.HKEY_LOCAL_MACHINE to "SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall",
            WinReg.HKEY_LOCAL_MACHINE to "SOFTWARE\\WOW6432Node\\Microsoft\\Windows\\CurrentVersion\\Uninstall",
        )
        val guidPattern = Regex("\\{[0-9A-Fa-f-]+}")
        for ((hkey, rootPath) in roots) {
            val subKeys = runCatching { Advapi32Util.registryGetKeys(hkey, rootPath) }.getOrNull() ?: continue
            for (sub in subKeys) {
                val fullPath = "$rootPath\\$sub"
                val displayName = runCatching {
                    Advapi32Util.registryGetStringValue(hkey, fullPath, "DisplayName")
                }.getOrNull() ?: continue
                if (!displayName.startsWith("venera-desktop", ignoreCase = true)) continue
                val uninst = runCatching {
                    Advapi32Util.registryGetStringValue(hkey, fullPath, "UninstallString")
                }.getOrNull() ?: continue
                val code = guidPattern.find(uninst)?.value ?: continue
                log("发现旧版 MSI ($displayName)，执行静默清理 code=$code")
                runCatching {
                    ProcessBuilder("msiexec.exe", "/x", code, "/qn")
                        .redirectErrorStream(true)
                        .start()
                        .waitFor()
                }
            }
        }
    }

    private fun deleteRegistryTreeQuietly(hkey: WinReg.HKEY, subKey: String) {
        runCatching {
            if (Advapi32Util.registryKeyExists(hkey, subKey)) {
                Advapi32Util.registryDeleteKey(hkey, subKey)
            }
        }
    }

    private fun vtableFn(obj: Pointer, index: Int): Function =
        Function.getFunction(
            obj.getPointer(0).getPointer(index * Native.POINTER_SIZE.toLong()),
            Function.C_CONVENTION,
        )

    private interface ComLib : StdCallLibrary {
        companion object {
            val INSTANCE: ComLib = Native.load("ole32", ComLib::class.java)
        }

        fun CoInitializeEx(pvReserved: Pointer?, coInit: Int): Int
        fun CoUninitialize()
        fun CoCreateInstance(clsid: Guid.GUID, outer: Pointer?, clsctx: Int, riid: Guid.GUID, out: PointerByReference): Int
    }
}
