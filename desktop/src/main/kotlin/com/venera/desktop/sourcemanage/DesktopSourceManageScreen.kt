package com.venera.desktop.sourcemanage

import com.venera.desktop.app.VeneraDesktopLargeScreen
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.components.venera.VeneraSwitch
import com.venera.compose.components.venera.VeneraTextField
import com.venera.compose.ui.tokens.VeneraCardEnter
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.app.LocalShellBackHandled
import com.venera.desktop.gallery.data.DesktopSourceIcons
import com.venera.desktop.ui.DesktopButton
import com.venera.desktop.ui.DesktopButtonSize
import com.venera.desktop.ui.DesktopButtonVariant
import com.venera.desktop.ui.DesktopPopUpButton
import com.venera.desktop.ui.DesktopPushButton
import com.venera.desktop.ui.DesktopSegmented
import com.venera.desktop.ui.SettingsControlPlacement
import com.venera.desktop.ui.SettingsRowTemplate
import com.venera.desktop.ui.theme.VeneraType

/**
 * 桌面站点墙 —— 一页分三层：页头工具、已开启站点卡墙、未启用站点索引带。
 *
 * ## 为什么不是「左清单 + 右详情」
 *
 * 那一形把 34 颗站摊成一张配置表，用户每次都要先读清单再读面板；
 * 而日常动作只有一个：**去某个站看东西**。所以卡片是入口（点卡=进站），
 * 账号 / 站点设置 / 脚本三段收进「⋯」抽屉，平时不上页面。
 *
 * ## 三段职责没有变
 *
 * 启停、登录、设置读写、脚本编辑、撤销改写、导入、检查更新 —— 回调原样接收，
 * 抽屉只是换了容器；屏依旧不碰罐、不解析脚本。
 */
@Composable
internal fun DesktopSourceManageScreen(
    /** 站点清单：key + 显示名 + 是否启用。 */
    rows: List<DesktopSourceRowUi>,
    /** 抽屉当前宿主（null = 抽屉关着）。 */
    selectedKey: String?,
    onSelectedChange: (String?) -> Unit,
    /** 读失败成因。非空照原话上屏。 */
    note: String?,
    onToggleSource: (key: String, enabled: Boolean) -> Unit,
    /**
     * 拖拽重排后整份交回：有序 key 表 + 新的启用集合。
     *
     * 为什么两件事一起交：跨区拖动本身就是「换位置 + 换启停」一次动作，
     * 分成两条回调会让中间那一帧出现"位置变了但还开着"的半截账。
     * `null` = 装配点没接写口，拖拽整条腿不摆（不摆拖不动的假把手）。
     */
    onReorderSources: ((ordered: List<String>, enabled: Set<String>) -> Unit)? = null,
    /** 导入 .js（文件选择 → 先验装载 → 落用户源目录）。null = 不摆。 */
    onImportSource: (() -> Unit)? = null,
    /** 打开在线站点源库弹窗。null = 不摆。 */
    onOpenSourceStore: (() -> Unit)? = null,
    /** 打开独立「站点延迟检测」页面。null = 不摆。 */
    onOpenLatencyMonitor: (() -> Unit)? = null,
    /** 主动触发一次当前源仓库检查与同步更新。null = 不摆。 */
    onCheckSourceUpdate: (() -> Unit)? = null,
    /** 页头下方源仓库切换条状态。null = 不摆（兼容既有测试调用）。 */
    repoBar: DesktopSourceRepoBarUi? = null,
    /** 导入正在进行（添加站点的把手置灰）。 */
    updateBusy: Boolean = false,
    /** 导入/更新的回话。 */
    updateNote: String? = null,
    /** 打开某一颗的编辑屏。null = 装配点没接上。 */
    onEditSource: ((key: String) -> Unit)? = null,
    /** 打开某一颗的手填凭据那一屏。null = 装配点没接上。 */
    onSourceLogin: ((key: String) -> Unit)? = null,
    /** 撤掉某一颗的改写。null 或未标 overridden 时不摆。 */
    onClearSourceOverride: ((key: String) -> Unit)? = null,
    /** 这一颗的账号读数（null = 读口没接）。 */
    account: DesktopSourceAccountUi? = null,
    /** 「退出登录」那枚把手。null = 写口没接。 */
    onSignOutSource: (() -> Unit)? = null,
    /** 站点设置：这一颗的声明项 + 当下值。null = 没有设置块。 */
    settings: List<DesktopSourceSettingUi>? = null,
    onSettingChange: ((settingKey: String, value: String) -> Unit)? = null,
    onSettingCallback: ((settingKey: String) -> Unit)? = null,
    /** callback 跑完/写完那一句回话。 */
    settingsNote: String? = null,
    /** 点卡片 = 去这个站浏览。null = 装配点没接（卡片退化为打开抽屉）。 */
    onOpenSite: ((key: String) -> Unit)? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(Filter.ALL) }
    var addMenuOpen by remember { mutableStateOf(false) }

    val shown = rows.filter { row ->
        when (filter) {
            Filter.ALL -> true
            Filter.ON -> row.enabled
            Filter.OFF -> !row.enabled
        } && (query.isBlank() ||
            row.title.contains(query, ignoreCase = true) ||
            row.key.contains(query, ignoreCase = true))
    }
    val active = shown.filter { it.enabled }
    val idle = shown.filter { !it.enabled }
    val selected = rows.firstOrNull { it.key == selectedKey }

    // ── 拖拽重排 ──
    // 显示顺序 = 已开启（大卡 + 普通卡）在前、未启用（索引带）在后；
    // 拖过那条分界线就是换启停，松手后两边各自的次序也按这次落盘。
    // 三个 ref 是为了让 SiteDragState 只建一次，却总能读到最新一帧的清单与显示序。
    val rowsRef = rememberUpdatedState(rows)
    val visibleRef = rememberUpdatedState(active.map { it.key } + idle.map { it.key })
    val commitRef = rememberUpdatedState(onReorderSources)
    val dragState = remember {
        SiteDragState(
            rowsProvider = { rowsRef.value },
            visibleProvider = { visibleRef.value },
            onCommit = { ordered, enabled -> commitRef.value?.invoke(ordered, enabled) },
        )
    }

    // 抽屉宽按稿降档：≥1100 取 424，窄窗取 390（design-tokens §2 / mockup 的 media 查询）。
    BoxWithConstraints(modifier.fillMaxSize()) {
        val drawerWidth = if (maxWidth < 1100.dp) 390.dp else 424.dp
        Column(modifier = Modifier.fillMaxSize()) {
            WallHeader(
                total = rows.size,
                activeCount = rows.count { it.enabled },
                query = query,
                onQueryChange = { query = it },
                filter = filter,
                onFilterChange = { filter = it },
                addEnabled = !updateBusy,
                onAddClick = { addMenuOpen = !addMenuOpen },
                addMenuOpen = addMenuOpen,
                onDismissAddMenu = { addMenuOpen = false },
                onImportSource = onImportSource,
                onOpenSourceStore = onOpenSourceStore,
                onOpenLatencyMonitor = onOpenLatencyMonitor,
                onCheckSourceUpdate = onCheckSourceUpdate,
                onBack = onBack,
            )
            if (repoBar != null) {
                SourceRepoBar(state = repoBar)
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp)
                    .padding(bottom = 32.dp),
            ) {
                if (updateNote != null) {
                    Text(
                        text = updateNote,
                        fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                        color = tokens.color.textTertiary,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                }
                if (note != null) {
                    Text(
                        text = note,
                        fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                        color = tokens.color.textTertiary,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                }

                if (active.isEmpty() && idle.isEmpty()) {
                    Text(
                        text = if (rows.isEmpty()) "这一版没有可用的站点。" else "没有符合条件的站点。",
                        fontSize = VeneraType.body.size, lineHeight = VeneraType.body.lineHeight,
                        color = tokens.color.textTertiary,
                        modifier = Modifier.padding(vertical = 40.dp),
                    )
                }

                // ── 已开启：前三张大卡 ──
                val heroes = active.take(3)
                if (heroes.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        // 拖动项所在的那一行整体抬到最上面，跨行拖时才不会被后面的行盖住。
                        modifier = Modifier.zIndex(if (dragState.key != null && heroes.any { it.key == dragState.key }) 1f else 0f),
                    ) {
                        heroes.forEachIndexed { index, row ->
                            Box(
                                Modifier
                                    .weight(1f)
                                    .then(
                                        if (onReorderSources != null) {
                                            Modifier.siteDragItem(row.key, row.enabled, dragState)
                                        } else {
                                            Modifier
                                        },
                                    ),
                            ) {
                                VeneraCardEnter(index, row.key) {
                                    SiteHeroCard(
                                        row = row,
                                        onOpen = { openSiteOrDrawer(row.key, onOpenSite, onSelectedChange) },
                                        onManage = { onSelectedChange(row.key) },
                                        onToggle = { onToggleSource(row.key, !row.enabled) },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                        repeat(3 - heroes.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(14.dp))
                }

                // ── 其余已开启 + 补位添加卡 ──
                // 「未启用」档按规格（component-spec §4）只留索引带；全部未启用 / 搜索无结果时卡区也整段空，
                // 补位卡不跟着漏出来（空态口径：卡区一句提示，索引带照旧）。
                val rest = active.drop(3)
                val restWithAdd =
                    if (filter == Filter.OFF || active.isEmpty()) rest
                    else rest + listOf<DesktopSourceRowUi?>(null)
                restWithAdd.chunked(3).forEachIndexed { chunkIdx, chunk ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(if (dragState.key != null && chunk.any { it?.key == dragState.key }) 1f else 0f),
                    ) {
                        chunk.forEachIndexed { i, row ->
                            val enterIndex = 3 + chunkIdx * 3 + i
                            if (row == null) {
                                VeneraCardEnter(enterIndex, "add-site", Modifier.weight(1f)) {
                                    AddSiteCard(
                                        enabled = !updateBusy,
                                        onClick = { addMenuOpen = true },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            } else {
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .then(
                                            if (onReorderSources != null) {
                                                Modifier.siteDragItem(row.key, row.enabled, dragState)
                                            } else {
                                                Modifier
                                            },
                                        ),
                                ) {
                                    VeneraCardEnter(enterIndex, row.key) {
                                        SiteCard(
                                            row = row,
                                            onOpen = { openSiteOrDrawer(row.key, onOpenSite, onSelectedChange) },
                                            onManage = { onSelectedChange(row.key) },
                                            onToggle = { onToggleSource(row.key, !row.enabled) },
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                }
                            }
                        }
                        repeat(3 - chunk.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(14.dp))
                }

                // ── 未启用：细索引带 ──
                if (idle.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "未启用 ${idle.size} 个",
                            fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                            color = tokens.color.textTertiary,
                        )
                        Box(
                            Modifier
                                .weight(1f)
                                .height(1.dp)
                                .background(tokens.color.outlineVariant.copy(alpha = 0.4f)),
                        )
                    }
                    idle.chunked(4).forEachIndexed { chunkIdx, chunk ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .zIndex(if (dragState.key != null && chunk.any { it.key == dragState.key }) 1f else 0f),
                        ) {
                            chunk.forEachIndexed { i, row ->
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .then(
                                            if (onReorderSources != null) {
                                                Modifier.siteDragItem(row.key, row.enabled, dragState)
                                            } else {
                                                Modifier
                                            },
                                        ),
                                ) {
                                    VeneraCardEnter(chunkIdx * 4 + i, row.key) {
                                        IdleIndexItem(
                                            row = row,
                                            onEnable = { onToggleSource(row.key, true) },
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                }
                            }
                            repeat(4 - chunk.size) { Spacer(Modifier.weight(1f)) }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }

        // ── 抽屉 ──
        AnimatedVisibility(
            visible = selected != null,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.zIndex(1f),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.52f))
                    .clickable(onClick = { onSelectedChange(null) }),
            )
        }
        AnimatedVisibility(
            visible = selected != null,
            enter = slideInHorizontally(tween(260)) { it },
            exit = slideOutHorizontally(tween(260)) { it },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .zIndex(2f),
        ) {
            if (selected != null) {
                SiteDrawer(
                    row = selected,
                    width = drawerWidth,
                    account = account,
                    settings = settings,
                    settingsNote = settingsNote,
                    onClose = { onSelectedChange(null) },
                    onToggleSource = onToggleSource,
                    onEditSource = onEditSource,
                    onSourceLogin = onSourceLogin,
                    onClearSourceOverride = onClearSourceOverride,
                    onSignOutSource = onSignOutSource,
                    onSettingChange = onSettingChange,
                    onSettingCallback = onSettingCallback,
                )
            }
        }
    }
}

/** 点卡：有进站出口就进站，没接上就开抽屉（不摆假把手）。 */
private fun openSiteOrDrawer(
    key: String,
    onOpenSite: ((String) -> Unit)?,
    onSelectedChange: (String?) -> Unit,
) {
    if (onOpenSite != null) onOpenSite(key) else onSelectedChange(key)
}

/** 页头：标题 / 计数 / 搜索 / 三档 / 添加站点。 */
@Composable
private fun WallHeader(
    total: Int,
    activeCount: Int,
    query: String,
    onQueryChange: (String) -> Unit,
    filter: Filter,
    onFilterChange: (Filter) -> Unit,
    addEnabled: Boolean,
    onAddClick: () -> Unit,
    addMenuOpen: Boolean,
    onDismissAddMenu: () -> Unit,
    onImportSource: (() -> Unit)?,
    onOpenSourceStore: (() -> Unit)?,
    onOpenLatencyMonitor: (() -> Unit)?,
    onCheckSourceUpdate: (() -> Unit)?,
    onBack: () -> Unit,
) {
    val tokens = VeneraTokens
    Column(modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 22.dp, bottom = 14.dp)) {
        if (!LocalShellBackHandled.current) {
            com.venera.desktop.ui.DesktopBackButton(onClick = onBack)
            Spacer(Modifier.height(8.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "站点",
                    fontSize = VeneraType.pageTitle.size, lineHeight = VeneraType.pageTitle.lineHeight,
                    fontWeight = VeneraType.cardTitle.weight,
                    color = tokens.color.textPrimary,
                )
                Text(
                    text = "$activeCount 个已开启 · ${total - activeCount} 个未启用",
                    fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                    color = tokens.color.textTertiary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SearchField(value = query, onValueChange = onQueryChange)
                DesktopSegmented(
                    options = listOf("全部", "已开启", "未启用"),
                    selectedIndex = filter.ordinal,
                    onSelect = { i -> onFilterChange(Filter.entries[i]) },
                )
                if (onOpenLatencyMonitor != null) {
                    DesktopButton(
                        text = "延迟检测",
                        onClick = onOpenLatencyMonitor,
                        size = DesktopButtonSize.Medium,
                        variant = DesktopButtonVariant.Secondary,
                    )
                }
                Box {
                    AccentButton(
                        label = "添加站点",
                        onClick = onAddClick,
                        enabled = addEnabled,
                        leadingIcon = Icons.Outlined.Add,
                    )
                    androidx.compose.material3.DropdownMenu(
                        expanded = addMenuOpen,
                        onDismissRequest = onDismissAddMenu,
                    ) {
                        if (onOpenSourceStore != null) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("浏览源库", fontSize = VeneraType.body.size) },
                                onClick = { onDismissAddMenu(); onOpenSourceStore() },
                            )
                        }
                        if (onImportSource != null) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("导入站点脚本", fontSize = VeneraType.body.size) },
                                onClick = { onDismissAddMenu(); onImportSource() },
                            )
                        }
                        if (onCheckSourceUpdate != null) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("检查同步更新", fontSize = VeneraType.body.size) },
                                onClick = { onDismissAddMenu(); onCheckSourceUpdate() },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 页头搜索：与画廊顶栏同一颗（34dp / r8 / 描边分档）。 */
@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    com.venera.desktop.ui.components.DesktopSearchField(
        value = value,
        onValueChange = onValueChange,
        onSearch = {},
        modifier = Modifier.semantics { contentDescription = "站点搜索框" },
        collapsedWidth = 172.dp,
        expandedWidth = 240.dp,
        placeholder = "搜站点",
    )
}

/** 稿里的主按钮（accent 实底 / r9 / 白字）：`DesktopPushButton` 是 26 高灰底，这一页的两枚都得走主色档。 */
@Composable
private fun AccentButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 32.dp,
    leadingIcon: ImageVector? = null,
) {
    val size = when {
        height >= 38.dp -> com.venera.desktop.ui.DesktopButtonSize.Large
        height <= 28.dp -> com.venera.desktop.ui.DesktopButtonSize.Compact
        else -> com.venera.desktop.ui.DesktopButtonSize.Medium
    }
    com.venera.desktop.ui.DesktopButton(
        text = label,
        onClick = onClick,
        modifier = modifier,
        size = size,
        variant = com.venera.desktop.ui.DesktopButtonVariant.Primary,
        leadingIcon = leadingIcon,
        enabled = enabled,
    )
}

/** 大卡：已开启的前三站，海报式竖向排布。 */
@Composable
private fun SiteHeroCard(
    row: DesktopSourceRowUi,
    onOpen: () -> Unit,
    onManage: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val lift by animateFloatAsState(if (hovered) -1f else 0f, tween(160), label = "hero-card-lift")
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .graphicsLayer { translationY = lift.dp.toPx() }
            .height(206.dp)
            .background(
                if (hovered) tokens.color.surfaceContainerHigh.copy(alpha = 0.60f)
                else tokens.color.surfaceContainerHigh.copy(alpha = 0.45f),
                shape,
            )
            .border(
                1.dp,
                if (hovered) tokens.color.primary.copy(alpha = 0.38f)
                else tokens.color.outlineVariant.copy(alpha = 0.25f),
                shape,
            )
            // clip 放在描边之后：卡身内容 / 点击指示收进圆角，又不把 1dp 描边裁掉一半。
            .clip(shape)
            .hoverable(interaction)
            .clickable(onClick = onOpen)
            .semantics { contentDescription = "站点 ${row.key}" }
            .padding(20.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            SiteIconSlot(key = row.key, title = row.title, size = 52.dp, innerSize = 38.dp, radius = 14.dp)
            Spacer(Modifier.height(13.dp))
            Text(
                text = row.title,
                fontSize = 27.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.SemiBold,
                color = tokens.color.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(7.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (row.loggedIn) StatusBadge("已登录", positive = true)
                if (requiresProxy(row.key)) StatusBadge("需代理")
                if (row.originLabel != "内置站点") StatusBadge(row.originLabel)
                Text(
                    // 稿 §2.3：副行念「来源 · N 项设置」；N 来自源脚本 settings 块的声明项数。
                    text = (if (row.originLabel == "内置站点") "内置站点" else "已装载") +
                        if (row.settingsCount > 0) " · ${row.settingsCount} 项设置" else "",
                    fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                    color = tokens.color.textTertiary,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "浏览 →",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                color = if (hovered) tokens.color.primary else tokens.color.textTertiary,
            )
        }
        Row(
            modifier = Modifier.align(Alignment.TopEnd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            com.venera.desktop.ui.DesktopSwitch(
                checked = row.enabled,
                onCheckedChange = { onToggle() },
                enabled = row.canDisable,
                modifier = Modifier.semantics { contentDescription = "站点开关 ${row.key}" },
            )
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .graphicsLayer { alpha = if (hovered) 1f else 0.5f }
                    .clickable(onClick = onManage)
                    .semantics { contentDescription = "管理 ${row.key}" },
                contentAlignment = Alignment.Center,
            ) {
                Text("⋯", fontSize = 15.sp, color = tokens.color.textTertiary)
            }
        }
    }
}

/** 普通卡：其余已开启站点。 */
@Composable
private fun SiteCard(
    row: DesktopSourceRowUi,
    onOpen: () -> Unit,
    onManage: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val lift by animateFloatAsState(if (hovered) -1f else 0f, tween(160), label = "site-card-lift")
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .graphicsLayer { translationY = lift.dp.toPx() }
            .height(96.dp)
            .background(
                if (hovered) tokens.color.surfaceContainerHigh.copy(alpha = 0.50f)
                else tokens.color.surfaceContainerHigh.copy(alpha = 0.35f),
                shape,
            )
            .border(
                1.dp,
                if (hovered) tokens.color.primary.copy(alpha = 0.38f)
                else tokens.color.outlineVariant.copy(alpha = 0.22f),
                shape,
            )
            .clip(shape)
            .hoverable(interaction)
            .clickable(onClick = onOpen)
            .semantics { contentDescription = "站点 ${row.key}" }
            .padding(horizontal = 15.dp, vertical = 13.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SiteIconSlot(key = row.key, title = row.title, size = 34.dp, innerSize = 26.dp, radius = 10.dp)
                Column(Modifier.weight(1f)) {
                    Text(
                        text = row.title,
                        fontSize = VeneraType.body.size, lineHeight = VeneraType.body.lineHeight,
                        fontWeight = FontWeight.SemiBold,
                        color = tokens.color.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        if (row.loggedIn) StatusBadge("已登录", positive = true)
                        if (requiresProxy(row.key)) StatusBadge("需代理")
                        if (row.originLabel != "内置站点") {
                            StatusBadge(row.originLabel)
                        } else {
                            Text(
                                text = "内置站点",
                                fontSize = 11.sp, lineHeight = 15.sp,
                                color = tokens.color.textTertiary,
                            )
                        }
                    }
                }
                com.venera.desktop.ui.DesktopSwitch(
                    checked = row.enabled,
                    onCheckedChange = { onToggle() },
                    enabled = row.canDisable,
                    modifier = Modifier.semantics { contentDescription = "站点开关 ${row.key}" },
                )
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .graphicsLayer { alpha = if (hovered) 1f else 0.5f }
                        .clickable(onClick = onManage)
                        .semantics { contentDescription = "管理 ${row.key}" },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("⋯", fontSize = 14.sp, color = tokens.color.textTertiary)
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "浏览 →",
                fontSize = 11.5.sp,
                color = if (hovered) tokens.color.primary else tokens.color.textTertiary,
            )
        }
    }
}

/** 补位卡：虚线 + 居中「添加站点」。 */
@Composable
private fun AddSiteCard(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .height(96.dp)
            .background(
                if (hovered && enabled) tokens.color.primary.copy(alpha = 0.04f) else Color.Transparent,
                shape,
            )
            // 稿是 1px 虚线：Compose 的 border 只有实线，这里自绘 dash。
            .dashedBorder(
                color = if (hovered && enabled) tokens.color.primary.copy(alpha = 0.55f)
                else tokens.color.outlineVariant.copy(alpha = 0.35f),
                radius = 16.dp,
            )
            .clip(shape)
            .hoverable(interaction, enabled = enabled)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = "添加站点" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "＋ 添加站点",
            fontSize = 13.sp,
            color = if (hovered && enabled) tokens.color.primary else tokens.color.textTertiary,
        )
    }
}

/** 索引带一条：左站名，右「启用」；悬停时描边与两头同时转 accent / 主字。 */
@Composable
private fun IdleIndexItem(
    row: DesktopSourceRowUi,
    onEnable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        modifier = modifier
            .height(44.dp)
            .dashedBorder(
                color = if (hovered) tokens.color.primary.copy(alpha = 0.55f)
                else tokens.color.outlineVariant.copy(alpha = 0.28f),
                radius = 10.dp,
            )
            .hoverable(interaction)
            .clickable(onClick = onEnable)
            .semantics { contentDescription = "启用 ${row.key}" }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = row.title,
            fontSize = 12.5.sp,
            color = if (hovered) tokens.color.textPrimary else tokens.color.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            "启用",
            fontSize = 11.sp,
            color = if (hovered) tokens.color.primary else tokens.color.textTertiary,
        )
    }
}

/**
 * 1dp 虚线圆角描边：`Modifier.border` 只画实线，稿的补位卡 / 索引条是 dashed。
 * 圆角取单一半径（本页两处都是 `RoundedCornerShape(dp)`），不处理百分比角。
 */
private fun Modifier.dashedBorder(
    color: Color,
    radius: Dp,
    width: Dp = 1.dp,
    dash: Dp = 4.dp,
    gap: Dp = 4.dp,
): Modifier = drawBehind {
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(
            width = width.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash.toPx(), gap.toPx())),
        ),
    )
}

/** 图标槽：有站方图标用真图，没有就写显示名首字。 */
@Composable
internal fun SiteIconSlot(
    key: String,
    title: String,
    size: androidx.compose.ui.unit.Dp,
    innerSize: androidx.compose.ui.unit.Dp,
    radius: androidx.compose.ui.unit.Dp,
) {
    val tokens = VeneraTokens
    val bitmap = remember(key) { loadSiteIcon(key) }
    Box(
        modifier = Modifier
            .size(size)
            .background(tokens.color.surfaceContainerHigh.copy(alpha = 0.6f), RoundedCornerShape(radius)),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.size(innerSize),
            )
        } else {
            Text(
                text = monogramOf(title),
                fontSize = if (size > 40.dp) 20.sp else 13.sp,
                fontWeight = FontWeight.Bold,
                color = tokens.color.textSecondary,
            )
        }
    }
}

/**
 * 显示名首字：英文名取前两个字母（Kavita→Ka、Komga→Ko 才分得开），中文取第一个字。
 * 与「不编站名」同一条纪律 —— 没有图标就是没有，不伪造一个。
 */
private fun monogramOf(title: String): String {
    if (title.isEmpty()) return "?"
    val first = title[0]
    return if (first.isLetter() && first.code < 128) title.take(2) else first.toString()
}

/** 站方图标解码；无图标或解码失败 = null（调用点退首字块）。 */
private fun loadSiteIcon(key: String): ImageBitmap? =
    DesktopSourceIcons.bytesOf(key)?.let { bytes ->
        runCatching {
            org.jetbrains.skia.Image.makeFromEncoded(bytes).use { it.toComposeImageBitmap() }
        }.getOrNull()
    }

/**
 * 状态徽章：`positive` = 「已登录」那枚（稿的绿、底 14%）；
 * 其余（未登录 / 已修改 / 内置站点）走中性 chip —— 稿里 `.badge.muted` 那一档。
 *
 * 绿不在 `VeneraTokens` 的语义槽里，取 gallery 层的成功色 `GalleryTokens.Color.StatusSuccess`
 * （深浅两套都有：`GalleryTokens.kt:89/126`），不另立色值字面量。
 */
@Composable
private fun StatusBadge(text: String, positive: Boolean = false) {
    val tokens = VeneraTokens
    val foreground = if (positive) {
        com.venera.desktop.gallery.ui.GalleryTokens.Color.StatusSuccess
    } else {
        tokens.color.textTertiary
    }
    Box(
        modifier = Modifier
            .background(
                if (positive) foreground.copy(alpha = 0.14f)
                else tokens.color.surfaceContainerHigh.copy(alpha = 0.6f),
                RoundedCornerShape(999.dp),
            )
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(text = text, fontSize = 10.sp, lineHeight = 12.sp, color = foreground)
    }
}

/** 右侧抽屉：账号 / 站点设置 / 脚本三段，加一枚启停开关。 */
@Composable
private fun SiteDrawer(
    row: DesktopSourceRowUi,
    width: Dp,
    account: DesktopSourceAccountUi?,
    settings: List<DesktopSourceSettingUi>?,
    settingsNote: String?,
    onClose: () -> Unit,
    onToggleSource: (String, Boolean) -> Unit,
    onEditSource: ((String) -> Unit)?,
    onSourceLogin: ((String) -> Unit)?,
    onClearSourceOverride: ((String) -> Unit)?,
    onSignOutSource: (() -> Unit)?,
    onSettingChange: ((String, String) -> Unit)?,
    onSettingCallback: ((String) -> Unit)?,
) {
    val tokens = VeneraTokens
    Column(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .background(tokens.color.surfaceContainerHigh.copy(alpha = 0.98f))
            .border(
                1.dp,
                tokens.color.outlineVariant.copy(alpha = 0.3f),
                RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
            ),
    ) {
        // 头部
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 18.dp, bottom = 14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = row.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = tokens.color.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier.padding(top = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (row.overridden) StatusBadge("已修改")
                    // 稿 869-873：来源徽章 + 版本 + 站点主机，三段各自可缺（host 空就不摆）。
                    StatusBadge(row.originLabel)
                    if (requiresProxy(row.key)) StatusBadge("需代理")
                    if (row.version.isNotBlank()) {
                        Text(
                            text = "v${row.version}",
                            fontSize = 11.sp,
                            color = tokens.color.textTertiary,
                        )
                    }
                    if (row.host.isNotBlank()) {
                        Text(
                            text = row.host,
                            fontSize = 11.sp,
                            color = tokens.color.textTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clickable(onClick = onClose)
                    .semantics { contentDescription = "关闭站点设置" },
                contentAlignment = Alignment.Center,
            ) {
                Text("✕", fontSize = 13.sp, color = tokens.color.textSecondary)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            // 启停行
            DrawerCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "在站点列表中显示",
                            fontSize = VeneraType.body.size, lineHeight = VeneraType.body.lineHeight,
                            fontWeight = FontWeight.SemiBold,
                            color = tokens.color.textPrimary,
                        )
                        Text(
                            text = if (row.canDisable) {
                                "关闭后，探索与搜索里不再出现这个站点。"
                            } else {
                                "只剩这一颗开着，不能再关（全关后漫画域装不起任何一屏）。"
                            },
                            fontSize = 11.sp, lineHeight = 16.sp,
                            color = tokens.color.textTertiary,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                    VeneraSwitch(
                        checked = row.enabled,
                        onCheckedChange = { onToggleSource(row.key, !row.enabled) },
                        enabled = row.canDisable,
                        modifier = Modifier.semantics { contentDescription = "启停 ${row.key}" },
                    )
                }
            }

            // 账号
            if (account != null) {
                DrawerCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "账号",
                            fontSize = VeneraType.cardTitle.size, lineHeight = VeneraType.cardTitle.lineHeight,
                            fontWeight = FontWeight.SemiBold,
                            color = tokens.color.textPrimary,
                        )
                        Spacer(Modifier.weight(1f))
                        StatusBadge(if (account.loggedIn) "已登录" else "未登录", positive = account.loggedIn)
                    }
                    if (account.loggedIn) {
                        Text(
                            text = "已登录" + account.accountLine.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
                            fontSize = VeneraType.body.size, lineHeight = VeneraType.body.lineHeight,
                            color = tokens.color.textSecondary,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                        if (onSignOutSource != null) {
                            Text(
                                text = "退出登录",
                                fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                                color = tokens.color.error,
                                modifier = Modifier
                                    .clickable(onClick = onSignOutSource)
                                    .padding(top = 10.dp),
                            )
                        }
                    } else {
                        Text(
                            text = "登录后同步收藏、追更与阅读记录。",
                            fontSize = 12.sp, lineHeight = 18.sp,
                            color = tokens.color.textSecondary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        val canLogin = account.canLoginWithPassword ||
                            account.canLoginWithCookies ||
                            account.canLoginWithWebview
                        if (canLogin && onSourceLogin != null) {
                            Spacer(Modifier.height(10.dp))
                            AccentButton(
                                label = "登录",
                                onClick = { onSourceLogin(row.key) },
                                height = 31.dp,
                            )
                        } else {
                            Text(
                                text = com.venera.desktop.settings.DesktopI18n.t("这个站点没有声明可用的登录方式。", "This source does not declare a desktop login method."),
                                fontSize = 11.sp, lineHeight = 16.sp,
                                color = tokens.color.textTertiary,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
            }

            // 站点设置
            if (settings != null && settings.isNotEmpty()) {
                DrawerCard {
                    Text(
                        text = com.venera.desktop.settings.DesktopI18n.t("站点设置", "Source Settings"),
                        fontSize = VeneraType.cardTitle.size, lineHeight = VeneraType.cardTitle.lineHeight,
                        fontWeight = FontWeight.SemiBold,
                        color = tokens.color.textPrimary,
                    )
                    Text(
                        text = com.venera.desktop.settings.DesktopI18n.t("由站点脚本提供。", "Provided by source script."),
                        fontSize = 11.sp, lineHeight = 16.sp,
                        color = tokens.color.textTertiary,
                        modifier = Modifier.padding(top = 3.dp, bottom = 6.dp),
                    )
                    settings.forEach { def ->
                        SettingDefRow(
                            def = def,
                            onSettingChange = onSettingChange,
                            onSettingCallback = onSettingCallback,
                        )
                    }
                    if (settingsNote != null) {
                        Text(
                            text = settingsNote,
                            fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                            color = tokens.color.textSecondary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }

            // 脚本与维护
            DrawerCard {
                Text(
                    text = com.venera.desktop.settings.DesktopI18n.t("脚本", "Script"),
                    fontSize = VeneraType.cardTitle.size, lineHeight = VeneraType.cardTitle.lineHeight,
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.color.textPrimary,
                )
                val scriptOrigin = if (row.originLabel == "内置站点") "内置" else row.originLabel
                Text(
                    text = if (row.version.isNotBlank()) {
                        com.venera.desktop.settings.DesktopI18n.t("v${row.version} · $scriptOrigin", "v${row.version} · $scriptOrigin")
                    } else {
                        scriptOrigin
                    },
                    fontSize = 11.sp, lineHeight = 16.sp,
                    color = tokens.color.textTertiary,
                    modifier = Modifier.padding(top = 3.dp, bottom = 10.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onEditSource != null) {
                        DesktopPushButton(onClick = { onEditSource(row.key) }) {
                            Text(com.venera.desktop.settings.DesktopI18n.t("编辑脚本", "Edit Script"), fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight)
                        }
                    }
                    if (row.overridden && onClearSourceOverride != null) {
                        DesktopPushButton(onClick = { onClearSourceOverride(row.key) }) {
                            val revertLabel = if (row.isBundled) {
                                com.venera.desktop.settings.DesktopI18n.t("恢复内置脚本", "Restore Built-in")
                            } else {
                                com.venera.desktop.settings.DesktopI18n.t("移除脚本", "Remove Script")
                            }
                            Text(revertLabel, fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight)
                        }
                    }
                }
                if (row.inUse) {
                    Text(
                        text = com.venera.desktop.settings.DesktopI18n.t("此刻漫画域正装着这一颗。", "Currently active in Comics domain."),
                        fontSize = 11.sp, lineHeight = 16.sp,
                        color = tokens.color.primary,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
                Text(
                    text = com.venera.desktop.settings.DesktopI18n.t("脚本随应用版本更新；你对脚本的修改只保存在本机，可以随时恢复。", "Scripts update with the app; local edits can be restored anytime."),
                    fontSize = 11.sp, lineHeight = 17.sp,
                    color = tokens.color.textTertiary,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

/** 抽屉里的一张卡（卡头与行距对齐设置中枢）。 */
@Composable
private fun DrawerCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val tokens = VeneraTokens
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .background(
                tokens.color.surfaceContainerHigh.copy(alpha = 0.35f),
                RoundedCornerShape(12.dp),
            )
            .border(1.dp, tokens.color.outlineVariant.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 15.dp),
    ) {
        content()
    }
}

/** 站点设置里的一行：按档位挑控件（switch / select / callback / input）。 */
@Composable
private fun SettingDefRow(
    def: DesktopSourceSettingUi,
    onSettingChange: ((String, String) -> Unit)?,
    onSettingCallback: ((String) -> Unit)?,
) {
    SettingsRowTemplate(
        title = def.title,
        caption = def.caption.ifBlank { null },
        controlPlacement = when (def.type) {
            "select", "input" -> SettingsControlPlacement.Below
            else -> SettingsControlPlacement.Inline
        },
        control = {
            when (def.type) {
                "switch" -> VeneraSwitch(
                    checked = def.currentValue == "true",
                    onCheckedChange = { next ->
                        onSettingChange?.invoke(def.settingKey, if (next) "true" else "false")
                    },
                    modifier = Modifier.semantics { contentDescription = "站点设置 ${def.title}" },
                )
                "select" -> {
                    if (def.options.size in 2..3 && def.options.all { it.length <= 6 }) {
                        DesktopSegmented(
                            options = def.options,
                            selectedIndex = def.options.indexOf(def.currentValue).coerceAtLeast(0),
                            onSelect = { i -> onSettingChange?.invoke(def.settingKey, def.options[i]) },
                        )
                    } else {
                        SourceSettingsDropdown(
                            options = def.options.ifEmpty { listOf(def.currentValue) },
                            currentValue = def.currentValue,
                            onSelect = { v -> onSettingChange?.invoke(def.settingKey, v) },
                        )
                    }
                }
                "callback" -> if (onSettingCallback != null) {
                    DesktopPushButton(onClick = { onSettingCallback(def.settingKey) }) {
                        Text(
                            def.buttonText.ifBlank { "执行" },
                            fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                        )
                    }
                }
                "input" -> com.venera.desktop.ui.components.DesktopInputField(
                    value = def.currentValue,
                    onValueChange = { onSettingChange?.invoke(def.settingKey, it) },
                    modifier = Modifier.width(200.dp),
                )
            }
        },
    )
}

/** 站点设置下拉（薄壳，控件）：几何与展开体全在 [DesktopPopUpButton]。 */
@Composable
private fun SourceSettingsDropdown(
    options: List<String>,
    currentValue: String,
    onSelect: (String) -> Unit,
) {
    val index = options.indexOf(currentValue).coerceAtLeast(0)
    DesktopPopUpButton(
        label = null,
        value = currentValue,
        options = options,
        selectedIndex = index,
        onSelect = { i -> onSelect(options.getOrElse(i) { currentValue }) },
    )
}

/**
 * 账号卡的读数（壳现算递成品，屏不碰罐）。
 *
 * [accountLine] 是源罐里存的账号字串（`getAccount`；jm 存的是用户名/数字号）。
 * 取不到就空串 —— 屏上只念「已登录」，**不编**一个账号名。
 */
data class DesktopSourceAccountUi(
    val loggedIn: Boolean,
    val accountLine: String = "",
    /** 这一颗声明了手填凭据字段名单（非空 = 那一形存在）。 */
    val canLoginWithCookies: Boolean,
    /** 这一颗声明了 `account.login(account, pwd)`。 */
    val canLoginWithPassword: Boolean = false,
    /** 这一颗声明了应用内网页登录（桌面当前没有 WebView 宿主）。 */
    val canLoginWithWebview: Boolean = false,
)

/**
 * 站点设置里的一行（UI 模型）：定义 + 当下值由壳拼好递进来，屏只搬。
 *
 * [currentValue] 空串 = 罐里没有、脚本也没写 default（select 档下拉里念原值档第一枚，
 * switch 档按关着摆）—— **不编**一个档名。
 */
data class DesktopSourceSettingUi(
    val settingKey: String,
    val title: String,
    /** switch / select / callback / input（`DesktopSourceSettings` 只认这四档）。 */
    val type: String,
    val options: List<String> = emptyList(),
    val buttonText: String = "",
    val currentValue: String = "",
) {
    /** select 档下拉下方那句小字（候选枚数；其余档不摆）。 */
    val caption: String
        get() = when (type) {
            "select" -> if (options.isEmpty()) "" else "候选：${options.joinToString(" / ")}"
            "switch" -> if (currentValue.isBlank()) "脚本没写出厂档" else ""
            else -> ""
        }
}

/** 过滤档三档（全部 / 已开启 / 未启用）。 */
private enum class Filter { ALL, ON, OFF }

private fun requiresProxy(key: String): Boolean =
    key in com.venera.desktop.platform.PROXY_REQUIRED_SOURCE_KEYS

/**
 * 站点清单的一行（UI 模型）。
 *
 * 单独定义而不直接用 `DesktopSourceRow`（data 层那颗）：UI 不该依赖数据层的形状。
 *
 * ⚠️ `public` 是**被签名逼的**：壳 `VeneraDesktopLargeScreen` 是 public，
 * 它有一个 `readSources: () -> List<DesktopSourceRowUi>` 形参 ——
 * Kotlin 不许 public 函数暴露 internal 类型参数。
 */
data class DesktopSourceRowUi(
    val key: String,
    val title: String,
    val enabled: Boolean,
    /** 这一颗有没有落在用户覆盖层里（源编辑）。 */
    val overridden: Boolean = false,
    /** 那颗源自己声明的手填凭据字段名单（空表 = 不给那一格）。 */
    val cookieFields: List<String> = emptyList(),
    /** 源脚本声明了 `account.login(account, pwd)`。 */
    val supportsPasswordLogin: Boolean = false,
    /** 源脚本声明了 `account.loginWithWebview`。 */
    val supportsWebLogin: Boolean = false,
    /** 源脚本头部声明的版本；空串 = 不摆。 */
    val version: String = "",
    /** 源脚本 `settings` 块声明的设置项数（稿 §2.3 副行的「N 项设置」）；0 = 没声明。 */
    val settingsCount: Int = 0,
    /** 这一颗是不是漫画域当前真在用的那颗。 */
    val inUse: Boolean = false,
    /** 这一颗源声明里的站点主机（首页测速用）。 */
    val host: String = "",
    /** 这一颗关不关得掉（只剩它一颗开着时为 false）。 */
    val canDisable: Boolean = true,
    /** 盘上那颗罐里真有「已登录」那一格。 */
    val loggedIn: Boolean = false,
    /** 来源标注（内置站点 / 官方源 / laincat/vensrc / 本地脚本）。 */
    val originLabel: String = "内置站点",
    /** 最近一次落盘登记的仓库 ID（null = 未登记或未覆写的随包源）。 */
    val repoId: String? = null,
    /** 是否存在随包原始版本（用于区分抽屉显示「恢复内置脚本」还是「移除脚本」）。 */
    val isBundled: Boolean = true,
)

/**
 * 站点墙的拖拽账：哪一格被拿起、拖了多远、每一格此刻落在根坐标的哪里。
 *
 * ## 为什么是长按
 *
 * 卡片身本来就有单击（浏览/开抽屉），索引条也有单击（就地启用）。直接拖会把这两种点按
 * 吃掉一半（用户按歪两像素就变成拖动）。长按拿起是桌面惯例，也让竖向滚动在有卡的区域照常生效。
 *
 * ## 为什么松手才结算
 *
 * 拖动过程中只做视觉变换（`graphicsLayer` 平移 + 抬升），不动清单：每动一次清单都要重读盘、
 * 重排整棵树，手指还没松就已经抖成一团。落点按「离指针最近的槽」算，跨过已启用/未启用那条
 * 分界线就顺手改启停 —— 这正是主人要的「拖到下面关闭、拖到上面启用」。
 */
@Stable
private class SiteDragState(
    private val rowsProvider: () -> List<DesktopSourceRowUi>,
    private val visibleProvider: () -> List<String>,
    private val onCommit: (List<String>, Set<String>) -> Unit,
) {
    /** key → (根坐标矩形, 那一格此刻是不是已开启)。 */
    val slots = mutableMapOf<String, Pair<Rect, Boolean>>()

    var key by mutableStateOf<String?>(null)
        private set
    var delta by mutableStateOf(Offset.Zero)
        private set

    fun start(k: String) {
        key = k
        delta = Offset.Zero
    }

    fun move(amount: Offset) {
        if (key != null) delta += amount
    }

    fun cancel() {
        key = null
        delta = Offset.Zero
    }

    fun drop() {
        val moving = key ?: return
        val origin = slots[moving]?.first?.center
        val pointer = origin?.plus(delta)
        val target = if (pointer == null) {
            null
        } else {
            slots.entries
                .filter { it.key != moving }
                .minByOrNull { (_, slot) -> (slot.first.center - pointer).getDistance() }
        }
        cancel()
        if (pointer == null || target == null) return

        val visible = visibleProvider().toMutableList()
        if (moving !in visible) return
        visible.remove(moving)
        val anchor = visible.indexOf(target.key).let { if (it < 0) visible.size else it }
        // 落在目标那一格的下半 ⇒ 排在它后面，上半 ⇒ 排在它前面（与常见拖拽手感一致）。
        val after = pointer.y > target.value.first.center.y
        visible.add((anchor + if (after) 1 else 0).coerceIn(0, visible.size), moving)

        val rows = rowsProvider()
        // 被筛选/搜索挡掉的站点不在可见集里，按原相对次序跟在后面 —— 拖一次不许把它们弄丢。
        val hidden = rows.map { it.key }.filter { it !in visible }
        val enabled = rows.filter { it.enabled }.map { it.key }.toMutableSet()
        if (target.value.second) enabled.add(moving) else enabled.remove(moving)
        onCommit(visible + hidden, enabled)
    }
}

/**
 * 一格可拖的站点：登记自己的矩形、拖动中做视觉变换、长按拿起松手结算。
 *
 * 平移**不套补间**（拖动要跟手）；松手后的落位由壳重读清单后的新布局直接给出。
 */
private fun Modifier.siteDragItem(
    key: String,
    enabled: Boolean,
    state: SiteDragState,
): Modifier = this
    .onGloballyPositioned { state.slots[key] = it.boundsInRoot() to enabled }
    .graphicsLayer {
        val me = state.key == key
        translationX = if (me) state.delta.x else 0f
        translationY = if (me) state.delta.y else 0f
        scaleX = if (me) 1.02f else 1f
        scaleY = if (me) 1.02f else 1f
        shadowElevation = if (me) 20f else 0f
        alpha = if (me) 0.94f else 1f
    }
    .pointerInput(key) {
        detectDragGesturesAfterLongPress(
            onDragStart = { state.start(key) },
            onDrag = { change, amount ->
                change.consume()
                state.move(amount)
            },
            onDragEnd = { state.drop() },
            onDragCancel = { state.cancel() },
        )
    }
