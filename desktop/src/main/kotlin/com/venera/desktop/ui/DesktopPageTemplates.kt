package com.venera.desktop.ui

import com.venera.desktop.app.VeneraDesktopLargeScreen
import com.venera.desktop.ui.theme.VeneraType
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.sp
import com.venera.desktop.gallery.ui.LocalChromePalette
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import kotlinx.coroutines.launch

import com.venera.desktop.app.LocalShellBackHandled

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.compose.components.venera.VeneraButton
import com.venera.compose.components.venera.VeneraCard
import com.venera.compose.ui.tokens.VeneraCardEnter
import com.venera.compose.ui.tokens.VeneraTokens

/**
 * 桌面二级页的公共页壳。
 *
 * 根导航仍由 `VeneraDesktopLargeScreen` 的侧栏负责；这里收口的是侧栏右侧每一页都会重复的
 * 标题、返回、说明、提示和内容宽度。页面只提供内容与动作，不再自己摆一遍页头。
 */
@Composable
internal fun DesktopPageShell(
    mode: WideScreenLayoutMode,
    title: String,
    modifier: Modifier = Modifier,
    width: DesktopPageWidth = DesktopPageWidth.Detail,
    subtitle: String? = null,
    subtitleColor: Color? = null,
    note: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    /**
     * 内容左右不留 [VeneraTokens.spacing.screenHorizontal]：墙类页要贴窗缘时用，
     * 页头卡片与内容各自决定内缩，页壳不再叠一圈。
     */
    edgeToEdgeX: Boolean = false,
    /** 页头不包卡片底、标题裸摆：贴近历史页那种无头卡观感的页用（默认 true 不影响既有调用方）。 */
    titleCard: Boolean = true,
    content: @Composable () -> Unit,
) {
    val tokens = VeneraTokens
    val maxWidth = desktopPageMaxWidth(width, mode)

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .then(if (maxWidth != null) Modifier.widthIn(max = maxWidth) else Modifier)
                .fillMaxSize()
                .then(
                    if (edgeToEdgeX) Modifier else Modifier.padding(horizontal = tokens.spacing.screenHorizontal),
                ),
        ) {
            val pageHead: @Composable () -> Unit = {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(
                        horizontal = tokens.spacing.space4,
                        vertical = tokens.spacing.space3,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space3),
                ) {
                    // 一屏一返回：壳层（titlebar / ContentHead）已摆返回时屏内这枚隐去。
                    if (onBack != null && !LocalShellBackHandled.current) {
                        DesktopBackButton(onClick = onBack)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            fontSize = VeneraType.pageTitle.size, lineHeight = VeneraType.pageTitle.lineHeight,
                            color = tokens.color.textPrimary,
                        )
                        if (subtitle != null) {
                            Text(
                                text = subtitle,
                                fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                                color = subtitleColor ?: tokens.color.textSecondary,
                            )
                        }
                    }
                    actions()
                }

                if (note != null) {
                    Text(
                        text = note,
                        fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                        color = tokens.color.textTertiary,
                        modifier = Modifier.padding(
                            start = tokens.spacing.space4,
                            end = tokens.spacing.space4,
                            bottom = tokens.spacing.space3,
                        ),
                    )
                }
            }
            if (titleCard) {
                VeneraCard(
                    modifier = Modifier.fillMaxWidth(),
                    edgeToEdge = true,
                ) {
                    pageHead()
                }
            } else {
                pageHead()
            }

            Spacer(Modifier.height(tokens.spacing.space3))
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                content()
            }
        }
    }
}

/**
 * 列表型页面的公共主体。
 *
 * 页面只交条目、键和一行怎么画；空态也走同一个槽位。这样下载队列、历史、收藏等页面
 * 不必各自再摆一套 `LazyColumn`、间距与空态分支。
 */
@Composable
internal fun <T> ListPageTemplate(
    items: List<T>,
    itemKey: (T) -> Any,
    modifier: Modifier = Modifier,
    emptyContent: @Composable () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(bottom = VeneraTokens.spacing.space6),
    verticalArrangement: Arrangement.Vertical =
        Arrangement.spacedBy(VeneraTokens.spacing.space1),
    itemContent: @Composable (T) -> Unit,
) {
    if (items.isEmpty()) {
        Box(modifier = modifier.fillMaxSize()) {
            emptyContent()
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = verticalArrangement,
    ) {
        items(items = items, key = itemKey) { item ->
            itemContent(item)
        }
    }
}

/**
 * 网格 / 瀑布流页的公共主体。
 *
 * 列数仍由调用点用同一把共享尺算好再递进来（画廊墙的 AUTO 档要读内容区实测宽，
 * 不能在这里另造第二套策略）；模板负责的是各页最容易抄歪的那一截：
 * 间距、整行头尾、分组标题、条目键和空态。
 */
@Composable
internal fun <T> GridPageTemplate(
    items: List<T>,
    columns: Int,
    itemKey: (T) -> Any,
    modifier: Modifier = Modifier,
    state: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
    contentPadding: PaddingValues = PaddingValues(bottom = VeneraTokens.spacing.space6),
    horizontalArrangement: Arrangement.Horizontal =
        Arrangement.spacedBy(VeneraTokens.spacing.gridGap),
    verticalItemSpacing: Dp = VeneraTokens.spacing.gridGap,
    /** 作品墙密度通过这一枚统一规格驱动列距（默认仍走既有 gridGap，不影响别的页）。 */
    spacing: Dp? = null,
    emptyContent: @Composable () -> Unit = {},
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    groupKey: ((T) -> Any)? = null,
    groupHeader: (@Composable (T) -> Unit)? = null,
    itemContent: @Composable (T) -> Unit,
) {
    if (items.isEmpty() && header == null) {
        Box(modifier = modifier.fillMaxSize()) {
            emptyContent()
        }
        return
    }

    val wallGap = spacing
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(columns.coerceAtLeast(1)),
        state = state,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalArrangement = if (wallGap != null) Arrangement.spacedBy(wallGap) else horizontalArrangement,
        verticalItemSpacing = wallGap ?: verticalItemSpacing,
    ) {
        if (header != null) {
            item(
                key = "grid-page-header",
                span = StaggeredGridItemSpan.FullLine,
            ) {
                header()
            }
        }

        if (items.isEmpty()) {
            item(
                key = "grid-page-empty",
                span = StaggeredGridItemSpan.FullLine,
            ) {
                emptyContent()
            }
        }

        items.forEachIndexed { index, item ->
            val previous = items.getOrNull(index - 1)
            val startsGroup = groupKey != null &&
                groupHeader != null &&
                (previous == null || groupKey(previous) != groupKey(item))
            if (startsGroup) {
                item(
                    key = "grid-page-group-${groupKey(item)}",
                    span = StaggeredGridItemSpan.FullLine,
                ) {
                    groupHeader(item)
                }
            }
            item(key = itemKey(item)) {
                VeneraCardEnter(index, itemKey(item).toString()) {
                    itemContent(item)
                }
            }
        }

        if (footer != null) {
            item(
                key = "grid-page-footer",
                span = StaggeredGridItemSpan.FullLine,
            ) {
                footer()
            }
        }
    }
}

/** 设置卡里的一行。控件由页面交插槽，模板只固定“左标题右控件”的骨架。 */
internal class SettingsRowSpec(
    val title: String,
    val caption: String?,
    val onClick: (() -> Unit)? = null,
    val control: (@Composable () -> Unit)? = null,
    val controlPlacement: SettingsControlPlacement = SettingsControlPlacement.Inline,
    /** 是否计入页头「N 项」。占位/纯展示行设 false，页头数字与左轨徽标同口径。默认 true 不影响其它页。 */
    val counted: Boolean = true,
)

/**
 * 详情页的公共页头 + 纵向滚动骨架。
 *
 * 详情屏（pixiv 作品 / 画师 / 画廊单帖详情）最容易被各抄歪的是**顶栏那一排**：返回钮、标题的
 * 省略号、右侧动作位。这里把那一排和整页的纵向滚动收口，页面只交正文条目与动作，不再各摆一遍页头。
 *
 * 与 [DesktopPageShell] 的分工：`DesktopPageShell` 是**普通二级页**（吃窗口内容宽度上限、卡片式页头），
 * 这一颗是**详情/看图这类通栏滚动页**（页头贴着内容、随正文一起滚，不设宽度上限）。
 */
@Composable
internal fun DetailPageTemplate(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = VeneraTokens
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(
                horizontal = tokens.spacing.screenHorizontal,
                vertical = tokens.spacing.space3,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space3),
        ) {
            // 一屏一返回：壳层已摆返回时屏内这枚隐去。
            if (!LocalShellBackHandled.current) {
                DesktopBackButton(onClick = onBack)
            }
            Text(
                text = title,
                fontSize = VeneraType.pageTitle.size, lineHeight = VeneraType.pageTitle.lineHeight,
                fontWeight = tokens.type.weightBold,
                color = tokens.color.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            actions()
        }
        content()
    }
}

/**
 * 全屏看图 / 阅读器这类特殊页的公共画布。
 *
 * 与其余模板不同，这一颗**恒用深色画布**（黑底），因为看图与阅读要把画面顶到最黑，
 * 不能跟着 token 的浅色 surface 走。页面交出标题、正文与可选页脚，返回钮与 Esc 之外的退出视觉都在壳里。
 *
 * 焦点抢占与键盘（← → Esc）仍留在各屏（阅读器还要翻页、看图只需 Esc），故这里只收**视觉**骨架：
 * 顶栏（返回 + 标题 + 动作）与可选页脚条都浮在正文之上，由 [chromeVisible] 控制淡入淡出；
 * 看图时画面因此能真正铺满整窗。
 */
@Composable
internal fun ViewerPageTemplate(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    chromeVisible: Boolean = true,
    /**
     * 画布底色（GLASS 桌面没有窗口模糊那层 ⇒ 念黑，
     * BLACK/GRAY/WHITE 照共享枚举的 argb）。默认黑 = 与从前逐帧一致。
     */
    backdropColor: Color = Color.Black,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val tokens = VeneraTokens
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backdropColor),
    ) {
        content()
        AnimatedVisibility(
            visible = chromeVisible,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.78f), Color.Transparent),
                        ),
                    )
                    .padding(horizontal = tokens.spacing.space4, vertical = tokens.spacing.space2),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space3),
            ) {
                // 沉浸 chrome：全屏页（reader / viewer）壳层不画返回 ⇒ local=false ⇒ 保留；
                // 万一被普通页复用，壳层已接管时隐去，避免同屏两枚。
                // 统一：全屏页用**浮动圆钮**（32dp 暗玻璃 + 白箭头）。
                if (!LocalShellBackHandled.current) {
                    DesktopBackButton(onClick = onBack, floating = true)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = VeneraType.body.size, lineHeight = VeneraType.body.lineHeight,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                            color = Color.White.copy(alpha = 0.66f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                actions()
            }
        }
        if (bottomBar != null) {
            AnimatedVisibility(
                visible = chromeVisible,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.84f)),
                            ),
                        )
                        .padding(horizontal = tokens.spacing.space4, vertical = tokens.spacing.space3),
                ) {
                    bottomBar()
                }
            }
        }
    }
}

/**
 * 控件相对标题的位置。
 *
 * 分段器、下拉框和输入框都有自己的最小可读宽度。把它们硬塞在标题右侧时，窄卡里的中文
 * 会被压成一列单字（两张实窗截图）。这类控件改用 [Below]：标题先占满整行，
 * 控件在下一行展开；开关和跳转箭头仍用 [Inline]，不白白拉高每一行。
 */
internal enum class SettingsControlPlacement {
    Inline,
    Below,
}

/** 一张设置卡：标题、说明与若干行。 */
internal class SettingsCardSpec(
    val title: String,
    val caption: String?,
    val rows: List<SettingsRowSpec>,
)

/**
 * 设置 / 表单页的公共页。
 *
 * [navigation] 非空时走主从双栏（设置中枢 / 源管理）；为空时走普通页壳（源登录 / 备份）。
 * 卡片网格、标题栏、搜索动作、空态都由模板收口，页面只交导航和卡片内容。
 */
/** 压平版设置页的一个分区（设置一级化批）。 */
internal data class SettingsFlatSection(
    val title: String,
    /** 分区副题（用分组名：浏览 / 阅读与收藏 / 系统）。 */
    val note: String? = null,
    /** 分区图标（左栏要更大并带图标）。 */
    val icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    val cards: List<SettingsCardSpec>,
)

/**
 * 压平版设置页（设置一级化批）：**左栏锚点 + 单列长页**。
 *
 * 与 `SettingsPageTemplate` 的区别：那个是"左栏切页"，本件左栏只**滚动**不换页——
 * 点一项滚到该分区，滚动时当前分区自动高亮（scroll-spy）。全页没有 chevron、没有返回，
 * 任何一行都不会把用户带到另一页。
 */
@Composable
internal fun SettingsFlatPage(
    mode: WideScreenLayoutMode,
    sections: List<SettingsFlatSection>,
    modifier: Modifier = Modifier,
    railWidth: Dp = 248.dp,
    /** 顶部警示行（写盘失败那类）：非空就摆，不藏。 */
    note: String? = null,
    headerActions: @Composable RowScope.() -> Unit = {},
    emptyContent: @Composable () -> Unit = {},
) {
    val tokens = VeneraTokens
    val palette = LocalChromePalette.current
    val contentScroll = rememberScrollState()
    val offsets = remember { mutableStateMapOf<String, Float>() }
    var currentTitle by remember { mutableStateOf(sections.firstOrNull()?.title) }
    val scope = rememberCoroutineScope()

    // scroll-spy：滚动到哪个分区，左栏就高亮哪个（阈值取视口顶 + 24dp）。
    androidx.compose.runtime.LaunchedEffect(contentScroll.value, sections) {
        val threshold = contentScroll.value + 96f
        val hit = sections.lastOrNull { (offsets[it.title] ?: Float.MAX_VALUE) <= threshold }
        if (hit != null) currentTitle = hit.title
    }

    Column(modifier.fillMaxSize()) {
        // 顶栏：题名 + 右侧（搜索 / 主题档）
        Row(
            modifier = Modifier.fillMaxWidth().padding(
                start = tokens.spacing.space6,
                end = tokens.spacing.space6,
                top = tokens.spacing.space4,
                bottom = tokens.spacing.space3,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space4),
        ) {
            Text(
                text = "设置",
                fontSize = VeneraType.pageTitle.size, lineHeight = VeneraType.pageTitle.lineHeight,
                fontWeight = VeneraType.cardTitle.weight,
                color = palette.ink1,
            )
            Spacer(Modifier.weight(1f))
            headerActions()
        }
        if (!note.isNullOrBlank()) {
            Text(
                text = note,
                fontSize = VeneraType.aux.size,
                lineHeight = VeneraType.aux.lineHeight,
                color = VeneraTokens.color.error,
                modifier = Modifier.padding(
                    start = VeneraTokens.spacing.space6,
                    end = VeneraTokens.spacing.space6,
                    bottom = VeneraTokens.spacing.space2,
                ),
            )
        }

        Row(Modifier.weight(1f).fillMaxWidth()) {
            // ── 左栏：锚点（不是导航）──
            Column(
                modifier = Modifier
                    .width(railWidth)
                    .fillMaxHeight()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                sections.groupBy { it.note ?: "" }.forEach { (group, items) ->
                    if (group.isNotBlank()) {
                        Text(
                            text = group,
                            fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                            fontWeight = VeneraType.cardTitle.weight,
                            color = palette.ink3,
                            letterSpacing = 0.4.sp,
                            modifier = Modifier.padding(start = 10.dp, top = 14.dp, bottom = 6.dp),
                        )
                    }
                    items.forEach { sec ->
                        val on = sec.title == currentTitle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(if (on) palette.accent.copy(alpha = 0.14f) else Color.Transparent)
                                .clickable {
                                    val target = offsets[sec.title]
                                    if (target != null) {
                                        scope.launch { contentScroll.animateScrollTo(target.toInt()) }
                                    }
                                }
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            // 图标块 22dp（选中 = accent 底白图标；常态 = 轨道底 + 次级图标）
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(if (on) palette.accent else palette.segmentTrack),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (sec.icon != null) {
                                    androidx.compose.material3.Icon(
                                        imageVector = sec.icon,
                                        contentDescription = null,
                                        tint = if (on) Color.White else palette.ink2,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                            Text(
                                text = sec.title,
                                fontSize = VeneraType.bodySecondary.size, lineHeight = VeneraType.bodySecondary.lineHeight,
                                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (on) palette.accent else palette.ink1,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            // ── 内容：单列长页 ──
            if (sections.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    emptyContent()
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(contentScroll)
                        .padding(start = 4.dp, end = tokens.spacing.space6, bottom = 40.dp),
                ) {
                    sections.forEachIndexed { index, sec ->
                        Column(
                            modifier = Modifier.onGloballyPositioned {
                                // 记录分区顶（相对滚动容器内容坐标）。
                                offsets[sec.title] = it.positionInParent().y
                            },
                        ) {
                            Spacer(Modifier.height(if (index == 0) tokens.spacing.space2 else 34.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = sec.title,
                                    fontSize = VeneraType.cardTitle.size, lineHeight = VeneraType.cardTitle.lineHeight,
                                    fontWeight = VeneraType.cardTitle.weight,
                                    color = palette.ink1,
                                )
                                if (!sec.note.isNullOrBlank()) {
                                    Spacer(Modifier.width(8.dp))
                                    Text(sec.note, fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight, color = palette.ink3)
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                sec.cards.forEach { card -> SettingsCardTemplate(card) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SettingsPageTemplate(
    mode: WideScreenLayoutMode,
    title: String,
    cards: List<SettingsCardSpec>,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    note: String? = null,
    onBack: (() -> Unit)? = null,
    navigationWidth: Dp = 268.dp,
    navigation: (@Composable ColumnScope.() -> Unit)? = null,
    headerActions: @Composable RowScope.() -> Unit = {},
    cardColumns: Int = 2,
    contentPadding: PaddingValues = PaddingValues(
        start = VeneraTokens.spacing.space6,
        end = VeneraTokens.spacing.space6,
        bottom = VeneraTokens.spacing.space6,
    ),
    emptyContent: @Composable () -> Unit = {},
) {
    if (navigation == null) {
        DesktopPageShell(
            mode = mode,
            title = title,
            subtitle = subtitle,
            note = note,
            onBack = onBack,
            actions = headerActions,
            modifier = modifier,
        ) {
            SettingsCardsGrid(
                cards = cards,
                columns = cardColumns,
                contentPadding = contentPadding,
                emptyContent = emptyContent,
            )
        }
        return
    }

    val tokens = VeneraTokens
    Row(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .width(navigationWidth)
                .fillMaxHeight(),
        ) {
            navigation()
        }
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = tokens.spacing.space6,
                        end = tokens.spacing.space6,
                        top = tokens.spacing.space4,
                        bottom = tokens.spacing.space3,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space3),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (subtitle != null) {
                        Text(
                            text = subtitle.uppercase(),
                            fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                            fontWeight = VeneraType.cardTitle.weight,
                            color = tokens.color.primary,
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
                    ) {
                        Text(
                            text = title,
                            fontSize = VeneraType.pageTitle.size, lineHeight = VeneraType.pageTitle.lineHeight,
                            fontWeight = VeneraType.cardTitle.weight,
                            color = tokens.color.textPrimary,
                        )
                        Text(
                            text = "${cards.sumOf { it.rows.count { row -> row.counted } }} 项",
                            fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                            color = tokens.color.textSecondary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(tokens.color.surfaceContainerHigh.copy(alpha = 0.72f))
                                .padding(
                                    horizontal = tokens.spacing.space2,
                                    vertical = tokens.spacing.space1,
                                ),
                        )
                    }
                }
                headerActions()
            }
            if (note != null) {
                Text(
                    text = note,
                    fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                    color = tokens.color.textTertiary,
                    modifier = Modifier.padding(
                        start = tokens.spacing.space6,
                        end = tokens.spacing.space6,
                        bottom = tokens.spacing.space2,
                    ),
                )
            }
            SettingsCardsGrid(
                cards = cards,
                columns = cardColumns,
                contentPadding = contentPadding,
                emptyContent = emptyContent,
            )
        }
    }
}

@Composable
private fun SettingsCardsGrid(
    cards: List<SettingsCardSpec>,
    columns: Int,
    contentPadding: PaddingValues,
    emptyContent: @Composable () -> Unit,
) {
    if (cards.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize()) {
            emptyContent()
        }
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val effectiveColumns = if (maxWidth < 760.dp) 1 else columns.coerceAtLeast(1)
        LazyVerticalGrid(
            columns = GridCells.Fixed(effectiveColumns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(VeneraTokens.spacing.space3),
            verticalArrangement = Arrangement.spacedBy(VeneraTokens.spacing.space3),
        ) {
            gridItems(items = cards, key = { it.title }) { card ->
                SettingsCardTemplate(card = card)
            }
        }
    }
}

/** 一张设置卡：设置中枢与源管理详情共用同一层卡壳。 */
@Composable
internal fun SettingsCardTemplate(
    title: String,
    caption: String?,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = VeneraTokens
    val shape = RoundedCornerShape(tokens.shape.medium)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(tokens.color.surface.copy(alpha = 0.82f))
            .border(
                width = 1.dp,
                color = tokens.color.outlineVariant.copy(alpha = 0.34f),
                shape = shape,
            ),
    ) {
        Column {
            Column(
                modifier = Modifier.padding(
                    start = tokens.spacing.space5,
                    end = tokens.spacing.space5,
                    top = tokens.spacing.space4,
                    bottom = tokens.spacing.space1,
                ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
                ) {
                    // 去掉标题左侧那颗渐变强调方块（图4），标题直接顶格。
                    Text(
                        text = title,
                        fontSize = VeneraType.sectionTitle.size, lineHeight = VeneraType.sectionTitle.lineHeight,
                        fontWeight = VeneraType.cardTitle.weight,
                        color = tokens.color.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (caption != null) {
                    Spacer(Modifier.height(tokens.spacing.space1))
                    Text(
                        text = caption,
                        fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                        color = tokens.color.textTertiary,
                    )
                }
                Spacer(Modifier.height(tokens.spacing.space2))
                content()
            }
        }
    }
}

@Composable
private fun SettingsCardTemplate(card: SettingsCardSpec) {
    SettingsCardTemplate(title = card.title, caption = card.caption) {
        card.rows.forEachIndexed { index, row ->
            if (index > 0) {
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(VeneraTokens.color.textTertiary.copy(alpha = 0.10f)),
                )
            }
            SettingsRowTemplate(row = row)
        }
    }
}

/** 设置行：整行可点时也保持和控件同一套布局，不再让每页各摆一遍。 */
@Composable
internal fun SettingsRowTemplate(
    title: String,
    caption: String?,
    onClick: (() -> Unit)? = null,
    control: (@Composable () -> Unit)? = null,
    controlPlacement: SettingsControlPlacement = SettingsControlPlacement.Inline,
) {
    val tokens = VeneraTokens
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
            )
            .padding(vertical = tokens.spacing.space2),
    ) {
        val stackControl = control != null &&
            (controlPlacement == SettingsControlPlacement.Below || maxWidth < 360.dp)
        if (stackControl) {
            Column(modifier = Modifier.fillMaxWidth()) {
                SettingsRowLabel(title = title, caption = caption)
                Spacer(Modifier.height(tokens.spacing.space2))
                Box(modifier = Modifier.fillMaxWidth()) {
                    control()
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space4),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    SettingsRowLabel(title = title, caption = caption)
                }
                control?.invoke()
            }
        }
    }
}

@Composable
private fun SettingsRowLabel(title: String, caption: String?) {
    val tokens = VeneraTokens
    Text(
        text = title,
        fontSize = VeneraType.body.size, lineHeight = VeneraType.body.lineHeight,
        color = tokens.color.textPrimary,
    )
    if (caption != null) {
        Spacer(Modifier.height(tokens.spacing.space1))
        Text(
            text = caption,
            fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
            color = tokens.color.textTertiary,
        )
    }
}

@Composable
private fun SettingsRowTemplate(row: SettingsRowSpec) {
    SettingsRowTemplate(
        title = row.title,
        caption = row.caption,
        onClick = row.onClick,
        control = row.control,
        controlPlacement = row.controlPlacement,
    )
}
