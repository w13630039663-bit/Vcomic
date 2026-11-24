package com.venera.desktop.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.FitScreen
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.venera.desktop.ui.theme.VeneraType
import kotlinx.coroutines.delay

/**
 * 设置弹窗的扁平色板，深浅两档同形。
 *
 * 自持而非复用玻璃令牌：扁平壳不吃玻璃皮，且深浅切换时整套表面要一起换，
 * 不能被主壳的局部供给牵着走。
 */
internal data class SettingsDialogPalette(
    val bg: Color,
    val divider: Color,
    val dividerStrong: Color,
    val hover: Color,
    val pressed: Color,
    val selected: Color,
    val focus: Color,
    val text1: Color,
    val text2: Color,
    val text3: Color,
    val field: Color,
    val fieldBorder: Color,
    val onAccent: Color,
    val iconColors: List<Color>,
    val iconSlate: Color,
    val switchOn: Color,
    val switchOff: Color,
    val danger: Color,
    val menuBg: Color,
) {
    companion object {
        val Deep = SettingsDialogPalette(
            bg = Color(0xFF2B2B2B),
            divider = Color(0xFF3A3A3A),
            dividerStrong = Color(0xFF454545),
            hover = Color(0x0DFFFFFF),
            pressed = Color(0x17FFFFFF),
            selected = Color(0x17FFFFFF),
            focus = Color(0xFF5B9CFF),
            text1 = Color(0xFFEAEAEA),
            text2 = Color(0xFFA8A8A8),
            text3 = Color(0xFF7C7C7C),
            field = Color(0xFF303030),
            fieldBorder = Color(0xFF454545),
            onAccent = Color.White,
            iconColors = listOf(
                Color(0xFF7C5CFF), Color(0xFF22C55E), Color(0xFF3B82F6),
                Color(0xFF06B6D4), Color(0xFFF97316),
            ),
            iconSlate = Color(0xFF64748B),
            switchOn = Color(0xFF3B82F6),
            switchOff = Color(0xFF555555),
            danger = Color(0xFFF87171),
            menuBg = Color(0xFF303030),
        )

        val Light = SettingsDialogPalette(
            bg = Color(0xFFFAFAFA),
            divider = Color(0xFFE6E6E6),
            dividerStrong = Color(0xFFDCDCDC),
            hover = Color(0x0A000000),
            pressed = Color(0x14000000),
            selected = Color(0x14000000),
            focus = Color(0xFF0A63F6),
            text1 = Color(0xFF1E1E1E),
            text2 = Color(0xFF5E5E5E),
            text3 = Color(0xFF8A8A8A),
            field = Color(0xFFFFFFFF),
            fieldBorder = Color(0xFFDCDCDC),
            onAccent = Color.White,
            iconColors = listOf(
                Color(0xFF6D4DF2), Color(0xFF16A34A), Color(0xFF2563EB),
                Color(0xFF0891B2), Color(0xFFEA580C),
            ),
            iconSlate = Color(0xFF94A3B8),
            switchOn = Color(0xFF2563EB),
            switchOff = Color(0xFFC9C9C9),
            danger = Color(0xFFDC2626),
            menuBg = Color(0xFFFFFFFF),
        )
    }
}

/** 按主题明暗取扁平色板。 */
internal fun settingsDialogPalette(dark: Boolean): SettingsDialogPalette =
    if (dark) SettingsDialogPalette.Deep else SettingsDialogPalette.Light

/**
 * 设置弹窗的扁平壳：跨窗顶栏 + 分区轨 + 分组扁平右栏。
 *
 * @param sections 左轨分区列表，顺序即选中顺序。
 * @param selectedSection 当前分区；搜索态下左轨不高亮任何一项时传 null。
 * @param onSelectSection 分区点击回调，传入被点分区。
 * @param title 顶栏居中标题（分区名或「搜索结果」）。
 * @param query 搜索框当前文本。
 * @param onQueryChange 搜索文本变化回调，传新文本。
 * @param cards 右栏要渲染的卡，每张卡的标题作为分组标题；搜索态已带分区前缀。
 * @param sectionIcon 分区图标供给，接收分区返回图标向量。
 * @param userCard 左轨用户卡槽，由装配方提供以复用既有账号状态判式。
 * @param footer 左轨底部版本尾注，换行用 \n。
 * @param palette 扁平色板，按主题明暗取。
 * @param onClose 关闭弹窗回调。
 * @param modifier 外层修饰符。
 */
@Composable
internal fun SettingsDialogShell(
    sections: List<DesktopSettingsSection>,
    selectedSection: DesktopSettingsSection?,
    onSelectSection: (DesktopSettingsSection) -> Unit,
    title: String,
    query: String,
    onQueryChange: (String) -> Unit,
    cards: List<DesktopSettingsCard>,
    sectionIcon: (DesktopSettingsSection) -> ImageVector,
    userCard: @Composable () -> Unit,
    footer: String,
    palette: SettingsDialogPalette,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    customContent: (@Composable () -> Unit)? = null,
) {
    BoxWithConstraints(
        modifier = modifier
            .sizeIn(minWidth = 720.dp, minHeight = 480.dp, maxWidth = 960.dp, maxHeight = 860.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(palette.bg)
            .border(1.dp, palette.dividerStrong, RoundedCornerShape(24.dp))
            // 拦截弹窗内部空白区域的点击，避免穿透到外层遮罩触发误退出。
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        // 弹窗自身宽度由 sizeIn 定：约束里的可用宽是父容器给的，系统 Dialog 窗口下会比 720 小，
        // 拿父约束判窄会把 200dp 左轨误压成图标条。只在真正窄窗时才收轨。
        val compact = maxWidth < 600.dp
        Column(modifier = Modifier.fillMaxSize()) {
            DialogTopBar(
                title = title,
                query = query,
                onQueryChange = onQueryChange,
                palette = palette,
                compact = compact,
                onClose = onClose,
            )
            Row(modifier = Modifier.weight(1f).fillMaxHeight()) {
                DialogRail(
                    sections = sections,
                    selectedSection = selectedSection,
                    onSelectSection = onSelectSection,
                    sectionIcon = sectionIcon,
                    userCard = userCard,
                    footer = footer,
                    palette = palette,
                    compact = compact,
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(palette.divider),
                )
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    if (customContent != null) {
                        customContent()
                    } else {
                        DialogContent(cards = cards, palette = palette, query = query)
                    }
                }
            }
        }
    }
}

/** 跨窗三槽顶栏：左搜索、中标题、右关闭。 */
@Composable
private fun DialogTopBar(
    title: String,
    query: String,
    onQueryChange: (String) -> Unit,
    palette: SettingsDialogPalette,
    compact: Boolean,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(if (compact) 64.dp else 200.dp)) {
            DialogSearchField(
                value = query,
                onValueChange = onQueryChange,
                palette = palette,
                compact = compact,
            )
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = palette.text1,
                maxLines = 1,
            )
        }
        Box(modifier = Modifier.width(56.dp), contentAlignment = Alignment.CenterEnd) {
            val interaction = remember { MutableInteractionSource() }
            val hovered by interaction.collectIsHoveredAsState()
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (hovered) palette.hover else Color.Transparent)
                    .hoverable(interaction)
                    .clickable(interactionSource = interaction, indication = null, onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = if (com.venera.desktop.settings.DesktopLanguage.isEnglish()) "Close" else "关闭",
                    tint = palette.text2,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(palette.divider),
    )
}

/** 顶栏左槽搜索场：与画廊顶栏同一颗，色源换成设置对话窗的扁平色板。 */
@Composable
private fun DialogSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    palette: SettingsDialogPalette,
    compact: Boolean,
) {
    val colors = com.venera.desktop.ui.components.DesktopSearchFieldColors(
        fieldBg = palette.field,
        fieldBgFocused = palette.field,
        fieldBorder = palette.fieldBorder,
        fieldBorderHover = palette.focus,
        fieldBorderFocus = palette.focus,
        iconTint = palette.text2,
        textColor = palette.text1,
        placeholderColor = palette.text3,
        hintColor = palette.text2,
        cursor = palette.focus,
    )
    com.venera.desktop.ui.components.DesktopSearchField(
        value = value,
        onValueChange = onValueChange,
        onSearch = {},
        modifier = Modifier.width(if (compact) 56.dp else 172.dp),
        placeholder = if (com.venera.desktop.settings.DesktopLanguage.isEnglish()) "Search" else "搜索",
        onClear = { onValueChange("") },
        colors = colors,
    )
}

/** 左轨：用户卡 + 分区列表 + 版本尾注，列表独立滚动。 */
@Composable
private fun DialogRail(
    sections: List<DesktopSettingsSection>,
    selectedSection: DesktopSettingsSection?,
    onSelectSection: (DesktopSettingsSection) -> Unit,
    sectionIcon: (DesktopSettingsSection) -> ImageVector,
    userCard: @Composable () -> Unit,
    footer: String,
    palette: SettingsDialogPalette,
    compact: Boolean,
) {
    val railWidth = if (compact) 64.dp else 200.dp
    Column(
        modifier = Modifier
            .width(railWidth)
            .fillMaxHeight()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        userCard()
        Spacer(Modifier.height(12.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            sections.forEachIndexed { index, section ->
                DialogNavItem(
                    section = section,
                    icon = sectionIcon(section),
                    color = palette.iconColors[index % palette.iconColors.size],
                    selected = section == selectedSection,
                    palette = palette,
                    compact = compact,
                    onClick = { onSelectSection(section) },
                )
            }
        }
        if (!compact) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = footer,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = palette.text2,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

/** 分区项：彩色圆角图标块 + 文字，选中整行浅底。 */
@Composable
private fun DialogNavItem(
    section: DesktopSettingsSection,
    icon: ImageVector,
    color: Color,
    selected: Boolean,
    palette: SettingsDialogPalette,
    compact: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val base = when {
        selected -> palette.selected
        pressed -> palette.pressed
        hovered -> palette.hover
        else -> Color.Transparent
    }
    val bg by animateColorAsState(base, tween(150), label = "navBg")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .then(if (focused) Modifier.border(2.dp, palette.focus, RoundedCornerShape(8.dp)) else Modifier)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .graphicsLayer(scaleX = if (pressed) 0.96f else 1f, scaleY = if (pressed) 0.96f else 1f)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (compact) Arrangement.Center else Arrangement.spacedBy(12.dp),
    ) {
        IconBlock(icon = icon, color = color, palette = palette)
        if (!compact) {
            Text(
                text = section.title,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                color = if (selected) palette.text1 else palette.text2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** 24dp 彩色圆角块，块内 14dp 线性图标。 */
@Composable
private fun IconBlock(icon: ImageVector, color: Color, palette: SettingsDialogPalette) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = palette.onAccent,
            modifier = Modifier.size(14.dp),
        )
    }
}

/** 右栏：分组标题 + 扁平行，独立滚动。 */
@Composable
private fun DialogContent(
    cards: List<DesktopSettingsCard>,
    palette: SettingsDialogPalette,
    query: String,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
    ) {
        if (cards.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp),
                contentAlignment = Alignment.Center,
            ) {
                val isEn = com.venera.desktop.settings.DesktopLanguage.isEnglish()
                Text(
                    text = if (query.isEmpty()) {
                        if (isEn) "No settings in this section." else "这一组还没有设置项。"
                    } else {
                        if (isEn) "No settings matching '$query'." else "没有匹配「$query」的设置项。"
                    },
                    fontSize = VeneraType.body.size,
                    color = palette.text2,
                )
            }
            return@Column
        }
        cards.forEachIndexed { cardIndex, card ->
            Spacer(Modifier.height(if (cardIndex == 0) 8.dp else 20.dp))
            Text(
                text = card.title,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
                color = palette.text2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
            )
            card.rows.forEachIndexed { rowIndex, row ->
                if (rowIndex > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .padding(horizontal = 12.dp)
                            .background(palette.divider),
                    )
                }
                DialogRow(
                    row = row,
                    color = if (row is DesktopSettingRow.Absent) palette.iconSlate
                    else palette.iconColors[(cardIndex + rowIndex) % palette.iconColors.size],
                    palette = palette,
                )
            }
        }
    }
}

/** 一枚扁平行：左彩色图标块 + 标题，右控件槽。 */
@Composable
private fun DialogRow(
    row: DesktopSettingRow,
    color: Color,
    palette: SettingsDialogPalette,
) {
    var expanded by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val rowClickable = row is DesktopSettingRow.Options ||
        (row is DesktopSettingRow.Entry && row.clickable)
    val base = when {
        pressed && rowClickable -> palette.pressed
        hovered && rowClickable -> palette.hover
        else -> Color.Transparent
    }
    val bg by animateColorAsState(base, tween(150), label = "rowBg")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .then(if (focused && rowClickable) Modifier.border(2.dp, palette.focus, RoundedCornerShape(8.dp)) else Modifier)
            .hoverable(interaction)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = rowClickable,
                onClick = {
                    when (row) {
                        is DesktopSettingRow.Options -> expanded = true
                        is DesktopSettingRow.Entry -> row.onOpen()
                        else -> Unit
                    }
                },
            )
            .graphicsLayer(scaleX = if (pressed && rowClickable) 0.96f else 1f, scaleY = if (pressed && rowClickable) 0.96f else 1f)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconBlock(icon = dialogRowIcon(row), color = color, palette = palette)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.title,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = if (row is DesktopSettingRow.Absent) palette.text3 else palette.text1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // 副文只留短说明：长 caption 上屏会压扁行高，也不符合短文案约束。
            val caption = row.caption?.takeIf { it.length <= 15 }
            if (caption != null) {
                Text(
                    text = caption,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = palette.text2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box {
            DialogRowControl(
                row = row,
                palette = palette,
                expanded = expanded,
                onExpandedChange = { expanded = it },
            )
        }
    }
}

/** 按设置项语义优先匹配图标，未命中的再按控件类型兜底，避免满屏同质图标。 */
private fun dialogRowIcon(row: DesktopSettingRow): ImageVector {
    val t = row.title.lowercase()
    return when {
        "译文" in t || "translate" in t -> Icons.Outlined.Translate
        "语言" in t || "language" in t -> Icons.Outlined.Language
        "错误" in t || "弹窗" in t || "toast" in t || "间隔" in t || "延迟" in t || "周期" in t -> Icons.Outlined.Schedule
        "主题" in t || "皮肤" in t || "theme" in t || "skin" in t || "预设" in t -> Icons.Outlined.Palette
        "模糊" in t || "blur" in t || "环境光" in t || "落影" in t -> Icons.Outlined.Layers
        "桌宠" in t || "吉祥物" in t || "mascot" in t -> Icons.Outlined.EmojiEmotions
        "缩放" in t || "scale" in t -> Icons.Outlined.ZoomIn
        "画幅" in t || "尺寸" in t || "大小" in t || "圆角" in t || "内衬" in t -> Icons.Outlined.FitScreen
        "布局" in t || "网格" in t || "密度" in t -> Icons.Outlined.GridView
        "清晰度" in t || "画质" in t || "原图" in t || "格式" in t || "预加载" in t || "提前加载" in t -> Icons.Outlined.Image
        "详情栏" in t -> Icons.Outlined.ViewAgenda
        "ai" in t || "状态模式" in t || "打盹" in t -> Icons.Outlined.AutoAwesome
        "屏蔽" in t || "分级" in t || "规则" in t -> Icons.Outlined.FilterList
        "视线" in t || "显示" in t || "透明度" in t -> Icons.Outlined.Visibility
        "呼吸" in t || "测速" in t -> Icons.Outlined.Sync
        "阅读" in t || "章节" in t -> Icons.Outlined.MenuBook
        "视频" in t || "动图" in t || "自动播放" in t -> Icons.Outlined.Movie
        "播放" in t -> Icons.Outlined.PlayArrow
        "快捷收藏" in t -> Icons.Outlined.StarBorder
        "收藏" in t && "排序" !in t -> Icons.Outlined.BookmarkBorder
        "排序" in t -> Icons.Outlined.FormatListBulleted
        "账号" in t || "登录" in t || "pixiv" in t || "gelbooru" in t || "saucenao" in t -> Icons.Outlined.AccountCircle
        "目录" in t || "路径" in t -> Icons.Outlined.Folder
        "下载" in t || "保存" in t -> Icons.Outlined.Download
        "清理" in t || "清除" in t -> Icons.Outlined.DeleteOutline
        "缓存" in t -> Icons.Outlined.Storage
        "备份" in t || "还原" in t || "导出" in t -> Icons.Outlined.Save
        "历史" in t -> Icons.Outlined.History
        "统计" in t -> Icons.Outlined.BarChart
        "代理" in t || "镜像" in t || "网络" in t -> Icons.Outlined.Public
        "线程" in t -> Icons.Outlined.Tune
        "协议" in t || "主页" in t || "发布" in t || "链接" in t -> Icons.Outlined.Link
        "更新" in t -> Icons.Outlined.CloudDownload
        "版本" in t -> Icons.Outlined.Info
        "日志" in t -> Icons.Outlined.Description
        "视图" in t || "窗口" in t || "特效" in t || "桌面" in t -> Icons.Outlined.Laptop
        "重置" in t -> Icons.Outlined.Refresh
        else -> when (row) {
            is DesktopSettingRow.Toggle -> Icons.Filled.Check
            is DesktopSettingRow.Options -> Icons.Outlined.Tune
            is DesktopSettingRow.Entry -> Icons.Outlined.ChevronRight
            is DesktopSettingRow.Action -> Icons.AutoMirrored.Outlined.OpenInNew
            is DesktopSettingRow.Text -> Icons.Outlined.Description
            is DesktopSettingRow.Absent -> Icons.Outlined.Block
            is DesktopSettingRow.Slider -> Icons.Outlined.Tune
        }
    }
}

/** 行右控件槽，六类行各摆各的把手。 */
@Composable
private fun DialogRowControl(
    row: DesktopSettingRow,
    palette: SettingsDialogPalette,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    when (row) {
        is DesktopSettingRow.Toggle -> DialogSwitch(
            checked = row.checked,
            enabled = row.enabled,
            palette = palette,
            onToggle = row.onToggle,
        )
        is DesktopSettingRow.Options -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = row.options.getOrElse(row.selectedIndex) { "" },
                    fontSize = 12.sp,
                    color = palette.text2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 240.dp),
                )
                Text("›", fontSize = 14.sp, color = palette.text3)
            }
            if (expanded) {
                DialogOptionsPopup(
                    options = row.options,
                    selectedIndex = row.selectedIndex,
                    palette = palette,
                    onSelect = { index ->
                        onExpandedChange(false)
                        row.onSelect(index)
                    },
                    onDismiss = { onExpandedChange(false) },
                )
            }
        }
        is DesktopSettingRow.Entry -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (row.value != null) {
                Text(
                    text = row.value,
                    fontSize = 12.sp,
                    color = palette.text2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 260.dp),
                )
            }
            if (row.clickable) {
                Text("›", fontSize = 14.sp, color = palette.text3)
            }
        }
        is DesktopSettingRow.Action -> Text(
            text = row.buttonLabel,
            fontSize = 12.sp,
            color = if (row.enabled) (row.buttonColor ?: palette.iconColors[2]) else palette.text3,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = row.enabled, onClick = row.onAction)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
        is DesktopSettingRow.Text -> DialogTextControl(row = row, palette = palette)
        is DesktopSettingRow.Slider -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            androidx.compose.material3.Slider(
                value = row.value,
                onValueChange = row.onChange,
                valueRange = row.range,
                steps = row.steps,
                modifier = Modifier.width(180.dp),
            )
            Text(
                text = row.display(row.value),
                fontSize = 11.sp,
                color = palette.text3,
            )
        }
        is DesktopSettingRow.Absent -> Text(
            text = "不可用",
            fontSize = 11.sp,
            color = palette.text3,
            modifier = Modifier
                .border(1.dp, palette.text3.copy(alpha = 0.3f), RoundedCornerShape(5.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** 选项浮层：向上展开，避开下一行遮挡。 */
@Composable
private fun DialogOptionsPopup(
    options: List<String>,
    selectedIndex: Int,
    palette: SettingsDialogPalette,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Popup(
        alignment = Alignment.BottomEnd,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                // 菜单项 fillMaxWidth 会顺着 Popup 的整窗约束撑满横条；收成最宽一项的内容宽。
                .width(IntrinsicSize.Min)
                .widthIn(min = 160.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(palette.menuBg)
                .border(1.dp, palette.dividerStrong, RoundedCornerShape(8.dp))
                .padding(4.dp),
        ) {
            options.forEachIndexed { index, option ->
                val selected = index == selectedIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (selected) palette.selected else Color.Transparent)
                        .clickable { onSelect(index) }
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(option, fontSize = 12.sp, color = palette.text1, maxLines = 1)
                    if (selected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = palette.focus,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 行内文本输入：停顿 700ms 或离场时提交未保存修改，不另摆保存钮。 */
@Composable
private fun DialogTextControl(row: DesktopSettingRow.Text, palette: SettingsDialogPalette) {
    var text by remember(row.value) { mutableStateOf(row.value) }
    val latestText by rememberUpdatedState(text)
    val latestInitial by rememberUpdatedState(row.value)
    val latestOnCommit by rememberUpdatedState(row.onCommit)
    LaunchedEffect(text) {
        delay(700)
        if (text.trim() != row.value) row.onCommit(text.trim())
    }
    DisposableEffect(Unit) {
        onDispose {
            val trimmed = latestText.trim()
            if (trimmed != latestInitial) latestOnCommit(trimmed)
        }
    }
    com.venera.desktop.ui.components.DesktopInputField(
        value = text,
        onValueChange = { text = it },
        modifier = Modifier.width(180.dp),
        colors = com.venera.desktop.ui.components.DesktopSearchFieldColors(
            fieldBg = palette.field,
            fieldBgFocused = palette.field,
            fieldBorder = palette.fieldBorder,
            fieldBorderHover = palette.focus,
            fieldBorderFocus = palette.focus,
            iconTint = palette.text2,
            textColor = palette.text1,
            placeholderColor = palette.text3,
            hintColor = palette.text2,
            cursor = palette.focus,
        ),
    )
}

/** 设置弹框开关，已收编至全仓标准 `DesktopSwitch` 几何，仅透传弹框色板。 */
@Composable
private fun DialogSwitch(
    checked: Boolean,
    enabled: Boolean,
    palette: SettingsDialogPalette,
    onToggle: (Boolean) -> Unit,
) {
    com.venera.desktop.ui.DesktopSwitch(
        checked = checked,
        onCheckedChange = onToggle,
        enabled = enabled,
        colors = com.venera.desktop.ui.DesktopSwitchColors(
            trackChecked = palette.switchOn,
            trackUnchecked = palette.switchOff,
            borderUnchecked = palette.divider,
            borderUncheckedHover = palette.text2.copy(alpha = 0.45f),
            thumb = palette.onAccent,
        ),
    )
}
