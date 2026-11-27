package com.venera.desktop.settings

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.venera.compose.data.platform.JsonKeyValueStore
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** 像素互动伙伴预设皮肤（3px 纯方块无描边风格）。 */
enum class PixelBuddySkin(
    val id: String,
    val labelZh: String,
    val labelEn: String,
) {
    CAT("cat", "薰衣草紫猫", "Lavender Cat"),
    ORANGE("orange", "暖橘小方块", "Warm Block"),
    BLUE("blue", "雾蓝小兔", "Mist Bunny");

    val label: String get() = if (DesktopLanguage.isEnglish()) labelEn else labelZh

    companion object {
        fun fromId(id: String?): PixelBuddySkin = entries.firstOrNull { it.id == id } ?: CAT
    }
}

/** 像素互动伙伴全量配置。 */
data class PixelBuddyConfig(
    val enabled: Boolean = true,
    val count: Int = 2,
    val primarySkin: PixelBuddySkin = PixelBuddySkin.CAT,
    val searchTypingSpell: Boolean = true,
    val searchCardBuild: Boolean = true,
    val cardClimb: Boolean = true,
    val poopEnabled: Boolean = true,
)

/**
 * 像素互动伙伴偏好持久化单源。
 */
object DesktopPixelBuddyPrefs {

    private const val KEY_ENABLED = "pref_pixel_buddy_enabled"
    private const val KEY_COUNT = "pref_pixel_buddy_count"
    private const val KEY_SKIN = "pref_pixel_buddy_skin"
    private const val KEY_SEARCH_SPELL = "pref_pixel_buddy_search_spell"
    private const val KEY_SEARCH_BUILD = "pref_pixel_buddy_search_build"
    private const val KEY_CARD_CLIMB = "pref_pixel_buddy_card_climb"
    private const val KEY_POOP = "pref_pixel_buddy_poop"

    private val _config = MutableStateFlow(PixelBuddyConfig())

    /** 当前生效的像素伙伴配置流。 */
    val config: StateFlow<PixelBuddyConfig> = _config.asStateFlow()

    /** 冷启动从主偏好存储回填像素伙伴配置。 */
    fun restore(store: JsonKeyValueStore) {
        _config.value = PixelBuddyConfig(
            enabled = store.getBoolean(KEY_ENABLED, true),
            count = store.getInt(KEY_COUNT, 2).coerceIn(1, 3),
            primarySkin = PixelBuddySkin.fromId(store.getString(KEY_SKIN, null)),
            searchTypingSpell = store.getBoolean(KEY_SEARCH_SPELL, true),
            searchCardBuild = store.getBoolean(KEY_SEARCH_BUILD, true),
            cardClimb = store.getBoolean(KEY_CARD_CLIMB, true),
            poopEnabled = store.getBoolean(KEY_POOP, true),
        )
    }

    /** 更新配置并落盘。 */
    fun update(store: JsonKeyValueStore?, transform: (PixelBuddyConfig) -> PixelBuddyConfig) {
        val next = transform(_config.value).let { it.copy(count = it.count.coerceIn(1, 3)) }
        _config.value = next
        if (store != null) {
            store.put(KEY_ENABLED, next.enabled)
            store.put(KEY_COUNT, next.count)
            store.put(KEY_SKIN, next.primarySkin.id)
            store.put(KEY_SEARCH_SPELL, next.searchTypingSpell)
            store.put(KEY_SEARCH_BUILD, next.searchCardBuild)
            store.put(KEY_CARD_CLIMB, next.cardClimb)
            store.put(KEY_POOP, next.poopEnabled)
        }
    }
}

/** 搜索框向像素伙伴层广播的实时状态。 */
data class PixelBuddySearchState(
    val focused: Boolean = false,
    val searching: Boolean = false,
    val boundsInWindow: Rect? = null,
    val query: String = "",
    val typingTick: Int = 0,
    val confirmTick: Int = 0,
    val crossPagePending: Boolean = false,
    val ignoredCardKeys: Set<String> = emptySet(),
)

/** 快捷互动指令（供设置页试玩按钮或业务事件触发）。 */
enum class PixelBuddyCommand {
    NONE, GREET, STACK, FEED, POOP, JUMP_CARD
}

/** 单个结果卡片在描绘会话中的显影阶段。 */
sealed interface PixelBuddyCardRevealPhase {
    /** 不处于描绘会话（或已完成清理），正常零遮罩渲染。 */
    data object None : PixelBuddyCardRevealPhase
    /** 等待宠物或光流唤醒，展示线稿半暗暗幕。 */
    data class Waiting(val accentArgb: Long) : PixelBuddyCardRevealPhase
    /** 宠物正沿着卡片顶边向右擦除，[progress] 取值 `0f..1f`。 */
    data class Skimming(val progress: Float, val accentArgb: Long) : PixelBuddyCardRevealPhase
    /** 已被擦亮或光流点亮，触发一次柔和退场与斜向扫光。 */
    data class Awakened(val sessionId: Int, val accentArgb: Long) : PixelBuddyCardRevealPhase
}

/** 全局卡片描绘会话状态快照。 */
data class PixelBuddyCardPaintSnapshot(
    val sessionId: Int = 0,
    val active: Boolean = false,
    val phases: Map<String, PixelBuddyCardRevealPhase> = emptyMap(),
) {
    fun phaseOf(key: String): PixelBuddyCardRevealPhase =
        if (!active) PixelBuddyCardRevealPhase.None else (phases[key] ?: PixelBuddyCardRevealPhase.None)
}

/**
 * 像素互动伙伴全局轻量事件与表面注册总线。
 */
object DesktopPixelBuddyBus {

    private val _searchState = MutableStateFlow(PixelBuddySearchState())
    val searchState: StateFlow<PixelBuddySearchState> = _searchState.asStateFlow()

    /** 卡片窗口位置无拷贝注册表：避免全量 map 拷贝触发 60fps 分配与重组。 */
    private val _cardRectsMap = ConcurrentHashMap<String, Rect>()

    private val _hasCards = MutableStateFlow(false)
    val hasCards: StateFlow<Boolean> = _hasCards.asStateFlow()

    private val _command = MutableStateFlow(Pair(0, PixelBuddyCommand.NONE))
    val command: StateFlow<Pair<Int, PixelBuddyCommand>> = _command.asStateFlow()

    private val _cardPaint = MutableStateFlow(PixelBuddyCardPaintSnapshot())
    val cardPaint: StateFlow<PixelBuddyCardPaintSnapshot> = _cardPaint.asStateFlow()

    private val _cardPaintActive = MutableStateFlow(false)
    val cardPaintActive: StateFlow<Boolean> = _cardPaintActive.asStateFlow()

    internal val wakeChannel = Channel<Unit>(Channel.CONFLATED)

    private val _scrollInterruptTick = MutableStateFlow(0)
    val scrollInterruptTick: StateFlow<Int> = _scrollInterruptTick.asStateFlow()

    private val _emptyAnchor = MutableStateFlow<Rect?>(null)
    val emptyAnchor: StateFlow<Rect?> = _emptyAnchor.asStateFlow()

    private val _retryAnchor = MutableStateFlow<Rect?>(null)
    val retryAnchor: StateFlow<Rect?> = _retryAnchor.asStateFlow()

    private val _wandMode = MutableStateFlow(false)
    val wandMode: StateFlow<Boolean> = _wandMode.asStateFlow()

    private val _wandPointerInWindow = MutableStateFlow<Offset?>(null)
    val wandPointerInWindow: StateFlow<Offset?> = _wandPointerInWindow.asStateFlow()

    /** 高频非重组鼠标窗口坐标（供逗猫棒与平时好奇转头读取，不触发 Compose 重组）。 */
    @Volatile
    var latestPointerInWindow: Offset? = null
        private set

    /** 立即唤醒宠物动画主循环（从闲置低帧率瞬间切回高帧率响应交互）。 */
    fun requestWake() {
        wakeChannel.trySend(Unit)
    }

    /** 上报搜索框聚焦态变化。 */
    fun reportSearchFocus(focused: Boolean, bounds: Rect? = null, query: String? = null) {
        _searchState.update { cur ->
            cur.copy(
                focused = focused,
                boundsInWindow = bounds ?: cur.boundsInWindow,
                query = query ?: cur.query,
            )
        }
        requestWake()
    }

    /** 上报搜索框输入变化（驱动打字节拍与实时像素拼字）。 */
    fun reportSearchTyping(query: String, bounds: Rect? = null) {
        _searchState.update { cur ->
            cur.copy(
                query = query,
                boundsInWindow = bounds ?: cur.boundsInWindow,
                typingTick = cur.typingTick + 1,
            )
        }
        requestWake()
    }

    /** 上报搜索确认（驱动扫光与结果卡片微光点亮；跨页跳转时屏蔽当前旧页残余卡片）。 */
    fun reportSearchConfirm(query: String, crossPage: Boolean = false) {
        val staleKeys = if (crossPage && _cardRectsMap.isNotEmpty()) HashSet(_cardRectsMap.keys) else emptySet()
        _searchState.update { cur ->
            cur.copy(
                query = query,
                searching = true,
                confirmTick = cur.confirmTick + 1,
                crossPagePending = crossPage,
                ignoredCardKeys = staleKeys,
            )
        }
        requestWake()
    }

    /** 顶栏或旧搜索框销毁时清理自身残留定位，避免新页宠物悬在已消失的旧位置。 */
    fun clearSearchFocusAndBounds(expectedBounds: Rect? = null) {
        _searchState.update { cur ->
            val clearBounds = expectedBounds == null || cur.boundsInWindow == expectedBounds
            cur.copy(
                focused = false,
                boundsInWindow = if (clearBounds) null else cur.boundsInWindow,
            )
        }
        requestWake()
    }

    /** 上报搜索加载中/完成状态。 */
    fun reportSearching(searching: Boolean) {
        _searchState.update { cur ->
            cur.copy(
                searching = searching,
                crossPagePending = if (searching) cur.crossPagePending else false,
            )
        }
        requestWake()
    }

    /** 注册当前视口内可见的漫画/画廊卡片边框（小于 1.5px 抖动直接忽略）。 */
    fun registerCard(key: String, rect: Rect) {
        val prev = _cardRectsMap[key]
        if (prev != null &&
            abs(prev.left - rect.left) < 1.5f &&
            abs(prev.top - rect.top) < 1.5f &&
            abs(prev.right - rect.right) < 1.5f &&
            abs(prev.bottom - rect.bottom) < 1.5f
        ) {
            return
        }
        _cardRectsMap[key] = rect
        if (!_hasCards.value) _hasCards.value = true
    }

    /** 注销离开视口的卡片边框（同时清除其跨页忽略标记，避免后续同 key 新卡被屏蔽）。 */
    fun unregisterCard(key: String) {
        _cardRectsMap.remove(key)
        val curIgnored = _searchState.value.ignoredCardKeys
        if (key in curIgnored) {
            _searchState.update { cur ->
                if (key in cur.ignoredCardKeys) cur.copy(ignoredCardKeys = cur.ignoredCardKeys - key) else cur
            }
        }
        if (_hasCards.value && _cardRectsMap.isEmpty()) _hasCards.value = false
    }

    /** 清除跨页忽略集合。 */
    fun clearIgnoredCards() {
        if (_searchState.value.ignoredCardKeys.isNotEmpty() || _searchState.value.crossPagePending) {
            _searchState.update { it.copy(crossPagePending = false, ignoredCardKeys = emptySet()) }
        }
    }

    /** O(1) 读取单张可见卡片的窗口矩形。 */
    fun cardRectOf(key: String): Rect? = _cardRectsMap[key]

    /** 仅在搜索描绘会话启动时快照全部可见卡片并转为局部坐标（自动跳过跨页残余旧卡）。 */
    fun snapshotCardRects(
        offsetX: Float = 0f,
        offsetY: Float = 0f,
        excludeKeys: Set<String> = emptySet(),
    ): Map<String, Rect> {
        if (_cardRectsMap.isEmpty()) return emptyMap()
        val out = HashMap<String, Rect>(_cardRectsMap.size)
        _cardRectsMap.forEach { (k, r) ->
            if (k !in excludeKeys) {
                out[k] = if (offsetX == 0f && offsetY == 0f) r else r.translate(-offsetX, -offsetY)
            }
        }
        return out
    }

    /** 检查除被屏蔽旧卡之外是否已有新卡片入列。 */
    fun hasUnignoredCards(excludeKeys: Set<String>): Boolean {
        if (_cardRectsMap.isEmpty()) return false
        if (excludeKeys.isEmpty()) return true
        return _cardRectsMap.keys.any { it !in excludeKeys }
    }

    /** 按需遍历当前可见卡片并就地转换坐标，零 Map 分配。 */
    fun forEachCardRect(offsetX: Float = 0f, offsetY: Float = 0f, action: (String, Rect) -> Unit) {
        _cardRectsMap.forEach { (k, r) ->
            action(k, if (offsetX == 0f && offsetY == 0f) r else r.translate(-offsetX, -offsetY))
        }
    }

    /** 更新当前搜索结果描绘会话的卡片遮罩与擦除进度。 */
    fun updateCardPaint(snapshot: PixelBuddyCardPaintSnapshot) {
        _cardPaint.value = snapshot
        _cardPaintActive.value = snapshot.active
        requestWake()
    }

    /** 清空卡片描绘遮罩会话，恢复普通渲染。 */
    fun clearCardPaint() {
        _cardPaint.value = PixelBuddyCardPaintSnapshot()
        _cardPaintActive.value = false
    }

    /** 通知结果列表正在被用户滚动，正在描绘的宠物应立即收笔避让。 */
    fun reportUserScroll() {
        _scrollInterruptTick.update { it + 1 }
        requestWake()
    }

    /** 上报或注销搜索空结果 / 加载失败重试按钮的窗口锚点矩形。 */
    fun reportStatusAnchor(isRetry: Boolean, rect: Rect?) {
        if (isRetry) _retryAnchor.value = rect else _emptyAnchor.value = rect
        if (rect != null) requestWake()
    }

    /** 切换或设置逗猫棒彩蛋开关。 */
    fun setWandMode(enabled: Boolean) {
        _wandMode.value = enabled
        if (!enabled) _wandPointerInWindow.value = null
        requestWake()
    }

    fun toggleWandMode() {
        val next = !_wandMode.value
        _wandMode.value = next
        if (!next) _wandPointerInWindow.value = null
        requestWake()
    }

    /** 上报鼠标窗口坐标（由祖先容器旁路只读观察，逗猫棒开启时同步流）。 */
    fun reportPointerPosition(posInWindow: Offset?, syncState: Boolean) {
        latestPointerInWindow = posInWindow
        if (syncState) _wandPointerInWindow.value = posInWindow
    }

    /** 触发一次像素伙伴快捷互动指令。 */
    fun trigger(cmd: PixelBuddyCommand) {
        _command.update { Pair(it.first + 1, cmd) }
        requestWake()
    }
}
