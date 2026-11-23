package com.venera.desktop.detail

import com.venera.desktop.ui.DesktopButton
import com.venera.desktop.ui.DesktopButtonDefaults
import com.venera.desktop.ui.DesktopButtonSize
import com.venera.desktop.ui.DesktopButtonVariant
import com.venera.desktop.ui.vShadow
import com.venera.desktop.ui.theme.VeneraType
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.venera.desktop.gallery.ui.LocalChromePalette
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.venera.compose.components.LocalUxPlatformServices
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.components.venera.VeneraCoverMask
import com.venera.compose.ui.tokens.StatusColors
import com.venera.desktop.gallery.ui.DesktopTheme
import com.venera.desktop.gallery.ui.desktopComicContinueCaption

// ════════════════════════════ 信息头 ════════════════════════════

/**
 * Hero（编辑排版版重写）：电影横幅。
 *
 * 封面 blur26 铺底 + 压层 + **底部渐隐到页底**（不是硬边裁切）；封面 140×188 悬浮书卡
 * （全页唯一投影——暗底上合法，其余一律发丝线）；标题 25/800 是全页最大字；
 * CTA 行 = 主胶囊（文案 + 话名小字）│发丝│ 图标钮组（下载/收藏/点赞/评论）│发丝│ 链接组；
 * 底部一条全宽 2dp 进度线（零文字，进度文字在胶囊里）。
 *
 * 冻结项：文案（开始/继续阅读、收藏三态、点赞 N、评论 N、离线下载、复制链接、浏览器打开）、
 * 胶囊三档判据、`desktopComicContinueCaption` 本屏单点调用。
 */
@Composable
internal fun ComicDetailHero(
    data: DesktopDetailViewData,
    masked: Boolean,
    onBack: () -> Unit,
    onResumeRead: () -> Unit,
    onBulkOpenChange: (Boolean) -> Unit,
    bulkAvailable: Boolean,
    onToggleFavorite: () -> Unit,
    /** 评论把手：滚到主列评论区（宽窄两档的滚动容器都在 Content 里）。 */
    onOpenComments: () -> Unit,
    onToggleLike: () -> Unit,
    onSearchTag: (String) -> Unit,
    onCopyWebsite: (() -> Unit)?,
    onOpenWebsite: (() -> Unit)?,
    /** 追更按钮态；null = 装配点没接这一路 ⇒ 整颗不摆（没有写口就没有那枚钮）。 */
    followState: com.venera.desktop.follow.FollowDetailUiState? = null,
    onToggleFollow: (() -> Unit)? = null,
) {
    val palette = LocalChromePalette.current
    var descExpanded by remember(data.description) { mutableStateOf(false) }
    val folded = desktopDescFolded(descExpanded, data.description)
    val mask = if (masked) VeneraCoverMask.Masked else VeneraCoverMask.Visible
    // 小字那颗算式在这一屏**恰好一处**调用（胶囊与进度线共用同一枚成品串，
    // 两处各算一遍就是能漂成两种的那一族 —— `DesktopComicContinueReadingTest` ⑥ 钉着）。
    val heroCapsule = data.readCapsule?.takeIf { it.hasHistory }
    val heroCaption = heroCapsule?.let { desktopComicContinueCaption(it.chapterTitle, it.pageIndex) }

    Box(modifier = Modifier.fillMaxWidth()) {
        // ── 背景：封面虚化 + 压层 + 底部渐隐到页底 ──
        Box(Modifier.matchParentSize()) {
            Box(Modifier.matchParentSize().blur(26.dp)) {
                VeneraCover(url = data.cover, contentDescription = null, mask = mask)
            }
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.44f)))
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.62f to Color.Black.copy(alpha = 0.28f),
                        // 终点收到透明：内容区底改由壳层玻璃给，渐变再收到 page 就会在
                        // hero 下缘压出一条实色带，和下方玻璃断开。
                        1f to Color.Transparent,
                    ),
                ),
            )
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            // Hero 左上那枚浮返回删掉，返回统一由壳层顶栏「‹ 漫画详情」承担，
            // 内容区不再摆第二枚返回。onBack 参数保留（壳层接线与离屏判据仍吃它）。
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                // 封面：悬浮书卡（全页唯一投影）。
                Box(
                    modifier = Modifier
                        .width(140.dp)
                        .height(188.dp)
                        .vShadow(10.dp, RoundedCornerShape(10.dp))
                        .clip(RoundedCornerShape(10.dp)),
                ) {
                    VeneraCover(url = data.cover, contentDescription = data.title, mask = mask)
                }

                Column(modifier = Modifier.weight(1f).widthIn(max = 760.dp)) {
                    Text(
                        text = data.title,
                        fontSize = VeneraType.heroTitle.size,
                        lineHeight = VeneraType.heroTitle.lineHeight,
                        fontWeight = VeneraType.heroTitle.weight,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // 副题行：作者（上传者缺栏由作者顶位）· 更新时间 · 评分（没报就不摆）。
                    val metaParts = buildList {
                        if (data.author.isNotBlank()) {
                            add(
                                if (data.uploader.isNotBlank() && data.uploader != data.author) {
                                    com.venera.desktop.settings.DesktopI18n.t(
                                        "作者 ${data.author} · 上传者 ${data.uploader}",
                                        "Author ${data.author} · Uploader ${data.uploader}",
                                    )
                                } else {
                                    com.venera.desktop.settings.DesktopI18n.t("作者 ${data.author}", "Author ${data.author}")
                                },
                            )
                        }
                        if (data.updateTime.isNotBlank()) add(data.updateTime)
                        if (data.rating > 0f) add("★ %.1f".format(data.rating))
                    }
                    if (metaParts.isNotEmpty()) {
                        Spacer(Modifier.height(7.dp))
                        Text(
                            text = metaParts.joinToString("  ·  "),
                            fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                            color = Color.White.copy(alpha = 0.62f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    // 热标签：摊平后前 8 颗（完整分组在侧列「标签与分类」），可点发搜索。
                    val hotTags = data.tagGroups.flatMap { (_, values) -> values }.take(8)
                    if (hotTags.isNotEmpty()) {
                        Spacer(Modifier.height(9.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            hotTags.forEachIndexed { index, tag ->
                                // 标签右键也按源搜（onSearchTag 已顶成本页源）。
                                val tagUx = LocalUxPlatformServices.current
                                com.venera.desktop.ui.components.DesktopComicCardMenuArea(
                                    actions = com.venera.desktop.ui.components.DesktopComicCardActions(
                                        sourceKey = data.sourceKey,
                                        onSearchInSource = onSearchTag,
                                        onCopyTitle = { tagUx.copyToClipboard(tag) },
                                    ),
                                    tag = tag,
                                ) {
                                    HeroTagChip(text = tag, highlighted = index == 0, onClick = { onSearchTag(tag) })
                                }
                            }
                        }
                    }

                    if (data.description.isNotBlank()) {
                        Spacer(Modifier.height(9.dp))
                        Text(
                            text = if (folded) data.description.take(DESKTOP_DESC_FOLD_LIMIT) + "…" else data.description,
                            fontSize = VeneraType.aux.size,
                            lineHeight = 20.sp,
                            color = Color.White.copy(alpha = 0.66f),
                            maxLines = if (folded) 2 else Int.MAX_VALUE,
                            overflow = TextOverflow.Ellipsis,
                        )
                        // 展开/收起那枚把手：**只有折得动的简介才摆**（理由见 desktopDescFolded）。
                        if (data.description.length > DESKTOP_DESC_FOLD_LIMIT) {
                            Text(
                                text = if (descExpanded) com.venera.desktop.settings.DesktopI18n.t("收起", "Collapse") else com.venera.desktop.settings.DesktopI18n.t("展开", "Expand"),
                                fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                                color = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { descExpanded = !descExpanded }
                                    .padding(vertical = 2.dp, horizontal = 2.dp),
                            )
                        }
                    }

                    Spacer(Modifier.height(11.dp))
                    // ── CTA 行：主胶囊 │ 发丝 │ 图标钮组 │ 发丝 │ 链接组 ──
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ReadCapsule(capsule = data.readCapsule, caption = heroCaption, onResume = onResumeRead)
                        HeroCtaDivider()
                        if (bulkAvailable) {
                            HeroActionButton(
                                icon = Icons.Outlined.FileDownload,
                                label = com.venera.desktop.settings.DesktopI18n.t(DESKTOP_BULK_DOWNLOAD_LABEL, "Download"),
                                onClick = { onBulkOpenChange(true) },
                            )
                        }
                        HeroActionButton(
                            icon = if (data.favorited == true) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            label = when {
                                data.favoriteBusy -> com.venera.desktop.settings.DesktopI18n.t("正在写收藏…", "Saving…")
                                data.favorited == null -> com.venera.desktop.settings.DesktopI18n.t("正在读收藏…", "Loading…")
                                data.favorited -> com.venera.desktop.settings.DesktopI18n.t("已收藏", "Favorited")
                                else -> com.venera.desktop.settings.DesktopI18n.t("收藏", "Favorite")
                            },
                            accent = data.favorited == true,
                            enabled = data.favorited != null && !data.favoriteBusy,
                            onClick = onToggleFavorite,
                        )
                        // 追更：独立于收藏的开关；未读回前置灰，不把"还不知道"装成"没追"。
                        if (followState != null && onToggleFollow != null) {
                            HeroActionButton(
                                icon = if (followState.following == true) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                label = when {
                                    followState.busy -> com.venera.desktop.settings.DesktopI18n.t("正在写追更…", "Saving…")
                                    followState.following == null -> com.venera.desktop.settings.DesktopI18n.t("正在读追更…", "Loading…")
                                    followState.following -> com.venera.desktop.settings.DesktopI18n.t("已追更", "Following")
                                    else -> com.venera.desktop.settings.DesktopI18n.t("追更", "Follow updates")
                                },
                                accent = followState.following == true,
                                enabled = followState.following != null && !followState.busy,
                                onClick = onToggleFollow,
                            )
                        }
                        // 点赞：源声明能点（canLike）就摆；读数没报时只念「点赞」，不拿 0 冒充。
                        val likeCount = data.likesCount
                        if (likeCount != null || data.canLike) {
                            HeroActionButton(
                                icon = if (data.liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                label = if (likeCount != null) {
                                    com.venera.desktop.settings.DesktopI18n.t("点赞 $likeCount", "Like $likeCount")
                                } else {
                                    com.venera.desktop.settings.DesktopI18n.t("点赞", "Like")
                                },
                                accent = data.liked,
                                onClick = onToggleLike,
                            )
                        }
                        HeroActionButton(
                            icon = Icons.Outlined.ChatBubbleOutline,
                            label = desktopCommentsHandleLabel(data.commentCount),
                            onClick = onOpenComments,
                        )
                        if (onCopyWebsite != null || onOpenWebsite != null) {
                            HeroCtaDivider()
                        }
                        if (onCopyWebsite != null) {
                            HeroActionButton(icon = Icons.Outlined.Link, label = com.venera.desktop.settings.DesktopI18n.t("复制链接", "Copy link"), onClick = onCopyWebsite)
                        }
                        if (onOpenWebsite != null) {
                            HeroActionButton(icon = Icons.Outlined.Public, label = com.venera.desktop.settings.DesktopI18n.t("浏览器打开", "Open in browser"), onClick = onOpenWebsite)
                        }
                    }
                    // 动作那一路的失败原话（点赞没被源接住/收藏没写成）：贴在操作行下面，不留白。
                    if (data.actionError != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = data.actionError,
                            fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                            color = StatusColors.Degraded,
                        )
                    }
                }
            }

            // ── 底部：全宽 2dp 进度线（只在真有历史时填；没历史画进度就是编的）──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Color.White.copy(alpha = 0.10f)),
            ) {
                if (data.readCapsule != null && data.readCapsule.progress > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(data.readCapsule.progress)
                            .height(2.dp)
                            .background(palette.accent),
                    )
                }
            }
        }
    }
}

/** hero 标签 chip：首枚反白（热标签），其余半透白描边。 */
@Composable
internal fun HeroTagChip(text: String, highlighted: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (highlighted) Color.White.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.10f))
            .border(
                0.5.dp,
                if (highlighted) Color.Transparent else Color.White.copy(alpha = 0.18f),
                CircleShape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
            fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Normal,
            color = if (highlighted) DesktopTheme.WindowBackground else Color.White.copy(alpha = 0.86f),
            maxLines = 1,
        )
    }
}

/** CTA 行里的竖向发丝（分组用，不是分隔线控件）。 */
@Composable
internal fun HeroCtaDivider() {
    Box(Modifier.width(0.5.dp).height(22.dp).background(Color.White.copy(alpha = 0.16f)))
}

/**
 * hero 图标钮：图标 + 小字（40dp 高 r10 半透底）。**字必须在**——收藏三态/点赞读数/评论读数
 * 的语义节点挂在这行 Text 上（四颗离屏判据按文本找把手）。
 */
@Composable
internal fun HeroActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    label: String,
    onClick: () -> Unit,
    accent: Boolean = false,
    enabled: Boolean = true,
) {
    DesktopButton(
        onClick = onClick,
        size = DesktopButtonSize.Large,
        variant = DesktopButtonVariant.Secondary,
        enabled = enabled,
        horizontalPadding = 12.dp,
        colors = DesktopButtonDefaults.translucentOnDark(active = accent),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = label,
            fontSize = VeneraType.micro.size,
            lineHeight = VeneraType.micro.lineHeight,
            maxLines = 1,
        )
    }
}


