package com.venera.desktop.data

import com.venera.desktop.app.favorite
import com.venera.desktop.app.DESKTOP_EXPLORE_SOURCE
import com.venera.desktop.app.removeCustomFavoriteFolder
import com.venera.compose.data.db.FavoriteRecord
import com.venera.compose.data.platform.PathProvider
import com.venera.desktop.gallery.data.DesktopCookieLogin
import com.venera.desktop.gallery.data.DesktopSourceCatalog
import com.venera.desktop.gallery.data.DesktopSourceNames
import com.venera.desktop.gallery.data.DesktopSourceOverrides
import com.venera.desktop.gallery.data.DesktopSourceSelection
import com.venera.desktop.gallery.data.DesktopSourceSettings
import com.venera.desktop.gallery.data.DesktopSourceState
import com.venera.desktop.gallery.data.desktopFavoriteRecordToWrite
import com.venera.desktop.search.DesktopComicSearchOutcome
import com.venera.desktop.sourcemanage.DesktopEdgeWebLogin
import com.venera.desktop.sourcemanage.DesktopSourceRowUi
import com.venera.engineprobe.EngineAssets
import com.venera.engineprobe.EngineSession
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 读取随包与用户导入源清单，合并运行期启用、改写、登录态及在用状态，返回有序的 UI 行列表。
 */
internal fun readDesktopSourceRows(
    assetDir: File,
    overrideDir: File?,
    sourceState: DesktopSourceState,
    seedDisabled: Set<String>,
    uiEngine: EngineSession?,
): List<DesktopSourceRowUi> {
    val bundledList = EngineAssets.listSourceKeys(assetDir).sorted()
    val bundledSet = bundledList.toSet()
    val imported = overrideDir?.let { dir ->
        File(dir, "sources")
            .listFiles { f -> f.isFile && f.extension == "js" }
            ?.map { it.nameWithoutExtension }
            ?.filter { DesktopSourceOverrides.isSafeKey(it) }
            .orEmpty()
    }.orEmpty()
    val all = (bundledList + imported).distinct()
    val saved = sourceState.orderKeys().orEmpty()
    val keys = if (saved.isEmpty()) {
        all.sorted()
    } else {
        val rank = saved.withIndex().associate { it.value to it.index }
        all.sortedWith(compareBy({ rank[it] ?: Int.MAX_VALUE }, { it }))
    }

    val names = LinkedHashMap<String, String>()
    val cookieFields = LinkedHashMap<String, List<String>>()
    val passwordLogin = LinkedHashMap<String, Boolean>()
    val webLogin = LinkedHashMap<String, Boolean>()
    val versions = LinkedHashMap<String, String>()
    val settingsCounts = LinkedHashMap<String, Int>()
    val hosts = LinkedHashMap<String, String>()

    keys.forEach { k ->
        val text = DesktopSourceNames.scriptOf(assetDir, k, overrideDir) ?: return@forEach
        DesktopSourceNames.displayNameOf(text)?.let { names[k] = it }
        cookieFields[k] = DesktopSourceNames.cookieFieldsOf(text)
        passwordLogin[k] = DesktopSourceNames.supportsPasswordLoginOf(text)
        webLogin[k] = DesktopSourceNames.supportsWebLoginOf(text)
        versions[k] = DesktopSourceNames.versionOf(text).ifBlank {
            overrideDir?.let { com.venera.desktop.gallery.data.DesktopSourceUpdater.installedVersionOf(it, k) }.orEmpty()
        }
        settingsCounts[k] = DesktopSourceSettings.definitionsOf(text).size
        hosts[k] = DesktopSourceNames.apiHostOf(text)
    }

    val overridden = overrideDir?.let { DesktopSourceOverrides.overriddenKeys(it) } ?: emptySet()
    val storedEnabled = sourceState.enabledKeys()
    val effectiveEnabled = DesktopSourceState.effectiveEnabled(keys, storedEnabled) - seedDisabled
    println(DesktopSourceState.reading(effectiveEnabled, fromDefault = storedEnabled == null, total = keys.size))
    println(
        if (overrideDir == null) {
            "D_源改写 编辑这一路是关的（改写层目录不在手）"
        } else {
            DesktopSourceOverrides.reading(overridden)
        },
    )

    val catalog = DesktopSourceCatalog.rows(
        keys = keys,
        names = names,
        enabled = effectiveEnabled,
        overridden = overridden,
        cookieFields = cookieFields,
        supportsPasswordLogin = passwordLogin,
        supportsWebLogin = webLogin,
        hosts = hosts,
    )
    return catalog.map { r ->
        val isBundled = r.key in bundledSet
        val repoId = overrideDir?.let {
            com.venera.desktop.gallery.data.DesktopSourceUpdater.repoIdOf(it, r.key)
        }
        val originLabel = com.venera.desktop.gallery.data.DesktopSourceUpdater.originLabelOf(
            isBundled = isBundled,
            overridden = r.overridden,
            repoId = repoId,
        )
        DesktopSourceRowUi(
            key = r.key,
            title = r.title,
            enabled = r.enabled,
            overridden = r.overridden,
            cookieFields = r.cookieFields,
            supportsPasswordLogin = r.supportsPasswordLogin,
            supportsWebLogin = r.supportsWebLogin,
            host = r.host,
            version = versions[r.key].orEmpty(),
            settingsCount = settingsCounts[r.key] ?: 0,
            inUse = r.key == DESKTOP_EXPLORE_SOURCE,
            loggedIn = uiEngine?.host?.let { host ->
                runCatching { host.isLogged(r.key) }.getOrDefault(false)
            } ?: false,
            canDisable = DesktopSourceSelection.canDisable(keys, effectiveEnabled, r.key),
            originLabel = originLabel,
            repoId = repoId,
            isBundled = isBundled,
        )
    }
}

/** 切换或设置单个漫画源的启用状态并写回本地清单（含随包与导入/社区新增源）。 */
internal fun writeDesktopSourceEnabled(
    assetDir: File,
    sourceState: DesktopSourceState,
    key: String,
    overrideDir: File? = null,
    enabled: Boolean? = null,
) {
    val bundled = EngineAssets.listSourceKeys(assetDir)
    val imported = overrideDir?.let { dir ->
        File(dir, "sources")
            .listFiles { f -> f.isFile && f.extension == "js" }
            ?.map { it.nameWithoutExtension }
            ?.filter { DesktopSourceOverrides.isSafeKey(it) }
            .orEmpty()
    }.orEmpty()
    val allKeys = (bundled + imported).distinct().sorted()
    val base = DesktopSourceState.effectiveEnabled(
        allKeys,
        sourceState.enabledKeys(),
    )
    val next = when (enabled) {
        true -> base + key
        false -> base - key
        null -> DesktopSourceSelection.toggle(base, key)
    }
    sourceState.setEnabledKeys(next)
}

/** 校验并保存源脚本到覆盖层目录；校验未通过时不落盘。 */
internal suspend fun saveDesktopSourceScript(
    overrideDir: File?,
    e: EngineSession?,
    key: String,
    content: String,
): String? {
    if (overrideDir == null) {
        return "这台机器的改写层目录建不出来（数据目录不可写？）：一个字都没写。"
    }
    if (e == null) {
        return "引擎没起来：这篇脚本没法先验再存，所以一个字都没写。"
    }
    return desktopEngineCall(e) {
        e.checkLoadable(key, content)
    }.fold(
        onSuccess = { reported ->
            val path = DesktopSourceOverrides.writeText(overrideDir, key, content)
            println(
                "D_源改写 存=$key 报回key=$reported 盘=$path 字节=${content.toByteArray(Charsets.UTF_8).size}",
            )
            if (reported != key) {
                "存下了，但这篇脚本自报的键是 $reported（不是 $key）：装载时会按它自己报的那个注册。"
            } else {
                null
            }
        },
        onFailure = { t ->
            "没存：${t.message?.take(400) ?: t.javaClass.simpleName}"
        },
    )
}

/** 使用手动输入的 Cookie 字段登录指定漫画源并验证落盘结果。 */
internal suspend fun loginDesktopSourceWithCookies(
    paths: PathProvider,
    e: EngineSession?,
    key: String,
    values: List<String>,
): String {
    if (e == null) {
        return "引擎没起来：这一趟发不出去，什么都没试。"
    }
    return desktopEngineCall(e) {
        e.load(key)
        e.loginWithCookies(values)
    }.fold(
        onSuccess = { login ->
            val onDisk = DesktopCookieLogin.tallyOnDisk(paths.dataRoot, login.fields)
            val outcome = DesktopCookieLogin.outcomeOf(login, onDisk)
            println(DesktopCookieLogin.reading(key, outcome))
            DesktopCookieLogin.screenNote(outcome)
        },
        onFailure = { t ->
            "这一趟没跑成：${t.javaClass.simpleName} " + (t.message?.take(200) ?: "")
        },
    )
}

/** 使用账号密码登录指定漫画源。 */
internal suspend fun loginDesktopSourceWithPassword(
    e: EngineSession?,
    key: String,
    account: String,
    password: String,
): String {
    if (e == null) {
        return "引擎没起来：这一趟发不出去，什么都没试。"
    }
    return desktopEngineCall(e) {
        e.load(key)
        e.loginWithPassword(account, password)
    }.fold(
        onSuccess = { login ->
            println(
                "D_账密登录 源=$key 源答=${if (login.ok) "过" else "没过"} 说明=${login.note.replace('\n', ' ').take(160)}",
            )
            if (login.ok) {
                "已登录：${login.note}；账号已落在本机源数据罐里"
            } else {
                "没登上：${login.note}"
            }
        },
        onFailure = { t ->
            "这一趟没跑成：${t.javaClass.simpleName} " + (t.message?.take(200) ?: "")
        },
    )
}

/** 通过 Edge 独立弹窗执行网页登录并把捕获的 Cookie 写入常驻引擎 CookieJar。 */
internal suspend fun loginDesktopSourceWithWebview(
    e: EngineSession?,
    key: String,
): String {
    if (e == null) {
        return "引擎没起来：这一趟发不出去，什么都没试。"
    }
    if (!DesktopEdgeWebLogin.isSupported) {
        return "本机未检测到 Microsoft Edge 浏览器，无法启动独立网页登录窗口。"
    }
    val webUrl = desktopEngineCall(e) {
        e.load(key)
        e.webLoginUrl()
    }.getOrNull()
    if (webUrl.isNullOrBlank()) {
        return "该源未声明有效的网页登录入口（loginWithWebview.url 为空）。"
    }
    val res = DesktopEdgeWebLogin.launchWebLogin(webUrl) { currentUrl, title ->
        desktopEngineCall(e) {
            e.checkWebLoginStatus(currentUrl, title)
        }.getOrDefault(false)
    }
    return res.fold(
        onSuccess = { cookies ->
            val saved = DesktopEdgeWebLogin.applyCookiesToJar(
                e.host.cookieJar,
                webUrl,
                cookies,
            )
            desktopEngineCall(e) {
                e.onWebLoginSuccess()
            }
            println("D_网页登录 源=$key 捕获=${cookies.size} 写入=$saved 状态=成功")
            "已登录：网页登录成功，已捕获 $saved 枚 Cookie 凭据并持久化写入。"
        },
        onFailure = { t ->
            "网页登录未完成：${t.message ?: t.javaClass.simpleName}"
        },
    )
}

/** 读取桌面端收藏列表（等待冷启动流首个非空快照，超时后回退当前值）。 */
internal suspend fun readDesktopFavoritesList(): Pair<List<FavoriteRecord>, Boolean> {
    val dao = favorite()
    val list = withTimeoutOrNull(2_000) {
        dao.favoritesFlow.first { it.isNotEmpty() }
    } ?: dao.favoritesFlow.value
    return list to true
}

/** 删除自定义收藏夹前先将其成员挪回「默认」夹，保证收藏不丢失。 */
internal suspend fun deleteDesktopFavoriteFolderAndMove(
    paths: PathProvider,
    name: String,
) {
    val dao = favorite()
    dao.favoritesFlow.value
        .filter { it.folderName == name }
        .forEach { row ->
            dao.addFavorite(
                desktopFavoriteRecordToWrite(
                    existing = row,
                    fresh = row,
                    folderName = "默认",
                ),
            )
        }
    removeCustomFavoriteFolder(paths, name)
}

/** 搜索续页游标状态，供游标型漫画源跨页传递 next 令牌。 */
internal class DesktopSearchCursorHolder {
    var cursor: String? = null
    var atPage: Int = 0
}

/** 执行单源漫画搜索并更新游标状态。 */
internal suspend fun searchDesktopComics(
    e: EngineSession?,
    cursorHolder: DesktopSearchCursorHolder,
    query: String,
    page: Int,
    sourceKey: String,
    options: List<String?>,
): DesktopComicSearchOutcome {
    if (e == null) {
        return DesktopComicSearchOutcome(emptyList(), null, "引擎没起来：这一趟发不出去。")
    }
    val cursor = if (page == cursorHolder.atPage + 1) cursorHolder.cursor else null
    return desktopEngineCall(e) {
        e.load(sourceKey)
        if (options.isEmpty()) e.search(query, page) else e.searchWith(query, options, page, cursor)
    }.map { got ->
        cursorHolder.cursor = got.next
        cursorHolder.atPage = page
        DesktopComicSearchOutcome(
            got.comics,
            got.maxPage,
            got.error?.takeIf { it.isNotBlank() },
            got.next,
        )
    }.getOrElse { t ->
        DesktopComicSearchOutcome(
            emptyList(),
            null,
            "搜索失败：${t.javaClass.simpleName} " + (t.message?.take(160) ?: ""),
        )
    }
}
