package com.venera.desktop.pixiv

import com.venera.desktop.ui.theme.VeneraType
import com.venera.desktop.ui.DesktopPushButton
import com.venera.desktop.gallery.ui.GalleryTokens
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.togetherWith
import com.venera.desktop.ui.components.DesktopContextMenuArea
import com.venera.desktop.ui.components.DesktopMenuEntry
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.venera.compose.components.LocalUxPlatformServices
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.components.venera.VeneraCoverMask
import com.venera.compose.gallery.data.GalleryPixivWorks
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.PixivComment
import com.venera.compose.gallery.data.PixivCredentials
import com.venera.compose.gallery.data.PixivUserHead
import com.venera.compose.gallery.data.PixivWorkDetail
import com.venera.compose.gallery.domain.PixivWorkFact
import com.venera.compose.gallery.domain.PixivWorkPage
import com.venera.compose.gallery.domain.PixivImageQuality
import com.venera.desktop.gallery.ui.DesktopCopyGlyph
import com.venera.desktop.gallery.ui.rememberDesktopCopyFeedback
import com.venera.compose.ui.tokens.VeneraTokens
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch



/**
 * 信息栏：画师行（点 → 切画师面板）/ 计数格 / 标题 / 标签（译名优先）/ 收藏 / 评论。
 * 评论按站方规矩：未登录只念一句真话，`commentsOff` 整节不摆；登录腿 `nextUrl` 续页。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DesktopPixivWorkInfoPane(
    illustId: Long,
    title: String,
    detail: PixivWorkDetail?,
    pixiv: GalleryPixivWorks,
    account: PixivCredentials,
    signed: Boolean,
    /** 中央大图落定没有（见壳体 `canvasSettled`）。false 时这一栏的图先不摆，别与大图抢连接。 */
    imagesReady: Boolean = true,
    /** 卡片预览画质：相关作品网格与列表卡同吃这一档。 */
    listQuality: PixivImageQuality = PixivImageQuality.MEDIUM,
    onOpenArtistPane: () -> Unit,
    followBusy: Boolean,
    followNote: String?,
    /** 关注态的就地覆盖（写成功那一刻的值）；null = 用详情里带进来的那一格。 */
    followedOverride: Boolean? = null,
    onFollowToggle: (Boolean) -> Unit,
    onBookmarkToggle: (suspend (Long, Boolean) -> Result<Unit>)?,
    onSavePixivWork: (suspend (PixivWorkDetail) -> String)? = null,
    /**
     * 这一篇**是否已经存过**（按落盘文件判，不按内存账）。
     * `null` = 这一侧没有下载目录，钮就不进"已下载"态。判断只读文件系统，同步调用即可。
     */
    isWorkSaved: ((PixivWorkDetail) -> Boolean)? = null,
    /** 复制当前查看的大图（多图只复制当前页那一档）；返回是否写进了剪贴板。null = 不摆复制入口。 */
    onCopyImage: (suspend (String) -> Boolean)? = null,
    /** 当前页画布上的图地址（右栏画布读它）。 */
    canvasImageUrl: String? = null,
    /** 复制用的地址：固定大图档，不跟详情清晰度档走。null = 不摆复制入口。 */
    copyImageUrl: String? = null,
    /** 点「这位画师的其他作品」的一张卡 → 另一篇作品详情（壳带 from = 当前作品页）。 */
    onOpenPost: (GalleryPost) -> Unit,
    onOpenArtistHome: (Long, String) -> Unit,
    /** 点标签 chip → pixiv 源搜索（壳切 pixiv 搜索模式带词）。 */
    onTagSearch: (String) -> Unit,
) {
    val tokens = VeneraTokens
    val ux = LocalUxPlatformServices.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // 收藏 busy/回执只这一栏用 ⇒ 状态住本栏（不上提）。
    var bookmarkBusy by remember(illustId) { mutableStateOf(false) }
    var bookmarkNote by remember(illustId) { mutableStateOf<String?>(null) }
    val authorId = detail?.authorId

    // 画师头像：详情腿不带这一格，单独补一发 userHead（拿不到就退首字母座，不拿别人的脸顶）。
    val head = produceState<PixivUserHead?>(null, authorId) {
        value = if (authorId == null) null else pixiv.userHead(authorId).getOrNull()
    }

    // 评论：登录腿一页一页续（`nextUrl` 游标；无票据时那腿 404，调都不调）。
    data class CommentFeed(val comments: List<PixivComment>, val nextUrl: String?, val total: Int?)
    var comments by remember(illustId, signed) { mutableStateOf<CommentFeed?>(null) }
    var commentsBusy by remember { mutableStateOf(false) }
    // 取评论失败与"还在取"要分开：一直转着"还在取"是假话（无票据 / 站方没答都落在这里）。
    var commentsFailed by remember(illustId) { mutableStateOf(false) }
    var commentsRetry by remember(illustId) { mutableIntStateOf(0) }
    // 评论折叠（主人口径）：初始只摆 2 条，按钮展开。
    var commentsExpanded by remember(illustId) { mutableStateOf(false) }
    // 评论默认只摆前两条（主人口径）：先让人看见"这里有话说"，再决定要不要展开全部。
    // ── 发评论：草稿 / 选中的贴纸 / 发送中 / 结果一句 / 两个小面板 ──
    // 贴纸表跟着作品页走一遍取（站方词表，实现侧还有 6 小时缓存），不写死 id。
    var commentDraft by remember(illustId) { mutableStateOf("") }
    var pickedStamp by remember(illustId) { mutableStateOf<com.venera.compose.gallery.data.PixivStamp?>(null) }
    var commentSending by remember { mutableStateOf(false) }
    var commentResult by remember(illustId) { mutableStateOf<String?>(null) }
    var emojiPickerOpen by remember { mutableStateOf(false) }
    var stampPickerOpen by remember { mutableStateOf(false) }
    var stampTable by remember { mutableStateOf<List<com.venera.compose.gallery.data.PixivStamp>?>(null) }
    var stampTableFailed by remember { mutableStateOf(false) }

    // 站方相关作品（详情页摆**站方 related 流**，不摆画师其他作品）：
    // `/v2/illust/related` 首页 + `fetchNextPage` 游标续页，右栏滚动近底自动拉下一笔。
    data class RelatedFeed(val posts: List<GalleryPost>, val nextUrl: String?, val failed: Boolean)
    var related by remember(illustId) { mutableStateOf<RelatedFeed?>(null) }
    var relatedBusy by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(illustId) {
        val t0 = System.currentTimeMillis()
        val page = pixiv.relatedIllusts(account.bearerOrNull() ?: "", illustId).getOrNull()
        println("D_取数 相关作品 耗时=${System.currentTimeMillis() - t0}ms")
        related = if (page != null) {
            RelatedFeed(page.works.map { it.post }, page.nextUrl, failed = false)
        } else {
            RelatedFeed(emptyList(), null, failed = true)
        }
    }

    if (signed && detail != null && !detail.commentsOff) {
        androidx.compose.runtime.LaunchedEffect(illustId, commentsRetry) {
            if (comments != null) return@LaunchedEffect
            val bearer = account.bearerOrNull()
            if (bearer == null) {
                commentsFailed = true
                return@LaunchedEffect
            }
            val page = pixiv.illustComments(bearer, illustId).getOrNull()
            if (page == null) {
                commentsFailed = true
            } else {
                commentsFailed = false
                comments = CommentFeed(page.comments, page.nextUrl, page.totalCount)
            }
        }
    }

    // 右栏滚动状态提出来一颗：相关作品的「近底自动续」吃它（无限下滑）。
    val infoScroll = rememberScrollState()
    androidx.compose.runtime.LaunchedEffect(infoScroll) {
        androidx.compose.runtime.snapshotFlow { infoScroll.value >= (infoScroll.maxValue - 600) }
            .distinctUntilChanged()
            .collect { near ->
                val feed = related ?: return@collect
                if (near && feed.nextUrl != null && !relatedBusy) {
                    relatedBusy = true
                    val page = account.bearerOrNull()?.let {
                        pixiv.fetchNextPage(it, feed.nextUrl).getOrNull()
                    }
                    if (page != null) {
                        related = feed.copy(
                            posts = feed.posts + page.works.map { it.post },
                            nextUrl = page.nextUrl,
                        )
                    }
                    relatedBusy = false
                }
            }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 2.dp, bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 卡① 作品头：标题 / 日期 / 作者 / 计数。固定不滚，滚到相关作品时仍在。
        PaneGlassCard(depth = 4f) {
        // ── 标题置顶：右栏第一眼该是"这是什么作品"，画师与计数是它的注脚 ──
        Text(
            text = detail?.title?.ifBlank { title } ?: title,
            fontSize = VeneraType.pageTitle.size, lineHeight = VeneraType.pageTitle.lineHeight,
            fontWeight = VeneraType.cardTitle.weight,
            color = tokens.color.textPrimary,
        )
        val metaBits = buildList {
            if (detail?.pageCount != null && detail.pageCount > 1) add("多图 · ${detail.pageCount}P")
            if (detail?.ugoira == true) add("动图")
            detail?.uploadDate?.takeIf { it.isNotBlank() }?.let { add(prettyUploadDate(it)) }
        }
        if (metaBits.isNotEmpty()) {
            Text(
                text = metaBits.joinToString("  ·  "),
                fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                color = tokens.color.textTertiary,
                modifier = Modifier.padding(top = tokens.spacing.space1),
            )
        }

        // ── 画师卡（一行收齐头像/名/chip/关注，chevron 明示可点）──
        val authorLabel = detail?.post?.author?.ifBlank { detail.authorAccount } ?: title
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = tokens.spacing.space4)
                // 卡中不再套卡：外层已是浮卡，这里只留可点区域与内距。
                .clip(GalleryTokens.Shape.Large)
                .clickable(enabled = authorId != null) { onOpenArtistPane() }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space3),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(tokens.color.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                val avatarUrl = head.value?.avatarUrl
                if (avatarUrl != null) {
                    VeneraCover(
                        url = avatarUrl,
                        contentDescription = authorLabel,
                        mask = VeneraCoverMask.Visible,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        authorLabel.take(1).uppercase().ifBlank { "?" },
                        fontSize = VeneraType.cardTitle.size, lineHeight = VeneraType.cardTitle.lineHeight,
                        fontWeight = VeneraType.cardTitle.weight,
                        color = tokens.color.onPrimaryContainer,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = authorLabel,
                        fontSize = VeneraType.cardTitle.size, lineHeight = VeneraType.cardTitle.lineHeight,
                        fontWeight = VeneraType.cardTitle.weight,
                        color = tokens.color.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(tokens.spacing.space2))
                    Text(
                        text = "画师",
                        fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                        color = tokens.color.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(tokens.color.primary.copy(alpha = 0.14f))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
                Text(
                    text = head.value?.account?.takeIf { it.isNotBlank() }?.let { "@$it" }
                        ?: "点头像看这位画师",
                    fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                    color = tokens.color.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (signed) {
                PixivFollowButton(
                    followed = followedOverride ?: (detail?.isAuthorFollowed == true),
                    busy = followBusy,
                    onToggle = onFollowToggle,
                )
            }
            if (authorId != null) {
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = tokens.color.textTertiary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        followNote?.let {
            Text(text = it, fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight, color = tokens.color.textTertiary)
        }

        // ── 计数行（站方没给的格不摆，判据 `PixivWorkPage.facts`）──
        if (detail != null) {
            val facts = PixivWorkPage.facts(detail.viewCount, detail.post.favCount, detail.uploadDate)
            if (facts.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = tokens.spacing.space4)
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space6),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    facts.forEach { cell ->
                        val icon: ImageVector = when (cell.kind) {
                            PixivWorkFact.VIEWS -> Icons.Outlined.Visibility
                            PixivWorkFact.BOOKMARKS -> Icons.Filled.Bookmark
                            PixivWorkFact.PUBLISHED -> Icons.Outlined.Schedule
                        }
                        val label = when (cell.kind) {
                            PixivWorkFact.VIEWS -> "浏览"
                            PixivWorkFact.BOOKMARKS -> "收藏"
                            PixivWorkFact.PUBLISHED -> "发布"
                        }
                        // 竖排小格：横排 icon+数字+标签在日期长的格上会把
                        // 「发布」挤成竖字 —— 改成值在上、标签在下，宽度天然够。
                        Column(
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    tint = tokens.color.primary,
                                    modifier = Modifier.size(15.dp),
                                )
                                Text(
                                    text = cell.text,
                                    fontSize = VeneraType.cardTitle.size, lineHeight = VeneraType.cardTitle.lineHeight,
                                    fontWeight = VeneraType.cardTitle.weight,
                                    color = tokens.color.textPrimary,
                                    maxLines = 1,
                                )
                            }
                            Text(
                                text = label,
                                fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                                color = tokens.color.textTertiary,
                            )
                        }
                    }
                }
            }
        }
        }
        // 中间只有这一段滚：标签 / 评论 / 相关作品。卡与卡之间留 12 露出底下的图。
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(infoScroll),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── 作品简介（画师写的那段话；站方没给就整块不摆）──
            val caption = detail?.caption?.takeIf { it.isNotBlank() }
            if (caption != null) {
                PaneGlassCard(depth = 6f) {
                    var captionExpanded by remember(caption) { mutableStateOf(false) }
                    PaneSectionHead(
                        title = "作品简介",
                        en = "ABOUT",
                        modifier = Modifier.padding(bottom = tokens.spacing.space2),
                    )
                    Text(
                        text = caption,
                        fontSize = VeneraType.bodySecondary.size,
                        lineHeight = VeneraType.bodySecondary.lineHeight,
                        color = tokens.color.textSecondary,
                        maxLines = if (captionExpanded) Int.MAX_VALUE else 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!captionExpanded && caption.length > 90) {
                        Text(
                            text = "展开",
                            fontSize = VeneraType.aux.size,
                            lineHeight = VeneraType.aux.lineHeight,
                            color = tokens.color.primary,
                            modifier = Modifier
                                .padding(top = tokens.spacing.space2)
                                .clip(RoundedCornerShape(50))
                                .clickable { captionExpanded = true },
                        )
                    }
                }
            }

            PaneGlassCard(depth = 7f) {
            // ── 标签（译名优先；内联 8 + 更多，判据在共享件）──
            // 点 chip = 直接跳 pixiv 源搜索（壳切 pixiv 模式带词）；
            // 右键 chip = 复制原文 / 复制译名（走 `LocalUxPlatformServices.copyToClipboard`）。
            if (detail != null && detail.tags.isNotEmpty()) {
                PaneSectionHead(
                    title = "作品标签",
                    en = "TAGS",
                    modifier = Modifier.padding(bottom = tokens.spacing.space2),
                )
                val expanded = remember { mutableStateOf(false) }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
                    verticalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
                ) {
                    PixivWorkPage.tagsShown(detail.tags, expanded.value).forEach { tag ->
                        val translated = detail.tagTranslations[tag]?.takeIf { it.isNotBlank() }
                        DesktopContextMenuArea(
                            items = {
                                buildList {
                                    add(
                                        DesktopMenuEntry.Item(
                                            label = "复制「$tag」",
                                            shortcut = "Ctrl+C",
                                            icon = Icons.Outlined.Description,
                                            onClick = {
                                                ux.copyToClipboard(tag)
                                                ux.toast("已复制标签")
                                            },
                                        ),
                                    )
                                    if (translated != null) {
                                        add(
                                            DesktopMenuEntry.Item(
                                                label = "复制翻译「$translated」",
                                                icon = Icons.Outlined.Translate,
                                                onClick = {
                                                    ux.copyToClipboard(translated)
                                                    ux.toast("已复制翻译")
                                                },
                                            ),
                                        )
                                    }
                                }
                            },
                        ) {
                            Text(
                                text = translated ?: tag,
                                fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                                color = tokens.color.textSecondary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(tokens.color.surfaceContainerHigh)
                                    .clickable { onTagSearch(tag) }
                                    .padding(horizontal = 11.dp, vertical = 5.dp),
                            )
                        }
                    }
                    val hidden = PixivWorkPage.tagsHidden(detail.tags)
                    if (hidden > 0 && !expanded.value) {
                        Text(
                            text = "更多 +$hidden",
                            fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                            color = tokens.color.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(tokens.color.primary.copy(alpha = 0.14f))
                                .clickable { expanded.value = true }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            }

            PaneGlassCard(depth = 5f) {
            // ── 评论 ──
            if (detail != null && !detail.commentsOff) {
                val total = comments?.total ?: detail.commentCount
                if (!signed) {
                    // 匿名态：评论读、发都只走登录腿，所以这一节不留空位 —— 把"为什么空"和
                    // "登录之后能做什么"一次说清，不摆一个点了会 404 的输入框。
                    Column(modifier = Modifier.padding(top = tokens.spacing.space5)) {
                        PaneSectionHead(title = "评论", en = "COMMENTS")
                        Text(
                            text = if ((total ?: 0) > 0) {
                                "登录后可看这 $total 条评论，也能自己发表。"
                            } else {
                                "登录后可查看评论、发表评论与贴纸。"
                            },
                            fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                            color = tokens.color.textTertiary,
                        )
                    }
                } else {
                    PaneSectionHead(
                        title = "评论",
                        en = "COMMENTS",
                        count = total?.let { "$it 条" },
                        modifier = Modifier.padding(top = tokens.spacing.space5, bottom = tokens.spacing.space2),
                    )

                    // ── 发评论：正文 + 表情（颜文字，纯文本插入）+ 贴纸（`stamp_id`）──
                    // 站方关了评论这一节本来就不摆（外层判 `commentsOff`）；未登录那支只念一句真话。
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = tokens.spacing.space2),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
                    ) {
                        com.venera.desktop.ui.components.DesktopInputField(
                            value = commentDraft,
                            // 发送途中锁输入：这一句还在路上，再改正文只会让屏上写着 A、发出去 B。
                            enabled = !commentSending,
                            // 站方上限 140：超了不再收字，而不是发出去被站方弹回来。
                            onValueChange = { if (it.length <= 140) commentDraft = it },
                            modifier = Modifier.weight(1f),
                            placeholder = "写评论…（最多 140 字）",
                        )
                        CommentGlyphButton(
                            icon = Icons.Outlined.EmojiEmotions,
                            description = "表情",
                            active = emojiPickerOpen,
                            onClick = {
                                emojiPickerOpen = !emojiPickerOpen
                                stampPickerOpen = false
                            },
                        )
                        CommentGlyphButton(
                            icon = Icons.Outlined.StickyNote2,
                            description = "贴纸",
                            active = stampPickerOpen || pickedStamp != null,
                            onClick = {
                                stampPickerOpen = !stampPickerOpen
                                emojiPickerOpen = false
                                // 贴纸表按需取一次（取不到就照实念，不摆一排空格子）。
                                if (stampPickerOpen && stampTable == null && !stampTableFailed) {
                                    scope.launch {
                                        val bearer = account.bearerOrNull()
                                        val res = bearer?.let { pixiv.stamps(it) }
                                        if (res != null && res.isSuccess) {
                                            stampTable = res.getOrNull().orEmpty()
                                        } else {
                                            stampTableFailed = true
                                        }
                                    }
                                }
                            },
                        )
                        DesktopPushButton(
                            enabled = !commentSending && (commentDraft.isNotBlank() || pickedStamp != null),
                            onClick = {
                                if (commentSending) return@DesktopPushButton
                                scope.launch {
                                    val bearer = account.bearerOrNull()
                                    if (bearer == null) {
                                        commentResult = "登录票据过期了，重新登录一次再发。"
                                        return@launch
                                    }
                                    commentSending = true
                                    commentResult = null
                                    val res = pixiv.commentAdd(
                                        bearer = bearer,
                                        illustId = illustId,
                                        comment = commentDraft.trim(),
                                        stampId = pickedStamp?.id,
                                    )
                                    commentSending = false
                                    if (res.isSuccess) {
                                        commentDraft = ""
                                        pickedStamp = null
                                        emojiPickerOpen = false
                                        stampPickerOpen = false
                                        commentResult = "已发表"
                                        // 重取第一页：条数与正文要是站方那边的现况，不是本地猜的。
                                        val page = pixiv.illustComments(bearer, illustId, null).getOrNull()
                                        if (page != null) {
                                            comments = CommentFeed(page.comments, page.nextUrl, page.totalCount)
                                        }
                                    } else {
                                        // 站方那句原话照念（写口的抛句里带着它），不缩成"发送失败"。
                                        commentResult = res.exceptionOrNull()?.message ?: "发送失败"
                                    }
                                }
                            },
                        ) { Text(if (commentSending) "发送中…" else "发送") }
                    }
                    pickedStamp?.let { stamp ->
                        Row(
                            modifier = Modifier.padding(bottom = tokens.spacing.space2),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            AsyncImage(
                                model = stamp.url,
                                contentDescription = "已选贴纸",
                                modifier = Modifier.size(22.dp),
                            )
                            Text(
                                text = "已选贴纸 · 再点一次同一格取消",
                                fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                                color = tokens.color.textTertiary,
                            )
                        }
                    }
                    commentResult?.let { note ->
                        Text(
                            text = note,
                            fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                            color = if (note == "已发表") tokens.color.primary else tokens.color.textSecondary,
                            modifier = Modifier.padding(bottom = tokens.spacing.space2),
                        )
                    }
                    if (emojiPickerOpen) {
                        // 站方的"表情"在评论里就是一段颜文字（纯文本），所以这里只做插入，不做图片。
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = tokens.spacing.space2),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            PIXIV_COMMENT_EMOJI.forEach { face ->
                                Text(
                                    text = face,
                                    fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                                    color = tokens.color.textSecondary,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(tokens.color.surfaceContainerHigh.copy(alpha = 0.5f))
                                        .clickable {
                                            if (commentDraft.length + face.length <= 140) {
                                                commentDraft += face
                                            }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                    if (stampPickerOpen) {
                        when {
                            stampTableFailed -> Text(
                                text = "贴纸表没取到（站方没答这一腿），先发文字或表情。",
                                fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                                color = tokens.color.textTertiary,
                                modifier = Modifier.padding(bottom = tokens.spacing.space2),
                            )
                            stampTable == null -> Text(
                                text = "贴纸表取中…",
                                fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                                color = tokens.color.textTertiary,
                                modifier = Modifier.padding(bottom = tokens.spacing.space2),
                            )
                            stampTable!!.isEmpty() -> Text(
                                text = "站方这会儿没有可贴的贴纸。",
                                fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                                color = tokens.color.textTertiary,
                                modifier = Modifier.padding(bottom = tokens.spacing.space2),
                            )
                            else -> Column(modifier = Modifier.padding(bottom = tokens.spacing.space2)) {
                                // 站方那一批贴纸是几十枚，一行横排只能看见前八枚、还得靠猜能横滚。
                                // 改成多行网格 + 限高竖滚：一屏看得见十几枚，滚到底就是全部。
                                Text(
                                    text = "${stampTable!!.size} 枚贴纸",
                                    fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                                    color = tokens.color.textTertiary,
                                    modifier = Modifier.padding(bottom = 4.dp),
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 176.dp)
                                        .verticalScroll(rememberScrollState()),
                                ) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        stampTable!!.forEach { stamp ->
                                            val selected = pickedStamp?.id == stamp.id
                                            Box(
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(
                                                        if (selected) {
                                                            tokens.color.primary.copy(alpha = 0.18f)
                                                        } else {
                                                            tokens.color.surfaceContainerHigh.copy(alpha = 0.4f)
                                                        },
                                                    )
                                                    .clickable {
                                                        pickedStamp = if (selected) null else stamp
                                                        stampPickerOpen = false
                                                    },
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                AsyncImage(
                                                    model = stamp.url,
                                                    contentDescription = "贴纸 ${stamp.id}",
                                                    modifier = Modifier.size(32.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    val feed = comments
                    when {
                        feed == null && commentsFailed -> Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
                        ) {
                            Text(
                                text = "评论没取到，站方这一跳没有答上来。",
                                fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                                color = tokens.color.textTertiary,
                                modifier = Modifier.weight(1f),
                            )
                            DesktopPushButton(onClick = { commentsRetry++ }) { Text("重试") }
                        }
                        feed == null -> Text(
                            text = "评论取中…",
                            fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                            color = tokens.color.textTertiary,
                        )
                        feed.comments.isEmpty() -> Text(
                            text = "还没有评论，来写第一条。",
                            fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                            color = tokens.color.textTertiary,
                        )
                        else -> {
                            // 两级：整节默认收起，展开后一次摆 2 条，再「更多评论」翻页。
                            val shown = if (commentsExpanded) feed.comments else feed.comments.take(2)
                            shown.forEach { c -> DesktopPixivCommentRow(c) }
                            val hidden = feed.comments.size - shown.size
                            // 摆"展开"只有两种情况：手里还有没摆出来的，或站方还有下一页。
                            if ((!commentsExpanded && (feed.comments.size > 2 || feed.nextUrl != null)) ||
                                (commentsExpanded && feed.nextUrl != null)
                            ) {
                                // 通栏虚位 ghost 钮（按设计稿对齐，不再用实心 pill）。
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = tokens.spacing.space2)
                                        .clip(GalleryTokens.Shape.Large)
                                        .background(tokens.color.surfaceContainerHigh.copy(alpha = 0.35f))
                                        .border(1.dp, tokens.color.outlineVariant, GalleryTokens.Shape.Large)
                                        .clickable(enabled = !commentsBusy) {
                                            commentsExpanded = true
                                            if (feed.nextUrl != null && !commentsBusy) {
                                                commentsBusy = true
                                                scope.launch {
                                                    val bearer = account.bearerOrNull()
                                                    val page = bearer?.let {
                                                        pixiv.illustComments(it, illustId, feed.nextUrl).getOrNull()
                                                    }
                                                    if (page != null) {
                                                        comments = CommentFeed(
                                                            feed.comments + page.comments,
                                                            page.nextUrl,
                                                            feed.total,
                                                        )
                                                    }
                                                    commentsBusy = false
                                                }
                                            }
                                        }
                                        .padding(vertical = 9.dp),
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    Text(
                                        when {
                                            commentsBusy -> "取评论中…"
                                            !commentsExpanded -> "展开全部 ${feed.comments.size} 条评论"
                                            hidden > 0 -> "更多评论（还有 $hidden 条）"
                                            else -> "更多评论"
                                        },
                                        fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                                        color = tokens.color.textSecondary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            }

            PaneGlassCard(depth = 9f) {
            // ── 站方相关作品（替换掉「这位画师的其他作品」）──
            // 数据腿 = `/v2/illust/related`（接口在 GalleryPixivWorks 那两颗：relatedIllusts +
            // fetchNextPage），右栏滚动近底自动续 —— 点一张 = 另一篇作品详情。
            when (val rel = related) {
                null -> {}
                else -> {
                    if (rel.failed) {
                        Text(
                            text = "相关作品加载失败",
                            fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                            color = tokens.color.textTertiary,
                            modifier = Modifier.padding(top = tokens.spacing.space5),
                        )
                    } else if (rel.posts.isNotEmpty()) {
                        PaneSectionHead(
                            title = "相关作品",
                            en = "RELATED",
                            count = "${rel.posts.size} 张",
                            modifier = Modifier.padding(top = tokens.spacing.space5, bottom = tokens.spacing.space2),
                        )
                        // 统一 3 列 1:1.2 网格（旧的是大小不一的 2 列），页数 chip。
                        // 这些缩略图等中央大图落定再起，否则大图会被抢成糊背板。
                        if (!imagesReady) {
                            Text(
                                text = "大图加载中",
                                fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                                color = tokens.color.textTertiary,
                            )
                        } else {
                        rel.posts.chunked(2).forEach { row ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = tokens.spacing.space2),
                                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
                            ) {
                                row.forEach { post ->
                                    // 与列表卡同一套悬停语言：微放大 + 描边提亮。浅色档不铺投影 ——
                                    // 低 elevation 的 Skia 阴影在浅底上会渲染成硬边灰框。
                                    val hover = remember { MutableInteractionSource() }
                                    val hovered by hover.collectIsHoveredAsState()
                                    val thumbScale by animateFloatAsState(
                                        targetValue = if (hovered) 1.03f else 1f,
                                        animationSpec = androidx.compose.animation.core.spring(
                                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                                            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
                                        ),
                                        label = "pixivRelatedHover",
                                    )
                                    val thumbBorder by animateColorAsState(
                                        targetValue = if (hovered) tokens.color.primary.copy(alpha = 0.55f)
                                        else Color.Transparent,
                                        animationSpec = androidx.compose.animation.core.tween(140),
                                        label = "pixivRelatedBorder",
                                    )
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f / 1.2f)
                                            .graphicsLayer {
                                                scaleX = thumbScale
                                                scaleY = thumbScale
                                            }
                                            .clip(RoundedCornerShape(tokens.shape.small))
                                            .border(1.dp, thumbBorder, RoundedCornerShape(tokens.shape.small))
                                            .hoverable(hover)
                                            .clickable { onOpenPost(post) },
                                    ) {
                                        VeneraCover(
                                            // 相关作品吃「卡片预览」那一档（设置里可换低/中/大/原图），
                                            // 不自己写死中图 —— 与列表卡同一个取址口。
                                            url = com.venera.compose.gallery.domain.GallerySettingsModel
                                                .pixivWallUrl(post, listQuality),
                                            contentDescription = post.author.ifBlank { "pixiv #${post.id}" },
                                            mask = VeneraCoverMask.Visible,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                        if ((post.pageCount ?: 0) > 1) {
                                            Text(
                                                text = "${post.pageCount}P",
                                                fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                                                fontWeight = VeneraType.cardTitle.weight,
                                                color = Color.White,
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(5.dp)
                                                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
                                                    .padding(horizontal = 6.dp, vertical = 1.dp),
                                            )
                                        }
                                    }
                                }
                                repeat(2 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                        if (rel.nextUrl != null) {
                            Text(
                                text = if (relatedBusy) "取更多中…" else "下滑继续取相关作品…",
                                fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                                color = tokens.color.textTertiary,
                            )
                        }
                        }
                    }
                }
            }
            }

            PaneGlassCard(depth = 6f) {
            // ── 完整画师主页（面板外的那条独立页出口；作者号没给就不摆）──
            if (authorId != null) {
                DesktopPushButton(
                    onClick = { onOpenArtistHome(authorId, detail?.post?.author?.ifBlank { detail.authorAccount } ?: "画师") },
                    modifier = Modifier.padding(top = tokens.spacing.space6),
                ) { Text("打开完整画师主页 →") }
            }
            }
        }
        // 卡⑤ 操作条：滚到哪儿都在，与上面那些卡同一层玻璃。
        PaneGlassCard(depth = 11f) {

        // ── 动作区：下载（原图/多图）与 收藏──
        if (detail != null && (onSavePixivWork != null || (signed && onBookmarkToggle != null))) {
            var savingWork by remember(illustId) { mutableStateOf(false) }
            // 已存过没有：按下载目录里的文件名判（`pixiv_<id>.jpg` / `pixiv_<id>_pN.jpg`）。
            var savedOnDisk by remember(illustId, detail.pageCount) {
                mutableStateOf(isWorkSaved?.invoke(detail) == true)
            }
            // 收藏态本地持有：站方 detail 不会因为这一按重取，继续读它按钮就永远不翻。
            var bookmarked by remember(illustId, detail.isBookmarked) {
                mutableStateOf(detail.isBookmarked == true)
            }
            val downloadLabel = if (detail.pageCount > 1) "下载全部 (${detail.pageCount}P)" else "下载原图"
            // 三态共用的开关式配色：底色/字色都用动画过渡，按下那一刻不闪。
            val saveBg by animateColorAsState(
                targetValue = if (savedOnDisk) tokens.color.surfaceContainerHigh else tokens.color.primary,
                animationSpec = androidx.compose.animation.core.tween(280),
                label = "pixivSaveBg",
            )
            val saveFg by animateColorAsState(
                targetValue = if (savedOnDisk) tokens.color.textSecondary else tokens.color.onPrimary,
                animationSpec = androidx.compose.animation.core.tween(280),
                label = "pixivSaveFg",
            )
            val saveBorder by animateColorAsState(
                targetValue = if (savedOnDisk) tokens.color.outlineVariant else tokens.color.primary,
                animationSpec = androidx.compose.animation.core.tween(280),
                label = "pixivSaveBorder",
            )
            val bookmarkBg by animateColorAsState(
                targetValue = if (bookmarked) tokens.color.primary.copy(alpha = 0.18f) else tokens.color.surfaceContainerHigh,
                animationSpec = androidx.compose.animation.core.tween(240),
                label = "pixivBookmarkBg",
            )
            val bookmarkBorder by animateColorAsState(
                targetValue = if (bookmarked) tokens.color.primary else tokens.color.outlineVariant.copy(alpha = 0.5f),
                animationSpec = androidx.compose.animation.core.tween(240),
                label = "pixivBookmarkBorder",
            )
            val bookmarkFg by animateColorAsState(
                targetValue = if (bookmarked) tokens.color.primary else tokens.color.textSecondary,
                animationSpec = androidx.compose.animation.core.tween(240),
                label = "pixivBookmarkFg",
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = tokens.spacing.space4),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 下载主钮
                if (onSavePixivWork != null) {
                    // 与隔壁收藏钮同一套按压反馈：一枚回弹一枚不动，看着像坏了。
                    val saveInteraction = remember { MutableInteractionSource() }
                    val savePressed by saveInteraction.collectIsPressedAsState()
                    val saveScale by animateFloatAsState(
                        targetValue = if (savePressed && !savingWork) 0.96f else 1f,
                        animationSpec = androidx.compose.animation.core.tween(150),
                        label = "pixivSavePress",
                    )
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(GalleryTokens.Shape.Large)
                            // 已下载态退成次要档（底/字/描边三层一起过渡），一眼分得开"存过"与"没存过"。
                            .background(saveBg)
                            .border(1.dp, saveBorder, GalleryTokens.Shape.Large)
                            .graphicsLayer {
                                scaleX = saveScale
                                scaleY = saveScale
                            }
                            .clickable(
                                interactionSource = saveInteraction,
                                indication = null,
                                enabled = !savingWork,
                            ) {
                                // 已下载态再点不再落第二份（落盘器遇同名会自动加 " (1)"，白占盘）。
                                if (savedOnDisk) {
                                    ux.toast("已下载过，不重复存。")
                                    return@clickable
                                }
                                savingWork = true
                                scope.launch {
                                    val res = runCatching { onSavePixivWork.invoke(detail) }
                                        .getOrElse { "存图失败：${it::class.java.simpleName}" }
                                    savingWork = false
                                    // 落盘成功才翻"已下载"；失败保持原态，不发假信号。
                                    if (res.startsWith("已保存")) {
                                        savedOnDisk = true
                                    }
                                    ux.toast(res)
                                }
                            }
                            .padding(vertical = 11.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 图标三态切换（未存 / 下载中 / 已下载），180ms 交叉淡入。
                        androidx.compose.animation.Crossfade(
                            targetState = when {
                                savingWork -> 1
                                savedOnDisk -> 2
                                else -> 0
                            },
                            animationSpec = androidx.compose.animation.core.tween(180),
                            label = "pixivSaveIcon",
                        ) { state ->
                            Icon(
                                imageVector = when (state) {
                                    2 -> Icons.Outlined.CheckCircle
                                    else -> Icons.Outlined.FileDownload
                                },
                                contentDescription = null,
                                tint = saveFg,
                                modifier = Modifier.size(17.dp),
                            )
                        }
                        androidx.compose.animation.AnimatedContent(
                            targetState = when {
                                savingWork -> "下载中…"
                                savedOnDisk -> "已下载"
                                else -> downloadLabel
                            },
                            transitionSpec = {
                                androidx.compose.animation.fadeIn(
                                    androidx.compose.animation.core.tween(160),
                                ) togetherWith androidx.compose.animation.fadeOut(
                                    androidx.compose.animation.core.tween(120),
                                )
                            },
                            label = "pixivSaveLabel",
                        ) { label ->
                            Text(
                                text = label,
                                fontSize = VeneraType.bodySecondary.size,
                                lineHeight = VeneraType.bodySecondary.lineHeight,
                                fontWeight = VeneraType.cardTitle.weight,
                                color = saveFg,
                            )
                        }
                    }
                }

                // 收藏按钮（已登录才摆）
                if (signed && onBookmarkToggle != null) {
                    // 按下反馈：0.96 回弹 / 150ms（桌面按压口径，
                    // 与首页那些按钮同档），涟漪一律不给（indication = null 是桌面语义）。
                    val bookmarkInteraction = remember { MutableInteractionSource() }
                    val bookmarkPressed by bookmarkInteraction.collectIsPressedAsState()
                    val bookmarkScale by animateFloatAsState(
                        targetValue = if (bookmarkPressed && !bookmarkBusy) 0.96f else 1f,
                        animationSpec = androidx.compose.animation.core.tween(150),
                        label = "pixivBookmarkPress",
                    )
                    Row(
                        modifier = Modifier
                            .then(if (onSavePixivWork == null) Modifier.fillMaxWidth() else Modifier)
                            .clip(GalleryTokens.Shape.Large)
                            .background(bookmarkBg)
                            .border(1.dp, bookmarkBorder, GalleryTokens.Shape.Large)
                            .graphicsLayer {
                                scaleX = bookmarkScale
                                scaleY = bookmarkScale
                            }
                            .clickable(
                                interactionSource = bookmarkInteraction,
                                indication = null,
                                enabled = !bookmarkBusy,
                            ) {
                                bookmarkBusy = true
                                bookmarkNote = null
                                val want = !bookmarked
                                scope.launch {
                                    val result = onBookmarkToggle.invoke(illustId, want)
                                    bookmarkBusy = false
                                    result.onSuccess {
                                        // 站方答成功才翻态：失败留在原档，按钮不许先动。
                                        bookmarked = want
                                    }
                                    bookmarkNote = result.fold(
                                        onSuccess = { if (want) "已收藏。" else "已取消收藏。" },
                                        onFailure = { t -> "没写上：${t.message?.take(120) ?: t.javaClass.simpleName}" },
                                    )
                                }
                            }
                            .padding(horizontal = 18.dp, vertical = 11.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.animation.Crossfade(
                            targetState = bookmarked,
                            animationSpec = androidx.compose.animation.core.tween(180),
                            label = "pixivBookmarkIcon",
                        ) { on ->
                            Icon(
                                imageVector = if (on) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = null,
                                tint = bookmarkFg,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        androidx.compose.animation.AnimatedContent(
                            targetState = if (bookmarked) "已收藏" else "收藏",
                            transitionSpec = {
                                androidx.compose.animation.fadeIn(
                                    androidx.compose.animation.core.tween(160),
                                ) togetherWith androidx.compose.animation.fadeOut(
                                    androidx.compose.animation.core.tween(120),
                                )
                            },
                            label = "pixivBookmarkLabel",
                        ) { label ->
                            Text(
                                text = label,
                                fontSize = VeneraType.bodySecondary.size,
                                lineHeight = VeneraType.bodySecondary.lineHeight,
                                fontWeight = VeneraType.cardTitle.weight,
                                color = bookmarkFg,
                            )
                        }
                    }
                    // 复制当前页大图：与画布右键同一件事的显式把手，转圈 → 对勾 / 叉号。
                    if (onCopyImage != null && copyImageUrl != null) {
                        val copyFeedback = rememberDesktopCopyFeedback()
                        val copyInteraction = remember { MutableInteractionSource() }
                        val copyPressed by copyInteraction.collectIsPressedAsState()
                        val copyScale by animateFloatAsState(
                            targetValue = if (copyPressed) 0.96f else 1f,
                            animationSpec = androidx.compose.animation.core.tween(150),
                            label = "pixivCopyPress",
                        )
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .graphicsLayer {
                                    scaleX = copyScale
                                    scaleY = copyScale
                                }
                                .clip(GalleryTokens.Shape.Large)
                                .background(tokens.color.surfaceContainerHigh)
                                .border(1.dp, tokens.color.outlineVariant.copy(alpha = 0.5f), GalleryTokens.Shape.Large)
                                .clickable(
                                    interactionSource = copyInteraction,
                                    indication = null,
                                ) {
                                    copyFeedback.run {
                                        val ok = runCatching { onCopyImage(copyImageUrl) }
                                            .getOrDefault(false)
                                        ux.toast(
                                            if (ok) {
                                                "图片已复制到剪贴板"
                                            } else {
                                                "复制失败：这张图没能放进剪贴板"
                                            },
                                        )
                                        ok
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            DesktopCopyGlyph(
                                state = copyFeedback.state,
                                idleTint = tokens.color.textSecondary,
                                doneTint = tokens.color.primary,
                                failedTint = GalleryTokens.Color.StatusDanger,
                                modifier = Modifier.size(17.dp),
                            )
                        }
                    }
                    // 交系统默认浏览器：能力位不给就不摆（按了没反应的钮不许出现）。
                    if (com.venera.desktop.gallery.ui.desktopCanBrowseExternally()) {
                        val openInteraction = remember { MutableInteractionSource() }
                        val openPressed by openInteraction.collectIsPressedAsState()
                        val openHovered by openInteraction.collectIsHoveredAsState()
                        val openScale by animateFloatAsState(
                            targetValue = if (openPressed) 0.92f else 1f,
                            animationSpec = androidx.compose.animation.core.tween(150),
                            label = "pixivOpenBrowserPress",
                        )
                        val openBg by animateColorAsState(
                            targetValue = when {
                                openHovered -> tokens.color.surfaceVariant
                                else -> tokens.color.surfaceContainerHigh
                            },
                            animationSpec = androidx.compose.animation.core.tween(150),
                            label = "pixivOpenBrowserBg",
                        )
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .graphicsLayer {
                                    scaleX = openScale
                                    scaleY = openScale
                                }
                                .clip(GalleryTokens.Shape.Large)
                                .background(openBg)
                                .border(1.dp, tokens.color.outlineVariant.copy(alpha = 0.5f), GalleryTokens.Shape.Large)
                                .clickable(
                                    interactionSource = openInteraction,
                                    indication = null,
                                ) {
                                    scope.launch {
                                        // 站方规范地址 artworks/{id}；pixiv.net 字面走 format 绕行
                                        //（该字面的唯一出处口径与画师页复制钮一致）。
                                        val url = "https://%s/artworks/%d".format("www.pixiv.net", illustId)
                                        val error = com.venera.desktop.gallery.ui.openInBrowser(url)
                                        ux.toast(error ?: "已在系统浏览器打开")
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("↗", fontSize = VeneraType.body.size, lineHeight = VeneraType.body.lineHeight, color = tokens.color.textSecondary)
                        }
                    }
                    // 复制「链接 + 标题 + 画师名」三行：转帖/存档的最短路径，一次拿齐。
                    val copyInfoInteraction = remember { MutableInteractionSource() }
                    val copyInfoPressed by copyInfoInteraction.collectIsPressedAsState()
                    val copyInfoHovered by copyInfoInteraction.collectIsHoveredAsState()
                    val copyInfoScale by animateFloatAsState(
                        targetValue = if (copyInfoPressed) 0.92f else 1f,
                        animationSpec = androidx.compose.animation.core.tween(150),
                        label = "pixivCopyInfoPress",
                    )
                    val copyInfoBg by animateColorAsState(
                        targetValue = when {
                            copyInfoHovered -> tokens.color.surfaceVariant
                            else -> tokens.color.surfaceContainerHigh
                        },
                        animationSpec = androidx.compose.animation.core.tween(150),
                        label = "pixivCopyInfoBg",
                    )
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .graphicsLayer {
                                scaleX = copyInfoScale
                                scaleY = copyInfoScale
                            }
                            .clip(GalleryTokens.Shape.Large)
                            .background(copyInfoBg)
                            .border(1.dp, tokens.color.outlineVariant.copy(alpha = 0.5f), GalleryTokens.Shape.Large)
                            .clickable(
                                interactionSource = copyInfoInteraction,
                                indication = null,
                            ) {
                                // 站方字段可能带尾换行，各段裁过再拼，不然画师名前会空一行。
                                val artist = detail?.post?.author?.ifBlank { detail.authorAccount }?.trim() ?: ""
                                val text = buildString {
                                    append("https://%s/artworks/%d".format("www.pixiv.net", illustId))
                                    append('\n')
                                    append(title.trim())
                                    if (artist.isNotBlank()) append('\n').append(artist)
                                }
                                ux.copyToClipboard(text)
                                ux.toast("已复制链接、标题与画师名")
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("⧉", fontSize = VeneraType.body.size, lineHeight = VeneraType.body.lineHeight, color = tokens.color.textSecondary)
                    }
                }
            }
            bookmarkNote?.let {
                Text(
                    text = it,
                    fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                    color = tokens.color.textTertiary,
                    modifier = Modifier.padding(top = tokens.spacing.space1),
                )
            }
        }
        }
    }
}

