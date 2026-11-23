package com.venera.desktop.favorites

import com.venera.desktop.ui.theme.VeneraType
import com.venera.desktop.ui.components.desktopCoverFloat
import com.venera.compose.components.venera.hScrollEndFade
import com.venera.desktop.gallery.ui.GalleryTokens
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.venera.desktop.gallery.ui.LocalChromePalette
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.compose.components.venera.VeneraButton
import com.venera.compose.components.venera.VeneraCard
import com.venera.compose.components.venera.VeneraChip
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.components.venera.VeneraCoverMask
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.follow.NetFollowImportProgress
import com.venera.desktop.gallery.ui.selection.DesktopCardSelectBadge
import com.venera.desktop.gallery.ui.selection.DesktopSelectionDock
import com.venera.desktop.gallery.ui.selection.DesktopSelectionDockBottomInset
import com.venera.desktop.gallery.ui.selection.DockActionPill
import com.venera.desktop.gallery.ui.selection.DockIconAction
import com.venera.desktop.gallery.ui.selection.LocalSelectionDockHazeState
import com.venera.desktop.settings.DesktopI18n
import com.venera.desktop.ui.DesktopGlobalNavAction
import com.venera.desktop.ui.DesktopListShortcutAction
import com.venera.desktop.ui.DesktopModifierState
import com.venera.desktop.ui.RegisterDesktopShortcutHandler
import com.venera.desktop.ui.components.desktopUiFloat
import com.venera.desktop.ui.computeShiftRangeSelection
import com.venera.desktop.ui.matchGlobalNavShortcut
import com.venera.desktop.ui.matchListShortcut
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay

/**
 * 网络收藏（完全重做 · 严格对齐 desktop-favorites-overhaul-.html ）。
 *
 * 核心架构：
 * 1. 彻底剔除移动端手风琴折叠卡与画廊堆叠，采用专业桌面端 Master-Detail 宽屏主从布局；
 * 2. 左栏固定 280dp 漫画源主控面板，按登录态分组，带状态指示圆点与「去登录」引导；
 * 3. 右栏顶头常驻展示真实多文件夹平铺 Tab（对齐安卓 loadFolders 真实结构与 folderId 传递）；
 * 4. 漫画卡片采用独立卡座（底板 + 1dp 微弱描边 + 悬浮状态），标题槽固定 36dp 双行高度，彻底消除文字错位。
 */
@Composable
internal fun DesktopNetworkFavoritesScreen(
    mode: WideScreenLayoutMode,
    /**
     * 当前选中的那颗源（macOS 皮批）：**源面板上移进收藏 hub**（本地/画廊/网络
     * 三路并列一个面板），本屏只管右列内容。展开态由壳算好递进来，屏零引擎调用。
     */
    activeSource: NetFavSourceRow,
    expandedPage: NetFavPageState?,
    folders: NetFavFoldersState?,
    onPickFolder: (String) -> Unit,
    maskedOf: (NetFavComicRow) -> Boolean,
    onOpenComic: (NetFavComicRow) -> Unit,
    onGoLogin: () -> Unit,
    /** 刷新当前源（壳侧重触发取数）。 */
    onRefresh: () -> Unit,
    /** 续页正在取（页尾念「正在加载更多…」且不重复触发）。 */
    loadingMore: Boolean,
    /** 「加载更多」/滑到底自动续页。 */
    onLoadMore: () -> Unit,
    /** 该夹已建订阅（同步过）⇒ 按钮念「同步追更」。 */
    followSubscribed: Boolean = false,
    /** 已在追更账上的 comicId，卡面角标「追更中」用它。 */
    followComicIds: Set<String> = emptySet(),
    /** 导入进度（与巡检进度同构的四段读数）；默认空档不摆条。 */
    followImportProgress: NetFollowImportProgress = NetFollowImportProgress(),
    /** 加入 / 同步追更；null = 未登录或写口没接，按钮不摆。 */
    onFollowImport: (() -> Unit)? = null,
    onCancelFollowImport: (() -> Unit)? = null,
    /** 批量取消网络收藏（源 key + 夹 id + 选中漫画）；null = 不摆取消按钮。 */
    onBatchUnfavorite: ((String, String?, List<NetFavComicRow>) -> Unit)? = null,
    /** 批量加入追更；null = 不摆加入追更按钮。 */
    onBatchFollow: ((List<NetFavComicRow>) -> Unit)? = null,
    modifier: Modifier = Modifier,
    embedded: Boolean = false,
) {
    val tokens = VeneraTokens
    val palette = LocalChromePalette.current
    var viewMode by rememberSaveable { mutableStateOf(0) } // 0 = 网格, 1 = 双列
    val cardLayout by com.venera.desktop.settings.DesktopComicCardLayout.config.collectAsState()
    // 两段式：第一次点只武装，第二次点才真拉；换源或换夹即撤武装。
    var followArmed by remember(activeSource.key, folders?.selectedId) { mutableStateOf(false) }

    val shownComics = expandedPage?.comics.orEmpty()
    fun netKey(comic: NetFavComicRow): String = "${comic.sourceKey}|${comic.comicId}"
    val selectedKeys = remember { mutableStateListOf<String>() }
    val selectedSet = selectedKeys.toSet()
    var lastAnchorKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(activeSource.key, folders?.selectedId) {
        selectedKeys.clear()
        lastAnchorKey = null
    }
    LaunchedEffect(shownComics) {
        val valid = shownComics.mapTo(HashSet()) { netKey(it) }
        selectedKeys.removeAll { it !in valid }
    }

    fun toggleNet(comic: NetFavComicRow) {
        val key = netKey(comic)
        if (DesktopModifierState.isShiftDown && lastAnchorKey != null) {
            val ordered = shownComics.map { netKey(it) }
            val next = computeShiftRangeSelection(
                orderedKeys = ordered,
                anchorKey = lastAnchorKey,
                targetKey = key,
                currentSelected = selectedKeys.toSet(),
            )
            selectedKeys.clear()
            selectedKeys.addAll(next)
        } else {
            if (key in selectedKeys) selectedKeys.remove(key) else selectedKeys.add(key)
        }
        lastAnchorKey = key
    }

    val selectedComics = remember(shownComics, selectedSet) {
        shownComics.filter { netKey(it) in selectedSet }
    }

    RegisterDesktopShortcutHandler(shownComics.size, selectedSet.size, priority = 100) { signal ->
        when (matchListShortcut(signal)) {
            DesktopListShortcutAction.SELECT_ALL -> {
                if (shownComics.isEmpty()) return@RegisterDesktopShortcutHandler false
                selectedKeys.clear()
                selectedKeys.addAll(shownComics.map { netKey(it) })
                true
            }
            DesktopListShortcutAction.DELETE_SELECTED -> {
                if (selectedComics.isEmpty() || onBatchUnfavorite == null) {
                    return@RegisterDesktopShortcutHandler false
                }
                val targets = selectedComics.toList()
                selectedKeys.clear()
                onBatchUnfavorite(activeSource.key, folders?.selectedId, targets)
                true
            }
            else -> {
                if (matchGlobalNavShortcut(signal) == DesktopGlobalNavAction.ESCAPE &&
                    selectedSet.isNotEmpty()
                ) {
                    selectedKeys.clear()
                    true
                } else {
                    false
                }
            }
        }
    }

    val dockHazeState = rememberHazeState()
    CompositionLocalProvider(LocalSelectionDockHazeState provides dockHazeState) {
    Box(modifier = modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .hazeSource(dockHazeState, zIndex = 0f)
            .then(if (!embedded) Modifier.padding(horizontal = tokens.spacing.screenHorizontal) else Modifier),
    ) {
        // ── 段头（书库式）：源名 + 同步状态 + 已取条数与夹数 + 右端刷新 ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = activeSource.name,
                fontSize = VeneraType.statNumber.size, lineHeight = VeneraType.statNumber.lineHeight,
                fontWeight = VeneraType.cardTitle.weight,
                color = tokens.color.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!activeSource.logged) {
                Text(
                    text = DesktopI18n.t("未登录", "Signed Out"),
                    fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                    color = palette.ink3,
                    modifier = Modifier.padding(start = 10.dp),
                )
            } else if (expandedPage != null && expandedPage.error == null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.padding(start = 10.dp),
                ) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(palette.ok),
                    )
                    Text(DesktopI18n.t("已同步", "Synced"), fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight, color = palette.ok)
                }
            }
            if (expandedPage != null) {
                // 夹数只在多夹源（folders 非空）时念；单夹源没有夹清单，不能念「0 个夹」。
                Text(
                    text = folders?.let { state ->
                        DesktopI18n.t("已取 ${expandedPage.comics.size} 本 · ${state.folders.size} 个夹", "${expandedPage.comics.size} fetched · ${state.folders.size} folders")
                    } ?: DesktopI18n.t("已取 ${expandedPage.comics.size} 本", "${expandedPage.comics.size} fetched"),
                    fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                    color = palette.ink3,
                    modifier = Modifier.padding(start = 10.dp),
                    maxLines = 1,
                )
            }
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            com.venera.desktop.ui.DesktopPopUpButton(
                label = DesktopI18n.t("视图", "View"),
                value = if (viewMode == 1) DesktopI18n.t("双列", "2-Col") else DesktopI18n.t("网格", "Grid"),
                options = listOf(DesktopI18n.t("网格", "Grid"), DesktopI18n.t("双列", "2-Col")),
                selectedIndex = viewMode,
                onSelect = { viewMode = it },
            )
            Spacer(Modifier.width(8.dp))
            if (onFollowImport != null) {
                val importing = followImportProgress.running
                com.venera.desktop.ui.DesktopActionButton(
                    text = when {
                        importing -> DesktopI18n.t("停止导入", "Stop")
                        followArmed -> DesktopI18n.t("确认导入（上限 200 本）", "Import up to 200?")
                        followSubscribed -> DesktopI18n.t("同步追更", "Sync Follows")
                        else -> DesktopI18n.t("加入追更", "Follow Folder")
                    },
                    onClick = {
                        when {
                            importing -> onCancelFollowImport?.invoke()
                            followArmed -> {
                                followArmed = false
                                onFollowImport()
                            }
                            else -> followArmed = true
                        }
                    },
                )
                androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
            }
            com.venera.desktop.ui.DesktopIconToolButton(
                glyph = "⟳",
                contentDescription = DesktopI18n.t("刷新", "Refresh"),
                onClick = onRefresh,
            )
        }

        // ── 导入读数：跑的时候报页码与已入本数，跑完报摘要，出错照原话 ──
        if (followImportProgress.running ||
            followImportProgress.summary.isNotBlank() ||
            followImportProgress.notice.isNotBlank()
        ) {
            Text(
                text = when {
                    followImportProgress.running -> DesktopI18n.t(
                        "正在拉取第 ${followImportProgress.pages + 1} 页 · 已导入 ${followImportProgress.fetched} 本",
                        "Pulling page ${followImportProgress.pages + 1} · ${followImportProgress.fetched} imported",
                    )
                    followImportProgress.notice.isNotBlank() -> followImportProgress.notice
                    else -> followImportProgress.summary
                },
                fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                color = if (followImportProgress.notice.isNotBlank()) palette.danger else palette.ink3,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        // ── 夹索引（对齐安卓 loadFolders 真实 Map，走夹 id）：书库式的横向索引条 ──
        if (folders != null && folders.folders.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = DesktopI18n.t("夹索引", "Folders"),
                    fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                    color = palette.ink3,
                    maxLines = 1,
                )
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()).hScrollEndFade(VeneraTokens.color.background),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    folders.folders.forEach { folder ->
                        com.venera.desktop.ui.DesktopFilterChip(
                            text = folder.name,
                            selected = folder.id == folders.selectedId,
                            onClick = { onPickFolder(folder.id) },
                        )
                    }
                }
            }
        }

        // ── 当前夹页眉：夹名 + 已取条数 + 细线（书库分区语言）──
        val activeFolderName = folders?.let { state ->
            state.folders.firstOrNull { it.id == state.selectedId }?.name
        }
        if (activeFolderName != null && expandedPage != null && expandedPage.comics.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = activeFolderName,
                    fontSize = VeneraType.bodySecondary.size, lineHeight = VeneraType.bodySecondary.lineHeight,
                    fontWeight = VeneraType.cardTitle.weight,
                    color = tokens.color.textPrimary,
                    maxLines = 1,
                )
                Text(
                    text = DesktopI18n.t("已取 ${expandedPage.comics.size} 本", "${expandedPage.comics.size} fetched"),
                    fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                    color = palette.ink3,
                    maxLines = 1,
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(palette.hairline),
                )
            }
        }

        // 漫画内容网格
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 4.dp),
        ) {
            when {
                        !activeSource.logged -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = DesktopI18n.t("该源未登录，请先登录账号获取网络收藏", "Not signed in to this source. Sign in to view network favorites."),
                                        fontSize = VeneraType.bodySecondary.size, lineHeight = VeneraType.bodySecondary.lineHeight,
                                        color = tokens.color.textTertiary,
                                    )
                                    com.venera.desktop.ui.DesktopActionButton(text = DesktopI18n.t("立即登录", "Sign In"), onClick = onGoLogin)
                                }
                            }
                        }

                        expandedPage == null -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = DesktopI18n.t("正在获取「${activeSource.name}」的网络收藏数据…", "Loading network favorites from '${activeSource.name}'…"),
                                    fontSize = VeneraType.bodySecondary.size, lineHeight = VeneraType.bodySecondary.lineHeight,
                                    color = tokens.color.textTertiary,
                                )
                            }
                        }

                        expandedPage.error != null && expandedPage.comics.isEmpty() -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Text(
                                        text = DesktopI18n.t("获取失败：${expandedPage.error}", "Load failed: ${expandedPage.error}"),
                                        fontSize = VeneraType.bodySecondary.size, lineHeight = VeneraType.bodySecondary.lineHeight,
                                        color = tokens.color.textTertiary,
                                    )
                                    com.venera.desktop.ui.DesktopActionButton(
                                        text = DesktopI18n.t("重试", "Retry"),
                                        onClick = onRefresh,
                                    )
                                }
                            }
                        }

                        expandedPage.comics.isEmpty() -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = DesktopI18n.t("该分类文件夹下暂无网络收藏内容", "No network favorites in this folder"),
                                    fontSize = VeneraType.bodySecondary.size, lineHeight = VeneraType.bodySecondary.lineHeight,
                                    color = tokens.color.textTertiary,
                                )
                            }
                        }

                        else -> {
                            val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
                            val nearEnd by remember(gridState) {
                                derivedStateOf {
                                    val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                                    last >= gridState.layoutInfo.totalItemsCount - 5
                                }
                            }
                            androidx.compose.runtime.LaunchedEffect(nearEnd, expandedPage.comics.size, loadingMore) {
                                if (nearEnd && expandedPage.hasMore && !loadingMore) onLoadMore()
                            }
                            androidx.compose.foundation.layout.BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val columnsCount = if (viewMode == 1) {
                                cardLayout.resolveDoubleColumns(2)
                            } else {
                                cardLayout.resolveGridColumns(this.maxWidth, 168.dp, 2, 6)
                            }
                            val coverAspect = cardLayout.resolveCoverAspect(0.75f)
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(columnsCount),
                                state = gridState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = if (selectedSet.isNotEmpty()) 88.dp else 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                itemsIndexed(expandedPage.comics) { index, comic ->
                                    // 与本地收藏墙同口径的浮动入场：按序号错峰淡入并上浮，滚入的格子重播。
                                    val comicIdentity = comic.sourceKey to comic.comicId
                                    var appeared by remember(comicIdentity) { mutableStateOf(false) }
                                    LaunchedEffect(comicIdentity) {
                                        delay((index.coerceAtMost(14) * 32).toLong())
                                        appeared = true
                                    }
                                    val enterAlpha by animateFloatAsState(
                                        targetValue = if (appeared) 1f else 0f,
                                        animationSpec = tween(durationMillis = 220),
                                        label = "net-fav-enter-alpha",
                                    )
                                    val enterShift by animateFloatAsState(
                                        targetValue = if (appeared) 0f else 16f,
                                        animationSpec = tween(durationMillis = 260),
                                        label = "net-fav-enter-shift",
                                    )
                                    Box(
                                        modifier = Modifier.graphicsLayer {
                                            alpha = enterAlpha
                                            translationY = enterShift
                                        },
                                    ) {
                                        NetFavCard(
                                            comic = comic,
                                            folderLabel = activeFolderName,
                                            masked = maskedOf(comic),
                                            following = comic.comicId in followComicIds,
                                            doubleColumn = viewMode == 1,
                                            coverAspect = coverAspect,
                                            selected = netKey(comic) in selectedSet,
                                            onToggleSelect = { toggleNet(comic) },
                                            onOpen = onOpenComic,
                                        )
                                    }
                                }
                                // 主人口径：「加载更多」钮撤掉（近尾自动续页已在上面），
                                // 只留取数中那句读数。
                                if (loadingMore) {
                                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 12.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = DesktopI18n.t("正在加载更多…", "Loading more…"),
                                                fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                                                color = tokens.color.textTertiary,
                                            )
                                        }
                                    }
                                }
                            }
                            // 滚过约两屏浮现的「回到顶部」悬浮钮。
                            com.venera.desktop.ui.DesktopBackToTopFab(
                                state = gridState,
                                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                            )
                            }
                        }
                    }
                }

        // ── 底部一行细计数（Finder 式；长提示句撤）──
        if (expandedPage != null && expandedPage.comics.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp, vertical = 8.dp),
            ) {
                Text(
                    text = if (expandedPage.hasMore) {
                        DesktopI18n.t("已显示 ${expandedPage.comics.size} 本 · 下滑续页", "Showing ${expandedPage.comics.size} comics · Scroll for more")
                    } else {
                        DesktopI18n.t("已显示全部 ${expandedPage.comics.size} 本", "Showing all ${expandedPage.comics.size} comics")
                    },
                    fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                    color = tokens.color.textTertiary,
                )
            }
        }
    }
    DesktopSelectionDock(
        selectedCount = selectedComics.size,
        totalCount = shownComics.size,
        onSelectAll = {
            selectedKeys.clear()
            selectedKeys.addAll(shownComics.map { netKey(it) })
        },
        onInvertSelect = {
            val all = shownComics.map { netKey(it) }
            selectedKeys.clear()
            selectedKeys.addAll(all.filter { it !in selectedSet })
        },
        onClearSelect = { selectedKeys.clear() },
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = DesktopSelectionDockBottomInset),
    ) {
        if (onBatchFollow != null) {
            val newFollows = selectedComics.filter { it.comicId !in followComicIds }
            if (newFollows.isNotEmpty()) {
                DockActionPill(
                    text = DesktopI18n.t("加入追更 (${newFollows.size})", "Follow (${newFollows.size})"),
                    primary = true,
                    onClick = {
                        selectedKeys.clear()
                        onBatchFollow(newFollows)
                    },
                )
            }
        }
        if (onBatchUnfavorite != null) {
            DockActionPill(
                text = DesktopI18n.t("取消收藏 (${selectedComics.size})", "Unfavorite (${selectedComics.size})"),
                danger = true,
                onClick = {
                    val targets = selectedComics.toList()
                    selectedKeys.clear()
                    onBatchUnfavorite(activeSource.key, folders?.selectedId, targets)
                },
            )
        }
        DockIconAction(
            icon = Icons.Outlined.ContentCopy,
            description = DesktopI18n.t("复制所选标题", "Copy selected titles"),
            onClick = {
                val text = selectedComics.joinToString("\n") { it.title }
                if (text.isNotBlank()) {
                    java.awt.Toolkit.getDefaultToolkit().systemClipboard
                        .setContents(java.awt.datatransfer.StringSelection(text), null)
                }
            },
        )
    }
    }
    }
}

/** 左侧源导航条目（高 40dp，选中 = 主题色微底、无描边。） */
@Composable
private fun SourceNavItem(
    src: NetFavSourceRow,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val tokens = VeneraTokens
    val palette = LocalChromePalette.current
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(shape)
            .background(
                if (isSelected) tokens.color.primary.copy(alpha = 0.12f)
                else Color.Transparent,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(if (src.logged) palette.ok else palette.ink3),
        )
        Text(
            text = src.name,
            fontSize = VeneraType.bodySecondary.size, lineHeight = VeneraType.bodySecondary.lineHeight,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) tokens.color.primary else tokens.color.textPrimary,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!src.logged) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(tokens.color.primary.copy(alpha = 0.1f))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            ) {
                Text(
                    text = DesktopI18n.t("去登录", "Sign In"),
                    fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                    fontWeight = VeneraType.cardTitle.weight,
                    color = tokens.color.primary,
                )
            }
        }
    }
}

/**
 * 书库式网络书卡：卡面（底色 + 发丝描边 + 内距 7）+ 封面 3:4 + 题名两行固定槽 + 夹名一行。
 *
 * 立体浮动仍走公共浮卡框；卡面这次真铺底（`tray = false` + 自铺 `palette.card`），
 * 描边罩整卡，与本地收藏那一屏同一套书卡语言。
 *
 * @param folderLabel 当前夹名；`null` = 单夹源没有夹名可念，卡下不摆那一行。
 */
@Composable
private fun NetFavCard(
    comic: NetFavComicRow,
    folderLabel: String?,
    masked: Boolean,
    following: Boolean,
    doubleColumn: Boolean = false,
    coverAspect: Float = 0.75f,
    selected: Boolean = false,
    onToggleSelect: (() -> Unit)? = null,
    onOpen: (NetFavComicRow) -> Unit,
) {
    val tokens = VeneraTokens
    val palette = LocalChromePalette.current
    val shape = RoundedCornerShape(12.dp)

    com.venera.desktop.ui.components.DesktopFloatingCard(
        cornerRadius = 12.dp,
        surface = palette.card,
        tray = false,
        chromeAspect = null,
        liftDp = 4.dp,
        hoverElevation = 10.dp,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (selected) Modifier.border(1.5.dp, palette.accent, shape) else Modifier)
            .clickable { onOpen(comic) },
    ) { floatState ->
        if (doubleColumn) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.card)
                    .padding(9.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(82.dp)
                        .height(110.dp)
                        .clip(RoundedCornerShape(7.dp)),
                ) {
                    VeneraCover(
                        url = comic.cover,
                        contentDescription = comic.title,
                        mask = if (masked) VeneraCoverMask.Masked else VeneraCoverMask.Visible,
                        modifier = Modifier.fillMaxSize().desktopCoverFloat(floatState),
                    )
                    if (onToggleSelect != null && (selected || floatState.isHovered)) {
                        DesktopCardSelectBadge(
                            selected = selected,
                            onToggle = onToggleSelect,
                            description = if (selected) DesktopI18n.t("已选中 ${comic.title}", "Selected ${comic.title}") else DesktopI18n.t("选择 ${comic.title}", "Select ${comic.title}"),
                            size = 22.dp,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(5.dp)
                                .desktopUiFloat(floatState),
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = comic.title,
                        fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                        fontWeight = VeneraType.cardTitle.weight,
                        color = tokens.color.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (folderLabel != null) {
                        Text(
                            text = folderLabel,
                            fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                            color = tokens.color.textTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (following) {
                        Text(
                            text = DesktopI18n.t("追更中", "Following"),
                            fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                            color = palette.accent,
                            maxLines = 1,
                        )
                    }
                }
            }
        } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.card)
                .padding(7.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(coverAspect)
                    .clip(RoundedCornerShape(7.dp)),
            ) {
                VeneraCover(
                    url = comic.cover,
                    contentDescription = comic.title,
                    mask = if (masked) VeneraCoverMask.Masked else VeneraCoverMask.Visible,
                    // 外层已定死 0.75 框；自持 3:4 会溢出绘制压到框外（卡片同因同修）。
                    preserveAspectRatio = false,
                    modifier = Modifier
                        .fillMaxSize()
                        .desktopCoverFloat(floatState),
                )
                // 书脊暗缝（与本地书卡同一形）。
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .width(6.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.22f),
                                    Color.Black.copy(alpha = 0.25f),
                                    Color.Transparent,
                                ),
                            ),
                        ),
                )
                // 已在追更账上：右上角一枚小标，扫一眼就知这夹导过没。
                if (following) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = DesktopI18n.t("追更中", "Following"),
                            fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                            color = Color.White,
                            maxLines = 1,
                        )
                    }
                }
                if (onToggleSelect != null && (selected || floatState.isHovered)) {
                    DesktopCardSelectBadge(
                        selected = selected,
                        onToggle = onToggleSelect,
                        description = if (selected) DesktopI18n.t("已选中 ${comic.title}", "Selected ${comic.title}") else DesktopI18n.t("选择 ${comic.title}", "Select ${comic.title}"),
                        size = 24.dp,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp)
                            .desktopUiFloat(floatState),
                    )
                }
            }

            Text(
                text = comic.title,
                fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                fontWeight = VeneraType.cardTitle.weight,
                color = tokens.color.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .heightIn(min = 40.dp),
            )
            if (!folderLabel.isNullOrBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(tokens.color.textTertiary),
                    )
                    Text(
                        text = folderLabel,
                        fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                        color = tokens.color.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        }
    }
}
