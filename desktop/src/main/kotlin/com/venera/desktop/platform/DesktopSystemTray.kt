package com.venera.desktop.platform

import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.imageio.ImageIO
import javax.swing.SwingUtilities

/**
 * 桌面系统托盘与后台下载通知管理器；无头或不支持托盘的环境自动降级为空操作。
 */
object DesktopSystemTray {
    @Volatile
    private var trayIcon: TrayIcon? = null

    /**
     * 安装系统托盘图标与右键菜单。
     *
     * @param onShowWindow 点击“显示主窗口”或双击托盘图标时的回调。
     * @param onExit 点击“退出”时的回调。
     */
    fun install(onShowWindow: () -> Unit, onExit: () -> Unit) {
        if (trayIcon != null) return
        runCatching {
            if (!SystemTray.isSupported()) return
            val stream = DesktopSystemTray::class.java.classLoader.getResourceAsStream("vcomic_icon.png")
                ?: return
            val image = stream.use { ImageIO.read(it) } ?: return
            val popup = PopupMenu().apply {
                add(MenuItem("显示主窗口").apply {
                    addActionListener { onShowWindow() }
                })
                addSeparator()
                add(MenuItem("退出 VComic").apply {
                    addActionListener { onExit() }
                })
            }
            val icon = TrayIcon(image, "VComic", popup).apply {
                isImageAutoSize = true
                addMouseListener(object : MouseAdapter() {
                    override fun mouseClicked(e: MouseEvent) {
                        if (e.button == MouseEvent.BUTTON1 && e.clickCount >= 2) {
                            onShowWindow()
                        }
                    }
                })
            }
            SwingUtilities.invokeLater {
                runCatching {
                    SystemTray.getSystemTray().add(icon)
                    trayIcon = icon
                }
            }
        }
    }

    /**
     * 发送后台下载完成系统通知。
     *
     * @param title 通知标题（如漫画名）。
     * @param message 通知正文（如章节名与完成状态）。
     */
    fun notifyDownloadComplete(title: String, message: String) {
        val icon = trayIcon ?: return
        SwingUtilities.invokeLater {
            runCatching {
                icon.displayMessage(title, message, TrayIcon.MessageType.INFO)
            }
        }
    }

    /** 卸载系统托盘图标，避免进程退出后残留幽灵图标。 */
    fun uninstall() {
        val icon = trayIcon ?: return
        trayIcon = null
        SwingUtilities.invokeLater {
            runCatching {
                if (SystemTray.isSupported()) {
                    SystemTray.getSystemTray().remove(icon)
                }
            }
        }
    }
}
