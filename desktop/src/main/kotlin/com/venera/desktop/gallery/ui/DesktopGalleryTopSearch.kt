package com.venera.desktop.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.venera.compose.data.platform.HttpEngine
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.data.GalleryTagSuggestion
import com.venera.compose.gallery.domain.GalleryHomeDomain
import com.venera.compose.gallery.domain.GallerySearchEntry
import com.venera.compose.gallery.domain.GallerySearchSource
import com.venera.compose.ui.tokens.LocalVeneraDarkTheme
import com.venera.desktop.gallery.data.DesktopPixivAutocomplete
import com.venera.desktop.ui.components.DesktopInputField
import com.venera.desktop.ui.theme.VeneraType
import com.venera.desktop.ui.vShadow
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 查询级成人内容排除的后缀（gallery-floating-search-组件规格 §4）。
 *
 * 语义由调用方定：[allowExplicit] = false 时 booru 各腿查询串追加排除 tag，true 恒回空串。
 * Moebooru 族已实测 `-rating:e` 只回 `s,q`；Gelbooru 用四档单词形。
 * TBIB 同 dapi 族但站方 rating 格未实测——注错 = 整腿空结果，比漏过滤更糟，先不注；
 * SAFEBOORU 天生全年龄；KONACHAN_R18 是**刻意单选的成人库**，注排除就失去存在意义；
 * PIXIV 不走 booru 的 `tags=` 轴，到不了这里。
 */
internal fun desktopSearchRatingSuffix(site: GallerySite, allowExplicit: Boolean): String {
    if (allowExplicit) return ""
    return when (site) {
        GallerySite.GELBOORU -> "-rating:explicit"
        GallerySite.YANDERE, GallerySite.KONACHAN -> "-rating:e"
        else -> ""
    }
}

private const val TOP_SEARCH_HISTORY_LIMIT = 5
private const val TOP_SEARCH_SUGGEST_LIMIT = 6

private fun suggestionSub(s: GalleryTagSuggestion): String =
    if (s.count > 0) "${s.count} · ${s.categoryLabel}" else s.categoryLabel

/** 该条历史归哪个域：booru = 带任一非 pixiv 站；pixiv = pixiv 单站条目（桌面来源轴剥 pixiv 的既定口径）。 */
private fun GallerySearchEntry.matchesDomain(booru: Boolean): Boolean =
    if (booru) sites.any { it != GallerySite.PIXIV } else sites.contains(GallerySite.PIXIV)

/**
 * 画廊顶栏的**真输入悬浮搜索场**（gallery-floating-search-落地）。
 *
 * 两域同一场：BOORU 带「站源胶囊 + 浮层站源 chip 行 + 全年龄开关」，PIXIV 只有历史与
 * 官方 AutoComplete 联想。回车/点行经 [onSearch] 交壳：scope `null` = booru 聚合，
 * 单站 = 单站源，`PIXIV` = pixiv 专属搜索轴。结果页仍是既有检索屏，这里只管输入发生地。
 *
 * @param domain 内容域，决定占位文案与浮层分区
 * @param booruSites booru 域可点选的站源表（聚合档 = 整表交接）
 * @param ports 历史（[desktopHistoryStore]）与 booru 联想腿的端口
 * @param pixivEngine pixiv AutoComplete 的 JS 引擎（null = 走该件的兜底腿）
 * @param searchR18 true = 查询允许成人内容（不追加排除）；false = 全年龄（查询级排除）
 * @param onPickSearchR18 开关落盘回调（状态住壳，形状与 wallDensity 同族）
 * @param onSearch 发车回调：站源（null = booru 聚合）+ 词原样（不改写不剥减号）
 * @param modifier 中段占位（chrome 那一格的 weight 槽）
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DesktopGalleryTopSearch(
    domain: GalleryHomeDomain,
    booruSites: List<GallerySite>,
    ports: GalleryPorts,
    pixivEngine: HttpEngine?,
    searchR18: Boolean,
    onPickSearchR18: (Boolean) -> Unit,
    onSearch: (GallerySite?, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalChromePalette.current
    val booru = domain == GalleryHomeDomain.BOORU
    val ioScope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    // 浮层的开关只用「聚焦 + 未被压住」两样算（原先只在打字时置开，
    // 纯聚焦不弹）。Esc / 点外部 / 发车是"压住"，重新聚焦或打字抬起。
    var suppressDropdown by remember { mutableStateOf(false) }
    var pickedSite by remember { mutableStateOf<GallerySite?>(null) }
    var history by remember { mutableStateOf<List<GallerySearchEntry>>(emptyList()) }
    var suggestions by remember { mutableStateOf<List<GalleryTagSuggestion>>(emptyList()) }
    var activeIdx by remember { mutableIntStateOf(-1) }
    var fieldWidthPx by remember { mutableIntStateOf(0) }
    // 输入场**下缘**在锚点 Box 里的 y（像素）。浮层从这一线 + 8dp 往下开——
    // Popup 的 BottomStart 会让浮层朝上生长盖住输入框，不能用。
    var fieldBottomPx by remember { mutableIntStateOf(0) }
    val fieldInteraction = remember { MutableInteractionSource() }
    val focused by fieldInteraction.collectIsFocusedAsState()
    val density = LocalDensity.current
    val open = focused && !suppressDropdown

    LaunchedEffect(focused) {
        if (focused) {
            suppressDropdown = false
            activeIdx = -1
        }
    }

    // 浮层展开时读一次历史：检索屏发车侧（壳层 push）会写盘，开一次读一次即是最新账。
    LaunchedEffect(open, booru) {
        if (!open) return@LaunchedEffect
        val got = withContext(Dispatchers.IO) {
            runCatching { desktopHistoryRead(desktopHistoryStore(ports)) }
        }
        history = got.getOrDefault(emptyList())
    }

    // 联想：250ms 防抖（与检索屏同参），站源跟随胶囊；pixiv 走官方 AutoComplete。
    LaunchedEffect(text, pickedSite, booru, open) {
        val word = text.trim()
        if (!open || word.isEmpty()) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(250)
        val nowWord = text.trim()
        if (nowWord.isEmpty()) return@LaunchedEffect
        val got = withContext(Dispatchers.IO) {
            runCatching {
                if (!booru) {
                    DesktopPixivAutocomplete.query(
                        engine = pixivEngine, word = nowWord, limit = TOP_SEARCH_SUGGEST_LIMIT,
                    )
                } else {
                    val configured = desktopGelbooruConfigured(ports)
                    val source = pickedSite?.let { GallerySearchSource(listOf(it)) } ?: GallerySearchSource.DESKTOP_ALL
                    val legs = source.availableLegs(configured)
                    // 腿并行 + 每腿独立超时（与检索屏同参）：串行时一条挂死腿拖住整排候选。
                    desktopMergeSuggestionLegs(
                        nowWord,
                        coroutineScope {
                            legs.map { site ->
                                async {
                                    val got = withTimeoutOrNull(DESKTOP_SUGGEST_LEG_WAIT_MS) {
                                        boardOf(ports, site).searchTags(nowWord)
                                    }
                                    site to (got ?: Result.failure(IllegalStateException("联想腿超时")))
                                }
                            }.map { it.await() }
                        },
                        limit = TOP_SEARCH_SUGGEST_LIMIT,
                    ).candidates
                }
            }
        }
        if (text.trim() == nowWord) suggestions = got.getOrDefault(emptyList())
    }

    fun submit(term: String) {
        val word = term.trim()
        if (word.isEmpty()) return
        suppressDropdown = true
        onSearch(if (booru) pickedSite else GallerySite.PIXIV, word)
    }

    fun clearDomainHistory() {
        val next = history.filterNot { it.matchesDomain(booru) }
        ioScope.launch {
            val did = withContext(Dispatchers.IO) {
                runCatching {
                    // 清完的档走删键（与整清同语义），不写空表哨兵 —— DesktopSearchHistory 头注那笔账。
                    if (next.isEmpty()) desktopHistoryClearAll(desktopHistoryStore(ports))
                    else desktopHistoryWrite(desktopHistoryStore(ports), next)
                }
            }
            if (did.isSuccess) history = next
        }
    }

    val visibleHistory = history.filter { entry ->
        val q = text.trim().lowercase()
        entry.matchesDomain(booru) &&
            (q.isEmpty() || entry.filters.joinToString(" ") { it.token }.lowercase().contains(q))
    }.take(TOP_SEARCH_HISTORY_LIMIT)

    val isDark = LocalVeneraDarkTheme.current
    val galleryColors = GalleryTokens.Color
    val scopePillShape = RoundedCornerShape(4.dp)

    Box(modifier, contentAlignment = Alignment.CenterStart) {
        DesktopInputField(
            value = text,
            onValueChange = {
                text = it
                activeIdx = -1
                suppressDropdown = false
            },
            modifier = Modifier
                .widthIn(min = 220.dp, max = 480.dp)
                .fillMaxWidth()
                // 场上任何一次按下都解除"压住"：点别处关浮层后焦点往往还留在输入框
                // （没触发重新聚焦），第二次点场就靠这一把抬起来（真窗首修 · 只观察不吞事件）。
                .pointerInput(Unit) {
                    awaitEachGesture { awaitFirstDown(requireUnconsumed = false); suppressDropdown = false }
                }
                .onGloballyPositioned {
                    fieldWidthPx = it.size.width
                    fieldBottomPx = it.size.height + it.positionInParent().y.roundToInt()
                }
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val rows = visibleHistory.size + suggestions.size
                    when (e.key) {
                        Key.Enter -> {
                            val term = when {
                                activeIdx < 0 || activeIdx >= rows -> text
                                activeIdx < visibleHistory.size ->
                                    visibleHistory[activeIdx].filters.joinToString(" ") { it.token }
                                else -> suggestions[activeIdx - visibleHistory.size].name
                            }
                            submit(term)
                            true
                        }
                        Key.Escape -> {
                            // 下拉开着才吃（先收下拉）；已收着的那枚放行，交给壳走全局返回。
                            if (focused && !suppressDropdown) {
                                suppressDropdown = true
                                activeIdx = -1
                                true
                            } else {
                                false
                            }
                        }
                        Key.DirectionDown -> {
                            if (rows > 0) activeIdx = (activeIdx + 1).coerceAtMost(rows - 1)
                            true
                        }
                        Key.DirectionUp -> {
                            if (rows > 0) activeIdx = (activeIdx - 1).coerceAtLeast(0)
                            true
                        }
                        else -> false
                    }
                },
            placeholder = if (booru) "搜索标签" else "搜索作品",
            leadingIcon = Icons.Outlined.Search,
            interactionSource = fieldInteraction,
            trailing = {
                if (text.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(palette.hover)
                            .clickable {
                                text = ""
                                activeIdx = -1
                                suppressDropdown = false
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "清空",
                            tint = palette.ink2,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
                if (booru) {
                    val scopeInteraction = remember { MutableInteractionSource() }
                    val scopeHovered by scopeInteraction.collectIsHoveredAsState()
                    Row(
                        modifier = Modifier
                            .height(26.dp)
                            .hoverable(scopeInteraction)
                            .clip(scopePillShape)
                            .background(if (scopeHovered) palette.hover else palette.card)
                            .border(1.dp, palette.hairline, scopePillShape)
                            .clickable { suppressDropdown = false }
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = pickedSite?.displayName ?: "聚合",
                            fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                            fontWeight = FontWeight.Medium,
                            color = palette.ink1,
                            maxLines = 1,
                        )
                        Icon(
                            imageVector = Icons.Outlined.KeyboardArrowDown,
                            contentDescription = null,
                            tint = palette.ink3,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
            },
        )

        if (open) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, fieldBottomPx + with(density) { 8.dp.roundToPx() }),
                onDismissRequest = { suppressDropdown = true; activeIdx = -1 },
            ) {
                val flyoutShape = RoundedCornerShape(12.dp)
                val itemShape = RoundedCornerShape(6.dp)
                Column(
                    modifier = Modifier
                        .width(with(density) { fieldWidthPx.toDp() }.coerceAtLeast(380.dp))
                        .then(if (!isDark) Modifier.vShadow(16.dp, flyoutShape) else Modifier)
                        .clip(flyoutShape)
                        .background(palette.elevated)
                        .border(
                            1.dp,
                            if (isDark) palette.frame else palette.hairline,
                            flyoutShape,
                        )
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (booru) {
                        SectionLabel("检索源范围", palette, onAction = null, onActionClick = null)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            ScopeChip("聚合", pickedSite == null, palette, itemShape) {
                                pickedSite = null; activeIdx = -1
                            }
                            booruSites.forEach { site ->
                                ScopeChip(site.displayName, pickedSite == site, palette, itemShape) {
                                    pickedSite = site; activeIdx = -1
                                }
                            }
                        }
                        val r18RowInteraction = remember { MutableInteractionSource() }
                        val r18RowHovered by r18RowInteraction.collectIsHoveredAsState()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                                .clip(itemShape)
                                .background(if (r18RowHovered) palette.hover else Color.Transparent)
                                .hoverable(r18RowInteraction)
                                .clickable(interactionSource = r18RowInteraction, indication = null) {
                                    onPickSearchR18(!searchR18)
                                }
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            R18Switch(!searchR18, palette) { onPickSearchR18(!it) }
                            Text(
                                text = "全年龄",
                                fontSize = VeneraType.bodySecondary.size, lineHeight = VeneraType.bodySecondary.lineHeight,
                                fontWeight = FontWeight.Medium,
                                color = palette.ink1,
                            )
                            Text(
                                text = "搜索结果排除成人内容",
                                fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                                color = palette.ink3,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (visibleHistory.isNotEmpty() || suggestions.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                    .height(1.dp)
                                    .background(palette.hairline),
                            )
                        }
                    }
                    if (visibleHistory.isNotEmpty()) {
                        SectionLabel("最近搜索", palette, onAction = if (booru || history.isNotEmpty()) "清除" else null) {
                            clearDomainHistory()
                        }
                        visibleHistory.forEachIndexed { idx, entry ->
                            val term = entry.filters.joinToString(" ") { it.token }
                            DropdownRow(
                                active = idx == activeIdx,
                                palette = palette,
                                shape = itemShape,
                                onClick = { submit(term) },
                                onDelete = {
                                    val removed = entry
                                    ioScope.launch {
                                        val next = withContext(Dispatchers.IO) {
                                            runCatching {
                                                desktopHistoryRemoveOne(desktopHistoryStore(ports), history, removed)
                                            }
                                        }
                                        next.getOrNull()?.let { history = it }
                                    }
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.History,
                                    contentDescription = null,
                                    tint = palette.ink3,
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    text = term,
                                    fontSize = VeneraType.bodySecondary.size, lineHeight = VeneraType.bodySecondary.lineHeight,
                                    color = palette.ink1,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                    if (visibleHistory.isNotEmpty() && suggestions.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                .height(1.dp)
                                .background(palette.hairline),
                        )
                    }
                    if (suggestions.isNotEmpty()) {
                        SectionLabel(
                            if (booru) "联想词 · ${pickedSite?.displayName ?: "聚合"}" else "联想词",
                            palette,
                            onAction = null,
                            onActionClick = null,
                        )
                        suggestions.forEachIndexed { idx, s ->
                            val cn = DesktopPixivAutocomplete.translationFor(s.name)
                            val catColor = when (s.categoryLabel) {
                                "画师" -> galleryColors.ToneCaution
                                "角色" -> galleryColors.ToneSafe
                                "作品", "版权" -> galleryColors.AccentPrimary
                                else -> palette.ink3
                            }
                            DropdownRow(
                                active = visibleHistory.size + idx == activeIdx,
                                palette = palette,
                                shape = itemShape,
                                onClick = { submit(s.name) },
                                onDelete = null,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(catColor),
                                )
                                Text(
                                    text = if (cn.isNotBlank() && cn != s.name) "$cn (${s.name})" else s.name,
                                    fontSize = VeneraType.bodySecondary.size, lineHeight = VeneraType.bodySecondary.lineHeight,
                                    color = palette.ink1,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = suggestionSub(s),
                                    fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                                    color = palette.ink3,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    if (visibleHistory.isEmpty() && suggestions.isEmpty()) {
                        Text(
                            text = "输入关键词开始搜索",
                            fontSize = VeneraType.bodySecondary.size, lineHeight = VeneraType.bodySecondary.lineHeight,
                            color = palette.ink3,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScopeChip(
    label: String,
    selected: Boolean,
    palette: DesktopChromePalette,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .height(28.dp)
            .hoverable(interaction)
            .clip(shape)
            .background(
                when {
                    selected -> palette.accent.copy(alpha = 0.16f)
                    hovered -> palette.hover
                    else -> palette.segmentTrack
                },
            )
            .border(
                1.dp,
                when {
                    selected -> palette.accent.copy(alpha = 0.45f)
                    hovered -> palette.frame
                    else -> palette.hairline
                },
                shape,
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) palette.accent else palette.ink2,
            maxLines = 1,
        )
    }
}

/** 20dp 滑块开关（规格 §4：34×20 轨 + 16 滑块）。[checked] = 全年龄档生效，回调交**新档**。 */
@Composable
private fun R18Switch(
    checked: Boolean,
    palette: DesktopChromePalette,
    onToggle: (Boolean) -> Unit,
) {
    com.venera.desktop.ui.DesktopSwitch(checked = checked, onCheckedChange = onToggle)
}

@Composable
private fun SectionLabel(
    label: String,
    palette: DesktopChromePalette,
    onAction: String?,
    onActionClick: (() -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(24.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
            fontWeight = FontWeight.SemiBold,
            color = palette.ink3,
        )
        if (onAction != null && onActionClick != null) {
            Spacer(Modifier.weight(1f))
            val actionInteraction = remember { MutableInteractionSource() }
            val actionHovered by actionInteraction.collectIsHoveredAsState()
            Text(
                text = onAction,
                fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                color = if (actionHovered) palette.danger else palette.ink3,
                modifier = Modifier
                    .hoverable(actionInteraction)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (actionHovered) palette.hover else Color.Transparent)
                    .clickable(interactionSource = actionInteraction, indication = null, onClick = onActionClick)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun DropdownRow(
    active: Boolean,
    palette: DesktopChromePalette,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
    onDelete: (() -> Unit)?,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(shape)
            .background(
                when {
                    active -> palette.accent.copy(alpha = 0.16f)
                    hovered -> palette.hover
                    else -> Color.Transparent
                },
            )
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
        if (onDelete != null) {
            val delInteraction = remember { MutableInteractionSource() }
            val delHovered by delInteraction.collectIsHoveredAsState()
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (delHovered) palette.hover else Color.Transparent)
                    .hoverable(delInteraction)
                    .clickable(interactionSource = delInteraction, indication = null, onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "删除这条历史",
                    tint = if (delHovered) palette.danger else palette.ink3,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}
