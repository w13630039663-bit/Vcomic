package com.venera.desktop.ui.components

import com.venera.desktop.ui.theme.VeneraType
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.desktop.gallery.ui.GalleryTokens

/**
 * 下拉菜单行的**桌面单源**（提升自画廊顶栏的 TopbarMenuItem）。
 *
 * - 32dp 行高、label 左、快捷键右，色面走 [GalleryTokens]（随深浅取档）；
 * - 危险项 [isDanger] 走 `ToneDanger`（与右键菜单 DesktopMenuEntry 同一语义）；
 * - 形态统一口径：m3 `DropdownMenu` 做容器（定位 / 收回成熟），行视觉一律从这里走 ——
 *   从前七个域各写一遍 DropdownMenuItem，行高与色面各自为政。
 */
@Composable
internal fun DesktopMenuRow(
    label: String,
    shortcut: String? = null,
    onDismiss: () -> Unit,
    icon: String? = null,
    isDanger: Boolean = false,
) {
    DropdownMenuItem(
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (icon != null) {
                    Text(
                        text = icon,
                        fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                        color = if (isDanger) GalleryTokens.Color.ToneDanger
                        else GalleryTokens.Color.TextSecondary,
                    )
                }
                Text(
                    text = label,
                    fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                    fontWeight = if (isDanger) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isDanger) GalleryTokens.Color.ToneDanger
                    else GalleryTokens.Color.TextSecondary,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                if (shortcut != null) {
                    Text(
                        text = shortcut,
                        fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                        color = GalleryTokens.Color.TextTertiary,
                        maxLines = 1,
                    )
                }
            }
        },
        onClick = onDismiss,
        modifier = Modifier.height(32.dp),
    )
}
