package com.venera.desktop.gallery.ui

import com.venera.desktop.ui.theme.VeneraType
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.components.venera.VeneraCoverMask
import com.venera.compose.gallery.data.PixivRankingMode
import com.venera.compose.gallery.data.PixivUser
import com.venera.compose.gallery.domain.FollowedArtistRef
import com.venera.compose.gallery.domain.GalleryFollowedArtistsReadout
import com.venera.compose.gallery.domain.GalleryHomeDomain
import com.venera.compose.gallery.ui.PixivRankingModeChips
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.gallery.data.DesktopPixivisionArticle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 画廊首页 chrome 之下、那面墙之上的几节横栏（安卓大屏形态的第二块）。
 *
 * ##改形
 *
 * - **正在关注的画师**：从"裸横滑圆座"改成稿 `.artist-strip`（`:1206`）那一颗轻量容器
 *   —— 细边圆角框里摆一排小画师 + 右缘一句说明，头像降到 42dp（稿 `:616`），不抢占首屏作品面积。
 * - **每日热门**：从"跟主墙同列数的两排小瀑布"改成稿 `.carousel`（`:638-642`）那一根**横向滚带**
 *   —— 一屏约 `columnCount` 张、左右两枚圆钮逐张滚、底下一根进度条。`dailyColumnCount` 因此仍是真参数
 *   （它决定一屏摆几张），`scaffold` 那句 `dailyColumnCount = columns` 照旧成立。
 * - **Pixiv 那一支**：从"一排药丸 + 一行入口"改成稿 `.pixiv-dashboard`（`:961`）那两块并排的板
 *   —— 左 = 排行榜药丸（档名切那面墙的取数腿），右 = 我的 pixiv 账号板（点它进账号屏）。
 *
 * ## 判据都在外面（§142 那一族：屏不取数、壳取数）
 *
 * 这里只吃**成品**：卡已是守卫判过的（`desktopWallGuardedCards` 那颗唯一起口在壳侧的取数件里
 * 走过），画师读数已是共享判据算好的（`desktopFollowedArtistsRowReadout`），登录态由 scaffold
 * 从 `ports.pixivAccount.authState` 读一帧喂进来（屏不找静态口）。
 */
@Composable
internal fun DesktopGalleryHomeRows(
    domain: GalleryHomeDomain,
    /** BOORU 域：画师横栏的成品读数（`null` = 名单还在读，那一档什么都不摆——与首页同口径）。 */
    followedArtists: GalleryFollowedArtistsReadout?,
    onOpenArtist: (FollowedArtistRef) -> Unit,
    /** BOORU 域：每日热门那一排（已过守卫的成品卡；空表 = 这一节不摆）。 */
    dailyCards: List<DesktopWallCardModel>,
    /** 每日热门横向滚带**一屏摆几张**；与主墙同档（AUTO 随内容区变宽加张）。 */
    dailyColumnCount: Int,
    /** PIXIV 域：当前档名与点档（档 = 那面墙的取数参数，住壳）。**必传无默认值**。 */
    rankingMode: PixivRankingMode,
    onPickRankingMode: (PixivRankingMode) -> Unit,
    /** 那面墙的同一个打开回调：行里的卡点下去进同一扇看图门，不另开第二条路由。 */
    onOpenPost: (DesktopWallCardModel) -> Unit,
    /** PIXIV 域：进「我的 pixiv」（账号页；起桌面有这一屏与登录）。**必传无默认值**。 */
    onOpenAccount: () -> Unit,
    /**
     * BOORU 域：日榜那一排的「查看全部」落点（阶段二 · 域 2：安卓那颗日榜二级页屏已共享，
     * 桌面喂的是同一颗 `GalleryDailyScreenBody`）。**必传无默认值**（评审 R8 那一族）。
     */
    onOpenDaily: () -> Unit,
    /** PIXIV 域账号板的登录态读数（scaffold 从 `authState` 读一帧；只管措辞，路由恒开）。 */
    pixivSignedIn: Boolean,
    /**
     * PIXIV 域榜单预览条的卡（是**独立榜单腿**的成品，壳里按档取+缓存）。
     * 空表 = 还没到货，那一节整排不摆（与 BOORU 日榜同一口径）。
     */
    previewCards: List<DesktopWallCardModel>,
    /**
     * 预览条卡的画师头像 / 站方标题（主人口径「榜首这里的卡片应该和下面的一样
     * 带有头像和标题」）：独立榜单腿的 `avatars` / `titles` 两张表原样递进。空表 = 不画头像、
     * 标题退卡上那份（booru 日榜那一支空表，逐像素不变）。
     */
    previewAvatars: Map<String, String> = emptyMap(),
    previewTitles: Map<String, String> = emptyMap(),
    // ── ：发现带（人气标签 + PIXIV 域推荐用户）──
    /** 标签榜成品（壳从当轮墙账聚合，[desktopHotTagsOf]）；空表 = 这一节不摆。 */
    hotTags: List<DesktopHotTag> = emptyList(),
    /** 标签卡点下去 = 那串词交给画廊搜索（壳接交接门；与画师行同一扇门）。必传无默认值（回调台账那把尺）。 */
    onOpenTag: (String) -> Unit,
    /** PIXIV 域站方推荐用户；`null` = 未登录 / 在读 / 端口缺席 —— 用户段整段不摆。 */
    recommendedUsers: List<PixivUser>? = null,
    /** 本会话内点过「关注」的 pixiv 用户号（首页不拉全量关注名单，精确态住账号页）。 */
    followedUserIds: Set<Long> = emptySet(),
    onFollowUser: (PixivUser) -> Unit,
    onOpenUser: (PixivUser) -> Unit,
    // ── ：关注新作（PIXIV 域登录腿）──
    /** 关注流首页的成品卡（壳过守卫投影）；空表 = 未登录 / 没到货，这一节不摆。 */
    followingCards: List<DesktopWallCardModel> = emptyList(),
    followingAvatars: Map<String, String> = emptyMap(),
    // ── ：pixivision 最新文章（站方首页解析，匿名）──
    /** 文章成品（壳解析产出）；空表 = 没到货 / 站方改版，这一节不摆。 */
    pixivisionArticles: List<DesktopPixivisionArticle> = emptyList(),
    /** 文章卡点下去 = 进二级页（壳存文章 + 切路由；不再直接外链）。**必传无默认值**。 */
    onOpenPixivisionArticle: (DesktopPixivisionArticle) -> Unit,
) {
    val tokens = VeneraTokens
    when (domain) {
        GalleryHomeDomain.BOORU -> {
            DesktopGalleryArtistsRow(artists = followedArtists, onOpenArtist = onOpenArtist)
            DesktopGalleryDailyRow(
                cards = dailyCards,
                visibleCount = dailyColumnCount,
                title = "每日热门",
                enTag = "DAILY",
                onOpenPost = onOpenPost,
                onOpenDaily = onOpenDaily,
                headerEnd = null,
            )
        }
        GalleryHomeDomain.PIXIV -> {
            // 1. 顶部：通栏 Pixivision 特辑（主推大卡 + 右侧双叠小卡）
            DesktopPixivisionHeaderRow(
                articles = pixivisionArticles,
                onOpenArticle = onOpenPixivisionArticle,
            )
            // 2. 紧接：推荐画师横滑流（3 联竖图大作，支持双向关注状态切换）
            DesktopPixivRecommendedUsersRow(
                users = recommendedUsers,
                followedIds = followedUserIds,
                onFollowUser = onFollowUser,
                onOpenUser = onOpenUser,
            )
            if (followingCards.isNotEmpty()) {
                DesktopGalleryDailyRow(
                    cards = followingCards,
                    visibleCount = dailyColumnCount,
                    title = "关注新作",
                    enTag = "FOLLOWING",
                    onOpenPost = onOpenPost,
                    onOpenDaily = null,
                    avatars = followingAvatars,
                    headerEnd = null,
                )
            }
            // 排行榜横向滚带：五档切换器与名次角标
            DesktopGalleryDailyRow(
                cards = previewCards,
                visibleCount = dailyColumnCount,
                title = "排行榜",
                enTag = "RANKING",
                onOpenPost = onOpenPost,
                onOpenDaily = null,
                avatars = previewAvatars,
                titles = previewTitles,
                showRank = true,
                headerEnd = {
                    PixivRankingModeChips(
                        selected = rankingMode,
                        onPick = onPickRankingMode,
                        fill = false,
                    )
                },
            )
        }
    }
}

/**
 * 节头：与首页那批 `HomeTrackHeader` 同一形（中文题 + 灰英签 + 左缘对齐屏轴）。
 * `end` 那一格：节头右缘可挂一枚把手（日榜那节挂「查看全部」）；没有把手的节如实传 `null`。
 */
@Composable
private fun DesktopGallerySectionHeader(title: String, enTag: String, end: (@Composable () -> Unit)?) {
    val tokens = VeneraTokens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = tokens.spacing.screenHorizontal,
                end = tokens.spacing.screenHorizontal,
                top = tokens.spacing.space2,
                bottom = tokens.spacing.space2,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
    ) {
        Text(
            text = title,
            fontSize = VeneraType.cardTitle.size, lineHeight = VeneraType.cardTitle.lineHeight,
            fontWeight = tokens.type.weightBold,
            color = tokens.color.textPrimary,
        )
        Text(
            text = enTag,
            fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
            fontWeight = tokens.type.weightMedium,
            color = tokens.color.textTertiary,
        )
        if (end != null) {
            Spacer(Modifier.weight(1f))
            end()
        }
    }
}

/**
 * 正在关注的画师那一排（1206`：细边圆角容器里一排小画师 + 右缘说明）。
 *
 * 头像降到 42dp（稿 `:616`）、圆座；摆法从裸 `LazyRow` 改成**收在一颗框里**的 `Row`，
 * 与稿"头像横栏保持轻量、不抢占首屏作品面积"同口径（稿 `:1211` 那句原样搬成右缘说明）。
 */
@Composable
private fun DesktopGalleryArtistsRow(
    artists: GalleryFollowedArtistsReadout?,
    onOpenArtist: (FollowedArtistRef) -> Unit,
) {
    val tokens = VeneraTokens
    // `null` 那一档 = 名单还在读：什么都不摆（首页那栏对同一档念"画师名单还在读…"，
    // 而这一行紧挨那面墙，一句灰字插在墙头上比不摆更吵；读到了自然亮）。
    if (artists == null) return
    Column(Modifier.fillMaxWidth()) {
        DesktopGallerySectionHeader(title = "正在关注的画师", enTag = "ARTISTS", end = null)
        Spacer(Modifier.height(tokens.spacing.space2))
        val row = artists.artists
        val shape = RoundedCornerShape(tokens.shape.large)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = tokens.spacing.screenHorizontal)
                .clip(shape)
                .background(tokens.color.surfaceVariant.copy(alpha = tokens.current.placeholderAlpha))
                .border(DesktopGalleryMetrics.hairline, tokens.color.outlineVariant, shape)
                .padding(horizontal = tokens.spacing.space6, vertical = tokens.spacing.space4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space6),
        ) {
            if (row.isEmpty()) {
                // 空名单那句是**读数**不是引导：共享判据把"没关注过"与"没读到"分得开（`gap` 两档）。
                Text(
                    text = "还没有关注的画师：在画廊点开一张图，认他名下的标签就能关注。",
                    fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                    color = tokens.color.textTertiary,
                    modifier = Modifier.weight(1f),
                )
            } else {
                row.forEach { artist ->
                    Column(
                        modifier = Modifier
                            .width(64.dp)
                            .clickable { onOpenArtist(artist) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(tokens.spacing.space1),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(
                                    tokens.color.surfaceVariant.copy(alpha = tokens.current.placeholderAlpha),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (artist.previewUrl.isNotBlank()) {
                                VeneraCover(
                                    url = artist.previewUrl,
                                    contentDescription = artist.name,
                                    shimmerWhileLoading = false,
                                    preserveAspectRatio = false,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                Text(
                                    text = artist.name.take(1),
                                    fontSize = VeneraType.cardTitle.size, lineHeight = VeneraType.cardTitle.lineHeight,
                                    fontWeight = tokens.type.weightBold,
                                    color = tokens.color.textSecondary,
                                )
                            }
                        }
                        Text(
                            text = artist.name,
                            fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                            color = tokens.color.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                // 右缘说明（稿 `.artist-note` `:630`）：轻量、右对齐、两行。
                Text(
                    text = "头像横栏保持轻量\n不抢占首屏作品面积",
                    fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                    color = tokens.color.textTertiary,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * 横向预览滚带（638`）：一屏摆约 `visibleCount` 张，左右两枚圆钮逐张滚，
 * 底下一根进度条随滚动填。图库站「每日热门」与 Pixiv「榜单预览」**共用这一颗**（只是节头文案与
 * 有没有「查看全部」出口不同）。卡与那面墙共用同一颗 `DesktopWallCardModel`（打码卡不给点开，
 * `openable` 判据），`DesktopGalleryCardInfo` 那一颗信息栏一字不动。
 */
@Composable
private fun DesktopGalleryDailyRow(
    cards: List<DesktopWallCardModel>,
    /** 一屏摆几张（= 主墙列数，scaffold 递进来的 `columns`）。 */
    visibleCount: Int,
    title: String,
    enTag: String,
    onOpenPost: (DesktopWallCardModel) -> Unit,
    /**
     * 节头右缘那枚「查看全部」的落点（日榜：进安卓那颗 `GalleryDailyScreenBody` 二级页屏）。
     * `null` = 这一节没有二级页出口（Pixiv 榜单预览就是：切档由 dashboard 那排药丸负责，下面那面墙
     * 本就是全量），那一枚把手整格不摆 —— 摆一枚点了没反应的是本仓明令禁的假开关。
     */
    onOpenDaily: (() -> Unit)?,
    /** 卡的画师头像表（`uid → 地址`；预览条用）。空表 = 不画，与从前逐像素一致。 */
    avatars: Map<String, String> = emptyMap(),
    /** 卡的站方标题表（`uid → 原题`）。空表 = 标题退卡上那份。 */
    titles: Map<String, String> = emptyMap(),
    /**
     * 节头右缘、计数读数之前的**切换槽**（排行榜的五档分段长在这里 ——
     * 一处切换器管一条架，dashboard 那块独立板退役）。必传无默认值（回调台账那把尺）：
     * 不摆的节显式交 `null`。
     */
    headerEnd: (@Composable () -> Unit)?,
    /** 卡图左上角名次角标（v3 排行榜架）：`true` 时按横滑序号画 #N（榜单腿本就按名次到货）。 */
    showRank: Boolean = false,
) {
    val tokens = VeneraTokens
    if (cards.isEmpty()) return
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val gap = DesktopGalleryMetrics.wallGridGap
    Column(Modifier.fillMaxWidth()) {
        DesktopGallerySectionHeader(
            title = title,
            enTag = enTag,
            end = {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space3),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (headerEnd != null) {
                        // fill = false：chips 不吃满整行，把余量让给 Spacer 与右端读数
                        // （吃满会把「N 张」压成一字一行 ——主人截图指认的竖排）。
                        headerEnd()
                        Spacer(Modifier.weight(1f))
                    }
                    Text(
                        text = "${cards.size} 张",
                        fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                        color = tokens.color.textTertiary,
                        maxLines = 1,
                    )
                    if (onOpenDaily != null) {
                        com.venera.compose.components.venera.VeneraTextButton(text = "查看全部", onClick = onOpenDaily)
                    }
                }
            },
        )
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = tokens.spacing.screenHorizontal),
        ) {
            // 一屏 `visibleCount` 张 ⇒ 单张卡宽 =（可用宽 − (张数−1) 段间距）/ 张数。
            val perCardWidth: Dp = ((maxWidth - gap * (visibleCount - 1).coerceAtLeast(0)) /
                visibleCount.coerceAtLeast(1))
            LazyRow(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                itemsIndexed(cards) { index, card ->
                    DailyRowCard(
                        index = index,
                        card = card,
                        width = perCardWidth,
                        showRank = showRank,
                        titleOverride = card.sourcePost?.let { titles[it.uid] } ?: card.title,
                        avatarUrl = card.sourcePost?.let { avatars[it.uid] },
                        onOpenPost = onOpenPost,
                    )
                }
            }
            // 左右两枚圆钮（稿 `.carousel-btn` `:705`）：逐张滚。滚到头/尾不夹死（越界 `animateScrollToItem` 自收）。
            CarouselButton(
                glyph = "‹",
                modifier = Modifier.align(Alignment.CenterStart),
                onClick = {
                    scope.launch { listState.animateScrollToItem((listState.firstVisibleItemIndex - 1).coerceAtLeast(0)) }
                },
            )
            CarouselButton(
                glyph = "›",
                modifier = Modifier.align(Alignment.CenterEnd),
                onClick = {
                    scope.launch {
                        listState.animateScrollToItem((listState.firstVisibleItemIndex + 1).coerceAtMost(cards.lastIndex))
                    }
                },
            )
        }
        Spacer(Modifier.height(tokens.spacing.space2))
        // 进度条（稿 `.carousel-progress` `:723`）：已滚比例 = 首可见项 / (总数 − 一屏可见张数)。
        val scrollable = (cards.size - visibleCount).coerceAtLeast(1)
        val fraction = (listState.firstVisibleItemIndex.toFloat() / scrollable).coerceIn(0f, 1f)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = tokens.spacing.screenHorizontal)
                .height(2.dp)
                .background(
                    tokens.color.outlineVariant.copy(alpha = tokens.current.placeholderAlpha),
                    RoundedCornerShape(percent = 50),
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceAtLeast(0.12f))
                    .fillMaxHeight()
                    .background(tokens.color.primary, RoundedCornerShape(percent = 50)),
            )
        }
    }
}

/** 滚带左右那一枚圆钮（稿 `.carousel-btn`）。摆位与描边走现成 token，字形就是 ‹ › 两个字符。 */
@Composable
private fun CarouselButton(glyph: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val tokens = VeneraTokens
    Box(
        modifier = modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(tokens.color.surfaceContainerHigh)
            .border(DesktopGalleryMetrics.hairline, tokens.color.outlineVariant, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            fontSize = VeneraType.cardTitle.size, lineHeight = VeneraType.cardTitle.lineHeight,
            color = tokens.color.textPrimary,
        )
    }
}

/** 横向滚带单张卡片。 */
@Composable
private fun DailyRowCard(
    index: Int,
    card: DesktopWallCardModel,
    width: Dp,
    showRank: Boolean,
    titleOverride: String?,
    avatarUrl: String?,
    onOpenPost: (DesktopWallCardModel) -> Unit,
) {
    val tokens = VeneraTokens
    val coverUrl = card.sourcePost?.let { post ->
        if (post.isVideo) post.videoPosterUrl.ifBlank { post.previewUrl }
        else if (post.site == com.venera.compose.gallery.data.GallerySite.PIXIV) {
            post.previewUrl.ifBlank { post.largeUrl }.ifBlank { post.fileUrl }
        } else {
            post.previewUrl.ifBlank { post.largeUrl }.ifBlank { post.fastUrl }
        }
    }?.takeIf { it.isNotBlank() } ?: card.url

    val post = card.sourcePost
    val finalTitle = titleOverride ?: card.title

    // 按序梯队弹性入场动效（对齐漫画搜索规格）。
    val cardKey = card.uid.ifBlank { card.url }.ifBlank { card.title }
    val entrance = remember(cardKey) { Animatable(0f) }
    LaunchedEffect(cardKey) {
        delay(index.coerceAtMost(6) * 35L)
        entrance.animateTo(
            1f,
            spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        )
    }

    Column(
        modifier = Modifier
            .width(width)
            .graphicsLayer {
                alpha = entrance.value
                translationY = (1f - entrance.value) * 14f
                scaleX = 0.985f + 0.015f * entrance.value
                scaleY = scaleX
            }
            .then(com.venera.desktop.ui.components.desktopCardLiftModifier(RoundedCornerShape(9.dp)))
            .clickable(enabled = card.openable) { onOpenPost(card) },
    ) {
        Box {
            VeneraCover(
                url = coverUrl,
                contentDescription = card.title,
                mask = if (card.masked) VeneraCoverMask.Masked else VeneraCoverMask.Visible,
                shimmerWhileLoading = true,
                preserveAspectRatio = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(tokens.shape.medium)),
            )
            if (showRank) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(tokens.spacing.space2)
                        .clip(RoundedCornerShape(tokens.shape.small))
                        .background(tokens.color.surfaceContainerHigh.copy(alpha = 0.92f))
                        .border(
                            DesktopGalleryMetrics.hairline,
                            tokens.color.outlineVariant,
                            RoundedCornerShape(tokens.shape.small),
                        )
                        .padding(horizontal = tokens.spacing.space2, vertical = tokens.spacing.space1),
                ) {
                    Text(
                        text = "#${index + 1}",
                        fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                        fontWeight = tokens.type.weightBold,
                        color = tokens.color.textPrimary,
                    )
                }
            }
        }
        if (post != null) {
            DesktopGalleryCardInfo(
                post = post,
                title = finalTitle,
                avatarUrl = avatarUrl,
                modifier = Modifier.padding(top = tokens.spacing.space2),
                onClickArtist = null,
                trailingAction = null,
            )
        } else {
            DesktopGalleryCardInfo(
                card = card,
                titleOverride = finalTitle,
                avatarUrl = avatarUrl,
                modifier = Modifier.padding(top = tokens.spacing.space2),
                onClickArtist = null,
                trailingAction = null,
            )
        }
    }
}
