package com.venera.desktop.app

import com.venera.compose.data.platform.PathProvider
import com.venera.desktop.data.writeDesktopSourceEnabled
import com.venera.desktop.gallery.data.DesktopSourceNames
import com.venera.desktop.gallery.data.DesktopSourceOverrides
import com.venera.desktop.gallery.data.DesktopSourceState
import com.venera.desktop.gallery.data.DesktopSourceUpdater
import com.venera.desktop.platform.DesktopNotifier
import com.venera.desktop.platform.desktopFailureNoteOf
import com.venera.desktop.platform.desktopPickOpenFile
import com.venera.desktop.ui.SourceManageUiState
import com.venera.engineprobe.DesktopSourceUpdate
import com.venera.engineprobe.DesktopSourceUpdate.Entry
import com.venera.engineprobe.DesktopSourceUpdate.SourceRepo
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 导入本地 `.js` 站点脚本：校验语法与 `ComicSource` 声明、落盘、登记本地来源并自动启用。
 */
internal fun importLocalSourceScript(
    installSourceText: (suspend (String, String, String, String) -> String?)?,
    sourceUi: SourceManageUiState,
    scope: CoroutineScope,
    onSuccess: (String) -> Unit,
) {
    val install = installSourceText
    if (install == null) {
        sourceUi.sourceNote = "脚本目录未就绪，导入没跑起来"
        return
    }
    val picked = desktopPickOpenFile(null, "导入站点脚本", "*.js")
        ?.let { File(it) } ?: return
    val text = runCatching { picked.readText() }.getOrElse { t ->
        sourceUi.sourceNote = "读文件失败：${t.message ?: t.javaClass.simpleName}"
        return
    }
    val key = DesktopSourceNames.keyOf(text) ?: run {
        sourceUi.sourceNote = "导入失败：脚本里找不到 key = \"...\" 声明"
        return
    }
    val title = DesktopSourceNames.displayNameOf(text) ?: key
    val version = DesktopSourceNames.versionOf(text)
    scope.launch {
        val why = install(key, text, version, DesktopSourceUpdater.REPO_LOCAL)
        if (why == null) {
            sourceUi.sourceSelectedKey = key
            sourceUi.sourceNote = "已导入「$title」（$key），即刻生效"
            sourceUi.sourceSettingsNote = null
            onSuccess(key)
        } else {
            sourceUi.sourceNote = "导入「$title」失败：$why"
        }
    }
}

/**
 * 安装或升级在线源库中的单条站点脚本：下载原文 → 引擎验载 → 原子落盘并记录所属仓库。
 */
internal suspend fun installRepoSourceEntry(
    entry: Entry,
    proxy: String?,
    overrideDir: File?,
    assetDir: File?,
    paths: PathProvider?,
    validateScript: suspend (fileKey: String, text: String) -> Result<String?>,
    onSuccess: (installedKey: String) -> Unit,
): String? {
    val dir = overrideDir ?: return "改写层目录建不出来，装不进去。"
    val downloaded = withContext(Dispatchers.IO) {
        DesktopSourceUpdate.downloadResult(entry, proxy)
    }
    val text = downloaded.getOrElse { t ->
        return "下载失败：${desktopFailureNoteOf(t)}"
    }
    val scriptKey = DesktopSourceNames.keyOf(text)?.takeIf { DesktopSourceOverrides.isSafeKey(it) }
    val fileKey = entry.fileName.substringAfterLast('/').removeSuffix(".js")
    val targetKey = when {
        scriptKey != null -> scriptKey
        DesktopSourceOverrides.isSafeKey(entry.key) -> entry.key
        DesktopSourceOverrides.isSafeKey(fileKey) -> fileKey
        else -> return "源键名不合法：${entry.key}"
    }
    return validateScript(targetKey, text).fold(
        onSuccess = {
            DesktopSourceOverrides.writeText(dir, targetKey, text)
            val version = entry.version.ifBlank { DesktopSourceNames.versionOf(text) }
            DesktopSourceUpdater.markInstalled(dir, targetKey, version, entry.repoId)
            if (assetDir != null && paths != null) {
                runCatching {
                    writeDesktopSourceEnabled(
                        assetDir = assetDir,
                        sourceState = DesktopSourceState(paths),
                        key = targetKey,
                        overrideDir = dir,
                        enabled = true,
                    )
                }
            }
            onSuccess(targetKey)
            null
        },
        onFailure = { "验装载没过：${it.message?.take(140) ?: it.javaClass.simpleName}" },
    )
}

/**
 * 按当前选中的仓库拉取清单并检查/同步同名站点的更新（保留用户手改稿不覆盖）。
 */
internal suspend fun syncSelectedSourceRepo(
    announce: Boolean,
    repo: SourceRepo,
    proxy: String?,
    sourceUi: SourceManageUiState,
    overrideDir: File?,
    validateScript: suspend (fileKey: String, text: String) -> Result<String?>,
    onTick: () -> Unit,
) {
    if (sourceUi.sourceUpdateBusy) return
    sourceUi.sourceUpdateBusy = true
    sourceUi.sourceUpdateNote = null
    try {
        val indexResult = withContext(Dispatchers.IO) {
            DesktopSourceUpdate.fetchIndexResult(repo, proxy)
        }
        val entries = indexResult.getOrElse { t ->
            sourceUi.sourceUpdateNote = "${repo.displayName} 清单拉不到：${desktopFailureNoteOf(t)}"
            return
        }
        sourceUi.repoCatalogEntries = entries
        sourceUi.repoCatalogRepo = repo

        val localByKey = sourceUi.sourceRows.associateBy { it.key }
        val localByLower = sourceUi.sourceRows.associateBy { it.key.lowercase() }
        val pending = mutableListOf<String>()
        val updatables = entries.mapNotNull { entry ->
            val fileKey = entry.fileName.substringAfterLast('/').removeSuffix(".js")
            val localRow = (
                localByKey[entry.key]
                    ?: localByLower[entry.key.lowercase()]
                    ?: if (entry.key.equals(fileKey, ignoreCase = true)) localByKey[fileKey] else null
                ) ?: return@mapNotNull null
            if (!DesktopSourceUpdater.isSameRepoOrigin(localRow.isBundled, localRow.overridden, localRow.repoId, repo)) {
                return@mapNotNull null
            }
            val matchedKey = localRow.key
            val localVersion = localRow.version
            if (entry.version.isBlank() || !DesktopSourceUpdate.isNewer(entry.version, localVersion)) {
                return@mapNotNull null
            }
            if (overrideDir != null && !DesktopSourceUpdater.canAutoOverwrite(overrideDir, matchedKey, localVersion)) {
                pending += entry.name
                return@mapNotNull null
            }
            matchedKey to entry
        }

        if (updatables.isEmpty() && pending.isEmpty()) {
            sourceUi.sourceUpdateNote = "已对照 ${repo.displayName}（共 ${entries.size} 个）：同源站点均为最新；如需切换来源或新增站点请点「浏览源库」。"
            return
        }

        var ok = 0
        val done = mutableListOf<String>()
        val fails = mutableListOf<String>()
        updatables.forEach { (localKey, entry) ->
            val downloaded = withContext(Dispatchers.IO) {
                DesktopSourceUpdate.downloadResult(entry, proxy)
            }
            val text = downloaded.getOrElse { t ->
                fails += "${entry.name}（${desktopFailureNoteOf(t)}）"
                return@forEach
            }
            validateScript(localKey, text).fold(
                onSuccess = {
                    val d = overrideDir
                    if (d == null) {
                        fails += "${entry.name}（改写层不在手）"
                    } else {
                        DesktopSourceOverrides.writeText(d, localKey, text)
                        DesktopSourceUpdater.markInstalled(d, localKey, entry.version, repo.id)
                        ok += 1
                        done += entry.name
                    }
                },
                onFailure = { t ->
                    fails += "${entry.name}（${t.message?.take(80) ?: t.javaClass.simpleName}）"
                },
            )
        }
        sourceUi.sourceUpdateNote = buildString {
            append("${repo.displayName} 同步完成：升级 $ok 个")
            if (pending.isNotEmpty()) append("；跳过手改 ${pending.size} 个（${pending.take(3).joinToString("、")}）")
            if (fails.isNotEmpty()) append("；失败 ${fails.size} 个（${fails.take(3).joinToString("、")}）")
        }
        if (announce && ok > 0) {
            DesktopNotifier.notify("${repo.displayName} 已升级 $ok 个", done.joinToString("、"))
        }
        if (ok > 0) onTick()
    } finally {
        sourceUi.sourceUpdateBusy = false
    }
}
