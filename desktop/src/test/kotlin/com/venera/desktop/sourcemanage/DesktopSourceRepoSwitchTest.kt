package com.venera.desktop.sourcemanage

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.venera.compose.data.platform.PathProvider
import com.venera.desktop.data.writeDesktopSourceEnabled
import com.venera.desktop.gallery.data.DesktopSourceOverrides
import com.venera.desktop.gallery.data.DesktopSourceRepoPreferences
import com.venera.desktop.gallery.data.DesktopSourceState
import com.venera.desktop.gallery.data.DesktopSourceUpdater
import com.venera.desktop.setContentThemed
import com.venera.engineprobe.DesktopSourceUpdate
import com.venera.engineprobe.DesktopSourceUpdate.SourceRepo
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class DesktopSourceRepoSwitchTest {

    @Test
    fun `parseIndex支持vensrc独立url字段与description中的花括号`() {
        val raw = """
            [
              {
                "name": "动漫屋",
                "key": "dm5",
                "version": "7.0.3",
                "fileName": "dm5.js",
                "url": "https://raw.githubusercontent.com/handahao666-boop/venera_comic_source/abc123/dm5.js",
                "description": "引用源：{handahao666-boop}"
              }
            ]
        """.trimIndent()
        val parsed = DesktopSourceUpdate.parseIndex(raw, SourceRepo.VENSRC_MAIN.id)
        assertEquals(1, parsed.size)
        val entry = parsed.first()
        assertEquals("dm5", entry.key)
        assertEquals("7.0.3", entry.version)
        assertEquals("https://raw.githubusercontent.com/handahao666-boop/venera_comic_source/abc123/dm5.js", entry.url)
        assertEquals(SourceRepo.VENSRC_MAIN.id, entry.repoId)
        assertEquals("引用源：{handahao666-boop}", entry.description)

        val candidatesNoProxy = DesktopSourceUpdate.downloadCandidates(entry, hasExplicitProxy = false)
        assertEquals(
            "https://cdn.jsdelivr.net/gh/handahao666-boop/venera_comic_source@abc123/dm5.js",
            candidatesNoProxy.first(),
        )
        assertTrue(candidatesNoProxy.contains(entry.url))
    }

    @Test
    fun `DesktopSourceUpdater正确记录仓库来源并保护手改稿`() {
        val dir = Files.createTempDirectory("vensrc-updater-test").toFile()
        try {
            DesktopSourceOverrides.writeText(dir, "dm5", "class Dm5 extends ComicSource { key = \"dm5\" }")
            DesktopSourceUpdater.markInstalled(dir, "dm5", "7.0.3", SourceRepo.VENSRC_MAIN.id)
            assertEquals(SourceRepo.VENSRC_MAIN.id, DesktopSourceUpdater.repoIdOf(dir, "dm5"))
            assertEquals(
                "laincat/vensrc",
                DesktopSourceUpdater.originLabelOf(isBundled = false, overridden = true, repoId = DesktopSourceUpdater.repoIdOf(dir, "dm5")),
            )
            assertTrue(DesktopSourceUpdater.canAutoOverwrite(dir, "dm5", "7.0.3"))
            assertFalse("本地脚本版本与登记不一致时视为手改稿，不覆盖", DesktopSourceUpdater.canAutoOverwrite(dir, "dm5", "7.0.4"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `writeDesktopSourceEnabled支持非随包导入源的独立启停`() {
        val root = Files.createTempDirectory("vensrc-enable-test").toFile()
        try {
            val assetDir = File(root, "assets").apply { mkdirs() }
            File(assetDir, "sources").mkdirs()
            File(assetDir, "sources/baozi.js").writeText("class Baozi extends ComicSource {}")
            val overrideDir = File(root, "source_overrides").apply { mkdirs() }
            DesktopSourceOverrides.writeText(overrideDir, "dm5", "class Dm5 extends ComicSource {}")

            val paths = object : PathProvider {
                override val dataRoot: File = File(root, "data").apply { mkdirs() }
                override val cacheRoot: File = File(root, "cache").apply { mkdirs() }
            }
            val state = DesktopSourceState(paths)
            writeDesktopSourceEnabled(assetDir, state, "dm5", overrideDir, enabled = true)
            assertTrue("新导入的 dm5 必须可独立启用", "dm5" in state.enabledKeys().orEmpty())

            writeDesktopSourceEnabled(assetDir, state, "dm5", overrideDir, enabled = false)
            assertFalse("新导入的 dm5 必须可独立关闭", "dm5" in state.enabledKeys().orEmpty())

            DesktopSourceRepoPreferences.saveSelectedRepo(paths, SourceRepo.VENSRC_EXT)
            assertEquals(SourceRepo.VENSRC_EXT, DesktopSourceRepoPreferences.readSelectedRepo(paths))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `站点墙展示源仓库切换与laincat_vensrc标注并可点击切换`() = runComposeUiTest {
        var repo by mutableStateOf(SourceRepo.OFFICIAL)
        var selected by mutableStateOf<String?>("dm5")
        var storeOpened = 0
        var syncClicked = 0
        val toggled = mutableListOf<Pair<String, Boolean>>()
        setContentThemed {
            Box(Modifier.fillMaxSize()) {
                DesktopSourceManageScreen(
                    rows = listOf(
                        DesktopSourceRowUi(
                            key = "dm5",
                            title = "动漫屋",
                            enabled = true,
                            overridden = true,
                            version = "7.0.3",
                            originLabel = "laincat/vensrc",
                            isBundled = false,
                        ),
                    ),
                    selectedKey = selected,
                    onSelectedChange = { selected = it },
                    note = null,
                    onToggleSource = { k, on -> toggled += k to on },
                    onBack = {},
                    repoBar = DesktopSourceRepoBarUi(
                        selectedRepo = repo,
                        summaryText = repo.displayName,
                        hasUpdatables = false,
                        busy = false,
                        onSelectRepo = { repo = it },
                        onOpenStore = { storeOpened++ },
                        onCheckSync = { syncClicked++ },
                    ),
                )
            }
        }
        onAllNodesWithText("laincat/vensrc").assertCountEquals(2)
        onNodeWithText("v7.0.3 · laincat/vensrc").assertExists()
        onNodeWithText("✕").performClick()
        waitForIdle()
        onNodeWithText("社区源 · laincat/vensrc").performClick()
        waitForIdle()
        assertEquals(SourceRepo.VENSRC_MAIN, repo)
        onNodeWithText("来自 laincat/vensrc").assertExists()
        onNodeWithText("扩展源 (71)").performClick()
        waitForIdle()
        assertEquals(SourceRepo.VENSRC_EXT, repo)
        onNodeWithText("浏览源库").performClick()
        onNodeWithText("升级已有站").performClick()
        onNodeWithContentDescription("站点开关 dm5").performClick()
        assertEquals(1, storeOpened)
        assertEquals(1, syncClicked)
        assertEquals(listOf("dm5" to false), toggled)
    }

    @Test
    fun `DesktopSourceNames支持单引号与分号分隔头部字段提取`() {
        val compact = "class CopyWeb extends ComicSource {\n    name='拷贝漫画'; key='copy_web_routes'; version = \"2.3.2\";\n}"
        assertEquals("拷贝漫画", com.venera.desktop.gallery.data.DesktopSourceNames.displayNameOf(compact))
        assertEquals("copy_web_routes", com.venera.desktop.gallery.data.DesktopSourceNames.keyOf(compact))
        assertEquals("2.3.2", com.venera.desktop.gallery.data.DesktopSourceNames.versionOf(compact))
    }

    @Test
    fun `在线站点库弹窗支持直接开关源显示且不拼残缺版本号`() = runComposeUiTest {
        var enabledState by mutableStateOf(true)
        val toggled = mutableListOf<Pair<String, Boolean>>()
        val row = DesktopSourceRowUi(key = "copy_manga", title = "拷贝漫画", enabled = enabledState, version = "")
        setContentThemed {
            Box(Modifier.fillMaxSize()) {
                DesktopSourceStore(
                    selectedRepo = SourceRepo.OFFICIAL,
                    localRowOf = { row.copy(enabled = enabledState) },
                    onToggleEnabled = { k, on ->
                        enabledState = on
                        toggled += k to on
                    },
                    onInstall = { null },
                    onClose = {},
                )
            }
        }
        onNodeWithText("在线站点库").assertExists()
    }

    @Test
    fun `resolveStoreItemState区分同源升级与异源切换`() {
        val officialEntry = DesktopSourceUpdate.Entry(
            name = "拷贝漫画",
            fileName = "copy_manga.js",
            key = "copy_manga",
            version = "2.0.0",
            repoId = SourceRepo.OFFICIAL.id,
        )
        val vensrcEntry = officialEntry.copy(repoId = SourceRepo.VENSRC_EXT.id)

        val bundledRow = DesktopSourceRowUi(
            key = "copy_manga",
            title = "拷贝漫画",
            enabled = true,
            overridden = false,
            version = "1.4.0",
            originLabel = "内置站点",
            repoId = null,
            isBundled = true,
        )
        assertEquals(StoreItemState.Upgradable, resolveStoreItemState(officialEntry, bundledRow, SourceRepo.OFFICIAL))
        assertEquals(StoreItemState.Switchable, resolveStoreItemState(vensrcEntry, bundledRow, SourceRepo.VENSRC_EXT))

        val vensrcMainRow = DesktopSourceRowUi(
            key = "copy_manga",
            title = "拷贝漫画",
            enabled = true,
            overridden = true,
            version = "1.4.0",
            originLabel = "laincat/vensrc",
            repoId = SourceRepo.VENSRC_MAIN.id,
            isBundled = true,
        )
        assertEquals(StoreItemState.Upgradable, resolveStoreItemState(vensrcEntry, vensrcMainRow, SourceRepo.VENSRC_EXT))
        assertEquals(StoreItemState.Switchable, resolveStoreItemState(officialEntry, vensrcMainRow, SourceRepo.OFFICIAL))

        val localRow = bundledRow.copy(overridden = true, originLabel = "本地脚本", repoId = DesktopSourceUpdater.REPO_LOCAL)
        assertEquals(StoreItemState.Switchable, resolveStoreItemState(officialEntry, localRow, SourceRepo.OFFICIAL))
        assertEquals(StoreItemState.Switchable, resolveStoreItemState(vensrcEntry, localRow, SourceRepo.VENSRC_EXT))
    }

    @Test
    fun `在线站点库界面严格区分可升级与可切换并触发对应反馈`() = runComposeUiTest {
        val entries = listOf(
            DesktopSourceUpdate.Entry("同源可升站", "a.js", "same_up", "1.2.0", repoId = SourceRepo.VENSRC_EXT.id),
            DesktopSourceUpdate.Entry("异源内置站", "b.js", "cross_origin", "2.0.0", repoId = SourceRepo.VENSRC_EXT.id),
            DesktopSourceUpdate.Entry("同源最新站", "c.js", "same_latest", "1.5.0", repoId = SourceRepo.VENSRC_EXT.id),
        )
        val locals = mapOf(
            "same_up" to DesktopSourceRowUi(
                key = "same_up",
                title = "同源可升站",
                enabled = true,
                overridden = true,
                version = "1.0.0",
                originLabel = "laincat/vensrc",
                repoId = SourceRepo.VENSRC_MAIN.id,
                isBundled = false,
            ),
            "cross_origin" to DesktopSourceRowUi(
                key = "cross_origin",
                title = "异源内置站",
                enabled = true,
                overridden = false,
                version = "1.0.0",
                originLabel = "内置站点",
                repoId = null,
                isBundled = true,
            ),
            "same_latest" to DesktopSourceRowUi(
                key = "same_latest",
                title = "同源最新站",
                enabled = true,
                overridden = true,
                version = "1.5.0",
                originLabel = "laincat/vensrc",
                repoId = SourceRepo.VENSRC_EXT.id,
                isBundled = false,
            ),
        )
        val installedCalls = mutableListOf<String>()
        setContentThemed {
            Box(Modifier.fillMaxSize()) {
                DesktopSourceStore(
                    selectedRepo = SourceRepo.VENSRC_EXT,
                    localRowOf = { locals[it.key] },
                    initialEntries = entries,
                    onInstall = {
                        installedCalls += it.key
                        null
                    },
                    onClose = {},
                )
            }
        }
        waitForIdle()
        onNodeWithText("可升级 (1)").assertExists()
        onNodeWithText("可切换 (1)").assertExists()
        onNodeWithText("已装 内置站点").assertExists()
        onNodeWithText("升级").assertExists()
        onNodeWithText("切换").assertExists()
        onNodeWithText("已是最新").assertExists()

        onNodeWithText("可切换 (1)").performClick()
        waitForIdle()
        onNodeWithText("异源内置站").assertExists()
        onNodeWithText("切换").performClick()
        waitForIdle()
        assertEquals(listOf("cross_origin"), installedCalls)
        onNodeWithText("已将「异源内置站」切换为 laincat/vensrc").assertExists()
    }
}
