package com.venera.desktop.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import com.google.gson.JsonArray
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.desktop.DesktopArchivePreview
import com.venera.desktop.DesktopBackupImportStage
import com.venera.desktop.DesktopBackupModule
import com.venera.desktop.DesktopBackupScreen
import com.venera.desktop.DesktopImportStrategy
import com.venera.desktop.DesktopLocalAssetCounts
import com.venera.desktop.DesktopParsedArchivePayload
import com.venera.desktop.DesktopVeneraRestoreOutcome
import com.venera.desktop.platform.DesktopWebDavSync
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 「备份与还原」那一屏的**离屏语义树**判据（工作台重做后）。
 *
 * ## 台架纪律（沿用 `DesktopComicHistoryScreenUiTest` 那几条）
 *
 *  - 一颗用例**只一层** `runComposeUiTest`；一律 `performClick` 而不用 `performSemanticsAction(OnClick)`
 *    （后者绕过使能闸门，拿它钉"busy 时按不动"会钉出假绿）。
 *  - 屏只吃形参：喂现成的 counts/armed/busy/三态，不开库、不开框（那两件事由源码尺 ⑤⑥⑦ 钉在壳与 runner 上）。
 *  - 默认台架是 1024×768，窄档会把导入工作区排到导出/WebDAV 之下；按它之前先 `performScrollTo()`，
 *    量的是"按压落点"而不是"恰好没滚出视口"。
 *
 * ## 这一批评的是那几族"按错就出事"的形状
 *
 * 覆盖导入会先 DELETE 再写入，是破坏性动作 —— 「两按才真灌」是最该红的判据（下面 ②③）。
 * 重做后第一次按落在预览态那枚「覆盖导入所选数据」上（`onArmRestore` 只武装），
 * 改口成「确认覆盖所选数据」再按才落 `onConfirmRestore`。
 */
@OptIn(ExperimentalTestApi::class)
class DesktopBackupScreenUiTest {

    private val wide = WideScreenLayoutMode.Expanded

    private var exports = 0
    private var arms = 0
    private var confirms = 0
    private var picks = 0
    private var backs = 0
    private fun reset() { exports = 0; arms = 0; confirms = 0; picks = 0; backs = 0 }

    @Composable
    private fun Stage(content: @Composable () -> Unit) {
        Box(Modifier.fillMaxSize()) { content() }
    }

    /** 预览态那份包信息；UI 判据只吃条数与模块可用性，负载留空即可。 */
    private fun preview(
        fileName: String = "venera_backup_1.venera",
        historyCount: Int = 4,
    ): DesktopArchivePreview = DesktopArchivePreview(
        path = "/tmp/$fileName",
        fileName = fileName,
        isVeneraZip = true,
        formatLabel = "通用 .venera 归档",
        sourceApp = "Venera Compose",
        version = 5,
        timestamp = 0L,
        historyCount = historyCount,
        favoriteCount = 0,
        favoriteFolderCount = 0,
        imageFavoriteCount = 0,
        galleryFavoriteCount = 0,
        galleryFollowCount = 0,
        statsCount = 0,
        guardRuleCount = 0,
        hasSourceAccounts = false,
        sourceAccountCount = 0,
        payload = DesktopParsedArchivePayload(
            history = JsonArray(),
            stats = JsonArray(),
            guardRules = JsonArray(),
            favoriteFolders = emptyList(),
            favoriteItems = emptyList(),
            imageFavorites = emptyList(),
            galleryFavorites = emptyList(),
            galleryFollows = emptyList(),
            sourceAccounts = emptyList(),
        ),
    )

    /**
     * 台架：五枚动作各数一次，其余状态由调用方点。写成 `ComposeUiTest` 的扩展函数，
     * 是因为 `setContent` 是那类型的成员 —— 普通私有函数里调不到它（§134 那颗历史台架同法）。
     */
    private fun androidx.compose.ui.test.ComposeUiTest.stage(
        armed: Boolean = false,
        busy: Boolean = false,
        note: String? = null,
        counts: DesktopLocalAssetCounts = DesktopLocalAssetCounts(),
        restore: DesktopVeneraRestoreOutcome? = null,
        canFileDialog: Boolean = true,
        importStage: DesktopBackupImportStage = DesktopBackupImportStage.IDLE,
        preview: DesktopArchivePreview? = null,
        strategy: DesktopImportStrategy = DesktopImportStrategy.OVERWRITE,
        selectedModules: Set<DesktopBackupModule> = setOf(DesktopBackupModule.HISTORY),
        importDragOver: Boolean = false,
    ) {
        setContent {
            Stage {
                DesktopBackupScreen(
                    mode = wide,
                    assetCounts = counts,
                    note = note,
                    includeSourceAccounts = false,
                    importStage = importStage,
                    preview = preview,
                    selectedModules = selectedModules,
                    importStrategy = strategy,
                    lastRestore = restore,
                    stagingFileName = null,
                    davConfig = null,
                    davState = DesktopWebDavSync.State.Idle,
                    davLastSync = null,
                    armed = armed,
                    busy = busy,
                    canFileDialog = canFileDialog,
                    importDragOver = importDragOver,
                    onToggleIncludeAccounts = {},
                    onExport = { exports++ },
                    onOpenDataDir = {},
                    onPickImportFile = { picks++ },
                    onInspectStagingFile = {},
                    onClearPreview = {},
                    onSelectStrategy = {},
                    onToggleModule = {},
                    onSelectAllModules = {},
                    onArmRestore = { arms++ },
                    onConfirmRestore = { confirms++ },
                    onResetImportDone = {},
                    onSaveDavConfig = {},
                    onTestDavConnection = {},
                    onDavUpload = {},
                    onDavDownloadAndPreview = {},
                    onDismissNote = {},
                    onBack = { backs++ },
                )
            }
        }
    }

    /** ① 待选态：导出与选包两枚各一枚；没解析前不摆真灌按钮。 */
    @Test
    fun 待选态摆导出与选包两枚而不摆真灌() = runComposeUiTest {
        reset()
        stage()
        onAllNodesWithText("导出 .venera 归档").assertCountEquals(1)
        onNodeWithText("拖入备份文件到此处，或点击浏览本机").assertExists()
        onNodeWithText("覆盖导入所选数据").assertDoesNotExist()
        onNodeWithText("确认覆盖所选数据").assertDoesNotExist()
        onNodeWithText("导出 .venera 归档").performScrollTo().performClick()
        assertEquals(1, exports)
        onNodeWithText("拖入备份文件到此处，或点击浏览本机").performScrollTo().performClick()
        assertEquals(1, picks)
        assertEquals("待选态绝不落真灌那一跳：", 0, confirms)
    }

    /** ② 已武装：那枚改口「确认覆盖所选数据」，按它才落 `onConfirmRestore`（两按才真灌）。 */
    @Test
    fun 已武装时那枚改口而按它才落真灌() = runComposeUiTest {
        reset()
        stage(armed = true, importStage = DesktopBackupImportStage.PREVIEW, preview = preview())
        onNodeWithText("覆盖导入所选数据").assertDoesNotExist()
        onNodeWithText("再次点击右侧按钮将立即清空并覆盖所选数据。").assertExists()
        onNodeWithText("确认覆盖所选数据").performScrollTo().assertExists()
        onNodeWithText("确认覆盖所选数据").performScrollTo().performClick()
        assertEquals(1, confirms)
        assertEquals("已武装后再按不该又走一遍武装：", 0, arms)
    }

    /** ②-b 未武装的预览态：第一次按「覆盖导入所选数据」只武装，不落真灌。 */
    @Test
    fun 未武装时第一次按只武装不落真灌() = runComposeUiTest {
        reset()
        stage(importStage = DesktopBackupImportStage.PREVIEW, preview = preview(), armed = false)
        onNodeWithText("覆盖模式将先删除已勾选模块的本机现有数据，且无法撤销。").assertExists()
        onNodeWithText("覆盖导入所选数据").performScrollTo().performClick()
        assertEquals(1, arms)
        assertEquals("第一次按绝不落真灌那一跳：", 0, confirms)
        onNodeWithText("确认覆盖所选数据").assertDoesNotExist()
    }

    /** ③ busy：导出与真灌两枚把手都不摆（跑着再按 = 双开）。 */
    @Test
    fun 正在跑时两枚把手都不摆() = runComposeUiTest {
        reset()
        stage(busy = true, importStage = DesktopBackupImportStage.PREVIEW, preview = preview())
        onAllNodesWithText("导出 .venera 归档").assertCountEquals(0)
        onAllNodesWithText("覆盖导入所选数据").assertCountEquals(0)
        onAllNodesWithText("确认覆盖所选数据").assertCountEquals(0)
        onNodeWithText("正在导入…").performScrollTo().assertExists()
    }

    /** ④ 无头：导出把手不摆、选包区按不动，只念那句实话（不摆一枚按不动的把手）。 */
    @Test
    fun 开不了文件框时不摆按不动的把手() = runComposeUiTest {
        reset()
        stage(canFileDialog = false)
        onAllNodesWithText("导出 .venera 归档").assertCountEquals(0)
        onNodeWithText("当前无法打开系统文件框，本地导出与选取文件功能已禁用。").assertExists()
        onNodeWithText("读取后将先展示包内数据明细与条数，确认后再写入数据库，不会直接覆盖当前数据。").assertExists()
        onNodeWithText("拖入备份文件到此处，或点击浏览本机").performScrollTo().performClick()
        assertEquals("无头时选包区点不动：", 0, picks)
    }

    /** ⑤ 本机资产读数与 note 都上屏：概览条摆实时条数，成因原样念出。 */
    @Test
    fun 本机资产读数与成因都上屏() = runComposeUiTest {
        reset()
        stage(
            counts = DesktopLocalAssetCounts(history = 3, stats = 1, guardRules = 2),
            note = "导出失败：IO 写不进去",
        )
        onNodeWithText("导出失败：IO 写不进去").assertExists()
        // 概览条历史 3；导出范围网格里历史 3、统计+屏蔽 1+2=3 —— 共三处。
        onAllNodesWithText("3").assertCountEquals(3)
        // 统计 1、屏蔽 2 在概览条各一处。
        onAllNodesWithText("1").assertCountEquals(1)
        onAllNodesWithText("2").assertCountEquals(1)
    }

    /** ⑥ 空资产：概览条摆六格零（画廊那格是「0 / 0」），不冒充成一次失败。 */
    @Test
    fun 空资产概览摆零而不冒充失败() = runComposeUiTest {
        reset()
        stage(counts = DesktopLocalAssetCounts())
        onNodeWithText("0 / 0").assertExists()
        onNodeWithText("作品 / 画师").assertExists()
        onAllNodesWithText("失败", substring = true).assertCountEquals(0)
    }

    /** ⑦ 还原结果带上跳过数那一档（少念几条 = 骗人说全灌进去了）。 */
    @Test
    fun 还原结果带跳过数那档() = runComposeUiTest {
        reset()
        stage(
            restore = DesktopVeneraRestoreOutcome(
                fileName = "venera_backup_1.venera",
                strategy = DesktopImportStrategy.MERGE,
                history = 4,
                stats = 2,
                guardImported = 1,
                guardSkipped = 3,
            ),
            importStage = DesktopBackupImportStage.DONE,
        )
        onNodeWithText("导入已完成（智能合并）").performScrollTo().assertExists()
        onNodeWithText("4 条").assertExists()
        onNodeWithText("已跳过 3 条无效正则表达式的屏蔽规则", substring = true).assertExists()
    }

    /** ⑧ 返回：按一次落一次。 */
    @Test
    fun 返回按得到() = runComposeUiTest {
        reset()
        stage()
        onNodeWithContentDescription("返回").performClick()
        assertEquals(1, backs)
    }
}
