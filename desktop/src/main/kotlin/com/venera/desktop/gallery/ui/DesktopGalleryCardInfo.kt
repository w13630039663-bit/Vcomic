package com.venera.desktop.gallery.ui

import com.venera.desktop.ui.theme.VeneraType
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.ui.tokens.VeneraTokens

/**
 * 桌面画廊卡片的统一信息栏。
 *
 * 第一行是作品标题（站方/词典都没给出时退回站点编号，不编假标题），
 * 第二行固定为「站源 + 画师」。两行都单行省略，卡片宽度一致时信息栏高度也一致；
 * 图片本身仍由站方比例决定高度，不在这里压成统一矩形。
 *
 * 用户口径「卡片应该显示画师头像和作品标题」：pixiv 推荐/榜单腿的画师头像
 * 就在同一条响应里（`PixivWork.authorAvatarUrl`），经 `DesktopWallPage.avatars` 由装配点
 * 闭包递到这一颗的 `avatarUrl` ⇒ 摆进第二行画师名前那枚 18dp 圆座。
 * `avatarUrl == null` = **这条腿今天给不出头像**（booru 三站的 board 行没有这一格），
 * 布局与从前逐字相同——不拿首字母座补位：那会把"站方没给"长成"有个默认头像"，是假读数。
 */
@Composable
internal fun DesktopGalleryCardInfo(
    post: GalleryPost,
    title: String?,
    avatarUrl: String?,
    modifier: Modifier,
    onClickArtist: (() -> Unit)?,
    trailingAction: (@Composable () -> Unit)?,
    /** 画师名覆盖（收件箱图库卡传关注标签名）；null = 走既有判据。 */
    authorOverride: String? = null,
    /** 不摆站徽标（pixiv 收件箱卡无站源胶囊）；false = 保留。 */
    hideSourceBadge: Boolean = false,
    /** 站源胶囊是否点亮为主题色；常态中性，由卡片悬停态驱动。 */
    highlightSource: Boolean = false,
) {
    val displayTitle = title?.takeIf { it.isNotBlank() }
        ?: "${post.site.displayName} #${post.id}"
    val author = authorOverride?.takeIf { it.isNotBlank() } ?: run {
        // 图库站（Booru）精准提取真实画师标签，排除上传来源/伪名字。
        if (post.site == com.venera.compose.gallery.data.GallerySite.PIXIV) {
            post.author.takeIf { it.isNotBlank() } ?: "未知画师"
        } else {
            desktopExtractRealArtistTag(post, post.author) ?: ""
        }
    }
    DesktopGalleryCardInfoLayout(
        title = displayTitle,
        // 站徽标按传参收放：隐藏 = 空串（布局对空串本就不画）。
        source = if (hideSourceBadge) "" else post.site.displayName.uppercase(),
        author = author,
        avatarUrl = avatarUrl,
        modifier = modifier,
        onClickArtist = onClickArtist,
        trailingAction = trailingAction,
        highlightSource = highlightSource,
    )
}

@Composable
internal fun DesktopGalleryCardInfo(
    card: DesktopWallCardModel,
    modifier: Modifier,
    titleOverride: String?,
    avatarUrl: String?,
    onClickArtist: (() -> Unit)?,
    trailingAction: (@Composable () -> Unit)?,
    authorOverride: String? = null,
    hideSourceBadge: Boolean = false,
    highlightSource: Boolean = false,
) {
    val post = card.sourcePost
    if (post != null) {
        DesktopGalleryCardInfo(
            post = post,
            title = titleOverride,
            avatarUrl = avatarUrl,
            modifier = modifier,
            onClickArtist = onClickArtist,
            trailingAction = trailingAction,
            authorOverride = authorOverride,
            hideSourceBadge = hideSourceBadge,
            highlightSource = highlightSource,
        )
    } else {
        val isPixiv = card.url.contains("pixiv.net")
        val author = authorOverride?.takeIf { it.isNotBlank() } ?: run {
            if (isPixiv) {
                card.author.takeIf { it.isNotBlank() } ?: "未知画师"
            } else {
                desktopExtractRealArtistTag(null, card.author) ?: ""
            }
        }
        DesktopGalleryCardInfoLayout(
            title = titleOverride ?: card.title,
            source = "",
            author = author,
            avatarUrl = avatarUrl,
            modifier = modifier,
            onClickArtist = onClickArtist,
            trailingAction = trailingAction,
        )
    }
}

/**
 * 画廊卡统一信息栏：标题行与右侧动作槽，底行依序排头像、画师名与右对齐站源胶囊。
 *
 * @param title 展示标题。
 * @param source 站点简称标签，空串时不显示。
 * @param author 画师名。
 * @param avatarUrl 画师头像地址，空时不占位不造假头像。
 * @param modifier 外部布局修饰符。
 * @param onClickArtist 点击画师区域的跳转回调。
 * @param trailingAction 标题右侧动作插槽（如悬停渐显的更多菜单按钮）。
 * @param highlightSource 站源胶囊是否点亮为主题色；常态为中性低饱和。
 */
@Composable
internal fun DesktopGalleryCardInfoLayout(
    title: String,
    source: String,
    author: String,
    avatarUrl: String? = null,
    modifier: Modifier = Modifier,
    onClickArtist: (() -> Unit)?,
    trailingAction: (@Composable () -> Unit)?,
    highlightSource: Boolean = false,
) {
    val tokens = VeneraTokens
    val artistInteraction = remember { MutableInteractionSource() }
    val artistHovered by artistInteraction.collectIsHoveredAsState()
    val sourceBg by androidx.compose.animation.animateColorAsState(
        targetValue = if (highlightSource) tokens.color.primary.copy(alpha = 0.12f)
        else tokens.color.outlineVariant.copy(alpha = 0.08f),
        animationSpec = androidx.compose.animation.core.tween(200),
        label = "sourcePillBg",
    )
    val sourceBorder by androidx.compose.animation.animateColorAsState(
        targetValue = if (highlightSource) tokens.color.primary.copy(alpha = 0.28f)
        else tokens.color.outlineVariant.copy(alpha = 0.14f),
        animationSpec = androidx.compose.animation.core.tween(200),
        label = "sourcePillBorder",
    )
    val sourceText by androidx.compose.animation.animateColorAsState(
        targetValue = if (highlightSource) tokens.color.primary else tokens.color.textSecondary.copy(alpha = 0.82f),
        animationSpec = androidx.compose.animation.core.tween(200),
        label = "sourcePillText",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 6.dp, end = 6.dp, top = 9.dp, bottom = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                fontSize = 13.5.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
                color = tokens.color.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (trailingAction != null) {
                trailingAction()
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .then(
                        if (onClickArtist != null && author.isNotBlank()) {
                            Modifier
                                .hoverable(artistInteraction)
                                .pointerHoverIcon(PointerIcon.Hand)
                                .clickable(onClick = onClickArtist)
                                .padding(vertical = 1.dp)
                        } else {
                            Modifier
                        },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (avatarUrl != null) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(
                                tokens.color.surfaceVariant.copy(alpha = tokens.current.placeholderAlpha),
                            )
                            .border(
                                width = 1.dp,
                                color = tokens.color.outlineVariant.copy(alpha = 0.25f),
                                shape = CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        VeneraCover(
                            url = avatarUrl,
                            contentDescription = null,
                            shimmerWhileLoading = false,
                            preserveAspectRatio = false,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = author,
                    fontSize = 12.5.sp,
                    lineHeight = 16.sp,
                    color = if (artistHovered) tokens.color.textPrimary else tokens.color.textSecondary,
                    textDecoration = if (artistHovered) TextDecoration.Underline else TextDecoration.None,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (source.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(sourceBg)
                        .border(
                            width = 1.dp,
                            color = sourceBorder,
                            shape = RoundedCornerShape(5.dp),
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = source,
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.4.sp,
                        color = sourceText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
