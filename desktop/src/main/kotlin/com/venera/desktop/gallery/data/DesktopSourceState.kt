package com.venera.desktop.gallery.data

import com.venera.compose.data.platform.JsonKeyValueStore
import com.venera.compose.data.platform.PathProvider

/**
 * 「这台机器上哪几颗源被显式开启」落在盘上的那一格（翻向）。
 *
 * ## 从前这一格记的是停用，现在是**启用**（同一颗键的方向翻了）
 *
 * 旧形状：盘上记「停用的那些」（默认全开 ⇒ 34 颗一屏齐亮）。
 * 新形状：盘上记「用户**亲手开过**的那些」（默认只开随包四颗）。翻向的理由是主人的裁决
 * 「现在的源管理大部分连登录都没，这种应该是手动开启而不是全部开的」——
 * 成人向、需要凭据的站**默认裸奔**不是合理的开箱姿态；安卓新版随包也**只预装四颗**
 * （`ComicSourceManager.BUNDLED_DEFAULT_FILES` = baozi / copy_manga / jm / manga_dex），
 * 其余都要用户自己去装 —— 桌面把 34 颗全亮着，等于比安卓还松。
 *
 * ## 迁移旧键值
 *
 * 旧版那份 `disabled_sources`（翻转语义的旧账，键名已随旧形状一起删掉，盘上的旧值留在原地
 * 不再消费）读不成新账：旧形状是"默认全开、记少数停用"，新形状是"默认四颗、记少数开启"——
 * 两本账没法互推（旧表里没开过的源在新账里既可能是"用户主动没开"也可能是"用户从来没考虑过"）。
 * 这条不是"丢数据"，是"语义换了，旧账作废"，写进读数里。
 *
 * ## 为什么这一份是桌面本地而不是共享表（现量过两份存储，不是凭印象）
 *
 * Android 的 `comic_source` 表是"手机上装过哪些源"的元数据（图标、排序、config_json），
 * 这一份是"这台机器上哪几颗参与选源"。并成一颗等于把 A 站的凭据发给 B 站 ⇒ **刻意不同轨**，
 * 并且把这句话写进读数里，别让它看起来像漏了同步。
 *
 * 存储件复用既有的 [JsonKeyValueStore]（`prefs/venera_desktop_sources.json`）：它的三条行为判据
 * （文件不存在=空表、半截 JSON 抛而不当"没配置"、写走临时文件再整体替换）正是这一格要的，
 * 自己另开一条写文件的路只会少掉那三条（`File.renameTo` 那一课就是这条路）。
 *
 * ⚠️ `public` 是**被包名决定的**，不是随便挑的：集成方 `com.venera.desktop.VeneraDesktop` 要把
 * 它交给 `DesktopComicEngineConfig`（那颗也是 public），而 Kotlin 不许 public 函数暴露 internal 参数类型。
 * 这一层其余的器件（`DesktopSourceCatalog` / `DesktopSourceSelection`）仍是 `internal`。
 */
class DesktopSourceState(paths: PathProvider) {

    private val store = JsonKeyValueStore(PREFS_NAME, paths)

    /**
     * 用户**亲手开启**的那些源脚本 key。
     *
     * 全新数据（盘上没这一格）= 交回 null，由调用点落回 [DEFAULT_ENABLED] ∩ keys ——
     * 这里刻意**不读旧键**（语义翻向，旧账作废，理由见类注释）。
     * 空集是合法的"用户手动把默认之外的源全关了"的落点（真写过空数组），与 null 分得开。
     *
     * ⚠️ 为什么要有那枚伴生 `init` 标志：`getStringSet` 对「没这个键」与「键存在但写了空数组」
     * 交回的都是空集（接口注释明令不许让调用方到处 `?: emptySet()` 漂），光读值分不出这两档。
     * 而翻向后这一格恰好是**数据**：没动过开关 = null（落默认四颗）、动过 = 集合本身（可能是空）。
     * 标志与数据必须**一次写**（[setEnabledKeys] 走 `putAll`），分两次写会留下"数据落了、标志没落"
     * 的半截窗口 —— 那等于默认档永远压着用户账。
     */
    fun enabledKeys(): Set<String>? =
        if (store.getBoolean(KEY_ENABLED_INIT, false)) store.getStringSet(KEY_ENABLED) else null

    /**
     * 整份交回而不是增量记：空集也要真写回一份空数组，并与 `init` 标志同一次落盘。
     */
    fun setEnabledKeys(keys: Set<String>) {
        store.putAll(mapOf(KEY_ENABLED_INIT to true, KEY_ENABLED to keys.sorted().toSet()))
    }

    /**
     * 用户拖出来的站点顺序（key 的有序表）；`null` = 从没拖过，清单落字母序。
     *
     * 为什么不复用 [enabledKeys] 那种 Set：顺序本身就是数据，Set 丢序。
     * 为什么用逗号串而不是 JSON 数组：key 是脚本文件名去扩展名（`[A-Za-z0-9_]+`），
     * 不含逗号，一颗 `split/join` 就够，不必为五个字节引一层解析。
     */
    fun orderKeys(): List<String>? = store.getString(KEY_ORDER, null)
        ?.split(',')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }

    /** 整份交回；空表也真写（与 [setEnabledKeys] 同一条"空集也要落"的纪律）。 */
    fun setOrderKeys(keys: List<String>) {
        store.put(KEY_ORDER, keys.joinToString(","))
    }

    companion object {
        /** 文件名自证是桌面本地那一份（不是共享表）。 */
        const val PREFS_NAME = "venera_desktop_sources"

        /** 新形状（显式开启清单）的键名。 */
        private const val KEY_ENABLED = "enabled_sources"

        /** 伴生标志：`true` = 用户真的动过开关（数据键的"存在但为空"由此与"从没写过"分开）。 */
        private const val KEY_ENABLED_INIT = "enabled_sources_init"

        /** 站点墙拖出来的顺序（逗号分隔的 key 串）。 */
        private const val KEY_ORDER = "source_order"

        /**
         * 默认开启的那几颗 —— **逐字对齐**对岸 `ComicSourceManager.kt` 的 `BUNDLED_DEFAULT_FILES`
         * （baozi / copy_manga / jm / manga_dex；键 = 脚本文件名去 .js）。
         * 对岸新增第五颗随包默认源而桌面没跟上时，由 [DEFAULT_ENABLED] 的回核尺子咬（见测试）。
         */
        val DEFAULT_ENABLED: Set<String> = setOf("baozi", "copy_manga", "jm", "manga_dex")

        /**
         * 这一台此刻该算「开着」的集合：有用户账用用户的账，没账落默认档 ∩ 清单。
         * 交集在这里做而不是在调用点做，是为了让屏上/选源两处吃到**同一颗件**的结果（单一条口径）。
         */
        fun effectiveEnabled(keys: List<String>, stored: Set<String>?): Set<String> =
            stored ?: (DEFAULT_ENABLED intersect keys.toSet())

        /** stdout 那一行：开启几颗、默认档是不是还压着（null 那一格）、口径只影响桌面。 */
        fun reading(enabled: Set<String>, fromDefault: Boolean, total: Int): String =
            "D_源 开启=${enabled.size} 档=${if (fromDefault) "默认(没动过开关)" else "用户账"} 清单=$total " +
                "口径=桌面本地（与共享表 comic_source 刻意不同轨；旧 disabled 键语义已作废）"
    }
}
