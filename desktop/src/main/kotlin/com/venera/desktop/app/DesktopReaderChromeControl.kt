package com.venera.desktop.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * 阅读器底栏向宿主壳申请切换沉浸模式的局部挂手。
 *
 * 非阅读器上下文或单测未注入时为 `null`，底栏可选择不显示或使用外部显式回调。
 */
internal data class DesktopReaderChromeController(
    val immersive: Boolean,
    val onToggle: () -> Unit,
)

internal val LocalReaderChromeController = compositionLocalOf<DesktopReaderChromeController?> { null }

/** 判定当前内容路由是否为漫画阅读器（含在线/已下载与本地导入直读）。 */
internal fun isComicReaderRoute(route: DesktopRoute): Boolean =
    route is DesktopRoute.Reader || route is DesktopRoute.LocalComicReader

/** 计算当前路由下外壳侧栏与顶栏的快照配置。 */
internal data class DesktopReaderChromeSnapshot(
    val inComicReader: Boolean,
    val fullscreenChrome: Boolean,
    val effectiveRailExpanded: Boolean,
)

internal fun resolveDesktopReaderChromeSnapshot(
    contentRoute: DesktopRoute,
    savedRailExpanded: Boolean,
    readerRailExpanded: Boolean,
    readerImmersive: Boolean,
): DesktopReaderChromeSnapshot {
    val inComicReader = isComicReaderRoute(contentRoute)
    val fullscreenChrome =
        contentRoute is DesktopRoute.GalleryPostViewer || (inComicReader && readerImmersive)
    val effectiveRailExpanded = if (inComicReader) readerRailExpanded else savedRailExpanded
    return DesktopReaderChromeSnapshot(
        inComicReader = inComicReader,
        fullscreenChrome = fullscreenChrome,
        effectiveRailExpanded = effectiveRailExpanded,
    )
}

/**
 * 阅读器外壳显隐与侧栏临时收起状态机（供逻辑复用与单测直接校验）。
 *
 * 进入阅读器时自动收起侧栏、退出沉浸；在阅读器内部切章节或打开设置叠层期间保持状态；
 * 退出阅读器后立即还原进入前的侧栏展开态并重置沉浸标志，且绝不污染持久化偏好。
 */
internal class DesktopReaderChromeStateMachine(
    savedRailExpanded: Boolean,
    initialRoute: DesktopRoute,
) {
    var savedRailExpanded: Boolean = savedRailExpanded
        private set
    var inComicReader: Boolean = isComicReaderRoute(initialRoute)
        private set
    var readerRailExpanded: Boolean = false
        private set
    var readerImmersive: Boolean = false
        private set
    var lastRoute: DesktopRoute = initialRoute
        private set

    fun syncRoute(contentRoute: DesktopRoute) {
        lastRoute = contentRoute
        val nowInReader = isComicReaderRoute(contentRoute)
        if (nowInReader != inComicReader) {
            inComicReader = nowInReader
            readerRailExpanded = false
            readerImmersive = false
        }
    }

    fun onRailExpandedChange(expanded: Boolean, persistSaved: (Boolean) -> Unit) {
        if (inComicReader) {
            readerRailExpanded = expanded
        } else {
            savedRailExpanded = expanded
            persistSaved(expanded)
        }
    }

    fun toggleImmersive() {
        if (inComicReader) {
            readerImmersive = !readerImmersive
        }
    }

    fun snapshot(): DesktopReaderChromeSnapshot = resolveDesktopReaderChromeSnapshot(
        contentRoute = lastRoute,
        savedRailExpanded = savedRailExpanded,
        readerRailExpanded = readerRailExpanded,
        readerImmersive = readerImmersive,
    )
}

internal data class DesktopReaderShellChromePolicy(
    val inComicReader: Boolean,
    val fullscreenChrome: Boolean,
    val effectiveRailExpanded: Boolean,
    val onRailExpandedChange: (Boolean) -> Unit,
    val chromeController: DesktopReaderChromeController?,
)

/**
 * 在大屏壳初始化阶段统筹阅读器的沉浸态与侧栏临时收起/还原。
 */
@Composable
internal fun rememberDesktopReaderShellChromePolicy(
    contentRoute: DesktopRoute,
    savedRailExpanded: Boolean,
    onSaveRailExpanded: (Boolean) -> Unit,
): DesktopReaderShellChromePolicy {
    val inComicReader = isComicReaderRoute(contentRoute)
    // 以是否身处阅读器作为 remember key：进出边沿同帧复位，切话与开设置期间稳态保持。
    var readerRailExpanded by remember(inComicReader) { mutableStateOf(false) }
    var readerImmersive by remember(inComicReader) { mutableStateOf(false) }

    val snapshot = resolveDesktopReaderChromeSnapshot(
        contentRoute = contentRoute,
        savedRailExpanded = savedRailExpanded,
        readerRailExpanded = readerRailExpanded,
        readerImmersive = readerImmersive,
    )

    return DesktopReaderShellChromePolicy(
        inComicReader = snapshot.inComicReader,
        fullscreenChrome = snapshot.fullscreenChrome,
        effectiveRailExpanded = snapshot.effectiveRailExpanded,
        onRailExpandedChange = { expanded ->
            if (inComicReader) {
                readerRailExpanded = expanded
            } else {
                onSaveRailExpanded(expanded)
            }
        },
        chromeController = if (inComicReader) {
            DesktopReaderChromeController(
                immersive = readerImmersive,
                onToggle = { readerImmersive = !readerImmersive },
            )
        } else {
            null
        },
    )
}
