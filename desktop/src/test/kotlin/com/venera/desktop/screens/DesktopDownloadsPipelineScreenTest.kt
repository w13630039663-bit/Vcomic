package com.venera.desktop.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
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
}
