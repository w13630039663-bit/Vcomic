package com.venera.desktop.data

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.venera.compose.data.db.CoreDbSchema
import com.venera.compose.data.db.CoreTableBackup
import com.venera.compose.data.db.DatabasePorts
import com.venera.compose.data.db.parseJsonObject
import com.venera.compose.data.platform.SqlDatabase
import com.venera.compose.security.guard.GuardRulePattern
import com.venera.desktop.gallery.data.desktopComicAtomicWriteTo
import com.venera.desktop.platform.desktopPickOpenFile
import com.venera.desktop.platform.desktopPickSaveFile
import java.io.File
import java.awt.Frame
import java.awt.GraphicsEnvironment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 桌面「备份」那一屏的纯件 + 编排（只接**导出**那一半）。
 *
 * ## 为什么这一颗只做三张核心表，不做整包 `.venera`
 *
 * 对岸那颗 `sync/BackupManager.kt` 是 ③ 档：`CacheDirs.inCache(context.cacheDir, …)`、
 * `ZipOutputStream(BufferedOutputStream(FileOutputStream(...)))`、`GalleryFollows` /
 * `GalleryFavorites` 两颗 singleton（那两棵要 Bitmap 与协程 scope）、`FavoriteImagesManager`
 * 落盘解包 —— 一整套 Android 宿主 + 应用级单例，桌面编不过、也不该为凑功能造空壳。
 *
 * 桌面**能干净接住**的是 `data/db/CoreTableBackup`：`exportTable(db, "comic_history" /
 * "reading_stats" / "content_guard_rules")` 三张表 ↔ JSON 数组那一栏，`desktop/build.gradle.kts`
 * 里 `data/db` 整目录进桌面编译面（`CoreTableBackupTest` 早已跑绿）。所以这一批交的是**桌面
 * 本机的一份 JSON 快照**：把三张表拼进一个 JSON object 里存到一个文件；恢复那一步在 §136 接上。
 *
 * ## 这一份 JSON **不冒充** `.venera`
 *
 * 对岸那个归档是 ZIP（`meta.json` + 多份 `*.json` 成员，键序由 `BackupManager` 定），
 * 桌面这里交的是单文件 JSON object（自己那套形状），**不承诺手机能读、不承诺跨端互读**。
 * 屏上那句话也这么写：「本机备份 · 手机读不了 —— 这一份只用于本机还原」。假装能同步比明说
 * 只覆盖三张表更坏（手册 §22.3 反向应用）。
 *
 * ## 三族键与 meta 计数
 *
 *  - `meta.version` = 一颗桌面侧的口径数（对岸那颗 `FavoriteBackupRows.CURRENT_VERSION`
 *    是给整包 ZIP 用的、语义不同，这里不搬那枚常量，桌面自己从 1 起）；
 *  - `meta.counts` = `{history, stats, guard}` 三个整数 —— 与三张数组的实际条数在
 *    导出的**同一趟**里现算，恢复时用它挡住"数组被截了一半但字段名都对"那种半截文件。
 */

/** 桌面本机备份的**版本号**（键 `meta.version`）。只有一颗 1，将来字段变化才往上加。 */
internal const val DESKTOP_BACKUP_VERSION: Int = 1

/** 三族数组的固定键名（一处事实源，别在编码与解码两处各写一遍）。 */
internal const val DESKTOP_BACKUP_KEY_HISTORY: String = "comic_history"
internal const val DESKTOP_BACKUP_KEY_STATS: String = "reading_stats"
internal const val DESKTOP_BACKUP_KEY_GUARD: String = "content_guard_rules"

/**
 * 备份文件里的三族条数摘要（屏上念「存了 N 条历史 / M 条阅读 / K 条屏蔽」那一行吃的就是这颗）。
 *
 * 计数住在**同一颗 data class** 里而不是散在 JSON 树上：三处都会数，两处对不齐就红
 * （与 §115 那把「页在不在盘上」唯一尺子的口径同族）。
 */
data class DesktopBackupCounts(val history: Int, val stats: Int, val guard: Int) {
    val isEmpty: Boolean get() = history == 0 && stats == 0 && guard == 0
}

/** 一份解码回来的备份：三族条数摘要 + 三族 JSON 数组本身（供 §136 恢复那一半吃）。 */
data class DesktopBackupSnapshot(
    val version: Int,
    val counts: DesktopBackupCounts,
    val history: JsonArray,
    val stats: JsonArray,
    val guard: JsonArray,
)

/**
 * 三族 JSON → 一份可写盘的文件文本。键序是**刻意的**：`meta` 在最前（人和机器都先看头），
 * 三族数组按 `comic_history → reading_stats → content_guard_rules`（与 `SchemaSql`
 * 建表语句的顺序一致）。`LinkedHashMap` 保序，与 §114 那颗 `jsonObject(fields)` 同法。
 */
internal fun desktopBackupEncodeJson(
    version: Int,
    timestamp: Long,
    history: JsonArray,
    stats: JsonArray,
    guard: JsonArray,
): String {
    val obj = JsonObject()
    val meta = JsonObject()
    meta.addProperty("version", version)
    meta.addProperty("timestamp", timestamp)
    meta.addProperty("app", "Venera Desktop")
    // counts 那一子对象与数组实际条数**同一趟算**（不各算各的，见 DesktopBackupCounts 头注）。
    val counts = JsonObject()
    counts.addProperty("history", history.size())
    counts.addProperty("stats", stats.size())
    counts.addProperty("guard", guard.size())
    meta.add("counts", counts)
    obj.add("meta", meta)
    obj.add(DESKTOP_BACKUP_KEY_HISTORY, history)
    obj.add(DESKTOP_BACKUP_KEY_STATS, stats)
    obj.add(DESKTOP_BACKUP_KEY_GUARD, guard)
    return obj.toString()
}

/**
 * 备份文件的文本 → 一份 [DesktopBackupSnapshot]。
 *
 * 严格 parse 走共享那颗 [parseJsonObject]（`JsonReader.isLenient = false`），非 object / 尾随
 * 内容都会抛并带原文开头。这一颗还多挡三档：
 *  - 三族键缺一个或类型不对 ⇒ 抛（念「文件不像桌面备份」）；
 *  - `meta.counts` 与实际数组长度**对不上** ⇒ 抛（半截文件那种形状，字段名都对而内容短一截）；
 *  - `meta.version` 大于 [DESKTOP_BACKUP_VERSION] ⇒ 抛（不许拿未来的备份往今天的库里灌）。
 */
internal fun desktopBackupDecodeJson(text: String): DesktopBackupSnapshot {
    val obj = parseJsonObject(text)
    val meta = obj.get("meta")?.takeIf { it.isJsonObject }?.asJsonObject
        ?: throw IllegalStateException("备份文件里没有 meta 那一段")
    val version = meta.get("version")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt
        ?: throw IllegalStateException("备份文件里的 meta.version 不是整数")
    if (version > DESKTOP_BACKUP_VERSION) {
        throw IllegalStateException("备份文件的版本 $version 比桌面这一颗 $DESKTOP_BACKUP_VERSION 新 ⇒ 不认")
    }
    val countsObj = meta.get("counts")?.takeIf { it.isJsonObject }?.asJsonObject
        ?: throw IllegalStateException("备份文件里没有 meta.counts 那一段")
    fun readCount(name: String): Int = countsObj.get(name)
        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt
        ?: throw IllegalStateException("备份文件里 meta.counts.$name 不是整数")
    val declared = DesktopBackupCounts(
        history = readCount("history"),
        stats = readCount("stats"),
        guard = readCount("guard"),
    )
    val history = obj.get(DESKTOP_BACKUP_KEY_HISTORY)
        ?.takeIf { it.isJsonArray }?.asJsonArray
        ?: throw IllegalStateException("备份文件里没有 `$DESKTOP_BACKUP_KEY_HISTORY` 那一族")
    val stats = obj.get(DESKTOP_BACKUP_KEY_STATS)
        ?.takeIf { it.isJsonArray }?.asJsonArray
        ?: throw IllegalStateException("备份文件里没有 `$DESKTOP_BACKUP_KEY_STATS` 那一族")
    val guard = obj.get(DESKTOP_BACKUP_KEY_GUARD)
        ?.takeIf { it.isJsonArray }?.asJsonArray
        ?: throw IllegalStateException("备份文件里没有 `$DESKTOP_BACKUP_KEY_GUARD` 那一族")
    // 计数与数组长度必须一致（一处对不上就是半截/手工改过的文件，宁可拒收）
    if (history.size() != declared.history) {
        throw IllegalStateException("备份文件里 comic_history 数组有 ${history.size()} 行，meta.counts 却念 ${declared.history}")
    }
    if (stats.size() != declared.stats) {
        throw IllegalStateException("备份文件里 reading_stats 数组有 ${stats.size()} 行，meta.counts 却念 ${declared.stats}")
    }
    if (guard.size() != declared.guard) {
        throw IllegalStateException("备份文件里 content_guard_rules 数组有 ${guard.size()} 行，meta.counts 却念 ${declared.guard}")
    }
    return DesktopBackupSnapshot(version, declared, history, stats, guard)
}

/**
 * 一次读库的产物：三族 JSON 数组 + 三族条数（counts 与实际数组长度出自**同一趟**，
 * §115 "唯一一把尺"那族病 —— 两处各数一遍一定会漂）。
 */
internal data class DesktopBackupExportPayload(
    val history: JsonArray,
    val stats: JsonArray,
    val guard: JsonArray,
) {
    val counts: DesktopBackupCounts
        get() = DesktopBackupCounts(history.size(), stats.size(), guard.size())
}

/**
 * 从一颗真库读三族表（不 encode、不写盘）。表不存在时靠 [CoreDbSchema.ensureOn] 建齐
 * （与 `CoreTableBackupTest` 起手同一法），空表也能读（counts 全 0 的一份，与「读不到库」
 * 是两回事）。拆两跳的理由：真库往返判据可以直接喂一颗临时库看那三族数组，而不必碰文件 IO。
 */
internal fun desktopBackupReadTables(db: SqlDatabase): DesktopBackupExportPayload {
    CoreDbSchema.ensureOn(db)
    val history = CoreTableBackup.exportTable(db, DESKTOP_BACKUP_KEY_HISTORY)
    val stats = CoreTableBackup.exportTable(db, DESKTOP_BACKUP_KEY_STATS)
    val guard = CoreTableBackup.exportTable(db, DESKTOP_BACKUP_KEY_GUARD)
    return DesktopBackupExportPayload(history, stats, guard)
}

/** 从 payload 拼文件文本（时间戳由调用方给，判据可喂固定值断 round-trip）。 */
internal fun desktopBackupEncodePayload(payload: DesktopBackupExportPayload, timestamp: Long): String =
    desktopBackupEncodeJson(DESKTOP_BACKUP_VERSION, timestamp, payload.history, payload.stats, payload.guard)

/**
 * 桌面「备份」那一屏的**runner**：现读三族表，写盘到调用方给的路径，返回三族条数摘要。
 *
 * 与 §134 那颗读源同一条兜法：`DatabasePorts.of(null)` 会抛 ⇒ 先兜住那一跳。IO 在 `Dispatchers.IO`
 * 上跑（与备份/还原同一条纪律）；文件写到一半失败 ⇒ `runCatching` 抛回来由屏上念成因。
 * 落盘走同目录临时档 + 覆盖改名：旧备份在新档就位前一直在，写一半被杀/断电不会两份都不完整。
 */
internal suspend fun desktopBackupExportTo(path: String): Result<DesktopBackupCounts> =
    withContext(Dispatchers.IO) {
        runCatching {
            val core = DatabasePorts.of(null).core
            val db = core.reader()
            val ts = System.currentTimeMillis()
            val payload = desktopBackupReadTables(db)
            val file = File(path)
            file.parentFile?.mkdirs()
            // 不再先删旧档：删除与写入之间的窗口正是丢档的成因。临时档写完后才覆盖原路径。
            desktopComicAtomicWriteTo(file, desktopBackupEncodePayload(payload, ts).toByteArray(Charsets.UTF_8))
            // counts 与写盘的数组出自**同一趟**（payload.counts 现算）；这里不再读一次库。
            payload.counts
        }
    }

/**
 * 备份文件的**默认名**（AWT `FileDialog` SAVE 模式给的那一格，不含目录）：`venera_desktop_backup_<ts>.json`。
 * 与对岸那颗 `venera_backup_$timestamp.venera` 同族形状而后缀不同 —— 桌面这一份不是 `.venera`（
 * 那是 ZIP 归档），用 `.json` 明说它是单文件 JSON。
 */
internal fun desktopBackupDefaultFileName(timestamp: Long): String =
    "venera_desktop_backup_$timestamp.json"

/**
 * stdout 那一行取证读数：包内复测的人一眼看出「这一趟真读到三族表、真写了盘 / 真灌回了库」。
 * 导与恢两跳各带自己的计数（哪一步跑过就念哪一段，都没跑过念"还没动过"），`落点=` 只在
 * 有过一次路径时出现，`因=` 只在失败/取消那一档出现（读失败、写失败、取消合并这一档，成因各异）。
 */
internal fun desktopBackupScreenReading(
    counts: DesktopBackupCounts?,
    restore: DesktopRestoreOutcome?,
    path: String?,
    note: String?,
): String {
    val parts = mutableListOf<String>()
    if (counts != null) parts += "导=历史${counts.history}/阅${counts.stats}/屏${counts.guard}"
    if (restore != null) parts += "恢=历史${restore.history}/阅${restore.stats}/屏${restore.guardImported}"
    if (parts.isEmpty()) parts += "还没动过"
    val head = "D_备份 屏 " + parts.joinToString(" ") +
        (if (path != null) " 落点=$path" else "")
    return if (note == null) head else "$head 因=${note.replace('\n', ' ').replace('\r', ' ')}"
}

/**
 * 这台机器给不给「挑一个本机文件存起来」那条路（与 §77 那颗 [desktopCanPickLocalImage]
 * 同一族形状：先问支持不支持，不支持时**不摆那枚按钮**）。
 */
internal fun desktopCanSaveBackupFile(): Boolean = !GraphicsEnvironment.isHeadless()

/**
 * 开系统的「另存为」框，等用户挑一个路径，交回那一条完整路径；取消 ⇒ `null`。
 *
 * ## 为什么必须在 AWT 事件线程上跑
 *
 * 与 §77 那颗 `desktopPickLocalImage` 同一条纪律：`FileDialog` 是**模态**的，会吃掉调用线程
 * 直到用户点完。Compose Desktop 的默认调度把组合那一侧的点击回调已经放在 AWT 事件线程上，
 * 所以直接开；从别处进来时走 `invokeAndWait` 挪过去 —— 反过来（已在事件线程上还调
 * `invokeAndWait`）会抛 `ErrorWrongThread`（§77 那颗注释里逐字记过这一坑）。
 *
 * 与 §77 那颗**唯一的不同**：`mode = FileDialog.SAVE` 那一档；`file` 传默认名，
 * 用户可以在文件名那一格里改。取消那一档读回来 `file == null`（AWT 就是这么报），
 * 交回 null 让调用方（屏）念「没挑 ⇒ 不写盘」那一句而不假装成功。
 */
internal fun desktopPickBackupSavePath(owner: Frame?, defaultName: String): String? =
    desktopPickSaveFile(owner, "存一份本机备份", defaultName)

/**
 * 开系统的「打开」框挑一份桌面备份 JSON，交回路径；取消 ⇒ `null`。默认名不给（打开框没有默认名语义）。
 */
internal fun desktopPickBackupOpenPath(owner: Frame?): String? =
    desktopPickOpenFile(owner, "挑一份本机备份")

/**
 * 恢复跑完的三项计数：进库多少条历史 / 阅读 / 屏蔽，外加**因写法有误没收的屏蔽规则数**
 * （对岸那颗 `GuardImportOutcome` 的桌面镜像：编译不过的正则不进库，屏上要念得出这个数）。
 */
data class DesktopRestoreOutcome(
    val history: Int,
    val stats: Int,
    val guardImported: Int,
    val guardSkipped: Int,
)

/**
 * 从一份已解码的 [DesktopBackupSnapshot] 恢复进一颗库（**一个事务**灌三张表）。
 *
 * 任一张表中途失败 ⇒ [SqlDatabase.inTransaction] 整笔回滚（不留"历史灌进去了而屏蔽灌到一半炸了"
 * 那种半截状态）。屏蔽规则那条用**真的** [GuardRulePattern.compiles] 挡编译不过的正则 ——
 * `security/guard` 已在桌面编译面（`DesktopGuardPolicy.kt:107` 就在用它），不注入假 lambda。
 * 恢复语义 = "归档里写什么就是什么"（`CoreTableBackup` 逐字），走 INSERT OR REPLACE 而不走
 * `HistoryDao.saveHistory`（那条带"空封面沿用旧值"规则，与恢复是两回事）。
 */
internal fun desktopBackupRestoreInto(db: SqlDatabase, snapshot: DesktopBackupSnapshot): DesktopRestoreOutcome {
    var outcome = DesktopRestoreOutcome(0, 0, 0, 0)
    db.inTransaction {
        val h = CoreTableBackup.importHistory(db, snapshot.history)
        val s = CoreTableBackup.importStats(db, snapshot.stats)
        val g = CoreTableBackup.importGuardRules(db, snapshot.guard) { GuardRulePattern.compiles(it) }
        outcome = DesktopRestoreOutcome(h, s, g.imported, g.skipped)
    }
    return outcome
}

/**
 * 桌面「还原」那一跳的**runner**：读盘、严格解码、一个事务灌进真库，返回三项计数。
 *
 * 与导出那颗同一条兜法：`DatabasePorts.of(null)` 会抛 ⇒ `runCatching` 兜住整趟。读的是 [core.writer]
 * （要写库）。文件不存在 / 不是合法桌面备份 JSON / counts 与数组对不上 ⇒ 那三档各由
 * [desktopBackupDecodeJson] 与这里抛，屏上念成因而不改写成"已恢复 0 条"。
 */
internal suspend fun desktopBackupRestoreFrom(path: String): Result<DesktopRestoreOutcome> =
    withContext(Dispatchers.IO) {
        runCatching {
            val file = File(path)
            if (!file.isFile) throw IllegalStateException("那份备份文件不在：$path")
            val snapshot = desktopBackupDecodeJson(file.readText())
            desktopBackupRestoreInto(DatabasePorts.of(null).core.writer(), snapshot)
        }
    }

/**
 * 恢复成功那一句上屏文案（含**跳过数**那一档）：编译不过的屏蔽规则被挡掉几条要说得出，
 * 否则用户以为"全灌进去了"而其实少几条 —— 与对岸 `GuardImportOutcome` 那句同口径。
 */
internal fun desktopRestoreOutcomeNote(o: DesktopRestoreOutcome): String {
    val base = "已恢复：历史 ${o.history} 条 · 阅读 ${o.stats} 条 · 屏蔽 ${o.guardImported} 条"
    return if (o.guardSkipped > 0) "$base（另有 ${o.guardSkipped} 条屏蔽规则写法有误，没收）" else base
}

/**
 * 导出跑完那一句上屏文案（三族条数）。空快照那一档要单独念一句：库里三张表都空时，
 * 「已导出：0 条 / 0 条 / 0 条」看着像失败了，而那**确实是成功**（导了一份空留底）——
 * 与 §122 那族「筛空一夹不许沿用『还没有收藏』」同一条判法。
 */
internal fun desktopBackupCountsNote(c: DesktopBackupCounts): String =
    if (c.isEmpty) {
        "已导出：三张表都是空的（这份留底里什么都没有）"
    } else {
        "已导出：历史 ${c.history} 条 · 阅读 ${c.stats} 条 · 屏蔽 ${c.guard} 条"
    }
