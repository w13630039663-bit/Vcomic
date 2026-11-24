package com.venera.desktop.architecture

import com.venera.desktop.gallery.ui.DesktopSourceTree
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 文件体积上限（<= 40 KB）与根目录三层（`app/` / `data/` / `ui/`）隔离静态护栏。
 *
 * 1. 防止已完成拆分的模块与新增文件再次膨胀突破 40 KB；历史尚未拆分的大文件
 *    须在 [LEGACY_OVERSIZED_ALLOWLIST] 显式登记，只减不增。
 * 2. 强制 `com.venera.desktop` 根目录零散落 `.kt` 文件。
 * 3. 强制 `com.venera.desktop.data`、`:core-data`、`:gallery-core` 零 `androidx.compose`
 *    及 `com.venera.desktop.ui` 依赖，保证数据层可独立于 UI 运行与测试。
 */
class DesktopFileSizeAndLayerGuardTest {

    companion object {
        private const val MAX_FILE_BYTES = 40L * 1024L

        private val LEGACY_OVERSIZED_ALLOWLIST = setOf(
            "core-data/src/main/kotlin/com/venera/compose/data/platform/PlatformAbstractions.kt",
            "gallery-core/src/main/kotlin/com/venera/compose/gallery/data/PixivAppClient.kt",
            "desktop/src/main/kotlin/com/venera/compose/components/VeneraSideBar.kt",
            "desktop/src/main/kotlin/com/venera/compose/feature/ComicDetailCore.kt",
            "desktop/src/main/kotlin/com/venera/compose/gallery/ui/GalleryWallGrid.kt",
            "desktop/src/main/kotlin/com/venera/desktop/app/DesktopComicAndFilesEffects.kt",
            "desktop/src/main/kotlin/com/venera/desktop/app/DesktopGalleryWallEffects.kt",
            "desktop/src/main/kotlin/com/venera/desktop/app/DesktopLargeScreenRouter.kt",
            "desktop/src/main/kotlin/com/venera/desktop/app/DesktopPixivRoutesHost.kt",
            "desktop/src/main/kotlin/com/venera/desktop/app/DesktopSearchAndExploreEffects.kt",
            "desktop/src/main/kotlin/com/venera/desktop/app/DesktopSearchTabHost.kt",
            "desktop/src/main/kotlin/com/venera/desktop/data/DesktopVeneraArchive.kt",
            "desktop/src/main/kotlin/com/venera/desktop/detail/DesktopComicDetailScreen.kt",
            "desktop/src/main/kotlin/com/venera/desktop/detail/DesktopComicDetailSections.kt",
            "desktop/src/main/kotlin/com/venera/desktop/downloads/DesktopDownloadsPipelineScreen.kt",
            "desktop/src/main/kotlin/com/venera/desktop/favorites/DesktopFavoritesHubScreen.kt",
            "desktop/src/main/kotlin/com/venera/desktop/favorites/DesktopFavoritesScreen.kt",
            "desktop/src/main/kotlin/com/venera/desktop/favorites/DesktopNetworkFavoritesScreen.kt",
            "desktop/src/main/kotlin/com/venera/desktop/gallery/ui/DesktopArtistInboxScreen.kt",
            "desktop/src/main/kotlin/com/venera/desktop/gallery/ui/DesktopGalleryCard.kt",
            "desktop/src/main/kotlin/com/venera/desktop/gallery/ui/DesktopGalleryPostDetailScreen.kt",
            "desktop/src/main/kotlin/com/venera/desktop/gallery/ui/DesktopGalleryPostViewer.kt",
            "desktop/src/main/kotlin/com/venera/desktop/gallery/ui/DesktopGallerySearchScreen.kt",
            "desktop/src/main/kotlin/com/venera/desktop/history/DesktopHistoryScreen.kt",
            "desktop/src/main/kotlin/com/venera/desktop/installer/InstallerScenes.kt",
            "desktop/src/main/kotlin/com/venera/desktop/pixiv/DesktopPixivAccountScreen.kt",
            "desktop/src/main/kotlin/com/venera/desktop/pixiv/DesktopPixivWorkInfoPane.kt",
            "desktop/src/main/kotlin/com/venera/desktop/pixiv/DesktopPixivWorkScreen.kt",
            "desktop/src/main/kotlin/com/venera/desktop/reader/DesktopReaderScreen.kt",
            "desktop/src/main/kotlin/com/venera/desktop/reader/DesktopReaderSettingsOverlay.kt",
            "desktop/src/main/kotlin/com/venera/desktop/search/DesktopSearchScreen.kt",
            "desktop/src/main/kotlin/com/venera/desktop/sourcemanage/DesktopSourceManageScreen.kt",
            "desktop/src/main/kotlin/com/venera/desktop/ui/DesktopBackupScreen.kt",
            "desktop/src/main/kotlin/com/venera/desktop/ui/DesktopDwmBackdrop.kt",
            "desktop/src/main/kotlin/com/venera/desktop/ui/DesktopHomeLightScreen.kt",
            "desktop/src/main/kotlin/com/venera/desktop/ui/DesktopHomePane.kt",
            "desktop/src/main/kotlin/com/venera/desktop/ui/DesktopMacControls.kt",
            "desktop/src/main/kotlin/com/venera/desktop/ui/DesktopReadingStatsScreen.kt",
            "desktop/src/main/kotlin/com/venera/desktop/ui/DesktopWallScaffold.kt",
            "desktop/src/main/kotlin/com/venera/desktop/ui/VeneraLightRailSkin.kt",
        )
    }

    @Test
    fun `新增与已重构生产源文件体积不超过40KB`() {
        val repoRoot = DesktopSourceTree.repoRoot
        val sourceRoots = listOf(
            File(repoRoot, "desktop/src/main/kotlin"),
            File(repoRoot, "core-data/src/main/kotlin"),
            File(repoRoot, "gallery-core/src/main/kotlin"),
            File(repoRoot, "engine-probe/src/main/kotlin"),
        )
        val offenders = sourceRoots
            .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
            .mapNotNull { file ->
                val rel = file.relativeTo(repoRoot).invariantSeparatorsPath
                val bytes = file.length()
                if (bytes > MAX_FILE_BYTES && rel !in LEGACY_OVERSIZED_ALLOWLIST) {
                    "$rel (${bytes / 1024} KB)"
                } else {
                    null
                }
            }
        assertTrue(
            "存在未登记在历史白名单且超过 40 KB 的源码文件（请拆分后再提交）：$offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    fun `桌面根包无散落源文件且数据层零Compose和UI反向依赖`() {
        val repoRoot = DesktopSourceTree.repoRoot
        val desktopRootDir = File(repoRoot, "desktop/src/main/kotlin/com/venera/desktop")
        val strayRootFiles = desktopRootDir
            .listFiles { f -> f.isFile && f.extension == "kt" }
            ?.map { it.name }
            .orEmpty()
        assertTrue(
            "com.venera.desktop 根目录禁止保留散落文件（应归入 app/、data/、ui/ 或功能子包）：$strayRootFiles",
            strayRootFiles.isEmpty(),
        )

        val pureDataRoots = listOf(
            File(repoRoot, "desktop/src/main/kotlin/com/venera/desktop/data"),
            File(repoRoot, "core-data/src/main/kotlin"),
            File(repoRoot, "gallery-core/src/main/kotlin"),
        )
        val forbiddenImports = listOf(
            "import androidx.compose",
            "import com.venera.desktop.ui",
        )
        val violations = mutableListOf<String>()
        pureDataRoots.forEach { root ->
            root.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { file ->
                    val rel = file.relativeTo(repoRoot).invariantSeparatorsPath
                    val code = DesktopSourceTree.codeLinesOf(file)
                    forbiddenImports.forEach { forbidden ->
                        if (code.any { it.startsWith(forbidden) }) {
                            violations += "$rel -> $forbidden"
                        }
                    }
                }
        }
        assertTrue(
            "纯数据层禁止导入 Compose 或反向依赖桌面 UI 层：$violations",
            violations.isEmpty(),
        )
    }
}
