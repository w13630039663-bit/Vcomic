package com.venera.desktop.gallery.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.components.LocalUxPlatformServices
import com.venera.compose.gallery.data.GalleryPixivCollections
import com.venera.compose.gallery.data.PixivCollectionCard
import com.venera.compose.ui.tokens.VeneraMotion
import kotlinx.coroutines.launch

/**
 * 珍藏册发现页（页签入口 + 简介条 + 筛选行 + 三 rail）。
 *
 * 屏只吃端口接口，不取静态口；数据三路：`top()`（推荐 / 大家的 / 标签推荐三节）、
 * `recommendedTags()`（筛选行）、选中非「全部」标签后改走 `search(tag, mode)`（单节刷新）。
 * 收藏态读 [GalleryPixivCollections.bookmarkedIds] 一帧；收藏 / 取消失败经 toast 念成因。
 */
@Composable
internal fun DesktopPixivCollectionsScreen(
    collections: GalleryPixivCollections,
    /** 页签「推荐」的落点 = 返回画廊 Tab（pixiv 域页签由壳接线）。 */
    onBack: () -> Unit,
    onOpenCollection: (PixivCollectionCard) -> Unit,
    onOpenCreator: (PixivCollectionCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GalleryTokens.Color
    val scope = rememberCoroutineScope()
    val ux = LocalUxPlatformServices.current

    var tick by remember { mutableIntStateOf(0) }
    var introVisible by remember { mutableStateOf(true) }
    var selectedTagIndex by remember { mutableIntStateOf(0) }

    val likedIds by collections.bookmarkedIds.collectAsState()

    // 三节成品（标签选中「全部」档）；选中具体标签后改走 search 单节。
    var top by remember { mutableStateOf<com.venera.compose.gallery.data.PixivCollectionsTop?>(null) }
    var topFailed by remember { mutableStateOf<String?>(null) }
    // 标签搜索档的成品（与 top 分账：换档不清另一份）。
    var searchCards by remember { mutableStateOf<List<PixivCollectionCard>>(emptyList()) }
    var searchFailed by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    val tags = produceState<List<String>>(emptyList(), tick) {
        value = collections.recommendedTags().getOrNull().orEmpty()
    }

    LaunchedEffect(tick, selectedTagIndex) {
        loading = true
        val tag = selectedTagIndex.takeIf { it > 0 }?.let { tags.value.getOrNull(it - 1) }
        if (tag == null) {
            topFailed = null
            collections.top().fold(
                onSuccess = {
                    top = it
                    topFailed = null
                },
                onFailure = { t ->
                    topFailed = "${t.message?.take(120) ?: t.javaClass.simpleName}"
                },
            )
        } else {
            searchCards = emptyList()
            searchFailed = null
            collections.search(tag, mode = "all").fold(
                onSuccess = {
                    searchCards = it.cards
                    searchFailed = null
                },
                onFailure = { t ->
                    searchCards = emptyList()
                    searchFailed = "${t.message?.take(120) ?: t.javaClass.simpleName}"
                },
            )
        }
        loading = false
    }

    val toggleLike: (PixivCollectionCard) -> Unit = { card ->
        scope.launch {
            val liked = card.id in likedIds
            val result = if (liked) collections.removeBookmark(card.id) else collections.addBookmark(card.id, private0 = false)
            result.fold(
                onSuccess = {
                    ux.toast(if (liked) "已取消收藏" else "已收藏")
                },
                onFailure = { t ->
                    ux.toast("收藏没成功：${t.message?.take(80) ?: "网络或登录态问题"}")
                },
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // ── 页题 + 返回（已由侧栏直达珍藏册，撤掉顶部「推荐 | 珍藏册」页签）──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!com.venera.desktop.LocalShellBackHandled.current) {
                com.venera.desktop.ui.DesktopPushButton(
                    onClick = onBack,
                    modifier = Modifier
                        .background(colors.SurfaceLevel1, androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "← 返回",
                        fontSize = 13.sp,
                        color = colors.TextSecondary,
                    )
                }
            }
            Text(
                text = "珍藏册",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = colors.TextPrimary,
            )
        }
        Text(
            text = "pixiv 测试版功能",
            fontSize = 12.sp,
            color = colors.TextTertiary,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(14.dp))

        // ── 简介条（可关；「功能说明」一句 15 字内）──
        if (introVisible) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "珍藏册",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.TextPrimary,
                )
                Text(
                    text = "测试版",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.TextOnAccent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.AccentPrimary)
                        .padding(horizontal = 7.dp, vertical = 1.dp),
                )
                Text(
                    text = "投稿、漫画、站外链接一页收纳",
                    fontSize = 12.sp,
                    color = colors.TextSecondary,
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "关闭",
                    tint = colors.TextTertiary,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable { introVisible = false }
                        .padding(6.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
        }

        // ── 筛选行（选标签即刷新；索引 0 = 全部）──
        CollectionFilterBar(
            tags = tags.value,
            selectedIndex = selectedTagIndex,
            onPick = { selectedTagIndex = it },
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(24.dp))

        // ── 内容：全部 = 三 rail；选中标签 = 单节（页签档 crossfade 200ms）──
        AnimatedContent(
            targetState = selectedTagIndex,
            transitionSpec = {
                fadeIn(tween(200, easing = VeneraMotion.easingStandard)) togetherWith
                    fadeOut(tween(200, easing = VeneraMotion.easingStandard))
            },
            label = "collectionsTabCrossfade",
        ) { tagIndex ->
            if (tagIndex == 0) {
                val current = top
                when {
                    loading && current == null -> {
                        Box(Modifier.fillMaxWidth().padding(vertical = 64.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = colors.AccentPrimary)
                        }
                    }
                    topFailed != null && current == null -> {
                        CollectionFailureBlock(
                            reason = topFailed ?: "",
                            onRetry = { tick++ },
                        )
                    }
                    current == null -> {
                        Box(Modifier.fillMaxWidth()) {}
                    }
                    current.recommend.isEmpty() && current.everyone.isEmpty() && current.tagRecommended.isEmpty() -> {
                        CollectionEmptyLine("暂无珍藏册")
                    }
                    else -> {
                        Column(verticalArrangement = Arrangement.spacedBy(40.dp)) {
                            CollectionRail(
                                title = "推荐珍藏册",
                                cards = current.recommend,
                                likedIds = likedIds,
                                onOpenCollection = onOpenCollection,
                                onOpenCreator = onOpenCreator,
                                onToggleLike = toggleLike,
                            )
                            CollectionRail(
                                title = "大家的珍藏册",
                                cards = current.everyone,
                                likedIds = likedIds,
                                onOpenCollection = onOpenCollection,
                                onOpenCreator = onOpenCreator,
                                onToggleLike = toggleLike,
                            )
                            current.tagRecommended.forEach { group ->
                                CollectionRail(
                                    title = "#${group.tag} 相关珍藏册",
                                    cards = group.cards,
                                    likedIds = likedIds,
                                    onOpenCollection = onOpenCollection,
                                    onOpenCreator = onOpenCreator,
                                    onToggleLike = toggleLike,
                                    hashTitle = true,
                                )
                            }
                        }
                    }
                }
            } else {
                val current = searchCards
                when {
                    loading && current.isEmpty() -> {
                        Box(Modifier.fillMaxWidth().padding(vertical = 64.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = colors.AccentPrimary)
                        }
                    }
                    searchFailed != null -> {
                        CollectionFailureBlock(
                            reason = searchFailed ?: "",
                            onRetry = { tick++ },
                        )
                    }
                    current.isEmpty() -> {
                        CollectionEmptyLine("这一标签暂无珍藏册")
                    }
                    else -> {
                        CollectionRail(
                            title = "#${tags.value.getOrNull(tagIndex - 1).orEmpty()} 相关珍藏册",
                            cards = current,
                            likedIds = likedIds,
                            onOpenCollection = onOpenCollection,
                            onOpenCreator = onOpenCreator,
                            onToggleLike = toggleLike,
                            hashTitle = true,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun CollectionFailureBlock(
    reason: String,
    onRetry: () -> Unit,
) {
    val colors = GalleryTokens.Color
    Column(
        Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "加载失败",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.TextPrimary,
        )
        if (reason.isNotBlank()) {
            Text(
                text = reason,
                fontSize = 12.sp,
                color = colors.TextTertiary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = "重试",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.AccentPrimary,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onRetry)
                .padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}

/**
 * 一节横向滚带：节头（标签节头带 # 前缀 accent）+ 260dp 卡 rail + 首载 stagger。
 * 「查看全部」没有真落点（无二级页路由），不摆假把手。
 */
@Composable
private fun CollectionRail(
    title: String,
    cards: List<PixivCollectionCard>,
    likedIds: Set<String>,
    onOpenCollection: (PixivCollectionCard) -> Unit,
    onOpenCreator: (PixivCollectionCard) -> Unit,
    onToggleLike: (PixivCollectionCard) -> Unit,
    hashTitle: Boolean = false,
) {
    if (cards.isEmpty()) return
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (hashTitle) {
                val head = title.substringBefore(' ')
                val rest = title.substringAfter(' ', missingDelimiterValue = "")
                Text(
                    text = head,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = GalleryTokens.Color.AccentPrimary,
                )
                if (rest.isNotEmpty()) {
                    Text(
                        text = " $rest",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = GalleryTokens.Color.TextPrimary,
                    )
                }
            } else {
                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = GalleryTokens.Color.TextPrimary,
                )
            }
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(cards, key = { _, card -> card.id }) { index, card ->
                Box(Modifier.then(CollectionMotion.railStagger(index))) {
                    CollectionCoverCard(
                        card = card,
                        liked = card.id in likedIds,
                        onClick = { onOpenCollection(card) },
                        onToggleLike = onToggleLike,
                        onOpenCreator = { onOpenCreator(card) },
                    )
                }
            }
        }
    }
}
