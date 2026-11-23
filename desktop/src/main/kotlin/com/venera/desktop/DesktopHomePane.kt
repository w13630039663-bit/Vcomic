package com.venera.desktop

import com.venera.desktop.ui.vShadow
import com.venera.desktop.settings.DesktopI18n
import com.venera.desktop.ui.theme.VeneraType
import com.venera.desktop.gallery.ui.GalleryTokens
import com.venera.desktop.ui.components.DesktopPetCue
import com.venera.desktop.ui.components.DesktopPetCueBus
import com.venera.desktop.ui.components.DesktopPetPayload
import com.venera.desktop.ui.components.desktopCoverFloat
import com.venera.desktop.ui.components.desktopUiFloat
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.material3.Text
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.desktop.ui.DesktopFilterChip
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.components.venera.VeneraCoverMask
import com.venera.compose.feature.HomeBookSpines
import com.venera.compose.feature.HomeTrackHeader
import com.venera.compose.feature.SourceStatusRowHome
import com.venera.compose.feature.healthOf
import com.venera.compose.feature.color
import com.venera.compose.feature.label
import com.venera.compose.ui.tokens.SettingsBadgeColors
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.compose.data.db.HistoryRecord
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection


// ⚠️ 废弃勿动：经典皮肤档（DesktopHomeSkin.CLASSIC）的旧首页仪表，该档已停用。
// 现行首页是 DesktopHomeLightScreen；改首页一律去那边，本文件只留给读盘兼容期。


/**
 * 桌面首页 —— **全宽仪表版 v4**（设计稿 v4 拍板，主人定稿）。
 *
 * ## 四层全宽通栏结构（逐区域对照 `desktop-home-dashboard-v4-2026-11-01.html`）
 *
 *  ① **沉浸 Hero（全宽通栏，190dp）**：虚化封面铺底 + 底部压暗，前景左侧封面卡 + 标题 + 进度条；
 *     右上角三枚统计玻璃胶囊；右下角继续阅读主 CTA + 随机一本骰子。
 *  ② **状态与服务仪表横栏（三卡等宽平铺，彻底消灭右侧大片死黑空虚）**：
 *     - 卡片 A：**漫画源监测 (SOURCES)**：源名称 + 可用性健康指示灯 + 延迟毫秒数气泡 + 源管理入口。
 * （延迟与可用性一目了然！）
 *     - 卡片 B：**正在关注的画师 (ARTISTS)**：头像 + 关注作者列表 + 收藏本数 + 全部画师入口。
 *     - 卡片 C：**本地书库 (LOCAL)**：书脊图标 + 本机本数 + 管理入口。
 *  ③ **「今天看什么 TODAY」双源全宽对开面板**：
 *     - 左半 50%：推荐漫画 4 张卡（2×2 铺满，带标题与来源标）；
 *     - 中间：1px 发丝中缝分割线；
 *     - 右半 50%：画廊每日热门 4 张卡（2×2 铺满，带日榜名次 `#1`..`#4`）；
 *     - 两半天然等高，两边同屏各露 4 张大图。
 *  ④ **「最近在读 RECENT」全宽横滑带**：3 张 16:9 横卡舒展铺满，封面底部嵌入阅读进度条。
 *  ⑤ **删除底部两列丑陋的压扁横条**：主人明令砍掉画廊 6 列与推荐 4 列挤压横带，页面在「最近在读」后收口。
 *
 * ## 不动的部分
 *
 *  - **参数面全兼容**：形参名、类型、调用点全部原样保留。
 *  - **判据全保留**：打码走 [comicMaskedOf] / `card.masked`（打码的画廊卡不给点开）；空态文案逐条保留。
 *  - 共享零件继续吃：`HomeTrackHeader` / `HomeBookSpines` / `SourceStatusRowHome` / `healthOf`。
 */
@Composable
internal fun DesktopHomePane(
    mode: WideScreenLayoutMode,
    stats: DesktopHomeStats,
    history: DesktopHomeHistorySnapshot = DesktopHomeHistorySnapshot.Loading,
    onOpenHistory: (com.venera.compose.data.db.HistoryRecord) -> Unit,
    daily: List<com.venera.desktop.gallery.ui.DesktopWallCardModel>,
    followedArtists: com.venera.compose.gallery.domain.GalleryFollowedArtistsReadout?,
    recommendComics: List<com.venera.engineprobe.EngineSession.ComicCard>,
    comicMaskedOf: (com.venera.engineprobe.EngineSession.ComicCard) -> Boolean,
    recommendNote: String?,
    localComicCount: Int?,
    onOpenPost: (com.venera.desktop.gallery.ui.DesktopWallCardModel) -> Unit,
    onOpenArtist: (com.venera.compose.gallery.domain.FollowedArtistRef) -> Unit,
    onOpenComicCard: (com.venera.engineprobe.EngineSession.ComicCard) -> Unit,
    /** 首页漫画卡右键按这批卡自己的源搜标签（装配点绑定源键）。 */
    onSearchComicTag: ((String) -> Unit)? = null,
    // 推荐档位切换 + 两腿失败可视化 + 重试。
    recommendR18: Boolean = false,
    onPickRecommendR18: ((Boolean) -> Unit)? = null,
    recommendFailed: Boolean = false,
    dailyFailed: Boolean = false,
    // 每日热门源档（false=图库 Safebooru，true=pixiv 日榜），按设计稿栏头切换。
    dailyPixiv: Boolean = false,
    onPickDailyPixiv: ((Boolean) -> Unit)? = null,
    onRetryHome: (() -> Unit)? = null,
    /**
     * 源状态行（壳测速账直递——已启用源动态 + pixiv/yande.re 固定，
     * 每行带自己的延迟账；进首页自动测，60s 冷却）。
     */
    homeSourceRows: List<DesktopHomeSourceStatusRow> = emptyList(),
    onOpenLocalComics: (() -> Unit)? = null,
    onGoSourceManage: (() -> Unit)? = null,
    /** 设计稿：最近阅读栏头「全部 ›」入口；null = 不摆。 */
    onOpenAllHistory: (() -> Unit)? = null,
    /** 本地与下载条——下载队列账（壳 collect 的同一份），空表只留入口。 */
    downloadTasks: List<com.venera.desktop.gallery.data.DesktopComicQueueTask> = emptyList(),
    onOpenDownloads: (() -> Unit)? = null,
    /** 问候名那格的 pixiv 账号（浅皮档转发用）；null = 没接/没登。 */
    pixivAccount: com.venera.desktop.gallery.data.DesktopPixivAccount? = null,
    // ──：true = 整屏交给浅色新皮（DesktopHomeLightScreen），
    //    数据口子一字不动；false = 走本函数（现行仪表版 v4），逐字节不变。──
    lightSkin: Boolean = false,
) {
    if (lightSkin) {
        DesktopHomeLightScreen(
            mode,
            stats,
            history,
            onOpenHistory,
            daily,
            followedArtists,
            recommendComics,
            comicMaskedOf,
            recommendNote,
            localComicCount,
            onOpenPost,
            onOpenArtist,
            onOpenComicCard,
            onSearchComicTag,
            recommendR18,
            onPickRecommendR18,
            recommendFailed,
            dailyFailed,
            dailyPixiv,
            onPickDailyPixiv,
            onRetryHome,
            homeSourceRows,
            onOpenLocalComics,
            onGoSourceManage,
            onOpenAllHistory,
            downloadTasks,
            onOpenDownloads,
            pixivAccount = pixivAccount,
        )
        return
    }
    val tokens = VeneraTokens
    // 内容宽对齐设计稿 stage 档 1320，大窗两侧收窄。
    val contentMaxWidth = 1320.dp
    val backdropMode by com.venera.desktop.settings.DesktopBackdropMode.mode.collectAsState()
    val backdropTransparent = DesktopDwmBackdrop.active &&
        com.venera.desktop.settings.DesktopBackdropMode.isTransparent(backdropMode)
    val pageScrim = if (backdropTransparent) {
        tokens.color.background.copy(alpha = 0.24f)
    } else {
        tokens.color.background
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pageScrim),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = contentMaxWidth)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = tokens.spacing.screenHorizontal,
                    end = tokens.spacing.screenHorizontal,
                    top = tokens.spacing.space5,
                    bottom = tokens.spacing.space6,
                ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // ══ ① Hero（通栏全宽 100%，170dp）══
            DesktopHomeHero(
                history = history,
                stats = stats,
                onOpenHistory = onOpenHistory,
            )

            // ══ ② 发现（设计稿次序：发现先于源状态/最近阅读）：左漫画推荐｜右每日热门 ══
            DesktopHomeMosaic(
                daily = daily,
                recommendComics = recommendComics,
                comicMaskedOf = comicMaskedOf,
                recommendNote = recommendNote,
                onOpenPost = onOpenPost,
                onOpenComicCard = onOpenComicCard,
                onSearchComicTag = onSearchComicTag,
                recommendR18 = recommendR18,
                onPickRecommendR18 = onPickRecommendR18,
                recommendFailed = recommendFailed,
                dailyFailed = dailyFailed,
                dailyPixiv = dailyPixiv,
                onPickDailyPixiv = onPickDailyPixiv,
                onRetry = onRetryHome,
            )

            // ══ ②.5 关注画师带（v4 退场的画师读数归位成横滑带；空名单整段不摆）══
            followedArtists?.takeIf { it.artists.isNotEmpty() }?.let { readout ->
                DesktopHomeArtistRail(
                    artists = readout,
                    onOpenArtist = onOpenArtist,
                )
            }

            // ══ ③ 源状态（真延迟表）｜最近阅读（历史余量）：两栏等高 ══
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(androidx.compose.foundation.layout.IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                DesktopHomeSourceTable(
                    rows = homeSourceRows,
                    onGoSourceManage = onGoSourceManage,
                    modifier = Modifier.weight(1f),
                )
                DesktopHomeRecentList(
                    records = history.records.drop(1).take(4),
                    onOpen = onOpenHistory,
                    onOpenAll = onOpenAllHistory,
                    modifier = Modifier.weight(1f),
                )
            }

            // ══ ④ 本地与下载（一行两卡等高；真数据真入口，空队列留入口不空摆）══
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(androidx.compose.foundation.layout.IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                DashLocalCard(
                    localComicCount = localComicCount,
                    onOpenLocalComics = onOpenLocalComics,
                    modifier = Modifier.weight(1f),
                )
                DashQueueCard(
                    tasks = downloadTasks,
                    onOpen = onOpenDownloads,
                    modifier = Modifier.weight(1f),
                )
            }

            // 读不到时的成因：只有 `DesktopHomeStatsSource.read()` 抛了才出这一行，显示真实异常。
            stats.note?.let { msg ->
                Text(
                    text = msg,
                    fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                    color = tokens.color.textTertiary,
                )
            }
        }
    }
}

@Composable
internal fun classicHomeCardSurface(): Color {
    val tokens = VeneraTokens
    val backdropMode by com.venera.desktop.settings.DesktopBackdropMode.mode.collectAsState()
    val transparent = DesktopDwmBackdrop.active &&
        com.venera.desktop.settings.DesktopBackdropMode.isTransparent(backdropMode)
    return if (transparent) {
        tokens.color.surfaceContainerHigh.copy(alpha = 0.72f)
    } else {
        tokens.color.surfaceContainerHigh
    }
}

// ═══════════════════════════════════════════════════════════════════
// ③ 「今天看什么」双源全宽对开面板
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun DesktopHomeMosaic(
    daily: List<com.venera.desktop.gallery.ui.DesktopWallCardModel>,
    recommendComics: List<com.venera.engineprobe.EngineSession.ComicCard>,
    comicMaskedOf: (com.venera.engineprobe.EngineSession.ComicCard) -> Boolean,
    recommendNote: String?,
    onOpenPost: (com.venera.desktop.gallery.ui.DesktopWallCardModel) -> Unit,
    onOpenComicCard: (com.venera.engineprobe.EngineSession.ComicCard) -> Unit,
    onSearchComicTag: ((String) -> Unit)? = null,
    // 推荐档位（正常=拷贝漫画 / R18=禁漫天堂）+ 两腿失败可视化 + 重试。
    recommendR18: Boolean = false,
    onPickRecommendR18: ((Boolean) -> Unit)? = null,
    recommendFailed: Boolean = false,
    dailyFailed: Boolean = false,
    // 每日热门源档（false=图库 Safebooru，true=pixiv 日榜），按设计稿栏头切换。
    dailyPixiv: Boolean = false,
    onPickDailyPixiv: ((Boolean) -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
) {
    val tokens = VeneraTokens
    // 两栏各摆满 6 张（3×2），不再 4 张。
    val recs = recommendComics.take(6)
    val posts = daily.take(6)
    // 两腿都塌 = 整机网络问题，念「网络连接超时」；只塌一条念各自的失败（都是终态，不再空转）。
    val bothFailed = recommendFailed && dailyFailed

    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(text = "发现", fontSize = VeneraType.body.size, lineHeight = VeneraType.body.lineHeight, fontWeight = VeneraType.cardTitle.weight, color = tokens.color.textPrimary)
            Text(text = "漫画 + 画廊 同屏", fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight, color = tokens.color.textTertiary)
        }
        Spacer(Modifier.height(11.dp))

        // 设计稿：无外层容器卡、无中缝线，两栏（各 3 列卡）直接铺，gap 16。
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
                // 左半：推荐漫画（每栏 3 列卡）
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        HalfHead(title = "推荐漫画", en = if (recommendR18) "禁漫天堂" else "拷贝漫画", count = recs.size)
                        Spacer(Modifier.weight(1f))
                        // 正常=拷贝漫画、R18=禁漫天堂，栏头直切。
                        if (onPickRecommendR18 != null) {
                            HomeSeg {
                                HomeSegChip("正常", !recommendR18) { onPickRecommendR18.invoke(false) }
                                HomeSegChip("R18", recommendR18) { onPickRecommendR18.invoke(true) }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    if (recs.isEmpty()) {
                        // 失败是终态就摆失败位（带重试），不再挂那句解释体空转。
                        if (recommendFailed) {
                            HomeFailedSlot(tip = if (bothFailed) "网络连接超时" else "推荐获取失败", onRetry = onRetry)
                        } else {
                            MosaicEmptySlot(text = recommendNote ?: "推荐加载中…")
                        }
                    } else {
                        // 按设计稿 miniwall 形——每栏 3 列小卡，不是 2×2 大卡。
                        recs.take(6).chunked(3).forEachIndexed { rowIdx, rowCards ->
                            if (rowIdx > 0) Spacer(Modifier.height(11.dp))
                            MosaicCellRow {
                                rowCards.forEach { card ->
                                    DesktopHomeComicMenu(card, onOpenComicCard, onSearchComicTag, modifier = Modifier.weight(1f)) {
                                        MosaicCell(
                                            coverUrl = card.cover,
                                            title = card.title,
                                            srcLabel = "漫画",
                                            srcColor = tokens.color.primary,
                                            masked = comicMaskedOf(card),
                                            openable = true,
                                            onClick = {
                                                DesktopPetCueBus.emit(
                                                    DesktopPetCue.OPEN_WORK,
                                                    DesktopPetPayload(
                                                        title = card.title,
                                                        author = card.author,
                                                        tag = card.tags.firstOrNull().orEmpty(),
                                                    ),
                                                )
                                                onOpenComicCard(card)
                                            },
                                            captionSub = card.author.ifBlank { "漫画" },
                                            modifier = Modifier,
                                        )
                                    }
                                }
                                repeat(3 - rowCards.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }

                // 右半：画廊每日热门（每栏 3 列卡，miniwall 形）
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        HalfHead(
                            title = "每日热门",
                            en = if (dailyPixiv) "pixiv 日榜" else "Safebooru",
                            count = posts.size,
                        )
                        Spacer(Modifier.weight(1f))
                        // 每日热门两档切换（图库 / pixiv），按设计稿栏头直切。
                        if (onPickDailyPixiv != null) {
                            HomeSeg {
                                HomeSegChip("图库", !dailyPixiv) { onPickDailyPixiv.invoke(false) }
                                HomeSegChip("pixiv", dailyPixiv) { onPickDailyPixiv.invoke(true) }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    if (posts.isEmpty()) {
                        if (dailyFailed) {
                            HomeFailedSlot(tip = if (bothFailed) "网络连接超时" else "每日热门获取失败", onRetry = onRetry)
                        } else {
                            MosaicEmptySlot(text = "每日热门加载中…")
                        }
                    } else {
                        posts.take(6).chunked(3).forEachIndexed { rowIdx, rowCards ->
                            if (rowIdx > 0) Spacer(Modifier.height(11.dp))
                            MosaicCellRow {
                                rowCards.forEachIndexed { idx, card ->
                                    MosaicCell(
                                        coverUrl = card.url,
                                        title = card.title,
                                        srcLabel = "画廊",
                                        srcColor = SettingsBadgeColors.Gallery,
                                        masked = card.masked,
                                        openable = card.openable,
                                        // 图片卡的角色事件统一在壳里 openWallCardAny 那一颗发，屏内不重复 emit。
                                        onClick = { onOpenPost(card) },
                                        captionSub = card.author.ifBlank { "画廊" },
                                        copyLink = card.url.takeIf { it.isNotBlank() },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                repeat(3 - rowCards.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
    }
}

/** 栏头（设计稿 col-h 形）：标题 13 粗 + EN 10 弱色字距 + 计数，无圆点。 */
@Composable
private fun HalfHead(title: String, en: String, count: Int) {
    val tokens = VeneraTokens
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = title, fontSize = VeneraType.bodySecondary.size, lineHeight = VeneraType.bodySecondary.lineHeight, fontWeight = VeneraType.cardTitle.weight, color = tokens.color.textPrimary)
        Text(text = en, fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight, color = tokens.color.textTertiary, letterSpacing = 1.2.sp)
        if (count > 0) {
            Text(text = "${count} 张", fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight, color = tokens.color.textTertiary, modifier = Modifier.padding(start = 2.dp))
        }
    }
}

@Composable
private fun MosaicCellRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(11.dp), content = content)
}

@Composable
private fun MosaicEmptySlot(text: String) {
    val tokens = VeneraTokens
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(GalleryTokens.Shape.Large)
            .border(1.dp, tokens.color.outlineVariant.copy(alpha = 0.5f), GalleryTokens.Shape.Large)
            .background(tokens.color.surfaceVariant.copy(alpha = tokens.current.placeholderAlpha)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
            color = tokens.color.textTertiary,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
    }
}

// 设计稿：分段切换器 = 槽（panel2 底、内衬 2、圆角 8）+ 无描边 chip。
@Composable
private fun HomeSeg(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val tokens = VeneraTokens
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(tokens.color.surfaceContainerHigh)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        content = content,
    )
}

// 推荐档位分段 chip（正常 / R18 / 图库 / pixiv），选中态走主色淡底。
@Composable
private fun HomeSegChip(label: String, selected: Boolean, onClick: () -> Unit) {
    // 全仓 chip 归一：删壳委托共享 DesktopFilterChip（原蓝 14% 淡底/无 hover 退役）。
    DesktopFilterChip(text = label, selected = selected, onClick = onClick)
}

// 失败位（可视化收口）：实心警告图标 + 一句成因 + 重试钮 —— 是终态就不是转圈。
@Composable
private fun HomeFailedSlot(tip: String, onRetry: (() -> Unit)?) {
    // 失败位吃统一记号件（图标+色+短句+重试），不再摆"⚠+灰字"。
    Box(
        modifier = Modifier.fillMaxWidth().height(180.dp),
        contentAlignment = Alignment.Center,
    ) {
        com.venera.desktop.ui.components.DesktopFailedState(
            kind = if (tip == "网络连接超时") {
                com.venera.desktop.platform.DesktopErrorKind.TIMEOUT
            } else {
                com.venera.desktop.platform.DesktopErrorKind.FAILED
            },
            title = tip,
            onRetry = onRetry,
        )
    }
}

@Composable
private fun MosaicCell(
    coverUrl: String,
    title: String,
    srcLabel: String,
    srcColor: Color,
    masked: Boolean,
    openable: Boolean,
    onClick: () -> Unit,
    /** 图下信息行副标（作者 / 来源）；null = 只摆标题行。 */
    captionSub: String? = null,
    /** 站方地址（设计稿悬停动作坞「复制链接」真口）；null = 不摆坞。 */
    copyLink: String? = null,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    val isDark = com.venera.compose.ui.tokens.LocalVeneraDarkTheme.current
    com.venera.desktop.ui.components.DesktopFloatingCard(
        cornerRadius = 12.dp,
        surface = tokens.color.surfaceContainerHigh,
        restElevation = 1.dp,
        modifier = modifier.clickable(enabled = openable, onClick = onClick),
    ) { floatState ->
        // 悬停动作坞（设计稿 .dock）：仅指针悬停出现，150ms 淡变 + 位移。
        val dockAlpha by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (floatState.isHovered) 1f else 0f,
            label = "homeDockAlpha",
        )
        val dockOffset by animateDpAsState(
            targetValue = if (floatState.isHovered) 0.dp else (-3).dp,
            label = "homeDockOffset",
        )
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 3.8f)
                    .background(tokens.color.surfaceVariant.copy(alpha = tokens.current.placeholderAlpha))
                    // 设计稿图框描边：1px 纯白（暗底）/ 纯黑（亮底）内缩，画在图上。
                    .drawWithContent {
                        drawContent()
                        val line = if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f)
                        drawRoundRect(
                            color = line,
                            topLeft = Offset(0.5.dp.toPx(), 0.5.dp.toPx()),
                            size = Size(size.width - 1.dp.toPx(), size.height - 1.dp.toPx()),
                            cornerRadius = CornerRadius(8.dp.toPx()),
                            style = Stroke(width = 1.dp.toPx()),
                        )
                    },
            ) {
                VeneraCover(
                    url = coverUrl,
                    contentDescription = title,
                    mask = if (masked) VeneraCoverMask.Masked else VeneraCoverMask.Visible,
                    shimmerWhileLoading = false,
                    preserveAspectRatio = false,
                    modifier = Modifier
                        .fillMaxSize()
                        .desktopCoverFloat(floatState),
                )
                val srcEdge by androidx.compose.animation.animateColorAsState(
                    targetValue = if (floatState.isHovered) tokens.color.primary.copy(alpha = 0.55f)
                    else Color.Transparent,
                    animationSpec = androidx.compose.animation.core.tween(200),
                    label = "homeSrcEdge",
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .desktopUiFloat(floatState)
                        .clip(RoundedCornerShape(99.dp))
                        .background(Color_BlackScrim)
                        .border(1.dp, srcEdge, RoundedCornerShape(99.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = srcLabel,
                        fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                        fontWeight = VeneraType.cardTitle.weight,
                        color = srcColor,
                    )
                }
                if (masked && !openable) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .desktopUiFloat(floatState)
                            .clip(RoundedCornerShape(99.dp))
                            .background(tokens.color.outlineVariant.copy(alpha = 0.55f))
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "已打码",
                            fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                            fontWeight = VeneraType.cardTitle.weight,
                            color = tokens.color.textSecondary,
                        )
                    }
                }
                // 悬停动作坞（设计稿 .dock）：一枚真口——复制站方地址（无地址不摆，禁假控件）。
                if (copyLink != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .alpha(dockAlpha)
                            .offset(y = dockOffset)
                            .desktopUiFloat(floatState),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color_BlackScrim)
                                .clickable {
                                    Toolkit.getDefaultToolkit()
                                        .systemClipboard
                                        .setContents(StringSelection(copyLink), null)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "⧉",
                                fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                                color = Color.White,
                            )
                        }
                    }
                }
            }
            // 图下信息行（设计稿 .lb 同形：标题 + 副标，不再用黑遮罩压图）。
            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp)) {
                Text(
                    text = title,
                    fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                    fontWeight = VeneraType.cardTitle.weight,
                    color = tokens.color.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!captionSub.isNullOrBlank()) {
                    Text(
                        text = captionSub,
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

private val Color_BlackScrim = Color.Black.copy(alpha = 0.65f)

// ═══════════════════════════════════════════════════════════════════
// ④ 最近在读（全宽通栏 16:9 横卡）
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun DesktopHomeRecentStrip(
    records: List<HistoryRecord>,
    onOpen: (HistoryRecord) -> Unit,
) {
    val tokens = VeneraTokens
    if (records.isEmpty()) return

    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(
                modifier = Modifier
                    .size(4.dp, 15.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(SettingsBadgeColors.Gallery),
            )
            Text(text = "最近在读", fontSize = VeneraType.body.size, lineHeight = VeneraType.body.lineHeight, fontWeight = VeneraType.cardTitle.weight, color = tokens.color.textPrimary)
            Text(text = "RECENT", fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight, fontWeight = VeneraType.cardTitle.weight, color = tokens.color.textTertiary, letterSpacing = 1.6.sp)
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            records.forEach { record ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(GalleryTokens.Shape.Large)
                        .clickable { onOpen(record) },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(GalleryTokens.Shape.Large)
                            .border(1.dp, tokens.color.outlineVariant.copy(alpha = 0.55f), GalleryTokens.Shape.Large)
                            .background(tokens.color.surfaceVariant.copy(alpha = tokens.current.placeholderAlpha)),
                    ) {
                        VeneraCover(
                            url = record.coverUrl,
                            contentDescription = record.title,
                            mask = VeneraCoverMask.Visible,
                            shimmerWhileLoading = false,
                            preserveAspectRatio = false,
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (record.totalPages > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .background(Color.Black.copy(alpha = 0.55f)),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(
                                            ((record.lastPageIndex + 1f) / record.totalPages).coerceIn(0.02f, 1f),
                                        )
                                        .fillMaxHeight()
                                        .background(tokens.color.primary),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = record.title,
                        fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                        color = tokens.color.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (record.totalPages > 0) {
                        val pct = ((record.lastPageIndex + 1) * 100 / record.totalPages).coerceIn(1, 100)
                        Text(
                            text = "读到 ${record.lastChapterTitle.ifBlank { "第 ${record.lastChapterIndex + 1} 章" }} · ${pct}%",
                            fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                            color = tokens.color.textTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            repeat(3 - records.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/**
 * 首页那些数字的**桌面侧取值口**。
 */
internal data class DesktopHomeStats(
    val todayPages: Int,
    val weekPages: Int,
    val streakDays: Int,
    val sources: List<DesktopHomeSource>,
    val weekBar: List<Int>,
    val note: String? = null,
) {
    data class DesktopHomeSource(val name: String, val latencyMs: Long?)

    companion object {
        val Empty = DesktopHomeStats(
            todayPages = 0,
            weekPages = 0,
            streakDays = 0,
            sources = emptyList(),
            weekBar = emptyList(),
        )
    }
}

/**
 * 首页漫画卡右键：搜标签走装配点绑定的那颗源键，不落全局默认源。
 * 收藏走「打开详情页」——详情页自带本地/网络两条腿且按源，卡上不摆没落点的假收藏项。
 */
@Composable
private fun DesktopHomeComicMenu(
    card: com.venera.engineprobe.EngineSession.ComicCard,
    onOpenComicCard: (com.venera.engineprobe.EngineSession.ComicCard) -> Unit,
    onSearchComicTag: ((String) -> Unit)?,
    // 修：排布 modifier（weight 等）必须挂在 menu 这一层——menu 是 Row 的直接 child，
    // 包在里面的卡拿不到 RowScope，weight 会被静默忽略 → 卡全宽竖排（主人截图量的那格病）。
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    com.venera.desktop.ui.components.DesktopComicCardMenuArea(
        actions = com.venera.desktop.ui.components.DesktopComicCardActions(
            sourceKey = "",
            onOpenDetail = { onOpenComicCard(card) },
            onSearchInSource = onSearchComicTag,
            onCopyTitle = {
                val sel = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                sel.setContents(java.awt.datatransfer.StringSelection(card.title), null)
            },
        ),
        tag = card.tags.firstOrNull() ?: card.title,
        modifier = modifier,
        content = content,
    )
}

// ═══════════════════════════════════════════════════════════════════
// ②′ 源状态表 + 最近阅读（设计稿两栏）
// ═══════════════════════════════════════════════════════════════════

/**
 * 源状态一行（壳测速账 → 表）。
 * 行 = 已启用漫画源（脚本主机）+ pixiv / yande.re 固定；每行带自己的测速账。
 */
data class DesktopHomeSourceStatusRow(
    val name: String,
    /** 探测主机（壳按 host 记账与去重；`""` = 固定行）。 */
    val host: String = "",
    /** 正在测速（表上念「测速中…」，状态胶囊念灰档）。 */
    val probing: Boolean = false,
    /** 真延迟毫秒；null 且未失败且未在测 = 还没测过。 */
    val latencyMs: Long? = null,
    /** 这一趟没通（主机发不出去）。 */
    val failed: Boolean = false,
)

/** 延迟 → 三档纯件（可测）：≤800ms 正常 / ≤2500ms 偏慢 / 其余异常；null = 未测。 */
internal fun desktopHomeSourceState(row: DesktopHomeSourceStatusRow): String = when {
    row.probing -> "测速中"
    row.failed -> "异常"
    row.latencyMs == null -> "未测"
    row.latencyMs <= com.venera.desktop.platform.DesktopSourceLatencyProbe.GOOD_MS -> "正常"
    row.latencyMs <= com.venera.desktop.platform.DesktopSourceLatencyProbe.SLOW_MS -> "偏慢"
    else -> "异常"
}

/** 源状态表：行全由壳递（启用源动态 + pixiv/yande.re 固定），延迟真测，不再有"常显假行"。 */
@Composable
private fun DesktopHomeSourceTable(
    rows: List<DesktopHomeSourceStatusRow>,
    onGoSourceManage: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    Box(
        modifier = modifier
            .fillMaxHeight()
            .vShadow(2.dp, GalleryTokens.Shape.Large)
            .clip(GalleryTokens.Shape.Large)
            .background(classicHomeCardSurface())
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = DesktopI18n.t("源状态", "Sources Status"), fontSize = VeneraType.body.size, lineHeight = VeneraType.body.lineHeight, fontWeight = VeneraType.cardTitle.weight, color = tokens.color.textPrimary)
                Text(text = DesktopI18n.t("真测延迟", "Live latency"), fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight, color = tokens.color.textTertiary)
                Spacer(Modifier.weight(1f))
                if (onGoSourceManage != null) {
                    Text(
                        text = DesktopI18n.t("站点 ›", "Sources ›"),
                        fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                        color = tokens.color.primary,
                        modifier = Modifier.clickable(onClick = onGoSourceManage),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                Text(DesktopI18n.t("源", "Source"), fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight, color = tokens.color.textTertiary, modifier = Modifier.weight(1.4f))
                Text(DesktopI18n.t("延迟", "Latency"), fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight, color = tokens.color.textTertiary, modifier = Modifier.weight(1f))
                Text(DesktopI18n.t("状态", "Status"), fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight, color = tokens.color.textTertiary, modifier = Modifier.weight(0.8f))
            }
            val hairline = tokens.color.outlineVariant.copy(alpha = 0.5f)
            rows.forEachIndexed { rowIdx, row ->
                val state = desktopHomeSourceState(row)
                Row(
                    Modifier.fillMaxWidth()
                        .padding(vertical = 7.dp)
                        .drawWithContent {
                            drawContent()
                            // 设计稿：行底 1px hairline（末行不加）。
                            if (rowIdx < rows.lastIndex) {
                                drawLine(
                                    color = hairline,
                                    start = Offset(0f, size.height),
                                    end = Offset(size.width, size.height),
                                    strokeWidth = 1.dp.toPx(),
                                )
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = row.name,
                        fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                        color = tokens.color.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1.4f),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        // 测速动画：测速中 = 脉动圆点 + 「测速中…」；
                        // 完成后数字直接落位（三档胶囊另算）。
                        if (row.probing) {
                            val pulse = rememberInfiniteTransition(label = "probePulse")
                            val pulseAlpha by pulse.animateFloat(
                                initialValue = 0.25f,
                                targetValue = 1f,
                                animationSpec = infiniteRepeatable(
                                    tween(600, easing = LinearEasing),
                                    RepeatMode.Reverse,
                                ),
                                label = "probePulseAlpha",
                            )
                            Box(
                                modifier = Modifier
                                    .padding(end = 6.dp)
                                    .size(6.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(tokens.color.primary.copy(alpha = pulseAlpha)),
                            )
                        }
                        Text(
                            text = when {
                                row.probing -> DesktopI18n.t("测速中…", "Testing…")
                                row.failed -> DesktopI18n.t("未通", "Failed")
                                row.latencyMs != null -> "${row.latencyMs} ms"
                                else -> DesktopI18n.t("未测", "Untested")
                            },
                            fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                            color = if (row.latencyMs == null) tokens.color.textTertiary else tokens.color.textSecondary,
                        )
                    }
                    // 设计稿：状态为胶囊（正常绿 / 偏慢黄 / 异常红 / 未测灰）。
                    Box(
                        modifier = Modifier
                            .weight(0.8f)
                            .clip(RoundedCornerShape(99.dp))
                            .background(
                                when (state) {
                                    "正常" -> GalleryTokens.Color.StatusSuccess.copy(alpha = 0.16f)
                                    "偏慢" -> Color(0xFFD9A441).copy(alpha = 0.16f)
                                    "异常" -> GalleryTokens.Color.StatusDanger.copy(alpha = 0.16f)
                                    else -> tokens.color.outlineVariant.copy(alpha = 0.25f)
                                },
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = DesktopI18n.t(state),
                            fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                            color = when (state) {
                                "正常" -> GalleryTokens.Color.StatusSuccess
                                "偏慢" -> Color(0xFFD9A441)
                                "异常" -> GalleryTokens.Color.StatusDanger
                                else -> tokens.color.textTertiary
                            },
                        )
                    }
                }
            }
        }
    }
}

/** 最近阅读：半栏纵向堆叠，每本一行（小封面 + 标题 + 进度），数据同旧横条、不砍成一条。 */
@Composable
private fun DesktopHomeRecentList(
    records: List<HistoryRecord>,
    onOpen: (HistoryRecord) -> Unit,
    onOpenAll: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    Box(
        modifier = modifier
            .fillMaxHeight()
            .vShadow(2.dp, GalleryTokens.Shape.Large)
            .clip(GalleryTokens.Shape.Large)
            .background(classicHomeCardSurface())
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = DesktopI18n.t("最近阅读", "Recent Reading"), fontSize = VeneraType.body.size, lineHeight = VeneraType.body.lineHeight, fontWeight = VeneraType.cardTitle.weight, color = tokens.color.textPrimary)
                Text(text = DesktopI18n.t("历史余量", "History"), fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight, color = tokens.color.textTertiary)
                Spacer(Modifier.weight(1f))
                if (onOpenAll != null) {
                    Text(
                        text = DesktopI18n.t("全部 ›", "All ›"),
                        fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                        color = tokens.color.primary,
                        modifier = Modifier.clickable(onClick = onOpenAll),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            if (records.isEmpty()) {
                Text(
                    text = DesktopI18n.t("暂无阅读记录。", "No reading history yet."),
                    fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                    color = tokens.color.textTertiary,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    records.forEach { record ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(GalleryTokens.Shape.Medium)
                                .background(tokens.color.surfaceVariant.copy(alpha = 0.5f))
                                .clickable { onOpen(record) }
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 44.dp, height = 60.dp)
                                    .clip(GalleryTokens.Shape.Medium)
                                    .background(tokens.color.surfaceVariant),
                            ) {
                                VeneraCover(
                                    url = record.coverUrl,
                                    contentDescription = record.title,
                                    mask = VeneraCoverMask.Visible,
                                    shimmerWhileLoading = false,
                                    preserveAspectRatio = false,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Text(
                                    text = record.title,
                                    fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                                    fontWeight = VeneraType.cardTitle.weight,
                                    color = tokens.color.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (record.totalPages > 0) {
                                    val pct = ((record.lastPageIndex + 1) * 100 / record.totalPages).coerceIn(1, 100)
                                    Text(
                                        text = "读到 " + record.lastChapterTitle.ifBlank { "第 " + (record.lastChapterIndex + 1) + " 章" } + " · " + pct + "%",
                                        fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                                        color = tokens.color.textTertiary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(tokens.color.outlineVariant.copy(alpha = 0.5f)),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(((record.lastPageIndex + 1f) / record.totalPages).coerceIn(0.02f, 1f))
                                                .fillMaxHeight()
                                                .background(tokens.color.primary),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
