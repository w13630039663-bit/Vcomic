package com.venera.desktop.settings

import com.venera.desktop.ui.theme.VeneraType
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.venera.desktop.ui.components.DesktopSearchField
import com.venera.desktop.ui.components.DesktopSearchFieldColors

/**
 * 设置总览：Bento 玻璃舱墙 + 底部「关于与发布」富卡区（.1 · 密度修订）。
 *
 * 大窗空旷的两条对策（令）：
 *  1. 舱内信息密度提升：舱高 136、每舱 2–4 行真控件/真读数，档位 chip 只取短名。
 *  2. 舱墙下补「关于与发布」双卡（形态对齐安卓设置关于页：身份位 + 链接行 + 运行读数），
 *     不抄对岸的宣传诗句（文案尺禁文学修辞），链接行只摆真回调。
 * 「网络」「收藏」两舱等档一/档二接线后补位；不摆假入口、不画假数。
 */

private val CellHeight = 136.dp
private val DeckGap = 14.dp

@Composable
internal fun SettingsBentoOverview(
    blockAi: Boolean,
    aiBadge: Boolean,
    videoAutoPlay: Boolean,
    columnLabel: String?,
    previewLabel: String?,
    animatedLabel: String?,
    readingModeLabel: String?,
    autoPlayLabel: String?,
    proxyTypeLabel: String?,
    favoriteSortLabel: String?,
    probeReadout: String?,
    guardRuleCount: Int,
    ruleSegments: List<Pair<Color, Float>>,
    cacheLabel: String?,
    downloadDirLabel: String?,
    gelbooruOn: Boolean,
    pixivOn: Boolean,
    versionLabel: String,
    onOpen: (DesktopSettingsSection) -> Unit,
    onToggleBlockAi: (Boolean) -> Unit,
    onToggleAiBadge: (Boolean) -> Unit,
    onToggleVideoAutoPlay: (Boolean) -> Unit,
    onOpenProjectHome: (() -> Unit)?,
    onOpenReleases: (() -> Unit)?,
    themeModeIndex: Int,
    onSelectTheme: (Int) -> Unit,
    onCheckUpdate: () -> Unit,
    maskOn: Boolean,
    previewIndex: Int,
    onToggleMask: (Boolean) -> Unit,
    onSelectPreview: (Int) -> Unit,
) {
    val glass = LocalSettingsGlass.current
    var query by remember { mutableStateOf("") }
    val searchFocus = remember { FocusRequester() }

    // 按 ② 重构成「左导航轨 + 右两栏」。撤掉五排假仪表盘卡片墙，
    // 整屏渐变底色已去，背景交给窗口底色。文案按铁律：主标题 ≤6 字、副标题 ≤15 字、禁隐喻。
    Row(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.K && e.isCtrlPressed) {
                    searchFocus.requestFocus()
                    true
                } else {
                    false
                }
            },
    ) {
        // 左：导航轨（草稿 ② 的 rail，11 类分组 + 总览）
        OverviewRail(
            onOpen = onOpen,
            modifier = Modifier.width(232.dp).fillMaxHeight(),
        )
        // 右：标题行 + 两栏（常用设置 / 状态 + 备份与维护）
        val isEn = com.venera.desktop.settings.DesktopLanguage.isEnglish()
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (isEn) "Settings" else "设置", fontSize = VeneraType.cardTitle.size, lineHeight = VeneraType.cardTitle.lineHeight, fontWeight = VeneraType.cardTitle.weight, color = glass.textPrimary)
                Text(if (isEn) "Overview" else "总览", fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight, color = glass.textTertiary)
                OverviewSearchField(
                    query = query,
                    onQuery = { query = it },
                    focusRequester = searchFocus,
                    modifier = Modifier.weight(1f),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(glass.track)
                        .clickable { onSelectTheme((themeModeIndex + 1) % 3) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    Icon(Icons.Outlined.Palette, contentDescription = null, tint = glass.primary, modifier = Modifier.size(14.dp))
                    Text(
                        (if (isEn) listOf("Dark", "Light", "System") else listOf("深色", "浅色", "跟随系统")).getOrElse(themeModeIndex) { if (isEn) "Dark" else "深色" },
                        fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight, fontWeight = VeneraType.cardTitle.weight, color = glass.textPrimary,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            if (query.isBlank()) {
                BentoDeck(
                    blockAi = blockAi,
                    aiBadge = aiBadge,
                    videoAutoPlay = videoAutoPlay,
                                                probeReadout = probeReadout,
                    guardRuleCount = guardRuleCount,
                        cacheLabel = cacheLabel,
                    downloadDirLabel = downloadDirLabel,
                            versionLabel = versionLabel,
                    onOpen = onOpen,
                    onToggleBlockAi = onToggleBlockAi,
                    onToggleAiBadge = onToggleAiBadge,
                    onToggleVideoAutoPlay = onToggleVideoAutoPlay,
                            themeModeIndex = themeModeIndex,
                    onSelectTheme = onSelectTheme,
                    onCheckUpdate = onCheckUpdate,
                    maskOn = maskOn,
                    previewIndex = previewIndex,
                    onToggleMask = onToggleMask,
                    onSelectPreview = onSelectPreview,
                )
            } else {
            // 舱过滤：按舱题/副题命中，命中舱横向平铺（行级搜索随接线批扩）。
            val hits = bentoEntries().filter { hit ->
                hit.title.contains(query.trim()) || hit.caption.contains(query.trim())
            }
            if (hits.isEmpty()) {
                Text(
                    if (isEn) "No category matching '${query.trim()}'" else "没有匹配「${query.trim()}」的分类",
                    fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                    color = glass.textTertiary,
                    modifier = Modifier.padding(top = 24.dp),
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    hits.forEach { hit ->
                        GlassCapsule(
                            icon = hit.icon,
                            title = hit.title,
                            caption = hit.caption,
                            onClick = { onOpen(hit.section) },
                            modifier = Modifier.fillMaxWidth().height(64.dp),
                        )
                    }
                }
            }
        }
    }
    }
}

/** 舱位表：题 / 副 / 区 / 图标（过滤共用；改口径只动这里）。 */
private data class BentoEntry(
    val section: DesktopSettingsSection,
    val title: String,
    val caption: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private fun bentoEntries(): List<BentoEntry> = listOf(
    BentoEntry(DesktopSettingsSection.APPEARANCE, "外观", "主题 · 布局 · 效果", Icons.Outlined.Palette),
    BentoEntry(DesktopSettingsSection.GALLERY, "浏览", "清晰度 · 标记 · 角标", Icons.Outlined.PhotoLibrary),
    BentoEntry(DesktopSettingsSection.PIXEL_BUDDY, "像素伙伴", "贴壁爬行 · 拼字 · 互动", Icons.Outlined.Pets),
    BentoEntry(DesktopSettingsSection.DATA, "数据", "目录 · 缓存 · 备份", Icons.Outlined.Storage),
    BentoEntry(DesktopSettingsSection.READER, "阅读器", "模式 · 翻页 · 连播", Icons.Outlined.MenuBook),
    BentoEntry(DesktopSettingsSection.SOURCES, "账号", "凭据 · 反搜 · 站点", Icons.Outlined.Laptop),
    BentoEntry(DesktopSettingsSection.EXPLORE, "屏蔽规则", "遮罩档 · 规则", Icons.Outlined.Tune),
    BentoEntry(DesktopSettingsSection.MEDIA, "图片查看器", "播放 · 底色 · 逐帧", Icons.Outlined.Movie),
    BentoEntry(DesktopSettingsSection.FAVORITES, "收藏", "去向 · 排序 · 追更", Icons.Outlined.Star),
    BentoEntry(DesktopSettingsSection.NETWORK, "网络", "代理 · 缓存上限", Icons.Outlined.Public),
    BentoEntry(DesktopSettingsSection.DESKTOP_CAPABILITIES, "桌面", "窗口 · 协议 · 日志", Icons.Outlined.Laptop),
    BentoEntry(DesktopSettingsSection.ABOUT, "关于", "版本 · 检查更新", Icons.Outlined.Info),
)

/**
 * 总览栅格：5 行（六轮定案）。
 *
 * 行1 三张数值卡 + 一张来源环形图；行2 个性化 + 两张开关卡；
 * 行3 存储 + 缓存；行3b 服务状态宽卡；行4 快捷入口；行5 贡献者。
 * 全部走 [SettingsCard]，与 [GlassCapsule] 共用同一套 hover 浮动参数。
 */
@Composable
private fun BentoDeck(
    blockAi: Boolean,
    aiBadge: Boolean,
    videoAutoPlay: Boolean,
    probeReadout: String?,
    guardRuleCount: Int,
    cacheLabel: String?,
    downloadDirLabel: String?,
    versionLabel: String,
    onOpen: (DesktopSettingsSection) -> Unit,
    onToggleBlockAi: (Boolean) -> Unit,
    onToggleAiBadge: (Boolean) -> Unit,
    onToggleVideoAutoPlay: (Boolean) -> Unit,
    themeModeIndex: Int,
    onSelectTheme: (Int) -> Unit,
    onCheckUpdate: () -> Unit,
    maskOn: Boolean,
    previewIndex: Int,
    onToggleMask: (Boolean) -> Unit,
    onSelectPreview: (Int) -> Unit,
) {
    val glass = LocalSettingsGlass.current
    // ②：两栏「常用设置 + 状态 / 备份与维护」。撤掉五排假仪表盘，
    // 只摆真控件与真值；无数据就念「未设置/未检测」，不编数字、不摆趋势图。
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
        // 左：常用设置（真控件，热切 + 落盘）
        SettingsCard(modifier = Modifier.weight(1.35f)) {
            CardTitle(Icons.Outlined.Palette, "常用设置", "外观与显示选项") {
                Text("全部设置 ›", fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight, color = glass.primary,
                    modifier = Modifier.padding(start = 8.dp).clickable { onOpen(DesktopSettingsSection.APPEARANCE) })
            }
            CardControlRow("应用模式", "窗口与控件配色") {
                CardSegmented(listOf("深色", "浅色", "跟随系统"), themeModeIndex, onSelectTheme, Modifier.width(230.dp))
            }
            CardControlRow("封面打码", "图库与本地封面") { CardSwitch(maskOn, onToggleMask) }
            CardControlRow("预览清晰度", "缩略图") {
                CardSegmented(listOf("标准", "高清"), previewIndex, onSelectPreview, Modifier.width(180.dp))
            }
            CardControlRow("自动播放", "视频与动图") { CardSwitch(videoAutoPlay, onToggleVideoAutoPlay) }
            // 这两枚真开关原摆在状态区，收进左栏常用设置：既不让右栏拖长，也不丢入口。
            CardControlRow("屏蔽 AI 图", "漫画侧独立开关") { CardSwitch(blockAi, onToggleBlockAi) }
            CardControlRow("AI 角标", "标记 AI 生成内容") { CardSwitch(aiBadge, onToggleAiBadge) }
            CardTitle(Icons.Outlined.GridView, "快捷入口", "常用设置页")
            QuickRow(Icons.Outlined.Palette, "外观", "主题与密度",
                onClick = { onOpen(DesktopSettingsSection.APPEARANCE) })
            QuickRow(Icons.Outlined.Laptop, "账号", "凭据与登录",
                onClick = { onOpen(DesktopSettingsSection.SOURCES) })
            QuickRow(Icons.Outlined.Tune, "规则", "屏蔽与管理",
                onClick = { onOpen(DesktopSettingsSection.EXPLORE) })
            QuickRow(Icons.Outlined.Storage, "数据", "目录与缓存",
                onClick = { onOpen(DesktopSettingsSection.DATA) })
        }
        // 右：状态（真值）+ 备份与维护（真入口）
        val isEn = com.venera.desktop.settings.DesktopLanguage.isEnglish()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SettingsCard(modifier = Modifier.fillMaxWidth()) {
                CardTitle(Icons.Outlined.Public, if (isEn) "Status" else "状态", if (isEn) "Local status" else "本机状态")
                // 贴草稿 ② 只留 5 行真值。此前多塞的 9 行档位摘要把右栏拖得
                // 比左栏长一大截，「规则」下面空出整片 —— 收回去，两栏高度就接近了。
                TruthRow(if (isEn) "Download Path" else "下载目录", downloadDirLabel ?: if (isEn) "Not set" else "未设置", ok = downloadDirLabel != null)
                TruthRow(if (isEn) "Cache Usage" else "缓存占用", cacheLabel ?: if (isEn) "Unmeasured" else "未测", ok = cacheLabel != null)
                TruthRow(if (isEn) "Block Rules" else "屏蔽规则", if (isEn) "${guardRuleCount} rules" else "${guardRuleCount} 条", ok = guardRuleCount > 0)
                TruthRow(if (isEn) "Probe Speed" else "源测速", probeReadout ?: if (isEn) "Not tested" else "未检测", ok = probeReadout != null)
                TruthRow(if (isEn) "Version" else "版本", versionLabel, ok = true)
                // 其余档位真值不摆进总览：它们各自的分区页才是归处，这里不重复铺一遍。
                // 账号态、规则分段同样收进分区页，避免状态区越堆越长。
            }
            SettingsCard(modifier = Modifier.fillMaxWidth()) {
                CardTitle(Icons.Outlined.Storage, if (isEn) "Maintenance" else "备份与维护", if (isEn) "Local operations" else "本机操作")
                QuickRow(Icons.Outlined.Storage, if (isEn) "Export" else "导出", if (isEn) "Backup" else "本机备份",
                    onClick = { onOpen(DesktopSettingsSection.DATA) })
                QuickRow(Icons.Outlined.Storage, if (isEn) "Clear Cache" else "清理缓存", if (isEn) "Free space" else "立即释放",
                    onClick = { onOpen(DesktopSettingsSection.DATA) })
                QuickRow(Icons.Outlined.OpenInNew, if (isEn) "Check Update" else "检查更新", if (isEn) "Release page" else "打开更新页", onClick = onCheckUpdate)
                // 贴草稿 ② 只留 3 条。开源主页 / 更新发布在「关于与更新」分区
                // 已有真入口，不在这里重复铺一遍（它们也把右栏拖长了）。
            }
        }
    }
}

/** 状态行：左标签 + 右真值 + 三态点。无真值时念「未设置」，不编一个数。 */
@Composable
private fun TruthRow(label: String, value: String, ok: Boolean) {
    val glass = LocalSettingsGlass.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatusDot(if (ok) "ok" else "off")
        Text(label, fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight, color = glass.textSecondary, modifier = Modifier.weight(1f), maxLines = 1)
        Text(value, fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight, color = glass.textTertiary, maxLines = 1)
    }
}

/** 左导航轨（② rail）：总览 + 11 类分组，点击直达对应设置分区。 */
@Composable
private fun OverviewRail(
    onOpen: (DesktopSettingsSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = LocalSettingsGlass.current
    Column(
        modifier
            // 去掉左轨整片底色，交给窗口底色（列完 11 项不再一路铺到窗口底）。
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 20.dp),
    ) {
        Text("设置", fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight, fontWeight = VeneraType.cardTitle.weight, color = glass.textPrimary,
            modifier = Modifier.padding(horizontal = 10.dp))
        Spacer(Modifier.height(14.dp))
        val groups = listOf("浏览", "阅读与收藏", "系统")
        groups.forEach { groupName ->
            Text(groupName, fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight, color = glass.textTertiary,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp))
            DesktopSettingsSection.entries.filter { it.group == groupName }.forEach { sec ->
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(9.dp))
                        .clickable { onOpen(sec) }
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = overviewSectionIcon(sec),
                        contentDescription = null,
                        tint = glass.textTertiary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(sec.title, fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight, color = glass.textPrimary, maxLines = 1)
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

/** 总览左轨每分区图标（与二级页同族材料图标，线性、单一图标库）。 */
private fun overviewSectionIcon(section: DesktopSettingsSection): androidx.compose.ui.graphics.vector.ImageVector =
    when (section) {
        DesktopSettingsSection.APPEARANCE -> Icons.Outlined.Palette
        DesktopSettingsSection.PIXEL_BUDDY -> Icons.Outlined.Pets
        DesktopSettingsSection.GALLERY -> Icons.Outlined.GridView
        DesktopSettingsSection.EXPLORE -> Icons.Outlined.PhotoLibrary
        DesktopSettingsSection.READER -> Icons.Outlined.MenuBook
        DesktopSettingsSection.FAVORITES -> Icons.Outlined.Star
        DesktopSettingsSection.NETWORK -> Icons.Outlined.Public
        DesktopSettingsSection.SOURCES -> Icons.Outlined.Tune
        DesktopSettingsSection.MEDIA -> Icons.Outlined.Movie
        DesktopSettingsSection.DATA -> Icons.Outlined.Storage
        DesktopSettingsSection.DESKTOP_CAPABILITIES -> Icons.Outlined.Laptop
        DesktopSettingsSection.ABOUT -> Icons.Outlined.Info
    }

/** 卡内控件行：左标签右控件。 */
@Composable
private fun CardControlRow(label: String, caption: String, control: @Composable () -> Unit) {
    val glass = LocalSettingsGlass.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.width(110.dp)) {
            Text(label, fontSize = VeneraType.bodySecondary.size, lineHeight = VeneraType.bodySecondary.lineHeight, fontWeight = VeneraType.cardTitle.weight, color = glass.textPrimary, maxLines = 1)
            Text(caption, fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight, color = glass.textTertiary, maxLines = 1)
        }
        Spacer(Modifier.weight(1f))
        control()
    }
}

/** 舱内开关行。 */
@Composable
private fun CapsuleSwitch(label: String?, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (label != null) {
            Text(label, fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight, color = LocalSettingsGlass.current.textSecondary)
        }
        com.venera.desktop.ui.DesktopSwitch(checked = checked, onCheckedChange = onToggle)
    }
}

/** 凭据状态行：状态灯 + 名。 */
@Composable
private fun CredentialRow(name: String, on: Boolean) {
    Row(
        Modifier.fillMaxWidth().height(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatusDot(if (on) CredentialLight.ON else CredentialLight.OFF)
        Text(name, fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight, color = LocalSettingsGlass.current.textSecondary, maxLines = 1)
    }
}

/** 总览搜索框：玻璃底 + Ctrl+K 焦点。 */
@Composable
private fun OverviewSearchField(
    query: String,
    onQuery: (String) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    // 批4·搜索场归一：自绘第三套退役，删壳委托共享 DesktopSearchField（settingsGlass 色档）。
    DesktopSearchField(
        value = query,
        onValueChange = onQuery,
        onSearch = {},
        placeholder = if (com.venera.desktop.settings.DesktopLanguage.isEnglish()) "Search settings" else "搜索设置项",
        shortcutHint = "Ctrl K",
        onClear = { onQuery("") },
        colors = DesktopSearchFieldColors.settingsGlass(),
        focusRequester = focusRequester,
        modifier = modifier,
    )
}
