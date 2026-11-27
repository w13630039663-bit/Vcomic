package com.venera.desktop.gallery.data

import com.venera.compose.data.platform.JsonKeyValueStore
import com.venera.compose.data.platform.PathProvider
import com.venera.compose.data.platform.PreferenceKeys
import com.venera.engineprobe.DesktopSourceUpdate.SourceRepo

/**
 * 源管理选择的订阅仓库持久化（官方源 / laincat/vensrc 主订阅 / 扩展订阅）
 * 与设置页代理参数解析（供在线源库与检查同步共用）。
 */
internal object DesktopSourceRepoPreferences {

    private const val KEY_SOURCE_REPO_ID = "pref_source_repo_id"

    /** 读取当前选中的上游源仓库；未设置时默认回落官方仓库。 */
    fun readSelectedRepo(paths: PathProvider?): SourceRepo {
        if (paths == null) return SourceRepo.OFFICIAL
        val id = runCatching {
            JsonKeyValueStore(DesktopSourceState.PREFS_NAME, paths).getString(KEY_SOURCE_REPO_ID, null)
        }.getOrNull()
        return SourceRepo.fromId(id)
    }

    /** 保存用户切换的源仓库。 */
    fun saveSelectedRepo(paths: PathProvider?, repo: SourceRepo) {
        if (paths == null) return
        runCatching {
            JsonKeyValueStore(DesktopSourceState.PREFS_NAME, paths).put(KEY_SOURCE_REPO_ID, repo.id)
        }
    }

    /**
     * 解析源仓库拉取与更新应使用的代理字符串：
     * 1. 启动参数 `-Dvenera.proxy=...` 优先；
     * 2. 否则读取设置页「网络与代理」（手动 `host:port` / 直连 `"DIRECT"` / 系统 `null`）。
     */
    fun resolveProxy(paths: PathProvider?): String? {
        val sysProxy = System.getProperty("venera.proxy")?.takeIf { it.isNotBlank() }
        if (sysProxy != null) return sysProxy
        if (paths == null) return null
        return runCatching {
            val store = JsonKeyValueStore(PreferenceKeys.PREFS_NAME, paths)
            when (store.getString("pref_proxy_type", "系统") ?: "系统") {
                "直连" -> "DIRECT"
                "手动" -> {
                    val host = store.getString("pref_proxy_host", "").orEmpty().trim()
                    val port = store.getInt("pref_proxy_port", 0)
                    if (host.isNotBlank() && port in 1..65535) "$host:$port" else null
                }
                else -> null
            }
        }.getOrNull()
    }
}
