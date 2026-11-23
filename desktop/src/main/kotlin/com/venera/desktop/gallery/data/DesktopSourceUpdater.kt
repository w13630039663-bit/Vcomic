package com.venera.desktop.gallery.data

import java.io.File
import java.util.Properties

/**
 * 改写层里哪些稿是**自动更新写的**（而不是用户手改的）。
 *
 * 两种稿在盘上完全同形，只能靠另一份记录区分：写进来的那一版版本号记在这里，
 * 下次要比的稿若与它一致，就说明没人再动过 ⇒ 可以换新的；不一致就是手改稿，
 * 宁可停在旧版也不覆盖 —— 覆盖用户手改的稿不可逆。
 */
object DesktopSourceUpdater {
    private const val MANIFEST = ".auto-updated.properties"

    /** 这一篇能不能被自动更新覆盖：没有稿（纯随包）或稿正是上次自动写的那一版。 */
    fun canAutoOverwrite(dir: File, fileKey: String, localVersion: String): Boolean {
        val overridden = DesktopSourceOverrides.textOf(dir, fileKey) ?: return true
        return read(dir).getProperty(fileKey) == localVersion
    }

    /** 记下这一篇是自动更新写的（版本号用于下次判定）。 */
    fun markAutoUpdated(dir: File, fileKey: String, version: String) {
        val props = read(dir)
        props.setProperty(fileKey, version)
        runCatching {
            File(dir, MANIFEST).outputStream().use { props.store(it, "auto-updated source versions") }
        }
    }

    private fun read(dir: File): Properties = Properties().apply {
        runCatching { File(dir, MANIFEST).inputStream().use { load(it) } }
    }
}
