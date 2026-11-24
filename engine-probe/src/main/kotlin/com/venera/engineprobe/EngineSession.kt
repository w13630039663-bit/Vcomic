package com.venera.engineprobe

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.lang.reflect.Type

/** 供桌面壳层订阅源脚本初始化失败事件的轻量回调。 */
@Volatile
var onEngineSourceInitFailure: ((key: String, detail: String) -> Unit)? = null

/**
 * 桌面侧可复用的**一次引擎会话**：装载一个真源 + 出探索/搜索/详情/收藏数据 + 取图。
 *
 * 按领域扩展拆分至：
 * - [EngineSessionExploreAndSearch]
 * - [EngineSessionNetworkFavorites]
 * - [EngineSessionComicAndComments]
 * - [EngineSessionAuthAndSettings]
 */
class EngineSession(
    val host: DesktopJsHost,
    internal val assetDir: File,
    internal val overrideDir: File? = null,
    internal val onThumbnailHeaders: ((url: String, headers: Map<String, String>) -> Unit)? = null,
) : AutoCloseable {

    internal val gson = Gson()
    internal val mapType: Type = object : TypeToken<Map<String, Any?>>() {}.type
    internal val listType: Type = object : TypeToken<List<Map<String, Any?>>>() {}.type
    var key: String = ""
        private set
    var name: String = ""
        private set

    data class ComicCard(
        val title: String,
        val id: String,
        val cover: String,
        val author: String = "",
        val tags: List<String> = emptyList(),
    )

    data class Section(val title: String, val comics: List<ComicCard>)

    data class Category(
        val name: String,
        val index: Int,
        val types: List<String>,
    )

    data class SearchOption(
        val label: String,
        val type: String,
        val choices: List<Pair<String, String>> = emptyList(),
        val required: Boolean = false,
        val defaultValue: String? = null,
    )

    data class Page(
        val comics: List<ComicCard>,
        val sections: List<Section> = emptyList(),
        val maxPage: Int? = null,
        val error: String? = null,
        val next: String? = null,
        val thumbnailHeaders: Map<String, Map<String, String>> = emptyMap(),
    )

    data class CategoryChip(val label: String, val param: String?, val kind: String)
    data class CategoryPart(val name: String, val chips: List<CategoryChip>)
    data class CategoryBoard(val parts: List<CategoryPart>, val ranking: List<Pair<String, String>>)

    data class NetFavCaps(
        val multiFolder: Boolean,
        val hasLoadComics: Boolean,
        val hasLoadNext: Boolean,
    )

    data class FavMeta(
        val exists: Boolean,
        val multiFolder: Boolean,
        val singleFolderForSingleComic: Boolean,
        val isOldToNewSort: Boolean,
        val hasLoadComics: Boolean,
        val hasLoadNext: Boolean,
        val hasAddFolder: Boolean,
        val hasDeleteFolder: Boolean,
    )

    data class FavFoldersResult(
        val folders: Map<String, String>,
        val favorited: List<String>,
    )

    data class ThumbnailRewrite(val url: String, val headers: Map<String, String>)
    data class Episode(val name: String, val id: String, val placeholder: Boolean = false)
    data class ThumbPage(val thumbnails: List<String>, val next: String?)
    data class CookieLogin(val ok: Boolean, val note: String, val fields: List<String>)
    data class PasswordLogin(val ok: Boolean, val note: String)

    fun forkEphemeral(): EngineSession {
        val childHost = DesktopJsHost(
            assetDir = host.configuredAssetDir,
            dataDir = host.configuredDataDir,
            proxy = host.configuredProxy,
            seedCookies = emptyList(),
            sharedCookieJar = host.cookieJar,
            sharedClient = host.httpClient,
            sharedDataStore = host.sourceDataStore,
        )
        return EngineSession(
            host = childHost,
            assetDir = assetDir,
            overrideDir = overrideDir,
            onThumbnailHeaders = onThumbnailHeaders,
        )
    }

    fun load(sourceKey: String, runInit: Boolean = true): String {
        val (loadedKey, loadedName) = loadIntoSources(sourceKey, runInit)
        key = loadedKey
        name = loadedName
        return loadedName
    }

    internal fun ensureSourceLoaded(sourceKey: String): String {
        val requested = sourceKey.trim()
        if (requested.isEmpty()) error("未指定漫画源，无法执行源请求")
        val loaded = host.evaluate("Boolean(ComicSource.sources[${gson.toJson(requested)}])")
            ?.trim('"', '\'') == "true"
        if (loaded) return requested
        val (loadedKey, _) = loadIntoSources(requested, runInit = true)
        return loadedKey
    }

    private fun loadIntoSources(sourceKey: String, runInit: Boolean): Pair<String, String> {
        val js = loadText(sourceKey).replace("\r\n", "\n")
        val className = Regex("""class\s+(\w+)\s+extends\s+ComicSource""").find(js)?.groupValues?.get(1)
            ?: error("未找到 extends ComicSource 的类声明: sources/$sourceKey.js")
        host.evaluate("(function() {\n$js\nwindow['temp_source'] = new $className();\n})();")

        val loadedKey = host.evaluate("window['temp_source'] ? window['temp_source'].key : null")
            ?.trim('"', '\'')
        if (loadedKey.isNullOrBlank() || loadedKey == "null") error("装载后取不到 source key: $sourceKey")

        val defsJson = host.evaluate(
            """
            (function() {
                var s = window['temp_source'];
                var defs = {};
                if (s.settings) {
                    for (var k in s.settings) {
                        if (s.settings[k] && s.settings[k].default !== undefined) defs[k] = s.settings[k].default;
                    }
                }
                return JSON.stringify(defs);
            })()
            """.trimIndent(),
        )
        if (!defsJson.isNullOrBlank() && defsJson != "null") {
            runCatching { gson.fromJson<Map<String, Any?>>(defsJson, mapType) }
                .getOrNull()?.let { host.registerDefaultSettings(loadedKey, it) }
        }

        host.evaluate("ComicSource.sources['$loadedKey'] = window['temp_source']; delete window['temp_source'];")
        if (runInit) {
            val initRes = host.evaluateAsync(
                "var s = ComicSource.sources['$loadedKey']; if (s && s.init) { return s.init(); } return null;",
                timeoutMs = 15_000L,
            )
            if (initRes.contains("\"success\":false")) {
                println("D_源初始化 失败 key=$loadedKey detail=${initRes.take(200)}")
                onEngineSourceInitFailure?.invoke(loadedKey, initRes.take(200))
            }
        }
        val loadedName = host.evaluate("ComicSource.sources['$loadedKey'].name")?.trim('"', '\'') ?: loadedKey
        return loadedKey to loadedName
    }

    /** 取本次装载要用的脚本原文：**用户改写层优先**，其次包内，再次仓库。 */
    fun loadText(sourceKey: String): String {
        val relative = "sources/$sourceKey.js"
        overrideDir?.let { dir ->
            EngineAssets.overrideFile(dir, relative)?.let { text ->
                println(
                    "D_资源 来源=用户改写 ${File(dir, relative).absolutePath} " +
                        "字节=${text.toByteArray(Charsets.UTF_8).size}",
                )
                return text
            }
        }
        return EngineAssets.readText(assetDir, relative)
    }

    /** 只做「这颗源装不装得起来」的校验（先解析校验再落盘，不修改本会话已装载源表）。 */
    fun checkLoadable(sourceKey: String, js: String): String {
        val normalized = js.replace("\r\n", "\n")
        val className = Regex("""class\s+(\w+)\s+extends\s+ComicSource""").find(normalized)?.groupValues?.get(1)
            ?: error("未找到 extends ComicSource 的类声明: sources/$sourceKey.js")
        val outcome = host.evaluateResult(
            """
            (function() {
                try {
                    window['temp_source'] = undefined;
                    $normalized
                    window['temp_source'] = new $className();
                    return String(window['temp_source'].key || '');
                } finally {
                    delete window['temp_source'];
                }
            })()
            """.trimIndent(),
        )
        val t = outcome.exceptionOrNull()
        if (t != null) {
            throw IllegalStateException(
                "这篇脚本装不起来：${t.javaClass.simpleName} ${t.message?.take(400) ?: "（无消息）"}",
                t,
            )
        }
        val reported = outcome.getOrNull()?.trim('"', '\'')
        return reported?.takeIf { it.isNotBlank() && it != "null" && it != "undefined" }
            ?: error("装载后取不到 source key: $sourceKey（类声明找到了，但 new 出来之后 key 那一格是空的）")
    }

    internal fun call(script: String, timeoutMs: Long = 30_000L): Page {
        val raw = evalWithRetry(script, timeoutMs = timeoutMs)
        val envelope = gson.fromJson<Map<String, Any?>>(raw, mapType)
        val err = envelope["error"]?.toString() ?: envelope["__err"]?.toString()
        if (envelope["success"] == false) {
            println("[session] 信封失败 error=$err raw=${raw.take(240)}")
            return Page(emptyList(), error = err)
        }
        val data0 = envelope["data"]
        val data = if (data0 is Map<*, *> && data0["data"] != null && data0["comics"] == null) {
            data0["data"]
        } else {
            data0
        }
        val maxPage = (data as? Map<*, *>)?.get("maxPage")?.let { (it as? Number)?.toInt() }
        val next = (data as? Map<*, *>)?.get("next")?.toString()?.takeIf { it.isNotBlank() && it != "null" }
        val sections = when (data) {
            is List<*> -> {
                val rows = data.mapNotNull { it as? Map<*, *> }
                if (rows.any { it["comics"] != null }) {
                    rows.map { m -> Section(m["title"]?.toString() ?: "", cards(m["comics"])) }
                } else {
                    listOf(Section("", cards(rows)))
                }
            }
            is Map<*, *> -> {
                val flat = cards(data["comics"])
                if (data["comics"] != null || flat.isNotEmpty()) {
                    listOf(Section("", flat))
                } else {
                    data.entries.mapNotNull { (k, v) ->
                        val items = cards(v)
                        if (items.isEmpty()) null else Section(k?.toString() ?: "", items)
                    }
                }
            }
            else -> {
                println("[session] data 形状不认识=${data?.javaClass?.simpleName} raw=${raw.take(240)}")
                emptyList()
            }
        }
        val total = sections.sumOf { it.comics.size }
        if (total == 0) println("[session] 解析出 0 条 data形状=${data?.javaClass?.simpleName} raw=${raw.take(300)}")
        val rewritten = applyThumbnailHook(sections.flatMap { s -> s.comics.map { it.cover } })
        val publishedHeaders = LinkedHashMap<String, Map<String, String>>()
        val finalSections = if (rewritten.isEmpty()) sections else sections.map { section ->
            Section(
                section.title,
                section.comics.map { c ->
                    val rw = rewritten[c.cover]
                    if (rw == null) {
                        c
                    } else {
                        if (rw.headers.isNotEmpty()) {
                            publishedHeaders[rw.url] = rw.headers
                            onThumbnailHeaders?.invoke(rw.url, rw.headers)
                        }
                        if (rw.url != c.cover) c.copy(cover = rw.url) else c
                    }
                },
            )
        }
        return Page(finalSections.flatMap { it.comics }, finalSections, maxPage, err, next, publishedHeaders)
    }

    internal fun applyThumbnailHook(covers: List<String>): Map<String, ThumbnailRewrite> {
        val distinct = covers.filter { it.isNotBlank() }.distinct()
        if (distinct.isEmpty()) return emptyMap()
        val rewrites = thumbnailRewrites(distinct)
        if (rewrites.isEmpty()) return emptyMap()
        return rewrites.filter { (orig, rw) -> rw.url != orig || rw.headers.isNotEmpty() }
    }

    private fun cards(raw: Any?): List<ComicCard> = (raw as? List<*> ?: emptyList<Any?>()).mapNotNull { c ->
        (c as? Map<*, *>)?.let {
            ComicCard(
                title = it["title"]?.toString() ?: "",
                id = it["id"]?.toString() ?: "",
                cover = it["cover"]?.toString() ?: "",
                author = it["subTitle"]?.toString() ?: it["author"]?.toString() ?: "",
                tags = (it["tags"] as? List<*>)?.mapNotNull { t -> t?.toString() }
                    ?: (it["theme"] as? List<*>)?.mapNotNull { t -> (t as? Map<*, *>)?.get("name")?.toString() }
                    ?: emptyList(),
            )
        }
    }

    internal fun evalWithRetry(script: String, timeoutMs: Long = 30_000L): String {
        var raw = host.evaluateAsync(script, timeoutMs = timeoutMs)
        if (raw.contains("Login expired")) {
            println("[session] 捕获 Login expired，尝试自动 relogin key=$key")
            if (relogin()) {
                println("[session] relogin 成功，重新执行 script")
                raw = host.evaluateAsync(script, timeoutMs = timeoutMs)
            }
        }
        return raw
    }

    fun explore(page: Int = 1): Page = execExplore(page)
    fun explore(index: Int, page: Int = 1): Page = execExplore(index, page)
    fun exploreModes(): List<String> = execExploreModes()
    fun categoryBoard(): CategoryBoard = execCategoryBoard()
    fun loadCategory(category: String, param: String?, kind: String, page: Int = 1): Page =
        execLoadCategory(category, param, kind, page)
    fun loadRanking(option: String, page: Int = 1): Page = execLoadRanking(option, page)
    fun exploreCategory(index: Int, page: Int = 1): Page = execExploreCategory(index, page)
    fun categories(): List<Category> = execCategories()
    fun searchOptions(): List<SearchOption> = execSearchOptions()
    fun searchWith(keyword: String, options: List<String?>, page: Int = 1, next: String? = null): Page =
        execSearchWith(keyword, options, page, next)
    fun search(keyword: String, page: Int = 1, timeoutMs: Long = 30_000L): Page =
        execSearch(keyword, page, timeoutMs)

    fun hasNetworkFavorites(): Boolean = execHasNetworkFavorites()
    fun favoriteMeta(): FavMeta = execFavoriteMeta()
    fun networkFavoritesFoldersWithComic(comicId: String?): FavFoldersResult =
        execNetworkFavoritesFoldersWithComic(comicId)
    fun networkFavoritesAddOrDel(comicId: String, folderId: String, isAdding: Boolean, favoriteId: String?) =
        execNetworkFavoritesAddOrDel(comicId, folderId, isAdding, favoriteId)
    fun networkFavoritesAddFolder(name: String) = execNetworkFavoritesAddFolder(name)
    fun networkFavoritesDeleteFolder(folderId: String) = execNetworkFavoritesDeleteFolder(folderId)
    fun networkFavoritesCaps(): NetFavCaps = execNetworkFavoritesCaps()
    fun networkFavoritesFolders(): List<Pair<String, String>> = execNetworkFavoritesFolders()
    fun networkFavoritesLoadComics(page: Int, folderId: String?): Page =
        execNetworkFavoritesLoadComics(page, folderId)
    fun networkFavoritesLoadNext(next: String?, folderId: String?): Page =
        execNetworkFavoritesLoadNext(next, folderId)

    fun coverBytes(cover: String): ByteArray = execCoverBytes(cover)
    fun thumbnailRewrites(urls: List<String>): Map<String, ThumbnailRewrite> = execThumbnailRewrites(urls)
    fun details(comicId: String): ComicInfo = execDetails(comicId)
    fun thumbnails(comicId: String, next: String?): ThumbPage = execThumbnails(comicId, next)
    fun comments(comicId: String, subId: String?, page: Int, replyTo: String?): DesktopCommentPage =
        execComments(comicId, subId, page, replyTo)
    fun commentCapabilities(): DesktopCommentCapabilities = execCommentCapabilities()
    fun sendComment(comicId: String, subId: String?, content: String, replyTo: String?): Boolean =
        execSendComment(comicId, subId, content, replyTo)
    fun starRating(comicId: String, rating: Float): Boolean = execStarRating(comicId, rating)
    fun likeComic(comicId: String, isLike: Boolean): Boolean = execLikeComic(comicId, isLike)
    fun writeCapabilities(): DesktopWriteCapabilities = execWriteCapabilities()
    fun pages(comicId: String, episode: Episode, sourceKey: String = key): List<String> =
        execPages(comicId, episode, sourceKey)
    fun pageImage(imageKey: String, comicId: String, episodeId: String, sourceKey: String = key): ByteArray =
        execPageImage(imageKey, comicId, episodeId, sourceKey)

    fun loginWithCookies(values: List<String>): CookieLogin = execLoginWithCookies(values)
    fun webLoginUrl(): String? = execWebLoginUrl()
    fun checkWebLoginStatus(url: String, title: String): Boolean = execCheckWebLoginStatus(url, title)
    fun onWebLoginSuccess(): Boolean = execOnWebLoginSuccess()
    fun loginWithPassword(account: String, password: String): PasswordLogin =
        execLoginWithPassword(account, password)
    fun relogin(): Boolean = execRelogin()
    fun loadSettingsAll(): Map<String, Any?> = execLoadSettingsAll()
    fun saveSettingValue(settingKey: String, value: Any?) = execSaveSettingValue(settingKey, value)
    fun applySettingLive(settingKey: String) = execApplySettingLive(settingKey)
    fun runSettingCallback(settingKey: String): String? = execRunSettingCallback(settingKey)

    override fun close() = host.close()
}
