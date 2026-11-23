package com.venera.desktop.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.venera.compose.source.model.ComicDetails
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.gallery.ui.GalleryTokens
import com.venera.desktop.gallery.ui.LocalChromePalette
import com.venera.desktop.settings.DesktopI18n
import com.venera.desktop.ui.theme.VeneraType

/**
 * 漫画搜索结果右侧的速览栏：只承载信息，全部动作与章节留在完整详情页。
 *
 * @param open 是否展开；false 时整栏收起，展开只由点结果卡触发。
 * @param details 源详情；null = 没选中 / 还在取，此时不摆任何假数据。
 * @param thumbnails 预览图地址；详情核分页攒的那批与详情自带那批取其一。
 * @param resolvedThumbnails 预览图原 url → 源声明的可加载 url；缺项按原样请求。
 * @param loading 详情是否正在取；true 时摆骨架读数，不闪回占位。
 * @param sourceLabel 当前源显示名。
 * @param onToggle 收起速览栏。
 * @param onOpenFullDetail 进二级详情页（原路由不变）。
 * @param onOpenRecommend 点相似作品换本；由壳改成新的速览选中本。
 */
@Composable
internal fun DesktopComicPeekPane(
    open: Boolean,
    details: ComicDetails?,
    thumbnails: List<String>,
    resolvedThumbnails: Map<String, String>,
    loading: Boolean,
    sourceLabel: String,
    onToggle: () -> Unit,
    onOpenFullDetail: () -> Unit,
    onOpenRecommend: (id: String, title: String, cover: String) -> Unit,
) {
    val tokens = VeneraTokens
    val palette = LocalChromePalette.current
    val expandSpec = spring<androidx.compose.ui.unit.IntSize>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMedium,
    )
    val collapseSpec = tween<androidx.compose.ui.unit.IntSize>(durationMillis = 260)

    AnimatedVisibility(
        visible = open,
        enter = expandHorizontally(animationSpec = expandSpec) + fadeIn(tween(180)),
        exit = shrinkHorizontally(animationSpec = collapseSpec) + fadeOut(tween(120)),
    ) {
        Column(
            modifier = Modifier
                .width(380.dp)
                .fillMaxHeight()
                .padding(start = 18.dp)
                .border(width = 1.dp, color = GalleryTokens.Color.OutlineHairline, shape = GalleryTokens.Shape.Large)
                .clip(GalleryTokens.Shape.Large)
                .background(palette.elevated),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = DesktopI18n.t("作品速览", "Comic Preview"),
                    fontSize = VeneraType.cardTitle.size, lineHeight = VeneraType.cardTitle.lineHeight,
                    fontWeight = VeneraType.cardTitle.weight,
                    color = tokens.color.textPrimary,
                )
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(tokens.color.surfaceVariant.copy(alpha = 0.6f))
                        .clickable(onClick = onToggle),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = DesktopI18n.t("收起", "Collapse"),
                        tint = tokens.color.textSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            when {
                details == null -> PeekPlaceholder(loading, Modifier.weight(1f))
                else -> PeekBody(
                    details = details,
                    thumbnails = thumbnails,
                    resolvedThumbnails = resolvedThumbnails,
                    loading = loading,
                    sourceLabel = sourceLabel,
                    onOpenFullDetail = onOpenFullDetail,
                    onOpenRecommend = onOpenRecommend,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 没选中或还在取详情时不摆假卡：只念一句成因。 */
@Composable
private fun PeekPlaceholder(loading: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (loading) DesktopI18n.t("正在读取详情…", "Loading details…") else DesktopI18n.t("选择作品", "Select a comic"),
            fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
            color = VeneraTokens.color.textTertiary,
        )
    }
}

/** 速览正文：封面 banner / 读数 / 四张预览 / 标签 / 简介 / 相似作品。 */
@Composable
private fun PeekBody(
    details: ComicDetails,
    thumbnails: List<String>,
    resolvedThumbnails: Map<String, String>,
    loading: Boolean,
    sourceLabel: String,
    onOpenFullDetail: () -> Unit,
    onOpenRecommend: (id: String, title: String, cover: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    val comic = details.comic
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        PeekBanner(details, sourceLabel)
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tokens.color.primary)
                .clickable(onClick = onOpenFullDetail)
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = DesktopI18n.t("完整详情", "Full Details"),
                fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                fontWeight = VeneraType.cardTitle.weight,
                color = tokens.color.onPrimary,
            )
        }
        PeekMetaRow(details)
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PeekPreviews(thumbnails.take(4), resolvedThumbnails)
            PeekTags(details.tagMap.values.flatten().distinct())
            if (comic.description.isNotBlank()) PeekDescription(comic.description)
            // 源不报 recommend 时整块不摆，不生成假卡。
            if (details.recommend.isNotEmpty()) {
                PeekSection(DesktopI18n.t("相似作品", "Similar Comics")) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        details.recommend.take(4).forEach { rec ->
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onOpenRecommend(rec.id, rec.title, rec.cover) },
                            ) {
                                AsyncImage(
                                    model = rec.cover,
                                    contentDescription = rec.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(0.72f)
                                        .clip(RoundedCornerShape(4.dp))
                                        .drawBehind {
                                            drawRect(Color.White.copy(alpha = 0.10f))
                                        },
                                )
                                Text(
                                    text = rec.title,
                                    fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                                    color = tokens.color.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 5.dp),
                                )
                            }
                        }
                    }
                }
            }
            if (loading) {
                Text(
                    text = DesktopI18n.t("详情更新中…", "Updating details…"),
                    fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                    color = tokens.color.textTertiary,
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** 顶部识别区：真封面放大作环境底，压一层单色相渐变保证文字可读，右侧立同比例主封面。 */
@Composable
private fun PeekBanner(details: ComicDetails, sourceLabel: String) {
    val tokens = VeneraTokens
    val comic = details.comic
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
    ) {
        AsyncImage(
            model = comic.cover,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(28.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            tokens.color.primary.copy(alpha = 0.22f),
                            tokens.color.background.copy(alpha = 0.42f),
                            tokens.color.background,
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.62f)),
                    ),
                ),
        )
        Row(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.62f))
                        .border(1.dp, GalleryTokens.Color.OutlineHairline, RoundedCornerShape(6.dp))
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(RoundedCornerShape(50))
                                .background(tokens.color.primary),
                        )
                        Text(
                            text = sourceLabel,
                            fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                            color = tokens.color.textPrimary,
                        )
                    }
                }
                Text(
                    text = comic.title,
                    fontSize = 18.sp, lineHeight = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = tokens.color.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (details.author.isNotBlank()) {
                    Text(
                        text = DesktopI18n.t("作者：", "Author: ") + details.author,
                        fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                        color = tokens.color.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            AsyncImage(
                model = comic.cover,
                contentDescription = comic.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(118.dp)
                    .height(158.dp)
                    .align(Alignment.CenterVertically)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(8.dp)),
            )
        }
    }
}

/** 章节数、评分、更新时间都只作读数，不摆成可点动作；缺的档位不摆。 */
@Composable
private fun PeekMetaRow(details: ComicDetails) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = DesktopI18n.t("章节 ${details.chapters.size}", "${details.chapters.size} chapters"),
            fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
            color = VeneraTokens.color.textSecondary,
        )
        if (details.rating > 0f) {
            Box(Modifier.size(1.dp, 12.dp).background(GalleryTokens.Color.Divider))
            Text(
                text = DesktopI18n.t("评分 ${details.rating}", "Rating ${details.rating}"),
                fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                color = VeneraTokens.color.textSecondary,
            )
        }
        if (details.updateTime.isNotBlank()) {
            Box(Modifier.size(1.dp, 12.dp).background(GalleryTokens.Color.Divider))
            Text(
                text = DesktopI18n.t("${details.updateTime} 更新", "Updated ${details.updateTime}"),
                fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                color = VeneraTokens.color.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 四张真预览图；源不报预览图时整块不摆。 */
@Composable
private fun PeekPreviews(thumbnails: List<String>, resolved: Map<String, String>) {
    if (thumbnails.isEmpty()) return
    PeekSection(DesktopI18n.t("预览", "Preview")) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            thumbnails.forEachIndexed { index, url ->
                Box(modifier = Modifier.weight(1f)) {
                    AsyncImage(
                        model = resolved[url] ?: url,
                        contentDescription = DesktopI18n.t("预览 ${index + 1}", "Preview ${index + 1}"),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.72f)
                            .clip(RoundedCornerShape(4.dp))
                            .drawBehind { drawRect(Color.White.copy(alpha = 0.10f)) },
                    )
                    Text(
                        text = "${index + 1}",
                        fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(alpha = 0.62f))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}

/** 标签分「分类 / 分级」两层：分类 chip 带强调竖条，限制级走警示色族。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PeekTags(tags: List<String>) {
    if (tags.isEmpty()) return
    val limited = { t: String -> t.startsWith("R18") || t.contains("限制") || t.contains("成人") }
    val categories = tags.filterNot(limited)
    val ratings = tags.filter(limited)
    PeekSection(DesktopI18n.t("标签", "Tags")) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (categories.isNotEmpty()) {
                PeekTagGroup(DesktopI18n.t("分类", "Categories")) {
                    categories.take(6).forEach { tag ->
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(VeneraTokens.color.surfaceVariant.copy(alpha = 0.6f))
                                .border(1.dp, GalleryTokens.Color.OutlineHairline, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(3.dp, 12.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(VeneraTokens.color.primary),
                            )
                            Text(
                                text = tag,
                                fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                                color = VeneraTokens.color.textPrimary,
                                maxLines = 1,
                            )
                        }
                    }
                    if (categories.size > 6) {
                        Text(
                            text = "+${categories.size - 6}",
                            fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                            color = VeneraTokens.color.textTertiary,
                        )
                    }
                }
            }
            if (ratings.isNotEmpty()) {
                PeekTagGroup(DesktopI18n.t("分级", "Rating")) {
                    ratings.forEach { tag ->
                        Text(
                            text = tag,
                            fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                            color = VeneraTokens.color.textSecondary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(VeneraTokens.color.surfaceVariant.copy(alpha = 0.6f))
                                .border(1.dp, GalleryTokens.Color.OutlineHairline, RoundedCornerShape(6.dp))
                                .padding(horizontal = 9.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 一组标签：11px 组名 + 换行 chip，组间不共用一条细线。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PeekTagGroup(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
            color = VeneraTokens.color.textTertiary,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = { content() },
        )
    }
}

/** 简介分「首句引导 / 正文」两层；源不报简介时整块不摆。 */
@Composable
private fun PeekDescription(description: String) {
    val firstSentenceEnd = description.indexOfFirst { it == '。' || it == '！' || it == '？' || it == '\n' }
    val lead = if (firstSentenceEnd >= 0) description.substring(0, firstSentenceEnd + 1) else description
    val rest = if (firstSentenceEnd >= 0) description.substring(firstSentenceEnd + 1).trim() else ""
    PeekSection(DesktopI18n.t("简介", "Description")) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = lead,
                fontSize = VeneraType.aux.size, lineHeight = 22.sp,
                fontWeight = VeneraType.cardTitle.weight,
                color = VeneraTokens.color.textPrimary,
            )
            if (rest.isNotBlank()) {
                Text(
                    text = rest,
                    fontSize = VeneraType.aux.size, lineHeight = 22.sp,
                    color = VeneraTokens.color.textSecondary,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 速览里的一节：独立面 + 12px 标题，替掉原来一条细分隔线。 */
@Composable
private fun PeekSection(title: String, content: @Composable () -> Unit) {
    val palette = LocalChromePalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(palette.card)
            .border(1.dp, GalleryTokens.Color.OutlineHairline, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
            fontWeight = VeneraType.cardTitle.weight,
            color = VeneraTokens.color.textSecondary,
        )
        content()
    }
}

/**
 * 壳装配点与速览栏之间的一层薄壳：把 holder 状态与详情读数收成形参，
 * 让壳那颗大 Composable lambda 不必直接捕获速览栏的十来个变量。
 *
 * @param state 速览选中态（holder）。
 * @param core 速览专用详情核；null = 装配没接上读数，此时不摆假数据。
 * @param sourceLabel 当前源显示名。
 * @param onStateChange 写回 holder（展开收起、换本都走这一口）。
 * @param onDateLoaded 按作品 id 记一枚真更新日期；由壳在最新 state 上合并，避免用旧快照回盖。
 * @param onOpenFullDetail 进二级详情页，三枚参数依次为 id / 标题 / 封面。
 */
@Composable
internal fun DesktopSearchPeekSlot(
    state: DesktopPeekUiState,
    core: com.venera.compose.feature.ComicDetailCore?,
    sourceLabel: String,
    onStateChange: (DesktopPeekUiState) -> Unit,
    onDateLoaded: (String, String) -> Unit = { _, _ -> },
    onOpenFullDetail: (String, String, String) -> Unit,
) {
    val uiState = core?.uiState?.collectAsState()?.value
    val thumbnails = uiState?.thumbnails.orEmpty().ifEmpty { uiState?.details?.thumbnails.orEmpty() }
    // 搜索结果不带时间，日期只在详情页；取到后按 id 回灌，让那一张行卡能摆日期徽标。
    // 详情核按 comicId 装载，换本时旧详情会先留一帧，故只记当前 id、由壳按最新 state 合并。
    val id = state.comicId
    val time = uiState?.details?.updateTime.orEmpty()
    LaunchedEffect(id, time) {
        if (id == null || time.isBlank() || state.updateTimes[id] == time) return@LaunchedEffect
        onDateLoaded(id, time)
    }
    // 先算源侧取图配置：内置表外的图床缺防盗头，预览会整排空白。
    LaunchedEffect(thumbnails, core) {
        if (core == null || thumbnails.isEmpty()) return@LaunchedEffect
        core.ensureThumbnailConfigs(thumbnails.take(4))
    }
    DesktopComicPeekPane(
        open = state.open,
        details = uiState?.details,
        thumbnails = thumbnails,
        resolvedThumbnails = uiState?.thumbnailConfigs?.mapValues { it.value.url } ?: emptyMap(),
        loading = uiState?.isLoading == true,
        sourceLabel = sourceLabel,
        onToggle = { onStateChange(state.copy(open = false)) },
        onOpenFullDetail = {
            val id = state.comicId
            if (id != null) onOpenFullDetail(id, state.title, state.cover)
        },
        onOpenRecommend = { id, title, cover ->
            onStateChange(state.copy(comicId = id, title = title, cover = cover))
        },
    )
}

/**
 * 组装速览栏 slot。整段从壳顶层 lambda 抽出来：壳那颗 Composable 的捕获表已逼近
 * JVM 方法描述符 253 槽上限，局部 remember 与嵌套 lambda 每多一枚就可能在起窗时
 * 抛 ClassFormatError，而编译期完全不报。
 *
 * @param state 速览选中态 holder。
 * @param handle 业务端口句柄；null = 装配没接上读数，速览栏不摆假数据。
 * @param sourceKey 当前源键，`ComicItem.sourceName` 用它认回真键。
 * @param sourceLabel 当前源显示名，速览栏角标念这个。
 * @param onStateChange 写回 holder（展开收起、换本都走这一口）。
 * @param onDateLoaded 按作品 id 记一枚真更新日期；由壳在最新 state 上合并。
 * @param onOpenFullDetail 进二级详情页，三枚参数依次为 id / 标题 / 封面。
 * @return 供 `DesktopDiscoverScreen` 的 `peekPane` 使用的 Composable slot。
 */
@Composable
internal fun desktopSearchPeekPaneOf(
    state: DesktopPeekUiState,
    handle: com.venera.desktop.business.DesktopBusinessPorts.Handle?,
    sourceKey: String,
    sourceLabel: String,
    onStateChange: (DesktopPeekUiState) -> Unit,
    onDateLoaded: (String, String) -> Unit = { _, _ -> },
    onOpenFullDetail: (String, String, String) -> Unit,
): @Composable () -> Unit {
    val peekScope = androidx.compose.runtime.rememberCoroutineScope()
    val peekCore = androidx.compose.runtime.remember(handle) {
        handle?.let {
            com.venera.desktop.business.DesktopBusinessPorts.createDetailCore(it, peekScope)
        }
    }
    val activeSourceKey = state.sourceKey?.takeIf { it.isNotBlank() } ?: sourceKey
    val activeSourceLabel = state.sourceLabel?.takeIf { it.isNotBlank() } ?: sourceLabel
    androidx.compose.runtime.LaunchedEffect(state.comicId, activeSourceKey, peekCore) {
        val id = state.comicId
        if (id == null || peekCore == null) return@LaunchedEffect
        peekCore.load(
            com.venera.compose.feature.ComicItem(
                id = id,
                title = state.title,
                author = "",
                coverUrl = state.cover,
                sourceName = activeSourceKey,
            ),
        )
    }
    return {
        DesktopSearchPeekSlot(
            state = state,
            core = peekCore,
            sourceLabel = activeSourceLabel,
            onStateChange = onStateChange,
            onDateLoaded = onDateLoaded,
            onOpenFullDetail = onOpenFullDetail,
        )
    }
}
