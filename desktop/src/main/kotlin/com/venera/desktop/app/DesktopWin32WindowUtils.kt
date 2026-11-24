package com.venera.desktop.app

import java.awt.Frame
import java.awt.Rectangle
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout

private const val GWL_EXSTYLE = -20
private const val SWP_NOSIZE = 0x0001
private const val SWP_NOMOVE = 0x0002

/**
 * 拿 HWND 的两条路，本探针走**第二条**：
 * 1. ~~`sun.awt.AWTAccessor` → `WComponentPeer.getHashCode()`~~ —— 实测**要走不通**：
 *    accessor 的运行期类是 `java.awt` 包里的匿名实现（`java.awt.Component$1`），
 *    该类所在的包 java.desktop **不导出**，反射直接 `IllegalAccessException`；
 *    而 `sun.awt.ComponentAccessor` 这个接口名在 JDK 21 里**已不存在**（`ClassNotFoundException`）。
 *    要修得靠 `--add-opens java.desktop/java.awt=ALL-UNNAMED`，且绑死 JDK 内部结构。
 * 2. **`user32.FindWindowW(null, title)`** —— 零 JDK 内部依赖、零 add-opens，代价是标题要唯一。
 *    ⚠️ 必须是 **W（UTF-16）** 版：`FindWindowA` 走 ANSI 码页，**带中文的标题匹配不到**，
 *    实测返回 0 —— 下游若拿 AWT bounds 兜底就会把桌面壁纸当成窗口截（本轮废掉三张图）。
 */
internal fun findHwnd(title: String): Long? {
    println("S06a_AWT Frame 标题=${Frame.getFrames().map { it.title }}")
    return runCatching {
        Arena.ofConfined().use { arena ->
            val fn = SymbolLookup.libraryLookup("user32", arena).find("FindWindowW").map {
                Linker.nativeLinker().downcallHandle(
                    it,
                    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS),
                )
            }.orElse(null) ?: return null
            // JDK 21 的 SegmentAllocator 既没有 `allocateUtf16String`，`allocateFrom(JAVA_CHAR, s)`
            // 也还没到位 —— 手工分配 UTF-16 缓冲区，跨 21/22/23 都能编
            val wTitle = arena.allocate((title.length + 1) * 2L)
            title.forEachIndexed { i, ch -> wTitle.set(ValueLayout.JAVA_CHAR, i * 2L, ch) }
            wTitle.set(ValueLayout.JAVA_CHAR, title.length * 2L, Char(0))
            val hwnd = (fn.invokeExact(MemorySegment.NULL, wTitle) as MemorySegment).address()
            if (hwnd == 0L) null else hwnd
        }
    }.getOrElse {
        println("S06a_HWND FAIL ${it::class.java.simpleName}: ${it.message?.take(200)}")
        null
    }
}

internal fun screenRectOf(hwnd: Long): Rectangle? = runCatching {
    Arena.ofConfined().use { arena ->
        val fn = SymbolLookup.libraryLookup("user32", arena).find("GetWindowRect").map {
            Linker.nativeLinker().downcallHandle(
                it,
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS),
            )
        }.orElse(null) ?: return null
        // RECT = 4 个 LONG(32 位)：必须按**字节数**分配，别用 allocate(JAVA_INT, 16) —— 那会被当成 int 初值
        val rect = arena.allocate(16L)
        val ok = fn.invokeExact(MemorySegment.ofAddress(hwnd), rect) as Int
        if (ok == 0) return null
        val l = rect.get(ValueLayout.JAVA_INT, 0)
        val t = rect.get(ValueLayout.JAVA_INT, 4)
        val r = rect.get(ValueLayout.JAVA_INT, 8)
        val b = rect.get(ValueLayout.JAVA_INT, 12)
        Rectangle(l.toInt(), t.toInt(), (r - l).toInt(), (b - t).toInt())
    }
}.getOrElse {
    println("S06a_矩形 FAIL ${it::class.java.simpleName}: ${it.message?.take(140)}")
    null
}

/**
 * 该点最前面的窗口是不是我们这扇（或其**祖先链上的任一子窗**）。
 * **锁屏 / 切到别的虚拟桌面时，`Robot` 照样能截出一张纯壁纸**，而 Win32 的 visible 位仍是 true
 * —— 只有这一招能识破，所以自截图前必须先过这道闸。
 *
 * ⚠️ 这一颗原来**只走 `GetParent` 一层**：CMP 桌面窗的画布嵌得更深
 * （Frame → 画布 → Skia 面板那种形状），于是"其实是我们这扇窗"会被判成"不是"，
 * **每一张取证图都会被这道闸拒掉**（抬成 topmost 之后仍然拒）。
 * 现在沿父链往上走，上限 [ANCESTOR_WALK_LIMIT] 层（防环；真到上限也没到本窗就是不在）。
 */
internal fun isPointOnWindow(hwnd: Long, x: Int, y: Int): Boolean {
    val at = windowAtPoint(x, y) ?: return false
    if (at == hwnd) return true
    var current = at
    repeat(ANCESTOR_WALK_LIMIT) {
        current = windowParent(current) ?: return false
        if (current == hwnd) return true
    }
    return false
}

/** 父链走几层就罢：环与畸形形状都按"不在本窗"处理，不硬凑成"在" */
private const val ANCESTOR_WALK_LIMIT = 6

/** `WS_EX_TOPMOST`：抬窗那颗位。回读它才算数，"我调用了 SetWindowPos" 不算 */
private const val WS_EX_TOPMOST = 0x8L

private const val HWND_TOPMOST_VALUE = -1L
private const val HWND_NOTOPMOST_VALUE = -2L
// SWP_NOSIZE / SWP_NOMOVE 这颗文件顶部就已声明（`addNoRedirectionBitmap` 在用），不重复登记
private const val SWP_NOACTIVATE = 0x10

/**
 * 把这扇窗抬成（或落回）topmost，返回**一句带证据的读数**。
 *
 * 为什么不能用 AWT 的 `setAlwaysOnTop` 就算数（实测）：AWT 那头报
 * `isAlwaysOnTop=true 支持=true visible=true`，而 `WindowFromPoint` 仍认用户的 Chrome 窗 ——
 * `SunAwtFrame` 上那一层样式**没落到 Win32**。所以这里直接 `SetWindowPos(HWND_TOPMOST)`，
 * 并且**回读 `GWL_EXSTYLE` 的 0x8 位**写进读数：抬窗这件事要么有位要么没有，不接受"我调用了"。
 *
 * 只带 `SWP_NOSIZE|SWP_NOMOVE|SWP_NOACTIVATE`：取证不需要改尺寸位置，而**不抢焦点**是刻意的 ——
 * 抢前台已经是用户批准的例外，再顺手抢输入焦点就是另一件事了。
 */
internal fun setTopmost(hwnd: Long?, on: Boolean): String {
    if (hwnd == null) return "hwnd 取不到，未抬"
    return runCatching {
        Arena.ofConfined().use { arena ->
            val u = SymbolLookup.libraryLookup("user32", arena)
            val get = u.find("GetWindowLongPtrW").map {
                Linker.nativeLinker().downcallHandle(
                    it,
                    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT),
                )
            }.orElse(null) ?: return "GetWindowLongPtrW 取不到"
            val pos = u.find("SetWindowPos").map {
                Linker.nativeLinker().downcallHandle(
                    it,
                    FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT,
                    ),
                )
            }.orElse(null) ?: return "SetWindowPos 取不到"
            val h = MemorySegment.ofAddress(hwnd)
            val old = (get.invokeExact(h, GWL_EXSTYLE) as MemorySegment).address()
            val moved = pos.invokeExact(
                h,
                MemorySegment.ofAddress(if (on) HWND_TOPMOST_VALUE else HWND_NOTOPMOST_VALUE),
                0, 0, 0, 0,
                SWP_NOSIZE or SWP_NOMOVE or SWP_NOACTIVATE,
            ) as Int
            val back = (get.invokeExact(h, GWL_EXSTYLE) as MemorySegment).address()
            "on=$on SetWindowPos=$moved（非 0 才算生效）位已上=${(back and WS_EX_TOPMOST) != 0L} " +
                "exstyle 0x${old.toString(16)}→0x${back.toString(16)}"
        }
    }.getOrElse { "抬窗抛了 ${it::class.java.simpleName}: ${it.message?.take(100)}" }
}

/** `GetWindowThreadProcessId` 的形状：`UINT (HWND, LPDWORD)` */
private val GWTPID_DESC = FunctionDescriptor.of(
    ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
)

/** `GetCurrentThreadId` 的形状：`DWORD ()` */
private val NO_ARG_DESC = FunctionDescriptor.of(ValueLayout.JAVA_INT)

/** `SetForegroundWindow` / `BringWindowToTop` 共用：`BOOL (HWND)` */
private val ONE_HWND_TO_BOOL_DESC = FunctionDescriptor.of(
    ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
)

/** 强顶后等合成器落定的那一下（与抬窗那次同量级，别拿旧 z-order 截图） */
private const val FORCE_TOP_WAIT_MS = 200L

/**
 * 取证专用：把本窗**强制顶到屏幕最前**，供自截图抢回中心点。返回一句带证据的读数，不是布尔。
 *
 * 为什么 [setTopmost] 抬过之后中心点仍被别家窗占着（实测）：本窗**早就挂着
 * `WS_EX_TOPMOST`**（exstyle 回读 `0x108→0x108`，那 `0x8` 位从头就在），而 `SetWindowPos(HWND_TOPMOST)`
 * 只保证"位在上"、不会把已在 topmost 带里的窗**重排到带顶**；用户的 Chrome 那扇也在同一带且压在上方，
 * `WindowFromPoint` 于是命中它。⇒ 需要的不是"再上一次位"，而是**在带内显式重排到顶**。
 *
 * 两手，由浅入深，读数反映真走到哪一手：
 *  ① `SetWindowPos(HWND_TOPMOST, SWP_NOSIZE|SWP_NOMOVE|SWP_NOACTIVATE)` —— 带内强制重排到顶，不抢焦点。
 *     回读中心点 [`check`] 若已落在本窗就成了，多数遮挡这一手够。
 * ② ① 仍压不过时，才走的"强制顶置"那一手：`AttachThreadInput` 把本线程**附到前台窗的线程**
 *     以绕过系统的 foreground lock，再 `SetForegroundWindow`+`BringWindowToTop`，收尾必 `detach`。
 *     这是 VeneraDesktop 那条注释从前判"不做"的一手，主人本轮明确批准，故实现之；⚠️ **不调 `requestFocus`**，
 *     抢的是 z-order/前台权，不是把用户正在打字的键盘焦点夺走。
 *
 * @param check 回读判据（通常是"中心点现在是不是本窗"），用它决定要不要走第二手 —— 不拿"我调用了"当成功。
 */
internal fun forceTopmostBand(hwnd: Long?, check: () -> Boolean): String {
    if (hwnd == null) return "hwnd 取不到，未强顶"
    return runCatching {
        Arena.ofConfined().use { arena ->
            val u = SymbolLookup.libraryLookup("user32", arena)
            val k = SymbolLookup.libraryLookup("kernel32", arena)
            val pos = u.find("SetWindowPos").map {
                Linker.nativeLinker().downcallHandle(
                    it,
                    FunctionDescriptor.of(
                        ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                    ),
                )
            }.orElse(null) ?: return "SetWindowPos 取不到"
            val h = MemorySegment.ofAddress(hwnd)
            val moved = pos.invokeExact(
                h, MemorySegment.ofAddress(HWND_TOPMOST_VALUE),
                0, 0, 0, 0, SWP_NOSIZE or SWP_NOMOVE or SWP_NOACTIVATE,
            ) as Int
            Thread.sleep(FORCE_TOP_WAIT_MS)
            if (check()) return "带内重排 SetWindowPos=$moved（非 0 才算生效）→ 中心点已归本窗"
            // ① 压不过：走的 AttachThreadInput 那一手绕 foreground lock。
            val fg = u.find("GetForegroundWindow").map {
                Linker.nativeLinker().downcallHandle(
                    it, FunctionDescriptor.of(ValueLayout.ADDRESS),
                )
            }.orElse(null) ?: return "带内重排=$moved 仍未压过，且 GetForegroundWindow 取不到"
            val fgHwnd = (fg.invokeExact() as MemorySegment).address()
            val gtpid = u.find("GetWindowThreadProcessId").map {
                Linker.nativeLinker().downcallHandle(it, GWTPID_DESC)
            }.orElse(null) ?: return "带内重排=$moved 仍未压过，且 GetWindowThreadProcessId 取不到"
            val ctid = k.find("GetCurrentThreadId").map {
                Linker.nativeLinker().downcallHandle(it, NO_ARG_DESC)
            }.orElse(null) ?: return "带内重排=$moved 仍未压过，且 GetCurrentThreadId 取不到"
            val attach = u.find("AttachThreadInput").map {
                Linker.nativeLinker().downcallHandle(
                    it,
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT),
                )
            }.orElse(null) ?: return "带内重排=$moved 仍未压过，且 AttachThreadInput 取不到"
            val sfg = u.find("SetForegroundWindow").map {
                Linker.nativeLinker().downcallHandle(it, ONE_HWND_TO_BOOL_DESC)
            }.orElse(null) ?: return "带内重排=$moved 仍未压过，且 SetForegroundWindow 取不到"
            val bwt = u.find("BringWindowToTop").map {
                Linker.nativeLinker().downcallHandle(it, ONE_HWND_TO_BOOL_DESC)
            }.orElse(null) ?: return "带内重排=$moved 仍未压过，且 BringWindowToTop 取不到"
            val pidBuf = arena.allocate(ValueLayout.JAVA_INT, 1)
            val otherTid = gtpid.invokeExact(MemorySegment.ofAddress(fgHwnd), pidBuf) as Int
            val ownTid = ctid.invokeExact() as Int
            val attached = attach.invokeExact(ownTid, otherTid, 1) as Int
            // 必须在 finally 中 detach，且每次 invokeExact 必须显式接收返回值以匹配签名多态句柄；-1 表示未执行。
            var setFg = -1
            var brought = -1
            var detached = -1
            var foregroundThrew: String? = null
            try {
                setFg = sfg.invokeExact(h) as Int
                brought = bwt.invokeExact(h) as Int
            } catch (err: Throwable) {
                // 收进读数而不是让它逃出去：抛了的话外层 getOrElse 只会留一句异常名，
                // 取证就看不见"到底有没有抢回来"这一句 —— 而那才是主人真正要的那格。
                foregroundThrew = "${err::class.java.simpleName}:${err.message?.take(60)}"
            } finally {
                if (attached != 0) detached = attach.invokeExact(ownTid, otherTid, 0) as Int
            }
            Thread.sleep(FORCE_TOP_WAIT_MS)
            "带内重排=$moved 不够 → Attach=$attached SetFg=$setFg BringTop=$brought detach=$detached" +
                (foregroundThrew?.let { " 前台那跳抛了$it" } ?: "") +
                "（附前台窗 hwnd=$fgHwnd 的线程后必 detach；-1=没走到那一步）→ 中心点归本窗=${check()}"
        }
    }.getOrElse { "强顶抛了 ${it::class.java.simpleName}: ${it.message?.take(100)}" }
}

/** `WindowFromPoint`：那点最前面的那扇窗（返回 0 或取不到都算 `null`） */
internal fun windowAtPoint(x: Int, y: Int): Long? = runCatching {
    Arena.ofConfined().use { arena ->
        val fn = SymbolLookup.libraryLookup("user32", arena).find("WindowFromPoint").map {
            Linker.nativeLinker().downcallHandle(
                it,
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT),
            )
        }.orElse(null) ?: return null
        val hwnd = (fn.invokeExact(x, y) as MemorySegment).address()
        if (hwnd == 0L) null else hwnd
    }
}.getOrElse {
    println("S06a_WindowFromPoint FAIL ${it::class.java.simpleName}: ${it.message?.take(140)}")
    null
}

/** `GetParent`：直接父窗（顶层窗没有父窗，取不到与没有都回 `null`） */
internal fun windowParent(hwnd: Long): Long? = runCatching {
    Arena.ofConfined().use { arena ->
        val fn = SymbolLookup.libraryLookup("user32", arena).find("GetParent").map {
            Linker.nativeLinker().downcallHandle(
                it,
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS),
            )
        }.orElse(null) ?: return null
        val parent = (fn.invokeExact(MemorySegment.ofAddress(hwnd)) as MemorySegment).address()
        if (parent == 0L) null else parent
    }
}.getOrElse {
    println("S06a_GetParent FAIL ${it::class.java.simpleName}: ${it.message?.take(140)}")
    null
}

/**
 * 那扇窗的**标题与类名**，只为诊断。原来拒图那句写的是"多半已锁屏/切到别的虚拟桌面"—— 那是**猜**的；
 * 取证时要的是"中心点到底被谁占着"。取不到就照实说取不到，不拿"未知"冒充"我们这扇"。
 */
internal fun windowDescribe(hwnd: Long): String = runCatching {
    Arena.ofConfined().use { arena ->
        val u = SymbolLookup.libraryLookup("user32", arena)
        // arena.allocate 是零填充的，所以 GetWindowTextW 没写满时 getUtf16String 也会在 NUL 处收住
        val buf = arena.allocate(2L * 512)
        fun text(of: String): String {
            val fn = u.find(of).map {
                Linker.nativeLinker().downcallHandle(
                    it,
                    FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT,
                    ),
                )
            }.orElse(null) ?: return "?"
            val n = fn.invokeExact(MemorySegment.ofAddress(hwnd), buf, 512) as Int
            return if (n <= 0) {
                ""
            } else {
                // 手工按 UTF-16 读：`getUtf16String` 在这套编译 JDK 上不解析（实测报 Unresolved reference），
                // 而 `get(ValueLayout.JAVA_CHAR, offset)` 是各版本都有的最低公分母
                val sb = StringBuilder(n)
                var i = 0
                while (i < n) {
                    val ch = buf.get(ValueLayout.JAVA_CHAR, i * 2L)
                    if (ch.code == 0) break
                    sb.append(ch)
                    i++
                }
                sb.toString()
            }
        }
        "标题「${text("GetWindowTextW")}」类名「${text("GetClassNameW")}」"
    }
}.getOrElse { "描述取不到（${it::class.java.simpleName}）" }
