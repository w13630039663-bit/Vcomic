package com.venera.desktop.downloads

import com.venera.desktop.ui.theme.VeneraType
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.gallery.data.DesktopComicDownloadedChapter
import com.venera.desktop.gallery.ui.DesktopComicDownloadListing
import com.venera.desktop.gallery.ui.desktopByteLabel
import com.venera.desktop.gallery.ui.desktopComicDeleteArmedLabel
import com.venera.desktop.gallery.ui.desktopComicDeleteIdleLabel
import com.venera.desktop.gallery.ui.desktopComicDeleteRowKey
import com.venera.desktop.gallery.ui.desktopComicDownloadedPendingLine
import com.venera.desktop.gallery.ui.desktopComicDownloadedScreenHeader
import com.venera.desktop.gallery.data.desktopComicRepairLabel
import com.venera.desktop.gallery.data.desktopComicRepairShouldShow
import com.venera.desktop.ui.DesktopPageShell
import com.venera.desktop.ui.DesktopPageWidth

/**
 * 桌面「本机已下载」屏 —— `.rowq` 行形重做（令"不复用旧 UI"）。
 *
 * 一笔一行：占位缩略格（盘上话级树没有封面 url，按稿摆占位、不假造封面）→
 * 「第 N 话「题名」」+ 状态徽（已下全 ok / 缺 N 页 warn）+「写/期 页 · 体积」+
 * 缺页那行的「补齐这一话」`.mini`（只入队不动盘，判据原语）→ `.qops`：
 * 打开目录 `.ib` + 两步删除 `.ib`（未武=灰描边，已武=红 danger 且读屏文案换"再按一次"）。
 *
 * 屏不碰盘的老口径原样保留：三态表头句、删除两枚文案、补齐判据全走既有纯函数；
 * 打开目录的把手只点名，真正 `Desktop.open` 在壳。
 */
@Composable
internal fun DesktopComicDownloadsScreen(
    mode: WideScreenLayoutMode,
    /** 盘上那一棵树数回来的结果；`null` = 还没数回来（念另一句，不念"本机没有"）。 */
    listing: DesktopComicDownloadListing?,
    note: String?,
    /** 武装着的那一行的身份串；`null` = 没武装。 */
    armedKey: String?,
    onRepair: (DesktopComicDownloadedChapter) -> Unit,
    onDelete: (DesktopComicDownloadedChapter) -> Unit,
    /** 「打开这一话的目录」（壳拿 `desktopComicLocalChapter` 定树路再 `Desktop.open`）。 */
    onOpenChapterDir: (DesktopComicDownloadedChapter) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    embedded: Boolean = false,
) {
    val tokens = VeneraTokens
    val chapters = listing?.groups?.flatMap { it.chapters }.orEmpty()
    val body: @Composable () -> Unit = {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = listing?.let(::desktopComicDownloadedScreenHeader)
                    ?: desktopComicDownloadedPendingLine(),
                fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                color = tokens.color.textTertiary,
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            )
            if (chapters.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    // 空与塌两句同样由顶行那句 header 念（纯函数原话，屏不另写）。
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(chapters, key = ::desktopComicDeleteRowKey) { chapter ->
                        // 下载章节行补右键，沿用行内三枚真按钮的回调。
                        com.venera.desktop.ui.components.DesktopContextMenuArea(
                            items = {
                                listOf(
                                    com.venera.desktop.ui.components.DesktopMenuEntry.Item(
                                        label = "补齐",
                                        icon = Icons.Outlined.Refresh,
                                        onClick = { onRepair(chapter) },
                                    ),
                                    com.venera.desktop.ui.components.DesktopMenuEntry.Item(
                                        label = "打开文件夹",
                                        icon = Icons.Outlined.FolderOpen,
                                        onClick = { onOpenChapterDir(chapter) },
                                    ),
                                    com.venera.desktop.ui.components.DesktopMenuEntry.Divider,
                                    com.venera.desktop.ui.components.DesktopMenuEntry.Item(
                                        label = "删除",
                                        icon = Icons.Outlined.DeleteOutline,
                                        isDanger = true,
                                        onClick = { onDelete(chapter) },
                                    ),
                                )
                            },
                        ) {
                        DownloadedRow(
                            chapter = chapter,
                            armed = armedKey == desktopComicDeleteRowKey(chapter),
                            onRepair = { onRepair(chapter) },
                            onDelete = { onDelete(chapter) },
                            onOpenDir = { onOpenChapterDir(chapter) },
                        )
                        }
                    }
                }
            }
        }
    }
    if (embedded) {
        Box(modifier = modifier.fillMaxSize()) { body() }
        return
    }
    DesktopPageShell(
        mode = mode,
        title = "本机已下载",
        subtitle = null,
        note = note,
        onBack = onBack,
        modifier = modifier,
        width = DesktopPageWidth.Browse,
    ) {
        body()
    }
}

/** 稿 `.rowq`：占位缩略 + 题名/徽/页量 + 补齐 mini（缺页档）+ 打开/两步删图标把手。 */
@Composable
private fun DownloadedRow(
    chapter: DesktopComicDownloadedChapter,
    armed: Boolean,
    onRepair: () -> Unit,
    onDelete: () -> Unit,
    onOpenDir: () -> Unit,
) {
    val tokens = VeneraTokens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(tokens.color.surfaceVariant.copy(alpha = 0.22f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        // 话级树没有封面 url（盘上只有页与清单）——按稿 `.thumb` 摆占位格，题名才说真话。
        DqThumb(url = "")
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "第 ${chapter.order + 1} 话「${chapter.title}」 · ${chapter.comicId}",
                fontSize = VeneraType.bodySecondary.size, lineHeight = VeneraType.bodySecondary.lineHeight,
                color = tokens.color.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                if (chapter.complete) {
                    DqBadge("已下全", DqTone.ok)
                } else {
                    DqBadge("缺 ${chapter.expectedPages - chapter.writtenPages} 页", DqTone.warn)
                }
                Text(
                    text = "${chapter.writtenPages}/${chapter.expectedPages} 页 · ${desktopByteLabel(chapter.bytes)}",
                    fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                    color = tokens.color.textTertiary,
                )
            }
            // 「补齐」只有缺页那一档才摆（判据纯函数，一句不重写）。
            if (desktopComicRepairShouldShow(chapter)) {
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    DqMiniButton(desktopComicRepairLabel(chapter), onClick = onRepair, primary = true)
                }
            }
        }
        DqIconButton(icon = Icons.Outlined.FolderOpen, description = "打开这一话的目录", onClick = onOpenDir)
        DqIconButton(
            icon = Icons.Outlined.DeleteOutline,
            description = if (armed) {
                desktopComicDeleteArmedLabel(chapter.order + 1)
            } else {
                desktopComicDeleteIdleLabel(chapter.order + 1)
            },
            onClick = onDelete,
            danger = true,
        )
    }
}
