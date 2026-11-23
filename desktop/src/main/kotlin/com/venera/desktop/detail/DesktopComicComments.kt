package com.venera.desktop.detail

import com.venera.engineprobe.DesktopComment

/**
 * 详情页评论那一屏的渲染输入与判读纯件。
 *
 * ## 为什么这一整摊住桌面而不直连安卓那颗屏
 *
 * 安卓 `ComicDetailScreen` 那 1337 行里的评论区绑 `LocalContext` / `viewModel` / material-icons /
 * `CircularProgressIndicator`，是 ③ 档（桌面编不过），所以与阅读统计屏（§134）、备份屏（§135）
 * 同一族做法：**桌面自搭**，只把对岸那几条**判读口径**搬成纯件 —— 逐条回核 `ComicDetailViewModel.DetailCommentState.received`
 * 与 `CommentLoadStatus`，而不是自造一套。判据能直接喂假数据进这几颗纯函数，屏不碰引擎。
 */

/**
 * 评论页那一屏渲染要吃的**成品**状态（壳算好递下来，屏不自己查引擎、不自己拼句子）。
 *
 * 与对岸 `DetailCommentState` 同一族字段（items / loading / error / 页码 / hasMore / loaded），
 * 但桌面这里**只有一棵树**（根评论），没有对岸那套"根 + 楼中楼"两棵树 —— 桌面这一批不接楼中楼
 * （那要把 `replyId` 一路带进翻页游标，与 §123 搜索翻页不同源，单列在后面）。
 */
data class DesktopCommentsView(
    val items: List<DesktopComment>,
    val isLoading: Boolean,
    val error: String?,
    /** 已读到第几页（0 = 一页都还没读回来）。 */
    val page: Int,
    /** 源站报的最大页（读不到 = null，游标型源没有这一栏）。由 `received` 沿用对岸口径。 */
    val maxPage: Int? = null,
    val canLoad: Boolean,
    val hasMore: Boolean,
    /** 「真发过车没」：与"列表为空"是两件事（空评论那一句靠它分开「还没读」与「读了确实没有」）。 */
    val loaded: Boolean,
)

/**
 * 进那一屏时壳的**起手状态**。
 *
 * `isLoading=true` 而不 `loaded=true`：能力位与第一页都还没回来 ⇒ 屏上念「评论加载中…」而不是
 * 「该源未提供评论加载功能」。后一句是**结论**（问完了、源真没说它支持），起手时念它是假话。
 */
fun desktopCommentsInitialView(): DesktopCommentsView = DesktopCommentsView(
    items = emptyList(),
    isLoading = true,
    error = null,
    page = 0,
    maxPage = null,
    canLoad = false,
    hasMore = false,
    loaded = false,
)

/**
 * 把"这一页读回来的评论"摊成下一帧状态。**摊合与翻页判式逐字对齐对岸 `received`**：
 *  - 第 1 页 ⇒ 替换（`items = comments`），续页 ⇒ 追加（`items + comments`）；
 *  - `maxPage` 读不到时：第 1 页当"没有上限"（不拿它压 hasMore），续页沿用已有那一个；
 *  - `hasMore = comments 非空 && 页码 < (maxPage ?: 第1页当无限 / 续页用已知上限或无限)`。
 * 这条判式不许桌面自己简写 —— 一旦与对岸漂了，"到底还有没有下一页"两处就会给两种答案，
 * 用户在一端翻得动、在另一端翻不动（§110「这一页在不在」只留一把尺子同一族纪律）。
 */
fun desktopCommentsReceived(
    current: DesktopCommentsView,
    comments: List<DesktopComment>,
    requestedPage: Int,
    maximumPage: Int?,
): DesktopCommentsView {
    // 逐字对齐对岸 received 那两行：maxPage 续页时若源没再报就沿用上一次的；
    // hasMore 的右侧上限 = 这一页报的 ?: (第 1 页当无限 / 续页沿用上次的 ?: 无限)。
    val nextMax = maximumPage ?: if (requestedPage == 1) null else current.maxPage
    val upperBound = maximumPage ?: if (requestedPage == 1) Int.MAX_VALUE else current.maxPage ?: Int.MAX_VALUE
    return current.copy(
        items = if (requestedPage == 1) comments else current.items + comments,
        isLoading = false,
        error = null,
        page = requestedPage,
        maxPage = nextMax,
        hasMore = comments.isNotEmpty() && requestedPage < upperBound,
        loaded = true,
    )
}

/**
 * 评论页顶部/底部那一句**状态成句**（对齐对岸 `CommentLoadStatus` 三档 + 空态那两句）。
 *
 * 只负责"该念哪一句"，不负责"摆不摆"：摆不摆是屏的事，屏再吃这一颗的返回值决定念白/念错/念未提供。
 * 返回 null = 没有任何状态句要念（读回来了且非空 —— 那就直接摆列表）。
 */
fun desktopCommentsStatusNote(view: DesktopCommentsView): String? = when {
    view.isLoading -> com.venera.desktop.settings.DesktopI18n.tr("评论加载中…", "Loading comments…")
    // 屏上念归档短句，不透原串。
    view.error != null -> com.venera.desktop.platform.desktopErrorShortText(view.error!!)
    !view.canLoad && view.items.isEmpty() -> com.venera.desktop.settings.DesktopI18n.tr("该源未提供评论加载功能", "Comments are not supported by this source")
    else -> null
}

/**
 * 空态那一句（对岸 `comments.isEmpty() && loaded && !loading && error==null` 那一支的文案）。
 * 分得开「还没读回来」与「读了确实没有」—— 后者才念"还没有评论"，前者一句都不许提前念（那是假话）。
 */
fun desktopCommentsEmptyNote(view: DesktopCommentsView): String? {
    val readButEmpty = view.loaded && !view.isLoading && view.error == null && view.items.isEmpty() && view.canLoad
    return if (readButEmpty) com.venera.desktop.settings.DesktopI18n.tr("这一本还没有评论", "No comments yet") else null
}

/**
 * 详情那枚「评论」把手的文案（对齐对岸 `:681`「评论 $commentCount」/「评论」两档）。
 *
 * `commentCount` 为 `null`（源没在详情里报这一栏）时**只念"评论"而不摆数** —— 对岸那枚是 `commentCount > 0`
 * 才带数，桌面进一步把"没报"与"报了 0"分开：没报不念 0（拿 0 冒充"没有评论"是假账，见 §121 那条口径）。
 */
fun desktopCommentsHandleLabel(commentCount: Int?): String =
    if (commentCount != null && commentCount > 0) com.venera.desktop.settings.DesktopI18n.tr("评论 $commentCount", "Comments $commentCount") else com.venera.desktop.settings.DesktopI18n.tr("评论", "Comments")

/**
 * 发车那一瞬的状态转移：只置「正在读」并清掉上一次的错，**其余一格都不碰**（已读回的 items /
 * page / hasMore 全留着 —— 续页时上一屏读到的那几条不许因为新发一趟就闪没）。对齐对岸
 * `loadComments` 起始那句 `copy(isLoading = true, error = null)`（`:797`）。
 */
fun desktopCommentsLoading(current: DesktopCommentsView): DesktopCommentsView =
    current.copy(isLoading = true, error = null)

/**
 * 那一趟塌了的状态转移：只置「停手 + 成因」，同样**不碰 items / page / hasMore**（已读回的那几条留着，
 * 屏上"停在第 N 页读不动了 + 为什么"比整屏抹平更可念）。对齐对岸 catch 那句
 * `copy(isLoading = false, error = message)`（`:811`）。
 */
fun desktopCommentsFailed(current: DesktopCommentsView, message: String): DesktopCommentsView =
    current.copy(isLoading = false, error = message)

/**
 * 取证读数行（壳里那一跳跑完打一句，与 §134 统计屏 / §135 备份屏同族：屏上没有的东西要靠这一行分档）。
 *
 * `D_评论 本=<comicId> 条=<已读回条数> 页=<当前页> 能读=<源认不认> 加载=<在不在读> 翻页=<还有没有下一页> [ 因=<成因> ]`。
 * 「条=0 到底=true 加载=false」= 读了确实没评论；「能读=false」= 源没那一形；两者在包内日志里分得开，
 * 屏上只各念一句 —— 出问题时这一行是唯一的证人。
 */
fun desktopCommentsScreenReading(comicId: String, view: DesktopCommentsView): String {
    val base = "D_评论 本=$comicId 条=${view.items.size} 页=${view.page} 能读=${view.canLoad} 加载=${view.isLoading} 翻页=${view.hasMore}"
    return if (view.error != null) "$base 因=${view.error}" else base
}
