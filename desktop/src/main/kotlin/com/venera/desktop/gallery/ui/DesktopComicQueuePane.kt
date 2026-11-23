package com.venera.desktop.gallery.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.venera.desktop.gallery.data.DesktopComicDownloadQueue
import com.venera.desktop.gallery.data.DesktopComicQueueStatus
import com.venera.desktop.gallery.data.DesktopComicQueueTask
import io.github.composefluent.component.Button
import io.github.composefluent.component.Text
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal const val DESKTOP_COMIC_QUEUE_STOP_LABEL = "暂停"

/** 「移除」那颗按钮的文案。 */
internal const val DESKTOP_COMIC_QUEUE_REMOVE_LABEL = "移除"

/**
 * 展示那一件：**只吃纯数据与两枚回调**（一次 IO 都不做），所以它能被喂着离屏渲出来验
 * —— 与批 97 / 详情那两屏同一条分家规矩，理由是同一句。
 */
internal fun desktopComicQueueScreenHeader(tasks: List<DesktopComicQueueTask>, primed: Boolean): String {
    if (tasks.isEmpty() && !primed) return "正在读那张表…"
    val running = tasks.firstOrNull { it.status == DesktopComicQueueStatus.RUNNING }
    if (running != null) {
        val waiting = tasks.count { it.status == DesktopComicQueueStatus.PENDING }
        return "正在下 第 ${running.chapterOrder + 1} 话「${running.chapterTitle}」 · " +
            "${running.downloadedPages}/${running.totalPages} 页 · 另有 $waiting 项排队"
    }
    val waiting = tasks.count { it.status == DesktopComicQueueStatus.PENDING }
    if (waiting > 0) return "$waiting 项排队 · 一次只跑一项 · 站在这一屏就接着跑"
    // ⚠️改掉「长按」那半句：本仓当前渲染路里从来没有长按
    //（详情屏那一枚是**看得见的按钮**），留着它就是替一屏已经不存在的操作作伪证。
    if (tasks.isEmpty()) return "本机队列空 —— 详情页「离线下载」勾选某几话会排到这里来"
    return "这一页上 ${tasks.size} 项都排完了（盘上那些页归「本机已下载」那一屏说）"
}

/**
 * 行里那一句：第几话、题名（用「」包起来 —— 源站的章节题名常长成「第2話」，与前缀挨着念就成了
 * `第 2 话 第2話`，批 97/98 两帧图证都拍到这一眼）、档位、页码、是哪一本。
 *
 * 失败那一档把站方原话带上（`error` 格）：只说「失败了」等于让用户再按一次去撞同一堵墙。
 */
internal fun desktopComicQueueRowLine(task: DesktopComicQueueTask): String {
    val base = "第 ${task.chapterOrder + 1} 话「${task.chapterTitle}」 · ${desktopComicQueueStatusText(task.status)} · " +
        "${task.downloadedPages}/${task.totalPages} 页 · 本子 ${task.comicId}"
    return if (task.status == DesktopComicQueueStatus.FAILED && task.error.isNotEmpty()) "$base · 因=${task.error}" else base
}

/** 档位那两个字（与顶栏牌子那句、与详情那一屏的行话同一套档名，各屏只是对象不同）。 */
internal fun desktopComicQueueStatusText(status: DesktopComicQueueStatus): String = when (status) {
    DesktopComicQueueStatus.PENDING -> "排队中"
    DesktopComicQueueStatus.RUNNING -> "正在下"
    DesktopComicQueueStatus.DONE -> "已排完"
    DesktopComicQueueStatus.FAILED -> "失败了"
    DesktopComicQueueStatus.STOPPED -> "已停手"
}

/**
 * 「停这一笔」只挂在**正在跑**那一行。
 *
 * 排队那一笔还没开始（扳了闸也只会在它开跑的第一页上立刻停手，那一格由「撤掉」说更清楚）；
 * 已排完 / 已停手 / 失败了 三档都没有在飞的东西可停。
 */
internal fun desktopComicQueueCanStop(task: DesktopComicQueueTask): Boolean =
    task.status == DesktopComicQueueStatus.RUNNING

/** 暂停那句提示：协作式停止，写到页边界为止。副标题按铁律收敛到 ≤15 字。 */
internal fun desktopComicQueueStoppedLine(task: DesktopComicQueueTask): String =
    "已暂停：第 ${task.chapterOrder + 1} 话"

/** 撤掉了那一句：点名盘上还剩几页，并指路到「本机已下载」那一屏 —— 撤掉不等于删干净。 */
internal fun desktopComicQueueRemovedLine(task: DesktopComicQueueTask, pagesLeft: Int): String =
    "撤掉了：第 ${task.chapterOrder + 1} 话「${task.chapterTitle}」已从表里摘出去，盘上那 $pagesLeft 页还留着（要删干净去「本机已下载」那一屏）"

