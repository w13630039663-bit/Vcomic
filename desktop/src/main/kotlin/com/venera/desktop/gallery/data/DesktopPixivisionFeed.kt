package com.venera.desktop.gallery.data

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * pixivision 的**一篇文章**（v3 「pixivision 最新文章」架的成品）。
 *
 * - [id] = 站方文章号（`/a/11839` 那一段，键用）；
 * - [category] = 站方分类徽标文案（`插画 / 特辑 / 漫画 …`），空串 = 这一卡没带；
 * - [coverUrl] = 卡封面（站方 `_thumbnail` 的 background-image，pximg CDN）；
 * - [url] = 文章完整地址（点卡 = 系统浏览器打开这一扇门，桌面上文章详情页后续批再接）。
 */
data class DesktopPixivisionArticle(
    val id: String,
    val title: String,
    val category: String,
    val coverUrl: String,
    val url: String,
)

/**
 * pixivision 最新文章的**取流 + 解析**（纯 JVM，零站方凭据）。
 *
 * ## 为什么是网页解析而不是 RSS
 *
 * 站方旧 RSS 路径（`/{lang}/rss/latest`）实测已被重定向回 HTML 首页 —— 端点死了。
 * 首页那二十张 `article-card-container` 是 WordPress 模板直出的规整结构，字段用正则切：
 * - 链接：`<a href="https://www.pixivision.net/{lang}/a/{id}"`；
 * - 封面：`<div class="_thumbnail" style="background-image:  url(…pximg…)">`；
 * - 分类：`<span class="arc__thumbnail-label _category-label …">{分类}</span>`；
 * - 标题：`<h2 class="arc__title"><a …>{标题}</a></h2>`。
 *
 * ## 走什么网络
 *
 * [client] 由调用方喂（壳里就是画廊那条 [com.venera.desktop.platform.DesktopHttpEngine] 的
 * OkHttp）—— pixivision 不进画廊取数预算体系（`GalleryLegGuard` 那十二秒是给三站墙的），
 * 但也不自建第二条线程池：给谁喂 client 谁就是它的流。整页 GET，15s 读超时兜底。
 *
 * 解析**零命中 = 抛**（与 `PixivAppParse` 同一口径）：站方改版式的表现是"这一节消失"，
 * 不是"屏上摆一排空卡"。
 */

/** 文章详情里的一格**作品单元**（站方 `am__work` 模板直出：大图 + 画师头像/名 + 作品链接）。 */
data class DesktopPixivisionWorkUnit(
    val imageUrl: String,
    val artistName: String,
    val artistAvatarUrl: String,
    val workUrl: String,
    /** 站方作品标题（`am__work__title` 锚文本），空 = 这一格没带。 */
    val workTitle: String = "",
    /** 站方配文说明（`am__work__text` / 段落），空 = 没带，不编。 */
    val caption: String = "",
    /**
     * 站方作品图宽高比（宽/高，来自 `<img width/height>`）。读不到 = 竖图默认 `2:3`
     * （官方文章页竖图占多数，**不猜横图**）。UI 据此按原始比例自撑高度。
     */
    val aspectRatio: Float = DEFAULT_PORTRAIT_RATIO,
) {
    companion object {
        /** 官方文章页作品图多数是竖图，比例缺失时按这一档兜底。 */
        const val DEFAULT_PORTRAIT_RATIO: Float = 2f / 3f
    }
}

/** 右栏一条（本月排行榜 1..5 / 推荐 5 条）：缩略图 + 标题 + 站方链接。 */
data class DesktopPixivisionRelatedItem(
    val title: String,
    val coverUrl: String,
    val url: String,
)

/** 文章详情的**整颗正文**（二级页的成品；作品单元按页序排列）。 */
data class DesktopPixivisionArticleBody(
    val title: String,
    val lead: String,
    val works: List<DesktopPixivisionWorkUnit>,
    /** 站方发布日期原文（如 `2026.11.05`），空 = 没带。 */
    val date: String = "",
    /** 站方粉色话题标签组（原文，含 `#`），空表 = 没带。 */
    val topics: List<String> = emptyList(),
    /** 右栏「本月排行榜」1..5。 */
    val ranking: List<DesktopPixivisionRelatedItem> = emptyList(),
    /** 右栏「推荐」5 条。 */
    val related: List<DesktopPixivisionRelatedItem> = emptyList(),
)

object DesktopPixivisionFeed {

    private val fallbackClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .readTimeout(15, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(
                com.venera.engineprobe.DesktopCloudflareCenter.ProxyFastFailInterceptor(
                    hasManualProxy = false,
                    isExplicitDirect = com.venera.desktop.platform.DesktopHttpEngine.isExplicitDirect(),
                ),
            )
            .build()
    }

    private val client: OkHttpClient
        get() = com.venera.desktop.platform.DesktopHttpEngine.latestSharedClient() ?: fallbackClient

    /** 文章卡的切块（站点模板直出，`_article-card` 开标签到闭标签）。 */
    private val cardRegex = Regex(
        pattern = """<article class="_article-card[^"]*">.*?</article>""",
        options = setOf(RegexOption.DOT_MATCHES_ALL),
    )
    private val linkRegex = Regex("""href="(https://www\.pixivision\.net/[^"]*/a/(\d+))"""")
    private val coverRegex = Regex("""_thumbnail"\s+style="background-image:\s*url\(([^)]+)\)""")
    private val categoryRegex = Regex("""_category-label[^"]*">([^<]+)<""")
    private val titleRegex = Regex("""arc__title"><a[^>]*>([^<]+)</a>""")

    /**
     * 抓 [lang] 首页并解析文章卡；[limit] 张封顶。
     *
     * 默认改跟**日文原站** `https://www.pixivision.net/ja/`（最新、文章最全），
     * 中文站 `/zh/` 更新滞后。linkRegex 已允许任意 lang 段，正文页语言随卡片 URL 走，不改解析。
     */
    suspend fun fetch(lang: String = "ja", limit: Int = 10): Result<List<DesktopPixivisionArticle>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = client.newCall(
                    Request.Builder()
                        .url("https://www.pixivision.net/$lang/")
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                        .build(),
                ).execute().use { response ->
                    check(response.isSuccessful) { "pixivision 首页 HTTP ${response.code}" }
                    response.body?.string().orEmpty()
                }
                parse(body, limit).also { articles ->
                    check(articles.isNotEmpty()) { "pixivision 首页改版：一张文章卡都没认出来" }
                }
            }
        }

    /** 解析纯件（判据可扫）：切块 → 逐卡提四格 → 按 id 去重保序。 */
    fun parse(body: String, limit: Int = 10): List<DesktopPixivisionArticle> {
        val seen = linkedSetOf<String>()
        val articles = mutableListOf<DesktopPixivisionArticle>()
        for (card in cardRegex.findAll(body)) {
            val block = card.value
            val linkMatch = linkRegex.find(block) ?: continue
            val url = linkMatch.groupValues[1]
            val id = linkMatch.groupValues[2]
            if (!seen.add(id)) continue
            val title = titleRegex.find(block)?.groupValues?.get(1)?.trim().orEmpty()
            if (title.isEmpty()) continue
            articles += DesktopPixivisionArticle(
                id = id,
                title = title,
                category = categoryRegex.find(block)?.groupValues?.get(1)?.trim().orEmpty(),
                coverUrl = coverRegex.find(block)?.groupValues?.get(1).orEmpty(),
                url = url,
            )
            if (articles.size >= limit) break
        }
        return articles
    }
    // ── 文章详情（批 B6）：同一颗 client，正文四格正则直切 ──
    private val titleH1Regex = Regex("""<h1 class="am__title"[^>]*>([^<]+)</h1>""")
    /** 作品大图 + 图上带的属性串（官方 `width`/`height` 给比例，用来按原比例自撑高度）。 */
    private val illustRegex = Regex("""<img src="([^"]+)" class="am__work__illust[^"]*"([^>]*)>""")
    private val widthAttrRegex = Regex("""\bwidth="(\d+)"""")
    private val heightAttrRegex = Regex("""\bheight="(\d+)"""")
    private val userNameRegex = Regex("""am__work__user-name">by <a[^>]*>([^<]+)</a>""")
    private val userIconRegex = Regex("""<img src="([^"]+)" class="am__work__uesr-icon""")
    private val workLinkRegex = Regex("""am__work__title"><a href="(https://www\.pixiv\.net/artworks/\d+)[^"]*"""")

    // ── 二级页重做：官方页面其余字段（零命中 = 空串/空表，如实不摆，不抛） ──
    private val dateRegex = Regex(
        """(?:class="[^"]*(?:am__date|article-date|am__header-date)[^"]*"[^>]*>\s*|<time[^>]*>)\s*((?:20\d{2})[.\-/]\d{1,2}[.\-/]\d{1,2})""",
    )
    private val topicRegex = Regex("""class="[^"]*(?:am__tag|tag-list|_tag)[^"]*"[^>]*>\s*(#[^<]+)<""")
    private val leadRegex = Regex(
        """class="[^"]*(?:am__lead|am__description|article-lead)[^"]*"[^>]*>\s*(?:<p[^>]*>)?\s*([^<]+)""",
    )
    private val workTitleRegex = Regex("""am__work__title"><a[^>]*>([^<]+)</a>""")
    private val captionRegex = Regex("""class="[^"]*(?:am__work__text|am__work__description)[^"]*"[^>]*>\s*(?:<p[^>]*>)?\s*([^<]+)""")
    private val relatedImgRegex = Regex("""<img[^>]+src="([^"]+)"[^>]*class="[^"]*(?:_thumbnail|related|ranking)[^"]*"""")
    private val relatedTitleRegex = Regex("""class="[^"]*(?:_title|related__title|ranking__title)[^"]*"[^>]*>\s*(?:<a[^>]*>)?\s*([^<]+)""")
    private val relatedLinkRegex = Regex("""href="(https://www\.pixivision\.net/[^"]*/a/\d+)"""")

    /** 抓文章页并解析正文（作品单元按页序排列；一张图都没认出来 = 抛 —— 站方改版不说谎）。 */
    suspend fun fetchArticle(url: String): Result<DesktopPixivisionArticleBody> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = client.newCall(
                    Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                        .build(),
                ).execute().use { response ->
                    check(response.isSuccessful) { "pixivision 文章 HTTP ${response.code}" }
                    response.body?.string().orEmpty()
                }
                // 作品图带上它自己那一段属性串：官方 width/height 直接给比例，
                // 读不到退竖图默认（官方文章页竖图占多数，不猜横图）。
                val illustMatches = illustRegex.findAll(body).toList()
                val images = illustMatches.map { it.groupValues[1] }
                val attrs = illustMatches.map { it.groupValues[2] }
                val names = userNameRegex.findAll(body).map { it.groupValues[1].trim() }.toList()
                val icons = userIconRegex.findAll(body).map { it.groupValues[1] }.toList()
                val links = workLinkRegex.findAll(body).map { it.groupValues[1] }.toList()
                val n = minOf(images.size, names.size, links.size)
                check(n > 0) { "pixivision 文章改版：一个作品单元都没认出来" }
                val titles = workTitleRegex.findAll(body).map { it.groupValues[1].trim() }.toList()
                val captions = captionRegex.findAll(body).map { it.groupValues[1].trim() }.toList()
                val works = (0 until n).map { i ->
                    val w = widthAttrRegex.find(attrs.getOrElse(i) { "" })?.groupValues?.get(1)?.toFloatOrNull()
                    val h = heightAttrRegex.find(attrs.getOrElse(i) { "" })?.groupValues?.get(1)?.toFloatOrNull()
                    val ratio = if (w != null && h != null && w > 0f && h > 0f) {
                        w / h
                    } else {
                        DesktopPixivisionWorkUnit.DEFAULT_PORTRAIT_RATIO
                    }
                    DesktopPixivisionWorkUnit(
                        imageUrl = images[i],
                        artistName = names.getOrElse(i) { "" },
                        artistAvatarUrl = icons.getOrElse(i) { "" },
                        workUrl = links[i],
                        workTitle = titles.getOrElse(i) { "" },
                        caption = captions.getOrElse(i) { "" },
                        aspectRatio = ratio,
                    )
                }
                // 右栏：站方「本月排行榜 / 推荐」列表。字段按页序现取，缺一项就退空表，
                // 不替站方编条目；缩略图只取官方 pximg CDN 的那一格。
                val relatedImages = relatedImgRegex.findAll(body).map { it.groupValues[1] }.toList()
                val relatedTitles = relatedTitleRegex.findAll(body).map { it.groupValues[1].trim() }.toList()
                val relatedLinks = relatedLinkRegex.findAll(body).map { it.groupValues[1] }.toList()
                val relatedItems = (0 until minOf(relatedImages.size, relatedTitles.size)).map { i ->
                    DesktopPixivisionRelatedItem(
                        title = relatedTitles[i],
                        coverUrl = relatedImages[i],
                        url = relatedLinks.getOrElse(i) { "" },
                    )
                }
                DesktopPixivisionArticleBody(
                    title = titleH1Regex.find(body)?.groupValues?.get(1)?.trim().orEmpty(),
                    lead = leadRegex.find(body)?.groupValues?.get(1)?.trim().orEmpty(),
                    works = works,
                    date = dateRegex.find(body)?.groupValues?.get(1)?.trim().orEmpty(),
                    topics = topicRegex.findAll(body)
                        .map { it.groupValues[1].trim() }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .toList(),
                    ranking = relatedItems.take(5),
                    related = relatedItems.drop(5).take(5),
                )
            }
        }
}
