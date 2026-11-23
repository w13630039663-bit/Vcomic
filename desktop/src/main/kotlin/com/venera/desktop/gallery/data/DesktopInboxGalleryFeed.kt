package com.venera.desktop.gallery.data

import com.venera.compose.gallery.data.BoardSource
import com.venera.compose.gallery.data.GalleryBoards
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.data.GalleryTagLexicon
import com.venera.compose.gallery.domain.GalleryArtistFollow

/**
 * 图库收件箱的失败分账。**屏面状态映射只认 [code]**（403 → 图库限流，401 与其余 → 同步失败），
 * 不从 [cause] 文案事后抠 —— 那句文案是各站 client 的私有措辞，一改就破。
 */
internal data class GalleryInboxFailure(
    val kind: GalleryInboxFailureKind,
    val site: GallerySite,
    val name: String,
    val cause: Throwable,
    val code: Int?,
)

/** 失败两本账：[TAG_CHECK] = 这枚关注名确认不了画师身份；[FETCH] = 取数那一腿失败。 */
internal enum class GalleryInboxFailureKind { TAG_CHECK, FETCH }

/**
 * 一个关注标签的收件箱段（图库单源按标签各一段，不混排）。
 *
 * 新作/旧作的分界 = post id 是否超过落盘水位；分侧只在**取数当下**判一次，
 * 落盘水位在用户「全部已读」之前不推进。
 */
internal data class GalleryInboxBatch(
    val name: String,
    val site: GallerySite,
    val posts: List<GalleryPost>,
    val newPosts: List<GalleryPost>,
    val oldPosts: List<GalleryPost>,
)

/** 一次图库收件箱取数的全部产物：各标签段 + 失败分账。 */
internal data class DesktopInboxGallerySnapshot(
    val batches: List<GalleryInboxBatch>,
    val failures: List<GalleryInboxFailure>,
)

/**
 * 图库单源取数纯判据层（无 Composable，可被单测直接取值）。
 *
 * ## 判新与去重
 *
 * `post.id > 水位[uid]` = 新入库；水位落盘**只发生在用户「全部已读」时**经
 * [DesktopArtistInboxStore.markAllViewed] —— 同步绝不推水位，否则本轮取回的作品
 * 全被推成旧作、分界线上方新作区恒空。同标签按 `GalleryPost.uid`、跨标签也按它去重
 * （`站点routeKey:id`，跨站同号不会误并）。
 *
 * ## tag type 过滤
 *
 * 三条 moebooru/gelbooru 系站的 post JSON 都不带标签分类，防角色同名污染只能按
 * 「每个关注名一笔 tag 端点查询 + 会话内缓存」实现：全等（小写）复检并要求 `category == 1`
 * （数字命名空间 1=画师）。safebooru 跳过在线检查（Danbooru 系 post 自带
 * `tag_string_artist`，翻译进 author，无需过滤）。**只有「确认 category != 1」才缺席**；
 * 检查失败（在线查询失败、离线兜底 artistNames 空集也算失败——契约 Miss 与
 * 「词典没这名字」区分不了）不判缺席，记入 failures 分账 [GalleryInboxFailureKind.TAG_CHECK]，
 * 与取数失败 [GalleryInboxFailureKind.FETCH] 分开记账 —— 无凭据 gelbooru 的 searchTags
 * 也 401，必须以 failures 形态出声而非静默缺席。
 *
 * ## KONACHAN_R18 特例（真网络腿）
 *
 * 默认 board 解析器把该站指到 [DesktopKonachanR18Board]，它的 fetch 桥封在
 * `DesktopKonachanComClient` 里、无 engine 参可注入 —— **测试桩绕不过这条真网络腿**，
 * 所以测试名单不含该站。这不是留暗桩，是腿的形状本来如此。
 *
 * @param boards 真装配已带 Gelbooru 凭据的那颗（无凭据该站以 failures 出声，不兜空池）。
 * @param lexicon 离线兜底用：searchTags 失败时问 `artistNames`（画师档那一问）。
 * @param boardOf 站 → 板的解析器，默认按 [DesktopGalleryBoards] 那张映射表。
 */
internal class DesktopInboxGalleryFeed(
    private val boards: GalleryBoards,
    private val lexicon: GalleryTagLexicon,
    private val boardOf: (GallerySite) -> BoardSource? = { site -> defaultInboxBoardOf(boards, site) },
) {

    /** 会话内的 tag 判定缓存：键 = `站点routeKey:小写名`，避免重复查询。 */
    private val tagVerdicts = mutableMapOf<String, GalleryTagVerdict>()

    /**
     * 按关注名单逐标签取数。
     *
     * @param follows 图库关注名单（PIXIV 条目由调用方跳过；这里再挡一道，本层只认 booru 站）。
     * @param watermarks 当前落盘水位表（键 = [GalleryArtistFollow.uid]）。
     */
    suspend fun fetch(
        follows: List<GalleryArtistFollow>,
        watermarks: Map<String, Long>,
    ): DesktopInboxGallerySnapshot {
        val batches = mutableListOf<GalleryInboxBatch>()
        val failures = mutableListOf<GalleryInboxFailure>()
        val takenUids = HashSet<String>()
        for (follow in follows) {
            if (follow.site == GallerySite.PIXIV) continue
            when (val verdict = verdictOf(follow.site, follow.name)) {
                GalleryTagVerdict.NOT_ARTIST -> continue
                GalleryTagVerdict.CONFIRMED -> Unit
                GalleryTagVerdict.UNCONFIRMED -> {
                    failures += GalleryInboxFailure(
                        GalleryInboxFailureKind.TAG_CHECK,
                        follow.site,
                        follow.name,
                        IllegalStateException("tag 检查失败，确认不了画师身份"),
                        null,
                    )
                    continue
                }
            }
            val board = boardOf(follow.site)
            if (board == null) {
                failures += GalleryInboxFailure(
                    GalleryInboxFailureKind.FETCH,
                    follow.site,
                    follow.name,
                    IllegalStateException("该站收件箱未接线"),
                    null,
                )
                continue
            }
            val result = board.searchPosts(tags = follow.name, page = 1, limit = INBOX_FETCH_LIMIT)
            val posts = result.getOrElse { cause ->
                failures += GalleryInboxFailure(
                    GalleryInboxFailureKind.FETCH,
                    follow.site,
                    follow.name,
                    cause,
                    galleryInboxHttpCode(cause),
                )
                return@getOrElse null
            } ?: continue
            val taken = posts.filter { takenUids.add(it.uid) }
            // 整批被去重清空（或站方回空）不摆空段：空段只会长出「0 张」的噪声段头
            if (taken.isEmpty()) continue
            val watermark = watermarks[follow.uid] ?: 0L
            batches += GalleryInboxBatch(
                name = follow.name,
                site = follow.site,
                posts = taken,
                newPosts = taken.filter { it.id > watermark },
                oldPosts = taken.filter { it.id <= watermark },
            )
        }
        return DesktopInboxGallerySnapshot(batches = batches, failures = failures)
    }

    /** 单名判定：先问缓存，未缓存才发查询（safebooru 一律直过，不发 tag 查询）。 */
    private suspend fun verdictOf(site: GallerySite, name: String): GalleryTagVerdict {
        val key = "${site.routeKey}:${name.trim().lowercase()}"
        tagVerdicts[key]?.let { return it }
        val verdict = checkTag(site, name)
        tagVerdicts[key] = verdict
        return verdict
    }

    private suspend fun checkTag(site: GallerySite, name: String): GalleryTagVerdict {
        // safebooru 是 Danbooru 系，post 自带画师标签，无需也无从按 tag 端点过滤。
        if (site == GallerySite.SAFEBOORU) return GalleryTagVerdict.CONFIRMED
        val board = boardOf(site)
        if (board == null) return GalleryTagVerdict.UNCONFIRMED
        val exact = board.searchTags(name).getOrNull()
            ?.firstOrNull { it.name.trim().lowercase() == name.trim().lowercase() }
        if (exact != null) {
            return if (exact.category == CATEGORY_ARTIST) {
                GalleryTagVerdict.CONFIRMED
            } else {
                // 角色同名等：确认不是画师标签，缺席（不计失败）。
                GalleryTagVerdict.NOT_ARTIST
            }
        }
        // tag 端点没给全等命中 = 确认不了（yande.re 是前缀写法、gelbooru 是 LIKE 前缀，
        // 全等命中缺席才可信）。离线兜底问画师档；空集与兜底本身抛都算失败——契约 Miss
        // 交空集，区分不了「词典没这名字」与「词典没读出来」。
        val offline = runCatching { lexicon.artistNames(listOf(name)) }.getOrDefault(emptySet())
        return if (name.trim().lowercase() in offline.map { it.lowercase() }) {
            GalleryTagVerdict.CONFIRMED
        } else {
            GalleryTagVerdict.UNCONFIRMED
        }
    }

    /** 只进收件箱第一屏的条数（P0 口径，站方天花板远高于此）。 */
    private companion object {
        const val INBOX_FETCH_LIMIT = 20
        const val CATEGORY_ARTIST = 1
    }
}

/** 关注名经 tag 检查后的三种结论。 */
private enum class GalleryTagVerdict { CONFIRMED, NOT_ARTIST, UNCONFIRMED }

/**
 * 默认 board 解析：五条常规腿走 [GalleryBoards] 那张映射表，
 * konachan.com（R18）走 [DesktopKonachanR18Board] —— 那是**真网络腿**（见类注释）。
 * 未接线的站交 `null`，由取数层以 failures 出声。
 */
private fun defaultInboxBoardOf(boards: GalleryBoards, site: GallerySite): BoardSource? = when (site) {
    GallerySite.YANDERE -> boards.yandere
    GallerySite.GELBOORU -> boards.gelbooru
    GallerySite.SAFEBOORU -> boards.safebooru
    GallerySite.KONACHAN -> boards.konachan
    GallerySite.TBIB -> boards.tbib
    GallerySite.KONACHAN_R18 -> DesktopKonachanR18Board()
    GallerySite.PIXIV -> null
}

/**
 * 从各站 client 的失败文案（统一形如 `… 返回 <code>`）里收 HTTP code。
 * 一处收：client 文案变动只破这一处，且此判据有单测钉着。
 */
internal fun galleryInboxHttpCode(cause: Throwable): Int? =
    Regex("返回 (\\d+)").find(cause.message ?: "")?.groupValues?.get(1)?.toIntOrNull()
