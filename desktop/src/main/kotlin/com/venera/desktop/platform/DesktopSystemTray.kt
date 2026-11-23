package com.venera.desktop.platform

import com.venera.desktop.gallery.data.DesktopComicDownloadQueue
import com.venera.desktop.gallery.data.DesktopComicQueueStatus
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.FontMetrics
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.Insets
import java.awt.Point
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon
import java.awt.Window
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.geom.RoundRectangle2D
import java.util.concurrent.Executors
import javax.imageio.ImageIO
import javax.swing.JDialog
import javax.swing.JPanel
import javax.swing.SwingUtilities

/** 托盘菜单线性图标类型。 */
internal enum class TrayIconKind {
    NONE,
    SHOW_WINDOW,
    HIDE_WINDOW,
    DOWNLOADS,
    FOLDER,
    FOLLOW,
    PAUSE,
    RESUME,
    MASK,
    EXIT,
}

/** 托盘菜单项模型（纯数据，便于布局绘制与单测）。 */
internal sealed interface DesktopTrayMenuEntry {
    data class Header(
        val title: String,
        val dotColor: Color,
    ) : DesktopTrayMenuEntry

    data class Action(
        val id: String,
        val label: String,
        val icon: TrayIconKind,
        val checked: Boolean? = null,
        val enabled: Boolean = true,
        val danger: Boolean = false,
        val onClick: () -> Unit = {},
    ) : DesktopTrayMenuEntry

    data object Separator : DesktopTrayMenuEntry
}

/** 托盘弹窗所需实时状态快照。 */
internal data class DesktopTraySnapshot(
    val windowVisible: Boolean,
    val runningCount: Int,
    val pendingCount: Int,
    val stoppedCount: Int,
    val failedCount: Int,
    val maskEnabled: Boolean,
    val hasDownloadsDir: Boolean,
    val canCheckFollow: Boolean,
)

/** 主窗与路由层注入给托盘菜单的业务动作集合。 */
internal data class DesktopTrayShellCallbacks(
    val isWindowVisible: () -> Boolean = { true },
    val onShowWindow: () -> Unit = {},
    val onHideToTray: () -> Unit = {},
    val onOpenDownloads: () -> Unit = {},
    val onOpenDownloadDir: () -> Unit = {},
    val onCheckFollowUpdates: () -> Unit = {},
    val onPauseAllDownloads: () -> Unit = {},
    val onResumeAllDownloads: () -> Unit = {},
    val isMaskEnabled: () -> Boolean = { false },
    val onToggleMask: () -> Unit = {},
    val hasDownloadsDir: () -> Boolean = { false },
    val canCheckFollow: () -> Boolean = { false },
    val onExit: () -> Unit = {},
)

/** 根据当前后台队列、窗口与遮罩状态构建实用托盘菜单列表。 */
internal fun buildDesktopTrayMenuEntries(
    snapshot: DesktopTraySnapshot,
    callbacks: DesktopTrayShellCallbacks,
): List<DesktopTrayMenuEntry> {
    val activeCount = snapshot.runningCount + snapshot.pendingCount
    val statusText = when {
        snapshot.runningCount > 0 -> "正在下载 ${snapshot.runningCount} 项" +
            if (snapshot.pendingCount > 0) " · ${snapshot.pendingCount} 排队" else ""
        snapshot.pendingCount > 0 -> "等待下载 ${snapshot.pendingCount} 项"
        snapshot.failedCount > 0 -> "下载异常 ${snapshot.failedCount} 项"
        snapshot.stoppedCount > 0 -> "已暂停 ${snapshot.stoppedCount} 项"
        else -> "VComic · 后台待命中"
    }
    val statusDot = when {
        snapshot.runningCount > 0 -> Color(0x3E, 0xCF, 0x8E)
        snapshot.failedCount > 0 -> Color(0xF2, 0x8B, 0x82)
        snapshot.stoppedCount > 0 || snapshot.pendingCount > 0 -> Color(0xF2, 0xB8, 0x7C)
        else -> Color(0x8A, 0x94, 0xA6)
    }

    return buildList {
        add(DesktopTrayMenuEntry.Header(statusText, statusDot))
        add(
            if (snapshot.windowVisible) {
                DesktopTrayMenuEntry.Action(
                    id = "hide_window",
                    label = "隐藏到托盘",
                    icon = TrayIconKind.HIDE_WINDOW,
                    onClick = callbacks.onHideToTray,
                )
            } else {
                DesktopTrayMenuEntry.Action(
                    id = "show_window",
                    label = "显示主窗口",
                    icon = TrayIconKind.SHOW_WINDOW,
                    onClick = callbacks.onShowWindow,
                )
            },
        )
        add(DesktopTrayMenuEntry.Separator)
        add(
            DesktopTrayMenuEntry.Action(
                id = "open_downloads",
                label = "打开下载与本地",
                icon = TrayIconKind.DOWNLOADS,
                onClick = callbacks.onOpenDownloads,
            ),
        )
        add(
            DesktopTrayMenuEntry.Action(
                id = "open_download_dir",
                label = "打开下载文件夹",
                icon = TrayIconKind.FOLDER,
                enabled = snapshot.hasDownloadsDir,
                onClick = callbacks.onOpenDownloadDir,
            ),
        )
        add(
            DesktopTrayMenuEntry.Action(
                id = "check_follow",
                label = "立即检查追更",
                icon = TrayIconKind.FOLLOW,
                enabled = snapshot.canCheckFollow,
                onClick = callbacks.onCheckFollowUpdates,
            ),
        )
        if (activeCount > 0) {
            add(
                DesktopTrayMenuEntry.Action(
                    id = "pause_all",
                    label = "暂停全部下载 ($activeCount)",
                    icon = TrayIconKind.PAUSE,
                    onClick = callbacks.onPauseAllDownloads,
                ),
            )
        } else if (snapshot.stoppedCount + snapshot.failedCount > 0) {
            val resumeCount = snapshot.stoppedCount + snapshot.failedCount
            add(
                DesktopTrayMenuEntry.Action(
                    id = "resume_all",
                    label = if (snapshot.failedCount > 0) "重试 / 继续下载 ($resumeCount)" else "继续全部下载 ($resumeCount)",
                    icon = TrayIconKind.RESUME,
                    onClick = callbacks.onResumeAllDownloads,
                ),
            )
        }
        add(
            DesktopTrayMenuEntry.Action(
                id = "toggle_mask",
                label = if (snapshot.maskEnabled) "隐私遮罩：已开启" else "隐私遮罩：已关闭",
                icon = TrayIconKind.MASK,
                checked = snapshot.maskEnabled,
                onClick = callbacks.onToggleMask,
            ),
        )
        add(DesktopTrayMenuEntry.Separator)
        add(
            DesktopTrayMenuEntry.Action(
                id = "exit",
                label = "退出 VComic",
                icon = TrayIconKind.EXIT,
                danger = true,
                onClick = callbacks.onExit,
            ),
        )
    }
}

/** 计算托盘菜单在屏幕工作区内的贴合坐标，防止越界或被任务栏遮挡。 */
internal fun computeTrayPopupOrigin(
    clickX: Int,
    clickY: Int,
    menuWidth: Int,
    menuHeight: Int,
    workArea: Rectangle,
): Point {
    val margin = 8
    val preferAbove = clickY + menuHeight + margin > workArea.y + workArea.height
    val rawY = if (preferAbove) clickY - menuHeight - 4 else clickY + 4
    val rawX = if (clickX + menuWidth + margin > workArea.x + workArea.width) {
        clickX - menuWidth
    } else {
        clickX
    }
    val minX = workArea.x + margin
    val maxX = (workArea.x + workArea.width - menuWidth - margin).coerceAtLeast(minX)
    val minY = workArea.y + margin
    val maxY = (workArea.y + workArea.height - menuHeight - margin).coerceAtLeast(minY)
    return Point(rawX.coerceIn(minX, maxX), rawY.coerceIn(minY, maxY))
}

/**
 * 桌面系统托盘与后台通知管理器；无头或不支持托盘的环境自动降级为空操作。
 * 右键弹出窗口同步 Windows 11 深浅色风格，支持快捷跳转、下载控制、追更检查与隐私遮罩。
 */
object DesktopSystemTray {
    @Volatile
    private var trayIcon: TrayIcon? = null

    @Volatile
    private var popupDialog: Win11TrayMenuDialog? = null

    @Volatile
    private var baseCallbacks: DesktopTrayShellCallbacks = DesktopTrayShellCallbacks()

    @Volatile
    private var routeCallbacks: DesktopTrayShellCallbacks? = null

    @Volatile
    private var cachedWindowsDark: Boolean = true

    private val themeExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "vcomic-tray-theme").apply { isDaemon = true }
    }

    /**
     * 安装系统托盘图标与 Win11 风格右键菜单。
     *
     * @param onShowWindow 点击“显示主窗口”或双击托盘图标时的回调。
     * @param onExit 点击“退出”时的回调。
     */
    fun install(
        onShowWindow: () -> Unit,
        onExit: () -> Unit,
        isWindowVisible: () -> Boolean = { true },
        onHideToTray: () -> Unit = {},
        isMaskEnabled: () -> Boolean = { false },
        onToggleMask: () -> Unit = {},
    ) {
        baseCallbacks = DesktopTrayShellCallbacks(
            isWindowVisible = isWindowVisible,
            onShowWindow = onShowWindow,
            onHideToTray = onHideToTray,
            isMaskEnabled = isMaskEnabled,
            onToggleMask = onToggleMask,
            onExit = onExit,
        )
        refreshWindowsThemeAsync()
        if (trayIcon != null) return
        runCatching {
            if (!SystemTray.isSupported()) return
            val stream = DesktopSystemTray::class.java.classLoader.getResourceAsStream("vcomic_icon.png")
                ?: return
            val image = stream.use { ImageIO.read(it) } ?: return
            SwingUtilities.invokeLater {
                runCatching {
                    val icon = TrayIcon(image, "VComic").apply {
                        isImageAutoSize = true
                        addMouseListener(object : MouseAdapter() {
                            override fun mouseClicked(e: MouseEvent) {
                                if (e.button == MouseEvent.BUTTON1 && e.clickCount >= 2) {
                                    mergedCallbacks().onShowWindow()
                                }
                            }

                            override fun mouseReleased(e: MouseEvent) {
                                if (e.isPopupTrigger || e.button == MouseEvent.BUTTON3) {
                                    refreshWindowsThemeAsync()
                                    showWin11Menu(e.xOnScreen, e.yOnScreen)
                                }
                            }
                        })
                    }
                    SystemTray.getSystemTray().add(icon)
                    trayIcon = icon
                }
            }
        }
    }

    /** 由路由层绑定或解绑实时业务回调（打开下载页、打开下载文件夹、检查追更、队列暂停恢复）。 */
    internal fun bindShellActions(callbacks: DesktopTrayShellCallbacks?) {
        routeCallbacks = callbacks
    }

    /** 触发唤醒主窗口到前台。 */
    fun requestShowWindow() {
        SwingUtilities.invokeLater {
            runCatching { baseCallbacks.onShowWindow() }
        }
    }

    private fun mergedCallbacks(): DesktopTrayShellCallbacks {
        val base = baseCallbacks
        val extra = routeCallbacks ?: return base
        return base.copy(
            onOpenDownloads = extra.onOpenDownloads,
            onOpenDownloadDir = extra.onOpenDownloadDir,
            onCheckFollowUpdates = extra.onCheckFollowUpdates,
            onPauseAllDownloads = extra.onPauseAllDownloads,
            onResumeAllDownloads = extra.onResumeAllDownloads,
            hasDownloadsDir = extra.hasDownloadsDir,
            canCheckFollow = extra.canCheckFollow,
        )
    }

    private fun currentSnapshot(callbacks: DesktopTrayShellCallbacks): DesktopTraySnapshot {
        val tasks = DesktopComicDownloadQueue.tasks.value
        return DesktopTraySnapshot(
            windowVisible = callbacks.isWindowVisible(),
            runningCount = tasks.count { it.status == DesktopComicQueueStatus.RUNNING },
            pendingCount = tasks.count { it.status == DesktopComicQueueStatus.PENDING },
            stoppedCount = tasks.count { it.status == DesktopComicQueueStatus.STOPPED },
            failedCount = tasks.count { it.status == DesktopComicQueueStatus.FAILED },
            maskEnabled = callbacks.isMaskEnabled(),
            hasDownloadsDir = callbacks.hasDownloadsDir(),
            canCheckFollow = callbacks.canCheckFollow(),
        )
    }

    private fun showWin11Menu(screenX: Int, screenY: Int) {
        val callbacks = mergedCallbacks()
        val entries = buildDesktopTrayMenuEntries(currentSnapshot(callbacks), callbacks)
        val dialog = popupDialog ?: Win11TrayMenuDialog().also { popupDialog = it }
        dialog.showMenu(entries, dark = cachedWindowsDark, screenX = screenX, screenY = screenY)
    }

    /** 读取 Windows 系统任务栏/应用明暗主题并缓存，避免阻塞 EDT。 */
    private fun refreshWindowsThemeAsync() {
        themeExecutor.execute {
            cachedWindowsDark = queryWindowsUsesDarkTheme()
        }
    }

    private fun queryWindowsUsesDarkTheme(): Boolean {
        val os = System.getProperty("os.name").orEmpty().lowercase()
        if (!os.contains("win")) return true
        val systemTheme = queryPersonalizeDword("SystemUsesLightTheme")
            ?: queryPersonalizeDword("AppsUseLightTheme")
        return systemTheme != 1
    }

    private fun queryPersonalizeDword(valueName: String): Int? = runCatching {
        val p = ProcessBuilder(
            "reg", "query",
            "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
            "/v", valueName,
        ).start()
        val out = p.inputStream.readBytes().toString(Charsets.UTF_8)
        p.waitFor()
        when {
            out.contains("0x1") -> 1
            out.contains("0x0") -> 0
            else -> null
        }
    }.getOrNull()

    /**
     * 发送系统通知（经由系统托盘进入 Windows 通知中心）。
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
        val dialog = popupDialog
        trayIcon = null
        popupDialog = null
        routeCallbacks = null
        SwingUtilities.invokeLater {
            runCatching {
                if (SystemTray.isSupported()) {
                    SystemTray.getSystemTray().remove(icon)
                }
                dialog?.dispose()
            }
        }
    }
}

/** 同步 Windows 11 深浅色圆角风格的轻量自绘托盘菜单窗体。 */
private class Win11TrayMenuDialog : JDialog(null as Window?) {
    private val menuPanel = Win11TrayMenuPanel { dismissMenu() }

    init {
        isUndecorated = true
        type = Type.UTILITY
        isAlwaysOnTop = true
        background = Color(0, 0, 0, 0)
        contentPane = menuPanel
        addWindowFocusListener(object : WindowAdapter() {
            override fun windowLostFocus(e: WindowEvent?) {
                dismissMenu()
            }
        })
        addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ESCAPE) dismissMenu()
            }
        })
    }

    fun showMenu(entries: List<DesktopTrayMenuEntry>, dark: Boolean, screenX: Int, screenY: Int) {
        menuPanel.updateEntries(entries, dark)
        pack()
        val workArea = resolveWorkAreaFor(screenX, screenY)
        val origin = computeTrayPopupOrigin(screenX, screenY, width, height, workArea)
        setLocation(origin.x, origin.y)
        isVisible = true
        toFront()
        requestFocus()
    }

    private fun dismissMenu() {
        if (isVisible) isVisible = false
    }

    private fun resolveWorkAreaFor(x: Int, y: Int): Rectangle {
        val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
        val matched = ge.screenDevices.firstOrNull { dev ->
            dev.defaultConfiguration.bounds.contains(x, y)
        } ?: ge.defaultScreenDevice
        val gc = matched.defaultConfiguration
        val bounds = gc.bounds
        val insets: Insets = Toolkit.getDefaultToolkit().getScreenInsets(gc)
        return Rectangle(
            bounds.x + insets.left,
            bounds.y + insets.top,
            (bounds.width - insets.left - insets.right).coerceAtLeast(240),
            (bounds.height - insets.top - insets.bottom).coerceAtLeast(240),
        )
    }
}

private class Win11TrayMenuPanel(
    private val onDismiss: () -> Unit,
) : JPanel() {
    private var entries: List<DesktopTrayMenuEntry> = emptyList()
    private var itemBounds: List<Rectangle> = emptyList()
    private var hoveredIndex: Int = -1
    private var pressedIndex: Int = -1
    private var darkMode: Boolean = true

    private val panelWidth = 236
    private val outerPad = 6
    private val headerHeight = 30
    private val actionHeight = 32
    private val separatorHeight = 9
    private val textFont = Font("Microsoft YaHei UI", Font.PLAIN, 13)
    private val headerFont = Font("Microsoft YaHei UI", Font.BOLD, 12)

    init {
        isOpaque = false
        val adapter = object : MouseAdapter() {
            override fun mouseMoved(e: MouseEvent) {
                updateHover(e.point)
            }

            override fun mouseExited(e: MouseEvent) {
                if (hoveredIndex != -1) {
                    hoveredIndex = -1
                    cursor = Cursor.getDefaultCursor()
                    repaint()
                }
            }

            override fun mousePressed(e: MouseEvent) {
                if (e.button != MouseEvent.BUTTON1) return
                val idx = indexAt(e.point)
                if (idx >= 0 && isInteractive(idx)) {
                    pressedIndex = idx
                    repaint()
                }
            }

            override fun mouseReleased(e: MouseEvent) {
                val prevPressed = pressedIndex
                pressedIndex = -1
                val idx = indexAt(e.point)
                repaint()
                if (e.button == MouseEvent.BUTTON1 && idx >= 0 && idx == prevPressed && isInteractive(idx)) {
                    val action = entries[idx] as? DesktopTrayMenuEntry.Action ?: return
                    onDismiss()
                    SwingUtilities.invokeLater { runCatching { action.onClick() } }
                }
            }
        }
        addMouseListener(adapter)
        addMouseMotionListener(adapter)
    }

    fun updateEntries(newEntries: List<DesktopTrayMenuEntry>, dark: Boolean) {
        entries = newEntries
        darkMode = dark
        hoveredIndex = -1
        pressedIndex = -1
        var y = outerPad
        val rects = ArrayList<Rectangle>(newEntries.size)
        for (entry in newEntries) {
            val h = when (entry) {
                is DesktopTrayMenuEntry.Header -> headerHeight
                is DesktopTrayMenuEntry.Action -> actionHeight
                DesktopTrayMenuEntry.Separator -> separatorHeight
            }
            rects += Rectangle(outerPad, y, panelWidth - outerPad * 2, h)
            y += h
        }
        itemBounds = rects
        preferredSize = Dimension(panelWidth, y + outerPad)
        revalidate()
        repaint()
    }

    private fun isInteractive(index: Int): Boolean {
        val entry = entries.getOrNull(index) as? DesktopTrayMenuEntry.Action ?: return false
        return entry.enabled
    }

    private fun indexAt(point: Point): Int =
        itemBounds.indexOfFirst { it.contains(point) }

    private fun updateHover(point: Point) {
        val next = indexAt(point).takeIf { it >= 0 && isInteractive(it) } ?: -1
        if (next != hoveredIndex) {
            hoveredIndex = next
            cursor = if (next >= 0) Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) else Cursor.getDefaultCursor()
            repaint()
        }
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB)
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)

            val bg = if (darkMode) Color(0x2C, 0x2C, 0x2C, 250) else Color(0xF9, 0xF9, 0xF9, 250)
            val border = if (darkMode) Color(255, 255, 255, 26) else Color(0, 0, 0, 26)
            val hoverBg = if (darkMode) Color(255, 255, 255, 19) else Color(0, 0, 0, 12)
            val pressBg = if (darkMode) Color(255, 255, 255, 11) else Color(0, 0, 0, 7)
            val dangerHoverBg = if (darkMode) Color(0xE8, 0x45, 0x45, 42) else Color(0xC4, 0x2B, 0x1C, 24)
            val primaryText = if (darkMode) Color(0xF4, 0xF4, 0xF4) else Color(0x1B, 0x1B, 0x1B)
            val secondaryText = if (darkMode) Color(0xAE, 0xB4, 0xC0) else Color(0x61, 0x67, 0x70)
            val disabledText = if (darkMode) Color(255, 255, 255, 86) else Color(0, 0, 0, 90)
            val dividerColor = if (darkMode) Color(255, 255, 255, 20) else Color(0, 0, 0, 16)
            val accentColor = if (darkMode) Color(0x60, 0xCD, 0xFF) else Color(0x00, 0x67, 0xC0)
            val dangerText = if (darkMode) Color(0xF4, 0x87, 0x71) else Color(0xC4, 0x2B, 0x1C)

            val panelShape = RoundRectangle2D.Float(0.5f, 0.5f, width - 1f, height - 1f, 10f, 10f)
            g2.color = bg
            g2.fill(panelShape)
            g2.color = border
            g2.stroke = BasicStroke(1f)
            g2.draw(panelShape)

            entries.forEachIndexed { index, entry ->
                val rect = itemBounds.getOrNull(index) ?: return@forEachIndexed
                when (entry) {
                    is DesktopTrayMenuEntry.Header -> {
                        g2.color = entry.dotColor
                        g2.fillOval(rect.x + 11, rect.y + (rect.height - 7) / 2, 7, 7)
                        g2.font = headerFont
                        g2.color = secondaryText
                        drawCenteredText(g2, entry.title, rect.x + 26, rect.y, rect.width - 34, rect.height)
                    }

                    DesktopTrayMenuEntry.Separator -> {
                        val lineY = rect.y + rect.height / 2
                        g2.color = dividerColor
                        g2.drawLine(rect.x + 8, lineY, rect.x + rect.width - 8, lineY)
                    }

                    is DesktopTrayMenuEntry.Action -> {
                        val isHovered = index == hoveredIndex
                        val isPressed = index == pressedIndex
                        if (entry.enabled && (isHovered || isPressed)) {
                            g2.color = when {
                                isPressed -> pressBg
                                entry.danger -> dangerHoverBg
                                else -> hoverBg
                            }
                            g2.fill(RoundRectangle2D.Float(rect.x.toFloat(), rect.y.toFloat(), rect.width.toFloat(), rect.height.toFloat(), 6f, 6f))
                        }

                        val fg = when {
                            !entry.enabled -> disabledText
                            entry.danger && isHovered -> dangerText
                            entry.checked == true -> accentColor
                            else -> primaryText
                        }
                        val iconX = rect.x + 10
                        val iconY = rect.y + (rect.height - 14) / 2
                        drawTrayIcon(g2, entry.icon, iconX, iconY, fg)

                        g2.font = textFont
                        g2.color = if (!entry.enabled) disabledText else if (entry.danger && isHovered) dangerText else primaryText
                        val rightReserved = if (entry.checked != null) 26 else 10
                        drawCenteredText(g2, entry.label, rect.x + 32, rect.y, rect.width - 32 - rightReserved, rect.height)

                        if (entry.checked == true) {
                            drawCheckMark(g2, rect.x + rect.width - 20, rect.y + (rect.height - 10) / 2, accentColor)
                        }
                    }
                }
            }
        } finally {
            g2.dispose()
        }
    }

    private fun drawCenteredText(g2: Graphics2D, text: String, x: Int, y: Int, maxWidth: Int, height: Int) {
        val fm: FontMetrics = g2.fontMetrics
        var shown = text
        if (fm.stringWidth(shown) > maxWidth) {
            while (shown.length > 1 && fm.stringWidth("$shown…") > maxWidth) {
                shown = shown.dropLast(1)
            }
            shown = "$shown…"
        }
        val baseline = y + (height - fm.height) / 2 + fm.ascent
        g2.drawString(shown, x, baseline)
    }

    private fun drawCheckMark(g2: Graphics2D, x: Int, y: Int, color: Color) {
        g2.color = color
        g2.stroke = BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g2.drawLine(x, y + 5, x + 3, y + 8)
        g2.drawLine(x + 3, y + 8, x + 9, y + 2)
    }

    private fun drawTrayIcon(g2: Graphics2D, kind: TrayIconKind, x: Int, y: Int, color: Color) {
        if (kind == TrayIconKind.NONE) return
        g2.color = color
        g2.stroke = BasicStroke(1.35f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        when (kind) {
            TrayIconKind.SHOW_WINDOW -> {
                g2.drawRoundRect(x + 1, y + 2, 12, 10, 2, 2)
                g2.drawLine(x + 1, y + 5, x + 13, y + 5)
            }
            TrayIconKind.HIDE_WINDOW -> {
                g2.drawRoundRect(x + 1, y + 2, 12, 10, 2, 2)
                g2.drawLine(x + 4, y + 9, x + 10, y + 9)
            }
            TrayIconKind.DOWNLOADS -> {
                g2.drawLine(x + 7, y + 2, x + 7, y + 9)
                g2.drawLine(x + 4, y + 6, x + 7, y + 9)
                g2.drawLine(x + 10, y + 6, x + 7, y + 9)
                g2.drawLine(x + 2, y + 12, x + 12, y + 12)
            }
            TrayIconKind.FOLDER -> {
                g2.drawLine(x + 1, y + 3, x + 5, y + 3)
                g2.drawLine(x + 5, y + 3, x + 7, y + 5)
                g2.drawRoundRect(x + 1, y + 5, 12, 7, 2, 2)
            }
            TrayIconKind.FOLLOW -> {
                g2.drawArc(x + 2, y + 2, 10, 10, 35, 250)
                g2.drawLine(x + 11, y + 2, x + 12, y + 5)
                g2.drawLine(x + 9, y + 5, x + 12, y + 5)
            }
            TrayIconKind.PAUSE -> {
                g2.drawLine(x + 5, y + 3, x + 5, y + 11)
                g2.drawLine(x + 9, y + 3, x + 9, y + 11)
            }
            TrayIconKind.RESUME -> {
                g2.drawPolygon(intArrayOf(x + 4, x + 11, x + 4), intArrayOf(y + 3, y + 7, y + 11), 3)
            }
            TrayIconKind.MASK -> {
                g2.drawOval(x + 1, y + 4, 12, 6)
                g2.fillOval(x + 6, y + 6, 2, 2)
                g2.drawLine(x + 2, y + 11, x + 12, y + 3)
            }
            TrayIconKind.EXIT -> {
                g2.drawArc(x + 2, y + 3, 10, 10, 130, 280)
                g2.drawLine(x + 7, y + 1, x + 7, y + 7)
            }
            TrayIconKind.NONE -> Unit
        }
    }
}
