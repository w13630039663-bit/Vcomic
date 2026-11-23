package com.venera.desktop.sourcemanage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.ui.DesktopPushButton
import com.venera.desktop.ui.theme.VeneraType
import com.venera.engineprobe.DesktopSourceUpdate
import kotlinx.coroutines.launch

/**
 * 在线源列表（源商店）。
 *
 * 「导入 .js」要求用户先去别处找到脚本文件，这一屏把那份清单直接摆出来：
 * 上游 `venera-configs` 的 `index.json` 就是唯一的清单来源，与启动自检对账同一份。
 *
 * 本地装没装由 [localVersionOf] 回答（壳读改写层与包内的实际版本），
 * 下载与落盘全交给 [onInstall] —— 这一屏只管展示与触发，不碰路径也不碰引擎。
 */
@Composable
internal fun DesktopSourceStore(
    /** 某个文件名键在本机的版本；null = 没装。 */
    localVersionOf: (fileKey: String) -> String?,
    /** 装一颗；交回 null = 成功，非 null = 失败成因。 */
    onInstall: suspend (fileName: String, fileKey: String, name: String) -> String?,
    onClose: () -> Unit,
) {
    val tokens = VeneraTokens
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<DesktopSourceUpdate.Entry>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var busyKey by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(reload) {
        entries = null
        error = null
        val proxy = System.getProperty("venera.proxy")?.takeIf { it.isNotBlank() }
        val got = runCatching { DesktopSourceUpdate.fetchIndex(proxy) }
        got.fold(
            onSuccess = { list ->
                if (list.isEmpty()) error = "清单拉不到（网络受阻？）" else entries = list
            },
            onFailure = { t ->
                println("D_源清单更新 拉取失败 ${t.javaClass.simpleName}: ${t.message?.take(160) ?: ""}")
                com.venera.desktop.DesktopErrorBus.report(
                    category = "漫画",
                    headline = "源清单更新拉取失败",
                    humanReason = "更新清单没拉到，多半是这条线路被挡住；可稍后重试或先配代理。",
                    detail = "${t.javaClass.simpleName}: ${t.message.orEmpty()}",
                    dedupKey = "comic:source_index_fetch",
                )
                error = "清单拉不到：${com.venera.desktop.platform.desktopFailureNoteOf(t)}"
            },
        )
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(tokens.color.surface),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            com.venera.desktop.settings.DesktopI18n.t("在线源", "Online Sources"),
                            fontSize = VeneraType.pageTitle.size,
                            lineHeight = VeneraType.pageTitle.lineHeight,
                            color = tokens.color.textPrimary,
                        )
                        Text(
                            text = com.venera.desktop.settings.DesktopI18n.t("官方仓库 · ${entries?.size ?: 0} 颗", "Official Repo · ${entries?.size ?: 0} sources"),
                            fontSize = VeneraType.aux.size,
                            lineHeight = VeneraType.aux.lineHeight,
                            color = tokens.color.textTertiary,
                        )
                    }
                    DesktopPushButton(onClick = { reload++ }, enabled = busyKey == null) { Text(com.venera.desktop.settings.DesktopI18n.t("刷新", "Refresh")) }
                    DesktopPushButton(onClick = onClose, enabled = busyKey == null) { Text(com.venera.desktop.settings.DesktopI18n.t("关闭", "Close")) }
                }
                Spacer(Modifier.height(10.dp))

                error?.let {
                    Text(
                        text = it,
                        fontSize = VeneraType.aux.size,
                        lineHeight = VeneraType.aux.lineHeight,
                        color = tokens.color.textTertiary,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                note?.let {
                    Text(
                        text = it,
                        fontSize = VeneraType.aux.size,
                        lineHeight = VeneraType.aux.lineHeight,
                        color = tokens.color.primary,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }

                val list = entries
                if (list == null && error == null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(com.venera.desktop.settings.DesktopI18n.t("读取中…", "Loading…"), fontSize = VeneraType.aux.size, color = tokens.color.textTertiary)
                    }
                } else if (list != null) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(list, key = { it.fileName }) { entry ->
                            val fileKey = entry.fileName.removeSuffix(".js")
                            val local = localVersionOf(fileKey)
                            val newer = local != null && DesktopSourceUpdate.isNewer(entry.version, local)
                            StoreRow(
                                name = entry.name,
                                description = entry.description,
                                remoteVersion = entry.version,
                                localVersion = local,
                                busy = busyKey == fileKey,
                                onInstall = {
                                    busyKey = fileKey
                                    note = null
                                    scope.launch {
                                        val why = onInstall(entry.fileName, fileKey, entry.name)
                                        busyKey = null
                                        note = if (why == null) com.venera.desktop.settings.DesktopI18n.tr("已装：${entry.name}", "Installed: ${entry.name}") else "${entry.name}: $why"
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StoreRow(
    name: String,
    description: String?,
    remoteVersion: String,
    localVersion: String?,
    busy: Boolean,
    onInstall: () -> Unit,
) {
    val tokens = VeneraTokens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                fontSize = VeneraType.body.size,
                lineHeight = VeneraType.body.lineHeight,
                color = tokens.color.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val isEn = com.venera.desktop.settings.LocalDesktopLanguage.current == com.venera.desktop.settings.DesktopLanguage.EN
            Text(
                text = buildString {
                    append(if (localVersion.isNullOrBlank()) (if (isEn) "Not installed" else "未安装") else (if (isEn) "Installed $localVersion" else "已装 $localVersion"))
                    if (remoteVersion.isNotBlank()) append(if (isEn) " · Remote $remoteVersion" else " · 线上 $remoteVersion")
                    if (!description.isNullOrBlank()) append(" · $description")
                },
                fontSize = VeneraType.micro.size,
                lineHeight = VeneraType.micro.lineHeight,
                color = tokens.color.textTertiary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when {
            busy -> CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
            localVersion == null -> DesktopPushButton(onClick = onInstall) { Text(com.venera.desktop.settings.DesktopI18n.t("安装", "Install")) }
            DesktopSourceUpdate.isNewer(remoteVersion, localVersion) ->
                DesktopPushButton(onClick = onInstall) { Text(com.venera.desktop.settings.DesktopI18n.t("更新", "Update")) }
            else -> Text(
                text = com.venera.desktop.settings.DesktopI18n.t("已是最新", "Up to date"),
                fontSize = VeneraType.aux.size,
                lineHeight = VeneraType.aux.lineHeight,
                color = tokens.color.textTertiary,
                modifier = Modifier
                    .background(Color.Transparent, RoundedCornerShape(5.dp))
                    .padding(horizontal = 4.dp),
            )
        }
    }
}
