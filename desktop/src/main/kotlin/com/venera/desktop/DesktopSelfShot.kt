package com.venera.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import java.awt.Rectangle
import java.awt.Robot
import java.io.File
import javax.imageio.ImageIO
import kotlinx.coroutines.delay

/**
 * 自截图。**先验窗口真的在屏上再截**：上一版只按矩形抓，窗口被最小化时
 * 截到的是桌面壁纸（`_qa/desktop-{nhentai3,jm-reader4}.png` 就是这么废掉的），
 * 而日志照样打"截图成功"——那是伪证，不是证据。
 */
internal fun selfShot(path: String) {
    val frame = java.awt.Frame.getFrames().firstOrNull { it.title == APP_TITLE }
    if (frame == null || !frame.isVisible || frame.state == java.awt.Frame.ICONIFIED) {
        println(
            "D_截图 无效：窗口不在屏上（frame=${if (frame == null) "null" else "visible=${frame.isVisible} iconified=${frame.state == java.awt.Frame.ICONIFIED}"}）" +
                "—— 本次没有可读的证据图，读数只认 stdout"
        )
        return
    }
    // 仅在取证模式抬窗并恢复 topmost；窗口位置与可见性仅认 Win32 HWND，不信任关闭后仍报可见的 AWT Frame。
    val hwnd = findHwnd(APP_TITLE)
    try {
        println("D_截图 抬窗 ${raiseOwnWindowForShot(frame, hwnd)}")
        // 合成器要一拍才真的把这扇窗压到普通窗之上；这一段**不阻塞正式路径**（正式用户走不到这里）。
        // 不用协程的 delay 是因为 selfShot 不是 suspend，而取证这一跑截完就退出进程。
        Thread.sleep(RAISE_SETTLE_MS)
        runCatching {
            if (hwnd == null) {
                println("D_截图 无效：Win32 已查不到标题窗口（多半已被关掉），AWT 的 bounds 不可信")
                return
            }
            val rect = screenRectOf(hwnd) ?: run {
                println("D_截图 无效：GetWindowRect 失败")
                return
            }
            // 锁屏/切虚拟桌面时窗口矩形照样存在，截出来是纯壁纸 —— 先确认屏幕中心那点真的落在本窗上
            val cx = rect.x + rect.width / 2
            val cy = rect.y + rect.height / 2
            var onTop = isPointOnWindow(hwnd, cx, cy)
            if (!onTop) {
                // 拍板「可以做强制顶置」⇒ 先抢一手再判，别拿"第一次没压过"就当结论。
                // 抢不过去也照实说：那一手走到哪、中心点归没归本窗，都在这句读数里。
                val forced = forceTopmostBand(hwnd) { isPointOnWindow(hwnd, cx, cy) }
                println("D_截图 强顶 $forced")
                onTop = isPointOnWindow(hwnd, cx, cy)
            }
            if (!onTop) {
                // 报**谁**占着那点，不报"多半已锁屏"那种猜法：取证的人拿这句就能决定下一步
                val at = windowAtPoint(cx, cy)
                println(
                    "D_截图 无效：中心点 ($cx,$cy) 落在 " +
                        "${if (at == null) "无窗" else "hwnd=$at " + windowDescribe(at)}" +
                        "，本窗 hwnd=$hwnd ${windowDescribe(hwnd)} rect=${rect.x},${rect.y} ${rect.width}x${rect.height} —— 不产出假证据"
                )
                return
            }
            val img = Robot().createScreenCapture(rect)
            ImageIO.write(img, "png", File(path))
            println("D_截图 $path rect=${rect.x},${rect.y} ${rect.width}x${rect.height} 字节=${File(path).length()}")
            // 同屏再抓一笔全屏当对照：窗口矩形取自 Win32，若截到的不是窗口，看全屏就知道差在哪
            val screen = java.awt.Toolkit.getDefaultToolkit().screenSize
            val full = Robot().createScreenCapture(Rectangle(0, 0, screen.width, screen.height))
            ImageIO.write(full, "png", File("$path.full.png"))
            println("D_截图 对照全屏 ${screen.width}x${screen.height} 字节=${File("$path.full.png").length()}")
        }.onFailure { println("D_截图 FAIL ${it::class.java.simpleName}: ${it.message?.take(140)}") }
    } finally {
        // 两手都要落回：AWT 那层的开关，与 Win32 那颗 WS_EX_TOPMOST 位。
        // 只解一半的话，这扇窗在本进程剩下的时间里仍压着用户的屏幕 —— 而它本来只是为了一张图。
        runCatching {
            frame.isAlwaysOnTop = false
            setTopmost(hwnd, false)
        }.onFailure { println("D_截图 落回 topmost 失败 ${it::class.java.simpleName}") }
    }
}

/**
 * 取证专用：把**自己这扇窗**抬到普通窗之上，让 `WindowFromPoint` 真认得它
 * （用户拍板走"抬自己这扇窗"那条）。
 *
 * 真干活的是 `setTopmost`（Win32 的 `SetWindowPos(HWND_TOPMOST)` + 回读 `WS_EX_TOPMOST` 位），
 * AWT 那层的 `setAlwaysOnTop` 只是**顺带**：实测它报 `isAlwaysOnTop=true 支持=true visible=true`
 * 而中心点仍然被用户的 Chrome 窗占着 —— 那一层样式没落到 Win32，所以不能拿它当证据。
 * 至于 `SetForegroundWindow`：它受系统的 foreground lock 管，非前台进程调它常常只换来任务栏闪一下，
 * 要绕过那把锁得 `AttachThreadInput` 附到前台线程 —— 比"抢前台"更脏的一手，**明确
 * 批准「可以强制顶置」，故现由 [forceTopmostBand] 走到这一手**（只在 `-Pshot` 取证分支里，见上）。
 *
 * ⚠️ **不调 `requestFocus()`**：取证要的只是 z-order，不是输入焦点。
 * 把键盘焦点从用户正在打的字上抢走，与"截一张图"完全不成比例。
 *
 * 返回的是**一句读数**，不是布尔：抬没抬起来、位上没上、抛了什么，都得留在 stdout 里 ——
 * 只回 true/false 的话，读图的人分不清"窗口真在上面"与"根本抬不动"。
 */
internal fun raiseOwnWindowForShot(frame: java.awt.Frame, hwnd: Long?): String {
    val awt = runCatching {
        if (!frame.isAlwaysOnTopSupported) {
            "AWT 不支持 topmost"
        } else {
            frame.isAlwaysOnTop = true
            frame.toFront()
            "AWT 报了 isAlwaysOnTop=${frame.isAlwaysOnTop}"
        }
    }.getOrElse { "AWT 那层抛了 ${it::class.java.simpleName}" }
    return "$awt ｜ Win32 ${setTopmost(hwnd, true)}"
}
