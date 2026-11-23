package com.venera.desktop.gallery.data

import com.venera.engineprobe.ComicInfo
import com.venera.engineprobe.EngineSession

/**
 * 漫画「批量/整本下载」的**决策层**（全抽纯函数，屏只搬不算）。
 *
 * ## 补的是哪一格
 *
 * 详情那一屏从起只有**单话**把手（`DesktopComicDetailScreen` 的
 * `onDownloadEpisode`，挂在卡片外面的兄弟节点上），整本 40 话就要按 40 次。对岸
 * `app/…/feature/ComicDetailScreen.kt:1659-1782` 那一整块「批量离线下载」在桌面上从来没有对应物。
 * 本文件把那块的语义逐条搬成可纯跑的判据面：默认只选中未下载 / 标题两档 /
 * 「全选未下载 ↔ 全不选」（判据是 `selectedIds.size == undownloadedIds.size`）/
 * 每行两档徽章 / 确认「开始下载 (N)」且 N=0 时按不动 / 那句「已将 N 话加入下载队列」。
 *
 * ## 两处与对岸的**形状差**（不许照抄，逐条写明）
 *
 * 1. **桌面没有章节分组**。`parseComicLoadInfo`（`engine-probe/…/ComicLoadInfo.kt:28-64`）把源站的
 *    分组表**摊平**成一条 `List<Episode>`，空表时补一条 `Episode("", comicId, placeholder = true)`。
 *    对岸那颗 `downloadChapters(details, selectedGroupIndex)` 在桌面上没有对应物 ⇒
 *    这里只保留「无章 ⇒ 一条整本占位」那一档，而空表那两句文案要改成**无分组**的实话。
 * 2. **桌面弹窗那一格的后端跟着主题档走**（⚠️ 第 140 批改过一次口径，别照旧文读）：
 *    从前桌面 main 只 provide `LocalVeneraDarkTheme`、外面套**空参** `MiuixTheme`，从不 provide
 *    `LocalVeneraTokens` ⇒ `VeneraTokens.appearance` 落在那颗 Local 的**默认值** `MIUIX`
 *    ⇒ `VeneraDialog` 实为 miuix 的 `WindowDialog`。
 *    现在壳里换成 `DesktopVeneraTheme`（钉 `AppearanceStyle.MD3`）⇒ `useMiuixWidgets` 恒假
 *    ⇒ 同一枚 `VeneraDialog` 在桌面**运行时**改走 `Md3AlertDialog`（与安卓默认档同形）。
 *    形状结论不变、理由变了：`WindowDialog.title` 只是 `String`，而 `AlertDialog` 的 `title` 是
 *    固定排版的 composable 且**没有**安卓那格「标题行右侧塞一颗 Row」的槽位 ⇒
 *    「全选未下载 / 全不选」仍只能放进 `content` 顶部（⑬ 那把尺钉的是这个落点，两档都成立）。
 * ⚠️ 「无头能不能断言弹窗的内容」这一格量过（凌晨探针，当时是 `WindowDialog`）：
 *    题、正文、确认钮三格 `onAllNodesWithText(...)` 全部命中，且 `performClick` 落得进回调
 *    （读数 `题=中 内容=中 确认=中 ‖ 点确认=中 点把手=中 点行=中 → confirmTaps=1 toggleTaps=1 rowTaps=1`）
 *    ⇒ 判据写真 UI 断言，不必退回纯函数级。
 *
 * ## 归一（这一格是最容易出事的地方）
 *
 * 读侧那条树里每一行的 `sourceKey`/`comicId` 取自**目录名**（净化形），而详情手上拿的是**原始形**
 * （`EngineSession.Comic.id` / `sourceKey`）。不归一的后果不是读错，而是「盘上有却全判未下载 ⇒ 重下一遍」。
 * 所以比之前两侧都过 `desktopComicSafePathKey`，且**归一不许住在读侧那颗件里**
 * （`DesktopComicShelfRawIdTest` ⑥ / `DesktopComicOfflineReadingTest` ② 各钉着那两颗的净化计数）。
 * 先例：`DesktopComicOfflineReading.kt:56-67`。话号那一格不净化 —— 它住在清单里而不是目录名里。
 */

/** 某一话在本机的三档。名字与对岸 `ChapterOffline`（`NONE`/`PARTIAL`/`COMPLETE`）同轨而用桌面自己那族前缀。 */
internal enum class DesktopComicBulkOffline {
    /** 本机没有（或一条页都没有）。⇒ 默认选中。 */
    NONE,

    /** 有而只下一半 ⇒ 默认选中，且徽章念「离线不全」而不是「已下载」（念绿就等于永不重下）。 */
    PARTIAL,

    /** 齐了 ⇒ 默认不选中。 */
    DOWNLOADED,
}

/** 弹窗里的一行：那一话 + 它在本机的档位。档位由壳里算好递进来，屏不许自己查盘。 */
internal data class DesktopComicBulkRow(
    val episode: EngineSession.Episode,
    val offline: DesktopComicBulkOffline,
)

/**
 * 一条盘上记录 → 三档。**判齐只吃那颗共用判式的结论**（`DesktopComicDownloadedChapter.complete`
 * 是 `desktopComicChapterIsComplete` 回读盘之后的答案），这里不再另立第四种「齐不齐」。
 *
 * 三档逐条对齐对岸 `chapterOfflineState`（`app/…/download/ChapterCompleteness.kt:29-40`）：
 *  - 盘上一颗算整页的都没有 ⇒ `NONE`（对岸 `pageCount <= 0` 那一支）；
 *  - 清单说齐 ⇒ `DOWNLOADED`；
 *  - 清单没标齐、而应写那几页盘上**一颗都不缺** ⇒ `DOWNLOADED`（对岸 `pageCount >= expected` 那一支）。
 * 这一支走得到：中断那一趟会把清单落回 `complete=false` 而页一颗都不删，
 *    于是"清单说没齐而盘上其实齐了"是真实存在的一档；判成 `PARTIAL` 会把它塞进默认选中，
 *    用户按下就是明知故犯地重跑一趟。
 *  - 其余 ⇒ `PARTIAL`。
 *
 * ⚠️ **一处刻意不照抄**：对岸还有「`expectedPages` 缺失或 ≤ 0 ⇒ 当成齐」那一支（老数据与
 * "页表没取到就写了清单"）。桌面走不到那一支：读侧 `readChapter` 认不出缺 `expectedPages` 的清单
 * （整条不进表），而 `writtenPages` 只数 `0 until expectedPages` ⇒ 应写非正数时盘上一颗都不算，
 * 于是桌面保守判 `NONE`（念「未下载」），而不是照抄那句"当齐"。**宁可让用户重跑一趟，
 * 不许把"我说不出该有几页"念成"已经齐了"** —— 与「清单应写 ≤ 0 时仍要问源站」同一条口径。
 */
internal fun desktopComicBulkOfflineOf(chapter: DesktopComicDownloadedChapter?): DesktopComicBulkOffline = when {
    chapter == null -> DesktopComicBulkOffline.NONE
    chapter.writtenPages <= 0 -> DesktopComicBulkOffline.NONE
    chapter.complete -> DesktopComicBulkOffline.DOWNLOADED
    chapter.writtenPages >= chapter.expectedPages -> DesktopComicBulkOffline.DOWNLOADED
    else -> DesktopComicBulkOffline.PARTIAL
}

/**
 * 把详情的章节表与本机那一棵树并成一弹窗那几行。
 *
 * 三个入参各是一件事，缺一就有一档念错：
 *  - [info] 为 `null` = 详情**还没读回来** ⇒ 交回空表（屏上念那一句，而不是念「没有可下载的章节」）；
 *  - [chapters] 为 `null` = 本机那一棵树**还没数回来** ⇒ 同样交回空表（不许把「没数」说成「本机都没有」，
 *    那会让默认选中变成「全选」而用户按下就是重下整本）；
 *  - [sourceKey] / [comicId] 是详情手上的**原始形**，比之前在本函数里归一（见文件头的「归一」那一段）。
 *
 * ⚠️ 这里吃**摊平的话单**而不是 `gallery/ui/DesktopComicDownloadsPane.kt` 那一型
 * （`DesktopComicDownloadListing`）：决策层住 `gallery/data`，反向去引 `gallery/ui` 就是层次倒置。
 * 壳里现成有一行 `groups.flatMap { it.chapters }`。
 */
internal fun desktopComicBulkRows(
    info: ComicInfo?,
    /** 本机那棵树数回来的**全部**话（`null` = 还没数回来）；筛站+号与归一都在本函数里做。 */
    chapters: List<DesktopComicDownloadedChapter>?,
    sourceKey: String,
    comicId: String,
): List<DesktopComicBulkRow> {
    val episodes = info?.episodes ?: return emptyList()
    if (chapters == null) return emptyList()
    val safeSource = desktopComicSafePathKey(sourceKey)
    val safeComic = desktopComicSafePathKey(comicId)
    val onDisk = chapters
        .filter { it.sourceKey == safeSource && it.comicId == safeComic }
        .associateBy { it.chapterId }
    return episodes.map { ep ->
        DesktopComicBulkRow(ep, desktopComicBulkOfflineOf(onDisk[ep.id]))
    }
}

/** 默认只选中「尚未下载」的那些（齐了的不进默认，半截的进）。 */
internal fun desktopComicBulkDefaultSelection(rows: List<DesktopComicBulkRow>): Set<String> =
    rows.filter { it.offline != DesktopComicBulkOffline.DOWNLOADED }.map { it.episode.id }.toSet()

/** 还没下载的那些的枚数（「全选未下载 ↔ 全不选」那一判据的分母）。 */
internal fun desktopComicBulkUndownloadedCount(rows: List<DesktopComicBulkRow>): Int =
    rows.count { it.offline != DesktopComicBulkOffline.DOWNLOADED }

/**
 * 标题行右侧那枚把手的文案：判据是对岸那一枚 `selectedIds.size == undownloadedIds.size`
 * （`ComicDetailScreen.kt:1683`），而不是"这一趟我记没记得按过"。
 *
 * ⚠️ 一枚都没下的时候不许念「全不选」—— 那时候表是空的，按它什么也不做，
 * 念出来像在说"已经全选了"。对岸同一格还多一道 `undownloadedIds.isNotEmpty()` 的闸，这里照抄。
 */
internal fun desktopComicBulkToggleAllLabel(
    rows: List<DesktopComicBulkRow>,
    selectedIds: Set<String>,
): String {
    val undownloaded = desktopComicBulkUndownloadedCount(rows)
    return if (undownloaded > 0 && selectedIds.size == undownloaded) "全不选" else "全选未下载"
}

/** 按下去的下一份选中集：已到「全选未下载」那一档就清空，否则填成未下载那一批。 */
internal fun desktopComicBulkToggleAllNext(
    rows: List<DesktopComicBulkRow>,
    selectedIds: Set<String>,
): Set<String> {
    val undownloaded = desktopComicBulkUndownloadedCount(rows)
    return if (undownloaded > 0 && selectedIds.size == undownloaded) {
        emptySet()
    } else {
        desktopComicBulkDefaultSelection(rows)
    }
}

/** 某一行的两档徽章；`NONE` 那一档**不摆**（对岸 `else -> Unit`：没下过的不需要一枚"未下载"）。 */
internal fun desktopComicBulkBadgeLabel(row: DesktopComicBulkRow): String? = when (row.offline) {
    DesktopComicBulkOffline.DOWNLOADED -> "已下载"
    DesktopComicBulkOffline.PARTIAL -> "离线不全"
    DesktopComicBulkOffline.NONE -> null
}

/**
 * 某一行的题名。占位话那一格**不念空串**：桌面这条路上「无章」那一档由
 * `parseComicLoadInfo` 补的 `Episode("", comicId, placeholder = true)` 表示（名是空的），
 * 而对岸同一格的名字是「整本」（`DownloadChapterSelection.kt:22`）⇒ 这里念同一句话，
 * 而不是把空名或那枚长号摆给用户看。
 */
internal fun desktopComicBulkRowLabel(row: DesktopComicBulkRow): String = when {
    row.episode.placeholder -> "整本"
    row.episode.name.isNotBlank() -> row.episode.name
    else -> row.episode.id
}

/** 确认那颗的文案（带枚数，枚数就是按下去会排进队列的笔数）。 */
internal fun desktopComicBulkConfirmLabel(selectedCount: Int): String = "开始下载 ($selectedCount)"

/** 确认那颗的可用性：一枚都没选就按不动（对岸 `enabled = selectedIds.isNotEmpty()`）。 */
internal fun desktopComicBulkConfirmEnabled(selectedIds: Set<String>): Boolean = selectedIds.isNotEmpty()

/** 按下去之后屏上那一句（对岸那颗 Toast 的人话；桌面没有 Toast，走详情那一行 note）。 */
internal fun desktopComicBulkEnqueueNote(enqueuedCount: Int): String = "已将 $enqueuedCount 话加入下载队列"

/**
 * 空表那**四档**各念一句（不许塌成一句，也不许按顺序短路掉真因）：
 *  - 本机那一棵树**数不出来**（读盘整趟抛了）⇒ 点名成因。这一档必须排第一：
 *    数不出来时 [desktopComicBulkRows] 也交回空表，若先看"没数回来"就会念成「这一本没取到章节」，
 *    那是把"我读不了盘"说成"这本没东西"（本仓那一族假话的第四种宿主）。
 *  - 还没数回来 ⇒ 待读那一句；
 *  - 详情还没读回来 ⇒ 等一等；
 *  - 两样都回来了而章节表是空的 ⇒ **这一格与对岸不同**：对岸写的是「当前分组暂无可下载章节」，
 *    而桌面没有分组那一层（`parseComicLoadInfo` 把分组摊平了）⇒ 改成「这一本没取到章节」的实话。
 */
internal fun desktopComicBulkEmptyNote(
    infoLoaded: Boolean,
    scanned: Boolean,
    scanError: String?,
): String = when {
    scanError != null -> "本机那一棵树数不出来：$scanError"
    !scanned -> DESKTOP_COMIC_BULK_PENDING_NOTE
    !infoLoaded -> "详情尚未加载完成，请稍后重试"
    else -> "这一本没取到章节，没有可下载的东西"
}

/** 本机那一棵树还没数回来时那一句（与「没章节」分开说，理由见上面那段）。 */
internal const val DESKTOP_COMIC_BULK_PENDING_NOTE: String = "正在数本机那一棵树…"

/** 弹窗的标题两档：一本都没有章的（源站给的是"整本"那一档）念「下载整本」，其余念选章节。 */
internal fun desktopComicBulkTitle(rows: List<DesktopComicBulkRow>): String {
    val wholeBookOnly = rows.size == 1 && rows.single().episode.placeholder
    return if (wholeBookOnly) "下载整本" else "选择下载章节"
}

/** 把选中集按章节表的原次序落成要排的笔（对岸 `allChs.filter { selectedIds.contains(it.id) }` 同一形）。 */
internal fun desktopComicBulkEpisodesOf(
    rows: List<DesktopComicBulkRow>,
    selectedIds: Set<String>,
): List<EngineSession.Episode> = rows.filter { selectedIds.contains(it.episode.id) }.map { it.episode }
