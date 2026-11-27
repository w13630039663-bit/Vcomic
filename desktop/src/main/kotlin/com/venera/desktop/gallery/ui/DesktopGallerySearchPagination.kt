package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GalleryContentGuard
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryDailyFeed
import com.venera.compose.gallery.domain.GalleryForYouMerge
import com.venera.compose.gallery.domain.GalleryLegGuard
import com.venera.compose.gallery.domain.GalleryPreviewQuality
import com.venera.compose.gallery.domain.PixivSearchFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * 画廊搜索翻页方式：无限滚动瀑布流 vs 确定性页数切换。
 */
enum class DesktopSearchPaginationMode(val label: String, val shortLabel: String) {
    INFINITE("无限滚动", "无限"),
    PAGED("分页切换", "分页"),
}

/**
 * 解析用户手输的页码文本：仅接受正整数（>= 1），非法或超界返回 null。
 */
internal fun parseSearchJumpPage(raw: String, maxPage: Int = 9999): Int? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty() || trimmed.any { !it.isDigit() }) return null
    val parsed = trimmed.toIntOrNull() ?: return null
    return parsed.takeIf { it in 1..maxPage }
}

/**
 * 同一次搜索内的页级内存缓存：切回已访问页秒开不重拉；搜索上下文变更时自动清空。
 */
internal class DesktopSearchPageCache(private val maxEntries: Int = 20) {
    private var contextKey: String = ""
    private val pages = LinkedHashMap<Int, DesktopWallPage>()

    /** 切换搜索上下文（词/源/排序/筛选/模式）；上下文不同则清空旧页缓存。 */
    fun bindContext(key: String) {
        if (key != contextKey) {
            contextKey = key
            pages.clear()
        }
    }

    /** 读取指定页缓存；未命中返回 null。 */
    fun get(page: Int): DesktopWallPage? = pages[page]

    /** 写入指定页缓存；超出容量时淘汰最早缓存页。 */
    fun put(page: Int, wallPage: DesktopWallPage) {
        if (page < 1) return
        pages[page] = wallPage
        if (pages.size > maxEntries) {
            val oldestKey = pages.keys.firstOrNull()
            if (oldestKey != null) pages.remove(oldestKey)
        }
    }

    /** 清空所有缓存页。 */
    fun clear() {
        pages.clear()
    }
}

/**
 * 图库（Booru）域按指定页码并发拉取独立一页（不累加前页）。
 *
 * 守卫走唯一落口 [desktopWallGuardedWall]，失败与缺席账口径与 [desktopWallFirstPage] 同源。
 */
internal suspend fun desktopWallSearchSpecificPage(
    ports: GalleryPorts,
    queries: Map<GallerySite, String>,
    silent: Map<GallerySite, String> = emptyMap(),
    page: Int,
    quality: GalleryPreviewQuality = DESKTOP_WALL_QUALITY,
    guard: GalleryContentGuard? = null,
    maskMode: String = "OFF",
    blockAi: Boolean = false,
): DesktopWallPage = withContext(Dispatchers.IO) {
    val targetPage = page.coerceAtLeast(1)
    val base = DesktopGalleryBatch(seed = System.currentTimeMillis(), queries = queries)
    if (queries.isEmpty()) {
        return@withContext DesktopWallPage(base.copy(failures = silent), emptyList(), quality)
    }

    val outcomes = coroutineScope {
        queries.map { (site, query) ->
            val board = boardOf(ports, site)
            site to async {
                val waitMs = if (
                    queries.size == 1 &&
                    (site == GallerySite.KONACHAN_R18 || site == GallerySite.KONACHAN || site == GallerySite.SAFEBOORU)
                ) {
                    45_000L
                } else {
                    GalleryDailyFeed.PER_SITE_TIMEOUT_MS
                }
                GalleryLegGuard.guard(site, timeoutMs = waitMs) {
                    board.searchPosts(query, page = targetPage, limit = board.pageSize)
                }
            }
        }.map { (site, deferred) -> site to deferred.await() }
    }.toMap()

    val answered = outcomes.filterValues { it.answered }.mapValues { it.value.posts }
    val reasons = outcomes.mapNotNull { (site, o) -> o.reason?.let { site to it } }.toMap() + silent
    val batch = desktopWallBatchAfter(
        base = base,
        pages = queries.keys.associateWith { targetPage },
        answered = answered,
        reasons = reasons,
        pageSizeOf = { site -> boardOf(ports, site).pageSize },
    )
    val (cards, wall) = desktopWallGuardedWall(batch.posts, quality, guard, maskMode, blockAi)
    DesktopWallPage(batch, cards, quality, wall = wall)
}

/**
 * Pixiv 作品域按指定页码拉取独立一页（不累加前页）。
 *
 * 人气预览档站方仅提供单页，直接复用 [desktopPixivSearchFirstPage]。
 */
internal suspend fun desktopPixivSearchSpecificPage(
    ports: GalleryPorts,
    word: String,
    filter: PixivSearchFilter,
    page: Int,
    quality: GalleryPreviewQuality = DESKTOP_WALL_QUALITY,
    guard: GalleryContentGuard? = null,
    maskMode: String = "OFF",
    blockAi: Boolean = false,
): DesktopWallPage = withContext(Dispatchers.IO) {
    val targetPage = page.coerceAtLeast(1)
    if (filter.order.needsPremium || targetPage == 1) {
        return@withContext desktopPixivSearchFirstPage(
            ports = ports,
            word = word,
            filter = filter,
            kind = DESKTOP_PIXIV_SEARCH_WORKS,
            quality = quality,
            guard = guard,
            maskMode = maskMode,
            blockAi = blockAi,
        )
    }

    val site = GallerySite.PIXIV
    val base = DesktopGalleryBatch(seed = System.currentTimeMillis(), queries = mapOf(site to word))
    val pixiv = ports.pixiv ?: return@withContext DesktopWallPage(
        base.copy(failures = mapOf(site to "这台桌面还没装 pixiv 客户端")),
        emptyList(),
        quality,
    )

    val works = pixiv.tagWorks(filter.searchWord(word, premium = false), page = targetPage, filter = filter)
        .getOrThrow()
    val fresh = works.filter { w -> filter.keeps(w.ugoira, w.post.width, w.post.height) }.map { it.post }
    val batch = DesktopGalleryBatch(
        seed = base.seed,
        pageBySite = mapOf(site to targetPage),
        queries = base.queries,
        posts = fresh,
        seenKeys = fresh.flatMap { GalleryForYouMerge.keysOf(it) }.toSet(),
        sitesDone = if (works.isEmpty()) setOf(site) else emptySet(),
    )
    val (cards, wall) = desktopWallGuardedWall(fresh, quality, guard, maskMode, blockAi)
    DesktopWallPage(
        batch = batch,
        cards = cards,
        quality = quality,
        wall = wall,
        avatars = works.mapNotNull { w ->
            w.post.uid.takeIf { w.authorAvatarUrl.isNotBlank() }?.let { it to w.authorAvatarUrl }
        }.toMap(),
        titles = works.mapNotNull { w ->
            w.post.uid.takeIf { w.title.isNotBlank() }?.let { it to w.title }
        }.toMap(),
    )
}
