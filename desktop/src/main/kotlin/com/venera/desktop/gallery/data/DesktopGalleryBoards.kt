package com.venera.desktop.gallery.data

import com.venera.compose.gallery.data.GalleryBoards
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GalleryTagSuggestion
import com.venera.compose.gallery.data.GelbooruClient
import com.venera.compose.gallery.data.GelbooruCredentials
import com.venera.compose.gallery.data.SafebooruClient
import com.venera.compose.gallery.data.ScoredBoard
import com.venera.compose.gallery.data.YandeReBoard
import com.venera.compose.gallery.data.YandeReClient

/**
 * 桌面侧的三站墙。三站都是真网络，取数面与 `android/GalleryAdaptersAndroid.kt` 里那三颗
 * 逐枚对齐（成员名、默认值、分页口径都不重新发明）。
 *
 * ⚠️ [GelbooruBoard] 与 [SafebooruBoard] 的差别只在凭据：Gelbooru 的 DAPI **匿名一律 401**
 * （实测，写在那颗 client 的类注释里），所以它必须拿到 [DesktopGalleryCredentials] 那枚
 * `GelbooruCredentials`；没配就是每轮都失败，而那笔失败会顺着 `GalleryDailyFeed` 的
 * `failures` 被界面说出来 —— 不许在这里替它兜成空池。
 */
class DesktopGalleryBoards(
    private val handle: DesktopGalleryHandle,
    private val credentials: GelbooruCredentials,
) : GalleryBoards {

    init {
        DesktopKonachanComClient.installEngine(handle.engine)
    }

    override val yandere: YandeReBoard by lazy { YandeReBoardImpl(handle) }
    override val gelbooru: ScoredBoard by lazy { GelbooruBoard(handle, credentials) }
    override val safebooru: ScoredBoard by lazy { SafebooruBoard(handle) }
    // `safebooru.donmai.us` 整站 CF 挑战（403）时，这两条腿匿名直连 200，
    // 作可用替代。两颗客户端都是桌面自持，安卓侧那颗装配器一行没动。
    override val konachan: ScoredBoard by lazy { KonachanBoard(handle) }
    override val tbib: ScoredBoard by lazy { TbibBoard(handle) }
}

private class YandeReBoardImpl(private val handle: DesktopGalleryHandle) : YandeReBoard {

    private val client: YandeReClient get() = YandeReClient.getInstance(handle.engine)

    override val pageSize: Int get() = YandeReClient.SEARCH_PAGE_SIZE
    override suspend fun searchPosts(tags: String, page: Int, limit: Int) = client.searchPosts(tags, page, limit)
    override suspend fun searchTags(term: String): Result<List<GalleryTagSuggestion>> = client.searchTags(term)
    override suspend fun fetchById(id: Long): Result<GalleryPost?> = client.fetchById(id)
    override suspend fun artistLinks(name: String) = client.artistLinks(name)
    override suspend fun resolveArtistAlias(name: String) = client.resolveArtistAlias(name)
    override suspend fun fetchDailyPopular() = client.fetchDailyPopular()
}

private class GelbooruBoard(
    private val handle: DesktopGalleryHandle,
    private val credentials: GelbooruCredentials,
) : ScoredBoard {

    private val client: GelbooruClient get() = GelbooruClient.getInstance(handle.engine, credentials)

    override val pageSize: Int get() = GelbooruClient.POOL_SIZE
    override suspend fun searchPosts(tags: String, page: Int, limit: Int) = client.searchPosts(tags, page, limit)
    override suspend fun searchTags(term: String): Result<List<GalleryTagSuggestion>> = client.searchTags(term)
    override suspend fun fetchById(id: Long): Result<GalleryPost?> = client.fetchById(id)
    override suspend fun fetchTopScored() = client.fetchTopScored()
}

private class SafebooruBoard(private val handle: DesktopGalleryHandle) : ScoredBoard {

    // posts 三腿优先走 danbooru 主站，直连超时/失败时自动切换到 Safebooru 原站 + DesktopKonachanBridge（WebView2 页内取回，
    // 避开浏览器 TLS 指纹校验）；且一旦切到桥接回退，本次会话直接走桥不再重复等 danbooru 10s 超时。
    private val posts = DesktopSafebooruDanbooruClient.getInstance(handle.engine)
    private val safebooruFallback = SafebooruClient.getInstance(handle.engine)

    @Volatile
    private var preferBridge: Boolean = false

    override val pageSize: Int get() = DesktopSafebooruDanbooruClient.POOL_SIZE

    override suspend fun searchPosts(tags: String, page: Int, limit: Int): Result<List<GalleryPost>> {
        if (preferBridge) return fetchViaBridge(tags, page, limit)
        return posts.searchPosts(tags, page, limit).recoverCatching {
            preferBridge = true
            fetchViaBridge(tags, page, limit).getOrThrow()
        }
    }

    override suspend fun searchTags(term: String): Result<List<GalleryTagSuggestion>> = safebooruFallback.searchTags(term)

    override suspend fun fetchById(id: Long): Result<GalleryPost?> {
        if (preferBridge) return fetchByIdViaBridge(id)
        return posts.fetchById(id).recoverCatching {
            preferBridge = true
            fetchByIdViaBridge(id).getOrThrow()
        }
    }

    override suspend fun fetchTopScored() =
        posts.fetchTopScored().recoverCatching { safebooruFallback.fetchTopScored().getOrThrow() }

    private suspend fun fetchViaBridge(tags: String, page: Int, limit: Int): Result<List<GalleryPost>> = runCatching {
        val enc = java.net.URLEncoder.encode(tags.trim(), "UTF-8")
        val body = DesktopKonachanBridge.fetchJson("https://safebooru.donmai.us/posts.json?tags=$enc&limit=$limit&page=$page")
        SafebooruClient.parsePostsJson(body)
    }

    private suspend fun fetchByIdViaBridge(id: Long): Result<GalleryPost?> = runCatching {
        val body = DesktopKonachanBridge.fetchJson("https://safebooru.donmai.us/posts.json?tags=id%3A$id")
        SafebooruClient.parsePostsJson(body).firstOrNull { it.id == id }
    }
}

/**
 * Konachan 没有高分池端点（与 yande.re 同族），`fetchTopScored` 用 `order:score` 元标签模拟 ——
 * 本机实测 `tags=order:score` 真换序。空池当失败，理由同 [SafebooruBoard] 那条口径。
 */
private class KonachanBoard(private val handle: DesktopGalleryHandle) : ScoredBoard {

    private val client: DesktopKonachanClient get() = DesktopKonachanClient.getInstance(handle.engine)

    override val pageSize: Int get() = DesktopKonachanClient.POOL_SIZE
    override suspend fun searchPosts(tags: String, page: Int, limit: Int) = client.searchPosts(tags, page, limit)
    override suspend fun searchTags(term: String): Result<List<GalleryTagSuggestion>> = client.searchTags(term)
    override suspend fun fetchById(id: Long): Result<GalleryPost?> = client.fetchById(id)
    override suspend fun fetchTopScored() = client.searchPosts("order:score", page = 1, allowInteractiveBypass = false)
}

private class TbibBoard(private val handle: DesktopGalleryHandle) : ScoredBoard {

    private val client: DesktopTbibClient get() = DesktopTbibClient.getInstance(handle.engine)

    override val pageSize: Int get() = DesktopTbibClient.POOL_SIZE
    override suspend fun searchPosts(tags: String, page: Int, limit: Int) = client.searchPosts(tags, page, limit)
    override suspend fun searchTags(term: String): Result<List<GalleryTagSuggestion>> = client.searchTags(term)
    override suspend fun fetchById(id: Long): Result<GalleryPost?> = client.fetchById(id)
    override suspend fun fetchTopScored() = client.fetchTopScored()
}

/**
 * konachan.com（R18 库）那颗板：**只进来源轴单选档**（`DESKTOP_ALL` 不圈它，见
 * `GallerySearchSource` 那条注），JSON 走桥、图直连。没有高分池端点，同族用
 * `order:score` 元标签模拟。
 */
internal class DesktopKonachanR18Board(
    private val engineOverride: com.venera.compose.data.platform.HttpEngine? = null,
) : ScoredBoard {

    private val client: DesktopKonachanComClient get() = DesktopKonachanComClient.getInstance(engineOverride)

    override val pageSize: Int get() = DesktopKonachanComClient.POOL_SIZE
    override suspend fun searchPosts(tags: String, page: Int, limit: Int) = client.searchPosts(tags, page, limit)
    override suspend fun searchTags(term: String): Result<List<GalleryTagSuggestion>> = client.searchTags(term)
    override suspend fun fetchById(id: Long): Result<GalleryPost?> = client.fetchById(id)
    override suspend fun fetchTopScored() = client.searchPosts("order:score", page = 1)
}
