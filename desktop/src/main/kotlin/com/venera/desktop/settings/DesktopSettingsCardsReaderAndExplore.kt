package com.venera.desktop.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.venera.compose.data.api.ReaderPreferences
import com.venera.compose.data.platform.JsonKeyValueStore
import com.venera.desktop.gallery.data.DesktopComicReaderPreferences
import com.venera.desktop.gallery.data.DesktopGalleryContentGuard
import com.venera.desktop.gallery.ui.DESKTOP_READER_MODE_CHOICES
import com.venera.desktop.gallery.ui.desktopMaskModeLabels
import com.venera.desktop.reader.DesktopAiUpscaleManager
import com.venera.desktop.reader.DesktopReaderPreferences
import com.venera.desktop.reader.ReaderFit

/** 构造「探索与卡片」分区设置卡（分级遮罩 + 屏蔽规则入口）。 */
@Composable
internal fun buildExploreSettingsCards(
    guard: DesktopGalleryContentGuard,
    onOpenBlockingRules: ((String) -> Unit)?,
    onMaskModeError: (String?) -> Unit,
): List<DesktopSettingsCard> {
    val maskMode by guard.nsfwMaskMode.collectAsState()
    val guardRules by guard.rules.collectAsState()
    val maskLabels = desktopMaskModeLabels()

    return buildList {
        add(
            DesktopSettingsCard(
                title = "内容分级",
                caption = "成人内容显示档位",
                rows = listOf(
                    DesktopSettingRow.Options(
                        title = "遮罩档",
                        caption = "不过滤 / 封面打码 / 彻底隐藏",
                        options = maskLabels.map { it.second },
                        selectedIndex = maskLabels.indexOfFirst { it.first == maskMode }.coerceAtLeast(0),
                        onSelect = { i ->
                            val ok = guard.setNsfwMaskMode(maskLabels[i].first)
                            onMaskModeError(
                                if (ok) {
                                    null
                                } else {
                                    println("D_遮罩档 写盘被拒 档位只接受 OFF/BLUR/HIDE 递来=${maskLabels[i].first}")
                                    "切换失败：设置未保存"
                                },
                            )
                        },
                    ),
                ),
            ),
        )
        if (onOpenBlockingRules != null) {
            add(
                DesktopSettingsCard(
                    title = "屏蔽规则",
                    caption = "命中即在画廊与封面遮罩里挡掉",
                    rows = listOf(
                        DesktopSettingRow.Entry("关键词屏蔽", "按包含匹配标题/作者/标签", "${guardRules.count { it.type == "KEYWORD" }} 条", onOpen = { onOpenBlockingRules("KEYWORD") }),
                        DesktopSettingRow.Entry("屏蔽标签", "按包含匹配标签", "${guardRules.count { it.type == "TAG" }} 条", onOpen = { onOpenBlockingRules("TAG") }),
                        DesktopSettingRow.Entry("屏蔽画师", "按包含匹配作者", "${guardRules.count { it.type == "AUTHOR" }} 条", onOpen = { onOpenBlockingRules("AUTHOR") }),
                        DesktopSettingRow.Entry("屏蔽作品", "按编号精确匹配", "${guardRules.count { it.type == "COMIC_ID" }} 条", onOpen = { onOpenBlockingRules("COMIC_ID") }),
                    ),
                ),
            )
        }
    }
}

/** 构造「阅读器」分区设置卡。 */
@Composable
internal fun buildReaderSettingsCards(
    readerPrefs: DesktopComicReaderPreferences?,
    businessReaderPrefs: ReaderPreferences?,
    mainThemeStore: JsonKeyValueStore?,
): List<DesktopSettingsCard> {
    val readingMode = readerPrefs?.readingMode?.collectAsState()?.value
    val reverseChapters = businessReaderPrefs?.reverseChapterOrder?.collectAsState()?.value
    val readerBgIndex by DesktopReaderBackground.index.collectAsState()
    val readerUiPrefs by DesktopReaderPreferences.state.collectAsState()

    return listOfNotNull(
        readerPrefs?.let {
            listOf(
                DesktopSettingsCard(
                    title = "阅读模式与排版",
                    caption = "翻页方向、条漫排版与连续阅读档位",
                    rows = listOf(
                        DesktopSettingRow.Options(
                            title = "翻页模式",
                            caption = "条漫 / 日漫 / 美漫 / 对开",
                            options = DESKTOP_READER_MODE_CHOICES.map { it.label },
                            selectedIndex = DESKTOP_READER_MODE_CHOICES.indexOf(readingMode).coerceAtLeast(0),
                            onSelect = { i ->
                                DesktopReaderPreferences.update { it.copy(reverseReading = false) }
                                readerPrefs.setReadingMode(DESKTOP_READER_MODE_CHOICES[i])
                            },
                        ),
                        DesktopSettingRow.Toggle(
                            title = "章节倒序",
                            caption = "详情目录按最新话在前（与详情页同一实例）",
                            checked = reverseChapters == true,
                            onToggle = { businessReaderPrefs?.setReverseChapterOrder(it) },
                        ),
                        DesktopSettingRow.Options(
                            title = "条漫版芯宽度",
                            caption = "垂直连读时的版芯最大宽度",
                            options = listOf("100%", "85%", "70%", "55%"),
                            selectedIndex = when (readerUiPrefs.stripMaxWidthPercent) {
                                100 -> 0
                                85 -> 1
                                70 -> 2
                                else -> 3
                            },
                            onSelect = { i ->
                                val pct = listOf(100, 85, 70, 55)[i]
                                DesktopReaderPreferences.update { it.copy(stripMaxWidthPercent = pct) }
                            },
                        ),
                        DesktopSettingRow.Options(
                            title = "条漫页面间距",
                            caption = "垂直连读时每页缝隙高度",
                            options = listOf("0px", "8px", "16px", "24px"),
                            selectedIndex = when (readerUiPrefs.stripPageGap) {
                                0 -> 0
                                8 -> 1
                                16 -> 2
                                else -> 3
                            },
                            onSelect = { i ->
                                val gap = listOf(0, 8, 16, 24)[i]
                                DesktopReaderPreferences.update { it.copy(stripPageGap = gap) }
                            },
                        ),
                        DesktopSettingRow.Options(
                            title = "阅读底色",
                            caption = "漫画画布的显示背景",
                            options = DesktopReaderBackground.OPTIONS,
                            selectedIndex = readerBgIndex,
                            onSelect = { i ->
                                mainThemeStore?.let { store ->
                                    DesktopReaderBackground.write(store, DesktopReaderBackground.OPTIONS[i])
                                }
                            },
                        ),
                    ),
                ),
            )
        },
        listOf(
            DesktopSettingsCard(
                title = "翻页与交互",
                caption = "翻页过渡、手势拖拽、分格引导与点击热区",
                rows = listOf(
                    DesktopSettingRow.Options(
                        title = "页面适配",
                        caption = "适应屏幕 / 适应宽度 / 原始",
                        options = ReaderFit.entries.map { it.label },
                        selectedIndex = readerUiPrefs.fitIndex,
                        onSelect = { i -> DesktopReaderPreferences.update { it.copy(fitIndex = i) } },
                    ),
                    DesktopSettingRow.Options(
                        title = "页面旋转",
                        caption = "竖排页面转横看",
                        options = listOf("0°", "90°", "180°", "270°"),
                        selectedIndex = readerUiPrefs.rotationIndex,
                        onSelect = { i -> DesktopReaderPreferences.update { it.copy(rotationIndex = i) } },
                    ),
                    DesktopSettingRow.Options(
                        title = "动画与浏览模式",
                        caption = "翻页动画与分格导览视图",
                        options = listOf("导览视图", "淡出", "翻页"),
                        selectedIndex = (readerUiPrefs.turnEffect - 2).coerceIn(0, 2),
                        onSelect = { i -> DesktopReaderPreferences.update { it.copy(turnEffect = i + 2) } },
                    ),
                    DesktopSettingRow.Toggle(
                        title = "平滑翻页",
                        caption = "关闭时立刻切页",
                        checked = readerUiPrefs.smoothTurning,
                        onToggle = { v -> DesktopReaderPreferences.update { it.copy(smoothTurning = v) } },
                    ),
                    DesktopSettingRow.Options(
                        title = "3D 翻页透视",
                        caption = "3D 轴心翻折的透视深度",
                        options = listOf("10 (微透视)", "20 (标准)", "35 (强立体)", "50 (深景深)"),
                        selectedIndex = when (readerUiPrefs.turnPerspective) {
                            10 -> 0
                            20 -> 1
                            35 -> 2
                            else -> 3
                        },
                        onSelect = { i ->
                            val p = listOf(10, 20, 35, 50)[i]
                            DesktopReaderPreferences.update { it.copy(turnPerspective = p) }
                        },
                    ),
                    DesktopSettingRow.Options(
                        title = "导览视图模式",
                        caption = "分格引导浏览画面的视觉呈现",
                        options = listOf("聚焦", "隐藏", "沉浸式"),
                        selectedIndex = readerUiPrefs.guidedViewStyle.coerceIn(0, 2),
                        onSelect = { i -> DesktopReaderPreferences.update { it.copy(guidedViewStyle = i) } },
                    ),
                    DesktopSettingRow.Options(
                        title = "点击翻页区",
                        caption = "左右边缘的翻页热区宽度",
                        options = listOf("15%", "25%", "33%"),
                        selectedIndex = listOf(15, 25, 33).indexOf(readerUiPrefs.tapZonePercent).coerceAtLeast(0),
                        onSelect = { i ->
                            val pct = listOf(15, 25, 33)[i]
                            DesktopReaderPreferences.update { it.copy(tapZonePercent = pct) }
                        },
                    ),
                    DesktopSettingRow.Slider(
                        title = "放大倍率",
                        caption = "悬浮放大镜的缩放倍数",
                        value = readerUiPrefs.magnifierScale,
                        range = 1.5f..4f,
                        steps = 25,
                        display = { v -> String.format("%.1f×", v) },
                        onChange = { v -> DesktopReaderPreferences.update { it.copy(magnifierScale = v) } },
                    ),
                    DesktopSettingRow.Options(
                        title = "预加载页数",
                        caption = "向后预取的页数",
                        options = listOf("2 页", "4 页", "8 页"),
                        selectedIndex = listOf(2, 4, 8).indexOf(readerUiPrefs.preloadCount).coerceAtLeast(0),
                        onSelect = { i ->
                            val n = listOf(2, 4, 8)[i]
                            DesktopReaderPreferences.update { it.copy(preloadCount = n) }
                        },
                    ),
                    DesktopSettingRow.Toggle(
                        title = "翻页音效",
                        caption = "翻页时播放纸页声",
                        checked = readerUiPrefs.pageSoundOn,
                        onToggle = { v -> DesktopReaderPreferences.update { it.copy(pageSoundOn = v) } },
                    ),
                    DesktopSettingRow.Toggle(
                        title = "双页展示",
                        caption = "对开模式下两页并排（关闭=每次一页）",
                        checked = readerUiPrefs.doublePageOn,
                        onToggle = { v -> DesktopReaderPreferences.update { it.copy(doublePageOn = v) } },
                    ),
                    DesktopSettingRow.Toggle(
                        title = "封面独占",
                        caption = "封面页不与第 2 页并排",
                        checked = readerUiPrefs.coverSinglePage,
                        onToggle = { v -> DesktopReaderPreferences.update { it.copy(coverSinglePage = v) } },
                    ),
                    DesktopSettingRow.Toggle(
                        title = "不用于横幅画面",
                        caption = "宽幅页面独占一屏，不参与并排",
                        checked = readerUiPrefs.keepWideSingle,
                        onToggle = { v -> DesktopReaderPreferences.update { it.copy(keepWideSingle = v) } },
                    ),
                    DesktopSettingRow.Toggle(
                        title = "与下一个水平对齐",
                        caption = "自动补白，让横幅落成完整跨页",
                        checked = readerUiPrefs.alignNextHorizontal,
                        onToggle = { v -> DesktopReaderPreferences.update { it.copy(alignNextHorizontal = v) } },
                    ),
                    DesktopSettingRow.Toggle(
                        title = "开头空白页",
                        caption = "首页前补白页，第 1 页单独落右侧",
                        checked = readerUiPrefs.blankPage,
                        onToggle = { v -> DesktopReaderPreferences.update { it.copy(blankPage = v) } },
                    ),
                    DesktopSettingRow.Toggle(
                        title = "方向键翻页",
                        caption = "用方向键切换页面（滚轮只缩放）",
                        checked = readerUiPrefs.arrowKeysOn,
                        onToggle = { v -> DesktopReaderPreferences.update { it.copy(arrowKeysOn = v) } },
                    ),
                ),
            ),
            DesktopSettingsCard(
                title = "画质、滤镜与扩展",
                caption = "色调滤镜与离线推理引擎（与阅读器筛选页同源）",
                rows = listOf(
                    DesktopSettingRow.Options(
                        title = "插值质量",
                        caption = "画面缩放时的插值采样模式",
                        options = listOf("平滑双线性", "高清双三次", "像素近邻"),
                        selectedIndex = readerUiPrefs.filterQualityIndex.coerceIn(0, 2),
                        onSelect = { i -> DesktopReaderPreferences.update { it.copy(filterQualityIndex = i) } },
                    ),
                    DesktopSettingRow.Slider(
                        title = "亮度",
                        caption = "0–400%，100% 为原画面",
                        value = readerUiPrefs.brightness,
                        range = 0f..4f,
                        steps = 80,
                        display = { v -> "${(v * 100).toInt()}%" },
                        onChange = { v -> DesktopReaderPreferences.update { it.copy(brightness = v) } },
                    ),
                    DesktopSettingRow.Slider(
                        title = "饱和度",
                        caption = "0–400%，100% 为原画面",
                        value = readerUiPrefs.saturation,
                        range = 0f..4f,
                        steps = 80,
                        display = { v -> "${(v * 100).toInt()}%" },
                        onChange = { v -> DesktopReaderPreferences.update { it.copy(saturation = v) } },
                    ),
                    DesktopSettingRow.Slider(
                        title = "对比度",
                        caption = "0–400%，100% 为原画面",
                        value = readerUiPrefs.contrast,
                        range = 0f..4f,
                        steps = 80,
                        display = { v -> "${(v * 100).toInt()}%" },
                        onChange = { v -> DesktopReaderPreferences.update { it.copy(contrast = v) } },
                    ),
                    DesktopSettingRow.Slider(
                        title = "色温",
                        caption = "泛黄怀旧强度",
                        value = readerUiPrefs.filterSepia,
                        range = 0f..100f,
                        steps = 0,
                        display = { v -> "${v.toInt()}" },
                        onChange = { v -> DesktopReaderPreferences.update { it.copy(filterSepia = v) } },
                    ),
                    DesktopSettingRow.Toggle(
                        title = "黑白",
                        caption = "画面转灰度",
                        checked = readerUiPrefs.filterInvert,
                        onToggle = { v -> DesktopReaderPreferences.update { it.copy(filterInvert = v) } },
                    ),
                    DesktopSettingRow.Toggle(
                        title = "负片",
                        caption = "反转画面明暗颜色",
                        checked = readerUiPrefs.filterNegative,
                        onToggle = { v -> DesktopReaderPreferences.update { it.copy(filterNegative = v) } },
                    ),
                    DesktopSettingRow.Action(
                        title = "AI超分引擎",
                        caption = "独立离线 Vulkan 推理组件 (${DesktopAiUpscaleManager.estimatedSizeText()})",
                        buttonLabel = "清理缓存",
                        onAction = { DesktopAiUpscaleManager.cleanEngine() },
                    ),
                ),
            ),
        ),
    ).flatten()
}
