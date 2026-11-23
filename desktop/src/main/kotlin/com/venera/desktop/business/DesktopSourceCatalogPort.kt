package com.venera.desktop.business

import com.venera.compose.data.api.SourceCatalog
import com.venera.compose.source.ComicSource
import com.venera.desktop.DESKTOP_EXPLORE_SOURCE
import com.venera.desktop.gallery.data.DesktopSourceNames
import com.venera.engineprobe.EngineAssets
import com.venera.engineprobe.EngineSession
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * `SourceCatalog` 的**桌面实现**（D′-1）：把随包那批 JS 源映射成轻量 [DesktopEngineComicSource]，
 * 喂给 `ComicDetailCore`。
 *
 * 为什么这颗必须真列出全部源而不只当前那一颗：`ComicDetailCore.resolveSourceKey(sourceName)`
 * 拿 `sourcesFlow.value` 按 `key`/`name` 找真 key（核 :1006）。桌面 `DesktopRoute.Detail` 带的是
 * **sourceKey**，壳拼 `ComicItem.sourceName = route.sourceKey`，所以列表里必须有一颗 `key` 与之相等，
 * `resolveSourceKey` 才认得回这一颗 —— 列表空 = 详情永远退到 `activeSourceKey`，跨源会取错本。
 *
 * 其余成员（ping 延迟、更新、搜索目标）详情不吃：恒空表 / NoOp，点名降级。
 */
internal class DesktopSourceCatalogPort(
    private val engine: EngineSession?,
    private val assetDir: File,
    private val overrideDir: File?,
) : SourceCatalog {

    private val keys: List<String> = runCatching { EngineAssets.listSourceKeys(assetDir).sorted() }
        .getOrDefault(emptyList())

    /** 一颗 key 缓存一颗适配器：适配器无状态（每次成员调用各自 load 上锁），复用安全。 */
    private val cache = ConcurrentHashMap<String, ComicSource>()

    private val sourceList: List<ComicSource> = keys.map { key ->
        cache.getOrPut(key) { DesktopEngineComicSource(key, engine, assetDir, overrideDir) }
    }

    private val _sourcesFlow = MutableStateFlow(sourceList)
    override val sourcesFlow: StateFlow<List<ComicSource>> = _sourcesFlow.asStateFlow()

    // 兜底档（resolveSourceKey 找不到时读它）：桌面默认探索源，详情几乎走不到这一路。
    private val _activeSourceKey = MutableStateFlow(DESKTOP_EXPLORE_SOURCE)
    override val activeSourceKey: StateFlow<String> = _activeSourceKey.asStateFlow()

    override val latencyMapFlow: StateFlow<Map<String, Long>> =
        MutableStateFlow<Map<String, Long>>(emptyMap()).asStateFlow()

    override val availableUpdates: StateFlow<Map<String, String>> =
        MutableStateFlow<Map<String, String>>(emptyMap()).asStateFlow()

    override fun getSource(key: String): ComicSource? =
        cache[key] ?: if (key in keys) {
            cache.getOrPut(key) { DesktopEngineComicSource(key, engine, assetDir, overrideDir) }
        } else {
            null
        }

    override suspend fun sourceSupportsLogin(key: String): Boolean {
        val raw = DesktopSourceNames.scriptOf(assetDir, key, overrideDir) ?: return false
        return DesktopSourceNames.cookieFieldsOf(raw).isNotEmpty() ||
            DesktopSourceNames.supportsPasswordLoginOf(raw) ||
            DesktopSourceNames.supportsWebLoginOf(raw)
    }

    override fun searchTargets(): List<ComicSource> = sourceList

    override fun refreshPings() {}

    override suspend fun checkUpdates(force: Boolean): Int = 0
}
