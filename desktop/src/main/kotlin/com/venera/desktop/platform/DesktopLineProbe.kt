package com.venera.desktop.platform

import com.venera.compose.data.platform.PreferenceKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * 线路延迟读数（档二 5b-4）。
 *
 * 对岸那颗 PreferredIp 一族绑 `data.network`（桌面禁引族，判据整域 no-op），这里不搬优选 IP
 * 那一整套；只做一条**最小真读数**：向 Gelbooru DAPI 打一枚 limit=1 的极小请求
 * （基址与对岸 `GelbooruClient.BASE` 同串），量一次往返毫秒并落盘 ——
 * 总览「网络」舱与设置行念的就是这一枚真数，没测过念「--」，不画假数。
 *
 * 走装配点递进来的 OkHttp（与取图同一台 ⇒ 代理/缓存档同受益）；账本键 `desktop_last_probe_ms/at`。
 */
object DesktopLineProbe {

    private const val PROBE_URL = "https://gelbooru.com/index.php?page=dapi&s=post&q=index&json=1&limit=1"

    sealed interface State {
        data object Idle : State
        data object Running : State
        data class Done(val latencyMs: Long, val at: Long) : State
        data object Failed : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    /** 测一次：成功写内存流与盘上账本；失败只出声不写（屏上回退到上次真数或「--」）。 */
    suspend fun probe(client: okhttp3.OkHttpClient, store: com.venera.compose.data.platform.JsonKeyValueStore): State {
        _state.value = State.Running
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val started = System.currentTimeMillis()
                client.newCall(Request.Builder().url(PROBE_URL).build()).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    System.currentTimeMillis() - started
                }
            }.getOrNull()
        }
        return if (result == null) {
            val failed = State.Failed
            _state.value = failed
            failed
        } else {
            store.put(KEY_LATENCY_MS, result)
            store.put(KEY_PROBED_AT, System.currentTimeMillis())
            val done = State.Done(result, System.currentTimeMillis())
            _state.value = done
            done
        }
    }

    /** 冷启动回填上次真数（舱位与设置行的「上次测速」读数来源）。 */
    fun restore(store: com.venera.compose.data.platform.JsonKeyValueStore) {
        val ms = store.getInt(KEY_LATENCY_MS, -1)
        val at = store.getLong(KEY_PROBED_AT, 0L)
        if (ms >= 0 && at > 0L) _state.value = State.Done(ms.toLong(), at)
    }

    /** 一行念法：`182 ms` / `6 天前 · 182 ms` / `--`（没测过）。 */
    fun readout(state: State, now: Long = System.currentTimeMillis()): String = when (state) {
        State.Idle, State.Failed -> "--"
        State.Running -> "测速中…"
        is State.Done -> "${state.latencyMs} ms"
    }

    private const val KEY_LATENCY_MS = "desktop_last_probe_ms"
    private const val KEY_PROBED_AT = "desktop_last_probe_at"
}
