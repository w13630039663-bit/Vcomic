package com.venera.desktop.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 首页皮肤档：经典（**已废弃，勿动**）/ Acrylic（现行唯一档）。
 *
 * 形状照 [DesktopThemeMode]：单例热流（设置行写完主壳热切）+ 主 prefs 文件同名键落盘，
 * 冷启动由主壳读盘回填 [mode]。
 *
 * ⚠️ [CLASSIC] 那条路径已废弃：设置只写 [LIGHT]，经典档的界面代码（标准侧栏轨、
 * 经典标题栏、旧首页仪表）全部保留但不再进入。**后续改动一律不要碰经典分支**，
 * 修外观/加功能只改 Acrylic 这一档，否则等于给废代码交维护税。
 */
object DesktopHomeSkin {

    /** 已废弃：保留常量只为读盘兼容，不要新增引用。 */
    const val CLASSIC = "经典"
    const val LIGHT = "Acrylic"

    private val _mode = MutableStateFlow(LIGHT)

    /** 当前档（固定为 Acrylic）。 */
    val mode: StateFlow<String> = _mode.asStateFlow()

    /** 主壳冷启动回填（统一收口为 Acrylic）。 */
    fun restore(saved: String) {
        _mode.value = LIGHT
    }

    /** 设置行的写侧：改流（热切）+落盘（冷启动）。 */
    fun write(store: com.venera.compose.data.platform.JsonKeyValueStore, value: String) {
        _mode.value = LIGHT
        store.put("pref_desktop_home_skin", LIGHT)
    }
}
