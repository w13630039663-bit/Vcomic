package com.venera.desktop.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.venera.compose.data.platform.JsonKeyValueStore

/** 构造「通用与外观」分区设置卡（含语言、主题、天气联动与全窗切换动效、缩放）。 */
@Composable
internal fun buildAppearanceSettingsCards(
    mainThemeStore: JsonKeyValueStore?,
    isEn: Boolean,
): List<DesktopSettingsCard> {
    val themeMode by DesktopThemeMode.mode.collectAsState()
    val uiScaleTier by DesktopUiScale.tier.collectAsState()
    val petVisible by DesktopPetVisibility.visible.collectAsState()
    val backdropMode by DesktopBackdropMode.mode.collectAsState()
    val blurEffect by DesktopBackdropMode.effect.collectAsState()
    val railBlur by DesktopRailBlur.visible.collectAsState()
    val topBarBlur by DesktopTopBarBlur.visible.collectAsState()
    val revealFx by DesktopThemeRevealPrefs.effect.collectAsState()
    val weatherMode by DesktopRailWeatherService.mode.collectAsState()
    val weatherCity by DesktopRailWeatherService.customCity.collectAsState()
    val weatherSnap by DesktopRailWeatherService.snapshot.collectAsState()

    val weatherSummary = buildString {
        if (weatherMode == DesktopRailWeatherMode.OFF) {
            append(if (isEn) "Disabled (default clear sky)" else "已关闭（默认晴空/星夜）")
        } else {
            val typeLabel = if (isEn) weatherSnap.type.dayLabelEn else weatherSnap.type.dayLabelZh
            val cityLabel = weatherSnap.city.ifBlank { if (isEn) "Waiting..." else "待获取" }
            val tempLabel = weatherSnap.tempC?.let { " ${it}°C" }.orEmpty()
            append("$cityLabel · $typeLabel$tempLabel")
        }
    }

    return listOf(
        DesktopSettingsCard(
            title = if (isEn) "Language" else "语言",
            caption = if (isEn) "Interface display language" else "界面显示语言",
            rows = listOfNotNull(
                mainThemeStore?.let { store ->
                    DesktopSettingRow.Options(
                        title = if (isEn) "Interface Language" else "界面语言",
                        caption = if (isEn) "Switch between Chinese and English" else "支持中文与英文即时切换",
                        options = listOf(DesktopLanguage.LABEL_ZH, DesktopLanguage.LABEL_EN),
                        selectedIndex = if (isEn) 1 else 0,
                        onSelect = { i ->
                            DesktopLanguage.write(store, if (i == 1) DesktopLanguage.EN else DesktopLanguage.ZH)
                        },
                    )
                },
                mainThemeStore?.let { store ->
                    val toastDurationMs by DesktopErrorToastDuration.durationMs.collectAsState()
                    DesktopSettingRow.Options(
                        title = if (isEn) "Error Toast Duration" else "错误弹窗显示时间",
                        caption = if (isEn) "Auto-dismiss delay before collapsing to bottom-right pill" else "自动收起为右下角报错胶囊的等待时间（默认 3 秒）",
                        options = DesktopErrorToastDuration.optionLabels(isEn),
                        selectedIndex = DesktopErrorToastDuration.indexOf(toastDurationMs),
                        onSelect = { i ->
                            val picked = DesktopErrorToastDuration.PRESETS_MS.getOrElse(i) {
                                DesktopErrorToastDuration.DEFAULT_MS
                            }
                            DesktopErrorToastDuration.write(store, picked)
                        },
                    )
                },
            ),
        ),
        DesktopSettingsCard(
            title = if (isEn) "Theme" else "主题",
            caption = if (isEn) "Dark / Light / Follow System" else "深色 / 浅色 / 跟随系统（即改即生效）",
            rows = listOfNotNull(
                mainThemeStore?.let { store ->
                    DesktopSettingRow.Options(
                        title = if (isEn) "Theme Mode" else "主题模式",
                        caption = if (isEn) "Dark, Light or System mode" else "支持在侧栏星穹开关或此处即时切换",
                        options = if (isEn) listOf("Dark", "Light", "System") else listOf(
                            DesktopThemeMode.DARK,
                            DesktopThemeMode.LIGHT,
                            DesktopThemeMode.SYSTEM,
                        ),
                        selectedIndex = listOf(
                            DesktopThemeMode.DARK,
                            DesktopThemeMode.LIGHT,
                            DesktopThemeMode.SYSTEM,
                        ).indexOf(themeMode).coerceAtLeast(0),
                        onSelect = { i ->
                            DesktopThemeMode.write(
                                store,
                                listOf(DesktopThemeMode.DARK, DesktopThemeMode.LIGHT, DesktopThemeMode.SYSTEM)[i],
                            )
                        },
                    )
                },
                mainThemeStore?.let { store ->
                    DesktopSettingRow.Options(
                        title = if (isEn) "Theme Transition" else "全窗切换动效",
                        caption = if (isEn) "Visual reveal effect when toggling dark/light" else "切换深浅色时全窗 GPU 离屏截帧转场效果",
                        options = DesktopThemeRevealPrefs.OPTIONS,
                        selectedIndex = DesktopThemeRevealPrefs.OPTIONS.indexOf(revealFx).coerceAtLeast(0),
                        onSelect = { i ->
                            DesktopThemeRevealPrefs.write(
                                store,
                                DesktopThemeRevealPrefs.OPTIONS.getOrElse(i) { DesktopThemeRevealPrefs.STARBURST },
                            )
                        },
                    )
                },
                mainThemeStore?.let { store ->
                    DesktopSettingRow.Options(
                        title = if (isEn) "Sidebar Weather" else "侧栏天气联动",
                        caption = weatherSummary,
                        options = DesktopRailWeatherMode.OPTIONS,
                        selectedIndex = DesktopRailWeatherMode.OPTIONS.indexOf(weatherMode).coerceAtLeast(0),
                        onSelect = { i ->
                            DesktopRailWeatherService.setMode(
                                store,
                                DesktopRailWeatherMode.OPTIONS.getOrElse(i) { DesktopRailWeatherMode.AUTO },
                            )
                        },
                    )
                },
                mainThemeStore?.let { store ->
                    if (weatherMode == DesktopRailWeatherMode.CITY || weatherCity.isNotBlank()) {
                        DesktopSettingRow.Text(
                            title = if (isEn) "Custom City" else "指定城市",
                            caption = if (isEn) "Override IP location (e.g. Shanghai, Tokyo)" else "填写城市名以避开代理 IP 漂移（回车或失焦生效）",
                            value = weatherCity,
                            onCommit = { city -> DesktopRailWeatherService.setCustomCity(store, city) },
                        )
                    } else {
                        null
                    }
                },
                if (weatherMode != DesktopRailWeatherMode.OFF) {
                    DesktopSettingRow.Action(
                        title = if (isEn) "Sync Weather" else "更新当前天气",
                        caption = if (weatherSnap.syncing) {
                            if (isEn) "Fetching weather..." else "正在获取气象数据..."
                        } else {
                            weatherSummary
                        },
                        buttonLabel = if (weatherSnap.syncing) {
                            if (isEn) "Syncing" else "同步中"
                        } else {
                            if (isEn) "Refresh" else "立即刷新"
                        },
                        enabled = !weatherSnap.syncing,
                        onAction = { DesktopRailWeatherService.refreshAsync() },
                    )
                } else {
                    null
                },
                mainThemeStore?.let {
                    DesktopSettingRow.Toggle(
                        title = if (isEn) "Backdrop Blur" else "底板模糊",
                        caption = if (isEn) "Solid background when disabled" else "关闭后内容区为实底",
                        checked = backdropMode != DesktopBackdropMode.SOLID,
                        onToggle = { next ->
                            DesktopBackdropMode.select(
                                if (next) blurEffect else DesktopBackdropMode.SOLID,
                            )
                        },
                    )
                },
                mainThemeStore?.let { bdStore ->
                    DesktopSettingRow.Toggle(
                        title = if (isEn) "Rail Blur" else "侧栏模糊",
                        caption = if (isEn) "Solid sidebar when disabled" else "独立开关，关闭为实底",
                        checked = railBlur,
                        onToggle = { next -> DesktopRailBlur.write(bdStore, next) },
                    )
                },
                mainThemeStore?.let { bdStore ->
                    DesktopSettingRow.Toggle(
                        title = if (isEn) "Top Bar Blur" else "顶栏模糊",
                        caption = if (isEn) "Solid top bar when disabled" else "独立开关，关闭为实底",
                        checked = topBarBlur,
                        onToggle = { next -> DesktopTopBarBlur.write(bdStore, next) },
                    )
                },
                mainThemeStore?.let {
                    DesktopSettingRow.Options(
                        title = if (isEn) "Blur Effect" else "模糊效果",
                        caption = if (isEn) "Acrylic real-time blur or Mica wallpaper tint" else "Acrylic 实时透明模糊 / Mica 壁纸云母着色",
                        options = DesktopBackdropMode.effectOptions,
                        selectedIndex = DesktopBackdropMode.effectOptions.indexOf(blurEffect).coerceAtLeast(0),
                        onSelect = { i ->
                            DesktopBackdropMode.selectEffect(
                                DesktopBackdropMode.effectOptions.getOrElse(i) { DesktopBackdropMode.ACRYLIC },
                            )
                        },
                    )
                },
                mainThemeStore?.let { skinStore ->
                    DesktopSettingRow.Toggle(
                        title = if (isEn) "Sidebar Mascot" else "侧栏桌宠",
                        caption = if (isEn) "Display mascot fish at sidebar bottom" else "在侧栏底部显示小鱼桌宠",
                        checked = petVisible,
                        onToggle = { next -> DesktopPetVisibility.write(skinStore, next) },
                    )
                },
            ),
        ),
        DesktopSettingsCard(
            title = if (isEn) "Scaling" else "缩放",
            caption = if (isEn) "UI scaling & display ratio" else "界面缩放与显示比例",
            rows = listOfNotNull(
                mainThemeStore?.let { store ->
                    DesktopSettingRow.Options(
                        title = if (isEn) "UI Scale" else "界面缩放",
                        caption = if (isEn) "Adjust interface display scale" else "调整界面的显示比例",
                        options = DesktopUiScale.TIERS,
                        selectedIndex = DesktopUiScale.TIERS.indexOf(uiScaleTier).coerceAtLeast(0),
                        onSelect = { i -> DesktopUiScale.write(store, DesktopUiScale.TIERS[i]) },
                    )
                },
            ),
        ),
    )
}
