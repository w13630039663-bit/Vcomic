package com.venera.desktop.gallery.ui.selection

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 画廊域的悬浮坞：左侧状态区 + 右侧四项动作（批量下载 / 收藏 / 复制链接 / 更多）。
 *
 * 参数与 `component-spec.md:11-23` 同名；动作是否可点由调用点按真实数据决定
 * （例如没有端口时整颗坞不挂，而不是挂一个按不动的按钮）。
 */
@Composable
internal fun GalleryMultiSelectDock(
    selectedCount: Int,
    totalCount: Int,
    onSelectAll: () -> Unit,
    onInvertSelect: () -> Unit,
    onClearSelect: () -> Unit,
    onBatchDownload: () -> Unit,
    onBatchFavorite: () -> Unit,
    onCopyLinks: () -> Unit,
    onOpenMoreModal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DesktopSelectionDock(
        selectedCount = selectedCount,
        totalCount = totalCount,
        onSelectAll = onSelectAll,
        onInvertSelect = onInvertSelect,
        onClearSelect = onClearSelect,
        modifier = modifier,
    ) {
        DockActionPill(
            text = "批量下载 ($selectedCount)",
            icon = Icons.Outlined.Download,
            primary = true,
            onClick = onBatchDownload,
        )
        DockActionPill(
            text = "收藏",
            icon = Icons.Outlined.FavoriteBorder,
            onClick = onBatchFavorite,
        )
        DockIconAction(
            icon = Icons.Outlined.ContentCopy,
            description = "复制所选链接",
            onClick = onCopyLinks,
        )
        DockIconAction(
            icon = Icons.Rounded.MoreHoriz,
            description = "更多批量操作",
            onClick = onOpenMoreModal,
        )
    }
}
