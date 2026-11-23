package com.venera.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import java.awt.event.KeyEvent as AwtKeyEvent

/**
 * 跨 Compose 树与 AWT 兜底层的统一按键信号。
 *
 * @property keyCode AWT 虚拟键码（`java.awt.event.KeyEvent.VK_*`），用于离线单测与跨层对齐。
 * @property isCtrlOrMeta 是否按下 Ctrl（Windows/Linux）或 Meta（macOS）。
 * @property isShift 是否按下 Shift。
 * @property isAlt 是否按下 Alt。
 */
data class DesktopShortcutSignal(
    val keyCode: Int,
    val isCtrlOrMeta: Boolean = false,
    val isShift: Boolean = false,
    val isAlt: Boolean = false,
)

/** 全局导航快捷键动作。 */
enum class DesktopGlobalNavAction {
    ESCAPE,
    FOCUS_SEARCH,
    TOGGLE_FULLSCREEN,
    TAB_1,
    TAB_2,
    TAB_3,
    TAB_4,
    TAB_5,
}

/** 列表与多选快捷键动作。 */
enum class DesktopListShortcutAction {
    SELECT_ALL,
    DELETE_SELECTED,
    OPEN_SELECTED,
}

/** 阅读器与大图扩展快捷键动作（缩放/适应/旋转）。 */
enum class DesktopViewerExtraAction {
    ZOOM_IN,
    ZOOM_OUT,
    ZOOM_RESET,
    FIT_SCREEN,
    ROTATE_OR_MODE,
}

/**
 * 将 Compose [KeyEvent] 转为 [DesktopShortcutSignal]；非按下事件返回 `null`。
 *
 * @param event Compose 键盘事件。
 */
fun KeyEvent.toShortcutSignalOrNull(): DesktopShortcutSignal? {
    if (type != KeyEventType.KeyDown) return null
    val vk = composeKeyToAwtVk(key) ?: return null
    return DesktopShortcutSignal(
        keyCode = vk,
        isCtrlOrMeta = isCtrlPressed || isMetaPressed,
        isShift = isShiftPressed,
        isAlt = isAltPressed,
    )
}

/**
 * 将 AWT [AwtKeyEvent] 转为 [DesktopShortcutSignal]；非 `KEY_PRESSED` 返回 `null`。
 *
 * @param event AWT 键盘事件。
 */
fun AwtKeyEvent.toShortcutSignalOrNull(): DesktopShortcutSignal? = awtKeyEventToShortcutSignal(this)

/**
 * 将 AWT [AwtKeyEvent] 转为 [DesktopShortcutSignal]；非 `KEY_PRESSED` 返回 `null`。
 *
 * @param event AWT 键盘事件。
 */
fun awtKeyEventToShortcutSignal(event: AwtKeyEvent): DesktopShortcutSignal? {
    if (event.id != AwtKeyEvent.KEY_PRESSED) return null
    return DesktopShortcutSignal(
        keyCode = event.keyCode,
        isCtrlOrMeta = event.isControlDown || event.isMetaDown,
        isShift = event.isShiftDown,
        isAlt = event.isAltDown,
    )
}

/**
 * 判定全局导航快捷键（Esc / Alt+← / Ctrl+F / F11 / Ctrl+1~5）。
 *
 * @param signal 标准化按键信号。
 */
fun matchGlobalNavShortcut(signal: DesktopShortcutSignal): DesktopGlobalNavAction? = when {
    signal.keyCode == AwtKeyEvent.VK_ESCAPE && !signal.isCtrlOrMeta && !signal.isAlt ->
        DesktopGlobalNavAction.ESCAPE
    signal.keyCode == AwtKeyEvent.VK_LEFT && signal.isAlt && !signal.isCtrlOrMeta ->
        DesktopGlobalNavAction.ESCAPE
    signal.keyCode == AwtKeyEvent.VK_F && signal.isCtrlOrMeta && !signal.isAlt ->
        DesktopGlobalNavAction.FOCUS_SEARCH
    signal.keyCode == AwtKeyEvent.VK_F11 && !signal.isCtrlOrMeta && !signal.isAlt ->
        DesktopGlobalNavAction.TOGGLE_FULLSCREEN
    signal.isCtrlOrMeta && !signal.isAlt && !signal.isShift -> when (signal.keyCode) {
        AwtKeyEvent.VK_1 -> DesktopGlobalNavAction.TAB_1
        AwtKeyEvent.VK_2 -> DesktopGlobalNavAction.TAB_2
        AwtKeyEvent.VK_3 -> DesktopGlobalNavAction.TAB_3
        AwtKeyEvent.VK_4 -> DesktopGlobalNavAction.TAB_4
        AwtKeyEvent.VK_5 -> DesktopGlobalNavAction.TAB_5
        else -> null
    }
    else -> null
}

/**
 * 判定列表与多选快捷键（Ctrl+A / Delete / Enter）。
 *
 * @param signal 标准化按键信号。
 */
fun matchListShortcut(signal: DesktopShortcutSignal): DesktopListShortcutAction? = when {
    signal.keyCode == AwtKeyEvent.VK_A && signal.isCtrlOrMeta && !signal.isAlt && !signal.isShift ->
        DesktopListShortcutAction.SELECT_ALL
    (signal.keyCode == AwtKeyEvent.VK_DELETE || signal.keyCode == AwtKeyEvent.VK_BACK_SPACE) &&
        !signal.isCtrlOrMeta && !signal.isAlt ->
        DesktopListShortcutAction.DELETE_SELECTED
    signal.keyCode == AwtKeyEvent.VK_ENTER && !signal.isCtrlOrMeta && !signal.isAlt ->
        DesktopListShortcutAction.OPEN_SELECTED
    else -> null
}

/**
 * 判定阅读器与大图扩展快捷键（+/-/0/F/R）。
 *
 * @param signal 标准化按键信号。
 */
fun matchViewerExtraShortcut(signal: DesktopShortcutSignal): DesktopViewerExtraAction? = when {
    signal.isAlt -> null
    signal.keyCode == AwtKeyEvent.VK_EQUALS || signal.keyCode == AwtKeyEvent.VK_PLUS ||
        signal.keyCode == AwtKeyEvent.VK_ADD -> DesktopViewerExtraAction.ZOOM_IN
    signal.keyCode == AwtKeyEvent.VK_MINUS || signal.keyCode == AwtKeyEvent.VK_SUBTRACT ->
        DesktopViewerExtraAction.ZOOM_OUT
    signal.keyCode == AwtKeyEvent.VK_0 || signal.keyCode == AwtKeyEvent.VK_NUMPAD0 ->
        DesktopViewerExtraAction.ZOOM_RESET
    signal.keyCode == AwtKeyEvent.VK_F && !signal.isCtrlOrMeta ->
        DesktopViewerExtraAction.FIT_SCREEN
    signal.keyCode == AwtKeyEvent.VK_R && !signal.isCtrlOrMeta ->
        DesktopViewerExtraAction.ROTATE_OR_MODE
    else -> null
}

private fun composeKeyToAwtVk(key: Key): Int? = when (key) {
    Key.Escape -> AwtKeyEvent.VK_ESCAPE
    Key.Enter, Key.NumPadEnter -> AwtKeyEvent.VK_ENTER
    Key.Spacebar -> AwtKeyEvent.VK_SPACE
    Key.Delete -> AwtKeyEvent.VK_DELETE
    Key.Backspace -> AwtKeyEvent.VK_BACK_SPACE
    Key.DirectionLeft -> AwtKeyEvent.VK_LEFT
    Key.DirectionRight -> AwtKeyEvent.VK_RIGHT
    Key.DirectionUp -> AwtKeyEvent.VK_UP
    Key.DirectionDown -> AwtKeyEvent.VK_DOWN
    Key.PageUp -> AwtKeyEvent.VK_PAGE_UP
    Key.PageDown -> AwtKeyEvent.VK_PAGE_DOWN
    Key.MoveHome -> AwtKeyEvent.VK_HOME
    Key.MoveEnd -> AwtKeyEvent.VK_END
    Key.Comma -> AwtKeyEvent.VK_COMMA
    Key.Period -> AwtKeyEvent.VK_PERIOD
    Key.Plus, Key.Equals -> AwtKeyEvent.VK_EQUALS
    Key.NumPadAdd -> AwtKeyEvent.VK_ADD
    Key.Minus -> AwtKeyEvent.VK_MINUS
    Key.NumPadSubtract -> AwtKeyEvent.VK_SUBTRACT
    Key.Zero -> AwtKeyEvent.VK_0
    Key.NumPad0 -> AwtKeyEvent.VK_NUMPAD0
    Key.One -> AwtKeyEvent.VK_1
    Key.Two -> AwtKeyEvent.VK_2
    Key.Three -> AwtKeyEvent.VK_3
    Key.Four -> AwtKeyEvent.VK_4
    Key.Five -> AwtKeyEvent.VK_5
    Key.A -> AwtKeyEvent.VK_A
    Key.D -> AwtKeyEvent.VK_D
    Key.F -> AwtKeyEvent.VK_F
    Key.R -> AwtKeyEvent.VK_R
    Key.F11 -> AwtKeyEvent.VK_F11
    else -> null
}

/**
 * 实时修饰键状态（由窗口级 AWT 事件流同步），供无 KeyEvent 参数的点击回调读取 Shift 连选状态。
 */
object DesktopModifierState {
    @Volatile
    var isShiftDown: Boolean = false
}

/**
 * 计算 Shift 区间连选后的新选中集合；若锚点不在列表中则退化为单选切换。
 *
 * @param orderedKeys 当前可见列表的有序键集合。
 * @param anchorKey 上一次点击的锚点键。
 * @param targetKey 本次点击的目标键。
 * @param currentSelected 当前已选集合。
 */
fun <T> computeShiftRangeSelection(
    orderedKeys: List<T>,
    anchorKey: T?,
    targetKey: T,
    currentSelected: Set<T>,
): Set<T> {
    val anchorIdx = if (anchorKey != null) orderedKeys.indexOf(anchorKey) else -1
    val targetIdx = orderedKeys.indexOf(targetKey)
    if (anchorIdx < 0 || targetIdx < 0) {
        return if (targetKey in currentSelected) currentSelected - targetKey else currentSelected + targetKey
    }
    val range = if (anchorIdx <= targetIdx) anchorIdx..targetIdx else targetIdx..anchorIdx
    return currentSelected + orderedKeys.subList(range.first, range.last + 1)
}

/**
 * 窗口级快捷键总线：按注册顺序逆序（栈顶优先）分发未消费按键，解决 Compose 焦点丢失导致快捷键失灵的问题。
 */
class DesktopGlobalShortcutBus {
    private data class Entry(
        val id: Any,
        val priority: Int,
        val handler: (DesktopShortcutSignal) -> Boolean,
    )

    private val entries = mutableListOf<Entry>()

    /**
     * 注册快捷键处理器；同优先级后注册者优先。
     *
     * @param id 唯一标识，用于注销。
     * @param priority 优先级（数值越大越先收到，如阅读器 300 > 浮层 200 > 列表 100 > 主壳 0）。
     * @param handler 返回 `true` 表示已消费该按键。
     */
    fun register(id: Any, priority: Int, handler: (DesktopShortcutSignal) -> Boolean) {
        entries.removeAll { it.id == id }
        entries.add(Entry(id, priority, handler))
    }

    /**
     * 注销指定标识的处理器。
     *
     * @param id 注册时传入的标识。
     */
    fun unregister(id: Any) {
        entries.removeAll { it.id == id }
    }

    /**
     * 按优先级从高到低分发按键信号。
     *
     * @param signal 标准化按键信号。
     */
    fun dispatch(signal: DesktopShortcutSignal): Boolean {
        val byPriorityThenRecency = entries
            .withIndex()
            .sortedWith(compareByDescending<IndexedValue<Entry>> { it.value.priority }.thenByDescending { it.index })
        for (item in byPriorityThenRecency) {
            if (item.value.handler(signal)) return true
        }
        return false
    }
}

val LocalDesktopShortcutBus = staticCompositionLocalOf<DesktopGlobalShortcutBus?> { null }

/**
 * 在当前组合作用域内注册窗口级快捷键处理器，离开组合时自动注销。
 *
 * @param key 触发重新注册的依赖键。
 * @param priority 优先级（阅读器/大图 300，浮层 200，列表 100，主壳 0）。
 * @param handler 按键处理回调，返回 `true` 表示消费。
 */
@Composable
fun RegisterDesktopShortcutHandler(
    vararg key: Any?,
    priority: Int,
    handler: (DesktopShortcutSignal) -> Boolean,
) {
    val bus = LocalDesktopShortcutBus.current ?: return
    val token = androidx.compose.runtime.remember { Any() }
    val latestHandler = androidx.compose.runtime.rememberUpdatedState(handler)
    DisposableEffect(bus, priority, *key) {
        bus.register(token, priority) { signal -> latestHandler.value(signal) }
        onDispose { bus.unregister(token) }
    }
}
