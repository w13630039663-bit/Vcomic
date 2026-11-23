package com.venera.desktop.platform

import com.venera.compose.data.platform.PreferenceKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * 桌面更新检查（档二 5b-2）。
 *
 * 安卓那颗 `AppUpdateChecker` 整颗文件绑 `Context`/`VeneraNetworkClient`（排除清单点名那颗），
 * 其对内 `ProjectChannel` 也拿不到 ⇒ 通道串这里**逐字复刻**（改仓库要对齐两处，指路注释写在串旁）：
 *  - 同一发布通道（对岸 `ProjectChannel.LATEST_RELEASE_API` 同串，SLUG=w13630039663-bit/venera-miuix，
 *    不是上游 venera-app/venera——那才是"装不上也不是这个界面"的坑，对岸头注钉过）；
 *  - 同一解包器（`ReleaseFeed.kt` 那颗纯件，已在桌面 srcDir 编译面内，无 android import）；
 *  - 同一比较判据（照抄对岸 `isNewer` 的段比口径）；
 *  - 本包版本走装配点递入的 [localVersion]（打包版 packageVersion=2.1.1 同源）。
 *
 * 桌面产物是 Exe/Msi 不是 APK：Available 只念「去发布页下载」，不摆应用内自动更新
 * （对岸那套 APK 下载链 `UpdateDownloadQueue` 不搬）。检查成功/失败都进 [state] 热流，
 * 设置区「检查更新」行即点即报，启动检查吃偏好档（24h 节流同对岸）。
 */
object DesktopUpdateCheck {

    /** 桌面端 VComic 发布通道。 */
    private const val LATEST_RELEASE_API = "https://api.github.com/repos/w13630039663-bit/Vcomic/releases/latest"

    /** 发布页（Available 时念"去发布页下载"）。 */
    private const val RELEASES_URL = "https://github.com/w13630039663-bit/Vcomic/releases"

    /** 一次检查的读数（给设置行与启动播报共用）。 */
    sealed interface Result {
        data object Idle : Result
        data object Checking : Result
        data object UpToDate : Result
        data class Available(val remote: String, val releaseUrl: String) : Result
        data object Failed : Result
    }

    private val _state = MutableStateFlow<Result>(Result.Idle)
    val state: StateFlow<Result> = _state.asStateFlow()

    /**
     * 手动/启动共用的检查口。[onAvailable] 只在发现新版时回调（给启动 toast/弹窗用），
     * 设置行自己读 [state] 即可。
     */
    suspend fun check(
        client: okhttp3.OkHttpClient,
        localVersion: String,
        onAvailable: ((remote: String, releaseUrl: String) -> Unit)? = null,
    ): Result {
        _state.value = Result.Checking
        val outcome = withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(LATEST_RELEASE_API)
                    .header("Accept", "application/vnd.github+json")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    com.venera.compose.data.update.parseLatestRelease(response.body?.string().orEmpty())
                }
            }.getOrNull()
        }
        val release = outcome
        if (release == null) {
            _state.value = Result.Failed
            return Result.Failed
        }
        return if (isNewer(release.version, localVersion)) {
            _state.value = Result.Available(release.version, RELEASES_URL)
            onAvailable?.invoke(release.version, RELEASES_URL)
            _state.value
        } else {
            _state.value = Result.UpToDate
            _state.value
        }
    }

    /** 比较判据逐字照抄对岸 `AppUpdateChecker.isNewer`（同口径不另起账）。 */
    fun isNewer(remote: String, local: String): Boolean {
        val a = segments(remote)
        val b = segments(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun segments(version: String): List<Int> = version
        .removePrefix("v").removePrefix("V")
        .substringBefore('-').substringBefore('+')
        .split('.')
        .map { s -> s.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }

    /** 启动检查的 24h 节流账本（盘上键，与对岸 last_update_check_at 同形）。 */
    private const val KEY_LAST_CHECK_AT = "desktop_last_update_check_at"
    const val STARTUP_CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L

    fun shouldCheckOnStartup(store: com.venera.compose.data.platform.JsonKeyValueStore): Boolean =
        System.currentTimeMillis() - store.getLong(KEY_LAST_CHECK_AT, 0L) >= STARTUP_CHECK_INTERVAL_MS

    fun markChecked(store: com.venera.compose.data.platform.JsonKeyValueStore) {
        store.put(KEY_LAST_CHECK_AT, System.currentTimeMillis())
    }

    /** 检查后把结果念成一行字（设置行用）。 */
    fun readoutOf(result: Result, localVersion: String): String = when (result) {
        Result.Idle -> "尚未检查"
        Result.Checking -> "检查中…"
        Result.UpToDate -> "已是最新版 $localVersion"
        is Result.Available -> "发现新版 ${result.remote}"
        Result.Failed -> "检查失败，稍后再试"
    }
}
