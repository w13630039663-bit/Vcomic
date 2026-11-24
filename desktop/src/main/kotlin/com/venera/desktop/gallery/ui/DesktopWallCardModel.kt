package com.venera.desktop.gallery.ui

import com.venera.desktop.app.DesktopUxPlatformServices
import com.venera.desktop.app.VeneraDesktopLargeScreen
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryPreviewQuality
import kotlin.math.max

/**
 * 一张墙上卡片的**渲染输入** —— 只有画得出来需要的字段，没有业务判断。
 *
 * ## 为什么要这一颗而不是直接把 `GalleryPost` 递进 UI
 *
 * `GalleryPost` 有 20+ 个字段，其中大半是取数语义（`rating` / `score` / `favCount` /
 * `largeWidth` / `pageUrl` …）。卡片只吃四样：图、标题/作者、纵横比、要不要打码。
 * 直接递 `GalleryPost` 会让 UI 与取数模型耦合 —— 那是两端共享面最不想要的那种耦合
 * （对岸 `GalleryCardCaption` 就因为同样的理由接的是算好的标题与作者，不是 `GalleryPost`）。
 *
 * ## 这颗模型第一次有了「点开后要摆什么」的字段
 *
 * 从前它只有墙上那一格画得出来的四样。这一批接「点一张图看大图」，而那条渲染路（活壳
 * `VeneraDesktopLargeScreen` 那颗**唯一**的渲染路）从前根本没有看图屏 —— 整套 viewer 只长在
 * 已拆掉的 Fluent 死线 `VeneraDesktopApp` 上，安卓那颗 `GalleryPostScreen` 是 ③ 档（吃 Bitmap /
 * Activity / WebView，桌面编不过）⇒ 只能桌面自搭。自搭那一屏要能只吃形参渲（`DesktopUiIsolationTest`
 * ② 那条），于是这里补上「打开一张图」要的**渲染输入**：身份、大图两档、标签、画师。
 *
 * ⚠️ 这些字段**全带默认值**，且只由 [desktopWallCardOf] 从 `GalleryPost` 搬 —— 不新造判据。
 *   默认值也让那两把无头 UI 尺里只传四枚具名参的 `cards(n)` 台架照旧编得过（它们不测看图那一格）。
 *   身份用共享面那颗 `GalleryPost.uid`（`站点routeKey:id`），与去重 / 收藏 / 历史同一口径，不自造第二套键。
 *
 * ## 为什么它不是纯 UI 件
 *
 * 它同时承载**判据**：图片 URL 该取哪一档（`preview` / `fast` / `large`）、
 * 打码该不该打、纵横比怎么算。这三件事有单测钉着（`DesktopWallCardProjectionTest`），
 * 所以它住在 `gallery/ui/` 而**不含 `@Composable`** —— 能被单测直接取值。
 */
data class DesktopWallCardModel(
    /** 卡片标题：作者名优先，没有作者时用站点名 + 编号。 */
    val title: String,
    /** 上屏的图片 URL（已按画质档挑过）。 */
    val url: String,
    /** 卡片纵横比（宽/高），摆瀑布流用。 */
    val aspect: Float,
    /** 要不要打码。 */
    val masked: Boolean,
    /**
     * 这一张的身份串（`站点routeKey:id`，照 `GalleryPost.uid`）。**空串 = 这张卡点不开**
     * （无头尺里的夹具卡没有真身份 ⇒ 不接点击，而不是摆一枚按了进空看图的死按钮）。
     */
    val uid: String = "",
    /** 大图页开门档（先铺 `fastUrl` 再等 `largeUrl`）；缺档留空串，看图屏逐级回退。 */
    val fastUrl: String = "",
    /** 大图页中档。 */
    val largeUrl: String = "",
    /** 原图档（pixiv 原档 / booru file_url）。画质批：大图缩放档吃它——
     *  缺档留空串，看图屏逐级回退（原→large→fast→卡图）。 */
    val fileUrl: String = "",
    /** 标签空格串（看图屏下方那一排 chip 吃它 —— §132 起认得出所属站那一片可点、
     *  经 `LocalDesktopSearchHandoff` 交回「站点 + 那一枚标签」；认不出的那一片仍只读，
     *  判据在 `desktopViewerTagSite`）。 */
    val tags: String = "",
    /** 画师原值（看图屏顶栏念它；与 [title] 同源，这里存的是原始 author，不是"作者否则站点+编号"）。 */
    val author: String = "",
    /**
     * 生成这张卡的站方原模型。看图屏的收藏 / 存图动作要回交共享业务端口，
     * 而那两个端口吃的是 [GalleryPost] 而不是 UI 投影。
     *
     * 默认 `null`：无头 UI 尺里的夹具卡仍可只构造渲染字段；真墙上的卡由
     * [desktopWallCardOf] 把原模型一并带上。UI 不靠它做取数判断，只把它原样交给
     * 装配点递进来的动作回调。
     */
    val sourcePost: GalleryPost? = null,
    /**
     * 等宽网格（Grid）行打包指定的跨度（1 = 单格 1:1 方图，2 = 双格横图；0 = 未指定，交由 aspect >= 1.15 判断）。
     */
    val gridSpan: Int = 0,
) {
    /**
     * 这张卡点不点得开：**有身份、且这张没被打码**，两点齐了才点得开。判据住这里，而不在各调用点重拼。
     *
     * ⚠️ 为什么把 `masked` 也算进"点不开"：桌面这条线只有 `DesktopUxPlatformServices.looksAdult`
     *   那一套**比安卓窄的替代判据**（类注释自己写着"宁可漏判，也不假装有那份词表"）。既然这张已被判成要遮，
     *   再给它接一枚"点开看大图"，等于在缺词表的那一端**主动放大成人图** —— 那是安全评审 A 条最忌的方向。
     *   所以打码的卡照旧在墙上遮着摆着，只是不给点开；这一格与"看大图"这一格各归各，不互相冒充。
     *   空 uid（无头尺里的夹具卡）同理不可点 ⇒ 不会摆出一枚按了进空看图的死按钮。
     */
    val openable: Boolean get() = uid.isNotBlank() && !masked
}

/**
 * `GalleryPost` → 卡片。
 *
 * @param quality 画质档：`PREVIEW` 取 `previewUrl`、`LARGE` 取 `largeUrl`；
 *   两档都缺时逐级回退，**不返回空串** —— 空串会让 `VeneraCover` 走"加载失败"那条路，
 *   而"这一站没给这一档"与"图挂了"是两件事，不该长得一样。
 * @param masked 内容守卫判过的结果。判据在 `DesktopGalleryContentGuard`，这里只搬。
 */
internal fun desktopWallCardOf(
    post: GalleryPost,
    quality: GalleryPreviewQuality = GalleryPreviewQuality.PREVIEW,
    masked: Boolean = false,
): DesktopWallCardModel {
    // 取图那一档：对齐共享面 GallerySettingsModel 判据，避免视频直连 mp4 或 pixiv/booru 档位缺失导致黑块。
    val url = when {
        post.isVideo -> post.videoPosterUrl.ifBlank { post.previewUrl }
        post.site == com.venera.compose.gallery.data.GallerySite.PIXIV -> {
            post.previewUrl.ifBlank { post.largeUrl }.ifBlank { post.fileUrl }
        }
        quality == GalleryPreviewQuality.LARGE -> {
            post.largeUrl.takeIf { it.isNotBlank() } ?: post.previewUrl
        }
        else -> post.previewUrl
    }.ifBlank { post.fastUrl }.ifBlank { post.largeUrl }.ifBlank { post.previewUrl }.ifBlank { post.fileUrl }

    // 纵横比：优先用**这张图自己的**尺寸（预览档优先，因为上屏的就是它）；
    // 没有尺寸时退到 3:4 —— 那是一张竖版同人图最常见的比例，
    // 退到 1:1 会让瀑布流整面变方，"退档"要退得像一点。
    val (w, h) = when {
        post.previewWidth > 0 && post.previewHeight > 0 -> post.previewWidth to post.previewHeight
        post.width > 0 && post.height > 0 -> post.width to post.height
        post.largeWidth > 0 && post.largeHeight > 0 -> post.largeWidth to post.largeHeight
        else -> 3 to 4
    }
    val aspect = (w.toFloat() / max(h, 1)).coerceIn(0.5f, 2.0f)

    val title = post.author.ifBlank { siteLabelOf(post.site) } + " #" + post.id

    return DesktopWallCardModel(
        title = title,
        url = url,
        aspect = aspect,
        masked = masked,
        // 把「打开这一张」要的渲染输入一并搬过来。只搬，不重判 ——
        // 大图取哪一档、要不要遮，仍由上面 `url` 与 `masked` 那两处既有判据说话。
        uid = post.uid,
        fastUrl = post.fastUrl,
        fileUrl = post.fileUrl,
        largeUrl = post.largeUrl,
        tags = post.tags,
        author = post.author,
        sourcePost = post,
    )
}

/** 站点名（卡片没有作者时用它兜底）。 */
internal fun siteLabelOf(site: GallerySite): String = when (site) {
    GallerySite.YANDERE -> "yande.re"
    GallerySite.GELBOORU -> "gelbooru"
    GallerySite.SAFEBOORU -> "safebooru"
    GallerySite.KONACHAN -> "konachan"
    GallerySite.KONACHAN_R18 -> "konachan"
    GallerySite.TBIB -> "tbib"
    GallerySite.PIXIV -> "pixiv"
}

/**
 * 一批 `GalleryPost` → 一面墙的卡片。
 *
 * 单独一颗而不是在调用点 `map`：**去重**这件事必须做（三站混排时同 id 不同站是常态，
 * 而"同一张图摆两遍"是墙最显眼的错）。去重键与共享面 `GallerySearchMerge` 同一口径：
 * `站点:id`，不是裸 id —— 裸 id 会把不同站上恰好同号的图误并成一张。
 */
internal fun desktopWallCardsOf(
    posts: List<GalleryPost>,
    quality: GalleryPreviewQuality = GalleryPreviewQuality.PREVIEW,
): List<DesktopWallCardModel> {
    val seen = HashSet<String>()
    return posts.mapNotNull { post ->
        if (!seen.add("${post.site}:${post.id}")) {
            null
        } else {
            runCatching { desktopWallCardOf(post, quality) }.getOrNull()
        }
    }
}

/** 常见的非画师假名称（上传者、站点名等，不可当成画师呈现）。 */
internal val DESKTOP_FAKE_ARTIST_NAMES: Set<String> = setOf(
    "danbooru", "gelbooru", "yande.re", "safebooru", "konachan", "unknown", "未知", "未知画师", "admin", "uploader"
)

/**
 * 提取图库站卡片的真实画师标签（由标签返回，杜绝 danbooru 等上传来源/假名字）。
 */
internal fun desktopExtractRealArtistTag(post: GalleryPost?, rawAuthor: String = ""): String? {
    val cleanRaw = rawAuthor.trim()
    if (cleanRaw.isNotBlank() && cleanRaw.lowercase() !in DESKTOP_FAKE_ARTIST_NAMES) {
        return cleanRaw
    }
    val tags = post?.tagList.orEmpty()
    return tags.firstOrNull { t ->
        val low = t.lowercase().trim()
        low.isNotBlank() &&
            low !in DESKTOP_FAKE_ARTIST_NAMES &&
            !low.startsWith("rating:") &&
            !low.startsWith("score:") &&
            !low.startsWith("source:") &&
            !low.contains(" ")
    }?.takeIf { it.lowercase() !in DESKTOP_FAKE_ARTIST_NAMES }
}

