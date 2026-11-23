package com.venera.desktop.downloads

import com.venera.desktop.ui.theme.VeneraType
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.gallery.data.DesktopComicDownloadedChapter
import com.venera.desktop.gallery.ui.desktopByteLabel
import com.venera.desktop.ui.VeneraCheckbox
import com.venera.desktop.ui.components.desktopCoverFloat
import com.venera.desktop.ui.components.desktopUiFloat
import java.io.File

/** 现代漫画卡片（加载作品真实封面、多话聚合展示、右上角更多选项菜单） */
@Composable
internal fun ComicBookGridCard(
    card: PipelineComicCard,
    isSelecting: Boolean,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onClick: () -> Unit,
    onRepair: () -> Unit,
    onOpenDetail: (() -> Unit)? = null,
    onOpenFolder: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    val tokens = VeneraTokens
    var menuExpanded by remember { mutableStateOf(false) }

    // 本地书卡补右键（三点菜单仍留）。危险项用 Divider 隔开、沿用既有
    // onDelete 确认链，不新造第二套删除。按对象裁剪，不摆收藏/搜索这类本地书没有的动作。
    com.venera.desktop.ui.components.DesktopContextMenuArea(
        items = {
            buildList {
                add(
                    com.venera.desktop.ui.components.DesktopMenuEntry.Item(
                        label = "打开",
                        shortcut = "Enter",
                        icon = Icons.Outlined.OpenInNew,
                        onClick = onClick,
                    ),
                )
                if (onOpenDetail != null) {
                    add(
                        com.venera.desktop.ui.components.DesktopMenuEntry.Item(
                            label = "查看详情",
                            icon = Icons.Outlined.Visibility,
                            onClick = { onOpenDetail.invoke() },
                        ),
                    )
                }
                if (onOpenFolder != null) {
                    add(
                        com.venera.desktop.ui.components.DesktopMenuEntry.Item(
                            label = "打开文件夹",
                            icon = Icons.Outlined.FolderOpen,
                            onClick = { onOpenFolder.invoke() },
                        ),
                    )
                }
                if (onDelete != null) {
                    add(com.venera.desktop.ui.components.DesktopMenuEntry.Divider)
                    add(
                        com.venera.desktop.ui.components.DesktopMenuEntry.Item(
                            label = "删除",
                            icon = Icons.Outlined.Delete,
                            isDanger = true,
                            onClick = { onDelete.invoke() },
                        ),
                    )
                }
            }
        },
    ) {
    com.venera.desktop.ui.components.DesktopFloatingCard(
        cornerRadius = 12.dp,
        surface = tokens.color.surface,
        selected = isSelected,
        selectedColor = tokens.color.primary,
        modifier = Modifier
            .width(160.dp)
            .clickable(onClick = onClick),
    ) { floatState ->
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(tokens.color.surfaceVariant.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                if (card.coverModel != null) {
                    AsyncImage(
                        model = card.coverModel,
                        contentDescription = card.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .desktopCoverFloat(floatState),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(tokens.color.surfaceContainerHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = card.title.take(1).uppercase(),
                            fontSize = VeneraType.statNumber.size, lineHeight = VeneraType.statNumber.lineHeight,
                            fontWeight = VeneraType.cardTitle.weight,
                            color = tokens.color.textTertiary,
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .desktopUiFloat(floatState),
                ) {
                    if (card is PipelineComicCard.DownloadedComicBookCard) {
                        if (card.isComplete) {
                            DqBadge(label = "齐 ${card.group.chapters.size} 话", tone = DqTone.ok, filled = true)
                        } else {
                            DqBadge(label = "缺 ${card.group.incompleteCount} 话", tone = DqTone.fail, filled = true)
                        }
                    } else if (card is PipelineComicCard.LocalImportedComicCard) {
                        DqBadge(label = "导入", tone = tokens.color.primary, filled = true)
                    }
                }

                // 右上角更多操作菜单
                if (!isSelecting) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(5.dp)
                            .desktopUiFloat(floatState),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f))
                                .clickable { menuExpanded = true },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "更多选项",
                                tint = Color.White,
                                modifier = Modifier.size(15.dp),
                            )
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            if (onOpenDetail != null) {
                                DropdownMenuItem(
                                    text = { Text("查看漫画详情") },
                                    leadingIcon = { Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                    onClick = {
                                        menuExpanded = false
                                        onOpenDetail()
                                    },
                                )
                            }
                            if (onOpenFolder != null) {
                                DropdownMenuItem(
                                    text = { Text("打开所在文件夹") },
                                    leadingIcon = { Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                    onClick = {
                                        menuExpanded = false
                                        onOpenFolder()
                                    },
                                )
                            }
                            if (onDelete != null) {
                                DropdownMenuItem(
                                    text = { Text("删除此漫画", color = tokens.color.error) },
                                    leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = tokens.color.error, modifier = Modifier.size(18.dp)) },
                                    onClick = {
                                        menuExpanded = false
                                        onDelete()
                                    },
                                )
                            }
                        }
                    }
                }

                if (isSelecting) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp),
                    ) {
                        VeneraCheckbox(
                            checked = isSelected,
                            onCheckedChange = { onToggleSelect() },
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
                Text(
                    text = card.title,
                    fontSize = VeneraType.bodySecondary.size, lineHeight = VeneraType.bodySecondary.lineHeight,
                    fontWeight = VeneraType.cardTitle.weight,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (card.bytes > 0) card.subtitle else "${card.pageCount} 页",
                        fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                        color = tokens.color.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
    }
}

/** 现代图片卡片（展示已下载的真实本地图片大图、来源胶囊与更多操作菜单） */
@Composable
internal fun ImageGridCard(
    image: DesktopLocalImageItem,
    isSelecting: Boolean,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onClick: () -> Unit,
    onOpenDetail: (() -> Unit)? = null,
    onCopy: (() -> Unit)? = null,
    onOpenFolder: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    val tokens = VeneraTokens
    val shape = RoundedCornerShape(12.dp)
    var menuExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .width(160.dp)
            .clip(shape)
            .background(tokens.color.surface)
            .border(
                1.dp,
                if (isSelected) tokens.color.primary else tokens.color.outlineVariant.copy(alpha = 0.35f),
                shape,
            )
            .clickable(onClick = onClick),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(tokens.color.surfaceVariant.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = File(image.path),
                    contentDescription = image.fileName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )

                // 来源站点徽标与多图胶囊
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (image.pageCount > 1) {
                        DqBadge(
                            label = "${image.pageCount}P",
                            tone = DqTone.warn,
                            filled = true,
                        )
                    }
                    DqBadge(
                        label = image.siteHint,
                        tone = tokens.color.primary,
                        filled = true,
                    )
                }

                // 右上角更多操作菜单
                if (!isSelecting) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(5.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f))
                                .clickable { menuExpanded = true },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "更多选项",
                                tint = Color.White,
                                modifier = Modifier.size(15.dp),
                            )
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            if (onOpenDetail != null) {
                                DropdownMenuItem(
                                    text = { Text("查看作品详情") },
                                    leadingIcon = { Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                    onClick = {
                                        menuExpanded = false
                                        onOpenDetail()
                                    },
                                )
                            }
                            if (onCopy != null) {
                                DropdownMenuItem(
                                    text = { Text("复制图片") },
                                    leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                    onClick = {
                                        menuExpanded = false
                                        onCopy()
                                    },
                                )
                            }
                            if (onOpenFolder != null) {
                                DropdownMenuItem(
                                    text = { Text("打开所在文件夹") },
                                    leadingIcon = { Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                    onClick = {
                                        menuExpanded = false
                                        onOpenFolder()
                                    },
                                )
                            }
                            if (onDelete != null) {
                                DropdownMenuItem(
                                    text = { Text("删除图片", color = tokens.color.error) },
                                    leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = tokens.color.error, modifier = Modifier.size(18.dp)) },
                                    onClick = {
                                        menuExpanded = false
                                        onDelete()
                                    },
                                )
                            }
                        }
                    }
                }

                if (isSelecting) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp),
                    ) {
                        VeneraCheckbox(
                            checked = isSelected,
                            onCheckedChange = { onToggleSelect() },
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(
                    text = desktopLocalImageTitle(image),
                    fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                    fontWeight = VeneraType.cardTitle.weight,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(3.dp))
                val metaText = if (image.pageCount > 1) {
                    "多图 · ${image.pageCount}P · ${desktopByteLabel(image.bytes)}"
                } else if (image.dimensions.isNotBlank()) {
                    "${image.dimensions} · ${desktopByteLabel(image.bytes)}"
                } else {
                    desktopByteLabel(image.bytes)
                }
                Text(
                    text = metaText,
                    fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                    color = tokens.color.textTertiary,
                    maxLines = 1,
                )
            }
        }
    }
}

/** 漫画多目录展开对话框：列出该漫画的所有已下载章节并支持直达阅读与独立维护 */
@Composable
internal fun ComicChaptersDialog(
    group: DownloadedComicGroup,
    onDismiss: () -> Unit,
    onOpenChapter: (DesktopComicDownloadedChapter) -> Unit,
    onRepairChapter: (DesktopComicDownloadedChapter) -> Unit,
    onOpenChapterDir: (DesktopComicDownloadedChapter) -> Unit,
    onDeleteChapter: (DesktopComicDownloadedChapter) -> Unit,
    onOpenComicDetail: (() -> Unit)? = null,
    onDeleteGroup: (() -> Unit)? = null,
) {
    val tokens = VeneraTokens
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(520.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(tokens.color.surface)
                .border(1.dp, tokens.color.outlineVariant.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                .padding(20.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 顶栏信息与源站跳转
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = group.title,
                        fontSize = VeneraType.cardTitle.size, lineHeight = VeneraType.cardTitle.lineHeight,
                        fontWeight = VeneraType.cardTitle.weight,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                    )
                    if (onOpenComicDetail != null) {
                        DqMiniButton(
                            text = "查看源站详情 ›",
                            primary = false,
                            onClick = onOpenComicDetail,
                        )
                    }
                }

                Text(
                    text = "已下载 ${group.chapters.size} 话 · 共 ${group.totalPages} 页 · ${desktopByteLabel(group.totalBytes)}",
                    fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                    color = tokens.color.textSecondary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )

                // 章节列表
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    group.chapters.forEach { ch ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(tokens.color.surfaceVariant.copy(alpha = 0.35f))
                                .border(0.5.dp, tokens.color.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .clickable { onOpenChapter(ch) }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "第 ${ch.order + 1} 话 · ${ch.title}",
                                fontSize = VeneraType.bodySecondary.size, lineHeight = VeneraType.bodySecondary.lineHeight,
                                fontWeight = VeneraType.cardTitle.weight,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${ch.writtenPages} 页 · ${desktopByteLabel(ch.bytes)}",
                                fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                                color = tokens.color.textTertiary,
                            )
                        }

                        if (ch.complete) {
                            DqBadge(label = "已齐", tone = DqTone.ok, filled = true)
                        } else {
                            val missing = (ch.expectedPages - ch.writtenPages).coerceAtLeast(1)
                            DqBadge(label = "缺 $missing 页", tone = DqTone.fail, filled = true)
                            DqMiniButton(
                                text = "补齐",
                                primary = false,
                                onClick = { onRepairChapter(ch) },
                            )
                        }

                        DqIconButton(
                            icon = Icons.Outlined.FolderOpen,
                            description = "打开目录",
                            onClick = { onOpenChapterDir(ch) },
                        )

                        DqIconButton(
                            icon = Icons.Outlined.Delete,
                            description = "删除此话",
                            danger = true,
                            onClick = { onDeleteChapter(ch) },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onDeleteGroup != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(tokens.color.error.copy(alpha = 0.15f))
                            .border(1.dp, tokens.color.error.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                            .clickable(onClick = onDeleteGroup)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text("删除整本漫画", color = tokens.color.error, fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight, fontWeight = VeneraType.cardTitle.weight)
                    }
                } else Spacer(modifier = Modifier.width(1.dp))

                DqMiniButton(
                    text = "关闭",
                    primary = true,
                    onClick = onDismiss,
                )
            }
        }
    }
}
}
