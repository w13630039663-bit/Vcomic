package com.venera.desktop.gallery.data

import com.venera.desktop.data.desktopEngineCall
import com.venera.engineprobe.DesktopCookieJar
import com.venera.engineprobe.EngineSession
import java.io.File

/**
 * 手填 cookie 那一形的**跑一趟**（批 88·下半；接进活渲染路）。
 *
 * ## 这一趟的形状：借**常驻那颗**引擎，不自己造第二颗
 *
 * 批 88 那一版是「起宿主 → 跑 → 关宿主」。今天桌面上已经有一颗**跟窗口同寿命**的常驻引擎
 * （探索/详情/阅读器/搜索/源编辑共用它，见 `DesktopEngineCall`），再造一颗的后果不是慢：
 * 两颗 `DesktopCookieJar` 指向**同一颗文件**（`DesktopCookieJar.fileIn(dataDir)`），
 * 各自 `init` 读一份、各自 `persist` 整份覆盖 —— 登录那一趟写进盘上，常驻那颗内存里的旧份
 * 会在下一次它自己 persist 时把刚登的凭据**整笔盖掉**。那是"看着登上了而凭据被另一条轨擦掉"，
 * 与本仓反复记的两轨同族。
 *
 * ⇒ 现在这颗件只跑「装那颗源 + 交源自己校验 + 回核」三跳，引擎由调用方递进来，
 * **锁也由调用方包**（那颗件不碰 `desktopEngineCall`，它不是 UI 也不是调用点）。
 * 回核那一跳仍是另起一颗**只读**实例去读盘：批 88 那条「拿内存里那份作证等于自己给自己签字」一字未改。
 *
 * ## 回核为什么读**盘**而不是读刚才那颗罐
 *
 * 源回 `true` 只说明站方认了这批凭据；「重启还带不带得上」是另一件事，而要防的正是
 * 那两半不同轨（屏上念「已登录」而重启后请求空手）。所以数枚数用的是**另起一颗只读实例**去读
 * `DesktopCookieJar` 落的那份文件 —— 内存里那份是刚才那一趟写的，拿它作证等于自己给自己签字。
 *
 * ⚠️ 那颗只读实例**不进 OkHttpClient**，只借它的 `load()` 解析盘上那份再数 ——
 * 递进去的 dataDir 与常驻那颗同一个，所以读到的就是它写的那份（唯一的出处是
 * `DesktopCookieJar.fileIn`，文件名不许在这里重拼）。
 */
internal data class DesktopCookieOutcome(
    /** 源那句 `validate` 的回话（`=== true` 才算过，与对岸 `JsComicSource.validateCookies` 同一判式）。 */
    val ok: Boolean,
    /** 没过时屏上要念的那一句（源自己的说法优先，桌面不重述判据）。 */
    val note: String,
    /** 脚本自己那份字段名单（回核数的是这几枚名字落了几个）。 */
    val fields: List<String>,
    /** 盘上那份里每个名字落了几枚（键 = cookie 名）。 */
    val onDisk: Map<String, Int>,
)

internal object DesktopCookieLogin {

    /**
     * 用户敲的那几枚 → 源那句 `validate(values)` 的实参表：**按名单次序**摊成一串。
     *
     * 为什么单独一颗：顺序是源脚本认的口径（`fields[i]` 与 `values[i]` 一一对应，ehentai 的
     * `validate` 就按 `fields[i]` 取名字）。屏上格子是竖着排的，而字典无序 —— 直接从
     * `mapValues` 那种形状里取值就会把四枚装成任意一套，源那边看着像"填了但不对"。
     */
    fun payload(fields: List<String>, draft: Map<String, String>): List<String> =
        fields.map { draft[it].orEmpty() }

    /**
     * 把「源的回话」与「盘上那份」拼成结果。**问引擎的那两跳不住在这颗件里** ——
     * 由调用点各占一行写在 `desktopEngineCall` 里面（§136 那把尺按**行首形状**数「问引擎」的每一跳，
     * `val res = e.loginWithCookies(` 那种藏在赋值右侧它看不见 ⇒ 藏 = 漏判 = 那把账尺失牙。
     * 把两跳包进本件同样是在**给那把尺造盲区**，所以宁可在调用点多写两行）。
     */
    fun outcomeOf(login: EngineSession.CookieLogin, onDisk: Map<String, Int>): DesktopCookieOutcome =
        DesktopCookieOutcome(login.ok, login.note, login.fields, onDisk)

    /**
     * 回核那份：**另起一颗只读实例**去数盘上落了几枚（批 88 那条「拿内存里那份作证等于
     * 自己给自己签字」一字未改）。落点只从 `DesktopCookieJar.fileIn` 拿，这里不重拼文件名 ——
     * 文件名漂一格的结果是「屏上回核读的那份不是宿主写的那份」，而那一形在屏上看着完全正常。
     */
    fun tallyOnDisk(dataDir: File, fields: List<String>): Map<String, Int> =
        DesktopCookieJar(DesktopCookieJar.fileIn(dataDir)).tally(fields)

    /**
     * 屏上那一句。三档两两不同，而**中间那一档是存在的理由**：
     * 源认了而盘上一枚没落 = 重启就丢，那一句绝不许被"登录成功"盖掉。
     *
     * ⚠️ 这一句要上屏，所以里面不许有 `D_` 那类内部读数、也不许有实现黑话。
     */
    fun screenNote(outcome: DesktopCookieOutcome): String {
        val landed = outcome.onDisk.values.sum()
        return when {
            !outcome.ok -> "没登上：${outcome.note}"
            landed == 0 -> "源认了这批凭据，可本机一份都没落下来 —— 重启就带不上，先别当已经登录"
            else -> "已登录：这批凭据落在本机那份里共 $landed 枚，重启也带得上"
        }
    }

    /** stdout 那一行：登录态、名单、盘上每枚名字各落了几颗，全部从状态现取。 */
    fun reading(sourceKey: String, outcome: DesktopCookieOutcome): String {
        val detail = outcome.fields.joinToString(",") { name -> "$name=${outcome.onDisk[name] ?: 0}" }
        return "D_手填登录 源=$sourceKey 源答=${if (outcome.ok) "过" else "没过"} " +
            "名单=${outcome.fields.size} 盘上=[$detail] 计=${outcome.onDisk.values.sum()} logged=${if (outcome.ok) 1 else 0}"
    }
}
