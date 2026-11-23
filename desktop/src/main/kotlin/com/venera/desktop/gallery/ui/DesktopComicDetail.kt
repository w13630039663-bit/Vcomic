package com.venera.desktop.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.data.db.HistoryDao
import com.venera.desktop.gallery.data.DesktopComicChapterRun
import com.venera.desktop.gallery.data.desktopComicChapterCancelledLine
import com.venera.desktop.gallery.data.desktopComicChapterDir
import com.venera.desktop.gallery.data.desktopComicChapterDownloadLine
import com.venera.desktop.gallery.data.desktopComicChapterProgressLine
import com.venera.desktop.gallery.data.desktopComicDownloadChapter
import java.io.File
import com.venera.compose.data.db.HistoryRecord
import com.venera.compose.data.db.LocalFavoritesManager
import com.venera.desktop.gallery.data.DesktopComicDownloadQueue
import com.venera.desktop.gallery.data.DesktopComicQueueStatus
import com.venera.desktop.gallery.data.DesktopComicQueueTask
import com.venera.desktop.gallery.data.desktopComicQueueReading
import com.venera.desktop.gallery.data.desktopComicQueueTaskId
import com.venera.engineprobe.EngineSession
import io.github.composefluent.component.Button
import io.github.composefluent.component.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DesktopComicTarget(val comicId: String, val title: String, val cover: String)

/** 详情那一屏的三态。`Failed` 吃的是**已经收成一行**的原话（catch 现场负责一行化）。 */
internal sealed interface ComicDetailState {
    data object Loading : ComicDetailState
    data class Failed(val reason: String) : ComicDetailState
    data class Ready(
        val title: String,
        val description: String,
        val episodes: List<EngineSession.Episode>,
        /** `loadInfo` 答复里那一栏 `cover`（缺栏 = 空串）。历史那一笔的封面优先吃它。 */
        val cover: String,
    ) : ComicDetailState
}

/**
 * stdout 那一行：这一屏开得成不成、几章、简介有没有、封面取没取到。
 *
 * 为什么要有一行（而不是只看屏）：详情这一屏的三态里，`Loading` 与 `Failed` 在像素上都是
 * "左上角一行小字"，隔着一张分不出；而"话=0"这一格正是"整本翻不出页"那条老缺陷的形状。
 *
 * `封面=` 那一格只念有无、不念那串地址：它是这一笔历史封面的**第一个来源**，而"站方没给"与
 * "桌面没读"塌成同一格时，落库那行为空就查不出是哪一站（地址本身还可能带签名参数，不进 stdout）。
 */
internal fun desktopComicDetailReading(comicId: String, sourceKey: String, state: ComicDetailState): String {
    val head = "D_详情 源=$sourceKey 号=$comicId"
    return when (state) {
        ComicDetailState.Loading -> "$head 态=等回话"
        is ComicDetailState.Failed -> "$head 态=没成 因=${desktopComicDetailOneLine(state.reason)}"
        is ComicDetailState.Ready -> {
            val desc = state.description.trim()
            // 话号那一串是**给取证抄的**：`--read=<号>/<话号>` 要一枚真话号，而这一屏从前不念它
            // ⇒ 阅读那一屏只能靠屏上点一下才到得了，而鼠标是禁的（手册 §8）。批 30 的 `--ranking=` 同一套办法。
            "$head 态=上屏 话=${state.episodes.size} 简介=${if (desc.isEmpty()) "无" else "${desc.length}字"}" +
                " 封面=${if (state.cover.isBlank()) "无" else "有"} 话号=${desktopComicEpisodeIds(state.episodes)}"
        }
    }
}

/**
 * 前 5 枚话号，占位章念 `-`。
 *
 * 上限 5 是**有意的**：整本刷进 stdout 既没人抄，也会把那一行拉到几百字。
 * `-` 而不是空串：空串在两枚相邻的话号之间看不出来，而"这一章是占位章"正是取页时要换算法的那一格。
 */
internal fun desktopComicEpisodeIds(episodes: List<EngineSession.Episode>): String =
    episodes.take(5).joinToString(",") { if (it.placeholder || it.id.isBlank()) "-" else it.id }

/** 一行化：站方的原话常带 stack 尾巴，那会把读数拆成两行，抓读数的脚本就漏掉后半句。 */
private fun desktopComicDetailOneLine(reason: String): String =
    reason.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ').trim().take(240)

/**
 * stdout 那一行：「读到哪儿」那一格是**读到了**、**库里没这一本**、还是**库没读到**。
 *
 * 为什么这一档要有读数（而屏上没有）：第三档在屏上的形状是"那枚胶囊不见了"，
 * 而"这本没读过"与"库没开到"塌成同一格 —— 只有这一行分得出（与 [desktopComicDetailReading] 里
 * `Loading`/`Failed` 都是"左上角一行小字"那一条同一个理由）。
 *
 * `记录=没读到` 那一档故意**不摆胶囊**而只念这一行：那种时候摆「开始阅读」，按下就把
 * `comic_history`（两端共用那张表）里"下次从第 40 页读"写成第一话第 0 页 —— 少一枚钮是可见的慢，
 * 挪走另一端的进度是静默的错。
 */
internal fun desktopComicHistoryProgressLine(comicId: String, outcome: Result<HistoryRecord?>): String {
    val head = "D_续读 号=$comicId"
    val record = outcome.getOrElse {
        return "$head 记录=没读到 因=${it::class.java.simpleName}: ${desktopComicDetailOneLine(it.message ?: "")}"
    }
    return if (record == null) "$head 记录=无" else "$head 记录=有 话=${record.lastChapterIndex} 页=${record.lastPageIndex}"
}

/**
 *
 * ## 为什么引擎句柄是从外面递进来的
 *
 * `EngineSession` 是探索那一屏在 IO 线程里构造的 GraalJS context，它归那一屏的
 * `DisposableEffect` 关（`DesktopDomainNavigationTest` ⑩/⑪ 钉着：应用根不许自己造一颗）。
 * 详情这一屏要的只是"用同一颗问一次 `loadInfo`"，所以句柄由持有者传下来。
 *
 * ## 为什么这一屏不是覆盖层
 *
 * 画廊那张卡点开是覆盖层（半屏还能看见墙），而漫画详情要点开某一话——那是**换一屏**，
 * 与画师单人页同形。覆盖层那条路要处理"压在模糊之上"的一整族问题，这里没有收益。
 */
// 预留章节下载提示文案常量（当前详情页使用 DESKTOP_EPISODE_DOWNLOAD_LABEL）。
internal const val DESKTOP_CHAPTER_DOWNLOAD_HINT: String = "点某一话右侧的「下载这一话」可把它存到本机"

/**
 * 那一行状态的人话（屏上只摆这一句，`D_下载` / `D_下载进度` / `D_下载取消` 三行只进 stdout —— 取证读数不进界面）。
 *
 * 五档分开写而不是拼一句：`written == 0` 与 `written > 0` 在屏上必须念不同的话 ——
 * 前者是"这一话本机早有了"（对岸那句"点一下就直接重下一遍"挡的就是这一档），
 * 后者是"这一趟真写了页"。合成一句就会把"没干活"念成"干了活"。
 *
 * 批 100 添的两档：`busy` 那一档现在带得上**进度**（从前只有"正在写本机…"一句，89 页那一趟从头到尾不吭声），
 * 而 `cancelled` 那一档念的是"停在第几页 + 下次接着补" —— 它**不许**塌进"已写下 N 页"那一档，
 * 因为那一趟没干完活（runner 自己交回的是 `Cancelled`，见 `DesktopComicChapterRun`）。
 */
internal fun desktopComicChapterStatusText(state: DesktopComicChapterDownload): String = when {
    state.queued -> DESKTOP_CHAPTER_DOWNLOAD_QUEUED_HINT
    state.busy -> "正在写本机… " + state.pagesOnDisk + "/" + state.total + " 页"
    state.failure != null -> "下载失败：" + state.failure + "（本机已有 " + state.pagesOnDisk + " 页）"
    state.cancelled -> "已停在 " + state.pagesOnDisk + "/" + state.total + " 页 · 下次从这里接着补"
    state.written == 0 -> "这一话本机已有 " + state.total + " 页"
    else -> "已写下 " + state.written + " 页"
}

/**
 * 排队那一档的人话（批 101）。
 *
 * ⚠️ 那句里「回到漫画域就接着跑」是**定档 C 的那条边界**写在屏上：执行器挂在漫画域那一层，
 * 退出这一域时那一圈泵会被协作式掐掉，而任务表与已写的页都留在盘上。
 * 不许写成「已暂停」—— 那听上去像有一枚"继续"的把手，而它今天没有。
 */
internal const val DESKTOP_CHAPTER_DOWNLOAD_QUEUED_HINT: String = "排着队 · 回到漫画域就接着跑"

/**
 * 这一本当前那一笔（**正在跑的优先，其次排着的，最后才是这一本最近的那一笔**）。
 *
 * 为什么要三档而不是只看"在跑的那一笔"：用户按完第二话会退到列表去看别的，
 * 回来时那一笔已经跑完了 —— 只看 RUNNING 就得到"屏上一个字都不念"，而盘上明明多了 89 页。
 */
internal fun desktopComicQueueTaskOf(
    tasks: List<DesktopComicQueueTask>, sourceKey: String, comicId: String,
): DesktopComicQueueTask? {
    val mine = tasks.filter { it.sourceKey == sourceKey && it.comicId == comicId }
    return mine.firstOrNull { it.status == DesktopComicQueueStatus.RUNNING }
        ?: mine.firstOrNull { it.status == DesktopComicQueueStatus.PENDING }
        ?: mine.lastOrNull()
}

/**
 * 把队列里那一笔映射成那一行状态（**纯映射**，展示那一件不收引擎句柄也不收任务表本身）。
 *
 * ⚠️ `written = 0` 那一格是刻意的：`DONE` 那一档映射过去吃的是既有的「这一话本机已有 N 页」那一句 ——
 * 队列里没有"这一趟写了几页"那一格（那是批 100 内存里那一趟的口径），硬造一枚就是假数据。
 */
internal fun desktopComicChapterDownloadOf(task: DesktopComicQueueTask?): DesktopComicChapterDownload? {
    task ?: return null
    return when (task.status) {
        DesktopComicQueueStatus.PENDING -> DesktopComicChapterDownload(
            task.chapterOrder, written = 0, total = task.totalPages, busy = false,
            failure = null, pagesOnDisk = task.downloadedPages, cancelled = false, queued = true,
        )

        DesktopComicQueueStatus.RUNNING -> DesktopComicChapterDownload(
            task.chapterOrder, written = 0, total = task.totalPages, busy = true,
            failure = null, pagesOnDisk = task.downloadedPages, cancelled = false, queued = false,
        )

        DesktopComicQueueStatus.DONE -> DesktopComicChapterDownload(
            task.chapterOrder, written = 0, total = task.downloadedPages, busy = false,
            failure = null, pagesOnDisk = task.downloadedPages, cancelled = false, queued = false,
        )

        DesktopComicQueueStatus.STOPPED -> DesktopComicChapterDownload(
            task.chapterOrder, written = 0, total = task.totalPages, busy = false,
            failure = null, pagesOnDisk = task.downloadedPages, cancelled = true, queued = false,
        )

        DesktopComicQueueStatus.FAILED -> DesktopComicChapterDownload(
            task.chapterOrder, written = 0, total = task.totalPages, busy = false,
            failure = task.error, pagesOnDisk = task.downloadedPages, cancelled = false, queued = false,
        )
    }
}

/**
 * 章节表里**某一具体行**下面那一句本机状态（详情屏的下载把手用）。
 *
 * ## 为什么不是复用 [desktopComicQueueTaskOf]
 *
 * 那颗是"这一本当前那一笔"（正在跑 → 排着 → 最近那一笔 三档挑一），它按**本子**取一笔 ——
 * 拿给整张章节表用就会三行齐念同一句进度。那是批 97 量到并立判据的那条缺陷
 * （「那一行状态只挂在被长按的那一行上」）。这里按**行号**取，取不到就念"没有可念的"（回 `null`，
 * 屏上那一行下面一个字都不摆），而不是退回去念"这一本最近那一笔"。
 *
 * ## 口径全部抄既有那两枚，不另写一份
 *
 * [desktopComicChapterDownloadOf]（档位 → 状态）与 [desktopComicChapterStatusText]（状态 → 人话）
 * 各自有判据钉着（批 101 ⑯ / 批 96 五档各念一句），这一颗只做"按行取那一笔"这一跳。
 */
internal fun desktopComicEpisodeDownloadNote(
    tasks: List<DesktopComicQueueTask>,
    sourceKey: String,
    comicId: String,
    chapterOrder: Int,
): String? {
    if (chapterOrder < 0) return null
    val task = tasks.firstOrNull {
        it.sourceKey == sourceKey && it.comicId == comicId && it.chapterOrder == chapterOrder
    } ?: return null
    val state = desktopComicChapterDownloadOf(task) ?: return null
    return desktopComicChapterStatusText(state)
}

/** 那一枚把手的人话（批 100）：桌面没有右键，动作没有可见把手就没人找得到（本仓那条口径）。 */
internal const val DESKTOP_CHAPTER_DOWNLOAD_CANCEL_LABEL: String = "取消"

/**
 * 按下去之后屏上先念的那一句（批 100）：**不许**写成"已停止" —— [DESKTOP_CHAPTER_DOWNLOAD_CANCEL_LABEL]
 * 那枚把手停的是"下一页不再取"，而正在飞的那一笔请求掐不掉（协作式取消）。
 */
internal const val DESKTOP_CHAPTER_DOWNLOAD_CANCELLED_HINT: String = "正在停手…"

/**
 * 一整话下载的那点状态（**纯数据**，展示那一件只吃它）。
 *
 * 为什么把 `busy` 与 `failure` 分两格而不是一个可空串：屏上那一行在"正在下"与"下完"两档念的是不同的话，
 * 而失败那一档还要把按钮留成可再按（对岸那一判是"点一下就直接重下一遍"，桌面这一批让它只补缺的那几页）。
 *
 * 批 100 添的两格：`pagesOnDisk` 是**盘上已有**的页数（进度那一格递回来的，失败那档也要它 ——
 * 第 63 页被掐断时说"本机已有 62 页"才是实话），`cancelled` 是 runner 自己交回的那一档而不是调用点猜的。
 *
 * 批 101 添的那一格：`queued` 是**还没轮到它跑**那一档。它必须与 `busy` 分开 ——
 * 合成一格就得到"正在写本机… 0/0 页"这种既没干活又宣称在干活的话（本仓那条「不许静默交错」）。
 */
internal data class DesktopComicChapterDownload(
    val chapterIndex: Int,
    val written: Int,
    val total: Int,
    val busy: Boolean,
    val failure: String?,
    val pagesOnDisk: Int,
    val cancelled: Boolean,
    val queued: Boolean,
)

/**
 * 收口件（批 100，重建）：runner 那一趟的结果折成屏上那一格的**纯数据**。
 * 三档钉死在测试里（DesktopComicDetailWiringTest ⑨）：
 *  - 失败：分母从**页表**来（knownPages），不塌 0；written=0；busy=false；原因带得上原文；
 *    盘上已有几页（pagesOnDisk）如实带着 —— 只报『失败』就是把『下了 62 页』抹掉。
 *  - 成功：分母吃算式自己报的；written 是补缺那几页，不是整话。
 *  - 取消：cancelled=true、无原因（停手不是坏）、分子=盘上已有。
 */
internal fun desktopComicChapterOutcome(
    chapterOrder: Int,
    outcome: Result<Pair<Int, DesktopComicChapterRun>>,
    knownPages: Int,
    pagesOnDisk: Int,
): DesktopComicChapterDownload = outcome.fold(
    onSuccess = { (total, run) ->
        when (run) {
            is DesktopComicChapterRun.Cancelled -> DesktopComicChapterDownload(
                chapterIndex = chapterOrder,
                written = run.written,
                total = total,
                busy = false,
                failure = null,
                pagesOnDisk = pagesOnDisk,
                cancelled = true,
                queued = false,
            )
            is DesktopComicChapterRun.Completed -> DesktopComicChapterDownload(
                chapterIndex = chapterOrder,
                written = run.written,
                total = total,
                busy = false,
                failure = null,
                pagesOnDisk = pagesOnDisk,
                cancelled = false,
                queued = false,
            )
        }
    },
    onFailure = { cause ->
        DesktopComicChapterDownload(
            chapterIndex = chapterOrder,
            written = 0,
            total = knownPages,
            busy = false,
            failure = desktopComicDetailOneLine(cause.message ?: cause.javaClass.simpleName),
            pagesOnDisk = pagesOnDisk,
            cancelled = false,
            queued = false,
        )
    },
)

/**
 * 纯展示那一件：吃 [ComicDetailState] 这块纯数据，**不发请求、不读盘、不要引擎句柄**。
 *
 * 拆开的理由是这一件能被喂着离屏渲成语义树来验（`DesktopComicDetailSemanticsTest`）——
 * 装载那一件要真 GraalJS 与真源，测试里渲不出来；而"屏上那句话到底变没变"这一层
 * 从前只有起窗拍帧一条路（一批 3~5 分钟），现在是一条断言几秒。
 */
/**
 * 一句话说清一个异常（取消息首行，空则用类名）。
 *
 * 这颗原先被抽取脚本当成"界面内小件"删掉了（它紧跟在被删的
 * `@Composable` 之后，而判据 `desktopComicChapterRows` 仍在用它）。
 * 它不是界面 —— 它是判据的输出格式，取回来。
 */
private fun oneLineOf(cause: Throwable): String =
    cause.message?.lineSequence()?.firstOrNull()?.takeIf { it.isNotBlank() }
        ?: cause::class.java.simpleName
