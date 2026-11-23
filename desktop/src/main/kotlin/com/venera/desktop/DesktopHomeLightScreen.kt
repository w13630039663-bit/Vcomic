package com.venera.desktop

import com.venera.desktop.ui.vShadow
import com.venera.desktop.settings.DesktopI18n
import com.venera.desktop.ui.components.DesktopPetCue
import com.venera.desktop.ui.components.DesktopPetCueBus
import com.venera.desktop.ui.components.DesktopPetPayload
import com.venera.desktop.ui.components.desktopCoverFloat
import com.venera.desktop.ui.components.desktopUiFloat
import com.venera.desktop.ui.theme.VeneraType
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.components.venera.VeneraCoverMask
import com.venera.compose.data.db.HistoryRecord
import com.venera.compose.gallery.domain.GalleryFollowedArtistsReadout
import com.venera.engineprobe.EngineSession
import com.venera.desktop.ui.DesktopFilterChip
import com.venera.desktop.gallery.ui.DesktopChromePalette
import com.venera.desktop.gallery.ui.DesktopWallCardModel
import com.venera.desktop.gallery.ui.LocalChromePalette
import java.time.LocalDate
import java.time.LocalTime

// 浅色首页屏：与 DesktopHomePane 共用数据与回调，仅替换皮肤与布局。

// 颜色从窗根注入的 LocalChromePalette 取档以支持浅/深主题切换，本文件不写色值字面量。
private object HomeLightTokens {
    private val palette: DesktopChromePalette
        @Composable @ReadOnlyComposable get() = LocalChromePalette.current

    val pageBg: Color @Composable @ReadOnlyComposable get() = palette.page
    val card: Color @Composable @ReadOnlyComposable get() = palette.card
    val pill: Color @Composable @ReadOnlyComposable get() = palette.pill
    val heroStart: Color @Composable @ReadOnlyComposable get() = palette.heroStart
    val heroEnd: Color @Composable @ReadOnlyComposable get() = palette.heroEnd
    val ink1: Color @Composable @ReadOnlyComposable get() = palette.ink1
    val ink2: Color @Composable @ReadOnlyComposable get() = palette.ink2
    val ink3: Color @Composable @ReadOnlyComposable get() = palette.ink3
    val accent: Color @Composable @ReadOnlyComposable get() = palette.accent
    val ok: Color @Composable @ReadOnlyComposable get() = palette.ok
    val warn: Color @Composable @ReadOnlyComposable get() = palette.warn
    val hairline: Color @Composable @ReadOnlyComposable get() = palette.hairline
    val barTrack: Color @Composable @ReadOnlyComposable get() = palette.frame
    val glassBar: Color @Composable @ReadOnlyComposable get() = palette.railGlass
}

private val Card16 = RoundedCornerShape(16.dp)
private val Card12 = RoundedCornerShape(12.dp)
private val Pill = RoundedCornerShape(999.dp)

@Composable
internal fun DesktopHomeLightScreen(
    mode: WideScreenLayoutMode,
    stats: DesktopHomeStats,
    history: DesktopHomeHistorySnapshot,
    onOpenHistory: (HistoryRecord) -> Unit,
    daily: List<DesktopWallCardModel>,
    followedArtists: GalleryFollowedArtistsReadout?,
    recommendComics: List<EngineSession.ComicCard>,
    comicMaskedOf: (EngineSession.ComicCard) -> Boolean,
    recommendNote: String?,
    localComicCount: Int?,
    onOpenPost: (DesktopWallCardModel) -> Unit,
    onOpenArtist: (com.venera.compose.gallery.domain.FollowedArtistRef) -> Unit,
    onOpenComicCard: (EngineSession.ComicCard) -> Unit,
    onSearchComicTag: ((String) -> Unit)? = null,
    recommendR18: Boolean = false,
    onPickRecommendR18: ((Boolean) -> Unit)? = null,
    recommendFailed: Boolean = false,
    dailyFailed: Boolean = false,
    dailyPixiv: Boolean = false,
    onPickDailyPixiv: ((Boolean) -> Unit)? = null,
    onRetryHome: (() -> Unit)? = null,
    homeSourceRows: List<DesktopHomeSourceStatusRow> = emptyList(),
    onOpenLocalComics: (() -> Unit)? = null,
    onGoSourceManage: (() -> Unit)? = null,
    onOpenAllHistory: (() -> Unit)? = null,
    downloadTasks: List<com.venera.desktop.gallery.data.DesktopComicQueueTask> = emptyList(),
    onOpenDownloads: (() -> Unit)? = null,
    /** 问候名那格的 pixiv 账号：壳递 `ports.pixivAccount` 的桌面实现，null = 没接/没登。 */
    pixivAccount: com.venera.desktop.gallery.data.DesktopPixivAccount? = null,
) {
    val hazeState = rememberHazeState()
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { DesktopErrorBus.suppressStartupHomeImageToast = false }
    }
    // 问候名：pixiv 已登用站方账号名，没登退电脑登录名；都拿不到才落"读者"。
    val greetingName = (pixivAccount?.authState?.collectAsState()?.value
        as? com.venera.compose.gallery.data.PixivAuthState.SignedIn)?.userName
        ?.takeIf { it.isNotBlank() }
        ?: System.getProperty("user.name")?.takeIf { it.isNotBlank() }
        ?: DesktopI18n.t("读者", "Reader")
    var heroDismissed by remember { mutableStateOf(false) }
    var dockDismissed by remember { mutableStateOf(false) }
    val current = history.records.firstOrNull()
    val backdropMode by com.venera.desktop.settings.DesktopBackdropMode.mode.collectAsState()
    val backdropTransparent = DesktopDwmBackdrop.active &&
        com.venera.desktop.settings.DesktopBackdropMode.isTransparent(backdropMode)
    // scrim 已由侧栏内容层(contentSurface)整块铺好，这里不再叠第二层，避免色阶断层。
    val pageScrim = Color.Transparent

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pageScrim),
    ) {
        // ── 内容层（hazeSource：底部悬浮条糊这一层）──
        Column(
            modifier = Modifier
                .fillMaxSize()
                .devHazeSource(hazeState)
                .verticalScroll(rememberScrollState())
                .padding(top = 30.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            GreetingBlock(name = greetingName)

            if (current != null && !heroDismissed) {
                ContinueHero(
                    record = current,
                    sideCovers = history.records.drop(1).take(2).map { it.coverUrl } + daily.take(2).map { it.url },
                    masked = false,
                    onContinue = { onOpenHistory(current) },
                    onDismiss = { heroDismissed = true },
                )
            }

            // ── 每日热门（每日热门腿原线：图库 Safebooru / pixiv 日榜两档直切）──
            LightSection(
                title = DesktopI18n.t("每日热门", "Daily Popular"),
                sub = if (dailyPixiv) {
                    DesktopI18n.t("pixiv 日榜 · ${daily.size} 张", "Pixiv Daily · ${daily.size}")
                } else {
                    DesktopI18n.t("yande 每日热门 · ${daily.size} 张", "Yande Daily · ${daily.size}")
                },
                chips = listOf(
                    DesktopI18n.t("图库", "Gallery") to !dailyPixiv,
                    "pixiv" to dailyPixiv,
                ),
                onChip = { idx ->
                    val picked = idx == 1
                    if (picked != dailyPixiv) onPickDailyPixiv?.invoke(picked)
                },
            ) {
                when {
                    dailyFailed && daily.isEmpty() -> LightFailedSlot(
                        tip = DesktopI18n.t("网络连接超时", "Network timeout"),
                        onRetry = onRetryHome,
                    )
                    daily.isEmpty() -> LightEmptySlot(DesktopI18n.t("暂时没有内容", "Nothing here yet"))
                    else -> LightGrid(cards = daily.size) { index ->
                        val card = daily[index]
                        LightSquareCard(
                            url = card.url,
                            masked = card.masked,
                            cornerTag = if (dailyPixiv) "pixiv" else DesktopI18n.t("图库", "Gallery"),
                            title = card.title,
                            meta = if (card.masked) DesktopI18n.t("已打码", "Masked") else DesktopI18n.t("点击查看大图", "View full image"),
                            // 图片卡的角色事件统一在壳里 openWallCardAny 那一颗发，屏内不重复 emit。
                            onClick = { onOpenPost(card) },
                        )
                    }
                }
            }

            // ── 推荐漫画（推荐腿原线：正常=拷贝漫画 / R18=禁漫，切换即清缓存重取）──
            LightSection(
                title = DesktopI18n.t("推荐漫画", "Recommended Comics"),
                sub = DesktopI18n.t("推荐漫画 ${recommendComics.size} 张", "${recommendComics.size} recommended"),
                chips = listOf(
                    DesktopI18n.t("正常", "SFW") to !recommendR18,
                    "R18" to recommendR18,
                ),
                onChip = { idx ->
                    val picked = idx == 1
                    if (picked != recommendR18) onPickRecommendR18?.invoke(picked)
                },
            ) {
                when {
                    (recommendFailed || !recommendNote.isNullOrBlank()) && recommendComics.isEmpty() -> LightFailedSlot(
                        tip = recommendNote ?: DesktopI18n.t("网络连接超时", "Network timeout"),
                        onRetry = onRetryHome,
                    )
                    recommendComics.isEmpty() -> LightEmptySlot(DesktopI18n.t("暂时没有内容", "Nothing here yet"))
                    else -> LightGrid(cards = recommendComics.size) { index ->
                        val card = recommendComics[index]
                        LightSquareCard(
                            url = card.cover,
                            masked = comicMaskedOf(card),
                            cornerTag = DesktopI18n.t("漫画", "Comic"),
                            title = card.title,
                            meta = card.author.ifBlank { DesktopI18n.t("点击查看详情", "View details") },
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
                        )
                    }
                }
            }

            // ── 双面板：源状态 / 最近阅读 ──
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Box(Modifier.weight(1f)) {
                    SourceStatusPanel(homeSourceRows, onGoSourceManage)
                }
                Box(Modifier.weight(1f)) {
                    RecentReadingPanel(
                        records = history.records.take(3),
                        loaded = history.loaded,
                        note = history.note,
                        onOpenAll = onOpenAllHistory,
                        onOpenRecord = onOpenHistory,
                    )
                }
            }

            // ── 入口条：本地书库 / 下载与本地 ──
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                LightEntry(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.History,
                    title = DesktopI18n.t("本地书库", "Local Library"),
                    meta = localComicCount?.let { DesktopI18n.t("$it 本", "$it items") } ?: "",
                    onClick = { onOpenLocalComics?.invoke() },
                )
                LightEntry(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.History,
                    title = DesktopI18n.t("下载与本地", "Downloads"),
                    meta = downloadTasks.count {
                        it.status == com.venera.desktop.gallery.data.DesktopComicQueueStatus.RUNNING ||
                            it.status == com.venera.desktop.gallery.data.DesktopComicQueueStatus.PENDING
                    }.takeIf { it > 0 }?.let { DesktopI18n.t("$it 个任务", "$it tasks") } ?: DesktopI18n.t("查看任务", "View tasks"),
                    onClick = { onOpenDownloads?.invoke() },
                )
            }
        }

        // ── 底部玻璃悬浮条（haze 模糊内容层；空 = 不摆，不放假条）──
        if (current != null && !dockDismissed) {
            LightReadingDock(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                record = current,
                hazeState = hazeState,
                onContinue = { onOpenHistory(current) },
                onOpenAll = { onOpenAllHistory?.invoke() },
                onDismiss = { dockDismissed = true },
            )
        }
    }
}

// ────────────────────────── 问候区 ──────────────────────────

@Composable
private fun homeLightCardColor(): Color {
    val backdropMode by com.venera.desktop.settings.DesktopBackdropMode.mode.collectAsState()
    val palette = com.venera.desktop.gallery.ui.LocalChromePalette.current
    val transparent = DesktopDwmBackdrop.active &&
        com.venera.desktop.settings.DesktopBackdropMode.isTransparent(backdropMode)
    return if (transparent) {
        HomeLightTokens.card.copy(alpha = if (palette.dark) 0.68f else 0.78f)
    } else {
        HomeLightTokens.card
    }
}

@Composable
private fun GreetingBlock(name: String) {
    val now = LocalDate.now()
    val hour = LocalTime.now().hour
    val hello = when {
        hour < 11 -> DesktopI18n.t("早上好", "Good morning")
        hour < 13 -> DesktopI18n.t("中午好", "Good noon")
        hour < 18 -> DesktopI18n.t("下午好", "Good afternoon")
        else -> DesktopI18n.t("晚上好", "Good evening")
    }
    val week = "一二三四五六日"[now.dayOfWeek.value - 1]
    val dateLabel = DesktopI18n.t(
        "${now.monthValue}月${now.dayOfMonth}日 星期$week",
        now.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMM d", java.util.Locale.ENGLISH)),
    )
    Text(
        dateLabel,
        fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
        color = HomeLightTokens.ink3,
    )
    Text(
        DesktopI18n.t("$hello，$name", "$hello, $name"),
        fontSize = VeneraType.heroTitle.size, lineHeight = VeneraType.heroTitle.lineHeight,
        fontWeight = VeneraType.cardTitle.weight,
        color = HomeLightTokens.ink1,
        modifier = Modifier.padding(top = 4.dp),
    )
}

// ────────────────────────── Hero：继续阅读 ──────────────────────────

@Composable
private fun ContinueHero(
    record: HistoryRecord,
    sideCovers: List<String>,
    masked: Boolean,
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
) {
    val now = LocalDate.now()
    val week = "一二三四五六日"[now.dayOfWeek.value - 1]
    val percent = readingPercentOf(record)
    // 「玻璃暗化」：模糊封面铺底 + 前景压暗 + 细描边，一张卡里只有封面本身带彩色。
    val dark = com.venera.compose.ui.tokens.LocalVeneraDarkTheme.current
    val glassStroke = if (dark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f)
    val heroScrim = if (dark) {
        Brush.horizontalGradient(
            listOf(Color.Black.copy(alpha = 0.88f), Color.Black.copy(alpha = 0.50f)),
        )
    } else {
        Brush.horizontalGradient(
            listOf(Color.White.copy(alpha = 0.90f), Color.White.copy(alpha = 0.58f)),
        )
    }

    val heroInk = if (dark) Color.White else Color(0xFF1A1D21)
    val heroInkDim = heroInk.copy(alpha = 0.75f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp)
            .clip(Card20)
            .border(1.dp, glassStroke, Card20),
    ) {
        // 打码命中时不铺模糊底图：模糊会绕过 VeneraCover 的打码，等于泄露原图。
        if (!masked) {
            com.venera.compose.components.CoverHeroBackdrop(
                coverUrl = record.coverUrl,
                modifier = Modifier.matchParentSize(),
                blurRadius = 52.dp,
                backdropScale = 1.5f,
            )
        }
        Box(Modifier.matchParentSize().background(heroScrim))

        // 左侧文案
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 36.dp)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                DesktopI18n.t(
                    "${now.monthValue}月${now.dayOfMonth}日 星期$week",
                    now.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMM d", java.util.Locale.ENGLISH)),
                ),
                fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                color = heroInkDim,
            )
            Text(
                DesktopI18n.t("继续阅读", "Continue Reading"),
                fontSize = VeneraType.pageTitle.size, lineHeight = VeneraType.pageTitle.lineHeight,
                fontWeight = VeneraType.cardTitle.weight,
                color = heroInk,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                heroSubOf(record, isEn = com.venera.desktop.settings.LocalDesktopLanguage.current == com.venera.desktop.settings.DesktopLanguage.EN),
                fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                color = heroInkDim,
                modifier = Modifier.padding(top = 8.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            HeroContinueButton(onContinue = onContinue)
        }

        // 右侧封面堆叠（中间当前书 112dp，两侧 100dp 各旋 ±8°，无任何音乐元素）
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(300.dp)
                .fillMaxHeight(),
        ) {
            val side0 = sideCovers.getOrNull(0)
            val side1 = sideCovers.getOrNull(1)
            if (side0 != null) {
                HeroCover(url = side0, size = 100.dp, rotation = -8f, modifier = Modifier.align(Alignment.CenterStart).padding(start = 0.dp))
            }
            if (side1 != null) {
                HeroCover(url = side1, size = 100.dp, rotation = 8f, modifier = Modifier.align(Alignment.Center).padding(start = 62.dp))
            }
            HeroCover(
                url = record.coverUrl,
                size = 112.dp,
                rotation = 0f,
                masked = masked,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 48.dp),
            )
        }

        // 右上角 ×（本轮会话内不再展示）
        Icon(
            Icons.Outlined.Close,
            contentDescription = DesktopI18n.t("隐藏继续阅读", "Hide continue reading"),
            tint = heroInkDim,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(16.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
    }
}

@Composable
private fun HeroContinueButton(onContinue: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, label = "heroBtnPress")
    val dark = com.venera.compose.ui.tokens.LocalVeneraDarkTheme.current
    val stroke = if (dark) Color.White.copy(alpha = 0.44f) else Color.Black.copy(alpha = 0.32f)
    val label = if (dark) Color.White else Color(0xFF1A1D21)
    Row(
        modifier = Modifier
            .padding(top = 16.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .border(1.dp, stroke, Pill)
            .clickable(interactionSource = interaction, indication = null, onClick = onContinue)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            DesktopI18n.t("继续阅读", "Continue Reading"),
            fontSize = VeneraType.bodySecondary.size, lineHeight = VeneraType.bodySecondary.lineHeight,
            fontWeight = VeneraType.cardTitle.weight,
            color = label,
        )
    }
}

@Composable
private fun HeroCover(url: String, size: androidx.compose.ui.unit.Dp, rotation: Float, masked: Boolean = false, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { rotationZ = rotation }
            .clip(Card12)
            .background(HomeLightTokens.card)
            .vShadow(6.dp, Card12),
    ) {
        if (url.isNotBlank()) {
            VeneraCover(
                url = url,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                mask = if (masked) VeneraCoverMask.Masked else VeneraCoverMask.Visible,
                preserveAspectRatio = false,
            )
        }
    }
}

private fun readingPercentOf(record: HistoryRecord): Int {
    if (record.totalPages <= 0) return 0
    return (((record.lastPageIndex + 1) * 100) / record.totalPages).coerceIn(0, 100)
}

private fun heroSubOf(record: HistoryRecord, isEn: Boolean = false): String {
    val chapter = record.lastChapterTitle.takeIf { it.isNotBlank() }
        ?: if (isEn) "Ch. ${record.lastChapterIndex + 1}" else "第${record.lastChapterIndex + 1}话"
    val page = if (record.totalPages > 0) {
        if (isEn) " P.${record.lastPageIndex + 1}/${record.totalPages}" else " 第${record.lastPageIndex + 1}/${record.totalPages}页"
    } else ""
    return "${record.title} · $chapter$page · ${readingPercentOf(record)}%"
}

// ────────────────────────── 段（标题 + 胶囊 + 内容）──────────────────────────

@Composable
private fun LightSection(
    title: String,
    sub: String,
    chips: List<Pair<String, Boolean>>,
    onChip: (Int) -> Unit,
    content: @Composable () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = VeneraType.statNumber.size, lineHeight = VeneraType.statNumber.lineHeight, fontWeight = VeneraType.cardTitle.weight, color = HomeLightTokens.ink1)
            Spacer(Modifier.width(12.dp))
            Text(sub, fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight, color = HomeLightTokens.ink3)
            Spacer(Modifier.weight(1f))
            chips.forEachIndexed { index, (label, on) ->
                LightChip(label = label, on = on, onClick = { onChip(index) })
                Spacer(Modifier.width(6.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        content()
    }
}

@Composable
private fun LightChip(label: String, on: Boolean, onClick: () -> Unit) {
    // 全仓 chip 归一：删壳委托共享 DesktopFilterChip（hover/按压/选中三态单源）。
    DesktopFilterChip(text = label, selected = on, onClick = onClick)
}

/**
 * 方卡网格：列数随窗宽 4-5-6-7 档，**最多两行**；
 * 卡宽按列数均分取定值——末行不满时卡保持同宽靠左排，**不拉伸放大**。
 */
@Composable
private fun LightGrid(cards: Int, cell: @Composable (Int) -> Unit) {
    BoxWithConstraints {
        val cols = when {
            maxWidth >= 1300.dp -> 7
            maxWidth >= 1100.dp -> 6
            maxWidth >= 920.dp -> 5
            else -> 4
        }
        val shown = minOf(cards, cols * 2)
        val gap = 20.dp
        val cellWidth = (maxWidth - gap * (cols - 1)) / cols
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            var i = 0
            while (i < shown) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    val end = minOf(i + cols, shown)
                    for (index in i until end) {
                        Box(Modifier.width(cellWidth)) { cell(index) }
                    }
                }
                i += cols
            }
        }
    }
}

@Composable
private fun LightSquareCard(
    url: String,
    masked: Boolean,
    cornerTag: String,
    title: String,
    meta: String,
    onClick: () -> Unit,
) {
    val tapInteraction = remember { MutableInteractionSource() }
    // 封面直出形（字摆在卡下水皮上）：底托不铺，描边/高光刃/弥散光按 1:1 封面比例罩住方块；
    // 左上角类型角标悬停点亮。这是漫画/图库两腿共用的卡，改这里两腿一起生效。
    com.venera.desktop.ui.components.DesktopFloatingCard(
        cornerRadius = 12.dp,
        surface = Color(0xFFEDEFF2),
        tray = false,
        chromeAspect = 1f,
        hoverElevation = 6.dp,
        modifier = Modifier.clickable(interactionSource = tapInteraction, indication = null, onClick = onClick),
    ) { floatState ->
        Column {
            val tagEdge by androidx.compose.animation.animateColorAsState(
                targetValue = if (floatState.isHovered) androidx.compose.ui.graphics.Color.White.copy(alpha = 0.55f)
                else Color.Transparent,
                animationSpec = androidx.compose.animation.core.tween(200),
                label = "lightTagEdge",
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(Card12)
                    .background(Color(0xFFEDEFF2)),
            ) {
                if (url.isNotBlank()) {
                    VeneraCover(
                        url = url,
                        contentDescription = title,
                        modifier = Modifier
                            .matchParentSize()
                            .desktopCoverFloat(floatState),
                        mask = if (masked) VeneraCoverMask.Masked else VeneraCoverMask.Visible,
                        preserveAspectRatio = false,
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .desktopUiFloat(floatState)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0xFF0F141C).copy(alpha = 0.55f))
                        .border(1.dp, tagEdge, RoundedCornerShape(5.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(cornerTag, color = Color.White, fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight)
                }
            }
            Text(
                title,
                fontSize = VeneraType.bodySecondary.size, lineHeight = VeneraType.bodySecondary.lineHeight,
                fontWeight = VeneraType.cardTitle.weight,
                color = HomeLightTokens.ink1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                meta,
                fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                color = HomeLightTokens.ink3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

// ────────────────────────── 双面板 ──────────────────────────

@Composable
private fun LightPanel(title: String, sub: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Card16)
            .background(homeLightCardColor())
            .border(1.dp, HomeLightTokens.hairline, Card16)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = VeneraType.body.size, lineHeight = VeneraType.body.lineHeight, fontWeight = VeneraType.cardTitle.weight, color = HomeLightTokens.ink1)
            Spacer(Modifier.width(8.dp))
            Text(sub, fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight, color = HomeLightTokens.ink3)
        }
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun SourceStatusPanel(rows: List<DesktopHomeSourceStatusRow>, onGoSourceManage: (() -> Unit)?) {
    LightPanel(title = DesktopI18n.t("源状态", "Sources Status"), sub = DesktopI18n.t("真实延迟", "Live latency")) {
        if (rows.isEmpty()) {
            Text(DesktopI18n.t("暂无启用源", "No enabled sources"), fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight, color = HomeLightTokens.ink3)
        } else {
            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        row.name,
                        fontSize = VeneraType.bodySecondary.size, lineHeight = VeneraType.bodySecondary.lineHeight,
                        fontWeight = VeneraType.control.weight,
                        color = HomeLightTokens.ink1,
                        modifier = Modifier.width(90.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        row.latencyMs?.let { "$it ms" } ?: "",
                        fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                        color = HomeLightTokens.ink3,
                        modifier = Modifier.weight(1f),
                    )
                    val state = desktopHomeSourceState(row)
                    LightStatePill(state)
                }
            }
        }
        if (onGoSourceManage != null) {
            Text(
                DesktopI18n.t("站点", "Sources"),
                fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                color = HomeLightTokens.accent,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onGoSourceManage,
                    ),
            )
        }
    }
}

@Composable
private fun LightStatePill(state: String) {
    val (bg, fg) = when (state) {
        "正常" -> HomeLightTokens.ok.copy(alpha = 0.12f) to HomeLightTokens.ok
        "偏慢", "测速中" -> HomeLightTokens.warn.copy(alpha = 0.12f) to HomeLightTokens.warn
        else -> Color(0xFF1A1D21).copy(alpha = 0.08f) to HomeLightTokens.ink2
    }
    val label = when (state) {
        "正常" -> DesktopI18n.t("正常", "OK")
        "偏慢" -> DesktopI18n.t("偏慢", "Slow")
        "测速中" -> DesktopI18n.t("测速中", "Testing")
        "离线" -> DesktopI18n.t("离线", "Offline")
        "不可用" -> DesktopI18n.t("不可用", "Unavailable")
        else -> DesktopI18n.t(state)
    }
    Box(
        modifier = Modifier
            .clip(Pill)
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(label, fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight, fontWeight = VeneraType.cardTitle.weight, color = fg)
    }
}

@Composable
private fun RecentReadingPanel(
    records: List<HistoryRecord>,
    loaded: Boolean,
    note: String?,
    onOpenAll: (() -> Unit)?,
    onOpenRecord: (HistoryRecord) -> Unit,
) {
    LightPanel(title = DesktopI18n.t("最近阅读", "Recent Reading"), sub = DesktopI18n.t("历史余量", "History")) {
        when {
            !loaded && records.isEmpty() -> Text(DesktopI18n.t("读取中…", "Loading…"), fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight, color = HomeLightTokens.ink3)
            records.isEmpty() -> Text(note?.let { DesktopI18n.t(it) } ?: DesktopI18n.t("本机还没有阅读记录", "No reading history yet"), fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight, color = HomeLightTokens.ink3)
            else -> Column {
                records.forEachIndexed { index, record ->
                    if (index > 0) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(HomeLightTokens.hairline)
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onOpenRecord(record) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFEDEFF2)),
                        ) {
                            if (record.coverUrl.isNotBlank()) {
                                VeneraCover(
                                    url = record.coverUrl,
                                    contentDescription = record.title,
                                    modifier = Modifier.matchParentSize(),
                                    mask = VeneraCoverMask.Visible,
                                    preserveAspectRatio = false,
                                )
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                record.title,
                                fontSize = VeneraType.bodySecondary.size, lineHeight = VeneraType.bodySecondary.lineHeight,
                                fontWeight = VeneraType.cardTitle.weight,
                                color = HomeLightTokens.ink1,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                (record.lastChapterTitle.takeIf { it.isNotBlank() } ?: DesktopI18n.t("第${record.lastChapterIndex + 1}话", "Ch. ${record.lastChapterIndex + 1}")) +
                                    " · ${readingPercentOf(record)}%",
                                fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                                color = HomeLightTokens.ink3,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                            Box(
                                modifier = Modifier
                                    .padding(top = 6.dp)
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(Pill)
                                    .background(HomeLightTokens.barTrack),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(readingPercentOf(record) / 100f)
                                        .fillMaxHeight()
                                        .clip(Pill)
                                        .background(HomeLightTokens.accent)
                                )
                            }
                        }
                    }
                }
            }
        }
        if (onOpenAll != null) {
            Text(
                DesktopI18n.t("全部", "All"),
                fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                color = HomeLightTokens.ink2,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onOpenAll,
                    ),
            )
        }
    }
}

// ────────────────────────── 入口条 ──────────────────────────

@Composable
private fun LightEntry(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    meta: String,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        modifier = modifier
            .height(56.dp)
            .clip(Card16)
            .background(homeLightCardColor())
            .border(1.dp, HomeLightTokens.hairline, Card16)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = HomeLightTokens.ink2, modifier = Modifier.size(16.dp))
        Text(title, fontSize = VeneraType.bodySecondary.size, lineHeight = VeneraType.bodySecondary.lineHeight, fontWeight = VeneraType.cardTitle.weight, color = HomeLightTokens.ink1)
        Text(
            meta,
            fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
            color = HomeLightTokens.ink3,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            maxLines = 1,
        )
    }
}

// ────────────────────────── 失败位 / 空位 ──────────────────────────

@Composable
private fun LightFailedSlot(tip: String, onRetry: (() -> Unit)?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Card16)
            .background(homeLightCardColor())
            .border(1.dp, HomeLightTokens.hairline, Card16)
            .padding(20.dp),
    ) {
        Text(tip, fontSize = VeneraType.bodySecondary.size, lineHeight = VeneraType.bodySecondary.lineHeight, color = HomeLightTokens.ink2)
        if (onRetry != null) {
            Text(
                DesktopI18n.t("重试", "Retry"),
                fontSize = VeneraType.bodySecondary.size, lineHeight = VeneraType.bodySecondary.lineHeight,
                fontWeight = VeneraType.cardTitle.weight,
                color = HomeLightTokens.accent,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onRetry,
                    ),
            )
        }
    }
}

@Composable
private fun LightEmptySlot(tip: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Card16)
            .background(homeLightCardColor())
            .border(1.dp, HomeLightTokens.hairline, Card16)
            .padding(20.dp),
    ) {
        Text(tip, fontSize = VeneraType.bodySecondary.size, lineHeight = VeneraType.bodySecondary.lineHeight, color = HomeLightTokens.ink3)
    }
}

// ────────────────────────── 底部玻璃悬浮条（haze）──────────────────────────

@Composable
private fun LightReadingDock(
    modifier: Modifier = Modifier,
    record: HistoryRecord,
    hazeState: dev.chrisbanes.haze.HazeState,
    onContinue: () -> Unit,
    onOpenAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val percent = readingPercentOf(record)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .vShadow(8.dp, Card16)
            .clip(Card16)
            .devHazeGlass(hazeState)
            .background(HomeLightTokens.glassBar)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 封面
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFEDEFF2)),
        ) {
            if (record.coverUrl.isNotBlank()) {
                VeneraCover(
                    url = record.coverUrl,
                    contentDescription = record.title,
                    modifier = Modifier.matchParentSize(),
                    mask = VeneraCoverMask.Visible,
                    preserveAspectRatio = false,
                )
            }
        }
        // 题名 + 进度
        Column(modifier = Modifier.width(220.dp)) {
            Text(
                record.title,
                fontSize = VeneraType.bodySecondary.size, lineHeight = VeneraType.bodySecondary.lineHeight,
                fontWeight = VeneraType.cardTitle.weight,
                color = HomeLightTokens.ink1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                heroSubOf(record, isEn = com.venera.desktop.settings.LocalDesktopLanguage.current == com.venera.desktop.settings.DesktopLanguage.EN).substringAfter(" · "),
                fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                color = HomeLightTokens.ink3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        // 进度条
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(Pill)
                    .background(HomeLightTokens.barTrack),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(percent / 100f)
                        .fillMaxHeight()
                        .clip(Pill)
                        .background(HomeLightTokens.accent)
                )
            }
            Text("$percent%", fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight, color = HomeLightTokens.ink2)
        }
        // 动作：历史 / 继续阅读 / 关闭
        LightDockIconButton(icon = Icons.Outlined.History, contentDescription = DesktopI18n.t("历史", "History"), onClick = onOpenAll)
        LightDockPrimaryButton(onClick = onContinue)
        LightDockIconButton(icon = Icons.Outlined.Close, contentDescription = DesktopI18n.t("关闭悬浮条", "Dismiss"), onClick = onDismiss)
    }
}

@Composable
private fun LightDockIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (hovered) HomeLightTokens.hairline else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = HomeLightTokens.ink2, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun LightDockPrimaryButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, label = "dockBtnPress")
    Box(
        modifier = Modifier
            .size(36.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(Pill)
            .background(HomeLightTokens.ink1)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // 圆钮底是 ink1（浅档近黑 / 深档白），字必须取**反档**才不会在深色下白底白字。
        Text("→", color = HomeLightTokens.pageBg, fontSize = VeneraType.cardTitle.size, lineHeight = VeneraType.cardTitle.lineHeight, fontWeight = VeneraType.cardTitle.weight)
    }
}

// ────────────────────────── haze 挂点（集中两颗，编不过只改这里）──────────────────────────

/** 内容层登记为 haze 源（底部悬浮条糊这一层）。 */
private fun Modifier.devHazeSource(state: HazeState): Modifier =
    this.hazeSource(state)

/** 玻璃层：haze 模糊已登记的源（30dp，白 tint），叠加半透白底由调用点自己画。 */
private fun Modifier.devHazeGlass(state: HazeState): Modifier {
    // 精简特效档：只摘模糊，调用点自己那层半透白底还在，玻璃条退成半透实底。
    if (com.venera.desktop.ui.DesktopEffects.reduce) return this
    val style = HazeBlurStyle {
        blurRadius(30.dp)
        colorEffects(listOf(HazeColorEffect.tint(Color.White.copy(alpha = 0.35f))))
    }
    return this.hazeBlur(HazeInput.Sources(state), style)
}

private val Card20 = RoundedCornerShape(20.dp)
