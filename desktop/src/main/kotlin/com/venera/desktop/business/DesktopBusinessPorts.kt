package com.venera.desktop.business

import com.venera.compose.data.api.AppearancePreferences
import com.venera.compose.data.api.BusinessPorts
import com.venera.compose.data.api.ComicPreferences
import com.venera.compose.data.api.ContentGuard
import com.venera.compose.data.api.DownloadQueue
import com.venera.compose.data.api.FavoriteLibrary
import com.venera.compose.data.api.GuardRuleBook
import com.venera.compose.data.api.HttpTextFetch
import com.venera.compose.data.api.HttpTextResponse
import com.venera.compose.data.api.LocalComicLibrary
import com.venera.compose.data.api.NamedStoreFactory
import com.venera.compose.data.api.NetworkHygiene
import com.venera.compose.data.api.NetworkPreferences
import com.venera.compose.data.api.ReaderPreferences
import com.venera.compose.data.api.ReadingHistory
import com.venera.compose.data.api.ReadingStats
import com.venera.compose.stats.aggregateTagStats
import com.venera.compose.data.api.UpdateDownloadQueue
import com.venera.compose.data.db.FavoriteItem
import com.venera.compose.data.db.HistoryDao
import com.venera.compose.data.db.HistoryRecord
import com.venera.compose.data.db.LocalFavoritesManager
import com.venera.compose.data.platform.AppearanceStyle
import com.venera.compose.data.platform.ComicMetricsSink
import com.venera.compose.data.platform.JsonKeyValueStore
import com.venera.compose.data.platform.KeyValueStore
import com.venera.compose.data.platform.Logger
import com.venera.compose.data.platform.NavigationBarStyle
import com.venera.compose.data.platform.PathProvider
import com.venera.compose.data.platform.PreferenceKeys
import com.venera.compose.data.platform.SurfaceMaterial
import com.venera.compose.data.platform.TagTranslationMode
import com.venera.compose.data.platform.ThemeColorSource
import com.venera.compose.data.platform.ThemeMode
import com.venera.compose.download.LocalChapter
import com.venera.compose.download.LocalComic
import com.venera.compose.feature.ComicDetailCore
import com.venera.compose.feature.HistoryCore
import com.venera.compose.security.guard.GuardRule
import com.venera.compose.source.model.Comic
import com.venera.compose.source.model.ExplorePagePart
import com.venera.desktop.gallery.data.DesktopGalleryContentGuard
import com.venera.desktop.gallery.data.DesktopReaderMode
import com.venera.compose.stats.ComicStatItem
import com.venera.compose.stats.DailyTrendItem
import com.venera.compose.stats.ReadingStatsSummary
import com.venera.compose.stats.TagStats
import com.venera.desktop.platform.DesktopLogger
import com.venera.engineprobe.EngineSession
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 桌面端 `BusinessPorts` 装配点（D′-2）：把 17 族端口拼成一份，喂给共享 `ComicDetailCore`。
 *
 * 形状照 `AndroidBusinessPorts`（每族一颗 private 适配器、构造不调 `getInstance`、成员体现取单例），
 * 平台串走 [DesktopBusinessPorts.PLATFORM] = `"desktop"`（[BusinessPorts.install] 先到先得，测试不许换装）。
 *
 * **详情页真吃的六族 + downloads**：comics / sources（D′-1 三颗适配器）、history / favorites
 * （包共享 DAO）、contentGuard / guardRuleBook（包桌面守卫，打码判据单源）、downloads（包桌面队列 +
 * 盘上完成态共用判式）、readerPrefs.reverseChapterOrder、comicPrefs.quickFavorite。
 * 其余族详情**不吃**：如实 NoOp / 降级 + 每颗点名（′-2）。视觉与"假把手"的口径同屏侧：宁可降级读数，
 * 不摆按不动的东西。
 */
object DesktopBusinessPorts {

    const val PLATFORM = "desktop"

    /**
     * 装配句柄：把壳/装配点手上的平台件打包递进 [BusinessPorts.install] 的 factory。
     *
     * @param engine 常驻引擎（comics/sources 适配器每次成员调用各自 `load` 上锁）
     * @param assetDir / overrideDir 随包源目录与用户改写层（sources 适配器读脚本名 / cookieFields）
     * @param paths 数据根（readerPrefs / comicPrefs / metricsSink / stores 落 JSON）
     * @param downloadsRoot 现读下载根目录（拿不到 null ⇒ 下载读侧给"无离线内容"的诚实答案）
     * @param coverMask 打码判据（**单源**：装配点递 `DesktopUxPlatformServices.coverMaskStateOf` 同一颗，
     *                  contentGuard.maskStateFor 原样转发它，不重写 `when(maskMode)`，守
     *                  `DesktopMaskJudgementSingleSourceTest`）
     * @param rules 守卫规则本（标签长按"屏蔽此标签"写它；`DesktopGalleryContentGuard` 那颗）
     * @param guardMaskMode 遮罩档流（详情不吃这枚流，仅为 `ContentGuard.nsfwMaskMode` 契约给真值）
     */
    class Handle(
        val engine: EngineSession?,
        val assetDir: File,
        val overrideDir: File?,
        val paths: PathProvider,
        val downloadsRoot: () -> File?,
        val coverMask: (
            sourceKey: String, title: String, author: String, tags: List<String>, comicId: String,
        ) -> String,
        val rules: DesktopGalleryContentGuard,
        val guardMaskMode: StateFlow<String>,
    )

    @Volatile
    private var installed = false

    /** 幂等安装：同平台重复调用以先装的为准；[BusinessPorts.install] 自己守"平台串不同再装即抛"。 */
    fun install(handle: Handle) {
        if (installed) return
        BusinessPorts.install(PLATFORM) { received ->
            createPorts(received as? Handle ?: handle)
        }
        installed = true
    }

    private fun createPorts(handle: Handle): BusinessPorts {
        val comics = DesktopComicContentApi(handle.engine)
        val sources = DesktopSourceCatalogPort(handle.engine, handle.assetDir, handle.overrideDir)
        val readerPrefs = DesktopReaderPreferencesPort.getInstance(handle.paths)
        val comicPrefs = DesktopComicPreferencesPort.getInstance(handle.paths)
        val contentGuard = DesktopContentGuardPort(handle.coverMask, handle.guardMaskMode)
        val guardRuleBook = DesktopGuardRuleBookPort(handle.rules)
        val downloads: DownloadQueue = DesktopDownloadQueuePort(handle.downloadsRoot)
        val history = DesktopReadingHistoryPort()
        val favorites = DesktopFavoriteLibraryPort()
        return BusinessPorts(
            contentGuard = contentGuard,
            guardRuleBook = guardRuleBook,
            readerPrefs = readerPrefs,
            appearancePrefs = NoOpAppearancePreferences(),
            comicPrefs = comicPrefs,
            networkPrefs = NoOpNetworkPreferences(),
            stores = DesktopNamedStoreFactory(handle.paths),
            comics = comics,
            sources = sources,
            history = history,
            stats = NoOpReadingStats(),
            favorites = favorites,
            downloads = downloads,
            localComics = NoOpLocalComicLibrary(),
            network = NoOpNetworkHygiene(),
            httpText = NoOpHttpTextFetch(),
            updates = NoOpUpdateDownloadQueue(),
        )
    }

    /**
     * 构建一颗详情页内核（D′-3 用）。装配点只管递句柄 + 作用域，core 生命周期由壳里
     * `remember(route)` / `DisposableEffect` 管（不引 lifecycle-viewmodel，方案风险⑤）。
     */
    fun createDetailCore(handle: Handle, scope: CoroutineScope): ComicDetailCore =
        ComicDetailCore(
            ports = createPorts(handle),
            scope = scope,
            metricsSink = DesktopComicMetricsSink(handle.paths),
            logger = DesktopLogger,
        )

    /**
     * 构建一颗历史页内核（阶段二 · 阅读历史收敛复用）。
     *
     * 历史核只真吃 `history` / `favorites` 两族端口（[HistoryCore] 的成员体逐条核对），
     * 数据源就是共享 `HistoryDao`（其 `historyFlow` 在 init 与每次写后自刷，桌面这里无需另起读表）。
     * 作用域仍由壳里 `remember` + `DisposableEffect` 管（同详情那一族，不引 lifecycle-viewmodel）。
     */
    fun createHistoryCore(handle: Handle, scope: CoroutineScope): HistoryCore =
        HistoryCore(
            ports = createPorts(handle),
            scope = scope,
        )

    /** 构造漫画源目录端口（用于按 key 查找并调用网络收藏等单源能力）。 */
    internal fun createSourceCatalog(handle: Handle) =
        DesktopSourceCatalogPort(handle.engine, handle.assetDir, handle.overrideDir)
}

/**
 * 打码判据**单源转发**（D′-2）：[maskStateFor] 直接交回装配点那一条 `coverMaskStateOf`，
 * 这里不写第二遍 `when(maskMode)` / `looksAdult`（`DesktopMaskJudgementSingleSourceTest` 钉死单源）。
 * 详情只吃 `maskStateFor(sourceKey,...)`；`blockAiComics` / `filterComics` / `filterExploreParts`
 * 详情不吃，交中性读数（不藏、不改列表）。
 */
private class DesktopContentGuardPort(
    private val coverMask: (String, String, String, List<String>, String) -> String,
    private val maskMode: StateFlow<String>,
) : ContentGuard {

    override val nsfwMaskMode: StateFlow<String> = maskMode
    override val blockAiComics: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()

    override fun setNsfwMaskMode(mode: String) { /* 详情不写档；设置屏走 DesktopGalleryContentGuard 那颗写侧 */ }

    override fun setBlockAiComics(enabled: Boolean) { /* 详情不吃这一族 */ }

    override fun maskStateFor(
        title: String,
        author: String,
        tags: List<String>,
        comicId: String,
        description: String,
    ): String = coverMask("", title, author, tags, comicId)

    override fun maskStateFor(
        sourceKey: String,
        title: String,
        author: String,
        tags: List<String>,
        comicId: String,
        description: String,
    ): String = coverMask(sourceKey, title, author, tags, comicId)

    override fun maskStateFor(comic: Comic): String =
        coverMask(comic.sourceKey, comic.title, "", comic.tags, comic.id)

    // 详情列表不经过这族过滤（桌面探索/搜索各有取数层）：如实原样交回，不藏任何东西。
    override fun filterComics(comics: List<Comic>): List<Comic> = comics

    override fun filterExploreParts(parts: List<ExplorePagePart>): List<ExplorePagePart> = parts
}

/** 规则本：读 / 写都包装配点那顆 `DesktopGalleryContentGuard`（与墙上/长按屏蔽同一本规则账）。 */
private class DesktopGuardRuleBookPort(
    private val guard: DesktopGalleryContentGuard,
) : GuardRuleBook {

    override val rules: StateFlow<List<GuardRule>> = guard.rules

    override suspend fun addRule(type: String, pattern: String, isRegex: Boolean): Long =
        guard.addRule(type, pattern, isRegex)

    // deleteRule 在具体那颗上（`GalleryContentGuard` 接口只有 addRule）；详情链不写删除（那是设置屏的事）。
    override suspend fun deleteRule(id: Long): Boolean = guard.deleteRule(id)
}

/**
 * 阅读偏好（D′-2）：详情只吃 [reverseChapterOrder]（屏里那颗"倒序章节"默认档），走对岸**同名键、同文件**
 * （`pref_reverse_chapter_order` / [PreferenceKeys.PREFS_NAME]）。其余成员桌面阅读器各走自己的
 * `DesktopComicReaderPreferences`，详情不吃，如实中性默认 + NoOp 写侧（值从不显示、也从不落错盘）。
 */
/**
 * 阅读偏好（桌面）。internal：设置中枢「章节倒序」行要直接装配这一颗
 * （桌面从不走 BusinessPorts.install，[BusinessPorts.of] 在桌面侧必抛——崩例已见）。
 * 各装配点各自 new 的实例**同盘同名键**，写后重开详情读到新值；内存流不互相同步，是已知取舍。
 */
internal class DesktopReaderPreferencesPort private constructor(paths: PathProvider) : ReaderPreferences {

    private val store = JsonKeyValueStore(PreferenceKeys.PREFS_NAME, paths)

    private val _reverseChapterOrder = MutableStateFlow(store.getBoolean(KEY_REVERSE_CHAPTERS, false))
    override val reverseChapterOrder: StateFlow<Boolean> = _reverseChapterOrder.asStateFlow()

    override fun setReverseChapterOrder(enabled: Boolean) {
        store.put(KEY_REVERSE_CHAPTERS, enabled)
        _reverseChapterOrder.value = enabled
    }

    // 以下全部：详情不吃（桌面阅读器另有 `DesktopComicReaderPreferences`），中性默认 + NoOp。
    // 档位名不写字面串：走那张表（`DesktopReaderMode.key`），否则与表漂（`DesktopReaderPagedSeedTest` 钉）。
    override val defaultReadingMode: StateFlow<String> = const(DesktopReaderMode.HORIZONTAL_LTR.key)
    override val clickToTurn: StateFlow<Boolean> = const(true)
    override val reverseTapDirection: StateFlow<Boolean> = const(false)
    override val volumeKeyTurn: StateFlow<Boolean> = const(false)
    override val keepScreenOn: StateFlow<Boolean> = const(true)
    override val nightFilter: StateFlow<Boolean> = const(false)
    override val pageGapDp: StateFlow<Float> = const(8f)
    override val autoScrollPageIntervalSec: StateFlow<Float> = const(3f)
    override val preloadImageCount: StateFlow<Int> = const(5)
    override val autoNextChapter: StateFlow<Boolean> = const(true)
    override val readerEnhanceEnabled: StateFlow<Boolean> = const(false)
    override val readerEnhanceSharpen: StateFlow<Float> = const(0f)
    override val readerEnhanceContrast: StateFlow<Float> = const(0f)
    override val readerEnhanceSaturation: StateFlow<Float> = const(0f)
    override val readerEnhanceBrightness: StateFlow<Float> = const(0f)
    override val pageTurnEffect: StateFlow<String> = const("NONE")

    override fun setDefaultReadingMode(mode: String) {}
    override fun setClickToTurn(enable: Boolean) {}
    override fun setReverseTapDirection(enabled: Boolean) {}
    override fun setVolumeKeyTurn(enable: Boolean) {}
    override fun setKeepScreenOn(enable: Boolean) {}
    override fun setNightFilter(enable: Boolean) {}
    override fun setPageGapDp(gap: Float) {}
    override fun setAutoScrollPageIntervalSec(sec: Float) {}
    override fun setPreloadImageCount(count: Int) {}
    override fun setAutoNextChapter(enabled: Boolean) {}
    override fun setReaderEnhanceEnabled(enabled: Boolean) {}
    override fun setReaderEnhanceSharpen(value: Float) {}
    override fun setReaderEnhanceContrast(value: Float) {}
    override fun setReaderEnhanceSaturation(value: Float) {}
    override fun setReaderEnhanceBrightness(value: Float) {}
    override fun setPageTurnEffect(effect: String) {}

    private fun <T> const(value: T): StateFlow<T> = MutableStateFlow(value).asStateFlow()

    companion object {
        // 与 Android 侧 `VeneraPreferences.KEY_REVERSE_CHAPTERS` 同名键（详情只读这一枚）。
        private const val KEY_REVERSE_CHAPTERS = "pref_reverse_chapter_order"

        private val cache = HashMap<String, DesktopReaderPreferencesPort>()

        /**
         * 取同一数据根下的共享实例。
         *
         * 各装配点各自 new 会各持一条内存流：设置页写完盘，详情屏手上那条流仍是旧值，
         * 于是"改了不动、重启才生效"。按路径缓存后写读落在同一对象上。
         * @param paths 数据根（缓存键取其绝对路径）
         */
        fun getInstance(paths: PathProvider): DesktopReaderPreferencesPort =
            synchronized(cache) {
                cache.getOrPut(paths.dataRoot.absolutePath) { DesktopReaderPreferencesPort(paths) }
            }
    }
}

/**
 * 漫画偏好（D′-2）：详情只吃 [quickFavorite]（长按收藏的直达夹），同名键 `pref_quick_favorite`。
 * 其余成员详情不吃，中性默认 + NoOp。两个 `var` 用普通背板字段（不读不写也不出错）。
 */
/**
 * 漫画偏好（桌面）。internal：设置中枢「收藏」区直接装配（同上，桌面无 install）。
 * 收藏四枚键与快捷夹详情读的是同一份盘上键值，跨实例一致走磁盘。
 */
internal class DesktopComicPreferencesPort private constructor(paths: PathProvider) : ComicPreferences {

    private val store = JsonKeyValueStore(PreferenceKeys.PREFS_NAME, paths)

    private val _quickFavorite = MutableStateFlow(store.getString(KEY_QUICK_FAVORITE, null))
    override val quickFavorite: StateFlow<String?> = _quickFavorite.asStateFlow()

    override fun setQuickFavorite(folder: String?) {
        store.put(KEY_QUICK_FAVORITE, folder)
        _quickFavorite.value = folder
    }

    override val defaultSearchTarget: StateFlow<String> = MutableStateFlow("").asStateFlow()
    override val checkUpdateOnStart: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()
    override var lastUpdateCheckAt: Long = 0L
    override var ignoredUpdateVersion: String? = null

    // ── 收藏三枚落真（此前是假流，设置中枢「收藏」区的写侧）──
    private val _favoriteSortOrder =
        MutableStateFlow(store.getString(KEY_FAVORITE_SORT_ORDER, "time") ?: "time")
    override val favoriteSortOrder: StateFlow<String> = _favoriteSortOrder.asStateFlow()

    private val _newFavoriteAddTo =
        MutableStateFlow(store.getString(KEY_NEW_FAVORITE_ADD_TO, "默认") ?: "默认")
    override val newFavoriteAddTo: StateFlow<String> = _newFavoriteAddTo.asStateFlow()

    private val _followUpdatesFolder =
        MutableStateFlow(store.getString(KEY_FOLLOW_UPDATES_FOLDER, null))
    override val followUpdatesFolder: StateFlow<String?> = _followUpdatesFolder.asStateFlow()

    override val secureScreen: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()

    override fun setDefaultSearchTarget(key: String) {}
    override fun setCheckUpdateOnStart(enable: Boolean) {}
    override fun setFavoriteSortOrder(order: String) {
        store.put(KEY_FAVORITE_SORT_ORDER, order)
        _favoriteSortOrder.value = order
    }
    override fun setNewFavoriteAddTo(value: String) {
        store.put(KEY_NEW_FAVORITE_ADD_TO, value)
        _newFavoriteAddTo.value = value
    }
    override fun setFollowUpdatesFolder(folder: String?) {
        store.put(KEY_FOLLOW_UPDATES_FOLDER, folder)
        _followUpdatesFolder.value = folder
    }
    override fun setSecureScreen(enabled: Boolean) {}

    companion object {
        // 与 Android 侧 `VeneraPreferences.KEY_QUICK_FAVORITE` 同名键（详情只读这一枚）。
        private const val KEY_QUICK_FAVORITE = "pref_quick_favorite"
        // 收藏三枚同名键（照 PreferenceKeys / 对岸 VeneraPreferences 的写法）。
        private const val KEY_FAVORITE_SORT_ORDER = "pref_favorite_sort_order"
        private const val KEY_NEW_FAVORITE_ADD_TO = "pref_new_favorite_add_to"
        private const val KEY_FOLLOW_UPDATES_FOLDER = "pref_follow_updates_folder"

        private val cache = HashMap<String, DesktopComicPreferencesPort>()

        /**
         * 取同一数据根下的共享实例。
         *
         * 理由同 [DesktopReaderPreferencesPort.getInstance]：设置页与详情、收藏屏必须是同一颗对象，
         * 否则屏上的选中态要重启才跟得上设置。
         * @param paths 数据根（缓存键取其绝对路径）
         */
        fun getInstance(paths: PathProvider): DesktopComicPreferencesPort =
            synchronized(cache) {
                cache.getOrPut(paths.dataRoot.absolutePath) { DesktopComicPreferencesPort(paths) }
            }
    }
}

/** 收藏库：包共享 `LocalFavoritesManager`（成员体现取单例，构造不调 —— 照对岸纪律）。 */
private class DesktopFavoriteLibraryPort : FavoriteLibrary {

    private val manager: LocalFavoritesManager get() = LocalFavoritesManager.getInstance(DesktopBusinessPorts.PLATFORM)

    override val folders: StateFlow<List<String>> get() = manager.folders
    override val counts: StateFlow<Map<String, Int>> get() = manager.counts
    override val version: StateFlow<Int> get() = manager.version
    override suspend fun getAllComics(): List<FavoriteItem> = manager.getAllComics()
    override suspend fun getFolderComics(folder: String): List<FavoriteItem> = manager.getFolderComics(folder)
    override suspend fun search(keyword: String): List<FavoriteItem> = manager.search(keyword)
    override suspend fun searchInFolder(folder: String, keyword: String): List<FavoriteItem> =
        manager.searchInFolder(folder, keyword)
    override suspend fun find(id: String, sourceKey: String): List<String> = manager.find(id, sourceKey)
    override suspend fun isExist(id: String, sourceKey: String): Boolean = manager.isExist(id, sourceKey)
    override suspend fun addComic(folder: String, comic: FavoriteItem, order: Int?, updateTime: String?): Boolean =
        manager.addComic(folder, comic, order, updateTime)
    override suspend fun deleteComicWithId(folder: String, id: String, sourceKey: String) =
        manager.deleteComicWithId(folder, id, sourceKey)
    override suspend fun batchDeleteComics(folder: String, comics: List<FavoriteItem>) =
        manager.batchDeleteComics(folder, comics)
    override suspend fun batchDeleteComicsInAllFolders(comics: List<FavoriteItem>) =
        manager.batchDeleteComicsInAllFolders(comics)
    override suspend fun batchMoveFavorites(s: String, t: String, comics: List<FavoriteItem>) =
        manager.batchMoveFavorites(s, t, comics)
    override suspend fun batchCopyFavorites(s: String, t: String, comics: List<FavoriteItem>) =
        manager.batchCopyFavorites(s, t, comics)
    override suspend fun createFolder(name: String, renameWhenInvalidName: Boolean): String =
        manager.createFolder(name, renameWhenInvalidName)
    override suspend fun deleteFolder(name: String) = manager.deleteFolder(name)
    override suspend fun rename(before: String, after: String) = manager.rename(before, after)
    override suspend fun updateOrder(folders: List<String>) = manager.updateOrder(folders)
    override suspend fun reorder(newFolder: List<FavoriteItem>, folder: String) = manager.reorder(newFolder, folder)
    override suspend fun prepareTableForFollowUpdates(table: String, clearData: Boolean) =
        manager.prepareTableForFollowUpdates(table, clearData)
}

/** 阅读历史：包共享 `HistoryDao`（详情只读 `historyFlow`；写侧详情链不走）。 */
private class DesktopReadingHistoryPort : ReadingHistory {

    private val dao: HistoryDao get() = HistoryDao.getInstance(DesktopBusinessPorts.PLATFORM)

    override val historyFlow: StateFlow<List<HistoryRecord>> get() = dao.historyFlow
    override suspend fun saveHistory(record: HistoryRecord) = dao.saveHistory(record)
    override suspend fun deleteHistory(comicId: String, sourceName: String) = dao.deleteHistory(comicId, sourceName)
    override suspend fun batchDeleteHistory(records: List<HistoryRecord>) = dao.batchDeleteHistory(records)
    override suspend fun clearAll() = dao.clearAll()
}

/**
 * 指标写侧（非 17 族，构核时给 `ComicDetailCore`）：落 `PREFS_COMIC_METRICS` 那张 JSON 表，
 * `put` 逐字照对岸 `ComicMetricsCache.put`（没报的栏不覆盖旧值、长度前缀键）。键公式在此重述一份 ——
 * `comicMetricCacheKey` 住在 `data/prefs`（桌面禁引族），把纯函数搬进共享层是另一笔跨平台改动，本轮从简。
 */
internal class DesktopComicMetricsSink(paths: PathProvider) : ComicMetricsSink {

    private val store = JsonKeyValueStore(PreferenceKeys.PREFS_COMIC_METRICS, paths)

    override fun put(sourceKey: String, comicId: String, rating: Double?, likesCount: Int?) {
        if (sourceKey.isBlank() || comicId.isBlank()) return
        val validRating = rating?.takeIf { it.isFinite() && it >= 0 }
        val validLikes = likesCount?.takeIf { it >= 0 }
        if (validRating == null && validLikes == null) return
        val key = metricsKey(sourceKey, comicId)
        val values = mutableMapOf<String, Any?>()
        validRating?.let { values["$key:rating"] = it.toString() }
        validLikes?.let { values["$key:likes"] = it.toString() }
        store.putAll(values)
    }

    /** 与对岸 `comicMetricCacheKey` 同构：长度前缀避免源键/号里带分隔符时撞键。 */
    private fun metricsKey(sourceKey: String, comicId: String): String =
        "${sourceKey.length}:$sourceKey$comicId"
}

/** 自有缓存工厂：详情链不吃（首页快照 / 搜索历史那两枚），但契约要给 —— 用共享 JsonKeyValueStore 现开。 */
private class DesktopNamedStoreFactory(private val paths: PathProvider) : NamedStoreFactory {
    override fun open(name: String): KeyValueStore = JsonKeyValueStore(name, paths)
}

// ── 以下五族详情完全不吃：NoOp / 降级实现，各点名（′-2「appearancePrefs/networkPrefs/stores/
//    stats/localComics/network/httpText/updates 详情不吃」）。留着是为了 17 参构造能装配齐，
//    而不是它们真在详情链上。 ──

private class NoOpAppearancePreferences : AppearancePreferences {
    override val themeMode: StateFlow<ThemeMode> = MutableStateFlow(ThemeMode.DARK).asStateFlow()
    override val appearanceStyle: StateFlow<AppearanceStyle> =
        MutableStateFlow(AppearanceStyle.MD3).asStateFlow()
    override val navigationBarStyle: StateFlow<NavigationBarStyle> =
        MutableStateFlow(NavigationBarStyle.DOCKED).asStateFlow()
    override val surfaceMaterial: StateFlow<SurfaceMaterial> =
        MutableStateFlow(SurfaceMaterial.SOLID).asStateFlow()
    override val tagTranslationMode: StateFlow<TagTranslationMode> =
        MutableStateFlow(TagTranslationMode.SYSTEM).asStateFlow()
    override val themeColorSource: StateFlow<ThemeColorSource> =
        MutableStateFlow(ThemeColorSource.WALLPAPER).asStateFlow()
    override val themeSeedColor: StateFlow<Int> = MutableStateFlow(0).asStateFlow()
    override val settingsHeroPath: StateFlow<String> = MutableStateFlow("").asStateFlow()
    override val settingsQuoteAvatarPath: StateFlow<String> = MutableStateFlow("").asStateFlow()
    override val startPage: StateFlow<String> = MutableStateFlow("").asStateFlow()
    override val homePetSticker: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()
    override fun setThemeMode(mode: ThemeMode) {}
    override fun setAppearanceStyle(style: AppearanceStyle) {}
    override fun setNavigationBarStyle(style: NavigationBarStyle) {}
    override fun setSurfaceMaterial(material: SurfaceMaterial) {}
    override fun setTagTranslationMode(mode: TagTranslationMode) {}
    override fun setThemeColorSource(source: ThemeColorSource) {}
    override fun setThemeSeedColor(argb: Int) {}
    override fun setSettingsHeroPath(path: String) {}
    override fun setSettingsQuoteAvatarPath(path: String) {}
    override fun setStartPage(tab: String) {}
    override fun setHomePetSticker(on: Boolean) {}
}

/**
 * 网络偏好（桌面）。档二落真（此前整域 NoOp）：代理三枚 / 下载并发 / HTTP 缓存上限
 * 真读真写（同名键照对岸 VeneraPreferences）；消费端 [com.venera.desktop.platform.DesktopHttpEngine]
 * 与下载队列**冷启动读盘** ⇒ 改完重启生效（行上如实标注）。comicStoragePath 桌面由 DesktopPaths 定根，
 * 本域的写口摆着但消费点在迁移批；CF 优选三枚仍 NoOp（判据域在桌面禁引族，接入随测速批）。
 */
internal class DesktopNetworkPreferencesPort(paths: PathProvider) : NetworkPreferences {

    private val store = JsonKeyValueStore(PreferenceKeys.PREFS_NAME, paths)

    private val _proxyType = MutableStateFlow(store.getString(KEY_PROXY_TYPE, "系统") ?: "系统")
    override val proxyType: StateFlow<String> = _proxyType.asStateFlow()

    private val _proxyHost = MutableStateFlow(store.getString(KEY_PROXY_HOST, "") ?: "")
    override val proxyHost: StateFlow<String> = _proxyHost.asStateFlow()

    private val _proxyPort = MutableStateFlow(store.getInt(KEY_PROXY_PORT, 0))
    override val proxyPort: StateFlow<Int> = _proxyPort.asStateFlow()

    private val _downloadThreads = MutableStateFlow(store.getInt(KEY_DOWNLOAD_THREADS, 1))
    override val downloadThreads: StateFlow<Int> = _downloadThreads.asStateFlow()

    private val _httpCacheMaxMb = MutableStateFlow(store.getInt(KEY_HTTP_CACHE_MAX_MB, 100))
    override val httpCacheMaxMb: StateFlow<Int> = _httpCacheMaxMb.asStateFlow()

    override val comicStoragePath: StateFlow<String> = MutableStateFlow("").asStateFlow()
    override val cfPreferredIpEnabled: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()
    override val cfPreferredIps: StateFlow<String> = MutableStateFlow("").asStateFlow()
    override val cfPreferredHosts: StateFlow<String> = MutableStateFlow("").asStateFlow()

    override fun setProxy(type: String, host: String, port: Int) {
        store.put(KEY_PROXY_TYPE, type)
        store.put(KEY_PROXY_HOST, host)
        store.put(KEY_PROXY_PORT, port)
        _proxyType.value = type
        _proxyHost.value = host
        _proxyPort.value = port
    }

    override fun setDownloadThreads(threads: Int) {
        store.put(KEY_DOWNLOAD_THREADS, threads)
        _downloadThreads.value = threads
    }

    override fun setHttpCacheMaxMb(mb: Int) {
        store.put(KEY_HTTP_CACHE_MAX_MB, mb)
        _httpCacheMaxMb.value = mb
    }

    override fun setComicStoragePath(path: String) {}
    override fun setCfPreferredIp(enabled: Boolean, ips: String, hosts: String) {}

    companion object {
        // 同名键照对岸 VeneraPreferences（975-980 行那一族），两端盘上互认。
        private const val KEY_PROXY_TYPE = "pref_proxy_type"
        private const val KEY_PROXY_HOST = "pref_proxy_host"
        private const val KEY_PROXY_PORT = "pref_proxy_port"
        private const val KEY_DOWNLOAD_THREADS = "pref_download_threads"
        private const val KEY_HTTP_CACHE_MAX_MB = "pref_http_cache_max_mb"
    }
}

private class NoOpNetworkPreferences : NetworkPreferences {
    override val proxyType: StateFlow<String> = MutableStateFlow("").asStateFlow()
    override val proxyHost: StateFlow<String> = MutableStateFlow("").asStateFlow()
    override val proxyPort: StateFlow<Int> = MutableStateFlow(0).asStateFlow()
    override val downloadThreads: StateFlow<Int> = MutableStateFlow(3).asStateFlow()
    override val httpCacheMaxMb: StateFlow<Int> = MutableStateFlow(0).asStateFlow()
    override val comicStoragePath: StateFlow<String> = MutableStateFlow("").asStateFlow()
    override val cfPreferredIpEnabled: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()
    override val cfPreferredIps: StateFlow<String> = MutableStateFlow("").asStateFlow()
    override val cfPreferredHosts: StateFlow<String> = MutableStateFlow("").asStateFlow()
    override fun setProxy(type: String, host: String, port: Int) {}
    override fun setDownloadThreads(threads: Int) {}
    override fun setHttpCacheMaxMb(mb: Int) {}
    override fun setComicStoragePath(path: String) {}
    override fun setCfPreferredIp(enabled: Boolean, ips: String, hosts: String) {}
}

private class NoOpReadingStats : ReadingStats {
    override suspend fun recordSession(
        comicId: String, comicTitle: String, sourceName: String, tags: List<String>,
        chapterTitle: String, pagesRead: Int, durationSeconds: Long,
    ) {
        // 详情不记统计（桌面另有 `DesktopReadingStats` 那条读侧路）。
    }

    override suspend fun getSummary(): ReadingStatsSummary = ReadingStatsSummary()
    override suspend fun getRecent14DaysTrend(): List<DailyTrendItem> = emptyList()
    override suspend fun getTopComics(limit: Int): List<ComicStatItem> = emptyList()
    override suspend fun getTagStats(days: Int): TagStats = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val core = com.venera.compose.data.db.DatabasePorts.of(null).core
            val store = com.venera.compose.data.db.ReadingStatsStore(core)
            val today = java.time.LocalDate.now()
            val fromDate = today.minusDays(days.toLong()).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val rows = store.taggedRows(fromDate)
            com.venera.compose.stats.aggregateTagStats(rows, days, com.venera.compose.stats.ReadingContentRating.ALL) {
                com.venera.desktop.desktopTopicOf(it)
            }
        }.getOrDefault(TagStats())
    }
}

private class NoOpLocalComicLibrary : LocalComicLibrary {
    override suspend fun getLocalComics(): List<LocalComic> = emptyList()
    override suspend fun getLocalChapters(comic: LocalComic): List<LocalChapter> = emptyList()
    override suspend fun deleteLocalComic(comic: LocalComic): Boolean = false
    override suspend fun exportToCbz(comic: LocalComic, targetFile: File, onProgress: (Float) -> Unit) =
        Result.failure<File>(UnsupportedOperationException("详情不吃本地漫画这一族"))
    override suspend fun importCbz(archiveFile: File, customTitle: String?) =
        Result.failure<LocalComic>(UnsupportedOperationException("详情不吃本地漫画这一族"))
}

private class NoOpNetworkHygiene : NetworkHygiene {
    override fun resetBreaker(host: String) {}
    override fun resetAllBreakers() {}
    override fun unreachableHosts(): Set<String> = emptySet()
    override fun rebuildHttpClient() {}
    override fun httpCacheSizeBytes(): Long = 0L
    override fun clearHttpCache(): Long = 0L
}

private class NoOpHttpTextFetch : HttpTextFetch {
    override suspend fun fetchText(url: String): HttpTextResponse =
        HttpTextResponse(ok = false, code = 0, body = null)
}

private class NoOpUpdateDownloadQueue : UpdateDownloadQueue {
    override val state: StateFlow<com.venera.compose.data.update.UpdateDownload> =
        MutableStateFlow<com.venera.compose.data.update.UpdateDownload>(
            com.venera.compose.data.update.UpdateDownload.Idle,
        ).asStateFlow()
    override fun start(asset: com.venera.compose.data.update.ReleaseAsset) {}
    override fun cancel() {}
}
