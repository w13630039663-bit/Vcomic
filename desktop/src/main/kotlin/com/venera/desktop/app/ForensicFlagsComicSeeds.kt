package com.venera.desktop.app

import com.venera.desktop.gallery.data.DesktopDecoTier
import com.venera.desktop.gallery.data.DesktopReaderMode
import com.venera.desktop.gallery.ui.DesktopComicReading
import com.venera.desktop.gallery.ui.DesktopComicTarget
import com.venera.desktop.gallery.ui.DesktopDetailTier
import com.venera.desktop.gallery.ui.DesktopDomain
import com.venera.desktop.gallery.ui.desktopDetailTierLabel
import com.venera.desktop.gallery.ui.desktopDomainRows

/** 号段百分号解码：`%2F` → `/`，而 `%25` 最后才解以保字面 `%2F`。 */
internal fun desktopForensicSegment(raw: String): String =
    raw.replace("%2F", "/").replace("%2f", "/").replace("%25", "%")

internal fun parseForensicComic(args: List<String>): DesktopComicTarget? {
    val given = args.firstOrNull { it == COMIC_FLAG || it.startsWith("$COMIC_FLAG=") } ?: return null
    val raw = given.removePrefix("$COMIC_FLAG=").let { if (it == given) "" else it }
    if (raw.isBlank()) {
        println("D_取证参数 --comic 没带上那一串（本轮不进详情屏，参数打错了）")
        return null
    }
    val slash = raw.indexOf('/')
    val comicId = desktopForensicSegment(if (slash < 0) raw else raw.substring(0, slash))
    if (comicId.isBlank()) {
        println("D_取证参数 --comic=$raw 漫画号段是空的（要写成 <漫画号>[/<题名>]，例如 12345；号里要放斜杠就写 %2F）")
        return null
    }
    val title = if (slash < 0) comicId else raw.substring(slash + 1).ifBlank { comicId }
    println("D_取证参数 --comic 起窗后直接进详情屏（号=$comicId 题名=${if (slash < 0) "<未给，用号充题名>" else "已给"}）")
    return DesktopComicTarget(comicId, title, "")
}

internal fun parseForensicDownload(args: List<String>): Pair<String, String>? {
    val given = args.firstOrNull { it == DOWNLOAD_FLAG || it.startsWith("$DOWNLOAD_FLAG=") } ?: return null
    val raw = given.removePrefix("$DOWNLOAD_FLAG=").let { if (it == given) "" else it }
    val slash = raw.indexOf('/')
    if (raw.isBlank() || slash <= 0 || slash == raw.length - 1) {
        println("D_取证参数 $DOWNLOAD_FLAG=$raw 不是两截（要写成 <漫画号>/<话号>，例如 1461316/1461318；号里要放斜杠就写 %2F；本轮不下载）")
        return null
    }
    val comicId = desktopForensicSegment(raw.substring(0, slash))
    val episodeId = raw.substring(slash + 1)
    println("D_取证参数 $DOWNLOAD_FLAG 起窗后下载整话（号=$comicId 话=$episodeId 走的是长按那一条路）")
    return comicId to episodeId
}

internal fun parseForensicDownloadCancelAfter(args: List<String>): Int? {
    val given = args.firstOrNull { it == DOWNLOAD_CANCEL_AFTER_FLAG || it.startsWith("$DOWNLOAD_CANCEL_AFTER_FLAG=") }
        ?: return null
    val raw = given.removePrefix("$DOWNLOAD_CANCEL_AFTER_FLAG=").let { if (it == given) "" else it }
    val n = raw.trim().toIntOrNull()
    if (n == null) {
        println("D_取证参数 $DOWNLOAD_CANCEL_AFTER_FLAG=$raw 不是一个数（要写成 --download-cancel-after=3；本轮不停手）")
        return null
    }
    if (n <= 0) {
        println("D_取证参数 $DOWNLOAD_CANCEL_AFTER_FLAG=$n 不是正数（0 与负数当没给，本轮不停手 —— 与「没给这枚参数」同形的那一档不收）")
        return null
    }
    println("D_取证参数 $DOWNLOAD_CANCEL_AFTER_FLAG 这一趟写下 $n 页后停手（扳的是屏上那枚「取消」同一颗闸）")
    return n
}

internal fun parseForensicDelete(args: List<String>): Pair<String, String>? {
    val given = args.firstOrNull { it == DELETE_FLAG || it.startsWith("$DELETE_FLAG=") } ?: return null
    val raw = given.removePrefix("$DELETE_FLAG=").let { if (it == given) "" else it }
    val slash = raw.indexOf('/')
    if (raw.isBlank() || slash <= 0 || slash == raw.length - 1) {
        println("D_取证参数 $DELETE_FLAG=$raw 不是两截（要写成 <漫画号>/<话号>，例如 1461316/1461318；号里要放斜杠就写 %2F；本轮不删）")
        return null
    }
    val comicId = desktopForensicSegment(raw.substring(0, slash))
    val chapterId = raw.substring(slash + 1)
    println("D_取证参数 $DELETE_FLAG 起窗后删掉那一话（号=$comicId 话=$chapterId 走的是屏上那颗把手第二次按下那一条路）")
    return comicId to chapterId
}

internal fun parseForensicRepair(args: List<String>): Pair<String, String>? {
    val given = args.firstOrNull { it == REPAIR_FLAG || it.startsWith("$REPAIR_FLAG=") } ?: return null
    val raw = given.removePrefix("$REPAIR_FLAG=").let { if (it == given) "" else it }
    val slash = raw.indexOf('/')
    if (raw.isBlank() || slash <= 0 || slash == raw.length - 1) {
        println("D_取证参数 $REPAIR_FLAG=$raw 不是两截（要写成 <漫画号>/<话号>，例如 1461316/1461318；号里要放斜杠就写 %2F；本轮不补齐）")
        return null
    }
    val comicId = desktopForensicSegment(raw.substring(0, slash))
    val chapterId = raw.substring(slash + 1)
    println("D_取证参数 $REPAIR_FLAG 起窗后把那一话排回队列（号=$comicId 话=$chapterId 走的是屏上那颗「补齐」把手同一条路）")
    return comicId to chapterId
}

internal fun parseForensicRead(args: List<String>, startPage: Int?): DesktopComicReading? {
    val given = args.firstOrNull { it == READ_FLAG || it.startsWith("$READ_FLAG=") } ?: return null
    val raw = given.removePrefix("$READ_FLAG=").let { if (it == given) "" else it }
    if (raw.isBlank()) {
        println("D_取证参数 --read 没带上那一串（本轮不进阅读屏，参数打错了）")
        return null
    }
    val parts = raw.split('/')
    val comicId = desktopForensicSegment(parts[0])
    if (comicId.isBlank()) {
        println("D_取证参数 --read=$raw 漫画号段是空的（要写成 <漫画号>[/<话号>[/<章名>]]，例如 12345/g；号里要放斜杠就写 %2F）")
        return null
    }
    val episodeId = parts.getOrNull(1)?.trim().orEmpty()
    val episodeName = parts.drop(2).joinToString("/").trim()
    println(
        "D_取证参数 --read 起窗后直接进阅读屏（号=$comicId 话=${if (episodeId.isEmpty()) "<未给，按占位章>" else episodeId}" +
            " 章名=${if (episodeName.isEmpty()) "<未给>" else "已给"}）",
    )
    return DesktopComicReading(
        comicId = comicId,
        title = comicId,
        episodeId = episodeId,
        episodeName = episodeName,
        placeholder = episodeId.isEmpty(),
        episodeIndex = 0,
        startPage = startPage ?: 0,
        coverUrl = "",
    )
}

internal fun parseForensicReadPage(args: List<String>): Int? {
    val given = args.firstOrNull { it == READ_PAGE_FLAG || it.startsWith("$READ_PAGE_FLAG=") } ?: return null
    val raw = given.removePrefix("$READ_PAGE_FLAG=").takeIf { it != given }
    if (raw == null) {
        println("D_取证参数 $READ_PAGE_FLAG 没带上那一串（本轮不种起点页，从第一页起）")
        return null
    }
    val value = raw.trim().toIntOrNull()
    if (value == null) {
        println("D_取证参数 $READ_PAGE_FLAG=$raw 不是个数，本轮不种起点页（不替你猜一个页号）")
        return null
    }
    if (value < 0) {
        println("D_取证参数 $READ_PAGE_FLAG=$value 是负数：页号从 0 起，本轮不种起点页")
        return null
    }
    println("D_取证参数 $READ_PAGE_FLAG 起窗后先落到第 ${value + 1} 页（索引=$value）")
    return value
}

internal fun parseForensicReadMode(args: List<String>): DesktopReaderMode? {
    val given = args.firstOrNull { it.startsWith("$READ_MODE_FLAG=") } ?: return null
    val raw = given.removePrefix("$READ_MODE_FLAG=").trim()
    if (raw.isEmpty()) {
        println("D_取证参数 $READ_MODE_FLAG 没带上那一串（本轮不顶档，用盘上那一档）")
        return null
    }
    val hit = DesktopReaderMode.entries.firstOrNull {
        it.key.equals(raw, ignoreCase = true) || it.name.equals(raw, ignoreCase = true)
    }
    if (hit == null) {
        println("D_取证参数 $READ_MODE_FLAG=$raw 认不出是哪一档（认键与枚举名，逐字照对岸那张表）：本轮不顶档")
        return null
    }
    println("D_取证参数 $READ_MODE_FLAG=$raw 认成 档=${hit.label}")
    return hit
}

internal fun parseForensicDecoTier(args: List<String>): DesktopDecoTier? {
    val given = args.firstOrNull { it == DECO_FLAG || it.startsWith("$DECO_FLAG=") } ?: return null
    val raw = given.removePrefix("$DECO_FLAG=").takeIf { it != given }
    if (raw == null || raw.isBlank()) {
        println("D_取证参数 $DECO_FLAG 没带上那一串（本轮不改装饰档，用盘上那一档）")
        return null
    }
    val hit = DesktopDecoTier.entries.firstOrNull { it.label == raw || it.key == raw }
    if (hit == null) {
        println("D_取证参数 $DECO_FLAG=$raw 认不出（屏名是 素/纹样/神社，键名是 plain/pattern/shrine）⇒ 本轮不改档")
        return null
    }
    println("D_取证参数 $DECO_FLAG=$raw 认成 档=${hit.label}")
    return hit
}

internal fun parseForensicDetailTier(args: List<String>): DesktopDetailTier? {
    val given = args.firstOrNull { it == DETAIL_TIER_FLAG || it.startsWith("$DETAIL_TIER_FLAG=") } ?: return null
    val raw = given.removePrefix("$DETAIL_TIER_FLAG=").takeIf { it != given }
    if (raw == null || raw.isBlank()) {
        println("D_取证参数 $DETAIL_TIER_FLAG 没带上那一串（本轮不改档位，用设置里那一档）")
        return null
    }
    val value = raw.trim()
    val hit = DesktopDetailTier.entries.firstOrNull {
        it.name.equals(value, ignoreCase = true) || desktopDetailTierLabel(it) == value
    }
    if (hit == null) {
        println("D_取证参数 $DETAIL_TIER_FLAG=$value 认不出是哪一档（认枚举名与屏名 预览/大图/原图），不替你猜一档")
        return null
    }
    println("D_取证参数 $DETAIL_TIER_FLAG=$value 认成 档=${desktopDetailTierLabel(hit)}（${hit.name}）")
    return hit
}

internal fun buildForensicComicLandingRowNote(section: String?): String {
    val rows = desktopDomainRows(DesktopDomain.COMIC)
    val hit = rows.firstOrNull { it.key == section }
    return if (hit == null) "，行交回第一行「${rows.first().title}」" else "，行留在「${hit.title}」"
}
