package com.venera.desktop.gallery.ui.selection

import coil3.PlatformContext
import com.venera.compose.data.platform.PathProvider
import com.venera.compose.gallery.data.GalleryPixivWorks
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.PixivImageQuality
import com.venera.desktop.gallery.data.desktopCoilBytes
import com.venera.desktop.gallery.data.desktopSaveImageFileName
import com.venera.desktop.gallery.data.desktopSaveUniqueName
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * 画廊批量动作的实现件：批量下载 / 批量收藏（只补缺失）/ 链接导出。
 *
 * ## 并发
 *
 * 与 `DesktopImagePipeline` 的每主机 3 并发同档：这里自带 [DESKTOP_GALLERY_BATCH_CONCURRENCY] 信号量，
 * 因为 `desktopCoilBytes` 在缓存 miss 时会走一台未配 Dispatcher 的兜底客户端
 * （`DesktopCoilBytes.kt:30-53`），只依赖管线限额并不成立。
 *
 * ## 多页
 *
 * pixiv 多页作品不能只落封面（单卡存图腿只取 `fileUrl`）。批量这一路先问
 * `workDetail` 拿逐页地址，再按页保存；取数失败才退回单张，并在计数里算失败，
 * 不假装"全部保存成功"。
 */

/** 一次批量任务的实时读数（UI 只读它，不猜总数）。 */
internal data class DesktopGalleryBatchProgress(
    val done: Int,
    val total: Int,
    val saved: Int,
    val skipped: Int,
    val failed: Int,
)

/** 批量下载的渲染输入。 */
internal data class DesktopGalleryBatchDownloadRequest(
    val posts: List<GalleryPost>,
    val quality: PixivImageQuality,
    val skipExisting: Boolean,
    val byAuthor: Boolean,
    val dir: File,
)

/** 批量下载结果。`failed` 只数"一张都没落下"的作品，不把部分页失败说成成功。 */
internal data class DesktopGalleryBatchDownloadOutcome(
    val saved: Int,
    val skipped: Int,
    val failed: Int,
) {
    val reading: String
        get() = buildString {
            append("已保存 $saved 张")
            if (skipped > 0) append(" · 跳过已存在 $skipped 张")
            if (failed > 0) append(" · 失败 $failed 张")
        }
}

/** 每主机 3 并发（与 `DesktopImagePipeline.maxRequestsPerHost` 同档）。 */
internal const val DESKTOP_GALLERY_BATCH_CONCURRENCY = 3

/** 数量门槛：超过这个数才做二次确认（设计分析 §3.3）。 */
internal const val DESKTOP_GALLERY_CONFIRM_DOWNLOAD_THRESHOLD = 20
internal const val DESKTOP_GALLERY_CONFIRM_COPY_THRESHOLD = 10

internal suspend fun desktopGalleryBatchDownload(
    context: PlatformContext,
    request: DesktopGalleryBatchDownloadRequest,
    paths: PathProvider?,
    pixiv: GalleryPixivWorks?,
    bearer: String?,
    onProgress: (DesktopGalleryBatchProgress) -> Unit,
): DesktopGalleryBatchDownloadOutcome = coroutineScope {
    val total = request.posts.size
    val done = AtomicInteger(0)
    val saved = AtomicInteger(0)
    val skipped = AtomicInteger(0)
    val failed = AtomicInteger(0)
    val gate = Semaphore(DESKTOP_GALLERY_BATCH_CONCURRENCY)

    fun publish() {
        onProgress(
            DesktopGalleryBatchProgress(
                done = done.get(),
                total = total,
                saved = saved.get(),
                skipped = skipped.get(),
                failed = failed.get(),
            ),
        )
    }

    request.posts.distinctBy { it.uid }.map { post ->
        async(Dispatchers.IO) {
            gate.withPermit {
                val result = runCatching {
                    saveOnePost(context, request, post, paths, pixiv, bearer)
                }.getOrNull()
                when (result) {
                    SaveTally.SAVED -> saved.incrementAndGet()
                    SaveTally.SKIPPED -> skipped.incrementAndGet()
                    null, SaveTally.FAILED -> failed.incrementAndGet()
                }
                done.incrementAndGet()
                publish()
            }
        }
    }.awaitAll()

    DesktopGalleryBatchDownloadOutcome(
        saved = saved.get(),
        skipped = skipped.get(),
        failed = failed.get(),
    )
}

private enum class SaveTally { SAVED, SKIPPED, FAILED }

private suspend fun saveOnePost(
    context: PlatformContext,
    request: DesktopGalleryBatchDownloadRequest,
    post: GalleryPost,
    paths: PathProvider?,
    pixiv: GalleryPixivWorks?,
    bearer: String?,
): SaveTally {
    val urls = resolveBatchUrls(post, request.quality, pixiv, bearer)
    if (urls.isEmpty()) return SaveTally.FAILED
    val dir = batchTargetDir(request, post)
    var wroteAny = false
    var allSkipped = true
    urls.forEachIndexed { index, url ->
        if (url.isBlank()) return@forEachIndexed
        val name = batchFileName(post, index, urls.size, paths)
        if (request.skipExisting && File(dir, name).exists()) {
            return@forEachIndexed
        }
        allSkipped = false
        val bytes = desktopCoilBytes(context, url) ?: return@forEachIndexed
        if (bytes.isEmpty()) return@forEachIndexed
        val written = runCatching {
            if (!dir.exists() && !dir.mkdirs()) error("目录建不出来")
            val unique = desktopSaveUniqueName(dir, name)
            val file = File(dir, unique)
            file.writeBytes(bytes)
            file.length() > 0
        }.getOrDefault(false)
        if (written) wroteAny = true
    }
    return when {
        wroteAny -> SaveTally.SAVED
        allSkipped && request.skipExisting -> SaveTally.SKIPPED
        else -> SaveTally.FAILED
    }
}

private suspend fun resolveBatchUrls(
    post: GalleryPost,
    quality: PixivImageQuality,
    pixiv: GalleryPixivWorks?,
    bearer: String?,
): List<String> {
    if (post.site == GallerySite.PIXIV && pixiv != null && (post.pageCount == null || post.pageCount!! > 1)) {
        val detail = runCatching { pixiv.workDetail(post.id, bearer).getOrNull() }.getOrNull()
        val pages = detail?.pages.orEmpty().map { it.urlFor(quality) }.filter { it.isNotBlank() }
        if (pages.isNotEmpty()) return pages
    }
    return listOf(
        when (quality) {
            PixivImageQuality.ORIGINAL -> post.fileUrl.ifBlank { post.largeUrl }
            PixivImageQuality.LARGE -> post.largeUrl.ifBlank { post.fileUrl }
            PixivImageQuality.MEDIUM -> post.largeUrl.ifBlank { post.fastUrl }.ifBlank { post.previewUrl }
            PixivImageQuality.SMALL -> post.previewUrl.ifBlank { post.fastUrl }
        },
    ).filter { it.isNotBlank() }
}

private fun batchTargetDir(request: DesktopGalleryBatchDownloadRequest, post: GalleryPost): File =
    if (request.byAuthor && post.author.isNotBlank()) {
        File(request.dir, sanitizeAuthorFolder(post.author))
    } else {
        request.dir
    }

private fun sanitizeAuthorFolder(author: String): String =
    author.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_").take(64).ifBlank { "未知作者" }

private fun batchFileName(
    post: GalleryPost,
    index: Int,
    pageCount: Int,
    paths: PathProvider?,
): String {
    if (post.site == GallerySite.PIXIV) {
        val ext = post.fileExt.ifBlank { "jpg" }
        return if (pageCount > 1) {
            "pixiv_${post.id}_p$index.$ext"
        } else {
            "pixiv_${post.id}.$ext"
        }
    }
    val base = desktopSaveImageFileName(post, paths)
    if (pageCount <= 1) return base
    val stem = base.substringBeforeLast('.', base)
    val ext = base.substringAfterLast('.', "")
    return if (ext.isEmpty()) "${stem}_p$index" else "${stem}_p$index.$ext"
}

/**
 * 批量收藏：**只补缺失项**，不循环 toggle。
 *
 * `addFavorite` 交回 `(nowFavored, message)`；已收藏的项在调用前就被剔掉，
 * 所以不会出现"批量收藏把原先的收藏取消掉"。
 */
internal suspend fun desktopGalleryBatchFavoriteAdd(
    posts: List<GalleryPost>,
    isFavored: (GalleryPost) -> Boolean,
    addFavorite: suspend (GalleryPost) -> Pair<Boolean, String>,
): Pair<Int, Int> {
    var added = 0
    var failed = 0
    posts.distinctBy { it.uid }.forEach { post ->
        if (isFavored(post)) return@forEach
        val ok = runCatching { addFavorite(post).first }.getOrDefault(false)
        if (ok) added++ else failed++
    }
    return added to failed
}

/** 链接导出类型（与弹窗第三档同名）。 */
internal enum class DesktopGalleryLinkKind(val label: String) {
    SITE("站方作品链接"),
    IMAGE("图片直链"),
    TAGS("Tags"),
}

/** 把选中作品拼成剪贴板文本；空项剔除，不写占位地址。 */
internal fun desktopGalleryLinkText(
    posts: List<GalleryPost>,
    kind: DesktopGalleryLinkKind,
): String = posts.distinctBy { it.uid }.mapNotNull { post ->
    when (kind) {
        DesktopGalleryLinkKind.SITE -> post.pageUrl.takeIf { it.isNotBlank() }
        DesktopGalleryLinkKind.IMAGE -> post.fileUrl.takeIf { it.isNotBlank() }
            ?: post.largeUrl.takeIf { it.isNotBlank() }
        DesktopGalleryLinkKind.TAGS -> post.tags.takeIf { it.isNotBlank() }
    }
}.joinToString("\n")

/** 画质档的界面文案（三档，与设计稿一致）。 */
internal val PixivImageQuality.dockLabel: String
    get() = when (this) {
        PixivImageQuality.ORIGINAL -> "原图（优先最高清）"
        PixivImageQuality.LARGE -> "1200 大图（兼顾速度）"
        PixivImageQuality.MEDIUM -> "中画质（极速保存）"
        PixivImageQuality.SMALL -> "小图"
    }

/** 批量下载前的二次确认文案。 */
internal fun desktopGalleryBatchConfirmLine(count: Int, kind: String): String =
    "$kind $count 项，超过单次建议数量，继续吗？"
