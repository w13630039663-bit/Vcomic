package com.venera.desktop.detail

import com.venera.desktop.follow.toFollowSnapshot

import com.venera.desktop.ui.theme.VeneraType
import com.venera.desktop.ui.DesktopMacDialog
import com.venera.desktop.ui.LocalMacDialogMaxCardHeight
import com.venera.desktop.ui.MacDialogBodyLineHeight
import com.venera.desktop.ui.MacDialogCheckbox
import com.venera.desktop.ui.MacDialogSegmented
import com.venera.desktop.ui.MacDialogTextButton
import com.venera.desktop.ui.macNoRippleClickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.venera.compose.components.LocalUxPlatformServices
import com.venera.compose.feature.ComicDetailCore
import com.venera.compose.feature.ComicItem
import com.venera.compose.feature.ReaderEvent
import com.venera.compose.download.GALLERY_CHAPTER_ID
import com.venera.compose.source.model.ReaderSession
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.desktop.gallery.data.DesktopComicBulkRow
import com.venera.desktop.gallery.data.DesktopComicBulkOffline
import com.venera.desktop.gallery.data.desktopComicBulkBadgeLabel
import com.venera.desktop.gallery.data.desktopComicBulkConfirmEnabled
import com.venera.desktop.gallery.data.desktopComicBulkConfirmLabel
import com.venera.desktop.gallery.data.desktopComicBulkEpisodesOf
import com.venera.desktop.gallery.data.desktopComicBulkRowLabel
import com.venera.desktop.gallery.data.desktopComicBulkTitle
import com.venera.desktop.gallery.data.desktopComicBulkToggleAllLabel
import com.venera.desktop.gallery.data.desktopComicBulkToggleAllNext
import com.venera.desktop.gallery.ui.desktopComicResumeReading
import com.venera.engineprobe.EngineSession

/** 批量下载入口文案。 */
internal const val DESKTOP_BULK_DOWNLOAD_LABEL: String = "离线下载"


/**
 * 桌面漫画详情页 ——替代共享屏 `ComicDetailScreenBody`（下掉共享屏，
 * 桌面详情重做成独立页，UI 对齐 `desktop-comic-detail-immersive-2026-11-01.html`）。
 *
 * ## 数据姿势：与共享屏同一颗核
 *
 * 吃 [ComicDetailCore]（壳里建好、按本缓存的那颗），取数/动作全部走核：
 * load / syncLocalFav / openChapter / toggleLocalFavorite / loadComments / sendComment /
 * downloads.enqueue —— 不另开一路，两端写的是同一张账。
 *
 * ## 结构（对照）
 *
 *  - **沉浸式信息头**：封面虚化铺底 + 封面卡 + 题名/作者/评分/热标签/简介 + 全部主操作
 *    （继续阅读胶囊 / 离线下载 / 收藏 / 复制链接 / 浏览器打开）+ 已读进度线。
 *  - **双列正文**：左 = 预览图集 + 评论区（屏内评论区，能发就走核发）；右 = 标签 + 章节目录 + 作品信息。
 *  - **底部整幅推荐横滑条**：封面卡**可点** —— 点推荐 = `core.load(下一本)`，壳监听
 *    `currentComic` 同步 route（共享屏同一条腿），所以这不是假把手。
 *
 * ## 与旧仿制屏的口径差（明说，不悄悄省）
 *
 *  - 旧屏的行级下载把手与行级下载状态注没了：单话下载走批量弹窗勾一行，进度在队列屏看。
 *  - 旧屏的收藏文件夹横排改成了**收藏夹弹窗**（心形钮扳开，点夹即落笔）—— 单选语义不变。
 *  - 旧屏的「还没读回来 / 读库失败」两档胶囊（不摆）在核路径下塌成「章节没回来不摆」：
 *    核的历史是 flow，没有可见的读库失败档；「还没读回来」由 details==null 的装载态盖住。
 */
@Composable
internal fun DesktopComicDetailScreen(
    core: ComicDetailCore,
    /**
     * 本地收藏改道桌面主表（修「详情页点了收藏、收藏屏看不到」）。
     *
     * 核的 `toggleLocalFavorite` 写的是 `LocalFavoritesManager` 官方夹表（一夹一表），
     * 而桌面收藏屏读的是主表 `comic_favorite`（`VeneraDesktop.kt` 那条「另一本账，不掺和」的口径）。
     * 这四枚参数非 null 时，本地收藏的**名单 / 已收档 / 写入**全改走桌面主表；
     * null（别的调用点）交回核的旧路，行为逐字不变。
     */
    localFavoriteFolders: List<String>? = null,
    localFavoriteAdded: Set<String>? = null,
    localFavorited: Boolean? = null,
    onLocalFavoriteToggle: ((folder: String, added: Boolean) -> Unit)? = null,
    /** 桌面主表的新建夹写口；null = 退回核的 `createLocalFolder`。 */
    onLocalFavoriteFolderCreate: ((name: String) -> Unit)? = null,
    /**
     * 追更开关的端口（装配点按本创建）；null = 没接这一路 ⇒ 追更按钮整颗不摆。
     * 屏不直接碰 DAO：读态、建基线、消红点全在端口里。
     */
    followPort: com.venera.desktop.follow.DesktopFollowDetailPort? = null,
    comic: ComicItem,
    mode: WideScreenLayoutMode,
    /** 这一本要不要打码（壳用 `ux.coverMaskStateOf` 现算，屏只搬 —— 打码判据单源口径）。 */
    masked: Boolean,
    onBack: () -> Unit,
    onStartLiveReading: (ReaderSession) -> Unit,
    onSearchTag: (String) -> Unit,
    onDownloadStarted: ((coverUrl: String?) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val ux = LocalUxPlatformServices.current
    val detailState by core.uiState.collectAsState()
    val isFav by core.isLocalFav.collectAsState()
    val favPanel by core.favPanel.collectAsState()
    val historyList by core.historyFlow.collectAsState()

    LaunchedEffect(comic.sourceName, comic.id) {
        core.load(comic)
        core.syncLocalFav(comic)
    }
    // 收藏面板的一次性提示（共享屏同形）。
    LaunchedEffect(favPanel.toast) {
        favPanel.toast?.let {
            ux.toast(it)
            core.consumeToast()
        }
    }
    // 默认倒序初值 + 阅读事件（Live → 切阅读器；Failed → 如实报错）。
    LaunchedEffect(Unit) {
        if (core.readerPrefs.reverseChapterOrder.value) {
            core.setReversed(true)
        }
        core.readerEvents.collect { event ->
            when (event) {
                is ReaderEvent.Live -> onStartLiveReading(event.session)
                is ReaderEvent.Failed -> ux.toastLong(event.message)
            }
        }
    }
    // 评论区：详情就绪后拉第一页（loadComments 吃详情的 subId，详情没回来时拉不动）。
    LaunchedEffect(detailState.details?.comic?.id) {
        if (detailState.details != null) {
            core.loadComments()
        }
    }
    // 预览缩略图的取图配置（EH 换镜像域那一条链）：窗口进一批、算一批 —— 与共享屏同一姿势。
    LaunchedEffect(detailState.thumbnails, detailState.details?.thumbnails) {
        val thumbs = detailState.thumbnails.ifEmpty { detailState.details?.thumbnails.orEmpty() }
        if (thumbs.isNotEmpty()) {
            core.ensureThumbnailConfigs(thumbs)
        }
    }

    val details = detailState.details
    val historyRecord = historyList.find { it.comicId == comic.id }

    if (details == null) {
        // 装载态：复现详情屏的 Hero + 双列几何，数据到达时骨架就地换真值，不摆居中三件套。
        // 题名照 route 念并固定在顶部；返回与重试全程可点，不摆全屏拦截层。
        ComicDetailSkeleton(
            title = comic.title,
            note = detailState.loadingMessage.ifBlank { com.venera.desktop.settings.DesktopI18n.t("正在加载详情…", "Loading details…") },
            error = detailState.error,
            wide = mode != WideScreenLayoutMode.Compact,
            onRetry = { core.load(comic) },
            onBack = onBack,
            modifier = modifier,
        )
        return
    }

    val chapterIds = details.chapters.map { it.id }
    val sourceKeyResolved = core.currentSourceKey()
    // 离线状态在 IO 线程按三档端口 chapterOffline 计算一次，避免组合期同步遍历目录阻塞 UI 线程。
    val bulkChapters = details.chapters
    val bulkComicId = details.comic.id
    val bulkRows by produceState(
        initialValue = emptyList<DesktopComicBulkRow>(),
        key1 = bulkComicId,
        key2 = sourceKeyResolved,
    ) {
        value = withContext(Dispatchers.IO) {
            bulkChapters.map { ch ->
                DesktopComicBulkRow(
                    episode = EngineSession.Episode(name = ch.title, id = ch.id),
                    offline = when (core.downloads.chapterOffline(sourceKeyResolved, bulkComicId, ch.id)) {
                        com.venera.compose.download.ChapterOffline.COMPLETE -> DesktopComicBulkOffline.DOWNLOADED
                        com.venera.compose.download.ChapterOffline.PARTIAL -> DesktopComicBulkOffline.PARTIAL
                        com.venera.compose.download.ChapterOffline.NONE -> DesktopComicBulkOffline.NONE
                    },
                )
            }
        }
    }
    val readCapsule = if (chapterIds.isNotEmpty()) {
        desktopDetailReadCapsule(historyRecord, chapterIds)
    } else {
        desktopDetailGalleryCapsule(historyRecord)
    }
    // 「上次读到这里」挂**续读真会打开的那一行**（越界夹进表尾，见 desktopDetailMarkedIndex）。
    val markedIndex = desktopDetailMarkedIndex(historyRecord, chapterIds)

    // ── 追更：读态 / 进入即消红 / 一键开关 ──
    val followState = followPort?.state?.collectAsState()?.value
    val followSnapshot = remember(details, sourceKeyResolved) { details.toFollowSnapshot() }
    val followScope = rememberCoroutineScope()
    LaunchedEffect(followPort, details.comic.id, sourceKeyResolved) {
        followPort?.attach(details.comic.id, sourceKeyResolved)
    }
    val onToggleFollow: (() -> Unit)? = followPort?.let { port ->
        {
            followScope.launch {
                runCatching {
                    port.toggle(
                        comicId = details.comic.id,
                        sourceName = sourceKeyResolved,
                        title = details.comic.title.ifBlank { comic.title },
                        author = details.author,
                        coverUrl = details.comic.cover.ifBlank { comic.coverUrl },
                        snapshot = followSnapshot,
                    )
                }.onSuccess { following ->
                    ux.toast(
                        com.venera.desktop.settings.DesktopI18n.tr(
                            if (following) "已加入追更" else "已取消追更",
                            if (following) "Added to follow updates" else "Removed from follow updates",
                        )
                    )
                }.onFailure {
                    com.venera.desktop.DesktopErrorBus.report(
                        category = "漫画",
                        headline = "追更写入失败",
                        humanReason = "这一本没写进追更账本，阅读进度不受影响；可再点一次。",
                        detail = "$sourceKeyResolved: ${it::class.java.simpleName}: ${it.message.orEmpty()}",
                        dedupKey = "comic:follow_toggle:$sourceKeyResolved",
                    )
                    ux.toastLong(
                        com.venera.desktop.settings.DesktopI18n.tr(
                            "追更写入失败：${com.venera.desktop.platform.desktopFailureNoteOf(it)}",
                            "Follow update failed: ${com.venera.desktop.platform.desktopFailureNoteOf(it)}",
                        )
                    )
                }
            }
        }
    }

    val data = DesktopDetailViewData(
        title = details.comic.title.ifBlank { comic.title },
        cover = details.comic.cover.ifBlank { comic.coverUrl },
        author = details.author,
        uploader = details.uploader,
        rating = details.stars,
        userRating = detailState.userRating,
        canRate = detailState.writeCapabilities.canRate,
        canLike = detailState.writeCapabilities.canLike,
        updateTime = details.updateTime,
        // 点赞读数吃核里的当前值（点赞后乐观更新会写回），源没报再退详情读数。
        likesCount = (if (detailState.likesCount > 0) detailState.likesCount else details.likesCount).takeIf { it > 0 },
        commentCount = details.commentCount.takeIf { it > 0 },
        tagGroups = details.tagMap.entries.map { it.key to it.value },
        description = details.comic.description.ifBlank { comic.description },
        thumbnails = detailState.thumbnails.ifEmpty { details.thumbnails },
        resolvedThumbnails = detailState.thumbnailConfigs.mapValues { it.value.url },
        recommend = details.recommend.map { rec ->
            DesktopDetailRecommendItem(
                id = rec.id,
                title = rec.title,
                cover = rec.cover,
                sourceKey = rec.sourceKey.ifBlank { comic.sourceName },
            )
        },
        chapters = details.chapters.mapIndexed { i, ch ->
            DesktopDetailChapter(
                id = ch.id,
                title = ch.title,
                marked = markedIndex != null && markedIndex == i,
                offline = bulkRows.getOrNull(i)?.offline,
            )
        },
        readCapsule = readCapsule,
        favorited = localFavorited ?: isFav,
        favoriteBusy = favPanel.pending.isNotEmpty(),
        favoriteFolders = localFavoriteFolders ?: favPanel.localFolders,
        favoriteAddedFolders = localFavoriteAdded ?: favPanel.localAdded,
        networkFolders = favPanel.networkFolders,
        networkAdded = favPanel.networkAdded,
        networkAvailable = favPanel.hasNetwork,
        // 单夹形（favorites 有、loadFolders 没有）：没有夹表可拉，只有一枚整本开关。
        networkSingle = if (favPanel.hasNetwork && !favPanel.networkMultiFolder) {
            favPanel.networkSingleAdded
        } else {
            null
        },
        networkLoading = favPanel.isLoadingNetwork,
        networkError = favPanel.networkError,
        liked = detailState.isLiked,
        actionError = detailState.error,
        comments = detailState.commentThread.items.map { c ->
            DesktopDetailComment(id = c.id, userName = c.userName, content = c.content, time = c.time, avatar = c.avatar)
        },
        commentsLoading = detailState.commentThread.isLoading,
        commentsError = detailState.commentThread.error,
        commentsHasMore = detailState.commentThread.hasMore,
        commentsLoadingMore = detailState.commentThread.isLoading && detailState.commentThread.items.isNotEmpty(),
        commentsPage = detailState.commentThread.page.coerceAtLeast(1),
        commentsMaxPage = detailState.commentThread.maxPage,
        canSendComment = detailState.commentCapabilities.canSend,
        isSendingComment = detailState.isSendingComment,
        website = details.url,
        sourceKey = sourceKeyResolved,
    )

    DesktopComicDetailContent(
        data = data,
        mode = mode,
        masked = masked,
        onBack = onBack,
        followState = followState,
        onToggleFollow = onToggleFollow,
        onResumeRead = {
            val capsule = readCapsule
            if (capsule != null) {
                if (chapterIds.isNotEmpty()) {
                    val idx = desktopComicResumeReading(historyRecord, chapterIds)?.first ?: 0
                    val ch = details.chapters.getOrNull(idx)
                    if (ch != null) {
                        core.openChapter(comic, ch.id, ch.title, idx, capsule.pageIndex)
                    } else {
                        ux.toast(com.venera.desktop.settings.DesktopI18n.tr("这一话认不出来 ⇒ 没进阅读器（源站可能删了那一话）", "Chapter not found ⇒ could not open reader (source may have removed it)"))
                    }
                } else {
                    core.openChapter(comic, GALLERY_CHAPTER_ID, comic.title, 0, capsule.pageIndex)
                }
            }
        },
        onOpenChapter = { chId ->
            val idx = chapterIds.indexOf(chId)
            val ch = details.chapters.getOrNull(idx)
            if (ch != null) {
                core.openChapter(comic, ch.id, ch.title, idx, 0)
            } else {
                ux.toast(com.venera.desktop.settings.DesktopI18n.tr("这一话认不出来 ⇒ 没进阅读器（源站可能删了那一话）", "Chapter not found ⇒ could not open reader (source may have removed it)"))
            }
        },
        // 入口可见性不依赖异步档位结果，避免首帧闪烁；离线档位到位后只补 badge，不换几何。
        onBulkDownload = if (chapterIds.isNotEmpty()) {
            { episodes ->
                val picked = details.chapters.filter { ch -> episodes.any { it.id == ch.id } }
                if (picked.isNotEmpty()) {
                    core.downloads.enqueue(
                        sourceKey = sourceKeyResolved,
                        comicId = details.comic.id,
                        comicTitle = details.comic.title,
                        comicCover = details.comic.cover,
                        chapters = picked,
                    )
                    onDownloadStarted?.invoke(details.comic.cover.ifBlank { comic.coverUrl })
                    ux.toast(com.venera.desktop.settings.DesktopI18n.tr("已将 " + picked.size + " 话加入下载队列", "Added ${picked.size} chapter(s) to download queue"))
                }
            }
        } else {
            null
        },
        onToggleFavoriteFolder = { folder ->
            // 改道腿非 null 就走桌面主表；否则交回核（旧路逐字不变）。
            val desktopWrite = onLocalFavoriteToggle
            if (desktopWrite != null) {
                val addedNow = (localFavoriteAdded ?: favPanel.localAdded).contains(folder)
                desktopWrite(folder, addedNow)
            } else {
                core.toggleLocalFavorite(comic, folder)
            }
        },
        // 新建夹同样改道：桌面主表有写口就走它，否则交回核（旧路逐字不变）。
        onCreateFavoriteFolder = { name ->
            val desktopCreate = onLocalFavoriteFolderCreate
            if (desktopCreate != null) desktopCreate(name) else core.createLocalFolder(name)
        },
        // 网络收藏那一路：夹 id + 当前档位交给核（核里走源的 addOrDelFavorite，toast 走面板）。
        onToggleNetworkFavorite = { folderId, added -> core.toggleNetworkFavorite(comic, folderId, added) },
        // 心形钮的第一跳：先把夹清单装进面板（localFolders 只在 openFavoritePanel 里装载 ——
        // 不调它弹窗就是"还没读到收藏夹清单"，主人量到的那格）。
        onOpenFavoritePanel = { core.openFavoritePanel(comic) },
        // 点赞走核那颗乐观更新（源不支持/失败会回滚并把原话写进 uiState.error，屏上照念）。
        onToggleLike = { core.toggleLike() },
        onSearchTag = onSearchTag,
        onOpenRecommend = { rec ->
            core.load(
                ComicItem(
                    id = rec.id,
                    title = rec.title,
                    coverUrl = rec.cover,
                    author = "",
                    sourceName = rec.sourceKey,
                ),
            )
        },
        onCopyWebsite = if (data.website.isNotBlank()) {
            { ux.copyToClipboard(data.website) }
        } else {
            null
        },
        onOpenWebsite = if (data.website.isNotBlank()) {
            {
                runCatching {
                    java.awt.Desktop.getDesktop().browse(java.net.URI(data.website))
                }.onFailure { ux.toast(com.venera.desktop.settings.DesktopI18n.tr("浏览器打不开这一页", "Could not open page in browser")) }
            }
        } else {
            null
        },
        onSendComment = { text, done ->
            core.sendComment(text) { ok, err ->
                ux.toast(
                    err ?: if (ok) com.venera.desktop.settings.DesktopI18n.tr("评论已发出", "Comment posted")
                    else com.venera.desktop.settings.DesktopI18n.tr("这一步没成，再试一次", "Failed, please try again"),
                )
                // 屏里按结果落「已发表 / 发送失败」，失败时留草稿。
                done(ok, err)
            }
        },
        onRate = { stars ->
            core.rateComic(stars) { ok, err ->
                ux.toast(
                    err ?: if (ok) com.venera.desktop.settings.DesktopI18n.tr("评分已提交", "Rating submitted")
                    else com.venera.desktop.settings.DesktopI18n.tr("这一步没成，再试一次", "Failed, please try again"),
                )
            }
        },
        onLoadMoreComments = { core.loadComments(loadMore = true) },
        modifier = modifier,
    )
}

/**
 * 详情页**纯展示**件：吃 [DesktopDetailViewData] 与回调，不吃核、不开库、不发协程。
 * 离屏判据渲这一件（喂成品数据），装载与真动作都在 [DesktopComicDetailScreen]。
 */
@Composable
internal fun DesktopComicDetailContent(
    data: DesktopDetailViewData,
    mode: WideScreenLayoutMode,
    masked: Boolean,
    onBack: () -> Unit,
    onResumeRead: () -> Unit,
    onOpenChapter: (String) -> Unit,
    /** 批量下载确认（交选中的那些话）。null = 下载口没接 ⇒ 入口与弹窗整颗不摆。 */
    onBulkDownload: ((List<EngineSession.Episode>) -> Unit)?,
    onToggleFavoriteFolder: (String) -> Unit,
    /** 追更按钮态；null = 没接这一路 ⇒ 按钮不摆（纯展示件不吃库）。 */
    followState: com.venera.desktop.follow.FollowDetailUiState? = null,
    onToggleFollow: (() -> Unit)? = null,
    /** 新建本地收藏夹（名称为准）。null = 写口没接 ⇒ 新建行整颗不摆。 */
    onCreateFavoriteFolder: ((String) -> Unit)? = null,
    /** 网络收藏那一跳（夹 id + 当前是否已收 → 核的 addOrDelFavorite）。 */
    onToggleNetworkFavorite: (String, Boolean) -> Unit,
    /** 心形钮的第一跳：把夹清单装进面板（核的 `openFavoritePanel`），随后 Content 才扳开弹窗。 */
    onOpenFavoritePanel: () -> Unit,
    /** 点赞那一跳（核的乐观更新；源不支持会回滚并念原话）。 */
    onToggleLike: () -> Unit,
    onSearchTag: (String) -> Unit,
    onOpenRecommend: (DesktopDetailRecommendItem) -> Unit,
    onCopyWebsite: (() -> Unit)?,
    onOpenWebsite: (() -> Unit)?,
    onSendComment: (String, (Boolean, String?) -> Unit) -> Unit,
    /** 打分那一跳（星数 1–5）：核里乐观更新 + 失败回滚，屏只递值。 */
    onRate: (Float) -> Unit,
    /** 评论区续下一页（核里 loadComments(loadMore = true)，追加不替换）。 */
    onLoadMoreComments: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    val wide = mode != WideScreenLayoutMode.Compact

    var showFavoriteDialog by remember { mutableStateOf(false) }
    var showBulkDialog by remember { mutableStateOf(false) }
    // 评论把手那一跳：宽栏 = 左主列那颗滚动容器；窄栏 = 头部卡片列那颗（两栏不并存，共用一格）。
    val headScroll = rememberScrollState()
    var commentsTop by remember { mutableStateOf(0f) }
    val contentScope = rememberCoroutineScope()
    fun scrollToComments() {
        contentScope.launch { headScroll.animateScrollTo(commentsTop.toInt()) }
    }
    // 离线状态尚未就绪（offline == null）时不默认全选，待 produceState 落地后按排除已下载重算。
    var bulkSelected by remember(data.chapters) {
        mutableStateOf(
            if (data.chapters.any { it.offline == null }) {
                emptySet<String>()
            } else {
                data.chapters.filter { it.offline != DesktopComicBulkOffline.DOWNLOADED }.map { it.id }.toSet()
            },
        )
    }
    var commentInput by remember(data.sourceKey, data.title) { mutableStateOf("") }
    var commentSendState by remember(data.sourceKey, data.title) { mutableStateOf(CommentSendState.IDLE) }
    var commentSendError by remember(data.sourceKey, data.title) { mutableStateOf<String?>(null) }
    var showRatingDialog by remember(data.sourceKey, data.title) { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 详情页 Hero 也吃按源右键。搜标签复用 onSearchTag（装配点
            // 已把它顶成 r.sourceKey），收藏走真面板入口，不摆点了没反应的 NoOp 项。
            val heroUx = LocalUxPlatformServices.current
            com.venera.desktop.ui.components.DesktopComicCardMenuArea(
                actions = com.venera.desktop.ui.components.DesktopComicCardActions(
                    sourceKey = data.sourceKey,
                    onAddLocalFavorite = {
                        onOpenFavoritePanel()
                        showFavoriteDialog = true
                    },
                    onSearchInSource = onSearchTag,
                    onDownload = { showBulkDialog = true },
                    onCopyTitle = { heroUx.copyToClipboard(data.title) },
                    onCopyLink = onCopyWebsite,
                    onOpenWebsite = onOpenWebsite,
                ),
                tag = data.tagGroups.firstOrNull()?.second?.firstOrNull(),
            ) {
            ComicDetailHero(
                data = data,
                masked = masked,
                onBack = onBack,
                onResumeRead = onResumeRead,
                onBulkOpenChange = { showBulkDialog = it },
                bulkAvailable = onBulkDownload != null && data.chapters.isNotEmpty(),
                onToggleFavorite = {
                    onOpenFavoritePanel()
                    showFavoriteDialog = true
                },
                followState = followState,
                onToggleFollow = onToggleFollow,
                onOpenComments = { scrollToComments() },
                onToggleLike = onToggleLike,
                onSearchTag = onSearchTag,
                onCopyWebsite = onCopyWebsite,
                onOpenWebsite = onOpenWebsite,
            )
            }
            Spacer(Modifier.height(tokens.spacing.space3))

            if (wide) {
                // 编辑排版版：主列（预览/评论/推荐，自适应铺满）+ 侧列 340（章节两列/
                // 标签/信息）。去盒壳，段结构由 SectionHead 撑。
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(30.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1f).verticalScroll(headScroll),
                        verticalArrangement = Arrangement.spacedBy(26.dp),
                    ) {
                        PreviewSection(data = data, masked = masked)
                        Box(
                            modifier = Modifier.onGloballyPositioned { commentsTop = it.positionInParent().y },
                        ) {
                            CommentsSection(
                                data = data,
                                commentInput = commentInput,
                                onCommentInput = { value ->
                                    commentInput = value
                                    // 一开始改字就退回未发送态：旧的成功/失败提示不能赖在新草稿上。
                                    if (commentSendState != CommentSendState.IDLE) {
                                        commentSendState = CommentSendState.IDLE
                                        commentSendError = null
                                    }
                                },
                                onSend = {
                                    onSendComment(commentInput) { ok, err ->
                                        if (ok) {
                                            commentInput = ""
                                            commentSendState = CommentSendState.SENT
                                            commentSendError = null
                                        } else {
                                            commentSendState = CommentSendState.FAILED
                                            commentSendError = err
                                        }
                                    }
                                },
                                onLoadMore = onLoadMoreComments,
                                sendState = commentSendState,
                                sendError = commentSendError,
                            )
                        }
                        RecommendSection(data = data, masked = masked, onOpenRecommend = onOpenRecommend)
                        Spacer(Modifier.height(24.dp))
                    }
                    Column(
                        modifier = Modifier.width(340.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(22.dp),
                    ) {
                        ChapterSection(data = data, onOpenChapter = onOpenChapter, columns = 2)
                        TagGroupsSection(data = data, onSearchTag = onSearchTag)
                        InfoSection(
                            data = data,
                            onOpenRate = if (data.canRate) {
                                { showRatingDialog = true }
                            } else null,
                        )
                        Spacer(Modifier.height(24.dp))
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 窄栏：头部（Hero）定内容高，卡片区吃剩余高自己滚；
                    // 章节区**定高 280dp 沉底**、自己滚 —— 信息再长章节行也回不到视口外
                    // （第 156 批那条"章节行在视口内"不变量）。
                    Column(
                        modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(headScroll),
                        verticalArrangement = Arrangement.spacedBy(26.dp),
                    ) {
                        PreviewSection(data = data, masked = masked)
                        Box(
                            modifier = Modifier.onGloballyPositioned { commentsTop = it.positionInParent().y },
                        ) {
                            CommentsSection(
                                data = data,
                                commentInput = commentInput,
                                onCommentInput = { value ->
                                    commentInput = value
                                    // 一开始改字就退回未发送态：旧的成功/失败提示不能赖在新草稿上。
                                    if (commentSendState != CommentSendState.IDLE) {
                                        commentSendState = CommentSendState.IDLE
                                        commentSendError = null
                                    }
                                },
                                onSend = {
                                    onSendComment(commentInput) { ok, err ->
                                        if (ok) {
                                            commentInput = ""
                                            commentSendState = CommentSendState.SENT
                                            commentSendError = null
                                        } else {
                                            commentSendState = CommentSendState.FAILED
                                            commentSendError = err
                                        }
                                    }
                                },
                                onLoadMore = onLoadMoreComments,
                                sendState = commentSendState,
                                sendError = commentSendError,
                            )
                        }
                        TagGroupsSection(data = data, onSearchTag = onSearchTag)
                        InfoSection(
                            data = data,
                            onOpenRate = if (data.canRate) {
                                { showRatingDialog = true }
                            } else null,
                        )
                        RecommendSection(data = data, masked = masked, onOpenRecommend = onOpenRecommend)
                    }
                    Spacer(Modifier.height(tokens.spacing.space3))
                    // 内层 LazyColumn 自己滚，外层不再套一层 verticalScroll（双层滚动会抢滚轮）。
                    Column(modifier = Modifier.height(NARROW_CHAPTER_CARD_HEIGHT).fillMaxWidth()) {
                        ChapterSection(
                            data = data,
                            onOpenChapter = onOpenChapter,
                            columns = 3,
                            maxHeight = NARROW_CHAPTER_CARD_HEIGHT,
                        )
                    }
                }
            }
        }

        // ── 打分弹窗：五颗星选一档，提交交核（失败回滚与 toast 在壳里）──
        if (showRatingDialog) {
            RatingDialog(
                current = if (data.userRating > 0f) data.userRating else data.rating,
                onDismiss = { showRatingDialog = false },
                onSubmit = { stars ->
                    showRatingDialog = false
                    onRate(stars)
                },
            )
        }

        // ── 收藏夹弹窗：清单式勾选，勾完不关窗，底部完成收口 ──
        if (showFavoriteDialog) {
            FavoriteDialog(
                data = data,
                onCreateFolder = onCreateFavoriteFolder,
                onToggleFavoriteFolder = onToggleFavoriteFolder,
                onToggleNetworkFavorite = onToggleNetworkFavorite,
                onDismiss = { showFavoriteDialog = false },
            )
        }

        // ── 「离线下载」批量弹窗：勾章节 → enqueue（判据全在 gallery/data 那颗纯件里）──
        if (showBulkDialog && onBulkDownload != null) {
            // 落地：233 项表在弹窗内只建一次，标题 / 工具 / 网格 / 确认都吃这一份。
            val rows = remember(data) { bulkRowsOf(data) }
            var query by remember { mutableStateOf("") }
            var segment by remember { mutableStateOf(0) }
            val visible = remember(rows, query, segment) {
                rows.filter { row ->
                    val hit = query.isBlank() ||
                        desktopComicBulkRowLabel(row).contains(query, ignoreCase = true)
                    val seg = when (segment) {
                        1 -> row.offline != DesktopComicBulkOffline.DOWNLOADED
                        // 「本机」= 本机已有内容（齐档 + 离线不全），不是只认 COMPLETE ——
                        // 否则页数不齐那一档在这一格查不到，看着像没下过。
                        2 -> row.offline != DesktopComicBulkOffline.NONE
                        else -> true
                    }
                    hit && seg
                }
            }
            val columns = if (wide) 2 else 1
            val downloadedCount = rows.count { it.offline == DesktopComicBulkOffline.DOWNLOADED }
            val partialCount = rows.count { it.offline == DesktopComicBulkOffline.PARTIAL }
            // produceState 还在 IO 上核盘时 chapters.offline 全为 null：如实念「正在核对本机」，
            // 不摆一个会误导人的「已下载 0」。
            val offlinePending = data.chapters.any { it.offline == null }
            DesktopMacDialog(
                onDismissRequest = { showBulkDialog = false },
                title = desktopComicBulkTitle(rows),
                // 显式命名 content：判据 ⑬ 靠 `content = {` 认「全选把手住在内容里」。
                content = {
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
                    ) {
                        MacDialogTextButton(
                            text = desktopComicBulkToggleAllLabel(rows, bulkSelected),
                            onClick = { bulkSelected = desktopComicBulkToggleAllNext(rows, bulkSelected) },
                        )
                        MacDialogTextButton(
                            text = com.venera.desktop.settings.DesktopI18n.t("反选", "Invert"),
                            onClick = {
                                val selectable = rows
                                    .filter { it.offline != DesktopComicBulkOffline.DOWNLOADED }
                                    .map { it.episode.id }
                                    .toSet()
                                bulkSelected = selectable - bulkSelected
                            },
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = com.venera.desktop.settings.DesktopI18n.t("共 ${rows.size} 话", "${rows.size} chapters"),
                            fontSize = VeneraType.aux.size, lineHeight = MacDialogBodyLineHeight,
                            color = tokens.color.textTertiary,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = tokens.spacing.space2),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
                    ) {
                        com.venera.desktop.ui.components.DesktopInputField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.width(220.dp),
                            placeholder = com.venera.desktop.settings.DesktopI18n.t("搜索章节", "Search chapters"),
                        )
                        // 「已下载」是徽章专用文本（语义判据钉它在合并树里只出现 1 次），
                        // 分段项避开同名词；「本机」= 已下载到本机那一档。
                        MacDialogSegmented(
                            items = listOf(
                                com.venera.desktop.settings.DesktopI18n.t("全部", "All"),
                                com.venera.desktop.settings.DesktopI18n.t("待下载", "Pending"),
                                com.venera.desktop.settings.DesktopI18n.t("本机", "Local"),
                            ),
                            selectedIndex = segment,
                            onSelect = { segment = it },
                        )
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = macDialogListMaxHeight()),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(visible.chunked(columns), key = { line -> line.first().episode.id }) { line ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                line.forEach { row ->
                                    val checked = bulkSelected.contains(row.episode.id)
                                    val downloaded = row.offline == DesktopComicBulkOffline.DOWNLOADED
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(36.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .macNoRippleClickable {
                                                bulkSelected = if (checked) {
                                                    bulkSelected - row.episode.id
                                                } else {
                                                    bulkSelected + row.episode.id
                                                }
                                            }
                                            .padding(horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        MacDialogCheckbox(
                                            checked = checked,
                                            onCheckedChange = { next ->
                                                bulkSelected = if (next) {
                                                    bulkSelected + row.episode.id
                                                } else {
                                                    bulkSelected - row.episode.id
                                                }
                                            },
                                        )
                                        Text(
                                            text = desktopComicBulkRowLabel(row),
                                            fontSize = VeneraType.aux.size, lineHeight = MacDialogBodyLineHeight,
                                            color = if (downloaded) tokens.color.textTertiary else tokens.color.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        desktopComicBulkBadgeLabel(row)?.let { badge ->
                                            BulkBadge(text = badge, color = tokens.color.textTertiary)
                                        }
                                    }
                                }
                                repeat(columns - line.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = tokens.spacing.space2),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space3, Alignment.End),
                    ) {
                        Text(
                            text = if (offlinePending) {
                                com.venera.desktop.settings.DesktopI18n.t("共 ${rows.size} 话 · 正在核对本机", "${rows.size} chapters · Checking local")
                            } else {
                                com.venera.desktop.settings.DesktopI18n.t(
                                    "共 ${rows.size} · 已下载 $downloadedCount · 离线不全 $partialCount · 本次 ${bulkSelected.size}",
                                    "${rows.size} total · $downloadedCount downloaded · $partialCount partial · ${bulkSelected.size} selected",
                                )
                            },
                            fontSize = VeneraType.aux.size, lineHeight = MacDialogBodyLineHeight,
                            color = tokens.color.textTertiary,
                            modifier = Modifier.weight(1f),
                        )
                        MacDialogTextButton(text = com.venera.desktop.settings.DesktopI18n.t("取消", "Cancel"), onClick = { showBulkDialog = false })
                        MacDialogTextButton(
                            text = desktopComicBulkConfirmLabel(bulkSelected.size),
                            onClick = {
                                onBulkDownload(desktopComicBulkEpisodesOf(rows, bulkSelected))
                                showBulkDialog = false
                            },
                            primary = true,
                            enabled = desktopComicBulkConfirmEnabled(bulkSelected),
                        )
                    }
                }
            })
        }
    }
}

/** 章节数据 → 批量行（弹窗与纯件那套判据吃的形）。 */
private fun bulkRowsOf(data: DesktopDetailViewData): List<DesktopComicBulkRow> =
    data.chapters.map { ch ->
        DesktopComicBulkRow(
            episode = EngineSession.Episode(name = ch.title, id = ch.id, placeholder = ch.placeholder),
            offline = ch.offline ?: DesktopComicBulkOffline.NONE,
        )
    }

/** 弹窗章节网格的高度上限：双列后首屏约 28 话可见，不把整页撑长。 */
private val BULK_LIST_MAX_HEIGHT = 520.dp

/**
 * 目录实吃高度：卡片允许高扣掉标题 / 工具行 / 搜索行 / 底栏 / 内外 padding（≈190dp）。
 *
 * 弹窗放大到 880dp 宽后，520dp 的目录在矮窗口里会把底栏顶出屏外 ——
 * 这里按宿主 Dialog 窗口高自动收口，窗口越高看得越多，最矮也保 160dp（约 4 行）。
 * 计算放在函数里而不是 `content = {` 开头，是为了不把「全选」把手推过源码尺 ⑬ 的 400 字窗口。
 */
@Composable
private fun macDialogListMaxHeight(): Dp {
    val room = LocalMacDialogMaxCardHeight.current - 190.dp
    return when {
        room < 160.dp -> 160.dp
        BULK_LIST_MAX_HEIGHT > room -> room
        else -> BULK_LIST_MAX_HEIGHT
    }
}

/** 窄栏章节卡的定高（沉底、表自己滚；信息区吃剩余高）。 */
internal val NARROW_CHAPTER_CARD_HEIGHT = 280.dp

/** 宽档章节目录的定高上限（目录自己滚，不把整页撑长）。 */
internal val WIDE_CHAPTER_LIST_MAX_HEIGHT = 420.dp

