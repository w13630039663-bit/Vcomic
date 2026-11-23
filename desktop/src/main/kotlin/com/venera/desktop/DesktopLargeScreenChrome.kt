package com.venera.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.venera.compose.components.LocalVeneraSideBarDownloadAnchor
import com.venera.compose.components.VeneraNavTab
import com.venera.compose.components.VeneraSideBar
import com.venera.compose.components.VeneraSideBarAccountCard
import com.venera.compose.components.VeneraSideBarDownloadActivity
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.compose.data.platform.PathProvider
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.gallery.data.PixivAuthState
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.downloads.DesktopFilesSegment
import com.venera.desktop.gallery.data.DesktopComicQueueStatus
import com.venera.desktop.gallery.data.DesktopComicQueueTask
import com.venera.desktop.gallery.data.DesktopComicReaderPreferences
import com.venera.desktop.gallery.data.DesktopGalleryContentGuard
import com.venera.desktop.gallery.data.DesktopGalleryCredentials
import com.venera.desktop.gallery.data.DesktopGalleryHandle
import com.venera.desktop.gallery.data.DesktopGalleryPreferences
import com.venera.desktop.gallery.data.DesktopKonachanWebViewHost
import com.venera.desktop.gallery.data.DesktopPixivAccount
import com.venera.desktop.gallery.ui.DesktopGlass
import com.venera.desktop.gallery.ui.LocalChromePalette
import com.venera.desktop.settings.DesktopBackdropMode
import com.venera.desktop.settings.DesktopHomeSkin
import com.venera.desktop.settings.DesktopRailBlur
import com.venera.desktop.settings.DesktopSettingsHub
import com.venera.desktop.settings.DesktopThemeMode
import com.venera.desktop.settings.DesktopTopBarBlur
import com.venera.desktop.ui.DesktopCommandBar
import com.venera.desktop.ui.DesktopGlobalNavAction
import com.venera.desktop.ui.DesktopTitleBarAction
import com.venera.desktop.ui.DesktopWindowTitleBar
import com.venera.desktop.ui.RegisterDesktopShortcutHandler
import com.venera.desktop.ui.matchGlobalNavShortcut
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 大屏主窗口外壳：负责窗根键盘返回、全局快捷键、整窗糊图背板同步、
 * 经典/浅皮标题栏、飞入下载叠层、Konachan 过盾宿主及侧边栏骨架。
 *
 * 所有状态均由调用方显式传入，避免隐式依赖静态单例。
 */
@Composable
internal fun DesktopLargeScreenChromeScaffold(
    route: DesktopRoute,
    onRouteChange: (DesktopRoute) -> Unit,
    currentTab: VeneraNavTab,
    onTabChange: (VeneraNavTab) -> Unit,
    onPetSwitchPage: () -> Unit,
    onOpenSettings: () -> Unit,
    backActionProvider: ((DesktopRoute) -> Unit) -> (() -> Unit)?,
    modifier: Modifier = Modifier,
    shellMode: WideScreenLayoutMode,
    fullscreenChrome: Boolean,
    discoverViewExplore: Boolean,
    windowMaximized: Boolean,
    onWindowMinimize: (() -> Unit)?,
    onWindowToggleMaximize: (() -> Unit)?,
    onWindowClose: (() -> Unit)?,
    windowDragModifier: Modifier,
    onShellBackdropChange: (String?, Boolean) -> Unit,
    railExpandedSaved: Boolean,
    onRailExpandedChange: (Boolean) -> Unit,
    filesSegment: DesktopFilesSegment,
    onOpenFilesSegment: (DesktopFilesSegment) -> Unit,
    queueTasks: List<DesktopComicQueueTask>,
    favoritesLoaded: Boolean,
    favoritesCount: Int,
    pixivAuthState: PixivAuthState,
    pixivAccountAvatarUrl: String?,
    flyState: DesktopFlyState,
    flyAnchor: com.venera.compose.components.VeneraSideBarDownloadAnchor,
    flyHostTopLeft: Offset,
    onFlyHostTopLeftChange: (Offset) -> Unit,
    flyPointer: MutableState<Offset?>,
    content: @Composable BoxScope.() -> Unit,
) {
    val windowShape = if (windowMaximized) RoundedCornerShape(0.dp) else RoundedCornerShape(12.dp)
    val homeSkin = DesktopHomeSkin.mode.collectAsState().value
    val homeSkinLight = homeSkin == DesktopHomeSkin.LIGHT
    val railBlurState by DesktopRailBlur.visible.collectAsState()
    val topBarBlurState by DesktopTopBarBlur.visible.collectAsState()
    val chromePalette = LocalChromePalette.current
    val immersiveRoute = route is DesktopRoute.Reader ||
        route is DesktopRoute.LocalComicReader ||
        route is DesktopRoute.GalleryPostViewer
    val backFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { backFocus.requestFocus() } }

    val detailBackdropUrl = when (val cur = route) {
        is DesktopRoute.PixivWork -> cur.coverUrl
        is DesktopRoute.GalleryPostDetail -> cur.post.url
        else -> null
    }
    val shellBackdropUrl: String? =
        if (!fullscreenChrome && detailBackdropUrl != null) detailBackdropUrl else null
    val shellBackdropDark = DesktopThemeMode.isDark(DesktopThemeMode.mode.collectAsState().value)
    LaunchedEffect(shellBackdropUrl, shellBackdropDark) {
        onShellBackdropChange(shellBackdropUrl, shellBackdropDark)
    }
    DisposableEffect(Unit) { onDispose { onShellBackdropChange(null, false) } }

    RegisterDesktopShortcutHandler(route, currentTab, priority = 0) { signal ->
        when (matchGlobalNavShortcut(signal)) {
            DesktopGlobalNavAction.ESCAPE -> {
                val back = backActionProvider(onRouteChange) ?: return@RegisterDesktopShortcutHandler false
                back()
                true
            }
            DesktopGlobalNavAction.FOCUS_SEARCH -> {
                onRouteChange(DesktopRoute.Tab)
                onTabChange(VeneraNavTab.SEARCH)
                true
            }
            DesktopGlobalNavAction.TAB_1 -> {
                onRouteChange(DesktopRoute.Tab)
                onTabChange(VeneraNavTab.HOME)
                true
            }
            DesktopGlobalNavAction.TAB_2 -> {
                onRouteChange(DesktopRoute.Tab)
                onTabChange(VeneraNavTab.GALLERY)
                true
            }
            DesktopGlobalNavAction.TAB_3 -> {
                onRouteChange(DesktopRoute.Tab)
                onTabChange(VeneraNavTab.FAVORITES)
                true
            }
            DesktopGlobalNavAction.TAB_4 -> {
                onRouteChange(DesktopRoute.Tab)
                onTabChange(VeneraNavTab.SEARCH)
                true
            }
            DesktopGlobalNavAction.TAB_5 -> {
                onRouteChange(DesktopRoute.Downloads)
                true
            }
            else -> false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(backFocus)
            .focusable()
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                val isBack = e.key == Key.Escape ||
                    (e.isAltPressed && e.key == Key.DirectionLeft)
                if (!isBack) return@onKeyEvent false
                val back = backActionProvider(onRouteChange) ?: return@onKeyEvent false
                back()
                true
            }
            .clip(windowShape)
            .border(
                1.dp,
                if (homeSkinLight) chromePalette.frame
                else VeneraTokens.color.outlineVariant.copy(alpha = 0.6f),
                windowShape,
            )
            .background(
                when {
                    shellBackdropUrl != null -> Color.Transparent
                    DesktopBackdropMode.isTransparentActive() -> Color.Transparent
                    DesktopDwmBackdrop.active && (railBlurState || topBarBlurState) -> Color.Transparent
                    !homeSkinLight -> VeneraTokens.color.background
                    else -> chromePalette.page
                }
            ),
    ) {
        if (!fullscreenChrome && !homeSkinLight) {
            DesktopWindowTitleBar(
                breadcrumb = desktopShellTitle(route, currentTab, discoverViewExplore),
                backdropFromShell = shellBackdropUrl != null,
                onBack = backActionProvider(onRouteChange),
                onOpenSearch = {
                    onTabChange(VeneraNavTab.SEARCH)
                    onRouteChange(DesktopRoute.Tab)
                },
                actions = buildList {
                    add(
                        DesktopTitleBarAction("⌕ 搜索") {
                            onTabChange(VeneraNavTab.SEARCH)
                            onRouteChange(DesktopRoute.Tab)
                        },
                    )
                    add(
                        DesktopTitleBarAction("⚙ 设置") {
                            onOpenSettings()
                        },
                    )
                },
                onMinimize = onWindowMinimize,
                onToggleMaximize = onWindowToggleMaximize,
                maximized = windowMaximized,
                onClose = onWindowClose,
                dragModifier = windowDragModifier,
            )
        } else if (fullscreenChrome) {
            if (!homeSkinLight && onWindowMinimize != null) {
                Box(
                    modifier = windowDragModifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(VeneraTokens.color.background),
                )
            }
        }

        CompositionLocalProvider(LocalVeneraSideBarDownloadAnchor provides flyAnchor) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .onGloballyPositioned { onFlyHostTopLeftChange(it.boundsInRoot().topLeft) }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            flyPointer.value = awaitFirstDown(requireUnconsumed = false).position
                        }
                    },
            ) {
                DesktopFlyOverlay(
                    state = flyState,
                    anchor = flyAnchor,
                    hostTopLeftInRoot = flyHostTopLeft,
                    onLanded = { flyAnchor.popTick.value += 1 },
                )
                if (com.venera.desktop.gallery.data.DesktopKonachanBridge.mountRequested.value) {
                    DesktopKonachanWebViewHost()
                }
                val railDownloadActivity = run {
                    val running = queueTasks.firstOrNull {
                        it.status == DesktopComicQueueStatus.RUNNING
                    }
                    val active = queueTasks.count {
                        it.status == DesktopComicQueueStatus.RUNNING ||
                            it.status == DesktopComicQueueStatus.PENDING
                    }
                    when {
                        running != null ->
                            VeneraSideBarDownloadActivity(
                                title = running.comicTitle,
                                progressLine = "正在下 ${running.downloadedPages}/${running.totalPages} 页 · ${running.chapterTitle}",
                                activeCount = active,
                            )
                        active > 0 ->
                            VeneraSideBarDownloadActivity(
                                title = "下载队列",
                                progressLine = "$active 个任务排队中",
                                activeCount = active,
                            )
                        else ->
                            VeneraSideBarDownloadActivity(
                                title = "下载与本地",
                                progressLine = "当前没有进行中的下载",
                            )
                    }
                }
                VeneraSideBar(
                    currentTab = currentTab,
                    onTabSelected = {
                        onTabChange(it)
                        onRouteChange(DesktopRoute.Tab)
                        onPetSwitchPage()
                    },
                    mode = shellMode,
                    modifier = Modifier.fillMaxSize(),
                    onSettingsSelected = onOpenSettings,
                    settingsSelected = route is DesktopRoute.Settings,
                    secondaryGroups = desktopSideBarGroups(
                        route,
                        filesSegment,
                        openFilesSegment = onOpenFilesSegment,
                    ) { onRouteChange(it) },
                    favoriteBadge = if (favoritesLoaded) favoritesCount else null,
                    downloadActivity = railDownloadActivity,
                    onDownloadActivityClick = { onRouteChange(DesktopRoute.Files) },
                    downloadsSelected = route is DesktopRoute.Files ||
                        route is DesktopRoute.LocalComics ||
                        route is DesktopRoute.Downloads ||
                        route is DesktopRoute.Queue,
                    accountCard = when (val auth = pixivAuthState) {
                        is PixivAuthState.SignedIn ->
                            VeneraSideBarAccountCard(
                                initial = auth.userName?.take(1)?.uppercase().takeUnless { it.isNullOrBlank() } ?: "P",
                                name = auth.userName ?: "pixiv 用户",
                                subtitle = "pixiv · 已登录",
                                logged = true,
                                avatarUrl = pixivAccountAvatarUrl,
                            )
                        else ->
                            VeneraSideBarAccountCard(
                                initial = "?",
                                name = "pixiv 未登录",
                                subtitle = "点按登录 pixiv",
                                logged = false,
                            )
                    },
                    onAccountCardClick = { onRouteChange(DesktopRoute.PixivAccount) },
                    railExpandedWidth = if (homeSkinLight) 260.dp else 264.dp,
                    railCollapsedWidth = if (homeSkinLight) 64.dp else 76.dp,
                    hiddenTabs = setOf(VeneraNavTab.EXPLORE),
                    tabTitleOverride = mapOf(VeneraNavTab.SEARCH to "漫画搜索"),
                    contentSurface = if (homeSkinLight && shellBackdropUrl == null) {
                        if (DesktopBackdropMode.isTransparentActive() && !immersiveRoute) {
                            DesktopGlass.surface(chromePalette)
                        } else {
                            chromePalette.page
                        }
                    } else {
                        null
                    },
                    railSurface = if (homeSkinLight && shellBackdropUrl == null) {
                        if (DesktopDwmBackdrop.active && railBlurState) {
                            DesktopGlass.surface(chromePalette)
                        } else {
                            chromePalette.page
                        }
                    } else {
                        null
                    },
                    contentTopBar = if (homeSkinLight && !fullscreenChrome) {
                        {
                            DesktopCommandBar(
                                palette = chromePalette,
                                breadcrumb = desktopShellTitle(route, currentTab, discoverViewExplore),
                                onBack = backActionProvider(onRouteChange),
                                onMinimize = onWindowMinimize,
                                onToggleMaximize = onWindowToggleMaximize,
                                maximized = windowMaximized,
                                onClose = onWindowClose,
                                dragModifier = windowDragModifier,
                                bareBackdrop = shellBackdropUrl != null,
                                transparentBackdrop = DesktopDwmBackdrop.active && topBarBlurState,
                            )
                        }
                    } else {
                        null
                    },
                    railSkin = if (homeSkinLight) {
                        {
                            VeneraLightRailSkin(
                                scope = this,
                                currentTab = currentTab,
                                onTabSelected = {
                                    onTabChange(it)
                                    onRouteChange(DesktopRoute.Tab)
                                    onPetSwitchPage()
                                },
                                secondaryGroups = desktopSideBarGroups(
                                    route,
                                    filesSegment,
                                    openFilesSegment = onOpenFilesSegment,
                                ) { onRouteChange(it) },
                                downloadActivity = railDownloadActivity,
                                onDownloadActivityClick = { onRouteChange(DesktopRoute.Files) },
                                downloadsSelected = route is DesktopRoute.Files ||
                                    route is DesktopRoute.LocalComics ||
                                    route is DesktopRoute.Downloads ||
                                    route is DesktopRoute.Queue,
                                accountCard = when (val auth = pixivAuthState) {
                                    is PixivAuthState.SignedIn ->
                                        VeneraSideBarAccountCard(
                                            initial = auth.userName?.take(1)?.uppercase().takeUnless { it.isNullOrBlank() } ?: "P",
                                            name = auth.userName ?: "pixiv 用户",
                                            subtitle = "pixiv · 已登录",
                                            logged = true,
                                            avatarUrl = pixivAccountAvatarUrl,
                                        )
                                    else ->
                                        VeneraSideBarAccountCard(
                                            initial = "?",
                                            name = "pixiv 未登录",
                                            subtitle = "点按登录 pixiv",
                                            logged = false,
                                        )
                                },
                                onAccountCardClick = { onRouteChange(DesktopRoute.PixivAccount) },
                                onSettingsSelected = onOpenSettings,
                                settingsSelected = route is DesktopRoute.Settings,
                            )
                        }
                    } else {
                        null
                    },
                    initialExpanded = railExpandedSaved,
                    onExpandedChange = onRailExpandedChange,
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            content = content,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 大屏壳外围浮层：设置中枢叠层与源脚本编辑 Dialog。
 */
@Composable
internal fun DesktopLargeScreenOverlays(
    settingsOpen: Boolean,
    route: DesktopRoute,
    routeBeforeSettings: DesktopRoute,
    onRouteChange: (DesktopRoute) -> Unit,
    onHistoryTickIncrement: () -> Unit,
    onSourceTickIncrement: () -> Unit,
    layoutMode: WideScreenLayoutMode,
    ports: GalleryPorts,
    galleryPrefs: DesktopGalleryPreferences?,
    galleryGuard: DesktopGalleryContentGuard?,
    readerPrefs: DesktopComicReaderPreferences?,
    galleryPaths: PathProvider?,
    wallDensity: com.venera.desktop.gallery.ui.DesktopWallDensity,
    onPickWallDensity: (com.venera.desktop.gallery.ui.DesktopWallDensity) -> Unit,
    sourceEditText: String?,
    saveSourceText: (suspend (key: String, content: String) -> String?)?,
    scope: CoroutineScope,
) {
    DesktopSettingsOverlayLayer(
        settingsOpen = settingsOpen,
        routeBeforeSettings = routeBeforeSettings,
        onRouteChange = onRouteChange,
        onHistoryTickIncrement = onHistoryTickIncrement,
        layoutMode = layoutMode,
        ports = ports,
        galleryPrefs = galleryPrefs,
        galleryGuard = galleryGuard,
        readerPrefs = readerPrefs,
        galleryPaths = galleryPaths,
        wallDensity = wallDensity,
        onPickWallDensity = onPickWallDensity,
    )
    val editRoute = route as? DesktopRoute.SourceEdit
    if (editRoute != null) {
        com.venera.compose.feature.sourcemanage.SourceEditScreen(
            fileName = "${editRoute.key}.js",
            sourceName = editRoute.title,
            initialText = sourceEditText ?: "",
            onSave = { content, onResult ->
                val save = saveSourceText
                if (save == null) {
                    onResult(false, "保存失败")
                } else {
                    scope.launch {
                        val note = runCatching { save(editRoute.key, content) }.fold(
                            onSuccess = { it },
                            onFailure = { t ->
                                "保存这一趟整个没跑成：${t.javaClass.simpleName} " +
                                    (t.message?.take(240) ?: "")
                            },
                        )
                        if (note == null) {
                            onSourceTickIncrement()
                            onRouteChange(DesktopRoute.SourceManage)
                            onResult(true, null)
                        } else {
                            onResult(false, note)
                        }
                    }
                }
            },
            onClose = {
                onRouteChange(DesktopRoute.SourceManage)
            },
        )
    }
}

internal fun petImage(card: com.venera.desktop.gallery.ui.DesktopWallCardModel) {
    com.venera.desktop.ui.components.DesktopPetCueBus.emit(
        com.venera.desktop.ui.components.DesktopPetCue.OPEN_IMAGE,
        com.venera.desktop.ui.components.DesktopPetPayload(
            title = card.title,
            author = card.author,
            tag = card.tags.substringBefore(' ').trim(),
            masked = card.masked,
        ),
    )
}

internal fun petWork(title: String, author: String = "", tag: String = "") {
    com.venera.desktop.ui.components.DesktopPetCueBus.emit(
        com.venera.desktop.ui.components.DesktopPetCue.OPEN_WORK,
        com.venera.desktop.ui.components.DesktopPetPayload(title = title, author = author, tag = tag),
    )
}

internal fun petSearch(term: String) {
    com.venera.desktop.ui.components.DesktopPetCueBus.emit(
        com.venera.desktop.ui.components.DesktopPetCue.SEARCH,
        if (term.isBlank()) null else com.venera.desktop.ui.components.DesktopPetPayload(tag = term.trim()),
    )
}

internal fun petSource(name: String) {
    com.venera.desktop.ui.components.DesktopPetCueBus.emit(
        com.venera.desktop.ui.components.DesktopPetCue.SWITCH_SOURCE,
        if (name.isBlank()) null else com.venera.desktop.ui.components.DesktopPetPayload(source = name.trim()),
    )
}

internal fun petPost(author: String) {
    com.venera.desktop.ui.components.DesktopPetCueBus.emit(
        com.venera.desktop.ui.components.DesktopPetCue.OPEN_IMAGE,
        com.venera.desktop.ui.components.DesktopPetPayload(author = author),
    )
}

internal fun petFilter() {
    com.venera.desktop.ui.components.DesktopPetCueBus.emit(
        com.venera.desktop.ui.components.DesktopPetCue.FILTER,
    )
}

internal fun petSwitchPage() {
    com.venera.desktop.ui.components.DesktopPetCueBus.emit(
        com.venera.desktop.ui.components.DesktopPetCue.SWITCH_PAGE,
    )
}


/**
 * 设置中枢半透明居中叠层（挂载在主内容区内部，覆盖内容区之上）。
 */
@Composable
internal fun DesktopSettingsOverlayLayer(
    settingsOpen: Boolean,
    routeBeforeSettings: DesktopRoute,
    onRouteChange: (DesktopRoute) -> Unit,
    onHistoryTickIncrement: () -> Unit,
    layoutMode: WideScreenLayoutMode,
    ports: GalleryPorts,
    galleryPrefs: DesktopGalleryPreferences?,
    galleryGuard: DesktopGalleryContentGuard?,
    readerPrefs: DesktopComicReaderPreferences?,
    galleryPaths: PathProvider?,
    wallDensity: com.venera.desktop.gallery.ui.DesktopWallDensity,
    onPickWallDensity: (com.venera.desktop.gallery.ui.DesktopWallDensity) -> Unit,
) {
    if (!settingsOpen) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onRouteChange(routeBeforeSettings) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (galleryPrefs != null && galleryGuard != null) {
            val galleryCreds = ports.credentials as? DesktopGalleryCredentials
            val settingsComicPrefs = galleryPaths?.let {
                com.venera.desktop.business.DesktopComicPreferencesPort.getInstance(it)
            }
            val settingsReaderPrefs = galleryPaths?.let {
                com.venera.desktop.business.DesktopReaderPreferencesPort.getInstance(it)
            }
            DesktopSettingsHub(
                mode = layoutMode,
                prefs = galleryPrefs,
                guard = galleryGuard,
                readerPrefs = readerPrefs,
                paths = galleryPaths,
                comicPrefs = settingsComicPrefs,
                businessReaderPrefs = settingsReaderPrefs,
                networkPrefs = galleryPaths?.let {
                    com.venera.desktop.business.DesktopNetworkPreferencesPort(it)
                },
                galleryHandle = galleryPaths?.let(DesktopGalleryHandle::of),
                galleryCreds = galleryCreds,
                gelbooruState = galleryCreds?.gelbooru,
                pixivAccount = ports.pixivAccount as? DesktopPixivAccount,
                pixivWorks = ports.pixiv,
                wallDensity = wallDensity,
                onPickWallDensity = onPickWallDensity,
                onOpenPixivAccount = { onRouteChange(DesktopRoute.PixivAccount) },
                onOpenPixivLogin = { onRouteChange(DesktopRoute.PixivLogin) },
                onOpenBlockingRules = { type -> onRouteChange(DesktopRoute.BlockingRules(type)) },
                onOpenSourceManage = { onRouteChange(DesktopRoute.SourceManage) },
                onOpenBackup = { onRouteChange(DesktopRoute.Backup) },
                onOpenHistory = { onHistoryTickIncrement(); onRouteChange(DesktopRoute.History) },
                onOpenStats = { onRouteChange(DesktopRoute.ReadingStats) },
                onClose = { onRouteChange(routeBeforeSettings) },
            )
        } else {
            Text(
                "无法打开设置",
                color = VeneraTokens.color.textSecondary,
            )
        }
    }
}
