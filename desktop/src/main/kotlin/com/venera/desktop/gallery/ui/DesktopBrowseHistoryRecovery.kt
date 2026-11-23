package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GalleryPost
import com.venera.desktop.gallery.data.DesktopBrowseView

/**
 * 「那一批取回来的完整帖」里按**卡上那枚 uid** 认回完整 [GalleryPost]（写侧的上游）。
 *
 * ## 为什么非要有这一颗
 *
 * 活渲染路里点开一张卡，递给回调的是 [DesktopWallCardModel] —— 那是**投影**（图上那一格与看图屏
 * 要的渲染输入），里面没有 `rating` / `width` / `previewUrl` 单独存的那些判据字段。
 * 而写侧那颗 `recordView(post: GalleryPost, at: Long)` 吃的是**完整帖**。
 * 于是「按下这一张」那一刻手里那颗卡**不足以**记一行历史 —— 这正是留下的那格缺口
 * （那份档与那颗存储件全在，活渲染路里连一个调用点都没有）。
 *
 * ## 三条口径
 *
 * - **键走 [GalleryPost.uid]**（`站点routeKey:id`），不另造第二把：卡上的 `uid` 就是投影时从
 *   `post.uid` 搬过来的（`DesktopWallCardModel.kt:115`），与收藏 / 历史 / 去重同一口径。
 *   ⚠️ 而 `desktopWallCardsOf` 里那颗去重键写的是 `"${post.site}:${post.id}"`（枚举名 `YANDERE`），
 *   与 `uid` 的 `yandere`（routeKey）**不是同一串** —— 拿错那一把就永远查不到，单看每一颗都自洽
 *   （本仓记过的那类复合键不同轨）。这里只认 `uid`。
 * - **认不出交回 null 而绝不抛**：一张卡能摆上屏就一定在那一批里（两面墙共用同一批 `posts` 投影而来），
 *   真到了 null 只能是调用点递错了批（比如拿搜索那批的卡去查主墙那张表）—— 那是「念得出成因」的事，
 *   不是崩窗的事。调用方把 null 那一档念成一句实话。
 * - **同键撞两张取第一张**：`desktopWallCardsOf` 已经按 `site:id` 剔过重了，这里不重复判；
 *   真要防的是「投影剔重用的那把键与这里查的那把键不同轨」，而那由上一条口径钉住。
 */
internal fun desktopBrowsePostOfUid(
    posts: List<GalleryPost>,
    uid: String,
): GalleryPost? {
    if (uid.isBlank()) return null
    return posts.firstOrNull { it.uid == uid }
}

/**
 * 「按下回原卡」之后那一句要不要念、念什么（A 档最后一颗）。
 *
 * ## 判据形状：五档各一句，不合流
 *
 * 从前 [com.venera.desktop.gallery.data.desktopBrowseRowOf] 那一条口径钉的是「磁盘那一行**只存**
 * 预览档 + 站 + 号 + 时刻」（判据 ⑨）。**回原卡**这一格从前没有 —— 批 86 记过「不摆那一枚按钮 =
 * 半屏假开关」。到那一族前提散了：`DesktopGalleryBoards` 三站都真接了
 * `fetchById(id): Result<GalleryPost?>`，于是**现取**再拼卡是拿得到**整张**（不是半屏）。
 *
 * 现取就有五种坏形状 —— **各归各的一句话**（合并任何一种就是把「为什么拿不回来」再糊一次）：
 *
 * | 顺序 | 触发形状 | 那一屏念什么 |
 * |---|---|---|
 * | ① failure | 网络/解析塌了（`runCatching` 抓到的那颗 [Throwable]）| `取不回那一张：{类名} {原因前 160}` |
 * | ② 站方 null | 站答了而那张找不到（被删 / 号错）| `那张拿不回来：站方说没有这一张` |
 * | ③ 串号 | 答回来的**站或号**跟那一行不一致（§110 那族「读回来了而读的不是那一行」）| `站方答的不是那一行：{站}#{号}` |
 * | ④ 投不出卡 | 拿到了 post 而投影那一路**返回空表**（`desktopWallGuardedCards` 那颗 `mapNotNull` 把这一颗剔了）| `这一张投不出卡（投影没给货）` |
 * | ⑤ 遮 | 投出来的那张 `openable=false`（被守卫判遮，或 uid 空那种夹具卡）| `这一张要遮着看 ⇒ 桌面不放大成人图` |
 *
 * 顺序按**证据强度**排：先"根本没答上"（①）、再"答上了而没有"（②）、再"有而不对"（③）、
 * 再"对而这一屏投不出"（④）、最后"投出来了而不给点开"（⑤）。
 * 把 ① 折进 ② 就是把「我这头断了」说成「站方说没了」；把 ⑤ 折进 ④ 就把 §130 那条
 * 「打码卡照旧在墙上摆着，只是不给点开」的口径抹了。
 *
 * ## 交 null 就是「切进看图屏了、屏上什么都不念」
 *
 * `null` = **那张真投出来了**、壳里那一路 `route = GalleryPostViewer(card)` 已经切过去 ⇒
 * 屏上不留一句多余的话（§134/§135 同族：读回来一句、走掉不念）。这一颗纯件**不抛** ——
 * 五档都是"看得见的坏"，不是"编程错"（编程错走 [error] 而不是走文案）。
 *
 * ## 为什么这颗住在 `gallery/ui/` 而不是壳里
 *
 * 判据。§134/§137 拆法同一族：屏只吃成品，判读住在能单测直接取值的纯件里；
 * 合进壳就变成"看得见的分支而拿不到值尺钉"—— 手册 §0 坑 ㉓ 那一族少数的读数没人会去数。
 * 而它**不放在 `gallery/data/`**：它吃 [DesktopWallCardModel]，那是 ui 层那颗投影模型；
 * data→ui 反向 import 破了 §S2-B 立下的分层。
 */
internal fun desktopBrowseRecoveryNote(
    row: DesktopBrowseView,
    fetched: GalleryPost?,
    failure: Throwable?,
    card: DesktopWallCardModel?,
): String? = when {
    failure != null ->
        "取不回那一张：${com.venera.desktop.platform.desktopFailureNoteOf(failure)}"
    fetched == null -> "那张拿不回来：站方说没有这一张"
    fetched.site != row.site || fetched.id != row.postId ->
        "站方答的不是那一行：${fetched.site.displayName} #${fetched.id}"
    card == null -> "这一张被本机那套屏蔽规则藏了 ⇒ 不摆进看图屏"
    !card.openable -> "这一张要遮着看 ⇒ 桌面不放大成人图"
    else -> null
}
