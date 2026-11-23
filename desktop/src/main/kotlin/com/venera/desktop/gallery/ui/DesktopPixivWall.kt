package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GalleryContentGuard
import com.venera.compose.gallery.data.GalleryPixivWorks
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.data.PixivRankingMode
import com.venera.compose.gallery.data.PixivWork
import com.venera.compose.gallery.domain.GalleryLegGuard
import com.venera.compose.gallery.domain.GalleryLegOutcome
import com.venera.compose.gallery.domain.GalleryPreviewQuality
import com.venera.compose.gallery.ui.GalleryWall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/**
 * pixiv 那一域的墙：首屏与「下一页」都走**匿名日榜**那条腿。
 *
 * ## 为什么切域必须换真内容，而不是换一块皮
 *
 * 那一排「图库站 / Pixiv」切换器是共享件 `GalleryHomeChrome` 里画出来的。若桌面切了域而墙
 * 还是原来那面 booru 墙，那枚切换器就是一枚假开关（本仓明令禁的那一形状）。所以这一批把
 * pixiv 那一档落成**真取数的腿**：`www.pixiv.net/ranking.php?mode=daily&p=N` ——
 * 现量 200、每页 50 条、**不需要登录**（R-18 两档匿名 403，一枚都不摆，理由逐字写在
 * `PixivClient.ranking` 与 `PixivRankingMode` 的注里）。
 * 首页其余各节（排行榜多档 / 推荐 / 关注画师）归下一批；这一批先把"切过去看到的是 pixiv 的图"
 * 落成事实。
 *
 * ## 为什么 pixiv 要自己的两颗而不是复用 `fetchWallPage`
 *
 * `boardOf` 对 `GallerySite.PIXIV` 是**会说话的抛**（调研稿 §八：pixiv 不进来源轴，
 * 它与 booru 的 `tags=` 语法不同轴）。所以按 `queries` 逐站走 `boardOf` 的那颗续页件接不了它。
 * 这两颗与 `fetchWallPage` 共用**同一批落地判据**（[desktopWallBatchAfter] 里的混排/去重/页码账/
 * 到底名单，缺席账那颗 [desktopFailureLedgerAfter]，守卫那颗唯一 [desktopWallGuardedCards]），
 * 只有"这一腿发什么请求"几行不同 —— 这正是"第二条取数路"应有的形状：判据复用，腿各自点名。
 *
 * ## 页码账
 *
 * `queries` 的键是 `GallerySite.PIXIV`（值恒空串：日榜没有查询词这一说，mode 由调用点点名而不是
 * 折进词里 —— 下一批排行榜多档时 mode 会成为真状态，届时另立口径）。`atEnd` 那句
 * 「发过的腿全到底」认的还是同一本 `sitesDone` 账，页尾那颗件不必为 pixiv 另写一行。
 */
internal suspend fun desktopPixivWallFirstPage(
    ports: GalleryPorts,
    mode: PixivRankingMode = PixivRankingMode.DAILY,
    quality: GalleryPreviewQuality = DESKTOP_WALL_QUALITY,
    guard: GalleryContentGuard? = null,
    maskMode: String = "OFF",
    blockAi: Boolean = false,
    /** 手动刷新腿：绕盘缓存真问站方。 */
    bypassCache: Boolean = false,
): DesktopWallPage = withContext(Dispatchers.IO) {
    val site = GallerySite.PIXIV
    val base = DesktopGalleryBatch(
        seed = System.currentTimeMillis(),
        queries = mapOf(site to ""),
    )
    val pixiv = ports.pixiv
    if (pixiv == null) {
        // 这一台没装 pixiv 客户端（`GalleryPorts.pixiv` 可空）：交回一颗带**原因**的空账。
        // 页尾那句因此念得出"这台还没装"而不是只剩"这一面墙还是空的" ——
        // 把我们的闸说成站方的空结果，是修过的那族假话同形。
        println("D_取数 pixiv首屏 不发车 原因=本机没装pixiv")
        return@withContext DesktopWallPage(
            base.copy(failures = mapOf(site to "这台桌面还没装 pixiv 客户端")),
            emptyList(),
            quality,
        )
    }
    // 榜单首屏直接问站方（不走 fetchPixivRanking）：头像与原题两张表要从同一响应的 works 里带出，
    // outcome 形状只装 GalleryPost 装不下 —— 缺了它卡上就没头像没原题（主人报）。
    val ranked: Result<List<PixivWork>>? = withTimeoutOrNull(4000L) {
        runCatching { pixiv.ranking(mode, page = 1, fresh = bypassCache).getOrThrow() }
    }
    val works = ranked?.getOrDefault(emptyList()) ?: emptyList()
    val outcome = GalleryLegGuard.outcomeOf(
        site = site,
        timedOut = ranked == null,
        outcome = ranked?.map { works -> works.map { it.post } },
    )
    println(
        "D_取数 pixiv首屏 ${mode.label} p=1 答上=${outcome.answered} 条=${outcome.posts.size}" +
            (outcome.reason?.let { " 原因=$it" } ?: ""),
    )
    val batch = desktopWallBatchAfter(
        base = base,
        pages = mapOf(site to 1),
        answered = if (outcome.answered) mapOf(site to outcome.posts) else emptyMap(),
        reasons = outcome.reason?.let { mapOf(site to it) } ?: emptyMap(),
        pageSizeOf = { PIXIV_RANKING_PAGE },
    )
    val (pixivCards, pixivWall) = desktopWallGuardedWall(batch.posts, quality, guard, maskMode, blockAi)
    return@withContext DesktopWallPage(
        batch = batch,
        cards = pixivCards,
        quality = quality,
        wall = pixivWall,
        // 与推荐腿同形状：uid → 头像 / 站方原题，键同 `post.uid`（卡上唯一卡吃同一把键）。
        avatars = works.mapNotNull { w ->
            w.post.uid.takeIf { w.authorAvatarUrl.isNotBlank() }?.let { it to w.authorAvatarUrl }
        }.toMap(),
        titles = works.mapNotNull { w ->
            w.post.uid.takeIf { w.title.isNotBlank() }?.let { it to w.title }
        }.toMap(),
    )
}

/**
 * pixiv 墙的续页 —— 与 [desktopPixivWallFirstPage] 同一条腿的下一页。
 *
 * 形状逐字对照 `fetchWallPage`（那颗对 PIXIV 会撞 `boardOf` 的抛，所以这里另发同一笔请求）：
 * 到底的腿不再问 → [desktopGalleryBatchLegs]；各腿要第几页 → [desktopGalleryBatchNextPages]；
 * 缺席账 → [desktopFailureLedgerAfter]；落地 → [desktopWallBatchAfter]。
 * 壳里那一支按域分派这两颗，「下一页」在两域都是门后有货的真把手。
 */
internal suspend fun fetchPixivWallPage(
    ports: GalleryPorts,
    base: DesktopGalleryBatch,
    mode: PixivRankingMode = PixivRankingMode.DAILY,
): Result<DesktopGalleryBatch> = withContext(Dispatchers.IO) {
    runCatching {
        val site = GallerySite.PIXIV
        val dispatched = desktopGalleryBatchLegs(base, mapOf(site to "")).keys.toList()
        val pages = desktopGalleryBatchNextPages(base, dispatched)
        if (pages.isEmpty()) return@runCatching base
        val pixiv = ports.pixiv
            ?: return@runCatching base.copy(failures = mapOf(site to "这台桌面还没装 pixiv 客户端"))
        val page = pages.getValue(site)
        val outcome = fetchPixivRanking(pixiv, mode, page)
        val answered = if (outcome.answered) mapOf(site to outcome.posts) else emptyMap()
        val reasons = desktopFailureLedgerAfter(
            previous = base.failures,
            asked = setOf(site),
            fresh = outcome.reason?.let { mapOf(site to it) } ?: emptyMap(),
        )
        desktopWallBatchAfter(
            base = base,
            pages = pages,
            answered = answered,
            reasons = reasons,
            pageSizeOf = { PIXIV_RANKING_PAGE },
        )
    }
}

/** 那一腿发一次（4 秒快速超时拦截；[fresh] = 手动刷新腿绕盘缓存真问站方）。 */
private suspend fun fetchPixivRanking(
    pixiv: GalleryPixivWorks,
    mode: PixivRankingMode,
    page: Int,
    fresh: Boolean = false,
): GalleryLegOutcome =
    withTimeoutOrNull(4000L) {
        GalleryLegGuard.guard(GallerySite.PIXIV) {
            // `PixivWork.post` 本来就是 GalleryPost（那颗契约注释自陈"pixiv 给的是站方原题"），
            // 所以这里只取 `.post`，不 `toPost()` —— 那是三站 board 行的扩展，pixiv 没有第二套。
            pixiv.ranking(mode, page = page, fresh = fresh).map { works -> works.map { it.post } }
        }
    } ?: GalleryLegOutcome(GallerySite.PIXIV, emptyList(), answered = false, reason = "超时")

// 登录态推荐流按 next_url 游标续页（pageBySite 仅作轮次计数）；未登录返回 null 由调用点降级为榜单流。

/**
 * 推荐墙首屏。`null` = 这台没接 pixiv / 没登录（调用点落回 [desktopPixivWallFirstPage]）。
 * 头像与原题两张表随页交回（`avatars` / `titles`，都在同一条响应里，零额外往返）。
 */
internal suspend fun desktopPixivRecommendedFirstPage(
    ports: GalleryPorts,
    quality: GalleryPreviewQuality = DESKTOP_WALL_QUALITY,
    guard: GalleryContentGuard? = null,
    maskMode: String = "OFF",
    blockAi: Boolean = false,
    /** 手动刷新腿：绕盘缓存真问站方。 */
    bypassCache: Boolean = false,
): DesktopWallPage? = withContext(Dispatchers.IO) {
    val pixiv = ports.pixiv ?: return@withContext null
    val bearer = ports.pixivAccount?.bearerOrNull() ?: return@withContext null
    // recommended 是登录腿且翻页只认游标，加 4 秒快速超时：塌了/超时与"没登录"同样交回 null，由调用点落回匿名榜单腿。
    val pageResult = runCatching {
        withTimeout(4000L) {
            pixiv.recommended(bearer, fresh = bypassCache)
        }
    }.getOrElse { Result.failure(it) }
    val page = pageResult.getOrNull()
    println(
        "D_取数 pixiv推荐 首屏 答上=${page != null} 条=${page?.works?.size ?: 0}" +
            if (page?.nextUrl != null) "（游标可续）" else "（游标即尽）",
    )
    pageResult.exceptionOrNull()?.let { println("D_取数 pixiv推荐 首屏 塌了 ${it::class.java.simpleName}: ${it.message?.take(160)}") }
    if (page == null) return@withContext null
    val site = GallerySite.PIXIV
    val fresh = page.works.map { it.post }
    val batch = DesktopGalleryBatch(
        seed = System.currentTimeMillis(),
        pageBySite = mapOf(site to 1),
        queries = mapOf(site to ""),
        posts = fresh,
        seenKeys = fresh.flatMap { com.venera.compose.gallery.domain.GalleryForYouMerge.keysOf(it) }.toSet(),
        // 游标尽 = 这一腿到底；页尾与近尾观察器因此不用为推荐另写判据。
        sitesDone = if (page.nextUrl == null) setOf(site) else emptySet(),
    )
    val (cards, wall) = desktopWallGuardedWall(fresh, quality, guard, maskMode, blockAi)
    DesktopWallPage(
        batch = batch,
        cards = cards,
        quality = quality,
        wall = wall,
        avatars = page.works.mapNotNull { w ->
            w.post.uid.takeIf { w.authorAvatarUrl.isNotBlank() }?.let { it to w.authorAvatarUrl }
        }.toMap(),
        titles = page.works.mapNotNull { w ->
            w.post.uid.takeIf { w.title.isNotBlank() }?.let { it to w.title }
        }.toMap(),
        nextCursor = page.nextUrl,
        isRecommended = true,
    )
}

/**
 * 推荐墙续页：吃上一颗页的 `nextCursor`。游标缺位（没到货 / 已尽 / 这台落的本来就是榜单墙）
 * 交回 `Result.failure`——调用点那记扳机本就不该发，发了是 bug，要响而不是静。
 */
internal suspend fun fetchPixivRecommendedNext(
    ports: GalleryPorts,
    base: DesktopWallPage,
    guard: GalleryContentGuard?,
    maskMode: String,
    blockAi: Boolean,
): Result<DesktopWallPage> = withContext(Dispatchers.IO) {
    val cursor = base.nextCursor
        ?: return@withContext Result.failure(IllegalStateException("推荐续页没有游标可用（不该发这一笔）"))
    val pixiv = ports.pixiv
        ?: return@withContext Result.failure(IllegalStateException("这台桌面没装 pixiv 客户端"))
    val bearer = ports.pixivAccount?.bearerOrNull()
        ?: return@withContext Result.failure(IllegalStateException("pixiv 登录态没了"))
    runCatching {
        // 同首屏：recommended 回 `PixivAppPage`（works + nextUrl），不是"一批 post"，
        // 不套 `GalleryLegGuard`。已在 runCatching 里，塌了直接冒到调用点那一趟折叠成 Failed 相位。
        val page = pixiv.recommended(bearer, cursor).getOrThrow()
        val site = GallerySite.PIXIV
        // 只留**没见过的**（游标流也可能回重）：去重键这本账与页码腿同一口径——
        // 一颗 post 的任一枚键已在 `seenKeys` 里就算重，剔掉。`fresh` 就是这一笔新段。
        val fresh = page.works.map { it.post }.filter { post ->
            com.venera.compose.gallery.domain.GalleryForYouMerge.keysOf(post).none { it in base.batch.seenKeys }
        }
        val batch = base.batch.copy(
            pageBySite = base.batch.pageBySite + (site to ((base.batch.pageBySite[site] ?: 1) + 1)),
            posts = base.batch.posts + fresh,
            seenKeys = base.batch.seenKeys + fresh.flatMap { com.venera.compose.gallery.domain.GalleryForYouMerge.keysOf(it) },
            sitesDone = if (page.nextUrl == null) base.batch.sitesDone + site else base.batch.sitesDone,
        )
        val (newCards, newWall) = desktopWallGuardedWall(fresh, base.quality, guard, maskMode, blockAi)
        DesktopWallPage(
            batch = batch,
            cards = base.cards + newCards,
            quality = base.quality,
            wall = GalleryWall(
                cards = base.wall.cards + newWall.cards,
                blockedCount = base.wall.blockedCount + newWall.blockedCount,
                blockedRules = (base.wall.blockedRules + newWall.blockedRules).distinct(),
                hiddenByRating = base.wall.hiddenByRating + newWall.hiddenByRating,
            ),
            avatars = base.avatars + page.works.mapNotNull { w ->
                w.post.uid.takeIf { w.authorAvatarUrl.isNotBlank() }?.let { it to w.authorAvatarUrl }
            }.toMap(),
            titles = base.titles + page.works.mapNotNull { w ->
                w.post.uid.takeIf { w.title.isNotBlank() }?.let { it to w.title }
            }.toMap(),
            nextCursor = page.nextUrl,
            // ⚠️ 续页交回的**整颗页必须带住 isRecommended**：首屏设了、续页漏了就等于
            // 翻完第一页墙被悄悄降级成"榜单页码腿"——下一记近尾扳机会拿推荐段的 batch 去发
            // ranking 请求，行为全错。这一枚跟着 base 走（`base.isRecommended` 恒真才走到这一支）。
            isRecommended = true,
        )
    }
}



/**
 * pixiv 日榜一页的条数。**50** 是站方的页档（`PixivClient.ranking` 的注：每页 50 条、
 * `p=2` 首条 `"rank":51`），不是从屏上"看起来一页有多少"里取的。
 */
private const val PIXIV_RANKING_PAGE = 50
