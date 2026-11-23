package com.venera.desktop.downloads

import com.venera.desktop.ui.theme.VeneraType
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.gallery.data.formatBytes
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 「本机已下载（图片）」屏 —— 设计稿 ③ 逐区对齐。
 *
 * 分组档与计数角住在枢纽页的 tab 行 gear 区（壳持有），这一屏只吃算好的 `groupMode` 与条目表。
 * **屏不碰盘**：扫描/删除都在壳的 IO 腿；删除两步口径沿用仓库纪律（第一次按只武装、
 * 点名"再按一次就删 N 张"，第二次才真删）。组头 = 折叠钮 + 文件夹图标 + 组名 + 张数角 + 路径
 * （等宽小字，与 `…/Venera/images/画师/` 那一格同语言）。
 */
@Composable
internal fun DesktopLocalImageLibraryScreen(
    /** `null` = 还没数回来（念"正在数盘上那格"，不念"一张都没有"）。 */
    items: List<DesktopLocalImageItem>?,
    /** 上一次删除的回执（壳现算，屏照原话摆）。 */
    note: String?,
    /** 落点根（页眉那句 + "打开落点"用；相对组名在它之下拼提示路径）。 */
    rootPath: String,
    /** 分组档（枢纽 tab 行 gear 区切，状态在壳）。 */
    groupMode: DesktopImageGroupMode,
    /** 真删（壳 IO）。交回被勾中的绝对路径集。 */
    onDelete: (List<String>) -> Unit,
    /** 打开那一格目录（壳拿 Desktop.open）。 */
    onOpenFolder: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    val selected = remember { mutableStateMapOf<String, Boolean>() }
    var deleteArmed by remember { mutableStateOf(false) }
    var collapsed by remember { mutableStateOf(setOf<String>()) }

    val list = items.orEmpty()
    val picked = selected.keys.filter { selected[it] == true }
    Column(modifier = modifier.fillMaxSize()) {
        // ── 页眉行：读数 + 打开落点（分组分段器在枢纽 tab 行，不在这里重复摆）──
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = when {
                    items == null -> "正在数本机已下载的图片…"
                    list.isEmpty() -> "这一格还一张都没有——去图墙或 Pixiv 点「下载原图」。"
                    else -> "共 ${list.size} 张 · ${formatBytes(list.sumOf { it.bytes })}"
                },
                fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                color = tokens.color.textTertiary,
                modifier = Modifier.weight(1f),
            )
            DqMiniButton(text = "打开文件夹", onClick = { onOpenFolder(rootPath) })
        }
        note?.let {
            Text(
                text = it,
                fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                color = tokens.color.textSecondary,
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            )
        }
        // ── 多选那一条（勾了才出）：已选 chip + 两步删 + 取消（设计稿 tbar gear 那一排）──
        if (picked.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .height(26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(tokens.color.primary.copy(alpha = 0.14f))
                        .border(0.5.dp, tokens.color.primary.copy(alpha = 0.28f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "已选 ${picked.size}",
                        fontSize = VeneraType.micro.size,
                        lineHeight = VeneraType.micro.lineHeight,
                        fontWeight = VeneraType.cardTitle.weight,
                        color = tokens.color.primary,
                    )
                }
                DqDangerButton(
                    text = if (deleteArmed) "再按一次就删 ${picked.size} 张" else "删除所选",
                    icon = Icons.Outlined.DeleteOutline,
                    onClick = {
                        if (deleteArmed) {
                            onDelete(picked)
                            selected.clear()
                            deleteArmed = false
                        } else {
                            deleteArmed = true
                        }
                    },
                )
                DqMiniButton(
                    text = "取消",
                    onClick = {
                        selected.clear()
                        deleteArmed = false
                    },
                )
                Text(
                    text = "${formatBytes(picked.mapNotNull { p -> list.firstOrNull { it.path == p }?.bytes }.sum())}",
                    fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                    color = tokens.color.textTertiary,
                )
            }
        }
        if (items != null && list.isNotEmpty()) {
            val groups = groupDesktopLocalImages(list, groupMode)
            val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT)
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                groups.forEach { (key, groupItems) ->
                    item(key = "grp:$key", span = { GridItemSpan(maxLineSpan) }) {
                        val folded = key in collapsed
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = if (folded) Icons.Outlined.ChevronRight else Icons.Outlined.ExpandMore,
                                contentDescription = if (folded) "展开这一组" else "折叠这一组",
                                tint = tokens.color.textTertiary,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        collapsed = if (folded) collapsed + key else collapsed - key
                                    },
                            )
                            Icon(
                                imageVector = Icons.Outlined.Folder,
                                contentDescription = null,
                                tint = tokens.color.textTertiary,
                                modifier = Modifier.size(15.dp),
                            )
                            Text(
                                text = desktopImageGroupLabel(key, groupMode),
                                fontSize = VeneraType.bodySecondary.size, lineHeight = VeneraType.bodySecondary.lineHeight,
                                fontWeight = VeneraType.cardTitle.weight,
                                color = tokens.color.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${groupItems.size} 张",
                                fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                                color = tokens.color.textTertiary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(tokens.color.surfaceVariant.copy(alpha = 0.55f))
                                    .padding(horizontal = 8.dp, vertical = 1.dp),
                            )
                            val groupPath = if (groupMode == DesktopImageGroupMode.ARTIST && key.isNotBlank()) {
                                File(rootPath, key).absolutePath
                            } else {
                                ""
                            }
                            if (groupPath.isNotBlank()) {
                                Text(
                                    text = groupPath,
                                    fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                                    fontFamily = FontFamily.Monospace,
                                    color = tokens.color.textTertiary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                            } else {
                                Box(modifier = Modifier.weight(1f))
                            }
                            DqMiniButton(text = "打开", onClick = {
                                onOpenFolder(groupPath.ifBlank { rootPath })
                            })
                        }
                    }
                    if (key !in collapsed) {
                        items(groupItems, key = { it.path }) { item ->
                            LocalImageCard(
                                item = item,
                                selected = selected[item.path] == true,
                                dateLabel = dateFmt.format(Date(item.mtime)),
                                onToggle = {
                                    deleteArmed = false
                                    selected[item.path] = !(selected[item.path] == true)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 一张卡：预览（视频给档名角标不装能播）+ 左上勾选 + 题名 + 尺寸·体积·日期。整卡点击 = 勾选（多选语义）。 */
@Composable
private fun LocalImageCard(
    item: DesktopLocalImageItem,
    selected: Boolean,
    dateLabel: String,
    onToggle: () -> Unit,
) {
    val tokens = VeneraTokens
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) tokens.color.primary else tokens.color.surfaceVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(12.dp),
            )
            .background(tokens.color.surfaceVariant.copy(alpha = 0.18f))
            .clickable(onClick = onToggle),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(tokens.color.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            val ext = item.fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
            if (ext == "mp4" || ext == "webm" || ext == "mkv") {
                Text(
                    text = "视频 · ${ext.uppercase(Locale.ROOT)}",
                    fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                    color = tokens.color.textTertiary,
                )
            } else {
                AsyncImage(
                    model = File(item.path),
                    contentDescription = item.fileName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
            }
            // 勾选角标（左上，设计稿 .card .cb）
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .size(19.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (selected) tokens.color.primary else Color.Black.copy(alpha = 0.38f))
                    .border(
                        1.5.dp,
                        if (selected) tokens.color.primary else Color.White.copy(alpha = 0.65f),
                        RoundedCornerShape(6.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "已选",
                        tint = Color.White,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = desktopLocalImageTitle(item),
                fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                color = tokens.color.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    if (item.dimensions.isNotBlank()) {
                        append(item.dimensions)
                        append(" · ")
                    }
                    append(formatBytes(item.bytes))
                    append(" · ")
                    append(dateLabel)
                },
                fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                color = tokens.color.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}
