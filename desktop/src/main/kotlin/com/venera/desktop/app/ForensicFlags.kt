package com.venera.desktop.app

import com.venera.desktop.gallery.data.DesktopDecoTier
import com.venera.desktop.gallery.data.DesktopReaderMode
import com.venera.desktop.gallery.ui.DesktopArtistTarget
import com.venera.desktop.gallery.ui.DesktopComicReading
import com.venera.desktop.gallery.ui.DesktopComicTarget
import com.venera.desktop.gallery.ui.DesktopDetailTier
import com.venera.desktop.gallery.ui.DesktopDomain
import com.venera.desktop.gallery.ui.DesktopGalleryHomeSectionKey
import com.venera.desktop.gallery.ui.DesktopRankingSeed
import com.venera.desktop.gallery.ui.desktopDetailTierLabel
import java.time.LocalDate

internal const val START_DOMAIN_FLAG = "--start-domain"
internal const val OPEN_FLAG = "--open"
internal const val ARTIST_FLAG = "--artist"
internal const val RANKING_FLAG = "--ranking"
internal const val COMIC_FLAG = "--comic"
internal const val DOWNLOAD_FLAG = "--download"
internal const val DOWNLOAD_CANCEL_AFTER_FLAG = "--download-cancel-after"
internal const val DELETE_FLAG = "--delete"
internal const val REPAIR_FLAG = "--repair"
internal const val READ_FLAG = "--read"
internal const val READ_PAGE_FLAG = "--read-page"
internal const val READ_MODE_FLAG = "--read-mode"
internal const val DECO_FLAG = "--deco"
internal const val DETAIL_TIER_FLAG = "--detail-tier"

/** `--start-domain=` 的三种读数。 */
internal sealed class StartDomainReadout {
    object NotGiven : StartDomainReadout()
    data class Resolved(val domain: DesktopDomain, val raw: String) : StartDomainReadout()
    data class Unresolved(val raw: String) : StartDomainReadout()
}

/** `--open=` 的三种读数。 */
internal sealed class OpenScreenReadout {
    object NotGiven : OpenScreenReadout()
    data class Resolved(
        val tab: com.venera.compose.components.VeneraNavTab?,
        val route: DesktopRoute?,
        val raw: String,
        val openFirstGalleryPost: Boolean = false,
    ) : OpenScreenReadout()
    data class Unresolved(val raw: String) : OpenScreenReadout()
}

/** `--artist=` 的三种读数。 */
internal sealed class ArtistReadout {
    object NotGiven : ArtistReadout()
    data class Resolved(val target: DesktopArtistTarget, val raw: String) : ArtistReadout()
    data class Unresolved(val raw: String, val why: String) : ArtistReadout()
}

/** `--ranking=` 的三种读数。 */
internal sealed class RankingReadout {
    object NotGiven : RankingReadout()
    data class Resolved(val seed: DesktopRankingSeed, val raw: String) : RankingReadout()
    data class Unresolved(val raw: String, val why: String) : RankingReadout()
}

/**
 * 取证命令行参数统一判读入口与 `KNOWN` 清单。
 *
 * 具体解析逻辑拆至 [ForensicFlagsScreenSeeds] 与 [ForensicFlagsComicSeeds]。
 */
internal object ForensicFlags {

    private const val START_DOMAIN = START_DOMAIN_FLAG
    private const val QUERY = "--query"
    private const val START_SECTION = "--start-section"
    private const val DETAIL = "--detail"
    private const val ARTIST = ARTIST_FLAG
    private const val RANKING = RANKING_FLAG
    private const val COMIC = COMIC_FLAG
    private const val DISABLE = "--disable"
    private const val COOKIE = "--cookie"
    private const val READ = READ_FLAG
    private const val READ_PAGE = READ_PAGE_FLAG
    private const val READ_MODE = READ_MODE_FLAG
    private const val DOWNLOAD = DOWNLOAD_FLAG
    private const val DOWNLOAD_CANCEL_AFTER = DOWNLOAD_CANCEL_AFTER_FLAG
    private const val DELETE = DELETE_FLAG
    private const val REPAIR = REPAIR_FLAG
    private const val REVERSE = "--reverse"
    private const val REVERSE_FILE = "--reverse-file"
    private const val VIEWER = "--viewer"
    private const val VIEWER_ZOOM = "--viewer-zoom"
    private const val DETAIL_TIER = DETAIL_TIER_FLAG
    private const val DECO = DECO_FLAG
    private const val COPY = "--copy"
    private const val SAVE = "--save"
    private const val CRASH = "--crash"
    private const val OPEN = OPEN_FLAG

    val KNOWN = listOf(
        "--proxy", "--shot", "--autofav", "--favcheck",
        START_DOMAIN, QUERY, START_SECTION, DETAIL, ARTIST, RANKING, REVERSE, REVERSE_FILE,
        VIEWER, VIEWER_ZOOM, COPY, SAVE, CRASH, COMIC, READ, READ_PAGE, READ_MODE, DISABLE, COOKIE, DETAIL_TIER, DOWNLOAD, DELETE, REPAIR,
        DECO, DOWNLOAD_CANCEL_AFTER, OPEN,
    )

    private val ON = setOf("1", "true", "on")
    private val OFF = setOf("0", "false", "off")

    fun state(args: List<String>, name: String): Boolean? {
        val given = args.firstOrNull { it == name || it.startsWith("$name=") } ?: return null
        val raw = given.removePrefix("$name=").takeIf { it != given }
        if (raw == null) {
            println("D_取证参数 $name 已给（不带值），按开处理")
            return true
        }
        val value = raw.trim().lowercase()
        return when {
            value in ON -> {
                println("D_取证参数 $name 开（值=$raw）")
                true
            }
            value in OFF -> {
                println("D_取证参数 $name 参数给了但值为关（值=$raw），本轮不执行")
                false
            }
            else -> {
                println("D_取证参数 $name 值无法判读（值=$raw），按关处理")
                false
            }
        }
    }

    fun crash(args: List<String>): String? {
        val given = args.firstOrNull { it.startsWith("--crash=") } ?: return null
        val raw = given.removePrefix("--crash=").trim().lowercase()
        return when (raw) {
            "thread", "edt" -> {
                println("D_取证参数 --crash 崩一笔（值=$raw）")
                raw
            }
            else -> {
                println("D_取证参数 --crash 值无法判读（值=$raw），只认 thread 与 edt，本轮不崩")
                null
            }
        }
    }

    fun viewerZoom(args: List<String>): Float? {
        val given = args.firstOrNull { it == VIEWER_ZOOM || it.startsWith("$VIEWER_ZOOM=") } ?: return null
        val raw = given.removePrefix("$VIEWER_ZOOM=")
        val value = raw.toFloatOrNull()
        if (value == null || value.isNaN()) {
            println("D_取证参数 $VIEWER_ZOOM=$raw 不是个数，本轮不放大（不替你猜一个倍数）")
            return null
        }
        println("D_取证参数 $VIEWER_ZOOM 起窗后放大到 $value")
        return value
    }

    fun startDomain(args: List<String>): StartDomainReadout = parseForensicStartDomain(args)

    fun open(args: List<String>): OpenScreenReadout = parseForensicOpenScreen(args)

    fun openNote(readout: OpenScreenReadout): String? = when (readout) {
        is OpenScreenReadout.NotGiven -> null
        is OpenScreenReadout.Resolved -> null
        is OpenScreenReadout.Unresolved ->
            "D_取证参数 --open 认不出（值=${readout.raw.ifBlank { "<没带值>" }}）—— 本轮停在默认那一屏（首页）。" +
                "可选值：底栏 HOME/FAVORITES/SEARCH/GALLERY/EXPLORE，二级页 HISTORY/SOURCES/QUEUE/DOWNLOADS/LOCALCOMICS/READINGSTATS/BACKUP/GALLERYSEARCH/BOOKMARKSHUB/GALLERYHISTORY/SETTINGS，取证 GALLERYVIEWER"
    }

    fun query(args: List<String>): String? {
        val given = args.firstOrNull { it == QUERY || it.startsWith("$QUERY=") } ?: return null
        val raw = given.removePrefix("$QUERY=").takeIf { it != given }
        return if (raw == null) {
            println("D_取证参数 --query 已给（不带值），按空查询起（那是空态那一屏，不是没给）")
            ""
        } else {
            println("D_取证参数 --query 预填搜索框（值=$raw）")
            raw
        }
    }

    fun queryNote(query: String?, domain: DesktopDomain): String? =
        if (query == null || domain == DesktopDomain.SEARCH) {
            null
        } else {
            "D_取证参数 --query=$query 落在「${domain.title}」域，那一屏没有搜索框，" +
                "本轮不预填任何东西（要它生效请配 --start-domain=SEARCH）"
        }

    fun reverseFile(args: List<String>): String? {
        val given = args.firstOrNull { it == REVERSE_FILE || it.startsWith("$REVERSE_FILE=") } ?: return null
        val raw = given.removePrefix("$REVERSE_FILE=").takeIf { it != given }
        return if (raw.isNullOrBlank()) {
            println("D_取证参数 --reverse-file 没带上那一条路径（本轮不挑图，看下面那一句）")
            null
        } else {
            println("D_取证参数 --reverse-file 替用户挑好那一张（值=$raw）")
            raw
        }
    }

    fun reverseFileNote(raw: String?, resolved: String?, domain: DesktopDomain): String? = when {
        raw == null -> null
        resolved == null -> "D_取证参数 --reverse-file 没有那一串路径（给了空值）⇒ 本轮不挑图，那一格照旧等用户自己按"
        domain != DesktopDomain.SEARCH -> "D_取证参数 --reverse-file=$resolved 落在「${domain.title}」域，那一格住在「搜索」那一屏 ⇒ 本轮不挑图（要它生效请配 --start-domain=SEARCH）"
        else -> null
    }

    fun unknown(args: List<String>): List<String> =
        args.filter { it.startsWith("--") && KNOWN.none { name -> it == name || it.startsWith("$name=") } }

    fun section(args: List<String>): String? {
        val given = args.firstOrNull { it == START_SECTION || it.startsWith("$START_SECTION=") } ?: return null
        val raw = given.removePrefix("$START_SECTION=").takeIf { it != given }
        return if (raw == null) {
            println("D_取证参数 --start-section 已给（不带值），那是对不上任何行的一枚，屏上不会预选任何东西")
            ""
        } else {
            println("D_取证参数 --start-section 要去到那一行（值=$raw）")
            raw
        }
    }

    fun sectionNote(section: String?, matched: Boolean, domain: DesktopDomain): String? =
        if (section == null || matched) {
            null
        } else {
            "D_取证参数 --start-section=$section 在「${domain.title}」域对不上任何一行（键与题名都不认），" +
                "本轮没预选任何东西 ⇒ 屏上是这一域的第一行，不是那一行"
        }

    fun artist(args: List<String>): ArtistReadout = parseForensicArtist(args)

    fun comic(args: List<String>): DesktopComicTarget? = parseForensicComic(args)

    fun download(args: List<String>): Pair<String, String>? = parseForensicDownload(args)

    fun downloadNote(seed: Pair<String, String>?, detailOpens: Boolean): String? =
        if (seed != null && !detailOpens) {
            "D_取证参数 $DOWNLOAD=${seed.first}/${seed.second} 没有配套的 --comic ⇒ 本轮不开详情那一屏，那一话没种进去（不下载）"
        } else {
            null
        }

    fun downloadCancelAfter(args: List<String>): Int? = parseForensicDownloadCancelAfter(args)

    fun downloadCancelAfterNote(after: Int?, downloadSeed: Pair<String, String>?, detailOpens: Boolean): String? =
        if (after != null && (downloadSeed == null || !detailOpens)) {
            "D_取证参数 $DOWNLOAD_CANCEL_AFTER=$after 没有配套的 $DOWNLOAD（或详情那一屏没开）⇒ 本轮没有可停的那一趟"
        } else {
            null
        }

    fun delete(args: List<String>): Pair<String, String>? = parseForensicDelete(args)

    fun deleteNote(seed: Pair<String, String>?, comicDomainOpens: Boolean): String? =
        if (seed != null && !comicDomainOpens) {
            "D_取证参数 $DELETE=${seed.first}/${seed.second} 起始域不是漫画 ⇒ 那一屏根本没开，本轮不动盘（不删）"
        } else {
            null
        }

    fun repair(args: List<String>): Pair<String, String>? = parseForensicRepair(args)

    fun repairNote(seed: Pair<String, String>?, comicDomainOpens: Boolean): String? =
        if (seed != null && !comicDomainOpens) {
            "D_取证参数 $REPAIR=${seed.first}/${seed.second} 起始域不是漫画 ⇒ 那一屏根本没开，本轮不排队（不补齐）"
        } else {
            null
        }

    fun disable(args: List<String>): String? {
        val given = args.firstOrNull { it == DISABLE || it.startsWith("$DISABLE=") } ?: return null
        val raw = given.removePrefix("$DISABLE=").let { if (it == given) "" else it }
        if (raw.isBlank()) {
            println("D_取证参数 --disable 没带上那一串（本轮不折任何停用，参数打错了）")
            return null
        }
        return raw
    }

    fun disableNote(seed: String?, keys: List<String>): String? = when {
        seed == null -> null
        seed in keys -> "D_取证参数 --disable=$seed 清单里有这一颗 ⇒ 已并进本次装载（只这一趟；要动盘上那份得在源管理那一屏点一下）"
        else -> "D_取证参数 --disable=$seed 清单里没有这一颗 ⇒ 本次装载一颗也没多停用"
    }

    fun cookies(args: List<String>): List<String> {
        val given = args.filter { it == COOKIE || it.startsWith("$COOKIE=") }
        if (given.isEmpty()) return emptyList()
        val kept = given.mapNotNull { raw ->
            val value = raw.removePrefix("$COOKIE=").let { if (it == raw) "" else it }
            if (value.isBlank()) {
                println("D_取证参数 --cookie 那一串是空的（这一枚不并进去）")
                null
            } else value
        }
        println("D_取证参数 --cookie 收到=${given.size} 可用=${kept.size}")
        return kept
    }

    fun read(args: List<String>, startPage: Int?): DesktopComicReading? = parseForensicRead(args, startPage)

    fun readPage(args: List<String>): Int? = parseForensicReadPage(args)

    fun readMode(args: List<String>): DesktopReaderMode? = parseForensicReadMode(args)

    fun decoTier(args: List<String>): DesktopDecoTier? = parseForensicDecoTier(args)

    fun detailTier(args: List<String>): DesktopDetailTier? = parseForensicDetailTier(args)

    fun detailTierNote(seed: DesktopDetailTier?, overlayOpens: Boolean): String? =
        if (seed != null && !overlayOpens) {
            "D_取证参数 $DETAIL_TIER=${desktopDetailTierLabel(seed)} 没有配套的 --detail ⇒ 本轮不开浮层，那一档没种进去"
        } else {
            null
        }

    fun readPageNote(startPage: Int?, seed: DesktopComicReading?): String? =
        if (startPage != null && seed == null) {
            "D_取证参数 $READ_PAGE=$startPage 没有配套的 --read ⇒ 本轮不进阅读屏，那一页没种进去"
        } else {
            null
        }

    fun artistNote(readout: ArtistReadout, flagDomain: DesktopDomain?): String? =
        buildForensicArtistNote(readout, flagDomain)

    fun ranking(args: List<String>, todayUtc: LocalDate): RankingReadout =
        parseForensicRanking(args, todayUtc)

    fun rankingNote(
        readout: RankingReadout,
        flagDomain: DesktopDomain?,
        flagSection: String?,
        artistWins: Boolean,
    ): String? = buildForensicRankingNote(readout, flagDomain, flagSection, artistWins)

    internal data class ForensicLanding(val domain: DesktopDomain, val section: String?)

    fun rankingLanding(
        readout: RankingReadout,
        flagDomain: DesktopDomain,
        flagSection: String?,
        artistWins: Boolean,
    ): ForensicLanding = when {
        readout !is RankingReadout.Resolved || artistWins -> ForensicLanding(flagDomain, flagSection)
        else -> ForensicLanding(DesktopDomain.GALLERY, DesktopGalleryHomeSectionKey.RANKING)
    }

    fun comicLanding(
        seed: DesktopComicTarget?,
        landing: ForensicLanding,
        takenOverByOther: Boolean,
    ): ForensicLanding = when {
        seed == null || takenOverByOther -> landing
        landing.domain == DesktopDomain.COMIC -> landing
        else -> ForensicLanding(DesktopDomain.COMIC, null)
    }

    fun comicNote(seed: DesktopComicTarget?, landing: ForensicLanding, takenOverByOther: Boolean): String? = when {
        seed == null -> null
        takenOverByOther -> "--comic=${seed.comicId} 没接管落点（与 --artist / --ranking 同给，赢的是那一枚）" +
            "，而详情那一屏只在「${DesktopDomain.COMIC.title}」域里 ⇒ 本轮不进详情屏"
        landing.domain != DesktopDomain.COMIC -> "--comic=${seed.comicId} 认得出，而起始域没跟过去" +
            "（落在「${landing.domain.title}」）⇒ 本轮不进详情屏"
        else -> "D_取证参数 --comic=${seed.comicId} 起始域跟着落到「${DesktopDomain.COMIC.title}」" +
            buildForensicComicLandingRowNote(landing.section)
    }

    fun readLanding(
        seed: DesktopComicReading?,
        landing: ForensicLanding,
        takenOverByOther: Boolean,
    ): ForensicLanding =
        if (seed == null || takenOverByOther) landing else ForensicLanding(DesktopDomain.COMIC, null)

    fun readNote(seed: DesktopComicReading?, landing: ForensicLanding, takenOverByOther: Boolean): String? = when {
        seed == null -> null
        takenOverByOther -> "--read=${seed.comicId}/${seed.episodeId} 没接管落点（与 --artist / --ranking 同给，赢的是那一枚）" +
            "，而阅读那一屏只在「${DesktopDomain.COMIC.title}」域里 ⇒ 本轮不进阅读屏"
        landing.domain != DesktopDomain.COMIC -> "--read=${seed.comicId}/${seed.episodeId} 认得出，而起始域没跟过去" +
            "（落在「${landing.domain.title}」）⇒ 本轮不进阅读屏"
        else -> "D_取证参数 --read=${seed.comicId}/${seed.episodeId} 起始域跟着落到「${DesktopDomain.COMIC.title}」"
    }

    fun detailPanel(detail: Boolean?, reverse: Boolean?, copy: Boolean?, save: Boolean?): Boolean =
        reverse == true || copy == true || save == true || detail == true

    fun reverseNote(detail: Boolean?, reverse: Boolean?): String? =
        if (reverse == true && detail != true) {
            "D_取证参数 --reverse 要按的那一枚按钮在详情浮层里 ⇒ 本轮把浮层一起摆出来" +
                "（--detail ${if (detail == null) "没给" else "值为关"}）"
        } else {
            null
        }

    fun overlaySeedNote(detail: Boolean?, copy: Boolean?, save: Boolean?): String? {
        if (detail == true) return null
        val seeds = listOfNotNull(
            "--copy（复制图片）".takeIf { copy == true },
            "--save（存到本机）".takeIf { save == true },
        )
        if (seeds.isEmpty()) return null
        return "D_取证参数 ${seeds.joinToString("、")} 要按的按钮在详情浮层里 ⇒ 本轮把浮层一起摆出来" +
            "（--detail ${if (detail == null) "没给" else "值为关"}）"
    }

    fun bothGivenNote(autofav: Boolean?, favcheck: Boolean?): String? {
        if (autofav == null || favcheck == null) return null
        return if (favcheck) {
            "D_取证参数 --autofav 与 --favcheck 同给，本轮只跑 favcheck（它读回后就退出进程，autofav 的写动作不执行）"
        } else {
            "D_取证参数 --autofav 与 --favcheck 同给，favcheck 的值为关 ⇒ 不提前退出，本轮跑 autofav"
        }
    }
}
