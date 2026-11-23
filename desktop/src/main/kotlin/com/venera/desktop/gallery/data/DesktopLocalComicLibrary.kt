package com.venera.desktop.gallery.data

import com.venera.compose.download.isPageImageFile
import java.io.File

/**
 * 桌面「本地漫画」那一域的**本级**读侧。
 *
 * ## 这一格补的是 A 档最后一颗纯功能缺口
 *
 * 对岸有一整屏「本地漫画」（`feature/LocalComicScreen.kt`，由 `LocalComicManager.getLocalComics()` 喂），
 * 它认的是**盘上所有攒下来的本子**：只要 `<下载根>/<站>/<号>/<话目录>/` 里有图就认。而桌面这一侧从前只有
 * 那颗**话级**「本机已下载」（`DesktopComicDownloadedList.kt`）—— 它**要求每一话都带
 * `chapter.json` 清单**，于是安卓同机攒的书、CBZ 导进来的裸目录在桌面上**一颗都不认**（那些目录只有图、
 * 没有清单）。本级题名（这本子叫什么）桌面从前也**无处可取**：话级那一屏念的是**号**（`label = comicId`，
 * 批 111 KDoc 明写「盘上没有本子的题名，造一个就是假话」）。这一批把那一格补上。
 *
 * ## 为什么复用第二把尺 `isPageImageFile`，而不是那把魔数尺
 *
 * 本仓有一道铁律：**「这一页在不在」只有一把尺子** `desktopComicPageLooksWhole`，而
 * `DesktopComicPageWholenessTest` ⑤ 把它钉死成「声明 + 五处调用共六处」。这一批**故意不吃那颗**：
 * 魔数尺按 `NNNN.jpg` 的**固定页名 + 前 12 字节**判，而本地裸目录里的页名是任意的（`001.png`、`cover.jpg`…），
 * 拿它去数只会念「这本一页都没有」。对岸这里吃的本就是**另一把尺**：`LocalComicManager` 用
 * `isPageImageFile`（`PageImageFiles.kt`：扩展名 ∈ {jpg,jpeg,png,webp} 且非空），而 `DownloadManager` 用
 * 魔数计数 —— 批 97 的 KDoc 早就把「两端两颗读侧本就不同口径」记在案上。所以本件用 `isPageImageFile` 是
 * **照对岸那颗本级读侧**，不是在桌面偷加第四种「齐不齐」。
 *
 * ⚠️ 两颗尺各数各的树，不许混：魔数尺数的是**桌面自己下的那棵树**（`已写/应写`），`isPageImageFile` 数的是
 * **别人攒的 / 导进来的那棵树**（本地漫画域）。同一棵目录树两处都扫的话，评据里明确分开来源。
 *
 * ## 三条不静默（与同族）
 *
 * `comic_info.json` 读不出 / 缺 title —— **不抛**，题名退回目录名（对岸 `optString("title", dirName)` 同一形）；
 * 某一本没有一章认得出图 —— 那一本**不进表**（不是"空本子"，而是本地域按定义只摆真读得出的）；
 * 整趟扫描由壳里 `runCatching` 兜，塌了壳念「数不出来 + 成因」而不念「本机没有」（这一批屏侧只吃成品表）。
 */

/** 本级一本：[id] 是**净化后的目录名**（对岸 `LocalComic.id = cDir.name`），[title] 是它念得出的题名。 */
internal data class DesktopLocalComic(
    val id: String,
    /** 盘上目录名兜底；有 `comic_info.json.title` 时被它覆盖（与对岸 `getLocalComics` 同一判式）。 */
    val title: String,
    /** 来源那一格：`comic_info.json.sourceKey`，缺则取上一级目录名（对岸同招）。 */
    val sourceKey: String,
    val chapterCount: Int,
    val totalPages: Int,
    val rootPath: String,
    /** 盘上 `lastModified()`，本级排序吃它（对岸 `sortedByDescending { updatedAt }`）。 */
    val updatedAt: Long,
    /** 是否为原地关联导入的外部文件夹（删除时只移除关联记录，不删原磁盘文件）。 */
    val isLinked: Boolean = false,
)

/** 某一话认得出的页数（[title] 走 `chapter.json.title` 否则目录名 —— 与本级题名同一条兜底口径）。 */
internal data class DesktopLocalComicChapter(
    val title: String,
    val pageCount: Int,
)

/**
 * 本级扫描的**一次**读盘结果（给屏侧吃）。三格分三档，各念各的话，不互相冒充。
 *
 * 与那颗 `DesktopComicDownloadListing` 同族而**不共用**：那一颗数的是带清单的话级树，
 * 这一颗数的是认裸目录的本级树。两棵树各有各的空，各有各的数不出来。
 */
internal data class DesktopLocalComicLibraryScan(
    val comics: List<DesktopLocalComic>,
    /** 那一层根在不在盘上（不在 ⇒ 念「还没有那一棵树」而不是「本机一本都没有」）。 */
    val rootExists: Boolean,
    /** 整趟扫描塌了的成因（壳里 `runCatching` 填；非空 ⇒ 表头念这一句而不是念空表）。 */
    val error: String?,
)

/** 表头那句：四档各一句，出自**同一次**扫描。还没数回来那一档由屏用 [desktopLocalComicLibraryPendingLine]。 */
internal fun desktopLocalComicLibraryHeader(scan: DesktopLocalComicLibraryScan): String = when {
    scan.error != null -> "数不出来：${scan.error}"
    !scan.rootExists -> "还没有本机漫画那一棵树（下载根目录尚未建立）"
    scan.comics.isEmpty() -> "本机还没有认得出的本地漫画"
    else -> "本地漫画 ${scan.comics.size} 本 · 共 ${scan.comics.sumOf { it.chapterCount }} 话 " +
        "${scan.comics.sumOf { it.totalPages }} 页"
}

/** 还没数回来那一档（屏侧 `scan == null` 时用），不改口成「本机没有」。 */
internal fun desktopLocalComicLibraryPendingLine(): String = "正在数本机那一棵树…"

/** 一行的那一句：念**题名**（本级域的唯一卖点），后面跟话数页数与来源。 */
internal fun desktopLocalComicLibraryRowLine(comic: DesktopLocalComic): String =
    comic.title + " · ${comic.chapterCount} 话 ${comic.totalPages} 页 · 源 ${comic.sourceKey}"

/**
 * 本级扫描的**取证读数**（只进 stdout，不进界面）—— 与那颗 `desktopComicDownloadedReading`
 * 同族。打的是**同一次**扫描的格：本 / 话 / 页 / 根在不在 / 塌没塌，而不另走一趟盘。
 *
 * ⚠️ `error != null` 时也打（念 `塌=成因`），因为「数不出来」与「确实空了」在屏上长得像、在读数上必须分得开
 * （那一族假话：`本=0` 既可能是真空、也可能是塌了拿不到）。
 */
internal fun desktopLocalComicLibraryReading(scan: DesktopLocalComicLibraryScan): String =
    "D_本地漫画 本=" + scan.comics.size +
    " 话=" + scan.comics.sumOf { it.chapterCount } +
    " 页=" + scan.comics.sumOf { it.totalPages } +
    " 根=" + (if (scan.rootExists) "1" else "0") +
    (if (scan.error != null) " 塌=${scan.error}" else "")

private val FIELD_COMIC_TITLE = Regex("\"title\"\\s*:\\s*\"([^\"]*)\"")
private val FIELD_COMIC_SOURCE = Regex("\"sourceKey\"\\s*:\\s*\"([^\"]*)\"")

/**
 * 数 `<根>/<站>/<号>` 那一层的本子（**本级**），只认那些「至少有一话读得出图」的。
 *
 * 与话级那颗 `parsedDirs` 差一层：那颗数到 `<话目录>` 且要 `chapter.json`；这一颗数到 `<号>` 且只要
 * 它下面有任意目录里装着 `isPageImageFile` 认得的图。散文件、以及 `<号>` 那层直接放的裸图都不参与
 * （对岸也是先 `listFiles { it.isDirectory }` 取 chapterDirs）。
 */
internal fun desktopLocalComics(root: File): List<DesktopLocalComic> {
    if (!root.isDirectory) return emptyList()
    val out = mutableListOf<DesktopLocalComic>()
    val sourceDirs = root.listFiles { f -> f.isDirectory } ?: return emptyList()
    for (srcDir in sourceDirs.sortedBy { it.name }) {
        val comicDirs = srcDir.listFiles { f -> f.isDirectory } ?: continue
        for (cDir in comicDirs.sortedBy { it.name }) {
            val chapters = desktopLocalComicChapters(cDir)
            if (chapters.isEmpty()) continue
            val info = readComicInfo(cDir)
            out += DesktopLocalComic(
                id = cDir.name,
                title = info?.first ?: cDir.name,
                sourceKey = info?.second ?: srcDir.name,
                chapterCount = chapters.size,
                totalPages = chapters.sumOf { it.pageCount },
                rootPath = cDir.absolutePath,
                updatedAt = cDir.lastModified(),
            )
        }
    }
    return out.sortedByDescending { it.updatedAt }
}

/** 探测任意漫画文件夹的话级结构（支持“多子目录分话”和“文件夹内直接放图片”两种排法）。 */
internal data class DesktopLocalFolderChapter(
    val title: String,
    val pageCount: Int,
    val dir: File,
)

/** 解析单个漫画文件夹内的章节分布；既无子话目录也无直属图片时返回空表。 */
internal fun desktopInspectLocalComicFolder(folder: File): List<DesktopLocalFolderChapter> {
    if (!folder.isDirectory) return emptyList()
    val subChapters = folder.listFiles { f -> f.isDirectory }
        ?.sortedBy { it.name }
        ?.mapNotNull { chDir ->
            val pages = chDir.listFiles { f -> f.isPageImageFile() }?.size ?: 0
            if (pages <= 0) null else {
                val manifest = File(chDir, "chapter.json")
                val title = if (manifest.isFile) {
                    runCatching { FIELD_COMIC_TITLE.find(manifest.readText())?.groupValues?.get(1) }.getOrNull()
                        ?.takeIf { it.isNotBlank() } ?: chDir.name
                } else {
                    chDir.name
                }
                DesktopLocalFolderChapter(title = title, pageCount = pages, dir = chDir)
            }
        }
        .orEmpty()
    if (subChapters.isNotEmpty()) return subChapters

    val directPages = folder.listFiles { f -> f.isPageImageFile() }?.size ?: 0
    if (directPages > 0) {
        return listOf(DesktopLocalFolderChapter(title = "全一话", pageCount = directPages, dir = folder))
    }
    return emptyList()
}

/** 某一目录下那些「真有图」的话（本级口径：目录里有 `isPageImageFile` 认得的颗数 ≥1 就算一话）。 */
internal fun desktopLocalComicChapters(comicDir: File): List<DesktopLocalComicChapter> {
    val chapterDirs = comicDir.listFiles { f -> f.isDirectory } ?: return emptyList()
    val out = mutableListOf<DesktopLocalComicChapter>()
    for (chDir in chapterDirs.sortedBy { it.name }) {
        val pages = chDir.listFiles { f -> f.isPageImageFile() }?.size ?: 0
        if (pages == 0) continue
        val manifest = File(chDir, "chapter.json")
        val title = if (manifest.isFile) {
            runCatching { FIELD_COMIC_TITLE.find(manifest.readText())?.groupValues?.get(1) }.getOrNull()
                ?.takeIf { it.isNotBlank() } ?: chDir.name
        } else {
            chDir.name
        }
        out += DesktopLocalComicChapter(title = title, pageCount = pages)
    }
    return out
}

/**
 * 读一本的 `comic_info.json`，交回 title / sourceKey 两格各自**独立**兜底（缺那颗文件或读不出 ⇒ 两格都 `null`，
 * 由调用点各退自己的目录名）。对岸 `getLocalComics` 那两格是两次 `optString`，一本有 sourceKey 而无 title 时
 * 仍认 sourceKey —— 合成一处兜底会把 sourceKey 一起退掉，那是漂。
 */
private fun readComicInfo(comicDir: File): Pair<String?, String?>? {
    val info = File(comicDir, "comic_info.json")
    if (!info.isFile) return null
    val text = runCatching { info.readText() }.getOrNull() ?: return null
    val title = FIELD_COMIC_TITLE.find(text)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
    val source = FIELD_COMIC_SOURCE.find(text)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
    if (title == null && source == null) return null
    return title to source
}
