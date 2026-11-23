package com.venera.desktop

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.venera.compose.data.db.CoreDbSchema
import com.venera.compose.data.db.CoreTableBackup
import com.venera.compose.data.db.DatabasePorts
import com.venera.compose.data.db.FavoriteImageBackupFields
import com.venera.compose.data.db.FavoriteImagesStore
import com.venera.compose.data.db.FavoriteItem
import com.venera.compose.data.db.LocalFavoritesManager
import com.venera.compose.data.db.parseJsonObject
import com.venera.compose.data.platform.PathProvider
import com.venera.compose.gallery.data.GalleryArtistFollowsStore
import com.venera.compose.gallery.data.GalleryFavorite
import com.venera.compose.gallery.data.GalleryFavoritesStore
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryArtistFollow
import com.venera.compose.security.guard.GuardRulePattern
import com.venera.desktop.gallery.data.DesktopSourceNames
import com.venera.engineprobe.EngineAssets
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 本机各数据模块实时条数，用于备份工作台顶部概览。 */
data class DesktopLocalAssetCounts(
    val history: Int = 0,
    val favorites: Int = 0,
    val favoriteFolders: Int = 0,
    val imageFavorites: Int = 0,
    val galleryFavorites: Int = 0,
    val galleryFollows: Int = 0,
    val stats: Int = 0,
    val guardRules: Int = 0,
)

/** 归档内可独立勾选导入的数据模块。 */
enum class DesktopBackupModule(val label: String, val note: String) {
    HISTORY("阅读历史", "含章节与页码进度"),
    FAVORITES("漫画收藏与收藏夹", "含排序与夹子结构"),
    IMAGE_FAVORITES("插图收藏", "元信息与远程图链"),
    GALLERY("画廊收藏与关注画师", "离线展示与关注名单"),
    STATS("阅读统计", "每日页数与阅读时长"),
    GUARD_RULES("屏蔽规则", "自动跳过无效正则"),
    SOURCE_ACCOUNTS("漫画源账号与设置", "未安装的源将自动跳过"),
}

/** 导入冲突处理策略。 */
enum class DesktopImportStrategy {
    MERGE,
    OVERWRITE,
}

/** 导出配置。 */
data class DesktopVeneraExportOptions(
    val includeSourceAccounts: Boolean = false,
)

/** 归档内单条源账号设置快照。 */
data class DesktopSourceAccountRow(
    val fileName: String,
    val key: String,
    val name: String,
    val version: String,
    val enabled: Boolean,
    val pinned: Boolean,
    val data: Map<String, Any?>,
)

/** 解析进内存的待导入归档负载（动库前已完成全部格式校验）。 */
data class DesktopParsedArchivePayload(
    val history: JsonArray,
    val stats: JsonArray,
    val guardRules: JsonArray,
    val favoriteFolders: List<ParsedFavoriteFolder>,
    val favoriteItems: List<ParsedFavoriteEntry>,
    val imageFavorites: List<FavoriteImageBackupFields>,
    val galleryFavorites: List<GalleryFavorite>,
    val galleryFollows: List<GalleryArtistFollow>,
    val sourceAccounts: List<DesktopSourceAccountRow>,
    /** v4 追更账本整表；老归档没有这两个 entry ⇒ 空数组，恢复时不动本机追更。 */
    val follows: JsonArray = JsonArray(),
    val followSyncs: JsonArray = JsonArray(),
)

data class ParsedFavoriteFolder(
    val folder: String,
    val sourceKey: String,
    val sourceFolder: String,
)

data class ParsedFavoriteEntry(
    val folder: String,
    val item: FavoriteItem,
    val updateTime: String?,
    val hasNewUpdate: Boolean,
    val lastCheckTime: Long?,
)

/** 导入前的归档预览模型，供 UI 展示包信息与分项勾选。 */
data class DesktopArchivePreview(
    val path: String,
    val fileName: String,
    val isVeneraZip: Boolean,
    val formatLabel: String,
    val sourceApp: String,
    val version: Int,
    val timestamp: Long,
    val historyCount: Int,
    val favoriteCount: Int,
    val favoriteFolderCount: Int,
    val imageFavoriteCount: Int,
    val galleryFavoriteCount: Int,
    val galleryFollowCount: Int,
    val statsCount: Int,
    val guardRuleCount: Int,
    val hasSourceAccounts: Boolean,
    val sourceAccountCount: Int,
    internal val payload: DesktopParsedArchivePayload,
) {
    /** 判断某模块在当前归档中是否可用（旧版桌面 JSON 仅包含历史/统计/屏蔽）。 */
    fun isModuleAvailable(module: DesktopBackupModule): Boolean = when (module) {
        DesktopBackupModule.HISTORY -> true
        DesktopBackupModule.STATS -> true
        DesktopBackupModule.GUARD_RULES -> true
        DesktopBackupModule.FAVORITES -> isVeneraZip
        DesktopBackupModule.IMAGE_FAVORITES -> isVeneraZip
        DesktopBackupModule.GALLERY -> isVeneraZip
        DesktopBackupModule.SOURCE_ACCOUNTS -> isVeneraZip && hasSourceAccounts
    }

    /** 某模块在归档中的条数展示文本。 */
    fun countText(module: DesktopBackupModule): String = when (module) {
        DesktopBackupModule.HISTORY -> "$historyCount 条"
        DesktopBackupModule.FAVORITES -> "$favoriteCount 本 / $favoriteFolderCount 夹"
        DesktopBackupModule.IMAGE_FAVORITES -> "$imageFavoriteCount 张"
        DesktopBackupModule.GALLERY -> "${galleryFavoriteCount + galleryFollowCount} 项"
        DesktopBackupModule.STATS -> "$statsCount 条"
        DesktopBackupModule.GUARD_RULES -> "$guardRuleCount 条"
        DesktopBackupModule.SOURCE_ACCOUNTS -> if (hasSourceAccounts) "$sourceAccountCount 个源" else "未包含"
    }
}

/** 全量导入完成报告。 */
data class DesktopVeneraRestoreOutcome(
    val fileName: String,
    val strategy: DesktopImportStrategy,
    val history: Int = 0,
    val favorites: Int = 0,
    val folders: Int = 0,
    val imageFavorites: Int = 0,
    val galleryFavorites: Int = 0,
    val galleryFollows: Int = 0,
    val stats: Int = 0,
    val guardImported: Int = 0,
    val guardSkipped: Int = 0,
    /** 追更账本恢复条数（含订阅绑定由 [followSyncs] 另记）。 */
    val follows: Int = 0,
    val followSyncs: Int = 0,
    val sourceAccountsImported: Int = 0,
    val sourceAccountsSkipped: Int = 0,
)

private const val VENERA_ARCHIVE_VERSION = 5

/**
 * 读取本机全部可备份模块的实时条数。
 *
 * @param paths 桌面端路径提供器，用于读取画廊本地 JSON 存储。
 */
internal suspend fun desktopReadLocalAssetCounts(paths: PathProvider?): DesktopLocalAssetCounts =
    withContext(Dispatchers.IO) {
        val core = runCatching { DatabasePorts.of(null).core }.getOrNull()
        val historyCount = core?.let {
            runCatching {
                CoreDbSchema.ensureOn(it.reader())
                it.reader().query("SELECT count(*) AS c FROM comic_history").firstOrNull()?.long("c")?.toInt()
            }.getOrNull()
        } ?: 0
        val statsCount = core?.let {
            runCatching {
                it.reader().query("SELECT count(*) AS c FROM reading_stats").firstOrNull()?.long("c")?.toInt()
            }.getOrNull()
        } ?: 0
        val guardCount = core?.let {
            runCatching {
                it.reader().query("SELECT count(*) AS c FROM content_guard_rules").firstOrNull()?.long("c")?.toInt()
            }.getOrNull()
        } ?: 0
        val imageFavCount = core?.let {
            runCatching {
                it.reader().query("SELECT count(*) AS c FROM favorite_images").firstOrNull()?.long("c")?.toInt()
            }.getOrNull()
        } ?: 0

        val favManager = runCatching { LocalFavoritesManager.getInstance(Unit) }.getOrNull()
        val folders = favManager?.let { runCatching { it.currentFolders() }.getOrNull() }.orEmpty()
        var favItems = 0
        if (favManager != null) {
            for (folder in folders) {
                favItems += runCatching { favManager.folderComics(folder) }.getOrDefault(0)
            }
        }

        val galleryFavs = paths?.let { runCatching { GalleryFavoritesStore.getInstance(it).favorites.value.size }.getOrNull() } ?: 0
        val galleryFollows = paths?.let { runCatching { GalleryArtistFollowsStore.getInstance(it).follows.value.size }.getOrNull() } ?: 0

        DesktopLocalAssetCounts(
            history = historyCount,
            favorites = favItems,
            favoriteFolders = folders.size,
            imageFavorites = imageFavCount,
            galleryFavorites = galleryFavs,
            galleryFollows = galleryFollows,
            stats = statsCount,
            guardRules = guardCount,
        )
    }

/** 返回通用 `.venera` 归档默认文件名。 */
internal fun desktopVeneraDefaultFileName(timestamp: Long): String = "venera_backup_$timestamp.venera"

/**
 * 探测并解析本机备份文件（自动识别安卓/桌面通用 `.venera` ZIP 归档与旧版桌面 JSON）。
 *
 * @param path 待解析的本地文件绝对路径。
 */
internal suspend fun desktopVeneraInspectFile(path: String): Result<DesktopArchivePreview> =
    withContext(Dispatchers.IO) {
        runCatching {
            val file = File(path)
            if (!file.isFile) throw IllegalStateException("备份文件不存在：$path")
            val header = ByteArray(2)
            file.inputStream().use { it.read(header) }
            val isZip = header[0] == 'P'.code.toByte() && header[1] == 'K'.code.toByte()
            if (isZip) inspectZipArchive(file) else inspectLegacyDesktopJson(file)
        }
    }

private fun inspectLegacyDesktopJson(file: File): DesktopArchivePreview {
    val raw = file.readText()
    val snapshot = desktopBackupDecodeJson(raw)
    val meta = parseJsonObject(raw).getAsJsonObject("meta")
    val ts = meta?.get("timestamp")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asLong ?: 0L
    val app = meta?.get("app")?.takeIf { it.isJsonPrimitive }?.asString ?: "Venera Desktop"
    return DesktopArchivePreview(
        path = file.absolutePath,
        fileName = file.name,
        isVeneraZip = false,
        formatLabel = "旧版桌面 JSON",
        sourceApp = app,
        version = snapshot.version,
        timestamp = ts,
        historyCount = snapshot.counts.history,
        favoriteCount = 0,
        favoriteFolderCount = 0,
        imageFavoriteCount = 0,
        galleryFavoriteCount = 0,
        galleryFollowCount = 0,
        statsCount = snapshot.counts.stats,
        guardRuleCount = snapshot.counts.guard,
        hasSourceAccounts = false,
        sourceAccountCount = 0,
        payload = DesktopParsedArchivePayload(
            history = snapshot.history,
            stats = snapshot.stats,
            guardRules = snapshot.guard,
            favoriteFolders = emptyList(),
            favoriteItems = emptyList(),
            imageFavorites = emptyList(),
            galleryFavorites = emptyList(),
            galleryFollows = emptyList(),
            sourceAccounts = emptyList(),
        ),
    )
}

private fun inspectZipArchive(file: File): DesktopArchivePreview {
    ZipFile(file).use { zip ->
        val metaText = readZipText(zip, "meta.json")
        val metaObj = metaText?.let { runCatching { parseJsonObject(it) }.getOrNull() }
        val version = metaObj?.get("version")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt ?: 1
        val ts = metaObj?.get("timestamp")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asLong ?: 0L
        val app = metaObj?.get("app")?.takeIf { it.isJsonPrimitive }?.asString ?: "Venera Compose"

        val history = readZipArray(zip, "history.json")
        val stats = readZipArray(zip, "stats.json")
        val guardRules = readZipArray(zip, "guard_rules.json")
        val foldersArr = readZipArray(zip, "favorite_folders.json")
        val favArr = readZipArray(zip, "favorites.json").let {
            if (it.size() > 0) it else readZipArray(zip, "favorite.json")
        }
        val imageFavArr = readZipArray(zip, "image_favorites.json")
        val galleryFollowsText = readZipText(zip, "gallery_follows.json")
        val galleryFavsText = readZipText(zip, "gallery_favorites.json")
        val sourceAccountsText = readZipText(zip, "source_accounts.json")
        val followsArr = readZipArray(zip, "comic_follow.json")
        val followSyncsArr = readZipArray(zip, "comic_follow_sync_source.json")

        val folders = parseFavoriteFolders(foldersArr)
        val favItems = parseFavoriteEntries(favArr)
        val imageFavs = parseImageFavorites(imageFavArr)
        val galleryFollows = parseGalleryFollows(galleryFollowsText)
        val galleryFavs = parseGalleryFavorites(galleryFavsText)
        val sourceAccounts = parseSourceAccounts(sourceAccountsText)

        return DesktopArchivePreview(
            path = file.absolutePath,
            fileName = file.name,
            isVeneraZip = true,
            formatLabel = "通用 .venera 归档",
            sourceApp = app,
            version = version,
            timestamp = ts,
            historyCount = history.size(),
            favoriteCount = favItems.size,
            favoriteFolderCount = folders.size,
            imageFavoriteCount = imageFavs.size,
            galleryFavoriteCount = galleryFavs.size,
            galleryFollowCount = galleryFollows.size,
            statsCount = stats.size(),
            guardRuleCount = guardRules.size(),
            hasSourceAccounts = sourceAccountsText != null,
            sourceAccountCount = sourceAccounts.size,
            payload = DesktopParsedArchivePayload(
                history = history,
                stats = stats,
                guardRules = guardRules,
                favoriteFolders = folders,
                favoriteItems = favItems,
                imageFavorites = imageFavs,
                galleryFavorites = galleryFavs,
                galleryFollows = galleryFollows,
                sourceAccounts = sourceAccounts,
                follows = followsArr,
                followSyncs = followSyncsArr,
            ),
        )
    }
}

/**
 * 导出全量通用 `.venera` ZIP 归档。
 *
 * @param path 目标文件路径。
 * @param paths 桌面端路径提供器（用于读取画廊存储与源数据目录）。
 * @param assetDir 随包资源目录（用于扫描已安装漫画源）。
 * @param overrideDir 源脚本覆盖层目录。
 * @param options 导出选项（是否包含漫画源账号）。
 */
internal suspend fun desktopVeneraExportTo(
    path: String,
    paths: PathProvider?,
    assetDir: File?,
    overrideDir: File?,
    options: DesktopVeneraExportOptions = DesktopVeneraExportOptions(),
): Result<DesktopLocalAssetCounts> = withContext(Dispatchers.IO) {
    runCatching {
        val core = DatabasePorts.of(null).core
        val db = core.reader()
        CoreDbSchema.ensureOn(db)
        val ts = System.currentTimeMillis()

        val historyJson = CoreTableBackup.exportTable(db, "comic_history")
        val statsJson = CoreTableBackup.exportTable(db, "reading_stats")
        val guardJson = CoreTableBackup.exportTable(db, "content_guard_rules")
        // 追更两表随「收藏」域一起走整包：账本与订阅绑定都不能在备份里掉队。
        val followsJson = CoreTableBackup.exportTable(db, "comic_follow")
        val followSyncsJson = CoreTableBackup.exportTable(db, "comic_follow_sync_source")
        val imageFavJson = encodeImageFavorites(FavoriteImagesStore(core).backupFields())

        val favManager = LocalFavoritesManager.getInstance(Unit)
        val folderNames = favManager.currentFolders()
        val syncByFolder = favManager.getFolderSync().associateBy { it.folder }
        val foldersJson = JsonArray().apply {
            for (folder in folderNames) {
                val sync = syncByFolder[folder]
                add(JsonObject().apply {
                    addProperty("folder", folder)
                    addProperty("sourceKey", sync?.sourceKey.orEmpty())
                    addProperty("sourceFolder", sync?.sourceFolder.orEmpty())
                })
            }
        }
        val favoritesJson = JsonArray().apply {
            for (folder in folderNames) {
                for (item in favManager.getFolderComics(folder)) {
                    add(encodeFavoriteItem(item, folder))
                }
            }
        }

        val galleryFollowsSnapshot = paths?.let { GalleryArtistFollowsStore.getInstance(it).follows.value }.orEmpty()
        val galleryFavsSnapshot = paths?.let { GalleryFavoritesStore.getInstance(it).favorites.value }.orEmpty()
        val sourceAccountsJson = if (options.includeSourceAccounts && paths != null && assetDir != null) {
            encodeSourceAccounts(paths, assetDir, overrideDir)
        } else {
            null
        }

        val metaJson = JsonObject().apply {
            addProperty("version", VENERA_ARCHIVE_VERSION)
            addProperty("timestamp", ts)
            addProperty("app", "Venera Desktop")
            addProperty("historyCount", historyJson.size())
            addProperty("favoriteCount", favoritesJson.size())
            addProperty("favoriteFolderCount", foldersJson.size())
            addProperty("statsCount", statsJson.size())
            addProperty("guardCount", guardJson.size())
            addProperty("imageFavoriteCount", imageFavJson.size())
            addProperty("galleryFollowCount", galleryFollowsSnapshot.size)
            addProperty("galleryFavoriteCount", galleryFavsSnapshot.size)
            addProperty("sourceAccountCount", sourceAccountsJson?.size() ?: 0)
            addProperty("followCount", followsJson.size())
            addProperty("followSyncCount", followSyncsJson.size())
        }

        val file = File(path)
        file.parentFile?.mkdirs()
        if (file.exists() && !file.delete()) {
            throw IllegalStateException("无法覆盖旧备份文件：$path")
        }

        ZipOutputStream(BufferedOutputStream(FileOutputStream(file))).use { zos ->
            writeZipEntry(zos, "meta.json", metaJson.toString())
            writeZipEntry(zos, "history.json", historyJson.toString())
            writeZipEntry(zos, "favorites.json", favoritesJson.toString())
            writeZipEntry(zos, "favorite_folders.json", foldersJson.toString())
            writeZipEntry(zos, "stats.json", statsJson.toString())
            writeZipEntry(zos, "guard_rules.json", guardJson.toString())
            writeZipEntry(zos, "image_favorites.json", imageFavJson.toString())
            writeZipEntry(zos, "gallery_follows.json", encodeGalleryFollows(galleryFollowsSnapshot))
            writeZipEntry(zos, "gallery_favorites.json", encodeGalleryFavorites(galleryFavsSnapshot))
            writeZipEntry(zos, "comic_follow.json", followsJson.toString())
            writeZipEntry(zos, "comic_follow_sync_source.json", followSyncsJson.toString())
            if (sourceAccountsJson != null) {
                writeZipEntry(zos, "source_accounts.json", sourceAccountsJson.toString())
            }
        }

        DesktopLocalAssetCounts(
            history = historyJson.size(),
            favorites = favoritesJson.size(),
            favoriteFolders = foldersJson.size(),
            imageFavorites = imageFavJson.size(),
            galleryFavorites = galleryFavsSnapshot.size,
            galleryFollows = galleryFollowsSnapshot.size,
            stats = statsJson.size(),
            guardRules = guardJson.size(),
        )
    }
}

/**
 * 将已解析的归档预览按所选模块与冲突策略写入本机。
 *
 * @param preview 待导入归档的内存预览对象。
 * @param selected 用户勾选要导入的模块集合。
 * @param strategy 冲突策略（智能合并或清空覆盖）。
 * @param paths 桌面端路径提供器。
 * @param assetDir 随包资源目录（用于核对已安装的漫画源）。
 */
internal suspend fun desktopVeneraImportFromPreview(
    preview: DesktopArchivePreview,
    selected: Set<DesktopBackupModule>,
    strategy: DesktopImportStrategy,
    paths: PathProvider?,
    assetDir: File?,
): Result<DesktopVeneraRestoreOutcome> = withContext(Dispatchers.IO) {
    runCatching {
        val core = DatabasePorts.of(null).core
        val db = core.writer()
        CoreDbSchema.ensureOn(db)
        val payload = preview.payload

        var historyImported = 0
        var statsImported = 0
        var guardImported = 0
        var guardSkipped = 0
        var followsImported = 0
        var followSyncsImported = 0

        db.inTransaction {
            if (DesktopBackupModule.HISTORY in selected) {
                if (strategy == DesktopImportStrategy.OVERWRITE) {
                    db.exec("DELETE FROM comic_history")
                }
                historyImported = CoreTableBackup.importHistory(db, payload.history)
            }
            if (DesktopBackupModule.STATS in selected) {
                if (strategy == DesktopImportStrategy.OVERWRITE) {
                    db.exec("DELETE FROM reading_stats")
                }
                statsImported = importStatsDedup(db, payload.stats, strategy == DesktopImportStrategy.MERGE)
            }
            if (DesktopBackupModule.GUARD_RULES in selected) {
                if (strategy == DesktopImportStrategy.OVERWRITE) {
                    db.exec("DELETE FROM content_guard_rules")
                }
                val res = importGuardDedup(db, payload.guardRules, strategy == DesktopImportStrategy.MERGE)
                guardImported = res.imported
                guardSkipped = res.skipped
            }
            // 追更两表与收藏同域：覆盖式先清空，合并式按主键覆盖（归档写什么就是什么）。
            if (DesktopBackupModule.FAVORITES in selected &&
                (payload.follows.size() > 0 || payload.followSyncs.size() > 0)
            ) {
                if (strategy == DesktopImportStrategy.OVERWRITE) {
                    db.exec("DELETE FROM comic_follow")
                    db.exec("DELETE FROM comic_follow_sync_source")
                }
                followsImported = CoreTableBackup.importTable(db, "comic_follow", payload.follows)
                followSyncsImported =
                    CoreTableBackup.importTable(db, "comic_follow_sync_source", payload.followSyncs)
            }
        }

        // 事务已提交再通知追更账本内存流刷库，保证界面立刻见到恢复出来的新数据。
        if (DesktopBackupModule.FAVORITES in selected &&
            (followsImported > 0 || followSyncsImported > 0 || strategy == DesktopImportStrategy.OVERWRITE)
        ) {
            runCatching { comicFollowDao().refresh() }
        }

        var foldersImported = 0
        var favoritesImported = 0
        if (DesktopBackupModule.FAVORITES in selected && preview.isVeneraZip) {
            val favManager = LocalFavoritesManager.getInstance(Unit)
            if (strategy == DesktopImportStrategy.OVERWRITE) {
                for (oldFolder in favManager.currentFolders()) {
                    favManager.deleteFolder(oldFolder)
                }
            }
            val existingFolders = favManager.currentFolders().toMutableSet()
            for (folderMeta in payload.favoriteFolders) {
                if (folderMeta.folder.isNotBlank() && existingFolders.add(folderMeta.folder)) {
                    favManager.createFolder(folderMeta.folder, renameWhenInvalidName = false)
                }
                foldersImported++
            }
            val grouped = payload.favoriteItems.groupBy { it.folder }
            for ((folderName, entries) in grouped) {
                val targetFolder = folderName.ifBlank { "默认" }
                if (existingFolders.add(targetFolder)) {
                    favManager.createFolder(targetFolder, renameWhenInvalidName = false)
                }
                val sorted = entries.sortedBy { it.item.displayOrder }.map { it.item }
                favoritesImported += favManager.addComics(targetFolder, sorted)
            }
        }

        var imageFavImported = 0
        if (DesktopBackupModule.IMAGE_FAVORITES in selected && preview.isVeneraZip) {
            val imageStore = FavoriteImagesStore(core)
            if (strategy == DesktopImportStrategy.OVERWRITE) {
                db.exec("DELETE FROM favorite_images")
            }
            imageFavImported = imageStore.restoreBackupFields(payload.imageFavorites)
        }

        var galleryFavImported = 0
        var galleryFollowImported = 0
        if (DesktopBackupModule.GALLERY in selected && preview.isVeneraZip && paths != null) {
            val favStore = GalleryFavoritesStore.getInstance(paths)
            val followStore = GalleryArtistFollowsStore.getInstance(paths)
            if (strategy == DesktopImportStrategy.OVERWRITE) {
                favStore.clear()
                for (old in followStore.follows.value) {
                    followStore.setFollowed(old.site, old.name, false)
                }
            }
            galleryFavImported = favStore.restore(payload.galleryFavorites)
            galleryFollowImported = followStore.restore(payload.galleryFollows)
        }

        var accountsImported = 0
        var accountsSkipped = 0
        if (DesktopBackupModule.SOURCE_ACCOUNTS in selected && preview.hasSourceAccounts && paths != null && assetDir != null) {
            val installedKeys = runCatching { EngineAssets.listSourceKeys(assetDir).toSet() }.getOrDefault(emptySet())
            val dataDir = File(paths.dataRoot, "comic_source").apply { mkdirs() }
            for (row in payload.sourceAccounts) {
                val targetKey = row.key.ifBlank { row.fileName.removeSuffix(".js") }
                if (targetKey !in installedKeys) {
                    accountsSkipped++
                    continue
                }
                val dataFile = File(dataDir, "$targetKey.data")
                val obj = JsonObject()
                if (strategy == DesktopImportStrategy.MERGE && dataFile.isFile) {
                    runCatching { parseJsonObject(dataFile.readText()) }.getOrNull()?.let { existing ->
                        for ((k, v) in existing.entrySet()) {
                            obj.add(k, v)
                        }
                    }
                }
                for ((k, v) in row.data) {
                    obj.add(k, plainToJson(v))
                }
                dataFile.writeText(obj.toString())
                accountsImported++
            }
        }

        DesktopVeneraRestoreOutcome(
            fileName = preview.fileName,
            strategy = strategy,
            history = historyImported,
            favorites = favoritesImported,
            folders = foldersImported,
            imageFavorites = imageFavImported,
            galleryFavorites = galleryFavImported,
            galleryFollows = galleryFollowImported,
            stats = statsImported,
            guardImported = guardImported,
            guardSkipped = guardSkipped,
            follows = followsImported,
            followSyncs = followSyncsImported,
            sourceAccountsImported = accountsImported,
            sourceAccountsSkipped = accountsSkipped,
        )
    }
}

private fun importStatsDedup(db: com.venera.compose.data.platform.SqlDatabase, rows: JsonArray, dedup: Boolean): Int {
    if (!dedup) return CoreTableBackup.importStats(db, rows)
    val known = HashSet<String>()
    db.query("SELECT comic_id, source_name, chapter_title, read_date, created_at FROM reading_stats").forEach { r ->
        known += "${r.string("comic_id")}|${r.string("source_name")}|${r.string("chapter_title")}|${r.string("read_date")}|${r.long("created_at")}"
    }
    val filtered = JsonArray()
    for (i in 0 until rows.size()) {
        val obj = rows.get(i).takeIf { it.isJsonObject }?.asJsonObject ?: continue
        val key = "${strOrEmpty(obj, "comic_id")}|${strOrEmpty(obj, "source_name")}|${strOrEmpty(obj, "chapter_title")}|${strOrEmpty(obj, "read_date")}|${longOrZero(obj, "created_at")}"
        if (known.add(key)) filtered.add(obj)
    }
    return CoreTableBackup.importStats(db, filtered)
}

private fun importGuardDedup(
    db: com.venera.compose.data.platform.SqlDatabase,
    rows: JsonArray,
    dedup: Boolean,
): CoreTableBackup.GuardImportOutcome {
    if (!dedup) {
        return CoreTableBackup.importGuardRules(db, rows) { GuardRulePattern.compiles(it) }
    }
    val known = HashSet<String>()
    db.query("SELECT rule_type, pattern FROM content_guard_rules").forEach { r ->
        known += "${r.string("rule_type")}|${r.string("pattern")}"
    }
    val filtered = JsonArray()
    for (i in 0 until rows.size()) {
        val obj = rows.get(i).takeIf { it.isJsonObject }?.asJsonObject ?: continue
        val key = "${strOrEmpty(obj, "rule_type")}|${strOrEmpty(obj, "pattern")}"
        if (known.add(key)) filtered.add(obj)
    }
    return CoreTableBackup.importGuardRules(db, filtered) { GuardRulePattern.compiles(it) }
}

private fun writeZipEntry(zos: ZipOutputStream, name: String, text: String) {
    zos.putNextEntry(ZipEntry(name))
    zos.write(text.toByteArray(Charsets.UTF_8))
    zos.closeEntry()
}

private fun readZipText(zip: ZipFile, name: String): String? {
    val entry = zip.getEntry(name) ?: return null
    return zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
}

private fun readZipArray(zip: ZipFile, name: String): JsonArray {
    val text = readZipText(zip, name) ?: return JsonArray()
    if (text.isBlank()) return JsonArray()
    val parsed = JsonParser.parseString(text)
    return if (parsed.isJsonArray) parsed.asJsonArray else JsonArray()
}

private fun parseFavoriteFolders(arr: JsonArray): List<ParsedFavoriteFolder> = buildList {
    for (i in 0 until arr.size()) {
        val obj = arr.get(i).takeIf { it.isJsonObject }?.asJsonObject ?: continue
        val folder = strOrEmpty(obj, "folder").ifBlank { continue }
        add(ParsedFavoriteFolder(folder, strOrEmpty(obj, "sourceKey"), strOrEmpty(obj, "sourceFolder")))
    }
}

private fun parseFavoriteEntries(arr: JsonArray): List<ParsedFavoriteEntry> = buildList {
    for (i in 0 until arr.size()) {
        val obj = arr.get(i).takeIf { it.isJsonObject }?.asJsonObject ?: continue
        val id = strOrEmpty(obj, "id").ifBlank { strOrEmpty(obj, "comic_id") }
        if (id.isBlank()) continue
        val folder = strOrEmpty(obj, "folder").ifBlank { strOrEmpty(obj, "folder_name") }.ifBlank { "默认" }
        val tagsList = mutableListOf<String>()
        val tagsEl = obj.get("tags")
        if (tagsEl != null && tagsEl.isJsonArray) {
            tagsEl.asJsonArray.forEach { t -> if (t.isJsonPrimitive) tagsList += t.asString }
        } else if (tagsEl != null && tagsEl.isJsonPrimitive) {
            tagsEl.asString.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { tagsList += it }
        }
        val item = FavoriteItem(
            id = id,
            name = strOrEmpty(obj, "name").ifBlank { strOrEmpty(obj, "title") },
            author = strOrEmpty(obj, "author"),
            sourceKey = strOrEmpty(obj, "sourceKey").ifBlank { strOrEmpty(obj, "source_name") },
            tags = tagsList,
            coverPath = strOrEmpty(obj, "coverPath").ifBlank { strOrEmpty(obj, "cover_url") },
            time = strOrEmpty(obj, "time").ifBlank { FavoriteItem.currentTimeString() },
            displayOrder = intOrZero(obj, "displayOrder"),
        )
        add(
            ParsedFavoriteEntry(
                folder = folder,
                item = item,
                updateTime = obj.get("updateTime")?.takeIf { it.isJsonPrimitive }?.asString,
                hasNewUpdate = obj.get("hasNewUpdate")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false,
                lastCheckTime = obj.get("lastCheckTime")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asLong,
            ),
        )
    }
}

private fun encodeFavoriteItem(item: FavoriteItem, folder: String): JsonObject = JsonObject().apply {
    addProperty("id", item.id)
    addProperty("name", item.name)
    addProperty("author", item.author)
    addProperty("sourceKey", item.sourceKey)
    add("tags", JsonArray().apply { item.tags.forEach { add(it) } })
    addProperty("coverPath", item.coverPath)
    addProperty("time", item.time)
    addProperty("displayOrder", item.displayOrder)
    addProperty("folder", folder)
}

private fun parseImageFavorites(arr: JsonArray): List<FavoriteImageBackupFields> = buildList {
    for (i in 0 until arr.size()) {
        val obj = arr.get(i).takeIf { it.isJsonObject }?.asJsonObject ?: continue
        val url = strOrEmpty(obj, "image_url").ifBlank { strOrEmpty(obj, "imageUrl") }
        if (url.isBlank()) continue
        add(
            FavoriteImageBackupFields(
                comicId = strOrEmpty(obj, "comic_id").ifBlank { strOrEmpty(obj, "comicId") },
                comicTitle = strOrEmpty(obj, "comic_title").ifBlank { strOrEmpty(obj, "comicTitle") },
                sourceName = strOrEmpty(obj, "source_name").ifBlank { strOrEmpty(obj, "sourceName") },
                chapterTitle = strOrEmpty(obj, "chapter_title").ifBlank { strOrEmpty(obj, "chapterTitle") },
                pageIndex = intOrZero(obj, "page_index"),
                imageUrl = url,
                createdAt = longOrZero(obj, "created_at"),
            ),
        )
    }
}

private fun encodeImageFavorites(rows: List<FavoriteImageBackupFields>): JsonArray = JsonArray().apply {
    for (r in rows) {
        add(JsonObject().apply {
            addProperty("comic_id", r.comicId)
            addProperty("comic_title", r.comicTitle)
            addProperty("source_name", r.sourceName)
            addProperty("chapter_title", r.chapterTitle)
            addProperty("page_index", r.pageIndex)
            addProperty("image_url", r.imageUrl)
            addProperty("created_at", r.createdAt)
        })
    }
}

private fun parseGalleryFollows(raw: String?): List<GalleryArtistFollow> {
    if (raw.isNullOrBlank()) return emptyList()
    val el = JsonParser.parseString(raw)
    if (!el.isJsonArray) return emptyList()
    return buildList {
        for (item in el.asJsonArray) {
            if (!item.isJsonObject) continue
            val obj = item.asJsonObject
            val site = GallerySite.fromRouteKey(strOrEmpty(obj, "site")) ?: continue
            val name = strOrEmpty(obj, "name").ifBlank { continue }
            add(GalleryArtistFollow(site, name, longOrZero(obj, "added_at")))
        }
    }
}

private fun encodeGalleryFollows(list: List<GalleryArtistFollow>): String = JsonArray().apply {
    for (f in list) {
        add(JsonObject().apply {
            addProperty("site", f.site.routeKey)
            addProperty("name", f.name)
            addProperty("added_at", f.addedAt)
        })
    }
}.toString()

private fun parseGalleryFavorites(raw: String?): List<GalleryFavorite> {
    if (raw.isNullOrBlank()) return emptyList()
    val el = JsonParser.parseString(raw)
    if (!el.isJsonArray) return emptyList()
    return buildList {
        for (item in el.asJsonArray) {
            if (!item.isJsonObject) continue
            val obj = item.asJsonObject
            val siteKey = strOrEmpty(obj, "site")
            val id = longOrZero(obj, "id")
            if (siteKey.isBlank() || id == 0L) continue
            add(
                GalleryFavorite(
                    siteKey = siteKey,
                    id = id,
                    savedAt = longOrZero(obj, "saved_at"),
                    tags = strOrEmpty(obj, "tags"),
                    rating = strOrEmpty(obj, "rating"),
                    score = intOrZero(obj, "score"),
                    favCount = obj.get("fav_count")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt,
                    author = strOrEmpty(obj, "author"),
                    source = strOrEmpty(obj, "source"),
                    width = intOrZero(obj, "width"),
                    height = intOrZero(obj, "height"),
                    previewUrl = strOrEmpty(obj, "preview_url"),
                    previewWidth = intOrZero(obj, "preview_width"),
                    previewHeight = intOrZero(obj, "preview_height"),
                    fastUrl = strOrEmpty(obj, "fast_url"),
                    largeUrl = strOrEmpty(obj, "large_url"),
                    largeWidth = intOrZero(obj, "large_width"),
                    largeHeight = intOrZero(obj, "large_height"),
                    fileUrl = strOrEmpty(obj, "file_url"),
                    fileSize = longOrZero(obj, "file_size"),
                    md5 = strOrEmpty(obj, "md5"),
                    fileExt = strOrEmpty(obj, "file_ext"),
                    durationSeconds = obj.get("duration")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble,
                    ugoira = obj.get("ugoira")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false,
                ),
            )
        }
    }
}

private fun encodeGalleryFavorites(list: List<GalleryFavorite>): String = JsonArray().apply {
    for (g in list) {
        add(JsonObject().apply {
            addProperty("site", g.siteKey)
            addProperty("id", g.id)
            addProperty("saved_at", g.savedAt)
            addProperty("tags", g.tags)
            addProperty("rating", g.rating)
            addProperty("score", g.score)
            g.favCount?.let { addProperty("fav_count", it) }
            addProperty("author", g.author)
            addProperty("source", g.source)
            addProperty("width", g.width)
            addProperty("height", g.height)
            addProperty("preview_url", g.previewUrl)
            addProperty("preview_width", g.previewWidth)
            addProperty("preview_height", g.previewHeight)
            addProperty("fast_url", g.fastUrl)
            addProperty("large_url", g.largeUrl)
            addProperty("large_width", g.largeWidth)
            addProperty("large_height", g.largeHeight)
            addProperty("file_url", g.fileUrl)
            addProperty("file_size", g.fileSize)
            addProperty("md5", g.md5)
            addProperty("file_ext", g.fileExt)
            g.durationSeconds?.let { addProperty("duration", it) }
            addProperty("ugoira", g.ugoira)
        })
    }
}.toString()

private fun parseSourceAccounts(raw: String?): List<DesktopSourceAccountRow> {
    if (raw.isNullOrBlank()) return emptyList()
    val el = JsonParser.parseString(raw)
    if (!el.isJsonArray) return emptyList()
    return buildList {
        for (item in el.asJsonArray) {
            if (!item.isJsonObject) continue
            val obj = item.asJsonObject
            val fileName = strOrEmpty(obj, "fileName")
            val key = strOrEmpty(obj, "key")
            if (fileName.isBlank() && key.isBlank()) continue
            val dataObj = obj.getAsJsonObject("data")
            val dataMap = if (dataObj != null) jsonToPlainMap(dataObj) else emptyMap()
            add(
                DesktopSourceAccountRow(
                    fileName = fileName,
                    key = key,
                    name = strOrEmpty(obj, "name"),
                    version = strOrEmpty(obj, "version"),
                    enabled = obj.get("enabled")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: true,
                    pinned = obj.get("pinned")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false,
                    data = dataMap,
                ),
            )
        }
    }
}

private fun encodeSourceAccounts(paths: PathProvider, assetDir: File, overrideDir: File?): JsonArray {
    val keys = runCatching { EngineAssets.listSourceKeys(assetDir).sorted() }.getOrDefault(emptyList())
    val dataDir = File(paths.dataRoot, "comic_source")
    val arr = JsonArray()
    for (key in keys) {
        val dataFile = File(dataDir, "$key.data")
        if (!dataFile.isFile) continue
        val dataObj = runCatching { parseJsonObject(dataFile.readText()) }.getOrNull() ?: continue
        if (dataObj.size() == 0) continue
        arr.add(JsonObject().apply {
            addProperty("fileName", "$key.js")
            addProperty("key", key)
            addProperty("name", key)
            addProperty("version", "")
            addProperty("enabled", true)
            addProperty("pinned", false)
            add("data", dataObj)
        })
    }
    return arr
}

private fun jsonToPlainMap(obj: JsonObject): Map<String, Any?> = LinkedHashMap<String, Any?>(obj.size()).apply {
    for ((k, v) in obj.entrySet()) put(k, jsonToPlain(v))
}

private fun jsonToPlain(el: JsonElement): Any? = when {
    el.isJsonNull -> null
    el.isJsonObject -> jsonToPlainMap(el.asJsonObject)
    el.isJsonArray -> el.asJsonArray.map { jsonToPlain(it) }
    else -> {
        val p = el.asJsonPrimitive
        when {
            p.isBoolean -> p.asBoolean
            p.isNumber -> p.asDouble
            else -> p.asString
        }
    }
}

private fun plainToJson(value: Any?): JsonElement = when (value) {
    null -> JsonNull.INSTANCE
    is JsonElement -> value
    is String -> com.google.gson.JsonPrimitive(value)
    is Boolean -> com.google.gson.JsonPrimitive(value)
    is Number -> com.google.gson.JsonPrimitive(value)
    is Map<*, *> -> JsonObject().apply { for ((k, v) in value) add(k.toString(), plainToJson(v)) }
    is Collection<*> -> JsonArray().apply { value.forEach { add(plainToJson(it)) } }
    else -> com.google.gson.JsonPrimitive(value.toString())
}

private fun strOrEmpty(obj: JsonObject, key: String): String =
    obj.get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()

private fun intOrZero(obj: JsonObject, key: String): Int =
    obj.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt ?: 0

private fun longOrZero(obj: JsonObject, key: String): Long =
    obj.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asLong ?: 0L
