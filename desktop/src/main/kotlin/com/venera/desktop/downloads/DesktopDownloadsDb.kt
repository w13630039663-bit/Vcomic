package com.venera.desktop.downloads

import com.venera.compose.data.platform.PathProvider
import com.venera.desktop.gallery.data.DESKTOP_LOCAL_COMIC_IMPORTED_SOURCE
import com.venera.desktop.gallery.data.DesktopComicDownloadedChapter
import com.venera.desktop.gallery.data.DesktopLocalComic
import com.venera.desktop.gallery.data.desktopComicChaptersByComic
import com.venera.desktop.gallery.data.desktopInspectLocalComicFolder
import com.venera.desktop.gallery.ui.DesktopComicDownloadListing
import com.venera.desktop.platform.DesktopDatabasePorts
import com.venera.desktop.platform.JdbcSqliteDatabase
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 桌面端下载台账库（`db/desktop_downloads.db`）。
 *
 * 严格区分「漫画下载」与「图片下载」两张表，分别记录内容元数据、源地址（`source_url`）与
 * 本机实际存放路径（`local_dir` / `local_path`）。「本机内容 → 漫画 / 图片」直接读本库，
 * 不再递归扫描用户磁盘文件夹，避免跨目录丢失记录或扫到大目录卡死。
 */
object DesktopDownloadsDb {
    private const val DB_NAME = "desktop_downloads.db"

    private val lock = Any()
    @Volatile
    private var dbFileRef: File? = null
    @Volatile
    private var dbRef: JdbcSqliteDatabase? = null

    private val _version = MutableStateFlow(0L)
    /** 每次写入、更新或删除台账记录后自增，供 UI 响应式刷新列表。 */
    val version: StateFlow<Long> = _version.asStateFlow()

    /** 装配期安装台账库路径（与 `DesktopDatabasePorts.install` 同生命周期）。 */
    fun install(paths: PathProvider) {
        installFile(DesktopDatabasePorts.dbFileAt(paths, DB_NAME))
    }

    /** 单测或自定义路径入口：指定 `.db` 文件位置并重置当前连接。 */
    fun installFile(file: File) {
        synchronized(lock) {
            if (dbFileRef?.absolutePath == file.absolutePath && dbRef != null) return
            runCatching { dbRef?.close() }
            dbRef = null
            dbFileRef = file
        }
        bumpVersion()
    }

    /** 退出应用时按顺序关闭连接。 */
    fun close() {
        synchronized(lock) {
            runCatching { dbRef?.close() }
            dbRef = null
            dbFileRef = null
        }
    }

    private fun obtain(): JdbcSqliteDatabase? = synchronized(lock) {
        dbRef?.let { return it }
        val file = dbFileRef ?: return null
        file.parentFile?.let { if (!it.exists()) it.mkdirs() }
        val db = JdbcSqliteDatabase(file)
        ensureSchema(db)
        dbRef = db
        db
    }

    private fun ensureSchema(db: JdbcSqliteDatabase) {
        db.exec(
            """
            CREATE TABLE IF NOT EXISTS comic_download_records (
                source_key TEXT NOT NULL,
                comic_id TEXT NOT NULL,
                comic_title TEXT NOT NULL,
                cover_url TEXT NOT NULL DEFAULT '',
                chapter_id TEXT NOT NULL,
                chapter_order INTEGER NOT NULL,
                chapter_title TEXT NOT NULL,
                expected_pages INTEGER NOT NULL,
                written_pages INTEGER NOT NULL,
                bytes INTEGER NOT NULL,
                complete INTEGER NOT NULL,
                source_url TEXT NOT NULL DEFAULT '',
                local_dir TEXT NOT NULL,
                downloaded_at INTEGER NOT NULL,
                PRIMARY KEY (source_key, comic_id, chapter_id)
            )
            """.trimIndent(),
        )
        db.exec("CREATE INDEX IF NOT EXISTS idx_comic_dl_time ON comic_download_records(downloaded_at DESC)")
        db.exec(
            """
            CREATE TABLE IF NOT EXISTS image_download_records (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                site TEXT NOT NULL,
                post_id TEXT NOT NULL DEFAULT '',
                title TEXT NOT NULL DEFAULT '',
                file_name TEXT NOT NULL,
                bytes INTEGER NOT NULL,
                dimensions TEXT NOT NULL DEFAULT '',
                source_url TEXT NOT NULL DEFAULT '',
                local_path TEXT NOT NULL UNIQUE,
                downloaded_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.exec("CREATE INDEX IF NOT EXISTS idx_image_dl_time ON image_download_records(downloaded_at DESC)")
        db.exec(
            """
            CREATE TABLE IF NOT EXISTS local_comic_imports (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                source_key TEXT NOT NULL DEFAULT 'imported',
                chapter_count INTEGER NOT NULL,
                total_pages INTEGER NOT NULL,
                root_path TEXT NOT NULL UNIQUE,
                is_linked INTEGER NOT NULL DEFAULT 1,
                imported_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.exec("CREATE INDEX IF NOT EXISTS idx_local_comic_imports_time ON local_comic_imports(imported_at DESC)")
    }

    private fun bumpVersion() {
        _version.value = _version.value + 1L
    }

    /** 记录或更新一话已下载漫画的台账信息。 */
    fun recordComicChapter(
        sourceKey: String,
        comicId: String,
        comicTitle: String,
        coverUrl: String,
        chapterId: String,
        chapterOrder: Int,
        chapterTitle: String,
        expectedPages: Int,
        writtenPages: Int,
        bytes: Long,
        complete: Boolean,
        sourceUrl: String,
        localDir: String,
        downloadedAt: Long = System.currentTimeMillis(),
    ) {
        val db = runCatching { obtain() }.getOrNull() ?: return
        runCatching {
            db.exec(
                """
                INSERT OR REPLACE INTO comic_download_records (
                    source_key, comic_id, comic_title, cover_url,
                    chapter_id, chapter_order, chapter_title,
                    expected_pages, written_pages, bytes, complete,
                    source_url, local_dir, downloaded_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                sourceKey,
                comicId,
                comicTitle,
                coverUrl,
                chapterId,
                chapterOrder,
                chapterTitle,
                expectedPages,
                writtenPages,
                bytes,
                if (complete) 1 else 0,
                sourceUrl,
                localDir,
                downloadedAt,
            )
            bumpVersion()
        }
    }

    /** 从台账删除指定漫画章节记录。 */
    fun deleteComicChapter(sourceKey: String, comicId: String, chapterId: String) {
        val db = runCatching { obtain() }.getOrNull() ?: return
        runCatching {
            db.exec(
                "DELETE FROM comic_download_records WHERE source_key = ? AND comic_id = ? AND chapter_id = ?",
                sourceKey,
                comicId,
                chapterId,
            )
            bumpVersion()
        }
    }

    /** 查询指定章节在台账里保存的本机章节目录；未记入则返回 null。 */
    fun comicChapterDirOf(sourceKey: String, comicId: String, chapterId: String): File? {
        val db = runCatching { obtain() }.getOrNull() ?: return null
        return runCatching {
            db.query(
                "SELECT local_dir FROM comic_download_records WHERE source_key = ? AND comic_id = ? AND chapter_id = ? LIMIT 1",
                sourceKey,
                comicId,
                chapterId,
            ).firstOrNull()?.string("local_dir")?.takeIf { it.isNotBlank() }?.let(::File)
        }.getOrNull()
    }

    /** 从数据库直接读取本机已下载漫画列表，不扫描电脑文件夹。 */
    internal fun listComicListing(): DesktopComicDownloadListing {
        val db = runCatching { obtain() }.getOrElse { t ->
            return DesktopComicDownloadListing(
                groups = emptyList(),
                skipped = 0,
                rootExists = false,
                error = "${t.javaClass.simpleName} ${t.message?.take(160) ?: ""}",
            )
        } ?: return DesktopComicDownloadListing(
            groups = emptyList(),
            skipped = 0,
            rootExists = false,
            error = "下载台账库尚未初始化",
        )
        return runCatching {
            val rows = db.query(
                """
                SELECT source_key, comic_id, comic_title, cover_url, chapter_id, chapter_order,
                       chapter_title, expected_pages, written_pages, bytes, complete, local_dir
                FROM comic_download_records
                ORDER BY downloaded_at DESC, chapter_order ASC
                """.trimIndent(),
            )
            val chapters = rows.map { row ->
                val dirPath = row.string("local_dir").orEmpty()
                val dir = File(dirPath)
                val exists = dirPath.isNotBlank() && dir.isDirectory
                val expected = row.long("expected_pages").toInt()
                val written = if (exists) row.long("written_pages").toInt() else 0
                val complete = exists && row.long("complete") == 1L
                DesktopComicDownloadedChapter(
                    sourceKey = row.string("source_key").orEmpty(),
                    comicId = row.string("comic_id").orEmpty(),
                    chapterId = row.string("chapter_id").orEmpty(),
                    order = row.long("chapter_order").toInt(),
                    title = row.string("chapter_title").orEmpty(),
                    expectedPages = expected,
                    writtenPages = written,
                    bytes = if (exists) row.long("bytes") else 0L,
                    complete = complete,
                    comicTitle = row.string("comic_title").orEmpty(),
                    coverUrl = row.string("cover_url").orEmpty(),
                    localDir = dirPath,
                )
            }
            DesktopComicDownloadListing(
                groups = desktopComicChaptersByComic(chapters),
                skipped = 0,
                rootExists = true,
            )
        }.getOrElse { t ->
            DesktopComicDownloadListing(
                groups = emptyList(),
                skipped = 0,
                rootExists = true,
                error = "${t.javaClass.simpleName} ${t.message?.take(160) ?: ""}",
            )
        }
    }

    /** 查询指定漫画在台账里记录的原始标题（用于界面展示补充）。 */
    fun comicTitleOf(sourceKey: String, comicId: String): String? {
        val db = runCatching { obtain() }.getOrNull() ?: return null
        return runCatching {
            db.query(
                "SELECT comic_title FROM comic_download_records WHERE source_key = ? AND comic_id = ? LIMIT 1",
                sourceKey,
                comicId,
            ).firstOrNull()?.string("comic_title")?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    /** 记录一张已下载图片的内容、源地址与本地保存路径。 */
    fun recordImage(
        site: String,
        postId: String,
        title: String,
        fileName: String,
        bytes: Long,
        dimensions: String,
        sourceUrl: String,
        localPath: String,
        downloadedAt: Long = System.currentTimeMillis(),
    ) {
        val db = runCatching { obtain() }.getOrNull() ?: return
        runCatching {
            db.exec(
                """
                INSERT OR REPLACE INTO image_download_records (
                    site, post_id, title, file_name, bytes, dimensions,
                    source_url, local_path, downloaded_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                site,
                postId,
                title,
                fileName,
                bytes,
                dimensions,
                sourceUrl,
                localPath,
                downloadedAt,
            )
            bumpVersion()
        }
    }

    /** 从图片台账删除指定本地路径列表对应的记录。 */
    fun deleteImagesByPaths(paths: List<String>) {
        if (paths.isEmpty()) return
        val db = runCatching { obtain() }.getOrNull() ?: return
        runCatching {
            db.inTransaction {
                for (path in paths) {
                    db.exec("DELETE FROM image_download_records WHERE local_path = ?", path)
                }
            }
            bumpVersion()
        }
    }

    /** 从数据库直接读取本机已下载图片列表（Pixiv 多图自动聚合成单卡），不扫描电脑文件夹。 */
    internal fun listImageItems(): List<DesktopLocalImageItem> {
        val db = runCatching { obtain() }.getOrNull() ?: return emptyList()
        return runCatching {
            val rows = db.query(
                """
                SELECT site, post_id, title, file_name, bytes, dimensions, local_path, downloaded_at
                FROM image_download_records
                ORDER BY downloaded_at DESC
                """.trimIndent(),
            )
            val rawList = rows.map { row ->
                val localPath = row.string("local_path").orEmpty()
                val parentName = File(localPath).parentFile?.name.orEmpty()
                DesktopLocalImageItem(
                    path = localPath,
                    dir = parentName,
                    fileName = row.string("file_name").orEmpty(),
                    bytes = row.long("bytes"),
                    mtime = row.long("downloaded_at"),
                    dimensions = row.string("dimensions").orEmpty(),
                )
            }
            aggregateDesktopImageItems(rawList)
        }.getOrDefault(emptyList())
    }

    /** 记录本地导入或原地关联的漫画文件夹到台账（不依赖当前下载根目录）。 */
    fun recordImportedComic(
        id: String,
        title: String,
        sourceKey: String = DESKTOP_LOCAL_COMIC_IMPORTED_SOURCE,
        chapterCount: Int,
        totalPages: Int,
        rootPath: String,
        isLinked: Boolean,
        importedAt: Long = System.currentTimeMillis(),
    ) {
        val db = runCatching { obtain() }.getOrNull() ?: return
        runCatching {
            db.exec(
                """
                INSERT OR REPLACE INTO local_comic_imports (
                    id, title, source_key, chapter_count, total_pages, root_path, is_linked, imported_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                id,
                title,
                sourceKey,
                chapterCount,
                totalPages,
                rootPath,
                if (isLinked) 1 else 0,
                importedAt,
            )
            bumpVersion()
        }
    }

    /** 从台账读取本地导入/关联漫画列表；路径有效时实时核验话数与页数。 */
    internal fun listImportedComics(): List<DesktopLocalComic> {
        val db = runCatching { obtain() }.getOrNull() ?: return emptyList()
        return runCatching {
            val rows = db.query(
                """
                SELECT id, title, source_key, chapter_count, total_pages, root_path, is_linked, imported_at
                FROM local_comic_imports
                ORDER BY imported_at DESC
                """.trimIndent(),
            )
            rows.mapNotNull { row ->
                val rootPath = row.string("root_path").orEmpty()
                if (rootPath.isBlank()) return@mapNotNull null
                val dir = File(rootPath)
                if (!dir.isDirectory) return@mapNotNull null
                val inspected = desktopInspectLocalComicFolder(dir)
                val chapters = if (inspected.isNotEmpty()) inspected.size else row.long("chapter_count").toInt()
                val pages = if (inspected.isNotEmpty()) inspected.sumOf { it.pageCount } else row.long("total_pages").toInt()
                if (pages <= 0) return@mapNotNull null
                DesktopLocalComic(
                    id = row.string("id").orEmpty(),
                    title = row.string("title").orEmpty().ifBlank { dir.name },
                    sourceKey = row.string("source_key").orEmpty().ifBlank { DESKTOP_LOCAL_COMIC_IMPORTED_SOURCE },
                    chapterCount = chapters,
                    totalPages = pages,
                    rootPath = rootPath,
                    updatedAt = row.long("imported_at"),
                    isLinked = row.long("is_linked") == 1L,
                )
            }
        }.getOrDefault(emptyList())
    }

    /** 按本地根路径移除导入/关联记录。 */
    fun deleteImportedComic(rootPath: String) {
        if (rootPath.isBlank()) return
        val db = runCatching { obtain() }.getOrNull() ?: return
        runCatching {
            db.exec("DELETE FROM local_comic_imports WHERE root_path = ?", rootPath)
            bumpVersion()
        }
    }
}
