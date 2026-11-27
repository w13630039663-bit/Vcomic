package com.venera.desktop.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.settings.DesktopI18n

/** 通用站源种子条目模型（由支持 `comic.torrents` 的站源返回）。 */
internal data class DesktopComicTorrentItem(
    val name: String,
    val url: String,
    val size: String = "",
    val posted: String = "",
    val seeds: String = "0",
    val peers: String = "0",
    val downloads: String = "0",
    val uploader: String = "",
)

/**
 * 通用种子列表弹窗：展示站源返回的 `.torrent` 列表，支持下载与复制链接。
 */
@Composable
internal fun DesktopComicTorrentDialog(
    loading: Boolean,
    error: String?,
    items: List<DesktopComicTorrentItem>,
    onRetry: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onCopyUrl: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = VeneraTokens
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = DesktopI18n.t("种子下载", "Torrents"),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 420.dp),
            ) {
                when {
                    loading -> {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(strokeWidth = 2.dp)
                            Spacer(Modifier.padding(horizontal = 6.dp))
                            Text(
                                text = DesktopI18n.t("正在加载种子列表…", "Loading torrents..."),
                                fontSize = 13.sp,
                                color = tokens.color.textSecondary,
                            )
                        }
                    }
                    error != null -> {
                        Text(
                            text = error,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = onRetry) {
                            Text(DesktopI18n.t("重试", "Retry"))
                        }
                    }
                    items.isEmpty() -> {
                        Text(
                            text = DesktopI18n.t("暂无可用种子", "No torrents available"),
                            fontSize = 13.sp,
                            color = tokens.color.textSecondary,
                            modifier = Modifier.padding(vertical = 20.dp),
                        )
                    }
                    else -> {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            items(items, key = { it.url }) { item ->
                                DesktopComicTorrentRow(
                                    item = item,
                                    onOpenUrl = { onOpenUrl(item.url) },
                                    onCopyUrl = { onCopyUrl(item.url) },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(DesktopI18n.t("关闭", "Close"))
            }
        },
    )
}

@Composable
private fun DesktopComicTorrentRow(
    item: DesktopComicTorrentItem,
    onOpenUrl: () -> Unit,
    onCopyUrl: () -> Unit,
) {
    val tokens = VeneraTokens
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .border(1.dp, tokens.color.textTertiary.copy(alpha = 0.25f), shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = item.name,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = tokens.color.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val meta = buildList {
            if (item.size.isNotBlank()) add(item.size)
            add("做种 ${item.seeds} / 下载 ${item.downloads}")
            if (item.posted.isNotBlank()) add(item.posted)
        }.joinToString(" · ")
        Text(
            text = meta,
            fontSize = 11.sp,
            color = tokens.color.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCopyUrl) {
                Text(DesktopI18n.t("复制链接", "Copy Link"), fontSize = 12.sp)
            }
            TextButton(onClick = onOpenUrl) {
                Text(DesktopI18n.t("下载种子", "Download"), fontSize = 12.sp)
            }
        }
    }
}
