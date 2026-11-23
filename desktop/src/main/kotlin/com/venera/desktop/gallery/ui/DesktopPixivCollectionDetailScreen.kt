package com.venera.desktop.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.components.LocalUxPlatformServices as UxServices
import com.venera.compose.gallery.data.GalleryPixivCollections
import com.venera.compose.gallery.data.PixivCollectionCard
import com.venera.compose.gallery.data.PixivCollectionDetail
import com.venera.compose.gallery.data.PixivWorkCard
import com.venera.compose.ui.tokens.VeneraCardEnter
import kotlinx.coroutines.launch

/**
 * 珍藏册详情页（左封面墙 + 右信息栏 380dp）。
 *
 * ## 数据口径（计划钉死，不许放宽）
 * - **描述块唯一来源 = 缩略卡 caption**（`detail.description`）：站方 `body.data` 无独立
 *   描述字段；空串渲染「暂无描述」，不编文字。
 * - **「TA的收藏」迷你墙只吃 [GalleryPixivCollections.myBookmarked]**（登录腿），语义 =
 *   该用户**收藏的**珍藏册（创建的珍藏册无端点）。匿名 / 无会话 failure ⇒ 整节隐藏，
 *   不摆错误态、不摆空墙；登录腿成功且空才摆「还没有收藏」。
 * - **相关珍藏册** = `recommend(collectionId)` 第一页（`nextIds` 截断，站方分页参数不用）。
 * - 关注钮 / 分享 / 评论：无真数据腿（契约里没有），不摆假把手。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DesktopPixivCollectionDetailScreen(
    collections: GalleryPixivCollections,
    /** 珍藏册 id：全链路 String（19–20 位数字串，禁 Long）。 */
    collectionId: String,
    /** 顶栏 / 标题条在详情到货前先念的名字（调用点带着走）。 */
    initialTitle: String,
    initialCover: String?,
    onBack: () -> Unit,
    /** 墙格点击 → 现有作品详情路由（壳接）。 */
    onOpenWork: (PixivWorkCard) -> Unit,
    onOpenCollection: (PixivCollectionCard) -> Unit,
    onOpenCreator: (PixivCollectionCard) -> Unit,
    /** 右栏「相关珍藏册」节头右上的落点（回发现页）。 */
    onOpenDiscover: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GalleryTokens.Color
    val scope = rememberCoroutineScope()
    val ux = UxServices.current

    var tick by remember { mutableIntStateOf(0) }
    val likedIds by collections.bookmarkedIds.collectAsState()

    // 首帧回填：本机收藏态是落盘快照，站方真态以 bookmarkState 为准（成功写穿）。
    // 匿名无会话时 failure 是常态，静默不念（收藏动作腿才 toast）。
    LaunchedEffect(collectionId) {
        collections.bookmarkState(collectionId)
    }

    var detail by remember(collectionId) {
        mutableStateOf<com.venera.compose.gallery.data.PixivCollectionDetail?>(null)
    }
    var detailFailed by remember(collectionId) { mutableStateOf<String?>(null) }

    LaunchedEffect(collectionId, tick) {
        detailFailed = null
        collections.detail(collectionId).fold(
            onSuccess = { detail = it },
            onFailure = { t ->
                detailFailed = "${t.message?.take(120) ?: t.javaClass.simpleName}"
            },
        )
    }

    // 相关珍藏册（第一页；nextIds 截断口径见接口注释）。null = 加载中，避免首帧闪空态。
    val related = produceState<List<PixivCollectionCard>?>(null, collectionId, tick) {
        value = null
        value = collections.recommend(collectionId).getOrNull().orEmpty()
    }

    val current = detail
    val toggleLike: () -> Unit = {
        scope.launch {
            val liked = collectionId in likedIds
            val result = if (liked) {
                collections.removeBookmark(collectionId)
            } else {
                collections.addBookmark(collectionId, private0 = false)
            }
            result.fold(
                onSuccess = { ux.toast(if (liked) "已取消收藏" else "已收藏") },
                onFailure = { t -> ux.toast("收藏没成功：${t.message?.take(80) ?: "网络或登录态问题"}") },
            )
        }
    }

    Row(modifier = modifier.fillMaxSize()) {
        // ── 左列：标题白卡条 + 懒加载封面墙（对齐官方样式：首行横幅 + 3 列竖卡）──
        val wallPlan = remember(current) {
            if (current == null) null else buildCollectionWallPlan(current)
        }
        // 作品页左右箭头要翻的那一列 = 这本珍藏册屏上的顺序（首行横幅在前，再逐行）。
        androidx.compose.runtime.LaunchedEffect(wallPlan) {
            com.venera.desktop.DesktopPixivWorkTrail.publishCollectionCards(
                listOfNotNull(wallPlan?.hero) + wallPlan?.rows?.flatten().orEmpty(),
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 860.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "header") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.SurfaceLevel1)
                            .border(1.dp, colors.OutlineHairline, RoundedCornerShape(16.dp))
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = current?.card?.title ?: initialTitle,
                            fontSize = 18.sp,
                            lineHeight = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.TextPrimary,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val creator = current?.card
                        if (creator != null) {
                            Row(
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .clip(CircleShape)
                                    .clickable { onOpenCreator(creator) }
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                            ) {
                                CollectionAvatar(
                                    url = creator.profileImageUrl.orEmpty(),
                                    name = creator.userName,
                                    size = 22.dp,
                                )
                                Text(
                                    text = creator.userName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                when {
                    detailFailed != null -> {
                        item(key = "failed") {
                            Column(
                                Modifier.fillMaxWidth().padding(vertical = 48.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = "加载失败",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.TextPrimary,
                                )
                                Text(
                                    text = detailFailed ?: "",
                                    fontSize = 12.sp,
                                    color = colors.TextTertiary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "重试",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.AccentPrimary,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable { tick++ }
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                    current == null || wallPlan == null -> {
                        item(key = "loading") {
                            Box(Modifier.fillMaxWidth().padding(vertical = 64.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = colors.AccentPrimary)
                            }
                        }
                    }
                    wallPlan.hero == null && wallPlan.rows.isEmpty() -> {
                        item(key = "empty") {
                            CollectionEmptyLine("这本珍藏册还没有作品")
                        }
                    }
                    else -> {
                        wallPlan.hero?.let { heroWork ->
                            item(key = "hero_${heroWork.id}") {
                                CollectionWallTile(
                                    work = heroWork,
                                    onOpen = { onOpenWork(heroWork) },
                                    aspectRatio = 2.2f,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        itemsIndexed(
                            items = wallPlan.rows,
                            key = { idx, row -> "row_${idx}_${row.firstOrNull()?.id ?: 0L}" },
                        ) { _, rowWorks ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                rowWorks.forEach { work ->
                                    CollectionWallTile(
                                        work = work,
                                        onOpen = { onOpenWork(work) },
                                        aspectRatio = 0.72f,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                repeat(3 - rowWorks.size) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        // ── 右栏 380dp：描述 / 标签 / 数据 / 日期 / 创建者+TA的收藏 / 相关珍藏册 ──
        Column(
            modifier = Modifier
                .width(380.dp)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(end = 24.dp, top = 16.dp, bottom = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(colors.SurfaceLevel1)
                .border(1.dp, colors.OutlineHairline, RoundedCornerShape(16.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (current == null) {
                Text(
                    text = initialTitle,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.TextPrimary,
                )
                if (detailFailed != null) {
                    Text(
                        text = "加载失败：${detailFailed ?: ""}",
                        fontSize = 13.sp,
                        color = colors.TextTertiary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "重试",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.AccentPrimary,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { tick++ }
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                } else {
                    Text(
                        text = "正在读取…",
                        fontSize = 13.sp,
                        color = colors.TextTertiary,
                    )
                }
            } else {
                // ① 描述块（= 卡上 caption；空串退空态，不编文字）
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = current.card.title,
                        fontSize = 20.sp,
                        lineHeight = 26.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp,
                        color = colors.TextPrimary,
                    )
                    val desc = current.description
                    Text(
                        text = desc.ifBlank { "暂无描述" },
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = if (desc.isBlank()) colors.TextTertiary else colors.TextSecondary,
                    )
                }

                // ② 标签行（流式；标签点击无真落点，不挂假把手）
                if (current.card.tags.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        current.card.tags.forEach { tag ->
                            Text(
                                text = "#$tag",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.TextSecondary,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(colors.SurfaceLevel2)
                                    .border(1.dp, colors.OutlineHairline, CircleShape)
                                    .padding(horizontal = 12.dp, vertical = 5.dp),
                            )
                        }
                    }
                }

                // ③ 数据行：心形 + 收藏数（分享 / 更多无真腿，不摆假钮）
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CollectionLikeButton(
                        liked = collectionId in likedIds,
                        count = current.card.bookmarkCount,
                        onToggle = toggleLike,
                    )
                }

                // ④ 日期行
                current.card.publishedDateTime?.takeIf { it.isNotBlank() }?.let { date ->
                    Text(
                        text = date,
                        fontSize = 12.sp,
                        color = colors.TextTertiary,
                    )
                }

                // ⑤ 创建者卡（点击进画师页）+ 下挂「TA的收藏」迷你墙（登录腿）
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onOpenCreator(current.card) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CollectionAvatar(
                            url = current.card.profileImageUrl.orEmpty(),
                            name = current.card.userName,
                            size = 40.dp,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = current.card.userName,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "创建者",
                                fontSize = 12.sp,
                                color = colors.TextTertiary,
                            )
                        }
                    }
                    if (current.creatorCollections.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "TA的珍藏册",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.TextSecondary,
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                current.creatorCollections.take(6).chunked(2).forEachIndexed { chunkIdx, pair ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        pair.forEachIndexed { i, card ->
                                            VeneraCardEnter(chunkIdx * 2 + i, card.id, Modifier.weight(1f)) {
                                                CollectionMiniCoverCard(
                                                    card = card,
                                                    onClick = { onOpenCollection(card) },
                                                )
                                            }
                                        }
                                        repeat(2 - pair.size) { Spacer(Modifier.weight(1f)) }
                                    }
                                }
                            }
                        }
                    }
                    CreatorBookmarksSection(
                        collections = collections,
                        creatorUserId = current.card.userId,
                        onOpenCollection = onOpenCollection,
                    )
                }

                // ⑥ 相关珍藏册（第一页；节头右上是回发现页的把手）
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "相关珍藏册",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.TextPrimary,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "发现珍藏册",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.AccentPrimary,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable(onClick = onOpenDiscover)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    val relatedCards = related.value
                    if (relatedCards == null) {
                        Text(
                            text = "正在读取…",
                            fontSize = 12.sp,
                            color = colors.TextTertiary,
                        )
                    } else if (relatedCards.isEmpty()) {
                        CollectionEmptyLine("暂无相关推荐")
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            relatedCards.chunked(2).forEachIndexed { chunkIdx, pair ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    pair.forEachIndexed { i, card ->
                                        VeneraCardEnter(chunkIdx * 2 + i, card.id, Modifier.weight(1f)) {
                                            CollectionMiniCoverCard(
                                                card = card,
                                                onClick = { onOpenCollection(card) },
                                            )
                                        }
                                    }
                                    repeat(2 - pair.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private class CollectionWallPlan(
    val hero: PixivWorkCard?,
    val rows: List<List<PixivWorkCard>>,
)

/**
 * 对齐官方样式：若首格在站方排版里是跨整行横幅（x=0, y=0 且占满最大列宽、宽大于高），
 * 首行单独铺横幅大图，其余作品按 3 列竖卡排布；否则全部按 3 列竖卡排布。
 */
private fun buildCollectionWallPlan(detail: PixivCollectionDetail): CollectionWallPlan {
    val workById = detail.works.associateBy { it.id }
    val orderedWorks = detail.tiles.mapNotNull { workById[it.workId] }.ifEmpty { detail.works }
    if (orderedWorks.isEmpty()) return CollectionWallPlan(null, emptyList())
    val maxCol = detail.tiles.maxOfOrNull { it.x + it.w } ?: 0
    val firstTile = detail.tiles.firstOrNull()
    val hasHeroBanner = firstTile != null &&
        maxCol > 0 &&
        firstTile.x == 0 &&
        firstTile.y == 0 &&
        firstTile.w >= maxCol &&
        firstTile.w > firstTile.h &&
        workById[firstTile.workId] != null
    return if (hasHeroBanner) {
        CollectionWallPlan(
            hero = orderedWorks.first(),
            rows = orderedWorks.drop(1).chunked(3),
        )
    } else {
        CollectionWallPlan(
            hero = null,
            rows = orderedWorks.chunked(3),
        )
    }
}

/**
 * 「TA的收藏」迷你墙（创建者卡下挂）。**登录腿门控钉死**：
 * failure（匿名 / 无会话）= 返回 null ⇒ 整节隐藏；成功且空才摆「还没有收藏」。
 */
@Composable
private fun CreatorBookmarksSection(
    collections: GalleryPixivCollections,
    creatorUserId: Long,
    onOpenCollection: (PixivCollectionCard) -> Unit,
) {
    val state = produceState<Result<com.venera.compose.gallery.data.PixivCollectionsSearchPage>?>(null, creatorUserId) {
        value = collections.myBookmarked(creatorUserId).fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(it) },
        )
    }
    val result = state.value ?: return
    val cards = result.getOrNull()?.cards ?: return // failure ⇒ 整节隐藏
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "TA的收藏",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = GalleryTokens.Color.TextSecondary,
        )
        if (cards.isEmpty()) {
            CollectionEmptyLine("还没有收藏")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                cards.take(6).chunked(2).forEachIndexed { chunkIdx, pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        pair.forEachIndexed { i, card ->
                            VeneraCardEnter(chunkIdx * 2 + i, card.id, Modifier.weight(1f)) {
                                CollectionMiniCoverCard(
                                    card = card,
                                    onClick = { onOpenCollection(card) },
                                )
                            }
                        }
                        repeat(2 - pair.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}
