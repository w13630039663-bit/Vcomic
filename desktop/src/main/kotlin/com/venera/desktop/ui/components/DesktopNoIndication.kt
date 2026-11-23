package com.venera.desktop.ui.components

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.DelegatableNode

/**
 * 空指示器（撤全项目「长按波浪」）。
 *
 * Compose 的 `Modifier.clickable` 默认吃 `LocalIndication`（Material 涟漪 = 桌面上的长按波浪）。
 * 桌面语义里没有涟漪：可点面靠**悬停上跳**（`desktopCardLiftModifier`）与色面变化反馈，
 * 按下不铺水波。在窗根 `LocalIndication provides DesktopNoIndication` 一次关掉，
 * 比逐个 `clickable` 加 `indication = null` 更彻底（后者漏一处就复发）。
 *
 * 注：显式传 `indication = null` 的调用点不受影响；本件只接管"没写 indication"的那一批。
 */
object DesktopNoIndication : IndicationNodeFactory {

    override fun create(interactionSource: InteractionSource): DelegatableNode = object : Modifier.Node() {}

    override fun equals(other: Any?): Boolean = other === this

    override fun hashCode(): Int = -1
}
