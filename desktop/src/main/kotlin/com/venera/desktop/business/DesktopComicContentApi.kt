package com.venera.desktop.business

import com.venera.compose.data.api.ComicContentApi
import com.venera.compose.source.model.CategoryComicsOption
import com.venera.compose.source.model.CategoryComicsResult
import com.venera.compose.source.model.ChapterPages
import com.venera.compose.source.model.Comic
import com.venera.compose.source.model.ComicDetails
import com.venera.compose.source.model.ExplorePagePart
import com.venera.compose.source.model.ResolvedImageConfig
import com.venera.compose.source.model.ResolvedThumbnailConfig
import com.venera.compose.source.model.SearchPage
import com.venera.compose.source.model.ThumbnailPage
import com.venera.compose.source.explore.SourceExploration
import com.venera.desktop.data.desktopEngineCall
import com.venera.engineprobe.EngineSession

/**
 * 详情页取数那一族端口（`ComicContentApi`）的**桌面实现**（D′-1）。
 *
 * 只有三颗真问引擎、且在详情链上：
 *  - [getComicDetails] = `engine.load(key)` + `engine.details(id)`（与壳里那条现链同一形状），
 *    引擎交回 `ComicInfo` 后经 [toComicDetails] 换算成共享 `ComicDetails`。
 *  - [getChapterPages] = `engine.load(key)` + `engine.pages(...)`，页键经 [engineChapterPages] 换算。
 *    占位章（整本一话）的重建：`parseComicLoadInfo` 交的是 `Episode("", comicId, placeholder=true)`，
 *    故 `chapterId == comicId` 即占位 —— 照那一格还回 `Episode`，`pages` 才对第三实参交 `null`。
 * - [loadThumbnails] = `engine.load(key)` + `engine.thumbnails(id, next)`（接真路，
 *    成因见方法体）。
 *
 * 其余成员**不在详情链上**（搜索 / 探索 / 分类 / 取图配置），按方案 §3.2 决策 3
 * 如实降级：failure/空表，各点名"详情不吃这族"。
 *
 * ⚠️ 每一跳问引擎都必须包在 `desktopEngineCall` 里（引擎零同步，实测那种 `取不到 source key`），
 * 这一颗新增的跳要同步登记进 `DesktopEngineCallCoverageTest` 的总数与分布（方案硬约束 4）。
 */
internal class DesktopComicContentApi(
    private val engine: EngineSession?,
) : ComicContentApi {

    override suspend fun getComicDetails(sourceKey: String, comicId: String): Result<ComicDetails> {
        val e = engine ?: return Result.failure(IllegalStateException("引擎没起来"))
        return desktopEngineCall(e, sourceKey = sourceKey) {
            e.load(sourceKey)
            e.details(comicId)
        }.map { it.toComicDetails(comicId, sourceKey) }
    }

    override suspend fun getChapterPages(
        sourceKey: String,
        comicId: String,
        chapterId: String,
    ): Result<ChapterPages> {
        val e = engine ?: return Result.failure(IllegalStateException("引擎没起来"))
        val placeholder = chapterId.isBlank()
        val title = ""
        return desktopEngineCall(e, sourceKey = sourceKey) {
            e.load(sourceKey)
            e.pages(comicId, EngineSession.Episode(title, chapterId, placeholder))
        }.map { keys -> engineChapterPages(comicId, chapterId, title, keys) }
    }

    /**
     * 缩略图分页：桌面引擎**没有** `comic.loadThumbnails` / `onThumbnailLoad` 调用口，
     * 逐行核对对岸 `JsComicSource.loadThumbnails`：未声明该接口的源恒回 `success(空页)`（failure 只在
     * 「声明了却调用失败」才发生）。`ComicDetailCore.loadThumbnails` 在 success-empty 与 failure 两条腿
     * 都退到「第一话页表」兜底，所以桌面如实回 `success(ThumbnailPage())`：屏上走兜底、零错误噪音。
     */
    /**
     * 缩略图分页（接真路）：`engine.load(key)` + `engine.thumbnails(id, next)`。
     * 从前桌面没有这一跳、恒回空页 ⇒ 详情预览走「第一话页表」兜底，而页图 URL 直接喂 Coil
     * 对里站一族就是 403 空格。引擎侧已把 URL 过一遍 `onThumbnailLoad`（s.exhentai.org → ehgt.org）。
     * 未声明 `loadThumbnails` 的源引擎交回空页，兜底语义不变。
     */
    override suspend fun loadThumbnails(sourceKey: String, comicId: String, next: String?): Result<ThumbnailPage> {
        val e = engine ?: return Result.failure(IllegalStateException("引擎没起来"))
        return desktopEngineCall(e, sourceKey = sourceKey) {
            e.load(sourceKey)
            e.thumbnails(comicId, next)
        }.fold(
            onSuccess = { page ->
                // EH 预览空白要排查。静默兜底把成因吞进了 Core，
                // 这里把「这一腿到底回来的是什么」念成读数 —— 数量与首张 host 就够定位
                // （0 张 = 源腿空/抛了；有张数 = 腿通了，问题在取图那一层）。
                val first = page.thumbnails.firstOrNull()
                val host = runCatching { java.net.URI(first).host }.getOrNull() ?: "-"
                val distinct = page.thumbnails.distinct()
                println(
                    "D_缩略图 源=$sourceKey 张=${page.thumbnails.size} 重=${page.thumbnails.size - distinct.size} " +
                        "有下一页=${page.next != null} 首host=$host",
                )
                Result.success(ThumbnailPage(page.thumbnails, page.next))
            },
            onFailure = { e ->
                println("D_缩略图 源=$sourceKey 失败 ${e::class.java.simpleName}: ${e.message?.take(120)}")
                Result.failure(e)
            },
        )
    }

    /**
     * 缩略图配置：走引擎把源 `onThumbnailLoad` 过一遍（终审——从前这里恒回空表，
     * EH 的 `s.exhentai.org → ehgt.org` 换域与 referer 头从来没生效过，详情页预览整排空白；
     * 安卓端走 JsComicSource 同一颗钩子所以没问题）。头由调用方（`ensureThumbnailConfigs`）
     * 发布进 `ImageHeaderPolicy`，取字节那层统一贴。
     */
    override suspend fun resolveThumbnailConfigs(
        sourceKey: String,
        urls: List<String>,
    ): Map<String, ResolvedThumbnailConfig> {
        val e = engine ?: return emptyMap()
        // ⚠️ load 必须在这一把锁里：不先 load，引擎里根本没有这颗源，
        // `ComicSource.sources[key]` 恒 undefined ⇒ 钩子整批落空（EH 预览仍空白的成因）。
        val resolved = desktopEngineCall(e) {
            e.load(sourceKey)
            e.thumbnailRewrites(urls)
        }
        resolved.fold(
            onSuccess = { map ->
                // 换域那一档念一行：EH 的 s.exhentai.org → ehgt.org 若没生效，取图层拿到的就是
                // 要 cookie 的里站域 —— 这是"封面能出、预览整排空白"最可能的分岔点。
                val hostA = runCatching { java.net.URI(urls.first()).host }.getOrNull() ?: "-"
                val hostB = map.values.firstOrNull()?.url?.let { runCatching { java.net.URI(it).host }.getOrNull() } ?: "-"
                println("D_缩略配置 源=$sourceKey n=${urls.size} 首host=$hostA -> 换后=$hostB")
            },
            onFailure = { e ->
                println("D_缩略配置 源=$sourceKey 失败 ${e::class.java.simpleName}: ${e.message?.take(120)}")
            },
        )
        return resolved.getOrDefault(emptyMap())
            .mapValues { (_, r) -> ResolvedThumbnailConfig(url = r.url, headers = r.headers) }
    }

    // ── 以下全部不在详情链上：如实降级，各点名「详情页不吃这一族」 ──

    override suspend fun search(
        sourceKey: String,
        keyword: String,
        page: Int,
        options: List<String?>?,
    ): Result<SearchPage> =
        Result.failure(UnsupportedOperationException("详情页不吃搜索这一族（桌面搜索走壳的 searchComics 装配位）"))

    override suspend fun resolveImageLoadingConfig(
        sourceKey: String,
        comicId: String,
        epId: String,
        imageKey: String,
        nl: String?,
        forceRefresh: Boolean,
    ): Result<ResolvedImageConfig> =
        Result.failure(
            UnsupportedOperationException(
                "详情页不吃取图配置这一族（桌面取图走 Coil fetcher 逐页问 EngineSession.pageImage）",
            ),
        )

    override suspend fun getSourceExplorations(): List<SourceExploration> = emptyList()

    override suspend fun loadExplorePage(
        sourceKey: String,
        pageIndex: Int,
        page: Int,
    ): Result<List<ExplorePagePart>> =
        Result.failure(UnsupportedOperationException("详情页不吃探索这一族"))

    override suspend fun loadCategoryComics(
        sourceKey: String,
        category: String,
        param: String?,
        options: List<String>,
        page: Int,
    ): Result<CategoryComicsResult> =
        Result.failure(UnsupportedOperationException("详情页不吃分类这一族"))

    override suspend fun getCategoryComicsOptions(
        sourceKey: String,
        category: String,
        param: String?,
    ): Result<List<CategoryComicsOption>> =
        Result.failure(UnsupportedOperationException("详情页不吃分类这一族"))

    override suspend fun loadCategoryRanking(
        sourceKey: String,
        option: String,
        page: Int,
    ): Result<List<Comic>> =
        Result.failure(UnsupportedOperationException("详情页不吃分类排行这一族"))

    override suspend fun tagSuggestionKeyword(sourceKey: String, namespace: String, raw: String): String? = null
}
