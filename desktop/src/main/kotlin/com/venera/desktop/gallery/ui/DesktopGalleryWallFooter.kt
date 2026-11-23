package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GallerySite

/**
 * 画廊那面墙的**页尾那一格**（「还有下一页吗」+ 那几句成品）。
 *
 * ## 缺口长什么样
 *
 * 续页那颗 [fetchWallPage] 与它里面那三处转交（哪些腿不再问 / 各腿要第几页 / 一页怎么落地）是
 * **批 23** 落的，批 31 还拿真脚本端口把整条链跑过（`DesktopWallLedgerTest` 六条，含"没答上那站不推进页码"）——
 * 而 `desktop/src/main` 里它的调用点**一直为零**（只有那颗测试在调）：逻辑齐了而没人开门，
 * 于是画廊那面墙只有第一页，三十几张之后的东西用户**看不见也够不着**。
 * 开的就是那一扇门（壳里 `desktopWallPageAfter` + `fetchWallPage` 各一处调用，
 * `DesktopGalleryWallFooterTest` ⑪ 把那两扇门的调用点数钉成了判据），而**页尾那一格是门上的门闩**：
 * 没有"还发得动吗"与那三句话，接上取数只是让用户对着空气再点一次。
 * 这是本仓记过一整族的那件事（「有逻辑没有门」，批 114 / 115 / 118 / 119 / 120 同形），
 * 也正是漫画搜索那一条的**另一半**：那边翻页刚接上，这边照旧断着。
 *
 * ## 这里只补"判断"，不重述任何一条判据
 *
 * 页码账、去重键、到底名单全在 [DesktopGalleryBatch] 里（批 10 立的持有件）；
 * 三句页尾文案全在 [desktopGalleryMoreReading] / [desktopMissingReading] / [desktopExhaustedReading] 里
 * （批 12 收成的一处出处，[desktopGalleryMoreLabel] 同理）。本文件只做**这一屏的组合**：
 * 把"还发得动吗"和"页尾念哪几句"算成**成品**递进屏 —— 屏自己拼 `batch.atEnd ||` 就是同一件事两个出处
 * （`DesktopMaskJudgementSingleSourceTest` 对遮罩、对搜索那四格，钉的都是这一族）。
 *
 * ## 三条不显然的口径
 *
 * 1. **「还没到货」不等于「到底」**：[batch] 为 `null`（首屏那一趟还没回来、或整条塌了）时那一枚按钮
 *    **不摆**。摆出来按下去等于发一笔 `queries` 为空的续页，而那颗 reducer 对空表交回的是"原样一颗"
 *    ⇒ 屏上多一枚按了什么都没发生的把手（评审 R6 那一族假开关）。
 * 2. **到底那一档仍然摆不出按钮，但必须念得出为什么**：`atEnd` 那句走
 *    [desktopExhaustedReading]（名单为空时它自己交回 `null` ⇒ 没话就不念，不留空壳句）——
 *    所以这里**不另判一次**"到底没"，只问那颗持有件。
 * 3. **失败只掉这一笔**：续页是**追加**语义，[DesktopGalleryMorePhase.Failed] 那句
 *    「已经摆出来的这些一张不少」是它的担保，所以失败那一档**不撤**那枚按钮
 * （撤了用户就没有重试的路 —— 在搜索那格量的正是同一件事，那一批的扰动实验
 *    把「Failed 折成 End」抓到过 2 红）。
 *
 * ## 第四格是批 13 那本账（「取回」与「上屏」是两个主语）
 *
 * [onScreen] 必须单独传：`batch.posts` 数的是**取回**的条目，而墙上摆的是**过了守卫**的那些
 * （`HIDDEN` 那一档根本不落屏）。把「已取 N 张」的 N 写成 `batch.posts.size`，
 * 读数就会在 175 取回 / 160 上屏那种晚上谎报张数 —— 那正是 `DesktopWallCountSourceTest` ⑤
 * 在搜索那屏抓过的同一颗缺陷，只是这次换到页尾那一格。
 */

/** 那一屏页尾要吃的那三格（**成品**：屏只搬，不判、不拼串）。 */
internal data class DesktopGalleryWallFooter(
    /** 那枚按钮的文案；`null` = **整颗不摆**（到底 / 还在取 / 还没到货 / 一笔都没发出去）。 */
    val moreLabel: String?,
    /** 页尾那几句（正在取 / 没取成 / 少了谁 / 到底了），已是成品句子，次序即屏上次序。 */
    val lines: List<String>,
    /** 取证那一句（`D_画廊 翻页 …`），**屏上不念**：包内取证对的是逐腿页码，不是像素。 */
    val reading: String,
)

/**
 * 页尾那一格该摆什么。四格全在这里算完，屏只看 `moreLabel == null` 摆不摆那一枚把手。
 *
 * @param batch 首屏与续页攒出的那一份持有件；`null` = **还没到货**（与"到货了而零张"是两件事）。
 * @param onScreen **墙上真摆出来**的那些张数（= 屏上 `cards.size`）。那枚把手与「正在取」那句
 *                 都数它，不数 `batch.posts`（那是取回的，两个主语，见文件头第四条）。
 * @param phase 「下一页」那一笔的相位 —— 复用批 23 收成一颗的 [DesktopGalleryMorePhase]，
 *              本屏**不另立第二颗**（那颗的声明处由 `DesktopGalleryPagingTest` ① 数着）。
 * @param unit 量词，**必填**：这一屏摆的是上屏的图 ⇒ 给「张」；那颗「最新」列表屏给的是「条」。
 *             写死其一的形状与 [desktopPerSiteReading] 同一族（判据 ② 喂两种值）。
 */
internal fun desktopGalleryWallFooter(
    batch: DesktopGalleryBatch?,
    onScreen: Int,
    phase: DesktopGalleryMorePhase,
    unit: String,
): DesktopGalleryWallFooter {
    val taken = onScreen
    // 摆不摆那一枚把手 = 那一笔发不发得动，两句话**同一颗判据**（屏与 effect 各判一遍一定漂）。
    val canLoadMore = desktopWallMoreAllowed(batch, phase)
    val lines = buildList {
        desktopGalleryMoreReading(phase, taken, unit)?.let { add(it) }
        if (batch != null) {
            // 「少了谁」：这一轮答上的腿，上一轮那句必须一起销掉 —— 那条规则住在
            // [desktopFailureLedgerAfter]，而 `batch.failures` 就是它算好的那一份 ⇒ 这里**不并**、只念。
            desktopMissingReading(batch.queries.keys, batch.failures)?.let { add(it) }
            desktopExhaustedReading(doneSiteNames(batch))?.let { add(it) }
        }
    }
    return DesktopGalleryWallFooter(
        moreLabel = if (canLoadMore) desktopGalleryMoreLabel(taken, unit) else null,
        lines = lines,
        reading = desktopGalleryWallReading(batch, onScreen, phase),
    )
}

/**
 * 「下一页」那一笔该不该发出去 —— 页尾那颗把手与壳里那颗 effect **共用这一颗**。
 *
 * 四档各挡一种坏形状：
 * - `batch == null`：**还没到货**（见文件头第 1 条，摆出来是一枚假开关）；
 * - `queries.isEmpty()`：一笔都没发出去 ⇒ `desktopGalleryBatchNextPages` 交回空表、
 *   [fetchWallPage] 一趟空发，而 reducer 对空 `pages` 交回的是"原样一颗"
 *   —— 按了什么都没发生的把手（评审 R6 那一族）；
 * - `atEnd`：到底了还发 = 屏上写着「已经取到底」而下面挂着「下一页」（手册 §0 那一族假读数）；
 * - `Loading`：同一笔发两遍（连点把两页并排贴上来，页码账却只推进一次）。
 *
 * ⚠️ **「失败」那一档不在这里**：那一档照发（文件头第 3 条）。
 * 这里不许写成 `phase !is Idle`：那会把 Failed 折成"不许重试"，
 * 在搜索那一格量的正是它（扰动「Failed 折成 End」抓到过 2 红）。
 */
internal fun desktopWallMoreAllowed(batch: DesktopGalleryBatch?, phase: DesktopGalleryMorePhase): Boolean {
    if (phase is DesktopGalleryMorePhase.Loading) return false
    return batch != null && batch.queries.isNotEmpty() && !batch.atEnd
}

/**
 * 到底名单：点名的是**发出去过而真到底**的那几站（次序恒为 `GallerySite.entries` 的自然序）。
 *
 * ⚠️ 不许直接 `batch.sitesDone` 就交出去：那一枚是 `Set`，并发取数回来的插入次序本就不稳
 * ⇒ 同一件事在两帧里换了位置（批 12 教过的那一条，[desktopMissingReading] 里同一句理由）。
 */
private fun doneSiteNames(batch: DesktopGalleryBatch): List<String> =
    GallerySite.entries.filter { it in batch.sitesDone }.map { it.displayName }

/**
 * 取证那一句（**stdout 读数**，与 `D_取数` / `D_收藏` / `D_搜索` / `D_守卫` 同族）。
 *
 * 为什么屏上已经有那几句还要这一行：屏上念的是"已取 N 张"，而包内取证要对的是**逐腿页码**与
 * **到底名单**（那两样在屏上看不见，而"少一批图"那一族恰好只有对账才抓得到 —— 手册 §0 坑 ㉓）。
 * 「还没到货」那一档也打（`取=还没到货`）：那一档与"到货了而零张"在读数上必须分得开，
 * 正如 [desktopGalleryWallFooter] 第 1 条口径讲的按钮。
 *
 * 「取」与「屏」两本账都念（批 13 那条）：对不上就说明守卫或混排吃掉了一批，而那是屏上看不出来的。
 */
internal fun desktopGalleryWallReading(
    batch: DesktopGalleryBatch?,
    onScreen: Int,
    phase: DesktopGalleryMorePhase,
): String = buildString {
    append("D_画廊 翻页 腿=").append(batch?.queries?.size ?: 0)
    append(" 页=")
    append(
        batch?.pageBySite?.takeIf { it.isNotEmpty() }
            ?.entries?.joinToString("、") { "${it.key.name}:${it.value}" } ?: "无",
    )
    append(" 到底=")
    append(
        when {
            batch == null || batch.queries.isEmpty() -> "无"
            batch.atEnd -> "是"
            else -> "否"
        },
    )
    append(" 取=").append(batch?.let { "${it.posts.size}" } ?: "还没到货")
    append(" 屏=").append(onScreen)
    append(" 剔重=").append(batch?.droppedDuplicates ?: 0)
    append(" 相位=")
    append(
        when (phase) {
            DesktopGalleryMorePhase.Idle -> "闲"
            DesktopGalleryMorePhase.Loading -> "取"
            is DesktopGalleryMorePhase.Failed -> "败(${phase.message.take(60)})"
        },
    )
    if (batch != null && batch.failures.isNotEmpty()) {
        append(" 缺席=").append(batch.failures.keys.joinToString("、") { it.name })
    }
}
