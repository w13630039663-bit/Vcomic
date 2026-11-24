package com.venera.engineprobe

import com.venera.engineprobe.EngineSession.FavFoldersResult
import com.venera.engineprobe.EngineSession.FavMeta
import com.venera.engineprobe.EngineSession.NetFavCaps
import com.venera.engineprobe.EngineSession.Page

/** 探测当前源是否声明网络收藏对象。 */
internal fun EngineSession.execHasNetworkFavorites(): Boolean = runCatching {
    host.evaluate(
        "JSON.stringify(!!(ComicSource.sources['$key'] && ComicSource.sources['$key'].favorites))",
    ) == "true"
}.getOrDefault(false)

/** 读取网络收藏全部结构元信息（多夹/排序/加载与增删夹能力）。 */
internal fun EngineSession.execFavoriteMeta(): FavMeta = runCatching {
    val raw = host.evaluate(
        """
        JSON.stringify((function() {
            var s = ComicSource.sources['$key'];
            if (!s || !s.favorites) return { exists: false };
            var f = s.favorites;
            return {
                exists: true,
                multiFolder: f.multiFolder === true,
                singleFolderForSingleComic: f.singleFolderForSingleComic === true,
                isOldToNewSort: f.isOldToNewSort === true,
                hasLoadComics: typeof f.loadComics === 'function',
                hasLoadNext: typeof f.loadNext === 'function',
                hasAddFolder: typeof f.addFolder === 'function',
                hasDeleteFolder: typeof f.deleteFolder === 'function'
            };
        })())
        """.trimIndent(),
    )
    val m = gson.fromJson<Map<String, Any?>>(raw, mapType)
    FavMeta(
        exists = m["exists"] == true,
        multiFolder = m["multiFolder"] == true,
        singleFolderForSingleComic = m["singleFolderForSingleComic"] == true,
        isOldToNewSort = m["isOldToNewSort"] == true,
        hasLoadComics = m["hasLoadComics"] == true,
        hasLoadNext = m["hasLoadNext"] == true,
        hasAddFolder = m["hasAddFolder"] == true,
        hasDeleteFolder = m["hasDeleteFolder"] == true,
    )
}.getOrDefault(FavMeta(false, false, false, false, false, false, false, false))

/** 读取网络收藏夹列表并查询指定漫画是否已纳入各夹。 */
internal fun EngineSession.execNetworkFavoritesFoldersWithComic(comicId: String?): FavFoldersResult = runCatching {
    val script = """
        return (async function() {
            var f = ComicSource.sources['$key']?.favorites;
            if (!f || !f.loadFolders) return {};
            var r = await f.loadFolders(${gson.toJson(comicId)});
            return (r && r.folders) ? r : (r || {});
        })()
    """.trimIndent()
    val raw = evalWithRetry(script)
    val env = gson.fromJson<Map<String, Any?>>(raw, mapType)
    val data = (env["data"] as? Map<*, *>) ?: (env as? Map<*, *>) ?: emptyMap<Any?, Any?>()
    val rawFolders = (data["folders"] as? Map<*, *>)?.mapNotNull { (k, v) ->
        (k?.toString() ?: return@mapNotNull null) to (v?.toString() ?: "")
    }?.toMap() ?: emptyMap()
    val allFolderId = "-1"
    val folders = LinkedHashMap<String, String>(rawFolders.size).apply {
        rawFolders[allFolderId]?.let { put(allFolderId, it) }
        rawFolders.forEach { (k, v) -> if (k != allFolderId) put(k, v) }
    }
    val favorited = (data["favorited"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
    FavFoldersResult(folders, favorited)
}.getOrDefault(FavFoldersResult(emptyMap(), emptyList()))

/** 添加或移出网络收藏。 */
internal fun EngineSession.execNetworkFavoritesAddOrDel(comicId: String, folderId: String, isAdding: Boolean, favoriteId: String?) {
    val script = """
        return (async function() {
            var s = ComicSource.sources['$key'];
            var f = s && s.favorites;
            if (!f || !f.addOrDelFavorite) return { __err: "该源没有 favorites.addOrDelFavorite" };
            var r = await f.addOrDelFavorite(
                ${gson.toJson(comicId)},
                ${gson.toJson(folderId)},
                ${gson.toJson(isAdding)},
                ${gson.toJson(favoriteId)}
            );
            return r || {};
        })()
    """.trimIndent()
    val raw = evalWithRetry(script)
    val env = runCatching { gson.fromJson<Map<String, Any?>>(raw, mapType) }.getOrNull()
    if (env != null && env["success"] == false) {
        error(env["error"]?.toString() ?: "收藏操作失败")
    }
}

/** 新建网络收藏夹。 */
internal fun EngineSession.execNetworkFavoritesAddFolder(name: String) {
    val script = """
        return (async function() {
            var s = ComicSource.sources['$key'];
            var f = s && s.favorites;
            if (!f || !f.addFolder) return { __err: "该源没有 favorites.addFolder" };
            var r = await f.addFolder(${gson.toJson(name)});
            return r || {};
        })()
    """.trimIndent()
    val raw = evalWithRetry(script)
    val env = runCatching { gson.fromJson<Map<String, Any?>>(raw, mapType) }.getOrNull()
    if (env != null && env["success"] == false) {
        error(env["error"]?.toString() ?: "新建文件夹失败")
    }
}

/** 删除网络收藏夹。 */
internal fun EngineSession.execNetworkFavoritesDeleteFolder(folderId: String) {
    val script = """
        return (async function() {
            var s = ComicSource.sources['$key'];
            var f = s && s.favorites;
            if (!f || !f.deleteFolder) return { __err: "该源没有 favorites.deleteFolder" };
            var r = await f.deleteFolder(${gson.toJson(folderId)});
            return r || {};
        })()
    """.trimIndent()
    val raw = evalWithRetry(script)
    val env = runCatching { gson.fromJson<Map<String, Any?>>(raw, mapType) }.getOrNull()
    if (env != null && env["success"] == false) {
        error(env["error"]?.toString() ?: "删除文件夹失败")
    }
}

/** 探测网络收藏分页方式与多夹能力。 */
internal fun EngineSession.execNetworkFavoritesCaps(): NetFavCaps = runCatching {
    val raw = host.evaluate(
        """
        JSON.stringify({
            mf: ComicSource.sources['$key']?.favorites?.multiFolder === true,
            lc: typeof ComicSource.sources['$key']?.favorites?.loadComics === 'function',
            ln: typeof ComicSource.sources['$key']?.favorites?.loadNext === 'function'
        })
        """,
    )
    val m = gson.fromJson<Map<String, Any?>>(raw, mapType)
    NetFavCaps(
        multiFolder = m["mf"] == true,
        hasLoadComics = m["lc"] == true,
        hasLoadNext = m["ln"] == true,
    )
}.getOrDefault(NetFavCaps(multiFolder = false, hasLoadComics = false, hasLoadNext = false))

/** 读取全部网络收藏夹 `id -> name` 映射，并将 `"-1"`（全部）置顶。 */
internal fun EngineSession.execNetworkFavoritesFolders(): List<Pair<String, String>> = runCatching {
    val raw = evalWithRetry(
        """
        return (async function() {
            var f = ComicSource.sources['$key']?.favorites;
            if (!f || !f.loadFolders) return {};
            var r = await f.loadFolders(null);
            return (r && r.folders) ? r.folders : (r || {});
        })()
        """,
    )
    val env = gson.fromJson<Map<String, Any?>>(raw, mapType)
    val rows: List<Pair<String, String>> = when (val data = env["data"]) {
        is Map<*, *> -> data.mapNotNull { (k, v) ->
            val id = k?.toString() ?: return@mapNotNull null
            id to (v?.toString() ?: id)
        }
        is List<*> -> data.mapNotNull { row ->
            val m = (row as? Map<*, *>) ?: return@mapNotNull null
            val id = (m["id"] ?: m["name"] ?: m["title"])?.toString() ?: return@mapNotNull null
            id to ((m["name"] ?: m["title"] ?: m["id"])?.toString() ?: id)
        }
        else -> emptyList()
    }
    val all = rows.filter { it.first == "-1" }
    all + rows.filter { it.first != "-1" }
}.getOrDefault(emptyList())

/** 页码型网络收藏分页加载。 */
internal fun EngineSession.execNetworkFavoritesLoadComics(page: Int, folderId: String?): Page = call(
    """
    return (async function() {
        var s = ComicSource.sources['$key'];
        var f = s && s.favorites;
        if (!f || !f.loadComics) return { comics: [], __err: "该源没有 favorites.loadComics" };
        var r = await f.loadComics(${gson.toJson(page)}, ${gson.toJson(folderId)});
        return r || { comics: [] };
    })()
    """,
)

/** 游标型网络收藏分页加载。 */
internal fun EngineSession.execNetworkFavoritesLoadNext(next: String?, folderId: String?): Page = call(
    """
    return (async function() {
        var s = ComicSource.sources['$key'];
        var f = s && s.favorites;
        if (!f || !f.loadNext) return { comics: [], __err: "该源没有 favorites.loadNext" };
        var r = await f.loadNext(${gson.toJson(next)}, ${gson.toJson(folderId)});
        return r || { comics: [] };
    })()
    """,
)
