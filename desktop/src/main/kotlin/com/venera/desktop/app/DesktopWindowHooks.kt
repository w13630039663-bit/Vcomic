package com.venera.desktop.app

import com.venera.desktop.data.DesktopErrorBus
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.rememberWindowState
import com.venera.compose.data.db.DatabasePorts
import com.venera.compose.data.platform.JsonKeyValueStore
import com.venera.compose.data.platform.PathProvider
import com.venera.desktop.gallery.data.DesktopGalleryContentGuard
import com.venera.desktop.gallery.data.DesktopImagePipeline
import com.venera.desktop.gallery.ui.DesktopGalleryMetrics
import com.venera.desktop.platform.DesktopSingleInstance
import com.venera.desktop.platform.DesktopSystemTray
import com.venera.desktop.platform.migrateLegacyFavorites
import com.venera.desktop.ui.DesktopEffects
import com.venera.desktop.ui.DesktopGlobalShortcutBus
import com.venera.desktop.ui.DesktopModifierState
import com.venera.desktop.ui.awtKeyEventToShortcutSignal
import com.venera.desktop.ui.components.DesktopPetCue
import com.venera.desktop.ui.components.DesktopPetCueBus
import dev.nucleusframework.application.NucleusDecoratedWindowScope
import dev.nucleusframework.window.tao.rememberTaoGpuRenderContext
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.KeyboardFocusManager
import java.awt.KeyEventDispatcher
import java.awt.KeyEventPostProcessor
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class DesktopWindowGeometryConfig(
    val windowStore: JsonKeyValueStore,
    val rememberWindow: Boolean,
    val savedMax: Boolean,
    val savedX: Int,
    val savedDensity: Float,
    val windowState: WindowState,
)

/**
 * 读取窗口几何记忆与特效总开关起步档，并构造钳制后的初始 [WindowState]。
 */
@Composable
internal fun rememberDesktopWindowGeometryConfig(paths: PathProvider): DesktopWindowGeometryConfig {
    val windowSize = remember {
        val screen = Toolkit.getDefaultToolkit().screenSize
        DesktopGalleryMetrics.windowSizeFor(screen.width, screen.height)
    }
    val windowStore = remember { JsonKeyValueStore("venera_desktop_window", paths) }
    remember {
        DesktopEffects.reduce = windowStore.getBoolean("pref_desktop_effects_reduce", false)
    }
    val rememberWindow = windowStore.getBoolean("pref_desktop_window_remember", true)
    val savedMax = windowStore.getBoolean("window_maximized", false)
    val savedW = windowStore.getInt("window_w", windowSize.width.value.toInt())
    val savedH = windowStore.getInt("window_h", windowSize.height.value.toInt())
    val savedX = windowStore.getInt("window_x", Int.MIN_VALUE)
    val savedY = windowStore.getInt("window_y", Int.MIN_VALUE)
    val savedDensity = windowStore.getFloat("window_density", 0f)
    val savedOnScreen = remember(savedX, savedY, savedDensity) {
        if (savedX == Int.MIN_VALUE || savedY == Int.MIN_VALUE) {
            false
        } else {
            runCatching {
                val k = if (savedDensity > 0f) savedDensity else 1f
                GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.any { dev ->
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
            WindowPosition(savedX.dp, savedY.dp)
        } else {
            WindowPosition.PlatformDefault
        },
    )
    return DesktopWindowGeometryConfig(
        windowStore = windowStore,
        rememberWindow = rememberWindow,
        savedMax = savedMax,
        savedX = savedX,
        savedDensity = savedDensity,
        windowState = windowState,
    )
}

/**
 * 挂载窗口级副作用：最小尺寸锁定、几何记忆防抖持久化、旧版收藏一次性迁移及单实例销毁。
 */
@Composable
internal fun NucleusDecoratedWindowScope.InstallDesktopWindowLifecycleEffects(
    geometry: DesktopWindowGeometryConfig,
    pixivCallbackId: Long?,
    launchListener: AutoCloseable?,
    singleInstance: DesktopSingleInstance?,
) {
    val winDensity = LocalDensity.current
    val windowState = geometry.windowState

    LaunchedEffect(winDensity) {
        nucleusWindow.setMinimumSize(DpSize(1120.dp, 720.dp))
        val minWpx = (1120f * winDensity.density).toInt()
        val minHpx = (720f * winDensity.density).toInt()
        Window.getWindows().forEach { w -> w.minimumSize = Dimension(minWpx, minHpx) }
        DesktopWindowMinSizeHook.installOrUpdate(
            title = APP_TITLE,
            minWidthPx = minWpx,
            minHeightPx = minHpx,
        )
    }

    LaunchedEffect(windowState) {
        val minW = 1120.dp
        val minH = 720.dp
        snapshotFlow { windowState.size }.collect { size ->
            if (size.width.value <= 0f || size.height.value <= 0f) return@collect
            val fixed = DpSize(maxOf(size.width, minW), maxOf(size.height, minH))
            if (fixed != size) windowState.size = fixed
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            runCatching {
                val moved = migrateLegacyFavorites(DatabasePorts.of(null))
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

    LaunchedEffect(Unit) {
        if (geometry.rememberWindow && geometry.savedMax) nucleusWindow.setMaximized(true)
    }

    LaunchedEffect(winDensity) {
        if (geometry.rememberWindow && geometry.savedDensity > 0f &&
            geometry.savedX != Int.MIN_VALUE && geometry.savedDensity != winDensity.density
        ) {
            val k = geometry.savedDensity / winDensity.density
            val savedY = geometry.windowStore.getInt("window_y", Int.MIN_VALUE)
            windowState.position = WindowPosition(geometry.savedX.dp * k, savedY.dp * k)
        }
    }

    if (geometry.rememberWindow) {
        LaunchedEffect(windowState) {
            snapshotFlow { Pair(windowState.size, windowState.position) }.collectLatest {
                delay(800)
                val maximized = nucleusWindow.isMaximized
                val minimized = nucleusWindow.isMinimized
                val posX = windowState.position.x.value.toInt()
                val posY = windowState.position.y.value.toInt()
                geometry.windowStore.put("window_maximized", maximized)
                if (!maximized && !minimized && posX > -10000 && posY > -10000) {
                    geometry.windowStore.put("window_w", windowState.size.width.value.toInt())
                    geometry.windowStore.put("window_h", windowState.size.height.value.toInt())
                    geometry.windowStore.put("window_x", posX)
                    geometry.windowStore.put("window_y", posY)
                    geometry.windowStore.put("window_density", winDensity.density)
                }
            }
        }
    }

    LaunchedEffect(pixivCallbackId) {
        if (pixivCallbackId != null) {
            runCatching { nucleusWindow.toFront() }
        }
    }

    DisposableEffect(launchListener, singleInstance) {
        onDispose {
            launchListener?.close()
            singleInstance?.close()
        }
    }
}

/**
 * 接管 Tao Skia GPU 资源上限并注册失焦空闲内存/纹理回收钩子，返回手动清理闭包供局部页面退出时调用。
 */
@Composable
internal fun NucleusDecoratedWindowScope.rememberDesktopGpuCachePurgeAndIdleReclaim(
    windowState: WindowState,
    maximized: Boolean,
): (String) -> Unit {
    val gpuRenderContext = rememberTaoGpuRenderContext()
    val targetGpuLimitBytes = remember {
        DesktopGpuCacheGuard.parseLimitBytes(System.getProperty("skiko.gpu.resourceCacheLimit"))
    }
    val idleReclaimScope = rememberCoroutineScope()
    var largeEvictGcToken by remember { mutableStateOf(0L) }

    val purgeGpuCache: (String) -> Unit = remember(gpuRenderContext, targetGpuLimitBytes, idleReclaimScope) {
        { reason ->
            val freedImageBytes = DesktopImagePipeline.evictLargeMemoryCache(reason)
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
            if (reason != "window_idle" && freedImageBytes >= 4L * 1024L * 1024L) {
                val token = ++largeEvictGcToken
                idleReclaimScope.launch(Dispatchers.Default) {
                    delay(260)
                    if (largeEvictGcToken == token) System.gc()
                }
            }
        }
    }

    LaunchedEffect(gpuRenderContext, windowState.size, maximized) {
        if (gpuRenderContext == null) {
            println("D_GPU缓存 跳过接管 因=TaoGpuRenderContext为空")
            return@LaunchedEffect
        }
        delay(150)
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

    return purgeGpuCache
}

/**
 * 安装系统托盘菜单与退出清理回调。
 */
@Composable
internal fun NucleusDecoratedWindowScope.InstallDesktopSystemTrayEffect(
    guard: DesktopGalleryContentGuard,
    onExitApplication: () -> Unit,
) {
    DisposableEffect(Unit) {
        DesktopSystemTray.install(
            onShowWindow = {
                runCatching {
                    nucleusWindow.setMinimized(false)
                    nucleusWindow.toFront()
                    nucleusWindow.requestFocus()
                }
            },
            onExit = {
                DesktopSystemTray.uninstall()
                closePersistenceInOrder()
                onExitApplication()
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
        onDispose { DesktopSystemTray.uninstall() }
    }
}

/**
 * 监听 AWT 窗口焦点并向全局快捷键总线 [shortcutBus] 派发按键信号。
 */
@Composable
internal fun NucleusDecoratedWindowScope.InstallDesktopShortcutsEffect(
    shortcutBus: DesktopGlobalShortcutBus,
    isWindowFocused: Boolean,
) {
    val latestWindowFocused by rememberUpdatedState(isWindowFocused)
    var awtWindowFocused by remember { mutableStateOf(true) }
    DisposableEffect(Unit) {
        val w = Window.getWindows().firstOrNull { it.isShowing }
        val l = object : WindowFocusListener {
            override fun windowGainedFocus(e: WindowEvent) { awtWindowFocused = true }
            override fun windowLostFocus(e: WindowEvent) { awtWindowFocused = false }
        }
        w?.addWindowFocusListener(l)
        onDispose { w?.removeWindowFocusListener(l) }
    }
    val latestAwtFocused by rememberUpdatedState(awtWindowFocused)
    DisposableEffect(shortcutBus) {
        val kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager()
        val modifierTracker = KeyEventDispatcher { awtEvent ->
            DesktopModifierState.isShiftDown = awtEvent.isShiftDown
            false
        }
        val postProcessor = KeyEventPostProcessor { awtEvent ->
            if (!(latestWindowFocused || latestAwtFocused) || awtEvent.isConsumed) return@KeyEventPostProcessor false
            val signal = awtKeyEventToShortcutSignal(awtEvent) ?: return@KeyEventPostProcessor false
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
}
