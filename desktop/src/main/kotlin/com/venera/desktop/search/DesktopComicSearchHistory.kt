package com.venera.desktop.search

import com.venera.compose.data.platform.KeyValueStore
import com.venera.compose.gallery.data.GalleryPorts

/**
 * 漫画搜索那一屏的**查询历史**。
 *
 * 对岸 `SearchViewModel.kt:474-481`（读 / 追加 / 清空）与 `SearchScreen.kt:316-324`
 * （那一排把手）早就在跑，而桌面从前一笔都不存 —— 搜过什么、怎么再搜一遍，只能重新敲。
 *
 * 三样必须与对岸逐字相同：prefs 名、键名、分隔符。偏一样的后果不是崩，而是两端各存
 * 一份而都看起来正常工作（与画廊那份搜索历史同一条理由，见 `gallery/ui/DesktopSearchHistory.kt`
 * 头部）。那三样的**算式**在这里不重述：`DesktopComicSearchHistoryTest` 从对岸源码文本
 * 现取再比，对岸改了那把尺先红，而不是桌面悄悄存进一份没人读的档。
 *
 * 两枚这一屏特有的判断：
 * 1. 搜过就记，空结果也记 —— 对岸在发车主那一跳之前就 `appendHistory`
 *    （`SearchViewModel.kt:294`），所以判的是"这一趟真发出去了没"而不是"有没有货"。
 * 2. 空串不记（对岸同一判据 `query.isNotBlank()`）：空串进历史会让那一排摆出一枚看不见
 *    的把手，而点开它就是一次空搜索。
 *
 * 为什么这一颗没有「删掉其中一条」：对岸那一排的把手只有「清空」一枚（`SearchScreen.kt:318-322`
 * 交的就是 `history` / `onPick` / `onClear` 三样），摆一枚按了没反应的把手是本仓那一族假开关。
 *
 * 清空为什么是 remove 而不是写空串：照对岸 `clearHistory()` 那句 `prefs.remove`。
 * 写成空串今天读回来也是空表，但"用户清过"与"从来没存过"是两件事，将来读侧改默认档时
 * 只差一处。跟着对岸走，不自己定语义。
 */

/** 盘上那两份名字（与对岸同一串，由判据逐字回核）。 */
internal const val DESKTOP_COMIC_SEARCH_PREFS: String = "venera_search"
internal const val DESKTOP_COMIC_SEARCH_HISTORY_KEY: String = "history"

/**
 * 盘上那枚分隔符：单元分隔符（对岸 `joinToString` 用的那一枚，正文里不会出现）。
 * 判据拿它对拍，所以这里给的是同一个码位而不是另起一串。
 */
internal val DESKTOP_COMIC_SEARCH_HISTORY_SEPARATOR: Char get() = 0x1F.toChar()

/** 封顶条数与屏上摆几枚（逐字照对岸：`HISTORY_MAX = 20` / `history.take(5)`）。 */
internal const val DESKTOP_COMIC_SEARCH_HISTORY_MAX: Int = 20
internal const val DESKTOP_COMIC_SEARCH_HISTORY_SHOWN: Int = 5

/**
 * 那一排的两句话（逐字照对岸 `SearchScreen.kt:820`「最近搜索」与 `:826`「清空」）。
 *
 * 住在这里而不是屏里：判据要从**对岸源码文本**现取再比（`DesktopComicSearchHistoryTest` ③），
 * 对岸改词桌面会红，而不是桌面悄悄换成"搜索记录"这种没人认得的说话。
 */
internal const val DESKTOP_COMIC_SEARCH_HISTORY_TITLE: String = "最近搜索"
internal const val DESKTOP_COMIC_SEARCH_HISTORY_CLEAR: String = "清空"

/**
 * 那一份键值件。走 `ports.stores` 而不是桌面自己 new 一颗：与画廊那份搜索历史同一条路
 * （`gallery/ui/DesktopSearchHistory.kt` 的 `desktopHistoryStore`），
 * 一名一文件由 `DesktopStoreFactory` 保证。
 */
internal fun desktopComicSearchHistoryStore(ports: GalleryPorts): KeyValueStore =
    ports.stores.open(DESKTOP_COMIC_SEARCH_PREFS)

/** 读一次：没这个键就是空表（不是 null —— 调用方拿到 null 迟早写出一份到处漂的兜底）。 */
internal fun desktopComicSearchHistoryRead(store: KeyValueStore): List<String> =
    store.getString(DESKTOP_COMIC_SEARCH_HISTORY_KEY, null)
        ?.split(DESKTOP_COMIC_SEARCH_HISTORY_SEPARATOR)
        ?.filter { it.isNotBlank() }
        .orEmpty()

/**
 * 追加一条并交回新表（新的排最前、去重、封顶）。
 *
 * 算式逐字照对岸 `appendHistory`：`(listOf(query) + readHistory()).distinct().take(HISTORY_MAX)`。
 * 那一枚 `distinct()` 不是可有可无的：少了它，同一个词搜两次就在屏上摆两枚一模一样的把手。
 * ⚠️ 落盘由调用方做（[desktopComicSearchHistoryWrite]）—— 这里不碰盘，判据才能直接喂表跑。
 */
internal fun desktopComicSearchHistoryPush(current: List<String>, query: String): List<String> {
    val trimmed = query.trim()
    if (trimmed.isBlank()) return current
    return (listOf(trimmed) + current).distinct().take(DESKTOP_COMIC_SEARCH_HISTORY_MAX)
}

/** 写盘那一趟（与 [desktopComicSearchHistoryPush] 分开，是为了让判据能只算不发 IO）。 */
internal fun desktopComicSearchHistoryWrite(store: KeyValueStore, entries: List<String>) {
    store.put(DESKTOP_COMIC_SEARCH_HISTORY_KEY, entries.joinToString(DESKTOP_COMIC_SEARCH_HISTORY_SEPARATOR.toString()))
}

/**
 * 读盘 → 追加 → 写盘，一趟做完并交回新表（**调用方必须在 IO 线程**）。
 *
 * ## 为什么不在这里吃"屏上那份本地态"
 *
 * 从前这一格的形状是 `push(searchHistory, word)` 再写盘 —— 拿的是壳里那枚状态。
 * 而那一枚要等读侧那一趟到货才真，用户在它到货**之前**按回车（包内取证 `--query=` 那一族
 * 就是开局立刻发车）时，本地那份还是空表 ⇒ 写回去就把手机侧/上一趟攒的那份**整笔抹掉**。
 * 对岸没有那一格，因为它每次现读：`appendHistory` 里写的就是 `readHistory()`
 * （`SearchViewModel.kt:474-478`）。这里照对岸走，顺手把那格坑填了。
 *
 * 空串那一条不写盘（[desktopComicSearchHistoryPush] 会原表交回，写一次等于白碰一趟盘）。
 */
internal fun desktopComicSearchHistoryAppend(store: KeyValueStore, query: String): List<String> {
    val trimmed = query.trim()
    if (trimmed.isBlank()) return desktopComicSearchHistoryRead(store)
    val next = desktopComicSearchHistoryPush(desktopComicSearchHistoryRead(store), trimmed)
    desktopComicSearchHistoryWrite(store, next)
    return next
}

/** 清空：删那个键（语义来由见文件头那一节）。 */
internal fun desktopComicSearchHistoryClearAll(store: KeyValueStore): List<String> {
    store.remove(DESKTOP_COMIC_SEARCH_HISTORY_KEY)
    return emptyList()
}

/**
 * 那一排**摆哪几枚**（"摆不摆"与"摆几枚"是同一格的两个问法，折成一处出处）。
 *
 * 摆不摆（对岸 `SearchScreen.kt:316` 那三条件逐条照搬）：有历史 **且** 输入框是空的 **且**
 * 没在发车。输入了字就不摆：那一排会在用户敲到第二个字时把"要点的词"从屏上抽走，
 * 而对岸那句 `ui.query.isEmpty()` 存在的正是这个理由。
 *
 * 第四条是**桌面特有**的（对岸没有，也不该有）：`resultCount > 0` 时整格不摆。
 * 对岸那一排住在 `LazyColumn` 的 item 里，与结果同屏共存；桌面这一屏的结果是一面
 * `LazyVerticalGrid` 且 `fillMaxSize`，那一排挡在它上面就是把墙往下挤一截。
 * 这一条也住在这里而不是屏里：屏自己拼 `results.isEmpty() &&` 就是同一件事两个出处
 * （`DesktopMaskJudgementSingleSourceTest` 对遮罩那一族钉过），而它只有住在这里才量得到。
 *
 * 摆几枚：`take(5)`（对岸 `SearchScreen.kt:830` 同一数，那句注释写着"不新增独立历史页面"）。
 *
 * 交回**空表 = 整格不摆**：屏只看这一颗的结果，不再自己拼那三条件（两处判据一定会漂）。
 */
internal fun desktopComicSearchHistoryChips(
    history: List<String>,
    query: String,
    searching: Boolean,
    resultCount: Int,
): List<String> =
    if (history.isEmpty() || query.isNotBlank() || searching || resultCount > 0) {
        emptyList()
    } else {
        history.take(DESKTOP_COMIC_SEARCH_HISTORY_SHOWN)
    }

/** 检索场下拉用的历史：只受「摆几枚」封顶，不看输入框与结果态。 */
internal fun desktopComicSearchDropdownHistory(history: List<String>): List<String> =
    history.take(DESKTOP_COMIC_SEARCH_HISTORY_SHOWN)
