package com.venera.desktop.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.venera.compose.data.platform.JsonKeyValueStore
import com.venera.compose.gallery.domain.GalleryWallLayout
import com.venera.desktop.gallery.data.DesktopGalleryPreferences
import com.venera.desktop.gallery.ui.DesktopCardSize
import com.venera.desktop.gallery.ui.DesktopDetailPaneWidth
import com.venera.desktop.gallery.ui.DesktopGallerySettingsSpec
import com.venera.desktop.gallery.ui.DesktopWallDensity

/** 构造「画廊与浏览」分区设置卡。 */
@Composable
internal fun rememberGallerySettingsCards(
    prefs: DesktopGalleryPreferences,
    mainThemeStore: JsonKeyValueStore?,
    wallDensity: DesktopWallDensity,
    onPickWallDensity: (DesktopWallDensity) -> Unit,
    languageMode: String,
    isEn: Boolean,
): List<DesktopSettingsCard> {
    val spec = DesktopGallerySettingsSpec
    val wallLayout by prefs.wallLayout.collectAsState()
    val previewQuality by prefs.galleryPreviewQuality.collectAsState()
    val detailQuality by prefs.detailQuality.collectAsState()
    val cardSize by prefs.cardSize.collectAsState()
    val paneWidth by prefs.detailPaneWidth.collectAsState()
    val paneOpen by prefs.detailPaneOpen.collectAsState()
    val imageCacheMb by prefs.imageCacheMb.collectAsState()
    val imageCacheMbTier = when {
        imageCacheMb >= 2048 -> 2
        imageCacheMb >= 1024 -> 1
        else -> 0
    }
    val blockAi by prefs.galleryBlockAi.collectAsState()
    val aiBadge by prefs.galleryAiBadge.collectAsState()
    val autoPlaySec by prefs.galleryAutoPlaySec.collectAsState()
    val preload by prefs.galleryPreload.collectAsState()
    val animated by prefs.galleryAnimated.collectAsState()
    val backdrop by prefs.galleryBackdrop.collectAsState()
    val coverAspectTier by DesktopCoverAspect.tier.collectAsState()
    val gridDensityTier by DesktopGridDensity.tier.collectAsState()
    val comicCardLayout by DesktopComicCardLayout.config.collectAsState()
    val tagTranslationMode by DesktopTagTranslation.mode.collectAsState()
    val canvasFrame by DesktopCanvasFrame.current.collectAsState()
    val mascotSpec by DesktopMascot.current.collectAsState()

    return remember(
        languageMode,
        previewQuality, blockAi, aiBadge, autoPlaySec, preload, animated, backdrop, tagTranslationMode,
        wallLayout, wallDensity, cardSize, detailQuality, paneWidth, paneOpen, imageCacheMb, coverAspectTier,
        gridDensityTier, comicCardLayout, canvasFrame, mascotSpec,
    ) {
        listOf(
            DesktopSettingsCard(
                title = "标签译名",
                caption = "画廊标签的中文显示档",
                rows = listOf(
                    DesktopSettingRow.Options(
                        title = "译文档位",
                        caption = "简体/繁体共用 cn 列，无真差异故不列",
                        options = listOf(DesktopTagTranslation.SYSTEM, DesktopTagTranslation.OFF),
                        selectedIndex = if (tagTranslationMode == DesktopTagTranslation.OFF) 1 else 0,
                        onSelect = { i ->
                            mainThemeStore?.let { store ->
                                DesktopTagTranslation.write(
                                    store,
                                    if (i == 1) DesktopTagTranslation.OFF else DesktopTagTranslation.SYSTEM,
                                )
                            }
                        },
                    ),
                ),
            ),
            DesktopSettingsCard(
                title = "浏览布局",
                caption = "网格布局与预览画质",
                rows = listOfNotNull(
                    DesktopSettingRow.Options(
                        title = "作品墙布局",
                        caption = "瀑布流或等宽方图网格",
                        options = listOf("瀑布流", "等宽网格"),
                        selectedIndex = if (wallLayout == GalleryWallLayout.Grid) 1 else 0,
                        onSelect = { i ->
                            prefs.setWallLayout(
                                if (i == 1) GalleryWallLayout.Grid else GalleryWallLayout.Staggered,
                            )
                        },
                    ),
                    DesktopSettingRow.Options(
                        title = "卡片密度",
                        caption = "紧凑卡小，宽松卡大",
                        options = DesktopWallDensity.entries.map { it.label },
                        selectedIndex = wallDensity.ordinal,
                        onSelect = { i -> onPickWallDensity(DesktopWallDensity.entries[i]) },
                    ),
                    DesktopSettingRow.Options(
                        title = "卡片大小",
                        caption = "在密度之上再缩放单卡",
                        options = DesktopCardSize.entries.map { cardSizeLabel(it) },
                        selectedIndex = cardSize.ordinal,
                        onSelect = { i -> prefs.setCardSize(DesktopCardSize.entries[i]) },
                    ),
                    DesktopSettingRow.Options(
                        title = "网格密度",
                        caption = "自适应或固定 x 行 x 列，画廊卡片统一生效；行数决定封面裁切比例",
                        options = DesktopGridDensity.entries.map { it.label },
                        selectedIndex = DesktopGridDensity.entries.indexOf(gridDensityTier).coerceAtLeast(0),
                        onSelect = { i ->
                            mainThemeStore?.let { store ->
                                DesktopGridDensity.write(store, DesktopGridDensity.entries[i])
                            }
                        },
                    ),
                    DesktopSettingRow.Options(
                        title = "漫画卡片布局",
                        caption = "覆盖全部漫画页面的网格与双列模式",
                        options = DesktopComicCardLayoutMode.entries.map {
                            if (isEn) it.labelEn else it.labelCn
                        },
                        selectedIndex = comicCardLayout.mode.ordinal,
                        onSelect = { i ->
                            mainThemeStore?.let { store ->
                                DesktopComicCardLayout.write(
                                    store,
                                    comicCardLayout.copy(mode = DesktopComicCardLayoutMode.entries[i]),
                                )
                            }
                        },
                    ),
                    if (comicCardLayout.isCustom) {
                        DesktopSettingRow.Options(
                            title = "漫画每屏行数",
                            caption = "决定网格卡片高度比例（1～6 行）",
                            options = (1..6).map { "$it 行" },
                            selectedIndex = (comicCardLayout.rows - 1).coerceIn(0, 5),
                            onSelect = { i ->
                                mainThemeStore?.let { store ->
                                    DesktopComicCardLayout.write(store, comicCardLayout.copy(rows = i + 1))
                                }
                            },
                        )
                    } else null,
                    if (comicCardLayout.isCustom) {
                        DesktopSettingRow.Options(
                            title = "漫画每行列数",
                            caption = "网格与双列模式同时生效（1～8 列）",
                            options = (1..8).map { "$it 列" },
                            selectedIndex = (comicCardLayout.cols - 1).coerceIn(0, 7),
                            onSelect = { i ->
                                mainThemeStore?.let { store ->
                                    DesktopComicCardLayout.write(store, comicCardLayout.copy(cols = i + 1))
                                }
                            },
                        )
                    } else null,
                    DesktopSettingRow.Options(
                        title = "封面画幅",
                        caption = "仅瀑布流与等宽自适应档生效；固定行数档由网格密度决定",
                        options = DesktopCoverAspect.entries.map { it.label },
                        selectedIndex = DesktopCoverAspect.entries.indexOf(coverAspectTier).coerceAtLeast(0),
                        onSelect = { i ->
                            mainThemeStore?.let { store ->
                                DesktopCoverAspect.write(store, DesktopCoverAspect.entries[i])
                            }
                        },
                    ),
                    DesktopSettingRow.Options(
                        title = "预览清晰度",
                        caption = "流量约十倍，刷新后生效",
                        options = spec.qualityTierLabels.map { it.second },
                        selectedIndex = spec.qualityTierLabels.indexOfFirst { it.first == previewQuality }.coerceAtLeast(0),
                        onSelect = { i -> prefs.setGalleryPreviewQuality(spec.qualityTierLabels[i].first) },
                    ),
                    DesktopSettingRow.Options(
                        title = "详情页清晰度",
                        caption = "详情大图取预览/大图/原图",
                        options = spec.detailQualityTierLabels.map { it.second },
                        selectedIndex = spec.detailQualityTierLabels.indexOfFirst { it.first == detailQuality }.coerceAtLeast(0),
                        onSelect = { i -> prefs.setDetailQuality(spec.detailQualityTierLabels[i].first) },
                    ),
                    DesktopSettingRow.Options(
                        title = "详情栏宽度",
                        caption = "详情页右栏信息位宽度",
                        options = DesktopDetailPaneWidth.entries.map { paneWidthLabel(it) },
                        selectedIndex = paneWidth.ordinal,
                        onSelect = { i -> prefs.setDetailPaneWidth(DesktopDetailPaneWidth.entries[i]) },
                    ),
                    DesktopSettingRow.Toggle(
                        title = "详情栏默认展开",
                        caption = "打开详情页时右栏先展开（页内收起不写回）",
                        checked = paneOpen,
                        onToggle = { prefs.setDetailPaneOpen(it) },
                    ),
                    DesktopSettingRow.Options(
                        title = "图片缓存上限",
                        caption = "重启后生效",
                        options = listOf("512MB", "1GB", "2GB"),
                        selectedIndex = imageCacheMbTier,
                        onSelect = { i -> prefs.setImageCacheMb(listOf(512, 1024, 2048)[i]) },
                    ),
                ),
            ),
            DesktopSettingsCard(
                title = "内容标记",
                caption = "依据站方标签，不靠画风推断",
                rows = listOf(
                    DesktopSettingRow.Toggle(
                        title = "屏蔽 AI 作品",
                        caption = "与漫画侧开关相互独立",
                        checked = blockAi,
                        onToggle = { prefs.setGalleryBlockAi(it) },
                    ),
                    DesktopSettingRow.Toggle(
                        title = "显示 AI 角标",
                        caption = "在卡片上标注 AI 标识",
                        checked = aiBadge,
                        onToggle = { prefs.setGalleryAiBadge(it) },
                    ),
                ),
            ),
            DesktopSettingsCard(
                title = "图片查看器",
                caption = "全屏大图界面的播放与预取行为",
                rows = listOf(
                    DesktopSettingRow.Options(
                        title = "自动连播间隔",
                        caption = "0 为不自动翻页",
                        options = spec.autoPlayTiers.map { if (it == 0) "不自动翻页" else "${it} 秒" },
                        selectedIndex = spec.autoPlayTiers.indexOf(autoPlaySec).coerceAtLeast(0),
                        onSelect = { i -> prefs.setGalleryAutoPlaySec(spec.autoPlayTiers[i]) },
                    ),
                    DesktopSettingRow.Options(
                        title = "提前加载",
                        caption = "看图时预取相邻卡片，提升翻页流畅度",
                        options = spec.preloadTierLabels.map { it.second },
                        selectedIndex = spec.preloadTierLabels.indexOfFirst { it.first == preload }.coerceAtLeast(0),
                        onSelect = { i -> prefs.setGalleryPreload(spec.preloadTierLabels[i].first) },
                    ),
                ),
            ),
            DesktopSettingsCard(
                title = "作品画布",
                caption = "作品详情页大图的承托：内衬、圆角、环境光与落影",
                rows = listOfNotNull(
                    DesktopSettingRow.Options(
                        title = "预设",
                        caption = "一键写全下面四枚；手调过任意一枚就回到「自定义」",
                        options = DesktopCanvasFrame.Preset.entries.map { it.label } + "自定义",
                        selectedIndex = DesktopCanvasFrame.presetOf(canvasFrame)?.ordinal
                            ?: DesktopCanvasFrame.Preset.entries.size,
                        onSelect = { i ->
                            val presets = DesktopCanvasFrame.Preset.entries
                            if (i < presets.size && mainThemeStore != null) {
                                DesktopCanvasFrame.update(mainThemeStore, presets[i].spec)
                            }
                        },
                    ),
                    mainThemeStore?.let { store ->
                        DesktopSettingRow.Slider(
                            title = "内衬",
                            caption = "画作与背板之间的留白厚度",
                            value = canvasFrame.padDp,
                            range = DesktopCanvasFrame.PAD_MIN..DesktopCanvasFrame.PAD_MAX,
                            steps = 23,
                            display = { v -> "${v.toInt()}dp" },
                            onChange = { v -> DesktopCanvasFrame.update(store, canvasFrame.copy(padDp = v)) },
                        )
                    },
                    mainThemeStore?.let { store ->
                        DesktopSettingRow.Slider(
                            title = "画作圆角",
                            caption = "图片自身的圆角，外框按同心圆自动加大一圈",
                            value = canvasFrame.radiusDp,
                            range = DesktopCanvasFrame.RADIUS_MIN..DesktopCanvasFrame.RADIUS_MAX,
                            steps = 31,
                            display = { v -> "${v.toInt()}dp" },
                            onChange = { v -> DesktopCanvasFrame.update(store, canvasFrame.copy(radiusDp = v)) },
                        )
                    },
                    mainThemeStore?.let { store ->
                        DesktopSettingRow.Slider(
                            title = "环境光",
                            caption = "画作四周的同图柔光；0 为关闭，越高边界越软",
                            value = canvasFrame.glow,
                            range = 0f..1f,
                            steps = 19,
                            display = { v -> "${(v * 100).toInt()}%" },
                            onChange = { v -> DesktopCanvasFrame.update(store, canvasFrame.copy(glow = v)) },
                        )
                    },
                    mainThemeStore?.let { store ->
                        DesktopSettingRow.Slider(
                            title = "落影",
                            caption = "画框向下的扩散阴影强度",
                            value = canvasFrame.shadow,
                            range = 0f..1f,
                            steps = 19,
                            display = { v -> "${(v * 100).toInt()}%" },
                            onChange = { v -> DesktopCanvasFrame.update(store, canvasFrame.copy(shadow = v)) },
                        )
                    },
                    mainThemeStore?.let { store ->
                        DesktopSettingRow.Action(
                            title = "重置",
                            caption = "回到推荐数值：内衬 10dp、圆角 16dp、环境光关闭、落影 38%",
                            buttonLabel = "重置",
                            onAction = { DesktopCanvasFrame.reset(store) },
                        )
                    },
                ),
            ),
            DesktopSettingsCard(
                title = "吉祥物",
                caption = "待机小薇薇：存在感、呼吸节奏、打盹与视线跟随",
                rows = listOfNotNull(
                    DesktopSettingRow.Options(
                        title = "状态模式",
                        caption = "「自动」按交互与闲置计时切换，其余锁档便于查验",
                        options = DesktopMascot.ModeOverride.entries.map {
                            listOf("自动", "安静待机", "我在听", "打盹")[it.ordinal]
                        },
                        selectedIndex = mascotSpec.modeOverride.ordinal,
                        onSelect = { i ->
                            mainThemeStore?.let { store ->
                                DesktopMascot.update(
                                    store,
                                    mascotSpec.copy(modeOverride = DesktopMascot.ModeOverride.entries[i]),
                                )
                            }
                        },
                    ),
                    mainThemeStore?.let { store ->
                        DesktopSettingRow.Slider(
                            title = "待机透明度",
                            caption = "形象在屏上的存在感",
                            value = mascotSpec.idleAlpha,
                            range = DesktopMascot.ALPHA_MIN..DesktopMascot.ALPHA_MAX,
                            steps = 47,
                            display = { v -> "${(v * 100).toInt()}%" },
                            onChange = { v -> DesktopMascot.update(store, mascotSpec.copy(idleAlpha = v)) },
                        )
                    },
                    mainThemeStore?.let { store ->
                        DesktopSettingRow.Slider(
                            title = "图标尺寸",
                            caption = "空态里形状的渲染边长",
                            value = mascotSpec.sizeDp,
                            range = DesktopMascot.SIZE_MIN..DesktopMascot.SIZE_MAX,
                            steps = 27,
                            display = { v -> "${v.toInt()}dp" },
                            onChange = { v -> DesktopMascot.update(store, mascotSpec.copy(sizeDp = v)) },
                        )
                    },
                    mainThemeStore?.let { store ->
                        DesktopSettingRow.Slider(
                            title = "呼吸周期",
                            caption = "一次完整呼吸的时长",
                            value = mascotSpec.breathCycle,
                            range = DesktopMascot.CYCLE_MIN..DesktopMascot.CYCLE_MAX,
                            steps = 54,
                            display = { v -> "%.1fs".format(v) },
                            onChange = { v -> DesktopMascot.update(store, mascotSpec.copy(breathCycle = v)) },
                        )
                    },
                    mainThemeStore?.let { store ->
                        DesktopSettingRow.Slider(
                            title = "呼吸幅度",
                            caption = "翅尖抬升与耳尖张开的倍率",
                            value = mascotSpec.breathAmp,
                            range = DesktopMascot.AMP_MIN..DesktopMascot.AMP_MAX,
                            steps = 15,
                            display = { v -> "%.1fx".format(v) },
                            onChange = { v -> DesktopMascot.update(store, mascotSpec.copy(breathAmp = v)) },
                        )
                    },
                    mainThemeStore?.let { store ->
                        DesktopSettingRow.Slider(
                            title = "打盹延迟",
                            caption = "无活动多久后进入打盹",
                            value = mascotSpec.dozeDelay,
                            range = DesktopMascot.DOZE_MIN..DesktopMascot.DOZE_MAX,
                            steps = 16,
                            display = { v -> "${v.toInt()}s" },
                            onChange = { v -> DesktopMascot.update(store, mascotSpec.copy(dozeDelay = v)) },
                        )
                    },
                    mainThemeStore?.let { store ->
                        DesktopSettingRow.Toggle(
                            title = "鼠标视线跟随",
                            caption = "指针活跃时双眼微随光标",
                            checked = mascotSpec.gazeFollow,
                            onToggle = { DesktopMascot.update(store, mascotSpec.copy(gazeFollow = it)) },
                        )
                    },
                    mainThemeStore?.let { store ->
                        DesktopSettingRow.Action(
                            title = "重置",
                            caption = "回到默认值：透明度 28%、64dp、呼吸 4.8s、打盹 15s",
                            buttonLabel = "重置",
                            onAction = { DesktopMascot.reset(store) },
                        )
                    },
                ),
            ),
        )
    }
}

/** 构造「视频与动图」分区设置卡。 */
@Composable
internal fun buildMediaSettingsCards(
    prefs: DesktopGalleryPreferences,
): List<DesktopSettingsCard> {
    val spec = DesktopGallerySettingsSpec
    val videoAutoPlay by prefs.videoAutoPlay.collectAsState()
    val animated by prefs.galleryAnimated.collectAsState()
    val backdrop by prefs.galleryBackdrop.collectAsState()

    return listOf(
        DesktopSettingsCard(
            title = "自动播放",
            caption = "详情页进入时是否自动播放",
            rows = listOf(
                DesktopSettingRow.Toggle(
                    title = "视频自动播放",
                    caption = "关闭后视频停在首帧，手动再播",
                    checked = videoAutoPlay,
                    onToggle = { prefs.setVideoAutoPlay(it) },
                ),
                DesktopSettingRow.Options(
                    title = "动图逐帧播",
                    caption = "booru gif / pixiv ugoira 是否自动逐帧循环（NEVER ⇒ 停在首帧）",
                    options = spec.animatedTierLabels.map { it.second },
                    selectedIndex = spec.animatedTierLabels.indexOfFirst { it.first == animated }.coerceAtLeast(0),
                    onSelect = { i -> prefs.setGalleryAnimated(spec.animatedTierLabels[i].first) },
                ),
            ),
        ),
        DesktopSettingsCard(
            title = "大图显示",
            caption = "全屏看图的背景与叠加",
            rows = listOf(
                DesktopSettingRow.Options(
                    title = "画布底色",
                    caption = "图片查看器的背景色彩",
                    options = spec.backdropTierLabels.map { it.second },
                    selectedIndex = spec.backdropTierLabels.indexOfFirst { it.first == backdrop }.coerceAtLeast(0),
                    onSelect = { i -> prefs.setGalleryBackdrop(spec.backdropTierLabels[i].first) },
                ),
            ),
        ),
    )
}

/** 构造「通用与外观」分区设置卡。 */
@Composable
internal fun buildAppearanceSettingsCards(
    mainThemeStore: JsonKeyValueStore?,
    isEn: Boolean,
): List<DesktopSettingsCard> {
    val themeMode by DesktopThemeMode.mode.collectAsState()
    val uiScaleTier by DesktopUiScale.tier.collectAsState()
    val petVisible by DesktopPetVisibility.visible.collectAsState()
    val backdropMode by DesktopBackdropMode.mode.collectAsState()
    val railBlur by DesktopRailBlur.visible.collectAsState()
    val topBarBlur by DesktopTopBarBlur.visible.collectAsState()

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
                        caption = if (isEn) "Dark, Light or System mode" else "浅色为 2026-11-04 首版，深色装饰件或有残留",
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
                mainThemeStore?.let {
                    DesktopSettingRow.Toggle(
                        title = if (isEn) "Backdrop Blur" else "底板模糊",
                        caption = if (isEn) "Solid background when disabled" else "关闭后内容区为实底",
                        checked = backdropMode != DesktopBackdropMode.SOLID,
                        onToggle = { next ->
                            DesktopBackdropMode.select(
                                if (next) DesktopBackdropMode.ACRYLIC else DesktopBackdropMode.SOLID,
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
                mainThemeStore?.let { skinStore ->
                    DesktopSettingRow.Options(
                        title = if (isEn) "Home Skin" else "首页皮肤",
                        caption = if (isEn) "Acrylic: translucent sidebar & home" else "Acrylic：玻璃侧栏与通透首页",
                        options = listOf(DesktopHomeSkin.LIGHT),
                        selectedIndex = 0,
                        onSelect = { DesktopHomeSkin.write(skinStore, DesktopHomeSkin.LIGHT) },
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
