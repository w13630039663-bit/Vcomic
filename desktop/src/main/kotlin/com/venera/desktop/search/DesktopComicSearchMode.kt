package com.venera.desktop.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.venera.compose.data.platform.JsonKeyValueStore
import com.venera.compose.data.platform.PathProvider
import com.venera.desktop.settings.DesktopI18n
import com.venera.engineprobe.EngineSession

/** 漫画搜索目标：单源深搜，或自定义多源聚合（全选即全网聚合）。 */
internal sealed interface DesktopSearchTarget {
    /** 单源深搜：具备按源动态筛选与自动续页能力。 */
    data class Single(val sourceKey: String) : DesktopSearchTarget

    /**
     * 多源聚合：按选中源流式分泳道检索。
     *
     * @param selectedKeys 参与聚合的源键集合；[allEnabled] 为 true 时跟随全部已启用源。
     * @param allEnabled 是否处于「全网聚合」动态模式（启用新源自动纳入）。
     */
    data class Aggregate(
        val selectedKeys: Set<String>,
        val allEnabled: Boolean = false,
    ) : DesktopSearchTarget
}

/** 聚合泳道四态，排序优先级由高到低：有结果 → 检索中 → 失败 → 无结果。 */
internal enum class AggregateLaneStatus(val sortRank: Int) {
    Ready(0),
    Loading(1),
    Failed(2),
    Empty(3),
}

/** 单个漫画源在聚合结果里的泳道快照。 */
internal data class DesktopAggregateLaneState(
    val sourceKey: String,
    val sourceLabel: String,
    val status: AggregateLaneStatus,
    val cards: List<EngineSession.ComicCard> = emptyList(),
    val error: String? = null,
)

/**
 * 聚合搜索壳层状态容器。收进一枚 holder 而不是多枚独立 remember ——
 * 避免撑爆 `VeneraDesktopLargeScreen` 顶层 lambda 的 JVM 253 槽捕获上限。
 */
internal class DesktopAggregateSearchHolder {
    var target by mutableStateOf<DesktopSearchTarget>(
        DesktopSearchTarget.Aggregate(selectedKeys = emptySet(), allEnabled = true),
    )
    var hydrated by mutableStateOf(false)
    var lanes by mutableStateOf<Map<String, DesktopAggregateLaneState>>(emptyMap())
    var skippedSelfHosted by mutableStateOf<List<String>>(emptyList())
    var onlyHits by mutableStateOf(false)
    var searching by mutableStateOf(false)
    var hasSearched by mutableStateOf(false)
    var searchTick by mutableIntStateOf(0)
    var retrySourceKey by mutableStateOf<String?>(null)
    var retryTick by mutableIntStateOf(0)
}

internal const val DESKTOP_SEARCH_TARGET_PREF_KEY = "pref_desktop_search_target"

/**
 * 编码搜索目标为紧凑串，便于落入轻量键值库：
 * - `all`：全网聚合（动态跟随全部已启用源）
 * - `single:<key>`：单源
 * - `multi:<k1>,<k2>`：自定义多源子集
 */
internal fun encodeSearchTarget(target: DesktopSearchTarget): String = when (target) {
    is DesktopSearchTarget.Single -> "single:${target.sourceKey}"
    is DesktopSearchTarget.Aggregate ->
        if (target.allEnabled) "all"
        else "multi:${target.selectedKeys.joinToString(",")}"
}

/** 解码落盘串；首次使用（null / 空 / 非法格式）一律回落全网聚合。 */
internal fun decodeSearchTarget(raw: String?): DesktopSearchTarget {
    val text = raw?.trim().orEmpty()
    return when {
        text.isEmpty() || text == "all" ->
            DesktopSearchTarget.Aggregate(selectedKeys = emptySet(), allEnabled = true)
        text.startsWith("single:") -> {
            val key = text.removePrefix("single:").trim()
            if (key.isEmpty()) DesktopSearchTarget.Aggregate(emptySet(), allEnabled = true)
            else DesktopSearchTarget.Single(key)
        }
        text.startsWith("multi:") -> {
            val keys = text.removePrefix("multi:")
                .split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toCollection(LinkedHashSet())
            when (keys.size) {
                0 -> DesktopSearchTarget.Aggregate(emptySet(), allEnabled = true)
                1 -> DesktopSearchTarget.Single(keys.first())
                else -> DesktopSearchTarget.Aggregate(keys, allEnabled = false)
            }
        }
        else -> DesktopSearchTarget.Aggregate(emptySet(), allEnabled = true)
    }
}

/**
 * 结合当前「源管理开启账」校验目标：剔除已停用源、单源退化与全网动态同步。
 *
 * @param saved 当前记忆或用户提交的目标。
 * @param enabledKeys 已启用源键列表（按源管理原序）。
 */
internal fun resolveSearchTarget(
    saved: DesktopSearchTarget,
    enabledKeys: List<String>,
): DesktopSearchTarget {
    if (enabledKeys.isEmpty()) return saved
    val enabledSet = enabledKeys.toSet()
    return when (saved) {
        is DesktopSearchTarget.Single -> {
            if (saved.sourceKey in enabledSet) saved
            else DesktopSearchTarget.Single(enabledKeys.first())
        }
        is DesktopSearchTarget.Aggregate -> {
            if (enabledKeys.size == 1) {
                DesktopSearchTarget.Single(enabledKeys.first())
            } else if (saved.allEnabled) {
                DesktopSearchTarget.Aggregate(enabledKeys.toCollection(LinkedHashSet()), allEnabled = true)
            } else {
                val alive = enabledKeys.filter { it in saved.selectedKeys }.toCollection(LinkedHashSet())
                when {
                    alive.isEmpty() ->
                        DesktopSearchTarget.Aggregate(enabledKeys.toCollection(LinkedHashSet()), allEnabled = true)
                    alive.size == 1 ->
                        DesktopSearchTarget.Single(alive.first())
                    alive.size == enabledKeys.size ->
                        DesktopSearchTarget.Aggregate(alive, allEnabled = true)
                    else ->
                        DesktopSearchTarget.Aggregate(alive, allEnabled = false)
                }
            }
        }
    }
}

/**
 * 规范化多源勾选提交：0 源返回 null（不允许确认），1 源自动降级单源，全选标为全网聚合。
 *
 * @param pickedKeys 用户在弹出面板勾选的源键。
 * @param enabledKeys 全部已启用源键列表。
 */
internal fun normalizeMultiSelection(
    pickedKeys: Set<String>,
    enabledKeys: List<String>,
): DesktopSearchTarget? {
    val ordered = enabledKeys.filter { it in pickedKeys }.toCollection(LinkedHashSet())
    return when {
        ordered.isEmpty() -> null
        ordered.size == 1 -> DesktopSearchTarget.Single(ordered.first())
        ordered.size == enabledKeys.size -> DesktopSearchTarget.Aggregate(ordered, allEnabled = true)
        else -> DesktopSearchTarget.Aggregate(ordered, allEnabled = false)
    }
}

/**
 * 按「有结果 → 检索中 → 失败 → 空结果」四层排序，同层维持源管理原序。
 *
 * @param lanes 各源当前快照映射。
 * @param sourceOrder 源管理清单顺序。
 */
internal fun sortAggregateLanes(
    lanes: Map<String, DesktopAggregateLaneState>,
    sourceOrder: List<String>,
): List<DesktopAggregateLaneState> {
    val orderIndex = sourceOrder.withIndex().associate { it.value to it.index }
    return lanes.values.sortedWith(
        compareBy<DesktopAggregateLaneState> { it.status.sortRank }
            .thenBy { orderIndex[it.sourceKey] ?: Int.MAX_VALUE }
            .thenBy { it.sourceKey },
    )
}

/** 读上次搜索目标偏好；数据根未就绪时默认回落全网聚合。 */
internal fun readSearchTarget(paths: PathProvider?): DesktopSearchTarget {
    if (paths == null) return DesktopSearchTarget.Aggregate(emptySet(), allEnabled = true)
    val raw = JsonKeyValueStore(DesktopSearchViewMode.STORE, paths)
        .getString(DESKTOP_SEARCH_TARGET_PREF_KEY, "")
    return decodeSearchTarget(raw)
}

/** 持久化记住上次单源或自定义多源聚合目标。 */
internal fun writeSearchTarget(paths: PathProvider?, target: DesktopSearchTarget) {
    if (paths == null) return
    JsonKeyValueStore(DesktopSearchViewMode.STORE, paths)
        .put(DESKTOP_SEARCH_TARGET_PREF_KEY, encodeSearchTarget(target))
}

/** 源选择器顶部的分级过滤标签。 */
internal enum class DesktopSearchSourceFilterTab {
    ALL,
    SAFE,
    R18,
    ;

    val label: String
        get() = when (this) {
            ALL -> DesktopI18n.tr("全部", "All")
            SAFE -> DesktopI18n.tr("正常源", "Safe")
            R18 -> DesktopI18n.tr("R18源", "R18")
        }
}

/**
 * 判定搜索源是否属于 R18 / 受限向，直接复用 [com.venera.desktop.DesktopSourceContentPresets] 单源分级表。
 * 同时剥离多账号变体后缀（如 `_multi_accounts`），确保同一站点的多账号克隆与主站分级一致。
 */
internal fun isR18SearchSource(sourceKey: String): Boolean {
    val trimmed = sourceKey.trim()
    if (trimmed.isEmpty()) return false
    val presets = com.venera.desktop.DesktopSourceContentPresets
    val levels = presets.levels
    if (presets.lastError != null) return presets.isGuarded(trimmed)
    val baseKey = trimmed.removeSuffix("_multi_accounts")
    val level = levels[trimmed] ?: levels[baseKey] ?: return false
    return level == "nsfw" || level == "mixed"
}

/**
 * 供源选择器渲染的分组结果：保持原始顺序列出可见源，并拆出正常源与 R18 源两段。
 */
internal data class GroupedSearchSourceOptions(
    val flat: List<DesktopSearchSourceOption>,
    val safe: List<DesktopSearchSourceOption>,
    val r18: List<DesktopSearchSourceOption>,
    val totalSafeCount: Int,
    val totalR18Count: Int,
)

/**
 * 按关键词与分级标签过滤并分组源列表，保留源管理原有顺序。
 *
 * @param options 全部已启用源。
 * @param query 搜索框过滤词。
 * @param tab 当前选中的分级标签（全部 / 正常源 / R18源）。
 * @param r18Resolver 分级判定函数，默认读内置分级表，便于单测注入。
 */
internal fun filterAndGroupSearchSources(
    options: List<DesktopSearchSourceOption>,
    query: String,
    tab: DesktopSearchSourceFilterTab,
    r18Resolver: (String) -> Boolean = ::isR18SearchSource,
): GroupedSearchSourceOptions {
    val q = query.trim().lowercase()
    var totalSafe = 0
    var totalR18 = 0
    val safeList = ArrayList<DesktopSearchSourceOption>()
    val r18List = ArrayList<DesktopSearchSourceOption>()
    val flatList = ArrayList<DesktopSearchSourceOption>()

    for (opt in options) {
        val isR18 = r18Resolver(opt.key)
        if (isR18) totalR18++ else totalSafe++
        val matchTab = when (tab) {
            DesktopSearchSourceFilterTab.ALL -> true
            DesktopSearchSourceFilterTab.SAFE -> !isR18
            DesktopSearchSourceFilterTab.R18 -> isR18
        }
        if (!matchTab) continue
        val matchQuery = q.isEmpty() ||
            opt.label.lowercase().contains(q) ||
            opt.key.lowercase().contains(q)
        if (!matchQuery) continue
        flatList.add(opt)
        if (isR18) r18List.add(opt) else safeList.add(opt)
    }

    return GroupedSearchSourceOptions(
        flat = flatList,
        safe = safeList,
        r18 = r18List,
        totalSafeCount = totalSafe,
        totalR18Count = totalR18,
    )
}

/**
 * 生成当前聚合目标的简短显示名：全选显示「全网聚合」，恰好只覆盖全部正常源/全部 R18 源时点明分组，
 * 其余自定义组合显示「已选 N 源」。
 */
internal fun describeAggregateLabel(
    activeKeys: List<String>,
    allOptions: List<DesktopSearchSourceOption>,
    r18Resolver: (String) -> Boolean = ::isR18SearchSource,
): String {
    if (allOptions.isEmpty() || activeKeys.size == allOptions.size) return DesktopI18n.tr("全网聚合", "All Sources")
    val safeKeys = allOptions.filter { !r18Resolver(it.key) }.map { it.key }
    val r18Keys = allOptions.filter { r18Resolver(it.key) }.map { it.key }
    val activeSet = activeKeys.toSet()
    return when {
        safeKeys.size >= 2 && r18Keys.isNotEmpty() && activeSet == safeKeys.toSet() -> DesktopI18n.tr("正常源聚合", "Safe Sources")
        r18Keys.size >= 2 && safeKeys.isNotEmpty() && activeSet == r18Keys.toSet() -> DesktopI18n.tr("R18 聚合", "R18 Sources")
        else -> DesktopI18n.tr("已选 ${activeKeys.size} 源", "${activeKeys.size} Selected")
    }
}

/** 自建私服源的默认演示地址表：用户未配置自定义服务器地址前，全网聚合应跳过以避免白等超时。 */
private val SELF_HOSTED_DEFAULT_ENDPOINTS: Map<String, Pair<String, String>> = mapOf(
    "kavita" to ("base_url" to "https://demo.kavita.org"),
    "komga" to ("base_url" to "https://demo.komga.org"),
    "lanraragi" to ("api" to "http://lrr.tvc-16.science"),
)

/**
 * 判断源是否为「尚未配置自建服务器地址的私服源」。
 *
 * @param sourceKey 源键名。
 * @param settingValueOf 按 `(sourceKey, settingKey)` 读取用户已保存设置值的回调。
 */
internal fun isUnconfiguredSelfHostedSource(
    sourceKey: String,
    settingValueOf: (sourceKey: String, settingKey: String) -> String?,
): Boolean {
    val rule = SELF_HOSTED_DEFAULT_ENDPOINTS[sourceKey.lowercase()] ?: return false
    val (settingKey, demoDefault) = rule
    val current = settingValueOf(sourceKey, settingKey)?.trim()?.trimEnd('/').orEmpty()
    if (current.isEmpty()) return true
    return current.equals(demoDefault.trimEnd('/'), ignoreCase = true)
}

/** 聚合目标过滤结果：[active] 为实际参与检索的源，[skippedSelfHosted] 为全选时自动跳过的未配置私服源键。 */
internal data class AggregateTargetPlan<T>(
    val active: List<T>,
    val skippedSelfHosted: List<String>,
)

/**
 * 根据聚合选择模式决定本趟实际参与检索的源列表。
 * 全选模式下自动跳过未配置服务器地址的私服源；手动指定子集时完全尊重用户勾选。
 */
internal fun <T> planAggregateTargets(
    enabledRows: List<T>,
    aggTarget: DesktopSearchTarget.Aggregate,
    keyOf: (T) -> String,
    isUnconfiguredSelfHosted: (String) -> Boolean,
): AggregateTargetPlan<T> {
    val isAllMode = aggTarget.allEnabled || aggTarget.selectedKeys.isEmpty()
    if (!isAllMode) {
        val picked = enabledRows.filter { keyOf(it) in aggTarget.selectedKeys }.ifEmpty { enabledRows }
        return AggregateTargetPlan(active = picked, skippedSelfHosted = emptyList())
    }
    val skipped = mutableListOf<String>()
    val kept = mutableListOf<T>()
    for (row in enabledRows) {
        val key = keyOf(row)
        if (isUnconfiguredSelfHosted(key)) {
            skipped += key
        } else {
            kept += row
        }
    }
    if (kept.isEmpty()) {
        return AggregateTargetPlan(active = enabledRows, skippedSelfHosted = emptyList())
    }
    return AggregateTargetPlan(active = kept, skippedSelfHosted = skipped)
}
