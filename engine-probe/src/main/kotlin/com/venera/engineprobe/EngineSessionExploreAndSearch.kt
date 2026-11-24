package com.venera.engineprobe

import com.venera.engineprobe.EngineSession.Category
import com.venera.engineprobe.EngineSession.CategoryBoard
import com.venera.engineprobe.EngineSession.CategoryChip
import com.venera.engineprobe.EngineSession.CategoryPart
import com.venera.engineprobe.EngineSession.Page
import com.venera.engineprobe.EngineSession.SearchOption

/** 探索页：支持数组探索模式与单对象探索两种源脚本写法。 */
internal fun EngineSession.execExplore(page: Int = 1): Page = call(
    """
    return (async function() {
        var s = ComicSource.sources['$key'];
        if (!s.explore) return { comics: [], __err: "该源没有 explore" };
        var r = Array.isArray(s.explore)
            ? await s.explore[${page.coerceAtLeast(1) - 1}].load($page)
            : await s.explore.load();
        return r || { comics: [] };
    })()
    """,
)

/** 按探索模式下标取一页。 */
internal fun EngineSession.execExplore(index: Int, page: Int = 1): Page = call(
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
    """,
)

/** 读取当前源声明的探索模式标题列表。 */
internal fun EngineSession.execExploreModes(): List<String> {
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
        """.trimIndent(),
    ) ?: return emptyList()
    return runCatching {
        gson.fromJson<List<String>>(raw, listType)?.filter { it.isNotBlank() } ?: emptyList()
    }.getOrDefault(emptyList())
}

/** 解析当前源分类面板分组及排行榜档位。 */
internal fun EngineSession.execCategoryBoard(): CategoryBoard = runCatching {
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
        """.trimIndent(),
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

/** 按分类 chip 加载一页结果（区分 search 型与 categoryComics 型接口）。 */
internal fun EngineSession.execLoadCategory(category: String, param: String?, kind: String, page: Int = 1): Page = call(
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
            if (s.search.load) return (await s.search.load(category, so, $page)) || { comics: [] };
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
        return (await cc.load(category, param, opts, $page)) || { comics: [] };
    })()
    """,
)

/** 加载分类排行榜单页。 */
internal fun EngineSession.execLoadRanking(option: String, page: Int = 1): Page = call(
    """
    return (async function() {
        var s = ComicSource.sources['$key'];
        var r = s && s.categoryComics && s.categoryComics.ranking;
        if (!r) return { comics: [], __err: "该源没有排行榜" };
        if (r.load) return (await r.load(${gson.toJson(option)}, $page)) || { comics: [] };
        if (r.loadWithNext) return (await r.loadWithNext(${gson.toJson(option)}, $page === 1 ? null : String($page))) || { comics: [] };
        return { comics: [], __err: "该源没有排行榜" };
    })()
    """,
)

/** 加载旧式 `categoryExplore[index]` 分页。 */
internal fun EngineSession.execExploreCategory(index: Int, page: Int = 1): Page {
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
        """,
    )
}

/** 列出当前源的 `categoryExplore` 分类目录。 */
internal fun EngineSession.execCategories(): List<Category> {
    val raw = host.evaluate(
        """
        JSON.stringify((function() {
            var s = ComicSource.sources['$key'];
            if (!s.categoryExplore || !Array.isArray(s.categoryExplore)) return [];
            return s.categoryExplore.map(function(c, i) {
                return { name: c.name || '', index: i, types: c.types || [] };
            });
        })())
        """.trimIndent(),
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

/** 解析 `search.optionList` 高级筛选选项及源声明默认值。 */
internal fun EngineSession.execSearchOptions(): List<SearchOption> {
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
        """.trimIndent(),
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

/** 带显式筛选选项数组执行搜索（支持页码型与游标型续页）。 */
internal fun EngineSession.execSearchWith(keyword: String, options: List<String?>, page: Int = 1, next: String? = null): Page = call(
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
    """,
)

/** 使用源默认筛选选项执行搜索。 */
internal fun EngineSession.execSearch(keyword: String, page: Int = 1, timeoutMs: Long = 30_000L): Page = call(
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
