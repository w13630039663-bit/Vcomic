package com.venera.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.venera.engineprobe.DesktopJsHost
import com.venera.engineprobe.EngineSession
import com.venera.compose.data.db.HistoryDao
import com.venera.compose.data.db.LocalFavoritesManager
import com.venera.desktop.platform.DesktopDatabasePorts
import com.venera.desktop.gallery.ui.DesktopComicFavoriteOutcome
import com.venera.desktop.gallery.ui.desktopAddComicFavorite
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

internal val APP_TITLE = "VComic（漫画与图库）"

/**
 * 桌面侧收藏管理器的唯一引用：第一次用到才取（取用即建两棵库，时机与 Android 侧
 * `getInstance(context)` 一致），留着它只为关窗时能按正确次序关（见 `closePersistenceInOrder`）。
 */
internal var favoritesManagerRef: LocalFavoritesManager? = null

internal fun favorites(): LocalFavoritesManager =
    favoritesManagerRef
        ?: LocalFavoritesManager.getInstance(DesktopDatabasePorts.PLATFORM).also { favoritesManagerRef = it }

/**
 * 阅读历史那颗件（`comic_history`）。与 [favorites] 同一口径**第一次用到才取**（两者都只经
 * `DesktopDatabasePorts.PLATFORM` 这一颗端口要库，别的来源一概不认）。
 *
 * 这里**不留引用**：`HistoryDao.getInstance` 自己缓存（companion 里那枚 `INSTANCE`），
 * 而关库那一串是按端口走的（`closePersistenceInOrder`），不是按这颗件走的。
 */
internal fun history(): HistoryDao = HistoryDao.getInstance(DesktopDatabasePorts.PLATFORM)

/**
 * `FavoriteDao` 与 `HistoryDao` 同一形状：`getInstance` 自己在 companion 里缓存，
 * 这里**不留引用**（留了就会与那颗缓存脱钩）。
 */

/** 自建空夹名单的 prefs 键（macOS 皮批）：stringset 存名字，删空夹才不"建完就消失"。 */
internal const val CUSTOM_FAV_FOLDERS_KEY = "pref_desktop_fav_custom_folders"

internal fun customFavoriteFolderStore(paths: com.venera.compose.data.platform.PathProvider) =
    com.venera.compose.data.platform.JsonKeyValueStore(
        com.venera.compose.data.platform.PreferenceKeys.PREFS_NAME,
        paths,
    )

internal fun readCustomFavoriteFolders(paths: com.venera.compose.data.platform.PathProvider): List<String> =
    runCatching { customFavoriteFolderStore(paths).getStringSet(CUSTOM_FAV_FOLDERS_KEY).orEmpty().toList() }
        .getOrDefault(emptyList())

internal fun addCustomFavoriteFolder(paths: com.venera.compose.data.platform.PathProvider, name: String) {
    if (name.isBlank()) return
    val store = customFavoriteFolderStore(paths)
    val next = readCustomFavoriteFolders(paths).toMutableSet()
    if (!next.add(name)) return // 重名 = 不动（静默，屏侧不给建第二颗同名夹）
    store.put(CUSTOM_FAV_FOLDERS_KEY, next)
}

internal fun removeCustomFavoriteFolder(paths: com.venera.compose.data.platform.PathProvider, name: String) {
    val store = customFavoriteFolderStore(paths)
    val next = readCustomFavoriteFolders(paths).toMutableSet()
    if (next.remove(name)) store.put(CUSTOM_FAV_FOLDERS_KEY, next)
}
internal fun favorite(): com.venera.compose.data.db.FavoriteDao =
    com.venera.compose.data.db.FavoriteDao.getInstance(DesktopDatabasePorts.PLATFORM)

/** 追更账本的进程内单例；与 [favorite] 同一静态口口径。 */
internal fun comicFollowDao(): com.venera.compose.data.db.ComicFollowDao =
    com.venera.compose.data.db.ComicFollowDao.getInstance(DesktopDatabasePorts.PLATFORM)

/**
 * `--autofav` 的取证链路：**不碰 UI**，自己在 IO 上走完"装载源 → 探索页 → 第一张卡"。
 *
 * 上一版这一跑依赖根组合里那颗 `cards` 状态 —— 而那颗状态的唯一用途是喂给从未存在过的布局。
 * 于是这个开关在做的其实是"为了让某个变量被写进去，先把 30 张封面拉下来"。
 * 取证自己会取它需要的那一张，`engine.close()` 也在同一份 `finally` 里，不会漏。
 *
 * @return 退出码：0 = 写链完好（含"这本已经在里面了"）；1 = 取卡失败或写失败。
 */
internal fun runAutofavEvidence(
    assetDir: File,
    dataDir: File,
    proxy: String?,
    sourceKey: String,
    seedCookies: List<String>,
): Int {
    val outcome = runBlocking {
        withContext(Dispatchers.IO) {
            runCatching {
                val host = DesktopJsHost(assetDir, dataDir, proxy, seedCookies)
                val engine = EngineSession(host, assetDir)
                try {
                    engine.load(sourceKey)
                    engine.explore(1).sections.firstOrNull()?.comics?.firstOrNull()
                } finally {
                    engine.close()
                }
            }
        }
    }
    val first = outcome.getOrNull()
    if (first == null) {
        println(
            "D_收藏 失败：源没回任何卡片，没东西可收" +
                "（${outcome.exceptionOrNull()?.message?.take(200) ?: "无网络层错误"}）",
        )
        return 1
    }

    // 按的是屏上那颗「收藏」同一枚动作（`gallery/ui/DesktopComicFavorite.kt`）：
    // 两处各写一遍的话，种子跑通的那条路就不是用户按的那条路（判据 ④ 数出现次数）。
    val favoriteOutcome = runBlocking { desktopAddComicFavorite(::favorites, sourceKey, first) }
    val total = runCatching { runBlocking { favorites().getAllComics().size } }
    total.fold(
        onSuccess = { println("D_收藏 命中=$it") },
        onFailure = {
            println("D_收藏 读回失败 ${it::class.java.simpleName}: ${it.message?.take(200)}")
        },
    )
    // 退出码判据：读回成功，且这一笔不是"没成"（"这本已经在里面了"也算写链完好）。
    return if (total.isSuccess && favoriteOutcome !is DesktopComicFavoriteOutcome.Failed) 0 else 1
}

/** 无人值守截图前给 UI 的落地时间。取封面是异步的，太早截会拿到半屏的占位块。 */
internal const val SELF_SHOT_SETTLE_MS = 2500L

/** 抬窗之后**再等一拍**才截：topmost 落到系统那边、合成器把这扇窗真压上来，都需要一点时间 */
internal const val RAISE_SETTLE_MS = 250L

/**
 * 失焦多久之后做一次空闲回收。
 *
 * 不是给人看的延时：切走的头几秒常是「去别的窗口复制个链接就回来」，那么早回收等于白做一次 full GC。
 */
internal const val IDLE_RECLAIM_DELAY_MS = 45_000L

/**
 * 关持久层的次序（5a 复审点名）：**先 manager.close()（有界等待在飞刷新，15s 上界），
 * 再 DesktopDatabasePorts.close()**——反序会让在飞的收藏刷新在下一次取用时撞上"已退役"，
 * 抛出落进管理器自己的协程 scope，变成 R25 那类 `database connection closed` 噪音。
 * 两步各自的失败原样打痕，不咽。
 */
internal fun closePersistenceInOrder() {
    favoritesManagerRef?.let { manager ->
        runCatching { manager.close() }.onFailure {
            println("D_关闭 收藏管理器未关净：${it::class.java.simpleName}: ${it.message?.take(200)}")
        }
    }
    runCatching { com.venera.compose.data.db.ComicFollowDao.closeIfInitialized() }.onFailure {
        println("D_关闭 追更账本未关净：${it::class.java.simpleName}: ${it.message?.take(200)}")
    }
    runCatching { DesktopDatabasePorts.close() }.onFailure {
        println("D_关闭 桌面接线关闭失败：${it::class.java.simpleName}: ${it.message?.take(200)}")
    }
}
