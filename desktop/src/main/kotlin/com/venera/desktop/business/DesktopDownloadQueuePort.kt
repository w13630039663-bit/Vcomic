package com.venera.desktop.business

import com.venera.compose.data.api.DownloadQueue
import com.venera.compose.download.ChapterOffline
import com.venera.compose.download.chapterOfflineState
import com.venera.compose.source.model.ComicChapter
import com.venera.compose.download.DownloadTask
import com.venera.desktop.gallery.data.DesktopComicDownloadQueue
import com.venera.desktop.gallery.data.desktopComicChapterPageFileName
import com.venera.desktop.gallery.data.desktopComicOfflineEpisodeIsWhole
import com.venera.desktop.gallery.data.desktopComicOfflineLocalChapter
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 下载队列那一族端口（`DownloadQueue`）的**桌面实现**（D′-2）。
 *
 * 详情页真吃的四颗：`isChapterDownloaded` / `chapterOffline` / `getDownloadedChapterFiles` / `enqueue`。
 * 盘上完成态一律走**共享判式** [chapterOfflineState]（`download/ChapterCompleteness.kt`，三参
 * `expectedPages` / `complete` / `writtenPages` 全部来自桌面已有的 [desktopComicOfflineLocalChapter]）——
 * 不发明第三种"齐不齐"（刚把那种漂移收成一颗）。
 *
 * `pause` / `resume` / `pauseAll` / `resumeAll` / `clearCompleted` / `relocateTasks`：桌面队列件
 * 今天没有那几档（详见 `DesktopComicDownloadQueue` —— 只有 enqueue/requestStop/drop），如实 NoOp + 点名。
 * `delete` 映射到已有的 [DesktopComicDownloadQueue.drop]（按号删那一笔并连本机文件一起清）。
 *
 * @param rootProvider 现读下载根目录（用户改目录后不必重启，与对岸 `ComicStorageRoot.resolve` 同口径；
 *                     拿不到根目录时返回 null，四颗读侧一律给"没有离线内容"的诚实答案）。
 */
internal class DesktopDownloadQueuePort(
    private val rootProvider: () -> File?,
) : DownloadQueue {

    // 屏只读 tasks 流而不写它（下载进度由壳里那条泵喂的 DesktopComicDownloadQueue 自己发）。
    // 这一族不接屏上的队列 UI（桌面队列屏直接读 DesktopComicDownloadQueue.tasks），所以这里交空流 + 点名。
    override val tasks: StateFlow<List<DownloadTask>> =
        MutableStateFlow<List<DownloadTask>>(emptyList()).asStateFlow()

    override fun enqueue(
        sourceKey: String,
        comicId: String,
        comicTitle: String,
        comicCover: String,
        chapters: List<ComicChapter>,
    ) {
        val root = rootProvider() ?: return
        // 第一次用完要有人把盘上表读回来（泵没起来时也成立）；同根重复读由队列对象自己挡，
        // 否则每次入队都用盘上快照覆盖内存表，把泵正跑那笔摊回待跑 —— 两圈并发写同一目录。
        DesktopComicDownloadQueue.prime(root)
        for (ch in chapters) {
            val placeholder = ch.id == comicId
            DesktopComicDownloadQueue.enqueue(
                root = root,
                sourceKey = sourceKey,
                comicId = comicId,
                comicTitle = comicTitle,
                comicCover = comicCover,
                chapterId = ch.id,
                chapterTitle = ch.title,
                chapterOrder = ch.order,
                placeholder = placeholder,
            )
        }
    }

    /** 三档齐不齐共用那颗判式：齐了才 true（与对岸 `isChapterDownloaded` 收口后口径一致）。 */
    override fun isChapterDownloaded(sourceKey: String, comicId: String, chapterId: String): Boolean =
        chapterOffline(sourceKey, comicId, chapterId) == ChapterOffline.COMPLETE

    override fun chapterOffline(sourceKey: String, comicId: String, chapterId: String): ChapterOffline {
        val root = rootProvider() ?: return ChapterOffline.NONE
        val local = desktopComicOfflineLocalChapter(root, sourceKey, comicId, chapterId)
            ?: return ChapterOffline.NONE
        return chapterOfflineState(
            expectedPages = local.expectedPages,
            complete = desktopComicOfflineEpisodeIsWhole(local),
            pageCount = local.writtenPages,
        )
    }

    /** 盘上这一话的页文件（0..written-1，按页名序）。没有本地章 ⇒ null（屏据此问源站）。 */
    override fun getDownloadedChapterFiles(sourceKey: String, comicId: String, chapterId: String): List<File>? {
        val root = rootProvider() ?: return null
        val local = desktopComicOfflineLocalChapter(root, sourceKey, comicId, chapterId) ?: return null
        if (local.writtenPages <= 0) return null
        return (0 until local.writtenPages).map { File(local.dir, desktopComicChapterPageFileName(it)) }
    }

    override fun delete(taskId: String, deleteFiles: Boolean) {
        val root = rootProvider() ?: return
        DesktopComicDownloadQueue.drop(root, taskId)
    }

    // 详情链不吃这几档：如实 NoOp（桌面队列件没有 pause/resume/clearCompleted/relocate）。
    override fun pause(taskId: String) {}
    override fun resume(taskId: String) {}
    override fun pauseAll() {}
    override fun resumeAll() {}
    override fun clearCompleted() {}
    override fun relocateTasks(oldRoot: File, newRoot: File): Int = 0
}
