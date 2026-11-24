package com.venera.desktop.app

import com.venera.compose.components.VeneraNavTab
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryRanking
import com.venera.compose.gallery.domain.GalleryRankings
import com.venera.desktop.gallery.ui.DesktopArtistTarget
import com.venera.desktop.gallery.ui.DesktopDomain
import com.venera.desktop.gallery.ui.DesktopGalleryHomeSectionKey
import com.venera.desktop.gallery.ui.DesktopRankingSeed
import java.time.LocalDate

/** 判读 `--start-domain=` 的目标域；认不出时返回 [StartDomainReadout.Unresolved] 供调用方中止启动。 */
internal fun parseForensicStartDomain(args: List<String>): StartDomainReadout {
    val given = args.firstOrNull { it == START_DOMAIN_FLAG || it.startsWith("$START_DOMAIN_FLAG=") }
        ?: return StartDomainReadout.NotGiven
    val raw = given.removePrefix("$START_DOMAIN_FLAG=").let { if (it == given) "" else it }
    val hit = DesktopDomain.entries.firstOrNull {
        it.name.equals(raw, ignoreCase = true) || it.title == raw
    }
    return hit?.let { StartDomainReadout.Resolved(it, raw) } ?: StartDomainReadout.Unresolved(raw)
}

/** 判读 `--open=` 的起窗直达页面或二级路由。 */
internal fun parseForensicOpenScreen(args: List<String>): OpenScreenReadout {
    val given = args.firstOrNull { it == OPEN_FLAG || it.startsWith("$OPEN_FLAG=") }
        ?: return OpenScreenReadout.NotGiven
    val raw = given.removePrefix("$OPEN_FLAG=").let { if (it == given) "" else it }
    if (raw.isBlank()) return OpenScreenReadout.Unresolved(raw)
    if (raw.trim().equals("galleryviewer", ignoreCase = true)) {
        println("D_取证参数 $OPEN_FLAG=$raw 认成 底栏格=画廊（GALLERY）+ 到货后打开第一张可点卡")
        return OpenScreenReadout.Resolved(
            tab = VeneraNavTab.GALLERY,
            route = null,
            raw = raw,
            openFirstGalleryPost = true,
        )
    }
    val tab = VeneraNavTab.entries.firstOrNull {
        it.name.equals(raw, ignoreCase = true) || it.title == raw
    }
    if (tab != null) {
        println("D_取证参数 $OPEN_FLAG=$raw 认成 底栏格=${tab.title}（${tab.name}）")
        return OpenScreenReadout.Resolved(tab = tab, route = null, raw = raw)
    }
    val route: DesktopRoute? = when (raw.trim().lowercase()) {
        "history" -> DesktopRoute.History
        "sources" -> DesktopRoute.SourceManage
        "queue" -> DesktopRoute.Queue
        "downloads" -> DesktopRoute.Downloads
        "localcomics" -> DesktopRoute.LocalComics
        "gallerysearch" -> DesktopRoute.GallerySearch
        "bookmarkshub" -> DesktopRoute.GalleryBookmarksHub
        "readingstats" -> DesktopRoute.ReadingStats
        "backup" -> DesktopRoute.Backup
        "galleryhistory" -> DesktopRoute.GalleryBrowseHistory
        "settings" -> DesktopRoute.Settings
        else -> null
    }
    return if (route != null) {
        println("D_取证参数 $OPEN_FLAG=$raw 认成 二级页=$raw")
        OpenScreenReadout.Resolved(tab = null, route = route, raw = raw)
    } else {
        OpenScreenReadout.Unresolved(raw)
    }
}

/** 判读 `--artist=<站点>/<标签>` 画师单人页直达种子。 */
internal fun parseForensicArtist(args: List<String>): ArtistReadout {
    val given = args.firstOrNull { it == ARTIST_FLAG || it.startsWith("$ARTIST_FLAG=") } ?: return ArtistReadout.NotGiven
    val raw = given.removePrefix("$ARTIST_FLAG=").let { if (it == given) "" else it }
    val slash = raw.indexOf('/')
    if (slash < 0) {
        return ArtistReadout.Unresolved(raw, "没有站点段（要写成 <站点>/<标签>，例如 ${GallerySite.entries.first().routeKey}/某标签）")
    }
    val siteKey = raw.substring(0, slash)
    val site = GallerySite.entries.firstOrNull {
        it.routeKey.equals(siteKey, ignoreCase = true) ||
            it.name.equals(siteKey, ignoreCase = true) ||
            it.displayName == siteKey
    } ?: return ArtistReadout.Unresolved(raw, "站点认不出（站点段=$siteKey，可选：" + forensicSiteKeys() + "）")
    val name = raw.substring(slash + 1)
    if (name.isBlank()) return ArtistReadout.Unresolved(raw, "标签段是空的")
    return ArtistReadout.Resolved(DesktopArtistTarget(site, name), raw)
}

internal fun forensicSiteKeys(): String = GallerySite.entries.joinToString(" ") { it.routeKey }

internal fun buildForensicArtistNote(readout: ArtistReadout, flagDomain: DesktopDomain?): String? = when (readout) {
    ArtistReadout.NotGiven -> null
    is ArtistReadout.Unresolved ->
        "D_取证参数 --artist=${readout.raw.ifBlank { "<没带值>" }} 读不出：${readout.why}" +
            " —— 本轮没进单人页，屏上仍是起始那一域（可选站点：" + forensicSiteKeys() + "）"
    is ArtistReadout.Resolved ->
        if (flagDomain == null || flagDomain == DesktopDomain.ARTISTS) {
            null
        } else {
            "D_取证参数 --artist=${readout.raw} 要落在「${DesktopDomain.ARTISTS.title}」那一域的单人页，" +
                "而 --start-domain 指的是「${flagDomain.title}」⇒ 本轮域名以 --artist 为准" +
                "（单人页在那一域的第二层，域留在「${flagDomain.title}」时那一屏不在组合里）"
        }
}

/** 判读 `--ranking=<档>[/<期>]` 排行榜档位与历史期锚点。 */
internal fun parseForensicRanking(args: List<String>, todayUtc: LocalDate): RankingReadout {
    val given = args.firstOrNull { it == RANKING_FLAG || it.startsWith("$RANKING_FLAG=") } ?: return RankingReadout.NotGiven
    val raw = given.removePrefix("$RANKING_FLAG=").let { if (it == given) "" else it }
    val slash = raw.indexOf('/')
    val rankPart = if (slash < 0) raw else raw.substring(0, slash)
    val periodPart = if (slash < 0) null else raw.substring(slash + 1)
    val ranking = GalleryRanking.entries.firstOrNull {
        it.name.equals(rankPart, ignoreCase = true) || it.label == rankPart || it.shortLabel == rankPart
    } ?: return RankingReadout.Unresolved(
        raw,
        "档段认不出（档段=${rankPart.ifBlank { "<没给档段>" }}，可选：" + forensicRankingKeys() + "）",
    )
    if (periodPart == null) return RankingReadout.Resolved(DesktopRankingSeed(ranking, null), raw)
    if (!GalleryRankings.supportsHistory(ranking)) {
        return RankingReadout.Unresolved(
            raw,
            "「${ranking.label}」这一档不给回看历史期，只有「${GalleryRanking.MONTH.label}」与" +
                "「${GalleryRanking.YEAR.label}」两档给（期段=$periodPart）",
        )
    }
    val anchor = parseForensicRankingPeriod(ranking, periodPart) ?: return RankingReadout.Unresolved(
        raw,
        "期段「$periodPart」的写法不对（月档写 2024-03、年档写 2019）",
    )
    if (anchor.isBefore(GalleryRankings.EARLIEST_PERIOD)) {
        return RankingReadout.Unresolved(
            raw,
            "那一早于地板 ${GalleryRankings.EARLIEST_PERIOD}（站方存档再往前回空，拍出来是一张空墙）",
        )
    }
    if (anchor.isAfter(todayUtc)) {
        return RankingReadout.Unresolved(
            raw,
            "那一还没到（本期是 $todayUtc）—— 窗口终点恒被今天钉住，种进去只会拍到一处被夹回本期的读数",
        )
    }
    return RankingReadout.Resolved(DesktopRankingSeed(ranking, anchor), raw)
}

private fun parseForensicRankingPeriod(ranking: GalleryRanking, period: String): LocalDate? {
    val trimmed = period.trim()
    return when (ranking) {
        GalleryRanking.MONTH -> Regex("""(\d{4})-(\d{1,2})""").matchEntire(trimmed)?.let { m ->
            val month = m.groupValues[2].toInt()
            if (month in 1..12) LocalDate.of(m.groupValues[1].toInt(), month, 1) else null
        }
        else -> Regex("""(\d{4})""").matchEntire(trimmed)?.let { LocalDate.of(it.groupValues[1].toInt(), 1, 1) }
    }
}

private fun forensicRankingKeys(): String = GalleryRanking.entries.joinToString(" ") { it.name }

internal fun buildForensicRankingNote(
    readout: RankingReadout,
    flagDomain: DesktopDomain?,
    flagSection: String?,
    artistWins: Boolean,
): String? = when (readout) {
    RankingReadout.NotGiven -> null
    is RankingReadout.Unresolved ->
        "D_取证参数 --ranking=${readout.raw.ifBlank { "<没带值>" }} 读不出：${readout.why}" +
            " —— 本轮没进排行榜那一屏，屏上仍是起始那一域（可选档：" + forensicRankingKeys() + "）"
    is RankingReadout.Resolved -> {
        val clauses = buildList {
            if (artistWins) {
                add(
                    "--artist 与 --ranking=${readout.raw} 同给，起始域已被 --artist 定成" +
                        "「${DesktopDomain.ARTISTS.title}」⇒ 本轮 --ranking 的档与期次没种进去",
                )
            }
            if (flagDomain != null && flagDomain != DesktopDomain.GALLERY) {
                add(
                    "--ranking=${readout.raw} 要落在「${DesktopDomain.GALLERY.title}」域的排行榜那一行，" +
                        "而 --start-domain 指的是「${flagDomain.title}」⇒ 本轮域以 --ranking 为准",
                )
            }
            if (!artistWins && flagSection != null && flagSection != DesktopGalleryHomeSectionKey.RANKING) {
                add(
                    "--ranking=${readout.raw} 要落在「排行榜」那一行，而 --start-section 给的是" +
                        "$flagSection ⇒ 本轮行以 --ranking 为准（那一行的键=${DesktopGalleryHomeSectionKey.RANKING}）",
                )
            }
        }
        clauses.joinToString("；").takeIf { it.isNotEmpty() }?.let { "D_取证参数 $it" }
    }
}
