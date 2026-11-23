package com.venera.desktop.ui

import com.venera.desktop.ui.theme.VeneraType
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.ui.tokens.VeneraTokens

/**
 * 桌面统一的「选块」（selection block）——：
 * 「不是设计稿都是选块了吗？我要完全对齐的，ui 要完全一样的」。
 *
 * ## 为什么另起一颗而不改 `VeneraChip`
 *
 * `VeneraChip` 是两端共享件（安卓小胶囊的既定形），改它会连坐手机端；
 * 而桌面（收藏/搜索两稿）用的是**圆角矩形块**：8dp 圆角、14dp 横向内边距、
 * 选中 = 主题色微底 + 1dp 主题色描边 + 主题色 SemiBold，未选 = 面板底 + 细描边 + 次要字色。
 * 桌面所有一级/二级选择器（收藏分段、文件夹、搜索源与筛选、探索模式与分类、视图切换、文件页分段）
 * 都改用这一颗，口径只此一处 —— 各处再各画一遍就是"两处都会漂"。
 *
 * @param badge 可选计数/角标（如文件夹数量、源收藏数），与文字之间留 6dp。
 */
@Composable
internal fun DesktopSelectionBlock(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
    accent: Color = VeneraTokens.color.primary,
) {
    val tokens = VeneraTokens
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(
                if (selected) accent.copy(alpha = 0.14f)
                else tokens.color.surfaceVariant.copy(alpha = 0.45f),
            )
            .border(
                width = 1.dp,
                color = if (selected) accent.copy(alpha = 0.48f)
                else tokens.color.outlineVariant.copy(alpha = 0.35f),
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = text,
            fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) accent else tokens.color.textSecondary,
            maxLines = 1,
        )
        if (!badge.isNullOrBlank()) {
            Text(
                text = badge,
                fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                fontWeight = VeneraType.control.weight,
                color = if (selected) accent.copy(alpha = 0.8f) else tokens.color.textTertiary,
                maxLines = 1,
            )
        }
    }
}

/**
 * 玻璃 chip（favorites-rework-）：半透白 + 发丝描边的胶囊选择件。
 *
 * 与 [DesktopSelectionBlock] 的分工：选块是面板上的实底件；玻璃 chip 浮在页面底色上
 * （收藏夹行 / 网络源夹行）。选中 = 主题色微底 + 主题色细描边 + 主题色字，
 * 未选 = 玻璃底 + 发丝线 + 次要字色。玻璃底取 [LocalChromePalette.railGlass]，
 * 与轨玻璃同一颗色（深浅两档自动跟随）。
 */
@Composable
internal fun DesktopGlassChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    val palette = com.venera.desktop.gallery.ui.LocalChromePalette.current
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(if (selected) tokens.color.primary.copy(alpha = 0.12f) else palette.railGlass)
            .border(
                1.dp,
                if (selected) tokens.color.primary.copy(alpha = 0.25f) else palette.hairline,
                shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            fontSize = VeneraType.bodySecondary.size, lineHeight = VeneraType.bodySecondary.lineHeight,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) tokens.color.primary else tokens.color.textSecondary,
            maxLines = 1,
        )
    }
}

/**
 * 玻璃空态卡（favorites-rework-）：居中一句读数的玻璃容器。
 * **只换容器不换词** —— 空态文案是成品句、被判据逐字钉着（「还没有收藏…」「正在读本地收藏…」），
 * 这里只负责把一行灰字升级成玻璃卡，措辞由调用点递入。
 */
@Composable
internal fun DesktopGlassNote(
    text: String,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    val palette = com.venera.desktop.gallery.ui.LocalChromePalette.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(palette.railGlass)
            .border(1.dp, palette.hairline, RoundedCornerShape(16.dp))
            .padding(horizontal = 32.dp, vertical = 28.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            fontSize = VeneraType.bodySecondary.size, lineHeight = VeneraType.bodySecondary.lineHeight,
            color = tokens.color.textTertiary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/**
 * 里那类**动作块**按钮（不是开关）。
 *
 * 统一（macOS 皮）：本颗改为**委托** [DesktopPushButton] —— 全站 80+ 处调用
 * （搜索「开始检索」、探索「重新取」、源管理、备份、详情等）因此一次收到 Mac 推压按钮的
 * 几何与色板（r6 / 白渐微影 / primary 系统蓝 / 按压 0.96），不再各自维持 r8 + 旧 token。
 *
 * 为什么委托而不是逐处替换：调用点太多，逐处改必然漂；委托保证"按钮只有一种形"。
 * 焦点环随旧实现退役（Mac 推压按钮不画 2dp 主色环，键盘焦点由系统 focus ring 承担）。
 */
@Composable
internal fun DesktopActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
) = DesktopPushButton(
    text = text,
    onClick = onClick,
    modifier = modifier,
    primary = primary,
)
