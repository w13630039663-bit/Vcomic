package com.venera.desktop.downloads

import com.venera.desktop.gallery.data.desktopGallerySaveDir
import com.venera.compose.data.platform.PathProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 「本机已下载（图片）」的盘上账（设计稿 ③ · 批"开工"）。
 *
 * ## 形状
 * 纯扫描 + 纯分组，**只读盘不写盘**（删除走壳里那颗 IO 动作）；根目录就是存图落点
 * `desktopGallerySaveDir(paths, customDir)`，读两层：根下散文件 = "站点直存"，
 * 一级子目录 = 一个画师组（归类那步的 saver 往子目录写之后，这一屏立刻看得见）。
 *
 * ## 为什么在壳的 IO 线程调
 * 目录 walk + 尺寸头探测（只读 header，不全解码）合起来对上百张图是几十毫秒级，
 * 放点击线程就是卡顿；屏那头永远只吃算好的 `List`。
 */
internal data class DesktopLocalImageItem(
    /** 绝对路径（首张或主图路径，用于显示封面）。 */
    val path: String,
    /** 相对落点根的那一级目录名；根下散文件 = 空串（"站点直存"组）。 */
    val dir: String,
    val fileName: String,
    val bytes: Long,
    val mtime: Long,
    /** "1244×1800" 那一档；解码读不出（视频/坏档）给空串，屏上不摆假尺寸。 */
    val dimensions: String,
    /** 该作品下的全部关联图片绝对路径（Pixiv多图时包含全部p0, p1...，用于批量删除）。 */
    val allPaths: List<String> = listOf(path),
    /** 图片页数（Pixiv多图时 > 1）。 */
    val pageCount: Int = 1,
) {
    /** 站点提示：pixiv 存图统一 `pixiv_` 前缀；其余认文件名首段，认不出念"本机"。 */
    val siteHint: String
        get() = when {
            fileName.startsWith("pixiv") -> "pixiv"
            else -> fileName.substringBefore('_').takeIf { it.length in 3..14 } ?: "本机"
        }
    val isAnimatedOrVideo: Boolean
        get() = extension in VIDEO_EXT || extension == "gif"
    private val extension: String
        get() = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
}

private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "avif")
private val VIDEO_EXT = setOf("mp4", "webm", "mkv")
private val WANTED_EXT = IMAGE_EXT + VIDEO_EXT

private val IMAGE_NAME_SUFFIX = Regex("""\.[A-Za-z0-9]+$""")

private val KNOWN_DOWNLOAD_PREFIXES = listOf(
    "pixiv_", "gelbooru_", "yande_", "danbooru_", "konachan_", "konachan_r18_", "safebooru_", "ehentai_", "exhentai_"
)

/** 仅识别由本软件正式下载的画廊图片文件，过滤外部其他文件 */
internal fun isSoftwareDownloadedImage(fileName: String): Boolean {
    val lower = fileName.lowercase(Locale.ROOT)
    return KNOWN_DOWNLOAD_PREFIXES.any { lower.startsWith(it) }
}

/** 标题档：`gelbooru_15207693.jpg` → `gelbooru · 15207693`；`pixiv_123456_p0.jpg` → `pixiv · 123456`。 */
internal fun desktopLocalImageTitle(item: DesktopLocalImageItem): String {
    val stem = item.fileName.replace(IMAGE_NAME_SUFFIX, "")
    val normalizedStem = if (stem.startsWith("pixiv_") && "_p" in stem) {
        stem.substringBefore("_p")
    } else {
        stem
    }
    val (site, id) = normalizedStem.split('_', limit = 2).let {
        if (it.size == 2) it[0] to it[1] else null to normalizedStem
    }
    return if (site != null && id.all { c -> c.isLetterOrDigit() }) "$site · $id" else normalizedStem
}

/** 扫描落点根；仅收录软件下载的画廊图片，并将 Pixiv 多图作品聚合成单个卡片。 */
internal fun scanDesktopLocalImages(paths: PathProvider?, customDir: String?): List<DesktopLocalImageItem> {
    if (paths == null) return emptyList()
    val root = desktopGallerySaveDir(paths, customDir)
    if (!root.isDirectory) return emptyList()
    val rawList = ArrayList<DesktopLocalImageItem>()
    fun collect(dir: File, relDir: String) {
        val files = dir.listFiles() ?: return
        for (f in files) {
            if (!f.isFile) continue
            // 严格过滤：必须是本软件支持的格式，且必须带有软件下载前缀
            if (!isSoftwareDownloadedImage(f.name)) continue
            val ext = f.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
            if (ext !in WANTED_EXT) continue
            rawList += DesktopLocalImageItem(
                path = f.absolutePath,
                dir = relDir,
                fileName = f.name,
                bytes = f.length(),
                mtime = f.lastModified(),
                dimensions = if (ext in VIDEO_EXT) "" else probeDesktopImageSize(f),
            )
        }
    }
    collect(root, "")
    root.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name.lowercase(Locale.ROOT) }
        ?.forEach { collect(it, it.name) }

    // Pixiv 多图作品聚合为单张卡片
    val pixivGrouped = LinkedHashMap<String, MutableList<DesktopLocalImageItem>>()
    val nonPixiv = ArrayList<DesktopLocalImageItem>()
    for (item in rawList) {
        if (item.fileName.startsWith("pixiv_")) {
            val stem = item.fileName.replace(IMAGE_NAME_SUFFIX, "")
            val workId = if ("_p" in stem) stem.substringBefore("_p") else stem
            pixivGrouped.getOrPut(workId) { mutableListOf() }.add(item)
        } else {
            nonPixiv.add(item)
        }
    }
    val out = ArrayList<DesktopLocalImageItem>()
    out.addAll(nonPixiv)
    for ((_, items) in pixivGrouped) {
        val sorted = items.sortedBy { it.fileName }
        val head = sorted.first()
        val totalBytes = sorted.sumOf { it.bytes }
        val latestMtime = sorted.maxOf { it.mtime }
        out.add(
            head.copy(
                bytes = totalBytes,
                mtime = latestMtime,
                allPaths = sorted.map { it.path },
                pageCount = sorted.size,
            )
        )
    }
    return out.sortedByDescending { it.mtime }
}

/** 只读图档 header 拿宽高（不解码像素）；抛了就交空串。 */
private fun probeDesktopImageSize(file: File): String = runCatching {
    javax.imageio.ImageIO.createImageInputStream(file).use { ins ->
        val reader = javax.imageio.ImageIO.getImageReaders(ins).asSequence().firstOrNull() ?: return ""
        try {
            reader.setInput(ins, true, true)
            "${reader.getWidth(0)}×${reader.getHeight(0)}"
        } finally {
            reader.dispose()
        }
    }
}.getOrDefault("")

internal enum class DesktopImageGroupMode(val label: String) {
    ARTIST("按画师"),
    TIME("按时间"),
    SITE("按站点"),
    NONE("不分组"),
}

/** 分组键算一颗纯函数（判据可无头喂数据钉住）。 */
internal fun desktopImageGroupKey(item: DesktopLocalImageItem, mode: DesktopImageGroupMode): String = when (mode) {
    DesktopImageGroupMode.ARTIST -> item.dir
    DesktopImageGroupMode.TIME -> SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date(item.mtime))
    DesktopImageGroupMode.SITE -> item.siteHint
    DesktopImageGroupMode.NONE -> ""
}

/** 组名那一行：画师组的空串念"（站点直存 / 无画师）"，与"无画师"这一档对齐设计稿。 */
internal fun desktopImageGroupLabel(key: String, mode: DesktopImageGroupMode): String = when {
    key.isBlank() -> if (mode == DesktopImageGroupMode.ARTIST) "（站点直存 / 无画师）" else "全部"
    else -> key
}

/** 按分组键聚堆；组内保持 mtime 序，组间按"最新那张"降序（时间档按日期字典序降序）。 */
internal fun groupDesktopLocalImages(
    items: List<DesktopLocalImageItem>,
    mode: DesktopImageGroupMode,
): List<Pair<String, List<DesktopLocalImageItem>>> {
    if (mode == DesktopImageGroupMode.NONE) return listOf("" to items)
    return items.groupBy { desktopImageGroupKey(it, mode) }
        .toList()
        .sortedByDescending { (_, list) -> list.maxOf { it.mtime } }
}

/** 删除回执句：成 N 删不动 M，各点名（0 也要念得出是哪一档）。 */
internal fun desktopImageDeleteReceipt(deleted: Int, failed: Int): String = when {
    deleted == 0 && failed == 0 -> "没选中任何一张 ⇒ 一颗文件都没动。"
    failed == 0 -> "已删掉 $deleted 张（盘上没了才是没了）。"
    else -> "删掉 $deleted 张，$failed 张删不动（占用/权限，盘上还在）。"
}

/** IO 腿：逐颗删文件，交回 (删掉数, 删不动数)。只在壳的 IO 线程调。 */
internal fun deleteDesktopLocalImages(paths: List<String>): Pair<Int, Int> {
    var ok = 0
    var bad = 0
    for (p in paths) {
        if (runCatching { File(p).delete() }.getOrDefault(false)) ok++ else bad++
    }
    return ok to bad
}
