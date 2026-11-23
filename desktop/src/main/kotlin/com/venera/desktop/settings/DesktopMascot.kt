package com.venera.desktop.settings

import com.venera.compose.data.platform.JsonKeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 待机吉祥物「薇薇」的调参：透明度、尺寸、呼吸与打盹节奏、视线跟随、状态锁档。
 *
 * 数值范围与默认值即设置页滑块的可调区间，读盘与写入共用同一份钳制。
 */
object DesktopMascot {

    /** 状态锁档：AUTO 跟随交互与闲置计时自动切换，其余三档强制锁定对应姿态。 */
    enum class ModeOverride { AUTO, IDLE, LISTENING, DOZING }

    /** 全部调参。待机透明度是形象存在感基线，打盹在其上再乘系数。 */
    data class Spec(
        val idleAlpha: Float = 0.28f,
        val sizeDp: Float = 64f,
        val breathCycle: Float = 4.8f,
        val breathAmp: Float = 1f,
        val dozeDelay: Float = 15f,
        val gazeFollow: Boolean = true,
        val modeOverride: ModeOverride = ModeOverride.AUTO,
    )

    const val KEY_ALPHA = "pref_desktop_mascot_idle_alpha"
    const val KEY_SIZE = "pref_desktop_mascot_size"
    const val KEY_CYCLE = "pref_desktop_mascot_breath_cycle"
    const val KEY_AMP = "pref_desktop_mascot_breath_amp"
    const val KEY_DOZE = "pref_desktop_mascot_doze_delay"
    const val KEY_GAZE = "pref_desktop_mascot_gaze_follow"
    const val KEY_MODE = "pref_desktop_mascot_mode_override"

    /** 滑块范围常量，设置页与读盘钳制共用。 */
    const val ALPHA_MIN = 0.12f
    const val ALPHA_MAX = 0.60f
    const val SIZE_MIN = 40f
    const val SIZE_MAX = 96f
    const val CYCLE_MIN = 2.5f
    const val CYCLE_MAX = 8f
    const val AMP_MIN = 0.4f
    const val AMP_MAX = 2f
    const val DOZE_MIN = 3f
    const val DOZE_MAX = 20f

    private val _current = MutableStateFlow(Spec())

    /** 当前生效参数，吉祥物各落位同读。 */
    val current: StateFlow<Spec> = _current.asStateFlow()

    /**
     * 从持久化存储还原全部调参。
     * @param store 目标键值存储
     */
    fun restore(store: JsonKeyValueStore) {
        val d = Spec()
        _current.value = clamp(
            Spec(
                idleAlpha = store.getFloat(KEY_ALPHA, d.idleAlpha),
                sizeDp = store.getFloat(KEY_SIZE, d.sizeDp),
                breathCycle = store.getFloat(KEY_CYCLE, d.breathCycle),
                breathAmp = store.getFloat(KEY_AMP, d.breathAmp),
                dozeDelay = store.getFloat(KEY_DOZE, d.dozeDelay),
                gazeFollow = store.getBoolean(KEY_GAZE, d.gazeFollow),
                modeOverride = ModeOverride.entries.getOrElse(store.getInt(KEY_MODE, d.modeOverride.ordinal)) { d.modeOverride },
            ),
        )
    }

    /**
     * 写入全部调参并立即生效。
     * @param store 目标键值存储
     * @param spec 目标参数
     */
    fun update(store: JsonKeyValueStore, spec: Spec) {
        val next = clamp(spec)
        _current.value = next
        store.putAll(
            mapOf(
                KEY_ALPHA to next.idleAlpha,
                KEY_SIZE to next.sizeDp,
                KEY_CYCLE to next.breathCycle,
                KEY_AMP to next.breathAmp,
                KEY_DOZE to next.dozeDelay,
                KEY_GAZE to next.gazeFollow,
                KEY_MODE to next.modeOverride.ordinal,
            ),
        )
    }

    /**
     * 回到默认值。
     * @param store 目标键值存储
     */
    fun reset(store: JsonKeyValueStore) = update(store, Spec())

    private fun clamp(spec: Spec): Spec = spec.copy(
        idleAlpha = spec.idleAlpha.coerceIn(ALPHA_MIN, ALPHA_MAX),
        sizeDp = spec.sizeDp.coerceIn(SIZE_MIN, SIZE_MAX),
        breathCycle = spec.breathCycle.coerceIn(CYCLE_MIN, CYCLE_MAX),
        breathAmp = spec.breathAmp.coerceIn(AMP_MIN, AMP_MAX),
        dozeDelay = spec.dozeDelay.coerceIn(DOZE_MIN, DOZE_MAX),
        modeOverride = spec.modeOverride,
    )
}
