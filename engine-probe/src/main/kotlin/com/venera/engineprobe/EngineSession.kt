package com.venera.engineprobe

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/** 供桌面壳层订阅源脚本初始化失败事件的轻量回调。 */
@Volatile
var onEngineSourceInitFailure: ((key: String, detail: String) -> Unit)? = null

/**
 * 桌面侧可复用的**一次引擎会话**：装载一个真源 + 出探索/搜索数据 + 取图。
 *
 * 装载序列与 `ProbeMain`（S0-3 探针）逐字同构，而 `ProbeMain` 又是照抄
 * `source/js/ComicSourceParser.kt` 的 —— 三者同源，桌面与 Android 的差异才只可能来自引擎本身。
 *
 * 刻意不依赖 Compose：返回的是**图片字节**，解码归 UI 侧。
 */
class EngineSession(
    val host: DesktopJsHost,
    private val assetDir: File,
    /**
     * 用户改写层目录（源编辑）。与 [assetDir] **同构**：
     * 里面仍是 `sources/<key>.js` 那一套相对路径，命中时它优先于包内与仓库。
     *
     * ## 为什么必须有这一层（不是"多做一档配置"）
     *
     * 随包那 34 颗 js 走 `EngineAssets.readText` 的**包内优先** —— 正式产物里那一份在 jar 内，
     * 只读。于是"编辑源脚本"若只往仓库目录写文件，会出现最坏的一种绿：盘上真改了、
     * 屏幕上也念"已保存"，而下一次装载读的仍是包内那一份 ⇒ 改动一字都不生效。
     * 所以装载侧先问这一层（与 `EngineAssets` 同一个取用次序），写侧与读侧才是同一条轨。
     *
     * 传 `null` 就是改动前的行为（一处都不问）。
     */
    private val overrideDir: File? = null,
    /**
     * 源 `onThumbnailLoad` 返回非空请求头时的发布回调（url -> headers）。
     * 供桌面端把防盗链 Referer / UA 统一写入 ImageHeaderPolicy，避免封面请求 403。
     */
    private val onThumbnailHeaders: ((url: String, headers: Map<String, String>) -> Unit)? = null,
) : AutoCloseable {

    private val gson = Gson()
    private val mapType = object : TypeToken<Map<String, Any?>>() {}.type
    private val listType = object : TypeToken<List<Map<String, Any?>>>() {}.type
    var key: String = ""
        private set
    var name: String = ""
        private set

    /**
     * 一本书卡。`author` / `tags` 是新加的两格（默认空 ⇒ 老调用点与老夹具一字不动）：
     * 搜索屏与探索屏要对齐安卓大屏端那一形 —— 卡上画作者行 + 标签 chip（`ComicCard` 只有
     * title/id/cover 时那两行永远拼不出来）。取数解析照安卓 `JsComicSource` 同一口径：
     * 作者优先 `subTitle`→`author`（缺失留空串），标签吃 `tags`（字符串数组）或 `theme`（对象数组的 `name`），
     * 缺栏一律退空表，不拿空列表冒充"这一本没标签"（对岸那句"没给"与"给了空"在屏上同形过，桌面不照抄）。
     */
    data class ComicCard(
        val title: String,
        val id: String,
        val cover: String,
        val author: String = "",
        val tags: List<String> = emptyList(),
    )

    /** 探索页的一个分区（jm 等的 `explore[i].load()` 回的是**分区数组**，不是单个 comics 列表） */
    data class Section(val title: String, val comics: List<ComicCard>)

    /**
     * 源声明的一枚分类（`ComicSource.categoryExplore` 的一项）。
     *
     * 对齐安卓 `SourceCategory`：`name` 上屏、`index` 是数组下标（取数按它走
     * `categoryExplore[index].load(page)`）、`types` 是这一类的动作位
     * （`search` / `booksearch` / `comicsearch`，安卓同一判据决定搜索落到哪条腿）。
     */
    data class Category(
        val name: String,
        val index: Int,
        val types: List<String>,
    )

    /** 某一分类的搜索选项（`search.optionList`）摊平成屏可摆的形状。 */
    data class SearchOption(
        val label: String,
        val type: String,
        /** 选项值：type=`select`/`multi-select` 时是候选表（值→显示名），`dropdown` 同理；其余为空。 */
        val choices: List<Pair<String, String>> = emptyList(),
        val required: Boolean = false,
        /**
         * 源声明的默认值（编码同安卓 `_veneraOptionValues`）：多选 = JSON 串 `["0","1"]`、
         * 单选/文本 = 键、下拉无默认 = null。补。
         */
        val defaultValue: String? = null,
    )

    data class Page(
        val comics: List<ComicCard>,
        val sections: List<Section> = emptyList(),
        val maxPage: Int? = null,
        val error: String? = null,
        /** 游标型续页（`loadNext` 族回的 `next`；null = 到底）。：网络收藏无限下滑用它。 */
        val next: String? = null,
        /** 源 `onThumbnailLoad` 返回的非空封面请求头（key = 最终封面 URL）。 */
        val thumbnailHeaders: Map<String, Map<String, String>> = emptyMap(),
    )

    /**
     * 派生一颗短生命周期的独立 QuickJS 会话，复用主会话的 Cookie 罐、HTTP 连接池与源设置存储。
     * 调用方必须在使用完毕后调用 [close] 释放该临时 JS 上下文（不会关闭共享连接池）。
     */
    fun forkEphemeral(): EngineSession {
        val childHost = DesktopJsHost(
            assetDir = host.configuredAssetDir,
            dataDir = host.configuredDataDir,
            proxy = host.configuredProxy,
            seedCookies = emptyList(),
            sharedCookieJar = host.cookieJar,
            sharedClient = host.httpClient,
            sharedDataStore = host.sourceDataStore,
        )
        return EngineSession(
            host = childHost,
            assetDir = assetDir,
            overrideDir = overrideDir,
            onThumbnailHeaders = onThumbnailHeaders,
        )
    }

    /**
     * 装载指定漫画源脚本并注册默认设置。
     *
     * @param sourceKey 源键名或脚本名。
     * @param runInit 是否执行源的异步 `init()`；纯读写设置时传 `false` 避免每次改设置阻塞联网初始化。
     * @return 源展示名称；装载失败直接抛异常。
     */
    fun load(sourceKey: String, runInit: Boolean = true): String {
        val (loadedKey, loadedName) = loadIntoSources(sourceKey, runInit)
        key = loadedKey
        name = loadedName
        return loadedName
    }

    /**
     * 确保指定源已在当前 JS 环境的 `ComicSource.sources` 中就绪。
     * 不修改主会话的当前选中 [key] / [name]，供下载队列等后台多源并发/跨源任务按任务自带 sourceKey 执行。
     */
    private fun ensureSourceLoaded(sourceKey: String): String {
        val requested = sourceKey.trim()
        if (requested.isEmpty()) error("未指定漫画源，无法执行源请求")
        val loaded = host.evaluate("Boolean(ComicSource.sources[${gson.toJson(requested)}])")
            ?.trim('"', '\'') == "true"
        if (loaded) return requested
        val (loadedKey, _) = loadIntoSources(requested, runInit = true)
        return loadedKey
    }

    private fun loadIntoSources(sourceKey: String, runInit: Boolean): Pair<String, String> {
        // Task 6：源脚本改走 EngineAssets —— 包内 `/sources/<key>.js` 优先，取不到才回仓库目录，
        // 两处都没有就抛（原来是 `error("源脚本不存在: …")`，形状不变）。CRLF 归一仍在这颗做，
        // 送进 eval 的**内容**与改动前逐字节相同。
        val js = loadText(sourceKey).replace("\r\n", "\n")
        val className = Regex("""class\s+(\w+)\s+extends\s+ComicSource""").find(js)?.groupValues?.get(1)
            ?: error("未找到 extends ComicSource 的类声明: sources/$sourceKey.js")
        host.evaluate("(function() {\n$js\nwindow['temp_source'] = new $className();\n})();")

        val loadedKey = host.evaluate("window['temp_source'] ? window['temp_source'].key : null")
            ?.trim('"', '\'')
        if (loadedKey.isNullOrBlank() || loadedKey == "null") error("装载后取不到 source key: $sourceKey")

        // 默认设置项必须先注册，否则源里的 loadSetting 拿不到 default（与 Android 侧同一步）
        val defsJson = host.evaluate(
            """
            (function() {
                var s = window['temp_source'];
                var defs = {};
                if (s.settings) {
                    for (var k in s.settings) {
                        if (s.settings[k] && s.settings[k].default !== undefined) defs[k] = s.settings[k].default;
                    }
                }
                return JSON.stringify(defs);
            })()
            """.trimIndent()
        )
        if (!defsJson.isNullOrBlank() && defsJson != "null") {
            runCatching { gson.fromJson<Map<String, Any?>>(defsJson, mapType) }
                .getOrNull()?.let { host.registerDefaultSettings(loadedKey, it) }
        }

        host.evaluate("ComicSource.sources['$loadedKey'] = window['temp_source']; delete window['temp_source'];")
        if (runInit) {
            val initRes = host.evaluateAsync(
                "var s = ComicSource.sources['$loadedKey']; if (s && s.init) { return s.init(); } return null;",
                timeoutMs = 15_000L,
            )
            if (initRes.contains("\"success\":false")) {
                println("D_源初始化 失败 key=$loadedKey detail=${initRes.take(200)}")
                onEngineSourceInitFailure?.invoke(loadedKey, initRes.take(200))
            }
        }
        val loadedName = host.evaluate("ComicSource.sources['$loadedKey'].name")?.trim('"', '\'') ?: loadedKey
        return loadedKey to loadedName
    }

    /**
     * 取本次装载要用的脚本原文：**用户改写层优先**，其次包内，再次仓库。
     *
     * 单列成函数是因为同一份口径有两处要用（这里装载、屏上"编辑前要改的那一篇"）。
     * 两处各写一遍的话，"屏上摆的是出厂那一篇、实际跑的是改过那一篇"就成了必然
     * —— 正是本仓反复记过的"两份判据一定会漂"。
     */
    fun loadText(sourceKey: String): String {
        val relative = "sources/$sourceKey.js"
        overrideDir?.let { dir ->
            EngineAssets.overrideFile(dir, relative)?.let { text ->
                println(
                    "D_资源 来源=用户改写 ${File(dir, relative).absolutePath} " +
                        "字节=${text.toByteArray(Charsets.UTF_8).size}",
                )
                return text
            }
        }
        return EngineAssets.readText(assetDir, relative)
    }

    /**
     * 只做「这颗源装不装得起来」的校验（先解析校验再落盘）。
     *
     * 序列与 [load] 前半段同构（同一枚正则、同一个 `new`），但**不注册、不改本会话状态**：
     * `temp_source` 读完 key 就删掉，`ComicSource.sources` 一个字都不动。
     *
     * ## 为什么必须显式校验
     *
     * 官方 Dart 那一版是"退出即写"，一旦语法有错，那颗源会在下次启动时被静默丢弃
     * （对岸 `SourceEditScreen` 的类注释记的就是这一课）。这一版把校验钉在落盘之前：
     * 装不起来就抛，由调用方把成因交回编辑器原地展示，盘上仍是上一版好脚本。
     *
     * ## 为什么走 [DesktopJsHost.evaluateResult] 而不是 [DesktopJsHost.evaluate]
     *
     * @return 装载成功时那颗源自己报的 key（可能与请求的那颗不同，由调用方判读）
     */
    fun checkLoadable(sourceKey: String, js: String): String {
        val normalized = js.replace("\r\n", "\n")
        val className = Regex("""class\s+(\w+)\s+extends\s+ComicSource""").find(normalized)?.groupValues?.get(1)
            ?: error("未找到 extends ComicSource 的类声明: sources/$sourceKey.js")
        val outcome = host.evaluateResult(
            """
            (function() {
                try {
                    window['temp_source'] = undefined;
                    $normalized
                    window['temp_source'] = new $className();
                    return String(window['temp_source'].key || '');
                } finally {
                    delete window['temp_source'];
                }
            })()
            """.trimIndent()
        )
        // 求值本身塌了（语法错 / 类体里抛 / `new` 失败）⇒ 把 GraalJS 那句原样交上去。
        // GraalJS 的语法错消息带着行号与片段，那正是用户要找的东西；咽成"取不到 key"
        // 就是把**用户的笔误**念成**这颗源坏了**。
        val t = outcome.exceptionOrNull()
        if (t != null) {
            throw IllegalStateException(
                "这篇脚本装不起来：${t.javaClass.simpleName} ${t.message?.take(400) ?: "（无消息）"}",
                t,
            )
        }
        val reported = outcome.getOrNull()?.trim('"', '\'')
        // 报不出 key 也算装不起来（装载侧对这一步是硬要求，见 load 里那句"取不到 source key"）。
        return reported?.takeIf { it.isNotBlank() && it != "null" && it != "undefined" }
            ?: error("装载后取不到 source key: $sourceKey（类声明找到了，但 new 出来之后 key 那一格是空的）")
    }

    /** 探索页：两种形态都兜（数组项各自带 load / 直接一个 load），与 Android 侧同一分支形状 */
    fun explore(page: Int = 1): Page = call(
        """
        return (async function() {
            var s = ComicSource.sources['$key'];
            if (!s.explore) return { comics: [], __err: "该源没有 explore" };
            var r = Array.isArray(s.explore)
                ? await s.explore[${page.coerceAtLeast(1) - 1}].load($page)
                : await s.explore.load();
            return r || { comics: [] };
        })()
        """
    )

    /**
     * 按**探索模式下标**取一页（「探索页面…筛选和分类每个源都不一样的」）。
     *
     * 源 JS 的 `explore` 是数组时，每一项是一套独立的探索（禁漫天堂、拷贝、E 站各不同），
     * 下标由 [exploreModes] 摊平上屏；单对象形态（`explore.load()`）忽略下标。
     * 方法名保留 `explore` 前缀的**独立名**只为可读；引擎账那把尺认的是 `explore(`，
     * 这里特意做成 [explore] 的重载（`fun explore(index, page)`），不另起新名绕开尺子。
     */
    fun explore(index: Int, page: Int = 1): Page = call(
        """
        return (async function() {
            var s = ComicSource.sources['$key'];
            if (!s.explore) return { comics: [], __err: "该源没有 explore" };
            if (!Array.isArray(s.explore)) return await s.explore.load();
            var m = s.explore[${index.coerceAtLeast(0)}];
            if (!m || !m.load) return { comics: [], __err: "没有这一格探索模式" };
            var r = await m.load($page);
            return r || { comics: [] };
        })()
        """
    )

    /**
     * 这一颗源声明的探索模式名（`explore` 数组每项的 `title`/`name`；单对象形态给一枚
     * 「探索」）。空表 = 该源没有探索 —— 屏上整排不摆，不摆一排按不动的假开关。
     */
    fun exploreModes(): List<String> {
        val raw = host.evaluate(
            """
            JSON.stringify((function() {
                var s = ComicSource.sources['$key'];
                if (!s.explore) return [];
                if (!Array.isArray(s.explore)) return ['探索'];
                return s.explore.map(function(m, i) {
                    return (m && (m.title || m.name)) ? (m.title || m.name) : ('模式 ' + (i + 1));
                });
            })())
            """.trimIndent()
        ) ?: return emptyList()
        return runCatching {
            gson.fromJson<List<String>>(raw, listType)?.filter { it.isNotBlank() } ?: emptyList()
        }.getOrDefault(emptyList())
    }

    // 分类面板数据来自源 JS 的 category 对象（parts[].categories + categoryParams + itemType）。

    /** 分类面板里的一枚 chip：`kind = "category"` 走分类接口，`"search"` 走搜索接口。 */
    data class CategoryChip(val label: String, val param: String?, val kind: String)

    /** 分类面板的一组（源 JS `category.parts[i]`）。 */
    data class CategoryPart(val name: String, val chips: List<CategoryChip>)

    /** 分类面板：分组 + 排行榜档位（后者源没声明 `enableRankingPage` 时为空表）。 */
    data class CategoryBoard(
        val parts: List<CategoryPart>,
        val ranking: List<Pair<String, String>>,
    )

    fun categoryBoard(): CategoryBoard = runCatching {
        val raw = host.evaluate(
            """
            JSON.stringify((function() {
                var s = ComicSource.sources['$key'];
                if (!s || !s.category) return { parts: [], ranking: [] };
                var cat = s.category;
                var parts = (cat.parts || []).map(function(p) {
                    var categories = p.categories || [];
                    var params = p.categoryParams || [];
                    if (p.groupParam !== undefined && p.groupParam !== null) {
                        params = categories.map(function() { return p.groupParam; });
                    }
                    var itemType = p.itemType || "category";
                    var chips = [];
                    for (var i = 0; i < categories.length; i++) {
                        var raw = categories[i];
                        var label = (typeof raw === 'object') ? (raw.label || '') : String(raw);
                        var param = (params[i] !== undefined && params[i] !== null) ? String(params[i]) : null;
                        chips.push({ label: label, param: param, kind: itemType });
                    }
                    return { name: p.name || "", chips: chips };
                }).filter(function(p) { return p.chips.length > 0; });
                var ranking = [];
                if (cat.enableRankingPage && s.categoryComics && s.categoryComics.ranking && s.categoryComics.ranking.options) {
                    var ro = s.categoryComics.ranking.options || [];
                    for (var j = 0; j < ro.length; j++) {
                        var opt = String(ro[j] || "");
                        var at = opt.indexOf('-');
                        if (at < 0) continue;
                        ranking.push({ key: opt.substring(0, at), label: opt.substring(at + 1) });
                    }
                }
                return { parts: parts, ranking: ranking };
            })())
            """.trimIndent()
        )
        val m = gson.fromJson<Map<String, Any?>>(raw, mapType)
        val parts = (m["parts"] as? List<*>)?.mapNotNull { row ->
            val p = row as? Map<*, *> ?: return@mapNotNull null
            val chips = (p["chips"] as? List<*>)?.mapNotNull { c ->
                val cm = c as? Map<*, *> ?: return@mapNotNull null
                val label = cm["label"]?.toString() ?: return@mapNotNull null
                CategoryChip(
                    label = label,
                    param = cm["param"]?.toString(),
                    kind = cm["kind"]?.toString() ?: "category",
                )
            } ?: emptyList()
            CategoryPart(p["name"]?.toString() ?: "", chips)
        } ?: emptyList()
        val ranking = (m["ranking"] as? List<*>)?.mapNotNull { r ->
            val rm = r as? Map<*, *> ?: return@mapNotNull null
            val key = rm["key"]?.toString() ?: return@mapNotNull null
            key to (rm["label"]?.toString() ?: key)
        } ?: emptyList()
        CategoryBoard(parts, ranking)
    }.getOrElse { CategoryBoard(emptyList(), emptyList()) }

    /**
     * 点一枚分类 chip 取一页。
     *
     * - `kind = "search"`：走源 `search.load(label, 默认选项, page)`（Android 的 search 型分类
     *   就是"拿分类名去搜"）；
     * - 否则走 `categoryComics.load(category, param, 选项, page)`；选项没显式给时，
     *   用 `optionList` 的默认值，或 `optionLoader` 返回组的首项（与安卓同一兜底）。
     */
    fun loadCategory(category: String, param: String?, kind: String, page: Int = 1): Page = call(
        """
        return (async function() {
            var s = ComicSource.sources['$key'];
            if (!s) return { comics: [], __err: "源没装载" };
            var kind = ${gson.toJson(kind)};
            var category = ${gson.toJson(category)};
            var param = ${gson.toJson(param)};
            if (kind === 'search') {
                if (!s.search || !(s.search.load || s.search.loadNext)) return { comics: [], __err: "该源没有 search" };
                var so = s.search.optionList ? _veneraOptionValues(s.search.optionList) : [];
                if (s.search.load) return (await s.search.load(category, so, ${page})) || { comics: [] };
                return (await s.search.loadNext(category, so, null)) || { comics: [] };
            }
            var cc = s.categoryComics;
            if (!cc || !cc.load) return { comics: [], __err: "该源没有分类接口" };
            function grpDefault(g) {
                var list = g.options || [];
                var keys = [];
                for (var i = 0; i < list.length; i++) {
                    var raw = String(list[i]);
                    var at = raw.indexOf('-');
                    if (at < 0) continue;
                    keys.push(raw.substring(0, at));
                }
                if (g['default'] !== undefined && g['default'] !== null) {
                    return Array.isArray(g['default']) ? JSON.stringify(g['default']) : String(g['default']);
                }
                return keys.length ? keys[0] : '';
            }
            var opts = [];
            if (cc.optionList) {
                opts = _veneraOptionValues(cc.optionList);
            } else if (cc.optionLoader) {
                var groups = (await cc.optionLoader(category, param)) || [];
                opts = groups.map(grpDefault);
            }
            return (await cc.load(category, param, opts, ${page})) || { comics: [] };
        })()
        """
    )

    /** 排行榜一页（`categoryComics.ranking.load(档位, page)`；只有 loadWithNext 的源走它）。 */
    fun loadRanking(option: String, page: Int = 1): Page = call(
        """
        return (async function() {
            var s = ComicSource.sources['$key'];
            var r = s && s.categoryComics && s.categoryComics.ranking;
            if (!r) return { comics: [], __err: "该源没有排行榜" };
            if (r.load) return (await r.load(${gson.toJson(option)}, ${page})) || { comics: [] };
            if (r.loadWithNext) return (await r.loadWithNext(${gson.toJson(option)}, ${page} === 1 ? null : String(${page}))) || { comics: [] };
            return { comics: [], __err: "该源没有排行榜" };
        })()
        """
    )

    // 网络收藏接口：脚本必须显式 return，否则 evaluateAsync 序列化会丢失 data 键；
    // 未登录时返回 NetworkFavPage.error="Not login"，供界面展示登录引导。

    /** 这一格源声明没声明 `favorites`(没声明 = 手风琴里这一颗卡头不摆)。 */
    fun hasNetworkFavorites(): Boolean = runCatching {
        host.evaluate(
            "JSON.stringify(!!(ComicSource.sources['$key'] && ComicSource.sources['$key'].favorites))"
        ) == "true"
    }.getOrDefault(false)

    /**
     * `favorites` 能力四连探（multiFolder + loadComics/loadNext 各一颗 typeof）。
     *
     * 复核修正：取页那一跳**必须按
     * hasLoadComics/hasLoadNext 挑**，不是按 multiFolder——两形正交：多数源只声明
     * loadComics（jm 那族），ehentai/exhentai 只声明 loadNext；此前桌面按 multiFolder
     * 分流，E站（多夹且无 loadComics）被送进 loadComics ⇒ 「该源没有 favorites.loadComics」
     * 死信。对岸 `JsComicSource.loadFavoriteData` 就是分别 typeof 探测（两者可单独存在）。
     */
    data class NetFavCaps(
        val multiFolder: Boolean,
        val hasLoadComics: Boolean,
        val hasLoadNext: Boolean,
    )

    data class FavMeta(
        val exists: Boolean,
        val multiFolder: Boolean,
        val singleFolderForSingleComic: Boolean,
        val isOldToNewSort: Boolean,
        val hasLoadComics: Boolean,
        val hasLoadNext: Boolean,
        val hasAddFolder: Boolean,
        val hasDeleteFolder: Boolean,
    )

    fun favoriteMeta(): FavMeta = runCatching {
        val raw = host.evaluate(
            """
            JSON.stringify((function() {
                var s = ComicSource.sources['$key'];
                if (!s || !s.favorites) return { exists: false };
                var f = s.favorites;
                return {
                    exists: true,
                    multiFolder: f.multiFolder === true,
                    singleFolderForSingleComic: f.singleFolderForSingleComic === true,
                    isOldToNewSort: f.isOldToNewSort === true,
                    hasLoadComics: typeof f.loadComics === 'function',
                    hasLoadNext: typeof f.loadNext === 'function',
                    hasAddFolder: typeof f.addFolder === 'function',
                    hasDeleteFolder: typeof f.deleteFolder === 'function'
                };
            })())
            """.trimIndent()
        )
        val m = gson.fromJson<Map<String, Any?>>(raw, mapType)
        FavMeta(
            exists = m["exists"] == true,
            multiFolder = m["multiFolder"] == true,
            singleFolderForSingleComic = m["singleFolderForSingleComic"] == true,
            isOldToNewSort = m["isOldToNewSort"] == true,
            hasLoadComics = m["hasLoadComics"] == true,
            hasLoadNext = m["hasLoadNext"] == true,
            hasAddFolder = m["hasAddFolder"] == true,
            hasDeleteFolder = m["hasDeleteFolder"] == true,
        )
    }.getOrDefault(FavMeta(false, false, false, false, false, false, false, false))

    data class FavFoldersResult(
        val folders: Map<String, String>,
        val favorited: List<String>,
    )

    fun networkFavoritesFoldersWithComic(comicId: String?): FavFoldersResult = runCatching {
        val script = """
            return (async function() {
                var f = ComicSource.sources['$key']?.favorites;
                if (!f || !f.loadFolders) return {};
                var r = await f.loadFolders(${gson.toJson(comicId)});
                return (r && r.folders) ? r : (r || {});
            })()
        """.trimIndent()
        val raw = evalWithRetry(script)
        val env = gson.fromJson<Map<String, Any?>>(raw, mapType)
        val data = (env["data"] as? Map<*, *>) ?: (env as? Map<*, *>) ?: emptyMap<Any?, Any?>()
        val rawFolders = (data["folders"] as? Map<*, *>)?.mapNotNull { (k, v) ->
            (k?.toString() ?: return@mapNotNull null) to (v?.toString() ?: "")
        }?.toMap() ?: emptyMap()
        val allFolderId = "-1"
        val folders = LinkedHashMap<String, String>(rawFolders.size).apply {
            rawFolders[allFolderId]?.let { put(allFolderId, it) }
            rawFolders.forEach { (k, v) -> if (k != allFolderId) put(k, v) }
        }
        val favorited = (data["favorited"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
        FavFoldersResult(folders, favorited)
    }.getOrDefault(FavFoldersResult(emptyMap(), emptyList()))

    fun networkFavoritesAddOrDel(comicId: String, folderId: String, isAdding: Boolean, favoriteId: String?) {
        val script = """
            return (async function() {
                var s = ComicSource.sources['$key'];
                var f = s && s.favorites;
                if (!f || !f.addOrDelFavorite) return { __err: "该源没有 favorites.addOrDelFavorite" };
                var r = await f.addOrDelFavorite(
                    ${gson.toJson(comicId)},
                    ${gson.toJson(folderId)},
                    ${gson.toJson(isAdding)},
                    ${gson.toJson(favoriteId)}
                );
                return r || {};
            })()
        """.trimIndent()
        val raw = evalWithRetry(script)
        val env = runCatching { gson.fromJson<Map<String, Any?>>(raw, mapType) }.getOrNull()
        if (env != null && env["success"] == false) {
            error(env["error"]?.toString() ?: "收藏操作失败")
        }
    }

    fun networkFavoritesAddFolder(name: String) {
        val script = """
            return (async function() {
                var s = ComicSource.sources['$key'];
                var f = s && s.favorites;
                if (!f || !f.addFolder) return { __err: "该源没有 favorites.addFolder" };
                var r = await f.addFolder(${gson.toJson(name)});
                return r || {};
            })()
        """.trimIndent()
        val raw = evalWithRetry(script)
        val env = runCatching { gson.fromJson<Map<String, Any?>>(raw, mapType) }.getOrNull()
        if (env != null && env["success"] == false) {
            error(env["error"]?.toString() ?: "新建文件夹失败")
        }
    }

    fun networkFavoritesDeleteFolder(folderId: String) {
        val script = """
            return (async function() {
                var s = ComicSource.sources['$key'];
                var f = s && s.favorites;
                if (!f || !f.deleteFolder) return { __err: "该源没有 favorites.deleteFolder" };
                var r = await f.deleteFolder(${gson.toJson(folderId)});
                return r || {};
            })()
        """.trimIndent()
        val raw = evalWithRetry(script)
        val env = runCatching { gson.fromJson<Map<String, Any?>>(raw, mapType) }.getOrNull()
        if (env != null && env["success"] == false) {
            error(env["error"]?.toString() ?: "删除文件夹失败")
        }
    }

    fun networkFavoritesCaps(): NetFavCaps = runCatching {
        val raw = host.evaluate(
            """
            JSON.stringify({
                mf: ComicSource.sources['$key']?.favorites?.multiFolder === true,
                lc: typeof ComicSource.sources['$key']?.favorites?.loadComics === 'function',
                ln: typeof ComicSource.sources['$key']?.favorites?.loadNext === 'function'
            })
            """
        )
        val m = gson.fromJson<Map<String, Any?>>(raw, mapType)
        NetFavCaps(
            multiFolder = m["mf"] == true,
            hasLoadComics = m["lc"] == true,
            hasLoadNext = m["ln"] == true,
        )
    }.getOrDefault(NetFavCaps(multiFolder = false, hasLoadComics = false, hasLoadNext = false))

    /**
     * 多夹源的夹清单（`favorites.loadFolders(null)`），**id→name 成对**摊平。
     *
     * 复核修正：对岸 FavFolders 的 `folders` 是 **JS Map（id→name）**，
     * 序列化后是对象不是数组——此前按 List 取 ⇒ 恒空表；且摊平时只留 name 丢了 id,
     * 而 `loadNext(next, folder)` 要的是 **folder id**（E站拼进 `?favcat=`），名字递进去
     * 取回的就是错页。照对岸补两处：认 Map 形状；`"-1"`（各源约定的「全部」）提回首位
     * （JS Map 的插入序经 JSON.stringify 后整数键会被排到 "-1" 前面,对岸同一坑同一修）。
     */
    fun networkFavoritesFolders(): List<Pair<String, String>> = runCatching {
        val raw = evalWithRetry(
            """
            return (async function() {
                var f = ComicSource.sources['$key']?.favorites;
                if (!f || !f.loadFolders) return {};
                var r = await f.loadFolders(null);
                return (r && r.folders) ? r.folders : (r || {});
            })()
            """
        )
        val env = gson.fromJson<Map<String, Any?>>(raw, mapType)
        val rows: List<Pair<String, String>> = when (val data = env["data"]) {
            is Map<*, *> -> data.mapNotNull { (k, v) ->
                val id = k?.toString() ?: return@mapNotNull null
                id to (v?.toString() ?: id)
            }
            // 兜另一形：有的源回 [{id,name}/{title}] 数组（对岸没这形,桌面宽容一点不重犯空表）。
            is List<*> -> data.mapNotNull { row ->
                val m = (row as? Map<*, *>) ?: return@mapNotNull null
                val id = (m["id"] ?: m["name"] ?: m["title"])?.toString() ?: return@mapNotNull null
                id to ((m["name"] ?: m["title"] ?: m["id"])?.toString() ?: id)
            }
            else -> emptyList()
        }
        val all = rows.filter { it.first == "-1" }
        all + rows.filter { it.first != "-1" }
    }.getOrDefault(emptyList())

    /** 网络收藏一页(单夹/某夹)。`favorites.loadComics(page, folderId)` 那一路。 */
    fun networkFavoritesLoadComics(page: Int, folderId: String?): Page = call(
        """
        return (async function() {
            var s = ComicSource.sources['$key'];
            var f = s && s.favorites;
            if (!f || !f.loadComics) return { comics: [], __err: "该源没有 favorites.loadComics" };
            var r = await f.loadComics(${gson.toJson(page)}, ${gson.toJson(folderId)});
            return r || { comics: [] };
        })()
        """
    )

    /** 网络收藏游标续页(ehentai 那族只声明 `loadNext`)。`next=null` = 首屏。 */
    fun networkFavoritesLoadNext(next: String?, folderId: String?): Page = call(
        """
        return (async function() {
            var s = ComicSource.sources['$key'];
            var f = s && s.favorites;
            if (!f || !f.loadNext) return { comics: [], __err: "该源没有 favorites.loadNext" };
            var r = await f.loadNext(${gson.toJson(next)}, ${gson.toJson(folderId)});
            return r || { comics: [] };
        })()
        """
    )

    /**
     * 某一分类的探索页（`categoryExplore[index].load(page)`）。对齐安卓
     * `JsComicSource` 走 `categoryExplore` 的那一路：探索屏"点某一格分类"进的就是这一腿。
     * 越界 / 该源没声明 ⇒ 带成因的空页（不抛：屏上那句成因比一次崩溃有用）。
     */
    fun exploreCategory(index: Int, page: Int = 1): Page {
        if (index < 0) return Page(emptyList(), error = "没有这一格分类")
        return call(
            """
            return (async function() {
                var s = ComicSource.sources['$key'];
                if (!s.categoryExplore || !Array.isArray(s.categoryExplore))
                    return { comics: [], __err: "该源没有分类探索" };
                var c = s.categoryExplore[$index];
                if (!c || !c.load) return { comics: [], __err: "没有这一格分类" };
                var r = await c.load($page);
                return r || { comics: [] };
            })()
            """
        )
    }

    /**
     * 这一颗源声明的分类清单（`categoryExplore` 摊平成 `[{name, types}]`）。
     * 源没声明 ⇒ 空表（探索屏那一排整颗不摆，不是摆一排空的）。
     */
    fun categories(): List<Category> {
        val raw = host.evaluate(
            """
            JSON.stringify((function() {
                var s = ComicSource.sources['$key'];
                if (!s.categoryExplore || !Array.isArray(s.categoryExplore)) return [];
                return s.categoryExplore.map(function(c, i) {
                    return { name: c.name || '', index: i, types: c.types || [] };
                });
            })())
            """.trimIndent()
        ) ?: return emptyList()
        return runCatching {
            val list = gson.fromJson<List<Map<String, Any?>>>(raw, listType) ?: emptyList()
            list.map { m ->
                Category(
                    name = m["name"]?.toString() ?: "",
                    index = (m["index"] as? Number)?.toInt() ?: 0,
                    types = (m["types"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList(),
                )
            }
        }.getOrDefault(emptyList())
    }

    /**
     * 这一颗源 `search.optionList` 摊平的选项清单（搜索屏"排序与高级筛选"那一格的渲染输入）。
     * 没声明 ⇒ 空表，那一格整片不摆。
     *
     * 复核修正（「筛选和分类每个源都不一样的，你这个定死了」）：
     *  - `o.options` 是 **"值-显示名" 字符串数组**（如 `"1-Doujinshi"`）；此前按对象取 label，
     *    上屏的是整串 `1-Doujinshi` 且值是下标 —— 与安卓 `_veneraOptionValues` 的口径不符。现按
     *    `-` 切分，键/名各归各；
     *  - 补 `default`：多选默认是数组（编码成 JSON 串）、单选/文本取原值、下拉无默认 = null。
     *    没有它，桌面一开口就把源默认筛掉的那几档丢了。
     */
    fun searchOptions(): List<SearchOption> {
        val raw = host.evaluate(
            """
            JSON.stringify((function() {
                var s = ComicSource.sources['$key'];
                if (!s.search || !s.search.optionList) return [];
                return s.search.optionList.map(function(o) {
                    var rawOpts = o.options || [];
                    var choices = [];
                    for (var i = 0; i < rawOpts.length; i++) {
                        var raw = String(rawOpts[i]);
                        var idx = raw.indexOf('-');
                        if (idx < 0) continue;
                        choices.push({ value: raw.substring(0, idx), label: raw.substring(idx + 1) });
                    }
                    var type = o.type || (choices.length > 0 ? 'select' : 'text');
                    var def = null;
                    if (o.default !== undefined && o.default !== null) {
                        def = (type === 'multi-select' && Array.isArray(o.default))
                            ? JSON.stringify(o.default) : String(o.default);
                    } else if (type === 'multi-select') {
                        def = '[]';
                    } else if (type !== 'dropdown') {
                        def = choices.length > 0 ? choices[0].value : '';
                    }
                    return {
                        label: o.label || o.name || '',
                        type: type,
                        required: !!o.required,
                        choices: choices,
                        defaultValue: def
                    };
                });
            })())
            """.trimIndent()
        ) ?: return emptyList()
        return runCatching {
            val list = gson.fromJson<List<Map<String, Any?>>>(raw, listType) ?: emptyList()
            list.map { m ->
                SearchOption(
                    label = m["label"]?.toString() ?: "",
                    type = m["type"]?.toString() ?: "text",
                    required = m["required"] == true,
                    choices = (m["choices"] as? List<*>)?.mapNotNull { row ->
                        (row as? Map<*, *>)?.let {
                            (it["value"]?.toString() ?: "") to (it["label"]?.toString() ?: "")
                        }
                    } ?: emptyList(),
                    defaultValue = m["defaultValue"]?.toString(),
                )
            }
        }.getOrDefault(emptyList())
    }

    /**
     * 带选项值搜索（`search.load(keyword, optionValues, page)`）。
     * [options] 是按 optionList 次序排好的值表（null = 该格「未选择」，与源 JS 的 null 同义）；
     * 与 [search] 的区别就在这一表——排序/筛选选块改完走这条腿。
     */
    fun searchWith(keyword: String, options: List<String?>, page: Int = 1, next: String? = null): Page = call(
        """
        return (async function() {
            var s = ComicSource.sources['$key'];
            if (!s.search || !(s.search.load || s.search.loadNext))
                return { comics: [], __err: "该源没有 search" };
            var opts = ${gson.toJson(options)};
            if (!s.search.load) {
                var r0 = await s.search.loadNext(${gson.toJson(keyword)}, opts, ${gson.toJson(next)});
                return r0 || { comics: [] };
            }
            var r = await s.search.load(${gson.toJson(keyword)}, opts, $page);
            return r || { comics: [] };
        })()
        """
    )

    fun search(keyword: String, page: Int = 1, timeoutMs: Long = 30_000L): Page = call(
        """
        return (async function() {
            var s = ComicSource.sources['$key'];
            var opts = s.search && s.search.optionList ? _veneraOptionValues(s.search.optionList) : [];
            var res = null;
            if (s.search && s.search.load) {
                res = await s.search.load(${gson.toJson(keyword)}, opts, $page);
            } else if (s.search && s.search.loadNext) {
                res = await s.search.loadNext(${gson.toJson(keyword)}, opts, null);
            } else {
                return { comics: [], __err: "该源没有 search" };
            }
            return res || { comics: [] };
        })()
        """,
        timeoutMs = timeoutMs,
    )

    private fun call(script: String, timeoutMs: Long = 30_000L): Page {
        val raw = evalWithRetry(script, timeoutMs = timeoutMs)
        val envelope = gson.fromJson<Map<String, Any?>>(raw, mapType)
        val err = envelope["error"]?.toString() ?: envelope["__err"]?.toString()
        if (envelope["success"] == false) {
            println("[session] 信封失败 error=$err raw=${raw.take(240)}")
            return Page(emptyList(), error = err)
        }
        val data0 = envelope["data"]
        // nhentai 的 explore 多套一层：`{data:{data:[{title, comics:[…]}, …]}}`
        val data = if (data0 is Map<*, *> && data0["data"] != null && data0["comics"] == null) {
            data0["data"]
        } else {
            data0
        }
        val maxPage = (data as? Map<*, *>)?.get("maxPage")?.let { (it as? Number)?.toInt() }
        // 游标型续页（`loadNext` 族回 `next`；ehentai 收藏就是这一形）。
        // `next` 缺席 / 为 null / 空串都算「到底了」——空串当游标会拿同一个地址无限翻。
        val next = (data as? Map<*, *>)?.get("next")?.toString()?.takeIf { it.isNotBlank() && it != "null" }
        // 兼容四种返回结构：search/list 对象、分区数组、漫画数组，以及 multiPart 旧式 Map（空分区不收）。
        val sections = when (data) {
            is List<*> -> {
                val rows = data.mapNotNull { it as? Map<*, *> }
                if (rows.any { it["comics"] != null }) {
                    rows.map { m -> Section(m["title"]?.toString() ?: "", cards(m["comics"])) }
                } else {
                    listOf(Section("", cards(rows)))
                }
            }

            is Map<*, *> -> {
                val flat = cards(data["comics"])
                if (data["comics"] != null || flat.isNotEmpty()) {
                    // 原来那一支的语义保持不动：给了 `comics` 键就照单分区收（哪怕是空的），
                    // 屏上那句「这一源没回探索内容」的成因才不被顺手改掉。
                    listOf(Section("", flat))
                } else {
                    data.entries.mapNotNull { (k, v) ->
                        val items = cards(v)
                        if (items.isEmpty()) null else Section(k?.toString() ?: "", items)
                    }
                }
            }

            else -> {
                println("[session] data 形状不认识=${data?.javaClass?.simpleName} raw=${raw.take(240)}")
                emptyList()
            }
        }
        val total = sections.sumOf { it.comics.size }
        if (total == 0) println("[session] 解析出 0 条 data形状=${data?.javaClass?.simpleName} raw=${raw.take(300)}")
        // 封面统一过一遍源的 `onThumbnailLoad`：同时收集 URL 改写（如 EH 换 ehgt.org）与防盗链请求头（如 jcomic/comick Referer）。
        val rewritten = applyThumbnailHook(sections.flatMap { s -> s.comics.map { it.cover } })
        val publishedHeaders = LinkedHashMap<String, Map<String, String>>()
        val finalSections = if (rewritten.isEmpty()) sections else sections.map { section ->
            Section(
                section.title,
                section.comics.map { c ->
                    val rw = rewritten[c.cover]
                    if (rw == null) {
                        c
                    } else {
                        if (rw.headers.isNotEmpty()) {
                            publishedHeaders[rw.url] = rw.headers
                            onThumbnailHeaders?.invoke(rw.url, rw.headers)
                        }
                        if (rw.url != c.cover) c.copy(cover = rw.url) else c
                    }
                },
            )
        }
        return Page(finalSections.flatMap { it.comics }, finalSections, maxPage, err, next, publishedHeaders)
    }

    /** 批量把封面原文映射到过完 `onThumbnailLoad` 的改写（含 URL 与防盗链请求头）；无钩子时返回空表。 */
    private fun applyThumbnailHook(covers: List<String>): Map<String, ThumbnailRewrite> {
        val distinct = covers.filter { it.isNotBlank() }.distinct()
        if (distinct.isEmpty()) return emptyMap()
        val rewrites = thumbnailRewrites(distinct)
        if (rewrites.isEmpty()) return emptyMap()
        return rewrites.filter { (orig, rw) -> rw.url != orig || rw.headers.isNotEmpty() }
    }

    private fun cards(raw: Any?): List<ComicCard> = (raw as? List<*> ?: emptyList<Any?>()).mapNotNull { c ->
        (c as? Map<*, *>)?.let {
            ComicCard(
                title = it["title"]?.toString() ?: "",
                id = it["id"]?.toString() ?: "",
                cover = it["cover"]?.toString() ?: "",
                // 作者：对岸那一格在源脚本里叫 subTitle，也有源直接给 author（认不到留空串）。
                author = it["subTitle"]?.toString() ?: it["author"]?.toString() ?: "",
                // 标签：`tags` 是字符串数组；`theme` 是对象数组（取 `name`）—— 两档照对岸解析，缺栏退空表。
                tags = (it["tags"] as? List<*>)?.mapNotNull { t -> t?.toString() }
                    ?: (it["theme"] as? List<*>)?.mapNotNull { t -> (t as? Map<*, *>)?.get("name")?.toString() }
                    ?: emptyList(),
            )
        }
    }

    /**
     * 缩略图：源若有 `onThumbnailLoad` 必须先过它（它给的是 `{url, headers}`，
     * 直接拿 `cover` 原值去打常缺 Referer），没有才原地址取。与 Android 侧同一判据。
     */
    fun coverBytes(cover: String): ByteArray {
        var url = cover
        var headers = emptyMap<String, String>()
        val env = runCatching {
            gson.fromJson<Map<String, Any?>>(
                host.evaluateAsync(
                    """
                    return (async function(){
                        var s = ComicSource.sources['$key'];
                        if (!s.comic || !s.comic.onThumbnailLoad) return { __none: true };
                        return await s.comic.onThumbnailLoad(${gson.toJson(cover)}, "", "");
                    })()
                    """.trimIndent()
                ), mapType
            )
        }.getOrNull()
        if (env != null && env["__none"] != true && env["success"] != false) {
            val data = env["data"] as? Map<*, *>
            if (data != null) {
                url = data["url"]?.toString() ?: url
                headers = (data["headers"] as? Map<*, *>)
                    ?.entries?.associate { it.key.toString() to it.value.toString() } ?: headers
            }
        }
        val (status, bytes) = host.fetchBytes(url, headers)
        if (status != 200 || bytes.isEmpty()) error("取缩略图失败 http=$status bytes=${bytes.size} url=$url")
        return bytes
    }

    /**
     * 源 `onThumbnailLoad` 的批量 Kotlin 面（详情页 EH 预览终审）：
     * 每个原始 url 过一遍钩子，交回 **改写后的地址 + 防盗头**（EH 一族把 `s.exhentai.org`
     * 换成免登录的 `ehgt.org` 并带 referer）。键 = **原始 url**，调用方拿原始键查表换地址。
     * 源没声明钩子 / 引擎没答 ⇒ 空表（调用方原样用裸地址，与"未声明 onThumbnailLoad"同效）。
     */
    data class ThumbnailRewrite(val url: String, val headers: Map<String, String>)

    fun thumbnailRewrites(urls: List<String>): Map<String, ThumbnailRewrite> {
        if (urls.isEmpty()) return emptyMap()
        // evaluateAsync 返回 {success, data, error} 信封，必须先拆信封再取 data，
        // 否则 onThumbnailLoad 声明的请求头（如 Referer）无法发布到取图策略。
        val envelope = runCatching {
            gson.fromJson<Map<String, Any?>>(
                host.evaluateAsync(
                    """
                    return (async function(){
                        var s = ComicSource.sources['$key'];
                        if (!s || !s.comic || !s.comic.onThumbnailLoad) return null;
                        var urls = ${gson.toJson(urls)};
                        var out = [];
                        for (var i = 0; i < urls.length; i++) {
                            try {
                                var r = await s.comic.onThumbnailLoad(urls[i], '', '');
                                out.push({ url: (r && r.url) ? r.url : urls[i], headers: (r && r.headers) ? r.headers : {} });
                            } catch (e) {
                                out.push({ url: urls[i], headers: {} });
                            }
                        }
                        return out;
                    })()
                    """.trimIndent()
                ), mapType
            )
        }.getOrNull()
        if (envelope == null) {
            println("D_缩略钩子 key=$key 信封解析失败（evaluateAsync 没回 JSON）")
            return emptyMap()
        }
        if (envelope["success"] == false) {
            println("D_缩略钩子 key=$key 失败 ${envelope["error"]?.toString()?.take(140)}")
            return emptyMap()
        }
        val data = envelope["data"] ?: return emptyMap()
        val items = (data as? List<*>)?.mapNotNull { it as? Map<String, Any?> }.orEmpty()
        if (items.isEmpty()) return emptyMap()
        val out = mutableMapOf<String, ThumbnailRewrite>()
        urls.forEachIndexed { i, original ->
            val item = items.getOrNull(i) ?: return@forEachIndexed
            val headers = (item["headers"] as? Map<*, *>)
                ?.entries?.associate { it.key.toString() to it.value.toString() } ?: emptyMap()
            out[original] = ThumbnailRewrite(
                url = item["url"]?.toString() ?: original,
                headers = headers,
            )
        }
        return out
    }

    /** `placeholder=true` 表示这是"整部就一章"的占位章，取页时话号要还原成 `null` */
    data class Episode(val name: String, val id: String, val placeholder: Boolean = false)

    /**
     * 详情：标题 + 简介 + 章节表。**怎么读那份答复全在 [parseComicLoadInfo]**（那一颗是纯函数，
     * 判据直接喂假信封进它）；这里只负责发一次 JS。
     */
    fun details(comicId: String): ComicInfo {
        val env = gson.fromJson<Map<String, Any?>>(
            evalWithRetry(
                """
                return (async function(){
                    var s = ComicSource.sources['$key'];
                    return await s.comic.loadInfo(${gson.toJson(comicId)});
                })()
                """.trimIndent()
            ), mapType
        )
        val info = parseComicLoadInfo(comicId, env)
        // 详情封面与推荐封面统一过一遍缩略图钩子（与 call() 出口同一条纪律）：
        // ehentai 一族的详情封面在 s.exhentai.org，UI 的 Coil 直连不带源 Cookie，不改写就是 403。
        val covers = buildList {
            if (info.cover.isNotBlank()) add(info.cover)
            info.recommend.forEach { if (it.cover.isNotBlank()) add(it.cover) }
        }
        val rewritten = applyThumbnailHook(covers)
        if (rewritten.isEmpty()) return info
        rewritten.values.forEach { rw ->
            if (rw.headers.isNotEmpty()) onThumbnailHeaders?.invoke(rw.url, rw.headers)
        }
        return info.copy(
            cover = rewritten[info.cover]?.url ?: info.cover,
            recommend = info.recommend.map { r -> r.copy(cover = rewritten[r.cover]?.url ?: r.cover) },
        )
    }

    /** 预览缩略图那一页（源 `comic.loadThumbnails` 的答复；`next == null` = 到底）。 */
    data class ThumbPage(val thumbnails: List<String>, val next: String?)

    /**
     * 预览缩略图分页：源声明了 `comic.loadThumbnails` 才发那一跳（未声明的源交回空页，
     * 调用点照对岸「未声明」同效路径兜底）。URL 过一遍缩略图钩子（同 [details] 的成因）。
     */
    fun thumbnails(comicId: String, next: String?): ThumbPage {
        val env = gson.fromJson<Map<String, Any?>>(
            evalWithRetry(
                """
                return (async function(){
                    var s = ComicSource.sources['$key'];
                    if (!s.comic || !s.comic.loadThumbnails) return { thumbnails: [] };
                    return await s.comic.loadThumbnails(${gson.toJson(comicId)}, ${gson.toJson(next)});
                })()
                """.trimIndent()
            ), mapType
        )
        val data = env["data"] as? Map<*, *> ?: return ThumbPage(emptyList(), null)
        val thumbs = (data["thumbnails"] as? List<*>)?.mapNotNull { it?.toString() }.orEmpty()
        val nextToken = data["next"]?.toString()?.takeIf { it.isNotBlank() && it != "null" }
        val rewritten = applyThumbnailHook(thumbs)
        rewritten.values.forEach { rw ->
            if (rw.headers.isNotEmpty()) onThumbnailHeaders?.invoke(rw.url, rw.headers)
        }
        return ThumbPage(
            if (rewritten.isEmpty()) thumbs else thumbs.map { rewritten[it]?.url ?: it },
            nextToken,
        )
    }

    /** 页表（图片键，可能带 `#crop` 之类后缀，交给 onImageLoad 之前原样传） */
    /**
     * 读一页评论：`comic.loadComments(comicId, subId, page, replyTo)`（退 `comments.load` 再退 `comments`）。
     * 怎么把信封读成「列表 + maxPage」全在 [parseCommentPage]（纯函数，判据直接喂假信封），这里只发一次 JS。
     *
     * 三档兜底逐字对齐 Android 侧 `JsComicSource.loadCommentsPage`（`:1444-1446`）：源声明哪条腿就走哪条，
     * 三条都没有就抛「当前源暂不支持加载评论」—— 与 `pageImage` 对 `onImageLoad`「声明了却调用失败才抛、
     * 没声明走原路」是**相反**的一族：评论没有"原路可退"，所以那一格要靠 [commentCapabilities] 先判、
     * 不支持就不摆那一枚入口，而不是点下去抛错（本仓禁假把手）。
     */
    fun comments(comicId: String, subId: String?, page: Int, replyTo: String?): DesktopCommentPage {
        val env = gson.fromJson<Map<String, Any?>>(
            host.evaluateAsync(
                """
                return (async function(){
                    var s = ComicSource.sources['$key'];
                    var args = [${gson.toJson(comicId)}, ${gson.toJson(subId)}, $page, ${gson.toJson(replyTo)}];
                    if (typeof s?.comic?.loadComments === 'function') return await s.comic.loadComments.apply(s.comic, args);
                    if (typeof s?.comments?.load === 'function') return await s.comments.load.apply(s.comments, args);
                    if (typeof s?.comments === 'function') return await s.comments.apply(null, args);
                    throw new Error("当前源暂不支持加载评论");
                })()
                """.trimIndent()
            ), mapType
        )
        return parseCommentPage(env)
    }

    /**
     * 这一颗源支不支持读 / 发评论（屏上「摆不摆那一枚评论入口」读它）。求值解不开时如实回全 false，
     * 与对岸 `getCommentCapabilities` catch 到异常回默认值同一口径（宁可少摆一枚把手，不摆按了抛错的假把手）。
     */
    fun commentCapabilities(): DesktopCommentCapabilities {
        val env = runCatching {
            gson.fromJson<Map<String, Any?>>(
                host.evaluateAsync(
                    """
                    return (async function(){
                        var s = ComicSource.sources['$key'];
                        return {
                            canLoad: !!s && (typeof s.comic?.loadComments === 'function' || typeof s.comments?.load === 'function' || typeof s.comments === 'function'),
                            canSend: !!s && (typeof s.comic?.sendComment === 'function' || typeof s.comments?.send === 'function')
                        };
                    })()
                    """.trimIndent()
                ), mapType
            )
        }.getOrNull()
        val data = env?.get("data") as? Map<*, *> ?: return DesktopCommentCapabilities()
        return DesktopCommentCapabilities(
            canLoad = data["canLoad"] == true,
            canSend = data["canSend"] == true,
        )
    }

    /**
     * 发一条详情评论：先走 `comic.sendComment(comicId, subId, content, replyTo)`，退 `comments.send` 同参。
     * 三档判据与 [commentCapabilities] 的 `canSend` 一字对齐 —— 能力位说能发，这里就真有腿可走。
     *
     * @param comicId 作品 id（详情页透传）
     * @param subId 源详情声明的子作品 id，可为 null（部分源按 subId 定位评论区）
     * @param content 评论正文，非空由调用方先校验
     * @param replyTo 被回复的评论 id；顶层评论传 null
     * @return true = 源接受了这条评论；false = 源脚本没有声明发送入口
     * @throws IllegalStateException 源脚本自己抛错（消息透传，由调用方翻成屏上文案）
     */
    fun sendComment(comicId: String, subId: String?, content: String, replyTo: String?): Boolean {
        val script = """
            return (async function(){
                var s = ComicSource.sources['$key'];
                var args = [${gson.toJson(comicId)}, ${gson.toJson(subId)}, ${gson.toJson(content)}, ${gson.toJson(replyTo)}];
                var r;
                if (typeof s?.comic?.sendComment === 'function') r = await s.comic.sendComment.apply(s.comic, args);
                else if (typeof s?.comments?.send === 'function') r = await s.comments.send.apply(s.comments, args);
                else return false;
                return r !== false;
            })()
        """.trimIndent()
        val raw = evalWithRetry(script)
        val env = runCatching { gson.fromJson<Map<String, Any?>>(raw, mapType) }.getOrNull()
        if (env != null && env["success"] == false) {
            error(env["error"]?.toString() ?: "发送评论失败")
        }
        return env?.get("data") == true
    }

    /**
     * 给作品打星：`comic.starRating(id, rating)`，退旧规范的 `comic.star` 同名参数。
     *
     * 源规范里的 rating 是 **0–10 分**（1 分 = 半颗星，见随包脚本注释），
     * 而界面与共享核吃的是 0–5 星 —— 这里统一乘 2 再交给源，屏与核不再各自换算。
     *
     * @param comicId 作品 id
     * @param rating 界面星级（0.0–5.0）
     * @return true = 源接受了这次评分；false = 源没有声明评分入口
     * @throws IllegalStateException 源脚本抛错（如未登录、令牌缺失）
     */
    fun starRating(comicId: String, rating: Float): Boolean {
        val sourceRating = rating * 2f
        val script = """
            return (async function(){
                var s = ComicSource.sources['$key'];
                var c = s && s.comic;
                var fn = c && (typeof c.starRating === 'function' ? c.starRating : (typeof c.star === 'function' ? c.star : null));
                if (!fn) return false;
                await fn.call(c, ${gson.toJson(comicId)}, ${gson.toJson(sourceRating)});
                return true;
            })()
        """.trimIndent()
        val raw = evalWithRetry(script)
        val env = runCatching { gson.fromJson<Map<String, Any?>>(raw, mapType) }.getOrNull()
        if (env != null && env["success"] == false) {
            error(env["error"]?.toString() ?: "评分失败")
        }
        return env?.get("data") == true
    }

    /**
     * 作品点赞 / 取消：`comic.likeComic(id, isLike)`，退旧规范的 `comic.like`。
     *
     * @param comicId 作品 id
     * @param isLike true 点赞，false 取消
     * @return true = 源接受了这次动作；false = 源没有声明点赞入口
     * @throws IllegalStateException 源脚本抛错（如未登录）
     */
    fun likeComic(comicId: String, isLike: Boolean): Boolean {
        val script = """
            return (async function(){
                var s = ComicSource.sources['$key'];
                var c = s && s.comic;
                if (!c) return false;
                if (typeof c.likeComic === 'function') { await c.likeComic(${gson.toJson(comicId)}, ${gson.toJson(isLike)}); return true; }
                if (typeof c.like === 'function') { await c.like(${gson.toJson(comicId)}, ${gson.toJson(isLike)}); return true; }
                return false;
            })()
        """.trimIndent()
        val raw = evalWithRetry(script)
        val env = runCatching { gson.fromJson<Map<String, Any?>>(raw, mapType) }.getOrNull()
        if (env != null && env["success"] == false) {
            error(env["error"]?.toString() ?: "点赞失败")
        }
        return env?.get("data") == true
    }

    /**
     * 这一颗源支不支持打分与作品点赞（详情屏据此摆不摆那两枚把手）。
     * 判据与 [starRating] / [likeComic] 的 JS 分支一致；求值解不开时如实回全 false。
     */
    fun writeCapabilities(): DesktopWriteCapabilities {
        val env = runCatching {
            gson.fromJson<Map<String, Any?>>(
                host.evaluateAsync(
                    """
                    return (async function(){
                        var s = ComicSource.sources['$key'];
                        var c = s && s.comic;
                        return {
                            canRate: !!c && (typeof c.starRating === 'function' || typeof c.star === 'function'),
                            canLike: !!c && (typeof c.likeComic === 'function' || typeof c.like === 'function')
                        };
                    })()
                    """.trimIndent()
                ), mapType
            )
        }.getOrNull()
        val data = env?.get("data") as? Map<*, *> ?: return DesktopWriteCapabilities()
        return DesktopWriteCapabilities(
            canRate = data["canRate"] == true,
            canLike = data["canLike"] == true,
        )
    }

    fun pages(comicId: String, episode: Episode, sourceKey: String = key): List<String> {
        val targetKey = ensureSourceLoaded(sourceKey.ifBlank { key })
        // 修复禁漫等源单话传 null 导致章节打不开问题：
        // 只有当没有章节 id 或章节 id 为空时才传 null；单话漫画真实 id 必须传给 loadEp
        val epArg = if (episode.placeholder && episode.id.isBlank()) "null" else gson.toJson(episode.id)
        val env = gson.fromJson<Map<String, Any?>>(
            evalWithRetry(
                """
                return (async function(){
                    var s = ComicSource.sources[${gson.toJson(targetKey)}];
                    if (!s || !s.comic) throw new Error('漫画源未加载或不支持章节读取: ' + ${gson.toJson(targetKey)});
                    return await s.comic.loadEp(${gson.toJson(comicId)}, $epArg, "");
                })()
                """.trimIndent()
            ), mapType
        )
        if (env["success"] == false) error("loadEp 失败: ${env["error"]}")
        val data = env["data"] as? Map<*, *> ?: error("loadEp 没有 data")
        // 页表的键有两家：jm 系回 `images`，多数源回 `pages`。**顺序与 Android 侧一致**
        // （`JsComicSource:642-643` 是先 images 后 pages）
        val list = ((data["images"] ?: data["pages"]) as? List<*> ?: emptyList<Any?>()).mapNotNull { it?.toString() }
        if (list.isEmpty()) {
            println("[session] loadEp 回了 0 页 ep=${if (episode.placeholder) "null" else episode.id} data键=${data.keys}")
        }
        return list
    }

    /**
     * 取一页并**按需去混淆**：`modifyImage` 里的 `const num` 就是 jm 的切块数（权威口径见
     * `doc` 与 `ImagePipelinePolicy`），`num<=1` 原样返回。
     * 还原不了时抛错，不把混淆图当正常图交回去。
     *
     * 第一百零九批：`comic.onImageLoad` 是**能力位**而不是必需 —— 随包 34 份脚本里 16 份没声明它
     * （goda / gelbooru / zaimanhua / wnacg / kavita / happy…）。没声明的那些，`loadEp` 交回的
     * 每一项本身就是完整地址，所以守卫那一档直接回 `{ __none: true }`；怎么把信封读成
     * 「地址 + 请求头 + 切块数」全在 [desktopImageRequestOf]，判据是 `DesktopImageLoadCapabilityTest`。
     * 守卫那一形与同文件 [coverBytes] 的缩略图守卫**同一颗**，不加第三种说法。
     */
    fun pageImage(imageKey: String, comicId: String, episodeId: String, sourceKey: String = key): ByteArray {
        val targetKey = ensureSourceLoaded(sourceKey.ifBlank { key })
        val env = gson.fromJson<Map<String, Any?>>(
            host.evaluateAsync(
                """
                return (async function(){
                    var s = ComicSource.sources[${gson.toJson(targetKey)}];
                    if (!s || !s.comic) throw new Error('漫画源未加载或不支持图片读取: ' + ${gson.toJson(targetKey)});
                    if (!s.comic.onImageLoad) return { __none: true };
                    return await s.comic.onImageLoad(
                        ${gson.toJson(imageKey)}, ${gson.toJson(comicId)}, ${gson.toJson(episodeId)}, undefined);
                })()
                """.trimIndent()
            ), mapType
        )
        val request = desktopImageRequestOf(imageKey, env)
        val (status, bytes) = host.fetchBytes(request.url, request.headers)
        if (status != 200 || bytes.isEmpty()) error("取页失败 http=$status bytes=${bytes.size}")
        if (request.scrambleBlocks <= 1) return bytes
        val img = try {
            JvmImageOps.decode(bytes)
        } catch (t: Throwable) {
            throw IllegalStateException(
                "页图解不开 num=${request.scrambleBlocks} 头=${JvmImageOps.magic(bytes)} " +
                    "解码器=[${JvmImageOps.readerFormats()}] <- ${t.message}",
                t,
            )
        }
        return JvmImageOps.descrambleBlocks(img, request.scrambleBlocks)
            ?: error("${img.height}px 不够切 ${request.scrambleBlocks} 块，去混淆无从谈起")
    }

    /**
     * 手填 cookie 那一形（批 88·下半）：把用户粘进来的那几枚交给**源自己的**
     * `account.loginWithCookies.validate(values)`，过了才落「已登录」那一格。
     *
     * ## 口径为什么不在这里重述
     *
     * 要哪几枚字段、什么算成功，全在源脚本里（`fields: [...]` 与 `validate` 的返回值），
     * 桌面**一个字都不认**：脚本名单随这一趟的答复一起回来（[CookieLogin.fields]），
     * 与批 83 读 `name = "…"` 那一行同一族做法。桌面写死一份字段名单的话，
     * 站方改名单时屏上照旧摆老几格而答案永远过不了。
     *
     * ## 信封形状照 Android 侧
     *
     * 对岸那颗 `JsComicSource.validateCookies`（`JsComicSource.kt:1838`）认的是
     * `env["success"] == true` 且 `data === true`，桌面沿用同一判式，只把"为什么没过"多带一句回来 ——
     * 那一格在屏上必须说得出话，只回 `false` 用户读到的是"点了没反应"。
     *
     * ⚠️ 源在 `validate` 里自己会走 `Network.setCookies`（ehentai 甚至先 `deleteCookies` 再 set），
     * 所以"先种凭据再校验"这一步由脚本做，桌面不代做（代做就是第二条轨）。
     */
    fun loginWithCookies(values: List<String>): CookieLogin {
        val script = """
            return (async function() {
                var s = ComicSource.sources['$key'];
                if (!s || !s.account || !s.account.loginWithCookies) {
                    return { ok: false, why: "该源没有 loginWithCookies 那一形", fields: [] };
                }
                if (typeof s.account.loginWithCookies.validate !== 'function') {
                    return { ok: false, why: "该源的 validate 不是函数", fields: [] };
                }
                var fields = Array.isArray(s.account.loginWithCookies.fields)
                    ? s.account.loginWithCookies.fields : [];
                var r = await s.account.loginWithCookies.validate(${gson.toJson(values)});
                return { ok: r === true, fields: fields };
            })()
        """.trimIndent()
        val raw = host.evaluateAsync(script)
        val env = runCatching { gson.fromJson<Map<String, Any?>>(raw, mapType) }.getOrNull()
            ?: return CookieLogin(false, "源那句 validate 的答复解不开", emptyList())
        if (env["success"] == false) {
            return CookieLogin(false, env["error"]?.toString() ?: "validate 执行失败", emptyList())
        }
        val data = env["data"] as? Map<*, *>
            ?: return CookieLogin(false, "validate 回的形状不认识", emptyList())
        val fields = (data["fields"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        if (data["ok"] != true) {
            return CookieLogin(false, data["why"]?.toString() ?: "源说这批凭据不算通过", fields)
        }
        host.markLoggedIn(key)
        return CookieLogin(true, "源认了这批凭据", fields)
    }

    /**
     * 网页登录那一形：读取源声明的 `account.loginWithWebview.url`。
     */
    fun webLoginUrl(): String? {
        // ⚠️ 走 `host.evaluate` 的脚本必须是**表达式**：顶层 `return` 在 `context.eval`
        // 里是 SyntaxError（evaluate 会把它咽成 null ⇒ 屏上念「url 为空」）。
        // evaluateAsync 那条路外面包着函数所以能写 return，这一条不能。
        val script = """
            (function() {
                var s = ComicSource.sources['$key'];
                if (!s || !s.account || !s.account.loginWithWebview) return null;
                var w = s.account.loginWithWebview;
                return (w && w.url) ? w.url : null;
            })()
        """.trimIndent()
        // evaluate 回的字符串裸值（decode 已剥引号），判 http 开头 + 非 null 字面量。
        val raw = host.evaluate(script) ?: return null
        return raw.trim().takeIf { it.startsWith("http") && it != "null" }
    }

    /**
     * 网页登录那一形：调用源声明的 `account.loginWithWebview.checkStatus(url, title)`。
     */
    fun checkWebLoginStatus(url: String, title: String): Boolean {
        val script = """
            return (async function() {
                var s = ComicSource.sources['$key'];
                if (!s || !s.account || !s.account.loginWithWebview) return false;
                var fn = s.account.loginWithWebview.checkStatus;
                if (typeof fn !== 'function') return false;
                try {
                    return (await fn(${gson.toJson(url)}, ${gson.toJson(title)})) === true;
                } catch(e) {
                    return false;
                }
            })()
        """.trimIndent()
        val raw = host.evaluateAsync(script)
        val env = runCatching { gson.fromJson<Map<String, Any?>>(raw, mapType) }.getOrNull()
        return env?.get("data") == true || raw.contains("\"data\":true")
    }

    /**
     * 网页登录成功回调：调用源声明的 `account.loginWithWebview.onLoginSuccess()` 并标记已登录。
     */
    fun onWebLoginSuccess(): Boolean {
        val script = """
            return (async function() {
                var s = ComicSource.sources['$key'];
                if (!s || !s.account || !s.account.loginWithWebview) return true;
                var fn = s.account.loginWithWebview.onLoginSuccess;
                if (typeof fn === 'function') {
                    try {
                        await fn();
                    } catch(e) {}
                }
                return true;
            })()
        """.trimIndent()
        host.evaluateAsync(script)
        host.markLoggedIn(key)
        return true
    }

    /**
     * 账号密码那一形：直接调用源自己的 `account.login(account, pwd)`。
     *
     * 判据与 Android `JsComicSource.login` 对齐：脚本抛出或返回带 `error` 的对象都算失败；
     * 成功后把账号密码按 Android 同一形状存进源数据罐，供 `relogin()` 使用。
     * 桌面不猜哪颗源支持账密 —— 能力位由 `DesktopSourceNames.supportsPasswordLoginOf`
     * 从脚本文本现取，这一跳只负责真正执行。
     */
    fun loginWithPassword(account: String, password: String): PasswordLogin {
        val script = """
            return (async function() {
                var s = ComicSource.sources['$key'];
                if (!s || !s.account || typeof s.account.login !== 'function') {
                    return { ok: false, why: "该源没有账号密码登录那一形" };
                }
                var res = await s.account.login(
                    ${gson.toJson(account)},
                    ${gson.toJson(password)}
                );
                if (res && res.error) {
                    return {
                        ok: false,
                        why: res.errorMessage || res.error || "登录失败"
                    };
                }
                return { ok: true };
            })()
        """.trimIndent()
        val raw = host.evaluateAsync(script)
        val env = runCatching { gson.fromJson<Map<String, Any?>>(raw, mapType) }.getOrNull()
            ?: return PasswordLogin(false, "源那句 login 的答复解不开")
        if (env["success"] == false) {
            return PasswordLogin(false, env["error"]?.toString() ?: "login 执行失败")
        }
        val data = env["data"] as? Map<*, *>
            ?: return PasswordLogin(false, "login 回的形状不认识")
        if (data["ok"] != true) {
            return PasswordLogin(false, data["why"]?.toString() ?: "源说这组账号密码没登上")
        }
        host.dataStoreOf().saveAccount(key, listOf(account, password))
        return PasswordLogin(true, "源认了这组账号密码")
    }

    /**
     * 对齐 Android 侧 `JsComicSource.relogin`：
     * 读取罐里存的账号密码凭据，调用 `loginWithPassword` 刷新 session。
     */
    fun relogin(): Boolean = runCatching {
        val accountData = host.dataStoreOf().getAccount(key)
        if (accountData is List<*> && accountData.size >= 2) {
            val user = accountData[0]?.toString() ?: ""
            val pwd = accountData[1]?.toString() ?: ""
            if (user.isNotBlank() && pwd.isNotBlank()) {
                val res = loginWithPassword(user, pwd)
                return res.ok
            }
        }
        false
    }.getOrDefault(false)

    /**
     * 自动捕获 `Login expired` 并尝试一次 `relogin()` 后重试。
     * 对齐 Android `retryZone`（禁漫/jm 等源 token 过期抛 Login expired 时的自愈机制）。
     */
    internal fun evalWithRetry(script: String, timeoutMs: Long = 30_000L): String {
        var raw = host.evaluateAsync(script, timeoutMs = timeoutMs)
        if (raw.contains("Login expired")) {
            println("[session] 捕获 Login expired，尝试自动 relogin key=$key")
            if (relogin()) {
                println("[session] relogin 成功，重新执行 script")
                raw = host.evaluateAsync(script, timeoutMs = timeoutMs)
            }
        }
        return raw
    }

    /** 手填 cookie 那一跑的三样结果。[fields] 是脚本自己那份名单（屏上回核与"摆哪几格"都读它）。 */
    data class CookieLogin(val ok: Boolean, val note: String, val fields: List<String>)

    /** 账号密码登录的结果。 */
    data class PasswordLogin(val ok: Boolean, val note: String)

    // 源设置运行时读写：定义解析在 DesktopSourceSettings，默认值已在 load() 时注册。

    /**
     * 这一颗源**此刻**的设置值（罐里用户改过的 + 出厂默认兜底后的那份）。
     *
     * 交回**信封原文**（Map），字符串化交由桌面侧 `DesktopSourceSettings.stringifyValues` ——
     * 那一颗是纯函数，判据喂假 Map 进得来；这里只发一次 JS。
     */
    fun loadSettingsAll(): Map<String, Any?> {
        val env = gson.fromJson<Map<String, Any?>>(
            host.evaluateAsync(
                """
                return (async function(){
                    var s = ComicSource.sources['$key'];
                    if (!s || !s.settings) return {};
                    var out = {};
                    for (var k in s.settings) {
                        if (typeof s.settings[k] === 'object' && s.settings[k] !== null &&
                            typeof s.settings[k].default !== 'undefined' &&
                            typeof s.settings[k].type === 'string') {
                            out[k] = { type: s.settings[k].type, default: s.settings[k].default };
                        }
                    }
                    return out;
                })()
                """.trimIndent()
            ), mapType
        )
        if (env["success"] == false) error("读设置失败: ${env["error"]}")
        val defs = (env["data"] as? Map<*, *>)?.entries?.associate {
            it.key.toString() to ((it.value as? Map<*, *>)?.get("default"))
        } ?: emptyMap()
        val stored = HashMap<String, Any?>()
        defs.forEach { (settingKey, fallback) ->
            stored[settingKey] = host.dataStoreOf().loadSetting(key, settingKey) ?: fallback
        }
        return stored
    }

    /** 写一枚设置值（值必须是 JS 原生形状 —— switch 写真布尔，select/input 写串）。 */
    fun saveSettingValue(settingKey: String, value: Any?) {
        host.dataStoreOf().saveSetting(key, settingKey, value)
    }

    /**
     * 在设置变更后轻量同步运行期状态（例如 `jm` 切换图片分流后立刻更新内存中的图床域），
     * 避免要求用户重启才能生效；刷新失败不阻塞保存动作。
     */
    fun applySettingLive(settingKey: String) {
        if (key == "jm" && settingKey == "imageStream") {
            runCatching {
                host.evaluateAsync(
                    """
                    return (async function(){
                        var s = ComicSource.sources['$key'];
                        if (s && s.refreshImgUrl) {
                            await s.refreshImgUrl(false);
                        }
                        return null;
                    })()
                    """.trimIndent(),
                    timeoutMs = 8_000L,
                )
            }
        }
    }

    /**
     * 跑 callback 档那一枚：调源脚本里 `settings[key].callback`。回话若为字符串则原样交回
     * （jm 的 refreshDomains 回「刷新了几条」那种人话）；非串/无回话交回 null。
     */
    fun runSettingCallback(settingKey: String): String? {
        val raw = host.evaluateAsync(
            """
            return (async function(){
                var s = ComicSource.sources['$key'];
                    var item = s && s.settings ? s.settings[${gson.toJson(settingKey)}] : null;
                if (!item || typeof item.callback !== 'function') {
                    return { ok: false, why: "这一颗源没有那枚回调" };
                }
                var r = await item.callback();
                return { ok: true, note: typeof r === 'string' ? r : null };
            })()
            """.trimIndent()
        )
        val env = runCatching { gson.fromJson<Map<String, Any?>>(raw, mapType) }.getOrNull()
            ?: return null
        if (env["success"] == false) error(env["error"]?.toString() ?: "回调执行失败")
        val data = env["data"] as? Map<*, *> ?: return null
        if (data["ok"] != true) error(data["why"]?.toString() ?: "这一颗源没有那枚回调")
        return data["note"]?.toString()
    }

    override fun close() = host.close()
}
