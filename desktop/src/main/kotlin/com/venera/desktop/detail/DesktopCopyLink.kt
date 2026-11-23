package com.venera.desktop.detail

import com.venera.desktop.ui.theme.VeneraType
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import com.venera.compose.ui.tokens.VeneraTokens

/**
 * 「复制链接」那一族 + 详情页那一排圆座动作钮（对齐对岸
 * `DetailActionButton` + `:679-686` 那排）。
 *
 * ## 为什么圆座里的字形是**文字**而不是 Icon
 *
 * 对岸那排吃 `material-icons`（BookmarkBorder/Comment/ContentCopy…），而桌面编译面立着
 * 「不引第二套图标集」那条口径（详情主胶囊那颗的注释里记过同一课：为了一枚图标把整套搬进来是蠢事）。
 * 所以圆座照搬、字放文字位 —— 形对齐而不拖依赖。
 *
 * ## 摆不摆、点不点得动，三闸都在调用点判（这里只提供件）
 *
 * 对岸那族是**状态型**（收藏/点赞：激活换身份色+粗体）与**动作型**（评论/复制链接：恒中性）两种形；
 * 桌面这一批把这一区分照搬：`active` 非 null 时换色换粗，null 时恒中性。
 * 没有真实动作的东西（点赞：桌面没有 like 那一跳）**不摆成钮**——屏上那颗 ★/♥ 是纯读数。
 *
 * ## 「复制链接」的剪贴板链
 *
 * 复制的是源在 `loadInfo` 报的 `url` 那一栏（文本 flavor，不是画廊那族图片件的位图 flavor）。
 * 无头守卫 `isHeadless` 照图片件那颗（文本剪贴板也没有 `isSupported` 档，无头时直接抛）。
 * 地址逐字交给系统：不拼、不补协议 —— 改过的那串就不是屏上念的那条了。
 */

/** 这台机器给不给"写文本到剪贴板"这条路（无头 = 不给）。 */
internal fun desktopCanCopyText(): Boolean = !GraphicsEnvironment.isHeadless()

/**
 * 一枚圆座动作钮（对岸 `DetailActionButton` 的桌面形）：40dp 圆座 + 下方标签。
 *
 * - [activeColor] 非 null = **状态型**：激活时字与标签都换身份色并加粗（对岸"换形与换色两条通道同时给"）。
 * - null = **动作型**：恒中性。
 * - 禁用（[enabled]=false）时座仍在而点不动 —— 这一族没有"按不动还摆着"的合法形状，
 *   调用点要么别摆要么把原因摆在标签上；这一枚只表达"当前没接上/正在忙"那一档（收藏写库进行中）。
 */
@Composable
internal fun DetailActionPill(
    glyph: String,
    label: String,
    onClick: () -> Unit,
    /** 状态型的身份色；动作型交回 null。 */
    activeColor: Color? = null,
    active: Boolean = false,
    enabled: Boolean = true,
) {
    val tokens = VeneraTokens
    val isActive = active && activeColor != null
    val tint = when {
        isActive -> activeColor
        !enabled -> tokens.color.textDisabled
        else -> tokens.color.textSecondary
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            // 锚：圆座没有独立文字语义节点，离屏判据点名这一枚要吃 contentDescription。
            .semantics { contentDescription = label }
            .padding(horizontal = tokens.spacing.space5, vertical = tokens.spacing.space2),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.space1),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(tokens.color.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = glyph, fontSize = VeneraType.body.size, lineHeight = VeneraType.body.lineHeight, color = tint)
        }
        Text(
            text = label,
            fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
            color = tint,
            fontWeight = if (isActive) tokens.type.weightBold else tokens.type.weightRegular,
        )
    }
}

/**
 * 桌面详情这一排的三枚（收藏 / 评论 / 复制链接）—— 全部有真 handler 才进这一排。
 *
 * 不摆的也写明（对齐截图里而对岸有而桌面做不了的，一条条数）：
 *  - **点赞 ♥**：`loadInfo` 报了 `likesCount`，但桌面引擎没有 like/unlike 那一跳
 *    （`EngineSession` 只有 load/explore/search/details/comments/pages/pageImage/cookie）——
 *    那一格只当**纯读数**摆在信息区，不摆成一枚按不动的钮；
 *  - **分享**：桌面没有分享目标（安卓那枚走系统分享面板），整颗不摆。
 */
@Composable
internal fun DetailActionRow(
    favorited: Boolean?,
    favoriteBusy: Boolean,
    onToggleFavorite: (() -> Unit)?,
    commentLabel: String,
    onOpenComments: () -> Unit,
    /** 源报了 `url` 才有；null ⇒ 复制链接那一枚不摆。 */
    website: String?,
    onCopyLink: (String) -> Unit,
) {
    val tokens = VeneraTokens
    Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space1)) {
        // 收藏 = 状态型（身份色 = primary；激活 = 已收藏）。三档里"还没读回来"禁而念得出。
        if (onToggleFavorite != null) {
            DetailActionPill(
                glyph = if (favorited == true) "★" else "☆",
                label = when {
                    favoriteBusy -> com.venera.desktop.settings.DesktopI18n.t("正在写收藏…", "Saving…")
                    favorited == null -> com.venera.desktop.settings.DesktopI18n.t("正在读收藏…", "Loading…")
                    favorited == true -> com.venera.desktop.settings.DesktopI18n.t("已收藏", "Favorited")
                    else -> com.venera.desktop.settings.DesktopI18n.t("收藏", "Favorite")
                },
                onClick = onToggleFavorite,
                activeColor = tokens.color.primary,
                active = favorited == true,
                enabled = favorited != null && !favoriteBusy,
            )
        }
        // 评论 = 动作型（恒中性），恒摆（与对岸那枚 `DetailActionButton(icon = Comment)` 同形）。
        DetailActionPill(
            glyph = "✉",
            label = commentLabel,
            onClick = onOpenComments,
        )
        // 复制链接 = 动作型；源没报地址（null）整枚不摆。
        if (!website.isNullOrBlank()) {
            DetailActionPill(
                glyph = "⧉",
                label = com.venera.desktop.settings.DesktopI18n.t("复制链接", "Copy link"),
                onClick = { onCopyLink(website) },
            )
        }
    }
}
