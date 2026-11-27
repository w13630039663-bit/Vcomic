package com.venera.desktop.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.gallery.data.PixivUser
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.gallery.data.DesktopPixivisionArticle
import com.venera.desktop.ui.components.desktopCardLiftModifier
import com.venera.desktop.ui.components.registerPixelBuddyCard
import com.venera.desktop.ui.theme.VeneraType

/**
 * pixivision 特辑通栏（图二样式：主推大卡 + 右侧双叠小卡）。
 */
@Composable
internal fun DesktopPixivisionHeaderRow(
    articles: List<DesktopPixivisionArticle>,
    onOpenArticle: (DesktopPixivisionArticle) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (articles.isEmpty()) return
    val tokens = VeneraTokens
    val gap = DesktopGalleryMetrics.wallGridGap

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = tokens.spacing.screenHorizontal),
    ) {
        PixivSectionHeader(title = "pixivision", enTag = "MEDIA")
        Spacer(Modifier.height(tokens.spacing.space1))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            // 主推大卡（1.35f 宽）
            PixivisionArticleCard(
                article = articles.first(),
                showCategory = true,
                onOpen = { onOpenArticle(articles.first()) },
                modifier = Modifier
                    .weight(1.35f)
                    .fillMaxHeight(),
            )
            // 右侧双叠小卡（1f 宽）
            val subArticles = articles.drop(1).take(2)
            if (subArticles.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(gap),
                ) {
                    subArticles.forEach { article ->
                        PixivisionArticleCard(
                            article = article,
                            showCategory = false,
                            onOpen = { onOpenArticle(article) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/** 单张 Pixivision 文章卡。 */
@Composable
private fun PixivisionArticleCard(
    article: DesktopPixivisionArticle,
    showCategory: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    val shape = RoundedCornerShape(tokens.shape.medium)

    Box(
        modifier = modifier
            .registerPixelBuddyCard("pixivision-${article.id}")
            .then(desktopCardLiftModifier(shape))
            .clip(shape)
            .background(tokens.color.surfaceVariant.copy(alpha = tokens.current.placeholderAlpha))
            .border(DesktopGalleryMetrics.hairline, tokens.color.outlineVariant, shape)
            .clickable(onClick = onOpen),
    ) {
        if (article.coverUrl.isNotBlank()) {
            VeneraCover(
                url = article.coverUrl,
                contentDescription = article.title,
                shimmerWhileLoading = true,
                preserveAspectRatio = false,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(84.dp)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.72f),
                    ),
                ),
        )
        if (showCategory && article.category.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(tokens.spacing.space2)
                    .clip(RoundedCornerShape(tokens.shape.small))
                    .background(tokens.color.primary)
                    .padding(horizontal = tokens.spacing.space2, vertical = tokens.spacing.space1),
            ) {
                Text(
                    text = article.category,
                    fontSize = VeneraType.micro.size,
                    lineHeight = VeneraType.micro.lineHeight,
                    fontWeight = tokens.type.weightBold,
                    color = tokens.color.onPrimary,
                )
            }
        }
        Text(
            text = article.title,
            fontSize = VeneraType.aux.size,
            lineHeight = VeneraType.aux.lineHeight,
            fontWeight = tokens.type.weightBold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = tokens.spacing.space3)
                .padding(bottom = tokens.spacing.space2),
        )
    }
}

/**
 * 推荐画师横滑卡片流（用户截图样式）：
 * - 卡片尺寸：280dp 宽 × 224dp 高；
 * - 上半部：3 张代表作竖图并排拼贴（高 160dp，间距 3dp）；
 * - 下半部：头像 + 昵称 + @handle + 胶囊关注按钮（高 28dp，双向关注与取消）；
 * - 左右翻页圆钮。
 */
@Composable
internal fun DesktopPixivRecommendedUsersRow(
    users: List<PixivUser>?,
    followedIds: Set<Long>,
    onFollowUser: (PixivUser) -> Unit,
    onOpenUser: (PixivUser) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (users.isNullOrEmpty()) return
    val tokens = VeneraTokens
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val canScrollBack by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
    }
    val canScrollForward by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            if (total == 0) false else {
                val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastVisible < total - 1
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = tokens.spacing.screenHorizontal),
    ) {
        PixivSectionHeader(title = "推荐画师", enTag = "USERS")
        Spacer(Modifier.height(tokens.spacing.space1))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(224.dp),
        ) {
            LazyRow(
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(users, key = { it.id }) { user ->
                    RecommendedUserCard(
                        user = user,
                        followed = user.id in followedIds,
                        onFollow = { onFollowUser(user) },
                        onOpen = { onOpenUser(user) },
                    )
                }
            }
            if (canScrollBack) {
                PixivArrowButton(
                    arrow = "‹",
                    modifier = Modifier.align(Alignment.CenterStart),
                    onClick = {
                        scope.launch { listState.animateScrollBy(-300f) }
                    },
                )
            }
            if (canScrollForward) {
                PixivArrowButton(
                    arrow = "›",
                    modifier = Modifier.align(Alignment.CenterEnd),
                    onClick = {
                        scope.launch { listState.animateScrollBy(300f) }
                    },
                )
            }
        }
    }
}

/** 单个推荐画师卡片（280dp 宽 × 224dp 高，3 联竖图大作）。 */
@Composable
private fun RecommendedUserCard(
    user: PixivUser,
    followed: Boolean,
    onFollow: () -> Unit,
    onOpen: () -> Unit,
) {
    val tokens = VeneraTokens
    val shape = RoundedCornerShape(tokens.shape.large)
    var localFollowed by remember(followed) { mutableStateOf(followed) }

    Column(
        modifier = Modifier
            .width(280.dp)
            .height(224.dp)
            .registerPixelBuddyCard("user-${user.id}")
            .then(desktopCardLiftModifier(shape))
            .clip(shape)
            .background(tokens.color.surfaceVariant.copy(alpha = tokens.current.placeholderAlpha))
            .border(DesktopGalleryMetrics.hairline, tokens.color.outlineVariant, shape)
            .clickable(onClick = onOpen),
    ) {
        // 上半部：画师作品三联图（高 160dp）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .background(tokens.color.surfaceContainerHigh),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            val illusts = user.illustUrls.take(3)
            (0 until 3).forEach { idx ->
                val thumbUrl = illusts.getOrNull(idx)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(tokens.color.surfaceVariant),
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

        // 下半部：画师资料与双向关注底栏（高 64dp）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(tokens.color.surfaceVariant.copy(alpha = 0.5f))
                .border(DesktopGalleryMetrics.hairline, tokens.color.outlineVariant.copy(alpha = 0.3f))
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
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = tokens.color.textSecondary,
                    )
                }
            }

            Column(Modifier.weight(1f)) {
                Text(
                    text = user.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = tokens.color.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (user.account.isNotBlank()) {
                    Text(
                        text = "@${user.account}",
                        fontSize = 11.sp,
                        color = tokens.color.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // 关注按钮（高 28dp 胶囊，真实切换状态）
            val followInteraction = remember { MutableInteractionSource() }
            val followHovered by followInteraction.collectIsHoveredAsState()

            Box(
                modifier = Modifier
                    .height(28.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            localFollowed -> Color(0x1F98A2B6)
                            followHovered -> Color(0xFF3880FF)
                            else -> tokens.color.primary
                        }
                    )
                    .border(
                        1.dp,
                        if (localFollowed) Color(0x33FFFFFF) else Color.Transparent,
                        CircleShape,
                    )
                    .clickable(
                        interactionSource = followInteraction,
                        indication = null,
                    ) {
                        localFollowed = !localFollowed
                        onFollow()
                    }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (localFollowed) "已关注" else "+ 关注",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (localFollowed) tokens.color.textSecondary else tokens.color.onPrimary,
                )
            }
        }
    }
}

/** 翻页圆钮。 */
@Composable
private fun PixivArrowButton(
    arrow: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .padding(horizontal = 4.dp)
            .size(32.dp)
            .clip(CircleShape)
            .background(Color(0xD9161B26))
            .border(1.dp, Color(0x29FFFFFF), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = arrow,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** 节头：中文标题 + 英文标签。 */
@Composable
private fun PixivSectionHeader(title: String, enTag: String) {
    val tokens = VeneraTokens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = tokens.spacing.space2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
    ) {
        Text(
            text = title,
            fontSize = VeneraType.cardTitle.size,
            lineHeight = VeneraType.cardTitle.lineHeight,
            fontWeight = tokens.type.weightBold,
            color = tokens.color.textPrimary,
        )
        Text(
            text = enTag,
            fontSize = VeneraType.micro.size,
            lineHeight = VeneraType.micro.lineHeight,
            fontWeight = tokens.type.weightMedium,
            color = tokens.color.textTertiary,
        )
    }
}
