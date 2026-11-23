package com.venera.desktop.gallery.data

import com.venera.compose.data.platform.PathProvider
import com.venera.compose.gallery.data.GalleryFeedSnapshot
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.serialization.json.Json

/**
 * 日榜离线快照的**桌面落盘**（与安卓 `GalleryFeedCache` 对位；档形是共享纯模型
 * `gallery/data/GalleryFeedSnapshot.kt`，阶段二 · 域 2 抽出来的那颗）。
 *
 * 安卓那颗落盘件绑 `Context.filesDir`（③ 档，桌面编不过），所以形状照桌面既有的两族：
 *  - 落点：`PathProvider.dataRoot` 根下的一个 JSON 档（与 `gallery_browse_history.json` 同族同层）；
 *  - 写法：临时文件 + **带覆盖语义的**改名（`File.renameTo` 在 Windows 上目标已存在只回 false
 *    而不抛 —— 那条教训 `DesktopGalleryBrowseHistory.kt` 记过原文，这里直接引它的口径）。
 *
 * 读不出来就**当作没有缓存**（静默重来是对的：最坏退成回缓存之前那一次联网加载，用户什么也没丢 ——
 * 这一档存的不是用户产生的数据，是站方的一屏，同安卓那颗 `read` 的注释逐字同轨）。
 *
 * 同步读（不是 suspend）：共享核 `GalleryDailyCore` 的构造里首帧就要 `shouldAutoLoad` 的答案，
 * 异步读抢不过那次判断，冷启动照样发一笔请求、缓存就白存（安卓那颗为什么同步，理由原样成立）。
 */
internal object DesktopGalleryDailySnapshotStore {

    const val FILE_NAME = "gallery_feed_cache.json"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        prettyPrint = false
    }

    fun file(paths: PathProvider): File = File(paths.dataRoot, FILE_NAME)

    fun read(paths: PathProvider): GalleryFeedSnapshot? {
        val f = file(paths)
        if (!f.isFile) return null
        return runCatching { json.decodeFromString<GalleryFeedSnapshot>(f.readText()) }
            .getOrNull()
            ?.takeIf { it.posts.isNotEmpty() }
    }

    /** 落盘失败要**念出来**（与壳里"D_取数 首屏失败"同一族口径）：假成功比不缓存坏 —— 用户下次冷启动
     * 以为有档、其实一直是每次联网。 */
    fun write(paths: PathProvider, snapshot: GalleryFeedSnapshot) {
        runCatching {
            val f = file(paths)
            val tmp = File(f.parentFile, "$FILE_NAME.tmp")
            tmp.writeText(json.encodeToString(snapshot))
            Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }.onFailure {
            println("D_日榜快照 落盘失败 ${it::class.java.simpleName}: ${it.message?.take(160)}")
        }
    }
}
