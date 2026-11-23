package com.venera.desktop.ui.components

import com.venera.desktop.platform.DesktopErrorKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * 侧栏角色位状态档位（8 组 37 态 +批新增）。
 *
 * [priority] 越大越优先（仅用于帧与角标选档，不再参与时间拦截）；
 * [holdMs] 为兼容既有枚举签名与判据保留，**不再驱动自动回退**（
 * 「把时间间隔去掉」）。每个事件直接上屏、台词常驻到下一个事件。
 */
enum class DesktopPetCue(val priority: Int, val holdMs: Long) {
    // P8 异常
    ERROR(8, 5_000),
    SCRIPT_ERROR(8, 5_000),
    PARSE_FAILED(8, 5_000),
    BLOCKED(8, 5_000),
    OFFLINE(8, 5_000),
    LOAD_FAILED(8, 5_000),
    // P7 等待 / 警示
    WARNING(7, 4_000),
    MASKED(7, 4_000),
    WAITING_APPROVAL(7, 4_000),
    // P6 完成
    LOAD_DONE(6, 3_500),
    DOWNLOAD_ALL_DONE(6, 3_500),
    CHAPTER_END(6, 3_500),
    // P5 进行中
    LOADING(5, 3_500),
    DOWNLOADING(5, 3_500),
    RETRYING(5, 3_500),
    // P4 浏览 / 整理 / 互动
    TAP_PET(4, 3_500),
    OPEN_WORK(4, 3_500),
    OPEN_IMAGE(4, 3_500),
    TOGGLE_FAVORITE(4, 3_500),
    SWITCH_SOURCE(4, 3_500),
    SWITCH_PAGE(4, 3_500),
    SEARCH(4, 3_500),
    FILTER(4, 3_500),
    OPEN_CHAPTER(4, 3_500),
    CONTINUE_READING(4, 3_500),
    TURN_PAGE(4, 3_500),
    ADD_BOOKSHELF(4, 3_500),
    MARK_READ(4, 3_500),
    REMOVE_FAVORITE(4, 3_500),
    LONG_PRESS(4, 3_500),
    HOVER(4, 3_500),
    // P1 待机
    IDLE(1, 0),
    NO_TASK(1, 0),
    FIRST_LAUNCH(1, 3_500),
    BACK_HOME(1, 3_500),
    // P0 打盹
    AWAY(0, 0),
    DOZING(0, 0),
    // ── ：截图三组（空 / 没结果、陪伴 / 时间、小反应）──
    // P4 空 / 没结果
    SEARCH_EMPTY(4, 0),
    COLLECTION_EMPTY(4, 0),
    LIST_END(4, 0),
    NETWORK_RESTORED(4, 0),
    // P4 陪伴 / 时间（不编造「上次是周几」，没持久化就不说具体日期）
    LONG_TIME_NO_SEE(4, 0),
    LATE_NIGHT(4, 0),
    WINDOW_RETURN(4, 0),
    // P4 小反应
    TAP_AGAIN(4, 0),
    QUIET_ON(4, 0),
}

/** 元信息载荷：只在内存里过一次，不落日志、不持久化。 */
data class DesktopPetPayload(
    val title: String = "",
    val author: String = "",
    val tag: String = "",
    val source: String = "",
    val chapter: String = "",
    val masked: Boolean = false,
    val multiImage: Boolean = false,
)

/** 一次上屏事件；[serial] 保证同一档位连发也能让订阅方重新执行。 */
data class DesktopPetEvent(
    val cue: DesktopPetCue,
    val payload: DesktopPetPayload?,
    val atMs: Long,
    val serial: Long,
)

/**
 * 角色位总线 = 状态快照 + 免打扰。
 *
 * **去掉全部时间节制**。原「30 秒冷却 / 每小时 6 条预算 / 停留自动回退」
 * 会把连续操作吞成「点了没反应」（下载、收藏尤其明显），整条撤掉：
 *   - 每次 emit 立即上屏，同档连发也重新执行（靠 [DesktopPetEvent.serial]）；
 *   - 台词常驻到下一个事件，不再按 [DesktopPetCue.holdMs] 自动清；
 *   - 优先级只用于帧与角标选档，不再拦截低档事件。
 * 唯一保留的闸 = 免打扰（开启后只放行 P7/P8 与用户主动找角色）。
 *
 * 与 `DesktopErrorBus` 同形：任意线程可 emit，Compose 侧只订阅。
 */
object DesktopPetCueBus {
    private val counter = AtomicLong()
    private val _current = MutableStateFlow<DesktopPetEvent?>(null)
    val current: StateFlow<DesktopPetEvent?> = _current.asStateFlow()

    private val _quietMode = MutableStateFlow(false)
    val quietMode: StateFlow<Boolean> = _quietMode.asStateFlow()
    fun setQuietMode(value: Boolean) {
        _quietMode.value = value
    }

    fun toggleQuietMode() {
        _quietMode.value = !_quietMode.value
    }

    /** 用户主动找角色（点 / 长按 / 悬停）与异常 / 警示：免打扰下仍放行。 */
    private val userCues = setOf(
        DesktopPetCue.TAP_PET, DesktopPetCue.LONG_PRESS, DesktopPetCue.HOVER,
        DesktopPetCue.TAP_AGAIN, DesktopPetCue.QUIET_ON,
    )

    @Synchronized
    fun emit(cue: DesktopPetCue, payload: DesktopPetPayload? = null) {
        val unrestricted = cue.priority >= 7 || cue in userCues
        if (_quietMode.value && !unrestricted) return
        _current.value = DesktopPetEvent(cue, payload, System.currentTimeMillis(), counter.incrementAndGet())
    }

    /** 一次性状态播完由订阅方调用，回到待机。当次不再自动调用，保留供外部显式收口。 */
    fun clear() {
        _current.value = null
    }
}

/**
 * 桌面错误档位 → 角色事件。分类口径沿用 `desktopErrorColor` 既有的「警示黄 / 危险红」：
 * 偏慢、过频、登录失效、未登录属可恢复提醒 ⇒ WARNING；
 * 断网、网络受阻、源受限、源服务异常、通用失败属明确取不到内容 ⇒ ERROR 组。
 */
fun petCueForError(kind: DesktopErrorKind): DesktopPetCue = when (kind) {
    DesktopErrorKind.TIMEOUT,
    DesktopErrorKind.RATE_LIMITED,
    DesktopErrorKind.AUTH_EXPIRED,
    DesktopErrorKind.NOT_LOGGED_IN -> DesktopPetCue.WARNING
    DesktopErrorKind.OFFLINE -> DesktopPetCue.OFFLINE
    DesktopErrorKind.BLOCKED -> DesktopPetCue.BLOCKED
    else -> DesktopPetCue.ERROR
}
