package com.venera.desktop.gallery.ui

import com.venera.desktop.ui.theme.VeneraType
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.components.venera.VeneraCoverMask
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.PixivUser
import com.venera.compose.ui.tokens.VeneraTokens

/**
 * 首页「人气标签」那一格的成品（v3 发现带）。
 *
 * 壳从**当轮已过守卫的墙账**里聚合（[desktopHotTagsOf]），屏不取数：
 * - [count] = 这一面墙里带它的张数 —— 是屏上真数得出的读数，不冒充站方热度；
 * - [coverUrl] = 带它的第一张卡的预览地址，空串 = 没拿到（卡退纯面板底，不替站方编图）。
 */
internal data class DesktopHotTag(
    val name: String,
    val count: Int,
    val coverUrl: String,
)

/**
 * 标签榜聚合（纯件，判据可扫）：对当轮墙卡按 `tagList` 计数，取前 [limit] 枚。
 *
 * - 计数同数时按名字排序，聚合结果与墙的来序无关（同一墙同一榜）；
 * - 代表图取**计数序里第一张带它的卡** —— 榜单排完之后再补图，榜前排的图就是卡的封面。
 */
internal fun desktopHotTagsOf(posts: List<GalleryPost>, limit: Int = 12): List<DesktopHotTag> {
    if (posts.isEmpty()) return emptyList()
    val counts = linkedMapOf<String, Int>()
    val covers = mutableMapOf<String, String>()
    for (post in posts) {
        val cover = post.previewUrl.ifBlank { post.largeUrl }.ifBlank { post.fastUrl }
        for (raw in post.tagList) {
            val tag = raw.trim()
            if (tag.isEmpty()) continue
            counts[tag] = (counts[tag] ?: 0) + 1
            if (!covers.containsKey(tag) && cover.isNotBlank()) covers[tag] = cover
        }
    }
    return counts.entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .take(limit)
        .map { (name, count) -> DesktopHotTag(name, count, covers[name].orEmpty()) }
}

/**
 * v3 「发现带」：人气标签（长图卡横滑）+ PIXIV 域推荐用户（单行）。
 *
 * - 标签卡点下去 = 把那串词交给画廊搜索（交接门由壳递进 [onOpenTag]，与画师行同一扇门）；
 * - 推荐用户段只在 **PIXIV 域且登录**时摆（[users] 非空）；站方 `/v1/user/recommended`
 *   这一腿不带作品拼图，卡上**只有头像与名字** —— 拿不到的不画，不替站方编三张缩略；
 * - 关注钮的当前态由壳递进（[followedIds] 是**本会话内**点过的账：首页不拉全量关注名单，
 *   精确的已关注态住在账号页那一屏，这里不知道就说不知道，钮恒给「关注」）。
 */
@Composable
internal fun DesktopGalleryDiscoverRow(
    hotTags: List<DesktopHotTag>,
    onOpenTag: (String) -> Unit,
    /** PIXIV 域站方推荐用户（`null` = 未登录 / 还在读 / 端口缺席 —— 用户段整段不摆）。 */
    users: List<PixivUser>?,
    followedIds: Set<Long>,
    onFollowUser: (PixivUser) -> Unit,
    onOpenUser: (PixivUser) -> Unit,
) {
    val hasTags = hotTags.isNotEmpty()
    val hasUsers = users != null && users.isNotEmpty()
    if (!hasTags && !hasUsers) return
    val tokens = VeneraTokens
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = tokens.spacing.screenHorizontal),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(GalleryTokens.Spacing.WallCardGap),
    ) {
        if (hasTags) {
            val tagsState = androidx.compose.foundation.lazy.rememberLazyListState()
            val canScrollBack by androidx.compose.runtime.remember {
                androidx.compose.runtime.derivedStateOf { tagsState.firstVisibleItemIndex > 0 || tagsState.firstVisibleItemScrollOffset > 0 }
            }
            val canScrollForward by androidx.compose.runtime.remember {
                androidx.compose.runtime.derivedStateOf {
                    val info = tagsState.layoutInfo
                    val total = info.totalItemsCount
                    if (total == 0) false else {
                        val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                        lastVisible < total - 1
                    }
                }
            }
            Column(Modifier.weight(1.1f)) {
                DiscoverHead(title = "人气标签", enTag = "TAGS")
                Spacer(Modifier.height(tokens.spacing.space2))
                Box(modifier = Modifier.fillMaxWidth().height(GalleryTokens.Component.HotTagCardHeight)) {
                    LazyRow(
                        state = tagsState,
                        horizontalArrangement = Arrangement.spacedBy(GalleryTokens.Spacing.Space12),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(hotTags, key = { it.name }) { tag ->
                            HotTagCard(
                                tag = tag,
                                onOpen = { onOpenTag(tag.name) },
                            )
                        }
                    }
                    if (canScrollBack) {
                        RailArrowButton(
                            arrow = "‹",
                            modifier = Modifier.align(Alignment.CenterStart),
                            onClick = {
                                scope.launch {
                                    tagsState.animateScrollBy(-300f)
                                }
                            },
                        )
                    }
                    if (canScrollForward) {
                        RailArrowButton(
                            arrow = "›",
                            modifier = Modifier.align(Alignment.CenterEnd),
                            onClick = {
                                scope.launch {
                                    tagsState.animateScrollBy(300f)
                                }
                            },
                        )
                    }
                }
            }
        }
        if (hasTags && hasUsers) {
            Box(
                Modifier
                    .width(1.dp)
                    .height(GalleryTokens.Component.HotTagCardHeight + 36.dp)
                    .background(tokens.color.outlineVariant),
            )
        }
        if (hasUsers) {
            val usersState = androidx.compose.foundation.lazy.rememberLazyListState()
            val canScrollBack by androidx.compose.runtime.remember {
                androidx.compose.runtime.derivedStateOf { usersState.firstVisibleItemIndex > 0 || usersState.firstVisibleItemScrollOffset > 0 }
            }
            val canScrollForward by androidx.compose.runtime.remember {
                androidx.compose.runtime.derivedStateOf {
                    val info = usersState.layoutInfo
                    val total = info.totalItemsCount
                    if (total == 0) false else {
                        val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                        lastVisible < total - 1
                    }
                }
            }
            Column(Modifier.weight(1.2f)) {
                DiscoverHead(title = "推荐画师", enTag = "USERS")
                Spacer(Modifier.height(tokens.spacing.space2))
                Box(modifier = Modifier.fillMaxWidth().height(GalleryTokens.Component.RecommendedUserCardHeight)) {
                    LazyRow(
                        state = usersState,
                        horizontalArrangement = Arrangement.spacedBy(GalleryTokens.Spacing.Space12),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(users.toList(), key = { it.id }) { user ->
                            RecommendedUserCard(
                                user = user,
                                followed = user.id in followedIds,
                                onFollow = { onFollowUser(user) },
                                onOpen = { onOpenUser(user) },
                            )
                        }
                    }
                    if (canScrollBack) {
                        RailArrowButton(
                            arrow = "‹",
                            modifier = Modifier.align(Alignment.CenterStart),
                            onClick = {
                                scope.launch {
                                    usersState.animateScrollBy(-300f)
                                }
                            },
                        )
                    }
                    if (canScrollForward) {
                        RailArrowButton(
                            arrow = "›",
                            modifier = Modifier.align(Alignment.CenterEnd),
                            onClick = {
                                scope.launch {
                                    usersState.animateScrollBy(300f)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RailArrowButton(
    arrow: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val tokens = VeneraTokens
    Box(
        modifier = modifier
            .padding(horizontal = 4.dp)
            .size(GalleryTokens.Component.RailArrowButtonSize)
            .clip(CircleShape)
            .background(Color(0xD9161B26))
            .border(1.dp, Color(0x29FFFFFF), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = arrow,
            color = Color.White,
            fontSize = VeneraType.body.size, lineHeight = VeneraType.body.lineHeight,
            fontWeight = VeneraType.cardTitle.weight,
        )
    }
}

@Composable
private fun DiscoverHead(title: String, enTag: String) {
    val tokens = VeneraTokens
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            fontSize = VeneraType.cardTitle.size, lineHeight = VeneraType.cardTitle.lineHeight,
            fontWeight = tokens.type.weightBold,
            color = tokens.color.textPrimary,
        )
        Spacer(Modifier.width(tokens.spacing.space2))
        Text(
            text = enTag,
            fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
            fontWeight = tokens.type.weightMedium,
            color = tokens.color.textTertiary,
        )
    }
}

/** 人气标签长图卡（设计规格：136dp 宽 × 224dp 高，官方专属封面铺满，黄绿圆上升徽标 + 双层暗色渐变）。 */
@Composable
private fun HotTagCard(
    tag: DesktopHotTag,
    onOpen: () -> Unit,
) {
    val tokens = VeneraTokens
    val shape = GalleryTokens.Shape.Large
    Box(
        modifier = Modifier
            .width(GalleryTokens.Component.HotTagCardWidth)
            .height(GalleryTokens.Component.HotTagCardHeight)
            // 可点卡统一「划过上跳」。
            .then(com.venera.desktop.ui.components.desktopCardLiftModifier(shape))
            .clip(shape)
            .background(GalleryTokens.Color.SurfaceLevel2)
            .border(DesktopGalleryMetrics.hairline, tokens.color.outlineVariant, shape)
            .clickable(onClick = onOpen),
    ) {
        if (tag.coverUrl.isNotBlank()) {
            VeneraCover(
                url = tag.coverUrl,
                contentDescription = tag.name,
                shimmerWhileLoading = true,
                preserveAspectRatio = false,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // 官方样式：黄绿圆形上升徽标（无数字，站方卡上不念排名）
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .size(28.dp)
                .clip(CircleShape)
                .background(GalleryTokens.Color.StatusSuccess),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "↑",
                fontSize = VeneraType.cardTitle.size, lineHeight = VeneraType.cardTitle.lineHeight,
                fontWeight = VeneraType.cardTitle.weight,
                color = Color.White,
            )
        }
        // 底部双层渐变：保证白字在任何封面上都清晰可读
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(100.dp)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.5f to Color.Black.copy(alpha = 0.5f),
                        1f to Color.Black.copy(alpha = 0.9f),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            // 官方双行：主标签名（罗马字/原名）+ #日文标签。第二段取官方 translated_name，
            // 没有译名就只摆一行 —— 不编数字、不拿墙卡计数冒充。
            Text(
                text = tag.name.substringBefore(" ("),
                style = GalleryTokens.Typography.CardTitle,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val secondary = tag.name.substringAfter(" (", "").removeSuffix(")")
            if (secondary.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "#$secondary",
                    fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 推荐用户卡（设计规格：280dp 宽 × 224dp 高，严格与左侧长图等高，杜绝底部空洞）。 */
@Composable
private fun RecommendedUserCard(
    user: PixivUser,
    followed: Boolean,
    onFollow: () -> Unit,
    onOpen: () -> Unit,
) {
    val tokens = VeneraTokens
    val shape = GalleryTokens.Shape.Large
    Column(
        modifier = Modifier
            .width(GalleryTokens.Component.RecommendedUserCardWidth)
            .height(GalleryTokens.Component.RecommendedUserCardHeight)
            // 可点卡统一「划过上跳」。
            .then(com.venera.desktop.ui.components.desktopCardLiftModifier(shape))
            .clip(shape)
            .background(GalleryTokens.Color.SurfaceLevel2)
            .border(DesktopGalleryMetrics.hairline, tokens.color.outlineVariant, shape)
            .clickable(onClick = onOpen),
    ) {
        // 上半部：画师作品三联图（高 160dp，3 张缩略图等宽拼贴对齐设计稿）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(GalleryTokens.Component.RecommendedThumbsHeight)
                .background(GalleryTokens.Color.SurfaceLevel1),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            val illusts = (user.illustUrls as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            val thumbs = (0 until 3).map { idx -> illusts.getOrNull(idx) }
            thumbs.forEach { thumbUrl ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(GalleryTokens.Color.SurfaceLevel3),
                ) {
                    if (!thumbUrl.isNullOrBlank()) {
                        VeneraCover(
                            url = thumbUrl,
                            contentDescription = user.name,
                            shimmerWhileLoading = true,
                            preserveAspectRatio = false,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
        // 下半部：画师信息与关注底栏（高 64dp）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(GalleryTokens.Component.RecommendedArtistBarHeight)
                .background(GalleryTokens.Color.SurfaceLevel2)
                .border(
                    width = DesktopGalleryMetrics.hairline,
                    color = GalleryTokens.Color.OutlineHairline,
                )
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(tokens.color.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                if (user.avatarUrl.isNotBlank()) {
                    VeneraCover(
                        url = user.avatarUrl,
                        contentDescription = user.name,
                        shimmerWhileLoading = false,
                        preserveAspectRatio = false,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        text = user.name.take(1),
                        fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                        fontWeight = VeneraType.cardTitle.weight,
                        color = tokens.color.textSecondary,
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = user.name,
                    style = GalleryTokens.Typography.CardTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (user.account.isNotBlank()) {
                    Text(
                        text = "@${user.account}",
                        fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                        color = GalleryTokens.Color.TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // 关注按钮（高 28dp 胶囊，阻止向卡片整屏事件冒泡）
            val pillShape = CircleShape
            val followInteraction = remember { MutableInteractionSource() }
            val followHovered by followInteraction.collectIsHoveredAsState()
            Box(
                modifier = Modifier
                    .height(28.dp)
                    .clip(pillShape)
                    .background(
                        when {
                            followed -> Color(0x1F98A2B6)
                            followHovered -> Color(0xFF3880FF)
                            else -> tokens.color.primary
                        }
                    )
                    .border(
                        1.dp,
                        if (followed) Color(0x33FFFFFF) else Color.Transparent,
                        pillShape,
                    )
                    .clickable(
                        interactionSource = followInteraction,
                        indication = null,
                        onClick = onFollow,
                    )
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (followed) "已关注" else "+ 关注",
                    fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                    fontWeight = VeneraType.cardTitle.weight,
                    color = if (followed) GalleryTokens.Color.TextSecondary else Color.White,
                )
            }
        }
    }
}
