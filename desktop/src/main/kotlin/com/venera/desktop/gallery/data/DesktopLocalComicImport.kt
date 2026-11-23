package com.venera.desktop.gallery.data

import com.venera.compose.download.isPageImage
import java.io.File
import java.util.zip.ZipFile

/**
 * 桌面「本地导入 CBZ/ZIP」那一颗(tool-suite 稿⑤)。
 *
 * 与对岸 `LocalComicManager.importCbz` **同形不同源**:那颗绑 Context(Log/ComicStorageRoot),
 * 被桌面编译面 exclude;这里照它逐条重走一遍 ——
 *   ① 落点 = `<下载根>/imported/<净化题名>/`,单话 `0001_全一话`;
 *   ② 页判据与扫描侧**同一把尺** [isPageImage];页名**恒 `.jpg`**(与桌面读侧 `desktopComicChapterPageFileName` 同轨,见落盘那段);
 *   ③ 元数据两颗(comic_info.json / chapter.json)逐字同字段;
 *   ④ 成败都念人话:文件不存在 / 不是压缩包 / 一页图都没解出来,各念各的。
 *
 * ⚠️ 返回 [DesktopLocalComicImportOutcome] 而不是 LocalComic:本级屏那颗吃的是
 * [DesktopLocalComic](本级树自己数出来的),导入完**不喂屏一个新对象**,而是让壳 tick 重数那棵树 ——
 * 「导入成功」与「树里数得出它」是两件事,分两步各自念自己的真话。
 */
data class DesktopLocalComicImportOutcome(
    /** `null` = 成功;非空 = 成因原话(屏上照摆,不改写成"导入失败"四个字)。 */
    val error: String?,
    /** 成功时念:解出来的页数(0 页成功不是成功,走 error)。 */
    val pages: Int = 0,
    /** 成功时念:题名(FileDialog 选的文件名去后缀)。 */
    val title: String = "",
)

/** 导入件写进 `comic_info.json` 的来源键；本级扫描侧照它认出「导入的本子」。 */
internal const val DESKTOP_LOCAL_COMIC_IMPORTED_SOURCE = "imported"

private val IMPORT_UNSAFE_TITLE = Regex("[/\\\\:*?\"<>|]")

/**
 * 题名净化与对岸同一式(路径分隔符与 Windows 非法字符 → `_`)。
 *
 * 净化的最后两刀是**防目录穿越**,不是字符洁癖:
 *   - 纯 `.` / `..` 的题名(形如 `...zip` → `nameWithoutExtension` 得 `..`)若原样拿去
 *     拼 `File(imported, title)`,`File(imported,"..")` 会回指**下载根本身**。导入失败时
 *     那段 `targetDir.deleteRecursively()` 就会把全部已下载漫画一起删掉。
 *   - 所以:① 先折叠所有连续点 ② 结果为 `.`/`..` 一律退成 `未命名`, ③ 下面导入件
 *     再用 [isSafeImportChild] 对 `targetDir.canonicalPath` 做一次越界兜底 —— 只删自己
 *     这次真正在 `imported/` 里创建的目录,绝不 `deleteRecursively()` 一个回指根的 `File`。
 */
internal fun desktopLocalComicImportSafeTitle(raw: String): String =
    raw.replace(IMPORT_UNSAFE_TITLE, "_")
        .replace(Regex("\\.{2,}"), "_")
        .trim()
        .let { if (it == "." || it == "..") "未命名" else it }
        .ifBlank { "未命名" }

/** `child` 必须真是 `imported/` 之下的一层,且不是 `imported/` 自己。canonicalPath 断了符号链。 */
private fun isSafeImportChild(imported: File, child: File): Boolean {
    val parent = imported.canonicalPath
    val path = child.canonicalPath
    return path.startsWith(parent + File.separator) &&
        path != parent &&
        child.canonicalFile.parentFile?.canonicalPath == parent
}

/**
 * 导入一颗 CBZ/ZIP 归档。**纯 JVM**(ZipFile + File),不碰 Context / Log;
 * IO 在调用点的 `withContext(Dispatchers.IO)` 里,这一颗只管落盘与判读。
 *
 * @param downloadsRoot 桌面数据根下的下载根(壳现递;`null` = 数据根还没定,导入没地方落)。
 */
internal fun desktopLocalComicImport(
    archiveFile: File,
    downloadsRoot: File?,
): DesktopLocalComicImportOutcome {
    if (downloadsRoot == null) {
        return DesktopLocalComicImportOutcome(error = "导入失败：目录不可用")
    }
    if (!archiveFile.exists() || !archiveFile.isFile) {
        return DesktopLocalComicImportOutcome(error = "导入文件不存在:${archiveFile.name}")
    }
    val title = desktopLocalComicImportSafeTitle(archiveFile.nameWithoutExtension)
    val imported = File(downloadsRoot, "imported")
    val targetDir = File(imported, title)
    // 越界兜底:净化函数已挡掉 `..`,这一道再对 canonicalPath 复核一次。
    // 宁可导入失败并念真话,也绝不把一个可能回指下载根的 File 拿去 mkdirs / deleteRecursively。
    if (!isSafeImportChild(imported, targetDir)) {
        return DesktopLocalComicImportOutcome(error = "导入失败：文件名无效")
    }
    val chapterDir = File(targetDir, "0001_全一话")
    // 只记「本次真新建」的目录:重复导入同名时目录已存在,失败时绝不能连旧的一起删。
    val createdTarget = !targetDir.exists()
    if (createdTarget && !targetDir.mkdirs()) {
        return DesktopLocalComicImportOutcome(error = "建不了目录:${targetDir.path}")
    }
    // 失败清理唯一出口:只删本次新建、且 canonical 复核仍在 imported/ 之下的那一棵。
    fun cleanupFailedImport() {
        if (createdTarget && isSafeImportChild(imported, targetDir)) targetDir.deleteRecursively()
    }
    if (!chapterDir.exists() && !chapterDir.mkdirs()) {
        cleanupFailedImport()
        return DesktopLocalComicImportOutcome(error = "建不了目录:${chapterDir.path}")
    }

    var pageIdx = 1
    runCatching {
        ZipFile(archiveFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory) continue
                // 与扫描侧同一把尺(判据复用的那条口径):jpeg 归一 jpg,页名参与排序。
                if (!isPageImage(entry.name)) continue
                // 页名恒 `.jpg`:桌面读侧 `desktopComicChapterPageFileName` 恒按 `.jpg` 找页,
                // 归档里的 png/webp 若保留原扩展名,翻开就是黑页。内容一字不动,只统一名字
                // —— 这一步与桌面读侧同轨,比对岸的 jpeg→jpg 归一更进一档。
                val out = File(chapterDir, pageIdx.toString().padStart(4, '0') + ".jpg")
                zip.getInputStream(entry).use { input ->
                    out.outputStream().use { output -> input.copyTo(output) }
                }
                pageIdx++
            }
        }
    }.getOrElse { t ->
        cleanupFailedImport()
        return DesktopLocalComicImportOutcome(error = "解压失败:${t.message?.take(160) ?: t.javaClass.simpleName}")
    }

    if (pageIdx == 1) {
        // 一页图都没有 = 里面装的不是漫画(纯目录/纯文档),落盘的半截目录清掉再念真话。
        cleanupFailedImport()
        return DesktopLocalComicImportOutcome(error = "归档里没有认得出的页图,这不是一个漫画包")
    }

    // 元数据两颗,字段与对岸逐字同一份(本级扫描按正则读 title/sourceKey,不挑格式)。
    runCatching {
        File(targetDir, "comic_info.json").writeText(
            """{"id":${jsonStr(title)},"title":${jsonStr(title)},"sourceKey":"imported","updateTime":${System.currentTimeMillis()}}""",
        )
        // 清单五字段与桌面写侧 `desktopComicChapterWriteManifest` 同一份:本级读侧的清单判式
        // (`readChapter`)按 expectedPages 认话、按 complete + 整页判认齐 —— 对岸导入件没写
        // 这两格,照抄它的清单整话认不出。页名既然恒 `.jpg`,整页判也自然过。
        File(chapterDir, "chapter.json").writeText(
            """{"chapterId":"1","title":${jsonStr("全一话")},"order":1,"expectedPages":${pageIdx - 1},"complete":true}""",
        )
    }

    return DesktopLocalComicImportOutcome(error = null, pages = pageIdx - 1, title = title)
}

/** 最小 JSON 字符串字面量(反斜杠与双引号转义;题名是文件名,不会有控制字符)。 */
private fun jsonStr(raw: String): String =
    "\"" + raw.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
