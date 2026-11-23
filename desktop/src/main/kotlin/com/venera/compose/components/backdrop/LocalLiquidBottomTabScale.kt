// SPDX-License-Identifier: Apache-2.0
// Ported from Kyant0/AndroidLiquidGlass (LiquidBottomTabs.kt)
// 通过 lambda 包装缩放比例供三层渲染共享，避免动画值提升为参数触发全量重组。
package com.venera.compose.components.backdrop

import androidx.compose.runtime.compositionLocalOf

val LocalLiquidBottomTabScale = compositionLocalOf { { 1f } }
