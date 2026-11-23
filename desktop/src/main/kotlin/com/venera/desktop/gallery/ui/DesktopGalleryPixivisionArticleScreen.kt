package com.venera.desktop.gallery.ui

import com.venera.desktop.ui.theme.VeneraType
import com.venera.desktop.ui.DesktopPushButton
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.venera.compose.components.LocalUxPlatformServices
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.components.venera.VeneraTextButton
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.gallery.data.DesktopPixivisionArticle
import com.venera.desktop.gallery.data.DesktopPixivisionArticleBody
import com.venera.desktop.gallery.data.DesktopPixivisionRelatedItem
import com.venera.desktop.gallery.data.DesktopPixivisionWorkUnit

/**
 * pixivision 文章详情 · 二级页（按官方信息重做，两轮收小）。
 *
 * 官方结构（主人自供截图）：分类徽标 + 日期 + 大标题 + 粉色话题组 + 导读 +
 * 作品单元（**画师条在图上方** + 作品图 + 配文）+ 右栏「本月排行榜 / 推荐」。
 *
 * 作品图：宽封顶 480dp 居中、按官方 `width/height` 比例自撑高度（横图矮 / 竖图长），
 * 超长竖图高封顶 768dp 后 Fit 完整展现。收小的是宽度上限，不裁高度 ——
 * 压高度会把官方多数长竖图切坏。所有字段来自 [DesktopPixivisionArticleBody]，空 = 如实不摆。
 *
 * 保留全部功能入口：返回 / 复制链接 / 在站方打开 / 搜画师 / 站方作品 / 在站方继续阅读 /
 * 右栏条目跳同款二级页。
 */
private val ArticleImageMaxWidth = 480.dp
private val ArticleImageMaxHeight = 768.dp

@Composable
internal fun DesktopGalleryPixivisionArticleScreen(
    article: DesktopPixivisionArticle,
    body: DesktopPixivisionArticleBody?,
    loading: Boolean,
    onBack: () -> Unit,
    onSearchArtist: (String) -> Unit,
    onOpenWork: (String) -> Unit,
    onOpenSite: () -> Unit,
    /** 右栏「本月排行榜 / 推荐」条目：跳同款文章二级页（壳里换 article 并重新取正文）。 */
    onOpenRelated: (DesktopPixivisionRelatedItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val showSideRail = (com.venera.compose.components.LocalWindowWidth.current?.value ?: 1280f) >= 1280f

    Column(modifier = modifier.fillMaxSize().background(GalleryTokens.Color.Background)) {
        // 顶栏：返回 + 类别徽标 + 站方动作（既有，不动）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = tokens.spacing.screenHorizontal, vertical = tokens.spacing.space3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            VeneraTextButton(text = "← 返回", onClick = onBack)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(GalleryTokens.Color.AccentPrimary)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    text = article.category.ifBlank { "特辑" },
                    fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                    fontWeight = VeneraType.cardTitle.weight,
                    color = GalleryTokens.Color.TextOnAccent,
                )
            }
            Spacer(Modifier.weight(1f))
            VeneraTextButton(
                text = "复制链接",
                onClick = {
                    val sel = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                    sel.setContents(java.awt.datatransfer.StringSelection(article.url), null)
                },
            )
            DesktopPushButton(onClick = onOpenSite) { Text("在站方打开") }
        }

        Row(modifier = Modifier.fillMaxSize()) {
            // ── 主栏：官方正文（max 840dp） ──
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.TopCenter,
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 840.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = tokens.spacing.screenHorizontal,
                        end = tokens.spacing.screenHorizontal,
                        bottom = tokens.spacing.space8,
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = tokens.spacing.space3),
                        ) {
                            if (!body?.date.isNullOrBlank()) {
                                Text(
                                    text = body.date,
                                    fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                                    color = GalleryTokens.Color.TextTertiary,
                                )
                                Spacer(Modifier.height(tokens.spacing.space2))
                            }
                            Text(
                                text = article.title.ifBlank { body?.title.orEmpty() },
                                style = GalleryTokens.Typography.ArticleTitle,
                                modifier = Modifier.padding(bottom = tokens.spacing.space2),
                            )
                            if (!body?.topics.isNullOrEmpty()) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space8),
                                    modifier = Modifier.padding(bottom = tokens.spacing.space2),
                                ) {
                                    body.topics.take(12).forEach { topic ->
                                        Text(
                                            text = topic,
                                            fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                                            fontWeight = VeneraType.cardTitle.weight,
                                            color = GalleryTokens.Color.StatusFavorite,
                                        )
                                    }
                                }
                            }
                            if (!body?.lead.isNullOrBlank()) {
                                Spacer(Modifier.height(tokens.spacing.space3))
                                Text(
                                    text = body.lead,
                                    style = GalleryTokens.Typography.ArticleLead,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                    if (loading && body == null) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 96.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    color = GalleryTokens.Color.AccentPrimary,
                                )
                            }
                        }
                        return@LazyColumn
                    }
                    if (!loading && body == null) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "特辑正文加载失败，请检查代理或网络后重试，也可点击下方前往站方阅读",
                                    fontSize = VeneraType.body.size,
                                    lineHeight = VeneraType.body.lineHeight,
                                    color = tokens.color.textTertiary,
                                )
                            }
                        }
                    }
                    items(body?.works.orEmpty().size) { index ->
                        val work = body?.works?.getOrNull(index) ?: return@items
                        ArticleWorkUnit(
                            work = work,
                            tokens = tokens,
                            onSearchArtist = onSearchArtist,
                            onOpenWork = { url ->
                                onOpenWork(url)
                                uriHandler.openUri(url)
                            },
                        )
                    }
                    item {
                        Spacer(Modifier.height(tokens.spacing.space3))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            VeneraTextButton(text = "在站方继续阅读", onClick = onOpenSite)
                        }
                    }
                }
            }

            // ── 右栏：官方「本月排行榜」+「推荐」（≥1280dp 才摆，条目跳同款二级页） ──
            if (showSideRail && body != null &&
                (body.ranking.isNotEmpty() || body.related.isNotEmpty())
            ) {
                Column(
                    modifier = Modifier
                        .width(236.dp)
                        .fillMaxHeight()
                        .padding(
                            end = tokens.spacing.screenHorizontal,
                            top = tokens.spacing.space3,
                            bottom = tokens.spacing.space8,
                        ),
                    verticalArrangement = Arrangement.spacedBy(tokens.spacing.space4),
                ) {
                    if (body.ranking.isNotEmpty()) {
                        RelatedRailBlock(
                            title = "本月排行榜",
                            items = body.ranking,
                            numbered = true,
                            onOpen = onOpenRelated,
                        )
                    }
                    if (body.related.isNotEmpty()) {
                        RelatedRailBlock(
                            title = "推荐",
                            items = body.related,
                            numbered = false,
                            onOpen = onOpenRelated,
                        )
                    }
                }
            }
        }
    }
}

/** 一个作品单元：官方顺序 = 画师条（图上方）→ 图 → 配文。 */
@Composable
private fun ArticleWorkUnit(
    work: DesktopPixivisionWorkUnit,
    tokens: VeneraTokens,
    onSearchArtist: (String) -> Unit,
    onOpenWork: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = tokens.spacing.space6),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 画师条与图同宽（≤480dp），在官方页面里就位于图上方
        Row(
            modifier = Modifier
                .widthIn(max = ArticleImageMaxWidth)
                .fillMaxWidth()
                .padding(bottom = tokens.spacing.space2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space8),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(tokens.color.surfaceContainerHigh),
            ) {
                if (work.artistAvatarUrl.isNotBlank()) {
                    VeneraCover(
                        url = work.artistAvatarUrl,
                        contentDescription = work.artistName,
                        shimmerWhileLoading = false,
                        preserveAspectRatio = false,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = work.artistName,
                    fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                    fontWeight = VeneraType.cardTitle.weight,
                    color = GalleryTokens.Color.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (work.workTitle.isNotBlank()) {
                    Text(
                        text = work.workTitle,
                        fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                        color = GalleryTokens.Color.TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            VeneraTextButton(text = "搜画师", onClick = { onSearchArtist(work.artistName) })
            DesktopPushButton(onClick = { onOpenWork(work.workUrl) }) { Text("站方作品") }
        }

        // 作品图：限宽 480dp、按官方 width/height 比例自撑高度；超长封顶后 Fit 完整展现。
        // VeneraCover 内部写死 ContentScale.Crop + 固定 coverAspectRatio，做不到「按原图比例」，
        // 所以这一格用平台同一台 ImageLoader 直接 AsyncImage（共享件不动，安卓编译面零影响）。
        val loader = LocalUxPlatformServices.current.imageLoader()
        Box(
            modifier = Modifier
                .widthIn(max = ArticleImageMaxWidth)
                .fillMaxWidth()
                .heightIn(max = ArticleImageMaxHeight)
                .aspectRatio(work.aspectRatio.coerceIn(0.35f, 3f))
                .clip(GalleryTokens.Shape.Large)
                .background(GalleryTokens.Color.SurfaceLevel1)
                .border(
                    1.dp,
                    GalleryTokens.Color.OutlineHairline,
                    GalleryTokens.Shape.Large,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (work.imageUrl.isNotBlank()) {
                if (loader != null) {
                    AsyncImage(
                        model = work.imageUrl,
                        contentDescription = work.workTitle.ifBlank { work.artistName },
                        imageLoader = loader,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    AsyncImage(
                        model = work.imageUrl,
                        contentDescription = work.workTitle.ifBlank { work.artistName },
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        // 配文照站方原文，满宽（与窄图形成阅读节奏），空则不摆
        if (work.caption.isNotBlank()) {
            Spacer(Modifier.height(tokens.spacing.space2))
            Text(
                text = work.caption,
                style = GalleryTokens.Typography.ArticleLead,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 右栏一块：站方黄色标题条 + 带缩略图的条目列表，条目点击跳同款二级页。 */
@Composable
private fun RelatedRailBlock(
    title: String,
    items: List<DesktopPixivisionRelatedItem>,
    numbered: Boolean,
    onOpen: (DesktopPixivisionRelatedItem) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GalleryTokens.Shape.Small)
            .background(GalleryTokens.Color.SurfaceLevel2)
            .border(
                1.dp,
                GalleryTokens.Color.OutlineHairline,
                GalleryTokens.Shape.Small,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(androidx.compose.ui.graphics.Color(0xFFFFE500))
                .padding(horizontal = GalleryTokens.Spacing.Space8, vertical = GalleryTokens.Spacing.Space8),
        ) {
            Text(
                text = title,
                fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                fontWeight = VeneraType.cardTitle.weight,
                color = androidx.compose.ui.graphics.Color(0xFF1A1A1A),
            )
        }
        items.forEachIndexed { index, item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(GalleryTokens.Shape.Small)
                    .then(
                        if (item.url.isNotBlank()) {
                            Modifier.clickable { onOpen(item) }
                        } else {
                            Modifier
                        },
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (numbered) {
                    Text(
                        text = "${index + 1}",
                        fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                        fontWeight = VeneraType.cardTitle.weight,
                        color = GalleryTokens.Color.TextSecondary,
                        modifier = Modifier.width(14.dp),
                    )
                }
                if (item.coverUrl.isNotBlank()) {
                    VeneraCover(
                        url = item.coverUrl,
                        contentDescription = item.title,
                        shimmerWhileLoading = false,
                        preserveAspectRatio = false,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(GalleryTokens.Shape.Small),
                    )
                } else {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(GalleryTokens.Shape.Small)
                            .background(GalleryTokens.Color.SurfaceLevel3),
                    )
                }
                Text(
                    text = item.title,
                    fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                    color = GalleryTokens.Color.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
