package com.venera.desktop.gallery.data

import com.google.gson.reflect.TypeToken
import com.venera.compose.gallery.data.GalleryPixivWorks
import com.venera.compose.gallery.data.PixivStamp
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.PixivAppClient
import com.venera.compose.gallery.data.PixivAppPage
import com.venera.compose.gallery.data.PixivClient
import com.venera.compose.gallery.data.PixivCommentPage
import com.venera.compose.gallery.data.PixivRankingMode
import com.venera.compose.gallery.data.PixivRestrict
import com.venera.compose.gallery.domain.PixivSearchFilter
import com.venera.compose.gallery.data.PixivUgoiraClip
import com.venera.compose.gallery.data.PixivUgoiraStore
import com.venera.compose.gallery.data.PixivUserDetail
import com.venera.compose.gallery.data.PixivUserHead
import com.venera.compose.gallery.data.PixivUserPage
import com.venera.compose.gallery.data.PixivUserWorks
import com.venera.compose.gallery.data.PixivWork
import com.venera.compose.gallery.data.PixivWorkDetail
import java.io.File
import java.lang.reflect.Type
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 桌面侧的 pixiv 取数件（`GalleryPixivWorks` 那 27 枚方法）。
 *
 * ## 为什么这一颗是纯转发，一条判据都不写
 *
 * 形状逐字照 Android 那颗 `AndroidGalleryPixivWorks`（`gallery/data/android/GalleryAdaptersAndroid.kt:209`）：
 * 每一枚都只是把参数原样交给 [PixivClient]（匿名腿）或 [PixivAppClient]（带 Bearer 那一侧）。
 * **这一颗里不许出现第二条判据** —— 过期怎么算、哪一档退匿名腿、失败念哪一句，全在
 * `PixivCredentialState` / 那两颗 client 里。两端各写一份判据就是"界面说已登录而请求全 401"
 * 那一族的成因（本仓记过一整族）。
 *
 * ## 两颗 client 为什么分开持有
 *
 * 与 Android 同一条理由，见 `PixivAppClient` 的文件头：匿名 `www` 那一侧与带凭据 `app-api`
 * 那一侧是不同的宿主、不同的失败形状，合成一颗就会出现"匿名能用所以整颗算能用"的假读数。
 *
 * ## 唯一一处不是纯转发的：`ugoiraClip`
 *
 * 动图逐帧包要 `PixivUgoiraStore`（下 zip + `java.util.zip` 解到缓存目录）。那颗现量
 * **只收平台口 [com.venera.compose.data.platform.HttpEngine] + [com.venera.compose.data.platform.PathProvider]、
 * 不吃 `Context`**（见其 `getInstance`），已在桌面编译面 ⇒ 桌面能真接。逐字照 Android
 * `GalleryAdaptersAndroid.kt:343` 那条双兜底：匿名 www 腿 `PixivClient.ugoiraMeta` 优先，取不到而
 * 又有 bearer 才退带凭据 app-api 腿 `PixivAppClient.ugoiraMetadata`，两腿都没帧表 ⇒ `success(null)`
 * （非 ugoira / 站方没给帧），交回后由屏层退回静帧。app 退路要的 bearer 由装配点经 [bearerProvider]
 * 回喂（桌面没有安卓那台能从 Context 现取的 `PixivAccount`）。
 */
internal class DesktopGalleryPixivWorks(
    private val handle: DesktopGalleryHandle,
    /** 已登录 pixiv 的 access token（app-api ugoira 元信息退路用）。null = 匿名，只走 www 腿。 */
    private val bearerProvider: suspend () -> String?,
    private val requestGuard: DesktopPixivRequestGuard = DesktopPixivRequestGuard(),
) : GalleryPixivWorks {

    private val client: PixivClient get() = PixivClient.getInstance(handle.engine)

    /** 带凭据那一侧（app-api）。与上面那颗分开持有，理由见类注释。 */
    private val appClient: PixivAppClient get() = PixivAppClient.getInstance(handle.engine)

    private val pixivCacheDir = File(handle.paths.cacheRoot, "pixiv_cache")
    private val pixivCache = DesktopPixivCache(pixivCacheDir)
    private val incrementalStore = DesktopPixivIncrementalStore(File(pixivCacheDir, "incremental"))

    /** 触发 429 冷却时的最近恢复时间戳（毫秒），供外部观察限流状态。 */
    internal val cooldownUntilMs = requestGuard.cooldownUntilMs

    private val _bookmarkedIds = MutableStateFlow(incrementalStore.cachedBookmarkIds())

    /** 当前已确认在 Pixiv 云端收藏的作品编号集合，随接口响应与收藏动作实时同步。 */
    internal val bookmarkedIds: StateFlow<Set<Long>> = _bookmarkedIds.asStateFlow()

    private fun syncPayloadBookmarks(payload: Any?, forceBookmarked: Boolean = false) {
        when (payload) {
            is PixivAppPage -> {
                if (payload.works.isEmpty()) return
                val cur = _bookmarkedIds.value
                var next: MutableSet<Long>? = null
                for (w in payload.works) {
                    val want = if (forceBookmarked) true else w.isBookmarked ?: continue
                    val id = w.post.id
                    if (want && id !in cur) {
                        (next ?: cur.toMutableSet().also { next = it }).add(id)
                    } else if (!want && id in cur) {
                        (next ?: cur.toMutableSet().also { next = it }).remove(id)
                    }
                }
                next?.let { _bookmarkedIds.value = it }
            }
            is PixivWorkDetail -> syncBookmarkFlag(payload.post.id, payload.isBookmarked)
        }
    }

    private fun syncBookmarkFlag(id: Long, flag: Boolean?) {
        if (flag == null) return
        val cur = _bookmarkedIds.value
        if (flag && id !in cur) {
            _bookmarkedIds.value = cur + id
        } else if (!flag && id in cur) {
            _bookmarkedIds.value = cur - id
        }
    }

    private suspend fun <T> cachedImpl(
        key: String,
        ttlMs: Long,
        type: Type,
        channel: PixivApiChannel,
        priority: PixivApiPriority = PixivApiPriority.HIGH,
        bypass: Boolean = false,
        fetch: suspend () -> Result<T>,
    ): Result<T> {
        if (!bypass) {
            pixivCache.get<T>(key, ttlMs, type)?.let {
                syncPayloadBookmarks(it)
                return Result.success(it)
            }
            if (requestGuard.isInCooldown(channel)) {
                pixivCache.getStale<T>(key, type)?.let {
                    syncPayloadBookmarks(it)
                    return Result.success(it)
                }
            }
        }
        val flightKey = if (bypass) "fresh|$key" else key
        val res = requestGuard.execute(flightKey, channel, priority, fetch)
        val payload = res.getOrNull()
        if (payload != null) {
            pixivCache.put(key, payload)
            syncPayloadBookmarks(payload)
        } else if (res.isFailure && !bypass) {
            pixivCache.getStale<T>(key, type)?.let {
                syncPayloadBookmarks(it)
                return Result.success(it)
            }
        }
        return res
    }

    private suspend inline fun <reified T> cached(
        key: String,
        ttlMs: Long,
        channel: PixivApiChannel,
        priority: PixivApiPriority = PixivApiPriority.HIGH,
        bypass: Boolean = false,
        noinline fetch: suspend () -> Result<T>,
    ): Result<T> = cachedImpl(key, ttlMs, object : TypeToken<T>() {}.type, channel, priority, bypass, fetch)

    // ── 匿名腿（`www`，不要票据）──

    /** 列表类 TTL（关注/书签/作品页/搜索：30 分钟）。 */
    private val TTL_LIST = 30L * 60 * 1000

    /** 详情类 TTL（作品详情/画师资料/头像：6 小时）。 */
    private val TTL_DETAIL = 6L * 60 * 60 * 1000

    /** 关注动态 TTL（最新的那一档，15 分钟）。 */
    private val TTL_FEED = 15L * 60 * 1000

    /** 匿名腿 TTL（排行榜/标签页：1 小时）。 */
    private val TTL_ANON = 60L * 60 * 1000

    /**
     * 作品详情那颗口袋的键（读、写、收藏后失效三处共用一把尺）。
     *
     * `v2`：口袋多了一格 `caption`。旧键里躺着的是"没有简介"那一版答复，
     * 6 小时 TTL + 失败退旧档会让新加的简介被旧账一直压住（用户报「简介还是无法正常显示」）。
     * 以后再加格就升这枚版本号，别只改读的那一处。
     */
    private fun detailCacheKey(illustId: Long) = "workDetail|v2|$illustId"

    override suspend fun ranking(mode: PixivRankingMode, page: Int, fresh: Boolean) =
        cached("ranking|v2|$mode|$page", TTL_ANON, PixivApiChannel.WWW, bypass = fresh) {
            client.ranking(mode, page)
        }

    override suspend fun tagWorks(word: String, page: Int, filter: PixivSearchFilter) =
        cached("tagWorks|$word|$page", TTL_ANON, PixivApiChannel.WWW) {
            client.tagWorks(word, page, filter)
        }

    override suspend fun fetchById(id: Long) =
        cached("workById|$id", TTL_DETAIL, PixivApiChannel.WWW) {
            client.workById(id)
        }

    override suspend fun userHead(userId: Long) =
        cached("userHead|$userId", TTL_DETAIL, PixivApiChannel.WWW) {
            client.userHead(userId)
        }

    override suspend fun profileAll(userId: Long) =
        cached("profileAll|$userId", TTL_LIST, PixivApiChannel.WWW) {
            client.profileAll(userId)
        }

    override suspend fun worksByIds(
        userId: Long,
        ids: List<Long>,
        workCategory: String,
        firstPage: Boolean,
    ) = cached("worksByIds|$userId|$workCategory", TTL_LIST, PixivApiChannel.WWW) {
        client.worksByIds(userId, ids, workCategory, firstPage)
    }

    // ── 登录腿（`app-api`，要 Bearer）──

    override suspend fun searchIllust(
        bearer: String,
        word: String,
        filter: PixivSearchFilter,
        premium: Boolean,
        nextUrl: String?,
    ) = cached("searchIllust|$word|$premium|$nextUrl", TTL_LIST, PixivApiChannel.APP) {
        appClient.searchIllust(bearer, word, filter, premium, nextUrl)
    }

    override suspend fun popularPreview(bearer: String, word: String) =
        cached("popularPreview|$word", TTL_LIST, PixivApiChannel.APP) {
            appClient.popularPreview(bearer, word)
        }

    override suspend fun userPremium(bearer: String, userId: String) =
        requestGuard.execute("userPremium|$userId", PixivApiChannel.APP, PixivApiPriority.HIGH) {
            appClient.userPremium(bearer, userId)
        }

    override suspend fun searchUser(bearer: String, word: String, nextUrl: String?) =
        cached("searchUser|$word|$nextUrl", TTL_LIST, PixivApiChannel.APP) {
            appClient.searchUser(bearer, word, nextUrl)
        }

    /**
     * 作品页详情：有票据先走登录腿，答上了却读不出图时退匿名腿；登录腿失败时不退回，避免误判错误原因。
     */
    override suspend fun workDetail(illustId: Long, bearer: String?): Result<PixivWorkDetail?> {
        val cacheKey = detailCacheKey(illustId)
        val cachedHit = pixivCache.get<PixivWorkDetail>(cacheKey, TTL_DETAIL, tt<PixivWorkDetail>())
        if (cachedHit != null) {
            syncBookmarkFlag(illustId, cachedHit.isBookmarked)
            return Result.success(cachedHit)
        }
        val channel = if (bearer == null) PixivApiChannel.WWW else PixivApiChannel.APP
        if (requestGuard.isInCooldown(channel)) {
            pixivCache.getStale<PixivWorkDetail>(cacheKey, tt<PixivWorkDetail>())?.let {
                syncBookmarkFlag(illustId, it.isBookmarked)
                return Result.success(it)
            }
        }
        val res = requestGuard.execute(cacheKey, channel, PixivApiPriority.HIGH) {
            if (bearer == null) {
                client.workDetail(illustId)
            } else {
                val viaApp = appClient.illustDetail(bearer, illustId)
                if (viaApp.isSuccess && viaApp.getOrNull() == null) {
                    client.workDetail(illustId)
                } else {
                    viaApp
                }
            }
        }
        val payload = res.getOrNull()
        if (payload != null) {
            pixivCache.put(cacheKey, payload)
            syncBookmarkFlag(illustId, payload.isBookmarked)
        } else if (res.isFailure) {
            pixivCache.getStale<PixivWorkDetail>(cacheKey, tt<PixivWorkDetail>())?.let {
                syncBookmarkFlag(illustId, it.isBookmarked)
                return Result.success(it)
            }
        }
        return res
    }

    override suspend fun illustComments(bearer: String, illustId: Long, nextUrl: String?) =
        requestGuard.execute(
            "illustComments|$illustId|$nextUrl",
            PixivApiChannel.APP,
            PixivApiPriority.LOW,
        ) {
            appClient.illustComments(bearer, illustId, nextUrl)
        }

    /**
     * 发评论：**写口不缓存、不重试**（重发一次就是往人家作品下发两条）。
     * 走守卫只为和读口共用同一个冷却/优先级闸门 —— 被挡下时那枚 Result 带着原因回去。
     */
    override suspend fun commentAdd(
        bearer: String,
        illustId: Long,
        comment: String,
        stampId: Int?,
    ): Result<Unit> = requestGuard.execute(
        // 飞行键里带正文与贴纸：连点两下"发送"会被并成同一跳，不会往人家作品下发两条。
        "commentAdd|$illustId|$stampId|$comment",
        PixivApiChannel.APP,
        PixivApiPriority.HIGH,
    ) {
        appClient.addIllustComment(bearer, illustId, comment, stampId).map { }
    }

    /** 贴纸表：读口，跟详情一起按 6 小时 TTL 缓存（这份表是站方词表，不是用户数据）。 */
    override suspend fun stamps(bearer: String): Result<List<PixivStamp>> =
        cached("stamps", TTL_DETAIL, PixivApiChannel.APP, PixivApiPriority.LOW) {
            appClient.stamps(bearer)
        }

    override suspend fun recommended(bearer: String, nextUrl: String?, fresh: Boolean) =
        cached("recommended|$nextUrl", TTL_LIST, PixivApiChannel.APP, PixivApiPriority.LOW, bypass = fresh) {
            appClient.recommendedIllusts(bearer, nextUrl)
        }

    override suspend fun rankingInApp(mode: PixivRankingMode, bearer: String, nextUrl: String?) =
        cached("rankingInApp|$mode|$nextUrl", TTL_LIST, PixivApiChannel.APP) {
            appClient.rankingIllusts(mode, bearer, nextUrl)
        }

    override suspend fun following(
        bearer: String,
        userId: String,
        restrict: PixivRestrict,
        nextUrl: String?,
        fresh: Boolean,
    ) = incrementalStore.loadFollowing(
        userId = userId,
        restrict = restrict,
        nextUrl = nextUrl,
        ttlMs = TTL_LIST,
        fresh = fresh,
    ) { cursor ->
        requestGuard.execute(
            flightKey = "followingPage|$userId|$restrict|$cursor|$fresh",
            channel = PixivApiChannel.APP,
            priority = PixivApiPriority.HIGH,
        ) {
            appClient.following(bearer, userId, restrict, cursor)
        }
    }

    override suspend fun followIllustFeed(
        bearer: String,
        restrict: PixivRestrict,
        nextUrl: String?,
        fresh: Boolean,
    ) = cached("feed|$restrict|$nextUrl", TTL_FEED, PixivApiChannel.APP, bypass = fresh) {
        appClient.followIllustFeed(bearer, restrict, nextUrl)
    }

    override suspend fun recommendedUsers(bearer: String, nextUrl: String?) =
        cached("recommendedUsers|$nextUrl", TTL_DETAIL, PixivApiChannel.APP, PixivApiPriority.LOW) {
            appClient.recommendedUsers(bearer, nextUrl)
        }

    override suspend fun bookmarkedIllusts(
        bearer: String,
        userId: String,
        restrict: PixivRestrict,
        nextUrl: String?,
        fresh: Boolean,
    ) = incrementalStore.loadBookmarks(
        userId = userId,
        restrict = restrict,
        nextUrl = nextUrl,
        ttlMs = TTL_LIST,
        fresh = fresh,
    ) { cursor ->
        requestGuard.execute(
            flightKey = "bookmarkPage|$userId|$restrict|$cursor|$fresh",
            channel = PixivApiChannel.APP,
            priority = PixivApiPriority.HIGH,
        ) {
            appClient.bookmarkIllusts(bearer, userId, restrict, cursor)
        }
    }.onSuccess { syncPayloadBookmarks(it, forceBookmarked = true) }

    override suspend fun userIllusts(bearer: String, userId: String, type: String, nextUrl: String?, fresh: Boolean) =
        cached("userIllusts|$userId|$type|$nextUrl", TTL_LIST, PixivApiChannel.APP, bypass = fresh) {
            appClient.userIllusts(bearer, userId, type, nextUrl)
        }

    override suspend fun userDetail(bearer: String, userId: String) =
        cached("userDetail|$userId", TTL_DETAIL, PixivApiChannel.APP) {
            appClient.userDetail(bearer, userId)
        }

    override suspend fun followAdd(bearer: String, userId: String, private: Boolean): Result<Unit> {
        val res = requestGuard.execute("followAdd|$userId|$private", PixivApiChannel.APP, PixivApiPriority.HIGH) {
            appClient.followAdd(bearer, userId, private)
        }
        if (res.isSuccess) {
            incrementalStore.markStaleByPrefix("following|")
            pixivCache.invalidatePrefix("userIllusts|$userId|")
            pixivCache.invalidatePrefix("userDetail|$userId")
            pixivCache.invalidatePrefix("feed|")
        }
        return res
    }

    override suspend fun followRemove(bearer: String, userId: String): Result<Unit> {
        val res = requestGuard.execute("followRemove|$userId", PixivApiChannel.APP, PixivApiPriority.HIGH) {
            appClient.followRemove(bearer, userId)
        }
        if (res.isSuccess) {
            userId.toLongOrNull()?.let { incrementalStore.removeFollowing(it) }
                ?: incrementalStore.markStaleByPrefix("following|")
            pixivCache.invalidatePrefix("userIllusts|$userId|")
            pixivCache.invalidatePrefix("userDetail|$userId")
            pixivCache.invalidatePrefix("feed|")
        }
        return res
    }

    override suspend fun bookmarkAdd(bearer: String, illustId: Long, private: Boolean): Result<Unit> {
        val res = requestGuard.execute("bookmarkAdd|$illustId|$private", PixivApiChannel.APP, PixivApiPriority.HIGH) {
            appClient.bookmarkAdd(bearer, illustId, private)
        }
        if (res.isSuccess) {
            syncBookmarkFlag(illustId, true)
            incrementalStore.markStaleByPrefix("bookmarks|")
            pixivCache.invalidatePrefix("feed|")
            pixivCache.invalidatePrefix(detailCacheKey(illustId))
        }
        return res
    }

    override suspend fun bookmarkRemove(bearer: String, illustId: Long): Result<Unit> {
        val res = requestGuard.execute("bookmarkRemove|$illustId", PixivApiChannel.APP, PixivApiPriority.HIGH) {
            appClient.bookmarkRemove(bearer, illustId)
        }
        if (res.isSuccess) {
            syncBookmarkFlag(illustId, false)
            incrementalStore.removeBookmark(illustId)
            pixivCache.invalidatePrefix("feed|")
            pixivCache.invalidatePrefix(detailCacheKey(illustId))
        }
        return res
    }

    override suspend fun relatedIllusts(bearer: String, illustId: Long) =
        cached("related|$illustId", TTL_DETAIL, PixivApiChannel.APP, PixivApiPriority.LOW) {
            appClient.relatedIllusts(bearer, illustId)
        }

    override suspend fun fetchNextPage(bearer: String, nextUrl: String) =
        cached("next|$nextUrl", TTL_LIST, PixivApiChannel.APP) {
            appClient.fetchNextPage(bearer, nextUrl)
        }

    /**
     * 动图逐帧包：匿名 www 腿优先，取不到且已登录时退带凭据 app-api 腿；两腿均无帧表返回空成功，不抛网络异常。
     */
    override suspend fun ugoiraClip(illustId: Long, original: Boolean): Result<PixivUgoiraClip?> =
        runCatching {
            var meta = requestGuard.execute("ugoiraMeta|www|$illustId", PixivApiChannel.WWW, PixivApiPriority.HIGH) {
                client.ugoiraMeta(illustId)
            }.getOrNull()
            if (meta == null) {
                val bearer = bearerProvider()
                if (bearer != null) {
                    meta = requestGuard.execute("ugoiraMeta|app|$illustId", PixivApiChannel.APP, PixivApiPriority.HIGH) {
                        appClient.ugoiraMetadata(bearer, illustId)
                    }.getOrNull()
                }
            }
            if (meta == null) return@runCatching null
            PixivUgoiraStore.getInstance(handle.engine, handle.paths).clip(illustId, meta, original).getOrThrow()
        }
}

/**
 * 桌面侧的 pixiv 账号件（`PixivCredentials`）。
 *
 * 形状照 `data/android/PixivAccount.kt`，但**不吃 `Context`**：
 * - `store` = [DesktopStoreFactory.open]（`JsonKeyValueStore`，落在数据根里）；
 * - `engine` = [DesktopHttpEngine.okHttpClient]（`public override val`，现成）。
 *
 * 判据一条不写：全部转发给 [PixivCredentialState]（那颗是纯 JVM，`android.` import 为 0）。
 * 两端各写一份判据就是"界面说已登录而请求全 401"那一族。
 *
 * ## 存储名与 Android 同串，但**落在不同文件上**
 *
 * `PreferenceKeys.PREFS_PIXIV_ACCOUNT` = `venera_pixiv_account`，Android 是 SharedPreferences、
 * 桌面是 `prefs/venera_pixiv_account.json`。名字一致不等于数据共享（跨端迁移不在本轮范围）。
 */
class DesktopPixivAccount internal constructor(
    store: com.venera.compose.data.platform.KeyValueStore,
    engine: (() -> okhttp3.OkHttpClient)?,
) : com.venera.compose.gallery.data.PixivCredentials {

    private val state = com.venera.compose.gallery.data.PixivCredentialState(
        store = store,
        engine = engine,
    )

    override val authState: kotlinx.coroutines.flow.StateFlow<com.venera.compose.gallery.data.PixivAuthState> =
        state.authState

    override val isClientConfigured: Boolean get() = state.isClientConfigured

    override suspend fun bearerOrNull(): String? = state.bearerOrNull()

    override fun beginLogin(): com.venera.compose.gallery.data.PixivLoginChallenge? = state.beginLogin()

    override suspend fun completeLogin(
        redirectUrl: String,
    ): Result<com.venera.compose.gallery.data.PixivAuthState> = state.completeLogin(redirectUrl)

    override suspend fun signInWithPasted(
        text: String,
    ): Result<com.venera.compose.gallery.data.PixivAuthState> = state.signInWithPasted(text)

    override fun signOut() = state.signOut()

    /** 已登录那位的用户号（头像那一跳用）。null = 没登录，或站方答复里没有 `user`。 */
    val signedInUserId: Long?
        get() = (state.authState.value as? com.venera.compose.gallery.data.PixivAuthState.SignedIn)
            ?.userId?.toLongOrNull()
}
