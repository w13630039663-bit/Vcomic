package com.venera.desktop.platform

import com.venera.compose.data.platform.JsonKeyValueStore
import com.venera.compose.data.update.ReleaseAsset
import com.venera.compose.data.update.UpdateDownload
import com.venera.compose.data.update.displayVersionOf
import com.venera.compose.data.update.sha256Of
import java.awt.Desktop
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 桌面更新检查、发布说明解析与一键下载安装入口。
 *
 * 从 GitHub Releases 最新版接口拉取版本号、Markdown 更新说明（`body`）与桌面端安装包
 * （优先 `.exe` / `.msi`，其次 `.zip`），支持自动检查偏好开关、24 小时启动节流、
 * 流式下载进度、SHA-256 校验与一键拉起安装程序。
 */
object DesktopUpdateCheck {

    /** 桌面端 VComic 发布通道。 */
    private const val LATEST_RELEASE_API = "https://api.github.com/repos/w13630039663-bit/Vcomic/releases/latest"

    /** 发布页（兜底链接）。 */
    const val RELEASES_URL = "https://github.com/w13630039663-bit/Vcomic/releases"

    /** 项目主页。 */
    const val HOMEPAGE_URL = "https://github.com/w13630039663-bit/Vcomic"

    /** 一次检查的读数（给设置页与启动播报共用）。 */
    sealed interface Result {
        data object Idle : Result
        data object Checking : Result
        data object UpToDate : Result
        data class Available(
            val remote: String,
            val releaseUrl: String,
            val changelog: String = "",
            val installer: ReleaseAsset? = null,
        ) : Result
        data object Failed : Result
    }

    /** 从 GitHub Release JSON 解析出的桌面版发布信息。 */
    data class DesktopRelease(
        val tagName: String,
        val version: String,
        val changelog: String,
        val htmlUrl: String,
        val installer: ReleaseAsset?,
    )

    private val _state = MutableStateFlow<Result>(Result.Idle)
    val state: StateFlow<Result> = _state.asStateFlow()

    private val _downloadState = MutableStateFlow<UpdateDownload>(UpdateDownload.Idle)
    val downloadState: StateFlow<UpdateDownload> = _downloadState.asStateFlow()

    /**
     * 手动/启动共用的检查口。[onAvailable] 只在发现新版时回调（给启动 toast/弹窗用），
     * 设置行自己读 [state] 即可。
     */
    suspend fun check(
        client: OkHttpClient,
        localVersion: String,
        onAvailable: ((remote: String, releaseUrl: String) -> Unit)? = null,
    ): Result {
        _state.value = Result.Checking
        val release = withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(LATEST_RELEASE_API)
                    .header("Accept", "application/vnd.github+json")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    parseDesktopRelease(response.body?.string().orEmpty())
                }
            }.getOrNull()
        }
        if (release == null) {
            _state.value = Result.Failed
            return Result.Failed
        }
        return if (isNewer(release.version, localVersion)) {
            val available = Result.Available(
                remote = release.version,
                releaseUrl = release.htmlUrl,
                changelog = release.changelog,
                installer = release.installer,
            )
            _state.value = available
            onAvailable?.invoke(release.version, release.htmlUrl)
            available
        } else {
            _state.value = Result.UpToDate
            Result.UpToDate
        }
    }

    /**
     * 启动时自动检查更新：仅当用户开启自动检查且距离上次检查超过 24 小时才执行。
     */
    suspend fun checkOnStartupIfDue(
        store: JsonKeyValueStore,
        client: OkHttpClient,
        localVersion: String,
        onAvailable: ((remote: String, releaseUrl: String) -> Unit)? = null,
    ): Result? {
        if (!isAutoCheckEnabled(store) || !shouldCheckOnStartup(store)) return null
        markChecked(store)
        return check(client = client, localVersion = localVersion, onAvailable = onAvailable)
    }

    /**
     * 解析 GitHub `releases/latest` 响应，提取版本号、更新说明与桌面安装包（`.exe`/`.msi`/`.zip`）。
     */
    internal fun parseDesktopRelease(body: String): DesktopRelease? {
        val root = runCatching { Json.parseToJsonElement(body) as? JsonObject }.getOrNull()
            ?: return null
        val tag = root["tag_name"]?.jsonPrimitive?.contentOrNull
        if (tag.isNullOrBlank()) return null

        val htmlUrl = root["html_url"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: RELEASES_URL
        val changelog = root["body"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val assets = runCatching { root["assets"]?.jsonArray }.getOrNull()
            ?.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val name = obj["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val url = obj["browser_download_url"]?.jsonPrimitive?.contentOrNull.orEmpty()
                if (name.isBlank() || url.isBlank()) return@mapNotNull null
                ReleaseAsset(
                    name = name,
                    size = obj["size"]?.jsonPrimitive?.longOrNull ?: 0L,
                    sha256 = sha256Of(obj["digest"]?.jsonPrimitive?.contentOrNull),
                    url = url,
                )
            }
            .orEmpty()

        return DesktopRelease(
            tagName = tag,
            version = displayVersionOf(tag),
            changelog = changelog,
            htmlUrl = htmlUrl,
            installer = pickDesktopInstaller(assets),
        )
    }

    /** 按桌面端安装体验优先挑选 `.exe`，其次 `.msi`，再次 `.zip`。 */
    internal fun pickDesktopInstaller(assets: List<ReleaseAsset>): ReleaseAsset? {
        val lower = assets.map { it to it.name.lowercase() }
        return lower.firstOrNull { (_, n) -> n.endsWith(".exe") }?.first
            ?: lower.firstOrNull { (_, n) -> n.endsWith(".msi") }?.first
            ?: lower.firstOrNull { (_, n) -> n.endsWith(".zip") && !n.contains("source") }?.first
    }

    /**
     * 一键下载桌面端安装包到 [targetDir]，实时更新 [downloadState]。
     * 若资产带 `sha256`，下载完成后自动校验散列。
     */
    suspend fun downloadInstaller(
        client: OkHttpClient,
        asset: ReleaseAsset,
        targetDir: File,
    ): UpdateDownload {
        _downloadState.value = UpdateDownload.Running(
            downloadedBytes = 0L,
            totalBytes = asset.size,
            speedBytesPerSec = 0L,
            viaMirror = false,
        )
        return withContext(Dispatchers.IO) {
            runCatching {
                targetDir.mkdirs()
                val safeName = asset.name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val dest = File(targetDir, safeName)
                val part = File(targetDir, "$safeName.part")
                if (part.exists()) part.delete()

                val req = Request.Builder().url(asset.url).build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        val failed = UpdateDownload.Failed("HTTP ${resp.code}：无法下载 ${asset.name}")
                        _downloadState.value = failed
                        return@withContext failed
                    }
                    val respBody = resp.body ?: run {
                        val failed = UpdateDownload.Failed("响应体为空")
                        _downloadState.value = failed
                        return@withContext failed
                    }
                    val total = respBody.contentLength().takeIf { it > 0L } ?: asset.size
                    var downloaded = 0L
                    var lastTickAt = System.currentTimeMillis()
                    var lastTickBytes = 0L
                    var speed = 0L
                    val buf = ByteArray(16 * 1024)

                    respBody.byteStream().use { input ->
                        FileOutputStream(part).use { out ->
                            while (true) {
                                val read = input.read(buf)
                                if (read <= 0) break
                                out.write(buf, 0, read)
                                downloaded += read
                                val now = System.currentTimeMillis()
                                val elapsed = now - lastTickAt
                                if (elapsed >= 150L) {
                                    speed = ((downloaded - lastTickBytes) * 1000L / elapsed).coerceAtLeast(0L)
                                    lastTickAt = now
                                    lastTickBytes = downloaded
                                    _downloadState.value = UpdateDownload.Running(
                                        downloadedBytes = downloaded,
                                        totalBytes = total,
                                        speedBytesPerSec = speed,
                                        viaMirror = false,
                                    )
                                }
                            }
                            out.flush()
                        }
                    }
                }

                val expectedSha = asset.sha256
                if (expectedSha != null) {
                    _downloadState.value = UpdateDownload.Verifying
                    val actual = computeSha256(part)
                    if (!actual.equals(expectedSha, ignoreCase = true)) {
                        part.delete()
                        val failed = UpdateDownload.Failed(
                            "校验失败：期望 ${expectedSha.take(8)}，实得 ${actual.take(8)}",
                        )
                        _downloadState.value = failed
                        return@withContext failed
                    }
                }

                if (dest.exists()) dest.delete()
                if (!part.renameTo(dest)) {
                    part.copyTo(dest, overwrite = true)
                    part.delete()
                }
                val ready = UpdateDownload.Ready(
                    file = dest,
                    verified = asset.sha256 != null,
                    size = dest.length(),
                )
                _downloadState.value = ready
                ready
            }.getOrElse { err ->
                val failed = UpdateDownload.Failed(
                    "${err.javaClass.simpleName}: ${err.message?.take(120) ?: "下载中断"}",
                )
                _downloadState.value = failed
                failed
            }
        }
    }

    /** 重置下载状态（方便出错后重新尝试）。 */
    fun resetDownload() {
        _downloadState.value = UpdateDownload.Idle
    }

    /**
     * 拉起已下载的安装程序：`.msi` 走 `msiexec /i`，`.exe` 直接执行，
     * 其余压缩包打开所在目录由用户解压。
     */
    fun launchInstaller(file: File): kotlin.Result<Unit> = runCatching {
        require(file.exists()) { "安装文件不存在：${file.name}" }
        val lower = file.name.lowercase()
        when {
            lower.endsWith(".msi") -> {
                ProcessBuilder("msiexec", "/i", file.absolutePath).start()
            }
            lower.endsWith(".exe") -> {
                ProcessBuilder(file.absolutePath).start()
            }
            else -> {
                val target = file.parentFile ?: file
                check(Desktop.isDesktopSupported()) { "当前桌面环境不支持打开目录" }
                Desktop.getDesktop().open(target)
            }
        }
        Unit
    }

    private fun computeSha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(16 * 1024)
        file.inputStream().use { input ->
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
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

    /** 自动检查更新开关与 24h 节流账本。 */
    private const val KEY_AUTO_CHECK_UPDATE = "desktop_auto_check_update"
    private const val KEY_LAST_CHECK_AT = "desktop_last_update_check_at"
    const val STARTUP_CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L

    fun isAutoCheckEnabled(store: JsonKeyValueStore): Boolean =
        store.getBoolean(KEY_AUTO_CHECK_UPDATE, true)

    fun setAutoCheckEnabled(store: JsonKeyValueStore, enabled: Boolean) {
        store.put(KEY_AUTO_CHECK_UPDATE, enabled)
    }

    fun shouldCheckOnStartup(store: JsonKeyValueStore): Boolean =
        System.currentTimeMillis() - store.getLong(KEY_LAST_CHECK_AT, 0L) >= STARTUP_CHECK_INTERVAL_MS

    fun markChecked(store: JsonKeyValueStore) {
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
