package com.venera.desktop

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import kotlinx.coroutines.delay

/**
 * 为 Nucleus 2 (tao) 原生 Win32 窗口挂接 `WM_GETMINMAXINFO` 消息钩子。
 *
 * 仅在 `WM_GETMINMAXINFO` 返回前把 `ptMinTrackSize` 钳制到指定物理像素下限，
 * 避免自绘无边框窗口在系统拖拽边框缩小时突破最小尺寸。
 */
internal object DesktopWindowMinSizeHook {

    private const val WM_GETMINMAXINFO = 0x0024
    private const val GWLP_WNDPROC = -4
    private const val MINMAXINFO_BYTES = 40L
    private const val OFFSET_MIN_TRACK_X = 24L
    private const val OFFSET_MIN_TRACK_Y = 28L

    @Volatile
    private var minWidthPx: Int = 0

    @Volatile
    private var minHeightPx: Int = 0

    @Volatile
    private var hookedHwnd: Long = 0L

    @Volatile
    private var oldWndProc: MemorySegment? = null

    @Volatile
    private var callWindowProcMh: java.lang.invoke.MethodHandle? = null

    @Volatile
    private var upcallStub: MemorySegment? = null

    /**
     * 更新最小物理像素下限，并在尚未挂钩时按窗口标题查找 HWND 安装钩子。
     *
     * @param title 主窗口标题，用于 Win32 `FindWindowW` 定位句柄。
     * @param minWidthPx 最小宽度（物理像素）。
     * @param minHeightPx 最小高度（物理像素）。
     */
    suspend fun installOrUpdate(title: String, minWidthPx: Int, minHeightPx: Int) {
        if (!System.getProperty("os.name", "").contains("Windows", ignoreCase = true)) return
        this.minWidthPx = minWidthPx.coerceAtLeast(1)
        this.minHeightPx = minHeightPx.coerceAtLeast(1)
        if (hookedHwnd != 0L) return

        var hwnd: Long? = null
        repeat(15) {
            hwnd = findHwnd(title)
            if (hwnd != null && hwnd != 0L) return@repeat
            delay(50)
        }
        val targetHwnd = hwnd ?: return
        install(targetHwnd)
    }

    @Synchronized
    private fun install(hwnd: Long) {
        if (hookedHwnd != 0L) return
        runCatching {
            val linker = Linker.nativeLinker()
            val user32 = SymbolLookup.libraryLookup("user32", Arena.global())
            val ptr = ValueLayout.ADDRESS

            val desc = FunctionDescriptor.of(
                ptr, ptr, ValueLayout.JAVA_INT, ptr, ptr,
            )
            val target = MethodHandles.lookup().findStatic(
                DesktopWindowMinSizeHook::class.java,
                "wndProc",
                MethodType.methodType(
                    MemorySegment::class.java,
                    MemorySegment::class.java,
                    Int::class.javaPrimitiveType,
                    MemorySegment::class.java,
                    MemorySegment::class.java,
                ),
            )
            val stub = linker.upcallStub(target, desc, Arena.global())

            val callDesc = FunctionDescriptor.of(
                ptr, ptr, ptr, ValueLayout.JAVA_INT, ptr, ptr,
            )
            val call = user32.find("CallWindowProcW").map {
                linker.downcallHandle(it, callDesc)
            }.orElse(null) ?: return

            val getPtr = user32.find("GetWindowLongPtrW").map {
                linker.downcallHandle(it, FunctionDescriptor.of(ptr, ptr, ValueLayout.JAVA_INT))
            }.orElse(null) ?: return

            val setPtr = user32.find("SetWindowLongPtrW").map {
                linker.downcallHandle(it, FunctionDescriptor.of(ptr, ptr, ValueLayout.JAVA_INT, ptr))
            }.orElse(null) ?: return

            val hwndSeg = MemorySegment.ofAddress(hwnd)
            val old = (getPtr.invokeExact(hwndSeg, GWLP_WNDPROC) as MemorySegment).address()
            if (old == 0L) return

            oldWndProc = MemorySegment.ofAddress(old)
            callWindowProcMh = call
            upcallStub = stub
            setPtr.invokeExact(hwndSeg, GWLP_WNDPROC, stub) as MemorySegment
            hookedHwnd = hwnd
            println("D_窗口最小尺寸 已挂钩 hwnd=0x${hwnd.toString(16)} min=${minWidthPx}x${minHeightPx}")
        }.onFailure {
            println("D_窗口最小尺寸 挂钩失败 ${it::class.java.simpleName}: ${it.message}")
        }
    }

    @JvmStatic
    private fun wndProc(
        hwnd: MemorySegment,
        msg: Int,
        wParam: MemorySegment,
        lParam: MemorySegment,
    ): MemorySegment {
        val proc = oldWndProc
        val call = callWindowProcMh
        if (proc == null || call == null) return MemorySegment.NULL

        val result = runCatching {
            call.invokeExact(proc, hwnd, msg, wParam, lParam) as MemorySegment
        }.getOrElse { MemorySegment.NULL }

        if (msg == WM_GETMINMAXINFO && lParam.address() != 0L) {
            runCatching {
                val info = MemorySegment.ofAddress(lParam.address()).reinterpret(MINMAXINFO_BYTES)
                val curX = info.get(ValueLayout.JAVA_INT, OFFSET_MIN_TRACK_X)
                val curY = info.get(ValueLayout.JAVA_INT, OFFSET_MIN_TRACK_Y)
                val targetX = maxOf(curX, minWidthPx)
                val targetY = maxOf(curY, minHeightPx)
                if (targetX != curX) info.set(ValueLayout.JAVA_INT, OFFSET_MIN_TRACK_X, targetX)
                if (targetY != curY) info.set(ValueLayout.JAVA_INT, OFFSET_MIN_TRACK_Y, targetY)
            }
            return MemorySegment.NULL
        }
        return result
    }
}
