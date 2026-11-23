package com.venera.desktop.favorites

import com.venera.desktop.ui.theme.VeneraType
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.components.venera.VeneraCoverMask
import com.venera.compose.data.db.ComicFollowRecord
import com.venera.compose.data.db.ComicFollowStatus
import com.venera.compose.data.db.FavoriteRecord
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.follow.DesktopFollowUpdatesScreen
import com.venera.desktop.follow.FollowCheckProgress
import com.venera.desktop.follow.NetFollowImportProgress
import com.venera.desktop.follow.desktopFollowUnreadCount
import com.venera.desktop.gallery.data.DesktopFavoriteFolderTab
import com.venera.desktop.gallery.ui.DesktopGalleryFavoritesWallScreen
import com.venera.desktop.gallery.ui.DesktopWallCardModel
import com.venera.desktop.gallery.ui.LocalChromePalette
import com.venera.desktop.search.DesktopSearchSourceIcon
import com.venera.desktop.settings.DesktopI18n
import com.venera.desktop.ui.components.DesktopGlassCardDefaults
import com.venera.desktop.ui.components.DesktopGlassCardLevel
import top.yukonga.miuix.kmp.basic.Text

/**
 * 收藏屏那排分段。**分段 UI 已撤**（本地/网络不再
 * 分段切换，改源面板三路并列），枚举保留给「画廊」档的分流判断用。
 */
internal enum class DesktopFavoritesSegment(val label: String) {
    LOCAL(DesktopI18n.tr("本地", "Local")),
    GALLERY(DesktopI18n.tr("画廊", "Gallery")),
    NETWORK(DesktopI18n.tr("网络", "Network")),
}

/** 源面板选中键：本地 / 画廊 / 追更三路内置，网络路 = 源 key。 */
internal const val FAVORITES_SOURCE_LOCAL = "local"
internal const val FAVORITES_SOURCE_GALLERY = "gallery"
internal const val FAVORITES_SOURCE_FOLLOW = "follow"

/**
 * 收藏 hub —— 书库式（收藏页重做稿 · 方向 B）单一主从布局。
 *
 * 左书库索引柜：总册卡（封面堆叠 + 总数/夹数 + 最近添加）→ 追更入口（带未读角标）→
 * 网络源按登录态分组 → 本周读数 → 「管理漫画源」。本地只由总册卡代表（点它回本地路），
 * 不另摆一条同名的本地行。右侧是一面墙，随面板选中切换（本地 / 追更 / 画廊 / 网络源）；
 * 本地那一档的「最近收藏」细轨与夹索引都在原屏里。
 *
 * 面板项选中 = 中性 7.5% 底 + 名字转主色加粗（稿面 sidebar 口径，不用蓝实底）；行尾只摆真值：
 * 网络源没有收藏计数（引擎三跳才有），**不造假数**——只摆登录状态点。
 *
 * 回调逐枚必传无默认值；`onCreateFolder / onDeleteFolder` 两枚例外（nullable）：
 * null = 对应把手不摆，判据台架不传就不出现（不放假把手，同 `onSearchComicTag` 先例）。
 */
@Composable
internal fun DesktopFavoritesHubScreen(
    mode: WideScreenLayoutMode,
    selectedSource: String,
    onPickSource: (String) -> Unit,
    // ── 本地那一档：逐枚转交原屏，不重拼任何判据 ──
    shownFavorites: List<FavoriteRecord>,
    folderTabs: List<DesktopFavoriteFolderTab>,
    /** 本地段当前挑中的夹（`null` = 全部），只用于把选块画成选中态。 */
    localFolderPicked: String? = null,
    /** 本地收藏排序档（0 = 收藏时间 / 1 = 标题）；与设置页读同一枚偏好。 */
    localSortMode: Int = 0,
    /** 改本地收藏排序档。 */
    onPickLocalSortMode: (Int) -> Unit = {},
    countNote: String,
    /** 本地收藏总数（源面板行角标；`-1` = 还没读回来，不念数）。 */
    localCount: Int,
    /** 本周收藏读数（壳用全量收藏算好；默认空档不念假数）。 */
    localWeekStats: FavWeekStats = FavWeekStats.EMPTY,
    loaded: Boolean,
    note: String?,
    filterNote: String?,
    onFolderTabSelect: (String?) -> Unit,
    /** 新建本地夹（装配点落 LocalFavoritesManager）。null = 把手不摆。 */
    onCreateFolder: ((String) -> Unit)?,
    /** 删除本地夹（成员移回默认，装配点管）。null = 右键菜单不摆。 */
    onDeleteFolder: ((String) -> Unit)?,
    maskedOf: (FavoriteRecord) -> Boolean,
    onOpen: (FavoriteRecord) -> Unit,
    onRemove: (FavoriteRecord) -> Unit,
    onSearchComicTag: ((sourceKey: String, tag: String) -> Unit)?,
    /** 本地收藏卡右键「加入追更」；null = 装配点没接，菜单不摆这一项。 */
    onAddFollowFavorite: ((FavoriteRecord) -> Unit)? = null,
    // ── 画廊那一档：成品全由壳算好（投影、守卫投卡、两步清空）──
    galleryLoaded: Boolean,
    galleryTotal: Int,
    galleryCards: List<DesktopWallCardModel>,
    galleryBlocked: Int,
    galleryUnrenderableLine: String,
    galleryClearVisible: Boolean,
    galleryClearLabel: String,
    onGalleryClearClick: () -> Unit,
    galleryClearNote: String?,
    onOpenGalleryPost: (DesktopWallCardModel) -> Unit,
    /** 画廊收藏批量取消（uid 列表）。null = 写口没接，收藏墙不进入多选。 */
    onGalleryRemoveSelected: ((List<String>) -> Unit)?,
    // ── 网络那一档：面板数据与右列成品 ──
    netSources: List<NetFavSourceRow>,
    /** 网络路当前选中的源（`selectedSource` 命中已登录源时非空）。 */
    netActiveSource: NetFavSourceRow?,
    netPage: NetFavPageState?,
    netFolders: NetFavFoldersState?,
    onPickNetFolder: (String) -> Unit,
    netMaskedOf: (NetFavComicRow) -> Boolean,
    onOpenNetComic: (NetFavComicRow) -> Unit,
    onGoSourceManage: () -> Unit,
    onRefreshNet: () -> Unit,
    netLoadingMore: Boolean,
    onLoadNetMore: () -> Unit,
    // ── 追更那一档：账本、巡检进度与全部写口 ──
    followRecords: List<ComicFollowRecord>,
    followProgress: FollowCheckProgress,
    onCheckFollow: () -> Unit,
    onCancelFollowCheck: () -> Unit,
    onOpenFollow: (ComicFollowRecord) -> Unit,
    onMarkFollowRead: (ComicFollowRecord) -> Unit,
    onSetFollowStatus: (ComicFollowRecord, ComicFollowStatus) -> Unit,
    /** 单本强制重查；null = 巡检器没接，卡片不摆这一颗。 */
    onRetryFollow: ((ComicFollowRecord) -> Unit)?,
    onBatchRetryFollow: ((List<ComicFollowRecord>) -> Unit)? = null,
    onRemoveFollow: (ComicFollowRecord) -> Unit,
    followMaskedOf: (ComicFollowRecord) -> Boolean,
    /** 网站收藏导追更：当前源+当前夹是否已订阅、夹内已在追更的 id、导入进度与两个把手。 */
    followSubscribed: Boolean = false,
    followComicIds: Set<String> = emptySet(),
    followImportProgress: NetFollowImportProgress = NetFollowImportProgress(),
    onFollowImport: (() -> Unit)? = null,
    onCancelFollowImport: (() -> Unit)? = null,
    onBatchUnfavoriteNetFavorites: ((String, String?, List<NetFavComicRow>) -> Unit)? = null,
    onBatchFollowNetFavorites: ((List<NetFavComicRow>) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    val palette = LocalChromePalette.current
    var panelCollapsed by rememberSaveable { mutableStateOf(false) }
    val followUnread = desktopFollowUnreadCount(followRecords)

    Column(modifier.fillMaxSize().padding(horizontal = tokens.spacing.screenHorizontal)) {
        // ── 书库页头：题名 + 当前路的副题（本地另注排序口径）──
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = DesktopI18n.t("收藏", "Favorites"),
                fontSize = VeneraType.statNumber.size, lineHeight = VeneraType.statNumber.lineHeight,
                fontWeight = VeneraType.cardTitle.weight,
                color = palette.ink1,
            )
            Text(
                text = when {
                    selectedSource == FAVORITES_SOURCE_LOCAL ->
                        if (localCount >= 0) {
                            if (localSortMode == 1) {
                                DesktopI18n.t("共 $localCount 本 · 按标题", "$localCount comics · By title")
                            } else {
                                DesktopI18n.t("共 $localCount 本 · 按收藏时间", "$localCount comics · By date added")
                            }
                        } else {
                            DesktopI18n.t("正在读取", "Loading")
                        }
                    selectedSource == FAVORITES_SOURCE_GALLERY -> DesktopI18n.t("共 $galleryTotal 张", "$galleryTotal items")
                    selectedSource == FAVORITES_SOURCE_FOLLOW ->
                        if (followRecords.isEmpty()) {
                            DesktopI18n.t("共 0 本", "0 comics")
                        } else if (followUnread > 0) {
                            DesktopI18n.t("共 ${followRecords.size} 本 · $followUnread 本有更新", "${followRecords.size} comics · $followUnread updated")
                        } else {
                            DesktopI18n.t("共 ${followRecords.size} 本", "${followRecords.size} comics")
                        }
                    else -> DesktopI18n.t("${netSources.size} 个支持源", "${netSources.size} sources")
                },
                fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                color = palette.ink3,
                modifier = Modifier.padding(start = 12.dp, bottom = 3.dp),
                maxLines = 1,
            )
        }

        // ── 主从：左书库索引柜（总册卡 + 源分组）+ 右内容列 ──
        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            FavoritesSourcePanel(
                collapsed = panelCollapsed,
                onToggleCollapsed = { panelCollapsed = !panelCollapsed },
                selectedSource = selectedSource,
                onPickSource = onPickSource,
                localCount = localCount,
                folderCount = folderTabs.count { it.folder != null },
                localCovers = shownFavorites.take(3).map { fav ->
                    fav.coverUrl to maskedOf(fav)
                },
                weekStats = localWeekStats,
                followCount = followRecords.size,
                followUnread = followUnread,
                netSources = netSources,
                onGoSourceManage = onGoSourceManage,
            )

            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                when {
                    selectedSource == FAVORITES_SOURCE_FOLLOW -> DesktopFollowUpdatesScreen(
                        mode = mode,
                        records = followRecords,
                        progress = followProgress,
                        onCheck = onCheckFollow,
                        onCancelCheck = onCancelFollowCheck,
                        onOpen = onOpenFollow,
                        onMarkRead = onMarkFollowRead,
                        onSetStatus = onSetFollowStatus,
                        onRetry = onRetryFollow,
                        onBatchRetry = onBatchRetryFollow,
                        onRemove = onRemoveFollow,
                        maskedOf = followMaskedOf,
                        embedded = true,
                    )
                    selectedSource == FAVORITES_SOURCE_LOCAL -> DesktopFavoritesScreen(
                        mode = mode,
                        shownFavorites = shownFavorites,
                        folderTabs = folderTabs,
                        selectedFolder = localFolderPicked,
                        countNote = countNote,
                        loaded = loaded,
                        note = note,
                        filterNote = filterNote,
                        onFolderTabSelect = onFolderTabSelect,
                        maskedOf = maskedOf,
                        onOpen = onOpen,
                        onRemove = onRemove,
                        onAddFollow = onAddFollowFavorite,
                        onSearchComicTag = onSearchComicTag,
                        onCreateFolder = onCreateFolder,
                        onDeleteFolder = onDeleteFolder,
                        showHeader = false,
                        embedded = true,
                        sortMode = localSortMode,
                        onPickSortMode = onPickLocalSortMode,
                    )
                    selectedSource == FAVORITES_SOURCE_GALLERY -> DesktopGalleryFavoritesWallScreen(
                        mode = mode,
                        loaded = galleryLoaded,
                        total = galleryTotal,
                        cards = galleryCards,
                        blockedCount = galleryBlocked,
                        unrenderableLine = galleryUnrenderableLine,
                        clearVisible = galleryClearVisible,
                        clearLabel = galleryClearLabel,
                        onClearClick = onGalleryClearClick,
                        clearNote = galleryClearNote,
                        onOpenPost = onOpenGalleryPost,
                        onRemoveSelected = onGalleryRemoveSelected,
                        embedded = true,
                    )
                    netActiveSource != null -> DesktopNetworkFavoritesScreen(
                        mode = mode,
                        activeSource = netActiveSource,
                        expandedPage = netPage,
                        folders = netFolders,
                        onPickFolder = onPickNetFolder,
                        maskedOf = netMaskedOf,
                        onOpenComic = onOpenNetComic,
                        onGoLogin = onGoSourceManage,
                        onRefresh = onRefreshNet,
                        loadingMore = netLoadingMore,
                        onLoadMore = onLoadNetMore,
                        followSubscribed = followSubscribed,
                        followComicIds = followComicIds,
                        followImportProgress = followImportProgress,
                        onFollowImport = onFollowImport,
                        onCancelFollowImport = onCancelFollowImport,
                        onBatchUnfavorite = onBatchUnfavoriteNetFavorites,
                        onBatchFollow = onBatchFollowNetFavorites,
                        embedded = true,
                    )
                }
            }
        }
    }
}

/**
 * 书库索引柜：总册卡（封面堆叠 + 总数与夹数 + 最近添加）→ 追更入口（带未读角标）→ 网络源分组 → 本周读数 → 底部「管理漫画源」。
 *
 * 本地只由总册卡代表（点它回本地路）；源列表只列网络源，避免同一套「本地收藏」在栏里出现两遍。
 * 网络源按登录态分组，行尾只念登录状态点（网络源没有收藏计数，不造假数）。
 * 追更入口是书库级第二路（本地 / 追更 / 画廊 / 网络源），未读数取账本 `has_new_update` 的真数。
 *
 * @param localCount 本地收藏总数；`-1` = 还没读回来，念「正在读取」而不是 0。
 * @param folderCount 本地夹数（不含「全部」那一档），来自壳递进来的夹索引那一排。
 * @param localCovers 总册卡封面堆叠的封面与打码态（最多取前三本）。
 * @param weekStats 本周新增与逐日条数；只有真实时间戳能算出的两格，没数据的那一格不摆。
 */
@Composable
private fun FavoritesSourcePanel(
    collapsed: Boolean,
    onToggleCollapsed: () -> Unit,
    selectedSource: String,
    onPickSource: (String) -> Unit,
    localCount: Int,
    folderCount: Int,
    localCovers: List<Pair<String, Boolean>>,
    weekStats: FavWeekStats,
    followCount: Int,
    followUnread: Int,
    netSources: List<NetFavSourceRow>,
    onGoSourceManage: () -> Unit,
) {
    val palette = LocalChromePalette.current
    val panelShape = RoundedCornerShape(12.dp)
    val cardShape = RoundedCornerShape(12.dp)
    val panelWidth by animateDpAsState(
        targetValue = if (collapsed) 64.dp else 264.dp,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
        label = "fav-panel-width",
    )
    val panelPad by animateDpAsState(
        targetValue = if (collapsed) 8.dp else 12.dp,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
        label = "fav-panel-pad",
    )
    Column(
        modifier = DesktopGlassCardDefaults.surfaceModifier(
            modifier = Modifier.width(panelWidth).fillMaxHeight(),
            level = DesktopGlassCardLevel.Primary,
            shape = panelShape,
            borderWidth = 0.5.dp,
        ).padding(panelPad),
    ) {
        // ── 顶部收起/展开把手 ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = if (collapsed) Arrangement.Center else Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedVisibility(
                visible = !collapsed,
                enter = fadeIn(tween(200, easing = FastOutSlowInEasing)) +
                    expandHorizontally(tween(220, easing = FastOutSlowInEasing)),
                exit = fadeOut(tween(140, easing = FastOutSlowInEasing)) +
                    shrinkHorizontally(tween(180, easing = FastOutSlowInEasing)),
            ) {
                Text(
                    text = DesktopI18n.t("书库目录", "Library"),
                    fontSize = VeneraType.micro.size,
                    lineHeight = VeneraType.micro.lineHeight,
                    color = palette.ink3,
                    maxLines = 1,
                )
            }
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(palette.ink1.copy(alpha = 0.06f))
                    .clickable(onClick = onToggleCollapsed),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (collapsed) "»" else "«",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = palette.ink2,
                    maxLines = 1,
                )
            }
        }

        // ── 总册卡：本地路唯一入口（收起时仅保留封面缩略图）──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    DesktopGlassCardDefaults.backgroundColor(level = DesktopGlassCardLevel.Nested),
                    cardShape,
                )
                .border(
                    width = if (selectedSource == FAVORITES_SOURCE_LOCAL) 1.dp else 0.5.dp,
                    color = if (selectedSource == FAVORITES_SOURCE_LOCAL) {
                        palette.accent.copy(alpha = 0.45f)
                    } else {
                        DesktopGlassCardDefaults.borderColor(level = DesktopGlassCardLevel.Nested)
                    },
                    shape = cardShape,
                )
                .clip(cardShape)
                .clickable { onPickSource(FAVORITES_SOURCE_LOCAL) }
                .padding(horizontal = if (collapsed) 6.dp else 12.dp, vertical = if (collapsed) 8.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (collapsed) Arrangement.Center else Arrangement.spacedBy(12.dp),
        ) {
            if (collapsed) {
                val firstCover = localCovers.firstOrNull()
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(palette.segmentTrack)
                        .border(0.5.dp, palette.hairline, RoundedCornerShape(7.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (firstCover != null) {
                        VeneraCover(
                            url = firstCover.first,
                            contentDescription = DesktopI18n.t("本地收藏", "Local Favorites"),
                            mask = if (firstCover.second) VeneraCoverMask.Masked else VeneraCoverMask.Visible,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text(
                            text = DesktopI18n.t("本", "L"),
                            fontSize = VeneraType.aux.size,
                            fontWeight = FontWeight.Bold,
                            color = palette.ink1,
                        )
                    }
                }
            } else {
                LocalCoverStack(localCovers)
            }
            AnimatedVisibility(
                visible = !collapsed,
                enter = fadeIn(tween(220, easing = FastOutSlowInEasing)) +
                    expandHorizontally(tween(240, easing = FastOutSlowInEasing)),
                exit = fadeOut(tween(130, easing = FastOutSlowInEasing)) +
                    shrinkHorizontally(tween(180, easing = FastOutSlowInEasing)),
            ) {
                Column {
                    Text(
                        text = DesktopI18n.t("本地收藏", "Local Favorites"),
                        fontSize = VeneraType.bodySecondary.size, lineHeight = VeneraType.bodySecondary.lineHeight,
                        fontWeight = VeneraType.cardTitle.weight,
                        color = palette.ink1,
                        maxLines = 1,
                    )
                    Text(
                        text = if (localCount >= 0) DesktopI18n.t("$localCount 本 · $folderCount 个文件夹", "$localCount comics · $folderCount folders") else DesktopI18n.t("正在读取", "Loading"),
                        fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                        color = palette.ink3,
                        maxLines = 2,
                    )
                    Text(
                        text = if (localCount < 0) {
                            DesktopI18n.t("最近添加 · 正在读取", "Recently added · Loading")
                        } else {
                            DesktopI18n.t("最近添加 · ", "Last added · ") + relativeAddedText(
                                lastAddedAt = weekStats.lastAddedAt,
                                now = System.currentTimeMillis(),
                            )
                        },
                        fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                        color = palette.ink3,
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        // ── 追更入口：书库级第二路，未读数做红点；收起态居中显示图标与角标 ──
        FollowPanelItem(
            collapsed = collapsed,
            selected = selectedSource == FAVORITES_SOURCE_FOLLOW,
            followCount = followCount,
            followUnread = followUnread,
            onClick = { onPickSource(FAVORITES_SOURCE_FOLLOW) },
        )

        if (netSources.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (!collapsed) {
                    PanelGroupLabel(DesktopI18n.t("已登录", "Signed In"), netSources.count { it.logged })
                }
                netSources.filter { it.logged }.forEach { src ->
                    SourcePanelItem(
                        collapsed = collapsed,
                        sourceKey = src.key,
                        name = src.name,
                        logged = true,
                        selected = selectedSource == src.key,
                        onClick = { onPickSource(src.key) },
                    )
                }
                val unlogged = netSources.filterNot { it.logged }
                if (unlogged.isNotEmpty()) {
                    if (!collapsed) {
                        PanelGroupLabel(DesktopI18n.t("未登录", "Signed Out"), unlogged.size)
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .height(0.5.dp)
                                .background(palette.hairline),
                        )
                    }
                    unlogged.forEach { src ->
                        SourcePanelItem(
                            collapsed = collapsed,
                            sourceKey = src.key,
                            name = src.name,
                            logged = false,
                            selected = selectedSource == src.key,
                            onClick = { onPickSource(src.key) },
                        )
                    }
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
        AnimatedVisibility(
            visible = !collapsed,
            enter = fadeIn(tween(200, easing = FastOutSlowInEasing)) +
                expandVertically(tween(220, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(130, easing = FastOutSlowInEasing)) +
                shrinkVertically(tween(180, easing = FastOutSlowInEasing)),
        ) {
            WeekStatBlock(weekStats)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onGoSourceManage)
                .padding(horizontal = if (collapsed) 0.dp else 8.dp, vertical = 8.dp),
            horizontalArrangement = if (collapsed) Arrangement.Center else Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (collapsed) {
                Icon(
                    imageVector = Icons.Outlined.Tune,
                    contentDescription = DesktopI18n.t("管理漫画源", "Manage Sources"),
                    tint = palette.ink2,
                    modifier = Modifier.size(18.dp),
                )
            } else {
                Text(
                    text = DesktopI18n.t("管理漫画源 →", "Manage Sources →"),
                    fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                    color = palette.ink2,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * 侧栏底部「本周」读数：新增本数 + 周一至周日七根柱（今天那一根点亮）。
 *
 * 只画真数：稿里第二格「导入本地 3 次」没有数据出处（应用不记导入流水），不摆。
 */
@Composable
private fun WeekStatBlock(stats: FavWeekStats) {
    val palette = LocalChromePalette.current
    val tokens = VeneraTokens
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.hairline))
        Spacer(Modifier.height(10.dp))
        Text(
            text = DesktopI18n.t("本周", "This Week"),
            fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
            color = palette.ink3,
            maxLines = 1,
        )
        Spacer(Modifier.height(8.dp))
        Column(
            modifier = DesktopGlassCardDefaults.surfaceModifier(
                level = DesktopGlassCardLevel.Nested,
                shape = RoundedCornerShape(10.dp),
                borderWidth = 0.5.dp,
            ).padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "${stats.addedThisWeek}",
                    fontSize = VeneraType.statNumber.size, lineHeight = VeneraType.statNumber.lineHeight,
                    fontWeight = VeneraType.statNumber.weight,
                    color = palette.ink1,
                )
                Text(
                    text = DesktopI18n.t("本", "comics"),
                    fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                    color = palette.ink3,
                    modifier = Modifier.padding(start = 2.dp, bottom = 3.dp),
                )
            }
            Text(
                text = DesktopI18n.t("新增收藏", "Newly Favorited"),
                fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                color = palette.ink3,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(8.dp))
        val peak = (stats.dailyCounts.maxOrNull() ?: 0).coerceAtLeast(1)
        Row(
            modifier = Modifier.fillMaxWidth().height(26.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            stats.dailyCounts.forEachIndexed { index, count ->
                val fraction = (count.toFloat() / peak).coerceAtLeast(0.08f)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(fraction)
                        .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                        .background(
                            if (index == stats.dailyCounts.lastIndex) {
                                palette.accent.copy(alpha = 0.5f)
                            } else {
                                palette.ink3.copy(alpha = 0.18f)
                            },
                        ),
                )
            }
        }
    }
}

/**
 * 总册卡左侧的三册封面堆叠：后两册斜放压在前一册下面，空档用轨道色占位。
 */
@Composable
private fun LocalCoverStack(covers: List<Pair<String, Boolean>>) {
    val palette = LocalChromePalette.current
    val slots = listOf(Triple(-9f, 0.dp, 0.65f), Triple(-3f, 12.dp, 0.85f), Triple(4f, 24.dp, 1f))
    Box(modifier = Modifier.width(66.dp).height(58.dp)) {
        slots.forEachIndexed { index, slot ->
            val (angle, offsetX, slotAlpha) = slot
            val cover = covers.getOrNull(index)
            Box(
                modifier = Modifier
                    .offset(x = offsetX, y = if (index == 0) 2.dp else 0.dp)
                    .graphicsLayer {
                        rotationZ = angle
                        alpha = slotAlpha
                    }
                    .width(40.dp)
                    .height(54.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(palette.segmentTrack)
                    .border(0.5.dp, palette.hairline, RoundedCornerShape(6.dp)),
            ) {
                if (cover != null) {
                    VeneraCover(
                        url = cover.first,
                        contentDescription = null,
                        mask = if (cover.second) VeneraCoverMask.Masked else VeneraCoverMask.Visible,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun PanelGroupLabel(label: String, count: Int) {
    val palette = LocalChromePalette.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight, color = palette.ink3)
        Spacer(Modifier.weight(1f))
        Text("$count", fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight, color = palette.ink3)
    }
}

/** 追更一行：首字色块 + 名 + 副题（本数与未读）+ 行尾未读红点；与网络源同一族样式。 */
@Composable
private fun FollowPanelItem(
    collapsed: Boolean,
    selected: Boolean,
    followCount: Int,
    followUnread: Int,
    onClick: () -> Unit,
) {
    val palette = LocalChromePalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) palette.accent.copy(alpha = 0.1f) else Color.Transparent)
            .border(
                width = if (selected) 0.5.dp else 0.dp,
                color = if (selected) palette.accent.copy(alpha = 0.4f) else Color.Transparent,
                shape = RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = if (collapsed) 6.dp else 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (collapsed) Arrangement.Center else Arrangement.spacedBy(10.dp),
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Box(
                modifier = Modifier
                    .size(if (collapsed) 26.dp else 20.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(palette.segmentTrack),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = DesktopI18n.t("追", "F"),
                    fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                    fontWeight = VeneraType.cardTitle.weight,
                    color = palette.ink1,
                    maxLines = 1,
                )
            }
            if (collapsed && followUnread > 0) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(palette.danger),
                )
            }
        }
        AnimatedVisibility(
            visible = !collapsed,
            enter = fadeIn(tween(200, easing = FastOutSlowInEasing)) +
                expandHorizontally(tween(220, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(120, easing = FastOutSlowInEasing)) +
                shrinkHorizontally(tween(180, easing = FastOutSlowInEasing)),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = DesktopI18n.t("追更", "Follow Updates"),
                        fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (selected) palette.ink1 else palette.ink2,
                        maxLines = 1,
                    )
                    Text(
                        text = when {
                            followCount <= 0 -> DesktopI18n.t("暂无追更", "Nothing followed")
                            followUnread > 0 -> DesktopI18n.t("$followCount 本 · $followUnread 本有更新", "$followCount · $followUnread updated")
                            else -> DesktopI18n.t("$followCount 本 · 暂无新更", "$followCount · No new chapters")
                        },
                        fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                        color = if (followUnread > 0) palette.accent else palette.ink3,
                        maxLines = 1,
                    )
                }
                if (followUnread > 0) {
                    Box(
                        modifier = Modifier
                            .height(16.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(palette.danger)
                            .padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (followUnread > 99) "99+" else "$followUnread",
                            fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                            color = Color.White,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** 网络源一行：站标 + 名 + 行尾登录状态点；收起态仅居中显示站标与登录小点。 */
@Composable
private fun SourcePanelItem(
    collapsed: Boolean,
    sourceKey: String,
    name: String,
    logged: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalChromePalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (selected) palette.ink1.copy(alpha = 0.075f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = if (collapsed) 4.dp else 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (collapsed) Arrangement.Center else Arrangement.spacedBy(8.dp),
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            DesktopSearchSourceIcon(
                sourceKey = sourceKey,
                label = name,
                size = if (collapsed) 24.dp else 20.dp,
            )
            if (collapsed) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (logged) palette.ok else palette.ink3),
                )
            }
        }
        AnimatedVisibility(
            visible = !collapsed,
            enter = fadeIn(tween(200, easing = FastOutSlowInEasing)) +
                expandHorizontally(tween(220, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(120, easing = FastOutSlowInEasing)) +
                shrinkHorizontally(tween(180, easing = FastOutSlowInEasing)),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = name,
                    fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (selected) palette.ink1 else palette.ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(if (logged) palette.ok else palette.ink3),
                )
            }
        }
    }
}

/** 网络收藏手风琴的一颗源(壳现算:引擎 hasNetworkFavorites + isLogged)。 */
internal data class NetFavSourceRow(
    val key: String,
    val name: String,
    val logged: Boolean,
)

/** 展开那一颗的当前页(壳现取:loadComics / loadNext 两形折平;error 非空照原话上屏)。 */
internal data class NetFavPageState(
    val comics: List<NetFavComicRow>,
    val error: String? = null,
    /** `true` = 还有下一页(游标源的 next / 分页源的 maxPage)。续页把手不摆(见壳注)。 */
    val hasMore: Boolean = false,
)

/** 多夹态(`favorites.multiFolder` 那族);单夹源整格 null。选中/回调都走**夹 id**（
 *  `loadNext/loadComics` 的 folder 形参要的是 id,E站拼 `?favcat=`;name 只上屏）。 */
internal data class NetFavFoldersState(
    val folders: List<NetFavFolderRow>,
    val selectedId: String?,
)

/** 一颗收藏夹(id→name 成对,对岸 FavFolders.folders 摊平)。 */
internal data class NetFavFolderRow(
    val id: String,
    val name: String,
)

/** 网络收藏里的一本(对岸 FavComicPage 的 Comic 摊平成渲染行)。 */
internal data class NetFavComicRow(
    val sourceKey: String,
    val comicId: String,
    val title: String,
    val cover: String,
)

/**
 * 侧栏「本周」读数：条数 + 周一到周日七格（索引 0 = 周一，6 = 周日）+ 最近一条的时间戳。
 *
 * @param addedThisWeek 本周新增收藏数（周一 00:00 起，本地时区）。
 * @param dailyCounts 逐日新增条数；今天之后的格子恒 0（不预言未来）。
 * @param lastAddedAt 全部收藏里最新的 `createdAt`；`null` = 没有任何收藏。
 */
internal data class FavWeekStats(
    val addedThisWeek: Int,
    val dailyCounts: List<Int>,
    val lastAddedAt: Long?,
) {
    companion object {
        /** 还没读到收藏时的空档：七格全 0、不念「最近添加」。 */
        val EMPTY = FavWeekStats(0, List(7) { 0 }, null)
    }
}

/**
 * 用全量收藏的时间戳算本周读数。壳在收藏那一屏的装配点调用，屏只搬结果。
 *
 * @param favorites 全量收藏（不是筛后那一批，否则筛一夹就会把「本周」改成那一夹的数）。
 * @param now 当前时刻（毫秒），由调用点传入以便同一帧内多格读数一致。
 */
internal fun desktopFavWeekStats(favorites: List<FavoriteRecord>, now: Long): FavWeekStats {
    val zone = java.time.ZoneId.systemDefault()
    val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val monday = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
    val counts = (0L..6L).map { offset ->
        val day = monday.plusDays(offset)
        if (day.isAfter(today)) {
            0
        } else {
            favorites.count { fav ->
                java.time.Instant.ofEpochMilli(fav.createdAt).atZone(zone).toLocalDate() == day
            }
        }
    }
    return FavWeekStats(
        addedThisWeek = counts.sum(),
        dailyCounts = counts,
        lastAddedAt = favorites.maxOfOrNull { it.createdAt },
    )
}

/** 「最近添加」那半句：按距今时长念，超过 30 天改念日期（不猜「很久以前」这种话）。 */
private fun relativeAddedText(lastAddedAt: Long?, now: Long): String {
    if (lastAddedAt == null) return DesktopI18n.tr("暂无记录", "No records")
    val diff = (now - lastAddedAt).coerceAtLeast(0L)
    return when {
        diff < 60_000L -> DesktopI18n.tr("刚刚", "Just now")
        diff < 3_600_000L -> DesktopI18n.tr("${diff / 60_000L} 分钟前", "${diff / 60_000L}m ago")
        diff < 86_400_000L -> DesktopI18n.tr("${diff / 3_600_000L} 小时前", "${diff / 3_600_000L}h ago")
        diff < 30L * 86_400_000L -> DesktopI18n.tr("${diff / 86_400_000L} 天前", "${diff / 86_400_000L}d ago")
        else -> java.time.Instant.ofEpochMilli(lastAddedAt)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
            .toString()
    }
}
