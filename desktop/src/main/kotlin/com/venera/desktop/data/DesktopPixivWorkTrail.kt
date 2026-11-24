package com.venera.desktop.data

import com.venera.desktop.app.DesktopRoute
import com.venera.compose.gallery.data.GallerySite
import com.venera.desktop.gallery.ui.DesktopWallCardModel

/**
 * 作品页左右箭头（上一作 / 下一作）要翻的那一列。
 *
 * 作品页的路由只带**一篇**作品 —— 它不知道"用户是从哪面墙、哪一页结果里点进来的"。
 * 所以由**列表侧**在落屏时把当前这一列按屏上顺序记进这份账，壳在开作品页时按作品号
 * 在这列里找前后各一篇，装进路由带走（`DesktopRoute.PixivWork.prevWork/nextWork`）。
 *
 * 找不到（从下载记录、相关作品、珍藏册搜索这类没有"列"的口进来）= 不摆箭头 ——
 * 不摆一对点了没反应的钮，也不替用户编一个顺序。
 *
 * 为什么是进程级对象而不是组合状态：壳的组合捕获面有槽位预算（`DesktopShellStateCaptureTest`），
 * 且这份账只在**点击那一刻**被读一次，不该牵动任何重组。写只在主线程（各屏的 `LaunchedEffect`）。
 *
 * 打码卡不入账：箭头若能把人直接送进一张被打码的卡，那就是绕过 R-18 守卫的第二扇门。
 */
internal object DesktopPixivWorkTrail {

    /** 账本里的一格：进路由只需要这三样，正文 / 分页 / 标签仍由作品页进场现问。 */
    class Entry(val illustId: Long, val title: String, val coverUrl: String)

    private var entries: List<Entry> = emptyList()

    /** 用一列现成的作品卡记账（列表屏自己已经转好投影的那一档）。 */
    fun publish(list: List<Entry>) {
        entries = list
    }

    /**
     * 卡片墙（主页 / 搜索 / 收藏 / 每日热门 / 来源墙共有那一颗渲染件）记账。
     *
     * `uid` 为空串的卡点不开（见 [DesktopWallCardModel.uid]），不进账；打码卡也不进。
     */
    fun publishWallCards(cards: List<DesktopWallCardModel>) {
        publish(
            cards.mapNotNull { card ->
                if (card.uid.isBlank() || card.masked) return@mapNotNull null
                if (GallerySite.fromRouteKey(card.uid.substringBefore(':')) != GallerySite.PIXIV) {
                    return@mapNotNull null
                }
                val id = card.uid.substringAfter(':', "").toLongOrNull() ?: return@mapNotNull null
                Entry(id, card.author.ifBlank { "pixiv #$id" }, card.url)
            },
        )
    }

    /** pixiv 作品墙（账号页三段 / 画师页共用的那一颗）记账：打码与点不开的卡不进账。 */
    fun publishPixivWallCards(cards: List<com.venera.desktop.pixiv.DesktopPixivWallCard>) {
        publish(
            cards.mapNotNull { card ->
                if (card.masked || !card.openable) return@mapNotNull null
                val id = card.post.id
                Entry(id, card.author.ifBlank { "pixiv #$id" }, card.url)
            },
        )
    }

    /** 珍藏册详情屏记账（屏上顺序 = 首行横幅 + 逐行）。 */
    fun publishCollectionCards(cards: List<com.venera.compose.gallery.data.PixivWorkCard>) {
        publish(
            cards.map { card ->
                Entry(
                    card.id,
                    card.title.ifBlank { "pixiv #${card.id}" },
                    card.url ?: card.mediumUrl ?: card.largeUrl.orEmpty(),
                )
            },
        )
    }

    /** 当前作品在这一列里的前后各一篇；不在列里 = null（不摆箭头）。 */
    fun around(illustId: Long): Pair<Entry?, Entry?>? {
        val index = entries.indexOfFirst { it.illustId == illustId }
        if (index < 0) return null
        return entries.getOrNull(index - 1) to entries.getOrNull(index + 1)
    }

    /** 清账：离开"有列的浏览"时（下载记录 / 相关作品那一类入口）调用，免得旧列冒充新列。 */
    fun clear() {
        entries = emptyList()
    }
}
