package com.venera.desktop.queue

import com.venera.desktop.ui.theme.VeneraType
import com.venera.desktop.gallery.ui.GalleryTokens
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.downloads.DqBadge
import com.venera.desktop.downloads.DqBar
import com.venera.desktop.downloads.DqErrCard
import com.venera.desktop.downloads.DqFileList
import com.venera.desktop.downloads.DqIconButton
import com.venera.desktop.downloads.DqMiniButton
import com.venera.desktop.downloads.DqSectionHead
import com.venera.desktop.downloads.DqThreadRow
import com.venera.desktop.downloads.DqThumb
import com.venera.desktop.downloads.DqTone
import com.venera.desktop.downloads.DqUrlBox
import com.venera.desktop.gallery.data.DesktopComicQueueStatus
import com.venera.desktop.gallery.data.DesktopComicQueueTask
import com.venera.desktop.gallery.ui.DESKTOP_COMIC_QUEUE_REMOVE_LABEL
import com.venera.desktop.gallery.ui.DESKTOP_COMIC_QUEUE_STOP_LABEL
import com.venera.desktop.gallery.ui.desktopComicQueueCanStop
import com.venera.desktop.gallery.ui.desktopComicQueueScreenHeader
import com.venera.desktop.gallery.ui.desktopComicQueueStatusText
import com.venera.desktop.ui.DesktopPageShell
import com.venera.desktop.ui.DesktopPageWidth

/**
 * 桌面下载队列屏 —— ①② 的件集装配（令"完全按设计稿，不复用旧 UI"）。
 *
 * 行（`.rowq`）：缩略 + 题名·话名 + 状态徽（`.badge` 五档色）+ "x/y 页" + `.bar` +
 * `.ib` 图标把手（停/撤两枚，desc 用原两枚文案常量）+ `.exp` 展开钮。
 * 展开（`.detail`）双列：左 = 进度行 + `.bar` + `.urlbox` 源信息（自带复制）+ `.errcard`
 * 失败原因 + `.mini` 动作排 + `.thread` 线程行；右 = `.filelist` 带框清单。
 *
 * 数据口径的两句实话：队列账只有**页档**（逐任务字节账未建）⇒ 剩余时间/传输速度做页档估算并
 * 在格底明说；写侧没有 FAILED→重排的口子 ⇒ 稿上"重试失败页"**不摆**（假把手纪律），
 * 稿上行的复选框（`.cb`）同理 —— 页内没有批量动作可挂，不摆按了没反应的框。
 */
@Composable
internal fun DesktopComicQueueScreen(
    mode: WideScreenLayoutMode,
    tasks: List<DesktopComicQueueTask>,
    /** 表**读回来过没有**：`false` 时念「正在读那张表」而不是「本机队列空」。 */
    primed: Boolean,
    note: String?,
    /** 并发档（档二 5b-3）：装配点读盘上 pref_download_threads 递进来；默认 1 = 旧形状。 */
    concurrency: Int = 1,
    /** 「停这一笔」（原语名沿用；屏上是图标把手，desc 念这句）。 */
    onStop: (DesktopComicQueueTask) -> Unit,
    onRemove: (DesktopComicQueueTask) -> Unit,
    expandedTaskId: String?,
    onToggleExpand: (DesktopComicQueueTask) -> Unit,
    detailFiles: List<Pair<String, Long>>,
    detailLoading: Boolean,
    onOpenDir: (DesktopComicQueueTask) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    embedded: Boolean = false,
) {
    val tokens = VeneraTokens
    val body: @Composable () -> Unit = {
        Column(modifier = Modifier.fillMaxSize()) {
            if (tasks.isNotEmpty()) {
                QueueStatsPanel(tasks = tasks, primed = primed, concurrency = concurrency)
            }
            if (tasks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = desktopComicQueueScreenHeader(tasks, primed),
                        fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                        color = tokens.color.textSecondary,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(tasks, key = { it.taskId }) { task ->
                        // 队列行补右键，沿用行内真按钮的回调（移除用 Divider 隔离）。
                        com.venera.desktop.ui.components.DesktopContextMenuArea(
                            items = {
                                buildList {
                                    add(
                                        com.venera.desktop.ui.components.DesktopMenuEntry.Item(
                                            label = "展开详情",
                                            icon = Icons.Outlined.UnfoldMore,
                                            onClick = { onToggleExpand(task) },
                                        ),
                                    )
                                    add(
                                        com.venera.desktop.ui.components.DesktopMenuEntry.Item(
                                            label = "打开文件夹",
                                            icon = Icons.Outlined.FolderOpen,
                                            onClick = { onOpenDir(task) },
                                        ),
                                    )
                                    if (desktopComicQueueCanStop(task)) {
                                        add(
                                            com.venera.desktop.ui.components.DesktopMenuEntry.Item(
                                                label = "暂停",
                                                icon = Icons.Outlined.Pause,
                                                onClick = { onStop(task) },
                                            ),
                                        )
                                    }
                                    add(com.venera.desktop.ui.components.DesktopMenuEntry.Divider)
                                    add(
                                        com.venera.desktop.ui.components.DesktopMenuEntry.Item(
                                            label = "移除",
                                            icon = Icons.Outlined.Delete,
                                            isDanger = true,
                                            onClick = { onRemove(task) },
                                        ),
                                    )
                                }
                            },
                        ) {
                        QueueRow(
                            task = task,
                            expanded = task.taskId == expandedTaskId,
                            onStop = { onStop(task) },
                            onRemove = { onRemove(task) },
                            onToggleExpand = { onToggleExpand(task) },
                        )
                        }
                        if (task.taskId == expandedTaskId) {
                            QueueDetailCard(
                                task = task,
                                concurrency = concurrency,
                                files = detailFiles,
                                loading = detailLoading,
                                onOpenDir = { onOpenDir(task) },
                                onStop = if (desktopComicQueueCanStop(task)) {
                                    { onStop(task) }
                                } else {
                                    null
                                },
                                onRemove = { onRemove(task) },
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
        title = "下载队列",
        subtitle = if (tasks.isEmpty()) null else desktopComicQueueScreenHeader(tasks, primed),
        note = note,
        onBack = onBack,
        modifier = modifier,
        width = DesktopPageWidth.Browse,
    ) {
        body()
    }
}

// ── ① 任务状态总面板（稿 `.stats`：四格 1dp 缝 + dot 行 + thread chips）──

private fun clockLabel(seconds: Long): String =
    "%02d:%02d:%02d".format(seconds / 3600, (seconds % 3600) / 60, seconds % 60)

@Composable
private fun statusTone(status: DesktopComicQueueStatus): Color = when (status) {
    DesktopComicQueueStatus.RUNNING -> VeneraTokens.color.primary
    DesktopComicQueueStatus.DONE -> DqTone.ok
    DesktopComicQueueStatus.FAILED -> DqTone.fail
    DesktopComicQueueStatus.STOPPED -> DqTone.warn
    DesktopComicQueueStatus.PENDING -> VeneraTokens.color.textTertiary
}

@Composable
private fun QueueStatsPanel(tasks: List<DesktopComicQueueTask>, primed: Boolean, concurrency: Int) {
    val tokens = VeneraTokens
    fun count(s: DesktopComicQueueStatus) = tasks.count { it.status == s }
    val running = tasks.firstOrNull { it.status == DesktopComicQueueStatus.RUNNING }
    val pending = tasks.firstOrNull { it.status == DesktopComicQueueStatus.PENDING }
    var etaLabel = "—"
    var rateLabel = "—"
    if (running != null && running.downloadedPages > 0 && running.totalPages > 0) {
        val elapsedSec = ((running.updateTime - running.createTime) / 1000L).coerceAtLeast(1L)
        val perPage = elapsedSec.toDouble() / running.downloadedPages
        val remaining = (running.totalPages - running.downloadedPages).coerceAtLeast(0)
        etaLabel = clockLabel((perPage * remaining).toLong())
        rateLabel = "%.1f 页/分".format(running.downloadedPages * 60.0 / elapsedSec.toDouble())
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = tokens.spacing.space2)
            .clip(GalleryTokens.Shape.Large)
            .border(1.dp, tokens.color.outlineVariant.copy(alpha = 0.3f), GalleryTokens.Shape.Large),
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        StatsCell(modifier = Modifier.weight(1f), k = "剩余时间", v = etaLabel,
            q = "按当前页档均速估算（逐任务字节账未建）")
        StatsCell(modifier = Modifier.weight(1f), k = "传输速度", v = rateLabel,
            q = if (running != null) "在跑：${running.comicTitle}" else "没有在跑的一笔")
        StatsCell(modifier = Modifier.weight(1.08f), k = "任务队列", v = "${tasks.size} 笔",
            dots = listOf(
                "运行中" to count(DesktopComicQueueStatus.RUNNING) to tokens.color.primary,
                "等待" to count(DesktopComicQueueStatus.PENDING) to tokens.color.textTertiary,
                "已完成" to count(DesktopComicQueueStatus.DONE) to DqTone.ok,
                "失败" to count(DesktopComicQueueStatus.FAILED) to DqTone.fail,
            ),
            note = if (!primed) "表还在读" else null)
        StatsCell(modifier = Modifier.weight(1.25f), k = "并发线程 · 全局速度限制",
            v = "${tasks.count { it.status == DesktopComicQueueStatus.RUNNING }} / $concurrency",
            q = "并发档随设置改（重启生效）· 限速未接",
            chips = listOfNotNull(
                running?.let { "#1 ${it.comicTitle.take(10)} ${it.downloadedPages}/${it.totalPages}页" },
                pending?.let { "#2 ${pending.chapterTitle.take(10)} 待开跑" },
                if (running == null && pending == null) "空闲" else null,
            ))
    }
}

@Composable
private fun StatsCell(
    modifier: Modifier,
    k: String,
    v: String,
    q: String? = null,
    dots: List<Pair<Pair<String, Int>, Color>>? = null,
    chips: List<String>? = null,
    note: String? = null,
) {
    val tokens = VeneraTokens
    Column(
        modifier = modifier
            .background(tokens.color.surfaceVariant.copy(alpha = 0.35f))
            .padding(horizontal = 13.dp, vertical = 11.dp),
    ) {
        Text(k, fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight, color = tokens.color.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            v,
            fontSize = VeneraType.sectionTitle.size, lineHeight = VeneraType.sectionTitle.lineHeight,
            fontWeight = VeneraType.cardTitle.weight,
            color = tokens.color.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (dots != null) {
            Column(modifier = Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                dots.chunked(2).forEach { line ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        line.forEach { (pair, color) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(7.dp).clip(CircleShape).background(color))
                                Text(" ${pair.first} ${pair.second}", fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight, color = tokens.color.textSecondary)
                            }
                        }
                    }
                }
            }
        }
        q?.let {
            Text(it, fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight, color = tokens.color.textTertiary, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
        }
        if (!chips.isNullOrEmpty()) {
            Row(
                modifier = Modifier.padding(top = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                chips.forEach { chip ->
                    Text(
                        text = chip,
                        fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                        color = tokens.color.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(tokens.color.surfaceContainerHigh)
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
            }
        }
        note?.let {
            Text(it, fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight, color = tokens.color.textTertiary, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

// ── ② 队列行（稿 `.rowq`）──

@Composable
private fun QueueRow(
    task: DesktopComicQueueTask,
    expanded: Boolean,
    onStop: () -> Unit,
    onRemove: () -> Unit,
    onToggleExpand: () -> Unit,
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
        DqThumb(url = task.comicCover, contentDescription = task.comicTitle)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${task.comicTitle} · ${task.chapterTitle}",
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
                DqBadge(desktopComicQueueStatusText(task.status), statusTone(task.status))
                Text(
                    text = "${task.downloadedPages} / ${task.totalPages} 页",
                    fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                    color = tokens.color.textTertiary,
                )
                if (task.status == DesktopComicQueueStatus.FAILED && task.error.isNotBlank()) {
                    Text(
                        text = task.error,
                        fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                        color = DqTone.fail,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
            DqBar(
                frac = if (task.totalPages > 0) task.downloadedPages.toFloat() / task.totalPages else 0f,
                tone = statusTone(task.status),
                modifier = Modifier.padding(top = 7.dp),
            )
        }
        // `.qops`：图标把手（desc 用原两枚文案常量，判据与读屏都念得到）。
        if (desktopComicQueueCanStop(task)) {
            DqIconButton(icon = Icons.Outlined.Stop, description = DESKTOP_COMIC_QUEUE_STOP_LABEL, onClick = onStop)
        }
        DqIconButton(
            icon = Icons.Outlined.Close,
            description = DESKTOP_COMIC_QUEUE_REMOVE_LABEL,
            onClick = onRemove,
            danger = true,
        )
        Icon(
            imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            contentDescription = if (expanded) "收起" else "展开",
            tint = if (expanded) tokens.color.primary else tokens.color.textTertiary,
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onToggleExpand),
        )
    }
}

// ── ② 展开详情（稿 `.detail` 双列）──

@Composable
private fun QueueDetailCard(
    task: DesktopComicQueueTask,
    concurrency: Int,
    files: List<Pair<String, Long>>,
    loading: Boolean,
    onOpenDir: () -> Unit,
    onStop: (() -> Unit)?,
    onRemove: () -> Unit,
) {
    val tokens = VeneraTokens
    val pct = if (task.totalPages > 0) task.downloadedPages * 100 / task.totalPages else 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
            .border(1.dp, tokens.color.outlineVariant.copy(alpha = 0.32f), RoundedCornerShape(12.dp))
            .background(tokens.color.surfaceVariant.copy(alpha = 0.16f))
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // 左列：进度 / 源 / 原因 / 动作 / 线程
        Column(modifier = Modifier.weight(1.05f)) {
            Text(
                text = "${task.comicTitle} · ${task.chapterTitle} · 源与线程",
                fontSize = VeneraType.bodySecondary.size, lineHeight = VeneraType.bodySecondary.lineHeight,
                fontWeight = VeneraType.cardTitle.weight,
                color = tokens.color.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            DqSectionHead("下载进度")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DqBadge(desktopComicQueueStatusText(task.status), statusTone(task.status))
                Text(
                    "${task.downloadedPages} / ${task.totalPages} 页 · $pct%",
                    fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                    color = tokens.color.textSecondary,
                )
            }
            DqBar(
                frac = if (task.totalPages > 0) task.downloadedPages.toFloat() / task.totalPages else 0f,
                tone = statusTone(task.status),
                modifier = Modifier.padding(top = 8.dp),
            )
            DqSectionHead("源信息")
            DqUrlBox("源 ${task.sourceKey} · comic ${task.comicId} · 话 ${task.chapterId}")
            if (task.error.isNotBlank()) {
                DqSectionHead("卡住成因")
                DqErrCard(task.error)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DqMiniButton("打开目录", onClick = onOpenDir)
                if (onStop != null) {
                    DqMiniButton(DESKTOP_COMIC_QUEUE_STOP_LABEL, onClick = onStop)
                }
                DqMiniButton(DESKTOP_COMIC_QUEUE_REMOVE_LABEL, onClick = onRemove)
            }
            DqSectionHead("下载线程（并发 $concurrency）")
            DqThreadRow(
                tid = "#1",
                work = if (task.status == DesktopComicQueueStatus.RUNNING) {
                    "正在下 ${task.downloadedPages}/${task.totalPages} 页"
                } else {
                    taskStatusWorkLine(task.status)
                },
                state = desktopComicQueueStatusText(task.status),
                active = task.status == DesktopComicQueueStatus.RUNNING,
            )
        }
        // 右列：文件清单框
        Column(modifier = Modifier.weight(1f)) {
            DqSectionHead(
                if (loading) "文件清单（正在数…）" else "文件清单（${files.size} 颗）",
            )
            DqFileList(
                files = files,
                loading = loading,
                emptyLine = "那一格还没落下文件（刚排队 / 被停手都可能是这一档）。",
            )
        }
    }
}

private fun taskStatusWorkLine(status: DesktopComicQueueStatus): String = when (status) {
    DesktopComicQueueStatus.PENDING -> "在排，还没轮上"
    DesktopComicQueueStatus.DONE -> "下完了，线程空着"
    DesktopComicQueueStatus.FAILED -> "空闲（任务失败）"
    DesktopComicQueueStatus.STOPPED -> "空闲（已暂停）"
    DesktopComicQueueStatus.RUNNING -> ""
}
