package com.venera.desktop.detail

import com.venera.desktop.ui.theme.VeneraType
import com.venera.desktop.ui.DesktopButton
import com.venera.desktop.ui.DesktopButtonColors
import com.venera.desktop.ui.DesktopButtonSize
import com.venera.desktop.ui.DesktopButtonVariant
import com.venera.desktop.ui.DesktopPushButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.venera.desktop.gallery.ui.LocalChromePalette
import com.venera.desktop.ui.components.desktopCoverFloat
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.venera.compose.components.LocalUxPlatformServices
import com.venera.compose.components.venera.VeneraChip
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.components.venera.VeneraCoverMask
import com.venera.desktop.ui.DesktopMacDialog
import com.venera.desktop.ui.MacDialogTextButton
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.gallery.ui.DesktopTheme
import com.venera.desktop.gallery.ui.DESKTOP_HISTORY_CHAPTER_MARK_DESCRIPTION
import com.venera.desktop.gallery.ui.desktopComicReadButtonLabel


/**
 * 简介折叠那一档的起算字数（对岸"超过三行才给展开"的桌面形 —— 行数折成字数，离屏量不到行）。
 */
internal const val DESKTOP_DESC_FOLD_LIMIT: Int = 120

/**
 * 简介那句要不要折叠（纯判断，屏与判据共用一颗）。
 *
 * `foldable=false` 时那枚"展开/收起"把手**整颗不摆** —— 一本 80 字的简介摆一枚
 * 永远展开不了什么的把手，就是假开关那一族。
 */
internal fun desktopDescFolded(expanded: Boolean, description: String): Boolean =
    !expanded && description.length > DESKTOP_DESC_FOLD_LIMIT

/**
 * 主胶囊：继续阅读 / 开始阅读（文案由 [desktopComicReadButtonLabel] 分档）。
 * [capsule] = null 时不摆 —— 「还没读回来 / 没章节」不摆一枚按了会编造落点的钮。
 *
 * 编辑排版版：999 全圆 → r9（与全仓钮同族）；AccentFill → ChromePalette.accent；
 * 话名小字移到胶囊内右侧（发丝分隔）。进度不再画在胶囊内——hero 底部那条全宽线承担。
 */
@Composable
internal fun ReadCapsule(
    capsule: DesktopReadCapsuleData?,
    /** 那行小字的成品串（hero 现算递进来的那枚；只有真有历史才非空）。 */
    caption: String?,
    onResume: () -> Unit,
) {
    if (capsule == null) return
    val accentFill = DesktopTheme.AccentFill
    DesktopButton(
        onClick = onResume,
        size = DesktopButtonSize.Large,
        variant = DesktopButtonVariant.Primary,
        horizontalPadding = 18.dp,
        colors = DesktopButtonColors(
            bg = accentFill,
            bgHover = accentFill.copy(alpha = 0.88f),
            border = Color.Transparent,
            borderHover = Color.Transparent,
            content = DesktopTheme.TextPrimary,
            contentHover = DesktopTheme.TextPrimary,
        ),
    ) {
        Text(
            text = desktopComicReadButtonLabel(capsule.hasHistory),
            fontSize = VeneraType.bodySecondary.size,
            lineHeight = VeneraType.bodySecondary.lineHeight,
            fontWeight = VeneraType.cardTitle.weight,
        )
        // 那一行小字只在真有历史时才摆：没历史而摆一行「继续 第 0 页」是编的。
        if (capsule.hasHistory) {
            Spacer(Modifier.width(4.dp))
            Box(Modifier.width(1.dp).height(15.dp).background(Color.White.copy(alpha = 0.30f)))
            Spacer(Modifier.width(4.dp))
            Text(
                text = caption.orEmpty(),
                fontSize = VeneraType.micro.size,
                lineHeight = VeneraType.micro.lineHeight,
                maxLines = 1,
            )
        }
    }
}



// ════════════════════════════ 卡片族 ════════════════════════════

/**
 * 段标题（编辑排版版）：13.5/700 题名 + 11 tabular 计数 + 可选尾按钮，
 * 下方 0.5dp 发丝。替代原 `DetailCard` 的「竖标尺 + 玻璃盒」——暗底上六段同形盒壳等重且廉价，
 * 层级改由字号与留白承担。
 */
@Composable
internal fun SectionHead(
    title: String,
    count: String? = null,
    tail: (@Composable () -> Unit)? = null,
) {
    val palette = LocalChromePalette.current
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(title, fontSize = VeneraType.bodySecondary.size, lineHeight = VeneraType.bodySecondary.lineHeight, fontWeight = VeneraType.cardTitle.weight, color = palette.ink1)
            if (!count.isNullOrBlank()) {
                Spacer(Modifier.width(7.dp))
                Text(count, fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight, color = palette.ink3)
            }
            Spacer(Modifier.weight(1f))
            tail?.invoke()
        }
        Spacer(Modifier.height(9.dp))
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(palette.hairline))
        Spacer(Modifier.height(14.dp))
    }
}

/** 段标题右端的文字把手（查看更多 / 倒序）。 */
@Composable
internal fun SectionTailButton(text: String, accent: Boolean = true, onClick: () -> Unit) {
    val palette = LocalChromePalette.current
    Text(
        text = text,
        fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
        color = if (accent) palette.accent else palette.ink3,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 1.dp),
    )
}



/** 预览格最小宽（格子定宽、不随窗口缩放；列数由容器宽算）。 */
internal val PREVIEW_MIN_CELL = 136.dp

/**
 * 预览图集（编辑排版版）：`auto-fill` 语义——列数 = 容器宽 / (最小格宽 + 间距)，
 * 每格 `weight(1f)` 把该行填满。**这是「右侧死白」的正解**：宽窗自然排 5-8 列，
 * 不再固定四列留一条空带。首屏 = 列数 × 2 行，超出的收在「查看更多」后面。
 */
@Composable
internal fun PreviewSection(data: DesktopDetailViewData, masked: Boolean) {
    if (data.thumbnails.isEmpty()) return
    val mask = if (masked) VeneraCoverMask.Masked else VeneraCoverMask.Visible
    var showAll by remember(data.thumbnails) { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cols = (maxWidth / (PREVIEW_MIN_CELL + 10.dp)).toInt().coerceIn(2, 8)
        val firstScreen = cols * 2
        val visible = if (showAll) data.thumbnails else data.thumbnails.take(firstScreen)
        Column(Modifier.fillMaxWidth()) {
            SectionHead(
                title = com.venera.desktop.settings.DesktopI18n.t("预览图集", "Preview"),
                count = com.venera.desktop.settings.DesktopI18n.t("${data.thumbnails.size} 页", "${data.thumbnails.size} pages"),
                tail = if (data.thumbnails.size > firstScreen) {
                    { SectionTailButton(com.venera.desktop.settings.DesktopI18n.t("查看更多", "Show all")) { showAll = true } }
                } else {
                    null
                },
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                visible.chunked(cols).forEachIndexed { lineIndex, line ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        line.forEachIndexed { i, url ->
                            val index = lineIndex * cols + i
                            val resolved = data.resolvedThumbnails[url] ?: url
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(0.75f)
                                    .clip(RoundedCornerShape(8.dp)),
                            ) {
                                VeneraCover(
                                    url = resolved,
                                    contentDescription = com.venera.desktop.settings.DesktopI18n.t("预览页", "Preview page"),
                                    mask = mask,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(6.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color.Black.copy(alpha = 0.5f))
                                        .padding(horizontal = 5.dp, vertical = 1.dp),
                                ) {
                                    Text("${index + 1}", fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight, color = Color.White)
                                }
                            }
                        }
                        // 末行不满时补位，格子保持同宽不拉伸。
                        repeat(cols - line.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}



/**
 * 评论区（编辑排版版）：输入行 + 双列评论网格（宽档两列 / 窄档自动一列）。
 * 闸门口径不变：`canSendComment` 只收输入行那一格，评论区本体恒摆。
 */
@Composable
internal fun CommentsSection(
    data: DesktopDetailViewData,
    commentInput: String,
    onCommentInput: (String) -> Unit,
    onSend: () -> Unit,
    /** 点「加载更多」续下一页（源还有页时才摆，null = 不摆）。 */
    onLoadMore: (() -> Unit)? = null,
    /** 最近一次发送结果（屏内本地状态；失败原因照原文念）。 */
    sendState: CommentSendState = CommentSendState.IDLE,
    sendError: String? = null,
) {
    val tokens = VeneraTokens
    val palette = LocalChromePalette.current
    Column(Modifier.fillMaxWidth()) {
        SectionHead(title = com.venera.desktop.settings.DesktopI18n.t("评论区", "Comments"), count = desktopCommentsHandleLabel(data.commentCount))

        if (data.canSendComment) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                com.venera.desktop.ui.components.DesktopInputField(
                    value = commentInput,
                    onValueChange = onCommentInput,
                    modifier = Modifier.weight(1f),
                    placeholder = com.venera.desktop.settings.DesktopI18n.t("说点什么吧……", "Write a comment…"),
                )
                DesktopPushButton(
                    onClick = onSend,
                    enabled = commentInput.isNotBlank() && !data.isSendingComment,
                ) {
                    Text(
                        if (data.isSendingComment) {
                            com.venera.desktop.settings.DesktopI18n.t("发送中…", "Sending…")
                        } else {
                            com.venera.desktop.settings.DesktopI18n.t("发表评论", "Post")
                        },
                        fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                    )
                }
            }
            // 发送状态必须常驻可见：既要有"发出去了"，也要有"没发出去"（失败时草稿留着可直接重试）。
            val statusText = when {
                data.isSendingComment ->
                    com.venera.desktop.settings.DesktopI18n.t("正在发送评论…", "Sending comment…")
                sendState == CommentSendState.SENT ->
                    com.venera.desktop.settings.DesktopI18n.t("评论已发表", "Comment posted")
                sendState == CommentSendState.FAILED ->
                    com.venera.desktop.settings.DesktopI18n.t(
                        "发送失败：${sendError ?: "源站拒绝了这条评论"}（草稿已保留，可直接重试）",
                        "Failed: ${sendError ?: "the source rejected it"} (draft kept — try again)",
                    )
                else -> null
            }
            if (statusText != null) {
                Text(
                    text = statusText,
                    fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                    color = when {
                        data.isSendingComment -> palette.ink3
                        sendState == CommentSendState.SENT -> palette.accent
                        else -> palette.danger
                    },
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        if (data.commentsLoading && data.comments.isEmpty()) {
            Text(
                text = com.venera.desktop.settings.DesktopI18n.t("正在取评论…", "Loading comments…"),
                fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                color = palette.ink3,
            )
        }

        // 双列网格：读序左→右、上→下（每行两枚，按评论原序铺）。
        data.comments.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(26.dp),
            ) {
                pair.forEach { comment ->
                    Box(Modifier.weight(1f)) { CommentRow(comment) }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        if (!data.commentsLoading && data.commentsError != null && data.comments.isEmpty()) {
            Text(
                text = data.commentsError,
                fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                color = palette.ink3,
            )
        } else if (!data.commentsLoading && data.comments.isEmpty()) {
            Text(
                text = com.venera.desktop.settings.DesktopI18n.t("还没人评论，来说第一句。", "No comments yet. Be the first to comment."),
                fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                color = palette.ink3,
            )
        }
        // 一页装不下全部：源还有页就摆续页按钮（带页码与已显示条数），别把「只显示了第一页」装成「全部」。
        if (data.commentsHasMore && onLoadMore != null) {
            Spacer(Modifier.height(4.dp))
            DesktopPushButton(
                onClick = onLoadMore,
                enabled = !data.commentsLoadingMore,
            ) {
                val pageHint = data.commentsMaxPage?.let { total ->
                    com.venera.desktop.settings.DesktopI18n.t(
                        "（第 ${data.commentsPage}/$total 页，已显示 ${data.comments.size} 条）",
                        " (page ${data.commentsPage}/$total, ${data.comments.size} shown)",
                    )
                }.orEmpty()
                Text(
                    if (data.commentsLoadingMore) {
                        com.venera.desktop.settings.DesktopI18n.t("正在取更多评论…", "Loading more comments…")
                    } else {
                        com.venera.desktop.settings.DesktopI18n.t("加载更多评论$pageHint", "Load more comments$pageHint")
                    },
                    fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                )
            }
        }
        Spacer(Modifier.height(tokens.spacing.space1))
    }
}

/** 一条评论：头像 28dp + 名/时间 + 正文（编辑排版版的紧凑行）。 */
@Composable
internal fun CommentRow(comment: DesktopDetailComment) {
    val palette = LocalChromePalette.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        // 头像：源给了地址就取图（不给/给不出的源落占位圆格 —— 逐源各安天命）。
        if (comment.avatar.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(palette.segmentTrack, CircleShape),
            )
        } else {
            VeneraCover(
                url = comment.avatar,
                contentDescription = null,
                mask = VeneraCoverMask.Visible,
                modifier = Modifier.size(28.dp).clip(CircleShape),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.userName,
                    fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                    color = palette.ink2,
                    maxLines = 1,
                )
                comment.time?.let {
                    Spacer(Modifier.width(7.dp))
                    Text(it, fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight, color = palette.ink3, maxLines = 1)
                }
            }
            Text(
                text = comment.content,
                fontSize = VeneraType.aux.size,
                lineHeight = 18.sp,
                color = palette.ink1,
            )
        }
    }
}



/**
 * 标签与分类（编辑排版版·侧列）：无盒壳，分组行直接铺。
 * 分组名 48dp 列 + FlowRow chips（全文照念，不截断——主人两次点名后的终审形）。
 */
@Composable
internal fun TagGroupsSection(data: DesktopDetailViewData, onSearchTag: (String) -> Unit) {
    if (data.tagGroups.isEmpty()) return
    Column(Modifier.fillMaxWidth()) {
        SectionHead(title = com.venera.desktop.settings.DesktopI18n.t("标签与分类", "Tags & Categories"))
        data.tagGroups.forEach { (group, values) ->
            TagGroupLine(groupName = group, tags = values, onSearchTag = onSearchTag)
        }
    }
}



/**
 * 一组标签那一段（组名 + 可点 chips）。
 *
 * **FlowRow 换行**（主人两次点名"胶囊截断"后的终审形）：每颗 chip 拿自然宽、
 * 装不下的换行 —— 全文照念，不再横滑把截断挪到视口外。
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun TagGroupLine(
    groupName: String,
    tags: List<String>,
    onSearchTag: (String) -> Unit,
) {
    val tokens = VeneraTokens
    if (tags.isEmpty()) return
    Row(modifier = Modifier.fillMaxWidth().padding(bottom = tokens.spacing.space2)) {
        if (groupName.isNotBlank()) {
            Text(
                text = "$groupName:",
                fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                color = tokens.color.textTertiary,
                modifier = Modifier.width(84.dp),
            )
        }
        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.space1),
        ) {
            tags.forEach { tag ->
                VeneraChip(text = tag, onClick = { onSearchTag(tag) })
            }
        }
    }
}

/**
 * 章节目录（编辑排版版·侧列）：**两列紧凑行**（宽档 2 列 / 窄档 3 列）。
 * 当前读到那一话 = accent 12% 底 + accent 字 + 实心点 + 「已读至」小字；
 * 每行右键菜单（打开章节 / 复制章节名）保留；正倒序沿用「只换看法不换账」口径。
 */
@Composable
internal fun ChapterSection(
    data: DesktopDetailViewData,
    onOpenChapter: (String) -> Unit,
    columns: Int,
    maxHeight: Dp = WIDE_CHAPTER_LIST_MAX_HEIGHT,
) {
    val palette = LocalChromePalette.current
    var reversed by remember(data.chapters) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        SectionHead(
            title = com.venera.desktop.settings.DesktopI18n.t("章节目录", "Chapters"),
            count = com.venera.desktop.settings.DesktopI18n.t("${data.chapters.size} 话", "${data.chapters.size} chapters"),
            tail = if (data.chapters.size > 1) {
                { SectionTailButton(if (reversed) com.venera.desktop.settings.DesktopI18n.t("正序", "Asc") else com.venera.desktop.settings.DesktopI18n.t("倒序", "Desc"), accent = false) { reversed = !reversed } }
            } else {
                null
            },
        )
        if (data.chapters.isEmpty()) {
            Text(
                text = com.venera.desktop.settings.DesktopI18n.t("暂无章节", "No chapters"),
                fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                color = palette.ink3,
            )
            return@Column
        }
        val ordered = if (reversed) {
            data.chapters.withIndex().toList().reversed()
        } else {
            data.chapters.withIndex().toList()
        }
        // 只组合视口内行：215 话全量铺开会一次构造 108 行 × 每行两枚右键菜单容器。
        // 目录定高自己滚，窄档沿用 280dp 沉底的既有口径。
        val rowCount = (ordered.size + columns - 1) / columns
        val listHeight = (rowCount * 32).dp.coerceAtMost(maxHeight)
        LazyColumn(
            modifier = Modifier.fillMaxWidth().height(listHeight),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(ordered.chunked(columns), key = { line -> line.first().index }) { line ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    line.forEach { (_, ch) ->
                        Box(Modifier.weight(1f)) {
                            // 章节行右键 —— 按对象裁剪，只摆这一行真能落的三项。
                            val chapterUx = LocalUxPlatformServices.current
                            com.venera.desktop.ui.components.DesktopContextMenuArea(
                                items = {
                                    listOf(
                                        com.venera.desktop.ui.components.DesktopMenuEntry.Item(
                                            label = com.venera.desktop.settings.DesktopI18n.tr("打开章节", "Open chapter"),
                                            shortcut = "Enter",
                                            icon = Icons.Outlined.OpenInNew,
                                            onClick = { onOpenChapter(ch.id) },
                                        ),
                                        com.venera.desktop.ui.components.DesktopMenuEntry.Item(
                                            label = com.venera.desktop.settings.DesktopI18n.tr("复制章节名", "Copy chapter title"),
                                            shortcut = "Ctrl+C",
                                            icon = Icons.Outlined.Description,
                                            onClick = { chapterUx.copyToClipboard(ch.title) },
                                        ),
                                    )
                                },
                            ) {
                                ChapterTile(chapter = ch, onOpen = { onOpenChapter(ch.id) })
                            }
                        }
                    }
                    repeat(columns - line.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** 章节瓦片：30dp 行，点 + 话名 + 当前话「已读至」。 */
@Composable
internal fun ChapterTile(chapter: DesktopDetailChapter, onOpen: () -> Unit) {
    val palette = LocalChromePalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (chapter.marked) {
                    palette.accent.copy(alpha = if (palette.dark) 0.20f else 0.12f)
                } else {
                    Color.Transparent
                },
            )
            .clickable(onClick = onOpen)
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(if (chapter.marked) 6.dp else 5.dp)
                .clip(CircleShape)
                .background(if (chapter.marked) palette.accent else palette.ink3.copy(alpha = 0.45f)),
        )
        Text(
            text = chapter.title.ifBlank { chapter.id },
            fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
            fontWeight = if (chapter.marked) FontWeight.SemiBold else FontWeight.Normal,
            color = if (chapter.marked) DesktopTheme.TextPrimary else palette.ink2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (chapter.marked) {
            Text(
                text = DESKTOP_HISTORY_CHAPTER_MARK_DESCRIPTION,
                fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                color = palette.accent,
                maxLines = 1,
            )
        }
    }
}



/** 作品信息（编辑排版版·侧列）：键值行；空栏不摆（没报就不念）。 */
@Composable
internal fun InfoSection(data: DesktopDetailViewData, onOpenRate: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth()) {
        SectionHead(title = com.venera.desktop.settings.DesktopI18n.t("作品信息", "Details"))
        if (data.author.isNotBlank()) {
            InfoLine(label = com.venera.desktop.settings.DesktopI18n.t("作者", "Author"), value = data.author)
        }
        if (data.uploader.isNotBlank() && data.uploader != data.author) {
            InfoLine(label = com.venera.desktop.settings.DesktopI18n.t("上传者", "Uploader"), value = data.uploader)
        }
        // 自己刚打的星优先于源均值（否则交完评分界面还念旧值，看着像"点了没反应"）。
        val shownRating = if (data.userRating > 0f) data.userRating else data.rating
        if (shownRating > 0f || data.canRate) {
            RatingLine(
                shownRating = shownRating,
                canRate = data.canRate,
                onOpenRate = onOpenRate,
            )
        }
        if (data.updateTime.isNotBlank()) {
            InfoLine(label = com.venera.desktop.settings.DesktopI18n.t("更新", "Updated"), value = data.updateTime)
        }
        data.likesCount?.let { n ->
            InfoLine(label = com.venera.desktop.settings.DesktopI18n.t("点赞", "Likes"), value = n.toString())
        }
        data.commentCount?.let { n ->
            InfoLine(label = com.venera.desktop.settings.DesktopI18n.t("评论", "Comments"), value = n.toString())
        }
        if (data.sourceKey.isNotBlank()) {
            InfoLine(label = com.venera.desktop.settings.DesktopI18n.t("来源", "Source"), value = data.sourceKey)
        }
    }
}

/**
 * 评分行：读数恒念（缺省 0 且不能打分则整行不摆）；源声明能打分时右侧补一枚「打分」把手。
 * 打分走弹窗，避免行内五颗星误触。
 */
@Composable
private fun RatingLine(shownRating: Float, canRate: Boolean, onOpenRate: (() -> Unit)?) {
    val tokens = VeneraTokens
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = tokens.spacing.space1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            com.venera.desktop.settings.DesktopI18n.t("评分", "Rating"),
            fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
            color = tokens.color.textTertiary,
            modifier = Modifier.width(56.dp),
        )
        Text(
            "★ %.1f".format(shownRating),
            fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
            color = tokens.color.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (canRate && onOpenRate != null) {
            DesktopPushButton(onClick = onOpenRate) {
                Text(
                    com.venera.desktop.settings.DesktopI18n.t("打分", "Rate"),
                    fontSize = VeneraType.aux.size,
                    lineHeight = VeneraType.aux.lineHeight,
                )
            }
        }
    }
}

/**
 * 打分弹窗：五颗大星选一档，提交交给核（乐观更新 + 失败回滚在核里）。
 *
 * @param current 进弹窗时的当前星级（0 = 还没评过）
 * @param onDismiss 取消 / 点外部关闭
 * @param onSubmit 非零星级才可提交
 */
@Composable
internal fun RatingDialog(current: Float, onDismiss: () -> Unit, onSubmit: (Float) -> Unit) {
    val tokens = VeneraTokens
    val palette = LocalChromePalette.current
    var selected by remember { mutableStateOf(current) }
    DesktopMacDialog(
        onDismissRequest = onDismiss,
        title = com.venera.desktop.settings.DesktopI18n.t("给这本打分", "Rate this title"),
        width = 300.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(5) { index ->
                val star = (index + 1).toFloat()
                IconButton(
                    onClick = { selected = star },
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        imageVector = if (selected >= star) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = com.venera.desktop.settings.DesktopI18n.t("$index 星", "$index star"),
                        tint = if (selected >= star) palette.accent else tokens.color.textTertiary,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            MacDialogTextButton(
                text = com.venera.desktop.settings.DesktopI18n.t("取消", "Cancel"),
                onClick = onDismiss,
            )
            Spacer(Modifier.width(8.dp))
            MacDialogTextButton(
                text = com.venera.desktop.settings.DesktopI18n.t("提交", "Submit"),
                onClick = { if (selected > 0f) onSubmit(selected) },
                primary = true,
                enabled = selected > 0f,
            )
        }
    }
}



@Composable
internal fun InfoLine(label: String, value: String) {
    val tokens = VeneraTokens
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = tokens.spacing.space1)) {
        Text(label, fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight, color = tokens.color.textTertiary, modifier = Modifier.width(56.dp))
        Text(
            text = value,
            fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
            color = tokens.color.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * 相关推荐（编辑排版版）：段标题 + 横滑封面行（124×165）。
 * 封面卡**可点** —— 点推荐 = 换载下一本（真把手不是装饰）。
 */
@Composable
internal fun RecommendSection(
    data: DesktopDetailViewData,
    masked: Boolean,
    onOpenRecommend: (DesktopDetailRecommendItem) -> Unit,
) {
    if (data.recommend.isEmpty()) return
    val mask = if (masked) VeneraCoverMask.Masked else VeneraCoverMask.Visible
    Column(Modifier.fillMaxWidth()) {
        SectionHead(title = com.venera.desktop.settings.DesktopI18n.t("相关推荐", "Recommended"), count = com.venera.desktop.settings.DesktopI18n.t("猜你喜欢", "You may like"))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            data.recommend.forEach { rec ->
                // 推荐卡右键按对象裁剪 —— 这一屏只递了打开详情一扇门，
                // 就不摆收藏/搜索等没落点的项（不造假菜单）。
                val recUx = LocalUxPlatformServices.current
                com.venera.desktop.ui.components.DesktopContextMenuArea(
                    items = {
                        listOf(
                            com.venera.desktop.ui.components.DesktopMenuEntry.Item(
                                label = com.venera.desktop.settings.DesktopI18n.tr("查看详情", "View details"),
                                shortcut = "Enter",
                                icon = Icons.Outlined.Visibility,
                                onClick = { onOpenRecommend(rec) },
                            ),
                            com.venera.desktop.ui.components.DesktopMenuEntry.Item(
                                label = com.venera.desktop.settings.DesktopI18n.tr("复制标题", "Copy title"),
                                shortcut = "Ctrl+C",
                                icon = Icons.Outlined.Description,
                                onClick = { recUx.copyToClipboard(rec.title) },
                            ),
                        )
                    },
                ) {
                    // 推荐卡吃画廊卡那份浮动：封面直出（字摆在卡下），底托不铺，
                    // 描边 / 高光刃 / 弥散光按 124×165 的封面比例罩住封面。
                    com.venera.desktop.ui.components.DesktopFloatingCard(
                        cornerRadius = 8.dp,
                        surface = LocalChromePalette.current.card,
                        tray = false,
                        chromeAspect = 124f / 165f,
                        hoverElevation = 6.dp,
                        modifier = Modifier
                            .width(124.dp)
                            .clickable { onOpenRecommend(rec) },
                    ) { floatState ->
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(165.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                            ) {
                                VeneraCover(
                                    url = rec.cover,
                                    contentDescription = rec.title,
                                    mask = mask,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .desktopCoverFloat(floatState),
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = rec.title.ifBlank { rec.id },
                                fontSize = VeneraType.micro.size,
                                lineHeight = 15.sp,
                                color = LocalChromePalette.current.ink2,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}



// ════════════════════════════ 零件 ════════════════════════════

/**
 * 操作行的一枚：圆形底 + 图标/字符 + 底下那行小字，**整颗**（圆+字）可点。
 * 灰（`enabled=false`）= 读数格。图标直接吃字符档（♥ ♡ ⧉ ↗）—— 桌面这一屏不引第二套图标库。
 */
/** 整颗一击区那一颗：enabled=false 时不挂 clickable（读数格没有动作，而不是摆一枚按不动的把手）。 */
internal fun Modifier.androidClickable(enabled: Boolean, onClick: () -> Unit): Modifier =
    if (enabled) this.then(Modifier.clickable(onClick = onClick)) else this

/** 圆形底色（拆出来是因为色档要按 accent 在调用点现选，Modifier 扩展里不读 token）。 */
internal fun Modifier.androidBackgroundCircle(color: Color): Modifier =
    this.then(Modifier.background(color, androidx.compose.foundation.shape.CircleShape))

/** 行末那枚两档徽章（`已下载` / `离线不全`）—— 对岸 `BadgeChip` 的桌面形。 */
@Composable
internal fun BulkBadge(text: String, color: Color) {
    val tokens = VeneraTokens
    Box(
        modifier = Modifier
            .background(
                color.copy(alpha = BULK_BADGE_ALPHA),
                RoundedCornerShape(tokens.shape.extraSmall),
            )
            .padding(
                horizontal = tokens.spacing.badgeHorizontalPadding,
                vertical = tokens.spacing.badgeVerticalPadding,
            ),
    ) {
        Text(text = text, fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight, color = color)
    }
}

/** 徽章底色那一档透明度（对岸 `BadgeChip` 的 `alpha = 0.15f`，同值不自造一档）。 */
internal const val BULK_BADGE_ALPHA = 0.15f
