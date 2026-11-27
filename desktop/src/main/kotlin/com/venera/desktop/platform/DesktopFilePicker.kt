package com.venera.desktop.platform

import java.awt.FileDialog
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 系统文件框的唯一入口。
 *
 * ## 为什么要收成一颗
 *
 * 此前几个屏各写各的 `FileDialog(owner = null, …)`，踩出两种一模一样的毛病：
 *  - **框弹在应用后面**：`owner` 为 null 时 Windows 不把对话框挂到主窗口上，
 *    既不居中也不抢焦点，用户看着窗口没反应，以为卡死。
 *  - **取消后又弹一次**：模态框在主事件线程上阻塞，关闭时系统把积压的输入投回主窗口，
 *    Compose 恢复渲染后照单全收，同一颗把手被再按一次。
 *
 * ## 三条纪律
 *
 * 1. `owner` 为 null 时**自己找**当前活动窗口，绝不让对话框当孤儿。
 * 2. 显示前把 owner 抬到前面，框一出来就在用户眼前。
 * 3. [picking] 是同步重入闸：弹着的时候再按直接当取消，不给第二次机会。
 *
 * 取消一律交回 `null`（调用方不必区分"取消"与"弹不出来"）。
 */
internal fun desktopPickFile(
    owner: Frame?,
    mode: Int,
    title: String,
    defaultName: String? = null,
    initialDir: String? = null,
    filterPattern: String? = null,
): String? {
    if (GraphicsEnvironment.isHeadless()) return null
    if (!picking.compareAndSet(false, true)) return null
    try {
        val frame = owner
            ?: Frame.getFrames().firstOrNull { it.isVisible && it.isActive }
            ?: Frame.getFrames().firstOrNull { it.isVisible }
        var chosen: String? = null
        val open = {
            val dialog = FileDialog(frame, title, mode)
            if (defaultName != null) dialog.file = defaultName
            if (initialDir != null) dialog.directory = initialDir
            if (filterPattern != null) dialog.file = filterPattern
            frame?.toFront()
            dialog.isVisible = true
            val name = dialog.file
            if (name != null) chosen = File(dialog.directory, name).absolutePath
        }
        if (java.awt.EventQueue.isDispatchThread()) {
            open()
        } else {
            runCatching { java.awt.EventQueue.invokeAndWait { open() } }
        }
        return chosen
    } finally {
        picking.set(false)
    }
}

/** 同一时刻只允许一枚系统文件框；见 [desktopPickFile] 第 3 条。 */
private val picking = AtomicBoolean(false)

/** 打开框：挑一个已存在的文件。 */
internal fun desktopPickOpenFile(owner: Frame?, title: String, filterPattern: String? = null): String? =
    desktopPickFile(owner, FileDialog.LOAD, title, filterPattern = filterPattern)

/** 保存框：给默认名，交回用户确认后的落盘路径。 */
internal fun desktopPickSaveFile(owner: Frame?, title: String, defaultName: String?): String? =
    desktopPickFile(owner, FileDialog.SAVE, title, defaultName)
