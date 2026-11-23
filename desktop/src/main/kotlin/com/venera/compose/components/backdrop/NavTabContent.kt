// SPDX-License-Identifier: Apache-2.0
// 同一份 content 会被外壳层、着色层与指示器层渲染三次，必须保持纯函数式且无自身状态。
package com.venera.compose.components.backdrop

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.components.AnimatedNavIcon
import com.venera.compose.components.VeneraNavTab
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 单个导航项。图标、颜色与字重共同表达选中态；
 * 按压放大比例读 [LocalLiquidBottomTabScale]（由官方组件在外层提供）。
 */
@Composable
fun RowScope.VeneraLiquidNavTab(
    tab: VeneraNavTab,
    selected: Boolean,
    isDark: Boolean,
    onTabSelected: (VeneraNavTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val contentColor by animateColorAsState(
        targetValue = if (selected) MiuixTheme.colorScheme.primary
        else MiuixTheme.colorScheme.onSurface.copy(alpha = if (isDark) 0.7f else 0.6f),
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "NavTabColor"
    )
    val tabScale = LocalLiquidBottomTabScale.current()

    Column(
        modifier = modifier
            .weight(1f)
            // 对齐官方 LiquidBottomTab：撑满 56dp 内容层高度后再整体居中，
            // 缺了 fillMaxHeight 时 Column 只有内容高度、垂直居中基准不牢，
            // 图标会相对药丸中心偏上（尤其按下放大时更明显）。
            .fillMaxHeight()
            .selectable(
                selected = selected,
                role = Role.Tab,
                interactionSource = interaction,
                indication = null,
                onClick = { if (!selected) onTabSelected(tab) },
            )
            .graphicsLayer {
                scaleX = tabScale
                scaleY = tabScale
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        // 官方用 spacedBy(2.dp, CenterVertically) 而非 Arrangement.Center + 手写 Spacer：
        // 排版由 Column 统一负责，图标与标签作为整体垂直居中。
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)
    ) {
        AnimatedNavIcon(
            tab = tab,
            selected = selected,
            tint = contentColor,
            baseSize = if (selected) 23.dp else 21.dp,
        )
        Text(
            text = tab.title,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = contentColor,
        )
    }
}

/** 官方组件的 content 插槽入口：一次性铺出所有 tab。 */
@Composable
fun RowScope.VeneraLiquidNavTabs(
    tabs: List<VeneraNavTab>,
    currentTab: VeneraNavTab,
    isDark: Boolean,
    onTabSelected: (VeneraNavTab) -> Unit,
) {
    tabs.forEach { tab ->
        VeneraLiquidNavTab(
            tab = tab,
            selected = tab == currentTab,
            isDark = isDark,
            onTabSelected = onTabSelected,
        )
    }
}
