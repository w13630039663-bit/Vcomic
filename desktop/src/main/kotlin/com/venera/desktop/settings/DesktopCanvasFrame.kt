package com.venera.desktop.settings

import com.venera.compose.data.platform.JsonKeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 作品详情页画布的承托参数：内衬厚度、画作圆角、环境光与落影强度。
 *
 * 不存"当前是哪组预设"：手调过任何一枚就自然脱离预设，
 * 这样面板上的预设选中态永远和实际值一致，不会出现显示分裂。
 */
object DesktopCanvasFrame {

    /** 内衬与圆角单位 dp；环境光、落影是 0–1 的强度。 */
    data class Spec(
        val padDp: Float = 10f,
        val radiusDp: Float = 16f,
        val glow: Float = 0f,
        val shadow: Float = 0.38f,
    )

    /** 四枚一次写全的预设档。 */
    enum class Preset(val label: String, val spec: Spec) {
        RECOMMENDED("推荐", Spec()),
        COMPACT("紧凑", Spec(padDp = 6f, radiusDp = 12f, glow = 0f, shadow = 0.30f)),
        FLAT("无框", Spec(padDp = 0f, radiusDp = 10f, glow = 0f, shadow = 0f)),
        SOFT("柔和", Spec(padDp = 12f, radiusDp = 20f, glow = 0.45f, shadow = 0.42f)),
    }

    const val KEY_PAD = "pref_desktop_canvas_frame_pad"
    const val KEY_RADIUS = "pref_desktop_canvas_frame_radius"
    const val KEY_GLOW = "pref_desktop_canvas_frame_glow"
    const val KEY_SHADOW = "pref_desktop_canvas_frame_shadow"

    /** 滑块范围，设置页与读盘钳制共用同一份，避免两处各写一个上限。 */
    const val PAD_MIN = 0f
    const val PAD_MAX = 24f
    const val RADIUS_MIN = 0f
    const val RADIUS_MAX = 32f

    private val _current = MutableStateFlow(Spec())

    /** 当前生效参数，两个详情页画布同读。 */
    val current: StateFlow<Spec> = _current.asStateFlow()

    /** 预设归属：四枚与某档完全一致才算选中，否则为 null（已手调）。 */
    fun presetOf(spec: Spec): Preset? = Preset.entries.firstOrNull { it.spec == spec }

    /**
     * 从持久化存储还原四枚。
     * @param store 目标键值存储
     */
    fun restore(store: JsonKeyValueStore) {
        _current.value = clamp(
            Spec(
                padDp = store.getFloat(KEY_PAD, Spec().padDp),
                radiusDp = store.getFloat(KEY_RADIUS, Spec().radiusDp),
                glow = store.getFloat(KEY_GLOW, Spec().glow),
                shadow = store.getFloat(KEY_SHADOW, Spec().shadow),
            ),
        )
    }

    /**
     * 写入四枚并立即生效。
     * @param store 目标键值存储
     * @param spec 目标参数
     */
    fun update(store: JsonKeyValueStore, spec: Spec) {
        val next = clamp(spec)
        _current.value = next
        store.putAll(
            mapOf(
                KEY_PAD to next.padDp,
                KEY_RADIUS to next.radiusDp,
                KEY_GLOW to next.glow,
                KEY_SHADOW to next.shadow,
            ),
        )
    }

    /**
     * 回到推荐值。
     * @param store 目标键值存储
     */
    fun reset(store: JsonKeyValueStore) = update(store, Spec())

    private fun clamp(spec: Spec): Spec = spec.copy(
        padDp = spec.padDp.coerceIn(PAD_MIN, PAD_MAX),
        radiusDp = spec.radiusDp.coerceIn(RADIUS_MIN, RADIUS_MAX),
        glow = spec.glow.coerceIn(0f, 1f),
        shadow = spec.shadow.coerceIn(0f, 1f),
    )
}
