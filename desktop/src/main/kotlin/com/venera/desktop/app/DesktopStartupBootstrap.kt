package com.venera.desktop.app

import com.venera.desktop.data.DesktopErrorBus
import com.venera.compose.data.platform.JsonKeyValueStore
import com.venera.compose.data.platform.NoInteractiveBypassTag
import com.venera.compose.data.platform.PathProvider
import com.venera.compose.data.platform.PreferenceKeys
import com.venera.compose.gallery.data.PixivUgoiraStore
import com.venera.compose.gallery.domain.GalleryRankings
import com.venera.desktop.gallery.data.DesktopReaderMode
import com.venera.desktop.gallery.ui.DesktopDomain
import com.venera.desktop.gallery.ui.DesktopGalleryHomeSectionKey
import com.venera.desktop.gallery.ui.desktopDomainRows
import com.venera.desktop.platform.DesktopCloudflareBridge
import com.venera.desktop.platform.DesktopLineProbe
import com.venera.desktop.reader.DesktopReaderPreferences
import com.venera.desktop.settings.DesktopBackdropMode
import com.venera.desktop.settings.DesktopCanvasFrame
import com.venera.desktop.settings.DesktopComicCardLayout
import com.venera.desktop.settings.DesktopCoverAspect
import com.venera.desktop.settings.DesktopErrorToastDuration
import com.venera.desktop.settings.DesktopGridDensity
import com.venera.desktop.settings.DesktopHomeSkin
import com.venera.desktop.settings.DesktopLanguage
import com.venera.desktop.settings.DesktopMascot
import com.venera.desktop.settings.DesktopPetVisibility
import com.venera.desktop.settings.DesktopPximgMirror
import com.venera.desktop.settings.DesktopRailBlur
import com.venera.desktop.settings.DesktopReaderBackground
import com.venera.desktop.settings.DesktopTagTranslation
import com.venera.desktop.settings.DesktopThemeMode
import com.venera.desktop.settings.DesktopTopBarBlur
import com.venera.desktop.settings.DesktopUiScale
import com.venera.desktop.ui.components.DesktopPetCue
import com.venera.desktop.ui.components.DesktopPetCueBus
import com.venera.engineprobe.DesktopCloudflareCenter
import com.venera.engineprobe.DesktopCookieJar
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * 桌面端冷启动偏好回填与时间陪伴线索投递。
 */
internal fun restoreDesktopStartupPreferences(paths: PathProvider) {
    val startupPrefs = JsonKeyValueStore(PreferenceKeys.PREFS_NAME, paths)
    DesktopThemeMode.restore(
        startupPrefs.getString("pref_desktop_theme_mode", null) ?: DesktopThemeMode.DARK,
    )
    DesktopErrorBus.suppressStartupHomeImageToast = true
    DesktopLanguage.restore(
        startupPrefs.getString("pref_desktop_language", null) ?: DesktopLanguage.ZH,
    )
    DesktopErrorToastDuration.restore(
        startupPrefs.getString(DesktopErrorToastDuration.PREF_KEY, null),
    )
    DesktopTagTranslation.restore(
        startupPrefs.getString("pref_desktop_tag_translation", null) ?: DesktopTagTranslation.SYSTEM,
    )
    DesktopHomeSkin.restore(
        startupPrefs.getString("pref_desktop_home_skin", null) ?: DesktopHomeSkin.CLASSIC,
    )
    DesktopUiScale.restore(startupPrefs.getString("pref_desktop_ui_scale", null))
    DesktopCoverAspect.restore(startupPrefs.getString("pref_desktop_cover_aspect", null))
    DesktopGridDensity.restore(startupPrefs.getString("pref_desktop_grid_density", null))
    DesktopComicCardLayout.restore(startupPrefs)
    DesktopCanvasFrame.restore(startupPrefs)
    DesktopMascot.restore(startupPrefs)
    DesktopPetVisibility.restore(startupPrefs.getBoolean("pref_desktop_pet_visible", true))
    DesktopReaderBackground.bind(startupPrefs)
    DesktopReaderPreferences.bind(startupPrefs)
    val backdropOverride = System.getProperty("desktop.backdrop")
        ?: System.getenv("DESKTOP_BACKDROP")
        ?: if (System.getProperty("desktop.dwmsbt") == "2" || System.getenv("DESKTOP_DWMSBT") == "2") "mica" else null
    DesktopRailBlur.restore(startupPrefs.getBoolean("pref_desktop_rail_blur", true))
    DesktopTopBarBlur.restore(startupPrefs.getBoolean("pref_desktop_topbar_blur", true))
    DesktopBackdropMode.bind(startupPrefs, envOverride = backdropOverride)
    DesktopPximgMirror.restore(startupPrefs.getString(DesktopPximgMirror.KEY_PXIMG_MIRROR, null))
    PixivUgoiraStore.customMirrorProvider = { DesktopPximgMirror.host.value }
    // 线路测速上次真数回填；更新链 24h 节流账本同文件。
    DesktopLineProbe.restore(startupPrefs)

    // 陪伴 / 时间档（久别、深夜）。只记上次启动时间，不编造日期、不催睡。
    val now = System.currentTimeMillis()
    val last = startupPrefs.getLong("pref_desktop_last_launch_at", 0L)
    if (last > 0L && now - last >= 3L * 24 * 60 * 60 * 1000) {
        DesktopPetCueBus.emit(DesktopPetCue.LONG_TIME_NO_SEE)
    } else if (LocalTime.now().hour >= 23 || LocalTime.now().hour < 5) {
        DesktopPetCueBus.emit(DesktopPetCue.LATE_NIGHT)
    }
    startupPrefs.put("pref_desktop_last_launch_at", now)
}

/**
 * 注册全局共享 CookieJar 与 Cloudflare 过盾处理器。
 */
internal fun registerDesktopCloudflareBridge(
    paths: PathProvider,
    sharedCookieJar: DesktopCookieJar,
) {
    DesktopCloudflareCenter.registerCookieJar(sharedCookieJar)
    DesktopCloudflareCenter.registerInteractiveBypassExempt { request ->
        request.tag(NoInteractiveBypassTag::class.java) != null
    }
    DesktopCloudflareBridge.proxyArgsProvider = {
        val store = JsonKeyValueStore(PreferenceKeys.PREFS_NAME, paths)
        when (store.getString("pref_proxy_type", "系统") ?: "系统") {
            "直连" -> listOf("--no-proxy-server")
            "手动" -> {
                val host = store.getString("pref_proxy_host", "").orEmpty().trim()
                val port = store.getInt("pref_proxy_port", 0)
                if (host.isNotBlank() && port in 1..65535) {
                    listOf("--proxy-server=http://$host:$port")
                } else {
                    emptyList()
                }
            }
            else -> emptyList()
        }
    }
    DesktopCloudflareCenter.registerBypassHandler { challengeUrl ->
        DesktopCloudflareBridge.bypass(challengeUrl)
    }
}

/**
 * 命令行取证参数解析与落地状态，由 [resolveDesktopForensicLaunch] 产出。
 */
internal data class DesktopForensicLaunch(
    val initialTab: com.venera.compose.components.VeneraNavTab,
    val initialRoute: DesktopRoute,
    val openFirstGalleryPost: Boolean,
    val seedDisabled: Set<String>,
    val seedCookies: List<String>,
    val readerModeSeed: DesktopReaderMode?,
    val autofav: Boolean?,
    val favcheck: Boolean?,
    val detail: Boolean?,
    val reverse: Boolean?,
    val copy: Boolean?,
    val save: Boolean?,
    val viewer: Boolean?,
    val viewerZoom: Float?,
    val detailTierSeed: Any?,
    val startDomain: DesktopDomain,
    val startSection: String?,
)

/**
 * 解析全部命令行取证参数并打印对应实话读数；无法识别的 `--start-domain` 会直接退出进程。
 */
internal fun resolveDesktopForensicLaunch(args: Array<String>): DesktopForensicLaunch {
    val startDomainReadout = ForensicFlags.startDomain(args.asList())
    val flagDomain = when (startDomainReadout) {
        is StartDomainReadout.NotGiven -> DesktopDomain.GALLERY
        is StartDomainReadout.Resolved -> {
            println("D_取证参数 --start-domain 认成「${startDomainReadout.domain.title}」（值=${startDomainReadout.raw} 枚举=${startDomainReadout.domain.name}）")
            startDomainReadout.domain
        }
        is StartDomainReadout.Unresolved -> {
            println(
                "D_取证参数 --start-domain 认不出（值=${startDomainReadout.raw.ifBlank { "<没带值>" }}）" +
                    "—— 不退到默认域：默认域那张图会顶着目标域的文件名成为证据",
            )
            println("D_取证参数 可选值：" + DesktopDomain.entries.joinToString(" ") { "${it.name}／${it.title}" })
            kotlin.system.exitProcess(1)
        }
    }
    val openReadout = ForensicFlags.open(args.asList())
    ForensicFlags.openNote(openReadout)?.let(::println)
    val initialTab = (openReadout as? OpenScreenReadout.Resolved)?.tab ?: com.venera.compose.components.VeneraNavTab.HOME
    val initialRoute = (openReadout as? OpenScreenReadout.Resolved)?.route ?: DesktopRoute.Tab
    val openFirstGalleryPost = (openReadout as? OpenScreenReadout.Resolved)?.openFirstGalleryPost == true

    val artistSeed = ForensicFlags.artist(args.asList())
    val rankingSeed = ForensicFlags.ranking(args.asList(), LocalDate.now(ZoneOffset.UTC))
    val artistWins = artistSeed is ArtistReadout.Resolved
    val domainAfterArtist = if (artistWins) DesktopDomain.ARTISTS else flagDomain
    if (artistSeed is ArtistReadout.Resolved) {
        val hit = artistSeed.target
        println(
            "D_取证参数 --artist=${artistSeed.raw} 认成 站点=${hit.site.displayName} " +
                "标签=${hit.name}（起始域跟着落到「${domainAfterArtist.title}」）",
        )
    }
    ForensicFlags.artistNote(artistSeed, flagDomain = (startDomainReadout as? StartDomainReadout.Resolved)?.domain)
        ?.let(::println)

    val comicSeed = ForensicFlags.comic(args.asList())
    val seedDisabled = listOfNotNull(ForensicFlags.disable(args.asList())).toSet()
    val seedCookies = ForensicFlags.cookies(args.asList())
    val startQuery = ForensicFlags.query(args.asList())
    val rawSection = ForensicFlags.section(args.asList())
    val flagSection = rawSection?.let { wanted ->
        desktopDomainRows(domainAfterArtist).firstOrNull { it.key == wanted || it.title == wanted }?.key
    }

    val landing = ForensicFlags.rankingLanding(
        rankingSeed,
        flagDomain = domainAfterArtist,
        flagSection = flagSection,
        artistWins = artistWins,
    )
    val takenOverByOther = artistWins || rankingSeed is RankingReadout.Resolved
    val comicLanding = ForensicFlags.comicLanding(comicSeed, landing, takenOverByOther)
    ForensicFlags.comicNote(comicSeed, comicLanding, takenOverByOther)?.let(::println)

    // 起点页须在 read 之前解析，保证参数传给 read() 消费。
    val readPageSeed = ForensicFlags.readPage(args.asList())
    val readSeed = ForensicFlags.read(args.asList(), readPageSeed)
    ForensicFlags.readPageNote(readPageSeed, readSeed)?.let(::println)
    val readLanding = ForensicFlags.readLanding(readSeed, comicLanding, takenOverByOther)
    ForensicFlags.readNote(readSeed, readLanding, takenOverByOther)?.let(::println)
    val readerModeSeed = ForensicFlags.readMode(args.asList())

    val downloadSeed = ForensicFlags.download(args.asList())
    val detailOpens = comicSeed != null && comicLanding.domain == DesktopDomain.COMIC
    ForensicFlags.downloadNote(downloadSeed, detailOpens = detailOpens)?.let(::println)
    val downloadCancelAfter = ForensicFlags.downloadCancelAfter(args.asList())
    ForensicFlags.downloadCancelAfterNote(downloadCancelAfter, downloadSeed, detailOpens = detailOpens)?.let(::println)

    val deleteSeed = ForensicFlags.delete(args.asList())
    ForensicFlags.deleteNote(deleteSeed, comicDomainOpens = comicLanding.domain == DesktopDomain.COMIC)?.let(::println)
    val repairSeed = ForensicFlags.repair(args.asList())
    ForensicFlags.repairNote(repairSeed, comicDomainOpens = comicLanding.domain == DesktopDomain.COMIC)?.let(::println)

    val detailTierSeed = ForensicFlags.detailTier(args.asList())
    val startDomain = readLanding.domain
    val startSection = readLanding.section

    if (rankingSeed is RankingReadout.Resolved &&
        landing.domain == DesktopDomain.GALLERY &&
        landing.section == DesktopGalleryHomeSectionKey.RANKING
    ) {
        val hit = rankingSeed.seed
        val row = desktopDomainRows(DesktopDomain.GALLERY).firstOrNull { it.key == DesktopGalleryHomeSectionKey.RANKING }
        println(
            "D_取证参数 --ranking=${rankingSeed.raw} 认成 档=${hit.ranking.label} " +
                "期=${GalleryRankings.periodShortLabel(hit.ranking, hit.anchor) ?: "本期"}" +
                "（起始域跟着落到「${DesktopDomain.GALLERY.title}」" +
                (row?.let { "、起始行跟着落到「${it.title}」" } ?: "，而那一域的行表里没有「排行榜」那一行") + "）",
        )
    }

    val autofav = ForensicFlags.state(args.asList(), "--autofav")
    val favcheck = ForensicFlags.state(args.asList(), "--favcheck")
    val detail = ForensicFlags.state(args.asList(), "--detail")
    val reverse = ForensicFlags.state(args.asList(), "--reverse")
    val copy = ForensicFlags.state(args.asList(), "--copy")
    val save = ForensicFlags.state(args.asList(), "--save")
    val reverseFile = ForensicFlags.reverseFile(args.asList())
    val viewer = ForensicFlags.state(args.asList(), "--viewer")
    val viewerZoom = ForensicFlags.viewerZoom(args.asList())

    ForensicFlags.unknown(args.asList())
        .forEach { println("D_参数 未识别 $it（已忽略，不影响其余参数）") }
    ForensicFlags.bothGivenNote(autofav, favcheck)?.let(::println)
    ForensicFlags.queryNote(startQuery, startDomain)?.let(::println)
    ForensicFlags.sectionNote(rawSection, matched = startSection != null, domain = startDomain)?.let(::println)
    ForensicFlags.rankingNote(
        rankingSeed,
        flagDomain = (startDomainReadout as? StartDomainReadout.Resolved)?.domain,
        flagSection = flagSection ?: rawSection,
        artistWins = artistWins,
    )?.let(::println)
    ForensicFlags.reverseNote(detail, reverse)?.let(::println)
    ForensicFlags.overlaySeedNote(detail, copy, save)?.let(::println)
    ForensicFlags.reverseFileNote(
        raw = args.asList().firstOrNull { it == "--reverse-file" || it.startsWith("--reverse-file=") },
        resolved = reverseFile,
        domain = startDomain,
    )?.let(::println)

    val panelOpen = ForensicFlags.detailPanel(detail, reverse, copy, save)
    ForensicFlags.detailTierNote(detailTierSeed, panelOpen)?.let(::println)

    return DesktopForensicLaunch(
        initialTab = initialTab,
        initialRoute = initialRoute,
        openFirstGalleryPost = openFirstGalleryPost,
        seedDisabled = seedDisabled,
        seedCookies = seedCookies,
        readerModeSeed = readerModeSeed,
        autofav = autofav,
        favcheck = favcheck,
        detail = detail,
        reverse = reverse,
        copy = copy,
        save = save,
        viewer = viewer,
        viewerZoom = viewerZoom,
        detailTierSeed = detailTierSeed,
        startDomain = startDomain,
        startSection = startSection,
    )
}
