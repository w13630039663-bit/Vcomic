package com.venera.desktop.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.desktop.ui.theme.VeneraType

private val EditorialSurface = Color(0xFF222228)
private val EditorialText1 = Color(0xFFF2F0F5)
private val EditorialText2 = Color(0xFFC4C0CA)
private val EditorialText3 = Color(0xFF8A8692)
private val EditorialAccent = Color(0xFF5B9CFF)
private val EditorialAccentDim = Color(0x1F5B9CFF)
private val EditorialBorder = Color(0x1AFFFFFF)
private val EditorialWarn = Color(0xFFFBBF24)
private val EditorialOk = Color(0xFF4ADE80)
private val EditorialCardShape = RoundedCornerShape(12.dp)

/** 每批行数：网格「同屏最多 N 列 × 3 行」的行数口径，列数随窗宽 4-5-6-7。 */
private const val BATCH_ROWS = 3

/**
 * 桌面「每日热门」聚合页（编辑台第三版：左右分栏 + 区头切换 + 网格滚动展开）。
 *
 * 图库栏在左（Yande 每日榜 / Konachan 热门分切换）、pixiv 栏在右（日榜 / 周榜 / 月榜切换），
 * 两栏各自独立滚动；网格每批 = 列数 × 3 行，滚到底自动展开一批，「查看更多」同款双通道，
 * 池尽挂「没有更多」终态。与共享 `GalleryDailyScreenBody` 并存：那一颗同时服务安卓。
 *
 * @param yandeCards Yande 每日榜的守卫投影卡（装配层从 `dailyCore.pools` 现算；日榜单页池尽即终态）
 * @param yandeLoading Yande 榜首轮取数中（摆骨架而非空白）
 * @param yandeError Yande 榜整轮失败串（非空时该栏显错误槽）
 * @param konachanCards Konachan 热门分已上屏的卡（装配腿 order:score 可分页）
 * @param konachanLoading Konachan 腿取数中
 * @param konachanHasMore Konachan 是否还有下一页（false 时该栏挂「没有更多」）
 * @param onKonachanLoadMore Konachan 续页扳机（装配层 effect 现取下一页）
 * @param pixivCards pixiv 当前档的作品卡
 * @param pixivAvatars uid → 画师头像（榜单腿随页带回；缺位退首字母座，是唯一卡既有行为）
 * @param pixivTitles uid → 站方原题（递 [DesktopGalleryCard.titleOverride]）
 * @param pixivLoading pixiv 腿取数中
 * @param pixivHasMore pixiv 是否还有下一页（续页真取，站方分页腿）
 * @param onPixivLoadMore pixiv 续页扳机
 * @param onPickPixivTab 切日榜 / 周榜 / 月榜档（装配层换腿并重置批次）
 * @param date 榜单日期读数
 * @param error 整轮失败串（图库栏显错误槽）
 * @param onOpenCard 点卡进作品详情页（装配统一入口：booru → 图库详情页、pixiv → pixiv 作品页）
 * @param onSearchTag 右键「搜画师」的交接门（唯一卡必填，照账尺纪律显式递）
 * @param onRemix 换一批；装配层先本地重排、无池退回真取
 * @param onRetry 整轮失败后的重试
 */
@Composable
internal fun DesktopDailyEditorialScreen(
    yandeCards: List<DesktopWallCardModel>,
    yandeLoading: Boolean,
    yandeError: String?,
    konachanCards: List<DesktopWallCardModel>,
    konachanLoading: Boolean,
    konachanHasMore: Boolean,
    onKonachanLoadMore: () -> Unit,
    pixivCards: List<DesktopWallCardModel>,
    pixivAvatars: Map<String, String>,
    pixivTitles: Map<String, String>,
    pixivLoading: Boolean,
    pixivHasMore: Boolean,
    onPixivLoadMore: () -> Unit,
    onPickPixivTab: (Int) -> Unit,
    pixivTab: Int,
    date: String,
    error: String?,
    onOpenCard: (DesktopWallCardModel) -> Unit,
    onSearchTag: (String) -> Unit,
    onRemix: () -> Unit,
    onRetry: () -> Unit,
    /** 网格列距：对齐画廊主墙同密度档的 `DesktopWallSpec.wallGap`（主人要求间距一致）。 */
    wallGap: androidx.compose.ui.unit.Dp = 16.dp,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // 列数按窗宽走原响应式档位（4-5-6-7）；每批 = 列数 × 3 行。
        val cols = when {
            maxWidth >= 2060.dp -> 7
            maxWidth >= 1740.dp -> 6
            maxWidth >= 1440.dp -> 5
            else -> 4
        }
        // pixiv 栏卡大点：列数比图库少一档（下限 3）。
        val pixivCols = maxOf(3, cols - 1)
        val pixivBatch = { pixivCols * BATCH_ROWS }
        // 图库栏档位（0 = Yande 每日榜、1 = Konachan 热门分）与各档已展开批数；页内态，切档重置。
        var booruPick by remember { mutableIntStateOf(0) }
        var yandeBatches by remember { mutableIntStateOf(1) }
        var konachanBatches by remember { mutableIntStateOf(1) }
        var pixivBatches by remember(pixivTab) { mutableIntStateOf(1) }
        // 已展开张数 = 批数 × 列数 × 3 行；查看更多与滚到底都是批次 +1。
        val visibleOf: (Int) -> Int = { batches -> batches * cols * BATCH_ROWS }
        val yandeScroll = rememberScrollState()
        val konachanScroll = rememberScrollState()
        val pixivScroll = rememberScrollState()

        Column(modifier = Modifier.fillMaxSize()) {
            EditorialHeader(date = date, isLoading = yandeLoading, error = error, onRemix = {
                    yandeBatches = 1; konachanBatches = 1; pixivBatches = 1
                    onRemix()
                }, onRetry = onRetry)
            SourceStatusRow(
                title = "图库", icon = "▦",
                stat = if (booruPick == 0) "Yande 每日榜 · ${yandeCards.size} 张" else "Konachan 热门分 · ${konachanCards.size} 张",
                state = when {
                    error != null -> SourceState.OFF
                    booruPick == 0 && yandeCards.isEmpty() -> if (yandeLoading) SourceState.LOADING else SourceState.OFF
                    booruPick == 1 && konachanCards.isEmpty() -> if (konachanLoading) SourceState.LOADING else SourceState.OFF
                    else -> SourceState.OK
                },
            )
            SourceStatusRow(
                title = "pixiv", icon = "▢",
                stat = if (pixivLoading && pixivCards.isEmpty()) "加载中" else "${pixivCards.size} 张",
                state = if (pixivCards.isEmpty()) (if (pixivLoading) SourceState.LOADING else SourceState.OFF) else SourceState.OK,
            )
            Spacer(Modifier.height(16.dp))
            // 左右分栏：两栏各自独立滚，互不推挤；窄窗不改（桌面档位最低 4 列仍放得下半窗）。
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(yandeScroll),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (booruPick == 0) {
                        PaneSection(
                            idx = "01", title = "图库",
                            cap = "每日榜 · ${yandeCards.size} / ${yandeCards.size} 张",
                            switches = listOf("Yande", "Konachan"), picked = 0,
                            onPick = { booruPick = 1 },
                        )
                        RankGrid(
                            cards = yandeCards, cols = cols, show = visibleOf(yandeBatches),
                            loading = yandeLoading, error = yandeError,
                            onOpenCard = onOpenCard, onSearchTag = onSearchTag, wallGap = wallGap,
                        )
                        ExpandFooter(
                            exhausted = true,
                            cap = if (yandeCards.isEmpty()) "本站未返回" else "Yande 每日榜 · 已展 ${visibleOf(yandeBatches)} / ${yandeCards.size} 张",
                            onMore = null,
                        )
                    } else {
                        PaneSection(
                            idx = "01", title = "图库",
                            cap = "热门分 · ${konachanCards.size} 张",
                            switches = listOf("Yande", "Konachan"), picked = 1,
                            onPick = { booruPick = 0 },
                        )
                        RankGrid(
                            cards = konachanCards, cols = cols, show = visibleOf(konachanBatches),
                            loading = konachanLoading, error = null,
                            onOpenCard = onOpenCard, onSearchTag = onSearchTag, wallGap = wallGap,
                        )
                        ExpandFooter(
                            exhausted = !konachanHasMore && konachanCards.isNotEmpty(),
                            cap = if (konachanLoading) "加载中" else "热门分 · 已展 ${konachanCards.size} 张",
                            onMore = { onKonachanLoadMore(); konachanBatches++ },
                            moreVisible = konachanHasMore && konachanCards.isNotEmpty(),
                        )
                        // 滚到底自动展开下一批（与「查看更多」同款双通道）。
                        AutoExpandOnBottom(konachanScroll) { if (konachanHasMore && !konachanLoading) { onKonachanLoadMore(); konachanBatches++ } }
                    }
                }
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(pixivScroll),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PaneSection(
                        idx = "02", title = "pixiv",
                        cap = if (pixivCards.isEmpty()) "加载中" else "站方排行榜 · ${pixivCards.size} 张",
                        switches = listOf("日榜", "周榜", "月榜"), picked = pixivTab,
                        onPick = onPickPixivTab,
                    )
                    RankGrid(
                        cards = pixivCards, cols = pixivCols, show = pixivBatches * pixivCols * BATCH_ROWS,
                        loading = pixivLoading, error = null,
                        onOpenCard = onOpenCard, onSearchTag = onSearchTag,
                        avatars = pixivAvatars, titles = pixivTitles, floatIn = true, wallGap = wallGap,
                    )
                    ExpandFooter(
                        exhausted = !pixivHasMore && pixivCards.isNotEmpty(),
                        cap = if (pixivLoading) "加载中" else "站方排行榜 · 已展 ${pixivCards.size} 张",
                        onMore = { onPixivLoadMore(); pixivBatches++ },
                        moreVisible = pixivHasMore && pixivCards.isNotEmpty(),
                    )
                    AutoExpandOnBottom(pixivScroll) { if (pixivHasMore && !pixivLoading) { onPixivLoadMore(); pixivBatches++ } }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "数据为站方返回。卡片、右键、收藏与大图入口沿用现有唯一卡；Konachan 无日榜端点，口径为热门分。",
                fontSize = 11.sp, lineHeight = 18.sp, color = EditorialText3,
            )
        }
    }
}

private enum class SourceState { OK, WARN, OFF, LOADING }

@Composable
private fun EditorialHeader(
    date: String,
    isLoading: Boolean,
    error: String?,
    onRemix: () -> Unit,
    onRetry: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth().height(72.dp)) {
        Column(modifier = Modifier.align(Alignment.BottomStart)) {
            Text(com.venera.desktop.settings.DesktopI18n.t("每日热门", "Daily Popular"), fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold, color = EditorialText1)
            Text(com.venera.desktop.settings.DesktopI18n.t("图库 · pixiv 榜单聚合", "Gallery & Pixiv Rankings"), fontSize = 12.sp, lineHeight = 18.sp, color = EditorialText3)
        }
        // 巨号日期水印：只作排印锚点，不吃指针。
        Text(
            text = date.ifBlank { "-- / --" },
            fontSize = 56.sp, lineHeight = 60.sp, fontWeight = FontWeight.Bold,
            color = Color.Transparent,
            style = TextStyle.Default.copy(
                drawStyle = Stroke(width = 1.5f),
                color = EditorialText3.copy(alpha = 0.38f),
            ),
            modifier = Modifier.align(Alignment.TopEnd).padding(end = 170.dp),
        )
        Row(
            modifier = Modifier.align(Alignment.BottomEnd),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = when {
                    error != null -> com.venera.desktop.settings.DesktopI18n.t("加载失败", "Load failed")
                    isLoading -> com.venera.desktop.settings.DesktopI18n.t("加载中", "Loading")
                    else -> com.venera.desktop.settings.DesktopI18n.t("$date 榜单 · 已到齐", "$date · Ready")
                },
                fontSize = 11.sp, color = EditorialText2,
            )
            EditorialAction(com.venera.desktop.settings.DesktopI18n.t("换一批", "Shuffle"), onRemix)
            if (error != null) EditorialAction(com.venera.desktop.settings.DesktopI18n.t("重试", "Retry"), onRetry)
        }
    }
    if (error != null) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = com.venera.desktop.settings.DesktopI18n.t("网络连接超时 · 可重试", "Network timeout · Retry"),
            fontSize = 12.sp, color = Color(0xFFF87171),
            modifier = Modifier
                .background(EditorialSurface, EditorialCardShape)
                .border(1.dp, EditorialBorder, EditorialCardShape)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .fillMaxWidth(),
        )
    }
}

@Composable
private fun EditorialAction(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(32.dp)
            .background(EditorialSurface, RoundedCornerShape(10.dp))
            .border(1.dp, EditorialBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 12.sp, color = EditorialText2)
    }
}

@Composable
private fun SourceStatusRow(
    title: String,
    icon: String,
    stat: String,
    state: SourceState,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(40.dp).border(1.dp, EditorialBorder),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, fontSize = 14.sp, color = EditorialText2, modifier = Modifier.padding(start = 10.dp))
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = EditorialText1,
            modifier = Modifier.padding(start = 10.dp))
        Text(stat, fontSize = 11.sp, color = EditorialText3, modifier = Modifier.padding(start = 12.dp))
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .padding(end = 12.dp)
                .width(7.dp).height(7.dp)
                .background(
                    when (state) {
                        SourceState.OK -> EditorialOk
                        SourceState.WARN -> EditorialWarn
                        else -> EditorialText3
                    },
                    RoundedCornerShape(50),
                ),
        )
    }
}

@Composable
private fun PaneSection(
    idx: String,
    title: String,
    cap: String,
    switches: List<String>,
    picked: Int,
    onPick: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(idx, fontSize = 11.sp, color = EditorialText3, letterSpacing = 1.2.sp)
        Text(title, fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold, color = EditorialText1)
        Text(cap, fontSize = 11.sp, color = EditorialText3)
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier
                .background(EditorialSurface, RoundedCornerShape(10.dp))
                .border(1.dp, EditorialBorder, RoundedCornerShape(10.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            switches.forEachIndexed { i, label ->
                val on = i == picked
                Box(
                    modifier = Modifier
                        .height(28.dp)
                        .background(if (on) EditorialAccentDim else Color.Transparent, RoundedCornerShape(7.dp))
                        .clickable { onPick(i) }
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        fontSize = 12.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (on) EditorialAccent else EditorialText3,
                    )
                }
            }
        }
    }
}

/**
 * 榜单网格：列数 × 3 行一批；前 3 名压 48sp 描边名次（榜单语义的排印表达，不是装饰）。
 * pixiv 卡走 [PixivEditorialCard]（头像 + 原题 + 加载浮动入场）；图库卡走同一颗唯一卡。
 */
@Composable
private fun RankGrid(
    cards: List<DesktopWallCardModel>,
    cols: Int,
    show: Int,
    loading: Boolean,
    error: String?,
    onOpenCard: (DesktopWallCardModel) -> Unit,
    onSearchTag: (String) -> Unit,
    avatars: Map<String, String> = emptyMap(),
    titles: Map<String, String> = emptyMap(),
    floatIn: Boolean = false,
    wallGap: androidx.compose.ui.unit.Dp = 16.dp,
) {
    when {
        error != null -> AbsentNote(error)
        cards.isEmpty() && loading -> Column(verticalArrangement = Arrangement.spacedBy(32.dp)) {
            repeat(BATCH_ROWS) { r ->
                Row(horizontalArrangement = Arrangement.spacedBy(wallGap)) {
                    repeat(cols) { EditorialSkeleton(Modifier.weight(1f).aspectRatio(1f)) }
                }
            }
        }
        cards.isEmpty() -> AbsentNote("暂时没有内容")
        else -> {
            val visible = cards.take(show)
            visible.chunked(cols).forEachIndexed { rowIdx, row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(wallGap),
                ) {
                    row.forEachIndexed { i, card ->
                        val globalIndex = rowIdx * cols + i
                        Box(modifier = Modifier.weight(1f)) {
                            if (floatIn) {
                                PixivEditorialCard(
                                    card = card,
                                    rank = if (globalIndex < 3) (globalIndex + 1).toString().padStart(2, '0') else null,
                                    avatars = avatars, titles = titles,
                                    onOpenCard = onOpenCard, onSearchTag = onSearchTag,
                                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                                )
                            } else {
                                EditorialGridCard(
                                    card = card,
                                    rank = if (globalIndex < 3) (globalIndex + 1).toString().padStart(2, '0') else null,
                                    onOpenCard = onOpenCard, onSearchTag = onSearchTag,
                                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                                )
                            }
                        }
                    }
                    repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** 展开尾：可展时摆「查看更多」，池尽摆「没有更多」文字态；不可见时整颗不摆。 */
@Composable
private fun ExpandFooter(
    exhausted: Boolean,
    cap: String,
    onMore: (() -> Unit)?,
    moreVisible: Boolean = false,
) {
    if (!moreVisible) {
        Text(
            text = "没有更多 · $cap",
            fontSize = 12.sp, color = EditorialText3,
            modifier = Modifier
                .fillMaxWidth()
                .background(EditorialSurface, EditorialCardShape)
                .border(1.dp, EditorialBorder, EditorialCardShape)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        return
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .border(1.dp, EditorialBorder, EditorialCardShape)
            .clickable(enabled = onMore != null && !exhausted) { onMore?.invoke() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (exhausted) "没有更多 · $cap" else "查看更多 · $cap",
            fontSize = 13.sp, color = if (exhausted) EditorialText3 else EditorialText2,
        )
    }
}

/** 滚到底自动展开：与「查看更多」同款双通道，240dp 近底阈值。 */
@Composable
private fun AutoExpandOnBottom(
    scroll: androidx.compose.foundation.ScrollState,
    onBottom: () -> Unit,
) {
    val density = LocalDensity.current
    androidx.compose.runtime.LaunchedEffect(scroll) {
        snapshotFlow { scroll.value }
            .collect {
                val thresholdPx = with(density) { 240.dp.toPx() }
                if (scroll.value >= scroll.maxValue - thresholdPx && scroll.maxValue > 0) onBottom()
            }
    }
}

/** 图库网格卡 = 唯一卡 + 描边名次盖层；点击走 [onOpenCard] 进图库作品详情页。 */
@Composable
private fun EditorialGridCard(
    card: DesktopWallCardModel,
    rank: String?,
    onOpenCard: (DesktopWallCardModel) -> Unit,
    onSearchTag: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        DesktopGalleryCard(
            card = card,
            aspect = 1f,
            onOpenPost = onOpenCard,
            onSearchArtist = onSearchTag,
            onOpenArtist = null,
            onToggleSelect = null,
            modifier = Modifier.fillMaxSize(),
        )
        if (rank != null) {
            Text(
                text = rank, fontSize = 48.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold,
                color = Color.Transparent,
                style = TextStyle.Default.copy(
                    drawStyle = Stroke(width = 1.5f), color = EditorialText1.copy(alpha = 0.85f),
                ),
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 40.dp),
            )
        }
    }
}

/**
 * pixiv 卡 = 唯一卡（带头像与原题两张表）+ 描边名次盖层。
 *
 * 加载完成有一次性浮动入场：隐形探针（同 URL 命中 Coil 内存缓存，零额外解码）探到真卡图就绪，
 * 整卡才从 8dp 下 + 透明浮上来 —— 图没到摆骨架，不是先摆空卡再闪一下。
 */
@Composable
private fun PixivEditorialCard(
    card: DesktopWallCardModel,
    rank: String?,
    avatars: Map<String, String>,
    titles: Map<String, String>,
    onOpenCard: (DesktopWallCardModel) -> Unit,
    onSearchTag: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uid = card.uid.ifBlank { card.sourcePost?.uid ?: "" }
    var imageReady by remember(card.url) { mutableStateOf(false) }
    val floatProgress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (imageReady) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(300),
        label = "pixiv-card-float-in",
    )
    Box(modifier = modifier) {
        if (!imageReady) EditorialSkeleton(Modifier.fillMaxSize())
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = floatProgress
                    translationY = (1f - floatProgress) * 8.dp.toPx()
                },
        ) {
            DesktopGalleryCard(
                card = card,
                // 方卡（外盒 1:1）+ 图裁切：原比例竖图会把底部信息栏挤出外盒，时有时无就是这样来的。
                aspect = 1f,
                onOpenPost = onOpenCard,
                onSearchArtist = onSearchTag,
                onOpenArtist = null,
                onToggleSelect = null,
                titleOverride = titles[uid],
                avatarUrl = avatars[uid],
                modifier = Modifier.fillMaxSize(),
            )
        }
        coil3.compose.AsyncImage(
            model = card.url,
            contentDescription = null,
            modifier = Modifier.fillMaxSize().alpha(0f),
            onSuccess = { imageReady = true },
        )
        if (rank != null) {
            Text(
                text = rank, fontSize = 48.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold,
                color = Color.Transparent,
                style = TextStyle.Default.copy(
                    drawStyle = Stroke(width = 1.5f), color = EditorialText1.copy(alpha = 0.85f),
                ),
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 40.dp),
            )
        }
    }
}

@Composable
private fun AbsentNote(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, EditorialBorder, EditorialCardShape)
            .padding(horizontal = 12.dp, vertical = 24.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(7.dp).height(7.dp).background(EditorialWarn, RoundedCornerShape(50)))
            Text(text, fontSize = 12.sp, color = EditorialText3, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun EditorialSkeleton(modifier: Modifier = Modifier) {
    Box(modifier = modifier.clip(EditorialCardShape).background(Color(0xFF2A2A31)))
}
