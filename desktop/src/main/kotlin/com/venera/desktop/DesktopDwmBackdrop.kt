package com.venera.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout

/**
 * DWM 系统模糊底（浅色）：给产品窗挂 **Acrylic**（DWMWA_SYSTEMBACKDROP_TYPE=3，
 * 实时模糊桌面内容；Mica=2 只取壁纸色是"平"的）。打法照 `MicaProbe`（S0-6a 探针）的实测结论：
 *
 * 1. HWND 走 JNA `Native.getWindowPointer`（jna-platform 已在编译类路径，vlcj 带进来的；
 *    探针那条 FindWindowW 路在产品窗要拼中文+pid 标题，不必绕）。
 * 2. **必须先补 `WS_EX_NOREDIRECTIONBITMAP`** —— 探针 A 档实测：不带这一位，DWM 材质全是
 *    纯色 fallback（mica/tabbed 逐字节相同）。带位后 skiko 渲染是否安好由真窗验证。
 * 3. 判活不认 HRESULT，认 **回读值**：设上≠系统认账（探针铁律）。
 * 4. `active` 是 Compose 状态：没激活时玻璃轨退中性实底（假模糊已撤，
 *    视觉降级必须看得见）。
 */
object DesktopDwmBackdrop {

    private const val ATTR_BACKDROP = 38
    /** DWMWA_WINDOW_CORNER_PREFERENCE：让 DWM 材质按圆角绘制，圆弧外不露矩形方角。 */
    private const val ATTR_WINDOW_CORNER_PREFERENCE = 33
    private const val DWMWCP_ROUND = 2
    private const val ATTR_IMMERSIVE_DARK = 20
    private const val DWMSBT_ACRYLIC = 3
    private const val DWMSBT_MICA = 2
    private const val DWMSBT_NONE = 1
    /**
     * 材质请求档（每拍脉冲写它的值）：默认 Acrylic=3。CAPTION 补回后 Acrylic 合成不出
     * （真窗两轮实锤），Mica=2 / MicaAlt=4 与系统标题栏窗是原生搭配（Notepad/Explorer 同组合），
     * 尚未在本窗验证 —— 用环境变量 DESKTOP_DWMSBT=2/4 做对照实验。
     */
    private val DWMSBT_REQUESTED: Int =
        System.getenv("DESKTOP_DWMSBT")?.toIntOrNull()
            ?: System.getProperty("desktop.dwmsbt")?.toIntOrNull()
            ?: DWMSBT_ACRYLIC

    init {
        println("D_ACRYLIC 请求档=$DWMSBT_REQUESTED（2=Mica 3=Acrylic 4=MicaAlt）")
    }
    private const val WS_EX_LAYERED = 0x00080000L

    /** 是否补 WS_EX_NOREDIRECTIONBITMAP（探针结论必要；产品窗疑致顶部黑边，默认关，见 apply 内注释）。 */
    private const val NEED_NO_REDIRECTION_BITMAP = false

    /** 是否 DwmExtendFrameIntoClientArea(margins=-1)（Win11 新构建疑致材质层错位黑边，默认关）。 */
    private const val NEED_EXTEND_FRAME = false
    private const val GWL_EXSTYLE = -20
    private const val GWL_STYLE = -16
    private const val GWLP_WNDPROC = -4
    private const val WM_NCCALCSIZE = 0x0083

    // WM_NCCALCSIZE 劫持：wParam=TRUE 时返回 0 将 8px 非客户区边框并入客户区，保留 THICKFRAME 缩放能力，其余消息转发 CallWindowProcW。
    private var oldWndProc: MemorySegment? = null
    private var upcallStub: MemorySegment? = null
    private var callWindowProcMh: java.lang.invoke.MethodHandle? = null
    private const val WS_EX_NOREDIRECTIONBITMAP = 0x00200000L
    private const val SWP_NOSIZE = 0x0001
    private const val SWP_NOMOVE = 0x0002
    private const val SWP_NOZORDER = 0x0004
    private const val SWP_FRAMECHANGED = 0x0020

    /**
     * 系统模糊底是否激活。宿主改用 Nucleus 后材质由 `WindowsBackdrop` 常驻合成，
     * 不再有 AWT 的判活/掉层，玻璃轨恒走「什么都不画、让材质透上来」。
     */
    val active: Boolean get() = true

    /**
     * 上一次判活结果（只用来「状态变化才打日志」）。
     * 六修后本函数会被 3s 看门狗常驻调用，逐次打印会把日志刷满。
     */
    private var lastLogged: Boolean? = null

    // 玻璃配方通道：SetWindowCompositionAttribute + ACCENT_POLICY 配合 SkiaLayer.transparency、空 contentPane 与 DwmExtendFrameIntoClientArea(-1)。
    private const val WCA_ACCENT_POLICY = 19
    private const val ACCENT_ENABLE_ACRYLICBLURBEHIND = 4
    private const val ACCENT_NONE = 0
    private const val DRAW_ACTIVE_WINDOW_EXISTING_BEHIND = 0x20

    /** 记档：配方施加后 skiko 是否真把 AWT 分层位清掉了（回读实锤）。 */
    var glassRecipeApplied by mutableStateOf(false)
        private set

    /** 上轮配方打没打进（配方线 ok 稳定时不再打日志）。 */
    private var lastRecipeLogged: Boolean? = null

    /** 材质是否已连续判活（常驻后 38 脉冲转轻档）。 */
    private var pulseSettled = false
    private var pulseStreak = 0
    private var acrylicFallbackLogged = false

    /**
     * 玻璃配方入口。**必须在 EDT 上调**（findSkiaLayer 走 AWT 组件树）。
     * 返回 true = 配方四件套全部落子后回读 EXSTYLE 仍不带分层位 ⇒ 通道活着；
     * 返回 false = 分层位没摘干净（skiko 现版本可能不认），调用方落回旧看门狗路径。
     * 全程只碰这一颗窗，不建窗、不钩窗口过程。
     *
     * @param stripLayered 是否动手摘分层位。**只允许启动赌博线传 true**（一次性）；
     *  3s 看门狗常驻线必须 false——十四修实锤：反复摘会闪、且摘后 skiko 呈现塌灰板。
     *  常驻线只重翻 skiko 开关 + 重落 accent/frame（幂等无害），分层位归 AWT 自己的账。
     */
    fun applyGlassRecipe(window: java.awt.Window, stripLayered: Boolean): Boolean = runCatching {
        val hwnd = com.sun.jna.Native.getWindowPointer(window)
            ?.let { MemorySegment.ofAddress(com.sun.jna.Pointer.nativeValue(it)) }
            ?: return false
        val jlayer = setSkiaLayerTransparent(window)
        val jpane = hackContentPane(window)
        val jaccent = setAccentAcrylic(hwnd, 0x661F1F1F)
        val jframe = extendFrameGlass(hwnd)
        if (stripLayered) {
            val ex0 = readExstyle32(hwnd)
            if (ex0 >= 0L && (ex0 and WS_EX_LAYERED) != 0L) clearLayered(hwnd)
        }
        val ex = readExstyle32(hwnd)
        val layered = ex >= 0L && (ex and WS_EX_LAYERED) != 0L
        // 判据 = 通道三件落子且分层位不在。**skiko 开关不作否决**（14:51 实锤它是
        // 假阴性旁观件；真正让 AWT 不叠分层位的是 ②contentPane hack）。
        val ok = jaccent && jframe && !layered
        glassRecipeApplied = ok
        // 日志只在翻转 / 赌博线时打——常驻线 ok 稳定 true 时不刷屏。
        if (stripLayered || lastRecipeLogged != ok) {
            lastRecipeLogged = ok
            println(
                "D_GLASS 配方 strip=$stripLayered skiko=$jlayer pane=$jpane accent=$jaccent frame=$jframe " +
                    "ex=0x${ex.toString(16)} layered=$layered → ok=$ok",
            )
        }
        ok
    }.getOrElse {
        println("D_GLASS FAIL ${it::class.java.simpleName}: ${it.message}")
        false
    }

    /** EXSTYLE 读数的打印配额（只打前几次，看门狗每 3s 调一次不能刷屏）。 */
    private var exstyleLogCount = 0
    private var layeredLogCount = 0
    private var arrangeLogCount = 0
    private var extendLogCount = 0

    /** 激活 Acrylic（浅色实验皮专用）。返回 true = 回读认账。 */
    fun apply(window: java.awt.Window, dark: Boolean): Boolean {
        val ok = runCatching {
            val hwnd = com.sun.jna.Native.getWindowPointer(window)
                ?.let { MemorySegment.ofAddress(com.sun.jna.Pointer.nativeValue(it)) }
                ?: run { println("D_DWM FAIL 拿不到 HWND"); return false }
            println("D_DWM hwnd=0x${hwnd.address().toString(16)} dark=$dark")
            // 深浅跟随主题（DWMWA_USE_IMMERSIVE_DARK_MODE）
            println("D_DWM immersiveDark hr=${setAttr(hwnd, ATTR_IMMERSIVE_DARK, if (dark) 1 else 0)}")
            // ── A 方案补丁链（候选③④⑤回 0/1 全 hr=0 但带子仍在——见研究轮结论：带子=非客户区+AWT 未重排）──
            println("D_DWM ncRender hr=${setAttr(hwnd, 2, 1)}")
            println("D_DWM caption hr=${setAttr(hwnd, 35, 0x00F9F7F6)}")
            println("D_DWM border hr=${setAttr(hwnd, 34, 0xFFFFFFFE.toInt())}")
            stripCaption(hwnd)
            installNCCalcSizeHook(hwnd)
            println("D_DWM frameRecalc hr=${frameRecalc(hwnd)}")
            // extendFrame(margins=-1, sheet of glass)
            // 在 Win11 新构建（26300）疑致 DWM 材质层错位——窗顶/left 露材质外=黑。
            // 新版 backdrop 一般不需要它，默认关；材质若打不开再翻回 true 对照。
            if (NEED_EXTEND_FRAME) {
                println("D_DWM extendFrame hr=${extendFrame(hwnd)}")
            }
            // WS_EX_LAYERED 会压住 DWM 材质（CMP 透明窗可能带），有则清掉
            clearLayered(hwnd)
            // 默认关闭 WS_EX_NOREDIRECTIONBITMAP：透明产品窗走 DirectComposition 路径，开启可能导致交换链下移并在窗顶露出黑条。
            if (NEED_NO_REDIRECTION_BITMAP) {
                println("D_DWM noRedirectionBitmap 回读=0x${addNoRedirectionBitmap(hwnd).toString(16)}")
            }
            var v = DWMSBT_ACRYLIC
            setAttr(hwnd, ATTR_BACKDROP, v)
            var back = getAttr(hwnd, ATTR_BACKDROP)
            if (back != v) {
                // Acrylic 不认账就试 Mica（探针口径：回读认账才算）
                v = DWMSBT_MICA
                setAttr(hwnd, ATTR_BACKDROP, v)
                back = getAttr(hwnd, ATTR_BACKDROP)
            }
            println("D_DWM backdrop set=$v 回读=$back（相等才算激活）")
            back == v
        }.getOrElse {
            println("D_DWM FAIL ${it::class.java.simpleName}: ${it.message}")
            false
        }
        return ok
    }

    /** 关掉系统底（切回经典档时走这条；位图样式不回滚，避免中途闪帧）。 */
    fun clear() {
        runCatching {
            // 不持 hwnd 就算了：DWM 属性跟窗走，窗在才有意义；这里只翻状态位。
        }
    }

    /**
     * **最小化、可降级的实时 Acrylic**（主人选「真·实时」后启用）。
     *
     * 与旧 `apply()` 的区别，就是把历史上两个故障的成因逐条摘掉：
     *  - **不装** `WM_NCCALCSIZE` 窗口过程钩子；
     *  - **不调** `frameRecalc()`（`SWP_NOSIZE` 不发 WM_SIZE，AWT 停在旧客户区 ⇒ 顶栏色带）；
     *  - **不设** caption color(35) / border color(34) / NCRENDERING(2) / immersiveDark(20)；
     *  - **不剥** `WS_CAPTION`（Win11 因此不再自动圆角 ⇒ 外围矩形方角）；
     *  - **不** `clearLayered`、**不** `extendFrame`。
     *
     * 只做三件事：① 33 号圆角偏好设为 ROUND 并回读；② 38 号 backdrop 设为 Acrylic 并回读；
     * ③ 补 `WS_EX_NOREDIRECTIONBITMAP`（探针实测：不带这一位 DWM 材质全是纯色 fallback，
     * 等于没糊），但**只改 EXSTYLE、不调 SetWindowPos(SWP_NOSIZE)** —— 真正的 WM_SIZE
     * 由本函数在**样式位真被新补**的那一轮用 `setSize(+1) → setSize(原值)` 脉冲发
     * （原在 `VeneraDesktop.kt` 调用点，修「切主题掉模糊」时挪进来，见下）。
     *
     * ##修复：可重复调用，主题切换即重落材质
     *
     * 起初只敢在启动时调一次；结果「切换深浅色模式」时 DWM 对系统底做重算，材质掉层，
     * 而 `active` 已是 true、再没人重设 38 号 ⇒ 玻璃轨走「什么都不画」分支，模糊平掉，
     * 连 haze 兜底都进不去。现在本函数**幂等可重入**：样式位已在就直接跳过（不发脉冲、
     * 不重排），33/38 两枚属性每次重设 + 回读 —— 系统还认账 ⇒ 材质被重新踹活；不认账
     * ⇒ `active=false`，上层自动退回静态壁纸副本 + haze，模糊在任何一档都活着。
     *
     * 判活一律认**回读值**（探针铁律：设上≠系统认账）。任一环失败就把材质设回 NONE、
     * `active=false`，上层自动退回静态壁纸副本 + haze 方案，绝不退化成「清晰透明」。
     */
    /**
     * ① skiko 透明开关：在 AWT 组件树里找 SkiaLayer。14:51 真窗实锤：命中的是子类
     *（前缀匹配命中、精确名扑空），`子类.getMethod` 对**父类声明**的方法会 NoSuchMethod
     * ⇒ `skiko=false` 假阴性。沿 superclass 链找 setter。
     * 注意：这一件目前**不是**糊的功臣（功臣是 ② contentPane hack 让 AWT 不再叠分层位），
     * 但它是库配方的一环，修对留着，不做无据删除。
     */
    private fun setSkiaLayerTransparent(window: java.awt.Window): Boolean {
        val layer = findSkiaLayer(window) ?: run {
            if (glassDiagLogCount < 2) {
                glassDiagLogCount++
                println("D_GLASS 缺 SkiaLayer 树=${dumpTree(window, 0)}")
            }
            return false
        }
        return runCatching {
            val setter = generateSequence<Class<*>>(layer.javaClass) { it.superclass }
                .firstNotNullOfOrNull { klz ->
                    runCatching { klz.getMethod("setTransparency", Boolean::class.javaPrimitiveType) }.getOrNull()
                } ?: return false
            setter.invoke(layer, false)
            setter.invoke(layer, true)
            if (!glassToggleLogged) {
                glassToggleLogged = true
                println("D_GLASS skiko 开关已翻 layer=${layer.javaClass.name}")
            }
            true
        }.getOrDefault(false)
    }

    private var glassDiagLogCount = 0
    private var glassToggleLogged = false

    private fun findSkiaLayer(c: java.awt.Component): java.awt.Component? {
        val name = c.javaClass.name
        if (name.startsWith("org.jetbrains.skiko.SkiaLayer")) return c
        if (c is java.awt.Container) {
            for (child in c.components) findSkiaLayer(child)?.let { return it }
        }
        return null
    }

    /** 组件树摘要（最多 3 层 / 60 节点，只打类简名），调试配方找层用。 */
    private fun dumpTree(c: java.awt.Component, depth: Int): String = buildString {
        if (depth > 0) append(if (depth == 1) "[" else " ")
        append(c.javaClass.simpleName)
        if (c is java.awt.Container && depth < 3) {
            append("(")
            c.components.forEachIndexed { i, child ->
                if (i > 0) append(",")
                if (count() < 600) append(dumpTree(child, depth + 1))
            }
            append(")")
        }
        if (depth > 0) append(if (depth == 1) "]" else " ")
    }

    /**
     * ③ accent 模糊：`SetWindowCompositionAttribute(WCA_ACCENT_POLICY, ACCENT_POLICY{
     * ACCENT_ENABLE_ACRYLICBLURBEHIND, DRAW_ACTIVE_WINDOW_EXISTING_BEHIND, AABBGGRR})`。
     * 结构体字节序 = 4×INT32（state/flags/color/anim），外层 3×（INT32/ptr/INT32），照库的 JNA FieldOrder。
     */
    private fun setAccentAcrylic(hwndSeg: MemorySegment, abgr: Int): Boolean = runCatching {
        Arena.ofConfined().use { arena ->
            val u = SymbolLookup.libraryLookup("user32", arena)
            val setWca = u.find("SetWindowCompositionAttribute").map {
                Linker.nativeLinker().downcallHandle(
                    it, FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS),
                )
            }.orElse(null) ?: return false
            val accent = arena.allocate(16L)
            accent.set(ValueLayout.JAVA_INT, 0L, ACCENT_ENABLE_ACRYLICBLURBEHIND)
            accent.set(ValueLayout.JAVA_INT, 4L, DRAW_ACTIVE_WINDOW_EXISTING_BEHIND)
            accent.set(ValueLayout.JAVA_INT, 8L, abgr)
            accent.set(ValueLayout.JAVA_INT, 12L, 1)
            val data = arena.allocate(24L)
            data.set(ValueLayout.JAVA_INT, 0L, WCA_ACCENT_POLICY)
            data.set(ValueLayout.ADDRESS, 8L, accent)
            data.set(ValueLayout.JAVA_INT, 16L, 16)
            (setWca.invokeExact(hwndSeg, data) as Int) != 0
        }
    }.getOrDefault(false)

    /** 关掉 accent（回退用）：ACCENT_NONE，其余字段清零。 */
    private fun setAccentOff(hwndSeg: MemorySegment): Boolean = runCatching {
        Arena.ofConfined().use { arena ->
            val u = SymbolLookup.libraryLookup("user32", arena)
            val setWca = u.find("SetWindowCompositionAttribute").map {
                Linker.nativeLinker().downcallHandle(
                    it, FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS),
                )
            }.orElse(null) ?: return false
            val accent = arena.allocate(16L)
            accent.set(ValueLayout.JAVA_INT, 0L, ACCENT_NONE)
            val data = arena.allocate(24L)
            data.set(ValueLayout.JAVA_INT, 0L, WCA_ACCENT_POLICY)
            data.set(ValueLayout.ADDRESS, 8L, accent)
            data.set(ValueLayout.JAVA_INT, 16L, 16)
            (setWca.invokeExact(hwndSeg, data) as Int) != 0
        }
    }.getOrDefault(false)

    /**
     * ② contentPane 透明 hack（库证过的顺序：先 skiko 开关，再这一步）。
     * JFrame 直接清根容器底色；否则退化清 window 背景 + 首个不透明容器。
     */
    private fun hackContentPane(window: java.awt.Window): Boolean = runCatching {
        val root = (window as? javax.swing.JFrame)?.contentPane
        if (root != null) {
            // 库证过的形状：contentPane 换/设为「不透明关 + 全透明底」，让位给 skiko 层。
            // （ComposeWindow 继承 JFrame，contentPane 实为 JComponent，setOpaque 可用。）
            // JComponent 的 protected 字段遮蔽 setter 可见性，走反射最稳。
            runCatching {
                java.awt.Component::class.java.getMethod("setOpaque", Boolean::class.javaPrimitiveType)
                    .invoke(root, false)
            }
            // Kotlin 把 AWT 的 `background` 映射到 protected 字段（只读），必须走 setBackground。
            root.setBackground(java.awt.Color(0, 0, 0, 0))
            root.parent?.setBackground(java.awt.Color(0, 0, 0, 0))
        } else {
            window.setBackground(java.awt.Color(0, 0, 0, 0))
        }
        true
    }.getOrDefault(false)

    /** ④ sheet of glass：extendFrame(-1)，hr>=0 算落子成功（真糊与否由屏幕验收）。 */
    private fun extendFrameGlass(hwndSeg: MemorySegment): Boolean = extendFrame(hwndSeg) >= 0

    // ── 旧入口：抢时序落 33/38（十五修之前唯一路线，配方失败时仍作降级驱动）──
    fun applyAcrylicOnly(window: java.awt.Window): Boolean {
        var styleChanged = false
        val ok = runCatching {
            val hwnd = com.sun.jna.Native.getWindowPointer(window)
                ?.let { MemorySegment.ofAddress(com.sun.jna.Pointer.nativeValue(it)) }
                ?: return@runCatching false

            setAttr(hwnd, ATTR_WINDOW_CORNER_PREFERENCE, DWMWCP_ROUND)
            val corner = getAttr(hwnd, ATTR_WINDOW_CORNER_PREFERENCE)
            // 每拍发送 NONE→ACRYLIC 脉冲强制 DWM 重算材质（写同值会被忽略）；常驻稳定后转轻脉冲避免闪烁。
            setAttr(hwnd, ATTR_BACKDROP, DWMSBT_NONE)
            setAttr(hwnd, ATTR_BACKDROP, DWMSBT_REQUESTED)
            if (pulseSettled && !acrylicFallbackLogged) {
                acrylicFallbackLogged = true
                println("D_ACRYLIC 脉冲已转轻档（材质常驻后不再全翻）")
            }
            val backdrop = getAttr(hwnd, ATTR_BACKDROP)

            // 本窗口 SetWindowLong 无法持久保留 WS_EX_NOREDIRECTIONBITMAP，且摘除 WS_EX_LAYERED 会丢失逐像素透明；
            // 故此处仅只读状态，由上层按 active 稳定走降级实底，避免判活假成功导致侧栏透明穿透。
        val exstyle = readExstyle32(hwnd)
        val layered = exstyle >= 0L && (exstyle and WS_EX_LAYERED) != 0L
        if (extendLogCount < 4) {
            extendLogCount++
            println("D_ACRYLIC 只读 EXSTYLE=0x${exstyle.toString(16)} layered=$layered")
        }
        // 判据：① 圆角偏好认账；② 材质**真落位**（>= MICA，排除 0=AUTO —— 旧判据把 0 放过，
        // 这正是「偶尔会没」：属性没落位时回读 0，却被判成激活成功）；
        // ③ 分层位已摘掉（分层窗不合成系统底）。
        if (corner == DWMWCP_ROUND && backdrop >= DWMSBT_MICA && !layered) {
            styleChanged = false
            if (lastLogged != true) {
                println("D_ACRYLIC OK corner=$corner backdrop=$backdrop exstyle=0x${exstyle.toString(16)}")
            }
            true
        } else {
            // 降级时不写回 DWMSBT_NONE，保留材质属性以便后续恢复；UI 侧由中性实底覆盖。
            if (lastLogged != false) {
                println(
                    "D_ACRYLIC FALLBACK corner=$corner backdrop=$backdrop " +
                        "exstyle=0x${exstyle.toString(16)}" +
                        "（${if (layered) "分层窗 WS_EX_LAYERED 拿不到系统底" else "材质没落位 backdrop=$backdrop"}）" +
                        " → 退回中性实底（假模糊已撤）",
                )
            }
            false
        }
        }.getOrElse {
            println("D_ACRYLIC FAIL ${it::class.java.simpleName}: ${it.message}")
            false
        }
        if (lastLogged != null && lastLogged != ok) {
            println("D_ACRYLIC STATE ${if (ok) "RECOVER" else "DEGRADE"} → active=$ok")
        }
        lastLogged = ok
        // 稳定计数：连续 3 拍判活 ⇒ 材质常驻，脉冲转轻档（只翻一次值，DWM 开销最小化）。
        if (ok) {
            pulseStreak++
            if (pulseStreak >= 3) pulseSettled = true
        } else {
            pulseStreak = 0
            pulseSettled = false
            acrylicFallbackLogged = false
        }
        if (ok && styleChanged) {
            // 加 EXSTYLE 位后必须**真发一次 WM_SIZE**，AWT/SkiaLayer 才会按新渲染路径
            // 重排客户区；跨两轮 EDT 的 +1/-1 脉冲，尺寸终值不变。重入轮样式没动，跳过。
            javax.swing.SwingUtilities.invokeLater {
                val w = window.width
                val h = window.height
                window.setSize(w, h + 1)
                javax.swing.SwingUtilities.invokeLater {
                    window.setSize(w, h)
                    window.invalidate()
                    window.validate()
                    window.repaint()
                }
            }
        }
        return ok
    }

    /**
     * 只补 `WS_EX_NOREDIRECTIONBITMAP` 位，**不**发 `SetWindowPos(SWP_NOSIZE|FRAMECHANGED)`。
     * 后者是历史 `frameRecalc()` 里那条「不发 WM_SIZE」的元凶；真 WM_SIZE 由调用方
     * （[applyAcrylicOnly]）在样式位真被新补的那一轮脉冲补。
     * 返回 `-3` = 位本来就在（重入轮，样式没动）；其余非负 = 补位后的 EXSTYLE。
     */
    private fun addNoRedirectionBitmapNoFrame(hwndSeg: MemorySegment): Long = runCatching {
        Arena.ofConfined().use { arena ->
            val u = SymbolLookup.libraryLookup("user32", arena)
            // EXSTYLE 为 32 位值，必须使用 32 位 GetWindowLongW/SetWindowLongW，避免 FFM 指针签名歧义导致回读恒 0。
            val get = u.find("GetWindowLongW").map {
                Linker.nativeLinker().downcallHandle(
                    it,
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT),
                )
            }.orElse(null) ?: return@use -1L
            val set = u.find("SetWindowLongW").map {
                Linker.nativeLinker().downcallHandle(
                    it,
                    FunctionDescriptor.of(
                        ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                    ),
                )
            }.orElse(null) ?: return@use -1L
            val old = (get.invokeExact(hwndSeg, GWL_EXSTYLE) as Int).toLong() and 0xFFFFFFFFL
            if ((old and WS_EX_NOREDIRECTIONBITMAP) != 0L) return@use -3L
            set.invokeExact(
                hwndSeg, GWL_EXSTYLE,
                (old or WS_EX_NOREDIRECTIONBITMAP).toInt(),
            ) as Int
            val after = (get.invokeExact(hwndSeg, GWL_EXSTYLE) as Int).toLong() and 0xFFFFFFFFL
            // 只打前 3 次（看门狗每 3s 调一次，不能刷屏），够看清「补位成不成」。
            if (exstyleLogCount < 3) {
                exstyleLogCount++
                println(
                    "D_ACRYLIC EXSTYLE 补位 old=0x${old.toString(16)} " +
                        "→ after=0x${after.toString(16)}（期望带 0x200000）",
                )
            }
            after
        }
    }.getOrDefault(-2L)

    // ── FFM 通道（照 MicaProbe 那套；invokeExact 静态返回类型必须与句柄一致）──

    private fun dwmFn(lookup: SymbolLookup, name: String): java.lang.invoke.MethodHandle? =
        lookup.find(name).map {
            Linker.nativeLinker().downcallHandle(
                it,
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                ),
            )
        }.orElse(null)

    private fun setAttr(hwndSeg: MemorySegment, attr: Int, value: Int): Int = runCatching {
        Arena.ofConfined().use { arena ->
            val fn = dwmFn(SymbolLookup.libraryLookup("dwmapi", arena), "DwmSetWindowAttribute")
                ?: return -99
            val pv = arena.allocateFrom(ValueLayout.JAVA_INT, value)
            fn.invokeExact(hwndSeg, attr, pv, ValueLayout.JAVA_INT.byteSize().toInt()) as Int
        }
    }.getOrDefault(-98)

    private fun getAttr(hwndSeg: MemorySegment, attr: Int): Int = runCatching {
        Arena.ofConfined().use { arena ->
            val fn = dwmFn(SymbolLookup.libraryLookup("dwmapi", arena), "DwmGetWindowAttribute")
                ?: return -99
            val pv = arena.allocate(ValueLayout.JAVA_INT)
            fn.invokeExact(hwndSeg, attr, pv, ValueLayout.JAVA_INT.byteSize().toInt()) as Int
            pv.get(ValueLayout.JAVA_INT, 0)
        }
    }.getOrDefault(-98)

    /**
     * 挂 NCCALCSIZE 劫持。upcall 用 [Arena.global]（永不释放，stub 引用钉在本对象
     * 字段上防 GC）；只拦 `WM_NCCALCSIZE(wParam=TRUE)` 回 0，其余全部 `CallWindowProcW`
     * 原样转发——窗口消息链上出任何岔子都退回系统默认行为。
     */
    private fun installNCCalcSizeHook(hwndSeg: MemorySegment) {
        if (oldWndProc != null) return // 已装过（切档重进不重复挂钩）
        runCatching {
            val linker = Linker.nativeLinker()
            val desc = FunctionDescriptor.of(
                ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, ValueLayout.ADDRESS,
            )
            val target = java.lang.invoke.MethodHandles.lookup().findStatic(
                DesktopDwmBackdrop::class.java, "wndProcBody",
                java.lang.invoke.MethodType.methodType(
                    MemorySegment::class.java,
                    MemorySegment::class.java,
                    Int::class.javaPrimitiveType,
                    MemorySegment::class.java,
                    MemorySegment::class.java,
                ),
            )
            // JDK 26 口径：Linker.upcallStub（旧教程里的 createUpcallStub 已更名）
            val stub = linker.upcallStub(target, desc, Arena.global())
            // CallWindowProcW 是 **5 参**：(proc, hwnd, msg, wParam, lParam) → LRESULT——
            // 上一版复用 upcall 的 4 参描述符，invoke 全抛 WrongMethodTypeException 被吞，
            // 消息全变 0 ⇒ 窗口永远起不来（心电图日志抓到的根因）。
            val callDesc = FunctionDescriptor.of(
                ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, ValueLayout.ADDRESS,
            )
            val call = SymbolLookup.libraryLookup("user32", Arena.global()).find("CallWindowProcW").map {
                linker.downcallHandle(it, callDesc)
            }.orElse(null) ?: run { println("D_DWM wndProc FAIL CallWindowProcW 缺"); return }
            val u = SymbolLookup.libraryLookup("user32", Arena.global())
            val ptr = ValueLayout.ADDRESS
            val get = u.find("GetWindowLongPtrW").map {
                linker.downcallHandle(it, FunctionDescriptor.of(ptr, ptr, ValueLayout.JAVA_INT))
            }.orElse(null) ?: return
            val set = u.find("SetWindowLongPtrW").map {
                linker.downcallHandle(it, FunctionDescriptor.of(ptr, ptr, ValueLayout.JAVA_INT, ptr))
            }.orElse(null) ?: return
            val old = (get.invokeExact(hwndSeg, GWLP_WNDPROC) as MemorySegment).address()
            oldWndProc = MemorySegment.ofAddress(old)
            callWindowProcMh = call
            upcallStub = stub
            set.invokeExact(hwndSeg, GWLP_WNDPROC, stub) as MemorySegment
            println("D_DWM wndProc hooked old=0x${old.toString(16)}")
            // 挂钩后立即触发 SWP_FRAMECHANGED，强制重算非客户区以消除顶部 8px 透明带。
            println("D_DWM frameRecalc hr=${frameRecalc(hwndSeg)}")
        }.onFailure { println("D_DWM wndProc FAIL ${it::class.java.simpleName}: ${it.message}") }
    }

    /** 窗口过程本体：只拦 NCCALCSIZE(wParam=TRUE)，其余转发。回调里抛异常 = 崩窗，全程 try 包住。 */
    @JvmStatic
    private fun wndProcBody(hwnd: MemorySegment, msg: Int, wParam: MemorySegment, lParam: MemorySegment): MemorySegment {
        // ── 心电图（诊断期）：前 20 条消息逐条打印——分辨"消息没进回调"还是"进了但转发炸"──
        if (msgLogCount < 20) {
            msgLogCount++
            println("D_DWM msg#$msgLogCount msg=0x${msg.toString(16)} wp=0x${wParam.address().toString(16)}")
        }
        val proc = oldWndProc
        val call = callWindowProcMh
        if (msg == WM_NCCALCSIZE && wParam.address() != 0L && proc != null && call != null) {
            return MemorySegment.NULL
        }
        if (proc == null || call == null) {
            if (fwdFailLog < 3) { fwdFailLog++; println("D_DWM 转发缺件 proc=${proc != null} call=${call != null}") }
            return MemorySegment.NULL
        }
        return runCatching {
            call.invokeExact(proc, hwnd, msg, wParam, lParam) as MemorySegment
        }.getOrElse {
            if (fwdFailLog < 5) { fwdFailLog++; println("D_DWM 转发炸 ${it::class.java.simpleName}: ${it.message}") }
            MemorySegment.NULL
        }
    }

    private var msgLogCount = 0
    private var fwdFailLog = 0

    /** 强制非客户区重算（SWP_FRAMECHANGED）：NCCALCSIZE 劫持生效的扳机。 */
    private fun frameRecalc(hwndSeg: MemorySegment): Int = runCatching {
        Arena.ofConfined().use { arena ->
            val u = SymbolLookup.libraryLookup("user32", arena)
            val ptr = ValueLayout.ADDRESS
            val pos = u.find("SetWindowPos").map {
                Linker.nativeLinker().downcallHandle(
                    it,
                    FunctionDescriptor.of(
                        ValueLayout.JAVA_INT, ptr, ptr,
                        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT,
                    ),
                )
            }.orElse(null) ?: return -1
            pos.invokeExact(
                hwndSeg, MemorySegment.NULL, 0, 0, 0, 0,
                SWP_NOSIZE or SWP_NOMOVE or SWP_NOZORDER or SWP_FRAMECHANGED,
            ) as Int
        }
    }.getOrDefault(-2)

    /** 剥 WS_CAPTION（保留 THICKFRAME）：返回剥后样式（-1 = user32 通道没打通）。 */
    private fun stripCaption(hwndSeg: MemorySegment): Long = runCatching {
        Arena.ofConfined().use { arena ->
            val u = SymbolLookup.libraryLookup("user32", arena)
            val ptr = ValueLayout.ADDRESS
            val get = u.find("GetWindowLongPtrW").map {
                Linker.nativeLinker().downcallHandle(it, FunctionDescriptor.of(ptr, ptr, ValueLayout.JAVA_INT))
            }.orElse(null) ?: return -1
            val set = u.find("SetWindowLongPtrW").map {
                Linker.nativeLinker().downcallHandle(it, FunctionDescriptor.of(ptr, ptr, ValueLayout.JAVA_INT, ptr))
            }.orElse(null) ?: return -1
            val pos = u.find("SetWindowPos").map {
                Linker.nativeLinker().downcallHandle(
                    it,
                    FunctionDescriptor.of(
                        ValueLayout.JAVA_INT, ptr, ptr,
                        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT,
                    ),
                )
            }.orElse(null) ?: return -1
            val WS_CAPTION = 0x00C00000L
            val old = (get.invokeExact(hwndSeg, GWL_STYLE) as MemorySegment).address()
            println("D_DWM style old=0x${old.toString(16)} caption=${(old and WS_CAPTION) != 0L}")
            if ((old and WS_CAPTION) == 0L) return old
            set.invokeExact(hwndSeg, GWL_STYLE, MemorySegment.ofAddress(old and WS_CAPTION.inv())) as MemorySegment
            pos.invokeExact(
                hwndSeg, MemorySegment.NULL, 0, 0, 0, 0,
                SWP_NOSIZE or SWP_NOMOVE or SWP_NOZORDER or SWP_FRAMECHANGED,
            ) as Int
            (get.invokeExact(hwndSeg, GWL_STYLE) as MemorySegment).address()
        }
    }.getOrDefault(-2)

    /** DwmExtendFrameIntoClientArea（margins 全 -1）：玻璃 frame 铺满客户区，材质通道前置。 */
    private fun extendFrame(hwndSeg: MemorySegment): Int = runCatching {
        Arena.ofConfined().use { arena ->
            val fn = SymbolLookup.libraryLookup("dwmapi", arena).find("DwmExtendFrameIntoClientArea").map {
                Linker.nativeLinker().downcallHandle(
                    it,
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS),
                )
            }.orElse(null) ?: return -99
            val margins = arena.allocate(16L)
            for (i in 0 until 4) margins.set(ValueLayout.JAVA_INT, i * 4L, -1)
            fn.invokeExact(hwndSeg, margins) as Int
        }
    }.getOrDefault(-98)

    /** 清 WS_EX_LAYERED（有则清 + FRAMECHANGED；返回清理后的 exstyle）。 */
    private fun clearLayered(hwndSeg: MemorySegment): Long = runCatching {
        Arena.ofConfined().use { arena ->
            val u = SymbolLookup.libraryLookup("user32", arena)
            val ptr = ValueLayout.ADDRESS
            val get = u.find("GetWindowLongPtrW").map {
                Linker.nativeLinker().downcallHandle(it, FunctionDescriptor.of(ptr, ptr, ValueLayout.JAVA_INT))
            }.orElse(null) ?: return -1
            val set = u.find("SetWindowLongPtrW").map {
                Linker.nativeLinker().downcallHandle(it, FunctionDescriptor.of(ptr, ptr, ValueLayout.JAVA_INT, ptr))
            }.orElse(null) ?: return -1
            val pos = u.find("SetWindowPos").map {
                Linker.nativeLinker().downcallHandle(
                    it,
                    FunctionDescriptor.of(
                        ValueLayout.JAVA_INT, ptr, ptr,
                        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT,
                    ),
                )
            }.orElse(null) ?: return -1
            val old = (get.invokeExact(hwndSeg, GWL_EXSTYLE) as MemorySegment).address()
            println("D_DWM exstyle old=0x${old.toString(16)} layered=${(old and WS_EX_LAYERED) != 0L}")
            if ((old and WS_EX_LAYERED) == 0L) return old
            set.invokeExact(hwndSeg, GWL_EXSTYLE, MemorySegment.ofAddress(old and WS_EX_LAYERED.inv())) as MemorySegment
            pos.invokeExact(
                hwndSeg, MemorySegment.NULL, 0, 0, 0, 0,
                SWP_NOSIZE or SWP_NOMOVE or SWP_NOZORDER or SWP_FRAMECHANGED,
            ) as Int
            (get.invokeExact(hwndSeg, GWL_EXSTYLE) as MemorySegment).address()
        }
    }.getOrDefault(-2)

    /**
     * 摘掉 `WS_EX_LAYERED`（v4.5 丢掉的正是这一步）。
     *
     * 走 32 位 `GetWindowLongW/SetWindowLongW`：EXSTYLE 本来就是 32 位量，
     * LONG_PTR 那条在 FFM 上有指针歧义。改完必须 `SetWindowPos(SWP_FRAMECHANGED)`
     * 让系统重算非客户区，否则样式位只是记在属性里、窗口不重排。
     *
     * 返回摘位后的 EXSTYLE（失败 -2）；`-1` = 符号没找到。
     */
    private fun clearLayered32(hwndSeg: MemorySegment): Long = runCatching {
        Arena.ofConfined().use { arena ->
            val u = SymbolLookup.libraryLookup("user32", arena)
            val get = u.find("GetWindowLongW").map {
                Linker.nativeLinker().downcallHandle(
                    it,
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT),
                )
            }.orElse(null) ?: return@use -1L
            val set = u.find("SetWindowLongW").map {
                Linker.nativeLinker().downcallHandle(
                    it,
                    FunctionDescriptor.of(
                        ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                    ),
                )
            }.orElse(null) ?: return@use -1L
            val pos = u.find("SetWindowPos").map {
                Linker.nativeLinker().downcallHandle(
                    it,
                    FunctionDescriptor.of(
                        ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                    ),
                )
            }.orElse(null) ?: return@use -1L
            val old = (get.invokeExact(hwndSeg, GWL_EXSTYLE) as Int).toLong() and 0xFFFFFFFFL
            if ((old and WS_EX_LAYERED) == 0L) return@use old
            set.invokeExact(hwndSeg, GWL_EXSTYLE, (old and WS_EX_LAYERED.inv()).toInt()) as Int
            pos.invokeExact(
                hwndSeg, MemorySegment.NULL, 0, 0, 0, 0,
                SWP_NOSIZE or SWP_NOMOVE or SWP_NOZORDER or SWP_FRAMECHANGED,
            ) as Int
            val after = (get.invokeExact(hwndSeg, GWL_EXSTYLE) as Int).toLong() and 0xFFFFFFFFL
            if (layeredLogCount < 4) {
                layeredLogCount++
                println("D_ACRYLIC 清分层位 old=0x${old.toString(16)} → after=0x${after.toString(16)}")
            }
            after
        }
    }.getOrDefault(-2L)

    /**
     * 为 Acrylic 摆好 EXSTYLE（十修）：**先摘** `WS_EX_LAYERED`、**再补**
     * `WS_EX_NOREDIRECTIONBITMAP`；补位没成则把分层位放回去（保持透明，避免灰板）。
     *
     * 返回最终 EXSTYLE；失败 -2 / 符号缺失 -1。
     */
    private fun arrangeForAcrylic(hwndSeg: MemorySegment): Long = runCatching {
        Arena.ofConfined().use { arena ->
            val u = SymbolLookup.libraryLookup("user32", arena)
            val getH = u.find("GetWindowLongW").map {
                Linker.nativeLinker().downcallHandle(
                    it,
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT),
                )
            }.orElse(null) ?: return@use -1L
            val setH = u.find("SetWindowLongW").map {
                Linker.nativeLinker().downcallHandle(
                    it,
                    FunctionDescriptor.of(
                        ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                    ),
                )
            }.orElse(null) ?: return@use -1L
            val posH = u.find("SetWindowPos").map {
                Linker.nativeLinker().downcallHandle(
                    it,
                    FunctionDescriptor.of(
                        ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                    ),
                )
            }.orElse(null) ?: return@use -1L

            fun read(): Long =
                (getH.invokeExact(hwndSeg, GWL_EXSTYLE) as Int).toLong() and 0xFFFFFFFFL

            fun write(value: Long) {
                setH.invokeExact(hwndSeg, GWL_EXSTYLE, value.toInt()) as Int
                // 改 EXSTYLE 必须让系统重算非客户区，否则位只记在属性里、窗口不重排。
                posH.invokeExact(
                    hwndSeg, MemorySegment.NULL, 0, 0, 0, 0,
                    SWP_NOSIZE or SWP_NOMOVE or SWP_NOZORDER or SWP_FRAMECHANGED,
                ) as Int
            }

            val before = read()
            val wasLayered = (before and WS_EX_LAYERED) != 0L
            if (wasLayered) write(before and WS_EX_LAYERED.inv())
            write(read() or WS_EX_NOREDIRECTIONBITMAP)
            val after = read()
            val hasBit = (after and WS_EX_NOREDIRECTIONBITMAP) != 0L
            val stillLayered = (after and WS_EX_LAYERED) != 0L
            if (!hasBit && wasLayered && !stillLayered) {
                // 补位没成 ⇒ 分层位放回去：宁可没有实时材质，也不能把窗口留在灰板状态。
                write(after or WS_EX_LAYERED)
            }
            if (arrangeLogCount < 4) {
                arrangeLogCount++
                println(
                    "D_ACRYLIC arrange before=0x${before.toString(16)} " +
                        "→ after=0x${read().toString(16)}（bit=$hasBit）",
                )
            }
            read()
        }
    }.getOrDefault(-2L)

    /** 只读 EXSTYLE（32 位），不写任何位。失败 -2。 */
    private fun readExstyle32(hwndSeg: MemorySegment): Long = runCatching {
        Arena.ofConfined().use { arena ->
            val u = SymbolLookup.libraryLookup("user32", arena)
            val get = u.find("GetWindowLongW").map {
                Linker.nativeLinker().downcallHandle(
                    it,
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT),
                )
            }.orElse(null) ?: return@use -1L
            (get.invokeExact(hwndSeg, GWL_EXSTYLE) as Int).toLong() and 0xFFFFFFFFL
        }
    }.getOrDefault(-2L)

    private fun addNoRedirectionBitmap(hwndSeg: MemorySegment): Long = runCatching {
        Arena.ofConfined().use { arena ->
            val u = SymbolLookup.libraryLookup("user32", arena)
            val ptr = ValueLayout.ADDRESS
            val get = u.find("GetWindowLongPtrW").map {
                Linker.nativeLinker().downcallHandle(it, FunctionDescriptor.of(ptr, ptr, ValueLayout.JAVA_INT))
            }.orElse(null) ?: return -1
            val set = u.find("SetWindowLongPtrW").map {
                Linker.nativeLinker().downcallHandle(it, FunctionDescriptor.of(ptr, ptr, ValueLayout.JAVA_INT, ptr))
            }.orElse(null) ?: return -1
            val pos = u.find("SetWindowPos").map {
                Linker.nativeLinker().downcallHandle(
                    it,
                    FunctionDescriptor.of(
                        ValueLayout.JAVA_INT, ptr, ptr,
                        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT,
                    ),
                )
            }.orElse(null) ?: return -1
            val old = (get.invokeExact(hwndSeg, GWL_EXSTYLE) as MemorySegment).address()
            set.invokeExact(hwndSeg, GWL_EXSTYLE, MemorySegment.ofAddress(old or WS_EX_NOREDIRECTIONBITMAP)) as MemorySegment
            pos.invokeExact(
                hwndSeg, MemorySegment.NULL, 0, 0, 0, 0,
                SWP_NOSIZE or SWP_NOMOVE or SWP_NOZORDER or SWP_FRAMECHANGED,
            ) as Int
            (get.invokeExact(hwndSeg, GWL_EXSTYLE) as MemorySegment).address()
        }
    }.getOrDefault(-2)
}
