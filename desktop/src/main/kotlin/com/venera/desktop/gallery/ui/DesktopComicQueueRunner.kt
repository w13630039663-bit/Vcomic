package com.venera.desktop.gallery.ui

import com.venera.desktop.gallery.data.DesktopComicChapterRun
import com.venera.desktop.gallery.data.DesktopComicDownloadQueue
import com.venera.desktop.gallery.data.DesktopComicQueueTask
import com.venera.desktop.gallery.data.desktopComicChapterCancelledLine
import com.venera.desktop.gallery.data.desktopComicChapterDownloadLine
import com.venera.desktop.gallery.data.desktopComicChapterProgressLine
import com.venera.desktop.gallery.data.desktopComicDownloadChapter
import com.venera.desktop.gallery.data.desktopComicQueueDir
import com.venera.desktop.gallery.data.desktopComicQueueReading
import com.venera.desktop.desktopEngineCall
import com.venera.engineprobe.EngineSession
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

/**
 * 第一百零一批 · 队列的执行器（**唯一**那一圈泵）。
 *
 * ## 为什么这一颗住在域那一层而不是详情那一屏
 *
 * 详情那一屏的 runner 从前挂在 `rememberCoroutineScope()` 上 ⇒ **离开那一屏就死**，跑到一半的那话既不完成也不留痕。
 * 引擎句柄归漫画域那一层持有（`DesktopComicPane.kt` 里那枚 `var session by remember`，退域才关），
 * 所以泵也挂在那一层：换屏、退详情都不断，而**退出漫画域**会协作式停手 —— 详情那一屏那一行念「回到漫画域就接着跑」
 * 而不是「已暂停」，因为任务表留在盘上而页一颗都没删。
 * 第一百零六批起，「下载队列」那一行**自己也驱动这一圈泵**（有活才起句柄，那一档见 `desktopComicEngineNeeded`）——
 * 从前它只读不驱动，于是那一屏念得出「排队中」而那一笔永远不动。
 *
 * ## 这一颗文件同时钉着三件事（各有判据）
 *
 * - **并发由认领钉，不立常量**：一圈泵一次只认领**一笔**（`DesktopComicDownloadQueue.awaitNextPending`
 *   锁内翻档），泵开几圈由装配点递的并发档定（盘上 pref_download_threads，默认 1 = 旧恒串行形状）。
 *   这里**故意不立并发常量** —— 写死的常量是假开关；档从设置「网络」区改，重启生效。
 * 档二 5b-3 由「恒串行 1」改判为「默认串行可开档」；判据 ⑨ 同步改口径。
 * - **只有一颗取消闸**：`shouldCancel` 递出去的是队列那枚 [DesktopComicDownloadQueue.stopRequested]，
 *   而它吃的是**同一格** —— 屏上那枚把手（`requestStop`）与取证种子那枚页上限（`armPageCap`）都扳它。
 * - **落盘那一跳的口径没换**：页还是由批 96/100/102/103 那颗 [desktopComicDownloadChapter] 写的，
 * 只把"谁来跑、跑几笔、跑到哪"这一层挪出了详情那一屏。
 *   ⚠️ 那一整串实参的顺序（**号在前、话号在后**）由 `DesktopComicDetailWiringTest` ⑪ 钉着，改判后钉在本文件。
 *
 * ## 泵接上 UI 那颗引擎之后，那两跳改过锁
 *
 * 从前这一圈**没人递引擎**（`desktopComicStartQueuePump` 在 main 里零调用点），那两跳裸调
 * `engine.pages` / `engine.pageImage` 碰不到共享那颗 ⇒ 今天无害。泵一接上壳里那颗常驻 `uiEngine`，
 * 它就与探索 / 详情 / 取页 / 搜索**抢同一颗 GraalJS context**（零同步，实测形态是
 * `IllegalStateException 装载后取不到 source key`）—— 所以 `DesktopEngineCallCoverageTest`
 * 那条"接上就要改过锁"的前置今天兑现了。
 *
 * ⚠️ 锁的粒度是**一跳**而不是**一整话**：整话锁住的话，89 页那一趟会把 UI 一直饿在锁外，
 * 用户点哪一屏都不动，而那看起来就像死了。所以页表那一问过锁一次、每一页取字节过锁一次，
 * 两趟之间让锁。代价是这一圈里 `pages` 与 `pageImage` 之间可能被插进别的 UI 调用 ——
 * 那不影响正确性：页表那一问的答复已经收在本地 `pages` 里，泵不再回头问引擎要第二次。
 *
 * ⚠️ 并发档 >1 时那 N 圈泵仍**共抢同一颗引擎锁**（一跳一让）：并发跑的是"下载写盘"这一段的重叠，
 * 引擎问页/取字节仍然排队 —— 所以并发主要吃在网络字节与磁盘写上，不会把 UI 饿成 N 倍。
 */
internal fun desktopComicStartQueuePump(
    queueScope: CoroutineScope,
    session: EngineSession?,
    downloadsRoot: File,
    concurrency: Int = 1,
) {
    val engine = session
    queueScope.launch {
        // 队列读盘不依赖引擎会话：队列页不启动引擎，须先 prime 落盘任务再按 session 是否存在决定是否启动泵循环。
        DesktopComicDownloadQueue.prime(downloadsRoot)
        println(desktopComicQueueReading(DesktopComicDownloadQueue.tasks.value, DesktopComicDownloadQueue.asideName))
        // 泵开几圈 = 并发档（默认 1 即旧形状；认领在队列锁内做，多圈不会抢同一笔）。
        if (engine != null) repeat(concurrency.coerceIn(1, 16)) {
            launch { desktopComicQueuePumpLoop(engine, downloadsRoot) }
        }
    }
}

/**
 * 那一圈泵：一笔一笔跑 → **没活时挂在表上等下一笔** → 退场时把「正在跑」交回「待跑」。
 *
 * 读表那一跳住在 [desktopComicStartQueuePump] 而不是这里 —— 没有引擎时也要有人把表读回来。
 *
 * 第一百零八批：这一圈从前在表空那一刻 `?: break` 退出，而句柄没换就不会再被踢一次 ⇒
 * 之后排进来的一笔一直没人接（包内读数 `待=1 跑=0` 到关窗）。现在取表那一枚换成
 * [DesktopComicDownloadQueue.awaitNextPending] —— 挂在一张 `MutableStateFlow` 上等新的一笔，
 * 不轮询、不丢唤醒。
 *
 * ⚠️ `finally` 那一格不是清理样式：泵被掐掉（退域，或挂在那一等时作用域被取消）时手上那一笔还停在
 * `RUNNING` 那一档，不交回待跑的话，回到这一域它**永远不会再被排上** —— 而顶栏那枚牌子会念一句「正在下」念到天荒地老。
 */
internal suspend fun desktopComicQueuePumpLoop(engine: EngineSession, root: File) {
    var held: String? = null
    try {
        // 作用域被掐掉之后不许再去问引擎要下一页（那一句的近亲是批 80 真跑撞到的 `Context execution was cancelled.`）。
        while (coroutineContext[Job]?.isActive == true) {
            val task = DesktopComicDownloadQueue.awaitNextPending(root)
            held = task.taskId
            desktopComicRunOneTask(engine, root, task)
            held = null
        }
    } finally {
        // 只归还这一圈手上那笔：旧作用域退场时若把全表「正在跑」都降级，
        // 新作用域已认领的那笔会被打回待跑、再被另一圈认领 —— 同一目录又两圈并发写。
        held?.let { DesktopComicDownloadQueue.abandonRunning(root, it) }
        println(desktopComicQueueReading(DesktopComicDownloadQueue.tasks.value, DesktopComicDownloadQueue.asideName))
    }
}

/**
 * 跑**一笔**：问页表 → 逐页落盘 → **回读盘**之后才定档。
 *
 * 页表那一问只有跑到这里才做（入队时并不知道分母），而它问出来的页数要立刻报回表里 ——
 * 顶栏与详情那一句「正在下 第 k 话 · i/M 页」吃的就是这一格，缺了它分母恒 0。
 *
 * ⚠️ `written` 这里记的是**盘上已有**的页数（进度那一格递回来的分子），不是"这一趟写了几页" ——
 * 批 100 那条读数口径分得很清，而队列那一格要的是前者（屏上要说"已有几页"）。
 */
private suspend fun desktopComicRunOneTask(engine: EngineSession, root: File, task: DesktopComicQueueTask) {
    val taskId = task.taskId
    DesktopComicDownloadQueue.markRunning(root, taskId)
    val dir = desktopComicQueueDir(root, task)
    var knownPages = 0
    var onDisk = 0
    val outcome = runCatching {
        // 过锁的那一跳：`getOrThrow()` 让失败仍旧落到这一颗 `runCatching` 里
        // （档位、读数、`desktopComicChapterOutcome` 那一路一个字不改），只是不再与 UI 抢同一颗 context。
        val pages = desktopEngineCall(engine, sourceKey = task.sourceKey) {
            engine.pages(task.comicId, EngineSession.Episode(task.chapterTitle, task.chapterId, task.placeholder), sourceKey = task.sourceKey)
        }.getOrThrow()
        knownPages = pages.size
        DesktopComicDownloadQueue.reportTotal(root, taskId, pages.size)
        val run = desktopComicDownloadChapter(dir, task.comicId, task.chapterId, task.chapterOrder, task.chapterTitle, pages.size,
            shouldCancel = { DesktopComicDownloadQueue.stopRequested(taskId) },
            progress = { written, expected ->
                DesktopComicDownloadQueue.reportProgress(root, taskId, written)
                onDisk = written
                println(desktopComicChapterProgressLine(task.comicId, task.chapterOrder, written, expected))
            },
        ) { page ->
            // 每一页各过各的锁（粒度 = 一跳，见类注释）：整话锁住会把 UI 饿死在锁外。
            val bytes = desktopEngineCall(engine, sourceKey = task.sourceKey) {
                engine.pageImage(pages[page], task.comicId, task.chapterId, sourceKey = task.sourceKey)
            }.getOrThrow()
            com.venera.desktop.gallery.data.DesktopDownloadSpeedTracker.recordBytes(bytes.size.toLong())
            bytes
        }
        pages.size to run
    }
    val settled = desktopComicChapterOutcome(task.chapterOrder, outcome, knownPages, onDisk)
    val cause = outcome.exceptionOrNull()
    // 取消不是失败：记成 FAILED 会让用户以为这一话坏了。留在「正在跑」，
    // 由泵退场那一步交回待跑，回到这一域自动接着下。
    if (cause is kotlinx.coroutines.CancellationException) throw cause
    println(
        when {
            cause != null ->
                desktopComicChapterDownloadLine(task.comicId, task.chapterOrder, settled.written, settled.total, cause = cause)

            settled.cancelled ->
                desktopComicChapterCancelledLine(task.comicId, task.chapterOrder, settled.pagesOnDisk, settled.total)

            else ->
                desktopComicChapterDownloadLine(task.comicId, task.chapterOrder, settled.written, settled.total, complete = true)
        },
    )
    when {
        cause != null -> DesktopComicDownloadQueue.fail(root, taskId, settled.failure ?: "取页没成交")
        outcome.getOrNull()?.second is DesktopComicChapterRun.Cancelled ->
            DesktopComicDownloadQueue.settle(root, taskId, DesktopComicChapterRun.Cancelled(settled.written))

        else -> DesktopComicDownloadQueue.settle(root, taskId, DesktopComicChapterRun.Completed(settled.written))
    }
    println(desktopComicQueueReading(DesktopComicDownloadQueue.tasks.value, DesktopComicDownloadQueue.asideName))
}
