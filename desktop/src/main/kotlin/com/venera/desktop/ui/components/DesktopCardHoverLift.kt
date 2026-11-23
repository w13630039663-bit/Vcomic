package com.venera.desktop.ui.components

import com.venera.desktop.ui.vShadow
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Shape
import com.venera.compose.ui.tokens.LocalVeneraDarkTheme
import com.venera.desktop.gallery.ui.GalleryTokens

/**
 * 卡片悬停抬升（漫画卡也吃画廊卡那份浮动）。
 *
 * 与唯一卡 `DesktopGalleryCard` 逐项同语言：悬停抬 `(-3dp)` + 阴影常态 `4dp` / 悬停 `12dp`
 * （spotColor 走 `ShadowSpot`），深色档不铺阴影（维持描边分层，与画廊卡同口径）。
 * 收在这一处，省得每屏各写一份会漂。
 *
 * 用法：把返回的 modifier 接在卡片根 modifier **最前**（抬升与阴影在裁切/描边之前生效）。
 * `weight` / `aspectRatio` 等排布 modifier 仍由卡片自己接在后面。
 */
@Composable
fun desktopCardLiftModifier(
    shape: Shape,
): Modifier {
    val isDark = LocalVeneraDarkTheme.current
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val lift by animateDpAsState(
        targetValue = if (isHovered) (-3).dp else 0.dp,
        label = "comicCardLift",
    )
    return Modifier
        .offset(y = lift)
        // 修「卡片还有灰边」：原常态 4dp 是 Skia drop shadow，在浅底上渲染成**硬边**
        //（观感就是一圈灰框），叠在卡片自己的 0.5dp 发丝描边上更明显。
        // 现在常态**不铺影**（靠白卡 vs 浅灰背景的色差分层），只有悬停才浮起 8dp。
        .vShadow(
            elevation = if (isDark) 0.dp else if (isHovered) 8.dp else 0.dp,
            shape = shape,
        )
        .hoverable(interactionSource)
}
