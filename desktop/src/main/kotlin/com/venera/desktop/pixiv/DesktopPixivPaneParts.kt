package com.venera.desktop.pixiv

import com.venera.desktop.ui.theme.VeneraType
import com.venera.desktop.ui.DesktopPushButton
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.components.venera.VeneraCoverMask
import com.venera.compose.gallery.data.GalleryPixivWorks
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.PixivComment
import com.venera.compose.gallery.data.PixivCredentials
import com.venera.compose.gallery.data.PixivUserDetail
import com.venera.compose.gallery.data.PixivUserHead
import com.venera.compose.ui.tokens.VeneraTokens

/**
 * 右栏视差位移（归一化到 -0.5..0.5 的指针位置）。
 *
 * 传的是 `State` 而不是值：读发生在 `graphicsLayer` 的 lambda 里，只触发 layer 更新；
 * 若在组合期读成局部量，每次鼠标移动都会重组整栏。
 */
internal val LocalPaneParallax = compositionLocalOf { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

/**
 * 右栏浮卡：一张卡一层玻璃、各自描边，靠卡间缝隙分前后。
 *
 * @param depth 这一层相对指针的位移倍率（像素）；越小越像贴在底上，越大越靠前。
 */
@Composable
internal fun PaneGlassCard(
    depth: Float,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val tokens = VeneraTokens
    val parallax = LocalPaneParallax.current
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val edge by animateColorAsState(
        targetValue = if (hovered) tokens.color.outlineVariant.copy(alpha = 0.85f)
        else tokens.color.outlineVariant.copy(alpha = 0.4f),
        animationSpec = androidx.compose.animation.core.tween(160),
        label = "paneCardEdge",
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                val p = parallax.value
                translationX = p.x * depth * 2f
                translationY = p.y * depth * 2f
            }
            .hoverable(hover)
            .clip(RoundedCornerShape(16.dp))
            .background(com.venera.desktop.gallery.ui.GdGlass.card())
            .border(0.5.dp, edge, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        content = content,
    )
}

/** 右栏分区头：▎竖条 + 中文 + 英文微标（与首页同一套分区语言）。 */
@Composable
internal fun PaneSectionHead(
    title: String,
    en: String,
    modifier: Modifier = Modifier,
    count: String? = null,
) {
    val tokens = VeneraTokens
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(3.dp, 13.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(tokens.color.primary),
        )
        Text(
            text = title,
            fontSize = VeneraType.cardTitle.size, lineHeight = VeneraType.cardTitle.lineHeight,
            fontWeight = VeneraType.cardTitle.weight,
            color = tokens.color.textPrimary,
        )
        Text(
            text = en,
            fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
            fontWeight = VeneraType.cardTitle.weight,
            color = tokens.color.textTertiary,
        )
        if (count != null) {
            Spacer(Modifier.weight(1f))
            Text(text = count, fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight, color = tokens.color.textTertiary)
        }
    }
}

/** 一条评论：头像（空串退首字母座）/ 正文 / 贴纸 / 时间。站方没给的不编。 */
@Composable
internal fun DesktopPixivCommentRow(comment: PixivComment) {
    val tokens = VeneraTokens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = tokens.spacing.space2),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space3),
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(tokens.color.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            if (comment.userAvatarUrl.isNotBlank()) {
                VeneraCover(
                    url = comment.userAvatarUrl,
                    contentDescription = comment.userName.ifBlank { "评论者" },
                    mask = VeneraCoverMask.Visible,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    comment.userName.take(1).uppercase().ifBlank { "?" },
                    fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                    fontWeight = VeneraType.cardTitle.weight,
                    color = tokens.color.onPrimaryContainer,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = comment.userName.ifBlank { comment.userAccount.ifBlank { "已注销" } },
                fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                fontWeight = VeneraType.cardTitle.weight,
                color = tokens.color.textSecondary,
            )
            if (comment.text.isNotBlank()) {
                Text(
                    text = comment.text,
                    fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                    color = tokens.color.textPrimary,
                    modifier = Modifier.padding(top = tokens.spacing.space1),
                )
            }
            if (comment.stampUrl.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .padding(top = tokens.spacing.space1)
                        .width(96.dp)
                        .height(96.dp)
                        .clip(RoundedCornerShape(tokens.shape.small)),
                ) {
                    VeneraCover(
                        url = comment.stampUrl,
                        contentDescription = "贴纸",
                        mask = VeneraCoverMask.Visible,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            comment.date?.let {
                Text(
                    text = prettyUploadDate(it),
                    fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                    color = tokens.color.textTertiary,
                    modifier = Modifier.padding(top = tokens.spacing.space1),
                )
            }
        }
    }
}

/**
 * 右栏画师面板（详情页点头像**就地切面板**，不跳独立页；
 * 完整画师主页 [DesktopPixivArtistScreen] 仍在，入口是底部那枚按钮）。
 *
 * 头 + 关注 + 统计（登录腿 userDetail）+ 作品小网格（登录 userIllusts 首页 /
 * 匿名 profileAll + worksByIds 兜底），点作品 → 看图播放器。
 */
@Composable
internal fun DesktopPixivArtistSidePane(
    userId: Long,
    fallbackName: String,
    pixiv: GalleryPixivWorks,
    account: PixivCredentials,
    signed: Boolean,
    followBusy: Boolean,
    followNote: String?,
    /** 关注态的就地覆盖（写成功那一刻的值）；null = 用画师详情里带进来的那一格。 */
    followedOverride: Boolean? = null,
    onFollowToggle: (Boolean) -> Unit,
    /**
     * 这是**右栏就地切面板**的局部返回（画师面板 → 作品信息），
     * 调用点只是 `showArtistPane = false`，不改壳层 route。
     * `LocalShellBackHandled` 只管 route 级返回，故这枚把手不受它约束，**刻意保留**；
     * 不要把它误判成「同屏两个返回」而删掉。
     */
    onBackToInfo: () -> Unit,
    /** 点作品卡 → 另一篇作品详情（壳带 from = 当前作品页）。 */
    onOpenPost: (GalleryPost) -> Unit,
    onOpenArtistHome: (Long, String) -> Unit,
) {
    val tokens = VeneraTokens
    val head = produceState<PixivUserHead?>(null, userId) {
        value = pixiv.userHead(userId).getOrNull()
    }
    val detail = produceState<PixivUserDetail?>(null, userId) {
        value = if (!signed) null else account.bearerOrNull()?.let { bearer ->
            pixiv.userDetail(bearer, userId.toString()).getOrNull()
        }
    }
    val works = produceState<List<GalleryPost>?>(null, userId, signed) {
        value = null
        val bearer = account.bearerOrNull()
        if (bearer != null) {
            pixiv.userIllusts(bearer, userId.toString(), "illust").getOrNull()?.let { page ->
                value = page.works.map { it.post }.take(30)
            }
            if (value != null) return@produceState
        }
        // 匿名兜底：id 表 → 按 id 取卡。
        val index = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            pixiv.profileAll(userId).getOrNull()
        } ?: return@produceState
        val ids = index.idsFor(com.venera.compose.gallery.data.PixivArtistSection.ILLUST).take(24)
        val rows = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            pixiv.worksByIds(userId, ids, "illust", firstPage = true).getOrNull()
        }
        value = rows?.filterNotNull()?.map { it.post }
    }
    val name = head.value?.name?.takeIf { it.isNotBlank() } ?: fallbackName

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space3),
        ) {
            com.venera.desktop.ui.DesktopBackButton(onClick = onBackToInfo)
            Text(
                text = "画师",
                fontSize = VeneraType.pageTitle.size, lineHeight = VeneraType.pageTitle.lineHeight,
                fontWeight = VeneraType.cardTitle.weight,
                color = tokens.color.textPrimary,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = tokens.spacing.space4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space3),
        ) {
            Box(
                modifier = Modifier
                    .size(tokens.spacing.pixivArtistAvatarSize)
                    .clip(CircleShape)
                    .background(tokens.color.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                val avatarUrl = head.value?.avatarUrl
                if (avatarUrl != null) {
                    VeneraCover(
                        url = avatarUrl,
                        contentDescription = name,
                        mask = VeneraCoverMask.Visible,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        name.take(1).uppercase(),
                        fontSize = VeneraType.pageTitle.size, lineHeight = VeneraType.pageTitle.lineHeight,
                        fontWeight = VeneraType.cardTitle.weight,
                        color = tokens.color.onPrimaryContainer,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    fontSize = VeneraType.cardTitle.size, lineHeight = VeneraType.cardTitle.lineHeight,
                    fontWeight = VeneraType.cardTitle.weight,
                    color = tokens.color.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                head.value?.account?.takeIf { it.isNotBlank() }?.let {
                    Text("@$it", fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight, color = tokens.color.textTertiary)
                }
            }
            if (signed) {
                PixivFollowButton(
                    followed = followedOverride ?: (detail.value?.isFollowed == true),
                    busy = followBusy,
                    onToggle = onFollowToggle,
                )
            }
        }
        followNote?.let {
            Text(text = it, fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight, color = tokens.color.textTertiary)
        }
        val d = detail.value
        val stats = listOfNotNull(
            d?.totalIllusts?.let { "插画 $it" },
            d?.totalManga?.let { "漫画 $it" },
            d?.totalFollowers?.let { "粉丝 $it" },
            d?.totalBookmarksPublic?.let { "收藏 $it" },
        )
        Text(
            text = stats.joinToString(" · ").ifBlank { if (signed) "统计还在取…" else "统计要登录才看得到。" },
            fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
            color = tokens.color.textSecondary,
            modifier = Modifier.padding(top = tokens.spacing.space2),
        )

        Text(
            text = "作品",
            fontSize = VeneraType.cardTitle.size, lineHeight = VeneraType.cardTitle.lineHeight,
            fontWeight = VeneraType.cardTitle.weight,
            color = tokens.color.textSecondary,
            modifier = Modifier.padding(top = tokens.spacing.space5, bottom = tokens.spacing.space2),
        )
        when (val list = works.value) {
            null -> Text("正在取…", fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight, color = tokens.color.textTertiary)
            emptyList<GalleryPost>() -> Text(
                "这一段还没有作品。",
                fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                color = tokens.color.textTertiary,
            )
            else -> {
                list.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = tokens.spacing.space2),
                        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
                    ) {
                        row.forEach { post ->
                            val aspect = if (post.previewHeight > 0 && post.previewWidth > 0) {
                                (post.previewWidth.toFloat() / post.previewHeight).coerceIn(0.5f, 2.0f)
                            } else {
                                0.75f
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(aspect)
                                    .clip(RoundedCornerShape(tokens.shape.small))
                                    .clickable { onOpenPost(post) },
                            ) {
                                VeneraCover(
                                    url = post.previewUrl.ifBlank { post.fastUrl }.ifBlank { post.largeUrl },
                                    contentDescription = post.author.ifBlank { "pixiv #${post.id}" },
                                    mask = VeneraCoverMask.Visible,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                        if (row.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        DesktopPushButton(
            onClick = { onOpenArtistHome(userId, name) },
            modifier = Modifier.padding(top = tokens.spacing.space4),
        ) { Text("打开完整画师主页 →") }
    }
}

/** 站方给的是带时区的 ISO 串；右栏只念到分钟，年月日加时分已经够认。 */
internal fun prettyUploadDate(raw: String): String =
    if (raw.length >= 16 && raw[10] == 'T') {
        raw.substring(0, 10) + "  " + raw.substring(11, 16)
    } else {
        raw
    }
