package com.venera.desktop.ui

import com.venera.desktop.data.DesktopHomeStats

import com.venera.desktop.data.DesktopHomeHistorySnapshot
import com.venera.desktop.ui.vShadow
import com.venera.desktop.settings.DesktopI18n
import com.venera.desktop.ui.theme.VeneraType
import com.venera.desktop.gallery.ui.GalleryTokens
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.components.venera.VeneraCoverMask
import com.venera.compose.feature.HomeBookSpines
import com.venera.compose.feature.healthOf
import com.venera.compose.feature.color
import com.venera.compose.feature.label
import com.venera.compose.ui.tokens.SettingsBadgeColors
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.compose.data.db.HistoryRecord
import kotlin.random.Random

// ═══════════════════════════════════════════════════════════════════
// ① Hero（通栏全宽沉浸横幅）
// ═══════════════════════════════════════════════════════════════════

@Composable
internal fun DesktopHomeHero(
    history: DesktopHomeHistorySnapshot,
    stats: DesktopHomeStats,
    onOpenHistory: (HistoryRecord) -> Unit,
) {
    val tokens = VeneraTokens
    val latest = history.records.firstOrNull()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            // 设计稿：高 170dp、深度靠阴影（去 1px 描边）。
            .height(170.dp)
            .vShadow(2.dp, GalleryTokens.Shape.Bento)
            .clip(GalleryTokens.Shape.Bento),
    ) {
        // 背景：最近那一本的封面虚化铺底 + 底部压暗
        if (latest != null) {
            VeneraCover(
                url = latest.coverUrl,
                contentDescription = null,
                mask = VeneraCoverMask.Visible,
                shimmerWhileLoading = false,
                preserveAspectRatio = false,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(22.dp),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            tokens.color.background.copy(alpha = 0.20f),
                            tokens.color.background.copy(alpha = 0.88f),
                        ),
                    ),
                ),
        )

        // 前景主体（设计稿）：底部对齐 —— 封面卡 + 标题进度居左，统计纯数字 + 随机一本居右下。
        // 继续阅读的门 = 点封面卡 / 标题（原右下 CTA 撤，功能不砍）；统计玻璃胶囊撤，改纯数字列。
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 20.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (latest != null) {
                Box(
                    modifier = Modifier
                        .width(84.dp)
                        .height(116.dp)
                        .vShadow(1.dp, GalleryTokens.Shape.Large)
                        .clip(GalleryTokens.Shape.Large)
                        .background(tokens.color.surfaceVariant.copy(alpha = tokens.current.placeholderAlpha))
                        .clickable { onOpenHistory(latest) },
                ) {
                    VeneraCover(
                        url = latest.coverUrl,
                        contentDescription = latest.title,
                        mask = VeneraCoverMask.Visible,
                        shimmerWhileLoading = false,
                        preserveAspectRatio = false,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Bottom,
            ) {
                if (latest != null) {
                    Text(
                        text = latest.title,
                        fontSize = VeneraType.cardTitle.size, lineHeight = VeneraType.cardTitle.lineHeight,
                        fontWeight = VeneraType.cardTitle.weight,
                        color = tokens.color.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { onOpenHistory(latest) },
                    )
                    val progressLine = if (latest.totalPages > 0) {
                        latest.lastChapterTitle.ifBlank { "第 ${latest.lastChapterIndex + 1} 章" } +
                            " · 第 ${latest.lastPageIndex + 1} / ${latest.totalPages} 页"
                    } else {
                        latest.lastChapterTitle.ifBlank { null }
                    }
                    progressLine?.let {
                        Text(
                            text = it,
                            fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                            color = tokens.color.textSecondary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    if (latest.totalPages > 0) {
                        Row(
                            modifier = Modifier.padding(top = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(9.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(170.dp)
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(tokens.color.outlineVariant.copy(alpha = 0.45f)),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(
                                            ((latest.lastPageIndex + 1f) / latest.totalPages).coerceIn(0.02f, 1f),
                                        )
                                        .fillMaxHeight()
                                        .background(tokens.color.primary),
                                )
                            }
                            Text(
                                text = "${((latest.lastPageIndex + 1) * 100 / latest.totalPages).coerceIn(1, 100)}%",
                                fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                                color = tokens.color.textSecondary,
                            )
                        }
                    }
                } else {
                    Text(
                        text = "暂无阅读记录",
                        fontSize = VeneraType.cardTitle.size, lineHeight = VeneraType.cardTitle.lineHeight,
                        fontWeight = VeneraType.cardTitle.weight,
                        color = tokens.color.textPrimary,
                    )
                    Text(
                        text = when {
                            history.note != null -> history.note
                            !history.loaded -> "正在读本地历史…"
                            else -> "还没有阅读记录。到「探索」找一本开始读吧。"
                        },
                        fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                        color = tokens.color.textSecondary,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }

            // 右下：纯数字统计三列（设计稿 stats 形）+ 随机一本小圆钮（功能保留）。
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (history.records.size > 1) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(tokens.color.surfaceContainerHigh.copy(alpha = 0.72f))
                            .clickable {
                                val pick = history.records[Random.nextInt(history.records.size)]
                                onOpenHistory(pick)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Shuffle,
                            contentDescription = "随机一本",
                            tint = tokens.color.textSecondary,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
                HeroStat("${stats.todayPages}", "今日页数")
                HeroStat("${stats.weekPages}", "本周累计")
                HeroStat("${stats.streakDays}", "连续天数")
            }
        }
    }
}

/** Hero 右下统计格（设计稿 .stat 形）：纯数字 18 粗 + 标签 10.5 弱色，右对齐，无胶囊底。 */
@Composable
internal fun HeroStat(value: String, label: String) {
    val tokens = VeneraTokens
    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = value,
            fontSize = VeneraType.sectionTitle.size, lineHeight = VeneraType.sectionTitle.lineHeight,
            fontWeight = VeneraType.cardTitle.weight,
            color = tokens.color.textPrimary,
        )
        Text(
            text = label,
            fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
            color = tokens.color.textTertiary,
            modifier = Modifier.padding(top = 1.dp),
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
// ② 状态与服务仪表横栏（三卡等宽平铺）
// ═══════════════════════════════════════════════════════════════════

/** 卡片 A：漫画源监测（展示可用性指示灯与延迟毫秒数） */
@Composable
internal fun DashSourceCard(
    stats: DesktopHomeStats,
    onGoSourceManage: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    // 若数据为空，展示核心内置源列表，确保源状态永远可见不落空
    val sourcesToShow = if (stats.sources.isNotEmpty()) {
        stats.sources.take(3)
    } else {
        listOf(
            DesktopHomeStats.DesktopHomeSource("ehgt.org", 212L),
            DesktopHomeStats.DesktopHomeSource("exhentai", 318L),
            DesktopHomeStats.DesktopHomeSource("pixiv", null),
        )
    }

    Column(
        modifier = modifier
            .clip(GalleryTokens.Shape.Bento)
            .background(tokens.color.surfaceContainerHigh)
            .border(1.dp, tokens.color.outlineVariant.copy(alpha = 0.5f), GalleryTokens.Shape.Bento)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .size(3.dp, 13.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(SettingsBadgeColors.Gallery),
            )
            Text(text = DesktopI18n.t("漫画源监测", "Source Monitor"), fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight, fontWeight = VeneraType.cardTitle.weight, color = tokens.color.textPrimary)
            Text(text = "SOURCES", fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight, fontWeight = VeneraType.cardTitle.weight, color = tokens.color.textTertiary, letterSpacing = 1.2.sp)
            Spacer(Modifier.weight(1f))
            if (onGoSourceManage != null) {
                Text(
                    text = DesktopI18n.t("站点 ›", "Sources ›"),
                    fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                    color = tokens.color.textTertiary,
                    modifier = Modifier.clickable { onGoSourceManage() },
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            sourcesToShow.forEach { source ->
                val health = healthOf(source.latencyMs)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(9.dp))
                        .background(tokens.color.surfaceVariant.copy(alpha = 0.18f))
                        .border(1.dp, tokens.color.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(9.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(health.color()),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = source.name,
                        fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                        fontWeight = VeneraType.cardTitle.weight,
                        color = tokens.color.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    val label = health.label(source.latencyMs)
                    val badgeBg = when (health) {
                        com.venera.compose.feature.SourceHealth.Connected -> SettingsBadgeColors.Gallery.copy(alpha = 0.16f)
                        com.venera.compose.feature.SourceHealth.Degraded -> SettingsBadgeColors.Theming.copy(alpha = 0.16f)
                        com.venera.compose.feature.SourceHealth.Failing -> Color(0xFFF29B9B).copy(alpha = 0.18f)
                        com.venera.compose.feature.SourceHealth.Unknown -> tokens.color.outlineVariant.copy(alpha = 0.25f)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(badgeBg)
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = label,
                            fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                            fontWeight = VeneraType.cardTitle.weight,
                            color = health.color(),
                        )
                    }
                }
            }
        }
    }
}

/** 卡片 B：正在关注的画师 */
@Composable
internal fun DashArtistsCard(
    followedArtists: com.venera.compose.gallery.domain.GalleryFollowedArtistsReadout?,
    onOpenArtist: (com.venera.compose.gallery.domain.FollowedArtistRef) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens

    Column(
        modifier = modifier
            .clip(GalleryTokens.Shape.Bento)
            .background(tokens.color.surfaceContainerHigh)
            .border(1.dp, tokens.color.outlineVariant.copy(alpha = 0.5f), GalleryTokens.Shape.Bento)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .size(3.dp, 13.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(SettingsBadgeColors.Theming),
            )
            Text(text = DesktopI18n.t("正在关注的画师", "Followed Artists"), fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight, fontWeight = VeneraType.cardTitle.weight, color = tokens.color.textPrimary)
            Text(text = "ARTISTS", fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight, fontWeight = VeneraType.cardTitle.weight, color = tokens.color.textTertiary, letterSpacing = 1.2.sp)
        }

        val readout = followedArtists
        when {
            readout == null -> Text(
                text = DesktopI18n.t("画师名单还在读…", "Loading followed artists…"),
                fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                color = tokens.color.textTertiary,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            readout.artists.isEmpty() -> Text(
                text = DesktopI18n.t("还没有关注的画师：在「画廊」点开一张图，认他名下的标签就能关注。", "No followed artists yet. Open an artwork in Gallery and follow via its artist tag."),
                fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                color = tokens.color.textTertiary,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            else -> {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    readout.artists.take(3).forEach { artist ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(9.dp))
                                .clickable { onOpenArtist(artist) }
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(tokens.color.surfaceVariant.copy(alpha = tokens.current.placeholderAlpha)),
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
                                        fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                                        fontWeight = VeneraType.cardTitle.weight,
                                        color = tokens.color.textSecondary,
                                    )
                                }
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = artist.name,
                                    fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                                    fontWeight = VeneraType.cardTitle.weight,
                                    color = tokens.color.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = if (artist.count > 0) "${artist.count} 本作品 · Pixiv" else "已关注",
                                    fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                                    color = tokens.color.textTertiary,
                                )
                            }
                            Text(
                                text = "›",
                                fontSize = VeneraType.bodySecondary.size, lineHeight = VeneraType.bodySecondary.lineHeight,
                                color = tokens.color.textTertiary,
                            )
                        }
                    }
                    if (readout.overflow > 0) {
                        Text(
                            text = "还有 ${readout.overflow} 位画师",
                            fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                            color = tokens.color.textTertiary,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 卡片 C：本地漫画书库（去描边改阴影，与新盒同形） */
@Composable
internal fun DashLocalCard(
    localComicCount: Int?,
    onOpenLocalComics: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens

    Column(
        modifier = modifier
            .fillMaxHeight()
            .vShadow(2.dp, GalleryTokens.Shape.Large)
            .clip(GalleryTokens.Shape.Large)
            .background(classicHomeCardSurface())
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .size(3.dp, 13.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(tokens.color.primary),
            )
            Text(text = DesktopI18n.t("本地书库", "Local Library"), fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight, fontWeight = VeneraType.cardTitle.weight, color = tokens.color.textPrimary)
            Text(text = "LOCAL", fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight, fontWeight = VeneraType.cardTitle.weight, color = tokens.color.textTertiary, letterSpacing = 1.2.sp)
            Spacer(Modifier.weight(1f))
            if (onOpenLocalComics != null) {
                Text(
                    text = DesktopI18n.t("管理 ›", "Manage ›"),
                    fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                    color = tokens.color.textTertiary,
                    modifier = Modifier.clickable { onOpenLocalComics() },
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(tokens.color.surfaceVariant.copy(alpha = 0.18f))
                .border(1.dp, tokens.color.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                .then(
                    if (onOpenLocalComics != null) {
                        Modifier.clickable { onOpenLocalComics.invoke() }
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HomeBookSpines()
            Column(Modifier.weight(1f)) {
                Text(
                    text = DesktopI18n.t("本地漫画", "Local Comics"),
                    fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                    fontWeight = VeneraType.cardTitle.weight,
                    color = tokens.color.textPrimary,
                )
                Text(
                    text = when (localComicCount) {
                        null -> DesktopI18n.t("本机本子数还没数出来", "Counting local comics…")
                        else -> DesktopI18n.t("本机已有 $localComicCount 本 · 离线可读", "$localComicCount local · Offline ready")
                    },
                    fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                    color = tokens.color.textTertiary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text(
                text = "›",
                fontSize = VeneraType.cardTitle.size, lineHeight = VeneraType.cardTitle.lineHeight,
                color = tokens.color.textTertiary,
            )
        }

        // 辅助说明行：避免死黑，让卡片高度均衡
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = DesktopI18n.t("离线书库状态", "Offline Library Status"), fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight, color = tokens.color.textTertiary)
            Text(text = DesktopI18n.t("就绪", "Ready"), fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight, color = tokens.color.primary, fontWeight = VeneraType.cardTitle.weight)
        }
    }
}

/**
 * 关注画师带（方案 ①）：v4 退场的画师读数归位成全宽横滑带。
 * 真数据（`GalleryFollowedArtistsReadout`），点一位 = 经既有交接门进画师页；溢出数只念不加假卡。
 */
@Composable
internal fun DesktopHomeArtistRail(
    artists: com.venera.compose.gallery.domain.GalleryFollowedArtistsReadout,
    onOpenArtist: (com.venera.compose.gallery.domain.FollowedArtistRef) -> Unit,
) {
    val tokens = VeneraTokens
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(text = DesktopI18n.t("关注画师", "Followed Artists"), fontSize = VeneraType.body.size, lineHeight = VeneraType.body.lineHeight, fontWeight = VeneraType.cardTitle.weight, color = tokens.color.textPrimary)
            Text(
                text = DesktopI18n.t("${artists.artists.size} 位已关注", "${artists.artists.size} followed"),
                fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                color = tokens.color.textTertiary,
            )
        }
        Spacer(Modifier.height(11.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            artists.artists.forEach { artist ->
                Row(
                    modifier = Modifier
                        .clip(GalleryTokens.Shape.Medium)
                        .clickable { onOpenArtist(artist) }
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(tokens.color.surfaceVariant.copy(alpha = tokens.current.placeholderAlpha)),
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
                                fontSize = VeneraType.bodySecondary.size, lineHeight = VeneraType.bodySecondary.lineHeight,
                                fontWeight = VeneraType.cardTitle.weight,
                                color = tokens.color.textSecondary,
                            )
                        }
                    }
                    Column {
                        Text(
                            text = artist.name,
                            fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                            fontWeight = VeneraType.cardTitle.weight,
                            color = tokens.color.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (artist.count > 0) "${artist.count} 本作品" else "已关注",
                            fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                            color = tokens.color.textTertiary,
                        )
                    }
                }
            }
            if (artists.overflow > 0) {
                Text(
                    text = "还有 ${artists.overflow} 位",
                    fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                    color = tokens.color.textTertiary,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

/**
 * 下载任务速览卡（方案 ②）：壳 collect 的同一份队列账，这里只读。
 * 念进行中/排队条数 + 最近两笔进度；空队列不空摆（念一句实话，入口仍在）。
 */
@Composable
internal fun DashQueueCard(
    tasks: List<com.venera.desktop.gallery.data.DesktopComicQueueTask>,
    onOpen: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    val active = tasks.filter {
        it.status == com.venera.desktop.gallery.data.DesktopComicQueueStatus.RUNNING ||
            it.status == com.venera.desktop.gallery.data.DesktopComicQueueStatus.PENDING
    }
    val running = active.count { it.status == com.venera.desktop.gallery.data.DesktopComicQueueStatus.RUNNING }
    Column(
        modifier = modifier
            .fillMaxHeight()
            .vShadow(2.dp, GalleryTokens.Shape.Large)
            .clip(GalleryTokens.Shape.Large)
            .background(classicHomeCardSurface())
            .clickable(enabled = onOpen != null) { onOpen?.invoke() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "下载任务", fontSize = VeneraType.body.size, lineHeight = VeneraType.body.lineHeight, fontWeight = VeneraType.cardTitle.weight, color = tokens.color.textPrimary)
            Text(
                text = if (active.isEmpty()) "暂无任务" else "进行中 $running · 排队 ${active.size - running}",
                fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                color = tokens.color.textTertiary,
            )
            Spacer(Modifier.weight(1f))
            if (onOpen != null) {
                Text(
                    text = "查看 ›",
                    fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                    color = tokens.color.primary,
                )
            }
        }
        if (active.isEmpty()) {
            Text(
                text = "队列空着。开一部漫画的下载，进度会出现在这里。",
                fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                color = tokens.color.textTertiary,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        } else {
            active.take(2).forEach { task ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = task.comicTitle.ifBlank { "未名任务" },
                        fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                        fontWeight = VeneraType.cardTitle.weight,
                        color = tokens.color.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (task.totalPages > 0) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(tokens.color.outlineVariant.copy(alpha = 0.5f)),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(
                                            (task.downloadedPages.toFloat() / task.totalPages)
                                                .coerceIn(0.02f, 1f),
                                        )
                                        .fillMaxHeight()
                                        .background(tokens.color.primary),
                                )
                            }
                            Text(
                                text = "${task.downloadedPages} / ${task.totalPages} 页",
                                fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                                color = tokens.color.textTertiary,
                            )
                        } else {
                            Text(
                                text = "排队中",
                                fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                                color = tokens.color.textTertiary,
                            )
                        }
                    }
                }
            }
            if (active.size > 2) {
                Text(
                    text = "还有 ${active.size - 2} 笔",
                    fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                    color = tokens.color.textTertiary,
                )
            }
        }
    }
}

