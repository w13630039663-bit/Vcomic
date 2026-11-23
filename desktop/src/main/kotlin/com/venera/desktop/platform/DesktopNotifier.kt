package com.venera.desktop.platform

import java.awt.Color
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.awt.Image
import java.awt.RenderingHints
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.Window
import java.awt.image.BufferedImage

/**
 * 系统通知（Windows 走通知中心）。
 *
 * AWT 的 `displayMessage` 必须先有一颗**已加入系统托盘**的图标，否则调用无效 ——
 * 所以这里常驻一枚托盘图标，双击还原主窗口。托盘不可用（远程会话、headless）时
 * 整条通道关掉并念一句成因，不静默假发。
 */
object DesktopNotifier {
    private var tray: TrayIcon? = null
    private var attempted = false

    /** 装一次托盘。重复调用无效。 */
    fun install() {
        if (attempted) return
        attempted = true
        if (GraphicsEnvironment.isHeadless()) {
            println("D_通知 headless 环境，通知通道关掉")
            return
        }
        if (!SystemTray.isSupported()) {
            println("D_通知 这台机器没有系统托盘，通知通道关掉")
            return
        }
        runCatching {
            val icon = TrayIcon(iconImage(), "Venera", null).apply {
                isImageAutoSize = true
                addActionListener { bringToFront() }
            }
            SystemTray.getSystemTray().add(icon)
            tray = icon
        }.onFailure {
            println("D_通知 托盘装不上：${it.javaClass.simpleName} ${it.message?.take(120)}")
        }
    }

    /**
     * 弹一条通知。
     *
     * @param title 标题，Windows 上过长会被截断，控制在二十来字以内。
     * @param body 正文。
     */
    fun notify(title: String, body: String) {
        val t = tray ?: return
        runCatching { t.displayMessage(title, body, TrayIcon.MessageType.INFO) }
    }

    private fun bringToFront() {
        runCatching {
            Window.getWindows()
                .filterIsInstance<java.awt.Frame>()
                .firstOrNull { it.isVisible }
                ?.apply {
                    if (state and java.awt.Frame.ICONIFIED != 0) state = java.awt.Frame.NORMAL
                    toFront()
                    requestFocus()
                }
        }
    }

    /** 托盘图标：16×16 圆角方块 + 一道横杠，不引外部资源。 */
    private fun iconImage(): Image {
        val size = 16
        val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color(0x7C5CFF)
        g.fillRoundRect(1, 1, size - 2, size - 2, 6, 6)
        g.color = Color.WHITE
        g.fillRoundRect(4, 7, size - 8, 2, 1, 1)
        g.dispose()
        return img
    }
}
