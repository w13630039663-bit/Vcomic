package com.venera.desktop.search

import com.venera.compose.feature.SearchPagination
import com.venera.desktop.platform.desktopFailureNoteOfText
import com.venera.desktop.settings.DesktopI18n
import com.venera.engineprobe.EngineSession

/**
 * 漫画搜索那一屏的**翻页与成因**判据。
 *
 * ## 这一批改的两件事
 *
 * 1. **翻页**：`EngineSession.search(keyword, page)` 第二枚形参早就在那里（默认 1），而桌面从前
 *    只发第一页 —— 40 条以后用户看不见也够不着。对岸那一整块（`SearchViewModel.loadMore` +
 *    `SearchScreen.kt:1151-1207`）今天接上，判据**逐字转交**共享那颗 [SearchPagination]。
 * 2. **成因**：源站的 `Page.error`（信封里那句 `__err`）从前被装配点 `getOrDefault(emptyList())`
 *    整笔咽掉 ⇒「该源没有 search」「站方回错误信封」都念成「这个词没搜到」。那是把**我们的失败**
 *    说成**站方的空结果**，本仓记过一整族（画廊补全那三句话：没答上／没有／没问，见
 *    `gallery/ui/DesktopSearchSuggestion.kt` 头部）。
 *
 * ## 为什么页码住在壳里而不在这一屏
 *
 * 那一屏不查引擎也不落库（`DesktopUiIsolationTest` ② 那条"UI 只吃形参"）。壳拿着
 * "这一轮第几页"，屏只交出"该取下一页了"那一下。
 */

/**
 * ⚠️ 这一颗是**公开**的：它是壳那枚 `searchComics` 形参的返回类型，而壳是公开 composable
 * （`internal` 的类型进不了公开签名，Kotlin 直接拒）。同形先例：`sourcemanage/DesktopSourceRowUi`。
 * 文件里其余那些仍 `internal`。
 */
data class DesktopComicSearchOutcome(
    val cards: List<EngineSession.ComicCard>,
    /** 源自己声明的总页数；`null` = 这一颗源不声明（游标型源，33 颗里 7 颗如此）。 */
    val maxPage: Int?,
    /** 源站在信封里给的那句话；非空 = **它答了而答的是失败**，不许折成"没有结果"。 */
    val sourceError: String?,
    /** 游标型续页令牌（`loadNext` 族回的 `next`；null = 到底/不适用）。装配点记着它，翻页那趟原样递回。 */
    val next: String? = null,
)

/**
 * 新一页里**没见过**的那些（对岸 `SearchViewModel.kt:443` 同一判据：按号排重）。
 *
 * 顺序不许反：对岸是「先排重、再过屏蔽规则」，反过来的话"这一页被规则剔空"会被当成
 * "这一页没有新条目"，进而误判成到底 —— 而源其实还有下一页。桌面这一屏的屏蔽是渲染层判的
 * （`maskedOf`），所以这里只有排重那一步，而那一步必须在算"还不还翻得动"之前。
 */
internal fun desktopComicSearchUnseen(
    current: List<EngineSession.ComicCard>,
    fresh: List<EngineSession.ComicCard>,
): List<EngineSession.ComicCard> {
    val seen = current.mapTo(HashSet()) { it.id }
    return fresh.filterNot { it.id in seen }
}

/**
 * 「还有下一页吗」—— 整颗转交对岸那颗 [SearchPagination]，桌面一处都不重述。
 *
 * 三条判据（零新条目算到底 / 声明的页数只用来提前收手 / 游标型只靠第一条）住在那颗件里，
 * 这里包一层只为让"桌面吃的是同一颗"成为一条能扫的接线（判据 ④ 数声明处）。
 */
internal fun desktopComicSearchCanLoadMore(
    declaredMaxPage: Int?,
    pageJustFetched: Int,
    unseenItemsOnPage: Int,
): Boolean = SearchPagination.canLoadMore(declaredMaxPage, pageJustFetched, unseenItemsOnPage)

/**
 * 页尾那**四档**各是什么。互斥且按证据强度排（次序逐字照对岸那四段 `if`）。
 *
 * ⚠️ 「到底」那一档在**有成因**时不许出现：那不是"没有了"，是"失败了"（对岸 `:1191` 那句注释
 * 原话）。把失败念成到底，用户就再也不会去点那一枚本来能重试的把手。
 */
internal enum class DesktopComicSearchMore {
    /** 什么都不摆（还没搜过、或结果本来就是空的 —— 那一行已经由屏顶那句说过）。 */
    None,

    /** 「正在加载更多…」 */
    Loading,

    /** 「加载更多」那枚把手 */
    Button,

    /** 「已经没有了」（对岸逐字） */
    End,

    /** 翻页失败：成因上屏 + 一枚「再搜一次」 */
    Failed,
}

/** 页尾那一格该摆哪一档（四档的判据全在这里，屏只按档位摆东西，不自己拼条件）。 */
internal fun desktopComicSearchMoreState(
    loadingMore: Boolean,
    canLoadMore: Boolean,
    hasSearched: Boolean,
    resultsEmpty: Boolean,
    reason: String?,
): DesktopComicSearchMore = when {
    loadingMore -> DesktopComicSearchMore.Loading
    canLoadMore -> DesktopComicSearchMore.Button
    reason != null && !resultsEmpty -> DesktopComicSearchMore.Failed
    // 到底：发过车、有结果、没成因、翻不动了。少任何一条都不念那一句。
    !canLoadMore && !loadingMore && hasSearched && reason == null && !resultsEmpty -> DesktopComicSearchMore.End
    else -> DesktopComicSearchMore.None
}

/** 页尾那两句成品文案（「已经没有了」逐字照对岸 `SearchScreen.kt:1198`）。 */
internal const val DESKTOP_COMIC_SEARCH_END_LABEL: String = "已经没有了"
internal const val DESKTOP_COMIC_SEARCH_MORE_LABEL: String = "加载更多"
internal const val DESKTOP_COMIC_SEARCH_LOADING_LABEL: String = "正在加载更多…"
internal const val DESKTOP_COMIC_SEARCH_RETRY_LABEL: String = "再搜一次"

/**
 * 页尾**失败那一档**念的那一句成品（成因在这里，而**不**在屏顶那一行）。
 *
 * 为什么成因放这一句而不是放顶部那枚 `note`：翻到第二页而失败时，屏上**已经有结果**，
 * 顶部那一行留给"这一趟整条塌了"那一档（两档各说各的失败，同屏念两句会让人以为坏了两次）。
 * 而对岸那一句是 `message = reason` 原话（`SearchScreen.kt:1182`），桌面**多带**"翻页失败"
 * 那四个字：那一枚「再搜一次」不写清楚是翻页再试还是从头再搜，用户会以为前头那些白看了。
 *
 * 成因缺席时（站方给了 `__err` 而串是空的）仍要念得出这一档，不许退成空句。
 */
internal fun desktopComicSearchFailedFooter(reason: String?): String =
    if (reason.isNullOrBlank()) {
        DesktopI18n.tr("这一页没翻过来，可以再试一次。", "Failed to load this page. Please try again.")
    } else {
        // 成因可能是异常原文（TLS 握手被切断那种）：上屏走归档短句，原文只进日志。
        DesktopI18n.tr(
            "这一页没翻过来：${desktopFailureNoteOfText(reason)}",
            "Failed to load this page: ${desktopFailureNoteOfText(reason)}",
        )
    }

/**
 * 结果那一格**没有卡可摆时**念的那一句（成品）。`null` = 那一格由屏自己念「正在加载…」。
 *
 * 四档顺序逐字照对岸 `SearchScreen.kt:1091-1112`（成因 → 搜完而 0 条 → 还没发车/空输入），
 * 差别只有一处、是明写的：
 *  - 「没搜到」那一档要多一枚 `searched`：从前桌面只看"输入框里有字"就念那一句，
 *    于是**敲了字而一次都没发车**也被说成"这个词没搜到"。对岸那三档里就有 `ui.hasSearched`。
 *
 * ⚠️ 有成因时中间那一格念的是**成因原话**而不是"没搜到"：这是这一批真正修的那格 ——
 * 从前站方那句 `__err: "该源没有 search"` 被装配点整笔咽掉，屏上只剩
 * 「「词」在「源」里没搜到」，那是把**我们的失败**说成**站方的空结果** ——
 * 用户于是反复换词重搜，而那一颗源压根不支持搜索。
 *
 * 与页尾那一格**互斥**（同一句成因不会同时出现在两处）：有结果时那一档走
 * [desktopComicSearchFailedFooter]，而这一颗对"有结果"直接交回 `null`；
 * 对岸也正是这个形状 —— `error` 只在 `ss-error`（空结果）与 `ss-more-error`（有结果）两处之一。
 */
internal fun desktopComicSearchEmptyNote(
    query: String,
    sourceKey: String,
    searched: Boolean,
    searching: Boolean,
    resultCount: Int,
    reason: String?,
): String? = when {
    resultCount > 0 || searching -> null
    // 成因要走归档：站方业务句原样保留，异常原文（握手被切断那种）念成档位短句。
    reason != null -> desktopFailureNoteOfText(reason)
    !searched || query.isBlank() -> DesktopI18n.tr("输入书名或作者，回车开始搜。", "Enter title or author and press Enter to search.")
    else -> DesktopI18n.tr("「$query」在「$sourceKey」里没搜到。", "No results for '$query' in '$sourceKey'.")
}

/**
 * 那一排的**落盘成因**那一行（屏顶那枚 `note`）。
 *
 * 「这一趟搜的成因」与「那一排读写不成的成因」在两格里各说一句：前者说"这一轮没成"，
 * 后者说"最近搜索这一排暂时摆不出/记不住"。它们是**不同的一件事**（塌的层不同），
 * 所以这里只在搜索那一路没成因时把落盘那句递上屏 —— 反过来说，只要顶栏念得出这一句，
 * 那一排就必然已经**整格不摆**（`desktopComicSearchHistoryChips` 只吃读回来的表）。
 */
internal fun desktopComicSearchHistoryNoteText(failure: String?): String? =
    if (failure == null) null else DesktopI18n.tr("历史记录不可用", "Search history unavailable")

/**
 * 取证那一句（**stdout 读数**，与 `D_收藏` / `D_取数` 同族：屏上那几格只说"光标在哪"，
 * 而"第几页、源声明几页、还翻得动吗"才是包内取证要对的账）。
 *
 * 为什么由纯件造而不是壳里拼一句：拼串的形状会漂，而 `D_搜索` 那一句是批内唯一能证明
 * "翻页那条链真在跑"的读数（无头 UI 只验到装配级）。
 * 「还没发车」那一档必须**不打**这一行：不打与"页=0"在读数上不是一件事（本仓那一族假话）。
 */
internal fun desktopComicSearchReading(
    query: String,
    page: Int,
    declaredMaxPage: Int?,
    resultCount: Int,
    canLoadMore: Boolean,
    historyCount: Int,
    reason: String?,
): String = buildString {
    append("D_搜索 词=").append(query.ifBlank { "空" })
    append(" 页=").append(page)
    append(" 源声明=").append(declaredMaxPage?.toString() ?: "不给")
    append(" 结果=").append(resultCount)
    append(" 可翻=").append(if (canLoadMore) "是" else "否")
    append(" 历史=").append(historyCount)
    append(" 成因=").append(reason ?: "无")
}
