package com.venera.desktop.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.settings.DesktopI18n
import com.venera.engineprobe.EngineSession

/**
 * 搜索页内自包含的「快速搜索书签」完整面板（含存为书签按钮、书签胶囊条与命名弹窗）。
 */
@Composable
internal fun DesktopSearchBookmarksShelf(
    sourceKey: String,
    sourceLabel: String,
    isAggregate: Boolean,
    query: String,
    filterGroups: List<EngineSession.SearchOption>,
    filterValues: List<String?>,
    onApplyBookmark: (DesktopSearchBookmark) -> Unit,
    modifier: Modifier = Modifier,
) {
    var bookmarks by remember { mutableStateOf(desktopSearchBookmarksReadFile()) }
    var showSaveDialog by remember { mutableStateOf(false) }
    val canSave = query.trim().isNotEmpty() || filterValues.any { !it.isNullOrBlank() }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        DesktopSearchSaveBookmarkButton(
            enabled = canSave,
            onClick = { showSaveDialog = true },
        )
        if (bookmarks.isNotEmpty()) {
            DesktopSearchBookmarksRow(
                bookmarks = bookmarks,
                onPick = onApplyBookmark,
                onTogglePin = { id ->
                    val next = desktopSearchBookmarksTogglePin(bookmarks, id)
                    bookmarks = next
                    desktopSearchBookmarksWriteFile(next)
                },
                onDelete = { id ->
                    val next = desktopSearchBookmarksDelete(bookmarks, id)
                    bookmarks = next
                    desktopSearchBookmarksWriteFile(next)
                },
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (showSaveDialog) {
        val initial = desktopSearchBookmarkDefaultName(query, sourceLabel, isAggregate)
        DesktopSearchBookmarkNameDialog(
            initialName = initial,
            onDismiss = { showSaveDialog = false },
            onConfirm = { name ->
                val selectedOpts = buildMap<String, List<String>> {
                    filterGroups.forEachIndexed { idx, group ->
                        val v = filterValues.getOrNull(idx)?.trim().orEmpty()
                        if (v.isNotEmpty()) put(group.label, listOf(v))
                    }
                }
                val entry = DesktopSearchBookmark(
                    id = "bm_${System.currentTimeMillis()}",
                    name = name,
                    sourceKey = sourceKey,
                    isAggregate = isAggregate,
                    keyword = query.trim(),
                    selectedOptions = selectedOpts,
                    createdAt = System.currentTimeMillis(),
                )
                val next = desktopSearchBookmarksAdd(bookmarks, entry)
                bookmarks = next
                desktopSearchBookmarksWriteFile(next)
                showSaveDialog = false
            },
        )
    }
}
@Composable
internal fun DesktopSearchSaveBookmarkButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    val shape = RoundedCornerShape(8.dp)
    val surfaceBg = MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier = modifier
            .height(34.dp)
            .clip(shape)
            .background(
                if (enabled) surfaceBg.copy(alpha = 0.72f)
                else surfaceBg.copy(alpha = 0.35f),
            )
            .border(
                width = 1.dp,
                color = tokens.color.textTertiary.copy(alpha = if (enabled) 0.45f else 0.2f),
                shape = shape,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.BookmarkAdd,
                contentDescription = DesktopI18n.t("存为书签", "Save Bookmark"),
                tint = if (enabled) tokens.color.textPrimary else tokens.color.textTertiary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = DesktopI18n.t("存为书签", "Bookmark"),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (enabled) tokens.color.textPrimary else tokens.color.textTertiary,
            )
        }
    }
}

/**
 * 搜索页展示的「搜索书签」横排胶囊条。
 * 支持点击一键回填搜索、置顶切换与删除。
 */
@Composable
internal fun DesktopSearchBookmarksRow(
    bookmarks: List<DesktopSearchBookmark>,
    onPick: (DesktopSearchBookmark) -> Unit,
    onTogglePin: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (bookmarks.isEmpty()) return
    val tokens = VeneraTokens
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = DesktopI18n.t("搜索书签", "Bookmarks"),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = tokens.color.textSecondary,
        )
        bookmarks.forEach { item ->
            DesktopSearchBookmarkChip(
                bookmark = item,
                onPick = { onPick(item) },
                onTogglePin = { onTogglePin(item.id) },
                onDelete = { onDelete(item.id) },
            )
        }
    }
}

@Composable
private fun DesktopSearchBookmarkChip(
    bookmark: DesktopSearchBookmark,
    onPick: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
) {
    val tokens = VeneraTokens
    val shape = RoundedCornerShape(999.dp)
    val borderColor = if (bookmark.pinned) {
        tokens.color.primary.copy(alpha = 0.55f)
    } else {
        tokens.color.textTertiary.copy(alpha = 0.35f)
    }
    Row(
        modifier = Modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
            .border(1.dp, borderColor, shape)
            .clickable(onClick = onPick)
            .padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.PushPin,
            contentDescription = DesktopI18n.t("置顶", "Pin"),
            tint = if (bookmark.pinned) tokens.color.primary else tokens.color.textTertiary,
            modifier = Modifier
                .size(13.dp)
                .clickable(onClick = onTogglePin),
        )
        Text(
            text = bookmark.name,
            fontSize = 12.sp,
            color = tokens.color.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            imageVector = Icons.Outlined.Close,
            contentDescription = DesktopI18n.t("删除", "Delete"),
            tint = tokens.color.textTertiary,
            modifier = Modifier
                .size(13.dp)
                .clickable(onClick = onDelete),
        )
    }
}

/**
 * 保存/重命名搜索书签的轻量弹窗。
 */
@Composable
internal fun DesktopSearchBookmarkNameDialog(
    initialName: String,
    title: String = DesktopI18n.t("存为搜索书签", "Save Search Bookmark"),
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(DesktopI18n.t("书签名称", "Bookmark Name")) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.trim().isNotEmpty(),
                onClick = { onConfirm(name.trim()) },
            ) {
                Text(DesktopI18n.t("保存", "Save"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(DesktopI18n.t("取消", "Cancel"))
            }
        },
    )
}
