package com.venera.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.venera.desktop.ui.toShortcutSignalOrNull
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.rememberWindowState
import dev.nucleusframework.application.DecoratedWindow
import dev.nucleusframework.application.NucleusBackend
import dev.nucleusframework.application.nucleusApplication
import dev.nucleusframework.window.WindowsBackdrop
import dev.nucleusframework.window.windowDragArea
import com.venera.engineprobe.DesktopJsHost
import com.venera.engineprobe.EngineAssets
import com.venera.engineprobe.EngineSession
import com.venera.compose.data.db.LocalFavoritesManager
import com.venera.compose.data.platform.CacheDirs
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.gallery.domain.GalleryRankings
import com.venera.desktop.platform.DesktopDatabasePorts
import com.venera.desktop.platform.DesktopPaths
import com.venera.desktop.platform.DesktopPixivCallback
import com.venera.desktop.platform.DesktopProtocolRegistrar
import com.venera.desktop.platform.DesktopSingleInstance
import com.venera.desktop.platform.desktopRunLogFile
import com.venera.desktop.platform.desktopRunLogReading
import com.venera.desktop.platform.installDesktopRunLog
import com.venera.desktop.platform.desktopPixivCallbackUrl
import com.venera.desktop.gallery.data.DesktopGalleryPorts
import com.venera.desktop.gallery.data.DesktopImagePipeline
import com.venera.desktop.gallery.data.DesktopSourceState
import com.venera.desktop.gallery.data.DesktopTagDictionaryAsset
import com.venera.desktop.gallery.data.DesktopComicReaderPreferences
import com.venera.desktop.gallery.data.DesktopGalleryBrowseHistory
import com.venera.desktop.gallery.data.desktopComicDownloadsRoot
import com.venera.desktop.gallery.ui.DesktopDomain
import com.venera.desktop.gallery.ui.DesktopGalleryHomeSectionKey
import com.venera.desktop.gallery.ui.DesktopGalleryMetrics
import com.venera.desktop.search.DesktopComicSearchOutcome
import com.venera.desktop.gallery.ui.desktopCanCopyImage
import com.venera.desktop.gallery.ui.desktopDomainRows
import com.venera.desktop.ui.components.DesktopPetCue
import com.venera.desktop.ui.components.DesktopPetCueBus
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicLong
import javax.imageio.ImageIO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext



/**
 * R1-F 的**端到端最小闭环**：真源脚本 → GraalJS 宿主 → 真网络取图 → skia 解码 → Fluent 窗口渲染。
 *
 * 跑法：
 * ```
 * ./gradlew :desktop:app -Pkey=jm -Pproxy=127.0.0.1:7890
 * ./gradlew :desktop:app -Pshot=_qa/desktop-search.png -PstartDomain=SEARCH
 * ./gradlew :desktop:app -Pshot=_qa/desktop-artist.png -Partist=yandere/某标签
 * ./gradlew :desktop:app -Pshot=_qa/desktop-ranking-month.png -Pranking=MONTH/
 * ```
 * 带 `--shot=路径` 时它自己截图后退出（无人值守取证用），否则就是个能点的窗口。
 * 带 `--start-domain=` 时取证落在指定那一域（七枚都认，见 ForensicFlags.startDomain）；
 * 认不出就直接退出码 1，**不退到默认域** —— 默认域那张图会顶着目标域的文件名成为证据。
 * 带 `--artist=` 时直接进那一位的单人页（见 ForensicFlags.artist）：那一屏本来要**点一下**才出来。
 * 带 `--ranking=` 时把排行榜拨到那一档、那一期（见 ForensicFlags.ranking）：
 * 「上一期 / 下一期 / 回到本期」那一排本来要把档拨到月/年之后才出现，而鼠标自验是禁的。
 *
 * 这一件的目的是**把"路线走不走得通"从一堆单点读数变成一个可运行的东西**：
 * 任何一环（引擎、桥协议、Cookie、gzip、WebP 解码、UI 组合）断了，窗口里就直接看得见。
 */
// 窗题与安装器/exe 描述同一句；窗内两处 FindWindowW 找 hwnd 都吃这一份常量，改动须同步。

/** 取证参数的判读口径与读数都在 [ForensicFlags]（那颗是可测的；这里只是接线） */

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
fun main(args: Array<String>) {
    if (args.any { it.equals("--installer-mode", ignoreCase = true) }) {
        com.venera.desktop.installer.main(
            args.filterNot { it.equals("--installer-mode", ignoreCase = true) }.toTypedArray(),
        )
        return
    }
    // 崩溃归属那一行（批 53）：**第一件事**，早于任何取数与开窗 ——
    // 批 47 那次崩在启动阶段（Coil 的 ImageLoader 缺 swing dispatcher），装晚了就正好管不到那一段。
    installDesktopCrashHandler()
    val root = File(System.getProperty("user.dir"))
    // 仓库目录只是兜底（资源已自持进 desktop/src/main/resources）：包内命中时这条压根不会被读
    // （每次取用都会打一条"来源=包内/仓库"的读数，见 EngineAssets）
    val assetDir = File(root, "desktop/src/main/resources")
    // 阶段 1：数据目录收进 DesktopPaths —— 语义与旧的 localDataDir() 一致，
    // 取不到 %LOCALAPPDATA% 就启动即抛，绝不退回 java.io.tmpdir。
    val paths = DesktopPaths.create()
    // 读数落盘：这一格必须排在**任何 D_* 读数之前**（`D_启动` 就在下面几十行），
    // 否则盘上那一份天生缺头几行，而"文件在而里面不全"正是这一族最难回头查的假证据。
    // 装上之后 stdout 一字不动（原样两路都有），只是多了一份 UTF-8 的盘上副本。
    val runLogFile = desktopRunLogFile(paths.dataRoot, java.time.LocalDateTime.now(), ProcessHandle.current().pid())
    val runLog = installDesktopRunLog(runLogFile)
    println(desktopRunLogReading(runLog, runLogFile.path))
    // 字体：字体族读数（排在日志落盘之后，盘上那一份也带这行）。
    // bundled=false 时界面静默回退系统字体 —— 这一格只有这行能看出来。
    run {
        val bundled = com.venera.desktop.ui.theme.VeneraFonts.bundled
        println(
            "D_字体 随包 MiSans=" + bundled +
                (if (bundled) " 四档齐" else " 未随包 ⇒ 回退系统字体（查 resources/fonts 与 build.gradle 白名单）"),
        )
    }
    val proxy = args.firstOrNull { it.startsWith("--proxy=") }?.removePrefix("--proxy=")
    // 源更新：代理同一颗读数经系统属性递给引擎侧的更新检查件（单源，不开第二份解析）。
    if (proxy != null) System.setProperty("venera.proxy", proxy)
    val shot = args.firstOrNull { it.startsWith("--shot=") }?.removePrefix("--shot=")
    // 报错通道自测开关。`--error-test[=edt|thread]`：
    // 开窗后 3s 往真实 handler 里抛一笔假异常，全链路走一遍（读数 → 落 log → 左下角弹卡），
    // 程序必须照常活着。默认档 = EDT（最接近真崩的形状）；=thread 走后台线程档。
    val errorTest = args.firstOrNull { it.startsWith("--error-test") }?.removePrefix("--error-test")
    val pixivCallbackState = MutableStateFlow<DesktopPixivCallback?>(null)
    val callbackSequence = AtomicLong(0)
    fun publishPixivCallback(arguments: List<String>) {
        val url = desktopPixivCallbackUrl(arguments) ?: return
        val id = callbackSequence.incrementAndGet()
        println("D_pixiv 收到浏览器回跳：id=$id（凭据内容不打印）")
        pixivCallbackState.value = DesktopPixivCallback(id = id, url = url)
    }
    publishPixivCallback(args.toList())
    val startKey = args.firstOrNull { !it.startsWith("--") && desktopPixivCallbackUrl(listOf(it)) == null } ?: "jm"
    DesktopProtocolRegistrar.registerIfNeeded(paths.dataRoot)
    // 主题模式冷启动回填（档二）：主 prefs 同名键；没存过保持深色出厂档。
    com.venera.desktop.settings.DesktopThemeMode.restore(
        com.venera.compose.data.platform.JsonKeyValueStore(
            com.venera.compose.data.platform.PreferenceKeys.PREFS_NAME,
            paths,
        ).getString("pref_desktop_theme_mode", null) ?: com.venera.desktop.settings.DesktopThemeMode.DARK,
    )
    // 界面语言冷启动回填：主 prefs 同名键；没存过保持中文出厂档。
    DesktopErrorBus.suppressStartupHomeImageToast = true
    com.venera.desktop.settings.DesktopLanguage.restore(
        com.venera.compose.data.platform.JsonKeyValueStore(
            com.venera.compose.data.platform.PreferenceKeys.PREFS_NAME,
            paths,
        ).getString("pref_desktop_language", null) ?: com.venera.desktop.settings.DesktopLanguage.ZH,
    )
    // 错误弹窗显示时间冷启动回填：默认 3 秒自动消失。
    com.venera.desktop.settings.DesktopErrorToastDuration.restore(
        com.venera.compose.data.platform.JsonKeyValueStore(
            com.venera.compose.data.platform.PreferenceKeys.PREFS_NAME,
            paths,
        ).getString(com.venera.desktop.settings.DesktopErrorToastDuration.PREF_KEY, null),
    )
    // 标签译文档位冷启动回填（档二 5b-1）。
    com.venera.desktop.settings.DesktopTagTranslation.restore(
        com.venera.compose.data.platform.JsonKeyValueStore(
            com.venera.compose.data.platform.PreferenceKeys.PREFS_NAME,
            paths,
        ).getString("pref_desktop_tag_translation", null) ?: com.venera.desktop.settings.DesktopTagTranslation.SYSTEM,
    )
    // 首页皮肤档冷启动回填：主 prefs 同名键；没存过保持经典出厂档。
    com.venera.desktop.settings.DesktopHomeSkin.restore(
        com.venera.compose.data.platform.JsonKeyValueStore(
            com.venera.compose.data.platform.PreferenceKeys.PREFS_NAME,
            paths,
        ).getString("pref_desktop_home_skin", null) ?: com.venera.desktop.settings.DesktopHomeSkin.CLASSIC,
    )
    // 界面缩放、封面画幅与阅读底色冷启动回填。
    run {
        val startupPrefs = com.venera.compose.data.platform.JsonKeyValueStore(
            com.venera.compose.data.platform.PreferenceKeys.PREFS_NAME,
            paths,
        )
        com.venera.desktop.settings.DesktopUiScale.restore(
            startupPrefs.getString("pref_desktop_ui_scale", null),
        )
        com.venera.desktop.settings.DesktopCoverAspect.restore(
            startupPrefs.getString("pref_desktop_cover_aspect", null),
        )
        com.venera.desktop.settings.DesktopGridDensity.restore(
            startupPrefs.getString("pref_desktop_grid_density", null),
        )
        com.venera.desktop.settings.DesktopComicCardLayout.restore(startupPrefs)
        com.venera.desktop.settings.DesktopCanvasFrame.restore(startupPrefs)
        com.venera.desktop.settings.DesktopMascot.restore(startupPrefs)
        com.venera.desktop.settings.DesktopPetVisibility.restore(
            startupPrefs.getBoolean("pref_desktop_pet_visible", true),
        )
        com.venera.desktop.settings.DesktopReaderBackground.bind(startupPrefs)
        com.venera.desktop.reader.DesktopReaderPreferences.bind(startupPrefs)
        val backdropOverride = System.getProperty("desktop.backdrop")
            ?: System.getenv("DESKTOP_BACKDROP")
            ?: if (System.getProperty("desktop.dwmsbt") == "2" || System.getenv("DESKTOP_DWMSBT") == "2") "mica" else null
        com.venera.desktop.settings.DesktopRailBlur.restore(
            startupPrefs.getBoolean("pref_desktop_rail_blur", true),
        )
        com.venera.desktop.settings.DesktopTopBarBlur.restore(
            startupPrefs.getBoolean("pref_desktop_topbar_blur", true),
        )
        com.venera.desktop.settings.DesktopBackdropMode.bind(startupPrefs, envOverride = backdropOverride)
        com.venera.desktop.settings.DesktopPximgMirror.restore(
            startupPrefs.getString(com.venera.desktop.settings.DesktopPximgMirror.KEY_PXIMG_MIRROR, null),
        )
        com.venera.compose.gallery.data.PixivUgoiraStore.customMirrorProvider = {
            com.venera.desktop.settings.DesktopPximgMirror.host.value
        }
    }
    // 线路测速上次真数回填（档二 5b-4）；更新链 24h 节流账本同文件。
    // （只保留深浅模式，旧 prefs 键忽略不读。）
    com.venera.desktop.platform.DesktopLineProbe.restore(
        com.venera.compose.data.platform.JsonKeyValueStore(
            com.venera.compose.data.platform.PreferenceKeys.PREFS_NAME,
            paths,
        ),
    )
    // 陪伴 / 时间档（久别、深夜）。只记「上次启动时间」这一格，
    // 不编造具体日期、不催睡；首次启动不念久别，深夜只念「陪着」。
    run {
        val store = com.venera.compose.data.platform.JsonKeyValueStore(
            com.venera.compose.data.platform.PreferenceKeys.PREFS_NAME,
            paths,
        )
        val now = System.currentTimeMillis()
        val last = store.getLong("pref_desktop_last_launch_at", 0L)
        if (last > 0L && now - last >= 3L * 24 * 60 * 60 * 1000) {
            DesktopPetCueBus.emit(DesktopPetCue.LONG_TIME_NO_SEE)
        } else if (java.time.LocalTime.now().hour >= 23 || java.time.LocalTime.now().hour < 5) {
            DesktopPetCueBus.emit(DesktopPetCue.LATE_NIGHT)
        }
        store.put("pref_desktop_last_launch_at", now)
    }
    val singleInstance = if (shot == null) {
        when (val acquisition = DesktopSingleInstance.acquireOrForward(args.toList(), paths.dataRoot)) {
            is DesktopSingleInstance.Acquisition.Primary -> acquisition.coordinator
            DesktopSingleInstance.Acquisition.ForwardedToPrimary -> kotlin.system.exitProcess(0)
            is DesktopSingleInstance.Acquisition.Unavailable -> {
                println("D_单实例 降级启动：${acquisition.reason}")
                null
            }
        }
    } else {
        null
    }
    val launchListener = singleInstance?.addLaunchListener(::publishPixivCallback)
    // 起始域参数仅用于无人值守取证；无法识别时直接中止启动，不回退默认域以免污染证据。
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
                    "—— 不退到默认域：默认域那张图会顶着目标域的文件名成为证据"
            )
            println("D_取证参数 可选值：" + DesktopDomain.entries.joinToString(" ") { "${it.name}／${it.title}" })
            kotlin.system.exitProcess(1)
        }
    }
    // 起窗直达种子：`--open=` 认这条大屏线真落得下去的那一屏，喂进 route/currentTab 初值。
    // 与 `--start-domain` 分道：后者认已拆掉的 Fluent 那套 DesktopDomain、在这条线全哑（只 println）；
    // 这一枚返回真 DesktopRoute / VeneraNavTab，装配点递进 LargeScreen 的那两枚 initialRoute / initialTab 形参。
    val openReadout = ForensicFlags.open(args.asList())
    ForensicFlags.openNote(openReadout)?.let(::println)
    val initialTab = (openReadout as? OpenScreenReadout.Resolved)?.tab ?: com.venera.compose.components.VeneraNavTab.HOME
    val initialRoute = (openReadout as? OpenScreenReadout.Resolved)?.route ?: DesktopRoute.Tab
    val openFirstGalleryPost = (openReadout as? OpenScreenReadout.Resolved)?.openFirstGalleryPost == true
    // 画师单人页那颗种子：那一屏是"点一下才出来的那一层"（要点列表上的「看他的图」），
    // 而鼠标自验是禁的（手册 §8）—— 批 19 判据全绿而图证那格只能写"没拍成"，缺的就是这一枚。
    val artistSeed = ForensicFlags.artist(args.asList())
    // 排行榜那颗种子：期次那一排是"把档拨到月/年才出现的那一层"，而鼠标自验是禁的
    // （手册 §8）—— 批 29 六条判据全绿而那三枚拍不到，缺的就是这一枚。判读吃 UTC 的今天，因为
    // "早于地板"与"那一还没到"两档都是相对本期判的。
    val rankingSeed = ForensicFlags.ranking(args.asList(), LocalDate.now(ZoneOffset.UTC))
    // 两枚种子同给时赢的是 `--artist`（它先把起始域定成「画师」，而那一屏与排行榜那一行不在同一域里）。
    // 这不是故障而是一次落空，所以另一枚必须自己说清"我这枚没种进去" —— 静默抢跑就是
    // `--autofav` 与 `--favcheck` 原来那个形状（见 ForensicFlags.bothGivenNote）。
    val artistWins = artistSeed is ArtistReadout.Resolved
    // 起始域在此层随种子覆盖，不交由应用根修改；未解析出目标时保持原域不变。
    val domainAfterArtist = if (artistWins) DesktopDomain.ARTISTS else flagDomain
    val artistTarget = (artistSeed as? ArtistReadout.Resolved)?.target
    if (artistSeed is ArtistReadout.Resolved) {
        val hit = artistSeed.target
        println(
            "D_取证参数 --artist=${artistSeed.raw} 认成 站点=${hit.site.displayName} " +
                "标签=${hit.name}（起始域跟着落到「${domainAfterArtist.title}」）"
        )
    }
    // 没落到那一屏的两档实话（读不出 / 顶掉了用户点名的那一域）：不中止，但绝不静默。
    ForensicFlags.artistNote(artistSeed, flagDomain = (startDomainReadout as? StartDomainReadout.Resolved)?.domain)
        ?.let(::println)
    val rankingTarget = (rankingSeed as? RankingReadout.Resolved)?.seed
    // 起窗后直接进哪一本漫画的详情屏：那一屏入口是封面，而鼠标自验是禁的（手册 §8）⇒
    // 不带这枚种子时详情那一屏的图证只能记成"没拍成"。`null` = 不进，停在漫画列表。
    val comicSeed = ForensicFlags.comic(args.asList())
    // 把某一颗随包源当成本台**停用着**起来（批 83.1）：那一格今天只有离屏语义树量过，"列表标灰而装载照旧"
    // 这种缺陷在帧上看不见。种子只活在这一趟（不写盘上那份），认不认得那串由消费点那句读数念。
    val seedDisabled = listOfNotNull(ForensicFlags.disable(args.asList())).toSet()
    // 批 88·下半：`--cookie=<host>:<名>=<值>` 那几枚凭据种子。只并本次装载的罐，不写盘上那份
    //（盘上那份是用户的登录态）。解析在宿主那一层做，理由见 ForensicFlags.cookies 的注释。
    val seedCookies = ForensicFlags.cookies(args.asList())
    // 起始查询串：搜索那一屏是七枚域里唯一"不给输入就拍不到东西"的，而键盘鼠标自验是禁的
    // （手册 §8）。这一枚把查询串变成命令行上的读数，AI 角标那类只在真数据上才出现的形状才拍得到。
    val startQuery = ForensicFlags.query(args.asList())
    // 起始**行**（⑤）：`--start-domain=` 只到域，到不了域内 pane 的那一行 ——
    // 于是图库域里新点亮的「最新」这类页今天拍不到。键与中文题名两种都认（与那颗同一口径）；
    // 对不上任何行不中止（这一枚没有"开错窗"的代价），但绝不静默。
    val rawSection = ForensicFlags.section(args.asList())
    val flagSection = rawSection?.let { wanted ->
        desktopDomainRows(domainAfterArtist).firstOrNull { it.key == wanted || it.title == wanted }?.key
    }
    // 排行榜那颗种子把**起始域与起始行**一起接管（那一屏在域内的第二层：不在「排行榜」那一行上，
    // 档与期都没处种）。`--artist` 赢时一字不改 —— 两枚同给由 rankingNote 念"这一枚没种进去"，
    // 不能再偷偷改用户的落点。接管这件事走纯函数，理由写在 ForensicFlags.rankingLanding 那里。
    val landing = ForensicFlags.rankingLanding(
        rankingSeed,
        flagDomain = domainAfterArtist,
        flagSection = flagSection,
        artistWins = artistWins,
    )
    // 漫画详情种子在 ranking 之后接管起始域，确保详情页所在域被正确组合。
    val takenOverByOther = artistWins || rankingSeed is RankingReadout.Resolved
    val comicLanding = ForensicFlags.comicLanding(comicSeed, landing, takenOverByOther)
    ForensicFlags.comicNote(comicSeed, comicLanding, takenOverByOther)?.let(::println)
    // 起窗后直接进哪一话的阅读那一屏。它排在详情**之后**：`--comic` 与 `--read` 同给时
    // 详情在底下留着（「← 返回详情」回得到），而屏上那一层是阅读。两枚都没给时不改用户的落点。
    // 起点页先判（半）：那一枚是这一枚的一半，次序反了就得让 `read` 自己去摸 args。
    val readPageSeed = ForensicFlags.readPage(args.asList())
    val readSeed = ForensicFlags.read(args.asList(), readPageSeed)
    ForensicFlags.readPageNote(readPageSeed, readSeed)?.let(::println)
    val readLanding = ForensicFlags.readLanding(readSeed, comicLanding, takenOverByOther)
    ForensicFlags.readNote(readSeed, readLanding, takenOverByOther)?.let(::println)
    // 把阅读那一屏顶到某一档模式（`--read-mode=`）。它只作本次初值、不写那枚键 ——
    // 顶过的那一句实话由那一屏自己念（`desktopReaderModeSeedNote`）。
    val readerModeSeed = ForensicFlags.readMode(args.asList())
    // 起窗后把哪一话整话写进本机（`--download=<漫画号>/<话号>`）。
    // 它走的是详情那一屏**长按那一条路**（同一颗 runner、同一套目录名、同一行 `D_下载`），不是另开一条取证专用路。
    val downloadSeed = ForensicFlags.download(args.asList())
    ForensicFlags.downloadNote(downloadSeed, detailOpens = comicSeed != null && comicLanding.domain == DesktopDomain.COMIC)?.let(::println)
    // 那一趟写下几页之后停手（`--download-cancel-after=<N>`，扳的是屏上那枚「取消」同一颗闸）。
    val downloadCancelAfter = ForensicFlags.downloadCancelAfter(args.asList())
    ForensicFlags.downloadCancelAfterNote(
        downloadCancelAfter, downloadSeed, detailOpens = comicSeed != null && comicLanding.domain == DesktopDomain.COMIC,
    )?.let(::println)
    // 起窗后把哪一话从本机删掉（`--delete=<漫画号>/<话号>`，与上面那枚同一把写法）。
    // 它走的是屏上那颗把手**第二次按下**那一条路（同一颗 runner、同一行 `D_删除`），不是另开一条取证专用路。
    val deleteSeed = ForensicFlags.delete(args.asList())
    ForensicFlags.deleteNote(deleteSeed, comicDomainOpens = comicLanding.domain == DesktopDomain.COMIC)?.let(::println)
    val repairSeed = ForensicFlags.repair(args.asList())
    ForensicFlags.repairNote(repairSeed, comicDomainOpens = comicLanding.domain == DesktopDomain.COMIC)?.let(::println)
    // 档位那颗种子（批 92，`--detail-tier=预览|大图|原图`，屏名与枚举名都认）。
    // ⚠️ 它的落点**不在** `tier` 的初值那一格，而在应用根的点卡那一跳 —— 写成初值会被同一次点卡里
    // 的"升档"盖掉（取证那颗 `--detail` 走的正是同一枚 lambda），那是"stdout 念了而屏上没动"那一族。
    val detailTierSeed = ForensicFlags.detailTier(args.asList())
    val startDomain = readLanding.domain
    val startSection = readLanding.section
    // 正面读数只在**真落到那一格**时念：两枚种子同给而赢的是 `--artist` 时，这一枚没接管落点，
    // 这里若照样念一句"起始域跟着落到「图库」"就与下一句（"这一枚没种进去"）互相打脸。
    if (rankingSeed is RankingReadout.Resolved &&
        landing.domain == DesktopDomain.GALLERY &&
        landing.section == DesktopGalleryHomeSectionKey.RANKING
    ) {
        val hit = rankingSeed.seed
        // 行名不写死：那一项的题名从行表里现取，行被改名或被撤下时这句读数会跟着变，
        // 而不会替一屏已经不存在的行作伪证（屏上每句断言都得答得出"哪次量的"）。
        val row = desktopDomainRows(DesktopDomain.GALLERY).firstOrNull { it.key == DesktopGalleryHomeSectionKey.RANKING }
        println(
            "D_取证参数 --ranking=${rankingSeed.raw} 认成 档=${hit.ranking.label} " +
                "期=${GalleryRankings.periodShortLabel(hit.ranking, hit.anchor) ?: "本期"}" +
                "（起始域跟着落到「${DesktopDomain.GALLERY.title}」" +
                (row?.let { "、起始行跟着落到「${it.title}」" } ?: "，而那一域的行表里没有「排行榜」那一行") + "）"
        )
    }
    // --autofav / --favcheck **只为取证存在**（无人值守把"点心收藏 → 重开进程还在"这条链
    // 量成两条 D_收藏 读数）：一个写（收藏第一张卡后读回条数并退出），一个读（启动时不点
    // 任何按钮直接读回条数并退出）。都不是默认行为，不带参数时窗口交互如常。
    val autofav = ForensicFlags.state(args.asList(), "--autofav")
    val favcheck = ForensicFlags.state(args.asList(), "--favcheck")
    // 起窗后自己把详情浮层摆出来（P0/P1）：那一层要**点一张卡**才出来，
    // 而 agent 侧不许动鼠标 ⇒ 不带这枚参数时它永远拍不到图。默认行为一字未动。
    val detail = ForensicFlags.state(args.asList(), "--detail")
    // 起窗后自动按一次「找同一张图」：结果那一格在浮层里，要按一下才出来，
    // 而鼠标自验是禁的 ⇒ 不带这枚参数时批 35 只能拍到那枚按钮。默认行为一字未动。
    val reverse = ForensicFlags.state(args.asList(), "--reverse")
    // 起窗后自动按一次「复制图片」：那一格只有按下去才量得出剪贴板里进了什么。
    val copy = ForensicFlags.state(args.asList(), "--copy")
    // 起窗后自动按一次「存到本机」：这一格唯一的证据是**盘上多出来那颗文件**，
    // 不按一次就量不到。默认行为一字未动。
    val save = ForensicFlags.state(args.asList(), "--save")
    // 起窗后替用户挑好那一张本机的图（批 77 下半）：那一格的入口是**系统文件框**，
    // 而鼠标自验是禁的（手册 §8）⇒ 不带这枚参数时路径之后的三跳结构性地拍不到。默认行为一字未动。
    // ⚠️ 它跳过的正是文件框那一步 ⇒ 那一跳今天没有读数，别把这枚种子的通过当成"挑图验过了"。
    val reverseFile = ForensicFlags.reverseFile(args.asList())
    // 点卡之后进不进大图那一层：`null` = 没给，而"没给"在应用根里是**进**（默认行为），
    // `=0` 才是不进。所以三态原样往下递，不在这里塌成二态 —— `D_落点` 的 `种子=` 那一格要念得出差别。
    val viewer = ForensicFlags.state(args.asList(), "--viewer")
    // 起窗后放大到几倍：`null` = 本轮不放大。给的不是个数时那一颗自己念一句并给 null。
    val viewerZoom = ForensicFlags.viewerZoom(args.asList())
    // 不认识的参数原样报出来，别让用户以为打进去了
    ForensicFlags.unknown(args.asList())
        .forEach { println("D_参数 未识别 $it（已忽略，不影响其余参数）") }
    // 两参同给原来会"静默抢跑"：favcheck 先 exitProcess，autofav 被吃掉且不吭声。现在如实说一句。
    ForensicFlags.bothGivenNote(autofav, favcheck)?.let(::println)
    // 查询串落在没有搜索框的那一域：不中止（域名由 `--start-domain=` 自己念），但必须出声。
    ForensicFlags.queryNote(startQuery, startDomain)?.let(::println)
    // 起始行对不上这一域的任何一行：不中止，但那一句必须念出来（静默 = 假开关的口径）。
    ForensicFlags.sectionNote(rawSection, matched = startSection != null, domain = startDomain)?.let(::println)
    // 排行榜那颗种子没落到那一格的四档实话（读不出 / 被 --artist 抢走 / 域被接管 / 行被接管）。
    // `flagSection ?: rawSection`：认得出就交键，认不出就把用户原样打的那一串递过去 ——
    // 那一枚被种子接管时读数得点名**他打的是什么**，而不是只说"行变了"。
    ForensicFlags.rankingNote(
        rankingSeed,
        flagDomain = (startDomainReadout as? StartDomainReadout.Resolved)?.domain,
        flagSection = flagSection ?: rawSection,
        artistWins = artistWins,
    )?.let(::println)
    // 反搜那颗种子替用户多开了一扇浮层时的那句实话（浮层本来就开着时不吭声）。
    ForensicFlags.reverseNote(detail, reverse)?.let(::println)
    // 「复制图片」与「存到本机」这两枚同给时要念清"为谁多开了这一扇"（
    // 它们按的是同一颗浮层里相邻的两枚按钮，屏上却只多开了一扇窗 —— 两句各提一枚就是两份口径判同一件事）。
    ForensicFlags.overlaySeedNote(detail, copy, save)?.let(::println)
    // 那一枚种子落空时的两句实话（各念各的）：给了参数而没带路径 = 打错了；
    // 带了路径而起始域不是「搜索」= 那一格根本不在组合里。静默是假开关的口径。
    ForensicFlags.reverseFileNote(
        raw = args.asList().firstOrNull { it == "--reverse-file" || it.startsWith("--reverse-file=") },
        resolved = reverseFile,
        domain = startDomain,
    )?.let(::println)
    // 浮层开不开由那颗纯函数判（`--detail` 开，或被 `--reverse` / `--copy` / `--save` 接管）—— 这一格不许写在 main 里：
    // main 要建窗口，测试碰不到，留在里面就只剩文本锚点而锚点看不见死分支（批 30 那条规矩的第三次应用）。
    val panelOpen = ForensicFlags.detailPanel(detail, reverse, copy, save)
    // 给了档位而浮层根本不开 ⇒ 那一档哪儿也没去，必须出声（`readPageNote` 同一族纪律）。
    // 吃的是上面那一枚 `panelOpen` 而不是 `--detail` 自己：`--reverse` / `--copy` / `--save` 那三枚
    // 顺带接管浮层时，档位种子是真落得下去的，那时念这一句就是假读数。
    ForensicFlags.detailTierNote(detailTierSeed, panelOpen)?.let(::println)

    val sources = EngineAssets.listSourceKeys(assetDir)
    println("D_启动 sources=${sources.size} 起始=$startKey proxy=${proxy ?: "直连"} 仓库兜底=${assetDir.path}")
    // 打包产物中 ImageIO 无法在临时目录创建缓存文件，且输入均为内存 ByteArray，故全局关闭磁盘缓存。
    javax.imageio.ImageIO.setUseCache(false)
    println(
        "D_环境 jdk=${System.getProperty("java.version")} " +
            "tmp=${System.getProperty("java.io.tmpdir")} " +
            "打包=${System.getProperty("jpackage.app-path") != null} imageio=内存"
    )
    println("D_数据目录 ${paths.dataRoot.path}（写入回读已验）")
    // Task 5a：把 data/db 接到桌面（只装 factory lambda，不碰 SQLite —— 建目录/建库推迟到第一次取用）。
    // 未接线就取用会由 DatabasePorts.of 直接抛，这里不包 try/catch：抛就是要崩在启动读数里。
    DesktopDatabasePorts.install(paths)
    com.venera.desktop.downloads.DesktopDownloadsDb.install(paths)
    // 必须在 DatabasePorts 之后初始化：DesktopGalleryContentGuard 在构造期即通过 DatabasePorts.of 访问数据库。
    val galleryHandle = com.venera.desktop.gallery.data.DesktopGalleryHandle.of(paths)
    DesktopGalleryPorts.install(galleryHandle)
    // 画廊共享口：桌面壳的那面墙要真数据，而它只吃形参（`DesktopUiIsolationTest` ② 钉着
    // "UI 里不许取静态口"）。所以在集成方取一次，往下传 —— 与 `gallerySettings` 同一条形状。
    val galleryPorts: GalleryPorts = GalleryPorts.of(galleryHandle)
    // 全局仅在此处安装一次图片管道（含防盗链头、指纹伪装、共享 Cookie 与磁盘缓存目录）。
    val gallerySettings = DesktopGalleryPorts.preferences(galleryHandle)
    val sharedCookieJar = com.venera.engineprobe.DesktopCookieJar(
        com.venera.engineprobe.DesktopCookieJar.fileIn(paths.dataRoot),
    )
    com.venera.engineprobe.DesktopCloudflareCenter.registerCookieJar(sharedCookieJar)
    com.venera.engineprobe.DesktopCloudflareCenter.registerInteractiveBypassExempt { request ->
        request.tag(com.venera.compose.data.platform.NoInteractiveBypassTag::class.java) != null
    }
    com.venera.desktop.platform.DesktopCloudflareBridge.proxyArgsProvider = {
        val store = com.venera.compose.data.platform.JsonKeyValueStore(
            com.venera.compose.data.platform.PreferenceKeys.PREFS_NAME,
            paths,
        )
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
    com.venera.engineprobe.DesktopCloudflareCenter.registerBypassHandler { challengeUrl ->
        com.venera.desktop.platform.DesktopCloudflareBridge.bypass(challengeUrl)
    }
    DesktopImagePipeline.install(
        galleryHandle.engine,
        CacheDirs.inCache(paths.cacheRoot, DesktopImagePipeline.IMAGE_CACHE_DIR_NAME),
        maxSizeBytes = gallerySettings.imageCacheMb.value * 1024L * 1024L,
    )
    // 桌面专属配置（首页装饰、分级遮罩、屏蔽规则写侧）独立存放，不混入共享 gallerySettings 键空间。
    val guard = DesktopGalleryPorts.contentGuard(galleryHandle)
    // 同一形状的第三例（批 34）：设置那一屏要写 SauceNao 的 key，而契约面只有读流。
    // 拿的是 `createPorts` 里那颗（"凭据只建一颗"），不是再 new 一颗 —— 否则用户填的与请求带的不是同一个事实。
    val galleryCredentials = DesktopGalleryPorts.credentials(galleryHandle)
    // coreDbFile/favoritesDbFile 是纯计算读数：这行自己不建目录，"已建"照实反映磁盘现状。
    val coreDb = DesktopDatabasePorts.coreDbFile()
    val favDb = DesktopDatabasePorts.favoritesDbFile()
    println(
        "D_接线 平台=desktop core=${coreDb.path} 已建=${coreDb.exists()} " +
            "fav=${favDb.path} 已建=${favDb.exists()}",
    )
    // 这台机器给不给写图片、这个包里有没有词典：两句都在启动里念一次，不等到屏上少了什么才回头找
    // （措辞住在读数件里，判据 ⑤ 钉着"只此一处"）。
    println(desktopCapabilityReading(desktopCanCopyImage(), DesktopTagDictionaryAsset.presence()))

    if (favcheck == true) {
        // 取证第二跑：开窗前直接读回收藏条数（数据源仍是 LocalFavoritesManager，不自己开 SQL）
        val outcome = runCatching { runBlocking { favorites().getAllComics() } }
        outcome.fold(
            onSuccess = { items ->
                println("D_收藏 命中=${items.size} 明细=${items.take(10).map { it.id to it.name }}")
            },
            onFailure = {
                println("D_收藏 读回失败 ${it::class.java.simpleName}: ${it.message?.take(200)}")
            },
        )
        closePersistenceInOrder()
        kotlin.system.exitProcess(if (outcome.isSuccess) 0 else 1)
    }

    if (autofav == true) {
        // 取证首跑直接在 IO 线程执行装载与探索，不经过 UI 组合，避免串行拉取封面。
        val code = runAutofavEvidence(assetDir, paths.dataRoot, proxy, startKey, seedCookies)
        closePersistenceInOrder()
        kotlin.system.exitProcess(code)
        return
    }

    // 崩一枚那枚种子（批 53）。两档各走一条线程路，为的是把"非 EDT"与"EDT"**分别**量一遍：
    // AWT 的事件派发线程有自己那一族异常路，`Thread.setDefaultUncaughtExceptionHandler` 管不管得到它
    // 正是这一枚要答的问题 —— 猜"应该管得到"就等于把那一半留在没量的状态。
    when (ForensicFlags.crash(args.asList())) {
        "thread" -> Thread({ throw IllegalStateException("取证种子：非 EDT 那一笔") }, "取证-非EDT").start()
        "edt" -> javax.swing.SwingUtilities.invokeLater { throw IllegalStateException("取证种子：EDT 那一笔") }
        else -> Unit
    }

    nucleusApplication(args = args, backend = NucleusBackend.Tao) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.ui.window.LocalWindowExceptionHandlerFactory provides DesktopWindowExceptionHandlerFactory,
        ) {
        // 默认窗口尺寸按屏幕分辨率钳制（DesktopGalleryMetrics.windowSizeFor），防止小屏下右侧列溢出屏幕。
        val windowSize = remember {
            val screen = java.awt.Toolkit.getDefaultToolkit().screenSize
            DesktopGalleryMetrics.windowSizeFor(screen.width, screen.height)
        }
        // ── 窗口记忆（档三）：尺寸/位置/最大化落盘，读回时校验在屏内 ——
        // 拔了显示器就回默认档，不把窗丢进虚空。开关键 pref_desktop_window_remember 由设置中枢「桌面」区写。
        val windowStore = remember {
            com.venera.compose.data.platform.JsonKeyValueStore("venera_desktop_window", paths)
        }
        // 特效总开关起步档：只在此处读一次盘，之后由设置中枢直写单例（进程内同源）。
        remember {
            com.venera.desktop.ui.DesktopEffects.reduce =
                windowStore.getBoolean("pref_desktop_effects_reduce", false)
        }
        val rememberWindow = windowStore.getBoolean("pref_desktop_window_remember", true)
        val savedMax = windowStore.getBoolean("window_maximized", false)
        val savedW = windowStore.getInt("window_w", windowSize.width.value.toInt())
        val savedH = windowStore.getInt("window_h", windowSize.height.value.toInt())
        val savedX = windowStore.getInt("window_x", Int.MIN_VALUE)
        val savedY = windowStore.getInt("window_y", Int.MIN_VALUE)
        // 落盘的是 dp 坐标；屏界是物理 px。写侧同时记了当次密度，读回先换算成 px 再比，
        // 混 DPI / 多屏才不会把窗判回屏内又落错屏。老档没这一键，按 1.0 走旧口径。
        val savedDensity = windowStore.getFloat("window_density", 0f)
        val savedOnScreen = remember(savedX, savedY, savedDensity) {
            if (savedX == Int.MIN_VALUE || savedY == Int.MIN_VALUE) {
                false
            } else {
                runCatching {
                    val k = if (savedDensity > 0f) savedDensity else 1f
                    java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.any { dev ->
                        val b = dev.defaultConfiguration.bounds
                        val px = savedX * k
                        val py = savedY * k
                        px >= b.x && py >= b.y && px < b.x + b.width && py < b.y + b.height
                    }
                }.getOrDefault(false)
            }
        }
        val windowState = rememberWindowState(
            placement = if (rememberWindow && savedMax) WindowPlacement.Maximized else WindowPlacement.Floating,
            width = (if (rememberWindow && savedW > 300) savedW.coerceAtLeast(1120) else windowSize.width.value.toInt().coerceAtLeast(1120)).dp,
            height = (if (rememberWindow && savedH > 200) savedH.coerceAtLeast(720) else windowSize.height.value.toInt().coerceAtLeast(720)).dp,
            position = if (rememberWindow && savedOnScreen) {
                androidx.compose.ui.window.WindowPosition(savedX.dp, savedY.dp)
            } else {
                androidx.compose.ui.window.WindowPosition.PlatformDefault
            },
        )
        DecoratedWindow(
            onCloseRequest = {
                closePersistenceInOrder()
                exitApplication()
            },
            title = APP_TITLE,
            icon = androidx.compose.ui.res.painterResource("vcomic_icon.png"),
            state = windowState,
        ) {
            // 材质由 WindowsBackdrop 合成在窗后（进程内无 AWT、窗口非分层），支持运行期热切换。
            val backdropMode by com.venera.desktop.settings.DesktopBackdropMode.mode.collectAsState()
            val railBlur by com.venera.desktop.settings.DesktopRailBlur.visible.collectAsState()
            val topBarBlur by com.venera.desktop.settings.DesktopTopBarBlur.visible.collectAsState()
            WindowsBackdrop(
                com.venera.desktop.settings.DesktopBackdropMode.styleOf(backdropMode, railBlur, topBarBlur),
            )
            val winDensity = androidx.compose.ui.platform.LocalDensity.current
            // 最小缩放下限：再小详情页的双列就挤没形了 —— 定在 1120×720（dp×密度换 px）。
            LaunchedEffect(winDensity) {
                nucleusWindow.setMinimumSize(
                    androidx.compose.ui.unit.DpSize(1120.dp, 720.dp)
                )
                val minWpx = (1120f * winDensity.density).toInt()
                val minHpx = (720f * winDensity.density).toInt()
                java.awt.Window.getWindows().forEach { w ->
                    w.minimumSize = java.awt.Dimension(minWpx, minHpx)
                }
                DesktopWindowMinSizeHook.installOrUpdate(
                    title = APP_TITLE,
                    minWidthPx = minWpx,
                    minHeightPx = minHpx,
                )
            }
            // 框架侧的下限只挡自绘边框拖拽，仍存在缩到更小的路径（原生缩放 / 恢复落盘尺寸）。
            // 这里按实际尺寸回写兜底：低于下限立即校正，不依赖框架实现。
            LaunchedEffect(windowState) {
                val minW = 1120.dp
                val minH = 720.dp
                androidx.compose.runtime.snapshotFlow { windowState.size }.collect { size ->
                    // 最小化 / 隐藏帧的高宽可能读到 0，不参与校正，免得把窗撑开。
                    if (size.width.value <= 0f || size.height.value <= 0f) return@collect
                    val fixed = androidx.compose.ui.unit.DpSize(
                        maxOf(size.width, minW),
                        maxOf(size.height, minH),
                    )
                    if (fixed != size) windowState.size = fixed
                }
            }
            // 旧收藏库一次性迁移：`local_favorite.db` 的一夹一表搬进新库 `comic_favorite`。
            // 幂等（靠库内标记），失败只记日志不影响启动；收藏页只读新库，不搬等于老收藏永远不可见。
            LaunchedEffect(Unit) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching {
                        val moved = com.venera.desktop.platform.migrateLegacyFavorites(
                            com.venera.compose.data.db.DatabasePorts.of(null),
                        )
                        if (moved > 0) println("D_收藏迁移 搬=$moved 条")
                    }.onFailure {
                        println("D_收藏迁移 失败 ${it.javaClass.simpleName}: ${it.message?.take(120) ?: ""}")
                        DesktopErrorBus.report(
                            category = "漫画",
                            headline = "旧版收藏迁移失败",
                            humanReason = "读取旧版收藏数据时出错，部分历史收藏可能未自动迁入。",
                            detail = "${it.javaClass.simpleName}: ${it.message.orEmpty()}",
                            dedupKey = "comic:legacy_fav_migrate",
                        )
                    }
                }
            }
            // 启动恢复最大化档。WindowState 的 placement 由宿主接管，按落盘值主动落一次。
            LaunchedEffect(Unit) {
                if (rememberWindow && savedMax) nucleusWindow.setMaximized(true)
            }
            // 跨密度修正：dp 坐标是按写窗那次的密度记的，换显示器/改缩放档后直接还原会物理漂移。
            // 密度同档（多数场景）不动，避免启动闪跳；不同档才按 px 反算重落。
            LaunchedEffect(winDensity) {
                if (rememberWindow && savedDensity > 0f && savedX != Int.MIN_VALUE &&
                    savedDensity != winDensity.density
                ) {
                    val k = savedDensity / winDensity.density
                    windowState.position = androidx.compose.ui.window.WindowPosition(savedX.dp * k, savedY.dp * k)
                }
            }
            val maximized by nucleusWindow.maximizedFlow.collectAsState()
            val gpuRenderContext = dev.nucleusframework.window.tao.rememberTaoGpuRenderContext()
            val targetGpuLimitBytes = remember {
                DesktopGpuCacheGuard.parseLimitBytes(System.getProperty("skiko.gpu.resourceCacheLimit"))
            }
            val idleReclaimScope = androidx.compose.runtime.rememberCoroutineScope()
            var largeEvictGcToken by remember { mutableStateOf(0L) }
            val purgeGpuCache: (String) -> Unit = remember(gpuRenderContext, targetGpuLimitBytes, idleReclaimScope) {
                { reason ->
                    val freedImageBytes = com.venera.desktop.gallery.data.DesktopImagePipeline
                        .evictLargeMemoryCache(reason)
                    if (gpuRenderContext == null) {
                        println("D_GPU缓存 跳过清理 reason=$reason 因=TaoGpuRenderContext为空")
                    } else {
                        runCatching {
                            gpuRenderContext.runOnGpuThread {
                                DesktopGpuCacheGuard.purgeAndRestore(
                                    gpuRenderContext.skiaContext,
                                    targetGpuLimitBytes,
                                    reason,
                                )
                            }
                        }.onFailure {
                            println("D_GPU缓存 清理失败 reason=$reason ${it.javaClass.simpleName}: ${it.message}")
                        }
                    }
                    // 离场清掉大块位图后，在退场动画结束时防抖轻触一次 GC，促使 Skia Cleaner 尽快归还原生像素。
                    if (reason != "window_idle" && freedImageBytes >= 4L * 1024L * 1024L) {
                        val token = ++largeEvictGcToken
                        idleReclaimScope.launch(kotlinx.coroutines.Dispatchers.Default) {
                            delay(260)
                            if (largeEvictGcToken == token) System.gc()
                        }
                    }
                }
            }
            // Tao 后端在 attach 与 resize 时会把 DirectContext 上限重设为 256MB，延迟校准把它压回配置值。
            LaunchedEffect(gpuRenderContext, windowState.size, maximized) {
                if (gpuRenderContext == null) {
                    println("D_GPU缓存 跳过接管 因=TaoGpuRenderContext为空")
                    return@LaunchedEffect
                }
                kotlinx.coroutines.delay(150)
                runCatching {
                    gpuRenderContext.runOnGpuThread {
                        DesktopGpuCacheGuard.enforceLimit(
                            gpuRenderContext.skiaContext,
                            targetGpuLimitBytes,
                            "window_attach_or_resize",
                        )
                    }
                }.onFailure {
                    println("D_GPU缓存 限额校准失败 ${it.javaClass.simpleName}: ${it.message}")
                }
            }
            // 窗口切走再回来念一句，兼顾空闲回收：失焦即"用户不看这一帧"，延时后做一次 full GC
            // 让 Skia 的 Cleaner 落地，Serial 再按 MaxHeapFreeRatio 收缩老年代，并清一次 GPU 未锁定纹理。
            // token 只认最后一颗，来回切焦点不会攒出一串回收。
            DisposableEffect(purgeGpuCache) {
                var hadLostFocus = false
                var idleToken = 0L
                val collectJob = idleReclaimScope.launch {
                    nucleusWindow.focusFlow.collect { focused ->
                        idleToken++
                        if (focused) {
                            if (hadLostFocus) {
                                hadLostFocus = false
                                DesktopPetCueBus.emit(DesktopPetCue.WINDOW_RETURN)
                            }
                        } else {
                            hadLostFocus = true
                            val token = idleToken
                            idleReclaimScope.launch {
                                delay(IDLE_RECLAIM_DELAY_MS)
                                if (idleToken == token) {
                                    System.gc()
                                    purgeGpuCache("window_idle")
                                }
                            }
                        }
                    }
                }
                onDispose { collectJob.cancel() }
            }
            // 系统托盘与后台下载通知：双击或点菜单唤醒主窗口，退出时卸载避免残留图标。
            DisposableEffect(Unit) {
                com.venera.desktop.platform.DesktopSystemTray.install(
                    onShowWindow = {
                        runCatching {
                            nucleusWindow.setMinimized(false)
                            nucleusWindow.toFront()
                            nucleusWindow.requestFocus()
                        }
                    },
                    onExit = {
                        com.venera.desktop.platform.DesktopSystemTray.uninstall()
                        closePersistenceInOrder()
                        exitApplication()
                    },
                    isWindowVisible = { !nucleusWindow.isMinimized },
                    onHideToTray = {
                        runCatching { nucleusWindow.setMinimized(true) }
                    },
                    isMaskEnabled = { guard.nsfwMaskMode.value != "OFF" },
                    onToggleMask = {
                        val next = if (guard.nsfwMaskMode.value == "OFF") "BLUR" else "OFF"
                        guard.setNsfwMaskMode(next)
                    },
                )
                onDispose { com.venera.desktop.platform.DesktopSystemTray.uninstall() }
            }
            // 窗口记忆写回：几何变化后停 800ms 再落盘（拖动不刷盘）。与读回同一开关键。
            if (rememberWindow) {                LaunchedEffect(windowState) {
                    androidx.compose.runtime.snapshotFlow {
                        Pair(windowState.size, windowState.position)
                    }.collectLatest {
                        kotlinx.coroutines.delay(800)
                        val maximized = nucleusWindow.isMaximized
                        val minimized = nucleusWindow.isMinimized
                        val posX = windowState.position.x.value.toInt()
                        val posY = windowState.position.y.value.toInt()
                        windowStore.put("window_maximized", maximized)
                        if (!maximized && !minimized && posX > -10000 && posY > -10000) {
                            // 最大化与最小化档不记尺寸/坐标（Windows 最小化坐标为 -32000）。
                            windowStore.put("window_w", windowState.size.width.value.toInt())
                            windowStore.put("window_h", windowState.size.height.value.toInt())
                            windowStore.put("window_x", posX)
                            windowStore.put("window_y", posY)
                            windowStore.put("window_density", winDensity.density)
                        }
                    }
                }
            }
            // 最大化状态已在上方订阅系统 windowState/maximizedFlow，不另存影子状态，避免系统快捷键操作后状态漂移。
            fun toggleMaximize() {
                nucleusWindow.setMaximized(!nucleusWindow.isMaximized)
            }
            val pixivCallback by pixivCallbackState.collectAsState()
            LaunchedEffect(pixivCallback?.id) {
                if (pixivCallback != null) {
                    runCatching {
                        nucleusWindow.toFront()
                    }
                }
            }
            DisposableEffect(launchListener, singleInstance) {
                onDispose {
                    launchListener?.close()
                    singleInstance?.close()
                }
            }
            // 源启停那份盘上状态的持有者（`venera_desktop_sources.json`）。
            // 放在这里而不是 `remember`：它无状态、只是读一个 json，重建的代价为零，
            // 而 `readSources` 那根 lambda 会在组合外被调用（不是 @Composable）。
            val sourceState = remember {
                com.venera.desktop.gallery.data.DesktopSourceState(paths)
            }

            // 源脚本覆盖层目录创建失败时返回 null 并隐藏编辑入口，不中止主程序启动。
            val overrideDir = remember {
                runCatching {
                    com.venera.desktop.gallery.data.DesktopSourceOverrides.dirFor(paths)
                }.onFailure {
                    println("D_源改写 目录建不出来，编辑这一路关掉：${it.javaClass.simpleName} ${it.message?.take(160)}")
                }.getOrNull()
            }

            // 窗口级常驻引擎通过 remember 仅装配一次，不可复用一次性探针引擎（用完即关）。
            val uiEngine = remember {
                runCatching {
                    val host = com.venera.engineprobe.DesktopJsHost(
                        assetDir,
                        paths.dataRoot,
                        proxy,
                        seedCookies,
                        sharedCookieJar = sharedCookieJar,
                    )
                    // 装载次序里多最前面那一站（用户改写层）。
                    // **写侧与读侧必须是同一个目录**，否则"已保存"那一句就是假话。
                    com.venera.engineprobe.onEngineSourceInitFailure = { key, detail ->
                        DesktopErrorBus.report(
                            category = "系统",
                            headline = "漫画源「$key」初始化失败",
                            humanReason = "该漫画源脚本初始化未成功，可能暂时无法拉取内容。",
                            detail = detail,
                            dedupKey = "engine:source_init:$key",
                        )
                    }
                    com.venera.engineprobe.onCookieJarPersistFailure = { reason, detail ->
                        DesktopErrorBus.report(
                            category = "系统",
                            headline = "登录凭据保存失败",
                            humanReason = "Cookie 无法写入硬盘，重启软件后账号登录状态可能会丢失。",
                            detail = "$reason · $detail",
                            dedupKey = "cookie:persist_fail",
                        )
                    }
                    EngineSession(
                        host = host,
                        assetDir = assetDir,
                        overrideDir = overrideDir,
                        onThumbnailHeaders = { url, headers ->
                            com.venera.compose.data.platform.ImageHeaderPolicy.publishForUrls(listOf(url), headers)
                        },
                    )
                }.onFailure {
                    // 起不来要念出来并推送到界面错误提示：否则详情页只会显示"没有章节"。
                    val msg = "${it::class.java.simpleName}: ${it.message?.take(200)}"
                    println("D_引擎 UI 起不来 $msg")
                    DesktopErrorBus.report("引擎", "脚本引擎启动失败", msg, dedupKey = "engine:init")
                }.getOrNull()
            }
            // 窗口销毁时收尾。不收的话进程退出前 JS 上下文还挂着。
            DisposableEffect(uiEngine) {
                onDispose { runCatching { uiEngine?.close() } }
            }

            // 搜索的游标续页门票：游标型源（ehentai 的 loadNext 族）第 2 页
            // 要拿第 1 页答复里的 `next` 当形参，页码替不了。屏按页递进（1→2→3），
            // 所以「上一趟的 next + 到过的页号」就是全部状态；新搜索从 1 重来，游标自然作废。
            var searchCursor by remember { mutableStateOf<String?>(null) }
            var searchCursorAtPage by remember { mutableIntStateOf(0) }

            // 桌面端统一使用共享组件与 Token，仅自持窗口、进程与数据接线；主题模式与玻璃令牌按深浅档供给。
            val themeMode by com.venera.desktop.settings.DesktopThemeMode.mode.collectAsState()
            val themeDark = com.venera.desktop.settings.DesktopThemeMode.isDark(themeMode)
            val uiScale by com.venera.desktop.settings.DesktopUiScale.scale.collectAsState()
            val baseDensity = androidx.compose.ui.platform.LocalDensity.current
            val scaledDensity = androidx.compose.ui.unit.Density(
                density = baseDensity.density * uiScale,
                fontScale = baseDensity.fontScale,
            )
            // 分级遮罩档：在这层订阅成 Compose 状态，主屏才会随「设置里切档」重组。
            // 从前 maskModeOf 是 { guard.nsfwMaskMode.value } 普通 lambda —— 写侧虽推了流，
            // 但 Compose 不订阅它，已上屏的封面遮罩停在旧快照（切档不即时生效）。
            val guardMaskMode by guard.nsfwMaskMode.collectAsState()
            val shortcutBus = remember { com.venera.desktop.ui.DesktopGlobalShortcutBus() }
            val rootFocusManager = androidx.compose.ui.platform.LocalFocusManager.current
            val isWindowFocused by nucleusWindow.focusFlow.collectAsState(initial = true)
            val latestWindowFocused by androidx.compose.runtime.rememberUpdatedState(isWindowFocused)
            // AWT 侧焦点兜底：nucleus 的 focusFlow 偶尔在"切走再切回"后停在 false，
            // 那会让窗口级快捷键整片失灵。这里用窗口自己的焦点事件补一路，两路取或。
            var awtWindowFocused by remember { mutableStateOf(true) }
            DisposableEffect(Unit) {
                val w = java.awt.Window.getWindows().firstOrNull { it.isShowing }
                val l = object : java.awt.event.WindowFocusListener {
                    override fun windowGainedFocus(e: java.awt.event.WindowEvent) { awtWindowFocused = true }
                    override fun windowLostFocus(e: java.awt.event.WindowEvent) { awtWindowFocused = false }
                }
                w?.addWindowFocusListener(l)
                onDispose { w?.removeWindowFocusListener(l) }
            }
            val latestAwtFocused by androidx.compose.runtime.rememberUpdatedState(awtWindowFocused)
            DisposableEffect(shortcutBus) {
                val kfm = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager()
                val modifierTracker = java.awt.KeyEventDispatcher { awtEvent ->
                    com.venera.desktop.ui.DesktopModifierState.isShiftDown = awtEvent.isShiftDown
                    false
                }
                val postProcessor = java.awt.KeyEventPostProcessor { awtEvent ->
                    if (!(latestWindowFocused || latestAwtFocused) || awtEvent.isConsumed) return@KeyEventPostProcessor false
                    val signal = com.venera.desktop.ui.awtKeyEventToShortcutSignal(awtEvent)
                        ?: return@KeyEventPostProcessor false
                    if (shortcutBus.dispatch(signal)) {
                        awtEvent.consume()
                        true
                    } else {
                        false
                    }
                }
                kfm.addKeyEventDispatcher(modifierTracker)
                kfm.addKeyEventPostProcessor(postProcessor)
                onDispose {
                    kfm.removeKeyEventDispatcher(modifierTracker)
                    kfm.removeKeyEventPostProcessor(postProcessor)
                }
            }
            val desktopLang by com.venera.desktop.settings.DesktopLanguage.mode.collectAsState()
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides scaledDensity,
                com.venera.desktop.settings.LocalSettingsGlass provides
                    com.venera.desktop.settings.settingsGlassFor(themeDark),
                com.venera.desktop.ui.LocalDesktopShortcutBus provides shortcutBus,
                LocalGpuCachePurge provides purgeGpuCache,
                com.venera.desktop.settings.LocalDesktopLanguage provides desktopLang,
            ) {
                com.venera.desktop.ui.RegisterDesktopShortcutHandler(windowState, priority = -10) { signal ->
                    if (com.venera.desktop.ui.matchGlobalNavShortcut(signal) ==
                        com.venera.desktop.ui.DesktopGlobalNavAction.TOGGLE_FULLSCREEN
                    ) {
                        windowState.placement = if (windowState.placement == WindowPlacement.Fullscreen) {
                            if (nucleusWindow.isMaximized) WindowPlacement.Maximized else WindowPlacement.Floating
                        } else {
                            WindowPlacement.Fullscreen
                        }
                        true
                    } else {
                        false
                    }
                }
                // 详情页整窗背板（壳递上来；null = 不在详情页）。
                // 画在窗根而不是内容区：内容槽有 24dp 内边距、顶上还有 46dp 顶栏，
                // 背板铺在里面就会在两条边界上露出窗底色，成为割裂线。
                // 带一个"深/浅"位：浅色实验档的背板底色是浅的，不能跟系统主题走。
                var shellBackdrop by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
                // 报错不关窗、不弹框、只由左侧桌宠接走；根 Box 不接指针修饰，事件原样穿透。
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onPreviewKeyEvent { keyEvent ->
                            val signal = keyEvent.toShortcutSignalOrNull() ?: return@onPreviewKeyEvent false
                            val nav = com.venera.desktop.ui.matchGlobalNavShortcut(signal)
                                ?: return@onPreviewKeyEvent false
                            // 只有 ESC 走预览通道：它先清焦点再派发，消费后不会再进后处理器。
                            // 其余按键留给窗口级总线单点派发，否则同一按键会被处理两次。
                            if (nav == com.venera.desktop.ui.DesktopGlobalNavAction.ESCAPE) {
                                rootFocusManager.clearFocus(force = true)
                                return@onPreviewKeyEvent shortcutBus.dispatch(signal)
                            }
                            false
                        },
                ) {
                // 整窗背板：压在所有内容之下，顶栏、侧栏那 24dp 槽、右侧空边全落在同一张糊图上。
                val detailBackdrop = shellBackdrop
                if (detailBackdrop != null) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        com.venera.compose.ui.tokens.LocalVeneraDarkTheme provides detailBackdrop.second,
                    ) {
                        com.venera.desktop.gallery.ui.DesktopImageBackdrop(
                            url = detailBackdrop.first,
                            modifier = Modifier
                                .fillMaxSize()
                                // 与壳的 windowShape 同值：非最大化 12dp，最大化贴边。
                                // 背板画在壳那一层之外，不自己裁就会在四角露出方角。
                                .clip(
                                    if (maximized) RoundedCornerShape(0.dp)
                                    else RoundedCornerShape(12.dp),
                                ),
                        )
                    }
                }
                // 启动动画层。--shot= 取证模式不摆，否则取证截图会全是启动画面；
                // zIndex 是因为主屏不接受 modifier，只能靠层级压在它上面。
                // 固定 1.2s 收起：等业务数据会让慢网停在启动画面。
                if (shot == null) {
                    var splashUp by remember { mutableStateOf(true) }
                    LaunchedEffect(Unit) {
                        delay(1_200)
                        splashUp = false
                    }
                    DesktopStartupSplash(
                        visible = splashUp,
                        dark = themeDark,
                        modifier = Modifier.fillMaxSize().zIndex(100f),
                    )
                }
                VeneraDesktopLargeScreen(
                    windowWidth = windowState.size.width,
                    ports = galleryPorts,
                // ── 自绘标题栏的窗控三跳(侧栏视图④)──
                // 真窗口操作在这一颗 Window 作用域里,逐枚接 window API(红线①:不摆装饰钮)。
                onWindowMinimize = { nucleusWindow.setMinimized(true) },
                onWindowToggleMaximize = { toggleMaximize() },
                windowMaximized = maximized,
                // 详情页整窗背板：壳算好图源递上来，本层在窗根铺（见上面那颗 state）。
                onShellBackdropChange = { url, dark -> shellBackdrop = url?.let { it to dark } },
                onWindowClose = {
                    closePersistenceInOrder()
                    exitApplication()
                },
                windowDragModifier = Modifier.windowDragArea(enabled = true),
                // 分级遮罩档的**当前值**：订阅成 Compose 状态后，这里吃的是会触发重组的值。
                // 另记一份 / 裸读 .value 都会与用户改设置的那一刻脱钩 —— 那正是"假开关"的形状。
                maskModeOf = { guardMaskMode },
                // 用户黑名单复用 GalleryBlockMatch.blocked 与画廊墙保持同源；AUTHOR 规则要求 author 非空才匹配。
                wallGuardOf = { guard },
                // 详情/阅读器要的引擎 + 写历史那一跳。
                // `writeHistory` 只递 `saveHistory` 这一个动作，**不把整个 DAO 递进 UI** ——
                // UI 不该能删历史、能读全表。
                engine = uiEngine,
                followDao = comicFollowDao(),
                writeHistory = { record -> history().saveHistory(record) },
                // 详情那一屏「开始/继续阅读」那一枚胶囊吃的那一行：**只递单条读口**
                // （与上面那枚"只递 saveHistory 这一个动作"同一条口径 —— UI 不该能读全表）。
                readHistoryRow = { comicId -> history().getHistory(comicId) },

                // 暴露遮罩档与屏蔽 AI 写口供设置页调用。
                setMaskMode = { mode -> guard.setNsfwMaskMode(mode) },
                setBlockAi = { on -> gallerySettings.setGalleryBlockAi(on) },

                // 源清单来自随包 JS，启停状态存本地 venera_desktop_sources.json；默认仅启用随包四项。
                readSources = {
                    // 源导入：清单 = 包内 ∪ 用户源目录（source_overrides/sources 里的
                    // **新键**也算数 —— 旧的只有改写键能进，全新 .js 能装载却列不出来，"能跑不能见"）。
                    val keys: List<String> = run {
                        val bundled = EngineAssets.listSourceKeys(assetDir).sorted()
                        val imported = overrideDir?.let { dir ->
                            java.io.File(dir, "sources")
                                .listFiles { f -> f.isFile && f.extension == "js" }
                                ?.map { it.nameWithoutExtension }
                                ?.filter { com.venera.desktop.gallery.data.DesktopSourceOverrides.isSafeKey(it) }
                                .orEmpty()
                        }.orEmpty()
                        val all = (bundled + imported).distinct()
                        // 用户拖过就以那一条顺序当正典；没拖过、或清单里冒出新的（新导入的脚本、
                        // 刚下的源）就落字母序那一段，排在老人家后面，不抢位子。
                        val saved = sourceState.orderKeys().orEmpty()
                        if (saved.isEmpty()) {
                            all.sorted()
                        } else {
                            val rank = saved.withIndex().associate { it.value to it.index }
                            all.sortedWith(compareBy({ rank[it] ?: Int.MAX_VALUE }, { it }))
                        }
                    }
                    // 单次读盘同时提取源名称、Cookie 字段、登录能力、版本与探测主机，避免多次读盘导致状态不一致。
                    val names = LinkedHashMap<String, String>()
                    val cookieFields = LinkedHashMap<String, List<String>>()
                    val passwordLogin = LinkedHashMap<String, Boolean>()
                    val webLogin = LinkedHashMap<String, Boolean>()
                    val versions = LinkedHashMap<String, String>()
                    // 稿 §2.3 大卡副行的「N 项设置」：同一趟脚本文本里扫 settings 块的声明项数，不另读盘。
                    val settingsCounts = LinkedHashMap<String, Int>()
                    // 首页「源状态」真测速的探测主机，同一趟现取（不开第二轨）。
                    val hosts = LinkedHashMap<String, String>()
                    keys.forEach { k ->
                        // 取用次序 覆盖层→包内→仓库（§144 那条：装载吃改后的脚本而清单念出厂那一个，
                        // 就是"看着还是那一颗、跑的已经不是它"）。
                        val text = com.venera.desktop.gallery.data.DesktopSourceNames
                            .scriptOf(assetDir, k, overrideDir) ?: return@forEach
                        com.venera.desktop.gallery.data.DesktopSourceNames
                            .displayNameOf(text)?.let { names[k] = it }
                        cookieFields[k] = com.venera.desktop.gallery.data.DesktopSourceNames
                            .cookieFieldsOf(text)
                        passwordLogin[k] = com.venera.desktop.gallery.data.DesktopSourceNames
                            .supportsPasswordLoginOf(text)
                        webLogin[k] = com.venera.desktop.gallery.data.DesktopSourceNames
                            .supportsWebLoginOf(text)
                        versions[k] = com.venera.desktop.gallery.data.DesktopSourceNames
                            .versionOf(text)
                        settingsCounts[k] = com.venera.desktop.gallery.data.DesktopSourceSettings
                            .definitionsOf(text).size
                        hosts[k] = com.venera.desktop.gallery.data.DesktopSourceNames.apiHostOf(text)
                    }
                    // 哪几颗有改写层：屏上那一列「已改写」与那枚「撤回」把手都吃这一份。
                    val overridden = overrideDir?.let { dir ->
                        com.venera.desktop.gallery.data.DesktopSourceOverrides.overriddenKeys(dir)
                    } ?: emptySet()
                    // 显式启用清单：未修改过时取默认四项与清单交集，并剔除 --disable 指定的源。
                    val storedEnabled = sourceState.enabledKeys()
                    val effectiveEnabled = com.venera.desktop.gallery.data.DesktopSourceState
                        .effectiveEnabled(keys, storedEnabled) - seedDisabled
                    println(
                        com.venera.desktop.gallery.data.DesktopSourceState
                            .reading(effectiveEnabled, fromDefault = storedEnabled == null, total = keys.size),
                    )
                    // 每次读取清单均输出源改写汇总，区分「改写数为 0」与「改写目录不可用」。
                    println(
                        if (overrideDir == null) {
                            "D_源改写 编辑这一路是关的（改写层目录不在手）"
                        } else {
                            com.venera.desktop.gallery.data.DesktopSourceOverrides.reading(overridden)
                        },
                    )
                    val catalog = com.venera.desktop.gallery.data.DesktopSourceCatalog
                        .rows(
                            keys = keys,
                            names = names,
                            enabled = effectiveEnabled,
                            overridden = overridden,
                            cookieFields = cookieFields,
                            supportsPasswordLogin = passwordLogin,
                            supportsWebLogin = webLogin,
                            hosts = hosts,
                        )
                    catalog.map { r ->
                        com.venera.desktop.sourcemanage.DesktopSourceRowUi(
                            key = r.key,
                            title = r.title,
                            enabled = r.enabled,
                            overridden = r.overridden,
                            cookieFields = r.cookieFields,
                            supportsPasswordLogin = r.supportsPasswordLogin,
                            supportsWebLogin = r.supportsWebLogin,
                            host = r.host,
                            version = versions[r.key].orEmpty(),
                            settingsCount = settingsCounts[r.key] ?: 0,
                            // 「在用」徽章吃运行时真那颗（漫画域主路径的硬编码源）。
                            inUse = r.key == DESKTOP_EXPLORE_SOURCE,
                            // 左栏那枚「已登录」徽章：罐里那一格是盘上事实，
                            // 读不到（引擎没起/罐没建）按没登录念 —— 那是实话，不是丢账。
                            loggedIn = uiEngine?.host?.let { host ->
                                runCatching { host.isLogged(r.key) }.getOrDefault(false)
                            } ?: false,
                            // 只剩这一颗开着 ⇒ 它关不掉（关光后漫画域一屏都装不起来，见 canDisable 那条）。
                            canDisable = com.venera.desktop.gallery.data.DesktopSourceSelection
                                .canDisable(keys, effectiveEnabled, r.key),
                        )
                    }
                },
                writeSourceEnabled = { key, enabled ->
                    // 写的是**开启账**（翻向后与旧"停用账"方向相反）。读回 null（从没动过开关）时
                    // 先落默认档再翻那一格 —— 否则"关掉 jm"会写成"只有 jm 开着"（把默认全量丢了个反向）。
                    val base = com.venera.desktop.gallery.data.DesktopSourceState
                        .effectiveEnabled(EngineAssets.listSourceKeys(assetDir).sorted(), sourceState.enabledKeys())
                    sourceState.setEnabledKeys(com.venera.desktop.gallery.data.DesktopSourceSelection.toggle(base, key))
                },
                // 站点墙拖出来的顺序：存进同一份 json，下一次 readSources 就按它排。
                // 搜索的「来源」与收藏的源栏都从 readSources 派生，所以这一颗写口顺带管住那两处。
                writeSourceOrder = { ordered -> sourceState.setOrderKeys(ordered) },
                // 源编辑统一读写覆盖层目录（包内资源只读）；目录不可用时 readSourceText 返回 null 以隐藏编辑入口。
                readSourceText = overrideDir?.let { dir ->
                    { key: String ->
                        com.venera.desktop.gallery.data.DesktopSourceNames
                            .scriptOf(assetDir, key, dir)
                    }
                },
                saveSourceText = { key, content ->
                    val dir = overrideDir
                    val e = uiEngine
                    if (dir == null) {
                        "这台机器的改写层目录建不出来（数据目录不可写？）：一个字都没写。"
                    } else if (e == null) {
                        // 引擎没起来 = **不能校验** = 不能落盘。这里不许退成"直接写文件"：
                        // 那等于把这篇没验过的原文装进下次启动（对岸那颗屏的类注释记的就是这一课），
                        // 而"引擎没起来"与"这篇脚本有语法错"在屏上必须是两句话。
                        "引擎没起来：这篇脚本没法先验再存，所以一个字都没写。"
                    } else {
                        // 校验那一跳要过引擎那把锁（与探索/详情/搜索/阅读器共用同一颗引擎，
                        // 见 `DesktopEngineCall` 那颗的类注释：并发交错会抢走 `window['temp_source']`）。
                        desktopEngineCall(e) {
                            e.checkLoadable(key, content)
                        }.fold(
                            onSuccess = { reported ->
                                val path = com.venera.desktop.gallery.data.DesktopSourceOverrides
                                .writeText(dir, key, content)
                                println(
                                    "D_源改写 存=$key 报回key=$reported 盘=$path " +
                                        "字节=${content.toByteArray(Charsets.UTF_8).size}",
                                )
                                if (reported != key) {
                                    // 键名被改动：下一次装载会把自己注册成另一个键。
                                    // 不念出来的话，用户改完发现"探索还是老样子"而不知所以。
                                    "存下了，但这篇脚本自报的键是 $reported（不是 $key）：" +
                                        "装载时会按它自己报的那个注册。"
                                } else {
                                    null
                                }
                            },
                            onFailure = { t ->
                                // 校验没过：盘上仍是上一版好脚本。成因原样交回编辑器原地展示。
                                "没存：${t.message?.take(400) ?: t.javaClass.simpleName}"
                            },
                        )
                    }
                },
                clearSourceOverride = { key ->
                    val dir = overrideDir
                    if (dir == null) error("改写层目录不在手，撤不掉：$key")
                    val ok = com.venera.desktop.gallery.data.DesktopSourceOverrides
                        .clear(dir, key)
                    println("D_源改写 撤=$key 成=$ok")
                    if (!ok) error("撤不掉 $key 的改写层（文件不在或删除被拒）")
                },
                // 引擎调用须独立成行且以 e. 开头，以匹配 DesktopEngineCallCoverageTest 的行首扫描规则。
                loginWithCookies = { key, values ->
                    val e = uiEngine
                    if (e == null) {
                        // 引擎没起来是一句**成因**，不是"没登上"。把两者混成一句，
                        // 用户会去检查自己填的那几枚，而真因是这一趟压根没发出去。
                        "引擎没起来：这一趟发不出去，什么都没试。"
                    } else {
                        desktopEngineCall(e) {
                            e.load(key)
                            e.loginWithCookies(values)
                        }.fold(
                            onSuccess = { login ->
                                // 回核那一步在锁**外面**：它不起第二颗宿主，只是另读一份 json
                                // （批 88 那条"拿内存里那份作证等于自己给自己签字"）。
                                val onDisk = com.venera.desktop.gallery.data.DesktopCookieLogin
                                    .tallyOnDisk(paths.dataRoot, login.fields)
                                val outcome = com.venera.desktop.gallery.data.DesktopCookieLogin
                                    .outcomeOf(login, onDisk)
                                println(com.venera.desktop.gallery.data.DesktopCookieLogin.reading(key, outcome))
                                com.venera.desktop.gallery.data.DesktopCookieLogin.screenNote(outcome)
                            },
                            onFailure = { t ->
                                "这一趟没跑成：${t.javaClass.simpleName} " + (t.message?.take(200) ?: "")
                            },
                        )
                    }
                },
                loginWithPassword = { key, account, password ->
                    val e = uiEngine
                    if (e == null) {
                        "引擎没起来：这一趟发不出去，什么都没试。"
                    } else {
                        desktopEngineCall(e) {
                            e.load(key)
                            e.loginWithPassword(account, password)
                        }.fold(
                            onSuccess = { login ->
                                println(
                                    "D_账密登录 源=$key 源答=${if (login.ok) "过" else "没过"} " +
                                        "说明=${login.note.replace('\n', ' ').take(160)}",
                                )
                                if (login.ok) {
                                    "已登录：${login.note}；账号已落在本机源数据罐里"
                                } else {
                                    "没登上：${login.note}"
                                }
                            },
                            onFailure = { t ->
                                "这一趟没跑成：${t.javaClass.simpleName} " + (t.message?.take(200) ?: "")
                            },
                        )
                    }
                },

                // ── 网页登录（调用 Edge App 独立弹窗并自动同步 Cookie）──
                loginWithWebview = { key ->
                    val e = uiEngine
                    if (e == null) {
                        "引擎没起来：这一趟发不出去，什么都没试。"
                    } else if (!com.venera.desktop.sourcemanage.DesktopEdgeWebLogin.isSupported) {
                        "本机未检测到 Microsoft Edge 浏览器，无法启动独立网页登录窗口。"
                    } else {
                        val webUrl = desktopEngineCall(e) {
                            e.load(key)
                            e.webLoginUrl()
                        }.getOrNull()
                        if (webUrl.isNullOrBlank()) {
                            "该源未声明有效的网页登录入口（loginWithWebview.url 为空）。"
                        } else {
                            val res = com.venera.desktop.sourcemanage.DesktopEdgeWebLogin.launchWebLogin(webUrl) { currentUrl, title ->
                                desktopEngineCall(e) {
                                    e.checkWebLoginStatus(currentUrl, title)
                                }.getOrDefault(false)
                            }
                            res.fold(
                                onSuccess = { cookies ->
                                    // ⚠️ 写进**引擎那颗罐**（host.cookieJar），不许另造实例：
                                    // 两颗罐对同一份文件各自整罐快照落盘，引擎一发请求就会把
                                    // 新实例写的那份整个盖回去 —— 登录凭据当场蒸发（实测）。
                                    val saved = com.venera.desktop.sourcemanage.DesktopEdgeWebLogin.applyCookiesToJar(
                                        e.host.cookieJar,
                                        webUrl,
                                        cookies,
                                    )
                                    desktopEngineCall(e) {
                                        e.onWebLoginSuccess()
                                    }
                                    println("D_网页登录 源=$key 捕获=${cookies.size} 写入=$saved 状态=成功")
                                    "已登录：网页登录成功，已捕获 $saved 枚 Cookie 凭据并持久化写入。"
                                },
                                onFailure = { t ->
                                    "网页登录未完成：${t.message ?: t.javaClass.simpleName}"
                                },
                            )
                        }
                    }
                },

                // 等待 favoritesFlow 首个非空值（init 异步刷新，初值为空表），超时后回退当前值避免空库久等。
                readFavorites = {
                    val dao = favorite()
                    val list = kotlinx.coroutines.withTimeoutOrNull(2_000) {
                        dao.favoritesFlow.first { it.isNotEmpty() }
                    } ?: dao.favoritesFlow.value
                    list to true
                },
                removeFavorite = { comicId -> favorite().removeFavorite(comicId) },
                readFavoriteFolders = { readCustomFavoriteFolders(paths) },
                createFavoriteFolder = { name -> addCustomFavoriteFolder(paths, name) },
                // 删夹（成员**先移回「默认」**再删，收藏一本不丢）。
                // 桌面这条线的"夹"就是主表 folderName 一格（自建空夹名单另落主 prefs stringset）；
                // LocalFavoritesManager 那套官方夹表是另一本账，不掺和。
                deleteFavoriteFolder = { name ->
                    val dao = favorite()
                    dao.favoritesFlow.value
                        .filter { it.folderName == name }
                        .forEach { row ->
                            dao.addFavorite(
                                com.venera.desktop.gallery.data.desktopFavoriteRecordToWrite(
                                    existing = row,
                                    fresh = row,
                                    folderName = "默认",
                                ),
                            )
                        }
                    removeCustomFavoriteFolder(paths, name)
                },

                // 收藏状态直接查库而非走 toggleFavorite，避免冷启动流未就绪时误判为已收藏并执行删除。
                isFavoriteOf = { comicId -> favorite().isFavorite(comicId) },
                addFavorite = { record -> favorite().addFavorite(record) },

                // 搜索共用引擎锁并返回完整分页与错误信息；引擎未启动时返回明确失败原因而非空表。
                searchComics = { query, page, sourceKey, options ->
                    val e = uiEngine
                    if (e == null) {
                        // 引擎没起来 = 一句**成因**，而不是"空表 = 没搜到"。
                        // 从前这里回 `emptyList()`，屏上于是念「这个词没搜到」——那是把
                        // 我们的失败说成站方的空结果（还的那笔账）。
                        DesktopComicSearchOutcome(emptyList(), null, "引擎没起来：这一趟发不出去。")
                    } else {
                        // 引擎调用须独立成行以 e. 开头；游标型源须回传上一页 next 游标，否则翻页恒返回第 1 页。
                        val cursor = if (page == searchCursorAtPage + 1) searchCursor else null
                        desktopEngineCall(e) {
                            e.load(sourceKey)
                            if (options.isEmpty()) e.search(query, page) else e.searchWith(query, options, page, cursor)
                        }.map { got ->
                            searchCursor = got.next
                            searchCursorAtPage = page
                            // 空串按"没成因"处理（与探索那一路同一条口径：
                            // `page.error?.takeIf { it.isNotBlank() }`）：站方回 `error: ""` 时
                            // 把它当成因上屏，念出来的是一行空话而屏上写着"失败了"。
                            DesktopComicSearchOutcome(
                                got.comics,
                                got.maxPage,
                                got.error?.takeIf { it.isNotBlank() },
                                got.next,
                            )
                        }.getOrElse { t ->
                            // 那一跳整个塌了（JS 抛 / 装载失败）：成因交回**空表 + 一句原话**，
                            // 屏上不许念"没搜到"。这里不折成 failure 抛出，为的是与"站方答了而
                            // 答的是失败"共用同一格 `sourceError`，壳只读一处。
                            DesktopComicSearchOutcome(
                                emptyList(),
                                null,
                                "搜索失败：${t.javaClass.simpleName} " + (t.message?.take(160) ?: ""),
                            )
                        }
                    }
                },
                // 漫画「下载这一话」要落的那个根目录（支持设置页/弹窗自定义，空串回落内置数据根/downloads）。
                downloadsRoot = com.venera.desktop.gallery.data.desktopResolvedComicDownloadsRoot(
                    paths.dataRoot,
                    gallerySettings.comicDownloadDir.collectAsState().value,
                ),
                // 阅读那一屏的模式偏好 + `--read-mode=` 种子。
                // getInstance 同一数据根只建一颗；它构造时读一次 prefs 那份小 JSON（与
                // gallerySettings 同族，不是 browseHistory 那种"开机建库"的重件），装配期建合法。
                readerPrefs = DesktopComicReaderPreferences.getInstance(paths),
                readerModeSeed = readerModeSeed,
                // 那份「画廊浏览历史」档的落点。只递 PathProvider 而不递那颗件 ——
                // `DesktopGalleryBrowseHistory.getInstance` 构造时同步读一次盘，装配期等于开机即建库，
                // 所以取它的那一跳住在壳里那圈 `Dispatchers.IO` 上（与 `writeHistory` 只递动作不递 DAO 同一口径）。
                galleryPaths = paths,
                // 起窗直达种子：`--open=` 认出的那一屏。没给时交回默认值（HOME / Tab），
                // 与从前一字未变；只有带 `--open` 的取证那一跑才递非默认值。
                initialTab = initialTab,
                initialRoute = initialRoute,
                openFirstGalleryPost = openFirstGalleryPost,
                pixivLoginCallback = pixivCallbackState,
                wallBlockAiOf = { gallerySettings.galleryBlockAi.value },
                // 设置那一屏要的两枚具体件（八枚偏好写侧 + 分级遮罩档读流/写口）。
                // 两枚都已经在装配点手上（`gallerySettings` = preferences 件、`guard` = contentGuard 件），
                // 这里只是把它们递下去 —— 不重取（`DesktopGalleryPorts` 那两颗「只建一颗」的口径）。
                galleryPrefs = gallerySettings,
                galleryGuard = guard,
                // 将随包源目录与覆盖层路径传入壳层拼装 ComicSource 清单，避免 UI 层自行读取路径。
                assetDir = assetDir,
                overrideDir = overrideDir,
                blockedByUserOf = { _, _, author, tags ->
                    guard.rules.value.any { rule ->
                        rule.isEnabled &&
                            com.venera.compose.security.guard.GalleryBlockMatch.blocked(
                                rule = rule,
                                author = author,
                                tags = tags,
                            )
                    }
                },
            )
                com.venera.desktop.gallery.ui.DesktopVeneraTheme {
                    DesktopErrorToastOverlay()
                }
                }
            } // CompositionLocalProvider(LocalSettingsGlass)

            if (shot != null) {
                    // 无人值守取证等 UI 落地后再截。这段**留在集成方**（本文件）而不是搬进
                    // `VeneraDesktopApp`：截图与"起来就退出"都是取证的形状，不是应用的形状 ——
                    // 塞进应用根的话，每个正式用户每一次启动都要多走一个 `shot != null` 的判断。
                    LaunchedEffect(Unit) {
                        delay(SELF_SHOT_SETTLE_MS)
                        selfShot(shot)
                        closePersistenceInOrder()
                        kotlin.system.exitProcess(0)
                    }
                }
        }

        // 报错通道自测开关（--error-test / --error-test=thread）：开窗 3s 后抛出测试异常验证日志与弹窗且不退出进程。
        if (errorTest != null) {
            val mode = errorTest.substringAfter('=', "edt")
            LaunchedEffect(Unit) {
                delay(3_000)
                val fake = IllegalStateException(
                    "报错通道自测（假异常，请忽略）：EDT=_awt 时走事件队列，thread=后台线程",
                )
                if (mode == "thread") {
                    Thread {
                        throw fake
                    }.apply { isDaemon = true }.start()
                } else {
                    java.awt.EventQueue.invokeLater {
                        throw fake
                    }
                }
            }
        }
        }
    }
    // 关窗后强制 exitProcess(0) 清理连接池、SQLite 等非守护线程，防止进程残留后台。
    kotlin.system.exitProcess(0)
}

