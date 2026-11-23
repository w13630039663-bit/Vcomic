package com.venera.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.venera.compose.ui.tokens.VeneraTokens

/**
 * 列表型界面的统一空态：整区居中的玻璃卡，文案由调用点传入。
 *
 * 空态句在装配点是成品、被语义判据逐字钉着，本组件只统一"怎么摆"——居中容器 +
 * 同一张玻璃卡，不改写任何措辞。此前收藏 / 历史 / 画廊各写一颗私有 CenterNote，
 * 其中历史还是裸灰字；统一到这一颗后，各屏的空态视觉一致，文案仍各归各。
 *
 * @param text 空态那一句，原样上屏，不在本组件内改写。
 */
@Composable
internal fun DesktopEmptyState(
    text: String,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        DesktopGlassNote(
            text = text,
            modifier = Modifier.padding(horizontal = tokens.spacing.space5),
        )
    }
}
