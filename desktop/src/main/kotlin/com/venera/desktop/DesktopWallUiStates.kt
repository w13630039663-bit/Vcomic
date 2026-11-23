package com.venera.desktop

import androidx.compose.ui.input.key.key
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.venera.compose.gallery.domain.GalleryPreviewQuality
import com.venera.desktop.gallery.ui.DesktopGallerySavedView
import com.venera.desktop.detail.desktopCommentsInitialView
import com.venera.desktop.gallery.ui.DesktopWallCardModel
import com.venera.desktop.gallery.ui.DesktopComicDownloadListing
import com.venera.desktop.gallery.ui.DESKTOP_WALL_QUALITY
import com.venera.desktop.gallery.data.DesktopBrowseView
import com.venera.desktop.gallery.data.DesktopGalleryBrowseHistory

/**
 * 探索分类面板里当前点中的那一格。
 *
 * [display] 是屏上选中态/标题用的串（`组名:条目名` 或 `排行榜:档位名`）；
 * [label]/[param]/[kind] 才是取数要的三格（`kind = "ranking"` 时 [param] 是排行榜档位 key）。
 */
internal data class SavedUiState(
    val view: DesktopGallerySavedView,
    val query: String,
    val siteKey: String?,
)

internal data class ExplorePick(
    val display: String,
    val label: String,
    val param: String?,
    val kind: String,
)



/** 偏好缺位那档的常量流（不新造第二份状态；`collectAsState` 两条分支同型）。 */
internal fun <T> constFlow(value: T) = kotlinx.coroutines.flow.MutableStateFlow(value)




/** 备份与还原那一屏的壳侧状态；打包理由见声明处。 */
internal class BackupUiState {
    var note by mutableStateOf<String?>(null)
    var lastCounts by mutableStateOf<DesktopBackupCounts?>(null)
    var lastRestore by mutableStateOf<DesktopRestoreOutcome?>(null)
    var armed by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var lastPath by mutableStateOf<String?>(null)
    var assetCounts by mutableStateOf(DesktopLocalAssetCounts())
    var includeSourceAccounts by mutableStateOf(false)
    var importStage by mutableStateOf(DesktopBackupImportStage.IDLE)
    var preview by mutableStateOf<DesktopArchivePreview?>(null)
    var selectedModules by mutableStateOf(emptySet<DesktopBackupModule>())
    var importStrategy by mutableStateOf(DesktopImportStrategy.MERGE)
    var veneraRestore by mutableStateOf<DesktopVeneraRestoreOutcome?>(null)
    var stagingFile by mutableStateOf<java.io.File?>(null)
    var davConfig by mutableStateOf<com.venera.desktop.platform.DesktopWebDavSync.Config?>(null)
    var dragOver by mutableStateOf(false)
    var assetRefreshTick by mutableStateOf(0)
}

/**
 * 墙上卡片取哪一档预览清晰度：设置流的**当前值**，现取现用。
 *
 * 与取数腿的 effect 键解耦：改这一档不重刷已上屏的整墙，刷新由手动扳机负责。
 */
/**
 * 开一篇 pixiv 作品的路由：能在那本列里找到前后两篇，就一并装进路由（作品页那两枚箭头吃它）。
 *
 * 判据全在 `DesktopPixivWorkTrail` —— 这里只把"哪一列"的账翻成路由里那两格；
 * 找不到 = 两格 null = 箭头不摆（不替用户编顺序）。
 */
internal fun pixivWorkRouteOf(
    illustId: Long,
    title: String,
    coverUrl: String,
    from: DesktopRoute?,
): DesktopRoute.PixivWork {
    val around = DesktopPixivWorkTrail.around(illustId)
    fun neighborOf(entry: DesktopPixivWorkTrail.Entry?) = entry?.let {
        DesktopRoute.DesktopWorkNeighbor(it.illustId, it.title, it.coverUrl)
    }
    return DesktopRoute.PixivWork(
        illustId = illustId,
        title = title,
        coverUrl = coverUrl,
        from = from,
        prevWork = neighborOf(around?.first),
        nextWork = neighborOf(around?.second),
    )
}

internal fun currentWallQuality(
    prefs: com.venera.desktop.gallery.data.DesktopGalleryPreferences?,
): com.venera.compose.gallery.domain.GalleryPreviewQuality =
    prefs?.galleryPreviewQuality?.value ?: DESKTOP_WALL_QUALITY

/** 探索分类面板的壳侧状态；打包理由见声明处。 */
internal class ExplorePickUiState {
    var pick by mutableStateOf<ExplorePick?>(null)
    var page by mutableStateOf<com.venera.engineprobe.EngineSession.Page?>(null)
    var maxPage by mutableStateOf<Int?>(null)
    var loading by mutableStateOf(false)
}

/**
 * 首页每日热门两源账本 + 聚合编辑台腿（pixiv 三档 + Konachan 热门分）；打包理由见声明处（255 槽顶）。
 */
internal val DAILY_PIXIV_MODES = listOf(
    com.venera.compose.gallery.data.PixivRankingMode.DAILY,
    com.venera.compose.gallery.data.PixivRankingMode.WEEKLY,
    com.venera.compose.gallery.data.PixivRankingMode.MONTHLY,
)

internal class DailyGalleryUiState(homePixivPick: Boolean = false) {
    // 首页每日热门源档（false=图库 / true=pixiv 日榜），railPrefs 键 pref_daily_home_pixiv。
    var homePixivPick by mutableStateOf(homePixivPick)
    // 首页每日热门两源账本
    var homeCards by mutableStateOf<List<DesktopWallCardModel>>(emptyList())
    var homePixivCards by mutableStateOf<List<DesktopWallCardModel>>(emptyList())
    var homeLoadedTick = mutableIntStateOf(-1)
    var homePixivLoadedTick = mutableIntStateOf(-1)
    var homeFailed by mutableStateOf(false)
    // 聚合编辑台 pixiv 腿：三档共用一条腿，续页真取
    var pixivTab by mutableIntStateOf(0)
    var pixivCards by mutableStateOf<List<DesktopWallCardModel>>(emptyList())
    var pixivAvatars by mutableStateOf<Map<String, String>>(emptyMap())
    var pixivTitles by mutableStateOf<Map<String, String>>(emptyMap())
    var pixivPage by mutableIntStateOf(1)
    var pixivHasMore by mutableStateOf(true)
    var pixivLoading by mutableStateOf(false)
    var pixivLoadTick by mutableIntStateOf(0)
    // 聚合编辑台 Konachan 腿：热门分（order:score）可分页；落屏存守卫投影后的卡
    var konachanCards by mutableStateOf<List<DesktopWallCardModel>>(emptyList())
    // 聚合编辑台 Yande 每日榜：原始池过守卫投影后的卡（同一把尺，图库/pixiv 卡同形）
    var yandeCards by mutableStateOf<List<DesktopWallCardModel>>(emptyList())
    var konachanPage by mutableIntStateOf(1)
    var konachanLoading by mutableStateOf(false)
    var konachanHasMore by mutableStateOf(true)
    var konachanLoadTick by mutableIntStateOf(0)
}

/**
 * 珍藏册搜索维度的一整份壳级状态。
 *
 * 合成 holder 的理由见壳内声明处注释：壳捕获面有槽位预算（`DesktopShellStateCaptureTest`），
 * 字段是本类里的 mutableStateOf（不占壳的捕获槽），读它的组合自动订阅。
 * `offset <= 0` = 没有下一页（-1 = 到底）。
 */
internal class PixivCollectionSearchLeg {
    var cards: List<com.venera.compose.gallery.data.PixivCollectionCard> by mutableStateOf(emptyList())
    var total: Int by mutableIntStateOf(0)
    var offset: Int by mutableIntStateOf(-1)
}
/** 壳层网络收藏状态容器，收拢顶层 Composable 捕获槽位。 */
internal class NetFavUiState {
    var netFavSources by mutableStateOf<List<com.venera.desktop.favorites.NetFavSourceRow>?>(null)
    var netFavExpandedKey by mutableStateOf<String?>(null)
    var netFavPage by mutableStateOf<com.venera.desktop.favorites.NetFavPageState?>(null)
    var netFavFolders by mutableStateOf<com.venera.desktop.favorites.NetFavFoldersState?>(null)
    var netFavFolderPicked by mutableStateOf<String?>(null)
    var netFavNext by mutableStateOf<String?>(null)
    var netFavPageNo by mutableIntStateOf(1)
    var netFavMaxPage by mutableStateOf<Int?>(null)
    var netFavLoadingMore by mutableStateOf(false)
    var netFavMoreTick by mutableIntStateOf(0)
    var netFavReloadTick by mutableIntStateOf(0)
}

/** 壳层源管理、源编辑与登录状态容器，收拢顶层 Composable 捕获槽位。 */
internal class SourceManageUiState {
    var sourceRows by mutableStateOf<List<com.venera.desktop.sourcemanage.DesktopSourceRowUi>>(emptyList())
    var sourceNote by mutableStateOf<String?>(null)
    var sourceUpdateBusy by mutableStateOf(false)
    var sourceUpdateNote by mutableStateOf<String?>(null)
    var sourceSelectedKey by mutableStateOf<String?>(null)
    var sourceSettings by mutableStateOf<List<com.venera.desktop.sourcemanage.DesktopSourceSettingUi>?>(null)
    var sourceSettingsNote by mutableStateOf<String?>(null)
    var sourceAccount by mutableStateOf<com.venera.desktop.sourcemanage.DesktopSourceAccountUi?>(null)
    var sourceDetailTick by mutableIntStateOf(0)
    var sourceEditText by mutableStateOf<String?>(null)
    var sourceEditNote by mutableStateOf<String?>(null)
    var sourceEditTick by mutableIntStateOf(0)
    var loginDraft by mutableStateOf<Map<String, String>?>(null)
    var loginPasswordDraft by mutableStateOf<Pair<String, String>?>(null)
    var loginWebviewTrigger by mutableStateOf(false)
    var loginTick by mutableIntStateOf(0)
    // 幂等游标：同一 loginTick 只发一次车。effect 内清空草稿会重跑本 effect，
    // 没有这枚就会拿同一 tick 再提交一遍（按钮禁用挡得住手点，挡不住重组重入）。
    var loginHandledTick by mutableIntStateOf(-1)
    var loginNote by mutableStateOf<String?>(null)
    var loginBusy by mutableStateOf(false)
}

/** 壳层下载队列、本地漫画与图片库辅助状态容器，收拢顶层 Composable 捕获槽位。 */
internal class FilesAuxUiState {
    var downloadsListing by mutableStateOf<DesktopComicDownloadListing?>(null)
    var comicCardBadges by mutableStateOf<Map<String, com.venera.desktop.gallery.data.DesktopComicOfflineBadge>>(emptyMap())
    var downloadsNote by mutableStateOf<String?>(null)
    var localComicNote by mutableStateOf<String?>(null)
    var localComicImportBusy by mutableStateOf(false)
    var localComicImportPick by mutableStateOf<java.io.File?>(null)
    var localFolderImportPick by mutableStateOf<java.io.File?>(null)
    var importedComics by mutableStateOf<List<com.venera.desktop.gallery.data.DesktopLocalComic>>(emptyList())
    var filesDomain by mutableStateOf(com.venera.desktop.downloads.DesktopFilesDomain.COMIC)
    var imageItems by mutableStateOf<List<com.venera.desktop.downloads.DesktopLocalImageItem>?>(null)
    var imageScanTick by mutableIntStateOf(0)
    var imageNote by mutableStateOf<String?>(null)
    var imageGroupMode by mutableStateOf(com.venera.desktop.downloads.DesktopImageGroupMode.ARTIST)
    var queueExpandedTaskId by mutableStateOf<String?>(null)
    var queueDetailFiles by mutableStateOf<List<Pair<String, Long>>>(emptyList())
    var queueDetailLoading by mutableStateOf(false)
    var queueNote by mutableStateOf<String?>(null)
    var queueWasBusy by mutableStateOf(false)
}

/** 壳层画廊浏览历史与清空收藏状态容器，收拢顶层 Composable 捕获槽位。 */
internal class BrowseClearUiState {
    var browseRows by mutableStateOf<List<DesktopBrowseView>?>(null)
    var browseNote by mutableStateOf<String?>(null)
    var browseTick by mutableIntStateOf(0)
    var browseStore by mutableStateOf<DesktopGalleryBrowseHistory?>(null)
    var galleryClearStage by mutableStateOf(com.venera.desktop.gallery.ui.DesktopFavoritesClearStage.IDLE)
    var galleryClearAtArm by mutableIntStateOf(0)
    var galleryClearNote by mutableStateOf<String?>(null)
}

/** 多选账的键：与选卡回调同一条回退（uid 空时取站方原模型的身份）。 */
internal fun selectionIdentity(card: DesktopWallCardModel): String =
    card.uid.ifBlank { card.sourcePost?.uid.orEmpty() }

/** 壳层详情、评论与阅读器辅助状态容器，收拢顶层 Composable 捕获槽位。 */
internal class DetailAndReaderUiState {
    var commentsView by mutableStateOf(desktopCommentsInitialView())
    var detailInfo by mutableStateOf<com.venera.engineprobe.ComicInfo?>(null)
    var detailNote by mutableStateOf<String?>(null)
    var detailFavorited by mutableStateOf<Boolean?>(null)
    var detailFavoriteBusy by mutableStateOf(false)
    var favoriteTick by mutableIntStateOf(0)
    var detailHistoryOutcome by mutableStateOf<Result<com.venera.compose.data.db.HistoryRecord?>?>(null)
    var showBulkDownload by mutableStateOf(false)
    var readerNote by mutableStateOf<String?>(null)
    var readerPage by mutableIntStateOf(0)
}

/** 壳层 Pixivision、标签词典与联想辅助状态容器，收拢顶层 Composable 捕获槽位。 */
internal class GalleryAuxUiState {
    var pixivisionArticles by mutableStateOf(emptyList<com.venera.desktop.gallery.data.DesktopPixivisionArticle>())
    var pixivisionArticleOpen by mutableStateOf<com.venera.desktop.gallery.data.DesktopPixivisionArticle?>(null)
    var pixivisionArticleBody by mutableStateOf<com.venera.desktop.gallery.data.DesktopPixivisionArticleBody?>(null)
    var pixivisionArticleLoading by mutableStateOf(false)
    var tagDimensionGroups by mutableStateOf(emptyList<com.venera.desktop.gallery.ui.DesktopTagDimension>())
    var tagDictReady by mutableStateOf(false)
    var gallerySuggestTerm by mutableStateOf("")
    var gallerySuggestLine by mutableStateOf<String?>(null)
    var gallerySuggestLoading by mutableStateOf(false)
}

internal data class PixivPreviewLeg(
    val cards: List<com.venera.desktop.gallery.ui.DesktopWallCardModel>,
    val avatars: Map<String, String>,
    val titles: Map<String, String>,
)
