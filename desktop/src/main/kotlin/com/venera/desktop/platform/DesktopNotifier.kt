package com.venera.desktop.platform

/**
 * 系统通知兼容入口：统一委托给 [DesktopSystemTray]，避免重复安装第二颗托盘图标。
 */
object DesktopNotifier {
    /** 保留兼容签名；托盘安装已统一由 [DesktopSystemTray.install] 负责。 */
    fun install() = Unit

    /**
     * 弹一条系统通知。
     *
     * @param title 标题，Windows 上过长会被截断，控制在二十来字以内。
     * @param body 正文。
     */
    fun notify(title: String, body: String) {
        DesktopSystemTray.notifyDownloadComplete(title, body)
    }
}
