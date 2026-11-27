package com.venera.desktop.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import dev.nucleusframework.window.WindowsBackdropStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 窗口系统材质模式：Mica / Acrylic / 实底。
 *
 * 与 [DesktopThemeMode] 同构：持有内存热流与偏好存储引用，界面切换后立即重绘并落盘。
 * 侧栏模糊是独立开关（[DesktopRailBlur]），取系统材料糊自己那一片，与底板档位互不推导。
 */
object DesktopBackdropMode {

    const val MICA = "Mica"
    const val ACRYLIC = "Acrylic"
    const val SOLID = "实底"

    val options: List<String> = listOf(ACRYLIC, MICA, SOLID)
    val effectOptions: List<String> = listOf(ACRYLIC, MICA)

    private const val PREF_KEY = "pref_desktop_backdrop_mode"
    private const val EFFECT_PREF_KEY = "pref_desktop_blur_effect"

    private val _mode = MutableStateFlow(ACRYLIC)
    private val _effect = MutableStateFlow(ACRYLIC)

    /** 当前生效的窗底材质档位（含实底）。 */
    val mode: StateFlow<String> = _mode.asStateFlow()

    /** 当前选择的模糊效果类型（Acrylic / Mica），独立于各区模糊开关保存。 */
    val effect: StateFlow<String> = _effect.asStateFlow()

    @Volatile
    private var prefsStore: com.venera.compose.data.platform.JsonKeyValueStore? = null

    /**
     * 绑定偏好存储并回填档位（启动参数优先于盘上值）。
     *
     * @param store 主偏好键值存储实例
     * @param envOverride 启动参数或环境变量指定的材质覆盖值
     */
    fun bind(
        store: com.venera.compose.data.platform.JsonKeyValueStore,
        envOverride: String? = null,
    ) {
        prefsStore = store
        val savedMode = normalizeOrNull(envOverride) ?: normalizeOrNull(store.getString(PREF_KEY, null))
        val savedEffect = normalizeEffectOrNull(envOverride)
            ?: normalizeEffectOrNull(store.getString(EFFECT_PREF_KEY, null))
            ?: if (savedMode == MICA) MICA else ACRYLIC
        _effect.value = savedEffect
        _mode.value = when (savedMode) {
            SOLID -> SOLID
            MICA -> MICA
            ACRYLIC -> ACRYLIC
            else -> savedEffect
        }
    }

    /**
     * 切换并持久化窗底材质档位。
     *
     * @param value 目标档位名，无法识别时回退当前所选模糊效果
     */
    fun select(value: String) {
        val next = normalizeOrNull(value) ?: _effect.value
        _mode.value = next
        prefsStore?.put(PREF_KEY, next)
        if (next == ACRYLIC || next == MICA) {
            _effect.value = next
            prefsStore?.put(EFFECT_PREF_KEY, next)
        }
    }

    /**
     * 切换并持久化模糊效果类型（Acrylic / Mica）。
     * 若底板模糊当前处于开启状态，同步热切换整窗材质。
     */
    fun selectEffect(value: String) {
        val nextEffect = normalizeEffectOrNull(value) ?: ACRYLIC
        _effect.value = nextEffect
        prefsStore?.put(EFFECT_PREF_KEY, nextEffect)
        if (_mode.value != SOLID) {
            _mode.value = nextEffect
            prefsStore?.put(PREF_KEY, nextEffect)
        }
    }

    /**
     * 判断当前材质档位是否需要窗口内容区放行透明通道。
     *
     * @param mode 当前材质档位名
     */
    fun isTransparent(mode: String): Boolean = mode != SOLID

    /** 在 Composable 内直接读取当前是否处于系统材质透明激活态。 */
    @Composable
    fun isTransparentActive(): Boolean {
        val current = mode.collectAsState().value
        return com.venera.desktop.ui.DesktopDwmBackdrop.active && isTransparent(current)
    }

    /** 在 Composable 内判断当前活跃的模糊效果是否为 Mica。 */
    @Composable
    fun isMicaEffect(): Boolean {
        val currentMode = mode.collectAsState().value
        val currentEffect = effect.collectAsState().value
        return currentMode == MICA || (currentMode == SOLID && currentEffect == MICA)
    }

    /**
     * 将档位名映射为 Nucleus 窗口系统的 DWM 材质枚举。
     *
     * @param mode 当前材质档位名
     * @param railBlur 侧栏模糊是否开启
     * @param topBarBlur 标题栏模糊是否开启
     * @param effect 当前选中的模糊效果（Acrylic / Mica）
     *
     * 三处只要有一处要模糊，系统材质就必须保留——材质是窗口级的，
     * 关掉它等于把侧栏/顶栏的模糊源一并抽走。Mica 选用加强云母 MicaAlt，确保壁纸色晕清晰可见。
     */
    fun styleOf(
        mode: String,
        railBlur: Boolean = DesktopRailBlur.visible.value,
        topBarBlur: Boolean = DesktopTopBarBlur.visible.value,
        effect: String = _effect.value,
        dark: Boolean = false,
    ): WindowsBackdropStyle {
        val activeEffect = when (mode) {
            MICA -> MICA
            ACRYLIC -> ACRYLIC
            else -> effect
        }
        return when {
            mode == SOLID && !railBlur && !topBarBlur -> WindowsBackdropStyle.None
            activeEffect == MICA -> if (dark) WindowsBackdropStyle.Mica else WindowsBackdropStyle.MicaAlt
            else -> WindowsBackdropStyle.Acrylic
        }
    }

    private fun normalizeEffectOrNull(raw: String?): String? = when (normalizeOrNull(raw)) {
        MICA -> MICA
        ACRYLIC -> ACRYLIC
        else -> null
    }

    private fun normalizeOrNull(raw: String?): String? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        return when (text.lowercase()) {
            "mica", "tabbed", "micaalt", "mica_alt", "mica alt" -> MICA
            "acrylic" -> ACRYLIC
            "none", "solid", "off", "实底" -> SOLID
            else -> null
        }
    }
}
