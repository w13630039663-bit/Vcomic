package com.venera.desktop.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.domain.GalleryRatingTone
import com.venera.compose.gallery.domain.ratingToneOf

/**
 * 画廊卡片是否显示 AI 角标的全局开关槽位（由 [DesktopWallScaffold] 接入偏好流）。
 */
internal val LocalDesktopGalleryAiBadge = staticCompositionLocalOf { true }

private val CardBadgeShape = RoundedCornerShape(6.dp)

/**
 * 判断作品卡片是否应展示 `R18` 限制级角标（兼容 Booru `e`/`explicit` 与 Pixiv `r18`/`masked`）。
 */
internal fun desktopCardShowR18Badge(post: GalleryPost?): Boolean {
    if (post == null) return false
    if (ratingToneOf(post.rating) == GalleryRatingTone.Explicit) return true
    return post.rating.trim().lowercase() in setOf("r18", "r-18", "masked")
}

/**
 * 卡片封面左上角媒体属性微标：多页数、动图标识。
 */
@Composable
internal fun DesktopGalleryCardTopLeftBadges(
    post: GalleryPost?,
    modifier: Modifier = Modifier,
) {
    if (post == null) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if ((post.pageCount ?: 1) > 1) {
            Row(
                modifier = Modifier
                    .clip(CardBadgeShape)
                    .background(GalleryTokens.Color.OverlayScrim)
                    .border(1.dp, Color.White.copy(alpha = 0.14f), CardBadgeShape)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Layers,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.94f),
                    modifier = Modifier.size(11.dp),
                )
                Text(
                    text = "${post.pageCount ?: 1}",
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.94f),
                )
            }
        }
        if (post.isVideo) {
            GalleryBadgeChip(
                text = "动图",
                bgColor = GalleryTokens.Color.OverlayScrim,
                borderColor = Color.White.copy(alpha = 0.14f),
            )
        }
    }
}

/**
 * 卡片封面右上角角标组：`R18` 限制级徽标与 `AI` 生成徽标（不与悬停三联药丸争位）。
 */
@Composable
internal fun DesktopGalleryCardTopRightBadges(
    card: DesktopWallCardModel,
    showAiBadge: Boolean,
    modifier: Modifier = Modifier,
) {
    val post = card.sourcePost ?: return
    val showR18 = desktopCardShowR18Badge(post)
    val showAi = aiBadgeFor(post, showAiBadge)
    if (!showR18 && !showAi) return

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showR18) {
            GalleryBadgeChip(
                text = "R18",
                bgColor = GalleryTokens.Color.StatusDanger.copy(alpha = 0.90f),
                borderColor = Color.White.copy(alpha = 0.24f),
            )
        }
        if (showAi) {
            GalleryBadgeChip(
                text = AI_BADGE_TEXT,
                bgColor = GalleryTokens.Color.OverlayScrim,
                borderColor = Color.White.copy(alpha = 0.22f),
            )
        }
    }
}

@Composable
private fun GalleryBadgeChip(
    text: String,
    bgColor: Color,
    borderColor: Color,
) {
    Box(
        modifier = Modifier
            .clip(CardBadgeShape)
            .background(bgColor)
            .border(1.dp, borderColor, CardBadgeShape)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.3.sp,
            color = Color.White,
        )
    }
}
