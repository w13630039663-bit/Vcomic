package com.venera.desktop.gallery.ui

import com.venera.desktop.ui.components.registerPixelBuddyCard

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.venera.desktop.gallery.ui.LocalDesktopSearchHandoff

/**
 * 子页作品墙卡片项（搜索 / 收藏 / 日榜）：**统一规格的薄委托**。
 *
 * 从前这颗自画 `VeneraCard + aspectRatio + 标题行`，有右键没浮动，与主墙等宽网格
 * 那颗自绘卡手感不一致。现在一律转交 [DesktopGalleryCard] —— 浮起、三态描边、胶囊动作坞、
 * 右键菜单、信息栏全走同一份，卡片规格不再随页面漂。这里只保留子页原有的
 * [LocalDesktopSearchHandoff] 交接门（Booru「搜画师」真口）转接。
 *
 * 安全口径不变：打码卡 `openable=false`，点不开（判据在统一卡内部那道闸）。
 */
@Composable
internal fun DesktopWallCard(
    card: DesktopWallCardModel,
    onOpenPost: (DesktopWallCardModel) -> Unit,
    /** 站方原题（pixiv 腿 `titles[uid]`；null/空 = 退站名 + 编号那一路）。 */
    titleOverride: String? = null,
    /** 画师头像地址（pixiv 腿 `avatars[uid]`；null = 走首字母座那条既有降级）。 */
    avatarUrl: String? = null,
    /**
     * 画师名覆盖（收件箱图库卡传关注标签名 —— moebooru/gelbooru 的 author 字段
     * 是上传者不是画师）；null = 走既有判据。
     */
    authorOverride: String? = null,
    /** 不摆站徽标（pixiv 卡在设计稿里无站源胶囊）；false = 保留。 */
    hideSourceBadge: Boolean = false,
    showInfo: Boolean = true,
    /** 多选态；`onToggleSelect == null` 时恒 false。 */
    selected: Boolean = false,
    /**
     * 切换多选。null = 这一屏没有多选（子页不摆勾选钮）。
     * 主墙与收藏墙各自由自己的选择账传入；无多选的屏显式写 null。
     */
    onToggleSelect: (() -> Unit)?,
    /** 点卡上画师名 → 壳接画师详情页路由；null = 回退浏览器（DesktopGalleryCard 既有语义）。 */
    onOpenArtist: ((Long, String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val searchHandoff = LocalDesktopSearchHandoff.current
    DesktopGalleryCard(
        card = card,
        // 子页作品墙按设置里的布局走（由 LocalDesktopWallSpec 定）。
        // 瀑布流恒用原图比例，不介入画幅档与行数档 —— 画幅档是等宽网格的裁切规格；
        // 等宽档下行数档优先（封面比例由行数定），rows 为空才回落到画幅档，最后按宽图判定。
        aspect = when {
            com.venera.desktop.gallery.ui.LocalDesktopWallSpec.current.layout ==
                com.venera.compose.gallery.domain.GalleryWallLayout.Staggered -> card.aspect
            else -> LocalDesktopWallSpec.current.rows?.let(
                com.venera.desktop.settings.DesktopGridDensity::rowsToRatio
            ) ?: LocalDesktopWallSpec.current.coverAspect.ratio ?: if (desktopGridCardIsWide(card)) 2f else 1f
        },
        onOpenPost = onOpenPost,
        onSearchArtist = { label ->
            searchHandoff(
                listOf(card.sourcePost?.site ?: com.venera.compose.gallery.data.GallerySite.YANDERE),
                label,
            )
        },
        titleOverride = titleOverride,
        avatarUrl = avatarUrl,
        authorOverride = authorOverride,
        hideSourceBadge = hideSourceBadge,
        // 账尺纪律：这两枚必填——子页没有那条路就显式写 null，漏传要看得见。
        // pixiv 卡从 source（users/{id}）提画师号进桌内详情页；提不出回退浏览器。
        onOpenArtist = onOpenArtist?.let { cb ->
            val artist = desktopPixivArtistTargetOf(card)
            if (artist != null) {
                { cb(artist.first, artist.second) }
            } else {
                null
            }
        },
        selected = selected,
        onToggleSelect = onToggleSelect,
        modifier = modifier.registerPixelBuddyCard("wall-${card.uid}"),
        showInfo = showInfo,
    )
}

/**
 * 提取 Pixiv 卡片对应的 `(userId, artistName)`；非 Pixiv 或缺失用户号时返回 `null`。
 */
internal fun desktopPixivArtistTargetOf(card: DesktopWallCardModel): Pair<Long, String>? {
    val post = card.sourcePost
    val isPixiv = post?.site == com.venera.compose.gallery.data.GallerySite.PIXIV ||
        card.url.contains("pixiv.net") ||
        (post != null && post.source.contains("pixiv.net"))
    if (!isPixiv) return null
    val artistId = post?.let {
        com.venera.compose.gallery.domain.GalleryArtistLinks.pixivUserId(it.source)
    } ?: com.venera.compose.gallery.domain.GalleryArtistLinks.pixivUserId(card.url)
        ?: return null
    val name = card.author.ifBlank { post?.author.orEmpty() }.trim()
        .ifBlank { card.title.ifBlank { "画师 #$artistId" } }
    return artistId to name
}

/**
 * 主墙卡片点击画师行时的分派：Pixiv 且能提取画师号时跳转应用内画师页，其余走画师搜索。
 */
internal fun desktopDispatchWallCardArtist(
    card: DesktopWallCardModel,
    onOpenPixivArtist: (com.venera.compose.gallery.data.PixivUser) -> Unit,
    onSearchArtist: (List<com.venera.compose.gallery.data.GallerySite>, String) -> Unit,
) {
    if (card.uid.isBlank()) return
    val pixivTarget = desktopPixivArtistTargetOf(card)
    if (pixivTarget != null) {
        onOpenPixivArtist(
            com.venera.compose.gallery.data.PixivUser(
                id = pixivTarget.first,
                name = pixivTarget.second,
                account = "",
                avatarUrl = "",
                muted = false,
            ),
        )
        return
    }
    val post = card.sourcePost
    val label = desktopExtractRealArtistTag(post, card.author.ifBlank { post?.author.orEmpty() })?.trim()
        .orEmpty()
        .ifBlank { post?.author?.trim().orEmpty() }
    if (label.isNotBlank()) {
        val sites = post?.site?.let(::listOf)
            ?: com.venera.compose.gallery.data.GallerySite.entries.filter {
                it != com.venera.compose.gallery.data.GallerySite.PIXIV
            }
        onSearchArtist(sites, label)
    }
}
