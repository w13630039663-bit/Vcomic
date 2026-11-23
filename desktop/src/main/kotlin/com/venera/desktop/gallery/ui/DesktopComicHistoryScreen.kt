package com.venera.desktop.gallery.ui

import com.venera.compose.data.db.HistoryRecord

/**
 * 「阅读历史」那一屏在桌面上**只剩这一颗策略件**——屏本体已退役，展示在共享面
 * `feature/HistoryScreenBody.kt`（桌面壳经 `VeneraDesktopLargeScreen` 的 `DesktopRoute.History`
 * 支路直接喂那一颗）。这里留下的全是判据与文案：取键、进度那句、四档门控与每档的话、stdout 读数。
 * （屏题名「历史」不再自持常量——上屏出处在共享屏本体；对岸 `HistoryScreen.kt:171-172` 顶栏题名与大标题同串。）
 */

internal const val DESKTOP_HISTORY_EMPTY_TITLE: String = "还没有阅读记录"
internal const val DESKTOP_HISTORY_EMPTY_MESSAGE: String = "去随便翻两页吧"

/** 异源那一行的说明。**必须上屏**：一枚按不动的卡与一句"为什么按不动"是两件事。 */
internal const val DESKTOP_HISTORY_OTHER_SOURCE_NOTE: String = "这一行是别的源记的，去站点换过来才打得开"

/**
 * 「那一屏的源清单还没读回来」那一档的说明。
 *
 * 为什么必须另有一句而不许借用上面那句：`source_name` 那一列两端写的不是同一种东西（手机写显示名、
 * 桌面写键），所以**显示名那一档要靠源清单才能认**。清单还没读回来的那一两帧里，手机写的那几行
 * 键对不上、名字又还没拿来可比 —— 这时念"这一行是别的源的，去源管理换过来"是**假话**：
 * 用户本来就站在那颗源上，换源管理什么也换不出来。那一档只能说"暂时认不出"。
 */
internal const val DESKTOP_HISTORY_SOURCE_NOT_LOADED_NOTE: String = "当前源的名字不在手，这一行暂时判不了是不是当前源"
/**
 * 库里那一行自己没写源（`source_name` 是空）那一档的说明。
 *
 * 这一档与「别的源」不是一件事：那一句把责任推给用户"你换错源了"，而这里是**那一行本来就残缺**
 * （手工导进来的库、或更早那版写空的行）。念成"别的源"会让人去换一颗根本无关的源。
 */
internal const val DESKTOP_HISTORY_ROW_SOURCE_MISSING_NOTE: String = "库里这一行没写是哪颗源的 ⇒ 打不开"

/**
 * 按下某一行的那一本时，该拿哪一颗源去 `load`；判不了时交回 `null`（调用方**不许**猜一颗）。
 *
 * ⚠️ 这里刻意**不**直接交回 `record.sourceName`：那一列里手机写的是**显示名**（"拷贝漫画"），
 * 而 `EngineSession.load()` 认的是**脚本键**（`copy_manga`）—— 拿显示名去装载就撞
 * `装载后取不到 source key`，屏上表现为"从历史点进去，一本都打不开"。这一格正是
 * 接门时最容易写错的那一笔（看起来完全合理：那一行自己写着是哪颗源，就按它装载）。
 *
 * 而两档命中（键或显示名）说的都是"**这一行与当前那颗源同轨**"，所以要装载的就是当前那颗键。
 * 这一颗是「历史 → 详情」那条路上**唯一**的取键处：历史那一屏的行与首页那一排历史卡共用它
 * （两处各写一遍就会漂 —— 与"打码判据只在壳里问"同一条纪律）。
 */
internal fun desktopComicHistoryOpenSourceOf(
    record: HistoryRecord,
    currentSourceKey: String,
    currentSourceName: String?,
): String? = if (
    desktopComicHistoryRowGate(record, currentSourceKey, currentSourceName ) == DesktopComicHistoryRowGate.OPEN
) {
    currentSourceKey
} else {
    null
}

/**
 * 「读到第几话第几页」那一句。算式逐字照对岸 `progressDescription()`：
 * 两处都从 1 数起（人话），各带一枚 `>= 1` 的门，两截之间 ` - `，都关着时整句是空串。
 */
internal fun desktopComicHistoryProgressText(record: HistoryRecord): String {
    val ep = record.lastChapterIndex + 1
    val page = record.lastPageIndex + 1
    return buildString {
        if (ep >= 1) append("第 $ep 话")
        if (page >= 1) {
            if (isNotEmpty()) append(" - ")
            append("第 $page 页")
        }
    }
}

/** 某一行在「阅读历史」那一屏上属于哪一档 —— 三档按不动各有各的一句话，不许塌成一档。 */
internal enum class DesktopComicHistoryRowGate {
    /** 与当前源同轨：给点击。 */
    OPEN,

    /** 确实是别颗源记的那一行：按不动 + 那句"去源管理换过来"。 */
    OTHER_SOURCE,

    /** 源清单还没读回来，显示名那一档无从可比：**不许**说成"别的源"。 */
    SOURCE_NOT_LOADED,

    /** 库里那一行自己没写源（空源名）：按不动，但那不是"别的源"。 */
    ROW_SOURCE_MISSING,
}

/**
 * 那一行开不开得动 + 开不动时**是哪一种**开不动 —— 「这一行是不是当前源」这件事在桌面上**只有这一处**判。
 *
 * ## 口径从批 82.8 起一字未改
 *
 * 与对岸 `resolveSourceKey` 同轨 —— 库里那一列**两端写的不是同一种东西**（手机写显示名、桌面写键），
 * 所以键与显示名任一 ignoreCase 命中都算同一颗源；两档都不中才是别源的。
 * `sourceName` 是那颗源的显示名，名字表没命中时递空串：空串**不放行**（先挡库里那一行为空，
 * 再让空显示名去比会把"没名字"读成"什么行都匹配"）。
 *
 * ## 把它的形状从 `Boolean` 换成四档，另加一档
 *
 * 换而不新增一颗，是因为「阅读历史那一屏」与「首页那一排历史卡」判的是**同一件事**：各留一条算式
 * 就是两条轨（与"打码判据只在壳里问"同一条纪律）。而屏上要念的不止"开不开得动"，还有
 * "**为什么**开不动"，布尔表达不出来（下面 [SOURCE_NOT_LOADED] 那一档就是撞出来的第三种形状）。
 *
 * `sourceName == null` 是"名字表还没在手"而**不是**"这一颗源没有名字"，所以它比空串多一档：
 * 键那一档照旧认得出（那一档不依赖名字表），只有靠显示名才认得出的那几行交回 [SOURCE_NOT_LOADED]
 * —— 那一档**不许**塌进 [OTHER_SOURCE]：把"我还没读到名字表"念成"这是别的源的行"，
 * 用户会跑去源管理换一颗本来就在用的源。
 *
 * ⚠️ 旧的 `Boolean` 那一颗（`desktopComicHistoryRowCanOpen`）**删掉而不是留着当包装**：
 * 屏与壳都换成问这一颗之后，那颗在 main 里就零调用点了 —— 那正是本仓记过一整轮的
 * 「有逻辑没有消费者」那一族，而一把只剩测试在叫的算式，等于两条轨里少了一条但没人发现。
 * 原来那七条判据（键档 / 显示名档 / 两档大小写 / 别源不吃 / 空源名两档不放行）
 * 由 `DesktopComicHistoryScreenTest` 改成点这一颗而**一条没减**（改成断言哪一档，比断言真假更准）。
 */
internal fun desktopComicHistoryRowGate(
    record: HistoryRecord,
    sourceKey: String,
    sourceName: String?,
): DesktopComicHistoryRowGate = when {
    record.sourceName.isBlank() -> DesktopComicHistoryRowGate.ROW_SOURCE_MISSING
    record.sourceName.equals(sourceKey, ignoreCase = true) -> DesktopComicHistoryRowGate.OPEN
    sourceName == null -> DesktopComicHistoryRowGate.SOURCE_NOT_LOADED
    record.sourceName.equals(sourceName, ignoreCase = true) -> DesktopComicHistoryRowGate.OPEN
    else -> DesktopComicHistoryRowGate.OTHER_SOURCE
}

/** 那一档上屏念哪一句（`OPEN` 交回 `null` = 那一行什么都不多摆）。 */
internal fun desktopComicHistoryRowGateNote(gate: DesktopComicHistoryRowGate): String? = when (gate) {
    DesktopComicHistoryRowGate.OPEN -> null
    DesktopComicHistoryRowGate.OTHER_SOURCE -> DESKTOP_HISTORY_OTHER_SOURCE_NOTE
    DesktopComicHistoryRowGate.SOURCE_NOT_LOADED -> DESKTOP_HISTORY_SOURCE_NOT_LOADED_NOTE
    DesktopComicHistoryRowGate.ROW_SOURCE_MISSING -> DESKTOP_HISTORY_ROW_SOURCE_MISSING_NOTE
}

/**
 * stdout 那一行：这一屏摆了几行、库开不开得了。行数由调用方从真状态现取。
 *
 * `名=` 那一格**三档各有念法**（加第三档）：`名=禁漫天堂` = 名字在手；`名=` = 名字表
 * 里没有这一颗（退化到只按键认）；`名=没在手` = 那一趟源清单还没到货。三档在屏上的表现
 * 是"手机写的那几行点不点得动"，而读数若把后两档塌成同一格，取证的人就分不出"这颗源没名字"与
 * "名字表还没到"—— 前者去改脚本，后者只是进屏进得太快。
 */
internal fun desktopComicHistoryScreenReading(
    sourceKey: String,
    sourceName: String?,
    rowCount: Int,
    note: String?,
): String {
    val nameCell = if (sourceName == null) "名=没在手" else "名=$sourceName"
    val head = "D_历史 屏 源=$sourceKey $nameCell 行=$rowCount"
    return if (note == null) head else "$head 因=${note.replace('\n', ' ').replace('\r', ' ')}"
}
