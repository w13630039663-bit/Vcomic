package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GalleryContentGuard
import com.venera.compose.gallery.data.GalleryPixivCollections
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.data.PixivWork
import com.venera.compose.gallery.domain.GalleryForYouMerge
import com.venera.compose.gallery.domain.GalleryPreviewQuality
import com.venera.compose.gallery.domain.PixivSearchFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * pixiv **专属搜索**那两条腿（落地）。
 *
 * ## 为什么不吃 `fetchSearchPage`
 *
 * `boardOf` 对 `GallerySite.PIXIV` 是会说话的抛（pixiv 与 booru 的 `tags=` 语法不同轴，
 * 调研稿 §八），搜索那颗逐站编排件接不了它。落地判据（去重键 / 守卫 / 页码账）照
 * [desktopPixivWallFirstPage] 与推荐墙（`desktopPixivRecommendedFirstPage`）同一套现成件。
 *
 * ## 两条腿怎么分（对岸 `GallerySearchViewModel.runPixivSearch` 同口径）
 *
 * - **作品 · 人气**：`popularPreview`（登录腿）。安卓非会员走的就是这一跳、会员走
 *   `searchIllust` 真参数；桌面取同一跳对两档都真（预览腿站方不给续页 ⇒ `sitesDone` 记到底）。
 * - **作品 · 其余**：`tagWorks`（匿名 www 腿）——排序/标签模式/比例/时间窗/排除 AI 都在
 *   `webParams()` 里真生效，收藏门槛由 `filter.searchWord` 折成词尾 `users入り` 标签；
 *   **作品类型 + 比例**是站方没参数的那两档，本地筛 [PixivSearchFilter.keeps]（安卓同一颗判据）。
 * - **画师**：`searchUser`（登录腿，游标翻页）。没登录那一档交回带原因的空页，
 *   页尾念得出"要登录"而不是只剩空墙。
 */
internal const val DESKTOP_PIXIV_SEARCH_WORKS = 0
internal const val DESKTOP_PIXIV_SEARCH_ARTIST = 1

/** pixiv 搜索第三维：珍藏册（目标下拉的显示序与 kind 值不同轴，映射住在搜索屏）。 */
internal const val DESKTOP_PIXIV_SEARCH_COLLECTION = 2

/**
 * 珍藏册搜索一页（匿名 www 腿，吃 [GalleryPixivCollections.search]）。
 *
 * 与作品 / 画师两维不同轴：不吃图墙也不出用户行，交回珍藏册卡表 + 总数 + 下一页 offset
 * （`nextOffset` 为 null = 到底）。[word] 是标签词；空串 = 「大家的一览」。
 */
internal suspend fun desktopPixivCollectionsSearchPage(
    collections: GalleryPixivCollections,
    word: String,
    mode: String = "all",
    offset: Int,
): Result<com.venera.compose.gallery.data.PixivCollectionsSearchPage> = withContext(Dispatchers.IO) {
    collections.search(word, mode = mode, offset = offset)
}

/** 本地筛（作品类型 + 画面比例）——与安卓 ViewModel 同一颗判据，桌面不另写一份。 */
private fun List<PixivWork>.keepsAll(filter: PixivSearchFilter): List<PixivWork> =
    filter { w -> filter.keeps(w.ugoira, w.post.width, w.post.height) }

/** pixiv 搜索首屏。 */
internal suspend fun desktopPixivSearchFirstPage(
    ports: GalleryPorts,
    word: String,
    filter: PixivSearchFilter,
    kind: Int,
    quality: GalleryPreviewQuality = DESKTOP_WALL_QUALITY,
    guard: GalleryContentGuard? = null,
    maskMode: String = "OFF",
    blockAi: Boolean = false,
): DesktopWallPage = withContext(Dispatchers.IO) {
    val site = GallerySite.PIXIV
    val base = DesktopGalleryBatch(seed = System.currentTimeMillis(), queries = mapOf(site to word))
    val pixiv = ports.pixiv ?: return@withContext DesktopWallPage(
        base.copy(failures = mapOf(site to "这台桌面还没装 pixiv 客户端")),
        emptyList(),
        quality,
    )

    // 作品 · 人气：登录腿 popularPreview（站方不给续页 ⇒ 一轮到底）。
    if (filter.order.needsPremium) {
        val bearer = ports.pixivAccount?.bearerOrNull()
            ?: return@withContext DesktopWallPage(
                base.copy(failures = mapOf(site to "「人气」需要登录 pixiv 账号（账号板里登）")),
                emptyList(),
                quality,
            )
        val page = pixiv.popularPreview(bearer, filter.searchWord(word, premium = false)).getOrNull()
            ?: return@withContext DesktopWallPage(
                base.copy(failures = mapOf(site to "人气预览没取到（登录态可能过期）")),
                emptyList(),
                quality,
            )
        val fresh = page.works.keepsAll(filter).map { it.post }
        val batch = DesktopGalleryBatch(
            seed = base.seed,
            pageBySite = mapOf(site to 1),
            queries = base.queries,
            posts = fresh,
            seenKeys = fresh.flatMap { GalleryForYouMerge.keysOf(it) }.toSet(),
            sitesDone = setOf(site),
        )
        val (cards, wall) = desktopWallGuardedWall(fresh, quality, guard, maskMode, blockAi)
        return@withContext DesktopWallPage(
            batch, cards, quality, wall = wall,
            avatars = page.works.mapNotNull { w ->
                w.post.uid.takeIf { w.authorAvatarUrl.isNotBlank() }?.let { it to w.authorAvatarUrl }
            }.toMap(),
            titles = page.works.mapNotNull { w ->
                w.post.uid.takeIf { w.title.isNotBlank() }?.let { it to w.title }
            }.toMap(),
        )
    }

    // 匿名搜索分支同样投影 title 与 authorAvatarUrl，避免卡片信息区退回「站名+编号」。
    val works = pixiv.tagWorks(filter.searchWord(word, premium = false), page = 1, filter = filter)
        .getOrThrow()
    val fresh = works.keepsAll(filter).map { it.post }
    val batch = DesktopGalleryBatch(
        seed = base.seed,
        pageBySite = mapOf(site to 1),
        queries = base.queries,
        posts = fresh,
        seenKeys = fresh.flatMap { GalleryForYouMerge.keysOf(it) }.toSet(),
        sitesDone = if (works.isEmpty()) setOf(site) else emptySet(),
    )
    val (cards, wall) = desktopWallGuardedWall(fresh, quality, guard, maskMode, blockAi)
    DesktopWallPage(
        batch, cards, quality, wall = wall,
        avatars = works.mapNotNull { w ->
            w.post.uid.takeIf { w.authorAvatarUrl.isNotBlank() }?.let { it to w.authorAvatarUrl }
        }.toMap(),
        titles = works.mapNotNull { w ->
            w.post.uid.takeIf { w.title.isNotBlank() }?.let { it to w.title }
        }.toMap(),
    )
}

/** pixiv 搜索续页。形状照推荐墙那颗续页（游标腿）与搜索页码腿各自点名。 */
internal suspend fun fetchPixivSearchNext(
    ports: GalleryPorts,
    base: DesktopWallPage,
    word: String,
    filter: PixivSearchFilter,
    kind: Int,
    guard: GalleryContentGuard?,
    maskMode: String,
    blockAi: Boolean,
): Result<DesktopWallPage> = withContext(Dispatchers.IO) {
    runCatching {
        val site = GallerySite.PIXIV
        val pixiv = ports.pixiv ?: error("这台桌面还没装 pixiv 客户端")

        check(!filter.order.needsPremium) { "人气预览没有下一页（不该发这一笔）" }
        check(kind != DESKTOP_PIXIV_SEARCH_ARTIST) { "画师搜索不走图墙续页（走 desktopPixivSearchUsersPage）" }
        val page = (base.batch.pageBySite[site] ?: 1) + 1
        val works = pixiv.tagWorks(filter.searchWord(word, premium = false), page, filter).getOrThrow()
        val fresh = works.keepsAll(filter).map { it.post }
        // 空页（或本地筛后空）= 到底；不记到底会变成「下一页」恒在而恒不出货。
        val batch = base.batch.copy(
            pageBySite = base.batch.pageBySite + (site to page),
            posts = base.batch.posts + fresh,
            seenKeys = base.batch.seenKeys + fresh.flatMap { GalleryForYouMerge.keysOf(it) },
            sitesDone = if (fresh.isEmpty()) base.batch.sitesDone + site else base.batch.sitesDone,
        )
        val (newCards, newWall) = desktopWallGuardedWall(fresh, base.quality, guard, maskMode, blockAi)
        DesktopWallPage(
            batch = batch,
            cards = base.cards + newCards,
            quality = base.quality,
            wall = com.venera.compose.gallery.ui.GalleryWall(
                cards = base.wall.cards + newWall.cards,
                blockedCount = base.wall.blockedCount + newWall.blockedCount,
                blockedRules = (base.wall.blockedRules + newWall.blockedRules).distinct(),
                hiddenByRating = base.wall.hiddenByRating + newWall.hiddenByRating,
            ),
            avatars = base.avatars + works.mapNotNull { w ->
                w.post.uid.takeIf { w.authorAvatarUrl.isNotBlank() }?.let { it to w.authorAvatarUrl }
            },
            titles = base.titles + works.mapNotNull { w ->
                w.post.uid.takeIf { w.title.isNotBlank() }?.let { it to w.title }
            },
        )
    }
}

/**
 * 画师搜索一页（登录腿 `searchUser`，游标翻页）。**画师维度不吃图墙**：结果是一排画师行
 * （屏上自渲染），点击进 `DesktopRoute.PixivArtist` 那颗既有主页 —— 所以这一腿直接交
 * 用户表 + 下一页游标，不套 `DesktopWallPage`。
 */
internal suspend fun desktopPixivSearchUsersPage(
    ports: GalleryPorts,
    word: String,
    nextUrl: String?,
): Result<Pair<List<com.venera.compose.gallery.data.PixivUser>, String?>> = withContext(Dispatchers.IO) {
    runCatching {
        val pixiv = ports.pixiv ?: error("这台桌面还没装 pixiv 客户端")
        val bearer = ports.pixivAccount?.bearerOrNull()
            ?: error("搜画师需要先登录 pixiv 账号（账号板里登）")
        val page = pixiv.searchUser(bearer, word, nextUrl).getOrThrow()
        page.users to page.nextUrl
    }
}
