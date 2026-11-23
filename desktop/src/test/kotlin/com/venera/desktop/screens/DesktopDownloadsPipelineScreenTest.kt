package com.venera.desktop.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.desktop.downloads.DesktopDownloadsPipelineScreen
import com.venera.desktop.downloads.DesktopLocalImageItem
import com.venera.desktop.gallery.data.DesktopComicDownloadedChapter
import com.venera.desktop.gallery.data.DesktopComicDownloadedGroup
import com.venera.desktop.gallery.data.DesktopComicQueueStatus
import com.venera.desktop.gallery.data.DesktopComicQueueTask
import com.venera.desktop.gallery.data.DesktopLocalComic
import com.venera.desktop.gallery.data.DesktopLocalComicLibraryScan
import com.venera.desktop.gallery.ui.DesktopComicDownloadListing
import com.venera.desktop.setContentThemed
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「下载与本地」一体化单屏与「本机内容（漫画/图片）」离屏语义测试。
 */
@OptIn(ExperimentalTestApi::class)
class DesktopDownloadsPipelineScreenTest {

    @Composable
    private fun Stage(content: @Composable () -> Unit) {
        Box(Modifier.fillMaxSize()) { content() }
    }

    private fun sampleTask(order: Int, status: DesktopComicQueueStatus = DesktopComicQueueStatus.RUNNING) =
        DesktopComicQueueTask(
            taskId = "task_$order",
            sourceKey = "jm",
            comicId = "comic_1",
            comicTitle = "芙莉莲",
            comicCover = "",
            chapterId = "ch_$order",
            chapterTitle = "第 $order 话",
            chapterOrder = order,
            directoryPath = "jm/comic_1/ch_$order",
            status = status,
            downloadedPages = 12,
            totalPages = 24,
            error = "",
            placeholder = false,
            createTime = 1L,
            updateTime = 2L,
        )

    private fun sampleChapter(order: Int, complete: Boolean = true) =
        DesktopComicDownloadedChapter(
            sourceKey = "jm",
            comicId = "comic_1",
            chapterId = "ch_$order",
            order = order,
            title = "第 $order 话",
            expectedPages = 24,
            writtenPages = if (complete) 24 else 21,
            bytes = 1024 * 1024 * 20,
            complete = complete,
        )

    private fun sampleImage(id: String) =
        DesktopLocalImageItem(
            path = "downloads/images/artist_a/pixiv_$id.jpg",
            dir = "artist_a",
            fileName = "pixiv_$id.jpg",
            bytes = 1024 * 1024 * 2,
            mtime = 1700000000000L,
            dimensions = "1920x1080",
        )

    @Test
    fun 下载与本地标题副题脉动带与双域切换真上屏() = runComposeUiTest {
        var importClicked = false
        var settingsClicked = false

        setContentThemed {
            Stage {
                DesktopDownloadsPipelineScreen(
                    mode = WideScreenLayoutMode.Expanded,
                    queueTasks = listOf(sampleTask(1)),
                    queuePrimed = true,
                    queueNote = null,
                    downloadedListing = DesktopComicDownloadListing(
                        groups = listOf(
                            DesktopComicDownloadedGroup(
                                comicId = "comic_1",
                                sourceKey = "jm",
                                chapters = listOf(sampleChapter(1)),
                            ),
                        ),
                        skipped = 0,
                        rootExists = true,
                    ),
                    downloadedNote = null,
                    onRepairDownloadedChapter = {},
                    onDeleteDownloadedChapter = {},
                    onOpenDownloadedChapterDir = {},
                    localComicScan = DesktopLocalComicLibraryScan(
                        comics = listOf(
                            DesktopLocalComic(
                                id = "imported_1",
                                title = "本地导入本",
                                sourceKey = "imported",
                                chapterCount = 1,
                                totalPages = 20,
                                rootPath = "/path/to/imported_1",
                                updatedAt = 1000L,
                            ),
                        ),
                        rootExists = true,
                        error = null,
                    ),
                    localComicNote = null,
                    onImportComics = { importClicked = true },
                    images = listOf(sampleImage("12345")),
                    imagesScanning = false,
                    imagesNote = null,
                    onOpenImageDir = {},
                    onOpenImage = {},
                    onDeleteImages = {},
                    onOpenSettingsDownloads = { settingsClicked = true },
                    onBack = {},
                )
            }
        }

        // 顶栏标题与副标题验证
        onNodeWithText("下载与本地").assertExists()
        onNodeWithText("管理已下载与本地导入的漫画与图片").assertExists()

        // 统计四格验证
        onNodeWithText("下载任务").assertExists()
        onNodeWithText("1 个任务").assertExists()
        onNodeWithText("传输速度").assertExists()
        onAllNodesWithText("本地存储").assertCountEquals(1)

        // 下载队列流验证
        onNodeWithText("下载队列").assertExists()
        onNodeWithText("下载完成后将自动归入下方已下载内容").assertExists()
        onNodeWithText("芙莉莲 · 第 1 话").assertExists()

        // 漫画档默认展示与导入按钮
        val importBtn = onNodeWithText("导入 CBZ / ZIP")
        importBtn.assertExists()
        importBtn.performClick()
        assertTrue("点击导入 CBZ/ZIP 把手必须成功触发回调", importClicked)

        // 双域切换：切换到图片档
        onNodeWithText("图片 (1)").performClick()
        onNodeWithText("pixiv · 12345").assertExists()

        // 下载设置把手验证
        val settingsBtn = onNodeWithText("下载设置")
        settingsBtn.assertExists()
        settingsBtn.performClick()
        assertTrue("点击下载设置必须直跳设置", settingsClicked)
    }

    @Test
    fun 多选模式与模态删除确认弹窗正常呼出() = runComposeUiTest {
        var deleteChapterInvoked = false

        setContentThemed {
            Stage {
                DesktopDownloadsPipelineScreen(
                    mode = WideScreenLayoutMode.Expanded,
                    queueTasks = emptyList(),
                    queuePrimed = true,
                    queueNote = null,
                    downloadedListing = DesktopComicDownloadListing(
                        groups = listOf(
                            DesktopComicDownloadedGroup(
                                comicId = "comic_1",
                                sourceKey = "jm",
                                chapters = listOf(sampleChapter(1)),
                            ),
                        ),
                        skipped = 0,
                        rootExists = true,
                    ),
                    downloadedNote = null,
                    onRepairDownloadedChapter = {},
                    onDeleteDownloadedChapter = { deleteChapterInvoked = true },
                    onOpenDownloadedChapterDir = {},
                    localComicScan = null,
                    localComicNote = null,
                    images = emptyList(),
                    onImportComics = {},
                    onOpenSettingsDownloads = {},
                    onBack = {},
                )
            }
        }

        // 进入多选模式
        onNodeWithText("选择").performClick()
        onNodeWithText("已选 0 本").assertExists()
        onNodeWithText("取消").assertExists()

        // 点击卡片进入选中
        onNodeWithText("第 1 话").performClick()
        onNodeWithText("已选 1 本").assertExists()

        // 点击删除所选呼出模态框
        onNodeWithText("删除所选 (1)").performClick()
        onNodeWithText("删除所选 1 项漫画话册？").assertExists()

        // 确认删除
        onNodeWithText("删除这 1 项").performClick()
        waitForIdle()
        assertTrue("模态框确认删除必须触发真正删除回调", deleteChapterInvoked)
    }

    @Test
    fun 漫画与图片双Tab稳固并存且图片空态下打开图片目录把手常驻() = runComposeUiTest {
        var openImageSaveDirInvoked = false

        setContentThemed {
            Stage {
                DesktopDownloadsPipelineScreen(
                    mode = WideScreenLayoutMode.Expanded,
                    queueTasks = emptyList(),
                    queuePrimed = true,
                    queueNote = null,
                    downloadedListing = null,
                    downloadedNote = null,
                    onRepairDownloadedChapter = {},
                    onDeleteDownloadedChapter = {},
                    onOpenDownloadedChapterDir = {},
                    localComicScan = null,
                    localComicNote = null,
                    images = emptyList(),
                    onImportComics = {},
                    onOpenImageSaveDir = { openImageSaveDirInvoked = true },
                    onOpenSettingsDownloads = {},
                    onBack = {},
                )
            }
        }

        // 漫画与图片两个 Tab 必须同时存在，杜绝吞 Tab 单紫块
        onNodeWithText("漫画 (0)").assertExists()
        onNodeWithText("图片 (0)").assertExists()

        // 切换到图片档
        onNodeWithText("图片 (0)").performClick()
        onNodeWithText("暂无已下载的画廊图片（可在作品详情或画廊点击下载）").assertExists()

        // 空态下「打开图片目录」把手依然常驻并可点击
        val openDirBtn = onNodeWithText("打开图片目录")
        openDirBtn.assertExists()
        openDirBtn.performClick()
        assertTrue("点击打开图片目录必须触发 onOpenImageSaveDir 回调", openImageSaveDirInvoked)
    }

    @Test
    fun 漫画真实名称映射与本地导入漫画批量删除回调正常生效() = runComposeUiTest {
        var deleteLocalComicInvoked = false

        setContentThemed {
            Stage {
                DesktopDownloadsPipelineScreen(
                    mode = WideScreenLayoutMode.Expanded,
                    queueTasks = listOf(sampleTask(1).copy(comicId = "comic_hash_123", comicTitle = "真实作品名：芙莉莲")),
                    queuePrimed = true,
                    queueNote = null,
                    downloadedListing = null,
                    downloadedNote = null,
                    onRepairDownloadedChapter = {},
                    onDeleteDownloadedChapter = {},
                    onOpenDownloadedChapterDir = {},
                    localComicScan = DesktopLocalComicLibraryScan(
                        comics = listOf(
                            DesktopLocalComic(
                                id = "comic_hash_123",
                                title = "comic_hash_123", // 磁盘原始为哈希ID
                                sourceKey = "imported",
                                chapterCount = 1,
                                totalPages = 15,
                                rootPath = "/path/to/hash",
                                updatedAt = 1000L,
                            ),
                        ),
                        rootExists = true,
                        error = null,
                    ),
                    localComicNote = null,
                    onImportComics = {},
                    onDeleteLocalComic = { deleteLocalComicInvoked = true },
                    images = emptyList(),
                    onOpenSettingsDownloads = {},
                    onBack = {},
                )
            }
        }

        // 真实漫画名称必须从 queueTasks 中映射出来，而不是显示原始哈希ID
        onNodeWithText("真实作品名：芙莉莲").assertExists()

        // 多选模式下勾选并删除本地导入漫画
        onNodeWithText("选择").performClick()
        onNodeWithText("真实作品名：芙莉莲").performClick()
        onNodeWithText("删除所选 (1)").performClick()
        onNodeWithText("删除这 1 项").performClick()
        waitForIdle()

        assertTrue("模态框确认删除必须成功触发 onDeleteLocalComic 回调", deleteLocalComicInvoked)
    }

    @Test
    fun pixiv多图卡片展示P数与聚合信息() = runComposeUiTest {
        setContentThemed {
            Stage {
                DesktopDownloadsPipelineScreen(
                    mode = WideScreenLayoutMode.Expanded,
                    queueTasks = emptyList(),
                    queuePrimed = true,
                    queueNote = null,
                    downloadedListing = null,
                    downloadedNote = null,
                    onRepairDownloadedChapter = {},
                    onDeleteDownloadedChapter = {},
                    onOpenDownloadedChapterDir = {},
                    localComicScan = null,
                    localComicNote = null,
                    images = listOf(
                        sampleImage("99999").copy(
                            pageCount = 12,
                            allPaths = (0 until 12).map { "downloads/images/pixiv_99999_p$it.jpg" },
                        ),
                    ),
                    onImportComics = {},
                    onOpenSettingsDownloads = {},
                    onBack = {},
                )
            }
        }

        // 切换到图片分栏
        onNodeWithText("图片 (1)").performClick()
        onNodeWithText("pixiv · 99999").assertExists()
        // 验证多图 P 数角标
        onNodeWithText("12P").assertExists()
    }

    @Test
    fun 下载队列区分失败态警告态等待态与短时成功态并透出报错原因() = runComposeUiTest {
        val now = System.currentTimeMillis()
        var retriedTaskId: String? = null

        setContentThemed {
            Stage {
                DesktopDownloadsPipelineScreen(
                    mode = WideScreenLayoutMode.Expanded,
                    queueTasks = listOf(
                        sampleTask(1, DesktopComicQueueStatus.FAILED).copy(
                            taskId = "task_fail",
                            chapterTitle = "失败话",
                            error = "loadEp 失败: 源脚本异常",
                        ),
                        sampleTask(2, DesktopComicQueueStatus.STOPPED).copy(
                            taskId = "task_warn",
                            chapterTitle = "暂停话",
                        ),
                        sampleTask(3, DesktopComicQueueStatus.PENDING).copy(
                            taskId = "task_wait",
                            chapterTitle = "等待话",
                            downloadedPages = 0,
                            totalPages = 0,
                        ),
                        sampleTask(4, DesktopComicQueueStatus.DONE).copy(
                            taskId = "task_just_done",
                            chapterTitle = "刚完成话",
                            downloadedPages = 24,
                            totalPages = 24,
                            updateTime = now,
                        ),
                        sampleTask(5, DesktopComicQueueStatus.DONE).copy(
                            taskId = "task_old_done",
                            chapterTitle = "历史完成话",
                            downloadedPages = 24,
                            totalPages = 24,
                            updateTime = 2L,
                        ),
                    ),
                    queuePrimed = true,
                    queueNote = null,
                    onQueuePauseResume = { retriedTaskId = it.taskId },
                    downloadedListing = null,
                    downloadedNote = null,
                    onRepairDownloadedChapter = {},
                    onDeleteDownloadedChapter = {},
                    onOpenDownloadedChapterDir = {},
                    localComicScan = null,
                    localComicNote = null,
                    images = emptyList(),
                    onImportComics = {},
                    onOpenSettingsDownloads = {},
                    onBack = {},
                )
            }
        }

        // 失败态与真实报错原文
        onNodeWithText("下载失败").assertExists()
        onNodeWithText("loadEp 失败: 源脚本异常").assertExists()
        onNodeWithContentDescription("重试").performClick()
        assertTrue("点击失败态重试按钮必须触发对应任务回调", retriedTaskId == "task_fail")

        // 警告态与等待态分离
        onNodeWithText("已暂停").assertExists()
        onNodeWithText("等待中").assertExists()

        // 刚完成任务显示成功态，历史旧完成任务不显示
        onNodeWithText("下载成功").assertExists()
        onNodeWithText("芙莉莲 · 刚完成话").assertExists()
        onAllNodesWithText("芙莉莲 · 历史完成话").assertCountEquals(0)

        // 超过展示窗口后，短时成功态自动退出队列展示
        mainClock.advanceTimeBy(4_500L)
        waitForIdle()
        onAllNodesWithText("芙莉莲 · 刚完成话").assertCountEquals(0)
    }

    @Test
    fun 切换路径后保留旧漫画标题且不将下载中在线漫画误显示为导入() = runComposeUiTest {
        var importFolderClicked = false

        setContentThemed {
            Stage {
                DesktopDownloadsPipelineScreen(
                    mode = WideScreenLayoutMode.Expanded,
                    downloadsRoot = java.io.File("D:/new_empty_root"),
                    queueTasks = listOf(
                        sampleTask(1, DesktopComicQueueStatus.PENDING).copy(
                            sourceKey = "ehentai",
                            comicId = "https://exhentai.org/g/3599509/6fc81d929b/",
                            comicTitle = "下载中的本子",
                        ),
                    ),
                    queuePrimed = true,
                    queueNote = null,
                    downloadedListing = DesktopComicDownloadListing(
                        groups = listOf(
                            DesktopComicDownloadedGroup(
                                comicId = "old_100",
                                sourceKey = "jm",
                                chapters = listOf(
                                    sampleChapter(0).copy(
                                        title = "第07話",
                                        comicTitle = "旧目录的真实漫画标题",
                                        coverUrl = "https://example.com/old_cover.jpg",
                                        localDir = "C:/old_dir/jm/old_100/0000_第07話",
                                    ),
                                ),
                            ),
                        ),
                        skipped = 0,
                        rootExists = true,
                    ),
                    downloadedNote = null,
                    onRepairDownloadedChapter = {},
                    onDeleteDownloadedChapter = {},
                    onOpenDownloadedChapterDir = {},
                    localComicScan = DesktopLocalComicLibraryScan(
                        comics = listOf(
                            // 在线下载中的目录（sourceKey 非 imported）：绝不能出现在下面变成「导入」卡片
                            DesktopLocalComic(
                                id = "https___exhentai.org_g_3599509_6fc81d929b_",
                                title = "https___exhentai.org_g_3599509_6fc81d929b_",
                                sourceKey = "ehentai",
                                chapterCount = 1,
                                totalPages = 20,
                                rootPath = "D:/new_empty_root/ehentai/https___exhentai.org_g_3599509_6fc81d929b_",
                                updatedAt = 1000L,
                            ),
                        ),
                        rootExists = true,
                        error = null,
                    ),
                    importedComics = listOf(
                        DesktopLocalComic(
                            id = "folder_1",
                            title = "原地关联漫画文件夹",
                            sourceKey = "imported",
                            chapterCount = 2,
                            totalPages = 35,
                            rootPath = "E:/Comics/MyComic",
                            updatedAt = 2000L,
                            isLinked = true,
                        ),
                    ),
                    localComicNote = null,
                    onImportComics = {},
                    onImportComicFolder = { importFolderClicked = true },
                    onOpenSettingsDownloads = {},
                    onBack = {},
                )
            }
        }

        // 台账中的真实书名必须展示，不能因换了 downloadsRoot 而退回第07話
        onNodeWithText("旧目录的真实漫画标题").assertExists()
        // 下载中的 ehentai 目录不得被当成本地导入卡展示
        onAllNodesWithText("https___exhentai.org_g_3599509_6fc81d929b_").assertCountEquals(0)
        // 原地关联的文件夹漫画展示「文件夹」胶囊与书名
        onNodeWithText("原地关联漫画文件夹").assertExists()
        onNodeWithText("文件夹").assertExists()
        // 点击「导入文件夹」按钮触发回调
        onNodeWithText("导入文件夹").performClick()
        assertTrue("点击导入文件夹按钮必须触发回调", importFolderClicked)
    }
}
