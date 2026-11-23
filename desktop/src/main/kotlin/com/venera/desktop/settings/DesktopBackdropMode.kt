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

    private const val PREF_KEY = "pref_desktop_backdrop_mode"

    private val _mode = MutableStateFlow(ACRYLIC)

    /** 当前生效的窗底材质档位。 */
    val mode: StateFlow<String> = _mode.asStateFlow()

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
        _mode.value = normalizeOrNull(envOverride)
            ?: normalizeOrNull(store.getString(PREF_KEY, null))
            ?: ACRYLIC
    }

    /**
     * 切换并持久化窗底材质档位。
     *
     * @param value 目标档位名，无法识别时回退 Acrylic
     */
    fun select(value: String) {
        val next = normalizeOrNull(value) ?: ACRYLIC
        _mode.value = next
        prefsStore?.put(PREF_KEY, next)
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
        return com.venera.desktop.DesktopDwmBackdrop.active && isTransparent(current)
    }

    /**
     * 将档位名映射为 Nucleus 窗口系统的 DWM 材质枚举。
     *
     * @param mode 当前材质档位名
     */
    /**
     * 将档位名映射为 Nucleus 窗口系统的 DWM 材质枚举。
     *
     * @param mode 当前材质档位名
     * @param railBlur 侧栏模糊是否开启
     * @param topBarBlur 标题栏模糊是否开启
     *
     * 三处只要有一处要模糊，系统材质就必须保留——材质是窗口级的，
     * 关掉它等于把侧栏/顶栏的模糊源一并抽走。
     */
    fun styleOf(
        mode: String,
        railBlur: Boolean = DesktopRailBlur.visible.value,
        topBarBlur: Boolean = DesktopTopBarBlur.visible.value,
    ): WindowsBackdropStyle = when {
        mode == SOLID && !railBlur && !topBarBlur -> WindowsBackdropStyle.None
        mode == MICA -> WindowsBackdropStyle.Mica
        else -> WindowsBackdropStyle.Acrylic
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
