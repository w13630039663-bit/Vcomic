package com.venera.desktop.platform

import com.venera.compose.data.platform.JsonKeyValueStore
import com.venera.compose.data.platform.PathProvider
import com.venera.compose.data.platform.PreferenceKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 桌面 WebDAV 同步（档二 5b-6）。
 *
 * ## 为什么是桌面自持一份，而不是复用 `sync/WebDavClient.kt`
 *
 * 对岸那颗客户端 import `android.util.Log` 且用 `org.xmlpull` 解 PROPFIND —— 两样桌面都没有，
 * srcDir 挂进来编不过也不跑得动（`sync/` 其余各颗还绑 `Context`）。所以这里自持一份**最小客户端**：
 * 标准 HTTP Basic Auth + PROPFIND（Depth 1）+ MKCOL + PUT/GET，XML 用 JDK 自带 DOM 解；
 * 配置读写走对岸**同名文件同名键**（`venera_webdav_prefs`：server_url / username / password /
 * remote_path），盘上互认。上传吃桌面既有备份导出链（[com.venera.desktop.data.desktopBackupExportTo]），
 * 下载落到中转盘（cacheRoot/webdav_staging），还原仍走备份页那条已验的路。
 *
 * ⚠️ 密码与对岸同形：盘上明文。公共机慎用（行上如实写着）。
 */
object DesktopWebDavSync {

    /** 一次同步的读数（行上念）。 */
    sealed interface State {
        data object Idle : State
        data object Running : State
        data class Done(val message: String) : State
        data class Failed(val message: String) : State
    }

    /** 盘上那份配置（键与对岸 `WebDavConfig` 的存储逐字同名）。 */
    data class Config(
        val serverUrl: String,
        val username: String,
        val password: String,
        val remotePath: String,
    )

    /** 最近一次成功上传 / 下载的归档（屏上「上次同步：时间 · 文件名」吃它）。 */
    data class LastSync(val fileName: String, val timestamp: Long)

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _lastSync = MutableStateFlow<LastSync?>(null)
    val lastSync: StateFlow<LastSync?> = _lastSync.asStateFlow()

    private const val KEY_LAST_SYNC_FILE = "last_sync_file"
    private const val KEY_LAST_SYNC_TIME = "last_sync_time"

    private val _lastDownloadedFile = MutableStateFlow<File?>(null)
    val lastDownloadedFile: StateFlow<File?> = _lastDownloadedFile.asStateFlow()

    /** 行上那一念（Idle 不念假话）。 */
    fun stateLine(state: State): String = when (state) {
        State.Idle -> "还没同步过"
        State.Running -> "传输中…"
        is State.Done -> state.message
        is State.Failed -> state.message
    }

    private fun davStore(paths: PathProvider): JsonKeyValueStore =
        JsonKeyValueStore(PreferenceKeys.PREFS_WEBDAV, paths)

    fun readConfig(paths: PathProvider): Config {
        val dav = davStore(paths)
        return Config(
            serverUrl = dav.getString("server_url", "") ?: "",
            username = dav.getString("username", "") ?: "",
            password = dav.getString("password", "") ?: "",
            remotePath = dav.getString("remote_path", "/Venera/") ?: "/Venera/",
        )
    }

    /** 保存 WebDAV 配置到与设置中心一致的偏好文件。 */
    fun saveConfig(paths: PathProvider, config: Config) {
        val dav = davStore(paths)
        dav.put("server_url", config.serverUrl.trim())
        dav.put("username", config.username.trim())
        dav.put("password", config.password)
        dav.put("remote_path", config.remotePath.trim().ifBlank { "/Venera/" })
    }

    /** 从同一份偏好读回「上次成功同步」；顺带刷新 [lastSync]，供屏首帧直接消费。 */
    fun loadLastSync(paths: PathProvider): LastSync? {
        val dav = davStore(paths)
        val fileName = dav.getString(KEY_LAST_SYNC_FILE, null)?.takeIf { it.isNotBlank() }
        if (fileName == null) {
            _lastSync.value = null
            return null
        }
        val info = LastSync(fileName, dav.getLong(KEY_LAST_SYNC_TIME, 0L))
        _lastSync.value = info
        return info
    }

    /** 上传 / 下载成功后落盘，连同时间一起写；键与配置文件同层，不新增文件。 */
    private fun recordLastSync(paths: PathProvider, fileName: String) {
        val info = LastSync(fileName, System.currentTimeMillis())
        val dav = davStore(paths)
        dav.put(KEY_LAST_SYNC_FILE, info.fileName)
        dav.put(KEY_LAST_SYNC_TIME, info.timestamp)
        _lastSync.value = info
    }

    /** 归档中转落点（cacheRoot 子目录；清缓存批那三堆之外，这一格随它自己的读写存在）。 */
    fun stagingDir(paths: PathProvider): File =
        File(paths.cacheRoot, "webdav_staging").apply { mkdirs() }

    /** 查找中转目录最近一次下载的归档文件。 */
    fun latestStagingFile(paths: PathProvider): File? =
        stagingDir(paths).listFiles()?.filter { it.isFile && (it.name.endsWith(".venera") || it.name.endsWith(".json")) }
            ?.maxByOrNull { it.lastModified() }

    /** 探测远端目录连接状态。 */
    suspend fun testConnection(config: Config): State {
        if (config.serverUrl.isBlank()) return fail("先配置 WebDAV 服务器地址")
        _state.value = State.Running
        return withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(dirUrl(config))
                    .header("Authorization", basic(config))
                    .method("PROPFIND", "".toRequestBody("application/xml".toMediaType()))
                    .header("Depth", "0")
                    .build()
                CLIENT.newCall(request).execute().use { response ->
                    check(response.isSuccessful || response.code == 207) { "HTTP ${response.code}" }
                    done("连接正常（HTTP ${response.code}）")
                }
            }.getOrElse { fail("连接失败（${it.message?.take(60) ?: it.javaClass.simpleName}）") }
        }
    }

    /** 上传：本地导出通用 `.venera` 全量归档 → MKCOL 补目录 → PUT。 */
    suspend fun upload(
        paths: PathProvider,
        assetDir: File? = null,
        overrideDir: File? = null,
        includeSourceAccounts: Boolean = false,
    ): State {
        val config = readConfig(paths)
        if (config.serverUrl.isBlank()) return fail("先配置 WebDAV 服务器地址")
        _state.value = State.Running
        val local = File(stagingDir(paths), "venera-desktop-${System.currentTimeMillis()}.venera")
        val export = com.venera.desktop.data.desktopVeneraExportTo(
            path = local.absolutePath,
            paths = paths,
            assetDir = assetDir,
            overrideDir = overrideDir,
            options = com.venera.desktop.data.DesktopVeneraExportOptions(includeSourceAccounts),
        )
        if (export.isFailure) {
            return fail("导出没成（${export.exceptionOrNull()?.javaClass?.simpleName ?: "未知"}）")
        }
        return withContext(Dispatchers.IO) {
            runCatching {
                ensureRemoteDir(config)
                val request = Request.Builder()
                    .url(dirUrl(config) + local.name)
                    .header("Authorization", basic(config))
                    .put(local.asRequestBody("application/octet-stream".toMediaType()))
                    .build()
                CLIENT.newCall(request).execute().use { response ->
                    check(response.isSuccessful) { "PUT 回了 HTTP ${response.code}" }
                }
                val uploaded = local.name
                local.delete()
                // 上次同步信息只是页面读数，写不进去不该把已经成功的上传判失败。
                runCatching { recordLastSync(paths, uploaded) }
                done("已上传 $uploaded")
            }.getOrElse { fail("上传失败（${it.javaClass.simpleName}）") }
        }
    }

    /** 列云端（PROPFIND Depth 1），取最新一份 GET 到中转盘并记录路径。 */
    suspend fun downloadLatest(paths: PathProvider): State {
        val config = readConfig(paths)
        if (config.serverUrl.isBlank()) return fail("先配置 WebDAV 服务器地址")
        _state.value = State.Running
        return withContext(Dispatchers.IO) {
            runCatching {
                val files = listRemote(config)
                val latest = files.filterNot { it.isDirectory }
                    .sortedByDescending { it.lastModified }
                    .firstOrNull()
                    ?: error("云端没有归档")
                val request = Request.Builder()
                    .url(dirUrl(config) + latest.name)
                    .header("Authorization", basic(config))
                    .get()
                    .build()
                val dest = File(stagingDir(paths), latest.name)
                CLIENT.newCall(request).execute().use { response ->
                    check(response.isSuccessful) { "GET 回了 HTTP ${response.code}" }
                    val body = response.body ?: error("响应没有正文")
                    dest.writeBytes(body.bytes())
                }
                _lastDownloadedFile.value = dest
                // 上传/下载本身已成功，读数落盘失败只影响「上次同步」行，不回滚传输结果。
                runCatching { recordLastSync(paths, latest.name) }
                done("已下载 ${latest.name}")
            }.getOrElse { fail("取回失败（${it.javaClass.simpleName}）") }
        }
    }

    // ── 最小客户端面（与对岸那四步同语义：探测/补目录/列取/传收）──

    private val CLIENT by lazy {
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    private class RemoteEntry(val name: String, val size: Long, val lastModified: Long, val isDirectory: Boolean)

    private fun baseUrl(config: Config): String {
        var base = config.serverUrl.trim()
        if (!base.startsWith("http://") && !base.startsWith("https://")) base = "https://$base"
        return base.removeSuffix("/")
    }

    private fun dirUrl(config: Config): String {
        val path = config.remotePath.trim().let { if (it.startsWith("/")) it else "/$it" }
            .let { if (it.endsWith("/")) it else "$it/" }
        return baseUrl(config) + path
    }

    private fun basic(config: Config): String = Credentials.basic(config.username, config.password)

    private suspend fun ensureRemoteDir(config: Config) {
        val check = Request.Builder().url(dirUrl(config))
            .header("Authorization", basic(config))
            .method("PROPFIND", "".toRequestBody("application/xml".toMediaType()))
            .header("Depth", "0").build()
        val exists = CLIENT.newCall(check).execute().use { it.isSuccessful || it.code == 207 || it.code == 405 }
        if (exists) return
        val mkcol = Request.Builder().url(dirUrl(config))
            .header("Authorization", basic(config))
            .method("MKCOL", null).build()
        CLIENT.newCall(mkcol).execute().use { response ->
            check(response.isSuccessful || response.code == 405) { "MKCOL 回了 HTTP ${response.code}" }
        }
    }

    private suspend fun listRemote(config: Config): List<RemoteEntry> {
        val request = Request.Builder().url(dirUrl(config))
            .header("Authorization", basic(config))
            .method("PROPFIND", """<?xml version="1.0"?><propfind xmlns="DAV:"><prop><displayname/><getcontentlength/><getlastmodified/><resourcetype/></prop></propfind>"""
                .toRequestBody("application/xml".toMediaType()))
            .header("Depth", "1").build()
        val xml = CLIENT.newCall(request).execute().use { response ->
            check(response.isSuccessful || response.code == 207) { "PROPFIND 回了 HTTP ${response.code}" }
            response.body?.string().orEmpty()
        }
        return parsePropfind(xml)
    }

    /** JDK DOM 解 PROPFIND（对岸用 xmlpull，那颗库桌面没有——这里不引第二套依赖）。 */
    private fun parsePropfind(xml: String): List<RemoteEntry> {
        if (xml.isBlank()) return emptyList()
        val doc = runCatching {
            val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            factory.newDocumentBuilder().parse(xml.byteInputStream())
        }.getOrNull() ?: return emptyList()
        val responses = doc.getElementsByTagName("response")
        return buildList {
            for (i in 0 until responses.length) {
                val node = responses.item(i) ?: continue
                val href = childText(node, "href") ?: continue
                val name = href.trimEnd('/').substringAfterLast('/')
                if (name.isBlank()) continue
                val isDir = node.collectAllDescendants().any { it.localName == "collection" }
                val size = childText(node, "getcontentlength")?.toLongOrNull() ?: 0L
                val modified = childText(node, "getlastmodified")?.let { text ->
                    runCatching {
                        java.time.ZonedDateTime
                            .parse(text.trim(), java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
                            .toInstant().toEpochMilli()
                    }.getOrDefault(0L)
                } ?: 0L
                add(RemoteEntry(name, size, modified, isDir))
            }
        }
    }

    private fun org.w3c.dom.Node.collectAllDescendants(): List<org.w3c.dom.Node> {
        val out = ArrayList<org.w3c.dom.Node>()
        val kids = childNodes
        for (i in 0 until kids.length) {
            val kid = kids.item(i) ?: continue
            out += kid
            out += kid.collectAllDescendants()
        }
        return out
    }

    private fun childText(node: org.w3c.dom.Node, tag: String): String? {
        val children = node.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.localName == tag || child.nodeName.endsWith(":$tag") || child.nodeName == tag) {
                return child.textContent?.takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    private fun done(message: String): State {
        val s = State.Done(message)
        _state.value = s
        return s
    }

    private fun fail(message: String): State {
        val s = State.Failed(message)
        _state.value = s
        return s
    }
}
