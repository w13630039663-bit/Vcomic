package com.venera.desktop.gallery.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.venera.compose.components.LocalWindowWidth
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.compose.data.platform.PathProvider
import com.venera.compose.gallery.data.GalleryContentGuard
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.data.PixivRestrict
import com.venera.compose.gallery.data.PixivWork
import com.venera.compose.gallery.domain.GalleryArtistFollow
import com.venera.compose.gallery.domain.GalleryCardTitle
import com.venera.compose.gallery.domain.GalleryPreviewQuality
import com.venera.compose.gallery.domain.GalleryTitleTag
import com.venera.compose.ui.tokens.VeneraCardEnter
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.gallery.data.DesktopArtistInboxStore
import com.venera.desktop.gallery.data.DesktopGalleryPreferences
import com.venera.desktop.gallery.data.DesktopInboxGalleryFeed
import com.venera.desktop.gallery.data.DesktopInboxGallerySnapshot
import com.venera.desktop.gallery.data.GalleryInboxBatch
import com.venera.desktop.gallery.data.GalleryInboxFailureKind
import com.venera.desktop.ui.DesktopPushButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 关注画师元数据与近期聚合信息（画师筛选抽屉与画师视图行的共用行模型）。
 *
 * 两源分侧存（规格 §4「不合并存储，只在显示层合并同名」）：同名合并后
 * [gallerySites] 可跨多站、[pixivWorks] 与图库并存，量词按侧取（张/篇）。
 */
internal data class ArtistFeedGroup(
    val artistName: String,
    val avatarUrl: String,
    val galleryWorks: List<DesktopWallCardModel>,
    /** 图库侧覆盖的站别（同名合并后可多站；pixiv 侧无站徽标，不进这里）。 */
    val gallerySites: List<GallerySite>,
    val pixivWorks: List<DesktopWallCardModel>,
    val latestTimestamp: Long,
) {
    val works: List<DesktopWallCardModel> get() = galleryWorks + pixivWorks
}

/**
 * 关注画师收件箱主界面。
 *
 * 来源轴（全部/图库/pixiv）双源不混排：图库逐标签走 [DesktopInboxGalleryFeed]，
 * pixiv 走关注动态流；新作/旧作以落盘水位分侧，同步只取数不推水位。
 */
@Composable
internal fun DesktopArtistInboxScreen(
    mode: WideScreenLayoutMode,
    ports: GalleryPorts,
    paths: PathProvider?,
    prefs: DesktopGalleryPreferences?,
    guard: GalleryContentGuard?,
    maskMode: String,
    blockAi: Boolean,
    onOpenPost: (DesktopWallCardModel) -> Unit,
    onOpenArtist: ((DesktopArtistTarget) -> Unit)?,
    onBack: () -> Unit,
    onOpenPixivAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val inboxStore = remember(paths) { DesktopArtistInboxStore.getInstance(paths) }
    val persistedState by inboxStore.state.collectAsState()

    var activeSource by remember { mutableStateOf(ArtistFeedSource.ALL) }
    var viewMode by remember { mutableStateOf(InboxViewMode.TIME) }
    var showArtistRail by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedArtistFilter by remember { mutableStateOf<String?>(null) }
    var refreshTick by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var feedWorks by remember { mutableStateOf<List<PixivWork>>(emptyList()) }
    var feedError by remember { mutableStateOf<String?>(null) }
    var gallerySnapshot by remember { mutableStateOf<DesktopInboxGallerySnapshot?>(null) }
    var galleryTitleLabels by remember { mutableStateOf<Map<String, GalleryTitleTag>>(emptyMap()) }

    val follows by ports.follows.follows.collectAsState()
    val feed = remember(ports) { DesktopInboxGalleryFeed(ports.boards, ports.lexicon) }

    // pixiv 腿：bearer 取自账号件（账号没接时 pixiv 段缺席，不挡图库段）
    val pixiv = ports.pixiv
    var bearer by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(ports.pixivAccount) {
        bearer = withContext(Dispatchers.IO) { ports.pixivAccount?.bearerOrNull() }
    }

    // follows 入键：屏内新关注/取关画师后重取图库段，不要求手动点刷新
    LaunchedEffect(bearer, refreshTick, follows) {
        val token = bearer
        isLoading = true
        feedError = null
        if (pixiv != null && token != null) {
            val res = withContext(Dispatchers.IO) {
                pixiv.followIllustFeed(token, PixivRestrict.Public, fresh = refreshTick > 0)
            }
            res.onSuccess { page ->
                feedWorks = page.works
            }.onFailure { err ->
                feedError = err.message ?: "获取关注动态失败"
            }
        }
        // 图库腿：水位读落盘现值（不用闭包陈旧值），取数层只判不推
        val booruFollows = follows.filter { it.site != GallerySite.PIXIV }
        gallerySnapshot = withContext(Dispatchers.IO) {
            feed.fetch(booruFollows, inboxStore.state.value.galleryWatermarks)
        }
        // 标题词典：本轮图库全部标签名一次收齐；null（词典没读出）退空表
        val names = gallerySnapshot?.batches
            ?.flatMap { batch -> batch.posts.flatMap { it.tagList } }
            ?.map { it.lowercase() }
            ?.distinct()
            .orEmpty()
        galleryTitleLabels = if (names.isEmpty()) {
            emptyMap()
        } else {
            withContext(Dispatchers.IO) { ports.lexicon.titleTags(names) } ?: emptyMap()
        }
        // 每轮同步收尾：只写 lastSyncAt（水位落盘只发生在全部已读）
        inboxStore.recordSync()
        isLoading = false
    }

    val wallSpec = LocalDesktopWallSpec.current
    val quality = prefs?.galleryPreviewQuality?.collectAsState()?.value ?: GalleryPreviewQuality.PREVIEW

    // pixiv 卡流（守卫管线照旧）
    val pixivCards = remember(feedWorks, quality, guard, maskMode, blockAi) {
        val posts = feedWorks.map { it.post }
        val models = desktopWallFollowingCards(posts, quality, guard, maskMode, blockAi)
        val titleMap = feedWorks.associate { it.post.uid to it.title }
        models.map { model ->
            model.copy(
                title = titleMap[model.uid]?.takeIf { it.isNotBlank() } ?: model.title,
                author = model.sourcePost?.author ?: model.author,
            )
        }
    }

    // 图库卡流：逐批次过守卫管线，标题走词典档（null 退关注名），画师取关注标签名
    val galleryBatchCards = remember(gallerySnapshot, quality, guard, maskMode, blockAi, galleryTitleLabels) {
        gallerySnapshot?.batches?.map { batch ->
            inboxGalleryBatchCards(batch, galleryTitleLabels, quality, guard, maskMode, blockAi)
        }.orEmpty()
    }

    val galleryItems = remember(galleryBatchCards) { galleryBatchCards.flatMap { it.items } }
    val pixivItems = remember(pixivCards) {
        pixivCards.map { ArtistInboxCardItem(model = it, hideSourceBadge = true) }
    }

    val readUids = persistedState.readUids
    val galleryUnread = remember(galleryItems, readUids) {
        galleryItems.map { it.model.uid }.filter { it.isNotBlank() && it !in readUids }.toSet()
    }
    val pixivUnread = remember(pixivItems, readUids) {
        pixivItems.map { it.model.uid }.filter { it.isNotBlank() && it !in readUids }.toSet()
    }

    // 画师筛选只作用于卡面（图库按关注标签名、pixiv 按画师名）
    val visibleGalleryItems = remember(galleryItems, selectedArtistFilter) {
        if (selectedArtistFilter == null) galleryItems else galleryItems.filter { it.authorOverride == selectedArtistFilter }
    }
    val visiblePixivItems = remember(pixivItems, selectedArtistFilter) {
        if (selectedArtistFilter == null) pixivItems else pixivItems.filter { it.model.author == selectedArtistFilter }
    }

    // 画师筛选激活时的可见域未读：段头计数与「全部已读」都用它，与段头 label（过滤后张/篇数）同口径
    val visibleGalleryUnread = remember(visibleGalleryItems, readUids) {
        visibleGalleryItems.map { it.model.uid }.filter { it.isNotBlank() && it !in readUids }.toSet()
    }
    val visiblePixivUnread = remember(visiblePixivItems, readUids) {
        visiblePixivItems.map { it.model.uid }.filter { it.isNotBlank() && it !in readUids }.toSet()
    }

    val gallerySegment = remember(visibleGalleryItems, galleryBatchCards, visibleGalleryUnread) {
        if (visibleGalleryItems.isEmpty()) {
            null
        } else {
            val (newCards, oldCards) = splitGalleryByBatchWatermark(visibleGalleryItems, galleryBatchCards)
            ArtistInboxSegment(
                source = ArtistFeedSource.GALLERY,
                label = inboxSectionHeadLabel("图库", visibleGalleryItems.size, "张"),
                unreadCount = visibleGalleryUnread.size,
                newCards = newCards,
                oldCards = oldCards,
            )
        }
    }
    val pixivSegment = remember(visiblePixivItems, visiblePixivUnread, persistedState.pixivWatermarks) {
        if (visiblePixivItems.isEmpty()) {
            null
        } else {
            val (newCards, oldCards) = artistInboxSplitByArtistWatermark(visiblePixivItems, persistedState.pixivWatermarks)
            ArtistInboxSegment(
                source = ArtistFeedSource.PIXIV,
                label = inboxSectionHeadLabel("pixiv", visiblePixivItems.size, "篇"),
                unreadCount = visiblePixivUnread.size,
                newCards = newCards,
                oldCards = oldCards,
            )
        }
    }

    val entries = artistInboxEntries(activeSource, gallerySegment, pixivSegment, persistedState.lastReadTimestamp)

    val unreadCount = when (activeSource) {
        ArtistFeedSource.ALL -> galleryUnread.size + pixivUnread.size
        ArtistFeedSource.GALLERY -> galleryUnread.size
        ArtistFeedSource.PIXIV -> pixivUnread.size
    }

    // 候选水位：本轮每标签/每画师已见最大 id（落盘只发生在 markAllViewed，合并取 max）
    val galleryMax = remember(gallerySnapshot) { inboxGalleryWatermarkCandidates(gallerySnapshot?.batches.orEmpty()) }
    val pixivMax = remember(pixivItems) { artistInboxWatermarkCandidates(pixivItems) }

    // 屏面状态映射只认 code：FETCH 且 403 = 图库限流，其余失败 = 同步失败
    val rateLimited = gallerySnapshot?.failures.orEmpty().any {
        it.kind == GalleryInboxFailureKind.FETCH && it.code == 403
    }
    val hasFailures = gallerySnapshot?.failures.orEmpty().isNotEmpty() || feedError != null

    // 显示层同名合并后的组：抽屉与画师视图共用一张（duplicate key 靠合并消除）
    val artistGroups = remember(galleryBatchCards, pixivCards, feedWorks) {
        inboxDrawerGroups(galleryBatchCards, pixivCards, feedWorks)
    }
    // 画师视图行序（规格 §3.4）：图库行在前、pixiv 行在后，各按组内最新作品排
    val digestRows = remember(artistGroups) {
        artistGroups.sortedWith(
            compareByDescending<ArtistFeedGroup> { it.gallerySites.isNotEmpty() }
                .thenByDescending { it.latestTimestamp },
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {

            ArtistInboxTopBar(
                unreadCount = unreadCount,
                unreadLabel = inboxUnreadLabel(activeSource, unreadCount),
                viewMode = viewMode,
                onViewModeChange = { viewMode = it },
                showArtistRail = showArtistRail,
                onToggleArtistRail = { showArtistRail = !showArtistRail },
                onBack = onBack,
                onRefresh = { refreshTick++ },
            )

            ArtistInboxSourceBar(
                activeSource = activeSource,
                lastSyncAt = persistedState.lastSyncAt,
                galleryUnread = galleryUnread.size,
                pixivUnread = pixivUnread.size,
                onSelectSource = { activeSource = it },
                onMarkAllRead = {
                    scope.launch {
                        val uids = when (activeSource) {
                            ArtistFeedSource.ALL -> galleryUnread + pixivUnread
                            ArtistFeedSource.GALLERY -> galleryUnread
                            ArtistFeedSource.PIXIV -> pixivUnread
                        }
                        // 全部视图推进两源候选水位；单源只传本侧（markAllViewed 可空分侧）
                        val gallerySide = if (activeSource != ArtistFeedSource.PIXIV) galleryMax else null
                        val pixivSide = if (activeSource != ArtistFeedSource.GALLERY) pixivMax else null
                        inboxStore.markAllViewed(uids, gallerySide, pixivSide)
                    }
                },
            )

            Row(modifier = Modifier.fillMaxSize()) {

                AnimatedVisibility(
                    visible = showArtistRail,
                    enter = expandHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                    exit = shrinkHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut(),
                ) {
                    ArtistInboxDrawerRail(
                        groups = artistGroups,
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        selectedArtist = selectedArtistFilter,
                        onSelectArtist = { artist ->
                            selectedArtistFilter = if (selectedArtistFilter == artist) null else artist
                        },
                        readUids = readUids,
                        onClose = { showArtistRail = false },
                        modifier = Modifier
                            .width(240.dp)
                            .fillMaxHeight(),
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    when {
                        viewMode == InboxViewMode.ARTIST -> {
                            if (digestRows.isEmpty()) {
                                if (isLoading) {
                                    ArtistInboxEmptyView(
                                        title = "正在建立索引",
                                        subtitle = "首次同步需要一点时间",
                                        actionLabel = "刷新",
                                        onAction = { refreshTick++ },
                                    )
                                } else {
                                    ArtistInboxEmptyView(
                                        title = "暂无新作品",
                                        subtitle = "关注画师最近没有更新",
                                        actionLabel = "刷新",
                                        onAction = { refreshTick++ },
                                    )
                                }
                            } else {
                                ArtistInboxArtistView(
                                    rows = digestRows,
                                    readUids = readUids,
                                    onOpenArtist = { name ->
                                        selectedArtistFilter = name
                                        viewMode = InboxViewMode.TIME
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                        entries.isNotEmpty() -> {
                            val windowWidth = LocalWindowWidth.current ?: 1280.dp
                            val availableWidth = if (showArtistRail) (windowWidth - 240.dp) else windowWidth
                            val columns = wallSpec.columnsFor(
                                with(LocalDensity.current) { availableWidth.toPx() },
                                com.venera.compose.gallery.domain.GalleryColumnMode.AUTO,
                            ).coerceAtLeast(2)

                            Column(modifier = Modifier.fillMaxSize()) {
                                // 有货也要让失败/限流可见：全部视图 pixiv 有图时，图库腿挂掉不能无声吞掉
                                if (rateLimited || hasFailures) {
                                    ArtistInboxNoticeBar(
                                        text = if (rateLimited) "图库限流，部分来源未同步" else "部分来源同步失败",
                                    )
                                }
                                ArtistInboxGrid(
                                    entries = entries,
                                    readUids = readUids,
                                    dimRead = activeSource == ArtistFeedSource.ALL,
                                    showSectionActions = activeSource == ArtistFeedSource.ALL,
                                    columns = columns,
                                    spacing = wallSpec.wallGap,
                                    onOpenPost = { card ->
                                        if (card.uid.isNotBlank()) {
                                            scope.launch { inboxStore.markRead(card.uid) }
                                        }
                                        onOpenPost(card)
                                    },
                                    onToggleRead = { uid -> scope.launch { inboxStore.toggleRead(uid) } },
                                    onMarkSectionRead = { source ->
                                        scope.launch {
                                            when (source) {
                                                // 段内全部已读只作用当前视图的未读集合（筛选激活时同为过滤后口径）
                                                ArtistFeedSource.GALLERY -> {
                                                    val scopedBatches = galleryBatchCards
                                                        .filter { selectedArtistFilter == null || it.batch.name == selectedArtistFilter }
                                                        .map { it.batch }
                                                    inboxStore.markAllViewed(visibleGalleryUnread, inboxGalleryWatermarkCandidates(scopedBatches), null)
                                                }
                                                else -> inboxStore.markAllViewed(
                                                    visiblePixivUnread,
                                                    null,
                                                    artistInboxWatermarkCandidates(visiblePixivItems),
                                                )
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                )
                            }
                        }
                        isLoading -> {
                            ArtistInboxEmptyView(
                                title = "正在建立索引",
                                subtitle = "首次同步需要一点时间",
                                actionLabel = "刷新",
                                onAction = { refreshTick++ },
                            )
                        }
                        activeSource == ArtistFeedSource.PIXIV && bearer == null -> {
                            ArtistInboxLoginGuide(onLogin = onOpenPixivAccount)
                        }
                        rateLimited -> {
                            ArtistInboxEmptyView(
                                title = "图库限流",
                                // 退避（P2）未实现，自动重试不存在：文案不承诺没有的行为，重试由用户点刷新触发
                                subtitle = "稍后手动重试",
                                actionLabel = "刷新",
                                onAction = { refreshTick++ },
                            )
                        }
                        hasFailures -> {
                            ArtistInboxEmptyView(
                                title = "同步失败",
                                subtitle = "请检查网络后重试",
                                actionLabel = "刷新",
                                onAction = { refreshTick++ },
                            )
                        }
                        else -> {
                            ArtistInboxEmptyView(
                                title = "暂无新作品",
                                subtitle = "关注画师最近没有更新",
                                actionLabel = "刷新",
                                onAction = { refreshTick++ },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 单个图库批次的投卡产物：整段卡 + 按落盘水位分好侧的两组。 */
private data class InboxGalleryBatchCards(
    val batch: GalleryInboxBatch,
    val items: List<ArtistInboxCardItem>,
    val newItems: List<ArtistInboxCardItem>,
    val oldItems: List<ArtistInboxCardItem>,
)

/**
 * 一个关注标签的取数结果 → 卡与分侧。投影走 [desktopWallFollowingCards]（守卫管线唯一转交件）；
 * 标题走词典档（null 退关注名）；画师名取关注标签本身 —— moebooru/gelbooru 的
 * author 字段是上传者不是画师。
 */
private fun inboxGalleryBatchCards(
    batch: GalleryInboxBatch,
    labels: Map<String, GalleryTitleTag>,
    quality: GalleryPreviewQuality,
    guard: GalleryContentGuard?,
    maskMode: String,
    blockAi: Boolean,
): InboxGalleryBatchCards {
    val models = desktopWallFollowingCards(batch.posts, quality, guard, maskMode, blockAi)
    val modelByUid = models.associateBy { it.uid }
    fun toItems(posts: List<GalleryPost>): List<ArtistInboxCardItem> = posts.mapNotNull { post ->
        modelByUid[post.uid]?.let { model ->
            val title = GalleryCardTitle.titleOf(post.tagList, labels) ?: batch.name
            ArtistInboxCardItem(
                model = model.copy(title = title),
                authorOverride = batch.name,
            )
        }
    }
    return InboxGalleryBatchCards(
        batch = batch,
        items = toItems(batch.posts),
        newItems = toItems(batch.newPosts),
        oldItems = toItems(batch.oldPosts),
    )
}

/** 图库段分侧：分侧判据在 feed 层按批次水位判过，这里按卡的批次归属归位。 */
private fun splitGalleryByBatchWatermark(
    items: List<ArtistInboxCardItem>,
    batches: List<InboxGalleryBatchCards>,
): Pair<List<ArtistInboxCardItem>, List<ArtistInboxCardItem>> {
    val newUids = batches.flatMap { it.newItems }.mapTo(HashSet()) { it.model.uid }
    return items.partition { it.model.uid in newUids }
}

/** 图库候选水位：本轮每标签已见最大 post id（键 = 关注 uid，落盘前只是候选值）。 */
private fun inboxGalleryWatermarkCandidates(batches: List<GalleryInboxBatch>): Map<String, Long> = buildMap {
    batches.forEach { batch ->
        batch.posts.maxOfOrNull { it.id }?.takeIf { it > 0 }?.let {
            put(GalleryArtistFollow.uidOf(batch.site, batch.name), it)
        }
    }
}

/**
 * 画师抽屉与画师视图共用的分组：图库各标签一段 + pixiv 按画师名聚合，
 * **显示层合并同名**（规格 §4「不合并存储，只在显示层合并同名」）——
 * 同名不同站（yandere:kantoku + gelbooru:kantoku）或图库标签撞 pixiv 画师名时并成一组，
 * 否则抽屉列表按 `artistName` 做 LazyColumn key 会撞 duplicate key 直接崩。
 */
private fun inboxDrawerGroups(
    gallery: List<InboxGalleryBatchCards>,
    pixivCards: List<DesktopWallCardModel>,
    feedWorks: List<PixivWork>,
): List<ArtistFeedGroup> {
    val collected = buildList {
        gallery.forEach { batchCards ->
            add(
                ArtistFeedGroup(
                    artistName = batchCards.batch.name,
                    avatarUrl = "",
                    galleryWorks = batchCards.items.map { it.model },
                    gallerySites = listOf(batchCards.batch.site),
                    pixivWorks = emptyList(),
                    latestTimestamp = batchCards.items.maxOfOrNull { it.model.sourcePost?.id ?: 0L } ?: 0L,
                ),
            )
        }
        val avatarMap = feedWorks.associate { it.post.author to it.authorAvatarUrl }
        pixivCards.groupBy { it.author.ifBlank { "未知画师" } }.forEach { (author, cards) ->
            add(
                ArtistFeedGroup(
                    artistName = author,
                    avatarUrl = avatarMap[author] ?: cards.firstOrNull()?.url.orEmpty(),
                    galleryWorks = emptyList(),
                    gallerySites = emptyList(),
                    pixivWorks = cards,
                    latestTimestamp = cards.firstOrNull()?.sourcePost?.id ?: 0L,
                ),
            )
        }
    }
    return collected
        .groupBy { it.artistName }
        .map { (_, same) ->
            ArtistFeedGroup(
                artistName = same.first().artistName,
                avatarUrl = same.firstOrNull { it.avatarUrl.isNotBlank() }?.avatarUrl.orEmpty(),
                galleryWorks = same.flatMap { it.galleryWorks },
                gallerySites = same.flatMap { it.gallerySites }.distinct(),
                pixivWorks = same.flatMap { it.pixivWorks },
                latestTimestamp = same.maxOf { it.latestTimestamp },
            )
        }
}

/**
 * 顶栏：返回 + 标题 + 未读计数（分源量词）+ 「时间|画师」形式切换 + 刷新。
 */
@Composable
private fun ArtistInboxTopBar(
    unreadCount: Int,
    unreadLabel: String,
    viewMode: InboxViewMode,
    onViewModeChange: (InboxViewMode) -> Unit,
    showArtistRail: Boolean,
    onToggleArtistRail: () -> Unit,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    val tokens = VeneraTokens

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "返回",
                tint = tokens.color.textSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = "关注动态",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = tokens.color.textPrimary,
        )
        if (unreadCount > 0) {
            Text(
                text = unreadLabel,
                fontSize = 11.sp,
                color = DesktopTheme.AccentBeni,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        ArtistInboxViewModeToggle(
            viewMode = viewMode,
            onViewModeChange = onViewModeChange,
        )
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable(onClick = onToggleArtistRail),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.FilterList,
                contentDescription = "画师列表",
                tint = if (showArtistRail) tokens.color.primary else tokens.color.textSecondary,
                modifier = Modifier.size(16.dp),
            )
        }
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable(onClick = onRefresh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = "刷新",
                tint = tokens.color.textSecondary,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

/** 顶栏「时间|画师」形式切换的分段控件（规格 §2）。 */
@Composable
private fun ArtistInboxViewModeToggle(
    viewMode: InboxViewMode,
    onViewModeChange: (InboxViewMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(tokens.color.surfaceVariant.copy(alpha = 0.35f))
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        InboxViewMode.entries.forEach { mode ->
            val isSelected = mode == viewMode
            val bg by animateColorAsState(
                targetValue = if (isSelected) tokens.color.surface else Color.Transparent,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(bg)
                    .clickable { onViewModeChange(mode) }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = mode.label,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) tokens.color.textPrimary else tokens.color.textSecondary,
                )
            }
        }
    }
}

/** 内容区顶部失败/限流提示条（有货时全态不可见问题的那条补充渠道）。 */
@Composable
private fun ArtistInboxNoticeBar(
    text: String,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(tokens.color.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(DesktopTheme.AccentBeni),
        )
        Text(
            text = text,
            fontSize = 12.sp,
            color = tokens.color.textSecondary,
        )
    }
}

/**
 * 画师视图（规格 §3.4）：双源混排**行**（不混排作品），图库行在前、pixiv 行在后。
 */
@Composable
private fun ArtistInboxArtistView(
    rows: List<ArtistFeedGroup>,
    readUids: Set<String>,
    onOpenArtist: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        itemsIndexed(rows, key = { _, group -> group.artistName }) { index, group ->
            VeneraCardEnter(index, group.artistName) {
                ArtistDigestRow(
                    group = group,
                    readUids = readUids,
                    onClick = { onOpenArtist(group.artistName) },
                )
            }
        }
    }
}

/**
 * 单条画师行：首字母座/真头像 + 名字 + 站徽标 + 「N 张/N 篇未读」+ 4 缩略 + 余量块。
 * 同名合并组带多枚站徽标，量词按侧分开（图库张、pixiv 篇）。
 */
@Composable
private fun ArtistDigestRow(
    group: ArtistFeedGroup,
    readUids: Set<String>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    val galleryUnread = group.galleryWorks.count { it.uid.isNotBlank() && it.uid !in readUids }
    val pixivUnread = group.pixivWorks.count { it.uid.isNotBlank() && it.uid !in readUids }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(tokens.color.surfaceVariant.copy(alpha = 0.18f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(modifier = Modifier.size(32.dp)) {
            if (group.avatarUrl.isNotBlank()) {
                AsyncImage(
                    model = group.avatarUrl,
                    contentDescription = group.artistName,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(tokens.color.primary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = group.artistName.take(1),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = tokens.color.primary,
                    )
                }
            }
        }

        Column(modifier = Modifier.width(200.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = group.artistName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.color.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                group.gallerySites.forEach { site ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = 0.07f))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    ) {
                        Text(
                            text = site.displayName,
                            fontSize = 9.sp,
                            color = tokens.color.textTertiary,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.padding(top = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (galleryUnread > 0) {
                    ArtistDigestUnreadPill(text = "${galleryUnread} 张未读")
                }
                if (pixivUnread > 0) {
                    ArtistDigestUnreadPill(text = "${pixivUnread} 篇未读")
                }
                if (galleryUnread == 0 && pixivUnread == 0) {
                    Text(
                        text = "没有未读",
                        fontSize = 11.sp,
                        color = tokens.color.textTertiary,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            group.works.take(4).forEach { work ->
                AsyncImage(
                    model = work.url,
                    contentDescription = null,
                    modifier = Modifier
                        .size(width = 64.dp, height = 64.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(tokens.color.surfaceVariant.copy(alpha = 0.4f)),
                )
            }
            val remainder = group.works.size - 4
            if (remainder > 0) {
                Box(
                    modifier = Modifier
                        .size(width = 40.dp, height = 64.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(tokens.color.surfaceVariant.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "+$remainder",
                        fontSize = 11.sp,
                        color = tokens.color.textSecondary,
                    )
                }
            }
        }
    }
}

/** 画师行的未读胶囊（朱底浅字）。 */
@Composable
private fun ArtistDigestUnreadPill(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(DesktopTheme.AccentBeni.copy(alpha = 0.13f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = DesktopTheme.AccentBeni,
        )
    }
}

/**
 * 副栏：来源轴三段 chip + 同步时刻 + 全部已读。
 */
@Composable
private fun ArtistInboxSourceBar(
    activeSource: ArtistFeedSource,
    lastSyncAt: Long,
    galleryUnread: Int,
    pixivUnread: Int,
    onSelectSource: (ArtistFeedSource) -> Unit,
    onMarkAllRead: () -> Unit,
) {
    val tokens = VeneraTokens
    val activeUnread = when (activeSource) {
        ArtistFeedSource.ALL -> galleryUnread + pixivUnread
        ArtistFeedSource.GALLERY -> galleryUnread
        ArtistFeedSource.PIXIV -> pixivUnread
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ArtistFeedSource.entries.forEach { source ->
                val isSelected = source == activeSource
                val sourceUnread = when (source) {
                    ArtistFeedSource.ALL -> galleryUnread + pixivUnread
                    ArtistFeedSource.GALLERY -> galleryUnread
                    ArtistFeedSource.PIXIV -> pixivUnread
                }
                val chipBg by animateColorAsState(
                    targetValue = if (isSelected) tokens.color.surfaceVariant.copy(alpha = 0.85f) else Color.Transparent,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(chipBg)
                        .clickable { onSelectSource(source) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        if (sourceUnread > 0) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(DesktopTheme.AccentBeni),
                            )
                        }
                        Text(
                            text = source.label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) tokens.color.textPrimary else tokens.color.textSecondary,
                        )
                    }
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = inboxSyncLabel(lastSyncAt, System.currentTimeMillis()),
                fontSize = 11.sp,
                color = tokens.color.textTertiary,
            )
            if (activeUnread > 0) {
                DesktopPushButton(onClick = onMarkAllRead) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(imageVector = Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(13.dp))
                        Text(text = "全部已读", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

/**
 * 收件箱私有网格：段头与分界线占满整行，卡按列排（不用 GridPageTemplate ——
 * 它没有段中分界线槽）。
 */
@Composable
private fun ArtistInboxGrid(
    entries: List<ArtistInboxEntry>,
    readUids: Set<String>,
    dimRead: Boolean,
    showSectionActions: Boolean,
    columns: Int,
    spacing: androidx.compose.ui.unit.Dp,
    onOpenPost: (DesktopWallCardModel) -> Unit,
    onToggleRead: (String) -> Unit,
    onMarkSectionRead: (ArtistFeedSource) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(columns.coerceAtLeast(1)),
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalItemSpacing = spacing,
    ) {
        entries.forEachIndexed { index, entry ->
            when (entry) {
                is ArtistInboxEntry.SectionHead -> item(
                    key = "head-${entry.source.name}",
                    span = StaggeredGridItemSpan.FullLine,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (entry.unreadCount > 0) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(DesktopTheme.AccentFuji),
                            )
                        }
                        Text(
                            text = entry.label,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = tokens.color.textPrimary,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        if (showSectionActions && entry.unreadCount > 0) {
                            DesktopPushButton(onClick = { onMarkSectionRead(entry.source) }) {
                                Text(text = "全部已读", fontSize = 11.sp)
                            }
                        }
                    }
                }

                is ArtistInboxEntry.Divider -> item(
                    key = "divider-$index-${entry.label}",
                    span = StaggeredGridItemSpan.FullLine,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(1.dp)
                                .background(tokens.color.outlineVariant.copy(alpha = 0.35f)),
                        )
                        Text(
                            text = entry.label,
                            fontSize = 11.sp,
                            color = tokens.color.textTertiary,
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(1.dp)
                                .background(tokens.color.outlineVariant.copy(alpha = 0.35f)),
                        )
                    }
                }

                is ArtistInboxEntry.Card -> item(
                    key = entry.model.uid.ifBlank { entry.model.url }.ifBlank { "card-$index" },
                ) {
                    val model = entry.model
                    val isRead = model.uid.isBlank() || model.uid in readUids
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                // 已读轻度淡化，视线聚焦未读新作
                                alpha = if (isRead && dimRead) 0.76f else 1f
                            },
                    ) {
                        DesktopWallCard(
                            card = model,
                            onOpenPost = { onOpenPost(model) },
                            titleOverride = model.title,
                            authorOverride = entry.authorOverride,
                            hideSourceBadge = entry.hideSourceBadge,
                            showInfo = true,
                            // 收件箱没有多选账（读/未读是卡级动作），显式禁用勾选。
                            onToggleSelect = null,
                            // 本页就在画师名下，卡上画师名不再跳转。
                            onOpenArtist = null,
                        )
                        InboxCardBadge(
                            isRead = isRead,
                            onToggleRead = {
                                if (model.uid.isNotBlank()) onToggleRead(model.uid)
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 卡片右上角状态微标签与就地标记。
 */
@Composable
private fun InboxCardBadge(
    isRead: Boolean,
    onToggleRead: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box(
        modifier = modifier
            .hoverable(interactionSource)
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    isHovered -> Color(0xCC000000)
                    !isRead -> DesktopTheme.AccentBeni.copy(alpha = 0.8f)
                    else -> Color.Transparent
                }
            )
            .clickable(onClick = onToggleRead)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (isHovered) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = if (isRead) Color(0xFF4ADE80) else Color.White,
                    modifier = Modifier.size(11.dp),
                )
                Text(
                    text = if (isRead) "标为未读" else "标为已读",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                )
            }
        } else if (!isRead) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                )
                Text(
                    text = "新作",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }
    }
}

/**
 * 可折叠画师筛选抽屉（去星标列，只留筛选）。
 */
@Composable
private fun ArtistInboxDrawerRail(
    groups: List<ArtistFeedGroup>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedArtist: String?,
    onSelectArtist: (String) -> Unit,
    readUids: Set<String>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    val filteredArtists = remember(groups, searchQuery) {
        if (searchQuery.isBlank()) groups else groups.filter { it.artistName.contains(searchQuery, ignoreCase = true) }
    }

    Column(
        modifier = modifier
            .border(
                width = 1.dp,
                color = tokens.color.surfaceVariant.copy(alpha = 0.25f),
            )
            .padding(vertical = 10.dp, horizontal = 10.dp),
    ) {
        // 顶栏：搜索框与关闭按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            com.venera.desktop.ui.components.DesktopSearchField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                onSearch = {},
                modifier = Modifier.weight(1f),
                placeholder = "筛选关注画师…",
            )

            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "收起",
                    tint = tokens.color.textSecondary,
                    modifier = Modifier.size(15.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 画师列表
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(filteredArtists, key = { it.artistName }) { group ->
                val isSelected = group.artistName == selectedArtist
                val unreadInGroup = group.works.count { it.uid.isNotBlank() && it.uid !in readUids }

                val itemBg by animateColorAsState(
                    targetValue = if (isSelected) tokens.color.surfaceVariant.copy(alpha = 0.8f) else Color.Transparent,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(itemBg)
                        .clickable { onSelectArtist(group.artistName) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Box(modifier = Modifier.size(28.dp)) {
                            if (group.avatarUrl.isNotBlank()) {
                                AsyncImage(
                                    model = group.avatarUrl,
                                    contentDescription = group.artistName,
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape),
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(tokens.color.primary.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = group.artistName.take(1),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = tokens.color.primary,
                                    )
                                }
                            }

                            if (unreadInGroup > 0) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .align(Alignment.TopEnd)
                                        .clip(CircleShape)
                                        .background(DesktopTheme.AccentBeni),
                                )
                            }
                        }

                        Text(
                            text = group.artistName,
                            fontSize = 12.sp,
                            fontWeight = if (unreadInGroup > 0) FontWeight.SemiBold else FontWeight.Normal,
                            color = tokens.color.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    if (unreadInGroup > 0) {
                        Text(
                            text = "+$unreadInGroup",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = DesktopTheme.AccentBeni,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(DesktopTheme.AccentBeni.copy(alpha = 0.12f))
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 引导登录 Pixiv 账号卡片（仅 pixiv 单源视图在无票据时摆出）。
 */
@Composable
private fun ArtistInboxLoginGuide(onLogin: () -> Unit) {
    val tokens = VeneraTokens
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "需登录 Pixiv 账号",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = tokens.color.textPrimary,
            )
            Text(
                text = "登录后同步 Pixiv 关注画师新作",
                fontSize = 13.sp,
                color = tokens.color.textSecondary,
            )
            DesktopPushButton(onClick = onLogin) {
                Text(text = "前往登录", fontSize = 13.sp)
            }
        }
    }
}

/**
 * 空状态或异常状态提示视图。
 */
@Composable
private fun ArtistInboxEmptyView(
    title: String,
    subtitle: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    val tokens = VeneraTokens
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = tokens.color.textPrimary,
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = tokens.color.textTertiary,
            )
            DesktopPushButton(onClick = onAction) {
                Text(text = actionLabel, fontSize = 12.sp)
            }
        }
    }
}
