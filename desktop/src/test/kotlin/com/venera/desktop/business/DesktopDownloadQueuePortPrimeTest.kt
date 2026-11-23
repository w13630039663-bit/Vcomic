package com.venera.desktop.business

import com.venera.compose.source.model.ComicChapter
import com.venera.desktop.gallery.data.DesktopComicDownloadQueue
import com.venera.desktop.gallery.data.DesktopComicQueueStatus
import com.venera.desktop.gallery.data.desktopComicQueueTaskId
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 钉住详情页入队那一跳**不把泵正跑的那笔打回待跑**。
 *
 * 端口不是单例（每本书一颗详情核各建一颗），所以幂等不能挂在端口上：
 * `DesktopComicDownloadQueue.prime` 自己按根去重 —— 同一根只读一次盘，换根或 force 才重读。
 * 盘上永不含 `RUNNING`（写盘即降级成待跑），同根重读就会把活圈手上那笔摊回待跑、被另一圈认领。
 */
class DesktopDownloadQueuePortPrimeTest {

    private fun newRoot(): java.io.File = Files.createTempDirectory("portq").toFile()

    /** 泵（Runner）先读过这一根，之后换一本书新建端口再入队：在跑那笔必须还是正在跑。 */
    @Test
    fun 新端口首次入队不把正在跑那笔打回待跑() {
        val root = newRoot()
        // 泵启动那一步先读盘（Runner 的形状）。
        DesktopComicDownloadQueue.prime(root)
        DesktopComicDownloadQueue.enqueue(root, "jm", "1461316", "题名", "", "ch-a", "第 1 话", 0, false)
        val taskId = desktopComicQueueTaskId("jm", "1461316", "ch-a")
        DesktopComicDownloadQueue.markRunning(root, taskId)

        // 换一本书：新建一颗端口（per-port 记忆在这里从零开始）。
        val port = DesktopDownloadQueuePort { root }
        port.enqueue(
            "jm", "9999", "别的书", "",
            listOf(ComicChapter(id = "ch-z", title = "第 1 话", order = 0)),
        )

        assertEquals(
            "同根再读盘会把在跑那笔摊回待跑 ⇒ 两圈并发写同一章节目录：",
            DesktopComicQueueStatus.RUNNING,
            DesktopComicDownloadQueue.tasks.value.first { it.taskId == taskId }.status,
        )
    }

    /** 下载根换了（用户改目录）：必须重读新根，不能拿旧根的内存表去覆盖。 */
    @Test
    fun 换根会重读而不是沿用旧根的表() {
        val first = newRoot()
        DesktopComicDownloadQueue.prime(first)
        DesktopComicDownloadQueue.enqueue(first, "jm", "1", "旧根的书", "", "ch-old", "第 1 话", 0, false)

        var current = first
        val port = DesktopDownloadQueuePort { current }
        current = newRoot()
        port.enqueue(
            "jm", "2", "新根的书", "",
            listOf(ComicChapter(id = "ch-new", title = "第 1 话", order = 0)),
        )

        val ids = DesktopComicDownloadQueue.tasks.value.map { it.chapterId }
        assertEquals("换根后表里只该有新根那一笔：" + ids, listOf("ch-new"), ids)
        assertTrue("旧根那一笔不该跟过来：" + ids, "ch-old" !in ids)
    }
}
