package com.venera.desktop.gallery.data

import com.venera.desktop.platform.DesktopHttpEngine
import com.venera.compose.data.platform.PathProvider
import java.io.ByteArrayInputStream

/**
 * pixiv 看图播放器的**取图字节 + 落盘 / 进剪贴板**那两条腿（
 * desktop-viewer-「下载」「复制图片」两枚把手）。
 *
 * 取字节走与图片管道同一套防盗链头（`DesktopImageHeaderInterceptor` 问 `ImageHeaderPolicy`
 * —— pixiv 要 Referer、EH 家族要 Referer+UA，逐站自适应），客户端从引擎那台**派生**，
 * 不另起一条无头路。
 */
internal object DesktopPixivImageBytes {

    /** 取某地址的图字节（头按 host 策略贴；非 200/空体如实抛，屏上念成因）。 */
    fun fetch(paths: PathProvider, url: String): ByteArray {
        val client = DesktopHttpEngine(paths).okHttpClient
            .newBuilder()
            .cache(null)
            .addInterceptor(DesktopImageHeaderInterceptor())
            .addInterceptor(DesktopPximgMirrorInterceptor())
            .build()
        val req = okhttp3.Request.Builder().url(url).build()
        client.newCall(req).execute().use { resp ->
            val bytes = resp.body?.bytes()
            if (resp.code != 200 || bytes == null || bytes.isEmpty()) {
                error("取图失败 http=${resp.code} url=$url")
            }
            return bytes
        }
    }

    /**
     * 取字节但**绕开共享磁盘缓存**（animated gif 逐帧专用）。
     *
     * 引擎那台客户端带 `venera_http_cache`（okhttp DiskLruCache）；Windows 上多协程并发写同一 `.tmp`
     * 会因文件占用删不掉，okhttp 打一条 `corrupt … removing` 警告（能自愈但刷屏）。gif 帧一次解进内存即可、
     * 不需缓存，故这里派生一台 `.cache(null)` 的客户端，从源头不碰那个有竞态的缓存盘。
     */
    fun fetchWithoutCache(paths: PathProvider, url: String): ByteArray {
        val client = DesktopHttpEngine(paths).okHttpClient
            .newBuilder()
            .cache(null)
            .addInterceptor(DesktopImageHeaderInterceptor())
            .addInterceptor(DesktopPximgMirrorInterceptor())
            .build()
        val req = okhttp3.Request.Builder().url(url).build()
        client.newCall(req).execute().use { resp ->
            val bytes = resp.body?.bytes()
            if (resp.code != 200 || bytes == null || bytes.isEmpty()) {
                error("取图失败 http=${resp.code} url=$url")
            }
            return bytes
        }
    }

    /** 字节进系统剪贴板（AWT imageFlavor；pixiv/booru 字节层一致，同一条腿）。 */
    fun copyToClipboard(bytes: ByteArray) {
        val img = javax.imageio.ImageIO.read(ByteArrayInputStream(bytes))
            ?: error("剪贴板吃不下这一档（不是可识别的图片）")
        val sel = object : java.awt.datatransfer.Transferable {
            override fun getTransferDataFlavors() = arrayOf(java.awt.datatransfer.DataFlavor.imageFlavor)
            override fun isDataFlavorSupported(f: java.awt.datatransfer.DataFlavor) =
                f == java.awt.datatransfer.DataFlavor.imageFlavor
            override fun getTransferData(f: java.awt.datatransfer.DataFlavor) = img
        }
        java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, null)
    }

    /** 字节落盘：文件框选落点（走统一的 [com.venera.desktop.platform.desktopPickSaveFile]）。返回屏上念的回执句。 */
    fun saveToFile(paths: PathProvider, url: String, suggestedName: String): String {
        val bytes = fetch(paths, url)
        val target = com.venera.desktop.platform.desktopPickSaveFile(
            owner = null,
            title = "保存图片",
            defaultName = suggestedName.ifBlank { "image.jpg" },
        )?.let { java.io.File(it) } ?: return "已取消保存。"
        target.writeBytes(bytes)
        recordPixivImage(url, target, bytes.size.toLong())
        com.venera.desktop.platform.DesktopSystemTray.notifyDownloadComplete("图片已保存", target.name)
        return "已保存：${target.absolutePath}"
    }

    /**
     * 直落已定目录（2.4 默认开关那一腿）：不再弹框，重名让位与画廊存图同一颗
     * [desktopSaveUniqueName]（同名不覆盖，两边同形由它自带的判据钉）。
     */
    fun saveToDir(paths: PathProvider, url: String, suggestedName: String, dir: java.io.File): String {
        val bytes = fetch(paths, url)
        dir.mkdirs()
        val target = java.io.File(dir, desktopSaveUniqueName(dir, suggestedName.ifBlank { "image.jpg" }))
        target.writeBytes(bytes)
        recordPixivImage(url, target, bytes.size.toLong())
        com.venera.desktop.platform.DesktopSystemTray.notifyDownloadComplete("图片已保存", target.name)
        return "已保存：${target.absolutePath}"
    }

    private fun recordPixivImage(url: String, target: java.io.File, bytes: Long) {
        val stem = target.nameWithoutExtension
        val postId = stem.removePrefix("pixiv_").substringBefore("_p")
        com.venera.desktop.downloads.DesktopDownloadsDb.recordImage(
            site = "pixiv",
            postId = postId,
            title = "",
            fileName = target.name,
            bytes = bytes,
            dimensions = "",
            sourceUrl = url,
            localPath = target.absolutePath,
        )
    }
}
