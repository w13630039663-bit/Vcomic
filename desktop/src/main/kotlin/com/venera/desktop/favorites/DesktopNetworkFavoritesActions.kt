package com.venera.desktop.favorites

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.settings.DesktopI18n

internal const val DESKTOP_FAVORITE_NOTE_MAX_LEN: Int = 200

/**
 * 网络收藏本地兜底搜索：当站源未提供服务端 `favorites.search` 时，
 * 对已加载的收藏条目按标题、副标题与标签做大小写不敏感过滤。
 */
internal fun <T> desktopFilterNetworkFavorites(
    items: List<T>,
    query: String,
    titleOf: (T) -> String,
    subtitleOf: (T) -> String = { "" },
    tagsOf: (T) -> List<String> = { emptyList() },
): List<T> {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return items
    return items.filter { item ->
        titleOf(item).lowercase().contains(needle) ||
            subtitleOf(item).lowercase().contains(needle) ||
            tagsOf(item).any { it.lowercase().contains(needle) }
    }
}

/**
 * 收藏弹窗内复用的「收藏备注」输入框（限制 200 字符，对齐 EH favnote 上限）。
 */
@Composable
internal fun DesktopFavoriteNoteInput(
    note: String,
    onNoteChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = note,
            onValueChange = { onNoteChange(it.take(DESKTOP_FAVORITE_NOTE_MAX_LEN)) },
            label = { Text(DesktopI18n.t("收藏备注（可选）", "Favorite Note (Optional)")) },
            maxLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "${note.length}/$DESKTOP_FAVORITE_NOTE_MAX_LEN",
            fontSize = 11.sp,
            color = tokens.color.textTertiary,
            modifier = Modifier.align(Alignment.End).padding(top = 2.dp),
        )
    }
}

/**
 * 网络收藏批量移动的目标文件夹选择弹窗。
 */
@Composable
internal fun DesktopNetworkFavoritesMoveDialog(
    selectedCount: Int,
    folders: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onConfirm: (targetFolderId: String) -> Unit,
) {
    val selectable = remember(folders) { folders.filterNot { it.first == "-1" } }
    var picked by remember(selectable) { mutableStateOf(selectable.firstOrNull()?.first.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = DesktopI18n.t(
                    "移动 $selectedCount 项到收藏夹",
                    "Move $selectedCount items to folder",
                ),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(selectable, key = { it.first }) { (id, name) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { picked = id }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = picked == id, onClick = { picked = id })
                            Text(text = name, fontSize = 13.sp)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        },
        confirmButton = {
            TextButton(
                enabled = picked.isNotEmpty(),
                onClick = { onConfirm(picked) },
            ) {
                Text(DesktopI18n.t("确认移动", "Move"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(DesktopI18n.t("取消", "Cancel"))
            }
        },
    )
}
