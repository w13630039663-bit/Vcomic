package com.venera.compose.feature

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.components.ComicCardLayout
import com.venera.compose.components.ComicLayoutToggleButton
import com.venera.compose.components.ComicSharedTransition
import com.venera.compose.components.LocalUxPlatformServices
import com.venera.compose.components.VeneraEmptyView
import com.venera.compose.components.comicListColumnCount
import com.venera.compose.components.coverSharedElement
import com.venera.compose.components.rememberComicListDisplayMode
import com.venera.compose.components.rememberContentWidth
import com.venera.compose.components.venera.VeneraCard
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.components.venera.VeneraCoverMask
import com.venera.compose.components.venera.VeneraDialog
import com.venera.compose.components.venera.VeneraTextButton
import com.venera.compose.components.venera.VeneraTopAppBar
import com.venera.compose.components.venera.VeneraTopBarPill
import com.venera.compose.components.venera.blurBackdropSource
import com.venera.compose.components.venera.rememberTopBarBackdrop
import com.venera.compose.components.venera.rememberVeneraTopAppBarBehavior
import com.venera.compose.data.db.HistoryRecord
import com.venera.compose.ui.tokens.LocalBottomBarClearance
import com.venera.compose.ui.tokens.StatusColors
import com.venera.compose.ui.tokens.VeneraCardEnter
import com.venera.compose.ui.tokens.VeneraTokens
import top.yukonga.miuix.kmp.basic.Text

/**
 * 历史页的 **屏本体（平台无关）**（阶段二 · 阅读历史收敛复用）。
 *
 * 形状照详情页那颗 [ComicDetailScreenBody]：屏只吃一颗 [HistoryCore] + 几个回调，
 * 不碰 ViewModel / Android Context / 预测返回手势。安卓侧的装配薄壳是 [AndroidHistoryScreen]
 * （`viewModel()` 那颗留在它里面，本文件因此上得了桌面编译面）；桌面对称的装配在
 * `VeneraDesktopLargeScreen` 的 `DesktopRoute.History` 支路里直接喂 body。
 *
 * 平台差异的两处出口都走既有形状，不另起平行线：
 *  - 封面打码 → `LocalUxPlatformServices.coverMaskStateOf`（与 `HomeScreen` 首页那两排卡
 *    同一条判据，安卓实现转发 `ContentGuardManager`、桌面实现走 `DesktopUxPlatformServices`）；
 *  - 共享元素飞行 → `coverSharedElement` 自己读 `LocalCoverTransitionScopes`，安卓有 `CoverTransitionHost`
 *    就飞、桌面壳没有列表侧 transition 域时退成普通封面（降级读数，不是假动画）。
 *
 * 对齐原版 `pages/history_page.dart`：网格 + 多选删除 + 清空（全部 / 仅未收藏）。
 * 清空确认从前挂在 `PredictiveBackOverlay`（那颗文件整颗不进桌面编译面）；这里换成两端共有的
 * [VeneraDialog]（三枚把手：清空未收藏 / 全部清空 / 取消），语义一字未改。
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HistoryScreenBody(
    core: HistoryCore,
    /** 按下某一行且非多选态时回调：交回那条历史记录本身，由各自的装配层决定怎么导航。 */
    onOpenRecord: (HistoryRecord) -> Unit,
    /** 二级页返回。多选态下顶栏那枚返回的动作是「退出多选」，与系统返回同语义。 */
    onBack: () -> Unit,
    /**
     * 某一行的「开不开得动」判据（退役仿制屏时迁入的插槽，`HistoryScreenBody` 收敛复用的最后一格）。
     *
     * 缺省恒开 ⇒ 安卓逐字不变（安卓每颗源都能装载，行行点得动）。桌面一次只装载**一颗**源
     * （`config.sourceKey`），库里别源那几行按下去拿 A 站凭据问 B 站那一本取不到键 —— 恒开就成了
     * 「点了没反应」的**假死**（本仓记过的「灰行不许留在屏上」那一族）。桌面壳据
     * `desktopComicHistoryRowGate` 递这一枚：开不动的行不给导航，并把「为什么开不动」那句摆出来。
     */
    rowGate: (HistoryRecord) -> Boolean = { true },
    /** 那一行开不动时上屏念的那句「为什么」（只在 [rowGate] 为假且非多选态时问）。缺省 null = 不摆那一行。 */
    rowNote: (HistoryRecord) -> String? = { null },
) {
    val tokens = VeneraTokens
    val records by core.history.collectAsState()
    val displayMode = rememberComicListDisplayMode()
    var showClearMenu by remember { mutableStateOf(false) }
    val ux = LocalUxPlatformServices.current
    // 大标题折叠 + 真实毛玻璃顶栏（页内自治）
    val topBarBehavior = rememberVeneraTopAppBarBehavior()
    val topBarBackdrop = rememberTopBarBackdrop()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // 列数按网格实测可用宽推（宽屏加列），左右内边距要从可用宽里扣掉
    val (gridWidth, gridWidthModifier) =
        rememberContentWidth(tokens.spacing.rowHorizontal * 2)

    Box(modifier = Modifier.fillMaxSize()) {
        if (records.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = statusBarTop + VeneraTokens.spacing.topBarFloor),
                contentAlignment = Alignment.Center,
            ) {
                VeneraEmptyView(
                    title = "还没有阅读记录",
                    message = "去随便翻两页吧",
                    icon = Icons.Outlined.History,
                )
            }
        } else {
            val cardLayout by com.venera.desktop.settings.DesktopComicCardLayout.config.collectAsState()
            val historyColumns = when {
                cardLayout.isCustom -> cardLayout.cols
                else -> comicListColumnCount(displayMode.value, gridWidth)
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(historyColumns),
                contentPadding = PaddingValues(
                    start = tokens.spacing.rowHorizontal,
                    end = tokens.spacing.rowHorizontal,
                    top = statusBarTop + VeneraTokens.spacing.topBarFloor + if (core.multiSelectMode) 44.dp else 0.dp,
                    bottom = LocalBottomBarClearance.current,
                ),
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.gridGap),
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.gridGap),
                // 挂载折叠与录制行为：下滑时大标题收起、真实高斯模糊背板淡入
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(topBarBehavior.nestedScrollConnection)
                    .blurBackdropSource(topBarBackdrop),
            ) {
                itemsIndexed(
                    records,
                    key = { _, record -> "${record.comicId}-${record.sourceName}" },
                ) { index, record ->
                    VeneraCardEnter(index, "${record.comicId}-${record.sourceName}") {
                        HistoryCard(
                            record = record,
                            detailed = displayMode.value == "detailed",
                            selected = (record.comicId to record.sourceName) in core.selected,
                            multiSelectMode = core.multiSelectMode,
                            masked = ux.coverMaskStateOf(
                                sourceKey = record.sourceName,
                                title = record.title,
                                author = record.author,
                                comicId = record.comicId,
                            ) == "VISIBLE",
                            openable = rowGate(record),
                            // 多选态下开不开得动不相关（长按选中删的是库里那一行本身，跨源也删得动），
                            // 所以那句「为什么打不开」只在非多选态念，免得选中删的时候多冒一句无关话。
                            notOpenableNote = if (!core.multiSelectMode) rowNote(record) else null,
                            onClick = {
                                if (core.multiSelectMode) {
                                    core.toggleSelect(record)
                                } else {
                                    // 只在开得动时才导航（桌面别源那几行按下去不假装导航 —— 缺省 rowGate=true，安卓不变）。
                                    if (rowGate(record)) onOpenRecord(record)
                                }
                            },
                            onLongClick = { core.enterMultiSelect(record) },
                        )
                    }
                }
            }
        }

        // ── 统一顶栏：置于前景层，毛玻璃对底层 LazyVerticalGrid 进行物理级模糊 ──
        VeneraTopAppBar(
            title = "历史",
            largeTitle = "历史",
            scrollBehavior = topBarBehavior,
            backdrop = topBarBackdrop,
            navigationIcon = {
                VeneraTopBarPill(
                    onClick = { if (core.multiSelectMode) core.exitMultiSelect() else onBack() }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = if (core.multiSelectMode) "退出多选" else "返回",
                        tint = tokens.color.textPrimary,
                    )
                }
            },
            actions = {
                if (core.multiSelectMode) {
                    TopBarAction(text = "全选", onClick = { core.selectAll(records) })
                    TopBarAction(
                        text = "删除",
                        tint = StatusColors.Failing,
                        onClick = { core.deleteSelected() },
                    )
                } else {
                    VeneraTopBarPill(onClick = { showClearMenu = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "清空选项",
                            tint = tokens.color.textSecondary,
                        )
                    }
                    ComicLayoutToggleButton(displayMode.value) { displayMode.value = it }
                }
            },
            bottomContent = {
                // 多选提示条放在 bottomContent 中，跟随 TopAppBar 一同悬浮折叠
                if (core.multiSelectMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = tokens.spacing.rowHorizontal, vertical = tokens.spacing.space2),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "退出多选",
                            tint = tokens.color.textPrimary,
                            modifier = Modifier
                                .size(tokens.spacing.iconButtonSize)
                                .clickable { core.exitMultiSelect() }
                                .padding(tokens.spacing.space2),
                        )
                        Spacer(modifier = Modifier.width(tokens.spacing.space2))
                        Text(
                            text = "已选择 ${core.selected.size} 项",
                            fontSize = tokens.type.body,
                            fontWeight = tokens.type.weightMedium,
                            color = tokens.color.textPrimary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            },
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }

    if (showClearMenu) {
        // 三档动作：清空未收藏 / 全部清空 / 取消。两枚 destructive 按钮里「全部清空」走 confirm、
        // 「清空未收藏」走 content 槽里的第三枚把手，「取消」走 dismiss——语义与原 ClearHistoryMenu 一致。
        VeneraDialog(
            show = true,
            onDismissRequest = { showClearMenu = false },
            title = "清空历史记录",
            confirmText = "全部清空",
            confirmDestructive = true,
            onConfirm = { core.clearAll(); showClearMenu = false },
            dismissText = "取消",
            onDismiss = { showClearMenu = false },
            content = {
                Text(
                    text = "「清空未收藏」会保留仍在你收藏夹里的漫画的阅读进度。",
                    fontSize = tokens.type.caption,
                )
                Spacer(modifier = Modifier.height(tokens.spacing.space3))
                VeneraTextButton(
                    text = "清空未收藏",
                    onClick = { core.clearUnfavorited(); showClearMenu = false },
                )
            },
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun HistoryCard(
    record: HistoryRecord,
    detailed: Boolean,
    selected: Boolean,
    multiSelectMode: Boolean,
    /** 封面是否可见（打码判据由壳层经 `coverMaskStateOf` 算好交进来，屏里不自判）。 */
    masked: Boolean,
    /**
     * 这一行开不开得动（缺省恒开，见屏本体上 `rowGate` 那颗插槽）。开不动时题名下面多摆一句
     * `notOpenableNote`（"为什么按不动"与"按不动"是两件事 —— 只留前者看着像坏了）。
     */
    openable: Boolean = true,
    /** 开不动那句（null / 空串 = 不摆那一行）。 */
    notOpenableNote: String? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val tokens = VeneraTokens
    VeneraCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box {
            ComicCardLayout(
                detailed = detailed,
                modifier = Modifier.padding(tokens.spacing.space3),
                cover = {
                    VeneraCover(
                        url = record.coverUrl,
                        contentDescription = record.title,
                        shimmerWhileLoading = false,
                        mask = if (masked) VeneraCoverMask.Visible else VeneraCoverMask.Masked,
                        modifier = Modifier
                            // 与详情页封面配对的共享元素：key 用 record.sourceName —— 交给
                            // 详情的 HistoryRecord.toComicItem() 填的正是同一个串，两端必然一致。
                            // 打码命中不飞（飞行内容渲染进 overlay，等于绕开页面裁剪）。
                            .coverSharedElement(
                                key = ComicSharedTransition.coverKey(record.sourceName, record.comicId),
                                allowFly = masked,
                            )
                            .fillMaxWidth()
                            .height(if (detailed) tokens.spacing.historyCoverHeight else tokens.spacing.historyCoverHeight + tokens.spacing.space9),
                    )
                },
                content = {
                    Column(
                        modifier = Modifier.padding(
                            top = if (detailed) tokens.spacing.none else tokens.spacing.space1,
                            end = if (detailed && multiSelectMode) tokens.spacing.badgeSize else tokens.spacing.none,
                        )
                    ) {
                        // 固定 38dp 双行槽，文字 SemiBold 呼吸感行高
                        Box(
                            modifier = if (detailed) Modifier else Modifier.fillMaxWidth().height(38.dp),
                            contentAlignment = Alignment.TopStart,
                        ) {
                            Text(
                                text = record.title,
                                fontSize = if (detailed) tokens.type.body else 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = tokens.color.textPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 19.sp,
                            )
                        }
                        Spacer(modifier = Modifier.height(tokens.spacing.space1))
                        Text(
                            text = record.progressDescription(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = tokens.color.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        // 源名采用 Mini Badge 药丸底座，彻底告别文字紧缩贴底与发虚错位
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(tokens.color.surfaceVariant.copy(alpha = 0.6f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = record.sourceName,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = tokens.color.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        // 开不动那句（只在非多选态且装配层给了那句时才摆）：一枚按不动的卡与一句
                        // "为什么按不动"是两件事，只留前者看着像坏了（同首页/详情"没有写口就没有那枚钮"那族口径）。
                        if (!openable && !notOpenableNote.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(tokens.spacing.space1))
                            Text(
                                text = notOpenableNote,
                                fontSize = tokens.type.overline,
                                color = tokens.color.textTertiary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
            )
            if (multiSelectMode) {
                Icon(
                    imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (selected) tokens.color.primary else if (detailed) tokens.color.textPrimary else Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(tokens.spacing.space2)
                        .size(tokens.spacing.badgeSize),
                )
            }
        }
    }
}

/** 顶栏内的文字操作按钮（多选态「全选 / 删除」用）。 */
@Composable
private fun TopBarAction(
    text: String,
    onClick: () -> Unit,
    tint: Color? = null,
) {
    val tokens = VeneraTokens
    Text(
        text = text,
        fontSize = tokens.type.body,
        fontWeight = tokens.type.weightMedium,
        color = tint ?: tokens.color.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(tokens.shape.small))
            .clickable(onClick = onClick)
            .padding(horizontal = tokens.spacing.space4, vertical = tokens.spacing.space2),
    )
}

/**
 * 进度描述，对齐原版 `history_page.dart` 的 `getDescription`：
 * 章节号与页码都 >= 1 才显示，两者都有时用 " - " 连接。
 */
fun HistoryRecord.progressDescription(): String {
    val ep = lastChapterIndex + 1
    val page = lastPageIndex + 1
    return buildString {
        if (ep >= 1) append("第 $ep 话")
        if (page >= 1) {
            if (isNotEmpty()) append(" - ")
            append("第 $page 页")
        }
    }
}
