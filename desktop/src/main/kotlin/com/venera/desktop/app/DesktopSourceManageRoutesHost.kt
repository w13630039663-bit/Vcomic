package com.venera.desktop.app

import com.venera.desktop.data.DesktopErrorBus
import com.venera.desktop.ui.SourceManageUiState
import com.venera.desktop.data.desktopEngineCall
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.desktop.gallery.data.DesktopSourceSettings
import com.venera.desktop.platform.desktopFailureNoteOf
import com.venera.desktop.sourcemanage.DesktopSourceLoginScreen
import com.venera.desktop.sourcemanage.DesktopSourceManageScreen
import com.venera.engineprobe.EngineSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 漫画源管理、源脚本编辑与源登录（Cookie / 账密 / Edge 网页登录）路由实现。
 */
@Composable
internal fun DesktopSourceManageAndLoginRoute(
    r: DesktopRoute,
    layoutMode: WideScreenLayoutMode,
    engine: EngineSession?,
    scope: CoroutineScope,
    sourceUi: SourceManageUiState,
    runSourceImport: () -> Unit,
    writeSourceEnabled: ((String, Boolean) -> Unit)?,
    writeSourceOrder: ((List<String>) -> Unit)?,
    readSourceText: ((String) -> String?)?,
    clearSourceOverride: (suspend (String) -> Unit)?,
    loginWithCookies: (suspend (String, List<String>) -> String)?,
    loginWithPassword: (suspend (String, String, String) -> String)?,
    loginWithWebview: (suspend (String) -> String)?,
    routeState: MutableState<DesktopRoute>,
    sourceTickState: MutableIntState,
) {
    var route by routeState
    var sourceTick by sourceTickState

    when (r) {
        is DesktopRoute.SourceManage, is DesktopRoute.SourceEdit -> DesktopSourceManageScreen(
            rows = sourceUi.sourceRows,
            selectedKey = sourceUi.sourceSelectedKey,
            onSelectedChange = { sourceUi.sourceSelectedKey = it; sourceUi.sourceSettingsNote = null },
            onImportSource = runSourceImport,
            updateBusy = sourceUi.sourceUpdateBusy,
            updateNote = sourceUi.sourceUpdateNote,
            note = if (r is DesktopRoute.SourceEdit && sourceUi.sourceEditNote != null) {
                sourceUi.sourceEditNote
            } else {
                sourceUi.sourceNote
            },
            onToggleSource = { key, enabled ->
                runCatching { writeSourceEnabled?.invoke(key, enabled) }
                    .onFailure { t ->
                        println("D_源启停 写失败 $key ${t.javaClass.simpleName}: ${t.message?.take(160) ?: ""}")
                        DesktopErrorBus.report(
                            category = "漫画",
                            headline = "源启停开关写入失败",
                            humanReason = "这颗源的启停状态没写进配置，重启后会回到原状态；可再点一次。",
                            detail = "$key: ${t.javaClass.simpleName}: ${t.message.orEmpty()}",
                            dedupKey = "comic:source_enabled_write:$key",
                        )
                        sourceUi.sourceNote = desktopFailureNoteOf(t)
                    }
                sourceTick++
            },
            onReorderSources = writeSourceOrder?.let { writeOrder ->
                { ordered, enabled ->
                    runCatching { writeOrder.invoke(ordered) }
                        .onFailure { t ->
                            println("D_源顺序 写失败 ${t.javaClass.simpleName}: ${t.message?.take(160) ?: ""}")
                            DesktopErrorBus.report(
                                category = "漫画",
                                headline = "源顺序保存失败",
                                humanReason = "拖拽后的源顺序没写进配置，重启后会回到旧顺序；可再拖一次。",
                                detail = "${t.javaClass.simpleName}: ${t.message.orEmpty()}",
                                dedupKey = "comic:source_order_write",
                            )
                            sourceUi.sourceNote = desktopFailureNoteOf(t)
                        }
                    sourceUi.sourceRows.forEach { row ->
                        val should = row.key in enabled
                        if (row.enabled != should) {
                            runCatching { writeSourceEnabled?.invoke(row.key, should) }
                        }
                    }
                    sourceTick++
                }
            },
            onBack = { route = DesktopRoute.Tab },
            onEditSource = readSourceText?.let {
                { key ->
                    route = DesktopRoute.SourceEdit(
                        key = key,
                        title = sourceUi.sourceRows.firstOrNull { it.key == key }?.title ?: key,
                    )
                    sourceUi.sourceEditTick++
                }
            },
            onSourceLogin = if (loginWithCookies == null && loginWithPassword == null && loginWithWebview == null) {
                null
            } else {
                { key ->
                    sourceUi.loginNote = null
                    sourceUi.loginDraft = null
                    sourceUi.loginPasswordDraft = null
                    val row = sourceUi.sourceRows.firstOrNull { it.key == key }
                    route = DesktopRoute.SourceLogin(
                        key = key,
                        title = row?.title ?: key,
                        fields = row?.cookieFields.orEmpty(),
                        supportsPasswordLogin = row?.supportsPasswordLogin == true,
                        supportsWebLogin = row?.supportsWebLogin == true,
                    )
                }
            },
            onClearSourceOverride = clearSourceOverride?.let { clearFn ->
                { key ->
                    scope.launch {
                        runCatching { clearFn.invoke(key) }.fold(
                            onSuccess = {
                                sourceTick++
                                sourceUi.sourceEditTick++
                                sourceUi.sourceDetailTick++
                            },
                            onFailure = { t ->
                                println("D_源改写 撤销失败 $key ${t.javaClass.simpleName}: ${t.message?.take(160) ?: ""}")
                                DesktopErrorBus.report(
                                    category = "漫画",
                                    headline = "源改写撤销失败",
                                    humanReason = "这颗源的改写脚本没撤掉，覆盖层文件可能被占用；可重试或手动清理。",
                                    detail = "$key: ${t.javaClass.simpleName}: ${t.message.orEmpty()}",
                                    dedupKey = "comic:source_revert:$key",
                                )
                                sourceUi.sourceNote = desktopFailureNoteOf(t)
                            },
                        )
                    }
                }
            },
            account = sourceUi.sourceAccount,
            onSignOutSource = engine?.let { e ->
                {
                    val key = sourceUi.sourceSelectedKey
                    if (key != null) {
                        runCatching { e.host.logoutSource(key) }
                            .onFailure { t ->
                                println("D_源退出登录 写失败 $key ${t.javaClass.simpleName}: ${t.message?.take(160) ?: ""}")
                                DesktopErrorBus.report(
                                    category = "漫画",
                                    headline = "源退出登录失败",
                                    humanReason = "账号没退干净，凭据文件可能被占用；可重试，必要时手动删除该源凭据。",
                                    detail = "$key: ${t.javaClass.simpleName}: ${t.message.orEmpty()}",
                                    dedupKey = "comic:source_logout:$key",
                                )
                                sourceUi.sourceSettingsNote = desktopFailureNoteOf(t)
                            }
                        sourceUi.sourceDetailTick++
                    }
                }
            },
            settings = sourceUi.sourceSettings,
            onSettingChange = engine?.let {
                { settingKey, value ->
                    val key = sourceUi.sourceSelectedKey ?: return@let
                    val parsed = DesktopSourceSettings.parseValueForWrite(value)
                    val prevSettings = sourceUi.sourceSettings
                    if (prevSettings != null) {
                        sourceUi.sourceSettings = prevSettings.map { item ->
                            if (item.settingKey == settingKey) item.copy(currentValue = value) else item
                        }
                    }
                    scope.launch {
                        val got = desktopEngineCall(engine) {
                            engine.load(key, runInit = false)
                            engine.saveSettingValue(settingKey, parsed)
                            engine.applySettingLive(settingKey)
                        }
                        got.fold(
                            onSuccess = {
                                sourceUi.sourceSettingsNote = null
                                sourceUi.sourceDetailTick++
                            },
                            onFailure = { t ->
                                sourceUi.sourceSettings = prevSettings
                                val detail = "${t.javaClass.simpleName}: ${t.message.orEmpty()}"
                                println("D_站点设置 存失败 $key.$settingKey $detail")
                                sourceUi.sourceSettingsNote = desktopFailureNoteOf(t)
                                DesktopErrorBus.report(
                                    category = "站点设置",
                                    headline = "保存 $key.$settingKey 失败",
                                    humanReason = "这项设置没写进源配置，界面已回滚到原值；可再改一次。",
                                    detail = detail,
                                    dedupKey = "setting:save:$key:$settingKey",
                                )
                            },
                        )
                    }
                }
            },
            onSettingCallback = engine?.let {
                { settingKey ->
                    val key = sourceUi.sourceSelectedKey ?: return@let
                    scope.launch {
                        val got = desktopEngineCall(engine) {
                            engine.load(key)
                            engine.runSettingCallback(settingKey)
                        }
                        got.fold(
                            onSuccess = { note ->
                                sourceUi.sourceSettingsNote = note ?: "跑完了（源没给回话）"
                                sourceUi.sourceDetailTick++
                            },
                            onFailure = { t ->
                                val note = "没跑成：${t.javaClass.simpleName} " + (t.message?.take(160) ?: "")
                                sourceUi.sourceSettingsNote = note
                                DesktopErrorBus.report(
                                    category = "站点设置",
                                    headline = "执行 $key.$settingKey 失败",
                                    detail = note,
                                    dedupKey = "setting:cb:$key:$settingKey",
                                )
                            },
                        )
                    }
                }
            },
            settingsNote = sourceUi.sourceSettingsNote,
        )

        is DesktopRoute.SourceLogin -> DesktopSourceLoginScreen(
            mode = layoutMode,
            sourceName = r.title,
            fields = r.fields,
            supportsPasswordLogin = r.supportsPasswordLogin,
            supportsWebLogin = r.supportsWebLogin,
            note = sourceUi.loginNote,
            busy = sourceUi.loginBusy,
            onSubmitCookies = { values ->
                sourceUi.loginDraft = values
                sourceUi.loginPasswordDraft = null
                sourceUi.loginTick++
            },
            onSubmitPassword = { account, password ->
                sourceUi.loginDraft = null
                sourceUi.loginPasswordDraft = account to password
                sourceUi.loginTick++
            },
            onStartWebLogin = {
                sourceUi.loginDraft = null
                sourceUi.loginPasswordDraft = null
                sourceUi.loginWebviewTrigger = true
                sourceUi.loginTick++
            },
            onBack = { route = DesktopRoute.SourceManage },
        )

        else -> Unit
    }
}
