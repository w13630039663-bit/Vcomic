package com.venera.desktop.gallery.ui

import com.venera.desktop.ui.vShadow
import com.venera.desktop.ui.theme.VeneraType
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryHomeDomain
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.gallery.ui.DesktopGalleryMetrics.wallGridGap
import com.venera.desktop.ui.DesktopIconToolButton
import com.venera.desktop.ui.MacMenuItem
import com.venera.desktop.ui.MacMenuDivider
import com.venera.desktop.ui.MacMenuSection
import com.venera.desktop.ui.DesktopPopUpButton
import com.venera.desktop.ui.DesktopSegmented

/**
 * 顶栏统一方案（`desktop/docs/designs/desktop-header-unify-2026-11-05.html`）落地 ——
 * 一条连续工具条，两域共用骨架；规格三件套在 `docs/designs/header-unify-2026-11-05/`。
 *
 * ## 改了什么
 * - 撤三座 12dp 玻璃岛、36dp 蓝色徽标、渐变蓝主按钮、条内 `Ctrl K` 键帽、长提示句；
 * - 源段改纯文字档（选中 = 2dp 底边线），搜索条 240–480dp 短占位，右区统一 32dp 灰底方钮；
 * - 状态胶囊组收成一行圆点 + 短文本，真账（到货 / 缺席 / 登录 / 推荐腿）全部继续消费。
 *
 * ## 没动什么
 * 全部形参与回调、`onOpenSearch` / `onRefresh` / `onOpenAccount` 路由、
 * `DesktopGalleryBatch` 派生账、共享 `components.venera.*`、BusinessPorts。
 */
// 「栏太小」二次复核：44 → 52 → **60dp**，控件 26 → 28 → 32。
private val DesktopGalleryTopbarHeight = 60.dp

/** 站徽色点（稿面个性化记号）：图库站青 / Pixiv 官方蓝。色值取自站方品牌，不进色板。 */
internal val GalleryStationDot = Color(0xFF00B3B0)
internal val PixivStationDot = Color(0xFF0096FA)

/**
 * 图库站 / Pixiv 共用顶栏（顶栏稿 `gallery-pixiv-topbar-2026-11-08.html`）。
 *
 * ## 为什么重做
 * 旧结构两层到三层：顶栏 56 + 筛选架 ≥48 + 标签维度舱 ≈40 = 图库站顶部吃 144dp，
 * 而 Pixiv 只有 56 + 状态行 24 = 80dp —— 切站内容区起点跳 64dp，且筛选架 19 枚 chip 平铺
 * （子源按真接入 booru 站动态生成，接第 5 个站就要换行）。
 *
 * ## 现在
 * **两站同一层 44dp**，与搜索 / 探索页同族（44 工具条 + 26 控件）：
 * 分段 / 搜索场 / 3 个 pop-up（评分·排序·子源，图库站独有）/ 读数 / 视图·刷新·更多（Pixiv 多「我的」）。
 *
 * ## 没动什么
 * 形参与回调一个不少；pop-up 仍走全站唯一那颗 `DesktopPopUpButton`（`⇅` 把手，
 * 与搜索 / 探索页同一形）；读数仍吃 `DesktopGalleryBatch` 真账（张数 / 逐站到货 / 缺席 / 登录态 /
 * 推荐腿），只把「一行独占」改成「并入工具条」，悬停展开的逐站明细原样保留。
 *
 * ## 窄窗降级（稿面 §4）
 * 可用宽 < 820dp 时 pop-up 只显示值（省掉「评分 / 排序 / 子源」三个类别词），
 * 让搜索场回到 150dp 以上；读数靠 ellipsis 收。
 */
@Composable
internal fun DesktopGalleryChromeRow(
    domain: GalleryHomeDomain,
    onPickDomain: (GalleryHomeDomain) -> Unit,
    /** PIXIV 域登录态读数（scaffold 从 `authState` 读一帧）。 */
    pixivSignedIn: Boolean,
    /** 中段搜索场槽（真输入场 + 聚焦下拉，由 pane 装好递进来）。 */
    topSearch: @Composable () -> Unit,
    /** 与右下浮动刷新钮同一记扳机（`wallRefreshTick++`），顶栏这枚是同一个门的第二只把手。 */
    onRefresh: () -> Unit,
    /** PIXIV 域「我的」落点（账号屏；BOORU 域不摆这一枚）。 */
    onOpenAccount: () -> Unit,
    // ── 顶栏稿：原筛选架三件过滤 + 张数读数，收进这一层 ──
    rating: DesktopGalleryRatingFilter,
    onPickRating: (DesktopGalleryRatingFilter) -> Unit,
    sort: DesktopGallerySort,
    onPickSort: (DesktopGallerySort) -> Unit,
    booruSites: List<GallerySite>,
    pickedSite: GallerySite?,
    onPickSite: (GallerySite?) -> Unit,
    /** 各站这一轮**上屏**的张数（只数真到货的腿；空表 = 还没到货）。 */
    perSiteCounts: Map<GallerySite, Int>,
    /** 逐站缺席原因（没凭据 / 超时 / 预算到点）。 */
    failures: Map<GallerySite, String>,
    /** 过滤后上屏张数（读数第一段）。 */
    visibleCount: Int,
    pixivWallRecommended: Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = LocalChromePalette.current
    val booru = domain == GalleryHomeDomain.BOORU
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // 窄窗降级：类别词让位给搜索场（控件升到 32dp 后阈值从 820 提到 940）。
        val compactPops = maxWidth < 940.dp
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(DesktopGalleryTopbarHeight)
                .padding(start = 16.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── 左：站切换（mac 白滑块 + 站徽色点） ──
            DesktopSegmented(
                options = GalleryHomeDomain.entries.map { it.label },
                selectedIndex = GalleryHomeDomain.entries.indexOf(domain),
                onSelect = { index -> onPickDomain(GalleryHomeDomain.entries[index]) },
                leadingDots = GalleryHomeDomain.entries.map {
                    when (it) {
                        GalleryHomeDomain.BOORU -> GalleryStationDot
                        GalleryHomeDomain.PIXIV -> PixivStationDot
                    }
                },
                compact = true,
            )

            // ── 中：真输入悬浮搜索场（聚焦即下拉，回车进检索屏） ──
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                topSearch()
            }

            // ── 右：过滤三件（图库站独有）+ 读数 + 工具钮 ──
            if (booru) {
                DesktopPopUpButton(
                    label = if (compactPops) null else "评分",
                    value = rating.label,
                    options = DesktopGalleryRatingFilter.entries.map { it.label },
                    selectedIndex = DesktopGalleryRatingFilter.entries.indexOf(rating),
                    onSelect = { index -> onPickRating(DesktopGalleryRatingFilter.entries[index]) },
                )
                DesktopPopUpButton(
                    label = if (compactPops) null else "排序",
                    value = sort.label,
                    options = DesktopGallerySort.OPTIONS.map { it.label },
                    selectedIndex = DesktopGallerySort.OPTIONS.indexOf(sort),
                    onSelect = { index -> onPickSort(DesktopGallerySort.OPTIONS[index]) },
                )
                // 子源按真接入的 booru 站动态生成（`GallerySite` 里没有的站一个不编）。
                DesktopPopUpButton(
                    label = if (compactPops) null else "子源",
                    value = pickedSite?.displayName ?: "聚合",
                    options = listOf("全部聚合") + booruSites.map { it.displayName },
                    selectedIndex = pickedSite?.let { site -> booruSites.indexOf(site) + 1 } ?: 0,
                    onSelect = { index -> onPickSite(if (index == 0) null else booruSites[index - 1]) },
                )
            }

            DesktopGalleryStatusReadout(
                domain = domain,
                perSiteCounts = perSiteCounts,
                failures = failures,
                pixivSignedIn = pixivSignedIn,
                pixivWallRecommended = pixivWallRecommended,
                visibleCount = visibleCount,
                // 读数不抢宽：长读数省略，把余量留给搜索场。
                modifier = Modifier.widthIn(max = 200.dp),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // 「画廊设定的选择器不能有多处不同的位置」：
                // 布局 / 密度**只住设置页**（`DesktopSettingsHub` 那八枚 setter 的落点，
                // `DesktopSettingsEntryTest` 钉死），顶栏不再重复摆第二处 —— 视图按钮随之撤掉。
                DesktopIconToolButton(
                    contentDescription = "刷新",
                    onClick = onRefresh,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = null,
                        tint = palette.ink2,
                        modifier = Modifier.size(15.dp),
                    )
                }
                GalleryMoreMenuButton(
                    onRefresh = onRefresh,
                )
                if (domain == GalleryHomeDomain.PIXIV) {
                    DesktopIconToolButton(
                        contentDescription = "我的 pixiv",
                        onClick = onOpenAccount,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PersonOutline,
                            contentDescription = null,
                            tint = palette.ink2,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 更多菜单：只剩**真入口** —— 两颗快捷键（键位表之外唯一能看见的地方）。
 *
 * 旧菜单四项（布局 / 密度 / 搜索标签 / 刷新）全是死项：布局密度筛选架已有同款、
 * 搜索标签与刷新顶栏已有同款 ⇒ 0 个独有入口。现在布局密度归视图菜单，这里只留快捷键。
 */
@Composable
private fun GalleryMoreMenuButton(
    onRefresh: () -> Unit,
) {
    val palette = LocalChromePalette.current
    var open by remember { mutableStateOf(false) }
    Box {
        DesktopIconToolButton(
            contentDescription = "更多",
            onClick = { open = true },
        ) {
            Icon(
                imageVector = Icons.Outlined.MoreHoriz,
                contentDescription = null,
                tint = palette.ink2,
                modifier = Modifier.size(15.dp),
            )
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier
                .widthIn(min = 186.dp)
                .vShadow(8.dp, RoundedCornerShape(9.dp))
                .background(palette.elevated, RoundedCornerShape(9.dp))
                .border(0.5.dp, palette.hairline, RoundedCornerShape(9.dp)),
        ) {
            Column(Modifier.padding(vertical = 4.dp)) {
                // 「搜索」一项已删：顶栏中段同位置就是真输入场（悬浮），
                // 同内容两扇门里留活的那扇。
                MacMenuItem("刷新", shortcut = "Ctrl R") {
                    open = false
                    onRefresh()
                }
            }
        }
    }
}

/**
 * 站源读数（原 `DesktopGallerySourceBar`，顶栏稿并进工具条）。
 *
 * 全部吃 `DesktopGalleryBatch` 这本**真账**：每站上了几张 = `posts` 按站数；
 * 谁没答上 + 为什么 = `failures`；登录态与推荐腿来自 scaffold。不编造数据。
 *
 * 形态：`● 183 张 · 已接入 2源` 一行，绿点 / 橙点表正常 / 告警；
 * 点开是逐站明细（与页尾同一本账的两种摆法，不各写一套措辞）。
 */
@Composable
internal fun DesktopGalleryStatusReadout(
    domain: GalleryHomeDomain,
    perSiteCounts: Map<GallerySite, Int>,
    failures: Map<GallerySite, String>,
    pixivSignedIn: Boolean,
    pixivWallRecommended: Boolean = false,
    /** 过滤后上屏张数；PIXIV 域不用（那边念登录态）。 */
    visibleCount: Int = 0,
    modifier: Modifier = Modifier,
) {
    val palette = LocalChromePalette.current
    var expanded by remember { mutableStateOf(false) }

    // 短文本：全部由真账派生，不写死"看起来像"的读数。
    val warn: Boolean
    val summary = when (domain) {
        GalleryHomeDomain.PIXIV -> when {
            pixivSignedIn && pixivWallRecommended -> {
                warn = false
                "已登录 · 推荐流"
            }
            pixivSignedIn -> {
                warn = true
                "推荐流未响应"
            }
            else -> {
                warn = false
                "匿名榜单"
            }
        }
        GalleryHomeDomain.BOORU -> when {
            perSiteCounts.isEmpty() && failures.isEmpty() -> {
                warn = false
                if (visibleCount > 0) "$visibleCount 张 · 加载中" else "加载中"
            }
            else -> {
                warn = failures.isNotEmpty()
                val n = perSiteCounts.size
                val m = failures.size
                val head = if (visibleCount > 0) "$visibleCount 张 · " else ""
                if (m > 0) "${head}${m}源缺席" else "${head}已接入 ${n}源"
            }
        }
    }

    // 完整逐站读数（点开）：与页尾同一本账的两种摆法，不各写一套措辞。
    val fullDetail = buildList {
        if (domain == GalleryHomeDomain.PIXIV) {
            when {
                pixivSignedIn && pixivWallRecommended -> add("Pixiv 已登录 · 站方推荐流")
                pixivSignedIn -> {
                    add("Pixiv 已登录 · 当前为榜单")
                    add("点刷新重试推荐")
                }
                else -> {
                    add("Pixiv 匿名可读 · 当前是榜单墙")
                    add("登录后换成站方推荐流")
                }
            }
            if (!pixivSignedIn) add("登录后可解锁关注流与个人收藏")
        } else {
            if (perSiteCounts.isEmpty() && failures.isEmpty()) {
                add("加载中")
            } else {
                if (perSiteCounts.isNotEmpty()) {
                    val tally = perSiteCounts.entries.joinToString(" · ") { (site, count) ->
                        "${site.displayName} $count"
                    }
                    add("已接入 $tally")
                }
                failures.forEach { (site, reason) ->
                    add("${site.displayName} $reason")
                }
            }
        }
    }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .height(32.dp)
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (warn) palette.warn else palette.ok),
            )
            Text(
                text = summary,
                fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                color = palette.ink3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .vShadow(8.dp, RoundedCornerShape(9.dp))
                .background(palette.elevated, RoundedCornerShape(9.dp))
                .border(0.5.dp, palette.hairline, RoundedCornerShape(9.dp)),
        ) {
            Column(Modifier.padding(vertical = 4.dp)) {
                fullDetail.forEach { line ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = line,
                                fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                                color = palette.ink2,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        onClick = { expanded = false },
                        modifier = Modifier.height(26.dp),
                    )
                }
            }
        }
    }
}

/**
 * 主墙那一节的节头：中文题 + 灰英签，右缘一句
 * 「N 列 · 原比例摆放 · 14dp 间距」读数。列数吃 scaffold 那颗**真值**（`columns`），间距吃
 * [wallGridGap]，"原比例摆放"吃的正是主墙那枚 `preserveNaturalImageHeight = true` 的真行为 ——
 * 稿上那句"竖图限高"在桌面这一侧**不成立**（共享网格对桌面墙不夹比例，极端长条照样按原比例摆），
 * 照抄就是假读数，所以这里念自家真行为。
 *
 * PIXIV 域的题**跟着真腿走**（用户报"看着还是榜单"后改的口径）：
 * 推荐腿上了墙 = 「为你推荐 / FOR YOU」；落回榜单腿 = 「{档名}作品墙 / RANKING」——
 * 从前恒念"{档名}作品墙"，推荐成功也念榜单，用户在屏上永远认不出墙换了腿。
 */
@Composable
internal fun DesktopGalleryWallHead(
    domain: GalleryHomeDomain,
    rankingModeLabel: String,
    columns: Int,
    /** PIXIV 域当前墙是不是站方推荐腿（见 `DesktopWallPage.isRecommended`）。BOORU 域恒 false。 */
    recommended: Boolean,
    layout: com.venera.compose.gallery.domain.GalleryWallLayout = com.venera.compose.gallery.domain.GalleryWallLayout.Grid,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    val isPixivRecommended = domain == GalleryHomeDomain.PIXIV && recommended
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = tokens.spacing.screenHorizontal),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = when {
                isPixivRecommended -> "为你推荐"
                domain == GalleryHomeDomain.PIXIV -> "${rankingModeLabel}作品墙"
                else -> "为你推荐"
            },
            // 节头题统一 itemTitle 16sp（阶段 2 取值对齐）：从前 sectionTitle 13sp，
            // 与横栏 SectionHeader / DiscoverHead 同语义双值 —— 主人截图里「为你推荐」
            // 比「排行榜」小一号就是它。
            fontSize = VeneraType.cardTitle.size, lineHeight = VeneraType.cardTitle.lineHeight,
            fontWeight = tokens.type.weightBold,
            color = tokens.color.textPrimary,
        )
        Spacer(Modifier.width(tokens.spacing.space2))
        Text(
            text = if (isPixivRecommended) "FOR YOU" else
                if (domain == GalleryHomeDomain.PIXIV) "RANKING" else "FOR YOU",
            fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
            fontWeight = tokens.type.weightMedium,
            color = tokens.color.textTertiary,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = if (layout == com.venera.compose.gallery.domain.GalleryWallLayout.Grid) {
                "等宽网格 · ${columns}列 · 1:1 方图"
            } else {
                "瀑布流 · ${columns}列 · 原比例摆放"
            },
            fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
            color = tokens.color.textTertiary,
            maxLines = 1,
        )
    }
}
