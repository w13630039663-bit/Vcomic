package com.venera.desktop.gallery.ui


import com.venera.desktop.ui.theme.VeneraType
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.compose.components.venera.VeneraButton
import com.venera.compose.components.venera.VeneraCard
import com.venera.compose.components.venera.VeneraSwitch
import com.venera.compose.gallery.domain.GalleryAnimatedMode
import com.venera.compose.gallery.domain.GalleryColumnMode
import com.venera.compose.gallery.domain.GalleryPreloadMode
import com.venera.compose.gallery.domain.GalleryPreviewQuality
import com.venera.compose.gallery.domain.GalleryViewerBackdrop
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.venera.desktop.gallery.data.DesktopGalleryContentGuard
import com.venera.desktop.gallery.data.DesktopGalleryPreferences
import com.venera.compose.ui.tokens.VeneraTokens

/**
 * 桌面活路径的设置屏（主人 goal「接着干设置」）。
 *
 * ## 为什么桌面从前没有这一屏而这一批补上
 *
 * 八枚画廊偏好的**读写口**（[DesktopGalleryPreferences] 的 setter 与读流）、分级遮罩档
 * （[DesktopGalleryContentGuard]）从批 46/52/61/63 起就都有判据盯着、装配点也早拿到了那两枚具体件
 * （`VeneraDesktop.kt:455/462`），而**当前这条活渲染路（`when (route)`）里没有任何一屏把它们摆出来** ——
 * 唯一含那些把手的 `DesktopGallerySettingsPane.kt` 是已拆掉的 Fluent 平行线遗骸（带 `io.github.composefluent`）。
 * 于是"账上记着能改"与"用户够得着"之间没有线。这一批把门开上，屏只吃形参。
 *
 * ## 为什么屏只吃形参而不吃那两枚具体件
 *
 * `DesktopUiIsolationTest` ② 管的是「UI 里不许 `GalleryPorts.of(...)` 取静态口」。装配点
 * （集成方）已用 `DesktopGalleryPorts.preferences(handle)` / `contentGuard(handle)` 各取一次
 * 那颗具体件，这里把每枚档拆成**一对形参**：一枚读值 + 一枚写回调。这样屏可被离屏尺单独渲、
 * 且"写侧存不存在"这件事由调用点（递不递那枚 lambda）决定，而不是屏自己假装都能改。
 *
 * ## 八枚的档名全部单源
 *
 * 每一排的档位与叫法都取自 [DesktopGallerySettingsSpec]（`columnTierLabels` 等五张表），
 * 不在屏内重述一遍 —— 与「判据只住一处」同一族纪律（两份标签表迟早漂成两端念不同词）。
 */
@Composable
internal fun DesktopGallerySettingsScreen(
    mode: WideScreenLayoutMode,
    onBack: () -> Unit,
    /**
     * 桌面那颗**具体的**偏好件（写侧 `setGalleryXxx` 只在这一颗上，共享接口只有读面）。
     * 由装配点经 `DesktopGalleryPorts.preferences(handle)` 取一次递进来 —— 屏不自己取静态口
     * （`DesktopUiIsolationTest` ② 禁的是 UI 里 `GalleryPorts.of(`，作形参传具体件是本仓既有先例：
     * Fluent 那颗 `DesktopGallerySettingsPane` 与源管理屏那条写侧都是这么接的）。
     */
    prefs: DesktopGalleryPreferences,
    /** 桌面那颗具体的守卫件（分级遮罩档的读流 + `setNsfwMaskMode` 写口在它身上）。 */
    guard: DesktopGalleryContentGuard,
) {
    val tokens = VeneraTokens
    val spec = DesktopGallerySettingsSpec
    // 八枚读值：全部 collect 那两枚件上的既有 StateFlow（单例，与墙上/大图页读的是同一份事实）。
    val columnMode by prefs.galleryColumnMode.collectAsState()
    val wallLayout by prefs.wallLayout.collectAsState()
    val previewQuality by prefs.galleryPreviewQuality.collectAsState()
    val blockAi by prefs.galleryBlockAi.collectAsState()
    val aiBadge by prefs.galleryAiBadge.collectAsState()
    val autoPlaySec by prefs.galleryAutoPlaySec.collectAsState()
    val preload by prefs.galleryPreload.collectAsState()
    val animated by prefs.galleryAnimated.collectAsState()
    val backdrop by prefs.galleryBackdrop.collectAsState()
    val maskMode by guard.nsfwMaskMode.collectAsState()
    // 批3·页头补齐：自拼头（返回钮 + Text）退役，进 DesktopPageShell 统一骨架
    // （Read 宽档 = 原 contentMaxWidthFor 同一把尺；卡壳页头 + 返回位与全仓二级页同形）。
    // 本屏仍是规格件（不可达，判据在 DesktopSettingsEntryTest 注释里记档），只换骨架不动段落。
    com.venera.desktop.ui.DesktopPageShell(
        mode = mode,
        title = "设置",
        width = com.venera.desktop.ui.DesktopPageWidth.Read,
        onBack = onBack,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.space4),
        ) {

        // 一枚枚段落，次序照 spec.editable 那八行的既定次序（先"墙上摆成什么样"，再"要不要挡"，
        // 最后四枚是"翻着翻着自己走""提前取哪几张""动图要不要逐帧""大图那一屏的底"）。
        SettingsSection("漫画源分级", "这一档决定探索/详情/画廊里成人内容怎么摆（与站点屏同一道写口）。") {
            EnumRow(desktopMaskModeLabels().map { it.second }, desktopMaskModeIndex(maskMode)) { i ->
                guard.setNsfwMaskMode(desktopMaskModeLabels()[i].first)
            }
        }
        SettingsSection(spec.editable[0].title, spec.editable[0].caption) {
            EnumRow(spec.columnTierLabels.map { it.second }, spec.columnTierLabels.indexOfFirst { it.first == columnMode }) { i -> prefs.setGalleryColumnMode(spec.columnTierLabels[i].first) }
        }
        // 图库站：主墙布局两档（瀑布流 / 等宽网格·大间隔）——设置自选。
        SettingsSection("墙布局", "作品墙的排布方式") {
            val layouts = com.venera.compose.gallery.domain.GalleryWallLayout.entries
            EnumRow(layouts.map { it.label }, layouts.indexOfFirst { it == wallLayout }) { i -> prefs.setWallLayout(layouts[i]) }
        }
        SettingsSection(spec.editable[1].title, spec.editable[1].caption) {
            EnumRow(spec.qualityTierLabels.map { it.second }, spec.qualityTierLabels.indexOfFirst { it.first == previewQuality }) { i -> prefs.setGalleryPreviewQuality(spec.qualityTierLabels[i].first) }
        }
        SettingsSection(spec.editable[2].title, spec.editable[2].caption) {
            SwitchRow(blockAi) { prefs.setGalleryBlockAi(it) }
        }
        SettingsSection(spec.editable[3].title, spec.editable[3].caption) {
            SwitchRow(aiBadge) { prefs.setGalleryAiBadge(it) }
        }
        SettingsSection(spec.editable[4].title, spec.editable[4].caption) {
            EnumRow(
                spec.autoPlayTiers.map { if (it == 0) "关" else "${it} 秒" },
                spec.autoPlayTiers.indexOf(autoPlaySec).coerceAtLeast(0),
            ) { i -> prefs.setGalleryAutoPlaySec(spec.autoPlayTiers[i]) }
        }
        SettingsSection(spec.editable[5].title, spec.editable[5].caption) {
            EnumRow(spec.preloadTierLabels.map { it.second }, spec.preloadTierLabels.indexOfFirst { it.first == preload }) { i -> prefs.setGalleryPreload(spec.preloadTierLabels[i].first) }
        }
        SettingsSection(spec.editable[6].title, spec.editable[6].caption) {
            EnumRow(spec.animatedTierLabels.map { it.second }, spec.animatedTierLabels.indexOfFirst { it.first == animated }) { i -> prefs.setGalleryAnimated(spec.animatedTierLabels[i].first) }
        }
        SettingsSection(spec.editable[7].title, spec.editable[7].caption) {
            EnumRow(spec.backdropTierLabels.map { it.second }, spec.backdropTierLabels.indexOfFirst { it.first == backdrop }) { i -> prefs.setGalleryBackdrop(spec.backdropTierLabels[i].first) }
        }

        // 三枚点名缺席：屏上照实说"这一档桌面改不了 + 差哪一层"，措辞直接取 spec.notOnScreen。
        Text(
            text = "以下几项桌面改不了",
            fontSize = VeneraType.sectionTitle.size, lineHeight = VeneraType.sectionTitle.lineHeight,
            color = tokens.color.textSecondary,
        )
        spec.notOnScreen.forEach { row ->
            VeneraCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(tokens.spacing.space3)) {
                    Text(row.title, fontSize = VeneraType.cardTitle.size, lineHeight = VeneraType.cardTitle.lineHeight, color = tokens.color.textSecondary)
                    Spacer(Modifier.height(tokens.spacing.space1))
                    Text(row.why, fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight, color = tokens.color.textTertiary)
                }
            }
        }
        }
    }
}

/** 一枚遮罩档当前值在那三档里的下标（认不出来落 0 = 不过滤，写侧另有 `desktopValidateMaskMode` 兜）。 */
private fun desktopMaskModeIndex(maskMode: String): Int =
    desktopMaskModeLabels().indexOfFirst { it.first == maskMode }.coerceAtLeast(0)

@Composable
private fun SettingsSection(
    title: String,
    caption: String,
    content: @Composable () -> Unit,
) {
    val tokens = VeneraTokens
    VeneraCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(tokens.spacing.space3)) {
            Text(title, fontSize = VeneraType.cardTitle.size, lineHeight = VeneraType.cardTitle.lineHeight, color = tokens.color.textPrimary)
            Spacer(Modifier.height(tokens.spacing.space1))
            Text(caption, fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight, color = tokens.color.textTertiary)
            Spacer(Modifier.height(tokens.spacing.space3))
            content()
        }
    }
}

@Composable
private fun EnumRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    // 控件：改指全仓唯一的 DesktopSegmented（一个轨 + 滑块）。
    com.venera.desktop.ui.DesktopSegmented(
        options = options,
        selectedIndex = selected,
        onSelect = onSelect,
        modifier = Modifier.fillMaxWidth(),
        fillWidth = true,
    )
}

@Composable
private fun SwitchRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (checked) "已开" else "已关",
            fontSize = VeneraTokens.type.body,
            color = VeneraTokens.color.textPrimary,
        )
        VeneraSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
