package com.venera.desktop.search

import com.venera.compose.data.platform.KeyValueStore
import com.venera.compose.gallery.data.GalleryPorts
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal const val DESKTOP_SEARCH_BOOKMARKS_PREFS: String = "venera_search_bookmarks"
internal const val DESKTOP_SEARCH_BOOKMARKS_KEY: String = "items_v1"
internal const val DESKTOP_SEARCH_BOOKMARKS_MAX: Int = 50

private val bookmarkJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * 全源通用的「快速搜索书签」条目。
 *
 * 记录一次完整的搜索条件：单源/聚合模式、站源键、关键词、筛选组选中项及置顶态。
 */
@Serializable
internal data class DesktopSearchBookmark(
    val id: String,
    val name: String,
    val sourceKey: String,
    val isAggregate: Boolean = false,
    val keyword: String = "",
    val selectedOptions: Map<String, List<String>> = emptyMap(),
    val pinned: Boolean = false,
    val createdAt: Long = 0L,
)

/** 打开搜索书签专用键值存储（走宿主 `ports.stores`）。 */
internal fun desktopSearchBookmarksStore(ports: GalleryPorts): KeyValueStore =
    ports.stores.open(DESKTOP_SEARCH_BOOKMARKS_PREFS)

/** 读取全部搜索书签，损坏数据兜底返回空表。 */
internal fun desktopSearchBookmarksRead(store: KeyValueStore): List<DesktopSearchBookmark> {
    val raw = store.getString(DESKTOP_SEARCH_BOOKMARKS_KEY, null)?.trim().orEmpty()
    if (raw.isEmpty()) return emptyList()
    return runCatching {
        bookmarkJson.decodeFromString<List<DesktopSearchBookmark>>(raw)
    }.getOrDefault(emptyList())
        .let(::desktopSearchBookmarksSort)
}

/** 将搜索书签表写回磁盘。 */
internal fun desktopSearchBookmarksWrite(
    store: KeyValueStore,
    entries: List<DesktopSearchBookmark>,
) {
    val sorted = desktopSearchBookmarksSort(entries).take(DESKTOP_SEARCH_BOOKMARKS_MAX)
    store.put(DESKTOP_SEARCH_BOOKMARKS_KEY, bookmarkJson.encodeToString(sorted))
}

/** 置顶优先，同组按创建时间倒序。 */
internal fun desktopSearchBookmarksSort(
    entries: List<DesktopSearchBookmark>,
): List<DesktopSearchBookmark> = entries.sortedWith(
    compareByDescending<DesktopSearchBookmark> { it.pinned }
        .thenByDescending { it.createdAt },
)

/** 生成默认书签名称（优先关键词，其次站源名）。 */
internal fun desktopSearchBookmarkDefaultName(
    keyword: String,
    sourceLabel: String,
    isAggregate: Boolean,
): String {
    val cleanKeyword = keyword.trim()
    val scopeLabel = if (isAggregate) "聚合" else sourceLabel.trim().ifEmpty { "全源" }
    return when {
        cleanKeyword.isNotEmpty() -> "$scopeLabel · $cleanKeyword"
        else -> "$scopeLabel · 筛选预设"
    }
}

/** 新增或覆盖同条件书签，返回排序后的新列表。 */
internal fun desktopSearchBookmarksAdd(
    current: List<DesktopSearchBookmark>,
    entry: DesktopSearchBookmark,
): List<DesktopSearchBookmark> {
    val cleanName = entry.name.trim()
    if (cleanName.isEmpty()) return current
    val normalized = entry.copy(
        name = cleanName,
        keyword = entry.keyword.trim(),
    )
    val deduped = current.filterNot {
        it.isAggregate == normalized.isAggregate &&
            it.sourceKey == normalized.sourceKey &&
            it.keyword == normalized.keyword &&
            it.selectedOptions == normalized.selectedOptions
    }
    return desktopSearchBookmarksSort(listOf(normalized) + deduped)
        .take(DESKTOP_SEARCH_BOOKMARKS_MAX)
}

/** 重命名指定书签。 */
internal fun desktopSearchBookmarksRename(
    current: List<DesktopSearchBookmark>,
    id: String,
    newName: String,
): List<DesktopSearchBookmark> {
    val trimmed = newName.trim()
    if (trimmed.isEmpty()) return current
    return current.map { if (it.id == id) it.copy(name = trimmed) else it }
}

/** 切换指定书签的置顶状态。 */
internal fun desktopSearchBookmarksTogglePin(
    current: List<DesktopSearchBookmark>,
    id: String,
): List<DesktopSearchBookmark> = desktopSearchBookmarksSort(
    current.map { if (it.id == id) it.copy(pinned = !it.pinned) else it },
)

/** 删除指定书签。 */
internal fun desktopSearchBookmarksDelete(
    current: List<DesktopSearchBookmark>,
    id: String,
): List<DesktopSearchBookmark> = current.filterNot { it.id == id }

/** 跨平台默认书签文件路径（兼容 Windows 与 Linux）。 */
internal fun desktopSearchBookmarksDefaultFile(): java.io.File {
    val home = System.getProperty("user.home").orEmpty().ifBlank { "." }
    val dir = java.io.File(home, ".venera")
    return java.io.File(dir, "search_bookmarks_v1.json")
}

/** 从本地文件读取全部搜索书签。 */
internal fun desktopSearchBookmarksReadFile(
    file: java.io.File = desktopSearchBookmarksDefaultFile(),
): List<DesktopSearchBookmark> {
    if (!file.isFile) return emptyList()
    return runCatching {
        val raw = file.readText(Charsets.UTF_8).trim()
        if (raw.isEmpty()) emptyList()
        else bookmarkJson.decodeFromString<List<DesktopSearchBookmark>>(raw)
    }.getOrDefault(emptyList()).let(::desktopSearchBookmarksSort)
}

/** 将搜索书签写入本地文件。 */
internal fun desktopSearchBookmarksWriteFile(
    entries: List<DesktopSearchBookmark>,
    file: java.io.File = desktopSearchBookmarksDefaultFile(),
) {
    runCatching {
        file.parentFile?.mkdirs()
        val sorted = desktopSearchBookmarksSort(entries).take(DESKTOP_SEARCH_BOOKMARKS_MAX)
        file.writeText(bookmarkJson.encodeToString(sorted), Charsets.UTF_8)
    }
}
