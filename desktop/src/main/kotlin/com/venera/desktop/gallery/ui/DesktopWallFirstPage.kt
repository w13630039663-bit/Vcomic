package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GalleryContentGuard
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryLegGuard
import com.venera.compose.gallery.domain.GalleryPreviewQuality
import com.venera.compose.gallery.ui.GalleryCard
import com.venera.compose.gallery.ui.GalleryWall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * 那一面墙用的缩略档 —— **首屏与续页共用这一枚**。
 *
 * 为什么提出来而不是让两处各写一档：续页那一趟要重算卡（[desktopWallGuardedCards]），
 * 而它吃的是 [DesktopGalleryBatch] 里**新到的那一段**条目。两处各挑一档的结果是
 * "翻一页之前是预览、翻页之后变大图"（或反过来），而那种事只在屏幕上看得出来、
 * 没有任何一条尺子会红。设置里那一枚 `galleryPreviewQuality` 今天**没有**接到这一屏
 * （`DesktopWallPreferenceWiringTest` ⑦ 那条只有 KDoc 而没有 `@Test`，惰的），
 * 所以这里是一枚写死的档而不是一条流 —— 那句话也要写在这里，别让人以为接过了。
 */
internal val DESKTOP_WALL_QUALITY: GalleryPreviewQuality = GalleryPreviewQuality.PREVIEW

/**
 * 首屏与续页**共用**的那一枚落地形状：这一轮的账本 + 墙上真摆出来的那些卡。
 *
 * ## 为什么两格要装在同一颗值里
 *
 * 分开存（一枚 `List<DesktopWallCardModel>` + 一枚 `DesktopGalleryBatch?`）的话，续页那一趟就会
 * 出现"卡是新一批的而页码账还是旧一批的"那一帧 —— 页尾那句「已取 N 张」数的是旧批，
 * 墙上摆的是新批，而两样各自都对得上自己的出处 ⇒ **没有任何一条尺子会红**
 * （手册 §0 坑 ㉓ 那一族：少的东西没人会去数，只有对账抓得到）。装成一颗值，"换一轮"就是换整颗。
 *
 * ## 那两枚数**故意不相等**
 *
 * [batch]`.posts` 数的是**取回**的条目（过了混排去重、没过守卫），[cards] 数的是**上屏**的那些
 * （`HIDDEN` 那一档不落屏）。`DesktopWallCountSourceTest` 批 13 钉的正是"两个主语两个数"，
 * 页尾那颗件因此两枚都要吃（见 [desktopGalleryWallFooter]）。
 */
internal data class DesktopWallPage(
    val batch: DesktopGalleryBatch,
    val cards: List<DesktopWallCardModel>,
    /**
     * 这一批卡是按哪一档缩略图算的。**续页照抄上一批的那一枚**（[desktopWallPageAfter] 从
     * [base] 取），所以"翻页之前是预览、翻页之后变大图"那一族在形状上就不成立 ——
     * 写成两颗各挑一档才是会漂的那一种。
     */
    val quality: GalleryPreviewQuality = DESKTOP_WALL_QUALITY,
    /**
     * 这一批卡的**落屏墙**（阶段三 · 首屏共享批 T3）：`cards` 是它的视图投影（同一批 `wall.cards`
     * map 出来的，两枚同源）。共享页 `GalleryForYouPage` 吃的是这一枚 —— 桌面从此不再自画墙卡，
     * 而是把同一份判据产物递回共享件渲。**排在 `quality` 之后**是给既有三参位构造留活路，
     * 默认空墙只给"账而没墙"的极端夹具用 —— 活路不是常态，活壳两条路都必须显式递墙。
     */
    val wall: GalleryWall = GalleryWall(emptyList(), 0, emptyList(), 0),
    /**
     * uid → 画师头像（卡片要显示画师头像）。
     *
     * 只有**给得出头像的那条腿**填它（pixiv 推荐/榜单：头像就在同一条响应里，
     * `PixivWork.authorAvatarUrl`，不另发一次请求）；booru 三站的 board 行今天站方没给 ⇒ 空表，
     * 卡上退首字母座 —— 不拿别人的头像顶、不补默认图（那会让人以为站方给过）。
     */
    val avatars: Map<String, String> = emptyMap(),
    /** uid → 站方原题（pixiv 腿 `PixivWork.title` 现成；booru 无题，缺位走词典/编号那条既有路）。 */
    val titles: Map<String, String> = emptyMap(),
    /**
     * 游标翻页的下一页指针（`/v1/illust/recommended` 只认 `next_url`，
     * 没有页码可推）。`null` = 没有游标（页码账那一族）**或这一腿到底了**（续页据此把到底摆上）。
     */
    val nextCursor: String? = null,
    /**
     * 这一面墙是不是 **pixiv 推荐腿**取来的。续页那颗 effect 靠它分派：
     * 推荐墙走游标腿 `fetchPixivRecommendedNext`（只认 `nextCursor`），榜单墙走页码腿
     * `fetchPixivWallPage`——两腿的翻页机制不是一件事，不能靠 `nextCursor == null` 反推
     *（推荐到底时也是 null，会与榜单墙混成一档）。
     */
    val isRecommended: Boolean = false,
)

/**
 * 为你推荐多轮避让环：记录最近几轮展示过的作品 uid 与画师，容量 240 条 / 120 位。
 *
 * 画师那一份必须有：只记 uid 时，热门画师换一张新图就算"没见过"，避让形同虚设。
 */
internal object DesktopRecommendedAvoidanceRing {
    private const val MAX_CAPACITY = 240
    private const val MAX_AUTHORS = 120
    private val ring = java.util.ArrayDeque<String>()
    private val set = HashSet<String>()
    private val authorRing = java.util.ArrayDeque<String>()
    private val authorSet = HashSet<String>()

    @Synchronized
    fun isRecent(uid: String): Boolean = uid in set

    @Synchronized
    fun isRecentAuthor(author: String): Boolean = author.isNotBlank() && author in authorSet

    @Synchronized
    fun record(uids: Collection<String>, authorKeys: Collection<String> = emptyList()) {
        for (uid in uids) {
            if (uid.isNotBlank() && set.add(uid)) {
                ring.addLast(uid)
                if (ring.size > MAX_CAPACITY) set.remove(ring.removeFirst())
            }
        }
        for (author in authorKeys) {
            if (author.isNotBlank() && authorSet.add(author)) {
                authorRing.addLast(author)
                if (authorRing.size > MAX_AUTHORS) authorSet.remove(authorRing.removeFirst())
            }
        }
    }
}

/**
 * 推荐档只摆静态图：gif 与视频（mp4 / webm）整条不进来。
 *
 * 这一屏是扫图用的墙，动图/视频的静帧在卡上既看不出内容也播不了，只会挤占格子。
 */
internal fun desktopRecommendKeepsStatic(post: GalleryPost): Boolean {
    val ext = post.fileExt.lowercase()
    return ext != "gif" && !post.isVideo
}

/** 画师键：站方给的画师/上传者名（归一大小写）；空串不能互为同一人，退回作品号。 */
internal fun recommendAuthorKey(post: GalleryPost): String =
    post.author.trim().lowercase().ifBlank { "uid:${post.uid}" }

/**
 * 同画师配额打散：同一画师最多 [perAuthorCap] 张，超出的顺延到队尾；
 * 顺延段再按「与队尾作者不同」的顺序重排，尽量不出现同画师连排。
 *
 * 不丢弃任何条目 —— 热门池就那些人，砍掉就等于池子更小；这里只改次序。
 */
internal fun desktopRecommendDisperse(
    posts: List<GalleryPost>,
    perAuthorCap: Int = 2,
): List<GalleryPost> {
    if (posts.size < 2 || perAuthorCap < 1) return posts
    // 按画师分队列（保留原次序），再轮转出牌：一人一张、转够 perAuthorCap 轮就停。
    // 轮转天然不会让同一人连排 —— 逐张"遇到同人就跳过"那种写法在池子被一家占满时会退化。
    val queues = LinkedHashMap<String, ArrayDeque<GalleryPost>>()
    for (post in posts) {
        queues.getOrPut(recommendAuthorKey(post)) { ArrayDeque() }.addLast(post)
    }
    val out = ArrayList<GalleryPost>(posts.size)
    var round = 0
    while (round < perAuthorCap) {
        var took = false
        for (queue in queues.values) {
            val next = queue.removeFirstOrNull() ?: continue
            out += next
            took = true
        }
        if (!took) break
        round++
    }
    // 配额用满后的余量排在尾部，仍按"与队尾不同"挑，能错开就错开。
    val pending = queues.values.flatMap { it }.toMutableList()
    while (pending.isNotEmpty()) {
        val last = out.lastOrNull()?.let { recommendAuthorKey(it) }
        val idx = pending.indexOfFirst { recommendAuthorKey(it) != last }
        out += pending.removeAt(if (idx >= 0) idx else 0)
    }
    return out
}

/** 推荐档的池子站：Konachan 顶掉 Safebooru（同为全站高分池，后者与 Gelbooru 题材重叠）。 */
internal val desktopRecommendPoolSites: Set<GallerySite> = setOf(
    GallerySite.YANDERE,
    GallerySite.GELBOORU,
    GallerySite.KONACHAN,
)

/**
 * 「推荐」那一档的首屏。
 *
 * 三条腿并发取池：yande.re 日榜 + Gelbooru 高分池 + Konachan 高分池。
 * Safebooru 那条腿撤了 —— 它与 Gelbooru 同为全站高分池、题材高度重叠，换成 Konachan
 * 才真把池子拉开（`desktopRecommendPoolSites` 是这条口径的唯一出处）。
 *
 * 之后四步都是本地加工，不再联网：
 * 1. 只留静态图（gif / 视频剔除）；
 * 2. 每站池子先洗牌，再走共享那颗 `GallerySearchMerge.interleave`（跨站轮转 + 去重）；
 * 3. 避让环按 uid **与画师**双键判定，见过的整体后置；
 * 4. 同画师配额打散（最多 2 张、不相邻），热门画师不再刷屏。
 *
 * 续页由调用点回落各站默认序（最新）—— 热门池这一轮已整池上墙，池尽才该换腿。
 */
internal suspend fun desktopWallRecommendedFirstPage(
    ports: GalleryPorts,
    quality: GalleryPreviewQuality = DESKTOP_WALL_QUALITY,
    guard: GalleryContentGuard? = null,
    maskMode: String = "OFF",
    blockAi: Boolean = false,
): DesktopWallPage = withContext(Dispatchers.IO) {
    // 每站一笔独立预算（12s）在腿守卫里，慢站缺席会被说出来，快站照旧出图。
    val boards = ports.boards
    val (yande, gelbooru, konachan) = coroutineScope {
        val y = async { GalleryLegGuard.guard(GallerySite.YANDERE) { boards.yandere.fetchDailyPopular() } }
        val g = async { GalleryLegGuard.guard(GallerySite.GELBOORU) { boards.gelbooru.fetchTopScored() } }
        val k = async { GalleryLegGuard.guard(GallerySite.KONACHAN) { boards.konachan.fetchTopScored() } }
        Triple(y.await(), g.await(), k.await())
    }
    val failures = listOf(yande, gelbooru, konachan)
        .mapNotNull { outcome -> outcome.reason?.let { outcome.site to it } }
        .toMap()
    val pools = mapOf(
        GallerySite.YANDERE to yande.posts.filter(::desktopRecommendKeepsStatic),
        GallerySite.GELBOORU to gelbooru.posts.filter(::desktopRecommendKeepsStatic),
        GallerySite.KONACHAN to konachan.posts.filter(::desktopRecommendKeepsStatic),
    ).filterKeys { it in desktopRecommendPoolSites }
    val sites = pools.filterValues { it.isNotEmpty() }.keys.toList()
    if (sites.isEmpty()) {
        println("D_取数 推荐首屏 塌了 三腿都没有静态图 缺席=${failures.size}")
        // 交回带原因的每站空账：页尾那句因此念得出"谁没答上"，而不是只剩"这一面墙还是空的"。
        return@withContext DesktopWallPage(
            DesktopGalleryBatch(seed = System.currentTimeMillis(), queries = emptyMap())
                .copy(failures = failures),
            emptyList(),
            quality,
        )
    }
    // 腿表用**空串**（各站默认序）：续页顺着"最新"续，与那两档共用同一本账。
    val base = DesktopGalleryBatch(
        seed = System.currentTimeMillis(),
        queries = sites.associateWith { "" },
    )

    val answered = pools.filterValues { it.isNotEmpty() }.mapValues { (_, posts) ->
        // 各站内部先洗牌：站方返回是单调的高分序，不洗就是"先看高分站的全部再下一站"。
        posts.shuffled()
    }
    val merged = com.venera.compose.gallery.domain.GallerySearchMerge
        .interleave(answered, base.seenKeys).getOrThrow()

    val (recentPosts, freshPosts) = merged.posts.partition { post ->
        DesktopRecommendedAvoidanceRing.isRecent(post.uid) ||
            DesktopRecommendedAvoidanceRing.isRecentAuthor(recommendAuthorKey(post))
    }
    val orderedPosts = desktopRecommendDisperse(freshPosts) + desktopRecommendDisperse(recentPosts)

    val batch = base.copy(
        pageBySite = sites.associateWith { 1 },
        posts = orderedPosts,
        seenKeys = base.seenKeys + orderedPosts.flatMap {
            com.venera.compose.gallery.domain.GalleryForYouMerge.keysOf(it)
        },
        failures = failures,
        droppedUnusable = merged.droppedUnusable,
        droppedDuplicates = merged.droppedDuplicates,
    )
    println(
        "D_取数 推荐首屏 腿=${sites.size} 候选=${merged.posts.size} 新=${freshPosts.size} " +
            "避让=${recentPosts.size} 缺席=${failures.size}",
    )
    val (recCards, recWall) = desktopWallGuardedWall(batch.posts, quality, guard, maskMode, blockAi)
    // 上屏那些才进避让环（被守卫挡下的没让用户看见，不该占用避让名额）。
    DesktopRecommendedAvoidanceRing.record(
        uids = recCards.map { it.uid },
        authorKeys = recCards.mapNotNull { card -> card.sourcePost?.let(::recommendAuthorKey) },
    )
    DesktopWallPage(batch, recCards, quality, wall = recWall)
}

/**
 * 桌面墙的**第一页真取数** —— 把 `GalleryPorts` 拉回来的东西变成能上屏的卡片。
 *
 * ## 为什么这颗在 `gallery/ui/` 而不在集成方
 *
 * 这里是**判据**（哪几站发车、失败怎么记、怎么去重），不是 UI 也不是平台接线。
 * 放集成方会让它落进 `VeneraDesktop.kt`（那个文件 600+ 行，混着窗口与进程），
 * 而放这里能被单测直接调 —— 与 `fetchWallPage` / `desktopWallCardsOf` 同一层。
 *
 * ## 与 `fetchWallPage` 的关系
 *
 * `fetchWallPage`（`DesktopLatestPane.kt`）是**续页**那一半：吃一个已有的
 * `DesktopGalleryBatch` 算下一笔。这一颗是第一页 —— 造查询表、发、把结果转成卡片。
 * 混排与去重都转交共享面 `GallerySearchMerge.interleave`，桌面不重述第二份判据。
 *
 * ## 首屏也攒那本账
 *
 * 从前这一颗只交回一面墙（`List<DesktopWallCardModel>`），于是**页码账从头就没建立**：
 * 续页那颗 `fetchWallPage` 要吃的 `base` 在 `desktop/src/main` 里没有任何出处
 * （批 23 立了 reducer，批 31 拿真脚本端口把整条链跑通过，而调用点是零 —— 「有逻辑没有门」那一族）。
 * 现在第一页走**同一颗** [desktopWallBatchAfter]（不发网络的那颗落地件），交回 [DesktopWallPage]：
 * 账本 + 卡。于是"第一页能翻而续页的判据漂"那一族从形状上就不成立 ——
 * 两条路共用一份页码账、一份去重键、一份到底名单，也共用下面那颗 [desktopWallGuardedCards] 的守卫判据
 * （遮罩那一路正是"第二条取数路"最容易漏过去的一格，安全评审 B 修的是同一族的第一条）。
 *
 * ## 失败怎么处理
 *
 * 逐站失败**不抛也不吞**：`GalleryLegGuard` 已经把它翻成 `answered = false` + `reason`，
 * 这里照实念进日志、且**只把真答上的腿**交给混排。一站点被墙了不该让整面墙空着，
 * 但"没答上"与"答上了没图"必须能分开（前者不进混排，就还它们各自的页数账）。
 */
internal suspend fun desktopWallFirstPage(
    ports: GalleryPorts,
    gelbooruConfigured: Boolean,
    quality: GalleryPreviewQuality = DESKTOP_WALL_QUALITY,
    /**
     * 内容守卫。**给了才会判遮罩**（安全评审 B：此前生产路径从不判，
     * `DesktopGalleryWallPolicy.of` 成了死代码，于是墙上**一张图都不遮**）。
     *
     * 缺省 `null` 时刻意**打印一行读数**而不是静默放行 —— 让"这一屏没走守卫"
     * 在日志里看得见，而不是让读的人以为它判过了。
     */
    guard: GalleryContentGuard? = null,
    /** 分级档原值（`OFF`/`BLUR`/`HIDE`）。只有 [guard] 非空时才有意义。 */
    maskMode: String = "OFF",
    /** `prefs.galleryBlockAi`。同样只在 [guard] 非空时才有意义。 */
    blockAi: Boolean = false,
): DesktopWallPage = withContext(Dispatchers.IO) {
    // 最新那一档的查询词：三站都用空串 —— 空串在共享面就是"该站的热门/最新"，
    // 与安卓首页那一格同一条路。这不是漏填。
    val sites = desktopWallLegs(gelbooruConfigured)
    val silent = desktopWallSilentSites(gelbooruConfigured)
    if (silent.isNotEmpty()) {
        println("D_取数 不发车 " + silent.entries.joinToString(" ") { (s, why) -> "${s.name}=$why" })
    }
    runFirstPage(ports, sites.associateWith { "" }, silent, quality, guard, maskMode, blockAi)
}

/**
 * 同上，但由调用方点名各站的查询词（搜索那一屏用）。
 *
 * `queries` 里没点到的站**不发车** —— 这是刻意的："这次只问这两站"与"三站都问但第三站
 * 用空串"是两件事，后者会往墙上混进一屏不相关的东西。
 */
internal suspend fun desktopWallFirstPage(
    ports: GalleryPorts,
    queries: Map<GallerySite, String>,
    /**
     * 发车前就没发的腿（Gelbooru 没配账号那一档）连**站方之外的原话**一起交进来。
     *
     * 为什么搜索那一屏非得要这一枚：`queries` 里点名的腿会被算进 `atEnd` 的主语
     * （`queries.isNotEmpty() && sitesDone.containsAll(queries.keys)`），而没配账号的那一站
     * 永远进不了 `sitesDone` ⇒ 「下一页」恒在而恒发不出货（`GallerySearchSource.availableLegs`
     * 的注释自陈的正是这一病）。所以它该从 `queries` 里剔出去、并把原因交给这里念。
     */
    silent: Map<GallerySite, String> = emptyMap(),
    quality: GalleryPreviewQuality = DESKTOP_WALL_QUALITY,
    guard: GalleryContentGuard? = null,
    maskMode: String = "OFF",
    blockAi: Boolean = false,
): DesktopWallPage = withContext(Dispatchers.IO) {
    // 缺席账默认空表；调用方点名腿时若还剔了某条（Gelbooru 没配账号），把它的原话经 [silent]
    // 一并交进来，才与无 queries 那一枚重载同形：不发废请求、而页尾仍念得出「没发车——为什么」。
    runFirstPage(ports, queries, silent, quality, guard, maskMode, blockAi)
}

private suspend fun runFirstPage(
    ports: GalleryPorts,
    queries: Map<GallerySite, String>,
    /** 发车前就没发的腿（Gelbooru 没配凭据那一档）。它们进那本缺席账，而**不进** `queries`。 */
    silent: Map<GallerySite, String>,
    quality: GalleryPreviewQuality,
    guard: GalleryContentGuard?,
    maskMode: String,
    blockAi: Boolean,
): DesktopWallPage {
    // 种子 = 这一轮开始那一刻。这一屏没有「换一批」那一档，那枚种子的作用只剩 reducer 的作废闸，
    // 而**不许写死 0**：两轮同一枚种子等于闸门恒开，"旧那一笔落进新这一轮"就再也判不出来。
    val base = DesktopGalleryBatch(seed = System.currentTimeMillis(), queries = queries)
    if (queries.isEmpty()) {
        // 未发出任何请求时仍返回带 silent 原因的空账（而非 null），以区分「未发车原因」与「尚未加载」。
        return DesktopWallPage(base.copy(failures = silent), emptyList(), quality)
    }

    // 每站一手并发，守卫在腿里（它负责"超预算就记原因"，不把异常冒到墙上）。
    val outcomes = coroutineScope {
        queries.map { (site, query) ->
            val board = boardOf(ports, site)
            site to async {
                // 单站请求若可能触发 Edge 过盾（Konachan / Konachan R18 / Safebooru），放宽至 45s，避免 12s 截断过盾。
                val waitMs = if (
                    queries.size == 1 &&
                    (site == GallerySite.KONACHAN_R18 || site == GallerySite.KONACHAN || site == GallerySite.SAFEBOORU)
                ) {
                    45_000L
                } else {
                    com.venera.compose.gallery.domain.GalleryDailyFeed.PER_SITE_TIMEOUT_MS
                }
                GalleryLegGuard.guard(site, timeoutMs = waitMs) {
                    board.searchPosts(query, page = 1, limit = board.pageSize)
                }
            }
        }.map { (site, deferred) -> site to deferred.await() }
    }.toMap()

    // 只把**真答上的**腿交给混排：给没答上那一站记一页就是把"永久漏掉那一页"写进账本
    // （`fetchWallPage` 的同一条理由，那里写在 `answered` 那一行上）。
    val answered = outcomes.filterValues { it.answered }.mapValues { it.value.posts }
    // 发车前拦截原因（如缺凭据）并入 failures 供页尾展示，但不进 queries 以免拖住 atEnd，也不走并账函数以免干扰测试计数。
    val reasons = outcomes.mapNotNull { (site, o) -> o.reason?.let { site to it } }.toMap() + silent

    println(
        "D_取数 首屏 腿=${queries.size} 答上=${answered.size} " +
            answered.entries.joinToString(" ") { (s, p) -> "${s.name}=${p.size}" } +
            if (reasons.isEmpty()) "" else " 缺席=" + reasons.entries.joinToString(" ") { (s, r) -> "${s.name}($r)" },
    )

    // 全腿失败仍落地空墙与逐站原因；混排抛错时直接上抛，与续页保持一致的失败形态。
    val batch = desktopWallBatchAfter(
        base = base,
        // 第一页：被派出去的腿各在第 1 页（没答上的腿**不许**被记页码，那一判住在 reducer 里）。
        pages = queries.keys.associateWith { 1 },
        answered = answered,
        reasons = reasons,
        pageSizeOf = { site -> boardOf(ports, site).pageSize },
    )

    val (firstCards, firstWall) = desktopWallGuardedWall(batch.posts, quality, guard, maskMode, blockAi)
    return DesktopWallPage(batch, firstCards, quality, wall = firstWall)
}

/**
 * 取回的那批条目 → 墙上那些卡（**遮罩判据唯一的起口**，首屏与续页共用这一颗）。
 *
 * ## 为什么单独提出来
 *
 * 安全评审 B 那一族最阴的复法不是"判据写错"，而是**第二条取数路直接跳过去**：
 * 第一条已经接好了，赶工的人在新那一屏上写 `desktopWallCardsOf(posts)` 就走过去了
 * （而 `desktopWallCardOf` 的 `masked` 形参**默认 false** ⇒ 那一屏一张都不遮，
 * 不报错、不红字、屏上看不出来）。续页今天就是那条新加的第二条路，所以两屏只能吃这一颗。
 *
 * `DesktopWallPolicyWiredTest` ①②③④ 扫的是**这颗文件**的代码行，形状要求一字未改。
 */
internal fun desktopWallGuardedCards(
    posts: List<GalleryPost>,
    quality: GalleryPreviewQuality,
    guard: GalleryContentGuard?,
    maskMode: String,
    blockAi: Boolean,
): List<DesktopWallCardModel> = desktopWallGuardedWall(posts, quality, guard, maskMode, blockAi).first

/**
 * 关注新作腿的投影：壳里那颗**唯一守卫起口**的转交件 ——
 * 与画廊收藏的投卡腿同一条纪律（`DesktopGalleryFavoritesWallEntryTest` 那把尺钉着
 * "壳里直调恰一处"）：另起一路直调就是第二条遮罩路，判据从此分叉。
 */
internal fun desktopWallFollowingCards(
    posts: List<GalleryPost>,
    quality: GalleryPreviewQuality,
    guard: GalleryContentGuard?,
    maskMode: String,
    blockAi: Boolean,
): List<DesktopWallCardModel> = desktopWallGuardedCards(posts, quality, guard, maskMode, blockAi)

/**
 * 守卫起口的**墙形状**（阶段三 · 首屏共享批 T3）：共享页 `GalleryForYouPage` 吃 `GalleryWall`，
 * 而 `DesktopWallCardModel` 那份投影由这里 map 出去 —— 判据、读数、去重仍只此一颗，两枚产物同源。
 */
internal fun desktopWallGuardedWall(
    posts: List<GalleryPost>,
    quality: GalleryPreviewQuality,
    guard: GalleryContentGuard?,
    maskMode: String,
    blockAi: Boolean,
): Pair<List<DesktopWallCardModel>, GalleryWall> {
    if (guard == null) {
        // 缺省不静默：日志里要能看见"这一屏没走守卫"。
        println("D_守卫 未判 理由=调用点没给 guard 张数=${posts.size}")
        val unguarded = DesktopGalleryWallPolicy.unguardedWall(posts)
        return desktopWallCardsOf(posts, quality) to unguarded
    }
    val wall = DesktopGalleryWallPolicy.of(
        posts = posts,
        maskMode = maskMode,
        blockAi = blockAi,
        guard = guard,
    )
    println(
        "D_守卫 判了 档=$maskMode 入=${posts.size} 出=${wall.cards.size} " +
            "遮=${wall.cards.count { it.masked }} 按分级藏=${wall.hiddenByRating} " +
            // 命中规则数直接取 wall.blockedRules.size，不可用总数减遮罩/隐藏数反推。
            "命中规则数=${wall.blockedRules.size}" +
            (if (wall.blockedRules.isEmpty()) "" else " 规则=${wall.blockedRules}"),
    )
    // 把 `DesktopWallCard.masked` 搬进 `DesktopWallCardModel.masked` —— 只搬，不重判。
    // 共享墙一起交回（同一批 `wall.cards` 的两种形状，同源不成两轨）。
    return wall.cards.map { desktopWallCardOf(it.post, quality, masked = it.masked) } to GalleryWall(
        cards = wall.cards.map { GalleryCard(it.post, it.masked) },
        blockedCount = wall.blockedCount,
        blockedRules = wall.blockedRules,
        hiddenByRating = wall.hiddenByRating,
    )
}

/**
 * 续页那一笔落地后的**整颗新值**（守卫仍走 [desktopWallGuardedCards]，那一颗是两屏唯一的起口）。
 *
 * ## 为什么只过"新到的那一段"
 *
 * [next]`.posts` 是**累计**的那一批（[desktopGalleryBatchApply] 的追加语义），而 [base]`.cards`
 * 已经在屏上。整批重过一遍守卫会有两笔账说不清：遮罩那一路的读数「入/出」会翻倍
 * （同一批字节问了两遍守卫），而 LazyVerticalStaggeredGrid 拿到的是全新列表 ⇒ 整面墙重排。
 * 追加语义下"下一批 = 旧批 + 本轮新条目"，所以 `drop` 掉旧批那一段之后剩下的恰好是新的。
 *
 * ⚠️ 那个前提住在 reducer 的追加支上，而它另有例外：[DesktopGalleryBatch] 里
 * `firstPage = pages.values.all { it == 1 }` ⇒ **各腿全在第 1 页那一笔是替换**。
 * 那一笔只可能出现在"旧批本来就空"（各腿全在第 1 页 = ）⇒ `drop(0)` = 全过一遍，
 * 结论仍然对。真正会把整面墙抹掉的是"空 dispatch 那一笔"，而它由 [desktopWallMoreAllowed]
 * 提前挡在门外（判据不许撤，`DesktopGalleryWallFooterTest` ⑧ 钉的是那一屏的行为）。
 */
internal fun desktopWallPageAfter(
    base: DesktopWallPage,
    next: DesktopGalleryBatch,
    guard: GalleryContentGuard?,
    maskMode: String,
    blockAi: Boolean,
): DesktopWallPage {
    // 档位从上一批那枚值里取（不是调用点给）：见 [DesktopWallPage.quality] 那条理由。
    val quality = base.quality
    val fresh = next.posts.drop(base.batch.posts.size)
    // 续页仅对新增片段过守卫，再与上一批累计 blockedCount/hiddenByRating 并去重合并 blockedRules。
    val appended = desktopWallGuardedWall(fresh, quality, guard, maskMode, blockAi)
    return DesktopWallPage(
        batch = next,
        cards = base.cards + appended.first,
        wall = (base.wall.cards + appended.second.cards).let { kept ->
            GalleryWall(
                cards = kept,
                blockedCount = base.wall.blockedCount + appended.second.blockedCount,
                blockedRules = (base.wall.blockedRules + appended.second.blockedRules).distinct(),
                hiddenByRating = base.wall.hiddenByRating + appended.second.hiddenByRating,
            )
        },
        quality = quality,
    )
}

/**
 * 续页那一段的墙合并读数是**累加**的：`blockedCount`/`hiddenByRating` 两本账各段独立成立
 * （被挡的/被收的张数按段累计才是"这一屏一共少了多少"），`blockedRules` 去重并表
 * （同一条规则两段各挡几张仍是一条规则）。这三枚的口径与 [desktopGalleryWallFooter] 那三句一一对上。
 */

/**
 * 桌面侧的 `gelbooruConfigured` 判据。
 *
 * gelbooru 要账号（`GallerySearchSource.NEEDS_ACCOUNT_REASON`），没配就**不发车**，
 * 而不是发了等它 401 —— 后者会在墙上留下一条"没答上"的噪声。
 *
 * 读的是 `gelbooruIdentity` 那条流（契约面唯一与它有关的事实来源）；
 * 流还没出值就当"没配"，那是这一侧的真实状态，不是缺省猜测。
 */
internal fun desktopGelbooruConfigured(ports: GalleryPorts): Boolean =
    runCatching { ports.credentials.gelbooruIdentity.value != null }.getOrDefault(false)

/**
 * 首页「每日热门」那一排的卡。
 *
 * 取数走共享那颗 `GalleryDailyFeed`（三站并发 + 预算 + 混排判据都在它里面，桌面不重述），
 * 落地仍从 [desktopWallGuardedCards] 那一颗**唯一起口**过守卫 —— 与首屏/续页/搜索同一个形状：
 * 第二条取路绕过守卫正是安全评审 B 记过的那族病（`DesktopWallPolicyWiredTest` 扫的是这颗文件
 * 的代码行，这里加一次**转交**调用而不起第二份判据）。
 *
 * 塌了交回空表：那一节整排不摆（壳里 `cards.isEmpty()` 判），日志里留一句读数 ——
 * 首页墙头上少一排海报，比墙上糊一句错误横幅读起来对。
 */
internal suspend fun desktopDailyHomeCards(
    ports: GalleryPorts,
    seed: Long,
    guard: GalleryContentGuard?,
    maskMode: String,
    blockAi: Boolean,
    quality: GalleryPreviewQuality = DESKTOP_WALL_QUALITY,
    /**
     * 渐进上屏钩子（主人口径「首页每日热门要 10 秒才有内容太慢」）：
     * `loadDaily` 的 `onLeg` 每站到货就发一次 —— 这里把已到货的池子**现过守卫**交给回调，
     * 壳里先摆上屏；全量到货后正常交回值再覆盖。快站 1~3 秒就能先亮，不再空等最慢站。
     * **必填无默认值**（`DesktopCallbackParamLedgerTest` 那条本仓明令）。
     */
    onPartial: (List<DesktopWallCardModel>) -> Unit,
): List<DesktopWallCardModel> {
    // 每日热门主腿走 yande.re，依次降级 Konachan 与 Safebooru（Safebooru 常遇 Cloudflare 403 故置末位）。
    val posts = ports.boards.yandere.fetchDailyPopular().recoverCatching { t ->
        println("D_取数 每日热门 塌了 ${t::class.java.simpleName}: ${t.message?.take(160)} 退=Konachan")
        ports.boards.konachan.fetchTopScored().getOrThrow()
    }.recoverCatching { t ->
        println("D_取数 每日热门 塌了 ${t::class.java.simpleName}: ${t.message?.take(160)} 退=Safebooru")
        ports.boards.safebooru.fetchTopScored().getOrThrow()
    }.fold(
        onSuccess = { it },
        onFailure = { t ->
            println("D_取数 每日热门 塌了 ${t::class.java.simpleName}: ${t.message?.take(160)}")
            return emptyList()
        },
    )
    if (posts.isNotEmpty()) onPartial(desktopWallGuardedCards(posts, quality, guard, maskMode, blockAi))
    println("D_取数 每日热门 条=${posts.size} 站=yande.re")
    return desktopWallGuardedCards(posts, quality, guard, maskMode, blockAi)
}
