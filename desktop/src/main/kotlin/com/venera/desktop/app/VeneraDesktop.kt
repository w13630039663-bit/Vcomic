package com.venera.desktop.app

import com.venera.desktop.data.DesktopErrorBus
import com.venera.desktop.ui.DesktopErrorToastOverlay
import com.venera.desktop.ui.DesktopStartupSplash
import com.venera.desktop.ui.DesktopWindowExceptionHandlerFactory
import com.venera.desktop.data.DesktopSearchCursorHolder
import com.venera.desktop.data.deleteDesktopFavoriteFolderAndMove
import com.venera.desktop.data.desktopCapabilityReading
import com.venera.desktop.data.installDesktopCrashHandler
import com.venera.desktop.data.loginDesktopSourceWithCookies
import com.venera.desktop.data.loginDesktopSourceWithPassword
import com.venera.desktop.data.loginDesktopSourceWithWebview
import com.venera.desktop.data.readDesktopFavoritesList
import com.venera.desktop.data.readDesktopSourceRows
import com.venera.desktop.data.saveDesktopSourceScript
import com.venera.desktop.data.searchDesktopComics
import com.venera.desktop.data.writeDesktopSourceEnabled
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.LocalWindowExceptionHandlerFactory
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.zIndex
import com.venera.compose.data.platform.CacheDirs
import com.venera.compose.data.platform.ImageHeaderPolicy
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.security.guard.GalleryBlockMatch
import com.venera.desktop.downloads.DesktopDownloadsDb
import com.venera.desktop.gallery.data.DesktopComicReaderPreferences
import com.venera.desktop.gallery.data.DesktopGalleryHandle
import com.venera.desktop.gallery.data.DesktopGalleryPorts
import com.venera.desktop.gallery.data.DesktopImagePipeline
import com.venera.desktop.gallery.data.DesktopSourceNames
import com.venera.desktop.gallery.data.DesktopSourceOverrides
import com.venera.desktop.gallery.data.DesktopSourceState
import com.venera.desktop.gallery.data.DesktopTagDictionaryAsset
import com.venera.desktop.gallery.data.desktopResolvedComicDownloadsRoot
import com.venera.desktop.gallery.ui.DesktopImageBackdrop
import com.venera.desktop.gallery.ui.DesktopVeneraTheme
import com.venera.desktop.gallery.ui.desktopCanCopyImage
import com.venera.desktop.platform.DesktopDatabasePorts
import com.venera.desktop.platform.DesktopPaths
import com.venera.desktop.platform.DesktopPixivCallback
import com.venera.desktop.platform.DesktopProtocolRegistrar
import com.venera.desktop.platform.DesktopSingleInstance
import com.venera.desktop.platform.desktopPixivCallbackUrl
import com.venera.desktop.platform.desktopRunLogFile
import com.venera.desktop.platform.desktopRunLogReading
import com.venera.desktop.platform.installDesktopRunLog
import com.venera.desktop.settings.DesktopBackdropMode
import com.venera.desktop.settings.DesktopLanguage
import com.venera.desktop.settings.DesktopRailBlur
import com.venera.desktop.settings.DesktopThemeMode
import com.venera.desktop.settings.DesktopTopBarBlur
import com.venera.desktop.settings.DesktopUiScale
import com.venera.desktop.settings.LocalDesktopLanguage
import com.venera.desktop.settings.LocalSettingsGlass
import com.venera.desktop.settings.settingsGlassFor
import com.venera.desktop.ui.DesktopGlobalNavAction
import com.venera.desktop.ui.DesktopGlobalShortcutBus
import com.venera.desktop.ui.LocalDesktopShortcutBus
import com.venera.desktop.ui.RegisterDesktopShortcutHandler
import com.venera.desktop.ui.matchGlobalNavShortcut
import com.venera.desktop.ui.theme.VeneraFonts
import com.venera.desktop.ui.toShortcutSignalOrNull
import com.venera.engineprobe.DesktopCookieJar
import com.venera.engineprobe.DesktopJsHost
import com.venera.engineprobe.EngineAssets
import com.venera.engineprobe.EngineSession
import com.venera.engineprobe.onCookieJarPersistFailure
import com.venera.engineprobe.onEngineSourceInitFailure
import dev.nucleusframework.application.DecoratedWindow
import dev.nucleusframework.application.NucleusBackend
import dev.nucleusframework.application.nucleusApplication
import dev.nucleusframework.window.WindowsBackdrop
import dev.nucleusframework.window.windowDragArea
import java.io.File
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicLong
import javax.imageio.ImageIO
import javax.swing.SwingUtilities
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking

/**
 * 桌面端应用唯一主入口与运行期端口装配点。
 *
 * 启动偏好回填及取证命令行解析见 [DesktopStartupBootstrap]，
 * 窗口生命周期与 GPU/内存回收钩子见 [DesktopWindowHooks]，
 * 源清单及引擎回调实现见 [DesktopEngineHostActions]。
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main(args: Array<String>) {
    if (args.any { it.equals("--installer-mode", ignoreCase = true) }) {
        com.venera.desktop.installer.main(
            args.filterNot { it.equals("--installer-mode", ignoreCase = true) }.toTypedArray(),
        )
        return
    }
    installDesktopCrashHandler()
    val root = File(System.getProperty("user.dir"))
    val assetDir = File(root, "desktop/src/main/resources")
    val paths = DesktopPaths.create()
    val runLogFile = desktopRunLogFile(paths.dataRoot, LocalDateTime.now(), ProcessHandle.current().pid())
    val runLog = installDesktopRunLog(runLogFile)
    println(desktopRunLogReading(runLog, runLogFile.path))
    run {
        val bundled = VeneraFonts.bundled
        println(
            "D_字体 随包 MiSans=" + bundled +
                (if (bundled) " 四档齐" else " 未随包 ⇒ 回退系统字体（查 resources/fonts 与 build.gradle 白名单）"),
        )
    }

    val proxy = args.firstOrNull { it.startsWith("--proxy=") }?.removePrefix("--proxy=")
    if (proxy != null) System.setProperty("venera.proxy", proxy)
    val shot = args.firstOrNull { it.startsWith("--shot=") }?.removePrefix("--shot=")
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

    restoreDesktopStartupPreferences(paths)

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

    val forensic = resolveDesktopForensicLaunch(args)

    val sources = EngineAssets.listSourceKeys(assetDir)
    println("D_启动 sources=${sources.size} 起始=$startKey proxy=${proxy ?: "直连"} 仓库兜底=${assetDir.path}")
    // 打包产物中 ImageIO 无法在临时目录创建缓存文件，且输入均为内存 ByteArray，故全局关闭磁盘缓存。
    ImageIO.setUseCache(false)
    println(
        "D_环境 jdk=${System.getProperty("java.version")} " +
            "tmp=${System.getProperty("java.io.tmpdir")} " +
            "打包=${System.getProperty("jpackage.app-path") != null} imageio=内存",
    )
    println("D_数据目录 ${paths.dataRoot.path}（写入回读已验）")

    DesktopDatabasePorts.install(paths)
    DesktopDownloadsDb.install(paths)
    val galleryHandle = DesktopGalleryHandle.of(paths)
    DesktopGalleryPorts.install(galleryHandle)
    val galleryPorts = GalleryPorts.of(galleryHandle)
    val gallerySettings = DesktopGalleryPorts.preferences(galleryHandle)
    val sharedCookieJar = DesktopCookieJar(DesktopCookieJar.fileIn(paths.dataRoot))
    registerDesktopCloudflareBridge(paths, sharedCookieJar)

    DesktopImagePipeline.install(
        galleryHandle.engine,
        CacheDirs.inCache(paths.cacheRoot, DesktopImagePipeline.IMAGE_CACHE_DIR_NAME),
        maxSizeBytes = gallerySettings.imageCacheMb.value * 1024L * 1024L,
    )
    val guard = DesktopGalleryPorts.contentGuard(galleryHandle)
    val coreDb = DesktopDatabasePorts.coreDbFile()
    val favDb = DesktopDatabasePorts.favoritesDbFile()
    println(
        "D_接线 平台=desktop core=${coreDb.path} 已建=${coreDb.exists()} " +
            "fav=${favDb.path} 已建=${favDb.exists()}",
    )
    println(desktopCapabilityReading(desktopCanCopyImage(), DesktopTagDictionaryAsset.presence()))

    if (forensic.favcheck == true) {
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

    if (forensic.autofav == true) {
        val code = runAutofavEvidence(assetDir, paths.dataRoot, proxy, startKey, forensic.seedCookies)
        closePersistenceInOrder()
        kotlin.system.exitProcess(code)
        return
    }

    when (ForensicFlags.crash(args.asList())) {
        "thread" -> Thread({ throw IllegalStateException("取证种子：非 EDT 那一笔") }, "取证-非EDT").start()
        "edt" -> SwingUtilities.invokeLater { throw IllegalStateException("取证种子：EDT 那一笔") }
        else -> Unit
    }

    nucleusApplication(args = args, backend = NucleusBackend.Tao) {
        CompositionLocalProvider(
            LocalWindowExceptionHandlerFactory provides DesktopWindowExceptionHandlerFactory,
        ) {
            val geometry = rememberDesktopWindowGeometryConfig(paths)
            val windowState = geometry.windowState
            DecoratedWindow(
                onCloseRequest = {
                    closePersistenceInOrder()
                    exitApplication()
                },
                title = APP_TITLE,
                icon = painterResource("vcomic_icon.png"),
                state = windowState,
            ) {
                val isWindowsHost = remember {
                    System.getProperty("os.name", "").contains("win", ignoreCase = true)
                }
                val backdropMode by DesktopBackdropMode.mode.collectAsState()
                val railBlur by DesktopRailBlur.visible.collectAsState()
                val topBarBlur by DesktopTopBarBlur.visible.collectAsState()
                if (isWindowsHost) {
                    WindowsBackdrop(DesktopBackdropMode.styleOf(backdropMode, railBlur, topBarBlur))
                }
                val maximized by nucleusWindow.maximizedFlow.collectAsState()
                val pixivCallback by pixivCallbackState.collectAsState()
                InstallDesktopWindowLifecycleEffects(
                    geometry = geometry,
                    pixivCallbackId = pixivCallback?.id,
                    launchListener = launchListener,
                    singleInstance = singleInstance,
                )
                val purgeGpuCache = rememberDesktopGpuCachePurgeAndIdleReclaim(windowState, maximized)
                InstallDesktopSystemTrayEffect(guard = guard, onExitApplication = ::exitApplication)

                val sourceState = remember { DesktopSourceState(paths) }
                val overrideDir = remember {
                    runCatching { DesktopSourceOverrides.dirFor(paths) }.onFailure {
                        println("D_源改写 目录建不出来，编辑这一路关掉：${it.javaClass.simpleName} ${it.message?.take(160)}")
                    }.getOrNull()
                }
                val uiEngine = remember {
                    runCatching {
                        val host = DesktopJsHost(
                            assetDir,
                            paths.dataRoot,
                            proxy,
                            forensic.seedCookies,
                            sharedCookieJar = sharedCookieJar,
                        )
                        onEngineSourceInitFailure = { key, detail ->
                            DesktopErrorBus.report(
                                category = "系统",
                                headline = "漫画源「$key」初始化失败",
                                humanReason = "该漫画源脚本初始化未成功，可能暂时无法拉取内容。",
                                detail = detail,
                                dedupKey = "engine:source_init:$key",
                            )
                        }
                        onCookieJarPersistFailure = { reason, detail ->
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
                                ImageHeaderPolicy.publishForUrls(listOf(url), headers)
                            },
                        )
                    }.onFailure {
                        val msg = "${it::class.java.simpleName}: ${it.message?.take(200)}"
                        println("D_引擎 UI 起不来 $msg")
                        DesktopErrorBus.report("引擎", "脚本引擎启动失败", msg, dedupKey = "engine:init")
                    }.getOrNull()
                }
                DisposableEffect(uiEngine) {
                    onDispose { runCatching { uiEngine?.close() } }
                }

                val searchCursorHolder = remember { DesktopSearchCursorHolder() }
                val themeMode by DesktopThemeMode.mode.collectAsState()
                val themeDark = DesktopThemeMode.isDark(themeMode)
                val uiScale by DesktopUiScale.scale.collectAsState()
                val baseDensity = LocalDensity.current
                val scaledDensity = Density(
                    density = baseDensity.density * uiScale,
                    fontScale = baseDensity.fontScale,
                )
                val guardMaskMode by guard.nsfwMaskMode.collectAsState()
                val shortcutBus = remember { DesktopGlobalShortcutBus() }
                val rootFocusManager = LocalFocusManager.current
                val isWindowFocused by nucleusWindow.focusFlow.collectAsState(initial = true)
                InstallDesktopShortcutsEffect(shortcutBus = shortcutBus, isWindowFocused = isWindowFocused)
                val desktopLang by DesktopLanguage.mode.collectAsState()

                CompositionLocalProvider(
                    LocalDensity provides scaledDensity,
                    LocalSettingsGlass provides settingsGlassFor(themeDark),
                    LocalDesktopShortcutBus provides shortcutBus,
                    LocalGpuCachePurge provides purgeGpuCache,
                    LocalDesktopLanguage provides desktopLang,
                ) {
                    RegisterDesktopShortcutHandler(windowState, priority = -10) { signal ->
                        if (matchGlobalNavShortcut(signal) == DesktopGlobalNavAction.TOGGLE_FULLSCREEN) {
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
                    var shellBackdrop by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .onPreviewKeyEvent { keyEvent ->
                                val signal = keyEvent.toShortcutSignalOrNull() ?: return@onPreviewKeyEvent false
                                val nav = matchGlobalNavShortcut(signal) ?: return@onPreviewKeyEvent false
                                if (nav == DesktopGlobalNavAction.ESCAPE) {
                                    rootFocusManager.clearFocus(force = true)
                                    return@onPreviewKeyEvent shortcutBus.dispatch(signal)
                                }
                                false
                            },
                    ) {
                        val detailBackdrop = shellBackdrop
                        if (detailBackdrop != null) {
                            CompositionLocalProvider(
                                com.venera.compose.ui.tokens.LocalVeneraDarkTheme provides detailBackdrop.second,
                            ) {
                                DesktopImageBackdrop(
                                    url = detailBackdrop.first,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(
                                            if (maximized) RoundedCornerShape(0.dp)
                                            else RoundedCornerShape(12.dp),
                                        ),
                                )
                            }
                        }
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
                            onWindowMinimize = { nucleusWindow.setMinimized(true) },
                            onWindowToggleMaximize = { nucleusWindow.setMaximized(!nucleusWindow.isMaximized) },
                            windowMaximized = maximized,
                            onShellBackdropChange = { url, dark -> shellBackdrop = url?.let { it to dark } },
                            onWindowClose = {
                                closePersistenceInOrder()
                                exitApplication()
                            },
                            windowDragModifier = Modifier.windowDragArea(enabled = true),
                            maskModeOf = { guardMaskMode },
                            wallGuardOf = { guard },
                            engine = uiEngine,
                            followDao = comicFollowDao(),
                            writeHistory = { record -> history().saveHistory(record) },
                            readHistoryRow = { comicId -> history().getHistory(comicId) },
                            setMaskMode = { mode -> guard.setNsfwMaskMode(mode) },
                            setBlockAi = { on -> gallerySettings.setGalleryBlockAi(on) },
                            readSources = {
                                readDesktopSourceRows(assetDir, overrideDir, sourceState, forensic.seedDisabled, uiEngine)
                            },
                            writeSourceEnabled = { key, _ -> writeDesktopSourceEnabled(assetDir, sourceState, key) },
                            writeSourceOrder = { ordered -> sourceState.setOrderKeys(ordered) },
                            readSourceText = overrideDir?.let { dir ->
                                { key: String -> DesktopSourceNames.scriptOf(assetDir, key, dir) }
                            },
                            saveSourceText = { key, content -> saveDesktopSourceScript(overrideDir, uiEngine, key, content) },
                            clearSourceOverride = { key ->
                                val dir = overrideDir ?: error("改写层目录不在手，撤不掉：$key")
                                val ok = DesktopSourceOverrides.clear(dir, key)
                                println("D_源改写 撤=$key 成=$ok")
                                if (!ok) error("撤不掉 $key 的改写层（文件不在或删除被拒）")
                            },
                            loginWithCookies = { key, values -> loginDesktopSourceWithCookies(paths, uiEngine, key, values) },
                            loginWithPassword = { key, account, password ->
                                loginDesktopSourceWithPassword(uiEngine, key, account, password)
                            },
                            loginWithWebview = { key -> loginDesktopSourceWithWebview(uiEngine, key) },
                            readFavorites = { readDesktopFavoritesList() },
                            removeFavorite = { comicId -> favorite().removeFavorite(comicId) },
                            readFavoriteFolders = { readCustomFavoriteFolders(paths) },
                            createFavoriteFolder = { name -> addCustomFavoriteFolder(paths, name) },
                            deleteFavoriteFolder = { name -> deleteDesktopFavoriteFolderAndMove(paths, name) },
                            isFavoriteOf = { comicId -> favorite().isFavorite(comicId) },
                            addFavorite = { record -> favorite().addFavorite(record) },
                            searchComics = { query, page, sourceKey, options ->
                                searchDesktopComics(uiEngine, searchCursorHolder, query, page, sourceKey, options)
                            },
                            downloadsRoot = desktopResolvedComicDownloadsRoot(
                                paths.dataRoot,
                                gallerySettings.comicDownloadDir.collectAsState().value,
                            ),
                            readerPrefs = DesktopComicReaderPreferences.getInstance(paths),
                            readerModeSeed = forensic.readerModeSeed,
                            galleryPaths = paths,
                            initialTab = forensic.initialTab,
                            initialRoute = forensic.initialRoute,
                            openFirstGalleryPost = forensic.openFirstGalleryPost,
                            pixivLoginCallback = pixivCallbackState,
                            wallBlockAiOf = { gallerySettings.galleryBlockAi.value },
                            galleryPrefs = gallerySettings,
                            galleryGuard = guard,
                            assetDir = assetDir,
                            overrideDir = overrideDir,
                            blockedByUserOf = { _, _, author, tags ->
                                guard.rules.value.any { rule ->
                                    rule.isEnabled && GalleryBlockMatch.blocked(rule = rule, author = author, tags = tags)
                                }
                            },
                        )
                        DesktopVeneraTheme {
                            DesktopErrorToastOverlay()
                        }
                    }
                }

                if (shot != null) {
                    LaunchedEffect(Unit) {
                        delay(SELF_SHOT_SETTLE_MS)
                        selfShot(shot)
                        closePersistenceInOrder()
                        kotlin.system.exitProcess(0)
                    }
                }
            }

            if (errorTest != null) {
                val mode = errorTest.substringAfter('=', "edt")
                LaunchedEffect(Unit) {
                    delay(3_000)
                    val fake = IllegalStateException(
                        "报错通道自测（假异常，请忽略）：EDT=_awt 时走事件队列，thread=后台线程",
                    )
                    if (mode == "thread") {
                        Thread { throw fake }.apply { isDaemon = true }.start()
                    } else {
                        java.awt.EventQueue.invokeLater { throw fake }
                    }
                }
            }
        }
    }
    kotlin.system.exitProcess(0)
}
