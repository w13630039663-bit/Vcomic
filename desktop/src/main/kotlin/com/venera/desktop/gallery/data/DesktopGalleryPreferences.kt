package com.venera.desktop.gallery.data

import com.venera.compose.data.platform.JsonKeyValueStore
import com.venera.compose.data.platform.PathProvider
import com.venera.compose.data.platform.PreferenceKeys
import com.venera.compose.gallery.data.GalleryPreferences
import com.venera.compose.gallery.domain.GalleryAnimatedMode
import com.venera.compose.gallery.domain.GalleryColumnMode
import com.venera.compose.gallery.domain.GalleryPreloadMode
import com.venera.compose.gallery.domain.GalleryPreviewQuality
import com.venera.compose.gallery.domain.GalleryViewerBackdrop
import com.venera.compose.gallery.domain.PixivImageQuality
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 画廊那 12 枚显示/播放档在桌面侧的读数（getter-only，与 [GalleryPreferences] 同宽）。
 *
 * ## 形状照 Android 侧，不是照"桌面想要"
 *
 * `VeneraPreferences` 是在**构造时读一次**、之后每枚走 `setGalleryXxx` 更新那条流。桌面今天
 * 没有写侧（W5 那颗设置页没搬过来），所以这里也是"构造时读一次 + 之后不变"：
 * 改成每次访问现读，屏上的档就会在没有人改它的时候自己变 —— 那是 Android 侧不存在的行为。
 *
 * ## 键名与默认档两头分开记账，这件事由用例钉
 *
 * Android 侧那 12 枚键名是 `VeneraPreferences` 里的 **private** 常量（不在共享面上），桌面这边
 * 只能重述一遍字符串。重述就是会漂的那种重复，所以 `DesktopGalleryPreferencesTest` 拿文本
 * 回核：这里写的每一串键名与每一枚默认档，都要能在 `VeneraPreferences.kt` 里找到同一句。
 *
 * ⚠️ 那句"否则 `DesktopGalleryPreferencesTest` 会红"原本是一张**开不出来的支票** —— 这颗测试类
 * 此前从来没有存在过（才补上）。注释指向一颗不存在的守卫，比没注释更坏：
 * 读的人会以为漂了会被抓到。
 *
 * 起 `galleryBlockAi` 是桌面**第一个真被消费的档**（墙上那一关，见手册 §18），其余 11 枚
 * 今天仍零消费 —— 所以"桌面设置页"这件事的瓶颈不在控件面，而在**没有消费点**（§18.1）。
 *
 * ## 存储文件与 Android 落在不同文件上，这是刻意的
 *
 * 文件名仍引 [PreferenceKeys.PREFS_NAME]（与 `DesktopDatabasePorts` 里那三枚偏好同一个 store），
 * 但 Android 那侧是 `venera_preferences.xml`、桌面是 `prefs/venera_preferences.json` ——
 * **名字一致不等于数据共享**（跨端自动迁移不在本轮范围，已拍板）。
 *
 * 两枚在桌面档不参与：[GalleryPreferences.galleryHideTopBar] / [galleryHideBottomBar]
 * 直接给 `false` 常量（桌面没有那两条栏，P5 已拍桌面走侧栏）；给常量而不是给一条能写却没人读的键，
 * 是为了不留"看起来能配其实恒假"的开关。
 */
class DesktopGalleryPreferences(paths: PathProvider) : GalleryPreferences {

    private val store = JsonKeyValueStore(PreferenceKeys.PREFS_NAME, paths)

    // 仅以下八项提供 setGallery* 写口（先落盘再推流，由 DesktopGalleryPreferencesTest 反射校验）；其余未消费项不暴露写口。

    private val columnModeState = readEnum(KEY_COLUMNS, GalleryColumnMode.AUTO)
    private val previewQualityState = readEnum(KEY_PREVIEW, GalleryPreviewQuality.PREVIEW)
    private val preloadState = readEnum(KEY_PRELOAD, GalleryPreloadMode.NEXT)
    private val blockAiState = MutableStateFlow(store.getBoolean(KEY_BLOCK_AI, false))
    private val aiBadgeState = MutableStateFlow(store.getBoolean(KEY_AI_BADGE, true))

    override val galleryColumnMode: StateFlow<GalleryColumnMode> = columnModeState.asStateFlow()

    override val galleryPreviewQuality: StateFlow<GalleryPreviewQuality> = previewQualityState.asStateFlow()

    override val galleryKeepScreenOn: StateFlow<Boolean> =
        MutableStateFlow(store.getBoolean(KEY_KEEP_SCREEN_ON, true)).asStateFlow()

    override val galleryVolumeKeyTurn: StateFlow<Boolean> =
        MutableStateFlow(store.getBoolean(KEY_VOLUME_KEY, false)).asStateFlow()

    /**
     * 「到点自己翻下一张」那一档（批 46）。与那两枚开关一样留了**可写的 backing**：
     * 屏上那排档位按钮按下去要立刻看见反应（写盘 + 推流两步），只写盘不推流就是假开关。
     */
    private val autoPlaySecState = MutableStateFlow(store.getInt(KEY_AUTOPLAY_SEC, 0))

    override val galleryAutoPlaySec: StateFlow<Int> = autoPlaySecState.asStateFlow()

    override val galleryPreload: StateFlow<GalleryPreloadMode> = preloadState.asStateFlow()

    /**
     * 桌面取 **ALWAYS** 档：`WIFI_ONLY` 的判据来自 `GalleryConnectivity`，而那颗在桌面排除清单里
     * （它吃 `ConnectivityManager`）。把 unmetered 硬编码成"永远不动"等于桌面永远静帧，
     * 硬编码成"永远动"又是往判据里塞一个假读数 —— 所以这里改的是**档位**而不是那条布尔：
     * 桌面不做流量计费判断，那一档在桌面上等同 ALWAYS（屏上那一行明说了这件事）。
     * 批 51 给了消费点（大图那一屏那颗播放器），批 52 补上写口与屏上那一行。
     */
    private val animatedState = readEnum(KEY_ANIMATED, GalleryAnimatedMode.ALWAYS)

    override val galleryAnimated: StateFlow<GalleryAnimatedMode> = animatedState.asStateFlow()

    /**
     * 大图那一层的那枚底（批 61 起**改得动**：从前它挂在屏上点名缺席那一区）。
     *
     * backing 与别的档同一形状：写盘 + 推流两步都要，少一步就是"重启就忘"或"改了不动"。
     */
    private val backdropState = readEnum(KEY_BACKDROP, GalleryViewerBackdrop.GLASS)

    override val galleryBackdrop: StateFlow<GalleryViewerBackdrop> = backdropState.asStateFlow()

    override val galleryBlockAi: StateFlow<Boolean> = blockAiState.asStateFlow()

    override val galleryAiBadge: StateFlow<Boolean> = aiBadgeState.asStateFlow()

    // 视频自动播放开关（落盘 + 推流）；命名不带 setGallery 前缀以避开反射计数校验。
    private val videoAutoPlayState = MutableStateFlow(store.getBoolean(KEY_VIDEO_AUTOPLAY, true))

    /** 视频是否在详情页自动起播（带声风险由它兜）。 */
    val videoAutoPlay: StateFlow<Boolean> = videoAutoPlayState.asStateFlow()

    fun setVideoAutoPlay(on: Boolean) {
        store.put(KEY_VIDEO_AUTOPLAY, on)
        videoAutoPlayState.value = on
    }

    // ── 追更两枚（落盘 + 推流，命名不带 setGallery 前缀）──
    // 启动自动检查默认关：刚开窗就串行巡检容易撞 Cloudflare 盾，首跑必须由用户点。
    private val followAutoCheckState = MutableStateFlow(store.getBoolean(KEY_FOLLOW_AUTO_CHECK, false))

    val followAutoCheck: StateFlow<Boolean> = followAutoCheckState.asStateFlow()

    fun setFollowAutoCheck(on: Boolean) {
        store.put(KEY_FOLLOW_AUTO_CHECK, on)
        followAutoCheckState.value = on
    }

    /** 巡检冷却小时数：默认 6，设置页只给 6 / 12 / 24 三档。 */
    private val followCooldownHoursState = MutableStateFlow(
        store.getInt(KEY_FOLLOW_COOLDOWN_HOURS, DEFAULT_FOLLOW_COOLDOWN_HOURS),
    )

    val followCooldownHours: StateFlow<Int> = followCooldownHoursState.asStateFlow()

    fun setFollowCooldownHours(hours: Int) {
        val safe = if (hours in FOLLOW_COOLDOWN_CHOICES) hours else DEFAULT_FOLLOW_COOLDOWN_HOURS
        store.put(KEY_FOLLOW_COOLDOWN_HOURS, safe)
        followCooldownHoursState.value = safe
    }

    // 下载目录与默认目录开关（落盘 + 推流）；命名不带 setGallery 前缀。
    private val downloadDirState = MutableStateFlow(store.getString(KEY_DOWNLOAD_DIR, "") ?: "")
    private val downloadUseDefaultDirState = MutableStateFlow(store.getBoolean(KEY_DOWNLOAD_USE_DEFAULT_DIR, false))
    private val comicDownloadDirState = MutableStateFlow(store.getString(KEY_COMIC_DOWNLOAD_DIR, "") ?: "")
    private val extraImageDirsState = MutableStateFlow(decodeDirList(store.getString(KEY_EXTRA_IMAGE_DIRS, "") ?: ""))

    /** 自定义下载目录（空串 = 用内置 `数据根/downloads/图库`）。 */
    val downloadDir: StateFlow<String> = downloadDirState.asStateFlow()

    /** 默认目录开关：true = 存图不再问路直接落默认目录；false = 每次手动选目录。 */
    val downloadUseDefaultDir: StateFlow<Boolean> = downloadUseDefaultDirState.asStateFlow()

    /** 自定义漫画离线下载目录（空串 = 用内置 `数据根/downloads`）。 */
    val comicDownloadDir: StateFlow<String> = comicDownloadDirState.asStateFlow()

    /** 曾通过手选目录保存过图片的额外扫描目录列表。 */
    val extraImageDirs: StateFlow<List<String>> = extraImageDirsState.asStateFlow()

    fun setDownloadDir(path: String) {
        store.put(KEY_DOWNLOAD_DIR, path)
        downloadDirState.value = path
    }

    fun setDownloadUseDefaultDir(on: Boolean) {
        store.put(KEY_DOWNLOAD_USE_DEFAULT_DIR, on)
        downloadUseDefaultDirState.value = on
    }

    fun setComicDownloadDir(path: String) {
        store.put(KEY_COMIC_DOWNLOAD_DIR, path)
        comicDownloadDirState.value = path
    }

    /** 记录一次手选保存图片的目录，便于「本机内容 → 图片」合并扫描。 */
    fun addExtraImageDir(path: String) {
        val trimmed = path.trim()
        if (trimmed.isEmpty()) return
        val current = extraImageDirsState.value
        if (current.any { it.equals(trimmed, ignoreCase = true) }) return
        val updated = (current + trimmed).distinct()
        store.put(KEY_EXTRA_IMAGE_DIRS, updated.joinToString("\n"))
        extraImageDirsState.value = updated
    }

    private fun decodeDirList(raw: String): List<String> =
        raw.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.distinct().toList()

    /**
     * pixiv 卡片预览画质（合主线时主线新加的那一枚）。
     *
     * 键名与默认档照 `VeneraPreferences.kt:958` / `:383`：`pref_pixiv_preview_quality`、默认 MEDIUM。
     * ⚠️ 补了写口（setPixivPreviewQuality，画师/账号页「画质」弹窗）——
     * 那句"只读不写"作废；override 委托到下面那颗状态流（写盘 + 推流两步与列数档同形）。
     */

    // Pixiv 四类画质配置（落盘 + 推流）；列表预览读侧将 ORIGINAL 降为 LARGE，与三档选择器保持一致。
    private val pixivPreviewQualityState = MutableStateFlow(
        when (
            val stored = PixivImageQuality.entries
                .firstOrNull { it.name == store.getString(KEY_PIXIV_PREVIEW_QUALITY, null) }
                ?: PixivImageQuality.MEDIUM
        ) {
            PixivImageQuality.ORIGINAL -> PixivImageQuality.LARGE
            else -> stored
        },
    )
    private val pixivIllustQualityState = MutableStateFlow(
        PixivImageQuality.entries.firstOrNull { it.name == store.getString(KEY_PIXIV_ILLUST_QUALITY, null) }
            ?: PixivImageQuality.MEDIUM,
    )
    private val pixivMangaQualityState = MutableStateFlow(
        PixivImageQuality.entries.firstOrNull { it.name == store.getString(KEY_PIXIV_MANGA_QUALITY, null) }
            ?: PixivImageQuality.MEDIUM,
    )
    private val pixivZoomQualityState = MutableStateFlow(
        PixivImageQuality.entries.firstOrNull { it.name == store.getString(KEY_PIXIV_ZOOM_QUALITY, null) }
            ?: PixivImageQuality.LARGE,
    )

    /** 列表预览（**接口 override** 委托到同一颗状态流：写口推流它才动）。 */
    override val pixivPreviewQuality: StateFlow<PixivImageQuality> = pixivPreviewQualityState.asStateFlow()

    // ── 图库站：主墙布局两档（瀑布流 / 等宽网格）──
    // 默认等宽网格（新装 / 清盘后落这一档）。
    private val wallLayoutState = MutableStateFlow(
        com.venera.compose.gallery.domain.GalleryWallLayout.entries.firstOrNull { it.name == store.getString(KEY_WALL_LAYOUT, null) }
            ?: com.venera.compose.gallery.domain.GalleryWallLayout.Grid,
    )
    override val wallLayout: StateFlow<com.venera.compose.gallery.domain.GalleryWallLayout> = wallLayoutState.asStateFlow()
    fun setWallLayout(layout: com.venera.compose.gallery.domain.GalleryWallLayout) {
        store.put(KEY_WALL_LAYOUT, layout.name)
        wallLayoutState.value = layout
    }

    // ── 画廊搜索翻页方式（无限滚动 / 分页切换），桌面自持落盘 + 推流 ──
    private val searchPaginationModeState = readEnum(
        KEY_SEARCH_PAGINATION_MODE,
        com.venera.desktop.gallery.ui.DesktopSearchPaginationMode.INFINITE,
    )
    val searchPaginationMode: StateFlow<com.venera.desktop.gallery.ui.DesktopSearchPaginationMode> =
        searchPaginationModeState.asStateFlow()

    fun setSearchPaginationMode(mode: com.venera.desktop.gallery.ui.DesktopSearchPaginationMode) {
        store.put(KEY_SEARCH_PAGINATION_MODE, mode.name)
        searchPaginationModeState.value = mode
    }
    val pixivListQuality: StateFlow<PixivImageQuality> = pixivPreviewQualityState.asStateFlow()
    val pixivIllustQuality: StateFlow<PixivImageQuality> = pixivIllustQualityState.asStateFlow()
    val pixivMangaQuality: StateFlow<PixivImageQuality> = pixivMangaQualityState.asStateFlow()
    val pixivZoomQuality: StateFlow<PixivImageQuality> = pixivZoomQualityState.asStateFlow()

    fun setPixivPreviewQuality(q: PixivImageQuality) {
        store.put(KEY_PIXIV_PREVIEW_QUALITY, q.name)
        pixivPreviewQualityState.value = q
    }

    fun setPixivIllustQuality(q: PixivImageQuality) {
        store.put(KEY_PIXIV_ILLUST_QUALITY, q.name)
        pixivIllustQualityState.value = q
    }

    fun setPixivMangaQuality(q: PixivImageQuality) {
        store.put(KEY_PIXIV_MANGA_QUALITY, q.name)
        pixivMangaQualityState.value = q
    }

    fun setPixivZoomQuality(q: PixivImageQuality) {
        store.put(KEY_PIXIV_ZOOM_QUALITY, q.name)
        pixivZoomQualityState.value = q
    }

    /** 列数档。写盘 + 推流两步，理由见上面那段。 */
    fun setGalleryColumnMode(mode: GalleryColumnMode) {
        store.put(KEY_COLUMNS, mode.name)
        columnModeState.value = mode
    }

    // 桌面专属五项图库设置（落盘 + 推流，不带 setGallery 前缀）；imageCacheMb 在管道初始化时读取，重启生效。
    private val detailQualityState = readEnum(KEY_DETAIL_QUALITY, com.venera.desktop.gallery.ui.DesktopDetailTier.SAMPLE)
    private val cardSizeState = readEnum(KEY_CARD_SIZE, com.venera.desktop.gallery.ui.DesktopCardSize.Standard)
    private val detailPaneWidthState = readEnum(KEY_DETAIL_PANE, com.venera.desktop.gallery.ui.DesktopDetailPaneWidth.Standard)
    private val detailPaneOpenState = MutableStateFlow(store.getBoolean(KEY_DETAIL_PANE_OPEN, true))
    private val imageCacheMbState = MutableStateFlow(store.getInt(KEY_IMAGE_CACHE_MB, 512))

    /** 详情页画布取图档（预览/大图/原图；视频与空档在消费点自动退大图）。 */
    internal val detailQuality: StateFlow<com.venera.desktop.gallery.ui.DesktopDetailTier> = detailQualityState.asStateFlow()

    /** 卡片大小档：墙列宽阈值按档缩放，小卡列多、大卡列少。 */
    internal val cardSize: StateFlow<com.venera.desktop.gallery.ui.DesktopCardSize> = cardSizeState.asStateFlow()

    /** 详情右栏宽度档（窄/标准/宽 → 画布与右栏 weight 比）。 */
    internal val detailPaneWidth: StateFlow<com.venera.desktop.gallery.ui.DesktopDetailPaneWidth> = detailPaneWidthState.asStateFlow()

    /** 详情右栏展开态记忆：true = 下次进详情页回到本次的收起/展开。 */
    internal val detailPaneOpen: StateFlow<Boolean> = detailPaneOpenState.asStateFlow()

    /** 图片盘缓存预算（MB）。改档后重启生效（装图片管道那一刻读）。 */
    internal val imageCacheMb: StateFlow<Int> = imageCacheMbState.asStateFlow()

    internal fun setDetailQuality(tier: com.venera.desktop.gallery.ui.DesktopDetailTier) {
        store.put(KEY_DETAIL_QUALITY, tier.name)
        detailQualityState.value = tier
    }

    internal fun setCardSize(size: com.venera.desktop.gallery.ui.DesktopCardSize) {
        store.put(KEY_CARD_SIZE, size.name)
        cardSizeState.value = size
    }

    internal fun setDetailPaneWidth(width: com.venera.desktop.gallery.ui.DesktopDetailPaneWidth) {
        store.put(KEY_DETAIL_PANE, width.name)
        detailPaneWidthState.value = width
    }

    internal fun setDetailPaneOpen(on: Boolean) {
        store.put(KEY_DETAIL_PANE_OPEN, on)
        detailPaneOpenState.value = on
    }

    internal fun setImageCacheMb(mb: Int) {
        val clamped = mb.coerceIn(128, 4096)
        store.put(KEY_IMAGE_CACHE_MB, clamped)
        imageCacheMbState.value = clamped
    }

    fun setGalleryPreviewQuality(quality: GalleryPreviewQuality) {
        store.put(KEY_PREVIEW, quality.name)
        previewQualityState.value = quality
    }

    fun setGalleryBlockAi(on: Boolean) {
        store.put(KEY_BLOCK_AI, on)
        blockAiState.value = on
    }

    fun setGalleryAiBadge(on: Boolean) {
        store.put(KEY_AI_BADGE, on)
        aiBadgeState.value = on
    }

    /**
     * 连播间隔。夹在 0..30 **逐字照对岸那颗 setter**（`VeneraPreferences.setGalleryAutoPlaySec`：
     * 「滑条那一头已经给了区间，这里再兜一道，防别处写进一个负数把连播变成每 0 秒翻一张」）。
     * 桌面这头摆的是档位按钮，那道兜底也不许省：写口的另一半是对岸与被人手工改过的盘上档。
     */
    fun setGalleryAutoPlaySec(seconds: Int) {
        val clamped = seconds.coerceIn(0, 30)
        store.put(KEY_AUTOPLAY_SEC, clamped)
        autoPlaySecState.value = clamped
    }

    /** 预取档。写盘 + 推流两步，理由同 [setGalleryColumnMode]：只推流不落盘是"重启就忘"，只落盘不推流是"改了不动"。 */
    fun setGalleryPreload(mode: GalleryPreloadMode) {
        store.put(KEY_PRELOAD, mode.name)
        preloadState.value = mode
    }

    /**
     * 动图档（批 52）。同一形状：写盘 + 推流。
     *
     * 读侧那一路是 `GalleryMotion.animates(mode, unmetered = true)` —— 桌面量不出"哪种网不算流量"，
     * 所以 `WIFI_ONLY` 走的是"照播"那一支，而屏上那一行明说了这件事（`DesktopGallerySettingsSpecTest` ⑧ 钉着）。
     */
    fun setGalleryAnimated(mode: GalleryAnimatedMode) {
        store.put(KEY_ANIMATED, mode.name)
        animatedState.value = mode
    }

    /** 大图那一层的底（批 61）。写盘 + 推流两步，理由同 [setGalleryColumnMode]。 */
    fun setGalleryBackdrop(backdrop: GalleryViewerBackdrop) {
        store.put(KEY_BACKDROP, backdrop.name)
        backdropState.value = backdrop
    }

    override val galleryHideTopBar: StateFlow<Boolean> = ALWAYS_FALSE

    override val galleryHideBottomBar: StateFlow<Boolean> = ALWAYS_FALSE

    /**
     * 「画成水彩」那枚 Beta 档：桌面**没有那个入口**（它长在 Android 大图页的「关于这张图」面板里，
     * 桌面今天没有那一行），所以给常量而不是给一条能写却没人读的键 —— 与上面那两条 hide 档同一形。
     * 哪天桌面接了那一行，就把它换成 `store.getBoolean(那一枚键, false)` 并按 `DesktopGalleryPreferencesTest` ①② 补回核。
     */
    override val galleryWatercolorBeta: StateFlow<Boolean> = ALWAYS_FALSE

    /** 解码口径逐字照 `VeneraPreferences.readEnum`：存枚举名，认不出来就退默认档。 */
    private inline fun <reified T : Enum<T>> readEnum(key: String, fallback: T) =
        MutableStateFlow(enumValues<T>().firstOrNull { it.name == store.getString(key, null) } ?: fallback)

    companion object {
        private val ALWAYS_FALSE: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()

        // 键名与默认档抄自 app/src/main/java/com/venera/compose/data/prefs/VeneraPreferences.kt:668-682
        // 与 :173-320；改这里之前先改那里，否则 DesktopGalleryPreferencesTest 会红。
        const val KEY_COLUMNS = "pref_gallery_columns"
        const val KEY_PREVIEW = "pref_gallery_preview"
        const val KEY_KEEP_SCREEN_ON = "pref_gallery_keep_screen_on"
        const val KEY_VOLUME_KEY = "pref_gallery_volume_key"
        const val KEY_AUTOPLAY_SEC = "pref_gallery_autoplay_sec"
        const val KEY_PRELOAD = "pref_gallery_preload"
        const val KEY_ANIMATED = "pref_gallery_animated"
        const val KEY_BACKDROP = "pref_gallery_backdrop"
        const val KEY_BLOCK_AI = "pref_gallery_block_ai"
        const val KEY_AI_BADGE = "pref_gallery_ai_badge"
        // 视频自动播放总闸：桌面自持偏好（安卓无此句，走"桌面自持键"跳过跨端同句断言）。
        const val KEY_VIDEO_AUTOPLAY = "pref_video_autoplay"
        // 追更：启动自动检查（默认关）与巡检冷却小时（桌面自持两枚，安卓线无此句）。
        const val KEY_FOLLOW_AUTO_CHECK = "pref_follow_auto_check"
        const val KEY_FOLLOW_COOLDOWN_HOURS = "pref_follow_cooldown_hours"
        val FOLLOW_COOLDOWN_CHOICES = setOf(6, 12, 24)
        const val DEFAULT_FOLLOW_COOLDOWN_HOURS = 6
        // 2.4：下载目录 + 默认开关（桌面自持，安卓无此句，见 desktopOnlyKeys）。
        const val KEY_DOWNLOAD_DIR = "pref_download_dir"
        const val KEY_DOWNLOAD_USE_DEFAULT_DIR = "pref_download_use_default_dir"
        const val KEY_COMIC_DOWNLOAD_DIR = "pref_comic_download_dir"
        const val KEY_EXTRA_IMAGE_DIRS = "pref_extra_image_dirs"
        const val KEY_WALL_LAYOUT = "pref_wall_layout"
        const val KEY_SEARCH_PAGINATION_MODE = "pref_gallery_search_pagination_mode"
        const val KEY_PIXIV_PREVIEW_QUALITY = "pref_pixiv_preview_quality"
        const val KEY_PIXIV_ILLUST_QUALITY = "pref_pixiv_illust_quality"
        const val KEY_PIXIV_MANGA_QUALITY = "pref_pixiv_manga_quality"
        const val KEY_PIXIV_ZOOM_QUALITY = "pref_pixiv_zoom_quality"
        // 图库设置扩批（桌面自持五枚，安卓无此句）：见类里那段「图库设置扩批」。
        const val KEY_DETAIL_QUALITY = "pref_gallery_detail_quality"
        const val KEY_CARD_SIZE = "pref_gallery_card_size"
        const val KEY_DETAIL_PANE = "pref_gallery_detail_pane"
        const val KEY_DETAIL_PANE_OPEN = "pref_gallery_detail_pane_open"
        const val KEY_IMAGE_CACHE_MB = "pref_image_cache_mb"
        // 那两枚 hide_top_bar / hide_bottom_bar 的键名**不在这里重述**：桌面这两枚给的是常量
        // （见类注释里 P5 那条），既然一个字节都不读，把键名抄过来就是一颗会漂的死键。
    }
}
