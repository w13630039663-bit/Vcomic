package com.venera.engineprobe

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

/**
 * 源更新与第三方源仓库订阅引擎。
 *
 * 兼容官方仓库（`venera-app/venera-configs`）与社区巡检仓库（`laincat/vensrc` 主订阅 / 扩展订阅），
 * 支持读取索引条目的独立 `url` 字段，并在未配置手工代理时为 `raw.githubusercontent.com` 自动提供 jsDelivr 回退。
 */
object DesktopSourceUpdate {

    /** 可选上游源仓库。 */
    enum class SourceRepo(
        val id: String,
        val displayName: String,
        val repoSlug: String,
        val badgeLabel: String,
        val indexUrl: String,
        val fallbackIndexUrls: List<String>,
        val rawBase: String?,
        val summaryHint: String,
    ) {
        OFFICIAL(
            id = "official",
            displayName = "官方源",
            repoSlug = "venera-app/venera-configs",
            badgeLabel = "官方源",
            indexUrl = "https://cdn.jsdelivr.net/gh/venera-app/venera-configs@main/index.json",
            fallbackIndexUrls = listOf(
                "https://fastly.jsdelivr.net/gh/venera-app/venera-configs@main/index.json",
                "https://raw.githubusercontent.com/venera-app/venera-configs/main/index.json",
            ),
            rawBase = "https://cdn.jsdelivr.net/gh/venera-app/venera-configs@main/",
            summaryHint = "软件内置的基础漫画源，装后可直接使用",
        ),
        VENSRC_MAIN(
            id = "vensrc_main",
            displayName = "社区稳定 · laincat/vensrc",
            repoSlug = "laincat/vensrc",
            badgeLabel = "laincat/vensrc",
            indexUrl = "https://cdn.jsdelivr.net/gh/laincat/vensrc@main/subscriptions/index.json",
            fallbackIndexUrls = listOf(
                "https://fastly.jsdelivr.net/gh/laincat/vensrc@main/subscriptions/index.json",
                "https://raw.githubusercontent.com/laincat/vensrc/main/subscriptions/index.json",
            ),
            rawBase = null,
            summaryHint = "社区每日测试全通过（推荐），装后可随时退回内置版",
        ),
        VENSRC_EXT(
            id = "vensrc_ext",
            displayName = "社区扩展 · laincat/vensrc",
            repoSlug = "laincat/vensrc",
            badgeLabel = "laincat/vensrc",
            indexUrl = "https://cdn.jsdelivr.net/gh/laincat/vensrc@main/subscriptions/extended.json",
            fallbackIndexUrls = listOf(
                "https://fastly.jsdelivr.net/gh/laincat/vensrc@main/subscriptions/extended.json",
                "https://raw.githubusercontent.com/laincat/vensrc/main/subscriptions/extended.json",
            ),
            rawBase = null,
            summaryHint = "社区测试核心看图通过，包含更多冷门站与修复版",
        ),
        ;

        val isCommunity: Boolean get() = this != OFFICIAL

        companion object {
            fun fromId(id: String?): SourceRepo =
                entries.firstOrNull { it.id == id } ?: OFFICIAL
        }
    }

    const val INDEX_URL = "https://cdn.jsdelivr.net/gh/venera-app/venera-configs@main/index.json"
    private const val RAW_BASE = "https://cdn.jsdelivr.net/gh/venera-app/venera-configs@main/"

    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/128.0.0.0 Safari/537.36 Venera/1.0.0"

    private val gson = Gson()
    private val listType = object : TypeToken<List<Map<String, Any?>>>() {}.type
    private val rawGhRegex = Regex("""^https://raw\.githubusercontent\.com/([^/]+)/([^/]+)/([^/]+)/(.+)$""")

    /** 上游清单里的一行。`version` 空串 = 上游没声明。 */
    data class Entry(
        val name: String,
        val fileName: String,
        val key: String,
        val version: String,
        val description: String? = null,
        val url: String? = null,
        val repoId: String = SourceRepo.OFFICIAL.id,
    ) {
        /** 兼容旧版五参数构造签名。 */
        constructor(
            name: String,
            fileName: String,
            key: String,
            version: String,
            description: String?,
        ) : this(name, fileName, key, version, description, null, SourceRepo.OFFICIAL.id)
    }

    /** 一颗可更新的源：`localVersion` 非空才有"从哪升到哪"那句话。 */
    data class Updatable(val entry: Entry, val localVersion: String)

    private fun client(proxy: String?): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
        when {
            proxy.equals("DIRECT", ignoreCase = true) || proxy.equals("NO_PROXY", ignoreCase = true) -> {
                builder.proxy(Proxy.NO_PROXY)
            }
            !proxy.isNullOrBlank() -> {
                val parts = proxy.split(":")
                if (parts.size == 2) {
                    val port = parts[1].trim().toIntOrNull()
                    if (port != null && port in 1..65535) {
                        builder.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(parts[0].trim(), port)))
                    }
                }
            }
        }
        return builder.build()
    }

    /**
     * 解析上游 `index.json` / `extended.json`。使用 Gson 解析 JSON 数组，避免正则被 `{}` 与转义字符截断。
     */
    fun parseIndex(text: String, repoId: String = SourceRepo.OFFICIAL.id): List<Entry> {
        val trimmed = text.trim()
        if (!trimmed.startsWith("[")) return emptyList()
        val rawList: List<Map<String, Any?>> = runCatching {
            gson.fromJson<List<Map<String, Any?>>>(trimmed, listType)
        }.getOrNull() ?: return emptyList()

        return rawList.mapNotNull { m ->
            val fileName = m["fileName"]?.toString()?.trim()?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val fallbackKey = fileName.substringAfterLast('/').removeSuffix(".js")
            Entry(
                name = m["name"]?.toString()?.trim()?.takeIf { it.isNotBlank() } ?: fallbackKey,
                fileName = fileName,
                key = m["key"]?.toString()?.trim()?.takeIf { it.isNotBlank() } ?: fallbackKey,
                version = m["version"]?.toString()?.trim().orEmpty(),
                description = m["description"]?.toString()?.trim()?.takeIf { it.isNotBlank() },
                url = m["url"]?.toString()?.trim()?.takeIf { it.isNotBlank() },
                repoId = repoId,
            )
        }
    }

    /** 语义版本比较：按 `.` 分段取数字比；相等/解析不出 = 不算新（不误更）。 */
    fun isNewer(remote: String, local: String): Boolean {
        if (remote.isBlank()) return false
        fun segments(v: String): List<Int> =
            v.split('.').map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
        val a = segments(remote)
        val b = segments(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** 拉上游清单（兼容旧签名，默认官方仓库，拉不到交回空表）。 */
    fun fetchIndex(proxy: String?): List<Entry> = fetchIndex(SourceRepo.OFFICIAL, proxy)

    /** 拉指定仓库清单（拉不到交回空表，不抛）。 */
    fun fetchIndex(repo: SourceRepo, proxy: String?): List<Entry> =
        fetchIndexResult(repo, proxy).getOrDefault(emptyList())

    /** 拉指定仓库清单并返回清晰异常结果（支持 CDN / 镜像自动回退）。 */
    fun fetchIndexResult(repo: SourceRepo, proxy: String?): Result<List<Entry>> {
        val http = client(proxy)
        val candidates = listOf(repo.indexUrl) + repo.fallbackIndexUrls
        var lastError: Throwable? = null
        for (candidateUrl in candidates) {
            val attempt = runCatching {
                http.newCall(Request.Builder().url(candidateUrl).header("User-Agent", UA).build())
                    .execute().use { resp ->
                        if (!resp.isSuccessful) error("HTTP ${resp.code}")
                        val body = resp.body?.string().orEmpty()
                        val list = parseIndex(body, repo.id)
                        if (list.isEmpty()) error("清单为空或格式不可解析")
                        list
                    }
            }
            if (attempt.isSuccess) return attempt
            lastError = attempt.exceptionOrNull()
        }
        return Result.failure(lastError ?: IllegalStateException("仓库清单拉取失败"))
    }

    /** 为 `raw.githubusercontent.com` 地址拼出对应的 jsDelivr CDN 地址，便于未挂代理直连加速。 */
    fun toJsdelivrMirror(rawUrl: String): String? {
        val m = rawGhRegex.matchEntire(rawUrl.trim()) ?: return null
        val (owner, repo, ref, path) = m.destructured
        return "https://cdn.jsdelivr.net/gh/$owner/$repo@$ref/$path"
    }

    /** 按条目信息构建候选下载链接列表（手工代理优先走原始直链，无手工代理优先 CDN 加速并保留直链回退）。 */
    fun downloadCandidates(entry: Entry, hasExplicitProxy: Boolean): List<String> {
        val repo = SourceRepo.fromId(entry.repoId)
        val direct = entry.url?.takeIf { it.isNotBlank() }
            ?: ((repo.rawBase ?: RAW_BASE) + entry.fileName.trimStart('/'))
        val mirror = toJsdelivrMirror(direct)
        val fastly = mirror?.replaceFirst("https://cdn.jsdelivr.net/", "https://fastly.jsdelivr.net/")
        return buildList {
            if (hasExplicitProxy) {
                add(direct)
                mirror?.let(::add)
                fastly?.let(::add)
            } else {
                mirror?.let(::add)
                fastly?.let(::add)
                add(direct)
            }
        }.distinct()
    }

    /** 下载单颗源原文（兼容旧签名：从官方仓库按文件名拼接下载）。 */
    fun download(fileName: String, proxy: String?): String? =
        download(
            Entry(
                name = fileName.removeSuffix(".js"),
                fileName = fileName,
                key = fileName.removeSuffix(".js"),
                version = "",
                url = null,
                repoId = SourceRepo.OFFICIAL.id,
            ),
            proxy,
        )

    /** 按条目真实 `url` 或所属仓库基址下载源脚本原文。 */
    fun download(entry: Entry, proxy: String?): String? =
        downloadResult(entry, proxy).getOrNull()

    /** 下载源脚本并返回清晰失败原因。 */
    fun downloadResult(entry: Entry, proxy: String?): Result<String> {
        val hasExplicitProxy = !proxy.isNullOrBlank() &&
            !proxy.equals("DIRECT", ignoreCase = true) &&
            !proxy.equals("NO_PROXY", ignoreCase = true)
        val http = client(proxy)
        var lastError: Throwable? = null
        for (targetUrl in downloadCandidates(entry, hasExplicitProxy)) {
            val attempt = runCatching {
                http.newCall(Request.Builder().url(targetUrl).header("User-Agent", UA).build())
                    .execute().use { resp ->
                        if (!resp.isSuccessful) error("HTTP ${resp.code}")
                        val body = resp.body?.string().orEmpty()
                        if (body.isBlank()) error("下载脚本为空")
                        body
                    }
            }
            if (attempt.isSuccess) return attempt
            lastError = attempt.exceptionOrNull()
        }
        return Result.failure(lastError ?: IllegalStateException("脚本下载失败"))
    }
}
