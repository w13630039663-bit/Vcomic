package com.venera.desktop.installer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.runtime.withFrameNanos
import com.venera.desktop.gallery.data.DesktopDirChooser
import dev.nucleusframework.application.DecoratedWindow
import dev.nucleusframework.application.NucleusApplicationScope
import dev.nucleusframework.application.NucleusBackend
import dev.nucleusframework.application.nucleusApplication
import dev.nucleusframework.window.WindowAppearance
import dev.nucleusframework.window.WindowAppearanceMode
import dev.nucleusframework.window.WindowsBackdrop
import dev.nucleusframework.window.WindowsBackdropStyle
import dev.nucleusframework.window.WindowsBackdropTier
import dev.nucleusframework.window.windowDragArea
import kotlinx.coroutines.delay
import java.io.File

/** 安装向导的四个主态；扩展态（迁移 / 冲突 / 失败 / 维护）不在主链上。 */
enum class InstallerStage { SPLASH, OPTIONS, INSTALLING, COMPLETE }

private const val WINDOW_WIDTH_DP = 640
private const val BASE_HEIGHT_DP = 440

/**
 * 解析启动参数里的起始态；认不出时回落 [InstallerStage.SPLASH]，不退成崩溃。
 *
 * @param raw `-PinstallerPage` 透传的态名，大小写不敏感，空值按启动页处理。
 */
internal fun installerStageOf(raw: String?): InstallerStage =
    InstallerStage.entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) }
        ?: InstallerStage.SPLASH

/**
 * 帧循环里的自动推进：启动页到点进选项页，安装页走满进完成页。
 *
 * @param stage 当前态。
 * @param elapsedMs 当前态已过的毫秒数。
 */
internal fun installerAutoAdvance(stage: InstallerStage, elapsedMs: Long): InstallerStage = when {
    stage == InstallerStage.SPLASH && elapsedMs >= InstallerTimeline.SPLASH_TOTAL_MS -> InstallerStage.OPTIONS
    stage == InstallerStage.INSTALLING &&
        elapsedMs >= InstallerTimeline.INSTALL_RUN_MS + InstallerTimeline.INSTALL_HOLD_MS -> InstallerStage.COMPLETE
    else -> stage
}

/**
 * 默认安装目录：`%LOCALAPPDATA%\venera-desktop`；取不到环境变量时回落到用户目录下同名路径。
 */
internal fun installerDefaultPath(): String {
    val local = System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }
    val fallback = System.getProperty("user.home")?.takeIf { it.isNotBlank() }
        ?.let { File(it, "AppData\\Local").path }
    val root = local ?: fallback
    // 两处都取不到时退回文案里的提示值，保证输入框不空
    return if (root == null) InstallerCopy.DefaultPathHint else File(root, "venera-desktop").path
}

/**
 * 独立窗口入口：`./gradlew :desktop:installer [-PinstallerPage=SPLASH|OPTIONS|INSTALLING|COMPLETE] [-PinstallerTheme=dark]`。
 *
 * 宿主走 Nucleus：系统材质（Win11 Mica / Win10 亚克力）只能在 Nucleus 的非分层窗上合成，
 * 标准 Compose `Window` 是分层窗，DWM 材质不生效。
 *
 * @param args 第 0 项为起始态名，第 1 项为主题（`dark` 之外一律浅色）。
 */
fun main(args: Array<String>) {
    val config = parseInstallerLaunchArgs(args)
    val sb = StringBuilder()
    sb.append("D_安装向导 起窗 stage=").append(config.startStage.name)
    sb.append(" theme=").append(if (config.dark) "dark" else "light")
    sb.append(" source=").append(config.sourceDir?.path ?: "preview")
    sb.append(" os.build=").append(System.getProperty("os.version"))
    println(sb)
    InstallerEngine.log(sb.toString())
    try {
        nucleusApplication(args = args, backend = NucleusBackend.Tao) {
            InstallerHost(config = config)
        }
    } catch (t: Throwable) {
        InstallerEngine.logError("main", t)
        throw t
    }
}

/**
 * 向导宿主：单一时钟推进四态，窗口材质由 [WindowsBackdrop] 交给 Nucleus 分档落地。
 */
@Composable
private fun NucleusApplicationScope.InstallerHost(config: InstallerLaunchConfig) {
    val startStage = config.startStage
    val dark = config.dark
    val realMode = config.sourceDir != null

    var stage by remember { mutableStateOf(startStage) }
    var clockMs by remember { mutableStateOf(0L) }
    var stageStartMs by remember { mutableStateOf(0L) }
    var installPath by remember { mutableStateOf(installerDefaultPath()) }
    var panelHeightPx by remember { mutableStateOf(0) }
    var panelOpen by remember { mutableStateOf(false) }
    var cancelAsk by remember { mutableStateOf(false) }
    var realProgress by remember { mutableStateOf(0f) }
    var realStatus by remember { mutableStateOf<String?>(null) }
    var installError by remember { mutableStateOf<InstallErrorKind?>(null) }
    var installAttempt by remember { mutableStateOf(0) }
    var createDesktopShortcut by remember { mutableStateOf(true) }
    var createStartMenuShortcut by remember { mutableStateOf(true) }
    var showUninstallPane by remember { mutableStateOf(config.uninstallMode) }
    var uninstallTrigger by remember { mutableStateOf(0) }
    val upgrade = remember(installPath) {
        File(installPath, "venera-desktop.exe").isFile || File(installPath, "unins000.exe").isFile
    }
    val windowState = rememberWindowState(
        size = DpSize(WINDOW_WIDTH_DP.dp, BASE_HEIGHT_DP.dp),
        position = WindowPosition(Alignment.Center),
    )
    val density = LocalDensity.current
    val panelDp = with(density) { panelHeightPx.toDp() }

    // 目录面板展开时窗口跟着长；卡片高度由窗口反推，不能反过来（会量到固定 440 而裁切面板）
    LaunchedEffect(panelDp) {
        windowState.size = DpSize(WINDOW_WIDTH_DP.dp, BASE_HEIGHT_DP.dp + panelDp)
    }
    // 折叠动画 300ms 期间保持旧高度，收完再回落，避免面板还在收就被窗口切掉
    LaunchedEffect(panelOpen) {
        if (!panelOpen) {
            delay(320)
            panelHeightPx = 0
        }
    }
    // 单一时钟：全局相位供背景与插画，页内进度由 stageStartMs 换算；真实安装模式不由时间强跳完成页
    LaunchedEffect(realMode) {
        val baseNanos = withFrameNanos { it }
        while (true) {
            clockMs = (withFrameNanos { it } - baseNanos) / 1_000_000L
            val next = installerAutoAdvance(stage, clockMs - stageStartMs)
            if (next != stage && !(realMode && stage == InstallerStage.INSTALLING)) {
                stage = next
                stageStartMs = clockMs
            }
        }
    }
    // 真实安装执行器：仅在带有 --source 时触发文件复制、快捷方式与卸载登记
    LaunchedEffect(stage, installAttempt, realMode) {
        val src = config.sourceDir ?: return@LaunchedEffect
        if (stage != InstallerStage.INSTALLING || installError != null) return@LaunchedEffect
        realProgress = 0f
        realStatus = "正在准备安装…"
        try {
            InstallerEngine.performInstall(
                sourceDir = src,
                targetDir = File(installPath),
                version = config.appVersion,
                createDesktopShortcut = createDesktopShortcut,
                createStartMenuShortcut = createStartMenuShortcut,
                onProgress = { prog ->
                    realProgress = prog.fraction
                    realStatus = prog.statusText
                },
            )
            delay(InstallerTimeline.INSTALL_HOLD_MS)
            stage = InstallerStage.COMPLETE
            stageStartMs = clockMs
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: InstallFailureException) {
            installError = e.kind
        } catch (t: Throwable) {
            InstallerEngine.logError("install-coro", t)
            installError = InstallErrorKind.FILE_LOCKED
        }
    }
    LaunchedEffect(uninstallTrigger) {
        if (uninstallTrigger <= 0) return@LaunchedEffect
        InstallerEngine.performUninstall(File(installPath))
        exitApplication()
    }

    DecoratedWindow(
        onCloseRequest = {
            if (stage == InstallerStage.INSTALLING) cancelAsk = true else exitApplication()
        },
        state = windowState,
        title = InstallerCopy.AppTitle,
        icon = painterResource("vcomic_icon.png"),
        resizable = false,
    ) {
        // 窗口外观跟随向导主题：系统是深色时云母也是深色，和浅色内容叠在一起会整窗发灰
        WindowAppearance(if (dark) WindowAppearanceMode.Dark else WindowAppearanceMode.Light)
        // 系统材质：Win11 走 Mica，Win10 由 Nucleus 的 Auto 档降级到亚克力
        WindowsBackdrop(WindowsBackdropStyle.Mica, tier = WindowsBackdropTier.Auto)
        val requestClose = {
            if (stage == InstallerStage.INSTALLING) cancelAsk = true else exitApplication()
        }
        Box(Modifier.fillMaxSize()) {
            InstallerCard(isDark = dark) {
                AmbientBackdrop(clockMs, Modifier.fillMaxSize())
                Column(Modifier.fillMaxSize()) {
                    InstallerTitleBar(
                        alpha = if (stage == InstallerStage.SPLASH) {
                            InstallerTimeline.chromeProgress(clockMs)
                        } else {
                            1f
                        },
                        onMinimize = { nucleusWindow.setMinimized(true) },
                        onClose = requestClose,
                        modifier = Modifier.windowDragArea(enabled = true),
                    )
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        if (showUninstallPane) {
                            UninstallScene(
                                onBack = { showUninstallPane = false },
                                onRepair = {
                                    showUninstallPane = false
                                    stage = InstallerStage.INSTALLING
                                    stageStartMs = clockMs
                                },
                                onUninstall = { uninstallTrigger++ },
                            )
                        } else {
                            when (stage) {
                                InstallerStage.SPLASH -> SplashScene(
                                    introMs = clockMs,
                                    applyIntro = startStage == InstallerStage.SPLASH,
                                )

                                InstallerStage.OPTIONS -> OptionsScene(
                                    installPath = installPath,
                                    onInstallPathChange = { installPath = it },
                                    isUpgrade = upgrade,
                                    onBrowse = {
                                        val picked = DesktopDirChooser.pickDirectory("选择安装目录", installPath)
                                        if (picked == null) {
                                            println("D_安装向导 目录框取消或不可用，保留原路径")
                                        } else {
                                            installPath = picked
                                        }
                                    },
                                    onStartInstallWithOptions = { desk, menu ->
                                        createDesktopShortcut = desk
                                        createStartMenuShortcut = menu
                                        stage = InstallerStage.INSTALLING
                                        stageStartMs = clockMs
                                    },
                                    onRequestUninstall = { showUninstallPane = true },
                                    onPathPanelHeight = { panelHeightPx = it },
                                    onPathPanelExpandedChange = { panelOpen = it },
                                )

                                InstallerStage.INSTALLING -> {
                                    val err = installError
                                    if (err != null) {
                                        ErrorScene(
                                            errorKind = err,
                                            onChangePath = {
                                                installError = null
                                                stage = InstallerStage.OPTIONS
                                                stageStartMs = clockMs
                                            },
                                            onRetry = {
                                                installError = null
                                                installAttempt++
                                            },
                                        )
                                    } else {
                                        InstallScene(
                                            pageElapsed = clockMs - stageStartMs,
                                            clockMs = clockMs,
                                            progressOverride = if (realMode) realProgress else null,
                                            statusTextOverride = if (realMode) realStatus else null,
                                        )
                                    }
                                }

                                InstallerStage.COMPLETE -> CompleteScene(
                                    clockMs = clockMs,
                                    installPath = installPath,
                                    onExperienceWithLaunch = { launchApp ->
                                        if (realMode && launchApp) {
                                            InstallerEngine.launchInstalledApp(File(installPath))
                                        }
                                        exitApplication()
                                    },
                                )
                            }
                        }
                    }
                }
                CancelConfirmOverlay(
                    visible = cancelAsk,
                    onKeepInstall = { cancelAsk = false },
                    onConfirmCancel = {
                        cancelAsk = false
                        stage = InstallerStage.OPTIONS
                        stageStartMs = clockMs
                    },
                )
            }
        }
    }
}
