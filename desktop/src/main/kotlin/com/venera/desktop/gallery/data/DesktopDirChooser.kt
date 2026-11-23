package com.venera.desktop.gallery.data

import com.sun.jna.Function
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.User32
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import java.io.File
import com.venera.desktop.appendDesktopErrorLog

/**
 * 原生「挑一个文件夹」对话框 —— **新式 IFileDialog**（Vista+ 的现代框，带新建文件夹/地址栏，
 * 令：不许再用老式树形框）。
 *
 * ## 为什么落在 JNA 上而不是外面再包一颗库
 * 选型当天查过 Maven Central：workito 不在 Central（JitPack 才有，往构建里加一条会漂的源），
 * darklaf 线（native-utils / windows / core 三颗 jar）里没有文件夹选择器，java-native-foundation
 * 的打包是 zip（普通 implementation 依赖吃不下）。而 **JNA 本来就在编译类路径上**（vlcj 4.x 的
 * 编译期依赖）。JNA 没有现成的 IFileDialog 绑定 ⇒ 这里按 vtable 索引直调 COM：索引取自
 * Wine `shobjidl.idl` 的 IFileDialog / IShellItem 声明顺序（IUnknown 0-2，IModalWindow::Show=3）。
 *
 * ## 踩过的坑（都有读数）
 * 1. **EDT 上 COM 常未初始化**：CoCreateInstance 直接 0x800401F0 起不来 ⇒ 每次自持
 *    `CoInitializeEx(APARTMENTTHREADED)`，已有初始化（RPC_E_CHANGED_MODE）不算失败也不配对释放。
 * 2. **`Guid.CLSID.fromString` 定义在父类上返回 `GUID`**，塞给要求 `CLSID` 的形参在 JNA 映射期
 *    抛 NPE ⇒ COM 绑定自持（不走 `Ole32.INSTANCE` 的 TypeMapper），形参一律收父类 `GUID`
 *    （COM 的 REFCLSID/REFIID 只看那 16 字节，继承与否无关）。
 * 3. **JNA 的 `Pointer.NULL` 在上游源码里就是 Java null**：Kotlin 侧拿它当非空兜底值会把 null
 *    喂进非空形参、在调用点炸 checkNotNull NPE ⇒ owner 收 `Pointer?`，null 由 JNA 编组为
 *    NULL 指针（对话框退化为非模态，不再崩）。
 * 4. **vtable 直调必须把 COM 对象指针当第一个实参**（COM 方法本质是 thiscall）：漏传时
 *    方法把标志位当 this 解引用，探针实测第一下 SetOptions 直接 Invalid memory access。
 *
 * ## 调用纪律
 * 模态原生框：从点击回调的线程直接调（Compose 的点击回调在 EDT 上，框自己泵消息，不冻界面）。
 * 取消 ⇒ `null`，由 caller 如实念回执。任何异常：帧进 stdout、全栈落 `desktop-error.log`。
 * 非 Windows 走 Swing `JFileChooser`（仅目录档），函数签名一致。
 */
internal object DesktopDirChooser {

    // ── COM 标识（JNA fromString 只吃 8-4-4-4-12 无括号格式）──
    private const val CLSID_FILE_DIALOG = "DC1C5A9C-E88A-4DDE-A5A1-60F82A20AEF7"
    private const val IID_I_FILE_DIALOG = "42F85136-DB7E-439C-85F1-E4075D135FC8"
    private const val IID_I_SHELL_ITEM = "43826D1E-E718-42EE-BC55-A1E261C37BFE"

    // ── IFileDialog vtable 索引（IUnknown 0-2、IModalWindow::Show=3 之后按 IDL 声明序）──
    private const val VTABLE_SET_OPTIONS = 9
    private const val VTABLE_SET_FOLDER = 12
    private const val VTABLE_SET_TITLE = 17
    private const val VTABLE_GET_RESULT = 20

    // ── IShellItem vtable 索引 ──
    private const val VTABLE_GET_DISPLAY_NAME = 5

    // FILEOPENDIALOGOPTIONS：只许文件系统目录（FOS_PICKFOLDERS）+ 强制文件系统路径
    private const val FOS_PICKFOLDERS = 0x20
    private const val FOS_FORCEFILESYSTEM = 0x40
    // SIGDN：要回文件系统完整路径
    private const val SIGDN_FILESYSPATH = 0x80058000L

    // COINIT_APARTMENTTHREADED（CoInitializeEx 第二参是 int）
    private const val COINIT_APARTMENTTHREADED = 0x2
    private const val CLSCTX_INPROC_SERVER = 0x1

    /** 弹「选择文件夹」；返回所选目录绝对路径。取消或不可用 ⇒ null（不抛）。 */
    fun pickDirectory(title: String, initialDir: String?): String? = pickDirectory(title, initialDir, null)

    /**
     * 弹「选择文件夹」，失败时把可读短句交给 [onFailure] 上屏。
     *
     * 原来的失败只进日志、屏上静默：用户点了"选目录"没反应，分不出是取消还是打不开。
     * 回调是可选的，不传则维持原静默行为（图片保存那条路仍只要 null）。
     */
    fun pickDirectory(
        title: String,
        initialDir: String?,
        onFailure: ((String) -> Unit)?,
    ): String? =
        if (isWindows) {
            runCatching { pickWindowsNative(title, initialDir) }.onFailure {
                // NPE 这类没 message 的异常只有帧才定位得了：帧进 stdout，全栈进 desktop-error.log。
                val frames = it.stackTrace.take(8)
                    .joinToString(" | ") { f -> "${f.className.substringAfterLast('.')}#${f.fileName}:${f.lineNumber}" }
                println("D_取图 目录框 异常 ${it::class.java.simpleName}: ${it.message?.take(120)} @ $frames")
                appendDesktopErrorLog("dir-chooser", it)
                onFailure?.invoke("目录框打不开：${it::class.java.simpleName}")
            }.getOrNull()
        } else {
            runCatching { pickSwing(title, initialDir) }.onFailure {
                onFailure?.invoke("目录框打不开：${it::class.java.simpleName}")
            }.getOrNull()
        }

    private val isWindows: Boolean
        get() = System.getProperty("os.name", "").contains("Windows", ignoreCase = true)

    private fun pickWindowsNative(title: String, initialDir: String?): String? {
        // Compose 的点击回调在 EDT 上，活动窗即主窗 —— 对话框挂它做模态，永不被主窗盖住。
        // 拿不到活动窗（返回 null）就走非模态，不能用 Pointer.NULL 兜底（它本身是 Java null）。
        val owner: Pointer? = User32.INSTANCE.GetActiveWindow()?.pointer
        // EDT 常没初始化 COM：CoCreateInstance 会 0x800401F0 直接起不来。本次自持初始化，
        // 已有初始化（RPC_E_CHANGED_MODE）不算失败也不配对释放。
        val comInit = ComLib.INSTANCE.CoInitializeEx(null, COINIT_APARTMENTTHREADED)
        val comOwnedHere = comInit.toLong() == 0L
        return try {
            pickWindowsNativeInner(title, initialDir, owner)
        } finally {
            if (comOwnedHere) ComLib.INSTANCE.CoUninitialize()
        }
    }

    private fun pickWindowsNativeInner(title: String, initialDir: String?, owner: Pointer?): String? {
        val dialogRef = PointerByReference()
        val hr = ComLib.INSTANCE.CoCreateInstance(
            Guid.GUID.fromString(CLSID_FILE_DIALOG),
            null,
            CLSCTX_INPROC_SERVER,
            Guid.GUID.fromString(IID_I_FILE_DIALOG),
            dialogRef,
        )
        if (hr.toLong() != 0L) {
            println("D_取图 目录框 起不来 hr=${hr.toLong()}")
            return null
        }
        val dialog = dialogRef.value ?: return null
        return try {
            // 每个 vtable 调用第一参都得是 COM 对象指针本身（thiscall 的 this）。
            vtableFn(dialog, VTABLE_SET_OPTIONS)
                .invokeInt(arrayOf(dialog, FOS_PICKFOLDERS or FOS_FORCEFILESYSTEM))
            if (!initialDir.isNullOrBlank()) {
                val itemRef = PointerByReference()
                val itemHr = ShellFolderJna.INSTANCE.SHCreateItemFromParsingName(
                    initialDir,
                    null,
                    Guid.GUID.fromString(IID_I_SHELL_ITEM),
                    itemRef,
                )
                if (itemHr.toLong() == 0L && itemRef.value != null) {
                    vtableFn(dialog, VTABLE_SET_FOLDER).invokeInt(arrayOf(dialog, itemRef.value))
                    vtableFn(itemRef.value, 2).invokeInt(arrayOf(itemRef.value)) // IShellItem::Release
                }
            }
            // vtable 直调没有 UNICODE_OPTIONS 的类型映射：String 会被按 ANSI 传，宽字符接口必须显式 WString。
            vtableFn(dialog, VTABLE_SET_TITLE).invokeInt(arrayOf(dialog, com.sun.jna.WString(title)))

            if (vtableFn(dialog, 3).invokeInt(arrayOf(dialog, owner)).toLong() != 0L) return null

            val resultRef = PointerByReference()
            if (vtableFn(dialog, VTABLE_GET_RESULT).invokeInt(arrayOf(dialog, resultRef)).toLong() != 0L) return null
            val item = resultRef.value ?: return null
            try {
                val pathRef = PointerByReference()
                val nameHr = vtableFn(item, VTABLE_GET_DISPLAY_NAME)
                    .invokeInt(arrayOf(item, SIGDN_FILESYSPATH, pathRef))
                if (nameHr.toLong() != 0L) return null
                val path = pathRef.value?.getWideString(0)?.takeIf { it.isNotBlank() } ?: return null
                ComLib.INSTANCE.CoTaskMemFree(pathRef.value)
                path
            } finally {
                vtableFn(item, 2).invokeInt(arrayOf(item)) // IShellItem::Release
            }
        } finally {
            vtableFn(dialog, 2).invokeInt(arrayOf(dialog)) // IFileDialog::Release
        }
    }

    private fun vtableFn(obj: Pointer, index: Int): Function =
        Function.getFunction(
            obj.getPointer(0).getPointer(index * Native.POINTER_SIZE.toLong()),
            Function.C_CONVENTION,
        )

    /**
     * COM 进程内初始化与对象创建的自持绑定。**不走 `Ole32.INSTANCE`**：
     * 那颗接口带 UNICODE_OPTIONS 的 TypeMapper，运行期对形参做映射，
     * 实测在 EDT 上把 CoInitializeEx 一族映射成 NPE —— 自持裸签名最稳。
     */
    private interface ComLib : StdCallLibrary {
        companion object {
            val INSTANCE: ComLib = Native.load("ole32", ComLib::class.java)
        }

        fun CoInitializeEx(pvReserved: Pointer?, coInit: Int): Int
        fun CoUninitialize()
        fun CoCreateInstance(clsid: Guid.GUID, outer: Pointer?, clsctx: Int, riid: Guid.GUID, out: PointerByReference): Int
        fun CoTaskMemFree(pv: Pointer?)
    }

    /** shell32 的目录项创建（初始目录 → IShellItem）。UNICODE_OPTIONS 会把 String 参数映射成宽字符。 */
    private interface ShellFolderJna : StdCallLibrary {
        companion object {
            val INSTANCE: ShellFolderJna =
                Native.load("shell32", ShellFolderJna::class.java, W32APIOptions.UNICODE_OPTIONS)
        }

        /** 初始目录路径 → IShellItem（定位用）。 */
        fun SHCreateItemFromParsingName(pszPath: String, pbc: Pointer?, riid: Guid.GUID, ppv: PointerByReference): Int
    }

    private fun pickSwing(title: String, initialDir: String?): String? {
        val chooser = javax.swing.JFileChooser(initialDir?.let(::File))
        chooser.fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
        chooser.dialogTitle = title
        val approved = chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION
        return if (approved) chooser.selectedFile.absolutePath else null
    }
}
