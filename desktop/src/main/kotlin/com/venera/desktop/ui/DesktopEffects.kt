package com.venera.desktop.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 全窗特效总开关（设置中枢「桌面件」区写，消费点只读这一位）。
 *
 * 只收装饰性的模糊与玻璃材质；阅读器打码页的模糊是内容保护，不在此列。
 * 侧栏玻璃本就只剩中性 tint、无模糊可关，实现不动。
 */
object DesktopEffects {
    var reduce: Boolean by mutableStateOf(false)
}
