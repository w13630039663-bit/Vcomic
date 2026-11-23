package com.venera.desktop.cache

import com.google.gson.Gson
import com.venera.engineprobe.ComicInfo
import java.io.File

/**
 * 漫画详情 / 章节目录 / 页表 的**磁盘缓存**（「阅读器和漫画详情页应该做数据持久化」）。
 *
 * ## 存什么
 *
 *  - 详情：`cache/comic/details/<safe(source)>_<safe(comicId)>.json` —— 整颗 `ComicInfo`
 *    （标题/简介/标签/评分/推荐/章节目录都在里面）。再次进详情先用它把屏点亮，
 *    源站那趟照旧在后台跑，回来覆盖 —— 断网/限流时章节表至少还在。
 *  - 页表：`cache/comic/pages/<safe(source)>_<safe(comicId)>_<safe(episode)>.json` ——
 *    页键列表。进阅读器先读它，引擎那一趟失败时退回缓存（图仍走 Coil 自己的磁盘缓存）。
 *
 * ## 纪律
 *
 *  - 写失败**不抛**（缓存是尽力而为；坏文件当没有，下次重写）。
 *  - 文件名净化 + 短哈希：漫画号里可能有 `/`、`#`、中文，直接当文件名会跨目录或撞名。
 */
internal class DesktopComicCache(root: File) {
    private val base = File(root, "comic")
    private val gson = Gson()

    private fun safe(raw: String): String {
        val cleaned = raw.replace(Regex("[^A-Za-z0-9._-]"), "_").take(60)
        return cleaned + "-" + Integer.toHexString(raw.hashCode())
    }

    private fun detailsFile(sourceKey: String, comicId: String) =
        File(File(base, "details"), safe(sourceKey) + "_" + safe(comicId) + ".json")

    private fun pagesFile(sourceKey: String, comicId: String, episodeId: String) =
        File(File(base, "pages"), safe(sourceKey) + "_" + safe(comicId) + "_" + safe(episodeId) + ".json")

    fun readDetails(sourceKey: String, comicId: String): ComicInfo? = runCatching {
        val f = detailsFile(sourceKey, comicId)
        if (!f.isFile) return null
        gson.fromJson(f.readText(), ComicInfo::class.java)
    }.getOrNull()

    fun writeDetails(sourceKey: String, comicId: String, info: ComicInfo) {
        runCatching {
            val f = detailsFile(sourceKey, comicId)
            f.parentFile?.mkdirs()
            f.writeText(gson.toJson(info))
        }
    }

    fun readPages(sourceKey: String, comicId: String, episodeId: String): List<String>? = runCatching {
        val f = pagesFile(sourceKey, comicId, episodeId)
        if (!f.isFile) return null
        val arr = gson.fromJson(f.readText(), Array<String>::class.java) ?: return null
        arr.toList()
    }.getOrNull()

    fun writePages(sourceKey: String, comicId: String, episodeId: String, keys: List<String>) {
        runCatching {
            val f = pagesFile(sourceKey, comicId, episodeId)
            f.parentFile?.mkdirs()
            f.writeText(gson.toJson(keys))
        }
    }
}
