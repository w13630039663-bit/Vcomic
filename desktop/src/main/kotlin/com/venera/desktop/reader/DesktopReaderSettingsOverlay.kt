package com.venera.desktop.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.gallery.data.DesktopComicPage
import com.venera.desktop.gallery.data.DesktopReaderMode
import com.venera.desktop.ui.theme.VeneraType
import kotlin.math.roundToInt

/**
 * 阅读设置悬浮卡片（对齐稿 §1/§2：600dp 四段 tab 壳）。
 *
 * 四段：电子书（空态卡）/ 页（18 条目）/ 筛选 / AI（三功能区）。
 * 顶栏四段钮经 [initialTab] 定位落段；段内全部条目吃真 prefs 键。
 *
 * @param initialTab 打开时定位的段（0=电子书 1=页 2=筛选 3=AI）。
 */
@Composable
internal fun ReaderSettingsOverlay(
    visible: Boolean,
    initialTab: Int,
    fit: ReaderFit,
    backgroundIndex: Int,
    prefs: DesktopReaderPrefsState,
    autoPlay: Boolean,
    /** 是否落在垂直连续档：决定条漫专属排版项是否可用。 */
    verticalOnly: Boolean,
    /** 切档回调；null = 调用方不给切档入口，条漫条目退成只读展示。 */
    onPickMode: ((DesktopReaderMode) -> Unit)?,
    onPickFit: (ReaderFit) -> Unit,
    onPickBackground: (Int) -> Unit,
    onUpdatePrefs: ((DesktopReaderPrefsState) -> DesktopReaderPrefsState) -> Unit,
    onResetFilters: () -> Unit,
    presets: List<ReaderFilterPreset>,
    onSavePreset: () -> Unit,
    onApplyPreset: (Int) -> Unit,
    onToggleAutoPlay: (Boolean) -> Unit,
    /** 切换当前漫画是否用独立配置；null = 没拿到书键，标签退成只读展示。 */
    onToggleBookCustomized: ((Boolean) -> Unit)?,
    /** 当前页模型（AI 对比图 before 格吃原图）。 */
    currentPage: DesktopComicPage? = null,
    /** 过当前参数组管线后的页模型（after 格；null=未开任何 AI 开关）。 */
    aiPageForComparison: DesktopAiPage? = null,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    var tab by remember(visible) { mutableStateOf(initialTab.coerceIn(SETTINGS_TAB_PAGE, SETTINGS_TAB_AI)) }
    // 顶栏切换段位时跟随定位（visible 不变只变 initialTab 的那一路）。
    LaunchedEffect(initialTab, visible) {
        if (visible) tab = initialTab.coerceIn(SETTINGS_TAB_PAGE, SETTINGS_TAB_AI)
    }
    var showPerspectiveDialog by remember { mutableStateOf(false) }
    var showGuidedViewDialog by remember { mutableStateOf(false) }
    var showSoundDialog by remember { mutableStateOf(false) }
    var showShadowDialog by remember { mutableStateOf(false) }
    AnimatedVisibility(
        visible = visible,
        // 从设置按钮正下方展开：原先从右侧滑进来，落点离那颗按钮太远，看着像另一个面板。
        enter = fadeIn(tween(HUD_ENTER_MS)) + slideInVertically(tween(HUD_ENTER_MS)) { -it / 4 },
        exit = fadeOut(tween(HUD_EXIT_MS)) + slideOutVertically(tween(HUD_EXIT_MS)) { -it / 4 },
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .padding(top = TOP_BAR_HEIGHT + 8.dp, end = 12.dp, bottom = 84.dp)
                .width(600.dp)
                .heightIn(max = 640.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(HudSurface)
                .border(1.dp, HudBorder, RoundedCornerShape(16.dp))
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        com.venera.desktop.settings.DesktopI18n.t("页", "Page") to SETTINGS_TAB_PAGE,
                        com.venera.desktop.settings.DesktopI18n.t("筛选", "Filter") to SETTINGS_TAB_FILTER,
                        "AI" to SETTINGS_TAB_AI,
                    ).forEach { (label, idx) ->
                        SettingsTabChip(
                            label = label,
                            selected = tab == idx,
                            onClick = { tab = idx },
                        )
                    }
                }
                // 本作独立设置开关：只有拿到书键时才可点，否则 toggleBookCustomized 会直接返回。
                Text(
                    text = if (prefs.isBookCustomized) com.venera.desktop.settings.DesktopI18n.t("本作独立", "Per-book") else com.venera.desktop.settings.DesktopI18n.t("跟随全局", "Global"),
                    fontSize = VeneraType.micro.size,
                    color = if (prefs.isBookCustomized) tokens.color.primary else HudTextDim,
                    modifier = if (onToggleBookCustomized != null) {
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onToggleBookCustomized.invoke(!prefs.isBookCustomized) }
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    } else {
                        Modifier
                    },
                )
            }
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                when (tab) {
                    SETTINGS_TAB_EBOOK -> SettingsEbookEmptyTab()
                    SETTINGS_TAB_PAGE -> SettingsPageTab(
                        prefs = prefs,
                        autoPlay = autoPlay,
                        backgroundIndex = backgroundIndex,
                        fit = fit,
                        verticalOnly = verticalOnly,
                        onPickMode = onPickMode,
                        onUpdatePrefs = onUpdatePrefs,
                        onPickFit = onPickFit,
                        onPickBackground = onPickBackground,
                        onToggleAutoPlay = onToggleAutoPlay,
                        onOpenPerspective = { showPerspectiveDialog = true },
                        onOpenGuidedView = { showGuidedViewDialog = true },
                        onOpenSound = { showSoundDialog = true },
                        onOpenShadow = { showShadowDialog = true },
                    )
                    SETTINGS_TAB_FILTER -> SettingsFilterTab(
                        prefs = prefs,
                        onUpdatePrefs = onUpdatePrefs,
                        onResetFilters = onResetFilters,
                        presets = presets,
                        onSavePreset = onSavePreset,
                        onApplyPreset = onApplyPreset,
                    )
                    SETTINGS_TAB_AI -> SettingsAiTab(
                        prefs = prefs,
                        onUpdatePrefs = onUpdatePrefs,
                        beforePage = currentPage,
                        afterPage = aiPageForComparison,
                    )
                }
            }
        }
    }

    ReaderTurnPerspectiveDialog(
        visible = showPerspectiveDialog,
        perspective = prefs.turnPerspective,
        onPerspectiveChange = { p -> onUpdatePrefs { it.copy(turnPerspective = p) } },
        onDismiss = { showPerspectiveDialog = false },
    )
    ReaderGuidedViewConfigDialog(
        visible = showGuidedViewDialog,
        style = prefs.guidedViewStyle,
        padding = prefs.guidedViewPadding,
        maxZoom = prefs.guidedViewMaxZoom,
        maskStyle = prefs.guidedViewMaskStyle,
        showFirst = prefs.guidedViewShowFullFirst,
        showLast = prefs.guidedViewShowFullLast,
        onUpdate = onUpdatePrefs,
        onDismiss = { showGuidedViewDialog = false },
    )
    ReaderSoundConfigDialog(
        visible = showSoundDialog,
        volume = prefs.soundVolume,
        onVolumeChange = { v -> onUpdatePrefs { it.copy(soundVolume = v) } },
        onDismiss = { showSoundDialog = false },
    )
    ReaderShadowConfigDialog(
        visible = showShadowDialog,
        prefs = prefs,
        onUpdatePrefs = onUpdatePrefs,
        onDismiss = { showShadowDialog = false },
    )
}

/** 电子书 tab：空态卡两行文案（规格：不放假开关）。 */
@Composable
internal fun SettingsEbookEmptyTab() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = com.venera.desktop.settings.DesktopI18n.t("暂无电子书内容", "No ebook content"),
                fontSize = VeneraType.body.size,
                fontWeight = FontWeight.Medium,
                color = HudTextPrimary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = com.venera.desktop.settings.DesktopI18n.t("支持 PDF / EPUB 后解锁提取", "Extraction available when PDF / EPUB is supported"),
                fontSize = VeneraType.aux.size,
                color = HudTextDim,
            )
        }
    }
}

/** 页 tab：规格 §2 的 18 条目，分组 1dp 分隔线。 */
@Composable
internal fun SettingsPageTab(
    prefs: DesktopReaderPrefsState,
    autoPlay: Boolean,
    backgroundIndex: Int,
    fit: ReaderFit,
    verticalOnly: Boolean,
    onPickMode: ((DesktopReaderMode) -> Unit)?,
    onUpdatePrefs: ((DesktopReaderPrefsState) -> DesktopReaderPrefsState) -> Unit,
    onPickFit: (ReaderFit) -> Unit,
    onPickBackground: (Int) -> Unit,
    onToggleAutoPlay: (Boolean) -> Unit,
    onOpenPerspective: () -> Unit,
    onOpenGuidedView: () -> Unit,
    onOpenSound: () -> Unit,
    onOpenShadow: () -> Unit,
) {
    val tokens = VeneraTokens
    SettingsSectionGap()
    // 组 A：模式 3 卡片网格 + 翻页音效
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ModeCard(com.venera.desktop.settings.DesktopI18n.t("导览视图", "Guided View"), prefs.turnEffect == 2, { onUpdatePrefs { it.copy(turnEffect = 2) } }, Modifier.weight(1f), gear = onOpenGuidedView)
        ModeCard(com.venera.desktop.settings.DesktopI18n.t("淡出", "Fade"), prefs.turnEffect == 3, { onUpdatePrefs { it.copy(turnEffect = 3) } }, Modifier.weight(1f))
        ModeCard(com.venera.desktop.settings.DesktopI18n.t("翻页", "Page Turn"), prefs.turnEffect == 4, { onUpdatePrefs { it.copy(turnEffect = 4) } }, Modifier.weight(1f), gear = onOpenPerspective)
    }
    SettingsToggleRow(
        label = com.venera.desktop.settings.DesktopI18n.t("翻页音效", "Page sound"),
        checked = prefs.pageSoundOn,
        onChange = { v -> onUpdatePrefs { it.copy(pageSoundOn = v) } },
        gear = onOpenSound,
    )
    SettingsSectionGap()
    // 组 B：方向
    SettingsToggleRow(com.venera.desktop.settings.DesktopI18n.t("倒序", "Reverse order"), prefs.reverseReading, { v -> onUpdatePrefs { it.copy(reverseReading = v) } })
    // 条漫不另存开关：勾上=切到垂直连续档，取消=回单页左至右，方向与版式只由档位说。
    SettingsToggleRow(
        label = com.venera.desktop.settings.DesktopI18n.t("条漫", "Webtoon strip"),
        checked = verticalOnly,
        onChange = { v ->
            onPickMode?.invoke(
                if (v) DesktopReaderMode.VERTICAL_CONTINUOUS else DesktopReaderMode.HORIZONTAL_LTR,
            )
        },
        enabled = onPickMode != null,
    )
    SettingsSectionGap()
    // 组 C：双页
    // 只是降级闸：对开档下关掉=每次一页；其它档位本来就是一页，勾上不升级档位。
    SettingsToggleRow(com.venera.desktop.settings.DesktopI18n.t("双页并排", "Double page"), prefs.doublePageOn, { v -> onUpdatePrefs { it.copy(doublePageOn = v) } })
    SettingsToggleRow(
        label = com.venera.desktop.settings.DesktopI18n.t("阴影效果", "Page shadow"),
        checked = prefs.shadowOn,
        onChange = { v -> onUpdatePrefs { it.copy(shadowOn = v) } },
        gear = onOpenShadow,
    )
    SettingsToggleRow(com.venera.desktop.settings.DesktopI18n.t("不用于横幅画面", "Skip wide pages"), prefs.keepWideSingle, { v -> onUpdatePrefs { it.copy(keepWideSingle = v) } })
    SettingsToggleRow(com.venera.desktop.settings.DesktopI18n.t("与下一个水平对齐", "Align next horizontal"), prefs.alignNextHorizontal, { v -> onUpdatePrefs { it.copy(alignNextHorizontal = v) } })
    SettingsToggleRow(com.venera.desktop.settings.DesktopI18n.t("开头空白页", "Blank cover page"), prefs.blankPage, { v -> onUpdatePrefs { it.copy(blankPage = v) } })
    SettingsSectionGap()
    // 组 D：缩放上限。适配档位本身由上面那排「画面适配」管，不另设总闸。
    SettingsToggleRow(com.venera.desktop.settings.DesktopI18n.t("勿放大超原始", "Do not upscale"), prefs.noEnlarge, { v -> onUpdatePrefs { it.copy(noEnlarge = v) } })
    SettingsToggleRow(
        label = com.venera.desktop.settings.DesktopI18n.t("每次只显示一页", "One page at a time"),
        checked = prefs.singleInScroll,
        onChange = { v -> onUpdatePrefs { it.copy(singleInScroll = v) } },
        enabled = verticalOnly,
    )
    SettingsSectionGap()
    // 组 E：旋转（三 chip，再点当前档复位）
    ChipGroupRow(label = com.venera.desktop.settings.DesktopI18n.t("页面旋转", "Page rotation")) {
        listOf(
            com.venera.desktop.settings.DesktopI18n.t("向左旋转", "Rotate left") to 1,
            com.venera.desktop.settings.DesktopI18n.t("向右旋转", "Rotate right") to 2,
            com.venera.desktop.settings.DesktopI18n.t("倒转", "Flip 180°") to 3,
        ).forEach { (label, slot) ->
            val selected = prefs.rotationIndex == slot
            HudModeChip(
                label = label,
                selected = selected,
                onClick = { onUpdatePrefs { it.copy(rotationIndex = if (selected) 0 else slot) } },
            )
        }
    }
    ChipGroupRow(label = com.venera.desktop.settings.DesktopI18n.t("横图旋转", "Landscape rotation")) {
        listOf(
            com.venera.desktop.settings.DesktopI18n.t("向左旋转", "Rotate left") to 1,
            com.venera.desktop.settings.DesktopI18n.t("向右旋转", "Rotate right") to 2,
            com.venera.desktop.settings.DesktopI18n.t("倒转", "Flip 180°") to 3,
        ).forEach { (label, slot) ->
            val selected = prefs.rotateH == slot
            HudModeChip(
                label = label,
                selected = selected,
                onClick = { onUpdatePrefs { it.copy(rotateH = if (selected) 0 else slot) } },
            )
        }
    }
    SettingsSectionGap()
    // 组 F：边距
    SettingsSliderRow(
        label = com.venera.desktop.settings.DesktopI18n.t("水平边距", "Horizontal margin"),
        value = prefs.marginH.toFloat(),
        valueRange = 0f..200f,
        step = 1f,
        display = "${prefs.marginH} dp",
        onChange = { v -> onUpdatePrefs { it.copy(marginH = v.roundToInt().coerceIn(0, 200)) } },
    )
    SettingsSliderRow(
        label = com.venera.desktop.settings.DesktopI18n.t("垂直边距", "Vertical margin"),
        value = prefs.marginV.toFloat(),
        valueRange = 0f..200f,
        step = 1f,
        display = "${prefs.marginV} dp",
        enabled = !verticalOnly,
        onChange = { v -> onUpdatePrefs { it.copy(marginV = v.roundToInt().coerceIn(0, 200)) } },
    )
    SettingsSectionGap()
    // 组 G：动画
    SettingsSliderRow(
        label = com.venera.desktop.settings.DesktopI18n.t("翻页动画速度", "Turn animation speed"),
        value = prefs.turnAnimSeconds,
        valueRange = 0.1f..4f,
        step = 0.05f,
        display = String.format("%.2fs", prefs.turnAnimSeconds),
        onChange = { v ->
            val snapped = (v / 0.05f).roundToInt() * 0.05f
            onUpdatePrefs { it.copy(turnAnimSeconds = snapped.coerceIn(0.1f, 4f)) }
        },
    )
    SettingsSectionGap()
    // 保留既有入口：适配 / 背景 / 预载 / 热区 / 插值 / 方向键 / 自动翻页
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(com.venera.desktop.settings.DesktopI18n.t("画面适配", "Image fit"), fontSize = VeneraType.aux.size, color = HudTextDim)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ReaderFit.entries.forEach { item ->
                HudModeChip(label = item.label, selected = fit == item, onClick = { onPickFit(item) })
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(com.venera.desktop.settings.DesktopI18n.t("画布背景", "Background"), fontSize = VeneraType.aux.size, color = HudTextDim)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                com.venera.desktop.settings.DesktopI18n.t("纯黑", "Black") to 0,
                com.venera.desktop.settings.DesktopI18n.t("深灰", "Dark gray") to 1,
                com.venera.desktop.settings.DesktopI18n.t("暖纸", "Warm paper") to 2,
            ).forEach { (label, idx) ->
                HudModeChip(label = label, selected = backgroundIndex == idx, onClick = { onPickBackground(idx) })
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(com.venera.desktop.settings.DesktopI18n.t("向后预加载页数", "Preload pages"), fontSize = VeneraType.aux.size, color = HudTextDim)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(2, 4, 8).forEach { count ->
                HudModeChip(
                    label = com.venera.desktop.settings.DesktopI18n.t("${count} 页", "$count pages"),
                    selected = prefs.preloadCount == count,
                    onClick = { onUpdatePrefs { it.copy(preloadCount = count) } },
                )
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(com.venera.desktop.settings.DesktopI18n.t("点击翻页区", "Tap turn zone"), fontSize = VeneraType.aux.size, color = HudTextDim)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(15, 25, 33).forEach { pct ->
                HudModeChip(
                    label = "$pct%",
                    selected = prefs.tapZonePercent == pct,
                    onClick = { onUpdatePrefs { it.copy(tapZonePercent = pct) } },
                )
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(com.venera.desktop.settings.DesktopI18n.t("图像缩放插值质量", "Scaling quality"), fontSize = VeneraType.aux.size, color = HudTextDim)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                com.venera.desktop.settings.DesktopI18n.t("平滑", "Smooth") to 0,
                com.venera.desktop.settings.DesktopI18n.t("高清", "High") to 1,
                com.venera.desktop.settings.DesktopI18n.t("像素近邻", "Pixel") to 2,
            ).forEach { (label, idx) ->
                HudModeChip(
                    label = label,
                    selected = prefs.filterQualityIndex == idx,
                    onClick = { onUpdatePrefs { it.copy(filterQualityIndex = idx) } },
                )
            }
        }
    }
    SettingsToggleRow(com.venera.desktop.settings.DesktopI18n.t("边缘拖拽跟随翻页", "Drag edge to turn"), prefs.dragTurnOn, { v -> onUpdatePrefs { it.copy(dragTurnOn = v) } })
    SettingsToggleRow(com.venera.desktop.settings.DesktopI18n.t("方向键翻页", "Arrow keys to turn"), prefs.arrowKeysOn, { v -> onUpdatePrefs { it.copy(arrowKeysOn = v) } })
    SettingsToggleRow(com.venera.desktop.settings.DesktopI18n.t("自动翻页", "Auto-turn"), autoPlay, onToggleAutoPlay)
    if (autoPlay) {
        SettingsSliderRow(
            label = com.venera.desktop.settings.DesktopI18n.t("间隔", "Interval"),
            value = prefs.autoPlaySecs.toFloat(),
            valueRange = 2f..15f,
            step = 1f,
            display = "${prefs.autoPlaySecs}s",
            onChange = { v -> onUpdatePrefs { it.copy(autoPlaySecs = v.roundToInt().coerceIn(2, 15)) } },
        )
    }
    SettingsSectionGap()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(com.venera.desktop.settings.DesktopI18n.t("条漫版芯限宽 ${prefs.stripMaxWidthPercent}%", "Strip max width ${prefs.stripMaxWidthPercent}%"), fontSize = VeneraType.aux.size, color = HudTextDim)
        Slider(
            value = prefs.stripMaxWidthPercent.toFloat(),
            onValueChange = { v -> onUpdatePrefs { it.copy(stripMaxWidthPercent = v.toInt().coerceIn(50, 100)) } },
            valueRange = 50f..100f,
            colors = SliderDefaults.colors(thumbColor = tokens.color.primary, activeTrackColor = tokens.color.primary),
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(com.venera.desktop.settings.DesktopI18n.t("条漫页间距 ${prefs.stripPageGap}px", "Strip page gap ${prefs.stripPageGap}px"), fontSize = VeneraType.aux.size, color = HudTextDim)
        Slider(
            value = prefs.stripPageGap.toFloat(),
            onValueChange = { v -> onUpdatePrefs { it.copy(stripPageGap = v.toInt().coerceIn(0, 32)) } },
            valueRange = 0f..32f,
            colors = SliderDefaults.colors(thumbColor = tokens.color.primary, activeTrackColor = tokens.color.primary),
        )
    }
}

/** 筛选 tab：规格 §3 —— 预设 chips + 0–400% 三滑条 + 色温/色相滑条 + 黑白/负片开关。 */
@Composable
internal fun SettingsFilterTab(
    prefs: DesktopReaderPrefsState,
    onUpdatePrefs: ((DesktopReaderPrefsState) -> DesktopReaderPrefsState) -> Unit,
    onResetFilters: () -> Unit,
    presets: List<ReaderFilterPreset>,
    onSavePreset: () -> Unit,
    onApplyPreset: (Int) -> Unit,
) {
    val tokens = VeneraTokens
    SettingsSectionGap()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(com.venera.desktop.settings.DesktopI18n.t("滤镜预设", "Filter presets"), fontSize = VeneraType.aux.size, color = HudTextDim)
        Text(
            text = com.venera.desktop.settings.DesktopI18n.t("重置", "Reset"),
            fontSize = VeneraType.aux.size,
            color = tokens.color.primary,
            modifier = Modifier.clickable(onClick = onResetFilters),
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        presets.forEachIndexed { i, preset ->
            HudModeChip(label = preset.name, selected = false, onClick = { onApplyPreset(i) })
        }
        HudModeChip(label = com.venera.desktop.settings.DesktopI18n.t("存为预设", "Save preset"), selected = false, onClick = onSavePreset)
    }
    SettingsSectionGap()
    // 0–400% 显示口径；prefs 内部 0–4f（1=100%）。规格：亮度/对比度/饱和度。
    SettingsSliderRow(
        label = com.venera.desktop.settings.DesktopI18n.t("亮度", "Brightness"),
        value = prefs.brightness,
        valueRange = 0f..4f,
        step = 0.05f,
        display = "${(prefs.brightness * 100).roundToInt()}%",
        onChange = { v -> onUpdatePrefs { it.copy(brightness = (v / 0.05f).roundToInt() * 0.05f) } },
    )
    SettingsSliderRow(
        label = com.venera.desktop.settings.DesktopI18n.t("饱和度", "Saturation"),
        value = prefs.saturation,
        valueRange = 0f..4f,
        step = 0.05f,
        display = "${(prefs.saturation * 100).roundToInt()}%",
        onChange = { v -> onUpdatePrefs { it.copy(saturation = (v / 0.05f).roundToInt() * 0.05f) } },
    )
    SettingsSliderRow(
        label = com.venera.desktop.settings.DesktopI18n.t("对比度", "Contrast"),
        value = prefs.contrast,
        valueRange = 0f..4f,
        step = 0.05f,
        display = "${(prefs.contrast * 100).roundToInt()}%",
        onChange = { v -> onUpdatePrefs { it.copy(contrast = (v / 0.05f).roundToInt() * 0.05f) } },
    )
    SettingsSliderRow(
        label = com.venera.desktop.settings.DesktopI18n.t("色温", "Warmth"),
        value = prefs.filterSepia,
        valueRange = 0f..100f,
        step = 1f,
        display = "${prefs.filterSepia.roundToInt()}",
        onChange = { v -> onUpdatePrefs { it.copy(filterSepia = v.roundToInt().toFloat()) } },
    )
    SettingsSliderRow(
        label = com.venera.desktop.settings.DesktopI18n.t("色相旋转", "Hue"),
        value = prefs.filterHue,
        valueRange = 0f..360f,
        step = 1f,
        display = "${prefs.filterHue.roundToInt()}°",
        onChange = { v -> onUpdatePrefs { it.copy(filterHue = v.roundToInt().toFloat()) } },
    )
    SettingsToggleRow(com.venera.desktop.settings.DesktopI18n.t("黑白", "Grayscale"), prefs.filterInvert, { v -> onUpdatePrefs { it.copy(filterInvert = v) } })
    SettingsToggleRow(com.venera.desktop.settings.DesktopI18n.t("负片", "Invert"), prefs.filterNegative, { v -> onUpdatePrefs { it.copy(filterNegative = v) } })
}

/** AI tab：规格 §4 三功能区卡（痕迹去除 / 去网纹 / 增强），全部纯本地算法。 */
@Composable
internal fun SettingsAiTab(
    prefs: DesktopReaderPrefsState,
    onUpdatePrefs: ((DesktopReaderPrefsState) -> DesktopReaderPrefsState) -> Unit,
    beforePage: DesktopComicPage?,
    afterPage: DesktopAiPage?,
) {
    SettingsSectionGap()
    // 对比图：当前页中央取两格实时渲染（before=原图 / after=过管线），规格 §AI tab。
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ComparisonCell(label = com.venera.desktop.settings.DesktopI18n.t("原图", "Original"), page = beforePage, modifier = Modifier.weight(1f))
        ComparisonCell(label = com.venera.desktop.settings.DesktopI18n.t("处理后", "Processed"), page = afterPage, modifier = Modifier.weight(1f))
    }
    Spacer(Modifier.height(8.dp))
    AiFunctionCard(
        title = com.venera.desktop.settings.DesktopI18n.t("AI 痕迹去除", "AI Artifact Removal"),
        on = prefs.aiArtifact,
        onToggle = { v -> onUpdatePrefs { it.copy(aiArtifact = v) } },
    ) {
        DropdownBar(
            label = com.venera.desktop.settings.DesktopI18n.t("模型", "Model"),
            value = when (prefs.aiArtifactModel) {
                0 -> com.venera.desktop.settings.DesktopI18n.t("快速 v2", "Fast v2")
                2 -> com.venera.desktop.settings.DesktopI18n.t("质量 v3", "Quality v3")
                else -> com.venera.desktop.settings.DesktopI18n.t("均衡 v3", "Balanced v3")
            },
            options = listOf(
                com.venera.desktop.settings.DesktopI18n.t("快速 v2", "Fast v2") to 0,
                com.venera.desktop.settings.DesktopI18n.t("均衡 v3", "Balanced v3") to 1,
                com.venera.desktop.settings.DesktopI18n.t("质量 v3", "Quality v3") to 2,
            ),
            onPick = { v -> onUpdatePrefs { it.copy(aiArtifactModel = v) } },
        )
        SettingsToggleRow(com.venera.desktop.settings.DesktopI18n.t("保持色调平缓", "Preserve smooth tone"), prefs.aiArtifactTone, { v -> onUpdatePrefs { it.copy(aiArtifactTone = v) } })
        SettingsSliderRow(
            label = com.venera.desktop.settings.DesktopI18n.t("痕迹尺寸", "Artifact size"),
            value = prefs.aiArtifactSize,
            valueRange = 1f..10f,
            step = 0.1f,
            display = String.format("%.1f%%", prefs.aiArtifactSize),
            onChange = { v -> onUpdatePrefs { it.copy(aiArtifactSize = (v * 10).roundToInt() / 10f) } },
        )
    }
    AiFunctionCard(
        title = com.venera.desktop.settings.DesktopI18n.t("AI 去网纹", "AI Descreen"),
        on = prefs.aiDescreen,
        onToggle = { v -> onUpdatePrefs { it.copy(aiDescreen = v) } },
    ) {
        DropdownBar(
            label = com.venera.desktop.settings.DesktopI18n.t("模型", "Model"),
            value = if (prefs.aiDescreenModel == 0) com.venera.desktop.settings.DesktopI18n.t("快速 v4", "Fast v4") else com.venera.desktop.settings.DesktopI18n.t("硬核快速 v4", "Hardcore Fast v4"),
            options = listOf(
                com.venera.desktop.settings.DesktopI18n.t("快速 v4", "Fast v4") to 0,
                com.venera.desktop.settings.DesktopI18n.t("硬核快速 v4", "Hardcore Fast v4") to 1,
            ),
            onPick = { v -> onUpdatePrefs { it.copy(aiDescreenModel = v) } },
        )
        SettingsToggleRow(com.venera.desktop.settings.DesktopI18n.t("保持大网点", "Keep large dots"), prefs.aiDescreenKeep, { v -> onUpdatePrefs { it.copy(aiDescreenKeep = v) } })
        SettingsSliderRow(
            label = com.venera.desktop.settings.DesktopI18n.t("尺寸", "Size"),
            value = prefs.aiDescreenSize,
            valueRange = 1f..10f,
            step = 0.1f,
            display = String.format("%.1f%%", prefs.aiDescreenSize),
            onChange = { v -> onUpdatePrefs { it.copy(aiDescreenSize = (v * 10).roundToInt() / 10f) } },
        )
    }
    AiFunctionCard(
        title = com.venera.desktop.settings.DesktopI18n.t("AI 增强", "AI Upscale"),
        on = prefs.aiUpscale,
        onToggle = { v -> onUpdatePrefs { it.copy(aiUpscale = v) } },
    ) {
        DropdownBar(
            label = com.venera.desktop.settings.DesktopI18n.t("模型", "Model"),
            value = if (prefs.aiUpscaleModel == 0) com.venera.desktop.settings.DesktopI18n.t("快速 v3", "Fast v3") else com.venera.desktop.settings.DesktopI18n.t("质量 v3", "Quality v3"),
            options = listOf(
                com.venera.desktop.settings.DesktopI18n.t("快速 v3", "Fast v3") to 0,
                com.venera.desktop.settings.DesktopI18n.t("质量 v3", "Quality v3") to 1,
            ),
            onPick = { v -> onUpdatePrefs { it.copy(aiUpscaleModel = v) } },
        )
        SettingsSliderRow(
            label = com.venera.desktop.settings.DesktopI18n.t("最大输入分辨率", "Max input resolution"),
            value = prefs.aiUpscaleMaxmp,
            valueRange = 0.2f..50f,
            step = 0.1f,
            display = String.format("%.1fmpx", prefs.aiUpscaleMaxmp),
            onChange = { v -> onUpdatePrefs { it.copy(aiUpscaleMaxmp = (v * 10).roundToInt() / 10f) } },
        )
        SettingsToggleRow(com.venera.desktop.settings.DesktopI18n.t("自动比例", "Auto scale"), prefs.aiUpscaleAuto, { v -> onUpdatePrefs { it.copy(aiUpscaleAuto = v) } })
        if (!prefs.aiUpscaleAuto) {
            DropdownBar(
                label = com.venera.desktop.settings.DesktopI18n.t("比例", "Scale"),
                value = "${prefs.aiUpscaleScale}x",
                options = listOf("2x" to 2, "4x" to 4),
                onPick = { v -> onUpdatePrefs { it.copy(aiUpscaleScale = v) } },
            )
        }
    }
}

/** AI 对比图单格：240dp 高圆角框，吃页模型实时渲染（before=普通页 / after=AI 页）。 */
@Composable
internal fun ComparisonCell(label: String, page: Any?, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0x14FFFFFF))
                .border(1.dp, HudRowStroke, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (page != null) {
                AsyncImage(
                    model = page,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(com.venera.desktop.settings.DesktopI18n.t("无当前页", "No current page"), fontSize = VeneraType.micro.size, color = HudTextDim)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = VeneraType.micro.size, color = HudTextDim)
    }
}

/** AI 功能卡外壳：标题行 + 开关 + 展开后的控件区。 */
@Composable
internal fun AiFunctionCard(
    title: String,
    on: Boolean,
    onToggle: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x14FFFFFF))
            .border(1.dp, HudRowStroke, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, fontSize = VeneraType.body.size, fontWeight = FontWeight.Medium, color = HudTextPrimary)
            com.venera.desktop.ui.DesktopSwitch(
                checked = on,
                onCheckedChange = onToggle,
                colors = com.venera.desktop.ui.DesktopSwitchDefaults.hudDark(),
            )
        }
        if (on) {
            Spacer(Modifier.height(4.dp))
            content()
        }
    }
}

/** 下拉条（filled 灰底圆角全宽条）：上行灰标签 + 下行值 + 尾部箭头，点开弹菜单。 */
@Composable
internal fun DropdownBar(
    label: String,
    value: String,
    options: List<Pair<String, Int>>,
    onPick: (Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(HudDropdownBg)
                .clickable { open = true }
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(label, fontSize = VeneraType.micro.size, color = HudTextDim)
            Text(value, fontSize = VeneraType.aux.size, fontWeight = FontWeight.Medium, color = HudTextPrimary)
        }
        Text(
            text = "▾",
            fontSize = VeneraType.aux.size,
            color = HudTextDim,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp),
        )
        androidx.compose.material3.DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = HudDropdownBg,
        ) {
            options.forEach { (label, v) ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(label, fontSize = VeneraType.aux.size, color = HudTextPrimary) },
                    onClick = {
                        onPick(v)
                        open = false
                    },
                )
            }
        }
    }
}

/** 音效参数弹窗：音量滑条（prefs.soundVolume）。 */
@Composable
internal fun ReaderSoundConfigDialog(
    visible: Boolean,
    volume: Int,
    onVolumeChange: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    val tokens = VeneraTokens
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x88000000))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(320.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(HudSurface)
                .border(1.dp, HudBorder, RoundedCornerShape(16.dp))
                .clickable(enabled = false) {}
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(com.venera.desktop.settings.DesktopI18n.t("翻页音效", "Page sound"), fontSize = VeneraType.body.size, fontWeight = FontWeight.SemiBold, color = HudTextPrimary)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(com.venera.desktop.settings.DesktopI18n.t("音量", "Volume"), fontSize = VeneraType.aux.size, color = HudTextDim)
                Text("$volume", fontSize = VeneraType.aux.size, color = HudTextPrimary)
            }
            Slider(
                value = volume.toFloat(),
                onValueChange = { onVolumeChange(it.roundToInt().coerceIn(0, 100)) },
                valueRange = 0f..100f,
                colors = SliderDefaults.colors(thumbColor = tokens.color.primary, activeTrackColor = tokens.color.primary),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    text = com.venera.desktop.settings.DesktopI18n.t("关闭", "Close"),
                    fontSize = VeneraType.aux.size,
                    color = tokens.color.primary,
                    modifier = Modifier.clickable(onClick = onDismiss).padding(4.dp),
                )
            }
        }
    }
}

/** 阴影参数弹窗：宽度% / 黑度% / 偏移% 三滑条（prefs.shadow_*）。 */
@Composable
internal fun ReaderShadowConfigDialog(
    visible: Boolean,
    prefs: DesktopReaderPrefsState,
    onUpdatePrefs: ((DesktopReaderPrefsState) -> DesktopReaderPrefsState) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    val tokens = VeneraTokens
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x88000000))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(360.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(HudSurface)
                .border(1.dp, HudBorder, RoundedCornerShape(16.dp))
                .clickable(enabled = false) {}
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(com.venera.desktop.settings.DesktopI18n.t("阴影效果", "Page shadow"), fontSize = VeneraType.body.size, fontWeight = FontWeight.SemiBold, color = HudTextPrimary)
            SliderWithValue(com.venera.desktop.settings.DesktopI18n.t("宽度 %", "Width %"), prefs.shadowSize, 0f..100f) {
                v -> onUpdatePrefs { it.copy(shadowSize = (v * 10).roundToInt() / 10f) }
            }
            SliderWithValue(com.venera.desktop.settings.DesktopI18n.t("黑度 %", "Opacity %"), prefs.shadowOpacity, 0f..100f) {
                v -> onUpdatePrefs { it.copy(shadowOpacity = (v * 10).roundToInt() / 10f) }
            }
            SliderWithValue(com.venera.desktop.settings.DesktopI18n.t("位移 %", "Offset %"), prefs.shadowDisplace, -100f..100f) {
                v -> onUpdatePrefs { it.copy(shadowDisplace = (v * 10).roundToInt() / 10f) }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    text = com.venera.desktop.settings.DesktopI18n.t("关闭", "Close"),
                    fontSize = VeneraType.aux.size,
                    color = tokens.color.primary,
                    modifier = Modifier.clickable(onClick = onDismiss).padding(4.dp),
                )
            }
        }
    }
}

@Composable
internal fun SliderWithValue(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    val tokens = VeneraTokens
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = VeneraType.aux.size, color = HudTextDim)
            Text(String.format("%.1f", value), fontSize = VeneraType.aux.size, color = HudTextPrimary)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = tokens.color.primary, activeTrackColor = tokens.color.primary),
        )
    }
}

/** 快速跳页弹窗（按 G 键或点击底栏页码触发）。 */
@Composable
internal fun ReaderJumpPageDialog(
    visible: Boolean,
    currentPage: Int,
    totalPages: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    if (!visible || totalPages <= 0) return
    var input by remember(visible, currentPage) { mutableStateOf("${currentPage + 1}") }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x88000000))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(280.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(HudSurface)
                .border(1.dp, HudBorder, RoundedCornerShape(16.dp))
                .clickable(enabled = false, onClick = {})
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = com.venera.desktop.settings.DesktopI18n.t("跳转到页码（1 - $totalPages）", "Jump to page (1 - $totalPages)"),
                fontSize = VeneraType.body.size,
                fontWeight = FontWeight.SemiBold,
                color = HudTextPrimary,
            )
            com.venera.desktop.ui.components.DesktopInputField(
                value = input,
                onValueChange = { s -> input = s.filter { it.isDigit() } },
                modifier = Modifier.fillMaxWidth(),
                colors = com.venera.desktop.ui.components.DesktopSearchFieldColors(
                    fieldBg = HudSurface,
                    fieldBgFocused = HudSurface,
                    fieldBorder = HudBorder,
                    fieldBorderHover = HudTextPrimary,
                    fieldBorderFocus = HudTextPrimary,
                    iconTint = HudTextDim,
                    textColor = HudTextPrimary,
                    placeholderColor = HudTextDim,
                    hintColor = HudTextDim,
                    cursor = HudTextPrimary,
                ),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HudModeChip(label = com.venera.desktop.settings.DesktopI18n.t("取消", "Cancel"), selected = false, onClick = onDismiss)
                Spacer(Modifier.width(8.dp))
                HudModeChip(
                    label = com.venera.desktop.settings.DesktopI18n.t("跳转", "Go"),
                    selected = true,
                    onClick = {
                        val target = input.toIntOrNull()?.minus(1)?.coerceIn(0, totalPages - 1)
                        if (target != null) onConfirm(target)
                        onDismiss()
                    },
                )
            }
        }
    }
}

/**
 * 局部放大镜浮层：镜内重放 [viewportContent]——主视口同款视口（同 state 驱动的
 * Pager/LazyRow/LazyColumn 或 3D/导览视口）直接重挂，排版零漂移。
 * 镜头变换（origin(0,0) 缩放 [scale] 倍、平移把光标处锚到镜中心）作用于内容层整体；
 * [pointerPosition] 是光标在根画布内的坐标，[layerOrigin] 是视口原点在根画布内的位置
 * （视口带 padding，paged 档原点内缩 strip+marginH，连续档只内缩 strip）。
 * 诊断网格/读数徽标为验收期临时件，验收通过即删。
 */
@Composable
internal fun ReaderMagnifierLens(
    visible: Boolean,
    pointerPosition: Offset,
    layerOrigin: Offset,
    rootOrigin: Offset = Offset.Zero,
    viewportWidthPx: Float,
    viewportHeightPx: Float,
    viewportContent: @Composable () -> Unit,
    scale: Float = 2.0f,
) {
    if (!visible || viewportWidthPx <= 0f || viewportHeightPx <= 0f) return
    val lensSize = 180.dp
    val density = LocalDensity.current
    val lensPx = with(density) { lensSize.toPx() }
    val localX = pointerPosition.x - layerOrigin.x
    val localY = pointerPosition.y - layerOrigin.y
    // 验收期临时读数：镜框窗口位置与根画布窗口位置对账，验收通过即删。
    var lensWinPos by remember { mutableStateOf(Offset.Zero) }
    // 验收期临时读数徽标：定位错位用，验收通过即删。
    val lensDebug = "p=(${pointerPosition.x.toInt()},${pointerPosition.y.toInt()}) " +
        "o=(${layerOrigin.x.toInt()},${layerOrigin.y.toInt()}) " +
        "l=(${localX.toInt()},${localY.toInt()}) " +
        "s=$scale vp=(${viewportWidthPx.toInt()}x${viewportHeightPx.toInt()}) " +
        "win=(${lensWinPos.x.toInt()},${lensWinPos.y.toInt()}) root=(${rootOrigin.x.toInt()},${rootOrigin.y.toInt()})"
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (pointerPosition.x - lensPx / 2f).roundToInt(),
                    (pointerPosition.y - lensPx / 2f).roundToInt(),
                )
            }
            .onGloballyPositioned { lensWinPos = it.positionInRoot() },
    ) {
        Box(
            modifier = Modifier
                .size(lensSize)
                .clip(CircleShape)
                .background(Color.Black)
                .border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                .border(1.dp, Color.Black.copy(alpha = 0.45f), CircleShape),
        ) {
        // 解除 180dp 父约束并强制左上角对齐：requiredSize 超出父容器会默认居中摆放，
        // 凭空引入 (lens - viewport) / 2 初始偏移，导致放大镜内画面整体错位。
        Box(
            modifier = Modifier
                .wrapContentSize(align = Alignment.TopStart, unbounded = true)
                .size(with(density) { viewportWidthPx.toDp() }, with(density) { viewportHeightPx.toDp() })
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = scale
                    scaleY = scale
                    translationX = lensPx / 2f - localX * scale
                    translationY = lensPx / 2f - localY * scale
                },
        ) {
            // 视口层：镜内重挂的主视口同款视口（排版/翻页动效零漂移）。
            Box(modifier = Modifier.fillMaxSize()) {
                viewportContent()
            }
                // 验收期诊断网格：层内坐标红线每 120px、原点绿块、中心青十字——
                // 镜里网格的相位/间距直接暴露层变换是否按公式走，验收通过即删。
                Spacer(
                    Modifier
                        .matchParentSize()
                        .drawBehind {
                            var gx = 0f
                            while (gx <= size.width) {
                                drawLine(Color.Red.copy(alpha = 0.55f), Offset(gx, 0f), Offset(gx, size.height), 2f)
                                gx += 120f
                            }
                            var gy = 0f
                            while (gy <= size.height) {
                                drawLine(Color.Red.copy(alpha = 0.55f), Offset(0f, gy), Offset(size.width, gy), 2f)
                                gy += 120f
                            }
                            drawRect(Color.Green, topLeft = Offset(0f, 0f), size = Size(56f, 56f))
                            drawLine(
                                Color.Cyan,
                                Offset(size.width / 2f - 40f, size.height / 2f),
                                Offset(size.width / 2f + 40f, size.height / 2f),
                                4f,
                            )
                            drawLine(
                                Color.Cyan,
                                Offset(size.width / 2f, size.height / 2f - 40f),
                                Offset(size.width / 2f, size.height / 2f + 40f),
                                4f,
                            )
                        },
                )
            }
        }
        // 验收期临时读数徽标：定位错位用，验收通过即删。
        Text(
            text = lensDebug,
            fontSize = 11.sp,
            color = Color(0xFF9AE6FF),
            modifier = Modifier
                .offset(y = lensSize + 6.dp)
                .background(Color(0xE6121418), RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 3.dp),
        )
    }
}

// ── 设置浮层通用小件 ─────────────────────────────────────────────────────────

/** tab 胶囊：选中淡蓝底，未选描边胶囊（design-tokens §tabSelectedBg）。 */
@Composable
internal fun SettingsTabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val tokens = VeneraTokens
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) tokens.color.primary.copy(alpha = 0.25f) else Color.Transparent)
            .border(
                1.dp,
                if (selected) tokens.color.primary else HudRowStroke,
                RoundedCornerShape(999.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            fontSize = VeneraType.aux.size,
            fontWeight = FontWeight.Medium,
            color = if (selected) tokens.color.primary else HudTextPrimary,
        )
    }
}

/** 分组分隔：上下各 12dp 留白 + 1dp divider（design-tokens §分隔）。 */
@Composable
internal fun SettingsSectionGap() {
    Box(
        modifier = Modifier
            .padding(vertical = 12.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(HudDivider),
    )
}

/** 条目行：左文案 + 右控件；行高 ≥52dp。enabled=false 时置灰。 */
@Composable
internal fun SettingsToggleRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    gear: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = VeneraType.aux.size,
            fontWeight = FontWeight.Medium,
            color = if (enabled) HudTextPrimary else HudTextDim,
            modifier = Modifier.weight(1f),
        )
        if (gear != null) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable(onClick = gear),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = com.venera.desktop.settings.DesktopI18n.t("参数", "Options"),
                    tint = HudTextDim,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        com.venera.desktop.ui.DesktopSwitch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = com.venera.desktop.ui.DesktopSwitchDefaults.hudDark(),
        )
    }
}

/** 滑条行：左标签 + 全宽滑条 + 右上角数值（design-tokens §滑条数值）。 */
@Composable
internal fun SettingsSliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    step: Float,
    display: String,
    enabled: Boolean = true,
    onChange: (Float) -> Unit,
) {
    val tokens = VeneraTokens
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(vertical = 4.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = label,
                fontSize = VeneraType.aux.size,
                fontWeight = FontWeight.Medium,
                color = if (enabled) HudTextPrimary else HudTextDim,
            )
            Text(text = display, fontSize = VeneraType.micro.size, color = HudTextDim)
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onChange,
            valueRange = valueRange,
            enabled = enabled,
            // 步进：M3 Slider 的 steps 参数是中间档位数（end-start)/step - 1。
            steps = (((valueRange.endInclusive - valueRange.start) / step) - 1).roundToInt()
                .coerceIn(0, 4000),
            colors = SliderDefaults.colors(
                thumbColor = tokens.color.primary,
                activeTrackColor = tokens.color.primary,
                inactiveTrackColor = Color(0x33FFFFFF),
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** chip 组行：左标签 + 横排描边胶囊。 */
@Composable
internal fun ChipGroupRow(label: String, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = VeneraType.aux.size,
            fontWeight = FontWeight.Medium,
            color = HudTextPrimary,
            modifier = Modifier.width(88.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            content()
        }
    }
}

/** 模式卡：图标位 + 文案 +（可选）⚙；选中淡蓝底（design-tokens §cardSelectedBg）。 */
@Composable
internal fun ModeCard(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    gear: (() -> Unit)? = null,
) {
    val tokens = VeneraTokens
    Row(
        modifier = modifier
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) tokens.color.primary.copy(alpha = 0.18f) else Color.Transparent)
            .border(
                1.dp,
                if (selected) tokens.color.primary else HudRowStroke,
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            fontSize = VeneraType.aux.size,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) tokens.color.primary else HudTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (gear != null) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .clickable(onClick = gear),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = com.venera.desktop.settings.DesktopI18n.t("参数", "Options"),
                    tint = if (selected) tokens.color.primary else HudTextDim,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

