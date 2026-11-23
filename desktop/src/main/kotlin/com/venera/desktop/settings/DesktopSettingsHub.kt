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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.components.LocalUxPlatformServices
import com.venera.compose.gallery.data.GelbooruCredentialState
import com.venera.compose.gallery.data.PixivAuthState
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.gallery.data.DesktopComicReaderPreferences
import com.venera.desktop.gallery.data.DesktopGalleryContentGuard
import com.venera.desktop.gallery.data.DesktopGalleryPreferences
import com.venera.desktop.gallery.data.DesktopDirChooser
import com.venera.desktop.gallery.data.DesktopPixivAccount
import com.venera.desktop.gallery.data.desktopGallerySaveDir
import com.venera.desktop.gallery.data.formatBytes
import com.venera.desktop.gallery.ui.openInBrowser
import com.venera.desktop.gallery.ui.DesktopGallerySettingsSpec
import com.venera.desktop.gallery.ui.desktopMaskModeLabels
import com.venera.desktop.ui.SettingsCardSpec
import com.venera.desktop.ui.SettingsControlPlacement
import com.venera.desktop.ui.SettingsPageTemplate
import com.venera.desktop.ui.SettingsRowSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch



/**
 * 桌面设置中枢 —— 按与最新指令重做。
 *
 * ##站点账号与登录系统集成：
 * 1. **Gelbooru 登录系统**：
 *    - 摆放在设置中的「源与账号」区域；
 *    - 支持显示当前 User ID 登录状态；
 *    - 点击呼出桌面级登录弹窗，输入 User ID 与 API Key，站方验证通过后自动落盘推流；
 *    - 提供注销/登出把手，安全清除凭据。
 * 2. **Pixiv 账号系统整合**：
 *    - 在设置中显示 Pixiv 登录状态、用户昵称、账号 ID 及会员徽标；
 *    - 提供直达「我的 Pixiv」二级页与「登录 Pixiv」/「退出登录」把手。
 */
/** 桌面版本号：关于区、更新检查与弹窗尾注的唯一出处。 */
internal const val DESKTOP_APP_VERSION = "2.1.1"

internal enum class DesktopSettingsSection(
    val titleZh: String,
    val titleEn: String,
    val groupZh: String,
    val groupEn: String,
) {
    /** 通用与外观：装饰档（素/纹样/神社）。 */
    APPEARANCE("通用与外观", "Appearance", "浏览", "Browse"),

    /** 画廊与浏览：墙上布局 + 大图页 + 内容标记 + 桌面暂不支持。 */
    GALLERY("画廊与浏览", "Gallery", "浏览", "Browse"),

    /** 探索与卡片：遮罩档。 */
    EXPLORE("探索与卡片", "Explore", "浏览", "Browse"),

    /** 阅读器：漫画阅读模式五档。 */
    READER("阅读器", "Reader", "阅读与收藏", "Reading & Favorites"),

    /** 源与账号：源管理、Gelbooru 登录、Pixiv 账号入口。 */
    SOURCES("源与账号", "Sources & Accounts", "系统", "System"),

    /** 视频与动图：自动播放总闸 + 动图逐帧档 + 大图观感。 */
    MEDIA("视频与动图", "Media & Video", "系统", "System"),

    /** 收藏：快捷夹 / 新收藏位置 / 排序 / 追更夹（档一接线）。 */
    FAVORITES("收藏", "Favorites", "阅读与收藏", "Reading & Favorites"),

    /** 网络：代理三枚 / 缓存上限（改档重启生效，行上如实标注）。 */
    NETWORK("网络", "Network", "系统", "System"),

    /** 数据与备份：备份/历史/统计入口。 */
    DATA("数据与备份", "Data & Backup", "系统", "System"),

    /** 桌面能力：尚未接入独立设置子页的项，照实点名，不摆假开关。 */
    DESKTOP_CAPABILITIES("桌面能力", "Capabilities", "系统", "System"),

    /** 关于与更新：版本一句。 */
    ABOUT("关于与更新", "About & Updates", "系统", "System");

    val title: String get() = if (DesktopLanguage.isEnglish()) titleEn else titleZh
    val group: String get() = if (DesktopLanguage.isEnglish()) groupEn else groupZh
}

/** 一枚设置行：屏上「左标题右控件」那一行。 */
internal sealed interface DesktopSettingRow {

    /** 行标题。 */
    val title: String

    /** 说明（可空 = 不摆那行小字）。 */
    val caption: String?

    /** 开关。 */
    data class Toggle(
        override val title: String,
        override val caption: String?,
        val checked: Boolean,
        val enabled: Boolean = true,
        val onToggle: (Boolean) -> Unit,
    ) : DesktopSettingRow

    /** 枚举档：短档名走分段器，长档名走下拉。 */
    data class Options(
        override val title: String,
        override val caption: String?,
        val options: List<String>,
        val selectedIndex: Int,
        val onSelect: (Int) -> Unit,
    ) : DesktopSettingRow

    /** 二级入口（源管理、备份那一族）。[clickable]=false = 纯展示行，不摆 ›、不计徽标。 */
    data class Entry(
        override val title: String,
        override val caption: String?,
        val value: String?,
        val onOpen: () -> Unit,
        val clickable: Boolean = true,
    ) : DesktopSettingRow

    /** 操作按钮（登录、退出等行内把手）。 */
    data class Action(
        override val title: String,
        override val caption: String?,
        val buttonLabel: String,
        val onAction: () -> Unit,
        val buttonColor: Color? = null,
        val enabled: Boolean = true,
    ) : DesktopSettingRow

    /** 自由文本行（代理主机/端口那一族）：失焦或回车提交 [onCommit]。 */
    data class Text(
        override val title: String,
        override val caption: String?,
        val value: String,
        val onCommit: (String) -> Unit,
    ) : DesktopSettingRow

    /** 点名缺席（「不可用」小签那档）。 */
    data class Absent(
        override val title: String,
        override val caption: String?,
    ) : DesktopSettingRow

    /** 滑条行：[value] 受控，[display] 把值转成右侧短文本。 */
    data class Slider(
        override val title: String,
        override val caption: String?,
        val value: Float,
        val range: ClosedFloatingPointRange<Float>,
        /** 中间刻度数（0=连续）。 */
        val steps: Int,
        val display: (Float) -> String,
        val onChange: (Float) -> Unit,
    ) : DesktopSettingRow
}

/** 一张卡：卡头（标题+副题）+ 若干行。 */
internal data class DesktopSettingsCard(
    val title: String,
    val caption: String?,
    val rows: List<DesktopSettingRow>,
)

@Composable
internal fun DesktopSettingsHub(
    mode: com.venera.compose.components.WideScreenLayoutMode,
    prefs: DesktopGalleryPreferences,
    guard: DesktopGalleryContentGuard,
    readerPrefs: DesktopComicReaderPreferences?,
    onOpenSourceManage: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenStats: () -> Unit,
    /** 缓存占用/清理用（null ⇒ 缓存那一卡不摆，与"没写口就不摆那枚钮"同口径）。 */
    paths: com.venera.compose.data.platform.PathProvider? = null,
    gelbooruState: GelbooruCredentialState? = null,
    pixivAccount: DesktopPixivAccount? = null,
    /** pixiv 匿名腿（userHead 头像/名字那跳吃它）；null ⇒ 登录态也只落账号名不落头像。 */
    pixivWorks: com.venera.compose.gallery.data.GalleryPixivWorks? = null,
    onOpenPixivAccount: (() -> Unit)? = null,
    onOpenPixivLogin: (() -> Unit)? = null,
    /** 打开某类屏蔽规则管理子页（KEYWORD/TAG/AUTHOR/COMIC_ID）。null ⇒ 不摆入口（无落点不摆假把手）。 */
    onOpenBlockingRules: ((String) -> Unit)? = null,
    /** 漫画偏好（快捷夹/新收藏位置/排序/追更夹的读写口）。null ⇒ 收藏区照实显缺。 */
    comicPrefs: com.venera.compose.data.api.ComicPreferences? = null,
    /** BusinessPorts 那颗阅读偏好（章节倒序的读写口；与详情页同实例才同步）。 */
    businessReaderPrefs: com.venera.compose.data.api.ReaderPreferences? = null,
    /** 反搜凭据仓（SauceNAO key 的读写口）。null ⇒ 那一行不摆。 */
    galleryCreds: com.venera.desktop.gallery.data.DesktopGalleryCredentials? = null,
    /** 网络偏好（代理/缓存上限的读写口；档二落真）。null ⇒ 网络区照实显缺。 */
    networkPrefs: com.venera.compose.data.api.NetworkPreferences? = null,
    /** 画廊句柄（更新检查吃它的 engine 那台 OkHttp——与取图同一台，代理/缓存档同受益）。null ⇒ 关于区不摆检查行。 */
    galleryHandle: com.venera.desktop.gallery.data.DesktopGalleryHandle? = null,
    /** 作品墙密度（紧凑 / 标准 / 宽松）当前值与写口，全部作品墙同读。 */
    wallDensity: com.venera.desktop.gallery.ui.DesktopWallDensity =
        com.venera.desktop.gallery.ui.DesktopWallDensity.Loose,
    onPickWallDensity: (com.venera.desktop.gallery.ui.DesktopWallDensity) -> Unit = {},
    /** 关闭面板（弹窗形态下回到主界面；全屏形态下为空即不摆关闭钮）。 */
    onClose: (() -> Unit)? = null,
) {
    val tokens = VeneraTokens
    val ux = LocalUxPlatformServices.current
    val scope = rememberCoroutineScope()
    var section by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf(DesktopSettingsSection.APPEARANCE)
    }
    // 总览舱墙：true = Bento 舱墙，点舱进对应分区详情。
    var query by remember { mutableStateOf("") }
    var showGelbooruDialog by remember { mutableStateOf(false) }
    // 缓存占用（-1 = 未知，进"数据与备份"区时现测）。清理只回收 pixiv+ugoira（无锁），okhttp 活缓存不硬删。
    var cacheBytes by remember { mutableStateOf(-1L) }
    var clearingCache by remember { mutableStateOf(false) }
    // 遮罩档写盘失败时念在设置页 note 那一行（桌面 toast 是静默降级，不能靠它报失败）。
    var maskModeError by remember { mutableStateOf<String?>(null) }
    // 目录框打不开时念在「下载目录」那一行 caption（原来只进日志、屏上静默）。
    var dirPickerError by remember { mutableStateOf<String?>(null) }

    // ── 档位读值（全部 collect 那几枚件上的既有流）──
    val wallLayout by prefs.wallLayout.collectAsState()
    val previewQuality by prefs.galleryPreviewQuality.collectAsState()
    // ── 图库设置扩批五枚（详情页清晰度 / 卡片大小 / 详情栏两枚 / 缓存上限）──
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
    val videoAutoPlay by prefs.videoAutoPlay.collectAsState()
    val downloadDir by prefs.downloadDir.collectAsState()
    val downloadUseDefaultDir by prefs.downloadUseDefaultDir.collectAsState()
    val comicDownloadDir by prefs.comicDownloadDir.collectAsState()
    val maskMode by guard.nsfwMaskMode.collectAsState()
    val guardRules by guard.rules.collectAsState()
    val readingMode = readerPrefs?.readingMode?.collectAsState()?.value
    // ── 档一接线：收藏四枚 + 反搜凭据 + Pixiv 画质三档 + 章节倒序 ──
    val quickFavorite = comicPrefs?.quickFavorite?.collectAsState()?.value
    val newFavoriteAddTo = comicPrefs?.newFavoriteAddTo?.collectAsState()?.value
    val favoriteSortOrder = comicPrefs?.favoriteSortOrder?.collectAsState()?.value
    val sauceNaoHasKey = galleryCreds?.sauceNaoHasKey?.collectAsState()?.value
    val pixivIllustQ = prefs.pixivIllustQuality.collectAsState().value
    val pixivMangaQ = prefs.pixivMangaQuality.collectAsState().value
    val pixivZoomQ = prefs.pixivZoomQuality.collectAsState().value
    // 卡片预览档位（低画质 / 中画质 / 大图）。
    val pixivPreviewQ = prefs.pixivPreviewQuality.collectAsState().value
    val reverseChapters = businessReaderPrefs?.reverseChapterOrder?.collectAsState()?.value
    var showSauceNaoDialog by remember { mutableStateOf(false) }
    // ── 档二：网络偏好（代理/缓存上限）+ 主题模式 ──
    val proxyType = networkPrefs?.proxyType?.collectAsState()?.value ?: ""
    val proxyHost = networkPrefs?.proxyHost?.collectAsState()?.value ?: ""
    val proxyPort = networkPrefs?.proxyPort?.collectAsState()?.value ?: 0
    val httpCacheMb = networkPrefs?.httpCacheMaxMb?.collectAsState()?.value ?: 100
    val downloadThreads = networkPrefs?.downloadThreads?.collectAsState()?.value ?: 1
    val themeMode by com.venera.desktop.settings.DesktopThemeMode.mode.collectAsState()
    val uiScaleTier by com.venera.desktop.settings.DesktopUiScale.tier.collectAsState()
    val coverAspectTier by com.venera.desktop.settings.DesktopCoverAspect.tier.collectAsState()
    val gridDensityTier by com.venera.desktop.settings.DesktopGridDensity.tier.collectAsState()
    val comicCardLayout by com.venera.desktop.settings.DesktopComicCardLayout.config.collectAsState()
    val readerBgIndex by com.venera.desktop.settings.DesktopReaderBackground.index.collectAsState()
    // ──：首页皮肤档（经典/浅色实验，热切热存）──
    val homeSkin by com.venera.desktop.settings.DesktopHomeSkin.mode.collectAsState()
    val petVisible by com.venera.desktop.settings.DesktopPetVisibility.visible.collectAsState()
    val backdropMode by com.venera.desktop.settings.DesktopBackdropMode.mode.collectAsState()
    val railBlur by com.venera.desktop.settings.DesktopRailBlur.visible.collectAsState()
    val topBarBlur by com.venera.desktop.settings.DesktopTopBarBlur.visible.collectAsState()
    val tagTranslationMode by com.venera.desktop.settings.DesktopTagTranslation.mode.collectAsState()
    val pximgMirror by com.venera.desktop.settings.DesktopPximgMirror.host.collectAsState()
    val mainThemeStore = paths?.let {
        com.venera.compose.data.platform.JsonKeyValueStore(
            com.venera.compose.data.platform.PreferenceKeys.PREFS_NAME,
            it,
        )
    }
    val canvasFrame by com.venera.desktop.settings.DesktopCanvasFrame.current.collectAsState()
    val mascotSpec by com.venera.desktop.settings.DesktopMascot.current.collectAsState()
    val languageMode by com.venera.desktop.settings.DesktopLanguage.mode.collectAsState()
    val isEn = languageMode == com.venera.desktop.settings.DesktopLanguage.EN

    val spec = DesktopGallerySettingsSpec
    val maskLabels = desktopMaskModeLabels()

    // 进"数据与备份"区时现测缓存占用（IO 线程，不卡 UI）；总览舱墙的数据舱也要这枚真数。
    androidx.compose.runtime.LaunchedEffect(paths) {
        if (paths != null) {
            cacheBytes = com.venera.desktop.gallery.data.DesktopCacheMaintenance.measureBytes(paths)
        }
    }

    // ── 每一组的卡（按稿：浏览 / 阅读与收藏 / 系统 三大组）──
    val galleryCards = remember(
        languageMode,
        previewQuality, blockAi, aiBadge, autoPlaySec, preload, animated, backdrop, tagTranslationMode,
        // 布局 / 密度两档也进 remember 键，改设置后选中态立刻重算（从前 wallLayout 漏键）。
        // 图库设置扩批五枚同纪律：不进键就是"改了不动"的假开关。
        wallLayout, wallDensity, cardSize, detailQuality, paneWidth, paneOpen, imageCacheMb, coverAspectTier,
        // 作品画布四枚同理：不进键，滑块拖完选中态与预设归属不会重算。
        canvasFrame,
    ) {
        listOf(
            DesktopSettingsCard(
                title = "标签译名",
                caption = "画廊标签的中文显示档",
                rows = listOf(
                    DesktopSettingRow.Options(
                        title = "译文档位",
                        caption = "简体/繁体共用 cn 列，无真差异故不列",
                        options = listOf(
                            com.venera.desktop.settings.DesktopTagTranslation.SYSTEM,
                            com.venera.desktop.settings.DesktopTagTranslation.OFF,
                        ),
                        selectedIndex = if (tagTranslationMode == com.venera.desktop.settings.DesktopTagTranslation.OFF) 1 else 0,
                        onSelect = { i ->
                            mainThemeStore?.let { store ->
                                com.venera.desktop.settings.DesktopTagTranslation.write(
                                    store,
                                    if (i == 1) com.venera.desktop.settings.DesktopTagTranslation.OFF
                                    else com.venera.desktop.settings.DesktopTagTranslation.SYSTEM,
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
                        selectedIndex = if (wallLayout == com.venera.compose.gallery.domain.GalleryWallLayout.Grid) 1 else 0,
                        onSelect = { i ->
                            prefs.setWallLayout(
                                if (i == 1) com.venera.compose.gallery.domain.GalleryWallLayout.Grid
                                else com.venera.compose.gallery.domain.GalleryWallLayout.Staggered
                            )
                        },
                    ),
                    DesktopSettingRow.Options(
                        title = "卡片密度",
                        caption = "紧凑卡小，宽松卡大",
                        options = com.venera.desktop.gallery.ui.DesktopWallDensity.entries.map { it.label },
                        selectedIndex = wallDensity.ordinal,
                        onSelect = { i ->
                            onPickWallDensity(
                                com.venera.desktop.gallery.ui.DesktopWallDensity.entries[i],
                            )
                        },
                    ),
                    DesktopSettingRow.Options(
                        title = "卡片大小",
                        caption = "在密度之上再缩放单卡",
                        options = com.venera.desktop.gallery.ui.DesktopCardSize.entries.map { cardSizeLabel(it) },
                        selectedIndex = cardSize.ordinal,
                        onSelect = { i -> prefs.setCardSize(com.venera.desktop.gallery.ui.DesktopCardSize.entries[i]) },
                    ),
                    DesktopSettingRow.Options(
                        title = "网格密度",
                        caption = "自适应或固定 x 行 x 列，画廊卡片统一生效；行数决定封面裁切比例",
                        options = com.venera.desktop.settings.DesktopGridDensity.entries.map { it.label },
                        selectedIndex = com.venera.desktop.settings.DesktopGridDensity.entries.indexOf(gridDensityTier).coerceAtLeast(0),
                        onSelect = { i ->
                            mainThemeStore?.let { store ->
                                com.venera.desktop.settings.DesktopGridDensity.write(
                                    store,
                                    com.venera.desktop.settings.DesktopGridDensity.entries[i],
                                )
                            }
                        },
                    ),
                    DesktopSettingRow.Options(
                        title = "漫画卡片布局",
                        caption = "覆盖全部漫画页面的网格与双列模式",
                        options = com.venera.desktop.settings.DesktopComicCardLayoutMode.entries.map {
                            if (isEn) it.labelEn else it.labelCn
                        },
                        selectedIndex = comicCardLayout.mode.ordinal,
                        onSelect = { i ->
                            mainThemeStore?.let { store ->
                                com.venera.desktop.settings.DesktopComicCardLayout.write(
                                    store,
                                    comicCardLayout.copy(mode = com.venera.desktop.settings.DesktopComicCardLayoutMode.entries[i]),
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
                                    com.venera.desktop.settings.DesktopComicCardLayout.write(
                                        store,
                                        comicCardLayout.copy(rows = i + 1),
                                    )
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
                                    com.venera.desktop.settings.DesktopComicCardLayout.write(
                                        store,
                                        comicCardLayout.copy(cols = i + 1),
                                    )
                                }
                            },
                        )
                    } else null,
                    DesktopSettingRow.Options(
                        title = "封面画幅",
                        caption = "仅瀑布流与等宽自适应档生效；固定行数档由网格密度决定",
                        options = com.venera.desktop.settings.DesktopCoverAspect.entries.map { it.label },
                        selectedIndex = com.venera.desktop.settings.DesktopCoverAspect.entries.indexOf(coverAspectTier).coerceAtLeast(0),
                        onSelect = { i ->
                            mainThemeStore?.let { store ->
                                com.venera.desktop.settings.DesktopCoverAspect.write(
                                    store,
                                    com.venera.desktop.settings.DesktopCoverAspect.entries[i],
                                )
                            }
                        },
                    ),
                    DesktopSettingRow.Options(
                        title = "预览清晰度",
                        caption = "流量约十倍，刷新后生效",
                        options = spec.qualityTierLabels.map { it.second },
                        selectedIndex = spec.qualityTierLabels.indexOfFirst { it.first == previewQuality }
                            .coerceAtLeast(0),
                        onSelect = { i -> prefs.setGalleryPreviewQuality(spec.qualityTierLabels[i].first) },
                    ),
                    DesktopSettingRow.Options(
                        title = "详情页清晰度",
                        caption = "详情大图取预览/大图/原图",
                        options = spec.detailQualityTierLabels.map { it.second },
                        selectedIndex = spec.detailQualityTierLabels.indexOfFirst { it.first == detailQuality }
                            .coerceAtLeast(0),
                        onSelect = { i -> prefs.setDetailQuality(spec.detailQualityTierLabels[i].first) },
                    ),
                    DesktopSettingRow.Options(
                        title = "详情栏宽度",
                        caption = "详情页右栏信息位宽度",
                        options = com.venera.desktop.gallery.ui.DesktopDetailPaneWidth.entries.map { paneWidthLabel(it) },
                        selectedIndex = paneWidth.ordinal,
                        onSelect = { i ->
                            prefs.setDetailPaneWidth(com.venera.desktop.gallery.ui.DesktopDetailPaneWidth.entries[i])
                        },
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
                        onSelect = { i ->
                            prefs.setImageCacheMb(listOf(512, 1024, 2048)[i])
                        },
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
                        selectedIndex = spec.preloadTierLabels.indexOfFirst { it.first == preload }
                            .coerceAtLeast(0),
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
                        options = com.venera.desktop.settings.DesktopCanvasFrame.Preset.entries
                            .map { it.label } + "自定义",
                        selectedIndex = (
                            com.venera.desktop.settings.DesktopCanvasFrame.presetOf(canvasFrame)?.ordinal
                                ?: com.venera.desktop.settings.DesktopCanvasFrame.Preset.entries.size
                            ),
                        onSelect = { i ->
                            val presets = com.venera.desktop.settings.DesktopCanvasFrame.Preset.entries
                            if (i < presets.size && mainThemeStore != null) {
                                com.venera.desktop.settings.DesktopCanvasFrame.update(
                                    mainThemeStore,
                                    presets[i].spec,
                                )
                            }
                        },
                    ),
                    mainThemeStore?.let { store ->
                        DesktopSettingRow.Slider(
                            title = "内衬",
                            caption = "画作与背板之间的留白厚度",
                            value = canvasFrame.padDp,
                            range = com.venera.desktop.settings.DesktopCanvasFrame.PAD_MIN..
                                com.venera.desktop.settings.DesktopCanvasFrame.PAD_MAX,
                            steps = 23,
                            display = { v -> "${v.toInt()}dp" },
                            onChange = { v ->
                                com.venera.desktop.settings.DesktopCanvasFrame.update(
                                    store,
                                    canvasFrame.copy(padDp = v),
                                )
                            },
                        )
                    },
                    mainThemeStore?.let { store ->
                        DesktopSettingRow.Slider(
                            title = "画作圆角",
                            caption = "图片自身的圆角，外框按同心圆自动加大一圈",
                            value = canvasFrame.radiusDp,
                            range = com.venera.desktop.settings.DesktopCanvasFrame.RADIUS_MIN..
                                com.venera.desktop.settings.DesktopCanvasFrame.RADIUS_MAX,
                            steps = 31,
                            display = { v -> "${v.toInt()}dp" },
                            onChange = { v ->
                                com.venera.desktop.settings.DesktopCanvasFrame.update(
                                    store,
                                    canvasFrame.copy(radiusDp = v),
                                )
                            },
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
                            onChange = { v ->
                                com.venera.desktop.settings.DesktopCanvasFrame.update(
                                    store,
                                    canvasFrame.copy(glow = v),
                                )
                            },
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
                            onChange = { v ->
                                com.venera.desktop.settings.DesktopCanvasFrame.update(
                                    store,
                                    canvasFrame.copy(shadow = v),
                                )
                            },
                        )
                    },
                    mainThemeStore?.let { store ->
                        DesktopSettingRow.Action(
                            title = "重置",
                            caption = "回到推荐数值：内衬 10dp、圆角 16dp、环境光关闭、落影 38%",
                            buttonLabel = "重置",
                            onAction = {
                                com.venera.desktop.settings.DesktopCanvasFrame.reset(store)
                            },
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
                        options = com.venera.desktop.settings.DesktopMascot.ModeOverride.entries
                            .map { listOf("自动", "安静待机", "我在听", "打盹")[it.ordinal] },
                        selectedIndex = mascotSpec.modeOverride.ordinal,
                        onSelect = { i ->
                            mainThemeStore?.let { store ->
                                com.venera.desktop.settings.DesktopMascot.update(
                                    store,
                                    mascotSpec.copy(modeOverride = com.venera.desktop.settings.DesktopMascot.ModeOverride.entries[i]),
                                )
                            }
                        },
                    ),
                    mainThemeStore?.let { store ->
                        DesktopSettingRow.Slider(
                            title = "待机透明度",
                            caption = "形象在屏上的存在感",
                            value = mascotSpec.idleAlpha,
                            range = com.venera.desktop.settings.DesktopMascot.ALPHA_MIN..
                                com.venera.desktop.settings.DesktopMascot.ALPHA_MAX,
                            steps = 47,
                            display = { v -> "${(v * 100).toInt()}%" },
                            onChange = { v ->
                                com.venera.desktop.settings.DesktopMascot.update(store, mascotSpec.copy(idleAlpha = v))
                            },
                        )
                    },
                    mainThemeStore?.let { store ->
                        DesktopSettingRow.Slider(
                            title = "图标尺寸",
                            caption = "空态里形状的渲染边长",
                            value = mascotSpec.sizeDp,
                            range = com.venera.desktop.settings.DesktopMascot.SIZE_MIN..
                                com.venera.desktop.settings.DesktopMascot.SIZE_MAX,
                            steps = 27,
                            display = { v -> "${v.toInt()}dp" },
                            onChange = { v ->
                                com.venera.desktop.settings.DesktopMascot.update(store, mascotSpec.copy(sizeDp = v))
                            },
                        )
                    },
                    mainThemeStore?.let { store ->
                        DesktopSettingRow.Slider(
                            title = "呼吸周期",
                            caption = "一次完整呼吸的时长",
                            value = mascotSpec.breathCycle,
                            range = com.venera.desktop.settings.DesktopMascot.CYCLE_MIN..
                                com.venera.desktop.settings.DesktopMascot.CYCLE_MAX,
                            steps = 54,
                            display = { v -> "%.1fs".format(v) },
                            onChange = { v ->
                                com.venera.desktop.settings.DesktopMascot.update(store, mascotSpec.copy(breathCycle = v))
                            },
                        )
                    },
                    mainThemeStore?.let { store ->
                        DesktopSettingRow.Slider(
                            title = "呼吸幅度",
                            caption = "翅尖抬升与耳尖张开的倍率",
                            value = mascotSpec.breathAmp,
                            range = com.venera.desktop.settings.DesktopMascot.AMP_MIN..
                                com.venera.desktop.settings.DesktopMascot.AMP_MAX,
                            steps = 15,
                            display = { v -> "%.1fx".format(v) },
                            onChange = { v ->
                                com.venera.desktop.settings.DesktopMascot.update(store, mascotSpec.copy(breathAmp = v))
                            },
                        )
                    },
                    mainThemeStore?.let { store ->
                        DesktopSettingRow.Slider(
                            title = "打盹延迟",
                            caption = "无活动多久后进入打盹",
                            value = mascotSpec.dozeDelay,
                            range = com.venera.desktop.settings.DesktopMascot.DOZE_MIN..
                                com.venera.desktop.settings.DesktopMascot.DOZE_MAX,
                            steps = 16,
                            display = { v -> "${v.toInt()}s" },
                            onChange = { v ->
                                com.venera.desktop.settings.DesktopMascot.update(store, mascotSpec.copy(dozeDelay = v))
                            },
                        )
                    },
                    mainThemeStore?.let { store ->
                        DesktopSettingRow.Toggle(
                            title = "鼠标视线跟随",
                            caption = "指针活跃时双眼微随光标",
                            checked = mascotSpec.gazeFollow,
                            onToggle = {
                                com.venera.desktop.settings.DesktopMascot.update(store, mascotSpec.copy(gazeFollow = it))
                            },
                        )
                    },
                    mainThemeStore?.let { store ->
                        DesktopSettingRow.Action(
                            title = "重置",
                            caption = "回到默认值：透明度 28%、64dp、呼吸 4.8s、打盹 15s",
                            buttonLabel = "重置",
                            onAction = {
                                com.venera.desktop.settings.DesktopMascot.reset(store)
                            },
                        )
                    },
                ),
            ),
        )
    }

    // ── 「视频与动图」分区：把散在"看图播放器"里的动图/底色档收拢，并加"视频自动播放"总闸 ──
    val mediaCards = listOf(
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
                    selectedIndex = spec.animatedTierLabels.indexOfFirst { it.first == animated }
                        .coerceAtLeast(0),
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
                    selectedIndex = spec.backdropTierLabels.indexOfFirst { it.first == backdrop }
                        .coerceAtLeast(0),
                    onSelect = { i -> prefs.setGalleryBackdrop(spec.backdropTierLabels[i].first) },
                ),
            ),
        ),
    )

    val appearanceCards = listOf(
        DesktopSettingsCard(
            title = if (isEn) "Language" else "语言",
            caption = if (isEn) "Interface display language" else "界面显示语言",
            rows = listOfNotNull(
                mainThemeStore?.let { store ->
                    DesktopSettingRow.Options(
                        title = if (isEn) "Interface Language" else "界面语言",
                        caption = if (isEn) "Switch between Chinese and English" else "支持中文与英文即时切换",
                        options = listOf(
                            com.venera.desktop.settings.DesktopLanguage.LABEL_ZH,
                            com.venera.desktop.settings.DesktopLanguage.LABEL_EN,
                        ),
                        selectedIndex = if (isEn) 1 else 0,
                        onSelect = { i ->
                            com.venera.desktop.settings.DesktopLanguage.write(
                                store,
                                if (i == 1) com.venera.desktop.settings.DesktopLanguage.EN
                                else com.venera.desktop.settings.DesktopLanguage.ZH,
                            )
                        },
                    )
                },
                mainThemeStore?.let { store ->
                    val toastDurationMs by com.venera.desktop.settings.DesktopErrorToastDuration.durationMs.collectAsState()
                    DesktopSettingRow.Options(
                        title = if (isEn) "Error Toast Duration" else "错误弹窗显示时间",
                        caption = if (isEn) "Auto-dismiss delay before collapsing to bottom-right pill" else "自动收起为右下角报错胶囊的等待时间（默认 3 秒）",
                        options = com.venera.desktop.settings.DesktopErrorToastDuration.optionLabels(isEn),
                        selectedIndex = com.venera.desktop.settings.DesktopErrorToastDuration.indexOf(toastDurationMs),
                        onSelect = { i ->
                            val picked = com.venera.desktop.settings.DesktopErrorToastDuration.PRESETS_MS.getOrElse(i) {
                                com.venera.desktop.settings.DesktopErrorToastDuration.DEFAULT_MS
                            }
                            com.venera.desktop.settings.DesktopErrorToastDuration.write(store, picked)
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
                            com.venera.desktop.settings.DesktopThemeMode.DARK,
                            com.venera.desktop.settings.DesktopThemeMode.LIGHT,
                            com.venera.desktop.settings.DesktopThemeMode.SYSTEM,
                        ),
                        selectedIndex = listOf(
                            com.venera.desktop.settings.DesktopThemeMode.DARK,
                            com.venera.desktop.settings.DesktopThemeMode.LIGHT,
                            com.venera.desktop.settings.DesktopThemeMode.SYSTEM,
                        ).indexOf(themeMode).coerceAtLeast(0),
                        onSelect = { i ->
                            com.venera.desktop.settings.DesktopThemeMode.write(
                                store,
                                listOf(
                                    com.venera.desktop.settings.DesktopThemeMode.DARK,
                                    com.venera.desktop.settings.DesktopThemeMode.LIGHT,
                                    com.venera.desktop.settings.DesktopThemeMode.SYSTEM,
                                )[i],
                            )
                        },
                    )
                },
                // 注意：listOfNotNull 的每个元素只取 let 的返回值（最后一行），
                // 多行必须拆成多个 let，否则前面的行会被静默吞掉。
                mainThemeStore?.let {
                    DesktopSettingRow.Toggle(
                        title = if (isEn) "Backdrop Blur" else "底板模糊",
                        caption = if (isEn) "Solid background when disabled" else "关闭后内容区为实底",
                        checked = backdropMode != com.venera.desktop.settings.DesktopBackdropMode.SOLID,
                        onToggle = { next ->
                            com.venera.desktop.settings.DesktopBackdropMode.select(
                                if (next) {
                                    com.venera.desktop.settings.DesktopBackdropMode.ACRYLIC
                                } else {
                                    com.venera.desktop.settings.DesktopBackdropMode.SOLID
                                },
                            )
                        },
                    )
                },
                mainThemeStore?.let { bdStore ->
                    DesktopSettingRow.Toggle(
                        title = if (isEn) "Rail Blur" else "侧栏模糊",
                        caption = if (isEn) "Solid sidebar when disabled" else "独立开关，关闭为实底",
                        checked = railBlur,
                        onToggle = { next ->
                            com.venera.desktop.settings.DesktopRailBlur.write(bdStore, next)
                        },
                    )
                },
                mainThemeStore?.let { bdStore ->
                    DesktopSettingRow.Toggle(
                        title = if (isEn) "Top Bar Blur" else "顶栏模糊",
                        caption = if (isEn) "Solid top bar when disabled" else "独立开关，关闭为实底",
                        checked = topBarBlur,
                        onToggle = { next ->
                            com.venera.desktop.settings.DesktopTopBarBlur.write(bdStore, next)
                        },
                    )
                },
                // ──：首页皮肤档。经典 = 现行仪表版逐字节不变；
                // 浅色实验 = 1:1 新皮（玻璃侧栏 + 无顶栏首页），切档即回、零残留。──
                mainThemeStore?.let { skinStore ->
                    DesktopSettingRow.Options(
                        title = if (isEn) "Home Skin" else "首页皮肤",
                        caption = if (isEn) "Acrylic: translucent sidebar & home" else "Acrylic：玻璃侧栏与通透首页",
                        options = listOf(
                            com.venera.desktop.settings.DesktopHomeSkin.LIGHT,
                        ),
                        selectedIndex = 0,
                        onSelect = {
                            com.venera.desktop.settings.DesktopHomeSkin.write(
                                skinStore,
                                com.venera.desktop.settings.DesktopHomeSkin.LIGHT,
                            )
                        },
                    )
                },
                mainThemeStore?.let { skinStore ->
                    DesktopSettingRow.Toggle(
                        title = if (isEn) "Sidebar Mascot" else "侧栏桌宠",
                        caption = if (isEn) "Display mascot fish at sidebar bottom" else "在侧栏底部显示小鱼桌宠",
                        checked = petVisible,
                        onToggle = { next ->
                            com.venera.desktop.settings.DesktopPetVisibility.write(skinStore, next)
                        },
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
                        options = com.venera.desktop.settings.DesktopUiScale.TIERS,
                        selectedIndex = com.venera.desktop.settings.DesktopUiScale.TIERS.indexOf(uiScaleTier).coerceAtLeast(0),
                        onSelect = { i ->
                            com.venera.desktop.settings.DesktopUiScale.write(
                                store,
                                com.venera.desktop.settings.DesktopUiScale.TIERS[i],
                            )
                        },
                    )
                },
            ),
        ),
    )

    val exploreCards: List<DesktopSettingsCard> = buildList {
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
                            maskModeError = if (ok) {
                                null
                            } else {
                                // 落盘失败 => 流不动，屏上仍是旧档（写侧的纪律）。这句必须让用户看见，
                                // 否则会以为切档成了、其实没存上。桌面 toast 空实现，走设置页 note 那一行。
                                println("D_遮罩档 写盘被拒 档位只接受 OFF/BLUR/HIDE 递来=${maskLabels[i].first}")
                                "切换失败：设置未保存"
                            }
                        },
                    ),
                ),
            ),
        )
        // 屏蔽规则四组入口（有落点才摆；计数读守卫账本身，规则即主墙/遮罩判定真吃的）。
        val openRules = onOpenBlockingRules
        if (openRules != null) {
            add(
                DesktopSettingsCard(
                    title = "屏蔽规则",
                    caption = "命中即在画廊与封面遮罩里挡掉",
                    rows = listOf(
                        DesktopSettingRow.Entry("关键词屏蔽", "按包含匹配标题/作者/标签", "${guardRules.count { it.type == "KEYWORD" }} 条", onOpen = { openRules("KEYWORD") }),
                        DesktopSettingRow.Entry("屏蔽标签", "按包含匹配标签", "${guardRules.count { it.type == "TAG" }} 条", onOpen = { openRules("TAG") }),
                        DesktopSettingRow.Entry("屏蔽画师", "按包含匹配作者", "${guardRules.count { it.type == "AUTHOR" }} 条", onOpen = { openRules("AUTHOR") }),
                        DesktopSettingRow.Entry("屏蔽作品", "按编号精确匹配", "${guardRules.count { it.type == "COMIC_ID" }} 条", onOpen = { openRules("COMIC_ID") }),
                    ),
                ),
            )
        }
    }

    val readerUiPrefs by com.venera.desktop.reader.DesktopReaderPreferences.state.collectAsState()

    val readerCards = listOfNotNull(
        readerPrefs?.let {
            listOf(
                DesktopSettingsCard(
                    title = "阅读模式与排版",
                    caption = "翻页方向、条漫排版与连续阅读档位",
                    rows = listOf(
                        DesktopSettingRow.Options(
                            title = "翻页模式",
                            caption = "条漫 / 日漫 / 美漫 / 对开",
                            // 档位表有五档，屏上只摆做得了的四档：横向连续还没接线，
                            // 摆上去就是按不动的假开关。
                            options = com.venera.desktop.gallery.ui.DESKTOP_READER_MODE_CHOICES.map { it.label },
                            selectedIndex = com.venera.desktop.gallery.ui.DESKTOP_READER_MODE_CHOICES
                                .indexOf(readingMode)
                                .coerceAtLeast(0),
                            onSelect = { i ->
                                // 档位自己带方向：清掉「倒序」再写档，否则刚选的档会被取反一次。
                                com.venera.desktop.reader.DesktopReaderPreferences.update {
                                    it.copy(reverseReading = false)
                                }
                                readerPrefs.setReadingMode(
                                    com.venera.desktop.gallery.ui.DESKTOP_READER_MODE_CHOICES[i],
                                )
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
                                com.venera.desktop.reader.DesktopReaderPreferences.update { it.copy(stripMaxWidthPercent = pct) }
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
                                com.venera.desktop.reader.DesktopReaderPreferences.update { it.copy(stripPageGap = gap) }
                            },
                        ),
                        DesktopSettingRow.Options(
                            title = "阅读底色",
                            caption = "漫画画布的显示背景",
                            options = com.venera.desktop.settings.DesktopReaderBackground.OPTIONS,
                            selectedIndex = readerBgIndex,
                            onSelect = { i ->
                                mainThemeStore?.let { store ->
                                    com.venera.desktop.settings.DesktopReaderBackground.write(
                                        store,
                                        com.venera.desktop.settings.DesktopReaderBackground.OPTIONS[i],
                                    )
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
                        options = com.venera.desktop.reader.ReaderFit.entries.map { it.label },
                        selectedIndex = readerUiPrefs.fitIndex,
                        onSelect = { i ->
                            com.venera.desktop.reader.DesktopReaderPreferences.update { it.copy(fitIndex = i) }
                        },
                    ),
                    DesktopSettingRow.Options(
                        title = "页面旋转",
                        caption = "竖排页面转横看",
                        options = listOf("0°", "90°", "180°", "270°"),
                        selectedIndex = readerUiPrefs.rotationIndex,
                        onSelect = { i ->
                            com.venera.desktop.reader.DesktopReaderPreferences.update { it.copy(rotationIndex = i) }
                        },
                    ),
                    DesktopSettingRow.Options(
                        title = "动画与浏览模式",
                        caption = "翻页动画与分格导览视图",
                        options = listOf("导览视图", "淡出", "翻页"),
                        selectedIndex = (readerUiPrefs.turnEffect - 2).coerceIn(0, 2),
                        onSelect = { i ->
                            com.venera.desktop.reader.DesktopReaderPreferences.update { it.copy(turnEffect = i + 2) }
                        },
                    ),
                    DesktopSettingRow.Toggle(
                        title = "平滑翻页",
                        caption = "关闭时立刻切页",
                        checked = readerUiPrefs.smoothTurning,
                        onToggle = { v ->
                            com.venera.desktop.reader.DesktopReaderPreferences.update { it.copy(smoothTurning = v) }
                        },
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
                            com.venera.desktop.reader.DesktopReaderPreferences.update { it.copy(turnPerspective = p) }
                        },
                    ),
                    DesktopSettingRow.Options(
                        title = "导览视图模式",
                        caption = "分格引导浏览画面的视觉呈现",
                        options = listOf("聚焦", "隐藏", "沉浸式"),
                        selectedIndex = readerUiPrefs.guidedViewStyle.coerceIn(0, 2),
                        onSelect = { i ->
                            com.venera.desktop.reader.DesktopReaderPreferences.update { it.copy(guidedViewStyle = i) }
                        },
                    ),
                    DesktopSettingRow.Options(
                        title = "点击翻页区",
                        caption = "左右边缘的翻页热区宽度",
                        options = listOf("15%", "25%", "33%"),
                        selectedIndex = listOf(15, 25, 33).indexOf(readerUiPrefs.tapZonePercent).coerceAtLeast(0),
                        onSelect = { i ->
                            val pct = listOf(15, 25, 33)[i]
                            com.venera.desktop.reader.DesktopReaderPreferences.update { it.copy(tapZonePercent = pct) }
                        },
                    ),
                    DesktopSettingRow.Slider(
                        title = "放大倍率",
                        caption = "悬浮放大镜的缩放倍数",
                        value = readerUiPrefs.magnifierScale,
                        range = 1.5f..4f,
                        steps = 25,
                        display = { v -> String.format("%.1f×", v) },
                        onChange = { v ->
                            com.venera.desktop.reader.DesktopReaderPreferences.update { it.copy(magnifierScale = v) }
                        },
                    ),
                    DesktopSettingRow.Options(
                        title = "预加载页数",
                        caption = "向后预取的页数",
                        options = listOf("2 页", "4 页", "8 页"),
                        selectedIndex = listOf(2, 4, 8).indexOf(readerUiPrefs.preloadCount).coerceAtLeast(0),
                        onSelect = { i ->
                            val n = listOf(2, 4, 8)[i]
                            com.venera.desktop.reader.DesktopReaderPreferences.update { it.copy(preloadCount = n) }
                        },
                    ),
                    DesktopSettingRow.Toggle(
                        title = "翻页音效",
                        caption = "翻页时播放纸页声",
                        checked = readerUiPrefs.pageSoundOn,
                        onToggle = { v ->
                            com.venera.desktop.reader.DesktopReaderPreferences.update { it.copy(pageSoundOn = v) }
                        },
                    ),
                    DesktopSettingRow.Toggle(
                        title = "双页展示",
                        caption = "对开模式下两页并排（关闭=每次一页）",
                        checked = readerUiPrefs.doublePageOn,
                        onToggle = { v ->
                            com.venera.desktop.reader.DesktopReaderPreferences.update { it.copy(doublePageOn = v) }
                        },
                    ),
                    DesktopSettingRow.Toggle(
                        title = "封面独占",
                        caption = "封面页不与第 2 页并排",
                        checked = readerUiPrefs.coverSinglePage,
                        onToggle = { v ->
                            com.venera.desktop.reader.DesktopReaderPreferences.update { it.copy(coverSinglePage = v) }
                        },
                    ),
                    DesktopSettingRow.Toggle(
                        title = "不用于横幅画面",
                        caption = "宽幅页面独占一屏，不参与并排",
                        checked = readerUiPrefs.keepWideSingle,
                        onToggle = { v ->
                            com.venera.desktop.reader.DesktopReaderPreferences.update { it.copy(keepWideSingle = v) }
                        },
                    ),
                    DesktopSettingRow.Toggle(
                        title = "与下一个水平对齐",
                        caption = "自动补白，让横幅落成完整跨页",
                        checked = readerUiPrefs.alignNextHorizontal,
                        onToggle = { v ->
                            com.venera.desktop.reader.DesktopReaderPreferences.update { it.copy(alignNextHorizontal = v) }
                        },
                    ),
                    DesktopSettingRow.Toggle(
                        title = "开头空白页",
                        caption = "首页前补白页，第 1 页单独落右侧",
                        checked = readerUiPrefs.blankPage,
                        onToggle = { v ->
                            com.venera.desktop.reader.DesktopReaderPreferences.update { it.copy(blankPage = v) }
                        },
                    ),
                    DesktopSettingRow.Toggle(
                        title = "方向键翻页",
                        caption = "用方向键切换页面（滚轮只缩放）",
                        checked = readerUiPrefs.arrowKeysOn,
                        onToggle = { v ->
                            com.venera.desktop.reader.DesktopReaderPreferences.update { it.copy(arrowKeysOn = v) }
                        },
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
                            onSelect = { i ->
                                com.venera.desktop.reader.DesktopReaderPreferences.update { it.copy(filterQualityIndex = i) }
                            },
                        ),
                        DesktopSettingRow.Slider(
                            title = "亮度",
                            caption = "0–400%，100% 为原画面",
                            value = readerUiPrefs.brightness,
                            range = 0f..4f,
                            steps = 80,
                            display = { v -> "${(v * 100).toInt()}%" },
                            onChange = { v ->
                                com.venera.desktop.reader.DesktopReaderPreferences.update { it.copy(brightness = v) }
                            },
                        ),
                        DesktopSettingRow.Slider(
                            title = "饱和度",
                            caption = "0–400%，100% 为原画面",
                            value = readerUiPrefs.saturation,
                            range = 0f..4f,
                            steps = 80,
                            display = { v -> "${(v * 100).toInt()}%" },
                            onChange = { v ->
                                com.venera.desktop.reader.DesktopReaderPreferences.update { it.copy(saturation = v) }
                            },
                        ),
                        DesktopSettingRow.Slider(
                            title = "对比度",
                            caption = "0–400%，100% 为原画面",
                            value = readerUiPrefs.contrast,
                            range = 0f..4f,
                            steps = 80,
                            display = { v -> "${(v * 100).toInt()}%" },
                            onChange = { v ->
                                com.venera.desktop.reader.DesktopReaderPreferences.update { it.copy(contrast = v) }
                            },
                        ),
                        DesktopSettingRow.Slider(
                            title = "色温",
                            caption = "泛黄怀旧强度",
                            value = readerUiPrefs.filterSepia,
                            range = 0f..100f,
                            steps = 0,
                            display = { v -> "${v.toInt()}" },
                            onChange = { v ->
                                com.venera.desktop.reader.DesktopReaderPreferences.update { it.copy(filterSepia = v) }
                            },
                        ),
                        DesktopSettingRow.Toggle(
                            title = "黑白",
                            caption = "画面转灰度",
                            checked = readerUiPrefs.filterInvert,
                            onToggle = { v ->
                                com.venera.desktop.reader.DesktopReaderPreferences.update { it.copy(filterInvert = v) }
                            },
                        ),
                        DesktopSettingRow.Toggle(
                            title = "负片",
                            caption = "反转画面明暗颜色",
                            checked = readerUiPrefs.filterNegative,
                            onToggle = { v ->
                                com.venera.desktop.reader.DesktopReaderPreferences.update { it.copy(filterNegative = v) }
                            },
                        ),
                        DesktopSettingRow.Action(
                            title = "AI超分引擎",
                            caption = "独立离线 Vulkan 推理组件 (${com.venera.desktop.reader.DesktopAiUpscaleManager.estimatedSizeText()})",
                            buttonLabel = "清理缓存",
                            onAction = { com.venera.desktop.reader.DesktopAiUpscaleManager.cleanEngine() },
                        ),
                    ),
                ),
        ),
    ).flatten()

    // ── ⭐️：Gelbooru 登录系统与 Pixiv 账号系统 ──
    val gelbooruIdentity by (gelbooruState?.identity ?: MutableStateFlow(null)).collectAsState()
    val isGelbooruSignedIn = gelbooruIdentity != null || gelbooruState?.isConfigured == true

    val pixivAuthState by (pixivAccount?.authState ?: MutableStateFlow(PixivAuthState.Anonymous)).collectAsState()
    val isPixivSignedIn = pixivAuthState is PixivAuthState.SignedIn
    val signedInUser = pixivAuthState as? PixivAuthState.SignedIn

    val gelbooruCard = DesktopSettingsCard(
        title = "Gelbooru 账号",
        caption = "配置 User ID 与 API Key 解锁 Gelbooru 搜索与日榜（官方接口要求鉴权）",
        rows = listOfNotNull(
            DesktopSettingRow.Action(
                title = if (isGelbooruSignedIn) "已登录 Gelbooru" else "未配置凭据",
                caption = if (isGelbooruSignedIn) {
                    val uid = gelbooruIdentity?.userId ?: gelbooruState?.userId.orEmpty()
                    "当前凭据 · User ID: $uid (可正常访问与检索)"
                } else {
                    "匿名请求受站方限制；配置后可在画廊与搜索中正常使用"
                },
                buttonLabel = if (isGelbooruSignedIn) "修改凭据" else "登录账号",
                onAction = { showGelbooruDialog = true },
            ),
            if (isGelbooruSignedIn) {
                DesktopSettingRow.Action(
                    title = "注销当前凭据",
                    caption = "清除保存在本机的 Gelbooru 鉴权信息",
                    buttonLabel = "退出登录",
                    buttonColor = VeneraTokens.color.error,
                    onAction = {
                        gelbooruState?.signOut()
                        ux.toast("已退出 Gelbooru 登录")
                    },
                )
            } else null,
        ),
    )

    val pixivCard = DesktopSettingsCard(
        title = "Pixiv 账号",
        caption = "登录 Pixiv 账号以使用云端书签、同步已关注画师及解除匿名限速",
        rows = listOfNotNull(
            if (isPixivSignedIn && signedInUser != null) {
                DesktopSettingRow.Action(
                    title = signedInUser.userName?.ifBlank { "Pixiv 用户" } ?: "Pixiv 用户",
                    caption = "已登录 · User ID: ${signedInUser.userId ?: "已认证"}",
                    buttonLabel = "管理我的 Pixiv",
                    onAction = { onOpenPixivAccount?.invoke() },
                )
            } else {
                DesktopSettingRow.Action(
                    title = "当前未登录",
                    caption = "未登录状态下画作检索受限，且无法使用云端书签",
                    buttonLabel = "登录 Pixiv",
                    onAction = { onOpenPixivLogin?.invoke() },
                )
            },
            if (isPixivSignedIn) {
                DesktopSettingRow.Action(
                    title = "退出 Pixiv 登录",
                    caption = "清除本地 Token 缓存并恢复为匿名访问",
                    buttonLabel = "退出登录",
                    buttonColor = VeneraTokens.color.error,
                    onAction = {
                        pixivAccount?.signOut()
                        ux.toast("已退出 Pixiv 登录")
                    },
                )
            } else null,
        ),
    )

    val sourceCards = listOfNotNull(
        gelbooruCard,
        pixivCard,
        // ── 档一：SauceNAO key 读写（写口此前全仓零调用）+ Pixiv 画质三档收拢 ──
        galleryCreds?.let { creds ->
            DesktopSettingsCard(
                title = "SauceNAO",
                caption = "反搜功能的 API 凭据",
                rows = listOf(
                    DesktopSettingRow.Action(
                        title = if (sauceNaoHasKey == true) "已配置 API Key" else "未配置 API Key",
                        caption = "配置后反搜结果页可用（当前状态实时读凭据仓）",
                        buttonLabel = if (sauceNaoHasKey == true) "更换" else "设置",
                        onAction = { showSauceNaoDialog = true },
                    ),
                ),
            )
        },
        DesktopSettingsCard(
            title = "Pixiv 画质",
            // 卡片预览 / 插画详情 / 漫画详情 / 大图缩放，四条一卡。
            caption = "卡片与详情图四档",
            rows = listOf(
                // 卡片预览档此前只在安卓侧可设，桌面补上；卡片只给三档，
                // 原图那一档在一两百 dp 的格子里没有意义。
                DesktopSettingRow.Options(
                    title = "卡片预览画质",
                    caption = null,
                    options = listOf(
                        com.venera.compose.gallery.domain.PixivImageQuality.SMALL.label,
                        com.venera.compose.gallery.domain.PixivImageQuality.MEDIUM.label,
                        com.venera.compose.gallery.domain.PixivImageQuality.LARGE.label,
                    ),
                    selectedIndex = when (pixivPreviewQ) {
                        com.venera.compose.gallery.domain.PixivImageQuality.SMALL -> 0
                        com.venera.compose.gallery.domain.PixivImageQuality.LARGE,
                        com.venera.compose.gallery.domain.PixivImageQuality.ORIGINAL,
                        -> 2
                        else -> 1
                    },
                    onSelect = { i ->
                        prefs.setPixivPreviewQuality(
                            when (i) {
                                0 -> com.venera.compose.gallery.domain.PixivImageQuality.SMALL
                                2 -> com.venera.compose.gallery.domain.PixivImageQuality.LARGE
                                else -> com.venera.compose.gallery.domain.PixivImageQuality.MEDIUM
                            },
                        )
                    },
                ),
                DesktopSettingRow.Options(
                    title = "插画详情画质",
                    caption = null,
                    options = com.venera.compose.gallery.domain.PixivImageQuality.entries.map { it.label },
                    selectedIndex = com.venera.compose.gallery.domain.PixivImageQuality.entries.indexOf(pixivIllustQ)
                        .coerceAtLeast(0),
                    onSelect = { i ->
                        prefs.setPixivIllustQuality(com.venera.compose.gallery.domain.PixivImageQuality.entries[i])
                    },
                ),
                DesktopSettingRow.Options(
                    title = "漫画详情画质",
                    caption = null,
                    options = com.venera.compose.gallery.domain.PixivImageQuality.entries.map { it.label },
                    selectedIndex = com.venera.compose.gallery.domain.PixivImageQuality.entries.indexOf(pixivMangaQ)
                        .coerceAtLeast(0),
                    onSelect = { i ->
                        prefs.setPixivMangaQuality(com.venera.compose.gallery.domain.PixivImageQuality.entries[i])
                    },
                ),
                DesktopSettingRow.Options(
                    title = "大图缩放画质",
                    caption = null,
                    options = com.venera.compose.gallery.domain.PixivImageQuality.entries.map { it.label },
                    selectedIndex = com.venera.compose.gallery.domain.PixivImageQuality.entries.indexOf(pixivZoomQ)
                        .coerceAtLeast(0),
                    onSelect = { i ->
                        prefs.setPixivZoomQuality(com.venera.compose.gallery.domain.PixivImageQuality.entries[i])
                    },
                ),
            ),
        ),
        DesktopSettingsCard(
            title = "站点",
            caption = "漫画源启停、脚本编辑、源站登录",
            rows = listOf(
                DesktopSettingRow.Entry(
                    title = "站点",
                    caption = "34 颗内置站点的启停 / 编辑 / 登录",
                    value = null,
                    onOpen = onOpenSourceManage,
                ),
            ),
        ),
    )

    val dataCards = buildList {
        add(
            DesktopSettingsCard(
                title = "数据",
                caption = "留底 / 历史 / 统计",
                rows = listOf(
                    DesktopSettingRow.Entry(
                        title = "备份与还原",
                        caption = "三张核心表 ↔ JSON",
                        value = null,
                        onOpen = onOpenBackup,
                    ),
                    DesktopSettingRow.Entry(
                        title = "阅读历史",
                        caption = null,
                        value = null,
                        onOpen = onOpenHistory,
                    ),
                    DesktopSettingRow.Entry(
                        title = "阅读统计",
                        caption = "14 天趋势 + 常读 Top 10",
                        value = null,
                        onOpen = onOpenStats,
                    ),
                ),
            ),
        )
        // 缓存：只摆有数据根时（paths 空 = 没缓存可清，不摆假把手）。清 pixiv + ugoira，okhttp 活缓存不硬删。
        val p = paths
        if (p != null) {
            // ── 2.4 下载目录（令）：图库/Pixiv 存图落点 + 默认开关 ──
            // 选择框是原生模态（DesktopDirChooser：Win32 Shell32 / 其余 Swing），点击回调线程直调；
            // 取消 ⇒ 不写盘。漫画队列目录仍在装配侧，没接进来就不摆进这张卡的话里。
            val shownDir = if (downloadDir.isBlank()) desktopGallerySaveDir(p).absolutePath else downloadDir
            val shownComicDir = if (comicDownloadDir.isBlank()) {
                com.venera.desktop.gallery.data.desktopComicDownloadsRoot(p.dataRoot).absolutePath
            } else {
                comicDownloadDir
            }
            add(
                DesktopSettingsCard(
                    title = "下载目录",
                    caption = "图库存图与漫画离线下载目录",
                    rows = listOf(
                        DesktopSettingRow.Toggle(
                            title = "默认目录直存",
                            caption = if (downloadUseDefaultDir) {
                                "开：存图不再问路，直接落到下面那一格目录"
                            } else {
                                "关：每次存图先弹目录选择，取消就不落盘"
                            },
                            checked = downloadUseDefaultDir,
                            onToggle = { prefs.setDownloadUseDefaultDir(it) },
                        ),
                        DesktopSettingRow.Entry(
                            title = "图库下载目录",
                            caption = dirPickerError ?: "点右侧换目录；留空回内置 数据根/downloads/图库",
                            value = if (downloadDir.isBlank()) "内置默认" else downloadDir,
                            onOpen = {
                                val picked = DesktopDirChooser.pickDirectory("选择图库下载目录", shownDir) {
                                    dirPickerError = it
                                }
                                if (picked != null) {
                                    dirPickerError = null
                                    prefs.setDownloadDir(picked)
                                }
                            },
                        ),
                        DesktopSettingRow.Action(
                            title = "打开图库文件夹",
                            caption = "在资源管理器里看已下载的图",
                            buttonLabel = "打开",
                            onAction = {
                                scope.launch {
                                    runCatching { java.awt.Desktop.getDesktop().open(java.io.File(shownDir)) }
                                }
                            },
                        ),
                        DesktopSettingRow.Action(
                            title = "恢复图库内置目录",
                            caption = "清掉自定义，回到数据根下的 downloads/图库",
                            buttonLabel = "恢复",
                            enabled = downloadDir.isNotBlank(),
                            onAction = { prefs.setDownloadDir("") },
                        ),
                        DesktopSettingRow.Entry(
                            title = "漫画下载目录",
                            caption = "新下载漫画章节存放目录；留空回内置 数据根/downloads",
                            value = if (comicDownloadDir.isBlank()) "内置默认" else comicDownloadDir,
                            onOpen = {
                                val picked = DesktopDirChooser.pickDirectory("选择漫画下载目录", shownComicDir) {
                                    dirPickerError = it
                                }
                                if (picked != null) {
                                    dirPickerError = null
                                    prefs.setComicDownloadDir(picked)
                                }
                            },
                        ),
                        DesktopSettingRow.Action(
                            title = "打开漫画文件夹",
                            caption = "在资源管理器里看已下载的漫画章节",
                            buttonLabel = "打开",
                            onAction = {
                                scope.launch {
                                    runCatching {
                                        val dir = java.io.File(shownComicDir)
                                        dir.mkdirs()
                                        java.awt.Desktop.getDesktop().open(dir)
                                    }
                                }
                            },
                        ),
                        DesktopSettingRow.Action(
                            title = "恢复漫画内置目录",
                            caption = "清掉自定义，回到数据根下的 downloads（已下载的旧漫画仍保留）",
                            buttonLabel = "恢复",
                            enabled = comicDownloadDir.isNotBlank(),
                            onAction = { prefs.setComicDownloadDir("") },
                        ),
                    ),
                ),
            )
            val sizeLabel = if (cacheBytes < 0) "统计中…" else formatBytes(cacheBytes)
            add(
                DesktopSettingsCard(
                    title = "缓存",
                    caption = "占用 $sizeLabel（进入本页时统计）",
                    rows = listOf(
                        DesktopSettingRow.Action(
                            title = "清理接口与 HTTP 缓存",
                            caption = "回收 pixiv 接口 JSON、逐帧解包目录与 okhttp 活缓存（2026-11-04 起 HTTP 缓存也在清理口径内）",
                            buttonLabel = if (clearingCache) "清理中…" else "清理（现 $sizeLabel）",
                            enabled = !clearingCache,
                            onAction = {
                                clearingCache = true
                                scope.launch {
                                    val freed = com.venera.desktop.gallery.data.DesktopCacheMaintenance.clear(p)
                                    com.venera.desktop.platform.DesktopHttpEngine.evictHttpCache()
                                    cacheBytes = com.venera.desktop.gallery.data.DesktopCacheMaintenance.measureBytes(p)
                                    clearingCache = false
                                    ux.toast("已释放 ${formatBytes(freed)}")
                                }
                            },
                        ),
                        DesktopSettingRow.Action(
                            title = "浏览历史",
                            caption = "清空本机画廊浏览记录（历史页同步清空）",
                            buttonLabel = "清空",
                            buttonColor = LocalSettingsGlass.current.danger,
                            onAction = {
                                scope.launch {
                                    com.venera.desktop.gallery.data.DesktopGalleryBrowseHistory
                                        .getInstance(p).clear()
                                    ux.toast("浏览历史已清空")
                                }
                            },
                        ),
                    ),
                ),
            )
        }
    }

    // ── 档二：网络区（代理三枚 + HTTP 缓存上限；消费端冷启动读盘 ⇒ 重启生效）──
    val networkCards = if (networkPrefs != null) {
        listOf(
            DesktopSettingsCard(
                title = "网络代理",
                caption = "直连 / 系统跟随 / 手动 HTTP（改后重启生效）",
                rows = listOf(
                    DesktopSettingRow.Options(
                        title = "代理类型",
                        caption = "「系统」档跟随 -Pproxy= 启动参数",
                        options = listOf("直连", "系统", "手动"),
                        selectedIndex = listOf("直连", "系统", "手动").indexOf(proxyType).coerceAtLeast(0),
                        onSelect = { i -> networkPrefs.setProxy(listOf("直连", "系统", "手动")[i], proxyHost, proxyPort) },
                    ),
                    DesktopSettingRow.Text(
                        title = "主机",
                        caption = "手动档生效",
                        value = proxyHost,
                        onCommit = { networkPrefs.setProxy(proxyType, it, proxyPort) },
                    ),
                    DesktopSettingRow.Text(
                        title = "端口",
                        caption = "手动档生效（1–65535）",
                        value = if (proxyPort > 0) "$proxyPort" else "",
                        onCommit = { networkPrefs.setProxy(proxyType, proxyHost, it.toIntOrNull() ?: 0) },
                    ),
                ),
            ),
            *(if (mainThemeStore != null) {
                arrayOf(
                    DesktopSettingsCard(
                        title = "图床反代",
                        caption = "自定义 Pixiv 图片加速域名",
                        rows = listOf(
                            DesktopSettingRow.Action(
                                title = "快捷节点",
                                caption = if (pximgMirror.isBlank()) "当前直连官方图床" else "反代失败时自动退回直连",
                                buttonLabel = if (pximgMirror == com.venera.desktop.settings.DesktopPximgMirror.PIXIV_RE) {
                                    "恢复直连"
                                } else {
                                    "设为 i.pixiv.re"
                                },
                                onAction = {
                                    val next = if (pximgMirror == com.venera.desktop.settings.DesktopPximgMirror.PIXIV_RE) {
                                        com.venera.desktop.settings.DesktopPximgMirror.DIRECT
                                    } else {
                                        com.venera.desktop.settings.DesktopPximgMirror.PIXIV_RE
                                    }
                                    com.venera.desktop.settings.DesktopPximgMirror.write(mainThemeStore, next)
                                },
                            ),
                            DesktopSettingRow.Text(
                                title = "反代域名",
                                caption = "填域名后自动生效，留空直连",
                                value = pximgMirror,
                                onCommit = {
                                    com.venera.desktop.settings.DesktopPximgMirror.write(mainThemeStore, it)
                                },
                            ),
                        ),
                    ),
                )
            } else emptyArray()),
            DesktopSettingsCard(
                title = "缓存上限",
                caption = "HTTP 响应缓存（改后重启生效）",
                rows = listOf(
                    DesktopSettingRow.Options(
                        title = "HTTP 缓存上限",
                        caption = "此前写死 100MB，现已开放",
                        options = listOf("100 MB", "256 MB", "512 MB", "1024 MB"),
                        selectedIndex = listOf(100, 256, 512, 1024).indexOf(httpCacheMb).coerceAtLeast(0),
                        onSelect = { i -> networkPrefs.setHttpCacheMaxMb(listOf(100, 256, 512, 1024)[i]) },
                    ),
                ),
            ),
            DesktopSettingsCard(
                title = "下载",
                caption = "同时下载的章节数（改后重启生效）",
                rows = listOf(
                    DesktopSettingRow.Options(
                        title = "下载并发",
                        caption = "数值越大同时下载的章节越多；并发过高会挤占看图带宽",
                        options = listOf("1", "2", "4"),
                        selectedIndex = listOf(1, 2, 4).indexOf(downloadThreads).coerceAtLeast(0),
                        onSelect = { i -> networkPrefs.setDownloadThreads(listOf(1, 2, 4)[i]) },
                    ),
                ),
            ),
            DesktopSettingsCard(
                title = "保存",
                caption = "存到本机的文件名档",
                rows = listOf(
                    DesktopSettingRow.Options(
                        title = "保存命名",
                        caption = "改后下次保存生效",
                        options = listOf("站名-编号", "原名", "画师-编号", "时间戳"),
                        selectedIndex = run {
                            val tier = mainThemeStore?.getString(
                                "pref_gallery_save_naming",
                                com.venera.compose.gallery.domain.GallerySaveNaming.SITE_ID.name,
                            ) ?: com.venera.compose.gallery.domain.GallerySaveNaming.SITE_ID.name
                            com.venera.compose.gallery.domain.GallerySaveNaming.entries
                                .indexOfFirst { it.name == tier }.coerceAtLeast(0)
                        },
                        onSelect = { i ->
                            mainThemeStore?.put(
                                "pref_gallery_save_naming",
                                com.venera.compose.gallery.domain.GallerySaveNaming.entries[i].name,
                            )
                        },
                    ),
                    DesktopSettingRow.Entry(
                        title = "保存格式",
                        caption = "桌面存原图字节，不转码",
                        value = "原图",
                        onOpen = {},
                        clickable = false,
                    ),
                ),
            ),
            DesktopSettingsCard(
                title = "线路测速",
                caption = "最近一次对 Gelbooru 的往返延迟",
                rows = run {
                    val probeState by com.venera.desktop.platform.DesktopLineProbe.state.collectAsState()
                    listOf(
                        DesktopSettingRow.Action(
                            title = "测速",
                            caption = com.venera.desktop.platform.DesktopLineProbe.readout(probeState),
                            buttonLabel = if (probeState == com.venera.desktop.platform.DesktopLineProbe.State.Running) "测速中…" else "测一次",
                            enabled = probeState != com.venera.desktop.platform.DesktopLineProbe.State.Running,
                            onAction = {
                                val handle = galleryHandle
                                val store = mainThemeStore
                                if (handle != null && store != null) {
                                    scope.launch {
                                        com.venera.desktop.platform.DesktopLineProbe.probe(handle.engine.okHttpClient, store)
                                    }
                                }
                            },
                        ),
                    )
                },
            ),
            // ── 档二 5b-6：WebDAV 配置四枚 + 上传/取回两把手（走桌面备份链）──
            run {
                val davPaths = paths
                val davConfig = davPaths?.let { com.venera.desktop.platform.DesktopWebDavSync.readConfig(it) }
                val davState by com.venera.desktop.platform.DesktopWebDavSync.state.collectAsState()
                val davStore = davPaths?.let {
                    com.venera.compose.data.platform.JsonKeyValueStore(
                        com.venera.compose.data.platform.PreferenceKeys.PREFS_WEBDAV,
                        it,
                    )
                }
                DesktopSettingsCard(
                    title = "WebDAV 同步",
                    caption = "归档上传与取回",
                    rows = listOfNotNull(
                        davStore?.let { s ->
                            DesktopSettingRow.Text(
                                title = "服务器地址",
                                caption = "https://… 根地址",
                                value = davConfig?.serverUrl.orEmpty(),
                                onCommit = { v -> s.put("server_url", v) },
                            )
                        },
                        davStore?.let { s ->
                            DesktopSettingRow.Text(
                                title = "账号",
                                caption = null,
                                value = davConfig?.username.orEmpty(),
                                onCommit = { v -> s.put("username", v) },
                            )
                        },
                        davStore?.let { s ->
                            DesktopSettingRow.Text(
                                title = "密码",
                                caption = "盘上明文（与对岸同形）",
                                value = davConfig?.password.orEmpty(),
                                onCommit = { v -> s.put("password", v) },
                            )
                        },
                        davStore?.let { s ->
                            DesktopSettingRow.Text(
                                title = "远端目录",
                                caption = "默认 /Venera/",
                                value = davConfig?.remotePath ?: "/Venera/",
                                onCommit = { v -> s.put("remote_path", v) },
                            )
                        },
                        DesktopSettingRow.Action(
                            title = "上传归档",
                            caption = com.venera.desktop.platform.DesktopWebDavSync.stateLine(davState),
                            buttonLabel = if (davState == com.venera.desktop.platform.DesktopWebDavSync.State.Running) "同步中…" else "上传",
                            enabled = davState != com.venera.desktop.platform.DesktopWebDavSync.State.Running && davPaths != null,
                            onAction = {
                                val p = davPaths ?: return@Action
                                scope.launch { com.venera.desktop.platform.DesktopWebDavSync.upload(p) }
                            },
                        ),
                        DesktopSettingRow.Action(
                            title = "取回最新",
                            caption = "下到本机后在备份页选那一份还原",
                            buttonLabel = if (davState == com.venera.desktop.platform.DesktopWebDavSync.State.Running) "同步中…" else "下载",
                            enabled = davState != com.venera.desktop.platform.DesktopWebDavSync.State.Running && davPaths != null,
                            onAction = {
                                val p = davPaths ?: return@Action
                                scope.launch { com.venera.desktop.platform.DesktopWebDavSync.downloadLatest(p) }
                            },
                        ),
                    ),
                )
            },
        )
    } else {
        listOf(
            DesktopSettingsCard(
                title = "网络",
                caption = "设置暂不可用",
                rows = listOf(
                    DesktopSettingRow.Absent(
                        title = "网络设置",
                        caption = "网络偏好件未接入",
                    ),
                ),
            ),
        )
    }

    // ── 档一：收藏区（comicPrefs 的读写口已落真，见 DesktopBusinessPorts）──
    val favoritesCards = if (comicPrefs != null) {
        listOf(
            DesktopSettingsCard(
                title = "收藏夹",
                caption = "新收藏去向与排序",
                rows = listOf(
                    DesktopSettingRow.Options(
                        title = "新收藏位置",
                        caption = "收藏时进默认夹还是每次询问",
                        options = listOf("默认", "每次询问"),
                        selectedIndex = if (newFavoriteAddTo == "每次询问") 1 else 0,
                        onSelect = { i -> comicPrefs.setNewFavoriteAddTo(if (i == 1) "每次询问" else "默认") },
                    ),
                    DesktopSettingRow.Options(
                        title = "收藏夹排序",
                        caption = "收藏夹列表的排列档",
                        options = listOf("时间", "标题"),
                        selectedIndex = if (favoriteSortOrder == "title") 1 else 0,
                        onSelect = { i -> comicPrefs.setFavoriteSortOrder(if (i == 1) "title" else "time") },
                    ),
                    // 长按收藏走这一枚（详情读它），所以摆可编辑行；留空 = 退回打开面板。
                    DesktopSettingRow.Text(
                        title = "快捷收藏夹",
                        caption = "填收藏夹名；留空则长按收藏打开面板",
                        value = quickFavorite.orEmpty(),
                        onCommit = { comicPrefs.setQuickFavorite(it.trim().ifBlank { null }) },
                    ),
                ),
            ),
            // 追更：启动自动检查默认关（防开窗即撞站点验证），冷却档只给三格。
            DesktopSettingsCard(
                title = "追更",
                caption = "巡检时机与冷却间隔",
                rows = listOf(
                    DesktopSettingRow.Toggle(
                        title = "启动时自动检查",
                        caption = "默认关闭；开启后开窗自动巡检一轮",
                        checked = prefs.followAutoCheck.collectAsState().value,
                        onToggle = { prefs.setFollowAutoCheck(it) },
                    ),
                    DesktopSettingRow.Options(
                        title = "巡检冷却",
                        caption = "同一本在这个间隔内不重复检查",
                        options = listOf("6 小时", "12 小时", "24 小时"),
                        selectedIndex = listOf(6, 12, 24)
                            .indexOf(prefs.followCooldownHours.collectAsState().value)
                            .coerceAtLeast(0),
                        onSelect = { i -> prefs.setFollowCooldownHours(listOf(6, 12, 24)[i]) },
                    ),
                ),
            ),
        )
    } else {
        listOf(
            DesktopSettingsCard(
                title = "收藏",
                caption = "设置暂不可用",
                rows = listOf(
                    DesktopSettingRow.Absent(
                        title = "收藏设置",
                        caption = "漫画偏好件未接入",
                    ),
                ),
            ),
        )
    }

    val aboutCards = listOf(
        DesktopSettingsCard(
            title = "关于 VComic",
            caption = "现代化跨平台漫画与画廊阅览中枢",
            rows = buildList {
                add(
                    DesktopSettingRow.Entry(
                        title = "版本",
                        caption = "与 packageVersion 同源",
                        value = DESKTOP_APP_VERSION,
                        onOpen = {},
                        clickable = false,
                    ),
                )
                // 检查更新（档二 5b-2）：桌面自持更新检查（同判据，发布页手动下载——桌面产物是 Exe/Msi）。
                val handle = galleryHandle
                if (handle != null) {
                    val updateResult by com.venera.desktop.platform.DesktopUpdateCheck.state.collectAsState()
                    add(
                        DesktopSettingRow.Action(
                            title = "检查更新",
                            caption = com.venera.desktop.platform.DesktopUpdateCheck.readoutOf(updateResult, DESKTOP_APP_VERSION),
                            buttonLabel = if (updateResult == com.venera.desktop.platform.DesktopUpdateCheck.Result.Checking) "检查中…" else "检查",
                            enabled = updateResult != com.venera.desktop.platform.DesktopUpdateCheck.Result.Checking,
                            onAction = {
                                scope.launch {
                                    com.venera.desktop.platform.DesktopUpdateCheck.check(
                                        client = handle.engine.okHttpClient,
                                        localVersion = DESKTOP_APP_VERSION,
                                    )
                                }
                            },
                        ),
                    )
                    if (updateResult is com.venera.desktop.platform.DesktopUpdateCheck.Result.Available) {
                        add(
                            DesktopSettingRow.Entry(
                                title = "去发布页下载",
                                caption = "桌面产物是 Exe/Msi，更新走发布页手动安装",
                                value = null,
                                onOpen = {
                                    scope.launch {
                                        openInBrowser((updateResult as com.venera.desktop.platform.DesktopUpdateCheck.Result.Available).releaseUrl)
                                    }
                                },
                            ),
                        )
                    }
                }
                addAll(
                    listOf(
                DesktopSettingRow.Entry(
                    title = "项目主页",
                    caption = "在系统浏览器打开",
                    value = null,
                    onOpen = { scope.launch { openInBrowser("https://github.com/w13630039663-bit/Vcomic") } },
                ),
                DesktopSettingRow.Entry(
                    title = "更新发布",
                    caption = "在系统浏览器打开发布页",
                    value = null,
                    onOpen = { scope.launch { openInBrowser("https://github.com/w13630039663-bit/Vcomic/releases") } },
                ),
                    ),
                )
            },
        ),
    )


    // ── 档三：桌面件真行（窗口记忆 / 协议开关 / 日志目录 / 键位表）──
    val desktopPaths = paths
    val desktopCapabilityExtra = if (desktopPaths != null) {
        val windowStore = com.venera.compose.data.platform.JsonKeyValueStore("venera_desktop_window", desktopPaths)
        val rememberWindow = windowStore.getBoolean("pref_desktop_window_remember", true)
        val effectsReduce = windowStore.getBoolean("pref_desktop_effects_reduce", false)
        val searchViewMode = com.venera.desktop.search.readSearchViewMode(desktopPaths)
        val protocolSentinel = java.io.File(
            desktopPaths.dataRoot,
            com.venera.desktop.platform.DesktopProtocolRegistrar.DISABLED_SENTINEL,
        )
        listOf(
            DesktopSettingsCard(
                title = "桌面件",
                caption = "窗口 · 协议 · 日志 · 键位",
                rows = listOf(
                    DesktopSettingRow.Options(
                        title = "默认视图",
                        caption = "漫画搜索结果的排布",
                        options = com.venera.desktop.search.DesktopSearchViewMode.entries.map { it.label },
                        selectedIndex = searchViewMode.ordinal,
                        onSelect = { i ->
                            com.venera.desktop.search.writeSearchViewMode(
                                desktopPaths,
                                com.venera.desktop.search.DesktopSearchViewMode.entries[i],
                            )
                        },
                    ),
                    DesktopSettingRow.Toggle(
                        title = "记住窗口",
                        caption = "下次启动回到本次的尺寸与位置（最大化也记）",
                        checked = rememberWindow,
                        onToggle = { windowStore.put("pref_desktop_window_remember", it) },
                    ),
                    DesktopSettingRow.Toggle(
                        title = "精简特效",
                        caption = "关闭模糊与玻璃材质，低配设备更流畅",
                        checked = effectsReduce,
                        onToggle = { on ->
                            windowStore.put("pref_desktop_effects_reduce", on)
                            com.venera.desktop.ui.DesktopEffects.reduce = on
                        },
                    ),
                    DesktopSettingRow.Toggle(
                        title = "协议关联",
                        caption = "pixiv:// 与 venera:// 每次启动自动注册",
                        checked = !protocolSentinel.isFile,
                        onToggle = { on ->
                            runCatching {
                                if (on) protocolSentinel.delete() else protocolSentinel.createNewFile()
                            }
                        },
                    ),
                    DesktopSettingRow.Action(
                        title = "运行日志",
                        caption = "在资源管理器打开日志目录",
                        buttonLabel = "打开",
                        onAction = {
                            scope.launch {
                                runCatching {
                                    java.awt.Desktop.getDesktop().open(java.io.File(desktopPaths.dataRoot, "logs"))
                                }.onFailure { ux.toast("打不开日志目录") }
                            }
                        },
                    ),
                    DesktopSettingRow.Entry("Esc", "返回总览 / 关闭浮层", value = null, onOpen = {}, clickable = false),
                    DesktopSettingRow.Entry("← → 与 PageUp PageDown", "翻页（阅读器与查看器）", value = null, onOpen = {}, clickable = false),
                    DesktopSettingRow.Entry("+ − 0", "缩放与复位（大图查看器）", value = null, onOpen = {}, clickable = false),
                    DesktopSettingRow.Entry("Ctrl K", "搜索设置项（总览页）", value = null, onOpen = {}, clickable = false),
                ),
            ),
        )
    } else {
        emptyList()
    }

    val rawCardsOf: (DesktopSettingsSection) -> List<DesktopSettingsCard> = { s ->
        when (s) {
            DesktopSettingsSection.APPEARANCE -> appearanceCards
            DesktopSettingsSection.GALLERY -> galleryCards
            DesktopSettingsSection.EXPLORE -> exploreCards
            DesktopSettingsSection.READER -> readerCards
            DesktopSettingsSection.SOURCES -> sourceCards
            DesktopSettingsSection.MEDIA -> mediaCards
            DesktopSettingsSection.FAVORITES -> favoritesCards
            DesktopSettingsSection.NETWORK -> networkCards
            DesktopSettingsSection.DATA -> dataCards
            DesktopSettingsSection.DESKTOP_CAPABILITIES -> desktopCapabilityExtra
            DesktopSettingsSection.ABOUT -> aboutCards
        }
    }

    val cardsOf: (DesktopSettingsSection) -> List<DesktopSettingsCard> = { s ->
        rawCardsOf(s).map { card ->
            card.copy(
                title = DesktopI18n.tr(card.title),
                caption = card.caption?.let { DesktopI18n.tr(it) },
                rows = card.rows.map { row ->
                    when (row) {
                        is DesktopSettingRow.Toggle -> row.copy(
                            title = DesktopI18n.tr(row.title),
                            caption = row.caption?.let { DesktopI18n.tr(it) },
                        )
                        is DesktopSettingRow.Options -> row.copy(
                            title = DesktopI18n.tr(row.title),
                            caption = row.caption?.let { DesktopI18n.tr(it) },
                            options = row.options.map { DesktopI18n.tr(it) },
                        )
                        is DesktopSettingRow.Entry -> row.copy(
                            title = DesktopI18n.tr(row.title),
                            caption = row.caption?.let { DesktopI18n.tr(it) },
                            value = row.value?.let { DesktopI18n.tr(it) },
                        )
                        is DesktopSettingRow.Action -> row.copy(
                            title = DesktopI18n.tr(row.title),
                            caption = row.caption?.let { DesktopI18n.tr(it) },
                            buttonLabel = DesktopI18n.tr(row.buttonLabel),
                        )
                        is DesktopSettingRow.Slider -> row.copy(
                            title = DesktopI18n.tr(row.title),
                            caption = row.caption?.let { DesktopI18n.tr(it) },
                            display = { v -> DesktopI18n.tr(row.display(v)) },
                        )
                        is DesktopSettingRow.Text -> row.copy(
                            title = DesktopI18n.tr(row.title),
                            caption = row.caption?.let { DesktopI18n.tr(it) },
                        )
                        is DesktopSettingRow.Absent -> row.copy(
                            title = DesktopI18n.tr(row.title),
                            caption = row.caption?.let { DesktopI18n.tr(it) },
                        )
                    }
                },
            )
        }
    }

    val q = query.trim()
    val cards = cardsOf(section)
        .map { card ->
            if (q.isEmpty()) {
                card
            } else {
                card.copy(
                    rows = card.rows.filter { row ->
                        row.title.contains(q, ignoreCase = true) ||
                            row.caption?.contains(q, ignoreCase = true) == true
                    },
                )
            }
        }
        .filter { it.rows.isNotEmpty() }
    // 压平页：任一分区 → 过滤搜索词后的卡规格（原 templateCards 的同一套映射，按分区取）。
    val specsOf: (DesktopSettingsSection) -> List<SettingsCardSpec> = { sec ->
        cardsOf(sec)
            .map { card ->
                if (q.isEmpty()) {
                    card
                } else {
                    card.copy(
                        rows = card.rows.filter { row ->
                            row.title.contains(q, ignoreCase = true) ||
                                row.caption?.contains(q, ignoreCase = true) == true
                        },
                    )
                }
            }
            .filter { it.rows.isNotEmpty() }
            .map { card ->
                SettingsCardSpec(
                    title = card.title,
                    caption = card.caption,
                    rows = card.rows.map { row ->
                        SettingsRowSpec(
                            title = row.title,
                            caption = row.caption,
                            onClick = (row as? DesktopSettingRow.Entry)?.takeIf { it.clickable }?.onOpen,
                            control = { SettingControl(row) },
                            controlPlacement = if (row is DesktopSettingRow.Options) {
                                SettingsControlPlacement.Below
                            } else {
                                SettingsControlPlacement.Inline
                            },
                            counted = row !is DesktopSettingRow.Absent &&
                                !(row is DesktopSettingRow.Entry && !row.clickable),
                        )
                    },
                )
            }
    }

    val templateCards = cards.map { card ->
        SettingsCardSpec(
            title = card.title,
            caption = card.caption,
            rows = card.rows.map { row ->
                SettingsRowSpec(
                    title = row.title,
                    caption = row.caption,
                    onClick = (row as? DesktopSettingRow.Entry)?.takeIf { it.clickable }?.onOpen,
                    control = { SettingControl(row) },
                    controlPlacement = if (row is DesktopSettingRow.Options) {
                        SettingsControlPlacement.Below
                    } else {
                        SettingsControlPlacement.Inline
                    },
                    // Absent 占位与纯展示行（Entry.clickable=false）不计入页头「N 项」，
                    // 与左轨徽标同口径 —— 别把待办/空壳算成功能数。
                    counted = row !is DesktopSettingRow.Absent &&
                        !(row is DesktopSettingRow.Entry && !row.clickable),
                )
            },
        )
    }

    // 搜索态：跨分区合并，卡名前缀来源分区（右栏单栏平铺时用户能看出归属）。
    val searchCards = if (q.isEmpty()) emptyList() else DesktopSettingsSection.entries.flatMap { sec ->
        specsOf(sec).map { card ->
            SettingsCardSpec(
                title = "${sec.title} · ${card.title}",
                caption = card.caption,
                rows = card.rows,
            )
        }
    }
    // 扁平壳的右栏数据：与旧模板同一套过滤口径，但保留行对象（控件回调在上面）。
    val flatCards: List<DesktopSettingsCard> = if (q.isEmpty()) cards
    else DesktopSettingsSection.entries.flatMap { sec ->
        cardsOf(sec).map { card ->
            card.copy(
                title = "${sec.title} · ${card.title}",
                rows = card.rows.filter { row ->
                    row.title.contains(q, ignoreCase = true) ||
                        row.caption?.contains(q, ignoreCase = true) == true
                },
            )
        }.filter { it.rows.isNotEmpty() }
    }
    val visibleCards = if (q.isEmpty()) templateCards else searchCards
    val visibleTitle = if (q.isEmpty()) section.title else if (isEn) "Search Results" else "搜索结果"
    val visibleSubtitle = if (q.isEmpty()) section.group else null

    // 弹窗形态：onClose 非空 ⇒ 居中模态对话框；为空 ⇒ 全屏（兼容旧调用与测试桩）。
    val panelContent: @Composable () -> Unit = {
        SettingsPageTemplate(
            mode = mode,
            title = visibleTitle,
            subtitle = visibleSubtitle,
            note = maskModeError,
            cards = visibleCards,
            navigationWidth = 280.dp,
            cardColumns = 1,
            modifier = Modifier.fillMaxSize(),
            navigation = {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 16.dp),
                ) {
                    SettingsSearchField(value = query, onChange = { query = it })
                    Spacer(Modifier.height(tokens.spacing.space4))
                    SettingsUserCard(
                        pixivAccount = pixivAccount,
                        pixivWorks = pixivWorks,
                        onOpenPixivAccount = onOpenPixivAccount,
                        onOpenPixivLogin = onOpenPixivLogin,
                    )
                    Spacer(Modifier.height(tokens.spacing.space4))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        DesktopSettingsSection.entries.forEach { sec ->
                            SettingsNavItem(
                                section = sec,
                                selected = q.isEmpty() && section == sec,
                                onClick = { section = sec },
                            )
                        }
                    }
                    Spacer(Modifier.height(tokens.spacing.space3))
                    Text(
                        text = "VComic",
                        fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                        color = tokens.color.textTertiary,
                    )
                }
            },
            headerActions = {
                if (onClose != null) {
                    androidx.compose.material3.IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "关闭",
                            tint = tokens.color.textSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            },
            emptyContent = {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (q.isEmpty()) "这一组还没有设置项。" else "没有匹配「$q」的设置项。",
                        fontSize = VeneraType.body.size, lineHeight = VeneraType.body.lineHeight,
                        color = tokens.color.textTertiary,
                    )
                }
            },
        )
    }

    if (onClose != null) {
        val dark = DesktopThemeMode.isDark(themeMode)
        val dialogPalette = settingsDialogPalette(dark)
        // 弹窗是主内容区之上的一层，不再开独立窗口：底层页面照常留在原地，
        // 遮罩与小模糊由外壳挂在这一层的底下。
        SettingsDialogShell(
            sections = DesktopSettingsSection.entries,
            selectedSection = if (q.isEmpty()) section else null,
            onSelectSection = { section = it },
            title = visibleTitle,
            query = query,
            onQueryChange = { query = it },
            cards = flatCards,
            sectionIcon = { settingsSectionIcon(it) },
            userCard = {
                SettingsUserCard(
                    pixivAccount = pixivAccount,
                    pixivWorks = pixivWorks,
                    onOpenPixivAccount = onOpenPixivAccount,
                    onOpenPixivLogin = onOpenPixivLogin,
                )
            },
            footer = "VComic\nVersion $DESKTOP_APP_VERSION",
            palette = dialogPalette,
            onClose = onClose,
        )
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            panelContent()
        }
    }

        // ── ⭐️ SauceNAO key 弹窗（写口此前全仓零调用）──
        if (showSauceNaoDialog && galleryCreds != null) {
            var inputKey by remember { mutableStateOf("") }
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showSauceNaoDialog = false },
            ) {
                Column(
                    modifier = Modifier
                        .width(420.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(tokens.color.surface)
                        .border(1.dp, tokens.color.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = "SauceNAO API Key",
                        fontSize = VeneraType.pageTitle.size, lineHeight = VeneraType.pageTitle.lineHeight,
                        fontWeight = VeneraType.cardTitle.weight,
                        color = tokens.color.textPrimary,
                    )
                    Text(
                        text = "从 saucenao.com 账号页取 API Key 粘进下面输入框。",
                        fontSize = VeneraType.body.size, lineHeight = VeneraType.body.lineHeight,
                        color = tokens.color.textTertiary,
                    )
                    com.venera.desktop.ui.components.DesktopInputField(
                        value = inputKey,
                        onValueChange = { inputKey = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        com.venera.desktop.ui.DesktopButton(
                            text = "取消",
                            onClick = { showSauceNaoDialog = false },
                            size = com.venera.desktop.ui.DesktopButtonSize.Medium,
                            variant = com.venera.desktop.ui.DesktopButtonVariant.Ghost,
                        )
                        com.venera.desktop.ui.DesktopButton(
                            text = "保存",
                            onClick = {
                                galleryCreds.setSauceNaoKey(inputKey.trim())
                                showSauceNaoDialog = false
                                ux.toast("SauceNAO 凭据已保存")
                            },
                            size = com.venera.desktop.ui.DesktopButtonSize.Medium,
                            variant = com.venera.desktop.ui.DesktopButtonVariant.Primary,
                        )
                    }
                }
            }
        }

        // ── ⭐️ Gelbooru 桌面登录弹窗 ──
        if (showGelbooruDialog && gelbooruState != null) {
            var inputUserId by remember { mutableStateOf(gelbooruState.userId) }
            var inputApiKey by remember { mutableStateOf(gelbooruState.apiKey) }
            var isVerifying by remember { mutableStateOf(false) }
            var errorMsg by remember { mutableStateOf<String?>(null) }

            androidx.compose.ui.window.Dialog(
                onDismissRequest = { if (!isVerifying) showGelbooruDialog = false },
            ) {
                Column(
                    modifier = Modifier
                        .width(420.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(tokens.color.surface)
                        .border(1.dp, tokens.color.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(tokens.color.primary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "G",
                                fontSize = VeneraType.sectionTitle.size, lineHeight = VeneraType.sectionTitle.lineHeight,
                                fontWeight = VeneraType.cardTitle.weight,
                                color = tokens.color.primary,
                            )
                        }
                        Column {
                            Text(
                                text = "登录 Gelbooru",
                                fontSize = VeneraType.cardTitle.size, lineHeight = VeneraType.cardTitle.lineHeight,
                                fontWeight = VeneraType.cardTitle.weight,
                                color = tokens.color.textPrimary,
                            )
                            Text(
                                text = "配置官方 API 鉴权凭据",
                                fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                                color = tokens.color.textTertiary,
                            )
                        }
                    }

                    Text(
                        text = "Gelbooru API 现已强制鉴权。请前往 gelbooru.com 登录后，在「My Account」页面中获取您的 User ID 与 API Key。",
                        fontSize = VeneraType.aux.size,
                        color = tokens.color.textSecondary,
                        lineHeight = 16.sp,
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("User ID (纯数字)", fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight, fontWeight = VeneraType.cardTitle.weight, color = tokens.color.textPrimary)
                        com.venera.desktop.ui.components.DesktopInputField(
                            value = inputUserId,
                            onValueChange = { inputUserId = it.trim() },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = "例如：123456",
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("API Key", fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight, fontWeight = VeneraType.cardTitle.weight, color = tokens.color.textPrimary)
                        com.venera.desktop.ui.components.DesktopInputField(
                            value = inputApiKey,
                            onValueChange = { inputApiKey = it.trim() },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = "64位字符串",
                        )
                    }

                    if (!errorMsg.isNullOrBlank()) {
                        Text(
                            text = errorMsg.orEmpty(),
                            fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                            color = VeneraTokens.color.error,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        com.venera.desktop.ui.DesktopButton(
                            text = "取消",
                            onClick = { showGelbooruDialog = false },
                            size = com.venera.desktop.ui.DesktopButtonSize.Medium,
                            variant = com.venera.desktop.ui.DesktopButtonVariant.Ghost,
                            enabled = !isVerifying,
                        )
                        com.venera.desktop.ui.DesktopButton(
                            text = if (isVerifying) "正在验证…" else "验证并保存",
                            onClick = {
                                scope.launch {
                                    isVerifying = true
                                    errorMsg = null
                                    val result = gelbooruState.signIn(inputUserId, inputApiKey)
                                    isVerifying = false
                                    if (result.isSuccess) {
                                        ux.toast("Gelbooru 登录成功")
                                        showGelbooruDialog = false
                                    } else {
                                        errorMsg = result.exceptionOrNull()?.message ?: "验证失败，请检查 User ID 与 Key"
                                    }
                                }
                            },
                            size = com.venera.desktop.ui.DesktopButtonSize.Medium,
                            variant = com.venera.desktop.ui.DesktopButtonVariant.Primary,
                            enabled = inputUserId.isNotBlank() && inputApiKey.isNotBlank(),
                            loading = isVerifying,
                        )
                    }
                }
            }
        }
}
